from collections import defaultdict
from pathlib import Path
import hashlib, json, re

PACKET=Path(__file__).resolve().parent
BEGIN=re.compile(r'^SILKCROP_BEGIN;seed=(-?\d+);index=(\d+);version=(\d+)$')
ROW_HEADER=re.compile(r'^SILKCROP_ROW_BEGIN;candidate=(\d+);variation=(-?\d+);occupied=(-?\d+),(-?\d+),(\d+),(\d+);edgeMargin=(\d+);fingerprint=(.*)$')
LEADS=re.compile(r'connectedLead=(-?\d+),(-?\d+),(\d+),(\d+);')
LABELS=re.compile(r'(?:^|;)L:([^;]+);')

def labels_from(fp):
    out={}
    for match in LABELS.finditer(fp):
        record=match.group(1)
        if ':' not in record: raise ValueError('canonical label has no bounds separator')
        identity,bounds_text=record.rsplit(':',1)
        bounds=[int(v) for v in bounds_text.split(',')]
        if len(bounds)!=4: raise ValueError('canonical label bounds are not x,y,w,h')
        if '@' not in identity: raise ValueError('canonical label has no id/text separator')
        label_id,text=identity.split('@',1)
        out[label_id]={'text':text,'bounds':bounds}
    return out

def normalized_physical(fp):
    value=re.sub(r'^layout=\d+\|','layout=<EPOCH>|',fp,count=1)
    value=LABELS.sub(';',value)
    return value

def parse_phase(path):
    seeds={}; current=None
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        begin=BEGIN.match(line)
        if begin:
            seed,index,version=begin.groups(); current=seed
            seeds.setdefault(seed,{'index':int(index),'version':int(version),'rows':[],'selectedAttempt':None,'routingWork':None,'measure':None})
            continue
        if line.startswith('SILKCROP_ROW_BEGIN;'):
            m=ROW_HEADER.match(line)
            if not m or not line.endswith(';SILKCROP_ROW_END'):
                raise ValueError(f'unframed candidate row in {path.name}')
            if current is None: raise ValueError('candidate row before root/version BEGIN')
            cand,var,x,y,w,h,margin,fp=m.groups()
            fp=fp[:-len(';SILKCROP_ROW_END')]
            row={'candidate':int(cand),'variation':int(var),'occupied':[int(x),int(y),int(w),int(h)],
                 'edgeMargin':int(margin),'fingerprint':fp,'labels':labels_from(fp),
                 'physicalFingerprintSha256':hashlib.sha256(normalized_physical(fp).encode()).hexdigest()}
            seeds[current]['rows'].append(row)
            continue
        route=re.match(r'^PCB_GROUPING_ROUTE_CASE seed=(-?\d+) .*?routingWork=(\d+)/(\d+);.*?selectedAttempt=(\d+)',line)
        if route:
            seed,orders,expansions,selected=route.groups()
            if seed in seeds:
                seeds[seed]['selectedAttempt']=int(selected)
                seeds[seed]['routingWork']=f'{orders}/{expansions}'
            continue
        if line.startswith('PCB_ROLE_WIDTH_MEASUREMENT '):
            try: record=json.loads(line.split(' ',1)[1])
            except Exception: continue
            seed=record.get('seed')
            if seed in seeds: seeds[seed]['measure']=record
    return seeds

def rect_edges(rect):
    x,y,w,h=rect
    return {'left':x,'top':y,'right':x+w,'bottom':y+h}

def label_ids_at(labels,side,value):
    result=[]
    for ident,label in labels.items():
        x,y,w,h=label['bounds']
        edge={'left':x,'top':y,'right':x+w,'bottom':y+h}[side]
        if edge==value: result.append(ident)
    return sorted(result)

def compare_row(old,new):
    old_occ=old['occupied']; new_occ=new['occupied']
    old_edges=rect_edges(old_occ); new_edges=rect_edges(new_occ)
    ids=set(old['labels'])|set(new['labels'])
    changed=[]
    for ident in sorted(ids):
        if old['labels'].get(ident)!=new['labels'].get(ident):
            changed.append({'id':ident,'old':old['labels'].get(ident),'new':new['labels'].get(ident)})
    changed_ids={item['id'] for item in changed}
    extrema=[]
    for side in ('left','top','right','bottom'):
        a=old_edges[side]; b=new_edges[side]
        if a==b: continue
        moved=[ident for ident in label_ids_at(new['labels'],side,b) if ident in changed_ids]
        extrema.append({'side':side,'oldEdge':a,'newEdge':b,
                        'direction':'expanded' if (b<a if side in ('left','top') else b>a) else 'contracted',
                        'oldEdgeLabels':label_ids_at(old['labels'],side,a),
                        'newEdgeLabels':label_ids_at(new['labels'],side,b),
                        'movedLabelsAtNewEdge':moved})
    ow=old_occ[2]+2*old['edgeMargin']; oh=old_occ[3]+2*old['edgeMargin']
    nw=new_occ[2]+2*new['edgeMargin']; nh=new_occ[3]+2*new['edgeMargin']
    return {'baselineOccupied':old_occ,'candidateOccupied':new_occ,
            'baselineEdgeMargin':old['edgeMargin'],'candidateEdgeMargin':new['edgeMargin'],
            'baselinePreCropNominalSize':[ow,oh],'candidatePreCropNominalSize':[nw,nh],
            'baselinePreCropNominalArea':ow*oh,'candidatePreCropNominalArea':nw*nh,
            'occupiedChanged':old_occ!=new_occ,'edgeMarginChanged':old['edgeMargin']!=new['edgeMargin'],
            'sameNonLabelPhysicalFingerprint':old['physicalFingerprintSha256']==new['physicalFingerprintSha256'],
            'baselinePhysicalFingerprintSha256':old['physicalFingerprintSha256'],
            'candidatePhysicalFingerprintSha256':new['physicalFingerprintSha256'],
            'changedLabelCount':len(changed),'changedLabels':changed,'changedExtrema':extrema}

