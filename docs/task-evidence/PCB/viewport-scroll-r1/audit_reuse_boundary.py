"""Audit unchanged JVM inputs and reconstruct the accepted compiled-web boundary.

UI behavior needs fresh DOM/browser proof; this audit cannot certify it.
"""
import hashlib
import importlib.util
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent/'canvas-owner-fit-r1'
EXPECTED_WEB = '4111c91583c55ec34abb372b220ef1b4f6de3c7d02dfe098c152888aa8f48503'
NODE_FIXTURE = 'tests/contracts/u04_ui_contract.mjs'
ORIGINALS = {
    'tsj-workbench-ui.css': '990d638e9b706d7233a33b852fea627b015236b810460da578a08a6eb9d91f4b',
    'tsj-workbench-ui.js': '8ddb7c76013ec3ab25b86ad18aec27445dde2d6738155dc63a37f638f3e03d3a',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    prior = json.loads((PRIOR/'final-inputs.json').read_text(encoding='utf-8-sig'))
    collector_path = HERE.parent/'pad-cost-vacancy-r1/input_snapshot.py'
    spec = importlib.util.spec_from_file_location('input_snapshot', collector_path)
    collector = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(collector)
    actual = {row['path']:row['sha256'] for row in collector.collect(ROOT)}
    require(set(actual) == {row['path'] for row in prior['files']},
            'Source/script/contract/jar inventory added or removed')
    changed = []
    for row in prior['files']:
        if actual[row['path']] != row['sha256']:
            changed.append(row['path'])
    require(changed == [NODE_FIXTURE], 'Unexpected JVM/source/script/jar or superset change: '+str(changed))
    old_fixture = next(row for row in prior['files'] if row['path'] == NODE_FIXTURE)
    require(old_fixture['sha256'] ==
            '97313996bd1f2c64c0bc60f1476066e1ce5ccb251aea290f4e813f118c680cb1',
            'Original Node-only fixture identity changed')
    verifier = (ROOT/'scripts/verify-current-contracts.ps1').read_text(encoding='utf-8-sig')
    require("-Filter '*.java'" in verifier and "('tests/contracts/' + $testClass + '.java')" in verifier
            and '.mjs' not in verifier, 'Maintained selected JVM input selection changed')
    old_native = json.loads((PRIOR/'final-native-timing.json').read_text(encoding='utf-8-sig'))
    require(old_native['status'] == 'PASS' and old_native['suiteCount'] == 56,
            'Accepted selected56 receipt missing')

    war = ROOT/'war'
    files = [p for p in war.rglob('*') if p.is_file() and p.suffix.lower() != '.pyc'
             and '__pycache__' not in p.parts]
    # The maintained provenance sorter is OrdinalIgnoreCase. These web paths
    # are ASCII; enforce that boundary rather than guess at Unicode case folding.
    require(all(p.relative_to(war).as_posix().isascii() for p in files),
            'Non-ASCII path requires the maintained ordinal comparer')
    files.sort(key=lambda p: str(p).lower())
    old_records, new_records, actual_changes = [], [], []
    for p in files:
        rel = p.relative_to(war).as_posix()
        digest = sha(p.read_bytes())
        new_records.append(rel+'='+digest)
        if rel in ORIGINALS:
            require(digest != ORIGINALS[rel], 'Expected UI candidate asset unchanged: '+rel)
            actual_changes.append(dict(path='war/'+rel,before=ORIGINALS[rel],after=digest))
        old_records.append(rel+'='+ORIGINALS.get(rel,digest))
    reconstructed = sha('\n'.join(old_records).encode('utf-8'))
    require(reconstructed == EXPECTED_WEB, 'Compiled web or another static asset changed')
    require(len(actual_changes) == 2, 'Missing intended UI assets')
    result = dict(status='PASS_UNCHANGED_JVM_AND_COMPILED_WEB_BOUNDARY',
                  acceptedCommit='5ad170cdea01808bfe25341457b77e709c60fa17',
                  priorFrozenSupersetCount=len(prior['files']),
                  changedNonJvmSupersetPaths=changed,
                  selection='Maintained verifier compiles src/*.java, every registered Java contract and fixed Java fixtures, then runs the selected56. The Node-only mjs fixture is not consumed. All other frozen inputs match byte-for-byte.',
                  selected56Reuse='PASS only at unchanged JVM input boundary; not fresh execution',
                  reconstructedAcceptedWebDigest=reconstructed,
                  candidateWebDigest=sha('\n'.join(new_records).encode('utf-8')),
                  webFileCount=len(files),changedWebAssets=actual_changes,
                  limits='No UI PASS from reused proof. Fresh Node and actual production input are required for changed focus/layout. Broad1447 superset is not wholly unchanged.')
    (HERE/'reuse-boundary.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print(result['status'])


if __name__ == '__main__':
    main()
