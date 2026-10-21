"""Independently qualify one finite composed packing-rejection observation."""
import argparse
import copy
import hashlib
import json
import math
import re
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Assertion-enabled Python required")

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PRIOR = HERE.parent / "random-composed-whitespace-r1"
FAMILY = "COMPOSED_CONTROLLED_INDICATOR"
PROFILE = "MEDIUM"
SEED = "-2750102463469069236"
REPLAY = "tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-2750102463469069236"
AUDIT = "PASS composed rejection source1482 machinery24 frozen private inputs"
DIAGNOSTIC_PREFIX = "PCB_PACKING_REJECTION_DIAGNOSTIC "
DIAGNOSTIC_SCHEMA = "packing-rejection-diagnostic@1"
SELECTED_GEOMETRY = "14cb0825afcb5d05b9c5866706a8945649f801a5f1bdb64477bb2264881a851f"
ROW_PREFIX = "PCB_SPARSE_NORMAL_DIAGNOSTIC "
SAMPLER_PREFIX = "PCB_WHITESPACE_CASE "
FINAL_ROW_MARKER = (
    "PASS: sparse normal board diagnostic family=" + FAMILY +
    " seed=" + SEED + " assertions=327")
NATIVE_MARKER = "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
ROUTER_REASONS = {"NO_PATH", "GRID_LIMIT", "SEARCH_LIMIT", "RECOVERY_LIMIT", "QUALITY_LIMIT"}
EVENT_KEYS = {
    "schema", "event", "phase", "inputPlanCanonical", "inputPlanSha256",
    "planCandidate", "planOutline", "layoutSeed", "routingSeed", "routePolicy",
    "baselineGeometrySha256", "exceptionType", "kind", "reason", "blockedNet",
    "message", "recovery",
}
RECOVERY_KEYS = {"canonical", "orderingPasses", "expansions", "outcome", "rejectionReason"}


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def text(path):
    return Path(path).read_text(encoding="utf-8-sig")


def private_log_text(path):
    data = Path(path).read_bytes()
    if data.startswith(b"\xff\xfe") or data.startswith(b"\xfe\xff"):
        return data.decode("utf-16")
    return data.decode("utf-8-sig", errors="replace")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def read_rows(receipt, prefix):
    return [json.loads(line[len(prefix):]) for line in receipt.splitlines()
            if line.startswith(prefix)]


def audit_inputs(private_root):
    inputs = runpy.run_path(str(HERE / "export_inputs.py"))
    current = inputs["record"](Path(private_root))
    require(current == read(HERE / "export-inputs.json"), "frozen private input seal")
    require(current["request"] == REPLAY, "exact accepted composed replay seal")
    return current


def phase_result(phase):
    folder = HERE / phase
    timing = read(folder / "timing.json")
    launch_path = folder / "process-launch.json"
    launch = read(launch_path)
    cleanup = read(folder / "process-cleanup.json")
    require(timing["phase"] == phase and timing["status"] == "PASS" and
            type(timing["exitCode"]) is int and timing["exitCode"] == 0 and
            timing["failure"] is None and timing["terminalCleanupIncluded"] is True and
            timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
            "actual successful operation " + phase)
    elapsed = timing["elapsedSeconds"]
    require(type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0,
            "actual operation elapsed time " + phase)
    require(launch["phase"] == phase and len(launch["drivers"]) == 2,
            "actual phase launch record " + phase)
    require(cleanup["status"] == "PASS" and type(cleanup["actualExecExitCode"]) is int and
            cleanup["actualExecExitCode"] == timing["exitCode"] == 0 and
            cleanup["launchSha256"] == sha(launch_path),
            "separate actual tool exit and launch seal " + phase)
    checks = cleanup["checks"]
    require(len(checks) == len(launch["drivers"]) == 2 and
            all(check["recordedBirthExited"] is True for check in checks),
            "owned driver births exited " + phase)
    project = lambda rows: [
        {key: row[key] for key in ("pid", "startTicks", "executable")} for row in rows]
    require(project(checks) == launch["drivers"], "driver birth correspondence " + phase)
    for side in ("before", "after"):
        require(text(folder / ("input-audit-" + side + ".txt")).strip() == AUDIT,
                "exact phase input audit " + phase + " " + side)
    return timing


