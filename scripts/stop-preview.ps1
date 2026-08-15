[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$previewScript = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'preview.ps1'))
$stateFile = Join-Path $repositoryRoot '.tools\preview\state.json'

if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) {
    Write-Host 'TroubleshootJS preview is not recorded as running.'
    exit 0
}

try {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $stateRepository = [IO.Path]::GetFullPath([string]$state.repositoryRoot)
    $stateScript = [IO.Path]::GetFullPath([string]$state.previewScript)
} catch {
    Remove-Item -LiteralPath $stateFile -Force
    Write-Warning 'Removed unreadable TroubleshootJS preview state; no process was stopped.'
    exit 0
}

if ($stateRepository -ne $repositoryRoot -or $stateScript -ne $previewScript) {
    Remove-Item -LiteralPath $stateFile -Force
    Write-Warning 'Removed foreign or stale preview state; no process was stopped.'
    exit 0
}

$process = Get-Process -Id ([int]$state.processId) -ErrorAction SilentlyContinue
$command = if ($null -ne $process) {
    Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)" -ErrorAction SilentlyContinue
} else { $null }
$identityMatches = $null -ne $process -and $null -ne $command -and $command.CommandLine -and
    $command.CommandLine.IndexOf($previewScript, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
    $process.StartTime.ToUniversalTime().Ticks -eq [long]$state.processStartTicks

if ($identityMatches) {
    Stop-Process -Id $process.Id -Force
    [void]$process.WaitForExit(5000)
    Write-Host "Stopped TroubleshootJS preview PID $($process.Id)."
} else {
    Write-Warning 'Preview state was stale; no process was stopped.'
}

Remove-Item -LiteralPath $stateFile -Force
