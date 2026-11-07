import sys

if not sys.flags.isolated or not sys.dont_write_bytecode:
    sys.stderr.write("FAILED_FIRST_BUILD_FAIL PYTHON_ISOLATION\n")
    raise SystemExit(2)

import argparse
import hashlib
import json
import math
import os
import re
import stat
import tempfile
import types
from pathlib import Path


TASK_NAME = "tsj-q100-failed-first-b9a0968471ab4187a791fc7f71bd3274"
PARENT_NAME = "tsj-q100-initial-outlets-7377fc69eb384d7dbef0140fbe649b43"
PARENT_HELPER_SHA256 = "87951c6c464fe4a39ae0b908017a7e857036e543c26b5332ee683839440c4d76"
PARENT_MANIFEST_SHA256 = "36e80ef3be066a4bc146adb193b90fd7da9bc33b9f5288b154d06f0af13593d9"
SOURCE_PARENT = "18e763bbee0ca32cc42e02a838e2191d6ac436cd"
PARENT_SOURCE_PARENT = "474c0efa072be8cceed019c95a8079a6181bc680"
PLAN_SHA256 = "77ff9bbbea59afa6bf55aa94078cdf7a8f81f1959dec8396fe3c0b0d90563771"

INPUT_SCHEMA = "tsj-failed-first-source-input-v1"
RUN_SCHEMA = "tsj-failed-first-source-v1"
CLASS_SCHEMA = "tsj-failed-first-classes-v1"
PREPARED_SCHEMA = "tsj-failed-first-prepared-v1"
FINALIZED_SCHEMA = "tsj-failed-first-finalized-v1"
AUDIT_SCHEMA = "tsj-failed-first-source-audit-v1"

PARENT_SOURCE_COUNT = 1088
COMPILER_SOURCE_COUNT = 1090
PARENT_CLASS_COUNT = 4120
COMPILE_LIMIT_MS = 60_000
MAX_MANIFEST_BYTES = 16 * 1024 * 1024
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_WIRE_BYTES = 64 * 1024
MAX_CLASS_BYTES = 256 * 1024 * 1024
MAX_CLASS_COUNT = 10_000
MAX_CLASS_INVENTORY_BYTES = 4 * 1024 * 1024
MAX_COMPILER_LIST_BYTES = 2 * 1024 * 1024
MAX_SMALL_JSON_BYTES = 256 * 1024
COPY_CHUNK_BYTES = 4096
HEX = re.compile(r"[0-9a-f]{64}\Z")
STAGE_NAME = re.compile(r"j8-failed-first-r([1-9][0-9]*)\Z")
COMPILE_CALL_NAME = re.compile(r"compile-call-r([1-9][0-9]*)\Z")

INPUT_KEYS = {
    "schema", "sourceParent", "parentManifestPath", "parentManifestSha256",
    "parentHelperSha256", "planSha256", "compilerSourceCount", "compilerListPath",
    "compilerListBytes", "compilerListSha256", "compilerRequestPath",
    "compilerRequestSha256", "classesRoot", "emptySourcepath", "sources", "tools", "wire",
}
RUN_KEYS = {
    "schema", "sourceParent", "parentManifestPath", "parentManifestSha256",
    "parentHelperSha256", "planSha256", "compilerSourceCount", "compilerListPath",
    "compilerListBytes", "compilerListSha256", "compilerRequestPath",
    "compilerRequestSha256", "classesRoot", "emptySourcepath", "sources", "tools", "wire",
    "build",
}
BUILD_KEYS = {
    "sourceManifestPath", "sourceManifestSha256", "compileCallDirectory",
    "compileParentPath", "compileParentSha256", "classInventoryPath",
    "classInventorySha256", "classCount", "classBytes",
}


class BuildInputError(ValueError):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def _need(condition, code):
    if not condition:
        raise BuildInputError(code)


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _is_int(value, low=0, high=2**63 - 1):
    return type(value) is int and low <= value <= high


def _is_reparse(info):
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400)


def _stable_stat(info):
    return (info.st_dev, info.st_ino, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None))


def _file_identity(info):
    modified = getattr(info, "st_mtime_ns", None)
    if modified is None:
        modified = info.st_mtime
    birth = getattr(info, "st_birthtime_ns", None)
    if birth is None:
        birth = getattr(info, "st_birthtime", None)
    return (info.st_dev, info.st_ino, info.st_size, modified, birth,
            stat.S_IFMT(info.st_mode))


def _same_file_identity(first, second):
    return _file_identity(first) == _file_identity(second)


def _canonical(path_value, code, require_exists=True):
    _need(type(path_value) is str or isinstance(path_value, Path), code + "_TYPE")
    spelling = str(path_value)
    try:
        original = Path(spelling)
        resolved = original.resolve(strict=require_exists)
    except (OSError, RuntimeError, ValueError):
        raise BuildInputError(code) from None
    _need(original.is_absolute() and str(original) == spelling, code + "_SPELLING")
    _need(str(resolved) == spelling, code + "_CANONICAL")
    for parent in (resolved, *resolved.parents):
        try:
            info = parent.lstat()
        except FileNotFoundError:
            continue
        except OSError:
            raise BuildInputError(code + "_STAT") from None
        _need(not _is_reparse(info), code + "_REPARSE")
    return resolved


