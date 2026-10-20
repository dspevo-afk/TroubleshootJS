#!/usr/bin/env python3
"""Compare the frozen and final maintained 44-board packing census receipts."""
import argparse
import copy
import hashlib
import json
from pathlib import Path

if not __debug__:
    raise RuntimeError("run the census reader with Python assertions enabled (without -O)")

HERE = Path(__file__).resolve().parent
KINDS = ("PCB_NORMAL_PACKING_CASE", "PCB_WHITESPACE_CASE",
         "PCB_NORMAL_SILK_CASE", "PCB_NORMAL_REFERENCE_CASE", "PCB_LEAD_SILK_CASE")
BASELINE_SHA256 = "e7bf2da7239d5d534b273adc399d60b2ba99a306863c1b773264202d81576578"
SCOPE = "GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_ISOLATION;FREE_IS_NOT_PACKABLE_PROOF"


def require(ok, message):
    if not ok:
        raise ValueError(message)


def exact(left, right):
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        return left.keys() == right.keys() and all(exact(left[k], right[k]) for k in left)
    if isinstance(left, (list, tuple)):
        return len(left) == len(right) and all(exact(a, b) for a, b in zip(left, right))
    return left == right


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate JSON key: " + key)
        result[key] = value
    return result


def read_receipt(path):
    raw = Path(path).read_bytes()
    text = raw.decode("utf-8")
    rows = {kind: [] for kind in KINDS}
    for number, line in enumerate(text.splitlines(), 1):
        marker, sep, body = line.partition(" ")
        if marker.startswith("PCB_"):
            require(sep and marker in rows, "unknown or malformed PCB row at line %d" % number)
            data = json.loads(body, object_pairs_hook=unique_pairs)
            require(isinstance(data, dict), "PCB row is not a JSON object at line %d" % number)
            rows[marker].append({"data": data, "raw": line, "line": number})
    return {"name": Path(path).name, "sha256": hashlib.sha256(raw).hexdigest(), "rows": rows}


def indexed(rows, kind, expected):
    result = {}
    for entry in rows[kind]:
        d = entry["data"]
        key = (d.get("family"), d.get("root"))
        require(all(key) and key not in result, "%s has missing/duplicate family-root key %r" % (kind, key))
        result[key] = entry
    require(len(result) == expected, "%s must contain %d unique rows" % (kind, expected))
    return result


def metric(d, key):
    value = d.get(key)
    require(type(value) is int and value >= 0, "missing/nonnegative integer metric " + key)
    return value


def label_metadata(row):
    result = []
    for label in row["labels"]:
        bounds = label["bounds"]
        require(isinstance(bounds, list) and len(bounds) == 4, "silkscreen label bounds must have four coordinates")
        result.append((label["id"], label["text"], label["fontSize"], label["bold"],
                       label["targetPadId"], bounds[2], bounds[3]))
    return result


def component_identity(row):
    return [(c["id"], c["type"], c["name"], c["referenceLabelCharacters"])
            for c in row["components"]]


def lead_inventory(row):
    return [(c["id"], len(c["leads"])) for c in row["components"]]


def overlap_set(row):
    return {json.dumps(x, sort_keys=True, separators=(",", ":")) for x in row["overlaps"]}


