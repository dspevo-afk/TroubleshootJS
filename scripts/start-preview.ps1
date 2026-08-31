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
    [int]$StartupTimeoutSeconds = 15
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force

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
    Throw-VerifierInfrastructure 'Caller-owned preview state was stale, foreign, or unprovable; it was retained for diagnosis.'
}

if (testHealthyPreview) {
    try {
        $candidateProcesses = @(Get-CimInstance Win32_Process `
            -Filter "Name = 'powershell.exe'" -ErrorAction Stop)
    } catch {
        Throw-VerifierInfrastructure ('Could not inspect candidate preview processes for safe adoption: ' +
            (Get-VerifierErrorMessage $_))
    }
    $matches = @($candidateProcesses | Where-Object {
        $_.PSObject.Properties['CommandLine'] -and
        -not [String]::IsNullOrWhiteSpace([string]$_.CommandLine) -and
        (Test-VerifierCommandLinePath $_.CommandLine $previewScript) -and
        (Test-VerifierCommandLineSwitch $_.CommandLine '-Port' ([string]$Port))
    })
    if ($matches.Count -ne 1) {
        throw "Port $Port is healthy but is not owned by one identifiable TroubleshootJS preview process."
    }
    $adoptedIdentity = Get-VerifierCurrentProcessIdentity ([int]$matches[0].ProcessId) `
        0 0 '' $previewScript $Port
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

if (-not $process.HasExited) {
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
    removeState
}
$details = if (Test-Path -LiteralPath $stderrLog) {
    (Get-Content -LiteralPath $stderrLog -Raw -ErrorAction SilentlyContinue).Trim()
} else { '' }
throw "TroubleshootJS preview failed to start on port $Port. $details"
