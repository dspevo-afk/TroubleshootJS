Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$launch=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'process-launch.json') -Raw | ConvertFrom-Json
$rows=@();$ok=$true
foreach($driver in $launch.drivers) {
    $observed=Get-Process -Id $driver.pid -ErrorAction SilentlyContinue
    $sameCreation=$null -ne $observed -and [string]$observed.StartTime.ToUniversalTime().Ticks -eq [string]$driver.startTicks
    if($sameCreation){$ok=$false}
    $rows += [ordered]@{pid=$driver.pid;startTicks=[string]$driver.startTicks;executable=$driver.executable;
        sameCreationStillRunning=$sameCreation;outcome=$(if($sameCreation){'STILL_RUNNING'}else{'EXITED'})}
}
[ordered]@{status=$(if($ok){'PASS'}else{'FAIL'});drivers=$rows;terminationAttempted=$false;
    scope='Recorded driver birth identities only; maintained native terminal receipt owns its bounded child cleanup'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'process-cleanup.json')
if(-not $ok){throw 'A recorded driver creation is still running'}
Write-Output 'PASS recorded native driver creations exited'
