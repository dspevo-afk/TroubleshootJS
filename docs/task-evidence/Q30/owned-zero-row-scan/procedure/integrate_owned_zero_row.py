from pathlib import Path
import hashlib,json,subprocess
root=Path('[REDACTED_USER_PATH]
s=Path(__file__).resolve().parent
trial=Path((s/'owned-zero-row-r2-source-pair-path.txt').read_text())
result=json.loads((s/'owned-zero-row-sequence-result.json').read_bytes())
assert result['status']=='FOCUSED_EXPERIMENT_PASS_NOT_ACCEPTANCE' and len(result['rows'])==8 and len(result['pairs'])==4 and not result['notRun']
assert not subprocess.check_output(['git','diff','--cached','--name-only'],cwd=root)
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()=='7c3bb8dffbad1e3e3385feed3d5a64822a916918'
audit=json.loads((trial/'source-audit.json').read_bytes())
def sha(raw):return hashlib.sha256(raw).hexdigest()
for item in audit['rootInputs']:
    raw=(root/item['path']).read_bytes()
    assert sha(raw)==item['sha256'] and len(raw)==item['bytes'],item['path']
for arm,items in audit['armInputs'].items():
    for item in items:
        raw=(trial/arm/item['path']).read_bytes()
        assert sha(raw)==item['sha256'] and len(raw)==item['bytes'],(arm,item['path'])
cir='src/com/lushprojects/circuitjs1/client/CirSim.java'
driver='scripts/verify-current-contracts.ps1'
test='tests/contracts/Q30OwnedLuZeroRowContractTest.java'
before=b'                for (j = 0; j != n; j++)\n                    if (row[j] != 0) row_all_zeros = false;'
after=b'                for (j = 0; j != n; j++) {\n                    if (row[j] != 0) {\n                        row_all_zeros = false;\n                        break;\n                    }\n                }'
base_cir=subprocess.check_output(['git','show','HEAD:'+cir],cwd=root)
assert base_cir.count(before)==1
staged_cir=base_cir.replace(before,after)
base_driver=subprocess.check_output(['git','show','HEAD:'+driver],cwd=root)
anchor=b"        @{ Name = 'A07ExecutionContractTest'; Marker = 'A07 execution contracts ' },"
assert base_driver.count(anchor)==1
staged_driver=base_driver.replace(anchor,anchor+b"\n        @{ Name = 'Q30OwnedLuZeroRowContractTest'; Marker = 'Q30 owned LU zero-row contracts ' },")
assert not (root/test).exists()
working_cir=(root/cir).read_bytes();assert working_cir.count(before)==1
assert working_cir.replace(before,after)==(trial/'candidate'/cir).read_bytes()
working_test=(trial/'candidate'/test).read_bytes()
assert sha(working_test)=='481e1eefc94d0285aabcae4da912a86ffd0249791627a9737c7c123ac5f51a40'
for name in (cir,driver,test):
    (root/name).write_bytes((trial/'candidate'/name).read_bytes())
staged={cir:staged_cir,driver:staged_driver,test:working_test.replace(b'\r\n',b'\n')}
records=[]
for name,raw in staged.items():
    blob=subprocess.check_output(['git','hash-object','-w','--stdin'],cwd=root,input=raw).decode().strip()
    subprocess.check_call(['git','update-index','--add','--cacheinfo','100644,'+blob+','+name],cwd=root)
    records.append({'path':name,'stagedBlob':blob,'stagedSha256':sha(raw),'workingSha256':sha((root/name).read_bytes())})
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],cwd=root,text=True).splitlines())==set(staged)
receipt={'status':'INTEGRATED_AND_SELECTIVELY_STAGED_PENDING_INDEX_GATES','scope':'only private row scan, independent native test and one maintained driver registration; existing normal hook and plan4 driver/source remain unstaged','files':records,'sequenceStatus':result['status']}
(s/'owned-zero-row-integration.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print(json.dumps(receipt))
