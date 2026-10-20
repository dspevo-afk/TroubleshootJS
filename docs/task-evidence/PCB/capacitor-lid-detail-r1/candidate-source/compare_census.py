#!/usr/bin/env python3
"""Strictly compare the frozen 44-board packing3 census with its candidate."""

import argparse
import copy
import hashlib
import json
import re
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
KINDS = (
    "PCB_NORMAL_PACKING_CASE",
    "PCB_WHITESPACE_CASE",
    "PCB_NORMAL_SILK_CASE",
    "PCB_NORMAL_REFERENCE_CASE",
    "PCB_LEAD_SILK_CASE",
)
EXPECTED_CASES = 44
EXPECTED_PAIRS = EXPECTED_CASES * len(KINDS)
LAYOUT_VERSION = 19
PACKING_VERSION = 3
BASELINE_SHA256 = "766fe39ad1e23ae18e08b7de1244a325826a5e42a9c5a5cba5cbe87b0d582510"
CLOCK_FIELDS = ("elapsedMillis", "maxAdvanceMillis", "generationRoutingMillis")
METRIC_PATHS = (
    ("outline", "area"),
    ("copperManhattanLength",),
    ("viaCount",),
    ("whitespace",),
)
METRIC_NAMES = ("area", "copper", "vias", "protectedVoid")

# These are the maintained emitters and identity owners consumed by this reader.
# Pinning them keeps the frozen raw schema and its clock/epoch interpretation explicit.
SOURCE_PINS = {
    "tests/contracts/PcbNormalPackingCensusContractTest.java":
        "d27dc575ff767da34a3d0eab70755777e65602f72162e634daa23757cf4691e8",
    "tests/contracts/PcbFunctionalGroupingContractTest.java":
        "c9396d0e93b9d042098f9e61efa0fa450e085fff96ae700b9690ebb64091a8d3",
    "tests/contracts/PcbCanvasBoundaryContractTest.java":
        "4aaa2d718ca2845eb0e38a0e6d9bcde5ed37144ea4f3199d795c99242e819aa6",
    "src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java":
        "724e4d6822eaeb3178efdfba30340ff12f0bdb1821734666b1a49df47ca5a8e",
    "src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java":
        "5a3ea41332c85ff1ea263fd3952963fda1c845e9d9359460abcc271fdcd4d180",
    "src/com/lushprojects/circuitjs1/client/PcbRoutingWork.java":
        "20409e00ad33953c57bb043103e676866f2f5248ad2ed1ab93f6882ca9450031",
    "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java":
        "6f3af5412696b6dbf3e14593edf195151825483a9a034b7dadbb9043b854980f",
    "src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java":
        "e1ef75f6b00098d8d5a9a9965622a81463426eb470a8b50f6c0c8f9ae16ff97d",
    "src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java":
        "c7d2f17a3e4b2a892beae7856b5d00dce9af66f7d50d2c3f802ec74d8116cf68",
}


