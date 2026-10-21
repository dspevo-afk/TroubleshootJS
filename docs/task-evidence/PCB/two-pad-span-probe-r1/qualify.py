"""Independently qualify one finite private two-pad span experiment."""
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
PRIOR = HERE.parent / "composed-rejection-diagnostic-r1"
FAMILY = "COMPOSED_CONTROLLED_INDICATOR"
PROFILE = "MEDIUM"
SEED = "-2750102463469069236"
REPLAY = "tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-2750102463469069236"
AUDIT = "PASS two-pad span source1482 machinery24 frozen control and variant inputs"
ROW_PREFIX = "PCB_SPARSE_NORMAL_DIAGNOSTIC "
SAMPLER_PREFIX = "PCB_WHITESPACE_CASE "
CAUSE_PREFIX = "PCB_PACKING_REJECTION_DIAGNOSTIC "
SPAN_PREFIX = "PCB_PACKING_SPAN_CHECKPOINT "
SPAN_SCHEMA = "packing-span-checkpoint@1"
BASELINE_GEOMETRY = "14cb0825afcb5d05b9c5866706a8945649f801a5f1bdb64477bb2264881a851f"
FINAL_ROW_MARKER = (
    "PASS: sparse normal board diagnostic family=" + FAMILY +
    " seed=" + SEED + " assertions=327")
NATIVE_MARKER = "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
CANARY_KINDS = ("seed", "family", "generator", "planner", "compactor")
CHECKPOINT_PHASES = ("SOURCE", "ORDINARY", "PAIRED", "FINAL")
CHECKPOINT_KEYS = {
    "schema", "phase", "inputPlanCanonical", "inputPlanSha256", "layoutSeed",
    "routingSeed", "planCandidate", "evaluations", "planOutline", "netSpans",
}
SPAN_KEYS = {"netId", "padIds", "centers", "span"}
PRIVATE_INPUTS = {
    "common/SparseNormalBoardDiagnostic.java",
    "common/verify-two-pad-span.ps1",
    "control/SeededPcbLayoutGenerator.java",
    "control/PcbPlacementPlanner.java",
    "control/PcbPlacementCompactor.java",
    "variant/SeededPcbLayoutGenerator.java",
    "variant/PcbPlacementPlanner.java",
    "variant/PcbPlacementCompactor.java",
}


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def text(path):
    return Path(path).read_text(encoding="utf-8-sig")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def read_rows(receipt, prefix):
    return [json.loads(line[len(prefix):]) for line in receipt.splitlines()
            if line.startswith(prefix)]


def private_log_text(path):
    data = Path(path).read_bytes()
    if data.startswith(b"\xff\xfe") or data.startswith(b"\xfe\xff"):
        return data.decode("utf-16")
    return data.decode("utf-8-sig", errors="replace")


def canonical_long(value):
    if not isinstance(value, str) or re.fullmatch(
            r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)", value) is None:
        return False
    parsed = int(value)
    return -(1 << 63) <= parsed <= (1 << 63) - 1


