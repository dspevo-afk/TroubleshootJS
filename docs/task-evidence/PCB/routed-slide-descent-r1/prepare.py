"""Prepare accepted sources plus the exact published selected-slide adapters."""
import hashlib,json,stat,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
OLD=HERE.parent/'routed-slide-compaction-probe-r1'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
seal=ROOT/'docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json'
assert sha(seal)=='9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a'
assert sha(OLD/'native-inputs.json')=='a4433f982b2453c976e414a7bf873f0a95ebbf18d218e154adbcb125f66fcb64'
assert not (HERE/'local-state.json').exists()
rows=read(seal)['files'];assert len(rows)==1475
for row in rows:assert sha(ROOT/row['path'])==row['sha256'],row['path']
base={r['path']:r['sha256'] for r in rows if not r['path'].startswith('docs/task-evidence/PCB/silkscreen-scale-r1/')}
assert len(base)==1467
prior=read(OLD/'native-inputs.json')
for group,folder in (('overrides',OLD/'source'),('additions',OLD/'source'),('gateOwners',OLD)):
    for name,digest in prior[group].items():assert sha(folder/name)==digest,name
copy=Path(tempfile.mkdtemp(prefix='tsj-routed-slide-descent-')).resolve()
assert copy.parent==Path(tempfile.gettempdir()).resolve()
assert not copy.is_symlink() and not copy.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT
(HERE/'local-state.json').write_text(json.dumps({'scratch':str(copy)},indent=2)+'\n')
for name in base:
    p=copy/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/name).read_bytes())
for group in ('overrides','additions'):
    for name,digest in prior[group].items():
        p=copy/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((OLD/'source'/name).read_bytes())
units=read(OLD/'unit-suites.json')+[['PcbRoutedSlideDescentContractTest','PCB routed slide descent contracts ']]
routes=[['PcbRoutedSlideDescentTrialContractTest','PCB routed slide descent trial contracts ']]
reg=copy/'scripts/verify-current-contracts.ps1';raw=reg.read_bytes()
anchor=b"        @{ Name = 'PcbPostSelectionEdgeTrimContractTest';"
assert raw.count(anchor)==1
extra=[units[-1],routes[0]]
raw=raw.replace(anchor,b''.join(("        @{ Name = '"+n+"'; Marker = '"+m+"' },\r\n").encode() for n,m in extra)+anchor)
reg.write_bytes(raw)
wrap=copy/'tests/contracts/PcbRoutedSlideDescentTrialContractTest.java'
assert not wrap.exists()
wrap.write_text('''package com.lushprojects.circuitjs1.client;

/** Bounded private diagnostic; original selected output is never mutated. */
public final class PcbRoutedSlideDescentTrialContractTest {
    public static void main(String[] args) {
        for(long seed:new long[]{5L,2L})
            PcbRoutedSlideDescentContractTest.observe(seed,"seed"+seed);
        System.out.println("PASS: PCB routed slide descent trial contracts "+
            "roots=2 rows=2 evaluator=selectedRoutedSlideDescent");
    }
}
''',encoding='utf-8',newline='')
for name,value in (('unit-suites.json',units),('routes-suites.json',routes)):
    assert not (HERE/name).exists();(HERE/name).write_text(json.dumps(value,indent=2)+'\n')
(HERE/'preparation-audit.json').write_text(json.dumps({'status':'PASS','main1475Exact':True,
    'acceptedBaseCount':1467,'publishedCopyCount':1471,'plannedCopyCount':1473,
    'publishedSealSha256':sha(OLD/'native-inputs.json'),'newFiles':[units[-1][0],routes[0][0]],
    'nativeExecution':'NOT_RUN'},indent=2)+'\n')
print('PASS accepted1475/published1471 exact; COPY prepared for bounded descent; native NOT_RUN')
