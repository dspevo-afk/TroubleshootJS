"""Source-authored synthetic P07 AUTHORED operation-1 receipt fixture.

This fixture writes a small structurally complete JSONL operation for the
pinned pure parser. It does not model a native capture or authenticate the
fixed physical PAD identity token.
"""

from __future__ import annotations

import hashlib
import json
import re


SCHEMA = "p07-actual-branch-order-study-v6"
LIMITS = {
    "operationNanos": 90_000_000_000,
    "groups": 640,
    "groupNanos": 5_000_000_000,
    "p07Steps": 32_768,
    "receiptBytes": 8 * 1024 * 1024,
    "lineBytes": 65_536,
    "padRows": 1_024,
    "prefixBytes": 1 * 1024 * 1024,
    "viaCost": 60,
    "holeCap": 48,
    "transitionCap": 4,
    "orderings": 3,
    "requestExpansions": 1_000_000,
    "branchExpansions": 100_000,
    "cells": 250_000,
    "pathNodes": 4_096,
    "snapshotEntries": 1_048_576,
    "publisherPayloadBytes": 2 * 1024 * 1024,
    "publisherChunkBytes": 4_096,
}
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
DEFAULT_PRISTINE = b"synthetic-authored-p07-pristine\n"
_SHA = re.compile(r"[0-9a-f]{64}\Z")
_UUID = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z"
)
_TOKEN = re.compile(r"[-+A-Za-z0-9_.:]{1,128}\Z")
_PHYSICAL_KEYS = ("gridWidth", "gridHeight", "padLands", "qualifiedP07Pads")
_LAND_KEYS = ("padId", "netId", "x", "y", "bounds", "attachment", "layer")
_PAD_KEYS = ("record", "group", "padId", "netId", "componentId", "x", "y", "role")
_GROUP_KEYS = (
    "record", "operation", "ordinal", "phase", "elapsedBeforeOwnLineNanos",
    "generatorAdvances", "p07Slices", "p07Steps", "diagnosticCloneSteps",
    "accounting", "observerChecks", "callbacks", "censusRows", "netOrderRows",
    "hashInputBytes", "serializedBeforeOwnLineBytes", "publisherChunkRows",
    "publisherResultRows", "diagnosticCopiedEntries", "graphChunkRows",
    "graphResultRows", "graphCursorSteps", "diagnosticSerializedBytes",
    "diagnosticHashBytes", "diagnosticUnits",
)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _json_line(value) -> bytes:
    text = json.dumps(value, ensure_ascii=True, sort_keys=True,
                       separators=(",", ":"), allow_nan=False)
    return text.encode("ascii") + b"\n"


def _pad_census_bytes(pads) -> bytes:
    return "".join(
        "\t".join(str(row[key]) for key in
                  ("padId", "netId", "componentId", "x", "y", "role")) + "\n"
        for row in pads
    ).encode("utf-8")


def _default_net_ids():
    return (
        ["A_COIL_LOW", "B_COIL_LOW", "D_SENSE"]
        + ["SYN_NET_%02d" % index for index in range(39)]
        + ["D_COIL_LOW"]
        + ["SYN_NET_%02d" % index for index in range(39, 56)]
    )


def make_physical_context(*, grid_width: int = 100, grid_height: int = 100,
                          net_ids=None):
    """Return reusable schema-compatible 246-row physical context fixtures."""
    if (type(grid_width) is not int or type(grid_height) is not int
            or grid_width < 3 or grid_height < 3
            or grid_width * grid_height > 250_000):
        raise ValueError("SYNTHETIC_GRID")
    ids = list(_default_net_ids() if net_ids is None else net_ids)
    if (len(ids) != 60 or len(set(ids)) != 60
            or not all(type(value) is str and _TOKEN.fullmatch(value) for value in ids)):
        raise ValueError("SYNTHETIC_NET_IDS")
    if grid_width < 100 or grid_height < 3:
        raise ValueError("SYNTHETIC_PAD_GRID")

    lands, pads = [], []
    for index in range(246):
        pad_id = "PAD_%03d" % index
        net_id = ids[index % len(ids)]
        component_id = "COMP_%03d" % (index % 100)
        x, y = index % 100, index // 100
        lands.append({
            "padId": pad_id,
            "netId": net_id,
            "x": x,
            "y": y,
            "bounds": [x - 1, y - 1, 2, 2],
            "attachment": "PLATED_THROUGH_HOLE",
            "layer": "TOP+BOTTOM",
        })
        pads.append({
            "record": "PAD",
            "group": 0,
            "padId": pad_id,
            "netId": net_id,
            "componentId": component_id,
            "x": x,
            "y": y,
            "role": "SIGNAL",
        })
    return {
        "gridWidth": grid_width,
        "gridHeight": grid_height,
        "padLands": lands,
        "qualifiedP07Pads": pads,
    }


