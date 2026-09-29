"""Archive explicit task evidence after the frozen sequence; no production writes."""
from pathlib import Path
import gzip
import hashlib
import json
import re

REC = Path(__file__).resolve().parent
paths = json.loads((REC / 'paths.json').read_text())
TASK = Path(paths['scratch'])
REPO = Path(paths['repo'])
OUT = REPO / 'docs/task-evidence/Q30/support-cost-isolation-r1'
assert not OUT.exists(), 'Use a deliberate update after first packaging, never overwrite silently'
plan = json.loads((REC / 'plan.json').read_text())
assert all((REC / (r[0] + '-validation.json')).exists() for r in plan['sequence'])
OUT.mkdir()
provenance = []
sha = lambda b: hashlib.sha256(b).hexdigest()

def sanitized(raw):
    text = raw.decode('utf-8-sig')
    substitutions = [(str(TASK), '<TASK_TEMP>'), (str(REC), '<RECOVERY>'),
                     (str(REPO), '<Q30_REPO>')]
    for path, token in substitutions:
        for spelling in (path, path.replace('\\', '/'), path.replace('\\', '\\\\')):
            text = text.replace(spelling, token)
    # Also sanitize redirected Store-app spellings and command paths.
    text = re.sub(r'(?i)C:(?:\\{1,2}|/)Users(?:\\{1,2}|/)david', '<USERPROFILE>', text)
    return text.encode('utf-8')

def keep(source, target, compress=False, exact=False):
    raw = source.read_bytes()
    payload = raw if exact else sanitized(raw)
    assert not re.search(rb'(?i)C:[\\/]+Users[\\/]+david', payload), source.name
    dst = OUT / target
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(gzip.compress(payload, mtime=0) if compress else payload)
    provenance.append({'source': source.name, 'stored': target,
        'gzip': compress, 'sourceSha256': sha(raw), 'payloadSha256': sha(payload),
        'pathSanitized': raw != payload, 'exactPayload': raw == payload})

def put(target, value):
    dst = OUT / target
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes((json.dumps(value, indent=2) + '\n').encode())

keep(REC/'plan.json', 'plan.json', exact=True)
keep(REC/'analysis.json', 'analysis.json', exact=True)
keep(REC/'timings.csv', 'timings.csv', exact=True)
keep(REC/'gates.json', 'gates.json', exact=True)
keep(REC/'README.md', 'README.md', exact=True)
keep(REC/'audit_packet.py', 'audit_packet.py', exact=True)
keep(REC/'final-input-audit.json', 'audits/final-input-audit.json', exact=True)
keep(REC/'initial-state.json', 'audits/initial-worktrees.json.gz', compress=True)
keep(TASK/'baseline-source-audit.json', 'source/baseline-inputs.json.gz', compress=True)
for rev in ('diagnostic-r1', 'diagnostic-r2', 'diagnostic-r3'):
    audit = REC / (rev + '-source-audit.json')
    keep(audit, 'source/' + rev + '-inputs.json.gz', compress=True)
    for src in sorted((REC/'source'/rev).glob('*.gz')):
        # Already compressed exact source bytes; do not transcode.
        target = OUT/'source'/rev/src.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(src.read_bytes())
        raw = gzip.decompress(src.read_bytes())
        assert not re.search(rb'(?i)C:[\\/]+Users[\\/]+david', raw)
        provenance.append({'source': rev+'/'+src.name, 'stored': target.relative_to(OUT).as_posix(),
            'gzip': True, 'sourceSha256': sha(raw), 'payloadSha256': sha(raw),
            'pathSanitized': False, 'exactPayload': True})

rows = []
for label, arm, mode, flag in plan['sequence']:
    folder = 'runs/' + label + '/'
    for suffix in ('summary.json', 'validation.json', 'host-result.json', 'spec.json'):
        keep(REC/(label+'-'+suffix), folder+suffix)
    for suffix in ('raw-report.json', 'proof-projection.json'):
        keep(REC/(label+'-'+suffix), folder+suffix+'.gz', compress=True, exact=True)
    if arm == 'diagnostic':
        keep(REC/(label+'-metadata-reader.json'), folder+'metadata-reader.jsonl')
    for src in sorted((TASK/label).glob('*.json')):
        if src.name in ('result.json', 'spec.json'):
            continue
        keep(src, folder+'host/'+src.name+'.gz', compress=True)
    # Preserve selected workload/clock observations, not unrelated full process lists.
    endpoints = {phase: json.loads((REC/(label+'-host-'+phase+'.json')).read_text())
                 for phase in ('before', 'after')}
    put(folder+'host-observations.json', {phase: {'utc': data['utc'],
        'processors': data['processors']} for phase, data in endpoints.items()})
    validation = json.loads((REC/(label+'-validation.json')).read_text())
    rows.append({key: validation[key] for key in
        ('label', 'rawReportSha256', 'projectionSha256', 'removedMetadata')})
put('parity.json', {'rows': rows, 'scope': 'Complete proof after named metadata and declared timing fields only'})

for name in ('control-canaries', 'diagnostic-canaries'):
    for src in sorted((TASK/name).glob('*.json')):
        keep(src, 'gates/'+name+'/'+src.name+'.gz', compress=True)
    for src in sorted((TASK/name/'cases').glob('*')):
        if src.is_file():
            keep(src, 'gates/'+name+'/cases/'+src.name+'.gz', compress=True,
                 exact=src.name.endswith('.report.json'))
for name in ('control-build-result.json', 'control-build.log',
             'diagnostic-r2-native-result.json', 'diagnostic-r2-native.log',
             'diagnostic-r3-native-result.json', 'diagnostic-r3-native.log',
             'diagnostic-r3-native-receipt.txt', 'diagnostic-r3-build-result.json',
             'diagnostic-r3-build.log', 'control-a07-reader.txt', 'diagnostic-a07-reader.txt'):
    keep(REC/name, 'gates/'+name+('.gz' if name.endswith('.log') else ''),
         compress=name.endswith('.log'))
for name in ('measure.py', 'run_sequence.py', 'validate_measurement.py',
             'validate_profile.py', 'analyze_sequence.py', 'audit_sources.py', 'package_evidence.py'):
    keep(REC/name, 'procedures/'+name)
for name in ('check_coordinator.py', 'check_d01.py', 'validate_current_q30_timing_pair.py'):
    keep(REPO/'docs/task-evidence/Q30/support-tracking-profile-r2/procedures/readers'/name,
         'procedures/readers/'+name, exact=True)
keep(REPO/'tests/browser/compiled_attribute_acceptance.py',
     'procedures/compiled_attribute_acceptance.py', exact=True)
for src in sorted(REC.glob('sequence-*.log')):
    keep(src, 'procedures/'+src.name)
put('procedures/paths.template.json', {key: '<'+key.upper()+'>' for key in paths})
put('provenance.json', provenance)
(OUT/'.gitattributes').write_bytes(b'* -text\n')
print(json.dumps({'output': str(OUT), 'storedRecords': len(provenance)}))
