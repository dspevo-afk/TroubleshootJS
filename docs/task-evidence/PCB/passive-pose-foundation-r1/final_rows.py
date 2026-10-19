"""Independent exact source and final-layout reproduction reader."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOTS = ['42', '-1022']
SOURCE_SHA = '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
PREFIX = 'PCB_PASSIVE_POSE_BASELINE_CASE '
SOURCE_PREFIX = 'PCB_PASSIVE_POSE_SOURCE_CASE '
SOURCE_FIELDS = ['seed', 'parts', 'pads', 'sourcePlanSha256', 'liveBoardGraphSha256',
    'sourceIntentSha256', 'layoutSeed', 'routingSeed']
PASS_MARKERS = ['PASS: PCB passive pose foundation contracts ',
    'PASS: PCB passive pose source bindings ', 'PASS: PCB passive pose current15 contracts ',
    'PASS: PCB passive pose current15 Group2 ']
TERMINAL = 'PASS: focused current contracts; 4 Java suites. Full matrix and independent oracles NOT RUN.'
CLEANUP = 'CLEANUP: current JVM contract classes and task-owned scratch removed.'


def terminal(timing, text):
    assert timing['status'] == 'PASS' and type(timing['exitCode']) is int and timing['exitCode'] == 0
    assert type(timing['suiteCount']) is int and timing['suiteCount'] == 4
    assert type(timing['elapsedSeconds']) in (int, float) and timing['elapsedSeconds'] >= 0
    assert timing['terminalCleanupIncluded'] is True
    assert timing['cleanupDuration'] == 'NOT_SEPARATELY_MEASURED'
    for marker in PASS_MARKERS:
        assert sum(line.startswith(marker) for line in text.splitlines()) == 1, 'Missing/duplicate suite PASS'
    assert text.splitlines().count(TERMINAL) == 1 and text.splitlines().count(CLEANUP) == 1
    assert text.index(TERMINAL) < text.index(CLEANUP), 'Terminal cleanup order'


def receipt(receipt_text, native_text):
    # Maintained receipts contain child-suite output, not the outer cleanup line.
    for marker in PASS_MARKERS:
        assert sum(line.startswith(marker) for line in receipt_text.splitlines()) == 1
    for prefix in (PREFIX, SOURCE_PREFIX):
        assert extract(receipt_text, prefix) == extract(native_text, prefix), 'Child receipt differs from terminal log'


def all_baselines():
    source = HERE / 'accepted15-baselines.json'
    assert hashlib.sha256(source.read_bytes()).hexdigest() == SOURCE_SHA
    rows = json.loads(source.read_text())['rows']
    assert len(rows) == 14 and len({r['seed'] for r in rows}) == 14
    return rows


def baselines():
    old = {r['seed']: r for r in all_baselines()}
    return [old[s] for s in ROOTS]


def compare(rows):
    assert type(rows) is list and len(rows) == 2, 'Need exactly two final rows'
    assert [r['seed'] for r in rows] == ROOTS, 'Substituted, duplicate, or reordered root'
    checks = []
    for row, old in zip(rows, baselines()):
        assert set(row) == set(old), 'Final field schema changed'
        for key in old:
            if key == 'elapsedMillis':
                assert type(row[key]) is int and row[key] >= 0
                continue
            assert type(row[key]) is type(old[key]) and row[key] == old[key], 'Accepted15 differs: ' + row['seed'] + '/' + key
        checks.append({'seed': row['seed'], 'status': 'PASS', 'exactFieldsExceptElapsed': len(old) - 1})
    return checks


def compare_sources(rows):
    old = [{k: r[k] for k in SOURCE_FIELDS} for r in all_baselines()]
    assert type(rows) is list and len(rows) == 14
    for row, expected in zip(rows, old):
        assert type(row) is dict and set(row) == set(expected)
        assert all(type(row[k]) is type(v) and row[k] == v for k, v in expected.items()), 'Source binding changed'
    return {'status': 'PASS', 'roots': len(rows), 'fieldsPerRoot': len(SOURCE_FIELDS)}


def extract(text, prefix=PREFIX):
    return [json.loads(line[len(prefix):]) for line in text.splitlines() if line.startswith(prefix)]


if __name__ == '__main__':
    text = (HERE / 'native.txt').read_text(encoding='utf-8-sig')
    terminal(json.loads((HERE / 'timing.json').read_text(encoding='utf-8-sig')), text)
    receipt((HERE / 'native-receipt.txt').read_text(encoding='utf-8-sig'), text)
    result = {'status': 'PASS', 'sourceBindings': compare_sources(extract(text, SOURCE_PREFIX)),
        'finalRows': compare(extract(text)),
        'limits': 'Native capability/identity prerequisite only; pose policy, replay, solver mutation and visual acceptance separate'}
    (HERE / 'reproduction.json').write_text(json.dumps(result, indent=2) + '\n')
    print('PASS all14 source bindings and both final42-field current15 reproductions')
