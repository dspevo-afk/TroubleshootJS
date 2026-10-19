[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$localState=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'local-state.json') -Raw | ConvertFrom-Json
$copyRoot=(Resolve-Path -LiteralPath $localState.scratch).Path
if(-not (Split-Path -Leaf $copyRoot).StartsWith('tsj-vacancy-grid-')){throw 'Unexpected copy owner'}
foreach($name in @('unit-process-launch.json','unit-timing.json','unit-native.txt','unit-native-receipt.txt')) {
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot $name)) {
        throw 'Prior unit artifacts exist; preserve the completed attempt before another run'
    }
}
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/vacancy-origin-grid-r1/run_unit.ps1'}
})
[ordered]@{drivers=$drivers;ownership='Captured before pure native work; isolated accepted source copy'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$status='FAIL';$code=1
Push-Location $copyRoot
try {
    $suites=@('PcbVacancyTargetsContractTest','PcbNearestVacancyPreferenceContractTest','PcbVacancyOriginGridContractTest')
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $PSScriptRoot 'unit-native-receipt.txt') *> (Join-Path $PSScriptRoot 'unit-native.txt')
    if(-not $?){throw 'Pure vacancy-grid native phase failed'}
    $status='PASS';$code=0
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;suiteCount=3;
        scope='Legacy vacancy/nearest fixtures and new unused pure grid helper; generation and routing NOT RUN';
        terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-timing.json')
}
exit $code
