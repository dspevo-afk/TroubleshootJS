"""Port only pinned pure synthetic fixture definitions; never load the old module."""
import ast
import hashlib
import json
import types

FIXTURE_SOURCE_SHA256 = 'fa2366d1bc48f036c9b3f3eb2b6944633f8b386a7070902d8051dd92ef462973'
FUNCTIONS = frozenset((
    '_sha', '_json_bytes', '_line', '_pads', '_expected_net_ids',
    '_expected_callback_count', '_snapshot', '_expected_choice', 'make_operation',
))
CONSTANTS = frozenset(('RUN_ID', 'PAD_ROWS', 'PRISTINE_BYTES'))
ADAPTER_ATTRIBUTES = ('sha', 'pad_bytes', 'ARMS', 'SCHEMA', 'LIMITS')

def load_synthetic_fixture(source, kernel_namespace):
    assert type(source) is bytes and len(source) <= 4194304
    assert hashlib.sha256(source).hexdigest() == FIXTURE_SOURCE_SHA256
    original = ast.parse(source)
    selected = []
    found = set()
    for node in original.body:
        if isinstance(node, ast.FunctionDef) and node.name in FUNCTIONS:
            selected.append(node)
            found.add(node.name)
        elif (isinstance(node, ast.Assign) and len(node.targets) == 1
              and isinstance(node.targets[0], ast.Name)
              and node.targets[0].id in CONSTANTS):
            selected.append(node)
            found.add(node.targets[0].id)
    assert found == FUNCTIONS | CONSTANTS
    adapter = types.SimpleNamespace(**{
        name: kernel_namespace['P07_' + name] for name in ADAPTER_ATTRIBUTES})
    namespace = dict(__name__='_current_synthetic_p07_fixture',
                     hashlib=hashlib, json=json, reader=adapter)
    # Annotations are postponed exactly as in the source. No old imports,
    # module-level paths, fixture writers or test classes enter this module.
    future = ast.ImportFrom(module='__future__',
                            names=[ast.alias(name='annotations')], level=0)
    module = ast.fix_missing_locations(ast.Module(body=[future, *selected], type_ignores=[]))
    proof = dict(
        status='PURE_SYNTHETIC_FIXTURE_SOURCE_PORT_ONLY',
        sourceSha256=FIXTURE_SOURCE_SHA256,
        selectedFunctions=sorted(FUNCTIONS), selectedConstants=sorted(CONSTANTS),
        selectedAstSha256=hashlib.sha256(ast.dump(
            ast.Module(body=selected, type_ignores=[]), include_attributes=False).encode()).hexdigest(),
        adapterAttributes=list(ADAPTER_ATTRIBUTES),
        oldModuleImports=0, filesystemOperations=0,
        originalArmObjects=0, nativeOrSessionCalls=0)
    exec(compile(module, '<current-synthetic-p07-fixture>', 'exec'), namespace)
    return namespace, proof
