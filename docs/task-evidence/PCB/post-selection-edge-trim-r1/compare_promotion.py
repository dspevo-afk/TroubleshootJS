"""Additional exact reproduction check; original strict oracle remains authoritative."""
import json
import assess_geometry as gate
HERE=gate.HERE
before=gate.collect((HERE/'focused-native.txt').read_text(encoding='utf-8-sig'))
after=gate.collect((HERE/'final-native.txt').read_text(encoding='utf-8-sig'))
failures=[]
for index,version in enumerate((14,15)):
    if set(before[index])!=set(after[index]):failures.append('Changed root set '+str(version))
    for seed,original in before[index].items():
        candidate=after[index].get(seed,{})
        for field in set(original)|set(candidate):
            if field!='elapsedMillis' and original.get(field)!=candidate.get(field):
                failures.append(str(version)+'/'+seed+'/'+field)
result={'status':'FAIL' if failures else 'PASS','failures':failures,'pairs':14,
    'scope':'Every deterministic native14/15 export field exact across explicit trial and default/replay promotion; elapsedMillis excluded. Additional reproduction check, no replacement for strict metric gate.'}
(HERE/'promotion-reproduction.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result));raise SystemExit(bool(failures))
