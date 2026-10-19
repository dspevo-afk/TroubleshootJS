"""Audit diagnostic receipts and exact restored production; not acceptance."""
import hashlib,json
from pathlib import Path
import assess
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def require(ok,why):
    if not ok:raise ValueError(why)
timing=read(HERE/'focused-timing.json')
require(timing['status']=='PASS' and timing['exitCode']==0 and timing['suiteCount']==3,'Native terminal')
log=(HERE/'focused-native.txt').read_text(encoding='utf-8-sig')
require('CLEANUP: current JVM contract classes and task-owned scratch removed.' in log,'Native cleanup')
require('CURRENT_CONTRACT_FAILURE' not in log and 'PCB_ROUTE_RANKING_FAILURE' not in log,'Failure log')
require(read(HERE/'focused-input-audit.json')['status']=='PASS','Consumed inputs')
require(read(HERE/'assessment.json')['status']=='PASS_EXACT_REPRODUCTION','Archived reproduction')
require(read(HERE/'assessor-canaries.json')['canaryCount']==8,'Reader canaries')
require(read(HERE/'restored-accepted-input-audit.json')['status']=='PASS','Accepted restoration')
require(read(HERE/'restored-accepted-input-audit.json')['frozenInputCount']==1447,'Accepted census')
require(read(HERE/'restored-provenance.json')==read(assess.OLD/'restored-provenance.json'),'Full restored execution identity')
require(read(HERE/'resource-cleanup.json')['status']=='PASS' and read(HERE/'byte-restoration.json')['status']=='PASS','Owned resources')
require(not (HERE/'local-state.json').exists(),'Private local state remains')
for row in read(assess.OLD/'original-source.json')['files']:
    require(hashlib.sha256((ROOT/row['path']).read_bytes()).hexdigest()==row['sha256'],'Owner '+row['path'])
for archived in HERE.glob('PcbRouteRankingCauseContractTest*.java'):
    require(not (ROOT/'tests/contracts'/archived.name).exists(),'Active diagnostic '+archived.name)
result={'status':'PASS_DIAGNOSTIC_AND_RESTORATION','cases':10,'nativeSeconds':timing['elapsedSeconds'],
        'finding':'Same requested attempts in every pair; changed candidate0 routing/selection causes trial regressions, not rank displacement',
        'production':'Exact accepted14/density13 and full compiled web restored; rejected15 remains rejected',
        'limits':'Finite informed roots; winner-only quality; no fresh GWT/player/full-matrix/electrical qualification; unchanged prior evidence reused only at exact audited execution boundary'}
(HERE/'result.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
files=[{'path':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(HERE.iterdir()) if p.is_file() and p.name!='result-integrity.json']
(HERE/'result-integrity.json').write_bytes((json.dumps({'files':files},indent=2)+'\n').encode())
print('PASS diagnostic/reproduction/restoration audit; no generation change accepted')
