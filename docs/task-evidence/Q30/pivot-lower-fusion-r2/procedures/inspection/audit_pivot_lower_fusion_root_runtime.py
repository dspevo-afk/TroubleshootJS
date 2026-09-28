from pathlib import Path
import hashlib,json
root=Path('<TASK_TEMP>
s=Path(__file__).resolve().parent
fixture=Path((s/'pivot-lower-fusion-r2-source-pair-path.txt').read_text())/'candidate'
recorded=json.loads((s/'pivot-lower-fusion-r2-canaries/input-manifest-before.json').read_bytes())
rows=recorded['files']
mismatches=[]
for row in rows:
    p=root/row['path']
    if not p.is_file(): mismatches.append(row['path']);continue
    raw=p.read_bytes()
    if len(raw)!=row['size'] or hashlib.sha256(raw).hexdigest()!=row['sha256']:mismatches.append(row['path'])
war_root={p.relative_to(root).as_posix() for p in (root/'war').rglob('*') if p.is_file()}
war_fixture={p.relative_to(fixture).as_posix() for p in (fixture/'war').rglob('*') if p.is_file()}
extra=sorted(war_root-war_fixture);missing=sorted(war_fixture-war_root)
changed=[p for p in sorted(war_root&war_fixture) if (root/p).read_bytes()!=(fixture/p).read_bytes()]
result={'status':'PASS' if not mismatches and not extra and not missing and not changed else 'FAIL','scope':'actual root final-source build versus measured/canary-tested r2 candidate; every consumed input and entire WAR compared byte-for-byte','inputCount':len(rows),'sourceSha256':recorded['sourceSha256'],'webSha256':recorded['webSha256'],'completeWarFiles':len(war_root),'consumedInputMismatches':mismatches,'extraWarFiles':extra,'missingWarFiles':missing,'warByteDifferences':changed,'rootBuild':json.loads((s/'pivot-lower-fusion-root-gwt.json').read_text(encoding='utf-8-sig'))}
(s/'pivot-lower-fusion-root-runtime-equality.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result));assert result['status']=='PASS'

final=s/'pivot-lower-fusion-final-source'
audit=json.loads((final/'final-source-audit.json').read_bytes())
for r in audit['inputs']:
    b=(final/r['path']).read_bytes()
    assert len(b)==r['bytes'] and hashlib.sha256(b).hexdigest()==r['sha256'],r['path']
print('PASS: all 1339 proposed final source inputs unchanged')
