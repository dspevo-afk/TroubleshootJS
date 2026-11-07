"""Source-authenticated in-memory controls for the BASE reason validator."""
from __future__ import annotations

import sys

if not sys.flags.isolated:
    sys.stderr.write("P8_PREFIX_READER_TEST_FAIL ISOLATED_MODE_REQUIRED\n")
    raise SystemExit(2)
if not sys.dont_write_bytecode:
    sys.stderr.write("P8_PREFIX_READER_TEST_FAIL BYTECODE_DISABLED_REQUIRED\n")
    raise SystemExit(2)

import copy
import ctypes
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import types


PARENT = "0891474cadc4b9e00d7911e81c124721a11efb87"
SCHEMA = "tsj-q100-geometric-prefix-study-inputs-v1"
PYTHON_SHA256 = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
READER_MODULE_NAME = "authenticated_geometric_prefix_reader"
MAX_PINS_BYTES = 4 * 1024 * 1024
MAX_FILE_BYTES = 8 * 1024 * 1024
MAX_TOOL_BYTES = 100 * 1024 * 1024
MAX_OUTPUT_BYTES = 40 * 1024 * 1024
MAX_OUTPUT_FILES = 512
MAX_OUTPUT_DIRS = 4096
MAX_SUMMARY_BYTES = 65536
PINS_KEYS = {
    "schema", "repositoryRoot", "parent", "canaryPrerequisite", "p6Context",
    "sourceInventory", "roles", "files", "toolPaths",
}
FILE_ROW_KEYS = {"path", "bytes", "sha256"}
SUMMARY_KEYS = {
    "schema", "status", "layoutQualification", "inputSha256", "sourceSha256",
    "sourceParent", "mode", "sourceClosure", "processes", "operations", "parity",
    "reasonCounts", "reasonRows", "cleanup", "outputClosure", "limits",
}
CASE_EXPECTATIONS = (
    ("header-input", "REASON_HEADER_BINDING"),
    ("header-source", "REASON_HEADER_BINDING"),
    ("query-order", "REASON_QUERY_ORDER"),
    ("query-identity", "REASON_QUERY_IDENTITY"),
    ("event-coordinates", "REASON_EVENT_CORRELATION"),
    ("owner-type", "REASON_EVENT_FIELD_TYPE"),
    ("returned-null", "REASON_QUERY_RESULT"),
    ("missing-footer", "REASON_FRAMING"),
    ("units", "REASON_UNIT_ARITHMETIC"),
    ("event-extra-key", "REASON_EVENT_KEYS"),
    ("missing-fine", "REASON_EVENT_COUNT"),
    ("null-fine", "REASON_MODE_EVENTS"),
)
HERE = Path(__file__).absolute().parent
EXPECTED_PYTHON_PATH: Path | None = None


class Reject(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(ok: bool, code: str) -> None:
    if not ok:
        raise Reject(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def identity(info: os.stat_result) -> tuple[int, int, int, int, int]:
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_nlink)


def no_reparse(path: Path, code: str = "REPARSE_PATH") -> None:
    for item in (path, *path.parents):
        try:
            info = item.lstat()
        except OSError as exc:
            raise Reject("PATH_MISSING") from exc
        need(not stat.S_ISLNK(info.st_mode) and
             not (getattr(info, "st_file_attributes", 0) & 0x400), code)


def canonical(value: str | Path, code: str, kind: str | None = None) -> Path:
    need(type(value) is str or isinstance(value, Path), code)
    path = Path(value)
    need(path.is_absolute() and str(path) == os.path.abspath(str(path)), code)
    no_reparse(path, code)
    try:
        resolved = path.resolve(strict=True)
        info = path.stat()
    except (OSError, RuntimeError) as exc:
        raise Reject(code) from exc
    need(os.path.normcase(str(resolved)) == os.path.normcase(str(path)), code)
    if kind == "file":
        need(stat.S_ISREG(info.st_mode), code)
    elif kind == "dir":
        need(stat.S_ISDIR(info.st_mode), code)
    return resolved


def stable_read(path_value: str | Path, maximum: int, code: str) -> bytes:
    path = canonical(path_value, code, "file")
    try:
        before = path.stat()
        need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= maximum,
             code + "_SIZE")
        with path.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            raw = stream.read(maximum + 1)
            closed = os.fstat(stream.fileno())
        after = path.stat()
    except Reject:
        raise
    except OSError as exc:
        raise Reject(code + "_READ") from exc
    need(identity(before) == identity(opened) == identity(closed) == identity(after)
         and before.st_ctime_ns == after.st_ctime_ns
         and opened.st_ctime_ns == closed.st_ctime_ns
         and len(raw) == after.st_size <= maximum, code + "_CHANGED")
    return raw


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
    need(type(length) is int and 0 < length < len(buffer) and last_error == 0
         and len(buffer.value) == length, "PYTHON_IMAGE_API")
    path = Path(buffer.value)
    need(path.is_absolute() and str(path) == os.path.abspath(str(path)),
         "PYTHON_IMAGE_PATH")
    no_reparse(path, "PYTHON_IMAGE_PATH")
    try:
        resolved = path.resolve(strict=True)
        info = path.stat()
    except (OSError, RuntimeError) as exc:
        raise Reject("PYTHON_IMAGE_PATH") from exc
    need(os.path.normcase(str(resolved)) == os.path.normcase(str(path))
         and stat.S_ISREG(info.st_mode), "PYTHON_IMAGE_PATH")
    return resolved


