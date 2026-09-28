from pathlib import Path
s=Path(__file__).resolve().parent
text=(s/'run_owned_zero_row_gates_r2.py').read_text(encoding='utf-8')
text=text.replace("owned-zero-row-r2-source-pair-path.txt","sticky-finite-r2-source-pair-path.txt")
text=text.replace("owned-zero-row-gates-r2.json","sticky-finite-r2-gates.json")
text=text.replace("focused zero-row trial gates","focused sticky-finite solve trial gates")
text=text.replace("'native-owned-zero-row-candidate-r2'","'native-sticky-finite-r2-candidate'")
text=text.replace("'owned-zero-row-r2-'","'sticky-finite-r2-'")
text=text.replace("started = time.monotonic()", """audit_raw = (fixture / 'source-audit.json').read_bytes()
audit = json.loads(audit_raw)
receipt['sourceAuditSha256'] = hashlib.sha256(audit_raw).hexdigest()
receipt['planSha256'] = hashlib.sha256((s / 'sticky-finite-r2-measurement-plan.json').read_bytes()).hexdigest()
assert receipt['planSha256'] == '727f2e9f4a63a74b0846ac242c1a2cd34f40f9f3b8bf8aed2b7f6cbb6e3ab0a9'
def unchanged():
    for arm, entries in audit['armInputs'].items():
        for e in entries:
            raw = (fixture / arm / e['path']).read_bytes()
            assert hashlib.sha256(raw).hexdigest() == e['sha256'] and len(raw) == e['bytes'], (arm,e['path'])
started = time.monotonic()""",1)
text=text.replace("    print('START ' + name, flush=True)","    unchanged()\n    print('START ' + name, flush=True)")
text=text.replace("    print('PASS ' + name, flush=True)","    unchanged()\n    print('PASS ' + name, flush=True)")
target=s/'run_sticky_finite_r2_gates.py'
assert not target.exists()
target.write_text(text,encoding='utf-8',newline='\n')
print(target)

