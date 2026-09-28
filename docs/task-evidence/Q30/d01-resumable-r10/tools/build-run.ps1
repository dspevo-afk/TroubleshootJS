param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
$q30Started=[DateTime]::UtcNow
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $SourceRoot
try {
  & (Get-Command pwsh).Source -NoProfile -File scripts/build.ps1 -JavaHome '$DESKTOPROOT/.tools/jdk8-download/jdk8u502-b07' -Target Compile -Style OBF *> (Join-Path $PSScriptRoot ($Label+'.log'))
  $q30Exit=$LASTEXITCODE
} finally { Pop-Location }
$q30Watch.Stop()
[ordered]@{gate='maintained JDK8/GWT five-permutation build';label=$Label;exitCode=$q30Exit;startedUtc=$q30Started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'})} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.log')) -Tail 6
exit $q30Exit
