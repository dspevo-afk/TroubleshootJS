"""Independent reader for the bounded canonical prefix predicate-stage study.

The immutable P5 reader is used only for its low-level operation, graph,
attribution, geometry, partial-receipt, process, and path oracles.  This module
owns the P6 input closure, native envelope, stage payload, and cross-mode
comparisons.
"""
from __future__ import annotations

import collections
import difflib
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
from typing import Any, Dict, List, Optional, Sequence, Tuple

sys.dont_write_bytecode = True

INPUT_SCHEMA = "tsj-canonical-prefix-stage-inputs-v1"
HOST_SCHEMA = "tsj-canonical-prefix-stage-host-v1"
HOST_RESULT_SCHEMA = "tsj-canonical-prefix-stage-host-result-v1"
INDEX_SCHEMA = "tsj-canonical-prefix-stage-index-v1"
NATIVE_SCHEMA = "tsj-canonical-prefix-stage-native-v1"
EXCEPTION_SCHEMA = "tsj-canonical-prefix-stage-exception-v1"
STAGES_SCHEMA = "tsj-canonical-prefix-stage-observations-v1"
CANARY_SCHEMA = "tsj-canonical-prefix-stage-canary-v1"
READER_SCHEMA = "tsj-canonical-prefix-stage-reader-result-v1"
SCOPE = "CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY"
P5_INDEX_SCOPE = "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY"
WIRE_SHA = "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb"
GRAPH_SHA = "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a"
PREFIX_SHA = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
RAW_PLACEMENT_SHA = "6553ad3df059c6bfe2980efe39ff951d3f6322fc0694b2c96e26508f8d3dfe2e"
PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
BASELINE_SHA = "f94eca24bac4b24cc264fe4ca5038c68cbbf54b8530bf529ed3ec66e48ecf019"
PLAN_SHA = "31547c7531d5c950d305f2010bf3cabfbe56fd709498953298d8d31e8bcab68f"
PROTOCOL_SHA = "4b795d7784afa14eaf95000f48ae4144ee57621c14773026acb27b6f98f697ec"
ADDENDUM_SHA = "3da49dcbc8dbb1950e5a875a5bf04c00b0829d60e61f2ef7e294a4fc4295abc2"
ADDENDUM_R1_SHA = "88a510191f4c81413971f398257aaf0ab08ba6031f26a66206fd4e1a7eb424bb"
ZERO_DELTA_SHA = "089167366a28d11f4342e07af94db109f3bb8655afd968ad5262c2c75b3a3b6f"
FULL_CUT_SHA = "1a1d7739d3a8ce4409896b56477c68f38b12b8d77f0d0264e16b0b3cf1bf70c5"
P5_INPUT_SHA = "1e570d555c919755d82e5e0f0c728425c7aa313c3bd2c123ebfd874832a7b636"
P5_QUALIFICATION_SHA = "301fca39736eca2b21c40b8dc92ca01e0c58b45493b103d3a8d3bee53624922e"
P5_READER_SHA = "c20bfc3daaa7fb8f7c428b47f29288cc6889c814f488c7caf1128023c214032b"
P5_CAPTURE_WORK_DELTA = 611
P5_ATTRIBUTION_SHA = "77273a2a36c6a088e212594c061e0e886b498cdfd2689caa396464d423ffdb4c"
BRIDGE_MANIFEST_SHA = "e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b"
P5_CAPTURE_GRAPH_BYTES = 3324982
P5_CAPTURE_TRANSPORT_BYTES = 5138203
P5_CAPTURE_TRANSPORT_SHA = "75efe4303fa987f8ea499fa0e3c44c7743866ad213e158f2c5b58a38ccaf50dc"
P5_ROUTER_OVERLAY_BYTES = 170500
P5_ROUTER_OVERLAY_SHA = "70cacaacc97cb3f01a1ad6221753e4d80a05479ba6663cd5dc6474a00dc4fb21"
MAIN_CLASS = "com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy"
WRITER_RELATIVE = "tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java"
ROUTER_RELATIVE = "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java"
MODE_FILE_ORDER = ("router", "support", "writer", "adapter", "blocker", "queries",
                   "netRouterOverlay", "stageInspector")
EXCLUDED_JAVA = {
    "src/com/lushprojects/circuitjs1/client/CirSim.java",
    "src/com/lushprojects/circuitjs1/client/ChipElm.java",
    "src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java",
    "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
    ROUTER_RELATIVE,
}
PIN_FIELDS = {
    "schema", "repositoryRoot", "historicalRoot", "parent", "baselineManifestPath",
    "baselineManifestSha256", "sourceRows", "changedRows", "modeFiles", "bridgeRoot",
    "bridgeManifest", "preflight", "nullRunRoot", "p5Root", "p5InputPinsPath",
    "p5InputPinsSha256", "p5QualificationPath", "p5QualificationSha256", "p5ReaderPath",
    "p5CaptureRunRoot", "p5ControlReceipt", "planPath", "planSha256", "protocolPath",
    "protocolSha256", "addendumPath", "addendumSha256", "zeroDeltaInputPath",
    "zeroDeltaInputSha256", "fullCutInputPath", "fullCutInputSha256", "files",
}
LIMITS = {
    "compileMs": 60000, "nativeMs": 480000, "negativeMs": 15000,
    "negativeWallMs": 135000, "cleanupMs": 120000, "outerMs": 1800000,
    "workNanos": 90000000000, "unitNanos": 5000000000, "groups": 640,
    "artifactBytes": 8388608, "lineBytes": 65536, "indexBytes": 65536,
    "stageBytes": 65536, "sourceBytes": 536870912, "sourceCount": 1084,
    "compileSources": 1080, "cloneEntries": 1048576, "unitEntries": 4096,
    "observationUnits": 32768, "queries": 22,
}
SELECTED_ORDINALS = frozenset((0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 15, 17, 20))
STAGE_NAMES = ("LEGAL_MOVE_REJECTED", "TRAVERSE_REJECTED",
               "OCCUPANCY_REJECTED", "ALLOWED")
HEX = re.compile(r"[0-9a-f]{64}\Z")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
RECORD_FIELDS = (
    "FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
    "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot", "TerminationProven",
)


class Rejected(ValueError):
    """Sanitized P6 reader rejection code."""


def need(condition: Any, code: str) -> None:
    if not condition:
        raise Rejected(code)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


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
    need(math.isfinite(value) and not (text.startswith("-") and value == 0.0),
         "JSON_NONFINITE_OR_NEGATIVE_ZERO")
    return value


def _constant(_text: str) -> None:
    raise Rejected("JSON_NONFINITE")


def strict_json(raw: bytes) -> Any:
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_ENCODING")
    try:
        return json.loads(raw.decode("utf-8", "strict"), object_pairs_hook=_pairs,
                          parse_int=_parse_int, parse_float=_parse_float,
                          parse_constant=_constant)
    except Rejected:
        raise
    except (UnicodeError, json.JSONDecodeError, RecursionError, ValueError):
        raise Rejected("JSON_INVALID") from None


def _keys(value: Any, fields: Sequence[str], code: str) -> None:
    need(type(value) is dict and set(value) == set(fields), code)


def _int(value: Any, low: int, high: int, code: str) -> int:
    need(type(value) is int and low <= value <= high, code)
    return value


def _number(value: Any, low: float, high: float, code: str) -> float:
    need(type(value) in (int, float) and math.isfinite(value)
         and not isinstance(value, bool) and low <= value <= high, code)
    return float(value)


def _digest(value: Any, code: str) -> str:
    need(type(value) is str and HEX.fullmatch(value) is not None, code)
    return value


def _same(left: Any, right: Any) -> bool:
    return type(left) is type(right) and left == right


def _reparse(path: Path) -> bool:
    try:
        st = path.lstat()
    except OSError:
        return False
    attrs = getattr(st, "st_file_attributes", 0)
    return stat.S_ISLNK(st.st_mode) or bool(attrs & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400))


def _no_reparse(path: Any, must_exist: bool = True) -> Path:
    value = Path(os.path.abspath(os.fspath(path)))
    for item in (value, *value.parents):
        if not item.exists() and not item.is_symlink():
            need(not must_exist, "PATH_MISSING")
            continue
        need(not _reparse(item), "PATH_REPARSE")
    need(not must_exist or value.exists(), "PATH_MISSING")
    return value


def _stable_read(path: Any, maximum: int, allow_empty: bool = False) -> bytes:
    value = _no_reparse(path)
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
    identity = lambda st: (st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink)
    need(identity(path_before) == identity(opened) == identity(closed) == identity(path_after)
         and path_before.st_ctime_ns == path_after.st_ctime_ns
         and opened.st_ctime_ns == closed.st_ctime_ns
         and len(raw) == opened.st_size == closed.st_size, "FILE_CHANGED")
    return raw


def _below(path: Any, parent: Any) -> bool:
    child = os.path.normcase(os.path.abspath(os.fspath(path)))
    base = os.path.normcase(os.path.abspath(os.fspath(parent)))
    try:
        return os.path.commonpath((child, base)) == base
    except ValueError:
        return False


def _same_path(left: Any, right: Any) -> bool:
    return os.path.normcase(os.path.normpath(os.path.abspath(os.fspath(left)))) == \
        os.path.normcase(os.path.normpath(os.path.abspath(os.fspath(right))))


def _absolute(value: Any, code: str) -> Path:
    need(type(value) is str and Path(value).is_absolute()
         and os.path.normpath(value) == value, code)
    return Path(value)


def _read_json(path: Any, maximum: int = 1024 * 1024) -> Any:
    return strict_json(_stable_read(path, maximum))


def _child(root: Any, name: str) -> Path:
    need(type(name) is str and name and "/" not in name and "\\" not in name
         and name not in (".", ".."), "RUN_CHILD_NAME")
    parent = Path(root).absolute()
    target = (parent / name).absolute()
    need(target != parent and _below(target, parent), "RUN_CHILD_ESCAPE")
    return target


