"""Compare the current pure marker adapter with a pinned SOURCE-extracted oracle.

Only whitelisted pure definitions execute from the old reader; no module import,
CLI, builder, manifest discovery or native producer is invoked. Actual mode reads
exactly two already-journalled normal roles through the current fixed reader.
"""
import argparse
import ast
import copy
import hashlib
import json
from pathlib import Path
import sys
import types

REFERENCE_SHA = '84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52'

def source(path, digest):
    with Path(path).open('rb') as stream:
        raw = stream.read(4194305)
    assert 0 < len(raw) <= 4194304 and hashlib.sha256(raw).hexdigest() == digest
    return raw

def load(path, digest, name):
    raw = source(path, digest)
    module = types.ModuleType(name)
    module.__file__ = str(path)
    sys.modules[name] = module
    exec(compile(raw, str(path), 'exec'), module.__dict__)
    return module

def reference(path):
    tree = ast.parse(source(path, REFERENCE_SHA))
    names = {'ReaderError', 'need', 'exact_keys', 'is_int', 'sha', '_validate_marker'}
    constants = {'MARKER_SCHEMA':'tsj-failure-cohort-marker-v1', 'COPY_CHUNK':4096,
        'MAX_TOTAL_NS':90000000000, 'MAX_GROUP_NS':5000000000, 'MAX_GROUP_UNITS':32768}
    selected = []
    found = set()
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name in names:
            selected.append(node)
            found.add(node.name)
        elif isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            key = node.targets[0].id
            if key in constants:
                assert ast.literal_eval(node.value) == constants[key]
                selected.append(node)
                found.add(key)
    assert found == names | set(constants) and len(selected) == 11
    assert not any(isinstance(n, (ast.Import, ast.ImportFrom, ast.With, ast.AsyncWith))
                   for node in selected for n in ast.walk(node))
    env = {'__name__':'_pinned_pure_native_marker_oracle', 'hashlib':hashlib}
    exec(compile(ast.Module(body=selected, type_ignores=[]), '<pinned-pure-marker-oracle>', 'exec'), env)
    return env['_validate_marker']

def accepted(function, marker, raw, doc):
    try:
        value = function(copy.deepcopy(marker), raw, copy.deepcopy(doc))
        assert value == marker
        return True
    except ValueError:
        return False

def synthetic(subject, oracle):
    cases = 0
    positives = 0
    for size, export_units in ((1,8), (4095,8), (4096,8), (4097,13), (8192,13), (46323,63)):
        raw = b'x' * size
        doc = {'work':dict(groupsBeforeExport=12, unitsBeforeExport=84,
            maxGroupNanosBeforeExport=3000, maximumUnitsPerGroupBeforeExport=23,
            elapsedCutoffNanos=1000000)}
        marker = dict(schema='tsj-failure-cohort-marker-v1', status='COMPLETED_DIAGNOSTIC',
            resultSha256=hashlib.sha256(raw).hexdigest(), resultBytes=size,
            elapsedNanos=1004000, finalExportNanos=4000, totalGroups=13,
            totalUnits=84+export_units, maxGroupNanos=4000,
            maximumUnitsPerGroup=max(23,export_units))
        variants = [marker]
        for key in marker:
            changed = dict(marker)
            del changed[key]
            variants.append(changed)
        changed = dict(marker, extra=0)
        variants.append(changed)
        variants.append({k:marker[k] for k in ('schema','status','resultSha256','resultBytes')})
        for key in ('resultBytes','elapsedNanos','finalExportNanos','totalGroups',
                    'totalUnits','maxGroupNanos','maximumUnitsPerGroup'):
            for value in (False, True, float(marker[key]), -1, 0, marker[key]-1, marker[key]+1):
                variants.append(dict(marker, **{key:value}))
        for key, value in (('schema','changed'), ('status','changed'), ('resultSha256','0'*64),
            ('elapsedNanos',90000000001), ('finalExportNanos',5000000001),
            ('maximumUnitsPerGroup',32769), ('totalGroups',641), ('elapsedNanos',1003999)):
            variants.append(dict(marker, **{key:value}))
        for item in variants:
            expected = accepted(oracle, item, raw, doc)
            actual = accepted(subject, item, raw, doc)
            assert actual == expected, (size, item)
            cases += 1
            positives += expected
    return dict(comparisons=cases, accepted=positives, rejected=cases-positives,
        resultLengths=[1,4095,4096,4097,8192,46323])

