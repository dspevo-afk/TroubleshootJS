[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$RunRoot,
    [Parameter(Mandatory=$true)][string]$SpecPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$SpecSha256,
    [Parameter(Mandatory=$true)][string]$OutputRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:failureCode = $null
$script:phase = 'PREFLIGHT'
$script:utf8 = [Text.UTF8Encoding]::new($false)
$script:strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
$script:chunkBytes = 32768
$script:artifactLimit = 8388608L
$script:lineLimit = 65536L
$script:specLimit = 4194304L
$script:perRunCopyLimit = 8388608L
$script:totalCopyLimit = 109051904L
$script:streamLimit = 65536L
$script:moduleSha256 = 'fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c'
$script:readerPath = $null
$script:pythonPath = $null
$script:modulePath = $null
$script:specFull = $null
$script:runRootFull = $null
$script:outputRootFull = $null
$script:outputCreationTicks = 0L
$script:outputCreated = $false
$script:owner = $null
$script:spec = $null
$script:inputSnapshot = $null
$script:contextSnapshot = $null
$script:caseResults = New-Object 'System.Collections.Generic.List[object]'
$script:failureRows = New-Object 'System.Collections.Generic.List[string]'
$script:outerWatch = [Diagnostics.Stopwatch]::StartNew()
$script:copiedBytesTotal = 0L

function Fail([string]$Code) {
    if ($Code -notmatch '^[A-Z0-9_]{1,96}$') { $Code = 'READER_NEGATIVE_CONTROL_FAILURE' }
    $script:failureCode = $Code
    throw $Code
}

function Test-PathWithin([string]$Root, [string]$Candidate) {
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd('\')
    $candidateFull = [IO.Path]::GetFullPath($Candidate).TrimEnd('\')
    if ($candidateFull.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    return $candidateFull.StartsWith($rootFull + '\', [StringComparison]::OrdinalIgnoreCase)
}

function Get-CanonicalPath([string]$Path, [string]$Code) {
    if ([String]::IsNullOrWhiteSpace($Path) -or -not [IO.Path]::IsPathRooted($Path)) { Fail $Code }
    try { $full = [IO.Path]::GetFullPath($Path) } catch { Fail $Code }
    if (-not $full.Equals($Path, [StringComparison]::Ordinal)) { Fail $Code }
    return $full
}

function Assert-DirectoryPath([string]$Path, [string]$Code) {
    $full = Get-CanonicalPath $Path $Code
    Assert-TaskNoReparseAncestors $full
    try { $item = Get-Item -LiteralPath $full -Force -ErrorAction Stop } catch { Fail $Code }
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Fail ($Code + '_TYPE')
    }
    return $full
}

function Assert-TaskNoReparseAncestors([string]$Path) {
    $maintained = Get-Command Assert-VerifierNoReparseAncestors -ErrorAction SilentlyContinue
    if ($maintained) { & $maintained $Path; return }
    # Bootstrap only: the pinned module cannot be imported before its source
    # and the caller's spec have been read and authenticated.
    $cursor = [IO.Path]::GetFullPath($Path)
    while (-not (Test-Path -LiteralPath $cursor)) {
        $parent = [IO.Path]::GetDirectoryName($cursor)
        if ([String]::IsNullOrEmpty($parent) -or $parent -ceq $cursor) { Fail 'BOOTSTRAP_PATH' }
        $cursor = $parent
    }
    while ($true) {
        $attributes = [IO.File]::GetAttributes($cursor)
        if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'BOOTSTRAP_REPARSE' }
        $parent = [IO.Path]::GetDirectoryName($cursor)
        if ([String]::IsNullOrEmpty($parent) -or $parent -ceq $cursor) { break }
        $cursor = $parent
    }
}

function Get-FileMetadata([string]$Path, [string]$Code) {
    try { $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop } catch { Fail $Code }
    if ($item -isnot [IO.FileInfo] -or
        ($item.Attributes -band [IO.FileAttributes]::Directory) -ne 0 -or
        ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail $Code }
    return [pscustomobject]@{
        length = [long]$item.Length
        attributes = [int]$item.Attributes
        creationTicks = [long]$item.CreationTimeUtc.Ticks
        writeTicks = [long]$item.LastWriteTimeUtc.Ticks
    }
}

function Read-StableBytes([string]$Path, [long]$MaximumBytes, [string]$Code) {
    Assert-TaskNoReparseAncestors $Path
    $before = Get-FileMetadata $Path $Code
    if ($before.length -lt 0 -or $before.length -gt $MaximumBytes) { Fail ($Code + '_SIZE') }
    $stream = $null
    $memory = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        if ([long]$stream.Length -ne $before.length) { Fail ($Code + '_OPEN_LENGTH') }
        $memory = New-Object IO.MemoryStream
        $buffer = New-Object byte[] $script:chunkBytes
        $total = 0L
        while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $total += [long]$count
            if ($total -gt $MaximumBytes) { Fail ($Code + '_READ_SIZE') }
            $memory.Write($buffer, 0, $count)
        }
        if ($total -ne $before.length) { Fail ($Code + '_READ_LENGTH') }
        $raw = $memory.ToArray()
    } catch {
        if ($script:failureCode) { throw }
        Fail $Code
    } finally {
        if ($null -ne $memory) { $memory.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
    Assert-TaskNoReparseAncestors $Path
    $after = Get-FileMetadata $Path $Code
    if ($after.length -ne $before.length -or $after.attributes -ne $before.attributes -or
        $after.creationTicks -ne $before.creationTicks -or $after.writeTicks -ne $before.writeTicks) {
        Fail ($Code + '_METADATA_CHANGED')
    }
    return ,$raw
}

function Get-StableHash([string]$Path, [long]$MaximumBytes, [string]$Code) {
    $raw = Read-StableBytes $Path $MaximumBytes $Code
    return [pscustomobject]@{ bytes = [long]$raw.Length; sha256 = (Get-Sha256Bytes $raw) }
}

function Get-Sha256Bytes([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Assert-JsonNoDuplicateKeys([string]$Text, [string]$Code) {
    $frames = New-Object 'System.Collections.Generic.List[object]'
    for ($i = 0; $i -lt $Text.Length; $i++) {
        if ($Text[$i] -eq '"') {
            $start = $i
            $i++
            $closed = $false
            while ($i -lt $Text.Length) {
                if ($Text[$i] -eq '\') {
                    $i++
                    if ($i -ge $Text.Length) { Fail ($Code + '_STRING') }
                } elseif ($Text[$i] -eq '"') {
                    $closed = $true
                    break
                }
                $i++
            }
            if (-not $closed) { Fail ($Code + '_STRING') }
            $next = $i + 1
            while ($next -lt $Text.Length -and [char]::IsWhiteSpace($Text[$next])) { $next++ }
            if ($next -lt $Text.Length -and $Text[$next] -eq ':' -and $frames.Count -gt 0) {
                $frame = $frames[$frames.Count - 1]
                if ($frame.kind -ceq 'object') {
                    $tokenLength = $i - $start + 1
                    if ($tokenLength -gt 65536) { Fail ($Code + '_KEY_SIZE') }
                    try { $key = ConvertFrom-Json -InputObject $Text.Substring($start, $tokenLength) -ErrorAction Stop }
                    catch { Fail ($Code + '_KEY_ENCODING') }
                    if ($key -isnot [string] -or $frame.keys.Contains($key)) { Fail ($Code + '_DUPLICATE_KEY') }
                    [void]$frame.keys.Add($key)
                }
            }
            continue
        }
        if (($Text[$i] -eq 'N' -and $i + 3 -le $Text.Length -and $Text.Substring($i, 3) -ceq 'NaN') -or
            ($Text[$i] -eq 'I' -and $i + 8 -le $Text.Length -and $Text.Substring($i, 8) -ceq 'Infinity') -or
            ($Text[$i] -eq '-' -and $i + 9 -le $Text.Length -and $Text.Substring($i, 9) -ceq '-Infinity')) {
            Fail ($Code + '_NONFINITE')
        }
        if ($Text[$i] -eq '{') {
            $keySet = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
            [void]$frames.Add([pscustomobject]@{ kind = 'object'; keys = $keySet })
        } elseif ($Text[$i] -eq '[') {
            [void]$frames.Add([pscustomobject]@{ kind = 'array'; keys = $null })
        } elseif (($Text[$i] -eq '}' -or $Text[$i] -eq ']') -and $frames.Count -gt 0) {
            $frames.RemoveAt($frames.Count - 1)
        }
    }
}

function Read-Json([byte[]]$Raw, [string]$Code, [long]$MaximumBytes, [bool]$OneLine = $false) {
    if ($Raw.Length -lt 2 -or $Raw.Length -gt $MaximumBytes -or
        $Raw[0] -eq 239 -or $Raw[1] -eq 187 -or $Raw.Length -gt 2 -and $Raw[2] -eq 191 -or
        [Array]::IndexOf($Raw, [byte]0) -ge 0) { Fail ($Code + '_ENCODING') }
    if ($OneLine -and ($Raw[$Raw.Length - 1] -ne 10 -or
        [Array]::IndexOf($Raw, [byte]13) -ge 0 -or
        @($Raw | Where-Object { $_ -eq 10 }).Count -ne 1)) { Fail ($Code + '_FRAMING') }
    try {
        $length = if ($Raw[$Raw.Length - 1] -eq 10) { $Raw.Length - 1 } else { $Raw.Length }
        $text = $script:strictUtf8.GetString($Raw, 0, $length)
    } catch { Fail ($Code + '_UTF8') }
    Assert-JsonNoDuplicateKeys $text $Code
    try { $value = ConvertFrom-Json -InputObject $text -ErrorAction Stop }
    catch { Fail ($Code + '_JSON') }
    return [pscustomobject]@{ value = $value; text = $text }
}

function Assert-JsonInteger($Value, [long]$Minimum, [long]$Maximum, [string]$Code) {
    if ($Value -is [bool] -or -not ($Value -is [int] -or $Value -is [long])) { Fail $Code }
    $number = [long]$Value
    if ($number -lt $Minimum -or $number -gt $Maximum) { Fail $Code }
    return $number
}

function Read-JsonFile([string]$Path, [long]$MaximumBytes, [string]$Code, [bool]$OneLine = $false) {
    $raw = Read-StableBytes $Path $MaximumBytes $Code
    $parsed = Read-Json $raw $Code $MaximumBytes $OneLine
    return [pscustomobject]@{ raw = $raw; value = $parsed.value; sha256 = Get-Sha256Bytes $raw }
}

function Assert-ExactKeys($Value, [string[]]$Expected, [string]$Code) {
    if ($null -eq $Value -or $Value -isnot [pscustomobject]) { Fail ($Code + '_OBJECT') }
    $actual = @($Value.PSObject.Properties | ForEach-Object { [string]$_.Name })
    if ($actual.Count -ne $Expected.Count) { Fail ($Code + '_KEYS') }
    $set = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($name in $actual) { [void]$set.Add($name) }
    foreach ($name in $Expected) { if (-not $set.Contains($name)) { Fail ($Code + '_KEYS') } }
}

function Write-CreateNewBytes([string]$Path, [byte[]]$Bytes, [string]$Code) {
    if ($script:outputCreated) { Assert-OutputIdentity }
    Assert-TaskNoReparseAncestors $Path
    $stream = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
    } catch {
        if ($script:failureCode) { throw }
        Fail $Code
    } finally { if ($null -ne $stream) { $stream.Dispose() } }
    $check = Read-StableBytes $Path ([long]$Bytes.Length) $Code
    if ((Get-Sha256Bytes $check) -cne (Get-Sha256Bytes $Bytes)) { Fail ($Code + '_VERIFY') }
    if ($script:outputCreated) { Assert-OutputIdentity }
}

function ConvertTo-JsonBytes($Value, [long]$MaximumBytes, [string]$Code) {
    try { $text = ConvertTo-Json -InputObject $Value -Depth 64 -Compress -ErrorAction Stop }
    catch { Fail ($Code + '_SERIALIZE') }
    $raw = $script:utf8.GetBytes($text + "`n")
    if ($raw.Length -gt $MaximumBytes) { Fail ($Code + '_SIZE') }
    return ,$raw
}

function Write-CreateNewJson([string]$Path, $Value, [long]$MaximumBytes, [string]$Code) {
    $raw = ConvertTo-JsonBytes $Value $MaximumBytes $Code
    Write-CreateNewBytes $Path $raw $Code
    return ,$raw
}

function Test-ProcessObject($Process, [string[]]$Arguments, [string]$Code) {
    Assert-ExactKeys $Process @('FilePath','Arguments','ProcessId','ProcessStartTicks','ExitCode',
        'Stdout','Stderr','StdoutLog','StderrLog','ProcessRoot','TerminationProven') $Code
    if (-not ([string]$Process.FilePath).Equals($script:pythonPath, [StringComparison]::OrdinalIgnoreCase) -or
        $Process.Arguments -isnot [System.Array] -or $Process.Arguments.Count -ne $Arguments.Count) {
        Fail ($Code + '_INVOCATION')
    }
    for ($i = 0; $i -lt $Arguments.Count; $i++) {
        if ([string]$Process.Arguments[$i] -cne [string]$Arguments[$i]) { Fail ($Code + '_ARGUMENTS') }
    }
    [void](Assert-JsonInteger $Process.ProcessId 1 ([int]::MaxValue) ($Code + '_PID'))
    [void](Assert-JsonInteger $Process.ProcessStartTicks 1 3155378975999999999L ($Code + '_START'))
    [void](Assert-JsonInteger $Process.ExitCode ([int]::MinValue) ([int]::MaxValue) ($Code + '_EXIT'))
    if ($Process.TerminationProven -isnot [bool]) { Fail ($Code + '_TERMINATION') }
    foreach ($name in @('Stdout','Stderr','StdoutLog','StderrLog','ProcessRoot')) {
        if ($Process.$name -isnot [string]) { Fail ($Code + '_STRING') }
    }
}

function Save-ReaderProcess([string]$Name, $Process, [string[]]$Arguments, [double]$WallMs) {
    Test-ProcessObject $Process $Arguments 'READER_PROCESS'
    $outBytes = $script:utf8.GetBytes([string]$Process.Stdout)
    $errBytes = $script:utf8.GetBytes([string]$Process.Stderr)
    if ($outBytes.Length -gt $script:streamLimit -or $errBytes.Length -gt $script:streamLimit) {
        Fail 'READER_STREAM_LIMIT'
    }
    $receiptRaw = Write-CreateNewJson (Join-Path $script:outputRootFull ('receipts\' + $Name + '.process.json')) `
        $Process 1048576L 'PROCESS_RECEIPT'
    Write-CreateNewBytes (Join-Path $script:outputRootFull ('receipts\' + $Name + '.stdout')) $outBytes 'PROCESS_STDOUT'
    Write-CreateNewBytes (Join-Path $script:outputRootFull ('receipts\' + $Name + '.stderr')) $errBytes 'PROCESS_STDERR'
    return [pscustomobject]@{
        receipt = ('receipts/' + $Name + '.process.json')
        sha256 = Get-Sha256Bytes $receiptRaw
        wallMs = [Math]::Round($WallMs, 3)
        exitCode = [int]$Process.ExitCode
        terminationProven = [bool]$Process.TerminationProven
        processId = [int]$Process.ProcessId
        processStartTicks = [long]$Process.ProcessStartTicks
        stdout = ('receipts/' + $Name + '.stdout')
        stderr = ('receipts/' + $Name + '.stderr')
    }
}

function Get-RunInputSnapshot([string]$Root) {
    $expectedTop = @('host-receipt.json','worker-process.json','worker.stdout','worker.stderr',
        'parent-process.json','parent.stdout','parent.stderr','data')
    $children = @(Get-ChildItem -LiteralPath $Root -Force)
    $names = @($children | ForEach-Object { [string]$_.Name })
    if ($names.Count -ne $expectedTop.Count) { Fail 'RUN_INPUT_ROOT_CENSUS' }
    foreach ($name in $expectedTop) { if ($names -cnotcontains $name) { Fail 'RUN_INPUT_ROOT_CENSUS' } }
    foreach ($item in $children) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail 'RUN_INPUT_REPARSE' }
        if ($item.Name -ceq 'data') {
            if (-not $item.PSIsContainer) { Fail 'RUN_INPUT_DATA_TYPE' }
        } elseif ($item.PSIsContainer) { Fail 'RUN_INPUT_FILE_TYPE' }
    }
    $dataRoot = Join-Path $Root 'data'
    Assert-TaskNoReparseAncestors $dataRoot
    $dataItems = @(Get-ChildItem -LiteralPath $dataRoot -Force)
    if ($dataItems.Count -ne 1 -or $dataItems[0].Name -cne 'sensitivity-result.json' -or
        $dataItems[0].PSIsContainer -or ($dataItems[0].Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Fail 'RUN_INPUT_DATA_CENSUS'
    }
    $files = @('host-receipt.json','worker-process.json','worker.stdout','worker.stderr',
        'parent-process.json','parent.stdout','parent.stderr','data/sensitivity-result.json')
    $map = New-Object 'System.Collections.Generic.Dictionary[string,object]' ([StringComparer]::Ordinal)
    $total = 0L
    foreach ($relative in $files) {
        $path = Join-Path $Root ($relative.Replace('/', '\'))
        $raw = Read-StableBytes $path $script:artifactLimit 'RUN_INPUT_FILE'
        $total += [long]$raw.Length
        if ($total -gt $script:perRunCopyLimit) { Fail 'RUN_INPUT_AGGREGATE_LIMIT' }
        $map.Add($relative, [pscustomobject]@{ raw = $raw; bytes = [long]$raw.Length; sha256 = Get-Sha256Bytes $raw })
    }
    $builder = New-Object Text.StringBuilder
    foreach ($relative in $files) {
        $entry = $map[$relative]
        [void]$builder.Append($relative).Append("`t").Append([long]$entry.bytes).Append("`t").Append($entry.sha256).Append("`n")
    }
    $digestBytes = $script:utf8.GetBytes($builder.ToString())
    $runDigest = Get-Sha256Bytes $digestBytes
    return [pscustomobject]@{ files = $map; totalBytes = $total; runSha256 = $runDigest }
}

function Get-ContextSnapshot() {
    $paths = @(
        @($script:spec.parentReader, $script:spec.parentReaderSha256),
        @($script:spec.parentPins, $script:spec.parentPinsSha256),
        @($script:spec.parentQualification, $script:spec.parentQualificationSha256),
        @($script:spec.transport, $script:spec.transportSha256),
        @($script:spec.blockers, $script:spec.blockersSha256),
        @($script:spec.stages, $script:spec.stagesSha256),
        @($script:spec.cutInput, $script:spec.cutInputSha256)
    )
    $result = New-Object 'System.Collections.Generic.List[object]'
    foreach ($entry in $paths) {
        $path = [string]$entry[0]
        $digest = Get-StableHash $path $script:artifactLimit 'P6_CONTEXT'
        if ($digest.sha256 -cne [string]$entry[1]) { Fail 'P6_CONTEXT_HASH' }
        [void]$result.Add([pscustomobject]@{ path = $path; bytes = $digest.bytes; sha256 = $digest.sha256 })
    }
    return ,$result.ToArray()
}

function Assert-SnapshotUnchanged([string]$Root, $Snapshot, [string]$Code) {
    $current = Get-RunInputSnapshot $Root
    if ($current.totalBytes -ne $Snapshot.totalBytes -or
        $current.runSha256 -cne $Snapshot.runSha256 -or
        $current.files.Count -ne $Snapshot.files.Count) { Fail ($Code + '_CHANGED') }
    foreach ($entry in $Snapshot.files.GetEnumerator()) {
        if (-not $current.files.ContainsKey($entry.Key) -or
            $current.files[$entry.Key].bytes -ne $entry.Value.bytes -or
            $current.files[$entry.Key].sha256 -cne $entry.Value.sha256) {
            Fail ($Code + '_CHANGED')
        }
    }
}

function Assert-ContextUnchanged($Snapshot) {
    foreach ($entry in $Snapshot) {
        $actual = Get-StableHash ([string]$entry.path) $script:artifactLimit 'P6_CONTEXT_POST'
        if ($actual.bytes -ne [long]$entry.bytes -or $actual.sha256 -cne [string]$entry.sha256) {
            Fail 'P6_CONTEXT_CHANGED'
        }
    }
}

function Get-OutputSourceRoots() {
    $roots = New-Object 'System.Collections.Generic.List[string]'
    foreach ($path in @($script:spec.repositoryRoot, $script:spec.parentRun,
        (Split-Path -Parent $script:specPath), (Split-Path -Parent $script:spec.parentReader),
        $script:runRootFull, $PSScriptRoot)) {
        $full = [IO.Path]::GetFullPath([string]$path)
        if (-not @($roots | Where-Object { $_.Equals($full, [StringComparison]::OrdinalIgnoreCase) }).Count) {
            [void]$roots.Add($full)
        }
    }
    foreach ($row in $script:spec.files) {
        $parent = Split-Path -Parent ([string]$row.path)
        if (-not @($roots | Where-Object { $_.Equals($parent, [StringComparison]::OrdinalIgnoreCase) }).Count) {
            [void]$roots.Add($parent)
        }
    }
    return $roots.ToArray()
}

function Assert-OutputRoot([string]$Requested) {
    $full = Get-CanonicalPath $Requested 'OUTPUT_ROOT_PATH'
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not (Test-PathWithin $temp $full) -or
        $full.TrimEnd('\').Equals($temp.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) { Fail 'OUTPUT_ROOT_NOT_TEMP' }
    foreach ($sourceRoot in (Get-OutputSourceRoots)) {
        if ((Test-PathWithin $sourceRoot $full) -or (Test-PathWithin $full $sourceRoot)) {
            Fail 'OUTPUT_ROOT_OVERLAPS_SOURCE'
        }
    }
    $parentFull = Split-Path -Parent $full
    Assert-TaskNoReparseAncestors $parentFull
    try { $parentItem = Get-Item -LiteralPath $parentFull -Force -ErrorAction Stop } catch { Fail 'OUTPUT_PARENT_MISSING' }
    if (-not $parentItem.PSIsContainer -or ($parentItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Fail 'OUTPUT_PARENT_INVALID'
    }
    Assert-TaskNoReparseAncestors $full
    if (Test-Path -LiteralPath $full) { Fail 'OUTPUT_ROOT_EXISTS' }
    try { [void][IO.Directory]::CreateDirectory($full) } catch { Fail 'OUTPUT_ROOT_CREATE' }
    Assert-TaskNoReparseAncestors $full
    $item = Get-Item -LiteralPath $full -Force -ErrorAction Stop
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        @(Get-ChildItem -LiteralPath $full -Force).Count -ne 0) { Fail 'OUTPUT_ROOT_CREATE_IDENTITY' }
    $script:outputCreationTicks = [long]$item.CreationTimeUtc.Ticks
    $script:outputRootFull = $full
    $script:outputCreated = $true
    [void][IO.Directory]::CreateDirectory((Join-Path $full 'fixtures'))
    [void][IO.Directory]::CreateDirectory((Join-Path $full 'receipts'))
    Assert-VerifierNoReparseAncestors (Join-Path $full 'fixtures')
    Assert-VerifierNoReparseAncestors (Join-Path $full 'receipts')
}

function Assert-OutputIdentity() {
    Assert-TaskNoReparseAncestors $script:outputRootFull
    $item = Get-Item -LiteralPath $script:outputRootFull -Force -ErrorAction Stop
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        [long]$item.CreationTimeUtc.Ticks -ne $script:outputCreationTicks) { Fail 'OUTPUT_ROOT_IDENTITY_CHANGED' }
}

function Get-CanonicalSpec([string]$Path, [string]$ExpectedSha) {
    $full = Get-CanonicalPath $Path 'SPEC_PATH'
    $raw = Read-StableBytes $full $script:specLimit 'SPEC_READ'
    if ((Get-Sha256Bytes $raw) -cne $ExpectedSha -or $raw.Length -lt 3 -or $raw[$raw.Length - 1] -ne 10 -or
        [Array]::IndexOf($raw, [byte]13) -ge 0) { Fail 'SPEC_HASH_OR_FRAMING' }
    $parsed = Read-Json $raw 'SPEC' $script:specLimit $true
    if ($parsed.value -isnot [pscustomobject] -or
        $parsed.value.schema -cne 'tsj-canonical-cut-sensitivity-input-v1') { Fail 'SPEC_SCHEMA' }
    return [pscustomobject]@{ path = $full; raw = $raw; sha256 = $ExpectedSha; value = $parsed.value }
}

function Initialize-Inputs() {
    $script:specPath = Get-CanonicalPath $SpecPath 'SPEC_PATH'
    $loaded = Get-CanonicalSpec $script:specPath $SpecSha256
    $script:spec = $loaded.value
    if ($script:spec.selectedPython -isnot [string] -or $script:spec.files -isnot [System.Array]) { Fail 'SPEC_TOOL_FIELDS' }
    $script:readerPath = Get-CanonicalPath (Join-Path $PSScriptRoot 'read_cut_sensitivity.py') 'READER_PATH'
    $script:pythonPath = Get-CanonicalPath ([string]$script:spec.selectedPython) 'PYTHON_PATH'
    $script:modulePath = Get-CanonicalPath (Join-Path ([string]$script:spec.repositoryRoot) 'scripts\VerifierIsolation.psm1') 'MODULE_PATH'
    $specRows = @($script:spec.files)
    $readerPin = @($specRows | Where-Object { [string]$_.path -ceq $script:readerPath })
    $pythonPin = @($specRows | Where-Object { [string]$_.path -ceq $script:pythonPath })
    $modulePin = @($specRows | Where-Object { [string]$_.path -ceq $script:modulePath })
    if ($readerPin.Count -ne 1 -or $pythonPin.Count -ne 1 -or $modulePin.Count -ne 1 -or
        $pythonPin[0].sha256 -cne $script:spec.selectedPythonSha256 -or
        $modulePin[0].sha256 -cne $script:moduleSha256) { Fail 'INPUT_TOOL_PIN' }
    $readerHash = Get-StableHash $script:readerPath 4194304L 'READER_SOURCE'
    $pythonHash = Get-StableHash $script:pythonPath 67108864L 'SELECTED_PYTHON'
    $moduleHash = Get-StableHash $script:modulePath 4194304L 'VERIFIER_ISOLATION'
    if ($readerHash.sha256 -cne [string]$readerPin[0].sha256 -or
        $pythonHash.sha256 -cne [string]$pythonPin[0].sha256 -or
        $moduleHash.sha256 -cne $script:moduleSha256) { Fail 'INPUT_TOOL_HASH' }
    $script:spec = $script:spec
    $script:sourcePins = [pscustomobject]@{
        specPath = $script:specPath; specSha256 = $SpecSha256
        readerPath = $script:readerPath; readerSha256 = $readerHash.sha256
        pythonPath = $script:pythonPath; pythonSha256 = $pythonHash.sha256
        modulePath = $script:modulePath; moduleSha256 = $moduleHash.sha256
    }
    try { Import-Module $script:modulePath -Force -DisableNameChecking -ErrorAction Stop }
    catch { Fail 'VERIFIER_ISOLATION_IMPORT' }
    if (-not (Get-Command Invoke-VerifierBoundedProcess -ErrorAction SilentlyContinue) -or
        -not (Get-Command Assert-VerifierNoReparseAncestors -ErrorAction SilentlyContinue)) { Fail 'VERIFIER_ISOLATION_API' }
    $script:runRootFull = Assert-DirectoryPath $RunRoot 'RUN_ROOT'
    $script:inputSnapshot = Get-RunInputSnapshot $script:runRootFull
    $script:contextSnapshot = Get-ContextSnapshot
    $current = Get-Process -Id $PID -ErrorAction Stop
    $exe = Get-CanonicalPath $current.MainModule.FileName 'OWNER_EXE'
    $start = [long]$current.StartTime.ToUniversalTime().Ticks
    if ($PSVersionTable.PSVersion.Major -ne 5 -or $start -le 0) { Fail 'OWNER_PROCESS_IDENTITY' }
    $exeDigest = Get-StableHash $exe 67108864L 'OWNER_EXE_HASH'
    if (-not $exe.Equals([string]$script:spec.selectedPowerShell, [StringComparison]::OrdinalIgnoreCase) -or
        $exeDigest.sha256 -cne [string]$script:spec.selectedPowerShellSha256) { Fail 'OWNER_EXECUTABLE_BINDING' }
    $script:owner = [pscustomobject][ordered]@{
        schema = 'tsj-canonical-cut-reader-negative-controls-owner-v1'
        classification = 'SYNTHETIC_TRANSPORT_ADAPTED_NOT_CANONICAL'
        pid = [int]$PID
        startTicks = $start
        executable = $exe
        script = (Get-CanonicalPath $PSCommandPath 'OWNER_SCRIPT_PATH')
        scriptSha256 = (Get-Sha256Bytes (Read-StableBytes $PSCommandPath 4194304L 'OWNER_SCRIPT'))
        outputRoot = $null
        outputCreationTicks = 0L
        specSha256 = $SpecSha256
        acceptedRunSnapshot = @($script:inputSnapshot.files.GetEnumerator() | ForEach-Object {
            [pscustomobject]@{ path = [string]$_.Key; bytes = [long]$_.Value.bytes; sha256 = [string]$_.Value.sha256 }
        })
        parentContext = @($script:contextSnapshot)
    }
}

function Get-JsonObjectFromBytes([byte[]]$Raw, [string]$Code, [bool]$OneLine = $true) {
    $parsed = Read-Json $Raw $Code $script:artifactLimit $OneLine
    if ($parsed.value -isnot [pscustomobject]) { Fail ($Code + '_OBJECT') }
    return $parsed.value
}

function Get-WorkerIndex([string]$Stdout) {
    $marker = 'CANONICAL_CUT_SENSITIVITY_COMPLETE '
    $raw = $script:utf8.GetBytes($Stdout)
    if ($raw.Length -gt $script:lineLimit -or -not $Stdout.StartsWith($marker, [StringComparison]::Ordinal) -or
        -not $Stdout.EndsWith("`n", [StringComparison]::Ordinal) -or
        $Stdout.Contains("`r") -or $Stdout.IndexOf("`n") -ne ($Stdout.Length - 1)) { Fail 'SOURCE_WORKER_INDEX' }
    $indexRaw = $script:utf8.GetBytes($Stdout.Substring($marker.Length))
    $index = Get-JsonObjectFromBytes $indexRaw 'SOURCE_WORKER_INDEX'
    return [pscustomobject]@{ marker = $marker; index = $index }
}

function Convert-ObjectToRaw($Value, [long]$Limit, [string]$Code) {
    return ConvertTo-JsonBytes $Value $Limit $Code
}

function Set-ResultMutation([string]$Mutation, $Result, $Index) {
    switch ($Mutation) {
        'control' { }
        'bfs-reachable-invert' {
            $Result.cases[0].bfs.reachable = -not [bool]$Result.cases[0].bfs.reachable
        }
        'minimum-via-mismatch' {
            $current = $Result.cases[0].minimumVias.minimumViaCount
            if ($null -eq $current) { $Result.cases[0].minimumVias.minimumViaCount = 0 }
            else { $Result.cases[0].minimumVias.minimumViaCount = [long]$current + 1L }
        }
        'changed-bit-count' { $Result.cases[1].changedBitCount = 3 }
        'opened-edge-owner-query' {
            if (@($Result.cases[1].openedEdges).Count -lt 1) { Fail 'MUTATION_OPENED_EDGE_FIXTURE' }
            $edge = $Result.cases[1].openedEdges[0]
            $edge.ownerNetId = 'SYNTHETIC_WRONG_OWNER'
            $edge.ordinal = [int]$edge.ordinal + 1
        }
        'preserved-vias-false' { $Result.cases[1].preserved.vias = $false }
        'graph-width-mismatch' { $Result.graph.width = [int]$Result.graph.width + 1 }
        'cut-owner-count-mismatch' {
            $properties = @($Result.cut.ownerCounts.PSObject.Properties)
            if ($properties.Count -lt 1) { Fail 'MUTATION_OWNER_COUNT_FIXTURE' }
            $name = [string]$properties[0].Name
            $Result.cut.ownerCounts.$name = [int]$Result.cut.ownerCounts.$name + 1
        }
        'acceptance-route-true' { $Result.acceptance.route = $true }
        'analysis-copy-entries' {
            $Result.work.analysis.copyEntries = [long]$Result.work.analysis.copyEntries + 1L
            $Index.work.copyEntries = [long]$Index.work.copyEntries + 1L
        }
        'missing-case' { $Result.cases = @($Result.cases | Select-Object -First 3) }
        'result-eof' { }
        'index-group-delta' { $Index.work.groups = [long]$Index.work.groups + 1L }
        default { Fail 'UNKNOWN_SYNTHETIC_MUTATION' }
    }
}

function Write-OverwriteSynthetic([string]$Path, [byte[]]$Bytes, [string]$Code) {
    Assert-TaskNoReparseAncestors $Path
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Fail $Code }
    try { [IO.File]::WriteAllBytes($Path, $Bytes) } catch { Fail $Code }
    $actual = Read-StableBytes $Path $script:artifactLimit $Code
    if ((Get-Sha256Bytes $actual) -cne (Get-Sha256Bytes $Bytes)) { Fail ($Code + '_VERIFY') }
}

function Build-SyntheticFixture([string]$CaseName, [string]$Mutation, [string]$CaseRoot) {
    $fixture = New-Object 'System.Collections.Generic.Dictionary[string,byte[]]' ([StringComparer]::Ordinal)
    foreach ($entry in $script:inputSnapshot.files.GetEnumerator()) { $fixture.Add([string]$entry.Key, [byte[]]$entry.Value.raw) }
    $origParent = Get-JsonObjectFromBytes $fixture['parent-process.json'] 'SOURCE_PARENT_PROCESS'
    $origWorker = Get-JsonObjectFromBytes $fixture['worker-process.json'] 'SOURCE_WORKER_PROCESS'
    $origHost = Get-JsonObjectFromBytes $fixture['host-receipt.json'] 'SOURCE_HOST_RECEIPT'
    $workerStdoutText = $script:strictUtf8.GetString($fixture['worker.stdout'])
    if ($origWorker.Arguments -isnot [System.Array] -or $origWorker.Arguments.Count -ne 8 -or
        [string]$origWorker.Arguments[0] -cne '-B' -or
        [string]$origWorker.Arguments[1] -cne [string]$script:spec.worker -or
        [string]$origWorker.Arguments[2] -cne 'ANALYZE' -or
        [string]$origWorker.Arguments[3] -cne $script:specPath -or
        [string]$origWorker.Arguments[4] -cne $SpecSha256 -or
        [string]$origWorker.Arguments[5] -cne (Join-Path $script:runRootFull 'data') -or
        [string]$origWorker.Arguments[6] -cne (Join-Path $script:runRootFull 'parent-process.json') -or
        [string]$origWorker.Arguments[7] -cne (Get-Sha256Bytes $fixture['parent-process.json'])) {
        Fail 'SOURCE_WORKER_ARGUMENTS'
    }
    if ($origHost.worker.receipt -cne 'worker-process.json' -or
        $origHost.parent.receipt -cne 'parent-process.json' -or
        $origHost.parent.sha256 -cne (Get-Sha256Bytes $fixture['parent-process.json'])) { Fail 'SOURCE_HOST_RECEIPTS' }
    if ((Get-Sha256Bytes $fixture['parent-process.json']) -cne $script:inputSnapshot.files['parent-process.json'].sha256 -or
        (Get-Sha256Bytes $fixture['parent.stdout']) -cne $script:inputSnapshot.files['parent.stdout'].sha256 -or
        (Get-Sha256Bytes $fixture['parent.stderr']) -cne $script:inputSnapshot.files['parent.stderr'].sha256) {
        Fail 'PARENT_RECEIPT_SOURCE_CHANGED'
    }
    $indexDoc = Get-WorkerIndex $workerStdoutText
    $index = $indexDoc.index
    if ($index.result.path -cne 'sensitivity-result.json' -or
        $index.result.sha256 -cne (Get-Sha256Bytes $fixture['data/sensitivity-result.json']) -or
        [long]$index.result.bytes -ne [long]$fixture['data/sensitivity-result.json'].Length) { Fail 'SOURCE_RESULT_BINDING' }
    $resultParsed = Get-JsonObjectFromBytes $fixture['data/sensitivity-result.json'] 'SOURCE_RESULT'
    Set-ResultMutation $Mutation $resultParsed $index
    $resultRaw = if ($Mutation -ceq 'result-eof') {
        $withoutLf = ConvertTo-JsonBytes $resultParsed $script:lineLimit 'MUTATED_RESULT'
        [byte[]]$withoutLf[0..($withoutLf.Length - 2)]
    } else { ConvertTo-JsonBytes $resultParsed $script:lineLimit 'MUTATED_RESULT' }
    $fixture['data/sensitivity-result.json'] = $resultRaw
    $index.result.bytes = [long]$resultRaw.Length
    $index.result.sha256 = Get-Sha256Bytes $resultRaw
    $index.aggregateBytes = [long]$resultRaw.Length
    $indexRaw = ConvertTo-JsonBytes $index $script:lineLimit 'SYNTHETIC_INDEX'
    $newWorkerStdout = $indexDoc.marker + $script:strictUtf8.GetString($indexRaw)
    $fixture['worker.stdout'] = $script:utf8.GetBytes($newWorkerStdout)
    $origWorker.Arguments[5] = [IO.Path]::GetFullPath((Join-Path $CaseRoot 'data'))
    $origWorker.Arguments[6] = [IO.Path]::GetFullPath((Join-Path $CaseRoot 'parent-process.json'))
    $origWorker.Stdout = $newWorkerStdout
    $newWorkerRaw = ConvertTo-JsonBytes $origWorker 1048576L 'SYNTHETIC_WORKER_PROCESS'
    $fixture['worker-process.json'] = $newWorkerRaw
    $origHost.worker.sha256 = Get-Sha256Bytes $newWorkerRaw
    $fixture['host-receipt.json'] = ConvertTo-JsonBytes $origHost 1048576L 'SYNTHETIC_HOST_RECEIPT'
    $fixtureTotal = 0L
    foreach ($entry in $fixture.GetEnumerator()) { $fixtureTotal += [long]$entry.Value.Length }
    if ($fixtureTotal -gt $script:perRunCopyLimit) { Fail 'SYNTHETIC_FIXTURE_AGGREGATE_LIMIT' }

    [void][IO.Directory]::CreateDirectory($CaseRoot)
    Assert-TaskNoReparseAncestors $CaseRoot
    [void][IO.Directory]::CreateDirectory((Join-Path $CaseRoot 'data'))
    foreach ($entry in $fixture.GetEnumerator()) {
        $destination = Join-Path $CaseRoot ($entry.Key.Replace('/', '\'))
        Write-CreateNewBytes $destination $entry.Value 'SYNTHETIC_FILE_COPY'
    }
    return [pscustomobject]@{
        copiedBytes = $fixtureTotal
        workerProcessSha256 = Get-Sha256Bytes $newWorkerRaw
        parentProcessSha256 = Get-Sha256Bytes $fixture['parent-process.json']
        classification = if ($CaseName -ceq 'copy-control') {
            'SYNTHETIC_COPY_READER_CONTROL_NOT_REAL_RUN'
        } else { 'SYNTHETIC_TRANSPORT_ADAPTED_NOT_CANONICAL' }
    }
}

function Get-StaticReason([string]$Stderr) {
    if ($Stderr -cmatch '\A([A-Z0-9_]+)\r\n\z') { return [string]$matches[1] }
    return $null
}

function Invoke-ReaderFixture($Spec, [string]$Name, [string]$Mutation, [string]$ExpectedReason, [bool]$ExpectSuccess) {
    $row = [pscustomobject][ordered]@{
        case = $Name
        mutation = $Mutation
        classification = if ($Name -ceq 'copy-control') {
            'SYNTHETIC_COPY_READER_CONTROL_NOT_REAL_RUN'
        } else { 'SYNTHETIC_TRANSPORT_ADAPTED_NOT_CANONICAL' }
        expectedReason = $ExpectedReason
        actualReason = $null
        status = 'NOT_RUN'
        exitCode = $null
        terminationProven = $false
        processId = $null
        processStartTicks = $null
        wallMs = $null
        copiedBytes = 0L
        fixture = $null
        processReceipt = $null
        stdoutReceipt = $null
        stderrReceipt = $null
        error = $null
    }
    [void]$script:caseResults.Add($row)
    $caseRoot = Join-Path (Join-Path $script:outputRootFull 'fixtures') $Name
    $row.fixture = $caseRoot
    try {
        $fixtureInfo = Build-SyntheticFixture $Name $Mutation $caseRoot
        $row.copiedBytes = [long]$fixtureInfo.copiedBytes
        $script:copiedBytesTotal += [long]$fixtureInfo.copiedBytes
        if ($script:copiedBytesTotal -gt $script:totalCopyLimit) { Fail 'TOTAL_COPY_LIMIT' }
        $readerArgs = @('-B',$script:readerPath,$caseRoot,$script:specPath,$SpecSha256,'ANALYZE')
        $watch = [Diagnostics.Stopwatch]::StartNew()
        if ($script:outerWatch.Elapsed.TotalMilliseconds + 180000.0 + 15000.0 -gt 1800000.0) {
            Fail 'OUTER_READER_RESERVE'
        }
        $process = Invoke-VerifierBoundedProcess $script:pythonPath $readerArgs 180000
        $watch.Stop()
        $row.wallMs = [Math]::Round($watch.Elapsed.TotalMilliseconds,3)
        $saved = Save-ReaderProcess $Name $process $readerArgs $row.wallMs
        $row.processReceipt = $saved.receipt
        $row.stdoutReceipt = $saved.stdout
        $row.stderrReceipt = $saved.stderr
        $row.exitCode = [int]$process.ExitCode
        $row.terminationProven = [bool]$process.TerminationProven
        $row.processId = [int]$process.ProcessId
        $row.processStartTicks = [long]$process.ProcessStartTicks
        if ($row.wallMs -gt 180000.0 -or -not $row.terminationProven) { Fail 'READER_PROCESS_LIMIT_OR_TERMINATION' }
        if ($ExpectSuccess) {
            if ($row.exitCode -ne 0 -or -not $row.terminationProven -or
                -not [String]::IsNullOrEmpty([string]$process.Stderr)) {
                $row.status = 'FAIL_CONTROL_PROCESS'
                $row.actualReason = Get-StaticReason ([string]$process.Stderr)
            } else {
                $outputRaw = $script:utf8.GetBytes([string]$process.Stdout)
                # Preserve actual selected-reader CRLF in its process/stream
                # receipts; normalize only this local JSON parse view.
                $text = [string]$process.Stdout
                if (-not $text.EndsWith("`r`n", [StringComparison]::Ordinal) -or
                    $text.IndexOf("`r") -ne $text.Length - 2 -or
                    $text.IndexOf("`n") -ne $text.Length - 1 -or
                    $outputRaw.Length -gt 65536) { Fail 'CONTROL_STDOUT_FRAMING' }
                $localRaw = $script:utf8.GetBytes($text.Substring(0, $text.Length - 2) + "`n")
                $readResult = Get-JsonObjectFromBytes $localRaw 'CONTROL_RESULT'
                if ($readResult.status -ceq 'PASS_CONDITIONAL_SENSITIVITY_ONLY' -and
                    $readResult.mode -ceq 'ANALYZE' -and $readResult.specSha256 -ceq $SpecSha256) {
                    $row.status = 'PASS_DATA_READER_CONTROL_ONLY'
                } else {
                    $row.status = 'FAIL_CONTROL_RESULT'
                    $row.actualReason = if ($null -ne $readResult.status) { [string]$readResult.status } else { 'CONTROL_RESULT_SHAPE' }
                }
            }
        } else {
            $row.actualReason = Get-StaticReason ([string]$process.Stderr)
            if ($row.exitCode -eq 2 -and $row.terminationProven -and
                [String]::IsNullOrEmpty([string]$process.Stdout) -and
                [string]$process.Stderr -ceq ($ExpectedReason + "`r`n") -and
                $row.actualReason -ceq $ExpectedReason) {
                $row.status = 'PASS_EXPECTED_STATIC_REJECTION'
            } else {
                $row.status = 'FAIL_UNEXPECTED_READER_RESULT'
                $row.error = 'EXPECTED_STATIC_REASON_NOT_MATCHED'
            }
        }
    } catch {
        $row.status = 'FAIL_FIXTURE_OR_LAUNCH'
        $message = [string]$_.Exception.Message
        $row.error = if ($message -match '^[A-Z0-9_]{1,96}$') { $message } else { 'FIXTURE_OR_LAUNCH_FAILURE' }
    }
    return ($row.status -like 'PASS_*')
}

function Save-ControllerResult([string]$Status) {
    $caseRows = $script:caseResults.ToArray()
    $result = [pscustomobject][ordered]@{
        schema = 'tsj-canonical-cut-reader-negative-controls-v1'
        status = $Status
        classification = 'SYNTHETIC_TRANSPORT_ADAPTED_NOT_CANONICAL'
        acceptedRunSha256 = $script:inputSnapshot.runSha256
        specSha256 = $SpecSha256
        readerSha256 = $script:sourcePins.readerSha256
        selectedPythonSha256 = $script:sourcePins.pythonSha256
        verifierIsolationSha256 = $script:sourcePins.moduleSha256
        owner = 'owner.json'
        cases = $caseRows
        caseCount = $caseRows.Count
        copiedBytes = [long]$script:copiedBytesTotal
        failures = $script:failureRows.ToArray()
        outerLimitMs = 1800000
        outerElapsedMs = [Math]::Round($script:outerWatch.Elapsed.TotalMilliseconds,3)
        outputRetained = $true
    }
    [void](Write-CreateNewJson (Join-Path $script:outputRootFull 'results.json') $result 1048576L 'RESULTS_JSON')
}

$caseSpecs = @(
    [pscustomobject]@{ Name='copy-control'; Mutation='control'; Expected='PASS_CONDITIONAL_SENSITIVITY_ONLY'; Success=$true },
    [pscustomobject]@{ Name='control-bfs-reachable-invert'; Mutation='bfs-reachable-invert'; Expected='CASE_RESULT_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='control-minimum-via-mismatch'; Mutation='minimum-via-mismatch'; Expected='CASE_RESULT_MISMATCH'; Success=$false },
    # Claimed changedBitCount is compared in expected_static; the dedicated
    # CASE_CHANGED_BIT_COUNT_INVALID guard covers the independently derived set.
    [pscustomobject]@{ Name='sense-changed-bit-count'; Mutation='changed-bit-count'; Expected='CASE_RESULT_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='sense-opened-edge-owner-query'; Mutation='opened-edge-owner-query'; Expected='CASE_RESULT_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='sense-preserved-vias-false'; Mutation='preserved-vias-false'; Expected='CASE_RESULT_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='graph-width-mismatch'; Mutation='graph-width-mismatch'; Expected='GRAPH_RESULT_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='cut-owner-count-mismatch'; Mutation='cut-owner-count-mismatch'; Expected='CUT_RESULT_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='acceptance-route-true'; Mutation='acceptance-route-true'; Expected='RESULT_ACCEPTANCE_INVALID'; Success=$false },
    [pscustomobject]@{ Name='analysis-copy-entry-delta'; Mutation='analysis-copy-entries'; Expected='ANALYSIS_WORK_MISMATCH'; Success=$false },
    [pscustomobject]@{ Name='missing-final-case'; Mutation='missing-case'; Expected='CASE_CENSUS_INVALID'; Success=$false },
    [pscustomobject]@{ Name='result-missing-final-lf'; Mutation='result-eof'; Expected='JSON_FRAMING_INVALID'; Success=$false },
    [pscustomobject]@{ Name='worker-index-group-delta'; Mutation='index-group-delta'; Expected='HOST_WORK_MISMATCH'; Success=$false }
)

try {
    $script:phase = 'INPUTS'
    Initialize-Inputs
    $script:phase = 'OUTPUT_ROOT'
    $requestedOutput = Get-CanonicalPath $OutputRoot 'OUTPUT_ROOT_PATH'
    Assert-OutputRoot $requestedOutput
    $script:owner.outputRoot = $script:outputRootFull
    $script:owner.outputCreationTicks = $script:outputCreationTicks
    [void](Write-CreateNewJson (Join-Path $script:outputRootFull 'owner.json') $script:owner 1048576L 'OWNER_RECEIPT')
    $script:phase = 'FIXTURES'
    $runFilesAfter = Get-RunInputSnapshot $script:runRootFull
    if ($runFilesAfter.totalBytes -ne $script:inputSnapshot.totalBytes) { Fail 'ACCEPTED_RUN_CHANGED' }
    foreach ($entry in $script:inputSnapshot.files.GetEnumerator()) {
        if ($runFilesAfter.files[$entry.Key].sha256 -cne $entry.Value.sha256) { Fail 'ACCEPTED_RUN_CHANGED' }
    }
    foreach ($caseSpec in $caseSpecs) {
        $script:phase = 'CASE_' + $caseSpec.Name.ToUpperInvariant().Replace('-', '_')
        if (-not (Invoke-ReaderFixture $caseSpec $caseSpec.Name $caseSpec.Mutation $caseSpec.Expected $caseSpec.Success)) {
            [void]$script:failureRows.Add($caseSpec.Name + ':EXPECTED_REASON_OR_CONTROL_MISMATCH')
            $script:failureCode = 'EXPECTED_READER_RESULT_MISMATCH'
            break
        }
        Assert-SnapshotUnchanged $script:runRootFull $script:inputSnapshot 'ACCEPTED_RUN'
        Assert-ContextUnchanged $script:contextSnapshot
    }
    $script:phase = 'POSTCHECK'
    Assert-SnapshotUnchanged $script:runRootFull $script:inputSnapshot 'ACCEPTED_RUN'
    Assert-ContextUnchanged $script:contextSnapshot
    $readerAfter = Get-StableHash $script:readerPath 4194304L 'READER_POST'
    $pythonAfter = Get-StableHash $script:pythonPath 67108864L 'PYTHON_POST'
    $moduleAfter = Get-StableHash $script:modulePath 4194304L 'MODULE_POST'
    $specAfter = Get-StableHash $script:specPath $script:specLimit 'SPEC_POST'
    if ($readerAfter.sha256 -cne $script:sourcePins.readerSha256 -or
        $pythonAfter.sha256 -cne $script:sourcePins.pythonSha256 -or
        $moduleAfter.sha256 -cne $script:sourcePins.moduleSha256 -or
        $specAfter.sha256 -cne $SpecSha256) { Fail 'SOURCE_OR_SPEC_CHANGED' }
    if ($null -eq $script:failureCode) {
        $script:phase = 'FINAL'
        $script:outerWatch.Stop()
        if ($script:outerWatch.Elapsed.TotalMilliseconds -gt 1800000.0) { Fail 'OUTER_WALL_LIMIT' }
    }
} catch {
    if ($null -eq $script:failureCode) {
        $message = [string]$_.Exception.Message
        if ($message -match '^[A-Z0-9_]{1,96}$') { $script:failureCode = $message }
        else { $script:failureCode = 'READER_NEGATIVE_' + $script:phase + '_FAILURE' }
    }
}

if ($script:outputCreated) {
    try {
        $finalStatus = if ($null -eq $script:failureCode -and $script:caseResults.Count -eq $caseSpecs.Count) {
            'PASS_ALL_EXPECTED_READER_REJECTIONS'
        } else { 'FAIL' }
        if ($null -ne $script:failureCode) { [void]$script:failureRows.Add($script:failureCode) }
        $script:outerWatch.Stop()
        Save-ControllerResult $finalStatus
    } catch {
        if ($null -eq $script:failureCode) { $script:failureCode = 'RESULT_PRESERVATION_FAILURE' }
    }
}

if ($null -ne $script:failureCode) {
    [Console]::Error.WriteLine('READER_NEGATIVE_CONTROLS_FAILURE ' + $script:failureCode)
    exit 1
}
[Console]::Out.WriteLine('READER_NEGATIVE_CONTROLS_PASS')
exit 0
