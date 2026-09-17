# Reproduce the Quick Play gate

Run from the repository root with the pinned JDK8/GWT dependencies and a verified
Python executable containing Playwright. Edge is the tested browser. New evidence
must use a unique directory; none of the runners overwrite an earlier result.
The actual run used two independent browser owners for the population. The serial
reproduction below preserves every case and all production budgets, but does not
reproduce concurrent timings. Seed-population criteria are in CRITERIA.md.

```powershell
$Root = (Get-Location).Path
$JavaHome = (Resolve-Path '.tools/jdk8-download/jdk8u502-b07').Path
$PythonExe = 'C:\path\to\verified\python.exe'
$Evidence = Join-Path $env:TEMP ('TroubleshootJS-QuickPlay-recheck-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $Evidence | Out-Null
& $PythonExe -c 'import sys, playwright.sync_api; print(sys.executable)'
if ($LASTEXITCODE -ne 0) { throw 'Python/Playwright prerequisite failed' }
& .\scripts\verify-current-contracts.ps1 -JavaHome $JavaHome -PythonExe $PythonExe -ReceiptOutputPath (Join-Path $Evidence 'native.txt')
if ($LASTEXITCODE -ne 0) { throw 'Full native matrix failed' }
& .\scripts\build.ps1 -JavaHome $JavaHome
if ($LASTEXITCODE -ne 0) { throw 'Production GWT build failed' }
$Prepare = @'
import json, pathlib, sys
out = pathlib.Path(sys.argv[1])
b = (out / 'native.txt').read_bytes()
log = b.decode('utf-16' if b.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')
rows = [json.loads(s[len('GATE_ROW '):]) for s in log.splitlines() if s.startswith('GATE_ROW ')]
assert len(rows) == 72
(out / 'native-rows.json').write_text(json.dumps(rows))
for cohort in ('development', 'holdout'):
    seeds = [r for r in rows if r['cohort'] == cohort]
    for lane in range(2):
        cases = [{'seed': r['seed'], 'cohort': cohort} for r in seeds[lane::2]]
        (out / (cohort + '-' + str(lane) + '.json')).write_text(json.dumps({'cases': cases}))
'@
& $PythonExe -c $Prepare $Evidence
if ($LASTEXITCODE -ne 0) { throw 'Native corpus extraction failed' }
foreach ($Cohort in @('development','holdout')) {
    foreach ($Lane in @(0,1)) {
        $Name = $Cohort + '-' + $Lane
        & $PythonExe .\docs\task-evidence\QuickPlayGate\verify_admission.py $Root (Join-Path $Evidence $Name) (Join-Path $Evidence ($Name + '.json'))
        if ($LASTEXITCODE -ne 0) { throw ('Population runner failed: ' + $Name) }
    }
}
& $PythonExe .\docs\task-evidence\QuickPlayGate\verify_admission.py $Root (Join-Path $Evidence 'canaries') .\docs\task-evidence\QuickPlayGate\canary-cases.json
if ($LASTEXITCODE -ne 0) { throw 'Actual retry/replay/cancellation checks failed' }
$Collect = @'
import json, pathlib, sys
out = pathlib.Path(sys.argv[1])
native = json.loads((out / 'native-rows.json').read_text())
all_rows = []
for cohort in ('development', 'holdout'):
    for lane in range(2):
        all_rows += json.loads((out / (cohort + '-' + str(lane)) / 'result.json').read_text())['cases']
assert len(all_rows) == 72
by_seed = {r['requestedSeed']: r for r in all_rows}
assert len(by_seed) == 72
compiled = [by_seed[r['seed']] for r in native]
canaries = json.loads((out / 'canaries' / 'result.json').read_text())['cases']
(out / 'corpus.json').write_text(json.dumps({'native': native, 'compiled': compiled, 'canaries': canaries}))
'@
& $PythonExe -c $Collect $Evidence
if ($LASTEXITCODE -ne 0) { throw 'Population collection failed' }
& $PythonExe .\tests\contracts\quickplay_gate_evidence.py $Evidence
if ($LASTEXITCODE -ne 0) { throw 'Strict population/geometry reader failed' }
foreach ($Mode in @('q15','q15negative','rc','layout','p07','negative','bench','a10','a10negative')) {
    & $PythonExe .\docs\task-evidence\QuickPlayGate\verify_routes.py $Root (Join-Path $Evidence ('route-' + $Mode)) $Mode
    if ($LASTEXITCODE -ne 0) { throw ('Compiled regression failed: ' + $Mode) }
}
& $PythonExe .\docs\task-evidence\QuickPlayGate\verify_player.py $Root (Join-Path $Evidence 'player') player
if ($LASTEXITCODE -ne 0) { throw 'Actual player input gate failed' }
& $PythonExe .\docs\task-evidence\P09\verify_families.py $Root (Join-Path $Evidence 'families') families
if ($LASTEXITCODE -ne 0) { throw 'Normal family entry gate failed' }
$ExtractQ15 = @'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1]); out = root / 'q15-reader'; out.mkdir()
for name, mode in [('compiled-control-board.json','q15'),('forced-negative.json','q15negative')]:
    report = json.loads((root / ('route-' + mode) / 'result.json').read_text())['report']
    (out / name).write_text(json.dumps(report))
'@
& $PythonExe -c $ExtractQ15 $Evidence
if ($LASTEXITCODE -ne 0) { throw 'Q15 actual-report extraction failed' }
& $PythonExe .\tests\contracts\q15_evidence_contract.py (Join-Path $Evidence 'q15-reader')
if ($LASTEXITCODE -ne 0) { throw 'Strict Q15 reader failed' }
node .\tests\contracts\quickplay_entropy_contract.mjs
if ($LASTEXITCODE -ne 0) { throw 'Signed entropy oracle failed' }
$JsdomApi = 'C:\path\to\jsdom\lib\api.js'
node .\tests\contracts\u04_ui_contract.mjs $JsdomApi
if ($LASTEXITCODE -ne 0) { throw 'U04 UI contracts failed' }
node .\tests\contracts\bench_meter_contract.mjs $JsdomApi
if ($LASTEXITCODE -ne 0) { throw 'Bench-meter UI contracts failed' }
```

Use the clean publication checkout to reproduce the frozen population. The owner's
checkout also contains preserved unrelated visual/packing work; its focused12-suite
selection and separate build/input hashes are retained in the evidence. Do not
substitute one variant's geometry or timings for the other's. Node/jsdom checks
are unit evidence; actual browser inputs and CircuitJS reports remain separate.

The normal-input driver first uses untouched entropy, then explicitly controls
only the eight-byte entropy source for a repeatable rejection/retry fixture.
It uses real DOM clicks throughout, not injected controller actions. Its exact
rejection may complete too quickly to sample an intermediate progress frame;
complete native/compiled attempt ledgers, final ERROR/identity and preserved
isolated-owner checks establish the exact-mode boundary independently of polling.
