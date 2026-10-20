[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
if(Test-Path -LiteralPath (Join-Path $repoRoot '.tools/preview/state.json')) {
    throw 'Managed preview state already exists; preserve its current owner'
}
if(@(Get-NetTCPConnection -State Listen -LocalPort 8909 -ErrorAction SilentlyContinue).Count -ne 0) {
    throw 'Preview port8909 already has a listener; preserve its current owner'
}
foreach($name in @('preview-process-launch.json','preview-start-timing.json','preview-start.txt')) {
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot $name)){throw 'Preserve prior preview launch'}
}
$mine=Get-CimInstance Win32_Process -Filter ('ProcessId='+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID)|Select-Object -Unique|ForEach-Object {
    $p=Get-Process -Id $_ -ErrorAction Stop
    @{pid=$p.Id;startTicks=[string]$p.StartTime.ToUniversalTime().Ticks;executable=$p.ProcessName}
})
@{phase='preview-start';port=8909;drivers=$drivers}|ConvertTo-Json -Depth 6|
    Set-Content -LiteralPath (Join-Path $PSScriptRoot 'preview-process-launch.json')
$watch=[Diagnostics.Stopwatch]::StartNew();$failure=$null;$ok=$false
try {
    Push-Location $repoRoot
    try {
        & ./scripts/start-preview.ps1 -QuickPlay -Port 8909 *> (Join-Path $PSScriptRoot 'preview-start.txt')
        if(-not $?){throw 'Maintained preview launcher failed'}
        $state=Get-Content -LiteralPath (Join-Path $repoRoot '.tools/preview/state.json') -Raw|ConvertFrom-Json
        if([int]$state.Port -ne 8909){throw 'Unexpected managed preview port'}
        $ok=$true
    } finally {Pop-Location}
} catch {$failure=$_.Exception.Message} finally {
    $watch.Stop()
    @{phase='preview-start';status=$(if($ok){'PASS'}else{'FAIL'});elapsedSeconds=$watch.Elapsed.TotalSeconds;failure=$failure;limits='Startup only; actual served identity, player and worker cleanup require separate proof'}|
        ConvertTo-Json|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'preview-start-timing.json')
}
if(-not $ok){throw ('Preview startup failed: '+$failure)}
Write-Output 'PASS maintained preview startup; worker remains owned and running on8909'
