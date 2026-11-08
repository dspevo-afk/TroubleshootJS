"""Generate the source-bound-independent generic R7 result/marker reader.

The pinned T reader is parsed as source, never imported or executed. The kernel
and CLI adapter are supplied as explicit reviewed source inputs and copied
verbatim; their pins are owned and checked externally by the root integration.
"""
from __future__ import annotations

import ast
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import types


PLAN_SHA256 = "56fccb76c7170faeb101fce60ebe036e1125b2b05c79ada2425252b64740164f"
WIRE_SHA256 = "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741"
PARENT_READER_SHA256 = "4f9573c223025c564c7063f7de8e31f79b777845b92a332cb85ee418e8394d72"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"

MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_WIRE_BYTES = 256 * 1024
MAX_BASE_BYTES = 256 * 1024
MAX_DERIVED_BYTES = 4 * 1024 * 1024
MAX_PROOF_BYTES = 8 * 1024 * 1024
SCHEMA = "tsj-generic-r7-reader-source-v1"
GENERATOR_FLAGS = ("--reference-reader", "--kernel", "--adapter", "--wire",
                   "--base-generator", "--io-helper", "--output-dir")
OUTPUT_NAMES = ("read_generic_r7_result_v1.py", "SOURCE_PROOF.json")
RETIRED_REFERENCE_NAMES = frozenset({"main", "parse_args", "CLI_FLAGS"})
KERNEL_API = "validate_result_and_marker"
KERNEL_ERROR_CLASS = "GR7_ReaderError"

OUTPUT_DOCSTRING = b'''"""Pure generic R7 result/marker reader; semantics and derived export demand only.

This reader does not prove native execution, measured counters, source classes,
the host process receipt, authored data, full D, R7 execution or Player behavior.
"""
'''
OUTPUT_IMPORTS = b'''from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import time
import types

'''
ALLOWED_OUTPUT_IMPORTS = frozenset({
    "__future__", "hashlib", "json", "os", "pathlib", "re", "stat", "sys",
    "time", "types",
})


