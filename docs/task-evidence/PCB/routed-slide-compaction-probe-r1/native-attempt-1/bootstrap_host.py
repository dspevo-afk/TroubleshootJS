"""Adapt pinned native host owners; fresh real three-unit transport required."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
OLD=HERE.parent/'routed-passive-feasibility-census-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
pins=read(OLD/'native-inputs.json')['gateOwners']; reused={}
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py',
             'suite_transport.ps1','check_suite_transport.ps1'):
    assert sha(OLD/name)==pins[name];reused[name]=pins[name]
    text=(OLD/name).read_text()
    changes={
      'routed-passive-feasibility-census-r1':'routed-slide-compaction-probe-r1',
      'tsj-routed-passive-feasibility-':'tsj-routed-slide-compaction-',
      'Get-RoutedPassiveFeasibilitySuiteNames':'Get-RoutedSlideCompactionSuiteNames',
      'Native routed passive physical census; unchanged per-JVM/search/physical limits':
        'Native selected routed slide compaction probe; unchanged per-JVM/search/physical limits',
      'private-passive-feasibility':'private-slide-compaction',
      'PcbRoutedPassiveFeasibilityTrialContractTest':'PcbRoutedSlideCompactionTrialContractTest'}
    for a,b in changes.items():text=text.replace(a,b)
    if name=='retire_copy.py':text=text.replace('1470','1471')
    if name=='check_suite_transport.ps1':
        text=text.replace("$unit.Count -ne 2 -or", "$unit.Count -ne 3 -or")
        text=text.replace("$unit[1] -cne 'PcbRoutedPassiveFeasibilityCensusContractTest'", "$unit[1] -cne 'PcbRoutedPassiveFeasibilityCensusContractTest' -or $unit[2] -cne 'PcbRoutedSlideCompactionContractTest'")
        text=text.replace('two-row','three-row').replace('unit2/route1','unit3/route1')
    assert not (HERE/name).exists()
    (HERE/name).write_text(text,encoding='utf-8',newline='')
(HERE/'host-reuse.json').write_text(json.dumps({'status':'PASS','files':reused,
 'scope':'Adapted host source only; fresh real selected PowerShell3unit/1route canaries and fresh native phases required'},indent=2)+'\n')
print('Prepared pinned native host;3unit/1route shapes require fresh proof')
