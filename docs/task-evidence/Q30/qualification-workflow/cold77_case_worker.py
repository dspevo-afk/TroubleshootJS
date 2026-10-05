"""One cold root using the unchanged prepared host and maintained strict reader.

Internal worker of cold77_batch; the maintained Windows serial runner owns its
process tree. No metrics instrumentation, retries, changed budgets or app copies.
"""
import argparse
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import time

from receipts import (
    LIMITS, WORKFLOW_MANIFEST_FILENAME, load_workflow_manifest, validate_case,
    write_exclusive,
)

HOST_SECONDS = 150
HERE = Path(__file__).resolve().parent

def need(ok, message):
    if not ok:
        raise ValueError(message)

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def read(path):
    raw = Path(path).read_bytes()
    need(0 < len(raw) <= 4 * 1024 * 1024, 'JSON byte bound')
    def pairs(rows):
        out = {}
        for key,value in rows:
            need(key not in out, 'duplicate JSON key')
            out[key] = value
        return out
    def constant(value):
        raise ValueError('nonfinite JSON: ' + value)
    return json.loads(raw.decode('utf-8-sig'), object_pairs_hook=pairs, parse_constant=constant), raw

def imported(path, name, expected):
    need(sha(path) == expected, 'source pin changed: ' + Path(path).name)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def audit_inputs(args, workflow_manifest=None):
    workflow = workflow_manifest or load_workflow_manifest(
        HERE, HERE / WORKFLOW_MANIFEST_FILENAME)
    matched_sha = workflow['externalMatchedHelperSha256']
    matched = imported(args.matched_helper, 'q30_cold_matched_owner', matched_sha)
    need(args.repo.resolve(strict=True) == matched.REPO.resolve(strict=True), 'repo role differs')
    variant = matched.load_variant('current', args.pointer)
    inputs = matched.inventory(args.repo, variant['snapshot']['files'], True)
    app = matched.app_state(variant)
    reader_path = args.repo / 'tests/qualification/q30-scale-acceptance/q30_scale_reader.py'
    pin = next(row['sha256'] for row in variant['snapshot']['files']
               if row['path'] == 'tests/qualification/q30-scale-acceptance/q30_scale_reader.py')
    reader = imported(reader_path, 'q30_cold_strict_reader', pin)
    reader.validate_plan(variant['plan'], variant['planRaw'], variant['paths']['acceptance'])
    need(sum(len(row['candidates']) for row in variant['plan']['cases']) == 308, 'candidate count changed')
    host_path = variant['task'] / 'isolated/host/normal_screen_host.py'
    owner = imported(host_path, 'q30_cold_original_host', variant['plan']['hostRunnerSha256'])
    complete_app = owner.capture_input_manifest(variant['app'])
    expected_app = variant['plan']['preparedInputIdentity']
    for actual_key, expected_key in (('sha256', 'combinedSha256'), ('sourceSha256', 'sourceSha256'),
            ('webSha256', 'webSha256'), ('fileCount', 'fileCount'),
            ('sourceFileCount', 'sourceFileCount'), ('webFileCount', 'webFileCount')):
        need(complete_app[actual_key] == expected_app[expected_key], 'prepared app identity changed: ' + actual_key)
    need(sys.version_info[:3] == (3, 13, 14), 'selected Python changed')
    # Windows Store Python reports its app-execution alias in sys.executable;
    # that alias cannot be resolved on this host. The maintained native runner
    # independently proves the image/birth of the selected physical executable.
    need(args.host_python.resolve(strict=True) == matched.HOST_PY.resolve(strict=True), 'physical Python role differs')
    binding = {'sourceIdentity': variant['snapshot']['sourceIdentity'], 'baseHead': variant['snapshot']['baseHead'],
        'planSha256': matched.digest(variant['planRaw']), 'acceptancePlanSha256': matched.digest(variant['acceptanceRaw']),
        'repositoryInputs': len(inputs), 'repositoryMapSha256': matched.digest(json.dumps(inputs, sort_keys=True, separators=(',', ':')).encode()),
        'preparedAppStateSha256': matched.digest(json.dumps(app, sort_keys=True, separators=(',', ':')).encode()),
        'preparedInputIdentity': expected_app,
        'readerSha256': pin, 'hostRunnerSha256': variant['plan']['hostRunnerSha256'], 'pointerSha256': sha(args.pointer),
        'matchedHelperSha256': matched_sha}
    return binding, variant, reader, matched

