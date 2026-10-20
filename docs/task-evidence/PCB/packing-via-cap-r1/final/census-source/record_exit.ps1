[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Phase,
      [Parameter(Mandatory=$true)][int]$ActualExecExitCode)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$launchPath=Join-Path $PSScriptRoot ($Phase+'-process-launch.json')
$target=Join-Path $PSScriptRoot ($Phase+'-process-cleanup.json')
if(Test-Path -LiteralPath $target){throw 'Preserve existing cleanup receipt'}
$launch=Get-Content -LiteralPath $launchPath -Raw|ConvertFrom-Json
$watch=[Diagnostics.Stopwatch]::StartNew()
$checks=@(foreach($birth in $launch.drivers){
    $live=Get-Process -Id $birth.pid -ErrorAction SilentlyContinue
    $exited=($null -eq $live) -or ([string]$live.StartTime.ToUniversalTime().Ticks -ne [string]$birth.startTicks)
    if(-not $exited){throw 'Recorded driver remains alive; preserve and investigate'}
    @{pid=$birth.pid;startTicks=$birth.startTicks;executable=$birth.executable;recordedBirthExited=$exited}
})
$watch.Stop()
@{phase=$Phase;status=$(if($ActualExecExitCode -eq 0){'PASS'}else{'PASS_CLEANUP_OF_FAILED_PHASE'});
  actualExecExitCode=$ActualExecExitCode;
  launchSha256=(Get-FileHash -LiteralPath $launchPath -Algorithm SHA256).Hash.ToLowerInvariant();
  checks=$checks;elapsedSeconds=$watch.Elapsed.TotalSeconds;
  scope='Separate root observation after actual tool exec exit; no termination attempted'}|
    ConvertTo-Json -Depth 7|Set-Content -LiteralPath $target
Write-Output ('PASS root recorded driver births exited phase='+$Phase+' actualExit='+$ActualExecExitCode)
