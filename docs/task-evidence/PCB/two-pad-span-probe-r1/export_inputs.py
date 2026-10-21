"""Seal the two private source arms and audit the unchanged accepted production boundary."""
import json
import runpy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
UTIL = runpy.run_path(str(HERE / 'inputs.py'))
read, sha = UTIL['read'], UTIL['sha']
GATES = ('prepare_probe.py', 'span_guard_patch.py', 'span-guard-notes.md',
    'observer-source/TaskPackingSpanObservation.java.inc', 'private-inputs.json',
    'control-generator-diff.txt', 'variant-generator-diff.txt', 'variant-planner-diff.txt',
    'variant-compactor-diff.txt', 'adapter-diff.txt', 'export_inputs.py', 'run_probe.ps1',
    'record_exit.ps1', 'observe_execution.ps1', 'source-review.json',
    'qualify.py', 'reader-scope.md', 'reader-preseal-review.json')


def record(private_root: Path):
    baseline = UTIL['record']()
    assert baseline == read(HERE / 'baseline-inputs.json')
    assert {p.name: sha(p) for p in (HERE / 'baseline-source').iterdir()} == baseline['gates']
    manifest = read(HERE / 'private-inputs.json')
    assert manifest['baseCommit'] == baseline['baseCommit']
    assert manifest['borrowedPrivateSources'] == baseline['borrowedPrivateSources']
    assert manifest['candidateInterpretation'] == 'PRIVATE_TWO_PAD_SPAN_FILTER@1;NOT_PRODUCTION_MODEL'
    assert len(manifest['privateInputs']) == 8
    assert {n: sha(private_root / n) for n in manifest['privateInputs']} == manifest['privateInputs']
    assert len(manifest['factoryChanges']) == 8 and len(manifest['guardChanges']) == 6
    assert all(c['replacements'] == 1 for c in manifest['factoryChanges'] + manifest['guardChanges'])
    review = read(HERE / 'source-review.json')
    assert review['status'] == 'PASS_PRIVATE_SPAN_PROBE_SOURCE_ONLY'
    assert review['privateInputManifestSha256'] == sha(HERE / 'private-inputs.json')
    assert review['privateInputs'] == manifest['privateInputs']
    return {'baseCommit': baseline['baseCommit'], 'baselineSealSha256': sha(HERE / 'baseline-inputs.json'),
        'request': baseline['request'], 'privateInputs': manifest['privateInputs'],
        'candidateInterpretation': manifest['candidateInterpretation'],
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
    print('PASS two-pad span source1482 machinery24 frozen control and variant inputs')


if __name__ == '__main__':
    main()
