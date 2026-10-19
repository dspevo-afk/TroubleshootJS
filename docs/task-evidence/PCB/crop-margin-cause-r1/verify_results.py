"""Audit finite crop observations and restored accepted execution boundary."""
import hashlib,json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def require(ok,why):
    if not ok:raise ValueError(why)
timing=read(HERE/'focused-timing.json');observation=read(HERE/'assessment.json')
require(timing['status']=='PASS' and timing['exitCode']==0 and timing['suiteCount']==3,'Native terminal')
log=(HERE/'focused-native.txt').read_text(encoding='utf-8-sig')
require('CLEANUP: current JVM contract classes and task-owned scratch removed.' in log,'Native cleanup')
require('CURRENT_CONTRACT_FAILURE' not in log and 'PCB_CROP_MARGIN_FAILURE' not in log,'Failure log')
require(read(HERE/'focused-input-audit.json')['status']=='PASS','Consumed inputs')
require(observation['status']=='PASS_EXACT_REPRODUCTION_AND_CROP_OBSERVATION' and len(observation['cases'])==6,'Exact reproduction')
require(read(HERE/'assessor-canaries.json')['canaryCount']==11,'Reader canaries')
require(read(HERE/'restored-accepted-input-audit.json')['status']=='PASS' and read(HERE/'restored-accepted-input-audit.json')['frozenInputCount']==1447,'Accepted restoration')
require(read(HERE/'restored-provenance.json')==read(HERE.parent/'route-ranking-cause-r1/restored-provenance.json'),'Full restored execution identity')
require(read(HERE/'resource-cleanup.json')['status']=='PASS' and read(HERE/'byte-restoration.json')['status']=='PASS','Owned resources')
require(not (HERE/'local-state.json').exists(),'Private local state remains')
for row in read(HERE.parent/'soft-isolation-advice-r1/original-source.json')['files']:
    require(hashlib.sha256((ROOT/row['path']).read_bytes()).hexdigest()==row['sha256'],'Owner '+row['path'])
for archived in HERE.glob('PcbCropMarginCauseContractTest*.java'):
    require(not (ROOT/'tests/contracts'/archived.name).exists(),'Active diagnostic '+archived.name)
result={'status':'PASS_DIAGNOSTIC_AND_RESTORATION','cases':6,'nativeSeconds':timing['elapsedSeconds'],
        'finding':'Five selected layouts have fixed floor above reconstructed access need; zero selected-layout fallback increments observed',
        'production':'Exact accepted14/density13 and full compiled execution identity; no production algorithm change',
        'limits':'Finite informed roots, not minimal-valid-margin proof. No fresh GWT/player/full-matrix/independent electrical qualification. Prior proof reused only at exact restored execution boundary.'}
(HERE/'result.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
files=[{'path':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(HERE.iterdir()) if p.is_file() and p.name!='result-integrity.json']
(HERE/'result-integrity.json').write_bytes((json.dumps({'files':files},indent=2)+'\n').encode())
print('PASS crop observation and exact restoration; no margin policy change accepted')
