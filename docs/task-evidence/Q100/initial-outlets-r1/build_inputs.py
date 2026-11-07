"""Authenticated source/build adapter for the initial-outlet metric study.

This adapter prepares an exact appended-source JDK8 compile and records only
the compile closure. It does not launch a compiler, native operation, or reader.
"""
import sys

if not sys.flags.isolated or not sys.dont_write_bytecode:
    raise RuntimeError("isolated no-bytecode Python is required")

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import tempfile
import types


TASK_ROOT = Path(__file__).resolve().parent
TASK_SOURCE = TASK_ROOT / "source" / "InitialOutletStudy.java"
TASK_WIRE = TASK_ROOT / "source" / "WIRE_R1.md"
PLAN_PATH = TASK_ROOT / "PLAN_R1.md"

PARENT_ROOT = Path(tempfile.gettempdir()) / (
    "tsj-q100-branch-first-628b039cd39440b18a2e2131211b61c8"
)
PARENT_HELPER_PATH = PARENT_ROOT / "read_counterfactual.py"
PARENT_MANIFEST_PATH = PARENT_ROOT / "j8-final-r4" / "run-manifest.json"
PARENT_HELPER_SHA256 = "dbe7ee4411d78217d08f118967dee873d63551d908747266145d12d1c44b5806"
PARENT_MANIFEST_SHA256 = "d70952ca8e13a3a1395c81cc561da5c2db3877f42c681540e6922c71c97fa816"
SOURCE_PARENT = "474c0efa072be8cceed019c95a8079a6181bc680"
PLAN_SHA256 = "d78724b8b26f93b0dc935aa863a50f8fd1efa20b3faeb3401dcd7bea27ead0d2"

SOURCE_INPUT_SCHEMA = "tsj-initial-outlet-source-input-v1"
RUN_SCHEMA = "tsj-initial-outlet-source-v1"
CLASS_SCHEMA = "tsj-initial-outlet-classes-v1"
PREPARED_SCHEMA = "tsj-initial-outlet-prepared-v1"
FINALIZED_SCHEMA = "tsj-initial-outlet-finalized-v1"
AUDIT_SCHEMA = "tsj-initial-outlet-source-audit-v1"

PARENT_SOURCE_COUNT = 1087
COMPILER_SOURCE_COUNT = 1088
COMPILE_LIMIT_MS = 60_000
MAX_MANIFEST_BYTES = 16 * 1024 * 1024
MAX_SOURCE_BYTES = 256 * 1024 * 1024
MAX_CLASS_BYTES = 256 * 1024 * 1024
MAX_CLASS_COUNT = 10_000
MAX_CLASS_INVENTORY_BYTES = 4 * 1024 * 1024
MAX_COMPILER_LIST_BYTES = 2 * 1024 * 1024
MAX_SMALL_JSON_BYTES = 256 * 1024
HEX = re.compile(r"[0-9a-f]{64}\Z")
STAGE_NAME = re.compile(r"j8-initial-r([1-9][0-9]*)\Z")
COMPILE_CALL_NAME = re.compile(r"compile-call-r([1-9][0-9]*)\Z")


class BuildInputError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith(("IO_", "COMPILE_")) else "IO_" + code


