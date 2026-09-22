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
            assert len(families) == 10 and all(row['procedural'] for row in families)

            def click(label):
                page.get_by_role('button', name=label, exact=True).click(timeout=15000)

            def prepare():
                start = time.monotonic()
                samples = []
                while time.monotonic() - start < 115:
                    state = snapshot()
                    if state.get('progress'):
                        samples.append(state['progress'])
                    if state['screen'] in ('TICKET', 'ERROR'):
                        return state, time.monotonic() - start, samples
                    page.wait_for_timeout(150)
                raise AssertionError('Board preparation exceeded external observation bound')

            def select(family):
                page.locator('label.tsj-product-field').filter(has_text='Difficulty').locator('select').select_option(family['profile'])
                page.locator('label.tsj-product-field').filter(has_text='Board family').locator('select').select_option(family['id'])

            saved = {}
            for family in families:
                replays = set()
                for ordinal in range(3):
                    select(family)
                    click('New board')
                    state, seconds, samples = prepare()
                    row = {'family': family['id'], 'profile': family['profile'],
                           'ordinal': ordinal, 'seconds': seconds,
                           'screen': state['screen'], 'replay': state.get('replay'),
                           'ready': state.get('ready', False),
                           'observedCandidates': sorted({s['candidate'] for s in samples}),
                           'maxObservedUnits': max((s['units'] for s in samples), default=0)}
                    result['cases'].append(row)
                    save('progress.json', result)
                    assert state['screen'] == 'TICKET', row
                    assert state['replay'].startswith('tsj-alpha/3/' + family['profile'] + '/' + family['id'] + '/'), row
                    assert state['replay'] not in replays, row
                    replays.add(state['replay'])
                    saved[family['id']] = state['replay']
                    click('Accept ticket and start')
                    page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'WORKBENCH'", timeout=30000)
                    page.wait_for_function('() => window.tsjProduct.snapshot(false).ready', timeout=30000)
                    row['ready'] = True
                    for private in ('data-tsj-verification', 'data-tsj-a08-report', 'data-tsj-quickplay-gate'):
                        assert page.locator('html').get_attribute(private) is None, private
                    row['privateMetadataAbsent'] = True
                    page.screenshot(path=str(output / (family['id'].lower() + '-' + str(ordinal) + '-top.png')))
                    click('Board view'); click('View bottom copper'); click('Board view')
                    page.screenshot(path=str(output / (family['id'].lower() + '-' + str(ordinal) + '-bottom.png')))
                    click('Board view'); click('View top copper'); click('Board view')
                    click('Main menu')
                    page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'", timeout=30000)
                    print('PASS', family['id'], family['profile'], ordinal, state['replay'], round(seconds, 2), flush=True)

            assert len(result['cases']) == 30
            result['replays'] = []
            for family_id in ('RELAY_OUTPUT', 'COMPOSED_CONTROLLED_INDICATOR'):
                replay = saved[family_id]
                page.get_by_text('Open a saved replay code', exact=True).click()
                page.get_by_label('Open current replay', exact=True).fill(replay)
                click('Prepare replay')
                state, seconds, samples = prepare()
                assert state['screen'] == 'TICKET' and state['replay'] == replay
                click('Accept ticket and start')
                page.wait_for_function('() => window.tsjProduct.snapshot(false).ready', timeout=30000)
                click('Board Power: ON')
                page.wait_for_function('() => window.tsjProduct.snapshot(false).isolated', timeout=30000)
                result['replays'].append({'family': family_id, 'replay': replay,
                                          'seconds': seconds, 'isolated': True,
                                          'observedCandidates': sorted({s['candidate'] for s in samples})})
                click('Main menu')
                page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'", timeout=30000)
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
