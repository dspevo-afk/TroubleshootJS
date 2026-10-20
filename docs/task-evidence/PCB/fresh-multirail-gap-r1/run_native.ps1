[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
foreach($suffix in @('-input-audit-before.txt','-input-audit-after.txt','-process-launch.json','-process-cleanup.json','-timing.json','.txt','-receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot ('native-route'+$suffix))){throw 'Preserve previous native diagnostic attempt'}
}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $PSScriptRoot 'inputs.py') audit *> (Join-Path $PSScriptRoot ('native-route-input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Exact native diagnostic input audit failed '+$Side)}
}
$request=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'request.json') -Raw|ConvertFrom-Json
$seed=[long]::Parse($request.seed,[Globalization.CultureInfo]::InvariantCulture)
if($seed.ToString([Globalization.CultureInfo]::InvariantCulture) -cne $request.seed){throw 'Noncanonical signed-long seed'}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object{
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase='native-route';drivers=$drivers;seed=$request.seed;scope='Recorded driver births before maintained exact-seed native probe'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'native-route-process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try{
    Push-Location $repoRoot
    try{
        & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite @($request.suite) -Q30CorpusSeeds @($seed) -ReceiptOutputPath (Join-Path $PSScriptRoot 'native-route-receipt.txt') *> (Join-Path $PSScriptRoot 'native-route.txt')
        $code=[int]$LASTEXITCODE
    }finally{Pop-Location}
}catch{$failure=$_.Exception.Message;$code=2}finally{
    $watch.Stop()
    @{phase='native-route';status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'native-route-timing.json')
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Exact native diagnostic failed exit='+$code)}
Write-Output 'PASS maintained exact fresh multi-rail native route probe'
