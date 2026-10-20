[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('baseline','candidate')][string]$Phase,
      [Parameter(Mandatory=$true)][int]$ActualMaintainedDriverExitCode)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$launchPath=Join-Path $PSScriptRoot ($Phase+'-process-launch.json')
$out=Join-Path $PSScriptRoot ($Phase+'-process-cleanup.json')
if(-not(Test-Path -LiteralPath $launchPath) -or (Test-Path -LiteralPath $out)) { throw 'Missing launch receipt or existing cleanup; preserve prior phase.' }
$launch=Get-Content -LiteralPath $launchPath -Raw | ConvertFrom-Json
if($launch.phase -ne $Phase -or $launch.drivers.Count -ne 3) { throw 'Unexpected process launch framing.' }
$watch=[Diagnostics.Stopwatch]::StartNew()
$checks=@(foreach($birth in $launch.drivers) {
    $current=Get-Process -Id ([int]$birth.pid) -ErrorAction SilentlyContinue
    $same=$null -ne $current -and [string]$current.StartTime.ToUniversalTime().Ticks -eq [string]$birth.startTicks
    if($same) { throw ('Recorded process birth remains alive: '+$birth.pid+' role='+$birth.role) }
    @{pid=[int]$birth.pid;startTicks=[string]$birth.startTicks;executable=[string]$birth.executable;
      role=[string]$birth.role;recordedBirthExited=$true}
})
$watch.Stop()
@{phase=$Phase;status='PASS_RECORDED_BIRTHS_EXITED';actualMaintainedDriverExitCode=$ActualMaintainedDriverExitCode;
  launchSha256=(Get-FileHash -LiteralPath $launchPath -Algorithm SHA256).Hash.ToLowerInvariant();
  checks=$checks;elapsedSeconds=$watch.Elapsed.TotalSeconds;
  scope='Separate post-exec birth check; no termination attempted'} |
    ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $out -Encoding utf8
Write-Output ('PASS recorded '+$Phase+' driver births exited; maintained driver exit='+$ActualMaintainedDriverExitCode)
