from pathlib import Path
import gzip, json, subprocess, sys, statistics, hashlib

repo=Path(sys.argv[1]).resolve()
scratch=Path(sys.argv[2]).resolve()
evidence=repo/'docs/task-evidence/Q30/intermediate-optimization'
progress=json.loads((scratch/'ab-progress.json').read_text())
assert progress['complete'] and len(progress['rows'])==8
reports=[]; strict=[]; provenance={}
for row in progress['rows']:
    output=scratch/('ab-'+row['name'])
    result=json.loads((output/'result.json').read_text())
    report=json.loads(next((output/'cases').glob('*.report.json')).read_text())
    manifest=json.loads((output/'input-manifest-before.json').read_text())
    wrapper={'rows':[{'seed':row['seed'],'previewSourceDigest':manifest['sourceSha256'],'previewWebDigest':manifest['webSha256'],'report':report}]}
    receipt=output/'strict-wrapper.json'
    receipt.write_text(json.dumps(wrapper))
    command=[sys.executable,'-B',str(repo/'docs/task-evidence/Q30/normal-admission/check_coordinator.py'),str(receipt),'--seeds',row['seed']]
    if row['index']==1: command.append('--self-test')
    checked=subprocess.run(command,capture_output=True,text=True)
    assert checked.returncode==0, checked.stdout+checked.stderr
    strict.append('RUN '+row['name']+'\n'+checked.stdout.rstrip())
    assert result['outcome']=='PASS' and result['cleanup']['status']=='PASS'
    assert not result['cleanup']['ownedSurvivors']
    assert report['cold']['proofCacheHitDelta']==0 and report['cold']['proofCacheMissDelta']==1
    assert report['measurementCacheInitialSize']==0 and report['measurementCacheCleared']
    assert report['cleanupComplete'] and report['restored']
    reports.append({'run':row['name'],'arm':row['arm'],'seed':row['seed'],**wrapper['rows'][0]})
    if row['arm'] not in provenance:
        provenance[row['arm']]=manifest
        (evidence/('input-manifest-'+row['arm']+'.json.gz')).write_bytes(gzip.compress(json.dumps(manifest).encode(),mtime=0))
    else:
        assert manifest['sourceSha256']==provenance[row['arm']]['sourceSha256']
        assert manifest['webSha256']==provenance[row['arm']]['webSha256']
    raw=json.dumps(result,indent=2)
    raw=raw.replace(str(scratch).replace('\\','\\\\'),'<task-temp>')
    raw=raw.replace(str(repo).replace('\\','\\\\'),'<repo>')
    raw=raw.replace(json.dumps(str(Path.home()))[1:-1],'<user>')
    (evidence/('runner-'+row['name']+'.json')).write_text(raw+'\n')

pairs=[]
for first,second in [(0,1),(2,3),(4,5),(6,7)]:
    ai=first if reports[first]['arm']=='A' else second
    bi=second if ai==first else first
    a,b=reports[ai]['report'],reports[bi]['report']
    ap=a['cold']['owner']['diagnosticProof']; bp=b['cold']['owner']['diagnosticProof']
    proof_keys=sorted(k for k in ap if k!='elapsedMillis')
    parity={key:ap[key]==bp[key] for key in proof_keys}
    assert all(parity.values()) and a['request']==b['request']
    assert a['cold']['totalWork']==b['cold']['totalWork']
    assert a['cold']['hypothesisWork']==b['cold']['hypothesisWork']==390
    pair={'seed':reports[ai]['seed'],'controlRun':reports[ai]['run'],'optimizedRun':reports[bi]['run'],
          'requestExact':True,'proofFieldParity':parity,'workExact':True,
          'controlColdMs':a['cold']['elapsedMs'],'optimizedColdMs':b['cold']['elapsedMs'],
          'coldSavedMs':a['cold']['elapsedMs']-b['cold']['elapsedMs'],
          'controlProofMs':a['cold']['proofElapsedMs'],'optimizedProofMs':b['cold']['proofElapsedMs'],
          'proofSavedMs':a['cold']['proofElapsedMs']-b['cold']['proofElapsedMs'],
          'hostMonotonicSavedSeconds':progress['rows'][ai]['hostProcessMonotonicSeconds']-progress['rows'][bi]['hostProcessMonotonicSeconds']}
    pairs.append(pair)

summary={'status':'MEASURED_NOT_NORMAL_ACCEPTANCE','pairCount':4,'uniqueSeeds':['13','7','64'],
    'rows':progress['rows'],'pairs':pairs,'medianPairedColdSavedMs':statistics.median(p['coldSavedMs'] for p in pairs),
    'allPairsImproved':all(p['coldSavedMs']>0 and p['proofSavedMs']>0 for p in pairs),
    'maxAbsoluteHostClockDriftSeconds':max(abs(r['wallMinusMonotonicSeconds']) for r in progress['rows']),
    'seed13WithinArmColdRangesMs':{arm:max(r['coldMs'] for r in progress['rows'] if r['arm']==arm and r['seed']=='13')-min(r['coldMs'] for r in progress['rows'] if r['arm']==arm and r['seed']=='13') for arm in ['A','B']},
    'normalDeadlineMs':90000,'allOptimizedColdStillOverDeadline':all(r['coldMs']>90000 for r in progress['rows'] if r['arm']=='B'),
    'fullAcceptanceMatrix':'NOT RUN','privateMeasurementOnly':True}
(evidence/'comparison.json').write_text(json.dumps(summary,indent=2)+'\n')
(evidence/'receipts.json.gz').write_bytes(gzip.compress(json.dumps({'runs':reports}).encode(),mtime=0))
(evidence/'strict-readers.txt').write_text('\n\n'.join(strict)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k not in ['rows','pairs']},indent=2))
