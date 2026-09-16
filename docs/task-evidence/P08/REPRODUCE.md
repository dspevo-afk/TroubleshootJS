# Reproduce P08

Run from a clean checkout of the published P08 commit. Use the existing pinned
JDK8/GWT build. Browser checks require installed Edge and Python Playwright.
Every browser output directory must be new; the runners own their profiles,
loopback listeners and exact process identities and verify their cleanup.

The native WindowsApps Python alias failed to qualify on this host. A task-local
virtual environment passed the unchanged bounded-process launcher. No timeout,
physical rule, independent oracle or expected negative was relaxed.

```powershell
$Repo = (Get-Location).Path
$JavaHome = Join-Path $Repo '.tools/jdk8-download/jdk8u502-b07'
$Run = Join-Path $env:TEMP ('TroubleshootJS-P08-repro-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $Run | Out-Null
python -m venv --without-pip --system-site-packages (Join-Path $Run 'pyenv')
if ($LASTEXITCODE -ne 0) { throw 'Python environment creation failed' }
$Python = Join-Path $Run 'pyenv/Scripts/python.exe'
& $Python -c 'import playwright.sync_api'
if ($LASTEXITCODE -ne 0) { throw 'Playwright is required in this Python environment' }
& ./scripts/verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $Python `
    -ReceiptOutputPath (Join-Path $Run 'native-receipt.txt')
if ($LASTEXITCODE -ne 0) { throw 'Native contract gate failed' }
& ./scripts/build.ps1 -JavaHome $JavaHome
if ($LASTEXITCODE -ne 0) { throw 'Production GWT build failed' }
foreach ($Mode in @('p07','negative','bench','layout','q15','q15negative')) {
    & $Python ./docs/task-evidence/P07/verify_browser.py $Repo (Join-Path $Run $Mode) $Mode
    if ($LASTEXITCODE -ne 0) { throw "Compiled gate failed: $Mode" }
}
foreach ($Mode in @('p06','negative','bench')) {
    & $Python ./docs/task-evidence/P06/verify_browser.py $Repo (Join-Path $Run ('p06-' + $Mode)) $Mode
    if ($LASTEXITCODE -ne 0) { throw "P06 compiled gate failed: $Mode" }
}
& $Python ./docs/task-evidence/P07/verify_player.py $Repo (Join-Path $Run 'player') player
if ($LASTEXITCODE -ne 0) { throw 'Normal player/privacy gate failed' }
```

The forced-negative modes succeed only when the verifier fails intentionally,
restores the previous owner and releases task-owned resources. They are not
positive electrical proofs. P07 bench uses actual mouse inputs and checks separate
5 V and 7 V copper circuits; P06 bench exercises real link removal/reinstallation.
Normal-player checks use no debug permission and include seed preparation, ticket
acceptance, face change, pan and zoom without developer metadata disclosure.

For a focused native rerun, pass `-Suite @('P08ScalabilityContractTest')` to the
same native script. It prints scale and dense-contact measurements, but deliberately
does not claim that the rest of the matrix or independent report oracles ran.
Do not compare mixed fixture dimensions to production board density; this is a
physical-validation workload, not a P09 routing or production-envelope result.
