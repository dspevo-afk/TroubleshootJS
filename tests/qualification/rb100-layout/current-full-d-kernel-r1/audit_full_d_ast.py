"""Independent inverse-prefix AST audit; never executes the emitted owner."""
import argparse
import ast
import copy
import hashlib
import json
from pathlib import Path
import sys

assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
K = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--kernel-source', type=Path, default=K/'full_d_kernel.py')
parser.add_argument('--provenance-source', type=Path, default=K/'FULL_D_PROVENANCE_R1.json')
parser.add_argument('--prior-source', type=Path, default=K.parent/'current-context-kernel-r1/prior_kernel.py')
parser.add_argument('--codec-source', type=Path, default=K.parent/'current-context-kernel-r1/context_codec.py')
parser.add_argument('--observation-source', type=Path, default=K.parent/'general-observation-consumer-v2-r1/observation_v2_kernel.py')
args = parser.parse_args()
source_paths = {'prior_kernel.py':args.prior_source, 'context_codec.py':args.codec_source,
                'observation_v2_kernel.py':args.observation_source}
sha = lambda raw: hashlib.sha256(raw).hexdigest()
emitted_raw = args.kernel_source.read_bytes()
assert sha(emitted_raw) == '8e8c374a089a13c4defea9c4b5b548eb1bc1242cddabc1faf468dba420695af4'
emitted = ast.parse(emitted_raw)
proof_raw = args.provenance_source.read_bytes()
assert sha(proof_raw) == 'c241bc75103425d7d75851c2a109853f1d2019e6a9b14395259415d130a36b30'
proof = json.loads(proof_raw)


def target_names(node):
    if isinstance(node, ast.Name):
        return {node.id}
    if isinstance(node, (ast.Tuple, ast.List)):
        return set().union(*(target_names(item) for item in node.elts))
    return set()


def bindings(statement):
    if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
        return {statement.name}
    if isinstance(statement, ast.Assign):
        return set().union(*(target_names(item) for item in statement.targets))
    if isinstance(statement, ast.AnnAssign):
        return target_names(statement.target)
    if isinstance(statement, ast.Import):
        return {item.asname or item.name.split('.')[0] for item in statement.names}
    if isinstance(statement, ast.ImportFrom):
        return {item.asname or item.name for item in statement.names}
    return set()


class Inverse(ast.NodeTransformer):
    def __init__(self, mapping):
        self.mapping = mapping
    def visit_Name(self, node):
        node.id = self.mapping.get(node.id, node.id)
        return node
    def visit_FunctionDef(self, node):
        node.name = self.mapping.get(node.name, node.name)
        return self.generic_visit(node)
    visit_AsyncFunctionDef = visit_FunctionDef
    def visit_ClassDef(self, node):
        node.name = self.mapping.get(node.name, node.name)
        return self.generic_visit(node)
    def visit_Global(self, node):
        node.names = [self.mapping.get(name, name) for name in node.names]
        return node
    def visit_alias(self, node):
        if node.asname is not None:
            node.asname = self.mapping.get(node.asname, node.asname)
        return node


def ast_sha(body):
    tree = ast.Module(body=body, type_ignores=[])
    return sha(ast.dump(tree, include_attributes=False).encode('utf-8'))


rows = []
for name, prefix, expected_sha in (
    ('prior_kernel.py', 'PK_', 'f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873'),
    ('context_codec.py', 'CC_', 'c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27'),
    ('observation_v2_kernel.py', 'OV2_', '502ff2e1dec1c4d01ddb4e2841aae6f7e4073a62975c1cc9668a09cf5a52b89c'),
):
    raw = source_paths[name].read_bytes()
    assert sha(raw) == expected_sha
    original = ast.parse(raw)
    mapping = dict(proof['composedKernels']['namespaceProof'][name]['rename'])
    assert mapping == {old: prefix + old for old in mapping}
    expected_bindings = set().union(*(bindings(node) for node in original.body)) - {'annotations'}
    if (original.body and isinstance(original.body[0], ast.Expr)
            and isinstance(original.body[0].value, ast.Constant)
            and isinstance(original.body[0].value.value, str)):
        expected_bindings.add('__doc__')
    assert set(mapping) == expected_bindings
    body = copy.deepcopy(original.body)
    if body and isinstance(body[0], ast.Expr) and isinstance(body[0].value, ast.Constant) and isinstance(body[0].value.value, str):
        body[0] = ast.Assign(targets=[ast.Name(id='__doc__', ctx=ast.Store())], value=body[0].value)
    body = [node for node in body if not (isinstance(node, ast.ImportFrom) and node.module == '__future__')]
    normalized_aliases = 0
    for node in body:
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            for alias in node.names:
                if alias.asname is None:
                    alias.asname = alias.name.split('.')[0] if isinstance(node, ast.Import) else alias.name
                    normalized_aliases += 1
    selected = [copy.deepcopy(node) for node in emitted.body
                if any(bound.startswith(prefix) for bound in bindings(node))]
    assert len(selected) == len(body)
    reversed_tree = Inverse({new: old for old, new in mapping.items()}).visit(
        ast.Module(body=selected, type_ignores=[]))
    expected_ast = ast_sha(body)
    reverse_ast = ast_sha(reversed_tree.body)
    assert expected_ast == reverse_ast, name
    rows.append(dict(source=name, sourceSha256=expected_sha, prefix=prefix,
                     statements=len(body), normalizedImplicitImportAliases=normalized_aliases,
                     normalizedExpectedAstSha256=expected_ast, inverseAstSha256=reverse_ast))
summary = dict(status='PASS_INDEPENDENT_INVERSE_AST_STRUCTURAL_AUDIT',
    kernelSha256=sha(emitted_raw), provenanceSha256=sha(proof_raw), rows=rows,
    scope='STRUCTURAL_EQUIVALENCE_WITH_EXPLICIT_IMPORT_ALIAS_AND_NAMESPACED_DOC_BINDING_NORMALIZATION_NOT_GENERAL_LEXICAL_PROOF',
    actualPriorDataReads=0, oldDDataReads=0, protectedHeldoutReads=0,
    legacyModulesExecuted=0, emittedKernelExecuted=0, nativeCalls=0)
print(json.dumps(summary, sort_keys=True, separators=(',', ':')))
