[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$JavaHome,
      [Parameter(Mandatory=$true)][string]$PythonExe)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$mine=Get-CimInstance Win32_Process -Filter ("ProcessId="+$PID)
$rows=@(@([int]$mine.ParentProcessId,[int]$PID) | ForEach-Object {
    $proc=Get-Process -Id $_ -ErrorAction Stop
    [ordered]@{pid=$proc.Id;startTicks=[string]$proc.StartTime.ToUniversalTime().Ticks;
        executable=$proc.ProcessName;script='docs/task-evidence/PCB/post-selection-edge-trim-r1/run_focused.ps1'}
})
[ordered]@{drivers=$rows;ownership='Captured by actual native driver before compilation'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'process-launch.json')
$plan=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'focused-plan.json') -Raw | ConvertFrom-Json
Push-Location $repo
try {
    & ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -Suite $plan.names -ReceiptOutputPath (Join-Path $PSScriptRoot 'focused-receipt.json')
    if(-not $?){exit 1}
} finally {Pop-Location}
