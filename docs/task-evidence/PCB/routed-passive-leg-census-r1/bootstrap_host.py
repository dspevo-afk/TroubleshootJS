"""Adapt the proven bounded native host for the one-unit/one-route census."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE.parent/'successful-route-pool-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
pins = read(OLD/'native-inputs.json')['gateOwners']
reused = {}
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','suite_transport.ps1','retire_copy.py'):
    assert sha(OLD/name) == pins[name]
    reused[name] = pins[name]
    text = (OLD/name).read_text()
    text = text.replace('successful-route-pool-r1','routed-passive-leg-census-r1')
    text = text.replace('tsj-successful-route-pool-','tsj-routed-passive-leg-census-')
    text = text.replace('Get-SuccessfulRoutePoolSuiteNames','Get-RoutedPassiveLegSuiteNames')
    text = text.replace('Native successful route pool observation; unchanged per-JVM/search/physical limits',
        'Native routed passive leg census; unchanged per-JVM/search/physical limits')
    text = text.replace('successful-route-pool phase','routed-passive-leg phase')
    if name == 'retire_copy.py':
        assert "'fromCount':1470" in text
        text = text.replace("'fromCount':1470","'fromCount':1469")
    assert not (HERE/name).exists(),name
    (HERE/name).write_text(text)
name = 'check_suite_transport.ps1'
assert sha(OLD/name) == pins[name]
reused[name] = pins[name]
text = (OLD/name).read_text().replace('Get-SuccessfulRoutePoolSuiteNames','Get-RoutedPassiveLegSuiteNames')
start = text.index('if($unit.Count')
end = text.index('$negative=')
text = text[:start]+'''if($unit.Count -ne 1 -or $unit[0] -cne 'PcbRoutedPassiveLegCensusContractTest'){throw 'Unit single-row nested array lost'}
$results.Add([ordered]@{case='Actual unit one-row nested manifest';result='PASS'})
$routes=@(Get-RoutedPassiveLegSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
if($routes.Count -ne 1 -or $routes[0] -cne 'PcbRoutedPassiveLegCensusTrialContractTest'){throw 'Route single-row nested array lost'}
$results.Add([ordered]@{case='Actual route one-row nested manifest';result='PASS'})
'''+text[end:]
text = text.replace('one-row and two-row inputs exact','unit/route one-row nested inputs exact')
assert not (HERE/name).exists()
(HERE/name).write_text(text)
(HERE/'host-reuse.json').write_text(json.dumps({'status':'PASS',
    'scope':'Scaffold-only adaptation; no previous runtime evidence reused','files':reused},indent=2)+'\n')
print('Prepared bounded1/1 native host and literal ownership-prefix retirement')
