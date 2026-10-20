#!/usr/bin/env python3
"""Independent reader for the accepted-layout-17 reference-label oracle."""
import argparse
import copy
import hashlib
import json
import math
import re
import subprocess
import sys
from pathlib import Path

SCHEMA = "REFERENCE_LEAD_CLEARANCE_R1_DRAFT"
NORMALIZATION = ("only leading layout=17| becomes layout=<LAYOUT_EPOCH>|; only x/y in exact "
                 "L:component:*@text:x,y,width,height; records become <REF_X>,<REF_Y>; "
                 "all other bytes retained")
GRAPH = "UNAVAILABLE_LAYOUT_ONLY_HELPER"
PREFIX = "PCB_REFERENCE_ORACLE "
FOOTER = "PASS: focused current contracts; 12 Java suites. Full matrix and independent oracles NOT RUN."
TOP_KEYS = ("schema", "layoutVersion", "layoutUnchanged", "graphSnapshot", "layoutFingerprint",
            "layoutSha256", "legacyLayout16Sha256", "normalizedLayoutFingerprint",
            "normalizedLayoutSha256", "normalization", "referenceLabelCount",
            "referenceLabelRecords", "labelCount", "labels")
REFERENCE_KEYS = ("id", "record", "normalizedRecord")
LABEL_KEYS = ("id", "text", "bounds", "fontSize", "bold", "targetPadId")
SOURCE_FILES = ("tests/contracts/PcbCompactnessContractTest.java",
                "tests/contracts/PcbFunctionalGroupingContractTest.java",
                "tests/contracts/PcbPocketPackingContractTest.java")
HASH_RE = re.compile(r'"([0-9a-fA-F]{64})"')
LABEL_MARKER_RE = re.compile(r"(?:^|[;|])L:")
LABEL_RECORD_RE = re.compile(r"(?=(?:^|[;|])(L:[^;|]*;))")
COMPONENT_ID_RE = re.compile(r"(?:^|[;|])C:component=([^@;|]+)@")
COMPONENT_MARKER_RE = re.compile(r"(?:^|[;|])C:")


def need(condition, message):
    if not condition:
        raise ValueError(message)


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, "duplicate JSON key: " + key)
        result[key] = value
    return result


def _bad_constant(value):
    raise ValueError("non-finite JSON constant: " + value)


def _sha(value):
    try:
        data = value.encode("utf-8", errors="strict")
    except UnicodeEncodeError as exc:
        raise ValueError("fingerprint is not valid UTF-8 text") from exc
    return hashlib.sha256(data).hexdigest()


def _hex_digest(value, name):
    need(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None,
         name + " must be 64 lowercase hex characters")


def _record_for(label):
    ident, text, bounds = label["id"], label["text"], label["bounds"]
    need("@" not in ident and ";" not in ident and "|" not in ident,
         "label ID contains a canonical record delimiter")
    need(";" not in text and "|" not in text, "label text contains a canonical record delimiter")
    return "L:" + ident + "@" + text + ":" + ",".join(str(v) for v in bounds) + ";"


