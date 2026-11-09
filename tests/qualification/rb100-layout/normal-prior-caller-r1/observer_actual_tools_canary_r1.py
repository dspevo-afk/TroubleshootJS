"""Actual selected SOURCE/tool observer, without any prior resource or DATA access."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

assert sys.flags.isolated and sys.dont_write_bytecode
parser = argparse.ArgumentParser()
parser.add_argument('--reader-source', required=True)
parser.add_argument('--observer-source', required=True)
parser.add_argument('--specs', required=True)
args = parser.parse_args()
def load(name, path_text, expected):
    path = Path(path_text)
    raw = path.read_bytes()
    assert 0 < len(raw) <= 4194304 and hashlib.sha256(raw).hexdigest() == expected
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module
reader = load('_actual_observer_reader', args.reader_source,
              '00f6d135b0f86c87f389ebb0efd45aa50b804f7c1fe263b02ffe62bb75b9fbd3')
observer = load('_actual_source_tool_observer', args.observer_source,
                '69b9c66222ca9f9a63d2d101250bc7986b1e0466635102044f9b6d1a47759cf7')
specs = json.loads(Path(args.specs).read_bytes())
assert set(specs) == {'toolSpecs', 'sourceSpecs'}
try:
    result = observer.observe_byte_facts(reader=reader, tool_specs=specs['toolSpecs'],
        source_specs=specs['sourceSpecs'], include_harness_module_link=True)
except observer.CallerObservationError as failure:
    print(failure.code, file=sys.stderr)
    raise SystemExit(2)
link = result['harnessModuleLinkEvidence']
assert link['record']['embedded'] is True and link['record']['occurrences'] >= 1
print(json.dumps(dict(status='PASS_ACTUAL_SELECTED_OBSERVER_BYTE_FACTS_ONLY',
    toolFacts=[dict(role=role, bytes=fact['bytes'], sha256=fact['sha256'])
               for role, fact in result['toolFacts'].items()],
    sourceFacts=[dict(role=role, bytes=fact['bytes'], sha256=fact['sha256'])
                 for role, fact in result['sourceFacts'].items()],
    harnessModuleLinkSha256=link['evidenceSha256'], embeddedModulePath=True,
    directoryOrPriorResourcesObserved=False, actualPriorBodiesRead=0,
    callerQualified=False), separators=(',', ':')))
