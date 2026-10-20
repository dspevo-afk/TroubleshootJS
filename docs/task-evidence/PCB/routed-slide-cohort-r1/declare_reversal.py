"""Declare exact context-bound inverses for the reviewed cohort adapters."""
import difflib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;OLD=HERE.parent/'routed-slide-descent-r1'
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
marker='informed routed slide frame adapter'
def erase(raw):
    kept=[];inside=False;count=0
    for line in raw.splitlines(keepends=True):
        if line.strip()==('// BEGIN COPY-ONLY '+marker+'.').encode():inside=True;count+=1
        elif line.strip()==('// END COPY-ONLY '+marker+'.').encode():inside=False
        elif not inside:kept.append(line)
    assert not inside and count==1
    return b''.join(kept)
record={'files':{}}
for short in ('PcbRoutedSlideCompactionContractTest','PcbRoutedSlideDescentContractTest'):
    name='tests/contracts/'+short+'.java';raw=(COPY/name).read_bytes();target=(OLD/'source'/name).read_bytes();operations=[]
    if short=='PcbRoutedSlideCompactionContractTest':
        operations.append({'kind':'erase','begin':'// BEGIN COPY-ONLY '+marker+'.','end':'// END COPY-ONLY '+marker+'.','count':1});raw=erase(raw)
    current=raw.decode().splitlines(keepends=True);before=target.decode().splitlines(keepends=True)
    groups=list(difflib.SequenceMatcher(None,current,before,autojunk=False).get_grouped_opcodes(3))
    for group in reversed(groups):
        source=''.join(current[group[0][1]:group[-1][2]]);replacement=''.join(before[group[0][3]:group[-1][4]])
        assert source and raw.count(source.encode())==1
        operations.append({'kind':'replace','old':source,'new':replacement,'count':1});raw=raw.replace(source.encode(),replacement.encode())
    assert raw==target,name
    record['files'][name]=operations
p=HERE/'adapter-reversal.json';assert not p.exists();p.write_text(json.dumps(record,indent=2)+'\n')
print('Declared exact reviewed inverse:',{n:len(ops) for n,ops in record['files'].items()})
