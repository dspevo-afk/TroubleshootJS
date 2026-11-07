#!/usr/bin/env python3
"""P9 literal oracle tests and authenticated semantic-control CLI."""

from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import sys
import tempfile
import time
import unittest
from unittest import mock
from pathlib import Path
from typing import Any


def _load_reader():
    path = Path(__file__).with_name("read_provenance.py")
    spec = importlib.util.spec_from_file_location("p9_read_provenance", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("reader module unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reader = _load_reader()


def _canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":")).encode("ascii")


def _fixture_rows(*, slot: Any = None):
    grid_width, grid_height = 400, 300
    cells = grid_width * grid_height
    identity = {
        "layer": 1,
        "fromState": cells + 238 * grid_width + 382,
        "toState": cells + 238 * grid_width + 381,
        "bit": 8,
        "selected": True,
        "fromGridX": 382,
        "fromGridY": 238,
        "toGridX": 381,
        "toGridY": 238,
        "fromX": 3820,
        "fromY": 2380,
        "toX": 3810,
        "toY": 2380,
        "direction": 3,
        "netId": "N_ACTIVE",
        "startPadId": "P_START",
        "rootPadId": "P_ROOT",
        "reachedPthPadIds": [],
    }
    header = {
        "kind": "header",
        "schema": reader.OPERAND_SCHEMA,
        "runId": "11111111-1111-4111-8111-111111111111",
        "inputSha256": "1" * 64,
        "sourceSha256": "2" * 64,
        "wireSha256": "3" * 64,
        "wrapperMode": "OBSERVE",
        "p8Mode": "OBSERVE",
        "parentPrefixSha256": "4" * 64,
        "parentGraphSha256": "5" * 64,
        "queryOrdinal": 10,
        "parentOrdinal": 11,
        "captureState": "CAPTURED",
        "identity": identity,
        "grid": 10,
        "outline": {"x": -10, "y": -10, "width": 4010, "height": 3010},
        "minX": 0,
        "minY": 0,
        "gridWidth": grid_width,
        "gridHeight": grid_height,
        "selectedLayer": 1,
        "requestedNetId": "N_ACTIVE",
        "traceWidthProfile": "ROLE_WIDTHS",
        "profileIdentity": "ROLE_TRACE@1",
        "emptyComponentFace": True,
        "checkProfiledPadTouches": True,
        "escapeOutletLand": 6,
        "escapeOutletLandOrigin": "PINNED_FULLER_SOURCE",
    }
    pads = []
    for index in range(reader.PAD_COUNT):
        x = 1000 + (index % 20) * 20
        y = 1000 + (index // 20) * 20
        role = tuple(reader.ROLE_WIDTHS)[index % len(reader.ROLE_WIDTHS)]
        net_id = "N_ACTIVE" if index == 0 else f"N{index:03d}"
        pads.append({
            "kind": "pad",
            "index": index,
            "padId": f"P{index:03d}",
            "componentId": f"C{index:03d}",
            "netId": net_id,
            "routingRole": role,
            "x": x,
            "y": y,
            "mountingSide": "TOP",
            "attachment": "PLATED_THROUGH_HOLE",
            "escapeDx": 0,
            "escapeDy": 0,
            "escapeLength": 0,
            "padBounds": {"x": x - 1, "y": y - 1, "width": 2, "height": 2},
            "ownerCourtyard": {"x": x - 10, "y": y - 10, "width": 20, "height": 20},
            "preparedEscapeCorridor": {"x": x, "y": y, "width": 0, "height": 0},
            "padTraceWidth": reader.ROLE_WIDTHS[role],
        })
    footer = {
        "kind": "footer",
        "complete": True,
        "runId": header["runId"],
        "wrapperMode": "OBSERVE",
        "p8Mode": "OBSERVE",
        "recordCount": reader.PAD_COUNT + 2,
        "padCount": reader.PAD_COUNT,
        "requestedNetId": "N_ACTIVE",
        "activeNetId": "N_ACTIVE",
        "activeTraceWidth": 13,
        "slotValue": slot,
    }
    return header, pads, footer


def _encode_rows(rows) -> bytes:
    return b"".join(_canonical(row) + b"\n" for row in rows)


def _observe_bytes(header=None, pads=None, footer=None) -> bytes:
    if header is None and pads is None and footer is None:
        header, pads, footer = _fixture_rows()
    return _encode_rows([header, *pads, footer])


def _expected_args():
    return {
        "run_id": "11111111-1111-4111-8111-111111111111",
        "input_sha": "1" * 64,
        "source_sha": "2" * 64,
        "wire_sha": "3" * 64,
        "parent_prefix_sha": "4" * 64,
        "parent_graph_sha": "5" * 64,
    }


def _parse_fixture(header=None, pads=None, footer=None):
    return reader.validate_observe_operands(
        _observe_bytes(header, pads, footer), **_expected_args()
    )


def _tap_bytes(*, tap_overrides=None):
    common = {
        "kind": "header",
        "schema": reader.OPERAND_SCHEMA,
        "runId": "11111111-1111-4111-8111-111111111111",
        "inputSha256": "1" * 64,
        "sourceSha256": "2" * 64,
        "wireSha256": "3" * 64,
        "wrapperMode": "TAP_THROW",
        "p8Mode": "OBSERVE",
        "parentPrefixSha256": "4" * 64,
        "parentGraphSha256": "5" * 64,
        "queryOrdinal": 10,
        "parentOrdinal": 11,
        "captureState": "DTO_DISCARDED",
    }
    footer = {
        "kind": "footer",
        "complete": True,
        "runId": common["runId"],
        "wrapperMode": "TAP_THROW",
        "p8Mode": "OBSERVE",
        "recordCount": 2,
        "captureState": "DTO_DISCARDED",
        "tapThrow": dict(reader.TAP_VALUES),
    }
    if tap_overrides:
        footer["tapThrow"].update(tap_overrides)
    return _encode_rows([common, footer])


def _expect_reject(test: unittest.TestCase, expected_code: str, function, *args, **kwargs):
    with test.assertRaises(reader.ReaderError) as caught:
        function(*args, **kwargs)
    test.assertEqual(caught.exception.code, expected_code)


class LiteralPositiveTests(unittest.TestCase):
    def test_P9L01_EMPTY(self):
        self.assertEqual(reader.nullable_state(None), "NO_RESERVATION")

    def test_P9L02_SINGLE(self):
        self.assertEqual(reader.merge_reservation(None, "N1"), "N1")

    def test_P9L03_SAME_NET(self):
        self.assertEqual(reader.merge_reservation(reader.merge_reservation(None, "N1"), "N1"), "N1")

    def test_P9L04_MIXED(self):
        mixed = reader.merge_reservation(reader.merge_reservation(None, "N1"), "N2")
        self.assertEqual(reader.merge_reservation(mixed, "N1"), "")

    def test_P9L05_STRICT_EDGE(self):
        self.assertFalse(reader._strict_rect_intersects((0, 0, 10, 1), (10, 0, 10, 1)))
        self.assertFalse(reader._strict_rect_intersects((0, 0, 0, 10), (0, 0, 10, 10)))
        self.assertFalse(reader._strict_rect_intersects((0, 0, 10, 0), (0, 0, 10, 10)))

    def test_P9L06_CLOSED_TOUCH(self):
        self.assertTrue(reader._closed_rect_touches((0, 0, 19, 9), (19, 2, 4, 4)))

    def test_P9L07_ROLE_PROFILE(self):
        expected = {"HIGH_CURRENT": 13, "SUPPLY": 11, "RETURN": 11, "CONTROL": 9, "SIGNAL": 9}
        self.assertEqual(reader.ROLE_WIDTHS, expected)

    def test_P9L08_PTH_FACE(self):
        pth = {"attachment": "PLATED_THROUGH_HOLE", "mountingSide": "TOP"}
        surface = {"attachment": "SURFACE_PAD", "mountingSide": "TOP"}
        self.assertTrue(reader._pad_has_copper(pth, 0))
        self.assertTrue(reader._pad_has_copper(pth, 1))
        self.assertTrue(reader._pad_has_copper(surface, 0))
        self.assertFalse(reader._pad_has_copper(surface, 1))

    def test_P9L09_OPPOSITE_FACE(self):
        header, pads, _footer = _fixture_rows()
        header = dict(header)
        header["selectedLayer"] = 1
        pad = dict(pads[0])
        pad.update({
            "x": 20, "y": 30, "mountingSide": "TOP", "escapeDx": 1,
            "escapeLength": 20, "padTraceWidth": 11,
            "ownerCourtyard": {"x": 0, "y": 0, "width": 30, "height": 40},
        })
        self.assertEqual(reader.derive_escape_corridor(header, pad), (20, 30, 0, 0))
        self.assertEqual(reader._expanded_corridor(header, pad, 9), (5, 15, 30, 30))
        self.assertTrue(reader._strict_rect_intersects((5, 15, 30, 30), (20, 30, 10, 1)))

    def test_P9L10_NEGATIVE_DIVISION(self):
        self.assertEqual(reader._start_index(-1, 3), 0)
        self.assertEqual(reader._end_index(-1, 3), -1)

    def test_P9L11_OUTLET_ROUNDING(self):
        header, pads, _footer = _fixture_rows()
        header = dict(header)
        header["selectedLayer"] = 0
        pad = dict(pads[0])
        pad.update({
            "x": 20, "y": 20, "mountingSide": "TOP", "escapeDx": 1,
            "escapeLength": 10,
            "ownerCourtyard": {"x": 0, "y": 0, "width": 30, "height": 40},
        })
        self.assertEqual(reader.derive_escape_corridor(header, pad), (20, 20, 20, 0))
        behind = dict(pad)
        behind["ownerCourtyard"] = {"x": 0, "y": 0, "width": 10, "height": 40}
        self.assertEqual(reader.derive_escape_corridor(header, behind), (20, 20, 10, 0))

    def test_P9L12_TOUCH_GATE(self):
        header, pads, footer = _fixture_rows(slot="N_ACTIVE")
        pad = pads[0]
        pad.update({
            "x": 3660, "y": 2380, "mountingSide": "TOP", "escapeDx": 0,
            "escapeDy": 0, "escapeLength": 0,
            "padBounds": {"x": 3500, "y": 2375, "width": 310, "height": 10},
            "ownerCourtyard": {"x": 3650, "y": 2370, "width": 20, "height": 20},
            "preparedEscapeCorridor": {"x": 3660, "y": 2380, "width": 0, "height": 0},
            "padTraceWidth": 13,
        })
        footer["activeTraceWidth"] = 13
        operands = _parse_fixture(header, pads, footer)
        hit = reader.analyze_slot(operands)
        self.assertEqual(hit["expectedValue"], "N_ACTIVE")
        self.assertEqual(hit["contributorPadCount"], 1)
        operands["header"]["checkProfiledPadTouches"] = False
        gated = reader.analyze_slot(operands)
        self.assertIsNone(gated["expectedValue"])
        self.assertEqual(gated["contributorPadCount"], 0)


class LiteralNegativeTests(unittest.TestCase):
    def test_P9N01_CRLF_OR_MISSING_FINAL_LF(self):
        raw = _observe_bytes().replace(b"\n", b"\r\n")
        _expect_reject(self, "P9_OPERAND_FRAMING", reader.validate_observe_operands, raw, **_expected_args())
        _expect_reject(self, "P9_OPERAND_FRAMING", reader.validate_observe_operands,
                       _observe_bytes()[:-1], **_expected_args())

    def test_P9N02_DUPLICATE_JSON_KEY(self):
        raw = _observe_bytes()
        line, rest = raw.split(b"\n", 1)
        duplicate = line[:-1] + b',"kind":"header"}\n' + rest
        _expect_reject(self, "P9_OPERAND_JSON", reader.validate_observe_operands, duplicate, **_expected_args())

    def test_P9N03_EXTRA_OR_MISSING_HEADER_KEY(self):
        h, p, f = _fixture_rows()
        h["unexpected"] = True
        _expect_reject(self, "P9_HEADER_FIELDS", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        del h["captureState"]
        _expect_reject(self, "P9_HEADER_FIELDS", _parse_fixture, h, p, f)

    def test_P9N04_PAD_REORDERED_OR_DUPLICATED(self):
        h, p, f = _fixture_rows()
        p[0], p[1] = p[1], p[0]
        _expect_reject(self, "P9_PAD_ORDER", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        p[1] = dict(p[0])
        _expect_reject(self, "P9_PAD_ORDER", _parse_fixture, h, p, f)

    def test_P9N05_PAD_MISSING_OR_EXTRA(self):
        h, p, f = _fixture_rows()
        _expect_reject(self, "P9_PAD_COUNT", reader.validate_observe_operands,
                       _encode_rows([h, *p[:-1], f]), **_expected_args())
        extra = dict(p[-1])
        extra["index"] = reader.PAD_COUNT
        _expect_reject(self, "P9_PAD_COUNT", reader.validate_observe_operands,
                       _encode_rows([h, *p, extra, f]), **_expected_args())

    def test_P9N06_BOOL_OR_FLOAT_AS_INTEGER(self):
        h, p, f = _fixture_rows()
        p[0]["index"] = True
        _expect_reject(self, "P9_INTEGER_TYPE", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        p[0]["index"] = 0.0
        _expect_reject(self, "P9_INTEGER_TYPE", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        p[0]["escapeLength"] = 10**40
        _expect_reject(self, "P9_PAD_GEOMETRY", _parse_fixture, h, p, f)
        null_sidecar = _tap_bytes()
        rows = [json.loads(line) for line in null_sidecar.splitlines()]
        rows[1]["complete"] = 1
        _expect_reject(self, "P9_FOOTER_FIELDS", reader.validate_control_operands,
                       _encode_rows(rows), mode="TAP_THROW", **_expected_args())

    def test_P9N07_UNKNOWN_ROLE_OR_ENUM(self):
        h, p, f = _fixture_rows()
        p[0]["routingRole"] = "UNKNOWN"
        _expect_reject(self, "P9_PAD_ROLE", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        p[0]["attachment"] = "SURFACE_PAD"
        _expect_reject(self, "P9_PAD_COPPER", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        p[0]["attachment"] = "UNKNOWN"
        _expect_reject(self, "P9_PAD_ROLE", _parse_fixture, h, p, f)

    def test_P9N08_ROLE_WIDTH_DISAGREEMENT(self):
        h, p, f = _fixture_rows()
        p[0]["padTraceWidth"] = 13 if p[0]["padTraceWidth"] != 13 else 11
        _expect_reject(self, "P9_PAD_WIDTH", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        p[1]["netId"] = p[0]["netId"]
        p[1]["routingRole"] = "SIGNAL"
        p[1]["padTraceWidth"] = 9
        _expect_reject(self, "P9_PAD_WIDTH", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        f["activeTraceWidth"] = 11
        _expect_reject(self, "P9_PAD_WIDTH", _parse_fixture, h, p, f)

    def test_P9N09_BAD_GRID_DERIVATION(self):
        h, p, f = _fixture_rows()
        h["gridWidth"] += 1
        _expect_reject(self, "P9_GRID_DERIVATION", _parse_fixture, h, p, f)

    def test_P9N10_WRONG_PINNED_OUTLET_ORIGIN_OR_VALUE(self):
        h, p, f = _fixture_rows()
        h["escapeOutletLand"] = 0
        _expect_reject(self, "P9_OUTLET_SOURCE_BINDING", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        h["escapeOutletLandOrigin"] = "COPY_CONSTRUCTOR_ZERO"
        _expect_reject(self, "P9_OUTLET_SOURCE_BINDING", _parse_fixture, h, p, f)

    def test_P9N11_INCONSISTENT_COURTYARD_FOR_COMPONENT(self):
        h, p, f = _fixture_rows()
        p[1]["componentId"] = p[0]["componentId"]
        p[1]["ownerCourtyard"]["x"] += 1
        _expect_reject(self, "P9_OWNER_COURTYARD", _parse_fixture, h, p, f)

    def test_P9N12_PREPARED_CORRIDOR_DIFFERS_FROM_DERIVATION(self):
        h, p, f = _fixture_rows()
        p[0]["preparedEscapeCorridor"]["x"] += 1
        _expect_reject(self, "P9_CORRIDOR_PARITY", _parse_fixture, h, p, f)

    def test_P9N13_WRONG_TARGET_OR_QUERY_IDENTITY(self):
        h, p, f = _fixture_rows()
        h["identity"]["bit"] = 4
        _expect_reject(self, "P9_QUERY_IDENTITY", _parse_fixture, h, p, f)
        h, p, f = _fixture_rows()
        h["emptyComponentFace"] = False
        _expect_reject(self, "P9_QUERY_IDENTITY", reader.validate_observe_operands,
                       _observe_bytes(h, p, f), **_expected_args(),
                       expected_empty_component_face=True)

    def test_P9N14_CAPTURED_SLOT_DIFFERS_FROM_ORACLE(self):
        h, p, f = _fixture_rows(slot="N001")
        operands = _parse_fixture(h, p, f)
        _expect_reject(self, "P9_SLOT_MISMATCH", reader.require_slot_match, reader.analyze_slot(operands))

    def test_P9N15_TAP_SENTINEL_NOT_CAUGHT_ONCE_OR_DTO_NOT_DISCARDED(self):
        _expect_reject(self, "P9_TAP_THROW_PROOF", reader.validate_control_operands,
                       _tap_bytes(tap_overrides={"caught": False}), mode="TAP_THROW",
                       run_id="11111111-1111-4111-8111-111111111111", input_sha="1" * 64,
                       source_sha="2" * 64, wire_sha="3" * 64,
                       parent_prefix_sha="4" * 64, parent_graph_sha="5" * 64)
        _expect_reject(self, "P9_TAP_THROW_PROOF", reader.validate_control_operands,
                       _tap_bytes(tap_overrides={"dtoDiscarded": False}), mode="TAP_THROW",
                       run_id="11111111-1111-4111-8111-111111111111", input_sha="1" * 64,
                       source_sha="2" * 64, wire_sha="3" * 64,
                       parent_prefix_sha="4" * 64, parent_graph_sha="5" * 64)

    def test_P9N16_UNEXPECTED_SIDECAR_FILE_OR_INDEX_REFERENCE(self):
        _expect_reject(self, "P9_OUTPUT_CENSUS", reader.validate_output_reference,
                       {"path": "runs/OBSERVE/p9/unexpected.json", "bytes": 1, "sha256": "0" * 64},
                       "runs/OBSERVE/p9/reservation-index.json", b"x")


class ParentEnvelopeFailFastTests(unittest.TestCase):
    """Synthetic component proof that malformed parents reject before deep audit."""

    def _write_bootstrap_pins(self, root: Path) -> tuple[Path, str]:
        reader_path = Path(reader.__file__).resolve(strict=True)
        auditor_path = Path(__file__).with_name("audit_inputs.py").resolve(strict=True)
        auditor_raw = auditor_path.read_bytes()
        auditor_module = reader._module_from_bytes(auditor_raw, auditor_path, "p9_test_audit_inputs")
        python_path = Path(auditor_module.actual_image()).resolve(strict=True)
        files = []
        for path in (reader_path, auditor_path, python_path):
            raw = path.read_bytes()
            files.append({"path": str(path), "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
        pins = {
            "schema": reader._BOOTSTRAP_INPUT_SCHEMA,
            "sourceParent": reader._BOOTSTRAP_SOURCE_PARENT,
            "repositoryRoot": "synthetic-root",
            "p8Prerequisite": {},
            "publishedP8": {},
            "sourceInventory": {},
            "roles": {"input": {"auditor": str(auditor_path)}},
            "toolPaths": {"pythonExe": str(python_path)},
            "files": files,
        }
        raw_pins = json.dumps(pins, ensure_ascii=True, sort_keys=True, separators=(",", ":")).encode("ascii") + b"\n"
        path = root / "input-provenance-pins.json"
        path.write_bytes(raw_pins)
        return path, hashlib.sha256(raw_pins).hexdigest()

    def _write_parent(self, path: Path, *, elapsed: Any = 100.0,
                      exit_code: Any = 0, terminated: Any = True) -> None:
        values = {
            "FilePath": "synthetic-python.exe", "Arguments": [], "ProcessId": 1,
            "ProcessStartTicks": 1, "ExitCode": exit_code, "Stdout": "", "Stderr": "",
            "StdoutLog": "C:/synthetic/out.log", "StderrLog": "C:/synthetic/err.log",
            "ProcessRoot": "C:/synthetic/process", "TerminationProven": terminated,
        }
        process = {key: values[key] for key in reader._HOST_PROCESS_KEYS}
        parent = {"schema": "tsj-q100-reservation-provenance-parent-v1",
                  "elapsedMs": elapsed, "process": process}
        path.write_bytes(json.dumps(parent, ensure_ascii=True, separators=(",", ":")).encode("ascii") + b"\n")

    def test_invalid_parent_envelopes_reject_before_deep_source_audit(self):
        cases = (
            (True, 0, True, "PARENT_ELAPSED_TYPE_INVALID"),
            (100.0, 2, True, "PARENT_PROCESS_EXIT_INVALID"),
            (100.0, 0, False, "PARENT_PROCESS_TERMINATION_INVALID"),
            (1.0, 0, True, "PARENT_TIMING_ORDER_INVALID"),
        )
        for elapsed, exit_code, terminated, expected in cases:
            with self.subTest(expected=expected), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                pins_path, pins_sha = self._write_bootstrap_pins(root)
                output = root / "output"
                output.mkdir()
                host = {"timing": {"cleanupStartOffsetMs": 100.0}}
                host_raw = json.dumps(host, ensure_ascii=True, separators=(",", ":")).encode("ascii") + b"\n"
                (output / "host-result.json").write_bytes(host_raw)
                parent_path = root / "parent.json"
                self._write_parent(parent_path, elapsed=elapsed, exit_code=exit_code, terminated=terminated)
                with mock.patch.object(reader, "_load_input_auditor", side_effect=AssertionError("deep audit reached")) as audit:
                    with self.assertRaises(reader.ReaderError) as raised:
                        reader._validate_reservation_suite(pins_path, pins_sha, output, parent_path)
                self.assertEqual(raised.exception.code, expected)
                audit.assert_not_called()


def run_literal() -> tuple[int, dict[str, Any], str | None]:
    runner = unittest.TextTestRunner(stream=io.StringIO(), verbosity=0)
    positive = runner.run(unittest.defaultTestLoader.loadTestsFromTestCase(LiteralPositiveTests))
    negative = runner.run(unittest.defaultTestLoader.loadTestsFromTestCase(LiteralNegativeTests))
    success = positive.wasSuccessful() and negative.wasSuccessful()
    summary = {
        "schema": reader.LITERAL_SCHEMA,
        "status": "PASS_LITERAL_ORACLE_ONLY" if success else "FAIL_LITERAL_ORACLE_ONLY",
        "positiveCount": 12,
        "negativeCount": 16,
        "passedPositiveCount": 12 - len(positive.failures) - len(positive.errors),
        "passedNegativeCount": 16 - len(negative.failures) - len(negative.errors),
    }
    failed = (positive.failures + positive.errors + negative.failures + negative.errors)
    failure_code = None if success else (failed[0][0].id().split(".")[-1] if failed else "LITERAL_FAILURE")
    return (0 if success else 2), summary, failure_code


SEMANTIC_CASES = (
    ("P9N01_CRLF_OR_MISSING_FINAL_LF", "P9_OPERAND_FRAMING"),
    ("P9N02_DUPLICATE_JSON_KEY", "P9_OPERAND_JSON"),
    ("P9N03_EXTRA_OR_MISSING_HEADER_KEY", "P9_HEADER_FIELDS"),
    ("P9N04_PAD_REORDERED_OR_DUPLICATED", "P9_PAD_ORDER"),
    ("P9N05_PAD_MISSING_OR_EXTRA", "P9_PAD_COUNT"),
    ("P9N06_BOOL_OR_FLOAT_AS_INTEGER", "P9_INTEGER_TYPE"),
    ("P9N07_UNKNOWN_ROLE_OR_ENUM", "P9_PAD_ROLE"),
    ("P9N08_ROLE_WIDTH_DISAGREEMENT", "P9_PAD_WIDTH"),
    ("P9N09_BAD_GRID_DERIVATION", "P9_GRID_DERIVATION"),
    ("P9N10_WRONG_PINNED_OUTLET_ORIGIN_OR_VALUE", "P9_OUTLET_SOURCE_BINDING"),
    ("P9N11_INCONSISTENT_COURTYARD_FOR_COMPONENT", "P9_OWNER_COURTYARD"),
    ("P9N12_PREPARED_CORRIDOR_DIFFERS_FROM_DERIVATION", "P9_CORRIDOR_PARITY"),
    ("P9N13_WRONG_TARGET_OR_QUERY_IDENTITY", "P9_QUERY_IDENTITY"),
    ("P9N14_CAPTURED_SLOT_DIFFERS_FROM_ORACLE", "P9_SLOT_MISMATCH"),
    ("P9N15_TAP_SENTINEL_NOT_CAUGHT_ONCE_OR_DTO_NOT_DISCARDED", "P9_TAP_THROW_PROOF"),
    ("P9N16_UNEXPECTED_SIDECAR_FILE_OR_INDEX_REFERENCE", "P9_OUTPUT_CENSUS"),
)


def _decode_object_lines(raw: bytes):
    return [json.loads(line.decode("ascii")) for line in raw[:-1].split(b"\n")]


def _semantic_observe_validator(private):
    context = private["context"]
    run = private["semanticRuns"]["OBSERVE"]
    operation = next(row for row in private["operations"] if row["wrapperMode"] == "OBSERVE")
    return {
        "run_id": operation["runId"],
        "input_sha": private["pinsSha"],
        "source_sha": context["sourceSha"],
        "wire_sha": context["p8WireSha"],
        "parent_prefix_sha": run["parentPrefixSha256"],
        "parent_graph_sha": run["parentGraphSha256"],
        "expected_identity": run["expectedIdentity"],
        "p4_pad_map": run["p4PadMap"],
        "expected_empty_component_face": True,
    }


def run_semantic_controls(pins_path: str, pins_sha: str, output_root: str,
                          parent_receipt: str) -> dict[str, Any]:
    started = time.perf_counter_ns()
    _public, private = reader._validate_reservation_suite(
        pins_path, pins_sha, output_root, parent_receipt, return_private=True)
    if private["mode"] != "SUITE":
        raise reader.ReaderError("P9_SEMANTIC_MODE")
    data = private["data"]
    observe_path = "runs/OBSERVE/p9/reservation-operands.jsonl"
    tap_path = "runs/TAP_THROW/p9/reservation-operands.jsonl"
    actual_observe = data[observe_path]
    actual_tap = data[tap_path]
    args = _semantic_observe_validator(private)
    observed = reader.validate_observe_operands(actual_observe, **args)
    positive = reader.analyze_slot(observed)
    if (positive["match"] is not True or positive != private["analysis"]):
        raise reader.ReaderError("P9_SEMANTIC_POSITIVE_CONTROL")

    cases = []

    def expect(case_id, expected_code, callback):
        try:
            callback()
        except reader.ReaderError as exc:
            actual_code = exc.code
        else:
            actual_code = "NO_REJECTION"
        if actual_code != expected_code:
            raise reader.ReaderError("P9_SEMANTIC_CONTROL_FAILURE")
        cases.append({"id": case_id, "expectedCode": expected_code,
                      "actualCode": actual_code, "status": "PASS_EXPECTED_REJECTION"})

    raw_lines = actual_observe.splitlines(keepends=True)
    expect(SEMANTIC_CASES[0][0], SEMANTIC_CASES[0][1],
           lambda: reader.validate_observe_operands(actual_observe.replace(b"\n", b"\r\n"), **args))

    def duplicate_key():
        first = raw_lines[0][:-1]
        raw = first[:-1] + b',"kind":"header"}\n' + b"".join(raw_lines[1:])
        reader.validate_observe_operands(raw, **args)

    expect(*SEMANTIC_CASES[1], duplicate_key)

    def mutate_observe(case_number, mutator, *, check_match=False):
        rows = _decode_object_lines(actual_observe)
        mutator(rows)
        raw = _encode_rows(rows)
        result = reader.validate_observe_operands(raw, **args)
        if check_match:
            reader.require_slot_match(reader.analyze_slot(result))
        return result

    expect(*SEMANTIC_CASES[2], lambda: mutate_observe(2, lambda rows: rows[0].update({"unexpected": True})))
    expect(*SEMANTIC_CASES[3], lambda: mutate_observe(3, lambda rows: rows.__setitem__(slice(1, 3), [rows[2], rows[1]])))
    expect(*SEMANTIC_CASES[4], lambda: reader.validate_observe_operands(
        _encode_rows([*_decode_object_lines(actual_observe)[:-2][:-1], _decode_object_lines(actual_observe)[-1]]), **args))
    expect(*SEMANTIC_CASES[5], lambda: mutate_observe(5, lambda rows: rows[1].update({"index": True})))
    expect(*SEMANTIC_CASES[6], lambda: mutate_observe(6, lambda rows: rows[1].update({"routingRole": "UNKNOWN"})))
    expect(*SEMANTIC_CASES[7], lambda: mutate_observe(7, lambda rows: rows[1].update({"padTraceWidth": 12})))
    expect(*SEMANTIC_CASES[8], lambda: mutate_observe(8, lambda rows: rows[0].update({"gridWidth": rows[0]["gridWidth"] + 1})))
    expect(*SEMANTIC_CASES[9], lambda: mutate_observe(9, lambda rows: rows[0].update({"escapeOutletLand": 0})))

    def bad_component_courtyard(rows):
        pads = rows[1:-1]
        pair = None
        first_by_component = {}
        for index, pad in enumerate(pads):
            if pad["componentId"] in first_by_component:
                pair = (first_by_component[pad["componentId"]], index)
                break
            first_by_component[pad["componentId"]] = index
        if pair is None:
            raise reader.ReaderError("P9_SEMANTIC_FIXTURE_UNAVAILABLE")
        pads[pair[1]]["ownerCourtyard"]["x"] += 1

    expect(*SEMANTIC_CASES[10], lambda: mutate_observe(10, bad_component_courtyard))
    expect(*SEMANTIC_CASES[11], lambda: mutate_observe(
        11, lambda rows: rows[1]["preparedEscapeCorridor"].update(
            {"x": rows[1]["preparedEscapeCorridor"]["x"] + 1})))
    expect(*SEMANTIC_CASES[12], lambda: mutate_observe(12, lambda rows: rows[0]["identity"].update({"bit": 4})))

    def wrong_slot():
        rows = _decode_object_lines(actual_observe)
        expected = private["analysis"]["expectedValue"]
        candidate = next((row["netId"] for row in rows[1:-1] if row["netId"] != expected), None)
        if candidate is None:
            candidate = "" if expected != "" else None
        rows[-1]["slotValue"] = candidate
        parsed = reader.validate_observe_operands(_encode_rows(rows), **args)
        reader.require_slot_match(reader.analyze_slot(parsed))

    expect(*SEMANTIC_CASES[13], wrong_slot)

    def bad_tap_sentinel():
        rows = _decode_object_lines(actual_tap)
        rows[-1]["tapThrow"]["caught"] = False
        p8_run = private["semanticRuns"]["TAP_THROW"]
        operation = next(row for row in private["operations"] if row["wrapperMode"] == "TAP_THROW")
        reader.validate_control_operands(
            _encode_rows(rows), mode="TAP_THROW", run_id=operation["runId"],
            input_sha=private["pinsSha"], source_sha=private["context"]["sourceSha"],
            wire_sha=private["context"]["p8WireSha"],
            parent_prefix_sha=p8_run["parentPrefixSha256"], parent_graph_sha=p8_run["parentGraphSha256"])

    expect(*SEMANTIC_CASES[14], bad_tap_sentinel)

    def unexpected_reference():
        reader.validate_output_reference(
            {"path": "runs/OBSERVE/p9/unexpected.json", "bytes": len(actual_observe),
             "sha256": hashlib.sha256(actual_observe).hexdigest()},
            observe_path, actual_observe)

    expect(*SEMANTIC_CASES[15], unexpected_reference)

    context = private["context"]
    if context["auditor"].recheck_inputs(context) is not True:
        raise reader.ReaderError("P9_SOURCE_RECHECK")
    expected_files, expected_dirs = reader._expected_tree("SUITE")
    current_data = reader._tree_census(Path(output_root).resolve(strict=True), expected_files, expected_dirs)
    parent_current = reader._stable_read(Path(parent_receipt).resolve(strict=True), reader.MAX_LINE_BYTES,
                                        "P9_PARENT_RECEIPT")
    unchanged = current_data == data and parent_current == private["parentRaw"]
    if not unchanged:
        raise reader.ReaderError("P9_SEMANTIC_OUTPUT_MUTATION")
    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000.0
    if elapsed_ms > 180_000:
        raise reader.ReaderError("P9_SEMANTIC_WALL_LIMIT")
    return {
        "schema": "tsj-q100-reservation-semantic-controls-v1",
        "status": "PASS_SYNTHETIC_RESERVATION_CONTROLS_ONLY",
        "syntheticFixtures": True,
        "passed": len(cases),
        "positiveControl": True,
        "cases": cases,
        "genuineOutputsUnchanged": unchanged,
        "layoutQualification": False,
    }


def main(argv=None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if args == ["--literal"]:
        exit_code, summary, failure_code = run_literal()
        if exit_code == 0:
            sys.stdout.buffer.write(json.dumps(summary, ensure_ascii=True, separators=(",", ":")).encode("ascii") + b"\n")
            sys.stdout.buffer.flush()
        else:
            sys.stderr.buffer.write(f"P9_PROVENANCE_LITERAL_REJECT {failure_code}\n".encode("ascii"))
            sys.stderr.buffer.flush()
        return exit_code
    if len(args) == 4:
        try:
            result = run_semantic_controls(*args)
            sys.stdout.buffer.write(_canonical(result) + b"\n")
            sys.stdout.buffer.flush()
            return 0
        except reader.ReaderError as exc:
            sys.stderr.buffer.write(f"P9_PROVENANCE_SEMANTIC_REJECT {exc.code}\n".encode("ascii"))
            return 2
        except Exception:
            sys.stderr.buffer.write(b"P9_PROVENANCE_SEMANTIC_REJECT UNEXPECTED_FAILURE\n")
            return 2
    sys.stderr.buffer.write(b"P9_PROVENANCE_LITERAL_REJECT ARGUMENTS\n")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
