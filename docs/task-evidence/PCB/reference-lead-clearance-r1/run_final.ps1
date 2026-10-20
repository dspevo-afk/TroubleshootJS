[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('fixtures','contracts','census','matrix','build')][string]$Phase,
      [Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$packetRoot=$PSScriptRoot
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $packetRoot '../../../..')).Path
foreach($suffix in @('-input-audit-before.txt','-input-audit-after.txt','-process-launch.json','-process-cleanup.json','-timing.json','.txt','-receipt.txt')){
    if(Test-Path -LiteralPath (Join-Path $packetRoot ($Phase+$suffix))){throw 'Preserve prior final-phase attempt'}
}
function Audit-Inputs([string]$Side){
    & $PythonExe -X utf8 -B (Join-Path $packetRoot 'final_inputs.py') audit *> (Join-Path $packetRoot ($Phase+'-input-audit-'+$Side+'.txt'))
    if($LASTEXITCODE -ne 0){throw ('Final input audit failed: '+$Side)}
}
Audit-Inputs 'before'
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object {
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase=$Phase;drivers=$drivers;scope='Actual parent/current births before maintained final clearance phase'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $packetRoot ($Phase+'-process-launch.json'))
$watch=[Diagnostics.Stopwatch]::StartNew();$code=2;$failure=$null
try {
    Push-Location $repoRoot
    try {
        if($Phase -eq 'build'){
            & ./scripts/build.ps1 -JavaHome $JavaHome -Style OBF -Target Compile -ProcessTimeoutSeconds 900 *> (Join-Path $packetRoot ($Phase+'.txt'))
        } else {
            $plan=Get-Content -LiteralPath (Join-Path $packetRoot 'final-suites.json') -Raw|ConvertFrom-Json
            $suites=@($plan.$Phase)
            & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $suites -ReceiptOutputPath (Join-Path $packetRoot ($Phase+'-receipt.txt')) *> (Join-Path $packetRoot ($Phase+'.txt'))
        }
        $code=[int]$LASTEXITCODE
    } finally {Pop-Location}
} catch {$failure=$_.Exception.Message;$code=2} finally {
    $watch.Stop()
    @{phase=$Phase;status=$(if($code -eq 0){'PASS'}else{'FAIL'});exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED';failure=$failure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $packetRoot ($Phase+'-timing.json'))
}
Audit-Inputs 'after'
if($code -ne 0){throw ('Maintained final clearance phase failed: '+$Phase+' exit='+$code)}
Write-Output ('PASS maintained final clearance phase '+$Phase)
