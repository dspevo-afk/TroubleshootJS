"""Normalize retained cold receipts without running or changing qualification."""
import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath, PureWindowsPath
from receipts import LIMITS, summarize_cases, validate_case, write_exclusive


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read(path):
    raw = path.read_bytes()
    def pairs(rows):
        value = {}
        for key, item in rows:
            if key in value:
                raise ValueError('duplicate JSON key: ' + key)
            value[key] = item
        return value
    doc = json.loads(raw.decode('utf-8-sig'), object_pairs_hook=pairs,
                     parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
    return doc, sha(raw)


def milliseconds(seconds):
    if type(seconds) not in (int, float) or not math.isfinite(seconds) or seconds < 0:
        raise ValueError('invalid independent elapsed seconds')
    return math.ceil(seconds * 1000)


def safe_file(root, raw):
    if (not isinstance(raw, str) or not raw or '\\' in raw or ':' in raw or
            PurePosixPath(raw).is_absolute() or PureWindowsPath(raw).is_absolute() or
            any(part in ('', '.', '..') for part in raw.split('/'))):
        raise ValueError('unsafe relative input path')
    root = root.resolve(strict=True)
    result = root.joinpath(*raw.split('/')).resolve(strict=True)
    if not result.is_relative_to(root) or not result.is_file():
        raise ValueError('input escaped its root or is not a file')
    return result


def verify_snapshot(snapshot, plan, repo):
    if snapshot['schema'] != 1 or plan['baseHead'] != snapshot['baseHead']:
        raise ValueError('source schema/base HEAD mismatch')
    payload = {key: snapshot[key] for key in ('schema', 'baseHead', 'files')}
    identity = sha(json.dumps(payload, sort_keys=True, separators=(',', ':'),
                              ensure_ascii=False).encode('utf-8'))
    if identity != snapshot['sourceIdentity'] or identity != plan['sourceIdentity']:
        raise ValueError('canonical source identity mismatch')
    seen = set()
    for row in snapshot['files']:
        if row['path'] in seen:
            raise ValueError('duplicate source input path')
        seen.add(row['path'])
        raw = safe_file(repo, row['path']).read_bytes()
        if len(raw) != row['size'] or sha(raw) != row['sha256']:
            raise ValueError('consumed source changed: ' + row['path'])


def normalize(task, repo):
    plan, plan_hash = read(task / 'scale-plan.json')
    snapshot, _ = read(task / 'isolated/source-snapshot.json')
    if plan['limits'] != LIMITS or plan['rootCount'] != 77 or len(plan['coldOrder']) != 77:
        raise ValueError('original frozen77/90s/640/5s contract changed')
    if plan['sourceIdentity'] != snapshot['sourceIdentity'] or plan['sourceFiles'] != snapshot['files']:
        raise ValueError('source snapshot/plan mismatch')
    if len(snapshot['files']) != 1354:
        raise ValueError('source input count changed')
    verify_snapshot(snapshot, plan, repo)
    receipts = []
    for batch in ('cold-pilot', 'cold-rest'):
        sequence, _ = read(task / batch / 'sequence.json')
        for row in sequence['rows']:
            seed, index = row['rootSeed'], row['index']
            if plan['coldOrder'][index - 1] != seed:
                raise ValueError('original cold order changed')
            slug = '%03d-seed%s' % (index, seed)
            base = task / batch / slug
            host, host_hash = read(base / 'result.json')
            reader, reader_hash = read(task / batch / (slug + '-reader.json'))
            observed = host['cases'][0]
            report, report_hash = read(safe_file(base, observed['reportFile']))
            if (reader['synthetic'] is not False or reader['scalePlanSha256'] != plan_hash or
                    reader['app']['sha256'] != report_hash or reader['app']['rootSeed'] != seed or
                    report['sourceIdentity'] != snapshot['sourceIdentity'] or
                    report['sourceCommit'] != snapshot['baseHead'] or report['rootSeed'] != seed):
                raise ValueError('retained reader/report binding differs')
            app_status = reader['app']['status']
            if (app_status == 'APP_PASS' and reader['status'] == 'APP_PASS' and
                    report['classification'] == report['applicationOutcome'] == 'PASS' and
                    reader['app']['errors'] == []):
                app_status = 'PASS'
            elif app_status == reader['status'] == 'COMPLETED_NONPASS' and reader['app']['errors'] == []:
                app_status = report['applicationOutcome'] if report['applicationOutcome'] == 'TIMEOUT' else 'FAIL'
            else:
                app_status = 'FAIL'
            clean = host['cleanup']
            audit = host['inputAudit']
            expected = plan['preparedInputIdentity']
            host_pass = (reader['host']['status'] == 'HOST_PASS' and reader['host']['errors'] == [] and
                host['outcome'] == 'PASS' and host['errors'] == [] and len(host['cases']) == 1 and
                observed['outcome'] == 'PASS' and observed['terminalReached'] is True and
                observed['stateMatch'] is True and observed['reportMatch'] is True and observed['timedOut'] is False and
                audit['status'] == 'PASS' and audit['beforeSha256'] == audit['afterSha256'] and
                audit['sourceSha256Before'] == audit['sourceSha256After'] == expected['sourceSha256'] and
                audit['webSha256Before'] == audit['webSha256After'] == expected['webSha256'] and
                audit['runnerBeforeSha256'] == audit['runnerAfterSha256'] == plan['hostRunnerSha256'] and
                audit['fileCountBefore'] == audit['fileCountAfter'] == expected['fileCount'])
            receipt = {
                'schema': 1, 'rootSeed': seed, 'sourceIdentity': snapshot['sourceIdentity'],
                'baseHead': snapshot['baseHead'], 'planSha256': plan_hash, 'limits': dict(LIMITS),
                'application': {'status': app_status, 'elapsedMs': report['generationElapsedMs'],
                    'workUnits': report['workUnits'], 'maxActiveOperationMs': report['maxActiveOperationMs'],
                    'maxCoordinatorAdvanceMs': report['maxCoordinatorAdvanceMs']},
                'host': {'status': 'PASS' if host_pass else 'FAIL',
                    'observationElapsedMs': milliseconds(observed['operationSeconds']),
                    'deadlineMs': milliseconds(observed['case']['timeoutSeconds']),
                    'exitCode': row['hostExitCode'], 'terminal': observed['terminalReached']},
                'cleanup': {'status': clean['status'] if clean['errors'] == [] else 'FAIL', 'elapsedMs': milliseconds(clean['seconds']),
                    'elapsedLimitations': None, 'serverStopped': clean['serverStopped'],
                    'ownedSurvivors': clean['ownedSurvivors']},
                'operation': {'elapsedMs': milliseconds(row['hostSeconds']), 'elapsedLimitations': None},
                'sourceInputsUnchanged': True,
                'provenance': {'legacyCase': batch + '/' + slug, 'hostSha256': host_hash,
                    'readerSha256': reader_hash, 'reportSha256': report_hash,
                    'legacyStatus': row['status'], 'originalHostSeconds': observed['operationSeconds'],
                    'originalCleanupSeconds': clean['seconds'], 'originalOperationSeconds': row['hostSeconds'],
                    'rounding': 'independent seconds rounded upward to integer milliseconds; application clock unchanged'},
            }
            validate_case(receipt)
            receipts.append(receipt)
    summary = summarize_cases(plan['coldOrder'], receipts)
    summary.update({'schema': 1, 'scope': 'Retained original cold77 receipts; no gate rerun',
                    'sourceIdentity': snapshot['sourceIdentity'], 'baseHead': snapshot['baseHead'],
                    'planSha256': plan_hash, 'sourceInputsRechecked': 1354, 'limits': dict(LIMITS)})
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--task-root', type=Path, required=True)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    summary = normalize(args.task_root.resolve(strict=True), args.repo.resolve(strict=True))
    write_exclusive(args.output, summary)
    print(json.dumps({'outcome': summary['outcome'], 'complete': summary['complete'],
                      'counts': {status: sum(row['status'] == status for row in summary['rows'])
                                 for status in ('PASS', 'FAIL', 'NOT_RUN')}}, indent=2))


if __name__ == '__main__':
    main()
