from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
repo=Path('<TASK_PATH>')
sha=lambda b:hashlib.sha256(b).hexdigest()
for name in ['solver-publication-profile-r1-native5.json','solver-publication-profile-r1-gwt5.json','solver-publication-profile-r2-canaries-summary.json']:
    d=json.loads((s/name).read_bytes());assert d['status']=='PASS' and d['exitCode']==0,name
assert 'PASS: exact maintained Test-A07Report; three negative corruption canaries' in (s/'solver-publication-profile-r2-a07-reader.log').read_text()
disabled=json.loads((s/'solver-publication-profile-r2-canaries/cases/002-solver-publication-profile-r2-canaries-disabled.report.json').read_bytes())
assert disabled['status']=='BLOCKED' and disabled['phase']=='normal-catalog-disabled'
for k in ['normalAdmission','normalCatalogEnabled','generationStarted','snapshotCaptured','liveOwnerMutation','cleanupRequired']:assert disabled[k] is False,k
files=['solver-publication-profile-r1-measurement-plan.json','solver-publication-profile-reviewed-inputs.json',
 'solver-publication-profile-r1-preparation/source-audit.json','scratch/pivot-lower-fusion-r2-preparation/source-audit.json',
 'run_publication_profile_timing.py','validate_publication_profile_pair_root.py',
 'validate_current_q30_timing_pair.py','solver-publication-profile-metadata-validator.py',
 'solver-publication-profile-r1-native5.json','solver-publication-profile-r1-gwt5.json',
 'solver-publication-profile-r2-canaries-summary.json','solver-publication-profile-r2-canaries/result.json',
 'solver-publication-profile-r2-a07-reader.log','cpu-profile-10ms-r1-fixture-preflight.json']
repo_files=['tests/browser/compiled_attribute_acceptance.py','docs/task-evidence/Q30/scale-plan-4/check_coordinator.py','docs/task-evidence/Q30/scale-plan-4/check_d01.py']
for name in ['run_publication_profile_r1_sequence_root.py','run_publication_profile_timing.py','validate_publication_profile_pair_root.py','solver-publication-profile-metadata-validator.py']:
    compile((s/name).read_text(),name,'exec')
def entries(root,names):return [{'path':n,'sha256':sha((root/n).read_bytes()),'bytes':(root/n).stat().st_size} for n in names]
out=s/'publication-profile-r1-execution-binding.json';assert not out.exists()
document={'status':'FROZEN_READY_FOR_PRIVATE_MEASUREMENT','driverSha256':sha((s/'run_publication_profile_r1_sequence_root.py').read_bytes()),
 'files':entries(s,files),'repoFiles':entries(repo,repo_files),
 'gates':'Native5, actual JDK8/GWT5, compiled A07/disabled normal, strict A07 plus three corruption canaries PASS. Baseline gates reused only under all-source/runtime equality audit.',
 'failurePreserved':'r1 canary invocation supplied relative output path: exit2 before launch, no host receipt. Corrected absolute path r2 passes; no app or gate source changed.',
 'limits':'Private profile, no normal acceptance, no source integration. Metadata and pair readers are reviewed but await actual profile report validation.'}
out.write_bytes((json.dumps(document,indent=2)+'\n').encode())
print(json.dumps({'bindingSha256':sha(out.read_bytes()),'driverSha256':document['driverSha256']}))
