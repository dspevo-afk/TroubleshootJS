"""Generate the checked, source-bound natural-reference host reader.

Parent sources are authenticated and parsed as data.  The only executable
source bootstrap is the fixed-pin public generator and its fixed-pin checked
I/O helper; neither the parent reader nor the host reader is imported here.
"""
from __future__ import annotations

import ast
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import sys
import tokenize
import types


PLAN_SHA256 = "a9c70b514b191b3e4a27137e85a4b13786c30ad43de0db88af27f05e79753e02"
WIRE_SHA256 = "4d1376c3c303fcc8c67222be040c0173f0af58d2d39f70fcc0fe081ffd127834"
HOST_READER_SHA256 = "dbe7ee4411d78217d08f118967dee873d63551d908747266145d12d1c44b5806"
REFERENCE_READER_SHA256 = "4f9573c223025c564c7063f7de8e31f79b777845b92a332cb85ee418e8394d72"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"

MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_BASE_BYTES = 256 * 1024
MAX_DERIVED_BYTES = MAX_SOURCE_BYTES
SCHEMA = "tsj-source-bound-reference-host-admission-generator-v1"
CLI_FLAGS = ("--host-reader", "--reference-reader", "--adapter",
             "--base-generator", "--io-helper", "--output-dir")
OUTPUT_NAMES = ("read_source_bound_reference_v1.py", "SOURCE_PROOF.json")

HOST_FUNCTIONS = (
    "ReaderError", "need", "sha", "exact_keys", "is_int", "is_digest",
    "_pairs", "_parse_int", "_parse_float", "_reject_constant",
    "_json_load", "_is_compact", "strict_compact_line", "strict_document",
    "host_document", "_lstat_regular", "_lstat_directory", "_read",
    "_canonical", "_same_path", "_same_host_path", "_validate_owner_call",
    "_validate_process_stderr", "_is_finite_number", "_validate_class_tree",
)
HOST_CONSTANTS = (
    "MAX_JSON", "MAX_CLASS_BYTES", "MAX_CLASS_COUNT", "HEX", "CLASS_SCHEMA",
    "PARENT_SCHEMA", "ROOT_CALL_OWNER_SCHEMA", "PS5_SHA256",
    "P9_ROOT_CALL_SHA256", "P9_PROCESS_MODULE_SHA256",
)
HOST_NAMES = frozenset(HOST_FUNCTIONS + HOST_CONSTANTS)
HOST_RENAMES = {name: "RH_" + name for name in HOST_FUNCTIONS + HOST_CONSTANTS}

OUTPUT_IMPORTS = b'''from __future__ import annotations

import hashlib
import json
import math
import os
import re
import stat
import sys
import time
import types
from pathlib import Path

'''
OUTPUT_DOCSTRING = b'''"""Source-bound admission for the pinned natural generic reference packet.

This generated reader binds the current host call and its source/class inputs.
It does not admit authored originals, full D, R7, HARD/PSYCHOTIC, or Player.
"""
'''
ALLOWED_OUTPUT_IMPORTS = frozenset({
    "__future__", "hashlib", "json", "math", "os", "pathlib", "re", "stat",
    "sys", "time", "types",
})


class GeneratorError(ValueError):
    """Stable, path-free error raised for an invalid source derivation."""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise GeneratorError(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _is_reparse(info) -> bool:
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400)


def _raw_canonical_path(value: str, code: str) -> Path:
    """Reject lexical aliases before pathlib can normalize them."""
    need(type(value) is str and value and "\x00" not in value, code + "_VALUE")
    if os.name == "nt":
        need("/" not in value, code + "_SEPARATOR")
        drive = re.match(r"^[A-Za-z]:\\", value)
        unc = value.startswith("\\\\")
        need(not value.startswith(("\\\\?\\", "\\\\.\\")), code + "_DEVICE_PATH")
        need(drive is not None or unc, code + "_ABSOLUTE")
        pieces = value[2:].split("\\") if unc else value[3:].split("\\")
        need(all(piece not in ("", ".", "..") and ":" not in piece
                 and not piece.endswith((" ", ".")) for piece in pieces), code + "_ALIAS")
    else:
        need(value.startswith("/") and not value.startswith("//"), code + "_ABSOLUTE")
        pieces = value.split("/")[1:]
        need(all(piece not in ("", ".", "..") for piece in pieces), code + "_ALIAS")
    try:
        path = Path(value)
    except (TypeError, ValueError, OSError):
        raise GeneratorError(code + "_PATH") from None
    need(path.is_absolute() and str(path) == value, code + "_CANONICAL")
    return path


