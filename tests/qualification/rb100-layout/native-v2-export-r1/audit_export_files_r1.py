"""Root independent stdlib audit of synthetic native export artifacts only."""
from pathlib import Path
import hashlib
import json
import re
import sys

assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
T = Path(__file__).resolve().parent
BODY_KEYS = (
    'schema', 'mode', 'status', 'runId', 'sourceManifestSha256',
    'wireContractSha256', 'inputContextSha256', 'qualifiedPrior',
    'originalCapture', 'cohort', 'arms', 'work', 'cleanup')
MARKER_KEYS = (
    'schema', 'status', 'runId', 'sourceManifestSha256', 'wireContractSha256',
    'inputContextSha256', 'resultSha256', 'resultBytes', 'elapsedNanos',
    'finalExportNanos', 'totalGroups', 'totalUnits', 'maxGroupNanos',
    'maximumUnitsPerGroup')
WORK_KEYS = (
    'elapsedCutoffNanos', 'cleanupNanos', 'groupsBeforeExport',
    'wrapperGroupsBeforeExport', 'maxGroupNanosBeforeExport',
    'maximumUnitsPerGroupBeforeExport', 'unitsBeforeExport', 'setupUnits',
    'setupGroups', 'setupCopiedEntries', 'setupCopyChunks',
    'originalCaptureOwnerUnits', 'originalCaptureWrapperUnits',
    'originalCaptureCallbackUnits', 'originalCaptureDiagnosticUnits',
    'originalCaptureGroups', 'originalCaptureCopiedEntries',
    'originalCaptureCopyChunks', 'nullOwnerUnits', 'nullWrapperUnits',
    'nullObserverUnits', 'observeOwnerUnits', 'observeWrapperUnits',
    'observeObserverUnits', 'observerUnits', 'meterUnits', 'metadataUnits',
    'metadataGroups', 'metadataCopiedEntries', 'metadataCopyChunks',
    'copiedEntries', 'copyChunks', 'copyChunkLimit', 'copiedEntryLimit')
WIRE = '1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed'


def pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError('duplicate JSON key')
        result[key] = value
    return result


def reject_number(value):
    raise ValueError('noninteger JSON number: ' + value)


def strict_json(raw, cap, canonical=False):
    assert 0 < len(raw) <= cap
    text = raw.decode('ascii')
    value = json.loads(text, object_pairs_hook=pairs,
                       parse_float=reject_number, parse_constant=reject_number)
    if canonical:
        assert raw == (json.dumps(value, ensure_ascii=True,
                                  separators=(',', ':')) + '\n').encode('ascii')
    return value


def number(value, maximum=9223372036854775807):
    assert type(value) is int and 0 <= value <= maximum
    return value


def ordinary_domain(value):
    visits = 0

    def text(item):
        assert type(item) is str and len(item) <= 256
        assert all(32 <= ord(char) <= 126 for char in item)

    def walk(item, depth):
        nonlocal visits
        visits += 1
        assert visits <= 1000000 and depth <= 64
        if item is None or type(item) is bool:
            return
        if type(item) is int:
            assert -9223372036854775808 <= item <= 9223372036854775807
        elif type(item) is str:
            text(item)
        elif type(item) is list:
            for child in item:
                walk(child, depth + 1)
        elif type(item) is dict:
            for key, child in item.items():
                visits += 1
                assert visits <= 1000000
                text(key)
                walk(child, depth + 1)
        else:
            raise AssertionError('unsupported domain value')

    walk(value, 0)


def owned(root, relative):
    assert type(relative) is str and relative and '\\' not in relative
    rel = Path(relative)
    assert not rel.anchor and '..' not in rel.parts
    path = root / rel
    assert path.resolve(strict=True) == path and path.is_relative_to(root)
    part = path
    while part != root.parent:
        stat = part.lstat()
        assert not part.is_symlink() and not (getattr(stat, 'st_file_attributes', 0) & 0x400)
        part = part.parent
    return path


