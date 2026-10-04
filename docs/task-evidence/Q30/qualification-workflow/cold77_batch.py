"""Audit-only by default; grant-bound serial cold77 with the original host/reader.

Additive orchestration outside the frozen application inventory. No build, metrics
instrumentation, retry, publication or normal-play enablement. A launch requires a
new parent-coordinated 4h12m quiet window after the offline audit.
"""
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time

from cold77_case_worker import audit_inputs, imported, need, read, receipt_from_records, sha
from receipts import LIMITS, summarize_cases, write_exclusive

HERE = Path(__file__).resolve().parent
RUNNER_SHA = '19931855b98aeb358f16dcbd94b6c69d81a221191bed2f07697a3ccdcffff5e6'
RECEIPTS_SHA = '5d0865564e39a33a679a7fe5adda0e3ba2c539ce5740034e0a495135619ab0a8'
STEP_MS, CLEANUP_MS, FINAL_MS, RELEASE_MS = 180000, 15000, 60000, 30000
CONTROLLER_RECORD_MS = 5000
OPERATION_MS = 77 * (STEP_MS + CLEANUP_MS) + FINAL_MS
WINDOW_MS = OPERATION_MS + CLEANUP_MS + RELEASE_MS


def utc(value):
    need(isinstance(value, str) and value.endswith('Z'), 'grant time requires UTC Z')
    return datetime.fromisoformat(value[:-1] + '+00:00')


def validate_grant(grant, manifest_sha, binding, now):
    need(type(grant) is dict and grant.get('schema') == 1 and type(grant.get('schema')) is int,
         'grant schema')
    need(grant.get('kind') == 'Q30_COLD77_SERIAL_QUALIFICATION', 'grant scope')
    need(isinstance(grant.get('parentReference'), str) and bool(grant['parentReference'].strip()), 'parent reference')
    for field, value in (('candidateManifestSha256', manifest_sha), ('sourceIdentity', binding['sourceIdentity']),
                         ('planSha256', binding['planSha256']), ('limits', LIMITS), ('rootCount', 77)):
        need(grant.get(field) == value, 'grant binding: ' + field)
    need(type(grant.get('rootCount')) is int and all(type(v) is int for v in grant['limits'].values()), 'grant integer types')
    begin, end = utc(grant['notBeforeUtc']), utc(grant['windowEndUtc'])
    need((end - begin).total_seconds() * 1000 == WINDOW_MS, 'grant must reserve exactly 4h12m')
    need(begin <= now < end, 'grant not active')
    # Reserve the outer cleanup and independent release even when invocation
    # consumes part of the 60-second orchestration allowance.
    budget = min(OPERATION_MS, int((end - now).total_seconds() * 1000) - CLEANUP_MS - RELEASE_MS)
    need(budget >= OPERATION_MS - FINAL_MS + CONTROLLER_RECORD_MS, 'grant has insufficient batch reserve')
    return budget


def verify_candidate(args):
    manifest_path = HERE / 'cold77-candidate-manifest.json'
    manifest = read(manifest_path)[0]
    need(manifest.get('schema') == 1 and manifest.get('limits') == LIMITS and
         manifest.get('windowMs') == WINDOW_MS, 'candidate contract')
    need(set(manifest['sources']) == {'cold77_batch.py', 'cold77_case_worker.py'}, 'candidate source set')
    for name, pin in manifest['sources'].items():
        path = HERE / name
        need(path.is_file() and sha(path) == pin['sha256'] and path.stat().st_size == pin['size'], 'candidate changed: ' + name)
    need(sha(HERE / 'serial_runner.py') == RUNNER_SHA and sha(HERE / 'receipts.py') == RECEIPTS_SHA,
         'maintained process/receipt owner changed')
    need(Path(sys.modules['receipts'].__file__).resolve(strict=True) == (HERE / 'receipts.py').resolve(strict=True),
         'receipts import escaped owner')
    binding, variant, _, _ = audit_inputs(args)
    need(binding == manifest['inputBinding'], 'candidate frozen inputs differ')
    return sha(manifest_path), binding, variant


