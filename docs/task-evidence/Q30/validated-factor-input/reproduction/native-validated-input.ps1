param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
$q30Suites=@('A07ExecutionContractTest','Q30TemporalWorkContractTest','Q30GenerationMeasurementBudgetContractTest','Q30NormalExecutionPolicyContractTest')
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $SourceRoot
try {
  & ./scripts/verify-current-contracts.ps1 -JavaHome '<PERSONAL_PATH> -Suite $q30Suites -ReceiptOutputPath (Join-Path $PSScriptRoot ($Label+'-receipt.txt')) *> (Join-Path $PSScriptRoot ($Label+'.log'))
  $q30Exit=$LASTEXITCODE
} finally { Pop-Location }
$q30Watch.Stop()
[ordered]@{status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'});exitCode=$q30Exit;elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;phase='focused native general solver/temporal/measurement budget/normal policy regression including maintained cleanup; owned nonlinear producer canaries are compiled-browser gates';suites=$q30Suites} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.log')) -Tail 14
exit $q30Exit
