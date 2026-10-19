"""Fresh source/raw prerequisites required before any trial route phase."""
import json
from pathlib import Path
import assess
HERE = Path(__file__).resolve().parent
read = lambda n: json.loads((HERE/n).read_text(encoding='utf-8-sig'))
try:
    text = (HERE/'unit.txt').read_text(encoding='utf-8-sig')
    assess.check_phase(text, (HERE/'unit-receipt.txt').read_text(encoding='utf-8-sig'),
                       read('unit-timing.json'), read('unit-suites.json'))
    bindings = assess.check_sources(text)
    assert len(bindings) == 14
    raw = assess.check_raw(text, bindings)
except Exception as error:
    (HERE/'unit-validation.json').write_text(json.dumps({
        'status': 'FAIL', 'phase': 'prerequisiteReader', 'errorType': type(error).__name__}, indent=2)+'\n')
    raise
(HERE/'unit-validation.json').write_text(json.dumps({
    'status': 'PASS', 'suiteCount': 9, 'oldSourceBindings': 14,
    'trial16Bindings': 14, 'trial19Bindings': 14, 'rawOtherCandidateChecks': raw,
    'limits': 'Prerequisites only; strict28 route rows are separate'}, indent=2)+'\n')
print('PASS unit9 /14 old+16+19 source bindings /15 untouched candidate comparisons')
