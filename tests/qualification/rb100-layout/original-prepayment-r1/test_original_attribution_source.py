"""Independent Source/provenance controls; never invoke a native producer."""
from pathlib import Path
import argparse
import importlib.util
import sys
import types
import unittest

ROOT=Path(__file__).resolve().parent
_parser=argparse.ArgumentParser(add_help=False)
_parser.add_argument("--source-root",type=Path,default=ROOT/"source")
_args,_unittest_args=_parser.parse_known_args()
SOURCE=_args.source_root
sys.argv=[sys.argv[0],*_unittest_args]
spec=importlib.util.spec_from_file_location("_attribution_generator", ROOT/"generate_original_attribution.py")
G=importlib.util.module_from_spec(spec)
spec.loader.exec_module(G)

class SourceTests(unittest.TestCase):
    def setUp(self):
        self.study=(SOURCE/"CURRENT_STUDY.java").read_bytes()
        self.reference=(SOURCE/"Q_AUTHORED_STUDY.java").read_bytes()
        self.inline=(ROOT/"OriginalAttribution.java.fragment").read_bytes()
        self.checks=(ROOT/"OriginalAttributionChecks.java.fragment").read_bytes()
        self.template=b"public final class OriginalAttributionCanary {\n"+G.MARKER+b"\n}\n"
    def derive(self, **changes):
        args={key:getattr(self,key) for key in ("study","reference","inline","checks","template")}
        args.update(changes)
        return G.derive(**args)
    def test_prefix_suffix_and_helpers(self):
        emitted,canary,proof=self.derive()
        at=self.study.rfind(b'}')
        self.assertEqual(emitted[:at], self.study[:at])
        self.assertEqual(emitted[-len(self.study[at:]):],self.study[at:])
        self.assertEqual(emitted,self.study[:at]+self.checks+b"\n"+self.study[at:])
        self.assertEqual(canary,self.template.replace(G.MARKER,self.inline.rstrip(b"\n")))
        self.assertTrue(proof["assemblyClosed"])
        self.assertEqual(proof["boundaryUnits"],9)
        self.assertEqual(proof["newResultDtos"],0)
    def test_independent_read_and_arithmetic_inventory(self):
        # Literal independent expectations, not generated proof counters.
        text=self.inline.decode("ascii")
        reads=['field(m.meter, "units")','setup.units','field(original, "groupCount")',
               'field(m.meter, "groups")','setup.groups']
        self.assertEqual(text.count("m.charge("),1)
        self.assertIn("m.charge(9L);",text)
        last=text.index("m.charge(9L);")
        for read in reads:
            self.assertEqual(text.count(read),1)
            self.assertGreater(text.index(read),last)
            last=text.index(read)
        self.assertEqual(text.count("r7Subtract("),3)
        self.assertEqual(text.count("r5Add("),1)
        self.assertIn("r7Subtract(attributionRawUnits, 9L)",text)
        self.assertNotIn("m.copy(",text)
        self.assertNotIn("m.finish(",text)
        self.assertNotIn("new OriginalArm",text)
        self.assertNotIn("new ArmResult",text)
    def test_strict_wrapper_policy(self):
        self.assertIn(b"value.getClass() != Long.class",self.checks)
        self.assertIn(b"value.getClass() != Integer.class",self.checks)
        self.assertNotIn(b"instanceof Number",self.checks)
        self.assertEqual(self.checks.count(b"number < 0"),2)
    def test_parent_mismatches(self):
        for name in ("study","reference"):
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,"SOURCE_SHA"):
                self.derive(**{name:getattr(self,name)+b"\n"})
    def test_fragment_mismatches(self):
        for name,raw in (("inline",self.inline.replace(b"charge(9L)",b"charge(5L)")),
                         ("checks",self.checks.replace(b"Long.class",b"Number.class"))):
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,"SOURCE_SHA"):
                self.derive(**{name:raw})
    def test_marker_missing_or_duplicate(self):
        for raw in (self.template.replace(G.MARKER,b""),self.template+G.MARKER):
            with self.subTest(raw=raw[:32]),self.assertRaisesRegex(ValueError,"TEMPLATE_MARKER"):
                self.derive(template=raw)
    def test_template_class_and_bounds(self):
        with self.assertRaisesRegex(ValueError,"TEMPLATE_CLASS"):
            self.derive(template=self.template.replace(b"OriginalAttributionCanary",b"WrongClass"))
        for raw in (b"", b"x"*(G.CAP+1)):
            with self.subTest(size=len(raw)),self.assertRaisesRegex(ValueError,"TEMPLATE_SIZE"):
                self.derive(template=raw)
    def test_all_output_hashes(self):
        emitted,canary,proof=self.derive()
        for raw,row in zip((emitted,canary),proof["outputs"]):
            self.assertEqual(len(raw),row["bytes"])
            self.assertEqual(G.sha(raw),row["sha256"])
    def test_access_time_is_not_content_identity(self):
        fields=dict(st_dev=1,st_ino=2,st_mode=3,st_nlink=1,st_size=4,
                    st_mtime_ns=5,st_ctime_ns=6,st_file_attributes=0,st_atime_ns=7)
        before=types.SimpleNamespace(**fields)
        fields["st_atime_ns"]=100
        self.assertEqual(G.snapshot(before),G.snapshot(types.SimpleNamespace(**fields)))
        fields["st_mtime_ns"]=8
        self.assertNotEqual(G.snapshot(before),G.snapshot(types.SimpleNamespace(**fields)))

if __name__=="__main__":
    assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
    unittest.main()
