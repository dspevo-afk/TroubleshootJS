"""Add the omitted compile-only shared fixture; retain two runtime suites."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE / 'local-state.json').read_text())['scratch'])
registry = COPY / 'scripts/verify-current-contracts.ps1'
old = registry.read_bytes()
failed = json.loads((HERE / 'attempt1/native-inputs.json').read_text())['files']
expected, = [r['sha256'] for r in failed if r['path'] == 'copy/scripts/verify-current-contracts.ps1']
assert hashlib.sha256(old).hexdigest() == expected
anchor = b"    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/P03StructuralFixtures.java')"
addition = b"    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/PcbQaRouteComparisonContractTest.java')"
assert old.count(anchor) == 1 and addition not in old
eol = b'\r\n' if b'\r\n' in old else b'\n'
new = old.replace(anchor, addition + eol + anchor)
registry.write_bytes(new)
assert (HERE / 'native-inputs.json').read_bytes() == (HERE / 'attempt1/native-inputs.json').read_bytes()
(HERE / 'native-inputs.json').unlink()
receipt = {'status': 'SOURCE_LIST_REPAIR', 'failedPhase': 'javac production/contract compilation',
    'failedElapsedSeconds': 35.285166, 'failedOriginalOrTranslatedRoutesExecuted': False,
    'oldRegistrySha256': expected, 'newRegistrySha256': hashlib.sha256(new).hexdigest(),
    'change': 'One shared fixture added to existing explicit compile-only source list; two runtime suites unchanged',
    'generatorOrFixtureChanged': False, 'budgetsOrAssertionsRelaxed': False,
    'failedAttemptPreserved': 'attempt1/'}
(HERE / 'registration-repair.json').write_text(json.dumps(receipt, indent=2) + '\n')
print('PASS exact one-source compile registration repair; failed seal preserved in attempt1')
