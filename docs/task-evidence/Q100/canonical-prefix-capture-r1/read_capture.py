"""Reader for the frozen canonical BASE fixed-prefix capture.

This validates actual source/process/receipt/transport evidence and runs only the
maintained independent relaxed-graph VIA oracle. It never accepts a route,
layout, or electrical design.
"""
from __future__ import annotations

import base64
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

sys.dont_write_bytecode = True
_ORACLE_DIR = Path(__file__).resolve().parent / "oracles-r1"
if str(_ORACLE_DIR) not in sys.path:
    sys.path.insert(0, str(_ORACLE_DIR))
import read_fixed_prefix_graph as graph_oracle
import fixed_prefix_minimum_vias as via_oracle

INPUT_SCHEMA = "tsj-canonical-prefix-inputs-v1"
HOST_SCHEMA = "tsj-canonical-prefix-host-v1"
HOST_RESULT_SCHEMA = "tsj-canonical-prefix-host-result-v1"
INDEX_SCHEMA = "tsj-canonical-prefix-index-v1"
TRANSPORT_SCHEMA = "tsj-canonical-prefix-transport-v1"
FACTS_SCHEMA = "tsj-canonical-prefix-facts-v1"
READER_SCHEMA = "tsj-canonical-prefix-reader-result-v1"
WIRE = "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb"
BASE_MANIFEST_SHA = "8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab"
P2_READER_SHA = "e27bfeab6d41ac32cbf5bcd1dd87c4a04186a833da21d2e1a68286f2a7310497"
OLD_BRANCH_READER_SHA = "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"
OLD_CONTROL_RECEIPT_SHA = "108b66d6ff45111e6dee2be0e912af4089cce0a6463a2e5c6358d42096c2bf4f"
PLAN_SHA = "cdfae32ed6e569b76fb529f247a3cede8384ca6ea4eb748b924fa6a4c4db1962"
PROTOCOL_SHA = "8b7a58e17c53460bae642bb7a564f39d8383e87a2c2e3dc7960b1ae44aaeaeec"
ADDENDUM_SHA = "ac159a550890c7a785106d01e983b863d6aba07ca2b2414b8a679503d2d7f28f"
GRAPH_ORACLE_SHA = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
VIA_ORACLE_SHA = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"
CAPTURE_ORACLE_SHA = "26cbdc32361f07f759cc32dd3fe432fd640e329ce7e51dd583094dc10d0af962"
PYTHON_SHA = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
CASE_ID = "CANONICAL_SEPARATE_REQ0_PASS2_A_COIL_LOW"
SCOPE = "CANONICAL_BASE_REQUEST0_ONLY"
MODE_SCOPE = {
    "CANARY": "CANONICAL_PREFIX_CANARY_ONLY",
    "CONTROL": "CANONICAL_PREFIX_CONTROL_ONLY",
    "CAPTURE": "CANONICAL_PREFIX_CAPTURE_DIAGNOSIS_ONLY",
    "MISSING_TARGET": "EXPECTED_MISSING_TARGET_CLEANUP_ONLY",
}
LIMITS = {
    "compileMs": 60000, "nativeMs": 480000, "cleanupMs": 120000,
    "outerMs": 1800000, "wrapperNanos": 90000000000,
    "groupNanos": 5000000000, "groups": 640, "receiptBytes": 8388608,
    "lineBytes": 65536, "chunkBytes": 32768, "indexBytes": 65536,
    "aggregateBytes": 8388608, "sourceBytes": 512 * 1024 * 1024,
    "sourceCount": 1084,
}
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
HEX = re.compile(r"[0-9a-f]{64}\Z")
SEED = re.compile(r"(?:0|-?[1-9][0-9]*)\Z")
TOKEN = re.compile(r"[-+A-Za-z0-9_.:]{1,128}\Z")
CHANGED_PATHS = {
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java",
}
FACT_KEYS = {
    "schema", "runId", "sourceManifestSha256", "wireSha256", "caseId",
    "rootSeed", "placementSeed", "routingSeed", "variant", "layoutVersion",
    "dependencyEpoch", "componentCount", "padCount", "netCount",
    "routeRequestIndex", "selectedPlacementIndex", "selectedPlacementAttempt",
    "eligiblePlacementAttempts", "receiptBranchOrdinal", "lineageBranchOrdinal",
    "pass", "netId", "startPadId", "rootPadId", "startX", "startY", "rootX",
    "rootY", "branchExpanded", "totalRouteExpansions", "terminalReason",
    "prefixHoleCount", "prefixViaCount", "prefixHoleCountsByKind",
    "reachedPadIds", "reachedPthPadIds", "rawPlacementFingerprint",
    "rawPlacementFingerprintUtf8Bytes", "rawPlacementSha256",
    "prefixGeometryFingerprint", "prefixGeometryFingerprintUtf8Bytes",
    "prefixGeometrySha256", "gridWidth", "gridHeight", "gridPitch", "originX",
    "originY", "primaryLayer", "startCell", "initialFacesMask", "cellCount",
    "captureWorkUnits", "copyEntries", "goalProbes", "ordinaryEdgeProbes",
    "viaProbes", "comparisonEntries", "captureActiveElapsedMillis",
    "viaMaskSemantics", "maximumHoles", "remainingHoles",
    "maximumBranchTransitions", "remainingBranchTransitionsFromPrefix",
}


class Rejected(ValueError):
    """Sanitized input validation failure."""


