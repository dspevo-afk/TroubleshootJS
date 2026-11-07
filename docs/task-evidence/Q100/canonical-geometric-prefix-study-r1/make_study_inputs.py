"""Create the closed BASE source inventory and input pins."""
from __future__ import annotations

import sys

if not sys.flags.isolated:
    sys.stderr.write("P8_INPUT_MAKER_FAIL ISOLATED_MODE_REQUIRED\n")
    raise SystemExit(2)
if not sys.dont_write_bytecode:
    sys.stderr.write("P8_INPUT_MAKER_FAIL BYTECODE_DISABLED_REQUIRED\n")
    raise SystemExit(2)

import hashlib
import ctypes
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import types


SCHEMA = "tsj-q100-geometric-prefix-study-inputs-v1"
PARENT = "0891474cadc4b9e00d7911e81c124721a11efb87"
P6_PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
CANARY_PINS_SHA = "11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a"
CANARY_QUALIFICATION_SHA = "755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276"
P6_PINS_SHA = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P8_AUDITOR_SHA = "169344976669b4882674083696c51d7992e7f2c3f69f19573d5788829d7d5874"
P8_AUDITOR_BYTES = 36304
SOURCE_INVENTORY_SPEC_SHA = "836b0d57086ceea09dc6db2579a970567cdc16c35b678594df88772c9c00ae21"
PYTHON_SHA = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
JAVA_SHA = "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
JAVAC_SHA = "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
POWERSHELL_SHA = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"

MAX_PINS_BYTES = 4 * 1024 * 1024
MAX_INVENTORY_BYTES = 1024 * 1024
MAX_INVENTORY_LINE = 65536
MAX_SINGLE_FILE = 100 * 1024 * 1024
MAX_FILES_BYTES = 8 * 1024 * 1024
MAX_JSONL_ROWS = 1084
EXPECTED_EXTRA_FILES = 25
EXPECTED_PYTHON_IMAGE: Path | None = None

P8_ROOT = Path(__file__).absolute().parent.parent / "canonical-geometric-reasons-r1"
P6_ROOT = Path(__file__).absolute().parent.parent / "canonical-prefix-rejection-stages-r1"
HERE = Path(__file__).absolute().parent
CANARY_PINS = P8_ROOT / "input-canary-pins-r9.json"
CANARY_QUALIFICATION = P8_ROOT / "CANARY_QUALIFICATION_R1.json"
P6_PINS = P6_ROOT / "input-pins-r6.json"

DIRECT_FILES = (
    "PLAN_R1.md",
    "SOURCE_INVENTORY_R1.md",
    "INPUTS_R1.md",
    "NATIVE_TRANSPORT_R1.md",
    "HOST_R1.md",
    "READER_R1.md",
    "CONTROL_CASES_R1.md",
    "CONTRACT_SET_R1.json",
    "PREREQUISITE_REUSE_REVIEW_R1.md",
    "source/CanonicalGeometricPrefixStudy.java",
    "source/CanonicalGeometricReasonInspector.java",
    "read_geometric_prefix.py",
    "run-study.ps1",
    "make_study_inputs.py",
    "audit_study_inputs.py",
    "run-study-controls.ps1",
    "test_read_geometric_prefix.py",
)
CONTRACT_FILES = (
    "CONTROL_CASES_R1.md",
    "HOST_R1.md",
    "INPUTS_R1.md",
    "NATIVE_TRANSPORT_R1.md",
    "PLAN_R1.md",
    "PREREQUISITE_REUSE_REVIEW_R1.md",
    "READER_R1.md",
    "SOURCE_INVENTORY_R1.md",
)
CONTRACT_SET_KEYS = {"schema", "status", "sourceParent", "contracts"}
CONTRACT_ROW_KEYS = {"path", "bytes", "sha256"}
PINS_KEYS = {
    "schema", "repositoryRoot", "parent", "canaryPrerequisite", "p6Context",
    "sourceInventory", "roles", "files", "toolPaths",
}
CANARY_TOP_KEYS = {
    "schema", "repositoryRoot", "parent", "p6PinsPath", "p6PinsSha256",
    "sourceRows", "files", "modeFiles", "bridgeManifest", "bridgeRoot",
    "javaHome", "pythonExe", "hostPath", "auditorPath", "planPath",
    "apiPath", "hostContractPath",
}
P6_SOURCE_ROLES = (
    "router", "support", "writer", "adapter", "blocker", "queries",
    "netRouterOverlay", "stageInspector",
)
P8_MODE_ROLES = (
    "router", "support", "writer", "adapter", "blocker", "queries",
    "stageInspector", "netRouterOverlay", "rulesOverlay", "reasonCanary",
)


