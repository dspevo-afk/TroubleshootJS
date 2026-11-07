"""Independently validate the closed BASE source inventory and input pins."""
from __future__ import annotations

import sys

if not sys.flags.isolated:
    sys.stderr.write("P8_INPUT_AUDIT_FAIL ISOLATED_MODE_REQUIRED\n")
    raise SystemExit(2)
if not sys.dont_write_bytecode:
    sys.stderr.write("P8_INPUT_AUDIT_FAIL BYTECODE_DISABLED_REQUIRED\n")
    raise SystemExit(2)

import hashlib
import ctypes
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
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
MAX_RESULT_BYTES = 65536
EXPECTED_PYTHON_IMAGE: Path | None = None

HERE = Path(__file__).absolute().parent
P8_ROOT = HERE.parent / "canonical-geometric-reasons-r1"
P6_ROOT = HERE.parent / "canonical-prefix-rejection-stages-r1"
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


def identity(st):
    return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink


def no_reparse(path: Path, code: str = "REPARSE_PATH") -> None:
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
    no_reparse(path)
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
    no_reparse(raw_path, "PYTHON_IMAGE_PATH")
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
    need(identity(before) == identity(opened) == identity(closed) == identity(after) and
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


def compact_json(value) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=True) + "\n").encode("ascii")


def canonical_json(value) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")


def exact_keys(value: object, expected: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == expected, code)
    return value


def exact_int(value: object, low: int = 0) -> bool:
    return type(value) is int and value >= low


def source_rel(value: object) -> str:
    need(type(value) is str and value and "\\" not in value, "SOURCE_PATH")
    rel = PurePosixPath(value)
    need(not rel.is_absolute() and all(part not in ("", ".", "..") for part in rel.parts) and
         rel.as_posix() == value, "SOURCE_PATH")
    return value


def row_key(path: Path) -> str:
    return os.path.normcase(str(path))


def rows_by_path(rows: object, expected_keys: set[str], code: str) -> dict[str, dict]:
    need(type(rows) is list, code)
    result = {}
    for row in rows:
        exact_keys(row, expected_keys, code)
        path = canonical(row["path"], want_dir=False)
        key = row_key(path)
        need(key not in result and exact_int(row["bytes"]) and
             0 < row["bytes"] <= MAX_SINGLE_FILE and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None, code)
        result[key] = {**row, "path": str(path)}
    return result


def imported_canary_auditor(p8_pins: dict):
    p8_rows = rows_by_path(p8_pins.get("files"), {"path", "bytes", "sha256"},
                           "CANARY_FILE_ROWS")
    path = canonical(P8_ROOT / "audit_canary_inputs.py", want_dir=False)
    row = p8_rows.get(row_key(path))
    need(row is not None and row["bytes"] == P8_AUDITOR_BYTES and
         row["sha256"] == P8_AUDITOR_SHA, "CANARY_AUDITOR_PIN")
    raw = stable_read(path, 2 * 1024 * 1024, "CANARY_AUDITOR_READ")
    need(len(raw) == row["bytes"] and sha(raw) == P8_AUDITOR_SHA,
         "CANARY_AUDITOR_PIN")
    module = types.ModuleType("authenticated_geometric_canary_auditor")
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[module.__name__] = module
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
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


