"""Authenticate one actual run, then reject in-memory semantic mutations."""
import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    raise SystemExit('ISOLATION_REQUIRED')
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

reader_path, reader_sha, manifest, manifest_sha, parent, directory = sys.argv[1:]
raw = Path(reader_path).read_bytes()
if hashlib.sha256(raw).hexdigest() != reader_sha:
    raise SystemExit('READER_CHANGED')
module = importlib.util.module_from_spec(importlib.util.spec_from_file_location('bf_actual_controls', reader_path))
sys.modules[module.__name__] = module
exec(compile(raw, reader_path, 'exec'), module.__dict__)
verified = module.validate_run(manifest, manifest_sha, parent, directory)
original = module.strict_document(verified['resultRaw'], module.MAX_JSON)
stable = json.dumps(original, sort_keys=True, separators=(',', ':'))
cases = []

def replace(document, path, value):
    target = document
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value

mutations = [
    ('global_receipt_swapped_with_lineage', ('reference','receiptOrdinal'),77),
    ('local_lineage_swapped_with_receipt', ('reference','lineageOrdinal'),307),
    ('rounded_large_root_seed', ('operation1','rootSeed'),'9007199254740992'),
    ('original_prefix_holes_removed', ('reference','prefixHoleCount'),0),
    ('fresh_prefix_not_copper_free', ('counterfactual','initialHoleCount'),48),
    ('via_limit_raised', ('counterfactual','maximumVias'),49),
    ('transition_limit_raised', ('counterfactual','maximumTransitions'),5),
    ('branch_limit_raised', ('counterfactual','maximumBranchExpansions'),100001),
    ('source_effects_changed', ('source','templateEffectsAfterSha256'),'0'*64),
    ('detached_geometry_changed', ('source','detachedGeometrySha256'),'0'*64),
    ('owner_reference_retained', ('cleanup','referencesReleased'),False),
    ('group_cap_exceeded', ('work','groups'),641),
    ('group_time_exceeded', ('work','maxGroupNanos'),5000000001),
    ('operation_time_exceeded', ('work','elapsedNanos'),90000000001),
    ('unit_cap_exceeded', ('work','maximumUnitsPerGroup'),32769),
    ('false_whole_board_geometry_pass', ('counterfactual','validators','routingGeometry'),'PASS'),
    ('required_partial_gate_omitted', ('counterfactual','validators','twoLayerRules'),'NOT_RUN'),
    ('trace_width_changed', ('counterfactual','publishedTraces',0,'width'),7),
    ('pad_net_identity_changed', ('source','padLands',0,'netId'),'TEST_FOREIGN'),
    ('receipt_byte_identity_changed', ('operation1','receiptSha256'),'0'*64),
    ('unobserved_reached_pad_added', ('reference','reachedPadIds'),['DA.A','TEST_EXTRA']),
]
for name, path, value in mutations:
    document = copy.deepcopy(original)
    replace(document, path, value)
    try:
        module._validate_result_document(document, verified['op1'], verified['op1Raw'],
                                         manifest_sha, module.WIRE_SHA256, verified['runId'])
    except module.ReaderError as problem:
        cases.append({'case':name,'status':'EXPECTED_REJECTION','code':str(problem)})
    else:
        raise AssertionError('MUTATION_ACCEPTED '+name)
if stable != json.dumps(original, sort_keys=True, separators=(',', ':')):
    raise AssertionError('ORIGINAL_MUTATED')
module._validate_result_document(original, verified['op1'], verified['op1Raw'],
                                 manifest_sha, module.WIRE_SHA256, verified['runId'])
module.recheck_inputs(verified['context']) if hasattr(module, 'recheck_inputs') else verified['context']['parentAuditor'].recheck_inputs(verified['context']['parentInputContext'])
print(json.dumps({'schema':'tsj-copper-free-branch-semantic-controls-v1',
                  'status':'PASS_AUTHENTICATED_ACTUAL_RUN_CONTROLS_ONLY','readerSha256':reader_sha,
                  'manifestSha256':manifest_sha,'positiveBeforeAfter':True,
                  'expectedRejections':len(cases),'cases':cases,
                  'syntheticHistoricalHostEvidence':False},separators=(',',':')))
