"""Seal task helpers after the unchanged production baseline and minimal adaptation review."""
import json
import runpy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BASELINE = runpy.run_path(str(HERE / 'inputs.py'))
read, sha = BASELINE['read'], BASELINE['sha']
GATES = (
    'prepare_export.py', 'adapter-manifest.json', 'adapter-diff.txt', 'exporter-diff.txt',
    'export_inputs.py', 'run_export.ps1', 'record_exit.ps1', 'observe_execution.ps1',
    'qualify.py', 'reader-scope.md', 'export-source/SparseNormalBoardDiagnostic.java',
    'source-review.json', 'reader-preseal-review.json',
    'preseal-preparation-note.json', 'summarize_geometry.py',
)


def record(adapter: Path):
    baseline = BASELINE['record']()
    assert baseline == read(HERE / 'baseline-inputs.json')
    manifest = read(HERE / 'adapter-manifest.json')
    assert sha(adapter) == manifest['generatedSha256']
    assert manifest['maintainedSourceSha256'] == baseline['machinery']['scripts/verify-current-contracts.ps1']
    assert len(manifest['changes']) == 5 and all(c['replacements'] == 1 for c in manifest['changes'])
    assert manifest['familyRestriction']['replacements'] == 1
    assert manifest['reusedFactorySha256'] == baseline['borrowedPublishedInputs'][
        'sparse-board-diagnostic-r1/make_export_adapter.py']
    original = (HERE.parent / 'sparse-board-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java').read_bytes()
    changed = (HERE / 'export-source/SparseNormalBoardDiagnostic.java').read_bytes()
    assert original.count(b'7141843758046216173L') == 1
    assert original.replace(b'7141843758046216173L', b'-2750102463469069236L') == changed
    assert sha(HERE / 'export-source/SparseNormalBoardDiagnostic.java') == manifest['exporter']['adaptedSha256']
    review = read(HERE / 'source-review.json')
    assert review['status'] == 'PASS_TASK_ONLY_ADAPTATION_SOURCE_REVIEW'
    assert review['adapterManifestSha256'] == sha(HERE / 'adapter-manifest.json')
    assert review['exporterSha256'] == manifest['exporter']['adaptedSha256']
    return {'baselineSealSha256': sha(HERE / 'baseline-inputs.json'),
            'baseCommit': baseline['baseCommit'], 'request': baseline['request'],
            'adapterSha256': manifest['generatedSha256'],
            'gates': {n: sha(HERE / n) for n in GATES}}


def main():
    command, adapter = sys.argv[1:]
    assert command in ('freeze', 'audit')
    value = record(Path(adapter))
    target, archive = HERE / 'export-inputs.json', HERE / 'export-frozen-source'
    if command == 'freeze':
        assert not target.exists() and not archive.exists(), 'Preserve prior export seal'
        archive.mkdir()
        for n in GATES:
            p = archive / n
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes((HERE / n).read_bytes())
        with target.open('x', encoding='utf-8', newline='\n') as f:
            json.dump(value, f, indent=2)
            f.write('\n')
    assert value == read(target)
    assert {p.relative_to(archive).as_posix(): sha(p) for p in archive.rglob('*') if p.is_file()} == value['gates']
    print('PASS exact random export source1482 machinery24 frozen helpers')


if __name__ == '__main__':
    main()