class Reject(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(ok: bool, code: str) -> None:
    if not ok:
        raise Reject(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _identity(st):
    return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink


def _no_reparse(path: Path, code: str = "REPARSE_PATH") -> None:
    for item in (path, *path.parents):
        try:
            st = item.lstat()
        except OSError as exc:
            raise Reject("PATH_MISSING") from exc
        need(not stat.S_ISLNK(st.st_mode) and
             not (getattr(st, "st_file_attributes", 0) & 0x400), code)


def canonical(path_value: str | Path, want_dir: bool | None = None) -> Path:
    need(type(path_value) is str or isinstance(path_value, Path), "PATH_TYPE")
    path = Path(path_value)
    need(path.is_absolute() and str(path) == os.path.abspath(str(path)),
         "NONCANONICAL_PATH")
    _no_reparse(path)
    try:
        resolved = path.resolve(strict=True)
    except OSError as exc:
        raise Reject("PATH_MISSING") from exc
    need(str(resolved) == str(path), "NONCANONICAL_PATH")
    if want_dir is True:
        need(path.is_dir(), "DIRECTORY_REQUIRED")
    elif want_dir is False:
        need(path.is_file(), "FILE_REQUIRED")
    return path


def current_process_image_path() -> Path:
    need(os.name == "nt", "PYTHON_IMAGE_API")
    try:
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        get_module_file_name = kernel32.GetModuleFileNameW
        get_module_file_name.argtypes = [
            ctypes.c_void_p, ctypes.POINTER(ctypes.c_wchar), ctypes.c_uint32]
        get_module_file_name.restype = ctypes.c_uint32
    except (AttributeError, OSError) as exc:
        raise Reject("PYTHON_IMAGE_API") from exc

    buffer = ctypes.create_unicode_buffer(32768)
    try:
        ctypes.set_last_error(0)
        length = get_module_file_name(None, buffer, len(buffer))
        last_error = ctypes.get_last_error()
    except (OSError, OverflowError, TypeError, ValueError, ctypes.ArgumentError) as exc:
        raise Reject("PYTHON_IMAGE_API") from exc
    need(type(length) is int and 0 < length < len(buffer) and last_error == 0 and
         len(buffer.value) == length, "PYTHON_IMAGE_API")
    raw_path = Path(buffer.value)
    need(raw_path.is_absolute() and
         str(raw_path) == os.path.abspath(str(raw_path)), "PYTHON_IMAGE_PATH")
    _no_reparse(raw_path, "PYTHON_IMAGE_PATH")
    try:
        resolved = raw_path.resolve(strict=True)
        info = raw_path.stat()
    except (OSError, RuntimeError) as exc:
        raise Reject("PYTHON_IMAGE_PATH") from exc
    need(os.path.normcase(str(resolved)) == os.path.normcase(str(raw_path)) and
         stat.S_ISREG(info.st_mode), "PYTHON_IMAGE_PATH")
    return resolved


def attest_python_image(expected_path: Path, expected_sha: str) -> None:
    expected = canonical(expected_path, want_dir=False)
    actual = current_process_image_path()
    need(row_key(actual) == row_key(expected), "PYTHON_IMAGE_PATH")
    raw = stable_read(actual, MAX_SINGLE_FILE, "PYTHON_IMAGE_READ")
    need(sha(raw) == expected_sha, "PYTHON_IMAGE_HASH")


def destination(path_value: str, basename: str | None = None) -> Path:
    need(type(path_value) is str and path_value, "PATH_TYPE")
    path = Path(path_value)
    need(path.is_absolute() and str(path) == os.path.abspath(str(path)),
         "NONCANONICAL_PATH")
    need(basename is None or path.name == basename, "OUTPUT_NAME")
    parent = canonical(path.parent, want_dir=True)
    path = parent / path.name
    need(not path.exists(), "OUTPUT_EXISTS")
    try:
        path.lstat()
    except FileNotFoundError:
        pass
    else:
        raise Reject("OUTPUT_EXISTS")
    return path


def stable_read(path_value: str | Path, limit: int = MAX_SINGLE_FILE,
                code: str = "FILE_READ") -> bytes:
    path = canonical(path_value, want_dir=False)
    try:
        before = path.stat()
        need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= limit, "FILE_SIZE")
        with path.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            raw = stream.read(limit + 1)
            closed = os.fstat(stream.fileno())
        after = path.stat()
    except Reject:
        raise
    except OSError as exc:
        raise Reject(code) from exc
    need(_identity(before) == _identity(opened) == _identity(closed) == _identity(after) and
         before.st_ctime_ns == after.st_ctime_ns and
         opened.st_ctime_ns == closed.st_ctime_ns and
         len(raw) == after.st_size <= limit, "FILE_CHANGED")
    return raw


def parse_json(raw: bytes, code: str = "INVALID_JSON"):
    need(not raw.startswith(b"\xef\xbb\xbf"), "JSON_BOM")

    def pairs(rows):
        result = {}
        for key, value in rows:
            need(key not in result, "DUP_JSON_KEY")
            result[key] = value
        return result

    def bad_constant(_value):
        raise Reject("NONFINITE_JSON")

    try:
        return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                          parse_constant=bad_constant)
    except Reject:
        raise
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise Reject(code) from exc


