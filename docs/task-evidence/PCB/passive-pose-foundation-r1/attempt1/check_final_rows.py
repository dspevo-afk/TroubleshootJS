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
source_rows = [{k: r[k] for k in reader.SOURCE_FIELDS} for r in reader.all_baselines()]
assert reader.compare_sources(copy.deepcopy(source_rows))['roots'] == 14
source_bad = 0


def reject_source(change):
    global source_bad
    altered = copy.deepcopy(source_rows)
    change(altered)
    try:
        reader.compare_sources(altered)
    except (AssertionError, KeyError, TypeError, ValueError):
        source_bad += 1
        return
    raise AssertionError('Synthetic altered source binding accepted')


reject_source(lambda values: values.pop())
reject_source(lambda values: values.reverse())
reject_source(lambda values: values.__setitem__(1, copy.deepcopy(values[0])))
for index in range(14):
    for field in reader.SOURCE_FIELDS:
        def change(values, at=index, key=field):
            value = values[at][key]
            values[at][key] = value + 1 if type(value) is int else value + '-changed'
        reject_source(change)
reject_source(lambda values: values[0].__setitem__('layoutSeed', int(values[0]['layoutSeed'])))
reject_source(lambda values: values[0].__setitem__('parts', True))
timing = {'status': 'PASS', 'exitCode': 0, 'suiteCount': 4, 'elapsedSeconds': 1.0,
    'terminalCleanupIncluded': True, 'cleanupDuration': 'NOT_SEPARATELY_MEASURED'}
terminal_text = '\n'.join(reader.PASS_MARKERS + [reader.TERMINAL, reader.CLEANUP])
reader.terminal(timing, terminal_text)
terminal_bad = 0
for field, value in [('status', 'FAIL'), ('exitCode', 1), ('exitCode', False), ('suiteCount', 3),
        ('suiteCount', True), ('elapsedSeconds', -1), ('elapsedSeconds', True),
        ('terminalCleanupIncluded', False), ('cleanupDuration', 'ASSUMED')]:
    altered = dict(timing)
    altered[field] = value
    try:
        reader.terminal(altered, terminal_text)
    except AssertionError:
        terminal_bad += 1
    else:
        raise AssertionError('Bad terminal timing accepted')
for marker in reader.PASS_MARKERS + [reader.TERMINAL, reader.CLEANUP]:
    for altered in (terminal_text.replace(marker, 'MISSING'), terminal_text + '\n' + marker):
        try:
            reader.terminal(timing, altered)
        except AssertionError:
            terminal_bad += 1
        else:
            raise AssertionError('Bad terminal marker accepted')
receipt_text = '\n'.join(reader.PASS_MARKERS) + '\n' + reader.SOURCE_PREFIX + '{"seed":"42"}'
native_text = receipt_text + '\n' + reader.TERMINAL + '\n' + reader.CLEANUP
reader.receipt(receipt_text, native_text)
receipt_bad = 0
for altered in [receipt_text.replace(m, 'MISSING') for m in reader.PASS_MARKERS] + [
        receipt_text + '\n' + reader.PASS_MARKERS[0], receipt_text.replace('"42"', '"17"')]:
    try:
        reader.receipt(altered, native_text)
    except AssertionError:
        receipt_bad += 1
    else:
        raise AssertionError('Bad child receipt accepted')
(HERE / 'final-row-canaries.json').write_text(json.dumps({'status': 'PASS',
    'phase': 'Before passive foundation native runtime', 'positiveCases': 1, 'negativeCases': bad,
    'sourcePositiveCases': 1, 'sourceNegativeCases': source_bad,
    'terminalPositiveCases': 1, 'terminalNegativeCases': terminal_bad,
    'receiptPositiveCases': 1, 'receiptNegativeCases': receipt_bad,
    'readerSha256': hashlib.sha256((HERE / 'final_rows.py').read_bytes()).hexdigest(),
    'scope': 'Synthetic exact final metric/source/identity/work reader only; event and physical proof separate'}, indent=2) + '\n')
print('PASS final synthetic1positive/' + str(bad) + 'negative; source1positive/' + str(source_bad) + 'negative')
