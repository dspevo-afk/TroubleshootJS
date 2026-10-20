"""Freeze exact old COPY or final candidate compiler inputs and execution gates."""
import hashlib
import json
import stat
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE_SEAL = ROOT / 'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
BASE_SHA = '9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
BASE_COMMIT = '2a73b949a02de718bd968c2920ba6045d7406c5a'
GATES = ('scope.md', 'protocol.json', 'preparation-audit.json', 'inputs.py',
         'routes-suites.json', 'unit-suites.json', 'run_native.ps1',
         'check_process_cleanup.ps1', 'sanitize.py', 'suite_transport.ps1',
         'check_suite_transport.ps1', 'suite-transport-canaries.json',
         'assess_normal.py', 'check_normal_assessment.py',
         'assessment-canary-results.json')


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def safe_file(root, relative):
    path = root / relative
    assert path.is_file() and path.resolve().is_relative_to(root.resolve()), relative
    for entry in (path, *path.parents):
        assert not entry.is_symlink(), relative
        assert not entry.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT, relative
        if entry == root:
            break
    return path


def files_for(phase):
    assert sha(BASE_SEAL) == BASE_SHA
    base = {r['path']: r['sha256'] for r in read(BASE_SEAL)['files']
            if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
    assert len(base) == 1467
    additions = {'tests/contracts/' + name + '.java'
                 for name, marker in read(HERE / 'routes-suites.json')}
    assert len(additions) == 8 and not additions & set(base)
    if phase == 'candidate':
        additions |= {'tests/contracts/' + name + '.java'
                      for name, marker in read(HERE / 'unit-suites.json')}
        root = ROOT
    else:
        root = Path(read(HERE / 'baseline-local-state.json')['scratch']).resolve()
        assert root.name.startswith('tsj-normal-packing-baseline-')
    actual = {n: sha(safe_file(root, n)) for n in sorted(set(base) | additions)}
    if phase == 'baseline':
        found = {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()}
        assert found == set(actual), 'Unexpected or missing baseline COPY input'
        changed = {n for n in base if base[n] != actual[n]}
        assert changed == {'scripts/verify-current-contracts.ps1'}, changed
        registry = (root / 'scripts/verify-current-contracts.ps1').read_bytes()
        kept = b''.join(line for line in registry.splitlines(keepends=True)
                        if b"Name = 'PcbNormalPackingCensusContractTest" not in line)
        assert hashlib.sha256(kept).hexdigest() == base['scripts/verify-current-contracts.ps1'], \
            'Baseline registry changes exceed eight census entries'
    else:
        expected_java = {n for n in actual if n.endswith('.java') and
                         n.startswith(('src/', 'tests/contracts/'))}
        found_java = {p.relative_to(root).as_posix() for directory in ('src', 'tests/contracts')
                      for p in (root / directory).rglob('*.java')}
        assert found_java == expected_java, 'Compiler Java inventory differs from sealed inputs'
    return root, base, additions, actual


def main():
    assert len(sys.argv) == 2
    action, phase = sys.argv[1].split('-', 1)
    assert action in ('freeze', 'audit') and phase in ('baseline', 'candidate')
    root, base, additions, files = files_for(phase)
    gates = {name: sha(HERE / name) for name in GATES}
    reader = read(HERE / 'assessment-canary-results.json')
    transport = read(HERE / 'suite-transport-canaries.json')
    assert reader['status'] == 'PASS' and reader['negativeCount'] >= 40
    assert transport['status'] == 'PASS' and transport['caseCount'] == 12
    for result in (reader, transport):
        for name, digest in result['consumedSha256'].items():
            actual = files['tests/contracts/PcbNormalPackingCensusContractTest.java'] \
                if name == 'sourceHelper.java' else sha(HERE / name)
            assert actual == digest, 'Canary input changed: ' + name
    record = {'schema': 1, 'phase': phase, 'baseCommit': BASE_COMMIT,
              'protocolSha256': sha(HERE / 'protocol.json'),
              'baseMainSealSha256': BASE_SHA, 'sourceCount': len(files),
              'files': [{'path': n, 'sha256': digest} for n, digest in files.items()],
              'gateOwners': gates}
    seal = HERE / (phase + '-inputs.json')
    archive = HERE / (phase + '-source')
    delta = {n for n in files if n not in base or files[n] != base[n]}
    if action == 'freeze':
        assert not seal.exists() and not archive.exists(), 'Preserve previous frozen attempt first'
        for n in sorted(delta):
            dest = archive / n
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes((root / n).read_bytes())
        seal.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    assert read(seal) == record, 'Frozen source or execution gate changed'
    archived = {p.relative_to(archive).as_posix(): sha(p)
                for p in archive.rglob('*') if p.is_file()}
    assert archived == {n: files[n] for n in sorted(delta)}, 'Source delta archive mismatch'
    print('PASS ' + action + ' ' + phase + ' exact sources=' + str(len(files)) +
          ' deltas=' + str(len(delta)) + ' gates=' + str(len(gates)))


if __name__ == '__main__':
    main()
