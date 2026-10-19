"""Exact accepted15 reproduction boundary, independent of observer event schema."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOTS = ['42', '-1022']
SOURCE_SHA = '12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816'
PREFIX = 'PCB_ROUTED_STRIP_CASE '


def baselines():
    source = HERE.parent / 'vacancy-origin-grid-r1/accepted15-baselines.json'
    assert hashlib.sha256(source.read_bytes()).hexdigest() == SOURCE_SHA
    old = {r['seed']: r for r in json.loads(source.read_text())['rows']}
    local = json.loads((HERE / 'accepted15-baselines.json').read_text())
    assert local['sourceSha256'] == SOURCE_SHA and local['rows'] == [old[s] for s in ROOTS]
    return local['rows']


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


def extract(text):
    return [json.loads(line[len(PREFIX):]) for line in text.splitlines() if line.startswith(PREFIX)]
