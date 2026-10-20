"""Adapt the sealed two-unit/one-route host without reusing runtime evidence."""
import hashlib
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'routed-passive-move-proof-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
pins=read(OLD/'native-inputs.json')['gateOwners']
reused={}
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py',
             'suite_transport.ps1','check_suite_transport.ps1'):
    assert sha(OLD/name)==pins[name]
    reused[name]=pins[name]
    text=(OLD/name).read_text()
    replacements={
        'routed-passive-move-proof-r1':'routed-passive-feasibility-census-r1',
        'tsj-routed-passive-move-proof-':'tsj-routed-passive-feasibility-',
        'Get-RoutedPassiveMoveProofSuiteNames':'Get-RoutedPassiveFeasibilitySuiteNames',
        'Native single private passive move proof; unchanged per-JVM/search/physical limits':
            'Native routed passive physical census; unchanged per-JVM/search/physical limits',
        'private-passive-move':'private-passive-feasibility',
        'PcbRoutedPassiveMoveProofTrialContractTest':'PcbRoutedPassiveFeasibilityTrialContractTest',
        'PcbRoutedPassiveMoveProofContractTest':'PcbRoutedPassiveFeasibilityCensusContractTest',
    }
    for before,after in replacements.items():text=text.replace(before,after)
    assert not (HERE/name).exists()
    (HERE/name).write_text(text,encoding='utf-8',newline='\n')
(HERE/'host-reuse.json').write_text(json.dumps({'status':'PASS','files':reused,
    'scope':'Source scaffold adaptation only; fresh transport/native proof required'},indent=2)+'\n')
print('Prepared sealed host adaptation; fresh unit2/routes1 and prefix-owned cleanup')
