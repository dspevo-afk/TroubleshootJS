[CmdletBinding()]
param(
    [string]$StateFile = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$modulePath = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
try {
    Import-Module $modulePath -Force -ErrorAction Stop
} catch {
    # A PowerShell non-terminating error can override an explicit exit 2 on
    # Windows PowerShell 5.1. Emit setup diagnostics directly to stderr; the
    # process exit below remains the authoritative infrastructure result.
    [Console]::Error.WriteLine('FAIL stop-preview infrastructure: VerifierIsolation.psm1 could not be imported: ' +
        [string]$_.Exception.Message)
    exit 2
}

try {
    $repositoryRoot = Get-VerifierFullPath (Split-Path -Parent $PSScriptRoot)
    $previewScript = Get-VerifierFullPath (Join-Path $PSScriptRoot 'preview.ps1')
    $stateDirectory = Get-VerifierFullPath (Join-Path $repositoryRoot '.tools\preview')
    $statePath = if ([String]::IsNullOrWhiteSpace($StateFile)) {
        Get-VerifierFullPath (Join-Path $stateDirectory 'state.json')
    } else {
        Get-VerifierFullPath $StateFile
    }
    [void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $repositoryRoot -AllowRoot)
    [void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $previewScript)
    [void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $stateDirectory -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $stateDirectory $statePath)
    if (-not (Test-VerifierChildPath $stateDirectory $statePath)) {
        Throw-VerifierInfrastructure 'Refusing to inspect a preview state file outside the repository preview state directory.'
    }
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        Write-Host 'TroubleshootJS preview is not recorded as running.'
        exit 0
    }

    $state = $null
    try {
        $state = Get-Content -LiteralPath $statePath -Raw -ErrorAction Stop | ConvertFrom-Json
    } catch {
        Throw-VerifierInfrastructure ('Preview state was unreadable; it was retained: ' +
            (Get-VerifierErrorMessage $_))
    }
    foreach ($property in @('repositoryRoot', 'previewScript', 'processId',
            'processStartTicks', 'processParentProcessId',
            'processParentProcessStartTicks', 'processCommandLine', 'port')) {
        if (-not $state.PSObject.Properties[$property]) {
            Throw-VerifierInfrastructure "Preview state omitted required ownership field '$property'; state was retained."
        }
    }
    $stateRepository = Get-VerifierCanonicalWindowsPath ([string]$state.repositoryRoot)
    $stateScript = Get-VerifierCanonicalWindowsPath ([string]$state.previewScript)
    if (-not (Test-VerifierCanonicalWindowsPathValue $stateRepository $repositoryRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $stateScript $previewScript)) {
        Throw-VerifierInfrastructure 'Preview state was foreign or stale; it was retained and no process was stopped.'
    }
    $port = 0
    $processId = 0
    $startTicks = 0L
    if (-not [int]::TryParse([string]$state.port, [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$port) -or
            $port -lt 1 -or $port -gt 65535 -or
            -not [int]::TryParse([string]$state.processId, [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$processId) -or
            $processId -le 0 -or
            -not [long]::TryParse([string]$state.processStartTicks,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$startTicks) -or
            $startTicks -le 0) {
        Throw-VerifierInfrastructure 'Preview state had an invalid PID/start/port identity; it was retained.'
    }
    $expectedParentProcessId = 0
    $expectedCommandLine = [string]$state.processCommandLine
    if (-not [int]::TryParse([string]$state.processParentProcessId,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$expectedParentProcessId) -or $expectedParentProcessId -le 0) {
        Throw-VerifierInfrastructure 'Preview state had an invalid parent-process identity; it was retained.'
    }
    $expectedParentProcessStartTicks = 0L
    if (-not [long]::TryParse([string]$state.processParentProcessStartTicks,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$expectedParentProcessStartTicks) -or
            $expectedParentProcessStartTicks -le 0) {
        Throw-VerifierInfrastructure 'Preview state had an invalid parent-process start identity; it was retained.'
    }
    if ([String]::IsNullOrWhiteSpace($expectedCommandLine) -or
            -not (Test-VerifierCommandLinePath $expectedCommandLine $previewScript) -or
            -not (Test-VerifierCommandLineSwitch $expectedCommandLine '-Port' ([string]$port))) {
        Throw-VerifierInfrastructure 'Preview state command-line identity did not carry its canonical script and port; it was retained.'
    }
    if (-not (Assert-VerifierPhysicalOwnedPath $stateDirectory $statePath)) {
        Throw-VerifierInfrastructure 'Preview state path changed physical ownership before process cleanup; state was retained.'
    }

    # Re-query the current PID and Win32_Process record. Exact -File/-Port
    # token boundaries, canonical paths, start identity, and the current
    # Process object are all required before termination.
    $identity = Get-VerifierCurrentProcessIdentity $processId $startTicks `
        $expectedParentProcessId $expectedCommandLine `
        $previewScript $port '' '' $expectedParentProcessStartTicks
    $process = $identity.Process
    [void](Stop-VerifierVerifiedProcessExactly $process $startTicks 5000 $identity.Record)
    $process.Refresh()
    if (-not [bool]$process.HasExited -or -not [bool]$process.WaitForExit(0)) {
        Throw-VerifierInfrastructure "Preview PID $processId termination was not proven after the verified stop; state was retained."
    }
    $inspection = Get-VerifierLoopbackListenerRecords $port
    if (-not $inspection.Success -or -not $inspection.Known -or $inspection.HasListeners) {
        Throw-VerifierInfrastructure "Preview port $port absence was not positively proven; state was retained."
    }
    # Delete state only after process and listener absence are positively
    # proven, and recheck the physical destination immediately before delete.
    if (-not (Assert-VerifierPhysicalOwnedPath $stateDirectory $statePath)) {
        Throw-VerifierInfrastructure 'Preview state path changed physical ownership before deletion; state was retained.'
    }
    Remove-VerifierOwnedTree $stateDirectory $statePath
    Write-Host "Stopped TroubleshootJS preview PID $processId and positively released port $port."
    exit 0
} catch {
    [Console]::Error.WriteLine('FAIL stop-preview infrastructure: ' + (Get-VerifierErrorMessage $_))
    exit 2
}
