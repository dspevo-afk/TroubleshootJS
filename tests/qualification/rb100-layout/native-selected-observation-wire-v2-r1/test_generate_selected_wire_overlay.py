"""Source and real-filesystem tests; no Java/session/build execution.

Run with Python -I -B and exactly --study Q --base-generator P --io-helper E.
All substantial fixtures live in a task-owned OS temporary directory.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

OPTIONS = None
GENERATOR_PATH = Path(__file__).resolve().with_name("generate_selected_wire_overlay.py")
g = types.ModuleType("_selected_wire_generator_under_test")
g.__file__ = str(GENERATOR_PATH)
exec(compile(GENERATOR_PATH.read_bytes(), str(GENERATOR_PATH), "exec"), g.__dict__)

OBSERVATION_KEYS = (
    "schema", "mode", "state", "callbackCount", "foundCallbackCount",
    "nonFoundCallbackCount", "firstNetOrdinal", "lastNetOrdinal",
    "lastCallbackTotalExpanded", "selectedCallbackOrdinal", "transientTupleCopies",
    "transientFieldCopies", "validationCount", "observerUnits", "copiedEntries",
    "copyChunks", "selectedSnapshot", "selectedHead",
)
HEAD_KEYS = (
    "branchOrdinal", "cell", "layer", "direction", "transitions", "cost",
    "storedHeuristic", "serial", "key", "storedPriority",
)
TERMINAL_BINDINGS = (
    ("pass", "Integer.valueOf(pass)"),
    ("netId", "netId"), ("startPadId", "startPadId"), ("rootPadId", "rootPadId"),
    ("startX", "Integer.valueOf(startX)"), ("startY", "Integer.valueOf(startY)"),
    ("rootX", "Integer.valueOf(rootX)"), ("rootY", "Integer.valueOf(rootY)"),
    ("gridWidth", "Integer.valueOf(gridWidth)"), ("gridHeight", "Integer.valueOf(gridHeight)"),
    ("goalsTop", "Integer.valueOf(goalsTop)"), ("goalsBottom", "Integer.valueOf(goalsBottom)"),
    ("prefixViaCount", "Integer.valueOf(prefixViaCount)"),
    ("reachedPadCount", "Integer.valueOf(reachedPadCount)"),
    ("initialManhattanDistance", "Integer.valueOf(initialManhattanDistance)"),
    ("minimumExploredManhattanDistance", "Integer.valueOf(minimumExploredManhattanDistance)"),
    ("branchExpanded", "Integer.valueOf(branchExpanded)"), ("totalExpanded", "Integer.valueOf(totalExpanded)"),
    ("expandedTop", "Integer.valueOf(expandedTop)"), ("expandedBottom", "Integer.valueOf(expandedBottom)"),
    ("frontierCount", "Integer.valueOf(frontierCount)"), ("bestStateCount", "Integer.valueOf(bestStateCount)"),
    ("terminalReason", "terminalReason"),
    ("relaxedBoundStarted", "Boolean.valueOf(relaxedBoundStarted)"),
    ("relaxedBoundComplete", "Boolean.valueOf(relaxedBoundComplete)"),
    ("relaxedBoundObservationChecked", "Boolean.valueOf(relaxedBoundObservationChecked)"),
    ("relaxedBoundLiveLayoutStable", "Boolean.valueOf(relaxedBoundLiveLayoutStable)"),
    ("relaxedBoundRouterStateStable", "Boolean.valueOf(relaxedBoundRouterStateStable)"),
    ("relaxedBoundRouterEffectsStable", "Boolean.valueOf(relaxedBoundRouterEffectsStable)"),
    ("relaxedBoundWorkUnits", "Integer.valueOf(relaxedBoundWorkUnits)"),
    ("relaxedBoundOrdinaryProbes", "Integer.valueOf(relaxedBoundOrdinaryProbes)"),
    ("relaxedBoundViaProbes", "Integer.valueOf(relaxedBoundViaProbes)"),
    ("relaxedBoundRefreshes", "Integer.valueOf(relaxedBoundRefreshes)"),
)


def span(raw, start, end):
    """Independent exact-once source-section oracle, including the start."""
    if raw.count(start) != 1 or raw.count(end) != 1:
        raise AssertionError("SOURCE_ORACLE_ANCHOR_COUNT")
    begin = raw.index(start)
    finish = raw.index(end)
    if finish <= begin:
        raise AssertionError("SOURCE_ORACLE_ANCHOR_ORDER")
    return raw[begin:finish]


class OverlayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if OPTIONS is None:
            raise RuntimeError("TEST_SOURCE_OPTIONS_REQUIRED")
        cls.source = Path(OPTIONS["--study"]).read_bytes()
        cls.base_raw = Path(OPTIONS["--base-generator"]).read_bytes()
        cls.io_raw = Path(OPTIONS["--io-helper"]).read_bytes()
        for raw, expected in (
            (cls.source, "d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd"),
            (cls.base_raw, "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"),
            (cls.io_raw, "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"),
        ):
            if hashlib.sha256(raw).hexdigest() != expected:
                raise RuntimeError("TEST_FIXTURE_HASH")
        cls.derived, cls.changes = g.derive(cls.source)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tsj-selected-wire-generator-tests-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.study = self.root / "Study.java"
        self.base_path = self.root / "base.py"
        self.io_path = self.root / "io.py"
        self.output = self.root / "output"
        self.study.write_bytes(self.source)
        self.base_path.write_bytes(self.base_raw)
        self.io_path.write_bytes(self.io_raw)
        self.output.mkdir()
        self.options = {
            "--study": str(self.study), "--base-generator": str(self.base_path),
            "--io-helper": str(self.io_path), "--output-dir": str(self.output),
        }
        self.base = g.load_base_generator(str(self.base_path))
        self.checked_io = self.base.load_io(str(self.io_path))

    def assert_code(self, owner, code, call):
        with self.assertRaises(owner) as caught:
            call()
        self.assertIs(type(caught.exception), owner)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(str(caught.exception), code)

    def generate_with_selected_io(self):
        # Keep the real selected implementation and its exact exception class;
        # only avoid loading a second identity of that same pinned module.
        with mock.patch.object(g, "load_base_generator", return_value=self.base), \
                mock.patch.object(self.base, "load_io", return_value=self.checked_io):
            return g.generate(self.options)

    def test_pinned_q_derivation_deterministic(self):
        self.assertEqual(g.derive(self.source), (self.derived, self.changes))
        self.assertEqual(self.changes, g.CHANGES)
        self.assertEqual(len(self.changes), 13)
        self.assertIs(type(self.derived), bytes)
        self.assertNotIn(b"\r", self.derived)
        self.assertLessEqual(len(self.derived), g.MAX_SOURCE_BYTES)
        self.assertNotEqual(self.source, self.derived)

    def test_wrong_source_types_sizes_hash_and_line_endings(self):
        for bad in (None, "java", bytearray(self.source), b"", b"x" * (g.MAX_SOURCE_BYTES + 1)):
            self.assert_code(g.OverlayError, "SOURCE_SIZE", lambda bad=bad: g.derive(bad))
        for bad in (b"x" + self.source[1:], self.source + b"\n", self.source.replace(b"\n", b"\r\n")):
            self.assert_code(g.OverlayError, "STUDY_HASH", lambda bad=bad: g.derive(bad))
        crlf = self.source.replace(b"\n", b"\r\n")
        with mock.patch.object(g, "STUDY_SHA256", g.sha(crlf)):
            self.assert_code(g.OverlayError, "STUDY_LINE_ENDINGS", lambda: g.derive(crlf))

    def test_exact_once_anchor_guards_and_inline_end(self):
        self.assertEqual(g.replace_region(b"AoldZ inline", b"A", b"Z", b"new", "X"), b"newZ inline")
        for raw, start, end, expected in (
            (b"AZ", b"B", b"Z", "X_START"),
            (b"AAZ", b"A", b"Z", "X_START"),
            (b"AZZ", b"A", b"Z", "X_END_COUNT"),
            (b"A", b"A", b"Z", "X_END_COUNT"),
            (b"ZA", b"A", b"Z", "X_END"),
        ):
            self.assert_code(g.OverlayError, expected,
                             lambda raw=raw, start=start, end=end: g.replace_region(raw, start, end, b"N", "X"))
        for before in (b"", b"A", b"Z"):
            self.assert_code(g.OverlayError, "SPLICE_COUNT",
                             lambda before=before: g.replace_once(b"AA", before, b"new"))
        self.assert_code(g.OverlayError, "SPLICE_TYPES", lambda: g.replace_once("A", b"A", b"N"))
        self.assertIn(b"    private void unit() { meter.charge(1); unitsCharged++; }\n", self.derived)

    def test_q_callback_stale_tuple_validation_and_publication_preserved(self):
        for start, end in (
            (b"static final class DetachedTerminal {\n",
             b"    void writeResult(java.util.Map<String, Object> out, String p) {\n"),
            (b"static final class TerminalTelemetryObserver {\n",
             b"    synchronized java.util.Map<String, Object> resultMap() {\n"),
            (b"    private void unit() {", b"static final int R9_CANARY_KEYS=11;\n"),
            (b"static final class R9CanaryPublication {\n",
             b"static void runR9OwnerCanaries(Arguments args,long started) throws Exception {\n"),
        ):
            self.assertEqual(span(self.derived, start, end), span(self.source, start, end))
        start = b"static final int R9_CANARY_KEYS=11;\n"
        end = b"static final class R9CanaryPublication {\n"
        expected = span(self.source, start, end).replace(
            b'"tsj-cohort-terminal-canary-v1"', b'"tsj-selected-observation-wire-canary-v2"').replace(
            b'"OWNER_CANARIES_RECORDED_ONLY"', b'"GENERIC_OBSERVATIONS_RECORDED_ONLY"')
        self.assertEqual(span(self.derived, start, end), expected)

    def test_main_clock_first_and_only_new_mode_before_delegation(self):
        main = span(self.derived, b"    public static void main(", b"    static final class Arguments {\n")
        self.assertTrue(main.split(b"{\n", 1)[1].startswith(b"        long started=System.nanoTime();\n"))
        self.assertIn(b"args.length!=4", main)
        self.assertIn(b'"SELECTED_OBSERVATION_CANARIES_V2".equals(args[0])', main)
        self.assertEqual(main.count(b"SelectedObservationWireCanary.run(args,started)"), 1)
        self.assertLess(main.index(b"SELECTED_WIRE_CANARY_ONLY"), main.index(b"SelectedObservationWireCanary.run"))
        for forbidden in (b"Session", b"new Parent", b"CohortTerminalTelemetryInput", b"Arguments.parse", b"readInput", b"assemble("):
            self.assertNotIn(forbidden, main)
        for start, end in (
            (b"    static Comparison assemble(", b"    /** Structural minimum and separately metered logical traversal work. */\n"),
            (b"static void runR9OwnerCanaries(", b"\n}\n\n}\n"),
        ):
            body = span(self.derived, start, end)
            self.assertIn(b'throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY")', body)
            self.assertNotIn(b"new Parent", body)
            self.assertNotIn(b"readInput", body)

    def test_maps_exact_order_and_head_primitive_writer(self):
        for body in (g.NULL_OBSERVATION, g.RESULT_MAP):
            actual = tuple(x.decode("ascii") for x in re.findall(rb'out.put\("([A-Za-z]+)"', body))
            self.assertEqual(actual, OBSERVATION_KEYS)
            self.assertIn(b"tsj-cohort-terminal-observation-v2", body)
            self.assertNotIn(b"observation-v1", body)
        head = tuple(x.decode("ascii") for x in re.findall(rb'out.put\("([A-Za-z]+)"', g.HEAD_WRITER))
        self.assertEqual(head, HEAD_KEYS)
        self.assertEqual(g.HEAD_WRITER.count(b"Integer.valueOf("), 10)
        self.assertNotIn(b"new ", g.HEAD_WRITER)
        self.assertIn(b"java.util.Collections.unmodifiableMap(head)", g.RESULT_MAP)
        self.assertIn(b"java.util.Collections.unmodifiableMap(out)", g.RESULT_MAP)

    def test_terminal_writer_preserves_33_independent_bindings_without_arrays(self):
        writer = span(self.derived,
                      b"    void writeResult(java.util.Map<String, Object> out, String p) {\n",
                      b"static final class DetachedHead {\n")
        bindings = tuple((key.decode("ascii"), value.decode("ascii")) for key, value in
                         re.findall(rb'out.put\(p\+"([A-Za-z]+)",([^;]+)\);', writer))
        self.assertEqual(len(bindings), 33)
        self.assertEqual(bindings, TERMINAL_BINDINGS)
        self.assertNotIn(b"String[]", writer)
        self.assertNotIn(b"Object[]", writer)
        self.assertNotIn(b"new ", writer)
        self.assertEqual(writer, g.TERMINAL_WRITER + b"}\n\n")

    def test_v2_atomic_copy_and_selection_guards(self):
        for required in (
            b"if (!sealed)", b"if (poisoned)", b"inCallback", b"accepted!=dispatches",
            b"found+failures!=dispatches", b"failures>1", b"headCopies<0 || headCopies>1",
            b"copiedSlots!=33L*dispatches+10L*headCopies",
            b"copyChunks!=dispatches+headCopies", b"headReleases!=headCopies",
            b"selectedCallbackOrdinal!=dispatches", b"(headCopies==1)!=(selectedHead!=null)",
            b'"BRANCH_SEARCH_LIMIT".equals(selected.terminalReason)',
            b"checks>94L*dispatches+12L*headCopies",
            b"unitsCharged!=69L*dispatches+checks+21L*headCopies",
            b'out.put("transientTupleCopies",Integer.valueOf((int)copyChunks))',
        ):
            self.assertIn(required, g.RESULT_MAP)
        self.assertIn(b"obs.size()!=18", g.V2_SCHEMA_GUARD)
        self.assertIn(b"tsj-cohort-terminal-observation-v2", g.V2_SCHEMA_GUARD)
        self.assertIn(b'obs.containsKey("selectedHead")', g.V2_SCHEMA_GUARD)
        self.assertIn(b'"FIRST_NON_FOUND".equals(state)', g.HEAD_STATE_GUARD)
        self.assertIn(b'"BRANCH_SEARCH_LIMIT".equals', g.HEAD_STATE_GUARD)
        self.assertIn(b"((Map<?,?>)selectedHead).size()!=10", g.HEAD_STATE_GUARD)

    def test_shared_materializer_actual_order_and_metadata_split(self):
        body = g.MATERIALIZER
        self.assertIn(b"if (sink==null) return nullObservation(m);", body)
        self.assertEqual(body.count(b"m.charge(1)"), 4)
        self.assertEqual(re.findall(rb"m.copy\((\d+)\)", body), [b"18", b"33", b"10", b"18"])
        operations = (
            b"m.copy(18)", b'field(sink.observer,"selected")', b"m.copy(33)",
            b'field(sink.observer,"selectedHead")', b"m.copy(10)",
            b"sink.observer.resultMap()", b"m.copy(18)",
            b"return new java.util.LinkedHashMap<String,Object>(observerResult)",
        )
        position = -1
        for op in operations:
            position = body.index(op, position + 1)
        self.assertEqual(self.derived.count(b"Map<String,Object> observation=materializeObservation(sink,m);"), 1)
        self.assertNotIn(g.OLD_MATERIALIZATION, self.derived)
        self.assertIn(b"m.copy(18)", g.NULL_OBSERVATION)
        self.assertNotIn(b"m.charge(", g.NULL_OBSERVATION)

    def test_real_filesystem_success_exclusive_readback_and_sanitized_proof(self):
        proof = g.generate(self.options)
        self.assertEqual(set(x.name for x in self.output.iterdir()),
                         {"CohortTerminalTelemetryStudy.java", "SOURCE_PROOF.json"})
        self.assertEqual((self.output / "CohortTerminalTelemetryStudy.java").read_bytes(), self.derived)
        raw = (self.output / "SOURCE_PROOF.json").read_bytes()
        self.assertEqual(json.loads(raw), proof)
        self.assertEqual(proof["derivedSha256"], hashlib.sha256(self.derived).hexdigest())
        self.assertEqual(proof["studyParentSha256"], g.STUDY_SHA256)
        self.assertEqual(proof["observationKeys"], 18)
        self.assertEqual(proof["authoredInvocations"], 0)
        self.assertEqual(proof["changes"], list(g.CHANGES))
        for local in (str(self.root), str(self.study), str(self.output)):
            self.assertNotIn(local.encode("utf-8"), raw)
        self.assertNotRegex(raw.decode("ascii"), r"[A-Za-z]:[\\/]")
        self.assert_code(g.OverlayError, "OUTPUT_NOT_EMPTY", lambda: g.generate(self.options))

    def test_occupied_output_and_study_hash_fail_before_writes(self):
        occupied = self.output / "keep.bin"
        occupied.write_bytes(b"preserve")
        self.assert_code(g.OverlayError, "OUTPUT_NOT_EMPTY", lambda: g.generate(self.options))
        self.assertEqual(occupied.read_bytes(), b"preserve")
        occupied.unlink()
        self.study.write_bytes(self.source + b"\n")
        self.assert_code(g.OverlayError, "STUDY_HASH", lambda: g.generate(self.options))
        self.assertEqual(list(self.output.iterdir()), [])

    def test_base_and_helper_errors_have_correct_owners(self):
        self.assert_code(g.OverlayError, "BASE_GENERATOR_ABSOLUTE", lambda: g.load_base_generator("relative.py"))
        for bad in (None, "", "a\0b"):
            self.assert_code(g.OverlayError, "BASE_GENERATOR_PATH", lambda bad=bad: g.load_base_generator(bad))
        self.base_path.write_bytes(b"raise RuntimeError('MUST_NOT_EXECUTE')\n")
        self.assert_code(g.OverlayError, "BASE_GENERATOR_HASH", lambda: g.load_base_generator(str(self.base_path)))
        self.io_path.write_bytes(b"raise RuntimeError('MUST_NOT_EXECUTE')\n")
        self.assert_code(self.base.OverlayError, "IO_HELPER_HASH", lambda: self.base.load_io(str(self.io_path)))
        self.assert_code(self.base.OverlayError, "IO_HELPER_ABSOLUTE", lambda: self.base.load_io("relative.py"))

    def test_inherited_io_rejects_nonregular_missing_empty_and_oversize(self):
        error = self.checked_io.ExportError
        self.assert_code(error, "OWNER_SOURCE_TYPE",
                         lambda: self.checked_io._read_regular(self.output, 100, "OWNER_SOURCE"))
        self.study.write_bytes(b"")
        self.assert_code(error, "OWNER_SOURCE_SIZE", lambda: self.generate_with_selected_io())
        self.study.write_bytes(b"x" * (g.MAX_SOURCE_BYTES + 1))
        self.assert_code(error, "OWNER_SOURCE_SIZE", lambda: self.generate_with_selected_io())
        self.assert_code(error, "OWNER_SOURCE_ABSOLUTE",
                         lambda: self.checked_io._read_regular("relative.java", 100, "OWNER_SOURCE"))
        self.study.unlink()
        self.assert_code(error, "OWNER_SOURCE_PATH", lambda: self.generate_with_selected_io())

    def test_inherited_io_occupied_file_is_exclusive_and_preserved(self):
        path = self.output / "keep.txt"
        path.write_bytes(b"original")
        self.assert_code(self.checked_io.ExportError, "OUTPUT_FILE_EXISTS",
                         lambda: self.checked_io._write_exclusive(path, b"replacement", 100, "OUTPUT_FILE"))
        self.assertEqual(path.read_bytes(), b"original")

    def test_real_noncanonical_paths_reject_with_correct_owner(self):
        base_alias = self.output / ".." / "base.py"
        self.assert_code(g.OverlayError, "BASE_GENERATOR_CANONICAL",
                         lambda: g.load_base_generator(str(base_alias)))
        source_alias = self.output / ".." / "Study.java"
        self.assert_code(self.checked_io.ExportError, "OWNER_SOURCE_CANONICAL",
                         lambda: self.checked_io._read_regular(source_alias, g.MAX_SOURCE_BYTES, "OWNER_SOURCE"))

    def test_raw_dot_and_separator_aliases_reject_before_loading_or_writing(self):
        for option, code in (("--study", "OWNER_SOURCE"), ("--base-generator", "BASE_GENERATOR"),
                             ("--io-helper", "IO_HELPER"), ("--output-dir", "OUTPUT_DIRECTORY")):
            original = Path(self.options[option])
            aliases = (str(original.parent) + os.sep + "." + os.sep + original.name,
                       str(original.parent) + os.sep + os.sep + original.name)
            for alias in aliases:
                with self.subTest(option=option, alias=alias), \
                        mock.patch.object(g, "load_base_generator") as loader:
                    self.assert_code(g.OverlayError, code + "_CANONICAL",
                                     lambda: g.generate({**self.options, option: alias}))
                    loader.assert_not_called()
                    self.assertEqual(list(self.output.iterdir()), [])

    def test_real_base_empty_oversize_and_nonregular_reject(self):
        for body in (b"", b"x" * (g.MAX_BASE_BYTES + 1)):
            self.base_path.write_bytes(body)
            self.assert_code(g.OverlayError, "BASE_GENERATOR_FILE",
                             lambda: g.load_base_generator(str(self.base_path)))
        self.assert_code(g.OverlayError, "BASE_GENERATOR_FILE",
                         lambda: g.load_base_generator(str(self.output)))

    def test_real_output_closure_guard_preserves_unexpected_entry(self):
        real_write = self.checked_io._write_exclusive

        def write_and_add(path, raw, limit, code):
            real_write(path, raw, limit, code)
            if path.name == "SOURCE_PROOF.json":
                (self.output / "unexpected.txt").write_bytes(b"preserve evidence")

        with mock.patch.object(self.checked_io, "_write_exclusive", side_effect=write_and_add):
            self.assert_code(g.OverlayError, "OUTPUT_CLOSURE", self.generate_with_selected_io)
        self.assertEqual((self.output / "unexpected.txt").read_bytes(), b"preserve evidence")

    def test_real_readback_guard_retains_changed_bytes(self):
        real_write = self.checked_io._write_exclusive

        def write_and_change(path, raw, limit, code):
            real_write(path, raw, limit, code)
            if path.name == "SOURCE_PROOF.json":
                (self.output / "CohortTerminalTelemetryStudy.java").write_bytes(b"changed")

        with mock.patch.object(self.checked_io, "_write_exclusive", side_effect=write_and_change):
            self.assert_code(g.OverlayError, "OUTPUT_READBACK_CHANGED", self.generate_with_selected_io)
        self.assertEqual((self.output / "CohortTerminalTelemetryStudy.java").read_bytes(), b"changed")

    def make_symlink_or_skip(self, link, target, directory=False):
        try:
            os.symlink(target, link, target_is_directory=directory)
        except (OSError, NotImplementedError):
            self.skipTest("Host does not permit os.symlink; real junction CLI gate remains required")

    def test_real_symlink_base_rejected_before_loading(self):
        link = self.root / "linked-base.py"
        self.make_symlink_or_skip(link, self.base_path)
        self.assert_code(g.OverlayError, "BASE_GENERATOR_REPARSE", lambda: g.load_base_generator(str(link)))

    def test_real_symlink_directory_base_rejected(self):
        link = self.root / "linked-dir"
        self.make_symlink_or_skip(link, self.output, directory=True)
        (self.output / "base.py").write_bytes(self.base_raw)
        self.assert_code(g.OverlayError, "BASE_GENERATOR_REPARSE",
                         lambda: g.load_base_generator(str(link / "base.py")))

    def test_real_symlink_io_and_source_reject_with_selected_owners(self):
        link = self.root / "linked-io.py"
        self.make_symlink_or_skip(link, self.io_path)
        self.assert_code(self.base.OverlayError, "IO_HELPER_REPARSE", lambda: self.base.load_io(str(link)))
        source_link = self.root / "linked-study.java"
        os.symlink(self.study, source_link)
        # The checked helper resolves first, so this symlink is NONCANONICAL.
        self.assert_code(self.checked_io.ExportError, "OWNER_SOURCE_CANONICAL",
                         lambda: self.checked_io._read_regular(source_link, g.MAX_SOURCE_BYTES, "OWNER_SOURCE"))

    def test_cli_parser_and_generator_option_guards(self):
        pairs = [value for pair in self.options.items() for value in pair]
        self.assertEqual(g.parse_args(pairs), self.options)
        self.assertEqual(g.parse_args(tuple(pairs)), self.options)
        for bad in (None, {}, pairs[:-1], pairs + ["extra", "value"],
                    ["--study", "a"] * 4,
                    pairs[:1] + [""] + pairs[2:],
                    pairs[:1] + ["a\0b"] + pairs[2:],
                    pairs[:1] + ["--bad"] + pairs[2:]):
            self.assert_code(g.OverlayError, "CLI_ARGUMENTS", lambda bad=bad: g.parse_args(bad))
        for bad in (None, {}, {**self.options, "--other": "value"},
                    {**self.options, "--study": None}):
            self.assert_code(g.OverlayError, "GENERATOR_OPTIONS", lambda bad=bad: g.generate(bad))

    def test_cli_isolation_and_sanitized_argument_failure(self):
        ordinary = subprocess.run([sys.executable, "-B", str(GENERATOR_PATH)],
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
        self.assertEqual(ordinary.returncode, 2)
        self.assertEqual(ordinary.stdout, b"")
        self.assertEqual(ordinary.stderr, ("SELECTED_WIRE_OVERLAY_FAIL PYTHON_ISOLATION" + os.linesep).encode("ascii"))
        isolated = subprocess.run([sys.executable, "-I", "-B", str(GENERATOR_PATH), "--private", str(self.root)],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
        self.assertEqual(isolated.returncode, 2)
        self.assertEqual(isolated.stdout, b"")
        self.assertEqual(isolated.stderr, ("SELECTED_WIRE_OVERLAY_FAIL CLI_ARGUMENTS" + os.linesep).encode("ascii"))

    def test_internal_failure_message_is_sanitized(self):
        # The CLI catches untrusted exception messages; it emits only the code.
        with mock.patch.object(g, "generate", side_effect=RuntimeError(str(self.root))), \
                mock.patch.object(g.sys, "stderr", new_callable=io.StringIO) as stderr, \
                mock.patch.object(g.sys, "stdout", new_callable=io.StringIO) as stdout:
            result = g.main([value for pair in self.options.items() for value in pair])
        self.assertEqual(result, 2)
        self.assertEqual(stdout.getvalue(), "")
        self.assertEqual(stderr.getvalue(), "SELECTED_WIRE_OVERLAY_FAIL INTERNAL_ERROR\n")


def parse_test_options(argv):
    keys = {"--study", "--base-generator", "--io-helper"}
    if len(argv) != 6:
        raise ValueError("TEST_ARGUMENTS")
    result = {}
    for index in range(0, 6, 2):
        key, value = argv[index:index + 2]
        if key not in keys or key in result or not value or value.startswith("--") or "\0" in value:
            raise ValueError("TEST_ARGUMENTS")
        result[key] = value
    if set(result) != keys:
        raise ValueError("TEST_ARGUMENTS")
    return result


if __name__ == "__main__":
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        raise SystemExit("TEST_PYTHON_ISOLATION_REQUIRED")
    OPTIONS = parse_test_options(sys.argv[1:])
    unittest.main(argv=[sys.argv[0]], verbosity=2)
