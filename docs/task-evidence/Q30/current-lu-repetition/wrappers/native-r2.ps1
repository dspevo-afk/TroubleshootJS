$ErrorActionPreference='Stop'
$q30Suites=@('A07ExecutionContractTest','Q30LuRepetitionProfileContractTest','Q30TemporalWorkContractTest','Q30GenerationMeasurementBudgetContractTest','Q30NormalExecutionPolicyContractTest')
$q30Watch=[Diagnostics.Stopwatch]::StartNew()
& ./scripts/verify-current-contracts.ps1 -JavaHome '<USER_HOME>/Desktop/TroubleshootJS/.tools/jdk8-download/jdk8u502-b07' -Suite $q30Suites -ReceiptOutputPath (Join-Path $PSScriptRoot 'native-lu-repetition-r2-receipt.txt') *> (Join-Path $PSScriptRoot 'native-lu-repetition-r2.log')
$q30Exit=$LASTEXITCODE
$q30Watch.Stop()
[ordered]@{status=$(if($q30Exit -eq 0){'PASS'}else{'FAIL'});exitCode=$q30Exit;elapsedSeconds=$q30Watch.Elapsed.TotalSeconds;phase='focused native solver/sampler cadence and boundaries/temporal/budget regression including maintained cleanup';suites=$q30Suites} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'native-lu-repetition-r2.json')
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'native-lu-repetition-r2.json')
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'native-lu-repetition-r2.log') -Tail 14
exit $q30Exit
