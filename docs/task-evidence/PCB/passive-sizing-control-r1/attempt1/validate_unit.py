"""Require all fixed source and untouched-candidate prerequisites before routes."""
import json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
read=lambda n:json.loads((HERE/n).read_text(encoding='utf-8-sig'))
try:
    text=(HERE/'unit.txt').read_text(encoding='utf-8-sig')
    assess.check_phase(text,(HERE/'unit-receipt.txt').read_text(encoding='utf-8-sig'),
      read('unit-timing.json'),read('unit-suites.json'))
    bindings=assess.check_sources(text)
    assert len(bindings)==14
    raw=assess.check_raw(text,bindings)
except Exception as error:
    (HERE/'unit-validation.json').write_text(json.dumps({'status':'FAIL',
      'phase':'prerequisiteReader','errorType':type(error).__name__},indent=2)+'\n')
    raise
(HERE/'unit-validation.json').write_text(json.dumps({'status':'PASS','suiteCount':8,
 'oldSourceBindings':14,'trial16Bindings':14,'control17Bindings':14,
 'rawOtherCandidateChecks':raw,
 'limits':'Native control prerequisites only; six full routed generations separate'},indent=2)+'\n')
print('PASS unit8 /14 old+16+17 source bindings /15 untouched candidate comparisons')
