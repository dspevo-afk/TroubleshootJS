"""Seal a repeat of the bounded offline reader; never rerun a generator."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
paths = [HERE / n for n in ('proposal.json', 'analyze.py', 'README.md', 'seal_and_check.py')]
paths.append(HERE.parent / 'placement-score-terms-r1/native.txt')
rows = [{'path': p.relative_to(ROOT).as_posix(), 'sha256': sha(p)} for p in paths]
target = HERE / 'repeat-inputs.json'
assert not target.exists(), 'Already sealed'
target.write_text(json.dumps({'files': rows, 'phase': 'Offline repeat; exploratory execution already recorded'}, indent=2) + '\n')
before = sha(HERE / 'analysis.json')
result = subprocess.run([sys.executable, '-X', 'utf8', '-B', str(HERE / 'analyze.py')], cwd=ROOT,
                        capture_output=True, text=True, timeout=30)
assert result.returncode == 0, result.stderr[:600]
assert sha(HERE / 'analysis.json') == before, 'Offline repeat changed output'
assert all(sha(ROOT / r['path']) == r['sha256'] for r in rows), 'Frozen offline input changed'
proof = {'status': 'PASS', 'boundInputCount': len(rows), 'outputSha256': before,
         'repeatedOutputExact': True, 'executed': 'Offline Python reader only; no native/build/generation/routing',
         'coldOrRestoreBudgetChange': False, 'resourcesCreated': False}
(HERE / 'repeat-audit.json').write_text(json.dumps(proof, indent=2) + '\n')
print('PASS frozen offline repeat5 exact inputs/output; original choice retained')
