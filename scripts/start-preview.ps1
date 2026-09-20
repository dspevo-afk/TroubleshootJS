[CmdletBinding()]
param(
    [ValidateSet('led', 'diode', 'parallel', 'rc', 'npn', 'nmos')]
    [string]$Challenge = 'led',
    [long]$Seed = 3,
    [switch]$QuickPlay,
    [switch]$BuildIfMissing,
    [switch]$OpenBrowser,
    [ValidateRange(1, 65535)]
    [int]$Port = 8899,
    [ValidateRange(1, 60)]
    [int]$StartupTimeoutSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force -DisableNameChecking

$repositoryRoot = Get-VerifierCanonicalWindowsPath ([IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)))
$previewScript = Get-VerifierCanonicalWindowsPath ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'preview.ps1')))
$stateDirectory = Get-VerifierCanonicalWindowsPath (Join-Path $repositoryRoot '.tools\preview')
$stateFile = Get-VerifierCanonicalWindowsPath (Join-Path $stateDirectory 'state.json')
$stdoutLog = Get-VerifierCanonicalWindowsPath (Join-Path $stateDirectory 'stdout.log')
$stderrLog = Get-VerifierCanonicalWindowsPath (Join-Path $stateDirectory 'stderr.log')
$previewRootUrl = "http://127.0.0.1:$Port"
$pageUrl = if ($QuickPlay) {
    "$previewRootUrl/circuitjs.html?tsjQuickPlay=true"
} else {
    "$previewRootUrl/circuitjs.html?tsjChallenge=$Challenge&seed=$Seed"
}
$bootstrapUrl = "$previewRootUrl/circuitjs1/circuitjs1.nocache.js"
$bootstrapPath = Get-VerifierCanonicalWindowsPath (Join-Path $repositoryRoot 'war\circuitjs1\circuitjs1.nocache.js')
$buildScript = Get-VerifierCanonicalWindowsPath ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build.ps1')))
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $repositoryRoot -AllowRoot)
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $previewScript)
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $stateDirectory -ValidateTree)
[void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $buildScript)

function findJdk8Home {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += (Join-Path $repositoryRoot '.tools\jdk8-download\jdk8u502-b07')
    $candidates += (Join-Path $repositoryRoot '.tools\jdk8u502-b07')
    $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $candidates += Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $candidateRoot = Get-VerifierCanonicalWindowsPath ([IO.Path]::GetFullPath([string]$candidate))
        $javaPath = Join-Path $candidateRoot 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) { continue }
        $versionResult = Invoke-VerifierBoundedProcess $javaPath @('-version') 15000
        $version = ([string]$versionResult.Stdout + [string]$versionResult.Stderr)
        if ($version -match 'version "1\.8\.') {
            return $candidateRoot
        }
    }
    return $null
}

function ensureProductionOutput {
    if (Test-Path -LiteralPath $bootstrapPath -PathType Leaf) { return }
    if (-not $BuildIfMissing) {
        throw 'Production output is missing. Run scripts\build.ps1 with JDK 8 first.'
    }
    $jdkHome = findJdk8Home
    if (-not $jdkHome) {
        throw 'Production output is missing and no JDK 8 was found. Install or select a JDK 8 and run scripts\build.ps1; no JDK was installed automatically.'
    }
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    Write-Host "Production output is missing; building with JDK 8 at $jdkHome..."
    $buildResult = Invoke-VerifierBoundedProcess $powershell @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $buildScript,
        '-JavaHome', $jdkHome, '-Target', 'Compile', '-Style', 'OBF'
    ) 900000
    if (-not [String]::IsNullOrWhiteSpace([string]$buildResult.Stdout)) {
        Write-Host ([string]$buildResult.Stdout)
    }
    if (-not [String]::IsNullOrWhiteSpace([string]$buildResult.Stderr)) {
        Write-Host ([string]$buildResult.Stderr)
    }
    if ($buildResult.ExitCode -ne 0 -or
            -not (Test-Path -LiteralPath $bootstrapPath -PathType Leaf)) {
        throw ('JDK 8 production build failed or did not produce the compiled bootstrap; exit ' +
            [string]$buildResult.ExitCode + '.')
    }
}