def attest_python_image(path: Path, expected_sha: str) -> None:
    expected = canonical(path, "PYTHON_IMAGE_PATH", "file")
    actual = current_process_image_path()
    need(os.path.normcase(str(actual)) == os.path.normcase(str(expected)),
         "PYTHON_IMAGE_PATH")
    actual_bytes = stable_read(actual, MAX_TOOL_BYTES, "PYTHON_IMAGE_READ")
    need(sha(actual_bytes) == expected_sha, "PYTHON_IMAGE_HASH")


def parse_json(raw: bytes, code: str) -> object:
    need(not raw.startswith(b"\xef\xbb\xbf"), "JSON_BOM")

    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, "DUPLICATE_JSON_KEY")
            result[key] = value
        return result

    def finite_float(text: str) -> float:
        value = float(text)
        need(math.isfinite(value), "NONFINITE_JSON")
        return value

    def bad_constant(_text: str):
        raise Reject("NONFINITE_JSON")

    try:
        return json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                          parse_float=finite_float, parse_constant=bad_constant)
    except Reject:
        raise
    except (UnicodeError, json.JSONDecodeError, ValueError) as exc:
        raise Reject(code) from exc


def exact_obj(value: object, keys: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == keys, code)
    return value


def canonical_pins(raw: bytes, value: object) -> bytes:
    try:
        return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError) as exc:
        raise Reject("INPUT_PINS_FORMAT") from exc


def compact_json(value: object, *, sort_keys: bool = True) -> bytes:
    try:
        return (json.dumps(value, sort_keys=sort_keys, separators=(",", ":"),
                           ensure_ascii=True, allow_nan=False) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError) as exc:
        raise Reject("REASON_JSON_SERIALIZE") from exc


def normpath(path: Path) -> str:
    return os.path.normcase(str(path))


def file_rows(pins: dict) -> dict[str, dict]:
    rows = pins.get("files")
    need(type(rows) is list and len(rows) == 25, "EXTRA_FILE_CENSUS")
    result = {}
    for row in rows:
        exact_obj(row, FILE_ROW_KEYS, "EXTRA_FILE_ROW")
        path = canonical(row["path"], "EXTRA_FILE_PATH", "file")
        need(type(row["bytes"]) is int and 0 < row["bytes"] <= 100 * 1024 * 1024
             and type(row["sha256"]) is str
             and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "EXTRA_FILE_ROW")
        key = normpath(path)
        need(key not in result, "EXTRA_FILE_DUPLICATE")
        result[key] = {"path": path, "bytes": row["bytes"], "sha256": row["sha256"]}
    return result


def verify_row(rows: dict[str, dict], path: Path, code: str) -> bytes:
    key = normpath(path)
    row = rows.get(key)
    need(row is not None, code)
    raw = stable_read(path, 100 * 1024 * 1024, code)
    need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], code)
    return raw


