"""Audit a rejected trial and exact accepted-source restoration, not acceptance."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
def read(name):return json.loads((HERE/name).read_text(encoding='utf-8-sig'))
def require(ok,message):
    if not ok:raise ValueError(message)
def main():
    native=read('focused-timing.json');metric=read('focused-assessment.json')
    require(native['status']=='PASS' and native['exitCode']==0 and native['suiteCount']==9,'Native terminal gate')
    require(read('focused-input-audit.json')['status']=='PASS','Trial consumed inputs changed')
    require(read('assessor-canaries.json')['status']=='PASS' and read('assessor-canaries.json')['canaryCount']==13,'Assessor guards')
    require(metric['status']=='FAIL' and len(metric['failures'])==9 and len(metric['cases'])==14,'Strict rejected result changed')
    require(read('assessment-timing.json')['exitCode']==1,'Metric FAIL must remain exit1')
    require(read('restored-accepted-input-audit.json')['status']=='PASS' and read('restored-accepted-input-audit.json')['frozenInputCount']==1447,'Accepted source inputs not restored')
    require(read('restored-provenance.json')==json.loads((HERE.parent/'viewport-scroll-r1/preview-expected.json').read_text(encoding='utf-8-sig')),'Restored full source/script/compiled-web boundary changed')
    for row in read('original-source.json')['files']:
        require(hashlib.sha256((ROOT/row['path']).read_bytes()).hexdigest()==row['sha256'],'Owner byte restoration '+row['path'])
    for row in read('rejection.json')['archivedContracts']:
        require(not (ROOT/'tests/contracts'/row['file']).exists(),'Active rejected contract remains')
        require(hashlib.sha256((HERE/'rejected-source'/row['file']).read_bytes()).hexdigest()==row['sha256'],'Archived contract bytes changed')
    require(read('resource-cleanup.json')['status']=='PASS','Owned resources remain')
    result=dict(status='PASS_REJECTION_AND_RESTORATION_AUDIT',candidate='REJECTED_NOT_PROMOTED',
        nativeSeconds=native['elapsedSeconds'],metricFailures=metric['failures'],
        production='Exact published410bcd73 default14 restored; prior native/GWT/player proof reused only at exact audited boundary, not fresh execution',
        limits='Trial native9 PASS but strict metric FAIL. Trial GWT/player gates NOT RUN. No additional shrinkage, unseen/all-seed or manufacturing claim.')
    (HERE/'restoration-audit.json').write_bytes((json.dumps(result,indent=2)+'\n').encode('utf-8'))
    print('PASS rejection/restoration audit; candidate remains REJECTED')
if __name__=='__main__':main()
