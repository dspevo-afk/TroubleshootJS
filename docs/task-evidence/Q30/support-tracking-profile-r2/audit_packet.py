"""Read-only packet integrity, source-overlay and complete proof-parity audit.

Run: python -B audit_packet.py [--seal]
--seal refreshes only inventory.json; normal mode requires that inventory.
This is an evidence reader, not another electrical or performance campaign.
"""
from pathlib import Path
import copy
import gzip
import hashlib
import importlib.util
import json
import re
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parent
sha = lambda data: hashlib.sha256(data).hexdigest()
read = lambda p: json.loads(p.read_text())


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main():
    preservation = None
    if '--worktrees' in sys.argv:
        at = sys.argv.index('--worktrees')
        roots = sys.argv[at+1:at+3]
        assert len(roots) == 2
        initial = json.loads(gzip.decompress((ROOT/'audits/recovery-worktree-state.json.gz').read_bytes()))
        preservation = {}
        for key, folder in zip(['desktop', 'q30'], roots):
            state = initial[key]
            assert all(sha((Path(folder)/p).read_bytes()) == digest for p, digest in state['files'].items())
            status = subprocess.check_output(['git','-C',folder,'status','--short'], text=True)
            ours = {' M docs/CODEX_TASK_REPORT.md', '?? docs/task-evidence/Q30/support-tracking-profile-r2/'}
            assert set(status.splitlines()) - (ours if key == 'q30' else set()) == set(state['status'].splitlines())
            assert subprocess.check_output(['git','-C',folder,'rev-parse','HEAD'],text=True).strip() == state['head']
            preservation[key] = {'preexistingChangedFiles':len(state['files']),'bytesAndStatus':'PASS'}
    files = sorted(p for p in ROOT.rglob('*') if p.is_file() and p.name not in ['inventory.json', 'audit-result.json'])
    inventory = []
    for path in files:
        raw = path.read_bytes()
        payload = gzip.decompress(raw) if path.suffix == '.gz' else raw
        assert not re.search(rb'(?i)C:[\\/]+Users[\\/]+david', payload), path
        inventory.append({'path': path.relative_to(ROOT).as_posix(), 'bytes': len(raw), 'sha256': sha(raw)})
    if '--seal' in sys.argv:
        (ROOT/'inventory.json').write_text(json.dumps(inventory, indent=2)+'\n')
    else:
        assert inventory == read(ROOT/'inventory.json'), 'inventory changed'
    for record in read(ROOT/'provenance.json'):
        raw = (ROOT/record['stored']).read_bytes()
        payload = gzip.decompress(raw) if record['gzip'] else raw
        assert sha(payload) == record['payloadSha256'], record['stored']
    overlays = read(ROOT/'source/overlays.json')['finalOverlays']
    final = json.loads(gzip.decompress((ROOT/'source/final-inputs.json.gz').read_bytes()))
    input_map = {e['path']: e for e in final['inputs']}
    for record in overlays:
        raw = gzip.decompress((ROOT/record['stored']).read_bytes())
        assert len(raw) == record['bytes'] and sha(raw) == record['sha256']
        assert input_map[record['path']]['sha256'] == record['sha256']
    pre = json.loads(gzip.decompress((ROOT/'source/pre-fix-source-audit.json.gz').read_bytes()))
    backups = {'CirSim.java': 'CirSim.java.pre-stamp-sampling.gz',
               'Q30TrackingProfile.java': 'Q30TrackingProfile.java.pre-stamp-sampling.gz',
               'Q30CoordinatorQualificationVerifier.java': 'Q30CoordinatorQualificationVerifier.pre-lifecycle-fix.java.gz'}
    changed = []
    for old in pre['inputs']:
        if old['sha256'] != input_map[old['path']]['sha256']:
            name = backups[Path(old['path']).name]
            assert sha(gzip.decompress((ROOT/'source'/name).read_bytes())) == old['sha256']
            changed.append(old['path'])
    # The pre-fix source audit was captured after the lifecycle repair and
    # before the stamp-sampling repair. Bind the earlier verifier backup to
    # ON03's actual runtime source manifest as well.
    original = json.loads(gzip.decompress((ROOT/'runs/03-profile-on/input-manifest-before.json.gz').read_bytes()))
    original_map = {e['path']: e for e in original['files']}
    for name, stored in backups.items():
        rel = 'src/com/lushprojects/circuitjs1/client/' + name
        assert sha(gzip.decompress((ROOT/'source'/stored).read_bytes())) == original_map[rel]['sha256']
    sys.path.insert(0, str(ROOT/'procedures/readers'))
    reader = module('coordinator', ROOT/'procedures/readers/check_coordinator.py')
    pair = module('pair', ROOT/'procedures/readers/validate_current_q30_timing_pair.py')
    parity = read(ROOT/'parity.json')
    reference = None
    for row in parity['rows']:
        folder = ROOT/'runs'/row['label']
        raw = gzip.decompress((folder/'raw-report.json.gz').read_bytes())
        assert sha(raw) == row['rawReportSha256']
        report = json.loads(raw)
        report.pop('trackingProfileRequested', None)
        for phase in ['cold', 'warm']:
            report[phase].pop('trackingProfile', None)
        host = read(folder/'host-result.json')
        assert host['outcome'] == host['inputAudit']['status'] == host['cleanup']['status'] == 'PASS'
        inputs = json.loads(gzip.decompress((folder/'input-manifest-before.json.gz').read_bytes()))
        wrapper = {'rows':[{'seed':'10014','previewSourceDigest':inputs['sourceSha256'],
                            'previewWebDigest':inputs['webSha256'],'report':report}]}
        reader.check_document(wrapper, ['10014'], self_test=False)
        projection, removed = pair.remove_declared_timings(report)
        # Original Path.write_text on this Windows host stored CRLF. Preserve
        # that byte convention while also comparing the full parsed projection.
        encoded = (json.dumps(projection, sort_keys=True)+'\r\n').encode()
        assert sha(encoded) == row['projectionSha256']
        assert reference is None or projection == reference
        reference = projection
        assert removed == read(folder/'validation.json')['removedTimings']
    print(json.dumps({'status':'PASS','inventoryFiles':len(inventory), 'sourceOverlays':len(overlays),
                      'postLifecyclePreSamplingChangedSources':len(changed), 'originalOnSourceBackups':len(backups),
                      'actualQ30Reports':len(parity['rows']),
                      'fullNonTimingParity':'PASS','rawReportHashAudit':'PASS',
                      'worktreePreservation':preservation,
                      'scope':'stored artifact integrity and existing strict positive reader; no new browser or performance run'}, indent=2))


if __name__ == '__main__':
    main()
