"""Authenticated semantic rejection controls for a failure-cohort result."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
import types


ROOT = Path(__file__).resolve().parent
READER_SHA256 = "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
PLAN_SHA256 = "e2cef26d301af9a8350cd39b2f5bc9d5a296c0840d5327578404c68a2896b04b"
WIRE_SHA256 = "c7bf55d15f8166e7d8c767abd3f068ee88087b6a9132f9331af7d21c5c202362"
NATIVE_SOURCE_SHA256 = "dc53245d52c7187868b40456b3952cdb2ecf20bc920b2354ff400f60ff68410d"
FIXTURE_SOURCE_SHA256 = "dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619"
HELPER_SHA256 = "cb8ae8eee78ca90293925b90eec1d8b0ed545668a7dd3a29e4ab169f39542ae2"
MANIFEST_SHA256 = "1274185600f9da79412f133ba569fb727eba673c7dd672e53abd8dcc5fd28a56"
READER_NAME = "read_failure_cohort.py"
MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
MAX_OPERATION_BYTES = 8 * 1024 * 1024


class ControlsError(ValueError):
    def __init__(self, code):
        self.code = code if code.startswith("BF_") else "BF_" + code
        super().__init__(self.code)


def need(condition, code):
    if not condition:
        raise ControlsError(code)


def file_sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_reader():
    path = ROOT / READER_NAME
    try:
        info = path.lstat()
        safe = path.is_file() and not path.is_symlink() \
            and not (getattr(info, "st_file_attributes", 0) & 0x400)
        need(safe, "READER_PATH")
        raw = path.read_bytes()
    except ControlsError:
        raise
    except OSError:
        raise ControlsError("READER_PATH") from None
    need(hashlib.sha256(raw).hexdigest() == READER_SHA256, "READER_HASH")
    module = types.ModuleType("_authenticated_failed_first_reader_for_controls")
    module.__file__ = str(path)
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
    except Exception:
        raise ControlsError("READER_LOAD") from None
    need(module.PLAN_SHA256 == PLAN_SHA256 and module.WIRE_SHA256 == WIRE_SHA256
         and module.NATIVE_SOURCE_SHA256 == NATIVE_SOURCE_SHA256
         and module.FIXTURE_SOURCE_SHA256 == FIXTURE_SOURCE_SHA256
         and module.BUILD_INPUTS_SHA256 == HELPER_SHA256,
         "READER_DEPENDENCY_PINS")
    return module, raw


def _mutate_seed(doc):
    doc["operation1"]["rootSeed"] = "9007199254740992"


def _mutate_receipt_digest(doc):
    doc["operation1"]["receiptSha256"] = "0" * 64


def _mutate_receipt_ordinal(doc):
    doc["reference"]["receiptOrdinal"] = doc["reference"]["lineageOrdinal"]


def _mutate_lineage_ordinal(doc):
    doc["reference"]["lineageOrdinal"] = doc["reference"]["receiptOrdinal"]


def _mutate_prefix_digest(doc):
    doc["reference"]["prefixGeometrySha256"] = "f" * 64


def _mutate_source_release(doc):
    doc["source"]["sourceUnchanged"] = False


def _mutate_manifest_binding(doc):
    doc["finalRunManifestSha256"] = "0" * 64


def _mutate_vector_duplicate(doc):
    ids = doc["capturedOrder"]["netIds"]
    ids[1] = ids[0]


def _mutate_vector_missing(doc):
    doc["capturedOrder"]["netIds"].pop()


def _mutate_vector_unknown(doc):
    captured = doc["capturedOrder"]
    protected = {captured["failedNetId"], captured["priorPromotedNetId"]}
    index = next(i for i, net in enumerate(captured["netIds"]) if net not in protected)
    captured["netIds"][index] = "UNKNOWN_NET_SENTINEL"


def _mutate_failed_net(doc):
    doc["capturedOrder"]["failedNetId"] = "UNKNOWN_NET_SENTINEL"


def _mutate_live_index(doc):
    captured = doc["capturedOrder"]
    index = captured["liveSearchIndex"]
    captured["liveSearchIndex"] = (index + 1) % len(captured["netIds"])


def _mutate_prior_promotion(doc):
    captured = doc["capturedOrder"]
    current = captured["priorPromotedNetId"]
    captured["priorPromotedNetId"] = next(net for net in captured["netIds"]
                                           if net != current)


def _mutate_captured_order(doc):
    captured = doc["capturedOrder"]
    movable = [i for i, net in enumerate(captured["netIds"])
               if net != captured["failedNetId"]]
    first, second = movable[:2]
    captured["netIds"][first], captured["netIds"][second] = (
        captured["netIds"][second], captured["netIds"][first])


def _mutate_control_request(doc):
    doc["control"]["ordering"]["requestedNetIds"].reverse()


def _mutate_trial_request(doc):
    doc["trial"]["ordering"]["requestedNetIds"].reverse()


def _mutate_fresh_prior(doc):
    captured = doc["capturedOrder"]
    current = captured["priorPromotedNetId"]
    doc["control"]["initial"]["priorPromotedNetId"] = next(
        net for net in captured["netIds"] if net != current)


def _mutate_search_count(doc):
    doc["control"]["ordering"]["searchCount"] = 2


def _mutate_complete_flag(doc):
    doc["control"]["outcome"]["complete"] = True


def _mutate_owner_complete(doc):
    doc["control"]["outcome"]["ownerComplete"] = False


def _mutate_reason(doc):
    doc["trial"]["outcome"]["reason"] = "UNKNOWN_NET_SENTINEL:UNMODELED"


def _mutate_terminal_missing(doc):
    doc["control"]["terminalFailure"] = None


def _mutate_terminal_cursor(doc):
    doc["control"]["terminalFailure"]["searchIndex"] += 1


def _mutate_terminal_ordinal_final(doc):
    control = doc["control"]
    control["terminalFailure"]["advanceCallOrdinal"] = control["work"]["advanceCalls"]


def _mutate_terminal_ordinal_early(doc):
    control = doc["control"]
    control["terminalFailure"]["advanceCallOrdinal"] = control["work"]["advanceCalls"] - 2


def _mutate_terminal_unknown(doc):
    doc["control"]["terminalFailure"]["failedNetId"] = "UNKNOWN_NET_SENTINEL"


def _mutate_terminal_untraversed(doc):
    doc["control"]["ordering"]["traversedNetIds"].remove(
        doc["control"]["terminalFailure"]["failedNetId"])


def _mutate_terminal_release(doc):
    doc["control"]["terminalFailure"]["searchReleased"] = False


def _mutate_repeated_feedback(doc):
    terminal = doc["control"]["terminalFailure"]
    captured = doc["capturedOrder"]["failedNetId"]
    terminal["failedNetId"] = captured
    terminal["searchIndex"] = doc["control"]["ordering"]["requestedNetIds"].index(captured) + 1
    terminal["ownerPromotedNetId"] = captured
    terminal["ownerReason"] = captured + ":NO_PATH"
    doc["control"]["outcome"]["reason"] = captured + ":NO_PATH"


def _mutate_trial_cohort(doc):
    doc["trial"]["ordering"]["requestedCohortNetIds"].reverse()


def _mutate_false_skip(doc):
    doc["trialDisposition"].update({"status": "SKIPPED", "reason": "CONTROL_FOUND",
                                    "controlFailedNetId": None, "cohortNetIds": None,
                                    "requestedNetIds": None, "orderChanged": False})


def _mutate_finalizer(doc):
    doc["control"]["finalizers"]["routingGeometry"] = "PASS"


def _mutate_layout(doc):
    doc["control"]["layout"] = {}


def _mutate_diagnostic_ledger(doc):
    doc["work"]["originalDiagnosticUnits"] += 1


def _control_tree_minimum(value, depth=0, visits=None):
    """Independent counter used to cross the documented boundary deliberately."""
    if visits is None:
        visits = [0]
    visits[0] += 1
    need(depth <= 64 and visits[0] <= 1048576, "CONTROL_TREE_BOUND")
    if type(value) is dict:
        total = 1
        for key, child in value.items():
            need(type(key) is str, "CONTROL_TREE_KEY")
            total += 1 + (len(key.encode("utf-8")) + 4095) // 4096
            total += _control_tree_minimum(child, depth + 1, visits)
        return total
    if type(value) is list:
        return 1 + sum(_control_tree_minimum(child, depth + 1, visits) for child in value)
    if type(value) is str:
        return 1 + (len(value.encode("utf-8")) + 4095) // 4096
    need(value is None or type(value) in (int, bool), "CONTROL_TREE_VALUE")
    return 1


def _additive_units(doc):
    work = doc["work"]
    operation = doc["operation1"]
    return (operation["p07OwnerSteps"] + work["originalDiagnosticUnits"]
            + operation["callbackUnits"] + work["extraTerminalCallbackUnits"]
            + work["controlOwnerUnits"] + work["controlWrapperUnits"]
            + work["trialOwnerUnits"] + work["trialWrapperUnits"]
            + work["metadataUnits"])


def _mutate_metadata_underreport(doc):
    minimum = _control_tree_minimum(doc)
    delta = doc["work"]["metadataUnits"] - minimum + 1
    need(delta > 0, "CONTROL_FIXTURE_METADATA_MARGIN")
    doc["work"]["metadataUnits"] -= delta
    doc["work"]["unitsBeforeExport"] -= delta
    need(doc["work"]["metadataUnits"] == minimum - 1
         and doc["work"]["unitsBeforeExport"] == _additive_units(doc),
         "CONTROL_METADATA_RECONCILIATION")


def _mutate_poll_underreport(doc):
    run_work = doc["control"]["work"]
    minimum = (run_work["advanceCalls"] + run_work["failureObservationUnits"]
               + run_work["terminalEvidenceUnits"])
    delta = run_work["wrapperUnits"] - minimum + 1
    need(delta > 0, "CONTROL_FIXTURE_POLL_MARGIN")
    run_work["wrapperUnits"] -= delta
    doc["work"]["controlWrapperUnits"] -= delta
    doc["work"]["unitsBeforeExport"] -= delta
    need(run_work["wrapperUnits"] == minimum - 1
         and doc["work"]["unitsBeforeExport"] == _additive_units(doc),
         "CONTROL_POLL_RECONCILIATION")


def _mutate_copy_cap(doc):
    doc["work"]["copiedEntries"] = doc["work"]["copiedEntryLimit"] + 1


def _mutate_group_cap(doc):
    work = doc["work"]
    run_groups = (doc["control"]["work"]["groups"]
                  + (0 if doc["trial"] is None else doc["trial"]["work"]["groups"]))
    base_groups = doc["operation1"]["groupCount"] + run_groups
    work["wrapperGroupsBeforeExport"] = 640 - base_groups
    work["groupsBeforeExport"] = 640


def _mutate_marker_units(marker):
    marker["totalUnits"] += 1


def _mutate_marker_groups(marker):
    marker["totalGroups"] += 1


def _mutate_marker_max_group(marker):
    marker["maxGroupNanos"] += 1


def _validate_case(reader, base_reader, doc, parsed, op1_raw, manifest_sha,
                   run_id, name, expected_code, mutator):
    mutant = copy.deepcopy(doc)
    mutator(mutant)
    try:
        reader.validate_semantics(mutant, parsed, op1_raw, manifest_sha,
                                  WIRE_SHA256, run_id, base_reader)
    except reader.ReaderError as failure:
        need(failure.code == expected_code,
             "CONTROL_WRONG_REJECTION_" + name.upper())
        return {"case": name, "rejectedWith": expected_code}
    except Exception:
        raise ControlsError("CONTROL_UNEXPECTED_EXCEPTION_" + name.upper()) from None
    raise ControlsError("CONTROL_ACCEPTED_MUTATION_" + name.upper())


def _semantic_cases(reader, base_reader, doc, parsed, op1_raw, manifest_sha, run_id):
    controls = [
        ("result_manifest_binding", "BF_RESULT_IDENTITY", _mutate_manifest_binding),
        ("operation_seed_binding", "BF_OP1_FIELDS", _mutate_seed),
        ("operation_receipt_digest", "BF_OP1_FIELDS", _mutate_receipt_digest),
        ("receipt_ordinal_not_lineage", "BF_REFERENCE_BINDING", _mutate_receipt_ordinal),
        ("lineage_ordinal_not_receipt", "BF_REFERENCE_BINDING", _mutate_lineage_ordinal),
        ("prefix_geometry_digest", "BF_REFERENCE_BINDING", _mutate_prefix_digest),
        ("source_release_flag", "BF_SOURCE_IMMUTABILITY", _mutate_source_release),
        ("captured_vector_duplicate", "BF_CAPTURED_VECTOR_BINDING", _mutate_vector_duplicate),
        ("captured_vector_missing", "BF_CAPTURED_VECTOR_BINDING", _mutate_vector_missing),
        ("captured_vector_unknown_net", "BF_CAPTURED_VECTOR_BINDING", _mutate_vector_unknown),
        ("failed_net_identity", "BF_CAPTURED_VECTOR_BINDING", _mutate_failed_net),
        ("live_search_index", "BF_CAPTURED_VECTOR_BINDING", _mutate_live_index),
        ("prior_promotion_fresh_binding", "BF_FRESH_INITIAL_BINDING", _mutate_prior_promotion),
        ("captured_order_session_binding", "BF_FRESH_ORDERING_BINDING", _mutate_captured_order),
        ("control_requested_order", "BF_FRESH_ORDERING_BINDING", _mutate_control_request),
        ("trial_requested_order", "BF_FRESH_ORDERING_BINDING", _mutate_trial_request),
        ("fresh_prior_promotion", "BF_FRESH_INITIAL_BINDING", _mutate_fresh_prior),
        ("single_search_no_fallback", "BF_FRESH_ORDERING_BINDING", _mutate_search_count),
        ("terminal_failure_missing", "BF_TERMINAL_FAILURE_REQUIRED", _mutate_terminal_missing),
        ("terminal_failure_cursor", "BF_TERMINAL_FAILURE_BINDING", _mutate_terminal_cursor),
        ("terminal_ordinal_is_final_call", "BF_TERMINAL_FAILURE_BINDING",
         _mutate_terminal_ordinal_final),
        ("terminal_ordinal_too_early", "BF_TERMINAL_FAILURE_BINDING",
         _mutate_terminal_ordinal_early),
        ("terminal_failure_unknown_net", "BF_TERMINAL_FAILURE_BINDING",
         _mutate_terminal_unknown),
        ("terminal_failure_not_traversed", "BF_TERMINAL_FAILURE_BINDING",
         _mutate_terminal_untraversed),
        ("terminal_search_not_released", "BF_TERMINAL_FAILURE_BINDING",
         _mutate_terminal_release),
        ("repeated_control_failure", "BF_REPEATED_FAILURE_ID", _mutate_repeated_feedback),
        ("trial_cohort_order", "BF_FRESH_ORDERING_BINDING", _mutate_trial_cohort),
        ("false_found_skip", "BF_TRIAL_EXECUTION_DISPOSITION", _mutate_false_skip),
        ("nonfound_complete_flag", "BF_NONFOUND_OUTCOME", _mutate_complete_flag),
        ("owner_completion_flag", "BF_FRESH_OUTCOME_BINDING", _mutate_owner_complete),
        ("unknown_terminal_reason", "BF_OUTCOME_REASON", _mutate_reason),
        ("nonfound_finalizer_not_run", "BF_NONFOUND_FINALIZERS", _mutate_finalizer),
        ("nonfound_layout_absent", "BF_FRESH_LAYOUT_DISPOSITION", _mutate_layout),
        ("original_diagnostic_group_census", "BF_ORIGINAL_WORK_BINDING",
         _mutate_diagnostic_ledger),
        ("metadata_tree_lower_bound", "BF_METADATA_TREE_ACCOUNTING",
         _mutate_metadata_underreport),
        ("bounded_polling_work", "BF_RUN_WORK_BOUNDS", _mutate_poll_underreport),
        ("copy_entry_cap", "BF_WORK_BOUNDS", _mutate_copy_cap),
        ("combined_group_cap", "BF_WORK_GROUPS", _mutate_group_cap),
    ]
    return [_validate_case(reader, base_reader, doc, parsed, op1_raw, manifest_sha, run_id,
                           name, code, mutator)
            for name, code, mutator in controls]


def _validate_marker_cases(reader, marker, result_raw, doc):
    cases = []
    for name, expected_code, mutate in (
        ("export_total_units", "BF_MARKER_BINDING", _mutate_marker_units),
        ("export_total_groups", "BF_MARKER_BINDING", _mutate_marker_groups),
        ("export_max_group", "BF_MARKER_BINDING", _mutate_marker_max_group),
    ):
        mutant = copy.deepcopy(marker)
        mutate(mutant)
        try:
            reader._validate_marker(mutant, result_raw, doc)
        except reader.ReaderError as failure:
            need(failure.code == expected_code, "CONTROL_WRONG_MARKER_REJECTION")
        else:
            raise ControlsError("CONTROL_ACCEPTED_MARKER_MUTATION")
        cases.append({"case": name, "rejectedWith": expected_code})
    return cases


def run_controls(args):
    need(sys.flags.isolated and sys.dont_write_bytecode, "ISOLATION_REQUIRED")
    need(args.manifest_sha == MANIFEST_SHA256, "MANIFEST_PIN")
    reader, reader_raw = load_reader()
    manifest_path = Path(args.manifest)
    parent_path = Path(args.parent)
    data_path = Path(args.data)
    need(manifest_path.is_absolute() and parent_path.is_absolute() and data_path.is_absolute(),
         "INPUT_PATHS_ABSOLUTE")

    verified = reader.validate_run(manifest_path, args.manifest_sha, parent_path, data_path)
    parent = verified["parent"]
    process = parent.get("process") if type(parent) is dict else None
    need(type(process) is dict and type(process.get("Arguments")) is list
         and len(process["Arguments"]) == 9, "AUTHENTICATED_PROCESS")
    run_id = process["Arguments"][5]
    actual_data = Path(process["Arguments"][8])
    result_raw = reader._read_regular(actual_data / "result.json", MAX_DOCUMENT_BYTES,
                                      "RESULT_READ")
    op1_raw = reader._read_regular(actual_data / "op-1.jsonl", MAX_OPERATION_BYTES,
                                   "OP1_READ")
    need(verified["readerSha256"] == READER_SHA256
         and verified["resultSha256"] == reader.sha(result_raw)
         and verified["op1Sha256"] == reader.sha(op1_raw), "AUTHENTICATED_BASELINE")
    base_reader = verified["context"]["baseReader"]
    p07_reader = verified["context"]["p07Reader"]
    doc = reader.strict_json_line(result_raw)
    try:
        parsed = base_reader._parse_operation1(
            op1_raw, doc.get("operation1"), run_id, args.manifest_sha,
            WIRE_SHA256, p07_reader)
    except Exception:
        raise ControlsError("AUTHENTICATED_P07_PARSE") from None
    baseline = reader.validate_semantics(doc, parsed, op1_raw, args.manifest_sha,
                                         WIRE_SHA256, run_id, base_reader)
    need(doc["trialDisposition"]["status"] == "RUN"
         and doc["trialDisposition"]["reason"] == "TRIAL_EXECUTED"
         and doc["trial"] is not None
         and doc["control"]["outcome"]["disposition"] == "NO_PATH"
         and doc["trial"]["outcome"]["disposition"] == "CAP_REJECTED"
         and doc["trial"]["outcome"]["reason"] == "D_COIL_LOW:BRANCH_SEARCH_LIMIT"
         and baseline["trialDisposition"]["status"] == "RUN",
         "EXPECTED_AUTHENTICATED_PAIR")

    parent_receipt_sha = file_sha256(parent_path)
    semantic_cases = _semantic_cases(reader, base_reader, doc, parsed, op1_raw,
                                     args.manifest_sha, run_id)
    marker_cases = _validate_marker_cases(reader, verified["marker"], result_raw, doc)

    result_after = reader._read_regular(actual_data / "result.json", MAX_DOCUMENT_BYTES,
                                        "RESULT_REREAD")
    op1_after = reader._read_regular(actual_data / "op-1.jsonl", MAX_OPERATION_BYTES,
                                     "OP1_REREAD")
    need(reader.sha(result_after) == verified["resultSha256"]
         and reader.sha(op1_after) == verified["op1Sha256"]
         and file_sha256(reader.ROOT / READER_NAME) == reader.sha(reader_raw)
         and file_sha256(parent_path) == parent_receipt_sha,
         "INPUTS_CHANGED_DURING_CONTROLS")
    all_cases = semantic_cases + marker_cases
    return {
        "schema": "tsj-failure-cohort-semantic-controls-v1",
        "status": "PASS_AUTHENTICATED_SEMANTIC_MUTATION_CONTROLS_ONLY",
        "readerSha256": reader.sha(reader_raw),
        "sourceManifestSha256": args.manifest_sha,
        "resultSha256": verified["resultSha256"],
        "operation1Sha256": verified["op1Sha256"],
        "parentReceiptSha256": parent_receipt_sha,
        "baselineControlDisposition": "NO_PATH",
        "baselineTrialDisposition": "CAP_REJECTED",
        "caseCount": len(all_cases),
        "cases": all_cases,
        "allMutationsRejectedWithExpectedCodes": True,
        "nativeRunCreated": False,
        "productionAdopted": False,
        "routingTreatmentSelected": False,
        "layoutQualification": False,
        "feasibilityClaimed": False,
        "playable100Accepted": False,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(prog="Semantic-Controls-R1.py")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--manifest-sha", required=True)
    parser.add_argument("--parent", required=True)
    parser.add_argument("--data", required=True)
    args = parser.parse_args(argv)
    try:
        result = run_controls(args)
        sys.stdout.write(json.dumps(result, ensure_ascii=True, allow_nan=False,
                                    separators=(",", ":")) + "\n")
        return 0
    except ControlsError as failure:
        sys.stderr.write(failure.code + "\n")
        return 2
    except Exception as failure:
        code = getattr(failure, "code", None)
        if type(code) is str:
            sys.stderr.write((code if code.startswith("BF_") else "BF_" + code) + "\n")
        else:
            sys.stderr.write("BF_SEMANTIC_CONTROLS_FAILURE\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
