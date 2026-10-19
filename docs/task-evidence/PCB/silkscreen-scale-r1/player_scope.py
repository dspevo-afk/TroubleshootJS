"""Pin browser-reader and saved-file inputs before interactive player checks."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
owners = [HERE / 'check_player.py', HERE / 'preview-expected.json',
          HERE.parent / 'canvas-owner-fit-r1/repaired14-55.tsjsave',
          HERE / 'player_scope.py']
rows = [{'path': p.relative_to(ROOT).as_posix(),
         'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in owners]
assert rows[2]['sha256'] == '453928609c7a09c533da3e7df64bd08190ac384b5982cdd712fde7e2a1bb2c71'
target = HERE / 'player-scope.json'
if sys.argv[1] == 'freeze':
    assert not target.exists(), 'Already frozen'
    target.write_text(json.dumps({'files': rows,
        'stage': 'After initial production navigation/identity read; before interactive fit/zoom/repair/restore input. Cold generation source already covered by final1475 frozen inputs.'}, indent=2) + '\n')
    print('FROZEN four browser/saved-input owners before interactive flow')
elif sys.argv[1] == 'audit':
    before = {r['path']: r['sha256'] for r in json.loads(target.read_text())['files']}
    after = {r['path']: r['sha256'] for r in rows}
    changed = [p for p in sorted(set(before) | set(after)) if before.get(p) != after.get(p)]
    result = {'status': 'FAIL' if changed else 'PASS', 'changedPaths': changed,
              'frozenInputCount': len(before), 'actualInputCount': len(after)}
    (HERE / 'player-scope-audit.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    raise SystemExit(bool(changed))
else:
    raise ValueError('Expected freeze/audit')
