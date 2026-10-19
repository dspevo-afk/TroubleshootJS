"""Synthetic margin-reader guards; not physical or solver qualification."""
import copy,json
import assess
baseline=[{'seed':s,'version':14,'width':200,'height':160,'geometrySha256':'frozen'} for s in sorted(assess.ROOTS)]
rows=[dict(r,actualMargin=50,requestedMargin=50,fixedMarginFloor=50,accessEnvelopeMargin=40,occupiedWidth=100,occupiedHeight=60,occupiedX=60,occupiedY=70,outlineX=10,outlineY=20,inferredFallbackUsed=False) for r in baseline]
assert len(assess.assess(rows,baseline))==6
mutations=[lambda r:r.pop(),lambda r:r.append(copy.deepcopy(r[0])),lambda r:r[0].update(geometrySha256='changed'),
           lambda r:r[0].update(occupiedWidth=101),lambda r:r[0].update(occupiedY=71),
           lambda r:r[0].update(requestedMargin=51),lambda r:r[0].update(inferredFallbackUsed=True),
           lambda r:r[0].update(actualMargin=50.0),lambda r:r[0].update(fixedMarginFloor=51)]
for change in mutations:
    altered=copy.deepcopy(rows);change(altered)
    try:assess.assess(altered,baseline)
    except ValueError:pass
    else:raise AssertionError('Invalid synthetic observation admitted')
fallback=copy.deepcopy(rows);fallback[0].update(fixedMarginFloor=40,requestedMargin=40,inferredFallbackUsed=True)
assert sum(r['inferredFallbackUsed'] for r in assess.assess(fallback,baseline))==1
result={'status':'PASS','canaryCount':11,'scope':'Synthetic exact/fallback observations and nine rejection guards; no PCB runtime proof'}
(assess.HERE/'assessor-canaries.json').write_bytes((json.dumps(result,indent=2)+'\n').encode());print('PASS synthetic crop reader canaries=11')
