"""Pre-runtime synthetic checks of exact final reproduction; no observed trial data."""
import copy
import hashlib
import json
from pathlib import Path
import final_rows as reader
HERE = Path(__file__).resolve().parent
rows = reader.baselines()
assert len(reader.compare(copy.deepcopy(rows))) == 2
bad = 0


def reject(change):
    global bad
    altered = copy.deepcopy(rows)
    change(altered)
    try:
        reader.compare(altered)
    except (AssertionError, KeyError, TypeError, ValueError):
        bad += 1
        return
    raise AssertionError('Synthetic altered final row accepted')


reject(lambda values: values.pop())
reject(lambda values: values.append(copy.deepcopy(values[0])))
reject(lambda values: values.reverse())
reject(lambda values: values.__setitem__(1, copy.deepcopy(values[0])))
for field in rows[0]:
    if field == 'elapsedMillis':
        continue
    def change(values, key=field):
        value = values[0][key]
        if type(value) is str:
            values[0][key] = value + '-changed'
        elif type(value) is int:
            values[0][key] = value + 1
        elif type(value) is list:
            values[0][key] = value + [99]
        else:
            raise AssertionError('Unexpected baseline field type')
    reject(change)
reject(lambda values: values[0].__setitem__('layoutSeed', int(values[0]['layoutSeed'])))
reject(lambda values: values[0].__setitem__('elapsedMillis', -1))
reject(lambda values: values[0].__setitem__('elapsedMillis', True))
(HERE / 'final-row-canaries.json').write_text(json.dumps({'status': 'PASS',
    'phase': 'Before observer native runtime', 'positiveCases': 1, 'negativeCases': bad,
    'readerSha256': hashlib.sha256((HERE / 'final_rows.py').read_bytes()).hexdigest(),
    'scope': 'Synthetic exact final metric/source/identity/work reader only; event and physical proof separate'}, indent=2) + '\n')
print('PASS final-row synthetic1positive/' + str(bad) + 'negative canaries')
