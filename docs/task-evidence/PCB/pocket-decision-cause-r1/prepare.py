"""Preserve only original planner/registry before diagnostic instrumentation."""
import hashlib,json,subprocess,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
if subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()!='b502278e9deacad1a724e65b68a34015bc7d21ea':raise ValueError('Accepted HEAD changed')
if (HERE/'local-state.json').exists():raise ValueError('Existing recovery state')
known={r['path']:r['sha256'] for r in json.loads((HERE.parent/'post-selection-edge-trim-r1/final-inputs.json').read_text())['files']}
paths=['src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java','scripts/verify-current-contracts.ps1']
rows=[{'path':p,'sha256':sha(ROOT/p)} for p in paths]
if any(r['sha256']!=known[r['path']] for r in rows):raise ValueError('Accepted owner changed')
scratch=Path(tempfile.mkdtemp(prefix='tsj-pocket-decision-'))
for i,r in enumerate(rows):(scratch/str(i)).write_bytes((ROOT/r['path']).read_bytes())
(HERE/'original-source.json').write_text(json.dumps({'files':rows},indent=2)+'\n')
(HERE/'local-state.json').write_text(json.dumps({'scratch':str(scratch)},indent=2)+'\n')
print('PRESERVED2 verified original owners in task-only OS-temp; no source edit yet')
