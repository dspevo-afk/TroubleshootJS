[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [Parameter(Mandatory=$true)][string]$PowerShellExe,
      [Parameter(Mandatory=$true)][string]$PrivateAdapterPath)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$attempt=Join-Path $PSScriptRoot 'export-attempt1'
foreach($name in @('process-launch.json','process-cleanup.json','timing.json','native.txt','receipt.txt','invalid-seed-canary.json')){
    if(Test-Path -LiteralPath (Join-Path $attempt $name)){throw 'Preserve previous geometry-export attempt'}
}
$adapter=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'export-adapter-manifest.json') -Raw|ConvertFrom-Json
if((Get-FileHash -LiteralPath $PrivateAdapterPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $adapter.generatedSha256){throw 'Private generated adapter bytes changed'}
$exportSource=Join-Path $PSScriptRoot 'export-source/FreshRb30GapDiagnostic.java'
function Audit-ExportInputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $PSScriptRoot 'export_inputs.py') audit *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Export input audit failed '+$Side)}
}
Audit-ExportInputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase='selected-geometry-export';drivers=$drivers;scope='Actual task-owned native adapter, two births captured before invalid/valid seed invocations'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    $privateDirectory=Split-Path -Parent $PrivateAdapterPath
    $negativePath=Join-Path $privateDirectory 'invalid-seed-canary.txt'
    if(Test-Path -LiteralPath $negativePath){throw 'Preserve prior invalid-seed stderr'}
    $negativeWatch=[Diagnostics.Stopwatch]::StartNew()
    & $PowerShellExe -NoProfile -File $PrivateAdapterPath -TaskRepositoryRoot $repoRoot -TaskExportSource $exportSource -TaskExportSeed 'not-a-signed-long' -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('FreshRb30GapDiagnostic') *> $negativePath
    $negativeCode=[int]$LASTEXITCODE
    $negativeWatch.Stop()
    $negativeText=Get-Content -LiteralPath $negativePath -Raw
    if($negativeCode -ne 1 -or $negativeText -notmatch "Cannot validate argument on parameter 'TaskExportSeed'"){throw 'Actual adapter did not reject invalid seed at parameter binding'}
    @{status='PASS_REJECTED_INVALID_SEED';actualAdapterExitCode=$negativeCode;elapsedSeconds=$negativeWatch.Elapsed.TotalSeconds;privateLogSha256=(Get-FileHash -LiteralPath $negativePath -Algorithm SHA256).Hash.ToLowerInvariant();privateLogBytes=(Get-Item -LiteralPath $negativePath).Length;limits='Actual selected generated PowerShell adapter parameter binding, before JDK/compiler/native work; raw stderr private/hash-bound'}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'invalid-seed-canary.json')
    $seed=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'request.json') -Raw|ConvertFrom-Json).seed
    & $PowerShellExe -NoProfile -File $PrivateAdapterPath -TaskRepositoryRoot $repoRoot -TaskExportSource $exportSource -TaskExportSeed $seed -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('FreshRb30GapDiagnostic') -ReceiptOutputPath (Join-Path $attempt 'receipt.txt') *> (Join-Path $attempt 'native.txt')
    $code=[int]$LASTEXITCODE
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase='selected-geometry-export';status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-ExportInputs 'after'
if($code -ne 0){throw ('Selected geometry export failed exit='+$code)}
Write-Output 'PASS task-owned selected RB30 geometry export adapter'
