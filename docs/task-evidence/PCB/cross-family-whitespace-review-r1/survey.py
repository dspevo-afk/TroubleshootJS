"""Rank immutable selected-board observations; free cells are not packing proof."""
import collections,hashlib,json,runpy,subprocess
from fractions import Fraction
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
PRIOR=HERE.parent/'to92-molded-detail-r2'
BASE='b83e3a6424b07f3918ea8e7f8de2651be289b6b1'
FAMILIES={'LED_INDICATOR','DIODE_PROTECTED_INDICATOR','PARALLEL_DUAL_INDICATOR','RC_DELAY',
          'NPN_LOW_SIDE_SWITCH','NMOS_LOW_SIDE_SWITCH','RELAY_OUTPUT','SENSOR_CONTROL',
          'RB15_CONTROL','COMPOSED_CONTROLLED_INDICATOR','RB30_CONTROL'}

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def survey():
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()==BASE
    audit_path=HERE.parent/'optocoupler-source-review-r1/audit_inputs.py'
    source_audit=runpy.run_path(str(audit_path))['audit']()
    phase=read(PRIOR/'census-assessment.json');assert phase['status']=='PASS'
    assert sha(PRIOR/'census-receipt.txt')==phase['receiptSha256']
    assert sha(PRIOR/'candidate-inputs.json')==phase['inputSealSha256']
    assert sha(PRIOR/'census-comparison.json')==phase['pairedComparisonSha256']
    consumed={p.relative_to(ROOT).as_posix():sha(p) for p in [audit_path]+[PRIOR/n for n in ('baseline-inputs.json','candidate-inputs.json','player-inputs.json','census-assessment.json','census-receipt.txt','census-comparison.json')]}
    for n,h in consumed.items():assert hashlib.sha256(subprocess.check_output(['git','show',BASE+':'+n],cwd=ROOT)).hexdigest()==h,n
    groups=collections.defaultdict(list)
    for line in (PRIOR/'census-receipt.txt').read_text(encoding='utf-8-sig').splitlines():
        prefix,sep,body=line.partition(' ')
        if sep and body.startswith('{'):groups[prefix].append(json.loads(body))
    assert set(groups)=={'PCB_WHITESPACE_CASE','PCB_NORMAL_PACKING_CASE','PCB_NORMAL_SILK_CASE','PCB_NORMAL_REFERENCE_CASE','PCB_LEAD_SILK_CASE'}
    assert all(len(items)==44 for items in groups.values())
    rows=[]
    for i,r in enumerate(groups['PCB_NORMAL_PACKING_CASE']):
        s=groups['PCB_WHITESPACE_CASE'][i];o=r['outline'];area=o['width']*o['height'];void=s['largestProtectedEmptyRectangle']
        assert area==o['area']>0 and r['status']=='ACCEPTED' and r['phase']=='FINALIZED'
        assert r['graphBefore']==r['graphAfter'] and r['layoutAlgorithmVersion']==19
        assert s['seed']==r['seed'] and s['parts']==r['logicalParts']==r['layoutParts']
        assert s['gridUnits']==10 and s['version']==1 and s['scope'].endswith('FREE_IS_NOT_PACKABLE_PROOF')
        assert void['area']==void['width']*void['height']==r['whitespace'] and 0<=void['area']<=area
        assert abs(s['protectedFreeFraction']-s['protectedFreeCells']/s['sampledCells'])<1e-12
        for prefix in ('PCB_NORMAL_SILK_CASE','PCB_NORMAL_REFERENCE_CASE','PCB_LEAD_SILK_CASE'):
            peer=groups[prefix][i];assert (peer['family'],peer['root'])==(r['family'],r['root'])
        rows.append({'family':r['family'],'profile':r['profile'],'root':r['root'],'publicSeed':r['seed'],
                     'parts':r['logicalParts'],'area':area,'copper':r['copperManhattanLength'],'vias':r['viaCount'],
                     'protectedVoid':r['whitespace'],'voidAreaFraction':float(Fraction(r['whitespace'],area)),
                     'protectedFreeCellFraction':s['protectedFreeFraction'],'largestProtectedEmptyRectangle':void,
                     'geometrySha256':r['geometrySha256'],'optionalProposal':'NOT_RECORDED_IN_CENSUS'})
    by_family=collections.defaultdict(list)
    for r in rows:by_family[r['family']].append(r)
    assert set(by_family)==FAMILIES and all(len(v)==4 for v in by_family.values())
    assert all({r['root'] for r in v}=={'0','13','17','-9223372036854775808'} for v in by_family.values())
    ranked=sorted(rows,key=lambda r:(-Fraction(r['protectedVoid'],r['area']),r['family'],r['root']))
    families=[]
    for name,items in sorted(by_family.items()):
        ratios=sorted(Fraction(r['protectedVoid'],r['area']) for r in items)
        families.append({'family':name,'cases':4,'voidAreaMin':float(ratios[0]),'voidAreaMedian':float((ratios[1]+ratios[2])/2),'voidAreaMax':float(ratios[-1])})
    return {'status':'PASS_FINITE_SELECTED_BOARD_SURVEY','baseCommit':BASE,'consumedPublishedInputs':consumed,
            'sourceReuseAudit':source_audit,'helperSha256':sha(Path(__file__)),'scopeSha256':sha(HERE/'scope.md'),
            'cases':44,'families':families,'rankedCases':ranked,
            'limits':'Four maintained reference roots per enabled family. Selected geometry only; census has no optional proposal evidence. Free-cell fraction/void ratio is not density, legal-removal, optimum, all-seed, timing or statistical proof. No native/build/browser rerun in this survey.'}

if __name__=='__main__':
    value=survey()
    with (HERE/'survey.json').open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
    print('PASS finite44/11 survey; top6:')
    for row in value['rankedCases'][:6]:print(row['family'],row['root'],row['publicSeed'],round(100*row['voidAreaFraction'],2))
