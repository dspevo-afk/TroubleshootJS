#!/usr/bin/env python3
"""Independent A01 manifest and measurement receipt validator.

The checker deliberately accepts only receipts produced by the versioned A01
route.  It verifies the electrical oracle and owner-side counters; it never
simulates a circuit itself and never treats an unsupported physical stage as a
zero-valued success.
"""
import argparse
import collections
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SIZES = (20, 40, 60, 100)
SEEDS = {"pilot": (0, 1), "holdout": (2, 3)}
STEPS_PER_ATTEMPT = 2
ATTEMPTS_PER_CORPUS = 16
EXPECTED_STEPS_PER_CORPUS = STEPS_PER_ATTEMPT * ATTEMPTS_PER_CORPUS
EXPECTED_COMBINED_STEPS = EXPECTED_STEPS_PER_CORPUS * 2
MAX_ATTEMPT_ELAPSED_MS = 5000
MAX_TOTAL_ELAPSED_MS = 30000
TRACE_INTERVAL_TOLERANCE_MS = 0
ALLOCATIONS = {
    "RB15": [4, 5, 3, 2, 1],
    "RB30": [4, 4, 8, 10, 2, 2],
    "RB56": [5, 4, 12, 4, 3, 3, 10, 8, 3, 2, 2],
    # RB100 intentionally records RB56 as one functional base allocation.
    "RB100": [56, 10, 12, 8, 4, 4, 4, 2],
}
UNSUPPORTED_SYNTHETIC = {
    "physicalPackages", "pads", "nets", "rawSegments", "canonicalSegments",
    "routing", "diagnostic", "playable",
}
COUNTER_FIELDS = (
    "analysisCount", "stampCount", "factorizationCount", "solveCount",
    "iterationCount", "subIterationCount", "acceptedStepCount",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def finite(value, name):
    require(isinstance(value, (int, float)) and not isinstance(value, bool) and
            math.isfinite(value), name + " must be finite")
    return float(value)


def integer(value, name, minimum=0):
    require(isinstance(value, int) and not isinstance(value, bool) and value >= minimum,
            name + " must be a nonnegative integer")
    return value


def validate_references(data):
    require(isinstance(data, dict) and data.get("protocol") == "TSJ-A01-REFERENCE-1",
            "reference protocol")
    boards = data.get("boards")
    require(isinstance(boards, list) and len(boards) == 4 and
            {b.get("id") for b in boards} == set(ALLOCATIONS),
            "four distinct reference boards")
    all_ids = set()
    purposeful = 0
    for board in boards:
        name = board["id"]
        target = integer(board.get("targetPackages"), "target count " + name, 1)
        require(target == int(name[2:]), "target count " + name)
        require(board.get("evidenceStage") == "ARCHITECTURE_SPECIFIED" and
                board.get("implemented") is False, "architecture-only stage " + name)
        allocation = board.get("allocations")
        require(isinstance(allocation, list) and
                [integer(item.get("count"), "allocation count") for item in allocation] ==
                ALLOCATIONS[name], "roadmap allocations " + name)
        allocation_ids = [item.get("id") for item in allocation]
        require(all(isinstance(item, str) and item.strip() for item in allocation_ids) and
                len(set(allocation_ids)) == len(allocation_ids), "duplicate allocation " + name)
        components = board.get("components")
        require(isinstance(components, list) and len(components) == target,
                "package/role count " + name)
        component_ids = [item.get("id") for item in components]
        require(all(isinstance(item, str) and item.strip() for item in component_ids) and
                len(set(component_ids)) == len(component_ids), "duplicate component " + name)
        require(not all_ids.intersection(component_ids), "component identity reused across boards")
        all_ids.update(component_ids)
        counts = collections.Counter(item.get("allocationId") for item in components)
        require(counts == dict(zip(allocation_ids, [item["count"] for item in allocation])),
                "unassigned role / allocation count " + name)
        for component in components:
            for field in ("id", "purpose", "domain", "packageRequirement",
                          "modelRequirement", "faultRequirement"):
                require(isinstance(component.get(field), str) and component[field].strip(),
                        "missing component " + field + " in " + name)
            require(not any(word in component["purpose"].lower()
                            for word in ("filler", "decorative", "unused")),
                    "inert role " + component["id"])
        unsupported = set(board.get("unsupportedStages", []))
        require(unsupported == {"generation", "routing", "solver", "diagnostic", "playable"},
                "unsupported future stages " + name)
        interfaces = board.get("interfaces")
        require(isinstance(interfaces, list) and interfaces and
                len({item.get("id") for item in interfaces}) == len(interfaces),
                "interfaces " + name)
        purposeful += len(components)
    require(purposeful == 201, "purposeful role total")
    return {"status": "PASS", "boards": 4, "purposefulRoleSlots": purposeful}


def identity():
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT, text=True, encoding="utf-8").splitlines()
    paths = sorted({name for name in names if
        (name.startswith(("src/", "scripts/", "tests/contracts/", "tests/benchmarks/")) or
         name in ("build.xml", "war/circuitjs.html")) and
        not name.lower().endswith(".pyc") and "/__pycache__/" not in
        ("/" + name.lower().replace("\\", "/"))})
    entries = [{"path": path, "sha256": hashlib.sha256((ROOT / path).read_bytes()).hexdigest()}
               for path in paths if (ROOT / path).is_file()]
    payload = json.dumps(entries, sort_keys=True, separators=(",", ":")).encode()
    compiled = sorted((ROOT / "war/circuitjs1").glob("*.cache.js"))
    require(len(compiled) == 5, "exactly five compiled permutations")
    compiled_entries = [{"path": path.relative_to(ROOT).as_posix(),
                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                        for path in compiled]
    return {
        "protocol": "TSJ-A01-IDENTITY-1",
        "head": subprocess.check_output(["git", "rev-parse", "HEAD"],
                                        cwd=ROOT, text=True).strip(),
        "sourceFingerprint": hashlib.sha256(payload).hexdigest(),
        "inputs": entries,
        "buildFingerprint": hashlib.sha256(json.dumps(
            compiled_entries, sort_keys=True, separators=(",", ":")).encode()).hexdigest(),
        "compiled": compiled_entries,
        "execution": execution_provenance(),
    }


def execution_provenance():
    """Hash the source, verifier scripts, and web tree selected by preview.ps1.

    This deliberately mirrors Get-VerifierExecutionTreeProvenance rather than
    treating the caller's URL labels as proof of the artifact the browser ran.
    """
    category_records = []
    category_results = {}
    for category in ("src", "scripts", "war"):
        root = ROOT / category
        require(root.is_dir(), "execution provenance root " + category)
        files = [path for path in root.rglob("*")
                 if path.is_file() and path.suffix.lower() != ".pyc" and
                 "__pycache__" not in path.parts]
        files.sort(key=lambda path: path.relative_to(root).as_posix().lower())
        seen = set()
        records = []
        for path in files:
            relative = path.relative_to(root).as_posix()
            folded = relative.lower()
            require(folded not in seen,
                    "execution provenance duplicate path " + relative)
            seen.add(folded)
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            records.append(relative + "=" + digest)
        category_digest = hashlib.sha256("\n".join(records).encode("utf-8")).hexdigest()
        category_results[category] = {
            "root": root,
            "digest": category_digest,
            "fileCount": len(records),
        }
        category_records.extend(category + "/" + record for record in records)
    execution_digest = hashlib.sha256(
        "\n".join(category_records).encode("utf-8")).hexdigest()
    return {
        "protocol": "troubleshootjs-execution-provenance-v1",
        "sourceDigest": category_results["src"]["digest"],
        "scriptDigest": category_results["scripts"]["digest"],
        "webDigest": category_results["war"]["digest"],
        "executionDigest": execution_digest,
        "fileCount": sum(item["fileCount"] for item in category_results.values()),
    }


def close(actual, expected, tolerance):
    return math.isfinite(actual) and math.isfinite(expected) and abs(actual - expected) <= tolerance


def expected_resistance(seed, index):
    return 100.0 + 10.0 * ((index + seed) % 7)


def expected_fixture_identity(size, seed):
    values = [str(int(expected_resistance(seed, index)))
              if expected_resistance(seed, index).is_integer()
              else repr(expected_resistance(seed, index))
              for index in range(size)]
    return ("a01-series-ladder-v1|size=" + str(size) + "|seed=" + str(seed) +
            "|source=10" + "".join("|r" + str(index) + "=" + value
                                    for index, value in enumerate(values)))


def percentile(values, percentage):
    if not values:
        return 0
    ordered = sorted(values)
    index = (percentage * len(ordered) + 99) // 100 - 1
    return ordered[max(0, min(len(ordered) - 1, index))]


def validate_trace(trace, name, elapsed=None):
    require(isinstance(trace, list) and len(trace) >= 5, name + " timing trace")
    require(all(isinstance(item, dict) for item in trace),
            name + " timing trace event objects")
    events = [item.get("event") for item in trace]
    required = ["constructed", "analyzed", "step1", "step2", "finished"]
    require(events[:5] == required, name + " timing trace events")
    previous = 0.0
    for item in trace:
        value = finite(item.get("ms"), name + " timing trace")
        require(value >= 0 and value >= previous,
                name + " timing trace is not monotonic/nonnegative")
        if elapsed is not None:
            require(value <= elapsed + TRACE_INTERVAL_TOLERANCE_MS,
                    name + " timing trace is outside the reported interval")
        previous = value


def validate_attempt(attempt, report_name):
    require(isinstance(attempt, dict), report_name + " attempt object")
    prefix = report_name + " attempt"
    require(attempt.get("status") == "PASS", prefix + " did not pass")
    corpus = attempt.get("corpus")
    require(corpus in SEEDS, prefix + " corpus")
    size = integer(attempt.get("size"), prefix + " size", 1)
    require(size in SIZES, prefix + " size")
    seed = integer(attempt.get("seed"), prefix + " seed")
    require(seed in SEEDS[corpus], prefix + " seed")
    require(attempt.get("temperature") in ("cold", "warm"), prefix + " temperature")
    require(attempt.get("fixtureVersion") == "a01-series-ladder-v1", prefix + " fixture version")
    identity_value = attempt.get("fixtureIdentity")
    require(identity_value == expected_fixture_identity(size, seed),
            prefix + " fixture identity is not derived from size/seed")
    require(attempt.get("identityIndependentOfTiming") is True and
            attempt.get("timingIndependentIdentity") is True,
            prefix + " timing leaked into identity")
    for field in UNSUPPORTED_SYNTHETIC - {"routing", "diagnostic", "playable"}:
        require(field in attempt and attempt[field] is None, prefix + " fake " + field)
    unsupported = {item.get("stage") for item in attempt.get("unsupportedStages", [])
                   if isinstance(item, dict) and item.get("status") == "UNSUPPORTED" and
                   isinstance(item.get("reason"), str) and item["reason"].strip()}
    require(UNSUPPORTED_SYNTHETIC.issubset(unsupported), prefix + " unsupported-stage receipts")
    solver_elements = integer(attempt.get("solverElements"), prefix + " solverElements", 1)
    full_matrix = integer(attempt.get("matrixFullSize"), prefix + " matrixFullSize", 1)
    reduced_matrix = integer(attempt.get("matrixReducedSize"), prefix + " matrixReducedSize", 1)
    require(solver_elements == size + 2 and full_matrix == size + 3 and
            reduced_matrix <= full_matrix, prefix + " matrix/solver dimensions")
    require(integer(attempt.get("voltageSourceCount"), prefix + " voltageSourceCount") == 2,
            prefix + " voltage-source count")
    for field in COUNTER_FIELDS:
        integer(attempt.get(field), prefix + " " + field)
    require(attempt["analysisCount"] > 0 and attempt["stampCount"] > 0 and
            attempt["factorizationCount"] > 0 and attempt["solveCount"] >= STEPS_PER_ATTEMPT and
            attempt["iterationCount"] >= STEPS_PER_ATTEMPT and
            attempt["subIterationCount"] >= STEPS_PER_ATTEMPT and
            attempt["acceptedStepCount"] == STEPS_PER_ATTEMPT, prefix + " owner-side counters")
    resistances = [expected_resistance(seed, index) for index in range(size)]
    expected_total = sum(resistances)
    total = finite(attempt.get("totalResistance"), prefix + " totalResistance")
    require(close(total, expected_total, 1e-9), prefix + " total resistance oracle")
    expected_current = 10.0 / expected_total
    reported_expected_current = finite(attempt.get("expectedCurrent"),
                                       prefix + " expectedCurrent")
    observed_current = finite(attempt.get("observedCurrent"), prefix + " observedCurrent")
    require(close(reported_expected_current, expected_current, 1e-12) and
            close(observed_current, expected_current, 1e-9),
            prefix + " independent current oracle")
    require(close(finite(attempt.get("sourceVoltage"), prefix + " sourceVoltage"), 10.0, 1e-9),
            prefix + " source voltage")
    expected_nodes = attempt.get("expectedNodeVoltages")
    observed_nodes = attempt.get("nodeVoltages")
    require(isinstance(expected_nodes, list) and isinstance(observed_nodes, list) and
            len(expected_nodes) == size + 1 and len(observed_nodes) == size + 1,
            prefix + " node-voltage vectors")
    recomputed_nodes = [10.0]
    running = 10.0
    for resistance in resistances:
        running -= expected_current * resistance
        recomputed_nodes.append(running)
    for index, (reported, observed, expected) in enumerate(
            zip(expected_nodes, observed_nodes, recomputed_nodes)):
        require(close(finite(reported, prefix + " expected node voltage"), expected, 1e-9) and
                close(finite(observed, prefix + " node voltage"), expected, 1e-6),
                prefix + " independent node-voltage oracle at " + str(index))
    elapsed = finite(attempt.get("elapsedMs"), prefix + " elapsedMs")
    require(elapsed >= 0 and elapsed <= MAX_ATTEMPT_ELAPSED_MS,
            prefix + " attempt budget")
    validate_trace(attempt.get("timingTrace"), prefix, elapsed)
    require(attempt.get("stageStatus") == "SOLVER_PASS", prefix + " stage status")
    require(attempt.get("identityMatchesWarm") is True, prefix + " cold/warm identity flag")


def validate_failure_attempt(attempt, report_name):
    require(isinstance(attempt, dict), report_name + " failure outcome object")
    prefix = report_name + " failure outcome"
    require(attempt.get("status") == "FAIL", prefix + " status")
    require(attempt.get("corpus") in SEEDS, prefix + " corpus")
    require(attempt.get("temperature") in ("cold", "warm", "canary"),
            prefix + " temperature")
    require(attempt.get("stageStatus") == "SOLVER_FAIL", prefix + " stage status")
    require(attempt.get("cleanup") in ("PASS", "FAIL", "UNKNOWN"),
            prefix + " cleanup status")
    require(isinstance(attempt.get("error"), str) and attempt["error"].strip(),
            prefix + " error")
    integer(attempt.get("size"), prefix + " size", 1)
    integer(attempt.get("seed"), prefix + " seed")
    integer(attempt.get("acceptedStepCount"), prefix + " acceptedStepCount")


def validate_performance(report, attempts, name):
    performance = report.get("performance")
    require(isinstance(performance, dict), name + " missing performance summary")
    passed = []
    for item in attempts:
        if item.get("status") != "PASS":
            continue
        elapsed = finite(item.get("elapsedMs"), name + " elapsed")
        require(elapsed >= 0 and elapsed <= MAX_ATTEMPT_ELAPSED_MS,
                name + " performance attempt budget")
        require(elapsed.is_integer(), name + " performance elapsed must be integral")
        passed.append(int(elapsed))
    failed = [item for item in attempts if item.get("status") != "PASS"]
    require(integer(performance.get("sampleCount"), name + " performance sampleCount") == len(attempts) and
            integer(performance.get("passedAttempts"), name + " performance passedAttempts") == len(passed) and
            integer(performance.get("failedAttempts"), name + " performance failedAttempts") == len(failed),
            name + " performance outcome accounting")
    require(integer(performance.get("p50AttemptElapsedMs"), name + " performance p50") == percentile(passed, 50) and
            integer(performance.get("p95AttemptElapsedMs"), name + " performance p95") == percentile(passed, 95) and
            integer(performance.get("worstAttemptElapsedMs"), name + " performance worst") == (max(passed) if passed else 0),
            name + " performance percentiles")
    memory = performance.get("memory")
    require(isinstance(memory, dict) and memory.get("status") in ("AVAILABLE", "UNAVAILABLE"),
            name + " memory result")
    if memory.get("status") == "UNAVAILABLE":
        require(isinstance(memory.get("reason"), str) and memory["reason"].strip(),
                name + " memory unavailability reason")
    else:
        for field in ("usedBytes", "totalBytes"):
            integer(memory.get(field), name + " memory " + field)
    require(performance.get("cancellation") == "bounded-two-steps-per-attempt",
            name + " cancellation accounting")


def validate_executed_artifact(report, name):
    artifact = report.get("executedArtifact")
    require(isinstance(artifact, dict) and
            artifact.get("protocol") == "troubleshootjs-execution-provenance-v1",
            name + " missing executed artifact provenance")
    for field in ("sourceDigest", "scriptDigest", "webDigest", "executionDigest"):
        value = artifact.get(field)
        require(isinstance(value, str) and len(value) == 64 and
                all(char in "0123456789abcdef" for char in value),
                name + " invalid executed artifact " + field)
    integer(artifact.get("fileCount"), name + " executed artifact fileCount", 1)
    return artifact


def validate_browser_receipt(value, name):
    browser = value.get("browser")
    require(isinstance(browser, dict), name + " missing browser metadata")
    if browser.get("userAgent") is None:
        require(isinstance(browser.get("userAgentReason"), str) and
                browser["userAgentReason"].strip(), name + " browser user agent reason")
    else:
        require(isinstance(browser.get("userAgent"), str) and browser["userAgent"].strip(),
                name + " browser user agent")
    viewport = browser.get("viewport")
    require(isinstance(viewport, dict), name + " browser viewport")
    for field in ("width", "height"):
        integer(viewport.get(field), name + " viewport " + field, 1)
    dpr = finite(browser.get("devicePixelRatio"), name + " devicePixelRatio")
    require(dpr > 0, name + " devicePixelRatio")
    memory = browser.get("memory")
    require(isinstance(memory, dict) and memory.get("status") in ("AVAILABLE", "UNAVAILABLE"),
            name + " browser memory result")
    if memory.get("status") == "AVAILABLE":
        for field in ("usedBytes", "totalBytes"):
            integer(memory.get(field), name + " browser memory " + field)
    else:
        require(isinstance(memory.get("reason"), str) and memory["reason"].strip(),
                name + " browser memory reason")


def report_from_value(value, name):
    # A collector receipt wraps the versioned Java report.  Direct reports are
    # accepted for deterministic local checker tests as well.
    if isinstance(value, dict) and value.get("protocol") == "TSJ-A01-COLLECTION-1":
        require(value.get("tabClosed") is True, name + " browser tab was not closed")
        require(value.get("debug") is True, name + " debug-off receipt used")
        require(value.get("status") in ("PASS", "FAIL"), name + " collection status")
        report = value.get("report")
        require(isinstance(report, dict), name + " missing report")
        terminal = value.get("terminal", "")
        require(isinstance(terminal, str) and terminal.startswith(("PASS:a01", "FAIL:a01")),
                name + " terminal result")
        terminal_status = "PASS" if terminal.startswith("PASS:") else "FAIL"
        require(value.get("status") == terminal_status and
                report.get("status") == terminal_status,
                name + " wrapper/terminal/report status contradiction")
        require(value.get("cleanup") in ("PASS", "FAIL", "UNKNOWN"),
                name + " collection cleanup result")
        recorded_errors = []
        for field in ("error", "cleanupError"):
            if field in value and value.get(field) not in (None, ""):
                require(isinstance(value.get(field), str) and value[field].strip(),
                        name + " malformed recorded " + field)
                recorded_errors.append(field)
        require(not recorded_errors,
                name + " collection error cannot coexist with qualification status")
        if value.get("status") == "PASS":
            require(value.get("cleanup") == "PASS",
                    name + " passing collection did not clean up")
        require(value.get("sourceFingerprint") == report.get("sourceFingerprint"),
                name + " source identity mismatch")
        require(value.get("buildFingerprint") == report.get("buildFingerprint"),
                name + " build identity mismatch")
        receipt_artifact = value.get("artifactIdentity")
        require(isinstance(receipt_artifact, dict),
                name + " missing collected artifact provenance")
        require(receipt_artifact == report.get("executedArtifact"),
                name + " collected/executed artifact provenance mismatch")
        validate_browser_receipt(value, name)
    else:
        report = value
    require(isinstance(report, dict) and report.get("protocol") == "TSJ-A01-REPORT-1",
            name + " report protocol")
    require(report.get("status") in ("PASS", "FAIL"), name + " report status")
    for field in ("sourceFingerprint", "buildFingerprint"):
        require(isinstance(report.get(field), str) and
                len(report[field]) == 64 and all(c in "0123456789abcdef" for c in report[field]),
                name + " missing " + field)
    validate_executed_artifact(report, name)
    corpus = report.get("corpus")
    require(corpus in SEEDS, name + " report corpus")
    integer(report.get("round"), name + " report round")
    attempts = report.get("attempts")
    require(isinstance(attempts, list), name + " attempt outcomes")
    if report.get("status") == "FAIL":
        require(attempts and report.get("allOutcomesRetained") is True,
                name + " failed outcomes were not retained")
        for attempt in attempts:
            if attempt.get("status") == "PASS":
                validate_attempt(attempt, name)
            else:
                validate_failure_attempt(attempt, name)
        require(report.get("cleanup") in ("PASS", "FAIL") and
                isinstance(report.get("originalOwnerRestored"), bool),
                name + " failed report cleanup")
        return report
    require(len(attempts) == ATTEMPTS_PER_CORPUS, name + " attempt count")
    grouped = {}
    for attempt in attempts:
        validate_attempt(attempt, name)
        grouped.setdefault((attempt["size"], attempt["seed"]), []).append(attempt)
    require(set(grouped) == {(size, seed) for size in SIZES for seed in SEEDS[corpus]},
            name + " corpus coverage")
    for key, pair in grouped.items():
        require(len(pair) == 2 and {item["temperature"] for item in pair} == {"cold", "warm"},
                name + " cold/warm pair " + str(key))
        require(pair[0]["fixtureIdentity"] == pair[1]["fixtureIdentity"] and
                tuple(pair[0][field] for field in COUNTER_FIELDS) ==
                tuple(pair[1][field] for field in COUNTER_FIELDS),
                name + " cold/warm identity or work mismatch " + str(key))
    accepted_steps = sum(item["acceptedStepCount"] for item in attempts)
    require(report.get("attemptCount") == ATTEMPTS_PER_CORPUS and
            report.get("acceptedSteps") == accepted_steps == EXPECTED_STEPS_PER_CORPUS,
            name + " aggregate step accounting")
    total_elapsed = finite(report.get("totalElapsedMs"), name + " totalElapsedMs")
    require(total_elapsed >= 0 and total_elapsed <= MAX_TOTAL_ELAPSED_MS,
            name + " total measurement budget")
    budget = report.get("budget")
    require(isinstance(budget, dict) and budget.get("protocol") == "TSJ-A01-BUDGET-1" and
            budget.get("version") == "a01-budget-v1" and budget.get("frozen") is True and
            budget.get("frozenBeforeHoldout") is True and
            budget.get("maxAttemptElapsedMs") == MAX_ATTEMPT_ELAPSED_MS and
            budget.get("maxTotalElapsedMs") == MAX_TOTAL_ELAPSED_MS and
            budget.get("maxSolverElements") == 102 and
            budget.get("maxMatrixFullSize") == 103 and
            budget.get("requiredAcceptedSteps") == EXPECTED_STEPS_PER_CORPUS and
            budget.get("combinedRequiredAcceptedSteps") == EXPECTED_COMBINED_STEPS,
            name + " unfrozen or changed budget")
    require(report.get("unsupportedStagePolicy") == "explicit-null-with-reason" and
            report.get("coldWarmProtocol") ==
            "cold-first-then-warm-in-one-loaded-application" and
            report.get("timingCannotAffectIdentity") is True and
            report.get("allOutcomesRetained") is True,
            name + " protocol declarations")
    baseline = report.get("baseline")
    require(isinstance(baseline, dict) and baseline.get("stage") == "IMPLEMENTED_SMALL_BASELINE",
            name + " missing small baseline")
    for field in ("physicalPackages", "solverElements", "pads", "nets", "rawSegments",
                  "canonicalSegments", "hypothesisCount", "matrixFullSize",
                  "matrixReducedSize"):
        integer(baseline.get(field), name + " baseline " + field)
    require(report.get("originalOwnerRestored") is True and report.get("cleanup") == "PASS",
            name + " owner cleanup")
    sequential_elapsed = sum(item["elapsedMs"] for item in attempts)
    require(total_elapsed >= sequential_elapsed,
            name + " aggregate/sequential timing inconsistency")
    validate_performance(report, attempts, name)
    return report


def check_measurements(files):
    require(files, "no A01 receipts supplied")
    reports = []
    for file_name in files:
        path = Path(file_name)
        value = json.loads(path.read_text(encoding="utf-8-sig"))
        reports.append(report_from_value(value, path.name))
    keys = {(item["corpus"], item["round"]) for item in reports}
    require(len(keys) == len(reports), "duplicate corpus/round receipt")
    corpora = {item["corpus"] for item in reports}
    if len(reports) > 1:
        require(corpora == {"pilot", "holdout"}, "pilot and holdout receipts are required")
    source_ids = {item["sourceFingerprint"] for item in reports}
    build_ids = {item["buildFingerprint"] for item in reports}
    require(len(source_ids) == 1 and len(build_ids) == 1,
            "source/build identity changed across receipts")
    require(all(item.get("status") == "PASS" for item in reports),
            "measurement corpus contains a failed outcome")
    expected_identity = identity()
    require(next(iter(source_ids)) == expected_identity["sourceFingerprint"] and
            next(iter(build_ids)) == expected_identity["buildFingerprint"],
            "receipts are not bound to the current source/build artifacts")
    expected_execution = expected_identity["execution"]
    for report in reports:
        require(report["executedArtifact"] == expected_execution,
                "receipt is not bound to the served/executed artifact")
    accepted_steps = sum(sum(item["acceptedStepCount"] for item in report["attempts"])
                         for report in reports)
    require(accepted_steps == EXPECTED_STEPS_PER_CORPUS * len(reports),
            "checker aggregate accepted-step count")
    return {
        "status": "PASS",
        "protocol": "TSJ-A01-CHECK-1",
        "reports": len(reports),
        "corpora": sorted(corpora),
        "attempts": sum(len(item["attempts"]) for item in reports),
        "acceptedSteps": accepted_steps,
        "expectedAcceptedSteps": EXPECTED_COMBINED_STEPS if len(reports) == 2
        else EXPECTED_STEPS_PER_CORPUS,
        "sourceFingerprint": next(iter(source_ids)),
        "buildFingerprint": next(iter(build_ids)),
        "executedArtifact": expected_execution,
        "budgetVersion": "a01-budget-v1",
        "timingIndependentIdentity": True,
        "allOutcomesRetained": True,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("references", "identity", "check"))
    parser.add_argument("files", nargs="*")
    parser.add_argument("--output")
    args = parser.parse_args()
    if args.command == "references":
        result = validate_references(json.loads(
            (ROOT / "tests/benchmarks/a01-reference-boards.json").read_text(
                encoding="utf-8-sig")))
    elif args.command == "identity":
        result = identity()
    else:
        result = check_measurements(args.files)
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        Path(args.output).write_text(rendered, encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, json.JSONDecodeError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
