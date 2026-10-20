"""Report the actual bounded private chain, without a production acceptance claim."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def result():
    assessment=read(HERE/'assessment.json');assert assessment['assessmentStatus']=='PASS'
    protocol=read(HERE/'protocol.json');prefix=protocol['summaryPrefix']
    reports=[json.loads(line[len(prefix):]) for line in (HERE/'routes.txt').read_text(encoding='utf-8-sig').splitlines() if line.startswith(prefix)]
    assert [r['seed'] for r in reports]==protocol['roots']
    roots=[]
    for report in reports:
        initial=report['sourceMetrics'];initial_control=report['initialControl']['metrics'];sequence=[]
        for step in report['steps']:
            accepted=step['acceptedRowIndex']
            if accepted is None:continue
            row=step['rows'][accepted];metrics=row['compaction']['metrics']
            baselines=(step['sourceMetrics'],step['control']['metrics'],initial,initial_control)
            assert all(metrics['area']<b['area'] for b in baselines)
            assert all(metrics[k]<=b[k] for b in baselines for k in ('copperLength','vias','largestProtectedEmptyRectangleArea'))
            sequence.append({'step':step['index'],'move':row['feasibility']['move'],
                'sourceMetrics':step['sourceMetrics'],'metrics':metrics,'attempts':len(step['rows'])})
        final=report['finalMetrics'];reduction=initial['area']-final['area']
        assert len(sequence)==report['aggregate']['acceptedSteps']
        roots.append({'seed':report['seed'],'termination':report['status'],'initialMetrics':initial,
            'initialControlMetrics':initial_control,'finalMetrics':final,'areaReduction':reduction,
            'areaReductionPercent':100.0*reduction/initial['area'],'aggregate':report['aggregate'],
            'acceptedSequence':sequence,'callbackElapsedMillis':report['elapsedMillis']})
    return {'analysisStatus':'COMPLETE','scope':protocol['scope'],'assessmentSha256':sha(HERE/'assessment.json'),
        'routesLogSha256':sha(HERE/'routes.txt'),'roots':roots,
        'measurementDisposition':'USEFUL_PRIVATE_CHAIN' if any(r['acceptedSequence'] for r in roots) else 'NO_STRICT_IMPROVEMENT',
        'productionChange':'NONE','boardShrinkageAcceptance':'NOT_CLAIMED','limits':protocol['limitsDisclosure']}
if __name__=='__main__':
    value=result();p=HERE/'descent-analysis.json'
    if p.exists():assert read(p)==value,'Prior analysis differs; preserve it'
    else:p.write_text(json.dumps(value,indent=2)+'\n')
    print('COMPLETE',[(r['seed'],r['aggregate']['acceptedSteps'],r['areaReduction'],r['termination']) for r in value['roots']])
