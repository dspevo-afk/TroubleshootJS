"""Offline validation for the normal-packing r2 final serial gates."""
import argparse
import json
import math
import re
from pathlib import Path


HERE = Path(__file__).resolve().parent
R2 = HERE.parent
ROOT = HERE.parents[4]
PHASES = ("contracts", "matrix", "build")
EXPECTED_CONTRACTS = (
    ("P03PlacementContractTest", "P03 placement contracts "),
    ("PcbCompactionContractTest", "PCB compaction contracts "),
    ("MediumBoardFloorplanningContractTest", "medium board floorplanning contracts "),
    ("A10DependencyContractTest", "A10 dependency contracts "),
    ("U04SessionContractTest", "U04 session contracts "),
    ("U06SessionSaveContractTest", "U06 session save contracts "),
    ("RelBSessionIdentityContractTest", "REL-B session identity contracts "),
    ("PlayerSessionFingerprintContractTest", "PlayerSessionFingerprintContractTest assertions="),
    ("ControlledIndicatorAssemblyContractTest", "current controlled-indicator contracts "),
    ("Rb30PhysicalMetadataContractTest", "Q30 physical metadata contracts "),
    ("QuickPlayGateContractTest", "Quick Play gate contracts "),
    ("QuickPlayContractTest", "Quick Play current seed envelopes and construction"),
    ("PcbCompactnessContractTest", "actual RB56 geometry report "),
    ("PcbLayerRoutingResumptionContractTest", "Pcb layer routing resumption contracts "),
    ("MediumBoardNormalAdmissionContractTest", "medium normal admission contracts "),
)
EXPECTED_SMALL = (
    "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR",
    "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT",
    "SENSOR_CONTROL", "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR",
)


class ValidationError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise ValidationError(message)


def read_json(path):
    require(path.is_file(), "missing JSON artifact: " + path.name)
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as exc:
        raise ValidationError("invalid JSON artifact: " + path.name) from exc


def read_text(path):
    require(path.is_file(), "missing text artifact: " + path.name)
    try:
        return path.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError) as exc:
        raise ValidationError("unreadable text artifact: " + path.name) from exc


def check_manifest():
    value = read_json(HERE / "suites.json")
    require(value.get("schema") == 1, "final suite manifest schema changed")
    contracts = value.get("contracts")
    require(isinstance(contracts, list) and len(contracts) == len(EXPECTED_CONTRACTS),
            "final manifest must contain fifteen contracts")
    actual = []
    for row in contracts:
        require(isinstance(row, dict), "contract manifest row is not an object")
        name, marker = row.get("name"), row.get("marker")
        require(isinstance(name, str) and isinstance(marker, str) and marker and
                "\n" not in marker and "\r" not in marker,
                "contract manifest row is malformed")
        actual.append((name, marker))
    require(tuple(actual) == EXPECTED_CONTRACTS, "final contract order or marker changed")
    for name, _ in EXPECTED_CONTRACTS:
        require((ROOT / "tests" / "contracts" / (name + ".java")).is_file(),
                "final contract source is missing: " + name)
    matrix = value.get("matrix")
    require(isinstance(matrix, dict), "matrix manifest is missing")
    require(matrix.get("name") == "QuickPlayPhysicalMatrixContractTest" and
            tuple(matrix.get("smallFamilies", ())) == EXPECTED_SMALL and
            matrix.get("smallBudgetMillis") == 180000 and
            matrix.get("mediumFamily") == "RB30_CONTROL" and
            matrix.get("mediumBudgetMillis") == 600000 and
            matrix.get("accepted") == 220 and matrix.get("exactReplays") == 220,
            "physical matrix manifest changed")
    build = value.get("build")
    require(isinstance(build, dict) and build.get("style") == "OBF" and
            build.get("target") == "Compile" and
            build.get("processTimeoutSeconds") == 900 and
            build.get("requiredPermutations") == 5,
            "build manifest changed")
    return value


def check_routes_assessment():
    value = read_json(R2 / "routes-assessment.json")
    require(value.get("phase") == "routes", "routes assessment phase is not routes")
    for field in ("assessmentStatus", "integrityStatus", "nativeStatus", "fitnessStatus"):
        require(value.get(field) == "PASS", "routes assessment is not PASS: " + field)
    require(isinstance(value.get("scope"), str) and value["scope"],
            "routes assessment scope is missing")


def _phase_path(phase, suffix):
    return HERE / (phase + suffix)