def check_canaries(private_root):
    timing = phase_result("canaries")
    record = read(HERE / "canaries" / "canaries.json")
    checks = record["checks"]
    require([item["kind"] for item in checks] == ["seed", "family", "generator"],
            "actual seed/family/generator canary order")
    expected_keys = {"kind", "status", "actualAdapterExitCode", "elapsedSeconds",
                     "privateLogSha256", "privateLogBytes"}
    for item in checks:
        elapsed = item["elapsedSeconds"]
        require(set(item) == expected_keys and
                item["status"] == "PASS_REJECTED_AT_PARAMETER_BINDING" and
                type(item["actualAdapterExitCode"]) is int and item["actualAdapterExitCode"] == 1 and
                type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0 and
                re.fullmatch(r"[0-9a-f]{64}", item["privateLogSha256"]) is not None and
                type(item["privateLogBytes"]) is int and item["privateLogBytes"] > 0,
                "actual host rejection evidence " + item["kind"])
    require(record["limits"] ==
            "Actual selected private adapter rejects noncanonical seed, excluded family and absent private generator before compiler/native work; raw stderr private/hash-bound",
            "exact three-canary scope")

    runner = re.sub(r"\s+", "", text(HERE / "run_export.ps1"))
    adapter_path = Path(private_root) / "verify-composed-rejection.ps1"
    adapter = re.sub(r"\s+", "", text(adapter_path))
    log_parameters = {"seed": "TaskExportSeed", "family": "TaskExportFamily",
                      "generator": "TaskGeneratorSource"}
    for item in checks:
        kind = item["kind"]
        log_path = Path(private_root) / ("invalid-" + kind + ".txt")
        raw = private_log_text(log_path)
        require(sha(log_path) == item["privateLogSha256"] and
                log_path.stat().st_size == item["privateLogBytes"] and
                ("Cannot validate argument on parameter '" + log_parameters[kind] + "'") in raw and
                re.search(r"CURRENT_CONTRACT_|javac|PASS: focused current contracts", raw) is None,
                "private parameter-binding rejection log " + kind)
    require("foreach($kindin@('seed','family','generator'))" in runner and
            runner.count("$family=if($kind-eq'family'){'RC_DELAY'}else{'COMPOSED_CONTROLLED_INDICATOR'}") == 1 and
            runner.count("$seed=if($kind-eq'seed'){'-02750102463469069236'}else{'-2750102463469069236'}") == 1,
            "exact ordered seed/family/generator negative inputs")
    require("$selectedGenerator=if($kind-eq'generator'){Join-Path$PrivateRoot'missing-generator-canary.java'}else{$generator}" in runner and
            "$kind-eq'generator'-and(Test-Path-LiteralPath$selectedGenerator)" in runner and
            "-TaskGeneratorSource$selectedGenerator" in runner and
            not (Path(private_root) / "missing-generator-canary.java").exists(),
            "runner binds an absent generator path only for its generator canary")
    require("[Parameter(Mandatory=$true)][ValidateScript({Test-Path-LiteralPath$_-PathTypeLeaf})][string]$TaskGeneratorSource" in adapter,
            "selected adapter requires an existing private generator source before body execution")
    require("Cannotvalidateargumentonparameter'" in runner and
            "CURRENT_CONTRACT_|javac|PASS:focusedcurrentcontracts" in runner,
            "parameter-binding rejection precedes compiler/native execution")
    require(not (HERE / "canaries" / "native.txt").exists() and
            not (HERE / "canaries" / "receipt.txt").exists(),
            "negative controls produced no native export")
    return timing, checks


