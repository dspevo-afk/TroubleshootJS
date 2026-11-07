#!/usr/bin/env python3
"""Strict P9 reservation provenance consumer.

The public reader keeps contributor identities and raw slot values private.  Its
offline geometry functions are deliberately independent of the Java reservation
implementation; the live reader delegates the unchanged P8 semantic checks to
their authenticated modules and adds P9's own envelope and sidecar checks.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import math
import os
import re
import stat
import sys
import tempfile
import time
import types
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


SCHEMA = "tsj-q100-reservation-provenance-reader-result-v1"
OPERAND_SCHEMA = "tsj-q100-reservation-operands-v1"
LITERAL_SCHEMA = "tsj-q100-reservation-literal-result-v1"
READER_ERROR_PREFIX = "P9_RESERVATION_READER_REJECT"
LITERAL_ERROR_PREFIX = "P9_PROVENANCE_LITERAL_REJECT"

ROLE_WIDTHS = {
    "HIGH_CURRENT": 13,
    "SUPPLY": 11,
    "RETURN": 11,
    "CONTROL": 9,
    "SIGNAL": 9,
}
MOUNTING_SIDES = {"TOP", "BOTTOM"}
ATTACHMENTS = {"PLATED_THROUGH_HOLE", "SURFACE_PAD"}
LAYERS = {0, 1}
BOARD_COORD_LIMIT = 100_000_000
INT32_MIN = -(2**31)
INT32_MAX = 2**31 - 1
MAX_GRID_CELLS = 700_000
MAX_LINE_BYTES = 65_536
MAX_OPERAND_BYTES = 8 * 1024 * 1024
MAX_PINS_BYTES = 4 * 1024 * 1024
PAD_COUNT = 246

HEADER_BASE_KEYS = (
    "kind", "schema", "runId", "inputSha256", "sourceSha256", "wireSha256",
    "wrapperMode", "p8Mode", "parentPrefixSha256", "parentGraphSha256",
    "queryOrdinal", "parentOrdinal", "captureState",
)
HEADER_OBSERVE_ADD = (
    "identity", "grid", "outline", "minX", "minY", "gridWidth", "gridHeight",
    "selectedLayer", "requestedNetId", "traceWidthProfile", "profileIdentity",
    "emptyComponentFace", "checkProfiledPadTouches", "escapeOutletLand",
    "escapeOutletLandOrigin",
)
IDENTITY_KEYS = (
    "layer", "fromState", "toState", "bit", "selected", "fromGridX", "fromGridY",
    "toGridX", "toGridY", "fromX", "fromY", "toX", "toY", "direction",
    "netId", "startPadId", "rootPadId", "reachedPthPadIds",
)
PAD_KEYS = (
    "kind", "index", "padId", "componentId", "netId", "routingRole", "x", "y",
    "mountingSide", "attachment", "escapeDx", "escapeDy", "escapeLength",
    "padBounds", "ownerCourtyard", "preparedEscapeCorridor", "padTraceWidth",
)
RECT_KEYS = ("x", "y", "width", "height")
FOOTER_OBSERVE_KEYS = (
    "kind", "complete", "runId", "wrapperMode", "p8Mode", "recordCount", "padCount",
    "requestedNetId", "activeNetId", "activeTraceWidth", "slotValue",
)
FOOTER_NULL_KEYS = (
    "kind", "complete", "runId", "wrapperMode", "p8Mode", "recordCount", "captureState",
)
TAP_KEYS = (
    "attempted", "caught", "sameThrowable", "exceptionClass", "message", "count",
    "dtoPadCount", "dtoDiscarded", "slotDiscarded", "queryContinued",
)
TAP_VALUES = {
    "attempted": True,
    "caught": True,
    "sameThrowable": True,
    "exceptionClass": "java.lang.IllegalStateException",
    "message": "FIRST_RESERVATION_OPERANDS_CALLBACK",
    "count": 1,
    "dtoPadCount": PAD_COUNT,
    "dtoDiscarded": True,
    "slotDiscarded": True,
    "queryContinued": True,
}

READER_RESULT_KEYS = (
    "schema", "status", "layoutQualification", "scope", "p9InputSha256",
    "p9SourceSha256", "p8WireSha256", "runs", "target", "slotComparison",
    "contributorNetCount", "contributorPadCount", "contributorSetSha256",
    "p8Parity", "sourceClosure", "processes", "outputClosure", "limits",
)
TARGET_KEYS = (
    "queryOrdinal", "parentOrdinal", "layer", "bit", "fromGridX", "fromGridY",
    "toGridX", "toGridY", "slotAxis", "slotGridX", "slotGridY",
)
TARGET_VALUE = {
    "queryOrdinal": 10,
    "parentOrdinal": 11,
    "layer": 1,
    "bit": 8,
    "fromGridX": 382,
    "fromGridY": 238,
    "toGridX": 381,
    "toGridY": 238,
    "slotAxis": "HORIZONTAL",
    "slotGridX": 381,
    "slotGridY": 238,
}
SLOT_COMPARISON_KEYS = (
    "expectedState", "capturedState", "match", "expectedValueSha256", "capturedValueSha256",
)
P8_PARITY_KEYS = (
    "p4p5p6NullObserve", "p4p5p6NullTap", "p8NullObserve", "p8NullTap",
)
RUN_KEYS = ("wrapperMode", "p8Mode", "runId", "disposition", "sidecar", "index")
RUN_ORDER = (
    ("NULL", "NULL", "COMPLETE_NULL_CONTROL_ONLY"),
    ("OBSERVE", "OBSERVE", "COMPLETE_RESERVATION_OBSERVATION_ONLY"),
    ("TAP_THROW", "OBSERVE", "EXPECTED_CONTAINED_TAP_THROW_ONLY"),
    ("CALLBACK_THROW", "CALLBACK_THROW", "EXPECTED_CALLBACK_THROW_ONLY"),
    ("REASON_THROW", "REASON_THROW", "EXPECTED_REASON_THROW_ONLY"),
)
P8_DATA_FILES = (
    "op-1.jsonl",
    "fixed-prefix.jsonl",
    "prefix-blockers.json",
    "prefix-stages.json",
    "canonical-stage-index.json",
    "prefix-geometric-reasons.jsonl",
    "canonical-geometric-prefix-index.json",
)
NORMAL_MODES = ("NULL", "OBSERVE", "TAP_THROW")


class ReaderError(ValueError):
    """A deterministic, contract-level rejection with a stable public code."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _reject(code: str) -> None:
    raise ReaderError(code)


def _keys(value: Any, expected: Sequence[str], code: str) -> dict[str, Any]:
    if type(value) is not dict or set(value) != set(expected):
        _reject(code)
    return value


def _exact_int(value: Any, code: str) -> int:
    if type(value) is not int:
        _reject("P9_INTEGER_TYPE")
    return value


def _bool(value: Any, code: str) -> bool:
    if type(value) is not bool:
        _reject(code)
    return value


def _nonempty_text(value: Any, code: str) -> str:
    if type(value) is not str or not value:
        _reject(code)
    return value


def _reject_constant(_value: str) -> None:
    raise ValueError("non-finite JSON number")


def _pairs_no_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def _canonical_json(value: Any) -> bytes:
    try:
        encoded = json.dumps(
            value, ensure_ascii=True, sort_keys=True, separators=(",", ":"), allow_nan=False
        ).encode("ascii")
    except (TypeError, ValueError, UnicodeEncodeError):
        _reject("P9_OPERAND_JSON")
    return encoded


def _strict_json(raw: bytes, code: str) -> Any:
    try:
        if raw.startswith(b"\xef\xbb\xbf") or b"\r" in raw:
            _reject(code)
        value = json.loads(
            raw.decode("ascii"),
            object_pairs_hook=_pairs_no_duplicates,
            parse_constant=_reject_constant,
        )
        return value
    except ReaderError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, TypeError):
        _reject(code)


def _strict_object_line(raw: bytes) -> dict[str, Any]:
    value = _strict_json(raw, "P9_OPERAND_JSON")
    if type(value) is not dict or _canonical_json(value) != raw:
        _reject("P9_OPERAND_JSON")
    return value


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _domain_digest(tag: str, value: Any) -> str:
    return _sha256(tag.encode("ascii") + b"\x00" + _canonical_json(value))


def nullable_state(value: Any) -> str:
    if value is None:
        return "NO_RESERVATION"
    if type(value) is not str:
        _reject("P9_SLOT_VALUE")
    if value == "":
        return "MIXED"
    return "SINGLE_NET"


def merge_reservation(existing: str | None, net_id: str) -> str:
    """Independent nullable slot merge used by literal fixtures."""
    if type(net_id) is not str or not net_id:
        _reject("P9_CONTRIBUTOR_SET")
    if existing is None:
        return net_id
    if existing == "":
        return ""
    if type(existing) is not str:
        _reject("P9_SLOT_VALUE")
    return existing if existing == net_id else ""


def compact_slot_value_digest(value: Any) -> str:
    nullable_state(value)
    return _domain_digest("tsj-p9-nullable-v1", value)


def compact_contributor_set_digest(net_ids: Iterable[str]) -> str:
    values = sorted(set(net_ids))
    if any(type(value) is not str or not value for value in values):
        _reject("P9_CONTRIBUTOR_SET")
    return _domain_digest("tsj-p9-contributors-v1", values)


