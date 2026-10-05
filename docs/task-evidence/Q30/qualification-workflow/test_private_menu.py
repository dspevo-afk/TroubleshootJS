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
           and node.name in ('failed_launch_seed', 'port_is_closed', 'load_warm_rows', 'validate_warm_observation', 'warm_identity_matches')]
assert len(helpers) == 5
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



class WarmObservationTests(unittest.TestCase):
    def test_failed_repeat_predecessor_replay_is_not_a_new_identity(self):
        replay = 'tsj-alpha/4/MEDIUM/RB30_CONTROL/10387'
        passed = {'outcome': 'PASS', 'replay': replay}
        failed = {'outcome': 'ERROR', 'replay': replay}
        self.assertTrue(DRIVER['warm_identity_matches'](passed, passed, replay))
        self.assertFalse(DRIVER['warm_identity_matches'](passed, failed, replay))
        self.assertFalse(DRIVER['warm_identity_matches'](failed, passed, replay))

    def test_frozen_order_candidate_mapping_and_chunk_bounds(self):
        import copy
        self.evidence = Path(tempfile.mkdtemp(prefix='q30-warm-plan-contract-'))
        frozen_path = DRIVER_PATH.parents[4] / 'tests/qualification/q30-scale-acceptance/acceptance-plan.json'
        frozen = json.loads(frozen_path.read_bytes())
        app = self.evidence / 'app'
        target = app / 'tests/qualification/q30-scale-acceptance/acceptance-plan.json'
        target.parent.mkdir(parents=True); target.write_bytes(frozen_path.read_bytes())
        sizes = {row['seed']: row['packages'] for row in frozen['cases']}
        plan = {'schema': 1, 'status': 'FROZEN_NORMAL_WARM_PLAN', 'cases': [
            {'rootSeed': seed, 'acceptedSeed': seed, 'publishedOrdinal': 0, 'packages': sizes[seed],
             'order': index, 'expectedReplay': 'tsj-alpha/4/MEDIUM/RB30_CONTROL/' + seed}
            for index, seed in enumerate(frozen['coldOrder'])]}
        good = self.evidence / 'good.json'; good.write_text(json.dumps(plan))
        rows = DRIVER['load_warm_rows'](good, app, 0, 3)
        self.assertEqual([row['rootSeed'] for row in rows], ['10387', '10226', '10014'])
        for start, count in ((-1, 1), (0, 4), (76, 2)):
            with self.assertRaises(ValueError): DRIVER['load_warm_rows'](good, app, start, count)
        bad_order = copy.deepcopy(plan); bad_order['cases'][0], bad_order['cases'][1] = bad_order['cases'][1], bad_order['cases'][0]
        bad_seed = copy.deepcopy(plan); bad_seed['cases'][0]['acceptedSeed'] = '10388'
        bad_ordinal = copy.deepcopy(plan); bad_ordinal['cases'][0]['publishedOrdinal'] = False
        bad_epoch = copy.deepcopy(plan); bad_epoch['status'] = 'DRAFT'
        for i, value in enumerate((bad_order, bad_seed, bad_ordinal, bad_epoch)):
            path = self.evidence / ('bad-' + str(i) + '.json'); path.write_text(json.dumps(value))
            with self.assertRaises(ValueError): DRIVER['load_warm_rows'](path, app, 0, 3)

    def test_terminal_token_identity_and_monotonic_time_are_required(self):
        row = {'name': 'launch', 'first': 'RB30_CONTROL', 'second': '-9223372036854775808',
            'third': 'MEDIUM', 'terminalObserved': True, 'startedHidden': False,
            'generationToken': 8, 'beforeToken': 7, 'terminalToken': 8,
            'terminalScreen': 'TICKET', 'startedMs': 100, 'finishedMs': 250, 'elapsedMs': 150}
        self.assertEqual(DRIVER['validate_warm_observation'](row, row['second']), 150)
        for changes in ({'terminalToken': 7}, {'generationToken': 7}, {'startedHidden': True},
                        {'elapsedMs': 0}, {'elapsedMs': float('nan')}, {'finishedMs': 249},
                        {'terminalObserved': False}, {'second': '-9223372036854775807'}):
            with self.assertRaises(ValueError):
                DRIVER['validate_warm_observation']({**row, **changes}, row['second'])

    def test_actual_edge_observer_measures_ordinary_event_without_mutating_seed(self):
        from playwright.sync_api import sync_playwright
        script = next(ast.literal_eval(node.value) for node in tree.body
            if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'WARM_OBSERVER_JS' for t in node.targets))
        self.evidence = Path(tempfile.mkdtemp(prefix='q30-warm-observer-canary-'))
        with sync_playwright() as pw:
            context = pw.chromium.launch_persistent_context(str(self.evidence / 'profile'), channel='msedge', headless=True)
            try:
                page = context.pages[0]
                page.set_content('<body data-player-screen="MENU"><button>Prepare exact seed</button></body>')
                page.evaluate("""() => {
                    let state = {token: 7, screen: 'MENU'};
                    window.tsjProduct = {snapshot: () => ({...state}), action: (token, view, name, family, seed, profile) => {
                        window.receivedSeed = seed;
                        state = {token: 8, screen: 'PREPARING'};
                        document.body.setAttribute('data-player-screen', state.screen);
                        setTimeout(() => { state = {token: 8, screen: 'TICKET', ready: true,
                            replay: 'tsj-alpha/4/MEDIUM/RB30_CONTROL/' + seed};
                            document.body.setAttribute('data-player-screen', state.screen); }, 60);
                    }};
                    document.querySelector('button').onclick = () => window.tsjProduct.action(7, 1, 'launch',
                        'RB30_CONTROL', '-9223372036854775808', 'MEDIUM');
                }""")
                page.evaluate(script)
                page.get_by_role('button', name='Prepare exact seed', exact=True).click()
                page.wait_for_function('() => window.__q30WarmObservation.rows[0].terminalObserved', timeout=5000)
                trace = page.evaluate('window.__q30WarmObservation')
                with (self.evidence / 'trace.json').open('x', encoding='utf-8') as stream: json.dump(trace, stream, indent=2)
                self.assertEqual(page.evaluate('window.receivedSeed'), '-9223372036854775808')
                elapsed = DRIVER['validate_warm_observation'](trace['rows'][0], '-9223372036854775808')
                self.assertGreaterEqual(elapsed, 40)
                self.assertLess(elapsed, 5000)
                self.assertEqual(trace['hiddenEvents'], [])
            finally:
                context.close()


if __name__ == '__main__':
    unittest.main()
