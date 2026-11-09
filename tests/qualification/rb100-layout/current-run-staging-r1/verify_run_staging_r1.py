"""Independent literal-order and native-census reader; never owner acceptance."""
from pathlib import Path
import argparse,hashlib,json,os,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
CAP=1048576
REFERENCE_SHA='9ff0fe9fb4080a42066c9e4feb689428cdf1404d4e47eba3d7c8cee787da0023'
NAMES=['positive','wrong_capture_order','duplicate_capture','missing_capture','requested_mismatch',
       'prior_false','capture_false','null_arguments','group_guard_before_read']
TOP='schema mode status names cases parentConstructions error'.split()
KEYS=('name status phase reason expectedError observedError units copiedEntries copyChunks groups priorGroups groupUnits '
      'maxGroupUnits maxGroupNanos meterOpen setupDeltaUnits setupDeltaCopies setupDeltaChunks stageDeltaUnits '
      'stageDeltaCopies stageDeltaChunks operationNanos totalElapsedNanos openGroupElapsedNanos inputUnchanged '
      'outputDetached rejections sourceReads sourceReadObserved sourceReadCensus payload discarded finishCalls').split()
def need(ok,code):
    if not ok:raise ValueError(code)
def read(path):
    p=Path(path).absolute()
    for ancestor in list(reversed(p.parents))+[p]:
        st=ancestor.lstat();need(not ancestor.is_symlink() and not getattr(st,'st_file_attributes',0)&0x400,'REPARSE')
    s=p.lstat()
    need(0<s.st_size<=CAP,'SIZE')
    with p.open('rb') as f:
        a=os.fstat(f.fileno());raw=f.read(CAP+1);b=os.fstat(f.fileno())
    z=p.lstat()
    def ident(x):return x.st_dev,x.st_ino,x.st_size,x.st_mtime_ns
    need(ident(s)==ident(a)==ident(b)==ident(z) and len(raw)==s.st_size,'CHANGED_FILE')
    return raw
