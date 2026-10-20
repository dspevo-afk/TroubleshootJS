[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object {
 $p=Get-Process -Id $_ -ErrorAction Stop
 @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase='preview-stop';drivers=$drivers}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'preview-stop-process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew()
$ok=$false;$failure=$null
try {
 Push-Location $root
 try {& ./scripts/stop-preview.ps1 *> (Join-Path $PSScriptRoot 'preview-stop.txt');if(-not $?){throw 'Maintained stop failed'};$ok=$true} finally {Pop-Location}
} catch {$failure=$_.Exception.Message} finally {
 $watch.Stop()
 @{status=$(if($ok){'PASS'}else{'FAIL'});phase='preview-stop';elapsedSeconds=$watch.Elapsed.TotalSeconds;failure=$failure;limits='Maintained stop only; separate root recorded-birth/state/port absence still required'}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'preview-stop-timing.json')
}
if(-not $ok){throw $failure}
Write-Output 'PASS maintained preview stop'