def audit_inputs(private_root):
    inputs = runpy.run_path(str(HERE / "export_inputs.py"))
    seal = inputs["record"](Path(private_root))
    require(seal == read(HERE / "export-inputs.json"), "frozen private control/variant input seal")
    require(seal.get("request") == REPLAY, "exact accepted composed replay seal")
    require(set(seal.get("privateInputs", {})) == PRIVATE_INPUTS,
            "exact eight sealed private source paths")
    return seal


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
    require([item["kind"] for item in checks] == list(CANARY_KINDS),
            "actual seed/family/generator/planner/compactor canary order")
    expected_keys = {"kind", "status", "actualAdapterExitCode", "elapsedSeconds",
                     "privateLogSha256", "privateLogBytes"}
    log_parameters = {
        "seed": "TaskExportSeed", "family": "TaskExportFamily",
        "generator": "TaskGeneratorSource", "planner": "TaskPlannerSource",
        "compactor": "TaskCompactorSource",
    }
    for item in checks:
        elapsed = item["elapsedSeconds"]
        require(set(item) == expected_keys and
                item["status"] == "PASS_REJECTED_AT_PARAMETER_BINDING" and
                type(item["actualAdapterExitCode"]) is int and item["actualAdapterExitCode"] == 1 and
                type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0 and
                re.fullmatch(r"[0-9a-f]{64}", item["privateLogSha256"]) is not None and
                type(item["privateLogBytes"]) is int and item["privateLogBytes"] > 0,
                "actual host rejection evidence " + item["kind"])
        path = Path(private_root) / ("invalid-" + item["kind"] + ".txt")
        raw = private_log_text(path)
        parameter = log_parameters[item["kind"]]
        require(sha(path) == item["privateLogSha256"] and path.stat().st_size == item["privateLogBytes"] and
                ("Cannot validate argument on parameter '" + parameter + "'") in raw and
                re.search(r"CURRENT_CONTRACT_|javac|PASS: focused current contracts", raw) is None,
                "private parameter-binding rejection log " + item["kind"])
    require(record["limits"] ==
            "Actual selected private adapter rejects noncanonical seed, excluded family and absent generator/planner/compactor before compiler/native work; raw stderr private/hash-bound",
            "exact five-canary scope")

    runner = re.sub(r"\s+", "", text(HERE / "run_probe.ps1"))
    adapter_path = Path(private_root) / "common" / "verify-two-pad-span.ps1"
    adapter = re.sub(r"\s+", "", text(adapter_path))
    require("foreach($kindin@('seed','family','generator','planner','compactor'))" in runner,
            "runner enumerates only the five ordered negative controls")
    require(runner.count("$family=if($kind-eq'family'){'RC_DELAY'}else{'COMPOSED_CONTROLLED_INDICATOR'}") == 1 and
            runner.count("$seed=if($kind-eq'seed'){'-02750102463469069236'}else{'-2750102463469069236'}") == 1,
            "exact rejected family and noncanonical signed-long inputs")
    source_names = {
        "generator": ("$selectedGenerator", "missing-generator-canary.java", "-TaskGeneratorSource$selectedGenerator"),
        "planner": ("$selectedPlanner", "missing-planner-canary.java", "-TaskPlannerSource$selectedPlanner"),
        "compactor": ("$selectedCompactor", "missing-compactor-canary.java", "-TaskCompactorSource$selectedCompactor"),
    }
    for kind, (variable, missing, binding) in source_names.items():
        require("$missing=Join-Path$PrivateRoot('missing-'+$kind+'-canary.java')" in runner and binding in runner and
                ("$kind-eq'" + kind + "'") in runner and
                not (Path(private_root) / missing).exists(),
                "runner explicitly binds absent " + kind + " source")
    for parameter in ("TaskGeneratorSource", "TaskPlannerSource", "TaskCompactorSource"):
        require(("[Parameter(Mandatory=$true)][ValidateScript({Test-Path-LiteralPath$_-PathTypeLeaf})][string]$" + parameter) in adapter,
                "adapter rejects absent " + parameter + " before its body")
    require("-TaskGeneratorSource$selectedGenerator" in runner and
            "-TaskPlannerSource$selectedPlanner" in runner and
            "-TaskCompactorSource$selectedCompactor" in runner and
            "Cannotvalidateargumentonparameter'" in runner and
            "CURRENT_CONTRACT_|javac|PASS:focusedcurrentcontracts" in runner,
            "actual canary parameter validation precedes compiler/native work")
    require(not (HERE / "canaries" / "native.txt").exists() and
            not (HERE / "canaries" / "receipt.txt").exists(),
            "negative controls produced no native export")
    return timing, checks


def previous_reader():
    baseline = read(HERE / "baseline-inputs.json")
    prior_qualifier = PRIOR / "qualify.py"
    require(sha(prior_qualifier) == baseline["publishedEvidence"]["qualify.py"],
            "published composed reader source pin")
    module = runpy.run_path(str(prior_qualifier))
    reader, adapted_sha, changes = module["previous_reader"]()
    require(reader["EXPECTED"] == {"composed": (FAMILY, PROFILE, SEED)} and len(changes) == 2,
            "reuse only the two pinned prior-reader transformations")
    return reader, adapted_sha, changes


def canonical_millis(row):
    require(isinstance(row, dict) and isinstance(row.get("packingProbe"), dict),
            "packing clock fields")
    probe = row["packingProbe"]
    millis, canonical = probe.get("millis"), probe.get("canonical")
    require(type(millis) is int and millis >= 0 and isinstance(canonical, str),
            "packing clock types")
    matches = list(re.finditer(r";millis=([0-9]+);", canonical))
    require(len(matches) == 1 and int(matches[0].group(1)) == millis,
            "packing canonical clock matches its numeric field")
    normalized = copy.deepcopy(row)
    normalized["packingProbe"]["millis"] = "<CLOCK>"
    match = matches[0]
    normalized["packingProbe"]["canonical"] = (
        canonical[:match.start(1)] + "<CLOCK>" + canonical[match.end(1):])
    return normalized


def accepted_observation(reader):
    baseline = read(HERE / "baseline-inputs.json")
    receipt_path = PRIOR / "composed" / "receipt.txt"
    require(sha(receipt_path) == baseline["publishedEvidence"]["composed/receipt.txt"],
            "published accepted receipt pin")
    receipt = text(receipt_path)
    rows = read_rows(receipt, ROW_PREFIX)
    samplers = read_rows(receipt, SAMPLER_PREFIX)
    events = read_rows(receipt, CAUSE_PREFIX)
    require(len(rows) == len(samplers) == 1 and len(events) == 2,
            "one accepted row/sampler and two accepted typed cause events")
    require(rows[0]["identity"]["selectedGeometrySha256Before"] == BASELINE_GEOMETRY and
            rows[0]["identity"]["selectedGeometrySha256After"] == BASELINE_GEOMETRY,
            "pinned accepted selected geometry")
    require(reader["validate_row"](rows[0], samplers[0], "composed"),
            "previous accepted row oracle")
    marker = [line for line in receipt.splitlines()
              if line.startswith("PASS: sparse normal board diagnostic ")]
    require(marker == [FINAL_ROW_MARKER], "pinned accepted final marker and assertions=327")
    return rows[0], samplers[0], events


