"""Measure all matched results; a zero-improvement outcome is not a failed census."""
from collections import Counter
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def result():
    assessment=read(HERE/'assessment.json');assert assessment['assessmentStatus']=='PASS'
    protocol=read(HERE/'protocol.json');prefix=protocol['summaryPrefix']
    reports=[json.loads(line[len(prefix):]) for line in (HERE/'routes.txt').read_text(encoding='utf-8-sig').splitlines() if line.startswith(prefix)]
    assert [r['seed'] for r in reports]==['5','2']
    assert reports[0]['rows']==[] and len(reports[1]['rows'])==31
    selected=[];outcomes=[];rejected=Counter();strict=0;area_reductions=0;valid=0
    for report in reports:
        source=report['sourceMetrics'];control=report['control']['metrics']
        assert report['newRouteRequests']==0 and report['selectedMutation'] is False
        selected.append({'seed':report['seed'],'source':source,'control':control})
        for row in report['rows']:
            feasibility=row['feasibility'];move=feasibility['move'];trial=row['compaction']
            if trial is None:
                assert feasibility['disposition']=='PHYSICAL_REJECTED'
                rejected[(feasibility['stage'],feasibility['reason'])]+=1
                outcomes.append({'move':move,'disposition':'PHYSICAL_REJECTED','stage':feasibility['stage'],'reason':feasibility['reason'],'compaction':None})
                continue
            assert feasibility['disposition']=='PHYSICAL_VALIDATED';valid+=1
            metrics=trial['metrics']
            smaller=all(metrics['area']<baseline['area'] for baseline in (source,control))
            better=smaller and all(metrics[k]<=baseline[k] for k in ('copperLength','vias','largestProtectedEmptyRectangleArea') for baseline in (source,control))
            assert trial['strictImprovement'] is better
            strict+=int(better);area_reductions+=int(smaller)
            outcomes.append({'move':move,'disposition':'PHYSICAL_VALIDATED','compacted':trial['compacted'],
                'metrics':metrics,'areaBelowBoth':smaller,'strictImprovement':better})
    assert valid+sum(rejected.values())==31
    return {'analysisStatus':'COMPLETE','scope':protocol['scope'],
        'assessmentSha256':sha(HERE/'assessment.json'),'routesLogSha256':sha(HERE/'routes.txt'),
        'sourceAndControl':selected,'independentMoves':31,'physicallyValidated':valid,
        'physicallyRejected':31-valid,'areaBelowSourceAndControl':area_reductions,
        'strictImprovements':strict,
        'measurementDisposition':'USEFUL_PRIVATE_RESULTS' if strict else 'NO_STRICT_IMPROVEMENT',
        'rejectionCounts':[{'stage':stage,'reason':reason,'count':count} for (stage,reason),count in sorted(rejected.items())],
        'outcomes':outcomes,'winnerSelected':False,'productionChange':'NONE',
        'boardShrinkageAcceptance':'NOT_CLAIMED','limits':protocol['limits']}
if __name__=='__main__':
    value=result();p=HERE/'compaction-analysis.json'
    if p.exists():assert read(p)==value,'Prior analysis differs; preserve it'
    else:p.write_text(json.dumps(value,indent=2)+'\n')
    print('COMPLETE',value['physicallyValidated'],'physical valid;',value['strictImprovements'],'strict matched improvements; no selection')
