"""Finite Source API gates, with no Java, producer or receipt execution."""
import argparse
import ast
import hashlib
from pathlib import Path
import sys
import types
import unittest
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R = Path(__file__).resolve().parent
owner = types.ModuleType('_cleanup_source_owner')
exec(compile((R/'derive_cleanup_restoration.py').read_bytes(),
             'derive_cleanup_restoration.py', 'exec', dont_inherit=True, optimize=0),
     owner.__dict__)


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.codec = SOURCE.read_bytes()
        cls.checks = (R/'RestorationChecks.java.fragment').read_bytes()
        cls.reader = (R/'RestorationBoolean.java.fragment').read_bytes()

    def derive(self, **kw):
        values = dict(codec=self.codec, checks=self.checks, reader=self.reader)
        values.update(kw)
        return owner.derive(**values)

    def reject(self, code, **kw):
        with self.assertRaisesRegex(owner.SourceError, '^'+code+'$'):
            self.derive(**kw)

    def test_pinned_inputs(self):
        for raw,n,pin in [(self.codec,23469,owner.CODEC_SHA),
                          (self.checks,1256,owner.CHECKS_SHA),
                          (self.reader,430,owner.READER_SHA)]:
            self.assertEqual(len(raw),n)
            self.assertEqual(hashlib.sha256(raw).hexdigest(),pin)

    def test_inverse_conserves_all_other_bytes(self):
        result=self.derive()
        inverse=result.replace(self.checks,b'',1).replace(self.reader+b'\n',b'',1)
        self.assertEqual(inverse,self.codec)
        self.assertEqual(result.count(self.checks),1)
        self.assertEqual(result.count(self.reader),1)

    def test_precedence_and_transport_conservation(self):
        result=self.derive()
        self.assertLess(result.index(owner.CHECK_ANCHOR),result.index(self.checks))
        start=self.codec.index(b'    static void writeBody(')
        self.assertTrue(result.endswith(self.codec[start:]))
        self.assertEqual(result.count(b'    static void writeCorrelatedBody('),1)
        self.assertEqual(result.count(b'    static void writeBody('),1)

    def test_determinism(self):
        self.assertEqual(self.derive(),self.derive())

    def test_modified_codec(self):
        self.reject('CODEC_SOURCE',codec=b'x'+self.codec[1:])

    def test_modified_checks(self):
        self.reject('CHECKS_SOURCE',checks=b'x'+self.checks[1:])

    def test_modified_reader(self):
        self.reject('READER_SOURCE',reader=b'x'+self.reader[1:])

    def test_truncated(self):
        for name,code in [('codec','CODEC_SOURCE'),('checks','CHECKS_SOURCE'),('reader','READER_SOURCE')]:
            self.reject(code,**{name:getattr(self,name)[:-1]})

    def test_oversize(self):
        self.reject('CODEC_SOURCE',codec=b'x'*(owner.SOURCE_CAP+1))

    def test_mutable_input(self):
        self.reject('CODEC_SOURCE',codec=bytearray(self.codec))

    def test_already_derived(self):
        self.reject('CODEC_SOURCE',codec=self.derive())

    def test_pure_owner(self):
        tree=ast.parse((R/'derive_cleanup_restoration.py').read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(tree) if isinstance(x,ast.Import)],['hashlib'])
        self.assertFalse(any(isinstance(x,ast.ImportFrom) for x in ast.walk(tree)))
        self.assertFalse(any(isinstance(x,ast.Call) and isinstance(x.func,ast.Name)
                             and x.func.id in {'open','exec','eval','compile','__import__'}
                             for x in ast.walk(tree)))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--codec-source',required=True,type=Path)
    args,rest=parser.parse_known_args();SOURCE=args.codec_source
    unittest.main(argv=[sys.argv[0]]+rest)
