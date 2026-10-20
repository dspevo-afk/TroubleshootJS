[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [Parameter(Mandatory=$true)][string]$PowerShellExe,
      [Parameter(Mandatory=$true)][string]$PrivateAdapterPath)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$attempt=Join-Path $PSScriptRoot 'clock-witness'
foreach($name in @('process-launch.json','timing.json','native.txt','receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $attempt $name)){throw 'Preserve prior witness attempt'}
}
if((Get-FileHash -LiteralPath $PrivateAdapterPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne '19af62225c5b3b4242343c81ebfa34a707df572eb1db80af5038d5ffdaa1dbec'){throw 'Adapter changed'}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $PSScriptRoot 'clock_inputs.py') audit *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw 'Witness inputs changed'}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase='clock-witness';drivers=$drivers}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    & $PowerShellExe -NoProfile -File $PrivateAdapterPath -TaskRepositoryRoot $repoRoot -TaskExportSource (Join-Path $PSScriptRoot 'clock-source/FreshRb30GapDiagnostic.java') -TaskExportSeed '10' -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('FreshRb30GapDiagnostic') -ReceiptOutputPath (Join-Path $attempt 'receipt.txt') *> (Join-Path $attempt 'native.txt')
    $code=[int]$LASTEXITCODE
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase='clock-witness';status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw 'Actual clock witness failed'}
Write-Output 'PASS actual clock witness operation'