def build_spec(args, binding, plan):
    common = []
    for name in ('repo', 'pointer', 'matched_helper', 'host_python', 'deps'):
        common.extend(['--' + name.replace('_', '-'), str(getattr(args, name).resolve(strict=True))])
    seeds = plan['coldOrder']
    need(len(seeds) == len(set(seeds)) == 77 and sum(len(row['candidates']) for row in plan['cases']) == 308,
         'frozen cohort/count')
    return {**{key: binding[key] for key in ('sourceIdentity', 'baseHead', 'planSha256')}, 'limits': dict(LIMITS),
        'steps': [{'rootSeed': seed, 'outerTimeoutMs': STEP_MS,
            'argv': [str(args.host_python.resolve(strict=True)), '-B', str(HERE / 'cold77_case_worker.py'),
                     *common, '--root-seed', seed, '--receipt', '{receipt}']} for seed in seeds]}


def full_result(summary, plan):
    seeds = plan['coldOrder']
    receipts = [row['receipt'] for row in summary['rows'] if row['receipt'] is not None]
    checked = summarize_cases(seeds, receipts)
    published = {row['receipt'].get('provenance', {}).get('publishedPackages')
                 for row in checked['rows'] if row['status'] == 'PASS'}
    sizes = sorted(value for value in published if type(value) is int)
    passed = summary.get('outcome') == checked['outcome'] == 'PASS' and None not in published and sizes == list(range(20, 41))
    return {'status': 'PASS_COLD77' if passed else 'FAIL_COLD77', 'qualified': passed,
            'rows': checked['rows'], 'publishedPackageSizes': sizes, 'complete': checked['complete'],
            'qualificationScope': 'Cold77 only; Q30 acceptance and normal-play enablement remain pending.'}


def owned_batch(args):
    manifest_sha, binding, variant = verify_candidate(args)
    root = args.batch_root
    grant = read(root / 'quiet-window-grant.json')[0]
    validate_grant(grant, manifest_sha, binding, datetime.now(timezone.utc))
    # The maintained owner resumes before returning its identity. Give its
    # exclusive launch record a bounded handoff wait inside the outer job.
    handoff_end = time.monotonic() + 2
    while not (root / 'launch.json').is_file() and time.monotonic() < handoff_end:
        time.sleep(0.025)
    launch = read(root / 'launch.json')[0]
    need(launch.get('status') == 'RUNNING' and launch.get('process', {}).get('pid') == os.getpid() and
         launch.get('candidateManifestSha256') == manifest_sha, 'owned batch launch identity')
    need((HERE / ('cold77-launch-used-' + sha(root / 'quiet-window-grant.json') + '.json')).is_file(),
         'owned batch grant not consumed')
    spec = read(root / 'batch-spec.json')[0]
    expected = build_spec(args, audit_inputs(args)[0], variant['plan'])
    need(spec == expected, 'owned batch spec changed')
    runner = imported(HERE / 'serial_runner.py', 'q30_cold77_serial_owner', RUNNER_SHA)
    summary = runner.run_serial(spec, root / 'sequence')
    result = full_result(summary, variant['plan'])
    try:
        after_sha, after, _ = verify_candidate(args)
        need(after_sha == manifest_sha and after == binding, 'final batch input binding changed')
        write_exclusive(root / 'input-audit-after.json', after)
    except Exception as error:
        result.update(qualified=False, status='FAIL_COLD77', inputAuditError=type(error).__name__ + ': ' + str(error))
    write_exclusive(root / 'batch-result.json', result)
    return 0 if result['qualified'] else 1