def validate_canary_inputs(module, canary: dict):
    p6_path = canonical(canary.get("p6PinsPath", ""), want_dir=False)
    need(p6_path == canonical(P6_PINS, want_dir=False) and
         canary.get("p6PinsSha256") == P6_PINS_SHA, "P6_CONTEXT")
    p6_raw = stable_read(p6_path, MAX_PINS_BYTES, "P6_PINS_READ")
    need(sha(p6_raw) == P6_PINS_SHA, "P6_PINS_HASH")
    p6 = parse_json(p6_raw, "P6_PINS_JSON")
    # P6 keeps its accepted historical serialization; its exact raw digest
    # and duplicate-key-safe parse bind this immutable parent context.
    need(type(p6) is dict and p6.get("schema") == "tsj-canonical-prefix-stage-inputs-v1" and
         p6.get("parent") == P6_PARENT, "P6_CONTEXT")
    p6_rows = module.row_map(p6.get("files"), "P6_FILE_ROW")
    helper_path = canonical(p6.get("preflight", ""), want_dir=False)
    need(helper_path == canonical(P6_ROOT / "audit_inputs.py", want_dir=False),
         "P6_AUDITOR_PATH")
    helper_row = module.require_row(
        p6_rows, helper_path, module.P6_AUDITOR_SHA256, "P6_AUDITOR_PIN",
    )
    helper_raw = stable_read(helper_path, 2 * 1024 * 1024, "P6_AUDITOR_READ")
    need(len(helper_raw) == helper_row["bytes"] and
         sha(helper_raw) == module.P6_AUDITOR_SHA256, "P6_AUDITOR_PIN")
    try:
        helper = module.import_p6_helper(helper_path, helper_raw)
        _p6, file_rows, expected_paths, production = module.validate_manifest(
            helper, canary,
        )
    except Exception as exc:
        raise Reject("CANARY_AUDIT") from exc
    need(production == 850, "CANARY_SOURCE_CENSUS")
    return {"helper": helper, "fileRows": file_rows,
            "expectedPaths": expected_paths, "p6Raw": p6_raw}


def close_canary_inputs(module, canary: dict, pins_raw: bytes,
                        context: dict) -> None:
    try:
        module.close_inputs(context["helper"], CANARY_PINS, pins_raw, canary,
                            context["fileRows"], context["expectedPaths"])
        need(stable_read(P6_PINS, MAX_PINS_BYTES, "CLOSE_CHANGED") == context["p6Raw"],
             "CLOSE_CHANGED")
    except Reject:
        raise
    except Exception as exc:
        raise Reject("CANARY_CLOSE") from exc


def validate_qualification(raw: bytes) -> dict:
    need(sha(raw) == CANARY_QUALIFICATION_SHA, "CANARY_QUALIFICATION_HASH")
    value = parse_json(raw, "CANARY_QUALIFICATION_JSON")
    need(type(value) is dict and
         value.get("schema") == "tsj-p8-focused-canary-qualification-v1" and
         value.get("status") == "PASS_FOCUSED_NATIVE_OBSERVER_PREREQUISITE_ONLY" and
         value.get("inputPinsSha256") == CANARY_PINS_SHA and
         value.get("sourceParent") == PARENT and
         type(value.get("productionFilesChanged")) is int and
         value.get("productionFilesChanged") == 0 and
         value.get("canonicalBaseReplay") == "NOT_RUN", "CANARY_QUALIFICATION")
    closure = value.get("sourceClosure")
    need(type(closure) is dict and
         closure.get("status") == "PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY" and
         closure.get("pinsSha256") == CANARY_PINS_SHA and
         closure.get("sourceRows") == 1084 and
         closure.get("productionFiles") == 850, "CANARY_QUALIFICATION")
    return value


def validate_contract_set(direct_raw: dict[str, bytes], file_rows: dict[str, dict]) -> None:
    raw = direct_raw["CONTRACT_SET_R1.json"]
    need(len(raw) <= 65536, "CONTRACT_SET_SIZE")
    value = parse_json(raw, "CONTRACT_SET_JSON")
    need(raw == compact_json(value), "CONTRACT_SET_FORMAT")
    exact_keys(value, {"schema", "status", "sourceParent", "contracts"},
               "CONTRACT_SET_KEYS")
    need(value["schema"] == "tsj-q100-geometric-prefix-contract-set-v1" and
         value["status"] == "FROZEN_FOR_IMPLEMENTATION_ONLY" and
         value["sourceParent"] == PARENT and type(value["contracts"]) is list and
         len(value["contracts"]) == len(CONTRACT_FILES), "CONTRACT_SET_CONTEXT")
    for row, relative in zip(value["contracts"], CONTRACT_FILES):
        exact_keys(row, CONTRACT_ROW_KEYS, "CONTRACT_SET_ROW")
        need(row["path"] == relative and exact_int(row["bytes"], 1) and
             type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "CONTRACT_SET_ROW")
        path = canonical(HERE / Path(relative), want_dir=False)
        key = row_key(path)
        need(key in file_rows and (row["bytes"], row["sha256"]) ==
             (file_rows[key]["bytes"], file_rows[key]["sha256"]),
             "CONTRACT_SET_BINDING")


