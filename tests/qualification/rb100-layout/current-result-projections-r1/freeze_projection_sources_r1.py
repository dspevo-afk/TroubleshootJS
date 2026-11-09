from pathlib import Path
import hashlib,json,re,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
U=Path(__file__).resolve().parent
def sha(raw):return hashlib.sha256(raw).hexdigest()
def write(name,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
    with (U/name).open('xb') as out:out.write(raw)
    return sha(raw)
expected={
 'CurrentResultProjections.java':'7609a483f18a8c3d841327ebc950ee2cbcf627cbf467a6b509487426f062b326',
 'CurrentResultProjectionsCanary.java':'a0391fd53033201475a3324bfe654bad673ace27f5bccef8c5d773c3badd768d',
 'projection_oracle.py':'fa6eb928cb311c21dd051041a83b4ebfc4ce2a6f200a03edfe6d9ade91d94820',
 'verify_projection_r1.py':'04dbde9b68202f61cb5be175423da67408b8f5e0b8553561f9b7ff3cb170922a',
 'test_projection_oracle.py':'800e9849851842e0c5a5929f007eedbd62e1bb926d2d09ab3344191ecf05d52e',
 'ORACLE_NOTE_R1.md':'6e911b9615345760d0995be56a3fa4d80ef6e904d05c68b702f0371f2b7ed8a3',
 'HELPER_NOTE_R1.md':'5358f3ce8a94120a23dc97df298b7ece3e0b51b1e409a8d848b93ce325f51782',
 'CANARY_NOTE_R1.md':'cbe23d9831a17f875cf58e7374107e04246df4683d32824f90c335677b4a8f9b',
 'PROJECTION_CONTRACT_R1.md':'24f40e39a9d52704a51aefbdf05170c25056447009c4b25605e45ad9de04fd4f',
 'REFERENCE_R1.json':'bd0c6f081a6a9d11c47befe531debc87ddbccf02541542253b202a6debe491bc',
 'CANARY_WIRE_R1.json':'b5b20358c18806889b53c8394be627bd5078f0fd7c282e09e285f0f60a357b59'}
for name,digest in expected.items():assert sha((U/name).read_bytes())==digest,name
names=tuple(expected)+('root_projection_gates_r1.py','root_reader_controls_r1.py',
    'freeze_projection_sources_r1.py','COMPONENT_SOURCE_FINDINGS_R1.md')
privacy=re.compile(rb'(?i)(?:(?<![A-Za-z0-9_])[A-Z]:[\\/]|[/]U[s]ers[/]|\\\\U[s]ers\\\\)')
for name in names:
    raw=(U/name).read_bytes();assert 0<len(raw)<=4194304
    assert raw.endswith(b'\n') and not raw.endswith(b'\n\n') and b'\r' not in raw,name
    assert not any(line.rstrip(b' \t')!=line for line in raw.splitlines()),name
    assert not privacy.search(raw),name
    if name.endswith('.py'):compile(raw,str(U/name),'exec',dont_inherit=True,optimize=0)
pin_roles=('projection_oracle.py','verify_projection_r1.py','test_projection_oracle.py','ORACLE_NOTE_R1.md')
write('ROOT_ORACLE_SOURCE_R1.json',{'status':'ROOT_ACCEPTED_PROJECTION_ORACLE_SOURCE_BEFORE_EXECUTION',
    'files':{name:expected[name] for name in pin_roles}})
review={'status':'ROOT_SOURCE_REVIEW_COMPLETE_BEFORE_COMPONENT_EXECUTION',
    'base':'f25dfcc880b69d830e24a2bff755ae0a0a25e74a',
    'review':'Root helper/API/5-55 census/borrowed-list ownership/cleanup-scalar authority/native fixtures/17-case wire/independent literal oracle/authenticated raw loader/bounded IO/process driver review',
    'independentReview':'Leaf read-only canary+helper contract/protocol/counter/failure-disposal Source review PASS on7609a483/a0391fd5; no execution',
    'coverageBoundary':'One alias per arm and one geometry field perturbed; all23reads/20derivations Source reviewed; no per-field mutation matrix',
    'unreleasedCorrection':'Oracle parent count changed1perprocess to2/15percase before release/execution; no complete rawdraft history claim',
    'sourceRejectedTentativeDesign':'No meter.cleanupNanos or finalized-work-placeholder read; actualR7sum passed as scalar',
    'nativeHelperBuild':'NOT_RUN','nativeComponent':'NOT_RUN','pureTests':'NOT_RUN',
    'normalAssembly':'CLOSED','fullD':'NOT_QUALIFIED','genuineEnvelope':'UNSPENT_GATED'}
write('ROOT_SOURCE_REVIEW_R1.json',review)
rows=[]
for name in names+('ROOT_ORACLE_SOURCE_R1.json','ROOT_SOURCE_REVIEW_R1.json'):
    raw=(U/name).read_bytes();rows.append({'role':name,'bytes':len(raw),'sha256':sha(raw)})
digest=write('SOURCE_FREEZE_R1.json',{'status':'ROOT_REVIEWED_FINAL_PROJECTION_COMPONENT_SOURCE',
    'base':review['base'],'files':rows,'actualProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'})
print('SOURCE_FREEZE '+digest)
