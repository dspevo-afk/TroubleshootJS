"""Replace only known local prefixes after the native driver has completed."""
import hashlib
import json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch']).resolve()
assert (HERE / 'unit-timing.json').is_file(), 'No terminal timing yet'
assert json.loads((HERE / 'unit-process-cleanup.json').read_text(encoding='utf-8-sig'))['status'] == 'PASS'
known = [(str(COPY), '[TASK_COPY]'), (str(ROOT), '[WORKTREE]')]
if len(sys.argv) > 1:
    known.append((str(Path(sys.argv[1]).resolve()), '[JDK8]'))
rows = []
for name in ('unit-native.txt', 'unit-native-receipt.txt'):
    path = HERE / name
    if not path.exists():
        continue
    raw = path.read_bytes()
    text = raw.decode('utf-8-sig')
    replacements = []
    for prefix, label in known:
        forms = {prefix, prefix.replace('\\', '/'), json.dumps(prefix)[1:-1]}
        count = 0
        for form in sorted(forms, key=len, reverse=True):
            count += text.count(form)
            text = text.replace(form, label)
        replacements.append({'prefixKind': label, 'count': count})
    # Preserve byte identity when no actual prefix replacement was necessary.
    if any(r['count'] for r in replacements):
        path.write_bytes(text.encode('utf-8'))
    rows.append({'file': name, 'rawSha256': hashlib.sha256(raw).hexdigest(),
        'sanitizedSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'replacements': replacements})
(HERE / 'unit-log-sanitization.json').write_text(json.dumps({'status': 'PASS',
    'scope': 'Known local prefixes only; no status/assertion/timing changes', 'files': rows}, indent=2) + '\n')
print('Known-prefix-only terminal log privacy audit complete')