def canonical_millis(row, restore_to=None):
    require(isinstance(row, dict) and isinstance(row.get("packingProbe"), dict),
            "packing clock fields")
    probe = row["packingProbe"]
    millis = probe.get("millis")
    canonical = probe.get("canonical")
    require(type(millis) is int and millis >= 0 and isinstance(canonical, str),
            "packing clock types")
    matches = list(re.finditer(r";millis=([0-9]+);", canonical))
    require(len(matches) == 1 and int(matches[0].group(1)) == millis,
            "packing canonical clock matches its numeric field")
    normalized = copy.deepcopy(row)
    if restore_to is None:
        normalized["packingProbe"]["millis"] = "<CLOCK>"
        match = matches[0]
        normalized["packingProbe"]["canonical"] = (
            canonical[:match.start(1)] + "<CLOCK>" + canonical[match.end(1):])
    else:
        normalized["packingProbe"]["millis"] = restore_to
        match = matches[0]
        normalized["packingProbe"]["canonical"] = (
            canonical[:match.start(1)] + str(restore_to) + canonical[match.end(1):])
    return normalized


def previous_reader():
    baseline = read(HERE / "baseline-inputs.json")
    prior_qualifier = PRIOR / "qualify.py"
    expected = baseline["publishedEvidence"]["qualify.py"]
    require(sha(prior_qualifier) == expected, "published composed reader source pin")
    module = runpy.run_path(str(prior_qualifier))
    reader, adapted_sha, changes = module["load_published_reader"]()
    require(reader["EXPECTED"] == {"composed": (FAMILY, PROFILE, SEED)} and len(changes) == 2,
            "reuse only the two pinned prior-reader transformations")
    return reader, adapted_sha, changes


def validate_previous_rows(row, sampler, reader):
    old_receipt_path = PRIOR / "composed" / "receipt.txt"
    baseline = read(HERE / "baseline-inputs.json")
    require(sha(old_receipt_path) == baseline["publishedEvidence"]["composed/receipt.txt"],
            "published accepted receipt pin")
    old_receipt = text(old_receipt_path)
    old_rows = read_rows(old_receipt, ROW_PREFIX)
    old_samplers = read_rows(old_receipt, SAMPLER_PREFIX)
    require(len(old_rows) == len(old_samplers) == 1, "one pinned accepted row and sampler")
    old_row, old_sampler = old_rows[0], old_samplers[0]
    require(canonical_millis(row) == canonical_millis(old_row),
            "ordinary row differs from accepted receipt beyond packing milliseconds")
    require(sampler == old_sampler, "whitespace sampler differs from accepted receipt")
    old_marker = [line for line in old_receipt.splitlines() if line.startswith("PASS: sparse normal board diagnostic ")]
    new_receipt = text(HERE / "composed" / "receipt.txt")
    new_marker = [line for line in new_receipt.splitlines() if line.startswith("PASS: sparse normal board diagnostic ")]
    require(old_marker == [FINAL_ROW_MARKER] and new_marker == old_marker,
            "exact accepted final marker and assertions=327")
    old_result = reader["validate_row"](old_row, old_sampler, "composed")
    result = reader["validate_row"](row, sampler, "composed")
    return result, old_result


def optional_text(value):
    return value is None or isinstance(value, str)


def canonical_long(value):
    if not isinstance(value, str) or re.fullmatch(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)", value) is None:
        return False
    parsed = int(value)
    return -(1 << 63) <= parsed <= (1 << 63) - 1