def expected_inventory(source_rows: list[dict]) -> bytes:
    need(type(source_rows) is list and len(source_rows) == MAX_JSONL_ROWS,
         "SOURCE_CENSUS")
    rows = []
    seen = set()
    production = 0
    for row in source_rows:
        exact_keys(row, {"path", "bytes", "role", "sha256"}, "SOURCE_ROW")
        rel = source_rel(row["path"])
        need(rel not in seen and exact_int(row["bytes"]) and
             0 < row["bytes"] <= MAX_SINGLE_FILE and type(row["role"]) is str and
             row["role"] and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "SOURCE_ROW")
        seen.add(rel)
        production += rel.startswith("src/")
        rows.append({"kind": "source", "path": rel, "bytes": row["bytes"],
                     "sha256": row["sha256"], "role": row["role"]})
    rows.sort(key=lambda row: row["path"])
    need(len(seen) == 1084 and production == 850, "SOURCE_CENSUS")
    records = [{"kind": "header", "schema": "tsj-q100-geometric-prefix-source-v1",
                "sourceParent": PARENT, "rowCount": 1084, "productionCount": 850},
               *rows, {"kind": "footer", "rowCount": 1084}]
    lines = [compact_json(record) for record in records]
    need(all(0 < len(line) <= MAX_INVENTORY_LINE for line in lines) and
         sum(map(len, lines)) <= MAX_INVENTORY_BYTES, "SOURCE_INVENTORY_SIZE")
    return b"".join(lines)


def validate_inventory(raw: bytes, source_rows: list[dict], expected_sha: str) -> None:
    need(len(raw) <= MAX_INVENTORY_BYTES and sha(raw) == expected_sha,
         "SOURCE_INVENTORY_HASH")
    need(not raw.startswith(b"\xef\xbb\xbf") and raw.endswith(b"\n"),
         "SOURCE_INVENTORY_FORMAT")
    lines = raw.splitlines(keepends=True)
    need(len(lines) == MAX_JSONL_ROWS + 2 and all(
        line.endswith(b"\n") and 0 < len(line) <= MAX_INVENTORY_LINE for line in lines),
        "SOURCE_INVENTORY_FORMAT")
    records = []
    for line in lines:
        record = parse_json(line[:-1], "SOURCE_INVENTORY_JSON")
        need(compact_json(record) == line, "SOURCE_INVENTORY_FORMAT")
        records.append(record)
    expected = expected_inventory(source_rows)
    need(raw == expected, "SOURCE_INVENTORY_ROWS")
    exact_keys(records[0], {"kind", "schema", "sourceParent", "rowCount", "productionCount"},
               "SOURCE_INVENTORY_HEADER")
    need(records[0] == {"kind": "header", "schema": "tsj-q100-geometric-prefix-source-v1",
                        "sourceParent": PARENT, "rowCount": 1084, "productionCount": 850},
         "SOURCE_INVENTORY_HEADER")
    for record in records[1:-1]:
        exact_keys(record, {"kind", "path", "bytes", "sha256", "role"},
                   "SOURCE_INVENTORY_ROW")
        need(record["kind"] == "source", "SOURCE_INVENTORY_ROW")
    exact_keys(records[-1], {"kind", "rowCount"}, "SOURCE_INVENTORY_FOOTER")
    need(records[-1] == {"kind": "footer", "rowCount": 1084},
         "SOURCE_INVENTORY_FOOTER")


