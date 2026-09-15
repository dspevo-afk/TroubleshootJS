# Reproduce P06 qualification

Run from the repository root in PowerShell with the maintained JDK8/GWT checkout.
Use a fresh output directory for each browser invocation. Do not reuse another
session's profile, listener or class directory.

```powershell
$repo = (Get-Location).Path
$jdk = Join-Path $repo '.tools/jdk8-download/jdk8u502-b07'
$evidence = Join-Path $env:TEMP ('TroubleshootJS-P06-check-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $evidence | Out-Null
[Environment]::CurrentDirectory = $repo

# Full current native matrix and independent oracles (43 registered Java suites).
& ./scripts/verify-current-contracts.ps1 -JavaHome $jdk `
  -ReceiptOutputPath (Join-Path $evidence 'native.txt')
if ($LASTEXITCODE -ne 0) { throw 'Native qualification failed' }

# Actual final-source GWT build, not the JVM contract compilation.
& ./scripts/build.ps1 -JavaHome $jdk
if ($LASTEXITCODE -ne 0) { throw 'Production build failed' }

# Real solver + ownership restoration, intentional failure cleanup,
# and real mouse/accessible-control remove/install/50-milliohm measurement.
foreach ($mode in @('p06', 'negative', 'bench', 'layout', 'q15')) {
  python ./docs/task-evidence/P06/verify_browser.py $repo (Join-Path $evidence $mode) $mode
  if ($LASTEXITCODE -ne 0) { throw "Compiled qualification failed: $mode" }
}

# Normal player menu -> procedural seed 3 -> ticket -> board -> flip/pan/zoom.
python ./docs/task-evidence/P05/verify_player.py $repo (Join-Path $evidence 'player') player
if ($LASTEXITCODE -ne 0) { throw 'Normal player smoke failed' }
```

The browser driver uses installed Python Playwright and the installed Edge channel,
creates its own loopback listener and browser profile, records executable/process
creation identities, closes the browser context and listener, and fails if owned
processes survive. Its original 600-second whole-verifier bound remains. The normal
player driver retains its 120-second preparation bound. Native suites retain the
60-second individual bound. No budget was raised for P06.

For targeted native iteration, add
`-Suite P06FactoryLinkContractTest,PhysicalServiceabilityContractTest,A11ProviderConformanceTest`.
The P06 fixture compares three frozen vertical offsets with genuine all-copper
controls. The separate flat-axial mutant must not acquire underpass permission.

To inspect the prototype interactively after a maintained build, serve `war` over
loopback and open `circuitjs.html` with this query:

```text
?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyP06=true&tsjP06Bench=true
```

This is an explicit **developer-only** bench, not Quick Play admission. Omitting
`tsjP06Bench` restores the prior owner after the test. `tsjP06Fail=true` intentionally
fails after mutation checks and must still report exact owner restoration.
The prototype is intentionally not a saved/customer challenge or a substitute for
fresh normal diagnostic proof. P09 must qualify any eventual generator adoption.

Clean publication source excludes the pre-existing visual/FPS/packing/tray/shop
work. The separate integration receipts include that preserved work and its dense
packing test. Do not include those unrelated changes when reproducing the clean
P06 commit or preparing another milestone's publication.