def same_control_row(row, sampler, accepted_row, accepted_sampler, reader):
    require(canonical_millis(row) == canonical_millis(accepted_row),
            "control row differs from accepted receipt beyond packing milliseconds")
    require(sampler == accepted_sampler, "control whitespace sampler differs from accepted receipt")
    return reader["validate_row"](row, sampler, "composed")


def check_native_phase(phase, timing, reader, accepted_row, accepted_sampler, accepted_events):
    folder = HERE / phase
    native = text(folder / "native.txt")
    receipt = text(folder / "receipt.txt")
    require("CURRENT_CONTRACT_FAILURE:" not in native and
            "CURRENT_CONTRACT_CLEANUP:" not in native and native.count(NATIVE_MARKER) == 1,
            "maintained native final marker " + phase)
    rows = read_rows(receipt, ROW_PREFIX)
    samplers = read_rows(receipt, SAMPLER_PREFIX)
    events = read_rows(receipt, CAUSE_PREFIX)
    checkpoints = read_rows(receipt, SPAN_PREFIX)
    markers = [line for line in receipt.splitlines()
               if line.startswith("PASS: sparse normal board diagnostic ")]
    require(len(rows) == len(samplers) == 1 and markers == [FINAL_ROW_MARKER],
            "exact one accepted row/sampler and assertions=327 " + phase)
    result = reader["validate_row"](rows[0], samplers[0], "composed")
    require(rows[0]["physicalMetrics"]["available"] is True and
            rows[0]["independentMetrics"]["matchesProduction"] is True,
            "independent four-metric evidence available " + phase)
    if phase == "control":
        same_control_row(rows[0], samplers[0], accepted_row, accepted_sampler, reader)
        require(events == accepted_events,
                "control typed cause events equal accepted receipt")
    return {
        "row": rows[0], "sampler": samplers[0], "events": events,
        "checkpoints": checkpoints, "rowResult": result,
        "timing": timing, "receiptSha256": sha(folder / "receipt.txt"),
        "nativeSha256": sha(folder / "native.txt"),
    }


def selected_pad_centers(row):
    pads = {}
    for pad in row["layout"]["pads"]:
        pad_id = pad["padId"]
        center = pad["center"]
        require(isinstance(pad_id, str) and pad_id and pad_id not in pads and
                type(center) is list and len(center) == 2 and
                all(type(value) is int for value in center),
                "ordinary row pad identity and center inventory")
        pads[pad_id] = center
    return pads


def expected_two_pad_nets(row):
    pads = selected_pad_centers(row)
    inventory = {}
    net_ids = set()
    for net in row["layout"]["nets"]:
        net_id = net["netId"]
        require(isinstance(net_id, str) and net_id and net_id not in net_ids,
                "ordinary row logical net identity uniqueness")
        net_ids.add(net_id)
        ends = net["padIds"]
        require(type(ends) is list and all(isinstance(value, str) and value for value in ends) and
                len(ends) == len(set(ends)),
                "ordinary row logical net endpoint identities")
        if len(ends) != 2:
            continue
        ordered = sorted(ends)
        require(all(pad_id in pads for pad_id in ordered),
                "ordinary row two-pad net endpoint membership")
        inventory[net_id] = {
            "netId": net_id,
            "padIds": ordered,
            "centers": [list(pads[ordered[0]]), list(pads[ordered[1]])],
            "span": manhattan(pads[ordered[0]], pads[ordered[1]]),
        }
    require(inventory, "ordinary row has exact two-pad net inventory")
    return inventory


def manhattan(a, b):
    return abs(int(a[0]) - int(b[0])) + abs(int(a[1]) - int(b[1]))


def checkpoint_without(checkpoint, excluded):
    return {key: copy.deepcopy(value) for key, value in checkpoint.items() if key not in excluded}