def _lstat_regular(path, code):
    try:
        info = path.lstat()
    except OSError:
        raise BuildInputError(code) from None
    _need(stat.S_ISREG(info.st_mode) and not _is_reparse(info), code)
    return info


def _lstat_directory(path, code):
    try:
        info = path.lstat()
    except OSError:
        raise BuildInputError(code) from None
    _need(stat.S_ISDIR(info.st_mode) and not _is_reparse(info), code)
    return info


def _read_file(path, limit, code):
    before = _lstat_regular(path, code)
    _need(0 <= before.st_size <= limit, code + "_SIZE")
    flags = os.O_RDONLY
    if hasattr(os, "O_BINARY"):
        flags |= os.O_BINARY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = None
    try:
        descriptor = os.open(str(path), flags)
        opened = os.fstat(descriptor)
        _need(stat.S_ISREG(opened.st_mode) and not _is_reparse(opened), code + "_OPEN_TYPE")
        _need(_same_file_identity(before, opened), code + "_OPEN_CHANGED")
        chunks = []
        total = 0
        with os.fdopen(descriptor, "rb", closefd=True) as stream:
            descriptor = None
            while True:
                chunk = stream.read(COPY_CHUNK_BYTES)
                if not chunk:
                    break
                total += len(chunk)
                _need(total <= limit, code + "_SIZE")
                chunks.append(chunk)
            opened_after = os.fstat(stream.fileno())
        after = path.lstat()
    except BuildInputError:
        raise
    except OSError:
        raise BuildInputError(code + "_READ") from None
    finally:
        if descriptor is not None:
            os.close(descriptor)
    _need(not _is_reparse(after) and stat.S_ISREG(after.st_mode)
         and _stable_stat(before) == _stable_stat(after)
         and _stable_stat(opened_after) == _stable_stat(opened)
         and _same_file_identity(after, opened_after)
         and total == after.st_size, code + "_CHANGED")
    return b"".join(chunks)


def _file_sha(path, limit, code):
    before = _lstat_regular(path, code)
    _need(0 < before.st_size <= limit, code + "_SIZE")
    flags = os.O_RDONLY
    if hasattr(os, "O_BINARY"):
        flags |= os.O_BINARY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = None
    try:
        descriptor = os.open(str(path), flags)
        opened = os.fstat(descriptor)
        _need(stat.S_ISREG(opened.st_mode) and not _is_reparse(opened)
             and _same_file_identity(before, opened), code + "_OPEN_CHANGED")
        digest = hashlib.sha256()
        total = 0
        with os.fdopen(descriptor, "rb", closefd=True) as stream:
            descriptor = None
            while True:
                chunk = stream.read(COPY_CHUNK_BYTES)
                if not chunk:
                    break
                total += len(chunk)
                _need(total <= limit, code + "_SIZE")
                digest.update(chunk)
            opened_after = os.fstat(stream.fileno())
        after = path.lstat()
    except BuildInputError:
        raise
    except OSError:
        raise BuildInputError(code + "_READ") from None
    finally:
        if descriptor is not None:
            os.close(descriptor)
    _need(not _is_reparse(after) and stat.S_ISREG(after.st_mode)
         and _stable_stat(before) == _stable_stat(after)
         and _stable_stat(opened_after) == _stable_stat(opened)
         and _same_file_identity(after, opened_after)
         and total == after.st_size, code + "_CHANGED")
    return total, digest.hexdigest()


def _inside(path, root, code):
    try:
        path.relative_to(root)
    except ValueError:
        raise BuildInputError(code) from None


def _pairs(rows):
    result = {}
    for key, value in rows:
        if key in result:
            raise BuildInputError("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _parse_float(text):
    value = float(text)
    _need(math.isfinite(value), "JSON_NONFINITE")
    return value


def _reject_constant(_text):
    raise BuildInputError("JSON_NONFINITE")


def _json_load(raw, limit, code):
    _need(type(raw) is bytes and 0 < len(raw) <= limit, code + "_SIZE")
    _need(not raw.startswith(b"\xef\xbb\xbf"), code + "_BOM")
    try:
        return json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=_pairs,
                          parse_float=_parse_float, parse_constant=_reject_constant)
    except BuildInputError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
        raise BuildInputError(code + "_JSON") from None


def _canonical_json(value):
    try:
        return (json.dumps(value, ensure_ascii=True, sort_keys=True,
                           separators=(",", ":"), allow_nan=False) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError):
        raise BuildInputError("JSON_SERIALIZE") from None


def _strict_document(raw, limit, code):
    _need(raw.endswith(b"\n") and not raw.endswith(b"\n\n") and b"\r" not in raw,
         code + "_FRAMING")
    value = _json_load(raw[:-1], limit, code)
    _need(raw == _canonical_json(value), code + "_CANONICAL")
    return value


def _write_new(path, raw, limit, code):
    _need(type(raw) is bytes and len(raw) <= limit, code + "_SIZE")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_BINARY"):
        flags |= os.O_BINARY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(str(path), flags, 0o600)
    except OSError:
        raise BuildInputError(code + "_CREATE_NEW") from None
    try:
        stream = os.fdopen(descriptor, "wb", closefd=True)
        descriptor = None
        with stream:
            view = memoryview(raw)
            offset = 0
            while offset < len(view):
                end = min(offset + COPY_CHUNK_BYTES, len(view))
                count = stream.write(view[offset:end])
                _need(count is not None and count > 0, code + "_WRITE")
                offset += count
            stream.flush()
            os.fsync(stream.fileno())
    except BuildInputError:
        raise
    except OSError:
        raise BuildInputError(code + "_WRITE") from None
    finally:
        if descriptor is not None:
            os.close(descriptor)
    _need(_read_file(path, limit, code + "_VERIFY") == raw, code + "_VERIFY")


