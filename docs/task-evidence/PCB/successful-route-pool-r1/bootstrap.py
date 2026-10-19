"""Prepare root-owned observer corpus/host scaffold; never execute Java here."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent/'global-pocket-descent-trial-r1'
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(OLD/'native-inputs.json') == 'f3fd5759e6796e204cbf147ca969fa674cd490e351d6be2bfe1ecf9a463553f4'
COPY = Path(read(HERE/'local-state.json')['scratch'])

def write(name, text):
    p = HERE/name
    assert not p.exists(),name
    p.write_text(text,encoding='utf-8')

unit = [['PcbSuccessfulRoutePoolObserverContractTest','PCB successful route pool observer contracts ']]
routes = [['PcbSuccessfulRoutePoolSeed'+str(seed)+'ContractTest',
    'PCB successful route pool seed'+str(seed)+' contracts '] for seed in (5,2)]
for name,value in (('unit-suites.json',unit),('routes-suites.json',routes)):
    write(name,json.dumps(value,indent=2)+'\n')
for seed in (5,2):
    name='PcbSuccessfulRoutePoolSeed'+str(seed)+'ContractTest'
    target=COPY/'tests/contracts'/(name+'.java')
    assert not target.exists()
    target.write_text('''package com.lushprojects.circuitjs1.client;

/** Read-only successful route pool at one fixed informed root. */
public final class NAME {
    public static void main(String[] args) {
        for(int version:new int[]{15,22})
            PcbSuccessfulRoutePoolObserverContractTest.observe(SEEDL,version,"seedSEED");
        System.out.println("PASS: PCB successful route pool seedSEED contracts "+
            "roots=1 pairs=1 rows=2 evaluator=finalizedRoutePool");
    }
}
'''.replace('NAME',name).replace('SEED',str(seed)))
registry=COPY/'scripts/verify-current-contracts.ps1'
raw=(ROOT/'scripts/verify-current-contracts.ps1').read_bytes()
needle=b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest';"
assert raw.count(needle)==1
rows=''.join("        @{ Name = '"+n+"'; Marker = '"+m+"' },\r\n" for n,m in unit+routes)
registry.write_bytes(raw.replace(needle,rows.encode()+needle))
reuse={}
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','suite_transport.ps1','retire_copy.py'):
    digest=sha(OLD/name)
    assert digest==read(OLD/'native-inputs.json')['gateOwners'][name]
    reuse[name]=digest
    text=(OLD/name).read_text()
    text=text.replace('global-pocket-descent-trial-r1','successful-route-pool-r1')
    text=text.replace('tsj-global-pocket-descent-','tsj-successful-route-pool-')
    text=text.replace('Get-GlobalPocketSuiteNames','Get-SuccessfulRoutePoolSuiteNames')
    text=text.replace('Native global pocket descent trial; unchanged per-JVM/search/physical limits',
        'Native successful route pool observation; unchanged per-JVM/search/physical limits')
    text=text.replace('global-pocket','successful-route-pool').replace('global pocket descent','successful route pool')
    if name=='retire_copy.py':
        assert "'fromCount':1476" in text
        text=text.replace("'fromCount':1476","'fromCount':1470")
    write(name,text)
write('bootstrap-reuse.json',json.dumps({'status':'PASS','scope':'Scaffold-only adaptation; no runtime proof reused','files':reuse},indent=2)+'\n')
checker=(OLD/'check_suite_transport.ps1').read_text()
checker=checker.replace('Get-GlobalPocketSuiteNames','Get-SuccessfulRoutePoolSuiteNames')
start=checker.index('if($unit.Count')
end=checker.index('$negative=')
checker=checker[:start]+'''if($unit.Count -ne 1 -or $unit[0] -cne 'PcbSuccessfulRoutePoolObserverContractTest'){throw 'Actual one-suite array boundary lost'}
$results.Add([ordered]@{case='Actual one-row nested manifest';result='PASS'})
$routes=@(Get-SuccessfulRoutePoolSuiteNames -JsonText (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'routes-suites.json') -Raw))
$expected=@('PcbSuccessfulRoutePoolSeed5ContractTest','PcbSuccessfulRoutePoolSeed2ContractTest')
if($routes.Count -ne 2){throw 'Actual two-row manifest count changed'}
for($i=0;$i -lt 2;$i++){if($routes[$i] -cne $expected[$i]){throw 'Actual suite ordering changed'}}
$results.Add([ordered]@{case='Actual two-row manifest in order';result='PASS'})
'''+checker[end:]
checker=checker.replace('four-row and seven-row inputs exact','one-row and two-row inputs exact')
write('check_suite_transport.ps1',checker)
print('Prepared one unit/two route manifests, root wrappers/registry and adapted bounded host scaffold')
