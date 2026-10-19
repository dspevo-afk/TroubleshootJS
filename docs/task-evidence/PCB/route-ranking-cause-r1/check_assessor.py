"""Synthetic reader canaries; no PCB runtime or physical proof."""
import copy,json
import assess
baseline=[{'seed':s,'version':14,'area':100,'geometrySha256':'baseline'} for s in sorted(assess.ROOTS)]
trial=[{'seed':s,'version':15,'area':110,'geometrySha256':'trial'} for s in sorted(assess.ROOTS)]
stats='policy;routeOutcomes=[P07@0=SUCCESS];placementScores=[0:1];selectedQualityMilli=100'
rows=[dict(r,selectedAttempt=0,statisticsCanonical=stats,elapsedMillis=1) for r in baseline+trial]
assert len(assess.assess(rows,baseline,trial))==10
mutations=[lambda r:r.pop(),lambda r:r.append(copy.deepcopy(r[0])),
           lambda r:r[0].update(area=101),lambda r:r[0].update(geometrySha256='changed'),
           lambda r:r[0].update(seed='42'),lambda r:r[0].update(statisticsCanonical='incomplete'),
           lambda r:r[0].pop('area')]
for change in mutations:
    altered=copy.deepcopy(rows);change(altered)
    try: assess.assess(altered,baseline,trial)
    except ValueError: pass
    else: raise AssertionError('Assessor admitted invalid synthetic observation')
result={'status':'PASS','canaryCount':8,'scope':'One synthetic exact case-set plus seven rejection canaries; not PCB runtime proof'}
(assess.HERE/'assessor-canaries.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
print('PASS assessor synthetic canaries=8')
