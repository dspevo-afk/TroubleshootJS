"""After acceptance, remove only five verified original snapshots; retain accepted edits."""
import hashlib,json,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(n):return json.loads((HERE/n).read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
for name in ('player-acceptance.json','final-geometry-assessment.json','post-player-input-audit.json'):
    if read(name)['status']!='PASS':raise ValueError('Missing acceptance '+name)
state=HERE/'local-state.json'
if state.is_symlink() or state.is_junction():raise ValueError('Recovery state reparse point')
scratch=Path(read('local-state.json')['scratch'])
temp=Path(tempfile.gettempdir()).resolve()
if (not scratch.is_absolute() or scratch.parent.resolve()!=temp or
        not scratch.name.startswith('tsj-edge-trim-') or scratch.is_symlink() or
        scratch.is_junction() or scratch.resolve()!=scratch.absolute()):
    raise ValueError('Snapshot ownership/containment boundary')
rows=read('original-source.json')['files']
if len(rows)!=5 or {p.name for p in scratch.iterdir()}!={str(i) for i in range(5)}:
    raise ValueError('Unexpected snapshot inventory')
final={r['path']:r['sha256'] for r in read('final-inputs.json')['files']}
for i,row in enumerate(rows):
    p=scratch/str(i)
    if p.is_symlink() or p.is_junction() or not p.is_file() or sha(p)!=row['sha256']:
        raise ValueError('Original snapshot changed')
    if sha(ROOT/row['path'])!=final[row['path']]:raise ValueError('Accepted source changed')
for i in range(5):(scratch/str(i)).unlink()
scratch.rmdir();state.unlink()
(HERE/'snapshot-cleanup.json').write_text(json.dumps({'status':'PASS','verifiedOriginalsRemoved':5,
    'ownedEmptySnapshotDirectoryRemoved':True,'privateRecoveryStateRemoved':True,
    'acceptedSourceRetained':True,'scope':'Nonrecursive deletion after exact original bytes, final owner hashes, direct OS-temp containment and no-reparse checks'},indent=2)+'\n')
print('PASS: adopted accepted source; removed five verified snapshots and private recovery state')