def reason(kind, path):
    p = path[2:] if path.startswith("$.") else path
    if kind == "PCB_NORMAL_PACKING_CASE":
        if p in ("elapsedMillis", "maxAdvanceMillis"):
            return "runtime observation varies between census executions"
        if p == "layoutAlgorithmVersion":
            return "intended layout epoch change"
        if p in ("geometrySha256", "outline.x", "outline.y", "outline.width", "outline.height"):
            return "physical geometry may change; outline area remains gated"
        if p in ("outline.area", "copperManhattanLength", "viaCount", "whitespace"):
            return "measured production metric; nonincreasing predicate enforced"
        if p == "holeCount":
            return "via population may change; non-via hole count remains exact"
        if p == "traceCount" or p.startswith("generation") or p == "routingRecovery":
            return "routing structure or work may change under the packing candidate"
    elif kind == "PCB_WHITESPACE_CASE":
        if p in ("sampledCells", "packageAccessOccupiedCells", "protectedFreeCells", "protectedFreeFraction",
                 "largestProtectedEmptyRectangle.x", "largestProtectedEmptyRectangle.y",
                 "largestProtectedEmptyRectangle.width", "largestProtectedEmptyRectangle.height"):
            return "independent GRID10 sampler observation; regridding is reported, not gated"
        if p == "largestProtectedEmptyRectangle.area":
            return "independent protected-void cross-check of the gated production metric"
    elif kind == "PCB_NORMAL_SILK_CASE":
        if p == "geometryApartFromTitleSha256" or p in ("bounds[0]", "bounds[1]"):
            return "physical geometry/label position may change"
    elif kind == "PCB_NORMAL_REFERENCE_CASE":
        if p in ("physicalBoardSha256", "geometryApartFromReferenceTextSha256"):
            return "physical geometry digest may change; exposed identity and ink metadata remain exact"
        if p.startswith("labels[") and (p.endswith(".bounds[0]") or p.endswith(".bounds[1]")):
            return "physical label position may change; text/style/target/size remain exact"
    elif kind == "PCB_LEAD_SILK_CASE":
        if p == "geometryApartFromLegendPositionAndEpochSha256":
            return "physical geometry digest may change; identity and ink metadata remain exact"
        if ".leads[" in p or ".bounds[" in p:
            return "lead or label geometry may move with component placement"
        if p == "overlaps" or p.startswith("overlaps["):
            return "reported label-to-lead overlap relation; new overlaps are rejected"
    return None


def field_diffs(kind, before, after, path="$", out=None):
    if out is None:
        out = []
    if isinstance(before, dict) and isinstance(after, dict):
        require(before.keys() == after.keys(), "%s row schema changed at %s" % (kind, path))
        for key in sorted(before):
            field_diffs(kind, before[key], after[key], path + "." + key, out)
    elif isinstance(before, list) and isinstance(after, list):
        if kind == "PCB_LEAD_SILK_CASE" and path == "$.overlaps" and before != after:
            why = reason(kind, path)
            out.append({"field": path, "baseline": before, "current": after, "reason": why})
            return out
        require(len(before) == len(after), "%s row list length changed at %s" % (kind, path))
        for i, (left, right) in enumerate(zip(before, after)):
            field_diffs(kind, left, right, "%s[%d]" % (path, i), out)
    elif not exact(before, after):
        why = reason(kind, path)
        require(why is not None, "%s protected field changed at %s: %r -> %r" % (kind, path, before, after))
        out.append({"field": path, "baseline": before, "current": after, "reason": why})
    return out


