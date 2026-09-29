from pathlib import Path
import gzip, hashlib, json, sys
rec=Path(__file__).parent;paths=json.loads((rec/'paths.json').read_text());root=Path(paths['scratch'])
base=json.loads((root/'baseline-source-audit.json').read_text());label=sys.argv[1]
sha=lambda b:hashlib.sha256(b).hexdigest()
for e in base['armInputs']['control']:
    assert sha((root/'control'/e['path']).read_bytes())==e['sha256'],e['path']
old={e['path']:e for e in base['armInputs']['candidate']}
extra=['src/com/lushprojects/circuitjs1/client/Q30SupportCostProfile.java','tests/contracts/Q30SupportCostDiagnosticContractTest.java']
records=[];changed=[]
for rel in sorted(set(old)|set(extra)):
    raw=(root/'diagnostic'/rel).read_bytes()
    e={'path':rel,'bytes':len(raw),'sha256':sha(raw)};records.append(e)
    if rel not in old or e['sha256']!=old[rel]['sha256']:
        changed.append(rel)
        target=rec/'source'/label/(rel.replace('/','__')+'.gz');target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(gzip.compress(raw,mtime=0))
out={'baseline':'live-lu-support-r1/source/source-audit.json.gz','controlInputs':len(base['armInputs']['control']),
     'diagnosticInputs':len(records),'changedFromPrototype':changed,'inputs':records}
(rec/(label+'-source-audit.json')).write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({'label':label,'controlInputs':out['controlInputs'],'diagnosticInputs':len(records),'changed':changed},indent=2))