class CensusError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise CensusError(message)


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def unique_json_keys(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate JSON key: " + key)
        result[key] = value
    return result


def ending_of(line):
    if line.endswith("\r\n"):
        return "\r\n"
    if line.endswith("\n") or line.endswith("\r"):
        return line[-1:]
    return ""


def nonnegative_integer(value, label):
    require(type(value) is int and value >= 0,
            label + " must be a nonnegative JSON integer")


def validate_clock_frame(row, context):
    data = row["data"]
    require("status" in data and data["status"] == "ACCEPTED",
            context + " is not an accepted admission")
    require("phase" in data and data["phase"] == "FINALIZED",
            context + " is not finalized")
    require("elapsedMillis" in data,
            context + " is missing top-level elapsedMillis")
    nonnegative_integer(data["elapsedMillis"], context + " elapsedMillis")
    require("maxAdvanceMillis" in data,
            context + " is missing top-level maxAdvanceMillis")
    if data["maxAdvanceMillis"] is not None:
        nonnegative_integer(data["maxAdvanceMillis"],
                            context + " maxAdvanceMillis")
    require("generationRoutingMillis" in data,
            context + " is missing top-level generationRoutingMillis")
    nonnegative_integer(data["generationRoutingMillis"],
                        context + " generationRoutingMillis")


def clock_token_pattern(field):
    return re.compile(r'("' + re.escape(field) + r'":)(null|[0-9]+)(?=[,}])')


def mask_clock_body(body, context):
    for field in CLOCK_FIELDS:
        pattern = clock_token_pattern(field)
        matches = list(pattern.finditer(body))
        require(len(matches) == 1,
                context + " must frame exactly one raw " + field + " token")
        body = pattern.sub(r'\g<1>#CLOCK#', body, count=1)
    return body


def mask_clock_line(line, context):
    ending = ending_of(line)
    content = line[:-len(ending)] if ending else line
    marker, separator, body = content.partition(" ")
    require(marker == KINDS[0] and separator,
            context + " is not a framed packing observation")
    return marker + separator + mask_clock_body(body, context) + ending


def read_receipt(path):
    path = Path(path)
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise CensusError("receipt is not UTF-8: " + path.name) from error

    rows = {kind: [] for kind in KINDS}
    lines = text.splitlines(keepends=True)
    for number, line in enumerate(lines, 1):
        content = line[:-len(ending_of(line))] if ending_of(line) else line
        marker, separator, body = content.partition(" ")
        if not marker.startswith("PCB_"):
            continue
        require(separator and marker in rows,
                "unknown or malformed PCB row at line %d: %s" % (number, marker))
        try:
            data = json.loads(body, object_pairs_hook=unique_json_keys)
        except (json.JSONDecodeError, CensusError) as error:
            raise CensusError("invalid PCB JSON at line %d: %s" % (number, error)) from error
        require(type(data) is dict,
                "PCB row is not a JSON object at line %d" % number)
        record = {
            "data": data,
            "line": number,
            "raw": content,
            "ending": ending_of(line),
        }
        rows[marker].append(record)

    receipt = {
        "name": path.name,
        "sha256": sha256(raw),
        "path": str(path),
        "lines": lines,
        "rows": rows,
    }
    validate_frame(receipt)
    masked_lines = list(lines)
    for row in rows[KINDS[0]]:
        index = row["line"] - 1
        row["maskedRaw"] = mask_clock_line(
            lines[index], row_context(KINDS[0], row))
        masked_lines[index] = row["maskedRaw"]
    receipt["maskedText"] = "".join(masked_lines)
    return receipt


def row_context(kind, row):
    data = row["data"]
    return "%s family=%s root=%s" % (
        kind, data.get("family", "?"), data.get("root", data.get("seed", "?")))


def pair_index(receipt, kind):
    indexed = {}
    for row in receipt["rows"][kind]:
        data = row["data"]
        family, root = data.get("family"), data.get("root")
        require(type(family) is str and family and type(root) is str and root,
                "%s row is missing family/root identity" % kind)
        key = (family, root)
        require(key not in indexed,
                "%s has duplicate family/root identity %r" % (kind, key))
        indexed[key] = row
    require(len(indexed) == EXPECTED_CASES,
            "%s must contain exactly %d unique family/root rows" %
            (kind, EXPECTED_CASES))
    return indexed


def get_path(data, path):
    value = data
    for part in path:
        value = value[part]
    return value


def validate_hash(value, context):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None,
            context + " must be a lowercase SHA-256")


