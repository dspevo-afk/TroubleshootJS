import json
from pathlib import Path
here=Path(__file__).resolve().parent
root=here.parents[4]
def read(name):return json.loads((here/name).read_text(encoding='utf-8-sig'))
native=read('final-native-timing.json');gwt=read('final-gwt-timing.json')
checks=['post-native-input-audit.json','post-gwt-input-audit.json','post-player-input-audit.json','post-player-provenance-audit.json','final-metric-assessment.json','desktop-launch-audit.json','narrow-launch-audit.json','download-cleanup.json','resources-cleanup.json','browser-cleanup.json']
for name in checks:
    if read(name)['status']!='PASS':raise ValueError(name)
flags=['ownerRestored','candidateDisposed','temporaryMeterClean','debugRestored','coordinatorReleased','playerSessionReleased','playerBridgeReleased']
for name in ['desktop-final-report.json','narrow-final-report.json']:
    r=read(name)
    if r['status']!='PASS' or r['cleanupPending'] or not r['ordinaryCustomerRetestPassed'] or not all(r[f] for f in flags):raise ValueError(name)
if native['status']!='PASS' or gwt['status']!='PASS':raise ValueError('native/build')
desktop=read('desktop-final-report.json');narrow=read('narrow-final-report.json');restore=narrow['sessionRestores'][0]
summary=dict(status='ACCEPTED13_FRESH_REQUALIFICATION_PASS',codeCommit='96bd1b1414866733927f93bdf0b039bd09cae204',native49=native,gwtFive=gwt,desktop=desktop,narrow=narrow,limits='Selected49, finite ten seeds/reference host/private qualification only; no normal advanced/HARD/allseed/modesthost/manufacturing release',publication='Accepted code already pushed; documentation/evidence commit follows',rejected14='Strict metricFAIL allten unchanged; no14productioncode published',resources='All owned preview/tab/viewport/download/scratch cleaned; original5untracked and sharedtools preserved')
(here/'final-gates.json').write_text(json.dumps(summary,indent=2)+'\n')
readme_path=here/'README.md'
readme_text=readme_path.read_text(encoding='utf-8')
readme_text=readme_text.split('\nFresh319×670 cold54 passed',1)[0]
readme_path.write_text(readme_text,encoding='utf-8')
with (here/'README.md').open('a',encoding='utf-8') as out:
    out.write(f"\nFresh319×670 cold54 passed in{narrow['generationMillis']/1000:.3f}s/{narrow['generationWork']}. The committed saved12 file restored through actual FileReader in{restore['elapsedMillis']/1000:.3f}s/{restore['work']}, with a fresh owner and prior owner retired. OFF and one loose part survived; retest/Close passed in{narrow['wallMillis']/1000:.3f}s with all seven cleanup flags. Five fresh unedited captures were inspected; browser warnings/errors are empty. Post-player1,440-input and five-field provenance audits passed; all owned resources were cleaned. This is the final restored13 acceptance boundary.\n")
text=(here/'README.md').read_text();text=text.replace('Fresh narrow restoration and final cleanup are pending.','Fresh narrow restoration and final cleanup passed.')
(here/'README.md').write_text(text)
report=root/'docs/CODEX_TASK_REPORT.md';old=report.read_text();prior=old[old.index('# Previous checkpoint: private13'):]
head=f'''# Current checkpoint: private13 accepted and published; later trial rejected

2026-10-14. Code HEAD `96bd1b1414866733927f93bdf0b039bd09cae204`, normally
pushed to `origin/codex/q60-faulted-profile-counts`; exact remote SHA verified.
No main merge or email. Final evidence is recorded in this checkpoint's containing commit; the handoff records its verified SHA.

The general candidate1 nearest-vacancy preference reduces root42 area8.57%,
largest protected void20.98%, copper2.79% and vias48→45. The other nine roots
remain exact12. Saved1–12 retain their layouts; publicalpha5/model1/epoch16 stay.
Private14 actual pad-cost trial was rejected: focused4 PASS83.175001s, allten
geometry/area/void/copper/vias unchanged. Its patch, fixtures and failures remain;
no14 GWT/browser/final52 or production publication occurred.

Git checkout changed raw EOL fingerprints during restoration. Original raw audit
FAIL and failed recovery remain; canonical source matches accepted13 Git content.
Fresh restored13 qualification: native49 PASS{native['elapsedIncludingCleanupSeconds']:.7f}s and
actual JDK8/GWT5 PASS{gwt['elapsedIncludingCleanupSeconds']:.7f}s, including terminal cleanup.
New frozen1,440 inputs and five served identity fields match. Entire compiled web
tree matches the first13 build; the earlier60 repair proof is retained only at
that audited unchanged-web boundary. Fresh54 desktop cold73.883s/622, pad4.996V,
chip menu/pinout/both faces, unrepairedFAIL, guarded Shop55/RDA repair/customerPASS,
actual Save13/FileReader13 restore78.141s/622, OFF+one part/retest/ClosePASS385.978s/all7.
Fresh319×670 cold{narrow['generationMillis']/1000:.3f}s/{narrow['generationWork']}; committed saved12 actualFileReader
{restore['elapsedMillis']/1000:.3f}s/{restore['work']}, fresh/prior-retired owners, OFF+one part, customer/Close
PASS{narrow['wallMillis']/1000:.3f}s/all7. Five fresh inspected captures, empty browser warning/error logs,
post-player byte/provenance audits and exact owned cleanup PASS. No owned preview,
verifier, tab, viewport override, download or scratch remains. Shared tools and
all five original untracked entries remain; unrelated Desktop work is untouched.

Evidence: PCB/nearest-vacancy-preference-r1 and PCB/pad-cost-vacancy-r1, with fresh
final proof in its restored13-requalification-r1 subfolder. Root reviewed the
integrated diff; separate source/evidence reviews did not run runtime gates.
Finite selected cases do not release all-seed, normal advanced/HARD, modest-host,
optimality or manufacturing gates. Next unstarted PCB work: independent domain/
occupancy overlays and a literal two-domain fixture before any axis-aware layout
change. Current receipts do not prove the cause or packability of every pocket.

'''
report.write_text(head+prior)
roadmap=root/'docs/ROADMAP.md';text=roadmap.read_text();text=text.replace('Fresh verification follows checkout EOL fingerprint changes.','Fresh49/JDK8GWT and desktop/narrow actual save-load/customer/cleanup checks passed after checkout EOL fingerprint changes.')
roadmap.write_text(text)
print('PASS final acceptance records and checkpoint updated')
