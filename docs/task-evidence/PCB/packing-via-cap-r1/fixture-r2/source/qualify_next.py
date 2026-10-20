"""Qualify real early operations; GO is separate from successful observation."""
import hashlib,json,math,re,runpy,sys
from pathlib import Path
if not __debug__:raise RuntimeError('Assertion-enabled Python required')
HERE=Path(__file__).resolve().parent
phase=sys.argv[1];assert phase in ('fixture-r2','target-run')
A=HERE/phase;prefix='fixture-' if phase=='fixture-r2' else ''
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def text(p):return p.read_text(encoding='utf-8-sig')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert runpy.run_path(str(HERE/'next_inputs.py'))['record'](phase)==read(A/'inputs.json')
t=read(A/(prefix+'timing.json'));c=read(A/(prefix+'process-cleanup.json'));l=read(A/(prefix+'process-launch.json'))
assert t['status']=='PASS' and type(t['exitCode']) is int and t['exitCode']==0 and t['failure'] is None and t['terminalCleanupIncluded'] is True
assert type(t['elapsedSeconds']) in (int,float) and math.isfinite(t['elapsedSeconds']) and t['elapsedSeconds']>0
assert c['status']=='PASS' and type(c['actualExecExitCode']) is int and c['actualExecExitCode']==0 and c['launchSha256']==sha(A/(prefix+'process-launch.json'))
assert len(l['drivers'])==len(c['checks'])==2 and all(x['recordedBirthExited'] is True for x in c['checks'])
assert [{k:x[k] for k in ('pid','startTicks','executable')} for x in c['checks']]==l['drivers']
for side in ('before','after'):assert text(A/(prefix+'input-audit-'+side+'.txt')).strip()=='PASS frozen via-cap '+phase+' source1482 machinery24 helpers10'
log=text(A/('fixture.txt' if phase=='fixture-r2' else 'native.txt'))
receipt=text(A/('fixture-receipt.txt' if phase=='fixture-r2' else 'receipt.txt'))
assert 'CURRENT_CONTRACT_FAILURE:' not in log and 'CURRENT_CONTRACT_CLEANUP:' not in log
assert log.count('PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.')==1
result={'status':'PASS_ACTUAL_OPERATION','phase':phase,'elapsedSeconds':t['elapsedSeconds'],'inputSealSha256':sha(A/'inputs.json')}
if phase=='fixture-r2':
    assert len(re.findall(r'(?m)^PASS: P07 two-layer contracts ',receipt))==1
    n='tests/contracts/P07TwoLayerContractTest.java'
    assert set(re.findall(r'"(?:\\.|[^"\\])*"',text(HERE/'baseline-source'/n)))<=set(re.findall(r'"(?:\\.|[^"\\])*"',text(A/'candidate-source'/n)))
    result['limits']='Actual maintained P07 fixture/all declared test compilation; no full matrix/build/player claim'
else:
    negative=read(A/'invalid-seed-canary.json')
    assert negative['status']=='PASS_REJECTED_INVALID_SEED' and negative['actualAdapterExitCode']==1 and negative['privateLogBytes']>0
    rows=[json.loads(x[len('PCB_PACKING_METRIC_EVIDENCE '):]) for x in receipt.splitlines() if x.startswith('PCB_PACKING_METRIC_EVIDENCE ')]
    assert len(rows)==1;row=rows[0]
    assert row['schema']=='packing-metric-evidence@1' and row['seed']=='-5776064669694490064'
    assert len(re.findall(r'(?m)^PASS: fresh RB30 gap diagnostic seed=-5776064669694490064 assertions=376\r?$',receipt))==1
    old=read(HERE.parent/'packing-metric-evidence-r1/diagnostic-final-r4/assessment.json')['fullExport']
    for n in ('seed','boardId','planCanonical','plan','nets','powerInputs'):assert row[n]==old[n],n
    g=row['geometryIdentity'];og=old['geometryIdentity']
    for n in ('logicalBoardFingerprintBefore','logicalBoardFingerprintAfter','componentIds','padIds','netIds'):assert g[n]==og[n],n
    assert g['logicalBoardFingerprintBefore']==g['logicalBoardFingerprintAfter']=='b9962dbbaf516f192a190d6806f26e3245822582f0596287ea6f18f1be7951d0'
    assert g['geometryFingerprintEqual'] is True and g['logicalBoardFingerprintEqual'] is True and g['geometryFingerprintBefore']==g['geometryFingerprintAfter']
    assert len(row['layout']['components'])==38 and sum(len(x['terminals']) for x in row['layout']['components'])==92 and len(row['nets'])==26 and len(row['powerInputs'])==4
    for n in ('geometry','routingGeometry','routeQuality','traceClearance','p07Rules','normalMediumAdmission','access'):assert row['validation'][n]=='PASS',n
    probe=row['route']['packingProbe'];e=probe['metricEvidence'];before=e['baseline'];after=e['candidate']
    assert probe['canonical'].startswith('normal-packing@3;')
    assert before=={'area':7520448,'copper':29840,'vias':34,'protectedVoid':2270400}
    predicates=None
    if e['state']=='COMPARED':
        assert after is not None and e['unavailableReason'] is None
        predicates={'areaStrictlySmaller':after['area']<before['area'],'copperNonincreasing':after['copper']<=before['copper'],'viasNonincreasing':after['vias']<=before['vias'],'protectedVoidNonincreasing':after['protectedVoid']<=before['protectedVoid']}
    go=predicates is not None and all(predicates.values())
    assert (probe['disposition']=='SELECTED')==go
    expected=after if go else before
    assert row['packingMetrics']['available'] is True and {k:row['packingMetrics'][k] for k in expected}==expected
    result.update({'decision':'GO' if go else 'NO_GO','metricEvidence':e,'strictPredicates':predicates,'fullExport':row,'limits':'Actual owner observation/physical validation/unchanged logical declarations. No final admission matrix, electrical player or GWT proof; NO_GO requires verified owned-source rollback.'})
assert not (A/'assessment.json').exists()
(A/'assessment.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS actual operation '+phase+(' decision='+result['decision'] if 'decision' in result else ''))