def canonical_json(value) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")


def compact_json(value) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=True) + "\n").encode("ascii")


def exact_keys(value: object, expected: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == expected, code)
    return value


def exact_int(value: object, low: int = 0) -> bool:
    return type(value) is int and value >= low


def source_rel(value: object) -> str:
    need(type(value) is str and value and "\\" not in value, "SOURCE_PATH")
    rel = PurePosixPath(value)
    need(not rel.is_absolute() and all(part not in ("", ".", "..") for part in rel.parts),
         "SOURCE_PATH")
    need(rel.as_posix() == value, "SOURCE_PATH")
    return value


def row_key(path: Path) -> str:
    return os.path.normcase(str(path))


def file_row(path_value: Path, raw: bytes | None = None) -> dict:
    path = canonical(path_value, want_dir=False)
    if raw is None:
        raw = stable_read(path)
    return {"path": str(path), "bytes": len(raw), "sha256": sha(raw)}


def rows_by_path(rows: object, expected_keys: set[str], code: str) -> dict[str, dict]:
    need(type(rows) is list, code)
    result = {}
    for row in rows:
        exact_keys(row, expected_keys, code)
        path = canonical(row["path"], want_dir=False)
        key = row_key(path)
        need(key not in result, code)
        need(exact_int(row["bytes"]) and 0 < row["bytes"] <= MAX_SINGLE_FILE and
             type(row["sha256"]) is str and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]), code)
        result[key] = {**row, "path": str(path)}
    return result


