[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$localState=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'local-state.json') -Raw | ConvertFrom-Json
$copyRoot=(Resolve-Path -LiteralPath $localState.scratch).Path
if(-not (Split-Path -Leaf $copyRoot).StartsWith('tsj-vacancy-grid-')){throw 'Unexpected copy owner'}
foreach($name in @('trial-process-launch.json','trial-timing.json','trial-native.txt','trial-native-receipt.txt')) {
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot $name)) {
        throw 'Prior trial artifacts exist; preserve completed attempt before another run'
    }
}
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/vacancy-origin-grid-r1/run_trial.ps1'}
})
[ordered]@{drivers=$drivers;ownership='Captured before native trial; isolated explicit16 source copy'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'trial-process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$status='FAIL';$code=1
Push-Location $copyRoot
try {
    $suites=@('PcbVacancyTargetsContractTest','PcbNearestVacancyPreferenceContractTest',
        'PcbVacancyOriginGridContractTest','PcbVacancyGridTrialTargetContractTest',
        'PcbVacancyGridTrialContractTest','PcbVacancyGridTrialContractTestGroup2',
        'PcbVacancyGridTrialContractTestGroup3','PcbVacancyGridTrialContractTestGroup4',
        'PcbVacancyGridTrialContractTestGroup5','PcbVacancyGridTrialContractTestGroup6',
        'PcbVacancyGridTrialContractTestGroup7')
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $PSScriptRoot 'trial-native-receipt.txt') *> (Join-Path $PSScriptRoot 'trial-native.txt')
    if(-not $?){throw 'Native vacancy-grid trial failed'}
    $status='PASS';$code=0
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;suiteCount=11;
        scope='Pure3, namespace/raw-candidate target,14 explicit16 routed roots in7 bounded partitions; metric reader separate';
        terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'trial-timing.json')
}
exit $code
