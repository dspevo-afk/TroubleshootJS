[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('contracts','census','build')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packet=$PSScriptRoot
$root=(Resolve-Path -LiteralPath (Join-Path $packet '../../../..')).Path
$control=Get-Content -LiteralPath (Join-Path $packet 'reader-control-r2-operation.json') -Raw|ConvertFrom-Json
if($control.status -cne 'PASS' -or [int]$control.actualExecExitCode -ne 0){throw 'R2 real reader control has not passed'}
foreach($suffix in @('-input-audit-before.txt','-input-audit-after.txt','-process-launch.json','-process-cleanup.json','-timing.json','.txt','-receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $packet ($Phase+$suffix))){throw 'Preserve previous phase artifacts'}
}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packet 'candidate_inputs.py') audit *> (Join-Path $packet ($Phase+'-input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Input audit failed '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Captured before maintained final-source capacitor lid phase; actual tool exit precedes root observation'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $packet ($Phase+'-process-launch.json'))
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    Push-Location $root
    try{
        if($Phase -eq 'build'){
            & ./scripts/build.ps1 -JavaHome $JavaHome -Style OBF -Target Compile -ProcessTimeoutSeconds 900 *> (Join-Path $packet ($Phase+'.txt'))
        }else{
            $suites=@((Get-Content -LiteralPath (Join-Path $packet 'suites.json') -Raw|ConvertFrom-Json).$Phase)
            & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $packet ($Phase+'-receipt.txt')) *> (Join-Path $packet ($Phase+'.txt'))
        }
        $code=[int]$LASTEXITCODE
    }finally{Pop-Location}
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $packet ($Phase+'-timing.json'))
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Maintained capacitor lid phase failed '+$Phase+' exit='+$code)}
Write-Output ('PASS maintained final capacitor lid phase '+$Phase)
