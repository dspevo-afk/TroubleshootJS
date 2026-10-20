"""Adapt the proven bounded host for two units and one private-move route suite."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE.parent/'routed-passive-leg-census-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
pins = read(OLD/'native-inputs.json')['gateOwners']
reused = {}
names = ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','suite_transport.ps1',
    'retire_copy.py','check_suite_transport.ps1')
for name in names:
    assert sha(OLD/name) == pins[name]
    reused[name] = pins[name]
    text = (OLD/name).read_text()
    text = text.replace('routed-passive-leg-census-r1','routed-passive-move-proof-r1')
    text = text.replace('tsj-routed-passive-leg-census-','tsj-routed-passive-move-proof-')
    text = text.replace('Get-RoutedPassiveLegSuiteNames','Get-RoutedPassiveMoveProofSuiteNames')
    text = text.replace('Native routed passive leg census; unchanged per-JVM/search/physical limits',
        'Native single private passive move proof; unchanged per-JVM/search/physical limits')
    text = text.replace('routed-passive-leg phase','private-passive-move phase')
    if name == 'retire_copy.py':
        assert "'fromCount':1469" in text
        text = text.replace("'fromCount':1469", "'fromCount':1470")
    if name == 'check_suite_transport.ps1':
        start = text.index('if($unit.Count')
        end = text.index('$negative=')
        text = text[:start]+'''if($unit.Count -ne 2 -or $unit[0] -cne 'PcbRoutedPassiveLegCensusContractTest' -or $unit[1] -cne 'PcbRoutedPassiveMoveProofContractTest'){throw 'Unit two-row nested array/order lost'}
$results.Add([ordered]@{case='Actual unit two-row nested manifest';result='PASS'})
$routes=@(Get-RoutedPassiveMoveProofSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
if($routes.Count -ne 1 -or $routes[0] -cne 'PcbRoutedPassiveMoveProofTrialContractTest'){throw 'Route single-row nested array lost'}
$results.Add([ordered]@{case='Actual route one-row nested manifest';result='PASS'})
'''+text[end:]
        text = text.replace('unit/route one-row nested inputs exact','unit2/route1 nested inputs exact')
    assert not (HERE/name).exists()
    (HERE/name).write_text(text)
(HERE/'host-reuse.json').write_text(json.dumps({'status':'PASS',
    'scope':'Scaffold adaptation only; prior native/host runtime proof not reused',
    'files':reused},indent=2)+'\n')
print('Prepared bounded2/1 native host and exact ownership-prefix retirement')
