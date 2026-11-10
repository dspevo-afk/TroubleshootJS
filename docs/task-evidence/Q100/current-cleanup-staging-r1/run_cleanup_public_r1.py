"""Check exact integrated bytes and run actual public pure/reader entry points."""
from pathlib import Path
import argparse,hashlib,json,re,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',type=Path,required=True);a=p.parse_args();F=a.repo
T=F/'tests/qualification/rb100-layout/current-cleanup-staging-r1'
source=R/'run_cleanup_gates_r1.py';raw=source.read_bytes();freeze=json.loads((R/'SOURCE_FREEZE_R1.json').read_bytes())
assert hashlib.sha256(raw).hexdigest()==next(x['sha256'] for x in freeze['files'] if x['name']==source.name)
d=types.ModuleType('_cleanup_public_runtime');d.__file__=str(source)
exec(compile(raw,str(source),'exec',dont_inherit=True,optimize=0),d.__dict__)
b,old,m=d.dependencies();d.frozen();d.audit_classes()
mapping={'CurrentAssemblyMetadata.java':F/'tests/qualification/rb100-layout/qualified-metadata-r1/CurrentAssemblyMetadata.java'}
for name in ['CleanupStagingCanary.java','cleanup_oracle.py','verify_cleanup_staging_r1.py','test_cleanup_oracle.py',
             'ROOT_ORACLE_SOURCE_R1.json','CONTRACT_R1.md','REFERENCE_R1.json','WIRE_R1.json']:mapping[name]=T/name
rows=[]
for name,path in mapping.items():
    content=d.read(path);assert content==d.read(R/name)
    rows.append({'path':path.relative_to(F).as_posix(),'bytes':len(content),'sha256':d.sha(content)})
pure=d.call(old,m,'cleanup-public-pure-r1',m.g.PY,['-I','-B',str(T/'test_cleanup_oracle.py'),'--reference-root',str(T),'-v'],limit=180000)
match=re.search(rb'Ran (\d+) tests? in [0-9.]+s\s+OK\s*\Z',d.read(R/pure['phase']/'stderr.txt'))
assert match and int(match[1])==16 and not d.read(R/pure['phase']/'stdout.txt')
readers=[]
for mode in ('smoke','suite'):
    c=d.call(old,m,'cleanup-public-reader-'+mode+'-r1',m.g.PY,['-I','-B',str(T/'verify_cleanup_staging_r1.py'),
        '--case',mode,'--input-json',str(R/('cleanup-native-'+mode+'-r1')/'stdout.txt'),'--reference-root',str(T)],limit=180000)
    assert not d.read(R/c['phase']/'stderr.txt')
    assert d.parse(d.read(R/c['phase']/'stdout.txt'))['status']=='PASS_INDEPENDENT_CLEANUP_STAGING_READER';readers.append(c)
for name,path in mapping.items():assert d.read(path)==d.read(R/name)
d.frozen();d.audit_classes();d.recheck(b,old,m)
expected={name:0 for name in [
 'cleanup-host-positive-r1','cleanup-host-positive-final-r1',
 'cleanup-pure-tests-r1','cleanup-jdk8-compile-r1',
 'cleanup-native-smoke-r1','cleanup-reader-smoke-r1','cleanup-native-suite-r1',
 'cleanup-reader-suite-r1','cleanup-maintained-81-r1',
 'cleanup-corrected-reader-positive-r1','cleanup-corrected-file-policy-r1',
 'cleanup-reader-control-positive-r1','cleanup-public-pure-r1',
 'cleanup-public-reader-smoke-r1','cleanup-public-reader-suite-r1']}
for name in ['cleanup-host-negative-r1','cleanup-host-negative-final-r1','cleanup-protocol-no-argument-r1',
             'cleanup-protocol-lowercase-r1']:expected[name]=1
for name in ['payload','counter','parent','clock','duplicate','oversize',
             'changed-reference','changed-oracle','existing-junction']:
    expected['cleanup-reader-control-'+name+'-r1']=2
calls=[];seen=set()
for directory in sorted(R.iterdir()):
    if directory.is_dir() and directory.name.startswith('cleanup-') and (directory/'result.json').is_file():
        assert directory.name in expected
        code=expected[directory.name]
        assert d.parse(d.read(directory/'result.json'))['exitCode']==code
        c=m.g.audit_call(directory,code);seen.add(directory.name)
        assert c['terminationProven'] and c['ownedRootAndLogsAbsent'];calls.append(c)
assert seen==set(expected) and len(calls)==28
v={'status':'PASS_CLEANUP_PUBLIC_BYTES_PURE_READERS_AND_PROCESS_AUDIT','sourceFreezeSha256':d.frozen(),
   'publicRuntimeSource':rows,'pure':pure,'pureTestCount':16,'readers':readers,'nativeNotRerunAfterCopy':True,
   'actualCallsReauthenticated':calls,'normalHostAcceptance':False,'genuineEnvelope':'UNSPENT_GATED'}
print(d.write('FINAL_PUBLIC_GATE_R1.json',v))
