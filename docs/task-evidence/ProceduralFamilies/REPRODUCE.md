# Reproduce the procedural-family and resistor qualification

Run from a clean published checkout with JDK8/GWT dependencies, Python/Playwright and Edge available. Use new external evidence directories; do not edit/build this checkout concurrently.

~~~powershell
$Root = (Get-Location).Path
$Java = Join-Path $Root '.tools/jdk8-download/jdk8u502-b07'
$Out = Join-Path $env:TEMP ('TSJ-procedural-recheck-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $Out | Out-Null
$Suites = @('RouterQueueContractTest','ProceduralFamilyContractTest','QuickPlayContractTest','PcbCompactionContractTest','DensePcbPackingContractTest','PhysicalServiceabilityContractTest','U04SessionContractTest','U05DifficultyContractTest','E03RelayContractTest','Q15ControlBoardContractTest','ControlledIndicatorAssemblyContractTest','A09DiagnosticContractTest','A10GenerationContractTest','A10RoutingContractTest','A10DependencyContractTest','P03PlacementContractTest','P04RoutingContractTest','QuickPlayGateContractTest','P09EnvelopeContractTest')
& .\scripts\verify-current-contracts.ps1 -JavaHome $Java -Suite $Suites
if ($LASTEXITCODE -ne 0) { throw 'Affected native matrix failed' }
& .\scripts\build.ps1 -JavaHome $Java -Style OBF -ProcessTimeoutSeconds 180
if ($LASTEXITCODE -ne 0) { throw 'GWT build failed' }
foreach ($Mode in @('a08','a08negative','e03','normal','a10')) {
    python -u .\docs\task-evidence\ProceduralFamilies\verify_all.py $Root (Join-Path $Out $Mode) $Mode
    if ($LASTEXITCODE -ne 0) { throw ('Compiled qualification failed: ' + $Mode) }
}
node .\tests\contracts\quickplay_entropy_contract.mjs
if ($LASTEXITCODE -ne 0) { throw 'Signed seed transport failed' }
~~~

The normal driver controls only the eight entropy bytes, then clicks real menu/ticket/face/cancel/replay/shop controls. Mutation/solver proof runs are explicitly developer-only. Browser failure screenshots and process-identity receipts remain in the external output directory. No difficulty band is unlocked and no rejected sample is silently treated as playable.
