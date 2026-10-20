[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('unit','routes')][string]$Phase)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$watch=[Diagnostics.Stopwatch]::StartNew()
$launch=Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-process-launch.json')) -Raw | ConvertFrom-Json
$rows=@($launch.drivers | ForEach-Object {
    $current=Get-Process -Id $_.pid -ErrorAction SilentlyContinue
    $observed=if($null -eq $current){'ABSENT'}else{'PRESENT'}
    $birthRevalidated=$false;$executableRevalidated=$false;$probeError=$null
    if($null -ne $current){
        try {
            $birthRevalidated=([string]$current.StartTime.ToUniversalTime().Ticks -ceq [string]$_.startTicks)
            $executableRevalidated=([string]$current.ProcessName -ceq [string]$_.executable)
        } catch { $probeError=$_.Exception.Message }
    }
    $same=$observed -eq 'PRESENT' -and $null -eq $probeError -and $birthRevalidated -and $executableRevalidated
    $outcome=if($observed -eq 'ABSENT'){'ABSENT'}elseif($null -ne $probeError){'UNKNOWN'}elseif($same){'STILL_RUNNING'}else{'DIFFERENT_PROCESS'}
    [ordered]@{pid=$_.pid;startTicks=$_.startTicks;executable=$_.executable;observed=$observed;
        birthRevalidated=$birthRevalidated;executableRevalidated=$executableRevalidated;
        sameCreationStillRunning=$same;outcome=$outcome;probeError=$probeError}
})
$watch.Stop()
$complete=@($rows | Where-Object {$_.outcome -in @('STILL_RUNNING','UNKNOWN')}).Count -eq 0
[ordered]@{status=$(if($complete){'PASS'}else{'FAIL'});phase=$Phase;drivers=$rows;
    verificationElapsedSeconds=$watch.Elapsed.TotalSeconds;terminationAttempted=$false;
    scope='Recorded r2 driver births only; absent or different births are reported and no uncertain process is terminated'} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-process-cleanup.json'))
if(-not $complete){throw 'Native driver creation remains running or could not be birth-revalidated'}
Write-Output ('PASS recorded normal-packing-r2 '+$Phase+' driver births exited; absence/different births reported; no termination attempted')