def validate_recovery(recovery):
    if recovery is None:
        return
    require(isinstance(recovery, dict) and set(recovery) == RECOVERY_KEYS,
            "recovery record schema")
    canonical = recovery["canonical"]
    orders = recovery["orderingPasses"]
    expansions = recovery["expansions"]
    outcome = recovery["outcome"]
    reason = recovery["rejectionReason"]
    require(isinstance(canonical, str) and canonical and type(orders) is int and orders >= 0 and
            type(expansions) is int and expansions >= 0 and
            outcome in {"SUCCESS", "EXHAUSTED", "ABORTED"} and
            (reason is None or reason in ROUTER_REASONS), "recovery record values")
    canonical_reason = "null" if reason is None else reason
    require(canonical.startswith("routing-work@1;outcome=" + outcome + ";reason=" + canonical_reason + ";"),
            "recovery canonical header")
    canonical_orders = re.findall(r"(?:^|;)orders=([0-9]+);", canonical)
    canonical_expansions = re.findall(r"(?:^|;)expansions=([0-9]+);", canonical)
    require(len(canonical_orders) == len(canonical_expansions) == 1 and
            int(canonical_orders[0]) == orders and int(canonical_expansions[0]) == expansions,
            "recovery canonical counters")


def validate_event(event):
    require(isinstance(event, dict) and set(event) == EVENT_KEYS, "rejection event schema")
    require(event["schema"] == DIAGNOSTIC_SCHEMA, "rejection event epoch")
    which = event["event"]
    require(which in {"COMPACT_ROUTER_REJECTED", "PACKING_CAUGHT"}, "rejection event kind")
    expected_phase = "P05_ROUTE_SEARCH" if which == "COMPACT_ROUTER_REJECTED" else "PACKING_EXPECTED_CATCH"
    require(event["phase"] == expected_phase, "rejection event phase")
    canonical = event["inputPlanCanonical"]
    outline = event["planOutline"]
    require(isinstance(canonical, str) and canonical and type(outline) is list and len(outline) == 4 and
            all(type(value) is int for value in outline) and outline[2] > 0 and outline[3] > 0,
            "input plan and outline types")
    prefix = "task-packing-plan@1;outline=" + ",".join(str(value) for value in outline) + ";"
    require(canonical.startswith(prefix) and canonical.endswith("\n") and
            all(line for line in canonical[len(prefix):].splitlines()),
            "input plan canonical outline and footprints")
    plan_sha = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    require(re.fullmatch(r"[0-9a-f]{64}", event["inputPlanSha256"]) is not None and
            event["inputPlanSha256"] == plan_sha, "event input-plan fingerprint")
    require(type(event["planCandidate"]) is int and event["planCandidate"] >= 0 and
            canonical_long(event["layoutSeed"]) and canonical_long(event["routingSeed"]),
            "candidate and canonical signed-long seeds")
    require(event["routePolicy"] == "P05_ONE_FACE", "observed P05 one-face route policy")
    if which == "COMPACT_ROUTER_REJECTED":
        require(event["baselineGeometrySha256"] is None and
                event["exceptionType"] == "com.lushprojects.circuitjs1.client.PcbNetRouter$Rejected" and
                event["kind"] == "ROUTING" and event["reason"] in ROUTER_REASONS and
                optional_text(event["blockedNet"]), "typed inner router rejection")
    else:
        require(event["baselineGeometrySha256"] == SELECTED_GEOMETRY,
                "outer selected baseline geometry binding")
        kind = event["kind"]
        exception = event["exceptionType"]
        require(kind in {"ROUTING", "PLACEMENT", "ENVELOPE", "QUALITY"} and isinstance(exception, str),
                "outer failure kind and exception type")
        expected_types = {
            "ROUTING": {"com.lushprojects.circuitjs1.client.SeededPcbLayoutGenerator$CandidateRejected"},
            "PLACEMENT": {"com.lushprojects.circuitjs1.client.SeededPcbLayoutGenerator$CandidateRejected",
                          "com.lushprojects.circuitjs1.client.PcbPlacementPlanner$Rejected"},
            "ENVELOPE": {"com.lushprojects.circuitjs1.client.SupportedEnvelope$Rejected"},
            "QUALITY": {"com.lushprojects.circuitjs1.client.PcbBoardLayout$RouteQualityRejectedException"},
        }
        require(exception in expected_types[kind], "outer exception type agrees with its typed catch")
        if exception.endswith("CandidateRejected") or kind == "QUALITY":
            require(event["reason"] is None and event["blockedNet"] is None,
                    "wrapper/quality catch does not invent a dropped inner reason or net")
        else:
            require(isinstance(event["reason"], str) and event["reason"] and event["blockedNet"] is None,
                    "typed placement/envelope reason")
    require(optional_text(event["kind"]) and optional_text(event["reason"]) and
            optional_text(event["blockedNet"]) and optional_text(event["message"]),
            "actual exception text fields")
    validate_recovery(event["recovery"])


