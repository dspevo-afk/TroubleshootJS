[CmdletBinding()]
param([switch]$Live, [string]$OutputPath = '', [string]$RepositoryRoot = '')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = if ([String]::IsNullOrWhiteSpace($RepositoryRoot)) {
    [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
} else {
    [IO.Path]::GetFullPath($RepositoryRoot)
}
Import-Module (Join-Path $root 'scripts/VerifierIsolation.psm1') -Force
$isolationModule = Get-Module VerifierIsolation
# Exercise private boundaries in their real module scope without exporting them.
function Test-VerifierSameRootPreviewListenerEligibility {
    & $script:isolationModule { Test-VerifierSameRootPreviewListenerEligibility @args } @args
}
function Get-VerifierPortLeaseBoundOwnershipProof {
    & $script:isolationModule { Get-VerifierPortLeaseBoundOwnershipProof @args } @args
}
function Test-VerifierLiveListenerInspectionAuthorization {
    & $script:isolationModule { Test-VerifierLiveListenerInspectionAuthorization @args } @args
}
function Assert-VerifierPreviewIdentitySchema {
    & $script:isolationModule { Assert-VerifierPreviewIdentitySchema @args } @args
}
$checks = 0
$observations = [Collections.Generic.List[object]]::new()
$hostObservations = [Collections.Generic.List[object]]::new()
$launchRecord = $null
function Assert-Check($Condition, [string]$Message) {
    if ($Condition -isnot [bool]) {
        $callSite = @(Get-PSCallStack) | Select-Object -Skip 1 -First 1
        $actualType = if ($null -eq $Condition) { 'null' } else { $Condition.GetType().FullName }
        throw "FAIL preview-listener-identity: assertion condition at line $($callSite.ScriptLineNumber) was $actualType, not Boolean."
    }
    if (-not $Condition) { throw "FAIL preview-listener-identity: $Message" }
    $script:checks++
}

function Invoke-PreviewBuildProvenanceCanary {
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $fixtureRoot = Join-Path $tempRoot ('TroubleshootJS\preview-build-identity-' +
        [Guid]::NewGuid().ToString('N'))
    try {
        foreach ($directory in @('src', 'scripts', '.tools\gwt-2.7.0', 'war\circuitjs1')) {
            New-Item -ItemType Directory -Path (Join-Path $fixtureRoot $directory) `
                -Force -ErrorAction Stop | Out-Null
        }
        Set-Content -LiteralPath (Join-Path $fixtureRoot 'src\Sample.java') `
            -Value 'class Sample { int value = 1; }' -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $fixtureRoot 'scripts\build.ps1') `
            -Value 'param()' -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $fixtureRoot '.tools\gwt-2.7.0\gwt-dev.jar') `
            -Value 'compiler-input' -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $fixtureRoot 'war\circuitjs1\circuitjs1.nocache.js') `
            -Value 'bootstrap-v1' -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $fixtureRoot 'war\circuitjs1\permutation.cache.js') `
            -Value 'permutation-v1' -Encoding ASCII

        function Get-ExpectedPlayerFixtureInputDigest([string]$VariantText) {
            $records = New-Object Collections.Generic.List[string]
            foreach ($record in @(
                    'contract=troubleshootjs-production-build-identity-v1',
                    'gwtVersion=2.7.0',
                    'target=Compile',
                    'style=OBF',
                    ('variant=' + $VariantText),
                    'module=com.lushprojects.circuitjs1.circuitjs1')) {
                [void]$records.Add($record)
            }
            foreach ($file in @(
                    [pscustomobject]@{ Record = 'src/Sample.java'; Path = (Join-Path $fixtureRoot 'src\Sample.java') }
                    [pscustomobject]@{ Record = 'build/build.ps1'; Path = (Join-Path $fixtureRoot 'scripts\build.ps1') }
                    [pscustomobject]@{ Record = 'classpath/gwt-dev.jar'; Path = (Join-Path $fixtureRoot '.tools\gwt-2.7.0\gwt-dev.jar') })) {
                $hasher = [Security.Cryptography.SHA256]::Create()
                try {
                    $digest = [BitConverter]::ToString(
                        $hasher.ComputeHash([IO.File]::ReadAllBytes($file.Path))).Replace('-', '').ToLowerInvariant()
                } finally {
                    $hasher.Dispose()
                }
                [void]$records.Add($file.Record + '=' + $digest)
            }
            $hasher = [Security.Cryptography.SHA256]::Create()
            try {
                $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($records -join "`n"))
                return [BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
            } finally {
                $hasher.Dispose()
            }
        }

        $initial = Get-VerifierProductionBuildIdentity -RepositoryRoot $fixtureRoot `
            -Variant Player -Style OBF
        Assert-Check ($initial.InputFileCount -eq 3 -and $initial.OutputFileCount -eq 2) `
            'Production build identity did not cover the source, build script, compiler jar, and deployed files.'
        $expectedCanonicalPlayerDigest = Get-ExpectedPlayerFixtureInputDigest 'Player'
        Assert-Check ($initial.InputDigest -ceq $expectedCanonicalPlayerDigest) `
            'Canonical Player build input identity changed from the independent fixture records.'

        $lowercasePlayer = Get-VerifierProductionBuildIdentity -RepositoryRoot $fixtureRoot `
            -Variant player -Style OBF
        $expectedLowercasePlayerDigest = Get-ExpectedPlayerFixtureInputDigest 'player'
        Assert-Check ($lowercasePlayer.InputDigest -ceq $expectedLowercasePlayerDigest -and
            $lowercasePlayer.OutputDigest -ceq $initial.OutputDigest -and
            $lowercasePlayer.InputFileCount -eq $initial.InputFileCount -and
            $lowercasePlayer.OutputFileCount -eq $initial.OutputFileCount) `
            'Lowercase Player variant did not use the independent Player-module identity while preserving fixture output identity.'

        # Run the former case-sensitive selector from a temporary copy only.
        # This negative canary proves the independent assertion detects the old
        # Developer-module label without changing the imported repository module.
        $liveModulePath = Join-Path $root 'scripts\VerifierIsolation.psm1'
        $oldComparisonModulePath = Join-Path $fixtureRoot 'scripts\VerifierIsolationOldComparison.psm1'
        $liveModuleText = [IO.File]::ReadAllText($liveModulePath)
        $fixedComparison = '$moduleName = if ($Variant -eq ''Player'') {'
        $oldComparison = '$moduleName = if ($Variant -ceq ''Player'') {'
        if (-not $liveModuleText.Contains($fixedComparison)) {
            throw 'Could not locate the fixed Player module selector for the isolated negative canary.'
        }
        [IO.File]::WriteAllText($oldComparisonModulePath,
            $liveModuleText.Replace($fixedComparison, $oldComparison), [Text.UTF8Encoding]::new($false))
        $oldComparisonModule = Import-Module -Name $oldComparisonModulePath -Force -PassThru -Scope Local
        try {
            $oldComparisonIdentity = & $oldComparisonModule {
                param($repositoryRoot)
                Get-VerifierProductionBuildIdentity -RepositoryRoot $repositoryRoot `
                    -Variant player -Style OBF
            } $fixtureRoot
            Assert-Check ($oldComparisonIdentity.InputDigest -cne $expectedLowercasePlayerDigest) `
                'The copied old case-sensitive selector unexpectedly passed the independent lowercase Player canary.'
        } finally {
            Remove-Module -ModuleInfo $oldComparisonModule -Force -ErrorAction SilentlyContinue
        }

        Set-Content -LiteralPath (Join-Path $fixtureRoot 'src\Sample.java') `
            -Value 'class Sample { int value = 2; }' -Encoding ASCII
        $changedInput = Get-VerifierProductionBuildIdentity -RepositoryRoot $fixtureRoot `
            -Variant Player -Style OBF
        Assert-Check ($changedInput.InputDigest -cne $initial.InputDigest -and
            $changedInput.OutputDigest -ceq $initial.OutputDigest) `
            'Source changes were not separated from the currently deployed output identity.'

        Set-Content -LiteralPath (Join-Path $fixtureRoot 'war\circuitjs1\permutation.cache.js') `
            -Value 'permutation-v2' -Encoding ASCII
        $changedOutput = Get-VerifierProductionBuildIdentity -RepositoryRoot $fixtureRoot `
            -Variant Player -Style OBF
        Assert-Check ($changedOutput.InputDigest -ceq $changedInput.InputDigest -and
            $changedOutput.OutputDigest -cne $changedInput.OutputDigest) `
            'Deployed output changes were not independently detected.'

        $developer = Get-VerifierProductionBuildIdentity -RepositoryRoot $fixtureRoot `
            -Variant Developer -Style OBF
        Assert-Check ($developer.Variant -ceq 'Developer' -and
            $developer.InputDigest -cne $changedOutput.InputDigest) `
            'Developer build identity was not distinct from the Player build variant.'
    } finally {
        if (Test-Path -LiteralPath $fixtureRoot) {
            Remove-VerifierOwnedTree $tempRoot $fixtureRoot
        }
    }
}

function Invoke-RawPreviewRequest([int]$Port, [string[]]$HostHeaders,
        [switch]$OmitHost) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $client.Connect([Net.IPAddress]::Loopback, $Port)
        $client.ReceiveTimeout = 5000
        $client.SendTimeout = 5000
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        $stream.WriteTimeout = 5000
        $request = "GET /__tsj/verify-identity HTTP/1.1`r`n"
        if (-not $OmitHost) {
            foreach ($hostHeader in $HostHeaders) {
                $request += "Host: $hostHeader`r`n"
            }
        }
        $request += "Connection: close`r`n`r`n"
        $requestBytes = [Text.Encoding]::ASCII.GetBytes($request)
        $stream.Write($requestBytes, 0, $requestBytes.Length)
        $stream.Flush()
        $memory = [IO.MemoryStream]::new()
        $buffer = New-Object byte[] 4096
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $memory.Write($buffer, 0, $read)
            if ($memory.Length -gt 65536) { throw 'Raw preview response exceeded 64 KiB.' }
        }
        $responseText = [Text.Encoding]::UTF8.GetString($memory.ToArray())
        $firstLine = ($responseText -split "`r`n", 2)[0]
        $statusMatch = [regex]::Match($firstLine, '^HTTP/1\.1 (?<status>[0-9]{3}) ')
        if (-not $statusMatch.Success) { throw "Raw preview response was malformed: $firstLine" }
        $bodyStart = $responseText.IndexOf("`r`n`r`n", [StringComparison]::Ordinal)
        if ($bodyStart -lt 0) { throw 'Raw preview response omitted its header terminator.' }
        return [pscustomobject]@{
            StatusCode = [int]$statusMatch.Groups['status'].Value
            Body = $responseText.Substring($bodyStart + 4)
        }
    } finally {
        $client.Close()
    }
}

Invoke-PreviewBuildProvenanceCanary
$process = Get-Process -Id $PID
$owner = [pscustomobject]@{
    ProcessId = $PID; ProcessStartTicks = $process.StartTime.ToUniversalTime().Ticks
    ProcessParentProcessId = 1; ProcessParentProcessStartTicks = 1L
    ProcessCommandLine = 'preview-canary'; Script = 'preview.ps1'; Port = 54321
    RunId = 'preview-canary'; Nonce = 'nonce-canary'; Owner = 'run'
    IdentityVerified = $true; Process = $process
}
$context = [pscustomobject]@{Server=$owner;RunId=$owner.RunId;PreviewNonce=$owner.Nonce}
$listener = [pscustomobject]@{ListenerOwnerKind='user-process';Port=$owner.Port;
    ProcessId=$owner.ProcessId;ProcessStartTicks=$owner.ProcessStartTicks}
$inspection = [pscustomobject]@{ListenerOwnerKind='user-process';Listeners=@($listener)}
function Test-Eligible {
    Test-VerifierSameRootPreviewListenerEligibility $context $inspection @($listener) `
        $owner $owner.ProcessId $owner.ProcessStartTicks $owner
}
Assert-Check (Test-Eligible) 'Complete same-root preview was not eligible.'
foreach ($case in @(
    @('Owner','caller'), @('IdentityVerified',$false), @('IdentityVerified','true'),
    @('Process',$null), @('ProcessId','1'), @('ProcessStartTicks',0L),
    @('ProcessParentProcessId',0), @('ProcessParentProcessStartTicks','1'),
    @('ProcessCommandLine',''), @('Script',''), @('RunId','foreign'), @('Nonce','foreign')
)) {
    $name=$case[0]; $saved=$owner.$name
    try { $owner.$name=$case[1]; Assert-Check (-not (Test-Eligible)) "Malformed owner $name accepted." }
    finally { $owner.$name=$saved }
}
foreach ($case in @(@('Port',54322), @('ProcessId',($PID+1)),
        @('ProcessStartTicks',($owner.ProcessStartTicks+1)), @('ProcessId','1'),
        @('ListenerOwnerKind','kernel-transport'))) {
    $name=$case[0]; $saved=$listener.$name
    try { $listener.$name=$case[1]; Assert-Check (-not (Test-Eligible)) "Foreign listener $name accepted." }
    finally { $listener.$name=$saved }
}
foreach ($name in @('Profile','DirectProcessOwner')) {
    try { Add-Member -InputObject $owner -NotePropertyName $name -NotePropertyValue 'foreign'
        Assert-Check (-not (Test-Eligible)) "Non-preview owner $name accepted." }
    finally { $owner.PSObject.Properties.Remove($name) }
}
$other=$owner.PSObject.Copy()
Assert-Check (-not (Test-VerifierSameRootPreviewListenerEligibility $context $inspection `
    @($listener) $other $owner.ProcessId $owner.ProcessStartTicks $other)) 'Copied owner accepted.'
Assert-Check (-not (Test-VerifierSameRootPreviewListenerEligibility $context $inspection `
    @() $owner $owner.ProcessId $owner.ProcessStartTicks $owner)) 'Empty listener set accepted.'