def compare(baseline, current, expected=44):
    for receipt in (baseline, current):
        for kind in KINDS:
            require(len(receipt["rows"][kind]) == expected,
                    "%s has %d rows; expected %d" % (kind, len(receipt["rows"][kind]), expected))
    bpack = indexed(baseline["rows"], KINDS[0], expected)
    cpack = indexed(current["rows"], KINDS[0], expected)
    require(list(bpack) == list(cpack), "family/root corpus or canonical row order changed")
    breference = indexed(baseline["rows"], KINDS[3], expected)
    creference = indexed(current["rows"], KINDS[3], expected)
    bsilk = indexed(baseline["rows"], KINDS[2], expected)
    csilk = indexed(current["rows"], KINDS[2], expected)
    blead = indexed(baseline["rows"], KINDS[4], expected)
    clead = indexed(current["rows"], KINDS[4], expected)
    for name, index in (("reference", breference), ("reference", creference),
                        ("silk", bsilk), ("silk", csilk), ("lead", blead), ("lead", clead)):
        require(index.keys() == bpack.keys(), "%s observation corpus differs from packing admissions" % name)
    bwhite, cwhite = baseline["rows"][KINDS[1]], current["rows"][KINDS[1]]
    changes = []
    metric_rows = []
    strict = ("family", "profile", "root", "seed", "status", "phase", "executionPolicy",
              "physicalAdmission", "graphBefore", "graphAfter", "logicalParts", "logicalPads",
              "logicalNets", "layoutParts", "layoutPads")
    # Normal packing rows retain family/root emission order. The independent
    # sampler has no family field, so pair its rows by that source order.
    for ordinal, key in enumerate(bpack):
        bp, cp = bpack[key]["data"], cpack[key]["data"]
        require(all(exact(bp.get(f), cp.get(f)) for f in strict), "admission, graph, or logical identity changed for %r" % (key,))
        require(metric(bp, "layoutAlgorithmVersion") == 18 and metric(cp, "layoutAlgorithmVersion") == 19,
                "expected accepted layout epoch 18 -> 19 for %r" % (key,))
        require(exact(bp["status"], "ACCEPTED") and exact(cp["status"], "ACCEPTED") and
                exact(bp["phase"], "FINALIZED") and exact(cp["phase"], "FINALIZED"),
                "all census admissions must remain finalized for %r" % (key,))
        require(bp["graphBefore"] == bp["graphAfter"] and cp["graphBefore"] == cp["graphAfter"],
                "admission changed the live graph for %r" % (key,))
        bw, cw = bwhite[ordinal]["data"], cwhite[ordinal]["data"]
        require(bwhite[ordinal]["line"] < bpack[key]["line"] and cwhite[ordinal]["line"] < cpack[key]["line"],
                "independent whitespace row must precede its accepted packing row")
        require(exact((bw.get("seed"), bw.get("parts")), (bp["root"], bp["logicalParts"])) and
                exact((cw.get("seed"), cw.get("parts")), (cp["root"], cp["logicalParts"])),
                "whitespace row does not correspond to its admission %r" % (key,))
        for row in (bp, cp):
            outline = row["outline"]
            require(metric(outline, "area") == metric(outline, "width") * metric(outline, "height"),
                    "outline area does not match width*height for %r" % (key,))
        bmetrics = (metric(bp["outline"], "area"), metric(bp, "copperManhattanLength"),
                    metric(bp, "viaCount"), metric(bp, "whitespace"))
        cmetrics = (metric(cp["outline"], "area"), metric(cp, "copperManhattanLength"),
                    metric(cp, "viaCount"), metric(cp, "whitespace"))
        require(all(now <= old for old, now in zip(bmetrics, cmetrics)),
                "production metric regression for %r: %r -> %r" % (key, bmetrics, cmetrics))
        require(metric(bp, "holeCount") - metric(bp, "viaCount") ==
                metric(cp, "holeCount") - metric(cp, "viaCount"), "non-via hole population changed for %r" % (key,))
        for row, white in ((bp, bw), (cp, cw)):
            require(exact(white.get("scope"), SCOPE) and exact(white.get("version"), 1) and
                    exact(white.get("gridUnits"), 10),
                    "independent sampler scope/version changed")
            require(metric(white.get("largestProtectedEmptyRectangle", {}), "area") == metric(row, "whitespace"),
                    "production protected-void value disagrees with independent sampler for %r" % (key,))
        metric_rows.append({"family": key[0], "root": key[1],
                            "baseline": dict(zip(("area", "copper", "vias", "protectedVoid"), bmetrics)),
                            "current": dict(zip(("area", "copper", "vias", "protectedVoid"), cmetrics))})
        for kind, bi, ci in ((KINDS[0], bpack, cpack), (KINDS[1], None, None),
                             (KINDS[2], bsilk, csilk), (KINDS[3], breference, creference),
                             (KINDS[4], blead, clead)):
            if kind == KINDS[1]:
                left, right = bwhite[ordinal], cwhite[ordinal]
            else:
                left, right = bi[key], ci[key]
            delta = field_diffs(kind, left["data"], right["data"])
            changes.append({"row": kind, "key": {"family": key[0], "root": key[1]},
                            "baselineSha256": hashlib.sha256(left["raw"].encode()).hexdigest(),
                            "currentSha256": hashlib.sha256(right["raw"].encode()).hexdigest(),
                            "changedFields": delta})
    for key in sorted(bpack):
        br, cr = breference[key]["data"], creference[key]["data"]
        require(exact(component_identity(br), component_identity(cr)), "functional component identity changed for %r" % (key,))
        require(exact(label_metadata(br), label_metadata(cr)), "public reference text/style/target/size changed for %r" % (key,))
        require(exact(br["labelCount"], cr["labelCount"]), "public label count changed for %r" % (key,))
        for row in (br, cr):
            for label in row["labels"]:
                require(len(label["bounds"]) == 4, "reference label bounds must have four coordinates")
        a, b = bsilk[key]["data"], csilk[key]["data"]
        require(exact(a["title"], b["title"]), "public board title changed for %r" % (key,))
        require(len(a["bounds"]) == len(b["bounds"]) == 4 and exact(a["bounds"][2:], b["bounds"][2:]),
                "public title dimensions changed for %r" % (key,))
        a, b = blead[key]["data"], clead[key]["data"]
        require(lead_inventory(a) == lead_inventory(b), "component/terminal inventory changed for %r" % (key,))
        require([x["id"] for x in a["labels"]] == [x["id"] for x in b["labels"]],
                "public lead/silk label identity changed for %r" % (key,))
        require(overlap_set(b) <= overlap_set(a), "new label-to-lead overlap reported for %r" % (key,))
    return {"schema": "packing-via-cap-census-comparison@1", "status": "PASS", "cases": expected,
            "baseline": {"file": baseline["name"], "sha256": baseline["sha256"]},
            "current": {"file": current["name"], "sha256": current["sha256"]},
            "rowCounts": {kind: expected for kind in KINDS}, "metrics": metric_rows,
            "rowComparisons": changes,
            "limits": ["Finite paired 44-board census only; exact target seed is outside this corpus.",
                       "Graph preservation uses the maintained before/after graph SHA-256 oracle; receipt does not expose individual net/pad ID lists.",
                       "Independent GRID10 free-cell/fraction values are recorded but not gated because geometry changes alter the sampled domain.",
                       "No production build, player-flow, electrical repair, all-seed, or manufacturing claim."]}