def _canonical_existing(path_value: str, code: str) -> Path:
    path = _raw_canonical_path(path_value, code)
    try:
        resolved = path.resolve(strict=True)
        need(str(resolved) == str(path), code + "_CANONICAL")
        for component in (path, *path.parents):
            info = component.lstat()
            need(not _is_reparse(info), code + "_REPARSE")
    except GeneratorError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise GeneratorError(code + "_PATH") from None
    return path


def _identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None),
            getattr(info, "st_file_attributes", 0))


def _handle_identity(info):
    # Selected Windows Python reports unlike ctime values through lstat and
    # fstat. Compare their common object fields; full path identities, including
    # ctime, are still compared before and after the read below.
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_file_attributes", 0))


def _read_pinned_file(path_value: str, expected_sha: str, limit: int, code: str):
    path = _canonical_existing(path_value, code)
    try:
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and not _is_reparse(before)
             and 0 < before.st_size <= limit, code + "_FILE")
        flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(str(path), flags)
        try:
            opened = os.fstat(descriptor)
            need(stat.S_ISREG(opened.st_mode) and not _is_reparse(opened)
                 and _handle_identity(opened) == _handle_identity(before), code + "_IDENTITY")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = None
                raw = stream.read(limit + 1)
        finally:
            if descriptor is not None:
                os.close(descriptor)
        after = path.lstat()
    except GeneratorError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise GeneratorError(code + "_READ") from None
    need(stat.S_ISREG(after.st_mode) and not _is_reparse(after)
         and _identity(before) == _identity(after), code + "_CHANGED")
    need(0 < len(raw) <= limit and len(raw) == before.st_size
         and sha(raw) == expected_sha, code + "_HASH")
    return raw, _identity(before)


def _read_pinned_base(path_value: str):
    return _read_pinned_file(path_value, BASE_GENERATOR_SHA256, MAX_BASE_BYTES,
                             "BASE_GENERATOR")


def load_base_generator(path_value: str):
    """Load only the fixed-pin public generator used for checked I/O."""
    raw, identity = _read_pinned_base(path_value)
    module = types.ModuleType("_pinned_reference_host_admission_base")
    module.__file__ = "<pinned-reference-host-admission-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise GeneratorError("BASE_GENERATOR_LOAD") from None
    need(getattr(module, "IO_HELPER_SHA256", None) == IO_HELPER_SHA256,
         "BASE_IO_HELPER_PIN")
    module._checked_source_raw = raw
    module._checked_source_identity = identity
    return module


def _pinned_source(raw: bytes, expected: str, code: str) -> None:
    need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES, code + "_SIZE")
    need(raw.endswith(b"\n") and b"\r" not in raw.replace(b"\r\n", b""),
         code + "_LINE_ENDINGS")
    try:
        raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise GeneratorError(code + "_UTF8") from None
    need(sha(raw) == expected, code + "_HASH")


def _top_name(node):
    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
        return node.name
    if isinstance(node, ast.Assign):
        names = []
        for target in node.targets:
            if isinstance(target, ast.Name):
                names.append(target.id)
            else:
                return None
        return names[0] if len(names) == 1 else tuple(names) if names else None
    if isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
        return node.target.id
    return None