def main():
    if not sys.flags.isolated or not sys.dont_write_bytecode or sys.flags.optimize:
        raise RuntimeError('ISOLATED_NONOPTIMIZED_NO_BYTECODE_REQUIRED')
    p = argparse.ArgumentParser()
    p.add_argument('--normalizer', required=True)
    p.add_argument('--normalizer-sha', required=True)
    p.add_argument('--reference-source', required=True)
    p.add_argument('--actual', action='store_true')
    for arg in ('reader', 'reader-sha', 'root-n', 'journal', 'journal-sha'):
        p.add_argument('--'+arg)
    a = p.parse_args()
    assert sys.flags.isolated and sys.dont_write_bytecode
    sut = load(a.normalizer, a.normalizer_sha, '_native_marker_current_subject')
    oracle = reference(a.reference_source)
    result = dict(status='PASS_PURE_NATIVE_MARKER_REFERENCE_PARITY_ONLY',
        synthetic=synthetic(sut._validate_native_marker, oracle), actual='NOT_RUN',
        nativeCalls=0, fullPriorAcceptance='NOT_ESTABLISHED', referenceSourceSha256=REFERENCE_SHA)
    if result['synthetic']['comparisons'] != 420:
        raise RuntimeError('EXACT_420_REFERENCE_COMPARISONS_REQUIRED')
    if a.actual:
        assert all((a.reader,a.reader_sha,a.root_n,a.journal,a.journal_sha))
        reader = load(a.reader,a.reader_sha,'_marker_reference_current_reader')
        journal,_,jt = reader._read_file_once(a.journal,1048576,None,a.journal_sha,'CURRENT_NORMAL_JOURNAL')
        assert json.loads(journal)['status'] == 'ALLOW_FIXED_20_NORMAL_PRIOR_READ'
        roles = {'N_PRIOR_CALL_STDOUT_TXT':'native-observe-r3/stdout.txt',
                 'N_PRIOR_RESULT':'native-data-r3/result.json'}
        data = {}
        tokens = {}
        pins = {spec.role:spec for spec in reader._ROLE_PINS}
        for role, relative in roles.items():
            pin = pins[role]
            assert pin.root == 'N' and pin.relative_path == relative
            raw,_,token = reader._read_file_once(str(Path(a.root_n)/relative),
                pin.max_bytes,pin.expected_bytes,pin.sha256,role)
            data[role] = raw
            tokens[role] = token
        marker = sut._console_json(data['N_PRIOR_CALL_STDOUT_TXT'],65536,'NATIVE_PRIOR_MARKER')
        raw = data['N_PRIOR_RESULT']
        doc = sut._json_load(raw,8388608,'PRIOR_RESULT',allow_float=False,line=True)
        assert accepted(oracle,marker,raw,doc) and accepted(sut._validate_native_marker,marker,raw,doc)
        for role, relative in roles.items():
            pin = pins[role]
            raw2,_,token2 = reader._read_file_once(str(Path(a.root_n)/relative),
                pin.max_bytes,pin.expected_bytes,pin.sha256,role)
            assert raw2 == data[role] and reader._token_identity(token2) == reader._token_identity(tokens[role])
        j2,_,jt2 = reader._read_file_once(a.journal,1048576,None,a.journal_sha,'CURRENT_NORMAL_JOURNAL')
        assert j2 == journal and reader._token_identity(jt2) == reader._token_identity(jt)
        result['actual'] = dict(status='PASS_TWO_AUTHENTICATED_NORMAL_ROLES_MARKER_ONLY',
            roles=[dict(role=role,bytes=len(data[role]),sha256=pins[role].sha256) for role in roles],
            exactKeys=10, markerCounters=marker)
    print(json.dumps(result,sort_keys=True))

if __name__ == '__main__':
    main()