def validate_checkpoint(checkpoint, phase, expected_inventory, expected_seed_pair,
                        expected_footprints):
    require(isinstance(checkpoint, dict) and set(checkpoint) == CHECKPOINT_KEYS,
            "checkpoint exact schema")
    require(checkpoint["schema"] == SPAN_SCHEMA and checkpoint["phase"] == phase,
            "checkpoint epoch and phase order")
    outline = checkpoint["planOutline"]
    require(type(outline) is list and len(outline) == 4 and
            all(type(value) is int for value in outline) and outline[2] > 0 and outline[3] > 0,
            "checkpoint plan outline")
    canonical = checkpoint["inputPlanCanonical"]
    prefix = "task-packing-plan@1;outline=" + ",".join(str(value) for value in outline) + ";"
    require(isinstance(canonical, str) and canonical.startswith(prefix) and canonical.endswith("\n"),
            "checkpoint canonical plan header")
    fingerprints = canonical[len(prefix):].splitlines()
    require(len(fingerprints) == expected_footprints and
            all(isinstance(value, str) and value for value in fingerprints),
            "checkpoint canonical footprint fingerprints")
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    require(checkpoint["inputPlanSha256"] == digest and
            re.fullmatch(r"[0-9a-f]{64}", checkpoint["inputPlanSha256"]) is not None,
            "checkpoint input-plan hash")
    require(canonical_long(checkpoint["layoutSeed"]) and
            canonical_long(checkpoint["routingSeed"]) and
            (checkpoint["layoutSeed"], checkpoint["routingSeed"]) == expected_seed_pair,
            "checkpoint canonical seeds match accepted replay")
    require(type(checkpoint["planCandidate"]) is int and checkpoint["planCandidate"] >= 0 and
            type(checkpoint["evaluations"]) is int and 0 <= checkpoint["evaluations"] <= 60000,
            "checkpoint candidate and bounded cumulative evaluations")
    spans = checkpoint["netSpans"]
    require(type(spans) is list and len(spans) == len(expected_inventory),
            "checkpoint exact two-pad net inventory")
    actual = {}
    ids = []
    for span in spans:
        require(isinstance(span, dict) and set(span) == SPAN_KEYS,
                "checkpoint span entry schema")
        net_id = span["netId"]
        require(isinstance(net_id, str) and net_id,
                "checkpoint exact two-pad net inventory")
        ids.append(net_id)
        expected = expected_inventory.get(net_id)
        require(expected is not None and span["padIds"] == expected["padIds"],
                "checkpoint exact two-pad net inventory")
        centers = span["centers"]
        require(type(centers) is list and len(centers) == 2 and
                all(type(center) is list and len(center) == 2 and
                    all(type(coord) is int for coord in center) for center in centers),
                "checkpoint endpoint center types")
        require(type(span["span"]) is int and span["span"] >= 0 and
                span["span"] == manhattan(centers[0], centers[1]),
                "checkpoint Manhattan span recomputes")
        actual[net_id] = copy.deepcopy(span)
    require(ids == sorted(expected_inventory) and len(set(ids)) == len(ids),
            "checkpoint exact two-pad net inventory")
    return actual


def validate_checkpoint_set(checkpoints, row, accepted_row, expected_seed_pair, arm):
    require(type(checkpoints) is list and len(checkpoints) == 4,
            "exact four stage checkpoints " + arm)
    expected = expected_two_pad_nets(row)
    accepted = expected_two_pad_nets(accepted_row)
    require(set(expected) == set(accepted) and
            {key: value["padIds"] for key, value in expected.items()} ==
            {key: value["padIds"] for key, value in accepted.items()},
            "current and accepted logical two-pad net identity inventory " + arm)
    maps = []
    for checkpoint, phase in zip(checkpoints, CHECKPOINT_PHASES):
        maps.append(validate_checkpoint(checkpoint, phase, expected, expected_seed_pair,
                                        row["counts"]["layoutParts"]))
    source_spans = {
        key: {"netId": value["netId"], "padIds": value["padIds"], "span": value["span"]}
        for key, value in accepted.items()
    }
    source_observed_spans = {
        key: {"netId": value["netId"], "padIds": value["padIds"], "span": value["span"]}
        for key, value in maps[0].items()
    }
    require(source_observed_spans == source_spans,
            "SOURCE spans equal accepted selected pad spans")
    outlines = [item["planOutline"] for item in checkpoints]
    candidates = [item["planCandidate"] for item in checkpoints]
    seeds = [(item["layoutSeed"], item["routingSeed"]) for item in checkpoints]
    require(all(value == outlines[0] for value in outlines) and
            all(value == candidates[0] for value in candidates) and
            all(value == seeds[0] for value in seeds),
            "stage plans preserve outline, candidate, and replay seeds")
    evaluations = [item["evaluations"] for item in checkpoints]
    require(evaluations[0] <= evaluations[1] <= evaluations[2] <= 60000 and
            evaluations[3] == evaluations[2],
            "stage cumulative evaluations remain ordered and within shared cap")
    return expected, maps


def validate_control_stages(checkpoints, maps):
    require(checkpoint_without(checkpoints[2], {"phase"}) ==
            checkpoint_without(checkpoints[3], {"phase"}),
            "control PAIRED equals FINAL except phase")