def check_private_text(text, label):
    root = str(ROOT.resolve())
    variants = {root, root.replace("\\", "/"), root.replace("/", "\\")}
    for variant in variants:
        require(variant not in text and variant.lower() not in text.lower(),
                "private worktree prefix remains in " + label)
    # The runner intentionally replaces user-profile prefixes.  This catches
    # an unrecognised user-profile path without naming a particular account.
    require(not re.search(r"(?i)[A-Z]:[\\/]Users[\\/]", text),
            "unredacted user-profile path remains in " + label)


def check_input_audit(phase, side):
    text = read_text(_phase_path(phase, "-input-audit-" + side + ".txt"))
    require(re.search(r"(?m)^PASS audit-candidate exact sources=1481 deltas=39 gates=14\r?$", text),
            "candidate input audit did not pass: " + phase + " " + side)
    require("FAIL" not in text, "candidate input audit contains FAIL: " + phase + " " + side)
    check_private_text(text, phase + " input audit " + side)


def check_launch(phase):
    value = read_json(_phase_path(phase, "-process-launch.json"))
    require(value.get("schema") == 1 and value.get("phase") == phase,
            "launch record schema/phase mismatch: " + phase)
    rows = value.get("drivers")
    require(isinstance(rows, list) and len(rows) == 2,
            "launch record must contain parent and current drivers: " + phase)
    require(tuple(row.get("role") for row in rows) == ("parent", "current"),
            "launch driver order changed: " + phase)
    for row in rows:
        require(isinstance(row.get("pid"), int) and row["pid"] > 0,
                "invalid launch pid: " + phase)
        require(isinstance(row.get("startTicks"), str) and row["startTicks"].isdigit() and
                int(row["startTicks"]) > 0, "invalid launch birth ticks: " + phase)
        require(isinstance(row.get("startUtc"), str) and row["startUtc"],
                "missing launch birth UTC: " + phase)
        require(isinstance(row.get("executable"), str) and row["executable"] and
                "\\" not in row["executable"] and "/" not in row["executable"],
                "launch executable is not a basename: " + phase)
    check_private_text(json.dumps(value), phase + " launch")
    return value


def check_cleanup(phase):
    value = read_json(_phase_path(phase, "-process-cleanup.json"))
    require(value.get("schema") == 1 and value.get("phase") == phase and
            value.get("status") == "PASS" and value.get("terminationAttempted") is False,
            "cleanup record failed: " + phase)
    rows = value.get("drivers")
    require(isinstance(rows, list) and len(rows) == 2,
            "cleanup driver count changed: " + phase)
    require(tuple(row.get("role") for row in rows) == ("parent", "current"),
            "cleanup driver order changed: " + phase)
    launch = read_json(_phase_path(phase, "-process-launch.json"))
    launch_rows = launch.get("drivers")
    require(isinstance(launch_rows, list) and len(launch_rows) == 2,
            "cleanup launch comparison is missing: " + phase)
    for row, launch_row in zip(rows, launch_rows):
        require(row.get("pid") == launch_row.get("pid") and
                row.get("startTicks") == launch_row.get("startTicks") and
                row.get("executable") == launch_row.get("executable"),
                "cleanup birth identity differs from launch: " + phase)
        require(row.get("outcome") in ("ABSENT", "DIFFERENT_PROCESS"),
                "cleanup retains a live/unknown process: " + phase)
        require(row.get("sameCreationStillRunning") is False and
                row.get("birthRevalidated") in (True, False) and
                row.get("executableRevalidated") in (True, False),
                "cleanup birth fields malformed: " + phase)
    check_private_text(json.dumps(value), phase + " cleanup")
    return value


def check_timing(phase, expected_count):
    value = read_json(_phase_path(phase, "-timing.json"))
    require(value.get("schema") == 1 and value.get("status") == "PASS" and
            value.get("exitCode") == 0 and value.get("phase") == phase,
            "timing record failed: " + phase)
    elapsed = value.get("elapsedSeconds")
    require(isinstance(elapsed, (int, float)) and not isinstance(elapsed, bool) and
            math.isfinite(elapsed) and elapsed >= 0, "invalid phase elapsed time: " + phase)
    require(value.get("suiteCount") == expected_count and
            value.get("terminalCleanupIncluded") is True and
            value.get("cleanupDuration") == "NOT_SEPARATELY_MEASURED",
            "timing boundary changed: " + phase)
    check_private_text(json.dumps(value), phase + " timing")
    return value


def receipt_is_in_log(receipt, log, phase):
    receipt_lines = [line for line in receipt.splitlines() if line]
    log_lines = [line for line in log.splitlines() if line]
    cursor = 0
    for expected in receipt_lines:
        try:
            cursor = log_lines.index(expected, cursor) + 1
        except ValueError as exc:
            raise ValidationError("receipt line is absent from full log: " + phase) from exc


