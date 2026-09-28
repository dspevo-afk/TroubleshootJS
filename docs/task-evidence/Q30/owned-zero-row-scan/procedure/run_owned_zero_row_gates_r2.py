from pathlib import Path
import hashlib, json, subprocess, sys, time

s = Path(__file__).resolve().parent
repo = Path('[REDACTED_USER_PATH]
fixture = Path((s / 'owned-zero-row-r2-source-pair-path.txt').read_text())
target = s / 'owned-zero-row-gates-r2.json'
assert not target.exists()
receipt = {'status': 'RUNNING', 'scope': 'focused zero-row trial gates, not Q30 acceptance', 'steps': []}
started = time.monotonic()

def save():
    receipt['elapsedSeconds'] = time.monotonic() - started
    target.write_text(json.dumps(receipt, indent=2) + '\n')

def unexpected_failure(kind, error, traceback):
    receipt['status'] = 'STOPPED_GATE_OR_AUDIT_EXCEPTION'
    receipt['failure'] = str(error)
    save()
    sys.__excepthook__(kind, error, traceback)

sys.excepthook = unexpected_failure

def run(name, command):
    print('START ' + name, flush=True)
    before = time.monotonic()
    result = subprocess.run(command, check=False)
    receipt['steps'].append({'name': name, 'command': command, 'exitCode': result.returncode,
                             'elapsedSeconds': time.monotonic() - before})
    save()
    if result.returncode:
        receipt['status'] = 'STOPPED_GATE_FAILURE'
        save()
        sys.exit(result.returncode)
    print('PASS ' + name, flush=True)

save()
run('native-candidate', ['pwsh', '-NoProfile', '-File', str(s / 'native-owned-zero-row.ps1'),
    '-SourceRoot', str(fixture / 'candidate'), '-Label', 'native-owned-zero-row-candidate-r2'])
for arm in ('candidate', 'control'):
    label = 'owned-zero-row-r2-' + arm
    run(arm + '-gwt', ['pwsh', '-NoProfile', '-File', str(s / 'build-run.ps1'),
        '-SourceRoot', str(fixture / arm), '-Label', label + '-gwt'])
    run(arm + '-compiled-canaries', [sys.executable, '-B', str(s / 'run_current_solver_canaries.py'),
        str(repo), str(s), str(fixture / arm), label + '-canaries'])
    out = s / (label + '-canaries')
    host = json.loads((out / 'result.json').read_bytes())
    assert host['outcome'] == 'PASS' and len(host['cases']) == 2
    assert host['inputAudit']['status'] == 'PASS'
    assert host['cleanup']['status'] == 'PASS' and host['cleanup']['serverStopped']
    assert not host['cleanup']['errors'] and not host['cleanup']['ownedSurvivors']
    a07 = []
    for row in host['cases']:
        assert row['outcome'] == 'PASS' and row['terminalReached'] and row['stateMatch']
        assert row['reportObserved'] and row['reportJsonValid'] and row['reportMatch']
        assert not row['timedOut'] and not row['navigationError']
        for key in ('pageErrors', 'httpErrors', 'consoleErrors', 'attributeReadErrors', 'listenerCleanupErrors'):
            assert row[key] == [], (arm, key)
        raw = (out / row['reportFile']).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == row['reportSha256']
        if row['observedState'] == 'PASS:a07':
            a07.append(out / row['reportFile'])
    assert len(a07) == 1
    run(arm + '-a07-strict', ['pwsh', '-NoProfile', '-File', str(s / 'read_a07.ps1'),
        '-Repo', str(repo), '-Report', str(a07[0])])
receipt['status'] = 'FOCUSED_GATES_PASS_NOT_ACCEPTANCE'
save()
print(json.dumps(receipt), flush=True)