def validate_causes(events, row, final):
    prior = runpy.run_path(str(PRIOR / "qualify.py"))
    for event in events:
        prior["validate_event"](event)
    outer_indices = [i for i, event in enumerate(events) if event["event"] == "PACKING_CAUGHT"]
    rejected = row["packingProbe"]["disposition"] == "ROUTE_REJECTED"
    require(len(outer_indices) == (1 if rejected else 0), "actual packing disposition and catch inventory")
    if not rejected:
        return
    index = outer_indices[0]
    outer = events[index]
    fields = ("inputPlanCanonical", "inputPlanSha256", "layoutSeed", "routingSeed",
              "planCandidate", "planOutline")
    require(all(outer[key] == final[key] for key in fields), "packing catch binds FINAL proposal")
    if outer["kind"] != "ROUTING":
        return
    require(index > 0, "routing catch has immediate inner event")
    inner = events[index - 1]
    require(inner["event"] == "COMPACT_ROUTER_REJECTED" and
            all(inner[key] == outer[key] for key in fields + ("routePolicy",)) and
            inner["message"] == outer["message"], "actual inner and outer routing correlation")
    recovery = outer["recovery"]
    require(isinstance(recovery, dict) and inner["recovery"] == recovery and
            inner["reason"] == recovery["rejectionReason"] and
            recovery["orderingPasses"] == row["packingProbe"]["orderings"] and
            recovery["expansions"] == row["packingProbe"]["expansions"],
            "actual routing recovery agrees with packing work")
    require(inner["blockedNet"] is None or inner["blockedNet"] in
            {net["netId"] for net in row["layout"]["nets"]}, "actual blocked net membership")


def validate_variant_stages(checkpoints, maps):
    source, ordinary, paired, final = maps
    require(checkpoints[3]["evaluations"] == checkpoints[2]["evaluations"],
            "final evaluations retain paired count")
    require(all(ordinary[key]["span"] <= source[key]["span"] for key in source),
            "variant ORDINARY two-pad spans do not exceed SOURCE")
    fallback = any(paired[key]["span"] > source[key]["span"] for key in source)
    if fallback:
        require(checkpoint_without(checkpoints[3], {"phase", "evaluations"}) ==
                checkpoint_without(checkpoints[0], {"phase", "evaluations"}),
                "variant FINAL falls back to SOURCE geometry")
    else:
        require(checkpoint_without(checkpoints[3], {"phase"}) ==
                checkpoint_without(checkpoints[2], {"phase"}),
                "variant FINAL equals PAIRED when spans remain within SOURCE")
    require(all(final[key]["span"] <= source[key]["span"] for key in source),
            "variant FINAL two-pad spans do not exceed SOURCE")
    return fallback


def rewrite_plan_fingerprint(checkpoint):
    canonical = checkpoint["inputPlanCanonical"]
    prefix = "task-packing-plan@1;outline=" + ",".join(
        str(value) for value in checkpoint["planOutline"]) + ";"
    body = canonical[len(prefix):].splitlines()
    require(body and canonical.startswith(prefix), "negative fingerprint canonical framing")
    body[-1] = body[-1] + ".negative"
    checkpoint["inputPlanCanonical"] = prefix + "\n".join(body) + "\n"
    checkpoint["inputPlanSha256"] = hashlib.sha256(
        checkpoint["inputPlanCanonical"].encode("utf-8")).hexdigest()


