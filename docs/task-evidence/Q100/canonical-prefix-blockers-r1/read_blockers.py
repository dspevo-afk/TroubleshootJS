"""Strict reader for the frozen canonical prefix blocker attribution run.

The P4 reader is imported only after its qualification and published byte copies
are pinned. Its operation, graph/facts, transport, and route projection validators
remain the independent oracle for unchanged P4 behavior. This reader owns the P5
host, new index/envelope, inspector exception, and blocker attribution contracts.
"""
from __future__ import annotations

import collections
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

sys.dont_write_bytecode = True

INPUT_SCHEMA = "tsj-canonical-prefix-blocker-inputs-v1"
HOST_SCHEMA = "tsj-canonical-prefix-blocker-host-v1"
HOST_RESULT_SCHEMA = "tsj-canonical-prefix-blocker-host-result-v1"
INDEX_SCHEMA = "tsj-canonical-prefix-blocker-index-v1"
NATIVE_SCHEMA = "tsj-canonical-prefix-blocker-native-v1"
EXCEPTION_SCHEMA = "tsj-canonical-prefix-blocker-exception-v1"
ATTRIBUTION_SCHEMA = "tsj-canonical-prefix-blocker-observations-v1"
CANARY_SCHEMA = "tsj-canonical-prefix-blocker-canary-v1"
TRANSPORT_SCHEMA = "tsj-canonical-prefix-transport-v1"
FACTS_SCHEMA = "tsj-canonical-prefix-facts-v1"
CUT_SCHEMA = "tsj-canonical-prefix-cut-queries-v1"
READER_SCHEMA = "tsj-canonical-prefix-blocker-reader-result-v1"

PARENT = "83027040b3314b3131ae1d315db58f51a3c4231d"
BASELINE_SHA = "98c19ae62429a1b7806ebdc7cdb977ec817297b731a2a6f9e52897439db2c11a"
PLAN_SHA = "6128ea850b934a36864c6be048419c520afb942c380911186ba0c2d26bdbf05c"
PROTOCOL_SHA = "d03ab20bf2736f1c023498fcbdd13c6e76099080bf226f5da74def0a242d5e94"
ADDENDUM_SHA = "0c8607e6b7e57c80224ba57a5d69a09bf461a538418caf290986f455e9205dda"
CUT_SHA = "1a1d7739d3a8ce4409896b56477c68f38b12b8d77f0d0264e16b0b3cf1bf70c5"
P4_QUALIFICATION_SHA = "0cc91c276bba8b6e1de867a732d9f34d3cea78dcadb28250ef99a3948687ce53"
P4_INPUT_SHA = "ca06e1ab3c1a7bcb11dab90cba7b64dc9d95b031b09d76678f408d9fa0f0f7e0"
P4_READER_SHA = "74b05764a1b1cea405b92f50b6ca781ef44a56c0e61680f6abd399701e7be480"
P4_READER_BYTES = 70178
OLD_BRANCH_READER_SHA = "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"
OLD_BRANCH_READER_BYTES = 137864
P4_CONTROL_RECEIPT_SHA = "9692b968119b78d9e10d2fda3cd609cf5343a9924a0e55b1ceea1008199eea9d"
P4_CONTROL_RECEIPT_BYTES = 377136
P4_CAPTURE_TRANSPORT_SHA = "75efe4303fa987f8ea499fa0e3c44c7743866ad213e158f2c5b58a38ccaf50dc"
P4_CAPTURE_TRANSPORT_BYTES = 5138203
WIRE_SHA = "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb"
GRAPH_SHA = "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a"
PREFIX_SHA = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
RAW_PLACEMENT_SHA = "6553ad3df059c6bfe2980efe39ff951d3f6322fc0694b2c96e26508f8d3dfe2e"
OVERFLOW_MARKER = "CANONICAL_PREFIX_CAPTURE_LIMIT"
MAIN_CLASS = "com.lushprojects.circuitjs1.client.CanonicalPrefixCaptureStudy"
WRITER_RELATIVE = "tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java"
OLD_READER_RELATIVE = "tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py"
EXCLUDED_JAVA = {
    "src/com/lushprojects/circuitjs1/client/CirSim.java",
    "src/com/lushprojects/circuitjs1/client/ChipElm.java",
    "src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java",
    "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
}
MODE_FILE_ORDER = ("router", "support", "writer", "adapter", "inspector", "queries")
MODE_FILE_KEYS = set(MODE_FILE_ORDER)
PIN_FIELDS = {
    "schema", "repositoryRoot", "historicalRoot", "parent",
    "baselineManifestPath", "baselineManifestSha256", "sourceRows", "changedRows",
    "modeFiles", "bridgeRoot", "bridgeManifest", "preflight", "controlRunRoot",
    "parentControlReceipt", "files", "planPath", "planSha256", "protocolPath",
    "protocolSha256", "addendumPath", "addendumSha256", "cutInputPath",
    "cutInputSha256", "p4QualificationPath", "p4QualificationSha256",
    "p4ReaderPath", "p4InputPinsPath", "p4InputPinsSha256", "p4CaptureRunRoot",
}
LIMITS = {
    "compileMs": 60000,
    "nativeMs": 480000,
    "negativeMs": 15000,
    "negativeWallMs": 135000,
    "cleanupMs": 120000,
    "outerMs": 1800000,
    "operationNanos": 90000000000,
    "groupNanos": 5000000000,
    "groups": 640,
    "receiptBytes": 8388608,
    "indexBytes": 65536,
    "lineBytes": 65536,
    "chunkBytes": 32768,
    "aggregateBytes": 8388608,
    "sourceBytes": 536870912,
    "sourceCount": 1084,
    "compileSources": 1079,
    "observationUnits": 32768,
    "unitEntries": 4096,
    "cloneEntries": 1048576,
    "attributionBytes": 65536,
    "queries": 22,
}
HEX = re.compile(r"[0-9a-f]{64}\Z")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SEED = re.compile(r"(?:0|-?[1-9][0-9]*)\Z")
RECORD_FIELDS = (
    "FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
    "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
    "TerminationProven",
)


class Rejected(ValueError):
    """Sanitized reader rejection; the message is a stable short reason code."""


def need(condition: Any, code: str) -> None:
    if not condition:
        raise Rejected(code)


def _pairs(rows: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    result: Dict[str, Any] = {}
    for key, value in rows:
        need(key not in result, "JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _parse_int(text: str) -> int:
    need(text != "-0", "JSON_NEGATIVE_ZERO")
    return int(text)


def _parse_float(text: str) -> float:
    value = float(text)
    need(math.isfinite(value), "JSON_NONFINITE")
    need(not (text.startswith("-") and value == 0.0), "JSON_NEGATIVE_ZERO")
    return value


def _reject_constant(_text: str) -> None:
    raise Rejected("JSON_NONFINITE")


def strict_json(raw: bytes) -> Any:
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_ENCODING")
    try:
        text = raw.decode("utf-8", "strict")
        return json.loads(
            text, object_pairs_hook=_pairs, parse_int=_parse_int,
            parse_float=_parse_float, parse_constant=_reject_constant,
        )
    except Rejected:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, TypeError):
        raise Rejected("JSON_INVALID") from None


def _keys(value: Any, names: Iterable[str], code: str) -> None:
    need(type(value) is dict and set(value) == set(names), code)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _digest(value: Any, code: str = "SHA256_INVALID") -> str:
    need(type(value) is str and HEX.fullmatch(value) is not None, code)
    return value


def _int(value: Any, low: int = 0, high: int = (1 << 63) - 1,
         code: str = "INTEGER_INVALID") -> int:
    need(type(value) is int and low <= value <= high, code)
    return value


def _number(value: Any, low: float, high: float, code: str) -> float:
    need(type(value) in (int, float) and math.isfinite(value)
         and low <= value <= high, code)
    return float(value)


def _same(left: Any, right: Any) -> bool:
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return set(left) == set(right) and all(_same(left[key], right[key]) for key in left)
    if type(left) is list:
        return len(left) == len(right) and all(_same(a, b) for a, b in zip(left, right))
    return left == right


def _reparse(path: Path) -> bool:
    try:
        st = path.lstat()
    except OSError:
        return False
    attrs = getattr(st, "st_file_attributes", 0)
    return path.is_symlink() or bool(attrs & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400))


def no_reparse(path: Any, require_exists: bool = True) -> Path:
    value = Path(path).absolute()
    for item in (value, *value.parents):
        if not item.exists() and not item.is_symlink():
            if require_exists:
                raise Rejected("PATH_MISSING")
            continue
        need(not _reparse(item), "PATH_REPARSE")
    if require_exists:
        need(value.exists(), "PATH_MISSING")
    return value


def _stable_read(path: Any, maximum: int, allow_empty: bool = False) -> bytes:
    value = no_reparse(path)
    try:
        path_before = value.stat()
        with value.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            raw = stream.read(maximum + 1)
            closed = os.fstat(stream.fileno())
        path_after = value.stat()
    except OSError:
        raise Rejected("FILE_READ") from None
    need(len(raw) <= maximum and (allow_empty or len(raw) > 0), "FILE_SIZE")
    identity = lambda st: (
        st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink
    )
    need(identity(path_before) == identity(opened) == identity(closed)
         == identity(path_after)
         and path_before.st_ctime_ns == path_after.st_ctime_ns
         and opened.st_ctime_ns == closed.st_ctime_ns
         and len(raw) == opened.st_size == closed.st_size,
         "FILE_CHANGED")
    return raw


def _below(path: Any, parent: Any) -> bool:
    p = os.path.normcase(os.path.abspath(str(path)))
    q = os.path.normcase(os.path.abspath(str(parent)))
    try:
        return os.path.commonpath((p, q)) == q
    except ValueError:
        return False


def _same_path(left: Any, right: Any) -> bool:
    return os.path.normcase(os.path.normpath(os.path.abspath(str(left)))) == \
        os.path.normcase(os.path.normpath(os.path.abspath(str(right))))


def _relative(value: Any) -> str:
    need(type(value) is str and value and "\\" not in value and ":" not in value
         and not value.startswith("/") and all(part not in ("", ".", "..")
                                                  for part in value.split("/")),
         "SOURCE_PATH")
    return value


def _child(root: Any, name: str) -> Path:
    need(type(name) is str and name and "/" not in name and "\\" not in name
         and name not in (".", ".."), "RUN_PATH")
    root_path = Path(root).absolute()
    child = (root_path / name).absolute()
    need(child != root_path and _below(child, root_path), "RUN_PATH_ESCAPE")
    return child


def _object(root: Any, name: str, maximum: int = 1024 * 1024) -> Dict[str, Any]:
    value = strict_json(_stable_read(_child(root, name), maximum))
    need(type(value) is dict, "OBJECT_EXPECTED")
    return value