def authenticated_sources(pins_path_value: str, pins_sha: str) -> tuple[Path, Path, bytes, bytes, dict]:
    global EXPECTED_PYTHON_PATH
    need(type(pins_sha) is str and re.fullmatch(r"[0-9a-f]{64}", pins_sha) is not None,
         "INPUT_PINS_HASH")
    pins_path = canonical(pins_path_value, "INPUT_PINS_PATH", "file")
    pins_raw = stable_read(pins_path, MAX_PINS_BYTES, "INPUT_PINS_READ")
    need(sha(pins_raw) == pins_sha, "INPUT_PINS_HASH")
    pins = parse_json(pins_raw, "INPUT_PINS_JSON")
    exact_obj(pins, PINS_KEYS, "INPUT_PINS_KEYS")
    need(pins.get("schema") == SCHEMA and pins.get("parent") == PARENT
         and canonical_pins(pins_raw, pins) == pins_raw, "INPUT_PINS_CONTEXT")

    rows = file_rows(pins)
    self_path = canonical(Path(__file__).absolute(), "SELF_PATH", "file")
    need(os.path.normcase(str(self_path)) ==
         os.path.normcase(str(canonical(HERE / "test_read_geometric_prefix.py", "SELF_PATH", "file"))),
         "SELF_PATH")
    self_raw = verify_row(rows, self_path, "SELF_PIN")

    roles = exact_obj(pins["roles"], {"native", "process", "input"}, "ROLE_KEYS")
    process_roles = exact_obj(roles["process"], {"helper", "reader", "host"}, "ROLE_KEYS")
    reader_path = canonical(process_roles["reader"], "READER_ROLE_PATH", "file")
    expected_reader_path = canonical(HERE / "read_geometric_prefix.py", "READER_ROLE_PATH", "file")
    need(os.path.normcase(str(reader_path)) == os.path.normcase(str(expected_reader_path)),
         "READER_ROLE_PATH")
    reader_raw = verify_row(rows, reader_path, "READER_PIN")

    tool_paths = exact_obj(pins["toolPaths"],
                           {"pythonExe", "javaHome", "powershellExe"}, "TOOL_PATHS")
    python_path = canonical(tool_paths["pythonExe"], "PYTHON_TOOL_PATH", "file")
    python_row = rows.get(normpath(python_path))
    need(python_row is not None and python_row["sha256"] == PYTHON_SHA256,
         "PYTHON_TOOL_PIN")
    need(len(verify_row(rows, python_path, "PYTHON_TOOL_PIN")) == python_row["bytes"],
         "PYTHON_TOOL_PIN")
    attest_python_image(python_path, PYTHON_SHA256)
    EXPECTED_PYTHON_PATH = python_path
    return pins_path, reader_path, reader_raw, self_raw, {
        "pins": pins, "pinsRaw": pins_raw, "pinsSha": pins_sha, "rows": rows,
        "pythonPath": python_path,
    }


def load_reader(reader_path: Path, raw: bytes) -> types.ModuleType:
    module = types.ModuleType(READER_MODULE_NAME)
    module.__file__ = str(reader_path)
    module.__package__ = ""
    sys.modules[READER_MODULE_NAME] = module
    try:
        code = compile(raw, str(reader_path), "exec", dont_inherit=True)
        exec(code, module.__dict__)
    except Exception as exc:
        raise Reject("READER_IMPORT_FAILED") from exc
    need(callable(getattr(module, "validate_bundle", None)) and
         callable(getattr(module, "validate_reason_sidecar", None)), "READER_API")
    return module


def output_fingerprint(root_value: str | Path) -> list[dict]:
    root = canonical(root_value, "OUTPUT_ROOT", "dir")
    stack = [root]
    rows = []
    seen = set()
    total = 0
    directory_count = 0
    while stack:
        directory = stack.pop()
        no_reparse(directory, "OUTPUT_REPARSE")
        try:
            with os.scandir(directory) as scan:
                entries = sorted(list(scan), key=lambda entry: entry.name.casefold())
        except OSError as exc:
            raise Reject("OUTPUT_SCAN") from exc
        for entry in entries:
            path = Path(entry.path)
            try:
                info = path.lstat()
            except OSError as exc:
                raise Reject("OUTPUT_CHANGED") from exc
            need(not stat.S_ISLNK(info.st_mode) and
                 not (getattr(info, "st_file_attributes", 0) & 0x400), "OUTPUT_REPARSE")
            if stat.S_ISDIR(info.st_mode):
                directory_count += 1
                need(directory_count <= MAX_OUTPUT_DIRS, "OUTPUT_SIZE")
                stack.append(path)
                continue
            need(stat.S_ISREG(info.st_mode), "OUTPUT_FILE_TYPE")
            raw = stable_read(path, MAX_FILE_BYTES, "OUTPUT_FILE")
            relative = path.relative_to(root).as_posix()
            rel = PurePosixPath(relative)
            need(relative and not rel.is_absolute() and
                 all(part not in ("", ".", "..") for part in rel.parts) and
                 rel.as_posix() == relative, "OUTPUT_PATH")
            key = os.path.normcase(relative)
            need(key not in seen, "OUTPUT_DUPLICATE")
            seen.add(key)
            total += len(raw)
            need(len(rows) < MAX_OUTPUT_FILES and total <= MAX_OUTPUT_BYTES,
                 "OUTPUT_SIZE")
            rows.append({"path": relative, "bytes": len(raw), "sha256": sha(raw)})
    rows.sort(key=lambda row: row["path"])
    return rows


