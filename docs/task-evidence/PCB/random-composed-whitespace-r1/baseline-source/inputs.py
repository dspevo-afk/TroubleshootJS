"""Audit unchanged accepted production inputs before the exact random-board diagnosis."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError('Assertion-enabled Python required')
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE = '22cbf4bc06cd241999536aa92d739481f831378c'
PRIOR = HERE.parent / 'paired-packing-r2'
GATES = ('scope.md', 'board-surface-review.md', 'inputs.py')
BORROWED = (
    'sparse-board-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java',
    'sparse-board-diagnostic-r1/make_export_adapter.py',
    'sparse-board-diagnostic-r1/qualify.py',
    'sparse-board-diagnostic-r1/run_export.ps1',
    'sparse-board-diagnostic-r1/record_exit.ps1',
)
EVIDENCE = (
    'candidate-inputs.json', 'player-inputs.json', 'acceptance.json',
    'production-modules.json', 'player-baseline-execution.json',
    'post-player-provenance.json', 'player-assessment.json', 'visual-review.json',
    'random-public-identity.json', 'random-off-public.txt',
    'random-top.jpg', 'dual-top.jpg', 'dual-bottom.jpg',
    'publication.json', 'postpublication-execution.json',
) + tuple(p + '-assessment.json' for p in ('contracts', 'target', 'census', 'matrix', 'build'))


def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def record():
    assert git('branch', '--show-current').decode().strip() == 'codex/q60-faulted-profile-counts'
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], cwd=ROOT, check=True)
    accepted = read(PRIOR / 'candidate-inputs.json')
    files = {n: sha(ROOT / n) for n in accepted['files']}
    assert len(files) == 1482 and files == accepted['files']
    java = {p.relative_to(ROOT).as_posix() for f in ('src', 'tests/contracts')
            for p in (ROOT / f).rglob('*.java')}
    assert java == {n for n in files if n.endswith('.java') and n.startswith(('src/', 'tests/contracts/'))}
    paths = git('ls-files', '-z', '--', 'scripts', '*.xml', '*.jar', '.classpath', '*.properties').decode().rstrip('\0').split('\0')
    machinery = {n: sha(ROOT / n) for n in sorted(set(paths))}
    assert len(machinery) == 24 and machinery == accepted['machinery']
    assert not git('diff', BASE, '--', 'src', 'tests/contracts', 'scripts', '*.xml', '*.jar', '.classpath', '*.properties').strip()
    player = read(PRIOR / 'player-inputs.json')
    assert {n: sha(PRIOR / n) for n in accepted['gates']} == accepted['gates']
    assert {n: sha(PRIOR / n) for n in player['gates']} == player['gates']
    assert {p.relative_to(PRIOR / 'player-frozen-source').as_posix(): sha(p)
            for p in (PRIOR / 'player-frozen-source').rglob('*') if p.is_file()} == player['gates']
    for phase, digest in player['acceptedFinalGates'].items():
        p = PRIOR / (phase + '-assessment.json')
        assert sha(p) == digest and read(p)['status'] == 'PASS'
    assert sha(PRIOR / 'candidate-inputs.json') == player['candidateSealSha256']
    assert sha(PRIOR / 'production-modules.json') == player['productionModuleMapSha256']
    module_root = ROOT / 'war/circuitjs1'
    modules = read(PRIOR / 'production-modules.json')['modules']
    assert len(modules) == 5
    for m in modules:
        p = module_root / (m['strongName'] + '.cache.js')
        assert sha(p) == m['sha256'] and p.stat().st_size == m['bytes']
    assert sha(module_root / 'circuitjs1.nocache.js') == player['productionBootstrapSha256']
    expected = read(PRIOR / 'player-baseline-execution.json')
    assert expected == read(PRIOR / 'post-player-provenance.json') == read(PRIOR / 'postpublication-execution.json')
    assert read(PRIOR / 'acceptance.json')['status'] == 'PASS_FINAL_SOURCE_AND_FINITE_PLAYER'
    publication = read(PRIOR / 'publication.json')
    assert publication['commit'] == publication['exactRemoteSha'] == BASE
    assert publication['normalPushActualToolExitCode'] == 0
    assert read(PRIOR / 'player-assessment.json')['publicIdentities']['random']['replay'] == \
        'tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-2750102463469069236'
    # Consume and verify the optional source-only painter review, including its image pins.
    review = (HERE / 'board-surface-review.md').read_text(encoding='utf-8')
    import re
    pins = dict(re.findall(r'\|(src/[^|]+)\|([0-9a-f]{64})\|', review))
    assert len(pins) == 5 and {n: sha(ROOT / n) for n in pins} == pins
    images = dict(re.findall(r'- `([^`]+\.jpg)`: ([0-9a-f]{64})', review))
    assert len(images) == 3 and {n: sha(PRIOR / n) for n in images} == images
    return {
        'baseCommit': BASE, 'files': files, 'machinery': machinery,
        'gates': {n: sha(HERE / n) for n in GATES},
        'borrowedPublishedInputs': {n: sha(HERE.parent / n) for n in BORROWED},
        'acceptedEvidence': {n: sha(PRIOR / n) for n in EVIDENCE},
        'acceptedNativeGates': accepted['gates'], 'acceptedPlayerHelpers': player['gates'],
        'expectedExecution': expected, 'surfaceReviewSources': pins, 'reviewedImages': images,
        'request': 'tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-2750102463469069236',
        'reuse': 'Unchanged accepted matrix/build/player only; no fresh matrix/build/browser claim. '
                'Snapshot maps plus published-base ancestry replace old prepublication HEAD-equality helpers.',
    }


def main():
    command, = sys.argv[1:]
    assert command in ('freeze', 'audit')
    if command == 'freeze':
        assert git('rev-parse', 'HEAD').decode().strip() == BASE
    value = record()
    target = HERE / 'baseline-inputs.json'
    archive = HERE / 'baseline-source'
    if command == 'freeze':
        assert not target.exists() and not archive.exists(), 'Preserve prior baseline'
        archive.mkdir()
        for n in GATES:
            (archive / n).write_bytes((HERE / n).read_bytes())
        with target.open('x', encoding='utf-8', newline='\n') as f:
            json.dump(value, f, indent=2)
            f.write('\n')
    assert value == read(target)
    assert {p.name: sha(p) for p in archive.iterdir()} == value['gates']
    print('PASS exact random diagnosis published source1482 machinery24 native13 player10 modules5 review5/images3')


if __name__ == '__main__':
    main()