def _node_spans(raw: bytes, code: str):
    try:
        text = raw.decode("utf-8", errors="strict")
        tree = ast.parse(text)
    except (SyntaxError, UnicodeDecodeError, ValueError, RecursionError):
        raise GeneratorError(code + "_PARSE") from None
    lines = raw.splitlines(keepends=True)
    need(b"".join(lines) == raw, code + "_LINES")
    spans = []
    names_seen = set()
    for ordinal, node in enumerate(tree.body):
        if _is_docstring(node, ordinal) or isinstance(node, (ast.Import, ast.ImportFrom)):
            continue
        if _is_main_guard(node):
            continue
        name = _top_name(node)
        need(name is not None, code + "_TOP_LEVEL_NODE")
        names = (name,) if type(name) is str else name
        need(all(item not in names_seen for item in names), code + "_DUPLICATE_NAME")
        names_seen.update(names)
        need(node.end_lineno is not None and 1 <= node.lineno <= node.end_lineno <= len(lines),
             code + "_SPAN")
        source = b"".join(lines[node.lineno - 1:node.end_lineno])
        need(source and source.endswith(b"\n"), code + "_SPAN")
        spans.append((node, names, source))
    return tree, spans


def _is_docstring(node, ordinal: int) -> bool:
    return (ordinal == 0 and isinstance(node, ast.Expr)
            and isinstance(node.value, ast.Constant) and type(node.value.value) is str)


def _is_main_guard(node) -> bool:
    return (isinstance(node, ast.If)
            and isinstance(node.test, ast.Compare)
            and isinstance(node.test.left, ast.Name)
            and node.test.left.id == "__name__"
            and len(node.test.ops) == 1 and isinstance(node.test.ops[0], ast.Eq)
            and len(node.test.comparators) == 1
            and isinstance(node.test.comparators[0], ast.Constant)
            and node.test.comparators[0].value == "__main__")


def _reference_nodes(raw: bytes):
    """Keep every pure parent node, removing only the retired outer CLI."""
    try:
        text = raw.decode("utf-8", errors="strict")
        tree = ast.parse(text)
    except (SyntaxError, UnicodeDecodeError, ValueError, RecursionError):
        raise GeneratorError("REFERENCE_READER_PARSE") from None
    lines = raw.splitlines(keepends=True)
    need(b"".join(lines) == raw, "REFERENCE_READER_LINES")
    kept = []
    excluded_imports = 0
    excluded_docstrings = 0
    excluded_cli = set()
    names_seen = set()
    guard_count = 0
    for ordinal, node in enumerate(tree.body):
        if _is_docstring(node, ordinal):
            excluded_docstrings += 1
            continue
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            excluded_imports += 1
            continue
        if _is_main_guard(node):
            guard_count += 1
            excluded_cli.add("__main__ guard")
            continue
        name = _top_name(node)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and name in ("main", "parse_args"):
            need(name not in excluded_cli, "REFERENCE_CLI_DUPLICATE")
            excluded_cli.add(name)
            continue
        if name == "CLI_FLAGS":
            need(isinstance(node, (ast.Assign, ast.AnnAssign)) and "CLI_FLAGS" not in excluded_cli,
                 "REFERENCE_CLI_DUPLICATE")
            excluded_cli.add("CLI_FLAGS")
            continue
        need(not isinstance(node, (ast.If, ast.With, ast.AsyncWith, ast.Try,
                                   ast.For, ast.AsyncFor, ast.While, ast.Delete,
                                   ast.Raise, ast.Expr)), "REFERENCE_UNEXPECTED_EXECUTABLE_NODE")
        need(name is not None, "REFERENCE_TOP_LEVEL_NODE")
        names = (name,) if type(name) is str else name
        need(all(item not in names_seen for item in names), "REFERENCE_DUPLICATE_NAME")
        names_seen.update(names)
        need(node.end_lineno is not None and 1 <= node.lineno <= node.end_lineno <= len(lines),
             "REFERENCE_NODE_SPAN")
        source = b"".join(lines[node.lineno - 1:node.end_lineno])
        need(source and source.endswith(b"\n"), "REFERENCE_NODE_SPAN")
        kept.append({"names": list(names), "source": source})
    need(excluded_docstrings == 1 and excluded_imports >= 1 and guard_count == 1
         and excluded_cli == {"main", "parse_args", "CLI_FLAGS", "__main__ guard"},
         "REFERENCE_CLI_EXCLUSIONS")
    return kept, {"docstring": excluded_docstrings, "imports": excluded_imports,
                  "cliNodes": ["main", "parse_args", "CLI_FLAGS", "__main__ guard"]}