def parse(raw):
    def pairs(rows):
        d={}
        for k,v in rows:need(k not in d,'DUPLICATE');d[k]=v
        return d
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda _:(_ for _ in ()).throw(ValueError('NONFINITE')))
def number(value,maximum=90000000000):need(type(value) is int and 0<=value<=maximum,'NUMBER')
def verify(v,reference):
    need(type(v) is dict and list(v)==TOP,'TOP_KEYS')
    need(v['schema']=='TSJ_CURRENT_RUN_STAGING_CANARY_R1' and v['mode']=='SUITE','PROTOCOL')
    need(v['status']=='PASS' and v['error']=='','STATUS')
    need(v['names']==NAMES and type(v['cases']) is list and len(v['cases'])==9,'NAMES')
    need(type(v['parentConstructions']) is int and v['parentConstructions']==9,'PARENTS')
    errors=['','COHORT_REQUESTED_VECTOR','COHORT_VECTOR_DUPLICATE','COHORT_NOT_IN_CAPTURE',
            'COHORT_REQUESTED_VECTOR','V2_SETUP_POLICY','V2_SETUP_POLICY','METADATA_ARGUMENTS',
            'native wrapper exceeded 640 measured groups']
    errors=['IOException:'+x if x else '' for x in errors]
    for i,(name,row) in enumerate(zip(NAMES,v['cases'])):
        need(type(row) is dict and list(row)==KEYS,'CASE_KEYS')
        need(row['name']==name and row['status']=='PASS' and row['reason']==('OK' if i==0 else 'EXPECTED_REJECTION'),'CASE_STATUS')
        need(type(row['phase']) is str and 0<len(row['phase'])<=64,'PHASE')
        need(row['expectedError']==row['observedError']==errors[i],'ERROR')
        for key in 'units copiedEntries copyChunks groups priorGroups groupUnits maxGroupUnits maxGroupNanos setupDeltaUnits setupDeltaCopies setupDeltaChunks stageDeltaUnits stageDeltaCopies stageDeltaChunks operationNanos totalElapsedNanos openGroupElapsedNanos rejections sourceReads sourceReadCensus finishCalls'.split():number(row[key])
        for key in 'meterOpen inputUnchanged outputDetached discarded'.split():need(type(row[key]) is bool,'BOOLEAN')
        need(row['inputUnchanged'] is True,'MUTATION')
        need(row['priorGroups']==0 and row['groups']<=640 and row['groupUnits']<=32768 and row['maxGroupUnits']<=32768,'GROUP_LIMITS')
        need(row['copiedEntries']<=1048576 and row['maxGroupNanos']<=5000000000 and row['openGroupElapsedNanos']<=5000000000,'LIMITS')
        need(row['operationNanos']>0 and row['totalElapsedNanos']>0,'CLOCK')
        need(row['meterOpen'] or row['openGroupElapsedNanos']==0,'CLOSED_CLOCK')
        stage=[row[k] for k in ('stageDeltaUnits','stageDeltaCopies','stageDeltaChunks')]
        setup=[row[k] for k in ('setupDeltaUnits','setupDeltaCopies','setupDeltaChunks')]
        if i<=4:
            delta=[[264,125,64],[259,123,63],[141,84,24],[258,123,63],[259,123,63]][i]
            need(setup==[10007,478,3] and stage==delta,'STAGING_CENSUS')
            need([row['units'],row['copiedEntries'],row['copyChunks']]==[10007+delta[0],478+delta[1],3+delta[2]],'TOTAL_CENSUS')
            need(row['groups']==(2 if i==0 else 1) and row['groupUnits']==delta[0] and row['maxGroupUnits']==10007,'STAGING_GROUPS')
        elif i in (5,6):
            need(651<row['units']<10007 and setup==[row['units'],473,2] and stage==[0,0,0],'REJECTED_INPUT_CENSUS')
            need(row['copiedEntries']==473 and row['copyChunks']==2 and row['groups']==0 and row['maxGroupUnits']==0 and row['groupUnits']==row['units'],'REJECTED_INPUT_GROUPS')
        elif i==7:
            # Three null guards act on an already validated, closed setup Meter.
            need(setup==[10007,478,3] and stage==[0,0,0],'NULL_GUARD_DELTA')
            need([row['units'],row['copiedEntries'],row['copyChunks'],row['groups'],row['groupUnits'],row['maxGroupUnits']]==[10007,478,3,1,10007,10007],'NULL_GUARD_CENSUS')
        else:
            need(setup==stage==[0,0,0],'GUARD_DELTA')
            need([row['units'],row['copiedEntries'],row['copyChunks'],row['groups'],row['groupUnits'],row['maxGroupUnits']]==[640,0,0,640,1,1],'GUARD_CENSUS')
        need(row['meterOpen']==(1<=i<=6),'OPEN')
        need(row['discarded']==(i!=0) and row['finishCalls']==(1 if i==0 else 0),'FINISH')
        need(row['rejections']==(0 if i==0 else 3 if i==7 else 1),'REJECTIONS')
        need(row['sourceReads']==(4 if i<=7 else 0) and row['sourceReadCensus']==(1 if i<=4 else 0),'READS')
        need((type(row['sourceReadObserved']) is int and row['sourceReadObserved']==0) if i==8 else row['sourceReadObserved'] is None,'OBSERVED_READS')
        need(row['outputDetached']==(i==0),'OUTPUT_ALIAS')
        if i==0:
            need(type(row['payload']) is dict and list(row['payload'])==['netIds','requestedNetIds'],'PAYLOAD_KEYS')
            need(row['payload']['netIds']==['A_COIL_LOW','B_COIL_LOW'] and row['payload']['requestedNetIds']==reference['expectedRequested'],'PAYLOAD')
        else:need(row['payload'] is None,'REJECTED_PAYLOAD')
    return {'status':'PASS_INDEPENDENT_SYNTHETIC_RUN_STAGING_READER','caseCount':9,
            'genuineEnvelope':'UNSPENT_GATED','normalHostAcceptance':False}
def main():
    p=argparse.ArgumentParser();p.add_argument('--input-json',type=Path,required=True);p.add_argument('--reference-root',type=Path,required=True)
    a=p.parse_args()
    try:
        raw=read(a.reference_root/'FIXTURE_REFERENCE_R1.json')
        need(hashlib.sha256(raw).hexdigest()==REFERENCE_SHA,'REFERENCE_IDENTITY')
        result=verify(parse(read(a.input_json)),parse(raw))
        print(json.dumps(result));return 0
    except Exception as e:
        print(json.dumps({'status':'REJECTED','error':type(e).__name__+':'+str(e)[:160]}));return 2
if __name__=='__main__':sys.exit(main())
