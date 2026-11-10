"""Eight focused Source checks; no JVM, owner, factory or receipt calls."""
import argparse
import ast
import hashlib
from pathlib import Path
import sys
import types
import unittest
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
owner=types.ModuleType('_arm_projection_source_owner')
exec(compile((R/'derive_arm_projection.py').read_bytes(),'derive_arm_projection.py',
             'exec',dont_inherit=True,optimize=0),owner.__dict__)


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.codec=SOURCE.read_bytes()
        cls.checks=(R/'ArmProjectionChecks.java.fragment').read_bytes()

    def derive(self,**kw):
        values=dict(codec=self.codec,checks=self.checks);values.update(kw)
        return owner.derive(**values)

    def reject(self,code,**kw):
        with self.assertRaisesRegex(owner.SourceError,'^'+code+'$'):self.derive(**kw)

    def test_pins_and_single_check(self):
        for raw,n,pin in [(self.codec,37585,owner.CODEC_SHA),(self.checks,1950,owner.CHECKS_SHA)]:
            self.assertEqual(len(raw),n);self.assertEqual(hashlib.sha256(raw).hexdigest(),pin)
        result=self.derive();self.assertEqual(result.count(self.checks),1)
        self.assertEqual(result.count(owner.CALL),1)

    def test_inverse_prefix_and_lower_conservation(self):
        result=self.derive();self.assertEqual(result.replace(self.checks,b'',1).replace(owner.CALL,b'',1),self.codec)
        at=self.codec.index(owner.ANCHOR)+len(owner.ANCHOR)
        self.assertEqual(result[:at],self.codec[:at])
        self.assertTrue(result.endswith(self.codec[self.codec.index(b'    static void writeBody('):]))

    def test_changed_codec(self):self.reject('CODEC_SOURCE',codec=b'x'+self.codec[1:])
    def test_changed_checks(self):self.reject('CHECKS_SOURCE',checks=b'x'+self.checks[1:])

    def test_truncated_inputs(self):
        self.reject('CODEC_SOURCE',codec=self.codec[:-1])
        self.reject('CHECKS_SOURCE',checks=self.checks[:-1])

    def test_type_and_cap_admission(self):
        self.reject('CODEC_SOURCE',codec=bytearray(self.codec))
        self.reject('CHECKS_SOURCE',checks=bytearray(self.checks))
        self.reject('CODEC_SOURCE',codec=b'x'*(owner.SOURCE_CAP+1))
        self.reject('CHECKS_SOURCE',checks=b'x'*(owner.SOURCE_CAP+1))

    def test_already_derived_rejected(self):self.reject('CODEC_SOURCE',codec=self.derive())

    def test_pure_owner(self):
        tree=ast.parse((R/'derive_arm_projection.py').read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(tree) if isinstance(x,ast.Import)],['hashlib'])
        self.assertFalse(any(isinstance(x,ast.ImportFrom) for x in ast.walk(tree)))
        self.assertFalse(any(isinstance(x,ast.Call) and isinstance(x.func,ast.Name) and x.func.id in {'open','exec','eval','compile','__import__'} for x in ast.walk(tree)))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--codec-source',required=True,type=Path)
    args,rest=parser.parse_known_args();SOURCE=args.codec_source
    unittest.main(argv=[sys.argv[0]]+rest)
