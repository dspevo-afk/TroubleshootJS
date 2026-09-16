# Reproduce P09

Run from the repository root. Use the pinned JDK8/GWT dependencies and an actual
Python executable with Playwright installed. Do not pass a Windows Store alias
or an untested venv redirector to the bounded process runner. The task-local
portable runtime's official download and verified digest are in `python-runtime.json`.
It changed no system settings. Substitute your own absolute executable path below.

```powershell
$JavaHome = (Resolve-Path '.tools/jdk8-download/jdk8u502-b07').Path
$PythonExe = 'C:\path\to\verified\python.exe'
& $PythonExe -c 'import sys; import playwright.sync_api; print(sys.executable)'
if ($LASTEXITCODE -ne 0) { throw 'Python/Playwright prerequisite failed' }
$Evidence = Join-Path $env:TEMP ('TroubleshootJS-P09-recheck-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Evidence | Out-Null
& .\scripts\verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -ReceiptOutputPath (Join-Path $Evidence 'native.txt')
if ($LASTEXITCODE -ne 0) { throw 'Native verification failed' }
& .\scripts\build.ps1 -JavaHome $JavaHome
if ($LASTEXITCODE -ne 0) { throw 'Production GWT build failed' }
$Root = (Get-Location).Path
foreach ($Mode in @('layout','rc','q15','q15negative','p07','negative','bench')) {
    & $PythonExe .\docs\task-evidence\P07\verify_browser.py $Root (Join-Path $Evidence ('p07-' + $Mode)) $Mode
    if ($LASTEXITCODE -ne 0) { throw ('Compiled gate failed: ' + $Mode) }
}
```

Then verify the factory-link developer route and normal-player isolation:

```powershell
foreach ($Mode in @('p06','negative','bench')) {
    & $PythonExe .\docs\task-evidence\P06\verify_browser.py $Root (Join-Path $Evidence ('p06-' + $Mode)) $Mode
    if ($LASTEXITCODE -ne 0) { throw ('Factory-link gate failed: ' + $Mode) }
}
& $PythonExe .\docs\task-evidence\P07\verify_player.py $Root (Join-Path $Evidence 'player') player
if ($LASTEXITCODE -ne 0) { throw 'Normal-player gate failed' }
```

`layout` includes the shared P09 policy falsifiers and an actual pre-install
negative proving the original live owner is unchanged. `q15` runs the eleven
current RB15 real-CircuitJS cases. Forced-negative modes deliberately produce
an expected FAIL application marker and a PASS runner result only when that exact
canary and owner restoration are observed. `bench` uses ordinary mouse input,
not injected model readings. Every browser run must retain its identity, compiled
resource names, status, screenshots and successful exact-owned cleanup.

The full native runner includes both P09 suites and independent seed, provider,
diagnostic, value and report/listener contracts. Quick Play runs first; its old
60-second budget and cases are unchanged. The new P09 structural corpus has its
predeclared 180-second test budget; it does not alter production generation limits.
A focused `-Suite P09EnvelopeContractTest,P09EnvelopeCorpus` invocation is useful
during development but is not a substitute for the full qualification above.


Run the actual normal nine-family entry matrix and strict Q15 evidence reader:

```powershell
& $PythonExe .\docs\task-evidence\P09\verify_families.py $Root (Join-Path $Evidence 'families') families
if ($LASTEXITCODE -ne 0) { throw 'Nine-family normal-input qualification failed' }
$Extract = @'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
out = root / 'q15-reader'
out.mkdir()
for filename, mode in [('compiled-control-board.json', 'q15'), ('forced-negative.json', 'q15negative')]:
    result = json.loads((root / ('p07-' + mode) / 'result.json').read_text())
    (out / filename).write_text(json.dumps(result['report']), encoding='utf-8')
'@
& $PythonExe -c $Extract $Evidence
if ($LASTEXITCODE -ne 0) { throw 'Actual Q15 report extraction failed' }
& $PythonExe .\tests\contracts\q15_evidence_contract.py (Join-Path $Evidence 'q15-reader')
if ($LASTEXITCODE -ne 0) { throw 'Strict Q15 report/negative reader failed' }
```

The final receipts distinguish clean P09 from the actual checkout containing
preserved unrelated changes. Repeat the focused integrated gate only on that
explicit source variant; do not describe its visual work as part of the commit.
