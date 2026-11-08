"""Generate the pinned, source-only prior validation kernel.

The public entry point accepts bytes that the caller has already obtained. It
does not open files, import either source module, or inspect normal-data inputs.
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
PINNED_N_SHA256 = "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
PINNED_P07_SHA256 = "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"
PROOF_SCHEMA = "tsj-prior-kernel-extraction-v1"

_PURE_STDLIB = frozenset({
    "base64", "collections", "hashlib", "heapq", "json", "math", "re",
})
_BUILTINS = frozenset(dir(builtins))
_FORBIDDEN_CALLS = frozenset({
    "__import__", "breakpoint", "compile", "eval", "exec", "input", "open",
    "print",
})
_FORBIDDEN_METHODS = frozenset({
    "dump", "fstat", "glob", "iterdir", "lstat", "mkdir", "open", "popen",
    "read", "read_bytes", "read_text", "rename", "replace", "rmdir", "run",
    "scandir", "stat", "system", "unlink", "walk", "write", "write_bytes",
    "write_text",
})
_ABSOLUTE_PATH = re.compile(
    r"^(?:[A-Za-z]:[\\/]|\\\\|/(?:Users|home|tmp|var|workspace|mnt)/)"
)


class ExtractionError(ValueError):
    """A deterministic, path-free extraction rejection."""


class ModuleInput(NamedTuple):
    label: str
    source: bytes
    entrypoints: tuple[str, ...]


class ExtractionResult(NamedTuple):
    kernel_source: bytes
    provenance_json: bytes


class _Binding(NamedTuple):
    name: str
    kind: str
    statement: ast.stmt
    alias: ast.alias | None


class _ModuleExtraction(NamedTuple):
    label: str
    source_sha256: str
    original_ast_sha256: str
    closure_ast_sha256: str
    closure: tuple[str, ...]
    closure_kinds: tuple[tuple[str, str], ...]
    rename: tuple[tuple[str, str], ...]
    nodes: tuple[ast.stmt, ...]
    future_annotations: bool
    has_annotations: bool


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _reject(message: str) -> None:
    raise ExtractionError(message)


def _validate_source_bytes(label: str, source: bytes) -> str:
    if type(source) is not bytes:
        _reject(f"{label} source must be bytes")
    if not source or len(source) > MAX_SOURCE_BYTES:
        _reject(f"{label} source size is outside the pinned limit")
    try:
        return source.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        _reject(f"{label} source is not strict UTF-8")


def _bound_names(target: ast.expr) -> tuple[str, ...]:
    if isinstance(target, ast.Name):
        return (target.id,)
    if isinstance(target, (ast.Tuple, ast.List)):
        return tuple(name for item in target.elts for name in _bound_names(item))
    return ()


def _is_pure_name_target(target: ast.expr) -> bool:
    if isinstance(target, ast.Name):
        return True
    if isinstance(target, (ast.Tuple, ast.List)):
        return bool(target.elts) and all(_is_pure_name_target(item) for item in target.elts)
    return False


def _top_level_bindings(tree: ast.Module) -> tuple[dict[str, _Binding], frozenset[str]]:
    bindings: dict[str, _Binding] = {}
    duplicates: set[str] = set()

    def add(binding: _Binding) -> None:
        if binding.name in bindings:
            duplicates.add(binding.name)
        else:
            bindings[binding.name] = binding

    for statement in tree.body:
        if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef)):
            add(_Binding(statement.name, "function", statement, None))
        elif isinstance(statement, ast.ClassDef):
            add(_Binding(statement.name, "class", statement, None))
        elif isinstance(statement, ast.Assign):
            for target in statement.targets:
                for name in _bound_names(target):
                    add(_Binding(name, "constant", statement, None))
        elif isinstance(statement, ast.AnnAssign):
            for name in _bound_names(statement.target):
                add(_Binding(name, "constant", statement, None))
        elif isinstance(statement, ast.Import):
            for alias in statement.names:
                bound = alias.asname or alias.name.partition(".")[0]
                add(_Binding(bound, "import", statement, alias))
        elif isinstance(statement, ast.ImportFrom) and statement.module != "__future__":
            for alias in statement.names:
                if alias.name == "*":
                    continue
                bound = alias.asname or alias.name
                add(_Binding(bound, "import", statement, alias))
    return bindings, frozenset(duplicates)


def _future_annotations(tree: ast.Module) -> bool:
    for statement in tree.body:
        if isinstance(statement, ast.ImportFrom) and statement.module == "__future__":
            names = {alias.name for alias in statement.names}
            if names != {"annotations"}:
                _reject("unsupported future import in selected source")
            return True
        if not isinstance(statement, (ast.Expr, ast.ImportFrom)):
            break
    return False


def _find_child_scope(parent: symtable.SymbolTable, node: ast.AST) -> symtable.SymbolTable:
    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
        name, scope_type = node.name, "function"
    elif isinstance(node, ast.ClassDef):
        name, scope_type = node.name, "class"
    else:
        _reject("unsupported scope node in dependency closure")
    matches = [child for child in parent.get_children()
               if child.get_name() == name and child.get_lineno() == node.lineno
               and child.get_type() == scope_type]
    if len(matches) != 1:
        _reject(f"cannot map source scope for {name}")
    return matches[0]


def _reject_global_mutation(scope: symtable.SymbolTable) -> None:
    pending = [scope]
    while pending:
        current = pending.pop()
        for symbol in current.get_symbols():
            if symbol.is_global() and symbol.is_assigned():
                _reject("global mutation in dependency closure")
        pending.extend(current.get_children())


def _header_expressions(node: ast.AST, *, include_annotations: bool) -> tuple[ast.AST, ...]:
    expressions: list[ast.AST] = []
    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
        expressions.extend(node.decorator_list)
        expressions.extend(node.args.defaults)
        expressions.extend(value for value in node.args.kw_defaults if value is not None)
        if include_annotations:
            expressions.extend(arg.annotation for arg in
                              (*node.args.posonlyargs, *node.args.args, *node.args.kwonlyargs)
                              if arg.annotation is not None)
            if node.args.vararg is not None and node.args.vararg.annotation is not None:
                expressions.append(node.args.vararg.annotation)
            if node.args.kwarg is not None and node.args.kwarg.annotation is not None:
                expressions.append(node.args.kwarg.annotation)
            if node.returns is not None:
                expressions.append(node.returns)
    elif isinstance(node, ast.ClassDef):
        expressions.extend(node.decorator_list)
        expressions.extend(node.bases)
        expressions.extend(keyword.value for keyword in node.keywords)
    return tuple(expressions)


def _is_pure_constant_expression(node: ast.AST) -> bool:
    if isinstance(node, ast.Constant):
        return True
    if isinstance(node, (ast.Tuple, ast.List, ast.Set)):
        return all(_is_pure_constant_expression(item) for item in node.elts)
    if isinstance(node, ast.Dict):
        return all(key is not None and _is_pure_constant_expression(key)
                   and _is_pure_constant_expression(value)
                   for key, value in zip(node.keys, node.values))
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub, ast.Invert)):
        return _is_pure_constant_expression(node.operand)
    if isinstance(node, ast.BinOp) and isinstance(node.op, (
            ast.Add, ast.Sub, ast.Mult, ast.FloorDiv, ast.Div, ast.Mod, ast.Pow,
            ast.LShift, ast.RShift, ast.BitOr, ast.BitAnd, ast.BitXor)):
        return (_is_pure_constant_expression(node.left)
                and _is_pure_constant_expression(node.right))
    if isinstance(node, ast.Call) and not node.keywords:
        if (isinstance(node.func, ast.Name) and node.func.id == "dict"
                and all(_is_pure_constant_expression(arg) for arg in node.args)):
            return True
        if (isinstance(node.func, ast.Attribute) and node.func.attr == "split"
                and isinstance(node.func.value, ast.Constant)
                and type(node.func.value.value) is str and not node.args):
            return True
        if (isinstance(node.func, ast.Attribute) and node.func.attr == "compile"
                and isinstance(node.func.value, ast.Name) and node.func.value.id == "re"
                and 1 <= len(node.args) <= 2
                and all(_is_pure_constant_expression(arg) for arg in node.args)):
            return True
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "dict":
        return (not node.args and all(keyword.arg is not None
                                      and _is_pure_constant_expression(keyword.value)
                                      for keyword in node.keywords))
    return False


def _constant_value(statement: ast.stmt) -> ast.expr:
    if isinstance(statement, ast.Assign):
        if not statement.targets or not all(_is_pure_name_target(target)
                                            for target in statement.targets):
            _reject("constant dependency has a non-name assignment target")
        value = statement.value
    elif (isinstance(statement, ast.AnnAssign) and statement.value is not None
          and _is_pure_name_target(statement.target)):
        value = statement.value
    else:
        _reject("constant dependency has no supported value")
    if not _is_pure_constant_expression(value):
        _reject("constant dependency is not a pure literal expression")
    return value


def _safe_import(binding: _Binding) -> None:
    statement = binding.statement
    if isinstance(statement, ast.Import):
        module = binding.alias.name.split(".", 1)[0]
    elif isinstance(statement, ast.ImportFrom):
        if statement.level or statement.module is None:
            _reject(f"relative import in dependency closure: {binding.name}")
        module = statement.module.split(".", 1)[0]
    else:
        _reject("invalid import binding")
    if module not in _PURE_STDLIB:
        _reject(f"non-pure or non-stdlib import in dependency closure: {module}")


def _selected_names_and_nodes(
    label: str,
    source: bytes,
    entrypoints: tuple[str, ...],
) -> _ModuleExtraction:
    text = _validate_source_bytes(label, source)
    try:
        tree = ast.parse(text, filename=f"<{label}-source>", mode="exec", type_comments=True)
        module_symbols = symtable.symtable(text, f"<{label}-source>", "exec")
    except (SyntaxError, ValueError, OverflowError) as exc:
        _reject(f"{label} source parse failed: {type(exc).__name__}")
    bindings, duplicates = _top_level_bindings(tree)
    future_annotations = _future_annotations(tree)
    selected: set[str] = set()
    pending = list(entrypoints)
    while pending:
        name = pending.pop(0)
        if name in selected:
            continue
        if name in duplicates:
            _reject(f"ambiguous module binding in {label}: {name}")
        binding = bindings.get(name)
        if binding is None:
            if name in _BUILTINS:
                continue
            _reject(f"unexpected free name in {label} closure: {name}")
        selected.add(name)
        if binding.kind == "import":
            _safe_import(binding)
            references: set[str] = set()
        elif binding.kind in ("function", "class"):
            node = binding.statement
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.decorator_list:
                _reject(f"decorator execution in dependency closure: {name}")
            if isinstance(node, ast.ClassDef) and node.decorator_list:
                _reject(f"decorator execution in dependency closure: {name}")
            header = _header_expressions(node, include_annotations=not future_annotations)
            if any(isinstance(child, ast.Call) for expression in header
                   for child in ast.walk(expression)):
                _reject(f"call in executable definition header: {name}")
            _reject_global_mutation(_find_child_scope(module_symbols, node))
            collector = _GlobalReferenceCollector(future_annotations)
            collector.visit(node)
            references = collector.references
            if binding.kind == "class":
                for item in node.body:
                    if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Pass)):
                        continue
                    if (isinstance(item, ast.Expr) and isinstance(item.value, ast.Constant)
                            and type(item.value.value) is str):
                        continue
                    if isinstance(item, (ast.Assign, ast.AnnAssign)):
                        _constant_value(item)
                        continue
                    _reject(f"executable class body statement in dependency closure: {name}")
        elif binding.kind == "constant":
            value = _constant_value(binding.statement)
            references = {child.id for child in ast.walk(value)
                          if isinstance(child, ast.Name) and isinstance(child.ctx, ast.Load)}
            references.update(other.name for other in bindings.values()
                              if other.statement is binding.statement)
            if isinstance(binding.statement, ast.AnnAssign) and not future_annotations:
                references.update(child.id for child in ast.walk(binding.statement.annotation)
                                  if isinstance(child, ast.Name)
                                  and isinstance(child.ctx, ast.Load))
        else:
            _reject(f"unsupported module binding in {label}: {name}")
        for reference in sorted(references):
            if reference in bindings:
                pending.append(reference)
            elif reference not in _BUILTINS:
                _reject(f"unexpected free name in {label} closure: {reference}")

    # Source order is stable and preserves the order of module-level initializers.
    ordered: list[str] = []
    for statement in tree.body:
        for name, binding in bindings.items():
            if name in selected and binding.statement is statement and name not in ordered:
                ordered.append(name)
    if set(ordered) != selected:
        _reject(f"incomplete binding inventory in {label}")

    # Keep only used aliases from import statements. Other selected statements
    # remain original AST nodes until the deterministic namespace pass.
    nodes: list[ast.stmt] = []
    for statement in tree.body:
        names_here = [name for name in ordered if bindings[name].statement is statement]
        if not names_here:
            continue
        if isinstance(statement, ast.Import):
            aliases = [bindings[name].alias for name in names_here]
            nodes.append(ast.Import(names=[alias for alias in aliases if alias is not None]))
        elif isinstance(statement, ast.ImportFrom):
            aliases = [bindings[name].alias for name in names_here]
            nodes.append(ast.ImportFrom(module=statement.module, names=[
                alias for alias in aliases if alias is not None], level=statement.level))
        else:
            if isinstance(statement, (ast.Assign, ast.AnnAssign)):
                _constant_value(statement)
            nodes.append(statement)
    ast.fix_missing_locations(ast.Module(body=nodes, type_ignores=[]))
    _validate_selected_ast(label, nodes)
    rename = tuple((name, f"{label}_{name}") for name in ordered)
    original_dump = ast.dump(ast.Module(body=nodes, type_ignores=[]), include_attributes=False)
    full_dump = ast.dump(tree, include_attributes=False)
    return _ModuleExtraction(
        label=label,
        source_sha256=_sha256(source),
        original_ast_sha256=_sha256(full_dump.encode("utf-8")),
        closure_ast_sha256=_sha256(original_dump.encode("utf-8")),
        closure=tuple(ordered),
        closure_kinds=tuple((name, bindings[name].kind) for name in ordered),
        rename=rename,
        nodes=tuple(nodes),
        future_annotations=future_annotations,
        has_annotations=any(_contains_annotation(node) for node in nodes),
    )


def _contains_annotation(node: ast.AST) -> bool:
    return any(isinstance(child, (ast.AnnAssign, ast.arg)) and
               (not isinstance(child, ast.arg) or child.annotation is not None)
               for child in ast.walk(node)) or any(
                   isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef))
                   and child.returns is not None for child in ast.walk(node))


def _validate_selected_ast(label: str, nodes: list[ast.stmt]) -> None:
    for node in nodes:
        for child in ast.walk(node):
            if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                if child.decorator_list:
                    _reject(f"decorator execution in {label} closure: {child.name}")
                if any(isinstance(expression_child, ast.Call)
                       for expression in _header_expressions(child, include_annotations=True)
                       for expression_child in ast.walk(expression)):
                    _reject(f"call in executable definition header in {label} closure")
            if isinstance(child, ast.Call):
                if isinstance(child.func, ast.Name) and child.func.id in _FORBIDDEN_CALLS:
                    _reject(f"dynamic execution or I/O call in {label} closure: {child.func.id}")
                if isinstance(child.func, ast.Attribute) and child.func.attr in _FORBIDDEN_METHODS:
                    _reject(f"file or process operation in {label} closure: {child.func.attr}")
                if (isinstance(child.func, ast.Attribute) and child.func.attr in ("load", "dump")
                        and isinstance(child.func.value, ast.Name)
                        and child.func.value.id == "json"):
                    _reject(f"file JSON operation in {label} closure: {child.func.attr}")
            if isinstance(child, (ast.Import, ast.ImportFrom)):
                if isinstance(child, ast.Import):
                    imported = [alias.name.split(".", 1)[0] for alias in child.names]
                elif child.module:
                    imported = [child.module.split(".", 1)[0]]
                else:
                    imported = []
                if any(module not in _PURE_STDLIB and module != "__future__"
                       for module in imported):
                    _reject(f"non-pure or non-stdlib import in {label} closure")
            if isinstance(child, ast.Constant) and type(child.value) is str:
                if _ABSOLUTE_PATH.match(child.value):
                    _reject(f"absolute path string in {label} closure")


class _LocalInventory(ast.NodeVisitor):
    """Track lexical bindings without treating PEP 709 targets as outer locals."""

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
        for expression in _header_expressions(node, include_annotations=True):
            self.visit(expression)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self.visit_FunctionDef(node)  # type: ignore[arg-type]

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        self.locals.add(node.name)
        for expression in _header_expressions(node, include_annotations=True):
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
        generators = node.generators  # type: ignore[attr-defined]
        for generator in generators:
            self.comprehension_targets.update(_bound_names(generator.target))
        if generators:
            # The first iterator is evaluated in the containing scope.
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
            self.visit(node.elt)  # type: ignore[attr-defined]

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


class _GlobalReferenceCollector(ast.NodeVisitor):
    """Resolve loaded names through lexical scopes, including inlined comps."""

    def __init__(self, future_annotations: bool) -> None:
        self.future_annotations = future_annotations
        self.frames = [{"kind": "module", "locals": set(), "globals": set(),
                        "nonlocals": set()}]
        self.references: set[str] = set()

    def _is_module_global(self, name: str) -> bool:
        current = self.frames[-1]
        if current["kind"] == "module":
            return True
        if name in current["globals"]:
            return True
        if name in current["nonlocals"] or name in current["locals"]:
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

    def visit_Name(self, node: ast.Name) -> None:
        if isinstance(node.ctx, ast.Load) and self._is_module_global(node.id):
            self.references.add(node.id)

    def visit_Global(self, node: ast.Global) -> None:
        return None

    def visit_Nonlocal(self, node: ast.Nonlocal) -> None:
        return None

    def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
        self.visit(node.target)
        if node.value is not None:
            self.visit(node.value)
        if not self.future_annotations:
            self.visit(node.annotation)

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        for expression in _header_expressions(
                node, include_annotations=not self.future_annotations):
            self.visit(expression)
        inventory = _local_inventory(node, "function")
        self.frames.append({"kind": "function", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        for statement in node.body:
            self.visit(statement)
        self.frames.pop()

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self.visit_FunctionDef(node)  # type: ignore[arg-type]

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        for expression in _header_expressions(
                node, include_annotations=not self.future_annotations):
            self.visit(expression)
        inventory = _local_inventory(node, "class")
        self.frames.append({"kind": "class", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        for statement in node.body:
            self.visit(statement)
        self.frames.pop()

    def visit_Lambda(self, node: ast.Lambda) -> None:
        for expression in (*node.args.defaults,
                           *(value for value in node.args.kw_defaults if value is not None)):
            self.visit(expression)
        inventory = _local_inventory(node, "lambda")
        self.frames.append({"kind": "lambda", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        self.visit(node.body)
        self.frames.pop()

    def _visit_comprehension(self, node: ast.AST) -> None:
        generators = node.generators  # type: ignore[attr-defined]
        if generators:
            # The first iterator uses the containing scope; later iterators see
            # the targets that have already been bound by earlier generators.
            self.visit(generators[0].iter)
        frame = {"kind": "comprehension", "locals": set(),
                 "globals": set(), "nonlocals": set()}
        self.frames.append(frame)
        for index, generator in enumerate(generators):
            if index:
                self.visit(generator.iter)
            frame["locals"].update(_bound_names(generator.target))
            for condition in generator.ifs:
                self.visit(condition)
        if isinstance(node, ast.DictComp):
            self.visit(node.key)
            self.visit(node.value)
        else:
            self.visit(node.elt)  # type: ignore[attr-defined]
        self.frames.pop()

    def visit_ListComp(self, node: ast.ListComp) -> None:
        self._visit_comprehension(node)

    def visit_SetComp(self, node: ast.SetComp) -> None:
        self._visit_comprehension(node)

    def visit_DictComp(self, node: ast.DictComp) -> None:
        self._visit_comprehension(node)

    def visit_GeneratorExp(self, node: ast.GeneratorExp) -> None:
        self._visit_comprehension(node)


def _local_binding_names(node: ast.AST) -> set[str]:
    """Find local binding identifiers that could capture a generated global."""
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


class _NamespaceTransformer(ast.NodeTransformer):
    def __init__(self, label: str, mapping: dict[str, str], root: symtable.SymbolTable):
        self.label = label
        self.mapping = mapping
        self.scopes = [root]
        self.frames = [{"kind": "module", "locals": set(), "globals": set(),
                        "nonlocals": set()}]
        self.used_child_scopes: dict[int, set[int]] = {}
        self.child_scope_cache: dict[int, tuple[symtable.SymbolTable, ...]] = {}

    @property
    def scope(self) -> symtable.SymbolTable:
        return self.scopes[-1]

    def _is_module_binding(self, name: str) -> bool:
        return self.frames[-1]["kind"] == "module" and name in self.mapping

    def _find_child_scope(
        self,
        names: tuple[str, ...],
        lineno: int,
        scope_type: str,
        *,
        required: bool,
    ) -> symtable.SymbolTable | None:
        children = self.child_scope_cache.setdefault(id(self.scope),
                                                     tuple(self.scope.get_children()))
        candidates = [child for child in children
                     if child.get_name() in names and child.get_lineno() == lineno
                     and child.get_type() == scope_type]
        used = self.used_child_scopes.setdefault(id(self.scope), set())
        matches = [child for child in candidates if id(child) not in used]
        if not matches and required:
            _reject(f"cannot map nested source scope for {names[0]}")
        if not matches:
            return None
        child = matches[0]
        used.add(id(child))
        return child

    def _child_scope(self, name: str, lineno: int, scope_type: str) -> symtable.SymbolTable:
        child = self._find_child_scope((name,), lineno, scope_type, required=True)
        assert child is not None
        return child

    def _is_namespaced_global(self, name: str) -> bool:
        if name not in self.mapping:
            return False
        current = self.frames[-1]
        if current["kind"] == "module":
            return True
        if name in current["globals"]:
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
        return [frame["locals"] for frame in self.frames if frame["kind"] == "comprehension"]

    def visit_Name(self, node: ast.Name) -> ast.AST:
        mapped = self._is_module_binding(node.id) or self._is_namespaced_global(node.id)
        if mapped:
            node.id = self.mapping[node.id]
        return node

    def visit_Global(self, node: ast.Global) -> ast.AST:
        node.names = [self.mapping.get(name, name) for name in node.names]
        return node

    def visit_FunctionDef(self, node: ast.FunctionDef) -> ast.AST:
        child = self._child_scope(node.name, node.lineno, "function")
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
        return self.visit_FunctionDef(node)  # type: ignore[arg-type]

    def visit_ClassDef(self, node: ast.ClassDef) -> ast.AST:
        child = self._child_scope(node.name, node.lineno, "class")
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
            aliases = []
            for alias in node.names:
                bound = alias.asname or alias.name.partition(".")[0]
                aliases.append(ast.alias(name=alias.name, asname=self.mapping[bound]))
            node.names = aliases
        return node

    def visit_ImportFrom(self, node: ast.ImportFrom) -> ast.AST:
        if len(self.scopes) == 1 and node.module != "__future__":
            aliases = []
            for alias in node.names:
                bound = alias.asname or alias.name
                aliases.append(ast.alias(name=alias.name, asname=self.mapping[bound]))
            node.names = aliases
        return node

    def visit_Lambda(self, node: ast.Lambda) -> ast.AST:
        node.args = self.visit(node.args)
        child = self._find_child_scope(("lambda", "<lambda>"), node.lineno,
                                       "function", required=True)
        inventory = _local_inventory(node, "lambda")
        assert child is not None
        self.scopes.append(child)
        self.frames.append({"kind": "lambda", "locals": inventory.locals,
                            "globals": inventory.globals, "nonlocals": inventory.nonlocals})
        node.body = self.visit(node.body)
        self.frames.pop()
        self.scopes.pop()
        return node

    def _visit_comprehension(self, node: ast.AST, scope_name: str) -> ast.AST:
        generators = node.generators  # type: ignore[attr-defined]
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
            node.elt = self.visit(node.elt)  # type: ignore[attr-defined]
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


def _namespace_nodes(extraction: _ModuleExtraction, source: bytes) -> tuple[ast.stmt, ...]:
    text = _validate_source_bytes(extraction.label, source)
    root = symtable.symtable(text, f"<{extraction.label}-source>", "exec")
    mapping = dict(extraction.rename)
    transformed: list[ast.stmt] = []
    for original in extraction.nodes:
        node = copy.deepcopy(original)
        transformer = _NamespaceTransformer(extraction.label, mapping, root)
        transformed.append(transformer.visit(node))
    return tuple(transformed)


def _module_proof(extraction: _ModuleExtraction) -> dict[str, object]:
    return {
        "closure": list(extraction.closure),
        "closureAstSha256": extraction.closure_ast_sha256,
        "closureSymbols": [
            {"kind": kind, "name": name} for name, kind in extraction.closure_kinds
        ],
        "label": extraction.label,
        "originalAstSha256": extraction.original_ast_sha256,
        "rename": [[left, right] for left, right in extraction.rename],
        "sourceSha256": extraction.source_sha256,
    }


def _extract_bundle(
    modules: tuple[ModuleInput, ...],
    *,
    facade_source: str = "",
) -> ExtractionResult:
    if not modules or len({module.label for module in modules}) != len(modules):
        _reject("module labels must be unique")
    if any(not re.fullmatch(r"[A-Z][A-Za-z0-9]*", module.label) for module in modules):
        _reject("module label is not a stable namespace")

    extractions = tuple(_selected_names_and_nodes(
        module.label, module.source, module.entrypoints) for module in modules)
    generated_names = {renamed for item in extractions for _, renamed in item.rename}
    for item in extractions:
        for node in item.nodes:
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                captures = _local_binding_names(node) & generated_names
                if captures:
                    _reject("generated namespace collides with original local binding: "
                            + sorted(captures)[0])
    annotated_modes = {(item.future_annotations, item.has_annotations) for item in extractions}
    if any(has_annotations for _, has_annotations in annotated_modes):
        future_modes = {future for future, has_annotations in annotated_modes if has_annotations}
        if len(future_modes) > 1:
            _reject("incompatible annotation modes across extracted modules")
    use_future = any(item.future_annotations and item.has_annotations for item in extractions)

    generated_nodes: list[ast.stmt] = []
    if use_future:
        generated_nodes.append(ast.ImportFrom(
            module="__future__", names=[ast.alias(name="annotations")], level=0))
    expected_blocks: list[tuple[ast.stmt, ...]] = []
    for module, extraction in zip(modules, extractions):
        block = _namespace_nodes(extraction, module.source)
        expected_blocks.append(block)
        generated_nodes.extend(block)
    facade_ast: tuple[ast.stmt, ...] = ()
    if facade_source:
        try:
            facade_ast = tuple(ast.parse(facade_source, filename="<pure-facade>", mode="exec").body)
        except SyntaxError as exc:
            _reject(f"pure facade parse failed: {type(exc).__name__}")
        _validate_selected_ast("facade", list(facade_ast))
        generated_nodes.extend(facade_ast)

    generated = "# Generated by extract_prior_kernel.py; see the accompanying provenance JSON.\n"
    generated += "\n\n".join(ast.unparse(node) for node in generated_nodes) + "\n"
    generated_bytes = generated.encode("utf-8")
    if len(generated_bytes) > MAX_KERNEL_BYTES:
        _reject("generated kernel exceeds the pinned size limit")
    try:
        parsed_generated = ast.parse(generated, filename="<generated-kernel>", mode="exec")
    except SyntaxError as exc:
        _reject(f"generated kernel parse failed: {type(exc).__name__}")

    cursor = 0
    if use_future:
        future_stmt = parsed_generated.body[0]
        if not (isinstance(future_stmt, ast.ImportFrom)
                and future_stmt.module == "__future__"
                and [alias.name for alias in future_stmt.names] == ["annotations"]):
            _reject("generated future annotation directive changed")
        cursor = 1
    for expected in expected_blocks:
        actual = tuple(parsed_generated.body[cursor:cursor + len(expected)])
        cursor += len(expected)
        if _ast_dump(actual) != _ast_dump(expected):
            _reject("generated AST differs beyond deterministic identifier renaming")
    if _ast_dump(tuple(parsed_generated.body[cursor:])) != _ast_dump(facade_ast):
        _reject("generated facade AST changed during serialization")
    if _ABSOLUTE_PATH.search(generated):
        _reject("generated kernel contains an absolute path string")

    proof = {
        "kernelSha256": _sha256(generated_bytes),
        "modules": [_module_proof(item) for item in extractions],
        "schema": PROOF_SCHEMA,
    }
    proof_bytes = (json.dumps(proof, sort_keys=True, separators=(",", ":"),
                              ensure_ascii=True, allow_nan=False) + "\n").encode("ascii")
    return ExtractionResult(generated_bytes, proof_bytes)


def _ast_dump(nodes: tuple[ast.stmt, ...]) -> str:
    return ast.dump(ast.Module(body=list(nodes), type_ignores=[]), include_attributes=False)


_PINNED_MODULES = (
    ("N", ("stable_cohort_order", "_validate_run", "_validate_terminal_failure",
           "_result_tree_minimum_units")),
    ("P07", ("validate_operation",)),
)

_FACADE_SOURCE = '''
def stable_cohort_order(net_ids, cohort_ids):
    return N_stable_cohort_order(net_ids, cohort_ids)

def validate_prior_run(run, kind, captured, cohort_ids):
    return N__validate_run(run, kind, captured, cohort_ids)

def validate_prior_terminal_failure(value, outcome, ordering, run_work, *, control):
    return N__validate_terminal_failure(value, outcome, ordering, run_work, control=control)

def result_tree_minimum_units(root):
    return N__result_tree_minimum_units(root)

def validate_operation(raw, entry, source_sha, wire_sha, arm, run_id, kind):
    return P07_validate_operation(raw, entry, source_sha, wire_sha, arm, run_id, kind)
'''


def extract_prior_kernel(n_source: bytes, p07_source: bytes) -> ExtractionResult:
    """Return pinned kernel source and deterministic provenance JSON bytes."""
    n_text = _validate_source_bytes("N", n_source)
    p07_text = _validate_source_bytes("P07", p07_source)
    del n_text, p07_text
    if _sha256(n_source) != PINNED_N_SHA256:
        _reject("N source SHA-256 does not match its pin")
    if _sha256(p07_source) != PINNED_P07_SHA256:
        _reject("P07 source SHA-256 does not match its pin")
    modules = tuple(ModuleInput(label, source, entrypoints)
                    for (label, entrypoints), source in zip(
                        _PINNED_MODULES, (n_source, p07_source)))
    return _extract_bundle(modules, facade_source=_FACADE_SOURCE)
