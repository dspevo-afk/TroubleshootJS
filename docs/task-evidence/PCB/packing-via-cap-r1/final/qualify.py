"""Accept only actual final-source operations and separately observed cleanup."""
import hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[4]
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
phase,=sys.argv[1:];assert phase in ('contracts','census','target','matrix','build')
assert runpy.run_path(str(HERE/'inputs.py'))['record']()==read(HERE/'candidate-inputs.json')
t=read(HERE/(phase+'-timing.json'));lpath=HERE/(phase+'-process-launch.json');l=read(lpath);c=read(HERE/(phase+'-process-cleanup.json'))
assert t['status']=='PASS' and t['phase']==phase and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None and t['terminalCleanupIncluded'] is True
assert type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0
assert c['status']=='PASS' and c['phase']==phase and type(c['actualExecExitCode']) is int and c['actualExecExitCode']==0 and c['launchSha256']==sha(lpath)
assert len(l['drivers'])==len(c['checks'])==2 and all(x['recordedBirthExited'] is True for x in c['checks'])
assert [{k:x[k] for k in ('pid','startTicks','executable')} for x in c['checks']]==l['drivers']
for side in ('before','after'):assert text(HERE/(phase+'-input-audit-'+side+'.txt')).strip()=='PASS final via-cap exact sources1482 machinery24 helpers'+('8' if phase=='census' else '6')
log=text(HERE/(phase+'.txt'))
result={'status':'PASS','phase':phase,'elapsedSeconds':t['elapsedSeconds'],'sourceSealSha256':sha(HERE/'candidate-inputs.json'),'limits':'Actual named finite final-source phase only; remaining native/build/player/publication gates separate'}
if phase=='build':
    for marker in ('Compiling module com.lushprojects.circuitjs1.circuitjs1','Compiling 5 permutations','Compile of permutations succeeded','Compilation succeeded --','Link succeeded','Linking succeeded --'):assert log.count(marker)==1,marker
    assert 'BUILD_FAILURE' not in log and 'BUILD_INFRASTRUCTURE' not in log
else:
    suites=read(HERE/'suites.json')[phase]
    assert log.count('PASS: focused current contracts; '+str(len(suites))+' Java suites. Full matrix and independent oracles NOT RUN.')==1
    assert 'CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log
    definitions=dict(re.findall(r"Name = '([^']+)'; Marker = '([^']+)'",text(ROOT/'scripts/verify-current-contracts.ps1')))
    receipt=text(HERE/(phase+'-receipt.txt'))
    for suite in suites:assert re.search(r'(?m)^PASS: '+re.escape(definitions[suite]),receipt),suite
    if phase=='census':
        # The task-owned paired reader has its own command entry point and report.
        assert runpy.run_path(str(HERE/'inputs.py'))['record']('census')==read(HERE/'census-inputs.json')
        assert read(HERE/'census-comparison.json')['status']=='PASS'
        result['pairedReaderSha256']=sha(HERE/'compare_census.py')
    elif phase=='matrix':
        rows=[s.split('|') for s in receipt.splitlines() if s.startswith('PHYSICAL_MATRIX|')]
        families=('LED_INDICATOR','DIODE_PROTECTED_INDICATOR','PARALLEL_DUAL_INDICATOR','RC_DELAY','NPN_LOW_SIDE_SWITCH','NMOS_LOW_SIDE_SWITCH','RELAY_OUTPUT','SENSOR_CONTROL','RB15_CONTROL','COMPOSED_CONTROLLED_INDICATOR','RB30_CONTROL')
        assert len(rows)==11 and tuple(r[1] for r in rows)==families
        for r in rows:
            assert len(r)==13 and r[3:5]==['20','20'] and 20<=int(r[11])<=48
            assert r[2]==('MEDIUM' if r[1] in ('COMPOSED_CONTROLLED_INDICATOR','RB30_CONTROL') else 'EASY')
        assert receipt.count('PASS: physical matrix partition coverage families=11 accepted=220 exactReplays=220 smallChildBudgetMs=180000 mediumChildBudgetMs=600000')==1
        result.update({'families':11,'accepted':220,'exactReplays':220})
    elif phase=='target':
        rows=[json.loads(s[len('Q30_CORPUS_JSON:'):]) for s in receipt.splitlines() if s.startswith('Q30_CORPUS_JSON:')]
        assert len(rows)==1;row=rows[0]
        prior=read(HERE.parents[1]/'fresh-multirail-gap-r1/native-route-assessment.json')['row']
        assert set(row)==set(prior)
        for k in ('schema','planEpoch','seed','outcome','routeOutcome','normalAdmissionOutcome','normalAdmissionReason','topologyAxis','support','partCount','contractValidated','cleanup','hypothesisCount','observationCount','routeTimingMethod','proofPhase','coldRouteOutcomes','warmRouteOutcomes','routeRejection','validationFailure'):assert row[k]==prior[k],k
        assert row['outcome']==row['normalAdmissionOutcome']=='ACCEPTED' and row['contractValidated'] is True and row['cleanup'] is True
        def clock(s):
            value,n=re.subn(r'(normal-packing@3;attempts=[01];orderings=\d+;expansions=\d+;millis=)\d+(;route=)',r'\1[wall-clock]\2',s)
            assert n==1;return value
        assert clock(row['coldRouteCanonical'])==clock(row['warmRouteCanonical'])
        assert ';disposition=SELECTED;' in row['coldRouteCanonical']
        assert log.count('Q30_CORPUS_CENSUS attempted=1 accepted=1 rejected=0 failed=0 childBudgetMs=60000')==1
        result['row']=row
        result['changedPriorFields']=[k for k in row if row[k]!=prior[k]]
assert not (HERE/(phase+'-assessment.json')).exists()
(HERE/(phase+'-assessment.json')).write_text(json.dumps(result,indent=2)+'\n')
print('PASS actual final via-cap phase '+phase)
