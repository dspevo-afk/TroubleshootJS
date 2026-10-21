[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('canaries','control','variant')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [Parameter(Mandatory=$true)][string]$PowerShellExe,
      [Parameter(Mandatory=$true)][string]$PrivateRoot)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packet=$PSScriptRoot
$repo=(Resolve-Path -LiteralPath (Join-Path $packet '../../../..')).Path
$attempt=Join-Path $packet $Phase
if(Test-Path -LiteralPath $attempt){throw 'Preserve prior phase before retry'}
New-Item -ItemType Directory -Path $attempt|Out-Null
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packet 'export_inputs.py') audit $PrivateRoot *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Input audit failed '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Captured before selected private span-probe adapter; root observes actual tool exit before separate birth checks'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$adapter=Join-Path $PrivateRoot 'common/verify-two-pad-span.ps1'
$exporter=Join-Path $PrivateRoot 'common/SparseNormalBoardDiagnostic.java'
$arm=if($Phase -eq 'variant'){'variant'}else{'control'}
$generator=Join-Path $PrivateRoot ($arm+'/SeededPcbLayoutGenerator.java')
$planner=Join-Path $PrivateRoot ($arm+'/PcbPlacementPlanner.java')
$compactor=Join-Path $PrivateRoot ($arm+'/PcbPlacementCompactor.java')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    if($Phase -eq 'canaries'){
        $checks=@(foreach($kind in @('seed','family','generator','planner','compactor')){
            $family=if($kind -eq 'family'){'RC_DELAY'}else{'COMPOSED_CONTROLLED_INDICATOR'}
            $seed=if($kind -eq 'seed'){'-02750102463469069236'}else{'-2750102463469069236'}
            $missing=Join-Path $PrivateRoot ('missing-'+$kind+'-canary.java')
            if(Test-Path -LiteralPath $missing){throw 'Canary path unexpectedly exists'}
            $selectedGenerator=if($kind -eq 'generator'){$missing}else{$generator}
            $selectedPlanner=if($kind -eq 'planner'){$missing}else{$planner}
            $selectedCompactor=if($kind -eq 'compactor'){$missing}else{$compactor}
            $log=Join-Path $PrivateRoot ('invalid-'+$kind+'.txt')
            if(Test-Path -LiteralPath $log){throw 'Preserve prior canary log'}
            $negativeWatch=[Diagnostics.Stopwatch]::StartNew()
            & $PowerShellExe -NoProfile -File $adapter -TaskRepositoryRoot $repo -TaskExportSource $exporter -TaskGeneratorSource $selectedGenerator -TaskPlannerSource $selectedPlanner -TaskCompactorSource $selectedCompactor -TaskExportFamily $family -TaskExportSeed $seed -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('SparseNormalBoardDiagnostic') *> $log
            $negativeCode=[int]$LASTEXITCODE;$negativeWatch.Stop()
            $parameter=if($kind -eq 'seed'){'TaskExportSeed'}elseif($kind -eq 'family'){'TaskExportFamily'}elseif($kind -eq 'generator'){'TaskGeneratorSource'}elseif($kind -eq 'planner'){'TaskPlannerSource'}else{'TaskCompactorSource'}
            $raw=Get-Content -LiteralPath $log -Raw
            if($negativeCode -ne 1 -or $raw -notmatch ("Cannot validate argument on parameter '"+$parameter+"'")){throw ('Parameter binding did not reject '+$kind)}
            if($raw -match 'CURRENT_CONTRACT_|javac|PASS: focused current contracts'){throw 'Invalid binding reached native work'}
            @{kind=$kind;status='PASS_REJECTED_AT_PARAMETER_BINDING';actualAdapterExitCode=$negativeCode;elapsedSeconds=$negativeWatch.Elapsed.TotalSeconds;privateLogSha256=(Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant();privateLogBytes=(Get-Item -LiteralPath $log).Length}
        })
        @{checks=$checks;limits='Actual selected private adapter rejects noncanonical seed, excluded family and absent generator/planner/compactor before compiler/native work; raw stderr private/hash-bound'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'canaries.json')
        $code=0
    }else{
        & $PowerShellExe -NoProfile -File $adapter -TaskRepositoryRoot $repo -TaskExportSource $exporter -TaskGeneratorSource $generator -TaskPlannerSource $planner -TaskCompactorSource $compactor -TaskExportFamily COMPOSED_CONTROLLED_INDICATOR -TaskExportSeed '-2750102463469069236' -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('SparseNormalBoardDiagnostic') -ReceiptOutputPath (Join-Path $attempt 'receipt.txt') *> (Join-Path $attempt 'native.txt')
        $code=[int]$LASTEXITCODE
    }
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Two-pad span probe failed '+$Phase+' exit='+$code)}
Write-Output ('PASS two-pad span selected private adapter phase '+$Phase)