def recover_rows(root, spec, variant=None, reader=None, binding=None, deadline=None):
    # On an outer timeout retain every completed normalized receipt and the
    # explicit NOT_RUN remainder. Raw in-flight report files remain untouched.
    receipts = []
    for index, step in enumerate(spec['steps'], 1):
        seed = step['rootSeed']
        if deadline is not None:
            need(datetime.now(timezone.utc) < deadline, 'controller finalization reserve exhausted')
        case = root / 'sequence/cases' / (f'{index:03d}-seed-' + seed.replace('-', 'minus-'))
        path = case / 'normalized-receipt.json'
        if not case.is_dir():
            break
        receipt = read(path)[0] if path.is_file() else None
        if reader is not None and (receipt is None or receipt['application']['elapsedMs'] is None):
            # Strictly audit any completed raw report after an interruption.
            # This fills diagnostic app data, never a missing host/source PASS.
            report_path = case / 'host-output/cases' / ('001-cold-' + seed + '.report.json')
            measured, checked = None, {}
            try:
                measured, raw = read(report_path)
                checked = reader.evaluate(measured, raw, variant['plan'], variant['planRaw'],
                                          acceptance_path=variant['paths']['acceptance'])
            except Exception:
                pass  # Raw/invalid report remains preserved; no app PASS invented.
            recovered = receipt_from_records(seed, binding, measured, {}, checked, None, None, False,
                ['Outer interruption; app-only recovery does not establish host or input cleanup'])
            if receipt is None:
                receipt = recovered
            elif checked.get('app', {}).get('status') in ('APP_PASS', 'COMPLETED_NONPASS'):
                receipt['application'] = recovered['application']
            receipt.setdefault('provenance', {})['outerRecovery'] = {
                'scope': 'DIAGNOSTIC_APP_ONLY', 'readerStatus': checked.get('status'),
                'reportSha256': sha(report_path) if report_path.is_file() else None}
        if receipt is None:
            break
        receipts.append(receipt)
        if path.is_file() is False:
            break
    return summarize_cases([row['rootSeed'] for row in spec['steps']], receipts)


