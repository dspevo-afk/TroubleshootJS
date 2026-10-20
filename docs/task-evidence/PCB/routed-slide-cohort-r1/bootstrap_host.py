"""Adapt exact prior native owners, with fresh5unit/7route transport canaries."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;OLD=HERE.parent/'routed-slide-descent-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
pins=read(OLD/'native-inputs.json')['gateOwners'];reused={}
for name in ('run_native.ps1','check_process_cleanup.ps1','sanitize.py','retire_copy.py','suite_transport.ps1','check_suite_transport.ps1'):
    assert sha(OLD/name)==pins[name];reused[name]=pins[name]
    text=(OLD/name).read_text()
    for old,new in {
        'routed-slide-descent-r1':'routed-slide-cohort-r1',
        'tsj-routed-slide-descent-':'tsj-routed-slide-cohort-',
        'Get-RoutedSlideDescentSuiteNames':'Get-RoutedSlideCohortSuiteNames',
        'Native selected routed slide descent; unchanged per-JVM/search/physical limits':
            'Native informed14 routed slide cohort; unchanged per-JVM/search/physical limits',
        'private-slide-descent':'private-slide-cohort',
        'PcbRoutedSlideDescentTrialContractTest':'PcbRoutedSlideCohortTrialContractTest',
    }.items():text=text.replace(old,new)
    if name=='retire_copy.py':text=text.replace('1473','1481')
    if name=='check_suite_transport.ps1':
        text=text.replace('$unit.Count -ne 4 -or','$unit.Count -ne 5 -or')
        text=text.replace("$unit[3] -cne 'PcbRoutedSlideDescentContractTest'", "$unit[3] -cne 'PcbRoutedSlideDescentContractTest' -or $unit[4] -cne 'PcbRoutedSlideCohortContractTest'")
        old="if($routes.Count -ne 1 -or $routes[0] -cne 'PcbRoutedSlideCohortTrialContractTest'){throw 'Route single-row nested array lost'}"
        assert text.count(old)==1
        names=['PcbRoutedSlideCohortTrialContractTest'+('' if i==1 else 'Group'+str(i)) for i in range(1,8)]
        new="$expectedRoutes=@("+','.join("'"+n+"'" for n in names)+")\n"
        new+="if($routes.Count -ne 7){throw 'Route seven-row nested array lost'}\n"
        new+="for($routeIndex=0;$routeIndex -lt 7;$routeIndex++){if($routes[$routeIndex] -cne $expectedRoutes[$routeIndex]){throw 'Route partition order changed'}}"
        text=text.replace(old,new)
        text=text.replace('four-row','five-row').replace('route one-row','route seven-row').replace('unit4/route1','unit5/route7')
    assert not (HERE/name).exists();(HERE/name).write_text(text,encoding='utf-8',newline='')
(HERE/'host-reuse.json').write_text(json.dumps({'status':'PASS','files':reused,
    'scope':'Adapted source only; fresh selected PowerShell5unit/7route transport and maintained native required;60second child unchanged'},indent=2)+'\n')
print('PASS host owners pinned/adapted; fresh5unit/7route transport required')