def span_reader_negatives(control_checkpoints, control_row, accepted_row, seed_pair,
                          variant_checkpoints, variant_row):
    rejected = []

    changed = copy.deepcopy(control_checkpoints)
    changed[0]["netSpans"].pop()
    try:
        validate_checkpoint_set(changed, control_row, accepted_row, seed_pair, "control-negative")
    except AssertionError as error:
        require(str(error) == "checkpoint exact two-pad net inventory",
                "missing-net negative rejected at exact inventory assertion")
        rejected.append("missing two-pad net")
    else:
        raise AssertionError("span reader accepted missing two-pad net")

    changed = copy.deepcopy(control_checkpoints)
    changed[0]["netSpans"][0]["centers"][0][0] += 1
    try:
        validate_checkpoint_set(changed, control_row, accepted_row, seed_pair, "control-negative")
    except AssertionError as error:
        require(str(error) == "checkpoint Manhattan span recomputes",
                "span arithmetic negative rejected at exact assertion")
        rejected.append("two-pad Manhattan span arithmetic")
    else:
        raise AssertionError("span reader accepted inconsistent Manhattan span")

    changed = copy.deepcopy(variant_checkpoints)
    paired_evaluations = changed[2]["evaluations"]
    changed[3]["evaluations"] = paired_evaluations - 1 if paired_evaluations > 0 else paired_evaluations + 1
    try:
        expected = expected_two_pad_nets(variant_row)
        maps = [validate_checkpoint(item, phase, expected, seed_pair,
                                    variant_row["counts"]["layoutParts"])
                for item, phase in zip(changed, CHECKPOINT_PHASES)]
        validate_variant_stages(changed, maps)
    except AssertionError as error:
        require(str(error) == "stage cumulative evaluations remain ordered and within shared cap" or
                str(error) == "final evaluations retain paired count",
                "stage count negative rejected at count/correlation assertion")
        rejected.append("changed FINAL cumulative evaluation count")
    else:
        raise AssertionError("span reader accepted changed FINAL evaluation count")

    changed = copy.deepcopy(variant_checkpoints)
    rewrite_plan_fingerprint(changed[3])
    try:
        expected = expected_two_pad_nets(variant_row)
        maps = [validate_checkpoint(item, phase, expected, seed_pair,
                                    variant_row["counts"]["layoutParts"])
                for item, phase in zip(changed, CHECKPOINT_PHASES)]
        validate_variant_stages(changed, maps)
    except AssertionError as error:
        require(str(error) in {
            "variant FINAL falls back to SOURCE geometry",
            "variant FINAL equals PAIRED when spans remain within SOURCE",
        }, "hash-valid stage correlation negative rejected at stage relation")
        rejected.append("hash-valid but uncorrelated FINAL plan")
    else:
        raise AssertionError("span reader accepted hash-valid uncorrelated FINAL plan")

    # Exercise the fallback accounting branch even when the actual arm does not fall back.
    synthetic = copy.deepcopy(variant_checkpoints)
    source_cp, paired_cp = synthetic[0], synthetic[2]
    source_net = source_cp["netSpans"][0]
    paired_net = next(item for item in paired_cp["netSpans"]
                      if item["netId"] == source_net["netId"])
    centers = paired_net["centers"]
    baseline_span = source_net["span"]
    centers[0][0] = centers[1][0] + baseline_span + 10
    paired_net["span"] = manhattan(centers[0], centers[1])
    rewrite_plan_fingerprint(paired_cp)
    synthetic[3] = copy.deepcopy(source_cp)
    synthetic[3]["phase"] = "FINAL"
    synthetic[3]["evaluations"] = paired_cp["evaluations"]
    expected = expected_two_pad_nets(variant_row)
    synthetic_maps = [validate_checkpoint(item, phase, expected, seed_pair,
                                          variant_row["counts"]["layoutParts"])
                      for item, phase in zip(synthetic, CHECKPOINT_PHASES)]
    require(validate_variant_stages(synthetic, synthetic_maps) is True,
            "synthetic cap violation selects source-geometry fallback")
    synthetic[3]["evaluations"] = max(0, synthetic[3]["evaluations"] - 1)
    try:
        synthetic_maps = [validate_checkpoint(item, phase, expected, seed_pair,
                                              variant_row["counts"]["layoutParts"])
                          for item, phase in zip(synthetic, CHECKPOINT_PHASES)]
        validate_variant_stages(synthetic, synthetic_maps)
    except AssertionError as error:
        require(str(error) == "stage cumulative evaluations remain ordered and within shared cap" or
                str(error) == "final evaluations retain paired count",
                "fallback count negative rejected at count assertion")
        rejected.append("synthetic fallback preserves paired evaluation count")
    else:
        raise AssertionError("span reader accepted fallback that reset counted evaluations")
    return rejected


def same_logical_identity(row, accepted_row, arm):
    identity_keys = ("liveGraphSha256Before", "liveGraphSha256After", "liveGraphUnchanged",
                     "componentIds", "padIds", "netIds")
    for key in identity_keys:
        require(row["identity"][key] == accepted_row["identity"][key],
                "logical identity preserved " + arm + " " + key)
    for key in ("logicalParts", "layoutParts", "logicalPads", "layoutPads", "logicalNets"):
        require(row["counts"][key] == accepted_row["counts"][key],
                "logical identity counts preserved " + arm + " " + key)
    require(row["layout"]["nets"] == accepted_row["layout"]["nets"],
            "logical net and pad membership preserved " + arm)
    def pad_identity(pad):
        return {key: pad[key] for key in ("padId", "componentId", "terminalId", "netId")}
    require(sorted((pad_identity(item) for item in row["layout"]["pads"]),
                   key=lambda item: item["padId"]) ==
            sorted((pad_identity(item) for item in accepted_row["layout"]["pads"]),
                   key=lambda item: item["padId"]),
            "logical pad endpoints preserved " + arm)


def metric_tuple(row):
    physical = row["physicalMetrics"]
    independent = row["independentMetrics"]
    names = ("area", "copper", "vias", "protectedVoid")
    require(physical["available"] is True and independent["matchesProduction"] is True and
            all(type(physical[name]) is int and physical[name] >= 0 and
                type(independent[name]) is int and independent[name] == physical[name]
                for name in names),
            "actual independent physical metric tuple")
    return {name: physical[name] for name in names}


