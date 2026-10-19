"""Apply the frozen control reader to completed, cleaned phase artifacts."""
import json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
read=lambda name:json.loads((HERE/name).read_text(encoding='utf-8-sig'))
try:
    assert read('unit-validation.json')['status']=='PASS'
    for phase in ('unit','routes'):
        assert read(phase+'-process-cleanup.json')['status']=='PASS'
        assert read(phase+'-log-sanitization.json')['status']=='PASS'
        assess.check_phase((HERE/(phase+'.txt')).read_text(encoding='utf-8-sig'),
          (HERE/(phase+'-receipt.txt')).read_text(encoding='utf-8-sig'),
          read(phase+'-timing.json'),read('unit-suites.json' if phase=='unit' else 'route-suites.json'))
    unit=(HERE/'unit.txt').read_text(encoding='utf-8-sig')
    routes=(HERE/'routes.txt').read_text(encoding='utf-8-sig')
    bindings=assess.check_sources(unit)
    assess.check_raw(unit,bindings)
    result=assess.assess_routes(assess.extract(routes,'PCB_SIZING_CONTROL_ROUTE '),
      assess.extract(routes,'PCB_SIZING_CONTROL_POSES '),bindings)
except Exception as error:
    (HERE/'assessment.json').write_text(json.dumps({'status':'FAIL',
      'phase':'completedArtifactReader','errorType':type(error).__name__},indent=2)+'\n')
    raise
(HERE/'assessment.json').write_text(json.dumps(result,indent=2)+'\n')
print('Sizing-control diagnostic assessment='+result['status']+'; no policy promotion')
raise SystemExit(0 if result['status']=='PASS' else 2)