def fixture():
    def row(kind, data, line):
        return {"data": data, "raw": kind + " " + json.dumps(data, separators=(",", ":")), "line": line}
    packing = {"family": "TEST", "profile": "EASY", "root": "1", "seed": "1", "status": "ACCEPTED",
               "phase": "FINALIZED", "executionPolicy": "P@1", "physicalAdmission": "A@1", "elapsedMillis": 1,
               "maxAdvanceMillis": None, "graphBefore": "graph", "graphAfter": "graph", "logicalParts": 1,
               "logicalPads": 2, "logicalNets": 1, "layoutParts": 1, "layoutPads": 2, "traceCount": 1,
               "geometrySha256": "geom", "layoutAlgorithmVersion": 18,
               "outline": {"x": 0, "y": 0, "width": 10, "height": 10, "area": 100},
               "copperManhattanLength": 20, "holeCount": 2, "viaCount": 0,
               "generationPlacementAttempts": 1, "generationRoutingAttempts": 1,
               "generationRoutingExpansions": 1, "generationRoutingMillis": 1, "routingRecovery": "work",
               "whitespace": 25}
    white = {"seed": "1", "version": 1, "parts": 1, "gridUnits": 10, "sampledCells": 100,
             "packageAccessOccupiedCells": 75, "protectedFreeCells": 25, "protectedFreeFraction": 0.25,
             "largestProtectedEmptyRectangle": {"x": 0, "y": 0, "width": 5, "height": 5, "area": 25},
             "scope": SCOPE}
    reference = {"family": "TEST", "root": "1", "components": [{"id": "R1", "type": "RESISTOR", "name": "R1",
                  "referenceLabelCharacters": 2}], "labels": [{"id": "component:R1", "text": "R1", "bounds": [1, 1, 10, 10],
                  "fontSize": 14, "bold": True, "targetPadId": None}], "labelCount": 1,
                  "physicalBoardSha256": "physical", "geometryApartFromReferenceTextSha256": "refgeom"}
    silk = {"family": "TEST", "root": "1", "title": "TEST BOARD", "bounds": [1, 1, 10, 10],
            "geometryApartFromTitleSha256": "silkgeom"}
    lead = {"family": "TEST", "root": "1", "geometryApartFromLegendPositionAndEpochSha256": "leadgeom",
            "components": [{"id": "R1", "leads": [[1, 2, 3, 4], [5, 6, 3, 4]]}],
            "labels": [{"id": "component:R1", "bounds": [1, 1, 10, 10]}], "overlaps": []}
    data = {KINDS[0]: packing, KINDS[1]: white, KINDS[2]: silk, KINDS[3]: reference, KINDS[4]: lead}
    positions = {KINDS[1]: 1, KINDS[0]: 2, KINDS[2]: 3, KINDS[3]: 4, KINDS[4]: 5}
    receipt = {"name": "fixture", "sha256": "fixture",
               "rows": {k: [row(k, v, positions[k])] for k, v in data.items()}}
    return receipt


def refresh_fixture(receipt):
    for kind, entries in receipt["rows"].items():
        for entry in entries:
            entry["raw"] = kind + " " + json.dumps(entry["data"], separators=(",", ":"))


