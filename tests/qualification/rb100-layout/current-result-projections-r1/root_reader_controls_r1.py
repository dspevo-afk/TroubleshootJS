"""Actual selected reader controls over explicitly synthetic Source fixtures."""
from pathlib import Path
import argparse,hashlib,json,os,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
def sha(raw):return hashlib.sha256(raw).hexdigest()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--work-root',required=True,type=Path)
    U=parser.parse_args().work_root.resolve(strict=True)
    frozen=json.loads((U/'SOURCE_FREEZE_R1.json').read_bytes())
    pins={r['role']:r['sha256'] for r in frozen['files']}
    for name in ('root_projection_gates_r1.py','root_reader_controls_r1.py'):
        assert sha((U/name).read_bytes())==pins[name]
    raw=(U/'root_projection_gates_r1.py').read_bytes()
    d=types.ModuleType('_projection_reader_control_driver');d.__file__=str(U/'root_projection_gates_r1.py')
    exec(compile(raw,d.__file__,'exec',dont_inherit=True,optimize=0),d.__dict__)
    m=d.dependency_runtime(U);freeze_sha,_=d.frozen(U)
    reference=d.parse(d.read(U/'REFERENCE_R1.json'));wire=d.parse(d.read(U/'CANARY_WIRE_R1.json'))
    directory=U/'reader-controls-r1';directory.mkdir()
    def expected_case(source):
        name=source['name'];cohort=name=='cohort_transfer';cleanup=name.startswith('cleanup_')
        guard=name=='group_guard_before_read';error=source['expectedError'];opened=source['meterOpen']
        values={'name':name,'status':'PASS','phase':'complete','reason':'OK','payloadJson':source['payloadJson'],
            'observedError':error,'rejections':3 if name=='argument_guards' else (1 if error else 0),
            'units':source['units'],'copiedEntries':source['copiedEntries'],'copyChunks':source['copyChunks'],
            'groups':source['groups'],'priorGroups':0,'groupUnits':1 if guard else source['units'],
            'maxGroupUnits':source['units'] if source['payloadJson'] else (1 if guard else 0),
            'maxGroupNanos':1000 if source['groups'] else 0,'meterOpen':opened,
            'helperUnits':source['helperUnits'],'helperCopies':2 if cohort else (10 if cleanup else 0),
            'helperChunks':1 if cohort or cleanup else 0,'operationNanos':3000,'totalElapsedNanos':2000,
            'openGroupElapsedNanos':1000 if opened else 0,
            'cleanupDisposition':'METER_DISCARDED_NO_OS_RESOURCES' if error else 'NO_OWNED_OS_RESOURCES',
            'inputUnchanged':True,'listsTransferred':cohort,'externalContainersDetached':cohort,
            'noInputContainersRetained':cleanup,'sourceReads':23 if cleanup else 0,
            'sourceSemanticRejectionExpected':source.get('sourceSemanticRejectionExpected',False),'discarded':bool(error)}
        assert list(values)==wire['caseKeys'];return values
    def summary(mode):
        chosen=[r for r in reference['cases'] if r['mode']==mode]
        value={'schema':wire['schema'],'mode':mode,'status':'PASS','names':[r['name'] for r in chosen],
               'cases':[expected_case(r) for r in chosen],'parentConstructions':len(chosen),'error':''}
        assert list(value)==wire['topKeys'];return value
    def put(name,value):
        path=directory/name
        with path.open('xb') as output:output.write((json.dumps(value,separators=(',',':'))+'\n').encode('ascii'))
        return path
    positive=summary('SMOKE');negative=summary('SUITE')
    pos=put('synthetic-smoke.json',positive);neg=put('synthetic-suite.json',negative);calls=[]
    def cli(tag,path,mode='smoke',code=None,program=None):
        phase='projection-reader-'+tag+'-r1'
        call=d.actual_call(U,m,phase,m.g.PY,['-I','-B',str(program or U/'verify_projection_r1.py'),
            '--case',mode,'--input-json',str(path),'--reference-root',str(U)],limit=180000,expected=2 if code else 0)
        assert not d.read(U/phase/'stderr.txt',d.REPORT_CAP,empty=True)
        raw=d.read(U/phase/'stdout.txt',d.REPORT_CAP);value=d.parse(raw)
        if code:assert value=={'status':'REJECTED','code':code},(tag,value)
        else:
            assert value['status']=='PASS_INDEPENDENT_RESULT_PROJECTIONS'
            assert value['caseCount']==(2 if mode=='smoke' else 15)
        calls.append({'tag':tag,'outcome':'EXPECTED_READER_REJECTION' if code else 'PASS_SYNTHETIC_REFERENCE_READER',
                      'code':code,'call':call,'stdoutSha256':sha(raw),'summary':value})
    cli('reference-positive',pos);cli('reference-suite',neg,'suite')
    bad=json.loads(json.dumps(positive));bad['cases'][1]['payloadJson']=bad['cases'][1]['payloadJson'].replace('600','601')
    cli('wrong-payload',put('wrong-payload.json',bad),code='SUMMARY_PAYLOAD')
    bad=json.loads(json.dumps(positive));bad['cases'][0]['units']+=1
    cli('wrong-counter',put('wrong-counter.json',bad),code='SUMMARY_COUNTER')
    bad=json.loads(json.dumps(positive));bad['parentConstructions']=1
    cli('wrong-parent',put('wrong-parent.json',bad),code='SUMMARY_COUNTER')
    bad=json.loads(json.dumps(positive));bad['cases'][0]['totalElapsedNanos']=90000000001
    cli('wrong-clock',put('wrong-clock.json',bad),code='SUMMARY_CLOCK')
    duplicate=directory/'duplicate.json'
    with duplicate.open('xb') as output:output.write(b'{"mode":"SMOKE",'+d.read(pos)[1:])
    cli('duplicate-json',duplicate,code='INPUT_JSON')
    oversized=directory/'oversized.json'
    with oversized.open('xb') as output:output.write(b' '*1048577)
    cli('oversized',oversized,code='INPUT_SIZE')
    wrong=directory/'wrong-source';wrong.mkdir()
    for name in ('projection_oracle.py','verify_projection_r1.py','test_projection_oracle.py','ORACLE_NOTE_R1.md','ROOT_ORACLE_SOURCE_R1.json'):
        raw=d.read(U/name)
        if name=='projection_oracle.py':raw+=b'# changed Source\n'
        with (wrong/name).open('xb') as output:output.write(raw)
    cli('changed-source',pos,code='SOURCE_PIN',program=wrong/'verify_projection_r1.py')
    alias=Path(U.anchor)/'Documents and Settings';st=alias.lstat()
    assert getattr(st,'st_file_attributes',0)&0x400
    relative=pos.relative_to(Path(U.anchor)/'Users')
    cli('existing-junction',alias/relative,code='INPUT_PATH')
    assert len(calls)==10 and sum(bool(r['code']) for r in calls)==8
    d.recheck_dependencies(m);assert d.frozen(U)[0]==freeze_sha
    result={'status':'PASS_TWO_SYNTHETIC_REFERENCE_READERS_AND_EIGHT_ACTUAL_REJECTION_CONTROLS',
        'sourceFreezeSha256':freeze_sha,'calls':calls,'nativeObservedSummariesUsed':False,
        'fixtureClockValuesAreNativeMeasurements':False,'junctionCreatedOrDeleted':False,
        'junctionProof':'SOURCE_ORDER_PLUS_SELECTED_EXISTING_ALIAS_PATH; NO_INDEPENDENT_OS_BYTE_TRACE',
        'actualProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
    d.write(U/'READER_CONTROLS_GATE_R1.json',result)
    print(result['status'])
if __name__=='__main__':main()
