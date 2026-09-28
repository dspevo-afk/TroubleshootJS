from pathlib import Path
import copy,hashlib,importlib.util,json
scratch=Path(__file__).resolve().parent
source=scratch/'validate_lu_repetition_pair.py'
assert hashlib.sha256(source.read_bytes()).hexdigest()=='56d11822c0456ef4372bfa7cdfc0f3d79965e6aa895fcfd665e0d4a8b72da312'
spec=importlib.util.spec_from_file_location('q30_sampler_validator',source)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
raw=next((scratch/'lu-repetition-current-01-profile-7/cases').glob('*.report.json'))
report=json.loads(raw.read_text(encoding='utf-8'))
profile=report['cold']['luRepetitionProfile']
deadline=report['measurementMaxJobMillis']
module.validate_profile_record(profile,'cold',report,deadline)
mutations=[
 ('foreign phase',lambda p:p.__setitem__('phase','warm')),
 ('nonadjacent comparison',lambda p:p['compareFactorCallPositions'].__setitem__(0,1023)),
 ('duplicate start',lambda p:p['sampleStartFactorCallPositions'].__setitem__(1,1021)),
 ('completed-pair count',lambda p:p.__setitem__('completedPairs',p['completedPairs']+1)),
 ('match count',lambda p:p.__setitem__('matchedPairs',p['matchedPairs']+1)),
 ('compared-cell census',lambda p:p.__setitem__('comparedEntries',p['comparedEntries']+1)),
 ('clock error',lambda p:p.__setitem__('probeClockErrors',1)),
 ('wall clock mislabeled',lambda p:p.__setitem__('probeClock','Date.now')),
 ('signed-zero entry overflow',lambda p:p.__setitem__('signedZeroDifferenceEntries',p['comparedEntries']+1)),
 ('size census drift',lambda p:p['sampledSizeGroups'].__setitem__('71',97)),
 ('overhead outside measurement',lambda p:p.__setitem__('probeOverheadMillis',deadline+1)),
]
receipts=[]
for name,mutate in mutations:
    changed=copy.deepcopy(profile);mutate(changed)
    try:module.validate_profile_record(changed,'cold',report,deadline)
    except module.ValidationError as error:receipts.append({'case':name,'status':'REJECTED_AS_EXPECTED','reason':str(error)})
    else:raise AssertionError('corruption accepted: '+name)
try:module.remove_profile_fields(report,False)
except module.ValidationError as error:receipts.append({'case':'profile in query-off report','status':'REJECTED_AS_EXPECTED','reason':str(error)})
else:raise AssertionError('query-off profile was accepted')
receipt={'status':'PASS','rawReportSha256':hashlib.sha256(raw.read_bytes()).hexdigest(),'validatorSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'positiveSamplerRecords':1,'negativeMetadataCanaries':receipts,'scope':'sampler metadata only; separate strict current-plan4 readers check electrical/proof/physical/work/cache/budget evidence'}
output=scratch/'lu-sampler-metadata-canaries.json';assert not output.exists()
output.write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print('PASS: one real sampler metadata record and '+str(len(receipts))+' negative metadata canaries')