def _create_directory(path, code):
    try:
        os.mkdir(str(path))
    except OSError:
        raise BuildInputError(code + "_CREATE_NEW") from None
    _lstat_directory(path, code)


def _directory_names(path, code):
    _lstat_directory(path, code)
    try:
        with os.scandir(path) as entries:
            names = [entry.name for entry in entries]
    except OSError:
        raise BuildInputError(code + "_LIST") from None
    _need(all(name not in (".", "..") and "/" not in name and "\\" not in name
              for name in names), code + "_ENTRY_NAME")
    return names


def _task_root():
    expected = _canonical(Path(tempfile.gettempdir()) / TASK_NAME, "TASK_ROOT")
    script = _canonical(__file__, "SELF_PATH")
    _need(script.parent == expected, "TASK_ROOT_BINDING")
    _lstat_directory(expected, "TASK_ROOT_DIRECTORY")
    return expected


def _parent_paths():
    root = _canonical(Path(tempfile.gettempdir()) / PARENT_NAME, "PARENT_ROOT")
    helper = _canonical(root / "build_inputs.py", "PARENT_HELPER_PATH")
    manifest = _canonical(root / "j8-initial-r2" / "run-manifest.json",
                          "PARENT_MANIFEST_PATH")
    return root, helper, manifest


