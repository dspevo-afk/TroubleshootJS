param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('old-negative', 'new-positive', 'new-negative')]
    [string]$Mode,
    [Parameter(Mandatory=$true)]
    [string]$RunnerPath
)

$ErrorActionPreference = 'Stop'
$prepRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$runnerResolved = [System.IO.Path]::GetFullPath($RunnerPath)
if (-not (Test-Path -LiteralPath $runnerResolved -PathType Leaf)) {
    throw "Runner script not found: $runnerResolved"
}

if ($Mode -eq 'new-positive') {
    $fixtureName = 'positive'
} else {
    $fixtureName = 'negative'
}
$repoPath = Join-Path $prepRoot ("fixtures\{0}" -f $fixtureName)
$specPath = Join-Path $prepRoot ("specs\{0}.json" -f $fixtureName)
$runId = [Guid]::NewGuid().ToString('N')
$outputPath = Join-Path $prepRoot ("results\{0}-{1}" -f $Mode, $runId)
$logPath = Join-Path $prepRoot ("logs\{0}-{1}.log" -f $Mode, $runId)
$auditPath = Join-Path $prepRoot 'audit_canary_result.py'

foreach ($requiredPath in @(
        (Join-Path $repoPath 'src'),
        (Join-Path $repoPath 'war\circuitjs.html'),
        $specPath,
        $auditPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Prepared canary input is missing: $requiredPath"
    }
}
if (Test-Path -LiteralPath $outputPath) {
    throw "Refusing to overwrite prior canary output: $outputPath"
}

Write-Host ("Mode: {0}; fixture: {1}" -f $Mode, $fixtureName)
Write-Host ("Runner: {0}" -f $runnerResolved)
Write-Host ("Output: {0}" -f $outputPath)
& python -B $runnerResolved $repoPath $outputPath $specPath *> $logPath
$runnerExit = $LASTEXITCODE
Write-Host ("Runner exit: {0}; log: {1}" -f $runnerExit, $logPath)
& python -B $auditPath $Mode $outputPath $runnerExit
$auditExit = $LASTEXITCODE
exit $auditExit
