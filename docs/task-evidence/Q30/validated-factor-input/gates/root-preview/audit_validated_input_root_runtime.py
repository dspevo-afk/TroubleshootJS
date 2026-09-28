from pathlib import Path
import hashlib,json
root=Path("<REPOSITORY>")
s=Path(__file__).parent
fixture=Path("<TASK_TEMP>/trial-validated-input-r3-dcff21e0634d4aaf84bdf05d13f06f1a")/'candidate'
recorded=json.loads((s/'validated-input-candidate-canaries-r3/input-manifest-before.json').read_bytes())
rows=recorded['files']
mismatches=[]
for row in rows:
 p=root/row['path']
 if not p.exists() or len(p.read_bytes())!=row['size'] or hashlib.sha256(p.read_bytes()).hexdigest()!=row['sha256']:mismatches.append(row['path'])
war_root={p.relative_to(root).as_posix() for p in (root/'war').rglob('*') if p.is_file()}
war_fixture={p.relative_to(fixture).as_posix() for p in (fixture/'war').rglob('*') if p.is_file()}
extra=sorted(war_root-war_fixture);missing=sorted(war_fixture-war_root)
all_war_diff=[x for x in sorted(war_root&war_fixture) if (root/x).read_bytes()!=(fixture/x).read_bytes()]
result={'status':'PASS' if not mismatches and not extra and not missing and not all_war_diff else 'FAIL','scope':'root final build versus previously measured r3 candidate; every compiled-browser consumed input and complete WAR inventory compared byte-for-byte','inputCount':len(rows),'sourceSha256':recorded['sourceSha256'],'webSha256':recorded['webSha256'],'completeWarFiles':len(war_root),'consumedInputMismatches':mismatches,'extraWarFiles':extra,'missingWarFiles':missing,'warByteDifferences':all_war_diff,'rootBuild':json.loads((s/'validated-input-root-gwt.json').read_text(encoding='utf-8-sig'))}
(s/'validated-input-root-runtime-equality.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
assert result['status']=='PASS'