def validate_event(event):
    need(type(event) is dict and tuple(event) == TOP_KEYS, "unexpected oracle event schema/order")
    need(event["schema"] == SCHEMA, "unexpected oracle schema")
    need(type(event["layoutVersion"]) is int and event["layoutVersion"] == 17,
         "oracle must be from layout version 17")
    need(type(event["layoutUnchanged"]) is bool and event["layoutUnchanged"],
         "observer did not prove layout unchanged")
    need(event["graphSnapshot"] == GRAPH, "unexpected graph scope sentinel")
    raw = event["layoutFingerprint"]
    need(type(raw) is str and raw.startswith("layout=17|"), "expected canonical layout=17 fingerprint")
    need(event["normalization"] == NORMALIZATION, "unexpected normalization declaration")
    _hex_digest(event["layoutSha256"], "layoutSha256")
    _hex_digest(event["legacyLayout16Sha256"], "legacyLayout16Sha256")
    _hex_digest(event["normalizedLayoutSha256"], "normalizedLayoutSha256")
    need(_sha(raw) == event["layoutSha256"], "raw layout SHA-256 mismatch")
    legacy = "layout=16|" + raw[len("layout=17|"):]
    need(_sha(legacy) == event["legacyLayout16Sha256"], "legacy layout-16 SHA-256 mismatch")

    labels = event["labels"]
    need(type(labels) is list, "labels must be an array")
    need(type(event["labelCount"]) is int and event["labelCount"] == len(labels),
         "label count mismatch")
    label_ids = []
    records = []
    refs = []
    normalized = "layout=<LAYOUT_EPOCH>|" + raw[len("layout=17|"):]
    for label in labels:
        need(type(label) is dict and tuple(label) == LABEL_KEYS, "unexpected label schema/order")
        ident, text, bounds = label["id"], label["text"], label["bounds"]
        need(type(ident) is str and ident != "", "label ID must be nonempty text")
        need(type(text) is str and text != "", "label text must be nonempty text")
        need(type(bounds) is list and len(bounds) == 4 and all(type(v) is int for v in bounds),
             "label bounds must contain four integers")
        need(bounds[2] > 0 and bounds[3] > 0, "label bounds must have positive dimensions")
        need(type(label["fontSize"]) is int and label["fontSize"] > 0,
             "label font size must be a positive integer")
        need(type(label["bold"]) is bool, "label bold must be boolean")
        target = label["targetPadId"]
        need(target is None or (type(target) is str and target != ""),
             "targetPadId must be null or nonempty text")
        label_ids.append(ident)
        record = _record_for(label)
        records.append(record)
        if ident.startswith("component:"):
            normalized_record = ("L:" + ident + "@" + text + ":<REF_X>,<REF_Y>," +
                                 str(bounds[2]) + "," + str(bounds[3]) + ";")
            need(raw.count(record) == 1, "component reference record must occur exactly once")
            normalized = normalized.replace(record, normalized_record, 1)
            refs.append({"id": ident, "record": record, "normalizedRecord": normalized_record})
    need(label_ids == sorted(label_ids) and len(set(label_ids)) == len(label_ids),
         "label IDs must be sorted and unique")
    need(len(LABEL_MARKER_RE.findall(raw)) == len(labels), "canonical label marker count mismatch")
    need([match.group(1) for match in LABEL_RECORD_RE.finditer(raw)] == records,
         "canonical label records differ from the complete label inventory")

    component_ids = [m.group(1) for m in COMPONENT_ID_RE.finditer(raw)]
    need(len(COMPONENT_MARKER_RE.findall(raw)) == len(component_ids),
         "canonical component marker framing mismatch")
    need(component_ids == sorted(component_ids) and len(set(component_ids)) == len(component_ids),
         "canonical component inventory must be sorted and unique")
    reference_component_ids = [ident[len("component:"):] for ident in label_ids
                               if ident.startswith("component:")]
    need(reference_component_ids == component_ids,
         "component-reference labels do not exactly match C:component inventory")
    need(type(event["referenceLabelCount"]) is int and event["referenceLabelCount"] == len(refs),
         "reference label count mismatch")
    reference_records = event["referenceLabelRecords"]
    need(type(reference_records) is list and len(reference_records) == len(refs),
         "reference label record inventory mismatch")
    for actual, expected in zip(reference_records, refs):
        need(type(actual) is dict and tuple(actual) == REFERENCE_KEYS and actual == expected,
             "reference label record mismatch")
    need(event["normalizedLayoutFingerprint"] == normalized,
         "normalized fingerprint changed bytes beyond the declared reference x/y fields")
    need(_sha(normalized) == event["normalizedLayoutSha256"],
         "normalized layout SHA-256 mismatch")
    return event["legacyLayout16Sha256"], event["normalizedLayoutSha256"]


def expected_hashes(source_root):
    occurrences = []
    for relative in SOURCE_FILES:
        path = source_root / relative
        need(path.is_file(), "missing archived source: " + relative)
        text = path.read_text(encoding="utf-8-sig")
        occurrences.extend(value.lower() for value in HASH_RE.findall(text))
    unique = set(occurrences)
    need(len(occurrences) == 69 and len(unique) == 44,
         "archived oracle literals must retain 69 occurrences and 44 distinct keys")
    return unique, len(occurrences)


def read_events(receipt):
    events = []
    for line_number, line in enumerate(receipt.read_text(encoding="utf-8-sig").splitlines(), 1):
        if not line.startswith(PREFIX):
            continue
        try:
            event = json.loads(line[len(PREFIX):], object_pairs_hook=_pairs_no_duplicates,
                               parse_constant=_bad_constant)
        except (json.JSONDecodeError, ValueError) as exc:
            raise ValueError("invalid oracle JSON at receipt line " + str(line_number) + ": " + str(exc)) from exc
        events.append(event)
    need(events, "receipt has no PCB_REFERENCE_ORACLE events")
    return events