def canaries():
    base = fixture()
    valid = copy.deepcopy(base)
    valid["rows"][KINDS[0]][0]["data"]["layoutAlgorithmVersion"] = 19
    refresh_fixture(valid)
    compare(base, valid, 1)  # Prove the exact frame that every negative starts from.
    def rejected(name, mutate):
        bad = copy.deepcopy(valid)
        mutate(bad["rows"])
        refresh_fixture(bad)
        try:
            compare(base, bad, 1)
        except (ValueError, KeyError, TypeError):
            return {"name": name, "result": "REJECTED_AS_EXPECTED"}
        raise ValueError("canary unexpectedly passed: " + name)
    checks = [{"name": "valid-epoch-frame", "result": "ACCEPTED_AS_EXPECTED"},
              rejected("admission", lambda r: r[KINDS[0]][0]["data"].update(status="REJECTED")),
              rejected("graph-id", lambda r: r[KINDS[0]][0]["data"].update(graphAfter="changed")),
              rejected("component-id", lambda r: r[KINDS[3]][0]["data"]["components"][0].update(id="R2")),
              rejected("public-metadata", lambda r: r[KINDS[3]][0]["data"]["labels"][0].update(targetPadId="R1.1"))]
    for name, mutate in (("area", lambda d: d["outline"].update(width=11, area=110)),
                         ("copper", lambda d: d.update(copperManhattanLength=21)),
                         ("vias", lambda d: d.update(viaCount=1, holeCount=3)),
                         ("protected-void", lambda d: d.update(whitespace=26))):
        def change_metric(rows, m=mutate, n=name):
            m(rows[KINDS[0]][0]["data"])
            if n == "protected-void":
                rows[KINDS[1]][0]["data"]["largestProtectedEmptyRectangle"].update(width=13, area=26)
        checks.append(rejected("metric-" + name, change_metric))
    improved = copy.deepcopy(base)
    p = improved["rows"][KINDS[0]][0]["data"]
    p["outline"].update(x=1, width=8, area=80); p.update(geometrySha256="geom2", layoutAlgorithmVersion=19,
        copperManhattanLength=18, whitespace=20, generationRoutingExpansions=3, routingRecovery="new-work")
    w = improved["rows"][KINDS[1]][0]["data"]
    w.update(sampledCells=80, protectedFreeCells=20, protectedFreeFraction=0.25,
             largestProtectedEmptyRectangle={"x": 1, "y": 0, "width": 4, "height": 5, "area": 20})
    ref = improved["rows"][KINDS[3]][0]["data"]
    ref["physicalBoardSha256"] = "physical2"; ref["labels"][0]["bounds"][0] = 2
    improved["rows"][KINDS[2]][0]["data"].update(bounds=[2, 1, 10, 10], geometryApartFromTitleSha256="silk2")
    ld = improved["rows"][KINDS[4]][0]["data"]
    ld["geometryApartFromLegendPositionAndEpochSha256"] = "lead2"; ld["components"][0]["leads"][0][0] = 2
    ld["labels"][0]["bounds"][0] = 2
    refresh_fixture(improved)
    compare(base, improved, 1)
    checks.append({"name": "allowed-geometry-improvement", "result": "ACCEPTED_AS_EXPECTED"})
    return {"status": "PASS", "canaries": checks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=HERE.parent.parent / "packing-metric-evidence-r1" / "final-r2" / "census-receipt.txt")
    parser.add_argument("--current", type=Path, default=HERE / "census-receipt.txt")
    parser.add_argument("--output", type=Path, default=HERE / "census-comparison.json")
    parser.add_argument("--canaries", action="store_true", help="check isolated pass/fail fixtures without reading receipts")
    args = parser.parse_args()
    if args.canaries:
        print(json.dumps(canaries(), indent=2))
        return 0
    report = None
    try:
        baseline = read_receipt(args.baseline)
        current = read_receipt(args.current)
        if args.baseline == parser.get_default("baseline"):
            require(baseline["sha256"] == BASELINE_SHA256, "frozen baseline receipt SHA-256 changed")
        report = compare(baseline, current)
    except Exception as exc:
        report = {"schema": "packing-via-cap-census-comparison@1", "status": "FAIL", "error": str(exc)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("%s: %s" % (report["status"], args.output.name))
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
