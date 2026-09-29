[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$TaskRoot,
    [Parameter(Mandatory=$true)][string]$RecoveryRoot,
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$specs = @(
    @{ Path=$TaskRoot; Name='TroubleshootJS-q30-support-cost-e045149241a0415dbbc12f8b4333e90c'; Region='OS_TEMP'; Parents=@([IO.Path]::GetTempPath(), (Join-Path $env:USERPROFILE 'AppData\Local\Temp')) },
    @{ Path=$RecoveryRoot; Name='TroubleshootJS-q30-cost-recovery-e045149241a0415dbbc12f8b4333e90c'; Region='LOCAL_APP_DATA'; Parents=@($env:LOCALAPPDATA, (Join-Path $env:USERPROFILE 'AppData\Local')) }
)
$preflightClock = [Diagnostics.Stopwatch]::StartNew()
$verified = @()
foreach ($spec in $specs) {
    $requested = [IO.Path]::GetFullPath($spec.Path).TrimEnd('\')
    $resolved = (Resolve-Path -LiteralPath $requested).ProviderPath.TrimEnd('\')
    if (-not [StringComparer]::OrdinalIgnoreCase.Equals($requested, $resolved)) { throw 'Resolved target differs from explicit task directory' }
    if ([IO.Path]::GetFileName($resolved) -cne $spec.Name) { throw 'Task ownership basename mismatch' }
    $resolvedParent = [IO.Path]::GetDirectoryName($resolved)
    $allowedParents = @($spec.Parents | ForEach-Object {
        if (Test-Path -LiteralPath $_) { (Resolve-Path -LiteralPath $_).ProviderPath.TrimEnd('\') }
    })
    if (-not ($allowedParents | Where-Object { [StringComparer]::OrdinalIgnoreCase.Equals($_,$resolvedParent) })) { throw 'Task directory is outside its intended parent' }
    $rootItem = Get-Item -LiteralPath $resolved -Force
    if (-not $rootItem.PSIsContainer -or ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Task root is not an ordinary directory' }
    $entries = @(Get-ChildItem -LiteralPath $resolved -Force -Recurse)
    if (@($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Task directory contains a reparse boundary' }
    foreach ($entry in $entries) {
        if (-not $entry.FullName.StartsWith($resolved+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Enumerated child escaped task root' }
    }
    $verified += [pscustomobject]@{ Path=$resolved; Name=$spec.Name; Region=$spec.Region; Files=@($entries | Where-Object { -not $_.PSIsContainer }).Count }
}
$references = @(Get-CimInstance Win32_Process | Where-Object {
    $processRow = $_
    if ($processRow.ProcessId -eq $PID -or -not $processRow.CommandLine) { return $false }
    foreach ($targetRow in $verified) {
        if ($processRow.CommandLine.IndexOf($targetRow.Path,[StringComparison]::OrdinalIgnoreCase) -ge 0) { return $true }
    }
    return $false
})
if ($references.Count) { throw ('Live process still references a task resource: '+(($references | ForEach-Object { $_.ProcessId }) -join ',')) }
$preflightClock.Stop()
$results = @()
foreach ($targetRow in $verified) {
    # Revalidate the exact resolved directory immediately before recursive deletion.
    if ((Resolve-Path -LiteralPath $targetRow.Path).ProviderPath.TrimEnd('\') -cne $targetRow.Path) { throw 'Task root changed after preflight' }
    if ((Get-Item -LiteralPath $targetRow.Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Task root became a reparse point' }
    $deleteClock = [Diagnostics.Stopwatch]::StartNew()
    Remove-Item -LiteralPath $targetRow.Path -Recurse -Force
    $deleteClock.Stop()
    if (Test-Path -LiteralPath $targetRow.Path) { throw 'Task directory remained after cleanup' }
    $results += [ordered]@{ name=$targetRow.Name; region=$targetRow.Region; files=$targetRow.Files; status='PASS'; deleteSeconds=$deleteClock.Elapsed.TotalSeconds }
}
$receipt = [ordered]@{ status='PASS'; scope='Only the two exact task-owned directories; no process termination or other cleanup'; preflightSeconds=$preflightClock.Elapsed.TotalSeconds; ownershipContainmentReparseChecks='PASS'; liveProcessReferences=0; resources=$results }
$receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputFile -Encoding UTF8
$receipt | ConvertTo-Json -Depth 6
