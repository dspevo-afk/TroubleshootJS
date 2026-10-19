[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$localState=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'local-state.json') -Raw | ConvertFrom-Json
$copyRoot=(Resolve-Path -LiteralPath $localState.scratch).Path
if(-not (Split-Path -Leaf $copyRoot).StartsWith('tsj-score-term-')){throw 'Unexpected copy owner'}
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/placement-score-terms-r1/run_focused.ps1'}
})
[ordered]@{drivers=$drivers;ownership='Captured before native work; isolated accepted source copy'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$status='FAIL';$code=1
Push-Location $copyRoot
try {
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite 'PcbPlacementScoreTermsContractTest' -ReceiptOutputPath (Join-Path $PSScriptRoot 'native-receipt.txt') *> (Join-Path $PSScriptRoot 'native.txt')
    if(-not $?){throw 'Native score diagnostic failed'}
    $status='PASS';$code=0
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;suiteCount=1;
        terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'native-timing.json')
}
exit $code
