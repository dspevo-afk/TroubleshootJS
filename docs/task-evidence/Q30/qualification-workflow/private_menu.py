"""Compiled menu/replay and privacy checks; headless snapshots do not prove visible repair."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from datetime import datetime, timezone
import importlib.util
import socket
from playwright.sync_api import sync_playwright
from threading import Thread
import json
import os
import subprocess
import sys
import time
import traceback

EXPECTED_CATALOG = (
    ('LED_INDICATOR', 'EASY'),
    ('DIODE_PROTECTED_INDICATOR', 'EASY'),
    ('PARALLEL_DUAL_INDICATOR', 'EASY'),
    ('RC_DELAY', 'EASY'),
    ('NPN_LOW_SIDE_SWITCH', 'EASY'),
    ('NMOS_LOW_SIDE_SWITCH', 'EASY'),
    ('RELAY_OUTPUT', 'EASY'),
    ('SENSOR_CONTROL', 'EASY'),
    ('RB15_CONTROL', 'EASY'),
    ('COMPOSED_CONTROLLED_INDICATOR', 'MEDIUM'),
    ('RB30_CONTROL', 'MEDIUM'),
)
NORMAL_JOB_MILLIS = 90000
OBSERVATION_MARGIN_SECONDS = 25
PROMOTED_FROM_SHA256 = '75e79a0ba5bcb1e3365aee413f0fd94f25d560e08e1c9cd8816896633c3e5b1a'
IDENTITY = None

root, output = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
output.mkdir(parents=True, exist_ok=False)


class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def identity_module():
    global IDENTITY
    if IDENTITY is None:
        path = Path(__file__).with_name('windows_process_identity.py')
        spec = importlib.util.spec_from_file_location('tsj_private_windows_process_identity', path)
        if spec is None or spec.loader is None:
            raise RuntimeError('Maintained Windows process identity module is unavailable')
        IDENTITY = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(IDENTITY)
    return IDENTITY


def cim_record(row):
    try:
        created = datetime.fromisoformat(row['created'].replace('Z', '+00:00'))
        if created.tzinfo is None:
            created = created.replace(tzinfo=timezone.utc)
        created = created.astimezone(timezone.utc)
        epoch = datetime(1601, 1, 1, tzinfo=timezone.utc)
        delta = created - epoch
        ticks = ((delta.days * 86400 + delta.seconds) * 10_000_000 +
                 delta.microseconds * 10)
        recorded = {'pid': row['pid'], 'creationFileTimeTicks': ticks,
                    'birthPrecision': identity_module().CIM_MICROSECOND,
                    'executable': row['ExecutablePath']}
    except (KeyError, TypeError, ValueError, OverflowError) as error:
        raise RuntimeError('CIM process record is incomplete') from error
    query = identity_module().query_process(row['pid'])
    comparison = identity_module().compare_identity(recorded, query)
    if comparison['status'] == identity_module().ABSENT:
        return None
    # CIM supplies discovery metadata only. Retain a fresh complete native
    # identity; coarse CIM/native interval overlap is never a retired-instance
    # MATCH. An overlapping discovery must still agree on its image, and the
    # exact native record must validate independently against this same query.
    native = query.get('identity')
    native_match = identity_module().compare_identity(native, query)
    overlap = comparison.get('reason') == 'cross-precision-birth-overlap'
    same_image = (isinstance(native, dict) and isinstance(row.get('ExecutablePath'), str) and
                  os.path.normcase(os.path.normpath(native.get('executable', ''))) ==
                  os.path.normcase(os.path.normpath(row['ExecutablePath'])))
    if (native_match['status'] != identity_module().MATCH or not same_image or
            not (overlap or comparison['status'] == identity_module().MATCH)):
        raise RuntimeError('CIM discovery conflicts with a complete native identity: ' +
                           repr(comparison))
    return native


def processes():
    query = ("Get-CimInstance Win32_Process | Select-Object "
             "@{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},"
             "@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},"
             "ExecutablePath | ConvertTo-Json -Compress")
    powershell = os.environ.get('TSJ_POWERSHELL')
    if not powershell:
        raise RuntimeError('Selected PowerShell path was not supplied by the phase owner')
    result = subprocess.run([powershell, '-NoProfile', '-NonInteractive',
                             '-Command', query], capture_output=True, text=True,
                            check=False)
    if result.returncode:
        raise RuntimeError('Process identity query failed: ' + result.stderr)
    rows = json.loads(result.stdout)
    return rows if isinstance(rows, list) else [rows]


def owned_edges():
    rows, pids = processes(), {os.getpid()}
    while True:
        expanded = pids | {row['pid'] for row in rows if row['parent'] in pids}
        if expanded == pids:
            break
        pids = expanded
    found = []
    for row in rows:
        if row['pid'] not in pids or 'msedge.exe' not in (row['ExecutablePath'] or '').lower():
            continue
        identity = cim_record(row)
        if identity is not None:
            found.append(identity)
    return found


def owned_survivors(owned):
    survivors = []
    for prior in owned:
        try:
            query = identity_module().query_process(prior['pid'])
            comparison = identity_module().compare_identity(prior, query)
        except Exception as error:
            survivors.append({'pid': prior.get('pid'), 'status': 'UNRESOLVED',
                              'reason': repr(error)})
            continue
        if comparison.get('status') != identity_module().ABSENT:
            survivors.append({'pid': prior['pid'], **comparison})
    return survivors


def port_is_closed(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(1)
        return probe.connect_ex(('127.0.0.1', port)) != 0


def save(name, data):
    (output / name).write_text(json.dumps(data, indent=2), encoding='utf-8')


server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Handler, directory=str(root / 'war')))
thread = Thread(target=server.serve_forever, daemon=True)
thread.start()
base = 'http://127.0.0.1:' + str(server.server_port)
owned = []
result = {'outcome': 'NOT_RUN', 'cases': [], 'errors': []}
try:
    owner_row = next(row for row in processes() if row['pid'] == os.getpid())
    owner = cim_record(owner_row)
    if owner is None:
        raise RuntimeError('Menu process disappeared before identity capture')
    save('identity.json', {'process': owner, 'port': server.server_port,
                           'profile': str(output / 'profile')})
    with sync_playwright() as pw:
        context = pw.chromium.launch_persistent_context(
            str(output / 'profile'), channel='msedge', headless=True,
            viewport={'width': 1440, 'height': 1000},
            args=['--disable-background-timer-throttling', '--disable-renderer-backgrounding'])
        try:
            owned = owned_edges()
            save('owned-processes.json', owned)
            page = context.pages[0]
            page.on('pageerror', lambda error: result['errors'].append(str(error)))
            page.goto(base + '/circuitjs.html', wait_until='domcontentloaded')
            page.wait_for_function("() => window.tsjProduct && document.body.getAttribute('data-player-screen') === 'MENU'", timeout=30000)
            snapshot = lambda: page.evaluate('window.tsjProduct.snapshot(false)')
            families = snapshot()['families']
            catalog = [(row['id'], row['profile']) for row in families]
            assert catalog == list(EXPECTED_CATALOG), catalog
            assert all(row['procedural'] for row in families)

            def click(label):
                page.get_by_role('button', name=label, exact=True).click(timeout=15000)

            def observation_bound_seconds(family):
                return NORMAL_JOB_MILLIS / 1000 + OBSERVATION_MARGIN_SECONDS

            def prepare(family, row):
                start = time.monotonic()
                samples = []
                bound = observation_bound_seconds(family)
                row['observationBoundSeconds'] = bound
                row['selectedJobBudgetMillis'] = NORMAL_JOB_MILLIS
                while time.monotonic() - start < bound:
                    state = snapshot()
                    if state.get('progress'):
                        samples.append(state['progress'])
                    if state['screen'] in ('TICKET', 'ERROR'):
                        row.update({
                            'screen': state['screen'],
                            'replay': state.get('replay'),
                            'ready': state.get('ready', False),
                            'elapsedSeconds': time.monotonic() - start,
                            'observedCandidates': sorted({s['candidate'] for s in samples}),
                            'maxObservedUnits': max((s['units'] for s in samples), default=0),
                            'outcome': 'ERROR' if state['screen'] == 'ERROR' else 'TICKET',
                            'terminalMessage': state.get('message'),
                            'terminalNotice': state.get('notice'),
                            'replayRole': ('PREDECESSOR_REQUEST_ON_ERROR' if state['screen'] == 'ERROR' else 'ACCEPTED_REQUEST'),
                        })
                        return state, time.monotonic() - start, samples
                    page.wait_for_timeout(150)
                row.update({
                    'screen': state.get('screen'),
                    'elapsedSeconds': time.monotonic() - start,
                    'observedCandidates': sorted({s['candidate'] for s in samples}),
                    'maxObservedUnits': max((s['units'] for s in samples), default=0),
                    'outcome': 'TIMEOUT',
                })
                raise AssertionError('Board preparation exceeded external observation bound')

            def select(family):
                page.locator('label.tsj-product-field').filter(has_text='Difficulty').locator('select').select_option(family['profile'])
                page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option(family['id'])

            saved = {}
            for family in families:
                replays = set()
                for ordinal in range(3):
                    row = {'family': family['id'], 'profile': family['profile'],
                           'ordinal': ordinal, 'outcome': 'STARTED'}
                    result['cases'].append(row)
                    save('progress.json', result)
                    try:
                        select(family)
                        click('New board')
                        state, seconds, samples = prepare(family, row)
                        assert state['screen'] == 'TICKET', row
                        assert state['replay'].startswith('tsj-alpha/4/' + family['profile'] + '/' + family['id'] + '/'), row
                        assert state['replay'] not in replays, row
                        replays.add(state['replay'])
                        saved[family['id']] = state['replay']
                        click('Accept ticket and start')
                        page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'WORKBENCH'", timeout=30000)
                        page.wait_for_function('() => window.tsjProduct.snapshot(false).ready', timeout=30000)
                        row['ready'] = True
                        for private in ('data-tsj-verification', 'data-tsj-a08-report', 'data-tsj-quickplay-gate',
                                        'data-tsj-q30-coordinator-report', 'data-tsj-q30-coordinator-state',
                                        'data-tsj-q30-d01-report', 'data-tsj-q30-d01-state'):
                            assert page.locator('html').get_attribute(private) is None, private
                        row['privateMetadataAbsent'] = True
                        capture = ordinal == 0 and family['id'] in ('LED_INDICATOR', 'COMPOSED_CONTROLLED_INDICATOR')
                        if capture:
                            page.screenshot(path=str(output / (family['id'].lower() + '-top.png')))
                        click('Board view'); click('View bottom copper'); click('Board view')
                        if capture:
                            page.screenshot(path=str(output / (family['id'].lower() + '-bottom.png')))
                        click('Board view'); click('View top copper'); click('Board view')
                        click('Main menu')
                        page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'", timeout=30000)
                        row['outcome'] = 'PASS'
                        row['seconds'] = seconds
                        save('progress.json', result)
                        print('PASS', family['id'], family['profile'], ordinal, state['replay'], round(seconds, 2), flush=True)
                    except BaseException as failure:
                        if row.get('outcome') not in ('ERROR', 'TIMEOUT'):
                            row['outcome'] = 'FAIL'
                        row['failure'] = repr(failure)
                        row['failureVisibleText'] = page.locator('body').inner_text(timeout=3000)
                        save('progress.json', result)
                        raise

            assert len(result['cases']) == 33 and all(row['outcome'] == 'PASS' for row in result['cases'])
            result['replays'] = []
            family_by_id = {row['id']: row for row in families}
            for family_id in ('RELAY_OUTPUT', 'COMPOSED_CONTROLLED_INDICATOR'):
                replay = saved[family_id]
                family = family_by_id[family_id]
                row = {'family': family_id, 'profile': family['profile'],
                       'replay': replay, 'outcome': 'STARTED'}
                result['replays'].append(row)
                save('progress.json', result)
                try:
                    page.get_by_text('Open a saved replay code', exact=True).click()
                    page.get_by_label('Open current replay', exact=True).fill(replay)
                    click('Prepare replay')
                    state, seconds, samples = prepare(family, row)
                    assert state['screen'] == 'TICKET' and state['replay'] == replay, row
                    click('Accept ticket and start')
                    page.wait_for_function('() => window.tsjProduct.snapshot(false).ready', timeout=30000)
                    click('Board Power: ON')
                    page.wait_for_function('() => window.tsjProduct.snapshot(false).isolated', timeout=30000)
                    row.update({'seconds': seconds, 'isolated': True,
                                'observedCandidates': sorted({s['candidate'] for s in samples}),
                                'outcome': 'PASS'})
                    save('progress.json', result)
                    click('Main menu')
                    page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'", timeout=30000)
                except BaseException as failure:
                    if row.get('outcome') not in ('ERROR', 'TIMEOUT'):
                        row['outcome'] = 'FAIL'
                    row['failure'] = repr(failure)
                    save('progress.json', result)
                    raise
            assert len(result['replays']) == 2 and all(
                row['outcome'] == 'PASS' for row in result['replays'])
            # Stale v3 Q30 codes are rejected at parse time before session/board mutation.
            before_stale = snapshot()
            stale_replay = 'tsj-alpha/3/MEDIUM/RB30_CONTROL/13'
            page.get_by_text('Open a saved replay code', exact=True).click()
            page.get_by_label('Open current replay', exact=True).fill(stale_replay)
            click('Prepare replay')
            page.locator('[role="status"]').filter(
                has_text='Unsupported replay identity or epoch').first.wait_for(
                    state='visible', timeout=5000)
            stale = snapshot()
            assert stale['screen'] == 'MENU' and stale['hasBoard'] and stale['isolated'], stale
            for key in ('token', 'screen', 'hasBoard', 'replay', 'isolated', 'ready', 'completed'):
                assert stale.get(key) == before_stale.get(key), (key, before_stale, stale)
            assert stale['replay'] == before_stale['replay'] and 'progress' not in stale
            result['staleReplayEpoch'] = {
                'replay': stale_replay, 'outcome': 'EXPECTED_REJECTED',
                'message': 'Unsupported replay identity or epoch',
                'screen': stale['screen'], 'predecessorReplay': stale['replay'],
                'predecessorIsolated': stale['isolated'],
                'noGenerationStarted': True, 'prelaunchStateUnchanged': True,
            }

            # Replay a seed accepted through the ordinary Q30 menu launch.
            q30_family = family_by_id['RB30_CONTROL']
            q30_replay = saved['RB30_CONTROL']
            row = {'family': 'RB30_CONTROL', 'profile': q30_family['profile'],
                   'replay': q30_replay, 'outcome': 'STARTED'}
            result['normalQ30Replay'] = row
            save('progress.json', result)
            try:
                page.get_by_label('Open current replay', exact=True).fill(q30_replay)
                click('Prepare replay')
                state, seconds, samples = prepare(q30_family, row)
                assert state['screen'] == 'TICKET' and state['replay'] == q30_replay, row
                click('Accept ticket and start')
                page.wait_for_function(
                    "() => document.body.getAttribute('data-player-screen') === 'WORKBENCH'",
                    timeout=30000)
                page.wait_for_function('() => window.tsjProduct.snapshot(false).ready', timeout=30000)
                click('Board Power: ON')
                page.wait_for_function('() => window.tsjProduct.snapshot(false).isolated', timeout=30000)
                row.update({'seconds': seconds, 'isolated': True,
                            'observedCandidates': sorted({s['candidate'] for s in samples}),
                            'outcome': 'PASS'})
                save('progress.json', result)
                click('Main menu')
                page.wait_for_function(
                    "() => document.body.getAttribute('data-player-screen') === 'MENU'",
                    timeout=30000)
            except BaseException as failure:
                if row.get('outcome') not in ('ERROR', 'TIMEOUT'):
                    row['outcome'] = 'FAIL'
                row['failure'] = repr(failure)
                save('progress.json', result)
                raise
            assert result['normalQ30Replay']['outcome'] == 'PASS'
            assert not result['errors'], result['errors']
            result['outcome'] = 'PASS'
        finally:
            capture_error = None
            try:
                owned = list({(row['pid'], row['creationFileTimeTicks']): row
                              for row in owned + owned_edges()}.values())
                save('owned-processes.json', owned)
            except Exception as error:
                capture_error = error
                result['ownershipCaptureError'] = repr(error)
            finally:
                context.close()
            if capture_error:
                raise capture_error
except BaseException:
    result['outcome'] = 'FAIL'
    result['failure'] = traceback.format_exc()
    print(result['failure'], flush=True)
finally:
    cleanup_started = time.monotonic()
    server.shutdown(); server.server_close(); thread.join(timeout=5)
    deadline = cleanup_started + 5
    while True:
        survivors = owned_survivors(owned)
        if not survivors or time.monotonic() >= deadline:
            break
        time.sleep(.25)
    try:
        port_closed = port_is_closed(server.server_port)
    except OSError:
        port_closed = False
    result['cleanup'] = {'seconds': time.monotonic() - cleanup_started,
                         'serverStopped': not thread.is_alive(),
                         'portClosed': port_closed,
                         'ownedSurvivors': survivors}
    save('result.json', result)
    print('FINISHED', result['outcome'], 'cases', len(result['cases']), flush=True)
    sys.exit(0 if result['outcome'] == 'PASS' and not survivors and not thread.is_alive() else 1)
