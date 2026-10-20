"""Describe selected-envelope projections; never claim a legal packing move."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def analyze():
    receipt=HERE/'npn/receipt.txt'
    rows=[json.loads(s.removeprefix('PCB_SPARSE_NORMAL_DIAGNOSTIC ')) for s in receipt.read_text(encoding='utf-8-sig').splitlines() if s.startswith('PCB_SPARSE_NORMAL_DIAGNOSTIC ')]
    assert len(rows)==1
    row,=rows;layout=row['layout'];parts=layout['components'];outline=layout['outline']
    assert row['replayId']=='tsj-alpha/6/EASY/NPN_LOW_SIDE_SWITCH/380469407799690561'
    axes={}
    for axis,i in (('x',0),('y',1)):
        intervals=sorted((p['accessEnvelope'][i],p['accessEnvelope'][i]+p['accessEnvelope'][i+2],p['componentId']) for p in parts)
        low=min(a for a,b,n in intervals);high=max(b for a,b,n in intervals)
        merged=[]
        for a,b,n in intervals:
            if not merged or a>merged[-1][1]:merged.append([a,b])
            else:merged[-1][1]=max(merged[-1][1],b)
        gaps=[{'start':a[1],'end':b[0],'width':b[0]-a[1]} for a,b in zip(merged,merged[1:])]
        copper=[(point[i]-t['clearanceGuard'],point[i]+t['clearanceGuard']) for t in layout['traces'] for point in t['points']]
        axes[axis]={'envelopeMin':low,'envelopeMax':high,'minOwners':[n for a,b,n in intervals if a==low],
                    'maxOwners':[n for a,b,n in intervals if b==high],
                    'outlineBeforeEnvelope':low-outline[i],'outlineAfterEnvelope':outline[i]+outline[i+2]-high,
                    'mergedCompleteEnvelopeProjections':merged,'projectionGapsBeforeCopper':gaps,
                    'copperGuardBoundingMin':min(a for a,b in copper),'copperGuardBoundingMax':max(b for a,b in copper)}
    measured=row['packingProbe']['metricEvidence'];a=measured['baseline'];b=measured['candidate']
    assert measured['state']=='COMPARED' and a['area']==b['area'] and row['packingProbe']['disposition']=='KEPT'
    return {'status':'PASS_DESCRIPTIVE_SELECTED_ENVELOPE_ANALYSIS','receiptSha256':sha(receipt),
            'helperSha256':sha(Path(__file__)),'geometrySha256':row['identity']['selectedGeometrySha256Before'],
            'outline':outline,'axes':axes,'packingBaseline':a,'packingCandidate':b,
            'copperReductionPercent':100*(a['copper']-b['copper'])/a['copper'],
            'selectionReason':'Strictly smaller area is false; other three nonincrease predicates pass.',
            'limits':'Axis projections and copper bounding boxes describe the selected board only. Projection gaps exclude copper/pads/holes/silk checks and are not legal-cut or packability proof. Candidate geometry was not exported or reconstructed. No proof that all visible whitespace is required.'}

if __name__=='__main__':
    value=analyze()
    with (HERE/'envelope-analysis.json').open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    print(json.dumps(value,indent=2))
