"""Actual-data semantic rejection controls for the initial-outlet reader."""
import copy
import hashlib
import json
import os
from pathlib import Path
import sys
import types


SCHEMA = "tsj-initial-outlet-semantic-controls-v1"
READER_SHA256 = "d4a1a51aaef19c6cac9d7785ac8c1f9f075b40c2bbbc9289b00758eafeca4074"
BUILD_INPUTS_SHA256 = "87951c6c464fe4a39ae0b908017a7e857036e543c26b5332ee683839440c4d76"
MANIFEST_SHA256 = "36e80ef3be066a4bc146adb193b90fd7da9bc33b9f5288b154d06f0af13593d9"
OP1_SHA256 = "61455b892d74b5dc779c9eb385dd9896de0e5525bc4bcd4c370474835dd9b1fa"
RESULT_SHA256 = "e5921bb2ec2f4fcaa0226c50f4dec1da864bf5526ac72242ba60f5f0c0bb4b1f"
MAX_OP1 = 8 * 1024 * 1024
MAX_RESULT = 8 * 1024 * 1024
MAX_PARENT = 256 * 1024


class DriverError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def fail(code):
    raise DriverError(code)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read_authenticated(path, expected_sha, label):
    try:
        st = path.lstat()
        if (not path.is_file() or path.is_symlink()
                or (getattr(st, "st_file_attributes", 0) & 0x400)):
            fail(label + "_FILE")
        raw = path.read_bytes()
    except DriverError:
        raise
    except OSError:
        fail(label + "_READ")
    if sha(raw) != expected_sha:
        fail(label + "_HASH")
    return raw


def load_module(name, path, raw):
    module = types.ModuleType(name)
    module.__file__ = str(path)
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
    except Exception:
        fail(name.upper() + "_LOAD")
    return module


def changed_bounded(value, low, high):
    if value < high:
        return value + 1
    if value > low:
        return value - 1
    fail("CONTROL_NO_ALTERNATE_VALUE")


def changed_delta(left, right, left_low, left_high, right_low, right_high):
    for delta in (1, -1):
        if (left_low <= left + delta <= left_high
                and right_low <= right + delta <= right_high):
            return delta
    fail("CONTROL_NO_SHARED_DELTA")


def validate_semantics(reader, doc, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id):
    return reader.validate_semantics(
        doc, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id)


def baseline(reader, doc, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id,
             marker, result_raw, parent_elapsed_ms):
    validated = validate_semantics(
        reader, doc, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id)
    reader._validate_marker(
        marker, result_raw, validated["work"], parent_elapsed_ms,
        validated["cleanup"]["combinedCleanupNanos"])
    return validated