def _pin_map(p5: Any, pins: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
    try:
        return p5._pin_map(pins)
    except Exception as error:
        raise Rejected("PIN_FILE_CLOSURE_" + str(error)[:48]) from None


def _pin(p5: Any, pinned: Dict[str, Dict[str, Any]], path: Any,
         expected: Optional[str] = None) -> Dict[str, Any]:
    if p5 is None:
        key = os.path.normcase(os.path.abspath(os.fspath(path)))
        need(key in pinned, "UNPINNED_INPUT")
        row = pinned[key]
        need(expected is None or row["sha256"] == expected, "INPUT_PIN_VALUE")
        raw = _stable_read(row["path"], 16 * 1024 * 1024, allow_empty=True)
        need(len(raw) == row["bytes"] and _sha(raw) == row["sha256"], "INPUT_CLOSE_HASH")
        return row
    try:
        return p5._pin(pinned, path, expected)
    except Exception as error:
        raise Rejected("PIN_REFERENCE_" + str(error)[:48]) from None


def _module(path: Path, expected_sha: str, name: str) -> Any:
    raw = _stable_read(path, 4 * 1024 * 1024)
    need(_sha(raw) == expected_sha, "MODULE_HASH")
    spec = importlib.util.spec_from_file_location(name, path)
    need(spec is not None and spec.loader is not None, "MODULE_IMPORT")
    module = importlib.util.module_from_spec(spec)
    try:
        sys.modules[name] = module
        spec.loader.exec_module(module)
    except Exception:
        sys.modules.pop(name, None)
        raise Rejected("MODULE_LOAD") from None
    return module


def _exact_input_fields(pins: Any) -> Dict[str, Any]:
    _keys(pins, tuple(PIN_FIELDS), "PINS_FIELDS")
    need(pins["schema"] == INPUT_SCHEMA and pins["parent"] == PARENT
         and pins["baselineManifestSha256"] == BASELINE_SHA
         and pins["planSha256"] == PLAN_SHA and pins["protocolSha256"] == PROTOCOL_SHA
         and pins["addendumSha256"] == ADDENDUM_SHA
         and pins["p5InputPinsSha256"] == P5_INPUT_SHA
         and pins["p5QualificationSha256"] == P5_QUALIFICATION_SHA
         and pins["zeroDeltaInputSha256"] == ZERO_DELTA_SHA
         and pins["fullCutInputSha256"] == FULL_CUT_SHA,
         "PINS_IDENTITY")
    for key in ("repositoryRoot", "historicalRoot", "baselineManifestPath", "bridgeRoot",
                "bridgeManifest", "preflight", "nullRunRoot", "p5Root", "p5InputPinsPath",
                "p5QualificationPath", "p5ReaderPath", "p5CaptureRunRoot", "p5ControlReceipt",
                "planPath", "protocolPath", "addendumPath", "zeroDeltaInputPath",
                "fullCutInputPath"):
        _absolute(pins[key], "PINS_PATH_" + key.upper())
    _keys(pins["modeFiles"], MODE_FILE_ORDER, "MODE_FILE_ROLES")
    for key, value in pins["modeFiles"].items():
        _absolute(value, "MODE_FILE_PATH_" + key.upper())
    need(type(pins["sourceRows"]) is list and len(pins["sourceRows"]) == 1084
         and pins["changedRows"] == [] and type(pins["changedRows"]) is list,
         "SOURCE_ZERO_DELTA")
    need(type(pins["files"]) is list and 1 <= len(pins["files"]) <= 128,
         "PINS_FILE_CENSUS")
    return pins


def _baseline(path: Path) -> Dict[str, Any]:
    raw = _stable_read(path, 4 * 1024 * 1024)
    need(_sha(raw) == BASELINE_SHA, "BASELINE_HASH")
    doc = strict_json(raw)
    _keys(doc, ("schema", "parent", "repositoryRoot", "files", "p5InputPinsSha256",
                "p5QualificationSha256", "productionSources"), "BASELINE_FIELDS")
    need(doc["schema"] == "tsj-prefix-rejection-source-baseline-v1"
         and doc["parent"] == PARENT and doc["productionSources"] == 850
         and doc["p5InputPinsSha256"] == P5_INPUT_SHA
         and doc["p5QualificationSha256"] == P5_QUALIFICATION_SHA,
         "BASELINE_IDENTITY")
    rows = doc["files"]
    need(type(rows) is list and len(rows) == LIMITS["sourceCount"], "BASELINE_CENSUS")
    seen = set()
    roles = collections.Counter()
    for row in rows:
        _keys(row, ("role", "path", "bytes", "sha256"), "BASELINE_ROW_FIELDS")
        path_text = row["path"]
        need(type(path_text) is str and path_text and "\\" not in path_text
             and ":" not in path_text and not path_text.startswith("/")
             and all(part not in ("", ".", "..") for part in path_text.split("/")),
             "BASELINE_RELATIVE_PATH")
        need(path_text.casefold() not in seen, "BASELINE_DUPLICATE_PATH")
        seen.add(path_text.casefold())
        need(row["role"] in ("BASE_SOURCE", "HOST", "READER", "WIRE", "WRITER"),
             "BASELINE_ROLE")
        _int(row["bytes"], 0, 100 * 1024 * 1024, "BASELINE_FILE_BYTES")
        _digest(row["sha256"], "BASELINE_FILE_SHA")
        roles[row["role"]] += 1
    need(roles == collections.Counter({"BASE_SOURCE": 1080, "HOST": 1, "READER": 1,
                                       "WIRE": 1, "WRITER": 1}), "BASELINE_ROLE_CENSUS")
    return doc


def _validate_parent_router_role(mode: Dict[str, Any], p5_pins: Dict[str, Any]) -> Dict[str, Any]:
    p5_router_path = p5_pins["modeFiles"]["router"]
    p5_router_rows = [row for row in p5_pins["files"]
                      if type(row) is dict and row.get("path") == p5_router_path]
    need(len(p5_router_rows) == 1
         and p5_router_rows[0].get("bytes") == P5_ROUTER_OVERLAY_BYTES
         and p5_router_rows[0].get("sha256") == P5_ROUTER_OVERLAY_SHA
         and _same_path(mode["router"], p5_router_path), "P5_ROUTER_ROLE_BINDING")
    return p5_router_rows[0]


def _validate_source_parent_chain(pins: Dict[str, Any], p5_pins: Dict[str, Any],
                                  p6_baseline: Dict[str, Any],
                                  p5_baseline: Dict[str, Any]) -> None:
    need(_same_path(pins["historicalRoot"], p6_baseline["repositoryRoot"])
         and _same_path(p5_pins["repositoryRoot"], pins["historicalRoot"])
         and _same_path(p5_pins["historicalRoot"], p5_baseline["repositoryRoot"]),
         "P5_SOURCE_PARENT_CHAIN")


def _validate_qualified_sidecar_ref(run: Dict[str, Any], index: Dict[str, Any],
                                    key: str, expected_path: str, code: str) -> Dict[str, Any]:
    qualified = run.get(key)
    sidecar = index.get(key)
    need(type(qualified) is dict and type(sidecar) is dict, code)
    _keys(qualified, ("path", "bytes", "sha256"), code + "_FIELDS")
    need(qualified["path"] == expected_path
         and type(qualified["bytes"]) is int and qualified["bytes"] == sidecar.get("bytes")
         and type(qualified["sha256"]) is str
         and qualified["sha256"] == sidecar.get("sha256"), code)
    return qualified


def _verify_overlay(pins: Dict[str, Any], pinned: Dict[str, Dict[str, Any]],
                    p5: Any) -> None:
    mode = pins["modeFiles"]
    p5_pins_path = Path(pins["p5InputPinsPath"])
    p5_pins_row = _pin(p5, pinned, p5_pins_path, P5_INPUT_SHA)
    p5_pins_raw = _stable_read(p5_pins_row["path"], 4 * 1024 * 1024)
    need(len(p5_pins_raw) == p5_pins_row["bytes"] and _sha(p5_pins_raw) == P5_INPUT_SHA,
         "P5_INPUT_ROLE_HASH")
    p5_pins = strict_json(p5_pins_raw)
    try:
        p5._check_pins_shape(p5_pins)
    except Exception as error:
        raise Rejected("P5_INPUT_ROLE_SHAPE_" + str(error)[:48]) from None
    p5_router_row = _validate_parent_router_role(mode, p5_pins)
    router_role_pin = _pin(p5, pinned, mode["router"], P5_ROUTER_OVERLAY_SHA)
    need(router_role_pin["bytes"] == P5_ROUTER_OVERLAY_BYTES, "P5_ROUTER_ROLE_PIN")
    need(_same_path(mode["writer"], Path(pins["repositoryRoot"]) / WRITER_RELATIVE)
         and _same_path(mode["blocker"], Path(pins["p5Root"]) /
                        "source/CanonicalPrefixBlockerInspector.java")
         and _same_path(mode["queries"], Path(pins["p5Root"]) / "source/CanonicalPrefixCutQueries.java")
         and _same_path(mode["adapter"], Path(__file__).resolve().parent /
                        "source/CanonicalPrefixStageStudy.java")
         and _same_path(mode["stageInspector"], Path(__file__).resolve().parent /
                        "source/CanonicalPrefixStageInspector.java")
         and _same_path(mode["netRouterOverlay"], Path(__file__).resolve().parent /
                        "overlays-r1/PcbNetRouter.java"), "MODE_FILE_IDENTITY")
    source_path = Path(pins["repositoryRoot"]) / ROUTER_RELATIVE
    source_row = next((row for row in pins["sourceRows"] if row["path"] == ROUTER_RELATIVE), None)
    need(source_row is not None and source_row["bytes"] == 129081
         and source_row["sha256"] == "92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec",
         "ROUTER_SOURCE_ROW")
    source = _stable_read(source_path, 2 * 1024 * 1024)
    need(len(source) == source_row["bytes"] and _sha(source) == source_row["sha256"],
         "ROUTER_SOURCE_HASH")
    overlay_path = Path(mode["netRouterOverlay"])
    overlay = _stable_read(overlay_path, 2 * 1024 * 1024)
    manifest_path = Path(__file__).resolve().parent / "overlays-r1/manifest-r1.json"
    diff_path = Path(__file__).resolve().parent / "overlays-r1/PcbNetRouter.diff"
    builder_path = Path(__file__).resolve().parent / "build_overlay.py"
    builder_pin = _pin(p5, pinned, builder_path,
                       "8d741d4d166d63209bc39eace2d527cadf710f3320e2c8cd01d8dff00bb7d1fc")
    manifest_pin = _pin(p5, pinned, manifest_path)
    diff_pin = _pin(p5, pinned, diff_path,
                    "d0d3838385afed3096096ba526453d7682c18695fd3d1859bb1d6dc8346f4feb")
    need(builder_pin["sha256"] == "8d741d4d166d63209bc39eace2d527cadf710f3320e2c8cd01d8dff00bb7d1fc",
         "OVERLAY_BUILDER_PIN")
    builder = _module(builder_path, builder_pin["sha256"], "p6_pinned_overlay_builder")
    need(callable(getattr(builder, "make_overlay", None)), "OVERLAY_BUILDER_API")
    try:
        expected_overlay = builder.make_overlay(source)
    except Exception:
        raise Rejected("OVERLAY_BUILDER_REJECTED_SOURCE") from None
    need(type(expected_overlay) is bytes and expected_overlay == overlay
         and _sha(overlay) == "0432eed2ac4e84c647aee867cc1856bff7969280b094794bc0acb95907d66d94",
         "OVERLAY_TRANSFORM")
    diff = _stable_read(diff_path, 2 * 1024 * 1024)
    manifest_raw = _stable_read(manifest_path, 65536)
    manifest = strict_json(manifest_raw)
    _keys(manifest, ("schema", "builder", "source", "addendum", "overlay", "diff",
                     "editFragments", "unchangedSegments", "unchangedBytes",
                     "nonWhitelistedBytesIdentical", "routerBoundsAndEndpointLoopByteIdentical",
                     "predicateOrderAndSingleEvaluation",
                     "observerFieldCopiedToFixedGraphCopies"), "OVERLAY_MANIFEST_FIELDS")
    need(manifest["schema"] == "tsj-p6-pcb-net-router-overlay-manifest-r1"
         and manifest["builder"] == {"path": "build_overlay.py", "sha256": builder_pin["sha256"]}
         and manifest["source"] == {"path": ROUTER_RELATIVE, "bytes": len(source),
                                     "sha256": _sha(source), "frozenBaselineRow": True}
         and manifest["addendum"] == {"path": "ADDENDUM_R2.md", "sha256": ADDENDUM_SHA}
         and manifest["overlay"] == {"path": "overlays-r1/PcbNetRouter.java",
                                     "bytes": len(overlay), "sha256": _sha(overlay)}
         and manifest["diff"] == {"path": "overlays-r1/PcbNetRouter.diff",
                                  "bytes": len(diff), "sha256": _sha(diff)}
         and manifest["nonWhitelistedBytesIdentical"] is True
         and manifest["routerBoundsAndEndpointLoopByteIdentical"] is True
         and manifest["observerFieldCopiedToFixedGraphCopies"] is False
         and manifest["predicateOrderAndSingleEvaluation"] ==
             ["isLegalMove", "canTraverse", "canOccupy"], "OVERLAY_MANIFEST_BINDING")
    try:
        diff_text = "\n".join(difflib.unified_diff(
            source.decode("utf-8", "strict").splitlines(keepends=False),
            overlay.decode("utf-8", "strict").splitlines(keepends=False),
            fromfile="source/PcbNetRouter.java", tofile="overlays-r1/PcbNetRouter.java",
            lineterm="")) + "\n"
    except UnicodeError:
        raise Rejected("OVERLAY_DIFF_ENCODING") from None
    need(diff == diff_text.encode("utf-8") and manifest_pin["sha256"] == _sha(manifest_raw)
         and diff_pin["sha256"] == _sha(diff), "OVERLAY_DIFF_BINDING")


def _validate_source_closure(pins: Dict[str, Any], pinned: Dict[str, Dict[str, Any]],
                             pins_path: Path, p5: Any) -> Dict[str, Any]:
    repo = _absolute(pins["repositoryRoot"], "SOURCE_REPO_PATH")
    historical = _absolute(pins["historicalRoot"], "SOURCE_HISTORICAL_PATH")
    baseline_path = _absolute(pins["baselineManifestPath"], "BASELINE_PATH")
    need(_same_path(baseline_path, Path(__file__).resolve().parent / "source-baseline-r1.json"),
         "BASELINE_LOCATION")
    baseline = _baseline(baseline_path)
    _pin(None, pinned, baseline_path, BASELINE_SHA) if False else None
    need(_same_path(historical, Path(baseline["repositoryRoot"]))
         and not _same_path(repo, historical), "SOURCE_ROOTS")
    _pin(p5, pinned, baseline_path, BASELINE_SHA)
    repo = _no_reparse(repo)
    historical = _no_reparse(historical)
    need(repo.is_dir() and historical.is_dir(), "SOURCE_ROOT_KINDS")
    baseline_rows = {row["path"]: row for row in baseline["files"]}
    current_rows: Dict[str, Dict[str, Any]] = {}
    total = 0
    for row in pins["sourceRows"]:
        _keys(row, ("role", "path", "bytes", "sha256"), "SOURCE_ROW_FIELDS")
        base = baseline_rows.get(row["path"])
        need(base is not None and row["role"] == base["role"]
             and row["path"] not in current_rows, "SOURCE_ROW_IDENTITY")
        size = _int(row["bytes"], 0, 100 * 1024 * 1024, "SOURCE_ROW_BYTES")
        digest = _digest(row["sha256"], "SOURCE_ROW_SHA")
        need(size == base["bytes"] and digest == base["sha256"], "SOURCE_BASELINE_PARITY")
        total += size
        need(total <= LIMITS["sourceBytes"], "SOURCE_BYTE_CAP")
        current_rows[row["path"]] = row
    need(set(current_rows) == set(baseline_rows), "SOURCE_PATH_CENSUS")
    current_files: List[Tuple[Path, int, str]] = []
    historical_files: List[Tuple[Path, int, str]] = []
    for relative, row in current_rows.items():
        parts = relative.split("/")
        old_path = _no_reparse(historical.joinpath(*parts))
        new_path = _no_reparse(repo.joinpath(*parts))
        old_raw = _stable_read(old_path, 100 * 1024 * 1024, allow_empty=True)
        new_raw = _stable_read(new_path, 100 * 1024 * 1024, allow_empty=True)
        digest = row["sha256"]
        need(len(old_raw) == row["bytes"] == len(new_raw)
             and _sha(old_raw) == digest == _sha(new_raw), "SOURCE_FILE_HASH")
        current_files.append((new_path, row["bytes"], digest))
        historical_files.append((old_path, row["bytes"], digest))

    extras = _pin_map(p5, pins)
    # All P6 authored/helper/parent inputs are explicit hashes; no directory walk is used.
    need(len(extras) == len(pins["files"]), "EXTRA_FILE_CENSUS")
    for key in ("planPath", "protocolPath", "addendumPath", "zeroDeltaInputPath", "fullCutInputPath",
                "preflight", "p5InputPinsPath", "p5QualificationPath", "p5ReaderPath"):
        path = _absolute(pins[key], "INPUT_PATH_" + key.upper())
        need(os.path.normcase(str(path)) in extras, "INPUT_PATH_UNPINNED_" + key.upper())
    for key, digest in (("planPath", PLAN_SHA), ("protocolPath", PROTOCOL_SHA),
                        ("addendumPath", ADDENDUM_SHA), ("zeroDeltaInputPath", ZERO_DELTA_SHA),
                        ("fullCutInputPath", FULL_CUT_SHA),
                        ("p5InputPinsPath", P5_INPUT_SHA),
                        ("p5QualificationPath", P5_QUALIFICATION_SHA)):
        need(_sha(_stable_read(pins[key], 4 * 1024 * 1024)) == digest,
             "CONTROL_INPUT_HASH_" + key.upper())
    addendum_r1 = Path(__file__).resolve().parent / "ADDENDUM_R1.md"
    _pin(p5, extras, addendum_r1, ADDENDUM_R1_SHA)
    p5_reader_path = Path(pins["p5ReaderPath"])
    _pin(None, extras, p5_reader_path, P5_READER_SHA)
    need(_below(Path(pins["nullRunRoot"]), Path(tempfile.gettempdir()).resolve())
         and not _below(Path(pins["nullRunRoot"]), repo), "NULL_RUN_ROOT_CONFINEMENT")
    _verify_overlay(pins, extras, p5)
    return {
        "repo": repo, "historical": historical, "baselinePath": baseline_path,
        "baseline": baseline,
        "sourceRows": current_rows, "currentFiles": current_files,
        "historicalFiles": historical_files,
        "temp": Path(tempfile.gettempdir()).resolve(), "extraFiles": len(extras),
        "pinned": extras,
    }


def _p5_load_context(pins: Dict[str, Any], closure: Dict[str, Any],
                     p5: Any, p6_pinned: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    p5_path = Path(pins["p5InputPinsPath"])
    p5_raw = _stable_read(p5_path, 4 * 1024 * 1024)
    need(_sha(p5_raw) == P5_INPUT_SHA, "P5_INPUT_HASH")
    p5_pins = strict_json(p5_raw)
    try:
        p5._check_pins_shape(p5_pins)
    except Exception as error:
        raise Rejected("P5_INPUT_SHAPE_" + str(error)[:48]) from None
    p5_files = p5_pins.get("files")
    need(type(p5_files) is list, "P5_FILES_LIST")
    for row in p5_files:
        _keys(row, ("path", "bytes", "sha256"), "P5_PIN_ROW_FIELDS")
        key = os.path.normcase(os.path.abspath(row["path"]))
        p6_row = p6_pinned.get(key)
        need(p6_row is not None and p6_row["bytes"] == row["bytes"]
             and p6_row["sha256"] == row["sha256"], "P5_PARENT_FILE_NOT_REBOUND")
    p5_baseline_path = Path(p5_pins["baselineManifestPath"])
    p5_baseline_pin = _pin(p5, p6_pinned, p5_baseline_path, p5.BASELINE_SHA)
    need(p5_baseline_pin["sha256"] == p5.BASELINE_SHA, "P5_BASELINE_PIN")
    try:
        p5_baseline = p5._read_source_baseline(p5_baseline_path)
    except Exception as error:
        raise Rejected("P5_BASELINE_READ_" + str(error)[:48]) from None
    _validate_source_parent_chain(pins, p5_pins, closure["baseline"], p5_baseline)
    need(_same_path(pins["bridgeRoot"], p5_pins["bridgeRoot"])
         and _same_path(pins["bridgeManifest"], p5_pins["bridgeManifest"])
         and _same_path(pins["modeFiles"]["router"], p5_pins["modeFiles"]["router"])
         and _same_path(pins["modeFiles"]["support"], p5_pins["modeFiles"]["support"])
         and _same_path(pins["modeFiles"]["blocker"], p5_pins["modeFiles"]["inspector"])
         and _same_path(pins["modeFiles"]["queries"], p5_pins["modeFiles"]["queries"]),
         "P5_SOURCE_ROLE_BINDING")
    p5_rows = {row["path"]: row for row in p5_pins["sourceRows"]}
    need(set(p5_rows) == set(closure["sourceRows"]), "P5_SOURCE_CENSUS")
    for path, row in p5_rows.items():
        current = closure["sourceRows"][path]
        need(row["role"] == current["role"] and row["bytes"] == current["bytes"]
             and row["sha256"] == current["sha256"], "P5_SOURCE_PARENT_PARITY")
    try:
        context = p5._load_p4_context(p5_pins, p6_pinned, closure)
    except Exception as error:
        raise Rejected("P5_GRAPH_CONTEXT_" + str(error)[:48]) from None
    context["p5Pins"] = p5_pins
    context["p5PinsRaw"] = p5_raw
    context["p5PinsPath"] = p5_path
    return context


def _p5_parent(pins: Dict[str, Any], p5: Any, context: Dict[str, Any],
               closure: Dict[str, Any], pinned: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    q_path = Path(pins["p5QualificationPath"])
    q_raw = _stable_read(q_path, 4 * 1024 * 1024)
    need(_sha(q_raw) == P5_QUALIFICATION_SHA, "P5_QUALIFICATION_HASH")
    qualification = strict_json(q_raw)
    need(type(qualification) is dict
         and qualification.get("status") == "PASS_FINITE_CUT_ATTRIBUTION_ONLY"
         and qualification.get("inputPinsSha256") == P5_INPUT_SHA
         and qualification.get("sourceRows") == 1084,
         "P5_QUALIFICATION_IDENTITY")
    runs = qualification.get("caseRuns")
    need(type(runs) is list, "P5_CASE_RUNS")
    control_run = next((row for row in runs if row.get("name") == "control-r3"
                        and row.get("mode") == "CONTROL"), None)
    capture_run = next((row for row in runs if row.get("name") == "capture-r3"
                        and row.get("mode") == "CAPTURE"), None)
    need(type(control_run) is dict and type(capture_run) is dict
         and control_run.get("result", {}).get("status") == "PASS_CONTROL_ONLY"
         and capture_run.get("result", {}).get("status") == "PASS_CAPTURE_ATTRIBUTION_ONLY"
         and control_run.get("allProcessesTerminated") is True
         and capture_run.get("allProcessesTerminated") is True,
         "P5_ACCEPTED_CASES")
    p5_pins = context["p5Pins"]
    p5_root = Path(pins["p5Root"])
    need(_same_path(pins["p5CaptureRunRoot"], p5_root / "capture-r3")
         and _same_path(pins["p5ControlReceipt"], p5_root / "control-r3/data/op-1.jsonl"),
         "P5_ACCEPTED_PATHS")
    need(_pin(None, pinned, q_path, P5_QUALIFICATION_SHA)["sha256"] == P5_QUALIFICATION_SHA,
         "P5_QUALIFICATION_PIN")

    def load_case(run: Dict[str, Any], name: str, mode: str) -> Dict[str, Any]:
        run_id = run.get("result", {}).get("runId")
        need(type(run_id) is str and UUID.fullmatch(run_id) is not None,
             "P5_CASE_RUN_ID")
        root = p5_root / name
        data = root / "data"
        index_path = data / "canonical-blocker-index.json"
        op_path = data / "op-1.jsonl"
        index_raw = _stable_read(index_path, 65536)
        need(index_raw.endswith(b"\n") and index_raw.count(b"\n") == 1
             and b"\r" not in index_raw, "P5_INDEX_ENCODING")
        index = strict_json(index_raw[:-1])
        need(type(index) is dict, "P5_INDEX_OBJECT")
        if mode == "CAPTURE":
            need(type(run.get("index")) is dict and _same(index, run["index"]),
                 "P5_INDEX_QUALIFICATION")
        elif "index" in run:
            need(_same(index, run["index"]), "P5_CONTROL_INDEX_QUALIFICATION")
        try:
            entry = p5._index(index_raw, index, mode, run_id, P5_INPUT_SHA,
                              context["p4Reader"])
        except Exception as error:
            raise Rejected("P5_INDEX_" + str(error)[:48]) from None
        ref = entry["receipt"]
        qref = run.get("operationReceipt")
        need(type(qref) is dict and qref.get("path") == name + "/data/op-1.jsonl"
             and qref.get("bytes") == ref.get("bytes")
             and qref.get("sha256") == ref.get("sha256"), "P5_OPERATION_QUALIFICATION")
        op_raw = _stable_read(op_path, 8 * 1024 * 1024)
        need(len(op_raw) == ref["bytes"] and _sha(op_raw) == ref["sha256"],
             "P5_OPERATION_HASH")
        try:
            operation = context["p4Reader"]._operation(
                op_raw, entry, run_id, P5_INPUT_SHA, context["branchReader"],
            )
        except Exception as error:
            raise Rejected("P5_OPERATION_" + str(error)[:48]) from None
        result = {"run": run, "runId": run_id, "index": index, "entry": entry,
                  "receipt": op_raw, "operation": operation, "root": root, "data": data}
        if mode == "CONTROL":
            _p5_data_files(p5, data, {"canonical-blocker-index.json", "op-1.jsonl"})
            return result

        transport_ref = _validate_qualified_sidecar_ref(
            run, index, "transport", "capture-r3/data/fixed-prefix.jsonl",
            "P5_TRANSPORT_QUALIFICATION",
        )
        attr_ref = _validate_qualified_sidecar_ref(
            run, index, "attribution", "capture-r3/data/prefix-blockers.json",
            "P5_ATTR_QUALIFICATION",
        )
        transport_raw = _stable_read(data / "fixed-prefix.jsonl", 8 * 1024 * 1024)
        attr_raw = _stable_read(data / "prefix-blockers.json", 65536)
        need(len(transport_raw) == transport_ref["bytes"]
             and _sha(transport_raw) == transport_ref["sha256"]
             and len(attr_raw) == attr_ref["bytes"] and _sha(attr_raw) == attr_ref["sha256"],
             "P5_CAPTURE_ARTIFACT_HASH")
        try:
            facts, diagnosis = context["p4Reader"]._capture_transport(
                transport_raw, index, operation, run_id, P5_INPUT_SHA,
            )
            decoded = context["p4Reader"].decode_transport(transport_raw, True)
        except Exception as error:
            raise Rejected("P5_CAPTURE_TRANSPORT_" + str(error)[:48]) from None
        need(diagnosis.get("status") == "ZERO_VIA_ROUTE_EXCLUDED"
             and facts.get("prefixGeometrySha256") == PREFIX_SHA
             and _sha(decoded["graphBytes"]) == GRAPH_SHA
             and decoded["graphBytes"] == context["p4GraphBytes"],
             "P5_CAPTURE_GRAPH_BINDING")
        try:
            cut = p5._validate_cut_document(
                Path(p5_pins["cutInputPath"]), P5_INPUT_SHA, decoded["graph"], facts,
            )
            attribution = p5.strict_json(attr_raw[:-1])
            run_info = {"runId": run_id, "workNanos": run["wrapperWorkNanos"]}
            totals = p5._validate_attribution(
                attr_raw, attribution, "CAPTURE", run_info, facts, operation,
                context, closure, P5_INPUT_SHA, cut,
            )
        except Exception as error:
            raise Rejected("P5_ATTRIBUTION_" + str(error)[:48]) from None
        need(run.get("result", {}).get("attributionTotals") == totals,
             "P5_RESULT_TOTALS_BINDING")
        result.update({"transportRaw": transport_raw, "decodedTransport": decoded,
                       "facts": facts, "attributionRaw": attr_raw,
                       "attribution": attribution, "attributionTotals": totals,
                       "cutDocument": cut})
        _p5_data_files(p5, data, {"canonical-blocker-index.json", "op-1.jsonl",
                                  "fixed-prefix.jsonl", "prefix-blockers.json"})
        return result

    control = load_case(control_run, "control-r3", "CONTROL")
    capture = load_case(capture_run, "capture-r3", "CAPTURE")
    try:
        accounting = p5._validate_capture_parity(capture, control, context)
    except Exception as error:
        raise Rejected("P5_CAPTURE_ROUTE_PARITY_" + str(error)[:48]) from None
    need(accounting.get("p07OwnerStepsDelta") == P5_CAPTURE_WORK_DELTA,
         "P5_CAPTURE_WORK_DELTA")
    return {"qualification": qualification, "control": control, "capture": capture,
            "accounting": accounting, "context": context}


def _validate_zero_delta_input(pins: Dict[str, Any], parent: Dict[str, Any]) -> Dict[str, Any]:
    doc = _read_json(pins["zeroDeltaInputPath"], 1024 * 1024)
    _keys(doc, ("electrical", "fullCutInputSha256", "fullCutQueryCount", "graphSha256",
                "grid", "layout", "p5AttributionSha256", "p5InputPinsSha256",
                "p5QualificationSha256", "playable100", "prefixGeometrySha256", "queries",
                "queryCount", "queryOrdinals", "route", "schema", "status", "target"),
          "ZERO_DELTA_FIELDS")
    ordinals = sorted(SELECTED_ORDINALS)
    cut = parent["capture"]["cutDocument"]
    expected_queries = [row for row in cut["cutQueries"] if row["ordinal"] in SELECTED_ORDINALS]
    need(doc["schema"] == "tsj-prefix-zero-delta-query-input-v1"
         and doc["status"] == "PREPARED_INPUT_NOT_STAGE_DIAGNOSIS"
         and doc["fullCutInputSha256"] == FULL_CUT_SHA
         and doc["fullCutQueryCount"] == LIMITS["queries"]
         and doc["queryCount"] == len(SELECTED_ORDINALS)
         and doc["queryOrdinals"] == ordinals and _same(doc["queries"], expected_queries)
         and doc["graphSha256"] == GRAPH_SHA and doc["prefixGeometrySha256"] == PREFIX_SHA
         and doc["p5AttributionSha256"] == P5_ATTRIBUTION_SHA
         and doc["p5InputPinsSha256"] == P5_INPUT_SHA
         and doc["p5QualificationSha256"] == P5_QUALIFICATION_SHA
         and doc["route"] is False and type(doc["route"]) is bool
         and doc["layout"] is False and type(doc["layout"]) is bool
         and doc["electrical"] is False and type(doc["electrical"]) is bool
         and type(doc["playable100"]) is int and doc["playable100"] == 0
         and _same(doc["grid"], cut["grid"]) and _same(doc["target"], cut["target"]),
         "ZERO_DELTA_INPUT_BINDING")
    return doc


def _p5_data_files(p5: Any, root: Path, expected: set) -> None:
    try:
        p5._data_files(root, expected)
    except Exception as error:
        raise Rejected("P5_DATA_CENSUS_" + str(error)[:48]) from None


def _object(run_root: Path, name: str, maximum: int = 1024 * 1024) -> Dict[str, Any]:
    value = _read_json(_child(run_root, name), maximum)
    need(type(value) is dict, "OBJECT_EXPECTED")
    return value


def _process(p5: Any, value: Any, success: Optional[bool] = None) -> Dict[str, Any]:
    try:
        return p5._process(value, success)
    except Exception as error:
        raise Rejected("PROCESS_" + str(error)[:48]) from None


def _source_audit(proc: Dict[str, Any], pins_sha: str, extra_files: int) -> None:
    need(proc["Stderr"] == "", "SOURCE_AUDIT_STDERR")
    value = strict_json(proc["Stdout"].encode("utf-8"))
    _keys(value, ("changedRows", "extraFiles", "pinsSha256", "sourceRows", "status"),
          "SOURCE_AUDIT_FIELDS")
    need(value == {"changedRows": 0, "extraFiles": extra_files,
                   "pinsSha256": pins_sha, "sourceRows": 1084,
                   "status": "PASS_CANONICAL_PREFIX_STAGE_INPUT_CLOSURE_ONLY"},
         "SOURCE_AUDIT_RESULT")


def _validate_host(run_root: Path, pins_path: Path, pins: Dict[str, Any], pins_sha: str,
                   mode: str, closure: Dict[str, Any], p5: Any) -> Dict[str, Any]:
    launch = _object(run_root, "host-launch.json")
    host = _object(run_root, "host-result.json")
    _keys(launch, ("schema", "status", "mode", "runId", "pid", "startTicks", "executable",
                   "script", "scriptSha256", "inputPinsSha256", "scratch", "scratchCreatedTicks",
                   "outputRoot", "compileLimitMs", "nativeLimitMs", "cleanupLimitMs", "outerLimitMs"),
          "HOST_LAUNCH_FIELDS")
    _keys(host, ("schema", "status", "mode", "runId", "inputPinsSha256", "sourceClosure",
                 "compilerProcessId", "compilerProcessStartTicks", "compilerExitCode",
                 "compilerTerminationProven", "nativeExitCode", "nativeTerminationProven",
                 "nativeProcessId", "nativeProcessStartTicks", "nativeCaptureLimitNegative",
                 "nativeOutputBytes", "captureNegativeWallMs", "nativeWallMs", "wrapperWorkNanos",
                 "postSourceAndScratchCleanupMs", "cleanupConservativeUpperMs", "cleanupBoundMs",
                 "scratchRemoved", "cleanupAccounting", "outerElapsedMs", "compilerSources"),
          "HOST_RESULT_FIELDS")
    run_id = launch["runId"]
    need(launch["schema"] == HOST_SCHEMA and host["schema"] == HOST_RESULT_SCHEMA
         and launch["status"] == "LAUNCHED_NOT_ACCEPTED"
         and host["status"] == "HOST_COMPLETE_READER_PENDING"
         and launch["mode"] == host["mode"] == mode
         and launch["inputPinsSha256"] == host["inputPinsSha256"] == pins_sha
         and host["runId"] == run_id and type(run_id) is str
         and UUID.fullmatch(run_id) is not None, "HOST_CONTEXT")
    _int(launch["pid"], 1, (1 << 32) - 1, "HOST_PID")
    need(type(launch["startTicks"]) is str and re.fullmatch(r"[1-9][0-9]*", launch["startTicks"])
         and type(launch["scratchCreatedTicks"]) is str
         and re.fullmatch(r"[1-9][0-9]*", launch["scratchCreatedTicks"]), "HOST_TICKS")
    for key, expected in (("compileLimitMs", LIMITS["compileMs"]),
                          ("nativeLimitMs", LIMITS["nativeMs"]),
                          ("cleanupLimitMs", LIMITS["cleanupMs"]),
                          ("outerLimitMs", LIMITS["outerMs"])):
        need(type(launch[key]) is int and launch[key] == expected, "HOST_LIMIT_" + key.upper())

    scratch = _absolute(launch["scratch"], "HOST_SCRATCH_PATH")
    output = _absolute(launch["outputRoot"], "HOST_OUTPUT_PATH")
    need(_below(scratch, closure["temp"]) and not scratch.exists()
         and _same_path(output, run_root) and _below(output, closure["temp"])
         and not _below(output, closure["repo"]), "HOST_PATH_CONFINEMENT")
    script = _absolute(launch["script"], "HOST_SCRIPT_PATH")
    need(_same_path(script, Path(__file__).resolve().parent / "run-native.ps1"),
         "HOST_SCRIPT_IDENTITY")
    script_pin = _pin(p5, closure["pinned"], script)
    need(launch["scriptSha256"] == script_pin["sha256"], "HOST_SCRIPT_HASH")
    executable = _absolute(launch["executable"], "HOST_EXECUTABLE_PATH")
    need(executable.name.casefold() == "powershell.exe"
         and "system32" in [part.casefold() for part in executable.parts]
         and p5._file_sha(executable) ==
             "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f",
         "HOST_POWERSHELL_PIN")
    scratch_intent = _object(run_root, "scratch-intent.json")
    _keys(scratch_intent, ("status", "scratch", "hostPid", "hostStartTicks", "executable", "runId"),
          "SCRATCH_INTENT_FIELDS")
    need(scratch_intent["status"] == "INTENT_NOT_CREATION_PROOF"
         and _same_path(scratch_intent["scratch"], scratch)
         and scratch_intent["hostPid"] == launch["pid"]
         and str(scratch_intent["hostStartTicks"]) == launch["startTicks"]
         and _same_path(scratch_intent["executable"], executable)
         and scratch_intent["runId"] == launch["runId"], "SCRATCH_INTENT_BINDING")

    need(host["sourceClosure"] == "PASS" and host["nativeCaptureLimitNegative"] == "PASS"
         and host["compilerSources"] == LIMITS["compileSources"]
         and host["cleanupBoundMs"] == LIMITS["cleanupMs"]
         and host["scratchRemoved"] is True
         and type(host["compilerTerminationProven"]) is bool
         and host["compilerTerminationProven"] is True
         and type(host["nativeTerminationProven"]) is bool
         and host["nativeTerminationProven"] is True, "HOST_FINAL_PROOF")
    native_wall = _number(host["nativeWallMs"], 0, LIMITS["nativeMs"], "HOST_NATIVE_WALL")
    negative_wall = _number(host["captureNegativeWallMs"], 0,
                            LIMITS["negativeWallMs"], "HOST_NEGATIVE_WALL")
    post = _number(host["postSourceAndScratchCleanupMs"], 0,
                   LIMITS["cleanupMs"], "HOST_POST_CLEANUP")
    outer = _number(host["outerElapsedMs"], 0, LIMITS["outerMs"], "HOST_OUTER")
    work = _int(host["wrapperWorkNanos"], 0, LIMITS["workNanos"], "HOST_WRAPPER_WORK")
    _int(host["nativeOutputBytes"], 0, LIMITS["artifactBytes"], "HOST_OUTPUT_BYTES")
    need(type(host["compilerExitCode"]) is int and host["compilerExitCode"] == 0
         and type(host["nativeExitCode"]) is int
         and (host["nativeExitCode"] != 0 if mode == "CALLBACK_THROW"
              else host["nativeExitCode"] == 0), "HOST_EXIT_CODES")
    expected_kind = ("FULL_NATIVE_WALL_PLUS_POST_SOURCE" if mode == "CALLBACK_THROW"
                     else "NATIVE_RESIDUAL_PLUS_POST_SOURCE")
    need(host["cleanupAccounting"] == expected_kind, "HOST_CLEANUP_KIND")
    upper = (native_wall + post if mode == "CALLBACK_THROW"
             else native_wall - work / 1_000_000.0 + post)
    stated_upper = _number(host["cleanupConservativeUpperMs"], 0,
                           LIMITS["cleanupMs"], "HOST_CLEANUP_UPPER")
    need(abs(stated_upper - upper) <= 0.001 and outer >= native_wall + negative_wall + post,
         "HOST_CLEANUP_ACCOUNTING")
    for key in ("compilerProcessId", "nativeProcessId"):
        _int(host[key], 1, (1 << 32) - 1, "HOST_" + key.upper())
    for key in ("compilerProcessStartTicks", "nativeProcessStartTicks"):
        need(type(host[key]) is str and re.fullmatch(r"[1-9][0-9]*", host[key]),
             "HOST_PROCESS_TICKS")

    python = Path(pins["preflight"]).parent
    python_exe = None
    audit_args = ["-B", pins["preflight"], str(closure["repo"]),
                  str(closure["baselinePath"]), BASELINE_SHA, str(pins_path), pins_sha]
    for filename in ("preflight-process.json", "pre-native-process.json", "post-source-process.json"):
        proc = _process(p5, _object(run_root, filename), True)
        need(proc["Arguments"] == audit_args and proc["Stderr"] == ""
             and p5._file_sha(proc["FilePath"]) ==
             "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec",
             "SOURCE_AUDIT_PROCESS")
        _pin(p5, closure["pinned"], pins["preflight"])
        _source_audit(proc, pins_sha, closure["extraFiles"])
        if python_exe is None:
            python_exe = proc["FilePath"]
        else:
            need(_same_path(proc["FilePath"], python_exe), "PYTHON_PROCESS_PATH_PARITY")

    compiler_intent = _object(run_root, "compiler-launch-intent.json")
    _keys(compiler_intent, ("status", "executable", "sha256", "arguments",
                            "sourceListSha256", "sourceListPath", "limitMs"),
          "COMPILER_INTENT_FIELDS")
    compiler = _process(p5, _object(run_root, "compiler-process.json"), True)
    javac_sha = "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
    need(compiler_intent["status"] == "INTENT_NOT_START_PROOF"
         and compiler_intent["limitMs"] == LIMITS["compileMs"]
         and compiler_intent["sha256"] == javac_sha
         and compiler["Arguments"] == compiler_intent["arguments"]
         and _same_path(compiler["FilePath"], compiler_intent["executable"])
         and compiler["ProcessId"] == host["compilerProcessId"]
         and str(compiler["ProcessStartTicks"]) == host["compilerProcessStartTicks"]
         and p5._file_sha(compiler["FilePath"]) == javac_sha, "COMPILER_BINDING")
    classes = scratch / "classes"
    empty_source = scratch / "empty-sourcepath"
    source_list_path = scratch / "sources.txt"
    cp = ";".join((str(classes), str(closure["repo"] / ".tools/gwt-2.7.0/gwt-user-2.7.0.jar"),
                   str(closure["repo"] / ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar")))
    expected_args = ["-source", "7", "-target", "7", "-encoding", "UTF-8",
                     "-classpath", cp, "-sourcepath", str(empty_source), "-d", str(classes),
                     "@" + str(source_list_path)]
    need(compiler_intent["arguments"] == expected_args
         and _same_path(compiler_intent["sourceListPath"], source_list_path),
         "COMPILER_ARGUMENTS")
    source_list_raw = _stable_read(_child(run_root, "compiler-sources.txt"), 2 * 1024 * 1024)
    need(_sha(source_list_raw) == compiler_intent["sourceListSha256"]
         and source_list_raw.endswith(b"\n") and b"\x00" not in source_list_raw,
         "COMPILER_SOURCE_LIST_HASH")
    try:
        source_lines = source_list_raw.decode("utf-8", "strict").splitlines()
    except UnicodeError:
        raise Rejected("COMPILER_SOURCE_LIST_ENCODING") from None
    expected_sources = [str(closure["repo"].joinpath(*row["path"].split("/"))).replace("\\", "/")
                        for row in pins["sourceRows"]
                        if row["role"] == "BASE_SOURCE" and row["path"].endswith(".java")
                        and row["path"] not in EXCLUDED_JAVA]
    expected_sources.extend(str(Path(pins["modeFiles"][role])).replace("\\", "/")
                            for role in MODE_FILE_ORDER)
    bridge_manifest_raw = _stable_read(pins["bridgeManifest"], 65536)
    need(_sha(bridge_manifest_raw) == BRIDGE_MANIFEST_SHA, "BRIDGE_MANIFEST_HASH")
    bridge_copies = strict_json(_stable_read(_child(run_root, "consumed-bridge-copies.json"), 65536))
    bridge_manifest = strict_json(_stable_read(pins["bridgeManifest"], 65536))
    need(type(bridge_manifest) is list and len(bridge_manifest) == 3
         and type(bridge_copies) is list and len(bridge_copies) == 3,
         "BRIDGE_COPY_CENSUS")
    for actual, bridge in zip(bridge_copies, bridge_manifest):
        _keys(actual, ("path", "bytes", "sha256"), "BRIDGE_COPY_FIELDS")
        _keys(bridge, ("path", "bytes", "sha256"), "BRIDGE_MANIFEST_FIELDS")
        copy_path = scratch / bridge["path"]
        need(_same_path(actual["path"], copy_path)
             and actual["bytes"] == bridge["bytes"]
             and actual["sha256"] == bridge["sha256"], "BRIDGE_COPY_BINDING")
        expected_sources.append(str(copy_path).replace("\\", "/"))
    need(len(source_lines) == LIMITS["compileSources"]
         and len(set(source_lines)) == LIMITS["compileSources"]
         and all(line.startswith('"') and line.endswith('"') and '"' not in line[1:-1]
                 for line in source_lines)
         and [line[1:-1] for line in source_lines] == expected_sources,
         "COMPILER_SOURCE_CENSUS")

    native_intent = _object(run_root, "native-launch-intent.json")
    _keys(native_intent, ("status", "arguments", "executable", "sha256", "limitMs"),
          "NATIVE_INTENT_FIELDS")
    native = _process(p5, _object(run_root, "native-process.json"), mode != "CALLBACK_THROW")
    need(native["ProcessId"] == host["nativeProcessId"]
         and str(native["ProcessStartTicks"]) == host["nativeProcessStartTicks"],
         "NATIVE_PROCESS_BINDING")
    out_bytes = len(native["Stdout"].encode("utf-8")) + len(native["Stderr"].encode("utf-8"))
    need(out_bytes == host["nativeOutputBytes"] and out_bytes <= LIMITS["artifactBytes"],
         "NATIVE_OUTPUT_CENSUS")
    java_sha = "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
    need(native_intent["status"] == "INTENT_NOT_START_PROOF"
         and native_intent["sha256"] == java_sha
         and native_intent["limitMs"] == LIMITS["nativeMs"]
         and native["Arguments"] == native_intent["arguments"]
         and _same_path(native["FilePath"], native_intent["executable"])
         and p5._file_sha(native["FilePath"]) == java_sha, "NATIVE_BINDING")
    data_root = output / "data"
    if mode == "CANARY":
        expected_native_args = ["-ea", "-cp", cp, MAIN_CLASS, "--canary"]
    else:
        expected_native_args = ["-ea", "-cp", cp, MAIN_CLASS, mode, run_id, pins_sha,
                                WIRE_SHA, str(data_root)]
    need(native_intent["arguments"] == expected_native_args, "NATIVE_ARGUMENTS")

    neg_intent = _object(run_root, "native-capture-negative-intent.json")
    _keys(neg_intent, ("status", "executable", "sha256", "arguments", "limitMs"),
          "OVERFLOW_INTENT_FIELDS")
    negative = _process(p5, _object(run_root, "native-capture-limit-negative.json"), False)
    negative_args = ["-ea", "-cp", cp, MAIN_CLASS, "--capture-overflow"]
    need(neg_intent["status"] == "INTENT_NOT_START_PROOF"
         and neg_intent["limitMs"] == LIMITS["negativeMs"]
         and neg_intent["sha256"] == java_sha and neg_intent["arguments"] == negative_args
         and negative["Arguments"] == negative_args
         and _same_path(negative["FilePath"], native["FilePath"])
         and negative["ExitCode"] != 0 and negative["Stdout"] == ""
         and "CANONICAL_PREFIX_CAPTURE_LIMIT" in negative["Stderr"]
         and len(negative["Stderr"].encode("utf-8")) <= LIMITS["artifactBytes"],
         "OVERFLOW_NEGATIVE")
    need(work <= LIMITS["workNanos"], "HOST_WORK_CAP")
    return {"runId": run_id, "launch": launch, "host": host, "native": native,
            "negative": negative, "workNanos": work, "cleanupUpperMs": stated_upper,
            "outputRoot": output, "scratch": scratch, "cp": cp,
            "nativeWallMs": native_wall, "negativeWallMs": negative_wall}


def _validate_stage_payload(raw: Optional[bytes], payload: Any, mode: str, run_id: str,
                            pins_sha: str, facts: Dict[str, Any], operation: Dict[str, Any],
                            p5_attribution: Dict[str, Any], p5_totals: Dict[str, Any],
                            cut_document: Dict[str, Any], work_nanos: int, p5: Any,
                            branch_reader: Any) -> Dict[str, Any]:
    need(type(payload) is dict, "STAGE_OBJECT")
    if raw is not None:
        need(type(raw) is bytes and 1 < len(raw) <= LIMITS["stageBytes"]
             and raw.endswith(b"\n") and raw.count(b"\n") == 1 and b"\r" not in raw
             and not raw.startswith(b"\xef\xbb\xbf"), "STAGE_ENCODING")
        need(_same(strict_json(raw[:-1]), payload), "STAGE_RAW_BINDING")
    else:
        try:
            embedded = json.dumps(payload, ensure_ascii=True, separators=(",", ":")).encode("ascii")
        except (TypeError, ValueError, UnicodeError):
            raise Rejected("STAGE_EMBEDDED_JSON") from None
        need(0 < len(embedded) <= LIMITS["stageBytes"], "STAGE_EMBEDDED_SIZE")
    _keys(payload, (
        "schema", "status", "mode", "runId", "sourceManifestSha256", "wireSha256", "scope",
        "layoutQualification", "zeroDeltaInputSha256", "fullCutInputSha256", "graphSha256",
        "prefixGeometrySha256", "receiptBranchOrdinal", "lineageBranchOrdinal", "netId",
        "startPadId", "rootPadId", "reachedPthPadIds", "lifecycle", "sourceProof", "queries",
        "totals", "exceptionStage",
    ), "STAGE_FIELDS")
    normal = mode in ("NULL", "OBSERVE")
    exception = mode == "CALLBACK_THROW"
    need(mode in ("NULL", "OBSERVE", "CALLBACK_THROW"), "STAGE_MODE")
    status = ({"NULL": "COMPLETE_NULL_CONTROL_ONLY",
               "OBSERVE": "COMPLETE_STAGE_OBSERVATIONS_ONLY",
               "CALLBACK_THROW": "EXPECTED_CALLBACK_THROW_ONLY"}[mode])
    need(payload["schema"] == STAGES_SCHEMA and payload["status"] == status
         and payload["mode"] == mode and payload["runId"] == run_id
         and payload["sourceManifestSha256"] == pins_sha
         and payload["wireSha256"] == WIRE_SHA and payload["scope"] == SCOPE
         and payload["layoutQualification"] is False
         and type(payload["layoutQualification"]) is bool
         and payload["zeroDeltaInputSha256"] == ZERO_DELTA_SHA
         and payload["fullCutInputSha256"] == FULL_CUT_SHA
         and payload["graphSha256"] == GRAPH_SHA
         and payload["prefixGeometrySha256"] == PREFIX_SHA
         and payload["receiptBranchOrdinal"] == 307
         and payload["lineageBranchOrdinal"] == 77
         and payload["netId"] == "A_COIL_LOW"
         and payload["startPadId"] == "QA.C" and payload["rootPadId"] == "DA.A"
         and payload["exceptionStage"] == ("FIRST_ENDPOINT_CALLBACK" if exception else None),
         "STAGE_IDENTITY")
    _int(payload["receiptBranchOrdinal"], 0, 1_000_000, "STAGE_RECEIPT_ORDINAL")
    _int(payload["lineageBranchOrdinal"], 0, 1_000_000, "STAGE_LINEAGE_ORDINAL")
    need(facts.get("netId") == payload["netId"]
         and facts.get("startPadId") == payload["startPadId"]
         and facts.get("rootPadId") == payload["rootPadId"]
         and facts.get("receiptBranchOrdinal") == 307
         and facts.get("lineageBranchOrdinal") == 77
         and facts.get("prefixGeometrySha256") == PREFIX_SHA
         and type(facts.get("reachedPadIds")) is list
         and type(facts.get("reachedPthPadIds")) is list,
         "STAGE_FACT_BINDING")
    reached_ids = facts["reachedPadIds"]
    reached_pth = facts["reachedPthPadIds"]
    pads = {row["padId"]: row for row in operation.get("pads", [])
            if type(row) is dict and type(row.get("padId")) is str}
    need(len(pads) == len(operation.get("pads", []))
         and len(reached_ids) == len(set(reached_ids))
         and len(reached_pth) == len(set(reached_pth))
         and all(type(item) is str and item in pads for item in reached_ids)
         and all(item in reached_ids for item in reached_pth), "STAGE_REACHED_PAD_CENSUS")
    branch = next((row for row in operation.get("branches", [])
                   if type(row) is dict and row.get("ordinal") == 307), None)
    need(type(branch) is dict, "STAGE_TARGET_BRANCH_MISSING")
    snapshot = branch.get("snapshot", {})
    need(snapshot.get("pass") == 2 and snapshot.get("netId") == payload["netId"]
         and snapshot.get("startPadId") == payload["startPadId"]
         and snapshot.get("rootPadId") == payload["rootPadId"]
         and snapshot.get("terminalReason") == "NO_PATH"
         and snapshot.get("reachedPadCount") == len(reached_ids),
         "STAGE_BRANCH_SNAPSHOT_BINDING")
    need(pads["DA.A"]["netId"] == payload["netId"]
         and pads["QA.C"]["netId"] == payload["netId"]
         and pads["DA.A"].get("x") == facts.get("rootX")
         and pads["DA.A"].get("y") == facts.get("rootY")
         and pads["QA.C"].get("x") == facts.get("startX")
         and pads["QA.C"].get("y") == facts.get("startY"), "STAGE_TARGET_PAD_BINDING")
    expected_pth = list(reached_pth)
    need(payload["reachedPthPadIds"] == expected_pth,
         "STAGE_REACHED_PTH_BINDING")
    endpoint_ids = [payload["rootPadId"]]
    endpoint_ids.extend(item for item in expected_pth
                        if item not in (payload["startPadId"], payload["rootPadId"]))
    need(len(endpoint_ids) == len(set(endpoint_ids)), "STAGE_ENDPOINT_DUPLICATE")

    lifecycle = payload["lifecycle"]
    _keys(lifecycle, ("offers", "selected", "completed", "ownersReleased", "contextRestored",
                      "sourceRefsDropped", "observersDetached"), "STAGE_LIFECYCLE_FIELDS")
    need(_int(lifecycle["offers"], 0, 1000000, "STAGE_OFFERS") == 308
         and _int(lifecycle["selected"], 0, 1000000, "STAGE_SELECTED") == 1
         and _int(lifecycle["completed"], 0, 1000000, "STAGE_COMPLETED") ==
             (0 if exception else 1)
         and all(type(lifecycle[key]) is bool and lifecycle[key] is True for key in (
             "ownersReleased", "contextRestored", "sourceRefsDropped", "observersDetached")),
         "STAGE_LIFECYCLE_BINDING")

    proof = payload["sourceProof"]
    _keys(proof, ("geometryBeforeSha256", "geometryAfterSha256", "faces"),
          "STAGE_SOURCE_PROOF_FIELDS")
    need(proof["geometryBeforeSha256"] == PREFIX_SHA
         and proof["geometryAfterSha256"] == PREFIX_SHA, "STAGE_GEOMETRY_STABILITY")
    p5_faces = p5_attribution.get("sourceProof", {}).get("faces")
    need(type(p5_faces) is list and len(p5_faces) == 2, "STAGE_PARENT_FACE_PROOF")
    expected_layer_counts = collections.Counter(
        query["layer"] for query in (cut_document["cutQueries"][:1] if exception
                                     else cut_document["cutQueries"])
    )
    faces = proof["faces"]
    need(type(faces) is list and len(faces) == 2, "STAGE_FACE_CENSUS")
    face_totals = {"copyEntries": 0, "comparisonEntries": 0,
                   "copyUnits": 0, "comparisonUnits": 0}
    for layer, face in enumerate(faces):
        _keys(face, ("layer", "sourceEffectsBeforeSha256", "sourceEffectsAfterSha256",
                     "liveToPristineBeforeEqual", "queryToPristineBeforeEqual",
                     "liveToPristineAfterEqual", "liveObserverNullBefore",
                     "pristineObserverNullBefore", "liveObserverNullAfter",
                     "pristineObserverNullAfter", "queryObserverNullAfter", "copyEntries",
                     "comparisonEntries", "copyUnits", "comparisonUnits", "queryCount"),
              "STAGE_FACE_FIELDS")
        parent_face = p5_faces[layer]
        _digest(face["sourceEffectsBeforeSha256"], "STAGE_FACE_EFFECTS_BEFORE")
        _digest(face["sourceEffectsAfterSha256"], "STAGE_FACE_EFFECTS_AFTER")
        need(face["layer"] == layer and parent_face.get("layer") == layer
             and face["sourceEffectsBeforeSha256"] == face["sourceEffectsAfterSha256"]
             and face["sourceEffectsBeforeSha256"] == parent_face.get("sourceEffectsBeforeSha256")
             and face["sourceEffectsAfterSha256"] == parent_face.get("sourceEffectsAfterSha256"),
             "STAGE_FACE_SOURCE_EFFECTS")
        for key in ("liveToPristineBeforeEqual", "queryToPristineBeforeEqual",
                    "liveToPristineAfterEqual", "liveObserverNullBefore",
                    "pristineObserverNullBefore", "liveObserverNullAfter",
                    "pristineObserverNullAfter", "queryObserverNullAfter"):
            need(type(face[key]) is bool and face[key] is True, "STAGE_FACE_BOOLEAN_" + key.upper())
        copy_entries = _int(face["copyEntries"], 1, 2 * LIMITS["cloneEntries"],
                            "STAGE_FACE_COPY_ENTRIES")
        comparison_entries = _int(face["comparisonEntries"], 1,
                                  3 * LIMITS["cloneEntries"], "STAGE_FACE_COMPARISON_ENTRIES")
        copy_units = _int(face["copyUnits"], 4, LIMITS["observationUnits"],
                          "STAGE_FACE_COPY_UNITS")
        comparison_units = _int(face["comparisonUnits"], 6, LIMITS["observationUnits"],
                                "STAGE_FACE_COMPARISON_UNITS")
        query_count = _int(face["queryCount"], 0, LIMITS["queries"], "STAGE_FACE_QUERY_COUNT")
        need(query_count == expected_layer_counts.get(layer, 0), "STAGE_FACE_QUERY_BINDING")
        need(copy_entries <= copy_units * LIMITS["unitEntries"]
             and comparison_entries <= comparison_units * LIMITS["unitEntries"],
             "STAGE_FACE_UNIT_LOWER_BOUND")
        face_totals["copyEntries"] += copy_entries
        face_totals["comparisonEntries"] += comparison_entries
        face_totals["copyUnits"] += copy_units
        face_totals["comparisonUnits"] += comparison_units

    query_rows = payload["queries"]
    expected_count = 1 if exception else LIMITS["queries"]
    need(type(query_rows) is list and len(query_rows) == expected_count,
         "STAGE_QUERY_CENSUS")
    cut_queries = cut_document["cutQueries"]
    observations = p5_attribution.get("observations")
    need(type(observations) is list and len(observations) == LIMITS["queries"],
         "STAGE_PARENT_OBSERVATIONS")
    callback_queries = 0
    event_count = 0
    layer_queries = collections.Counter()
    counter_fields = (
        "routerCongestionBefore", "routerCongestionAfter", "routerCongestionDelta",
        "workCongestionBefore", "workCongestionAfter", "workCongestionDelta",
        "conflictsBefore", "conflictsAfter", "conflictsDelta",
    )
    query_fields = (
        "ordinal", "layer", "fromState", "toState", "bit", "selected", "fromGridX",
        "fromGridY", "toGridX", "toGridY", "outcome", "permitsStep",
        "routerCongestionBefore", "routerCongestionAfter", "routerCongestionDelta",
        "workCongestionBefore", "workCongestionAfter", "workCongestionDelta",
        "conflictsBefore", "conflictsAfter", "conflictsDelta", "events",
    )
    width, height = facts["gridWidth"], facts["gridHeight"]
    cells = facts["cellCount"]
    pitch, origin_x, origin_y = facts["gridPitch"], facts["originX"], facts["originY"]
    need(width == 478 and height == 309 and cells == width * height and pitch == 10,
         "STAGE_GRID_IDENTITY")
    known_nets = _known_board_nets(operation)
    net_set = set(known_nets)
    for ordinal, row in enumerate(query_rows):
        _keys(row, query_fields, "STAGE_QUERY_FIELDS")
        cut_query = cut_queries[ordinal]
        expected_selected = ordinal in SELECTED_ORDINALS
        need(row["ordinal"] == ordinal == cut_query["ordinal"]
             and row["layer"] == cut_query["layer"]
             and row["fromState"] == cut_query["fromState"]
             and row["toState"] == cut_query["toState"]
             and row["bit"] == cut_query["bit"]
             and type(row["selected"]) is bool and row["selected"] is expected_selected,
             "STAGE_QUERY_ORDER_OR_CUT")
        for key in ("ordinal", "layer", "fromState", "toState", "bit"):
            _int(row[key], 0, (1 << 31) - 1, "STAGE_QUERY_" + key.upper())
        layer = row["layer"]
        need(layer in (0, 1), "STAGE_QUERY_LAYER")
        layer_queries[layer] += 1
        from_cell, to_cell = row["fromState"] % cells, row["toState"] % cells
        from_x, from_y = from_cell % width, from_cell // width
        to_x, to_y = to_cell % width, to_cell // width
        need(row["fromGridX"] == from_x and row["fromGridY"] == from_y
             and row["toGridX"] == to_x and row["toGridY"] == to_y
             and cut_query["fromX"] == origin_x + from_x * pitch
             and cut_query["fromY"] == origin_y + from_y * pitch
             and cut_query["toX"] == origin_x + to_x * pitch
             and cut_query["toY"] == origin_y + to_y * pitch,
             "STAGE_QUERY_GRID_BINDING")
        direction = {(0, -1): 1, (1, 0): 2, (0, 1): 4, (-1, 0): 8}.get(
            (to_x - from_x, to_y - from_y))
        need(direction == row["bit"], "STAGE_QUERY_DIRECTION")
        if exception:
            need(row["outcome"] == "EXPECTED_CALLBACK_THROW" and row["permitsStep"] is None,
                 "STAGE_EXCEPTION_QUERY_OUTCOME")
        else:
            need(row["outcome"] == "RETURNED" and row["permitsStep"] is False
                 and type(row["permitsStep"]) is bool, "STAGE_QUERY_OUTCOME")
        for key in ("routerCongestionBefore", "routerCongestionAfter",
                    "routerCongestionDelta", "workCongestionBefore", "workCongestionAfter",
                    "workCongestionDelta"):
            _int(row[key], 0, 1_000_000_000, "STAGE_" + key.upper())
        need(row["routerCongestionAfter"] >= row["routerCongestionBefore"]
             and row["workCongestionAfter"] >= row["workCongestionBefore"]
             and row["routerCongestionAfter"] - row["routerCongestionBefore"] ==
                 row["routerCongestionDelta"]
             and row["workCongestionAfter"] - row["workCongestionBefore"] ==
                 row["workCongestionDelta"]
             and row["routerCongestionDelta"] == row["workCongestionDelta"],
             "STAGE_CONGESTION_ARITHMETIC")
        for key in ("conflictsBefore", "conflictsAfter", "conflictsDelta"):
            _counter_map(row[key], net_set, branch_reader, "STAGE_" + key.upper())
        parent_observation = observations[ordinal]
        for key in counter_fields:
            need(_same(row[key], parent_observation.get(key)),
                 "STAGE_P5_COUNTER_PARITY_" + key.upper())

        expected_callback = (mode == "OBSERVE" and expected_selected) or (exception and ordinal == 0)
        if expected_callback:
            callback_queries += 1
        events = row["events"]
        need(type(events) is list, "STAGE_EVENTS_LIST")
        expected_events = endpoint_ids if expected_callback else []
        need(len(events) == len(expected_events), "STAGE_EVENT_CENSUS")
        for event_ordinal, (event, endpoint_id) in enumerate(zip(events, expected_events)):
            _keys(event, ("ordinal", "netId", "startPadId", "endpointPadId", "fromGridX",
                          "fromGridY", "toGridX", "toGridY", "direction", "stage"),
                  "STAGE_EVENT_FIELDS")
            need(event["ordinal"] == event_ordinal
                 and event["netId"] == payload["netId"]
                 and event["startPadId"] == payload["startPadId"]
                 and event["endpointPadId"] == endpoint_id
                 and event["fromGridX"] == from_x and event["fromGridY"] == from_y
                 and event["toGridX"] == to_x and event["toGridY"] == to_y
                 and event["direction"] == {1: 0, 2: 1, 4: 2, 8: 3}[row["bit"]],
                 "STAGE_EVENT_IDENTITY")
            _int(event["ordinal"], 0, 1000000, "STAGE_EVENT_ORDINAL")
            for key, high in (("fromGridX", width - 1), ("toGridX", width - 1),
                              ("fromGridY", height - 1), ("toGridY", height - 1)):
                _int(event[key], 0, high, "STAGE_EVENT_" + key.upper())
            _int(event["direction"], 0, 3, "STAGE_EVENT_DIRECTION")
            need(type(event["stage"]) is str and event["stage"] in STAGE_NAMES[:-1],
                 "STAGE_EVENT_TERMINAL_STAGE")
            event_count += 1

    total = payload["totals"]
    _keys(total, ("queryCount", "selectedQueryCount", "callbackQueryCount", "eventCount",
                  "attachments", "detachments", "copyEntries", "comparisonEntries",
                  "copyUnits", "comparisonUnits", "queryUnits", "priorObservationUnits",
                  "totalObservationUnits", "elapsedNanos", "maximumUnitNanos"),
          "STAGE_TOTAL_FIELDS")
    expected_queries = 1 if exception else LIMITS["queries"]
    expected_selected_count = 1 if exception else len(SELECTED_ORDINALS)
    expected_callbacks = expected_selected_count if mode == "OBSERVE" else (1 if exception else 0)
    expected_prior = (0 if exception else p5_totals["copyUnits"] + p5_totals["comparisonUnits"]
                      + p5_totals["queryUnits"] + p5_totals["geometryRowUnits"])
    for key, wanted in (("queryCount", expected_queries),
                        ("selectedQueryCount", expected_selected_count),
                        ("callbackQueryCount", expected_callbacks),
                        ("eventCount", event_count),
                        ("attachments", expected_callbacks),
                        ("detachments", expected_callbacks),
                        ("queryUnits", expected_queries),
                        ("priorObservationUnits", expected_prior)):
        need(_int(total[key], 0, LIMITS["observationUnits"], "STAGE_TOTAL_" + key.upper()) == wanted,
             "STAGE_TOTAL_BINDING_" + key.upper())
    for key in ("copyEntries", "comparisonEntries", "copyUnits", "comparisonUnits"):
        _int(total[key], 0, LIMITS["observationUnits"] * LIMITS["unitEntries"],
             "STAGE_TOTAL_" + key.upper())
        need(total[key] == face_totals[key], "STAGE_TOTAL_FACE_SUM_" + key.upper())
    _int(total["totalObservationUnits"], 0, LIMITS["observationUnits"],
         "STAGE_TOTAL_OBSERVATION_UNITS")
    need(total["totalObservationUnits"] == expected_prior + total["copyUnits"]
         + total["comparisonUnits"] + total["queryUnits"], "STAGE_TOTAL_UNITS_ARITHMETIC")
    elapsed = _int(total["elapsedNanos"], 1, LIMITS["workNanos"], "STAGE_ELAPSED")
    maximum = _int(total["maximumUnitNanos"], 1, LIMITS["unitNanos"], "STAGE_MAX_UNIT")
    need(elapsed <= work_nanos and maximum <= elapsed
         and total["totalObservationUnits"] <= LIMITS["observationUnits"],
         "STAGE_WORK_OR_UNIT_BOUND")
    need(layer_queries == collections.Counter({0: expected_layer_counts.get(0, 0),
                                               1: expected_layer_counts.get(1, 0)}),
         "STAGE_QUERY_LAYER_CENSUS")
    return {"queryCount": expected_queries, "selectedQueryCount": expected_selected_count,
            "callbackQueryCount": expected_callbacks, "eventCount": event_count,
            "copyUnits": total["copyUnits"], "comparisonUnits": total["comparisonUnits"],
            "queryUnits": total["queryUnits"], "priorObservationUnits": expected_prior,
            "totalObservationUnits": total["totalObservationUnits"],
            "elapsedNanos": elapsed, "maximumUnitNanos": maximum}


def _known_board_nets(operation: Dict[str, Any]) -> List[str]:
    pads = operation.get("pads")
    candidate = operation.get("header", {}).get("candidate", {})
    need(type(pads) is list and len(pads) == candidate.get("padCount"),
         "STAGE_BOARD_PAD_CENSUS")
    nets = set()
    for row in pads:
        need(type(row) is dict and type(row.get("netId")) is str and row["netId"],
             "STAGE_BOARD_PAD_NET")
        nets.add(row["netId"])
    need(len(nets) == candidate.get("netCount") == 60, "STAGE_BOARD_NET_CENSUS")
    return sorted(nets)


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


def _decode_marker(stdout: str, marker: str) -> Dict[str, Any]:
    need(type(stdout) is str and "\x00" not in stdout and stdout.endswith(("\n", "\r\n")),
         "NATIVE_STDOUT_CLOSURE")
    normalized = stdout.replace("\r\n", "\n")
    need("\r" not in normalized and normalized.count("\n") == 1,
         "NATIVE_STDOUT_LINES")
    line = normalized[:-1]
    need(line.startswith(marker) and len(line.encode("utf-8")) <= LIMITS["lineBytes"],
         "NATIVE_MARKER")
    body = line[len(marker):]
    need(body and not body.startswith(" "), "NATIVE_MARKER_BODY")
    value = strict_json(body.encode("utf-8"))
    need(type(value) is dict, "NATIVE_MARKER_OBJECT")
    return value


def _canary(info: Dict[str, Any]) -> Dict[str, Any]:
    native = info["native"]
    need(native["ExitCode"] == 0 and native["Stderr"] == "", "CANARY_PROCESS")
    stdout = native["Stdout"]
    need(type(stdout) is str and "\x00" not in stdout
         and stdout.endswith(("\n", "\r\n")), "CANARY_STDOUT")
    normalized = stdout.replace("\r\n", "\n")
    need("\r" not in normalized, "CANARY_LINE_ENDING")
    lines = normalized[:-1].split("\n")
    need(len(lines) >= 2 and all(line.startswith("PASS: ")
                                 and len(line.encode("utf-8")) <= LIMITS["lineBytes"]
                                 for line in lines[:-1]), "CANARY_PASS_LINES")
    marker = "CANONICAL_PREFIX_STAGE_CANARY_COMPLETE "
    need(lines[-1].startswith(marker)
         and len(lines[-1].encode("utf-8")) <= LIMITS["lineBytes"], "CANARY_MARKER")
    value = strict_json(lines[-1][len(marker):].encode("utf-8"))
    _keys(value, ("schema", "status", "assertions", "blockerAssertions", "stageAssertions",
                  "elapsedNanos", "groups", "maximumGroupNanos", "layoutQualification"),
          "CANARY_FIELDS")
    need(value["schema"] == CANARY_SCHEMA and value["status"] == "PASS"
         and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "CANARY_IDENTITY")
    _int(value["assertions"], 1, 100000, "CANARY_ASSERTIONS")
    _int(value["blockerAssertions"], 1, 100000, "CANARY_BLOCKER_ASSERTIONS")
    _int(value["stageAssertions"], 1, 100000, "CANARY_STAGE_ASSERTIONS")
    elapsed = _int(value["elapsedNanos"], 1, LIMITS["workNanos"], "CANARY_ELAPSED")
    _int(value["groups"], 1, LIMITS["groups"], "CANARY_GROUPS")
    _int(value["maximumGroupNanos"], 1, LIMITS["unitNanos"], "CANARY_MAX_GROUP")
    need(elapsed == info["workNanos"], "CANARY_HOST_WORK")
    return value


def _native_trailer(info: Dict[str, Any], mode: str, pins_sha: str) -> Dict[str, Any]:
    native, host = info["native"], info["host"]
    need(native["ExitCode"] == 0 and native["Stderr"] == "", "NATIVE_STREAMS")
    value = _decode_marker(native["Stdout"], "CANONICAL_PREFIX_STAGE_COMPLETE ")
    _keys(value, ("schema", "mode", "runId", "sourceManifestSha256", "wireSha256",
                  "scope", "layoutQualification", "index", "wrapper", "aggregateBytes"),
          "NATIVE_TRAILER_FIELDS")
    need(value["schema"] == NATIVE_SCHEMA and value["mode"] == mode
         and value["runId"] == info["runId"]
         and value["sourceManifestSha256"] == pins_sha and value["wireSha256"] == WIRE_SHA
         and value["scope"] == SCOPE and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "NATIVE_TRAILER_IDENTITY")
    index_ref = value["index"]
    _keys(index_ref, ("path", "bytes", "sha256"), "TRAILER_INDEX_FIELDS")
    need(index_ref["path"] == "canonical-stage-index.json", "TRAILER_INDEX_PATH")
    _int(index_ref["bytes"], 1, LIMITS["indexBytes"], "TRAILER_INDEX_BYTES")
    _digest(index_ref["sha256"], "TRAILER_INDEX_SHA")
    wrapper = value["wrapper"]
    _keys(wrapper, ("preparationNanos", "graphExportNanos", "postNanos", "elapsedNanos",
                    "workNanos", "groups"), "WRAPPER_FIELDS")
    for key in ("preparationNanos", "graphExportNanos", "postNanos"):
        _int(wrapper[key], 0, LIMITS["unitNanos"], "WRAPPER_" + key.upper())
    elapsed = _int(wrapper["elapsedNanos"], 1, LIMITS["workNanos"], "WRAPPER_ELAPSED")
    work = _int(wrapper["workNanos"], 0, LIMITS["workNanos"], "WRAPPER_WORK")
    need(type(wrapper["groups"]) is int and wrapper["groups"] == 3
         and wrapper["preparationNanos"] + wrapper["graphExportNanos"] + wrapper["postNanos"]
             <= work <= elapsed
         and work == info["workNanos"]
         and elapsed <= host["nativeWallMs"] * 1_000_000,
         "WRAPPER_ACCOUNTING")
    aggregate = _int(value["aggregateBytes"], 0, LIMITS["artifactBytes"], "NATIVE_AGGREGATE")
    need(len(native["Stdout"].encode("utf-8")) <= LIMITS["artifactBytes"],
         "NATIVE_STDOUT_CAP")
    return value


def _stage_index(raw: bytes, value: Any, mode: str, info: Dict[str, Any], pins_sha: str,
                 p5: Any, parent: Dict[str, Any]) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    _keys(value, ("schema", "mode", "profile", "kind", "runId", "sourceManifestSha256",
                  "wireSha256", "scope", "layoutQualification", "target", "operation",
                  "transport", "attribution", "stages", "selection"), "INDEX_FIELDS")
    need(value["schema"] == INDEX_SCHEMA and value["mode"] == mode
         and value["profile"] == "BASE" and value["kind"] == "AUTHORED"
         and value["runId"] == info["runId"]
         and value["sourceManifestSha256"] == pins_sha and value["wireSha256"] == WIRE_SHA
         and value["scope"] == SCOPE
         and value["layoutQualification"] is False
         and type(value["layoutQualification"]) is bool, "INDEX_IDENTITY")
    _keys(value["target"], ("receiptBranchOrdinal", "lineageBranchOrdinal"), "INDEX_TARGET_FIELDS")
    need(value["target"] == {"receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77},
         "INDEX_TARGET")
    stage_ref = value["stages"]
    _keys(stage_ref, ("path", "bytes", "sha256"), "INDEX_STAGES_REF_FIELDS")
    need(stage_ref["path"] == "prefix-stages.json", "INDEX_STAGES_PATH")
    _int(stage_ref["bytes"], 1, LIMITS["stageBytes"], "INDEX_STAGES_BYTES")
    _digest(stage_ref["sha256"], "INDEX_STAGES_SHA")
    projection = _p5_index_projection(value, p5)
    try:
        entry = p5._index(raw, projection, mode="CAPTURE", run_id=info["runId"],
                          pins_sha=pins_sha, p4_reader=parent["context"]["p4Reader"])
    except Exception as error:
        raise Rejected("INDEX_P5_PROJECTION_" + str(error)[:48]) from None
    return entry, stage_ref


def _p5_index_projection(value: Dict[str, Any], p5: Any) -> Dict[str, Any]:
    need(type(value) is dict and value.get("scope") == SCOPE, "INDEX_OUTER_SCOPE")
    projection = dict(value)
    projection.pop("stages")
    projection["schema"] = p5.INDEX_SCHEMA
    projection["mode"] = "CAPTURE"
    projection["scope"] = P5_INDEX_SCOPE
    return projection


def _success_run(run_root: Path, info: Dict[str, Any], mode: str, pins_sha: str,
                 pins: Dict[str, Any], closure: Dict[str, Any], parent: Dict[str, Any],
                 p5: Any) -> Dict[str, Any]:
    native = info["native"]
    trailer = _native_trailer(info, mode, pins_sha)
    data_root = _child(run_root, "data")
    index_path = _child(data_root, "canonical-stage-index.json")
    index_raw = _stable_read(index_path, LIMITS["indexBytes"])
    need(index_raw.endswith(b"\n") and b"\r" not in index_raw
         and index_raw.count(b"\n") == 1, "INDEX_ENCODING")
    index = strict_json(index_raw[:-1])
    need(type(index) is dict, "INDEX_OBJECT")
    entry, stage_ref = _stage_index(index_raw, index, mode, info, pins_sha, p5, parent)
    trailer_ref = trailer["index"]
    need(trailer_ref["path"] == "canonical-stage-index.json"
         and trailer_ref["bytes"] == len(index_raw)
         and trailer_ref["sha256"] == _sha(index_raw), "TRAILER_INDEX_BINDING")
    operation_ref = entry["receipt"]
    receipt_raw = _stable_read(_child(data_root, operation_ref["path"]), 8 * 1024 * 1024)
    need(len(receipt_raw) == operation_ref["bytes"]
         and _sha(receipt_raw) == operation_ref["sha256"], "P6_OPERATION_HASH")
    context = parent["context"]
    try:
        operation = context["p4Reader"]._operation(
            receipt_raw, entry, info["runId"], pins_sha, context["branchReader"],
        )
    except Exception as error:
        raise Rejected("P6_OPERATION_" + str(error)[:48]) from None
    wrapper = trailer["wrapper"]
    need(wrapper["workNanos"] == info["workNanos"]
         and wrapper["elapsedNanos"] - wrapper["workNanos"] == entry["cleanup"]["elapsedNanos"],
         "WRAPPER_CLEANUP_BINDING")
    groups = operation["groups"]
    need(len(groups) + 3 <= LIMITS["groups"]
         and max(row["elapsedBeforeOwnLineNanos"] for row in groups) <= LIMITS["unitNanos"]
         and sum(row["elapsedBeforeOwnLineNanos"] for row in groups) <= entry["elapsedToCloseNanos"]
         and max(row["elapsedBeforeOwnLineNanos"] for row in groups)
             <= entry["maxFullGroupNanos"], "OPERATION_GROUP_BUDGET")
    need(wrapper["graphExportNanos"] > 0, "GRAPH_EXPORT_MODE")

    transport_ref = index.get("transport")
    attribution_ref = index.get("attribution")
    need(type(transport_ref) is dict and type(attribution_ref) is dict,
         "INDEX_SIDECAR_REFS")
    transport_raw = _stable_read(_child(data_root, transport_ref["path"]),
                                 LIMITS["artifactBytes"])
    attribution_raw = _stable_read(_child(data_root, attribution_ref["path"]), 65536)
    stages_raw = _stable_read(_child(data_root, stage_ref["path"]), LIMITS["stageBytes"])
    need(len(transport_raw) == transport_ref["bytes"]
         and _sha(transport_raw) == transport_ref["sha256"]
         and len(attribution_raw) == attribution_ref["bytes"]
         and _sha(attribution_raw) == attribution_ref["sha256"]
         and len(stages_raw) == stage_ref["bytes"] and _sha(stages_raw) == stage_ref["sha256"],
         "P6_SIDECAR_HASH")
    try:
        facts, diagnosis = context["p4Reader"]._capture_transport(
            transport_raw, index, operation, info["runId"], pins_sha,
        )
        decoded = context["p4Reader"].decode_transport(transport_raw, True)
    except Exception as error:
        raise Rejected("P6_CAPTURE_TRANSPORT_" + str(error)[:48]) from None
    need(diagnosis.get("status") == "ZERO_VIA_ROUTE_EXCLUDED"
         and facts.get("prefixGeometrySha256") == PREFIX_SHA
         and index["transport"]["path"] == "fixed-prefix.jsonl"
         and index["transport"]["bytes"] == P5_CAPTURE_TRANSPORT_BYTES
         and _sha(decoded["graphBytes"]) == GRAPH_SHA
         and decoded["graphBytes"] == context["p4GraphBytes"], "P6_GRAPH_BINDING")
    try:
        cut_document = p5._validate_cut_document(
            Path(context["p5Pins"]["cutInputPath"]), P5_INPUT_SHA, decoded["graph"], facts,
        )
        attribution = strict_json(attribution_raw[:-1])
        blocker_totals = p5._validate_attribution(
            attribution_raw, attribution, "CAPTURE",
            {"runId": info["runId"], "workNanos": info["workNanos"]}, facts,
            operation, context, closure, pins_sha, cut_document,
        )
    except Exception as error:
        raise Rejected("P6_BLOCKER_ATTRIBUTION_" + str(error)[:48]) from None
    need(blocker_totals["copyUnits"] == attribution["totals"]["copyUnits"]
         and blocker_totals["comparisonUnits"] == attribution["totals"]["comparisonUnits"],
         "P6_BLOCKER_TOTAL_BINDING")
    try:
        p5._validate_capture_parity(
            {"receipt": receipt_raw, "facts": facts, "decodedTransport": decoded},
            {"receipt": parent["control"]["receipt"]}, context,
        )
        capture_route = context["p4Reader"]._compare_capture(
            receipt_raw, parent["capture"]["receipt"], context["branchReader"],
        )
    except Exception as error:
        raise Rejected("P6_PARENT_ROUTE_PARITY_" + str(error)[:48]) from None
    need(capture_route.get("p07OwnerStepsDelta") == 0, "P6_P5_CAPTURE_ROUTE_EQUALITY")
    p5_facts = dict(parent["capture"]["facts"])
    current_facts = dict(facts)
    for key in ("runId", "sourceManifestSha256", "captureActiveElapsedMillis"):
        p5_facts.pop(key, None)
        current_facts.pop(key, None)
    need(_same(current_facts, p5_facts), "P6_PARENT_FACTS_PARITY")

    stage_payload = strict_json(stages_raw[:-1])
    stage_totals = _validate_stage_payload(
        stages_raw, stage_payload, mode, info["runId"], pins_sha, facts, operation,
        attribution, blocker_totals, cut_document, info["workNanos"], p5,
        context["branchReader"],
    )
    _data_files(p5, data_root, {"canonical-stage-index.json", "op-1.jsonl",
                                "fixed-prefix.jsonl", "prefix-blockers.json",
                                "prefix-stages.json"})
    aggregate = len(index_raw) + len(receipt_raw) + len(transport_raw) + len(attribution_raw) + len(stages_raw)
    need(aggregate <= LIMITS["artifactBytes"] and trailer["aggregateBytes"] == aggregate,
         "P6_ARTIFACT_AGGREGATE")
    need(wrapper["preparationNanos"] + wrapper["graphExportNanos"] + wrapper["postNanos"]
         <= wrapper["workNanos"], "P6_WRAPPER_PHASES")
    return {"info": info, "trailer": trailer, "index": index, "entry": entry,
            "receipt": receipt_raw, "operation": operation, "transportRaw": transport_raw,
            "decodedTransport": decoded, "facts": facts, "attributionRaw": attribution_raw,
            "attribution": attribution, "blockerTotals": blocker_totals,
            "cutDocument": cut_document, "stagesRaw": stages_raw, "stages": stage_payload,
            "stageTotals": stage_totals, "aggregateBytes": aggregate}


def _data_files(p5: Any, data_root: Path, expected: set) -> None:
    try:
        p5._data_files(data_root, expected)
    except Exception as error:
        raise Rejected("DATA_FILE_CENSUS_" + str(error)[:48]) from None


def _exception_marker(info: Dict[str, Any], pins_sha: str) -> Dict[str, Any]:
    native = info["native"]
    need(native["ExitCode"] != 0 and native["Stdout"] == "", "CALLBACK_THROW_STREAMS")
    stderr = native["Stderr"]
    marker = "CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW "
    need(type(stderr) is str and stderr.count(marker) == 1 and stderr.startswith(marker),
         "CALLBACK_THROW_MARKER_POSITION")
    first, sep, _tail = stderr.partition("\n")
    need(bool(sep), "CALLBACK_THROW_MARKER_END")
    original = first + "\n"
    if first.endswith("\r"):
        original = first + "\n"
        first = first[:-1]
    need(len(original.encode("utf-8")) <= LIMITS["lineBytes"], "CALLBACK_THROW_MARKER_SIZE")
    payload = strict_json(first[len(marker):].encode("utf-8"))
    _keys(payload, ("schema", "mode", "runId", "sourceManifestSha256", "wireSha256",
                    "scope", "layoutQualification", "exceptionType", "exceptionMessage",
                    "partialReceipt", "stages", "inclusiveWorkNanos", "cleanupAccounting"),
          "CALLBACK_THROW_FIELDS")
    need(payload["schema"] == EXCEPTION_SCHEMA and payload["mode"] == "CALLBACK_THROW"
         and payload["runId"] == info["runId"] and payload["sourceManifestSha256"] == pins_sha
         and payload["wireSha256"] == WIRE_SHA and payload["scope"] == SCOPE
         and payload["layoutQualification"] is False
         and type(payload["layoutQualification"]) is bool
         and payload["exceptionType"] == "java.lang.IllegalStateException"
         and payload["exceptionMessage"] == "CANONICAL_PREFIX_STAGE_CALLBACK_THROW"
         and payload["cleanupAccounting"] == "FULL_NATIVE_WALL_PLUS_POST_SOURCE",
         "CALLBACK_THROW_IDENTITY")
    work = _int(payload["inclusiveWorkNanos"], 1, LIMITS["workNanos"],
                "CALLBACK_THROW_WORK")
    need(work == info["workNanos"] and work == info["host"]["wrapperWorkNanos"],
         "CALLBACK_THROW_WORK_BINDING")
    ref = payload["partialReceipt"]
    _keys(ref, ("path", "bytes", "sha256"), "CALLBACK_THROW_PARTIAL_REF_FIELDS")
    need(ref["path"] == "op-1.jsonl", "CALLBACK_THROW_PARTIAL_PATH")
    _int(ref["bytes"], 1, LIMITS["artifactBytes"], "CALLBACK_THROW_PARTIAL_BYTES")
    _digest(ref["sha256"], "CALLBACK_THROW_PARTIAL_SHA")
    return payload


def _complete_rows_through_branch(p5: Any, raw: bytes, branch_reader: Any) -> List[Dict[str, Any]]:
    try:
        rows = p5._receipt_rows(raw, branch_reader)
    except Exception as error:
        raise Rejected("COMPLETE_RECEIPT_PARSE_" + str(error)[:48]) from None
    positions = [index for index, row in enumerate(rows)
                 if row.get("record") == "BRANCH" and row.get("ordinal") == 307]
    need(len(positions) == 1, "COMPLETE_TARGET_BRANCH")
    return rows[:positions[0] + 1]


def _exception_run(run_root: Path, info: Dict[str, Any], pins: Dict[str, Any], pins_sha: str,
                   parent: Dict[str, Any], p5: Any) -> Dict[str, Any]:
    marker = _exception_marker(info, pins_sha)
    data_root = _child(run_root, "data")
    _data_files(p5, data_root, {"op-1.jsonl"})
    partial_raw = _stable_read(_child(data_root, "op-1.jsonl"), LIMITS["artifactBytes"])
    ref = marker["partialReceipt"]
    need(len(partial_raw) == ref["bytes"] and _sha(partial_raw) == ref["sha256"],
         "CALLBACK_THROW_PARTIAL_HASH")
    try:
        prefix_raw, rows = p5._receipt_prefix_through_branch(
            partial_raw, parent["context"]["branchReader"],
        )
    except Exception as error:
        raise Rejected("CALLBACK_THROW_PARTIAL_PARSE_" + str(error)[:48]) from None
    need(prefix_raw and all(row.get("record") != "FOOTER" for row in rows),
         "CALLBACK_THROW_PARTIAL_NOT_COMPLETE")
    branch = parent["context"]["branchReader"]
    try:
        p5._compare_partial_to_control(
            rows, parent["control"]["receipt"], info, pins_sha, branch,
        )
        stage_payload = marker["stages"]
        stage_totals = _validate_stage_payload(
            None, stage_payload, "CALLBACK_THROW", info["runId"], pins_sha,
            parent["capture"]["facts"], parent["capture"]["operation"],
            parent["capture"]["attribution"], parent["capture"]["attributionTotals"],
            parent["capture"]["cutDocument"], info["workNanos"], p5, branch,
        )
    except Exception as error:
        if isinstance(error, Rejected):
            raise
        raise Rejected("CALLBACK_THROW_VALIDATION_" + str(error)[:48]) from None
    try:
        null_raw = _stable_read(Path(pins["nullRunRoot"]) / "data/op-1.jsonl",
                                LIMITS["artifactBytes"])
        null_rows = _complete_rows_through_branch(p5, null_raw, branch)
        p5._compare_partial_to_control(rows, null_raw, info, pins_sha, branch)
        need(_same(p5._non_group_prefix(rows), p5._non_group_prefix(null_rows)),
             "CALLBACK_THROW_NULL_ROUTE_PREFIX")
        parent_capture_rows = _complete_rows_through_branch(
            p5, parent["capture"]["receipt"], branch,
        )
        need(_same(p5._non_group_prefix(rows), p5._non_group_prefix(parent_capture_rows)),
             "CALLBACK_THROW_P5_CAPTURE_PREFIX")
    except Exception as error:
        if isinstance(error, Rejected):
            raise
        raise Rejected("CALLBACK_THROW_PREFIX_PARITY_" + str(error)[:48]) from None
    return {"info": info, "marker": marker, "partialRaw": partial_raw,
            "partialRows": rows, "prefixRaw": prefix_raw,
            "stages": marker["stages"], "stageTotals": stage_totals}


def _load_run(run_root: Path, mode: str, pins_path: Path, pins: Dict[str, Any], pins_sha: str,
              closure: Dict[str, Any], parent: Dict[str, Any], p5: Any) -> Dict[str, Any]:
    info = _validate_host(run_root, pins_path, pins, pins_sha, mode, closure, p5)
    if mode == "CANARY":
        _data_files(p5, _child(run_root, "data"), set())
        return {"info": info, "canary": _canary(info)}
    if mode == "CALLBACK_THROW":
        return _exception_run(run_root, info, pins, pins_sha, parent, p5)
    return _success_run(run_root, info, mode, pins_sha, pins, closure, parent, p5)


def _normalized_facts(value: Dict[str, Any]) -> Dict[str, Any]:
    result = dict(value)
    for key in ("runId", "sourceManifestSha256", "captureActiveElapsedMillis"):
        result.pop(key, None)
    return result


def _normalized_p5_attribution(value: Dict[str, Any]) -> Dict[str, Any]:
    result = json.loads(json.dumps(value))
    result.pop("runId", None)
    result.pop("sourceManifestSha256", None)
    result["totals"].pop("elapsedNanos", None)
    result["totals"].pop("maximumUnitNanos", None)
    return result


def _normalized_stage(value: Dict[str, Any]) -> Dict[str, Any]:
    result = json.loads(json.dumps(value))
    for key in ("runId", "sourceManifestSha256", "mode", "status"):
        result.pop(key, None)
    for row in result["queries"]:
        row.pop("events", None)
    for key in ("callbackQueryCount", "eventCount", "attachments", "detachments"):
        result["totals"].pop(key, None)
    result["totals"].pop("elapsedNanos", None)
    result["totals"].pop("maximumUnitNanos", None)
    return result


def _assert_stage_parity(left: Dict[str, Any], right: Dict[str, Any]) -> None:
    need(_same(_normalized_stage(left), _normalized_stage(right)), "CROSS_MODE_STAGE_PARITY")


def _compare_capture_runs(left: Dict[str, Any], right: Dict[str, Any],
                          p5: Any, parent: Dict[str, Any]) -> None:
    context = parent["context"]
    try:
        accounting = context["p4Reader"]._compare_capture(
            left["receipt"], right["receipt"], context["branchReader"],
        )
    except Exception as error:
        raise Rejected("CROSS_MODE_ROUTE_PROJECTION_" + str(error)[:48]) from None
    need(accounting.get("p07OwnerStepsDelta") == 0, "CROSS_MODE_ROUTE_DELTA")
    need(left["decodedTransport"]["graphBytes"] == right["decodedTransport"]["graphBytes"]
         and _sha(left["decodedTransport"]["graphBytes"]) == GRAPH_SHA
         and _same(_normalized_facts(left["facts"]), _normalized_facts(right["facts"])),
         "CROSS_MODE_GRAPH_FACTS")
    need(_same(_normalized_p5_attribution(left["attribution"]),
               _normalized_p5_attribution(right["attribution"])),
         "CROSS_MODE_BLOCKER_COUNTER_PARITY")
    _assert_stage_parity(left["stages"], right["stages"])


def _compare_throw_to_null(thrown: Dict[str, Any], null_run: Dict[str, Any],
                           p5: Any, parent: Dict[str, Any]) -> None:
    context = parent["context"]
    branch = context["branchReader"]
    try:
        null_rows = _complete_rows_through_branch(p5, null_run["receipt"], branch)
    except Exception as error:
        raise Rejected("THROW_NULL_RECEIPT_PARSE_" + str(error)[:48]) from None
    need(_same(p5._non_group_prefix(thrown["partialRows"]),
               p5._non_group_prefix(null_rows)), "THROW_NULL_ROUTE_PREFIX")
    throw_stage = thrown["stages"]
    null_stage = null_run["stages"]
    need(throw_stage["prefixGeometrySha256"] == null_stage["prefixGeometrySha256"] == PREFIX_SHA
         and throw_stage["reachedPthPadIds"] == null_stage["reachedPthPadIds"],
         "THROW_NULL_GEOMETRY_PARITY")
    counters = ("routerCongestionBefore", "routerCongestionAfter", "routerCongestionDelta",
                "workCongestionBefore", "workCongestionAfter", "workCongestionDelta",
                "conflictsBefore", "conflictsAfter", "conflictsDelta")
    need(len(throw_stage["queries"]) == 1 and throw_stage["queries"][0]["ordinal"] == 0
         and throw_stage["queries"][0]["outcome"] == "EXPECTED_CALLBACK_THROW"
         and null_stage["queries"][0]["ordinal"] == 0
         and all(_same(throw_stage["queries"][0][key], null_stage["queries"][0][key])
                 for key in counters), "THROW_NULL_QUERY_COUNTER_PARITY")
    for thrown_face, null_face in zip(throw_stage["sourceProof"]["faces"],
                                      null_stage["sourceProof"]["faces"]):
        for key in ("layer", "sourceEffectsBeforeSha256", "sourceEffectsAfterSha256",
                    "liveToPristineBeforeEqual", "queryToPristineBeforeEqual",
                    "liveToPristineAfterEqual", "liveObserverNullBefore",
                    "pristineObserverNullBefore", "liveObserverNullAfter",
                    "pristineObserverNullAfter", "queryObserverNullAfter", "copyEntries",
                    "comparisonEntries", "copyUnits", "comparisonUnits"):
            need(_same(thrown_face[key], null_face[key]), "THROW_NULL_FACE_PARITY")


def _recheck_inputs(p5: Any, pinned: Dict[str, Dict[str, Any]], pins_path: Path,
                    pins_raw: bytes, closure: Dict[str, Any]) -> None:
    try:
        p5._recheck_pins(pinned, pins_path, pins_raw)
        p5._recheck_source_files(closure["currentFiles"])
        p5._recheck_source_files(closure["historicalFiles"])
    except Exception as error:
        raise Rejected("INPUT_OR_SOURCE_CHANGED_" + str(error)[:48]) from None


def validate_run(run_root: Any, pins_path: Any, pins_sha: str, mode: str) -> Dict[str, Any]:
    need(type(pins_sha) is str and HEX.fullmatch(pins_sha) is not None, "INPUT_SHA")
    need(mode in ("CANARY", "NULL", "OBSERVE", "CALLBACK_THROW"), "MODE")
    pins_path = _no_reparse(pins_path)
    run_root = _no_reparse(run_root)
    pins_raw = _stable_read(pins_path, 4 * 1024 * 1024)
    need(_sha(pins_raw) == pins_sha, "PINS_HASH")
    pins = _exact_input_fields(strict_json(pins_raw))
    p5_reader_path = Path(pins["p5ReaderPath"])
    p5_module_pin = _stable_read(p5_reader_path, 4 * 1024 * 1024)
    need(_sha(p5_module_pin) == P5_READER_SHA, "P5_READER_HASH")
    p5 = _module(p5_reader_path, P5_READER_SHA, "p6_pinned_p5_reader")
    try:
        pinned = p5._pin_map(pins)
    except Exception as error:
        raise Rejected("P6_PIN_FILES_" + str(error)[:48]) from None
    need(_same_path(Path(__file__).resolve(), Path(__file__).resolve().parent / "read_stages.py"),
         "READER_SELF_PATH")
    self_pin = _pin(None, pinned, Path(__file__).resolve())
    test_pin = _pin(None, pinned, Path(__file__).resolve().parent / "test_read_stages.py")
    need(self_pin["sha256"] == _sha(_stable_read(__file__, 4 * 1024 * 1024))
         and test_pin["bytes"] > 0, "READER_TEST_PINS")
    closure = _validate_source_closure(pins, pinned, pins_path, p5)
    closure["pinned"] = pinned
    parent_context = _p5_load_context(pins, closure, p5, pinned)
    parent = _p5_parent(pins, p5, parent_context, closure, pinned)
    _validate_zero_delta_input(pins, parent)
    temp_root = closure["temp"]
    need(_below(run_root, temp_root) and not _below(run_root, closure["repo"]),
         "RUN_ROOT_CONFINEMENT")
    null_root = Path(pins["nullRunRoot"])
    if mode == "NULL":
        need(_same_path(run_root, null_root), "NULL_RUN_ROOT_IDENTITY")
    elif mode != "CANARY":
        need(not _same_path(run_root, null_root), "NON_NULL_RUN_ROOT_IDENTITY")

    current = _load_run(run_root, mode, pins_path, pins, pins_sha, closure, parent, p5)
    if mode == "CANARY":
        result = {
            "schema": READER_SCHEMA, "status": "PASS_CANARY_ONLY", "mode": mode,
            "runId": current["info"]["runId"], "inputPinsSha256": pins_sha,
            "scope": SCOPE, "layoutQualification": False,
            "acceptance": {"route": False, "layout": False, "electrical": False, "q100": False},
            "comparison": "NOT_APPLICABLE", "cleanupUpperMs": current["info"]["cleanupUpperMs"],
            "canary": current["canary"],
        }
        _recheck_inputs(p5, pinned, pins_path, pins_raw, closure)
        return result

    if mode == "NULL":
        result = {
            "schema": READER_SCHEMA, "status": "PASS_NULL_CONTROL_ONLY", "mode": mode,
            "runId": current["info"]["runId"], "inputPinsSha256": pins_sha,
            "scope": SCOPE, "layoutQualification": False,
            "acceptance": {"route": False, "layout": False, "electrical": False, "q100": False},
            "comparison": "PASS_P5_CONTROL_CAPTURE_AND_GRAPH_PARITY",
            "cleanupUpperMs": current["info"]["cleanupUpperMs"],
            "operation": {"receiptBytes": len(current["receipt"]),
                          "groupCount": len(current["operation"]["groups"]),
                          "graphSha256": GRAPH_SHA, "queryCount": current["stageTotals"]["queryCount"],
                          "selection": current["index"]["selection"]},
            "stageTotals": current["stageTotals"],
        }
        _recheck_inputs(p5, pinned, pins_path, pins_raw, closure)
        return result

    null_info = _validate_host(null_root, pins_path, pins, pins_sha, "NULL", closure, p5)
    null_run = _success_run(null_root, null_info, "NULL", pins_sha, pins, closure, parent, p5)
    if mode == "OBSERVE":
        _compare_capture_runs(current, null_run, p5, parent)
        result = {
            "schema": READER_SCHEMA, "status": "PASS_STAGE_OBSERVATIONS_ONLY", "mode": mode,
            "runId": current["info"]["runId"], "inputPinsSha256": pins_sha,
            "scope": SCOPE, "layoutQualification": False,
            "acceptance": {"route": False, "layout": False, "electrical": False, "q100": False},
            "comparison": "PASS_NULL_P5_CAPTURE_AND_CONTROL_PARITY",
            "cleanupUpperMs": current["info"]["cleanupUpperMs"],
            "operation": {"receiptBytes": len(current["receipt"]),
                          "groupCount": len(current["operation"]["groups"]),
                          "graphSha256": GRAPH_SHA, "queryCount": current["stageTotals"]["queryCount"],
                          "selection": current["index"]["selection"]},
            "stageTotals": current["stageTotals"],
        }
        _recheck_inputs(p5, pinned, pins_path, pins_raw, closure)
        return result

    need(mode == "CALLBACK_THROW", "MODE_DISPATCH")
    _compare_throw_to_null(current, null_run, p5, parent)
    result = {
        "schema": READER_SCHEMA, "status": "PASS_EXPECTED_CALLBACK_THROW_CLEANUP_ONLY",
        "mode": mode, "runId": current["info"]["runId"], "inputPinsSha256": pins_sha,
        "scope": SCOPE, "layoutQualification": False,
        "acceptance": {"route": False, "layout": False, "electrical": False, "q100": False},
        "comparison": "PASS_NULL_AND_P5_CONTROL_PREFIX_THROUGH_BRANCH_307",
        "cleanupUpperMs": current["info"]["cleanupUpperMs"],
        "partialOperation": {"receiptBytes": len(current["partialRaw"]),
                              "prefixBytesThroughBranch307": len(current["prefixRaw"]),
                              "footerPresent": False,
                              "exceptionStage": current["stages"]["exceptionStage"]},
        "stageTotals": current["stageTotals"],
    }
    _recheck_inputs(p5, pinned, pins_path, pins_raw, closure)
    return result


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 4:
        result = {"schema": READER_SCHEMA, "status": "REJECTED",
                  "error": "ARGUMENT_COUNT_INVALID",
                  "acceptance": {"route": False, "layout": False,
                                 "electrical": False, "q100": False}}
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 2
    try:
        result = validate_run(args[0], args[1], args[2], args[3])
    except Rejected as error:
        result = {"schema": READER_SCHEMA, "status": "REJECTED",
                  "error": (str(error) or "REJECTED")[:96],
                  "acceptance": {"route": False, "layout": False,
                                 "electrical": False, "q100": False}}
        print(json.dumps(result, ensure_ascii=True, sort_keys=True, separators=(",", ":")))
        return 2
    except Exception:
        result = {"schema": READER_SCHEMA, "status": "REJECTED",
                  "error": "READER_INTERNAL_REJECTION",
                  "acceptance": {"route": False, "layout": False,
                                 "electrical": False, "q100": False}}
        print(json.dumps(result, ensure_ascii=True, sort_keys=True, separators=(",", ":")))
        return 2
    print(json.dumps(result, ensure_ascii=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
