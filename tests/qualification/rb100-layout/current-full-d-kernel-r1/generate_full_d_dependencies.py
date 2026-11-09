"""Deterministic SOURCE composer and provenance generator for full-D V2.

This module accepts exact source bytes and an explicitly authored semantic
adapter source. It performs no filesystem access, imports no target modules,
and executes no source text. It emits a self-contained source module with
scope-aware AST namespaces and a canonical provenance record. The emitted
module must still pass root-owned selected-I-B purity and behavior gates.
"""
from __future__ import annotations

import ast
import builtins
import copy
import hashlib
import json
import re
import symtable
from typing import NamedTuple


MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_KERNEL_BYTES = 4 * 1024 * 1024
PROVENANCE_SCHEMA = "tsj-full-d-v2-dependency-generation-v1"

SOURCE_PINS = {
    "prior_kernel.py": (122882, "f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873"),
    "context_codec.py": (21653, "c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27"),
    "extract_prior_kernel.py": (45816, "a0fed4127673b665b5fb977cbd5ff06f95612d3bfc3786dfb98816a4edeebf7e"),
    "observation_v2_kernel.py": (16734, "502ff2e1dec1c4d01ddb4e2841aae6f7e4073a62975c1cc9668a09cf5a52b89c"),
    "read_full_observation_component_v2.py": (115770, "f23a965ef3bc28a78ef06158efb9c7c50c375dc164d7e2840e5d350f51db58c3"),
    "test_general_observation_adapters.py": (15532, "833df3a60ca91f0054781178d321eade14df947caf022caee9fb10cab29ce53f"),
    "test_observation_v2_kernel.py": (28953, "6c0bc60f451488fa2aac3d662433b449d287a30e195ee06e82a1c615290da476"),
    "PLAN_R1.md": (5856, "5ae3b8d88ceaafb5706b6cabffee4db8fb54fed01500df25dd089beb5c0843ac"),
    "WIRE_R1.md": (7363, "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed"),
    "CONTEXT_R1.md": (4734, "3536015e2bf93078b9d5b05d6963faf8b136dec98fe088a65a3da62af0e5bbcc"),
    "CONSUMER_MAP_R1.md": (3523, "5250c5c982d7c3b51ac2a18a93caa3fc5a79be5f59bdb3263c9348078e6c93c9"),
    "PREPAYMENT_R1.md": (4567, "2d6968320ad1707e3eb913429f7b23c6ebd7ee27a1ad1d3b05fec5197e2fed39"),
    "MARKER_BOUND_R1.json": (1164, "886a2a9a35bc81d85851d82c85b3d54ed2157aab1d66e8602169f0e9cd36cfed"),
    "N_ORACLE_SOURCE.py": (92761, "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"),
    "P07_ORACLE_SOURCE.py": (137864, "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"),
    "PROVENANCE_R1.json": (8659, "215ad455f799117d30ca9a7e3101ee9326c8af4fdd30295c4fd0d28331a7436f"),
}

ORACLE_FUNCTIONS = (
    "need", "exact_keys", "is_int", "is_sha", "is_uuid", "sha",
    "_pairs", "_reject_constant", "_finite_tree", "_strict_json_line_core",
    "strict_json_line", "_ascii_tree", "_prior_semantics",
    "_prior_grid_dimensions", "_pad_lookup", "_expected_prior_ref",
    "_validate_original_capture", "_validate_arm_cleanup", "_validate_snapshot",
    "_validate_observation", "_validate_arm", "_validate_work",
    "_validate_top_cleanup", "validate_result", "_parity_projection",
    "_validate_finite_expectation", "_validate_marker",
    "_r7_validate_json_domain", "_r7_canonical_json_line",
    "validate_r7_marker_line", "_r7_export_copy_deltas",
    "derive_export_copy_reconciliation", "_r8_pad_projection",
    "derive_original_group_reconciliation",
)
ORACLE_CONSTANTS = (
    "RESULT_KEYS", "R7_MARKER_KEYS", "PRIOR_KEYS", "RECEIPT_KEYS",
    "CAPTURE_KEYS", "CAPTURE_REQUEST_KEYS", "SEED_KEYS",
    "CAPTURE_OWNER_KEYS", "TEMPLATE_KEYS", "PAD_IDENTITY_KEYS",
    "COHORT_KEYS", "ARM_KEYS", "ARM_CLEANUP_KEYS", "WORK_KEYS",
    "TOP_CLEANUP_KEYS", "MAX_JSON_LINE", "MAX_MARKER_BYTES",
    "EXPORT_DIGEST_BYTES", "MIN_SIGNED_LONG", "MAX_SIGNED_LONG",
    "MAX_OPERATION1_BYTES", "MAX_GROUPS", "MAX_GROUP_NS", "MAX_GROUP_UNITS",
    "MAX_TOTAL_NS", "MAX_CLEANUP_NS", "MAX_EXPANSIONS", "MAX_COPY_ENTRIES",
    "COPY_CHUNK", "MAX_INT", "UUID_RE", "SHA_RE", "RESULT_SCHEMA",
    "MARKER_SCHEMA", "CLEANUP_SCHEMA", "RESULT_MODE", "R8_P07_PAD_KEYS",
    "R8_PAD_PHYSICAL_KEYS",
)
FORBIDDEN_IMPORTS = frozenset({
    "os", "pathlib", "stat", "sys", "time", "subprocess", "importlib",
    "runpy", "socket", "threading", "multiprocessing",
})
FORBIDDEN_CALLS = frozenset({
    "open", "exec", "eval", "compile", "__import__", "getattr", "setattr",
    "globals", "locals", "vars", "dir",
})


