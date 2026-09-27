from pathlib import Path
import json, subprocess, sys, time, datetime

repo=Path(sys.argv[1]).resolve()
scratch=Path(sys.argv[2]).resolve()
order=[('B','13'),('A','13'),('A','7'),('B','7'),('B','64'),('A','64'),('A','13'),('B','13')]
records=[]
for index,(arm,seed) in enumerate(order,1):
    name=f'{index:02d}-{arm}{seed}'
    output=scratch/('ab-'+name)
    target=repo if arm=='B' else scratch/'control-build'
    case={'name':name,'path':f'circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed={seed}',
          'stateAttribute':'data-tsj-q30-coordinator-state','expectedState':'PASS:complete',
          'reportAttribute':'data-tsj-q30-coordinator-report','timeoutSeconds':600}
    spec=scratch/('spec-'+name+'.json')
    spec.write_text(json.dumps({'cases':[case]},indent=2)+'\n')
    began=time.monotonic(); wall=time.time()
    with (scratch/('ab-'+name+'.log')).open('w') as log:
        proc=subprocess.run([sys.executable,'-B',str(repo/'tests/browser/compiled_attribute_acceptance.py'),str(target),str(output),str(spec)],stdout=log,stderr=subprocess.STDOUT)
    duration=time.monotonic()-began
    record={'index':index,'name':name,'arm':arm,'seed':seed,'exitCode':proc.returncode,
            'hostProcessMonotonicSeconds':duration,'wallMinusMonotonicSeconds':(time.time()-wall)-duration}
    if proc.returncode==0:
        result=json.loads((output/'result.json').read_text())
        report=json.loads(next((output/'cases').glob('*.report.json')).read_text())
        manifest=json.loads((output/'input-manifest-before.json').read_text())
        record.update({'runnerOutcome':result['outcome'],'coldMs':report['cold']['elapsedMs'],
            'proofMs':report['cold']['proofElapsedMs'],'routingMs':report['cold']['routingElapsedMs'],
            'warmMs':report['warm']['elapsedMs'],'units':report['cold']['totalWork'],
            'proofUnits':report['cold']['hypothesisWork'],'sourceDigest':manifest['sourceSha256'],
            'webDigest':manifest['webSha256'],'processAndServerCleanup':result['cleanup']})
    records.append(record)
    (scratch/'ab-progress.json').write_text(json.dumps({'predeclaredOrder':order,'rows':records,'complete':index==len(order) and proc.returncode==0},indent=2)+'\n')
    print(json.dumps(record),flush=True)
    if proc.returncode:
        raise SystemExit(proc.returncode)
print('PASS: eight fresh-process paired measurements completed; attribution/strict proof analysis remains separate',flush=True)
