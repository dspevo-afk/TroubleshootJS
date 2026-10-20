[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('canary','cold','repeat')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [Parameter(Mandatory=$true)][string]$PowerShellExe,
      [Parameter(Mandatory=$true)][string]$PrivateDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packet=$PSScriptRoot
$root=(Resolve-Path -LiteralPath (Join-Path $packet '../../../..')).Path
$attempt=Join-Path $packet $Phase
if(Test-Path -LiteralPath $attempt){throw 'Preserve previous attempt'}
$adapter=Join-Path $PrivateDirectory 'verify-task-export.ps1'
$owner=Join-Path $PrivateDirectory 'PcbPlacementPlanner.java'
$manifest=Get-Content -LiteralPath (Join-Path $packet 'private-input-manifest.json') -Raw|ConvertFrom-Json
foreach($item in @(@($adapter,$manifest.privateAdapterSha256),@($owner,$manifest.privateOwnerSha256))){
    if((Get-FileHash -LiteralPath $item[0] -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item[1]){throw 'Private native input bytes changed'}
}
$export=(Resolve-Path -LiteralPath (Join-Path $packet '../sparse-board-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java')).Path
New-Item -ItemType Directory -Path $attempt|Out-Null
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packet 'experiment_inputs.py') audit *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Input audit failed '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Task-only private planner substitution; root observes actual tool exit separately'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    if($Phase -eq 'canary'){
        $missing=Join-Path $PrivateDirectory 'does-not-exist.java'
        if(Test-Path -LiteralPath $missing){throw 'Negative owner path exists unexpectedly'}
        $rawPath=Join-Path $PrivateDirectory 'invalid-owner-path.txt'
        if(Test-Path -LiteralPath $rawPath){throw 'Preserve prior negative log'}
        & $PowerShellExe -NoProfile -File $adapter -TaskRepositoryRoot $root -TaskExportSource $export -TaskPlannerSource $missing -TaskExportFamily COMPOSED_CONTROLLED_INDICATOR -TaskExportSeed '7141843758046216173' -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('SparseNormalBoardDiagnostic') *> $rawPath
        $negativeCode=[int]$LASTEXITCODE;$raw=Get-Content -LiteralPath $rawPath -Raw
        if($negativeCode -ne 1 -or $raw -notmatch "Cannot validate argument on parameter 'TaskPlannerSource'"){throw 'Missing private owner did not reject at binding'}
        if($raw -match 'CURRENT_CONTRACT_|javac|PASS: focused current contracts'){throw 'Invalid owner reached native execution'}
        @{status='PASS_REJECTED_MISSING_PRIVATE_OWNER_AT_BINDING';actualAdapterExitCode=$negativeCode;privateLogSha256=(Get-FileHash -LiteralPath $rawPath -Algorithm SHA256).Hash.ToLowerInvariant();privateLogBytes=(Get-Item -LiteralPath $rawPath).Length}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'canary.json')
        $code=0
    }else{
        & $PowerShellExe -NoProfile -File $adapter -TaskRepositoryRoot $root -TaskExportSource $export -TaskPlannerSource $owner -TaskExportFamily COMPOSED_CONTROLLED_INDICATOR -TaskExportSeed '7141843758046216173' -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('SparseNormalBoardDiagnostic') -ReceiptOutputPath (Join-Path $attempt 'receipt.txt') *> (Join-Path $attempt 'native.txt')
        $code=[int]$LASTEXITCODE
    }
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Private planner experiment failed '+$Phase+' exit='+$code)}
Write-Output ('PASS task-only second sweep phase '+$Phase)
