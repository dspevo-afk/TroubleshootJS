from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent;fixture=Path("<private-fixture-directory>")
def sha(raw):return hashlib.sha256(raw).hexdigest()
audit=json.loads((fixture/'source-audit.json').read_bytes())
expected={x['path']:x['sha256'] for x in audit['rootInputs']}
for x in audit['differences']:expected[x['path']]=x['candidateSha256']
expected[audit['newTest']['path']]=audit['newTest']['sha256']
assert len(expected)==1339
for name,h in expected.items():assert sha((fixture/name).read_bytes())==h,name
labels=['current-preflight-canaries-r2','current-preflight-01-profile-10014','current-preflight-02-off-10014']
manifests=[json.loads((s/label/'input-manifest-before.json').read_bytes()) for label in labels]
assert len({x['sha256'] for x in manifests})==1
for m in manifests:
 actual={x['path']:x['sha256'] for x in m['files']}
 for name,h in expected.items():
  if name in actual:assert actual[name]==h,name
assert 'src/com/lushprojects/circuitjs1/client/CirSim.java' in actual
profile=json.loads((s/'current-preflight-01-profile-10014-summary.json').read_bytes())
c=profile['cold']['luRepetitionProfile'];estimated=c['zeroRowPreflightMillis']*c['factorCalls']/c['zeroRowPreflightTimingSamples']
result={'status':'PASS','scope':'all1339 source-only fixture inputs still match prebuild source audit; compiled canaries and both measured browser runs have identical complete consumed source/web/runner manifests','fixture':fixture.name,'sourceAuditSha256':sha((fixture/'source-audit.json').read_bytes()),'expectedSourceInputs':len(expected),'manifestSha256':manifests[0]['sha256'],'sourceSha256':manifests[0]['sourceSha256'],'webSha256':manifests[0]['webSha256'],'runLabels':labels,'coldSample':{'fullCellReads':c['sampledInputMatrixCellReads'],'leftToRightCellReads':c['leftToRightFirstNonzeroReads'],'leftToRightReadFraction':c['leftToRightFirstNonzeroReads']/c['sampledInputMatrixCellReads'],'diagonalFirstCellReads':c['diagonalFirstNonzeroReads'],'zeroRowPreflightMeasuredMillis':c['zeroRowPreflightMillis'],'selectedCalls':c['zeroRowPreflightTimingSamples'],'totalFactors':c['factorCalls'],'extrapolatedPreflightMillis':estimated,'extrapolationLimit':'sparse deterministic sample; browser clock granularity uncalibrated; not directly measured cumulative work or a promised speedup'},'build':json.loads((s/'current-preflight-gwt-r2.json').read_text(encoding='utf-8-sig'))}
(s/'current-preflight-final-input-audit.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
