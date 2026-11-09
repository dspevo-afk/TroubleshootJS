"""Bind independent reader to actual selected native case/argv/process evidence."""
from pathlib import Path
import argparse,hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
parser=argparse.ArgumentParser();parser.add_argument('--case',choices=('smoke','suite'),required=True)
mode=parser.parse_args().case
Q=Path(__file__).resolve().parent
p=Q/'run_export_composition_r1.py';raw=p.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='c7514ea198a9f569302bea77093a3dcd1ab36e9775ca77ee3824b39180fc9610'
cut=b'_audit_compile()\nif MODE == "suite":\n    pins.extend(_accepted_smoke())'
assert raw.count(cut)==1
m=types.ModuleType('_root_native_export_oracle');m.__file__=str(p)
saved=sys.argv
try:
    sys.argv=[str(p),'--case',mode]
    exec(compile(raw.split(cut)[0],str(p),'exec'),m.__dict__)
finally:sys.argv=saved
m._audit_compile();m._accepted_smoke()
phase=Q/('native-export-composition-'+mode+'-r1');directory=Q/('native-run-'+mode+'-r1')
gate_raw=m._read(directory/'gate.json',m.REPORT_CAP);gate=m._json(gate_raw)
input_raw=m._read(directory/'input.json',m.REPORT_CAP);run_input=m._json(input_raw)
fixture=m._empty_fixture(directory/'fixture-root')
argv=['-ea','-Xmx512m','-cp',m.classpath,m.CASE_CLASSES[mode],mode.upper(),str(fixture)]
m._request(phase,m.g.JDK/'java.exe',argv);assert run_input['argv']==argv
native_call=m.g.audit_call(phase,0);assert native_call==gate['call']
stdout=m._read(phase/'stdout.txt',m.REPORT_CAP);stdout_sha=hashlib.sha256(stdout).hexdigest()
assert stdout_sha==gate['stdoutSha256']
summary=m._summary(stdout,m.case_definitions[mode],fixture)
assert summary==gate['summary'] and not m._read(phase/'stderr.txt',m.REPORT_CAP,allow_empty=True)
case=summary['cases'][0];expected_p=len((fixture/case['name']/'op-1.jsonl').parts)
review=m._json(m._read(Q/'ROOT_SOURCE_REVIEW_R1.json',m.SOURCE_CAP))
for role,digest in review['files'].items():assert hashlib.sha256(m._read(Q/role,m.SOURCE_CAP)).hexdigest()==digest
call=m.root.call('export-composition-native-oracle-'+mode+'-r1',m.g.PY,
    ['-I','-B',str(Q/'verify_export_composition_r1.py'),'--case',mode,
     '--input-json',str(phase/'stdout.txt'),'--expected-path-entries',str(expected_p)])
reader_phase=Q/('export-composition-native-oracle-'+mode+'-r1')
assert not m._read(reader_phase/'stderr.txt',m.REPORT_CAP,allow_empty=True)
reader_raw=m._read(reader_phase/'stdout.txt',m.REPORT_CAP);reader=m._json(reader_raw)
assert reader['status']=='PASS_INDEPENDENT_EXPORT_BYTES_AND_LEDGER' and reader['name']==case['name']
assert 1<=reader['expectedR']<=32768
assert reader['expectedM']>0 if mode=='smoke' else reader['expectedM']==0
assert hashlib.sha256(m._read(phase/'stdout.txt',m.REPORT_CAP)).hexdigest()==stdout_sha
assert m._read(directory/'gate.json',m.REPORT_CAP)==gate_raw
assert m._read(directory/'input.json',m.REPORT_CAP)==input_raw
assert m._recheck(tuple(m.pins),m.jars,m.classes,m.inventory,m.tools)==(m.class_count,m.class_bytes)
m._audit_compile();m._accepted_smoke();m._empty_fixture(fixture)
result={'status':'PASS_INDEPENDENT_CURRENT_EXPORT_COMPOSITION_'+mode.upper(),
    'mode':mode,'nativeGateSha256':hashlib.sha256(gate_raw).hexdigest(),
    'nativeInputSha256':hashlib.sha256(input_raw).hexdigest(),'nativeStdoutSha256':stdout_sha,
    'expectedPathEntries':expected_p,'nativeCall':native_call,'readerCall':call,'readerSummary':reader,
    'readerStdoutSha256':hashlib.sha256(reader_raw).hexdigest(),
    'rootGateSourceSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    'allConsumedInputsReauthenticated':True,'fixtureRootEmpty':True,'genuineEnvelope':'UNSPENT_GATED'}
with (Q/('NATIVE_ORACLE_'+mode.upper()+'_R1.json')).open('xb') as out:
    out.write((json.dumps(result,indent=2)+'\n').encode('ascii'))
print(json.dumps(reader,separators=(',',':')))
