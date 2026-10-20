[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('canaries','npn')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [Parameter(Mandatory=$true)][string]$PowerShellExe,
      [Parameter(Mandatory=$true)][string]$PrivateAdapterPath)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packetRoot=$PSScriptRoot
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $packetRoot '../../../..')).Path
$attempt=Join-Path $packetRoot $Phase
if(Test-Path -LiteralPath $attempt){throw 'Preserve prior phase before retry'}
$adapter=Get-Content -LiteralPath (Join-Path $packetRoot 'adapter-manifest.json') -Raw|ConvertFrom-Json
if((Get-FileHash -LiteralPath $PrivateAdapterPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $adapter.generatedSha256){throw 'Private adapter changed'}
New-Item -ItemType Directory -Path $attempt|Out-Null
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packetRoot 'inputs.py') audit *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Input audit failed '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Captured before actual selected private adapter invocation; root observes exit separately'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$exportSource=Join-Path $packetRoot 'export-source/SparseNormalBoardDiagnostic.java'
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    if($Phase -eq 'canaries'){
        $checks=@(foreach($kind in @('seed','family')){
            $family=if($kind -eq 'family'){'UNSUPPORTED_FAMILY'}else{'NPN_LOW_SIDE_SWITCH'}
            $seed=if($kind -eq 'seed'){'not-a-signed-long'}else{'380469407799690561'}
            $privateLog=Join-Path (Split-Path -Parent $PrivateAdapterPath) ('invalid-'+$kind+'.txt')
            if(Test-Path -LiteralPath $privateLog){throw 'Preserve prior canary raw log'}
            $negativeWatch=[Diagnostics.Stopwatch]::StartNew()
            & $PowerShellExe -NoProfile -File $PrivateAdapterPath -TaskRepositoryRoot $repoRoot -TaskExportSource $exportSource -TaskExportFamily $family -TaskExportSeed $seed -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('SparseNormalBoardDiagnostic') *> $privateLog
            $negativeCode=[int]$LASTEXITCODE;$negativeWatch.Stop()
            $parameter=if($kind -eq 'seed'){'TaskExportSeed'}else{'TaskExportFamily'}
            $raw=Get-Content -LiteralPath $privateLog -Raw
            if($negativeCode -ne 1 -or $raw -notmatch ("Cannot validate argument on parameter '"+$parameter+"'")){throw ('Parameter binding did not reject '+$kind)}
            if($raw -match 'CURRENT_CONTRACT_|javac|PASS: focused current contracts'){throw 'Invalid argument reached native execution'}
            @{kind=$kind;status='PASS_REJECTED_AT_PARAMETER_BINDING';actualAdapterExitCode=$negativeCode;elapsedSeconds=$negativeWatch.Elapsed.TotalSeconds;privateLogSha256=(Get-FileHash -LiteralPath $privateLog -Algorithm SHA256).Hash.ToLowerInvariant();privateLogBytes=(Get-Item -LiteralPath $privateLog).Length}
        })
        @{checks=$checks;limits='Actual selected private adapter binding before JDK/compiler/native work; raw stderr private and hash-bound'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'canaries.json')
        $code=0
    }else{
        $family='NPN_LOW_SIDE_SWITCH'
        $seed='380469407799690561'
        & $PowerShellExe -NoProfile -File $PrivateAdapterPath -TaskRepositoryRoot $repoRoot -TaskExportSource $exportSource -TaskExportFamily $family -TaskExportSeed $seed -JavaHome $JavaHome -PythonExe $PythonExe -Suite @('SparseNormalBoardDiagnostic') -ReceiptOutputPath (Join-Path $attempt 'receipt.txt') *> (Join-Path $attempt 'native.txt')
        $code=[int]$LASTEXITCODE
    }
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Sparse diagnostic phase failed '+$Phase+' exit='+$code)}
Write-Output ('PASS sparse normal board task adapter phase '+$Phase)