def _host_nodes(raw: bytes):
    """Select the frozen owner-call and class-tree kernel, no surrounding CLI."""
    _tree, spans = _node_spans(raw, "HOST_READER")
    found = {}
    for _node, names, source in spans:
        for name in names:
            if name in HOST_NAMES:
                need(name not in found, "HOST_KERNEL_DUPLICATE")
                found[name] = source
    need(set(found) == HOST_NAMES, "HOST_KERNEL_CLOSURE")
    # Preserve source order instead of sorting by the contract list.
    ordered = []
    for _node, names, source in spans:
        selected = [name for name in names if name in HOST_NAMES]
        if selected:
            need(len(selected) == len(names), "HOST_KERNEL_COMBINED_NODE")
            ordered.append((selected[0], source))
    need(len([name for name in ordered if name[0] in HOST_FUNCTIONS]) == len(HOST_FUNCTIONS)
         and len([name for name in ordered if name[0] in HOST_CONSTANTS]) == len(HOST_CONSTANTS),
         "HOST_KERNEL_COUNTS")
    return ordered


def _rename_identifier_tokens(raw: bytes, renames: dict[str, str]) -> bytes:
    """Rename Python NAME tokens while preserving every non-token byte."""
    try:
        text = raw.decode("utf-8", errors="strict")
        lines = text.splitlines(keepends=True)
        starts = [0]
        for line in lines:
            starts.append(starts[-1] + len(line))
        need(starts[-1] == len(text), "TOKEN_LINES")
        edits = []
        for token in tokenize.generate_tokens(io.StringIO(text).readline):
            if token.type != tokenize.NAME or token.string not in renames:
                continue
            start = starts[token.start[0] - 1] + token.start[1]
            end = starts[token.end[0] - 1] + token.end[1]
            need(0 <= start < end <= len(text)
                 and text[start:end] == token.string, "TOKEN_SPAN")
            edits.append((start, end, renames[token.string]))
    except GeneratorError:
        raise
    except (SyntaxError, IndentationError, tokenize.TokenError, UnicodeDecodeError,
            ValueError, IndexError):
        raise GeneratorError("TOKENIZE_KERNEL") from None
    changed = text
    for start, end, value in reversed(edits):
        changed = changed[:start] + value + changed[end:]
    return changed.encode("utf-8")


def _audit_imports(tree, code: str) -> list[str]:
    found = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            need(all(alias.name.split(".", 1)[0] in ALLOWED_OUTPUT_IMPORTS
                     for alias in node.names), code + "_IMPORT")
            found.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom):
            need(node.level == 0 and node.module in ALLOWED_OUTPUT_IMPORTS,
                 code + "_IMPORT")
            found.add(node.module)
    return sorted(found)


def _top_level_bindings(tree, code: str):
    bindings = []
    for ordinal, node in enumerate(tree.body):
        if _is_docstring(node, ordinal) or isinstance(node, (ast.Import, ast.ImportFrom)):
            continue
        if _is_main_guard(node):
            bindings.append("<main-guard>")
            continue
        name = _top_name(node)
        need(name is not None, code + "_TOP_LEVEL_NODE")
        if type(name) is str:
            bindings.append(name)
        else:
            bindings.extend(name)
    return bindings