class GenerationError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class GenerationResult(NamedTuple):
    kernel_source: bytes
    provenance_json: bytes


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _reject(code: str) -> None:
    raise GenerationError(code)


def _parse_module(label: str, raw: bytes) -> ast.Module:
    if type(raw) is not bytes or len(raw) > MAX_SOURCE_BYTES:
        _reject("SOURCE_BYTES_" + label)
    try:
        return ast.parse(raw.decode("utf-8", errors="strict"), filename=label,
                         mode="exec", type_comments=True)
    except (UnicodeError, SyntaxError, ValueError, TypeError):
        _reject("SOURCE_AST_" + label)


def _top_level_nodes(tree: ast.Module) -> dict[str, list[ast.stmt]]:
    nodes: dict[str, list[ast.stmt]] = {}
    for statement in tree.body:
        if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            nodes.setdefault(statement.name, []).append(statement)
        elif isinstance(statement, (ast.Assign, ast.AnnAssign)):
            targets = statement.targets if isinstance(statement, ast.Assign) else [statement.target]
            for target in targets:
                if isinstance(target, ast.Name):
                    nodes.setdefault(target.id, []).append(statement)
    return nodes


def _ast_sha(nodes: list[ast.stmt]) -> str:
    module = ast.Module(body=nodes, type_ignores=[])
    dumped = ast.dump(module, annotate_fields=True, include_attributes=False)
    return _sha(dumped.encode("utf-8"))


def _bound_names(target: ast.expr) -> tuple[str, ...]:
    if isinstance(target, ast.Name):
        return (target.id,)
    if isinstance(target, (ast.Tuple, ast.List)):
        return tuple(name for item in target.elts for name in _bound_names(item))
    return ()


def _module_binding_names(tree: ast.Module) -> list[str]:
    names: list[str] = []
    for statement in tree.body:
        if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.append(statement.name)
        elif isinstance(statement, ast.Assign):
            for target in statement.targets:
                names.extend(_bound_names(target))
        elif isinstance(statement, ast.AnnAssign):
            names.extend(_bound_names(statement.target))
        elif isinstance(statement, ast.Import):
            names.extend(alias.asname or alias.name.partition(".")[0]
                         for alias in statement.names)
        elif isinstance(statement, ast.ImportFrom) and statement.module != "__future__":
            if any(alias.name == "*" for alias in statement.names):
                _reject("NAMESPACE_STAR_IMPORT")
            names.extend(alias.asname or alias.name for alias in statement.names)
        elif hasattr(ast, "TypeAlias") and isinstance(statement, ast.TypeAlias):
            names.extend(_bound_names(statement.name))
    if (tree.body and isinstance(tree.body[0], ast.Expr)
            and isinstance(tree.body[0].value, ast.Constant)
            and type(tree.body[0].value.value) is str):
        names.append("__doc__")
    if len(names) != len(set(names)):
        _reject("NAMESPACE_DUPLICATE_BINDING")
    return names


def _local_binding_names(node: ast.AST) -> set[str]:
    names: set[str] = set()
    for child in ast.walk(node):
        if isinstance(child, ast.Name) and isinstance(child.ctx, (ast.Store, ast.Del)):
            names.add(child.id)
        elif isinstance(child, ast.arg):
            names.add(child.arg)
        elif isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            if child is not node:
                names.add(child.name)
        elif isinstance(child, ast.alias):
            names.add(child.asname or child.name.partition(".")[0])
        elif isinstance(child, ast.ExceptHandler) and child.name is not None:
            names.add(child.name)
        elif isinstance(child, ast.MatchAs) and child.name is not None:
            names.add(child.name)
        elif isinstance(child, ast.MatchStar) and child.name is not None:
            names.add(child.name)
        elif isinstance(child, ast.MatchMapping) and child.rest is not None:
            names.add(child.rest)
    return names