def inherited_context(pins: dict):
    prerequisite = exact_keys(pins["canaryPrerequisite"], {
        "root", "pinsPath", "pinsSha256", "qualificationPath", "qualificationSha256",
    }, "CANARY_CONTEXT")
    canary_root = canonical(prerequisite["root"], want_dir=True)
    need(canary_root == canonical(P8_ROOT, want_dir=True), "CANARY_CONTEXT")
    canary_pins_path = canonical(prerequisite["pinsPath"], want_dir=False)
    qualification_path = canonical(prerequisite["qualificationPath"], want_dir=False)
    need(canary_pins_path == canonical(CANARY_PINS, want_dir=False) and
         prerequisite["pinsSha256"] == CANARY_PINS_SHA and
         qualification_path == canonical(CANARY_QUALIFICATION, want_dir=False) and
         prerequisite["qualificationSha256"] == CANARY_QUALIFICATION_SHA,
         "CANARY_CONTEXT")
    canary_raw = stable_read(canary_pins_path, MAX_PINS_BYTES, "CANARY_PINS_READ")
    need(sha(canary_raw) == CANARY_PINS_SHA, "CANARY_PINS_HASH")
    canary = parse_json(canary_raw, "CANARY_PINS_JSON")
    exact_keys(canary, CANARY_TOP_KEYS, "CANARY_PINS_KEYS")
    need(canary_raw == canonical_json(canary), "CANARY_PINS_FORMAT")
    need(canary.get("schema") == "tsj-q100-geometric-canary-inputs-v1" and
         canary.get("parent") == PARENT, "CANARY_CONTEXT")
    repository = canonical(pins["repositoryRoot"], want_dir=True)
    need(canonical(canary["repositoryRoot"], want_dir=True) == repository and
         pins["parent"] == PARENT, "REPOSITORY_CONTEXT")

    p6_context = exact_keys(pins["p6Context"], {"pinsPath", "pinsSha256"}, "P6_CONTEXT")
    p6_path = canonical(p6_context["pinsPath"], want_dir=False)
    need(p6_path == canonical(P6_PINS, want_dir=False) and
         p6_context["pinsSha256"] == P6_PINS_SHA and
         canonical(canary["p6PinsPath"], want_dir=False) == p6_path and
         canary["p6PinsSha256"] == P6_PINS_SHA, "P6_CONTEXT")
    p6_raw = stable_read(p6_path, MAX_PINS_BYTES, "P6_PINS_READ")
    need(sha(p6_raw) == P6_PINS_SHA, "P6_PINS_HASH")

    qualification_raw = stable_read(qualification_path, 65536,
                                    "CANARY_QUALIFICATION_READ")
    qualification = validate_qualification(qualification_raw)
    p8_auditor = imported_canary_auditor(canary)
    canary_context = validate_canary_inputs(p8_auditor, canary)
    return (repository, canary, canary_raw, p6_path, p6_raw, qualification_raw,
            p8_auditor, canary_context)


def expected_roles_for(repository: Path) -> dict:
    return {
        "native": {
            "study": canonical(HERE / "source/CanonicalGeometricPrefixStudy.java", False),
            "inspector": canonical(HERE / "source/CanonicalGeometricReasonInspector.java", False),
        },
        "process": {
            "helper": canonical(repository / "scripts/VerifierIsolation.psm1", False),
            "reader": canonical(HERE / "read_geometric_prefix.py", False),
            "host": canonical(HERE / "run-study.ps1", False),
        },
        "input": {
            "maker": canonical(HERE / "make_study_inputs.py", False),
            "auditor": canonical(HERE / "audit_study_inputs.py", False),
        },
    }


def expected_tool_paths(canary: dict, p8_auditor) -> dict:
    python = canonical(canary["pythonExe"], want_dir=False)
    java_home = canonical(canary["javaHome"], want_dir=True)
    try:
        powershell = canonical(p8_auditor.powershell_executable(), want_dir=False)
    except Exception as exc:
        raise Reject("POWERSHELL_PATH") from exc
    return {"pythonExe": python, "javaHome": java_home, "powershellExe": powershell}


