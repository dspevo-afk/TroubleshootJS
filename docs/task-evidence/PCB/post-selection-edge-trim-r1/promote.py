"""Promote the measured border policy while retaining explicit saved14 interpretation."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
SRC=ROOT/'src/com/lushprojects/circuitjs1/client'
original=HERE/'promotion-originals'
original.mkdir(exist_ok=True)
def edit(name,fn):
    p=SRC/name; raw=p.read_bytes()
    snapshot=original/name
    if snapshot.exists():raise ValueError('Already snapshotted '+name)
    snapshot.write_bytes(raw)
    text=raw.decode();out=fn(text)
    if out==text:raise ValueError('No change '+name)
    p.write_bytes(out.encode())
def once(s,a,b):
    if s.count(a)!=1:raise ValueError('Expected one occurrence: '+a)
    return s.replace(a,b)
def placement(s):
    s=once(s,'CURRENT_VERSION = 14, EDGE_TRIM_TRIAL_VERSION15 = 15','CANVAS_OWNER_VERSION14 = 14, CURRENT_VERSION = 15')
    s=s.replace('EDGE_TRIM_TRIAL_VERSION15','CANVAS_OWNER_VERSION14')
    s=once(s,'if (placementVersion == CANVAS_OWNER_VERSION14) return 14;','if (placementVersion == CANVAS_OWNER_VERSION14) return 13;')
    return once(s,'return placementVersion == CURRENT_VERSION ? 13 : placementVersion;','return placementVersion == CURRENT_VERSION ? 14 : placementVersion;')
edit('Rb56Placement.java',placement)
edit('GenerationRequest.java',lambda s:once(s,'static final int RB56_PLACEMENT_INTERPRETATION_CURRENT = 14;',
    'static final int RB56_PLACEMENT_INTERPRETATION_CANVAS = 14;\r\n    static final int RB56_PLACEMENT_INTERPRETATION_CURRENT = 15;'))
def replay(s):
    s=once(s,'private static final String PRIVATE_CURRENT_PREFIX = "tsj-private-session/14/";',
        'private static final String PRIVATE_CANVAS_PREFIX = "tsj-private-session/14/";\r\n    private static final String PRIVATE_CURRENT_PREFIX = "tsj-private-session/15/";')
    s=once(s,'private static final String CURRENT_PLACEMENT_TOKEN = "rb56-placement-14";',
        'private static final String CANVAS_PLACEMENT_TOKEN = "rb56-placement-14";\r\n    private static final String CURRENT_PLACEMENT_TOKEN = "rb56-placement-15";')
    # Retain each original current14 branch verbatim under named canvas14 owners,
    # then append the same bounded branch with the new current15 identity.
    for head,tail in [('        } else if (placementVersion == GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT) {','        } else {'),
                      ('        } else if (fields.length == 6 && "tsj-private-session".equals(fields[0]) &&\r\n                "14".equals(fields[1]) &&','        } else {')]:
        start=0
        while True:
            pos=s.find(head,start)
            if pos<0:break
            end=s.index(tail,pos+len(head));block=s[pos:end]
            old=block.replace('RB56_PLACEMENT_INTERPRETATION_CURRENT','RB56_PLACEMENT_INTERPRETATION_CANVAS').replace('PRIVATE_CURRENT_PREFIX','PRIVATE_CANVAS_PREFIX').replace('CURRENT_PLACEMENT_TOKEN','CANVAS_PLACEMENT_TOKEN')
            new=block.replace('"14".equals(fields[1])','"15".equals(fields[1])')
            s=s[:pos]+old+new+s[end:];start=pos+len(old)+len(new)
    return s
edit('PlayerSessionReplay.java',replay)
(HERE/'promotion-source.json').write_text(json.dumps({'files':[{'path':p.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in (SRC/'Rb56Placement.java',SRC/'GenerationRequest.java',SRC/'PlayerSessionReplay.java')]},indent=2)+'\n')
print('PROMOTED default15; explicit saved14 retained; native paired fixtures still use literal14/15')
