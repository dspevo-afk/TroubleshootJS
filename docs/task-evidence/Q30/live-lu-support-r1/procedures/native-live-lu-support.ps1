param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$Label,[switch]$TrackerOnly)
$ErrorActionPreference='Stop'
$q30Suites=@('LuStructuralSupportContractTest','LuStructuralFactorizationContractTest','Q30OwnedLuZeroRowContractTest','Q30LuPivotCollectionContractTest','A07ExecutionContractTest','Q30TemporalWorkContractTest','Q30GenerationMeasurementBudgetContractTest','Q30NormalExecutionPolicyContractTest','A10GenerationContractTest')
if ($TrackerOnly) { $q30Suites=@('LuStructuralSupportContractTest') }
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
Push-Location -LiteralPath $SourceRoot
try {
  & ./scripts/verify-current-contracts.ps1 -JavaHome '<USER_HOME>/Desktop/TroubleshootJS/.tools/jdk8-download/jdk8u502-b07' -Suite $q30Suites -ReceiptOutputPath (Join-Path $PSScriptRoot ($Label+'-receipt.txt')) *> (Join-Path $PSScriptRoot ($Label+'.log'))
  $q30Exit=$LASTEXITCODE
} finally { Pop-Location }
$q30Watch.Stop()
[ordered]@{status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'});exitCode=$q30Exit;elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;phase='focused structural support and numeric parity plus unchanged independent LU, execution, temporal, budget, normal policy and generation contracts; maintained cleanup included';suites=$q30Suites} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.json'))
Get-Content -LiteralPath (Join-Path $PSScriptRoot ($Label+'.log')) -Tail 12
exit $q30Exit
