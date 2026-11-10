"""Source-only transform checks; no JVM, owner, factory or receipt calls."""
import argparse
import ast
import hashlib
from pathlib import Path
import sys
import types
import unittest
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
owner=types.ModuleType('_copy_effects_source_owner')
exec(compile((R/'derive_copy_effects_summary.py').read_bytes(),'derive_copy_effects_summary.py',
             'exec',dont_inherit=True,optimize=0),owner.__dict__)


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.codec=SOURCE.read_bytes()
        cls.checks=(R/'EffectsChecks.java.fragment').read_bytes()
        cls.reader=(R/'EffectsString.java.fragment').read_bytes()

    def derive(self,**kw):
        values=dict(codec=self.codec,checks=self.checks,reader=self.reader);values.update(kw)
        return owner.derive(**values)

    def reject(self,code,**kw):
        with self.assertRaisesRegex(owner.SourceError,'^'+code+'$'):self.derive(**kw)

    def test_pins(self):
        for raw,n,pin in [(self.codec,31250,owner.CODEC_SHA),(self.checks,844,owner.CHECKS_SHA),(self.reader,511,owner.READER_SHA)]:
            self.assertEqual(len(raw),n);self.assertEqual(hashlib.sha256(raw).hexdigest(),pin)

    def test_inverse_conservation(self):
        result=self.derive();inverse=result.replace(self.checks,b'',1).replace(self.reader,owner.OLD_READER,1)
        for call in owner.OLD_CALLS:inverse=inverse.replace(owner.new_call(call),call,1)
        self.assertEqual(inverse,self.codec)
        self.assertEqual(result.count(self.checks),1);self.assertEqual(result.count(self.reader),1)

    def test_existing_errors_and_single_reader(self):
        result=self.derive()
        for call in owner.OLD_CALLS:self.assertEqual(result.count(owner.new_call(call)),1)
        self.assertEqual(result.count(b'private static String correlationString('),1)
        self.assertNotIn(owner.OLD_READER,result)
        self.assertEqual(result.count(b'"BODY_GEOMETRY_STRING"'),6)
        self.assertIn(b'text.length() > BODY_STRING_LIMIT',self.reader)
        self.assertNotIn(b'256',self.reader)

    def test_old_precedence_and_lower_suffix(self):
        result=self.derive();self.assertLess(result.index(owner.ANCHOR),result.index(self.checks))
        start=self.codec.index(b'    static void writeBody(')
        self.assertTrue(result.endswith(self.codec[start:]))
        self.assertEqual(result.count(b'    static void writeCorrelatedBody('),1)

    def test_determinism(self):self.assertEqual(self.derive(),self.derive())
    def test_changed_codec(self):self.reject('CODEC_SOURCE',codec=b'x'+self.codec[1:])
    def test_changed_checks(self):self.reject('CHECKS_SOURCE',checks=b'x'+self.checks[1:])
    def test_changed_reader(self):self.reject('READER_SOURCE',reader=b'x'+self.reader[1:])

    def test_truncation(self):
        for name,code in [('codec','CODEC_SOURCE'),('checks','CHECKS_SOURCE'),('reader','READER_SOURCE')]:self.reject(code,**{name:getattr(self,name)[:-1]})

    def test_oversize_and_mutable(self):
        self.reject('CODEC_SOURCE',codec=b'x'*(owner.SOURCE_CAP+1))
        self.reject('CODEC_SOURCE',codec=bytearray(self.codec))

    def test_already_derived(self):self.reject('CODEC_SOURCE',codec=self.derive())

    def test_pure_owner(self):
        tree=ast.parse((R/'derive_copy_effects_summary.py').read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(tree) if isinstance(x,ast.Import)],['hashlib'])
        self.assertFalse(any(isinstance(x,ast.ImportFrom) for x in ast.walk(tree)))
        self.assertFalse(any(isinstance(x,ast.Call) and isinstance(x.func,ast.Name) and x.func.id in {'open','exec','eval','compile','__import__'} for x in ast.walk(tree)))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--codec-source',required=True,type=Path)
    args,rest=parser.parse_known_args();SOURCE=args.codec_source
    unittest.main(argv=[sys.argv[0]]+rest)
