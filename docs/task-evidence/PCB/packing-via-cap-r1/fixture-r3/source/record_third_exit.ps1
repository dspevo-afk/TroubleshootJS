[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('fixture-r3','target-r3')][string]$Attempt,
      [Parameter(Mandatory=$true)][int]$ActualExecExitCode)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$attemptPath=Join-Path $PSScriptRoot $Attempt
$prefix=if($Attempt -eq 'fixture-r3'){'fixture-'}else{''}
$launchPath=Join-Path $attemptPath ($prefix+'process-launch.json')
$target=Join-Path $attemptPath ($prefix+'process-cleanup.json')
if(Test-Path -LiteralPath $target){throw 'Preserve prior cleanup observation'}
$launch=Get-Content -LiteralPath $launchPath -Raw|ConvertFrom-Json
$watch=[Diagnostics.Stopwatch]::StartNew()
$checks=@(foreach($birth in $launch.drivers){
    $live=Get-Process -Id $birth.pid -ErrorAction SilentlyContinue
    $exited=$null -eq $live -or [string]$live.StartTime.ToUniversalTime().Ticks -cne [string]$birth.startTicks
    if(-not $exited){throw 'Recorded driver still alive; preserve and investigate'}
    @{pid=$birth.pid;startTicks=$birth.startTicks;executable=$birth.executable;recordedBirthExited=$true}
})
$watch.Stop()
@{status=$(if($ActualExecExitCode -eq 0){'PASS'}else{'PASS_CLEANUP_OF_FAILED_PHASE'});actualExecExitCode=$ActualExecExitCode;launchSha256=(Get-FileHash -LiteralPath $launchPath -Algorithm SHA256).Hash.ToLowerInvariant();checks=$checks;elapsedSeconds=$watch.Elapsed.TotalSeconds;scope='Separate root observation after actual tool completion; no termination'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $target
Write-Output ('PASS recorded driver births exited '+$Attempt+' actualExit='+$ActualExecExitCode)
