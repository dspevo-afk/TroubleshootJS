"""Seal actual private native inputs and audit the unchanged published production boundary."""
import json
import runpy
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / 'random-composed-whitespace-r1'
UTIL = runpy.run_path(str(HERE / 'inputs.py'))
read, sha = UTIL['read'], UTIL['sha']
GATES = ('prepare_diagnostic.py', 'observer-source/TaskPackingObservation.java.inc',
    'private-inputs.json', 'generator-diff.txt', 'adapter-diff.txt', 'source-review.json',
    'export_inputs.py', 'run_export.ps1', 'record_exit.ps1', 'observe_execution.ps1',
    'qualify.py', 'reader-scope.md', 'reader-preseal-review.json')


def record(private_root: Path):
    baseline = read(HERE / 'baseline-inputs.json')
    subprocess.run(['git', 'merge-base', '--is-ancestor', baseline['baseCommit'], 'HEAD'], cwd=ROOT, check=True)
    assert subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT).decode().strip() == 'codex/q60-faulted-profile-counts'
    # The old baseline audit has no private-path dependency and validates the full accepted R2 boundary.
    old = runpy.run_path(str(PRIOR / 'inputs.py'))
    published = old['record']()
    assert published == read(PRIOR / 'baseline-inputs.json')
    assert baseline['files'] == published['files'] and baseline['machinery'] == published['machinery']
    assert len(baseline['files']) == 1482 and len(baseline['machinery']) == 24
    assert {n: sha(HERE / n) for n in baseline['gates']} == baseline['gates']
    assert {p.name: sha(p) for p in (HERE / 'baseline-source').iterdir()} == baseline['gates']
    assert {n: sha(PRIOR / n) for n in baseline['publishedEvidence']} == baseline['publishedEvidence']
    accepted = read(PRIOR / 'acceptance.json')
    assert {n: sha(PRIOR / n) for n in accepted['filesBeforeAcceptance']} == accepted['filesBeforeAcceptance']
    old_seal = read(PRIOR / 'export-inputs.json')
    assert {n: sha(PRIOR / n) for n in old_seal['gates']} == old_seal['gates']
    assert {p.relative_to(PRIOR / 'export-frozen-source').as_posix(): sha(p)
            for p in (PRIOR / 'export-frozen-source').rglob('*') if p.is_file()} == old_seal['gates']
    manifest = read(HERE / 'private-inputs.json')
    assert manifest['previousAdapterSha256'] == baseline['previousAdapterSha256'] == old_seal['adapterSha256']
    assert manifest['publishedGeneratorSha256'] == baseline['files'][
        'src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java']
    assert {n: sha(private_root / n) for n in manifest['privateInputs']} == manifest['privateInputs']
    assert len(manifest['privateInputs']) == 3 and len(manifest['generatorChanges']) == 5 and len(manifest['adapterChanges']) == 3
    assert all(c['replacements'] == 1 for c in manifest['generatorChanges'] + manifest['adapterChanges'])
    assert read(HERE / 'source-review.json')['status'] == 'PASS_PASSIVE_PRIVATE_SOURCE_ONLY'
    assert read(HERE / 'source-review.json')['privateInputManifestSha256'] == sha(HERE / 'private-inputs.json')
    return {'baseCommit': baseline['baseCommit'], 'baselineSealSha256': sha(HERE / 'baseline-inputs.json'),
        'request': baseline['request'], 'privateInputs': manifest['privateInputs'],
        'gates': {n: sha(HERE / n) for n in GATES}}


def main():
    command, private_root = sys.argv[1:]
    assert command in ('freeze', 'audit')
    value = record(Path(private_root))
    target, archive = HERE / 'export-inputs.json', HERE / 'export-frozen-source'
    if command == 'freeze':
        assert not target.exists() and not archive.exists()
        archive.mkdir()
        for n in GATES:
            p = archive / n
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes((HERE / n).read_bytes())
        for n in value['privateInputs']:
            p = archive / 'private-inputs' / n
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes((Path(private_root) / n).read_bytes())
        with target.open('x', encoding='utf-8', newline='\n') as f:
            json.dump(value, f, indent=2)
            f.write('\n')
    assert value == read(target)
    expected = dict(value['gates'], **{'private-inputs/' + n: h for n, h in value['privateInputs'].items()})
    assert {p.relative_to(archive).as_posix(): sha(p) for p in archive.rglob('*') if p.is_file()} == expected
    print('PASS composed rejection source1482 machinery24 frozen private inputs')


if __name__ == '__main__':
    main()