def _audit_adapter(raw: bytes):
    need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES, "ADAPTER_SIZE")
    try:
        text = raw.decode("utf-8", errors="strict")
        tree = ast.parse(text)
    except (SyntaxError, UnicodeDecodeError, ValueError, RecursionError):
        raise GeneratorError("ADAPTER_PARSE") from None
    _audit_imports(tree, "ADAPTER")
    need(not any(isinstance(node, ast.ImportFrom) and node.module == "__future__"
                 for node in tree.body), "ADAPTER_FUTURE_IMPORT")
    need(all(isinstance(node, (ast.FunctionDef, ast.ClassDef, ast.Assign, ast.AnnAssign,
                               ast.Import, ast.ImportFrom, ast.Expr)) or _is_main_guard(node)
             for node in tree.body), "ADAPTER_TOP_LEVEL_NODE")
    guards = [node for node in tree.body if _is_main_guard(node)]
    expected_guard = ast.parse("if __name__ == '__main__':\n    raise SystemExit(main())\n").body[0]
    need(len(guards) == 1 and guards[0] is tree.body[-1]
         and ast.dump(guards[0], include_attributes=False)
         == ast.dump(expected_guard, include_attributes=False), "ADAPTER_ENTRY_GUARD")
    need(not any(isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name)
                 and node.value.id == "sys" and node.attr == "path" for node in ast.walk(tree)),
         "ADAPTER_SYS_PATH")
    need(not any(isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                 and node.func.id in ("compile", "eval", "exec", "__import__")
                 for node in ast.walk(tree)), "ADAPTER_DYNAMIC_LOADER")
    bindings = _top_level_bindings(tree, "ADAPTER")
    need("main" in bindings and bindings.count("main") == 1
         and sum(isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                 and node.name == "main" for node in tree.body) == 1,
         "ADAPTER_MAIN")
    # The parent's CLI nodes were excluded above. A reviewed adapter can own
    # its new parser under the same ordinary name without restoring that CLI.
    return tree, bindings


def _audit_derived(raw: bytes, expected_bindings: list[str], adapter: bytes) -> dict:
    try:
        text = raw.decode("utf-8", errors="strict")
        tree = ast.parse(text)
    except (SyntaxError, UnicodeDecodeError, ValueError, RecursionError):
        raise GeneratorError("DERIVED_SOURCE_PARSE") from None
    imports = _audit_imports(tree, "DERIVED")
    actual = _top_level_bindings(tree, "DERIVED")
    need(len(expected_bindings) == len(set(expected_bindings)),
         "DERIVED_DUPLICATE_BINDING")
    need(actual == expected_bindings, "DERIVED_TOP_LEVEL_CLOSURE")
    need(raw.endswith(adapter) and raw.count(adapter) == 1, "ADAPTER_BYTE_CLOSURE")
    need(not any(isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name)
                 and node.value.id == "sys" and node.attr == "path" for node in ast.walk(tree)),
         "DERIVED_SYS_PATH")
    need(not any(isinstance(node, ast.Name) and node.id in ("argparse", "importlib", "subprocess")
                 for node in ast.walk(tree)), "DERIVED_DYNAMIC_LOADER")
    return {"topLevelBindings": actual, "imports": imports}