function openPageIfRequested {
    if ($OpenBrowser) { Start-Process -FilePath $pageUrl }
}

function writePreviewUrls {
    Write-Host ("Preview root URL (for verify-browser -BaseUrl): " + $previewRootUrl)
    Write-Host ("Preview page URL: " + $pageUrl)
}

function testEndpoint([string]$url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function testHealthyPreview {
    return (testEndpoint $pageUrl) -and (testEndpoint $bootstrapUrl)
}

function getProcessStartTicks($process) {
    return [long](Get-VerifierProcessStartTicks $process)
}

function getPreviewCommandProcess([int]$processId, [int]$expectedPort = $Port,
        [long]$expectedStartTicks = 0, [int]$expectedParentProcessId = 0,
        [string]$expectedCommandLine = '',
        [long]$expectedParentProcessStartTicks = 0) {
    try {
        $identity = Get-VerifierCurrentProcessIdentity $processId $expectedStartTicks `
            $expectedParentProcessId $expectedCommandLine $previewScript $expectedPort `
            '' '' $expectedParentProcessStartTicks
        return $identity.Process
    } catch {
        return $null
    }
}

function readValidState {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return $null }
    try {
        Assert-VerifierNoReparseAncestors $stateDirectory
        if (-not (Test-VerifierPhysicalChildPath $stateDirectory $stateFile)) {
            Throw-VerifierInfrastructure 'Preview state path changed physical ownership.'
        }
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        if (-not (Test-VerifierCanonicalWindowsPathValue ([string]$state.repositoryRoot) $repositoryRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$state.previewScript) $previewScript) -or
                [int]$state.port -le 0) {
            return $null
        }
        $stateParent = 0
        $stateParentStart = 0L
        $stateCommand = ''
        if (-not $state.PSObject.Properties['processParentProcessId'] -or
                -not [int]::TryParse([string]$state.processParentProcessId, [ref]$stateParent) -or
                $stateParent -le 0 -or
                -not $state.PSObject.Properties['processParentProcessStartTicks'] -or
                -not [long]::TryParse([string]$state.processParentProcessStartTicks,
                    [Globalization.NumberStyles]::Integer,
                    [Globalization.CultureInfo]::InvariantCulture, [ref]$stateParentStart) -or
                $stateParentStart -le 0 -or
                -not $state.PSObject.Properties['processCommandLine'] -or
                [String]::IsNullOrWhiteSpace([string]$state.processCommandLine)) {
            return $null
        }
        $stateCommand = [string]$state.processCommandLine
        $process = getPreviewCommandProcess ([int]$state.processId) ([int]$state.port) `
            ([long]$state.processStartTicks) $stateParent $stateCommand $stateParentStart
        if ($null -eq $process) {
            return $null
        }
        return [pscustomobject]@{ State = $state; Process = $process }
    } catch {
        return $null
    }
}

function getDetachedPreviewIdentityFromRecord($currentRecord,
        [long]$expectedStartTicks, [string]$expectedCommandLine,
        [int]$expectedPort, [int]$expectedParentProcessId = 0,
        [long]$expectedParentProcessStartTicks = 0) {
    if ($null -eq $currentRecord -or
            -not $currentRecord.PSObject.Properties['Process'] -or
            $currentRecord.Process.GetType() -ne [Diagnostics.Process] -or
            -not (Test-VerifierStrictIntegralValue $expectedStartTicks 1 ([long]::MaxValue)) -or
            -not (Test-VerifierStrictIntegralValue $expectedPort 1 65535) -or
            -not (Test-VerifierStrictStringValue $expectedCommandLine) -or
            [String]::IsNullOrWhiteSpace($expectedCommandLine)) {
        return $null
    }
    if ($expectedParentProcessId -lt 0 -or
            $expectedParentProcessStartTicks -lt 0) { return $null }
    if ([long]$currentRecord.ProcessStartTicks -ne $expectedStartTicks -or
            ($expectedParentProcessId -gt 0 -and
                [int]$currentRecord.ParentProcessId -ne $expectedParentProcessId) -or
            -not (Test-VerifierCommandLineEquivalent `
                ([string]$currentRecord.CommandLine) $expectedCommandLine) -or
            -not (Test-VerifierCommandLinePath `
                ([string]$currentRecord.CommandLine) $previewScript) -or
            -not (Test-VerifierCommandLineSwitch `
                ([string]$currentRecord.CommandLine) '-Port' ([string]$expectedPort))) {
        return $null
    }

    # A detached launch loses its short-lived launcher parent after the
    # launcher exits.  The preview's own identity route binds the current
    # process/start/port to the exact worktree and script, so this fallback can
    # reuse a healthy detached preview without relaxing PID/start or command
    # identity checks and without attempting termination.
    $identity = $null
    try {
        $identityResponse = Invoke-WebRequest -UseBasicParsing `
            -Uri ("http://127.0.0.1:$expectedPort/__tsj/verify-identity") `
            -TimeoutSec 2 -ErrorAction Stop
        if ([int]$identityResponse.StatusCode -ne 200) { return $null }
        $identity = $identityResponse.Content | ConvertFrom-Json -ErrorAction Stop
    } catch {
        return $null
    }
    if ($null -eq $identity -or
            -not $identity.PSObject.Properties['protocol'] -or
            [string]$identity.protocol -cne 'troubleshootjs-preview-identity-v1' -or
            -not $identity.PSObject.Properties['repositoryRoot'] -or
            -not $identity.PSObject.Properties['previewScript'] -or
            -not $identity.PSObject.Properties['previewPort'] -or
            -not $identity.PSObject.Properties['processId'] -or
            -not $identity.PSObject.Properties['processStartTicks']) {
        return $null
    }
    $identityPort = 0
    $identityProcessId = 0
    $identityStartTicks = 0L
    if (-not [int]::TryParse([string]$identity.previewPort,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$identityPort) -or
            -not [int]::TryParse([string]$identity.processId,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$identityProcessId) -or
            -not [long]::TryParse([string]$identity.processStartTicks,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$identityStartTicks) -or
            $identityPort -ne $expectedPort -or
            $identityProcessId -ne [int]$currentRecord.ProcessId -or
            $identityStartTicks -ne $expectedStartTicks -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string]$identity.repositoryRoot) $repositoryRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string]$identity.previewScript) $previewScript)) {
        return $null
    }
    # The identity route and the process record are independent observations.
    # Re-query the same PID after the route response so a fast exit/PID reuse
    # cannot turn the earlier record into an adoption proof.
    $currentAgain = Get-VerifierCurrentProcessRecordById `
        ([int]$currentRecord.ProcessId)
    if ($null -eq $currentAgain -or
            [long]$currentAgain.ProcessStartTicks -ne $expectedStartTicks -or
            -not (Test-VerifierCommandLineEquivalent `
                ([string]$currentAgain.CommandLine) $expectedCommandLine)) {
        return $null
    }
    $detachedRecord = [pscustomobject]@{
        ProcessId = [int]$currentAgain.ProcessId
        ParentProcessId = [int]$currentAgain.ParentProcessId
        ProcessStartTicks = [long]$currentAgain.ProcessStartTicks
        ParentProcessStartTicks = [long]$expectedParentProcessStartTicks
        CommandLine = [string]$currentAgain.CommandLine
        Name = if ($currentAgain.PSObject.Properties['Name']) {
            [string]$currentAgain.Name
        } else { '' }
        ExecutablePath = if ($currentAgain.PSObject.Properties['ExecutablePath']) {
            [string]$currentAgain.ExecutablePath
        } else { '' }
    }
    return [pscustomobject]@{
        Process = $currentAgain.Process
        Record = $detachedRecord
        Identity = $identity
    }
}

