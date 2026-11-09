"""Actual selected Java main protocol rejections; never producer success."""
from pathlib import Path
import hashlib,json,os,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
C=Path(__file__).resolve().parent
p=C/'run_export_composition_r1.py';raw=p.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='c7514ea198a9f569302bea77093a3dcd1ab36e9775ca77ee3824b39180fc9610'
cut=b'_audit_compile()\nif MODE == "suite":\n    pins.extend(_accepted_smoke())'
assert raw.count(cut)==1
m=types.ModuleType('_root_export_composition_protocol');m.__file__=str(p)
saved=sys.argv
try:
    sys.argv=[str(p),'--case','smoke']
    exec(compile(raw.split(cut)[0],str(p),'exec'),m.__dict__)
finally:sys.argv=saved
m._audit_compile();m._accepted_smoke()
root=C/'protocol-fixture-r1';root.mkdir();assert root.resolve(strict=True)==root
identity=os.lstat(root);calls=[]
def control(name,args,reason,mode):
    argv=['-ea','-Xmx512m','-cp',m.classpath,m.CASE_CLASSES['smoke']]+args
    call=m.root.call(name,m.g.JDK/'java.exe',argv,expected=1)
    m._request(C/name,m.g.JDK/'java.exe',argv)
    assert m.g.audit_call(C/name,1)==call
    assert not m._read(C/name/'stderr.txt',m.REPORT_CAP,allow_empty=True)
    value=m._json(m._read(C/name/'stdout.txt',m.REPORT_CAP))
    assert set(value)=={'mode','status','names','cases'}
    assert value['mode']==mode and value['status']=='FAIL' and value['names']==[]
    assert len(value['cases'])==1
    record=value['cases'][0]
    assert record['name']=='protocol' and record['status']=='FAIL' and record['phase']=='protocol'
    assert record['reason']==record['error']=='AssertionError:'+reason
    assert record['units']==record['copiedEntries']==record['copyChunks']==record['wrapperGroups']==0
    assert record['cleanupDisposition']=='' and record['cleanupError']==''
    assert not record['meterOpen'] and not record['metricsReturned']
    current=os.lstat(root);assert (current.st_dev,current.st_ino)==(identity.st_dev,identity.st_ino)
    calls.append({'outcome':'EXPECTED_PROTOCOL_REJECTION_NOT_NATIVE_PRODUCER_PASS',
                  'case':name,'reason':reason,'call':call,'summary':value})
control('export-composition-protocol-invalid-mode-r1',['INVALID',str(root)],'CANARY_ARGUMENTS','INVALID')
assert not any(root.iterdir())
marker=root/'owned-marker.txt';content=b'controlled task marker preserved by nonempty-root rejection\n'
with marker.open('xb') as out:out.write(content)
marker_identity=os.lstat(marker)
control('export-composition-protocol-nonempty-root-r1',['SMOKE',str(root)],'DIRECTORY_NOT_EMPTY','SMOKE')
assert list(root.iterdir())==[marker] and marker.read_bytes()==content
now=os.lstat(marker)
assert (now.st_dev,now.st_ino,now.st_size,now.st_mtime_ns)==(marker_identity.st_dev,marker_identity.st_ino,marker_identity.st_size,marker_identity.st_mtime_ns)
assert marker.resolve(strict=True).parent==root and marker.parent==root and root.parent==C
marker.unlink();assert not any(root.iterdir())
control('export-composition-protocol-extra-argument-r1',['SMOKE',str(root),'EXTRA'],'CANARY_ARGUMENTS','SMOKE')
assert not any(root.iterdir());assert root.resolve(strict=True).parent==C
current=os.lstat(root);assert (current.st_dev,current.st_ino)==(identity.st_dev,identity.st_ino)
root.rmdir();assert not root.exists()
assert m._recheck(tuple(m.pins),m.jars,m.classes,m.inventory,m.tools)==(m.class_count,m.class_bytes)
m._audit_compile();m._accepted_smoke()
value={'status':'PASS_THREE_ACTUAL_MAIN_PROTOCOL_REJECTION_CONTROLS','calls':calls,
       'nativeCaseCount':0,'selectedExitCode':1,'allExpectedRejections':True,
       'controlledMarkerPreservedByRejectedMain':True,'taskFixtureCleaned':True,
       'sourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
       'genuineEnvelope':'UNSPENT_GATED'}
with (C/'PROTOCOL_GATE_R1.json').open('xb') as out:out.write((json.dumps(value,indent=2)+'\n').encode('ascii'))
print(value['status'])