def validate_frame(receipt):
    for kind in KINDS:
        count = len(receipt["rows"][kind])
        require(count == EXPECTED_CASES,
                "%s has %d rows; expected exactly %d" %
                (kind, count, EXPECTED_CASES))

    packing = receipt["rows"][KINDS[0]]
    packing_keys = []
    for index, row in enumerate(packing):
        context = row_context(KINDS[0], row)
        data = row["data"]
        validate_clock_frame(row, context)
        family, root = data.get("family"), data.get("root")
        require(type(family) is str and family and type(root) is str and root,
                context + " is missing family/root identity")
        packing_keys.append((family, root))
        require(type(data.get("layoutAlgorithmVersion")) is int and
                data["layoutAlgorithmVersion"] == LAYOUT_VERSION,
                context + " has layoutAlgorithmVersion %r; expected current layout %d" %
                (data.get("layoutAlgorithmVersion"), LAYOUT_VERSION))
        require(data.get("seed") == root,
                context + " seed does not retain its exact signed root")
        graph_before, graph_after = data.get("graphBefore"), data.get("graphAfter")
        validate_hash(graph_before, context + " graphBefore")
        validate_hash(graph_after, context + " graphAfter")
        require(graph_before == graph_after,
                context + " changed its live graph during physical admission")
        validate_hash(data.get("geometrySha256"), context + " geometrySha256")
        for path, label in zip(METRIC_PATHS, METRIC_NAMES):
            nonnegative_integer(get_path(data, path), context + " metric " + label)
        outline = data.get("outline")
        require(type(outline) is dict and
                outline["area"] == outline["width"] * outline["height"],
                context + " outline area does not equal width times height")

    require(len(set(packing_keys)) == EXPECTED_CASES,
            "packing census contains duplicate or missing family/root cases")

    for kind in (KINDS[2], KINDS[3], KINDS[4]):
        observed = pair_index(receipt, kind)
        require(list(observed.keys()) == packing_keys,
                "%s corpus/order differs from the packing admissions" % kind)
        for key, row in observed.items():
            context = "%s family=%s root=%s" % (kind, key[0], key[1])
            data = row["data"]
            for field in ("geometryApartFromTitleSha256",
                          "physicalBoardSha256",
                          "geometryApartFromReferenceTextSha256",
                          "geometryApartFromLegendPositionAndEpochSha256"):
                if field in data:
                    validate_hash(data[field], context + " " + field)

    whitespace_rows = receipt["rows"][KINDS[1]]
    for index, (white, pack) in enumerate(zip(whitespace_rows, packing)):
        wdata, pdata = white["data"], pack["data"]
        context = "PCB_WHITESPACE_CASE pair %d" % index
        require(wdata.get("seed") == pdata.get("root"),
                context + " seed does not match its canonical packing case")
        require(wdata.get("parts") == pdata.get("logicalParts"),
                context + " part count does not match its packing admission")
        require(wdata.get("version") == 1,
                context + " has a noncurrent independent sampler version")
        require(wdata.get("scope") ==
                "GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;FREE_IS_NOT_PACKABLE_PROOF",
                context + " has an unexpected sampler scope")
        require(get_path(wdata, ("largestProtectedEmptyRectangle", "area")) ==
                pdata["whitespace"],
                context + " largest protected rectangle disagrees with production void")


def check_source_contract():
    hashes = {}
    contents = {}
    for relative, expected in SOURCE_PINS.items():
        source_path = REPO / relative
        try:
            raw = source_path.read_bytes()
        except OSError as error:
            raise CensusError("missing maintained census source: " + relative) from error
        actual = sha256(raw)
        require(actual == expected,
                "maintained census source changed: %s sha256=%s expected=%s" %
                (relative, actual, expected))
        hashes[relative] = actual
        contents[relative] = raw.decode("utf-8")

    generator = contents["src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java"]
    placement = contents["src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java"]
    probe = contents["src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java"]
    require(len(re.findall(r"\bCURRENT_VERSION\s*=\s*19\s*;", generator)) == 1,
            "current generator source does not frame layout epoch 19 exactly once")
    require(len(re.findall(r"\bCONNECTED_POCKET_PACKING_VERSION\s*=\s*3\s*;", placement)) == 1,
            "current placement source does not frame connected-pocket packing3 exactly once")
    require(probe.count('"normal-packing@3"') == 1,
            "current probe identity source does not frame normal-packing@3 exactly once")
    return hashes


def strict_equal(left, right):
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return left.keys() == right.keys() and all(
            strict_equal(left[key], right[key]) for key in left)
    if type(left) is list:
        return len(left) == len(right) and all(
            strict_equal(a, b) for a, b in zip(left, right))
    return left == right