def control_cases(doc, reader, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id,
                  actual_marker, result_raw, parent_elapsed_ms, validated):
    measurement = doc["measurement"]
    rows = measurement["rows"]
    pair_index = next(i for i, row in enumerate(rows) if row.get("paired") is True)
    singleton_index = next(i for i, row in enumerate(rows) if row.get("paired") is False)

    def mutated(name, expected, edit):
        target = copy.deepcopy(doc)
        edit(target)
        def invoke():
            reader.validate_semantics(
                target, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id)
        return (name, expected, invoke)

    def set_pair(field, value_fn):
        def edit(target):
            row = target["measurement"]["rows"][pair_index]
            row[field] = value_fn(row[field])
        return edit

    cases = [
        mutated("missing-net-row", "IO_MEASUREMENT_ROW_COUNT",
                lambda target: target["measurement"]["rows"].pop()),
        mutated("duplicate-net-row", "IO_MEASUREMENT_ROW_ORDER",
                lambda target: target["measurement"]["rows"].__setitem__(
                    1, copy.deepcopy(target["measurement"]["rows"][0]))),
        mutated("reordered-net-rows", "IO_MEASUREMENT_ROW_ORDER",
                lambda target: target["measurement"]["rows"].reverse()),
        mutated("pair-root-binding", "IO_MEASUREMENT_PAIR_IDENTITY",
                set_pair("rootPadId", lambda _: "MUTATED_ROOT")),
        mutated("nearest-start-binding", "IO_MEASUREMENT_PAIR_IDENTITY",
                set_pair("startPadId", lambda _: "MUTATED_START")),
        mutated("pair-role-binding", "IO_MEASUREMENT_PAIR_IDENTITY",
                set_pair("role", lambda value: "SIGNAL" if value != "SIGNAL" else "CONTROL")),
        mutated("pair-width-binding", "IO_MEASUREMENT_PAIR_IDENTITY",
                set_pair("traceWidth", lambda value: value + 1)),
        mutated("mask-range", "IO_MEASUREMENT_FORWARD",
                lambda target: target["measurement"]["rows"][pair_index]
                    ["forward"].__setitem__("topMask", 16)),
        mutated("mask-popcount", "IO_MEASUREMENT_FORWARD",
                lambda target: target["measurement"]["rows"][pair_index]
                    ["forward"].__setitem__(
                        "topCount",
                        (target["measurement"]["rows"][pair_index]["forward"]
                         ["topCount"] + 1) % 5)),
        mutated("orientation-total", "IO_MEASUREMENT_FORWARD",
                lambda target: target["measurement"]["rows"][pair_index]
                    ["forward"].__setitem__(
                        "totalCount",
                        (target["measurement"]["rows"][pair_index]["forward"]
                         ["totalCount"] + 1) % 9)),
        mutated("pair-value", "IO_MEASUREMENT_VALUE",
                lambda target: target["measurement"]["rows"][pair_index]
                    .__setitem__(
                        "value",
                        (target["measurement"]["rows"][pair_index]["value"] + 1) % 9)),
        mutated("singleton-shape", "IO_MEASUREMENT_SINGLETON_FIELDS",
                lambda target: target["measurement"]["rows"][singleton_index]
                    .__setitem__("startPadId", "MUTATED_SINGLETON")),
        mutated("query-count", "IO_MEASUREMENT_COUNTS",
                lambda target: target["measurement"].__setitem__(
                    "queryCount", target["measurement"]["queryCount"] + 1)),
    ]

    hist = measurement["distribution"]["pairedValueHistogram"]
    hist_key = min(hist, key=lambda key: hist[key])
    cases.append(mutated(
        "histogram-count", "IO_DISTRIBUTION_HISTOGRAM",
        lambda target: target["measurement"]["distribution"]["pairedValueHistogram"]
            .__setitem__(
                hist_key,
                target["measurement"]["distribution"]["pairedValueHistogram"][hist_key] + 1)))
    cases.append(mutated(
        "distinct-value-count", "IO_DISTRIBUTION_SUMMARY",
        lambda target: target["measurement"]["distribution"].__setitem__(
            "distinctValueCount",
            changed_bounded(
                target["measurement"]["distribution"]["distinctValueCount"], 0, 9))))
    cases.append(mutated(
        "varies-summary", "IO_DISTRIBUTION_SUMMARY",
        lambda target: target["measurement"]["distribution"].__setitem__(
            "varies", not target["measurement"]["distribution"]["varies"])))
    cases.append(mutated(
        "cleanup-flag", "IO_CLEANUP_PROOF",
        lambda target: target["cleanup"].__setitem__("querySessionReleased", False)))
    cases.append(mutated(
        "cleanup-sum", "IO_CLEANUP_PROOF",
        lambda target: target["cleanup"].__setitem__(
            "combinedCleanupNanos",
            changed_bounded(target["cleanup"]["combinedCleanupNanos"], 0, 120_000_000_000))))
    cases.append(mutated(
        "operation-group-step-sum", "IO_ORIGINAL_GROUP_ACCOUNTING",
        lambda target: target["operation1"].__setitem__(
            "groupP07StepsSum",
            changed_bounded(target["operation1"]["groupP07StepsSum"], 0, 32768 * 640))))
    cases.append(mutated(
        "operation-group-count", "IO_ORIGINAL_GROUP_ACCOUNTING",
        lambda target: target["operation1"].__setitem__(
            "groupCount", changed_bounded(target["operation1"]["groupCount"], 1, 640))))
    cases.append(mutated(
        "operation-owner-step-count", "IO_ORIGINAL_GROUP_ACCOUNTING",
        lambda target: target["operation1"].__setitem__(
            "p07OwnerSteps",
            changed_bounded(target["operation1"]["p07OwnerSteps"], 0, 32768 * 640))))

    def shift_preexport_and_work(target):
        work = target["work"]
        delta = changed_delta(
            work["preExportUnits"], work["units"],
            0, 32768, 0, 32768 * work["groups"])
        work["preExportUnits"] += delta
        work["units"] += delta

    cases.append(mutated(
        "preexport-tree-count", "IO_PREEXPORT_TREE_UNITS",
        shift_preexport_and_work))
    cases.append(mutated(
        "metric-group-count", "IO_WORK_ACCOUNTING",
        lambda target: target["work"].__setitem__(
            "metricGroups", target["work"]["metricGroups"] + 1)))

    def shifted_marker_case():
        target = copy.deepcopy(actual_marker)
        delta = changed_delta(
            target["exportUnits"], target["exportMaximumUnitsPerGroup"],
            1, 32768, 1, 32768)
        target["exportUnits"] += delta
        target["exportMaximumUnitsPerGroup"] += delta
        def invoke():
            reader._validate_marker(
                target, result_raw, validated["work"], parent_elapsed_ms,
                validated["cleanup"]["combinedCleanupNanos"])
        return invoke

    cases.append(("marker-units-formula", "IO_MARKER_BINDING", shifted_marker_case()))
    return cases