def audit(root):
    root = root.resolve(strict=True)
    assert root.parent == T and root.name.startswith('fixtures-')
    report_raw = (root / 'positive-reports.json').read_bytes()
    report = strict_json(report_raw, 1048576)
    assert report['schema'] == 'synthetic-native-v2-export-audit-r1'
    assert report['mode'] in ('SMOKE', 'SUITE')
    assert type(report['cases']) is list and report['cases']
    results = []
    outputs = set()
    for case in report['cases']:
        directory = owned(root, case['outputDir'])
        assert directory not in outputs and directory.is_dir()
        outputs.add(directory)
        assert {p.name for p in directory.iterdir()} == {'op-1.jsonl', 'result.json'}
        result_file = owned(root, case['outputDir'] + '/result.json')
        receipt_file = owned(root, case['outputDir'] + '/op-1.jsonl')
        marker_file = owned(root, case['markerFile'])
        assert not marker_file.is_relative_to(directory)
        raw = result_file.read_bytes()
        marker_raw = marker_file.read_bytes()
        receipt = receipt_file.read_bytes()
        body = strict_json(raw, 1048576, canonical=True)
        marker = strict_json(marker_raw, 656, canonical=True)
        ordinary_domain(body)
        ordinary_domain(marker)
        assert tuple(body) == BODY_KEYS and tuple(marker) == MARKER_KEYS
        assert body['schema'] == 'tsj-cohort-terminal-result-v2'
        assert body['mode'] == 'OBSERVED_COHORT_TERMINAL_TELEMETRY'
        assert body['status'] == 'COMPLETED_DIAGNOSTIC_ONLY'
        assert marker['schema'] == 'tsj-cohort-terminal-marker-v2'
        assert marker['status'] == 'COMPLETED_DIAGNOSTIC'
        for key in BODY_KEYS[3:7]:
            assert marker[key] == body[key]
        assert re.fullmatch('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', body['runId'])
        for key in BODY_KEYS[4:7]:
            assert re.fullmatch('[0-9a-f]{64}', body[key])
        assert all(type(body[key]) is dict for key in BODY_KEYS[7:])
        assert body['wireContractSha256'] == WIRE
        assert hashlib.sha256(raw).hexdigest() == marker['resultSha256']
        assert len(receipt) == number(case['receiptBytes'], 8388608)
        assert hashlib.sha256(receipt).hexdigest() == case['receiptSha256']
        work = body['work']
        assert tuple(work) == WORK_KEYS
        for value in work.values():
            number(value)
        pre, post, metrics = case['pre'], case['post'], case['metrics']
        for state in (pre, post):
            for key in ('units', 'groups', 'prior', 'copies', 'chunks', 'maxNanos', 'maxUnits'):
                number(state[key])
        r, m = len(raw), len(marker_raw)
        c = (r + 4095) // 4096
        fee = 5 * c + 3
        duration = number(marker['finalExportNanos'], 5000000000)
        elapsed = number(marker['elapsedNanos'], 90000000000)
        assert elapsed >= work['elapsedCutoffNanos'] + duration
        assert work['wrapperGroupsBeforeExport'] == pre['groups']
        assert work['groupsBeforeExport'] == pre['groups'] + pre['prior']
        offset = sum(work[k] for k in ('originalCaptureOwnerUnits',
                                      'originalCaptureCallbackUnits',
                                      'originalCaptureDiagnosticUnits'))
        assert work['meterUnits'] == work['unitsBeforeExport'] == pre['units'] + offset
        assert work['copiedEntries'] == pre['copies']
        assert work['copyChunks'] == pre['chunks']
        assert work['copyChunkLimit'] == 4096 and work['copiedEntryLimit'] == 1048576
        expected = dict(resultBytes=r, markerBytes=m, exportUnits=fee,
                        finalExportNanos=duration, elapsedNanos=elapsed,
                        totalUnits=work['unitsBeforeExport'] + fee,
                        totalGroups=pre['groups'] + pre['prior'] + 1,
                        maxGroupNanos=max(work['maxGroupNanosBeforeExport'], duration),
                        maximumUnitsPerGroup=max(work['maximumUnitsPerGroupBeforeExport'], fee),
                        copiedEntriesDelta=2*r + 32 + m, copyChunksDelta=2*c + 2)
        for key, value in expected.items():
            assert number(metrics[key]) == value, (case['label'], key)
        for key in MARKER_KEYS[7:]:
            assert marker[key] == expected[key], (case['label'], key)
        assert marker['totalGroups'] <= 640
        assert marker['maxGroupNanos'] <= 5000000000 and marker['maximumUnitsPerGroup'] <= 32768
        assert post['units'] == pre['units'] + fee
        assert post['groups'] == pre['groups'] + 1 and post['prior'] == pre['prior']
        assert post['copies'] == pre['copies'] + 2*r + 32 + m
        assert post['chunks'] == pre['chunks'] + 2*c + 2
        assert post['maxNanos'] == max(pre['maxNanos'], duration)
        assert post['maxUnits'] == max(pre['maxUnits'], fee)
        results.append(dict(label=case['label'], resultBytes=r, markerBytes=m,
                            blocks=c, fee=fee, resultSha256=hashlib.sha256(raw).hexdigest()))
    return dict(status='PASS_INDEPENDENT_STDLIB_SYNTHETIC_EXPORT_FILE_AUDIT_ONLY',
                mode=report['mode'], cases=results,
                reportSha256=hashlib.sha256(report_raw).hexdigest(),
                genuineEnvelope='UNSPENT_GATED', normalHostAcceptance='NONE')


if __name__ == '__main__':
    assert len(sys.argv) == 3
    result = audit(Path(sys.argv[1]))
    target = T / sys.argv[2]
    assert target.parent == T and not target.exists()
    with target.open('x', encoding='utf-8', newline='\n') as handle:
        json.dump(result, handle, sort_keys=True, indent=2)
        handle.write('\n')
    print(json.dumps(result, sort_keys=True))
