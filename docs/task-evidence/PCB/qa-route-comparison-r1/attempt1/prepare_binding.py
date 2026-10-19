"""Read the exact accepted routed case; reject copied or rounded seed transport."""
import hashlib
import json
from pathlib import Path
HERE = Path(__file__).resolve().parent
prior = HERE.parent / 'pocket-decision-cause-r1/accepted15-cases.json'
row, = [r for r in json.loads(prior.read_text())['cases'] if r['seed'] == '42']
assert row['layoutSeed'] == '5526715536693200536'
assert row['routingSeed'] == '4493419427463069983'
assert row['selectedAttempt'] == 1 and row['version'] == 15 and row['density'] == 14
(HERE / 'accepted-case.json').write_text(json.dumps({'source': 'pocket-decision-cause-r1/accepted15-cases.json',
    'sourceSha256': hashlib.sha256(prior.read_bytes()).hexdigest(), 'case': row}, indent=2) + '\n')
note = {'status': 'CORRECTED_BEFORE_INSTRUMENTATION_OR_RUNTIME',
    'field': 'routingSeed', 'incorrectPriorSummaryCopy': '4493419427469983',
    'exactImmutableAcceptedValue': row['routingSeed'], 'fixtureSource': 'accepted-case.json',
    'rootOrAttemptSubstitution': False, 'claim': 'Preparation metadata correction only; no route experiment ran'}
(HERE / 'seed-copy-correction.json').write_text(json.dumps(note, indent=2) + '\n')
print('PINNED acceptedroot42 exact signed-long layout/routing streams and routed metrics')