def validate_timing(path):
    data = json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=_pairs_no_duplicates,
                      parse_constant=_bad_constant)
    validate_timing_object(data)


def validate_footer(path):
    validate_footer_text(path.read_text(encoding="utf-8-sig"))


def build_map(receipt, source_root, timing, driver_log):
    validate_timing(timing)
    validate_footer(driver_log)
    expected, literal_count = expected_hashes(source_root)
    mapping = {}
    events = read_events(receipt)
    for event in events:
        old_hash, normalized_hash = validate_event(event)
        if old_hash in expected:
            prior = mapping.setdefault(old_hash, normalized_hash)
            need(prior == normalized_hash, "one frozen legacy key maps to conflicting normalized layouts")
    need(set(mapping) == expected, "baseline oracle receipt does not cover every archived literal key")
    return {"schema": "REFERENCE_LEAD_CLEARANCE_R1_ORACLE_MAP",
            "receiptRows": len(events), "expectedLiteralOccurrences": literal_count,
            "expectedLegacyHashCount": len(expected),
            "legacyLayout16Sha256ToNormalizedLayoutSha256": dict(sorted(mapping.items()))}


def _fixture(component_x=10, component_y=20, component_width=4, component_height=2,
             component_text="R1", body="chip",
             pad="land", trace="net-a", title_x=1, title_text="PCB"):
    labels = [
        {"id": "board-title", "text": title_text, "bounds": [title_x, 1, 9, 2],
         "fontSize": 2, "bold": False, "targetPadId": None},
        {"id": "component:R1", "text": component_text,
         "bounds": [component_x, component_y, component_width, component_height],
         "fontSize": 2, "bold": True, "targetPadId": "R1.1"},
    ]
    raw = ("layout=17|score=3|region:one|100x80|outline=0,0,100,80|" +
           "C:component=R1@body=" + body + "|pose=0;P:R1.1@" + pad + "|" +
           _record_for(labels[0]) + _record_for(labels[1]) + "T:trace=" + trace + ";H:via=v1;")
    normalized = raw.replace("layout=17|", "layout=<LAYOUT_EPOCH>|", 1)
    ref_record = _record_for(labels[1])
    ref_normal = ("L:component:R1@" + component_text + ":<REF_X>,<REF_Y>," +
                  str(component_width) + "," + str(component_height) + ";")
    normalized = normalized.replace(ref_record, ref_normal, 1)
    legacy = "layout=16|" + raw[len("layout=17|"):]
    return {
        "schema": SCHEMA, "layoutVersion": 17, "layoutUnchanged": True, "graphSnapshot": GRAPH,
        "layoutFingerprint": raw, "layoutSha256": _sha(raw), "legacyLayout16Sha256": _sha(legacy),
        "normalizedLayoutFingerprint": normalized, "normalizedLayoutSha256": _sha(normalized),
        "normalization": NORMALIZATION, "referenceLabelCount": 1,
        "referenceLabelRecords": [{"id": "component:R1", "record": ref_record,
                                   "normalizedRecord": ref_normal}],
        "labelCount": len(labels), "labels": labels,
    }


def _must_fail(call, message):
    try:
        call()
    except (ValueError, OSError, json.JSONDecodeError):
        return
    raise ValueError("self-test expected rejection: " + message)


