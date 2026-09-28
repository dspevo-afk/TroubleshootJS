param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$Label)
$ErrorActionPreference='Stop'
$q30Suites=@('A07ExecutionContractTest','Q30OwnedLuZeroRowContractTest','Q30LuPivotCollectionContractTest','Q30TemporalWorkContractTest','Q30GenerationMeasurementBudgetContractTest','Q30NormalExecutionPolicyContractTest')
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $SourceRoot
try {
  & ./scripts/verify-current-contracts.ps1 -JavaHome '<TASK_TEMP> -Suite $q30Suites -ReceiptOutputPath (Join-Path $PSScriptRoot ($Label+'-receipt.txt')) *> (Join-Path $PSScriptRoot ($Label+'.log'))
  $q30Exit=$LASTEXITCODE
} finally { Pop-Location }
$q30Watch.Stop()
[ordered]@{status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'});exitCode=$q30Exit;elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;phase='focused native owned-path oracle/general solver/temporal/measurement budget/normal policy including maintained cleanup';suites=$q30Suites} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.log')) -Tail 14
exit $q30Exit