def _trunc_div(numerator: int, denominator: int) -> int:
    """Java integer division for a positive divisor, without float rounding."""
    if denominator <= 0:
        raise ValueError("denominator must be positive")
    return (abs(numerator) // denominator) * (1 if numerator >= 0 else -1)


def _bounded_board_int(value: Any, code: str) -> int:
    number = _exact_int(value, code)
    if abs(number) > BOARD_COORD_LIMIT:
        _reject(code)
    return number


def _safe_i32(value: int, code: str) -> int:
    if type(value) is not int or not INT32_MIN <= value <= INT32_MAX:
        _reject(code)
    return value


def _rect(value: Any, *, positive: bool, code: str) -> tuple[int, int, int, int]:
    obj = _keys(value, RECT_KEYS, code)
    x = _bounded_board_int(obj["x"], code)
    y = _bounded_board_int(obj["y"], code)
    width = _exact_int(obj["width"], code)
    height = _exact_int(obj["height"], code)
    if width < (1 if positive else 0) or height < (1 if positive else 0):
        _reject(code)
    _bounded_board_int(x + width, code)
    _bounded_board_int(y + height, code)
    return x, y, width, height


def _rect_i32(value: Any, *, positive: bool, code: str) -> tuple[int, int, int, int]:
    obj = _keys(value, RECT_KEYS, code)
    x = _exact_int(obj["x"], code)
    y = _exact_int(obj["y"], code)
    width = _exact_int(obj["width"], code)
    height = _exact_int(obj["height"], code)
    if width < (1 if positive else 0) or height < (1 if positive else 0):
        _reject(code)
    for number in (x, y, width, height, x + width, y + height):
        _safe_i32(number, code)
    return x, y, width, height


def validate_grid_header(header: Mapping[str, Any]) -> None:
    outline = _rect(header.get("outline"), positive=True, code="P9_GRID_DERIVATION")
    ox, oy, ow, oh = outline
    min_x = _bounded_board_int(header.get("minX"), "P9_GRID_DERIVATION")
    min_y = _bounded_board_int(header.get("minY"), "P9_GRID_DERIVATION")
    width = _exact_int(header.get("gridWidth"), "P9_GRID_DERIVATION")
    height = _exact_int(header.get("gridHeight"), "P9_GRID_DERIVATION")
    grid = _exact_int(header.get("grid"), "P9_GRID_DERIVATION")
    if grid != 10 or min_x != ox + 10 or min_y != oy + 10:
        _reject("P9_GRID_DERIVATION")
    if width != _trunc_div(ow - 20, 10) + 1 or height != _trunc_div(oh - 20, 10) + 1:
        _reject("P9_GRID_DERIVATION")
    if width < 1 or height < 1 or width * height > MAX_GRID_CELLS:
        _reject("P9_GRID_DERIVATION")


def derive_escape_corridor(header: Mapping[str, Any], pad: Mapping[str, Any]) -> tuple[int, int, int, int]:
    x = _bounded_board_int(pad["x"], "P9_PAD_GEOMETRY")
    y = _bounded_board_int(pad["y"], "P9_PAD_GEOMETRY")
    dx = _exact_int(pad["escapeDx"], "P9_PAD_GEOMETRY")
    dy = _exact_int(pad["escapeDy"], "P9_PAD_GEOMETRY")
    length = _exact_int(pad["escapeLength"], "P9_PAD_GEOMETRY")
    if (dx, dy) not in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)) or length < 0:
        _reject("P9_PAD_GEOMETRY")
    if length > 0 and (dx, dy) == (0, 0):
        _reject("P9_PAD_GEOMETRY")
    # The unrounded endpoint is a source board coordinate even when its copper
    # face is inactive and the prepared corridor collapses to a point.
    source_end_x = _bounded_board_int(x + dx * length, "P9_PAD_GEOMETRY")
    source_end_y = _bounded_board_int(y + dy * length, "P9_PAD_GEOMETRY")
    rounded_length = -((-length) // 10) * 10
    side = pad["mountingSide"]
    active_side = "TOP" if header["selectedLayer"] == 0 else "BOTTOM"
    outlet = header["escapeOutletLand"]
    if outlet != 6 or header["escapeOutletLandOrigin"] != "PINNED_FULLER_SOURCE":
        _reject("P9_OUTLET_SOURCE_BINDING")
    if outlet > 0 and length > 0 and side == active_side:
        cx, cy, cw, ch = _rect(pad["ownerCourtyard"], positive=True, code="P9_OWNER_COURTYARD")
        if dx:
            edge = cx + cw if dx > 0 else cx
            distance = (edge - x) * dx
        else:
            edge = cy + ch if dy > 0 else cy
            distance = (edge - y) * dy
        extension = distance + outlet + 1
        rounded_extension = -((-extension) // 10) * 10
        rounded_length = max(rounded_length, rounded_extension)
    if side != active_side:
        corridor = (x, y, 0, 0)
    elif dx:
        end_x = _safe_i32(x + dx * rounded_length, "P9_PAD_GEOMETRY")
        corridor = (min(x, end_x), y, abs(end_x - x), 0)
    elif dy:
        end_y = _safe_i32(y + dy * rounded_length, "P9_PAD_GEOMETRY")
        corridor = (x, min(y, end_y), 0, abs(end_y - y))
    else:
        corridor = (x, y, 0, 0)
    for value in corridor:
        _safe_i32(value, "P9_PAD_GEOMETRY")
    _safe_i32(corridor[0] + corridor[2], "P9_PAD_GEOMETRY")
    _safe_i32(corridor[1] + corridor[3], "P9_PAD_GEOMETRY")
    return corridor


def _expanded_corridor(header: Mapping[str, Any], pad: Mapping[str, Any], active_width: int) -> tuple[int, int, int, int]:
    x, y, width, height = derive_escape_corridor(header, pad)
    pad_width = pad["padTraceWidth"]
    clearance = (active_width + pad_width + 1) // 2 + 6
    margin = clearance - 1
    expanded = (x - margin, y - margin, width + 2 * margin, height + 2 * margin)
    for value in expanded:
        _safe_i32(value, "P9_PAD_GEOMETRY")
    _safe_i32(expanded[0] + expanded[2], "P9_PAD_GEOMETRY")
    _safe_i32(expanded[1] + expanded[3], "P9_PAD_GEOMETRY")
    return expanded


def _strict_rect_intersects(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    if aw <= 0 or ah <= 0 or bw <= 0 or bh <= 0:
        return False
    return not (ax + aw <= bx or bx + bw <= ax or ay + ah <= by or by + bh <= ay)


def _closed_rect_touches(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax <= bx + bw and bx <= ax + aw and ay <= by + bh and by <= ay + ah


def rectangle_candidate_range(rect: tuple[int, int, int, int], min_x: int, min_y: int,
                              grid_width: int, grid_height: int) -> tuple[int, int, int, int]:
    x, y, width, height = rect
    x0 = max(0, _trunc_div(x - min_x, 10) - 1)
    x1 = min(grid_width - 1, _trunc_div(x + width - min_x, 10) + 1)
    y0 = max(0, _trunc_div(y - min_y, 10) - 1)
    y1 = min(grid_height - 1, _trunc_div(y + height - min_y, 10) + 1)
    return x0, x1, y0, y1


def _start_index(delta: int, size: int) -> int:
    q = _trunc_div(delta, 10)
    if delta > 0 and delta % 10:
        q += 1
    return min(size, max(0, q))


def _end_index(delta: int, size: int) -> int:
    q = _trunc_div(delta, 10)
    if delta < 0 and delta % 10:
        q -= 1
    return min(size - 1, max(-1, q))


def touch_candidate_range(bounds: tuple[int, int, int, int], min_x: int, min_y: int,
                          grid_width: int, grid_height: int, active_width: int,
                          horizontal: bool = True) -> tuple[int, int, int, int]:
    x, y, width, height = bounds
    half = active_width // 2
    if horizontal:
        x0 = x - 10 - half - 1
        x1 = x + width + half
        y0 = y - half - 1
        y1 = y + height + half
    else:
        x0 = x - half - 1
        x1 = x + width + half
        y0 = y - 10 - half - 1
        y1 = y + height + half
    return (
        _start_index(x0 - min_x, grid_width),
        _end_index(x1 - min_x, grid_width),
        _start_index(y0 - min_y, grid_height),
        _end_index(y1 - min_y, grid_height),
    )


def _pad_has_copper(pad: Mapping[str, Any], active_layer: int) -> bool:
    if pad["attachment"] == "PLATED_THROUGH_HOLE":
        return True
    return pad["mountingSide"] == ("TOP" if active_layer == 0 else "BOTTOM")


def _validate_pad_row(pad: Any, expected_index: int, header: Mapping[str, Any],
                      component_courtyards: dict[str, tuple[int, int, int, int]],
                      seen_pad_ids: set[str], net_roles: dict[str, tuple[str, int]],
                      p4_pad_map: Mapping[str, Any] | None) -> dict[str, Any]:
    row = _keys(pad, PAD_KEYS, "P9_PAD_FIELDS")
    if row["kind"] != "pad" or _exact_int(row["index"], "P9_PAD_ORDER") != expected_index:
        _reject("P9_PAD_ORDER")
    pad_id = _nonempty_text(row["padId"], "P9_PAD_ID")
    component_id = _nonempty_text(row["componentId"], "P9_PAD_ID")
    net_id = _nonempty_text(row["netId"], "P9_PAD_ID")
    if pad_id in seen_pad_ids:
        _reject("P9_PAD_ORDER")
    seen_pad_ids.add(pad_id)
    role = row["routingRole"]
    if type(role) is not str or role not in ROLE_WIDTHS:
        _reject("P9_PAD_ROLE")
    if (type(row["mountingSide"]) is not str or row["mountingSide"] not in MOUNTING_SIDES
            or type(row["attachment"]) is not str or row["attachment"] not in ATTACHMENTS):
        _reject("P9_PAD_ROLE")
    x = _bounded_board_int(row["x"], "P9_PAD_GEOMETRY")
    y = _bounded_board_int(row["y"], "P9_PAD_GEOMETRY")
    _exact_int(row["escapeDx"], "P9_PAD_GEOMETRY")
    _exact_int(row["escapeDy"], "P9_PAD_GEOMETRY")
    _exact_int(row["escapeLength"], "P9_PAD_GEOMETRY")
    pad_bounds = _rect(row["padBounds"], positive=True, code="P9_PAD_GEOMETRY")
    owner_courtyard = _rect(row["ownerCourtyard"], positive=True, code="P9_OWNER_COURTYARD")
    prepared = _rect_i32(row["preparedEscapeCorridor"], positive=False, code="P9_PAD_GEOMETRY")
    width = _exact_int(row["padTraceWidth"], "P9_PAD_WIDTH")
    if width != ROLE_WIDTHS[role]:
        _reject("P9_PAD_WIDTH")
    role_width = (role, width)
    if net_id in net_roles and net_roles[net_id] != role_width:
        _reject("P9_PAD_WIDTH")
    net_roles[net_id] = role_width
    if component_id in component_courtyards and component_courtyards[component_id] != owner_courtyard:
        _reject("P9_OWNER_COURTYARD")
    component_courtyards[component_id] = owner_courtyard
    if p4_pad_map is not None:
        old = p4_pad_map.get(pad_id)
        if old is None or (old.get("netId"), old.get("componentId"), old.get("x"), old.get("y")) != (
            net_id, component_id, x, y
        ):
            _reject("P9_QUERY_IDENTITY")
        old_role = old.get("routingRole", old.get("role")) if type(old) is dict else None
        if old_role is not None and old_role != role:
            _reject("P9_PAD_ROLE")
    if not _pad_has_copper(row, header["selectedLayer"]):
        _reject("P9_PAD_COPPER")
    if prepared != derive_escape_corridor(header, row):
        _reject("P9_CORRIDOR_PARITY")
    return row


def _validate_identity(header: Mapping[str, Any], expected_identity: Mapping[str, Any] | None) -> None:
    identity = _keys(header["identity"], IDENTITY_KEYS, "P9_QUERY_IDENTITY")
    for key in ("layer", "fromState", "toState", "bit", "fromGridX", "fromGridY",
                "toGridX", "toGridY", "fromX", "fromY", "toX", "toY", "direction"):
        _exact_int(identity[key], "P9_QUERY_IDENTITY")
    if type(identity["selected"]) is not bool:
        _reject("P9_QUERY_IDENTITY")
    if type(identity["reachedPthPadIds"]) is not list or identity["reachedPthPadIds"] != []:
        _reject("P9_QUERY_IDENTITY")
    if (type(identity["direction"]) is not int or identity["direction"] != 3
            or any(type(identity[k]) is not str or not identity[k]
                   for k in ("netId", "startPadId", "rootPadId"))):
        _reject("P9_QUERY_IDENTITY")
    if expected_identity is not None:
        expected = dict(expected_identity)
        if (set(expected) != set(identity)
                or any(type(identity[key]) is not type(expected[key]) or identity[key] != expected[key]
                       for key in identity)):
            _reject("P9_QUERY_IDENTITY")
    if (
        identity["layer"] != 1 or identity["bit"] != 8 or identity["selected"] is not True
        or identity["netId"] != header["requestedNetId"]
        or identity["fromGridX"] != 382 or identity["fromGridY"] != 238
        or identity["toGridX"] != 381 or identity["toGridY"] != 238
        or header["queryOrdinal"] != 10 or header["parentOrdinal"] != 11
        or header["selectedLayer"] != 1
    ):
        _reject("P9_QUERY_IDENTITY")
    for key in ("fromX", "fromY", "toX", "toY"):
        _bounded_board_int(identity[key], "P9_QUERY_IDENTITY")
    if type(identity["netId"]) is not str or not identity["netId"]:
        _reject("P9_QUERY_IDENTITY")
    if type(identity["selected"]) is not bool:
        _reject("P9_QUERY_IDENTITY")
    if identity["fromX"] == identity["toX"] or identity["fromY"] != identity["toY"]:
        _reject("P9_QUERY_IDENTITY")
    if identity["fromX"] <= identity["toX"]:
        _reject("P9_QUERY_IDENTITY")


def validate_observe_operands(raw: bytes, *, run_id: str, input_sha: str,
                              source_sha: str, wire_sha: str,
                              parent_prefix_sha: str, parent_graph_sha: str,
                              expected_identity: Mapping[str, Any] | None = None,
                              p4_pad_map: Mapping[str, Any] | None = None,
                              expected_empty_component_face: bool | None = None) -> dict[str, Any]:
    if len(raw) > MAX_OPERAND_BYTES or not raw.endswith(b"\n") or b"\r" in raw:
        _reject("P9_OPERAND_FRAMING")
    lines = raw[:-1].split(b"\n")
    if len(lines) != PAD_COUNT + 2:
        _reject("P9_PAD_COUNT")
    if any(not line or len(line) + 1 > MAX_LINE_BYTES for line in lines):
        _reject("P9_OPERAND_FRAMING")
    rows = [_strict_object_line(line) for line in lines]
    header = rows[0]
    _keys(header, HEADER_BASE_KEYS + HEADER_OBSERVE_ADD, "P9_HEADER_FIELDS")
    expected_head = {
        "kind": "header", "schema": OPERAND_SCHEMA, "runId": run_id,
        "inputSha256": input_sha, "sourceSha256": source_sha, "wireSha256": wire_sha,
        "wrapperMode": "OBSERVE", "p8Mode": "OBSERVE",
        "parentPrefixSha256": parent_prefix_sha, "parentGraphSha256": parent_graph_sha,
        "queryOrdinal": 10, "parentOrdinal": 11, "captureState": "CAPTURED",
        "grid": 10, "traceWidthProfile": "ROLE_WIDTHS", "profileIdentity": "ROLE_TRACE@1",
        "escapeOutletLand": 6, "escapeOutletLandOrigin": "PINNED_FULLER_SOURCE",
    }
    for key, value in expected_head.items():
        if type(value) is int:
            _exact_int(header.get(key), "P9_INTEGER_TYPE")
        if header.get(key) != value or (type(value) is bool and type(header.get(key)) is not bool):
            if key in ("escapeOutletLand", "escapeOutletLandOrigin"):
                _reject("P9_OUTLET_SOURCE_BINDING")
            _reject("P9_QUERY_IDENTITY" if key in ("queryOrdinal", "parentOrdinal") else "P9_HEADER_FIELDS")
    _exact_int(header["selectedLayer"], "P9_QUERY_IDENTITY")
    if type(header["emptyComponentFace"]) is not bool or type(header["checkProfiledPadTouches"]) is not bool:
        _reject("P9_INTEGER_TYPE")
    if expected_empty_component_face is not None:
        if type(expected_empty_component_face) is not bool or header["emptyComponentFace"] is not expected_empty_component_face:
            _reject("P9_QUERY_IDENTITY")
    validate_grid_header(header)
    _validate_identity(header, expected_identity)

    p4_by_id = p4_pad_map
    component_courtyards: dict[str, tuple[int, int, int, int]] = {}
    seen_pad_ids: set[str] = set()
    net_roles: dict[str, tuple[str, int]] = {}
    pads = [
        _validate_pad_row(rows[i + 1], i, header, component_courtyards, seen_pad_ids, net_roles, p4_by_id)
        for i in range(PAD_COUNT)
    ]
    if p4_by_id is not None and set(seen_pad_ids) != set(p4_by_id):
        _reject("P9_PAD_COUNT")
    if header["checkProfiledPadTouches"] is not True:
        _reject("P9_QUERY_IDENTITY")
    active_role = net_roles.get(header["requestedNetId"])
    if active_role is None:
        # The selected net's role-width must be represented in the captured
        # Router cache; an absent cache cannot justify its reported trace width.
        _reject("P9_PAD_WIDTH")

    footer = rows[-1]
    _keys(footer, FOOTER_OBSERVE_KEYS, "P9_FOOTER_FIELDS")
    _exact_int(footer["recordCount"], "P9_INTEGER_TYPE")
    _exact_int(footer["padCount"], "P9_INTEGER_TYPE")
    _bool(footer["complete"], "P9_FOOTER_FIELDS")
    if (
        footer["kind"] != "footer" or footer["complete"] is not True
        or footer["runId"] != run_id or footer["wrapperMode"] != "OBSERVE"
        or footer["p8Mode"] != "OBSERVE" or footer["recordCount"] != PAD_COUNT + 2
        or footer["padCount"] != PAD_COUNT
        or footer["requestedNetId"] != header["requestedNetId"]
        or footer["activeNetId"] != header["requestedNetId"]
    ):
        _reject("P9_FOOTER_FIELDS")
    active_width = _exact_int(footer["activeTraceWidth"], "P9_PAD_WIDTH")
    if active_width != ROLE_WIDTHS.get(active_role[0]):
        _reject("P9_PAD_WIDTH")
    slot_value = footer["slotValue"]
    if slot_value is not None and type(slot_value) is not str:
        _reject("P9_SLOT_VALUE")
    if type(slot_value) is str and slot_value != "" and slot_value not in {p["netId"] for p in pads}:
        _reject("P9_SLOT_VALUE")
    return {"header": header, "pads": pads, "footer": footer, "slotValue": slot_value}


def validate_control_operands(raw: bytes, *, mode: str, run_id: str, input_sha: str,
                              source_sha: str, wire_sha: str,
                              parent_prefix_sha: str, parent_graph_sha: str) -> dict[str, Any]:
    if mode not in ("NULL", "TAP_THROW"):
        _reject("P9_OUTPUT_CENSUS")
    if len(raw) > MAX_OPERAND_BYTES or not raw.endswith(b"\n") or b"\r" in raw:
        _reject("P9_OPERAND_FRAMING")
    lines = raw[:-1].split(b"\n")
    if len(lines) != 2:
        _reject("P9_OPERAND_FRAMING")
    if any(not line or len(line) + 1 > MAX_LINE_BYTES for line in lines):
        _reject("P9_OPERAND_FRAMING")
    header, footer = (_strict_object_line(line) for line in lines)
    _keys(header, HEADER_BASE_KEYS, "P9_HEADER_FIELDS")
    _keys(footer, FOOTER_NULL_KEYS if mode == "NULL" else FOOTER_NULL_KEYS + ("tapThrow",), "P9_FOOTER_FIELDS")
    state = "NOT_REGISTERED" if mode == "NULL" else "DTO_DISCARDED"
    p8_mode = "NULL" if mode == "NULL" else "OBSERVE"
    exact = {
        "kind": "header", "schema": OPERAND_SCHEMA, "runId": run_id,
        "inputSha256": input_sha, "sourceSha256": source_sha, "wireSha256": wire_sha,
        "wrapperMode": mode, "p8Mode": p8_mode,
        "parentPrefixSha256": parent_prefix_sha, "parentGraphSha256": parent_graph_sha,
        "queryOrdinal": 10, "parentOrdinal": 11, "captureState": state,
    }
    if any(header.get(k) != v for k, v in exact.items()):
        _reject("P9_HEADER_FIELDS")
    expected_footer = {
        "kind": "footer", "complete": True, "runId": run_id,
        "wrapperMode": mode, "p8Mode": p8_mode, "recordCount": 2,
        "captureState": state,
    }
    if any(footer.get(k) != v for k, v in expected_footer.items()):
        _reject("P9_FOOTER_FIELDS")
    if type(footer["complete"]) is not bool:
        _reject("P9_FOOTER_FIELDS")
    _exact_int(footer["recordCount"], "P9_INTEGER_TYPE")
    if mode == "TAP_THROW":
        tap = _keys(footer["tapThrow"], TAP_KEYS, "P9_TAP_THROW_PROOF")
        for key in ("attempted", "caught", "sameThrowable", "dtoDiscarded", "slotDiscarded", "queryContinued"):
            if type(tap[key]) is not bool:
                _reject("P9_TAP_THROW_PROOF")
        for key in ("count", "dtoPadCount"):
            if type(tap[key]) is not int:
                _reject("P9_TAP_THROW_PROOF")
        for key in ("exceptionClass", "message"):
            if type(tap[key]) is not str:
                _reject("P9_TAP_THROW_PROOF")
        if tap != TAP_VALUES:
            _reject("P9_TAP_THROW_PROOF")
    return {"header": header, "footer": footer}


def _candidate_grid_bounds(rect: tuple[int, int, int, int], header: Mapping[str, Any],
                           target_x: int, target_y: int) -> bool:
    x0, x1, y0, y1 = rectangle_candidate_range(
        rect, header["minX"], header["minY"], header["gridWidth"], header["gridHeight"]
    )
    return x0 <= target_x <= x1 and y0 <= target_y <= y1


def _touch_grid_candidate(bounds: tuple[int, int, int, int], header: Mapping[str, Any],
                          target_x: int, target_y: int, active_width: int) -> bool:
    x0, x1, y0, y1 = touch_candidate_range(
        bounds, header["minX"], header["minY"], header["gridWidth"], header["gridHeight"],
        active_width, horizontal=True,
    )
    return x0 <= target_x <= x1 and y0 <= target_y <= y1


def analyze_slot(operands: Mapping[str, Any]) -> dict[str, Any]:
    header = operands["header"]
    pads = operands["pads"]
    footer = operands["footer"]
    active_width = footer["activeTraceWidth"]
    identity = header["identity"]
    slot_x = identity["toGridX"]
    slot_y = identity["toGridY"]
    edge = (
        header["minX"] + slot_x * 10,
        header["minY"] + slot_y * 10,
        10,
        1,
    )
    hit_indices: set[int] = set()
    contributors: set[str] = set()
    for pad in pads:
        if not _pad_has_copper(pad, header["selectedLayer"]):
            continue
        rectangle = _expanded_corridor(header, pad, active_width)
        rectangle_hit = (
            _candidate_grid_bounds(rectangle, header, slot_x, slot_y)
            and _strict_rect_intersects(rectangle, edge)
        )
        touch_hit = False
        if header["checkProfiledPadTouches"]:
            bounds = _rect(pad["padBounds"], positive=True, code="P9_PAD_GEOMETRY")
            if _touch_grid_candidate(bounds, header, slot_x, slot_y, active_width):
                half = active_width // 2
                stroke = (edge[0] - half, edge[1] - half, edge[2] + active_width, active_width)
                touch_hit = _closed_rect_touches(stroke, bounds)
        if rectangle_hit or touch_hit:
            hit_indices.add(pad["index"])
            contributors.add(pad["netId"])
    if not contributors:
        expected = None
    elif len(contributors) == 1:
        expected = next(iter(contributors))
    else:
        expected = ""
    return {
        "expectedValue": expected,
        "expectedState": nullable_state(expected),
        "capturedValue": operands["slotValue"],
        "capturedState": nullable_state(operands["slotValue"]),
        "match": expected == operands["slotValue"],
        "contributorNetCount": len(contributors),
        "contributorPadCount": len(hit_indices),
        "contributorSetSha256": compact_contributor_set_digest(contributors),
        "expectedValueSha256": compact_slot_value_digest(expected),
        "capturedValueSha256": compact_slot_value_digest(operands["slotValue"]),
        # Private-only values. The public result builder intentionally omits them.
        "_contributorNetIds": tuple(sorted(contributors)),
        "_contributorPadIndices": tuple(sorted(hit_indices)),
    }


def require_slot_match(analysis: Mapping[str, Any]) -> None:
    if analysis.get("match") is not True:
        _reject("P9_SLOT_MISMATCH")


def validate_output_reference(raw: Any, expected_path: str, expected_bytes: bytes) -> dict[str, Any]:
    ref = _keys(raw, ("path", "bytes", "sha256"), "P9_OUTPUT_CENSUS")
    if (
        ref["path"] != expected_path
        or type(ref["bytes"]) is not int
        or ref["bytes"] != len(expected_bytes)
        or ref["sha256"] != _sha256(expected_bytes)
    ):
        _reject("P9_OUTPUT_CENSUS")
    return ref


def _module_from_bytes(raw: bytes, path: Path, name: str) -> types.ModuleType:
    module = types.ModuleType(name)
    module.__file__ = str(path)
    module.__package__ = ""
    try:
        code = compile(raw, str(path), "exec", dont_inherit=True)
        exec(code, module.__dict__)
    except ReaderError:
        raise
    except Exception:
        _reject("P9_MODULE_LOAD")
    return module


_BOOTSTRAP_PINS_KEYS = {
    "schema", "sourceParent", "repositoryRoot", "p8Prerequisite", "publishedP8",
    "sourceInventory", "roles", "toolPaths", "files",
}
_BOOTSTRAP_INPUT_SCHEMA = "tsj-q100-reservation-provenance-inputs-v1"
_BOOTSTRAP_SOURCE_PARENT = "4623f1e5680503527a4423c2ca31fb09ede3cbb8"
_BOOTSTRAP_MAX_FILE_BYTES = 100 * 1024 * 1024


def _load_authenticated_bootstrap(pins_path: str | os.PathLike[str], pins_sha: str) -> dict[str, Any]:
    """Authenticate the small pins/self/executable bootstrap before deep source closure."""
    try:
        pin_path = Path(pins_path).resolve(strict=True)
        pins_raw = _stable_read(pin_path, MAX_PINS_BYTES, "P9_INPUT_PINS_HASH")
    except OSError:
        _reject("P9_INPUT_PINS_HASH")
    if len(pins_raw) > MAX_PINS_BYTES or not pins_raw.endswith(b"\n") or b"\r" in pins_raw:
        _reject("P9_INPUT_PINS_HASH")
    if _sha256(pins_raw) != pins_sha:
        _reject("P9_INPUT_PINS_HASH")
    pins = _strict_json(pins_raw[:-1], "P9_INPUT_ROOT_BINDING")
    if (type(pins) is not dict or set(pins) != _BOOTSTRAP_PINS_KEYS
            or pins.get("schema") != _BOOTSTRAP_INPUT_SCHEMA
            or pins.get("sourceParent") != _BOOTSTRAP_SOURCE_PARENT
            or _canonical_json(pins) + b"\n" != pins_raw):
        _reject("P9_INPUT_ROOT_BINDING")
    roles = pins.get("roles")
    files = pins.get("files")
    tool_paths = pins.get("toolPaths")
    if (type(roles) is not dict or type(roles.get("input")) is not dict
            or type(files) is not list or type(tool_paths) is not dict):
        _reject("P9_INPUT_ROOT_BINDING")
    auditor_path = roles["input"].get("auditor")
    python_path = tool_paths.get("pythonExe")
    if type(auditor_path) is not str or type(python_path) is not str:
        _reject("P9_INPUT_ROOT_BINDING")
    try:
        reader_path = Path(__file__).resolve(strict=True)
        auditor_resolved = Path(auditor_path).resolve(strict=True)
    except (OSError, RuntimeError):
        _reject("P9_INPUT_ROOT_BINDING")
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        _reject("P9_INPUT_TOOL_IDENTITY")

    by_path: dict[str, dict[str, Any]] = {}
    for row in files:
        if type(row) is not dict or set(row) != {"path", "bytes", "sha256"}:
            _reject("P9_INPUT_TOP_CENSUS")
        path = row["path"]
        if (type(path) is not str or type(row["bytes"]) is not int or row["bytes"] < 1
                or row["bytes"] > _BOOTSTRAP_MAX_FILE_BYTES
                or type(row["sha256"]) is not str or re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is None):
            _reject("P9_INPUT_TOP_CENSUS")
        try:
            key = os.path.normcase(str(Path(path).resolve(strict=True)))
        except (OSError, RuntimeError):
            _reject("P9_INPUT_TOP_CENSUS")
        if key in by_path:
            _reject("P9_INPUT_TOP_CENSUS")
        by_path[key] = row
    authenticated: dict[str, bytes] = {}
    targets = (("reader", reader_path, "P9_INPUT_SELF_PIN"),
               ("auditor", auditor_resolved, "P9_INPUT_SELF_PIN"))
    for name, path, error_code in targets:
        row = by_path.get(os.path.normcase(str(path)))
        if row is None:
            _reject(error_code)
        body = _stable_read(path, row["bytes"], error_code)
        if len(body) != row["bytes"] or _sha256(body) != row["sha256"]:
            _reject(error_code)
        authenticated[name] = body
    auditor = _module_from_bytes(authenticated["auditor"], auditor_resolved, "p9_authenticated_audit_inputs")
    try:
        image_resolved = Path(auditor.actual_image()).resolve(strict=True)
        python_resolved = Path(python_path).resolve(strict=True)
    except Exception:
        _reject("P9_INPUT_TOOL_IDENTITY")
    if os.path.normcase(str(python_resolved)) != os.path.normcase(str(image_resolved)):
        _reject("P9_INPUT_TOOL_IDENTITY")
    python_row = by_path.get(os.path.normcase(str(image_resolved)))
    if python_row is None:
        _reject("P9_INPUT_TOOL_IDENTITY")
    python_raw = _stable_read(image_resolved, python_row["bytes"], "P9_INPUT_TOOL_IDENTITY")
    if (len(python_raw) != python_row["bytes"] or _sha256(python_raw) != python_row["sha256"]
            or _sha256(python_raw) != auditor.PYTHON_SHA):
        _reject("P9_INPUT_TOOL_IDENTITY")
    return {
        "pins": pins,
        "pinsRaw": pins_raw,
        "pinsPath": str(pin_path),
        "auditorPath": str(auditor_resolved),
        "auditorRaw": authenticated["auditor"],
        "auditorModule": auditor,
    }


def _load_input_auditor(pins_sha: str, bootstrap: Mapping[str, Any]) -> tuple[Any, dict[str, Any]]:
    auditor = bootstrap["auditorModule"]
    try:
        context = auditor.validate_inputs(bootstrap["pinsPath"], pins_sha)
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            _reject(code)
        _reject("P9_INPUT_UNEXPECTED_FAILURE")
    if (context.get("pinsRaw") != bootstrap["pinsRaw"] or context.get("pins") != bootstrap["pins"]
            or context.get("pinsPath") != bootstrap["pinsPath"]):
        _reject("P9_INPUT_PINS_HASH")
    context["auditor"] = auditor
    return auditor, context


_OVERLAY_INPUT_SHA = "3bc847e015e1b379dcafcb1e39eb4964e65e7a57d6ef06ed2c710cca1ff48f2f"
_OVERLAY_INPUT_BYTES = 142148
_OVERLAY_ATTACH = b"fixedGraphReasonObserverForStudy=CanonicalReservationProvenanceStudy.decorateForStudy(this,observer);"
_OVERLAY_DETACH = b"            CanonicalReservationProvenanceStudy.detachForStudy(fixedGraphReasonObserverForStudy);\n"
_OVERLAY_SNAPSHOT = (
    b"        CanonicalReservationProvenanceStudy.RouterOperands "
    b"snapshotReservationOperandsForStudy() {\n"
    b"            return new CanonicalReservationProvenanceStudy.RouterOperands("
    b"layer,layout,board,outline,minX,minY,gridWidth,gridHeight,"
    b"checkProfiledPadTouches,emptyComponentFace,activeNetId,activeTraceWidth,"
    b"pads,padNets,padWidths,padBounds,escapeCorridors);\n"
    b"        }\n"
)
_OVERLAY_EDIT_TEMPLATES = (
    ("ATTACH_DECORATOR", 70923, 42, _OVERLAY_ATTACH),
    ("DETACH_RELEASE", 71035, 0, _OVERLAY_DETACH),
    ("PASSIVE_SNAPSHOT", 80109, 0, _OVERLAY_SNAPSHOT),
)


def verify_overlay(context: Mapping[str, Any]) -> None:
    try:
        p9_root = Path(context["p9Root"])
        source_path = Path(context["roots"]["CANARY"]) / "overlays-r1" / "PcbNetRouter.java"
        source_key = os.path.normcase(os.path.abspath(str(source_path))).casefold()
        source_row = context["existing"][source_key]
        source = context["_reads"].read(source_path, expected=source_row)
        overlay = context["filesData"]["overlays-r1/PcbNetRouter.java"]
        proof_raw = context["filesData"]["overlays-r1/overlay-manifest.json"]
        if not proof_raw.endswith(b"\n") or b"\r" in proof_raw:
            _reject("P9_OVERLAY_EDIT_SET")
        proof = _strict_json(proof_raw[:-1], "P9_OVERLAY_EDIT_SET")
        if _canonical_json(proof) + b"\n" != proof_raw:
            _reject("P9_OVERLAY_EDIT_SET")
    except ReaderError:
        raise
    except Exception:
        _reject("P9_OVERLAY_PRESERVATION")
    if len(source) != _OVERLAY_INPUT_BYTES or _sha256(source) != _OVERLAY_INPUT_SHA:
        _reject("P9_OVERLAY_EDIT_SET")
    top = ("schema", "sourceParent", "builder", "contractSet", "input", "outputs", "edits", "preserved", "claims")
    _keys(proof, top, "P9_OVERLAY_EDIT_SET")
    if proof["schema"] != "tsj-q100-reservation-overlay-v1" or proof["sourceParent"] != "4623f1e5680503527a4423c2ca31fb09ede3cbb8":
        _reject("P9_OVERLAY_EDIT_SET")

    def exact_ref(ref: Any, name: str, body: bytes, code: str) -> None:
        ref = _keys(ref, ("path", "bytes", "sha256"), code)
        if type(ref["path"]) is not str or type(ref["bytes"]) is not int or type(ref["sha256"]) is not str:
            _reject(code)
        if _canonical_json(ref) != _canonical_json({"path": name, "bytes": len(body), "sha256": _sha256(body)}):
            _reject(code)

    exact_ref(proof["builder"], "P9/build_overlay.py", context["filesData"]["build_overlay.py"], "P9_OVERLAY_EDIT_SET")
    exact_ref(proof["contractSet"], "P9/CONTRACT_SET_R1.json", context["filesData"]["CONTRACT_SET_R1.json"], "P9_OVERLAY_EDIT_SET")
    exact_ref(proof["input"], "CANARY/overlays-r1/PcbNetRouter.java", source, "P9_OVERLAY_EDIT_SET")
    if type(proof["outputs"]) is not list or len(proof["outputs"]) != 1:
        _reject("P9_OVERLAY_EDIT_SET")
    exact_ref(proof["outputs"][0], "P9/overlays-r1/PcbNetRouter.java", overlay, "P9_OVERLAY_EDIT_SET")

    edits = []
    preserved = []
    pieces = []
    input_cursor = 0
    output_cursor = 0
    for name, offset, removed, inserted in _OVERLAY_EDIT_TEMPLATES:
        if offset < input_cursor or offset + removed > len(source):
            _reject("P9_OVERLAY_EDIT_SET")
        if input_cursor < offset:
            segment = source[input_cursor:offset]
            preserved.append({"inputOffset": input_cursor, "outputOffset": output_cursor,
                              "bytes": len(segment), "sha256": _sha256(segment)})
            pieces.append(segment)
            output_cursor += len(segment)
        removed_raw = source[offset:offset + removed]
        edits.append({"id": name, "sourceOffset": offset, "removeBytes": removed,
                      "removedSha256": _sha256(removed_raw), "insertBytes": len(inserted),
                      "insertSha256": _sha256(inserted)})
        pieces.append(inserted)
        output_cursor += len(inserted)
        input_cursor = offset + removed
    if input_cursor < len(source):
        segment = source[input_cursor:]
        preserved.append({"inputOffset": input_cursor, "outputOffset": output_cursor,
                          "bytes": len(segment), "sha256": _sha256(segment)})
        pieces.append(segment)
    if (_canonical_json(proof["edits"]) != _canonical_json(edits)
            or _canonical_json(proof["preserved"]) != _canonical_json(preserved)
            or b"".join(pieces) != overlay):
        _reject("P9_OVERLAY_PRESERVATION")
    claims = {key: True for key in ("observerOnly", "predicatesUnchanged", "cacheWritesUnchanged",
                                    "copyAndConstructorUnchanged", "p8ForwardingOnce")}
    if _canonical_json(proof["claims"]) != _canonical_json(claims):
        _reject("P9_OVERLAY_PRESERVATION")


def _load_p8_reader(context: Mapping[str, Any]) -> types.ModuleType:
    return _module_from_bytes(context["p8ReaderRaw"], Path(context["p8ReaderPath"]), "p9_cached_p8_reader")


_HOST_RESULT_KEYS = (
    "schema", "status", "mode", "suiteId", "inputSha256", "sourceSha256",
    "sourceParent", "p8PrerequisiteSha256", "wireSha256", "modeOrder", "host",
    "processes", "sourceChecks", "compiler", "bridgeCopies", "canaries",
    "operations", "timing", "cleanup", "limits", "outputClosure",
    "layoutQualification",
)
_HOST_PROCESS_KEYS = (
    "FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
    "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
    "TerminationProven",
)
_HOST_DESCRIPTOR_KEYS = ("receipt", "stdout", "stderr", "wallMs")
_HOST_FILE_REF_KEYS = ("path", "bytes", "sha256")
_HOST_INTENT_KEYS = (
    "schema", "role", "owner", "executable", "executableSha256", "arguments",
    "limitMs", "inputSha256", "sourceSha256", "outputRoot",
)
_HOST_CANARY_ORDER = (
    "preflight", "compiler", "preNative", "p6Canary", "p8Canary", "p9Canary",
    "badMode", "badUuid", "badInputSha", "nonemptyData", "nonemptyP9", "postSource",
)
_HOST_SUITE_ORDER = _HOST_CANARY_ORDER[:-1] + (
    "nullMode", "observeMode", "tapThrow", "callbackThrow", "reasonThrow", "postSource",
)
_HOST_NATIVE_STEMS = (
    ("p6Canary", "p6-canary"), ("p8Canary", "p8-canary"),
    ("p9Canary", "p9-canary"), ("badMode", "p9-bad-mode"),
    ("badUuid", "p9-bad-uuid"), ("badInputSha", "p9-bad-input-sha"),
    ("nonemptyData", "p9-nonempty-data"), ("nonemptyP9", "p9-nonempty-p9"),
)
_HOST_MODE_ORDER = (
    "P6_CANARY", "P8_CANARY", "P9_CANARY", "P9_BAD_MODE", "P9_BAD_UUID",
    "P9_BAD_INPUT_SHA", "P9_NONEMPTY_DATA", "P9_NONEMPTY_P9",
)
_HOST_CANARY_IDS = (
    "NULL_UNARMED", "FORWARD_ONCE", "TARGET_ONE_SHOT", "DETACH_DROP",
    "CONTAIN_SENTINEL", "UNEXPECTED_PROPAGATES", "UNIT_LIMIT", "INCLUSIVE_OUTPUT_LIMIT",
)
_HOST_NEGATIVE_ROLES = {
    "badMode": ("p9-bad-mode", "INVALID_MODE", 0),
    "badUuid": ("p9-bad-uuid", "INVALID_RUN_UUID", 1),
    "badInputSha": ("p9-bad-input-sha", "INVALID_INPUT_SHA256", 2),
    "nonemptyData": ("p9-nonempty-data", "OUTPUT_DIRECTORY_NOT_EMPTY", 5),
    "nonemptyP9": ("p9-nonempty-p9", "OUTPUT_DIRECTORY_NOT_EMPTY", 6),
}
_BASE_LEAVES = (
    "host-launch-intent.json", "scratch-intent.json", "compiler-launch-intent.json",
    "compiler-sources.txt", "compiler-process.json", "compiler.stdout", "compiler.stderr",
    "bridge-copies.json", "host-result.json", "suite-index.json", "suite-footer.json",
    "source-preflight-process.json", "source-preflight.stdout", "source-preflight.stderr",
    "source-prenative-process.json", "source-prenative.stdout", "source-prenative.stderr",
    "source-post-process.json", "source-post.stdout", "source-post.stderr",
    "controls/P9_NONEMPTY_DATA/data/sentinel.txt", "controls/P9_NONEMPTY_P9/p9/sentinel.txt",
)
_EMPTY_DIRS = (
    "controls/P9_BAD_MODE/data", "controls/P9_BAD_MODE/p9",
    "controls/P9_BAD_UUID/data", "controls/P9_BAD_UUID/p9",
    "controls/P9_BAD_INPUT_SHA/data", "controls/P9_BAD_INPUT_SHA/p9",
    "controls/P9_NONEMPTY_DATA/p9", "controls/P9_NONEMPTY_P9/data",
)


def _stable_read(path: Path, maximum: int, code: str, *, allow_empty: bool = False) -> bytes:
    try:
        before = path.lstat()
        attrs = getattr(before, "st_file_attributes", 0)
        if not path.is_absolute() or not stat.S_ISREG(before.st_mode) or stat.S_ISLNK(before.st_mode) or attrs & 0x400:
            _reject(code)
        if before.st_size < (0 if allow_empty else 1) or before.st_size > maximum:
            _reject(code)
        raw = path.read_bytes()
        after = path.lstat()
    except ReaderError:
        raise
    except OSError:
        _reject(code)
    after_attrs = getattr(after, "st_file_attributes", 0)
    if ((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) !=
            (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
            or after_attrs & 0x400 or len(raw) != before.st_size or len(raw) > maximum):
        _reject(code)
    return raw


def _compact_json(raw: bytes, code: str) -> Any:
    value = _strict_json(raw, code)
    # Compact is a framing rule, not a Python serializer fingerprint. PS5 emits
    # round-trip doubles (and JSON string escapes) with different spellings.
    # The raw artifact hashes still bind those exact producer bytes.
    try:
        json.dumps(value, ensure_ascii=True, separators=(",", ":"), allow_nan=False)
    except (TypeError, ValueError, UnicodeError):
        _reject(code)
    quoted = False
    escaped = False
    for byte in raw:
        if quoted:
            if escaped:
                escaped = False
            elif byte == 0x5c:
                escaped = True
            elif byte == 0x22:
                quoted = False
        elif byte == 0x22:
            quoted = True
        elif byte in (0x20, 0x09, 0x0a, 0x0d):
            _reject(code)
    return value


def _strict_document(raw: bytes, code: str, *, max_line: int = MAX_LINE_BYTES) -> Any:
    if (not raw or len(raw) > max_line or not raw.endswith(b"\n") or b"\r" in raw
            or raw.startswith(b"\xef\xbb\xbf") or any(byte > 0x7f for byte in raw)):
        _reject(code)
    return _compact_json(raw[:-1], code)


def _exact_keys(value: Any, expected: Sequence[str], code: str, *, ordered: bool = False) -> dict[str, Any]:
    if type(value) is not dict or set(value) != set(expected) or (ordered and tuple(value) != tuple(expected)):
        _reject(code)
    return value


def _exact_bool(value: Any, code: str) -> bool:
    if type(value) is not bool:
        _reject(code)
    return value


def _digest_text(value: Any, code: str) -> str:
    if type(value) is not str or re.fullmatch(r"[0-9a-f]{64}", value) is None:
        _reject(code)
    return value


def _canonical_abs_path(value: Any, code: str, *, exists: bool | None = None) -> Path:
    if type(value) is not str:
        _reject(code)
    path = Path(value)
    if not path.is_absolute() or ".." in path.parts:
        _reject(code)
    try:
        resolved = path.resolve(strict=exists is True)
    except OSError:
        _reject(code)
    if str(resolved) != value:
        _reject(code)
    return resolved


def _relative_path(value: Any, code: str) -> str:
    if (type(value) is not str or not value or value.startswith("/") or "\\" in value
            or any(part in ("", ".", "..") for part in value.split("/"))
            or re.fullmatch(r"[A-Za-z0-9._/-]+", value) is None):
        _reject(code)
    return value


def _load_host_file(root: Path, rel: str, maximum: int, code: str) -> bytes:
    rel = _relative_path(rel, code)
    path = root.joinpath(*rel.split("/"))
    try:
        resolved = path.resolve(strict=True)
    except OSError:
        _reject(code)
    if not _under(resolved, root):
        _reject(code)
    return _stable_read(resolved, maximum, code, allow_empty=True)


def _under(path: Path, root: Path) -> bool:
    try:
        return os.path.commonpath((os.path.normcase(str(path)), os.path.normcase(str(root)))) == os.path.normcase(str(root))
    except (ValueError, OSError):
        return False


def _expected_tree(mode: str) -> tuple[set[str], set[str]]:
    if mode not in ("CANARY", "SUITE"):
        _reject("P9_HOST_MODE")
    leaves = set(_BASE_LEAVES)
    for _role, stem in _HOST_NATIVE_STEMS:
        leaves.update((f"{stem}-launch-intent.json", f"{stem}-process.json", f"{stem}.stdout", f"{stem}.stderr"))
    if mode == "SUITE":
        for run in ("NULL", "OBSERVE", "TAP_THROW", "CALLBACK_THROW", "REASON_THROW"):
            leaves.update((f"runs/{run}/launch-intent.json", f"runs/{run}/process.json",
                           f"runs/{run}.stdout", f"runs/{run}.stderr"))
        for run in NORMAL_MODES:
            for name in P8_DATA_FILES:
                leaves.add(f"runs/{run}/data/{name}")
            leaves.update((f"runs/{run}/p9/reservation-operands.jsonl", f"runs/{run}/p9/reservation-index.json"))
        leaves.add("runs/CALLBACK_THROW/data/op-1.jsonl")
        leaves.update(f"runs/REASON_THROW/data/{name}" for name in
                      ("op-1.jsonl", "prefix-blockers.json", "prefix-stages.json", "prefix-geometric-reasons.jsonl"))
    dirs: set[str] = set(_EMPTY_DIRS)
    if mode == "SUITE":
        dirs.add("runs")
    for leaf in leaves | set(_EMPTY_DIRS):
        parent = leaf.rpartition("/")[0]
        while parent:
            dirs.add(parent)
            parent = parent.rpartition("/")[0]
    return leaves, dirs


def _tree_census(root: Path, expected_files: set[str], expected_dirs: set[str]) -> dict[str, bytes]:
    actual_files: set[str] = set()
    actual_dirs: set[str] = set()
    seen = 0
    stack = [(root, "")]
    while stack:
        here, rel = stack.pop()
        try:
            with os.scandir(here) as entries:
                for entry in entries:
                    seen += 1
                    if seen > len(expected_files) + len(expected_dirs):
                        _reject("P9_OUTPUT_TREE_CENSUS")
                    child = entry.name if not rel else rel + "/" + entry.name
                    info = entry.stat(follow_symlinks=False)
                    attrs = getattr(info, "st_file_attributes", 0)
                    if stat.S_ISLNK(info.st_mode) or attrs & 0x400:
                        _reject("P9_OUTPUT_TREE_REPARSE")
                    if stat.S_ISDIR(info.st_mode):
                        actual_dirs.add(child)
                        stack.append((Path(entry.path), child))
                    elif stat.S_ISREG(info.st_mode):
                        actual_files.add(child)
                    else:
                        _reject("P9_OUTPUT_TREE_ENTRY_KIND")
        except ReaderError:
            raise
        except OSError:
            _reject("P9_OUTPUT_TREE_READ")
    if actual_files != expected_files or actual_dirs != expected_dirs:
        _reject("P9_OUTPUT_TREE_CENSUS")
    data: dict[str, bytes] = {}
    aggregate = 0
    for rel in sorted(expected_files):
        if rel.endswith((".json", ".jsonl")):
            maximum = MAX_LINE_BYTES if rel.endswith(".json") else MAX_OPERAND_BYTES
        elif rel == "compiler-sources.txt":
            maximum = 1024 * 1024
        else:
            maximum = MAX_OPERAND_BYTES
        raw = _load_host_file(root, rel, maximum, "P9_OUTPUT_FILE_READ")
        aggregate += len(raw)
        if aggregate > 40 * 1024 * 1024:
            _reject("P9_OUTPUT_SUITE_LIMIT")
        data[rel] = raw
    return data


def _relative_ref(value: Any, path: str, raw: bytes, code: str) -> dict[str, Any]:
    ref = _keys(value, _HOST_FILE_REF_KEYS, code)
    if (ref["path"] != path or type(ref["bytes"]) is not int or ref["bytes"] != len(raw)
            or ref["sha256"] != _sha256(raw)):
        _reject(code)
    return ref


def _parse_host_marker(stdout: str) -> dict[str, Any]:
    if type(stdout) is not str:
        _reject("P9_PARENT_MARKER")
    try:
        raw = stdout.encode("utf-8", "strict")
    except UnicodeError:
        _reject("P9_PARENT_MARKER")
    prefix = b"P9_RESERVATION_HOST_COMPLETE "
    if not raw.startswith(prefix) or len(raw) > MAX_LINE_BYTES or not raw.endswith((b"\n", b"\r\n")):
        _reject("P9_PARENT_MARKER")
    body = raw[len(prefix):]
    if body.endswith(b"\r\n"):
        body = body[:-2]
    elif body.endswith(b"\n"):
        body = body[:-1]
    if not body or b"\n" in body or b"\r" in body:
        _reject("P9_PARENT_MARKER")
    marker = _compact_json(body, "P9_PARENT_MARKER")
    if type(marker) is not dict:
        _reject("P9_PARENT_MARKER")
    return marker


def _json_lines(raw: bytes, code: str, max_bytes: int = MAX_OPERAND_BYTES) -> list[Any]:
    if (len(raw) > max_bytes or not raw.endswith(b"\n") or b"\r" in raw
            or raw.startswith(b"\xef\xbb\xbf")):
        _reject(code)
    lines = raw[:-1].split(b"\n")
    if not lines or any(not line or len(line) + 1 > MAX_LINE_BYTES for line in lines):
        _reject(code)
    values = [_strict_object_line(line) for line in lines]
    if any(type(value) is not dict for value in values):
        _reject(code)
    return values


def _scalar_leaves(value: Any) -> int:
    if type(value) is dict:
        return sum(_scalar_leaves(item) for item in value.values())
    if type(value) is list:
        return sum(_scalar_leaves(item) for item in value)
    return 1


def _ceil_div(number: int, divisor: int) -> int:
    return (number + divisor - 1) // divisor


def _validate_p9_index(raw: bytes, *, mode: str, operation: Mapping[str, Any],
                       operands_raw: bytes, p8_data: Mapping[str, bytes],
                       p8_run: Mapping[str, Any], p8_proc: Mapping[str, Any],
                       operation_record: bytes, groups: int,
                       input_sha: str, source_sha: str) -> dict[str, Any]:
    index = _strict_document(raw, "P9_NATIVE_INDEX")
    top_keys = ("schema", "wrapperMode", "p8Mode", "runId", "inputSha256", "sourceSha256",
                "p8Index", "sidecar", "capture", "work", "bytes", "cleanup", "status", "layoutQualification")
    _exact_keys(index, top_keys, "P9_NATIVE_INDEX_FIELDS")
    if (index["schema"] != "tsj-q100-reservation-provenance-index-v1" or index["wrapperMode"] != mode
            or index["p8Mode"] != ("NULL" if mode == "NULL" else "OBSERVE")
            or index["runId"] != operation["runId"] or index["inputSha256"] != input_sha
            or index["sourceSha256"] != source_sha
            or index["status"] != "PROVISIONAL_NATIVE_RESULT" or index["layoutQualification"] is not False):
        _reject("P9_NATIVE_INDEX_BINDING")
    p8_index_raw = p8_data["canonical-geometric-prefix-index.json"]
    _relative_ref(index["p8Index"], "canonical-geometric-prefix-index.json", p8_index_raw, "P9_NATIVE_INDEX_P8_REF")
    _relative_ref(index["sidecar"], "reservation-operands.jsonl", operands_raw, "P9_NATIVE_INDEX_SIDECAR_REF")

    expected_capture = {
        "NULL": ("NONE", False, 0, 0, 0, None, None, 0, 0, 0, False, 0, 0),
        "OBSERVE": ("COMPLETE", True, 14, 14, 0, 10, 11, 1, 246, 246, False, 14, 1),
        "TAP_THROW": ("DISCARDED_AFTER_SENTINEL", True, 14, 14, 0, 10, 11, 1, 246, 0, True, 14, 1),
    }[mode]
    capture_keys = ("state", "registrationArmed", "fineAttachments", "decoratorsDetached", "openDecorators",
                    "targetAttachmentOrdinal", "targetParentOrdinal", "targetEvents", "padRowsBuilt",
                    "padRowsRetained", "sentinelObserved", "forwardedFineEvents", "targetForwardedEvents")
    capture = _exact_keys(index["capture"], capture_keys, "P9_NATIVE_INDEX_CAPTURE")
    if tuple(capture[key] for key in capture_keys) != expected_capture:
        _reject("P9_NATIVE_INDEX_CAPTURE")
    for key in ("registrationArmed", "sentinelObserved"):
        _exact_bool(capture[key], "P9_NATIVE_INDEX_CAPTURE")
    for key in ("fineAttachments", "decoratorsDetached", "openDecorators", "targetEvents", "padRowsBuilt",
                "padRowsRetained", "forwardedFineEvents", "targetForwardedEvents"):
        _exact_int(capture[key], "P9_NATIVE_INDEX_CAPTURE")
    for key in ("targetAttachmentOrdinal", "targetParentOrdinal"):
        if expected_capture[capture_keys.index(key)] is None:
            if capture[key] is not None:
                _reject("P9_NATIVE_INDEX_CAPTURE")
        else:
            _exact_int(capture[key], "P9_NATIVE_INDEX_CAPTURE")

    cleanup_keys = ("p8AfterOwnersClosed", "p8OwnersReleased", "p8ContextRestored", "p8SourcePreserved",
                    "p8ObserversDetached", "p8ReferencesDropped", "p9RegistrationCleared",
                    "p9DecoratorDetachCount", "p9OpenDecorators", "p9DecoratorReferencesDropped",
                    "p9DtoReferencesDropped")
    cleanup = _exact_keys(index["cleanup"], cleanup_keys, "P9_NATIVE_INDEX_CLEANUP")
    for key in cleanup_keys:
        if key in ("p9DecoratorDetachCount", "p9OpenDecorators"):
            continue
        _exact_bool(cleanup[key], "P9_NATIVE_INDEX_CLEANUP")
    if (any(cleanup[key] is not True for key in cleanup_keys if key not in ("p9DecoratorDetachCount", "p9OpenDecorators"))
            or cleanup["p9DecoratorDetachCount"] != expected_capture[3]
            or cleanup["p9OpenDecorators"] != 0):
        _reject("P9_NATIVE_INDEX_CLEANUP")
    _exact_int(cleanup["p9DecoratorDetachCount"], "P9_NATIVE_INDEX_CLEANUP")
    _exact_int(cleanup["p9OpenDecorators"], "P9_NATIVE_INDEX_CLEANUP")

    byte_keys = ("p8DataBytes", "p8StdoutBytes", "p8StderrBytes", "p9SidecarBytes", "preIndexAggregateBytes")
    byte_values = _exact_keys(index["bytes"], byte_keys, "P9_NATIVE_INDEX_BYTES")
    for key in byte_keys:
        _exact_int(byte_values[key], "P9_NATIVE_INDEX_BYTES")
        if byte_values[key] < 0:
            _reject("P9_NATIVE_INDEX_BYTES")
    p8_data_bytes = sum(len(value) for value in p8_data.values())
    p8_stdout = len(p8_proc["Stdout"].encode("utf-8", "strict"))
    p8_stderr = len(p8_proc["Stderr"].encode("utf-8", "strict"))
    expected_pre = p8_data_bytes + p8_stdout + p8_stderr + len(operands_raw)
    if (byte_values["p8DataBytes"] != p8_data_bytes or byte_values["p8StdoutBytes"] != p8_stdout
            or byte_values["p8StderrBytes"] != p8_stderr or byte_values["p9SidecarBytes"] != len(operands_raw)
            or byte_values["preIndexAggregateBytes"] != expected_pre):
        _reject("P9_NATIVE_INDEX_BYTES")
    full_native_bytes = expected_pre + len(raw)
    if expected_pre > MAX_OPERAND_BYTES or full_native_bytes > MAX_OPERAND_BYTES:
        _reject("P9_NATIVE_INDEX_BYTES")

    work_keys = (
        "delegateStartOffsetNanos", "delegateReturnOffsetNanos", "p8ReportedWorkNanos", "delegateCleanupNanos",
        "p9DelegateCleanupNanos", "outsideWorkSegments", "p9CleanupNanos", "creditCutoffOffsetNanos",
        "creditedWorkNanos", "p8Units", "p9CaptureUnits", "p9ReleaseUnits", "p9MetadataUnits",
        "p9SerializationUnits", "p9ByteUnits", "p9IndexUnits", "p9ExportUnits", "p9Units", "totalUnits",
        "actualReceiptGroups", "nativeWrapperGroups", "p9Groups", "totalGroups",
        "maximumP9GroupNanosBeforeIndex", "measuredWorkAtIndexCutoffNanos", "finalGuardFormula",
    )
    work = _exact_keys(index["work"], work_keys, "P9_NATIVE_INDEX_WORK")
    int_work = [key for key in work_keys if key not in ("outsideWorkSegments", "finalGuardFormula")]
    for key in int_work:
        _exact_int(work[key], "P9_NATIVE_INDEX_WORK")
        if work[key] < 0:
            _reject("P9_NATIVE_INDEX_WORK")
    p8_marker = p8_run["p8Marker"]
    p8_index = p8_run["p8Index"]
    p6_index_raw = p8_run["p6"]["indexRaw"]
    p6_index = _strict_document(p6_index_raw, "P9_NATIVE_INDEX_WORK")
    p6_cleanup = p8_run["p6"]["entry"]["cleanup"]["elapsedNanos"]
    p8_groups = groups
    expected_capture_units = 6208 if mode == "OBSERVE" else (6208 if mode == "TAP_THROW" else 0)
    expected_release_units = expected_capture_units
    expected_serialization = {"NULL": 20, "OBSERVE": 6454, "TAP_THROW": 30}[mode]
    metadata_units = (_scalar_leaves(p8_marker) + _scalar_leaves(p8_index)
                      + _scalar_leaves(p6_index) + operation_record.count(b"\n"))
    byte_units = (_ceil_div(len(p6_index_raw), 4096) + _ceil_div(len(p8_index_raw), 4096)
                  + _ceil_div(len(operation_record), 4096) + 3 * _ceil_div(len(operands_raw), 4096))
    expected_p9_export = expected_serialization + byte_units + 4
    expected_p9_units = expected_capture_units + expected_release_units + metadata_units + expected_p9_export
    expected_total_units = p8_run["totalObservationUnits"] + expected_p9_units
    if (work["p8ReportedWorkNanos"] != p8_marker["workNanos"]
            or work["delegateCleanupNanos"] != p6_cleanup
            or work["p8Units"] != p8_run["totalObservationUnits"]
            or work["p9CaptureUnits"] != expected_capture_units
            or work["p9ReleaseUnits"] != expected_release_units
            or work["p9MetadataUnits"] != metadata_units
            or work["p9SerializationUnits"] != expected_serialization
            or work["p9ByteUnits"] != byte_units or work["p9IndexUnits"] != 4
            or work["p9ExportUnits"] != expected_p9_export or work["p9Units"] != expected_p9_units
            or work["totalUnits"] != expected_total_units or expected_total_units > 32768
            or work["actualReceiptGroups"] != p8_groups or work["nativeWrapperGroups"] != 3
            or work["p9Groups"] != 3 or work["totalGroups"] != p8_groups + 6
            or work["totalGroups"] > 640 or work["maximumP9GroupNanosBeforeIndex"] > 5_000_000_000
            or work["finalGuardFormula"] != "P9_ELAPSED_MINUS_P6_ARM_CLEANUP"
            or work["measuredWorkAtIndexCutoffNanos"] != work["creditCutoffOffsetNanos"] - p6_cleanup):
        _reject("P9_NATIVE_INDEX_WORK")
    _exact_int(p6_cleanup, "P9_NATIVE_INDEX_WORK")
    if (work["delegateStartOffsetNanos"] > work["delegateReturnOffsetNanos"]
            or work["delegateReturnOffsetNanos"] > work["creditCutoffOffsetNanos"]):
        _reject("P9_NATIVE_INDEX_WORK")
    if work["creditCutoffOffsetNanos"] - p6_cleanup > 90_000_000_000:
        _reject("P9_NATIVE_INDEX_WORK")
    _exact_int(work["p9MetadataUnits"], "P9_NATIVE_INDEX_WORK")
    _exact_int(work["creditedWorkNanos"], "P9_NATIVE_INDEX_WORK")
    segs = work["outsideWorkSegments"]
    if type(segs) is not list or len(segs) != 2:
        _reject("P9_NATIVE_INDEX_WORK")
    segment_keys = ("phase", "startOffsetNanos", "endOffsetNanos", "units", "groupCount", "maximumGroupNanos", "cleanupNanos")
    cleanup_sum = 0
    previous_end = -1
    phases = ("P9_PREFLIGHT", "P9_SIDECAR_EXPORT")
    for segment, phase in zip(segs, phases):
        _exact_keys(segment, segment_keys, "P9_NATIVE_INDEX_WORK")
        if segment["phase"] != phase:
            _reject("P9_NATIVE_INDEX_WORK")
        for key in segment_keys[1:]:
            _exact_int(segment[key], "P9_NATIVE_INDEX_WORK")
            if segment[key] < 0:
                _reject("P9_NATIVE_INDEX_WORK")
        if (segment["startOffsetNanos"] > segment["endOffsetNanos"]
                or segment["endOffsetNanos"] > work["creditCutoffOffsetNanos"]
                or (previous_end >= 0 and segment["startOffsetNanos"] < previous_end)
                or segment["groupCount"] != 1 or segment["maximumGroupNanos"] > 5_000_000_000):
            _reject("P9_NATIVE_INDEX_WORK")
        previous_end = segment["endOffsetNanos"]
        cleanup_sum += segment["cleanupNanos"]
        if segment["cleanupNanos"] > segment["endOffsetNanos"] - segment["startOffsetNanos"]:
            _reject("P9_NATIVE_INDEX_WORK")
    if work["p9CleanupNanos"] != work["p9DelegateCleanupNanos"] + cleanup_sum:
        _reject("P9_NATIVE_INDEX_WORK")
    if (segs[0]["startOffsetNanos"] != 0
            or segs[0]["endOffsetNanos"] != work["delegateStartOffsetNanos"]
            or segs[1]["startOffsetNanos"] != work["delegateReturnOffsetNanos"]
            or segs[1]["endOffsetNanos"] != work["creditCutoffOffsetNanos"]):
        _reject("P9_NATIVE_INDEX_WORK")
    if mode == "TAP_THROW":
        if work["creditedWorkNanos"] != 0:
            _reject("P9_NATIVE_INDEX_WORK")
    else:
        expected_credit = work["p8ReportedWorkNanos"] - work["p9DelegateCleanupNanos"] + sum(
            s["endOffsetNanos"] - s["startOffsetNanos"] - s["cleanupNanos"] for s in segs)
        if expected_credit < 0 or work["creditedWorkNanos"] != expected_credit:
            _reject("P9_NATIVE_INDEX_WORK")
    return index


def _read_parent_envelope(path: Path) -> dict[str, Any]:
    raw = _stable_read(path, MAX_LINE_BYTES, "P9_PARENT_RECEIPT")
    parent = _strict_document(raw, "P9_PARENT_RECEIPT")
    _exact_keys(parent, ("schema", "elapsedMs", "process"), "P9_PARENT_RECEIPT_FIELDS", ordered=True)
    if parent["schema"] != "tsj-q100-reservation-provenance-parent-v1":
        _reject("P9_PARENT_RECEIPT_SCHEMA")
    elapsed = parent["elapsedMs"]
    if (type(elapsed) not in (int, float) or type(elapsed) is bool
            or (type(elapsed) is float and not math.isfinite(elapsed))):
        _reject("PARENT_ELAPSED_TYPE_INVALID")
    if not (0 < elapsed <= 1_800_000):
        _reject("PARENT_TIMING_ORDER_INVALID")
    proc = _exact_keys(parent["process"], _HOST_PROCESS_KEYS, "P9_PARENT_PROCESS", ordered=True)
    if type(proc["ExitCode"]) is not int or proc["ExitCode"] != 0:
        _reject("PARENT_PROCESS_EXIT_INVALID")
    if type(proc["TerminationProven"]) is not bool or proc["TerminationProven"] is not True:
        _reject("PARENT_PROCESS_TERMINATION_INVALID")
    return parent


def _validate_parent_timing(parent: Mapping[str, Any], host: Mapping[str, Any]) -> None:
    elapsed = parent["elapsedMs"]
    timing = host.get("timing")
    if (type(timing) is dict and type(timing.get("cleanupStartOffsetMs")) in (int, float)
            and type(timing.get("cleanupStartOffsetMs")) is not bool
            and elapsed < timing["cleanupStartOffsetMs"]):
        _reject("PARENT_TIMING_ORDER_INVALID")


def _precheck_parent_envelope(path: Path, output_root: Path) -> None:
    """Reject cheap parent-envelope failures before the deep source inventory audit."""
    parent = _read_parent_envelope(path)
    try:
        host_raw = _load_host_file(output_root, "host-result.json", MAX_LINE_BYTES, "P9_HOST_RESULT_READ")
        host = _strict_document(host_raw, "P9_HOST_RESULT")
    except ReaderError:
        # A malformed or absent host result is handled by the unchanged full
        # host/source path. Only use a parseable actual host timing field here.
        return
    if type(host) is dict:
        _validate_parent_timing(parent, host)


def _validate_parent_receipt(path: Path, context: Mapping[str, Any], pins_sha: str,
                             output_root: Path, host_raw: bytes, host: Mapping[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    parent = _read_parent_envelope(path)
    _validate_parent_timing(parent, host)
    elapsed = parent["elapsedMs"]
    proc = parent["process"]
    expected_args = ["-NoLogo", "-NoProfile", "-NonInteractive", "-File", context["pins"]["roles"]["process"]["host"],
                     "-RepositoryRoot", context["pins"]["repositoryRoot"], "-InputPinsPath", context["pinsPath"],
                     "-InputPinsSha256", pins_sha, "-OutputRoot", str(output_root),
                     "-JavaHome", context["pins"]["toolPaths"]["javaHome"], "-PythonExe", context["pins"]["toolPaths"]["pythonExe"],
                     "-Mode", host["mode"]]
    if (proc["FilePath"] != context["pins"]["toolPaths"]["powershellExe"] or type(proc["Arguments"]) is not list
            or proc["Arguments"] != expected_args
            or type(proc["Stdout"]) is not str or type(proc["Stderr"]) is not str or proc["Stderr"] != ""):
        _reject("P9_PARENT_PROCESS")
    _exact_int(proc["ProcessId"], "P9_PARENT_PROCESS")
    _exact_int(proc["ProcessStartTicks"], "P9_PARENT_PROCESS")
    if proc["ProcessId"] < 1 or proc["ProcessStartTicks"] < 1:
        _reject("P9_PARENT_PROCESS")
    for key in ("ProcessRoot", "StdoutLog", "StderrLog"):
        value = _canonical_abs_path(proc[key], "P9_PARENT_PROCESS", exists=False)
        if value.exists():
            _reject("P9_PARENT_PROCESS")
    marker = _parse_host_marker(proc["Stdout"])
    _exact_keys(marker, ("schema", "status", "mode", "suiteId", "inputSha256", "sourceSha256",
                         "wireSha256", "hostResult", "suiteIndex", "suiteFooter", "layoutQualification"),
                "P9_PARENT_MARKER_FIELDS")
    if (marker["schema"] != "tsj-q100-reservation-provenance-host-complete-v1"
            or marker["mode"] != host["mode"] or marker["suiteId"] != host["suiteId"]
            or marker["inputSha256"] != pins_sha or marker["sourceSha256"] != context["sourceSha"]
            or marker["wireSha256"] != context["p8WireSha"]
            or marker["status"] != host["status"] or marker["layoutQualification"] is not False):
        _reject("P9_PARENT_MARKER_BINDING")
    _relative_ref(marker["hostResult"], "host-result.json", host_raw, "P9_PARENT_MARKER_BINDING")
    return parent, marker


def _validate_process_receipt(data: Mapping[str, bytes], descriptor: Any, role: str,
                              expected_path: str, expected_args: list[str], expected_exit: int,
                              output_root: Path) -> tuple[dict[str, Any], float]:
    desc = _exact_keys(descriptor, _HOST_DESCRIPTOR_KEYS, "P9_HOST_PROCESS_DESCRIPTOR")
    receipt_path = _relative_path(desc["receipt"], "P9_HOST_PROCESS_DESCRIPTOR")
    stdout_path = _relative_path(desc["stdout"], "P9_HOST_PROCESS_DESCRIPTOR")
    stderr_path = _relative_path(desc["stderr"], "P9_HOST_PROCESS_DESCRIPTOR")
    if receipt_path not in data or stdout_path not in data or stderr_path not in data:
        _reject("P9_HOST_PROCESS_DESCRIPTOR")
    raw = data[receipt_path]
    proc = _strict_document(raw, "P9_HOST_PROCESS_RECEIPT")
    _exact_keys(proc, _HOST_PROCESS_KEYS, "P9_HOST_PROCESS_RECEIPT", ordered=True)
    if (proc["FilePath"] != expected_path or proc["Arguments"] != expected_args
            or type(proc["Arguments"]) is not list or type(proc["ExitCode"]) is not int
            or proc["ExitCode"] != expected_exit or type(proc["TerminationProven"]) is not bool
            or not proc["TerminationProven"] or type(proc["Stdout"]) is not str or type(proc["Stderr"]) is not str):
        _reject("P9_HOST_PROCESS_IDENTITY")
    _exact_int(proc["ProcessId"], "P9_HOST_PROCESS_IDENTITY")
    _exact_int(proc["ProcessStartTicks"], "P9_HOST_PROCESS_IDENTITY")
    if not 1 <= proc["ProcessId"] <= 2**31 - 1 or not 1 <= proc["ProcessStartTicks"] <= 3155378975999999999:
        _reject("P9_HOST_PROCESS_IDENTITY")
    wall = desc["wallMs"]
    if (type(wall) not in (int, float) or type(wall) is bool
            or (type(wall) is float and not math.isfinite(wall)) or not (0 <= wall <= 480_000)):
        _reject("P9_HOST_PROCESS_WALL")
    for name, key in ((stdout_path, "Stdout"), (stderr_path, "Stderr")):
        try:
            stream_bytes = proc[key].encode("utf-8", "strict")
        except UnicodeError:
            _reject("P9_HOST_PROCESS_STREAM")
        if data[name] != stream_bytes:
            _reject("P9_HOST_PROCESS_STREAM")
    for key in ("ProcessRoot", "StdoutLog", "StderrLog"):
        value = _canonical_abs_path(proc[key], "P9_HOST_PROCESS_CLEANUP", exists=False)
        if value.exists():
            _reject("P9_HOST_PROCESS_CLEANUP")
    return proc, float(wall)


def _audit_summary(value: Any, pins_sha: str, source_sha: str) -> dict[str, Any]:
    keys = ("status", "pinsSha256", "sourceInventorySha256", "existingFiles", "directFiles",
            "derivedFiles", "totalFiles", "productionFiles", "topFiles")
    summary = _exact_keys(value, keys, "P9_SOURCE_CLOSURE")
    expected = ("PASS_Q100_RESERVATION_INPUTS_ONLY", pins_sha, source_sha, 3437, 17, 2, 3456, 850, 27)
    if tuple(summary[key] for key in keys) != expected:
        _reject("P9_SOURCE_CLOSURE")
    for key in keys[3:]:
        _exact_int(summary[key], "P9_SOURCE_CLOSURE")
    return summary


def _parse_prefixed_stream(text: Any, prefix: str, code: str, *, allow_preamble: bool = False,
                           allow_crlf: bool = False) -> dict[str, Any]:
    if type(text) is not str:
        _reject(code)
    try:
        raw = text.encode("utf-8", "strict")
        marker = prefix.encode("ascii") + b" "
    except (UnicodeError, AttributeError):
        _reject(code)
    if len(raw) > MAX_LINE_BYTES or not (raw.endswith(b"\n") or (allow_crlf and raw.endswith(b"\r\n"))):
        _reject(code)
    lines = raw.splitlines(keepends=True)
    found = []
    for index, line in enumerate(lines):
        body = line[:-2] if line.endswith(b"\r\n") else line[:-1] if line.endswith(b"\n") else line
        if body.startswith(marker):
            found.append((index, body[len(marker):]))
    if len(found) != 1:
        _reject(code)
    index, payload = found[0]
    if (not allow_preamble and index != 0) or any(line not in (b"\n", b"\r\n") for line in lines[index + 1:]):
        _reject(code)
    value = _compact_json(payload, code)
    if type(value) is not dict:
        _reject(code)
    return value


def _validate_host_launch(host: Mapping[str, Any], data: Mapping[str, bytes],
                          context: Mapping[str, Any], parent: Mapping[str, Any],
                          output_root: Path, pins_sha: str, source_sha: str,
                          wire_sha: str) -> None:
    host_identity = host["host"]
    launch = _strict_document(data["host-launch-intent.json"], "P9_HOST_LAUNCH_INTENT")
    ext = ("mode", "script", "scriptSha256", "outputCreationTicks", "sourceParent", "p8PrerequisiteSha256")
    _exact_keys(launch, _HOST_INTENT_KEYS + ext, "P9_HOST_LAUNCH_INTENT", ordered=True)
    owner = _exact_keys(launch["owner"], ("pid", "startTicks", "executable", "executableSha256"),
                        "P9_HOST_LAUNCH_OWNER", ordered=True)
    process = parent["process"]
    expected_args = ["-NoLogo", "-NoProfile", "-NonInteractive", "-File", context["pins"]["roles"]["process"]["host"],
                     "-RepositoryRoot", context["pins"]["repositoryRoot"], "-InputPinsPath", context["pinsPath"],
                     "-InputPinsSha256", pins_sha, "-OutputRoot", str(output_root),
                     "-JavaHome", context["pins"]["toolPaths"]["javaHome"], "-PythonExe", context["pins"]["toolPaths"]["pythonExe"],
                     "-Mode", host["mode"]]
    if (launch["schema"] != "tsj-q100-reservation-provenance-launch-v1" or launch["role"] != "host"
            or launch["executable"] != host_identity["executable"] or launch["executableSha256"] != host_identity["executableSha256"]
            or launch["arguments"] != expected_args or process["Arguments"] != expected_args
            or launch["limitMs"] != 1_800_000 or launch["inputSha256"] != pins_sha
            or launch["sourceSha256"] != source_sha or launch["outputRoot"] != str(output_root)
            or launch["mode"] != host["mode"] or launch["script"] != host_identity["script"]
            or launch["scriptSha256"] != host_identity["scriptSha256"]
            or launch["sourceParent"] != host["sourceParent"]
            or launch["p8PrerequisiteSha256"] != host["p8PrerequisiteSha256"]):
        _reject("P9_HOST_LAUNCH_BINDING")
    if (owner["pid"] != host_identity["pid"] or owner["startTicks"] != host_identity["startTicks"]
            or owner["executable"] != host_identity["executable"]
            or owner["executableSha256"] != host_identity["executableSha256"]
            or process["ProcessId"] != owner["pid"] or process["ProcessStartTicks"] != owner["startTicks"]):
        _reject("P9_HOST_LAUNCH_OWNER")
    _exact_int(launch["outputCreationTicks"], "P9_HOST_LAUNCH_BINDING")
    if (launch["script"] != context["pins"]["roles"]["process"]["host"]
            or launch["scriptSha256"] != host_identity["scriptSha256"]
            or launch["outputCreationTicks"] <= 0):
        _reject("P9_HOST_LAUNCH_BINDING")
    # Bind the recorded .NET creation tick to the actual directory's Win32
    # FILETIME. This is a read-only identity check on the user-selected output.
    _verify_directory_creation_ticks(output_root, launch["outputCreationTicks"])


def _verify_directory_creation_ticks(path: Path, expected_ticks: int) -> None:
    if os.name != "nt":
        _reject("P9_OUTPUT_CREATION_TIME")
    try:
        import ctypes
        from ctypes import wintypes
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                      wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
        kernel.CreateFileW.restype = wintypes.HANDLE
        kernel.GetFileTime.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.FILETIME),
                                      ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME)]
        kernel.GetFileTime.restype = wintypes.BOOL
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        handle = kernel.CreateFileW(
            str(path), 0x80, 0x00000001 | 0x00000002 | 0x00000004, None, 3, 0x02000000, None
        )
        invalid = wintypes.HANDLE(-1).value
        if handle is None or handle == invalid:
            _reject("P9_OUTPUT_CREATION_TIME")
        created = wintypes.FILETIME()
        accessed = wintypes.FILETIME()
        written = wintypes.FILETIME()
        try:
            ok = kernel.GetFileTime(handle, ctypes.byref(created), ctypes.byref(accessed), ctypes.byref(written))
        finally:
            closed = kernel.CloseHandle(handle)
        if not ok or not closed:
            _reject("P9_OUTPUT_CREATION_TIME")
        filetime = (created.dwHighDateTime << 32) | created.dwLowDateTime
        dotnet_ticks = filetime + 504911232000000000
    except ReaderError:
        raise
    except Exception:
        _reject("P9_OUTPUT_CREATION_TIME")
    if dotnet_ticks != expected_ticks:
        _reject("P9_OUTPUT_CREATION_TIME")


def _validate_scratch(data: Mapping[str, bytes], host: Mapping[str, Any], output_root: Path,
                      context: Mapping[str, Any]) -> None:
    intent = _strict_document(data["scratch-intent.json"], "P9_SCRATCH_INTENT")
    _exact_keys(intent, ("schema", "status", "path", "owner", "suiteId"), "P9_SCRATCH_INTENT", ordered=True)
    owner = _exact_keys(intent["owner"], ("pid", "startTicks", "executable", "executableSha256"),
                        "P9_SCRATCH_OWNER", ordered=True)
    cleanup = host["cleanup"]
    _exact_keys(cleanup, ("scratchPath", "scratchCreationTicks", "scratchRemoved", "boundMs", "formula"),
                "P9_HOST_CLEANUP", ordered=True)
    if (intent["schema"] != "tsj-q100-reservation-provenance-scratch-v1"
            or intent["status"] != "INTENT_NOT_CREATION_PROOF" or intent["suiteId"] != host["suiteId"]
            or intent["path"] != cleanup["scratchPath"] or type(intent["path"]) is not str):
        _reject("P9_SCRATCH_BINDING")
    scratch = _canonical_abs_path(intent["path"], "P9_SCRATCH_BINDING", exists=False)
    if scratch.exists() or not _under(scratch, Path(tempfile.gettempdir()).resolve()):
        _reject("P9_SCRATCH_CLEANUP")
    for forbidden in (output_root, Path(context["p9Root"]), Path(context["pins"]["repositoryRoot"])):
        if _under(scratch, forbidden) or _under(forbidden, scratch):
            _reject("P9_SCRATCH_BINDING")
    if (owner["pid"] != host["host"]["pid"] or owner["startTicks"] != host["host"]["startTicks"]
            or owner["executable"] != host["host"]["executable"]
            or owner["executableSha256"] != host["host"]["executableSha256"]):
        _reject("P9_SCRATCH_OWNER")
    ticks = cleanup["scratchCreationTicks"]
    if type(ticks) is not str or re.fullmatch(r"[1-9][0-9]{0,18}", ticks) is None:
        _reject("P9_SCRATCH_CLEANUP")
    if cleanup["scratchRemoved"] is not True or type(cleanup["scratchRemoved"]) is not bool:
        _reject("P9_SCRATCH_CLEANUP")


def _validate_compiler_and_bridges(host: Mapping[str, Any], data: Mapping[str, bytes],
                                   context: Mapping[str, Any]) -> None:
    compiler = _exact_keys(host["compiler"], ("sourceCount", "sourceList", "sourceListBytes", "sourceListSha256"),
                           "P9_COMPILER_RESULT", ordered=True)
    expected_paths = context["auditor"].expected_compiler_sources(context, host["cleanup"]["scratchPath"])
    expected_raw = "".join('"' + path.replace("\\", "/") + '"' + os.linesep for path in expected_paths).encode("ascii")
    raw = data["compiler-sources.txt"]
    if (compiler["sourceCount"] != 1085 or compiler["sourceList"] != "compiler-sources.txt"
            or compiler["sourceListBytes"] != len(raw) or compiler["sourceListSha256"] != _sha256(raw)
            or raw != expected_raw):
        _reject("P9_COMPILER_SOURCE_LIST")
    bridges = _exact_keys(host["bridgeCopies"], ("receipt", "count", "verifiedAfterCompile", "verifiedBeforeNative", "verifiedAfterNative"),
                          "P9_BRIDGE_COPIES_RESULT", ordered=True)
    if (bridges["receipt"] != "bridge-copies.json" or bridges["count"] != 3
            or any(type(bridges[k]) is not bool or bridges[k] is not True
                   for k in ("verifiedAfterCompile", "verifiedBeforeNative", "verifiedAfterNative"))):
        _reject("P9_BRIDGE_COPIES_RESULT")
    parsed = _strict_document(data["bridge-copies.json"], "P9_BRIDGE_COPIES")
    if type(parsed) is not list or len(parsed) != 3:
        _reject("P9_BRIDGE_COPIES")
    expected = {row["name"]: row for row in context["bridges"]}
    actual: dict[str, Any] = {}
    scratch_parents = set()
    for raw_row in parsed:
        row = _exact_keys(raw_row, ("path", "bytes", "sha256"), "P9_BRIDGE_COPIES", ordered=True)
        if type(row["path"]) is not str or type(row["bytes"]) is not int or row["sha256"] not in {x["sha256"] for x in expected.values()}:
            _reject("P9_BRIDGE_COPIES")
        name = Path(row["path"]).name
        exp = expected.get(name)
        if exp is None or name in actual or row["bytes"] != exp["bytes"] or row["sha256"] != exp["sha256"]:
            _reject("P9_BRIDGE_COPIES")
        path = _canonical_abs_path(row["path"], "P9_BRIDGE_COPIES", exists=False)
        scratch_parents.add(str(path.parent))
        if not _under(path, Path(tempfile.gettempdir()).resolve()):
            _reject("P9_BRIDGE_COPIES")
        actual[name] = row
    if set(actual) != set(expected) or len(scratch_parents) != 1:
        _reject("P9_BRIDGE_COPIES")


def _validate_canaries_and_preflight(host: Mapping[str, Any], data: Mapping[str, bytes],
                                     process_raw: Mapping[str, Mapping[str, Any]],
                                     pins_sha: str, source_sha: str,
                                     mode: str) -> None:
    canaries = _exact_keys(host["canaries"], ("p6", "p8", "p9", "preflight"), "P9_CANARY_RESULT", ordered=True)
    p6_text = process_raw["p6Canary"]["Stdout"]
    p8_text = process_raw["p8Canary"]["Stdout"]
    p9_text = process_raw["p9Canary"]["Stdout"]
    p6 = _parse_prefixed_stream(p6_text, "CANONICAL_PREFIX_STAGE_CANARY_COMPLETE", "P9_P6_CANARY_MARKER", allow_preamble=True)
    p8 = _parse_prefixed_stream(p8_text, "P8_GEOMETRIC_REASON_CANARY_COMPLETE", "P9_P8_CANARY_MARKER")
    p9 = _parse_prefixed_stream(p9_text, "P9_RESERVATION_CANARY_COMPLETE", "P9_P9_CANARY_MARKER")
    if (canaries["p6"] != p6 or canaries["p8"] != p8 or canaries["p9"] != p9):
        _reject("P9_CANARY_RESULT_BINDING")
    _exact_keys(p6, ("schema", "status", "assertions", "blockerAssertions", "stageAssertions", "elapsedNanos",
                      "groups", "maximumGroupNanos", "layoutQualification"), "P9_P6_CANARY_FIELDS")
    if (p6["schema"] != "tsj-canonical-prefix-stage-canary-v1" or p6["status"] != "PASS"
            or type(p6["layoutQualification"]) is not bool or p6["layoutQualification"]):
        _reject("P9_P6_CANARY_STATUS")
    for key, low, high in (("assertions", 1, 2**63-1), ("blockerAssertions", 1, 2**63-1),
                           ("stageAssertions", 1, 2**63-1), ("elapsedNanos", 0, 90_000_000_000),
                           ("groups", 1, 640), ("maximumGroupNanos", 0, 5_000_000_000)):
        value = _exact_int(p6[key], "P9_P6_CANARY_FIELDS")
        if not low <= value <= high:
            _reject("P9_P6_CANARY_FIELDS")
    _exact_keys(p8, ("schema", "status", "assertions", "caseCount", "workNanos", "groups", "maximumGroupNanos",
                     "totalObservationUnits", "cases", "layoutQualification"), "P9_P8_CANARY_FIELDS")
    if (p8["schema"] != "tsj-q100-geometric-reason-canary-v1"
            or p8["status"] != "PASS" or p8["layoutQualification"] is not False
            or type(p8["cases"]) is not list or len(p8["cases"]) != p8["caseCount"]):
        _reject("P9_P8_CANARY_STATUS")
    for key, high in (("assertions", 2**63-1), ("caseCount", 2**31-1), ("workNanos", 90_000_000_000),
                      ("groups", 640), ("maximumGroupNanos", 5_000_000_000), ("totalObservationUnits", 32768)):
        if not 0 <= _exact_int(p8[key], "P9_P8_CANARY_FIELDS") <= high:
            _reject("P9_P8_CANARY_FIELDS")
    _exact_keys(p9, ("schema", "status", "passed", "cases", "layoutQualification"), "P9_P9_CANARY_FIELDS")
    if (p9["schema"] != "tsj-q100-reservation-canary-v1" or p9["status"] != "PASS_LITERAL_NATIVE_ADAPTER_ONLY"
            or p9["passed"] != 8 or type(p9["passed"]) is not int or p9["layoutQualification"] is not False
            or p9["cases"] != list(_HOST_CANARY_IDS)):
        _reject("P9_P9_CANARY_STATUS")
    preflight = canaries["preflight"]
    if type(preflight) is not list or len(preflight) != 5:
        _reject("P9_PREFLIGHT_CENSUS")
    control_run = "00000000-0000-4000-8000-000000000009"
    for row, (role, (stem, failure, arg_index)) in zip(preflight, _HOST_NEGATIVE_ROLES.items()):
        _exact_keys(row, ("name", "expectedFailureCode", "observedFailureCode", "argIndex", "exitCode",
                          "terminationProven", "dataDirectoryUnchanged", "p9DirectoryUnchanged"), "P9_PREFLIGHT_ROW")
        expected_name = {"badMode": "P9_BAD_MODE", "badUuid": "P9_BAD_UUID", "badInputSha": "P9_BAD_INPUT_SHA",
                         "nonemptyData": "P9_NONEMPTY_DATA", "nonemptyP9": "P9_NONEMPTY_P9"}[role]
        if (row["name"] != expected_name or row["expectedFailureCode"] != failure or row["observedFailureCode"] != failure
                or type(row["argIndex"]) is not int or row["argIndex"] != arg_index
                or type(row["exitCode"]) is not int or row["exitCode"] != 1
                or any(type(row[k]) is not bool or not row[k] for k in ("terminationProven", "dataDirectoryUnchanged", "p9DirectoryUnchanged"))):
            _reject("P9_PREFLIGHT_ROW")
        raw = process_raw[role]
        if raw["ExitCode"] != 1 or raw["Stdout"] != "":
            _reject("P9_PREFLIGHT_PROCESS")
        marker = _parse_prefixed_stream(raw["Stderr"], "P9_RESERVATION_PROVENANCE_PREFLIGHT_REJECT", "P9_PREFLIGHT_MARKER")
        _exact_keys(marker, ("schema", "phase", "code", "argIndex"), "P9_PREFLIGHT_MARKER")
        if (marker["schema"] != "tsj-q100-reservation-provenance-preflight-v1" or marker["phase"] != "PREFLIGHT"
                or marker["code"] != failure or type(marker["argIndex"]) is not int or marker["argIndex"] != arg_index):
            _reject("P9_PREFLIGHT_MARKER")
    if data["controls/P9_NONEMPTY_DATA/data/sentinel.txt"] != b"P9_PREEXISTING_DATA_DO_NOT_CHANGE\n" or data[
            "controls/P9_NONEMPTY_P9/p9/sentinel.txt"] != b"P9_PREEXISTING_DATA_DO_NOT_CHANGE\n":
        _reject("P9_PREFLIGHT_SENTINEL")


def _validate_operations(host: Mapping[str, Any], mode: str, output_root: Path,
                         pins_sha: str, source_sha: str, wire_sha: str,
                         data: Mapping[str, bytes]) -> list[dict[str, Any]]:
    operations = host["operations"]
    if mode == "CANARY":
        if type(operations) is not list or operations:
            _reject("P9_HOST_OPERATION_CENSUS")
        return []
    if type(operations) is not list or len(operations) != 5:
        _reject("P9_HOST_OPERATION_CENSUS")
    result = []
    seen = set()
    for row, expected in zip(operations, RUN_ORDER):
        _exact_keys(row, ("wrapperMode", "p8Mode", "runId", "disposition", "processKey", "p8DataDirectory",
                          "p9Directory", "workNanos", "groups", "observationUnits", "p9Index"),
                    "P9_HOST_OPERATION_FIELDS", ordered=True)
        wrapper, p8_mode, disposition = expected
        expected_role = {"NULL": "nullMode", "OBSERVE": "observeMode", "TAP_THROW": "tapThrow",
                         "CALLBACK_THROW": "callbackThrow", "REASON_THROW": "reasonThrow"}[wrapper]
        run_id = row["runId"]
        if (row["wrapperMode"] != wrapper or row["p8Mode"] != p8_mode or row["disposition"] != disposition
                or row["processKey"] != expected_role or type(run_id) is not str
                or re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", run_id) is None
                or run_id in seen):
            _reject("P9_HOST_OPERATION_BINDING")
        seen.add(run_id)
        p8_dir = f"runs/{wrapper}/data"
        p9_dir = f"runs/{wrapper}/p9" if wrapper in NORMAL_MODES else None
        if row["p8DataDirectory"] != p8_dir or row["p9Directory"] != p9_dir:
            _reject("P9_HOST_OPERATION_BINDING")
        _exact_int(row["groups"], "P9_HOST_OPERATION_BINDING")
        _exact_int(row["observationUnits"], "P9_HOST_OPERATION_BINDING")
        if row["groups"] < 0 or row["groups"] > 640 or row["observationUnits"] < 0 or row["observationUnits"] > 32768:
            _reject("P9_HOST_OPERATION_BINDING")
        if wrapper == "CALLBACK_THROW":
            if row["workNanos"] is not None or row["p9Index"] is not None:
                _reject("P9_HOST_OPERATION_BINDING")
        else:
            _exact_int(row["workNanos"], "P9_HOST_OPERATION_BINDING")
            if not 0 <= row["workNanos"] <= 90_000_000_000:
                _reject("P9_HOST_OPERATION_BINDING")
        if wrapper in NORMAL_MODES:
            index_raw = data[f"runs/{wrapper}/p9/reservation-index.json"]
            _relative_ref(row["p9Index"], f"runs/{wrapper}/p9/reservation-index.json", index_raw,
                          "P9_HOST_OPERATION_INDEX")
        elif row["p9Index"] is not None:
            _reject("P9_HOST_OPERATION_INDEX")
        result.append(row)
    return result


_HOST_LIMITS = {
    "compileMs": 60000, "auditMs": 60000, "nativeMs": 480000, "readerMs": 180000,
    "outerMs": 1800000, "cleanupMs": 120000, "workNanos": 90000000000,
    "groups": 640, "groupNanos": 5000000000, "observationUnits": 32768,
    "cloneEntries": 1048576, "chunkEntries": 4096, "expansions": 1000000,
    "holes": 48, "flips": 4, "operationBytes": 8388608,
    "hostEvidenceBytes": 8388608, "suiteBytes": 41943040, "lineBytes": 65536,
    "compilerListBytes": 1048576, "inventoryBytes": 1048576,
}


def _finite_number(value: Any, code: str, low: float = 0.0, high: float = 1_800_000.0) -> float:
    if (type(value) not in (int, float) or type(value) is bool
            or (type(value) is float and not math.isfinite(value)) or not low <= value <= high):
        _reject(code)
    return float(value)


def _validate_host_timing(host: Mapping[str, Any], process_walls: Mapping[str, float],
                          parent_elapsed: float, mode: str, operations: Sequence[Mapping[str, Any]],
                          indices: Mapping[str, Mapping[str, Any]]) -> dict[str, Any]:
    timing_keys = ("nativePhaseStartOffsetMs", "cleanupStartOffsetMs", "nativePhaseElapsedMs",
                   "childWallSumMs", "workCreditsMs", "nativePhaseChargeMs",
                   "nativePhaseOverheadMs", "hostElapsedBeforeFinalWriteMs")
    timing = _exact_keys(host["timing"], timing_keys, "P9_HOST_TIMING", ordered=True)
    values = {key: _finite_number(timing[key], "P9_HOST_TIMING") for key in timing_keys}
    start = values["nativePhaseStartOffsetMs"]
    cleanup_start = values["cleanupStartOffsetMs"]
    phase = values["nativePhaseElapsedMs"]
    child_sum = values["childWallSumMs"]
    credits = values["workCreditsMs"]
    charge = values["nativePhaseChargeMs"]
    overhead = values["nativePhaseOverheadMs"]
    before_final = values["hostElapsedBeforeFinalWriteMs"]
    if not (0 <= start <= cleanup_start <= before_final <= parent_elapsed <= 1_800_000):
        _reject("P9_HOST_TIMING_ORDER")
    native_roles = ["p6Canary", "p8Canary", "p9Canary", "badMode", "badUuid", "badInputSha", "nonemptyData", "nonemptyP9"]
    if mode == "SUITE":
        native_roles.extend(("nullMode", "observeMode", "tapThrow", "callbackThrow", "reasonThrow"))
    actual_child_sum = sum(process_walls[role] for role in native_roles)
    if (not math.isclose(phase, cleanup_start - start, rel_tol=0.0, abs_tol=0.002)
            or not math.isclose(child_sum, actual_child_sum, rel_tol=0.0, abs_tol=0.002)
            or phase < child_sum or not math.isclose(overhead, phase - child_sum, rel_tol=0.0, abs_tol=0.002)):
        _reject("P9_HOST_TIMING_INTERVAL")
    actual_credit_nanos = sum(indices[row["wrapperMode"]]["work"]["creditedWorkNanos"]
                              for row in operations if row["wrapperMode"] in ("NULL", "OBSERVE"))
    actual_credits = actual_credit_nanos / 1_000_000.0
    if not math.isclose(credits, actual_credits, rel_tol=0.0, abs_tol=0.002):
        _reject("P9_HOST_TIMING_CREDIT")
    if credits < 0 or credits > child_sum or charge < 0 or not math.isclose(charge, phase - credits, rel_tol=0.0, abs_tol=0.002):
        _reject("P9_HOST_TIMING_CHARGE")
    residuals = []
    for role in native_roles:
        credit = (indices["NULL"]["work"]["creditedWorkNanos"] / 1_000_000.0 if role == "nullMode" else
                  indices["OBSERVE"]["work"]["creditedWorkNanos"] / 1_000_000.0 if role == "observeMode" else 0.0)
        if process_walls[role] + 0.002 < credit:
            _reject("P9_HOST_WORK_CREDIT_WALL")
        residuals.append(process_walls[role] - credit)
    conservative_charge = max(charge, phase - credits, sum(residuals))
    cleanup = _exact_keys(host["cleanup"], ("scratchPath", "scratchCreationTicks", "scratchRemoved", "boundMs", "formula"),
                          "P9_HOST_CLEANUP", ordered=True)
    if (type(cleanup["boundMs"]) is not int or cleanup["boundMs"] != 120000
            or cleanup["formula"] != "nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)"
            or cleanup["scratchRemoved"] is not True or type(cleanup["scratchRemoved"]) is not bool):
        _reject("P9_HOST_CLEANUP")
    upper = conservative_charge + (parent_elapsed - cleanup_start)
    if upper > 120000:
        _reject("P9_HOST_CLEANUP_BOUND")
    return {"boundMs": 120000, "nativePhaseChargeMs": conservative_charge,
            "finalHostTailMs": parent_elapsed - cleanup_start, "upperBoundMs": upper,
            "processRootsAbsent": True, "scratchRemoved": True, "cleanupStartOffsetMs": cleanup_start}


def _validate_limits(value: Any) -> dict[str, Any]:
    limits = _exact_keys(value, tuple(_HOST_LIMITS), "P9_HOST_LIMITS", ordered=True)
    for key, expected in _HOST_LIMITS.items():
        if type(limits[key]) is not int or limits[key] != expected:
            _reject("P9_HOST_LIMITS")
    return limits


def _validate_source_processes(host: Mapping[str, Any], process_raw: Mapping[str, Mapping[str, Any]],
                               pins_sha: str, source_sha: str) -> None:
    checks = host["sourceChecks"]
    for role, key in (("preflight", "preflight"), ("preNative", "preNative"), ("postSource", "postSource")):
        proc = process_raw[role]
        if proc["ExitCode"] != 0 or proc["Stderr"] != "":
            _reject("P9_SOURCE_PROCESS")
        marker = _parse_prefixed_stream(proc["Stdout"], "PASS_Q100_RESERVATION_INPUTS_ONLY", "P9_SOURCE_MARKER")
        _audit_summary(marker, pins_sha, source_sha)
        if marker != checks[key]:
            _reject("P9_SOURCE_MARKER_BINDING")


def _validate_host_disjointness(output_root: Path, pins_path: Path, parent_path: Path,
                                context: Mapping[str, Any]) -> None:
    temp = Path(tempfile.gettempdir()).resolve()
    if not _under(output_root, temp) or os.path.normcase(str(output_root)) == os.path.normcase(str(temp)):
        _reject("P9_OUTPUT_ROOT_CONFINEMENT")
    if _under(parent_path, output_root) or _under(output_root, parent_path):
        _reject("P9_PARENT_OUTPUT_ALIAS")
    forbidden = [Path(context["pins"]["repositoryRoot"]), Path(context["p9Root"]), Path(pins_path),
                 Path(context["inventoryPath"])]
    forbidden.extend(Path(value) for key, value in context["roots"].items() if key != "P9")
    for candidate in forbidden:
        if _under(output_root, candidate) or _under(candidate, output_root):
            _reject("P9_OUTPUT_SOURCE_ALIAS")
    for candidate in (pins_path, Path(context["inventoryPath"])):
        if _under(candidate, output_root):
            _reject("P9_OUTPUT_INPUT_ALIAS")
    for current in (output_root, *output_root.parents):
        try:
            info = current.lstat()
        except OSError:
            _reject("P9_OUTPUT_PATH")
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            _reject("P9_OUTPUT_REPARSE")


def _artifact_identity(rel: str) -> tuple[str, str]:
    if rel == "controls/P9_NONEMPTY_DATA/data/sentinel.txt":
        return "CONTROL_PAYLOAD", "HOST"
    if rel == "controls/P9_NONEMPTY_P9/p9/sentinel.txt":
        return "CONTROL_PAYLOAD", "HOST"
    match = re.fullmatch(r"runs/(NULL|OBSERVE|TAP_THROW|CALLBACK_THROW|REASON_THROW)\.(stdout|stderr)", rel)
    if match:
        return ("STDOUT" if match.group(2) == "stdout" else "STDERR"), match.group(1)
    if rel.startswith("runs/"):
        parts = rel.split("/")
        mode = parts[1]
        if len(parts) == 3 and parts[2] == "launch-intent.json":
            return "HOST_INTENT", mode
        if len(parts) == 3 and parts[2] == "process.json":
            return "PROCESS_RECEIPT", mode
        if len(parts) == 4 and parts[2] == "p9":
            if parts[3] == "reservation-operands.jsonl":
                return "NATIVE_PAYLOAD", mode
            if parts[3] == "reservation-index.json":
                return "NATIVE_INDEX", mode
        if len(parts) == 4 and parts[2] == "data":
            return ("NATIVE_INDEX" if parts[3] in
                    ("canonical-stage-index.json", "canonical-geometric-prefix-index.json") else "NATIVE_PAYLOAD"), mode
        raise ValueError("unknown runs artifact")
    if rel.startswith("source-"):
        if rel.endswith("-process.json"):
            return "PROCESS_RECEIPT", "HOST"
        if rel.endswith(".stdout"):
            return "STDOUT", "HOST"
        if rel.endswith(".stderr"):
            return "STDERR", "HOST"
    for role, stem in _HOST_NATIVE_STEMS:
        if rel == f"{stem}-launch-intent.json":
            return "HOST_INTENT", "HOST"
        if rel == f"{stem}-process.json":
            return "PROCESS_RECEIPT", "HOST"
        if rel == f"{stem}.stdout":
            return "STDOUT", "HOST"
        if rel == f"{stem}.stderr":
            return "STDERR", "HOST"
    if rel in ("host-launch-intent.json", "scratch-intent.json", "compiler-launch-intent.json"):
        return "HOST_INTENT", "HOST"
    if rel == "compiler-process.json":
        return "PROCESS_RECEIPT", "HOST"
    if rel == "compiler.stdout":
        return "STDOUT", "HOST"
    if rel == "compiler.stderr":
        return "STDERR", "HOST"
    if rel == "compiler-sources.txt":
        return "COMPILER_INPUT", "HOST"
    if rel == "bridge-copies.json":
        return "BRIDGE_COPIES", "HOST"
    raise ValueError("unknown output artifact")


def _validate_output_indexes(host: Mapping[str, Any], data: Mapping[str, bytes],
                             expected_files: set[str], expected_dirs: set[str],
                             pins_sha: str, source_sha: str, wire_sha: str,
                             marker: Mapping[str, Any]) -> dict[str, Any]:
    index_raw = data["suite-index.json"]
    footer_raw = data["suite-footer.json"]
    result_raw = data["host-result.json"]
    index = _strict_document(index_raw, "P9_OUTPUT_INDEX")
    footer = _strict_document(footer_raw, "P9_OUTPUT_FOOTER")
    index_keys = ("schema", "status", "mode", "suiteId", "inputSha256", "sourceSha256", "wireSha256",
                  "modeOrder", "artifactCount", "aggregateBytes", "artifacts", "directories", "layoutQualification")
    footer_keys = ("schema", "status", "mode", "suiteId", "inputSha256", "sourceSha256", "wireSha256",
                   "modeOrder", "suiteIndex", "artifactCount", "aggregateBytes", "directoryCount", "layoutQualification")
    _exact_keys(index, index_keys, "P9_OUTPUT_INDEX_FIELDS", ordered=True)
    _exact_keys(footer, footer_keys, "P9_OUTPUT_FOOTER_FIELDS", ordered=True)
    mode = host["mode"]
    expected_order = list(_HOST_MODE_ORDER + (("NULL", "OBSERVE", "TAP_THROW", "CALLBACK_THROW", "REASON_THROW") if mode == "SUITE" else ()))
    identity = ("tsj-q100-reservation-provenance-suite-index-v1", "HOST_INDEX_COMPLETE", mode,
                host["suiteId"], pins_sha, source_sha, wire_sha, expected_order)
    if tuple(index[k] for k in ("schema", "status", "mode", "suiteId", "inputSha256", "sourceSha256", "wireSha256", "modeOrder")) != identity:
        _reject("P9_OUTPUT_INDEX_BINDING")
    footer_identity = ("tsj-q100-reservation-provenance-suite-footer-v1", "HOST_OUTPUT_CLOSED", mode,
                      host["suiteId"], pins_sha, source_sha, wire_sha, expected_order)
    if tuple(footer[k] for k in ("schema", "status", "mode", "suiteId", "inputSha256", "sourceSha256", "wireSha256", "modeOrder")) != footer_identity:
        _reject("P9_OUTPUT_FOOTER_BINDING")
    for item in (index["layoutQualification"], footer["layoutQualification"]):
        if type(item) is not bool or item:
            _reject("P9_OUTPUT_SCOPE")
    indexed_paths = expected_files - {"host-result.json", "suite-index.json", "suite-footer.json"}
    artifact_rows = index["artifacts"]
    if type(artifact_rows) is not list or len(artifact_rows) != len(indexed_paths):
        _reject("P9_OUTPUT_ARTIFACT_CENSUS")
    if [row.get("path") if type(row) is dict else None for row in artifact_rows] != sorted(indexed_paths):
        _reject("P9_OUTPUT_ARTIFACT_ORDER")
    artifact_total = 0
    for row in artifact_rows:
        _exact_keys(row, ("path", "role", "operation", "bytes", "sha256"), "P9_OUTPUT_ARTIFACT_FIELDS", ordered=True)
        rel = _relative_path(row["path"], "P9_OUTPUT_ARTIFACT_PATH")
        try:
            expected_role, expected_operation = _artifact_identity(rel)
        except ValueError:
            _reject("P9_OUTPUT_ARTIFACT_ROLE")
        raw = data[rel]
        if (row["role"] != expected_role or row["operation"] != expected_operation
                or type(row["bytes"]) is not int or row["bytes"] != len(raw)
                or row["sha256"] != _sha256(raw)):
            _reject("P9_OUTPUT_ARTIFACT_BINDING")
        artifact_total += len(raw)
    directories = sorted(expected_dirs)
    if (index["directories"] != directories or type(index["artifactCount"]) is not int
            or index["artifactCount"] != len(artifact_rows) or type(index["aggregateBytes"]) is not int
            or index["aggregateBytes"] != artifact_total):
        _reject("P9_OUTPUT_INDEX_TOTALS")
    suite_index_ref = _relative_ref(footer["suiteIndex"], "suite-index.json", index_raw, "P9_OUTPUT_FOOTER_REF")
    if (type(footer["artifactCount"]) is not int or footer["artifactCount"] != len(artifact_rows)
            or type(footer["aggregateBytes"]) is not int or footer["aggregateBytes"] != artifact_total
            or type(footer["directoryCount"]) is not int or footer["directoryCount"] != len(expected_dirs)):
        _reject("P9_OUTPUT_FOOTER_TOTALS")

    closure = _exact_keys(host["outputClosure"], ("files", "directories", "aggregateBytes", "hostResultExcluded"),
                          "P9_OUTPUT_CLOSURE_FIELDS", ordered=True)
    _exact_bool(closure["hostResultExcluded"], "P9_OUTPUT_CLOSURE_FIELDS")
    if closure["hostResultExcluded"] is not True or closure["directories"] != directories:
        _reject("P9_OUTPUT_CLOSURE_BINDING")
    closure_paths = expected_files - {"host-result.json"}
    files = closure["files"]
    if type(files) is not list or len(files) != len(closure_paths):
        _reject("P9_OUTPUT_CLOSURE_CENSUS")
    if [r.get("name") if type(r) is dict else None for r in files] != sorted(closure_paths):
        _reject("P9_OUTPUT_CLOSURE_ORDER")
    closure_total = 0
    for row in files:
        _exact_keys(row, ("name", "bytes", "sha256"), "P9_OUTPUT_CLOSURE_FIELDS", ordered=True)
        rel = _relative_path(row["name"], "P9_OUTPUT_CLOSURE_PATH")
        raw = data[rel]
        if (type(row["bytes"]) is not int or row["bytes"] != len(raw) or row["sha256"] != _sha256(raw)):
            _reject("P9_OUTPUT_CLOSURE_BINDING")
        closure_total += len(raw)
    if type(closure["aggregateBytes"]) is not int or closure["aggregateBytes"] != closure_total:
        _reject("P9_OUTPUT_CLOSURE_TOTALS")
    total_size = closure_total + len(result_raw)
    if total_size > 40 * 1024 * 1024:
        _reject("P9_OUTPUT_SUITE_LIMIT")
    _relative_ref(marker["suiteIndex"], "suite-index.json", index_raw, "P9_PARENT_MARKER_BINDING")
    _relative_ref(marker["suiteFooter"], "suite-footer.json", footer_raw, "P9_PARENT_MARKER_BINDING")
    # Each operation and the host partition has its own inclusive cap.
    operation_names = ("NULL", "OBSERVE", "TAP_THROW", "CALLBACK_THROW", "REASON_THROW") if mode == "SUITE" else ()
    for operation in operation_names:
        size = sum(len(raw) for rel, raw in data.items() if rel == f"runs/{operation}.stdout"
                   or rel == f"runs/{operation}.stderr" or rel.startswith(f"runs/{operation}/"))
        if size > MAX_OPERAND_BYTES:
            _reject("P9_OPERATION_OUTPUT_LIMIT")
    operation_paths = {rel for rel in expected_files if rel.startswith("runs/")}
    host_size = sum(len(raw) for rel, raw in data.items() if rel not in operation_paths)
    if host_size > MAX_OPERAND_BYTES:
        _reject("P9_HOST_EVIDENCE_LIMIT")
    return {"index": index, "footer": footer, "suiteIndexRef": suite_index_ref,
            "aggregateBytes": closure_total, "hostResultBytes": len(result_raw),
            "directories": directories, "outputClosure": closure}


def _validate_host_identity(host: Mapping[str, Any], mode: str, pins: Mapping[str, Any],
                            context: Mapping[str, Any], pins_sha: str, source_sha: str,
                            wire_sha: str) -> None:
    _exact_keys(host, _HOST_RESULT_KEYS, "P9_HOST_RESULT_FIELDS", ordered=True)
    if (host["schema"] != "tsj-q100-reservation-provenance-host-v1"
            or host["status"] != ("HOST_CANARY_COMPLETE_READER_PENDING" if mode == "CANARY" else "HOST_COMPLETE_READER_PENDING")
            or host["mode"] != mode or host["inputSha256"] != pins_sha
            or host["sourceSha256"] != source_sha or host["wireSha256"] != wire_sha
            or host["sourceParent"] != pins["sourceParent"]
            or host["p8PrerequisiteSha256"] != pins["p8Prerequisite"]["pinsSha256"]
            or type(host["suiteId"]) is not str or re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", host["suiteId"]) is None
            or host["layoutQualification"] is not False):
        _reject("P9_HOST_RESULT_BINDING")
    host_identity = _exact_keys(host["host"], ("pid", "startTicks", "executable", "executableSha256", "script", "scriptSha256"),
                                "P9_HOST_IDENTITY", ordered=True)
    _exact_int(host_identity["pid"], "P9_HOST_IDENTITY")
    _exact_int(host_identity["startTicks"], "P9_HOST_IDENTITY")
    if (host_identity["pid"] < 1 or host_identity["startTicks"] < 1
            or host_identity["executable"] != pins["toolPaths"]["powershellExe"]
            or host_identity["script"] != pins["roles"]["process"]["host"]):
        _reject("P9_HOST_IDENTITY")
    pskey = os.path.normcase(os.path.abspath(pins["toolPaths"]["powershellExe"]))
    psrow = context["rows"].get(pskey)
    scriptkey = os.path.normcase(os.path.abspath(pins["roles"]["process"]["host"]))
    scriptrow = context["rows"].get(scriptkey)
    if (type(psrow) is not dict or type(scriptrow) is not dict
            or host_identity["executableSha256"] != psrow["sha256"]
            or host_identity["scriptSha256"] != scriptrow["sha256"]):
        _reject("P9_HOST_IDENTITY")
    checks = _exact_keys(host["sourceChecks"], ("preflight", "preNative", "postSource"), "P9_SOURCE_CHECKS")
    for row in checks.values():
        _audit_summary(row, pins_sha, source_sha)


def _validate_host_process_roles(host: Mapping[str, Any], data: Mapping[str, bytes],
                                context: Mapping[str, Any], pins_sha: str,
                                source_sha: str, mode: str, host_pid: int,
                                host_ticks: int, output_root: Path) -> tuple[dict[str, dict[str, Any]], dict[str, float]]:
    process_map = host["processes"]
    expected_order = _HOST_CANARY_ORDER if mode == "CANARY" else _HOST_SUITE_ORDER
    if type(process_map) is not dict or tuple(process_map) != expected_order:
        _reject("P9_HOST_PROCESS_CENSUS")
    stem_for = {
        "preflight": "source-preflight", "preNative": "source-prenative", "postSource": "source-post",
        "compiler": "compiler",
    }
    stem_for.update({role: stem for role, stem in _HOST_NATIVE_STEMS})
    if mode == "SUITE":
        stem_for.update({"nullMode": "runs/NULL", "observeMode": "runs/OBSERVE", "tapThrow": "runs/TAP_THROW",
                         "callbackThrow": "runs/CALLBACK_THROW", "reasonThrow": "runs/REASON_THROW"})
    python = context["tools"]["pythonExe"]
    auditor = context["pins"]["roles"]["input"]["auditor"]
    java = context["tools"]["javaExe"]
    javac = context["tools"]["javacExe"]
    raw_by_role: dict[str, dict[str, Any]] = {}
    wall_by_role: dict[str, float] = {}
    classpath_value: str | None = None
    bridge_copies = _strict_document(data["bridge-copies.json"], "P9_BRIDGE_COPIES")
    if type(bridge_copies) is not list or len(bridge_copies) != 3:
        _reject("P9_BRIDGE_COPIES")
    bridge_by_name: dict[str, dict[str, Any]] = {}
    for bridge in bridge_copies:
        row = _exact_keys(bridge, ("path", "bytes", "sha256"), "P9_BRIDGE_COPIES")
        if type(row["path"]) is not str or type(row["bytes"]) is not int:
            _reject("P9_BRIDGE_COPIES")
        name = Path(row["path"]).name
        if name in bridge_by_name or name not in {item["name"] for item in context["bridges"]}:
            _reject("P9_BRIDGE_COPIES")
        expected_bridge = next(item for item in context["bridges"] if item["name"] == name)
        if row["bytes"] != expected_bridge["bytes"] or row["sha256"] != expected_bridge["sha256"]:
            _reject("P9_BRIDGE_COPIES")
        bridge_by_name[name] = row
    if len(bridge_by_name) != 3:
        _reject("P9_BRIDGE_COPIES")
    bridge_parents = {str(Path(row["path"]).parent) for row in bridge_by_name.values()}
    if len(bridge_parents) != 1:
        _reject("P9_BRIDGE_COPIES")
    scratch_root = Path(next(iter(bridge_parents)))
    if scratch_root.name == "" or not _under(scratch_root, Path(tempfile.gettempdir()).resolve()):
        _reject("P9_COMPILER_ARGUMENTS")
    classes_dir = scratch_root / "classes"
    sourcepath_dir = scratch_root / "empty-sourcepath"
    compiler_list = scratch_root / "compiler-sources.txt"
    canary_source_rows = context["canaryPins"]["sourceRows"]
    gwt_paths = []
    for relative in (".tools/gwt-2.7.0/gwt-user-2.7.0.jar", ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar"):
        matches = [r for r in canary_source_rows if type(r) is dict and r.get("path") == relative]
        if len(matches) != 1 or matches[0].get("role") != "BASE_SOURCE":
            _reject("P9_GWT_SOURCE")
        gwt_paths.append(str(Path(context["pins"]["repositoryRoot"]).joinpath(*relative.split("/"))))
    expected_classpath = ";".join((str(classes_dir), *gwt_paths))
    compiler_paths = context["auditor"].expected_compiler_sources(context, host["cleanup"]["scratchPath"])
    expected_compiler_list = "".join('"' + path.replace("\\", "/") + '"' + os.linesep for path in compiler_paths).encode("ascii")
    if data["compiler-sources.txt"] != expected_compiler_list:
        _reject("P9_COMPILER_SOURCE_LIST")
    expected_compiler_args = ["-source", "7", "-target", "7", "-encoding", "UTF-8", "-classpath",
                              expected_classpath, "-sourcepath", str(sourcepath_dir), "-d", str(classes_dir),
                              "@" + str(compiler_list)]
    for role in expected_order:
        stem = stem_for[role]
        if role in ("preflight", "preNative", "postSource"):
            receipt_rel, stdout_rel, stderr_rel = f"{stem}-process.json", f"{stem}.stdout", f"{stem}.stderr"
            exe = python
            expected_args = ["-I", "-B", auditor, context["pinsPath"], pins_sha]
            expected_exit = 0
        elif role == "compiler":
            receipt_rel, stdout_rel, stderr_rel = "compiler-process.json", "compiler.stdout", "compiler.stderr"
            exe = javac
            expected_args = expected_compiler_args
            expected_exit = 0
        else:
            receipt_rel = f"{stem}-process.json" if "/" not in stem else f"{stem}/process.json"
            stdout_rel = f"{stem}.stdout" if "/" not in stem else stem + ".stdout"
            stderr_rel = f"{stem}.stderr" if "/" not in stem else stem + ".stderr"
            exe = java
            expected_args = None
            expected_exit = 1 if role in ("badMode", "badUuid", "badInputSha", "nonemptyData", "nonemptyP9", "callbackThrow", "reasonThrow") else 0
        descriptor = _exact_keys(process_map[role], _HOST_DESCRIPTOR_KEYS, "P9_HOST_PROCESS_DESCRIPTOR")
        if (descriptor["receipt"] != receipt_rel or descriptor["stdout"] != stdout_rel
                or descriptor["stderr"] != stderr_rel):
            _reject("P9_HOST_PROCESS_DESCRIPTOR")
        intent_rel = ("host-launch-intent.json" if role == "host" else
                      f"{stem}-launch-intent.json" if "/" not in stem and role not in ("compiler", "preflight", "preNative", "postSource") else
                      f"{stem}/launch-intent.json" if "/" in stem else
                      "compiler-launch-intent.json" if role == "compiler" else
                      f"{stem}-launch-intent.json")
        persisted_intent = role not in ("preflight", "preNative", "postSource")
        if (persisted_intent and intent_rel not in data) or receipt_rel not in data:
            _reject("P9_HOST_PROCESS_DESCRIPTOR")
        intent = _strict_document(data[intent_rel], "P9_HOST_LAUNCH_INTENT") if persisted_intent else None
        if intent is not None:
            expected_intent_keys = set(_HOST_INTENT_KEYS)
            if role == "compiler":
                expected_intent_keys.update(("sourceCount", "sourceListBytes", "sourceListSha256"))
            _exact_keys(intent, expected_intent_keys, "P9_HOST_LAUNCH_INTENT")
        expected_intent_role = ("P9_BAD_MODE" if role == "badMode" else
                                       "P9_BAD_UUID" if role == "badUuid" else
                                       "P9_BAD_INPUT_SHA" if role == "badInputSha" else
                                       "P9_NONEMPTY_DATA" if role == "nonemptyData" else
                                       "P9_NONEMPTY_P9" if role == "nonemptyP9" else
                                       "compiler" if role == "compiler" else
                                       "preflight" if role == "preflight" else
                                       "preNative" if role == "preNative" else
                                       "postSource" if role == "postSource" else
                                       "P6_CANARY" if role == "p6Canary" else
                                       "P8_CANARY" if role == "p8Canary" else
                                       "P9_CANARY" if role == "p9Canary" else
                                       {"nullMode": "NULL", "observeMode": "OBSERVE", "tapThrow": "TAP_THROW",
                                        "callbackThrow": "CALLBACK_THROW", "reasonThrow": "REASON_THROW"}.get(role, role))
        if intent is not None:
            if (intent["schema"] != "tsj-q100-reservation-provenance-launch-v1"
                    or intent["role"] != expected_intent_role
                    or intent["inputSha256"] != pins_sha or intent["sourceSha256"] != source_sha
                    or intent["outputRoot"] != str(output_root)):
                _reject("P9_HOST_LAUNCH_INTENT")
            owner = _exact_keys(intent["owner"], ("pid", "startTicks", "executable", "executableSha256"), "P9_HOST_LAUNCH_OWNER")
            if (owner["pid"] != host_pid or owner["startTicks"] != host_ticks
                    or owner["executable"] != host["host"]["executable"]
                    or owner["executableSha256"] != host["host"]["executableSha256"]):
                _reject("P9_HOST_LAUNCH_OWNER")
            _exact_int(intent["limitMs"], "P9_HOST_LAUNCH_INTENT")
            if intent["executable"] != exe:
                _reject("P9_HOST_LAUNCH_INTENT")

        if role in ("preflight", "preNative", "postSource"):
            expected_args = ["-I", "-B", auditor, context["pinsPath"], pins_sha]
        elif role == "compiler":
            expected_args = intent["arguments"]
        else:
            if role == "p6Canary":
                hinted_args = intent["arguments"] if intent is not None else None
                if (type(hinted_args) is not list or len(hinted_args) != 5
                        or hinted_args[0:2] != ["-ea", "-cp"]
                        or hinted_args[3] != "com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy"
                        or hinted_args[4] != "--canary" or hinted_args[2] != expected_classpath):
                    _reject("P9_HOST_PROCESS_ARGUMENTS")
                classpath_value = expected_classpath
            if classpath_value is None and role != "p6Canary":
                _reject("P9_HOST_PROCESS_ARGUMENTS")
            class_name = {
                "p6Canary": "com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy",
                "p8Canary": "com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary",
                "p9Canary": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceCanary",
                "badMode": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "badUuid": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "badInputSha": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "nonemptyData": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "nonemptyP9": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "nullMode": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "observeMode": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "tapThrow": "com.lushprojects.circuitjs1.client.CanonicalReservationProvenanceStudy",
                "callbackThrow": "com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy",
                "reasonThrow": "com.lushprojects.circuitjs1.client.CanonicalGeometricPrefixStudy",
            }[role]
            if role == "p6Canary":
                app = ["--canary"]
            elif role in ("p8Canary", "p9Canary"):
                app = []
            elif role in _HOST_NEGATIVE_ROLES:
                stem_name, _expected_failure, arg_index = _HOST_NEGATIVE_ROLES[role]
                control_run = "00000000-0000-4000-8000-000000000009"
                data_rel = f"controls/{stem_name.upper().replace('-', '_')}/data"
                p9_rel = f"controls/{stem_name.upper().replace('-', '_')}/p9"
                if role == "nonemptyData":
                    data_rel = "controls/P9_NONEMPTY_DATA/data"
                    p9_rel = "controls/P9_NONEMPTY_DATA/p9"
                elif role == "nonemptyP9":
                    data_rel = "controls/P9_NONEMPTY_P9/data"
                    p9_rel = "controls/P9_NONEMPTY_P9/p9"
                app = ["NULL", control_run, pins_sha, context["p8WireSha"], source_sha,
                       str(output_root.joinpath(*data_rel.split("/"))), str(output_root.joinpath(*p9_rel.split("/")))]
                app[arg_index] = {"badMode": "INVALID", "badUuid": "not-a-v4-uuid",
                                  "badInputSha": "not-a-sha256"}.get(role, app[arg_index])
            elif role in ("nullMode", "observeMode", "tapThrow"):
                operation_mode = {"nullMode": "NULL", "observeMode": "OBSERVE", "tapThrow": "TAP_THROW"}[role]
                operation = next((r for r in host["operations"] if r.get("wrapperMode") == operation_mode), None)
                if operation is None:
                    _reject("P9_HOST_OPERATION_CENSUS")
                app = [operation_mode, operation["runId"], pins_sha, context["p8WireSha"], source_sha,
                       str(output_root.joinpath(*operation["p8DataDirectory"].split("/"))),
                       str(output_root.joinpath(*operation["p9Directory"].split("/")))]
            elif role == "callbackThrow":
                operation = next((r for r in host["operations"] if r.get("wrapperMode") == "CALLBACK_THROW"), None)
                if operation is None:
                    _reject("P9_HOST_OPERATION_CENSUS")
                app = ["CALLBACK_THROW", operation["runId"], pins_sha, context["p8WireSha"],
                       str(output_root.joinpath(*operation["p8DataDirectory"].split("/")))]
            else:
                operation = next((r for r in host["operations"] if r.get("wrapperMode") == "REASON_THROW"), None)
                if operation is None:
                    _reject("P9_HOST_OPERATION_CENSUS")
                app = ["REASON_THROW", operation["runId"], pins_sha, context["p8WireSha"], source_sha,
                       str(output_root.joinpath(*operation["p8DataDirectory"].split("/")))]
            expected_args = ["-ea", "-cp", classpath_value or "", class_name] + app
        if intent is not None and (intent["arguments"] != expected_args or intent["limitMs"] != (60000 if role == "compiler" else 480000)):
            _reject("P9_HOST_LAUNCH_INTENT")
        if role == "compiler":
            arguments = expected_args
            # The source list path is a private removed scratch leaf. Verify the
            # command shape and derive every path from its authenticated copy.
            if arguments != expected_compiler_args:
                _reject("P9_COMPILER_ARGUMENTS")
        proc, wall = _validate_process_receipt(data, descriptor, role, exe, expected_args, expected_exit, output_root)
        if intent is not None and (intent["arguments"] != proc["Arguments"] or type(intent["arguments"]) is not list):
            _reject("P9_HOST_PROCESS_ARGUMENTS")
        if role == "p6Canary":
            classpath_value = proc["Arguments"][2]
            if type(classpath_value) is not str or not classpath_value:
                _reject("P9_HOST_PROCESS_ARGUMENTS")
        if role == "compiler":
            if (intent["limitMs"] != 60000 or intent["sourceCount"] != 1085
                    or intent["sourceListBytes"] != len(data["compiler-sources.txt"])
                    or intent["sourceListSha256"] != _sha256(data["compiler-sources.txt"])):
                _reject("P9_COMPILER_INTENT")
        elif role in ("preflight", "preNative", "postSource", "compiler"):
            if wall > 60000:
                _reject("P9_HOST_PROCESS_WALL")
        elif role in ("p6Canary", "p8Canary", "p9Canary", "badMode", "badUuid", "badInputSha", "nonemptyData", "nonemptyP9",
                      "nullMode", "observeMode", "tapThrow", "callbackThrow", "reasonThrow"):
            if wall > 480000:
                _reject("P9_HOST_PROCESS_WALL")
        raw_by_role[role] = proc
        wall_by_role[role] = wall
    return raw_by_role, wall_by_role


def _p8_call(function, *args):
    try:
        return function(*args)
    except ReaderError:
        raise
    except Exception:
        _reject("P9_P8_SEMANTIC")


def _p8_operation_row(row: Mapping[str, Any], mode: str | None = None) -> dict[str, Any]:
    return {
        "mode": row["p8Mode"] if mode is None else mode,
        "runId": row["runId"],
        "processKey": row["processKey"],
        "workNanos": row["workNanos"],
        "groups": row["groups"],
        "observationUnits": row["observationUnits"],
    }


def _p8_data_projection(data: Mapping[str, bytes], wrapper: str) -> dict[str, bytes]:
    p8_mode = "OBSERVE" if wrapper == "TAP_THROW" else wrapper
    projected: dict[str, bytes] = {}
    for name in P8_DATA_FILES:
        source = f"runs/{wrapper}/data/{name}"
        target = f"runs/{p8_mode}/data/{name}"
        if source not in data:
            _reject("P9_P8_DATA_PROJECTION")
        projected[target] = data[source]
    if len(projected) != 7:
        _reject("P9_P8_DATA_PROJECTION")
    return projected


def _p8_payload_map(data: Mapping[str, bytes], wrapper: str) -> dict[str, bytes]:
    prefix = f"runs/{wrapper}/data/"
    result = {name: data[prefix + name] for name in P8_DATA_FILES if prefix + name in data}
    if set(result) != set(P8_DATA_FILES):
        _reject("P9_P8_DATA_PROJECTION")
    return result


def _validate_p8_semantics(host: Mapping[str, Any], data: Mapping[str, bytes],
                           process_raw: Mapping[str, Mapping[str, Any]],
                           process_walls: Mapping[str, float], operations: Sequence[Mapping[str, Any]],
                           output_root: Path, context: Mapping[str, Any], pins_sha: str,
                           source_sha: str) -> tuple[Any, dict[str, dict[str, Any]], dict[str, dict[str, Any]]]:
    p8 = _load_p8_reader(context)
    parent = _p8_call(p8.load_semantic_parent, context["p8Pins"], context["p8Rows"], context["p8SourceRows"])
    parent.update({"pinsSha": pins_sha,
                   "pinsPath": context["p8Pins"]["p6Context"]["pinsPath"],
                   "baseRepository": context["p8Pins"]["repositoryRoot"]})
    process_bundle = {"host": host, "raw": dict(process_raw), "walls": dict(process_walls),
                      "processes": [], "classpath": None}
    p6_canary_intent = _strict_document(data["p6-canary-launch-intent.json"], "P9_HOST_LAUNCH_INTENT")
    p6_args = p6_canary_intent.get("arguments")
    if type(p6_args) is not list or len(p6_args) != 5 or type(p6_args[2]) is not str:
        _reject("P9_HOST_PROCESS_ARGUMENTS")
    process_bundle["classpath"] = p6_args[2]

    operation_by_mode = {row["wrapperMode"]: row for row in operations}
    p8_runs: dict[str, dict[str, Any]] = {}
    indices: dict[str, dict[str, Any]] = {}
    for wrapper in ("NULL", "OBSERVE", "TAP_THROW"):
        row = operation_by_mode[wrapper]
        p8_mode = "OBSERVE" if wrapper == "TAP_THROW" else wrapper
        p8_row = _p8_operation_row(row, p8_mode)
        p8_data = _p8_data_projection(data, wrapper)
        process_key = row["processKey"]
        proc = process_raw[process_key]
        if proc["ExitCode"] != 0 or proc["Stderr"] != "":
            _reject("P9_P8_PROCESS")
        marker = _p8_call(p8.validate_native_marker, proc["Stdout"].encode("utf-8", "strict"),
                          b"CANONICAL_GEOMETRIC_PREFIX_COMPLETE ", "P9_P8_MARKER")
        run = _p8_call(p8._validate_complete_operation, output_root, p8_data, host, p8_row,
                       process_bundle, parent, pins_sha, source_sha)
        run["p8Marker"] = marker
        p8_runs[wrapper] = run
        p8_payload = _p8_payload_map(data, wrapper)
        operation_raw = data[f"runs/{wrapper}/data/op-1.jsonl"]
        p9_sidecar = data[f"runs/{wrapper}/p9/reservation-operands.jsonl"]
        p9_index_raw = data[f"runs/{wrapper}/p9/reservation-index.json"]
        parent_prefix = run["reason"]["header"]["parentPrefixSha256"]
        parent_graph = run["reason"]["header"]["parentGraphSha256"]
        if wrapper == "OBSERVE":
            target_rows = [item for item in run["reasonRows"]
                           if item.get("ordinal") == 10 and item.get("parentOrdinal") == 11]
            if len(target_rows) != 1:
                _reject("P9_QUERY_IDENTITY")
            target = target_rows[0]
            identity = target.get("identity")
            if type(identity) is not dict:
                _reject("P9_QUERY_IDENTITY")
            direction = identity.get("direction")
            if type(direction) is not int or direction != 3:
                _reject("P9_QUERY_IDENTITY")
            expected_identity = dict(identity)
            fine_events = target.get("fineEvents")
            empty_events = [event for event in fine_events if type(event) is dict
                            and event.get("site") == "EMPTY_FACE_RESERVATION"] if type(fine_events) is list else []
            if len(empty_events) != 1:
                _reject("P9_QUERY_IDENTITY")
            pads_raw = run["p6"]["operation"].get("pads")
            if type(pads_raw) is not list or len(pads_raw) != PAD_COUNT:
                _reject("P9_P4_PAD_BINDING")
            p4_pad_map: dict[str, Any] = {}
            for old in pads_raw:
                if type(old) is not dict or type(old.get("padId")) is not str or old["padId"] in p4_pad_map:
                    _reject("P9_P4_PAD_BINDING")
                p4_pad_map[old["padId"]] = old
            operands = validate_observe_operands(
                p9_sidecar, run_id=row["runId"], input_sha=pins_sha, source_sha=source_sha,
                wire_sha=context["p8WireSha"], parent_prefix_sha=parent_prefix,
                parent_graph_sha=parent_graph, expected_identity=expected_identity,
                p4_pad_map=p4_pad_map, expected_empty_component_face=True)
            analysis = analyze_slot(operands)
            require_slot_match(analysis)
            p8_runs[wrapper]["operands"] = operands
            p8_runs[wrapper]["slotAnalysis"] = analysis
            p8_runs[wrapper]["expectedIdentity"] = expected_identity
            p8_runs[wrapper]["p4PadMap"] = p4_pad_map
            p8_runs[wrapper]["parentPrefixSha256"] = parent_prefix
            p8_runs[wrapper]["parentGraphSha256"] = parent_graph
        else:
            validate_control_operands(
                p9_sidecar, mode=wrapper, run_id=row["runId"], input_sha=pins_sha,
                source_sha=source_sha, wire_sha=context["p8WireSha"],
                parent_prefix_sha=parent_prefix, parent_graph_sha=parent_graph)
            p8_runs[wrapper]["parentPrefixSha256"] = parent_prefix
            p8_runs[wrapper]["parentGraphSha256"] = parent_graph
        p9_index = _validate_p9_index(
            p9_index_raw, mode=wrapper, operation=row, operands_raw=p9_sidecar,
            p8_data=p8_payload, p8_run=run, p8_proc=proc, operation_record=operation_raw,
            groups=row["groups"], input_sha=pins_sha, source_sha=source_sha)
        indices[wrapper] = p9_index

    _p8_call(p8._compare_reason_modes, p8_runs["NULL"], p8_runs["OBSERVE"])
    _p8_call(p8._compare_reason_modes, p8_runs["NULL"], p8_runs["TAP_THROW"])
    _p8_call(parent["p6"]._compare_capture_runs, p8_runs["NULL"]["p6"], p8_runs["OBSERVE"]["p6"],
             parent["p5"], parent["parent"])
    _p8_call(parent["p6"]._compare_capture_runs, p8_runs["NULL"]["p6"], p8_runs["TAP_THROW"]["p6"],
             parent["p5"], parent["parent"])

    callback_row = operation_by_mode["CALLBACK_THROW"]
    callback_projection = _p8_operation_row(callback_row, "CALLBACK_THROW")
    callback = _p8_call(p8._validate_callback_throw, output_root, data, callback_projection,
                        process_bundle, parent, pins_sha)
    reason_row = operation_by_mode["REASON_THROW"]
    reason_projection = _p8_operation_row(reason_row, "REASON_THROW")
    reason = _p8_call(p8._validate_reason_throw, output_root, data, host, reason_projection,
                      process_bundle, p8_runs["NULL"], parent, context["p8Pins"],
                      pins_sha, source_sha)
    p8_runs["CALLBACK_THROW"] = callback
    p8_runs["REASON_THROW"] = reason
    parity = {key: True for key in P8_PARITY_KEYS}
    return parent, p8_runs, {"indices": indices, "parity": parity, "processBundle": process_bundle}


def _public_run_rows(operations: Sequence[Mapping[str, Any]], data: Mapping[str, bytes]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for operation in operations:
        wrapper = operation["wrapperMode"]
        if wrapper in NORMAL_MODES:
            base = f"runs/{wrapper}/p9/"
            sidecar_raw = data[base + "reservation-operands.jsonl"]
            index_raw = data[base + "reservation-index.json"]
            sidecar = {"path": base + "reservation-operands.jsonl", "bytes": len(sidecar_raw),
                       "sha256": _sha256(sidecar_raw)}
            index = {"path": base + "reservation-index.json", "bytes": len(index_raw),
                     "sha256": _sha256(index_raw)}
        else:
            sidecar = index = None
        rows.append({"wrapperMode": wrapper, "p8Mode": operation["p8Mode"], "runId": operation["runId"],
                     "disposition": operation["disposition"], "sidecar": sidecar, "index": index})
    return rows


def _validate_reservation_suite(pins_path: str | os.PathLike[str], pins_sha: str,
                                output_root_value: str | os.PathLike[str],
                                parent_receipt_value: str | os.PathLike[str],
                                *, return_private: bool = False):
    started = time.perf_counter_ns()
    if type(pins_sha) is not str or re.fullmatch(r"[0-9a-f]{64}", pins_sha) is None:
        _reject("P9_INPUT_PINS_HASH")
    bootstrap = _load_authenticated_bootstrap(pins_path, pins_sha)
    output_root = _canonical_abs_path(str(output_root_value), "P9_OUTPUT_PATH", exists=True)
    parent_path = _canonical_abs_path(str(parent_receipt_value), "P9_PARENT_RECEIPT", exists=True)
    if not output_root.is_dir() or not parent_path.is_file():
        _reject("P9_OUTPUT_PATH")
    _precheck_parent_envelope(parent_path, output_root)
    auditor, context = _load_input_auditor(pins_sha, bootstrap)
    verify_overlay(context)
    _validate_host_disjointness(output_root, Path(context["pinsPath"]), parent_path, context)
    preliminary_raw = _load_host_file(output_root, "host-result.json", MAX_LINE_BYTES, "P9_HOST_RESULT_READ")
    preliminary = _strict_document(preliminary_raw, "P9_HOST_RESULT")
    mode = preliminary.get("mode") if type(preliminary) is dict else None
    expected_files, expected_dirs = _expected_tree(mode)
    data = _tree_census(output_root, expected_files, expected_dirs)
    host_raw = data["host-result.json"]
    if host_raw != preliminary_raw:
        _reject("P9_HOST_RESULT_CHANGED")
    host = _strict_document(host_raw, "P9_HOST_RESULT")
    wire_sha = context["p8WireSha"]
    _validate_host_identity(host, mode, context["pins"], context, pins_sha, context["sourceSha"], wire_sha)
    parent_raw_before = _stable_read(parent_path, MAX_LINE_BYTES, "P9_PARENT_RECEIPT")
    parent, marker = _validate_parent_receipt(parent_path, context, pins_sha, output_root, host_raw, host)
    _validate_host_launch(host, data, context, parent, output_root, pins_sha, context["sourceSha"], wire_sha)
    _validate_scratch(data, host, output_root, context)
    _validate_compiler_and_bridges(host, data, context)
    process_raw, process_walls = _validate_host_process_roles(
        host, data, context, pins_sha, context["sourceSha"], mode,
        host["host"]["pid"], host["host"]["startTicks"], output_root)
    operations = _validate_operations(host, mode, output_root, pins_sha, context["sourceSha"], wire_sha, data)
    output_info = _validate_output_indexes(host, data, expected_files, expected_dirs,
                                           pins_sha, context["sourceSha"], wire_sha, marker)
    _validate_canaries_and_preflight(host, data, process_raw, pins_sha, context["sourceSha"], mode)
    _validate_source_processes(host, process_raw, pins_sha, context["sourceSha"])
    limits = _validate_limits(host["limits"])
    parent_elapsed = float(parent["elapsedMs"])
    indices: dict[str, dict[str, Any]] = {}
    parity = None
    analysis = None
    semantic_parent = None
    semantic_runs: dict[str, dict[str, Any]] = {}
    if mode == "SUITE":
        semantic_parent, semantic_runs, details = _validate_p8_semantics(
            host, data, process_raw, process_walls, operations, output_root,
            context, pins_sha, context["sourceSha"])
        indices = details["indices"]
        parity = details["parity"]
        analysis = semantic_runs["OBSERVE"]["slotAnalysis"]
    timing_summary = _validate_host_timing(host, process_walls, parent_elapsed, mode, operations, indices)
    # The scratch/process proof is independently checked above; retaining the
    # computed summary makes that proof explicit without leaking any path.
    if not timing_summary["processRootsAbsent"] or not timing_summary["scratchRemoved"]:
        _reject("P9_HOST_CLEANUP")

    try:
        if auditor.recheck_inputs(context) is not True:
            _reject("P9_SOURCE_RECHECK")
    except ReaderError:
        raise
    except Exception:
        _reject("P9_SOURCE_RECHECK")
    final_data = _tree_census(output_root, expected_files, expected_dirs)
    parent_raw = _stable_read(parent_path, MAX_LINE_BYTES, "P9_PARENT_RECEIPT")
    if final_data != data:
        _reject("P9_OUTPUT_CHANGED_DURING_READ")
    if parent_raw != parent_raw_before:
        _reject("P9_PARENT_RECEIPT_CHANGED")
    source_summary = context["summary"]
    source_closure = {key: source_summary[key] for key in (
        "status", "pinsSha256", "sourceInventorySha256", "existingFiles", "directFiles",
        "derivedFiles", "totalFiles", "productionFiles", "topFiles")}
    source_closure["rechecked"] = True
    if mode == "CANARY":
        net_count = pad_count = 0
        contributor_digest = None
        runs = []
        target = slot_comparison = None
        parity = None
    else:
        net_count = analysis["contributorNetCount"]
        pad_count = analysis["contributorPadCount"]
        contributor_digest = analysis["contributorSetSha256"]
        runs = _public_run_rows(operations, data)
        target = dict(TARGET_VALUE)
        slot_comparison = {
            "expectedState": analysis["expectedState"],
            "capturedState": analysis["capturedState"],
            "match": True,
            "expectedValueSha256": analysis["expectedValueSha256"],
            "capturedValueSha256": analysis["capturedValueSha256"],
        }
    python_key = os.path.normcase(os.path.abspath(context["tools"]["pythonExe"]))
    python_row = context["rows"].get(python_key)
    if type(python_row) is not dict:
        _reject("P9_INPUT_TOOL_IDENTITY")
    processes = {
        "hostReturned": True,
        "hostTerminationProven": parent["process"]["TerminationProven"],
        "childProcessCount": len(process_raw),
        "allChildrenTerminated": all(proc["TerminationProven"] is True for proc in process_raw.values()),
        "allProcessRootsAbsent": True,
        "readerExecutableSha256": python_row["sha256"],
    }
    result = {
        "schema": SCHEMA,
        "status": _result_status(mode),
        "layoutQualification": False,
        "scope": "HOST_CANARY_ONLY" if mode == "CANARY" else "ONE_AUTHORED_BASE_PARENT11_SLOT_ONLY",
        "p9InputSha256": pins_sha,
        "p9SourceSha256": context["sourceSha"],
        "p8WireSha256": wire_sha,
        "runs": runs,
        "target": target,
        "slotComparison": slot_comparison,
        "contributorNetCount": net_count,
        "contributorPadCount": pad_count,
        "contributorSetSha256": contributor_digest,
        "p8Parity": parity,
        "sourceClosure": source_closure,
        "processes": processes,
        "outputClosure": output_info["outputClosure"],
        "limits": limits,
    }
    if tuple(result) != READER_RESULT_KEYS:
        _reject("P9_RESULT_SCHEMA")
    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000.0
    if not math.isfinite(elapsed_ms) or elapsed_ms > min(180_000, limits["readerMs"]):
        _reject("P9_READER_WALL_LIMIT")
    if return_private:
        private = {"context": context, "host": host, "data": data, "processRaw": process_raw,
                   "processWalls": process_walls, "operations": operations, "semanticParent": semantic_parent,
                   "semanticRuns": semantic_runs, "indices": indices, "analysis": analysis,
                   "parentRaw": parent_raw_before, "hostRaw": host_raw, "mode": mode,
                   "pinsSha": pins_sha}
        return result, private
    return result


def validate_reservation_suite(pins_path: str | os.PathLike[str], pins_sha: str,
                               output_root: str | os.PathLike[str],
                               parent_receipt: str | os.PathLike[str]) -> dict[str, Any]:
    return _validate_reservation_suite(pins_path, pins_sha, output_root, parent_receipt)


def _result_status(mode: str) -> str:
    if mode == "CANARY":
        return "PASS_RESERVATION_HOST_CANARY_ONLY"
    if mode == "SUITE":
        return "PASS_RESERVATION_PROVENANCE_DIAGNOSTIC_ONLY"
    _reject("P9_HOST_MODE")


def _result_emit(result: Mapping[str, Any]) -> bytes:
    if tuple(result.keys()) != READER_RESULT_KEYS:
        _reject("P9_RESULT_SCHEMA")
    return _canonical_json(result) + b"\n"


def main(argv: Sequence[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    try:
        if len(args) != 4:
            _reject("P9_READER_ARGUMENTS")
        result = validate_reservation_suite(args[0], args[1], args[2], args[3])
        payload = _result_emit(result)
        sys.stdout.buffer.write(payload)
        sys.stdout.buffer.flush()
        return 0
    except ReaderError as exc:
        sys.stdout.buffer.write(b"")
        sys.stderr.buffer.write(f"{READER_ERROR_PREFIX} {exc.code}\n".encode("ascii"))
        sys.stderr.buffer.flush()
        return 2
    except Exception:
        sys.stdout.buffer.write(b"")
        sys.stderr.buffer.write(f"{READER_ERROR_PREFIX} UNEXPECTED_FAILURE\n".encode("ascii"))
        sys.stderr.buffer.flush()
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
