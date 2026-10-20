"""Freeze final gates against the exact successful early native candidate."""
import hashlib,json,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
PARENT=HERE.parent
ROOT=HERE.parents[4]
GATES=('scope.md','inputs.py','qualify.py','run_phase.ps1','record_exit.ps1','suites.json')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(phase='candidate'):
    assert phase in ('candidate','census')
    old=runpy.run_path(str(PARENT/'third_inputs.py'))['record']('target-r3')
    assert old==read(PARENT/'target-r3/inputs.json')
    assert read(PARENT/'target-r3/assessment.json')['decision']=='GO'
    assert read(PARENT/'fixture-r3/assessment.json')['status']=='PASS_ACTUAL_OPERATION'
    return {'baseCommit':old['baseCommit'],'phase':phase,'files':old['files'],'machinery':old['machinery'],
            'gates':{n:sha(HERE/n) for n in GATES+(('compare_census.py','census-reader-notes.md') if phase=='census' else ())},'earlyTargetSealSha256':sha(PARENT/'target-r3/inputs.json'),
            'earlyTargetAssessmentSha256':sha(PARENT/'target-r3/assessment.json'),
            'publishedCensusSha256':sha(PARENT.parent/'packing-metric-evidence-r1/final-r2/census-receipt.txt')}
def main():
    command,phase=sys.argv[1:];assert command in ('freeze','audit')
    value=record(phase);target=HERE/(phase+'-inputs.json')
    archive=HERE/('census-source' if phase=='census' else 'source')
    if phase=='census':assert record('candidate')==read(HERE/'candidate-inputs.json')
    if command=='freeze':
        assert not target.exists()
        for n in value['gates']:
            d=archive/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes((HERE/n).read_bytes())
        target.write_text(json.dumps(value,indent=2)+'\n')
    assert read(target)==value
    assert {p.relative_to(archive).as_posix():sha(p) for p in archive.rglob('*') if p.is_file()}==value['gates']
    print('PASS final via-cap exact sources1482 machinery24 helpers'+str(len(value['gates'])))
if __name__=='__main__':main()