def main():
    if len(__import__('sys').argv)!=3: raise SystemExit('usage: analyze_phase_rows.py baseline.txt candidate.txt')
    old=parse_phase(PACKET/__import__('sys').argv[1]); new=parse_phase(PACKET/__import__('sys').argv[2])
    if not old or not new: raise SystemExit('FAIL no observer roots parsed')
    reports=[]; matched=0; physical_equal=0
    for seed in sorted(set(old)|set(new),key=lambda s:int(s)):
        a=old.get(seed); b=new.get(seed)
        root={'seed':seed,'baselinePresent':a is not None,'candidatePresent':b is not None}
        if a and b:
            root.update({'baselineVersion':a['version'],'candidateVersion':b['version'],
                         'baselineSelectedAttempt':a['selectedAttempt'],'candidateSelectedAttempt':b['selectedAttempt'],
                         'baselineRoutingWork':a['routingWork'],'candidateRoutingWork':b['routingWork'],
                         'baselineMeasure':a['measure'],'candidateMeasure':b['measure']})
            aa=defaultdict(list); bb=defaultdict(list)
            for r in a['rows']: aa[(r['candidate'],r['variation'])].append(r)
            for r in b['rows']: bb[(r['candidate'],r['variation'])].append(r)
            candidate_rows=[]
            for key in sorted(set(aa)|set(bb)):
                for ordinal in range(max(len(aa[key]),len(bb[key]))):
                    pair={'candidate':key[0],'variation':key[1],'ordinal':ordinal,
                          'baselinePresent':ordinal<len(aa[key]),'candidatePresent':ordinal<len(bb[key])}
                    if ordinal<len(aa[key]) and ordinal<len(bb[key]):
                        matched+=1; detail=compare_row(aa[key][ordinal],bb[key][ordinal])
                        pair.update(detail)
                        if detail['sameNonLabelPhysicalFingerprint']: physical_equal+=1
                        pair['selectedInBaseline']=key[0]==a['selectedAttempt']
                        pair['selectedInCandidate']=key[0]==b['selectedAttempt']
                    candidate_rows.append(pair)
            root['candidateRows']=candidate_rows
        reports.append(root)
    result={'schema':1,'status':'PASS_ANALYSIS' if matched else 'FAIL_NO_MATCHED_ROWS',
            'normalization':'For comparison only, leading layout epoch replaced and every L: label record removed; full labels retained in changedLabels.',
            'matchedCandidateRows':matched,'sameNonLabelPhysicalRows':physical_equal,'roots':reports}
    (PACKET/'candidate-analysis.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('matchedCandidateRows='+str(matched)+' sameNonLabelPhysicalRows='+str(physical_equal))
    for root in reports:
        if not root.get('baselinePresent') or not root.get('candidatePresent'): continue
        bm=root.get('baselineMeasure') or {}; cm=root.get('candidateMeasure') or {}
        print('seed='+root['seed']+' selected='+str(root['baselineSelectedAttempt'])+'->'+str(root['candidateSelectedAttempt'])+
              ' routingWork='+str(root['baselineRoutingWork'])+'->'+str(root['candidateRoutingWork'])+
              ' area='+str(bm.get('area'))+'->'+str(cm.get('area')))
        for row in root['candidateRows']:
            if row.get('baselinePresent') and row.get('candidatePresent') and (row['occupiedChanged'] or row['edgeMarginChanged'] or not row['sameNonLabelPhysicalFingerprint']):
                print('  candidate='+str(row['candidate'])+' variation='+str(row['variation'])+
                      ' physicalSame='+str(row['sameNonLabelPhysicalFingerprint'])+
                      ' occupied='+str(row['baselineOccupied'])+'->'+str(row['candidateOccupied'])+
                      ' margin='+str(row['baselineEdgeMargin'])+'->'+str(row['candidateEdgeMargin'])+
                      ' changedLabels='+str(row['changedLabelCount'])+' extrema='+json.dumps(row['changedExtrema'],separators=(',',':')))
    if not matched: raise SystemExit(1)
if __name__=='__main__': main()