"""Source-locked overlay and real bounded-filesystem component controls."""
import argparse,hashlib,importlib.util,stat,sys,tempfile,unittest
from pathlib import Path
path=Path(__file__).resolve().with_name('generate_branch_limit_selection_overlay.py')
spec=importlib.util.spec_from_file_location('selection_overlay_tested',path)
overlay=importlib.util.module_from_spec(spec);spec.loader.exec_module(overlay)
args=argparse.ArgumentParser(add_help=False,allow_abbrev=False)
args.add_argument('--source',required=True);args.add_argument('--io-helper',required=True)
paths,unit_args=args.parse_known_args()
SOURCE=Path(paths.source);HELPER=Path(paths.io_helper)

class SourceTests(unittest.TestCase):
    def test_exact_transform_is_deterministic_and_leaves_original_dto_and_caps(self):
        original=SOURCE.read_bytes();first,changes=overlay.derive(original);second,_=overlay.derive(original)
        self.assertEqual(first,second);self.assertEqual(len(changes),8)
        start=b'    static final class BranchSnapshot {';end=b'    private static final class Exhausted'
        def branch(raw):return raw[raw.index(start):raw.index(end)]
        self.assertEqual(branch(original),branch(first))
        for line in original.splitlines():
            if b'final int GRID=' in line or b'final int MAX_BRANCH_EXPANSIONS=' in line:
                self.assertIn(line,first)
        self.assertEqual(first.count(b'emitTerminal("BRANCH_SEARCH_LIMIT",current)'),1)
        self.assertNotIn(b'previous',overlay.SELECTION)
    def test_wrong_source_hash_is_rejected(self):
        with self.assertRaises(overlay.OverlayError) as raised:overlay.derive(SOURCE.read_bytes()+b'\n')
        self.assertEqual(raised.exception.code,'SOURCE_HASH')
    def test_splice_requires_exactly_one_matching_anchor(self):
        for source in (b'NONE',b'TARGET TARGET'):
            with self.subTest(source=source):
                with self.assertRaises(overlay.OverlayError) as raised:overlay.replace_once(source,b'TARGET',b'NEW')
                self.assertEqual(raised.exception.code,'SPLICE_COUNT')
    def test_cli_rejects_duplicate_abbreviation_nul_and_extra(self):
        base=['--source','a','--io-helper','b','--output-dir','c']
        examples=[['--source','a','--source','b','--output-dir','c'],['--s','a','--io-helper','b','--output-dir','c'],['--source','a\0b','--io-helper','b','--output-dir','c'],base+['--extra','x']]
        for value in examples:
            with self.subTest(value=value):
                with self.assertRaises(overlay.OverlayError) as raised:overlay.parse_args(value)
                self.assertEqual(raised.exception.code,'CLI_ARGUMENTS')

class FilesystemTests(unittest.TestCase):
    def setUp(self):
        self.temp=Path(tempfile.gettempdir()).resolve(strict=True)
        self.root=Path(tempfile.mkdtemp(prefix='tsj-selection-overlay-unit-',dir=str(self.temp)))
        self.out=self.root/'out';self.out.mkdir();self.addCleanup(self.cleanup)
    def cleanup(self):
        def check(p,parent):
            info=p.lstat()
            if p.parent.resolve(strict=True)!=parent or p.resolve(strict=True)!=p or not stat.S_ISDIR(info.st_mode) or getattr(info,'st_file_attributes',0)&0x400:raise AssertionError('UNSAFE_FIXTURE_DIRECTORY')
        check(self.root,self.temp);check(self.out,self.root)
        for directory,allowed in [(self.out,{'PcbLayerRoutingPrototype.java','SOURCE_PROOF.json','keep.txt'}),(self.root,{'bad-source.java','bad-helper.py'})]:
            for item in tuple(directory.iterdir()):
                if item==self.out:continue
                info=item.lstat()
                if item.name not in allowed or not stat.S_ISREG(info.st_mode) or getattr(info,'st_file_attributes',0)&0x400:raise AssertionError('UNSAFE_FIXTURE_FILE')
                item.unlink()
        self.out.rmdir();self.root.rmdir()
    def options(self,source=SOURCE,helper=HELPER):return {'--source':str(source),'--io-helper':str(helper),'--output-dir':str(self.out)}
    def test_real_generate_exact_output_and_proof(self):
        proof=overlay.generate(self.options())
        self.assertEqual({p.name for p in self.out.iterdir()},{'PcbLayerRoutingPrototype.java','SOURCE_PROOF.json'})
        self.assertEqual(proof['parentSha256'],overlay.PARENT_SHA256)
        self.assertEqual(hashlib.sha256((self.out/'PcbLayerRoutingPrototype.java').read_bytes()).hexdigest(),proof['derivedSha256'])
        self.assertEqual(proof['authoredInvocations'],0)
    def test_occupied_output_never_overwrites(self):
        sentinel=self.out/'keep.txt';sentinel.write_bytes(b'PRESERVE')
        with self.assertRaises(overlay.OverlayError) as raised:overlay.generate(self.options())
        self.assertEqual(raised.exception.code,'OUTPUT_NOT_EMPTY');self.assertEqual(sentinel.read_bytes(),b'PRESERVE')
    def test_bad_parent_rejects_before_output_write(self):
        bad=self.root/'bad-source.java';bad.write_bytes(b'invalid')
        with self.assertRaises(overlay.OverlayError) as raised:overlay.generate(self.options(source=bad))
        self.assertEqual(raised.exception.code,'SOURCE_HASH');self.assertEqual(list(self.out.iterdir()),[])
    def test_bad_helper_not_executed(self):
        bad=self.root/'bad-helper.py';bad.write_bytes(b'raise RuntimeError("must not execute")\n')
        with self.assertRaises(overlay.OverlayError) as raised:overlay.load_io(bad)
        self.assertEqual(raised.exception.code,'IO_HELPER_HASH')

if __name__=='__main__':unittest.main(argv=[sys.argv[0],*unit_args])