class _LocalInventory(ast.NodeVisitor):
    """Lexical bindings with Python 3.13 inlined-comprehension handling."""

    def __init__(self) -> None:
        self.locals: set[str] = set()
        self.comprehension_targets: set[str] = set()
        self.globals: set[str] = set()
        self.nonlocals: set[str] = set()

    def add_arguments(self, arguments: ast.arguments) -> None:
        for argument in (*arguments.posonlyargs, *arguments.args, *arguments.kwonlyargs):
            self.locals.add(argument.arg)
        if arguments.vararg is not None:
            self.locals.add(arguments.vararg.arg)
        if arguments.kwarg is not None:
            self.locals.add(arguments.kwarg.arg)

    def collect_function(self, node: ast.FunctionDef | ast.AsyncFunctionDef) -> None:
        self.add_arguments(node.args)
        for statement in node.body:
            self.visit(statement)

    def collect_lambda(self, node: ast.Lambda) -> None:
        self.add_arguments(node.args)
        self.visit(node.body)

    def collect_class(self, node: ast.ClassDef) -> None:
        for statement in node.body:
            self.visit(statement)

    def visit_Name(self, node: ast.Name) -> None:
        if isinstance(node.ctx, (ast.Store, ast.Del)):
            self.locals.add(node.id)

    def visit_Global(self, node: ast.Global) -> None:
        self.globals.update(node.names)

    def visit_Nonlocal(self, node: ast.Nonlocal) -> None:
        self.nonlocals.update(node.names)

    def visit_Import(self, node: ast.Import) -> None:
        self.locals.update(alias.asname or alias.name.partition(".")[0]
                           for alias in node.names)

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
        self.locals.update(alias.asname or alias.name for alias in node.names
                           if alias.name != "*")

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self.locals.add(node.name)
        for expression in (*node.decorator_list, *node.args.defaults,
                           *(value for value in node.args.kw_defaults if value is not None),
                           *(arg.annotation for arg in
                             (*node.args.posonlyargs, *node.args.args, *node.args.kwonlyargs)
                             if arg.annotation is not None),
                           *((node.returns,) if node.returns is not None else ())):
            self.visit(expression)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self.visit_FunctionDef(node)

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        self.locals.add(node.name)
        for expression in (*node.decorator_list, *node.bases,
                           *(keyword.value for keyword in node.keywords)):
            self.visit(expression)

    def visit_Lambda(self, node: ast.Lambda) -> None:
        for expression in (*node.args.defaults,
                           *(value for value in node.args.kw_defaults if value is not None)):
            self.visit(expression)

    def visit_ExceptHandler(self, node: ast.ExceptHandler) -> None:
        if node.name is not None:
            self.locals.add(node.name)
        if node.type is not None:
            self.visit(node.type)
        for statement in node.body:
            self.visit(statement)

    def visit_MatchAs(self, node: ast.MatchAs) -> None:
        if node.name is not None:
            self.locals.add(node.name)
        if node.pattern is not None:
            self.visit(node.pattern)

    def visit_MatchStar(self, node: ast.MatchStar) -> None:
        if node.name is not None:
            self.locals.add(node.name)

    def visit_MatchMapping(self, node: ast.MatchMapping) -> None:
        if node.rest is not None:
            self.locals.add(node.rest)
        for pattern in node.patterns:
            self.visit(pattern)

    def _visit_comprehension(self, node: ast.AST) -> None:
        generators = node.generators
        for generator in generators:
            self.comprehension_targets.update(_bound_names(generator.target))
        if generators:
            self.visit(generators[0].iter)
        for index, generator in enumerate(generators):
            if index:
                self.visit(generator.iter)
            for condition in generator.ifs:
                self.visit(condition)
        if isinstance(node, ast.DictComp):
            self.visit(node.key)
            self.visit(node.value)
        else:
            self.visit(node.elt)

    def visit_ListComp(self, node: ast.ListComp) -> None:
        self._visit_comprehension(node)

    def visit_SetComp(self, node: ast.SetComp) -> None:
        self._visit_comprehension(node)

    def visit_DictComp(self, node: ast.DictComp) -> None:
        self._visit_comprehension(node)

    def visit_GeneratorExp(self, node: ast.GeneratorExp) -> None:
        self._visit_comprehension(node)


def _local_inventory(node: ast.AST, kind: str) -> _LocalInventory:
    inventory = _LocalInventory()
    if kind == "function" and isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
        inventory.collect_function(node)
    elif kind == "lambda" and isinstance(node, ast.Lambda):
        inventory.collect_lambda(node)
    elif kind == "class" and isinstance(node, ast.ClassDef):
        inventory.collect_class(node)
    inventory.locals.difference_update(inventory.globals | inventory.nonlocals)
    return inventory