def validate_diagnostics(events, row):
    require(type(events) is list and len(events) >= 1, "task rejection observations present")
    for event in events:
        validate_event(event)
    outer_indices = [index for index, event in enumerate(events) if event["event"] == "PACKING_CAUGHT"]
    require(len(outer_indices) == 1, "exactly one expected outer packing catch")
    outer_index = outer_indices[0]
    outer = events[outer_index]
    require(row["identity"]["selectedGeometrySha256Before"] == SELECTED_GEOMETRY and
            row["identity"]["selectedGeometrySha256After"] == SELECTED_GEOMETRY,
            "ordinary row selected geometry baseline")
    correlated_index = None
    if outer["kind"] == "ROUTING":
        require(outer_index > 0, "outer router correlation has prior event")
        inner = events[outer_index - 1]
        require(inner["event"] == "COMPACT_ROUTER_REJECTED", "outer router correlation is immediate")
        match_fields = ("inputPlanCanonical", "inputPlanSha256", "planCandidate", "planOutline",
                        "layoutSeed", "routingSeed", "routePolicy")
        require(all(inner[key] == outer[key] for key in match_fields), "outer router correlation")
        require(isinstance(inner["recovery"], dict) and outer["recovery"] == inner["recovery"],
                "correlated router recovery statistics")
        require(inner["reason"] == inner["recovery"]["rejectionReason"] and
                inner["message"] == outer["message"], "correlated router reason and wrapper message")
        require(inner["blockedNet"] is None or
                inner["blockedNet"] in {net["netId"] for net in row["layout"]["nets"]},
                "correlated blocked net belongs to the actual board")
        recovery = outer["recovery"]
        probe = row["packingProbe"]
        require(recovery["orderingPasses"] == 5 and recovery["expansions"] == 305104 and
                probe["orderings"] == recovery["orderingPasses"] and
                probe["expansions"] == recovery["expansions"],
                "correlated recovery equals observed packing probe work")
        correlated_index = outer_index - 1
    return {"outerIndex": outer_index, "correlatedRouterIndex": correlated_index,
            "innerRouterEvents": sum(event["event"] == "COMPACT_ROUTER_REJECTED" for event in events),
            "outer": outer}


def reader_negatives(row, sampler, validator):
    cases = (
        ("public identity", lambda item: item.update(publicSeed="0")),
        ("pad net membership", lambda item: item["layout"]["pads"][0].update(netId="__WRONG_NET__")),
        ("independent copper", lambda item: item["physicalMetrics"].update(
            copper=item["physicalMetrics"]["copper"] + 1)),
        ("independent protected void", lambda item: item["physicalMetrics"].update(
            protectedVoid=item["physicalMetrics"]["protectedVoid"] + 1)),
        ("current physical epochs", lambda item: item["layout"]["constraints"].update(
            placementPackingVersion=3)),
    )
    rejected = []
    for message, mutate in cases:
        changed = copy.deepcopy(row)
        mutate(changed)
        try:
            validator(changed, sampler, "composed")
        except AssertionError as error:
            require(str(error) == message, "row negative failed at wrong original assertion")
            rejected.append(message)
        else:
            raise AssertionError("row reader accepted negative control: " + message)
    return rejected


