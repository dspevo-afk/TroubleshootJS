[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('canary','current','nohook')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [Parameter(Mandatory=$true)][string]$PowerShellExe,
      [Parameter(Mandatory=$true)][string]$PrivateDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packet=$PSScriptRoot
$root=(Resolve-Path -LiteralPath (Join-Path $packet '../../../..')).Path
$attempt=Join-Path $packet $Phase
if(Test-Path -LiteralPath $attempt){throw 'Preserve previous diagnostic attempt'}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packet 'inputs.py') audit $PrivateDirectory *> (Join-Path $attempt ('input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Input audit failed '+$Side)}
}
New-Item -ItemType Directory -Path $attempt|Out-Null
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Task-only original-assertion LED diagnostic; root separately observes actual tool exit'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $attempt 'process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    $adapter=Join-Path $PrivateDirectory ($Phase+'-adapter.ps1')
    if($Phase -eq 'canary'){$adapter=Join-Path $PrivateDirectory 'nohook-adapter.ps1'}
    $arguments=@('-NoProfile','-File',$adapter,'-TaskRepositoryRoot',$root,
        '-TaskExportSource',(Join-Path $packet 'export-source/SparseNormalBoardDiagnostic.java'),
        '-TaskExportFamily','LED_INDICATOR','-TaskExportSeed','0','-JavaHome',$JavaHome,
        '-PythonExe',$PythonExe,'-Suite','SparseNormalBoardDiagnostic')
    if($Phase -eq 'canary'){
        $missing=Join-Path $PrivateDirectory 'missing-generator.java'
        if(Test-Path -LiteralPath $missing){throw 'Negative input unexpectedly exists'}
        $arguments+=@('-TaskGeneratorSource',$missing)
        $rawPath=Join-Path $PrivateDirectory 'invalid-generator-binding.txt'
        if(Test-Path -LiteralPath $rawPath){throw 'Preserve previous binding log'}
        & $PowerShellExe @arguments *> $rawPath
        $negativeCode=[int]$LASTEXITCODE;$raw=Get-Content -LiteralPath $rawPath -Raw
        if($negativeCode -ne 1 -or $raw -notmatch "Cannot validate argument on parameter 'TaskGeneratorSource'"){throw 'Wrong binding failure'}
        if($raw -match 'CURRENT_CONTRACT_|javac|PASS: focused current contracts'){throw 'Invalid input reached native execution'}
        @{status='PASS_REJECTED_AT_PARAMETER_BINDING';actualAdapterExitCode=$negativeCode;privateLogSha256=(Get-FileHash -LiteralPath $rawPath -Algorithm SHA256).Hash.ToLowerInvariant();privateLogBytes=(Get-Item -LiteralPath $rawPath).Length}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'canary.json')
        $code=0
    }else{
        if($Phase -eq 'nohook'){$arguments+=@('-TaskGeneratorSource',(Join-Path $PrivateDirectory 'SeededPcbLayoutGenerator.java'))}
        $arguments+=@('-ReceiptOutputPath',(Join-Path $attempt 'receipt.txt'))
        & $PowerShellExe @arguments *> (Join-Path $attempt 'native.txt')
        $code=[int]$LASTEXITCODE
    }
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Diagnostic phase failed '+$Phase+' exit='+$code)}
Write-Output ('PASS paired diversity diagnostic phase '+$Phase)