def validate_state_fingerprint(value: object) -> list[dict]:
    need(type(value) is list and len(value) <= MAX_OUTPUT_FILES, "OUTPUT_FINGERPRINT")
    rows = []
    seen = set()
    total = 0
    for row in value:
        exact_obj(row, FILE_ROW_KEYS, "OUTPUT_FINGERPRINT")
        relative = row["path"]
        need(type(relative) is str and relative and "\\" not in relative,
             "OUTPUT_FINGERPRINT")
        rel = PurePosixPath(relative)
        need(not rel.is_absolute() and all(part not in ("", ".", "..") for part in rel.parts)
             and rel.as_posix() == relative, "OUTPUT_FINGERPRINT")
        need(type(row["bytes"]) is int and 0 <= row["bytes"] <= MAX_FILE_BYTES
             and type(row["sha256"]) is str
             and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             "OUTPUT_FINGERPRINT")
        key = os.path.normcase(relative)
        need(key not in seen, "OUTPUT_FINGERPRINT")
        seen.add(key)
        total += row["bytes"]
        rows.append({"path": relative, "bytes": row["bytes"], "sha256": row["sha256"]})
    need(total <= MAX_OUTPUT_BYTES and
         [row["path"] for row in rows] == sorted(row["path"] for row in rows),
         "OUTPUT_FINGERPRINT")
    return rows


def parse_reason_rows(reader: types.ModuleType, raw: bytes) -> list[dict]:
    result = reader.read_rows(raw, "REASON")
    need(type(result) is list, "REASON_ROWS")
    return result


def serialize_reason_rows(rows: list[dict]) -> bytes:
    # Preserve the native-authored insertion order for reason JSONL. Sorted
    # keys are reserved for the harness result object, not these byte contracts.
    return b"".join(compact_json(row, sort_keys=False) for row in rows)


def expect_rejection(reader: types.ModuleType, raw: bytes, context: dict,
                     expected_code: str) -> str:
    try:
        reader.validate_reason_sidecar(raw, context)
    except reader.Reject as exc:
        actual = getattr(exc, "code", None)
        need(type(actual) is str, "FIXTURE_REJECTION_CODE_TYPE")
        need(actual == expected_code, "FIXTURE_REJECTION_CODE_MISMATCH")
        return actual
    except Exception as exc:
        raise Reject("FIXTURE_UNEXPECTED_EXCEPTION") from exc
    raise Reject("FIXTURE_ACCEPTED")


