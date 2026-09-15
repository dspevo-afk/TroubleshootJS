# Reproduce P05 qualification

Use the candidate checkout matching `source-build-identity.json`, Windows,
JDK 8, the maintained GWT dependencies, Python with Playwright, and installed Edge.
Run one expensive gate at a time. Keep output outside the repository.

```powershell
$ErrorActionPreference = 'Stop'
$Root = (Get-Location).Path
[Environment]::CurrentDirectory = $Root
$JavaHome = (Resolve-Path '.tools/jdk8-download/jdk8u502-b07').Path
$Evidence = Join-Path $env:TEMP ('TroubleshootJS-P05-recheck-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Evidence | Out-Null
$Suites = @('PcbCompactionContractTest','Q15ControlBoardContractTest',
    'A10RoutingContractTest','P02ConductorContractTest','P03PlacementContractTest',
    'P04RoutingContractTest','P05RoutingContractTest','P05RoutingCorpus','P05GenerationCorpus')
& ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -Suite $Suites `
    -ReceiptOutputPath (Join-Path $Evidence 'native.txt')
if ($LASTEXITCODE -ne 0) { throw 'Native qualification failed' }
& ./scripts/build.ps1 -JavaHome $JavaHome
if ($LASTEXITCODE -ne 0) { throw 'Production build failed' }
python ./docs/task-evidence/P05/verify_browser.py $Root (Join-Path $Evidence 'layout') layout
if ($LASTEXITCODE -ne 0) { throw 'Compiled layout qualification failed' }
python ./docs/task-evidence/P05/verify_browser.py $Root (Join-Path $Evidence 'q15') q15
if ($LASTEXITCODE -ne 0) { throw 'Compiled Q15 qualification failed' }
```

For the preserved visual integration, also select `VisualWorkbenchContractTest`,
`PhysicalServiceabilityContractTest` and `U01ViewportContractTest` in the native
runner. Run its untracked `DensePcbPackingContractTest` separately after applying
only the accompanying integration hunk to the exact preserved baseline.
That extra file is not part of the clean P05 commit. Do not fetch or overwrite a
different visual implementation merely to recreate the screenshot appearance.

```powershell
# On the separately preserved integrated checkout, after its own fresh build:
python ./docs/task-evidence/P05/verify_player.py $Root (Join-Path $Evidence 'player') player
if ($LASTEXITCODE -ne 0) { throw 'Normal-player smoke failed' }
```

The player driver operates normal controls: EASY / Procedural control board,
exact seed 3, prepare, accept ticket, Board view / bottom copper, middle-drag and
wheel zoom. It does not inject controller operations. Inspect the generated menu,
ticket, board and copper images. The driver captures page errors and verifies
WORKBENCH state, then closes its browser context and preview server and verifies
recorded process identities are gone. It is not a general CDP-wrapper certificate.

Each browser result has a fresh profile and loopback port. Layout/Q15 retain a
600-second overall verification deadline; player preparation retains 120 seconds.
Neither changes production routing budgets. The full Q15 matrix takes about six
minutes on the recorded host because it includes admission, repair and support
checks across eleven cases. A timeout or cleanup failure is not a passing result.