function readRecoverableStaleState {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return $null }

    # Keep path failures outside the tolerant state parser.  A replaced state
    # namespace is an infrastructure failure, never a stale-state recovery.
    Assert-VerifierNoReparseAncestors $stateDirectory
    if (-not (Test-VerifierPhysicalChildPath $stateDirectory $stateFile)) {
        Throw-VerifierInfrastructure 'Preview state path changed physical ownership.'
    }

    $state = $null
    try {
        $state = Get-Content -LiteralPath $stateFile -Raw -ErrorAction Stop |
            ConvertFrom-Json -ErrorAction Stop
    } catch {
        return $null
    }
    if ($null -eq $state -or @($state).Count -ne 1) { return $null }

    # Only a record that names this exact worktree and preview script can be
    # archived.  Foreign, malformed, or otherwise ambiguous state stays at
    # the active path so the caller can diagnose it without any mutation.
    if (-not $state.PSObject.Properties['repositoryRoot'] -or
            -not $state.PSObject.Properties['previewScript'] -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string]$state.repositoryRoot) $repositoryRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string]$state.previewScript) $previewScript)) {
        return $null
    }

    $statePort = 0
    $stateProcessId = 0
    if (-not $state.PSObject.Properties['port'] -or
            -not [int]::TryParse([string]$state.port,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$statePort) -or
            $statePort -lt 1 -or $statePort -gt 65535 -or
            -not $state.PSObject.Properties['processId'] -or
            -not [int]::TryParse([string]$state.processId,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$stateProcessId) -or
            $stateProcessId -le 0) {
        return $null
    }

    $stateStartTicks = 0L
    $stateCommandLine = ''
    $stateParentProcessId = 0
    $stateParentProcessStartTicks = 0L
    $detachedStateFieldsValid = (
        $state.PSObject.Properties['processStartTicks'] -and
        [long]::TryParse([string]$state.processStartTicks,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$stateStartTicks) -and
        $stateStartTicks -gt 0 -and
        $state.PSObject.Properties['processCommandLine'] -and
        (Test-VerifierStrictStringValue $state.processCommandLine) -and
        -not [String]::IsNullOrWhiteSpace([string]$state.processCommandLine))
    if ($state.PSObject.Properties['processParentProcessId']) {
        $detachedStateFieldsValid = $detachedStateFieldsValid -and
            [int]::TryParse([string]$state.processParentProcessId,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$stateParentProcessId) -and
            $stateParentProcessId -ge 0
    }
    if ($state.PSObject.Properties['processParentProcessStartTicks']) {
        $detachedStateFieldsValid = $detachedStateFieldsValid -and
            [long]::TryParse([string]$state.processParentProcessStartTicks,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$stateParentProcessStartTicks) -and
            $stateParentProcessStartTicks -ge 0
    }
    if ($detachedStateFieldsValid) {
        $stateCommandLine = [string]$state.processCommandLine
    }

    # A missing PID is stale only after both process views positively prove it
    # is absent.  If the PID is alive, first permit the narrow detached-preview
    # reuse path when the preview itself proves the exact current identity.
    # Otherwise retain the state and refuse to stop or overwrite anything.
    $recordedProcess = Get-VerifierCurrentProcessRecordById $stateProcessId
    if ($null -ne $recordedProcess) {
        if ($detachedStateFieldsValid) {
            $detached = getDetachedPreviewIdentityFromRecord $recordedProcess `
                $stateStartTicks $stateCommandLine $statePort `
                $stateParentProcessId $stateParentProcessStartTicks
            if ($null -ne $detached) {
                return [pscustomobject]@{
                    Kind = 'live'
                    State = $state
                    Identity = $detached
                }
            }
        }
        return $null
    }
    return [pscustomobject]@{
        Kind = 'stale'
        State = $state
        Port = $statePort
        ProcessId = $stateProcessId
    }
}

