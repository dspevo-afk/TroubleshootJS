"""Freeze the published rejection source/evidence before the two-arm private span probe."""
import hashlib
import json
import runpy
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / 'composed-rejection-diagnostic-r1'
BASE = '113456fc2d781411f9a3e94bfca4b676fee883b6'
GATES = ('scope.md', 'inputs.py', 'movement-review.json')


def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def record():
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], cwd=ROOT, check=True)
    assert subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT).decode().strip() == 'codex/q60-faulted-profile-counts'
    previous = runpy.run_path(str(PRIOR / 'export_inputs.py'))
    private_archive = PRIOR / 'export-frozen-source/private-inputs'
    assert previous['record'](private_archive) == read(PRIOR / 'export-inputs.json')
    acceptance = read(PRIOR / 'acceptance.json')
    assert acceptance['status'] == 'PASS_FINITE_PRIVATE_REJECTION_ATTRIBUTION_ONLY'
    assert {n: sha(PRIOR / n) for n in acceptance['filesBeforeAcceptance']} == acceptance['filesBeforeAcceptance']
    assert read(PRIOR / 'publication.json')['exactRemoteSha'] == BASE
    old_baseline = read(PRIOR / 'baseline-inputs.json')
    assert read(PRIOR / 'postpublication-execution.json') == old_baseline['expectedExecution']
    review = read(HERE / 'movement-review.json')
    assert review['status'] == 'SOURCE_AND_OBSERVED_GEOMETRY_ONLY_NO_IMPLEMENTATION_GO'
    assert {n: sha(ROOT / n) for n in review['consumedSources']} == review['consumedSources']
    assert review['consumedReceiptSha256'] == sha(PRIOR / 'composed/receipt.txt')
    return {'baseCommit': BASE, 'files': old_baseline['files'], 'machinery': old_baseline['machinery'],
        'expectedExecution': old_baseline['expectedExecution'], 'request': old_baseline['request'],
        'gates': {n: sha(HERE / n) for n in GATES},
        'publishedEvidence': {n: sha(PRIOR / n) for n in ('baseline-inputs.json', 'export-inputs.json',
            'acceptance.json', 'assessment.json', 'composed/receipt.txt', 'publication.json',
            'postpublication-execution.json', 'qualify.py', 'record_exit.ps1', 'observe_execution.ps1')},
        'borrowedPrivateSources': {n: sha(private_archive / n) for n in
            ('SeededPcbLayoutGenerator.java', 'SparseNormalBoardDiagnostic.java', 'verify-composed-rejection.ps1')},
        'reuse': 'Accepted frozen private inputs equal the actual previously consumed hashes; full published production/evidence boundaries audited through unchanged snapshot maps/ancestry. No fresh production matrix/build/player claim.'}


def main():
    command, = sys.argv[1:]
    assert command in ('freeze', 'audit')
    if command == 'freeze':
        assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip() == BASE
    value = record()
    target, archive = HERE / 'baseline-inputs.json', HERE / 'baseline-source'
    if command == 'freeze':
        assert not target.exists() and not archive.exists()
        archive.mkdir()
        for n in GATES:
            (archive / n).write_bytes((HERE / n).read_bytes())
        with target.open('x', encoding='utf-8', newline='\n') as f:
            json.dump(value, f, indent=2)
            f.write('\n')
    assert value == read(target)
    assert {p.name: sha(p) for p in archive.iterdir()} == value['gates']
    print('PASS two-pad span baseline published source1482 machinery24 accepted rejection and movement review')


if __name__ == '__main__':
    main()