def run():
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        fail("ISOLATION_REQUIRED")
    if len(sys.argv) != 1:
        fail("NO_ARGUMENTS")

    root = Path(__file__).resolve().parent
    reader_path = root / "read_initial_outlets.py"
    builder_path = root / "build_inputs.py"
    reader_raw = read_authenticated(reader_path, READER_SHA256, "READER")
    builder_raw = read_authenticated(builder_path, BUILD_INPUTS_SHA256, "BUILD_INPUTS")

    reader = load_module("_semantic_controls_authenticated_reader", reader_path, reader_raw)
    builder = load_module("_semantic_controls_authenticated_build_inputs",
                          builder_path, builder_raw)
    if not callable(getattr(builder, "validate_run_manifest", None)):
        fail("BUILD_INPUTS_API")
    if not callable(getattr(builder, "recheck_inputs", None)):
        fail("FINAL_RECHECK_API")

    manifest_path = root / "j8-initial-r2" / "run-manifest.json"
    manifest_raw = read_authenticated(
        manifest_path, MANIFEST_SHA256, "RUN_MANIFEST")
    context = builder.validate_run_manifest(str(manifest_path), MANIFEST_SHA256)
    if (type(context) is not dict
            or context.get("sourceSha") != MANIFEST_SHA256
            or context.get("manifest", {}).get("wire", {}).get("sha256")
                != reader.WIRE_SHA256):
        fail("RUN_MANIFEST_CONTEXT")
    base = context.get("baseReader")
    if type(base) is not types.ModuleType:
        fail("BASE_READER_CONTEXT")
    p07 = context.get("p07Reader")
    if type(p07) is not types.ModuleType:
        fail("P07_READER_CONTEXT")

    parent_path = root / "native-observe-r2" / "parent.json"
    data_dir = root / "native-data-r2"
    parent_file = base._canonical(parent_path, "PARENT_PATH")
    data_dir = base._canonical(data_dir, "DATA_PATH")
    base._inside(parent_file, root, "PARENT_ROOT")
    base._inside(data_dir, root, "DATA_ROOT")
    if parent_file.name != "parent.json" or data_dir.name != "native-data-r2":
        fail("ACTUAL_DATA_LOCATION")
    call_dir = parent_file.parent
    base._lstat_directory(call_dir, "CALL_DIRECTORY")
    base._lstat_directory(data_dir, "DATA_DIRECTORY")
    if sorted(item.name for item in call_dir.iterdir()) != [
            "owner.json", "parent.json", "process.json", "request.json",
            "result.json", "stderr.txt", "stdout.txt"]:
        fail("NATIVE_CALL_TREE")
    if sorted(item.name for item in data_dir.iterdir()) != ["op-1.jsonl", "result.json"]:
        fail("NATIVE_DATA_TREE")

    parent_raw = base._read(parent_file, MAX_PARENT, "PARENT_READ")
    parent = base.host_document(parent_raw, MAX_PARENT)
    reader.exact_keys(parent, ("schema", "elapsedMs", "process"), "PARENT_SCHEMA")
    process = parent["process"]
    args = process.get("Arguments") if type(process) is dict else None
    if (type(args) is not list or len(args) != 9
            or args[0] != "-ea" or args[1] != "-cp"
            or args[3] != reader.ENTRY_CLASS or args[4] != "OBSERVE"
            or type(args[5]) is not str or reader.UUID.fullmatch(args[5]) is None
            or args[6] != MANIFEST_SHA256 or args[7] != reader.WIRE_SHA256
            or args[8] != str(data_dir)):
        fail("NATIVE_ARGUMENT_BINDING")
    run_id = args[5]
    jars = context.get("jars")
    if (type(jars) is not dict
            or type(jars.get("gwt-user-2.7.0.jar")) is not dict
            or type(jars.get("gwt-dev-2.7.0.jar")) is not dict):
        fail("NATIVE_JAR_CONTEXT")
    expected_classpath = ";".join((
        str(context["classesRoot"]),
        jars["gwt-user-2.7.0.jar"]["path"],
        jars["gwt-dev-2.7.0.jar"]["path"]))
    if args[2] != expected_classpath:
        fail("NATIVE_CLASSPATH")
    expected_args = [
        "-ea", "-cp", expected_classpath, reader.ENTRY_CLASS, "OBSERVE",
        run_id, MANIFEST_SHA256, reader.WIRE_SHA256, str(data_dir)]
    request_raw = base._read(call_dir / "request.json", 64 * 1024, "REQUEST_READ")
    request = base.host_document(request_raw, 64 * 1024)
    base._validate_owner_call(
        call_dir, parent_file, parent, request, process,
        context["java"]["path"], expected_args, 480_000, context["processModule"])

    marker_raw = process["Stdout"].encode("utf-8", errors="strict")
    marker = base.strict_console_line(marker_raw, "NATIVE_MARKER")
    op1_raw = base._read(data_dir / "op-1.jsonl", MAX_OP1, "OP1_READ")
    result_raw = base._read(data_dir / "result.json", MAX_RESULT, "RESULT_READ")
    if sha(op1_raw) != OP1_SHA256:
        fail("OP1_HASH")
    if sha(result_raw) != RESULT_SHA256:
        fail("RESULT_HASH")
    doc = reader._strict_json_line(result_raw, MAX_RESULT, "RESULT_FRAMING")
    if doc.get("operation1") is None:
        fail("OP1_RESULT_MISSING")
    try:
        parsed_op1 = base._parse_operation1(
            op1_raw, doc["operation1"], run_id, MANIFEST_SHA256,
            reader.WIRE_SHA256, p07)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and code.startswith("IO_"):
            fail("OP1_PARSE_" + code[3:])
        fail("OP1_P07_VALIDATION")

    validated = baseline(
        reader, doc, parsed_op1, op1_raw, MANIFEST_SHA256,
        reader.WIRE_SHA256, run_id, marker, result_raw, parent["elapsedMs"])
    cases = control_cases(
        doc, reader, parsed_op1, op1_raw, MANIFEST_SHA256,
        reader.WIRE_SHA256, run_id, marker, result_raw,
        parent["elapsedMs"], validated)

    outcomes = []
    for name, expected_code, invoke in cases:
        actual_code = None
        try:
            invoke()
        except reader.ReaderError as exc:
            actual_code = exc.code
        except Exception as exc:
            actual_code = "UNEXPECTED_" + type(exc).__name__
        if actual_code == expected_code:
            status = "EXPECTED_REJECTED"
        elif actual_code is None:
            status = "ACCEPTED_INVALID"
        else:
            status = "WRONG_REJECTION"
        outcomes.append({
            "name": name,
            "expectedCode": expected_code,
            "actualCode": actual_code,
            "status": status,
        })

    baseline(reader, doc, parsed_op1, op1_raw, MANIFEST_SHA256,
             reader.WIRE_SHA256, run_id, marker, result_raw, parent["elapsedMs"])
    rechecked = builder.recheck_inputs(context)
    if (type(rechecked) is not dict
            or rechecked.get("sourceSha") != MANIFEST_SHA256):
        fail("FINAL_INPUT_RECHECK")

    failed = sum(1 for item in outcomes if item["status"] != "EXPECTED_REJECTED")
    return {
        "schema": SCHEMA,
        "status": "PASS_SEMANTIC_REJECTION_CONTROLS" if failed == 0
                  else "FAIL_SEMANTIC_REJECTION_CONTROLS",
        "baselineBeforeControls": "PASS",
        "baselineAfterControls": "PASS",
        "finalInputRecheck": "PASS",
        "controlCount": len(outcomes),
        "expectedRejectedCount": len(outcomes) - failed,
        "failedCount": failed,
        "controls": outcomes,
        "sha256": {
            "reader": READER_SHA256,
            "buildInputs": BUILD_INPUTS_SHA256,
            "runManifest": MANIFEST_SHA256,
            "wire": reader.WIRE_SHA256,
            "nativeParent": sha(parent_raw),
            "operation1": sha(op1_raw),
            "result": sha(result_raw),
        },
    }


def main():
    try:
        output = run()
    except DriverError as exc:
        output = {
            "schema": SCHEMA,
            "status": "FAIL_DRIVER",
            "errorCode": exc.code,
        }
        code = 2
    except Exception as exc:
        output = {
            "schema": SCHEMA,
            "status": "FAIL_DRIVER",
            "errorCode": "UNEXPECTED_" + type(exc).__name__,
        }
        code = 2
    else:
        code = 0 if output["failedCount"] == 0 else 2
    sys.stdout.write(json.dumps(
        output, ensure_ascii=True, allow_nan=False,
        separators=(",", ":"), sort_keys=True) + "\n")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