def controlled_launch(args, manifest_sha, binding, variant):
    grant, _ = read(args.grant_json)
    validate_grant(grant, manifest_sha, binding, datetime.now(timezone.utc))
    # Hash canonical JSON, so the retained grant and the supplied grant bind
    # the same exclusive marker regardless of whitespace.
    grant_sha = hashlib.sha256((json.dumps(grant, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False) + '\n').encode()).hexdigest()
    marker = HERE / ('cold77-launch-used-' + grant_sha + '.json')
    write_exclusive(marker, {'grantSha256': grant_sha, 'candidateManifestSha256': manifest_sha,
        'parentReference': grant['parentReference'], 'status': 'ONE_COLD77_BATCH_RESERVED'})
    root = Path(tempfile.mkdtemp(prefix='q30-cold77-owned-batch-'))
    spec = build_spec(args, binding, variant['plan'])
    write_exclusive(root / 'batch-spec.json', spec)
    write_exclusive(root / 'input-audit-before.json', binding)
    write_exclusive(root / 'quiet-window-grant.json', grant)
    argv = [str(args.host_python.resolve(strict=True)), '-B', str(HERE / 'cold77_batch.py'), '--owned-batch', '--batch-root', str(root)]
    for name in ('repo', 'pointer', 'matched_helper', 'host_python', 'deps'):
        argv.extend(['--' + name.replace('_', '-'), str(getattr(args, name).resolve(strict=True))])
    runner = imported(HERE / 'serial_runner.py', 'q30_cold77_outer_owner', RUNNER_SHA)
    k, child, waited = runner._api(), None, None
    result = {'schema': 1, 'status': 'FAIL_BATCH_CONTROLLER', 'qualified': False,
              'candidateManifestSha256': manifest_sha, 'windowEndUtc': grant['windowEndUtc'],
              'cleanupLimitMs': CLEANUP_MS, 'independentReleaseReserveMs': RELEASE_MS,
              'taskRoot': str(root), 'errors': []}
    try:
        budget = validate_grant(grant, manifest_sha, binding, datetime.now(timezone.utc)) - CONTROLLER_RECORD_MS
        child = runner._spawn(k, {'executable': args.host_python.resolve(strict=True)}, argv, root)
        budget = validate_grant(grant, manifest_sha, binding, datetime.now(timezone.utc)) - CONTROLLER_RECORD_MS
        result.update(operationLimitMs=budget, process={key: child[value] for key, value in
            (('pid', 'pid'), ('creationFileTimeTicks', 'creationTicks'), ('executable', 'image'),
             ('commandSha256', 'commandSha256'), ('resumeMonotonicNs', 'resumeNs'))})
        pending = root / 'launch.pending.json'
        write_exclusive(pending, {**result, 'status': 'RUNNING'})
        pending.rename(root / 'launch.json')  # Atomic exclusive publish on Windows.
        waited = runner._wait(k, child, budget)
        result['operation'] = waited
        final_deadline = min(utc(grant['windowEndUtc']) - timedelta(milliseconds=RELEASE_MS),
            datetime.now(timezone.utc) + timedelta(milliseconds=CONTROLLER_RECORD_MS))
        result['finalizationDeadlineUtc'] = final_deadline.isoformat().replace('+00:00', 'Z')
        need(datetime.now(timezone.utc) < final_deadline, 'controller finalization reserve exhausted')
        reader_path = args.repo / 'tests/qualification/q30-scale-acceptance/q30_scale_reader.py'
        reader = imported(reader_path, 'q30_cold77_recovery_reader', binding['readerSha256'])
        result['retainedSequence'] = recover_rows(root, spec, variant, reader, binding, final_deadline)
        recovered = full_result(result['retainedSequence'], variant['plan'])
        batch = read(root / 'batch-result.json')[0] if (root / 'batch-result.json').is_file() else None
        result['batch'] = batch
        drains_ok = all(not d.overflow.is_set() and not d.errors and not d.thread.is_alive() and d.streamClosed for d in child['drains'])
        result['qualified'] = bool(waited['reason'] is None and waited['exitCode'] == 0 and
            waited['cleanup']['cleanupVerified'] and drains_ok and batch == recovered and recovered['qualified'] and
            read(root / 'input-audit-after.json')[0] == binding)
        result['status'] = 'PASS_COLD77_CONTROLLER' if result['qualified'] else 'FAIL_BATCH_CONTROLLER'
        need(datetime.now(timezone.utc) < final_deadline, 'controller finalization reserve exhausted')
    except BaseException as error:
        result['errors'].append(type(error).__name__ + ': ' + str(error))
        if child is not None and waited is None:
            try:
                result['emergencyCleanup'] = runner._wait(k, child, 0)
            except BaseException as cleanup_error:
                result['errors'].append('emergency cleanup: ' + repr(cleanup_error))
    finally:
        if child is not None:
            for key in ('job', 'thread', 'proc'):
                if child.get(key) and not k.CloseHandle(child[key]):
                    result['errors'].append('CloseHandle failed: ' + key)
                child[key] = None
        if result['errors']:
            result.update(qualified=False, status='FAIL_BATCH_CONTROLLER')
        record_deadline = min(utc(grant['windowEndUtc']) - timedelta(milliseconds=RELEASE_MS),
            locals().get('final_deadline', datetime.now(timezone.utc) + timedelta(milliseconds=CONTROLLER_RECORD_MS)))
        if datetime.now(timezone.utc) >= record_deadline:
            result.update(qualified=False, status='FAIL_BATCH_CONTROLLER')
            result['errors'].append('controller finalization deadline exhausted')
        write_exclusive(root / 'controller-result.json', result)
        within = datetime.now(timezone.utc) < record_deadline
        if not within:
            result.update(qualified=False, status='FAIL_BATCH_CONTROLLER')
        write_exclusive(root / 'controller-publication.json', {'withinFinalizationDeadline': within,
            'qualified': result['qualified'], 'status': result['status'],
            'scope': 'Requires successful exit and independent desktop release; blocked I/O is not bounded by this cooperative check.'})
    print(json.dumps({'status': result['status'], 'qualified': result['qualified'], 'taskRoot': str(root), 'errors': result['errors']}))
    return 0 if result['qualified'] else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('repo', 'pointer', 'matched-helper', 'host-python', 'deps'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--launch', action='store_true')
    parser.add_argument('--grant-json', type=Path)
    parser.add_argument('--owned-batch', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--batch-root', type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    need(not args.launch or args.grant_json is not None, 'launch requires fresh parent grant')
    need(not args.owned_batch or args.batch_root is not None and not args.launch, 'internal batch arguments')
    manifest_sha, binding, variant = verify_candidate(args)
    if args.owned_batch:
        return owned_batch(args)
    if args.launch:
        return controlled_launch(args, manifest_sha, binding, variant)
    print(json.dumps({'status': 'PASS_OFFLINE_INPUT_AUDIT', 'browserLaunched': False, 'casesLaunched': 0,
        'candidateManifestSha256': manifest_sha, 'inputBinding': binding, 'windowMs': WINDOW_MS,
        'spec': build_spec(args, binding, variant['plan'])}, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
