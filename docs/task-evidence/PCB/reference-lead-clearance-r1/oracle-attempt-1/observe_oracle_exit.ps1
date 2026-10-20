[CmdletBinding()]
param([Parameter(Mandatory=$true)][int]$ActualExecExitCode)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($ActualExecExitCode -ne 0){throw 'Caller must first receive the actual driver exit0'}
$out=Join-Path $PSScriptRoot 'baseline-process-cleanup.json'
if(Test-Path -LiteralPath $out){throw 'Preserve prior cleanup observation'}
$launchPath=Join-Path $PSScriptRoot 'baseline-process-launch.json'
$launch=Get-Content -LiteralPath $launchPath -Raw|ConvertFrom-Json
if($launch.phase -ne 'baseline' -or $launch.drivers.Count -ne 2){throw 'Unexpected launch framing'}
$watch=[Diagnostics.Stopwatch]::StartNew()
$checks=@(foreach($birth in $launch.drivers){
    $current=Get-Process -Id ([int]$birth.pid) -ErrorAction SilentlyContinue
    $same=$null -ne $current -and [string]$current.StartTime.ToUniversalTime().Ticks -eq [string]$birth.startTicks
    if($same){throw ('Recorded driver still alive: '+$birth.pid)}
    @{pid=[int]$birth.pid;startTicks=[string]$birth.startTicks;executable=[string]$birth.executable;recordedBirthExited=$true}
})
$watch.Stop()
@{phase='baseline';status='PASS';actualExecExitCode=$ActualExecExitCode;
  launchSha256=(Get-FileHash -LiteralPath $launchPath -Algorithm SHA256).Hash.ToLowerInvariant();
  checks=$checks;elapsedSeconds=$watch.Elapsed.TotalSeconds;
  scope='Separate root observation after actual exec exit0; no termination attempted'}|
    ConvertTo-Json -Depth 7|Set-Content -LiteralPath $out
Write-Output 'PASS both recorded oracle driver births exited'