def derive(host_raw: bytes, reference_raw: bytes, adapter_raw: bytes):
    """Return exact generated bytes and a deterministic, source-only census."""
    _pinned_source(host_raw, HOST_READER_SHA256, "HOST_READER")
    _pinned_source(reference_raw, REFERENCE_READER_SHA256, "REFERENCE_READER")
    need(type(adapter_raw) is bytes and 0 < len(adapter_raw) <= MAX_SOURCE_BYTES,
         "ADAPTER_SIZE")
    try:
        adapter_raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise GeneratorError("ADAPTER_UTF8") from None

    reference_nodes, exclusions = _reference_nodes(reference_raw)
    host_nodes = _host_nodes(host_raw)
    _adapter_tree, adapter_bindings = _audit_adapter(adapter_raw)

    sections = [OUTPUT_DOCSTRING, OUTPUT_IMPORTS]
    extracted_reference = []
    expected_bindings = []
    for item in reference_nodes:
        source = item["source"]
        sections.extend((source, b"\n"))
        extracted_reference.append({
            "names": item["names"], "sourceSha256": sha(source),
            "sourceBytes": len(source), "derivedSha256": sha(source),
            "derivedBytes": len(source), "byteEqual": True,
        })
        expected_bindings.extend(item["names"])

    extracted_host = []
    for name, source in host_nodes:
        renamed = _rename_identifier_tokens(source, HOST_RENAMES)
        sections.extend((renamed, b"\n"))
        extracted_host.append({
            "name": name, "derivedName": HOST_RENAMES[name],
            "sourceSha256": sha(source), "sourceBytes": len(source),
            "derivedSha256": sha(renamed), "derivedBytes": len(renamed),
            "tokenRenamed": source != renamed,
        })
        expected_bindings.append(HOST_RENAMES[name])

    # The adapter is supplied explicitly by the root after review.  Its bytes
    # are copied unchanged, with only a separator before them.
    sections.extend((b"\n", adapter_raw))
    expected_bindings.extend(adapter_bindings)
    result = b"".join(sections)
    need(0 < len(result) <= MAX_DERIVED_BYTES, "DERIVED_SIZE")
    need(len(extracted_host) == len(HOST_FUNCTIONS) + len(HOST_CONSTANTS),
         "HOST_KERNEL_COUNTS")
    audit = _audit_derived(result, expected_bindings, adapter_raw)
    proof = {
        "schema": SCHEMA,
        "status": "GENERATED_SOURCE_ONLY_NOT_EXECUTED",
        "planSha256": PLAN_SHA256,
        "wireContractSha256": WIRE_SHA256,
        "hostReaderSha256": sha(host_raw),
        "referenceReaderSha256": sha(reference_raw),
        "adapterSha256": sha(adapter_raw),
        "baseGeneratorSha256": BASE_GENERATOR_SHA256,
        "ioHelperSha256": IO_HELPER_SHA256,
        "derivedSha256": sha(result),
        "derivedBytes": len(result),
        "outputs": list(OUTPUT_NAMES),
        "referenceExclusions": exclusions,
        "referenceNodes": extracted_reference,
        "hostKernelNodes": extracted_host,
        "hostKernelRenamePrefix": "RH_",
        "hostKernelNodeCount": len(extracted_host),
        "hostKernelFunctionCount": len(HOST_FUNCTIONS),
        "hostKernelConstantCount": len(HOST_CONSTANTS),
        "adapterByteEqual": result.endswith(adapter_raw),
        "adapterBindings": adapter_bindings,
        "outputClosure": audit,
        "authoredInvocations": 0,
        "newNativeProducerInvocations": 0,
        "fullD": False,
        "R7": False,
    }
    return result, proof


def parse_args(argv):
    need(type(argv) in (list, tuple) and len(argv) == 2 * len(CLI_FLAGS),
         "CLI_ARGUMENTS")
    values = {}
    for index in range(0, len(argv), 2):
        flag, value = argv[index:index + 2]
        need(type(flag) is str and flag in CLI_FLAGS and flag not in values
             and type(value) is str and bool(value) and "\x00" not in value
             and not value.startswith("--"), "CLI_ARGUMENTS")
        _raw_canonical_path(value, "CLI_PATH")
        values[flag] = value
    need(set(values) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    return values


def _read_input_bound(io_module, path_value: str, code: str):
    path = _canonical_existing(path_value, code)
    try:
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and not _is_reparse(before)
             and 0 < before.st_size <= MAX_SOURCE_BYTES, code + "_FILE")
        raw = io_module._read_regular(path_value, MAX_SOURCE_BYTES, code)
        after = path.lstat()
    except Exception as failure:
        if isinstance(failure, GeneratorError):
            raise
        code_value = getattr(failure, "code", code + "_READ")
        if type(code_value) is not str or not code_value:
            code_value = code + "_READ"
        raise GeneratorError(code_value) from None
    need(type(raw) is bytes and 0 < len(raw) <= MAX_SOURCE_BYTES, code + "_SIZE")
    need(stat.S_ISREG(after.st_mode) and not _is_reparse(after)
         and _identity(before) == _identity(after), code + "_CHANGED")
    need(len(raw) == before.st_size, code + "_SIZE")
    return raw, _identity(before)


def _read_input(io_module, path_value: str, code: str) -> bytes:
    return _read_input_bound(io_module, path_value, code)[0]


