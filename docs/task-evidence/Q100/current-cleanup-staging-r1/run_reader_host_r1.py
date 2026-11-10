from pathlib import Path
import json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent;p=R/'run_cleanup_gates_r1.py'
d=types.ModuleType('_corrected_reader_host_driver');d.__file__=str(p)
exec(compile(p.read_bytes(),str(p),'exec',dont_inherit=True,optimize=0),d.__dict__)
b,old,m=d.dependencies()
original=R.parent/'tsj-q100-r7-cleanup-staging-7c129905f6f44c04bcf3a43441c69c4d'
calls=[]
c=d.call(old,m,'cleanup-corrected-reader-positive-r1',m.g.PY,['-I','-B',str(R/'verify_cleanup_staging_r1.py'),
 '--case','smoke','--input-json',str(original/'cleanup-native-smoke-r1/stdout.txt'),'--reference-root',str(R)],limit=180000)
assert d.parse(d.read(R/c['phase']/'stdout.txt'))['status']=='PASS_INDEPENDENT_CLEANUP_STAGING_READER';calls.append(c)
c=d.call(old,m,'cleanup-corrected-file-policy-r1',m.g.PY,['-I','-B',str(R/'read_policy_canary_r1.py')])
v=d.parse(d.read(R/c['phase']/'stdout.txt'));assert v['status']=='PASS_REAL_STABLE_AND_CHANGED_FILE_READER_CANARIES';calls.append(c)
for call in calls:assert not d.read(R/call['phase']/'stderr.txt')
d.recheck(b,old,m)
print(d.write('READER_HOST_GATE_R3.json',{'status':'PASS_CORRECTED_REAL_READER_AND_FILE_MUTATION_CONTROLS','calls':calls,'filePolicy':v,'genuineEnvelope':'UNSPENT_GATED'}))