Assert-Check (Test-Eligible) 'Negative canaries changed the healthy fixture.'
$cleanup=$null
if ($Live) {
    $context=$null
    try {
        $context=New-VerifierRunContext $root ''
        [void](Start-VerifierOwnedPreview $context (Join-Path $root 'scripts/preview.ps1') 90 $true)
        $owner=$context.Server
        $launchProcess = Get-VerifierCurrentProcessRecordById $owner.ProcessId
        Assert-Check ($null -ne $launchProcess -and
            [long]$launchProcess.ProcessStartTicks -eq [long]$owner.ProcessStartTicks -and
            -not [String]::IsNullOrWhiteSpace([string]$launchProcess.ExecutablePath) -and
            [bool]$owner.Process.StartInfo.CreateNoWindow -and
            (Test-VerifierCommandLinePath ([string]$launchProcess.CommandLine) $owner.Script) -and
            (Test-VerifierCommandLineSwitch ([string]$launchProcess.CommandLine) '-Port' ([string]$owner.Port))) `
            'Live preview process launch identity did not prove PID, creation time, executable, script, and port.'
        $launchRecord = [ordered]@{
            task = 'preview-listener-identity'
            pid = [int]$owner.ProcessId
            processStartTicks = [long]$owner.ProcessStartTicks
            executable = [string]$launchProcess.ExecutablePath
            hidden = [bool]$owner.Process.StartInfo.CreateNoWindow
            port = [int]$owner.Port
        }
        foreach ($hostHeader in @("127.0.0.1:$($owner.Port)", "localhost:$($owner.Port)", "[::1]:$($owner.Port)")) {
            $hostResponse = Invoke-RawPreviewRequest $owner.Port @($hostHeader)
            Assert-Check ($hostResponse.StatusCode -eq 200) `
                "Loopback Host header '$hostHeader' was not accepted by the TCP fallback."
            $hostObservations.Add([ordered]@{case=$hostHeader;expected=200;actual=$hostResponse.StatusCode})
        }
        foreach ($hostCase in @(
            [pscustomobject]@{ Name='non-loopback DNS name'; Headers=@("preview.invalid:$($owner.Port)"); Omit=$false }
            [pscustomobject]@{ Name='DNS suffix impersonation'; Headers=@("localhost.preview.invalid:$($owner.Port)"); Omit=$false }
            [pscustomobject]@{ Name='wrong port'; Headers=@("127.0.0.1:$([int]$owner.Port + 1)"); Omit=$false }
            [pscustomobject]@{ Name='global IPv6 address'; Headers=@("[2001:db8::1]:$($owner.Port)"); Omit=$false }
            [pscustomobject]@{ Name='userinfo authority'; Headers=@("user@127.0.0.1:$($owner.Port)"); Omit=$false }
            [pscustomobject]@{ Name='path in authority'; Headers=@("127.0.0.1:$($owner.Port)/../../"); Omit=$false }
            [pscustomobject]@{ Name='query in authority'; Headers=@("127.0.0.1:$($owner.Port)?next=example.invalid"); Omit=$false }
            [pscustomobject]@{ Name='duplicate Host fields'; Headers=@("127.0.0.1:$($owner.Port)", "localhost:$($owner.Port)"); Omit=$false }
            [pscustomobject]@{ Name='missing Host field'; Headers=@(); Omit=$true }
        )) {
            $hostResponse = Invoke-RawPreviewRequest $owner.Port $hostCase.Headers `
                -OmitHost:$hostCase.Omit
            Assert-Check ($hostResponse.StatusCode -eq 400) `
                "TCP fallback accepted invalid Host case '$($hostCase.Name)'."
            $hostObservations.Add([ordered]@{case=$hostCase.Name;expected=400;actual=$hostResponse.StatusCode})
        }
        $rawIdentity = Invoke-RawPreviewRequest $owner.Port @("127.0.0.1:$($owner.Port)")
        $liveIdentity = $rawIdentity.Body | ConvertFrom-Json -ErrorAction Stop
        Assert-VerifierPreviewIdentitySchema $liveIdentity
        $identityJson = $rawIdentity.Body
        Assert-Check ($rawIdentity.StatusCode -eq 200 -and
            $identityJson.IndexOf($root, [StringComparison]::OrdinalIgnoreCase) -lt 0 -and
            $identityJson.IndexOf([string]$owner.Script, [StringComparison]::OrdinalIgnoreCase) -lt 0) `
            'Live identity response exposed the repository root or preview script path.'
        Assert-Check ($liveIdentity.protocol -ceq 'troubleshootjs-preview-identity-v2' -and
            $liveIdentity.repositoryIdentity -ceq (Get-VerifierOpaquePathIdentity 'repository' $root) -and
            $liveIdentity.previewScriptIdentity -ceq (Get-VerifierOpaquePathIdentity 'preview-script' $owner.Script) -and
            $liveIdentity.previewPort -eq $owner.Port -and
            $liveIdentity.processId -eq $owner.ProcessId -and
            $liveIdentity.processStartTicks -eq $owner.ProcessStartTicks) `
            'Live identity response did not preserve the opaque worktree/process ownership check.'
        Write-Host ('LIVE_PROCESS_LAUNCH task=preview-listener-identity pid=' +
            [string]$launchRecord.pid + ' processStartTicks=' + [string]$launchRecord.processStartTicks +
            ' executable=' + [string]$launchRecord.executable + ' hidden=' + [string]$launchRecord.hidden +
            ' port=' + [string]$launchRecord.port)
        Write-Host ('PASS: live TCP Host checks accepted loopback IPv4/localhost/IPv6 and rejected ' +
            'non-loopback, wrong-port, malformed-authority, duplicate, and missing Host values; ' +
            'identity-v2 preserved opaque owner binding without returning repository paths.')
        # Real empty/partial requests that disappear before a response. These
        # reproduce the TcpClient.GetStream/write startup race, not a DOM mock.
        for ($i=0;$i -lt 12;$i++) {
            $client=[Net.Sockets.TcpClient]::new()
            try {
                $client.Connect([Net.IPAddress]::Loopback,[int]$owner.Port)
                if ($i % 2) {
                    $bytes=[Text.Encoding]::ASCII.GetBytes("GET /circuitjs.html HTTP/1.1`r`n")
                    $client.GetStream().Write($bytes,0,$bytes.Length)
                }
                $client.Client.LingerState=[Net.Sockets.LingerOption]::new($true,0)
            } finally { $client.Close() }
        }
        $response=Invoke-WebRequest -UseBasicParsing -Uri ($owner.BaseUrl+'/__tsj/verify-identity') -TimeoutSec 10
        $afterAbort=$response.Content | ConvertFrom-Json
        Assert-Check ($response.StatusCode -eq 200 -and $afterAbort.processId -eq $owner.ProcessId -and
            $afterAbort.processStartTicks -eq $owner.ProcessStartTicks -and
            $afterAbort.verifierRunId -ceq $context.RunId) 'Disconnected clients killed or replaced the live preview.'
        for ($i=0;$i -lt 3;$i++) {
            $proof=Get-VerifierPortLeaseBoundOwnershipProof $context $owner.Lease `
                $owner.ProcessId $owner.ProcessStartTicks $owner $owner
            Assert-Check ($proof.ProofStage -ceq 'preview-root-listener-scoped-identity') 'Real preview used a broad census.'
            Assert-Check ($proof.ProofElapsedMilliseconds -lt 500) 'Real proof exceeded unchanged deadline.'
            $inspection=$proof.Inspection
            Assert-Check (Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
                $owner.ProcessId $owner.ProcessStartTicks) 'Real downstream authorization failed.'
            $observations.Add([ordered]@{stage=$proof.ProofStage;elapsedMs=$proof.ProofElapsedMilliseconds})
        }
        foreach ($case in @(@('ProcessCommandLine','wrong-command'), @('Nonce','wrong-nonce'),
                @('ProcessParentProcessStartTicks',($owner.ProcessParentProcessStartTicks+1)),
                @('ProcessStartTicks',($owner.ProcessStartTicks+1)))) {
            $name=$case[0];$saved=$owner.$name;$accepted=$false
            try {
                $owner.$name=$case[1]
                try { $accepted=Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
                    $owner.ProcessId $owner.ProcessStartTicks } catch { $accepted=$false }
                Assert-Check (-not $accepted) "Real mismatched $name authorized."
            } finally { $owner.$name=$saved }
        }
        $breakpoint=$null
        try {
            $breakpoint=Set-PSBreakpoint -Command Get-VerifierCurrentProcessIdentity -Action { Start-Sleep -Milliseconds 600 }
            Assert-Check (-not (Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
                $owner.ProcessId $owner.ProcessStartTicks)) 'Slow identity proof reset or ignored its deadline.'
        } finally { if ($null -ne $breakpoint) { Remove-PSBreakpoint -Breakpoint $breakpoint } }
        Assert-Check (Test-VerifierLiveListenerInspectionAuthorization $inspection $context $owner `
            $owner.ProcessId $owner.ProcessStartTicks) 'Real preview did not recover after negative canaries.'
    } finally {
        if ($null -ne $context) { $cleanup=Complete-VerifierRun $context }
    }
    Assert-Check ($null -ne $cleanup -and $cleanup.Success) 'Exact live cleanup failed.'
    $transportOutput = [IO.File]::ReadAllText([string]$owner.StdoutLog)
    Assert-Check ([bool]($transportOutput -match 'transport=TcpListener')) `
        'Live preview canary did not select the real TcpListener fallback.'
}
$result=[ordered]@{protocol='troubleshootjs-preview-listener-identity-v2';status='PASS';
    checks=$checks;live=[bool]$Live;proofs=$observations.ToArray();hostCases=$hostObservations.ToArray();
    processLaunch=$launchRecord;ownershipBudgetMs=500;
    cleanup=if($Live){[bool]$cleanup.Success}else{$null}}
if ($OutputPath) {
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath),
        ($result | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}
$result | ConvertTo-Json -Depth 8

Write-Output ("PASS: preview listener identity contracts assertions=" + $checks + " live=" + [bool]$Live)
