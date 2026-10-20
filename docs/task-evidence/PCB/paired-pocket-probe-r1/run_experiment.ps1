[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('canary','baseline','cold','repeat')][string]$Phase,
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
$baselineAdapter=Join-Path $PrivateDirectory 'baseline-adapter.ps1'
$experimentAdapter=Join-Path $PrivateDirectory 'experiment-adapter.ps1'
$owner=Join-Path $PrivateDirectory 'PcbPlacementPlanner.java'
$export=Join-Path $packet 'export-source/SparseNormalBoardDiagnostic.java'
New-Item -ItemType Directory -Path $attempt|Out-Null
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packet 'inputs.py') audit $PrivateDirectory *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Input audit failed '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Task-only native probe; root separately observes actual tool exit'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    $arguments=@('-NoProfile','-File',$baselineAdapter,'-TaskRepositoryRoot',$root,'-TaskExportSource',$export,
        '-TaskExportFamily','PARALLEL_DUAL_INDICATOR','-TaskExportSeed','-9223372036854775808',
        '-JavaHome',$JavaHome,'-PythonExe',$PythonExe,'-Suite','SparseNormalBoardDiagnostic')
    if($Phase -eq 'canary'){
        $checks=@()
        foreach($kind in @('seed','owner')){
            $bad=@($arguments)
            if($kind -eq 'seed'){$bad[[array]::IndexOf($bad,'-TaskExportSeed')+1]='01'}
            else{
                $bad[2]=$experimentAdapter
                $missing=Join-Path $PrivateDirectory 'does-not-exist.java'
                if(Test-Path -LiteralPath $missing){throw 'Negative path unexpectedly exists'}
                $bad+=@('-TaskPlannerSource',$missing)
            }
            $rawPath=Join-Path $PrivateDirectory ('invalid-'+$kind+'.txt')
            if(Test-Path -LiteralPath $rawPath){throw 'Preserve prior canary log'}
            & $PowerShellExe @bad *> $rawPath
            $negativeCode=[int]$LASTEXITCODE;$raw=Get-Content -LiteralPath $rawPath -Raw
            $parameter=if($kind -eq 'seed'){'TaskExportSeed'}else{'TaskPlannerSource'}
            if($negativeCode -ne 1 -or $raw -notmatch ("Cannot validate argument on parameter '"+$parameter+"'")){throw ('Wrong binding failure '+$kind)}
            if($raw -match 'CURRENT_CONTRACT_|javac|PASS: focused current contracts'){throw 'Invalid input reached native execution'}
            $checks+=@{kind=$kind;status='PASS_REJECTED_AT_PARAMETER_BINDING';actualAdapterExitCode=$negativeCode;privateLogSha256=(Get-FileHash -LiteralPath $rawPath -Algorithm SHA256).Hash.ToLowerInvariant();privateLogBytes=(Get-Item -LiteralPath $rawPath).Length}
        }
        @{checks=$checks}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $attempt 'canaries.json')
        $code=0
    }else{
        if($Phase -ne 'baseline'){$arguments[2]=$experimentAdapter;$arguments+=@('-TaskPlannerSource',$owner)}
        $arguments+=@('-ReceiptOutputPath',(Join-Path $attempt 'receipt.txt'))
        & $PowerShellExe @arguments *> (Join-Path $attempt 'native.txt')
        $code=[int]$LASTEXITCODE
    }
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Private paired experiment failed '+$Phase+' exit='+$code)}
Write-Output ('PASS paired probe phase '+$Phase)
