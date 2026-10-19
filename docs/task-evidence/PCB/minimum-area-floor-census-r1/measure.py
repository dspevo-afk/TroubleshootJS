"""Offline, source-bound census of the retained candidate 4/5 sizing formula."""

from __future__ import annotations

import hashlib
import json
import math
import re
import stat
import sys
from fractions import Fraction
from pathlib import Path, PurePosixPath
from typing import Any


HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[3]
PROTOCOL_PATH = "docs/task-evidence/PCB/minimum-area-floor-census-r1/protocol.json"
BASELINE_PATH = "docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json"
BASELINE_SHA256 = "12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816"
MAIN_SEAL_PATH = "docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json"
MAIN_SEAL_SHA256 = "9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a"
NATIVE_PATH = "docs/task-evidence/PCB/post-selection-edge-trim-r1/final-native.txt"
NATIVE_SHA256 = "db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d"
NATIVE_PREFIX = "PCB_EDGE_TRIM_CASE "
NATIVE_ROW_COUNT = 14
MAIN_SEAL_FILE_COUNT = 1475

EXPECTED_ROOTS = [
    "-1017", "-1004", "-1022", "0", "17", "42", "1", "7", "5", "14", "2", "3", "4", "6"
]
EXPECTED_LIMITS = {
    "floor": 5_000_000,
    "maxArea": 64_000_000,
    "largeHeadroom": 8_000_000,
    "headroom": 1_000_000,
    "smallPartCutoff": 40,
    "additiveAllowance": 500_000,
}
SOURCE_PATHS = {
    "src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java",
    "src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java",
    "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",
    "src/com/lushprojects/circuitjs1/client/MediumBoardPhysicalPolicy.java",
}
BASE_COMMIT = "748e9c083f2bd4a4ace6289529fba94dc1aa6060"
SCOPE = "Offline retained successful raw candidate4/5 sizing arithmetic; no alternative/runtime/all-seed proof"
ACCEPTANCE = [
    "Exact baseline, main1475 and source pins before arithmetic",
    "All14 roots and six raw candidate score demands per root, no silently skipped errors",
    "Exactly28 fallback sizing rows with current Java-double and independent rational-ceil agreement",
    "Strict increase versus equality/no-op floor classified separately; no proposed lowered threshold",
    "No changed source, native/GWT/player execution or changed routing/physical/work gates",
]
SCORE_FIELDS = (
    "total", "electrical", "region", "connector", "corridor", "outline", "courtyard", "demand"
)
MAX_SCORE_VALUE = EXPECTED_LIMITS["maxArea"] * 16
MAX_DEMAND = EXPECTED_LIMITS["maxArea"] // 2

_SCORE_ENTRY_RE = re.compile(
    r"([0-5])\{total=(0|[1-9][0-9]*);electrical=(0|[1-9][0-9]*);region=(0|[1-9][0-9]*);"
    r"connector=(0|[1-9][0-9]*);corridor=(0|[1-9][0-9]*);outline=([1-9][0-9]*);"
    r"courtyard=([1-9][0-9]*);demand=([1-9][0-9]*)\}"
)
_HEX_SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")


def _error(message: str) -> ValueError:
    return ValueError(message)


