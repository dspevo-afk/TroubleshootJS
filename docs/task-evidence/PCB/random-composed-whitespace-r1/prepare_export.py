"""Prepare a task-only pinned replay and a private maintained-verifier adapter."""
import difflib
import hashlib
import json
import runpy
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    private, = map(Path, sys.argv[1:])
    baseline = runpy.run_path(str(HERE / 'inputs.py'))
    assert baseline['record']() == baseline['read'](HERE / 'baseline-inputs.json')
    assert private.is_dir() and not (HERE / 'adapter-manifest.json').exists()
    old = HERE.parent / 'sparse-board-diagnostic-r1'
    source = (old / 'export-source/SparseNormalBoardDiagnostic.java').read_bytes()
    before = b'7141843758046216173L'
    after = b'-2750102463469069236L'
    assert source.count(before) == 1 and after not in source
    changed = source.replace(before, after)
    target = HERE / 'export-source/SparseNormalBoardDiagnostic.java'
    target.parent.mkdir()
    target.write_bytes(changed)
    # Run the exact already-published five-replacement factory, then narrow its family binding.
    factory = old / 'make_export_adapter.py'
    generated = private / 'original-two-family-adapter.ps1'
    manifest = private / 'original-adapter-manifest.json'
    subprocess.run([sys.executable, '-X', 'utf8', '-B', str(factory), '--output', str(generated),
                    '--manifest', str(manifest)], cwd=ROOT, check=True)
    raw = generated.read_bytes()
    old_binding = b"[ValidateSet('RC_DELAY','COMPOSED_CONTROLLED_INDICATOR')]"
    new_binding = b"[ValidateSet('COMPOSED_CONTROLLED_INDICATOR')]"
    assert raw.count(old_binding) == 1
    adapter = raw.replace(old_binding, new_binding)
    final = private / 'verify-random-composed.ps1'
    assert not final.exists()
    final.write_bytes(adapter)
    original = json.loads(manifest.read_text(encoding='utf-8'))
    receipt = dict(original)
    receipt.update(generatedSha256=sha(adapter), generatedBytes=len(adapter),
                   reusedFactorySha256=sha(factory.read_bytes()),
                   preRestrictionAdapterSha256=sha(raw),
                   familyRestriction={'replacements': 1, 'oldSha256': sha(old_binding),
                                      'newSha256': sha(new_binding)},
                   exporter={'publishedSha256': sha(source), 'adaptedSha256': sha(changed),
                             'changedLiteralCount': 1,
                             'oldComposedSeed': '7141843758046216173',
                             'actualFirstRandomSeed': '-2750102463469069236'},
                   limits='Private task adapter: the published five guarded hooks plus composed-only '
                          'family binding. No maintained source, native limit, assertion, construction, '
                          'admission, sampler or disposal change. Exporter changes only one pinned seed literal.')
    (HERE / 'adapter-manifest.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    for name, a, b in (
        ('adapter', (ROOT / 'scripts/verify-current-contracts.ps1').read_bytes(), adapter),
        ('exporter', source, changed),
    ):
        diff = ''.join(difflib.unified_diff(a.decode().splitlines(True), b.decode().splitlines(True),
                                          fromfile='published-' + name, tofile='task-' + name))
        (HERE / (name + '-diff.txt')).write_text(diff, encoding='utf-8', newline='\n')
    print('PASS prepared exact published factory, composed-only adapter, one pinned replay literal')


if __name__ == '__main__':
    main()