def mutate_case(name: str, observe_rows: list[dict], null_rows: list[dict],
                observe_context: dict, null_context: dict,
                observe_raw: bytes, null_raw: bytes) -> tuple[bytes, dict]:
    rows = copy.deepcopy(observe_rows)
    context = observe_context
    if name == "header-input":
        rows[0]["inputSha256"] = "0" * 64
    elif name == "header-source":
        rows[0]["sourceSha256"] = "0" * 64
    elif name == "query-order":
        rows[1], rows[2] = rows[2], rows[1]
    elif name == "query-identity":
        rows[1]["identity"]["startPadId"] = "UNKNOWN_PAD"
    elif name == "event-coordinates":
        rows[1]["fineEvents"][0]["toGridX"] += 1
    elif name == "owner-type":
        rows[1]["fineEvents"][0]["ownerIndex"] = True
    elif name == "returned-null":
        rows[1]["allowed"] = None
    elif name == "missing-footer":
        need(observe_raw.endswith(b"\n"), "REASON_POSITIVE_FRAMING")
        original_lines = observe_raw.splitlines(keepends=True)
        need(len(original_lines) == 16 and original_lines[-1].endswith(b"\n"),
             "REASON_POSITIVE_FRAMING")
        return b"".join(original_lines[:-1]), context
    elif name == "units":
        rows[-1]["totalObservationUnits"] += 1
    elif name == "event-extra-key":
        rows[1]["fineEvents"][0]["unexpectedField"] = 0
    elif name == "missing-fine":
        rows[1]["fineEvents"] = []
    elif name == "null-fine":
        rows = copy.deepcopy(null_rows)
        context = null_context
        need(len(rows) == 16 and len(observe_rows[1]["fineEvents"]) == 1
             and rows[1]["identity"] == observe_rows[1]["identity"],
             "NULL_POSITIVE_FRAMING")
        rows[1]["fineEvents"] = [copy.deepcopy(observe_rows[1]["fineEvents"][0])]
        return serialize_reason_rows(rows), context
    else:
        raise Reject("UNKNOWN_FIXTURE")
    return serialize_reason_rows(rows), context


def run_semantic_controls(reader: types.ModuleType, state: dict,
                          before_fingerprint: list[dict], output_root: Path) -> dict:
    contexts = state.get("reasonContexts")
    raws = state.get("reasonRaws")
    need(type(contexts) is dict and set(contexts) == {"NULL", "OBSERVE"}
         and type(raws) is dict and set(raws) == {"NULL", "OBSERVE"},
         "REASON_CONTEXTS")
    observe_context = contexts["OBSERVE"]
    null_context = contexts["NULL"]
    observe_raw = raws["OBSERVE"]
    null_raw = raws["NULL"]
    need(type(observe_context) is dict and observe_context.get("mode") == "OBSERVE"
         and type(null_context) is dict and null_context.get("mode") == "NULL"
         and type(observe_raw) is bytes and type(null_raw) is bytes,
         "REASON_CONTEXTS")
    need(state.get("reasonContext") == observe_context
         and state.get("reasonRaw") == observe_raw, "REASON_CONTEXT_ALIAS")

    # The suite bundle and both unchanged sidecars are validated before any
    # synthetic mutation is constructed.
    observe_rows = parse_reason_rows(reader, observe_raw)
    null_rows = parse_reason_rows(reader, null_raw)
    need(len(observe_rows) == 16 and len(null_rows) == 16,
         "REASON_POSITIVE_FRAMING")
    need(serialize_reason_rows(observe_rows) == observe_raw
         and serialize_reason_rows(null_rows) == null_raw,
         "REASON_POSITIVE_CANONICAL")
    observe_fine_count = sum(len(row.get("fineEvents", [])) for row in observe_rows[1:-1]
                             if type(row) is dict and type(row.get("fineEvents")) is list)
    observe_coarse_count = sum(len(row.get("coarseEvents", [])) for row in observe_rows[1:-1]
                               if type(row) is dict and type(row.get("coarseEvents")) is list)
    null_fine_count = sum(len(row.get("fineEvents", [])) for row in null_rows[1:-1]
                          if type(row) is dict and type(row.get("fineEvents")) is list)
    observe_positive = reader.validate_reason_sidecar(observe_raw, observe_context)
    null_positive = reader.validate_reason_sidecar(null_raw, null_context)
    need(observe_positive.get("status") == "COMPLETE_REASON_OBSERVATIONS_ONLY"
         and observe_positive.get("reasonCount") == observe_fine_count
         and observe_positive.get("coarseCount") == observe_coarse_count
         and len(observe_positive.get("reasonRows", [])) == observe_fine_count
         and observe_positive.get("raw") == observe_raw,
         "OBSERVE_POSITIVE_CONTROL")
    need(null_positive.get("status") == "COMPLETE_NULL_CONTROL_ONLY"
         and null_positive.get("reasonCount") == null_fine_count == 0
         and null_positive.get("raw") == null_raw, "NULL_POSITIVE_CONTROL")

    passed = []
    for case_id, expected_code in CASE_EXPECTATIONS:
        raw, context = mutate_case(case_id, observe_rows, null_rows,
                                   observe_context, null_context,
                                   observe_raw, null_raw)
        actual_code = expect_rejection(reader, raw, context, expected_code)
        passed.append({"id": case_id, "expectedCode": expected_code,
                       "actualCode": actual_code, "status": "PASS_EXPECTED_REJECTION"})

    need(output_fingerprint(output_root) == before_fingerprint,
         "OUTPUTS_CHANGED_BY_FIXTURES")
    return {
        "schema": "tsj-q100-geometric-prefix-semantic-controls-v1",
        "status": "PASS_SYNTHETIC_REASON_CONTROLS_ONLY",
        "syntheticFixtures": True,
        "passed": len(passed),
        "positiveControl": True,
        "cases": passed,
        "genuineOutputsUnchanged": True,
        "layoutQualification": False,
    }