def diagnostic_negatives(events, row, valid_summary):
    negatives = []
    outer_index = valid_summary["outerIndex"]

    changed = copy.deepcopy(events)
    changed[outer_index]["baselineGeometrySha256"] = "0" * 64
    try:
        validate_diagnostics(changed, row)
    except AssertionError as error:
        require(str(error) == "outer selected baseline geometry binding",
                "selected geometry negative failed at wrong assertion")
        negatives.append("outer selected baseline geometry binding")
    else:
        raise AssertionError("diagnostic reader accepted selected geometry negative")

    correlated_index = valid_summary["correlatedRouterIndex"]
    if correlated_index is not None:
        changed = copy.deepcopy(events)
        for index in (correlated_index, outer_index):
            recovery = changed[index]["recovery"]
            old = recovery["expansions"]
            literal = ";expansions=" + str(old) + ";"
            require(recovery["canonical"].count(literal) == 1, "counter negative canonical framing")
            recovery["expansions"] += 1
            recovery["canonical"] = recovery["canonical"].replace(
                literal, ";expansions=" + str(old + 1) + ";")
            validate_event(changed[index])
        try:
            validate_diagnostics(changed, row)
        except AssertionError as error:
            require(str(error) == "correlated recovery equals observed packing probe work",
                    "recovery counter negative failed at wrong assertion")
            negatives.append("coherent recovery counters disagree with packing probe")
        else:
            raise AssertionError("diagnostic reader accepted coherent recovery counter negative")
    else:
        negatives.append("correlated recovery counters: NOT APPLICABLE (outer catch is nonrouting)")

    inner_indices = [index for index, item in enumerate(events)
                     if item["event"] == "COMPACT_ROUTER_REJECTED"]
    if inner_indices:
        changed = copy.deepcopy(events)
        changed[inner_indices[0]]["inputPlanSha256"] = "0" * 64
        try:
            validate_diagnostics(changed, row)
        except AssertionError as error:
            require(str(error) == "event input-plan fingerprint",
                    "inner plan fingerprint negative failed at wrong assertion")
            negatives.append("event input-plan fingerprint")
        else:
            raise AssertionError("diagnostic reader accepted inner fingerprint negative")
    else:
        negatives.append("inner plan fingerprint: NOT APPLICABLE (no compact router event)")

    if valid_summary["correlatedRouterIndex"] is not None:
        changed = copy.deepcopy(events)
        inner = changed[valid_summary["correlatedRouterIndex"]]
        old_prefix = "task-packing-plan@1;outline=" + ",".join(str(v) for v in inner["planOutline"]) + ";"
        inner["planOutline"][2] += 10
        new_prefix = "task-packing-plan@1;outline=" + ",".join(str(v) for v in inner["planOutline"]) + ";"
        require(inner["inputPlanCanonical"].startswith(old_prefix), "correlation negative canonical framing")
        inner["inputPlanCanonical"] = new_prefix + inner["inputPlanCanonical"][len(old_prefix):]
        inner["inputPlanSha256"] = hashlib.sha256(inner["inputPlanCanonical"].encode("utf-8")).hexdigest()
        validate_event(inner)
        try:
            validate_diagnostics(changed, row)
        except AssertionError as error:
            require(str(error) == "outer router correlation",
                    "event correlation negative failed at wrong assertion")
            negatives.append("outer router correlation")
        else:
            raise AssertionError("diagnostic reader accepted valid-but-uncorrelated inner plan")

        changed = copy.deepcopy(events)
        inner = changed[valid_summary["correlatedRouterIndex"]]
        inner["reason"] = next(reason for reason in sorted(ROUTER_REASONS) if reason != inner["reason"])
        validate_event(inner)
        try:
            validate_diagnostics(changed, row)
        except AssertionError as error:
            require(str(error) == "correlated router reason and wrapper message",
                    "typed reason negative failed at wrong assertion")
            negatives.append("correlated typed reason agrees with recovery")
        else:
            raise AssertionError("diagnostic reader accepted mismatched router reason")
    else:
        negatives.append("outer router correlation: NOT APPLICABLE (outer catch is nonrouting)")
    return negatives


