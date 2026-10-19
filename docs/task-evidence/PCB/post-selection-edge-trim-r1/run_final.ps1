[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe,
      [ValidateSet('native','build')][string]$Phase='native')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$rows=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $proc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$proc.Id;startTicks=[string]$proc.StartTime.ToUniversalTime().Ticks;
        executable=$proc.ProcessName;script='docs/task-evidence/PCB/post-selection-edge-trim-r1/run_final.ps1';phase=$Phase}
})
[ordered]@{drivers=$rows;ownership='Captured by actual phase driver before work'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Phase+'-process-launch.json'))
$watch=[Diagnostics.Stopwatch]::StartNew()
$result='FAIL';$code=1
Push-Location $repo
try {
    if($Phase -eq 'native') {
        $plan=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'final-suite-plan.json') -Raw | ConvertFrom-Json
        & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $plan.names -ReceiptOutputPath (Join-Path $PSScriptRoot 'final-native-receipt.json') *> (Join-Path $PSScriptRoot 'final-native.txt')
        if(-not $?){throw 'Native verifier failed'}
    } else {
        & ./scripts/build.ps1 -JavaHome $JavaHome *> (Join-Path $PSScriptRoot 'final-build.txt')
        if(-not $?){throw 'Production build failed'}
    }
    $result='PASS';$code=0
} finally {
    $watch.Stop();Pop-Location
    [ordered]@{status=$result;exitCode=$code;elapsedSeconds=$watch.Elapsed.TotalSeconds;
        phase=$Phase;terminalCleanupIncluded=$true;cleanupDuration='NOT_SEPARATELY_MEASURED'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ('final-'+$Phase+'-timing.json'))
}
exit $code