def _load_parent():
    _need(sys.flags.isolated and sys.dont_write_bytecode, "PYTHON_ISOLATION")
    _task_root()
    _parent_root, helper_path, manifest_path = _parent_paths()
    helper_raw = _read_file(helper_path, 2 * 1024 * 1024, "PARENT_HELPER")
    _need(_sha(helper_raw) == PARENT_HELPER_SHA256, "PARENT_HELPER_HASH")
    module = types.ModuleType("_authenticated_initial_outlet_build_inputs")
    module.__file__ = str(helper_path)
    try:
        exec(compile(helper_raw, str(helper_path), "exec"), module.__dict__)
        context = module.validate_run_manifest(str(manifest_path), PARENT_MANIFEST_SHA256)
        context = module.recheck_inputs(context)
    except module.BuildInputError as failure:
        code = getattr(failure, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            raise BuildInputError("PARENT_" + code) from None
        raise BuildInputError("PARENT_VALIDATION") from None
    except (Exception, SystemExit) as failure:
        kind = re.sub(r"[^A-Za-z0-9_]", "_", type(failure).__name__).upper()[:48]
        raise BuildInputError("PARENT_INTERNAL_" + (kind or "ERROR")) from None
    _need(_sha(_read_file(helper_path, 2 * 1024 * 1024, "PARENT_HELPER_RECHECK"))
         == PARENT_HELPER_SHA256, "PARENT_HELPER_CHANGED")
    _need(type(context) is dict and context.get("sourceSha") == PARENT_MANIFEST_SHA256
         and context.get("path") == manifest_path, "PARENT_CONTEXT")
    manifest = context.get("manifest")
    source_paths = context.get("sourcePaths")
    source_rows = context.get("sources")
    _need(type(manifest) is dict and manifest.get("schema") == "tsj-initial-outlet-source-v1"
         and manifest.get("sourceParent") == PARENT_SOURCE_PARENT, "PARENT_MANIFEST_SCHEMA")
    _need(manifest.get("compilerSourceCount") == PARENT_SOURCE_COUNT
         and type(source_paths) is list and len(source_paths) == PARENT_SOURCE_COUNT
         and type(source_rows) is list and len(source_rows) == PARENT_SOURCE_COUNT
         and manifest.get("sources") == source_rows, "PARENT_SOURCE_CENSUS")
    _need(type(context.get("baseReader")) is types.ModuleType
         and context.get("p07Reader") is not None
         and isinstance(context.get("classesRoot"), Path)
         and type(context.get("tools")) is list and len(context["tools"]) == 4,
         "PARENT_CONTEXT_ROLES")
    inventory = context.get("classInventory")
    _need(type(inventory) is dict and inventory.get("schema") == "tsj-initial-outlet-classes-v1"
         and _is_int(inventory.get("classCount"), 1, MAX_CLASS_COUNT)
         and inventory["classCount"] == PARENT_CLASS_COUNT
         and type(inventory.get("entries")) is list
         and len(inventory["entries"]) == PARENT_CLASS_COUNT,
         "PARENT_CLASS_INVENTORY")
    seen = set()
    for index, row in enumerate(source_rows):
        _need(type(row) is dict and set(row) == {"path", "bytes", "sha256"}
             and row.get("path") == source_paths[index]
             and _is_int(row.get("bytes"), 1, MAX_CLASS_BYTES)
             and type(row.get("sha256")) is str and HEX.fullmatch(row["sha256"]),
             "PARENT_SOURCE_ROW")
        path = _canonical(row["path"], "PARENT_SOURCE_PATH")
        key = os.path.normcase(str(path))
        _need(key not in seen, "PARENT_SOURCE_DUPLICATE")
        seen.add(key)
    _need(context.get("tools") == manifest.get("tools"), "PARENT_TOOLS_BINDING")
    return module, context


def _read_source(path, limit, code):
    raw = _read_file(path, limit, code)
    _need(raw.endswith(b"\n") and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"),
         code + "_FRAMING")
    try:
        raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise BuildInputError(code + "_UTF8") from None
    return raw


def _source_identity(parent_context):
    root = _task_root()
    plan = _canonical(root / "PLAN_R1.md", "PLAN_PATH")
    plan_raw = _read_file(plan, 128 * 1024, "PLAN")
    _need(_sha(plan_raw) == PLAN_SHA256, "PLAN_HASH")
    source_dir = _canonical(root / "source", "SOURCE_DIRECTORY")
    _lstat_directory(source_dir, "SOURCE_DIRECTORY")
    expected_source_names = {
        "FailedFirstRoutingStudy.java", "FailedFirstFixtures.java", "WIRE_R1.md",
    }
    names = _directory_names(source_dir, "SOURCE_DIRECTORY")
    _need(len(names) == len(expected_source_names) and set(names) == expected_source_names,
         "SOURCE_DIRECTORY_CLOSURE")
    native_path = _canonical(source_dir / "FailedFirstRoutingStudy.java", "NATIVE_SOURCE_PATH")
    fixture_path = _canonical(source_dir / "FailedFirstFixtures.java", "FIXTURE_SOURCE_PATH")
    wire_path = _canonical(source_dir / "WIRE_R1.md", "WIRE_PATH")
    native_raw = _read_source(native_path, MAX_SOURCE_BYTES, "NATIVE_SOURCE")
    fixture_raw = _read_source(fixture_path, MAX_SOURCE_BYTES, "FIXTURE_SOURCE")
    wire_raw = _read_source(wire_path, MAX_WIRE_BYTES, "WIRE")

    parent_manifest = parent_context["manifest"]
    parent_rows = parent_context["sources"]
    parent_paths = parent_context["sourcePaths"]
    _need(len(parent_rows) == len(parent_paths) == PARENT_SOURCE_COUNT,
         "PARENT_SOURCE_CENSUS")
    source_rows = [dict(row) for row in parent_rows]
    source_rows.extend([
        {"path": str(native_path), "bytes": len(native_raw), "sha256": _sha(native_raw)},
        {"path": str(fixture_path), "bytes": len(fixture_raw), "sha256": _sha(fixture_raw)},
    ])
    source_paths = list(parent_paths) + [str(native_path), str(fixture_path)]
    _need(len(source_rows) == len(source_paths) == COMPILER_SOURCE_COUNT,
         "COMPILER_SOURCE_COUNT")
    seen = set()
    for path_value in source_paths:
        _need(type(path_value) is str and "\n" not in path_value and "\r" not in path_value
             and '"' not in path_value, "COMPILER_PATH_CHARACTER")
        canonical = _canonical(path_value, "COMPILER_SOURCE_PATH")
        key = os.path.normcase(str(canonical))
        _need(key not in seen, "COMPILER_SOURCE_DUPLICATE")
        seen.add(key)
    compiler_list = "".join('"' + value.replace("\\", "/") + '"\n'
                            for value in source_paths).encode("utf-8")
    _need(len(compiler_list) <= MAX_COMPILER_LIST_BYTES, "COMPILER_LIST_SIZE")

    manifest_tools = parent_manifest.get("tools")
    javac = parent_context.get("javac")
    jars = parent_context.get("jars")
    _need(type(manifest_tools) is list and len(manifest_tools) == 4
         and type(javac) is dict and type(javac.get("path")) is str
         and type(jars) is dict
         and set(jars) == {"gwt-user-2.7.0.jar", "gwt-dev-2.7.0.jar"},
         "PARENT_TOOL_SET")
    return {
        "planRaw": plan_raw,
        "nativeRaw": native_raw,
        "fixtureRaw": fixture_raw,
        "wireRaw": wire_raw,
        "nativePath": native_path,
        "fixturePath": fixture_path,
        "wirePath": wire_path,
        "sourcePaths": source_paths,
        "sources": source_rows,
        "tools": [dict(row) for row in manifest_tools],
        "compilerList": compiler_list,
        "javac": javac,
        "jars": jars,
        "wire": {"path": str(wire_path), "bytes": len(wire_raw), "sha256": _sha(wire_raw)},
    }


def _revision(path, pattern, code):
    match = pattern.fullmatch(path.name)
    _need(match is not None, code)
    revision = int(match.group(1))
    _need(1 <= revision <= 9999, code)
    return revision


def _expected_stage(stage_value, require_empty):
    root = _task_root()
    stage = _canonical(stage_value, "STAGE_PATH")
    _need(stage.parent == root, "STAGE_LOCATION")
    _revision(stage, STAGE_NAME, "STAGE_REVISION")
    _lstat_directory(stage, "STAGE_DIRECTORY")
    if require_empty:
        _need(not _directory_names(stage, "STAGE_DIRECTORY"), "STAGE_NOT_EMPTY")
    return stage


def _expected_compile_call(call_value, stage):
    root = _task_root()
    call_dir = _canonical(call_value, "COMPILE_CALL_PATH")
    _need(call_dir.parent == root, "COMPILE_CALL_LOCATION")
    stage_revision = _revision(stage, STAGE_NAME, "STAGE_REVISION")
    call_revision = _revision(call_dir, COMPILE_CALL_NAME, "COMPILE_CALL_REVISION")
    _need(call_revision == stage_revision, "COMPILE_CALL_REVISION_MISMATCH")
    _lstat_directory(call_dir, "COMPILE_CALL_DIRECTORY")
    return call_dir


def _compile_request(stage, parent_context):
    classes = stage / "classes"
    empty_sourcepath = stage / "empty-sourcepath"
    javac = parent_context.get("javac")
    jars = parent_context.get("jars")
    _need(type(javac) is dict and type(javac.get("path")) is str
         and type(jars) is dict
         and set(jars) == {"gwt-user-2.7.0.jar", "gwt-dev-2.7.0.jar"},
         "PARENT_TOOL_SET")
    classpath = ";".join((str(classes), jars["gwt-user-2.7.0.jar"]["path"],
                           jars["gwt-dev-2.7.0.jar"]["path"]))
    arguments = ["-source", "7", "-target", "7", "-encoding", "UTF-8",
                 "-classpath", classpath, "-sourcepath", str(empty_sourcepath),
                 "-d", str(classes), "@" + str(stage / "compiler-sources.txt")]
    return {"FilePath": javac["path"], "Arguments": arguments, "LimitMs": COMPILE_LIMIT_MS}


def _input_manifest(stage, identity, parent_context):
    request = _compile_request(stage, parent_context)
    request_raw = _canonical_json(request)
    value = {
        "schema": INPUT_SCHEMA,
        "sourceParent": SOURCE_PARENT,
        "parentManifestPath": str(_parent_paths()[2]),
        "parentManifestSha256": PARENT_MANIFEST_SHA256,
        "parentHelperSha256": PARENT_HELPER_SHA256,
        "planSha256": PLAN_SHA256,
        "compilerSourceCount": COMPILER_SOURCE_COUNT,
        "compilerListPath": str(stage / "compiler-sources.txt"),
        "compilerListBytes": len(identity["compilerList"]),
        "compilerListSha256": _sha(identity["compilerList"]),
        "compilerRequestPath": str(stage / "compiler-request.json"),
        "compilerRequestSha256": _sha(request_raw),
        "classesRoot": str(stage / "classes"),
        "emptySourcepath": str(stage / "empty-sourcepath"),
        "sources": identity["sources"],
        "tools": identity["tools"],
        "wire": identity["wire"],
    }
    _need(set(value) == INPUT_KEYS, "INPUT_MANIFEST_SCHEMA")
    return value, request_raw


def prepare(stage_value):
    stage = _expected_stage(stage_value, require_empty=True)
    parent_module, parent_context = _load_parent()
    identity = _source_identity(parent_context)
    input_manifest, request_raw = _input_manifest(stage, identity, parent_context)
    manifest_raw = _canonical_json(input_manifest)
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


def _validate_source_input(stage, parent_context, expected_sha):
    path = _canonical(stage / "source-manifest.json", "SOURCE_MANIFEST_PATH")
    raw = _read_file(path, MAX_MANIFEST_BYTES, "SOURCE_MANIFEST")
    _need(type(expected_sha) is str and HEX.fullmatch(expected_sha) is not None
         and _sha(raw) == expected_sha, "SOURCE_MANIFEST_HASH")
    value = _strict_document(raw, MAX_MANIFEST_BYTES, "SOURCE_MANIFEST")
    _need(type(value) is dict and set(value) == INPUT_KEYS
         and value.get("schema") == INPUT_SCHEMA, "SOURCE_MANIFEST_SCHEMA")
    identity = _source_identity(parent_context)
    expected, request_raw = _input_manifest(stage, identity, parent_context)
    _need(raw == _canonical_json(expected), "SOURCE_MANIFEST_IDENTITY")
    compiler_list = _read_file(stage / "compiler-sources.txt",
                               MAX_COMPILER_LIST_BYTES, "COMPILER_LIST")
    _need(compiler_list == identity["compilerList"], "COMPILER_LIST_BINDING")
    request_path = _canonical(value["compilerRequestPath"], "COMPILER_REQUEST_PATH")
    _need(request_path == stage / "compiler-request.json", "COMPILER_REQUEST_LOCATION")
    request_disk = _read_file(request_path, MAX_SMALL_JSON_BYTES, "COMPILER_REQUEST")
    _need(request_disk == request_raw and _sha(request_disk) == value["compilerRequestSha256"],
         "COMPILER_REQUEST_BINDING")
    return value, identity, request_raw, raw


def _validate_compile_call(call_value, stage, parent_context, request):
    call_dir = _expected_compile_call(call_value, stage)
    parent_path = _canonical(call_dir / "parent.json", "COMPILE_PARENT_PATH")
    request_path = _canonical(call_dir / "request.json", "COMPILE_REQUEST_PATH")
    process_path = _canonical(call_dir / "process.json", "COMPILE_PROCESS_PATH")
    parent_raw = _read_file(parent_path, MAX_SMALL_JSON_BYTES, "COMPILE_PARENT")
    request_raw = _read_file(request_path, MAX_SMALL_JSON_BYTES, "COMPILE_REQUEST_RECEIPT")
    process_raw = _read_file(process_path, MAX_SMALL_JSON_BYTES, "COMPILE_PROCESS_RECEIPT")
    base_reader = parent_context.get("baseReader")
    _need(type(base_reader) is types.ModuleType, "PARENT_PROCESS_READER")
    try:
        parent_doc = base_reader.host_document(parent_raw, MAX_SMALL_JSON_BYTES)
        request_doc = base_reader.host_document(request_raw, MAX_SMALL_JSON_BYTES)
        process_doc = base_reader.host_document(process_raw, MAX_SMALL_JSON_BYTES)
    except base_reader.ReaderError as failure:
        reason = getattr(failure, "code", None)
        _need(type(reason) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", reason) is not None,
             "COMPILE_RECEIPT_VALIDATION")
        raise BuildInputError("COMPILE_" + reason) from None
    # These are foreign PowerShell host receipts. Preserve and hash their raw
    # bytes, then authenticate their parsed values through the exact request
    # binding and the unchanged selected-host process validator below.
    _need(type(parent_doc) is dict and type(process_doc) is dict
         and request_doc == request, "COMPILE_RECEIPT_BINDING")
    request_disk = _canonical_json(request_doc)
    _need(request_disk == _canonical_json(request), "COMPILE_REQUEST_BINDING")
    try:
        base_reader._validate_owner_call(
            call_dir, parent_path, parent_doc, request_doc, process_doc,
            request["FilePath"], request["Arguments"], COMPILE_LIMIT_MS,
            parent_context["processModule"], required_stdout="",
            expected_stderr=base_reader.COMPILE_STDERR,
        )
    except base_reader.ReaderError as failure:
        reason = getattr(failure, "code", None)
        _need(type(reason) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", reason) is not None,
             "COMPILE_VALIDATION_ERROR")
        raise BuildInputError("COMPILE_" + reason) from None
    _need(_is_int(process_doc.get("ExitCode"), 0, 0)
         and process_doc.get("TerminationProven") is True,
         "COMPILE_NOT_SUCCESS")
    return call_dir, parent_path, parent_raw, parent_doc, process_doc


def _require_pre_finalize_stage(stage):
    expected = {"classes", "empty-sourcepath", "compiler-sources.txt",
                "compiler-request.json", "source-manifest.json"}
    names = _directory_names(stage, "STAGE_DIRECTORY")
    _need(len(names) == len(expected) and set(names) == expected,
         "STAGE_PRE_FINALIZE_CLOSURE")
    classes = _canonical(stage / "classes", "CLASSES_ROOT")
    empty_sourcepath = _canonical(stage / "empty-sourcepath", "EMPTY_SOURCEPATH")
    _lstat_directory(classes, "CLASSES_ROOT")
    _need(not _directory_names(empty_sourcepath, "EMPTY_SOURCEPATH"),
         "EMPTY_SOURCEPATH_NOT_EMPTY")


def _inventory_class_tree(classes, source_manifest_sha):
    _lstat_directory(classes, "CLASSES_ROOT")
    rows = []
    total = 0
    visited_dirs = 0
    pending = [classes]
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
            byte_count, digest = _file_sha(path, MAX_CLASS_BYTES, "CLASS_FILE")
            total += byte_count
            _need(total <= MAX_CLASS_BYTES, "CLASS_TOTAL_BYTES")
            relative = path.relative_to(classes).as_posix()
            _need(relative and not relative.startswith("/")
                 and ".." not in relative.split("/"), "CLASS_RELATIVE_PATH")
            rows.append({"path": relative, "bytes": byte_count, "sha256": digest})
    rows.sort(key=lambda row: row["path"])
    required = {
        "com/lushprojects/circuitjs1/client/FailedFirstRoutingStudy.class",
        "com/lushprojects/circuitjs1/client/FailedFirstFixtures.class",
    }
    _need(required.issubset({row["path"] for row in rows}), "STUDY_CLASSES_MISSING")
    _need(rows and len({row["path"].casefold() for row in rows}) == len(rows),
         "CLASS_PATH_DUPLICATE")
    inventory = {
        "schema": CLASS_SCHEMA,
        "sourceManifestSha256": source_manifest_sha,
        "classCount": len(rows),
        "totalBytes": total,
        "entries": rows,
    }
    raw = _canonical_json(inventory)
    _need(len(raw) <= MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY_SIZE")
    return inventory, raw


def _expected_run_manifest(input_manifest, build):
    value = {key: input_manifest[key] for key in INPUT_KEYS}
    value["schema"] = RUN_SCHEMA
    value["build"] = build
    return value


def finalize(stage_value, call_value, source_manifest_sha256):
    stage = _expected_stage(stage_value, require_empty=False)
    _require_pre_finalize_stage(stage)
    parent_module, parent_context = _load_parent()
    input_manifest, identity, request_raw, source_manifest_raw = _validate_source_input(
        stage, parent_context, source_manifest_sha256)
    request = _strict_document(request_raw, MAX_SMALL_JSON_BYTES, "COMPILER_REQUEST")
    call_dir, parent_path, parent_raw, _parent_doc, _process_doc = _validate_compile_call(
        call_value, stage, parent_context, request)
    classes = _canonical(input_manifest["classesRoot"], "CLASSES_ROOT")
    inventory, inventory_raw = _inventory_class_tree(classes, _sha(source_manifest_raw))
    inventory_path = stage / "class-inventory.json"
    build = {
        "sourceManifestPath": str(stage / "source-manifest.json"),
        "sourceManifestSha256": _sha(source_manifest_raw),
        "compileCallDirectory": str(call_dir),
        "compileParentPath": str(parent_path),
        "compileParentSha256": _sha(parent_raw),
        "classInventoryPath": str(inventory_path),
        "classInventorySha256": _sha(inventory_raw),
        "classCount": inventory["classCount"],
        "classBytes": inventory["totalBytes"],
    }
    _need(set(build) == BUILD_KEYS, "RUN_BUILD_SCHEMA")
    run_manifest = _expected_run_manifest(input_manifest, build)
    run_raw = _canonical_json(run_manifest)
    _need(len(run_raw) <= MAX_MANIFEST_BYTES, "RUN_MANIFEST_SIZE")
    _write_new(inventory_path, inventory_raw, MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY")
    _write_new(stage / "run-manifest.json", run_raw, MAX_MANIFEST_BYTES, "RUN_MANIFEST")
    _need(set(_directory_names(stage, "STAGE_DIRECTORY")) == {
        "classes", "empty-sourcepath", "compiler-sources.txt", "compiler-request.json",
        "source-manifest.json", "class-inventory.json", "run-manifest.json",
    }, "STAGE_FINAL_CLOSURE")
    return {
        "schema": FINALIZED_SCHEMA,
        "status": "FINALIZED_ONLY",
        "runManifestPath": str(stage / "run-manifest.json"),
        "runManifestSha256": _sha(run_raw),
        "sourceManifestSha256": _sha(source_manifest_raw),
        "compilerSourceCount": COMPILER_SOURCE_COUNT,
        "classCount": inventory["classCount"],
        "classBytes": inventory["totalBytes"],
    }


def _validate_run_manifest(path_value, expected_sha):
    path = _canonical(path_value, "RUN_MANIFEST_PATH")
    _need(path.name == "run-manifest.json", "RUN_MANIFEST_LOCATION")
    stage = path.parent
    _need(stage.parent == _task_root(), "STAGE_LOCATION")
    _revision(stage, STAGE_NAME, "STAGE_REVISION")
    _lstat_directory(stage, "STAGE_DIRECTORY")
    raw = _read_file(path, MAX_MANIFEST_BYTES, "RUN_MANIFEST")
    _need(type(expected_sha) is str and HEX.fullmatch(expected_sha) is not None
         and _sha(raw) == expected_sha, "RUN_MANIFEST_HASH")
    manifest = _strict_document(raw, MAX_MANIFEST_BYTES, "RUN_MANIFEST")
    _need(type(manifest) is dict and set(manifest) == RUN_KEYS
         and manifest.get("schema") == RUN_SCHEMA, "RUN_MANIFEST_SCHEMA")

    parent_module, parent_context = _load_parent()
    input_manifest, identity, request_raw, source_manifest_raw = _validate_source_input(
        stage, parent_context, manifest.get("build", {}).get("sourceManifestSha256")
        if type(manifest.get("build")) is dict else "")
    build = manifest.get("build")
    _need(type(build) is dict and set(build) == BUILD_KEYS, "RUN_BUILD_SCHEMA")
    _need(build.get("sourceManifestPath") == str(stage / "source-manifest.json")
         and build.get("sourceManifestSha256") == _sha(source_manifest_raw),
         "SOURCE_MANIFEST_BINDING")
    _need(_canonical_json(manifest) == _canonical_json(_expected_run_manifest(input_manifest, build)),
         "RUN_MANIFEST_IDENTITY")
    expected_stage_entries = {
        "classes", "empty-sourcepath", "compiler-sources.txt", "compiler-request.json",
        "source-manifest.json", "class-inventory.json", "run-manifest.json",
    }
    names = _directory_names(stage, "STAGE_DIRECTORY")
    _need(len(names) == len(expected_stage_entries) and set(names) == expected_stage_entries,
         "STAGE_CLOSURE")
    request = _strict_document(request_raw, MAX_SMALL_JSON_BYTES, "COMPILER_REQUEST")
    call_dir, parent_path, parent_raw, _parent_doc, process_doc = _validate_compile_call(
        build.get("compileCallDirectory"), stage, parent_context, request)
    _need(str(parent_path) == build.get("compileParentPath")
         and _sha(parent_raw) == build.get("compileParentSha256"), "COMPILE_PARENT_BINDING")
    _need(call_dir == _canonical(build.get("compileCallDirectory"), "COMPILE_CALL_PATH"),
         "COMPILE_CALL_BINDING")

    inventory_path = _canonical(build.get("classInventoryPath"), "CLASS_INVENTORY_PATH")
    classes = _canonical(manifest.get("classesRoot"), "CLASSES_ROOT")
    _need(inventory_path == stage / "class-inventory.json"
         and classes == stage / "classes", "CLASS_INVENTORY_PATH_BINDING")
    inventory_raw = _read_file(inventory_path, MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY")
    _need(_sha(inventory_raw) == build.get("classInventorySha256"), "CLASS_INVENTORY_HASH")
    inventory = _strict_document(inventory_raw, MAX_CLASS_INVENTORY_BYTES, "CLASS_INVENTORY")
    expected_inventory, expected_inventory_raw = _inventory_class_tree(
        classes, build.get("sourceManifestSha256"))
    _need(inventory_raw == expected_inventory_raw and inventory == expected_inventory,
         "CLASS_TREE_BINDING")
    _need(_is_int(build.get("classCount"), 1, MAX_CLASS_COUNT)
         and _is_int(build.get("classBytes"), 1, MAX_CLASS_BYTES)
         and build["classCount"] == inventory["classCount"]
         and build["classBytes"] == inventory["totalBytes"], "CLASS_COUNT_BINDING")

    parent_context = parent_module.recheck_inputs(parent_context)
    identity_again = _source_identity(parent_context)
    _need(identity_again["sourcePaths"] == identity["sourcePaths"]
         and identity_again["sources"] == identity["sources"]
         and identity_again["wire"] == identity["wire"], "SOURCE_RECHECK_BINDING")
    _need(_read_file(path, MAX_MANIFEST_BYTES, "RUN_MANIFEST_RECHECK") == raw,
         "RUN_MANIFEST_CHANGED")
    return {
        "sourceSha": expected_sha,
        "manifest": manifest,
        "path": path,
        "sourcePaths": [row["path"] for row in manifest["sources"]],
        "sources": manifest["sources"],
        "tools": manifest["tools"],
        "wire": manifest["wire"],
        "wireRaw": identity["wireRaw"],
        "baseReader": parent_context["baseReader"],
        "parentContext": parent_context,
        "parentAuditor": parent_context["parentAuditor"],
        "parentInputContext": parent_context["parentInputContext"],
        "p07Reader": parent_context["p07Reader"],
        "classesRoot": classes,
        "java": parent_context["java"],
        "javac": parent_context["javac"],
        "jars": parent_context["jars"],
        "processModule": parent_context["processModule"],
        "parentManifestPath": _parent_paths()[2],
        "parentManifestSha256": PARENT_MANIFEST_SHA256,
        "sourceManifestPath": stage / "source-manifest.json",
        "sourceManifestSha256": build["sourceManifestSha256"],
        "classInventory": inventory,
        "compileCallDirectory": call_dir,
        "compileProcess": process_doc,
    }


def validate_run_manifest(path_value, expected_sha):
    return _validate_run_manifest(path_value, expected_sha)


def recheck_inputs(context):
    _need(type(context) is dict and isinstance(context.get("path"), Path)
         and type(context.get("sourceSha")) is str, "RECHECK_CONTEXT")
    refreshed = validate_run_manifest(context["path"], context["sourceSha"])
    _need(refreshed["sourceSha"] == context["sourceSha"]
         and refreshed["sourcePaths"] == context["sourcePaths"], "RECHECK_BINDING")
    return refreshed


def audit(path_value, expected_sha):
    context = validate_run_manifest(path_value, expected_sha)
    return {
        "schema": AUDIT_SCHEMA,
        "status": "SOURCE_AUDIT_ONLY",
        "sourceSha": context["sourceSha"],
        "sourceRows": len(context["sourcePaths"]),
        "compilerSourceCount": context["manifest"]["compilerSourceCount"],
        "classCount": context["classInventory"]["classCount"],
        "classBytes": context["classInventory"]["totalBytes"],
        "wireSha256": context["wire"]["sha256"],
    }


def _emit(value):
    raw = _canonical_json(value)
    sys.stdout.buffer.write(raw)
    sys.stdout.buffer.flush()


def _safe_exception_detail(failure):
    kind = re.sub(r"[^A-Za-z0-9_]", "_", type(failure).__name__)[:64] or "Exception"
    try:
        reason = str(failure)
    except Exception:
        reason = "<unavailable>"
    for prefix in (str(Path(tempfile.gettempdir())), str(Path(tempfile.gettempdir()) / TASK_NAME),
                   str(Path(tempfile.gettempdir()) / PARENT_NAME)):
        reason = reason.replace(prefix, "<PRIVATE_ROOT>")
    reason = re.sub(r"(?i)\b[A-Z]:[\\/][^\s,;)]*", "<path>", reason)
    reason = reason.replace("\r", " ").replace("\n", " ")
    reason = re.sub(r"[^A-Za-z0-9_.:=<>/ -]", "_", reason)[:320]
    return kind + ":" + (reason or "<no-detail>")


def main(argv=None):
    parser = argparse.ArgumentParser(prog="build_inputs.py")
    commands = parser.add_subparsers(dest="command", required=True)
    prep = commands.add_parser("prepare")
    prep.add_argument("stage")
    finish = commands.add_parser("finalize")
    finish.add_argument("stage")
    finish.add_argument("compile_call_directory")
    finish.add_argument("source_manifest_sha256")
    check = commands.add_parser("audit")
    check.add_argument("run_manifest")
    check.add_argument("run_manifest_sha256")
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
        code = failure.code if re.fullmatch(r"[A-Z0-9_]{1,96}", failure.code) else "INVALID_ERROR_CODE"
        sys.stderr.write("FAILED_FIRST_BUILD_FAIL " + code + "\n")
        sys.stderr.flush()
        return 2
    except Exception as failure:
        detail = _safe_exception_detail(failure)
        kind = re.sub(r"[^A-Za-z0-9_]", "_", type(failure).__name__).upper()[:48]
        sys.stderr.write("FAILED_FIRST_BUILD_FAIL INTERNAL_" + (kind or "ERROR") +
                         " " + detail + "\n")
        sys.stderr.flush()
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
