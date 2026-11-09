"""Selected Windows observer on explicit root-owned synthetic resource fixtures."""
import argparse
import errno
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
from unittest import mock

assert sys.flags.isolated and sys.dont_write_bytecode
parser = argparse.ArgumentParser()
parser.add_argument('--reader-source', required=True)
parser.add_argument('--observer-source', required=True)
parser.add_argument('--config', required=True)
parser.add_argument('--case', required=True)
args = parser.parse_args()
def load(name, path_text, digest):
    path = Path(path_text)
    assert path.stat().st_size <= 4194304
    with path.open('rb') as stream:
        raw = stream.read(4194305)
    assert 0 < len(raw) <= 4194304 and hashlib.sha256(raw).hexdigest() == digest
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module
reader = load('_observer_resource_current_reader', args.reader_source,
              '863a258cbab888cf112b9e927e16a099436632e9a1d43eefe0b70690c30543d8')
observer = load('_observer_resource_current_source', args.observer_source,
                '4786259585a13819350add727bb2bfbca9f6890db208fbe8f4f60959d8c94f8c')
config = json.loads(Path(args.config).read_bytes())
assert set(config) == {'toolSpecs', 'sourceSpecs', 'directorySpecs', 'absenceSpecs'}
def observe():
    return observer.observe_caller_facts(reader=reader, tool_specs=config['toolSpecs'],
        source_specs=config['sourceSpecs'], directory_specs=config['directorySpecs'],
        absence_specs=config['absenceSpecs'])
try:
    if args.case == 'injected-access-error':
        target = config['absenceSpecs']['qualification']['processRoot']['path']
        original = os.lstat
        def access_error(path, *a, **kw):
            if os.path.normcase(os.fspath(path)) == os.path.normcase(target):
                error = PermissionError(errno.EACCES, 'synthetic access control')
                error.winerror = 5
                raise error
            return original(path, *a, **kw)
        with mock.patch.object(observer.os, 'lstat', side_effect=access_error):
            result = observe()
    elif args.case == 'actual-replacement':
        directory = config['directorySpecs']['qualification']['path']
        target = Path(directory) / 'owner.json'
        original = os.scandir
        count = [0]
        def replace_on_second_scan(path):
            if os.path.normcase(os.fspath(path)) == os.path.normcase(directory):
                count[0] += 1
                if count[0] == 2:
                    replacement = target.with_name('owned-replacement.tmp')
                    replacement.write_bytes(b'actual synthetic replacement\n')
                    os.replace(replacement, target)
            return original(path)
        with mock.patch.object(observer.os, 'scandir', side_effect=replace_on_second_scan):
            result = observe()
    else:
        result = observe()
except observer.CallerObservationError as failure:
    print(failure.code, file=sys.stderr)
    raise SystemExit(2)
assert args.case == 'positive'
assert tuple(result['resourceFacts']) == ('qualification', 'nativePrior', 'nativeData')
assert all(result['resourceFacts'][name][field]['absent'] is True
    for name in ('qualification', 'nativePrior') for field in ('processRoot', 'stderrLog', 'stdoutLog'))
print(json.dumps(dict(status='PASS_SELECTED_SYNTHETIC_OBSERVER_RESOURCES_ONLY',
    tools=len(result['toolFacts']), sources=len(result['sourceFacts']),
    directoryCounts={name: len(facts['directoryNames']) for name, facts in result['resourceFacts'].items()},
    observedAbsences=6, actualPriorBodiesRead=0, normalCallerQualified=False), separators=(',', ':')))