def write_new(path: Path, raw: bytes) -> None:
    need(not path.exists(), "OUTPUT_EXISTS")
    try:
        with path.open("xb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError as exc:
        raise Reject("OUTPUT_EXISTS") from exc
    except OSError as exc:
        raise Reject("OUTPUT_WRITE") from exc
    need(stable_read(path, max(len(raw), 1), "OUTPUT_READBACK") == raw,
         "OUTPUT_CLOSE_CHANGED")


def imported_p8_auditor(p8_pins: dict, pins_raw: bytes):
    need(sha(pins_raw) == CANARY_PINS_SHA, "CANARY_PINS_HASH")
    file_rows = rows_by_path(p8_pins.get("files"), {"path", "bytes", "sha256"},
                              "CANARY_FILE_ROWS")
    auditor_path = canonical(P8_ROOT / "audit_canary_inputs.py", want_dir=False)
    row = file_rows.get(row_key(auditor_path))
    need(row is not None and row["bytes"] == P8_AUDITOR_BYTES and
         row["sha256"] == P8_AUDITOR_SHA, "CANARY_AUDITOR_PIN")
    raw = stable_read(auditor_path, 2 * 1024 * 1024, "CANARY_AUDITOR_READ")
    need(len(raw) == row["bytes"] and sha(raw) == P8_AUDITOR_SHA,
         "CANARY_AUDITOR_PIN")
    module = types.ModuleType("authenticated_geometric_canary_auditor")
    module.__file__ = str(auditor_path)
    module.__package__ = ""
    sys.modules[module.__name__] = module
    try:
        exec(compile(raw, str(auditor_path), "exec"), module.__dict__)
    except Exception as exc:
        raise Reject("CANARY_AUDITOR_IMPORT") from exc
    need(callable(getattr(module, "validate_manifest", None)) and
         callable(getattr(module, "close_inputs", None)) and
         callable(getattr(module, "row_map", None)) and
         callable(getattr(module, "require_row", None)) and
         callable(getattr(module, "import_p6_helper", None)) and
         callable(getattr(module, "powershell_executable", None)) and
         getattr(module, "P8_PARENT", None) == PARENT and
         getattr(module, "P6_PINS_SHA256", None) == P6_PINS_SHA,
         "CANARY_AUDITOR_API")
    return module


def validate_canary_inputs(p8_auditor, p8_pins: dict):
    p6_path = canonical(p8_pins.get("p6PinsPath", ""), want_dir=False)
    need(p6_path == canonical(P6_PINS, want_dir=False) and
         p8_pins.get("p6PinsSha256") == P6_PINS_SHA, "P6_CONTEXT")
    p6_raw = stable_read(p6_path, MAX_PINS_BYTES, "P6_PINS_READ")
    need(sha(p6_raw) == P6_PINS_SHA, "P6_PINS_HASH")
    p6 = parse_json(p6_raw, "P6_PINS_JSON")
    # The immutable P6 pins are authenticated by their frozen raw digest;
    # retain their accepted historical key order and JSON framing.
    need(type(p6) is dict and p6.get("parent") == P6_PARENT and
         p6.get("schema") == "tsj-canonical-prefix-stage-inputs-v1", "P6_CONTEXT")
    p6_rows = p8_auditor.row_map(p6.get("files"), "P6_FILE_ROW")
    helper_path = canonical(p6.get("preflight", ""), want_dir=False)
    need(helper_path == canonical(P6_ROOT / "audit_inputs.py", want_dir=False),
         "P6_AUDITOR_PATH")
    helper_row = p8_auditor.require_row(
        p6_rows, helper_path, p8_auditor.P6_AUDITOR_SHA256, "P6_AUDITOR_PIN",
    )
    helper_raw = stable_read(helper_path, 2 * 1024 * 1024, "P6_AUDITOR_READ")
    need(len(helper_raw) == helper_row["bytes"] and
         sha(helper_raw) == p8_auditor.P6_AUDITOR_SHA256, "P6_AUDITOR_PIN")
    try:
        helper = p8_auditor.import_p6_helper(helper_path, helper_raw)
        _p6, file_rows, expected_paths, production = p8_auditor.validate_manifest(
            helper, p8_pins,
        )
    except Exception as exc:
        raise Reject("CANARY_AUDIT") from exc
    need(production == 850, "CANARY_SOURCE_CENSUS")
    return {"helper": helper, "fileRows": file_rows,
            "expectedPaths": expected_paths, "p6Raw": p6_raw}


def close_canary_inputs(p8_auditor, p8_pins: dict, p8_raw: bytes,
                        context: dict) -> None:
    helper = context["helper"]
    try:
        p8_auditor.close_inputs(helper, CANARY_PINS, p8_raw, p8_pins,
                                context["fileRows"], context["expectedPaths"])
        need(stable_read(P6_PINS, MAX_PINS_BYTES, "CLOSE_CHANGED") == context["p6Raw"],
             "CLOSE_CHANGED")
    except Reject:
        raise
    except Exception as exc:
        raise Reject("CANARY_CLOSE") from exc


def validate_qualification(qualification_raw: bytes) -> dict:
    need(sha(qualification_raw) == CANARY_QUALIFICATION_SHA,
         "CANARY_QUALIFICATION_HASH")
    qualification = parse_json(qualification_raw, "CANARY_QUALIFICATION_JSON")
    need(type(qualification) is dict and
         qualification.get("schema") == "tsj-p8-focused-canary-qualification-v1" and
         qualification.get("status") == "PASS_FOCUSED_NATIVE_OBSERVER_PREREQUISITE_ONLY" and
         qualification.get("inputPinsSha256") == CANARY_PINS_SHA and
         qualification.get("sourceParent") == PARENT and
         type(qualification.get("productionFilesChanged")) is int and
         qualification.get("productionFilesChanged") == 0 and
         qualification.get("canonicalBaseReplay") == "NOT_RUN", "CANARY_QUALIFICATION")
    closure = qualification.get("sourceClosure")
    need(type(closure) is dict and
         closure.get("status") == "PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY" and
         closure.get("pinsSha256") == CANARY_PINS_SHA and
         closure.get("sourceRows") == 1084 and
         closure.get("productionFiles") == 850, "CANARY_QUALIFICATION")
    return qualification


def validate_contract_set(direct_raw: dict[str, bytes]) -> None:
    raw = direct_raw["CONTRACT_SET_R1.json"]
    need(len(raw) <= 65536, "CONTRACT_SET_SIZE")
    value = parse_json(raw, "CONTRACT_SET_JSON")
    need(raw == compact_json(value), "CONTRACT_SET_FORMAT")
    exact_keys(value, {"schema", "status", "sourceParent", "contracts"}, "CONTRACT_SET_KEYS")
    need(value["schema"] == "tsj-q100-geometric-prefix-contract-set-v1" and
         value["status"] == "FROZEN_FOR_IMPLEMENTATION_ONLY" and
         value["sourceParent"] == PARENT and type(value["contracts"]) is list and
         len(value["contracts"]) == len(CONTRACT_FILES), "CONTRACT_SET_CONTEXT")
    seen = []
    for row, expected_path in zip(value["contracts"], CONTRACT_FILES):
        exact_keys(row, CONTRACT_ROW_KEYS, "CONTRACT_SET_ROW")
        need(row["path"] == expected_path and exact_int(row["bytes"], 1) and
             type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "CONTRACT_SET_ROW")
        expected_raw = direct_raw[expected_path]
        need(row["bytes"] == len(expected_raw) and row["sha256"] == sha(expected_raw),
             "CONTRACT_SET_BINDING")
        seen.append(row["path"])
    need(tuple(seen) == CONTRACT_FILES, "CONTRACT_SET_ORDER")


def create_source_inventory(source_rows: object) -> bytes:
    need(type(source_rows) is list and len(source_rows) == MAX_JSONL_ROWS,
         "SOURCE_CENSUS")
    unique = set()
    rows = []
    production = 0
    for source in source_rows:
        exact_keys(source, {"path", "role", "bytes", "sha256"}, "SOURCE_ROW")
        rel = source_rel(source["path"])
        need(rel not in unique, "SOURCE_DUPLICATE")
        unique.add(rel)
        need(exact_int(source["bytes"]) and source["bytes"] <= MAX_SINGLE_FILE and
             type(source["role"]) is str and source["role"] and
             type(source["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", source["sha256"]) is not None,
             "SOURCE_ROW")
        production += rel.startswith("src/")
        rows.append({"kind": "source", "path": rel, "bytes": source["bytes"],
                     "sha256": source["sha256"], "role": source["role"]})
    rows.sort(key=lambda row: row["path"])
    need(len(unique) == MAX_JSONL_ROWS and production == 850, "SOURCE_CENSUS")
    records = [{
        "kind": "header", "schema": "tsj-q100-geometric-prefix-source-v1",
        "sourceParent": PARENT, "rowCount": MAX_JSONL_ROWS, "productionCount": 850,
    }, *rows, {"kind": "footer", "rowCount": MAX_JSONL_ROWS}]
    lines = [compact_json(record) for record in records]
    need(all(0 < len(line) <= MAX_INVENTORY_LINE for line in lines) and
         sum(map(len, lines)) <= MAX_INVENTORY_BYTES, "SOURCE_INVENTORY_SIZE")
    return b"".join(lines)


def build_files(direct_raw: dict[str, bytes], inventory_path: Path,
                inventory_raw: bytes, canary_pins_path: Path,
                canary_pins_raw: bytes, qualification_path: Path,
                qualification_raw: bytes, p6_pins_path: Path,
                p6_pins_raw: bytes, tools: dict[str, Path]) -> list[dict]:
    candidates: list[tuple[Path, bytes]] = []
    for relative in DIRECT_FILES:
        candidates.append((canonical(HERE / Path(relative), want_dir=False),
                          direct_raw[relative]))
    candidates.extend((
        (inventory_path, inventory_raw),
        (canary_pins_path, canary_pins_raw),
        (qualification_path, qualification_raw),
        (p6_pins_path, p6_pins_raw),
    ))
    java_home = tools["javaHome"]
    candidates.extend((
        (tools["pythonExe"], stable_read(tools["pythonExe"], MAX_SINGLE_FILE, "TOOL_READ")),
        (canonical(java_home / "bin/java.exe", want_dir=False),
         stable_read(java_home / "bin/java.exe", MAX_SINGLE_FILE, "TOOL_READ")),
        (canonical(java_home / "bin/javac.exe", want_dir=False),
         stable_read(java_home / "bin/javac.exe", MAX_SINGLE_FILE, "TOOL_READ")),
        (tools["powershellExe"], stable_read(tools["powershellExe"], MAX_SINGLE_FILE, "TOOL_READ")),
    ))
    rows = []
    seen = set()
    total = 0
    for path, raw in candidates:
        path = canonical(path, want_dir=False)
        key = row_key(path)
        need(key not in seen, "DUPLICATE_FILE_PIN")
        seen.add(key)
        need(len(raw) <= MAX_SINGLE_FILE, "FILE_SIZE")
        total += len(raw)
        rows.append({"path": str(path), "bytes": len(raw), "sha256": sha(raw)})
    need(len(rows) == EXPECTED_EXTRA_FILES and total <= MAX_FILES_BYTES,
         "EXTRA_FILE_CENSUS")
    rows.sort(key=lambda row: row["path"].casefold())
    return rows


def _load_parents(repository: Path, canary_pins_path: Path,
                  qualification_path: Path, canary_pins_sha: str,
                  qualification_sha: str):
    need(canary_pins_path == canonical(CANARY_PINS, want_dir=False) and
         canary_pins_sha == CANARY_PINS_SHA, "CANARY_PINS_CONTEXT")
    need(qualification_path == canonical(CANARY_QUALIFICATION, want_dir=False) and
         qualification_sha == CANARY_QUALIFICATION_SHA, "CANARY_QUALIFICATION_CONTEXT")
    p8_raw = stable_read(canary_pins_path, MAX_PINS_BYTES, "CANARY_PINS_READ")
    need(sha(p8_raw) == CANARY_PINS_SHA, "CANARY_PINS_HASH")
    p8_pins = parse_json(p8_raw, "CANARY_PINS_JSON")
    exact_keys(p8_pins, CANARY_TOP_KEYS, "CANARY_PINS_KEYS")
    need(p8_raw == canonical_json(p8_pins), "CANARY_PINS_FORMAT")
    need(p8_pins.get("schema") == "tsj-q100-geometric-canary-inputs-v1" and
         p8_pins.get("parent") == PARENT, "CANARY_PINS_CONTEXT")
    p8_repository = canonical(p8_pins["repositoryRoot"], want_dir=True)
    need(p8_repository == repository, "REPOSITORY_CONTEXT")
    need(type(p8_pins["sourceRows"]) is list and len(p8_pins["sourceRows"]) == 1084,
         "SOURCE_CENSUS")
    need(type(p8_pins["modeFiles"]) is dict and
         set(p8_pins["modeFiles"]) == set(P8_MODE_ROLES), "CANARY_MODE_KEYS")
    p6_path = canonical(p8_pins["p6PinsPath"], want_dir=False)
    need(p6_path == canonical(P6_PINS, want_dir=False) and
         p8_pins["p6PinsSha256"] == P6_PINS_SHA, "P6_CONTEXT")
    qualification_raw = stable_read(qualification_path, 65536, "CANARY_QUALIFICATION_READ")
    validate_qualification(qualification_raw)
    p8_auditor = imported_p8_auditor(p8_pins, p8_raw)
    canary_context = validate_canary_inputs(p8_auditor, p8_pins)
    return p8_pins, p8_raw, p6_path, canary_context["p6Raw"], qualification_raw, \
        p8_auditor, canary_context


def _source_closure(repository: Path, source_rows: list[dict]) -> None:
    need(len(source_rows) == 1084, "SOURCE_CENSUS")
    production = 0
    seen = set()
    for row in source_rows:
        exact_keys(row, {"path", "bytes", "role", "sha256"}, "SOURCE_ROW")
        rel = source_rel(row["path"])
        need(rel not in seen and exact_int(row["bytes"]) and
             row["bytes"] <= MAX_SINGLE_FILE and type(row["role"]) is str and
             type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "SOURCE_ROW")
        seen.add(rel)
        production += rel.startswith("src/")
        path = canonical(repository.joinpath(*PurePosixPath(rel).parts), want_dir=False)
        raw = stable_read(path, MAX_SINGLE_FILE, "SOURCE_READ")
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "SOURCE_CHANGED")
    need(len(seen) == 1084 and production == 850, "SOURCE_CENSUS")


def _close_file_rows(file_rows: list[dict]) -> None:
    total = 0
    for row in file_rows:
        path = canonical(row["path"], want_dir=False)
        raw = stable_read(path, MAX_SINGLE_FILE, "CLOSE_CHANGED")
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "CLOSE_CHANGED")
        total += len(raw)
    need(len(file_rows) == EXPECTED_EXTRA_FILES and total <= MAX_FILES_BYTES,
         "EXTRA_FILE_CENSUS")