class _NamespaceTransformer(ast.NodeTransformer):
    """Scope-aware global renamer adapted from the pinned prior extractor."""

    def __init__(self, label: str, mapping: dict[str, str], root: symtable.SymbolTable):
        self.mapping = mapping
        self.scopes = [root]
        self.frames = [{"kind": "module", "locals": set(), "globals": set(),
                        "nonlocals": set()}]
        self.used_child_scopes: dict[int, set[int]] = {}
        self.child_scope_cache: dict[int, tuple[symtable.SymbolTable, ...]] = {}

    @property
    def scope(self) -> symtable.SymbolTable:
        return self.scopes[-1]

    def _find_child_scope(self, names: tuple[str, ...], lineno: int,
                          scope_type: str, *, required: bool) -> symtable.SymbolTable | None:
        children = self.child_scope_cache.setdefault(id(self.scope),
                                                     tuple(self.scope.get_children()))
        candidates = [child for child in children if child.get_name() in names
                      and child.get_lineno() == lineno and child.get_type() == scope_type]
        used = self.used_child_scopes.setdefault(id(self.scope), set())
        matches = [child for child in candidates if id(child) not in used]
        if not matches and required:
            _reject("NAMESPACE_SCOPE_" + names[0])
        if not matches:
            return None
        child = matches[0]
        used.add(id(child))
        return child

    def _is_module_binding(self, name: str) -> bool:
        return self.frames[-1]["kind"] == "module" and name in self.mapping

    def _is_namespaced_global(self, name: str) -> bool:
        if name not in self.mapping:
            return False
        current = self.frames[-1]
        if current["kind"] == "module" or name in current["globals"]:
            return True
        if name in current["nonlocals"]:
            return False
        if any(name in frame for frame in self._comprehension_frames):
            return False
        if name in current["locals"]:
            return False
        for parent in reversed(self.frames[:-1]):
            if parent["kind"] == "module":
                return True
            if parent["kind"] == "class":
                continue
            if name in parent["globals"]:
                return True
            if name in parent["nonlocals"] or name in parent["locals"]:
                return False
        return True

    @property
    def _comprehension_frames(self) -> list[set[str]]:
        return [frame["locals"] for frame in self.frames
                if frame["kind"] == "comprehension"]

    def visit_Name(self, node: ast.Name) -> ast.AST:
        if self._is_module_binding(node.id) or self._is_namespaced_global(node.id):
            node.id = self.mapping[node.id]
        return node

    def visit_Global(self, node: ast.Global) -> ast.AST:
        node.names = [self.mapping.get(name, name) for name in node.names]
        return node

    def visit_FunctionDef(self, node: ast.FunctionDef) -> ast.AST:
        child = self._find_child_scope((node.name,), node.lineno, "function", required=True)
        inventory = _local_inventory(node, "function")
        if self._is_module_binding(node.name):
            node.name = self.mapping[node.name]
        node.decorator_list = [self.visit(item) for item in node.decorator_list]
        node.args = self.visit(node.args)
        if node.returns is not None:
            node.returns = self.visit(node.returns)
        self.scopes.append(child)
        self.frames.append({"kind": "function", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        node.body = [self.visit(item) for item in node.body]
        self.frames.pop()
        self.scopes.pop()
        return node

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> ast.AST:
        return self.visit_FunctionDef(node)

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.AST:
        child = self._find_child_scope((node.name,), node.lineno, "class", required=True)
        inventory = _local_inventory(node, "class")
        if self._is_module_binding(node.name):
            node.name = self.mapping[node.name]
        node.decorator_list = [self.visit(item) for item in node.decorator_list]
        node.bases = [self.visit(item) for item in node.bases]
        node.keywords = [self.visit(item) for item in node.keywords]
        self.scopes.append(child)
        self.frames.append({"kind": "class", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        node.body = [self.visit(item) for item in node.body]
        self.frames.pop()
        self.scopes.pop()
        return node

    def visit_Import(self, node: ast.Import) -> ast.AST:
        if len(self.scopes) == 1:
            node.names = [ast.alias(name=alias.name,
                                    asname=self.mapping[alias.asname or alias.name.partition(".")[0]])
                          for alias in node.names]
        return node

    def visit_ImportFrom(self, node: ast.ImportFrom) -> ast.AST:
        if len(self.scopes) == 1 and node.module != "__future__":
            node.names = [ast.alias(name=alias.name,
                                    asname=self.mapping[alias.asname or alias.name])
                          for alias in node.names]
        return node

    def visit_Lambda(self, node: ast.Lambda) -> ast.AST:
        node.args = self.visit(node.args)
        child = self._find_child_scope(("lambda", "<lambda>"), node.lineno,
                                       "function", required=True)
        inventory = _local_inventory(node, "lambda")
        self.scopes.append(child)
        self.frames.append({"kind": "lambda", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        node.body = self.visit(node.body)
        self.frames.pop()
        self.scopes.pop()
        return node

    def _visit_comprehension(self, node: ast.AST, scope_name: str) -> ast.AST:
        generators = node.generators
        if generators:
            generators[0].iter = self.visit(generators[0].iter)
        child = self._find_child_scope((scope_name,), node.lineno,
                                       "function", required=False)
        if child is not None:
            self.scopes.append(child)
        frame = {"kind": "comprehension", "locals": set(),
                 "globals": set(), "nonlocals": set()}
        self.frames.append(frame)
        for index, generator in enumerate(generators):
            if index:
                generator.iter = self.visit(generator.iter)
            frame["locals"].update(_bound_names(generator.target))
            generator.target = self.visit(generator.target)
            generator.ifs = [self.visit(item) for item in generator.ifs]
        if isinstance(node, ast.DictComp):
            node.key = self.visit(node.key)
            node.value = self.visit(node.value)
        else:
            node.elt = self.visit(node.elt)
        self.frames.pop()
        if child is not None:
            self.scopes.pop()
        return node

    def visit_ListComp(self, node: ast.ListComp) -> ast.AST:
        return self._visit_comprehension(node, "listcomp")

    def visit_SetComp(self, node: ast.SetComp) -> ast.AST:
        return self._visit_comprehension(node, "setcomp")

    def visit_DictComp(self, node: ast.DictComp) -> ast.AST:
        return self._visit_comprehension(node, "dictcomp")

    def visit_GeneratorExp(self, node: ast.GeneratorExp) -> ast.AST:
        return self._visit_comprehension(node, "genexpr")


class _ReverseNamespace(ast.NodeTransformer):
    def __init__(self, mapping: dict[str, str]) -> None:
        self.mapping = mapping

    def visit_Name(self, node: ast.Name) -> ast.AST:
        node.id = self.mapping.get(node.id, node.id)
        return node

    def visit_Global(self, node: ast.Global) -> ast.AST:
        node.names = [self.mapping.get(name, name) for name in node.names]
        return node

    def visit_FunctionDef(self, node: ast.FunctionDef) -> ast.AST:
        node.name = self.mapping.get(node.name, node.name)
        return self.generic_visit(node)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> ast.AST:
        node.name = self.mapping.get(node.name, node.name)
        return self.generic_visit(node)

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.AST:
        node.name = self.mapping.get(node.name, node.name)
        return self.generic_visit(node)

    def visit_alias(self, node: ast.alias) -> ast.AST:
        if node.asname is not None:
            node.asname = self.mapping.get(node.asname, node.asname)
        return node


class _NormalizeImplicitImportAliases(ast.NodeTransformer):
    """Use explicit equivalent bindings when comparing an inverse namespace AST."""

    def visit_Import(self, node: ast.Import) -> ast.AST:
        for alias in node.names:
            if alias.asname is None:
                alias.asname = alias.name.partition(".")[0]
        return node

    def visit_ImportFrom(self, node: ast.ImportFrom) -> ast.AST:
        if node.module != "__future__":
            for alias in node.names:
                if alias.asname is None:
                    alias.asname = alias.name
        return node


def _namespace_module(label: str, raw: bytes, prefix: str):
    if (type(label) is not str or re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", label) is None
            or type(prefix) is not str
            or re.fullmatch(r"[A-Z][A-Z0-9_]{0,15}_", prefix) is None):
        _reject("NAMESPACE_LABEL")
    if type(raw) is not bytes or len(raw) > MAX_SOURCE_BYTES:
        _reject("NAMESPACE_SOURCE_BYTES")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeError:
        _reject("NAMESPACE_SOURCE_UTF8")
    try:
        tree = ast.parse(text, filename=label, mode="exec", type_comments=True)
        root = symtable.symtable(text, label, "exec")
    except (SyntaxError, ValueError, OverflowError, RecursionError):
        _reject("NAMESPACE_PARSE_" + label)
    has_future_annotations = any(
        isinstance(statement, ast.ImportFrom) and statement.module == "__future__"
        and any(alias.name == "annotations" for alias in statement.names)
        for statement in tree.body)
    has_annotations = any(
        (isinstance(node, ast.arg) and node.annotation is not None)
        or (isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
            and node.returns is not None)
        or (isinstance(node, ast.AnnAssign) and node.annotation is not None)
        for node in ast.walk(tree))
    if has_annotations and not has_future_annotations:
        _reject("NAMESPACE_FUTURE_ANNOTATION_COMPOSITION_" + label)
    safety = _KernelSafety()
    safety.visit(tree)
    allowed_imports = frozenset({
        "__future__", "base64", "collections", "hashlib", "heapq", "json",
        "re", "types",
    })
    if safety.errors or any(name not in allowed_imports for name in safety.imports):
        _reject("NAMESPACE_SOURCE_POLICY_" + label)
    banned_path_text = re.compile(r"(?:[A-Za-z]:\\|/Users/|/home/|/tmp/)")
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and type(node.value) is str \
           and banned_path_text.search(node.value):
            _reject("NAMESPACE_PATH_LITERAL_" + label)
    names = _module_binding_names(tree)
    bound_names = set(names)
    builtin_names = set(vars(builtins))
    pending_scopes = [(root, True)]
    while pending_scopes:
        scope, is_root = pending_scopes.pop()
        for symbol in scope.get_symbols():
            if symbol.is_global() and symbol.is_referenced() \
               and symbol.get_name() not in bound_names \
               and symbol.get_name() not in builtin_names:
                _reject("NAMESPACE_FREE_GLOBAL_" + label)
            if not is_root and symbol.is_global() and symbol.is_assigned():
                _reject("NAMESPACE_GLOBAL_MUTATION_" + label)
        pending_scopes.extend((child, False) for child in scope.get_children())
    mapping = {name: prefix + name for name in names}
    reverse = {value: key for key, value in mapping.items()}
    if len(reverse) != len(mapping) or set(reverse) & set(names):
        _reject("NAMESPACE_CAPTURE_" + label)
    generated = set(mapping.values())
    for statement in tree.body:
        collision = _local_binding_names(statement) & generated
        if collision:
            _reject("NAMESPACE_LOCAL_CAPTURE_" + label)
    transformed = []
    for original in tree.body:
        node = copy.deepcopy(original)
        transformed.append(_NamespaceTransformer(label, mapping, root).visit(node))
    transformed_module = ast.Module(body=transformed, type_ignores=tree.type_ignores)
    ast.fix_missing_locations(transformed_module)
    restored = copy.deepcopy(transformed_module)
    restored = _ReverseNamespace(reverse).visit(restored)
    ast.fix_missing_locations(restored)
    normalized_original = _NormalizeImplicitImportAliases().visit(copy.deepcopy(tree))
    if ast.dump(restored, annotate_fields=True, include_attributes=False) != \
       ast.dump(normalized_original, annotate_fields=True, include_attributes=False):
        _reject("NAMESPACE_INVERSE_AST_" + label)
    return transformed_module, mapping, _ast_sha(tree.body), _ast_sha(transformed)


def _proof_nodes(tree: ast.Module) -> dict[str, list[ast.stmt]]:
    inventory = _top_level_nodes(tree)
    selected = {}
    for name in (*ORACLE_FUNCTIONS, *ORACLE_CONSTANTS):
        found = inventory.get(name, [])
        if len(found) != 1:
            _reject("ORACLE_SYMBOL_" + name)
        selected[name] = found
    return selected


class _KernelSafety(ast.NodeVisitor):
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.imports: list[str] = []
        self.functions: set[str] = set()

    def visit_Import(self, node: ast.Import) -> None:
        for alias in node.names:
            self.imports.append(alias.name)
            if alias.name.split(".", 1)[0] in FORBIDDEN_IMPORTS:
                self.errors.append("IMPORT_" + alias.name)

    def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
        module = node.module or ""
        self.imports.append(module)
        if module.split(".", 1)[0] in FORBIDDEN_IMPORTS:
            self.errors.append("IMPORT_" + module)

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self.functions.add(node.name)
        self.generic_visit(node)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self.errors.append("ASYNC_FUNCTION")

    def visit_Call(self, node: ast.Call) -> None:
        if isinstance(node.func, ast.Name) and node.func.id in FORBIDDEN_CALLS:
            self.errors.append("CALL_" + node.func.id)
        self.generic_visit(node)


def _validate_kernel_source(raw: bytes) -> tuple[ast.Module, _KernelSafety]:
    tree = _parse_module("full_d_kernel.py", raw)
    safety = _KernelSafety()
    safety.visit(tree)
    if safety.errors or "validate_full_d_v2" not in safety.functions:
        _reject("KERNEL_SOURCE_POLICY")
    expected_local_imports = {
        "source.context_codec", "source.observation_v2_kernel", "source.prior_kernel",
    }
    local_imports = {name for name in safety.imports if name.startswith("source.")}
    if local_imports != expected_local_imports:
        _reject("KERNEL_DEPENDENCY_IMPORTS")
    banned_path_text = re.compile(r"(?:[A-Za-z]:\\|/Users/|/home/|/tmp/)")
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and type(node.value) is str:
            if banned_path_text.search(node.value):
                _reject("KERNEL_PATH_LITERAL")
    return tree, safety


def _module_docstring(tree: ast.Module) -> ast.stmt | None:
    if (tree.body and isinstance(tree.body[0], ast.Expr)
            and isinstance(tree.body[0].value, ast.Constant)
            and type(tree.body[0].value.value) is str):
        return copy.deepcopy(tree.body[0])
    return None


def _is_future(statement: ast.stmt) -> bool:
    return isinstance(statement, ast.ImportFrom) and statement.module == "__future__"


def _namespace_dependency(label: str, raw: bytes, prefix: str):
    module, mapping, original_ast_sha, transformed_ast_sha = _namespace_module(
        label, raw, prefix)
    original_tree = _parse_module(label, raw)
    normalized_original = _NormalizeImplicitImportAliases().visit(
        copy.deepcopy(original_tree))
    normalized_original_ast_sha = _ast_sha(normalized_original.body)
    body = []
    for statement in module.body:
        if _is_future(statement):
            continue
        if (statement is module.body[0] and isinstance(statement, ast.Expr)
                and isinstance(statement.value, ast.Constant)
                and type(statement.value.value) is str):
            body.append(ast.Assign(
                targets=[ast.Name(id=mapping["__doc__"], ctx=ast.Store())],
                value=copy.deepcopy(statement.value)))
            continue
        body.append(statement)
    return (body, mapping, original_ast_sha, normalized_original_ast_sha,
            transformed_ast_sha)


def _adapter_imports_and_body(adapter_tree: ast.Module, namespaces: dict[str, dict[str, str]]):
    module_map = {
        "source.context_codec": "context_codec.py",
        "source.observation_v2_kernel": "observation_v2_kernel.py",
        "source.prior_kernel": "prior_kernel.py",
    }
    aliases: list[ast.stmt] = []
    standard_imports: list[ast.stmt] = []
    adapter_body: list[ast.stmt] = []
    seen_modules: set[str] = set()
    for index, statement in enumerate(adapter_tree.body):
        if _is_future(statement):
            continue
        if isinstance(statement, ast.ImportFrom) and statement.module in module_map:
            source_name = module_map[statement.module]
            seen_modules.add(statement.module)
            namespace = namespaces[source_name]
            for imported in statement.names:
                local_name = imported.asname or imported.name
                source_bound = imported.name
                target_name = namespace.get(source_bound)
                if target_name is None:
                    _reject("ADAPTER_DEPENDENCY_SYMBOL_" + source_bound)
                aliases.append(ast.Assign(
                    targets=[ast.Name(id=local_name, ctx=ast.Store())],
                    value=ast.Name(id=target_name, ctx=ast.Load())))
            continue
        if isinstance(statement, (ast.Import, ast.ImportFrom)):
            standard_imports.append(copy.deepcopy(statement))
            continue
        if index == 0 and isinstance(statement, ast.Expr) \
           and isinstance(statement.value, ast.Constant) \
           and type(statement.value.value) is str:
            continue
        adapter_body.append(copy.deepcopy(statement))
    if seen_modules != set(module_map):
        _reject("ADAPTER_DEPENDENCY_IMPORT_SET")
    return standard_imports, aliases, adapter_body


def _validate_emitted_source(raw: bytes) -> tuple[ast.Module, _KernelSafety]:
    tree = _parse_module("emitted_full_d_kernel.py", raw)
    safety = _KernelSafety()
    safety.visit(tree)
    allowed_imports = frozenset({
        "__future__", "base64", "collections", "hashlib", "heapq", "json",
        "re", "types",
    })
    if safety.errors or "validate_full_d_v2" not in safety.functions:
        _reject("EMITTED_SOURCE_POLICY")
    if any(name not in allowed_imports for name in safety.imports):
        _reject("EMITTED_IMPORT_POLICY")
    banned_path_text = re.compile(r"(?:[A-Za-z]:\\|/Users/|/home/|/tmp/)")
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and type(node.value) is str:
            if banned_path_text.search(node.value):
                _reject("EMITTED_PATH_LITERAL")
    return tree, safety


def _compose_kernel(source_bundle: dict[str, bytes], adapter_source: bytes):
    adapter_tree, _adapter_safety = _validate_kernel_source(adapter_source)
    dependency_specs = (
        ("prior_kernel.py", "PK_"),
        ("context_codec.py", "CC_"),
        ("observation_v2_kernel.py", "OV2_"),
    )
    dependency_body: list[ast.stmt] = []
    namespaces: dict[str, dict[str, str]] = {}
    namespace_proof = {}
    for label, prefix in dependency_specs:
        (body, mapping, raw_original_ast_sha, normalized_original_ast_sha,
         transformed_ast_sha) = _namespace_dependency(label, source_bundle[label], prefix)
        namespaces[label] = mapping
        dependency_body.extend(body)
        namespace_proof[label] = {
            "namespace": prefix,
            "scopeModel": "SYMTABLE_WITH_PYTHON_3_13_INLINE_COMPREHENSION_FRAMES_AND_LAMBDA_SCOPE_NAMES",
            "namespaceCaptureGuard": "MODULE_BINDINGS_LOCAL_BINDINGS_AND_PREFIX_COLLISIONS",
            "unknownGlobalGuard": "REJECT_UNBOUND_NONBUILTIN_GLOBALS",
            "rawOriginalAstSha256": raw_original_ast_sha,
            "inverseNormalizedOriginalAstSha256": normalized_original_ast_sha,
            "originalAstSha256": raw_original_ast_sha,
            "namespacedAstSha256": transformed_ast_sha,
            "inverseAstCheck": "PASS_WITH_EQUIVALENT_IMPORT_ALIAS_NORMALIZATION",
            "inverseNormalization": "Unaliased imports normalize to explicit same-bound-name aliases; future imports are unchanged.",
            "rename": [[old, new] for old, new in mapping.items()],
        }
    standard_imports, dependency_aliases, adapter_body = _adapter_imports_and_body(
        adapter_tree, namespaces)
    generated_doc = ast.Expr(value=ast.Constant(
        value="Generated self-contained full-D V2 pure semantic owner."))
    future_annotations = ast.ImportFrom(
        module="__future__", names=[ast.alias(name="annotations")], level=0)
    module = ast.Module(
        body=[generated_doc, future_annotations, *dependency_body,
              *standard_imports, *dependency_aliases, *adapter_body],
        type_ignores=[])
    ast.fix_missing_locations(module)
    try:
        emitted = (ast.unparse(module) + "\n").encode("utf-8")
    except (ValueError, TypeError, RecursionError):
        _reject("EMIT_UNPARSE")
    if len(emitted) > MAX_KERNEL_BYTES:
        _reject("EMITTED_KERNEL_LIMIT")
    emitted_tree, emitted_safety = _validate_emitted_source(emitted)
    namespace_proof["adapter"] = {
        "sourceSha256": _sha(adapter_source),
        "adapterAstSha256": _ast_sha(adapter_tree.body),
        "emittedAdapterAstSha256": _ast_sha(adapter_body),
        "dependencyAliases": [
            {"local": statement.targets[0].id,
             "namespaced": statement.value.id}
            for statement in dependency_aliases
        ],
    }
    return emitted, namespace_proof, emitted_tree, emitted_safety


def _validate_published_prior_proof(raw: bytes) -> dict[str, object]:
    try:
        value = json.loads(raw.decode("utf-8", errors="strict"))
    except (UnicodeError, json.JSONDecodeError, ValueError, TypeError):
        _reject("PRIOR_PROVENANCE_JSON")
    if type(value) is not dict or value.get("schema") != "tsj-prior-kernel-extraction-v1":
        _reject("PRIOR_PROVENANCE_SCHEMA")
    modules = value.get("modules")
    if type(modules) is not list or [row.get("label") for row in modules] != ["N", "P07"]:
        _reject("PRIOR_PROVENANCE_MODULES")
    expected = {
        "N": ("84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52",
              "9d74dab66480e099c62e899dd2005cbf3f9f646f45e12eca215282950bdeb635"),
        "P07": ("a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e",
                "f8b8ab425706fcee4aea81b38b892e54d657ff76ba084d62cd69490b9474d59d"),
    }
    for row in modules:
        if type(row) is not dict:
            _reject("PRIOR_PROVENANCE_ROW")
        source_sha, original_ast_sha = expected[row["label"]]
        if row.get("sourceSha256") != source_sha or row.get("originalAstSha256") != original_ast_sha:
            _reject("PRIOR_PROVENANCE_PIN_" + row["label"])
    if value.get("kernelSha256") != SOURCE_PINS["prior_kernel.py"][1]:
        _reject("PRIOR_PROVENANCE_KERNEL")
    return value


def generate_full_d_dependencies(*, source_bundle: dict[str, bytes],
                                 semantic_adapter_source: bytes) -> GenerationResult:
    """Compose pinned pure dependencies and the adapter into one pure module.

    The function consumes exact in-memory bytes, namespaces dependency globals
    with a scope-aware AST transform, verifies inverse AST restoration for each
    dependency, and returns self-contained source plus canonical provenance.
    It does not load source modules, access files, or execute emitted code.
    """
    if type(source_bundle) is not dict or set(source_bundle) != set(SOURCE_PINS):
        _reject("SOURCE_BUNDLE_KEYS")
    for name, (expected_bytes, expected_sha) in SOURCE_PINS.items():
        raw = source_bundle[name]
        if type(raw) is not bytes or len(raw) != expected_bytes or _sha(raw) != expected_sha:
            _reject("SOURCE_PIN_" + name)
    if type(semantic_adapter_source) is not bytes or not semantic_adapter_source:
        _reject("KERNEL_SOURCE_BYTES")
    if len(semantic_adapter_source) > MAX_KERNEL_BYTES:
        _reject("KERNEL_SOURCE_LIMIT")

    prior_proof = _validate_published_prior_proof(source_bundle["PROVENANCE_R1.json"])
    consumer_tree = _parse_module(
        "read_full_observation_component_v2.py",
        source_bundle["read_full_observation_component_v2.py"])
    selected = _proof_nodes(consumer_tree)
    selected_nodes = [selected[name][0]
                      for name in (*ORACLE_FUNCTIONS, *ORACLE_CONSTANTS)]
    kernel_tree, _template_safety = _validate_kernel_source(semantic_adapter_source)
    kernel_source_sha = _sha(semantic_adapter_source)
    adapter_ast_sha = _ast_sha(kernel_tree.body)
    original_ast_sha = _ast_sha(selected_nodes)
    consumer_ast_sha = _ast_sha(consumer_tree.body)

    prior_tree = _parse_module("prior_kernel.py", source_bundle["prior_kernel.py"])
    prior_names = _top_level_nodes(prior_tree)
    for name in ("stable_cohort_order", "validate_prior_run",
                 "validate_prior_terminal_failure", "result_tree_minimum_units",
                 "validate_operation"):
        if len(prior_names.get(name, [])) != 1:
            _reject("PRIOR_FACADE_" + name)
    context_tree = _parse_module("context_codec.py", source_bundle["context_codec.py"])
    context_names = _top_level_nodes(context_tree)
    for name in ("validate_context", "canonical_context", "context_sha256"):
        if len(context_names.get(name, [])) != 1:
            _reject("CONTEXT_FACADE_" + name)
    observation_tree = _parse_module(
        "observation_v2_kernel.py", source_bundle["observation_v2_kernel.py"])
    observation_names = _top_level_nodes(observation_tree)
    observation_class = observation_names.get("ObservationV2Kernel", [])
    if len(observation_class) != 1:
        _reject("OBSERVATION_CLASS")
    method_names = {node.name for node in observation_class[0].body
                    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))}
    if not {"validate_snapshot", "validate_head", "accounting",
            "validate_observation"}.issubset(method_names):
        _reject("OBSERVATION_METHODS")

    emitted_source, namespace_proof, emitted_tree, emitted_safety = _compose_kernel(
        source_bundle, semantic_adapter_source)
    emitted_sha = _sha(emitted_source)
    emitted_ast_sha = _ast_sha(emitted_tree.body)

    original_rows = []
    for name in (*ORACLE_FUNCTIONS, *ORACLE_CONSTANTS):
        node = selected[name][0]
        original_rows.append({
            "name": name,
            "kind": "function" if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) else "constant",
            "lineStart": node.lineno,
            "lineEnd": getattr(node, "end_lineno", node.lineno),
        })
    proof = {
        "schema": PROVENANCE_SCHEMA,
        "status": "SOURCE_GENERATED_NOT_PRODUCT_EXECUTION",
        "sourcePins": {
            name: {"bytes": expected_bytes, "sha256": expected_sha}
            for name, (expected_bytes, expected_sha) in SOURCE_PINS.items()
        },
        "priorExtraction": {
            "provenanceSha256": _sha(source_bundle["PROVENANCE_R1.json"]),
            "kernelSha256": prior_proof["kernelSha256"],
            "originalAstSha256": {
                row["label"]: row["originalAstSha256"]
                for row in prior_proof["modules"]
            },
            "facade": ["stable_cohort_order", "validate_prior_run",
                       "validate_prior_terminal_failure", "result_tree_minimum_units",
                       "validate_operation"],
        },
        "semanticOracle": {
            "consumerSourceSha256": SOURCE_PINS["read_full_observation_component_v2.py"][1],
            "consumerModuleAstSha256": consumer_ast_sha,
            "selectedClosureAstSha256": original_ast_sha,
            "selectedFunctions": list(ORACLE_FUNCTIONS),
            "selectedConstants": list(ORACLE_CONSTANTS),
            "sourceRows": original_rows,
            "portMode": "AST_ANCHORED_CURRENT_V2_ADAPTER",
        },
        "composedKernels": {
            "priorKernelSha256": SOURCE_PINS["prior_kernel.py"][1],
            "contextCodecSha256": SOURCE_PINS["context_codec.py"][1],
            "observationKernelSha256": SOURCE_PINS["observation_v2_kernel.py"][1],
            "namespacingMechanismReferenceSha256": SOURCE_PINS["extract_prior_kernel.py"][1],
            "namespaceProof": namespace_proof,
        },
        "adaptations": [
            "descriptor and old N-reader module authority replaced by explicit current context and pre-call RuntimeIdentity",
            "historical ten-key prior projected into schema-v2 twelve-key qualifiedPrior with inputContextSha256",
            "result schema, ordered keys, runtime identity, marker schema/keys and 656-byte bound use WIRE_R1",
            "current context physical grid is joined to every parsed P07 operation-1 branch snapshot",
            "operation-1 parser is statically bound; original PAD/group and export-copy reconciliation are mandatory",
            "old capture, cohort, arm, cleanup, parity, finite expectation and work equations are retained",
        ],
        "kernel": {
            "adapterTemplateBytes": len(semantic_adapter_source),
            "adapterTemplateSha256": kernel_source_sha,
            "adapterTemplateAstSha256": adapter_ast_sha,
            "emittedBytes": len(emitted_source),
            "emittedSha256": emitted_sha,
            "emittedAstSha256": emitted_ast_sha,
            "imports": sorted(emitted_safety.imports),
            "api": "validate_full_d_v2(*, context, runtime_identity, op1_raw, result_raw, marker_raw)",
        },
    }
    proof_raw = (json.dumps(proof, ensure_ascii=True, sort_keys=True,
                            separators=(",", ":")) + "\n").encode("ascii")
    return GenerationResult(emitted_source, proof_raw)


__all__ = ("GenerationError", "GenerationResult", "generate_full_d_dependencies")
