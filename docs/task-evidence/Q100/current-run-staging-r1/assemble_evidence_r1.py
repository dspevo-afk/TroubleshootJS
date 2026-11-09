"""Build sanitized evidence from actual accepted receipts; no runtime invocation."""
from pathlib import Path
import argparse,hashlib,json,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',type=Path,required=True);a=p.parse_args()
F=a.repo;E=F/'docs/task-evidence/Q100/current-run-staging-r1';T=F/'tests/qualification/rb100-layout/current-run-staging-r1'
def digest(raw):return hashlib.sha256(raw).hexdigest()
def read(name):return json.loads((R/name).read_bytes())
def write(path,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii');path.write_bytes(raw);return digest(raw)
def info(name):
    raw=(R/name).read_bytes();return {'name':name,'bytes':len(raw),'sha256':digest(raw)}
final=read('FINAL_PUBLIC_GATE_R1.json');assert final['status']=='PASS_FINAL_PUBLIC_RUNTIME_BYTES_PURE_READER_AND_PROCESS_AUDIT'
assert len(final['actualCallsReauthenticated'])==20
compile_gate=read('COMPILE_GATE_R1.json');native=read('STAGING_GATE_R1.json');reg=read('REGRESSION_GATE_R1.json')
assert reg['summary']['cases']==81 and reg['summary']['assertions']==12089
assert native['summary']['caseCount']==9
gate_names=['HOST_GATE_R2.json','PURE_GATE_R1.json','REGRESSION_GATE_R1.json','STAGING_GATE_R1.json','READER_CONTROLS_GATE_R1.json','FINAL_PUBLIC_GATE_R1.json']
for name in gate_names:
    raw=(R/name).read_bytes();assert read(name)['status'].startswith('PASS');(E/name).write_bytes(raw)
(E/'ACTUAL_STAGING_STDOUT_R1.json').write_bytes((R/'run-staging-suite-r1/stdout.txt').read_bytes())
(E/'ACTUAL_REGRESSION_STDOUT_R1.json').write_bytes((R/'run-input-81-regression-r1/stdout.txt').read_bytes())
summary={k:v for k,v in compile_gate.items() if k not in ('dependencies',)}
deps_raw=(json.dumps(compile_gate['dependencies'],separators=(',',':'))+'\n').encode('ascii')
summary['privateFullCompileGateSha256']=digest((R/'COMPILE_GATE_R1.json').read_bytes())
summary['dependencyInventorySha256']=digest(deps_raw)
summary['dependencyClassCount']=len(compile_gate['dependencies'])
summary['dependencyClassBytes']=sum(x['bytes'] for x in compile_gate['dependencies'])
write(E/'COMPILE_SUMMARY_R1.json',summary)
sourcepins={'status':'PASS_CURRENT_RUN_INPUT_AND_PREBODY_STAGING_SOURCE','baseCommit':'a8273fbab8e2744a9adde2e47eb05662e846285c',
 'sourceFreezeSha256':final['sourceFreezeSha256'],'source':final['publicRuntimeSource'],
 'nativeProducerCalls':0,'normalHostAcceptance':False,'genuineEnvelope':'UNSPENT_GATED'}
write(T/'ACCEPTED_SOURCE_R1.json',sourcepins)
write(F/'tests/qualification/rb100-layout/native-v2-input-setup-r1/ACCEPTED_SOURCE_R2.json',sourcepins)
history=R.parent/'tsj-q100-run-input-policy-32333695d1e94cc1881126c7049e2f57'/'source-history-r1'
historyrows=[]
for q in sorted(history.iterdir()):
    if q.is_file():
        raw=q.read_bytes();historyrows.append({'name':q.name,'bytes':len(raw),'sha256':digest(raw)})
write(E/'SOURCE_HISTORY_R1.json',{'status':'PRESERVED_PRIVATE_PRE_NATIVE_SOURCE_HISTORY','files':historyrows,
 'notes':['authoring sentinel assertion failure corrected before component runtime',
          'draft schema requested vector and draft canary source-review issues corrected before component runtime',
          'two missing terminal LF characters corrected before successful Source freeze',
          'metadata-orchestration JS parse failure made no nested tool call; incorrect status wildcard counts withdrawn and corrected'],
 'nativeFailure':'Separate failed-candidate-r1 packet remains FAIL, never reclassified'})
proof={'status':'PASS_CORRECTED_SYNTHETIC_RUN_INPUT_AND_PREBODY_COHORT_STAGING',
 'baseCommit':'a8273fbab8e2744a9adde2e47eb05662e846285c','sourceFreezeSha256':final['sourceFreezeSha256'],
 'contractSha256':digest((R/'CONTRACT_R1.md').read_bytes()),'fixtureReferenceSha256':digest((R/'FIXTURE_REFERENCE_R1.json').read_bytes()),
 'gates':[info(x) for x in gate_names],'privateCompileGate':info('COMPILE_GATE_R1.json'),
 'compile':{'freshSources':5,'freshClasses':compile_gate['classCount'],'freshClassBytes':compile_gate['classBytes'],
    'dependencyClasses':len(compile_gate['dependencies']),'dependencyClassBytes':summary['dependencyClassBytes'],
    'removedOldClasses':len(compile_gate['removedOldClasses']),'allMajor51':True,'noDuplicateOrShadowClasses':True,
    'acceptedCompileReauthenticated':{'sourceCount':863,'classCount':2486,'classBytes':11953557},
    'dependencyCompileReused':True,'call':compile_gate['call']},
 'regression':reg['summary'],'nativeStagingCases':9,'pureTests':{'private':12,'publicFresh':12},
 'reader':{'privateActualOutputPass':True,'publicActualOutputPass':True,'controlPositive':1,'controlExpectedExit2':8,
    'negativeNames':['payload','counter','parent','clock','duplicate','oversize','changed-reference','existing-junction']},
 'protocolExpectedExit1':2,'realHostPositiveAndNegative':True,
 'debits':{'setup':[10007,478,3],'stage':[264,125,64],'combined':[10271,603,67],'closedGroups':2},
 'actualCallsReauthenticated':20,'processesTerminated':True,'ownedRootsAndLogsAbsent':True,
 'isolatedProcessCleanupDurationMs':None,'helperOwnedOsResources':0,'finishAfterFailure':False,
 'preservedFailure':read('PRESERVED_NATIVE_FAILURE_R1.json'),
 'sourceReview':{'rootIntegrated':True,'independentHelperReview':'PASS_SOURCE_ONLY_TWO_HELPERS',
    'independentIndexedMutationReview':'PASS_SOURCE_ONLY_12_INDEXED_MUTATIONS_AFTER_FIX',
    'independentNativeRun':'NOT_RUN','unchangedPrepareMethodBody':'SOURCE_REAUTHENTICATED_NOT_RERUN'},
 'normalAssembly':'CLOSED','genuineEnvelope':'UNSPENT_GATED','nativeProducerCalls':0,'originalArmFabrications':0,
 'armResultFabrications':0,'graphs':0,'sessions':0,'routingRuns':0,'normalHostAcceptance':False,
 'productionChanges':False,'gwtBuild':'NOT_APPLICABLE','visiblePlayer':'NOT_APPLICABLE','ci':'NOT_RUN',
 'electricalPopulation':'NOT_RENEWED','playable100':0,'beta':'HELD','full30':'EXCLUDED',
 'hardPsychotic':'UNAVAILABLE_UNRUN','protectedUnreadBoundary':'UNCERTIFIED','oldNormal20':'HISTORICAL_READ_NO_REREAD',
 'budgetsSeedsEpochsOracles':'UNCHANGED','next':'R7 scalar-to-cleanup body handoff, then whole-result/full-D/actual-host acceptance'}
pin=write(E/'PROOF_R1.json',proof)
print(json.dumps({'status':proof['status'],'proofSha256':pin,'freshClasses':compile_gate['classCount'],'freshBytes':compile_gate['classBytes']}))
