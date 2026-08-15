[CmdletBinding()]
param(
    [ValidateSet('led', 'diode')]
    [string]$Challenge = 'led',
    [long]$Seed = 3,
    [ValidateRange(1, 65535)]
    [int]$Port = 8899,
    [ValidateRange(1, 60)]
    [int]$StartupTimeoutSeconds = 15
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$previewScript = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'preview.ps1'))
$stateDirectory = Join-Path $repositoryRoot '.tools\preview'
$stateFile = Join-Path $stateDirectory 'state.json'
$stdoutLog = Join-Path $stateDirectory 'stdout.log'
$stderrLog = Join-Path $stateDirectory 'stderr.log'
$pageUrl = "http://127.0.0.1:$Port/circuitjs.html?tsjChallenge=$Challenge&seed=$Seed"
$bootstrapUrl = "http://127.0.0.1:$Port/circuitjs1/circuitjs1.nocache.js"

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
    return $process.StartTime.ToUniversalTime().Ticks
}

function getPreviewCommandProcess([int]$processId) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    $command = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
    if ($null -eq $command -or [String]::IsNullOrEmpty($command.CommandLine) -or
            $command.CommandLine.IndexOf($previewScript, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        return $null
    }
    return $process
}

function readValidState {
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return $null }
    try {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        if ([IO.Path]::GetFullPath([string]$state.repositoryRoot) -ne $repositoryRoot -or
                [IO.Path]::GetFullPath([string]$state.previewScript) -ne $previewScript) {
            return $null
        }
        $process = getPreviewCommandProcess ([int]$state.processId)
        if ($null -eq $process -or (getProcessStartTicks $process) -ne [long]$state.processStartTicks) {
            return $null
        }
        return [pscustomobject]@{ State = $state; Process = $process }
    } catch {
        return $null
    }
}

function writeState($process, [int]$previewPort) {
    New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
    $state = [ordered]@{
        repositoryRoot = $repositoryRoot
        previewScript = $previewScript
        processId = $process.Id
        processStartTicks = getProcessStartTicks $process
        port = $previewPort
    }
    $temporaryState = "$stateFile.tmp"
    $state | ConvertTo-Json | Set-Content -LiteralPath $temporaryState -Encoding UTF8
    Move-Item -LiteralPath $temporaryState -Destination $stateFile -Force
}

function removeState {
    if (Test-Path -LiteralPath $stateFile) { Remove-Item -LiteralPath $stateFile -Force }
}

function stopOwnedProcess($validState) {
    if ($null -eq $validState) { return }
    Stop-Process -Id $validState.Process.Id -Force -ErrorAction SilentlyContinue
    [void]$validState.Process.WaitForExit(5000)
}

$validState = readValidState
if ($null -ne $validState -and [int]$validState.State.port -eq $Port -and (testHealthyPreview)) {
    Write-Host "TroubleshootJS preview already running (PID $($validState.Process.Id))."
    Write-Host $pageUrl
    exit 0
}

if ($null -ne $validState) {
    stopOwnedProcess $validState
    removeState
} elseif (Test-Path -LiteralPath $stateFile) {
    removeState
}

if (testHealthyPreview) {
    $matches = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq 'powershell.exe' -and $_.CommandLine -and
        $_.CommandLine.IndexOf($previewScript, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $_.CommandLine -match ("(?i)-Port\s+" + [Regex]::Escape([string]$Port) + "(?:\s|$)")
    })
    if ($matches.Count -ne 1) {
        throw "Port $Port is healthy but is not owned by one identifiable TroubleshootJS preview process."
    }
    $adopted = Get-Process -Id $matches[0].ProcessId -ErrorAction Stop
    writeState $adopted $Port
    Write-Host "Adopted existing TroubleshootJS preview (PID $($adopted.Id))."
    Write-Host $pageUrl
    exit 0
}

New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
Remove-Item -LiteralPath $stdoutLog, $stderrLog -Force -ErrorAction SilentlyContinue
$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$previewScript`"",
    '-Port', [string]$Port)
$process = Start-Process -FilePath $powershell -ArgumentList $arguments -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
writeState $process $Port

$deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
do {
    if ($process.HasExited) { break }
    if (testHealthyPreview) {
        Write-Host "TroubleshootJS preview started (PID $($process.Id))."
        Write-Host $pageUrl
        exit 0
    }
    Start-Sleep -Milliseconds 200
} while ([DateTime]::UtcNow -lt $deadline)

if (-not $process.HasExited) {
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
}
removeState
$details = if (Test-Path -LiteralPath $stderrLog) {
    (Get-Content -LiteralPath $stderrLog -Raw -ErrorAction SilentlyContinue).Trim()
} else { '' }
throw "TroubleshootJS preview failed to start on port $Port. $details"