class GeneratorError(ValueError):
    """Stable, path-free error for source derivation and checked materialization."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def need(condition: bool, code: str) -> None:
    if not condition:
        raise GeneratorError(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _is_reparse(info) -> bool:
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400)


def _raw_canonical_path(value: str, code: str) -> Path:
    """Reject lexical aliases before pathlib has an opportunity to normalize."""
    need(type(value) is str and bool(value) and "\x00" not in value,
         code + "_VALUE")
    if os.name == "nt":
        need("/" not in value, code + "_SEPARATOR")
        drive = re.match(r"^[A-Za-z]:\\", value)
        unc = value.startswith("\\\\")
        need(not value.startswith(("\\\\?\\", "\\\\.\\")), code + "_DEVICE_PATH")
        need(drive is not None or unc, code + "_ABSOLUTE")
        parts = value[2:].split("\\") if unc else value[3:].split("\\")
        need(all(part not in ("", ".", "..") and ":" not in part
                 and not part.endswith((" ", ".")) for part in parts),
             code + "_ALIAS")
    else:
        need(value.startswith("/") and not value.startswith("//"), code + "_ABSOLUTE")
        parts = value.split("/")[1:]
        need(all(part not in ("", ".", "..") for part in parts), code + "_ALIAS")
    try:
        path = Path(value)
    except (TypeError, ValueError, OSError):
        raise GeneratorError(code + "_PATH") from None
    need(path.is_absolute() and str(path) == value, code + "_CANONICAL")
    return path


def _identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None),
            getattr(info, "st_file_attributes", 0))


def _handle_identity(info):
    """Compare only stat fields common to an open handle and its path entry."""
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None),
            getattr(info, "st_file_attributes", 0))


def _canonical_existing(path_value: str, code: str) -> Path:
    path = _raw_canonical_path(path_value, code)
    try:
        resolved = path.resolve(strict=True)
        need(str(resolved) == str(path), code + "_CANONICAL")
        for item in (path, *path.parents):
            info = item.lstat()
            need(not _is_reparse(info), code + "_REPARSE")
    except GeneratorError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise GeneratorError(code + "_PATH") from None
    return path


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
                 and _handle_identity(opened) == _handle_identity(before),
                 code + "_IDENTITY")
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


def load_base_generator(path_value: str):
    """Load only the fixed-pin public generator that supplies checked I/O."""
    raw, identity = _read_pinned_file(path_value, BASE_GENERATOR_SHA256,
                                      MAX_BASE_BYTES, "BASE_GENERATOR")
    module = types.ModuleType("_pinned_generic_r7_reader_base")
    module.__file__ = "<pinned-generic-r7-reader-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise GeneratorError("BASE_GENERATOR_LOAD") from None
    need(getattr(module, "IO_HELPER_SHA256", None) == IO_HELPER_SHA256,
         "BASE_IO_HELPER_PIN")
    module._checked_source_raw = raw
    module._checked_source_identity = identity
    return module


def _node_names(node):
    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
        return [node.name]
    if isinstance(node, ast.Assign):
        result = []
        for target in node.targets:
            if not isinstance(target, ast.Name):
                return None
            result.append(target.id)
        return result or None
    if isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
        return [node.target.id]
    return None


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


def _parse_source(raw: bytes, code: str):
    try:
        text = raw.decode("utf-8", errors="strict")
        tree = ast.parse(text)
    except (SyntaxError, UnicodeDecodeError, ValueError, RecursionError):
        raise GeneratorError(code + "_PARSE") from None
    lines = raw.splitlines(keepends=True)
    need(b"".join(lines) == raw, code + "_LINES")
    return tree, lines


def _source_span(node, lines, code: str) -> bytes:
    need(node.end_lineno is not None
         and 1 <= node.lineno <= node.end_lineno <= len(lines), code + "_SPAN")
    raw = b"".join(lines[node.lineno - 1:node.end_lineno])
    need(raw and raw.endswith(b"\n"), code + "_SPAN")
    return raw


def _reference_nodes(raw: bytes):
    """Retain every T pure definition and checked-I/O helper, retire only its CLI."""
    tree, lines = _parse_source(raw, "REFERENCE_READER")
    result = []
    names_seen = set()
    excluded_imports = 0
    excluded_docstrings = 0
    excluded_cli = set()
    guard_count = 0
    for ordinal, node in enumerate(tree.body):
        if _is_docstring(node, ordinal):
            excluded_docstrings += 1
            continue
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            excluded_imports += 1
            continue
        if _is_main_guard(node):
            expected_guard = ast.parse(
                "if __name__ == '__main__':\n    raise SystemExit(main())\n").body[0]
            need(ast.dump(node, include_attributes=False)
                 == ast.dump(expected_guard, include_attributes=False),
                 "REFERENCE_MAIN_GUARD")
            guard_count += 1
            excluded_cli.add("__main__ guard")
            continue
        names = _node_names(node)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and names in (["main"], ["parse_args"]):
            name = names[0]
            need(name not in excluded_cli, "REFERENCE_CLI_DUPLICATE")
            excluded_cli.add(name)
            continue
        if names == ["CLI_FLAGS"]:
            need("CLI_FLAGS" not in excluded_cli, "REFERENCE_CLI_DUPLICATE")
            excluded_cli.add("CLI_FLAGS")
            continue
        need(not isinstance(node, (ast.If, ast.With, ast.AsyncWith, ast.Try,
                                   ast.For, ast.AsyncFor, ast.While, ast.Delete,
                                   ast.Raise, ast.Expr)),
             "REFERENCE_UNEXPECTED_TOP_LEVEL_NODE")
        need(names is not None, "REFERENCE_TOP_LEVEL_NODE")
        need(all(name not in names_seen for name in names), "REFERENCE_DUPLICATE_NAME")
        names_seen.update(names)
        source = _source_span(node, lines, "REFERENCE_NODE")
        result.append({"names": names, "source": source})
    need(excluded_docstrings == 1 and excluded_imports >= 1 and guard_count == 1
         and excluded_cli == {"main", "parse_args", "CLI_FLAGS", "__main__ guard"},
         "REFERENCE_CLI_EXCLUSIONS")
    return result, {"docstringCount": excluded_docstrings,
                    "importNodeCount": excluded_imports,
                    "retiredCliNodes": ["main", "parse_args", "CLI_FLAGS",
                                        "__main__ guard"]}


def _kernel_nodes(raw: bytes):
    need(raw.endswith(b"\n"), "KERNEL_FINAL_LF")
    tree, lines = _parse_source(raw, "KERNEL")
    need(tree.body and _is_docstring(tree.body[0], 0), "KERNEL_DOCSTRING")
    nodes = []
    names_seen = set()
    names_in_order = []
    for ordinal, node in enumerate(tree.body):
        if _is_docstring(node, ordinal):
            continue
        need(not isinstance(node, (ast.Import, ast.ImportFrom, ast.If, ast.Expr,
                                   ast.With, ast.AsyncWith, ast.Try, ast.For,
                                   ast.AsyncFor, ast.While, ast.Delete, ast.Raise)),
             "KERNEL_TOP_LEVEL_NODE")
        names = _node_names(node)
        need(names is not None, "KERNEL_TOP_LEVEL_NODE")
        need(all(name not in names_seen for name in names), "KERNEL_DUPLICATE_NAME")
        names_seen.update(names)
        names_in_order.extend(names)
        nodes.append((names, _source_span(node, lines, "KERNEL_NODE")))
    need(KERNEL_ERROR_CLASS in names_seen and KERNEL_API in names_seen,
         "KERNEL_API_CLOSURE")
    need(all(name.startswith(("GR7_", "gr7_")) or name == KERNEL_API
             for name in names_seen), "KERNEL_NAMESPACE")
    need(not any(isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                 and node.func.id in ("compile", "eval", "exec", "__import__")
                 for node in ast.walk(tree)), "KERNEL_DYNAMIC_LOADER")
    return nodes, names_in_order


def _adapter_nodes(raw: bytes):
    need(raw.endswith(b"\n"), "ADAPTER_FINAL_LF")
    tree, lines = _parse_source(raw, "ADAPTER")
    names_seen = set()
    names_in_order = []
    guard_nodes = []
    for ordinal, node in enumerate(tree.body):
        if _is_docstring(node, ordinal):
            raise GeneratorError("ADAPTER_DOCSTRING_NOT_BYTE_POSITIONABLE")
        if _is_main_guard(node):
            guard_nodes.append(node)
            continue
        need(not isinstance(node, (ast.Import, ast.ImportFrom, ast.If, ast.Expr,
                                   ast.With, ast.AsyncWith, ast.Try, ast.For,
                                   ast.AsyncFor, ast.While, ast.Delete, ast.Raise)),
             "ADAPTER_TOP_LEVEL_NODE")
        names = _node_names(node)
        need(names is not None, "ADAPTER_TOP_LEVEL_NODE")
        need(all(name not in names_seen for name in names), "ADAPTER_DUPLICATE_NAME")
        names_seen.update(names)
        names_in_order.extend(names)
    need(names_in_order.count("main") == 1
         and sum(isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                 and node.name == "main" for node in tree.body) == 1,
         "ADAPTER_MAIN")
    expected_guard = ast.parse(
        "if __name__ == '__main__':\n    raise SystemExit(main())\n").body[0]
    need(len(guard_nodes) == 1 and guard_nodes[0] is tree.body[-1]
         and ast.dump(guard_nodes[0], include_attributes=False)
         == ast.dump(expected_guard, include_attributes=False), "ADAPTER_ENTRY_GUARD")
    need(not any(isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name)
                 and node.value.id == "sys" and node.attr == "path"
                 for node in ast.walk(tree)), "ADAPTER_SYS_PATH")
    need(not any(isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                 and node.func.id in ("compile", "eval", "exec", "__import__")
                 for node in ast.walk(tree)), "ADAPTER_DYNAMIC_LOADER")
    return tree, names_in_order


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
    result = []
    guards = 0
    for node in tree.body:
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            continue
        if _is_main_guard(node):
            guards += 1
            continue
        if isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant) \
                and type(node.value.value) is str:
            continue
        names = _node_names(node)
        need(names is not None, code + "_TOP_LEVEL_NODE")
        result.extend(names)
    need(guards == 1, code + "_ENTRY_GUARD")
    return result


def derive(reference_raw: bytes, kernel_raw: bytes, adapter_raw: bytes,
           wire_raw: bytes):
    """Compose the pinned T pure reader, R7 kernel, and explicit CLI adapter."""
    need(type(reference_raw) is bytes and 0 < len(reference_raw) <= MAX_SOURCE_BYTES
         and reference_raw.endswith(b"\n") and b"\r" not in reference_raw,
         "REFERENCE_READER_SIZE_OR_LINES")
    need(sha(reference_raw) == PARENT_READER_SHA256, "REFERENCE_READER_HASH")
    need(type(wire_raw) is bytes and 0 < len(wire_raw) <= MAX_WIRE_BYTES
         and sha(wire_raw) == WIRE_SHA256, "WIRE_HASH")
    need(type(kernel_raw) is bytes and 0 < len(kernel_raw) <= MAX_SOURCE_BYTES,
         "KERNEL_SIZE")
    need(type(adapter_raw) is bytes and 0 < len(adapter_raw) <= MAX_SOURCE_BYTES,
         "ADAPTER_SIZE")
    try:
        kernel_raw.decode("utf-8", errors="strict")
        adapter_raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise GeneratorError("SUPPLIED_SOURCE_UTF8") from None

    reference_nodes, exclusions = _reference_nodes(reference_raw)
    kernel_nodes, kernel_names = _kernel_nodes(kernel_raw)
    _adapter_tree, adapter_names = _adapter_nodes(adapter_raw)
    reference_names = [name for row in reference_nodes for name in row["names"]]
    expected = reference_names + kernel_names + adapter_names
    need(len(expected) == len(set(expected)), "SOURCE_BINDING_COLLISION")

    sections = [OUTPUT_DOCSTRING, OUTPUT_IMPORTS]
    for row in reference_nodes:
        sections.extend((row["source"], b"\n"))
    # These two explicit root-reviewed sources are copied as complete byte runs.
    sections.extend((kernel_raw, b"\n\n", adapter_raw))
    derived = b"".join(sections)
    need(0 < len(derived) <= MAX_DERIVED_BYTES, "DERIVED_SIZE")
    try:
        derived_tree = ast.parse(derived.decode("utf-8", errors="strict"))
    except (SyntaxError, UnicodeDecodeError, ValueError, RecursionError):
        raise GeneratorError("DERIVED_PARSE") from None
    imports = _audit_imports(derived_tree, "DERIVED")
    bindings = _top_level_bindings(derived_tree, "DERIVED")
    need(bindings == expected, "DERIVED_TOP_LEVEL_CLOSURE")
    need(derived.count(kernel_raw) == 1 and derived.endswith(adapter_raw)
         and derived.count(adapter_raw) == 1, "VERBATIM_SOURCE_CLOSURE")
    need(not any(isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name)
                 and node.value.id == "sys" and node.attr == "path"
                 for node in ast.walk(derived_tree)), "DERIVED_SYS_PATH")
    need(not any(isinstance(node, ast.Name)
                 and node.id in ("argparse", "importlib", "subprocess")
                 for node in ast.walk(derived_tree)), "DERIVED_DYNAMIC_IMPORT")
    proof = {
        "schema": SCHEMA,
        "status": "GENERATED_SOURCE_ONLY_NOT_EXECUTED",
        "planSha256": PLAN_SHA256,
        "wireContractSha256": WIRE_SHA256,
        "parentReaderSha256": sha(reference_raw),
        "wireSourceSha256": sha(wire_raw),
        "kernelSourceSha256": sha(kernel_raw),
        "kernelSourceBytes": len(kernel_raw),
        "kernelBindings": kernel_names,
        "adapterSourceSha256": sha(adapter_raw),
        "adapterSourceBytes": len(adapter_raw),
        "adapterBindings": adapter_names,
        "baseGeneratorSha256": BASE_GENERATOR_SHA256,
        "ioHelperSha256": IO_HELPER_SHA256,
        "derivedSha256": sha(derived),
        "derivedBytes": len(derived),
        "outputs": list(OUTPUT_NAMES),
        "referenceExclusions": exclusions,
        "referenceNodes": [{"names": row["names"],
                             "sourceSha256": sha(row["source"]),
                             "sourceBytes": len(row["source"]),
                             "byteEqual": True} for row in reference_nodes],
        "kernelByteEqual": derived.count(kernel_raw) == 1,
        "adapterByteEqual": derived.endswith(adapter_raw),
        "outputClosure": {"topLevelBindings": bindings, "imports": imports},
        "kernelAdapterPinOwner": "ROOT_EXTERNAL_REVIEW_REQUIRED",
        "actualNativeCounterProof": False,
        "authoredInvocations": 0,
        "newNativeProducerInvocations": 0,
        "fullD": False,
    }
    return derived, proof


def parse_args(argv):
    need(type(argv) in (list, tuple)
         and len(argv) == 2 * len(GENERATOR_FLAGS), "CLI_ARGUMENTS")
    values = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        need(type(flag) is str and flag in GENERATOR_FLAGS and flag not in values
             and type(value) is str and bool(value) and "\x00" not in value
             and not value.startswith("--"), "CLI_ARGUMENTS")
        _raw_canonical_path(value, "CLI_PATH")
        values[flag] = value
    need(set(values) == set(GENERATOR_FLAGS), "CLI_ARGUMENTS")
    return values


def _read_input(io_module, path_value: str, limit: int, code: str):
    path = _canonical_existing(path_value, code)
    try:
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and not _is_reparse(before)
             and 0 < before.st_size <= limit, code + "_FILE")
        raw = io_module._read_regular(path_value, limit, code)
        after = path.lstat()
    except GeneratorError:
        raise
    except Exception as failure:
        failure_code = getattr(failure, "code", code + "_READ")
        if type(failure_code) is not str or not failure_code:
            failure_code = code + "_READ"
        raise GeneratorError(failure_code) from None
    need(type(raw) is bytes and 0 < len(raw) <= limit
         and len(raw) == before.st_size, code + "_SIZE")
    need(stat.S_ISREG(after.st_mode) and not _is_reparse(after)
         and _identity(before) == _identity(after), code + "_CHANGED")
    return raw, _identity(before)


def _recheck_inputs(options, base, io_module, originals, identities,
                    helper_raw, helper_identity):
    current_base, current_base_identity = _read_pinned_file(
        options["--base-generator"], BASE_GENERATOR_SHA256,
        MAX_BASE_BYTES, "BASE_GENERATOR")
    need(current_base == base._checked_source_raw
         and current_base_identity == base._checked_source_identity,
         "BASE_GENERATOR_CHANGED")
    try:
        current_helper, current_helper_identity = _read_pinned_file(
            options["--io-helper"], IO_HELPER_SHA256, MAX_BASE_BYTES, "IO_HELPER")
        need(current_helper == helper_raw and current_helper_identity == helper_identity,
             "IO_HELPER_CHANGED")
        current_io = base.load_io(options["--io-helper"])
        after_helper, after_helper_identity = _read_pinned_file(
            options["--io-helper"], IO_HELPER_SHA256, MAX_BASE_BYTES, "IO_HELPER")
        need(after_helper == helper_raw and after_helper_identity == helper_identity,
             "IO_HELPER_CHANGED")
    except Exception:
        raise GeneratorError("IO_HELPER_RECHECK") from None
    need(callable(getattr(current_io, "_read_regular", None))
         and callable(getattr(io_module, "_read_regular", None)), "IO_HELPER_RECHECK")
    for flag, limit, code in (("--reference-reader", MAX_SOURCE_BYTES, "REFERENCE_READER"),
                              ("--kernel", MAX_SOURCE_BYTES, "KERNEL"),
                              ("--adapter", MAX_SOURCE_BYTES, "ADAPTER"),
                              ("--wire", MAX_WIRE_BYTES, "WIRE")):
        current, current_identity = _read_input(io_module, options[flag], limit, code)
        need(current == originals[flag] and current_identity == identities[flag],
             "SOURCE_CHANGED")


def generate(options: dict[str, str]):
    need(type(options) is dict and set(options) == set(GENERATOR_FLAGS),
         "GENERATOR_OPTIONS")
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
    helper_after, helper_identity_after = _read_pinned_file(
        options["--io-helper"], IO_HELPER_SHA256, MAX_BASE_BYTES, "IO_HELPER")
    need(helper_after == helper_raw and helper_identity_after == helper_identity,
         "IO_HELPER_CHANGED")
    need(callable(getattr(io_module, "_checked_directory", None))
         and callable(getattr(io_module, "_directory_names", None))
         and callable(getattr(io_module, "_read_regular", None))
         and callable(getattr(io_module, "_write_exclusive", None)), "IO_HELPER_API")

    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(not io_module._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    originals, identities = {}, {}
    for flag, limit, code in (("--reference-reader", MAX_SOURCE_BYTES, "REFERENCE_READER"),
                              ("--kernel", MAX_SOURCE_BYTES, "KERNEL"),
                              ("--adapter", MAX_SOURCE_BYTES, "ADAPTER"),
                              ("--wire", MAX_WIRE_BYTES, "WIRE")):
        originals[flag], identities[flag] = _read_input(
            io_module, options[flag], limit, code)
    derived, proof = derive(originals["--reference-reader"], originals["--kernel"],
                            originals["--adapter"], originals["--wire"])
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")
    files = {"read_generic_r7_result_v1.py": derived, "SOURCE_PROOF.json": proof_raw}
    file_limits = {"read_generic_r7_result_v1.py": MAX_DERIVED_BYTES,
                   "SOURCE_PROOF.json": MAX_PROOF_BYTES}

    _recheck_inputs(options, base, io_module, originals, identities,
                    helper_raw, helper_identity)
    need(not io_module._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    for name, raw in files.items():
        output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
        io_module._write_exclusive(output / name, raw, file_limits[name], "OUTPUT_FILE")
    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    names = io_module._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == len(files) and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io_module._read_regular(output / name, file_limits[name],
                                     "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    _recheck_inputs(options, base, io_module, originals, identities,
                    helper_raw, helper_identity)
    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    names = io_module._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == len(files) and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        need(io_module._read_regular(output / name, file_limits[name],
                                     "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("GENERIC_R7_READER_GENERATOR_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        proof = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(
                char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for char in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("GENERIC_R7_READER_GENERATOR_FAIL " + code + "\n")
        return 2
    summary = {"schema": SCHEMA, "status": proof["status"],
               "derivedSha256": proof["derivedSha256"], "files": len(OUTPUT_NAMES)}
    sys.stdout.write(json.dumps(summary, sort_keys=True, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
