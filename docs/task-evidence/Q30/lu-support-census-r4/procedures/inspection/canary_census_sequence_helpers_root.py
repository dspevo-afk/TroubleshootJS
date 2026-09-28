from pathlib import Path
import importlib.util,json,copy,hashlib
s=Path(__file__).resolve().parent
def load(name,file):
 spec=importlib.util.spec_from_file_location(name,s/file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
p=load('census_pair','validate_lu_support_census_pair_root.py')
m=load('census_meta','lu-support-census-metadata-validator.py')
t=load('census_timing','run_lu_support_census_timing.py')
for name in ['prepare_lu_support_census_sequence_root.py','run_lu_support_census_sequence_root.py','validate_lu_support_census_pair_root.py','run_lu_support_census_timing.py']:compile((s/name).read_text(encoding='utf-8'),name,'exec')
off=json.loads((s/'publication-profile-r1-01-control-10014/cases/001-publication-profile-r1-01-control-10014.report.json').read_bytes())
p.validate_report_mode(off,False,'prior baseline bytes')
assert t.actual_support_paths(off)==[]
on=copy.deepcopy(off); fixture=m._full_fixture()
for name in ['supportCensusRequested','supportCensusQuery','coldSupportCensus','warmSupportCensus']:on[name]=fixture[name]
on['cold']['supportCensus']=on['coldSupportCensus'];on['warm']['supportCensus']=on['warmSupportCensus'];on['supportCensusForRun']=on['warmSupportCensus']
p.validate_report_mode(on,True,'synthetic placement fixture')
for phase in ['cold','warm']:m.validate_report(on,phase)
projected,removed=p.strip_support_paths(on)
assert projected==off and set(removed)==set(p.SUPPORT_PATHS)
assert set(t.actual_support_paths(on))==set(p.SUPPORT_PATHS)
negative=[]
for label,mutate in [('wrong final snapshot',lambda d:d.__setitem__('supportCensusForRun',d['coldSupportCensus'])),('extra field',lambda d:d.__setitem__('unknownSupportCensus',{})),('false mode',lambda d:d.__setitem__('supportCensusRequested',False))]:
 d=copy.deepcopy(on);mutate(d)
 try:p.validate_report_mode(d,True,label)
 except p.ValidationError:negative.append(label)
 else:raise AssertionError('accepted '+label)
t.validate_case_path(t.expected_path('10014',True),'10014',True)
receipt={'status':'PASS_HELPER_SOURCE_CANARY_ONLY','metadataSelftest':m.selftest(),'rawPriorControlModeValid':True,'syntheticOnPlacementAndProjectionValid':True,'modeNegativeCases':negative,'limits':'No host/actual census data consumed; final compiled profile still required.'}
out=s/'lu-support-census-sequence-root-source-canary.json';assert not out.exists()
out.write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':receipt['status'],'metadataNegatives':receipt['metadataSelftest']['negativeCaseCount'],'modeNegatives':len(negative),'sha256':hashlib.sha256(out.read_bytes()).hexdigest()}))
