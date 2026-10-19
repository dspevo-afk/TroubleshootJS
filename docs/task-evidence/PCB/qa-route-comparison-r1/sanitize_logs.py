"""Remove only known private path prefixes from completed phase text outputs."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch'])
assert (HERE / 'native-timing.json').is_file(), 'Wait for completed driver first'
rows = []
for name in ('native.txt', 'native-receipt.txt'):
    path = HERE / name
    if not path.exists():
        continue
    raw = path.read_bytes()
    safe = raw
    count = 0
    for owner, replacement in ((ROOT, b'<repo>'), (COPY, b'<owned-copy>')):
        for prefix in (str(owner), owner.as_posix()):
            token = prefix.encode('utf-8')
            count += safe.count(token)
            safe = safe.replace(token, replacement)
    path.write_bytes(safe)
    rows.append({'file': name, 'rawSha256': hashlib.sha256(raw).hexdigest(),
                 'sanitizedSha256': hashlib.sha256(safe).hexdigest(), 'knownPrefixSubstitutions': count})
(HERE / 'log-sanitization.json').write_text(json.dumps({'status': 'PASS', 'files': rows,
    'scope': 'Known repository/owned-copy prefixes only; diagnostics and outcome retained'}, indent=2) + '\n')
print('SANITIZED completed native text outputs; raw and sanitized hashes retained')
