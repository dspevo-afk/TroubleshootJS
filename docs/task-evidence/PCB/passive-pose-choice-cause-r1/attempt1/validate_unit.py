"""Require protocol/source prerequisites before any paired fullroute capture."""
import json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
text=(HERE/'unit.txt').read_text(encoding='utf-8-sig')
receipt=(HERE/'unit-receipt.txt').read_text(encoding='utf-8-sig')
timing=json.loads((HERE/'unit-timing.json').read_text(encoding='utf-8-sig'))
suites=json.loads((HERE/'unit-suites.json').read_text())
try:
    assess.check_phase(text,receipt,timing,suites)
    sample=assess.check_protocol_sample(text)
    assert sample['status']=='PASS' and sample['sampleCount']==1 and sample['syntheticOnly'] is True
    bindings=assess.check_sources(text)
    assert len(bindings)==14
except Exception as error:
    (HERE/'unit-validation.json').write_text(json.dumps({'status':'FAIL',
      'phase':'prerequisiteReader','errorType':type(error).__name__},indent=2)+'\n')
    raise
(HERE/'unit-validation.json').write_text(json.dumps({'status':'PASS','suiteCount':3,
 'protocolSample':sample,'oldSourceBindings':14,'derivedTrialBindings':len(bindings),
 'limits':'Native observer protocol and source prerequisites only; fullroute capture/fidelity separate'},indent=2)+'\n')
print('PASS unit3 observer protocol and14+14 exact source bindings')
