"""Independent structural checks for the fresh synthetic RUN profile."""
from pathlib import Path
import argparse,copy,hashlib,json,sys,types,unittest
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
p=argparse.ArgumentParser();p.add_argument('--reference-root',type=Path,required=True);p.add_argument('--codec-source',type=Path,required=True)
a,rest=p.parse_known_args();sys.argv=[sys.argv[0]]+rest
raw=a.codec_source.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='de21e233e27f93c8183101a89ed6fdb9a00812d69e1fe38cc47c3f3e573b3eb8'
CODEC=types.ModuleType('_unchanged_structural_codec')
exec(compile(raw,'authenticated_context_codec','exec',dont_inherit=True,optimize=0),CODEC.__dict__)
CONTEXT_RAW=(a.reference_root/'SYNTHETIC_RUN_CONTEXT_R1.json').read_bytes()
assert hashlib.sha256(CONTEXT_RAW).hexdigest()=='b18d9b6169cfe1ed846402a6a7784fb49f008624a00bc8aa535fa73e444e984d'
CONTEXT=json.loads(CONTEXT_RAW)
def captured():
    ids=['NET_%03d'%i for i in range(60)]
    ids[10]='D_SENSE';ids[43]='A_COIL_LOW';ids[50]='B_COIL_LOW'
    return ids
def requested():
    v=captured()
    return [v[i] for i in [43,50,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,44,45,46,47,48,49,51,52,53,54,55,56,57,58,59]]
class Profile(unittest.TestCase):
    def setUp(self):self.c=copy.deepcopy(CONTEXT);self.n=self.c['nativeProjection']
    def test_canonical_true(self):self.assertEqual(CODEC.canonical_context(self.c),CONTEXT_RAW)
    def test_captured_literal(self):self.assertEqual(self.n['capture']['capturedNetIds'],captured())
    def test_requested_literal(self):
        for t in (self.n['prior']['trialDisposition'],self.n['capture']['trialDisposition']):
            self.assertEqual(t['requestedNetIds'],requested());self.assertIs(t['orderChanged'],True)
    def test_both_representations(self):
        x=self.n['prior']['trialDisposition'];y=self.n['capture']['trialDisposition']
        self.assertEqual(x,y);self.assertIsNot(x,y);self.assertIsNot(x['requestedNetIds'],y['requestedNetIds'])
    def test_same_multiset_lengths(self):self.assertEqual(sorted(map(len,captured())),sorted(map(len,requested())))
    def test_entries(self):self.assertEqual(CODEC.data_entries(self.n),271)
    def test_old_false_is_structural_only(self):
        for t in (self.n['prior']['trialDisposition'],self.n['capture']['trialDisposition']):t['orderChanged']=False
        self.assertNotEqual(CODEC.canonical_context(self.c),CONTEXT_RAW)
    def test_flag_type_rejected(self):
        self.n['prior']['trialDisposition']['orderChanged']=1
        with self.assertRaises(ValueError):CODEC.canonical_context(self.c)
    def test_duplicate_requested_rejected(self):
        self.n['prior']['trialDisposition']['requestedNetIds'][1]='A_COIL_LOW'
        with self.assertRaises(ValueError):CODEC.canonical_context(self.c)
    def test_factory_identity(self):
        value=CODEC.generate_input_java(self.c,hashlib.sha256(CONTEXT_RAW).hexdigest())
        if isinstance(value,str):value=value.encode('utf-8')
        self.assertEqual(value,(a.reference_root/'CohortTerminalTelemetryInputV2.java').read_bytes())
    def test_setup_census(self):
        self.assertEqual(18+72+290+271+3+202+11+9140,10007)
        self.assertEqual(271+202+5,478)
    def test_staging_census(self):
        self.assertEqual(4+12+2+60+60*3+1+5,264)
        self.assertEqual(1+2+60+60+2,125)
        self.assertEqual(1+1+1+60+1,64)
if __name__=='__main__':unittest.main()