def compare_value(kind, before, after, context, path, clock_differences):
    if type(before) is dict and type(after) is dict:
        require(before.keys() == after.keys(),
                "%s schema changed at %s" % (context, path))
        for key in before:
            next_path = path + "." + key
            if kind == KINDS[0] and key in CLOCK_FIELDS:
                require((before[key] is None) == (after[key] is None),
                        "%s clock availability changed at %s" % (context, next_path))
                if not strict_equal(before[key], after[key]):
                    clock_differences.append({
                        "family": context.split(" family=", 1)[1].split(" root=", 1)[0],
                        "root": context.rsplit(" root=", 1)[1],
                        "field": next_path,
                        "baseline": before[key],
                        "current": after[key],
                    })
                continue
            compare_value(kind, before[key], after[key], context,
                          next_path, clock_differences)
        return
    if type(before) is list and type(after) is list:
        require(len(before) == len(after),
                "%s list length changed at %s" % (context, path))
        for index, (left, right) in enumerate(zip(before, after)):
            compare_value(kind, left, right, context,
                          "%s[%d]" % (path, index), clock_differences)
        return
    require(strict_equal(before, after),
            "%s protected field changed at %s (baseline=%r current=%r)" %
            (context, path, before, after))


def normalized_receipt_text(receipt):
    return receipt["maskedText"]


def compare_frames(baseline, current):
    for label, receipt in (("baseline", baseline), ("current", current)):
        for row in receipt["rows"][KINDS[0]]:
            version = row["data"].get("layoutAlgorithmVersion")
            require(type(version) is int and version == LAYOUT_VERSION,
                    "%s %s has layoutAlgorithmVersion %r; expected current layout %d" %
                    (label, row_context(KINDS[0], row), version, LAYOUT_VERSION))
    require(list(baseline["rows"].keys()) == list(current["rows"].keys()),
            "receipt observation schemas differ")
    clock_differences = []
    paired_observations = []
    packing_keys = [(row["data"]["family"], row["data"]["root"])
                    for row in baseline["rows"][KINDS[0]]]

    for kind in KINDS:
        before_rows, after_rows = baseline["rows"][kind], current["rows"][kind]
        require(len(before_rows) == len(after_rows) == EXPECTED_CASES,
                "%s must have exactly %d paired rows" % (kind, EXPECTED_CASES))
        for index, (before, after) in enumerate(zip(before_rows, after_rows)):
            if kind == KINDS[1]:
                family, root = packing_keys[index]
                context = "%s family=%s root=%s" % (kind, family, root)
            else:
                context = row_context(kind, before)
                require(row_context(kind, before) == row_context(kind, after),
                        "%s canonical family/root order changed" % kind)
            compare_value(kind, before["data"], after["data"], context,
                          "$", clock_differences)
            before_masked = before["maskedRaw"] if kind == KINDS[0] else \
                before["raw"] + before["ending"]
            after_masked = after["maskedRaw"] if kind == KINDS[0] else \
                after["raw"] + after["ending"]
            require(before_masked == after_masked,
                    "%s raw observation text changed outside the three validated clock tokens" % context)
            paired_observations.append({
                "kind": kind,
                "family": family if kind == KINDS[1] else before["data"]["family"],
                "root": root if kind == KINDS[1] else before["data"]["root"],
                "baselineMaskedRawSha256": sha256(before_masked.encode("utf-8")),
                "currentMaskedRawSha256": sha256(after_masked.encode("utf-8")),
                "maskedRawExact": True,
            })

    require(len(paired_observations) == EXPECTED_PAIRS,
            "paired observation count is not exactly %d" % EXPECTED_PAIRS)
    require(normalized_receipt_text(baseline) == normalized_receipt_text(current),
            "complete receipt text changed outside validated clock tokens")

    metric_pairs = []
    for index, (before, after) in enumerate(zip(
            baseline["rows"][KINDS[0]], current["rows"][KINDS[0]])):
        bd, cd = before["data"], after["data"]
        values_before = {
            "area": get_path(bd, ("outline", "area")),
            "copper": bd["copperManhattanLength"],
            "vias": bd["viaCount"],
            "protectedVoid": bd["whitespace"],
        }
        values_current = {
            "area": get_path(cd, ("outline", "area")),
            "copper": cd["copperManhattanLength"],
            "vias": cd["viaCount"],
            "protectedVoid": cd["whitespace"],
        }
        require(strict_equal(values_before, values_current),
                "%s four production metrics changed" % row_context(KINDS[0], before))
        metric_pairs.append({
            "family": bd["family"],
            "root": bd["root"],
            "baseline": values_before,
            "current": values_current,
            "exact": True,
        })

    return {
        "status": "PASS",
        "cases": EXPECTED_CASES,
        "observationPairs": len(paired_observations),
        "rowCounts": {kind: EXPECTED_CASES for kind in KINDS},
        "rawReceiptTextEqualAfterClockMask": True,
        "allFourMetricsExact": True,
        "metricPairs": metric_pairs,
        "clockFieldsAllowed": list(CLOCK_FIELDS),
        "clockDifferences": clock_differences,
        "pairedObservations": paired_observations,
        "baseline": {"file": baseline["name"], "sha256": baseline["sha256"]},
        "current": {"file": current["name"], "sha256": current["sha256"]},
    }


