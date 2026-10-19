"""Freeze final native/build owners and audit the exact consumed boundary."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / 'post-selection-edge-trim-r1/final-inputs.json'


def collect():
    paths = {ROOT / r['path'] for r in json.loads(PRIOR.read_text())['files']}
    for directory in ('src', 'scripts', 'tests/contracts'):
        for p in (ROOT / directory).rglob('*'):
            if p.is_file() and '__pycache__' not in p.parts and p.suffix != '.pyc':
                paths.add(p)
    paths.update((ROOT / '.tools/gwt-2.7.0').glob('*.jar'))
    paths.update(HERE / name for name in ('proposal.json', 'original-source.json',
                 'suite-plan.json', 'register.py', 'run_phase.ps1', 'freeze_inputs.py', 'check_player.py', 'audit_owners.py'))
    return [{'path': p.relative_to(ROOT).as_posix(),
             'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in sorted(paths)]


if __name__ == '__main__':
    actual = collect()
    target = HERE / 'final-inputs.json'
    if sys.argv[1] == 'freeze':
        assert not target.exists(), 'Already frozen'
        target.write_text(json.dumps({'files': actual}, indent=2) + '\n')
        print('FROZEN inputs=' + str(len(actual)))
    elif sys.argv[1] == 'audit':
        label = sys.argv[2]
        assert label.isascii() and label.replace('-', '').isalnum()
        before = {r['path']: r['sha256'] for r in json.loads(target.read_text())['files']}
        after = {r['path']: r['sha256'] for r in actual}
        changed = [p for p in sorted(set(before) | set(after)) if before.get(p) != after.get(p)]
        result = {'status': 'FAIL' if changed else 'PASS', 'changedPaths': changed,
                  'frozenInputCount': len(before), 'actualInputCount': len(after)}
        (HERE / (label + '-input-audit.json')).write_text(json.dumps(result, indent=2) + '\n')
        print(json.dumps(result))
        raise SystemExit(bool(changed))
    else:
        raise ValueError('Expected freeze/audit')