def need(ok: bool, code: str) -> None:
    if not ok:
        raise Rejected(code)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _pairs(rows: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    out: Dict[str, Any] = {}
    for key, value in rows:
        need(key not in out, "JSON_DUPLICATE_KEY")
        out[key] = value
    return out


def _parse_int(text: str) -> int:
    need(text != "-0", "JSON_NEGATIVE_ZERO")
    return int(text)


def _parse_float(text: str) -> float:
    value = float(text)
    need(math.isfinite(value), "JSON_NONFINITE")
    return value


def _constant(_text: str) -> None:
    raise Rejected("JSON_NONFINITE")


def strict_json(raw: bytes) -> Any:
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_TYPE_OR_BOM")
    try:
        value = json.loads(raw.decode("utf-8", "strict"),
                           object_pairs_hook=_pairs, parse_int=_parse_int,
                           parse_float=_parse_float, parse_constant=_constant)
    except Rejected:
        raise
    except (UnicodeError, json.JSONDecodeError, RecursionError, ValueError):
        raise Rejected("JSON_INVALID") from None

    def strings(item: Any) -> None:
        if type(item) is str:
            need(not any(0xd800 <= ord(char) <= 0xdfff for char in item), "JSON_SURROGATE")
        elif type(item) is dict:
            for key, child in item.items():
                strings(key)
                strings(child)
        elif type(item) is list:
            for child in item:
                strings(child)
    strings(value)
    return value


def _keys(value: Any, expected: Iterable[str], code: str) -> None:
    need(type(value) is dict and set(value) == set(expected), code)


def _int(value: Any, low: int = 0, high: int = (1 << 63) - 1,
         code: str = "INTEGER_INVALID") -> int:
    need(type(value) is int and low <= value <= high, code)
    return value


def _digest(value: Any, code: str = "SHA256_INVALID") -> str:
    need(type(value) is str and HEX.fullmatch(value) is not None, code)
    return value


def _token(value: Any, code: str = "TOKEN_INVALID") -> str:
    need(type(value) is str and TOKEN.fullmatch(value) is not None, code)
    return value


def _reparse(path: Path) -> bool:
    try:
        st = path.lstat()
    except FileNotFoundError:
        return False
    except OSError:
        return True
    return stat.S_ISLNK(st.st_mode) or bool(getattr(st, "st_file_attributes", 0) & 0x400)


def no_reparse(path: Any) -> Path:
    absolute = Path(os.path.abspath(path))
    for item in (absolute, *absolute.parents):
        need(not _reparse(item), "REPARSE_PATH")
    return absolute


def stable_read(path: Any, maximum: int = LIMITS["receiptBytes"],
                allow_empty: bool = False) -> bytes:
    target = no_reparse(path)
    try:
        before = target.stat()
        need(stat.S_ISREG(before.st_mode), "FILE_KIND")
        need(0 <= before.st_size <= maximum if allow_empty else
             0 < before.st_size <= maximum, "FILE_SIZE")
        def identity(st: os.stat_result) -> Tuple[Any, ...]:
            return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink
        with target.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            raw = stream.read(maximum + 1)
            closed = os.fstat(stream.fileno())
        after = target.stat()
        need(identity(before) == identity(opened) == identity(closed) == identity(after)
             and before.st_ctime_ns == after.st_ctime_ns
             and opened.st_ctime_ns == closed.st_ctime_ns
             and len(raw) == after.st_size and len(raw) <= maximum,
             "FILE_CHANGED_OR_SIZE")
        no_reparse(target)
        return raw
    except Rejected:
        raise
    except (OSError, ValueError):
        raise Rejected("FILE_READ_FAILED") from None


def _same(a: Any, b: Any) -> bool:
    return os.path.normcase(os.path.normpath(str(a))) == os.path.normcase(
        os.path.normpath(str(b)))


def _below(path: Any, parent: Any) -> bool:
    child, base = Path(os.path.abspath(path)), Path(os.path.abspath(parent))
    return _same(child, base) or any(_same(base, part) for part in child.parents)


def _relative(value: Any) -> str:
    need(type(value) is str and 0 < len(value) <= 4096, "SOURCE_PATH_INVALID")
    normal = value.replace("\\", "/")
    need(value == normal and not normal.startswith("/")
         and re.match(r"^[A-Za-z]:", normal) is None
         and all(part not in ("", ".", "..") for part in normal.split("/")),
         "SOURCE_PATH_INVALID")
    return normal


def _child(root: Any, name: str) -> Path:
    base = Path(os.path.abspath(root))
    path = Path(os.path.abspath(base / name))
    need(path != base and base in path.parents, "RUN_PATH_ESCAPE")
    return path


def _object(root: Any, name: str, limit: int = 1024 * 1024) -> Any:
    return strict_json(stable_read(_child(root, name), limit))


def _pin_map(pins: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
    rows = pins.get("files")
    need(type(rows) is list and len(rows) >= 8, "PINS_FILES")
    pinned: Dict[str, Dict[str, Any]] = {}
    for row in rows:
        _keys(row, ("path", "bytes", "sha256"), "PIN_ROW_FIELDS")
        path = Path(row["path"])
        need(path.is_absolute() and str(path) == os.path.abspath(path),
             "PIN_PATH_ABSOLUTE")
        path = no_reparse(path).resolve(strict=True)
        need(_same(path, row["path"]), "PIN_PATH_CANONICAL")
        size = _int(row["bytes"], 0, 16 * 1024 * 1024, "PIN_BYTES")
        digest = _digest(row["sha256"], "PIN_SHA")
        key = os.path.normcase(str(path))
        need(key not in pinned, "DUPLICATE_PIN")
        pinned[key] = {"path": path, "bytes": size, "sha256": digest}
    return pinned


def _pin(pinned: Dict[str, Dict[str, Any]], path: Any,
         expected: Optional[str] = None) -> Dict[str, Any]:
    target = Path(os.path.abspath(path))
    key = os.path.normcase(str(target))
    need(key in pinned, "UNPINNED_INPUT")
    row = pinned[key]
    need(expected is None or row["sha256"] == expected, "INPUT_PIN_VALUE")
    raw = stable_read(row["path"], 16 * 1024 * 1024, allow_empty=True)
    need(len(raw) == row["bytes"] and _sha(raw) == row["sha256"], "INPUT_CLOSE_HASH")
    return row


def _p2_helper_path(pinned: Dict[str, Dict[str, Any]]) -> Path:
    matches = [r["path"] for r in pinned.values() if r["sha256"] == P2_READER_SHA]
    need(len(matches) == 1, "P2_HELPER_CENSUS")
    return Path(matches[0])


def _p2_module(pinned: Dict[str, Dict[str, Any]]) -> Any:
    path = _p2_helper_path(pinned)
    row = pinned.get(os.path.normcase(str(path)))
    need(row is not None and row["sha256"] == P2_READER_SHA, "P2_HELPER_PIN")
    raw = stable_read(path, 2 * 1024 * 1024)
    need(_sha(raw) == P2_READER_SHA and len(raw) == row["bytes"], "P2_HELPER_HASH")
    spec = importlib.util.spec_from_file_location("p2_canonical_prefix_helpers", path)
    need(spec is not None and spec.loader is not None, "P2_HELPER_IMPORT")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _source_closure(pins: Dict[str, Any], pinned: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    need(pins.get("schema") == INPUT_SCHEMA, "PINS_SCHEMA")
    repo, historical = Path(pins.get("repositoryRoot", "")), Path(pins.get("historicalRoot", ""))
    baseline_path = Path(pins.get("baselineManifestPath", ""))
    need(repo.is_absolute() and historical.is_absolute() and baseline_path.is_absolute()
         and not _same(repo, historical), "SOURCE_ROOTS")
    need(pins.get("parent") == "96611058bc41f208a0de677bf06c677c909471e3"
         and pins.get("baselineManifestSha256") == BASE_MANIFEST_SHA,
         "BASELINE_IDENTITY")
    _pin(pinned, baseline_path, BASE_MANIFEST_SHA)
    for key, digest in (("planPath", PLAN_SHA), ("protocolPath", PROTOCOL_SHA),
                        ("addendumPath", ADDENDUM_SHA)):
        path = Path(pins.get(key, ""))
        need(path.is_absolute(), "FROZEN_INPUT_PATH")
        _pin(pinned, path, digest)
    preflight = Path(pins.get("preflight", ""))
    need(preflight.is_absolute(), "PREFLIGHT_PATH")
    _pin(pinned, preflight)

    base_doc = strict_json(stable_read(baseline_path, 16 * 1024 * 1024))
    base_rows, current_rows, changes = (
        base_doc.get("files"), pins.get("sourceRows"), pins.get("changedRows"))
    need(type(base_rows) is list and len(base_rows) == LIMITS["sourceCount"]
         and type(current_rows) is list and len(current_rows) == LIMITS["sourceCount"]
         and type(changes) is list and len(changes) == 2, "SOURCE_CENSUS")
    base_by_path: Dict[str, Dict[str, Any]] = {}
    for row in base_rows:
        need(type(row) is dict and type(row.get("path")) is str, "BASE_SOURCE_ROW")
        path = _relative(row["path"])
        need(path not in base_by_path, "BASE_SOURCE_DUPLICATE")
        base_by_path[path] = row
    current_by_path: Dict[str, Dict[str, Any]] = {}
    total_bytes = 0
    for row in current_rows:
        _keys(row, ("role", "path", "bytes", "sha256"), "SOURCE_ROW_FIELDS")
        path = _relative(row["path"])
        need(path in base_by_path and path not in current_by_path
             and row["role"] == base_by_path[path].get("role"), "SOURCE_ROW_IDENTITY")
        size = _int(row["bytes"], 0, 100 * 1024 * 1024, "SOURCE_ROW_BYTES")
        digest = _digest(row["sha256"])
        total_bytes += size
        need(total_bytes <= LIMITS["sourceBytes"], "SOURCE_BYTES_CAP")
        current_by_path[path] = row
    need(set(current_by_path) == set(base_by_path), "SOURCE_PATH_CENSUS")

    change_by_path: Dict[str, Dict[str, Any]] = {}
    for row in changes:
        _keys(row, ("path", "before", "after"), "CHANGED_ROW_FIELDS")
        path = _relative(row["path"])
        need(path in CHANGED_PATHS and path not in change_by_path,
             "CHANGED_PATH_ALLOWLIST")
        _keys(row["before"], ("bytes", "sha256"), "CHANGED_BEFORE_FIELDS")
        _keys(row["after"], ("bytes", "sha256"), "CHANGED_AFTER_FIELDS")
        change_by_path[path] = row
    need(set(change_by_path) == CHANGED_PATHS, "CHANGED_PATH_CENSUS")

    repo = no_reparse(repo).resolve(strict=True)
    historical = no_reparse(historical).resolve(strict=True)
    need(repo.is_dir() and historical.is_dir(), "SOURCE_ROOT_KIND")
    role_count = 0
    for relative, current in current_by_path.items():
        before = base_by_path[relative]
        before_bytes = _int(before.get("bytes"), 0, 100 * 1024 * 1024, "BASE_BYTES")
        before_sha = _digest(before.get("sha256"), "BASE_SHA")
        after_bytes = current["bytes"]
        after_sha = current["sha256"]
        role_count += before.get("role") == "BASE_SOURCE"
        old_file = no_reparse(historical.joinpath(*relative.split("/")))
        new_file = no_reparse(repo.joinpath(*relative.split("/")))
        old_raw = stable_read(old_file, 100 * 1024 * 1024, allow_empty=True)
        new_raw = stable_read(new_file, 100 * 1024 * 1024, allow_empty=True)
        need(len(old_raw) == before_bytes and _sha(old_raw) == before_sha,
             "HISTORICAL_SOURCE_HASH")
        need(len(new_raw) == after_bytes and _sha(new_raw) == after_sha,
             "CURRENT_SOURCE_HASH")
        change = change_by_path.get(relative)
        if change is None:
            need((after_bytes, after_sha) == (before_bytes, before_sha),
                 "UNEXPECTED_SOURCE_DELTA")
        else:
            old_size = _int(change["before"]["bytes"], 0, 16 * 1024 * 1024)
            new_size = _int(change["after"]["bytes"], 0, 16 * 1024 * 1024)
            need((old_size, change["before"]["sha256"]) == (before_bytes, before_sha)
                 and (new_size, change["after"]["sha256"]) == (after_bytes, after_sha),
                 "CHANGED_SOURCE_BINDING")
    need(role_count > 0, "BASE_ROLE_CENSUS")

    mode_files = pins.get("modeFiles")
    need(type(mode_files) is dict and set(mode_files) ==
         {"router", "support", "writer", "adapter"}, "MODE_FILE_FIELDS")
    for value in mode_files.values():
        need(type(value) is str and Path(value).is_absolute(), "MODE_FILE_PATH")
        _pin(pinned, value)

    bridge_root, bridge_manifest = Path(pins.get("bridgeRoot", "")), Path(
        pins.get("bridgeManifest", ""))
    need(bridge_root.is_absolute() and bridge_manifest.is_absolute(), "BRIDGE_PATHS")
    _pin(pinned, bridge_manifest)
    bridge_doc = strict_json(stable_read(bridge_manifest, 65536))
    need(type(bridge_doc) is list and len(bridge_doc) == 3, "BRIDGE_MANIFEST")
    for row in bridge_doc:
        need(type(row) is dict and type(row.get("path")) is str, "BRIDGE_ROW")
        _pin(pinned, bridge_root / ("bridge-" + row["path"]), row.get("sha256"))

    p2_path = _p2_helper_path(pinned)
    _pin(pinned, p2_path, P2_READER_SHA)
    branch_reader = repo / "tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py"
    _pin(pinned, branch_reader, OLD_BRANCH_READER_SHA)
    for name, digest in (("read_fixed_prefix_graph.py", GRAPH_ORACLE_SHA),
                         ("fixed_prefix_minimum_vias.py", VIA_ORACLE_SHA),
                         ("read_fixed_prefix_capture.py", CAPTURE_ORACLE_SHA)):
        oracle = (_ORACLE_DIR / name).resolve(strict=True)
        _pin(pinned, oracle, digest)
    self_row = pinned.get(os.path.normcase(str(Path(__file__).resolve())))
    need(self_row is not None and
         _sha(stable_read(Path(__file__), 2 * 1024 * 1024)) == self_row["sha256"],
         "READER_SELF_PIN")

    parent_control = Path(pins.get("parentControlReceipt", ""))
    need(parent_control.is_absolute(), "PARENT_CONTROL_PATH")
    _pin(pinned, parent_control, OLD_CONTROL_RECEIPT_SHA)
    control_root = Path(pins.get("controlRunRoot", ""))
    need(control_root.is_absolute() and str(control_root) == os.path.abspath(control_root),
         "CONTROL_ROOT_SCALAR")
    temp = Path(os.path.abspath(os.environ.get("TEMP") or
                                os.environ.get("TMP") or
                                Path.home() / "AppData/Local/Temp"))
    need(_below(control_root, temp) and not _below(control_root, repo),
         "CONTROL_ROOT_CONFINEMENT")
    return {
        "repo": repo, "historical": historical, "baseline": baseline_path,
        "sourceRows": current_by_path, "changedRows": change_by_path,
        "modeFiles": mode_files, "bridgeRoot": bridge_root,
        "parentControlReceipt": parent_control, "controlRunRoot": control_root,
        "temp": temp, "extraFiles": len(pinned),
    }


def select_lineage(branches: Sequence[Dict[str, Any]],
                   ordinal_in_pass: int) -> Optional[Dict[str, Any]]:
    """Resolve a one-based ordinal in pass 2, distinct from global BRANCH ordinal."""
    need(type(branches) in (list, tuple), "SELECTOR_BRANCHES")
    ordinal = _int(ordinal_in_pass, 1, 100000, "SELECTOR_LINEAGE_ORDINAL")
    rows = [row for row in branches if type(row) is dict
            and type(row.get("snapshot")) is dict
            and row["snapshot"].get("pass") == 2]
    return rows[ordinal - 1] if ordinal <= len(rows) else None


def validate_target_selector(operation: Dict[str, Any]) -> Dict[str, Any]:
    need(type(operation) is dict and type(operation.get("branches")) is list,
         "SELECTOR_OPERATION")
    branches = operation["branches"]
    global_rows = [row for row in branches
                   if type(row) is dict and row.get("ordinal") == 307]
    pass_two = [row for row in branches if type(row) is dict
                and type(row.get("snapshot")) is dict
                and row["snapshot"].get("pass") == 2]
    selected = select_lineage(branches, 77)
    need(len(global_rows) == 1 and len(pass_two) == 77
         and selected is global_rows[0], "SELECTOR_ORDINAL_BINDING")
    snap = selected["snapshot"]
    need(snap.get("netId") == "A_COIL_LOW"
         and snap.get("startPadId") == "QA.C" and snap.get("rootPadId") == "DA.A"
         and snap.get("branchExpanded") == 25 and snap.get("totalExpanded") == 918222
         and snap.get("terminalReason") == "NO_PATH"
         and snap.get("frontierCount") == 0, "SELECTOR_TARGET_SNAPSHOT")
    return {"branch": selected, "snapshot": snap,
            "receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77}


def zero_via_result(graph: Any) -> Dict[str, Any]:
    """Use the maintained VIA-count oracle; infer only zero-VIA connectivity."""
    try:
        checked = via_oracle.analyze_minimum_vias(graph)
    except (via_oracle.MinimumViaError, TypeError, ValueError):
        raise Rejected("ZERO_VIA_GRAPH_INVALID") from None
    minimum = checked["minimumViaCount"]
    if minimum == 0:
        status = "ZERO_VIA_CONNECTED"
    else:
        status = "ZERO_VIA_ROUTE_EXCLUDED"
    return {
        "schema": via_oracle.RESULT_SCHEMA, "status": status,
        "minimumViaCount": minimum, "termination": checked["termination"],
        "reachable": checked["reachable"], "claim": "ZERO_VIA_CONNECTIVITY_ONLY",
        "productionFeasibility": "UNKNOWN",
        "acceptance": {"route": False, "layout": False, "electrical": False,
                       "q100": False},
    }


def decode_transport(raw: bytes, parse_payloads: bool = True) -> Dict[str, Any]:
    """Decode and verify chunk ordering, canonical base64, hashes, limits and EOF."""
    need(type(raw) is bytes and 0 < len(raw) <= LIMITS["aggregateBytes"],
         "TRANSPORT_SIZE")
    need(raw.endswith(b"\n") and b"\r" not in raw, "TRANSPORT_LINE_ENDING")
    lines = raw[:-1].split(b"\n")
    need(lines and all(0 < len(line) <= LIMITS["lineBytes"] for line in lines),
         "TRANSPORT_LINE_SIZE")
    rows = [strict_json(line) for line in lines]
    header = rows[0]
    _keys(header, ("record", "schema", "runId", "sourceManifestSha256",
                   "wireSha256", "scope", "payloads", "aggregateCapBytes",
                   "chunkBytes"), "TRANSPORT_HEADER_FIELDS")
    need(header["record"] == "HEADER" and header["schema"] == TRANSPORT_SCHEMA
         and header["wireSha256"] == WIRE and header["scope"] == SCOPE
         and _int(header["aggregateCapBytes"], 1, LIMITS["aggregateBytes"]) ==
         LIMITS["aggregateBytes"]
         and _int(header["chunkBytes"], 1, LIMITS["chunkBytes"]) ==
         LIMITS["chunkBytes"], "TRANSPORT_HEADER")
    need(type(header["runId"]) is str and UUID.fullmatch(header["runId"]) is not None,
         "TRANSPORT_RUN_ID")
    _digest(header["sourceManifestSha256"], "TRANSPORT_SOURCE_SHA")
    descriptors = header["payloads"]
    need(type(descriptors) is list and len(descriptors) == 2, "TRANSPORT_PAYLOADS")
    descs: Dict[str, Dict[str, Any]] = {}
    for kind, item in zip(("GRAPH", "FACTS"), descriptors):
        _keys(item, ("kind", "bytes", "sha256", "chunks"), "TRANSPORT_DESCRIPTOR")
        need(item["kind"] == kind, "TRANSPORT_PAYLOAD_ORDER")
        size = _int(item["bytes"], 1, LIMITS["aggregateBytes"], "TRANSPORT_PAYLOAD_BYTES")
        count = _int(item["chunks"], 1, 256, "TRANSPORT_CHUNK_COUNT")
        need(count == (size + LIMITS["chunkBytes"] - 1) // LIMITS["chunkBytes"],
             "TRANSPORT_CHUNK_COUNT")
        _digest(item["sha256"], "TRANSPORT_PAYLOAD_SHA")
        descs[kind] = item
    payload_total = sum(item["bytes"] for item in descs.values())
    need(payload_total <= LIMITS["aggregateBytes"], "TRANSPORT_AGGREGATE_CAP")
    footer = rows[-1]
    _keys(footer, ("record", "complete", "graphChunks", "factChunks", "payloadBytes"),
          "TRANSPORT_FOOTER_FIELDS")
    need(footer["record"] == "FOOTER" and footer["complete"] is True
         and type(footer["complete"]) is bool
         and _int(footer["graphChunks"], 1, 256) == descs["GRAPH"]["chunks"]
         and _int(footer["factChunks"], 1, 256) == descs["FACTS"]["chunks"]
         and _int(footer["payloadBytes"], 1, LIMITS["aggregateBytes"]) == payload_total,
         "TRANSPORT_FOOTER")
    chunk_rows = rows[1:-1]
    need(len(chunk_rows) == sum(item["chunks"] for item in descs.values()),
         "TRANSPORT_CHUNK_CENSUS")
    output = {"GRAPH": bytearray(), "FACTS": bytearray()}
    order = (["GRAPH"] * descs["GRAPH"]["chunks"] +
             ["FACTS"] * descs["FACTS"]["chunks"])
    indices = {"GRAPH": 0, "FACTS": 0}
    for row, kind in zip(chunk_rows, order):
        _keys(row, ("record", "kind", "index", "offset", "bytes", "data"),
              "TRANSPORT_CHUNK_FIELDS")
        need(row["record"] == "CHUNK" and row["kind"] == kind,
             "TRANSPORT_CHUNK_ORDER")
        desc, data = descs[kind], output[kind]
        index = _int(row["index"], 0, desc["chunks"] - 1, "TRANSPORT_CHUNK_INDEX")
        need(index == indices[kind], "TRANSPORT_CHUNK_INDEX")
        need(_int(row["offset"], 0, desc["bytes"], "TRANSPORT_CHUNK_OFFSET") == len(data),
             "TRANSPORT_CHUNK_OFFSET")
        expected = min(LIMITS["chunkBytes"], desc["bytes"] - len(data))
        need(_int(row["bytes"], 1, LIMITS["chunkBytes"],
                  "TRANSPORT_CHUNK_BYTES") == expected, "TRANSPORT_CHUNK_BYTES")
        encoded = row["data"]
        need(type(encoded) is str and encoded.isascii(), "TRANSPORT_BASE64")
        try:
            decoded = base64.b64decode(encoded, validate=True)
        except (ValueError, base64.binascii.Error):
            raise Rejected("TRANSPORT_BASE64") from None
        need(base64.b64encode(decoded).decode("ascii") == encoded
             and len(decoded) == expected, "TRANSPORT_BASE64_CANONICAL")
        data.extend(decoded)
        indices[kind] += 1
    payload = {kind: bytes(data) for kind, data in output.items()}
    for kind, value in payload.items():
        need(len(value) == descs[kind]["bytes"]
             and _sha(value) == descs[kind]["sha256"], "TRANSPORT_PAYLOAD_HASH")
    result = {"header": header, "footer": footer, "graphBytes": payload["GRAPH"],
              "factsBytes": payload["FACTS"], "graph": None, "facts": None}
    if parse_payloads:
        need(not payload["GRAPH"].startswith(b"\xef\xbb\xbf"), "GRAPH_BOM")
        try:
            result["graph"] = graph_oracle.parse_graph_bytes(payload["GRAPH"])
        except (graph_oracle.GraphInputError, ValueError):
            raise Rejected("GRAPH_PAYLOAD_INVALID") from None
        result["facts"] = strict_json(payload["FACTS"])
    return result


def _entry(value: Any) -> Dict[str, Any]:
    _keys(value, ("operation", "armId", "receipt", "elapsedToCloseNanos",
                  "maxFullGroupNanos", "cleanup"), "ENTRY_FIELDS")
    need(value["operation"] == 1 and type(value["operation"]) is int
         and value["armId"] == "OBSERVED_DEFAULT_NEAREST", "ENTRY_IDENTITY")
    _keys(value["receipt"], ("path", "bytes", "sha256"), "ENTRY_RECEIPT_FIELDS")
    need(value["receipt"]["path"] == "op-1.jsonl", "ENTRY_RECEIPT_PATH")
    _int(value["receipt"]["bytes"], 1, LIMITS["receiptBytes"], "ENTRY_RECEIPT_BYTES")
    _digest(value["receipt"]["sha256"])
    _int(value["elapsedToCloseNanos"], 1, LIMITS["wrapperNanos"], "ENTRY_ELAPSED")
    _int(value["maxFullGroupNanos"], 1, LIMITS["groupNanos"], "ENTRY_GROUP_MAX")
    cleanup = value["cleanup"]
    _keys(cleanup, ("status", "elapsedNanos", "ownersReleased", "contextRestored"),
          "ENTRY_CLEANUP_FIELDS")
    need(cleanup["status"] == "PASS" and cleanup["ownersReleased"] is True
         and cleanup["contextRestored"] is True, "ENTRY_CLEANUP")
    _int(cleanup["elapsedNanos"], 0, 120_000_000_000, "ENTRY_CLEANUP_NANOS")
    return value


def _index(raw: bytes, value: Any, mode: str, run_id: str,
           input_sha: str) -> Dict[str, Any]:
    _keys(value, ("schema", "mode", "profile", "kind", "runId",
                  "sourceManifestSha256", "wireSha256", "scope",
                  "layoutQualification", "target", "operation", "transport",
                  "selection"), "INDEX_FIELDS")
    need(value["schema"] == INDEX_SCHEMA and value["mode"] == mode
         and value["profile"] == "BASE" and value["kind"] == "AUTHORED"
         and value["runId"] == run_id and value["sourceManifestSha256"] == input_sha
         and value["wireSha256"] == WIRE and value["scope"] == SCOPE
         and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "INDEX_IDENTITY")
    _keys(value["target"], ("receiptBranchOrdinal", "lineageBranchOrdinal"),
          "INDEX_TARGET_FIELDS")
    need(_same_json(value["target"], {"receiptBranchOrdinal": 307,
                                      "lineageBranchOrdinal": 77}), "INDEX_TARGET")
    entry = _entry(value["operation"])
    selection = value["selection"]
    _keys(selection, ("calls", "selected", "callbacks", "snapshotDropped"),
          "INDEX_SELECTION_FIELDS")
    expected = ({"calls": 0, "selected": 0, "callbacks": 0, "snapshotDropped": True}
                if mode == "CONTROL" else
                {"calls": 2, "selected": 1, "callbacks": 1, "snapshotDropped": True}
                if mode == "CAPTURE" else None)
    need(expected is not None and _same_json(selection, expected), "INDEX_SELECTION")
    transport = value["transport"]
    if mode == "CONTROL":
        need(transport is None, "CONTROL_TRANSPORT")
    else:
        _keys(transport, ("path", "bytes", "sha256", "graph", "facts"),
              "INDEX_TRANSPORT_FIELDS")
        need(transport["path"] == "fixed-prefix.jsonl", "INDEX_TRANSPORT_PATH")
        _int(transport["bytes"], 1, LIMITS["aggregateBytes"], "INDEX_TRANSPORT_BYTES")
        _digest(transport["sha256"])
        for item in ("graph", "facts"):
            _keys(transport[item], ("bytes", "sha256"), "INDEX_PAYLOAD_FIELDS")
            _int(transport[item]["bytes"], 1, LIMITS["aggregateBytes"])
            _digest(transport[item]["sha256"])
    need(0 < len(raw) <= LIMITS["indexBytes"], "INDEX_SIZE")
    return entry


def _normalize(raw: bytes, reader: Any) -> List[Dict[str, Any]]:
    need(raw.endswith(b"\n") and b"\r" not in raw, "RECEIPT_ENCODING")
    lines = raw.splitlines(keepends=True)
    need(lines and all(line != b"\n" and len(line) <= LIMITS["lineBytes"] for line in lines),
         "RECEIPT_LINES")
    rows = [reader.strict_json(line[:-1]) for line in lines]
    need(rows[0].get("record") == "HEADER", "RECEIPT_HEADER")
    rows[0]["runId"] = "<NORMALIZED_RUN_ID>"
    rows[0]["sourceManifestSha256"] = "<NORMALIZED_INPUT_SHA>"
    for row in rows:
        if row.get("record") == "GROUP":
            row["elapsedBeforeOwnLineNanos"] = 0
    return rows


def _same_json(a: Any, b: Any) -> bool:
    if type(a) is not type(b):
        return False
    if type(a) is dict:
        return set(a) == set(b) and all(_same_json(a[key], b[key]) for key in a)
    if type(a) is list:
        return len(a) == len(b) and all(_same_json(x, y) for x, y in zip(a, b))
    return a == b


def _compare(candidate: bytes, reference: bytes, reader: Any, code: str) -> None:
    need(_same_json(_normalize(candidate, reader), _normalize(reference, reader)), code)


def _capture_projection(raw: bytes, reader: Any) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """Separate unchanged route semantics from measured capture scheduling.

    The unchanged operation validator must run first and checks every actual
    GROUP/accounting/budget. This projection never substitutes those checks.
    """
    rows = _normalize(raw, reader)
    projected = []
    ledger = {}
    for row in rows:
        if row["record"] == "GROUP":
            continue
        row.pop("group", None)
        if row["record"] == "RESULT":
            ledger["logicalWork"] = dict(row["logicalWork"])
            for key in ("generatorAdvances", "p07Slices", "p07OwnerSteps", "routingOwnerSteps"):
                row["logicalWork"].pop(key)
        elif row["record"] == "FOOTER":
            ledger["groupCount"] = row.pop("groupCount")
        projected.append(row)
    return projected, ledger


def _compare_capture(candidate: bytes, reference: bytes, reader: Any) -> Dict[str, Any]:
    actual, actual_ledger = _capture_projection(candidate, reader)
    control, control_ledger = _capture_projection(reference, reader)
    need(_same_json(actual, control), "CAPTURE_CONTROL_ROUTE_PARITY")
    # Preserve both complete work ledgers and bind the additional P07 work to
    # the real immutable snapshot's captureWorkUnits after decoding it.
    return {"control": control_ledger, "capture": actual_ledger,
            "p07OwnerStepsDelta": actual_ledger["logicalWork"]["p07OwnerSteps"] -
                                  control_ledger["logicalWork"]["p07OwnerSteps"]}


def _operation(raw: bytes, entry: Dict[str, Any], run_id: str, input_sha: str,
               reader: Any) -> Dict[str, Any]:
    checked = reader.validate_operation(raw, entry, input_sha, WIRE, 1, run_id, "AUTHORED")
    candidate = checked["header"]["candidate"]
    need(candidate["rootSeed"] == "9007199254740993"
         and candidate["placementSeed"] == "-6587958367974952701"
         and candidate["routingSeed"] == "8445301674744508279"
         and candidate["variant"] == "SEPARATE"
         and candidate["layoutVersion"] == 26
         and candidate["dependencyEpoch"] == "tsj-generation-dependencies-v28",
         "OPERATION_CANDIDATE")
    validate_target_selector(checked)
    need(len(checked["pads"]) == candidate["padCount"], "OPERATION_PAD_CENSUS")
    return checked


def _process(value: Any, success: bool, p2: Any) -> Dict[str, Any]:
    # Reuse P2's strict process receipt validator and its exact int64 tick rules.
    return p2.process(value, success)


def _source_audit(proc: Dict[str, Any], input_sha: str, extra_files: int) -> None:
    need(proc["Stderr"] == "", "SOURCE_AUDIT_STDERR")
    value = strict_json(proc["Stdout"].encode("utf-8"))
    need(value.get("status") == "PASS_CANONICAL_PREFIX_INPUT_CLOSURE_ONLY"
         and value.get("sourceRows") == 1084 and value.get("changedRows") == 2
         and value.get("extraFiles") == extra_files
         and value.get("pinsSha256") == input_sha, "SOURCE_AUDIT_RESULT")


def _trailer(native: Dict[str, Any], host: Dict[str, Any],
             mode: str, run_id: str, input_sha: str) -> Dict[str, Any]:
    need(native["ExitCode"] == 0 and native["Stderr"] == "", "NATIVE_STREAMS")
    stdout = native["Stdout"]
    need(stdout.endswith("\n") and "\r" not in stdout.replace("\r\n", "") and "\x00" not in stdout
         and stdout.count("\n") == 1, "TRAILER_CLOSURE")
    line = stdout[:-1]
    need(len(line.encode("utf-8")) <= LIMITS["lineBytes"], "TRAILER_LINE_SIZE")
    marker = "CANONICAL_PREFIX_CAPTURE_COMPLETE "
    need(line.startswith(marker), "TRAILER_MARKER")
    value = strict_json(line[len(marker):].encode("utf-8"))
    _keys(value, ("schema", "mode", "runId", "sourceManifestSha256", "wireSha256",
                  "scope", "layoutQualification", "index", "wrapper", "aggregateBytes"),
          "TRAILER_FIELDS")
    need(value["schema"] == "tsj-canonical-prefix-native-v1"
         and value["mode"] == mode and value["runId"] == run_id
         and value["sourceManifestSha256"] == input_sha and value["wireSha256"] == WIRE
         and value["scope"] == SCOPE and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "TRAILER_IDENTITY")
    _keys(value["index"], ("path", "bytes", "sha256"), "TRAILER_INDEX_FIELDS")
    need(value["index"]["path"] == "canonical-prefix-index.json", "TRAILER_INDEX_PATH")
    _int(value["index"]["bytes"], 1, LIMITS["indexBytes"], "TRAILER_INDEX_BYTES")
    _digest(value["index"]["sha256"])
    wrapper = value["wrapper"]
    _keys(wrapper, ("preparationNanos", "graphExportNanos", "postNanos",
                    "elapsedNanos", "workNanos", "groups"), "WRAPPER_FIELDS")
    for key in ("preparationNanos", "graphExportNanos", "postNanos"):
        _int(wrapper[key], 0, LIMITS["groupNanos"], "WRAPPER_" + key.upper())
    _int(wrapper["elapsedNanos"], 1, LIMITS["wrapperNanos"], "WRAPPER_ELAPSED")
    _int(wrapper["workNanos"], 0, LIMITS["wrapperNanos"], "WRAPPER_WORK")
    need(type(wrapper["groups"]) is int and wrapper["groups"] == 3
         and sum(wrapper[key] for key in
                 ("preparationNanos", "graphExportNanos", "postNanos")) <=
         wrapper["workNanos"] <= wrapper["elapsedNanos"],
         "WRAPPER_ACCOUNTING")
    output_bytes = len(native["Stdout"].encode("utf-8")) + len(native["Stderr"].encode("utf-8"))
    need(output_bytes <= LIMITS["receiptBytes"]
         and host.get("nativeOutputBytes") == output_bytes
         and value["aggregateBytes"] <= LIMITS["aggregateBytes"],
         "NATIVE_OUTPUT_CAP")
    need(wrapper["workNanos"] == host.get("wrapperWorkNanos")
         and wrapper["elapsedNanos"] <= host.get("nativeWallMs") * 1_000_000,
         "HOST_WRAPPER_BINDING")
    return value


def _host(run_root: Path, pins_path: Path, pins: Dict[str, Any], input_sha: str,
          mode: str, closure: Dict[str, Any], p2: Any, pinned: Dict[str, Any]) -> Dict[str, Any]:
    launch, host = _object(run_root, "host-launch.json"), _object(run_root, "host-result.json")
    need(launch.get("schema") == HOST_SCHEMA and host.get("schema") == HOST_RESULT_SCHEMA
         and launch.get("status") == "LAUNCHED_NOT_ACCEPTED"
         and host.get("status") == "HOST_COMPLETE_READER_PENDING"
         and launch.get("mode") == mode and host.get("mode") == mode
         and launch.get("inputPinsSha256") == host.get("inputPinsSha256") == input_sha,
         "HOST_CONTEXT")
    run_id = launch.get("runId")
    need(type(run_id) is str and UUID.fullmatch(run_id) is not None
         and host.get("runId") == run_id, "HOST_RUN_ID")
    for key, expected in (("compileLimitMs", 60000), ("nativeLimitMs", 480000),
                          ("cleanupLimitMs", 120000), ("outerLimitMs", 1800000)):
        need(type(launch.get(key)) is int and launch[key] == expected, "HOST_LIMIT")
    need(host.get("sourceClosure") == "PASS"
         and host.get("nativeCaptureLimitNegative") == "PASS"
         and host.get("compilerSources") == 1077 and host.get("cleanupBoundMs") == 120000
         and host.get("scratchRemoved") is True, "HOST_FINAL_PROOF")
    for key, ceiling in (("nativeWallMs", 480000), ("captureNegativeWallMs", 135000),
                         ("postSourceAndScratchCleanupMs", 120000),
                         ("outerElapsedMs", 1800000)):
        value = host.get(key)
        need(type(value) in (int, float) and math.isfinite(value) and 0 <= value <= ceiling,
             "HOST_BUDGET_" + key)
    work = _int(host.get("wrapperWorkNanos"), 0, LIMITS["wrapperNanos"])
    upper = host.get("cleanupConservativeUpperMs")
    need(type(upper) in (int, float) and math.isfinite(upper)
         and 0 <= upper <= 120000
         and abs(upper - (host["nativeWallMs"] - work / 1e6 +
                          host["postSourceAndScratchCleanupMs"])) <= 0.001,
         "HOST_CLEANUP_ACCOUNTING")
    for prefix in ("compiler", "native"):
        need(host.get(prefix + "TerminationProven") is True
             and type(host.get(prefix + "ExitCode")) is int
             and type(host.get(prefix + "ProcessId")) is int
             and type(host.get(prefix + "ProcessStartTicks")) in (int, str),
             "HOST_PROCESS_PROOF")
    need(host.get("compilerExitCode") == 0
         and (host.get("nativeExitCode") == 0 if mode != "MISSING_TARGET"
              else type(host.get("nativeExitCode")) is int
              and host["nativeExitCode"] != 0), "HOST_EXIT_CODES")
    scratch = Path(launch.get("scratch", ""))
    output_root = Path(launch.get("outputRoot", ""))
    need(scratch.is_absolute() and _below(scratch, closure["temp"])
         and not scratch.exists()
         and output_root.is_absolute() and str(output_root) == os.path.abspath(output_root)
         and _below(output_root, closure["temp"])
         and not _below(output_root, closure["repo"]), "HOST_PATHS")
    script = Path(launch.get("script", ""))
    script_pin = pinned.get(os.path.normcase(str(script)))
    need(script.is_absolute() and script_pin is not None
         and launch.get("scriptSha256") == script_pin["sha256"], "HOST_SCRIPT_PIN")

    audit_args = ["-B", str(pins["preflight"]), str(closure["repo"]),
                  str(closure["baseline"]), BASE_MANIFEST_SHA,
                  str(Path(os.path.abspath(pins_path))), input_sha]
    for name in ("preflight-process.json", "pre-native-process.json",
                 "post-source-process.json"):
        proc = _process(_object(run_root, name), True, p2)
        args = proc["Arguments"]
        need(proc["Stderr"] == "" and len(args) == 7 and args[0] == "-B"
             and args[4] == BASE_MANIFEST_SHA and args[6] == input_sha
             and _same(args[1], audit_args[1]) and _same(args[2], audit_args[2])
             and _same(args[3], audit_args[3]) and _same(args[5], audit_args[5]),
             "SOURCE_AUDIT_ARGS")
        need(_sha(p2.stable_read(proc["FilePath"], 32 * 1024 * 1024)) == PYTHON_SHA,
             "SOURCE_AUDIT_PYTHON")
        _source_audit(proc, input_sha, closure["extraFiles"])

    ci = _object(run_root, "compiler-launch-intent.json")
    compiler = _process(_object(run_root, "compiler-process.json"), True, p2)
    need(ci.get("status") == "INTENT_NOT_START_PROOF" and ci.get("limitMs") == 60000
         and ci.get("sha256") == "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
         and compiler["Arguments"] == ci.get("arguments")
         and _same(compiler["FilePath"], ci.get("executable"))
         and compiler["ProcessId"] == host["compilerProcessId"]
         and str(compiler["ProcessStartTicks"]) == str(host["compilerProcessStartTicks"]),
         "COMPILER_BINDING")
    sources = stable_read(_child(run_root, "compiler-sources.txt"), 2 * 1024 * 1024)
    need(_sha(sources) == ci.get("sourceListSha256") and sources.endswith(b"\n")
         and b"\x00" not in sources, "COMPILER_SOURCE_HASH")
    source_lines = sources.decode("utf-8", "strict").splitlines()
    need(len(source_lines) == 1077 and len(set(source_lines)) == 1077
         and all(line.startswith('"') and line.endswith('"')
                 and '"' not in line[1:-1] for line in source_lines),
         "COMPILER_SOURCE_CENSUS")
    classes = scratch / "classes"
    source_list = scratch / "sources.txt"
    cp = ";".join((str(classes), str(closure["repo"] / ".tools/gwt-2.7.0/gwt-user-2.7.0.jar"),
                   str(closure["repo"] / ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar")))
    need(_same(ci.get("sourceListPath", ""), source_list)
         and ci["arguments"] == ["-source", "7", "-target", "7", "-encoding", "UTF-8",
              "-classpath", cp, "-sourcepath", str(scratch / "empty-sourcepath"),
              "-d", str(classes), "@" + str(source_list)], "COMPILER_EXACT_ARGUMENTS")
    need(_sha(p2.stable_read(ci["executable"], 32 * 1024 * 1024)) == ci["sha256"],
         "JAVAC_BINARY_HASH")
    excluded = {"src/com/lushprojects/circuitjs1/client/" + name for name in
                ("CirSim.java", "ChipElm.java", "PhysicalSpecificationDeveloperVerifier.java",
                 "PcbLayerRoutingPrototype.java")}
    expected_sources = [str(closure["repo"] / row["path"]).replace("\\", "/")
                        for row in pins["sourceRows"] if row["role"] == "BASE_SOURCE"
                        and row["path"].endswith(".java") and row["path"] not in excluded]
    expected_sources.extend(str(Path(pins["modeFiles"][role])).replace("\\", "/")
                            for role in ("router", "support", "writer", "adapter"))
    bridge_doc = strict_json(stable_read(pins["bridgeManifest"], 65536))
    need(_sha(stable_read(pins["bridgeManifest"], 65536)) ==
         "e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b",
         "COPIED_BRIDGE_MANIFEST")
    bridge_copies = _object(run_root, "consumed-bridge-copies.json")
    need(type(bridge_copies) is list and len(bridge_copies) == len(bridge_doc) == 3,
         "COPIED_BRIDGE_CENSUS")
    for actual, wanted in zip(bridge_copies, bridge_doc):
        _keys(actual, ("path", "bytes", "sha256"), "COPIED_BRIDGE_FIELDS")
        need(_same(actual["path"], scratch / wanted["path"])
             and type(actual["bytes"]) is int and actual["bytes"] == wanted["bytes"]
             and actual["sha256"] == wanted["sha256"], "COPIED_BRIDGE_BINDING")
        expected_sources.append(str(Path(actual["path"])).replace("\\", "/"))
    need([line[1:-1] for line in source_lines] == expected_sources
         and len(set(expected_sources)) == 1077, "COMPILER_EXACT_SOURCE_BYTES")

    native_intent = _object(run_root, "native-launch-intent.json")
    negative_intent = _object(run_root, "native-capture-negative-intent.json")
    native = _process(_object(run_root, "native-process.json"),
                      mode != "MISSING_TARGET", p2)
    negative = _process(_object(run_root, "native-capture-limit-negative.json"), False, p2)
    need(type(host.get("nativeOutputBytes")) is int and host["nativeOutputBytes"] ==
         len(native["Stdout"].encode("utf-8")) + len(native["Stderr"].encode("utf-8"))
         and host["nativeOutputBytes"] <= LIMITS["receiptBytes"], "NATIVE_CAPTURE_BYTES")
    adapter = Path(pins["modeFiles"]["adapter"])
    adapter_bytes = stable_read(adapter, 4 * 1024 * 1024).decode("utf-8", "strict")
    package_match = re.search(r"(?m)^\s*package\s+([A-Za-z0-9_.]+)\s*;", adapter_bytes)
    class_match = re.search(r"(?m)^\s*(?:public\s+)?(?:final\s+)?class\s+([A-Za-z_$][A-Za-z0-9_$]*)",
                            adapter_bytes)
    need(package_match is not None and class_match is not None
         and Path(adapter).stem == class_match.group(1), "ADAPTER_CLASS")
    main_class = package_match.group(1) + "." + class_match.group(1)
    need(main_class == "com.lushprojects.circuitjs1.client.CanonicalPrefixCaptureStudy",
         "NATIVE_MAIN_CLASS")
    args = native_intent.get("arguments")
    need(native_intent.get("status") == "INTENT_NOT_START_PROOF"
         and native_intent.get("sha256") == "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
         and native_intent.get("limitMs") == 480000
         and native["Arguments"] == args and type(args) is list
         and len(args) == (5 if mode == "CANARY" else 9)
         and args[:2] == ["-ea", "-cp"] and args[3] == main_class
         and args[2] == cp
         and _same(native["FilePath"], native_intent.get("executable"))
         and native["ProcessId"] == host["nativeProcessId"]
         and str(native["ProcessStartTicks"]) == str(host["nativeProcessStartTicks"]),
         "NATIVE_BINDING")
    if mode == "CANARY":
        need(args[-1] == "--canary", "CANARY_ARGS")
    else:
        need(args[4] == mode and args[5] == run_id and args[6] == input_sha
             and args[7] == WIRE and _same(args[8], output_root / "data"),
             "NATIVE_ARGS")
    need(negative_intent.get("status") == "INTENT_NOT_START_PROOF"
         and negative_intent.get("limitMs") == 15000
         and negative_intent.get("sha256") == native_intent.get("sha256")
         and negative["Arguments"] == negative_intent.get("arguments")
         and negative["Arguments"] == ["-ea", "-cp", args[2], main_class, "--capture-overflow"]
         and _same(negative["FilePath"], native_intent.get("executable"))
         and negative["Stdout"] == ""
         and "CANONICAL_PREFIX_CAPTURE_LIMIT" in negative["Stderr"]
         and len(negative["Stderr"].encode("utf-8")) <= LIMITS["receiptBytes"],
         "CAPTURE_LIMIT_NEGATIVE")
    need(_sha(p2.stable_read(native_intent["executable"], 32 * 1024 * 1024)) ==
         native_intent.get("sha256"), "JAVA_BINARY_HASH")
    return {"runId": run_id, "launch": launch, "host": host, "native": native,
            "workNanos": work, "cleanupUpperMs": upper, "outputRoot": output_root}


def _canary(native: Dict[str, Any], work: int) -> Dict[str, Any]:
    need(native["ExitCode"] == 0 and native["Stderr"] == "", "CANARY_EXIT")
    stdout = native["Stdout"]
    need(stdout.endswith("\n") and "\r" not in stdout.replace("\r\n", "") and "\x00" not in stdout,
         "CANARY_STDOUT")
    lines = stdout.replace("\r\n", "\n")[:-1].split("\n")
    need(len(lines) >= 2 and all(line.startswith("PASS: ") for line in lines[:-1]),
         "CANARY_FIXTURE_PASS")
    marker = "CANONICAL_PREFIX_CANARY_COMPLETE "
    need(lines[-1].startswith(marker), "CANARY_MARKER")
    value = strict_json(lines[-1][len(marker):].encode("utf-8"))
    _keys(value, ("schema", "status", "assertions", "elapsedNanos", "groups",
                  "maximumGroupNanos", "layoutQualification"), "CANARY_FIELDS")
    need(value["schema"] == "tsj-canonical-prefix-canary-v1"
         and value["status"] == "PASS" and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "CANARY_IDENTITY")
    _int(value["assertions"], 1, 100000, "CANARY_ASSERTIONS")
    _int(value["elapsedNanos"], 1, LIMITS["wrapperNanos"], "CANARY_ELAPSED")
    _int(value["groups"], 0, LIMITS["groups"], "CANARY_GROUPS")
    _int(value["maximumGroupNanos"], 0, LIMITS["groupNanos"], "CANARY_GROUP_MAX")
    need(value["elapsedNanos"] <= work, "CANARY_WORK_BINDING")
    return {"assertions": value["assertions"], "groups": value["groups"],
            "maximumGroupNanos": value["maximumGroupNanos"]}


def _missing(native: Dict[str, Any], run_id: str, input_sha: str) -> Dict[str, Any]:
    need(native["ExitCode"] != 0 and native["Stdout"] == "", "MISSING_EXIT_OR_STDOUT")
    stderr = native["Stderr"]
    need(type(stderr) is str and stderr.endswith("\n") and "\r" not in stderr
         and len(stderr.encode("utf-8")) <= LIMITS["receiptBytes"], "MISSING_STDERR")
    lines = stderr.splitlines()
    marker = "CANONICAL_PREFIX_MISSING_TARGET "
    need(lines and lines[0].startswith(marker)
         and sum(line.startswith(marker) for line in lines) == 1, "MISSING_MARKER")
    value = strict_json(lines[0][len(marker):].encode("utf-8"))
    _keys(value, ("schema", "mode", "runId", "sourceManifestSha256", "wireSha256",
                  "operation", "selection", "workNanos", "scope",
                  "layoutQualification"), "MISSING_FIELDS")
    need(value["schema"] == "tsj-canonical-prefix-missing-target-v1"
         and value["mode"] == "MISSING_TARGET" and value["runId"] == run_id
         and value["sourceManifestSha256"] == input_sha and value["wireSha256"] == WIRE
         and value["scope"] == SCOPE and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "MISSING_IDENTITY")
    entry = _entry(value["operation"])
    selection = value["selection"]
    _keys(selection, ("calls", "selected", "callbacks", "snapshotDropped"),
          "MISSING_SELECTION_FIELDS")
    need(_same_json(selection, {"calls": 2, "selected": 0, "callbacks": 0,
                                  "snapshotDropped": True}), "MISSING_SELECTION")
    work = _int(value["workNanos"], 1, LIMITS["wrapperNanos"], "MISSING_WORK")
    need(len(lines) <= 2049, "MISSING_STACK_BOUND")
    return {"entry": entry, "selection": selection, "workNanos": work,
            "trailingStackLines": len(lines) - 1}


def _load_operation(run_root: Path, host_info: Dict[str, Any], mode: str,
                    input_sha: str, reader: Any) -> Dict[str, Any]:
    data = _child(run_root, "data")
    index_path = _child(data, "canonical-prefix-index.json")
    index_raw = stable_read(index_path, LIMITS["indexBytes"])
    index = strict_json(index_raw)
    entry = _index(index_raw, index, mode, host_info["runId"], input_sha)
    receipt_path = _child(data, entry["receipt"]["path"])
    receipt = stable_read(receipt_path, LIMITS["receiptBytes"])
    need(len(receipt) == entry["receipt"]["bytes"]
         and _sha(receipt) == entry["receipt"]["sha256"], "RECEIPT_HASH")
    operation = _operation(receipt, entry, host_info["runId"], input_sha, reader)
    trailer = host_info["trailer"]
    _keys(trailer["index"], ("path", "bytes", "sha256"), "TRAILER_INDEX_FIELDS")
    need(trailer["index"]["path"] == "canonical-prefix-index.json"
         and trailer["index"]["bytes"] == len(index_raw)
         and trailer["index"]["sha256"] == _sha(index_raw), "INDEX_TRAILER_BINDING")
    wrapper = trailer["wrapper"]
    need(wrapper["workNanos"] == host_info["workNanos"]
         and wrapper["elapsedNanos"] - wrapper["workNanos"] ==
         entry["cleanup"]["elapsedNanos"], "WRAPPER_CLEANUP_BINDING")
    groups = operation["groups"]
    need(len(groups) + 3 <= LIMITS["groups"]
         and max(row["elapsedBeforeOwnLineNanos"] for row in groups) <=
         LIMITS["groupNanos"]
         and sum(row["elapsedBeforeOwnLineNanos"] for row in groups) <=
         entry["elapsedToCloseNanos"]
         and max(row["elapsedBeforeOwnLineNanos"] for row in groups) <=
         entry["maxFullGroupNanos"], "OPERATION_GROUP_BUDGET")
    transport_raw = b""
    if mode == "CONTROL":
        need(wrapper["graphExportNanos"] == 0, "CONTROL_GRAPH_EXPORT")
    else:
        need(wrapper["graphExportNanos"] > 0, "CAPTURE_GRAPH_EXPORT")
        desc = index["transport"]
        transport_path = _child(data, desc["path"])
        transport_raw = stable_read(transport_path, LIMITS["aggregateBytes"])
        need(len(transport_raw) == desc["bytes"] and _sha(transport_raw) == desc["sha256"],
             "TRANSPORT_HASH")
    aggregate = len(index_raw) + len(receipt) + len(transport_raw)
    need(aggregate <= LIMITS["aggregateBytes"]
         and trailer["aggregateBytes"] == aggregate, "OPERATION_AGGREGATE_CAP")
    if mode == "CONTROL":
        need(not (data / "fixed-prefix.jsonl").exists(), "CONTROL_SIDECAR")
    return {"index": index, "indexRaw": index_raw, "receipt": receipt,
            "receiptPath": receipt_path, "operation": operation,
            "transportRaw": transport_raw, "wrapper": wrapper,
            "entry": entry, "aggregateBytes": aggregate}


def _validate_facts(decoded: Dict[str, Any], operation: Dict[str, Any],
                    run_id: str, input_sha: str) -> Dict[str, Any]:
    facts = decoded["facts"]
    _keys(facts, FACT_KEYS, "FACTS_FIELDS")
    need(facts["schema"] == FACTS_SCHEMA and facts["runId"] == run_id
         and facts["sourceManifestSha256"] == input_sha and facts["wireSha256"] == WIRE
         and facts["caseId"] == CASE_ID, "FACTS_IDENTITY")
    need(facts["viaMaskSemantics"] == "RELAXED_GEOMETRY_OMITS_REMAINING_HOLE_CAP"
         and facts["maximumHoles"] == 48 and facts["remainingHoles"] == 0
         and facts["maximumBranchTransitions"] == 4
         and facts["remainingBranchTransitionsFromPrefix"] == 0,
         "FACTS_CAPACITY")
    for key, expected in (("rootSeed", "9007199254740993"),
                          ("placementSeed", "-6587958367974952701"),
                          ("routingSeed", "8445301674744508279")):
        need(type(facts[key]) is str and SEED.fullmatch(facts[key]) is not None
             and facts[key] == expected, "FACTS_SEED")
    candidate, context = operation["header"]["candidate"], operation["result"]["context"]
    need(facts["variant"] == "SEPARATE" and facts["layoutVersion"] == 26
         and facts["dependencyEpoch"] == "tsj-generation-dependencies-v28"
         and facts["componentCount"] == candidate["componentCount"]
         and facts["padCount"] == candidate["padCount"]
         and facts["netCount"] == candidate["netCount"]
         and facts["routeRequestIndex"] == context["requestIndex"] == 0
         and facts["selectedPlacementIndex"] == 0
         and facts["selectedPlacementAttempt"] == context["selectedAttempt"] == 4
         and _same_json(facts["eligiblePlacementAttempts"], context["eligibleAttempts"])
         and _same_json(facts["eligiblePlacementAttempts"], [4, 2, 3, 0, 1])
         and type(facts["layoutVersion"]) is int, "FACTS_CONTEXT")
    target = validate_target_selector(operation)
    branch, snap = target["branch"], target["snapshot"]
    need(facts["receiptBranchOrdinal"] == 307 and facts["lineageBranchOrdinal"] == 77
         and facts["pass"] == 2 and facts["netId"] == snap["netId"]
         and facts["startPadId"] == snap["startPadId"]
         and facts["rootPadId"] == snap["rootPadId"]
         and facts["startX"] == snap["startX"] and facts["startY"] == snap["startY"]
         and facts["rootX"] == snap["rootX"] and facts["rootY"] == snap["rootY"]
         and facts["branchExpanded"] == snap["branchExpanded"]
         and facts["totalRouteExpansions"] == snap["totalExpanded"]
         and facts["terminalReason"] == snap["terminalReason"]
         and facts["prefixViaCount"] == snap["prefixViaCount"] == 48
         and facts["prefixHoleCount"] == 48, "FACTS_LINEAGE")
    raw_context = operation["result"]["context"]
    need(facts["rawPlacementSha256"] == raw_context["pristineSha256"]
         and facts["rawPlacementFingerprintUtf8Bytes"] == raw_context["pristineBytes"]
         and facts["prefixGeometrySha256"] == branch["prefixSha256"]
         and facts["prefixGeometryFingerprintUtf8Bytes"] == branch["prefixBytes"],
         "FACTS_FINGERPRINT_SOURCE_BINDING")
    for text_key, bytes_key, hash_key in (
        ("rawPlacementFingerprint", "rawPlacementFingerprintUtf8Bytes", "rawPlacementSha256"),
        ("prefixGeometryFingerprint", "prefixGeometryFingerprintUtf8Bytes", "prefixGeometrySha256"),
    ):
        text = facts[text_key]
        _digest(facts[hash_key])
        need(type(text) is str and len(text.encode("utf-8")) == facts[bytes_key]
             and _sha(text.encode("utf-8")) == facts[hash_key],
             "FACTS_FINGERPRINT")
    holes = facts["prefixHoleCountsByKind"]
    _keys(holes, ("VIA", "PLATED", "NON_PLATED"), "FACTS_HOLE_KINDS")
    need(all(type(x) is int and x >= 0 for x in holes.values())
         and sum(holes.values()) == facts["prefixHoleCount"]
         and holes["VIA"] == facts["prefixViaCount"], "FACTS_HOLE_COUNTS")
    pads = {row["padId"]: row for row in operation["pads"]}
    reached, pth = facts["reachedPadIds"], facts["reachedPthPadIds"]
    for items in (reached, pth):
        need(type(items) is list and len(items) == len(set(items))
             and all(type(item) is str and item in pads for item in items),
             "FACTS_REACHED_PADS")
    need(set(pth).issubset(reached) and facts["startPadId"] in pads
         and facts["rootPadId"] in pads, "FACTS_REACHED_PTH")
    graph = decoded["graph"]
    cells = graph.width * graph.height
    numeric_caps = {
        "componentCount": 256, "padCount": 1024, "netCount": 1023,
        "routeRequestIndex": 0, "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 5, "receiptBranchOrdinal": 1000000,
        "lineageBranchOrdinal": 1000000, "pass": 2, "startX": 1000000,
        "startY": 1000000, "rootX": 1000000, "rootY": 1000000,
        "branchExpanded": 100000, "totalRouteExpansions": 1000000,
        "prefixHoleCount": 48, "prefixViaCount": 48,
        "rawPlacementFingerprintUtf8Bytes": 4194304,
        "prefixGeometryFingerprintUtf8Bytes": 4194304,
        "gridWidth": 500, "gridHeight": 500, "gridPitch": 1000,
        "primaryLayer": 1,
        "startCell": cells - 1, "initialFacesMask": 3, "cellCount": 250000,
        "captureWorkUnits": 10000000, "copyEntries": 10000000,
        "goalProbes": graph.state_count, "ordinaryEdgeProbes": graph.state_count * 4,
        "viaProbes": cells, "comparisonEntries": 10000000,
        "captureActiveElapsedMillis": 90000, "maximumHoles": 48,
        "remainingHoles": 48, "maximumBranchTransitions": 4,
        "remainingBranchTransitionsFromPrefix": 4,
    }
    for key, cap in numeric_caps.items():
        _int(facts[key], 0, cap, "FACTS_NUMBER_" + key)
    _int(facts["originX"], -1000000, 1000000, "FACTS_ORIGIN_X")
    _int(facts["originY"], -1000000, 1000000, "FACTS_ORIGIN_Y")
    need(facts["gridWidth"] == graph.width and facts["gridHeight"] == graph.height
         and facts["cellCount"] == cells and facts["gridPitch"] == 10
         and facts["primaryLayer"] == graph.primary
         and facts["initialFacesMask"] in (1, 2, 3)
         and all(state % cells == facts["startCell"] for state in graph.starts)
         and sum(1 << (state // cells) for state in graph.starts) ==
         facts["initialFacesMask"]
         and -1000000 <= facts["originX"] <= 1000000
         and -1000000 <= facts["originY"] <= 1000000,
         "FACTS_GRAPH_GEOMETRY")
    return facts


def _capture_transport(raw: bytes, index: Dict[str, Any],
                       operation: Dict[str, Any], run_id: str,
                       input_sha: str) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    decoded = decode_transport(raw, True)
    header = decoded["header"]
    need(header["runId"] == run_id and header["sourceManifestSha256"] == input_sha,
         "TRANSPORT_CONTEXT")
    desc = index["transport"]
    need(len(decoded["graphBytes"]) == desc["graph"]["bytes"]
         and _sha(decoded["graphBytes"]) == desc["graph"]["sha256"]
         and len(decoded["factsBytes"]) == desc["facts"]["bytes"]
         and _sha(decoded["factsBytes"]) == desc["facts"]["sha256"],
         "INDEX_PAYLOAD_BINDING")
    need(decoded["graph"].case_id == CASE_ID, "GRAPH_CASE_ID")
    facts = _validate_facts(decoded, operation, run_id, input_sha)
    diagnosis = zero_via_result(decoded["graph"])
    return facts, diagnosis


def _recheck(pinned: Dict[str, Dict[str, Any]], pins_path: Path,
             pins_raw: bytes) -> None:
    need(stable_read(pins_path, 1024 * 1024) == pins_raw, "PINS_CHANGED")
    for row in pinned.values():
        raw = stable_read(row["path"], 16 * 1024 * 1024, allow_empty=True)
        need(len(raw) == row["bytes"] and _sha(raw) == row["sha256"],
             "INPUT_CHANGED_DURING_READ")


def validate_run(run_root: Any, input_pins: Any, input_sha: str,
                 mode: str) -> Dict[str, Any]:
    """Validate one run and report a scoped diagnostic; never a route PASS."""
    need(type(input_sha) is str and HEX.fullmatch(input_sha) is not None, "INPUT_SHA")
    need(mode in ("CANARY", "CONTROL", "CAPTURE", "MISSING_TARGET"), "MODE")
    run_root, pins_path = no_reparse(run_root), no_reparse(input_pins)
    pins_raw = stable_read(pins_path, 1024 * 1024)
    need(_sha(pins_raw) == input_sha, "PINS_HASH")
    pins = strict_json(pins_raw)
    need(type(pins) is dict and pins.get("schema") == INPUT_SCHEMA, "PINS_SCHEMA")
    pinned = _pin_map(pins)
    p2 = _p2_module(pinned)
    # Also apply P2's parser to the pins bytes so its strict transport/source helper
    # contract stays in use alongside the P4 schema checks.
    p2.parse(pins_raw)
    closure = _source_closure(pins, pinned)
    need(_below(run_root, closure["temp"]) and not _below(run_root, closure["repo"]),
         "RUN_ROOT_CONFINEMENT")
    info = _host(run_root, pins_path, pins, input_sha, mode, closure, p2, pinned)
    if mode == "CANARY":
        canary = _canary(info["native"], info["workNanos"])
        result = {
            "schema": READER_SCHEMA, "status": "PASS_CANARY_ONLY", "mode": mode,
            "runId": info["runId"], "inputPinsSha256": input_sha,
            "scope": MODE_SCOPE[mode], "layoutQualification": False,
            "comparison": "NOT_APPLICABLE", "graphDiagnosis": None,
            "cleanupUpperMs": info["cleanupUpperMs"], "canary": canary,
        }
        _recheck(pinned, pins_path, pins_raw)
        return result

    branch_path = closure["repo"] / "tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py"
    source = stable_read(branch_path, 2 * 1024 * 1024)
    need(_sha(source) == OLD_BRANCH_READER_SHA, "OLD_BRANCH_READER_HASH")
    spec = importlib.util.spec_from_file_location("p4_pinned_branch_order_reader", branch_path)
    need(spec is not None and spec.loader is not None, "OLD_BRANCH_READER_IMPORT")
    branch_reader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(branch_reader)

    if mode == "MISSING_TARGET":
        marker = _missing(info["native"], info["runId"], input_sha)
        need(marker["workNanos"] == info["workNanos"], "MISSING_WORK_BINDING")
        data = _child(run_root, "data")
        index_path, transport_path = data / "canonical-prefix-index.json", data / "fixed-prefix.jsonl"
        need(not index_path.exists() and not transport_path.exists(), "MISSING_ARTIFACTS")
        receipt_path = _child(data, marker["entry"]["receipt"]["path"])
        raw = stable_read(receipt_path, LIMITS["receiptBytes"])
        need(len(raw) == marker["entry"]["receipt"]["bytes"]
             and _sha(raw) == marker["entry"]["receipt"]["sha256"], "MISSING_RECEIPT_HASH")
        operation = _operation(raw, marker["entry"], info["runId"], input_sha,
                               branch_reader)
        control_root = closure["controlRunRoot"]
        # CONTROL is validated as an independent source-bound run before it is used.
        validate_run(control_root, pins_path, input_sha, "CONTROL")
        control_raw = stable_read(_child(control_root / "data", "op-1.jsonl"),
                                  LIMITS["receiptBytes"])
        _compare(raw, control_raw, branch_reader, "MISSING_CONTROL_PARITY")
        result = {
            "schema": READER_SCHEMA, "status": "PASS_EXPECTED_MISSING_TARGET",
            "mode": mode, "runId": info["runId"], "inputPinsSha256": input_sha,
            "scope": MODE_SCOPE[mode], "layoutQualification": False,
            "comparison": "PASS_CONTROL_OPERATION_PARITY", "graphDiagnosis": None,
            "cleanupUpperMs": info["cleanupUpperMs"],
            "operation": {"receiptBytes": len(raw),
                          "groupCount": len(operation["groups"]),
                          "selection": marker["selection"]},
        }
        _recheck(pinned, pins_path, pins_raw)
        return result

    # The native success trailer binds the output index and wrapper accounting.
    trailer = _trailer(info["native"], info["host"], mode, info["runId"], input_sha)
    info["trailer"] = trailer
    loaded = _load_operation(run_root, info, mode, input_sha, branch_reader)
    control_root = closure["controlRunRoot"]
    if mode == "CONTROL":
        need(_same(run_root, control_root), "CONTROL_RUN_ROOT")
        candidate = loaded["receipt"]
        parent = stable_read(closure["parentControlReceipt"], LIMITS["receiptBytes"])
        need(_sha(parent) == OLD_CONTROL_RECEIPT_SHA, "PARENT_CONTROL_HASH")
        # P2 owns the normalized BASE-authored operation comparison.
        p2.compare_base(candidate, parent, branch_reader)
        comparison = "PASS_PARENT_P2_BASE_AUTHORED_OP1"
        diagnosis = None
    else:
        need(not _same(run_root, control_root), "CAPTURE_RUN_ROOT")
        validate_run(control_root, pins_path, input_sha, "CONTROL")
        control_receipt = stable_read(_child(control_root / "data", "op-1.jsonl"),
                                      LIMITS["receiptBytes"])
        accounting = _compare_capture(loaded["receipt"], control_receipt, branch_reader)
        facts, diagnosis = _capture_transport(loaded["transportRaw"], loaded["index"],
                                              loaded["operation"], info["runId"], input_sha)
        need(accounting["p07OwnerStepsDelta"] == facts["captureWorkUnits"],
             "CAPTURE_WORK_UNIT_BINDING")
        comparison = "PASS_CONTROL_ROUTE_AND_BRANCH_PARITY"
    status = "PASS_CONTROL_ONLY" if mode == "CONTROL" else \
             "PASS_CAPTURE_DIAGNOSIS_ONLY"
    result = {
        "schema": READER_SCHEMA, "status": status, "mode": mode,
        "runId": info["runId"], "inputPinsSha256": input_sha,
        "scope": MODE_SCOPE[mode], "layoutQualification": False,
        "comparison": comparison, "graphDiagnosis": diagnosis,
        "captureAccounting": accounting if mode == "CAPTURE" else None,
        "cleanupUpperMs": info["cleanupUpperMs"],
        "operation": {"receiptBytes": len(loaded["receipt"]),
                      "groupCount": len(loaded["operation"]["groups"]),
                      "receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77},
    }
    _recheck(pinned, pins_path, pins_raw)
    return result


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 4:
        print(json.dumps({"schema": READER_SCHEMA, "status": "REJECTED",
                          "error": "ARGUMENT_COUNT_INVALID",
                          "acceptance": {"route": False, "layout": False,
                                         "electrical": False, "q100": False}},
                         sort_keys=True, separators=(",", ":")))
        return 2
    try:
        result = validate_run(args[0], args[1], args[2], args[3])
    except Rejected as error:
        print(json.dumps({"schema": READER_SCHEMA, "status": "REJECTED",
                          "error": str(error),
                          "acceptance": {"route": False, "layout": False,
                                         "electrical": False, "q100": False}},
                         sort_keys=True, separators=(",", ":")))
        return 2
    except (OSError, ValueError, TypeError, KeyError, IndexError):
        print(json.dumps({"schema": READER_SCHEMA, "status": "REJECTED",
                          "error": "VALIDATION_FAILED",
                          "acceptance": {"route": False, "layout": False,
                                         "electrical": False, "q100": False}},
                         sort_keys=True, separators=(",", ":")))
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