def _need(condition: bool, code: str) -> None:
    if not condition:
        raise BuildInputError(code)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _is_int(value, low: int = 0, high: int = 2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high


def _same_json(left, right) -> bool:
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return set(left) == set(right) and all(_same_json(left[key], right[key])
                                               for key in left)
    if type(left) is list:
        return len(left) == len(right) and all(_same_json(a, b)
                                               for a, b in zip(left, right))
    return left == right


def _pairs(rows):
    result = {}
    for key, value in rows:
        if key in result:
            raise BuildInputError("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _parse_float(text: str) -> float:
    value = float(text)
    _need(math.isfinite(value), "JSON_NONFINITE")
    return value


def _reject_constant(_text: str):
    raise BuildInputError("JSON_NONFINITE")


def _json_load(raw: bytes, limit: int, code: str):
    _need(type(raw) is bytes and 0 < len(raw) <= limit, code)
    _need(not raw.startswith(b"\xef\xbb\xbf"), code + "_BOM")
    try:
        value = json.loads(
            raw.decode("utf-8", errors="strict"),
            object_pairs_hook=_pairs,
            parse_float=_parse_float,
            parse_constant=_reject_constant,
        )
    except BuildInputError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
        raise BuildInputError(code) from None
    return value


def _canonical_json(value) -> bytes:
    try:
        return (json.dumps(value, ensure_ascii=True, sort_keys=True,
                           separators=(",", ":"), allow_nan=False) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError):
        raise BuildInputError("JSON_SERIALIZE") from None


def _strict_document(raw: bytes, limit: int, code: str):
    _need(raw.endswith(b"\n") and not raw.endswith(b"\n\n") and b"\r" not in raw,
         code + "_FRAMING")
    value = _json_load(raw[:-1], limit, code)
    _need(raw == _canonical_json(value), code + "_CANONICAL")
    return value


def _is_reparse(info) -> bool:
    return stat.S_ISLNK(info.st_mode) or bool(getattr(info, "st_file_attributes", 0) & 0x400)


def _canonical(path_value, code: str, require_exists: bool = True) -> Path:
    _need(type(path_value) is str or isinstance(path_value, Path), code + "_TYPE")
    try:
        original = Path(path_value)
        resolved = original.resolve(strict=require_exists)
    except (OSError, RuntimeError, ValueError):
        raise BuildInputError(code) from None
    _need(original.is_absolute() and str(original) == str(resolved), code + "_CANONICAL")
    for parent in (resolved, *resolved.parents):
        try:
            info = parent.lstat()
        except FileNotFoundError:
            continue
        except OSError:
            raise BuildInputError(code + "_STAT") from None
        _need(not _is_reparse(info), code + "_REPARSE")
    return resolved


def _lstat_regular(path: Path, code: str):
    try:
        info = path.lstat()
    except OSError:
        raise BuildInputError(code) from None
    _need(stat.S_ISREG(info.st_mode) and not _is_reparse(info), code)
    return info


def _lstat_directory(path: Path, code: str):
    try:
        info = path.lstat()
    except OSError:
        raise BuildInputError(code) from None
    _need(stat.S_ISDIR(info.st_mode) and not _is_reparse(info), code)
    return info


def _stable_stat(info):
    return (info.st_dev, info.st_ino, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None))


def _read_file(path: Path, limit: int, code: str) -> bytes:
    before = _lstat_regular(path, code)
    _need(0 <= before.st_size <= limit, code + "_SIZE")
    try:
        raw = path.read_bytes()
        after = path.lstat()
    except OSError:
        raise BuildInputError(code + "_READ") from None
    _need(not _is_reparse(after) and stat.S_ISREG(after.st_mode)
         and _stable_stat(before) == _stable_stat(after)
         and len(raw) == after.st_size and len(raw) <= limit, code + "_CHANGED")
    return raw


def _file_record(path: Path, limit: int, code: str) -> dict:
    raw = _read_file(path, limit, code)
    return {"path": str(path), "bytes": len(raw), "sha256": _sha(raw)}


def _inside(path: Path, root: Path, code: str) -> None:
    try:
        path.relative_to(root)
    except ValueError:
        raise BuildInputError(code) from None


def _same_path(left, right) -> bool:
    try:
        a = Path(left).resolve(strict=False)
        b = Path(right).resolve(strict=False)
    except (OSError, RuntimeError, ValueError):
        return False
    return os.path.normcase(str(a)) == os.path.normcase(str(b))


def _write_new(path: Path, raw: bytes, limit: int, code: str) -> None:
    _need(type(raw) is bytes and len(raw) <= limit, code + "_SIZE")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_BINARY"):
        flags |= os.O_BINARY
    try:
        descriptor = os.open(str(path), flags, 0o600)
    except OSError:
        raise BuildInputError(code + "_CREATE_NEW") from None
    try:
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            view = memoryview(raw)
            offset = 0
            while offset < len(view):
                count = stream.write(view[offset:])
                if count is None or count <= 0:
                    raise BuildInputError(code + "_WRITE")
                offset += count
            stream.flush()
            os.fsync(stream.fileno())
    except BuildInputError:
        raise
    except OSError:
        raise BuildInputError(code + "_WRITE") from None
    _need(_read_file(path, limit, code + "_VERIFY") == raw, code + "_VERIFY")


def _create_directory(path: Path, code: str) -> None:
    try:
        os.mkdir(path)
    except OSError:
        raise BuildInputError(code + "_CREATE_NEW") from None
    _lstat_directory(path, code)


def _load_parent() -> tuple[types.ModuleType, dict]:
    _need(sys.flags.isolated and sys.dont_write_bytecode, "PYTHON_ISOLATION")
    helper = _canonical(PARENT_HELPER_PATH, "PARENT_HELPER_PATH")
    helper_raw = _read_file(helper, 2 * 1024 * 1024, "PARENT_HELPER")
    _need(_sha(helper_raw) == PARENT_HELPER_SHA256, "PARENT_HELPER_HASH")
    parent_manifest = _canonical(PARENT_MANIFEST_PATH, "PARENT_MANIFEST_PATH")
    module = types.ModuleType("_authenticated_copper_free_parent_reader")
    module.__file__ = str(helper)
    try:
        exec(compile(helper_raw, str(helper), "exec"), module.__dict__)
        context = module.validate_manifest(str(parent_manifest), PARENT_MANIFEST_SHA256)
    except (Exception, SystemExit) as failure:
        code = getattr(failure, "code", None)
        if type(code) is str and code:
            raise BuildInputError("PARENT_" + code) from None
        raise BuildInputError("PARENT_VALIDATION") from None
    _need(type(context) is dict and context.get("sha256") == PARENT_MANIFEST_SHA256,
         "PARENT_CONTEXT")
    manifest = context.get("manifest")
    paths = context.get("sourcePaths")
    _need(type(manifest) is dict and type(paths) is list
         and manifest.get("compilerSourceCount") == PARENT_SOURCE_COUNT
         and len(paths) == PARENT_SOURCE_COUNT
         and len(manifest.get("sources", [])) == PARENT_SOURCE_COUNT,
         "PARENT_SOURCE_COUNT")
    _need(all(isinstance(path, Path) for path in paths), "PARENT_SOURCE_PATHS")
    source_rows = manifest["sources"]
    _need([str(path) for path in paths] == [row.get("path") for row in source_rows],
         "PARENT_SOURCE_ORDER")
    _need(type(manifest.get("tools")) is list and len(manifest["tools"]) == 4
         and isinstance(context.get("processModule"), Path)
         and type(context.get("p07Reader")) is not type(None), "PARENT_TOOLS")
    _need(_sha(_read_file(helper, 2 * 1024 * 1024, "PARENT_HELPER_RECHECK"))
         == PARENT_HELPER_SHA256, "PARENT_HELPER_CHANGED")
    return module, context


def _check_plan() -> bytes:
    raw = _read_file(_canonical(PLAN_PATH, "PLAN_PATH"), 128 * 1024, "PLAN")
    _need(_sha(raw) == PLAN_SHA256, "PLAN_HASH")
    return raw


def _check_source_file(path: Path, limit: int, code: str) -> bytes:
    canonical = _canonical(path, code + "_PATH")
    _inside(canonical, TASK_ROOT, code + "_ROOT")
    raw = _read_file(canonical, limit, code)
    _need(raw.endswith(b"\n") and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"),
         code + "_FRAMING")
    try:
        raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise BuildInputError(code + "_UTF8") from None
    return raw


def _source_identity(parent_reader, parent_context) -> dict:
    plan_raw = _check_plan()
    old_manifest = parent_context["manifest"]
    old_sources = old_manifest["sources"]
    old_paths = parent_context["sourcePaths"]
    _need(len(old_sources) == len(old_paths) == PARENT_SOURCE_COUNT,
         "PARENT_SOURCE_COUNT")
    source_path = _canonical(TASK_SOURCE, "NATIVE_SOURCE_PATH")
    wire_path = _canonical(TASK_WIRE, "WIRE_PATH")
    _inside(source_path, TASK_ROOT, "NATIVE_SOURCE_ROOT")
    _inside(wire_path, TASK_ROOT, "WIRE_ROOT")
    existing = {os.path.normcase(str(path)) for path in old_paths}
    _need(os.path.normcase(str(source_path)) not in existing
         and os.path.normcase(str(wire_path)) not in existing, "NEW_SOURCE_DUPLICATE")
    source_raw = _check_source_file(source_path, 4 * 1024 * 1024, "NATIVE_SOURCE")
    wire_raw = _check_source_file(wire_path, 64 * 1024, "WIRE")
    source_rows = [dict(row) for row in old_sources]
    source_rows.append({"path": str(source_path), "bytes": len(source_raw),
                        "sha256": _sha(source_raw)})
    _need(len(source_rows) == COMPILER_SOURCE_COUNT, "COMPILER_SOURCE_COUNT")
    source_paths = [str(path) for path in old_paths] + [str(source_path)]
    for value in source_paths:
        _need("\n" not in value and "\r" not in value and '"' not in value,
             "COMPILER_PATH_CHARACTER")
    compiler_list = "".join('"' + value.replace("\\", "/") + '"\n'
                            for value in source_paths).encode("utf-8")
    _need(len(compiler_list) <= MAX_COMPILER_LIST_BYTES, "COMPILER_LIST_SIZE")
    tools = [dict(row) for row in old_manifest["tools"]]
    wire_row = {"path": str(wire_path), "bytes": len(wire_raw), "sha256": _sha(wire_raw)}
    return {
        "planRaw": plan_raw,
        "sourceRaw": source_raw,
        "wireRaw": wire_raw,
        "sourcePath": source_path,
        "wirePath": wire_path,
        "sourcePaths": source_paths,
        "sources": source_rows,
        "tools": tools,
        "wire": wire_row,
        "compilerList": compiler_list,
    }


def _task_root() -> Path:
    root = _canonical(TASK_ROOT, "TASK_ROOT")
    _need(root.is_dir(), "TASK_ROOT_TYPE")
    return root


def _expected_stage(stage_value, require_empty: bool) -> Path:
    root = _task_root()
    stage = _canonical(stage_value, "STAGE_PATH")
    _need(stage.parent == root and STAGE_NAME.fullmatch(stage.name) is not None,
         "STAGE_LOCATION")
    _lstat_directory(stage, "STAGE_DIRECTORY")
    if require_empty:
        try:
            entries = list(stage.iterdir())
        except OSError:
            raise BuildInputError("STAGE_LIST") from None
        _need(not entries, "STAGE_NOT_EMPTY")
    return stage


def _stage_revision(stage: Path) -> int:
    match = STAGE_NAME.fullmatch(stage.name)
    _need(match is not None, "STAGE_REVISION")
    revision = int(match.group(1))
    _need(1 <= revision <= 9999, "STAGE_REVISION")
    return revision


def _expected_compile_request(stage: Path, parent_context: dict, compiler_list_path: Path) -> dict:
    classes = stage / "classes"
    empty_sourcepath = stage / "empty-sourcepath"
    _need(_same_path(classes, stage / "classes")
         and _same_path(empty_sourcepath, stage / "empty-sourcepath"), "BUILD_PATHS")
    javac = parent_context["javac"]
    jars = parent_context["jars"]
    _need(type(javac) is dict and type(javac.get("path")) is str
         and set(jars) == {"gwt-user-2.7.0.jar", "gwt-dev-2.7.0.jar"}, "PARENT_TOOL_SET")
    classpath = ";".join((str(classes), jars["gwt-user-2.7.0.jar"]["path"],
                           jars["gwt-dev-2.7.0.jar"]["path"]))
    arguments = ["-source", "7", "-target", "7", "-encoding", "UTF-8",
                 "-classpath", classpath, "-sourcepath", str(empty_sourcepath),
                 "-d", str(classes), "@" + str(compiler_list_path)]
    return {"FilePath": javac["path"], "Arguments": arguments, "LimitMs": COMPILE_LIMIT_MS}


def _input_manifest(stage: Path, identity: dict, parent_reader, parent_context) -> dict:
    list_path = stage / "compiler-sources.txt"
    request_path = stage / "compiler-request.json"
    request = _expected_compile_request(stage, parent_context, list_path)
    request_raw = _canonical_json(request)
    return {
        "schema": SOURCE_INPUT_SCHEMA,
        "sourceParent": SOURCE_PARENT,
        "parentManifestPath": str(_canonical(PARENT_MANIFEST_PATH, "PARENT_MANIFEST_PATH")),
        "parentManifestSha256": PARENT_MANIFEST_SHA256,
        "parentHelperSha256": PARENT_HELPER_SHA256,
        "planSha256": PLAN_SHA256,
        "compilerSourceCount": COMPILER_SOURCE_COUNT,
        "compilerListPath": str(list_path),
        "compilerListBytes": len(identity["compilerList"]),
        "compilerListSha256": _sha(identity["compilerList"]),
        "compilerRequestPath": str(request_path),
        "compilerRequestSha256": _sha(request_raw),
        "classesRoot": str(stage / "classes"),
        "emptySourcepath": str(stage / "empty-sourcepath"),
        "sources": identity["sources"],
        "tools": identity["tools"],
        "wire": identity["wire"],
    }, request_raw


def prepare(stage_value: str) -> dict:
    stage = _expected_stage(stage_value, require_empty=True)
    parent_reader, parent_context = _load_parent()
    identity = _source_identity(parent_reader, parent_context)
    source_manifest, request_raw = _input_manifest(stage, identity, parent_reader, parent_context)
    manifest_raw = _canonical_json(source_manifest)
    _need(len(manifest_raw) <= MAX_MANIFEST_BYTES, "SOURCE_MANIFEST_SIZE")

    _create_directory(stage / "classes", "CLASSES_ROOT")
    _create_directory(stage / "empty-sourcepath", "EMPTY_SOURCEPATH")
    _write_new(stage / "compiler-sources.txt", identity["compilerList"],
               MAX_COMPILER_LIST_BYTES, "COMPILER_LIST")
    _write_new(stage / "compiler-request.json", request_raw,
               MAX_SMALL_JSON_BYTES, "COMPILER_REQUEST")
    _write_new(stage / "source-manifest.json", manifest_raw,
               MAX_MANIFEST_BYTES, "SOURCE_MANIFEST")
    return {
        "schema": PREPARED_SCHEMA,
        "status": "PREPARED_ONLY",
        "sourceManifestPath": str(stage / "source-manifest.json"),
        "sourceManifestSha256": _sha(manifest_raw),
        "compilerListPath": str(stage / "compiler-sources.txt"),
        "compilerListSha256": _sha(identity["compilerList"]),
        "compilerRequestPath": str(stage / "compiler-request.json"),
        "compilerRequestSha256": _sha(request_raw),
        "compilerSourceCount": COMPILER_SOURCE_COUNT,
    }


def _read_json(path: Path, limit: int, code: str):
    return _strict_document(_read_file(path, limit, code), limit, code)


def _validate_input_manifest(stage: Path, parent_reader, parent_context, expected_sha: str):
    path = stage / "source-manifest.json"
    raw = _read_file(path, MAX_MANIFEST_BYTES, "SOURCE_MANIFEST")
    _need(type(expected_sha) is str and HEX.fullmatch(expected_sha) is not None
         and _sha(raw) == expected_sha, "SOURCE_MANIFEST_HASH")
    value = _strict_document(raw, MAX_MANIFEST_BYTES, "SOURCE_MANIFEST")
    expected_fields = {
        "schema", "sourceParent", "parentManifestPath", "parentManifestSha256",
        "parentHelperSha256", "planSha256", "compilerSourceCount", "compilerListPath",
        "compilerListBytes", "compilerListSha256", "compilerRequestPath",
        "compilerRequestSha256", "classesRoot", "emptySourcepath", "sources", "tools", "wire",
    }
    _need(type(value) is dict and set(value) == expected_fields
         and value["schema"] == SOURCE_INPUT_SCHEMA, "SOURCE_MANIFEST_SCHEMA")
    identity = _source_identity(parent_reader, parent_context)
    expected, request_raw = _input_manifest(stage, identity, parent_reader, parent_context)
    _need(_same_json(value, expected), "SOURCE_MANIFEST_IDENTITY")
    list_raw = _read_file(stage / "compiler-sources.txt", MAX_COMPILER_LIST_BYTES,
                          "COMPILER_LIST")
    _need(list_raw == identity["compilerList"]
         and _sha(list_raw) == value["compilerListSha256"], "COMPILER_LIST_BINDING")
    request_disk = _read_file(stage / "compiler-request.json", MAX_SMALL_JSON_BYTES,
                              "COMPILER_REQUEST")
    _need(request_disk == request_raw
         and _sha(request_disk) == value["compilerRequestSha256"], "COMPILER_REQUEST_BINDING")
    classes = _canonical(value["classesRoot"], "CLASSES_ROOT")
    empty_sourcepath = _canonical(value["emptySourcepath"], "EMPTY_SOURCEPATH")
    _need(classes == stage / "classes" and empty_sourcepath == stage / "empty-sourcepath",
         "BUILD_PATH_BINDING")
    _lstat_directory(classes, "CLASSES_ROOT")
    _lstat_directory(empty_sourcepath, "EMPTY_SOURCEPATH")
    try:
        _need(not any(empty_sourcepath.iterdir()), "EMPTY_SOURCEPATH_NOT_EMPTY")
    except OSError:
        raise BuildInputError("EMPTY_SOURCEPATH_READ") from None
    return value, identity, request_raw, raw


def _validate_compile_call(call_value, stage: Path, parent_reader, parent_context,
                           request, require_success: bool = True) -> tuple[Path, bytes, dict, dict]:
    call_dir = _canonical(call_value, "COMPILE_CALL_PATH")
    call_match = COMPILE_CALL_NAME.fullmatch(call_dir.name)
    _need(call_dir.parent == _task_root() and call_match is not None
         and int(call_match.group(1)) == _stage_revision(stage), "COMPILE_CALL_LOCATION")
    _lstat_directory(call_dir, "COMPILE_CALL_DIRECTORY")
    parent_path = call_dir / "parent.json"
    parent_raw = _read_file(parent_path, MAX_SMALL_JSON_BYTES, "COMPILE_PARENT")
    parent = parent_reader.host_document(parent_raw, MAX_SMALL_JSON_BYTES)
    request_disk_raw = _read_file(call_dir / "request.json", MAX_SMALL_JSON_BYTES,
                                  "COMPILE_REQUEST_RECEIPT")
    request_disk = parent_reader.host_document(request_disk_raw, MAX_SMALL_JSON_BYTES)
    _need(request_disk == request, "COMPILE_REQUEST_RECEIPT_BINDING")
    process_raw = _read_file(call_dir / "process.json", MAX_SMALL_JSON_BYTES,
                              "COMPILE_PROCESS_RECEIPT")
    process = parent_reader.host_document(process_raw, MAX_SMALL_JSON_BYTES)
    _need(type(process) is dict, "COMPILE_PROCESS_SCHEMA")
    try:
        parent_reader._validate_owner_call(
            call_dir, parent_path, parent, request_disk, process,
            request["FilePath"], request["Arguments"], COMPILE_LIMIT_MS,
            parent_context["processModule"], required_stdout="",
            expected_stderr=parent_reader.COMPILE_STDERR,
        )
    except parent_reader.ReaderError as failure:
        reason = getattr(failure, "code", None)
        _need(type(reason) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", reason) is not None,
             "COMPILE_VALIDATION_ERROR")
        raise BuildInputError("COMPILE_" + reason) from None
    _need(not require_success or (process.get("ExitCode") == 0
         and process.get("TerminationProven") is True), "COMPILE_NOT_SUCCESS")
    return parent_path, parent_raw, parent, process


def _require_pre_finalize_stage(stage: Path) -> None:
    expected = {"classes", "empty-sourcepath", "compiler-sources.txt",
                "compiler-request.json", "source-manifest.json"}
    try:
        names = [entry.name for entry in stage.iterdir()]
    except OSError:
        raise BuildInputError("STAGE_LIST") from None
    _need(len(names) == len(expected) and set(names) == expected,
         "STAGE_PRE_FINALIZE_CLOSURE")


def _inventory_class_tree(classes: Path, source_manifest_sha: str) -> tuple[dict, bytes]:
    _lstat_directory(classes, "CLASSES_ROOT")
    rows = []
    total = 0
    pending = [classes]
    visited_dirs = 0
    while pending:
        directory = pending.pop()
        _lstat_directory(directory, "CLASS_DIRECTORY")
        visited_dirs += 1
        _need(visited_dirs <= MAX_CLASS_COUNT, "CLASS_DIRECTORY_COUNT")
        try:
            entries = sorted(os.scandir(directory), key=lambda item: item.name)
        except OSError:
            raise BuildInputError("CLASS_DIRECTORY_READ") from None
        for entry in entries:
            name = entry.name
            _need(name not in (".", "..") and "/" not in name and "\\" not in name,
                 "CLASS_ENTRY_NAME")
            path = directory / name
            try:
                info = path.lstat()
            except OSError:
                raise BuildInputError("CLASS_ENTRY_STAT") from None
            _need(not _is_reparse(info), "CLASS_REPARSE")
            if stat.S_ISDIR(info.st_mode):
                pending.append(path)
                continue
            _need(stat.S_ISREG(info.st_mode) and name.lower().endswith(".class"),
                 "CLASS_ENTRY_TYPE")
            _need(len(rows) < MAX_CLASS_COUNT and 0 < info.st_size <= MAX_CLASS_BYTES,
                 "CLASS_FILE_LIMIT")
            raw = _read_file(path, MAX_CLASS_BYTES, "CLASS_FILE")
            total += len(raw)
            _need(total <= MAX_CLASS_BYTES, "CLASS_TOTAL_BYTES")
            relative = path.relative_to(classes).as_posix()
            _need(relative and not relative.startswith("/")
                 and ".." not in relative.split("/"), "CLASS_RELATIVE_PATH")
            rows.append({"path": relative, "bytes": len(raw), "sha256": _sha(raw)})
    rows.sort(key=lambda row: row["path"])
    _need(rows and any(row["path"] ==
         "com/lushprojects/circuitjs1/client/InitialOutletStudy.class" for row in rows),
         "STUDY_CLASS_MISSING")
    _need(len({row["path"].casefold() for row in rows}) == len(rows), "CLASS_PATH_DUPLICATE")
    inventory = {
        "schema": CLASS_SCHEMA,
        "sourceManifestSha256": source_manifest_sha,
        "classCount": len(rows),
        "totalBytes": total,
        "entries": rows,
    }
    raw_inventory = _canonical_json(inventory)
    _need(len(raw_inventory) <= MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY_SIZE")
    return inventory, raw_inventory


def _check_class_inventory(stage: Path, build: dict, manifest: dict) -> tuple[Path, dict]:
    inventory_path = _canonical(build["classInventoryPath"], "CLASS_INVENTORY_PATH")
    classes = _canonical(manifest["classesRoot"], "CLASSES_ROOT")
    _need(inventory_path == stage / "class-inventory.json"
         and classes == stage / "classes", "CLASS_INVENTORY_PATH_BINDING")
    raw = _read_file(inventory_path, MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY")
    _need(_sha(raw) == build["classInventorySha256"], "CLASS_INVENTORY_HASH")
    inventory = _strict_document(raw, MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY")
    expected_inventory, expected_raw = _inventory_class_tree(classes,
                                                               build["sourceManifestSha256"])
    _need(raw == expected_raw and inventory == expected_inventory,
         "CLASS_TREE_BINDING")
    _need(_is_int(build["classCount"], 1, MAX_CLASS_COUNT)
         and _is_int(build["classBytes"], 1, MAX_CLASS_BYTES)
         and build["classCount"] == inventory["classCount"]
         and build["classBytes"] == inventory["totalBytes"], "CLASS_COUNT_BINDING")
    return classes, inventory


def _expected_run_manifest(input_manifest: dict, build: dict) -> dict:
    return {
        "schema": RUN_SCHEMA,
        "sourceParent": input_manifest["sourceParent"],
        "parentManifestPath": input_manifest["parentManifestPath"],
        "parentManifestSha256": input_manifest["parentManifestSha256"],
        "parentHelperSha256": input_manifest["parentHelperSha256"],
        "planSha256": input_manifest["planSha256"],
        "compilerSourceCount": input_manifest["compilerSourceCount"],
        "compilerListPath": input_manifest["compilerListPath"],
        "compilerListBytes": input_manifest["compilerListBytes"],
        "compilerListSha256": input_manifest["compilerListSha256"],
        "compilerRequestPath": input_manifest["compilerRequestPath"],
        "compilerRequestSha256": input_manifest["compilerRequestSha256"],
        "classesRoot": input_manifest["classesRoot"],
        "emptySourcepath": input_manifest["emptySourcepath"],
        "sources": input_manifest["sources"],
        "tools": input_manifest["tools"],
        "wire": input_manifest["wire"],
        "build": build,
    }


def finalize(stage_value: str, call_value: str, source_manifest_sha: str) -> dict:
    stage = _expected_stage(stage_value, require_empty=False)
    _require_pre_finalize_stage(stage)
    parent_reader, parent_context = _load_parent()
    input_manifest, identity, request_raw, source_manifest_raw = _validate_input_manifest(
        stage, parent_reader, parent_context, source_manifest_sha)
    request = _strict_document(request_raw, MAX_SMALL_JSON_BYTES, "COMPILER_REQUEST")
    call_dir = _canonical(call_value, "COMPILE_CALL_PATH")
    parent_path, parent_raw, _parent_doc, process = _validate_compile_call(
        call_dir, stage, parent_reader, parent_context, request)
    classes = _canonical(input_manifest["classesRoot"], "CLASSES_ROOT")
    inventory, inventory_raw = _inventory_class_tree(classes, source_manifest_sha)
    inventory_path = stage / "class-inventory.json"
    _write_new(inventory_path, inventory_raw, MAX_CLASS_INVENTORY_BYTES,
               "CLASS_INVENTORY")
    build = {
        "sourceManifestPath": str(stage / "source-manifest.json"),
        "sourceManifestSha256": source_manifest_sha,
        "compileCallDirectory": str(call_dir),
        "compileParentPath": str(parent_path),
        "compileParentSha256": _sha(parent_raw),
        "classInventoryPath": str(inventory_path),
        "classInventorySha256": _sha(inventory_raw),
        "classCount": inventory["classCount"],
        "classBytes": inventory["totalBytes"],
    }
    run_manifest = _expected_run_manifest(input_manifest, build)
    run_raw = _canonical_json(run_manifest)
    _need(len(run_raw) <= MAX_MANIFEST_BYTES, "RUN_MANIFEST_SIZE")
    run_manifest_path = stage / "run-manifest.json"
    _write_new(run_manifest_path, run_raw, MAX_MANIFEST_BYTES,
               "RUN_MANIFEST")
    return {
        "schema": FINALIZED_SCHEMA,
        "status": "FINALIZED_SOURCE_ONLY",
        "runManifestPath": str(run_manifest_path),
        "runManifestSha256": _sha(run_raw),
        "sourceManifestSha256": source_manifest_sha,
        "compilerSourceCount": COMPILER_SOURCE_COUNT,
        "classCount": inventory["classCount"],
        "classBytes": inventory["totalBytes"],
        "compileExitCode": process["ExitCode"],
    }


def validate_run_manifest(path_value: str | Path, expected_sha: str) -> dict:
    """Authenticate final source/tool/compile/class closure for the reader."""
    _need(sys.flags.isolated and sys.dont_write_bytecode, "PYTHON_ISOLATION")
    path = _canonical(path_value, "RUN_MANIFEST_PATH")
    _need(path.name == "run-manifest.json", "RUN_MANIFEST_LOCATION")
    stage = path.parent
    _need(stage.parent == _task_root() and STAGE_NAME.fullmatch(stage.name) is not None,
         "STAGE_LOCATION")
    _lstat_directory(stage, "STAGE_DIRECTORY")
    raw = _read_file(path, MAX_MANIFEST_BYTES, "RUN_MANIFEST")
    _need(type(expected_sha) is str and HEX.fullmatch(expected_sha) is not None
         and _sha(raw) == expected_sha, "RUN_MANIFEST_HASH")
    run_manifest = _strict_document(raw, MAX_MANIFEST_BYTES, "RUN_MANIFEST")
    expected_top = {
        "schema", "sourceParent", "parentManifestPath", "parentManifestSha256",
        "parentHelperSha256", "planSha256", "compilerSourceCount", "compilerListPath",
        "compilerListBytes", "compilerListSha256", "compilerRequestPath",
        "compilerRequestSha256", "classesRoot", "emptySourcepath", "sources", "tools",
        "wire", "build",
    }
    _need(type(run_manifest) is dict and set(run_manifest) == expected_top
         and run_manifest["schema"] == RUN_SCHEMA, "RUN_MANIFEST_SCHEMA")

    parent_reader, parent_context = _load_parent()
    manifest_stage = _canonical(run_manifest["compilerListPath"], "COMPILER_LIST_PATH").parent
    _need(manifest_stage == stage and stage.parent == _task_root()
         and STAGE_NAME.fullmatch(stage.name) is not None,
         "STAGE_LOCATION")
    input_manifest, identity, request_raw, source_manifest_raw = _validate_input_manifest(
        stage, parent_reader, parent_context,
        run_manifest.get("build", {}).get("sourceManifestSha256")
        if type(run_manifest.get("build")) is dict else "")
    expected_build_keys = {
        "sourceManifestPath", "sourceManifestSha256", "compileCallDirectory",
        "compileParentPath", "compileParentSha256", "classInventoryPath",
        "classInventorySha256", "classCount", "classBytes",
    }
    build = run_manifest["build"]
    _need(type(build) is dict and set(build) == expected_build_keys,
         "RUN_BUILD_SCHEMA")
    _need(build["sourceManifestPath"] == str(stage / "source-manifest.json")
         and build["sourceManifestSha256"] == _sha(source_manifest_raw),
         "SOURCE_MANIFEST_BINDING")
    expected_run = _expected_run_manifest(input_manifest, build)
    _need(_same_json(run_manifest, expected_run), "RUN_MANIFEST_IDENTITY")

    expected_stage_entries = {
        "classes", "empty-sourcepath", "compiler-sources.txt", "compiler-request.json",
        "source-manifest.json", "class-inventory.json", "run-manifest.json",
    }
    try:
        names = [entry.name for entry in stage.iterdir()]
    except OSError:
        raise BuildInputError("STAGE_LIST") from None
    _need(len(names) == len(expected_stage_entries)
         and set(names) == expected_stage_entries, "STAGE_CLOSURE")

    request = _strict_document(request_raw, MAX_SMALL_JSON_BYTES, "COMPILER_REQUEST")
    expected_call = _canonical(build["compileCallDirectory"], "COMPILE_CALL_PATH")
    call_match = COMPILE_CALL_NAME.fullmatch(expected_call.name)
    _need(expected_call.parent == _task_root() and call_match is not None
         and int(call_match.group(1)) == _stage_revision(stage), "COMPILE_CALL_LOCATION")
    parent_path, parent_raw, _parent_doc, _process = _validate_compile_call(
        expected_call, stage, parent_reader, parent_context, request)
    _need(str(parent_path) == build["compileParentPath"]
         and _sha(parent_raw) == build["compileParentSha256"],
         "COMPILE_PARENT_BINDING")
    classes, inventory = _check_class_inventory(stage, build, run_manifest)

    # Re-read the authenticated parent tool helper and every new/derived input
    # after the expensive closure work. The reader may call recheck_inputs again
    # after consuming the native operation.
    _need(_sha(_read_file(PARENT_HELPER_PATH, 2 * 1024 * 1024,
                         "PARENT_HELPER_RECHECK")) == PARENT_HELPER_SHA256,
         "PARENT_HELPER_CHANGED")
    _need(_sha(_check_source_file(TASK_SOURCE, 4 * 1024 * 1024,
                                  "NATIVE_SOURCE")) == run_manifest["sources"][-1]["sha256"],
         "NATIVE_SOURCE_CHANGED")
    _need(_sha(_check_source_file(TASK_WIRE, 64 * 1024, "WIRE"))
         == run_manifest["wire"]["sha256"], "WIRE_CHANGED")
    _need(_sha(_check_plan()) == PLAN_SHA256, "PLAN_CHANGED")
    _need(_read_file(path, MAX_MANIFEST_BYTES, "RUN_MANIFEST_RECHECK") == raw,
         "RUN_MANIFEST_CHANGED")
    return {
        "sourceSha": expected_sha,
        "manifest": run_manifest,
        "path": path,
        "sourcePaths": [row["path"] for row in run_manifest["sources"]],
        "sources": run_manifest["sources"],
        "tools": run_manifest["tools"],
        "wire": run_manifest["wire"],
        "wireRaw": identity["wireRaw"],
        "baseReader": parent_reader,
        "parentContext": parent_context,
        "parentAuditor": parent_context["parentAuditor"],
        "parentInputContext": parent_context["parentInputContext"],
        "p07Reader": parent_context["p07Reader"],
        "classesRoot": classes,
        "java": parent_context["java"],
        "javac": parent_context["javac"],
        "jars": parent_context["jars"],
        "processModule": parent_context["processModule"],
        "parentManifestPath": _canonical(PARENT_MANIFEST_PATH, "PARENT_MANIFEST_PATH"),
        "parentManifestSha256": PARENT_MANIFEST_SHA256,
        "sourceManifestPath": stage / "source-manifest.json",
        "sourceManifestSha256": build["sourceManifestSha256"],
        "classInventory": inventory,
    }


def recheck_inputs(context: dict) -> dict:
    _need(type(context) is dict and isinstance(context.get("path"), Path)
         and type(context.get("sourceSha")) is str, "RECHECK_CONTEXT")
    refreshed = validate_run_manifest(context["path"], context["sourceSha"])
    _need(refreshed["sourceSha"] == context["sourceSha"]
         and refreshed["sourcePaths"] == context["sourcePaths"], "RECHECK_BINDING")
    return refreshed


def audit(path_value: str, expected_sha: str) -> dict:
    context = validate_run_manifest(path_value, expected_sha)
    return {
        "schema": AUDIT_SCHEMA,
        "status": "PASS_INITIAL_OUTLET_SOURCE_ONLY",
        "sourceSha256": context["sourceSha"],
        "compilerSourceCount": context["manifest"]["compilerSourceCount"],
        "classCount": context["classInventory"]["classCount"],
        "classBytes": context["classInventory"]["totalBytes"],
    }


def _emit(value) -> None:
    sys.stdout.write(json.dumps(value, ensure_ascii=True, sort_keys=True,
                                separators=(",", ":"), allow_nan=False) + "\n")
    sys.stdout.flush()


def _safe_exception_detail(failure: Exception) -> str:
    kind = re.sub(r"[^A-Za-z0-9_]", "_", type(failure).__name__)[:64] or "Exception"
    try:
        reason = str(failure)
    except Exception:
        reason = "<unavailable>"
    reason = reason[:512]
    reason = re.sub(r"(?i)\b[A-Z]:[\\/][^\s,;)]*", "<path>", reason)
    reason = re.sub(r"\\\\[^\s,;)]*", "<path>", reason)
    reason = reason.replace("\r", " ").replace("\n", " ")
    reason = re.sub(r"[^A-Za-z0-9_.:=<>/ -]", "_", reason)
    return (kind + ":" + reason)[:640]


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="build_inputs.py")
    sub = parser.add_subparsers(dest="command", required=True)
    prepare_parser = sub.add_parser("prepare")
    prepare_parser.add_argument("stage")
    finalize_parser = sub.add_parser("finalize")
    finalize_parser.add_argument("stage")
    finalize_parser.add_argument("compile_call_directory")
    finalize_parser.add_argument("source_manifest_sha256")
    audit_parser = sub.add_parser("audit")
    audit_parser.add_argument("run_manifest")
    audit_parser.add_argument("run_manifest_sha256")
    args = parser.parse_args(argv)
    try:
        if args.command == "prepare":
            result = prepare(args.stage)
        elif args.command == "finalize":
            result = finalize(args.stage, args.compile_call_directory,
                              args.source_manifest_sha256)
        else:
            result = audit(args.run_manifest, args.run_manifest_sha256)
        _emit(result)
        return 0
    except BuildInputError as failure:
        sys.stderr.write("INITIAL_OUTLET_BUILD_FAIL " + failure.code + "\n")
        sys.stderr.flush()
        return 2
    except Exception as failure:
        detail = _safe_exception_detail(failure)
        sys.stderr.write("INITIAL_OUTLET_BUILD_FAIL UNEXPECTED " + detail + "\n")
        sys.stderr.flush()
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
