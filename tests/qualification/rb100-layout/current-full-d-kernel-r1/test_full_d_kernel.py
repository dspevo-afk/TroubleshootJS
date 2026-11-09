"""Independent synthetic full-D V2 admission tests.

These fixtures are authored locally from the frozen source contracts.  The only
non-test helper is the explicitly named synthetic AUTHORED P07 receipt builder;
the subject is loaded by an explicit path.  No prior packet, old consumer,
manifest resolver, or native process is loaded.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
from types import MappingProxyType
import unittest


_PARSER = argparse.ArgumentParser(add_help=False)
_PARSER.add_argument(
    "--kernel-source",
    default=str(Path(__file__).with_name("full_d_kernel.py")),
)
_PARSER.add_argument(
    "--fixture-builder",
    default=str(Path(__file__).with_name("synthetic_authored_p07.py")),
)
_ARGS, _REMAINING = _PARSER.parse_known_args()
sys.argv[:] = [sys.argv[0], *_REMAINING]

_SUBJECT_PATH = Path(_ARGS.kernel_source).resolve()
_BUILDER_PATH = Path(_ARGS.fixture_builder).resolve()
_EXPECTED_BUILDER_SHA256 = (
    "4f80ec976e565a585db061207baee63ccfedf52fb5c64c627895ccbcd890d8f7"
)


def _load_explicit(path: Path, module_name: str):
    if not path.is_file():
        raise RuntimeError("FULL_D_EXPLICIT_SOURCE_MISSING:" + path.name)
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError("FULL_D_EXPLICIT_SOURCE_LOADER:" + path.name)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


_BUILDER_RAW = _BUILDER_PATH.read_bytes()
if hashlib.sha256(_BUILDER_RAW).hexdigest() != _EXPECTED_BUILDER_SHA256:
    raise RuntimeError("SYNTHETIC_AUTHORED_P07_BUILDER_SHA256")
_BUILDER = _load_explicit(_BUILDER_PATH, "_tsj_full_d_authored_p07_fixture")
_SUBJECT = _load_explicit(_SUBJECT_PATH, "_tsj_full_d_kernel_subject")


_RESULT_KEYS = (
    "schema", "mode", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "inputContextSha256", "qualifiedPrior",
    "originalCapture", "cohort", "arms", "work", "cleanup",
)
_MARKER_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "inputContextSha256", "resultSha256",
    "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
    "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup",
)
_WORK_KEYS = (
    "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
    "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
    "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "setupUnits",
    "setupGroups", "setupCopiedEntries", "setupCopyChunks",
    "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
    "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
    "originalCaptureGroups", "originalCaptureCopiedEntries",
    "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits",
    "nullObserverUnits", "observeOwnerUnits", "observeWrapperUnits",
    "observeObserverUnits", "observerUnits", "meterUnits", "metadataUnits",
    "metadataGroups", "metadataCopiedEntries", "metadataCopyChunks",
    "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit",
)
_RUNTIME_KEYS = (
    "runId", "sourceManifestSha256", "wireContractSha256",
    "inputContextSha256",
)

_RUN_ID = "66666666-6666-4666-8666-666666666666"
_CAPTURE_ID = "77777777-7777-4777-8777-777777777777"
_NULL_ARM_ID = "88888888-8888-4888-8888-888888888888"
_OBSERVE_ARM_ID = "99999999-9999-4999-8999-999999999999"
_SOURCE_SHA256 = "a" * 64
_HISTORICAL_PRIOR_RUN_ID = "12345678-1234-4234-8234-123456789abc"
_COHORT_IDS = ["A_COIL_LOW", "B_COIL_LOW"]


def _canonical_line(value):
    return (
        json.dumps(
            value,
            ensure_ascii=True,
            allow_nan=False,
            separators=(",", ":"),
            sort_keys=False,
        ).encode("ascii")
        + b"\n"
    )


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _capture_net_ids():
    # A_COIL_LOW stays at captured index 43 for the historical context contract.
    # Stable cohort ordering then places D_COIL_LOW at requested ordinal 43.
    return (
        ["B_COIL_LOW"]
        + ["SYN_NET_{:02d}".format(index) for index in range(40)]
        + ["D_COIL_LOW", "D_SENSE", "A_COIL_LOW"]
        + ["SYN_NET_{:02d}".format(index) for index in range(40, 56)]
    )


def _stable_expected_order(captured_ids):
    cohort = set(_COHORT_IDS)
    return list(_COHORT_IDS) + [net for net in captured_ids if net not in cohort]


def _context_and_runtime(physical_context):
    captured_ids = _capture_net_ids()
    requested_ids = _stable_expected_order(captured_ids)
    trial = {
        "status": "RUN",
        "reason": "TRIAL_EXECUTED",
        "capturedFailedNetId": "A_COIL_LOW",
        "controlFailedNetId": "B_COIL_LOW",
        "cohortNetIds": list(_COHORT_IDS),
        "requestedNetIds": requested_ids,
        "orderChanged": True,
        "feedbackValidated": True,
    }
    prior = {
        "sourceParent": "d" * 40,
        "manifestSha256": "1" * 64,
        "sourceManifestSha256": "2" * 64,
        "readerSha256": "3" * 64,
        "resultSha256": "4" * 64,
        "operation1Sha256": "5" * 64,
        "parentReceiptSha256": "6" * 64,
        "priorRunId": _HISTORICAL_PRIOR_RUN_ID,
        "priorOriginalReceipt": {
            "receiptPath": "op-1.jsonl",
            "receiptBytes": 4096,
            "receiptSha256": "5" * 64,
            "receiptOrdinal": 307,
            "lineageOrdinal": 77,
        },
        "trialDisposition": copy.deepcopy(trial),
    }
    owner_settings = {
        "boardMode": "FULLER_TWO_LAYER",
        "maxExpansions": 1_000_000,
        "expandedAtFailure": 918_222,
        "maxVias": 48,
        "viaCost": 60,
        "maxTransitions": 4,
        "branchStrategy": "CANONICAL_NEAREST",
        "netStrategy": "CANONICAL",
        "roleWidthsEnabled": True,
        "orderingStreamEnabled": True,
    }
    seed_values = {
        "rootSeed": "9007199254740993",
        "placementSeed": "-6587958367974952701",
        "routingSeed": "8445301674744508279",
    }
    source_identity = {
        "geometryBeforeSha256": "8" * 64,
        "geometryAfterSha256": "8" * 64,
        "detachedGeometrySha256": "a" * 64,
        "effectsBeforeSha256": "b" * 64,
        "effectsAfterSha256": "b" * 64,
        "traceCount": 0,
        "holeCount": 0,
        "padLandIdentity": {
            "format": "TSJ_PAD_LAND_ASCII_V1",
            "rowCount": 246,
            "sha256": (
                "541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0"
            ),
        },
    }
    context = {
        "schema": "tsj-cohort-terminal-input-context-v2",
        "nativeProjection": {
            "prior": prior,
            "capture": {
                "cohortNetIds": list(_COHORT_IDS),
                "capturedNetIds": captured_ids,
                "trialDisposition": copy.deepcopy(trial),
                "controlDisposition": "NO_PATH",
                "trialOutcome": "CAP_REJECTED",
                "passIndex": 2,
                "failedNetId": "A_COIL_LOW",
                "priorPromotedNetId": "D_SENSE",
                "liveSearchIndex": 44,
            },
            "ownerSettings": owner_settings,
            "seedValues": seed_values,
            "sourceIdentity": source_identity,
        },
        "physicalContext": copy.deepcopy(physical_context),
    }
    context_sha256 = _sha(_canonical_line(context))
    runtime_identity = {
        "runId": _RUN_ID,
        "sourceManifestSha256": _SOURCE_SHA256,
        "wireContractSha256": _SUBJECT.WIRE_CONTRACT_SHA256,
        "inputContextSha256": context_sha256,
    }
    return context, runtime_identity


def _captured_for_runs(context):
    settings = context["nativeProjection"]["ownerSettings"]
    return {
        "owner": {
            "policy": settings["boardMode"],
            "maximumExpansions": settings["maxExpansions"],
            "expandedAtFailure": settings["expandedAtFailure"],
            "maximumVias": settings["maxVias"],
            "viaCost": settings["viaCost"],
            "policyTransitions": settings["maxTransitions"],
            "policyVias": settings["maxVias"],
            "branchOrderStrategy": settings["branchStrategy"],
            "netOrderStrategy": settings["netStrategy"],
            "roleWidths": settings["roleWidthsEnabled"],
            "orderingStream": settings["orderingStreamEnabled"],
        },
        "netIds": list(context["nativeProjection"]["capture"]["capturedNetIds"]),
        "failedNetId": "A_COIL_LOW",
        "priorPromotedNetId": "D_SENSE",
    }


def _trial_run(captured, expected_order):
    traversed = list(expected_order[:43])
    terminal_units = 11 + len(traversed)
    work = {
        "advanceCalls": 2,
        "ownerUnits": 7,
        "wrapperUnits": 2 + 2 + terminal_units,
        "copyEntries": 0,
        "copyChunks": 0,
        "groups": 1,
        "maxGroupNanos": 100,
        "maximumUnitsPerGroup": 7 + 2 + 2 + terminal_units,
        "failureObservationUnits": 2,
        "terminalEvidenceUnits": terminal_units,
    }
    owner = captured["owner"]
    return {
        "kind": "OBSERVED_FAILURE_COHORT_TRIAL",
        "initial": {
            "pass": 2,
            "passes": 0,
            "expanded": 0,
            "maximumExpansions": owner["maximumExpansions"],
            "maximumVias": owner["maximumVias"],
            "priorPromotedNetId": captured["priorPromotedNetId"],
            "policy": owner["policy"],
            "viaCost": owner["viaCost"],
            "policyTransitions": owner["policyTransitions"],
            "policyVias": owner["policyVias"],
            "branchOrderStrategy": owner["branchOrderStrategy"],
            "netOrderStrategy": owner["netOrderStrategy"],
            "roleWidths": owner["roleWidths"],
            "capturedOrderingStream": owner["orderingStream"],
            "sessionOrderingStream": False,
            "instrumentationModeDifference": owner["orderingStream"],
            "routingRelevantSettingsMatch": True,
            "traceCount": 0,
            "holeCount": 0,
        },
        "ordering": {
            "capturedNetIds": list(captured["netIds"]),
            "computedNetIds": list(captured["netIds"]),
            "requestedCohortNetIds": list(_COHORT_IDS),
            "requestedNetIds": list(expected_order),
            "traversedNetIds": traversed,
            "orderMatchesCapture": True,
            "searchCount": 1,
        },
        "outcome": {
            "disposition": "CAP_REJECTED",
            "reason": "D_COIL_LOW:BRANCH_SEARCH_LIMIT",
            "complete": False,
            "ownerComplete": True,
            "pass": 3,
            "passes": 1,
            "expanded": 419_905,
            "maximumExpansions": owner["maximumExpansions"],
            "errorClass": None,
            "errorMessage": None,
        },
        "terminalFailure": {
            "failedNetId": "D_COIL_LOW",
            "searchIndex": 43,
            "searchPass": 2,
            "passBefore": 2,
            "passAfter": 3,
            "advanceCallOrdinal": 1,
            "ownerSearchCleared": True,
            "searchReleased": True,
            "ownerPromotedNetId": "D_COIL_LOW",
            "ownerReason": "D_COIL_LOW:BRANCH_SEARCH_LIMIT",
        },
        "finalizers": {
            "routingGeometry": "NOT_RUN",
            "allNetConnectivity": "NOT_RUN",
            "twoLayerRules": "NOT_RUN",
            "routeQuality": "NOT_RUN",
            "viaCap": "NOT_RUN",
        },
        "layout": None,
        "work": work,
    }


def _observation_rows(physical_context):
    by_id = {
        row["padId"]: row
        for row in physical_context["qualifiedP07Pads"]
    }
    rows_for_target = [
        row for row in physical_context["qualifiedP07Pads"]
        if row["netId"] == "D_COIL_LOW"
    ]
    root = min(rows_for_target, key=lambda row: row["padId"])
    nearest = min(
        (row for row in rows_for_target if row["padId"] != root["padId"]),
        key=lambda row: (
            abs(row["x"] - root["x"]) + abs(row["y"] - root["y"]),
            row["padId"],
        ),
    )
    initial_distance = abs(nearest["x"] - root["x"]) + abs(nearest["y"] - root["y"])
    snapshot = {
        "pass": 2,
        "netId": "D_COIL_LOW",
        "startPadId": nearest["padId"],
        "rootPadId": root["padId"],
        "startX": nearest["x"],
        "startY": nearest["y"],
        "rootX": root["x"],
        "rootY": root["y"],
        "gridWidth": physical_context["gridWidth"],
        "gridHeight": physical_context["gridHeight"],
        "goalsTop": 1,
        "goalsBottom": 1,
        "prefixViaCount": 0,
        "reachedPadCount": 1,
        "initialManhattanDistance": initial_distance,
        "minimumExploredManhattanDistance": 0,
        "branchExpanded": 100_000,
        "totalExpanded": 419_905,
        "expandedTop": 60_000,
        "expandedBottom": 40_000,
        "frontierCount": 1,
        "bestStateCount": 1,
        "terminalReason": "BRANCH_SEARCH_LIMIT",
        "relaxedBoundStarted": False,
        "relaxedBoundComplete": False,
        "relaxedBoundObservationChecked": False,
        "relaxedBoundLiveLayoutStable": False,
        "relaxedBoundRouterStateStable": False,
        "relaxedBoundRouterEffectsStable": False,
        "relaxedBoundWorkUnits": 0,
        "relaxedBoundOrdinaryProbes": 0,
        "relaxedBoundViaProbes": 0,
        "relaxedBoundRefreshes": 0,
    }
    # With 10,000 cells: (((2*2+1)*5+4)*10,000)+321 = 290,321.
    head = {
        "branchOrdinal": 1,
        "cell": 321,
        "layer": 1,
        "direction": 4,
        "transitions": 2,
        "cost": 3,
        "storedHeuristic": 5,
        "serial": 1,
        "key": 290_321,
        "storedPriority": 8,
    }
    # d=1, h=1, v=12: entries=43, chunks=2, units=69+12+21=102.
    observation = {
        "schema": "tsj-cohort-terminal-observation-v2",
        "mode": "OBSERVE",
        "state": "FIRST_NON_FOUND",
        "callbackCount": 1,
        "foundCallbackCount": 0,
        "nonFoundCallbackCount": 1,
        "firstNetOrdinal": 43,
        "lastNetOrdinal": 43,
        "lastCallbackTotalExpanded": 419_905,
        "selectedCallbackOrdinal": 1,
        "transientTupleCopies": 2,
        "transientFieldCopies": 43,
        "validationCount": 12,
        "observerUnits": 102,
        "copiedEntries": 43,
        "copyChunks": 2,
        "selectedSnapshot": snapshot,
        "selectedHead": head,
    }
    null_observation = {
        "schema": "tsj-cohort-terminal-observation-v2",
        "mode": "NULL",
        "state": "NOT_ENABLED",
        "callbackCount": 0,
        "foundCallbackCount": 0,
        "nonFoundCallbackCount": 0,
        "firstNetOrdinal": None,
        "lastNetOrdinal": None,
        "lastCallbackTotalExpanded": None,
        "selectedCallbackOrdinal": None,
        "transientTupleCopies": 0,
        "transientFieldCopies": 0,
        "validationCount": 0,
        "observerUnits": 0,
        "copiedEntries": 0,
        "copyChunks": 0,
        "selectedSnapshot": None,
        "selectedHead": None,
    }
    # Both ids are read from the explicit physical rows, not a validator result.
    assert snapshot["startPadId"] in by_id and snapshot["rootPadId"] in by_id
    return null_observation, observation


def _original_capture(context, runtime, op1_raw):
    projection = context["nativeProjection"]
    settings = projection["ownerSettings"]
    return {
        "schema": "tsj-cohort-terminal-original-capture-v1",
        "captureId": _CAPTURE_ID,
        "sourceManifestSha256": runtime["sourceManifestSha256"],
        "operation": "AUTHORED",
        "receipt": {
            "receiptPath": "op-1.jsonl",
            "receiptBytes": len(op1_raw),
            "receiptSha256": _sha(op1_raw),
            "receiptOrdinal": 307,
            "lineageOrdinal": 77,
        },
        "request": {"requestIndex": 0, "selectedAttempt": 4, "pass": 2},
        "seeds": copy.deepcopy(projection["seedValues"]),
        "ownerSettings": {
            "policy": settings["boardMode"],
            "maximumExpansions": settings["maxExpansions"],
            "maximumVias": settings["maxVias"],
            "viaCost": settings["viaCost"],
            "policyTransitions": settings["maxTransitions"],
            "policyVias": settings["maxVias"],
            "branchOrderStrategy": settings["branchStrategy"],
            "netOrderStrategy": settings["netStrategy"],
            "roleWidths": settings["roleWidthsEnabled"],
            "orderingStream": settings["orderingStreamEnabled"],
        },
        "priorPromotedNetId": "D_SENSE",
        "netIds": list(projection["capture"]["capturedNetIds"]),
        "template": {
            "geometryBeforeSha256": projection["sourceIdentity"]["geometryBeforeSha256"],
            "geometryAfterSha256": projection["sourceIdentity"]["geometryAfterSha256"],
            "effectsBeforeSha256": projection["sourceIdentity"]["effectsBeforeSha256"],
            "effectsAfterSha256": projection["sourceIdentity"]["effectsAfterSha256"],
            "traceCount": 0,
            "holeCount": 0,
        },
        "padIdentity": {
            "schema": "TSJ_PAD_LAND_ASCII_V1",
            "padCount": 246,
            "padLandSha256": (
                "541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0"
            ),
        },
        "ownersReleased": True,
        "contextRestored": True,
        "cleanupNanos": 100,
    }


def _arm_cleanup(context):
    geometry = context["nativeProjection"]["sourceIdentity"]["geometryBeforeSha256"]
    return {
        "schema": "tsj-cohort-terminal-arm-cleanup-v1",
        "adapterSessionAliasDropped": True,
        "adapterSearchAliasDropped": True,
        "adapterCandidateAliasDropped": True,
        "adapterRouteResultAliasDropped": True,
        "adapterBoardTemplateAliasesDropped": True,
        "sourceGeometryBeforeSha256": geometry,
        "sourceGeometryAfterSha256": geometry,
        "contextRestored": True,
        "cleanupNanos": 100,
    }


def _tree_minimum_units(root):
    """Independent transcription of the bounded N tree-copy lower bound."""
    stack = [(root, 0)]
    units = 0
    while stack:
        value, depth = stack.pop()
        if depth > 64:
            raise AssertionError("fixture tree exceeds source depth")
        units += 1
        if type(value) is dict:
            for key, child in value.items():
                encoded = key.encode("utf-8")
                units += 1 + (len(encoded) + 4095) // 4096
                stack.append((child, depth + 1))
        elif type(value) is list:
            stack.extend((child, depth + 1) for child in value)
        elif type(value) is str:
            encoded = value.encode("utf-8")
            units += (len(encoded) + 4095) // 4096
    return units


def _work_record(doc, runs, observe, metadata_units):
    null_run, observe_run = runs
    setup_units = 40
    original_capture_owner_units = 7
    original_capture_wrapper_units = 9
    original_capture_callback_units = 1
    original_capture_diagnostic_units = 0
    setup_groups = 1
    original_capture_groups = 2
    metadata_groups = 1
    groups_before_export = (
        setup_groups + original_capture_groups
        + null_run["work"]["groups"] + observe_run["work"]["groups"]
        + metadata_groups
    )
    owner_group_count = 2
    wrapper_groups = groups_before_export - owner_group_count
    setup_entries = 0
    setup_chunks = 0
    original_entries = 0
    original_chunks = 0
    metadata_entries = 12
    metadata_chunks = 1
    copied_entries = (
        setup_entries + original_entries
        + null_run["work"]["copyEntries"] + observe_run["work"]["copyEntries"]
        + observe["copiedEntries"] + metadata_entries
    )
    copy_chunks = (
        setup_chunks + original_chunks
        + null_run["work"]["copyChunks"] + observe_run["work"]["copyChunks"]
        + observe["copyChunks"] + metadata_chunks
    )
    counters = {
        "elapsedCutoffNanos": 1_000_000,
        "cleanupNanos": 500,
        "groupsBeforeExport": groups_before_export,
        "wrapperGroupsBeforeExport": wrapper_groups,
        "maxGroupNanosBeforeExport": 1_000,
        "maximumUnitsPerGroupBeforeExport": max(128, metadata_units),
        "setupUnits": setup_units,
        "setupGroups": setup_groups,
        "setupCopiedEntries": setup_entries,
        "setupCopyChunks": setup_chunks,
        "originalCaptureOwnerUnits": original_capture_owner_units,
        "originalCaptureWrapperUnits": original_capture_wrapper_units,
        "originalCaptureCallbackUnits": original_capture_callback_units,
        "originalCaptureDiagnosticUnits": original_capture_diagnostic_units,
        "originalCaptureGroups": original_capture_groups,
        "originalCaptureCopiedEntries": original_entries,
        "originalCaptureCopyChunks": original_chunks,
        "nullOwnerUnits": null_run["work"]["ownerUnits"],
        "nullWrapperUnits": null_run["work"]["wrapperUnits"],
        "nullObserverUnits": 0,
        "observeOwnerUnits": observe_run["work"]["ownerUnits"],
        "observeWrapperUnits": observe_run["work"]["wrapperUnits"],
        "observeObserverUnits": observe["observerUnits"],
        "observerUnits": observe["observerUnits"],
        "metadataUnits": metadata_units,
        "metadataGroups": metadata_groups,
        "metadataCopiedEntries": metadata_entries,
        "metadataCopyChunks": metadata_chunks,
        "copiedEntries": copied_entries,
        "copyChunks": copy_chunks,
        "copyChunkLimit": 4096,
        "copiedEntryLimit": 1_048_576,
    }
    counters["meterUnits"] = sum(
        counters[name]
        for name in (
            "setupUnits",
            "originalCaptureOwnerUnits",
            "originalCaptureWrapperUnits",
            "originalCaptureCallbackUnits",
            "originalCaptureDiagnosticUnits",
            "nullOwnerUnits",
            "nullWrapperUnits",
            "nullObserverUnits",
            "observeOwnerUnits",
            "observeWrapperUnits",
            "observeObserverUnits",
            "metadataUnits",
        )
    )
    counters["unitsBeforeExport"] = counters["meterUnits"]
    return {name: counters[name] for name in _WORK_KEYS}


def _marker_for(doc, result_raw):
    work = doc["work"]
    export_units = 5 * ((len(result_raw) + 4095) // 4096) + 3
    final_export = 100
    marker = {
        "schema": "tsj-cohort-terminal-marker-v2",
        "status": "COMPLETED_DIAGNOSTIC",
        "runId": doc["runId"],
        "sourceManifestSha256": doc["sourceManifestSha256"],
        "wireContractSha256": doc["wireContractSha256"],
        "inputContextSha256": doc["inputContextSha256"],
        "resultSha256": _sha(result_raw),
        "resultBytes": len(result_raw),
        "elapsedNanos": work["elapsedCutoffNanos"] + final_export + 100,
        "finalExportNanos": final_export,
        "totalGroups": work["groupsBeforeExport"] + 1,
        "totalUnits": work["unitsBeforeExport"] + export_units,
        "maxGroupNanos": max(work["maxGroupNanosBeforeExport"], final_export),
        "maximumUnitsPerGroup": max(
            work["maximumUnitsPerGroupBeforeExport"], export_units
        ),
    }
    return marker, _canonical_line(marker)


def _build_packet():
    captured_ids = _capture_net_ids()
    physical_context = _BUILDER.make_physical_context(
        grid_width=100,
        grid_height=100,
        net_ids=captured_ids,
    )
    context, runtime = _context_and_runtime(physical_context)
    op1 = _BUILDER.build_authored_operation1(
        run_id=runtime["runId"],
        source_sha256=runtime["sourceManifestSha256"],
        wire_sha256=runtime["wireContractSha256"],
        physical_context=physical_context,
        branch_net_id="D_COIL_LOW",
    )
    op1_raw = op1["raw"]
    captured = _captured_for_runs(context)
    expected_order = _stable_expected_order(captured_ids)
    null_run = _trial_run(captured, expected_order)
    observe_run = _trial_run(captured, expected_order)
    null_observation, observe_observation = _observation_rows(physical_context)
    prior = {
        "schema": "tsj-cohort-qualified-prior-v2",
        "inputContextSha256": runtime["inputContextSha256"],
    }
    prior.update(copy.deepcopy(context["nativeProjection"]["prior"]))
    doc = {
        "schema": "tsj-cohort-terminal-result-v2",
        "mode": "OBSERVED_COHORT_TERMINAL_TELEMETRY",
        "status": "COMPLETED_DIAGNOSTIC_ONLY",
        "runId": runtime["runId"],
        "sourceManifestSha256": runtime["sourceManifestSha256"],
        "wireContractSha256": runtime["wireContractSha256"],
        "inputContextSha256": runtime["inputContextSha256"],
        "qualifiedPrior": prior,
        "originalCapture": _original_capture(context, runtime, op1_raw),
        "cohort": {
            "netIds": list(_COHORT_IDS),
            "requestedNetIds": expected_order,
        },
        "arms": {
            "null": {
                "armRole": "NULL",
                "armId": _NULL_ARM_ID,
                "freshRun": null_run,
                "observation": null_observation,
                "cleanup": _arm_cleanup(context),
            },
            "observe": {
                "armRole": "OBSERVE",
                "armId": _OBSERVE_ARM_ID,
                "freshRun": observe_run,
                "observation": observe_observation,
                "cleanup": _arm_cleanup(context),
            },
        },
        "work": {},
        "cleanup": {
            "schema": "tsj-cohort-terminal-cleanup-v1",
            "originalCaptureOwnersReleased": True,
            "originalCaptureContextRestored": True,
            "nullArmAliasesDropped": True,
            "observeArmAliasesDropped": True,
            "sourceGeometryUnchanged": True,
            "effectsScope": "ORIGINAL_CAPTURE_COPY_ONLY",
            "originalCaptureCopyEffectsUnchanged": True,
            "contextRestored": True,
            "cleanupNanos": 500,
        },
    }
    doc["work"] = _work_record(
        doc, (null_run, observe_run), observe_observation, 0
    )
    tree_minimum = _tree_minimum_units(doc)
    metadata_units = tree_minimum + 512
    doc["work"] = _work_record(
        doc, (null_run, observe_run), observe_observation, metadata_units
    )
    doc["cleanup"]["cleanupNanos"] = doc["work"]["cleanupNanos"]
    tree_minimum = _tree_minimum_units(doc)
    if doc["work"]["metadataUnits"] < tree_minimum:
        raise AssertionError("fixture metadata demand below final tree floor")
    result_raw = _canonical_line(doc)
    marker, marker_raw = _marker_for(doc, result_raw)
    return {
        "context": context,
        "runtime": runtime,
        "physical": physical_context,
        "p07": op1,
        "op1Raw": op1_raw,
        "resultDoc": doc,
        "resultRaw": result_raw,
        "markerDoc": marker,
        "markerRaw": marker_raw,
        "treeMinimum": tree_minimum,
        "expectedOrder": expected_order,
    }


def _refresh_result_and_marker(packet):
    packet["resultRaw"] = _canonical_line(packet["resultDoc"])
    packet["markerDoc"], packet["markerRaw"] = _marker_for(
        packet["resultDoc"], packet["resultRaw"]
    )


def _rebuild_op1(packet, mutator):
    rows = [json.loads(line) for line in packet["op1Raw"].splitlines()]
    mutator(rows)
    pads = [row for row in rows if row.get("record") == "PAD"]
    p07_result = next(row for row in rows if row.get("record") == "RESULT")
    p07_result["context"]["padCensusSha256"] = _sha(
        _BUILDER._pad_census_bytes(pads)
    )
    group_bytes = 0
    for row in rows:
        if row.get("record") == "GROUP":
            row["serializedBeforeOwnLineBytes"] = group_bytes
            group_bytes = 0
        else:
            group_bytes += len(_BUILDER._json_line(row))
    groups = [row for row in rows if row.get("record") == "GROUP"]
    if groups:
        groups[0]["hashInputBytes"] = (
            len(_BUILDER.DEFAULT_PRISTINE) * 2
            + len(_BUILDER._pad_census_bytes(pads))
            + 1
        )
    packet["op1Raw"] = b"".join(_BUILDER._json_line(row) for row in rows)
    packet["resultDoc"]["originalCapture"]["receipt"]["receiptBytes"] = len(
        packet["op1Raw"]
    )
    packet["resultDoc"]["originalCapture"]["receipt"]["receiptSha256"] = _sha(
        packet["op1Raw"]
    )
    _refresh_result_and_marker(packet)


def _with_result_mutation(mutator):
    packet = _build_packet()
    mutator(packet["resultDoc"])
    _refresh_result_and_marker(packet)
    return packet


def _with_marker_mutation(mutator):
    packet = _build_packet()
    mutator(packet["markerDoc"])
    packet["markerRaw"] = _canonical_line(packet["markerDoc"])
    return packet


def _call(packet, *, context=None, runtime=None, op1_raw=None,
          result_raw=None, marker_raw=None):
    return _SUBJECT.validate_full_d_v2(
        context=packet["context"] if context is None else context,
        runtime_identity=packet["runtime"] if runtime is None else runtime,
        op1_raw=packet["op1Raw"] if op1_raw is None else op1_raw,
        result_raw=packet["resultRaw"] if result_raw is None else result_raw,
        marker_raw=packet["markerRaw"] if marker_raw is None else marker_raw,
    )


class FullDSyntheticAdmissionTests(unittest.TestCase):
    def reject(self, expected_code, packet, **overrides):
        with self.assertRaises(_SUBJECT.ReaderError) as caught:
            _call(packet, **overrides)
        self.assertEqual(caught.exception.code, expected_code)

    def test_independent_full_100_component_246_pad_60_net_positive(self):
        packet = _build_packet()
        context_before = copy.deepcopy(packet["context"])
        result = _call(packet)

        self.assertEqual(packet["context"], context_before)
        self.assertEqual(tuple(packet["resultDoc"]), _RESULT_KEYS)
        self.assertEqual(tuple(packet["resultDoc"]["work"]), _WORK_KEYS)
        self.assertEqual(tuple(packet["markerDoc"]), _MARKER_KEYS)
        self.assertEqual(tuple(packet["runtime"]), _RUNTIME_KEYS)
        self.assertEqual(len(packet["physical"]["qualifiedP07Pads"]), 246)
        self.assertEqual(
            len({row["componentId"] for row in packet["physical"]["qualifiedP07Pads"]}),
            100,
        )
        self.assertEqual(
            len({row["netId"] for row in packet["physical"]["qualifiedP07Pads"]}),
            60,
        )
        self.assertEqual(packet["p07"]["expected"]["padCount"], 246)
        self.assertEqual(packet["p07"]["expected"]["componentCount"], 100)
        self.assertEqual(packet["p07"]["expected"]["netCount"], 60)
        self.assertEqual(packet["p07"]["expected"]["branchNetId"], "D_COIL_LOW")
        self.assertEqual(packet["p07"]["expected"]["groupCount"], 2)
        self.assertEqual(packet["expectedOrder"][42], "D_COIL_LOW")
        self.assertEqual(
            packet["resultDoc"]["arms"]["null"]["freshRun"]["outcome"]["expanded"],
            419_905,
        )
        self.assertEqual(
            packet["resultDoc"]["arms"]["observe"]["freshRun"]["outcome"]["expanded"],
            419_905,
        )
        for arm_name in ("null", "observe"):
            run = packet["resultDoc"]["arms"][arm_name]["freshRun"]
            self.assertEqual(run["outcome"]["reason"], "D_COIL_LOW:BRANCH_SEARCH_LIMIT")
            self.assertEqual(len(run["ordering"]["traversedNetIds"]), 43)
            self.assertEqual(run["ordering"]["traversedNetIds"][-1], "D_COIL_LOW")
        self.assertEqual(
            packet["resultDoc"]["arms"]["observe"]["freshRun"]["ordering"][
                "traversedNetIds"
            ][-1],
            "D_COIL_LOW",
        )
        self.assertEqual(
            len(packet["resultDoc"]["arms"]["observe"]["freshRun"]["ordering"][
                "traversedNetIds"
            ]),
            43,
        )
        self.assertNotEqual(
            packet["resultDoc"]["qualifiedPrior"]["operation1Sha256"],
            _sha(packet["op1Raw"]),
        )
        self.assertEqual(result["status"], "SEMANTIC_PACKET_VALIDATED_DERIVED_ONLY")
        self.assertEqual(result["evidenceScope"], "PURE_SEMANTIC_AND_DERIVED_DEMAND_ONLY")
        self.assertEqual(result["originalGroupReconciliation"]["ownerGroupCount"], 2)
        self.assertEqual(result["originalGroupReconciliation"]["padCount"], 246)
        self.assertEqual(
            result["originalGroupReconciliation"]["wrapperGroupsBeforeExport"],
            packet["resultDoc"]["work"]["wrapperGroupsBeforeExport"],
        )

        result_chunks = (len(packet["resultRaw"]) + 4095) // 4096
        export_entries = 2 * len(packet["resultRaw"]) + 32 + len(packet["markerRaw"])
        export_chunks = 2 * result_chunks + 2
        self.assertEqual(
            result["exportCopyReconciliation"]["derivedExportCopiedEntries"],
            export_entries,
        )
        self.assertEqual(
            result["exportCopyReconciliation"]["derivedExportCopyChunks"],
            export_chunks,
        )
        self.assertEqual(
            result["exportCopyReconciliation"]["requiredCumulativeCopiedEntries"],
            packet["resultDoc"]["work"]["copiedEntries"] + export_entries,
        )
        self.assertEqual(
            result["exportCopyReconciliation"]["requiredCumulativeCopyChunks"],
            packet["resultDoc"]["work"]["copyChunks"] + export_chunks,
        )
        work = packet["resultDoc"]["work"]
        self.assertEqual(work["groupsBeforeExport"], 1 + 2 + 1 + 1 + 1)
        self.assertEqual(work["wrapperGroupsBeforeExport"], 6 - 2)
        self.assertEqual(
            work["unitsBeforeExport"],
            sum(
                work[name]
                for name in (
                    "setupUnits",
                    "originalCaptureOwnerUnits",
                    "originalCaptureWrapperUnits",
                    "originalCaptureCallbackUnits",
                    "originalCaptureDiagnosticUnits",
                    "nullOwnerUnits",
                    "nullWrapperUnits",
                    "nullObserverUnits",
                    "observeOwnerUnits",
                    "observeWrapperUnits",
                    "observeObserverUnits",
                    "metadataUnits",
                )
            ),
        )
        self.assertEqual(work["copiedEntries"], 43 + 12)
        self.assertEqual(work["copyChunks"], 2 + 1)
        self.assertGreaterEqual(work["metadataUnits"], packet["treeMinimum"])
        self.assertGreaterEqual(
            work["maximumUnitsPerGroupBeforeExport"], work["metadataUnits"]
        )
        self.assertLessEqual(work["maximumUnitsPerGroupBeforeExport"], 32_768)
        self.assertEqual(result["resultBytes"], len(packet["resultRaw"]))
        self.assertEqual(result["markerBytes"], len(packet["markerRaw"]))
        self.assertLessEqual(result["markerBytes"], 656)

        self.assertIsInstance(result, MappingProxyType)
        self.assertIsInstance(result["work"], MappingProxyType)
        with self.assertRaises(TypeError):
            result["work"]["meterUnits"] = -1

        original_prior_id = result["qualifiedPrior"]["priorRunId"]
        packet["context"]["nativeProjection"]["prior"]["priorRunId"] = (
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        )
        self.assertEqual(result["qualifiedPrior"]["priorRunId"], original_prior_id)

    def test_context_runtime_prior_and_receipt_bindings_reject(self):
        packet = _with_result_mutation(
            lambda doc: doc["qualifiedPrior"].update(operation1Sha256="0" * 64)
        )
        self.reject("BF_QUALIFIED_PRIOR_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["qualifiedPrior"]["trialDisposition"].update(
                feedbackValidated=1
            )
        )
        self.reject("BF_QUALIFIED_PRIOR_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["originalCapture"]["request"].update(requestIndex=False)
        )
        self.reject("BF_ORIGINAL_REQUEST_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["originalCapture"]["ownerSettings"].update(roleWidths=1)
        )
        self.reject("BF_ORIGINAL_OWNER_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["originalCapture"]["template"].update(traceCount=False)
        )
        self.reject("BF_ORIGINAL_TEMPLATE_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["originalCapture"]["netIds"].__setitem__(0, [])
        )
        self.reject("BF_ORIGINAL_OWNER_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["originalCapture"]["receipt"].update(receiptOrdinal=308)
        )
        self.reject("BF_ORIGINAL_RECEIPT_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["originalCapture"]["seeds"].update(
                rootSeed="9007199254740994"
            )
        )
        self.reject("BF_ORIGINAL_SEED_BINDING", packet)

        packet = _build_packet()
        bad_runtime = copy.deepcopy(packet["runtime"])
        bad_runtime["wireContractSha256"] = "0" * 64
        self.reject("BF_RUNTIME_WIRE_IDENTITY", packet, runtime=bad_runtime)

        packet = _build_packet()
        bad_runtime = copy.deepcopy(packet["runtime"])
        bad_runtime["inputContextSha256"] = "0" * 64
        self.reject("BF_RUNTIME_CONTEXT_BINDING", packet, runtime=bad_runtime)

        packet = _build_packet()
        bad_context = copy.deepcopy(packet["context"])
        bad_context["nativeProjection"]["ownerSettings"]["maxVias"] = 47
        self.reject("BF_OWNER_SETTINGS_FIXED", packet, context=bad_context)

        packet = _build_packet()
        bad_context = copy.deepcopy(packet["context"])
        bad_context["physicalContext"]["padLands"].append(
            copy.deepcopy(bad_context["physicalContext"]["padLands"][0])
        )
        self.reject("BF_PAD_LAND_COUNT", packet, context=bad_context)

    def test_cohort_arm_and_current_n_run_mutations_reject(self):
        packet = _with_result_mutation(
            lambda doc: doc["cohort"]["requestedNetIds"].__setitem__(0, "B_COIL_LOW")
        )
        self.reject("BF_COHORT_ORDER_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["observe"].update(
                armId=doc["arms"]["null"]["armId"]
            )
        )
        self.reject("BF_ARM_ID_DUPLICATE", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["observe"].update(armId=[])
        )
        self.reject("BF_ARM_IDENTITY", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["observe"]["freshRun"]["ordering"][
                "requestedNetIds"
            ].__setitem__(0, "B_COIL_LOW")
        )
        self.reject("BF_FRESH_ORDERING_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["observe"]["freshRun"]["terminalFailure"].update(
                searchReleased=False
            )
        )
        self.reject("BF_TERMINAL_FAILURE_BINDING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["null"]["freshRun"]["outcome"].update(
                expanded=419_904
            )
        )
        self.reject("BF_ARM_PARITY", packet)

    def test_observation_contract_mutations_reject(self):
        packet = _with_result_mutation(
            lambda doc: doc["arms"]["null"]["observation"].update(callbackCount=1)
        )
        self.reject("BF_NULL_OBSERVATION", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["observe"]["observation"].update(observerUnits=103)
        )
        self.reject("BF_OBSERVER_ACCOUNTING", packet)

        packet = _with_result_mutation(
            lambda doc: doc["arms"]["observe"]["observation"]["selectedSnapshot"].update(
                startX=(
                    doc["arms"]["observe"]["observation"]["selectedSnapshot"]["startX"]
                    + 1
                )
            )
        )
        self.reject("BF_SNAPSHOT_PAD_COORDINATES", packet)

    def test_flat_work_prepayment_and_cleanup_equations_reject(self):
        packet = _with_result_mutation(
            lambda doc: doc["work"].update(
                unitsBeforeExport=doc["work"]["unitsBeforeExport"] + 1
            )
        )
        self.reject("BF_WORK_UNIT_EQUATION", packet)

        packet = _with_result_mutation(
            lambda doc: doc["work"].update(
                groupsBeforeExport=doc["work"]["groupsBeforeExport"] + 1
            )
        )
        self.reject("BF_WORK_GROUP_EQUATION", packet)

        packet = _with_result_mutation(
            lambda doc: doc["work"].update(
                copiedEntries=doc["work"]["copiedEntries"] + 1
            )
        )
        self.reject("BF_WORK_COPY_EQUATION", packet)

        packet = _with_result_mutation(
            lambda doc: doc["work"].update(maximumUnitsPerGroupBeforeExport=64)
        )
        self.reject("BF_WORK_MAXIMA", packet)

        packet = _with_result_mutation(
            lambda doc: doc["cleanup"].update(contextRestored=False)
        )
        self.reject("BF_TOP_CLEANUP_BINDING", packet)

    def test_original_operation_parser_and_mandatory_physical_reconciliation_reject(self):
        def source_header(rows):
            header = next(row for row in rows if row.get("record") == "HEADER")
            header["sourceManifestSha256"] = "b" * 64

        packet = _build_packet()
        _rebuild_op1(packet, source_header)
        self.reject("BF_R8_OPERATION1_HEADER_IDENTITY", packet)

        def footer_group_count(rows):
            footer = next(row for row in rows if row.get("record") == "FOOTER")
            footer["groupCount"] += 1

        packet = _build_packet()
        _rebuild_op1(packet, footer_group_count)
        self.reject("BF_R8_OPERATION1_FOOTER_CENSUS", packet)

        def extra_component(rows):
            pad = next(
                row for row in rows
                if row.get("record") == "PAD" and row.get("padId") == "PAD_000"
            )
            pad["componentId"] = "COMP_XXX"

        packet = _build_packet()
        _rebuild_op1(packet, extra_component)
        self.reject("BF_R8_OPERATION1_PAD_CENSUS", packet)

        def missing_net_in_header_census(rows):
            header = next(row for row in rows if row.get("record") == "HEADER")
            header["candidate"]["netCount"] = 59

        packet = _build_packet()
        _rebuild_op1(packet, missing_net_in_header_census)
        self.reject("BF_R8_OPERATION1_PAD_CENSUS", packet)

        def wrong_branch_grid(rows):
            branch = next(row for row in rows if row.get("record") == "BRANCH")
            branch["snapshot"]["gridWidth"] = 99

        packet = _build_packet()
        _rebuild_op1(packet, wrong_branch_grid)
        self.reject("BF_R8_GRID_BINDING", packet)

        def physical_pad_drift(rows):
            pad = next(
                row for row in rows
                if row.get("record") == "PAD" and row.get("padId") == "PAD_000"
            )
            pad["x"] = 1

        packet = _build_packet()
        _rebuild_op1(packet, physical_pad_drift)
        self.reject("BF_R8_PAD_PHYSICAL_IDENTITY_MISMATCH", packet)

        packet = _build_packet()
        wrong_receipt_raw = packet["op1Raw"][:-2] + b"X\n"
        self.reject("BF_ORIGINAL_RECEIPT_BINDING", packet, op1_raw=wrong_receipt_raw)

    def test_result_marker_key_framing_hash_and_size_bounds_reject(self):
        packet = _with_result_mutation(
            lambda doc: doc.__setitem__("sourceManifestSha256", "c" * 64)
        )
        self.reject("BF_RESULT_IDENTITY", packet)

        packet = _build_packet()
        reversed_doc = dict(reversed(tuple(packet["resultDoc"].items())))
        reversed_result_raw = _canonical_line(reversed_doc)
        marker, marker_raw = _marker_for(reversed_doc, reversed_result_raw)
        packet["resultDoc"] = reversed_doc
        packet["resultRaw"] = reversed_result_raw
        packet["markerDoc"] = marker
        packet["markerRaw"] = marker_raw
        self.reject("BF_RESULT_SCHEMA_ORDER", packet)

        packet = _with_marker_mutation(
            lambda marker: marker.update(resultSha256="0" * 64)
        )
        self.reject("BF_MARKER_BINDING", packet)

        packet = _build_packet()
        reordered_marker = dict(reversed(tuple(packet["markerDoc"].items())))
        self.reject(
            "BF_MARKER_SCHEMA_ORDER",
            packet,
            marker_raw=_canonical_line(reordered_marker),
        )

        packet = _build_packet()
        oversized_marker = b" " * (657 - len(packet["markerRaw"])) + packet["markerRaw"]
        self.reject("BF_MARKER_SIZE_BEFORE_PARSE", packet, marker_raw=oversized_marker)

        packet = _build_packet()
        oversized_result = b" " * (1_048_577) + b"\n"
        self.reject("BF_RESULT_JSON", packet, result_raw=oversized_result)


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *_REMAINING])
