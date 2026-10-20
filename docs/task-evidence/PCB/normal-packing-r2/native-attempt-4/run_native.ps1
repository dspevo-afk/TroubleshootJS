[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('unit','routes')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packetRoot=(Resolve-Path -LiteralPath $PSScriptRoot).Path
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..')).Path
$protocol=Get-Content -LiteralPath (Join-Path $packetRoot 'protocol.json') -Raw | ConvertFrom-Json
$scopeText=[string]$protocol.scope
if([string]::IsNullOrWhiteSpace($scopeText)){throw 'Normal-packing-r2 protocol scope is missing'}
if([int]$protocol.nativeJvmMillis -ne 60000 -or [int]$protocol.nativeCaseMillis -ne 60000){
    throw 'Normal-packing-r2 native JVM/case budget changed'
}
$runRoot=(Resolve-Path -LiteralPath $repoRoot).Path
if($runRoot -cne $repoRoot){throw 'Unexpected main worktree root'}
$frozenInput='candidate-inputs.json'
if(-not (Test-Path -LiteralPath (Join-Path $packetRoot $frozenInput))){throw ('Missing frozen input seal: '+$frozenInput)}
foreach($suffix in @('-process-launch.json','-timing.json','-process-cleanup.json','-log-sanitization.json','.txt','-receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $packetRoot ($Phase+$suffix))){
        throw 'Prior phase artifacts exist; preserve completed attempt before another run'
    }
}
$baseline=Get-Content -LiteralPath (Join-Path $packetRoot 'baseline-validation.json') -Raw | ConvertFrom-Json
if($baseline.assessmentStatus -ne 'PASS'){throw 'Baseline validation prerequisite has not passed'}
if($Phase -eq 'routes'){
    $unit=Get-Content -LiteralPath (Join-Path $packetRoot 'unit-validation.json') -Raw | ConvertFrom-Json
    if($unit.assessmentStatus -ne 'PASS'){throw 'Unit validation prerequisite has not passed'}
}
function Invoke-FrozenInputAudit([string]$Command){
    & $PythonExe -X utf8 -B (Join-Path $packetRoot 'inputs.py') $Command
    if($LASTEXITCODE -ne 0){throw ('Frozen input audit failed: '+$Command)}
}
Invoke-FrozenInputAudit 'audit-candidate'
$suiteFile=if($Phase -eq 'unit'){'unit-suites.json'}else{'routes-suites.json'}
. (Join-Path $packetRoot 'suite_transport.ps1')
$suites=@(Get-NormalPackingR2SuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $packetRoot $suiteFile) -Raw))
$expectedCount=if($Phase -eq 'unit'){4}else{8}
if($suites.Count -ne $expectedCount){throw ('Unexpected '+$Phase+' suite count')}
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
if($null -eq $mine -or [int]$mine.ParentProcessId -le 0){throw 'Unable to capture native driver parent'}
$driverIds=@([int]$mine.ParentProcessId,[int]$PID) | Select-Object -Unique
$drivers=@($driverIds | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/normal-packing-r2/run_native.ps1'}
})
[ordered]@{drivers=$drivers;phase=$Phase;scope=$scopeText;
    ownership='Captured before bounded normal-packing-r2 phase in the main worktree'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $packetRoot ($Phase+'-process-launch.json'))
$watch=[Diagnostics.Stopwatch]::StartNew()
$status='FAIL';$code=2;$nativeError=$null;$postAuditError=$null;$timingError=$null;$entered=$false
try {
    try {
        Push-Location $runRoot;$entered=$true
        & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $packetRoot ($Phase+'-receipt.txt')) *> (Join-Path $packetRoot ($Phase+'.txt'))
        $code=[int]$LASTEXITCODE
        if($code -eq 0){$status='PASS'}else{$nativeError=[InvalidOperationException]::new('Normal-packing-r2 native driver exited with code '+$code)}
    } catch {
        $nativeError=$_.Exception;$code=2
    } finally {
        if($entered){Pop-Location;$entered=$false}
    }
} finally {
    $watch.Stop()
    try { Invoke-FrozenInputAudit 'audit-candidate' } catch { $postAuditError=$_.Exception;if($null -eq $nativeError){$status='FAIL';$code=2} }
    try {
        [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;suiteCount=$suites.Count;
            phase=$Phase;scope=$scopeText;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $packetRoot ($Phase+'-timing.json'))
    } catch { $timingError=$_.Exception }
}
if($null -ne $nativeError){
    [Console]::Error.WriteLine($nativeError.ToString())
    exit $code
}
if($null -ne $postAuditError){throw $postAuditError}
if($null -ne $timingError){throw $timingError}
exit $code
