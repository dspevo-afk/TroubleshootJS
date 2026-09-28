from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
plan=s/'solver-publication-profile-r1-measurement-plan.json'
assert not plan.exists()
audit=s/'solver-publication-profile-r1-preparation/source-audit.json'
canary=json.loads((s/'solver-publication-profile-r2-canaries/result.json').read_bytes())
assert canary['outcome']=='PASS' and canary['cleanup']['status']=='PASS'
base=json.loads((s/'cpu-profile-10ms-r1-measurement-plan.json').read_bytes())
document={'schema':1,'status':'PREDECLARED','purpose':'PRIVATE_SOLVER_PUBLICATION_COST_PROFILE',
 'seed':'10014','requestedPackageCount':40,'productionAcceptance':False,
 'sourceAuditSha256':sha(audit.read_bytes()),'baseSourceAuditSha256':base['sourceAuditSha256'],
 'source':{'control':'scratch/pivot-lower-fusion-r2-preparation/candidate','profile':'solver-publication-profile-r1-preparation/candidate'},
 'runtime':{'control':{'sha256':base['runtimeSha256'],'fileCount':base['runtimeFiles']},
            'profile':{'sha256':canary['inputAudit']['beforeSha256'],'fileCount':canary['inputAudit']['fileCountBefore']}},
 'applicationQuery':'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed=10014',
 'profileQuery':'tsjQ30PublicationProfile=true',
 'sampleInterval':512,'maxRetainedDurationsPerPhase':64,
 'sequence':[{'label':'publication-profile-r1-01-control-10014','profile':False},
             {'label':'publication-profile-r1-02-profile-10014','profile':True},
             {'label':'publication-profile-r1-03-control-10014','profile':False}],
 'requirements':['One fresh owned browser per row; unchanged frozen source/runtime before and after.',
   'Private measurement cache cleared before cold; warm reported separately; no normal cache reuse.',
   'Both complete report parity comparisons and unchanged strict readers/corruptions must pass.',
   'Profile metadata and duplicate snapshots validated before removing allowlisted profile fields in comparison copies.',
   'Preserve every candidate, hypothesis, settling step, physical admission, service/repair/retest and cleanup.',
   'Stop on source/host/reader/cleanup failure and preserve original failed evidence.'],
 'interpretation':'Selected performance.now intervals are observed sums, not exact total costs. 0.1ms timer quantization and periodic sampling may bias small phase estimates. OFF/ON/OFF bounds observed disturbance but does not isolate compiler-code-shape versus timing overhead. One seed provides ranking only, no population performance or production acceptance.',
 'limits':{'normalMaxJobMillis':90000,'maxJobSteps':640,'maxUnitMillis':5000,'privateMeasurementMillis':300000}}
plan.write_bytes((json.dumps(document,indent=2)+'\n').encode())
raw=(s/'run_timing.py').read_bytes()
assert sha(raw)=='a7ef67c63397a28c1781b40746507fc0fa129f030742b8c6201350f099a9765f'
old=b'tsjQ30KernelProfile=true';new=b'tsjQ30PublicationProfile=true'
assert raw.count(old)==1
runner=s/'run_publication_profile_timing.py';assert not runner.exists()
runner.write_bytes(raw.replace(old,new))
print(json.dumps({'planSha256':sha(plan.read_bytes()),'timingAdapterSha256':sha(runner.read_bytes())}))
