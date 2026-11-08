"""Runtime fixtures for the current pure kernel; no normal prior or native runs."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parent
FIXTURE_SHA = 'fa2366d1bc48f036c9b3f3eb2b6944633f8b386a7070902d8051dd92ef462973'
FIXTURE_SOURCE = None
KERNEL_DIRECTORY = ROOT

def source_module(path, name):
    module = types.ModuleType(name)
    module.__file__ = str(path)
    sys.modules[name] = module
    exec(compile(path.read_bytes(), str(path), 'exec'), module.__dict__)
    return module

class PriorKernelRuntimeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert sys.flags.isolated and sys.dont_write_bytecode
        source = KERNEL_DIRECTORY / 'prior_kernel.py'
        provenance = json.loads((KERNEL_DIRECTORY / 'PROVENANCE_R1.json').read_bytes())
        assert hashlib.sha256(source.read_bytes()).hexdigest() == provenance['kernelSha256']
        cls.kernel = source_module(source, '_current_synthetic_runtime_kernel')
        fixture_path = Path(FIXTURE_SOURCE)
        assert fixture_path.stat().st_size <= 4194304
        fixture_raw = fixture_path.read_bytes()
        assert hashlib.sha256(fixture_raw).hexdigest() == FIXTURE_SHA
        port = source_module(ROOT / 'p07_fixture_port_r1.py', '_current_runtime_fixture_port')
        cls.fixture, cls.fixture_proof = port.load_synthetic_fixture(
            fixture_raw, cls.kernel.__dict__)
        cls.raw, cls.entry = cls.fixture['make_operation'](1)
        cls.run_id = cls.fixture['RUN_ID']

    def operation(self, raw=None, source_sha=None, kind='READER_FIXTURE'):
        return self.kernel.validate_operation(
            self.raw if raw is None else raw, self.entry,
            '1' * 64 if source_sha is None else source_sha,
            '2' * 64, 1, self.run_id, kind)

    def reject(self, code, function, error_type=ValueError):
        with self.assertRaises(error_type) as captured:
            function()
        self.assertEqual(getattr(captured.exception, 'code', str(captured.exception)), code)

    def test_pinned_p07_tiny_fixture_positive(self):
        parsed = self.operation()
        self.assertEqual(len(parsed['pads']), 4)
        self.assertEqual(len(parsed['groups']), 5)
        self.assertEqual(parsed['header']['mode'], 'TINY_CANARY')
        self.assertEqual(parsed['header']['candidate']['rootSeed'], '42')
        self.assertEqual(parsed['header']['candidate']['componentCount'], 4)
        self.assertEqual(parsed['header']['candidate']['padCount'], 4)
        self.assertEqual(parsed['header']['candidate']['netCount'], 1)
        self.assertEqual(self.fixture_proof['oldModuleImports'], 0)

    def test_wrong_source_identity_rejects(self):
        self.reject('HEADER_IDENTITY', lambda: self.operation(source_sha='0' * 64))

    def test_tiny_fixture_cannot_claim_authored(self):
        self.reject('AUTHORED_CANDIDATE', lambda: self.operation(kind='AUTHORED'))

    def test_pad_record_must_be_string(self):
        rows = [json.loads(line) for line in self.raw.splitlines()]
        next(row for row in rows if row['record'] == 'PAD')['record'] = 1
        raw = b''.join((json.dumps(row, sort_keys=True, separators=(',', ':')) + '\n').encode()
                       for row in rows)
        self.reject('RECORD_TYPE', lambda: self.operation(raw=raw))

    def test_n_stable_order_has_independent_expected_order(self):
        values = ['a', 'b', 'c']
        self.assertEqual(self.kernel.stable_cohort_order(values, ['c', 'a']), ['c', 'a', 'b'])
        self.assertEqual(values, ['a', 'b', 'c'])

    def test_n_order_rejections(self):
        self.reject('BF_ORDER_DUPLICATE',
                    lambda: self.kernel.stable_cohort_order(['a', 'a'], ['a']),
                    self.kernel.N_ReaderError)
        self.reject('BF_ORDER_COHORT_UNKNOWN',
                    lambda: self.kernel.stable_cohort_order(['a'], ['b']),
                    self.kernel.N_ReaderError)

    def test_n_tree_units_and_bounds(self):
        self.assertEqual(self.kernel.result_tree_minimum_units({'x': ['a', None]}), 7)
        self.assertEqual(self.kernel.result_tree_minimum_units('a' * 4097), 3)
        self.assertEqual(self.kernel.result_tree_minimum_units('\u00e9' * 2049), 3)
        self.reject('BF_RESULT_TREE_KEY',
                    lambda: self.kernel.result_tree_minimum_units({1: None}),
                    self.kernel.N_ReaderError)
        deep = None
        for _ in range(65):
            deep = [deep]
        self.reject('BF_RESULT_TREE_DEPTH',
                    lambda: self.kernel.result_tree_minimum_units(deep),
                    self.kernel.N_ReaderError)

    def test_n_terminal_binding_and_release(self):
        terminal = dict(failedNetId='b', searchIndex=2, searchPass=2, passBefore=2,
                        passAfter=3, advanceCallOrdinal=2, ownerSearchCleared=True,
                        searchReleased=True, ownerPromotedNetId='b',
                        ownerReason='b:BRANCH_SEARCH_LIMIT')
        outcome = dict(disposition='CAP_REJECTED', reason='b:BRANCH_SEARCH_LIMIT')
        order = dict(requestedNetIds=['a', 'b'], traversedNetIds=['a', 'b'])
        work = dict(advanceCalls=3)
        self.assertIs(self.kernel.validate_prior_terminal_failure(
            terminal, outcome, order, work, control=False), terminal)
        self.reject('BF_TERMINAL_FAILURE_BINDING',
                    lambda: self.kernel.validate_prior_terminal_failure(
                        dict(terminal, searchReleased=False), outcome, order, work, control=False),
                    self.kernel.N_ReaderError)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture-source', required=True)
    parser.add_argument('--kernel-directory', type=Path, default=ROOT)
    args, unittest_args = parser.parse_known_args()
    FIXTURE_SOURCE = args.fixture_source
    KERNEL_DIRECTORY = args.kernel_directory
    unittest.main(argv=[sys.argv[0], *unittest_args])