def set_raw_scalar(frame, kind, row_index, path, value):
    row = frame["rows"][kind][row_index]
    data_value = row["data"]
    for part in path[:-1]:
        data_value = data_value[part]
    data_value[path[-1]] = value

    line_index = row["line"] - 1
    line = frame["lines"][line_index]
    ending = ending_of(line)
    content = line[:-len(ending)] if ending else line
    marker, separator, body = content.partition(" ")
    require(marker == kind and separator,
            "canary cannot locate raw row framing for " + kind)
    field = path[-1]
    pattern = re.compile(r'("' + re.escape(field) + r'":)(?:"(?:\\.|[^"\\])*"|null|-?[0-9]+|true|false)(?=[,}])')
    matches = list(pattern.finditer(body))
    require(len(matches) == 1,
            "canary requires one raw scalar for " + ".".join(path))
    body = pattern.sub(lambda match: match.group(1) + json.dumps(value, separators=(",", ":")),
                       body, count=1)
    updated = marker + separator + body + ending
    frame["lines"][line_index] = updated
    row["raw"] = marker + separator + body
    row["ending"] = ending
    if kind == KINDS[0]:
        row["maskedRaw"] = mask_clock_line(updated, row_context(kind, row))
        masked_lines = list(frame["lines"])
        for packing_row in frame["rows"][KINDS[0]]:
            masked_lines[packing_row["line"] - 1] = packing_row["maskedRaw"]
        frame["maskedText"] = "".join(masked_lines)


def canary_expect_rejection(name, baseline, current, reason_fragment):
    try:
        compare_frames(baseline, current)
    except CensusError as error:
        message = str(error)
        require(reason_fragment in message,
                "canary %s rejected for the wrong reason: %s" % (name, message))
        return {"name": name, "status": "PASS_REJECTED", "reason": message}
    raise CensusError("canary %s unexpectedly accepted" % name)


