[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$watch=[Diagnostics.Stopwatch]::StartNew()
$launch=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-process-launch.json') -Raw | ConvertFrom-Json
$rows=@($launch.drivers | ForEach-Object {
    $current=Get-Process -Id $_.pid -ErrorAction SilentlyContinue
    $same=$null -ne $current -and [string]$current.StartTime.ToUniversalTime().Ticks -eq [string]$_.startTicks
    [ordered]@{pid=$_.pid;startTicks=$_.startTicks;executable=$_.executable;
        sameCreationStillRunning=$same;outcome=$(if($same){'STILL_RUNNING'}else{'EXITED'})}
})
$watch.Stop()
$complete=@($rows | Where-Object {$_.sameCreationStillRunning}).Count -eq 0
[ordered]@{status=$(if($complete){'PASS'}else{'FAIL'});drivers=$rows;
    verificationElapsedSeconds=$watch.Elapsed.TotalSeconds;terminationAttempted=$false;
    scope='Recorded driver birth identities only; maintained native terminal receipt owns its bounded child cleanup'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'unit-process-cleanup.json')
if(-not $complete){throw 'Native driver birth still running; wait for actual terminal completion'}
Write-Output 'PASS recorded unit driver creations exited; no termination attempted'
