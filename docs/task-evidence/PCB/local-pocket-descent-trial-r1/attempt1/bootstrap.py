"""Namespace only the proven corpus/transport scaffolding, never trial20 source."""
import hashlib
import re
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE.parent/'routed-trace-guard-trial-r1'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(OLD/'native-inputs.json') == 'ff43bd487094430c77c1bac333c5bc7fc89798791404faff736e3adc5116c3e6'
seal = json.loads((OLD/'native-inputs.json').read_text())
names = ('prepare_cases.py','run_native.ps1','check_process_cleanup.ps1','sanitize.py',
         'suite_transport.ps1','check_suite_transport.ps1','retire_copy.py')
for name in names:
    assert sha(OLD/name) == seal['gateOwners'][name],name
    text = (OLD/name).read_text()
    text = text.replace('routed-trace-guard-trial-r1','local-pocket-descent-trial-r1')
    text = text.replace('tsj-routed-trace-guard-','tsj-local-pocket-descent-')
    text = text.replace('PcbRoutedTraceGuardTrialContractTest','PcbLocalPocketDescentTrialContractTest')
    text = text.replace('PcbRoutedTraceGuardContractTest','PcbLocalPocketDescentContractTest')
    text = text.replace('PcbTraceWidthGeometryContractTest','PcbVacancyTargetsContractTest')
    text = text.replace('PCB routed trace guard trial','PCB local pocket descent trial')
    text = text.replace('PCB routed trace guard contracts','PCB local pocket descent contracts')
    text = text.replace('PCB trace width geometry contracts','PCB vacancy targets contracts')
    text = text.replace('PCB_TRACE_GUARD_CASE','PCB_POCKET_DESCENT_CASE')
    text = text.replace('observeTraceGuard','observePocketDescent')
    text = text.replace('Get-TraceGuardSuiteNames','Get-LocalPocketSuiteNames')
    text = text.replace('Native trace guard trial','Native local pocket descent trial')
    text = text.replace('trace-guard phase','local-pocket phase')
    if name == 'prepare_cases.py':
        text = text.replace('ca050e0445168249481b68dbb357f9e7b5553f9f',
            '91ffe9a3c0e444c28b1ad0ae51005071e7fe0c8a')
        text = re.sub(r'\b20\b','21',text)
        text = text.replace('guard20','pocket21')
        text = text.replace('trace-guard trial root','local-pocket trial root')
        text = text.replace('evaluator=compactRouted','evaluator=planMedium')
        text = text.replace('COPY-only explicit20','COPY-only explicit21')
    target = HERE/name
    assert not target.exists(),name
    target.write_text(text)
print('PASS namespaced corpus/transport only; no rejected policy source included')
