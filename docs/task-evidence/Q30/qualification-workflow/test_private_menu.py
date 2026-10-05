"""Focused menu identity and cleanup checks; generated canary receipts are retained."""
from pathlib import Path
import ast
import json
import os
import re
import socket
import tempfile
import unittest

import audit_release
import private_qualification
import qualify

DRIVER_PATH = Path(__file__).with_name('private_menu.py')
tree = ast.parse(DRIVER_PATH.read_text(encoding='utf-8'))
helpers = [node for node in tree.body if isinstance(node, ast.FunctionDef)
           and node.name in ('failed_launch_seed', 'port_is_closed')]
assert len(helpers) == 2
DRIVER = dict(re=re, audit_release=audit_release, Path=Path, json=json)
# The driver remains a CLI. Execute its actual helper definitions, without
# starting an HTTP server or browser merely to load these focused contracts.
exec(compile(ast.Module(body=helpers, type_ignores=[]), str(DRIVER_PATH), 'exec'), DRIVER)


class MenuIdentityTests(unittest.TestCase):
    def test_canonical_signed_long_is_retained_without_float_conversion(self):
        prefix = 'No valid board. Family: RB30_CONTROL; difficulty: MEDIUM; launch seed: '
        for seed in ('0', '-1', '9223372036854775807', '-9223372036854775808', '9007199254740993'):
            with self.subTest(seed=seed):
                self.assertEqual(DRIVER['failed_launch_seed'](prefix + seed, 'RB30_CONTROL', 'MEDIUM'), seed)
        for seed in ('-0', '+0', '01', '1.0', '1e3', '9223372036854775808', '-9223372036854775809', ' 1', '1\n'):
            with self.subTest(seed=seed):
                self.assertIsNone(DRIVER['failed_launch_seed'](prefix + seed, 'RB30_CONTROL', 'MEDIUM'))

    def test_predecessor_replay_and_wrong_family_are_not_failed_seed(self):
        for message in (None, '', 'tsj-alpha/4/MEDIUM/RB30_CONTROL/2136104807735007552',
                        'Family: RB15_CONTROL; difficulty: MEDIUM; launch seed: 4',
                        'Family: RB30_CONTROL; difficulty: EASY; launch seed: 4'):
            self.assertIsNone(DRIVER['failed_launch_seed'](message, 'RB30_CONTROL', 'MEDIUM'))

    def test_diagnostic_subset_cannot_pass_full_menu_acceptance(self):
        self.evidence = Path(tempfile.mkdtemp(prefix='q30-menu-negative-'))
        path = self.evidence / 'diagnostic-subset.json'
        with path.open('x', encoding='utf-8') as stream:
            json.dump({'outcome': 'DIAGNOSTIC_PASS', 'cases': [{'outcome': 'PASS'}]}, stream)
        with self.assertRaisesRegex(private_qualification.QualificationError, '33 launches'):
            private_qualification._validate_menu_result(path)
        self.assertIn('audit_release.py', qualify._workflow_names('private-menu'))


class NativeListenerTests(unittest.TestCase):
    def test_real_listener_presence_then_absence(self):
        powershell = os.environ.get('TSJ_POWERSHELL')
        if os.name != 'nt' or not powershell:
            self.skipTest('Requires selected physical TSJ_POWERSHELL on Windows')
        self.evidence = Path(tempfile.mkdtemp(prefix='q30-menu-listener-'))
        opened, closed = self.evidence / 'open', self.evidence / 'closed'
        opened.mkdir(); closed.mkdir()
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.bind(('127.0.0.1', 0)); listener.listen(1)
            port = listener.getsockname()[1]
            self.assertFalse(DRIVER['port_is_closed'](port, powershell, opened))
        self.assertTrue(DRIVER['port_is_closed'](port, powershell, closed))


if __name__ == '__main__':
    unittest.main()
