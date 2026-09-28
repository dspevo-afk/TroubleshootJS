param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
$q30Suites=@('D01DiagnosticContractTest','Q30PlanContractTest','Q30TemporalWorkContractTest','Q30GenerationMeasurementBudgetContractTest','Q30NormalExecutionPolicyContractTest')
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $SourceRoot
try {
  & ./scripts/verify-current-contracts.ps1 -JavaHome '$DESKTOPROOT/.tools/jdk8-download/jdk8u502-b07' -Suite $q30Suites -ReceiptOutputPath (Join-Path $PSScriptRoot ($Label+'-receipt.txt')) *> (Join-Path $PSScriptRoot ($Label+'.log'))
  $q30Exit=$LASTEXITCODE
} finally { Pop-Location }
$q30Watch.Stop()
[ordered]@{status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'});exitCode=$q30Exit;elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;phase='focused private D01 source compile/diagnostic/plan/temporal/budget/normal-policy contracts including maintained cleanup; browser lifecycle not established';suites=$q30Suites} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.log')) -Tail 14
exit $q30Exit
