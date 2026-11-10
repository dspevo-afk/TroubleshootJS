"""Root Source/census reproduction; no native or protected-data work."""
from pathlib import Path
import hashlib,json,sys,types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
B=R.parent/'tsj-q100-run-input-policy-r2-061d83b05aad44bcbc973417357e031b'
def sha(raw):return hashlib.sha256(raw).hexdigest()
old=(B/'CurrentAssemblyMetadata.java').read_bytes()
new=(R/'CurrentAssemblyMetadata.java').read_bytes()
assert sha(old)=='488bb13cd7875ab9612ea300bf4430eac7e1bef5243ff95057186b41c77d21de'
assert sha(new)=='a98ea9290f8fcdb86be4377698acbe012292972c56016383a9f2684bd40b9c7c'
anchor=b'    static ReceiptContentBinding prepare('
begin=new.index(b'    /**\n     * Explicit staging entry point:')
assert new[:begin]==old[:old.index(anchor)]
assert new[new.index(anchor):]==old[old.index(anchor):]
child=R/'fixture-reproduction-r3';child.mkdir()
generator=(R/'freeze_fixture_r1.py').read_bytes()
module=types.ModuleType('_fresh_source_fixture_reproduction')
module.__file__=str(child/'freeze_fixture_r1.py')
exec(compile(generator,module.__file__,'exec',dont_inherit=True,optimize=0),module.__dict__)
assert (child/'REFERENCE_R1.json').read_bytes()==(R/'REFERENCE_R1.json').read_bytes()
reference=json.loads((R/'REFERENCE_R1.json').read_bytes())
assert reference['handoff']['units']==18576 and reference['handoff']['priorGroups']==1
assert len(reference['names'])==24 and len(reference['suite'])==22
value={'status':'PASS_ROOT_UNCHANGED_OLD_METADATA_AND_FRESH_REFERENCE_REPRODUCTION',
       'helperSha256':sha(new),'oldHelperSha256':sha(old),
       'referenceSha256':sha((R/'REFERENCE_R1.json').read_bytes()),
       'generatorSha256':sha(generator),'oldMethodRegionsUnchanged':True,
       'freshReproductionExact':True,'nativeProducerCalls':0,'genuineEnvelope':'UNSPENT_GATED'}
raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
with (R/'SOURCE_REVIEW_R3.json').open('xb') as f:f.write(raw)
print(sha(raw))