def source_closure(repository: Path, source_rows: list[dict]) -> int:
    need(type(source_rows) is list and len(source_rows) == 1084, "SOURCE_CENSUS")
    production = 0
    seen = set()
    for row in source_rows:
        exact_keys(row, {"path", "bytes", "role", "sha256"}, "SOURCE_ROW")
        rel = source_rel(row["path"])
        need(rel not in seen and exact_int(row["bytes"]) and
             0 < row["bytes"] <= MAX_SINGLE_FILE and type(row["role"]) is str and
             row["role"] and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "SOURCE_ROW")
        seen.add(rel)
        production += rel.startswith("src/")
        path = canonical(repository.joinpath(*PurePosixPath(rel).parts), want_dir=False)
        raw = stable_read(path, MAX_SINGLE_FILE, "SOURCE_READ")
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "SOURCE_CHANGED")
    need(len(seen) == 1084 and production == 850, "SOURCE_CENSUS")
    return production


def expected_file_paths(inventory_path: Path, canary_pins_path: Path,
                        qualification_path: Path, p6_path: Path,
                        tools: dict[str, Path]) -> list[Path]:
    paths = [canonical(HERE / Path(relative), want_dir=False) for relative in DIRECT_FILES]
    paths.extend((inventory_path, canary_pins_path, qualification_path, p6_path))
    java_home = tools["javaHome"]
    paths.extend((
        tools["pythonExe"],
        canonical(java_home / "bin/java.exe", want_dir=False),
        canonical(java_home / "bin/javac.exe", want_dir=False),
        tools["powershellExe"],
    ))
    need(len(paths) == EXPECTED_EXTRA_FILES and len({row_key(p) for p in paths}) == len(paths),
         "EXTRA_FILE_CENSUS")
    return paths


def validate_file_rows(rows: object, expected_paths: list[Path]) -> dict[str, dict]:
    need(type(rows) is list and len(rows) == EXPECTED_EXTRA_FILES, "EXTRA_FILE_CENSUS")
    found = rows_by_path(rows, {"path", "bytes", "sha256"}, "EXTRA_FILE_ROW")
    expected = {row_key(path): path for path in expected_paths}
    need(set(found) == set(expected), "EXTRA_FILE_CENSUS")
    order = [row["path"] for row in rows]
    need(order == sorted(order, key=str.casefold), "EXTRA_FILE_ORDER")
    total = 0
    for key, path in expected.items():
        row = found[key]
        raw = stable_read(path, MAX_SINGLE_FILE, "EXTRA_INPUT_HASH")
        need(len(raw) == row["bytes"] and sha(raw) == row["sha256"],
             "EXTRA_INPUT_HASH")
        total += len(raw)
    need(total <= MAX_FILES_BYTES, "EXTRA_FILE_BYTES")
    return found


def validate_contract_files(direct_raw: dict[str, bytes], file_rows: dict[str, dict]) -> None:
    need(sha(direct_raw["SOURCE_INVENTORY_R1.md"]) == SOURCE_INVENTORY_SPEC_SHA,
         "SOURCE_INVENTORY_CONTRACT_HASH")
    raw = direct_raw["CONTRACT_SET_R1.json"]
    need(len(raw) <= 65536, "CONTRACT_SET_SIZE")
    value = parse_json(raw, "CONTRACT_SET_JSON")
    need(raw == compact_json(value), "CONTRACT_SET_FORMAT")
    exact_keys(value, {"schema", "status", "sourceParent", "contracts"},
               "CONTRACT_SET_KEYS")
    need(value["schema"] == "tsj-q100-geometric-prefix-contract-set-v1" and
         value["status"] == "FROZEN_FOR_IMPLEMENTATION_ONLY" and
         value["sourceParent"] == PARENT and type(value["contracts"]) is list and
         len(value["contracts"]) == len(CONTRACT_FILES), "CONTRACT_SET_CONTEXT")
    for row, relative in zip(value["contracts"], CONTRACT_FILES):
        exact_keys(row, CONTRACT_ROW_KEYS, "CONTRACT_SET_ROW")
        need(row["path"] == relative and exact_int(row["bytes"], 1) and
             type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "CONTRACT_SET_ROW")
        path = canonical(HERE / Path(relative), want_dir=False)
        row_pin = file_rows.get(row_key(path))
        need(row_pin is not None and (row["bytes"], row["sha256"]) ==
             (row_pin["bytes"], row_pin["sha256"]), "CONTRACT_SET_BINDING")


