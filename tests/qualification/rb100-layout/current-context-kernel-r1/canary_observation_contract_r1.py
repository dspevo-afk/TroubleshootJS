"""Actual fixed normal observation adapter only; never whole prior acceptance."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
import types

def load(path,digest,name):
    with Path(path).open('rb') as stream:
        raw=stream.read(4194305)
    assert 0<len(raw)<=4194304 and hashlib.sha256(raw).hexdigest()==digest
    module=types.ModuleType(name)
    module.__file__=str(path)
    sys.modules[name]=module
    exec(compile(raw,str(path),'exec'),module.__dict__)
    return module

def main():
    if not sys.flags.isolated or not sys.dont_write_bytecode or sys.flags.optimize:
        raise RuntimeError('ISOLATED_NONOPTIMIZED_NO_BYTECODE_REQUIRED')
    p=argparse.ArgumentParser()
    for arg in ('normalizer','normalizer-sha','reader','reader-sha','root-n','root-d','journal','journal-sha'):
        p.add_argument('--'+arg,required=True)
    a=p.parse_args()
    sut=load(a.normalizer,a.normalizer_sha,'_observation_contract_subject')
    reader=load(a.reader,a.reader_sha,'_observation_contract_reader')
    journal,_,jt=reader._read_file_once(a.journal,1048576,None,a.journal_sha,'NORMAL_ONLY_JOURNAL')
    assert json.loads(journal)['status']=='ALLOW_FIXED_20_NORMAL_PRIOR_READ'
    names=set(sut.ROLE_TO_ARTIFACT)|set(sut.ROLE_TO_D_QUALIFICATION)|{'D_PRIOR_OBSERVATION_CROSSCHECK'}
    assert len(names)==17
    specs={s.role:s for s in reader._ROLE_PINS}
    data={}
    tokens={}
    roots={'N':a.root_n,'D':a.root_d}
    # Authenticate all17 component inputs before decoding any body.
    for name in sorted(names):
        pin=specs[name]
        path=str(Path(roots[pin.root])/pin.relative_path)
        raw,_,token=reader._read_file_once(path,pin.max_bytes,pin.expected_bytes,pin.sha256,name)
        data[name]=raw
        tokens[name]=token
    pins={name:dict(root=specs[name].root,path=specs[name].relative_path) for name in names}
    host={'qualification':{
        'parent':sut._json_load(data['D_PRIOR_QUALIFICATION_PARENT_JSON'],524288,'Q_PARENT',allow_float=True),
        'result':sut._json_load(data['D_PRIOR_QUALIFICATION_RESULT_JSON'],524288,'Q_RESULT',allow_float=True)}}
    prior=sut._json_load(data['N_PRIOR_RESULT'],8388608,'PRIOR_RESULT',allow_float=False,line=True)
    summary=sut._console_json(data['D_PRIOR_QUALIFICATION_STDOUT_TXT'],1024,'QUALIFICATION_STDOUT')
    raw=data['D_PRIOR_OBSERVATION_CROSSCHECK']
    result=sut._validate_observation_crosscheck(raw,data,pins,host,prior,summary)
    assert type(result) is dict and len(result)==17
    assert [set(row) for row in result['approvedPriorArtifactRows']]==[{'path','bytes','sha256'}]*9
    assert repr(result['processElapsedMs'])=='112691.18650000001'
    assert repr(result['wrapperTotalElapsedMs'])=='112943.8217'
    variants=[]
    for key,value in (('processElapsedMs',float('nan')),('processElapsedMs',float('inf')),
        ('wrapperTotalElapsedMs',float('-inf')),('processElapsedMs',True),
        ('wrapperTotalElapsedMs',result['wrapperTotalElapsedMs']+1),('newAuthoredSessions',False),
        ('processRootAndLogsAbsentOnRecheck',False),('isolatedCleanupDurationMs',0),
        ('newNativeComparisonStatus','PASS')):
        doc=copy.deepcopy(result)
        doc[key]=value
        variants.append((key,json.dumps(doc,separators=(',',':'),allow_nan=True).encode()))
    for kind in ('extra-field','row-role','row-reorder','row-sha','row-count'):
        doc=copy.deepcopy(result)
        if kind=='extra-field': doc['unexpected']=0
        if kind=='row-role': doc['approvedPriorArtifactRows'][0]['role']='call-owner'
        if kind=='row-reorder': doc['approvedPriorArtifactRows'][:2]=reversed(doc['approvedPriorArtifactRows'][:2])
        if kind=='row-sha': doc['approvedPriorArtifactRows'][0]['sha256']='0'*64
        if kind=='row-count': doc['approvedPriorArtifactRows'].pop()
        variants.append((kind,json.dumps(doc,separators=(',',':')).encode()))
    compact=json.dumps(result,separators=(',',':')).encode()
    needle=repr(result['processElapsedMs']).encode()
    assert compact.count(needle)==1
    variants.append(('float-overflow',compact.replace(needle,b'1e999')))
    failures=[]
    for name,changed in variants:
        try:
            sut._validate_observation_crosscheck(changed,data,pins,host,prior,summary)
        except ValueError as error:
            failures.append(dict(case=name,code=getattr(error,'code',type(error).__name__)))
        else:
            raise AssertionError('MUTATED_OBSERVATION_ACCEPTED:'+name)
    if len(failures)!=15:
        raise RuntimeError('EXACT15_OBSERVATION_REJECTIONS_REQUIRED')
    for name in sorted(names):
        pin=specs[name]
        raw2,_,token=reader._read_file_once(str(Path(roots[pin.root])/pin.relative_path),
            pin.max_bytes,pin.expected_bytes,pin.sha256,name)
        assert raw2==data[name] and reader._token_identity(token)==reader._token_identity(tokens[name])
    j2,_,jt2=reader._read_file_once(a.journal,1048576,None,a.journal_sha,'NORMAL_ONLY_JOURNAL')
    assert j2==journal and reader._token_identity(jt2)==reader._token_identity(jt)
    print(json.dumps(dict(status='PASS_AUTHENTICATED_NORMAL_OBSERVATION_COMPONENT_ONLY',
        authenticatedRoleCount=17,observationBytes=len(raw),observationSha256=specs['D_PRIOR_OBSERVATION_CROSSCHECK'].sha256,
        finiteTimingFields=2,artifactRows=9,artifactFields=3,negativeCases=failures,
        fullPriorAcceptance='NOT_ESTABLISHED',nativeCalls=0),sort_keys=True))

if __name__=='__main__':
    main()