def _pin_map(pins: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
    rows = pins.get("files")
    need(type(rows) is list and 1 <= len(rows) <= 128, "PINS_FILES")
    result: Dict[str, Dict[str, Any]] = {}
    for row in rows:
        _keys(row, ("path", "bytes", "sha256"), "PIN_ROW_FIELDS")
        need(type(row["path"]) is str, "PIN_PATH")
        path = Path(row["path"])
        need(path.is_absolute() and os.path.normpath(str(path)) == str(path),
             "PIN_PATH_ABSOLUTE")
        path = no_reparse(path).resolve(strict=True)
        need(_same_path(path, row["path"]), "PIN_PATH_CANONICAL")
        size = _int(row["bytes"], 0, 16 * 1024 * 1024, "PIN_BYTES")
        digest = _digest(row["sha256"], "PIN_SHA")
        key = os.path.normcase(str(path))
        need(key not in result, "DUPLICATE_PIN")
        raw = _stable_read(path, 16 * 1024 * 1024, allow_empty=True)
        need(len(raw) == size and _sha(raw) == digest, "PIN_FILE_HASH")
        result[key] = {"path": path, "bytes": size, "sha256": digest}
    return result


def _pin(pinned: Dict[str, Dict[str, Any]], path: Any,
         expected: Optional[str] = None) -> Dict[str, Any]:
    target = Path(os.path.abspath(str(path)))
    key = os.path.normcase(str(target))
    need(key in pinned, "UNPINNED_INPUT")
    row = pinned[key]
    need(expected is None or row["sha256"] == expected, "INPUT_PIN_VALUE")
    raw = _stable_read(row["path"], 16 * 1024 * 1024, allow_empty=True)
    need(len(raw) == row["bytes"] and _sha(raw) == row["sha256"], "INPUT_CLOSE_HASH")
    return row


def _check_pins_shape(pins: Any) -> Dict[str, Any]:
    _keys(pins, PIN_FIELDS, "PINS_FIELDS")
    need(pins["schema"] == INPUT_SCHEMA and pins["parent"] == PARENT
         and pins["baselineManifestSha256"] == BASELINE_SHA
         and pins["planSha256"] == PLAN_SHA and pins["protocolSha256"] == PROTOCOL_SHA
         and pins["addendumSha256"] == ADDENDUM_SHA
         and pins["cutInputSha256"] == CUT_SHA
         and pins["p4QualificationSha256"] == P4_QUALIFICATION_SHA
         and pins["p4InputPinsSha256"] == P4_INPUT_SHA,
         "PINS_IDENTITY")
    for name in (
        "repositoryRoot", "historicalRoot", "baselineManifestPath", "bridgeRoot",
        "bridgeManifest", "preflight", "controlRunRoot", "parentControlReceipt",
        "planPath", "protocolPath", "addendumPath", "cutInputPath",
        "p4QualificationPath", "p4ReaderPath", "p4InputPinsPath", "p4CaptureRunRoot",
    ):
        value = pins[name]
        need(type(value) is str and Path(value).is_absolute()
             and os.path.normpath(value) == value, "PINS_ABSOLUTE_PATH")
    mode_files = pins["modeFiles"]
    _keys(mode_files, MODE_FILE_KEYS, "MODE_FILE_FIELDS")
    for value in mode_files.values():
        need(type(value) is str and Path(value).is_absolute()
             and os.path.normpath(value) == value, "MODE_FILE_PATH")
    return pins


def _read_source_baseline(path: Path) -> Dict[str, Any]:
    raw = _stable_read(path, 4 * 1024 * 1024)
    need(_sha(raw) == BASELINE_SHA, "BASELINE_HASH")
    doc = strict_json(raw)
    _keys(doc, (
        "schema", "parent", "repositoryRoot", "files", "p4InputPinsSha256",
        "p4QualificationSha256", "productionSources",
    ), "BASELINE_FIELDS")
    need(doc["schema"] == "tsj-prefix-blocker-source-baseline-v1"
         and doc["parent"] == PARENT and doc["p4InputPinsSha256"] == P4_INPUT_SHA
         and doc["p4QualificationSha256"] == P4_QUALIFICATION_SHA
         and type(doc["repositoryRoot"]) is str
         and Path(doc["repositoryRoot"]).is_absolute()
         and doc["productionSources"] == 850,
         "BASELINE_IDENTITY")
    rows = doc["files"]
    need(type(rows) is list and len(rows) == LIMITS["sourceCount"], "BASELINE_CENSUS")
    seen = set()
    production = 0
    for row in rows:
        _keys(row, ("role", "path", "bytes", "sha256"), "BASELINE_ROW_FIELDS")
        path_value = _relative(row["path"])
        need(path_value.casefold() not in seen, "BASELINE_DUPLICATE_PATH")
        seen.add(path_value.casefold())
        need(row["role"] in ("BASE_SOURCE", "WRITER", "HOST", "READER", "WIRE"),
             "BASELINE_ROLE")
        _int(row["bytes"], 0, 100 * 1024 * 1024, "BASELINE_BYTES")
        _digest(row["sha256"], "BASELINE_SHA")
        if row["role"] == "BASE_SOURCE" and path_value.startswith("src/") \
                and path_value.endswith(".java"):
            production += 1
    need(production == doc["productionSources"], "BASELINE_PRODUCTION_CENSUS")
    role_counts = collections.Counter(row["role"] for row in rows)
    need(role_counts == collections.Counter({
        "BASE_SOURCE": 1080, "WRITER": 1, "HOST": 1, "READER": 1, "WIRE": 1,
    }), "BASELINE_ROLE_CENSUS")
    by_path = {row["path"]: row for row in rows}
    need(by_path.get(WRITER_RELATIVE, {}).get("role") == "WRITER"
         and by_path.get(OLD_READER_RELATIVE, {}).get("role") == "READER",
         "BASELINE_OWNED_PATHS")
    return doc


def _validate_source_closure(pins: Dict[str, Any], pinned: Dict[str, Dict[str, Any]],
                             input_sha: str, pins_path: Path) -> Dict[str, Any]:
    repo = Path(pins["repositoryRoot"])
    historical = Path(pins["historicalRoot"])
    baseline_path = Path(pins["baselineManifestPath"])
    need(_same_path(baseline_path, Path(__file__).resolve().parent / "source-baseline-r1.json"),
         "BASELINE_PATH")
    baseline_doc = _read_source_baseline(baseline_path)
    need(_same_path(historical, baseline_doc["repositoryRoot"])
         and not _same_path(repo, historical), "SOURCE_ROOTS")
    _pin(pinned, baseline_path, BASELINE_SHA)
    need(type(pins["sourceRows"]) is list
         and len(pins["sourceRows"]) == LIMITS["sourceCount"], "SOURCE_ROWS")
    need(type(pins["changedRows"]) is list and len(pins["changedRows"]) == 1,
         "CHANGED_ROWS")
    base_rows = baseline_doc["files"]
    base_by_path = {row["path"]: row for row in base_rows}
    current_by_path: Dict[str, Dict[str, Any]] = {}
    total = 0
    for row in pins["sourceRows"]:
        _keys(row, ("role", "path", "bytes", "sha256"), "SOURCE_ROW_FIELDS")
        relative = _relative(row["path"])
        before = base_by_path.get(relative)
        need(before is not None and relative not in current_by_path
             and row["role"] == before["role"], "SOURCE_ROW_IDENTITY")
        size = _int(row["bytes"], 0, 100 * 1024 * 1024, "SOURCE_ROW_BYTES")
        digest = _digest(row["sha256"], "SOURCE_ROW_SHA")
        total += size
        need(total <= LIMITS["sourceBytes"], "SOURCE_BYTES_CAP")
        current_by_path[relative] = row
    need(set(current_by_path) == set(base_by_path), "SOURCE_PATH_CENSUS")
    changes: Dict[str, Dict[str, Any]] = {}
    for row in pins["changedRows"]:
        _keys(row, ("path", "before", "after"), "CHANGED_ROW_FIELDS")
        relative = _relative(row["path"])
        need(relative == WRITER_RELATIVE and relative not in changes,
             "CHANGED_PATH_ALLOWLIST")
        _keys(row["before"], ("bytes", "sha256"), "CHANGED_BEFORE_FIELDS")
        _keys(row["after"], ("bytes", "sha256"), "CHANGED_AFTER_FIELDS")
        changes[relative] = row
    need(set(changes) == {WRITER_RELATIVE}, "CHANGED_PATH_CENSUS")

    repo = no_reparse(repo).resolve(strict=True)
    historical = no_reparse(historical).resolve(strict=True)
    need(repo.is_dir() and historical.is_dir(), "SOURCE_ROOT_KIND")
    current_files: List[Tuple[Path, int, str]] = []
    for relative, current in current_by_path.items():
        before = base_by_path[relative]
        old_size = _int(before["bytes"], 0, 100 * 1024 * 1024, "BASE_SOURCE_BYTES")
        old_sha = _digest(before["sha256"], "BASE_SOURCE_SHA")
        new_size, new_sha = current["bytes"], current["sha256"]
        old_path = no_reparse(historical.joinpath(*relative.split("/")))
        new_path = no_reparse(repo.joinpath(*relative.split("/")))
        old_raw = _stable_read(old_path, 100 * 1024 * 1024, allow_empty=True)
        new_raw = _stable_read(new_path, 100 * 1024 * 1024, allow_empty=True)
        need(len(old_raw) == old_size and _sha(old_raw) == old_sha,
             "HISTORICAL_SOURCE_HASH")
        need(len(new_raw) == new_size and _sha(new_raw) == new_sha,
             "CURRENT_SOURCE_HASH")
        change = changes.get(relative)
        if change is None:
            need((new_size, new_sha) == (old_size, old_sha), "UNEXPECTED_SOURCE_DELTA")
        else:
            before_ref, after_ref = change["before"], change["after"]
            _int(before_ref["bytes"], 0, 100 * 1024 * 1024, "CHANGE_BEFORE_BYTES")
            _int(after_ref["bytes"], 0, 100 * 1024 * 1024, "CHANGE_AFTER_BYTES")
            need((before_ref["bytes"], before_ref["sha256"]) == (old_size, old_sha)
                 and (after_ref["bytes"], after_ref["sha256"]) == (new_size, new_sha),
                 "CHANGED_SOURCE_BINDING")
        current_files.append((new_path, new_size, new_sha))
    need(len(current_files) == LIMITS["sourceCount"], "SOURCE_CLOSE_COUNT")

    mode_files = pins["modeFiles"]
    writer_path = repo.joinpath(*WRITER_RELATIVE.split("/"))
    need(_same_path(mode_files["writer"], writer_path), "WRITER_PATH")
    for role in ("router", "support", "adapter", "inspector", "queries"):
        _pin(pinned, mode_files[role])
    _pin(pinned, pins["preflight"])
    for key, digest in (
        ("planPath", PLAN_SHA), ("protocolPath", PROTOCOL_SHA),
        ("addendumPath", ADDENDUM_SHA), ("cutInputPath", CUT_SHA),
        ("p4QualificationPath", P4_QUALIFICATION_SHA),
        ("p4InputPinsPath", P4_INPUT_SHA),
    ):
        _pin(pinned, pins[key], digest)
    _pin(pinned, pins["p4ReaderPath"], P4_READER_SHA)

    bridge_root, bridge_manifest = Path(pins["bridgeRoot"]), Path(pins["bridgeManifest"])
    no_reparse(bridge_root)
    _pin(pinned, bridge_manifest, "e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b")
    bridge_rows = strict_json(_stable_read(bridge_manifest, 65536))
    need(type(bridge_rows) is list and len(bridge_rows) == 3, "BRIDGE_CENSUS")
    bridge_names = {"CirSim.java", "ChipElm.java", "PhysicalSpecificationDeveloperVerifier.java"}
    need({row.get("path") for row in bridge_rows if type(row) is dict} == bridge_names,
         "BRIDGE_NAMES")
    for row in bridge_rows:
        _keys(row, ("path", "bytes", "sha256"), "BRIDGE_ROW_FIELDS")
        _int(row["bytes"], 1, 16 * 1024 * 1024, "BRIDGE_BYTES")
        _pin(pinned, bridge_root / ("bridge-" + row["path"]), _digest(row["sha256"]))

    control_root = Path(pins["controlRunRoot"])
    parent_control = Path(pins["parentControlReceipt"])
    p4_capture_root = Path(pins["p4CaptureRunRoot"])
    need(parent_control.name == "op-1.jsonl", "PARENT_CONTROL_NAME")
    _pin(pinned, parent_control, P4_CONTROL_RECEIPT_SHA)
    p4_root = Path(pins["p4ReaderPath"]).parent
    need(_same_path(pins["p4ReaderPath"],
                     Path(pins["p4QualificationPath"]).parent / "read_capture.py")
         and _same_path(pins["p4CaptureRunRoot"], p4_root / "capture-r2")
         and _same_path(parent_control, p4_root / "control-r3" / "data" / "op-1.jsonl"),
         "P4_PATH_RELATION")
    for name in (
        "input-pins-r4.json",
        "control-r3/data/op-1.jsonl",
        "capture-r2/data/canonical-prefix-index.json",
        "capture-r2/data/op-1.jsonl",
        "capture-r2/data/fixed-prefix.jsonl",
    ):
        expected_path = (
            Path(pins["p4InputPinsPath"]) if name == "input-pins-r4.json"
            else p4_root.joinpath(*name.split("/"))
        )
        _pin(pinned, expected_path)
    need(_same_path(pins["p4InputPinsPath"], p4_root / "input-pins-r4.json"),
         "P4_INPUT_PINS_PATH")

    temp_root = Path(tempfile.gettempdir()).resolve()
    need(control_root.is_absolute() and _below(control_root, temp_root)
         and not _below(control_root, repo), "CONTROL_ROOT_CONFINEMENT")
    need(pins_path.is_absolute() and not _below(pins_path, repo), "PINS_PATH")
    self_key = os.path.normcase(str(Path(__file__).resolve()))
    self_row = pinned.get(self_key)
    need(self_row is not None
         and _sha(_stable_read(__file__, 4 * 1024 * 1024)) == self_row["sha256"],
         "READER_SELF_PIN")
    return {
        "repo": repo,
        "historical": historical,
        "baselinePath": baseline_path,
        "baselineDoc": baseline_doc,
        "baseRows": base_by_path,
        "sourceRows": current_by_path,
        "changedRows": changes,
        "currentFiles": current_files,
        "modeFiles": mode_files,
        "bridgeRoot": bridge_root,
        "bridgeRows": bridge_rows,
        "controlRunRoot": control_root,
        "parentControlReceipt": parent_control,
        "p4Root": p4_root,
        "p4CaptureRoot": p4_capture_root,
        "temp": temp_root,
        "extraFiles": len(pinned),
    }


def _module_from_pinned(path: Path, name: str, expected_sha: str,
                        expected_bytes: Optional[int] = None) -> Any:
    raw = _stable_read(path, 4 * 1024 * 1024)
    need(_sha(raw) == expected_sha
         and (expected_bytes is None or len(raw) == expected_bytes), "MODULE_HASH")
    spec = importlib.util.spec_from_file_location(name, path)
    need(spec is not None and spec.loader is not None, "MODULE_IMPORT")
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except Exception:
        raise Rejected("MODULE_LOAD") from None
    return module


def _load_p4_context(pins: Dict[str, Any], pinned: Dict[str, Dict[str, Any]],
                     closure: Dict[str, Any]) -> Dict[str, Any]:
    qualification_path = Path(pins["p4QualificationPath"])
    qualification_raw = _stable_read(qualification_path, 4 * 1024 * 1024)
    need(_sha(qualification_raw) == P4_QUALIFICATION_SHA, "P4_QUALIFICATION_HASH")
    qualification = strict_json(qualification_raw)
    need(qualification.get("schema") == "tsj-canonical-prefix-capture-qualification-v1"
         and qualification.get("status") == "ACCEPTED_FINITE_CAPTURE_AND_DIAGNOSIS_ONLY"
         and qualification.get("sourceRows") == 1084
         and qualification.get("inputPinsSha256") == P4_INPUT_SHA
         and qualification.get("candidate", {}).get("prefixGeometrySha256") == PREFIX_SHA,
         "P4_QUALIFICATION_IDENTITY")

    published = qualification.get("publishedCopies")
    need(type(published) is list and len(published) >= 12, "P4_PUBLISHED_COPIES")
    published_by_path: Dict[str, Dict[str, Any]] = {}
    for row in published:
        _keys(row, ("path", "bytes", "sha256"), "P4_PUBLISHED_ROW")
        path_text = _relative(row["path"])
        need(path_text not in published_by_path, "P4_PUBLISHED_DUPLICATE")
        _int(row["bytes"], 1, 8 * 1024 * 1024, "P4_PUBLISHED_BYTES")
        _digest(row["sha256"], "P4_PUBLISHED_SHA")
        published_by_path[path_text] = row
    p4_reader_path = Path(pins["p4ReaderPath"])
    need(p4_reader_path.name == "read_capture.py"
         and _same_path(p4_reader_path.parent, qualification_path.parent),
         "P4_READER_PATH")
    reader_ref = published_by_path.get("read_capture.py")
    need(reader_ref is not None and reader_ref["bytes"] == P4_READER_BYTES
         and reader_ref["sha256"] == P4_READER_SHA, "P4_READER_PUBLISHED_PIN")
    _pin(pinned, p4_reader_path, P4_READER_SHA)

    oracle_refs = (
        ("oracles-r1/read_fixed_prefix_graph.py",
         "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"),
        ("oracles-r1/fixed_prefix_minimum_vias.py",
         "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"),
        ("oracles-r1/read_fixed_prefix_capture.py",
         "26cbdc32361f07f759cc32dd3fe432fd640e329ce7e51dd583094dc10d0af962"),
    )
    for relative, digest in oracle_refs:
        row = published_by_path.get(relative)
        need(row is not None and row["sha256"] == digest, "P4_ORACLE_PUBLISHED_PIN")
        oracle_path = qualification_path.parent.joinpath(*relative.split("/"))
        _pin(pinned, oracle_path, digest)

    old_branch_path = closure["repo"].joinpath(*OLD_READER_RELATIVE.split("/"))
    old_reader = closure["sourceRows"].get(OLD_READER_RELATIVE)
    need(old_reader is not None and old_reader["role"] == "READER"
         and old_reader["sha256"] == OLD_BRANCH_READER_SHA
         and old_reader["bytes"] == OLD_BRANCH_READER_BYTES, "OLD_BRANCH_READER_PIN")
    branch_reader = _module_from_pinned(
        old_branch_path, "p5_pinned_old_branch_reader", OLD_BRANCH_READER_SHA,
        OLD_BRANCH_READER_BYTES,
    )
    p4_reader = _module_from_pinned(
        p4_reader_path, "p5_pinned_p4_reader", P4_READER_SHA, P4_READER_BYTES,
    )
    need(getattr(p4_reader, "OLD_BRANCH_READER_SHA", None) == OLD_BRANCH_READER_SHA
         and callable(getattr(p4_reader, "_operation", None))
         and callable(getattr(p4_reader, "_capture_transport", None))
         and callable(getattr(p4_reader, "_compare", None))
         and callable(getattr(p4_reader, "_compare_capture", None)),
         "P4_READER_API")
    need(callable(getattr(branch_reader, "validate_operation", None))
         and callable(getattr(branch_reader, "_geometry_rows", None))
         and callable(getattr(branch_reader, "java_lexical_key", None)),
         "OLD_BRANCH_READER_API")

    p4_inputs_path = Path(pins["p4InputPinsPath"])
    p4_inputs_raw = _stable_read(p4_inputs_path, 4 * 1024 * 1024)
    need(_sha(p4_inputs_raw) == P4_INPUT_SHA, "P4_INPUT_PINS_HASH")
    p4_inputs = strict_json(p4_inputs_raw)
    need(type(p4_inputs) is dict
         and p4_inputs.get("schema") == "tsj-canonical-prefix-inputs-v1"
         and len(p4_inputs.get("sourceRows", [])) == 1084,
         "P4_INPUT_PINS_IDENTITY")

    control_run = next((row for row in qualification["caseRuns"]
                        if row.get("name") == "control-r3"
                        and row.get("mode") == "CONTROL"), None)
    capture_run = next((row for row in qualification["caseRuns"]
                        if row.get("name") == "capture-r2"
                        and row.get("mode") == "CAPTURE"), None)
    need(type(control_run) is dict and control_run.get("status") == "PASS_CONTROL_ONLY"
         and control_run.get("runId") == "f25dbc4b-ae90-4149-ac2b-615aa1c99a00",
         "P4_CONTROL_QUALIFICATION")
    need(type(capture_run) is dict and capture_run.get("status") == "PASS_CAPTURE_DIAGNOSIS_ONLY"
         and capture_run.get("runId") == "07e0f654-d4a0-41de-833c-589a60f5ad3f",
         "P4_CAPTURE_QUALIFICATION")

    p4_control_ref = control_run.get("operationReceipt")
    _keys(p4_control_ref, ("path", "bytes", "sha256"), "P4_CONTROL_RECEIPT_REF")
    need(p4_control_ref["path"] == "control-r3/data/op-1.jsonl"
         and p4_control_ref["bytes"] == P4_CONTROL_RECEIPT_BYTES
         and p4_control_ref["sha256"] == P4_CONTROL_RECEIPT_SHA
         and _same_path(pins["parentControlReceipt"],
                        qualification_path.parent / p4_control_ref["path"]),
         "P4_CONTROL_RECEIPT_QUALIFICATION")

    p4_capture_root = Path(pins["p4CaptureRunRoot"])
    data_root = p4_capture_root / "data"
    p4_index_path = data_root / "canonical-prefix-index.json"
    p4_op_path = data_root / "op-1.jsonl"
    p4_transport_path = data_root / "fixed-prefix.jsonl"
    p4_index_raw = _stable_read(p4_index_path, LIMITS["indexBytes"])
    need(p4_index_raw.endswith(b"\n") and p4_index_raw.count(b"\n") == 1
         and b"\r" not in p4_index_raw, "P4_CAPTURE_INDEX_ENCODING")
    p4_index = strict_json(p4_index_raw[:-1])
    need(type(capture_run.get("index")) is dict
         and _same(p4_index, capture_run["index"]), "P4_CAPTURE_INDEX_QUALIFICATION")
    p4_index_ref = p4_index.get("operation")
    p4_entry = p4_index_ref
    need(type(p4_entry) is dict, "P4_CAPTURE_ENTRY")
    _keys(p4_entry, ("operation", "armId", "receipt", "elapsedToCloseNanos",
                     "maxFullGroupNanos", "cleanup"), "P4_CAPTURE_ENTRY_FIELDS")
    p4_op_ref = p4_entry.get("receipt")
    _keys(p4_op_ref, ("path", "bytes", "sha256"), "P4_CAPTURE_OP_REF")
    qualified_op_ref = capture_run.get("operationReceipt")
    _keys(qualified_op_ref, ("path", "bytes", "sha256"), "P4_QUALIFIED_OP_REF")
    need(p4_op_ref["path"] == "op-1.jsonl"
         and _same(p4_op_ref, {
             "path": "op-1.jsonl", "bytes": qualified_op_ref["bytes"],
             "sha256": qualified_op_ref["sha256"],
         })
         and qualified_op_ref["path"] == "capture-r2/data/op-1.jsonl",
         "P4_CAPTURE_OP_BINDING")
    p4_op_raw = _stable_read(p4_op_path, LIMITS["receiptBytes"])
    need(len(p4_op_raw) == p4_op_ref["bytes"]
         and _sha(p4_op_raw) == p4_op_ref["sha256"], "P4_CAPTURE_OP_HASH")

    qualified_transport = qualification.get("captureTransport")
    _keys(qualified_transport, ("path", "bytes", "sha256"), "P4_CAPTURE_TRANSPORT_REF")
    p4_transport_ref = p4_index.get("transport")
    _keys(p4_transport_ref, ("path", "bytes", "sha256", "graph", "facts"),
          "P4_CAPTURE_TRANSPORT_DESCRIPTOR")
    need(qualified_transport == {
        "path": "capture-r2/data/fixed-prefix.jsonl",
        "bytes": P4_CAPTURE_TRANSPORT_BYTES,
        "sha256": P4_CAPTURE_TRANSPORT_SHA,
    } and p4_transport_ref["path"] == "fixed-prefix.jsonl"
         and p4_transport_ref["bytes"] == P4_CAPTURE_TRANSPORT_BYTES
         and p4_transport_ref["sha256"] == P4_CAPTURE_TRANSPORT_SHA,
         "P4_CAPTURE_TRANSPORT_QUALIFICATION")
    p4_transport_raw = _stable_read(p4_transport_path, LIMITS["aggregateBytes"])
    need(len(p4_transport_raw) == P4_CAPTURE_TRANSPORT_BYTES
         and _sha(p4_transport_raw) == P4_CAPTURE_TRANSPORT_SHA,
         "P4_CAPTURE_TRANSPORT_HASH")

    p4_run_id = p4_index.get("runId")
    need(p4_run_id == capture_run["runId"], "P4_CAPTURE_RUN_ID")
    p4_index_entry = p4_reader._index(
        p4_index_raw, p4_index, "CAPTURE", p4_run_id, P4_INPUT_SHA,
    )
    p4_operation = p4_reader._operation(
        p4_op_raw, p4_index_entry, p4_run_id, P4_INPUT_SHA, branch_reader,
    )
    p4_facts, p4_diagnosis = p4_reader._capture_transport(
        p4_transport_raw, p4_index, p4_operation, p4_run_id, P4_INPUT_SHA,
    )
    p4_decoded_transport = p4_reader.decode_transport(p4_transport_raw, True)
    need(p4_facts["rawPlacementSha256"] == RAW_PLACEMENT_SHA
         and p4_facts["rawPlacementFingerprintUtf8Bytes"] == 252676
         and _sha(p4_facts["rawPlacementFingerprint"].encode("utf-8")) == RAW_PLACEMENT_SHA
         and p4_facts["prefixGeometrySha256"] == PREFIX_SHA
         and p4_facts["prefixGeometryFingerprintUtf8Bytes"] == 265894
         and p4_index["transport"]["graph"]["sha256"] == GRAPH_SHA
         and _sha(p4_decoded_transport["graphBytes"]) == GRAPH_SHA
         and p4_diagnosis["status"] == "ZERO_VIA_ROUTE_EXCLUDED",
         "P4_CAPTURE_FACTS")
    return {
        "qualification": qualification,
        "p4Inputs": p4_inputs,
        "p4Reader": p4_reader,
        "branchReader": branch_reader,
        "p4ControlRun": control_run,
        "p4CaptureRun": capture_run,
        "p4ControlReceipt": _stable_read(
            Path(pins["parentControlReceipt"]), LIMITS["receiptBytes"],
        ),
        "p4CaptureIndex": p4_index,
        "p4CaptureOperation": p4_operation,
        "p4Facts": p4_facts,
        "p4Graph": p4_decoded_transport["graph"],
        "p4GraphBytes": p4_decoded_transport["graphBytes"],
        "p4TransportRaw": p4_transport_raw,
        "p4OperationRaw": p4_op_raw,
    }


def _recheck_pins(pinned: Dict[str, Dict[str, Any]], pins_path: Path,
                  pins_raw: bytes) -> None:
    need(_stable_read(pins_path, 4 * 1024 * 1024) == pins_raw, "PINS_CHANGED")
    for row in pinned.values():
        raw = _stable_read(row["path"], 16 * 1024 * 1024, allow_empty=True)
        need(len(raw) == row["bytes"] and _sha(raw) == row["sha256"],
             "INPUT_CHANGED_DURING_READ")


def _recheck_source_files(rows: List[Tuple[Path, int, str]]) -> None:
    for path, size, digest in rows:
        raw = _stable_read(path, 100 * 1024 * 1024, allow_empty=True)
        need(len(raw) == size and _sha(raw) == digest, "SOURCE_CHANGED_DURING_READ")


def _process(value: Any, success: Optional[bool] = None) -> Dict[str, Any]:
    _keys(value, RECORD_FIELDS, "PROCESS_FIELDS")
    need(value["TerminationProven"] is True, "PROCESS_TERMINATION")
    exit_code = value["ExitCode"]
    need(type(exit_code) is int
         and (success is None or (exit_code == 0 if success else exit_code != 0)),
         "PROCESS_EXIT")
    need(type(value["Stdout"]) is str and type(value["Stderr"]) is str
         and type(value["FilePath"]) is str and type(value["Arguments"]) is list
         and all(type(arg) is str and "\x00" not in arg and len(arg) <= 32768
                 for arg in value["Arguments"]), "PROCESS_INVOCATION")
    _int(value["ProcessId"], 1, (1 << 32) - 1, "PROCESS_ID")
    _int(value["ProcessStartTicks"], 1, (1 << 63) - 1, "PROCESS_START_TICKS")
    for key in ("FilePath", "StdoutLog", "StderrLog", "ProcessRoot"):
        item = value[key]
        need(type(item) is str and Path(item).is_absolute()
             and os.path.normpath(item) == item, "PROCESS_ABSOLUTE_PATH")
    root = Path(value["ProcessRoot"])
    stdout_log, stderr_log = Path(value["StdoutLog"]), Path(value["StderrLog"])
    need(_below(stdout_log, root) and _below(stderr_log, root)
         and stdout_log != root and stderr_log != root
         and stdout_log.name.casefold() == "stdout.log"
         and stderr_log.name.casefold() == "stderr.log", "PROCESS_LOG_BINDING")
    temp_root = Path(tempfile.gettempdir()).resolve()
    need(_below(root, temp_root), "PROCESS_ROOT_CONFINEMENT")
    for path in (root, stdout_log, stderr_log):
        if path.exists() or path.is_symlink():
            no_reparse(path)
    return value


def _file_sha(path: Any, maximum: int = 64 * 1024 * 1024) -> str:
    return _sha(_stable_read(path, maximum))


def _source_audit(proc: Dict[str, Any], pins_sha: str, extra_files: int) -> None:
    need(proc["Stderr"] == "", "SOURCE_AUDIT_STDERR")
    value = strict_json(proc["Stdout"].encode("utf-8"))
    _keys(value, ("changedRows", "extraFiles", "pinsSha256", "sourceRows", "status"),
          "SOURCE_AUDIT_FIELDS")
    need(value == {
        "changedRows": 1,
        "extraFiles": extra_files,
        "pinsSha256": pins_sha,
        "sourceRows": 1084,
        "status": "PASS_CANONICAL_PREFIX_BLOCKER_INPUT_CLOSURE_ONLY",
    }, "SOURCE_AUDIT_RESULT")


def _validate_host(run_root: Path, pins_path: Path, pins_raw: bytes,
                   pins: Dict[str, Any], pins_sha: str, mode: str,
                   closure: Dict[str, Any], pinned: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    launch = _object(run_root, "host-launch.json")
    host = _object(run_root, "host-result.json")
    _keys(launch, (
        "schema", "status", "mode", "runId", "pid", "startTicks", "executable",
        "script", "scriptSha256", "inputPinsSha256", "scratch", "scratchCreatedTicks",
        "outputRoot", "compileLimitMs", "nativeLimitMs", "cleanupLimitMs", "outerLimitMs",
    ), "HOST_LAUNCH_FIELDS")
    _keys(host, (
        "schema", "status", "mode", "runId", "inputPinsSha256", "sourceClosure",
        "compilerProcessId", "compilerProcessStartTicks", "compilerExitCode",
        "compilerTerminationProven", "nativeExitCode", "nativeTerminationProven",
        "nativeProcessId", "nativeProcessStartTicks", "nativeCaptureLimitNegative",
        "nativeOutputBytes", "captureNegativeWallMs", "nativeWallMs", "wrapperWorkNanos",
        "postSourceAndScratchCleanupMs", "cleanupConservativeUpperMs", "cleanupBoundMs",
        "scratchRemoved", "cleanupAccounting", "outerElapsedMs", "compilerSources",
    ), "HOST_RESULT_FIELDS")
    run_id = launch["runId"]
    need(launch["schema"] == HOST_SCHEMA
         and host["schema"] == HOST_RESULT_SCHEMA
         and launch["status"] == "LAUNCHED_NOT_ACCEPTED"
         and host["status"] == "HOST_COMPLETE_READER_PENDING"
         and launch["mode"] == host["mode"] == mode
         and launch["inputPinsSha256"] == host["inputPinsSha256"] == pins_sha
         and host["runId"] == run_id
         and type(run_id) is str and UUID.fullmatch(run_id) is not None,
         "HOST_CONTEXT")
    _int(launch["pid"], 1, (1 << 32) - 1, "HOST_PID")
    need(type(launch["startTicks"]) is str
         and re.fullmatch(r"[1-9][0-9]*", launch["startTicks"]) is not None
         and type(launch["scratchCreatedTicks"]) is str
         and re.fullmatch(r"[1-9][0-9]*", launch["scratchCreatedTicks"]) is not None,
         "HOST_TICKS")
    for key, limit in (
        ("compileLimitMs", LIMITS["compileMs"]),
        ("nativeLimitMs", LIMITS["nativeMs"]),
        ("cleanupLimitMs", LIMITS["cleanupMs"]),
        ("outerLimitMs", LIMITS["outerMs"]),
    ):
        need(type(launch[key]) is int and launch[key] == limit, "HOST_LIMITS")

    scratch = Path(launch["scratch"])
    output_root = Path(launch["outputRoot"])
    need(scratch.is_absolute() and output_root.is_absolute()
         and os.path.normpath(str(scratch)) == str(scratch)
         and os.path.normpath(str(output_root)) == str(output_root)
         and _below(scratch, closure["temp"]) and not scratch.exists()
         and _same_path(output_root, run_root)
         and _below(output_root, closure["temp"])
         and not _below(output_root, closure["repo"]), "HOST_PATHS")
    script = Path(launch["script"])
    script_pin = _pin(pinned, script)
    need(_same_path(script, Path(__file__).resolve().parent / "run-native.ps1")
         and launch["scriptSha256"] == script_pin["sha256"], "HOST_SCRIPT_PIN")
    host_executable = Path(launch["executable"])
    need(host_executable.is_absolute()
         and host_executable.name.casefold() == "powershell.exe"
         and "system32" in [part.casefold() for part in host_executable.parts]
         and _file_sha(host_executable) ==
             "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f",
         "HOST_POWERSHELL_PIN")

    need(host["sourceClosure"] == "PASS"
         and host["nativeCaptureLimitNegative"] == "PASS"
         and host["compilerSources"] == LIMITS["compileSources"]
         and host["cleanupBoundMs"] == LIMITS["cleanupMs"]
         and host["scratchRemoved"] is True
         and type(host["compilerTerminationProven"]) is bool
         and host["compilerTerminationProven"] is True
         and type(host["nativeTerminationProven"]) is bool
         and host["nativeTerminationProven"] is True,
         "HOST_FINAL_PROOF")
    native_wall = _number(host["nativeWallMs"], 0, LIMITS["nativeMs"], "HOST_NATIVE_WALL")
    negative_wall = _number(
        host["captureNegativeWallMs"], 0, LIMITS["negativeWallMs"], "HOST_NEGATIVE_WALL",
    )
    post_cleanup = _number(
        host["postSourceAndScratchCleanupMs"], 0, LIMITS["cleanupMs"], "HOST_POST_CLEANUP",
    )
    outer = _number(host["outerElapsedMs"], 0, LIMITS["outerMs"], "HOST_OUTER")
    work = _int(host["wrapperWorkNanos"], 0, LIMITS["operationNanos"], "HOST_WRAPPER_WORK")
    _int(host["nativeOutputBytes"], 0, LIMITS["receiptBytes"], "HOST_OUTPUT_BYTES")
    need(host["compilerExitCode"] == 0 and type(host["compilerExitCode"]) is int
         and type(host["nativeExitCode"]) is int
         and (host["nativeExitCode"] != 0 if mode == "INSPECTOR_THROW"
              else host["nativeExitCode"] == 0), "HOST_EXIT_CODES")
    expected_cleanup = (
        "FULL_NATIVE_WALL_PLUS_POST_SOURCE"
        if mode == "INSPECTOR_THROW" else "NATIVE_RESIDUAL_PLUS_POST_SOURCE"
    )
    need(host["cleanupAccounting"] == expected_cleanup, "HOST_CLEANUP_ACCOUNTING_KIND")
    if mode == "INSPECTOR_THROW":
        cleanup_upper = native_wall + post_cleanup
    else:
        cleanup_upper = native_wall - work / 1_000_000.0 + post_cleanup
    stated_upper = _number(
        host["cleanupConservativeUpperMs"], 0, LIMITS["cleanupMs"], "HOST_CLEANUP_UPPER",
    )
    need(abs(stated_upper - cleanup_upper) <= 0.001
         and outer >= native_wall + negative_wall + post_cleanup
         and outer <= LIMITS["outerMs"], "HOST_CLEANUP_ACCOUNTING")
    need(host["compilerProcessId"] >= 1 and host["nativeProcessId"] >= 1
         and type(host["compilerProcessId"]) is int and type(host["nativeProcessId"]) is int
         and type(host["compilerProcessStartTicks"]) is str
         and type(host["nativeProcessStartTicks"]) is str,
         "HOST_PROCESS_FIELDS")

    audit_args = [
        "-B", pins["preflight"], str(closure["repo"]),
        str(closure["baselinePath"]), BASELINE_SHA,
        str(pins_path.absolute()), pins_sha,
    ]
    for name in ("preflight-process.json", "pre-native-process.json", "post-source-process.json"):
        proc = _process(_object(run_root, name), True)
        need(proc["Arguments"] == audit_args and proc["Stderr"] == "",
             "SOURCE_AUDIT_ARGUMENTS")
        need(_file_sha(proc["FilePath"]) ==
             "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec",
             "SOURCE_AUDIT_PYTHON")
        _pin(pinned, pins["preflight"])
        _source_audit(proc, pins_sha, closure["extraFiles"])

    compiler_intent = _object(run_root, "compiler-launch-intent.json")
    _keys(compiler_intent, (
        "status", "executable", "sha256", "arguments", "sourceListSha256",
        "sourceListPath", "limitMs",
    ), "COMPILER_INTENT_FIELDS")
    compiler = _process(_object(run_root, "compiler-process.json"), True)
    need(compiler_intent["status"] == "INTENT_NOT_START_PROOF"
         and compiler_intent["limitMs"] == LIMITS["compileMs"]
         and compiler_intent["sha256"] ==
             "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
         and compiler["Arguments"] == compiler_intent["arguments"]
         and _same_path(compiler["FilePath"], compiler_intent["executable"])
         and compiler["ProcessId"] == host["compilerProcessId"]
         and str(compiler["ProcessStartTicks"]) == host["compilerProcessStartTicks"]
         and _file_sha(compiler["FilePath"]) == compiler_intent["sha256"],
         "COMPILER_BINDING")

    classes = scratch / "classes"
    empty_source = scratch / "empty-sourcepath"
    source_list_path = scratch / "sources.txt"
    cp = ";".join((
        str(classes),
        str(closure["repo"] / ".tools/gwt-2.7.0/gwt-user-2.7.0.jar"),
        str(closure["repo"] / ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar"),
    ))
    expected_compiler_args = [
        "-source", "7", "-target", "7", "-encoding", "UTF-8",
        "-classpath", cp, "-sourcepath", str(empty_source), "-d", str(classes),
        "@" + str(source_list_path),
    ]
    need(compiler_intent["arguments"] == expected_compiler_args
         and _same_path(compiler_intent["sourceListPath"], source_list_path),
         "COMPILER_ARGUMENTS")
    source_list_raw = _stable_read(_child(run_root, "compiler-sources.txt"), 2 * 1024 * 1024)
    need(_sha(source_list_raw) == compiler_intent["sourceListSha256"]
         and source_list_raw.endswith(b"\n") and b"\x00" not in source_list_raw,
         "COMPILER_SOURCE_LIST_HASH")
    try:
        source_lines = source_list_raw.decode("utf-8", "strict").splitlines()
    except UnicodeDecodeError:
        raise Rejected("COMPILER_SOURCE_LIST_ENCODING") from None
    expected_sources: List[str] = []
    for row in pins["sourceRows"]:
        if row["role"] == "BASE_SOURCE" and row["path"].endswith(".java") \
                and row["path"] not in EXCLUDED_JAVA:
            expected_sources.append(
                str(closure["repo"].joinpath(*row["path"].split("/"))).replace("\\", "/")
            )
    for role in MODE_FILE_ORDER:
        expected_sources.append(str(Path(pins["modeFiles"][role])).replace("\\", "/"))
    bridge_copies = strict_json(_stable_read(
        _child(run_root, "consumed-bridge-copies.json"), 65536,
    ))
    need(type(bridge_copies) is list and len(bridge_copies) == 3
         and len(closure["bridgeRows"]) == 3, "BRIDGE_COPY_CENSUS")
    for actual, wanted in zip(bridge_copies, closure["bridgeRows"]):
        _keys(actual, ("path", "bytes", "sha256"), "BRIDGE_COPY_FIELDS")
        copy_path = scratch / wanted["path"]
        need(_same_path(actual["path"], copy_path)
             and actual["bytes"] == wanted["bytes"]
             and actual["sha256"] == wanted["sha256"], "BRIDGE_COPY_BINDING")
        expected_sources.append(str(copy_path).replace("\\", "/"))
    need(len(source_lines) == LIMITS["compileSources"]
         and len(set(source_lines)) == LIMITS["compileSources"]
         and all(line.startswith('"') and line.endswith('"')
                 and '"' not in line[1:-1] for line in source_lines)
         and [line[1:-1] for line in source_lines] == expected_sources,
         "COMPILER_SOURCE_CENSUS")

    native_intent = _object(run_root, "native-launch-intent.json")
    _keys(native_intent, ("status", "arguments", "executable", "sha256", "limitMs"),
          "NATIVE_INTENT_FIELDS")
    native = _process(
        _object(run_root, "native-process.json"),
        False if mode == "INSPECTOR_THROW" else True,
    )
    need(native["ProcessId"] == host["nativeProcessId"]
         and str(native["ProcessStartTicks"]) == host["nativeProcessStartTicks"]
         and type(host["nativeProcessStartTicks"]) is str
         and type(host["nativeProcessId"]) is int,
         "NATIVE_PROCESS_BINDING")
    native_output_bytes = len(native["Stdout"].encode("utf-8")) + len(native["Stderr"].encode("utf-8"))
    need(native_output_bytes == host["nativeOutputBytes"]
         and native_output_bytes <= LIMITS["receiptBytes"], "NATIVE_OUTPUT_CENSUS")

    java_sha = "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
    need(native_intent["status"] == "INTENT_NOT_START_PROOF"
         and native_intent["sha256"] == java_sha
         and native_intent["limitMs"] == LIMITS["nativeMs"]
         and native["Arguments"] == native_intent["arguments"]
         and _same_path(native["FilePath"], native_intent["executable"])
         and _file_sha(native["FilePath"]) == java_sha, "NATIVE_BINDING")
    data_root = output_root / "data"
    if mode == "CANARY":
        expected_native_args = ["-ea", "-cp", cp, MAIN_CLASS, "--canary"]
    else:
        expected_native_args = [
            "-ea", "-cp", cp, MAIN_CLASS, mode, run_id, pins_sha, WIRE_SHA, str(data_root),
        ]
    need(native_intent["arguments"] == expected_native_args, "NATIVE_ARGUMENTS")

    negative_intent = _object(run_root, "native-capture-negative-intent.json")
    _keys(negative_intent, ("status", "executable", "sha256", "arguments", "limitMs"),
          "OVERFLOW_INTENT_FIELDS")
    negative = _process(_object(run_root, "native-capture-limit-negative.json"), False)
    negative_args = ["-ea", "-cp", cp, MAIN_CLASS, "--capture-overflow"]
    need(negative_intent["status"] == "INTENT_NOT_START_PROOF"
         and negative_intent["limitMs"] == LIMITS["negativeMs"]
         and negative_intent["sha256"] == java_sha
         and negative_intent["arguments"] == negative_args
         and negative["Arguments"] == negative_args
         and _same_path(negative["FilePath"], native["FilePath"])
         and negative["ExitCode"] != 0 and negative["Stdout"] == ""
         and OVERFLOW_MARKER in negative["Stderr"]
         and len(negative["Stderr"].encode("utf-8")) <= LIMITS["receiptBytes"],
         "OVERFLOW_NEGATIVE")
    negative_wall_ref = host["captureNegativeWallMs"]

    need(host["wrapperWorkNanos"] <= LIMITS["operationNanos"], "HOST_WRAPPER_BUDGET")
    return {
        "runId": run_id,
        "launch": launch,
        "host": host,
        "native": native,
        "negative": negative,
        "nativeIntent": native_intent,
        "compilerIntent": compiler_intent,
        "workNanos": work,
        "cleanupUpperMs": stated_upper,
        "outputRoot": output_root,
        "scratch": scratch,
        "cp": cp,
        "negativeWallMs": negative_wall_ref,
    }


def _decode_single_line(stdout: str, marker: str) -> Dict[str, Any]:
    need(type(stdout) is str and "\x00" not in stdout
         and stdout.endswith(("\n", "\r\n")), "NATIVE_STDOUT_CLOSURE")
    normalized = stdout.replace("\r\n", "\n")
    need("\r" not in normalized and normalized.count("\n") == 1,
         "NATIVE_STDOUT_LINES")
    line = normalized[:-1]
    need(line.startswith(marker), "NATIVE_MARKER")
    need(len(line.encode("utf-8")) <= LIMITS["lineBytes"], "NATIVE_MARKER_SIZE")
    payload = line[len(marker):]
    need(payload and not payload.startswith(" "), "NATIVE_MARKER_JSON")
    value = strict_json(payload.encode("utf-8"))
    need(type(value) is dict, "NATIVE_MARKER_OBJECT")
    return value


def _trailer(info: Dict[str, Any], mode: str, pins_sha: str) -> Dict[str, Any]:
    native, host = info["native"], info["host"]
    need(native["ExitCode"] == 0 and native["Stderr"] == "", "NATIVE_STREAMS")
    value = _decode_single_line(native["Stdout"], "CANONICAL_PREFIX_BLOCKER_COMPLETE ")
    _keys(value, (
        "schema", "mode", "runId", "sourceManifestSha256", "wireSha256",
        "scope", "layoutQualification", "index", "wrapper", "aggregateBytes",
    ), "NATIVE_TRAILER_FIELDS")
    need(value["schema"] == NATIVE_SCHEMA and value["mode"] == mode
         and value["runId"] == info["runId"]
         and value["sourceManifestSha256"] == pins_sha
         and value["wireSha256"] == WIRE_SHA
         and value["scope"] == "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY"
         and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "NATIVE_TRAILER_IDENTITY")
    _keys(value["index"], ("path", "bytes", "sha256"), "TRAILER_INDEX_FIELDS")
    need(value["index"]["path"] == "canonical-blocker-index.json",
         "TRAILER_INDEX_PATH")
    _int(value["index"]["bytes"], 1, LIMITS["indexBytes"], "TRAILER_INDEX_BYTES")
    _digest(value["index"]["sha256"], "TRAILER_INDEX_SHA")
    wrapper = value["wrapper"]
    _keys(wrapper, (
        "preparationNanos", "graphExportNanos", "postNanos",
        "elapsedNanos", "workNanos", "groups",
    ), "WRAPPER_FIELDS")
    for key in ("preparationNanos", "graphExportNanos", "postNanos"):
        _int(wrapper[key], 0, LIMITS["groupNanos"], "WRAPPER_PHASE_TIME")
    _int(wrapper["elapsedNanos"], 1, LIMITS["operationNanos"], "WRAPPER_ELAPSED")
    _int(wrapper["workNanos"], 0, LIMITS["operationNanos"], "WRAPPER_WORK")
    need(type(wrapper["groups"]) is int and wrapper["groups"] == 3
         and sum(wrapper[name] for name in
                 ("preparationNanos", "graphExportNanos", "postNanos"))
         <= wrapper["workNanos"] <= wrapper["elapsedNanos"]
         and wrapper["workNanos"] == info["workNanos"]
         and wrapper["elapsedNanos"] <= host["nativeWallMs"] * 1_000_000,
         "WRAPPER_ACCOUNTING")
    need(type(value["aggregateBytes"]) is int
         and 0 <= value["aggregateBytes"] <= LIMITS["aggregateBytes"]
         and len(native["Stdout"].encode("utf-8")) <= LIMITS["receiptBytes"],
         "NATIVE_AGGREGATE_CAP")
    return value


def _canary(info: Dict[str, Any]) -> Dict[str, Any]:
    native = info["native"]
    need(native["ExitCode"] == 0 and native["Stderr"] == "", "CANARY_EXIT")
    stdout = native["Stdout"]
    need(type(stdout) is str and stdout.endswith(("\n", "\r\n"))
         and "\x00" not in stdout, "CANARY_STDOUT_CLOSURE")
    normalized = stdout.replace("\r\n", "\n")
    need("\r" not in normalized, "CANARY_LINE_ENDING")
    lines = normalized[:-1].split("\n")
    need(len(lines) >= 2 and all(
        line.startswith("PASS: ") and len(line.encode("utf-8")) <= LIMITS["lineBytes"]
        for line in lines[:-1]
    ), "CANARY_FIXTURE_OUTPUT")
    marker = "CANONICAL_PREFIX_BLOCKER_CANARY_COMPLETE "
    need(lines[-1].startswith(marker)
         and len(lines[-1].encode("utf-8")) <= LIMITS["lineBytes"], "CANARY_MARKER")
    value = strict_json(lines[-1][len(marker):].encode("utf-8"))
    _keys(value, (
        "schema", "status", "assertions", "inspectorAssertions", "elapsedNanos",
        "groups", "maximumGroupNanos", "layoutQualification",
    ), "CANARY_FIELDS")
    need(value["schema"] == CANARY_SCHEMA and value["status"] == "PASS"
         and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool,
         "CANARY_IDENTITY")
    _int(value["assertions"], 1, 100000, "CANARY_ASSERTIONS")
    _int(value["inspectorAssertions"], 1, 100000, "CANARY_INSPECTOR_ASSERTIONS")
    _int(value["elapsedNanos"], 1, LIMITS["operationNanos"], "CANARY_ELAPSED")
    _int(value["groups"], 1, LIMITS["groups"], "CANARY_GROUPS")
    _int(value["maximumGroupNanos"], 1, LIMITS["groupNanos"], "CANARY_GROUP_NANOS")
    need(value["elapsedNanos"] == info["workNanos"], "CANARY_HOST_WORK_BINDING")
    return value


def _index(raw: bytes, value: Any, mode: str, run_id: str,
           pins_sha: str, p4_reader: Any) -> Dict[str, Any]:
    _keys(value, (
        "schema", "mode", "profile", "kind", "runId", "sourceManifestSha256",
        "wireSha256", "scope", "layoutQualification", "target", "operation",
        "transport", "attribution", "selection",
    ), "INDEX_FIELDS")
    need(value["schema"] == INDEX_SCHEMA and value["mode"] == mode
         and value["profile"] == "BASE" and value["kind"] == "AUTHORED"
         and value["runId"] == run_id and value["sourceManifestSha256"] == pins_sha
         and value["wireSha256"] == WIRE_SHA
         and value["scope"] == "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY"
         and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool,
         "INDEX_IDENTITY")
    _keys(value["target"], ("receiptBranchOrdinal", "lineageBranchOrdinal"),
          "INDEX_TARGET_FIELDS")
    need(value["target"] == {
        "receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77,
    }, "INDEX_TARGET")
    entry = p4_reader._entry(value["operation"])
    expected_selection = (
        {"calls": 0, "selected": 0, "callbacks": 0, "snapshotDropped": True}
        if mode == "CONTROL" else
        {"calls": 2, "selected": 1, "callbacks": 1, "snapshotDropped": True}
        if mode == "CAPTURE" else None
    )
    need(expected_selection is not None, "INDEX_MODE")
    _keys(value["selection"], ("calls", "selected", "callbacks", "snapshotDropped"),
          "INDEX_SELECTION_FIELDS")
    need(_same(value["selection"], expected_selection), "INDEX_SELECTION")
    if mode == "CONTROL":
        need(value["transport"] is None and value["attribution"] is None,
             "CONTROL_NO_SIDECARS")
    else:
        transport = value["transport"]
        _keys(transport, ("path", "bytes", "sha256", "graph", "facts"),
              "INDEX_TRANSPORT_FIELDS")
        need(transport["path"] == "fixed-prefix.jsonl", "INDEX_TRANSPORT_PATH")
        _int(transport["bytes"], 1, LIMITS["aggregateBytes"], "INDEX_TRANSPORT_BYTES")
        _digest(transport["sha256"], "INDEX_TRANSPORT_SHA")
        for key in ("graph", "facts"):
            _keys(transport[key], ("bytes", "sha256"), "INDEX_PAYLOAD_FIELDS")
            _int(transport[key]["bytes"], 1, LIMITS["aggregateBytes"], "INDEX_PAYLOAD_BYTES")
            _digest(transport[key]["sha256"], "INDEX_PAYLOAD_SHA")
        attribution = value["attribution"]
        _keys(attribution, ("path", "bytes", "sha256"), "INDEX_ATTRIBUTION_FIELDS")
        need(attribution["path"] == "prefix-blockers.json", "INDEX_ATTRIBUTION_PATH")
        _int(attribution["bytes"], 1, LIMITS["attributionBytes"], "INDEX_ATTRIBUTION_BYTES")
        _digest(attribution["sha256"], "INDEX_ATTRIBUTION_SHA")
    need(0 < len(raw) <= LIMITS["indexBytes"], "INDEX_SIZE")
    return entry


def _data_files(data_root: Path, expected: set) -> None:
    need(data_root.is_dir() and not data_root.is_symlink(), "DATA_ROOT")
    no_reparse(data_root)
    names = set()
    for item in data_root.iterdir():
        need(item.is_file() and not item.is_symlink() and not _reparse(item),
             "DATA_ENTRY_TYPE")
        names.add(item.name)
    need(names == expected, "DATA_FILE_CENSUS")


def _receipt_prefix_through_branch(raw: bytes, branch_reader: Any) -> Tuple[bytes, List[Dict[str, Any]]]:
    need(type(raw) is bytes and 0 < len(raw) <= LIMITS["receiptBytes"]
         and raw.endswith(b"\n") and b"\r" not in raw, "PARTIAL_RECEIPT_ENCODING")
    lines = raw.splitlines(keepends=True)
    need(lines and all(line.endswith(b"\n") and line != b"\n"
                       and len(line) <= LIMITS["lineBytes"] for line in lines),
         "PARTIAL_RECEIPT_LINES")
    rows = [branch_reader.strict_json(line[:-1]) for line in lines]
    need(all(type(row) is dict for row in rows)
         and rows[0].get("record") == "HEADER", "PARTIAL_RECEIPT_HEADER")
    target_positions = [
        i for i, row in enumerate(rows)
        if row.get("record") == "BRANCH" and row.get("ordinal") == 307
    ]
    need(len(target_positions) == 1, "PARTIAL_TARGET_BRANCH")
    target_index = target_positions[0]
    prefix_rows = rows[:target_index + 1]
    need(not any(row.get("record") == "FOOTER" for row in prefix_rows)
         and not any(row.get("record") == "BRANCH" and row.get("ordinal") == 307
                     for row in rows[target_index + 1:]),
         "PARTIAL_NOT_CLOSED_AT_TARGET")
    need(all(row.get("record") != "FOOTER" for row in rows),
         "PARTIAL_FOOTER_FORBIDDEN")
    return b"".join(lines[:target_index + 1]), prefix_rows


def _load_success(run_root: Path, info: Dict[str, Any], mode: str,
                  pins_sha: str, context: Dict[str, Any]) -> Dict[str, Any]:
    trailer = _trailer(info, mode, pins_sha)
    data_root = _child(run_root, "data")
    index_path = _child(data_root, "canonical-blocker-index.json")
    index_raw = _stable_read(index_path, LIMITS["indexBytes"])
    need(index_raw.endswith(b"\n") and b"\r" not in index_raw
         and index_raw.count(b"\n") == 1, "INDEX_ENCODING")
    index_value = strict_json(index_raw[:-1])
    need(type(index_value) is dict, "INDEX_OBJECT")
    entry = _index(
        index_raw, index_value, mode, info["runId"], pins_sha, context["p4Reader"],
    )
    receipt_path = _child(data_root, entry["receipt"]["path"])
    receipt_raw = _stable_read(receipt_path, LIMITS["receiptBytes"])
    need(len(receipt_raw) == entry["receipt"]["bytes"]
         and _sha(receipt_raw) == entry["receipt"]["sha256"], "RECEIPT_HASH")
    operation = context["p4Reader"]._operation(
        receipt_raw, entry, info["runId"], pins_sha, context["branchReader"],
    )
    wrapper = trailer["wrapper"]
    need(wrapper["workNanos"] == info["workNanos"]
         and wrapper["elapsedNanos"] - wrapper["workNanos"]
             == entry["cleanup"]["elapsedNanos"], "WRAPPER_CLEANUP_BINDING")
    groups = operation["groups"]
    need(len(groups) + 3 <= LIMITS["groups"]
         and max(row["elapsedBeforeOwnLineNanos"] for row in groups) <= LIMITS["groupNanos"]
         and sum(row["elapsedBeforeOwnLineNanos"] for row in groups)
             <= entry["elapsedToCloseNanos"]
         and max(row["elapsedBeforeOwnLineNanos"] for row in groups)
             <= entry["maxFullGroupNanos"], "OPERATION_GROUP_BUDGET")
    need((wrapper["graphExportNanos"] == 0 if mode == "CONTROL"
          else wrapper["graphExportNanos"] > 0), "GRAPH_EXPORT_MODE")
    transport_raw = b""
    attribution_raw = b""
    decoded_transport = None
    facts = None
    if mode == "CONTROL":
        _data_files(data_root, {"canonical-blocker-index.json", "op-1.jsonl"})
    else:
        _data_files(data_root, {
            "canonical-blocker-index.json", "op-1.jsonl",
            "fixed-prefix.jsonl", "prefix-blockers.json",
        })
        transport = index_value["transport"]
        transport_path = _child(data_root, transport["path"])
        transport_raw = _stable_read(transport_path, LIMITS["aggregateBytes"])
        need(len(transport_raw) == transport["bytes"]
             and _sha(transport_raw) == transport["sha256"], "TRANSPORT_HASH")
        attribution_ref = index_value["attribution"]
        attribution_path = _child(data_root, attribution_ref["path"])
        attribution_raw = _stable_read(attribution_path, LIMITS["attributionBytes"])
        need(len(attribution_raw) == attribution_ref["bytes"]
             and _sha(attribution_raw) == attribution_ref["sha256"],
             "ATTRIBUTION_HASH")
        need(len(attribution_raw) <= LIMITS["attributionBytes"], "ATTRIBUTION_SIZE")
        facts, diagnosis = context["p4Reader"]._capture_transport(
            transport_raw, index_value, operation, info["runId"], pins_sha,
        )
        need(diagnosis["status"] == "ZERO_VIA_ROUTE_EXCLUDED"
             and diagnosis["minimumViaCount"] is None
             and facts["prefixGeometrySha256"] == PREFIX_SHA,
             "CAPTURE_GRAPH_FACTS")
        decoded_transport = context["p4Reader"].decode_transport(transport_raw, True)
        need(index_value["transport"]["graph"]["sha256"] == GRAPH_SHA
             and _sha(decoded_transport["graphBytes"]) == GRAPH_SHA
             and decoded_transport["graphBytes"] == context["p4GraphBytes"],
             "CAPTURE_GRAPH_BINDING")
    trailer_index = trailer["index"]
    need(trailer_index["path"] == "canonical-blocker-index.json"
         and trailer_index["bytes"] == len(index_raw)
         and trailer_index["sha256"] == _sha(index_raw), "INDEX_TRAILER_BINDING")
    aggregate = len(index_raw) + len(receipt_raw) + len(transport_raw) + len(attribution_raw)
    need(aggregate <= LIMITS["aggregateBytes"]
         and trailer["aggregateBytes"] == aggregate, "OPERATION_AGGREGATE_CAP")
    need(wrapper["preparationNanos"] + wrapper["graphExportNanos"]
         + wrapper["postNanos"] <= wrapper["workNanos"],
         "WRAPPER_PHASE_WORK")
    return {
        "trailer": trailer,
        "index": index_value,
        "indexRaw": index_raw,
        "entry": entry,
        "receipt": receipt_raw,
        "operation": operation,
        "transportRaw": transport_raw,
        "attributionRaw": attribution_raw,
        "facts": facts,
        "decodedTransport": decoded_transport,
        "aggregateBytes": aggregate,
    }


DIRECTIONS = (("N", 1, 0, -1), ("E", 2, 1, 0),
              ("S", 4, 0, 1), ("W", 8, -1, 0))


def _derive_cut_queries(graph: Any, facts: Dict[str, Any]) -> Dict[str, Any]:
    width, height = graph.width, graph.height
    cells = width * height
    need(cells == graph.cells and cells == facts["cellCount"]
         and width == facts["gridWidth"] and height == facts["gridHeight"],
         "CUT_GRAPH_GEOMETRY")
    starts = tuple(graph.starts)
    need(starts and len(set(starts)) == len(starts), "CUT_GRAPH_STARTS")
    queue = collections.deque(sorted(starts))
    reachable = set(starts)
    planar_edges = 0
    while queue:
        state = queue.popleft()
        need(0 <= state < graph.state_count, "CUT_STATE_RANGE")
        layer, cell = divmod(state, cells)
        need(layer in (0, 1), "CUT_LAYER")
        x, y = cell % width, cell // width
        mask = graph.steps[state]
        for _name, bit, dx, dy in DIRECTIONS:
            nx, ny = x + dx, y + dy
            if nx < 0 or nx >= width or ny < 0 or ny >= height:
                continue
            if mask & bit:
                planar_edges += 1
                next_state = layer * cells + ny * width + nx
                if next_state not in reachable:
                    reachable.add(next_state)
                    queue.append(next_state)

    cut_rows = []
    ordinal = 0
    for state in sorted(reachable):
        layer, cell = divmod(state, cells)
        x, y = cell % width, cell // width
        mask = graph.steps[state]
        for name, bit, dx, dy in DIRECTIONS:
            nx, ny = x + dx, y + dy
            if nx < 0 or nx >= width or ny < 0 or ny >= height:
                continue
            target = layer * cells + ny * width + nx
            if target in reachable:
                continue
            need(not (mask & bit), "CUT_REACHABILITY_CLOSURE")
            cut_rows.append({
                "bit": bit,
                "direction": name,
                "expectedPermitsLayerStep": False,
                "fromCell": cell,
                "fromState": state,
                "fromX": facts["originX"] + x * facts["gridPitch"],
                "fromY": facts["originY"] + y * facts["gridPitch"],
                "layer": layer,
                "ordinal": ordinal,
                "toCell": ny * width + nx,
                "toState": layer * cells + ny * width + nx,
                "toX": facts["originX"] + nx * facts["gridPitch"],
                "toY": facts["originY"] + ny * facts["gridPitch"],
            })
            ordinal += 1
    return {
        "reachableStates": sorted(reachable),
        "directedPlanarEdgesExamined": planar_edges,
        "cutQueries": cut_rows,
    }


def _validate_cut_document(path: Path, input_sha: str, graph: Any,
                           facts: Dict[str, Any]) -> Dict[str, Any]:
    raw = _stable_read(path, 4 * 1024 * 1024)
    need(_sha(raw) == CUT_SHA, "CUT_INPUT_HASH")
    doc = strict_json(raw)
    _keys(doc, (
        "attribution", "cutQueries", "directedPlanarEdgesExamined",
        "graphSha256", "grid", "p4InputPinsSha256", "p4QualificationSha256",
        "prefixGeometrySha256", "productionFeasibility", "queryCount",
        "reachableStates", "schema", "scope", "target",
        "verticalTransitionsAllowed",
    ), "CUT_INPUT_FIELDS")
    expected_target = {
        "lineageBranchOrdinal": 77,
        "netId": "A_COIL_LOW",
        "passNumber": 2,
        "placementSeed": "-6587958367974952701",
        "prefixHoles": 48,
        "receiptBranchOrdinal": 307,
        "requestIndex": 0,
        "rootPadId": "DA.A",
        "rootSeed": "9007199254740993",
        "routingSeed": "8445301674744508279",
        "selectedAttempt": 4,
        "startPadId": "QA.C",
    }
    expected_grid = {
        "cells": facts["cellCount"],
        "height": facts["gridHeight"],
        "originX": facts["originX"],
        "originY": facts["originY"],
        "pitch": facts["gridPitch"],
        "primary": facts["primaryLayer"],
        "width": facts["gridWidth"],
    }
    need(doc["schema"] == CUT_SCHEMA
         and doc["attribution"] == "NOT_RUN"
         and doc["productionFeasibility"] == "UNKNOWN"
         and doc["scope"] == "COMPLETE_OUTGOING_PLANAR_CUT_OF_REACHABLE_SET"
         and doc["p4InputPinsSha256"] == P4_INPUT_SHA
         and doc["p4QualificationSha256"] == P4_QUALIFICATION_SHA
         and doc["graphSha256"] == GRAPH_SHA
         and graph is not None
         and doc["prefixGeometrySha256"] == facts["prefixGeometrySha256"] == PREFIX_SHA
         and _same(doc["target"], expected_target)
         and _same(doc["grid"], expected_grid)
         and _int(doc["verticalTransitionsAllowed"], 0, 0,
                  "CUT_VERTICAL_TRANSITIONS") == 0
         and facts["remainingHoles"] == 0
         and facts["remainingBranchTransitionsFromPrefix"] == 0,
         "CUT_INPUT_IDENTITY")
    derived = _derive_cut_queries(graph, facts)
    need(type(doc["reachableStates"]) is list
         and _same(doc["reachableStates"], derived["reachableStates"])
         and len(doc["reachableStates"]) == 12
         and _int(doc["directedPlanarEdgesExamined"], 0, 1000000,
                  "CUT_EDGE_COUNT") == derived["directedPlanarEdgesExamined"] == 23
         and _int(doc["queryCount"], 0, LIMITS["queries"], "CUT_QUERY_COUNT")
             == LIMITS["queries"]
         and type(doc["cutQueries"]) is list
         and len(doc["cutQueries"]) == LIMITS["queries"]
         and _same(doc["cutQueries"], derived["cutQueries"]),
         "CUT_GRAPH_REDERIVATION")
    return doc


def _clearance_radius(closure: Dict[str, Any]) -> int:
    path = closure["repo"] / "src/com/lushprojects/circuitjs1/client/PcbTraceRules.java"
    row = closure["sourceRows"].get("src/com/lushprojects/circuitjs1/client/PcbTraceRules.java")
    need(row is not None and row["role"] == "BASE_SOURCE", "ROUTER_RULE_SOURCE")
    raw = _stable_read(path, 2 * 1024 * 1024)
    need(len(raw) == row["bytes"] and _sha(raw) == row["sha256"], "ROUTER_RULE_HASH")
    try:
        text = raw.decode("utf-8", "strict")
    except UnicodeDecodeError:
        raise Rejected("ROUTER_RULE_ENCODING") from None
    matches = re.findall(
        r"\bstatic\s+final\s+int\s+ROUTING_GRID_CLEARANCE_CELLS\s*=\s*([0-9]+)\s*;",
        text,
    )
    need(len(matches) == 1, "ROUTER_CLEARANCE_DECLARATION")
    radius = int(matches[0])
    need(0 <= radius <= 8, "ROUTER_CLEARANCE_BOUND")
    return radius


def _known_board_nets(operation: Dict[str, Any]) -> List[str]:
    pads = operation["pads"]
    need(type(pads) is list and len(pads) == operation["header"]["candidate"]["padCount"],
         "BOARD_PAD_CENSUS")
    nets = set()
    for pad in pads:
        need(type(pad) is dict and type(pad.get("netId")) is str
             and pad["netId"], "BOARD_PAD_NET")
        nets.add(pad["netId"])
    need(len(nets) == operation["header"]["candidate"]["netCount"] == 60,
         "BOARD_NET_CENSUS")
    return sorted(nets)


def _geometry_entries(rows: Dict[str, Any]) -> int:
    traces, holes = rows["traces"], rows["holes"]
    need(type(traces) is list and type(holes) is list, "GEOMETRY_ROW_LISTS")
    entries = 0
    for row in traces:
        need(type(row) is list and len(row) == 9, "GEOMETRY_TRACE_SHAPE")
        need((row[0] is None or (type(row[0]) is str and bool(row[0])))
             and type(row[1]) is str and bool(row[1])
             and (row[2] is None or (type(row[2]) is str and bool(row[2])))
             and (row[3] is None or (type(row[3]) is str and bool(row[3])))
             and type(row[4]) is int and row[4] in (0, 1)
             and type(row[5]) is str and bool(row[5]), "GEOMETRY_TRACE_FIELDS")
        _int(row[6], 1, 1_000_000, "GEOMETRY_TRACE_WIDTH")
        xs, ys = row[7], row[8]
        need(type(xs) is list and type(ys) is list and len(xs) >= 2
             and len(xs) == len(ys), "GEOMETRY_TRACE_POINTS")
        for coordinate in xs:
            _int(coordinate, -1_000_000_000, 1_000_000_000, "GEOMETRY_TRACE_X")
        for coordinate in ys:
            _int(coordinate, -1_000_000_000, 1_000_000_000, "GEOMETRY_TRACE_Y")
        entries += 9 + len(xs) + len(ys)
    for row in holes:
        need(type(row) is list and len(row) == 8, "GEOMETRY_HOLE_SHAPE")
        need(type(row[0]) is str and bool(row[0])
             and row[1] in ("NON_PLATED", "PLATED", "VIA")
             and (row[2] is None or (type(row[2]) is str and bool(row[2])))
             and type(row[7]) is str and bool(row[7]), "GEOMETRY_HOLE_FIELDS")
        _int(row[3], -1_000_000_000, 1_000_000_000, "GEOMETRY_HOLE_X")
        _int(row[4], -1_000_000_000, 1_000_000_000, "GEOMETRY_HOLE_Y")
        _int(row[5], 1, 1_000_000, "GEOMETRY_HOLE_DRILL")
        _int(row[6], 1, 1_000_000, "GEOMETRY_HOLE_LAND")
        entries += 8
    return entries


def _prefix_geometry(payload: Any, facts: Dict[str, Any], operation: Dict[str, Any],
                     branch_reader: Any) -> Dict[str, Any]:
    _keys(payload, ("traces", "holes"), "PREFIX_GEOMETRY_ROWS_FIELDS")
    traces, holes = payload["traces"], payload["holes"]
    need(type(traces) is list and type(holes) is list
         and len(holes) == facts["prefixHoleCount"] == 48,
         "PREFIX_GEOMETRY_ROWS_CENSUS")
    entries = _geometry_entries(payload)
    need(entries <= LIMITS["cloneEntries"], "PREFIX_GEOMETRY_ROWS_CAP")

    def trace_key(row: List[Any]) -> Tuple[Any, ...]:
        java_text = lambda value: "null" if value is None else value
        return (
            branch_reader.java_lexical_key(row[1]),
            branch_reader.java_lexical_key(java_text(row[2])),
            branch_reader.java_lexical_key(java_text(row[3])),
            row[4], row[6],
            branch_reader.java_lexical_key(java_text(row[0])),
        )

    need(traces == sorted(traces, key=trace_key), "PREFIX_TRACE_CANONICAL_ORDER")
    need(holes == sorted(holes, key=lambda row: branch_reader.java_lexical_key(row[0])),
         "PREFIX_HOLE_CANONICAL_ORDER")

    static_prefix = facts["rawPlacementFingerprint"]
    need(type(static_prefix) is str
         and len(static_prefix.encode("utf-8")) == facts["rawPlacementFingerprintUtf8Bytes"]
         and _sha(static_prefix.encode("utf-8")) == facts["rawPlacementSha256"] == RAW_PLACEMENT_SHA,
         "PREFIX_STATIC_SOURCE")
    snapshot = {
        "staticPrefix": static_prefix,
        "traces": traces,
        "holes": holes,
        "geometrySha256": facts["prefixGeometrySha256"],
        "geometryBytes": facts["prefixGeometryFingerprintUtf8Bytes"],
    }
    try:
        fingerprint, raw = branch_reader._geometry_rows(snapshot)
    except Exception:
        raise Rejected("PREFIX_NATIVE_GEOMETRY_PARSE") from None
    need(len(raw) == 265894 and _sha(raw) == PREFIX_SHA
         and fingerprint == facts["prefixGeometryFingerprint"]
         and len(fingerprint.encode("utf-8")) == facts["prefixGeometryFingerprintUtf8Bytes"],
         "PREFIX_NATIVE_GEOMETRY_BINDING")
    nets = _known_board_nets(operation)
    net_set = set(nets)
    need(all(row[2] is None or row[2] in net_set for row in holes),
         "PREFIX_HOLE_NET_CENSUS")
    trace_counts = collections.Counter(row[1] for row in traces)
    need(set(trace_counts).issubset(net_set), "PREFIX_TRACE_NET_CENSUS")
    expected_counts = [
        {"netId": net_id, "traceCount": trace_counts.get(net_id, 0)}
        for net_id in sorted(nets, key=branch_reader.java_lexical_key)
    ]
    hole_counts = collections.Counter(row[1] for row in holes)
    expected_holes = {
        "NON_PLATED": hole_counts.get("NON_PLATED", 0),
        "PLATED": hole_counts.get("PLATED", 0),
        "VIA": hole_counts.get("VIA", 0),
    }
    need(expected_holes == facts["prefixHoleCountsByKind"]
         and expected_holes["VIA"] == facts["prefixViaCount"] == 48
         and sum(expected_holes.values()) == facts["prefixHoleCount"] == 48,
         "PREFIX_HOLE_KIND_CENSUS")
    return {
        "fingerprint": fingerprint,
        "geometryBytes": len(raw),
        "geometryEntries": entries,
        "geometryUnits": (entries + LIMITS["unitEntries"] - 1) // LIMITS["unitEntries"],
        "traceNetCounts": expected_counts,
        "knownNets": nets,
        "holeCounts": expected_holes,
    }


def _counter_map(value: Any, nets: set, branch_reader: Any, code: str) -> Dict[str, int]:
    need(type(value) is list, code)
    result: Dict[str, int] = {}
    order: List[str] = []
    for row in value:
        _keys(row, ("netId", "count"), code + "_ROW")
        net_id = row["netId"]
        need(type(net_id) is str and net_id in nets, code + "_NET")
        count = _int(row["count"], 1, 1_000_000_000, code + "_COUNT")
        need(net_id not in result, code + "_DUPLICATE")
        result[net_id] = count
        order.append(net_id)
    need(order == sorted(order, key=branch_reader.java_lexical_key), code + "_ORDER")
    return result


def _neighbor_rows(value: Any, nets: set, grid_width: int, grid_height: int,
                   center_x: int, center_y: int, radius: int,
                   branch_reader: Any) -> List[Dict[str, Any]]:
    need(type(value) is list and len(value) <= (2 * radius + 1) ** 2,
         "NEIGHBOR_ROWS")
    previous: Optional[Tuple[Any, ...]] = None
    cells = set()
    result = []
    for row in value:
        _keys(row, ("gridX", "gridY", "netId"), "NEIGHBOR_ROW_FIELDS")
        x = _int(row["gridX"], 0, grid_width - 1, "NEIGHBOR_X")
        y = _int(row["gridY"], 0, grid_height - 1, "NEIGHBOR_Y")
        net_id = row["netId"]
        need(type(net_id) is str and net_id in nets, "NEIGHBOR_NET")
        need(abs(x - center_x) <= radius and abs(y - center_y) <= radius,
             "NEIGHBOR_RADIUS")
        cell = (x, y)
        need(cell not in cells, "NEIGHBOR_CELL_DUPLICATE")
        cells.add(cell)
        key = (x, y, branch_reader.java_lexical_key(net_id))
        need(previous is None or previous < key, "NEIGHBOR_ORDER")
        previous = key
        result.append(row)
    return result


def _validate_observation(row: Any, cut_query: Dict[str, Any], facts: Dict[str, Any],
                          known_nets: List[str], radius: int,
                          branch_reader: Any) -> bool:
    _keys(row, (
        "ordinal", "layer", "fromState", "toState", "bit", "fromX", "fromY",
        "toX", "toY", "permitsStep", "routerCongestionBefore",
        "routerCongestionAfter", "routerCongestionDelta", "workCongestionBefore",
        "workCongestionAfter", "workCongestionDelta", "conflictsBefore",
        "conflictsAfter", "conflictsDelta", "targetOccupiedNet",
        "targetClearanceNet", "neighborOccupiedNets", "classification",
    ), "OBSERVATION_FIELDS")
    ordinal = _int(row["ordinal"], 0, LIMITS["queries"] - 1, "OBSERVATION_ORDINAL")
    layer = _int(row["layer"], 0, 1, "OBSERVATION_LAYER")
    from_state = _int(row["fromState"], 0, (1 << 31) - 1, "OBSERVATION_FROM_STATE")
    to_state = _int(row["toState"], 0, (1 << 31) - 1, "OBSERVATION_TO_STATE")
    bit = _int(row["bit"], 1, 8, "OBSERVATION_BIT")
    from_x = _int(row["fromX"], -1_000_000_000, 1_000_000_000, "OBSERVATION_FROM_X")
    from_y = _int(row["fromY"], -1_000_000_000, 1_000_000_000, "OBSERVATION_FROM_Y")
    to_x = _int(row["toX"], -1_000_000_000, 1_000_000_000, "OBSERVATION_TO_X")
    to_y = _int(row["toY"], -1_000_000_000, 1_000_000_000, "OBSERVATION_TO_Y")
    need(row["ordinal"] == cut_query["ordinal"]
         and row["layer"] == cut_query["layer"]
         and row["fromState"] == cut_query["fromState"]
         and row["toState"] == cut_query["toState"]
         and row["bit"] == cut_query["bit"]
         and row["fromX"] == cut_query["fromX"] and row["fromY"] == cut_query["fromY"]
         and row["toX"] == cut_query["toX"] and row["toY"] == cut_query["toY"]
         and row["permitsStep"] is False
         and type(row["permitsStep"]) is bool, "OBSERVATION_CUT_BINDING")
    net_set = set(known_nets)
    target_occupied, target_clearance = row["targetOccupiedNet"], row["targetClearanceNet"]
    need(target_occupied is None or
         (type(target_occupied) is str and target_occupied in net_set),
         "TARGET_OCCUPIED_NET")
    need(target_clearance is None or target_clearance == "" or
         (type(target_clearance) is str and target_clearance in net_set),
         "TARGET_CLEARANCE_NET")
    width, height = facts["gridWidth"], facts["gridHeight"]
    pitch = facts["gridPitch"]
    need((to_x - facts["originX"]) % pitch == 0
         and (to_y - facts["originY"]) % pitch == 0,
         "OBSERVATION_GRID_COORDINATE")
    center_x = (to_x - facts["originX"]) // pitch
    center_y = (to_y - facts["originY"]) // pitch
    need(0 <= center_x < width and 0 <= center_y < height,
         "OBSERVATION_GRID_BOUNDS")
    neighbors = _neighbor_rows(
        row["neighborOccupiedNets"], net_set, width, height,
        center_x, center_y, radius, branch_reader,
    )

    router_before = _int(row["routerCongestionBefore"], 0, 1_000_000_000,
                          "ROUTER_CONGESTION_BEFORE")
    router_after = _int(row["routerCongestionAfter"], router_before, 1_000_000_000,
                         "ROUTER_CONGESTION_AFTER")
    router_delta = _int(row["routerCongestionDelta"], 0, 1_000_000_000,
                         "ROUTER_CONGESTION_DELTA")
    work_before = _int(row["workCongestionBefore"], 0, 1_000_000_000,
                       "WORK_CONGESTION_BEFORE")
    work_after = _int(row["workCongestionAfter"], work_before, 1_000_000_000,
                      "WORK_CONGESTION_AFTER")
    work_delta = _int(row["workCongestionDelta"], 0, 1_000_000_000,
                      "WORK_CONGESTION_DELTA")
    need(router_after - router_before == router_delta
         and work_after - work_before == work_delta
         and router_delta == work_delta, "CONGESTION_DELTA_ARITHMETIC")

    before = _counter_map(row["conflictsBefore"], net_set, branch_reader, "CONFLICTS_BEFORE")
    after = _counter_map(row["conflictsAfter"], net_set, branch_reader, "CONFLICTS_AFTER")
    delta = _counter_map(row["conflictsDelta"], net_set, branch_reader, "CONFLICTS_DELTA")
    need(all(after.get(net, 0) >= count for net, count in before.items()),
         "CONFLICTS_MONOTONIC")
    computed = {
        net: after.get(net, 0) - before.get(net, 0)
        for net in set(before) | set(after)
        if after.get(net, 0) - before.get(net, 0) > 0
    }
    computed = dict(sorted(computed.items(), key=lambda item: branch_reader.java_lexical_key(item[0])))
    need(delta == computed, "CONFLICTS_DELTA_ARITHMETIC")

    own_net = facts["netId"]
    histogram = collections.Counter(
        neighbor["netId"] for neighbor in neighbors if neighbor["netId"] != own_net
    )
    if router_delta > 0:
        target_foreign = (
            target_occupied is not None and target_occupied != own_net
        ) or (
            target_clearance == "" or
            (target_clearance is not None and target_clearance != own_net)
        )
        expected = {
            net: count * router_delta for net, count in histogram.items() if count > 0
        }
        expected = dict(sorted(
            expected.items(), key=lambda item: branch_reader.java_lexical_key(item[0]),
        ))
        need(target_foreign and bool(expected) and delta == expected,
             "FOREIGN_CONFLICT_DELTA")
        classification = "FOREIGN_OCCUPANCY_REJECTION_RECORDED"
    else:
        need(not delta, "ZERO_CONGESTION_CONFLICT_DELTA")
        classification = "BLOCKED_WITHOUT_FOREIGN_OCCUPANCY_REJECTION"
    need(row["classification"] == classification, "OBSERVATION_CLASSIFICATION")
    return router_delta > 0


def _unit_entries_lower_bound(entries: int, units: int, code: str) -> None:
    need(entries <= units * LIMITS["unitEntries"], code)


def _validate_lifecycle(value: Any, mode: str) -> None:
    _keys(value, (
        "offers", "selected", "completed", "ownersReleased",
        "contextRestored", "sourceRefsDropped",
    ), "LIFECYCLE_FIELDS")
    for key in ("offers", "selected", "completed"):
        _int(value[key], 0, 1_000_000, "LIFECYCLE_" + key.upper())
    exception = mode == "INSPECTOR_THROW"
    need(value["offers"] == 308 and value["selected"] == 1
         and value["completed"] == (0 if exception else 1)
         and all(type(value[key]) is bool and value[key] is True for key in (
             "ownersReleased", "contextRestored", "sourceRefsDropped",
         )), "LIFECYCLE_BINDING")


def _validate_face_rows(value: Any, mode: str, layer_query_counts: Dict[int, int]
                        ) -> Dict[str, int]:
    exception = mode == "INSPECTOR_THROW"
    expected_layers = [0] if exception else [0, 1]
    need(type(value) is list and len(value) == len(expected_layers),
         "SOURCE_PROOF_FACE_CENSUS")
    totals = {"copyEntries": 0, "comparisonEntries": 0,
              "copyUnits": 0, "comparisonUnits": 0}
    layers = []
    for row in value:
        _keys(row, (
            "layer", "sourceEffectsBeforeSha256", "sourceEffectsAfterSha256",
            "liveToPristineBeforeEqual", "queryToPristineBeforeEqual",
            "liveToPristineAfterEqual", "copyEntries", "comparisonEntries",
            "copyUnits", "comparisonUnits", "queryCount",
        ), "SOURCE_PROOF_FACE_FIELDS")
        layer = _int(row["layer"], 0, 1, "SOURCE_PROOF_FACE_LAYER")
        layers.append(layer)
        before = _digest(row["sourceEffectsBeforeSha256"], "FACE_SOURCE_EFFECTS_BEFORE")
        after = _digest(row["sourceEffectsAfterSha256"], "FACE_SOURCE_EFFECTS_AFTER")
        need(before == after
             and type(row["liveToPristineBeforeEqual"]) is bool
             and row["liveToPristineBeforeEqual"] is True
             and type(row["liveToPristineAfterEqual"]) is bool
             and row["liveToPristineAfterEqual"] is True
             and type(row["queryToPristineBeforeEqual"]) is bool
             and row["queryToPristineBeforeEqual"] is (False if exception else True),
             "SOURCE_PROOF_FACE_EQUALITY")
        copy_entries = _int(
            row["copyEntries"], 1, 2 * LIMITS["cloneEntries"] if not exception
            else LIMITS["cloneEntries"], "FACE_COPY_ENTRIES",
        )
        comparison_entries = _int(
            row["comparisonEntries"], 1,
            LIMITS["observationUnits"] * LIMITS["unitEntries"],
            "FACE_COMPARISON_ENTRIES",
        )
        copy_units = _int(row["copyUnits"], 2 if exception else 4,
                          LIMITS["observationUnits"], "FACE_COPY_UNITS")
        comparison_units = _int(row["comparisonUnits"], 4 if exception else 6,
                                LIMITS["observationUnits"], "FACE_COMPARISON_UNITS")
        query_count = _int(row["queryCount"], 0, LIMITS["queries"], "FACE_QUERY_COUNT")
        need(query_count == layer_query_counts.get(layer, 0),
             "SOURCE_PROOF_FACE_QUERY_BINDING")
        _unit_entries_lower_bound(copy_entries, copy_units, "FACE_COPY_CHUNK_BOUND")
        _unit_entries_lower_bound(comparison_entries, comparison_units,
                                  "FACE_COMPARISON_CHUNK_BOUND")
        totals["copyEntries"] += copy_entries
        totals["comparisonEntries"] += comparison_entries
        totals["copyUnits"] += copy_units
        totals["comparisonUnits"] += comparison_units
    need(layers == expected_layers, "SOURCE_PROOF_FACE_ORDER")
    return totals


def _validate_attribution(raw: Optional[bytes], payload: Any, mode: str,
                          info: Dict[str, Any], facts: Dict[str, Any],
                          operation: Dict[str, Any], context: Dict[str, Any],
                          closure: Dict[str, Any], pins_sha: str,
                          cut_document: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if raw is not None:
        need(type(raw) is bytes and 0 < len(raw) <= LIMITS["attributionBytes"]
             and raw.endswith(b"\n") and raw.count(b"\n") == 1
             and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"),
             "ATTRIBUTION_ENCODING")
        need(type(payload) is dict and _same(strict_json(raw[:-1]), payload),
             "ATTRIBUTION_RAW_BINDING")
    else:
        need(type(payload) is dict, "ATTRIBUTION_OBJECT")
        try:
            embedded = json.dumps(payload, ensure_ascii=True, separators=(",", ":")).encode("ascii")
        except (TypeError, ValueError, UnicodeError):
            raise Rejected("ATTRIBUTION_EMBEDDED_JSON") from None
        need(len(embedded) <= LIMITS["attributionBytes"], "ATTRIBUTION_EMBEDDED_SIZE")

    _keys(payload, (
        "schema", "status", "runId", "sourceManifestSha256", "wireSha256",
        "scope", "layoutQualification", "cutInputSha256", "graphSha256",
        "prefixGeometrySha256", "receiptBranchOrdinal", "lineageBranchOrdinal",
        "netId", "startPadId", "rootPadId", "lifecycle", "sourceProof",
        "prefixGeometryRows", "observations", "prefixTraceNetCounts", "totals",
        "exceptionStage",
    ), "ATTRIBUTION_FIELDS")
    exception = mode == "INSPECTOR_THROW"
    need(payload["schema"] == ATTRIBUTION_SCHEMA
         and payload["status"] == ("EXPECTED_INSPECTOR_THROW_ONLY" if exception
                                    else "COMPLETE_OBSERVATIONS_ONLY")
         and payload["runId"] == info["runId"]
         and payload["sourceManifestSha256"] == pins_sha
         and payload["wireSha256"] == WIRE_SHA
         and payload["scope"] == "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY"
         and payload["layoutQualification"] is False
         and type(payload["layoutQualification"]) is bool
         and payload["cutInputSha256"] == CUT_SHA
         and payload["graphSha256"] == GRAPH_SHA
         and payload["prefixGeometrySha256"] == PREFIX_SHA
         and payload["receiptBranchOrdinal"] == 307
         and payload["lineageBranchOrdinal"] == 77
         and payload["netId"] == "A_COIL_LOW"
         and payload["startPadId"] == "QA.C"
         and payload["rootPadId"] == "DA.A"
         and payload["exceptionStage"] == ("AFTER_PRISTINE_COPY" if exception else None),
         "ATTRIBUTION_IDENTITY")
    for key in ("receiptBranchOrdinal", "lineageBranchOrdinal"):
        _int(payload[key], 0, 1_000_000, "ATTRIBUTION_" + key.upper())
    _validate_lifecycle(payload["lifecycle"], mode)

    geometry = _prefix_geometry(
        payload["prefixGeometryRows"], facts, operation, context["branchReader"],
    )
    need(_same(payload["prefixTraceNetCounts"], geometry["traceNetCounts"]),
         "PREFIX_TRACE_CENSUS_BINDING")

    proof = payload["sourceProof"]
    _keys(proof, ("geometryBeforeSha256", "geometryAfterSha256", "faces"),
          "SOURCE_PROOF_FIELDS")
    need(_digest(proof["geometryBeforeSha256"], "SOURCE_PROOF_BEFORE") == PREFIX_SHA
         and _digest(proof["geometryAfterSha256"], "SOURCE_PROOF_AFTER") == PREFIX_SHA,
         "SOURCE_PROOF_GEOMETRY")
    if exception:
        layer_query_counts = {0: 0}
        cut_queries = []
    else:
        need(cut_document is not None, "CUT_DOCUMENT_REQUIRED")
        cut_queries = cut_document["cutQueries"]
        layer_query_counts = collections.Counter(row["layer"] for row in cut_queries)
    face_totals = _validate_face_rows(proof["faces"], mode, layer_query_counts)

    observations = payload["observations"]
    need(type(observations) is list, "OBSERVATIONS_LIST")
    if exception:
        need(not observations, "EXCEPTION_OBSERVATIONS_EMPTY")
        foreign_count = 0
    else:
        need(len(observations) == LIMITS["queries"], "OBSERVATION_CENSUS")
        radius = _clearance_radius(closure)
        foreign_count = 0
        for ordinal, (row, cut_query) in enumerate(zip(observations, cut_queries)):
            need(type(cut_query) is dict and cut_query["ordinal"] == ordinal,
                 "CUT_QUERY_ORDER")
            if _validate_observation(
                row, cut_query, facts, geometry["knownNets"], radius,
                context["branchReader"],
            ):
                foreign_count += 1

    totals = payload["totals"]
    _keys(totals, (
        "queryCount", "foreignCongestionQueries", "unattributedQueries",
        "copyEntries", "comparisonEntries", "copyUnits", "comparisonUnits",
        "queryUnits", "elapsedNanos", "maximumUnitNanos",
        "geometryRowEntries", "geometryRowUnits",
    ), "ATTRIBUTION_TOTALS_FIELDS")
    query_count = _int(totals["queryCount"], 0, LIMITS["queries"], "TOTAL_QUERY_COUNT")
    foreign = _int(totals["foreignCongestionQueries"], 0, LIMITS["queries"],
                   "TOTAL_FOREIGN_COUNT")
    unattributed = _int(totals["unattributedQueries"], 0, LIMITS["queries"],
                        "TOTAL_UNATTRIBUTED_COUNT")
    copy_entries = _int(totals["copyEntries"], 1, 4 * LIMITS["cloneEntries"]
                        if not exception else LIMITS["cloneEntries"], "TOTAL_COPY_ENTRIES")
    comparison_entries = _int(
        totals["comparisonEntries"], 1,
        LIMITS["observationUnits"] * LIMITS["unitEntries"], "TOTAL_COMPARISON_ENTRIES",
    )
    copy_units = _int(totals["copyUnits"], 2 if exception else 4,
                      LIMITS["observationUnits"], "TOTAL_COPY_UNITS")
    comparison_units = _int(totals["comparisonUnits"], 4 if exception else 6,
                            LIMITS["observationUnits"], "TOTAL_COMPARISON_UNITS")
    query_units = _int(totals["queryUnits"], 0, LIMITS["queries"], "TOTAL_QUERY_UNITS")
    elapsed = _int(totals["elapsedNanos"], 1, LIMITS["operationNanos"],
                   "TOTAL_ELAPSED_NANOS")
    maximum_unit = _int(totals["maximumUnitNanos"], 1, LIMITS["groupNanos"],
                        "TOTAL_MAXIMUM_UNIT_NANOS")
    geometry_entries = _int(totals["geometryRowEntries"], 0, LIMITS["cloneEntries"],
                            "TOTAL_GEOMETRY_ENTRIES")
    geometry_units = _int(totals["geometryRowUnits"], 0, LIMITS["observationUnits"],
                          "TOTAL_GEOMETRY_UNITS")
    expected_geometry_units = (
        geometry["geometryEntries"] + LIMITS["unitEntries"] - 1
    ) // LIMITS["unitEntries"]
    need(geometry_entries == geometry["geometryEntries"]
         and geometry_units == geometry["geometryUnits"] == expected_geometry_units,
         "TOTAL_GEOMETRY_BINDING")
    need(copy_entries == face_totals["copyEntries"]
         and comparison_entries == face_totals["comparisonEntries"]
         and copy_units == face_totals["copyUnits"]
         and comparison_units == face_totals["comparisonUnits"],
         "TOTAL_FACE_BINDING")
    _unit_entries_lower_bound(copy_entries, copy_units, "TOTAL_COPY_CHUNK_BOUND")
    _unit_entries_lower_bound(comparison_entries, comparison_units,
                              "TOTAL_COMPARISON_CHUNK_BOUND")
    need(query_count == (0 if exception else LIMITS["queries"])
         and foreign == foreign_count
         and unattributed == query_count - foreign
         and query_units == query_count
         and elapsed <= info["workNanos"]
         and maximum_unit <= elapsed
         and copy_units + comparison_units + query_units + geometry_units
             <= LIMITS["observationUnits"],
         "TOTAL_ACCOUNTING")
    return {
        "queryCount": query_count, "foreignCongestionQueries": foreign,
        "unattributedQueries": unattributed, "copyEntries": copy_entries,
        "comparisonEntries": comparison_entries, "copyUnits": copy_units,
        "comparisonUnits": comparison_units, "queryUnits": query_units,
        "geometryRowEntries": geometry_entries, "geometryRowUnits": geometry_units,
        "elapsedNanos": elapsed, "maximumUnitNanos": maximum_unit,
    }


def _receipt_rows(raw: bytes, branch_reader: Any) -> List[Dict[str, Any]]:
    need(type(raw) is bytes and 0 < len(raw) <= LIMITS["receiptBytes"]
         and raw.endswith(b"\n") and b"\r" not in raw, "RECEIPT_PREFIX_ENCODING")
    lines = raw.splitlines(keepends=True)
    need(lines and all(line.endswith(b"\n") and line != b"\n"
                       and len(line) <= LIMITS["lineBytes"] for line in lines),
         "RECEIPT_PREFIX_LINES")
    try:
        rows = [branch_reader.strict_json(line[:-1]) for line in lines]
    except Exception:
        raise Rejected("RECEIPT_PREFIX_JSON") from None
    need(all(type(row) is dict for row in rows)
         and rows[0].get("record") == "HEADER", "RECEIPT_PREFIX_OBJECTS")
    return rows


def _non_group_prefix(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    result = [dict(row) for row in rows if row.get("record") != "GROUP"]
    need(result and result[0].get("record") == "HEADER", "RECEIPT_PREFIX_HEADER")
    for row in result:
        row.pop("group", None)
    result[0]["runId"] = "<NORMALIZED_RUN_ID>"
    result[0]["sourceManifestSha256"] = "<NORMALIZED_INPUT_SHA>"
    return result


def _compare_partial_to_control(partial_rows: List[Dict[str, Any]],
                                control_raw: bytes, info: Dict[str, Any],
                                pins_sha: str, branch_reader: Any) -> None:
    partial_header = partial_rows[0]
    need(partial_header.get("runId") == info["runId"]
         and partial_header.get("sourceManifestSha256") == pins_sha
         and partial_header.get("wireSha256") == WIRE_SHA,
         "PARTIAL_RECEIPT_CONTEXT")
    target_positions = [
        index for index, row in enumerate(partial_rows)
        if row.get("record") == "BRANCH" and row.get("ordinal") == 307
    ]
    need(len(target_positions) == 1, "PARTIAL_TARGET_BRANCH")
    partial_index = target_positions[0]
    partial_prefix = partial_rows[:partial_index + 1]
    need(all(row.get("record") == "GROUP" for row in partial_rows[partial_index + 1:]),
         "PARTIAL_RECEIPT_TAIL")

    control_rows = _receipt_rows(control_raw, branch_reader)
    control_positions = [
        index for index, row in enumerate(control_rows)
        if row.get("record") == "BRANCH" and row.get("ordinal") == 307
    ]
    need(len(control_positions) == 1, "CONTROL_TARGET_BRANCH")
    control_prefix = control_rows[:control_positions[0] + 1]
    need(_same(_non_group_prefix(partial_prefix), _non_group_prefix(control_prefix)),
         "PARTIAL_CONTROL_ROUTING_PREFIX_PARITY")


def _exception_marker(info: Dict[str, Any], pins_sha: str) -> Dict[str, Any]:
    native = info["native"]
    need(native["ExitCode"] != 0 and native["Stdout"] == "",
         "EXCEPTION_PROCESS_STREAMS")
    stderr = native["Stderr"]
    marker = "CANONICAL_PREFIX_BLOCKER_EXPECTED_EXCEPTION "
    need(type(stderr) is str and stderr.count(marker) == 1
         and stderr.startswith(marker), "EXCEPTION_MARKER_POSITION")
    first, separator, _tail = stderr.partition("\n")
    need(bool(separator), "EXCEPTION_MARKER_LINE_END")
    original_line = first + "\n"
    if first.endswith("\r"):
        original_line = first + "\n"
        first = first[:-1]
    need(len(original_line.encode("utf-8")) <= LIMITS["lineBytes"],
         "EXCEPTION_MARKER_LINE_SIZE")
    payload = strict_json(first[len(marker):].encode("utf-8"))
    _keys(payload, (
        "schema", "mode", "runId", "sourceManifestSha256", "wireSha256",
        "scope", "layoutQualification", "exceptionType", "exceptionMessage",
        "partialReceipt", "inspector", "inclusiveWorkNanos", "cleanupAccounting",
    ), "EXCEPTION_MARKER_FIELDS")
    need(payload["schema"] == EXCEPTION_SCHEMA
         and payload["mode"] == "INSPECTOR_THROW"
         and payload["runId"] == info["runId"]
         and payload["sourceManifestSha256"] == pins_sha
         and payload["wireSha256"] == WIRE_SHA
         and payload["scope"] == "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY"
         and payload["layoutQualification"] is False
         and type(payload["layoutQualification"]) is bool
         and payload["exceptionType"] == "java.lang.IllegalStateException"
         and payload["exceptionMessage"] == "CANONICAL_PREFIX_INSPECTOR_THROW"
         and payload["cleanupAccounting"] == "FULL_NATIVE_WALL_PLUS_POST_SOURCE",
         "EXCEPTION_MARKER_IDENTITY")
    work = _int(payload["inclusiveWorkNanos"], 1, LIMITS["operationNanos"],
                 "EXCEPTION_INCLUSIVE_WORK")
    need(work == info["workNanos"]
         and work == info["host"]["wrapperWorkNanos"], "EXCEPTION_WORK_BINDING")
    _keys(payload["partialReceipt"], ("path", "bytes", "sha256"),
          "EXCEPTION_PARTIAL_REF_FIELDS")
    need(payload["partialReceipt"]["path"] == "op-1.jsonl",
         "EXCEPTION_PARTIAL_REF_PATH")
    _int(payload["partialReceipt"]["bytes"], 1, LIMITS["receiptBytes"],
         "EXCEPTION_PARTIAL_REF_BYTES")
    _digest(payload["partialReceipt"]["sha256"], "EXCEPTION_PARTIAL_REF_SHA")
    return payload


def _oracle(code: str, function: Any, *args: Any) -> Any:
    try:
        return function(*args)
    except Rejected:
        raise
    except Exception:
        raise Rejected(code) from None


def _load_run(run_root: Path, mode: str, pins_path: Path, pins_raw: bytes,
              pins: Dict[str, Any], pins_sha: str, closure: Dict[str, Any],
              pinned: Dict[str, Dict[str, Any]], context: Dict[str, Any]
              ) -> Tuple[Dict[str, Any], Optional[Dict[str, Any]]]:
    info = _validate_host(
        run_root, pins_path, pins_raw, pins, pins_sha, mode, closure, pinned,
    )
    if mode == "CANARY":
        _data_files(_child(run_root, "data"), set())
        return info, None
    if mode == "INSPECTOR_THROW":
        marker = _exception_marker(info, pins_sha)
        data_root = _child(run_root, "data")
        _data_files(data_root, {"op-1.jsonl"})
        raw = _stable_read(_child(data_root, "op-1.jsonl"), LIMITS["receiptBytes"])
        ref = marker["partialReceipt"]
        need(len(raw) == ref["bytes"] and _sha(raw) == ref["sha256"],
             "EXCEPTION_PARTIAL_RECEIPT_HASH")
        prefix_raw, rows = _receipt_prefix_through_branch(raw, context["branchReader"])
        need(len(prefix_raw) > 0, "EXCEPTION_PARTIAL_PREFIX_EMPTY")
        return info, {"marker": marker, "partialRaw": raw,
                      "partialRows": rows, "prefixRaw": prefix_raw}
    loaded = _oracle(
        "NATIVE_SUCCESS_LOAD", _load_success, run_root, info, mode, pins_sha, context,
    )
    return info, loaded


def _validate_capture_parity(capture: Dict[str, Any], control: Dict[str, Any],
                             context: Dict[str, Any]) -> Dict[str, Any]:
    reader = context["p4Reader"]
    branch_reader = context["branchReader"]
    _oracle("CAPTURE_CONTROL_ROUTE_PARITY", reader._compare_capture,
            capture["receipt"], control["receipt"], branch_reader)
    accounting = _oracle(
        "CAPTURE_PARENT_ROUTE_PARITY", reader._compare_capture,
        capture["receipt"], context["p4ControlReceipt"], branch_reader,
    )
    need(accounting["p07OwnerStepsDelta"] == capture["facts"]["captureWorkUnits"] == 611,
         "CAPTURE_WORK_UNIT_BINDING")
    need(capture["decodedTransport"]["graphBytes"] == context["p4GraphBytes"]
         and _sha(capture["decodedTransport"]["graphBytes"]) == GRAPH_SHA,
         "CAPTURE_GRAPH_PARENT_PARITY")
    p4_facts = dict(context["p4Facts"])
    capture_facts = dict(capture["facts"])
    for key in ("runId", "sourceManifestSha256", "captureActiveElapsedMillis"):
        p4_facts.pop(key, None)
        capture_facts.pop(key, None)
    need(_same(capture_facts, p4_facts), "CAPTURE_FACTS_PARENT_PARITY")
    return accounting


def validate_run(run_root: Any, input_pins: Any, input_sha: str,
                 mode: str) -> Dict[str, Any]:
    need(type(input_sha) is str and HEX.fullmatch(input_sha) is not None, "INPUT_SHA")
    need(mode in ("CANARY", "CONTROL", "CAPTURE", "INSPECTOR_THROW"), "MODE")
    run_root = no_reparse(run_root)
    pins_path = no_reparse(input_pins)
    pins_raw = _stable_read(pins_path, 4 * 1024 * 1024)
    need(_sha(pins_raw) == input_sha, "PINS_HASH")
    pins = strict_json(pins_raw)
    _check_pins_shape(pins)
    pinned = _pin_map(pins)
    closure = _validate_source_closure(pins, pinned, input_sha, pins_path)
    need(_below(run_root, closure["temp"]) and not _below(run_root, closure["repo"]),
         "RUN_ROOT_CONFINEMENT")
    context = _load_p4_context(pins, pinned, closure)
    current_info, current = _load_run(
        run_root, mode, pins_path, pins_raw, pins, input_sha, closure, pinned, context,
    )

    if mode == "CANARY":
        canary = _canary(current_info)
        result = {
            "schema": READER_SCHEMA, "status": "PASS_CANARY_ONLY", "mode": mode,
            "runId": current_info["runId"], "inputPinsSha256": input_sha,
            "scope": "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY",
            "layoutQualification": False, "comparison": "NOT_APPLICABLE",
            "graphDiagnosis": None, "cleanupUpperMs": current_info["cleanupUpperMs"],
            "canary": canary,
        }
        _recheck_pins(pinned, pins_path, pins_raw)
        _recheck_source_files(closure["currentFiles"])
        return result

    control_root = Path(pins["controlRunRoot"])
    if mode == "CONTROL":
        need(_same_path(run_root, control_root), "CONTROL_RUN_ROOT")
        control_info, control = current_info, current
    else:
        need(not _same_path(run_root, control_root), "NONCONTROL_RUN_ROOT")
        control_info, control = _load_run(
            control_root, "CONTROL", pins_path, pins_raw, pins, input_sha,
            closure, pinned, context,
        )
    need(control is not None and control_info["host"]["mode"] == "CONTROL",
         "CONTROL_RUN_REQUIRED")
    _oracle("CONTROL_PARENT_PARITY", context["p4Reader"]._compare,
            control["receipt"], context["p4ControlReceipt"],
            context["branchReader"], "CONTROL_PARENT_PARITY")

    if mode == "CONTROL":
        need(current is not None, "CONTROL_LOAD")
        result = {
            "schema": READER_SCHEMA, "status": "PASS_CONTROL_ONLY", "mode": mode,
            "runId": current_info["runId"], "inputPinsSha256": input_sha,
            "scope": "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY",
            "layoutQualification": False, "comparison": "PASS_P4_CONTROL_ROUTE_PARITY",
            "graphDiagnosis": None, "cleanupUpperMs": current_info["cleanupUpperMs"],
            "operation": {"receiptBytes": len(current["receipt"]),
                          "groupCount": len(current["operation"]["groups"]),
                          "selection": current["index"]["selection"]},
        }
        _recheck_pins(pinned, pins_path, pins_raw)
        _recheck_source_files(closure["currentFiles"])
        return result

    if mode == "CAPTURE":
        need(current is not None, "CAPTURE_LOAD")
        accounting = _validate_capture_parity(current, control, context)
        cut_document = _validate_cut_document(
            Path(pins["cutInputPath"]), input_sha,
            current["decodedTransport"]["graph"], current["facts"],
        )
        attribution = strict_json(current["attributionRaw"][:-1])
        totals = _validate_attribution(
            current["attributionRaw"], attribution, mode, current_info,
            current["facts"], current["operation"], context, closure, input_sha,
            cut_document,
        )
        result = {
            "schema": READER_SCHEMA, "status": "PASS_CAPTURE_ATTRIBUTION_ONLY",
            "mode": mode, "runId": current_info["runId"],
            "inputPinsSha256": input_sha,
            "scope": "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY",
            "layoutQualification": False,
            "comparison": "PASS_CONTROL_ROUTE_AND_GRAPH_PARITY",
            "captureAccounting": accounting,
            "graphDiagnosis": {
                "claim": "FINITE_PREFIX_QUERY_ATTRIBUTION_ONLY",
                "graphSha256": GRAPH_SHA,
                "cutQueryCount": totals["queryCount"],
                "foreignCongestionQueries": totals["foreignCongestionQueries"],
                "unattributedQueries": totals["unattributedQueries"],
                "prefixTraceNetCounts": attribution["prefixTraceNetCounts"],
                "prefixHoleCountsByKind": current["facts"]["prefixHoleCountsByKind"],
                "productionFeasibility": "UNKNOWN",
                "route": False, "layout": False, "electrical": False, "q100": False,
            },
            "cleanupUpperMs": current_info["cleanupUpperMs"],
            "operation": {"receiptBytes": len(current["receipt"]),
                          "groupCount": len(current["operation"]["groups"]),
                          "receiptBranchOrdinal": 307,
                          "lineageBranchOrdinal": 77},
            "attributionTotals": totals,
        }
        _recheck_pins(pinned, pins_path, pins_raw)
        _recheck_source_files(closure["currentFiles"])
        return result

    need(mode == "INSPECTOR_THROW" and current is not None, "EXCEPTION_LOAD")
    _oracle("PARTIAL_CONTROL_PREFIX", _compare_partial_to_control,
            current["partialRows"], control["receipt"], current_info, input_sha,
            context["branchReader"])
    exception_payload = current["marker"]["inspector"]
    exception_totals = _validate_attribution(
        None, exception_payload, mode, current_info, context["p4Facts"],
        context["p4CaptureOperation"], context, closure, input_sha, None,
    )
    result = {
        "schema": READER_SCHEMA,
        "status": "PASS_EXPECTED_INSPECTOR_THROW_CLEANUP_ONLY",
        "mode": mode, "runId": current_info["runId"],
        "inputPinsSha256": input_sha,
        "scope": "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY",
        "layoutQualification": False,
        "comparison": "PASS_CONTROL_PREFIX_THROUGH_BRANCH_307",
        "graphDiagnosis": None,
        "cleanupUpperMs": current_info["cleanupUpperMs"],
        "partialOperation": {
            "receiptBytes": len(current["partialRaw"]),
            "prefixBytesThroughBranch307": len(current["prefixRaw"]),
            "exceptionStage": exception_payload["exceptionStage"],
        },
        "attributionTotals": exception_totals,
    }
    _recheck_pins(pinned, pins_path, pins_raw)
    _recheck_source_files(closure["currentFiles"])
    return result


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 4:
        print(json.dumps({
            "schema": READER_SCHEMA, "status": "REJECTED",
            "error": "ARGUMENT_COUNT_INVALID",
            "acceptance": {"route": False, "layout": False,
                           "electrical": False, "q100": False},
        }, sort_keys=True, separators=(",", ":")))
        return 2
    try:
        result = validate_run(args[0], args[1], args[2], args[3])
    except Rejected as error:
        code = str(error) if str(error) else "REJECTED"
        result = {
            "schema": READER_SCHEMA, "status": "REJECTED",
            "error": code[:80],
            "acceptance": {"route": False, "layout": False,
                           "electrical": False, "q100": False},
        }
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 2
    except Exception:
        result = {
            "schema": READER_SCHEMA, "status": "REJECTED",
            "error": "READER_INTERNAL_REJECTION",
            "acceptance": {"route": False, "layout": False,
                           "electrical": False, "q100": False},
        }
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 2
    print(json.dumps(result, ensure_ascii=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
