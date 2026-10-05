"""Compiled menu/replay and privacy checks; headless snapshots do not prove visible repair."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from datetime import datetime, timezone
import importlib.util
import argparse
import re

import audit_release
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

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('app', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--diagnose-q30', action='store_true',
                    help='Run only bounded Q30 launch observations; never a full-menu PASS')
parser.add_argument('--diagnose-launches', type=int, choices=range(1, 5), default=2)
parser.add_argument('--diagnose-exact-seed', action='append', default=[],
                    help='Use the ordinary exact-seed UI for these diagnosis-only inputs')
parser.add_argument('--headed', action='store_true')
parser.add_argument('--warm-plan', type=Path,
                    help='Frozen Q30 normal exact/replay repeat plan; separate from menu qualification')
parser.add_argument('--warm-start', type=int, default=0)
parser.add_argument('--warm-count', type=int, choices=range(1, 4), default=3)
args = parser.parse_args()
if args.diagnose_exact_seed and not args.diagnose_q30:
    parser.error('Exact-seed diagnosis requires --diagnose-q30')
if len(args.diagnose_exact_seed) > 4:
    parser.error('At most four diagnosis seeds may be supplied')
if args.warm_plan and (args.diagnose_q30 or args.diagnose_exact_seed):
    parser.error('Warm measurement and bounded diagnosis are separate scopes')
root, output = args.app.resolve(), args.output.resolve()
output.mkdir(parents=True, exist_ok=False)


class DiagnosticComplete(Exception):
    """End a bounded diagnostic subset without claiming full qualification."""


def failed_launch_seed(message, family, profile):
    prefix = 'Family: ' + family + '; difficulty: ' + profile + '; launch seed: '
    if not isinstance(message, str) or prefix not in message:
        return None
    value = message.split(prefix, 1)[1]
    if not re.fullmatch(r'-?(0|[1-9][0-9]*)', value) or len(value) > 20:
        return None
    parsed = int(value)
    return value if -(1 << 63) <= parsed < (1 << 63) and str(parsed) == value else None


def load_warm_rows(path, app, start, count):
    plan = json.loads(Path(path).read_bytes())
    frozen = json.loads((Path(app) / 'tests/qualification/q30-scale-acceptance/acceptance-plan.json').read_bytes())
    rows = plan.get('cases')
    if (plan.get('schema') != 1 or plan.get('status') != 'FROZEN_NORMAL_WARM_PLAN'
            or not isinstance(rows, list) or len(rows) != 77
            or [row.get('rootSeed') for row in rows] != frozen['coldOrder']
            or type(start) is not int or type(count) is not int
            or not 0 <= start < 77 or not 1 <= count <= 3 or start + count > 77):
        raise ValueError('Warm plan must retain the frozen 77-root order and a bounded slice')
    for index, row in enumerate(rows):
        seed = row.get('acceptedSeed'); root_seed = row.get('rootSeed')
        ordinal = row.get('publishedOrdinal')
        for value in (seed, root_seed):
            if (not isinstance(value, str) or not re.fullmatch(r'-?(0|[1-9][0-9]*)', value)
                    or len(value) > 20 or not -(1 << 63) <= int(value) < (1 << 63)
                    or str(int(value)) != value):
                raise ValueError('Warm plan seed must be exact canonical signed-long text')
        if type(ordinal) is not int or not 0 <= ordinal < 4:
            raise ValueError('Warm plan accepted ordinal is outside the frozen four candidates')
        expected = ((int(root_seed) + ordinal * 0x9e3779b97f4a7c15 + (1 << 63)) % (1 << 64)) - (1 << 63)
        if (row.get('order') != index or seed != str(expected)
                or row.get('expectedReplay') != 'tsj-alpha/4/MEDIUM/RB30_CONTROL/' + seed
                or type(row.get('packages')) is not int or not 20 <= row['packages'] <= 40):
            raise ValueError('Warm plan accepted identity is not the frozen canonical candidate')
    return rows[start:start + count]


WARM_OBSERVER_JS = r"""() => {
    const originalAction = window.tsjProduct.action;
    const history = {timeOrigin: performance.timeOrigin, rows: [], hiddenEvents: []};
    window.__q30WarmObservation = history;
    let active = null;
    function observe() {
        if (!active || active.terminalObserved || active.generationToken === undefined) return;
        const state = window.tsjProduct.snapshot(false);
        if (state.token !== active.generationToken) return;
        if ((state.screen === 'TICKET' || state.screen === 'ERROR') &&
                document.body.getAttribute('data-player-screen') === state.screen) {
            active.terminalObserved = true;
            active.finishedMs = performance.now();
            active.elapsedMs = active.finishedMs - active.startedMs;
            active.terminalScreen = state.screen;
            active.terminalToken = state.token;
            active.replay = state.replay || null;
            active.ready = state.ready === true;
        }
    }
    window.tsjProduct.action = function(token, view, name, first, second, third) {
        if (name === 'launch' || name === 'replay') {
            if ((active && !active.terminalObserved) || history.rows.length >= 6)
                throw new Error('Warm observer overlapping or excessive launch');
            active = {name, first, second, third, beforeToken: token,
                startedMs: performance.now(), startedUtcMillis: Date.now(),
                startedHidden: document.hidden, terminalObserved: false};
            history.rows.push(active);
            const value = originalAction.apply(this, arguments);
            active.generationToken = window.tsjProduct.snapshot(false).token;
            observe();
            return value;
        }
        return originalAction.apply(this, arguments);
    };
    new MutationObserver(observe).observe(document.body,
        {attributes: true, attributeFilter: ['data-player-screen']});
    document.addEventListener('visibilitychange', () => {
        if (history.hiddenEvents.length >= 32) throw new Error('Warm visibility bound exceeded');
        history.hiddenEvents.push({atMs: performance.now(), hidden: document.hidden});
    });
}"""


def validate_warm_observation(observation, expected_seed):
    if (not isinstance(observation, dict) or observation.get('name') != 'launch'
            or observation.get('first') != 'RB30_CONTROL'
            or observation.get('second') != expected_seed or observation.get('third') != 'MEDIUM'
            or observation.get('terminalObserved') is not True
            or observation.get('startedHidden') is not False
            or type(observation.get('generationToken')) is not int
            or observation['generationToken'] <= observation.get('beforeToken', observation['generationToken'])
            or observation.get('terminalToken') != observation['generationToken']
            or observation.get('terminalScreen') not in ('TICKET', 'ERROR')):
        raise ValueError('Warm terminal observation is not bound to this normal exact launch')
    import math
    elapsed = observation.get('elapsedMs')
    start, finish = observation.get('startedMs'), observation.get('finishedMs')
    if (any(type(value) not in (int, float) or not math.isfinite(value) for value in (elapsed, start, finish))
            or elapsed <= 0 or finish <= start or abs(elapsed - (finish - start)) > 0.001):
        raise ValueError('Warm browser monotonic interval is missing or inconsistent')
    return elapsed


def warm_identity_matches(first, repeated, expected_replay):
    # On ERROR the public replay belongs to the predecessor, not a new board.
    return (first.get('outcome') == 'PASS' and repeated.get('outcome') == 'PASS'
            and first.get('replay') == repeated.get('replay') == expected_replay)


warm_rows = load_warm_rows(args.warm_plan, root, args.warm_start, args.warm_count) if args.warm_plan else None


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


def port_is_closed(port, powershell, evidence_root):
    # A socket timeout (including Windows10035) does not prove absence.
    # Reuse the bounded owned Windows listener query and retain its receipt.
    data, host = audit_release._run_cim_and_ports(
        {'tools': {'powershell': powershell}, 'tempRoot': str(evidence_root)}, [])
    listeners = data.get('listeners')
    if not isinstance(listeners, list) or any(
            not isinstance(row, dict) or type(row.get('localPort')) is not int
            or type(row.get('ownerPid')) is not int for row in listeners):
        raise RuntimeError('Native TCP listener query returned an invalid table')
    closed = not any(row['localPort'] == port for row in listeners)
    with (Path(evidence_root) / 'listener-query.json').open('x', encoding='utf-8') as stream:
        json.dump({'port': port, 'closed': closed, 'query': host}, stream, indent=2)
    return closed


def save(name, data):
    (output / name).write_text(json.dumps(data, indent=2), encoding='utf-8')


server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Handler, directory=str(root / 'war')))
thread = Thread(target=server.serve_forever, daemon=True)
thread.start()
base = 'http://127.0.0.1:' + str(server.server_port)
owned = []
result = {'outcome': 'NOT_RUN', 'cases': [], 'errors': [],
          'scope': 'NORMAL_WARM_MEASUREMENT' if warm_rows else 'Q30_DIAGNOSIS_ONLY' if args.diagnose_q30 else 'FULL_MENU_QUALIFICATION',
          'headed': args.headed, 'launchObserver': 'forwarding-only public action arguments; no seed mutation'}
try:
    owner_row = next(row for row in processes() if row['pid'] == os.getpid())
    owner = cim_record(owner_row)
    if owner is None:
        raise RuntimeError('Menu process disappeared before identity capture')
    save('identity.json', {'process': owner, 'port': server.server_port,
                           'profile': str(output / 'profile')})
    with sync_playwright() as pw:
        context = pw.chromium.launch_persistent_context(
            str(output / 'profile'), channel='msedge', headless=not args.headed,
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
            # Observe the ordinary UI's exact string arguments before forwarding
            # them unchanged. No controller action is initiated by this observer.
            page.evaluate("""() => {
                const action = window.tsjProduct.action;
                window.__q30MenuLaunchTrace = [];
                window.tsjProduct.action = function(token, view, name, first, second, third) {
                    if (name === 'random' || name === 'launch' || name === 'replay') {
                        if (window.__q30MenuLaunchTrace.length >= 64)
                            throw new Error('Q30 bounded launch trace exhausted');
                        window.__q30MenuLaunchTrace.push({token, view, name, first, second, third,
                            observedAtMillis: Date.now()});
                    }
                    return action.apply(this, arguments);
                };
            }""")
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
                        sample = {'hostElapsedSeconds': time.monotonic() - start, **state['progress']}
                        samples.append(sample)
                        row['lastProgress'] = sample
                        with (output / 'progress-samples.jsonl').open('a', encoding='utf-8') as stream:
                            stream.write(json.dumps({'family': row['family'],
                                'ordinal': row.get('ordinal'), 'sample': sample}) + '\n')
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
                            'failedLaunchSeed': failed_launch_seed(
                                state.get('message'), row['family'], row['profile']),
                            'progressSamples': samples,
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


            if warm_rows:
                page.evaluate(WARM_OBSERVER_JS)
                result['warmPlan'] = {'path': str(args.warm_plan),
                    'start': args.warm_start, 'count': args.warm_count}
                result['browserEnvironment'] = page.evaluate("""() => ({
                    userAgent: navigator.userAgent, hardwareConcurrency: navigator.hardwareConcurrency,
                    deviceMemoryGiB: navigator.deviceMemory || null,
                    width: window.innerWidth, height: window.innerHeight,
                    devicePixelRatio: window.devicePixelRatio, hidden: document.hidden,
                    timeOrigin: performance.timeOrigin})""")
                result['browserVersion'] = context.browser.version if context.browser else None
                result['metric'] = 'performance.now: ordinary exact-seed public action to terminal player view'
                result['internalMetricLimit'] = 'Public interface supplies sampled progress only, not exact terminal reason, final work count or cache-hit count.'
                result['warmPairs'] = []
                family = next(f for f in families if f['id'] == 'RB30_CONTROL')
                for case in warm_rows:
                    pair = {'rootSeed': case['rootSeed'], 'acceptedSeed': case['acceptedSeed'],
                        'order': case['order'], 'expectedReplay': case['expectedReplay'], 'attempts': []}
                    result['warmPairs'].append(pair)
                    for temperature in ('PRIMING', 'WARM'):
                        row = {'family': family['id'], 'profile': family['profile'],
                            'ordinal': case['order'], 'temperature': temperature,
                            'rootSeed': case['rootSeed'], 'acceptedSeed': case['acceptedSeed'],
                            'outcome': 'STARTED'}
                        pair['attempts'].append(row); result['cases'].append(row)
                        save('progress.json', result)
                        select(family)
                        row['predecessorReplay'] = snapshot().get('replay')
                        page.get_by_text('Enter an exact seed', exact=True).click()
                        page.get_by_label('Exact seed (signed decimal integer)', exact=True).fill(case['acceptedSeed'])
                        click('Prepare exact seed')
                        row['launchAction'] = page.evaluate('window.__q30MenuLaunchTrace.slice(-1)[0]')
                        save('launch-actions.json', page.evaluate('window.__q30MenuLaunchTrace'))
                        state, seconds, samples = prepare(family, row)
                        page.wait_for_function('() => window.__q30WarmObservation.rows.slice(-1)[0].terminalObserved', timeout=5000)
                        observation = page.evaluate('window.__q30WarmObservation.rows.slice(-1)[0]')
                        row['publicAdmissionMs'] = validate_warm_observation(observation, case['acceptedSeed'])
                        row['browserObservation'] = observation
                        row['publicAdmissionWithin90000Ms'] = row['publicAdmissionMs'] <= NORMAL_JOB_MILLIS
                        row['lastObservedStage'] = samples[-1]['label'] if samples else None
                        row['internalTerminalReason'] = None
                        row['exactFinalWorkUnits'] = None
                        row['seconds'] = seconds
                        if state['screen'] == 'TICKET':
                            assert state['replay'] == case['expectedReplay'], 'Warm repeated board identity changed'
                            assert observation['replay'] == state['replay'] and observation['ready']
                            click('Accept ticket and start')
                            page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'WORKBENCH' && window.tsjProduct.snapshot(false).ready", timeout=30000)
                            row['outcome'] = 'PASS'
                            click('Main menu')
                        else:
                            row['outcome'] = 'ERROR'
                            row['failureVisibleText'] = page.locator('body').inner_text(timeout=3000)
                            page.screenshot(path=str(output / ('failure-' + str(case['order']) + '-' + temperature + '.png')))
                            page.get_by_role('button', name='Main menu', exact=True).last().click(timeout=15000)
                        page.wait_for_function("() => document.body.getAttribute('data-player-screen') === 'MENU'", timeout=30000)
                        save('warm-observations.json', page.evaluate('window.__q30WarmObservation'))
                        save('progress.json', result)
                        print('WARM_ATTEMPT', case['rootSeed'], temperature, row['outcome'], round(row['publicAdmissionMs'], 3), flush=True)
                    first, repeated = pair['attempts']
                    pair['warmEstablished'] = first['outcome'] == 'PASS'
                    pair['sameIdentity'] = warm_identity_matches(first, repeated, case['expectedReplay'])
                    pair['interAttemptGapMs'] = repeated['browserObservation']['startedMs'] - first['browserObservation']['finishedMs']
                    pair['sameDocumentTimeOrigin'] = result['browserEnvironment']['timeOrigin'] == page.evaluate('performance.timeOrigin')
                    assert pair['sameDocumentTimeOrigin'] and pair['interAttemptGapMs'] >= 0
                    save('progress.json', result)
                result['warmObservation'] = page.evaluate('window.__q30WarmObservation')
                assert result['warmObservation']['hiddenEvents'] == [] and not result['errors']
                assert len(result['cases']) == 2 * len(warm_rows)
                result['outcome'] = 'WARM_MEASUREMENT_COMPLETE'
                raise DiagnosticComplete()

            saved = {}
            selected_families = [f for f in families if f['id'] == 'RB30_CONTROL'] if args.diagnose_q30 else families
            for family in selected_families:
                replays = set()
                launch_count = (len(args.diagnose_exact_seed) or args.diagnose_launches) if args.diagnose_q30 else 3
                for ordinal in range(launch_count):
                    row = {'family': family['id'], 'profile': family['profile'],
                           'ordinal': ordinal, 'outcome': 'STARTED'}
                    result['cases'].append(row)
                    save('progress.json', result)
                    try:
                        select(family)
                        row['predecessorReplay'] = snapshot().get('replay')
                        if args.diagnose_exact_seed:
                            page.get_by_text('Enter an exact seed', exact=True).click()
                            page.get_by_label('Exact seed (signed decimal integer)', exact=True).fill(
                                args.diagnose_exact_seed[ordinal])
                            click('Prepare exact seed')
                        else:
                            click('New board')
                        row['launchAction'] = page.evaluate('window.__q30MenuLaunchTrace.slice(-1)[0]')
                        save('launch-actions.json', page.evaluate('window.__q30MenuLaunchTrace'))
                        save('progress.json', result)
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
                        save('failure-snapshot.json', snapshot())
                        page.screenshot(path=str(output / 'failed-launch.png'))
                        save('progress.json', result)
                        raise

            if args.diagnose_q30:
                assert result['cases'] and all(row['outcome'] == 'PASS' for row in result['cases'])
                assert not result['errors'], result['errors']
                result['outcome'] = 'DIAGNOSTIC_PASS'
                raise DiagnosticComplete()
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
except DiagnosticComplete:
    pass
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
        port_closed = port_is_closed(server.server_port, os.environ.get('TSJ_POWERSHELL'), output)
    except Exception as error:
        port_closed = False
        result['listenerQueryError'] = repr(error)
    result['cleanup'] = {'seconds': time.monotonic() - cleanup_started,
                         'serverStopped': not thread.is_alive(),
                         'portClosed': port_closed,
                         'ownedSurvivors': survivors}
    save('result.json', result)
    print('FINISHED', result['outcome'], 'cases', len(result['cases']), flush=True)
    sys.exit(0 if result['outcome'] in ('PASS', 'DIAGNOSTIC_PASS', 'WARM_MEASUREMENT_COMPLETE') and port_closed and not survivors and not thread.is_alive() else 1)
