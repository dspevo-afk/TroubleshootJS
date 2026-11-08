"""Focused checked-source generator tests; no Java/native execution.

Invoke with Python -I -B and the generator's exact seven source/path pairs.
Tests create their own exclusive output directories below the system temp root.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest

GENERATOR_PATH = Path(__file__).resolve().with_name("generate_generic_r7_export_overlay.py")
g = types.ModuleType("_generic_r7_export_overlay_under_test")
g.__file__ = str(GENERATOR_PATH)
exec(compile(GENERATOR_PATH.read_bytes(), str(GENERATOR_PATH), "exec"), g.__dict__)

OPTIONS = None
EXPECTED_STUDY_CHANGES = (
    "EXACT_SIX_ARGUMENT_MODE", "GENERIC_ARGUMENT_PARSER", "R5_FINALIZER_BINDING",
    "GENERIC_EMPTY_TO_RESULT_OUTPUT", "R5_SNAPSHOT_NO_ORIGINAL_STATE",
    "GENERIC_WORK_SHAPE_AND_EQUATIONS", "GENERIC_RESULT_OUTPUT_CLASS",
    "GENERIC_FOURTEEN_KEY_MARKER", "GENERIC_R7_METER_LEDGER",
    "R5_OUTPUT_ARGUMENT_TYPE_1", "R5_OUTPUT_ARGUMENT_TYPE_2",
    "R5_OUTPUT_DIRECTORY_ROLE", "R5_FINALIZER_WORK_BINDING",
    "R5_CANARY_COUNT_GEOMETRY_PATH", "R5_CANARY_WRITE_GEOMETRY_PATH",
    "R5_CANARY_READBACK_GEOMETRY_PATH", "R5_EXPORT_WORK_FIELDS",
)
EXPECTED_DRIVER_CHANGES = (
    "GENERIC_RESULT_DRIVER", "IMMEDIATE_RUN_METER_BOUNDARIES",
    "R7_MODE_WIRE_IDENTITY",
)


def path_args(options):
    return [part for flag in g.CLI_FLAGS for part in (flag, options[flag])]


class DerivationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if OPTIONS is None:
            raise RuntimeError("TEST_SOURCE_OPTIONS_REQUIRED")
        cls.study = Path(OPTIONS["--study"]).read_bytes()
        cls.driver = Path(OPTIONS["--driver"]).read_bytes()
        cls.support = Path(OPTIONS["--native-support"]).read_bytes()
        cls.wire = Path(OPTIONS["--wire"]).read_bytes()
        cls.study_out, cls.driver_out, cls.proof = g.derive(
            cls.study, cls.driver, cls.support, cls.wire)

    def assert_code(self, code, call):
        with self.assertRaises(g.OverlayError) as caught:
            call()
        self.assertIs(type(caught.exception), g.OverlayError)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(str(caught.exception), code)

    def test_pinned_derivation_is_byte_stable_and_closed(self):
        self.assertEqual(
            g.derive(self.study, self.driver, self.support, self.wire),
            (self.study_out, self.driver_out, self.proof))
        self.assertEqual(tuple(self.proof["studyChanges"]), EXPECTED_STUDY_CHANGES)
        self.assertEqual(tuple(self.proof["driverChanges"]), EXPECTED_DRIVER_CHANGES)
        self.assertEqual(tuple(self.proof["outputs"]), g.OUTPUT_NAMES)
        self.assertEqual(self.proof["markerMaximumBytes"], 666)
        self.assertEqual(self.proof["phaseBoundaryReadUnitsPerPoint"], 76)
        self.assertEqual(self.proof["phaseBoundaryPrepaymentUnits"], 304)
        self.assertEqual(self.proof["phasePointPrepaidCopies"], 109)
        self.assertEqual(self.proof["freshRunTotalPrepaymentPerRun"], 51)
        self.assertEqual(self.proof["workPhaseNumericSlotWrites"], 23)
        self.assertEqual(self.proof["finalizerElapsedSlotWrites"], 1)
        self.assertEqual(self.proof["nativeExecution"], "NOT_RUN")
        self.assertNotIn(b"\r", self.study_out)
        self.assertNotIn(b"\r", self.driver_out)
        self.assertLessEqual(len(self.study_out), g.MAX_SOURCE_BYTES)
        self.assertLessEqual(len(self.driver_out), g.MAX_SOURCE_BYTES)
        self.assertNotIn(b"R7_WORK_SLOTS=34", self.study_out)
        self.assertNotIn(b"originalCaptureOwnerUnits", self.study_out)
        self.assertEqual(self.study_out.count(b"writeCanonicalJsonCanaryValues(result,"), 3)

    def test_study_driver_wire_and_support_pins_are_independent(self):
        self.assert_code("STUDY_HASH", lambda: g.derive(
            self.study + b"// changed pinned unit source\n", self.driver, self.support, self.wire))
        self.assert_code("DRIVER_HASH", lambda: g.derive(
            self.study, self.driver + b"// changed pinned unit source\n", self.support, self.wire))
        self.assert_code("WIRE_HASH", lambda: g.derive(
            self.study, self.driver, self.support, self.wire + b"\n"))
        missing = self.support.replace(b"/*<R7_CORE>*/", b"/*<R7_REMOVED>*/", 1)
        self.assert_code("SUPPORT_SECTION_R7_CORE",
                         lambda: g.derive(self.study, self.driver, missing, self.wire))

    def test_source_owner_bytes_outside_owned_splices_survive(self):
        self.assertTrue(self.study_out.startswith(self.study[:self.study.index(
            b"    public static void main(String[] args) throws Exception {\n")]))
        self.assertIn(b"private static final int R7_PHASE_READ_UNITS=18*4+1+3;",
                      self.study_out)
        self.assertIn(b"meter.charge(R7_CLOSED_SNAPSHOT_READS);", self.study_out)
        self.assertIn(b"meter.copy(24);", self.study_out)
        self.assertIn(b"meter.charge(22);", self.study_out)
        self.assertIn(b"afterBlocked.maxUnits<=pre.maxUnits", self.study_out)
        self.assertIn(b"!open && !ownerStarted && !ownerActive && !ownerAdopted", self.study_out)
        self.assertIn(b"inFlight==0 && prior==0", self.study_out)
        self.assertNotIn(b"ownerStarted=true", self.study_out)
        self.assertNotIn(b"ownerAdopted=true", self.study_out)
        self.assertIn(b"finalizer.verifyBound(meter,pre,result,work);", self.study_out)
        self.assertIn(b"body==result && w==work", self.study_out)
        self.assertIn(b"work.get(\"setup\")==setup", self.study_out)
        self.assertIn(b"work.get(\"preExport\")==preExport", self.study_out)
        self.assertIn(b"R5_MARKER_BOUND=666", self.study_out)
        self.assertNotIn(b"R5_MARKER_BOUND=722", self.study_out)
        self.assertEqual(self.study_out.count(b"r5MarkerString(sink,"), 6)
        self.assertEqual(self.study_out.count(b"r5MarkerNumber(sink,"), 7)
        self.assertIn(b"references.{OPEN,BLOCKED}.run.layout.components[].geometryCanonical",
                      self.proof["geometryPath"].encode("ascii"))
        self.assertIn(b"ledger.recordRun(m,fresh,blocked,ownerValue,wrapperValue,groupsValue,",
                      self.driver_out)
        self.assertIn(b"ledger.afterOpen(m);", self.driver_out)
        self.assertIn(b"ledger.afterBlocked(m);", self.driver_out)
        reference = self.driver_out[
            self.driver_out.index(b"    private static Map<String,Object> reference("):
            self.driver_out.index(b"    private static String geometry(")]
        post = reference.index(b"ledger.afterOpen(m);")
        first_metadata_charge = reference.index(b"m.charge(3);", post)
        for field in (
            b'"sessionConstructorEntries"', b'"json"', b'"cleanupNanos"',
            b'"referencesDropped"', b'"ownerUnits"', b'"wrapperUnits"', b'"groups"',
            b'"copiedEntries"', b'"copyChunks"', b'"maxGroupNanos"',
            b'"maximumUnitsPerGroup"',
        ):
            self.assertGreater(reference.index(field, post), post)
            self.assertLess(reference.index(field, post), first_metadata_charge)
        self.assertGreater(reference.index(b"ledger.recordRun(", post), first_metadata_charge)


class ArgumentTests(unittest.TestCase):
    def test_exact_unique_flag_set_and_path_lexical_policy(self):
        options = {flag: "C:\\source\\item" for flag in g.CLI_FLAGS}
        self.assertEqual(g.parse_args(path_args(options)), options)
        duplicate = path_args(options)
        duplicate[2] = "--study"
        self.assert_overlay_error("CLI_ARGUMENTS", lambda: g.parse_args(duplicate))
        wrong_count = path_args(options)[:-2]
        self.assert_overlay_error("CLI_ARGUMENTS", lambda: g.parse_args(wrong_count))

        for alias in (
            "C:/source/item", "\\\\?\\C:\\source\\item",
            "\\\\.\\C:\\source\\item",
        ):
            self.assert_overlay_error(
                "CLI_PATH_WINDOWS_ALIAS",
                lambda alias=alias: g._raw_canonical_path(alias, "CLI_PATH"))
        for alias in (
            "C:\\source\\.\\item", "C:\\source\\..\\item",
            "C:\\source\\item\\", "C:\\source\\item.", "C:\\source\\item ",
            "C:\\source\\\\item", "C:\\",
            "\\\\server",
        ):
            self.assert_overlay_error(
                "CLI_PATH_ALIAS",
                lambda alias=alias: g._raw_canonical_path(alias, "CLI_PATH"))

    def assert_overlay_error(self, code, call):
        with self.assertRaises(g.OverlayError) as caught:
            call()
        self.assertEqual(caught.exception.code, code)


class CheckedGenerationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if OPTIONS is None:
            raise RuntimeError("TEST_SOURCE_OPTIONS_REQUIRED")
        cls.inputs = {flag: Path(OPTIONS[flag]).read_bytes()
                      for flag in g.CLI_FLAGS[:-1]}

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tsj-generic-r7-overlay-tests-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.options = {}
        for flag, raw in self.inputs.items():
            name = flag[2:] + (".java" if flag in ("--study", "--driver") else
                               ".json" if flag == "--wire" else ".inc" if
                               flag == "--native-support" else ".py")
            path = self.root / name
            path.write_bytes(raw)
            self.options[flag] = str(path.resolve())
        self.output = self.root / "output"
        self.output.mkdir()
        self.options["--output-dir"] = str(self.output.resolve())

    def test_exclusive_generation_census_readback_and_source_rechecks(self):
        proof = g.generate(self.options)
        expected = set(g.OUTPUT_NAMES)
        self.assertEqual({item.name for item in self.output.iterdir()}, expected)
        study = (self.output / g.OUTPUT_NAMES[0]).read_bytes()
        driver = (self.output / g.OUTPUT_NAMES[1]).read_bytes()
        proof_raw = (self.output / g.OUTPUT_NAMES[2]).read_bytes()
        self.assertEqual(hashlib.sha256(study).hexdigest(), proof["derivedStudySha256"])
        self.assertEqual(hashlib.sha256(driver).hexdigest(), proof["derivedDriverSha256"])
        self.assertEqual(json.loads(proof_raw.decode("ascii")), proof)
        self.assertEqual({item.name for item in self.output.iterdir()}, expected)

    def test_nonempty_output_rejects_before_any_output_mutation(self):
        sentinel = self.output / "preserve-me"
        sentinel.write_bytes(b"owned")
        with self.assertRaises(g.OverlayError) as caught:
            g.generate(self.options)
        self.assertEqual(caught.exception.code, "OUTPUT_NOT_EMPTY")
        self.assertEqual(sentinel.read_bytes(), b"owned")
        self.assertEqual([item.name for item in self.output.iterdir()], ["preserve-me"])

    def test_tampered_support_and_io_helper_fail_closed(self):
        support = Path(self.options["--native-support"])
        support.write_bytes(support.read_bytes().replace(b"R7_CORE", b"R7_XOR", 1))
        with self.assertRaises(g.OverlayError):
            g.generate(self.options)
        self.assertEqual(list(self.output.iterdir()), [])

        # Fresh fixture, same fixed-size path, but the helper's immutable hash fails.
        self.setUp()
        helper = Path(self.options["--io-helper"])
        helper.write_bytes(helper.read_bytes() + b"#tamper\n")
        with self.assertRaises(g.OverlayError) as caught:
            g.generate(self.options)
        self.assertEqual(caught.exception.code, "IO_HELPER_HASH")
        self.assertEqual(list(self.output.iterdir()), [])


def main():
    global OPTIONS
    OPTIONS = g.parse_args(sys.argv[1:])
    sys.argv = [sys.argv[0]]
    unittest.main()


if __name__ == "__main__":
    main()