def case_spec(seed):
    return {'cases': [{'name': 'cold-' + seed,
        'path': 'circuitjs.html?lang=en&tsjNormalMode=cold&tsjNormalSeed=' + seed,
        'stateAttribute': 'data-tsj-q30-normal-state', 'reportAttribute': 'data-tsj-q30-normal-report',
        'expectedPrefix': 'SCREEN_DONE', 'terminalPrefixes': ['SCREEN_DONE'], 'timeoutSeconds': HOST_SECONDS}]}

def receipt_from_records(seed, binding, report, host, checked, host_exit, elapsed_ms, inputs_unchanged, errors):
    app = checked.get('app') or {}
    host_checked = checked.get('host') or {}
    measured = report if isinstance(report, dict) else {}
    app_status = ('PASS' if app.get('status') == 'APP_PASS' and not app.get('errors') else
        'TIMEOUT' if app.get('status') == 'COMPLETED_NONPASS' and not app.get('errors') and
        measured.get('applicationOutcome') == 'TIMEOUT' else 'FAIL')
    observed = (host.get('cases') or [{}])[0] if isinstance(host, dict) else {}
    cleanup = host.get('cleanup') or {} if isinstance(host, dict) else {}
    clean = cleanup.get('status') == 'PASS' and cleanup.get('errors') == []
    result = {'schema': 1, 'rootSeed': seed, 'sourceIdentity': binding['sourceIdentity'], 'baseHead': binding['baseHead'],
        'planSha256': binding['planSha256'], 'limits': dict(LIMITS),
        'application': {'status': app_status, 'elapsedMs': measured.get('generationElapsedMs'),
            'workUnits': measured.get('workUnits'), 'maxActiveOperationMs': measured.get('maxActiveOperationMs'),
            'maxCoordinatorAdvanceMs': measured.get('maxCoordinatorAdvanceMs')},
        'host': {'status': 'PASS' if host_checked.get('status') == 'HOST_PASS' and not host_checked.get('errors') and not errors else 'FAIL',
            'observationElapsedMs': math.ceil(observed['operationSeconds'] * 1000) if observed.get('operationSeconds') is not None else None,
            'deadlineMs': HOST_SECONDS * 1000, 'exitCode': host_exit, 'terminal': observed.get('terminalReached') is True},
        'cleanup': {'status': 'PASS' if clean else 'FAIL',
            'elapsedMs': math.ceil(cleanup['seconds'] * 1000) if cleanup.get('seconds') is not None else None,
            'elapsedLimitations': None if cleanup.get('seconds') is not None else 'Host cleanup receipt unavailable',
            'serverStopped': cleanup.get('serverStopped') is True, 'ownedSurvivors': cleanup.get('ownedSurvivors', [])},
        'operation': {'elapsedMs': elapsed_ms, 'elapsedLimitations': None if elapsed_ms is not None else
            'Outer interruption; worker operation clock unavailable'}, 'sourceInputsUnchanged': inputs_unchanged,
        'provenance': {'applicationOutcome': measured.get('applicationOutcome'), 'readerStatus': checked.get('status'),
            'applicationErrors': app.get('errors', []), 'hostErrors': host_checked.get('errors', []),
            'workerErrors': errors, 'binding': binding, 'instrumentation': 'NONE_ORIGINAL_QUALIFICATION_HOST',
            'clockPolicy': 'Application metrics from unchanged report; independent host/cleanup/worker wall clocks rounded upward.'}}
    validate_case(result)
    return result

