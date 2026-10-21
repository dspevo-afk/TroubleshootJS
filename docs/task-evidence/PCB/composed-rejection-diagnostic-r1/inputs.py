"""Freeze the accepted exact-board observation before private rejection instrumentation."""
import hashlib
import json
import runpy
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / 'random-composed-whitespace-r1'
BASE = '6c583204af0897071514304b886c9dfb3eb1562b'
GATES = ('scope.md', 'inputs.py', 'source-advice.json')


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def record(previous_adapter: Path):
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], cwd=ROOT, check=True)
    assert subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT).decode().strip() == 'codex/q60-faulted-profile-counts'
    old = runpy.run_path(str(PRIOR / 'export_inputs.py'))
    assert old['record'](previous_adapter) == read(PRIOR / 'export-inputs.json')
    accepted = read(PRIOR / 'acceptance.json')
    assert accepted['status'] == 'PASS_FINITE_EXACT_RANDOM_DIAGNOSIS_ONLY'
    assert {n: sha(PRIOR / n) for n in accepted['filesBeforeAcceptance']} == accepted['filesBeforeAcceptance']
    assert read(PRIOR / 'publication.json')['exactRemoteSha'] == BASE
    assert read(PRIOR / 'postpublication-execution.json') == read(PRIOR / 'execution-after.json')
    advice = read(HERE / 'source-advice.json')
    assert advice['status'] == 'SOURCE_ONLY_NO_ACTUAL_CAUSE_MEASURED'
    assert {n: sha(ROOT / n) for n in advice['consumedSources']} == advice['consumedSources']
    assert advice['consumedReceiptSha256'] == sha(PRIOR / 'composed/receipt.txt')
    previous = read(PRIOR / 'baseline-inputs.json')
    return {'baseCommit': BASE, 'files': previous['files'], 'machinery': previous['machinery'],
        'previousAdapterSha256': sha(previous_adapter), 'expectedExecution': previous['expectedExecution'],
        'request': previous['request'], 'gates': {n: sha(HERE / n) for n in GATES},
        'publishedEvidence': {n: sha(PRIOR / n) for n in (
            'baseline-inputs.json', 'export-inputs.json', 'acceptance.json', 'assessment.json',
            'geometry-summary.json', 'composed/receipt.txt', 'publication.json',
            'postpublication-execution.json', 'export-source/SparseNormalBoardDiagnostic.java',
            'qualify.py', 'run_export.ps1', 'record_exit.ps1')},
        'publishedPrivateSubstitutionFactory': sha(HERE.parent / 'paired-diversity-diagnostic-r1/make_diagnostic.py'),
        'reuse': 'Previous exact source/native/player/build boundaries audited through their published snapshot maps and ancestry; no fresh production matrix/build/player claim.'}


def main():
    command, adapter = sys.argv[1:]
    assert command in ('freeze', 'audit')
    if command == 'freeze':
        assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip() == BASE
    value = record(Path(adapter))
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
    print('PASS composed rejection baseline source1482 machinery24 exact published observation and advice')


if __name__ == '__main__':
    main()
