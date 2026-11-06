[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SpecPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$SpecSha256,
    [Parameter(Mandatory=$true)][string]$OutputRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:utf8 = [Text.UTF8Encoding]::new($false)
$script:strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
$script:moduleSha256 = 'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'
$script:pythonSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec'
$script:powerShellSha256 = '60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f'
$script:childLimitMs = 180000
$script:outerLimitMs = 1200000
$script:outerReserveMs = 30000
$script:failureCode = $null
$script:outputCreated = $false
$script:outputCreationTicks = 0L
$script:outputRootFull = $null
$script:specFull = $null
$script:specRaw = $null
$script:specText = $null
$script:specValue = $null
$script:hostFull = $null
$script:hostSha256 = $null
$script:controllerSha256 = $null
$script:moduleFull = $null
$script:pythonFull = $null
$script:powerShellFull = $null
$script:sourceRoots = @()
$script:mutatedSpecs = @()
$script:caseResults = @()
$script:outerWatch = [Diagnostics.Stopwatch]::StartNew()
$script:runGuid = [Guid]::NewGuid().ToString('N')
$script:controllerPath = [IO.Path]::GetFullPath($PSCommandPath)
$script:manifestPath = $null

function Stop-Controller([string]$Code) {
    if ($Code -notmatch '^[A-Z0-9_]{1,96}$') { $Code = 'CONTROLLER_STATIC_FAILURE' }
    $script:failureCode = $Code
    throw $Code
}

function Get-Sha256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Get-StableBytes([string]$Path, [long]$MaximumBytes, [string]$Code) {
    Assert-NoReparseAncestors $Path
    try { $before = Get-Item -LiteralPath $Path -Force -ErrorAction Stop } catch { Stop-Controller $Code }
    if ($before -isnot [IO.FileInfo] -or $before.Length -lt 0 -or $before.Length -gt $MaximumBytes -or
        ($before.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Stop-Controller ($Code + '_SHAPE') }
    $stream = $null
    $memory = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        if ([long]$stream.Length -ne [long]$before.Length) { Stop-Controller ($Code + '_OPEN_LENGTH') }
        $memory = New-Object IO.MemoryStream
        $buffer = New-Object byte[] 32768
        $total = 0L
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $total += [long]$read
            if ($total -gt $MaximumBytes) { Stop-Controller ($Code + '_READ_SIZE') }
            $memory.Write($buffer, 0, $read)
        }
        if ($total -ne [long]$before.Length) { Stop-Controller ($Code + '_READ_LENGTH') }
        $bytes = $memory.ToArray()
    } catch {
        if ($script:failureCode) { throw }
        Stop-Controller $Code
    } finally {
        if ($null -ne $memory) { $memory.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
    Assert-NoReparseAncestors $Path
    try { $after = Get-Item -LiteralPath $Path -Force -ErrorAction Stop } catch { Stop-Controller ($Code + '_POST_READ') }
    if ($after -isnot [IO.FileInfo] -or [long]$after.Length -ne [long]$before.Length -or
        [long]$after.CreationTimeUtc.Ticks -ne [long]$before.CreationTimeUtc.Ticks -or
        [long]$after.LastWriteTimeUtc.Ticks -ne [long]$before.LastWriteTimeUtc.Ticks -or
        [int]$after.Attributes -ne [int]$before.Attributes) { Stop-Controller ($Code + '_METADATA_CHANGED') }
    return ,$bytes
}

function Assert-NoReparseAncestors([string]$Path) {
    try { $cursor = [IO.Path]::GetFullPath($Path) } catch { Stop-Controller 'PATH_CANONICALIZE' }
    while (-not (Test-Path -LiteralPath $cursor)) {
        $parent = Split-Path -Parent $cursor
        if ([String]::IsNullOrWhiteSpace($parent) -or
            $parent.Equals($cursor, [StringComparison]::OrdinalIgnoreCase)) { Stop-Controller 'PATH_ANCESTOR_MISSING' }
        $cursor = $parent
    }
    while ($true) {
        try { $item = Get-Item -LiteralPath $cursor -Force -ErrorAction Stop } catch { Stop-Controller 'PATH_ANCESTOR_METADATA' }
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Stop-Controller 'PATH_REPARSE' }
        $parent = Split-Path -Parent $cursor
        if ([String]::IsNullOrWhiteSpace($parent) -or
            $parent.Equals($cursor, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = $parent
    }
}

function Test-Within([string]$Root, [string]$Candidate) {
    $trimChars = [char[]]@('\', '/')
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd($trimChars)
    $candidateFull = [IO.Path]::GetFullPath($Candidate).TrimEnd($trimChars)
    if ($candidateFull.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    return $candidateFull.StartsWith($rootFull + '\', [StringComparison]::OrdinalIgnoreCase) -or
        $candidateFull.StartsWith($rootFull + '/', [StringComparison]::OrdinalIgnoreCase)
}

function Get-CanonicalPath([string]$Path, [string]$Code) {
    if ([String]::IsNullOrWhiteSpace($Path) -or -not [IO.Path]::IsPathRooted($Path)) { Stop-Controller $Code }
    try { $full = [IO.Path]::GetFullPath($Path) } catch { Stop-Controller $Code }
    if (-not $full.Equals($Path, [StringComparison]::Ordinal)) { Stop-Controller $Code }
    return $full
}

function Read-Spec([string]$Path, [string]$ExpectedSha) {
    $script:specFull = Get-CanonicalPath $Path 'SPEC_PATH'
    if (-not ([IO.Path]::GetDirectoryName($script:specFull)).Equals($PSScriptRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Stop-Controller 'SPEC_DIRECTORY_BINDING'
    }
    $script:specRaw = Get-StableBytes $script:specFull 4194304L 'SPEC_READ'
    if ((Get-Sha256 $script:specRaw) -cne $ExpectedSha) { Stop-Controller 'SPEC_HASH' }
    if ($script:specRaw.Length -lt 3 -or $script:specRaw[$script:specRaw.Length - 1] -ne 10 -or
        [Array]::IndexOf($script:specRaw, [byte]13) -ge 0 -or [Array]::IndexOf($script:specRaw, [byte]0) -ge 0 -or
        ($script:specRaw[0] -eq 239 -and $script:specRaw[1] -eq 187 -and $script:specRaw[2] -eq 191)) {
        Stop-Controller 'SPEC_ENCODING'
    }
    $newlineCount = 0
    foreach ($byte in $script:specRaw) { if ($byte -eq 10) { $newlineCount++ } }
    if ($newlineCount -ne 1) { Stop-Controller 'SPEC_EOF' }
    try { $script:specText = $script:strictUtf8.GetString($script:specRaw, 0, $script:specRaw.Length - 1) }
    catch { Stop-Controller 'SPEC_UTF8' }
    if (-not $script:specText.EndsWith('}', [StringComparison]::Ordinal)) { Stop-Controller 'SPEC_CANONICAL_SHAPE' }
    try { $script:specValue = ConvertFrom-Json -InputObject $script:specText -ErrorAction Stop }
    catch { Stop-Controller 'SPEC_JSON' }
    if ($null -eq $script:specValue -or $script:specValue -isnot [pscustomobject]) { Stop-Controller 'SPEC_OBJECT' }
}

function Get-SourceRoots() {
    $roots = New-Object 'System.Collections.Generic.List[string]'
    foreach ($path in @($PSScriptRoot, $script:specValue.repositoryRoot, $script:specValue.parentRun,
        (Split-Path -Parent ([string]$script:specValue.parentReader)))) {
        if (-not [String]::IsNullOrWhiteSpace([string]$path)) {
            $full = [IO.Path]::GetFullPath([string]$path)
            if (-not @($roots | Where-Object { $_.Equals($full, [StringComparison]::OrdinalIgnoreCase) }).Count) {
                [void]$roots.Add($full)
            }
        }
    }
    $inventoryRaw = Get-StableBytes ([string]$script:specValue.sourceManifest) 4194304L 'SOURCE_INVENTORY_READ'
    if ((Get-Sha256 $inventoryRaw) -cne [string]$script:specValue.sourceManifestSha256) { Stop-Controller 'SOURCE_INVENTORY_HASH' }
    try { $inventory = ConvertFrom-Json -InputObject $script:strictUtf8.GetString($inventoryRaw, 0, $inventoryRaw.Length - 1) -ErrorAction Stop }
    catch { Stop-Controller 'SOURCE_INVENTORY_JSON' }
    foreach ($path in @($inventory.repositoryRoot, $inventory.parentSourceRoot)) {
        $full = [IO.Path]::GetFullPath([string]$path)
        if (-not @($roots | Where-Object { $_.Equals($full, [StringComparison]::OrdinalIgnoreCase) }).Count) {
            [void]$roots.Add($full)
        }
    }
    foreach ($row in $script:specValue.files) {
        $parent = Split-Path -Parent ([string]$row.path)
        if (-not [String]::IsNullOrWhiteSpace($parent)) {
            $full = [IO.Path]::GetFullPath($parent)
            if (-not @($roots | Where-Object { $_.Equals($full, [StringComparison]::OrdinalIgnoreCase) }).Count) {
                [void]$roots.Add($full)
            }
        }
    }
    return ,$roots.ToArray()
}

function Assert-ExternalOutputRoot([string]$Requested) {
    $full = Get-CanonicalPath $Requested 'CONTROLLER_OUTPUT_PATH'
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $trimChars = [char[]]@('\', '/')
    if (-not (Test-Within $temp $full) -or $full.TrimEnd($trimChars).Equals($temp.TrimEnd($trimChars), [StringComparison]::OrdinalIgnoreCase)) {
        Stop-Controller 'CONTROLLER_OUTPUT_NOT_TEMP'
    }
    foreach ($root in $script:sourceRoots) {
        if (Test-Within $root $full) { Stop-Controller 'CONTROLLER_OUTPUT_INSIDE_SOURCE' }
    }
    Assert-NoReparseAncestors $full
    if (Test-Path -LiteralPath $full) { Stop-Controller 'CONTROLLER_OUTPUT_EXISTS' }
    $parent = Split-Path -Parent $full
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { Stop-Controller 'CONTROLLER_OUTPUT_PARENT' }
    try { [void][IO.Directory]::CreateDirectory($full) } catch { Stop-Controller 'CONTROLLER_OUTPUT_CREATE' }
    Assert-NoReparseAncestors $full
    $item = Get-Item -LiteralPath $full -Force -ErrorAction Stop
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        @(Get-ChildItem -LiteralPath $full -Force).Count -ne 0) { Stop-Controller 'CONTROLLER_OUTPUT_IDENTITY' }
    $script:outputRootFull = $full
    $script:outputCreationTicks = [long]$item.CreationTimeUtc.Ticks
    $script:outputCreated = $true
}

function Assert-ControllerOutputIdentity() {
    if (-not $script:outputCreated) { Stop-Controller 'CONTROLLER_OUTPUT_NOT_OWNED' }
    Assert-NoReparseAncestors $script:outputRootFull
    $item = Get-Item -LiteralPath $script:outputRootFull -Force -ErrorAction Stop
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        [long]$item.CreationTimeUtc.Ticks -ne $script:outputCreationTicks) { Stop-Controller 'CONTROLLER_OUTPUT_CHANGED' }
}

function Write-NewBytes([string]$Path, [byte[]]$Bytes, [string]$Code) {
    if (Test-Path -LiteralPath $Path) { Stop-Controller ($Code + '_EXISTS') }
    Assert-NoReparseAncestors $Path
    $stream = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        if ($Bytes.Length -gt 0) { $stream.Write($Bytes, 0, $Bytes.Length) }
        $stream.Flush($true)
    } catch {
        if ($script:failureCode) { throw }
        Stop-Controller $Code
    } finally { if ($null -ne $stream) { $stream.Dispose() } }
}

function Write-NewJson([string]$Path, $Value, [string]$Code) {
    try { $text = ConvertTo-Json -InputObject $Value -Depth 50 -Compress -ErrorAction Stop }
    catch { Stop-Controller ($Code + '_SERIALIZE') }
    $raw = $script:utf8.GetBytes($text + "`n")
    Write-NewBytes $Path $raw $Code
    return [pscustomobject]@{ path = [IO.Path]::GetFileName($Path); bytes = [long]$raw.Length; sha256 = (Get-Sha256 $raw) }
}

function New-SpecBytesFromObject($Value) {
    try { $text = ConvertTo-Json -InputObject $Value -Depth 50 -Compress -ErrorAction Stop }
    catch { Stop-Controller 'MUTANT_SPEC_SERIALIZE' }
    $raw = $script:utf8.GetBytes($text + "`n")
    if ($raw.Length -gt 4194304) { Stop-Controller 'MUTANT_SPEC_SIZE' }
    return ,$raw
}

function New-MutantSpec([string]$Name, [byte[]]$Raw) {
    $path = Join-Path $PSScriptRoot ('negative-spec-' + $script:runGuid + '-' + $Name + '.json')
    $path = Get-CanonicalPath $path 'MUTANT_SPEC_PATH'
    Write-NewBytes $path $Raw 'MUTANT_SPEC_WRITE'
    $actual = Get-StableBytes $path 4194304L 'MUTANT_SPEC_VERIFY'
    $sha = Get-Sha256 $actual
    if ($actual.Length -ne $Raw.Length -or $sha -cne (Get-Sha256 $Raw)) { Stop-Controller 'MUTANT_SPEC_VERIFY' }
    $asciiOnly = $true
    foreach ($byte in $actual) { if ($byte -gt 127) { $asciiOnly = $false; break } }
    $entry = [pscustomobject][ordered]@{
        name = $Name; path = $path; bytes = [long]$actual.Length; sha256 = $sha
        asciiOnly = $asciiOnly; retained = $true
    }
    $script:mutatedSpecs += $entry
    return $entry
}

function Get-SpecBody() {
    return $script:specText
}

function Get-ControllerIdentity() {
    try {
        $process = Get-Process -Id $PID -ErrorAction Stop
        $exe = [IO.Path]::GetFullPath([string]$process.MainModule.FileName)
        $ticks = [long]$process.StartTime.ToUniversalTime().Ticks
    } catch { Stop-Controller 'CONTROLLER_PROCESS_IDENTITY' }
    if ($PID -le 0 -or $ticks -le 0) { Stop-Controller 'CONTROLLER_PROCESS_IDENTITY' }
    return [pscustomobject][ordered]@{
        pid = [int]$PID; startTicks = [string]$ticks; executable = $exe
        script = [IO.Path]::GetFullPath($script:controllerPath)
    }
}

function Get-HostOutputState([string]$Path) {
    $exists = Test-Path -LiteralPath $Path
    $artifacts = @()
    $empty = $true
    $creationTicks = $null
    if ($exists) {
        $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
        if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            $empty = $false
            $artifacts += 'OUTPUT_ROOT_NOT_DIRECTORY'
        } else {
            $creationTicks = [string]$item.CreationTimeUtc.Ticks
            $children = @(Get-ChildItem -LiteralPath $Path -Force)
            $empty = ($children.Count -eq 0)
            foreach ($name in @('parent-process.json','parent.stdout','parent.stderr',
                'worker-process.json','worker.stdout','worker.stderr','host-receipt.json','data')) {
                if (Test-Path -LiteralPath (Join-Path $Path $name)) { $artifacts += $name }
            }
            $dataPath = Join-Path $Path 'data'
            if ((Test-Path -LiteralPath $dataPath -PathType Container) -and
                (Test-Path -LiteralPath (Join-Path $dataPath 'sensitivity-result.json'))) {
                $artifacts += 'data/sensitivity-result.json'
            }
            if ($children.Count -gt 0 -and $artifacts.Count -eq 0) { $artifacts += 'UNEXPECTED_CHILD' }
        }
    }
    return [pscustomobject][ordered]@{
        exists = $exists; empty = $empty; creationTicks = $creationTicks
        knownArtifacts = @($artifacts); noSuccessfulArtifacts = ($artifacts.Count -eq 0)
    }
}

function Get-FailureMarker([string]$Stderr) {
    # Windows PowerShell's Console.Error.WriteLine emits exactly one CRLF here.
    if ([String]::IsNullOrEmpty($Stderr) -or -not $Stderr.EndsWith("`r`n", [StringComparison]::Ordinal) -or
        $Stderr.IndexOf("`r`n") -ne ($Stderr.Length - 2)) { return $null }
    $line = $Stderr.Substring(0, $Stderr.Length - 2)
    if ($line.Contains("`r") -or $line.Contains("`n")) { return $null }
    if ($line -cmatch '^P7_HOST_FAILURE ([A-Z0-9_]{1,96})$') { return $Matches[1] }
    return $null
}

function Test-StringArrayEqual([object[]]$Actual, [string[]]$Expected) {
    if ($Actual.Count -ne $Expected.Count) { return $false }
    for ($i = 0; $i -lt $Expected.Count; $i++) {
        if ([string]$Actual[$i] -cne $Expected[$i]) { return $false }
    }
    return $true
}

function Add-HostCase([string]$Name, [string]$CaseSpecPath, [string]$CaseSpecSha,
        [string]$ExpectedFailure, [string]$HostOutputPath, [string]$OutputMode,
        [object]$ExpectedCreationTicks) {
    $script:casePlan += [pscustomobject][ordered]@{
        name = $Name; specPath = $CaseSpecPath; specSha256 = $CaseSpecSha
        expectedFailure = $ExpectedFailure; hostOutputPath = $HostOutputPath
        outputMode = $OutputMode; expectedCreationTicks = $ExpectedCreationTicks
    }
}

function New-UniqueHostOutput([string]$Name) {
    $path = Join-Path $script:outputRootFull ('host-output-' + $script:runGuid + '-' + $Name)
    $path = Get-CanonicalPath $path 'HOST_OUTPUT_PATH'
    if (Test-Path -LiteralPath $path) { Stop-Controller 'HOST_OUTPUT_COLLISION' }
    Assert-NoReparseAncestors $path
    return $path
}

function Add-HostArtifact([string]$Name, $Receipt, [double]$WallMs, $Owner,
        [string[]]$Arguments, [string]$HostOutputPath, [string]$OutputMode,
        [long]$ExpectedCreationTicks, [string]$ExpectedFailure) {
    $prefix = ('{0:D2}-{1}' -f ($script:caseResults.Count + 1), $Name)
    $stdout = [string]$Receipt.Stdout
    $stderr = [string]$Receipt.Stderr
    $stdoutRaw = $script:utf8.GetBytes($stdout)
    $stderrRaw = $script:utf8.GetBytes($stderr)
    $stdoutName = $prefix + '.stdout.utf8'
    $stderrName = $prefix + '.stderr.utf8'
    $receiptName = $prefix + '.process.json'
    Write-NewBytes (Join-Path $script:outputRootFull $stdoutName) $stdoutRaw 'PROCESS_STDOUT_SAVE'
    Write-NewBytes (Join-Path $script:outputRootFull $stderrName) $stderrRaw 'PROCESS_STDERR_SAVE'
    $receiptFile = Write-NewJson (Join-Path $script:outputRootFull $receiptName) $Receipt 'PROCESS_RECEIPT_SAVE'
    $observedFailure = Get-FailureMarker $stderr
    $outputState = Get-HostOutputState $HostOutputPath
    $expectedOutputState = if ($OutputMode -ceq 'EXISTING_EMPTY') {
        $outputState.exists -and $outputState.empty -and
            $outputState.creationTicks -ceq [string]$ExpectedCreationTicks
    } else { -not $outputState.exists }
    $plannedCase = $script:casePlan[$script:caseResults.Count]
    $expectedArgs = @('-NoProfile','-File',$script:hostFull,'-Mode','CANARY',
        '-SpecPath',$plannedCase.specPath,'-SpecSha256',$plannedCase.specSha256,
        '-OutputRoot',$plannedCase.hostOutputPath)
    $actualArgs = @($Receipt.Arguments)
    $passedArgs = @($Arguments)
    $ownerStillSame = $false
    try {
        $ownerNow = Get-Process -Id ([int]$Owner.pid) -ErrorAction Stop
        $ownerStillSame = ([long]$ownerNow.StartTime.ToUniversalTime().Ticks -eq [long]$Owner.startTicks)
    } catch { $ownerStillSame = $false }
    $processValid = ([string]$Receipt.FilePath).Equals($script:powerShellFull, [StringComparison]::OrdinalIgnoreCase) -and
        [int]$Receipt.ProcessId -gt 0 -and [long]$Receipt.ProcessStartTicks -gt 0 -and
        $Receipt.TerminationProven -is [bool] -and $Receipt.TerminationProven -eq $true -and
        $Receipt.ExitCode -is [int] -and $Receipt.ExitCode -ne 0 -and
        $WallMs -ge 0 -and $WallMs -le $script:childLimitMs -and
        (Test-StringArrayEqual $passedArgs $expectedArgs) -and
        (Test-StringArrayEqual $actualArgs $passedArgs) -and $ownerStillSame
    $semanticPass = $processValid -and [String]::IsNullOrEmpty($stdout) -and
        $observedFailure -ceq $ExpectedFailure -and $expectedOutputState -and
        $outputState.noSuccessfulArtifacts
    $caseRecord = [pscustomobject][ordered]@{
        name = $Name; status = if ($semanticPass) { 'PASS_REJECTED_AS_EXPECTED' } else { 'FAIL' }
        expectedFailure = $ExpectedFailure; observedFailure = $observedFailure
        hostOutputPath = $HostOutputPath; outputMode = $OutputMode
        outputState = $outputState; outputStateExpected = [bool]$expectedOutputState
        ownerAtLaunch = $Owner; ownerSameAfterExit = $ownerStillSame
        process = [pscustomobject][ordered]@{
            filePath = [string]$Receipt.FilePath; arguments = @($Receipt.Arguments)
            processId = [int]$Receipt.ProcessId; processStartTicks = [string]$Receipt.ProcessStartTicks
            exitCode = [int]$Receipt.ExitCode; terminationProven = [bool]$Receipt.TerminationProven
            wallMs = [Math]::Round($WallMs, 3); childLimitMs = $script:childLimitMs
            receiptFile = $receiptName; receiptSha256 = $receiptFile.sha256
            stdoutFile = $stdoutName; stdoutBytes = [long]$stdoutRaw.Length; stdoutSha256 = (Get-Sha256 $stdoutRaw)
            stderrFile = $stderrName; stderrBytes = [long]$stderrRaw.Length; stderrSha256 = (Get-Sha256 $stderrRaw)
        }
    }
    $script:caseResults += $caseRecord
    return [bool]$semanticPass
}

function Get-HostInputPin([string]$Path, [string]$ExpectedSha, [string]$Name) {
    $rows = @($script:specValue.files | Where-Object {
        ([string]$_.path).Equals($Path, [StringComparison]::OrdinalIgnoreCase)
    })
    if ($rows.Count -ne 1 -or [string]$rows[0].sha256 -cne $ExpectedSha) { Stop-Controller ($Name + '_SPEC_PIN') }
}

function Invoke-NegativeCase($Case) {
    if ($script:outerWatch.Elapsed.TotalMilliseconds + $script:childLimitMs + $script:outerReserveMs -gt $script:outerLimitMs) {
        Stop-Controller 'CONTROLLER_OUTER_RESERVE'
    }
    if ($Case.outputMode -ceq 'NONEXISTENT' -and (Test-Path -LiteralPath $Case.hostOutputPath)) {
        Stop-Controller 'HOST_OUTPUT_PREEXISTS'
    }
    if ($Case.outputMode -ceq 'EXISTING_EMPTY') {
        if (Test-Path -LiteralPath $Case.hostOutputPath) { Stop-Controller 'EXISTING_OUTPUT_COLLISION' }
        [void][IO.Directory]::CreateDirectory($Case.hostOutputPath)
        Assert-NoReparseAncestors $Case.hostOutputPath
        $item = Get-Item -LiteralPath $Case.hostOutputPath -Force -ErrorAction Stop
        if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            @(Get-ChildItem -LiteralPath $Case.hostOutputPath -Force).Count -ne 0) { Stop-Controller 'EXISTING_OUTPUT_CREATE' }
        $Case.expectedCreationTicks = [long]$item.CreationTimeUtc.Ticks
    }
    $owner = Get-ControllerIdentity
    $hostArguments = @('-NoProfile','-File',$script:hostFull,'-Mode','CANARY',
        '-SpecPath',[string]$Case.specPath,'-SpecSha256',[string]$Case.specSha256,
        '-OutputRoot',[string]$Case.hostOutputPath)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $receipt = $null
    try { $receipt = Invoke-VerifierBoundedProcess $script:powerShellFull $hostArguments $script:childLimitMs }
    catch {
        $watch.Stop()
        $script:caseResults += [pscustomobject][ordered]@{
            name = $Case.name; status = 'FAIL_NO_PROCESS_RECEIPT'; expectedFailure = $Case.expectedFailure
            hostOutputPath = $Case.hostOutputPath; outputMode = $Case.outputMode
            ownerAtLaunch = $owner; launchExceptionType = $_.Exception.GetType().FullName
            launchExceptionMessage = [string]$_.Exception.Message
            wallMs = [Math]::Round($watch.Elapsed.TotalMilliseconds, 3)
            terminationProven = $false
        }
        $script:failureCode = 'BOUNDED_HOST_PROCESS_UNPROVEN'
        return $false
    }
    $watch.Stop()
    return Add-HostArtifact $Case.name $receipt $watch.Elapsed.TotalMilliseconds $owner $hostArguments `
        $Case.hostOutputPath $Case.outputMode ([long]$Case.expectedCreationTicks) $Case.expectedFailure
}

function Write-ControllerManifest() {
    if (-not $script:outputCreated) { return }
    Assert-ControllerOutputIdentity
    if ($script:outerWatch.Elapsed.TotalMilliseconds -gt $script:outerLimitMs) {
        $script:failureCode = 'CONTROLLER_OUTER_LIMIT'
    }
    $allPass = ($script:caseResults.Count -eq 7)
    foreach ($case in $script:caseResults) {
        if ($case.status -cne 'PASS_REJECTED_AS_EXPECTED') { $allPass = $false }
    }
    if ($null -ne $script:failureCode) { $allPass = $false }
    $hostBytes = Get-StableBytes $script:hostFull 1048576L 'HOST_SCRIPT_RECHECK'
    $moduleBytes = Get-StableBytes $script:moduleFull 4194304L 'MODULE_RECHECK'
    $pythonBytes = Get-StableBytes $script:pythonFull 67108864L 'PYTHON_RECHECK'
    $powerShellBytes = Get-StableBytes $script:powerShellFull 67108864L 'POWERSHELL_RECHECK'
    $specBytes = Get-StableBytes $script:specFull 4194304L 'SPEC_RECHECK'
    $hostDigest = Get-Sha256 $hostBytes
    $moduleDigest = Get-Sha256 $moduleBytes
    $pythonDigest = Get-Sha256 $pythonBytes
    $powerShellDigest = Get-Sha256 $powerShellBytes
    $specDigest = Get-Sha256 $specBytes
    $controllerBytes = Get-StableBytes $script:controllerPath 1048576L 'CONTROLLER_SCRIPT_RECHECK'
    $controllerSha = Get-Sha256 $controllerBytes
    $mutantRechecks = @()
    foreach ($mutant in $script:mutatedSpecs) {
        $bytes = Get-StableBytes $mutant.path 4194304L 'MUTANT_SPEC_RECHECK'
        $sha = Get-Sha256 $bytes
        $unchanged = ($bytes.Length -eq $mutant.bytes -and $sha -ceq $mutant.sha256)
        $mutantRechecks += [pscustomobject][ordered]@{
            name = $mutant.name; path = $mutant.path; bytes = [long]$bytes.Length
            sha256 = $sha; unchanged = $unchanged
        }
        if (-not $unchanged) {
            $allPass = $false
            if ($null -eq $script:failureCode) { $script:failureCode = 'MUTANT_SPEC_CHANGED' }
        }
    }
    if ($hostDigest -cne $script:hostSha256 -or $moduleDigest -cne $script:moduleSha256 -or
        $pythonDigest -cne $script:pythonSha256 -or $powerShellDigest -cne $script:powerShellSha256 -or
        $specBytes.Length -ne $script:specRaw.Length -or $specDigest -cne $SpecSha256 -or
        $controllerSha -cne $script:controllerSha256) {
        $allPass = $false
        if ($null -eq $script:failureCode) { $script:failureCode = 'PINNED_INPUT_CHANGED' }
    }
    $manifest = [pscustomobject][ordered]@{
        schema = 'tsj-p7-host-negative-controls-v1'
        status = if ($allPass) { 'PASS_REJECTIONS_ONLY' } else { 'FAIL_NEGATIVE_CONTROLS' }
        scope = 'P7_HOST_CANARY_NEGATIVE_REJECTIONS_ONLY'
        runGuid = $script:runGuid
        controller = $script:controllerIdentity
        controllerScriptSha256 = $controllerSha
        controllerScriptUnchanged = ($controllerSha -ceq $script:controllerSha256)
        input = [pscustomobject][ordered]@{
            specPath = $script:specFull; specSha256 = $SpecSha256
            hostPath = $script:hostFull; hostSha256 = $script:hostSha256
            selectedPowerShell = $script:powerShellFull; selectedPowerShellSha256 = $script:powerShellSha256
            selectedPython = $script:pythonFull; selectedPythonSha256 = $script:pythonSha256
            verifierIsolation = $script:moduleFull; verifierIsolationSha256 = $script:moduleSha256
        }
        mutatedSpecs = @($script:mutatedSpecs)
        mutatedSpecRechecks = @($mutantRechecks)
        cases = @($script:caseResults)
        expectedCases = 7
        completedCases = $script:caseResults.Count
        childLimitMs = $script:childLimitMs
        outerLimitMs = $script:outerLimitMs
        outerElapsedMs = [Math]::Round($script:outerWatch.Elapsed.TotalMilliseconds, 3)
        failureCode = $script:failureCode
        limitations = @('NO_HOST_CASE_ACCEPTANCE','NO_ANALYZE_PARENT_RUN','NO_WORKER_SUCCESS','NO_BUILD_OR_CASE_EXECUTION')
    }
    $null = Write-NewJson $script:manifestPath $manifest 'CONTROLLER_MANIFEST'
}

try {
    $controllerScriptBytes = Get-StableBytes $script:controllerPath 1048576L 'CONTROLLER_SCRIPT_READ'
    $script:controllerSha256 = Get-Sha256 $controllerScriptBytes
    $script:controllerIdentity = Get-ControllerIdentity
    Read-Spec $SpecPath $SpecSha256
    $script:sourceRoots = Get-SourceRoots
    $script:hostFull = Get-CanonicalPath (Join-Path $PSScriptRoot 'run-cut-sensitivity.ps1') 'HOST_PATH'
    $hostBytes = Get-StableBytes $script:hostFull 1048576L 'HOST_READ'
    $script:hostSha256 = Get-Sha256 $hostBytes
    $script:moduleFull = Get-CanonicalPath (Join-Path ([string]$script:specValue.repositoryRoot) 'scripts\VerifierIsolation.psm1') 'MODULE_PATH'
    $script:pythonFull = Get-CanonicalPath ([string]$script:specValue.selectedPython) 'PYTHON_PATH'
    $script:powerShellFull = Get-CanonicalPath ([string]$script:specValue.selectedPowerShell) 'POWERSHELL_PATH'
    if ([string]$script:specValue.selectedPythonSha256 -cne $script:pythonSha256 -or
        [string]$script:specValue.selectedPowerShellSha256 -cne $script:powerShellSha256) { Stop-Controller 'SELECTED_TOOL_PIN' }
    $moduleBytes = Get-StableBytes $script:moduleFull 4194304L 'MODULE_READ'
    $pythonBytes = Get-StableBytes $script:pythonFull 67108864L 'PYTHON_READ'
    $powerShellBytes = Get-StableBytes $script:powerShellFull 67108864L 'POWERSHELL_READ'
    if ((Get-Sha256 $moduleBytes) -cne $script:moduleSha256 -or
        (Get-Sha256 $pythonBytes) -cne $script:pythonSha256 -or
        (Get-Sha256 $powerShellBytes) -cne $script:powerShellSha256) { Stop-Controller 'SELECTED_TOOL_HASH' }
    Get-HostInputPin $script:hostFull $script:hostSha256 'HOST'
    Get-HostInputPin $script:moduleFull $script:moduleSha256 'MODULE'
    Get-HostInputPin $script:pythonFull $script:pythonSha256 'PYTHON'
    Get-HostInputPin $script:powerShellFull $script:powerShellSha256 'POWERSHELL'
    Import-Module $script:moduleFull -Force -DisableNameChecking -ErrorAction Stop
    if (-not (Get-Command Invoke-VerifierBoundedProcess -ErrorAction SilentlyContinue) -or
        -not (Get-Command Assert-VerifierNoReparseAncestors -ErrorAction SilentlyContinue)) { Stop-Controller 'VERIFIER_ISOLATION_API' }
    Assert-VerifierNoReparseAncestors $script:hostFull
    Assert-VerifierNoReparseAncestors $script:moduleFull
    Assert-ExternalOutputRoot $OutputRoot
    $script:manifestPath = Join-Path $script:outputRootFull 'negative-controls.json'

    $groupsBody = Get-SpecBody
    $groupsMatches = [regex]::Matches($groupsBody, '"groups"\s*:\s*640(?=\s*[,}])')
    if ($groupsMatches.Count -ne 1) { Stop-Controller 'SPEC_GROUPS_MUTATION_ANCHOR' }
    $groupMatch = $groupsMatches[0]
    $groupsBooleanBody = $groupsBody.Substring(0, $groupMatch.Index) + '"groups":true' +
        $groupsBody.Substring($groupMatch.Index + $groupMatch.Length)
    $groupsBooleanMutant = New-MutantSpec 'groups-boolean' $script:utf8.GetBytes($groupsBooleanBody + "`n")

    $unknownBody = Get-SpecBody
    if (-not $unknownBody.EndsWith('}', [StringComparison]::Ordinal)) { Stop-Controller 'SPEC_UNKNOWN_MUTATION_ANCHOR' }
    $unknownText = $unknownBody.Substring(0, $unknownBody.Length - 1) + ',"negativeControlUnknownKey":0}'
    $unknownMutant = New-MutantSpec 'unknown-key' $script:utf8.GetBytes($unknownText + "`n")

    $duplicateBody = Get-SpecBody
    $duplicateText = $duplicateBody.Substring(0, $duplicateBody.Length - 1) + ',"schema":"tsj-canonical-cut-sensitivity-input-v1"}'
    $duplicateMutant = New-MutantSpec 'duplicate-schema-key' $script:utf8.GetBytes($duplicateText + "`n")

    $workerObject = ConvertFrom-Json -InputObject $script:specText -ErrorAction Stop
    $workerRows = @($workerObject.files | Where-Object {
        ([string]$_.path).Equals([string]$workerObject.worker, [StringComparison]::OrdinalIgnoreCase)
    })
    if ($workerRows.Count -ne 1) { Stop-Controller 'WORKER_PIN_MUTATION_ANCHOR' }
    $wrongWorkerSha = if ([string]$workerRows[0].sha256 -ceq ('0' * 64)) { '1' * 64 } else { '0' * 64 }
    $workerRows[0].sha256 = $wrongWorkerSha
    $workerMutant = New-MutantSpec 'worker-file-pin-sha' (New-SpecBytesFromObject $workerObject)

    $script:casePlan = @()
    $originalSpecSha = Get-Sha256 $script:specRaw
    $wrongSpecSha = if ($originalSpecSha[0] -ceq '0') { '1' + $originalSpecSha.Substring(1) } else { '0' + $originalSpecSha.Substring(1) }
    Add-HostCase 'wrongSpecSha' $script:specFull $wrongSpecSha 'SPEC_HASH' (New-UniqueHostOutput 'wrongSpecSha') 'NONEXISTENT' $null
    Add-HostCase 'groupsBoolean' $groupsBooleanMutant.path $groupsBooleanMutant.sha256 'SPEC_LIMIT_VALUE' (New-UniqueHostOutput 'groupsBoolean') 'NONEXISTENT' $null
    Add-HostCase 'unknownSpecKey' $unknownMutant.path $unknownMutant.sha256 'SPEC_KEYS' (New-UniqueHostOutput 'unknownSpecKey') 'NONEXISTENT' $null
    Add-HostCase 'duplicateSpecKey' $duplicateMutant.path $duplicateMutant.sha256 'SPEC_DUPLICATE_KEY' (New-UniqueHostOutput 'duplicateSpecKey') 'NONEXISTENT' $null
    Add-HostCase 'workerFilePinSHAwrong' $workerMutant.path $workerMutant.sha256 'SPEC_FILE_PIN_MISSING' (New-UniqueHostOutput 'workerFilePinSHAwrong') 'NONEXISTENT' $null

    $insideD = Get-CanonicalPath (Join-Path ([string]$script:specValue.repositoryRoot) ('p7-negative-output-' + $script:runGuid)) 'OUTPUT_INSIDE_D_PATH'
    if (Test-Path -LiteralPath $insideD) { Stop-Controller 'OUTPUT_INSIDE_D_COLLISION' }
    Assert-NoReparseAncestors $insideD
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $insideExpected = if (Test-Within $temp $insideD) { 'OUTPUT_ROOT_INSIDE_SOURCE' } else { 'OUTPUT_ROOT_NOT_TEMP' }
    Add-HostCase 'outputInsideD' $script:specFull $originalSpecSha $insideExpected $insideD 'NONEXISTENT' $null

    $existingOutput = New-UniqueHostOutput 'outputAlreadyExists'
    Add-HostCase 'outputAlreadyExists' $script:specFull $originalSpecSha 'OUTPUT_ROOT_EXISTS' $existingOutput 'EXISTING_EMPTY' $null
    $script:controllerIdentity = Get-ControllerIdentity
    $allSeven = $true
    foreach ($case in $script:casePlan) {
        try {
            $pass = Invoke-NegativeCase $case
            if (-not $pass) { $allSeven = $false }
        } catch {
            $script:caseResults += [pscustomobject][ordered]@{
                name = $case.name; status = 'FAIL_CONTROLLER_EXCEPTION'
                expectedFailure = $case.expectedFailure; hostOutputPath = $case.hostOutputPath
                exceptionType = $_.Exception.GetType().FullName
                exceptionSha256 = (Get-Sha256 ($script:utf8.GetBytes([string]$_.Exception.Message)))
            }
            $script:failureCode = 'CONTROLLER_CASE_FAILURE'
            $allSeven = $false
        }
        if ($script:failureCode -ceq 'BOUNDED_HOST_PROCESS_UNPROVEN') { break }
    }
    if (-not $allSeven -and $null -eq $script:failureCode) { $script:failureCode = 'NEGATIVE_CASE_REJECTED_UNEXPECTEDLY' }
} catch {
    if ($null -eq $script:failureCode) {
        $message = [string]$_.Exception.Message
        if ($message -match '^[A-Z0-9_]{1,96}$') { $script:failureCode = $message }
        else { $script:failureCode = 'CONTROLLER_SETUP_FAILURE' }
    }
}

try { Write-ControllerManifest }
catch {
    if ($null -eq $script:failureCode) { $script:failureCode = 'CONTROLLER_MANIFEST_FAILURE' }
}
$script:outerWatch.Stop()
if ($null -ne $script:failureCode -or $script:caseResults.Count -ne 7 -or
    @($script:caseResults | Where-Object { $_.status -cne 'PASS_REJECTED_AS_EXPECTED' }).Count -ne 0) {
    [Console]::Error.WriteLine('P7_NEGATIVE_CONTROLS_FAILURE ' + $(if ($script:failureCode) { $script:failureCode } else { 'SEMANTIC_REJECTION_NOT_PROVEN' }))
    exit 1
}
[Console]::Out.WriteLine('P7_NEGATIVE_CONTROLS_COMPLETE PASS_REJECTIONS_ONLY')
exit 0
