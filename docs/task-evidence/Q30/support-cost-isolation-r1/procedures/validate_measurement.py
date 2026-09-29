from pathlib import Path
import copy, hashlib, importlib.util, json, sys
sys.dont_write_bytecode=True
REC=Path(__file__).resolve().parent
PATHS=json.loads((REC/'paths.json').read_text());REPO=Path(PATHS['repo']);ROOT=Path(PATHS['scratch'])
sys.path.insert(0,str(REPO/'docs/task-evidence/Q30/scale-plan-4'))
import check_coordinator as reader
spec=importlib.util.spec_from_file_location('pair',REPO/'docs/task-evidence/Q30/live-lu-support-r1/procedures/validate_current_q30_timing_pair.py')
pair=importlib.util.module_from_spec(spec);spec.loader.exec_module(pair)
label=sys.argv[1];report=json.loads((REC/(label+'-raw-report.json')).read_text())
summary=json.loads((REC/(label+'-summary.json')).read_text())
host=json.loads((ROOT/label/'result.json').read_text());inputs=json.loads((ROOT/label/'input-manifest-before.json').read_text())
assert summary['exitCode']==0 and host['outcome']==host['cleanup']['status']==host['inputAudit']['status']=='PASS'
metadata={};removed_metadata=[]
if summary['arm']=='diagnostic':
    assert report.pop('supportCostRequested') is summary['profile']
    assert report.pop('supportCostMode')==summary['mode']
    removed_metadata.extend(['supportCostRequested','supportCostMode'])
    for phase in ['cold','warm']:
        if summary['profile']:
            metadata[phase]=report[phase].pop('supportCost')
            assert isinstance(metadata[phase],dict)
            removed_metadata.append(phase+'.supportCost')
        else:
            assert report[phase].pop('supportCost',None) is None
wrapper={'rows':[{'seed':'10014','previewSourceDigest':inputs['sourceSha256'],'previewWebDigest':inputs['webSha256'],'report':report}]}
checked=reader.check_document(wrapper,['10014'],self_test=True)
projected,removed=pair.remove_declared_timings(report)
encoded=(json.dumps(projected,sort_keys=True)+'\n').encode()
(REC/(label+'-proof-projection.json')).write_bytes(encoded)
reference=REC/'01-control-proof-projection.json'
assert not reference.exists() or reference.read_bytes()==encoded, 'Non-timing proof differs from unchanged control'
receipt={'status':'PASS','label':label,'strictReader':checked,'removedTimings':removed,
         'removedMetadata':removed_metadata,
         'projectionSha256':hashlib.sha256(encoded).hexdigest(),'rawReportSha256':hashlib.sha256((REC/(label+'-raw-report.json')).read_bytes()).hexdigest(),
         'metadataValidation':'separate independent reader' if removed_metadata else 'NOT APPLICABLE','unchangedControlParity':'PASS'}
(REC/(label+'-validation.json')).write_text(json.dumps(receipt,indent=2)+'\n')
if metadata:(REC/(label+'-profiles.json')).write_text(json.dumps(metadata,indent=2)+'\n')
print(json.dumps({'status':'PASS','label':label,'strictCorruptions':checked.get('canaries'),'completeNonTimingParity':'PASS'}))
