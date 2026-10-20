[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,[Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
foreach($suffix in @('-input-audit-before.txt','-input-audit-after.txt','-process-launch.json','-process-cleanup.json','-timing.json','.txt','-receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot ('fixture'+$suffix))){throw 'Preserve prior fixture attempt'}
}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $PSScriptRoot 'inputs.py') audit candidate *> (Join-Path $PSScriptRoot ('fixture-input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw 'Fixture source audit failed'}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase='fixture';drivers=$drivers}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'fixture-process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    Push-Location $repoRoot
    try{& ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('P07TwoLayerContractTest') -ReceiptOutputPath (Join-Path $PSScriptRoot 'fixture-receipt.txt') *> (Join-Path $PSScriptRoot 'fixture.txt');$code=[int]$LASTEXITCODE}finally{Pop-Location}
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase='fixture';status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'fixture-timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Via-cap fixture failed exit='+$code)}
Write-Output 'PASS actual maintained via-cap fixture operation'
