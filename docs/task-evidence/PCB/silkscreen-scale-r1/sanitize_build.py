"""Redact known local path prefixes while retaining hashes and exact outcomes."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
log = HERE / 'build.txt'
receipt = HERE / 'build-log-sanitization.json'
assert not receipt.exists(), 'Already processed'
raw = log.read_bytes()
safe = raw
rows = []
for label, prefix in (('repo', ROOT), ('user-home', Path.home())):
    count = 0
    for text in dict.fromkeys((str(prefix), prefix.as_posix())):
        token = text.encode('utf-8')
        count += safe.count(token)
        safe = safe.replace(token, ('<' + label + '>').encode())
    rows.append({'prefixKind': label, 'replacementCount': count})
log.write_bytes(safe)
receipt.write_text(json.dumps({'status': 'KNOWN_PRIVATE_PREFIXES_REDACTED',
    'rawSha256': hashlib.sha256(raw).hexdigest(),
    'sanitizedSha256': hashlib.sha256(safe).hexdigest(), 'substitutions': rows,
    'scope': 'Only known local path prefixes; compiler diagnostics and outcomes unchanged'}, indent=2) + '\n')
print('Build log path prefixes sanitized; raw/sanitized hashes retained')