def unchanged_counter_negative(row, sampler, reader):
    changed = copy.deepcopy(row)
    probe = changed["packingProbe"]
    literal = ";expansions=" + str(probe["expansions"]) + ";"
    require(probe["canonical"].count(literal) == 1, "ordinary counter negative canonical framing")
    probe["expansions"] += 1
    probe["canonical"] = probe["canonical"].replace(literal, ";expansions=" + str(probe["expansions"]) + ";")
    reader["validate_row"](changed, sampler, "composed")
    try:
        validate_previous_rows(changed, sampler, reader)
    except AssertionError as error:
        require(str(error) == "ordinary row differs from accepted receipt beyond packing milliseconds",
                "ordinary counter negative failed at wrong assertion")
        return "coherent packing expansion change rejected by exact nonclock comparison"
    raise AssertionError("reader accepted changed ordinary packing expansion count")


def qualify(private_root):
    private_root = Path(private_root)
    require(private_root.is_dir(), "private root path")
    seal = audit_inputs(private_root)
    baseline = read(HERE / "baseline-inputs.json")
    private_manifest = read(HERE / "private-inputs.json")
    source_review = read(HERE / "source-review.json")
    require(source_review["status"] == "PASS_PASSIVE_PRIVATE_SOURCE_ONLY" and
            source_review["privateInputManifestSha256"] == sha(HERE / "private-inputs.json") and
            source_review["privateInputs"] == private_manifest["privateInputs"] == seal["privateInputs"] and
            source_review["verifiedHooks"] == 5 and source_review["verifiedPrivateAdapterBindings"] == 3,
            "root passive-source review and private input hashes")
    require({name: sha(private_root / name) for name in private_manifest["privateInputs"]} ==
            private_manifest["privateInputs"], "private generator/adapter/exporter source pins")
    expected_execution = baseline["expectedExecution"]
    require(set(expected_execution) == {"Protocol", "SourceDigest", "ScriptDigest", "WebDigest", "Digest", "FileCount"} and
            expected_execution == read(HERE / "execution-before.json") == read(HERE / "execution-after.json"),
            "six-field execution provenance unchanged")
    advice = read(HERE / "source-advice.json")
    require(advice["status"] == "SOURCE_ONLY_NO_ACTUAL_CAUSE_MEASURED" and
            sha(HERE / "source-advice.json") == baseline["gates"]["source-advice.json"],
            "source-only advice stays explicitly non-attributing")
    require(sha(PRIOR / "qualify.py") == baseline["publishedEvidence"]["qualify.py"] and
            sha(PRIOR / "composed" / "receipt.txt") == baseline["publishedEvidence"]["composed/receipt.txt"],
            "previous reader and accepted receipt remain pinned")

    canary_timing, canaries = check_canaries(private_root)
    timing = phase_result("composed")
    folder = HERE / "composed"
    receipt = text(folder / "receipt.txt")
    native = text(folder / "native.txt")
    require("CURRENT_CONTRACT_FAILURE:" not in native and "CURRENT_CONTRACT_CLEANUP:" not in native,
            "no maintained native failure")
    require(native.count(NATIVE_MARKER) == 1, "maintained native final marker")

    rows = read_rows(receipt, ROW_PREFIX)
    samplers = read_rows(receipt, SAMPLER_PREFIX)
    events = read_rows(receipt, DIAGNOSTIC_PREFIX)
    require(len(rows) == len(samplers) == 1 and len(events) >= 1,
            "one ordinary row/sampler and task rejection events")
    reader, adapted_sha, reader_changes = previous_reader()
    row_result, accepted_row_result = validate_previous_rows(rows[0], samplers[0], reader)
    require(rows[0]["physicalMetrics"]["available"] is True,
            "independent metric negative-control baseline")
    summary = validate_diagnostics(events, rows[0])
    row_negatives = reader_negatives(rows[0], samplers[0], reader["validate_row"])
    event_negatives = diagnostic_negatives(events, rows[0], summary)
    counter_negative = unchanged_counter_negative(rows[0], samplers[0], reader)

    outer = summary["outer"]
    if summary["correlatedRouterIndex"] is not None:
        attribution = "ROUTER_REJECTION_CAPTURED_BEFORE_CANDIDATE_REJECTED_WRAP"
        underlying_limit = "The actual PcbNetRouter.Rejected reason/net and recovery stats were captured before the unchanged outer wrapper. No production acceptance, route-policy change, or broader replay claim follows."
    else:
        attribution = "OBSERVED_OUTER_EXCEPTION_ONLY"
        underlying_limit = "The actual outer expected-failure catch is recorded, but no matching pre-wrap router rejection proves an underlying finalization subtype; that cause remains unknown."

    assessment = HERE / "assessment.json"
    require(not assessment.exists(), "preserve prior assessment")
    row_result.update({"elapsedSeconds": timing["elapsedSeconds"],
                       "receiptSha256": sha(folder / "receipt.txt"),
                       "nativeSha256": sha(folder / "native.txt")})
    assessment_value = {
        "status": "PASS_EXACT_COMPOSED_REJECTION_ATTRIBUTION",
        "inputSealSha256": sha(HERE / "export-inputs.json"),
        "privateInputSealSha256": sha(HERE / "private-inputs.json"),
        "execution": expected_execution,
        "reusedReader": {"publishedQualifierSha256": baseline["publishedEvidence"]["qualify.py"],
                         "adaptedPublishedReaderSha256": adapted_sha,
                         "guardedChanges": reader_changes},
        "acceptedObservation": {"rowResult": row_result,
                                "previousAcceptedResult": accepted_row_result,
                                "ordinaryRowClockOnlyDifference": True,
                                "whitespaceSamplerUnchanged": True,
                                "finalMarker": FINAL_ROW_MARKER},
        "rejection": {
            "schema": DIAGNOSTIC_SCHEMA,
            "eventCount": len(events),
            "compactRouterRejectionCount": summary["innerRouterEvents"],
            "unrelatedCompactRouterRejectionCount": summary["innerRouterEvents"] -
                (1 if summary["correlatedRouterIndex"] is not None else 0),
            "correlatedRouterObservation": events[summary["correlatedRouterIndex"]]
                if summary["correlatedRouterIndex"] is not None else None,
            "outerEvent": outer["event"],
            "outerPhase": outer["phase"],
            "exceptionType": outer["exceptionType"],
            "kind": outer["kind"],
            "reason": outer["reason"],
            "blockedNet": outer["blockedNet"],
            "message": outer["message"],
            "selectedBaselineGeometrySha256": outer["baselineGeometrySha256"],
            "inputPlanSha256": outer["inputPlanSha256"],
            "planCandidate": outer["planCandidate"],
            "planOutline": outer["planOutline"],
            "routePolicy": outer["routePolicy"],
            "recovery": outer["recovery"],
            "attribution": attribution,
            "underlyingCauseLimit": underlying_limit,
        },
        "canaries": {"elapsedSeconds": canary_timing["elapsedSeconds"], "checks": canaries},
        "rowNegativeControls": row_negatives,
        "rejectionNegativeControls": event_negatives,
        "unchangedCounterNegativeControl": counter_negative,
        "limits": "One actual accepted composed replay and its existing packing probe only. No production edit or acceptance, extra route/budget, fresh matrix/build/player claim, all-seed generalization, manufacturing, or optimality claim.",
    }
    assessment.write_text(json.dumps(assessment_value, indent=2) + "\n", encoding="utf-8")
    return assessment_value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--private-root", type=Path, required=True)
    args = parser.parse_args()
    qualify(args.private_root)
    print("PASS exact composed rejection attribution; actual canaries/export/cleanup and bounded reader negatives")


if __name__ == "__main__":
    main()