def close_source_inventory(path: Path, expected_raw: bytes, expected_sha: str) -> None:
    raw = stable_read(path, MAX_INVENTORY_BYTES, "CLOSE_CHANGED")
    need(raw == expected_raw and sha(raw) == expected_sha, "CLOSE_CHANGED")


def validate_run(pins_path_value: str, expected_sha: str) -> dict:
    global EXPECTED_PYTHON_IMAGE
    need(sys.flags.isolated and sys.dont_write_bytecode, "PYTHON_ISOLATION_REQUIRED")
    need(type(expected_sha) is str and re.fullmatch(r"[0-9a-f]{64}", expected_sha) is not None,
         "INPUT_PINS_HASH")
    pins_path = canonical(pins_path_value, want_dir=False)
    pins_raw = stable_read(pins_path, MAX_PINS_BYTES, "INPUT_PINS_READ")
    need(sha(pins_raw) == expected_sha, "INPUT_PINS_HASH")
    pins = parse_json(pins_raw, "INPUT_PINS_JSON")
    exact_keys(pins, PINS_KEYS, "INPUT_PINS_KEYS")
    need(pins.get("schema") == SCHEMA and pins.get("parent") == PARENT,
         "INPUT_PINS_CONTEXT")
    need(pins_raw == canonical_json(pins), "INPUT_PINS_FORMAT")
    (repository, canary, canary_raw, p6_path, p6_raw, qualification_raw,
     p8_auditor, canary_context) = inherited_context(pins)
    prerequisite = pins["canaryPrerequisite"]
    qualification_path = canonical(prerequisite["qualificationPath"], want_dir=False)
    canary_pins_path = canonical(prerequisite["pinsPath"], want_dir=False)

    source_inventory = exact_keys(pins["sourceInventory"], {"path", "sha256"},
                                  "SOURCE_INVENTORY_PIN")
    inventory_path = canonical(source_inventory["path"], want_dir=False)
    need(inventory_path.name == "native-source-inventory.jsonl" and
         type(source_inventory["sha256"]) is str and
         re.fullmatch(r"[0-9a-f]{64}", source_inventory["sha256"]) is not None,
         "SOURCE_INVENTORY_PIN")
    inventory_raw = stable_read(inventory_path, MAX_INVENTORY_BYTES,
                                "SOURCE_INVENTORY_READ")
    source_rows = canary["sourceRows"]
    validate_inventory(inventory_raw, source_rows, source_inventory["sha256"])

    expected_roles = expected_roles_for(repository)
    roles = exact_keys(pins["roles"], {"native", "process", "input"}, "ROLE_KEYS")
    for name, expected in expected_roles.items():
        actual = exact_keys(roles[name], set(expected), "ROLE_KEYS")
        for role, path in expected.items():
            value = actual[role]
            need(type(value) is str and canonical(value, want_dir=False) == path,
                 "ROLE_MAPPING")
    helper_path = expected_roles["process"]["helper"]
    helper_rows = [row for row in source_rows
                   if row["path"] == "scripts/VerifierIsolation.psm1"]
    need(len(helper_rows) == 1 and helper_rows[0]["role"] == "BASE_SOURCE" and
         helper_rows[0]["path"] == helper_path.relative_to(repository).as_posix(),
         "HELPER_SOURCE_ROW")

    tools = expected_tool_paths(canary, p8_auditor)
    EXPECTED_PYTHON_IMAGE = tools["pythonExe"]
    tool_pins = exact_keys(pins["toolPaths"],
                           {"pythonExe", "javaHome", "powershellExe"}, "TOOL_PATHS")
    for name, expected in tools.items():
        value = tool_pins[name]
        need(type(value) is str and canonical(value, want_dir=name == "javaHome") == expected,
             "TOOL_PATHS")
    attest_python_image(tools["pythonExe"], PYTHON_SHA)

    expected_paths = expected_file_paths(inventory_path, canary_pins_path,
                                         qualification_path, p6_path, tools)
    file_rows = validate_file_rows(pins["files"], expected_paths)

    direct_raw = {}
    for relative in DIRECT_FILES:
        direct_path = canonical(HERE / Path(relative), want_dir=False)
        raw = stable_read(direct_path, MAX_SINGLE_FILE, "DIRECT_INPUT_READ")
        direct_raw[relative] = raw
        row = file_rows.get(row_key(direct_path))
        need(row is not None and len(raw) == row["bytes"] and sha(raw) == row["sha256"],
             "EXTRA_INPUT_HASH")
    validate_contract_files(direct_raw, file_rows)

    rows = rows_by_path(pins["files"], {"path", "bytes", "sha256"}, "EXTRA_FILE_ROW")
    for path, expected in (
        (canary_pins_path, CANARY_PINS_SHA),
        (qualification_path, CANARY_QUALIFICATION_SHA),
        (p6_path, P6_PINS_SHA),
        (inventory_path, source_inventory["sha256"]),
        (tools["pythonExe"], PYTHON_SHA),
        (canonical(tools["javaHome"] / "bin/java.exe", False), JAVA_SHA),
        (canonical(tools["javaHome"] / "bin/javac.exe", False), JAVAC_SHA),
        (tools["powershellExe"], POWERSHELL_SHA),
    ):
        row = rows.get(row_key(path))
        need(row is not None and row["sha256"] == expected, "PINNED_EXTERNAL_FILE")
    # Close the genuine CANARY/P6 context after consuming BASE inputs, without
    # relabeling or rebuilding the parent pins.
    close_canary_inputs(p8_auditor, canary, canary_raw, canary_context)
    need(stable_read(pins_path, MAX_PINS_BYTES, "CLOSE_CHANGED") == pins_raw and
         stable_read(canary_pins_path, MAX_PINS_BYTES, "CLOSE_CHANGED") == canary_raw and
         stable_read(qualification_path, 65536, "CLOSE_CHANGED") == qualification_raw and
         stable_read(p6_path, MAX_PINS_BYTES, "CLOSE_CHANGED") == p6_raw,
         "CLOSE_CHANGED")
    validate_file_rows(pins["files"], expected_paths)
    close_source_inventory(inventory_path, inventory_raw, source_inventory["sha256"])
    production = source_closure(repository, source_rows)
    need(production == 850, "SOURCE_CENSUS")
    attest_python_image(tools["pythonExe"], PYTHON_SHA)
    result = {
        "status": "PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY",
        "pinsSha256": expected_sha,
        "sourceInventorySha256": source_inventory["sha256"],
        "sourceRows": 1084,
        "productionFiles": production,
        "extraFiles": len(file_rows),
    }
    return result


def main() -> int:
    if len(sys.argv) != 3:
        sys.stderr.write("P8_INPUT_AUDIT_FAIL USAGE\n")
        return 2
    try:
        result = validate_run(sys.argv[1], sys.argv[2])
        need(EXPECTED_PYTHON_IMAGE is not None, "PYTHON_IMAGE_PATH")
        attest_python_image(EXPECTED_PYTHON_IMAGE, PYTHON_SHA)
        line = "PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY " + json.dumps(
            result, sort_keys=True, separators=(",", ":")) + "\n"
        encoded = line.encode("ascii")
        if len(encoded) > MAX_RESULT_BYTES:
            raise Reject("RESULT_SIZE")
        sys.stdout.buffer.write(encoded)
        sys.stdout.buffer.flush()
        return 0
    except Reject as exc:
        sys.stderr.write("P8_INPUT_AUDIT_FAIL " + exc.code + "\n")
        return 2
    except Exception:
        sys.stderr.write("P8_INPUT_AUDIT_FAIL INPUTS_INVALID\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
