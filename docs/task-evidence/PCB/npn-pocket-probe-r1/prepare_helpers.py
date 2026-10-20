"""Narrow published experiment helpers to the exact NPN observation."""
from pathlib import Path
HERE=Path(__file__).resolve().parent;OLD=HERE.parent/'composed-pocket-probe-r1'

def change(text,old,new,count=1):
    assert text.count(old)==count,old
    return text.replace(old,new)

def write(name,text):
    with (HERE/name).open('x',encoding='utf-8',newline='\n') as f:f.write(text)

def main():
    runner=(OLD/'run_experiment.ps1').read_text(encoding='utf-8-sig')
    runner=change(runner,'../sparse-board-diagnostic-r1/','../npn-sparse-diagnostic-r1/')
    runner=change(runner,'COMPOSED_CONTROLLED_INDICATOR','NPN_LOW_SIDE_SWITCH',2)
    runner=change(runner,'7141843758046216173','380469407799690561',2)
    write('run_experiment.ps1',runner)
    write('record_exit.ps1',(OLD/'record_exit.ps1').read_text(encoding='utf-8-sig'))
    seal=(OLD/'experiment_inputs.py').read_text(encoding='utf-8-sig')
    seal=change(seal,"'experiment-scope.md'","'scope.md'")
    seal=change(seal,"'reader-scope.md')","'reader-scope.md','prepare_helpers.py')")
    seal=change(seal,"previous=HERE.parent/'sparse-board-diagnostic-r1'","previous=HERE.parent/'npn-sparse-diagnostic-r1'")
    seal=change(seal,"sources=read(previous/'baseline-inputs.json')","sources=read(HERE/'baseline-inputs.json')")
    seal=change(seal,"len(manifest['ownerChanges'])==len(manifest['adapterChanges'])==3","len(manifest['ownerChanges'])==4 and len(manifest['adapterChanges'])==3")
    seal=change(seal,'tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/7141843758046216173','tsj-alpha/6/EASY/NPN_LOW_SIDE_SWITCH/380469407799690561')
    seal=change(seal,'helpers10 and exact supplemental inputs','helpers11 and exact supplemental inputs')
    seal=change(seal,"    return {'baselineSealSha256'","    review=read(HERE/'anchor-oracle-review.json')\n    assert review['status']=='PASS_PREFREEZE_OBSERVER_ORACLE_CORRECTION' and review['correctedReaderSha256']==sha(HERE/'qualify_experiment.py')\n    return {'anchorOracleReviewSha256':sha(HERE/'anchor-oracle-review.json'),'baselineSealSha256'")
    write('experiment_inputs.py',seal)
    reader=(OLD/'qualify_experiment.py').read_text(encoding='utf-8-sig')
    reader=change(reader,"PRIOR=HERE.parent/'sparse-board-diagnostic-r1'","PRIOR=HERE.parent/'npn-sparse-diagnostic-r1'")
    reader=change(reader,"PRIOR/'composed/receipt.txt'","PRIOR/'npn/receipt.txt'")
    reader=change(reader,'family=COMPOSED_CONTROLLED_INDICATOR seed=7141843758046216173','family=NPN_LOW_SIDE_SWITCH seed=380469407799690561')
    reader=change(reader,"(row,sampler[0],'composed')","(row,sampler[0],'npn')")
    reader=change(reader,'helpers10 and exact supplemental inputs','helpers11 and exact supplemental inputs')
    reader=change(reader,"'one outward anchor escape direction'","'one inward anchor escape direction'")
    reader=change(reader,"margins={(-1,0):x-ox,(1,0):ox+ow-x-w,(0,-1):y-oy,(0,1):oy+oh-y-h}",
                        "margins={(1,0):x-ox,(-1,0):ox+ow-x-w}")
    anchor_controls='''def anchor_controls():
    original=rows(text(PRIOR/'npn/receipt.txt'),'PCB_SPARSE_NORMAL_DIAGNOSTIC ')[0]
    by_id={p['componentId']:p for p in original['layout']['components']};outline=original['layout']['outline']
    result={}
    for name,direction,shift in (('J1',(1,0),10),('J2',(-1,0),-10)):
        part=by_id[name]
        require(anchor_margin(part,outline)==(direction,30),'literal NPN edge margin')
        altered=copy.deepcopy(part);altered['accessEnvelope'][0]+=shift
        try:require(anchor_margin(altered,outline)==(direction,30),'outward anchor edge binding preserved')
        except AssertionError as failure:require(str(failure)=='outward anchor edge binding preserved','specific anchor translation rejection')
        else:raise AssertionError('wrong edge margin accepted')
        result[name]={'direction':list(direction),'expectedMargin':30,'positive':'PASS','negativeTranslation':'PASS'}
    return result

'''
    reader=change(reader,'def main():',anchor_controls+'def main():')
    reader=change(reader,"    reused=runpy.run_path(str(PRIOR/'qualify.py'))","    anchor_canaries=anchor_controls()\n    reused=runpy.run_path(str(PRIOR/'qualify.py'))")
    reader=change(reader,"'readerCanaries':{'positiveClockOnly'","'anchorCanaries':anchor_canaries,'readerCanaries':{'positiveClockOnly'")
    move_function='''def move_checks(moves,parts):
    by_id={p['componentId']:p for p in parts}
    for item in moves:
        require(item['schema']=='pocket-accepted-move@1' and item['sweep'] in (1,2),'accepted move framing')
        require(type(item['evaluation']) is int and 0<=item['evaluation']<=60000,'accepted move cap')
        require(item['componentId'] in by_id and by_id[item['componentId']]['anchor']=='NONE','accepted move nonanchor')
        values=[item[k] for k in ('fromX','fromY','toX','toY')]
        require(all(type(v) is int for v in values) and values[:2]!=values[2:],'accepted move translation')
        require((item['toX']-item['fromX'])%10==0 and (item['toY']-item['fromY'])%10==0,'accepted move grid')
        box=item['accessEnvelope'];old=by_id[item['componentId']]['accessEnvelope']
        require(isinstance(box,list) and len(box)==4 and all(type(v) is int for v in box) and box[2:]==old[2:],'accepted move envelope dimensions')
        require(all(type(item[k]) in (int,float) and math.isfinite(item[k]) and item[k]>=0 for k in ('areaEnd','wireEnd')),'accepted move proxies')

def ordered_move_events(receipt,parts):
    pending=[];all_moves=[]
    for line in receipt.splitlines():
        if line.startswith('PCB_POCKET_MOVE_OBSERVATION '):
            item=json.loads(line[len('PCB_POCKET_MOVE_OBSERVATION '):]);pending.append(item);all_moves.append(item)
        elif line.startswith('PCB_POCKET_SWEEP_OBSERVATION '):
            item=json.loads(line[len('PCB_POCKET_SWEEP_OBSERVATION '):])
            require(len(pending)==item['acceptedMoves'],'accepted move aggregate count')
            require(all(m['sweep']==item['sweep'] and item['evaluationStart']<m['evaluation']<=item['evaluationEnd'] for m in pending),'accepted move sweep interval')
            require(all(a['evaluation']<b['evaluation'] for a,b in zip(pending,pending[1:])),'accepted move evaluation order')
            if pending:require(pending[-1]['areaEnd']==item['areaEnd'] and pending[-1]['wireEnd']==item['wireEnd'],'accepted move final proxies')
            pending=[]
    require(not pending,'accepted move terminal aggregate')
    move_checks(all_moves,parts)
    return all_moves

'''
    reader=change(reader,'def phase_result(phase):',move_function+'def phase_result(phase):')
    reader=change(reader,'results={};actual={};observations={}','results={};actual={};observations={};move_observations={}')
    reader=change(reader,"        results[phase]={'elapsedSeconds'","        moves=ordered_move_events(receipt,row['layout']['components']);move_observations[phase]=moves\n        results[phase]={'acceptedMoves':moves,'elapsedSeconds'")
    reader=change(reader,"require(observations['cold']==observations['repeat'],'exact sweep determinism')","require(observations['cold']==observations['repeat'],'exact sweep determinism');require(move_observations['cold']==move_observations['repeat'],'exact accepted move determinism')")
    move_controls='''    move_canaries={'negativeMoveCap':'NOT_RUN_NO_ACCEPTED_MOVE','negativeMoveAnchor':'NOT_RUN_NO_ACCEPTED_MOVE'}
    if move_observations['cold']:
        for label,key,value,expected in (('negativeMoveCap','evaluation',60001,'accepted move cap'),('negativeMoveAnchor','componentId','J1','accepted move nonanchor')):
            bad_moves=copy.deepcopy(move_observations['cold']);bad_moves[0][key]=value
            try:move_checks(bad_moves,original['layout']['components'])
            except AssertionError as failure:require(str(failure)==expected,'specific accepted move rejection');move_canaries[label]='PASS'
            else:raise AssertionError('accepted move negative accepted')
'''
    reader=change(reader,"    before=original['physicalMetrics'];",move_controls+"    before=original['physicalMetrics'];")
    reader=change(reader,"'readerCanaries':{'positiveClockOnly'","'acceptedMoveCanaries':move_canaries,'readerCanaries':{'positiveClockOnly'")
    write('qualify_experiment.py',reader)
    print('PASS NPN-specific experiment wrappers/reader prepared; no execution')

if __name__=='__main__':main()