def _validate_physical_context(physical_context):
    if (type(physical_context) is not dict
            or tuple(physical_context) != _PHYSICAL_KEYS):
        raise ValueError("SYNTHETIC_PHYSICAL_KEYS")
    width, height = physical_context["gridWidth"], physical_context["gridHeight"]
    lands, pads = physical_context["padLands"], physical_context["qualifiedP07Pads"]
    if (type(width) is not int or type(height) is not int or width < 1 or height < 1
            or width * height > 250_000 or type(lands) is not list or type(pads) is not list
            or len(lands) != 246 or len(pads) != 246):
        raise ValueError("SYNTHETIC_PHYSICAL_COUNTS")
    by_land, by_pad, role_by_net = {}, {}, {}
    previous_land = previous_pad = None
    for land, pad in zip(lands, pads):
        if type(land) is not dict or tuple(land) != _LAND_KEYS:
            raise ValueError("SYNTHETIC_LAND_KEYS")
        if type(pad) is not dict or tuple(pad) != _PAD_KEYS:
            raise ValueError("SYNTHETIC_PAD_KEYS")
        pad_id = land["padId"]
        if (type(pad_id) is not str or _TOKEN.fullmatch(pad_id) is None
                or pad_id != pad["padId"] or (previous_land is not None and pad_id <= previous_land)
                or (previous_pad is not None and pad_id <= previous_pad)):
            raise ValueError("SYNTHETIC_PAD_ORDER")
        previous_land = previous_pad = pad_id
        if (pad["record"] != "PAD" or pad["group"] != 0
                or land["netId"] != pad["netId"]
                or (land["x"], land["y"]) != (pad["x"], pad["y"])):
            raise ValueError("SYNTHETIC_PAD_LAND_BINDING")
        if (type(pad["netId"]) is not str or _TOKEN.fullmatch(pad["netId"]) is None
                or type(pad["componentId"]) is not str
                or _TOKEN.fullmatch(pad["componentId"]) is None
                or type(pad["role"]) is not str or _TOKEN.fullmatch(pad["role"]) is None
                or type(pad["x"]) is not int or type(pad["y"]) is not int
                or not (0 <= pad["x"] < width and 0 <= pad["y"] < height)):
            raise ValueError("SYNTHETIC_PAD_FIELDS")
        if (type(land["bounds"]) is not list or len(land["bounds"]) != 4
                or land["attachment"] != "PLATED_THROUGH_HOLE"
                or land["layer"] != "TOP+BOTTOM"):
            raise ValueError("SYNTHETIC_LAND_FIELDS")
        x, y, bound_width, bound_height = land["bounds"]
        if (any(type(value) is not int for value in land["bounds"])
                or bound_width <= 0 or bound_height <= 0
                or not (x <= land["x"] <= x + bound_width
                        and y <= land["y"] <= y + bound_height)):
            raise ValueError("SYNTHETIC_LAND_BOUNDS")
        prior_role = role_by_net.setdefault(pad["netId"], pad["role"])
        if prior_role != pad["role"]:
            raise ValueError("SYNTHETIC_NET_ROLE")
        by_land[pad_id] = land
        by_pad[pad_id] = pad
    if len({row["netId"] for row in pads}) != 60:
        raise ValueError("SYNTHETIC_NET_COUNT")
    if len({row["componentId"] for row in pads}) != 100:
        raise ValueError("SYNTHETIC_COMPONENT_COUNT")
    if any(sum(row["netId"] == net for row in pads) < 2 for net in role_by_net):
        raise ValueError("SYNTHETIC_NET_PAD_MINIMUM")
    return width, height, lands, pads


def _nearest_branch_pads(pads, net_id=None):
    by_net = {}
    for row in pads:
        by_net.setdefault(row["netId"], []).append(row)
    choices = [name for name in sorted(by_net)
               if len(by_net[name]) > 1 and (net_id is None or name == net_id)]
    if not choices:
        raise ValueError("SYNTHETIC_BRANCH_NET")
    selected_net = choices[0]
    ordered = sorted(by_net[selected_net], key=lambda row: row["padId"])
    root = ordered[0]
    start = min(
        ordered[1:],
        key=lambda row: (abs(row["x"] - root["x"]) + abs(row["y"] - root["y"]),
                         row["padId"]),
    )
    return selected_net, root, start


