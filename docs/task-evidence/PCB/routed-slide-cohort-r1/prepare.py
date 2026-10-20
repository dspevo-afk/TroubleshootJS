"""Prepare the exact published1473 source with seven bounded cohort wrappers."""
import hashlib,json,stat,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-slide-descent-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
seal=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(seal)=='9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
assert sha(OLD/'native-inputs.json')=='8c1c0a121455a4e02d85253c3df03c456d27dc1ae8044673ee29c764d0928ad5'
assert not (HERE/'local-state.json').exists()
rows=read(seal)['files'];assert len(rows)==1475
for row in rows:assert sha(ROOT/row['path'])==row['sha256'],row['path']
base={r['path']:r['sha256'] for r in rows if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base)==1467
prior=read(OLD/'native-inputs.json')
for group,folder in (('overrides',OLD/'source'),('additions',OLD/'source'),('gateOwners',OLD)):
    for n,d in prior[group].items():assert sha(folder/n)==d,n
copy=Path(tempfile.mkdtemp(prefix='tsj-routed-slide-cohort-')).resolve()
assert copy.parent==Path(tempfile.gettempdir()).resolve()
assert not copy.is_symlink() and not copy.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
(HERE/'local-state.json').write_text(json.dumps({'scratch':str(copy)},indent=2)+'\n')
for n in base:
    p=copy/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
for group in ('overrides','additions'):
    for n,d in prior[group].items():
        p=copy/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((OLD/'source'/n).read_bytes())
roots=[-1017,-1004,-1022,0,17,42,1,7,5,14,2,3,4,6]
partitions=[roots[i:i+2] for i in range(0,len(roots),2)]
units=read(OLD/'unit-suites.json')+[['PcbRoutedSlideCohortContractTest','PCB routed slide cohort contracts ']]
routes=[]
for index,pair in enumerate(partitions,1):
    name='PcbRoutedSlideCohortTrialContractTest'+('' if index==1 else 'Group'+str(index))
    marker='PCB routed slide cohort '+('trial' if index==1 else 'Group'+str(index))+' contracts '
    routes.append([name,marker])
    main='''package com.lushprojects.circuitjs1.client;

/** Fixed informed pair; maintained child budget remains unchanged. */
public final class NAME {
    public static void main(String[] args) {
        PcbRoutedSlideCohortTrialContractTest.runSeeds(new long[]{PAIR},"MARKER");
    }
METHOD}
'''
    method='''    static void runSeeds(long[] seeds,String marker) {
        if(seeds.length!=2 || seeds[0]==seeds[1])
            throw new IllegalArgumentException("Two distinct informed roots per JVM");
        for(long seed:seeds)
            PcbRoutedSlideDescentContractTest.observeInformedCohort(seed,"seed"+seed);
        System.out.println("PASS: "+marker+
            "roots=2 rows=2 evaluator=selectedRoutedSlideCohort");
    }
''' if index==1 else ''
    text=main.replace('NAME',name).replace('PAIR',','.join(str(v)+'L' for v in pair)).replace('MARKER',marker).replace('METHOD',method)
    p=copy/('tests/contracts/'+name+'.java');assert not p.exists();p.write_text(text,encoding='utf-8',newline='')
reg=copy/'scripts/verify-current-contracts.ps1';raw=reg.read_bytes()
anchor=b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest';";assert raw.count(anchor)==1
extra=[units[-1]]+routes
raw=raw.replace(anchor,b''.join(("        @{ Name = '"+n+"'; Marker = '"+m+"' },\r\n").encode() for n,m in extra)+anchor)
reg.write_bytes(raw)
for n,value in (('unit-suites.json',units),('routes-suites.json',routes)):
    assert not (HERE/n).exists();(HERE/n).write_text(json.dumps(value,indent=2)+'\n')
(HERE/'preparation-audit.json').write_text(json.dumps({'status':'PASS','main1475Exact':True,
    'acceptedBaseCount':1467,'publishedCopyCount':1473,'plannedCopyCount':1481,
    'publishedSealSha256':sha(OLD/'native-inputs.json'),'roots':[str(v) for v in roots],
    'partitions':[[str(v) for v in pair] for pair in partitions],'newUnit':'PcbRoutedSlideCohortContractTest',
    'nativeExecution':'NOT_RUN'},indent=2)+'\n')
print('PASS accepted1475/published1473 exact; cohort COPY prepared, planned1481; native NOT_RUN')
