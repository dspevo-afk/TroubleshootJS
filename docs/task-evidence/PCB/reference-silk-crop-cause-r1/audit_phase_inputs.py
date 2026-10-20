from pathlib import Path
import json, runpy, sys

ROOT=Path(__file__).resolve().parents[4]
PACKET=Path(__file__).resolve().parent
REF=ROOT/'docs/task-evidence/PCB/reference-lead-clearance-r1'
OWNED=('src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java',
       'tests/contracts/PcbFunctionalGroupingContractTest.java')
phase=sys.argv[1]
if phase not in ('baseline','candidate'): raise SystemExit('phase must be baseline or candidate')
module=runpy.run_path(str(REF/'final_inputs.py'))
observed=module['record']()
expected=json.loads((REF/'final-inputs.json').read_text(encoding='utf-8-sig'))
apply=runpy.run_path(str(PACKET/'apply_observers.py'))
gen,test=apply['expected_sources'](phase)
if (ROOT/OWNED[0]).read_bytes()!=gen or (ROOT/OWNED[1]).read_bytes()!=test:
    raise SystemExit('FAIL phase-owned bytes differ from sealed source plus insertion-only observers')
for name in OWNED:
    observed['files'][name]=expected['files'][name]
if observed!=expected:
    raise SystemExit('FAIL one or more non-owned inputs, machinery, gates, prerequisites, or repository identity changed')
print('PASS phase inputs sources=1481 (2 exact phase-owned overlays) machinery=24 gates=8')