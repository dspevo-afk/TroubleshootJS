"""Root-only canary for one already-authorized normal stdout role.

Explicit paths are supplied by root. No manifest rows or other DATA are opened.
This is console-boundary evidence, never complete prior qualification.
"""
import argparse
import hashlib
from pathlib import Path
import sys
import types

def load(path, digest, name):
    raw = Path(path).read_bytes()
    assert 0 < len(raw) <= 4194304 and hashlib.sha256(raw).hexdigest() == digest
    module = types.ModuleType(name)
    module.__file__ = str(path)
    sys.modules[name] = module
    exec(compile(raw, str(path), 'exec'), module.__dict__)
    return module

def main():
    p = argparse.ArgumentParser()
    for arg in ('normalizer', 'normalizer-sha', 'reader', 'reader-sha', 'root-d', 'journal', 'journal-sha'):
        p.add_argument('--' + arg, required=True)
    a = p.parse_args()
    assert sys.flags.isolated and sys.dont_write_bytecode
    reader = load(a.reader, a.reader_sha, '_console_canary_reader')
    normalizer = load(a.normalizer, a.normalizer_sha, '_console_canary_normalizer')
    journal, _, jt = reader._read_file_once(a.journal, 1048576, None, a.journal_sha, 'ROOT_NORMAL_JOURNAL')
    import json
    assert json.loads(journal)['status'] == 'ALLOW_FIXED_20_NORMAL_PRIOR_READ'
    pins = {row.role: row for row in reader._ROLE_PINS}
    pin = pins['D_PRIOR_QUALIFICATION_STDOUT_TXT']
    assert pin.root == 'D' and pin.relative_path == 'prior-input-qualify-r2/stdout.txt'
    path = str(Path(a.root_d) / 'prior-input-qualify-r2/stdout.txt')
    raw, digest, token = reader._read_file_once(path, pin.max_bytes, pin.expected_bytes, pin.sha256, pin.role)
    assert len(raw) == 812 and raw.endswith(b'\r\n') and raw.count(b'\r') == raw.count(b'\n') == 1
    value = normalizer._console_json(raw, 1024, 'QUALIFICATION_STDOUT')
    normalizer._exact_keys(value, normalizer.QUALIFICATION_SUMMARY_KEYS, 'QUALIFICATION_SUMMARY_KEYS')
    expected = dict(schema='tsj-failure-cohort-reader-v1',
        status='PASS_OBSERVED_FAILURE_COHORT_DIAGNOSIS_ONLY',
        manifestSha256=pins['N_PRIOR_RUN_MANIFEST'].sha256,
        readerSha256=normalizer.N_PRIOR_READER_SHA256,
        resultSha256=pins['N_PRIOR_RESULT'].sha256,
        operation1Sha256=pins['N_PRIOR_OPERATION1'].sha256,
        trialDisposition='TRIAL_EXECUTED', netCount=60, controlDisposition='NO_PATH',
        trialOutcomeDisposition='CAP_REJECTED', controlTraversedCount=44,
        trialTraversedCount=43, controlLayoutConnectivity=None, trialLayoutConnectivity=None,
        layoutQualification=False, routingTreatmentSelected=False, productionAdopted=False,
        feasibilityClaimed=False, playable100Accepted=False)
    assert value == expected and all(type(value[k]) is type(v) for k, v in expected.items())
    for broken in (raw[:-2], raw + b'{}\r\n', raw[:-2] + b'\r', raw[:-2] + b'\n{}\n'):
        try:
            normalizer._console_json(broken, 1024, 'QUALIFICATION_STDOUT')
        except normalizer.PriorNormalizationError:
            pass
        else:
            raise AssertionError('MALFORMED_FRAMING_ACCEPTED')
    again, _, newer = reader._read_file_once(path, pin.max_bytes, pin.expected_bytes, pin.sha256, pin.role)
    assert again == raw and reader._token_identity(newer) == reader._token_identity(token)
    j2, _, jt2 = reader._read_file_once(a.journal, 1048576, None, a.journal_sha, 'ROOT_NORMAL_JOURNAL')
    assert j2 == journal and reader._token_identity(jt2) == reader._token_identity(jt)
    print(json.dumps(dict(status='PASS_AUTHORIZED_CONSOLE_BOUNDARY_ONLY', bytes=812,
        sha256=digest, terminator='CRLF', exactKeys=19, exactValues=True,
        malformedFramingRejections=4, roleBodyNamesRead=[pin.role],
        actualPriorAcceptance='NOT_ESTABLISHED', nativeCalls=0), sort_keys=True))

if __name__ == '__main__':
    main()
