"""Preserve only task owners before production edits; never expose private paths."""
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
owners = ['src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java',
          'scripts/verify-current-contracts.ps1']
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == 'ffd02ec83ce04465447f31c2a0df568dad52a543'
assert not (HERE / 'local-state.json').exists()
expected = {r['path']: r['sha256'] for r in json.loads((HERE.parent / 'post-selection-edge-trim-r1/final-inputs.json').read_text())['files']}
rows = [{'path': p, 'sha256': hashlib.sha256((ROOT / p).read_bytes()).hexdigest()} for p in owners]
assert all(r['sha256'] == expected[r['path']] for r in rows)
scratch = Path(tempfile.mkdtemp(prefix='tsj-silkscreen-scale-'))
for i, row in enumerate(rows):
    (scratch / str(i)).write_bytes((ROOT / row['path']).read_bytes())
(HERE / 'original-source.json').write_text(json.dumps({'files': rows}, indent=2) + '\n')
(HERE / 'local-state.json').write_text(json.dumps({'scratch': str(scratch)}, indent=2) + '\n')
print('PRESERVED2 exact accepted owners; no production edit yet')