def _recheck_inputs(base_path: str, io_path: str, base, io_module,
                    paths: dict[str, str], original: dict[str, bytes],
                    identities: dict[str, tuple], helper_raw: bytes,
                    helper_identity: tuple) -> None:
    current_base, current_base_identity = _read_pinned_base(base_path)
    need(current_base == base._checked_source_raw
         and current_base_identity == base._checked_source_identity,
         "BASE_GENERATOR_CHANGED")
    try:
        current_helper, current_helper_identity = _read_pinned_file(
            io_path, IO_HELPER_SHA256, MAX_BASE_BYTES, "IO_HELPER")
        need(current_helper == helper_raw and current_helper_identity == helper_identity,
             "IO_HELPER_CHANGED")
        current_io = base.load_io(io_path)
    except Exception:
        raise GeneratorError("IO_HELPER_RECHECK") from None
    need(callable(getattr(current_io, "_read_regular", None))
         and callable(getattr(io_module, "_read_regular", None)), "IO_HELPER_RECHECK")
    for key in ("--host-reader", "--reference-reader", "--adapter"):
        current, current_identity = _read_input_bound(
            io_module, paths[key], key[2:].upper().replace("-", "_"))
        need(current == original[key] and current_identity == identities[key],
             "SOURCE_CHANGED")


def generate(options: dict[str, str]):
    need(type(options) is dict and set(options) == set(CLI_FLAGS), "GENERATOR_OPTIONS")
    for value in options.values():
        _raw_canonical_path(value, "INPUT_PATH")

    base = load_base_generator(options["--base-generator"])
    need(getattr(base, "IO_HELPER_SHA256", None) == IO_HELPER_SHA256,
         "BASE_IO_HELPER_PIN")
    helper_raw, helper_identity = _read_pinned_file(
        options["--io-helper"], IO_HELPER_SHA256, MAX_BASE_BYTES, "IO_HELPER")
    try:
        io_module = base.load_io(options["--io-helper"])
    except Exception:
        raise GeneratorError("IO_HELPER_LOAD") from None
    helper_raw_after, helper_identity_after = _read_pinned_file(
        options["--io-helper"], IO_HELPER_SHA256, MAX_BASE_BYTES, "IO_HELPER")
    need(helper_raw_after == helper_raw and helper_identity_after == helper_identity,
         "IO_HELPER_CHANGED")
    need(callable(getattr(io_module, "_checked_directory", None))
         and callable(getattr(io_module, "_directory_names", None))
         and callable(getattr(io_module, "_read_regular", None))
         and callable(getattr(io_module, "_write_exclusive", None)), "IO_HELPER_API")
    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io_module._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")

    original, identities = {}, {}
    for key, code in (("--host-reader", "HOST_READER"),
                      ("--reference-reader", "REFERENCE_READER"),
                      ("--adapter", "ADAPTER")):
        original[key], identities[key] = _read_input_bound(io_module, options[key], code)
    derived, proof = derive(original["--host-reader"], original["--reference-reader"],
                            original["--adapter"])
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")
    files = {
        "read_source_bound_reference_v1.py": derived,
        "SOURCE_PROOF.json": proof_raw,
    }

    _recheck_inputs(options["--base-generator"], options["--io-helper"], base,
                    io_module, options, original, identities, helper_raw,
                    helper_identity)
    need(not io_module._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    for name, raw in files.items():
        output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
        io_module._write_exclusive(output / name, raw, MAX_DERIVED_BYTES, "OUTPUT_FILE")

    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    names = io_module._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == len(files) and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io_module._read_regular(output / name, MAX_DERIVED_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    _recheck_inputs(options["--base-generator"], options["--io-helper"], base,
                    io_module, options, original, identities, helper_raw,
                    helper_identity)
    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    names = io_module._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == len(files) and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io_module._read_regular(output / name, MAX_DERIVED_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None) -> int:
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("REFERENCE_HOST_ADMISSION_GENERATOR_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        proof = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(
                char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for char in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("REFERENCE_HOST_ADMISSION_GENERATOR_FAIL " + code + "\n")
        return 2
    summary = {"schema": SCHEMA, "status": proof["status"],
               "derivedSha256": proof["derivedSha256"], "files": len(OUTPUT_NAMES)}
    sys.stdout.write(json.dumps(summary, sort_keys=True, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