def actual_variant_decision(row, accepted_row):
    baseline = metric_tuple(accepted_row)
    actual = metric_tuple(row)
    geometry = row["identity"]["selectedGeometrySha256After"]
    require(row["identity"]["selectedGeometrySha256Before"] == geometry and
            row["identity"]["selectedGeometryUnchanged"] is True,
            "selected geometry remains unchanged during export")
    incumbent = accepted_row["identity"]["selectedGeometrySha256After"]
    require(row["packingProbe"]["baselineGeometrySha256"] == incumbent,
            "variant original incumbent binding")
    if geometry == incumbent:
        require(actual == baseline, "unchanged selected incumbent retains baseline metrics")
        return "NO_GO", baseline, actual
    selected = (actual["area"] < baseline["area"] and
                actual["copper"] <= baseline["copper"] and
                actual["vias"] <= baseline["vias"] and
                actual["protectedVoid"] <= baseline["protectedVoid"])
    require(selected and row["packingProbe"]["disposition"] == "SELECTED",
            "changed selected geometry satisfies original strict four-metric selector")
    return "GO", baseline, actual


def check_selected_spans(row, source_map):
    actual = expected_two_pad_nets(row)
    require(set(actual) == set(source_map) and
            all(actual[key]["span"] <= source_map[key]["span"] for key in source_map),
            "selected variant physical two-pad spans do not exceed SOURCE")
    return actual


def row_reader_negatives(row, sampler, reader):
    module = runpy.run_path(str(PRIOR / "qualify.py"))
    return module["reader_negatives"](row, sampler, reader["validate_row"])


def cause_reader_negative(control):
    changed = copy.deepcopy(control["checkpoints"][3])
    rewrite_plan_fingerprint(changed)
    try:
        validate_causes(control["events"], control["row"], changed)
    except AssertionError as error:
        require(str(error) == "packing catch binds FINAL proposal",
                "hash-valid cause correlation negative failed at exact relation")
        return "hash-valid FINAL change rejected by actual cause correlation"
    raise AssertionError("cause reader accepted uncorrelated FINAL proposal")


def unchanged_counter_negative(row, sampler, accepted_row, reader):
    changed = copy.deepcopy(row)
    probe = changed["packingProbe"]
    literal = ";expansions=" + str(probe["expansions"]) + ";"
    require(probe["canonical"].count(literal) == 1,
            "packing counter negative canonical framing")
    probe["expansions"] += 1
    probe["canonical"] = probe["canonical"].replace(
        literal, ";expansions=" + str(probe["expansions"]) + ";")
    reader["validate_row"](changed, sampler, "composed")
    try:
        require(canonical_millis(changed) == canonical_millis(accepted_row),
                "control row differs from accepted receipt beyond packing milliseconds")
    except AssertionError as error:
        require(str(error) == "control row differs from accepted receipt beyond packing milliseconds",
                "coherent ordinary expansion negative rejected at exact comparison")
        return "coherent packing expansion change rejected by exact nonclock comparison"
    raise AssertionError("reader accepted changed ordinary packing expansion count")


