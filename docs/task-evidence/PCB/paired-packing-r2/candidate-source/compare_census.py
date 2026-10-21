#!/usr/bin/env python3
"""Compare the maintained 44-case normal-packing census with its pinned baseline."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASELINE = HERE.parent / "to92-molded-detail-r2" / "census-receipt.txt"
CURRENT = HERE / "census-receipt.txt"
OUTPUT = HERE / "census-comparison.json"
BASELINE_SHA256 = "e6194601188b138d55fa27eff4fd4e2bfd98a73ca64ed3316527a0ced9992c88"
FAMILIES = (
    "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR",
    "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT",
    "SENSOR_CONTROL", "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL",
)
ROOTS = ("0", "17", "13", "-9223372036854775808")
KINDS = ("PCB_WHITESPACE_CASE", "PCB_NORMAL_PACKING_CASE", "PCB_NORMAL_SILK_CASE",
         "PCB_NORMAL_REFERENCE_CASE", "PCB_LEAD_SILK_CASE")
SOURCE_PINS = {
    "tests/contracts/PcbNormalPackingCensusContractTest.java": "d27dc575ff767da34a3d0eab70755777e65602f72162e634daa23757cf4691e8",
    "tests/contracts/PcbFunctionalGroupingContractTest.java": "c9396d0e93b9d042098f9e61efa0fa450e085fff96ae700b9690ebb64091a8d3",
    "tests/contracts/PcbCanvasBoundaryContractTest.java": "4aaa2d718ca2845eb0e38a0e6d9bcde5ed37144ea4f3199d795c99242e819aa6",
    "src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java": "724e4d6822eaeb3178efdfbaa30340ff12f0bdb1821734666b1a49df47ca5a8e",
    "src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java": "c7d2f17a3e4b2a892beae7856b5d00dce9af66f7d50d2c3f802ec74d8116cf68",
    "src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java": "5a3ea41332c85ff1ea263fd3952963fda1c845e9d9359460abcc271fdcd4d180",
    "src/com/lushprojects/circuitjs1/client/PhysicalBoardFingerprint.java": "e6b8a1bca49ff913e614171c65bc86df9f6a5a39ed28cb44744b8e88cf9daf06",
    "src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java": "9dcf4917692f32071574ef5adf8f75aa55ef0e6a9a3159d3011549be2b4fbda3",
    "src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java": "1144f0575cbbae802db890100a460fda9a8541c79459629c06769a4ee9d6fceb",
    "src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java": "087287969fcdb6c18e1b38ed015054c4c16e25bad81823f936fe79843e61d66b",
    "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java": "7f27c83d12f47579a7fea50b496c2f0320e833e412f0aebe82594a62c7184750",
}


class GateFailure(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise GateFailure(message)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def display_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.name


def source_pins() -> dict[str, str]:
    result = {}
    for relative, expected in SOURCE_PINS.items():
        path = ROOT / relative
        actual = sha(path.read_bytes())
        require(actual == expected, f"consumed source changed: {relative} {actual}")
        result[relative] = actual
    constraints = (ROOT / "src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java").read_text("utf-8")
    generator = (ROOT / "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java").read_text("utf-8")
    probe = (ROOT / "src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java").read_text("utf-8")
    require(re.search(r"CONNECTED_POCKET_PACKING_VERSION\s*=\s*4\s*;", constraints) is not None,
            "current normal connected-pocket packing version is not 4")
    require(re.search(r"CURRENT_VERSION\s*=\s*19\s*;", generator) is not None,
            "normal layout epoch is not explicitly 19")
    require("normal-packing@3" in probe, "packing receipt identity changed from normal-packing@3")
    return result


def no_duplicate_keys(pairs):
    out = {}
    for key, value in pairs:
        if key in out:
            raise GateFailure(f"duplicate JSON key: {key}")
        out[key] = value
    return out


def reject_constant(value):
    raise GateFailure(f"non-standard JSON number: {value}")


def parse_receipt(path: Path, label: str) -> dict:
    raw = path.read_bytes()
    digest = sha(raw)
    if label == "baseline":
        require(digest == BASELINE_SHA256, f"baseline receipt SHA-256 mismatch: {digest}")
    text = raw.decode("utf-8", errors="strict")
    require(not text.startswith("\ufeff"), f"{label} receipt must not have a UTF-8 BOM")
    rows, passes = [], []
    for number, line in enumerate(text.splitlines(), 1):
        if not line:
            continue
        if line.startswith("PASS: PCB normal packing census "):
            match = re.fullmatch(
                r'PASS: PCB normal packing census .*? cases=(\d+) accepted=(\d+) rejected=(\d+) assertions=(\d+) suite="([^"]+)"', line)
            require(match is not None, f"{label}:{number}: malformed census pass summary")
            passes.append(tuple(match.groups()))
            continue
        match = re.fullmatch(r"(\S+) (\{.*\})", line)
        require(match is not None and match.group(1) in KINDS,
                f"{label}:{number}: unexpected receipt line")
        try:
            value = json.loads(match.group(2), object_pairs_hook=no_duplicate_keys,
                               parse_constant=reject_constant)
        except (json.JSONDecodeError, UnicodeError) as error:
            raise GateFailure(f"{label}:{number}: invalid JSON: {error}") from error
        require(type(value) is dict, f"{label}:{number}: row is not an object")
        rows.append({"kind": match.group(1), "data": value, "sha256": sha(line.encode("utf-8")), "line": number})
    require(len(passes) == 8 and len({row[4] for row in passes}) == 8,
            f"{label}: expected eight distinct census suite summaries")
    require(sum(int(row[0]) for row in passes) == 44 and
            sum(int(row[1]) for row in passes) == 44 and sum(int(row[2]) for row in passes) == 0,
            f"{label}: suite summaries do not account for 44 accepted cases")
    require(len(rows) == 220, f"{label}: expected 220 observation rows, found {len(rows)}")
    cases = []
    for offset in range(0, len(rows), 5):
        group = rows[offset:offset + 5]
        require(tuple(row["kind"] for row in group) == KINDS,
                f"{label}:{group[0]['line']}: observation rows are missing or out of order")
        packing = group[1]["data"]
        family, root = packing.get("family"), packing.get("root")
        require(type(family) is str and type(root) is str, f"{label}: missing family/root identity")
        whitespace = group[0]["data"]
        require(whitespace.get("seed") == root, f"{label}: whitespace seed does not match packing root")
        for row in group[2:]:
            require(row["data"].get("family") == family and row["data"].get("root") == root,
                    f"{label}: observation identity does not match packing row")
        cases.append({"key": (family, root), "rows": {row["kind"]: row for row in group}})
    expected = [(family, root) for family in FAMILIES for root in ROOTS]
    require([case["key"] for case in cases] == expected,
            f"{label}: cases are missing, duplicated, or outside canonical family/root order")
    return {"sha256": digest, "cases": cases, "passes": passes}


def strict_equal(a, b) -> bool:
    if type(a) is not type(b):
        return False
    if type(a) is dict:
        return a.keys() == b.keys() and all(strict_equal(a[k], b[k]) for k in a)
    if type(a) is list:
        return len(a) == len(b) and all(strict_equal(x, y) for x, y in zip(a, b))
    return a == b


def permitted(kind: str, path: str):
    if kind == "PCB_NORMAL_PACKING_CASE":
        exact = {
            "elapsedMillis": "wall-clock measurement varies by run",
            "maxAdvanceMillis": "largest construction advance is a wall-clock measurement",
            "generationRoutingMillis": "routing elapsed time varies by run",
            "geometrySha256": "full placement/routing fingerprint covers movable geometry and coordinates",
            "outline.x": "generic pair translation may reposition the board envelope",
            "outline.y": "generic pair translation may reposition the board envelope",
            "outline.width": "generic compaction may resize the measured envelope; area remains gated",
            "outline.height": "generic compaction may resize the measured envelope; area remains gated",
            "outline.area": "measured production area is compared as a nonregression metric",
            "copperManhattanLength": "measured production copper is compared as a nonregression metric",
            "holeCount": "hole inventory may change with routing; non-via hole count is held exact",
            "viaCount": "measured production vias are compared as a nonregression metric",
            "whitespace": "measured production protected-empty area is compared as a nonregression metric",
            "traceCount": "rerouting may change the routed segment count",
            "generationPlacementAttempts": "paired placement adds bounded search work",
            "generationRoutingAttempts": "candidate routing work may change under the retained cap",
            "generationRoutingExpansions": "candidate routing work may change under the retained cap",
            "routingRecovery": "route recovery detail may change with candidate geometry",
        }
        return exact.get(path)
    if kind == "PCB_WHITESPACE_CASE":
        exact = {
            "sampledCells": "grid dimensions follow the measured outline",
            "packageAccessOccupiedCells": "independent sampler occupancy follows candidate geometry",
            "protectedFreeCells": "independent sampler free cells follow candidate geometry",
            "protectedFreeFraction": "independent sampler fraction follows candidate geometry",
            "largestProtectedEmptyRectangle.x": "sampled empty rectangle may translate",
            "largestProtectedEmptyRectangle.y": "sampled empty rectangle may translate",
            "largestProtectedEmptyRectangle.width": "sampled empty rectangle may resize",
            "largestProtectedEmptyRectangle.height": "sampled empty rectangle may resize",
            "largestProtectedEmptyRectangle.area": "sampled empty rectangle area is the independently measured protected-void metric",
        }
        return exact.get(path)
    if kind == "PCB_NORMAL_SILK_CASE":
        if path == "geometryApartFromTitleSha256":
            return "normalized physical geometry digest changes with placement/routing"
        if re.fullmatch(r"bounds\[[01]\]", path):
            return "public title position may follow board geometry; text and size stay exact"
    if kind == "PCB_NORMAL_REFERENCE_CASE":
        if path in ("physicalBoardSha256", "geometryApartFromReferenceTextSha256"):
            return "physical geometry digest changes with generic package movement"
        if re.fullmatch(r"labels\[\d+\]\.bounds\[[01]\]", path):
            return "public ink position may follow package geometry; text/style/target/size stay exact"
    if kind == "PCB_LEAD_SILK_CASE":
        if path == "geometryApartFromLegendPositionAndEpochSha256":
            return "normalized geometry digest changes with component/copper movement; epoch is pinned separately"
        if path == "overlaps":
            return "an existing lead/silk overlap may be removed; new overlaps are rejected"
        if re.fullmatch(r"components\[\d+\]\.leads\[\d+\]\[[01]\]", path):
            return "whole-component translation moves every physical lead by the same delta"
        if re.fullmatch(r"labels\[\d+\]\.bounds\[[01]\]", path):
            return "public ink position may follow geometry; label identity and size stay exact"
    return None


def leaves(a, b, prefix=""):
    if type(a) is dict and type(b) is dict:
        require(a.keys() == b.keys(), f"field inventory changed at {prefix or '<row>'}")
        for key in sorted(a):
            yield from leaves(a[key], b[key], f"{prefix}.{key}" if prefix else key)
    elif type(a) is list and type(b) is list:
        require(len(a) == len(b), f"array length changed at {prefix}")
        for i, (left, right) in enumerate(zip(a, b)):
            yield from leaves(left, right, f"{prefix}[{i}]")
    elif not strict_equal(a, b):
        yield prefix, a, b


def int_field(value, label, minimum=0):
    require(type(value) is int and value >= minimum, f"invalid integer {label}: {value!r}")
    return value


def rect_inside(rect, outline, label):
    require(type(rect) is list and len(rect) == 4 and all(type(v) is int for v in rect),
            f"invalid rectangle {label}")
    x, y, width, height = rect
    require(width >= 0 and height >= 0 and x >= outline["x"] and y >= outline["y"] and
            x + width <= outline["x"] + outline["width"] and
            y + height <= outline["y"] + outline["height"], f"{label} outside measured outline")


def validate_sampler(case, side):
    rows = case["rows"]
    packing = rows["PCB_NORMAL_PACKING_CASE"]["data"]
    sample = rows["PCB_WHITESPACE_CASE"]["data"]
    outline = packing["outline"]
    cols, lines = outline["width"] // 10, outline["height"] // 10
    total = cols * lines
    require(sample.get("version") == 1 and sample.get("gridUnits") == 10 and
            sample.get("scope") == "GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;FREE_IS_NOT_PACKABLE_PROOF",
            f"{side}: sampler scope/version changed for {case['key']}")
    require(0 < total <= 250000 and sample.get("sampledCells") == total,
            f"{side}: sampler grid count does not match outline for {case['key']}")
    occupied = int_field(sample.get("packageAccessOccupiedCells"), "packageAccessOccupiedCells")
    free = int_field(sample.get("protectedFreeCells"), "protectedFreeCells")
    require(occupied <= total and free <= total and occupied + free <= total,
            f"{side}: sampler occupied/free cell counts exceed grid for {case['key']}")
    fraction = sample.get("protectedFreeFraction")
    require(type(fraction) is float and fraction == free / float(total),
            f"{side}: sampler free fraction is inconsistent for {case['key']}")
    largest = sample.get("largestProtectedEmptyRectangle")
    require(type(largest) is dict, f"{side}: sampler largest-rectangle object missing")
    x, y, width, height = (largest.get(k) for k in ("x", "y", "width", "height"))
    for name, value in (("x", x), ("y", y), ("width", width), ("height", height),
                        ("area", largest.get("area"))):
        int_field(value, f"sampler rectangle {name}")
    require(width % 10 == height % 10 == 0 and largest["area"] == width * height,
            f"{side}: sampler rectangle area/grid dimensions are inconsistent for {case['key']}")
    require(x >= outline["x"] and y >= outline["y"] and
            x + width <= outline["x"] + cols * 10 and y + height <= outline["y"] + lines * 10,
            f"{side}: sampler rectangle escapes the sampled grid for {case['key']}")
    require((x - outline["x"]) % 10 == (y - outline["y"]) % 10 == 0,
            f"{side}: sampler rectangle is not grid aligned for {case['key']}")
    require(packing.get("whitespace") == largest["area"],
            f"{side}: production protected-void metric disagrees with sampler for {case['key']}")


def validate_case(case, side):
    key, rows = case["key"], case["rows"]
    pack = rows["PCB_NORMAL_PACKING_CASE"]["data"]
    outline = pack.get("outline")
    require(type(outline) is dict, f"{side}: outline missing for {key}")
    for name in ("x", "y"):
        require(type(outline.get(name)) is int, f"{side}: invalid outline {name} for {key}")
    width, height = int_field(outline.get("width"), "outline.width", 1), int_field(outline.get("height"), "outline.height", 1)
    area = int_field(outline.get("area"), "outline.area", 1)
    require(area == width * height, f"{side}: outline area does not equal width*height for {key}")
    require(pack.get("layoutAlgorithmVersion") == 19, f"{side}: layout epoch is not 19 for {key}")
    require(pack.get("status") == "ACCEPTED" and pack.get("phase") == "FINALIZED",
            f"{side}: case is not an accepted finalized admission for {key}")
    require(pack.get("graphBefore") == pack.get("graphAfter"), f"{side}: live graph changed during census for {key}")
    for name in ("graphBefore", "geometrySha256"):
        require(type(pack.get(name)) is str and re.fullmatch(r"[0-9a-f]{64}", pack[name]),
                f"{side}: invalid {name} for {key}")
    for name in ("logicalParts", "logicalPads", "logicalNets", "layoutParts", "layoutPads", "traceCount",
                 "copperManhattanLength", "holeCount", "viaCount", "generationPlacementAttempts",
                 "generationRoutingAttempts", "generationRoutingExpansions", "generationRoutingMillis", "whitespace"):
        int_field(pack.get(name), name)
    require(pack["logicalParts"] == pack["layoutParts"] and pack["logicalPads"] == pack["layoutPads"],
            f"{side}: incomplete logical component/pad placement for {key}")
    require(pack["viaCount"] <= pack["holeCount"], f"{side}: via count exceeds hole inventory for {key}")
    recovery = pack.get("routingRecovery")
    require(recovery is None or type(recovery) is str, f"{side}: invalid route recovery receipt for {key}")
    for name in ("elapsedMillis", "maxAdvanceMillis"):
        value = pack.get(name)
        require(value is None or (type(value) is int and value >= 0), f"{side}: invalid clock {name} for {key}")
    validate_sampler(case, side)

    silk = rows["PCB_NORMAL_SILK_CASE"]["data"]
    reference = rows["PCB_NORMAL_REFERENCE_CASE"]["data"]
    lead = rows["PCB_LEAD_SILK_CASE"]["data"]
    for row, name in ((silk, "geometryApartFromTitleSha256"),
                      (reference, "physicalBoardSha256"),
                      (reference, "geometryApartFromReferenceTextSha256"),
                      (lead, "geometryApartFromLegendPositionAndEpochSha256")):
        require(type(row.get(name)) is str and re.fullmatch(r"[0-9a-f]{64}", row[name]),
                f"{side}: invalid {name} for {key}")
    require(silk.get("title") == reference.get("labels", [{}])[0].get("text") and
            silk.get("bounds") == reference.get("labels", [{}])[0].get("bounds"),
            f"{side}: title ink differs between independent public observations for {key}")
    components, labels = reference.get("components"), reference.get("labels")
    require(type(components) is list and len(components) == pack["logicalParts"],
            f"{side}: reference component inventory is incomplete for {key}")
    require(type(labels) is list and reference.get("labelCount") == len(labels),
            f"{side}: public ink inventory/count mismatch for {key}")
    component_ids = [item.get("id") for item in components]
    require(component_ids == sorted(component_ids) and len(set(component_ids)) == len(component_ids),
            f"{side}: component identity order/uniqueness changed for {key}")
    label_ids = [item.get("id") for item in labels]
    require(label_ids == sorted(label_ids) and len(set(label_ids)) == len(label_ids),
            f"{side}: label identity order/uniqueness changed for {key}")
    by_label = {item["id"]: item for item in labels}
    for component in components:
        item = by_label.get("component:" + component["id"])
        require(item is not None and item.get("text") == component.get("name"),
                f"{side}: public reference text does not identify component {component.get('id')} for {key}")
    for item in labels:
        rect_inside(item.get("bounds"), outline, f"{side} label {item.get('id')} {key}")
        require(type(item.get("text")) is str and type(item.get("fontSize")) is int and
                type(item.get("bold")) is bool and
                (item.get("targetPadId") is None or type(item.get("targetPadId")) is str),
                f"{side}: invalid public ink metadata for {key}")
    rect_inside(silk.get("bounds"), outline, f"{side} title {key}")

    lead_components = lead.get("components")
    require(type(lead_components) is list and [c.get("id") for c in lead_components] == component_ids,
            f"{side}: lead/component identities do not match reference inventory for {key}")
    require([label.get("id") for label in lead.get("labels", [])] == label_ids,
            f"{side}: lead/silk label identities do not match reference inventory for {key}")
    ref_by_id = {label["id"]: label for label in labels}
    for label in lead["labels"]:
        require(label.get("bounds") == ref_by_id[label["id"]].get("bounds"),
                f"{side}: lead/silk and public reference label bounds disagree for {key}/{label.get('id')}")
    overlap_rows, calculated = lead.get("overlaps"), []
    require(type(overlap_rows) is list, f"{side}: lead/silk overlaps missing for {key}")
    for component in lead_components:
        leads = component.get("leads")
        require(type(leads) is list and len(leads) > 0, f"{side}: terminal lead inventory missing for {key}")
        deltas = []
        for i, rect in enumerate(leads):
            rect_inside(rect, outline, f"{side} lead {component['id']}/{i} {key}")
            deltas.append((rect[0], rect[1]))
        # The peer comparison below checks rigid movement by preserving size and one delta.
    for label in lead["labels"]:
        rect_inside(label.get("bounds"), outline, f"{side} lead/silk label {label.get('id')} {key}")
    for label in lead["labels"]:
        lx, ly, lw, lh = label["bounds"]
        for component in lead_components:
            for index, (x, y, width, height) in enumerate(component["leads"]):
                if x < lx + lw and x + width > lx and y < ly + lh and y + height > ly:
                    calculated.append({"label": label["id"], "component": component["id"], "terminal": index})
    require(overlap_rows == calculated, f"{side}: reported lead/silk collision inventory is inconsistent for {key}")


def changed_leaf_list(base_case, current_case):
    changes = []
    for kind in KINDS:
        left = base_case["rows"][kind]["data"]
        right = current_case["rows"][kind]["data"]
        for path, old, new in leaves(left, right):
            reason = permitted(kind, path)
            require(reason is not None, f"unapproved field change {kind}.{path}")
            changes.append({"row": kind, "field": path, "baseline": old, "current": new, "reason": reason})
    return changes


def compare_corpora(base, current):
    require(len(base["cases"]) == 44 and len(current["cases"]) == 44,
            f"census case count mismatch: baseline={len(base['cases'])} current={len(current['cases'])}")
    for side, corpus in (("baseline", base), ("current", current)):
        for case in corpus["cases"]:
            require(len(case["rows"]) == 5 and set(case["rows"]) == set(KINDS),
                    f"{side}: observation row count/type mismatch for {case['key']}")
    require([c["key"] for c in base["cases"]] == [c["key"] for c in current["cases"]],
            "case order/identity mismatch against canonical baseline")
    changes_by_case = []
    for before, after in zip(base["cases"], current["cases"]):
        changes = changed_leaf_list(before, after)
        bpack = before["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]
        cpack = after["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]
        metrics = (("area", bpack["outline"]["area"], cpack["outline"]["area"]),
                   ("copper", bpack["copperManhattanLength"], cpack["copperManhattanLength"]),
                   ("vias", bpack["viaCount"], cpack["viaCount"]),
                   ("protectedVoid", bpack["whitespace"], cpack["whitespace"]))
        for metric, old, new in metrics:
            require(new <= old, f"production metric regression {metric} for {before['key']}: {old} -> {new}")
        brec, crec = before["rows"]["PCB_LEAD_SILK_CASE"]["data"], after["rows"]["PCB_LEAD_SILK_CASE"]["data"]
        require(set(map(lambda row: json.dumps(row, sort_keys=True), crec["overlaps"])) <=
                set(map(lambda row: json.dumps(row, sort_keys=True), brec["overlaps"])),
                f"new lead/silk overlap for {before['key']}")
        validate_case(after, "current")
        require(bpack["layoutAlgorithmVersion"] == cpack["layoutAlgorithmVersion"] == 19,
                f"layout epoch mismatch for {before['key']}")
        require(bpack["holeCount"] - bpack["viaCount"] == cpack["holeCount"] - cpack["viaCount"],
                f"non-via physical hole inventory changed for {before['key']}")
        for bc, cc in zip(brec["components"], crec["components"]):
            require(len(bc["leads"]) == len(cc["leads"]), f"terminal lead count changed for {before['key']}/{bc['id']}")
            shifts = {(new[0] - old[0], new[1] - old[1]) for old, new in zip(bc["leads"], cc["leads"])}
            require(len(shifts) == 1, f"component leads did not move as one rigid translation for {before['key']}/{bc['id']}")
            for old, new in zip(bc["leads"], cc["leads"]):
                require(old[2:] == new[2:], f"physical lead size changed for {before['key']}/{bc['id']}")
        for kind in KINDS:
            row_changes = [c for c in changes if c["row"] == kind]
            for item in row_changes:
                if item["field"].endswith("Sha256"):
                    # Keep fingerprints as evidence, and require a separately visible non-clock delta.
                    clocks = {"elapsedMillis", "maxAdvanceMillis", "generationRoutingMillis"}
                    evidence = any(not c["field"].endswith("Sha256") and
                                   not (c["row"] == "PCB_NORMAL_PACKING_CASE" and c["field"] in clocks)
                                   for c in changes)
                    require(evidence, f"digest changed without explicit geometry/work delta for {before['key']}: {kind}.{item['field']}")
        changes_by_case.append({
            "family": before["key"][0], "root": before["key"][1],
            "metrics": {name: {"baseline": old, "current": new} for name, old, new in metrics},
            "rowHashes": {kind: {"baseline": before["rows"][kind]["sha256"],
                                 "current": after["rows"][kind]["sha256"]} for kind in KINDS},
            "changes": changes,
        })
    for case in base["cases"]:
        validate_case(case, "baseline")
    return changes_by_case


def mutate_hash(value):
    return ("0" if value[0] != "0" else "1") + value[1:]


def canaries(base):
    passed = []
    compare_corpora(base, copy.deepcopy(base))
    passed.append("baseline self-comparison")

    clock = copy.deepcopy(base)
    case = next(c for c in clock["cases"] if c["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["maxAdvanceMillis"] is not None)
    pack = case["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]
    pack["elapsedMillis"] += 1
    pack["generationRoutingMillis"] += 1
    pack["maxAdvanceMillis"] += 1
    compare_corpora(base, clock)
    passed.append("clock changes accepted")

    geometry = copy.deepcopy(base)
    case = geometry["cases"][0]
    rows = case["rows"]
    dx, dy = 10, 20
    pack = rows["PCB_NORMAL_PACKING_CASE"]["data"]
    pack["outline"]["x"] += dx; pack["outline"]["y"] += dy
    pack["geometrySha256"] = mutate_hash(pack["geometrySha256"])
    pack["copperManhattanLength"] = max(0, pack["copperManhattanLength"] - 1)
    sample = rows["PCB_WHITESPACE_CASE"]["data"]["largestProtectedEmptyRectangle"]
    sample["x"] += dx; sample["y"] += dy
    silk = rows["PCB_NORMAL_SILK_CASE"]["data"]
    silk["bounds"][0] += dx; silk["bounds"][1] += dy
    silk["geometryApartFromTitleSha256"] = mutate_hash(silk["geometryApartFromTitleSha256"])
    ref = rows["PCB_NORMAL_REFERENCE_CASE"]["data"]
    for label in ref["labels"]:
        label["bounds"][0] += dx; label["bounds"][1] += dy
    ref["physicalBoardSha256"] = mutate_hash(ref["physicalBoardSha256"])
    ref["geometryApartFromReferenceTextSha256"] = mutate_hash(ref["geometryApartFromReferenceTextSha256"])
    lead = rows["PCB_LEAD_SILK_CASE"]["data"]
    for component in lead["components"]:
        for rect in component["leads"]:
            rect[0] += dx; rect[1] += dy
    for label in lead["labels"]:
        label["bounds"][0] += dx; label["bounds"][1] += dy
    lead["geometryApartFromLegendPositionAndEpochSha256"] = mutate_hash(lead["geometryApartFromLegendPositionAndEpochSha256"])
    compare_corpora(base, geometry)
    passed.append("position movement and improving copper accepted")

    negatives = []
    def reject(name, mutate, expected):
        candidate = copy.deepcopy(base)
        mutate(candidate)
        try:
            compare_corpora(base, candidate)
        except GateFailure as error:
            require(expected in str(error), f"canary {name} failed for wrong reason: {error}")
            negatives.append(name)
            return
        raise GateFailure(f"canary {name} was incorrectly accepted")

    reject("admission", lambda c: c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("physicalAdmission", "ALTERED"), "physicalAdmission")
    reject("graph identity", lambda c: (c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("graphBefore", "0" * 64), c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("graphAfter", "0" * 64)), "graphAfter")
    reject("component identity", lambda c: c["cases"][0]["rows"]["PCB_NORMAL_REFERENCE_CASE"]["data"]["components"][0].__setitem__("id", "ALTERED"), "components[0].id")
    reject("pad target", lambda c: c["cases"][0]["rows"]["PCB_NORMAL_REFERENCE_CASE"]["data"]["labels"][-1].__setitem__("targetPadId", "ALTERED"), "targetPadId")
    reject("public text", lambda c: c["cases"][0]["rows"]["PCB_NORMAL_SILK_CASE"]["data"].__setitem__("title", "ALTERED"), "PCB_NORMAL_SILK_CASE.title")
    reject("outline area regression", lambda c: c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["outline"].__setitem__("area", c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["outline"]["area"] + 1), "metric regression area")
    reject("copper regression", lambda c: c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("copperManhattanLength", c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["copperManhattanLength"] + 1), "metric regression copper")
    reject("via regression", lambda c: (c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("viaCount", c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["viaCount"] + 1), c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("holeCount", c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["holeCount"] + 1)), "metric regression vias")
    reject("protected void regression", lambda c: (c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"].__setitem__("whitespace", c["cases"][0]["rows"]["PCB_NORMAL_PACKING_CASE"]["data"]["whitespace"] + 10), c["cases"][0]["rows"]["PCB_WHITESPACE_CASE"]["data"]["largestProtectedEmptyRectangle"].__setitem__("area", c["cases"][0]["rows"]["PCB_WHITESPACE_CASE"]["data"]["largestProtectedEmptyRectangle"]["area"] + 10)), "metric regression protectedVoid")
    reject("row count", lambda c: c["cases"].pop(), "case count mismatch")
    reject("observation row count", lambda c: c["cases"][0]["rows"].pop("PCB_NORMAL_SILK_CASE"), "observation row count/type")
    reject("case order", lambda c: c["cases"].__setitem__(slice(0, 2), reversed(c["cases"][:2])), "case order/identity")
    reject("new lead/silk overlap", lambda c: c["cases"][0]["rows"]["PCB_LEAD_SILK_CASE"]["data"]["overlaps"].append({"label": c["cases"][0]["rows"]["PCB_LEAD_SILK_CASE"]["data"]["labels"][0]["id"], "component": c["cases"][0]["rows"]["PCB_LEAD_SILK_CASE"]["data"]["components"][0]["id"], "terminal": 0}), "overlap")
    reject("lead shape", lambda c: c["cases"][0]["rows"]["PCB_LEAD_SILK_CASE"]["data"]["components"][0]["leads"][0].__setitem__(2, c["cases"][0]["rows"]["PCB_LEAD_SILK_CASE"]["data"]["components"][0]["leads"][0][2] + 1), "leads[0][2]")
    return {"status": "PASS", "positiveControls": passed, "negativeControls": negatives}


def main() -> int:
    if not __debug__:
        raise RuntimeError("run this reader with Python assertions enabled (do not use -O)")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=BASELINE)
    parser.add_argument("--current", type=Path, default=CURRENT)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--canaries", action="store_true", help="run self-contained reader controls only")
    args = parser.parse_args()
    try:
        pins = source_pins()
        base = parse_receipt(args.baseline, "baseline")
        controls = canaries(base)
        if args.canaries:
            print(f"PASS: paired-packing census reader canaries positives={len(controls['positiveControls'])} negatives={len(controls['negativeControls'])}")
            return 0
        current = parse_receipt(args.current, "current")
        changes = compare_corpora(base, current)
        report = {
            "status": "PASS", "cases": 44, "observationPairs": 220,
            "baseline": {"path": display_path(args.baseline), "sha256": base["sha256"]},
            "current": {"path": display_path(args.current), "sha256": current["sha256"]},
            "sourcePins": pins, "canaries": controls,
            "comparison": changes,
            "limits": [
                "This corpus compares the maintained 44 family/root cases only; it makes no all-seed or global-optimum claim.",
                "The independent grid sampler's cell counts, fraction, bounds, and largest-rectangle arithmetic are checked; free cells are not treated as packability proof.",
                "The live-graph digest and exposed component/label/lead identities are exact. Full geometry digests are retained and reported, while coordinate and routing changes are checked through exposed deltas and the maintained census's validateGeometry call.",
                "The receipt does not serialize all pad/probe rectangles or routed trace vertices. The maintained source-pinned census validates those in-memory before emitting each row; this reader cannot reconstruct those hidden coordinates from the receipt.",
            ],
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
        print(f"PASS: paired-packing census comparison cases=44 observations=220 changes={sum(len(c['changes']) for c in changes)} output={display_path(args.output)}")
        return 0
    except (OSError, UnicodeError, GateFailure, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