def _direct_inputs() -> dict[str, bytes]:
    result = {}
    for relative in DIRECT_FILES:
        path = HERE / Path(relative)
        try:
            path = canonical(path, want_dir=False)
            result[relative] = stable_read(path, MAX_SINGLE_FILE, "DIRECT_INPUT_READ")
        except Reject as exc:
            if exc.code == "PATH_MISSING":
                raise Reject("DIRECT_INPUT_MISSING") from exc
            raise
    validate_contract_set(result)
    need(sha(result["SOURCE_INVENTORY_R1.md"]) == SOURCE_INVENTORY_SPEC_SHA,
         "SOURCE_INVENTORY_CONTRACT_HASH")
    return result


def create_inputs(repository_arg: str, canary_pins_arg: str, canary_pins_sha: str,
                  qualification_arg: str, qualification_sha: str,
                  inventory_output_arg: str, pins_output_arg: str) -> dict:
    global EXPECTED_PYTHON_IMAGE
    script_path = canonical(Path(__file__).absolute(), want_dir=False)
    need(script_path == canonical(HERE / "make_study_inputs.py", want_dir=False), "SELF_PATH")
    repository = canonical(repository_arg, want_dir=True)
    canary_pins_path = canonical(canary_pins_arg, want_dir=False)
    qualification_path = canonical(qualification_arg, want_dir=False)
    inventory_output = destination(inventory_output_arg, "native-source-inventory.jsonl")
    pins_output = destination(pins_output_arg)
    need(row_key(inventory_output) != row_key(pins_output), "OUTPUT_PATH_COLLISION")
    direct_raw = _direct_inputs()
    p8_pins, p8_raw, p6_path, p6_raw, qualification_raw, p8_auditor, canary_context = _load_parents(
        repository, canary_pins_path, qualification_path, canary_pins_sha,
        qualification_sha,
    )
    source_rows = p8_pins["sourceRows"]
    # The authenticated P8 validate_manifest call above has already closed
    # these rows against E. Recheck after pin creation below to catch changes
    # during inventory construction without adding a redundant full census.
    inventory_raw = create_source_inventory(source_rows)

    # Authenticate the old auditor's selected tool paths and hashes from the
    # exact R9 rows before adding them to this BASE file inventory.
    p8_file_rows = rows_by_path(p8_pins["files"], {"path", "bytes", "sha256"},
                                "CANARY_FILE_ROWS")
    tool_paths = {}
    python_path = canonical(p8_pins["pythonExe"], want_dir=False)
    java_home = canonical(p8_pins["javaHome"], want_dir=True)
    try:
        powershell_path = canonical(p8_auditor.powershell_executable(), want_dir=False)
    except Exception as exc:
        raise Reject("POWERSHELL_PATH") from exc
    for path, expected in ((python_path, PYTHON_SHA),
                           (canonical(java_home / "bin/java.exe", False), JAVA_SHA),
                           (canonical(java_home / "bin/javac.exe", False), JAVAC_SHA),
                           (powershell_path, POWERSHELL_SHA)):
        row = p8_file_rows.get(row_key(path))
        need(row is not None and row["sha256"] == expected, "TOOL_PIN")
        raw = stable_read(path, MAX_SINGLE_FILE, "TOOL_READ")
        need(len(raw) == row["bytes"] and sha(raw) == expected, "TOOL_PIN")
    tool_paths = {"pythonExe": python_path, "javaHome": java_home,
                  "powershellExe": powershell_path}
    EXPECTED_PYTHON_IMAGE = python_path

    need(p8_pins["parent"] == PARENT, "PARENT")
    helper_path = canonical(repository / "scripts/VerifierIsolation.psm1", want_dir=False)
    helper_source = [row for row in source_rows if row.get("path") ==
                     "scripts/VerifierIsolation.psm1"]
    need(len(helper_source) == 1 and helper_source[0].get("role") == "BASE_SOURCE",
         "HELPER_SOURCE_ROW")

    # Confirm the running process is the pinned interpreter before creating
    # either output; sys.executable may be a Windows Store alias.
    attest_python_image(python_path, PYTHON_SHA)
    write_new(inventory_output, inventory_raw)
    inventory_row = file_row(inventory_output, inventory_raw)
    native_study = canonical(HERE / "source/CanonicalGeometricPrefixStudy.java", False)
    native_inspector = canonical(HERE / "source/CanonicalGeometricReasonInspector.java", False)
    reader = canonical(HERE / "read_geometric_prefix.py", False)
    host = canonical(HERE / "run-study.ps1", False)
    maker = canonical(HERE / "make_study_inputs.py", False)
    auditor = canonical(HERE / "audit_study_inputs.py", False)
    input_files = build_files(
        direct_raw, inventory_output, inventory_raw, canary_pins_path, p8_raw,
        qualification_path, qualification_raw, p6_path, p6_raw, tool_paths,
    )
    _close_file_rows(input_files)
    pins = {
        "schema": SCHEMA,
        "repositoryRoot": str(repository),
        "parent": PARENT,
        "canaryPrerequisite": {
            "root": str(canonical(P8_ROOT, want_dir=True)),
            "pinsPath": str(canary_pins_path),
            "pinsSha256": CANARY_PINS_SHA,
            "qualificationPath": str(qualification_path),
            "qualificationSha256": CANARY_QUALIFICATION_SHA,
        },
        "p6Context": {"pinsPath": str(p6_path), "pinsSha256": P6_PINS_SHA},
        "sourceInventory": {"path": str(inventory_output),
                            "sha256": inventory_row["sha256"]},
        "roles": {
            "native": {"study": str(native_study), "inspector": str(native_inspector)},
            "process": {"helper": str(helper_path), "reader": str(reader),
                        "host": str(host)},
            "input": {"maker": str(maker), "auditor": str(auditor)},
        },
        "files": input_files,
        "toolPaths": {key: str(value) for key, value in tool_paths.items()},
    }
    need(set(pins) == PINS_KEYS and len(input_files) == EXPECTED_EXTRA_FILES,
         "INPUT_PIN_SHAPE")
    pins_raw = canonical_json(pins)
    need(len(pins_raw) <= MAX_PINS_BYTES, "PINS_SIZE")
    attest_python_image(python_path, PYTHON_SHA)
    write_new(pins_output, pins_raw)
    need(stable_read(inventory_output, MAX_INVENTORY_BYTES) == inventory_raw and
         stable_read(pins_output, MAX_PINS_BYTES) == pins_raw, "CLOSE_CHANGED")
    _close_file_rows(input_files)
    _source_closure(repository, source_rows)
    close_canary_inputs(p8_auditor, p8_pins, p8_raw, canary_context)
    attest_python_image(python_path, PYTHON_SHA)
    return {
        "status": "INPUTS_CREATED_NOT_BASE_ACCEPTANCE",
        "sourceRows": 1084,
        "productionFiles": 850,
        "extraFiles": len(input_files),
        "pinsSha256": sha(pins_raw),
        "sourceInventorySha256": sha(inventory_raw),
    }


def main() -> int:
    if len(sys.argv) != 8:
        sys.stderr.write("P8_INPUT_MAKER_FAIL USAGE\n")
        return 2
    try:
        result = create_inputs(*sys.argv[1:])
        need(EXPECTED_PYTHON_IMAGE is not None, "PYTHON_IMAGE_PATH")
        attest_python_image(EXPECTED_PYTHON_IMAGE, PYTHON_SHA)
        sys.stdout.write("P8_INPUT_MAKER_CREATED " + json.dumps(
            result, sort_keys=True, separators=(",", ":")) + "\n")
        sys.stdout.flush()
        return 0
    except Reject as exc:
        sys.stderr.write("P8_INPUT_MAKER_FAIL " + exc.code + "\n")
        return 2
    except Exception:
        sys.stderr.write("P8_INPUT_MAKER_FAIL INPUTS_INVALID\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