def qualify(private_root):
    private_root = Path(private_root)
    require(private_root.is_dir(), "private root path")
    seal = audit_inputs(private_root)
    baseline = read(HERE / "baseline-inputs.json")
    manifest = read(HERE / "private-inputs.json")
    review = read(HERE / "source-review.json")
    require(manifest["status"] == "GENERATED_PRIVATE_TWO_ARM_SPAN_PROBE" and
            manifest["privateInputs"] == seal["privateInputs"] and
            set(manifest["privateInputs"]) == PRIVATE_INPUTS,
            "factory private input manifest and eight sealed source inputs")
    require(review["status"] == "PASS_PRIVATE_SPAN_PROBE_SOURCE_ONLY" and
            review["privateInputManifestSha256"] == sha(HERE / "private-inputs.json") and
            review["privateInputs"] == manifest["privateInputs"],
            "root source-only review and private input pins")
    require({name: sha(private_root / name) for name in manifest["privateInputs"]} ==
            manifest["privateInputs"], "all private exporter/adapter/owner source pins")
    expected_execution = baseline["expectedExecution"]
    require(set(expected_execution) == {"Protocol", "SourceDigest", "ScriptDigest", "WebDigest",
                                        "Digest", "FileCount"} and
            expected_execution == read(HERE / "execution-before.json") ==
            read(HERE / "execution-after.json"),
            "six-field execution provenance unchanged")
    require(baseline["request"] == REPLAY and
            sha(HERE / "scope.md") == baseline["gates"]["scope.md"] and
            sha(HERE / "inputs.py") == baseline["gates"]["inputs.py"] and
            sha(HERE / "movement-review.json") == baseline["gates"]["movement-review.json"],
            "frozen scope, input recorder, and movement review")
    require(sha(PRIOR / "qualify.py") == baseline["publishedEvidence"]["qualify.py"] and
            sha(PRIOR / "composed" / "receipt.txt") == baseline["publishedEvidence"]["composed/receipt.txt"],
            "published row oracle and accepted receipt remain pinned")

    canary_timing, canaries = check_canaries(private_root)
    reader, adapted_sha, reader_changes = previous_reader()
    accepted_row, accepted_sampler, accepted_events = accepted_observation(reader)
    control_timing = phase_result("control")
    variant_timing = phase_result("variant")
    control = check_native_phase("control", control_timing, reader,
                                 accepted_row, accepted_sampler, accepted_events)
    variant = check_native_phase("variant", variant_timing, reader,
                                 accepted_row, accepted_sampler, accepted_events)
    same_logical_identity(control["row"], accepted_row, "control")
    same_logical_identity(variant["row"], accepted_row, "variant")

    seed_pairs = {(event["layoutSeed"], event["routingSeed"]) for event in accepted_events}
    require(len(seed_pairs) == 1, "accepted cause events share one actual layout/routing seed pair")
    seed_pair = next(iter(seed_pairs))
    expected_inventory, control_maps = validate_checkpoint_set(
        control["checkpoints"], control["row"], accepted_row, seed_pair, "control")
    variant_inventory, variant_maps = validate_checkpoint_set(
        variant["checkpoints"], variant["row"], accepted_row, seed_pair, "variant")
    require(expected_inventory == variant_inventory,
            "control and variant use the same ordinary logical two-pad inventory")
    require(control["checkpoints"][0] == variant["checkpoints"][0],
            "control SOURCE equals variant SOURCE in all checkpoint fields")
    validate_control_stages(control["checkpoints"], control_maps)
    variant_fallback = validate_variant_stages(variant["checkpoints"], variant_maps)
    validate_causes(control["events"], control["row"], control["checkpoints"][3])
    validate_causes(variant["events"], variant["row"], variant["checkpoints"][3])
    selected_variant_spans = check_selected_spans(variant["row"], variant_maps[0])

    decision, baseline_metrics, variant_metrics = actual_variant_decision(
        variant["row"], accepted_row)
    row_negatives = row_reader_negatives(variant["row"], variant["sampler"], reader)
    counter_negative = unchanged_counter_negative(
        control["row"], control["sampler"], accepted_row, reader)
    span_negatives = span_reader_negatives(
        control["checkpoints"], control["row"], accepted_row, seed_pair,
        variant["checkpoints"], variant["row"])
    cause_negative = cause_reader_negative(control)

    assessment_path = HERE / "assessment.json"
    require(not assessment_path.exists(), "preserve prior assessment")
    assessment = {
        "status": "PASS_FINITE_PRIVATE_SPAN_PROBE",
        "decision": decision,
        "inputSealSha256": sha(HERE / "export-inputs.json"),
        "privateInputManifestSha256": sha(HERE / "private-inputs.json"),
        "sourceReviewSha256": sha(HERE / "source-review.json"),
        "execution": expected_execution,
        "reusedReader": {
            "publishedQualifierSha256": baseline["publishedEvidence"]["qualify.py"],
            "adaptedPublishedReaderSha256": adapted_sha,
            "guardedChanges": reader_changes,
        },
        "acceptedReplay": REPLAY,
        "control": {
            "rowResult": control["rowResult"],
            "receiptSha256": control["receiptSha256"],
            "nativeSha256": control["nativeSha256"],
            "elapsedSeconds": control["timing"]["elapsedSeconds"],
            "rowAndSamplerMatchAccepted": True,
            "typedCauseEventsMatchAccepted": True,
            "checkpoints": control["checkpoints"],
        },
        "variant": {
            "rowResult": variant["rowResult"],
            "receiptSha256": variant["receiptSha256"],
            "nativeSha256": variant["nativeSha256"],
            "elapsedSeconds": variant["timing"]["elapsedSeconds"],
            "metrics": {"baseline": baseline_metrics, "selected": variant_metrics},
            "fallbackApplied": variant_fallback,
            "typedCauseEvents": variant["events"],
            "selectedPadSpans": selected_variant_spans,
            "checkpoints": variant["checkpoints"],
        },
        "twoPadNetCount": len(expected_inventory),
        "canaries": {"elapsedSeconds": canary_timing["elapsedSeconds"], "checks": canaries},
        "rowNegativeControls": row_negatives,
        "unchangedCounterNegativeControl": counter_negative,
        "spanNegativeControls": span_negatives,
        "causeNegativeControl": cause_negative,
        "limits": "One actual accepted composed replay with one sealed control and one private variant only. The observer reports cumulative placement evaluations, not per-pair visits. A source-relative pad-span cap is a geometric proposal filter, not a routing, electrical, manufacturing, optimality, all-seed, production, full-matrix, build, or player-flow claim.",
    }
    assessment_path.write_text(json.dumps(assessment, indent=2) + "\n", encoding="utf-8")
    return assessment


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--private-root", type=Path, required=True)
    args = parser.parse_args()
    result = qualify(args.private_root)
    print("PASS finite private two-pad span probe decision=" + result["decision"])


if __name__ == "__main__":
    main()