def run(pins_path_arg: str, pins_sha: str, output_root_arg: str,
        parent_receipt_arg: str) -> dict:
    pins_path, reader_path, reader_raw, _self_raw, source_context = \
        authenticated_sources(pins_path_arg, pins_sha)
    output_root = canonical(output_root_arg, "OUTPUT_ROOT", "dir")
    parent_path = canonical(parent_receipt_arg, "PARENT_RECEIPT_PATH", "file")
    parent_before = stable_read(parent_path, MAX_FILE_BYTES, "PARENT_RECEIPT")
    before = output_fingerprint(output_root)
    reader = load_reader(reader_path, reader_raw)
    try:
        summary, state = reader.validate_bundle(
            str(pins_path), pins_sha, str(output_root), str(parent_path),
            return_context=True,
        )
    except reader.Reject as exc:
        raise Reject("GENUINE_BUNDLE_" + str(getattr(exc, "code", "UNKNOWN"))) from exc
    except Exception as exc:
        raise Reject("GENUINE_BUNDLE_UNEXPECTED") from exc
    need(type(summary) is dict and set(summary) == SUMMARY_KEYS
         and summary.get("schema") == "tsj-q100-geometric-prefix-reader-v1"
         and summary.get("status") == "PASS_Q100_GEOMETRIC_PREFIX_OBSERVATIONS_ONLY"
         and summary.get("layoutQualification") is False
         and summary.get("inputSha256") == pins_sha
         and summary.get("sourceSha256") == source_context["pins"]["sourceInventory"]["sha256"]
         and summary.get("sourceParent") == PARENT and summary.get("mode") == "SUITE",
         "GENUINE_BUNDLE_SUMMARY")
    need(type(state) is dict, "GENUINE_BUNDLE_STATE")
    reported_fingerprint = validate_state_fingerprint(state.get("outputFingerprint"))
    after_bundle = output_fingerprint(output_root)
    need(after_bundle == before and after_bundle == reported_fingerprint,
         "OUTPUTS_CHANGED_DURING_VALIDATION")
    need(stable_read(parent_path, MAX_FILE_BYTES, "PARENT_RECEIPT") == parent_before,
         "PARENT_RECEIPT_CHANGED")
    semantic = run_semantic_controls(reader, state, before, output_root)
    need(output_fingerprint(output_root) == before and
         stable_read(parent_path, MAX_FILE_BYTES, "PARENT_RECEIPT") == parent_before,
         "GENUINE_EVIDENCE_CHANGED")
    attest_python_image(source_context["pythonPath"], PYTHON_SHA256)
    need(semantic["passed"] == 12, "SEMANTIC_CASE_CENSUS")
    return semantic


def main() -> int:
    if len(sys.argv) != 5:
        sys.stderr.write("P8_PREFIX_READER_TEST_FAIL USAGE\n")
        return 2
    try:
        result = run(*sys.argv[1:])
        need(EXPECTED_PYTHON_PATH is not None, "PYTHON_IMAGE_PATH")
        attest_python_image(EXPECTED_PYTHON_PATH, PYTHON_SHA256)
        raw = compact_json(result)
        need(len(raw) <= MAX_SUMMARY_BYTES, "SEMANTIC_SUMMARY_SIZE")
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
        return 0
    except Reject as exc:
        sys.stderr.write("P8_PREFIX_READER_TEST_FAIL " + exc.code + "\n")
        return 2
    except Exception:
        sys.stderr.write("P8_PREFIX_READER_TEST_FAIL UNEXPECTED_FAILURE\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
