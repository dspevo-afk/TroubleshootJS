param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $SourceRoot
try {
  & ./scripts/verify-current-contracts.ps1 -JavaHome '<USER_HOME>/Desktop/TroubleshootJS/.tools/jdk8-download/jdk8u502-b07' -Suite 'LuAcceptedZeroSignWitnessContractTest' -ReceiptOutputPath (Join-Path $PSScriptRoot ($Label+'-receipt.txt')) *> (Join-Path $PSScriptRoot ($Label+'.log'))
  $q30Exit=$LASTEXITCODE
} finally { Pop-Location }
$q30Watch.Stop()
[ordered]@{status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'});exitCode=$q30Exit;elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;phase='unchanged accepted source signed-zero witness; native compile/test/owned cleanup'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.log')) -Tail 9
exit $q30Exit
