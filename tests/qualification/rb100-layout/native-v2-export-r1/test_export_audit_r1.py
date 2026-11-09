"""Independent root reader controls; these never call the native exporter."""
from pathlib import Path
import hashlib
import sys
import types
import unittest

assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
SOURCE = Path(__file__).resolve().with_name('audit_export_files_r1.py')
raw = SOURCE.read_bytes()
reader = types.ModuleType('_root_synthetic_export_file_audit')
reader.__file__ = str(SOURCE)
exec(compile(raw, str(SOURCE), 'exec'), reader.__dict__)


class ReaderControls(unittest.TestCase):
    def test_independent_canonical_scalar_bytes(self):
        value = reader.strict_json(b'{"a":[null,true,-9223372036854775808,"q\\\"\\\\"]}\n',
                                   256, canonical=True)
        self.assertEqual(value, {'a': [None, True, -9223372036854775808, 'q"\\']})
        reader.ordinary_domain(value)

    def test_duplicates_and_noninteger_tokens(self):
        for bad in (b'{"a":1,"a":2}\n', b'{"a":1.0}\n', b'{"a":NaN}\n', b'{"a":Infinity}\n'):
            with self.subTest(raw=bad), self.assertRaises(ValueError):
                reader.strict_json(bad, 256, canonical=True)

    def test_noncanonical_and_suffix(self):
        for bad in (b'{"a": 1}\n', b'{"a":1}', b'{"a":1}\n\n', b'{"a":1}\n{}\n',
                    b'{"a":"\\u0041"}\n'):
            with self.subTest(raw=bad), self.assertRaises((AssertionError, ValueError)):
                reader.strict_json(bad, 256, canonical=True)

    def test_size_ascii_and_domain(self):
        for raw, cap in ((b'{}\n', 2), (b'{"a":"\xff"}\n', 256)):
            with self.subTest(raw=raw), self.assertRaises((AssertionError, UnicodeError)):
                reader.strict_json(raw, cap, canonical=True)
        for value in ({'a': '\u00e9'}, {'a': 'x'*257}, {'a': 9223372036854775808},
                      {'a': -9223372036854775809}, {'a': 1.0}, {'a': object()}):
            with self.subTest(value=value), self.assertRaises(AssertionError):
                reader.ordinary_domain(value)

    def test_exact_integer_and_marker_bounds(self):
        self.assertEqual(reader.number(656, 656), 656)
        for value in (True, False, -1, 657, 1.0):
            with self.subTest(value=value), self.assertRaises(AssertionError):
                reader.number(value, 656)

    def test_shallow_path_escape_rejected_before_io(self):
        root = SOURCE.parent
        for value in ('../outside', '/outside', 'a\\b', ''):
            with self.subTest(value=value), self.assertRaises(AssertionError):
                reader.owned(root, value)


if __name__ == '__main__':
    print('ROOT_READER_CONTROLS_ONLY sourceSha256=' + hashlib.sha256(raw).hexdigest(), flush=True)
    unittest.main(verbosity=2)
