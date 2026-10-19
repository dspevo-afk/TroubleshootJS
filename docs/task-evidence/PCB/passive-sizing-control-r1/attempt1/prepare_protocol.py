"""Declare fixed control protocol/suites and seal old pose census before runtime."""
import hashlib
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
POLICY=HERE.parent/'passive-pose-policy-r1'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
cases=[('42',v) for v in (15,16,17)]+[('3',v) for v in (15,16,17)]
text=(POLICY/'routes.txt').read_text(encoding='utf-8-sig')
prefix='PCB_PASSIVE_POLICY_POSES '
poses=[json.loads(s[len(prefix):]) for s in text.splitlines() if s.startswith(prefix)]
old_cases=[key for key in cases if key[1]!=17]
bound=[next(r for r in poses if (r['seed'],r['version'])==key) for key in old_cases]
assert len(bound)==4
(HERE/'pose-baselines.json').write_text(json.dumps({'rows':bound,
 'sourceSha256':sha(POLICY/'routes.txt'),'scope':'Four published15/16 census rows only'},indent=2)+'\n')
unit=[['PcbPassivePosePolicyContractTest','PCB passive pose policy contracts '],
 ['PcbPassivePoseAdmissionContractTest','PCB passive pose admission contracts '],
 ['PcbPassivePoseSourceContractTest','PCB passive pose source bindings '],
 ['PcbPassivePoseTrialSourceContractTest','PCB passive pose trial source contracts '],
 ['PcbPassiveSizingSourceContractTest','PCB passive sizing source contracts '],
 ['PcbPassiveSizingRawContractTest','PCB passive sizing raw42 contracts '],
 ['PcbPassiveSizingRawContractTestGroup2','PCB passive sizing rawneg1022 contracts '],
 ['PcbPassiveSizingRawContractTestGroup3','PCB passive sizing raw4 contracts ']]
routes=[['PcbPassiveSizingRouteContractTest','PCB passive sizing route42 contracts '],
 ['PcbPassiveSizingRouteContractTestGroup2','PCB passive sizing route3 contracts ']]
for name,value in [('unit-suites.json',unit),('route-suites.json',routes)]:
    assert not (HERE/name).exists()
    (HERE/name).write_text(json.dumps(value,indent=2)+'\n')
protocol={'schema':'pcb-passive-sizing-control-v1','cases':cases,
 'admissionByVersion':{'15':'RB56_BOARD_NORMAL@1','16':'RB56_BOARD_NORMAL@2','17':'RB56_BOARD_NORMAL@3'},
 'policyByVersion':{'15':0,'16':1,'17':2},'density':14,
 'sourcePrefixes':{'old':'PCB_PASSIVE_POSE_SOURCE_CASE ','trial16':'PCB_PASSIVE_POLICY_INPUT ',
                   'control17':'PCB_SIZING_CONTROL_INPUT '},
 'controlSourceFields':['seed','sourceCanonical15','sourcePlanSha25617','graph17','intent17',
                      'layoutSeed17','routingSeed17','eligibleIds','densityVersion17',
                      'passivePosePolicy17','admissionIdentity17'],
 'rawPrefix':'PCB_SIZING_CONTROL_RAW ','rawFields':['seed','attempt','geometry15','geometry17',
           'evaluations15','evaluations17','demand15','demand17','courtyard15','courtyard17'],
 'rawCases':[[str(seed),attempt] for seed in (42,-1022,4) for attempt in (0,2,3,4,5)],
 'routePrefix':'PCB_SIZING_CONTROL_ROUTE ','posePrefix':'PCB_SIZING_CONTROL_POSES ',
 'poseFields':['seed','issuedAdmissionIdentity','version','top0','top90'],
 'unitReportOrder':'old14,trial16source14,control17source14,raw15; pure policy/admission emit only PASS markers',
 'routeReportOrder':'pose,route per case;42 at15/16/17 then3 at15/16/17',
 'timingScope':'Native sizing-only control; unchanged per-JVM/search/physical limits',
 'limits':'No nested choice capture or synthetic serializer sample. Six normal routes only; no added native pose/origin/fit/score/candidate work. Diagnostics only, no new accepted fitness/packing policy',
 'baselinesSha256':{name:sha(HERE/name) for name in ('accepted15-baselines.json','route-baselines.json',
                                                    'raw-baselines.json','pose-baselines.json')}}
(HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
print('Declared unit8/routes2 six-case control protocol and four old pose baselines; no native run')