def _reject_duplicate_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise _error(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _reject_json_constant(value: str) -> None:
    raise _error(f"non-finite JSON number is forbidden: {value}")


def _parse_json_bytes(data: bytes, label: str) -> Any:
    try:
        return json.loads(
            data.decode("utf-8"),
            object_pairs_hook=_reject_duplicate_pairs,
            parse_constant=_reject_json_constant,
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise _error(f"invalid UTF-8 JSON in {label}: {exc}") from exc


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _require_sha256(data: bytes, expected: str, label: str) -> str:
    if type(expected) is not str or _HEX_SHA256_RE.fullmatch(expected) is None:
        raise _error(f"invalid expected SHA-256 for {label}")
    actual = _sha256(data)
    if actual != expected:
        raise _error(f"SHA-256 mismatch for {label}: expected {expected}, got {actual}")
    return actual


def _is_reparse(path: Path) -> bool:
    try:
        info = path.lstat()
    except OSError as exc:
        raise _error("cannot inspect a repository path") from exc
    if stat.S_ISLNK(info.st_mode):
        return True
    attributes = getattr(info, "st_file_attributes", 0)
    return bool(attributes & 0x400)  # FILE_ATTRIBUTE_REPARSE_POINT on Windows.


def _safe_repo_path(repo_root: Path, relative: str) -> Path:
    if type(relative) is not str or not relative or "\x00" in relative or "\\" in relative:
        raise _error(f"invalid repository-relative path: {relative!r}")
    pure = PurePosixPath(relative)
    if pure.is_absolute() or pure.as_posix() != relative or any(part in (".", "..") for part in pure.parts):
        raise _error(f"unsafe repository-relative path: {relative!r}")
    try:
        root = Path(repo_root).resolve(strict=True)
    except OSError as exc:
        raise _error("cannot resolve repository root") from exc
    current = root
    if _is_reparse(root):
        raise _error("repository root is a symlink or reparse point")
    for part in pure.parts:
        current = current / part
        if _is_reparse(current):
            raise _error(f"symlink or reparse point in repository path: {relative}")
    try:
        current.resolve(strict=True).relative_to(root)
    except (OSError, ValueError) as exc:
        raise _error(f"repository path escapes the root: {relative}") from exc
    if not current.is_file():
        raise _error(f"repository path is not a regular file: {relative}")
    return current


def _read_repo_file(repo_root: Path, relative: str) -> bytes:
    path = _safe_repo_path(repo_root, relative)
    try:
        return path.read_bytes()
    except OSError as exc:
        raise _error(f"cannot read {relative}: {exc}") from exc


def _exact_keys(value: Any, keys: set[str], label: str) -> dict[str, Any]:
    if type(value) is not dict or set(value) != keys:
        raise _error(f"{label} must contain exactly these keys: {', '.join(sorted(keys))}")
    return value


def _audit_protocol(protocol: Any) -> dict[str, Any]:
    _exact_keys(
        protocol,
        {"schema", "baseCommit", "baselineRef", "mainSealRef", "sourcePins", "roots", "limits", "scope", "acceptance"},
        "protocol",
    )
    if type(protocol["schema"]) is not int or protocol["schema"] != 1:
        raise _error("protocol schema must be integer 1")
    if protocol["baseCommit"] != BASE_COMMIT:
        raise _error("protocol baseCommit does not match the assigned source commit")
    if protocol["roots"] != EXPECTED_ROOTS or type(protocol["roots"]) is not list:
        raise _error("protocol roots do not match the exact informed-root order")
    if protocol["limits"] != EXPECTED_LIMITS or type(protocol["limits"]) is not dict:
        raise _error("protocol limits differ from the assigned sizing constants")
    if protocol["scope"] != SCOPE or protocol["acceptance"] != ACCEPTANCE:
        raise _error("protocol scope or acceptance text differs from the assigned contract")
    baseline_ref = _exact_keys(protocol["baselineRef"], {"path", "sha256"}, "baselineRef")
    main_ref = _exact_keys(protocol["mainSealRef"], {"path", "sha256"}, "mainSealRef")
    if baseline_ref != {"path": BASELINE_PATH, "sha256": BASELINE_SHA256}:
        raise _error("protocol baseline reference is not the pinned accepted15 input")
    if main_ref != {"path": MAIN_SEAL_PATH, "sha256": MAIN_SEAL_SHA256}:
        raise _error("protocol main seal reference is not the pinned main1475 input")
    pins = protocol["sourcePins"]
    if type(pins) is not dict or set(pins) != SOURCE_PATHS:
        raise _error("protocol sourcePins do not contain the exact four sealed source owners")
    for path, digest in pins.items():
        if type(path) is not str or type(digest) is not str or _HEX_SHA256_RE.fullmatch(digest) is None:
            raise _error("protocol contains a malformed source pin")
    return protocol


def _verify_source_bindings(
    repo_root: Path, source_pins: dict[str, str], sealed_files: dict[str, str]
) -> None:
    if type(source_pins) is not dict or set(source_pins) != SOURCE_PATHS:
        raise _error("source pin set is incomplete or contains unexpected files")
    for source_path in sorted(SOURCE_PATHS):
        expected = source_pins[source_path]
        if type(expected) is not str or _HEX_SHA256_RE.fullmatch(expected) is None:
            raise _error(f"malformed source pin for {source_path}")
        if sealed_files.get(source_path) != expected:
            raise _error(f"source pin is not bound to the main input seal: {source_path}")
        source_bytes = _read_repo_file(repo_root, source_path)
        _require_sha256(source_bytes, expected, source_path)


def _verify_sealed_files(repo_root: Path, sealed_files: dict[str, str], reader=None) -> int:
    if type(sealed_files) is not dict:
        raise _error("main seal file map must be an object")
    read_file = reader or _read_repo_file
    for path, expected in sealed_files.items():
        if type(path) is not str or type(expected) is not str or _HEX_SHA256_RE.fullmatch(expected) is None:
            raise _error("main seal contains a malformed path or digest")
        _require_sha256(read_file(repo_root, path), expected, path)
    return len(sealed_files)


def _audit_planner_formula(planner_bytes: bytes) -> None:
    try:
        source = planner_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise _error("PcbPlacementPlanner.java is not UTF-8") from exc
    compact = re.sub(r"\s+", "", source)
    required_fragments = (
        "staticfinalintOUTLINE_CANDIDATES=6,MAX_PARTS=128,MAX_EXTENT=16000,CHANNEL=40;",
        "staticfinallongMAX_AREA=64000000L;",
        "privatestaticfinallongMEDIUM_MIN_AREA=5000000L;",
        "booleanuseCompact=compactMediumInterpretation&&candidate<OUTLINE_CANDIDATES-2;",
        "intpartCount=board.getComponentIds().size();",
        "doublespacingFactor=3.0;if(partCount>40)spacingFactor+=Math.min(6.0,(partCount-40)*.10);",
        "if(placementVersion>=4&&candidate==4)spacingFactor=placementVersion>=5?2.0:3.0;",
        "Math.max(MEDIUM_MIN_AREA,(long)Math.ceil(demandArea*spacingFactor)+500000L)",
        "if(partCount>40)targetArea=Math.min(targetArea,MAX_AREA-8000000L);",
        "targetArea=Math.min(targetArea,MAX_AREA-1000000L);",
    )
    missing = [fragment for fragment in required_fragments if fragment not in compact]
    if missing:
        raise _error("planner sizing formula/owner bindings differ from the source-bound census contract")


def _audit_native_rows(repo_root: Path, baseline: dict[str, Any]) -> dict[str, Any]:
    if baseline.get("source") != NATIVE_PATH or baseline.get("sourceSha256") != NATIVE_SHA256:
        raise _error("accepted15 baseline does not identify the pinned post-selection native log")
    native_bytes = _read_repo_file(repo_root, NATIVE_PATH)
    _require_sha256(native_bytes, NATIVE_SHA256, NATIVE_PATH)
    try:
        native_text = native_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise _error("pinned native log is not UTF-8") from exc
    version15_rows = []
    prefix_count = 0
    for line_number, line in enumerate(native_text.splitlines(), start=1):
        if not line.startswith(NATIVE_PREFIX):
            continue
        prefix_count += 1
        row = _parse_json_bytes(line[len(NATIVE_PREFIX):].encode("utf-8"), f"{NATIVE_PATH}:{line_number}")
        if type(row) is not dict or type(row.get("version")) is not int:
            raise _error(f"malformed prefixed native row at line {line_number}")
        if row["version"] == 15:
            version15_rows.append(row)
    baseline_rows = baseline.get("rows")
    if type(baseline_rows) is not list or len(version15_rows) != NATIVE_ROW_COUNT:
        raise _error("native source does not contain exactly 14 version-15 prefixed rows")
    if version15_rows != baseline_rows:
        raise _error("version-15 native source rows differ from the accepted15 baseline rows")
    return {
        "path": NATIVE_PATH,
        "sha256": NATIVE_SHA256,
        "prefixedRows": prefix_count,
        "version15Rows": len(version15_rows),
        "matchesBaselineRows": True,
    }


def audit_repository(repo_root: Path, protocol: Any | None = None) -> tuple[dict[str, Any], dict[str, Any]]:
    """Verify the protocol, baseline provenance, main1475 seal, and pinned source owners."""
    if protocol is None:
        protocol_bytes = _read_repo_file(repo_root, PROTOCOL_PATH)
        protocol = _parse_json_bytes(protocol_bytes, PROTOCOL_PATH)
    protocol = _audit_protocol(protocol)

    baseline_bytes = _read_repo_file(repo_root, BASELINE_PATH)
    _require_sha256(baseline_bytes, BASELINE_SHA256, BASELINE_PATH)
    baseline = _parse_json_bytes(baseline_bytes, BASELINE_PATH)
    if type(baseline) is not dict or baseline.get("status") != "PINNED":
        raise _error("accepted15 baseline must be a PINNED object")
    native_audit = _audit_native_rows(repo_root, baseline)

    main_seal_bytes = _read_repo_file(repo_root, MAIN_SEAL_PATH)
    _require_sha256(main_seal_bytes, MAIN_SEAL_SHA256, MAIN_SEAL_PATH)
    main_seal = _parse_json_bytes(main_seal_bytes, MAIN_SEAL_PATH)
    if type(main_seal) is not dict or set(main_seal) != {"files"} or type(main_seal["files"]) is not list:
        raise _error("main input seal has an unexpected shape")
    if len(main_seal["files"]) != MAIN_SEAL_FILE_COUNT:
        raise _error("main input seal does not contain the expected 1475 entries")
    sealed_files: dict[str, str] = {}
    for entry in main_seal["files"]:
        _exact_keys(entry, {"path", "sha256"}, "main seal file entry")
        path, digest = entry["path"], entry["sha256"]
        if type(path) is not str or type(digest) is not str or _HEX_SHA256_RE.fullmatch(digest) is None:
            raise _error("main input seal contains a malformed file entry")
        if path in sealed_files:
            raise _error(f"duplicate path in main input seal: {path}")
        sealed_files[path] = digest

    checked_files = _verify_sealed_files(repo_root, sealed_files)
    source_pins = protocol["sourcePins"]
    _verify_source_bindings(repo_root, source_pins, sealed_files)
    planner_path = "src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java"
    planner_bytes = _read_repo_file(repo_root, planner_path)
    _audit_planner_formula(planner_bytes)

    provenance = {
        "baseCommit": protocol["baseCommit"],
        "baseline": {"path": BASELINE_PATH, "sha256": BASELINE_SHA256},
        "mainInputSeal": {
            "path": MAIN_SEAL_PATH,
            "sha256": MAIN_SEAL_SHA256,
            "files": MAIN_SEAL_FILE_COUNT,
            "checkedFiles": checked_files,
        },
        "sourcePins": dict(sorted(source_pins.items())),
        "nativeRows": native_audit,
    }
    return baseline, provenance


def _require_int(value: Any, label: str, minimum: int, maximum: int) -> int:
    if type(value) is not int or value < minimum or value > maximum:
        raise _error(f"{label} must be an integer in [{minimum}, {maximum}]")
    return value


def _canonical_field(text: str, field: str) -> str | None:
    matches = re.findall(rf"(?:^|;){re.escape(field)}=([^;]*)(?=;|$)", text)
    if text.count(field + "=") == 0:
        return None
    if len(matches) != 1:
        raise _error(f"statisticsCanonical must contain exactly one {field} field")
    return matches[0]


def _parse_ranked(text: str) -> list[int] | None:
    raw = _canonical_field(text, "ranked")
    if raw is None:
        return None
    if re.fullmatch(r"\[[0-5](?:, [0-5]){5}\]", raw) is None:
        raise _error("statisticsCanonical ranked field must list six canonical candidate ids")
    values = [int(value) for value in re.findall(r"[0-5]", raw)]
    if len(set(values)) != 6:
        raise _error("statisticsCanonical ranked field contains duplicate candidates")
    return values


def _parse_score_entries(text: str) -> tuple[dict[int, dict[str, int]], list[int]]:
    marker = "placementScores="
    if text.count(marker) != 1:
        raise _error("statisticsCanonical must contain exactly one placementScores field")
    start = text.index(marker) + len(marker)
    if start >= len(text) or text[start] != "[":
        raise _error("placementScores must start with an opening bracket")
    close = text.find("]", start + 1)
    if close < 0:
        raise _error("placementScores is missing its closing bracket")
    if close + 1 < len(text) and text[close + 1] != ";":
        raise _error("trailing garbage after placementScores closing bracket")
    body = text[start + 1:close]
    pieces = body.split(", ")
    if len(pieces) != 6:
        raise _error("placementScores must contain exactly six candidates")
    scores: dict[int, dict[str, int]] = {}
    order: list[int] = []
    for piece in pieces:
        match = _SCORE_ENTRY_RE.fullmatch(piece)
        if match is None:
            raise _error("placementScores entry does not match the strict canonical grammar")
        candidate = int(match.group(1))
        if candidate in scores:
            raise _error(f"duplicate placementScores candidate {candidate}")
        values = [int(match.group(index)) for index in range(2, 10)]
        if any(value > MAX_SCORE_VALUE for value in values):
            raise _error("placementScores contains an out-of-range score value")
        score = dict(zip(SCORE_FIELDS, values))
        for positive_field in ("outline", "courtyard"):
            _require_int(score[positive_field], f"candidate {candidate} {positive_field}", 1, MAX_SCORE_VALUE)
        _require_int(score["demand"], "candidate demand", 1, MAX_DEMAND)
        scores[candidate] = score
        order.append(candidate)
    if set(scores) != set(range(6)):
        raise _error("placementScores must contain each candidate 0 through 5 exactly once")
    return scores, order


def _parse_row(row: Any, expected_seed: str) -> tuple[dict[str, Any], dict[int, dict[str, int]]]:
    if type(row) is not dict:
        raise _error(f"baseline row for root {expected_seed} must be an object")
    if row.get("seed") != expected_seed or type(row.get("seed")) is not str:
        raise _error(f"baseline root order/identity mismatch at {expected_seed}")
    if row.get("status") != "PASS":
        raise _error(f"root {expected_seed} does not have status PASS")
    _require_int(row.get("version"), f"root {expected_seed} version", 15, 15)
    _require_int(row.get("density"), f"root {expected_seed} density", 14, 14)
    part_count = _require_int(row.get("parts"), f"root {expected_seed} parts", 20, 128)
    selected_attempt = _require_int(row.get("selectedAttempt"), f"root {expected_seed} selectedAttempt", 0, 5)
    canonical = row.get("statisticsCanonical")
    if type(canonical) is not str or not canonical:
        raise _error(f"root {expected_seed} statisticsCanonical must be a nonempty string")
    canonical_selected = _canonical_field(canonical, "selectedAttempt")
    if canonical_selected is None or re.fullmatch(r"[0-5]", canonical_selected) is None:
        raise _error(f"root {expected_seed} statisticsCanonical is missing a valid selectedAttempt")
    if int(canonical_selected) != selected_attempt:
        raise _error(f"root {expected_seed} selectedAttempt disagrees with statisticsCanonical")
    scores, score_order = _parse_score_entries(canonical)
    ranked = _parse_ranked(canonical)
    if ranked is None:
        raise _error(f"root {expected_seed} statisticsCanonical is missing ranked placement attempts")
    if selected_attempt not in ranked:
        raise _error(f"root {expected_seed} selectedAttempt is absent from ranked order")
    if score_order != ranked:
        raise _error(f"root {expected_seed} placementScores order disagrees with ranked order")
    if "rankedPlacementAttempts" in row:
        json_ranked = row["rankedPlacementAttempts"]
        if (type(json_ranked) is not list or len(json_ranked) != 6 or
                any(type(value) is not int or value < 0 or value > 5 for value in json_ranked) or
                len(set(json_ranked)) != 6 or json_ranked != ranked):
            raise _error(f"root {expected_seed} JSON rankedPlacementAttempts disagree with canonical ranking")
    return {"seed": expected_seed, "parts": part_count, "selectedAttempt": selected_attempt}, scores


def _spacing(candidate: int, part_count: int) -> tuple[float, Fraction]:
    if candidate == 4:
        return 2.0, Fraction(2, 1)
    if candidate != 5:
        raise _error("only fallback candidates 4 and 5 have a measured spacing factor")
    if part_count <= EXPECTED_LIMITS["smallPartCutoff"]:
        return 3.0, Fraction(3, 1)
    delta = part_count - EXPECTED_LIMITS["smallPartCutoff"]
    # Match Java's operation order and binary64 literals exactly.
    factor = 3.0 + min(6.0, delta * 0.10)
    exact = Fraction(3, 1) + min(Fraction(6, 1), Fraction(delta, 10))
    return factor, exact


def _ceil_exact(value: Fraction) -> int:
    return -(-value.numerator // value.denominator)


def _require_ceil_agreement(ieee_ceil: int, rational_ceil: int, seed: str, candidate: int) -> None:
    if ieee_ceil != rational_ceil:
        raise _error(
            f"root {seed} candidate {candidate} ceil disagreement: IEEE={ieee_ceil}, rational={rational_ceil}"
        )


def _measure_candidate(seed: str, part_count: int, selected_attempt: int, candidate: int, demand: int) -> dict[str, Any]:
    _require_int(demand, f"root {seed} candidate {candidate} demand", 1, MAX_DEMAND)
    factor, exact_factor = _spacing(candidate, part_count)
    ieee_ceil = math.ceil(float(demand) * factor)
    rational_ceil = _ceil_exact(Fraction(demand, 1) * exact_factor)
    _require_ceil_agreement(ieee_ceil, rational_ceil, seed, candidate)
    pre_floor = ieee_ceil + EXPECTED_LIMITS["additiveAllowance"]
    floor = EXPECTED_LIMITS["floor"]
    floor_increased = pre_floor < floor
    floored_target = max(pre_floor, floor)
    max_area = EXPECTED_LIMITS["maxArea"]
    caps: list[str] = []
    target = floored_target
    if part_count > EXPECTED_LIMITS["smallPartCutoff"]:
        large_cap = max_area - EXPECTED_LIMITS["largeHeadroom"]
        if target > large_cap:
            caps.append("large-inventory")
        target = min(target, large_cap)
    final_cap = max_area - EXPECTED_LIMITS["headroom"]
    if target > final_cap:
        caps.append("final")
    final_target = min(target, final_cap)
    return {
        "seed": seed,
        "parts": part_count,
        "selectedAttempt": selected_attempt,
        "candidate": candidate,
        "demand": demand,
        "factor": factor,
        "factorExact": f"{exact_factor.numerator}/{exact_factor.denominator}",
        "ceilPythonIeee": ieee_ceil,
        "ceilExactRational": rational_ceil,
        "preFloor": pre_floor,
        "floorAppliedStrictIncrease": floor_increased,
        "flooredTarget": floored_target,
        "capsApplied": caps,
        "finalCappedTarget": final_target,
    }


def measure_rows(data: Any) -> dict[str, Any]:
    """Pure function: strictly validate the pinned row shape and compute 28 fallback rows."""
    if type(data) is not dict or data.get("status") != "PINNED":
        raise _error("baseline input must be a PINNED object")
    rows = data.get("rows")
    if type(rows) is not list or len(rows) != len(EXPECTED_ROOTS):
        raise _error("baseline must contain exactly the 14 informed roots")
    output_rows: list[dict[str, Any]] = []
    for index, expected_seed in enumerate(EXPECTED_ROOTS):
        root_info, scores = _parse_row(rows[index], expected_seed)
        for candidate in (4, 5):
            output_rows.append(
                _measure_candidate(
                    root_info["seed"], root_info["parts"], root_info["selectedAttempt"],
                    candidate, scores[candidate]["demand"]
                )
            )
    strict_increases = sum(row["floorAppliedStrictIncrease"] for row in output_rows)
    equality_noops = sum(
        row["preFloor"] == EXPECTED_LIMITS["floor"] and not row["floorAppliedStrictIncrease"]
        for row in output_rows
    )
    ordinary_noops = len(output_rows) - strict_increases - equality_noops
    return {
        "schema": 1,
        "scope": SCOPE,
        "rootCount": len(rows),
        "candidateRowCount": len(output_rows),
        "floorCounts": {
            "strictIncrease": strict_increases,
            "equalityNoop": equality_noops,
            "aboveFloorNoop": ordinary_noops,
        },
        "rows": output_rows,
        "interpretationLimit": "Sizing arithmetic census only; it makes no route, ranking-invariance, alternative-repair, or all-seed claim.",
    }


def main() -> int:
    baseline, provenance = audit_repository(REPO_ROOT)
    census = measure_rows(baseline)
    census["provenance"] = provenance
    output_path = HERE / "census.json"
    try:
        output_path.write_text(
            json.dumps(census, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n"
        )
    except OSError as exc:
        raise _error("cannot write census.json") from exc
    print(f"PASS source-bound offline census: roots={census['rootCount']} fallbackRows={census['candidateRowCount']}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as exc:
        print(f"FAIL minimum-area-floor census: {exc}", file=sys.stderr)
        raise SystemExit(2)
