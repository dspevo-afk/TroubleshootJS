"""Source/checked-I/O tests; no Java, routing Session, build or authored run.

Invoke Python -I -B with explicit --study, --base-generator and --io-helper
pairs. Real generator CLI controls use those pinned sources in OS-temp copies.
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
GENERATOR_PATH = Path(__file__).resolve().with_name("generate_generic_reference_overlay.py")
g = types.ModuleType("_generic_reference_generator_under_test")
g.__file__ = str(GENERATOR_PATH)
exec(compile(GENERATOR_PATH.read_bytes(), str(GENERATOR_PATH), "exec"), g.__dict__)

STUDY_PIN = "88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf"
BASE_PIN = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_PIN = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
WIRE_PIN = "dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6"
EXPECTED_ROOT_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256", "wireContractSha256",
    "references", "sourceReview",
)
MAIN_START = b"    public static void main(String[] args) throws Exception {\n"
MAIN_END = b"    static final class Arguments {\n"
HEADER_START = b"static final int R9_CANARY_KEYS="
HEADER_END = b"static final class R9CanaryPublication {\n"
PATH_START = b"static final int R9_PATH_ROOT="
PATH_END = b"static void writeCanonicalJson(Object value,CanonicalJsonSink sink) throws Exception {\n"


def span(raw, start, end):
    """Exact-once independent source-section oracle, including its start."""
    if raw.count(start) != 1 or raw.count(end) != 1:
        raise AssertionError("SOURCE_ORACLE_ANCHOR_COUNT")
    begin, finish = raw.index(start), raw.index(end)
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
        for raw, expected in ((cls.source, STUDY_PIN), (cls.base_raw, BASE_PIN),
                              (cls.io_raw, IO_PIN)):
            if hashlib.sha256(raw).hexdigest() != expected:
                raise RuntimeError("TEST_FIXTURE_HASH")
        cls.derived, cls.changes = g.derive(cls.source)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tsj-generic-reference-overlay-tests-")
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
        # Preserve the real selected implementation and its exception identity.
        with mock.patch.object(g, "load_base_generator", return_value=self.base), \
                mock.patch.object(self.base, "load_io", return_value=self.checked_io):
            return g.generate(self.options)

    def cli(self, options=None, *, isolated=True):
        command = [sys.executable]
        if isolated:
            command.append("-I")
        command.extend(["-B", str(GENERATOR_PATH)])
        if options is not None:
            command.extend(value for pair in options.items() for value in pair)
        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              timeout=20)

    def assert_cli_failure(self, result, code):
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, b"")
        self.assertEqual(result.stderr,
                         ("GENERIC_REFERENCE_OVERLAY_FAIL " + code + os.linesep).encode("ascii"))

    def test_pinned_derivation_deterministic_and_bounded(self):
        self.assertEqual(g.derive(self.source), (self.derived, self.changes))
        self.assertEqual(self.changes, (
            "GENERIC_REFERENCE_MAIN", "HEADER_KEY_COUNT", "HEADER_KEYS",
            "HEADER_SCHEMA", "HEADER_STATUS", "HEADER_MEMBERS",
            "GEOMETRY_PATH_STATES", "GEOMETRY_MAP_TRANSITIONS", "GEOMETRY_CONTAINER_TYPES",
        ))
        self.assertEqual(self.changes, g.CHANGES)
        self.assertIs(type(self.derived), bytes)
        self.assertNotIn(b"\r", self.derived)
        self.assertLessEqual(len(self.derived), g.MAX_SOURCE_BYTES)
        self.assertNotEqual(self.source, self.derived)

    def test_source_types_sizes_hash_and_line_endings(self):
        for bad in (None, "java", bytearray(self.source), b"",
                    b"x" * (g.MAX_SOURCE_BYTES + 1)):
            self.assert_code(g.OverlayError, "SOURCE_SIZE", lambda bad=bad: g.derive(bad))
        for bad in (b"x" + self.source[1:], self.source + b"\n",
                    self.source.replace(b"\n", b"\r\n")):
            self.assert_code(g.OverlayError, "STUDY_HASH", lambda bad=bad: g.derive(bad))
        crlf = self.source.replace(b"\n", b"\r\n")
        with mock.patch.object(g, "STUDY_SHA256", g.sha(crlf)):
            self.assert_code(g.OverlayError, "STUDY_LINE_ENDINGS", lambda: g.derive(crlf))

    def test_checked_splice_anchor_failures(self):
        self.assertEqual(g.replace_region(b"AoldZ inline", b"A", b"Z", b"new", "X"),
                         b"newZ inline")
        for raw, start, end, expected in (
            (b"AZ", b"B", b"Z", "X_START"), (b"AAZ", b"A", b"Z", "X_START"),
            (b"AZZ", b"A", b"Z", "X_END_COUNT"), (b"A", b"A", b"Z", "X_END_COUNT"),
            (b"ZA", b"A", b"Z", "X_END"),
        ):
            self.assert_code(g.OverlayError, expected,
                             lambda raw=raw, start=start, end=end:
                             g.replace_region(raw, start, end, b"N", "X"))
        for before in (b"", b"A", b"Z"):
            self.assert_code(g.OverlayError, "SPLICE_COUNT",
                             lambda before=before: g.replace_once(b"AA", before, b"new"))
        self.assert_code(g.OverlayError, "SPLICE_TYPES",
                         lambda: g.replace_once("A", b"A", b"N"))
        self.assert_code(g.OverlayError, "SPLICE_TYPES",
                         lambda: g.replace_region(b"AZ", "A", b"Z", b"N", "X"))
        # Check actual transformation anchors independently of the hash gate.
        for bad, code in (
            (self.source.replace(MAIN_START, b"", 1), "GENERIC_REFERENCE_MAIN_START"),
            (self.source + MAIN_END, "GENERIC_REFERENCE_MAIN_END_COUNT"),
            (self.source.replace(b"R9_CANARY_KEYS=11;", b"R9_CANARY_KEYS=10;", 1),
             "SPLICE_COUNT"),
        ):
            with mock.patch.object(g, "STUDY_SHA256", g.sha(bad)):
                self.assert_code(g.OverlayError, code, lambda bad=bad: g.derive(bad))

    def test_main_clock_first_guard_exact_mode_and_wire_before_delegate(self):
        main = span(self.derived, MAIN_START, MAIN_END)
        self.assertTrue(main[len(MAIN_START):].startswith(b"        long started=System.nanoTime();\n"))
        self.assertIn(b"args==null || args.length!=4", main)
        self.assertIn(b'!"GENERIC_ROUTING_REFERENCE_V1".equals(args[0])', main)
        self.assertIn(b"args[1]==null || !UUID.matcher(args[1]).matches()", main)
        self.assertIn(b"args[2]==null || !SHA.matcher(args[2]).matches()", main)
        self.assertIn(b"args[3]==null || !SHA.matcher(args[3]).matches()", main)
        self.assertIn(b'!"' + WIRE_PIN.encode("ascii") + b'".equals(args[3])', main)
        self.assertEqual(main.count(b"GenericRoutingReferenceCanary.run(args,started);"), 1)
        self.assertLess(main.index(b"GENERIC_ROUTING_REFERENCE_ONLY"),
                        main.index(b"GenericRoutingReferenceCanary.run"))
        for forbidden in (b"Session", b"new Parent", b"CohortTerminalTelemetryInput",
                          b"Arguments.parse", b"readInput", b"assemble(", b"SELECTED_OBSERVATION",
                          b"AUTHORED", b"descriptor", b"System.out"):
            self.assertNotIn(forbidden, main)
        prefix = self.derived[:self.derived.index(MAIN_START)]
        self.assertIn(b'"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"', prefix)
        self.assertIn(b'Pattern.compile("[0-9a-f]{64}")', prefix)

    def test_exact_seven_key_header_and_ordered_map_members(self):
        header = span(self.derived, HEADER_START, HEADER_END)
        self.assertTrue(header.startswith(b"static final int R9_CANARY_KEYS=7;\n"))
        key_switch = span(header, b"static String r9CanaryKey(int index) {\n",
                          b"static void r9HeaderText(")
        actual = tuple((int(index), key.decode("ascii")) for index, key in
                       re.findall(rb'case (\d+):return "([A-Za-z0-9]+)";', key_switch))
        self.assertEqual(actual, tuple(enumerate(EXPECTED_ROOT_KEYS)))
        self.assertIn(b'"tsj-generic-routing-reference-v1".equals(value)', header)
        self.assertIn(b'"GENERIC_NATURAL_REFERENCES_RECORDED_ONLY".equals(value)', header)
        self.assertIn(b'canary.size()==R9_CANARY_KEYS,"R9_CANARY_KEYS"', header)
        self.assertIn(b'r9CanaryKey(i).equals(key),"R9_CANARY_KEY_ORDER"', header)
        self.assertIn(b'!it.hasNext(),"R9_CANARY_KEYS"', header)
        validator = header[header.index(b"static void r9ValidateCanaryHeader("):]
        self.assertNotIn(b"case 7:", validator)
        self.assertNotIn(b"value==null", validator)
        self.assertNotIn(b"new String", header)
        self.assertNotIn(b"new java.util", header)
        self.assertNotIn(b"instanceof java.util.List", header)
        for index, count, error in ((5, 2, "R9_REFERENCE_KEYS"),
                                    (6, 7, "R9_SOURCE_REVIEW_KEYS")):
            expected = ("        case %d:\n"
                        "            meter.charge(1);\n"
                        '            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");\n'
                        "            meter.charge(1);\n"
                        '            r5Require(((java.util.Map<?,?>)value).size()==%d,"%s");\n'
                        "            break;\n") % (index, count, error)
            self.assertIn(expected.encode("ascii"), validator)

    def test_all_bytes_outside_entry_header_and_path_classification_preserved(self):
        self.assertEqual(self.derived[:self.derived.index(MAIN_START)],
                         self.source[:self.source.index(MAIN_START)])
        self.assertEqual(span(self.derived, MAIN_END, PATH_START),
                         span(self.source, MAIN_END, PATH_START))
        self.assertEqual(span(self.derived, PATH_END, HEADER_START),
                         span(self.source, PATH_END, HEADER_START))
        self.assertEqual(self.derived[self.derived.index(HEADER_END):],
                         self.source[self.source.index(HEADER_END):])
        # Existing header text validation and fixed publication prepayments stay exact.
        for start, end in (
            (b"/* Three exact readNative invocations", b"static String r9CanaryKey("),
            (b"static void r9HeaderText(", b"static void r9ValidateCanaryHeader("),
        ):
            self.assertEqual(span(self.derived, start, end), span(self.source, start, end))

    def test_narrow_geometry_source_transitions_and_retired_root_paths(self):
        policy = span(self.derived, PATH_START, PATH_END)
        self.assertTrue(policy.startswith(
            b"static final int R9_PATH_ROOT=0, R9_PATH_ARM=1, R9_PATH_REFERENCES=2,\n"))
        self.assertIn(b"    R9_PATH_FRESH=3, R9_PATH_LAYOUT=4, R9_PATH_COMPONENTS=5,\n"
                      b"    R9_PATH_COMPONENT=6, R9_PATH_GEOMETRY=7, R9_PATH_OTHER=8;\n", policy)
        maps = span(policy, b"static int r9PathMapChild(", b"static int r9PathListChild(")
        self.assertEqual(maps, b'''static int r9PathMapChild(int state,String key) {
    switch (state) {
    case R9_PATH_ROOT:
        return "references".equals(key)?R9_PATH_REFERENCES:R9_PATH_OTHER;
    case R9_PATH_REFERENCES:
        return "OPEN".equals(key) || "BLOCKED".equals(key)?R9_PATH_ARM:R9_PATH_OTHER;
    case R9_PATH_ARM:
        return "run".equals(key)?R9_PATH_FRESH:R9_PATH_OTHER;
    case R9_PATH_FRESH:
        return "layout".equals(key)?R9_PATH_LAYOUT:R9_PATH_OTHER;
    case R9_PATH_LAYOUT:
        return "components".equals(key)?R9_PATH_COMPONENTS:R9_PATH_OTHER;
    case R9_PATH_COMPONENT:
        return "geometryCanonical".equals(key)?R9_PATH_GEOMETRY:R9_PATH_OTHER;
    default:return R9_PATH_OTHER;
    }
}
''')
        for old in (b"R9_PATH_OPTIONAL_ARM", b'"complete"', b'"blocked"',
                    b'"nonBranchQuality"', b'"freshRun"'):
            self.assertNotIn(old, policy)
        for start, end in (
            (b"static int r9PathListChild(", b"static boolean r9PathTypeValid("),
            (b"static int r9PathStringLimit(", PATH_END),
        ):
            self.assertEqual(span(self.derived, start, end), span(self.source, start, end))

    def test_geometry_container_type_source_contract_exact(self):
        types_source = span(self.derived, b"static boolean r9PathTypeValid(",
                            b"static int r9PathStringLimit(")
        self.assertEqual(types_source, b'''static boolean r9PathTypeValid(int state,Object value) {
    switch (state) {
    case R9_PATH_ROOT:case R9_PATH_REFERENCES:case R9_PATH_ARM:case R9_PATH_FRESH:case R9_PATH_COMPONENT:
        return value instanceof java.util.Map;
    case R9_PATH_LAYOUT:
        return value==null || value instanceof java.util.Map;
    case R9_PATH_COMPONENTS:return value instanceof java.util.List;
    case R9_PATH_GEOMETRY:return value instanceof String;
    case R9_PATH_OTHER:return true;
    default:return false;
    }
}
''')
        # Actual formatter and metadata traversal must both retain type guards.
        self.assertIn(b'if (!r9PathTypeValid(pathState,value)) throw new IllegalArgumentException("JSON canary path type");',
                      self.derived)
        self.assertIn(b'r9PathTypeValid(state,value)', self.derived)

    def test_source_policy_path_projection_and_257_4096_4097_contract(self):
        # This inspects the derived Java declarations, not Java runtime behavior.
        # Root still owes real native reflection/tree canaries for these cases.
        policy = span(self.derived, PATH_START, PATH_END)
        maps = span(policy, b"static int r9PathMapChild(", b"static int r9PathListChild(")
        transitions = {}
        for state, body in re.findall(rb"case (R9_PATH_[A-Z_]+):\n(.*?)(?=    case |    default:)",
                                      maps, flags=re.S):
            target = re.search(rb"\?(R9_PATH_[A-Z_]+):R9_PATH_OTHER;", body)
            self.assertIsNotNone(target)
            keys = re.findall(rb'"([A-Za-z]+)"\.equals\(key\)', body)
            for key in keys:
                transitions[(state, key.decode("ascii"))] = target.group(1)
        self.assertEqual(len(transitions), 7)
        self.assertIn(b"return state==R9_PATH_COMPONENTS?R9_PATH_COMPONENT:R9_PATH_OTHER;", policy)
        declared = re.search(rb"return state==R9_PATH_GEOMETRY\?(\d+):(\d+);", policy)
        self.assertIsNotNone(declared)
        geometry_limit, ordinary_limit = map(int, declared.groups())
        self.assertEqual((geometry_limit, ordinary_limit), (4096, 256))

        def projected_state(path):
            state = b"R9_PATH_ROOT"
            for part in path:
                if type(part) is int:
                    state = (b"R9_PATH_COMPONENT" if state == b"R9_PATH_COMPONENTS"
                             else b"R9_PATH_OTHER")
                else:
                    state = transitions.get((state, part), b"R9_PATH_OTHER")
            return state

        for case in ("OPEN", "BLOCKED"):
            path = ("references", case, "run", "layout", "components", 0, "geometryCanonical")
            self.assertEqual(projected_state(path), b"R9_PATH_GEOMETRY")
            for length, accepted in ((257, True), (4096, True), (4097, False)):
                self.assertEqual(length <= geometry_limit, accepted)
        ordinary_paths = (
            ("references.OPEN.run.layout.components.0.geometryCanonical",),
            ("references", "open", "run", "layout", "components", 0, "geometryCanonical"),
            ("references", "OTHER", "run", "layout", "components", 0, "geometryCanonical"),
            ("references", 0, "run", "layout", "components", 0, "geometryCanonical"),
            ("references", "OPEN", "run", "layout", "components", "0", "geometryCanonical"),
            ("references", "OPEN", "run", "layout", "components", 0, "geometrycanonical"),
            ("references", "OPEN", "run", "layout", "components", 0, 0, "geometryCanonical"),
            ("references", "OPEN", "run", "layout", "components", 0, "extra", "geometryCanonical"),
            ("references", "OPEN", "run", "layout", "other", 0, "geometryCanonical"),
            ("complete", "freshRun", "layout", "components", 0, "geometryCanonical"),
            ("blocked", "freshRun", "layout", "components", 0, "geometryCanonical"),
        )
        for path in ordinary_paths:
            with self.subTest(path=path):
                self.assertEqual(projected_state(path), b"R9_PATH_OTHER")
                for length in (257, 4096, 4097):
                    self.assertFalse(length <= ordinary_limit)

    def test_header_diff_contains_only_authorized_scalar_switch_changes(self):
        original = span(self.source, HEADER_START, HEADER_END)
        expected = original.replace(b"R9_CANARY_KEYS=11;", b"R9_CANARY_KEYS=7;", 1)
        old_keys = (b'    case 5:return "complete"; case 6:return "blocked"; case 7:return "nonBranchQuality";\n'
                    b'    case 8:return "genericCallbackThrow"; case 9:return "directOwnerAbortReuse";\n'
                    b'    case 10:return "sourceReview";\n')
        expected = expected.replace(old_keys,
                                    b'    case 5:return "references"; case 6:return "sourceReview";\n', 1)
        expected = expected.replace(b'"tsj-selected-observation-wire-canary-v2"',
                                    b'"tsj-generic-routing-reference-v1"', 1)
        expected = expected.replace(b'"GENERIC_OBSERVATIONS_RECORDED_ONLY"',
                                    b'"GENERIC_NATURAL_REFERENCES_RECORDED_ONLY"', 1)
        old_members = span(original, b"        case 7:\n", b"        }\n        i++;\n")
        new_members = b""
        for index, size, error in ((5, 2, "R9_REFERENCE_KEYS"), (6, 7, "R9_SOURCE_REVIEW_KEYS")):
            new_members += ("        case %d:\n"
                            "            meter.charge(1);\n"
                            '            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");\n'
                            "            meter.charge(1);\n"
                            '            r5Require(((java.util.Map<?,?>)value).size()==%d,"%s");\n'
                            "            break;\n" % (index, size, error)).encode("ascii")
        expected = expected.replace(old_members, new_members, 1)
        self.assertEqual(span(self.derived, HEADER_START, HEADER_END), expected)

    def test_denied_old_entries_and_publication_fee_unchanged(self):
        for start, end in (
            (b"    static Comparison assemble(",
             b"    /** Structural minimum and separately metered logical traversal work. */\n"),
            (b"static void runR9OwnerCanaries(", b"\n}\n\n}\n"),
        ):
            body = span(self.derived, start, end)
            self.assertIn(b'throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY")', body)
            self.assertNotIn(b"new Parent", body)
            self.assertNotIn(b"readInput", body)
        self.assertIn(b"long bytes=count.position,blocks=(bytes+4095L)/4096L,fee=3*blocks;",
                      self.derived)
        for marker in (b"static ResultTreeAccounting accountResultTree(",
                       b"static void writeCanonicalJson(", b"static final class R5CountSink",
                       b"static R9CanaryPublicationMetrics publishR9CanaryStdout("):
            self.assertEqual(self.derived.count(marker), self.source.count(marker))

    def test_generated_exact_two_files_proof_and_determinism(self):
        proof = g.generate(self.options)
        first = {path.name: path.read_bytes() for path in self.output.iterdir()}
        self.assertEqual(set(first), {"CohortTerminalTelemetryStudy.java", "SOURCE_PROOF.json"})
        self.assertEqual(first["CohortTerminalTelemetryStudy.java"], self.derived)
        raw = first["SOURCE_PROOF.json"]
        self.assertEqual(json.loads(raw), proof)
        self.assertEqual(raw, (json.dumps(proof, sort_keys=True, indent=2) + "\n").encode("ascii"))
        self.assertEqual(proof["derivedSha256"], hashlib.sha256(self.derived).hexdigest())
        self.assertEqual(proof["studyParentSha256"], STUDY_PIN)
        self.assertEqual(proof["baseGeneratorSha256"], BASE_PIN)
        self.assertEqual(proof["ioHelperSha256"], IO_PIN)
        self.assertEqual(proof["wireContractSha256"], WIRE_PIN)
        self.assertEqual(proof["rootKeys"], list(EXPECTED_ROOT_KEYS))
        self.assertEqual(proof["references"], "ORDERED_MAP_OPEN_BLOCKED")
        self.assertEqual(proof["sourceReviewKeys"], 7)
        self.assertEqual(proof["authoredInvocations"], 0)
        self.assertIs(proof["fullD"], False)
        self.assertIs(proof["physicalQualification"], False)
        self.assertIs(proof["solverQualification"], False)
        self.assertEqual(proof["publicationFee"], "3*ceil(bytes/4096)")
        self.assertEqual(proof["geometryValuePath"],
                         "references.{OPEN,BLOCKED}.run.layout.components[index].geometryCanonical")
        self.assertEqual(proof["geometryTextLimit"], 4096)
        self.assertEqual(proof["ordinaryTextLimit"], 256)
        self.assertEqual(proof["changes"], list(g.CHANGES))
        for local in (str(self.root), str(self.study), str(self.output)):
            self.assertNotIn(local.encode("utf-8"), raw)
        self.assertNotRegex(raw.decode("ascii"), r"[A-Za-z]:[\\/]")
        second_output = self.root / "second-output"
        second_output.mkdir()
        self.assertEqual(g.generate({**self.options, "--output-dir": str(second_output)}), proof)
        self.assertEqual({path.name: path.read_bytes() for path in second_output.iterdir()}, first)

    def test_occupied_output_and_wrong_study_hash_before_writes(self):
        occupied = self.output / "keep.bin"
        occupied.write_bytes(b"preserve")
        self.assert_code(g.OverlayError, "OUTPUT_NOT_EMPTY", lambda: g.generate(self.options))
        self.assertEqual(occupied.read_bytes(), b"preserve")
        occupied.unlink()
        self.study.write_bytes(self.source + b"\n")
        self.assert_code(g.OverlayError, "STUDY_HASH", lambda: g.generate(self.options))
        self.assertEqual(list(self.output.iterdir()), [])

    def test_bootstrap_and_helper_pin_failures_with_exact_owners(self):
        self.assert_code(g.OverlayError, "BASE_GENERATOR_ABSOLUTE",
                         lambda: g.load_base_generator("relative.py"))
        for bad in (None, "", "a\0b"):
            self.assert_code(g.OverlayError, "BASE_GENERATOR_PATH",
                             lambda bad=bad: g.load_base_generator(bad))
        self.base_path.write_bytes(b"raise RuntimeError('MUST_NOT_EXECUTE')\n")
        self.assert_code(g.OverlayError, "BASE_GENERATOR_HASH",
                         lambda: g.load_base_generator(str(self.base_path)))
        self.io_path.write_bytes(b"raise RuntimeError('MUST_NOT_EXECUTE')\n")
        self.assert_code(self.base.OverlayError, "IO_HELPER_HASH",
                         lambda: self.base.load_io(str(self.io_path)))
        self.assert_code(self.base.OverlayError, "IO_HELPER_ABSOLUTE",
                         lambda: self.base.load_io("relative.py"))

    def test_real_base_empty_oversize_nonregular_and_missing(self):
        for body in (b"", b"x" * (g.MAX_BASE_BYTES + 1)):
            self.base_path.write_bytes(body)
            self.assert_code(g.OverlayError, "BASE_GENERATOR_FILE",
                             lambda: g.load_base_generator(str(self.base_path)))
        self.assert_code(g.OverlayError, "BASE_GENERATOR_FILE",
                         lambda: g.load_base_generator(str(self.output)))
        self.base_path.unlink()
        self.assert_code(g.OverlayError, "BASE_GENERATOR_READ",
                         lambda: g.load_base_generator(str(self.base_path)))

    def test_checked_io_source_nonregular_empty_oversize_missing(self):
        error = self.checked_io.ExportError
        self.assert_code(error, "OWNER_SOURCE_TYPE",
                         lambda: self.checked_io._read_regular(self.output, 100, "OWNER_SOURCE"))
        self.study.write_bytes(b"")
        self.assert_code(error, "OWNER_SOURCE_SIZE", self.generate_with_selected_io)
        self.study.write_bytes(b"x" * (g.MAX_SOURCE_BYTES + 1))
        self.assert_code(error, "OWNER_SOURCE_SIZE", self.generate_with_selected_io)
        self.study.unlink()
        self.assert_code(error, "OWNER_SOURCE_PATH", self.generate_with_selected_io)
        self.assert_code(error, "OWNER_SOURCE_ABSOLUTE",
                         lambda: self.checked_io._read_regular("relative.java", 100, "OWNER_SOURCE"))

    def test_checked_exclusive_write_preserves_occupied_file(self):
        path = self.output / "keep.txt"
        path.write_bytes(b"original")
        self.assert_code(self.checked_io.ExportError, "OUTPUT_FILE_EXISTS",
                         lambda: self.checked_io._write_exclusive(path, b"replacement", 100,
                                                                  "OUTPUT_FILE"))
        self.assertEqual(path.read_bytes(), b"original")

    def test_all_raw_aliases_and_relative_values_reject_before_loader(self):
        for option, code in g.CLI_CODES:
            original = Path(self.options[option])
            aliases = (
                str(original.parent) + os.sep + "." + os.sep + original.name,
                str(original.parent) + os.sep + os.sep + original.name,
                str(original.parent) + os.sep + "output" + os.sep + ".." + os.sep + original.name,
            )
            for alias in aliases:
                with self.subTest(option=option, alias=alias), \
                        mock.patch.object(g, "load_base_generator") as loader:
                    self.assert_code(g.OverlayError, code + "_CANONICAL",
                                     lambda: g.generate({**self.options, option: alias}))
                    loader.assert_not_called()
            with mock.patch.object(g, "load_base_generator") as loader:
                self.assert_code(g.OverlayError, code + "_ABSOLUTE",
                                 lambda: g.generate({**self.options, option: "relative"}))
                loader.assert_not_called()
        self.assertEqual(list(self.output.iterdir()), [])

    def test_real_noncanonical_base_and_checked_source(self):
        self.assert_code(g.OverlayError, "BASE_GENERATOR_CANONICAL",
                         lambda: g.load_base_generator(str(self.output / ".." / "base.py")))
        self.assert_code(self.checked_io.ExportError, "OWNER_SOURCE_CANONICAL",
                         lambda: self.checked_io._read_regular(self.output / ".." / "Study.java",
                                                              g.MAX_SOURCE_BYTES, "OWNER_SOURCE"))

    def test_real_output_closure_guard_retains_unexpected_entry(self):
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
            self.skipTest("Host does not permit os.symlink; real Windows junction CLI gate remains required")

    def test_real_symlink_base_rejected_before_load(self):
        link = self.root / "linked-base.py"
        self.make_symlink_or_skip(link, self.base_path)
        self.assert_code(g.OverlayError, "BASE_GENERATOR_REPARSE",
                         lambda: g.load_base_generator(str(link)))

    def test_real_symlink_base_parent_rejected(self):
        link = self.root / "linked-dir"
        self.make_symlink_or_skip(link, self.output, directory=True)
        (self.output / "base.py").write_bytes(self.base_raw)
        self.assert_code(g.OverlayError, "BASE_GENERATOR_REPARSE",
                         lambda: g.load_base_generator(str(link / "base.py")))

    def test_real_symlink_helper_source_output_selected_owner(self):
        link = self.root / "linked-io.py"
        self.make_symlink_or_skip(link, self.io_path)
        self.assert_code(self.base.OverlayError, "IO_HELPER_REPARSE",
                         lambda: self.base.load_io(str(link)))
        source_link = self.root / "linked-study.java"
        os.symlink(self.study, source_link)
        # The checked exporter resolves first, so this is a canonicality rejection.
        self.assert_code(self.checked_io.ExportError, "OWNER_SOURCE_CANONICAL",
                         lambda: self.checked_io._read_regular(source_link, g.MAX_SOURCE_BYTES,
                                                              "OWNER_SOURCE"))
        output_link = self.root / "linked-output"
        os.symlink(self.output, output_link, target_is_directory=True)
        self.assert_code(self.checked_io.ExportError, "OUTPUT_DIRECTORY_CANONICAL",
                         lambda: self.checked_io._checked_directory(output_link, "OUTPUT_DIRECTORY"))

    def test_cli_parser_and_options_exact_shape(self):
        pairs = [value for pair in self.options.items() for value in pair]
        self.assertEqual(g.parse_args(pairs), self.options)
        self.assertEqual(g.parse_args(tuple(pairs)), self.options)
        for bad in (None, {}, pairs[:-1], pairs + ["extra", "value"],
                    ["--study", "a"] * 4, pairs[:1] + [""] + pairs[2:],
                    pairs[:1] + ["a\0b"] + pairs[2:], pairs[:1] + ["--bad"] + pairs[2:]):
            self.assert_code(g.OverlayError, "CLI_ARGUMENTS", lambda bad=bad: g.parse_args(bad))
        for bad in (None, {}, {**self.options, "--other": "value"},
                    {**self.options, "--study": None}):
            self.assert_code(g.OverlayError, "GENERATOR_OPTIONS", lambda bad=bad: g.generate(bad))

    def test_actual_selected_cli_positive_and_source_proof_readback(self):
        result = self.cli(self.options)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, b"")
        expected = {
            "schema": "tsj-generic-routing-reference-overlay-v1",
            "status": "GENERATED_NATIVE_ONLY_NOT_COMPILED",
            "derivedSha256": hashlib.sha256(self.derived).hexdigest(), "files": 2,
        }
        self.assertEqual(result.stdout,
                         (json.dumps(expected, separators=(",", ":")) + os.linesep).encode("ascii"))
        self.assertEqual((self.output / "CohortTerminalTelemetryStudy.java").read_bytes(), self.derived)
        proof = json.loads((self.output / "SOURCE_PROOF.json").read_bytes())
        self.assertEqual(proof["derivedSha256"], expected["derivedSha256"])
        self.assertEqual({p.name for p in self.output.iterdir()},
                         {"CohortTerminalTelemetryStudy.java", "SOURCE_PROOF.json"})
        self.assert_cli_failure(self.cli(self.options), "OUTPUT_NOT_EMPTY")

    def test_actual_selected_cli_bad_pin_paths_and_occupied_controls(self):
        for path, original, code in (
            (self.study, self.source, "STUDY_HASH"),
            (self.base_path, self.base_raw, "BASE_GENERATOR_HASH"),
            (self.io_path, self.io_raw, "IO_HELPER_HASH"),
        ):
            path.write_bytes(original + b"\n")
            self.assert_cli_failure(self.cli(self.options), code)
            self.assertEqual(list(self.output.iterdir()), [])
            path.write_bytes(original)
        alias = str(self.study.parent) + os.sep + "." + os.sep + self.study.name
        self.assert_cli_failure(self.cli({**self.options, "--study": alias}),
                                "OWNER_SOURCE_CANONICAL")
        self.assert_cli_failure(self.cli({**self.options, "--output-dir": "relative"}),
                                "OUTPUT_DIRECTORY_ABSOLUTE")
        keep = self.output / "keep.txt"
        keep.write_bytes(b"preserve")
        self.assert_cli_failure(self.cli(self.options), "OUTPUT_NOT_EMPTY")
        self.assertEqual(keep.read_bytes(), b"preserve")

    def test_actual_cli_isolation_and_sanitized_argument_failure(self):
        self.assert_cli_failure(self.cli(isolated=False), "PYTHON_ISOLATION")
        self.assert_cli_failure(self.cli({"--private": str(self.root)}), "CLI_ARGUMENTS")

    def test_internal_failure_message_is_sanitized(self):
        with mock.patch.object(g, "generate", side_effect=RuntimeError(str(self.root))), \
                mock.patch.object(g.sys, "stderr", new_callable=io.StringIO) as stderr, \
                mock.patch.object(g.sys, "stdout", new_callable=io.StringIO) as stdout:
            result = g.main([value for pair in self.options.items() for value in pair])
        self.assertEqual(result, 2)
        self.assertEqual(stdout.getvalue(), "")
        self.assertEqual(stderr.getvalue(), "GENERIC_REFERENCE_OVERLAY_FAIL INTERNAL_ERROR\n")


def parse_test_options(argv):
    keys = {"--study", "--base-generator", "--io-helper"}
    if type(argv) not in (list, tuple) or len(argv) != 6:
        raise ValueError("TEST_ARGUMENTS")
    result = {}
    for index in range(0, 6, 2):
        key, value = argv[index:index + 2]
        if (type(key) is not str or key not in keys or key in result or type(value) is not str
                or not value or value.startswith("--") or "\0" in value):
            raise ValueError("TEST_ARGUMENTS")
        # Inputs remain explicit, canonical absolute source paths.
        g.lexical_path(value, "TEST_SOURCE")
        result[key] = value
    if set(result) != keys:
        raise ValueError("TEST_ARGUMENTS")
    return result


if __name__ == "__main__":
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        raise SystemExit("TEST_PYTHON_ISOLATION_REQUIRED")
    OPTIONS = parse_test_options(sys.argv[1:])
    unittest.main(argv=[sys.argv[0]], verbosity=2)