function archiveStaleState($staleState, [string]$stateHash) {
    if ($null -eq $staleState -or
            -not $staleState.PSObject.Properties['State'] -or
            -not (Test-VerifierStrictStringValue $stateHash) -or
            [String]::IsNullOrWhiteSpace($stateHash)) {
        Throw-VerifierInfrastructure 'Stale preview recovery received incomplete state evidence.'
    }
    $recoveryDirectory = Get-VerifierCanonicalWindowsPath `
        (Join-Path $stateDirectory 'recovery')
    New-Item -ItemType Directory -Path $recoveryDirectory -Force | Out-Null
    Assert-VerifierNoReparseAncestors $recoveryDirectory
    if (-not (Assert-VerifierPhysicalOwnedPath $stateDirectory $recoveryDirectory)) {
        Throw-VerifierInfrastructure 'Preview stale-state archive was outside its physical namespace.'
    }
    $archiveName = 'stale-state-' +
        [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ',
            [Globalization.CultureInfo]::InvariantCulture) + '-' +
        [Guid]::NewGuid().ToString('N') + '.json'
    $archivePath = Get-VerifierCanonicalWindowsPath `
        (Join-Path $recoveryDirectory $archiveName)
    if (-not (Test-VerifierPhysicalChildPath $recoveryDirectory $archivePath)) {
        Throw-VerifierInfrastructure 'Preview stale-state archive destination was outside its physical namespace.'
    }

    # The state is caller-owned metadata.  Recheck its bytes immediately
    # before moving it, and use a no-overwrite move into a unique archive so a
    # concurrent launcher cannot be silently replaced or discarded.
    if (-not (Test-VerifierPhysicalChildPath $stateDirectory $stateFile) -or
            (Get-VerifierFileSha256 $stateFile) -cne $stateHash) {
        Throw-VerifierInfrastructure 'Preview state changed before stale-state archival; it was retained.'
    }
    try {
        Move-Item -LiteralPath $stateFile -Destination $archivePath -ErrorAction Stop
    } catch {
        Throw-VerifierInfrastructure ('Could not archive stale preview state; it was retained: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ((Test-Path -LiteralPath $stateFile) -or
            -not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
        Throw-VerifierInfrastructure 'Preview stale-state archival was not positively proven.'
    }
    return $archivePath
}

function writeState($process, [int]$previewPort, $identity = $null) {
    New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
    Assert-VerifierNoReparseAncestors $stateDirectory
    if (-not (Assert-VerifierPhysicalOwnedPath $repositoryRoot $stateDirectory) -or
            -not (Assert-VerifierPhysicalOwnedPath $stateDirectory $stateFile)) {
        Throw-VerifierInfrastructure 'Preview state destination was outside its physical owned namespace.'
    }
    $parentProcessId = 0
    $parentProcessStartTicks = 0L
    $commandLine = ''
    if ($null -ne $identity -and $identity.PSObject.Properties['Record']) {
        $parentProcessId = [int]$identity.Record.ParentProcessId
        $parentProcessStartTicks = [long]$identity.Record.ParentProcessStartTicks
        $commandLine = [string]$identity.Record.CommandLine
    } else {
        try {
            $capturedIdentity = Get-VerifierCurrentProcessIdentity $process.Id 0 0 '' `
                $previewScript $previewPort
            $parentProcessId = [int]$capturedIdentity.Record.ParentProcessId
            $parentProcessStartTicks = [long]$capturedIdentity.Record.ParentProcessStartTicks
            $commandLine = [string]$capturedIdentity.Record.CommandLine
        } catch {
            # The state still retains PID/start/script/port evidence. A later
            # stop attempt must re-query WMI and will fail closed if command
            # identity cannot be proven.
        }
    }
    $state = [ordered]@{
        repositoryRoot = $repositoryRoot
        previewScript = $previewScript
        processId = $process.Id
        processStartTicks = getProcessStartTicks $process
        processParentProcessId = $parentProcessId
        processParentProcessStartTicks = $parentProcessStartTicks
        processCommandLine = $commandLine
        port = $previewPort
    }
    $temporaryState = "$stateFile.tmp"
    if (-not (Test-VerifierPhysicalChildPath $stateDirectory $temporaryState)) {
        Throw-VerifierInfrastructure 'Preview temporary state destination was outside its physical namespace.'
    }
    $state | ConvertTo-Json | Set-Content -LiteralPath $temporaryState -Encoding UTF8
    if (-not (Assert-VerifierPhysicalOwnedPath $stateDirectory $temporaryState) -or
            -not (Assert-VerifierPhysicalOwnedPath $stateDirectory $stateFile)) {
        throw 'Preview state destination changed physical ownership before commit.'
    }
    Move-Item -LiteralPath $temporaryState -Destination $stateFile -Force
}

function removeState {
    if (Test-Path -LiteralPath $stateFile) {
        Remove-VerifierOwnedTree $stateDirectory $stateFile
    }
}

function stopOwnedProcess($validState) {
    if ($null -eq $validState) { return }
    $state = $validState.State
    $identity = Get-VerifierCurrentProcessIdentity ([int]$state.processId) `
        ([long]$state.processStartTicks) ([int]$state.processParentProcessId) `
        ([string]$state.processCommandLine) $previewScript ([int]$state.port) `
        '' '' ([long]$state.processParentProcessStartTicks)
    $process = $identity.Process
    [void](Stop-VerifierVerifiedProcessExactly $process ([long]$state.processStartTicks) 5000 $identity.Record)
    Complete-VerifierProcessOutputCapture $process 5000
    $inspection = Get-VerifierLoopbackListenerRecords ([int]$state.port)
    if (-not $inspection.Success -or -not $inspection.Known -or $inspection.HasListeners) {
        Throw-VerifierInfrastructure 'Caller-owned preview listener absence was not positively proven after cleanup.'
    }
}

ensureProductionOutput

$validState = readValidState
if ($null -ne $validState -and [int]$validState.State.port -eq $Port -and (testHealthyPreview)) {
    Write-Host "TroubleshootJS preview already running (PID $($validState.Process.Id))."
    writePreviewUrls
    openPageIfRequested
    exit 0
}

if ($null -ne $validState) {
    stopOwnedProcess $validState
    removeState
} elseif (Test-Path -LiteralPath $stateFile) {
    $staleStateHash = Get-VerifierFileSha256 $stateFile
    $staleState = readRecoverableStaleState
    if ($null -eq $staleState) {
        Throw-VerifierInfrastructure 'Caller-owned preview state was stale, foreign, or unprovable; it was retained for diagnosis.'
    }
    if ([string]$staleState.Kind -eq 'live') {
        if ([int]$staleState.State.port -ne $Port -or -not (testHealthyPreview)) {
            Throw-VerifierInfrastructure 'Caller-owned preview state identified a live detached preview that was not healthy on the requested port; it was retained.'
        }
        Write-Host "TroubleshootJS preview already running (PID $($staleState.Identity.Process.Id); detached launcher parent retained by state)."
        writePreviewUrls
        openPageIfRequested
        exit 0
    }
    $archivedState = archiveStaleState $staleState $staleStateHash
    Write-Host "Archived stale preview state for diagnosis: $archivedState"
}

if (testHealthyPreview) {
    try {
        $candidateProcesses = @(Get-VerifierProcessSnapshotWithFallback `
            'preview adoption')
        $matches = @(Get-VerifierPreviewAdoptionCandidateRecords `
            $candidateProcesses $previewScript $Port 'powershell.exe')
    } catch {
        Throw-VerifierInfrastructure ('Could not inspect candidate preview processes for safe adoption: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($matches.Count -ne 1) {
        throw "Port $Port is healthy but is not owned by one identifiable TroubleshootJS preview process."
    }
    $adoptedIdentity = $null
    try {
        $adoptedIdentity = Get-VerifierCurrentProcessIdentity `
            ([int]$matches[0].ProcessId) 0 ([int]$matches[0].ParentProcessId) `
            ([string]$matches[0].CommandLine) $previewScript $Port
    } catch {
        # A detached preview may have lost the launcher parent by the time a
        # later invocation adopts it.  Retain the shared PID/start/command
        # checks and require the preview's own exact worktree identity route;
        # this fallback never authorizes termination.
        $candidateRecord = Get-VerifierCurrentProcessRecordById `
            ([int]$matches[0].ProcessId)
        if ($null -eq $candidateRecord) { throw }
        $adoptedIdentity = getDetachedPreviewIdentityFromRecord $candidateRecord `
            ([long]$candidateRecord.ProcessStartTicks) `
            ([string]$matches[0].CommandLine) $Port `
            ([int]$matches[0].ParentProcessId)
        if ($null -eq $adoptedIdentity) { throw }
    }
    $adopted = $adoptedIdentity.Process
    writeState $adopted $Port $adoptedIdentity
    Write-Host "Adopted existing TroubleshootJS preview (PID $($adopted.Id))."
    writePreviewUrls
    openPageIfRequested
    exit 0
}

New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
Assert-VerifierNoReparseAncestors $stateDirectory
if (-not (Test-VerifierPhysicalChildPath $stateDirectory $stdoutLog) -or
        -not (Test-VerifierPhysicalChildPath $stateDirectory $stderrLog)) {
    Throw-VerifierInfrastructure 'Preview log destination was outside its physical state namespace.'
}
foreach ($oldLog in @($stdoutLog, $stderrLog)) {
    if (Test-Path -LiteralPath $oldLog) {
        Remove-VerifierOwnedTree $stateDirectory $oldLog
    }
}
$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $previewScript,
    '-Port', [string]$Port)
$process = Start-VerifierProcess $powershell $arguments $stdoutLog $stderrLog
# Publish the handle/PID/start evidence before any fallible WMI identity
# capture. If identity proof is unavailable, retain this state and leave the
# process/port for an exact later cleanup attempt; never orphan or broad-kill it.
writeState $process $Port
$initialIdentity = $null
try {
    $initialIdentity = Get-VerifierCurrentProcessIdentity $process.Id 0 0 '' $previewScript $Port
    writeState $process $Port $initialIdentity
} catch {
    # Keep the explicit infrastructure exit code observable under Windows
    # PowerShell 5.1; Write-Error can otherwise make the host report exit 1.
    [Console]::Error.WriteLine('FAIL start-preview infrastructure: process identity could not be proven; state retained: ' +
        (Get-VerifierErrorMessage $_))
    exit 2
}

$deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
do {
    if ($process.HasExited) { break }
    if (testHealthyPreview) {
        # Revalidate the exact current process immediately before declaring the
        # caller-owned preview usable; a PID replacement must not be recorded
        # as the preview that bound this port.
        $readyIdentity = Get-VerifierCurrentProcessIdentity $process.Id `
            ([long]$initialIdentity.Record.ProcessStartTicks) `
            ([int]$initialIdentity.Record.ParentProcessId) `
            ([string]$initialIdentity.Record.CommandLine) $previewScript $Port `
            '' '' ([long]$initialIdentity.Record.ParentProcessStartTicks)
        writeState $process $Port $readyIdentity
        Write-Host "TroubleshootJS preview started (PID $($process.Id))."
        writePreviewUrls
        openPageIfRequested
        exit 0
    }
    Start-Sleep -Milliseconds 200
} while ([DateTime]::UtcNow -lt $deadline)

$exitedBeforeReady = [bool]$process.HasExited
if (-not $exitedBeforeReady) {
    try {
        stopOwnedProcess ([pscustomobject]@{
            State = [pscustomobject]@{
                processId = $process.Id
                processStartTicks = getProcessStartTicks $process
                processParentProcessId = [int]$initialIdentity.Record.ParentProcessId
                processParentProcessStartTicks = [long]$initialIdentity.Record.ParentProcessStartTicks
                processCommandLine = [string]$initialIdentity.Record.CommandLine
                port = $Port
            }
            Process = $process
        })
        removeState
    } catch {
        throw
    }
} else {
    $process.Refresh()
    if (-not [bool]$process.HasExited) {
        Throw-VerifierInfrastructure 'Preview startup process exit state was not proven.'
    }
    Complete-VerifierProcessOutputCapture $process 5000
    removeState
}
$details = if (Test-Path -LiteralPath $stderrLog) {
    [string](Get-Content -LiteralPath $stderrLog -Raw -ErrorAction SilentlyContinue)
} else { '' }
$details = ([string]$details).Trim()
$diagnostics = if ($details) { " $details" } else { '' }
if ($exitedBeforeReady) {
    throw "TroubleshootJS preview exited before becoming ready on port $Port (exit code $($process.ExitCode)).$diagnostics"
}
throw "TroubleshootJS preview did not become ready before the startup timeout on port $Port ($StartupTimeoutSeconds s).$diagnostics"
