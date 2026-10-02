"""Independent source/path rejection fixtures for the read-only cold adapter."""
import json
from pathlib import Path
import tempfile
import unittest

import normalize_cold_evidence as adapter


class NormalizerTests(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp(prefix="q30-normalizer-test-"))
        (self.base / 'input.txt').write_bytes(b'independent fixture')
        rows = [{'path': 'input.txt', 'size': 19,
                 'sha256': adapter.sha(b'independent fixture')}]
        self.snapshot = {'schema': 1, 'baseHead': 'b'*40, 'files': rows}
        identity = adapter.sha(json.dumps(self.snapshot, sort_keys=True,
                           separators=(',', ':'), ensure_ascii=False).encode('utf-8'))
        self.snapshot['sourceIdentity'] = identity
        self.plan = {'baseHead': 'b'*40, 'sourceIdentity': identity}

    def test_actual_relative_file_and_canonical_identity_pass(self):
        self.assertEqual(adapter.safe_file(self.base, 'input.txt'), self.base / 'input.txt')
        adapter.verify_snapshot(self.snapshot, self.plan, self.base)

    def test_windows_posix_and_noncanonical_paths_reject(self):
        for path in ('../input.txt', 'C:/input.txt', 'C:input.txt', r'..\input.txt',
                     '/input.txt', './input.txt', 'a//b', r'\\server\input.txt'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                adapter.safe_file(self.base, path)

    def test_declared_identity_or_head_mismatch_rejects(self):
        self.plan['sourceIdentity'] = 'c'*64
        with self.assertRaises(ValueError): adapter.verify_snapshot(self.snapshot, self.plan, self.base)
        self.plan['sourceIdentity'] = self.snapshot['sourceIdentity']
        self.plan['baseHead'] = 'd'*40
        with self.assertRaises(ValueError): adapter.verify_snapshot(self.snapshot, self.plan, self.base)

    def test_changed_source_bytes_reject(self):
        (self.base / 'input.txt').write_bytes(b'changed fixture')
        with self.assertRaises(ValueError): adapter.verify_snapshot(self.snapshot, self.plan, self.base)

    def test_duplicate_manifest_paths_reject(self):
        self.snapshot['files'] *= 2
        payload = {key:self.snapshot[key] for key in ('schema', 'baseHead', 'files')}
        digest = adapter.sha(json.dumps(payload, sort_keys=True, separators=(',', ':'),
                             ensure_ascii=False).encode('utf-8'))
        self.snapshot['sourceIdentity'] = self.plan['sourceIdentity'] = digest
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            adapter.verify_snapshot(self.snapshot, self.plan, self.base)