def check_common_phase(phase, expected_count):
    check_input_audit(phase, "before")
    check_input_audit(phase, "after")
    check_launch(phase)
    check_timing(phase, expected_count)
    check_cleanup(phase)
    log = read_text(_phase_path(phase, "-log.txt"))
    require(log, "phase log is empty: " + phase)
    check_private_text(log, phase + " log")
    sanitize = read_json(_phase_path(phase, "-log-sanitization.json"))
    require(sanitize.get("schema") == 1 and sanitize.get("phase") == phase and
            sanitize.get("allowedPrefixes") == ["[WORKTREE]", "[JDK8]"],
            "log sanitization record failed: " + phase)
    expected_files = [phase + "-log.txt"]
    if phase in ("contracts", "matrix"):
        expected_files.append(phase + "-receipt.txt")
    require([row.get("file") for row in sanitize.get("files", [])] == expected_files,
            "log sanitization files missing: " + phase)
    return log


def check_contracts(manifest):
    log = check_common_phase("contracts", 15)
    receipt = read_text(_phase_path("contracts", "-receipt.txt"))
    check_private_text(receipt, "contracts receipt")
    receipt_is_in_log(receipt, log, "contracts")
    for name, marker in EXPECTED_CONTRACTS:
        lines = [line for line in receipt.splitlines() if line.startswith("PASS: " + marker)]
        require(len(lines) == 1, "contract marker count is not one: " + name)
    require("PASS: focused current contracts; 15 Java suites." in receipt,
            "fifteen-suite focused summary is missing")


def check_matrix(manifest):
    log = check_common_phase("matrix", 1)
    receipt = read_text(_phase_path("matrix", "-receipt.txt"))
    check_private_text(receipt, "matrix receipt")
    receipt_is_in_log(receipt, log, "matrix")
    require("PASS: Quick Play physical matrix contracts pairs=10 assertions=" in receipt,
            "small physical matrix completion is missing")
    require("PASS: Quick Play physical matrix contracts pairs=1 assertions=" in receipt,
            "RB30 physical matrix completion is missing")
    coverage = ("PASS: physical matrix partition coverage families=11 accepted=220 "
                "exactReplays=220 smallChildBudgetMs=180000 mediumChildBudgetMs=600000")
    require(coverage in receipt, "physical matrix coverage summary is missing")
    rows = [line for line in receipt.splitlines() if line.startswith("PHYSICAL_MATRIX|")]
    require(len(rows) == 11, "physical matrix row count is not eleven")
    expected = list(EXPECTED_SMALL) + ["RB30_CONTROL"]
    for index, row in enumerate(rows):
        fields = row.split("|")
        require(len(fields) == 13 and fields[1] == expected[index] and
                fields[3] == "20" and fields[4] == "20",
                "physical matrix row identity changed: " + str(index))
        profile = "MEDIUM" if fields[1] in ("COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL") else "EASY"
        require(fields[2] == profile, "physical matrix row profile changed: " + fields[1])
        try:
            checked = int(fields[11])
        except ValueError as exc:
            raise ValidationError("physical matrix row count is malformed") from exc
        require(20 <= checked <= 48, "physical matrix row coverage bound changed")


def check_build(manifest):
    log = check_common_phase("build", 0)
    markers = (
        "Compiling module com.lushprojects.circuitjs1.circuitjs1",
        "Compiling 5 permutations",
        "Compile of permutations succeeded",
        "Compilation succeeded --",
        "Link succeeded",
        "Linking succeeded --",
    )
    for marker in markers:
        require(marker in log, "GWT build marker is missing: " + marker)
    require("BUILD_INFRASTRUCTURE" not in log and "BUILD_FAILURE" not in log,
            "GWT build log contains a failure marker")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=PHASES + ("final",), default="final")
    args = parser.parse_args(argv)
    try:
        manifest = check_manifest()
        check_routes_assessment()
        if args.phase in ("contracts", "final"):
            check_contracts(manifest)
        if args.phase == "matrix":
            check_contracts(manifest)
        if args.phase in ("matrix", "final"):
            check_matrix(manifest)
        if args.phase == "build":
            check_contracts(manifest)
            check_matrix(manifest)
        if args.phase in ("build", "final"):
            check_build(manifest)
        print("PASS normal-packing-r2 final validation artifacts phase=" + args.phase)
        return 0
    except ValidationError as exc:
        print("FAIL normal-packing-r2 final validation: " + str(exc))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
