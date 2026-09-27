"""Actual visible New Board sequence in one Edge page; output lives outside the repo."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
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
SMALL_BOARD_OBSERVATION_SECONDS = 115  # Existing 90-second cap plus 25 seconds to observe.
NORMAL_MEDIUM_JOB_MILLIS = 300000       # Qualified immutable MEDIUM_BOARD_NORMAL@1 request.
NORMAL_MEDIUM_MARGIN_SECONDS = 25

root, output = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
output.mkdir(parents=True, exist_ok=False)


class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def processes():
    query = ("Get-CimInstance Win32_Process | Select-Object "
             "@{n='pid';e={$_.ProcessId}},@{n='parent';e={$_.ParentProcessId}},"
             "@{n='created';e={$_.CreationDate.ToUniversalTime().ToString('o')}},"
             "ExecutablePath | ConvertTo-Json -Compress")
    result = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query],
                            capture_output=True, text=True, timeout=20)
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
    return [row for row in rows if row['pid'] in pids and
            'msedge.exe' in (row['ExecutablePath'] or '').lower()]


def save(name, data):
    (output / name).write_text(json.dumps(data, indent=2), encoding='utf-8')


server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Handler, directory=str(root / 'war')))
thread = Thread(target=server.serve_forever, daemon=True)
thread.start()
base = 'http://127.0.0.1:' + str(server.server_port)
owned = []
result = {'outcome': 'NOT_RUN', 'cases': [], 'errors': []}
owner = next(row for row in processes() if row['pid'] == os.getpid())
save('identity.json', {'pid': owner['pid'], 'created': owner['created'],
                       'executable': owner['ExecutablePath'],
                       'port': server.server_port, 'profile': str(output / 'profile')})
try:
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
                if family['id'] == 'RB30_CONTROL':
                    return NORMAL_MEDIUM_JOB_MILLIS / 1000 + NORMAL_MEDIUM_MARGIN_SECONDS
                return SMALL_BOARD_OBSERVATION_SECONDS

            def prepare(family, row):
                start = time.monotonic()
                samples = []
                bound = observation_bound_seconds(family)
                row['observationBoundSeconds'] = bound
                row['selectedJobBudgetMillis'] = (
                    NORMAL_MEDIUM_JOB_MILLIS if family['id'] == 'RB30_CONTROL' else 90000)
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
                        assert state['replay'].startswith('tsj-alpha/3/' + family['profile'] + '/' + family['id'] + '/'), row
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
                        page.screenshot(path=str(output / (family['id'].lower() + '-' + str(ordinal) + '-top.png')))
                        click('Board view'); click('View bottom copper'); click('Board view')
                        page.screenshot(path=str(output / (family['id'].lower() + '-' + str(ordinal) + '-bottom.png')))
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
                        save('progress.json', result)
                        raise

            assert len(result['cases']) == 33 and all(row['outcome'] == 'PASS' for row in result['cases'])
            result['replays'] = []
            family_by_id = {row['id']: row for row in families}
            for family_id in ('RELAY_OUTPUT', 'COMPOSED_CONTROLLED_INDICATOR', 'RB30_CONTROL'):
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
            assert len(result['replays']) == 3 and all(
                row['outcome'] == 'PASS' for row in result['replays'])
            assert not result['errors'], result['errors']
            result['outcome'] = 'PASS'
        finally:
            owned = list({(row['pid'], row['created']): row for row in owned + owned_edges()}.values())
            context.close()
except BaseException:
    result['outcome'] = 'FAIL'
    result['failure'] = traceback.format_exc()
    print(result['failure'], flush=True)
finally:
    cleanup_started = time.monotonic()
    server.shutdown(); server.server_close(); thread.join(timeout=5)
    survivors = []
    for _ in range(20):
        survivors = [row for row in processes() if any(row['pid'] == prior['pid'] and
                     row['created'] == prior['created'] for prior in owned)]
        if not survivors:
            break
        time.sleep(.25)
    result['cleanup'] = {'seconds': time.monotonic() - cleanup_started,
                         'serverStopped': not thread.is_alive(),
                         'ownedSurvivors': survivors}
    save('result.json', result)
    print('FINISHED', result['outcome'], 'cases', len(result['cases']), flush=True)
    sys.exit(0 if result['outcome'] == 'PASS' and not survivors and not thread.is_alive() else 1)