def self_test():
    base = _fixture()
    old, normalized = validate_event(base)
    for moved in (_fixture(component_x=42), _fixture(component_y=42)):
        moved_old, moved_normal = validate_event(moved)
        need(old != moved_old and normalized == moved_normal, "component x/y-only normalization failed")
    for changed in (_fixture(component_width=6), _fixture(component_height=3), _fixture(component_text="R1A"),
                    _fixture(body="changed"), _fixture(pad="changed"),
                    _fixture(trace="changed"), _fixture(title_x=8), _fixture(title_text="BOARD")):
        _, changed_normal = validate_event(changed)
        need(changed_normal != normalized, "a retained geometry/label field was normalized away")
    duplicate = copy.deepcopy(base)
    duplicate["labels"].append(copy.deepcopy(duplicate["labels"][-1]))
    duplicate["labelCount"] += 1
    _must_fail(lambda: validate_event(duplicate), "duplicate label")
    missing = copy.deepcopy(base)
    missing["labels"].pop()
    missing["labelCount"] -= 1
    missing["referenceLabelCount"] = 0
    missing["referenceLabelRecords"] = []
    _must_fail(lambda: validate_event(missing), "missing component label")
    wrong_graph = copy.deepcopy(base)
    wrong_graph["graphSnapshot"] = "AVAILABLE"
    _must_fail(lambda: validate_event(wrong_graph), "graph sentinel")
    malformed = copy.deepcopy(base)
    malformed["layoutFingerprint"] += "|L:unlisted@X:1,1,1,1;"
    malformed["layoutSha256"] = _sha(malformed["layoutFingerprint"])
    suffix = malformed["layoutFingerprint"][len("layout=17|"):]
    malformed["legacyLayout16Sha256"] = _sha("layout=16|" + suffix)
    malformed["normalizedLayoutFingerprint"] = malformed["layoutFingerprint"].replace(
        "layout=17|", "layout=<LAYOUT_EPOCH>|", 1).replace(
            malformed["referenceLabelRecords"][0]["record"],
            malformed["referenceLabelRecords"][0]["normalizedRecord"], 1)
    malformed["normalizedLayoutSha256"] = _sha(malformed["normalizedLayoutFingerprint"])
    _must_fail(lambda: validate_event(malformed), "unlisted canonical label framing")
    duplicate_json = '{"x":1,"x":2}'
    _must_fail(lambda: json.loads(duplicate_json, object_pairs_hook=_pairs_no_duplicates), "duplicate JSON key")
    _must_fail(lambda: validate_footer_text(FOOTER + FOOTER), "duplicate focused footer")
    timing = {"phase": "baseline", "status": "PASS", "exitCode": 0, "elapsedSeconds": 1.25,
              "terminalCleanupIncluded": True, "cleanupDuration": "NOT_SEPARATELY_MEASURED", "failure": None}
    validate_timing_object(timing)
    _must_fail(lambda: validate_timing_object(dict(timing, status="FAIL")), "failed timing status")
    for exit_code in (False, 0.0, 2):
        bad = dict(timing, exitCode=exit_code)
        _must_fail(lambda bad=bad: validate_timing_object(bad), "non-exact integer-zero exit code")
    child = subprocess.run([sys.executable, "-O", str(Path(__file__).resolve()), "--self-test"],
                           text=True, capture_output=True, check=False)
    need(child.returncode == 2 and "assertion-enabled Python" in child.stderr,
         "optimized Python invocation was not refused")
    print("PASS reference oracle reader self-test normalization/framing/inventory/timing/-O")


def validate_footer_text(text):
    need(text.count(FOOTER) == 1, "focused12 driver footer must occur exactly once")


def validate_timing_object(data):
    keys = {"phase", "status", "exitCode", "elapsedSeconds", "terminalCleanupIncluded",
            "cleanupDuration", "failure"}
    need(type(data) is dict and set(data) == keys, "unexpected timing record schema")
    need(data["phase"] == "baseline" and data["status"] == "PASS", "baseline timing is not PASS")
    need(type(data["exitCode"]) is int and data["exitCode"] == 0, "timing exitCode must be exact integer zero")
    elapsed = data["elapsedSeconds"]
    need(type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed >= 0,
         "elapsedSeconds must be a finite nonnegative number")
    need(type(data["terminalCleanupIncluded"]) is bool and data["terminalCleanupIncluded"],
         "terminal cleanup must be included in the measured phase")
    need(data["cleanupDuration"] == "NOT_SEPARATELY_MEASURED" and data["failure"] is None,
         "unexpected cleanup or failure timing fields")


def main(argv=None):
    if not __debug__:
        print("REFUSED: reader requires assertion-enabled Python", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--timing", type=Path)
    parser.add_argument("--driver-log", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        need(not any((args.receipt, args.source_root, args.timing, args.driver_log, args.output)),
             "--self-test cannot be combined with input arguments")
        self_test()
        return 0
    need(all((args.receipt, args.source_root, args.timing, args.driver_log)),
         "--receipt, --source-root, --timing and --driver-log are required")
    result = build_map(args.receipt, args.source_root, args.timing, args.driver_log)
    payload = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8", newline="\n")
    print("PASS reference oracle reader rows=" + str(result["receiptRows"]) +
          " mapped=" + str(result["expectedLegacyHashCount"]) + " timing=int0 focused12-footer=1")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print("FAIL reference oracle reader: " + str(exc), file=sys.stderr)
        raise SystemExit(2)
