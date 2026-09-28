[CmdletBinding()]
param([Parameter(Mandatory)][string]$Scratch,
      [Parameter(Mandatory)][string]$Recovery,
      [Parameter(Mandatory)][string]$Packet)
$ErrorActionPreference = 'Stop'
$scratchPath = [IO.Path]::GetFullPath($Scratch).TrimEnd('\')
$recoveryPath = [IO.Path]::GetFullPath($Recovery).TrimEnd('\')
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$localParent = [IO.Path]::GetFullPath($env:LOCALAPPDATA).TrimEnd('\')
if ((Split-Path -Leaf $scratchPath) -cne 'TroubleshootJS-q30-tracking-retry-59699beb6d5448e9b42995c511adcee5' -or
    (Split-Path -Parent $scratchPath) -ine $tempParent) { throw 'Scratch ownership or containment mismatch.' }
if ((Split-Path -Leaf $recoveryPath) -cne 'TroubleshootJS-q30-profile-recovery-20260928' -or
    (Split-Path -Parent $recoveryPath) -ine $localParent) { throw 'Recovery ownership or containment mismatch.' }
foreach ($sentinel in @((Join-Path $scratchPath 'baseline-source-audit.json'),
                        (Join-Path $recoveryPath 'recovery-worktree-state.json'),
                        (Join-Path $Packet 'parity.json'), (Join-Path $Packet 'inventory.json'))) {
    if (-not (Test-Path -LiteralPath $sentinel -PathType Leaf)) { throw 'Task ownership/evidence sentinel missing.' }
}
$receipt = [ordered]@{status='STARTED'; targets=@(); liveTaskProcesses=@();
    scope='Only the two exact task-owned directories; no other Temp or repository cleanup';
    initialDeletedTemp='Removed externally by user-confirmed cleanup task, not this script'}
try {
    $verified = @()
    foreach ($target in @($scratchPath,$recoveryPath)) {
        $item = Get-Item -LiteralPath $target -Force
        if ($item.FullName.TrimEnd('\') -ine $target -or
            ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Root resolution/reparse mismatch.' }
        $entries = @(Get-ChildItem -LiteralPath $target -Recurse -Force)
        if (@($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
            throw 'Task directory contains reparse points; cleanup refused.'
        }
        $files = @($entries | Where-Object { -not $_.PSIsContainer })
        $verified += [pscustomobject]@{path=$target;fileCount=$files.Count;bytes=($files | Measure-Object Length -Sum).Sum}
    }
    $live = @(Get-CimInstance Win32_Process | Where-Object {
        $_.ProcessId -ne $PID -and $_.CommandLine -and
        ($_.CommandLine.IndexOf($scratchPath,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or
         $_.CommandLine.IndexOf($recoveryPath,[StringComparison]::OrdinalIgnoreCase) -ge 0)
    } | Select-Object ProcessId,CreationDate,ExecutablePath)
    if ($live.Count) { $receipt.liveTaskProcesses=$live; throw 'A process still references a task directory; no termination attempted.' }
    foreach ($entry in $verified) {
        $watch = [Diagnostics.Stopwatch]::StartNew()
        Remove-Item -LiteralPath $entry.path -Recurse -Force
        $watch.Stop()
        if (Test-Path -LiteralPath $entry.path) { throw 'Task directory still exists after removal.' }
        $receipt.targets += [ordered]@{name=(Split-Path -Leaf $entry.path);verifiedFileCount=$entry.fileCount;
            verifiedBytes=$entry.bytes;reparsePoints=0;removed=$true;cleanupSeconds=$watch.Elapsed.TotalSeconds}
    }
    $receipt.status='PASS'
} catch {
    $receipt.status='FAIL'
    $receipt.error=$_.Exception.Message.Replace($scratchPath,'<TASK_TEMP>').Replace($recoveryPath,'<RECOVERY>')
    throw
} finally {
    $receipt.finishedUtc=[DateTime]::UtcNow.ToString('o')
    $receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $Packet 'resource-cleanup.json') -Encoding utf8
}
$receipt | ConvertTo-Json -Depth 6
