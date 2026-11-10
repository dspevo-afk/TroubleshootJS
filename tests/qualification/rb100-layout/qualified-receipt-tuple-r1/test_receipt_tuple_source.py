"""Source-only tests. No class loading, builders, data or receipt I/O."""
import argparse
import ast
import hashlib
from pathlib import Path
import sys
import types
import unittest

assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R = Path(__file__).resolve().parent
owner = types.ModuleType('_receipt_tuple_source')
exec(compile((R/'derive_receipt_tuple.py').read_bytes(), 'derive_receipt_tuple.py',
             'exec', dont_inherit=True, optimize=0), owner.__dict__)


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.metadata = SOURCE.read_bytes()
        cls.fragment = (R/'QualifiedReceiptTuple.java.fragment').read_bytes()

    def derive(self, metadata=None, fragment=None):
        return owner.derive(self.metadata if metadata is None else metadata,
                            self.fragment if fragment is None else fragment)

    def reject(self, code, metadata=None, fragment=None):
        with self.assertRaisesRegex(owner.SourceError, '^'+code+'$'):
            self.derive(metadata, fragment)

    def test_exact_pins(self):
        for raw, n, pin in [(self.metadata,9970,owner.METADATA_SHA),
                            (self.fragment,2443,owner.FRAGMENT_SHA)]:
            self.assertEqual(len(raw), n)
            self.assertEqual(hashlib.sha256(raw).hexdigest(), pin)

    def test_inverse_conserves_other_bytes(self):
        result = self.derive()
        old = result.replace(owner.CALL, b'', 1).replace(self.fragment+b'\n', b'', 1)
        self.assertEqual(old, self.metadata)
        self.assertEqual(result.count(self.fragment), 1)
        self.assertEqual(result.count(owner.CALL), 1)

    def test_call_precedes_copy_and_binding(self):
        result = self.derive()
        shape = result.index(owner.CALL_ANCHOR)
        call = result.index(owner.CALL)
        copy = result.index(b'        meter.copy(12);', shape)
        binding = result.index(b'        ReceiptContentBinding ticket =', shape)
        mutation = result.index(b'        body.put("qualifiedPrior", qualified);', shape)
        self.assertLess(shape, call)
        self.assertLess(call, copy)
        self.assertLess(copy, binding)
        self.assertLess(binding, mutation)

    def test_determinism(self):
        self.assertEqual(self.derive(), self.derive())

    def test_changed_owner(self):
        self.reject('METADATA_SOURCE', b'x'+self.metadata[1:])

    def test_changed_fragment(self):
        self.reject('FRAGMENT_SOURCE', fragment=b'x'+self.fragment[1:])

    def test_truncated_inputs(self):
        self.reject('METADATA_SOURCE', self.metadata[:-1])
        self.reject('FRAGMENT_SOURCE', fragment=self.fragment[:-1])

    def test_old_size_synthetic_input(self):
        self.reject('METADATA_SOURCE', b'x'*6856)

    def test_oversize(self):
        self.reject('METADATA_SOURCE', b'x'*(owner.SOURCE_CAP+1))

    def test_mutable_input_rejected(self):
        self.reject('METADATA_SOURCE', bytearray(self.metadata))

    def test_already_derived_rejected(self):
        self.reject('METADATA_SOURCE', self.derive())

    def test_owner_is_pure(self):
        tree = ast.parse((R/'derive_receipt_tuple.py').read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(tree)
                          if isinstance(x, ast.Import)], ['hashlib'])
        self.assertFalse(any(isinstance(x, ast.ImportFrom) for x in ast.walk(tree)))
        self.assertFalse(any(isinstance(x, ast.Call) and isinstance(x.func, ast.Name)
                             and x.func.id in {'open','exec','eval','compile','__import__'}
                             for x in ast.walk(tree)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--metadata-source', required=True, type=Path)
    args, rest = parser.parse_known_args()
    SOURCE = args.metadata_source
    unittest.main(argv=[sys.argv[0]]+rest)
