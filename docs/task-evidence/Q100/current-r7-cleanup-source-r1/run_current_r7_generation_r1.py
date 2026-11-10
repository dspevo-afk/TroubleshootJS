"""Real selected current Source CLI controls; no Study or legacy builder execution."""
from pathlib import Path
import hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
path=R/'run_current_r7_gates_r1.py';raw=path.read_bytes()
freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
assert hashlib.sha256(raw).hexdigest()==next(x['sha256'] for x in freeze['files'] if x['name']==path.name)
d=types.ModuleType('_current_r7_generation_gate');d.__file__=str(path)
exec(compile(raw,str(path),'exec',dont_inherit=True,optimize=0),d.__dict__)
previous,b,old,m=d.dependency();pin=d.frozen()
reference=d.parse(d.read(R/'REFERENCE_R1.json'))
calls=[]
def invoke(label,source,component,authored,output,expected,error=None,old_args=False):
    args=['-I','-B',str(d.C/'generate_body_ownership.py'),'--source-root',str(source),
        '--component-root',str(component),'--output-root',str(output)]
    if not old_args:args+=['--authored-root',str(authored)]
    result=d.call(old,m,'current-r7-emit-'+label+'-r1',m.g.PY,args,expected=expected)
    stdout=d.read(R/result['phase']/'stdout.txt',1048576)
    stderr=d.read(R/result['phase']/'stderr.txt')
    if expected:
        assert not stdout and stderr
        if error:assert stderr.strip()==error.encode('ascii')
    else:assert not stderr
    calls.append({'name':label,'expectedExit':expected,'call':result})
    return stdout
output=R/'emitted-source-r1';output.mkdir()
positive_raw=invoke('positive',R/'baseline',d.C,d.A,output,0)
manifest=d.parse(positive_raw)
assert positive_raw==(json.dumps(manifest,sort_keys=True,separators=(',',':'),ensure_ascii=True)+'\n').encode('ascii')
assert manifest['schema']=='TSJ_CURRENT_R7_CLEANUP_SOURCE_EMIT_R1' and manifest['status']=='SOURCE_ONLY'
assert manifest['finalSource']==dict(reference['expectedFinalStudy'],role='exclusive-output',basename='CohortTerminalTelemetryStudy.java')
assert manifest['derivedSource']==dict(reference['expectedPreOwnershipStudy'],role='pre-ownership-current-study')
for role,path in [('currentStudy',R/'baseline/CURRENT_STUDY.java'),('ownershipHelper',d.C/'CurrentBodyOwnership.java'),
                  ('authoredModule',d.A/'generate_authored_flat_work_overlay.py'),('authoredMembers',d.A/'AuthoredFlatWorkMembers.java')]:
    content=d.read(path);row=manifest['inputs'][role]
    assert row['sha256']==d.sha(content) and row['bytes']==len(content) and row['basename']==path.name
pure=manifest['pureProvenance'];pure_raw=pure['ascii'].encode('ascii')
assert pure['sha256']==d.sha(pure_raw) and pure['bytes']==len(pure_raw)
assert d.parse(pure_raw)==pure['object'] and pure['object']['schema']=='TSJ_CURRENT_R7_AUTHORED_SOURCE_R1'
assert pure_raw==(json.dumps(pure['object'],sort_keys=True,separators=(',',':'),ensure_ascii=True)+'\n').encode('ascii')
assert pure['object']['limitsAndGates']['genuineOriginalNullObserve']=='UNSPENT_GATED'
final=d.read(output/'CohortTerminalTelemetryStudy.java')
assert len(final)==reference['expectedFinalStudy']['bytes'] and d.sha(final)==reference['expectedFinalStudy']['sha256']
base=d.read(R/'baseline/CURRENT_STUDY.java');hook=reference['hookAscii'].encode('ascii')
assert final.count(hook)==1 and final.replace(hook,b'',1)==base
folder=R/'emitter-control-fixtures-r1';folder.mkdir()
def fixture(label,source=R/'baseline',component=d.C,authored=d.A):
    root=folder/label;root.mkdir();out=root/'output';out.mkdir()
    return root,source,component,authored,out
for label,role,name,error in [
    ('source-pin','source','CURRENT_STUDY.java','EMIT_SOURCE_PIN'),
    ('helper-pin','component','CurrentBodyOwnership.java','EMIT_COMPONENT_PIN'),
    ('module-pin','authored','generate_authored_flat_work_overlay.py','EMIT_AUTHORED_MODULE_PIN'),
    ('template-pin','authored','AuthoredFlatWorkMembers.java','EMIT_AUTHORED_FRAGMENT_PIN')]:
    root,source,component,authored,out=fixture(label);changed=root/'changed';changed.mkdir()
    if role=='source':parent=source;source=changed;names=[name]
    elif role=='component':parent=component;component=changed;names=[name]
    else:parent=authored;authored=changed;names=['AuthoredFlatWorkMembers.java','generate_authored_flat_work_overlay.py']
    for leaf in names:
        content=d.read(parent/leaf)
        if leaf==name:content=(b'X' if content[:1]!=b'X' else b'Y')+content[1:]
        (changed/leaf).write_bytes(content)
    invoke(label,source,component,authored,out,2,error)
    assert not list(out.iterdir())
root,source,component,authored,out=fixture('existing-output')
sentinel=b'PREEXISTING_TASK_SOURCE_ARTIFACT\n';target=out/'CohortTerminalTelemetryStudy.java';target.write_bytes(sentinel)
invoke('existing-output',source,component,authored,out,2,'EMIT_OUTPUT_EXISTS')
assert target.read_bytes()==sentinel
root,source,component,authored,out=fixture('old-cli')
stdout=invoke('old-cli',root/'no-source',root/'no-component',root/'no-authored',out,2,old_args=True)
assert not list(out.iterdir())
stderr=d.read(R/'current-r7-emit-old-cli-r1/stderr.txt')
assert b'--authored-root' in stderr and b'required' in stderr
root,source,component,authored,out=fixture('junction')
junction=Path(R.anchor)/'Documents and Settings'
assert getattr(junction.lstat(),'st_file_attributes',0)&0x400
invoke('junction',junction,component,authored,out,2,'EMIT_SOURCE_INPUT')
assert not list(out.iterdir())
policy=d.call(old,m,'current-r7-real-file-policy-r1',m.g.PY,['-I','-B',str(R/'current_r7_file_policy_canary_r1.py')])
policy_value=d.parse(d.read(R/policy['phase']/'stdout.txt',1048576))
assert policy_value['status']=='PASS_REAL_CURRENT_R7_FILE_STABILITY_CONTROLS' and not d.read(R/policy['phase']/'stderr.txt')
assert d.frozen()==pin;d.recheck(previous,b,old,m)
value={'status':'PASS_REAL_CURRENT_R7_SOURCE_EMISSION_AND_CONTROLS','sourceFreezeSha256':pin,
    'positiveManifest':manifest,'positiveManifestSha256':d.sha(positive_raw),
    'positive':calls[0]['call'],'expectedExit2':7,'calls':calls,'realFilePolicyCall':policy,
    'realFilePolicy':policy_value,'actualAggregationHook':'NOT_RUN_UNSPENT_GATED','genuineEnvelope':'UNSPENT_GATED'}
print(d.write('GENERATION_GATE_R1.json',value))
