"""In-memory rejection controls from actual data; no manufactured host receipts."""
import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    raise SystemExit('ISOLATION_REQUIRED')
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

def load(name, path, expected):
    raw = Path(path).read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise SystemExit('SOURCE_CHANGED')
    module = importlib.util.module_from_spec(importlib.util.spec_from_file_location(name, path))
    sys.modules[module.__name__] = module
    exec(compile(raw, path, 'exec'), module.__dict__)
    return module

reader_path, reader_sha, old_path, directory, parent_path = sys.argv[1:]
reader = load('bf_geometry_controls', reader_path, reader_sha)
old = load('bf_controls_parent', old_path, 'a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e')
args = json.loads(Path(parent_path).read_bytes())['process']['Arguments']
data = Path(directory)
doc = reader.strict_document((data/'result.json').read_bytes(), reader.MAX_JSON)
op1 = old.validate_operation((data/'op-1.jsonl').read_bytes(), doc['operation1'], args[6], args[7], 1, args[5], 'AUTHORED')
target = next(row for row in op1['branches'] if row['ordinal'] == 307)
pads = reader._validate_pad_lands(doc['source']['padLands'], op1['pads'])
counter = doc['counterfactual']
snapshot = target['snapshot']
nodes = reader._validate_node_chain(counter['nodeChain'], snapshot)
reader._validate_published_geometry(counter['publishedTraces'], counter['publishedHoles'], pads,
                                    'A_COIL_LOW', 'QA.C', 'DA.A', nodes, True)
reader._validate_validator_scope('FOUND', True, counter['validators'])
cases = []

def reject(name, operation):
    try:
        operation()
    except reader.ReaderError as problem:
        cases.append({'case': name, 'status': 'EXPECTED_REJECTION', 'code': str(problem)})
    else:
        raise AssertionError('MUTATION_ACCEPTED '+name)

def node_case(name, change):
    values = copy.deepcopy(counter['nodeChain'])
    change(values)
    reject(name, lambda: reader._validate_node_chain(values, snapshot))

node_case('changed_start_coordinate', lambda values: values[0].__setitem__('x', values[0]['x']+10))
node_case('changed_goal_coordinate', lambda values: values[-1].__setitem__('x', values[-1]['x']+10))
node_case('invalid_layer', lambda values: values[1].__setitem__('layer', 'INVALID'))
node_case('transition_budget_exceeded', lambda values: values[-1].__setitem__('transitions', 5))
node_case('repeated_state', lambda values: values.insert(1, copy.deepcopy(values[0])))
node_case('changed_cost', lambda values: values[-1].__setitem__('cost', values[-1]['cost']+1))

def trace_case(name, change):
    values = copy.deepcopy(counter['publishedTraces'])
    change(values)
    reject(name, lambda: reader._validate_published_geometry(values, counter['publishedHoles'], pads,
           'A_COIL_LOW', 'QA.C', 'DA.A', nodes, True))

trace_case('changed_trace_width', lambda values: values[0].__setitem__('width', 7))
trace_case('foreign_trace_net', lambda values: values[0].__setitem__('netId', 'TEST_FOREIGN'))
trace_case('changed_published_start', lambda values: values[0]['x'].__setitem__(0, values[0]['x'][0]+10))
trace_case('unknown_pad_identity', lambda values: values[0].__setitem__('startPadId', 'TEST_MISSING'))

values = copy.deepcopy(counter['validators'])
values['routingGeometry'] = 'PASS'
reject('false_whole_board_geometry_pass', lambda: reader._validate_validator_scope('FOUND', True, values))
values2 = copy.deepcopy(counter['validators'])
values2['endpointsConnected'] = False
reject('false_connected_endpoint_result', lambda: reader._validate_validator_scope('FOUND', True, values2))
reject('rejected_path_claims_completed_validators',
       lambda: reader._validate_validator_scope('QUALITY_REJECTED', True, counter['validators']))
print(json.dumps({'schema':'tsj-copper-free-branch-geometry-controls-v1',
                  'status':'PASS_ACTUAL_DATA_GEOMETRY_CONTROLS_ONLY',
                  'readerSha256':reader_sha,'positiveBaseline':True,'expectedRejections':len(cases),
                  'cases':cases,'wholeHostQualified':False}, separators=(',',':')))
