[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('canaries','npn')][string]$Phase,
      [Parameter(Mandatory=$true)][int]$ActualExecExitCode)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$attempt=Join-Path $PSScriptRoot $Phase
$target=Join-Path $attempt 'process-cleanup.json'
if(Test-Path -LiteralPath $target){throw 'Preserve previous cleanup receipt'}
$launchPath=Join-Path $attempt 'process-launch.json'
$launch=Get-Content -LiteralPath $launchPath -Raw|ConvertFrom-Json
if($launch.phase -cne $Phase){throw 'Wrong phase'}
$watch=[Diagnostics.Stopwatch]::StartNew()
$checks=@(foreach($birth in $launch.drivers){
    $live=Get-Process -Id $birth.pid -ErrorAction SilentlyContinue
    $exited=$null -eq $live -or [string]$live.StartTime.ToUniversalTime().Ticks -cne [string]$birth.startTicks
    if(-not $exited){throw 'Recorded driver still alive; preserve and investigate'}
    @{pid=$birth.pid;startTicks=$birth.startTicks;executable=$birth.executable;recordedBirthExited=$true}
})
$watch.Stop()
@{status=$(if($ActualExecExitCode -eq 0){'PASS'}else{'PASS_CLEANUP_OF_FAILED_PHASE'});actualExecExitCode=$ActualExecExitCode;launchSha256=(Get-FileHash -LiteralPath $launchPath -Algorithm SHA256).Hash.ToLowerInvariant();checks=$checks;elapsedSeconds=$watch.Elapsed.TotalSeconds;scope='Separate root birth checks after actual tool exec completion; no termination'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $target
Write-Output ('PASS sparse diagnostic driver cleanup '+$Phase+' actualExit='+$ActualExecExitCode)
