[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$state=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'local-state.json') -Raw | ConvertFrom-Json
$copyRoot=(Resolve-Path -LiteralPath $state.scratch).Path
if(-not (Split-Path -Leaf $copyRoot).StartsWith('tsj-routed-strip-')){throw 'Unexpected copy owner'}
foreach($name in @('process-launch.json','timing.json','native.txt','native-receipt.txt')) {
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot $name)) {
        throw 'Prior native artifacts exist; preserve completed attempt before another run'
    }
}
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/routed-strip-cause-r1/run_native.ps1'}
})
[ordered]@{drivers=$drivers;ownership='Captured before current15 read-only strip cause work; isolated source copy'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$status='FAIL';$code=1
Push-Location $copyRoot
try {
    $suites=@('PcbRoutedStripCauseContractTest','PcbRoutedStripCauseContractTestGroup2')
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $PSScriptRoot 'native-receipt.txt') *> (Join-Path $PSScriptRoot 'native.txt')
    if(-not $?){throw 'Native routed-strip cause phase failed'}
    $status='PASS';$code=0
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;suiteCount=2;
        scope='Two informed current15 routes, one per bounded JVM; observation only and independent signature fixtures';
        terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'timing.json')
}
exit $code
