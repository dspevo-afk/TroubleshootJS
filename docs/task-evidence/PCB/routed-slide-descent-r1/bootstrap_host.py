"""Adapt frozen host owners; require fresh selected-host transport proof."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;OLD=HERE.parent/'routed-slide-compaction-probe-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
pins=read(OLD/'native-inputs.json')['gateOwners'];reused={}
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py','suite_transport.ps1','check_suite_transport.ps1'):
    assert sha(OLD/name)==pins[name];reused[name]=pins[name]
    text=(OLD/name).read_text()
    for old,new in {
        'routed-slide-compaction-probe-r1':'routed-slide-descent-r1',
        'tsj-routed-slide-compaction-':'tsj-routed-slide-descent-',
        'Get-RoutedSlideCompactionSuiteNames':'Get-RoutedSlideDescentSuiteNames',
        'Native selected routed slide compaction probe; unchanged per-JVM/search/physical limits':
            'Native selected routed slide descent; unchanged per-JVM/search/physical limits',
        'private-slide-compaction':'private-slide-descent',
        'PcbRoutedSlideCompactionTrialContractTest':'PcbRoutedSlideDescentTrialContractTest',
    }.items():text=text.replace(old,new)
    if name=='retire_copy.py':text=text.replace('1471','1473')
    if name=='check_suite_transport.ps1':
        text=text.replace('$unit.Count -ne 3 -or','$unit.Count -ne 4 -or')
        text=text.replace("$unit[2] -cne 'PcbRoutedSlideCompactionContractTest'", " $unit[2] -cne 'PcbRoutedSlideCompactionContractTest' -or $unit[3] -cne 'PcbRoutedSlideDescentContractTest'")
        text=text.replace('three-row','four-row').replace('unit3/route1','unit4/route1')
    assert not (HERE/name).exists();(HERE/name).write_text(text,encoding='utf-8',newline='')
(HERE/'host-reuse.json').write_text(json.dumps({'status':'PASS','files':reused,
    'scope':'Adapted source only; fresh actual selected PowerShell4unit/1route transport and native required'},indent=2)+'\n')
print('PASS host owners pinned/adapted; fresh real transport required')