def run_canaries(baseline):
    # Use a genuine accepted baseline as both same-epoch control frames.
    control = copy.deepcopy(baseline)
    positive = copy.deepcopy(baseline)
    for field in CLOCK_FIELDS:
        eligible = [index for index, row in enumerate(baseline["rows"][KINDS[0]])
                    if row["data"][field] is not None]
        require(bool(eligible), "positive control needs an actual measured " + field)
        index = eligible[0]
        replacement = baseline["rows"][KINDS[0]][index]["data"][field] + 1
        set_raw_scalar(positive, KINDS[0], index, (field,), replacement)
    positive_result = compare_frames(control, positive)
    require(positive_result["status"] == "PASS" and
            len(positive_result["clockDifferences"]) == len(CLOCK_FIELDS),
            "positive clock-only canary did not record all three changed clock values")

    rejections = []
    changed_area = copy.deepcopy(baseline)
    original_area = get_path(changed_area["rows"][KINDS[0]][0]["data"], ("outline", "area"))
    set_raw_scalar(changed_area, KINDS[0], 0, ("outline", "area"), original_area + 1)
    rejections.append(canary_expect_rejection(
        "changed-area", control, changed_area, "protected field changed at $.outline.area"))

    changed_geometry = copy.deepcopy(baseline)
    original_hash = changed_geometry["rows"][KINDS[0]][0]["data"]["geometrySha256"]
    replacement_hash = ("0" if original_hash[0] != "0" else "1") + original_hash[1:]
    set_raw_scalar(changed_geometry, KINDS[0], 0,
                   ("geometrySha256",), replacement_hash)
    rejections.append(canary_expect_rejection(
        "changed-geometry-hash", control, changed_geometry, "protected field changed at $.geometrySha256"))

    changed_work = copy.deepcopy(baseline)
    original_work = changed_work["rows"][KINDS[0]][0]["data"]["generationRoutingExpansions"]
    set_raw_scalar(changed_work, KINDS[0], 0,
                   ("generationRoutingExpansions",), original_work + 1)
    rejections.append(canary_expect_rejection(
        "changed-nonclock-routing-work", control, changed_work,
        "protected field changed at $.generationRoutingExpansions"))

    wrong_epoch = copy.deepcopy(baseline)
    set_raw_scalar(wrong_epoch, KINDS[0], 0,
                   ("layoutAlgorithmVersion",), LAYOUT_VERSION - 1)
    rejections.append(canary_expect_rejection(
        "current-epoch-framing", control, wrong_epoch,
        "expected current layout 19"))

    changed_availability = copy.deepcopy(baseline)
    unmeasured = [index for index, row in enumerate(baseline["rows"][KINDS[0]])
                  if row["data"]["maxAdvanceMillis"] is None]
    require(bool(unmeasured), "negative control needs an actual unmeasured advance clock")
    set_raw_scalar(changed_availability, KINDS[0], unmeasured[0],
                   ("maxAdvanceMillis",), 0)
    rejections.append(canary_expect_rejection(
        "clock-availability", control, changed_availability,
        "clock availability changed at $.maxAdvanceMillis"))

    return {
        "status": "PASS",
        "baselineControlSha256": baseline["sha256"],
        "positiveClockOnly": {
            "status": "PASS",
            "sameLayoutEpoch": LAYOUT_VERSION,
            "samePackingVersion": PACKING_VERSION,
            "recordedClockDifferences": len(positive_result["clockDifferences"]),
        },
        "negativeCases": rejections,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, help="frozen accepted census receipt")
    parser.add_argument("--current", required=True, help="candidate census receipt")
    parser.add_argument("--output", required=True, help="JSON comparison result")
    arguments = parser.parse_args()

    source_hashes = check_source_contract()
    baseline = read_receipt(arguments.baseline)
    require(baseline["sha256"] == BASELINE_SHA256,
            "baseline raw SHA-256 changed: %s" % baseline["sha256"])
    current = read_receipt(arguments.current)
    canaries = run_canaries(baseline)
    result = compare_frames(baseline, current)
    result["interpretation"] = {
        "baselineLayoutAlgorithmVersion": LAYOUT_VERSION,
        "currentLayoutAlgorithmVersion": LAYOUT_VERSION,
        "baselineConnectedPocketPackingVersion": PACKING_VERSION,
        "currentConnectedPocketPackingVersion": PACKING_VERSION,
        "packingVersionEvidence": "pinned accepted packing3 baseline SHA plus pinned current source constant and normal-packing@3 receipt identity",
        "sourceSha256": source_hashes,
    }
    result["canaries"] = canaries
    result["limits"] = [
        "Finite paired 44-case accepted census; no all-seed or optimality claim.",
        "The maintained receipt emits layoutAlgorithmVersion directly; packing3 is bound by the pinned baseline and unchanged, source-pinned packing3 implementation.",
        "Only elapsedMillis, maxAdvanceMillis, and generationRoutingMillis may vary; all clock values are validated before masking.",
    ]

    output = Path(arguments.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "status": "PASS",
        "cases": result["cases"],
        "observationPairs": result["observationPairs"],
        "clockDifferences": len(result["clockDifferences"]),
        "canaries": result["canaries"]["status"],
        "output": output.name,
    }, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (CensusError, OSError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