def _group(*, ordinal: int, phase: str, serialized_bytes: int,
           hash_input_bytes: int, callbacks: int, census_rows: int):
    advance = phase == "ADVANCE"
    return {
        "record": "GROUP",
        "operation": 1,
        "ordinal": ordinal,
        "phase": phase,
        "elapsedBeforeOwnLineNanos": 100,
        "generatorAdvances": 1 if advance else 0,
        "p07Slices": 1 if advance else 0,
        "p07Steps": 7 if advance else None,
        "diagnosticCloneSteps": 0,
        "accounting": "MEASURED" if advance else "NOT_APPLICABLE",
        "observerChecks": 1 if advance else 0,
        "callbacks": callbacks,
        "censusRows": census_rows,
        "netOrderRows": 0,
        "hashInputBytes": hash_input_bytes,
        "serializedBeforeOwnLineBytes": serialized_bytes,
        "publisherChunkRows": 0,
        "publisherResultRows": 0,
        "diagnosticCopiedEntries": 0,
        "graphChunkRows": 0,
        "graphResultRows": 0,
        "graphCursorSteps": 0,
        "diagnosticSerializedBytes": 0,
        "diagnosticHashBytes": 0,
        "diagnosticUnits": 0,
    }


def build_authored_operation1(*, run_id: str, source_sha256: str,
                              wire_sha256: str, physical_context=None,
                              branch_net_id=None, pristine_bytes: bytes = DEFAULT_PRISTINE):
    """Build JSONL and independent fixture expectations for real P07 parsing.

    ``physical_context`` may be reused verbatim by a later full-V2 synthetic
    packet. This writer binds only its 246 P07 PAD rows; it does not claim the
    fixed sourceIdentity pad-land digest is computed from these synthetic rows.
    """
    if (type(run_id) is not str or _UUID.fullmatch(run_id) is None
            or type(source_sha256) is not str or _SHA.fullmatch(source_sha256) is None
            or type(wire_sha256) is not str or _SHA.fullmatch(wire_sha256) is None
            or type(pristine_bytes) is not bytes or not pristine_bytes
            or len(pristine_bytes) > LIMITS["prefixBytes"]):
        raise ValueError("SYNTHETIC_IDENTITY_OR_PREFIX")
    if physical_context is None:
        physical_context = make_physical_context()
    width, height, lands, pads = _validate_physical_context(physical_context)
    net_ids = {row["netId"] for row in pads}
    if net_ids != {row["netId"] for row in lands}:
        raise ValueError("SYNTHETIC_NET_LAND_CENSUS")

    net_id, root, start = _nearest_branch_pads(pads, branch_net_id)
    pristine_sha = _sha(pristine_bytes)
    pad_bytes = _pad_census_bytes(pads)
    pad_sha = _sha(pad_bytes)
    branch_snapshot = {
        "pass": 0,
        "netId": net_id,
        "startPadId": start["padId"],
        "rootPadId": root["padId"],
        "startX": start["x"],
        "startY": start["y"],
        "rootX": root["x"],
        "rootY": root["y"],
        "gridWidth": width,
        "gridHeight": height,
        "goalsTop": 1,
        "goalsBottom": 1,
        "prefixViaCount": 0,
        "reachedPadCount": 1,
        "initialManhattanDistance": abs(start["x"] - root["x"])
        + abs(start["y"] - root["y"]),
        "minimumExploredManhattanDistance": 0,
        "branchExpanded": 1,
        "totalExpanded": 1,
        "expandedTop": 1,
        "expandedBottom": 0,
        "frontierCount": 1,
        "bestStateCount": 1,
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
        "terminalReason": "FOUND",
    }

    header = {
        "record": "HEADER",
        "group": 0,
        "schema": SCHEMA,
        "runId": run_id,
        "mode": "AUTHORED",
        "operation": 1,
        "arm": {
            "id": "OBSERVED_DEFAULT_NEAREST",
            "api": "BOUNDARY_CONVENIENCE",
            "strategy": "CANONICAL_NEAREST",
            "netOrderStrategy": "CANONICAL",
            "observed": True,
            "publisherCapture": False,
            "graphCapture": False,
        },
        "sourceManifestSha256": source_sha256,
        "wireSha256": wire_sha256,
        "candidate": {
            "rootSeed": ROOT_SEED,
            "placementSeed": PLACEMENT_SEED,
            "routingSeed": ROUTING_SEED,
            "variant": "SEPARATE",
            "layoutVersion": 26,
            "dependencyEpoch": "tsj-generation-dependencies-v28",
            "componentCount": 100,
            "padCount": 246,
            "netCount": 60,
            "traceWidthProfile": "ROLE_WIDTHS",
        },
        "limits": dict(LIMITS),
    }
    branch = {
        "record": "BRANCH",
        "operation": 1,
        "ordinal": 0,
        "group": 0,
        "prefixSha256": pristine_sha,
        "prefixBytes": len(pristine_bytes),
        "snapshot": branch_snapshot,
    }
    receipt = {
        "attempt": 4,
        "startedOrderings": 1,
        "completedOrderings": 1,
        "yieldedOrderings": 1,
        "finalizedOrderings": 1,
        "selectedOrdering": 0,
        "expansions": 1,
        "guidanceStarted": False,
        "comparisonComplete": False,
        "termination": "FIRST_SUCCESS_UNGUIDED",
    }
    context = {
        "requestIndex": 0,
        "selectedAttempt": 4,
        "eligibleAttempts": [4],
        "pristineSha256": pristine_sha,
        "pristineBytes": len(pristine_bytes),
        "padCensusSha256": pad_sha,
        "boardSha256": _sha(b"synthetic-authored-p07-board\n"),
    }
    result = {
        "record": "RESULT",
        "operation": 1,
        "group": 1,
        "proof": "COMPLETE",
        "route": "REQUEST_FINALIZED",
        "context": context,
        "receipt": receipt,
        "outcome": "SYNTHETIC_ROUTE_FINALIZED",
        "winner": {
            "geometrySha256": _sha(b"synthetic-authored-p07-geometry\n"),
            "geometryBytes": 1,
            "attempt": 4,
            "ordering": 0,
            "quality": "SYNTHETIC_QUALITY",
        },
        "physical": {
            "connectivity": "PASS",
            "clearanceKeepout": "PASS",
            "layeredRaster": "PASS",
            "access": "NOT_RUN",
            "functional": "NOT_RUN",
        },
        "logicalWork": {
            "generatorAdvances": 1,
            "p07Slices": 1,
            "p07OwnerSteps": 7,
            "diagnosticCloneSteps": 0,
            "routingOwnerSteps": 7,
            "expansions": 1,
        },
        "boundary": {
            "requestCount": 1,
            "outcomeCount": 1,
            "routeRetired": True,
            "candidateRetired": True,
            "generatorComplete": False,
            "eventObserved": True,
            "successorAdvanceCalls": 0,
        },
    }

    lines = [_json_line(header)]
    lines.extend(_json_line(row) for row in pads)
    lines.append(_json_line(branch))
    group0_bytes = sum(map(len, lines))
    # Match the parser's minimum hash-input census, including the one-byte
    # synthetic finalized geometry referenced by the result.
    hash_input_bytes = len(pristine_bytes) * 2 + len(pad_bytes) + 1
    groups = [
        _group(ordinal=0, phase="ADVANCE", serialized_bytes=group0_bytes,
               hash_input_bytes=hash_input_bytes, callbacks=1, census_rows=246),
    ]
    lines.append(_json_line(groups[0]))
    result_line = _json_line(result)
    lines.append(result_line)
    groups.append(_group(ordinal=1, phase="REPORT", serialized_bytes=len(result_line),
                         hash_input_bytes=0, callbacks=0, census_rows=0))
    lines.append(_json_line(groups[1]))
    lines.append(_json_line({
        "record": "FOOTER",
        "group": 1,
        "operation": 1,
        "complete": True,
        "padCount": 246,
        "branchCount": 1,
        "groupCount": 2,
    }))
    raw = b"".join(lines)
    if len(raw) > LIMITS["receiptBytes"] or any(
            len(line) > LIMITS["lineBytes"] for line in lines):
        raise ValueError("SYNTHETIC_RECEIPT_BOUNDS")

    return {
        "raw": raw,
        "pads": [dict(row) for row in pads],
        "padLands": [dict(row) for row in lands],
        "physicalContext": {
            "gridWidth": width,
            "gridHeight": height,
            "padLands": [dict(row) for row in lands],
            "qualifiedP07Pads": [dict(row) for row in pads],
        },
        "entry": {"elapsedToCloseNanos": 1_000, "maxFullGroupNanos": 1_000},
        "runId": run_id,
        "sourceManifestSha256": source_sha256,
        "wireSha256": wire_sha256,
        "expected": {
            "padCount": 246,
            "netCount": 60,
            "componentCount": 100,
            "groupCount": 2,
            "branchCount": 1,
            "p07OwnerSteps": 7,
            "padCensusSha256": pad_sha,
            "branchNetId": net_id,
            "rootPadId": root["padId"],
            "nearestPadId": start["padId"],
            "grid": (width, height),
        },
    }
