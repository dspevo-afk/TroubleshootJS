"""Root-owned SOURCE-only composition checks; no legacy module is executed."""
import argparse
import ast
import copy
import hashlib
import json
from pathlib import Path
import sys
import types
import unittest


parser = argparse.ArgumentParser()
parser.add_argument('--generator-source', required=True)
parser.add_argument('--adapter-source', required=True)
parser.add_argument('--source-root', required=True)
args, remaining = parser.parse_known_args()
sys.argv = [sys.argv[0], *remaining]
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize


def load_source(path, name):
    raw = Path(path).read_bytes()
    assert 0 < len(raw) <= 4194304
    module = types.ModuleType(name)
    module.__file__ = str(path)
    sys.modules[name] = module
    exec(compile(raw, str(path), 'exec'), module.__dict__)
    return module


class FullDGenerationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generator = load_source(args.generator_source, '_root_full_d_generator_under_test')
        cls.bundle = {name: (Path(args.source_root) / name).read_bytes()
                      for name in cls.generator.SOURCE_PINS}
        cls.adapter = Path(args.adapter_source).read_bytes()

    def generate(self, bundle=None, adapter=None):
        return self.generator.generate_full_d_dependencies(
            source_bundle=copy.deepcopy(self.bundle) if bundle is None else bundle,
            semantic_adapter_source=self.adapter if adapter is None else adapter)

    def reject(self, *, bundle=None, adapter=None):
        with self.assertRaises(self.generator.GenerationError):
            self.generate(bundle=bundle, adapter=adapter)

    def test_reproducible_self_contained_emission_and_unchanged_inputs(self):
        before = copy.deepcopy(self.bundle)
        one = self.generate()
        two = self.generate()
        self.assertEqual(one.kernel_source, two.kernel_source)
        self.assertEqual(one.provenance_json, two.provenance_json)
        self.assertEqual(self.bundle, before)
        self.assertIs(type(one.kernel_source), bytes)
        self.assertIs(type(one.provenance_json), bytes)
        self.assertTrue(one.provenance_json.endswith(b'\n'))
        proof = json.loads(one.provenance_json)
        self.assertIs(type(proof), dict)
        tree = ast.parse(one.kernel_source)
        allowed = {'__future__', 'base64', 'collections', 'hashlib', 'heapq',
                   'json', 're', 'types', 'typing'}
        forbidden_calls = {'open', 'exec', 'eval', 'compile', '__import__',
                           'getattr', 'setattr', 'globals', 'locals', 'vars',
                           'input', 'breakpoint'}
        functions = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    self.assertIn(alias.name, allowed)
            if isinstance(node, ast.ImportFrom):
                self.assertEqual(node.level, 0)
                self.assertIn(node.module, allowed)
                self.assertTrue(all(alias.name != '*' for alias in node.names))
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
                self.assertNotIn(node.func.id, forbidden_calls)
            if isinstance(node, ast.FunctionDef):
                functions.add(node.name)
        self.assertIn('validate_full_d_v2', functions)
        self.assertGreater(len(one.kernel_source), len(self.adapter))
        self.assertLessEqual(len(one.kernel_source), 4194304)
        # Loading the new emitted owner proves isolated imports resolve. None of
        # the original whole-source oracle modules is imported or executed.
        emitted = types.ModuleType('_root_full_d_emitted_positive')
        sys.modules[emitted.__name__] = emitted
        exec(compile(one.kernel_source, '<emitted-full-d-kernel>', 'exec'), emitted.__dict__)
        self.assertTrue(callable(emitted.validate_full_d_v2))
        self.assertEqual(emitted.RESULT_SCHEMA, 'tsj-cohort-terminal-result-v2')
        self.assertEqual(emitted.MAX_MARKER_BYTES, 656)
        self.assertEqual(emitted.MAX_MARKER_TRANSPORT_BYTES, 722)

    def test_every_pinned_source_one_byte_mutation_rejected(self):
        for name in self.bundle:
            with self.subTest(source=name):
                mutated = dict(self.bundle)
                raw = mutated[name]
                mutated[name] = bytes([raw[0] ^ 1]) + raw[1:]
                self.reject(bundle=mutated)

    def test_missing_source_rejected(self):
        value = dict(self.bundle)
        del value[next(iter(value))]
        self.reject(bundle=value)

    def test_extra_source_rejected(self):
        self.reject(bundle={**self.bundle, 'foreign.py': b'x = 1\n'})

    def test_nonbyte_source_rejected(self):
        value = dict(self.bundle)
        value[next(iter(value))] = 'source is not bytes'
        self.reject(bundle=value)

    def test_empty_adapter_rejected(self):
        self.reject(adapter=b'')

    def test_nonbyte_adapter_rejected(self):
        self.reject(adapter='source is not bytes')

    def test_oversize_adapter_rejected(self):
        self.reject(adapter=b' ' * 4194305)

    def test_syntax_error_rejected(self):
        self.reject(adapter=b'def (:\n')

    def test_foreign_import_rejected(self):
        self.reject(adapter=self.adapter + b'\nimport os\n')

    def test_foreign_local_import_rejected(self):
        self.reject(adapter=self.adapter + b'\nfrom source.foreign import authority\n')

    def test_open_call_rejected(self):
        self.reject(adapter=self.adapter + b'\ndef bad():\n    return open("x")\n')

    def test_dynamic_code_call_rejected(self):
        self.reject(adapter=self.adapter + b'\ndef bad():\n    return eval("1")\n')

    def test_absolute_path_literal_rejected(self):
        self.reject(adapter=self.adapter + b'\nFOREIGN_PATH = "/tmp/foreign-authority"\n')

    def test_namespace_global_lambda_and_inlined_comprehension_shadowing(self):
        source = b'''value = 7
def probe():
    local = (lambda value: value + 1)(3)
    direct = [value for value in range(3)]
    mixed = [value + offset for value in range(2) for offset in range(value)]
    delayed = list(value for value in range(2))
    return value, local, direct, mixed, delayed
'''
        tree, mapping, original_sha, renamed_sha = self.generator._namespace_module(
            'independent-shadowing', source, 'NS_')
        self.assertEqual(mapping['value'], 'NS_value')
        self.assertNotEqual(original_sha, renamed_sha)
        namespace = {}
        exec(compile(ast.fix_missing_locations(tree), '<authored-shadowing>', 'exec'), namespace)
        self.assertEqual(namespace['NS_probe'](), (7, 4, [0, 1, 2], [1], [0, 1]))

    def test_namespace_closure_nonlocal_and_explicit_global(self):
        source = b'''value = 7
def global_step():
    global value
    return value
def closure():
    value = 12
    def step():
        nonlocal value
        value += 1
        return value
    return step(), value
'''
        tree, _mapping, _before, _after = self.generator._namespace_module(
            'independent-closure', source, 'NS_')
        namespace = {}
        exec(compile(ast.fix_missing_locations(tree), '<authored-closure>', 'exec'), namespace)
        self.assertEqual(namespace['NS_global_step'](), 7)
        self.assertEqual(namespace['NS_closure'](), (13, 13))
        self.assertEqual(namespace['NS_value'], 7)

    def test_namespace_global_mutation_rejected(self):
        source = b'value = 7\ndef step():\n    global value\n    value += 1\n'
        with self.assertRaises(self.generator.GenerationError):
            self.generator._namespace_module('independent-global-mutation', source, 'NS_')

    def test_namespace_local_capture_rejected(self):
        source = b'value = 7\ndef probe():\n    NS_value = 3\n    return value + NS_value\n'
        with self.assertRaises(self.generator.GenerationError):
            self.generator._namespace_module('independent-local-capture', source, 'NS_')

    def test_namespace_module_capture_rejected(self):
        source = b'value = 7\nNS_value = 3\n'
        with self.assertRaises(self.generator.GenerationError):
            self.generator._namespace_module('independent-module-capture', source, 'NS_')


if __name__ == '__main__':
    unittest.main()
