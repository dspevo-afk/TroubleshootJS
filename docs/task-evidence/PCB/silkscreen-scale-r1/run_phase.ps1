[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [ValidateSet('native','build')][string]$Phase='native')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$drivers=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $ownedProc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$ownedProc.Id;startTicks=[string]$ownedProc.StartTime.ToUniversalTime().Ticks;
        executable=$ownedProc.ProcessName;script='docs/task-evidence/PCB/silkscreen-scale-r1/run_phase.ps1';phase=$Phase}
})
[ordered]@{drivers=$drivers;ownership='Captured before phase work'} | ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-process-launch.json'))
$watch=[Diagnostics.Stopwatch]::StartNew();$status='FAIL';$code=1
Push-Location $repo
try {
    if($Phase -eq 'native') {
        $plan=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'suite-plan.json') -Raw | ConvertFrom-Json
        if($plan.names.Count -ne $plan.count -or $plan.count -ne 7){throw 'Unexpected suite plan'}
        & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $plan.names -ReceiptOutputPath (Join-Path $PSScriptRoot 'native-receipt.txt') *> (Join-Path $PSScriptRoot 'native.txt')
        if(-not $?){throw 'Native verifier failed'}
    } else {
        & ./scripts/build.ps1 -JavaHome $JavaHome -Target Compile *> (Join-Path $PSScriptRoot 'build.txt')
        if(-not $?){throw 'Production build failed'}
    }
    $status='PASS';$code=0
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$status;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;
        phase=$Phase;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-timing.json'))
}
exit $code
