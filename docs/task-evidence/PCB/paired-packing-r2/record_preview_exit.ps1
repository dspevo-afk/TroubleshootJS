[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('preview','preview-stop')][string]$Prefix,
      [Parameter(Mandatory=$true)][int]$ActualExecExitCode)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$launchPath=Join-Path $PSScriptRoot ($Prefix+'-process-launch.json')
$target=Join-Path $PSScriptRoot ($Prefix+'-process-cleanup.json')
if(Test-Path -LiteralPath $target){throw 'Preserve previous root driver observation'}
$launch=Get-Content -LiteralPath $launchPath -Raw|ConvertFrom-Json
$expected=if($Prefix -eq 'preview'){'preview-start'}else{'preview-stop'}
if($launch.phase -cne $expected){throw 'Wrong launch phase/prefix'}
$watch=[Diagnostics.Stopwatch]::StartNew()
$checks=@(foreach($birth in $launch.drivers){
    $live=Get-Process -Id $birth.pid -ErrorAction SilentlyContinue
    $exited=$null -eq $live -or [string]$live.StartTime.ToUniversalTime().Ticks -cne [string]$birth.startTicks
    if(-not $exited){throw 'Recorded preview driver still alive; preserve and investigate'}
    @{pid=$birth.pid;startTicks=$birth.startTicks;executable=$birth.executable;recordedBirthExited=$true}
})
$watch.Stop()
@{phase=$expected;status=$(if($ActualExecExitCode -eq 0){'PASS'}else{'PASS_CLEANUP_OF_FAILED_PHASE'});actualExecExitCode=$ActualExecExitCode;launchSha256=(Get-FileHash -LiteralPath $launchPath -Algorithm SHA256).Hash.ToLowerInvariant();checks=$checks;elapsedSeconds=$watch.Elapsed.TotalSeconds;scope='Root driver births after actual tool exec completion; managed preview worker proof separate'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $target
Write-Output ('PASS preview driver observation '+$Prefix+' actualExit='+$ActualExecExitCode)