def execute(args):
    started = time.monotonic_ns()
    destination = args.receipt
    need(destination.is_absolute() and destination.parent.is_dir() and not destination.exists(), 'receipt collision/path')
    root = destination.parent
    manifest_path = HERE / WORKFLOW_MANIFEST_FILENAME
    workflow_before = sha(manifest_path)
    workflow = load_workflow_manifest(HERE, manifest_path)
    before, variant, reader, matched = audit_inputs(args, workflow)
    seed = args.root_seed
    need(seed in variant['plan']['coldOrder'], 'root not in frozen cohort')
    write_exclusive(root / 'input-audit-before.json', before)
    spec = root / 'cold-spec.json'
    write_exclusive(spec, case_spec(seed))
    host_path = variant['task'] / 'isolated/host/normal_screen_host.py'
    host_output = root / 'host-output'
    env = dict(os.environ, PYTHONPATH=str(args.deps.resolve(strict=True)), PYTHONDONTWRITEBYTECODE='1', PYTHONIOENCODING='utf-8')
    matched.host_runtime(args.host_python, args.deps, root, env)
    errors, report, host, checked, code = [], None, {}, {}, None
    try:
        # Inherits the already-assigned serial-runner job. Its unchanged 180s
        # deadline covers this call, all descendants and receipt finalization.
        code = subprocess.run([str(args.host_python), '-B', str(host_path), str(variant['app']),
            str(host_output), str(spec)], env=env, shell=False, check=False).returncode
    except Exception as error:
        errors.append('host process: ' + type(error).__name__ + ': ' + str(error))
    try:
        host = read(host_output / 'result.json')[0]
    except Exception as error:
        errors.append('host receipt: ' + type(error).__name__ + ': ' + str(error))
    try:
        report, raw = read(host_output / ('cases/001-cold-' + seed + '.report.json'))
        checked = reader.evaluate(report, raw, variant['plan'], variant['planRaw'], host, variant['paths']['acceptance'])
        checked.update(reader.qualification_code_digests(), scalePlanSha256=before['planSha256'])
        write_exclusive(root / 'strict-reader-result.json', checked)
    except Exception as error:
        errors.append(type(error).__name__ + ': ' + str(error))
    unchanged = False
    try:
        after_workflow = load_workflow_manifest(HERE, manifest_path)
        need(sha(manifest_path) == workflow_before and after_workflow == workflow,
             'workflow binding changed')
        after, _, _, _ = audit_inputs(args, after_workflow)
        write_exclusive(root / 'input-audit-after.json', after)
        unchanged = before == after
    except Exception as error:
        errors.append('final input audit: ' + type(error).__name__ + ': ' + str(error))
    receipt = receipt_from_records(seed, before, report, host, checked, code,
        (time.monotonic_ns() - started + 999999) // 1000000, unchanged, errors)
    if report is not None:
        row = next(row for row in variant['plan']['cases'] if row['seed'] == seed)
        published = [item.get('ordinal') for item in report.get('candidates', []) if item.get('published') is True]
        ordinal = published[0] if len(published) == 1 and type(published[0]) is int and 0 <= published[0] < 4 else None
        receipt['provenance'].update(reportSha256=hashlib.sha256(raw).hexdigest(),
            publishedOrdinal=ordinal,
            publishedPackages=row['candidates'][ordinal]['packages'] if ordinal is not None else None)
    write_exclusive(destination, receipt)
    status = validate_case(receipt)
    print(json.dumps({'rootSeed': seed, 'receiptValidation': status, 'application': receipt['application'], 'workerErrors': errors}))
    return 0 if status == 'PASS' and not errors and code == 0 else 1

def parser():
    result = argparse.ArgumentParser(description=__doc__)
    for name in ('repo', 'pointer', 'matched-helper', 'host-python', 'deps', 'receipt'):
        result.add_argument('--' + name, type=Path, required=True)
    result.add_argument('--root-seed', required=True)
    return result

if __name__ == '__main__':
    raise SystemExit(execute(parser().parse_args()))
