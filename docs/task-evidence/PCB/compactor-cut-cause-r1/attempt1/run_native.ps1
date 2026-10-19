[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('unit','routes')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$state=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'local-state.json') -Raw | ConvertFrom-Json
$copyRoot=(Resolve-Path -LiteralPath $state.scratch).Path
if(-not (Split-Path -Leaf $copyRoot).StartsWith('tsj-compactor-cut-cause-')){throw 'Unexpected copy owner'}
foreach($suffix in @('-process-launch.json','-timing.json','.txt','-receipt.txt')) {
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot ($Phase+$suffix))) {
        throw 'Prior phase artifacts exist; preserve completed attempt before another run'
    }
}
if($Phase -eq 'routes'){
    $prereq=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-validation.json') -Raw | ConvertFrom-Json
    if($prereq.status -ne 'PASS'){throw 'Unit prerequisites have not passed'}
}
& $PythonExe -X utf8 -B (Join-Path $PSScriptRoot 'inputs.py') audit
if($LASTEXITCODE -ne 0){throw 'Pre-runtime frozen input audit failed'}
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/compactor-cut-cause-r1/run_native.ps1'}
})
[ordered]@{drivers=$drivers;phase=$Phase;ownership='Captured before bounded native compactor-cut phase; unique source copy'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-process-launch.json'))
$suiteFile=if($Phase -eq 'routes'){'routes-suites.json'}else{'unit-suites.json'}
$suiteRows=Get-Content -LiteralPath (Join-Path $PSScriptRoot $suiteFile) -Raw | ConvertFrom-Json
$suites=@($suiteRows | ForEach-Object { [string]$_[0] })
$watch=[Diagnostics.Stopwatch]::StartNew();$status='FAIL';$code=2
Push-Location $copyRoot
try {
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $PSScriptRoot ($Phase+'-receipt.txt')) *> (Join-Path $PSScriptRoot ($Phase+'.txt'))
    $code=[int]$LASTEXITCODE
    if($code -eq 0){$status='PASS'}
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;suiteCount=$suites.Count;
        phase=$Phase;scope='Native compactor cut observation; unchanged per-JVM/search/physical limits';
        terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-timing.json'))
}
exit $code
