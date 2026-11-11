"""Selected Windows current CODE/two-JDK-tool facts, no normal input or run."""
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import types
import unittest


def load_base():
    cfg = json.loads(Path(os.environ["TSJ_STAGING_CONFIG"]).read_bytes())
    row = cfg["tests"]
    raw = Path(row["path"]).read_bytes()
    assert len(raw) == row["bytes"] and hashlib.sha256(raw).hexdigest() == row["sha256"]
    module = types.ModuleType("_native_byte_facts_fixture_driver")
    module.__file__ = row["path"]
    exec(compile(raw, row["path"], "exec", dont_inherit=True), module.__dict__)
    return module


BASE = load_base()
SOURCE_NAMES = ("study", "metadata", "codec", "inputSetup", "projections", "inputProducer")


class ByteFactsTests(unittest.TestCase):
    tearDownClass = classmethod(BASE.StageTests.tearDownClass.__func__)
    bind = BASE.StageTests.bind
    refresh = BASE.StageTests.refresh
    run_stage = BASE.StageTests.run_stage

    @classmethod
    def setUpClass(cls):
        BASE.StageTests.setUpClass.__func__(cls)
        for role, name in (("observer", "_native_facts_observer"),
                           ("invocations", "_native_facts_invocations"),
                           ("byteFacts", "_native_facts_component")):
            row = cls.cfg[role]
            raw, _, _ = cls.reader._read_file_once(row["path"], 4194304,
                row["bytes"], row["sha256"], "EXPLICIT_CURRENT_CODE")
            value = cls.loader._exec_authenticated_module(name, row["path"], raw)
            setattr(cls, role, value)

    def setUp(self):
        BASE.StageTests.setUp(self)
        self.run_stage()
        self.core_dir = self.scratch / "compile CODE # ; source"
        self.core_dir.mkdir()
        self.source_specs = {}
        for row in self.cfg["coreSources"]:
            raw, _, _ = self.reader._read_file_once(row["path"], 4194304,
                row["bytes"], row["sha256"], "CURRENT_NATIVE_CORE_CODE")
            path = self.core_dir / row["basename"]
            with path.open("xb") as output:
                output.write(raw)
            self.source_specs[row["nativeRole"]] = dict(path=str(path),
                expectedBytes=len(raw), maxBytes=4194304, sha256=row["sha256"])
        self.source_specs["inputProducer"] = dict(path=str(self.dest),
            expectedBytes=len(self.fixture), maxBytes=1048576,
            sha256=hashlib.sha256(self.fixture).hexdigest())
        self.tool_specs = {name: dict(spec) for name, spec in self.cfg["toolSpecs"].items()}

    def observe(self, **changes):
        args = dict(reader=self.reader, io_owner=self.caller, observer=self.observer,
            invocation_owner=self.invocations, source_specs=self.source_specs,
            tool_specs=self.tool_specs)
        return self.byteFacts.observe_native_code_tools(**dict(args, **changes))

    def reject(self, code, **changes):
        types_ = (self.byteFacts.NativeByteFactsError,
                  self.observer.CallerObservationError, self.invocations.InvocationError,
                  self.caller.CallerError)
        with self.assertRaises(types_) as caught:
            self.observe(**changes)
        self.assertEqual(code, getattr(caught.exception, "code", str(caught.exception)))

    def changed_sources(self, name, **changes):
        result = {key: dict(value) for key, value in self.source_specs.items()}
        result[name].update(changes)
        return result

    def test_00_actual_current_six_sources_two_tools(self):
        result = self.observe()
        self.assertEqual({"sourceFacts", "toolFacts"}, set(result))
        self.assertEqual(SOURCE_NAMES, tuple(result["sourceFacts"]))
        self.assertEqual(("nativeJava", "nativeJavac"), tuple(result["toolFacts"]))
        for category, specs in (("sourceFacts", self.source_specs), ("toolFacts", self.tool_specs)):
            self.assertEqual(set(specs), set(result[category]))
            for role, spec in specs.items():
                self.assertEqual(dict(path=spec["path"], bytes=spec["expectedBytes"],
                    sha256=spec["sha256"]), result[category][role])
        self.assertNotIn("status", result)
        self.assertNotIn("harnessModuleLinkEvidence", result)

    def test_01_wrong_actual_java_hash_rejected(self):
        specs = {name: dict(spec) for name, spec in self.tool_specs.items()}
        specs["nativeJava"]["sha256"] = "0" * 64
        self.reject("TOOL_READ_SHA256", tool_specs=specs)

    def test_wrong_current_core_hash_rejected(self):
        self.reject("SOURCE_READ_SHA256", source_specs=self.changed_sources("study", sha256="0" * 64))

    def test_prior_five_tool_roles_rejected(self):
        specs = {"nativeJava": dict(self.tool_specs["nativeJava"])}
        for name in ("qualificationPython", "rootCallPowerShell", "rootCallHarness", "processModule"):
            specs[name] = dict(specs["nativeJava"])
        self.assertEqual(5, len(specs))
        self.reject("TOOL_MAP_INVALID", tool_specs=specs)

    def test_missing_native_tool_rejected(self):
        specs = dict(self.tool_specs)
        del specs["nativeJavac"]
        self.reject("TOOL_MAP_INVALID", tool_specs=specs)

    def test_missing_or_extra_source_rejected(self):
        missing = dict(self.source_specs)
        del missing["codec"]
        self.reject("SOURCE_MAP_INVALID", source_specs=missing)
        self.reject("SOURCE_MAP_INVALID", source_specs=dict(self.source_specs, extra=self.source_specs["study"]))

    def test_wrong_source_basename_rejected(self):
        self.reject("INVALID_SOURCE_BASENAME", source_specs=self.changed_sources("study",
            path=str(self.core_dir / "WrongStudy.java")))

    def test_wrong_tool_basename_rejected(self):
        specs = {name: dict(spec) for name, spec in self.tool_specs.items()}
        specs["nativeJavac"]["path"] = str(self.core_dir / "wrong-compiler.exe")
        self.reject("INVALID_TOOL", tool_specs=specs)

    def test_all_limits_preflight_before_bad_tool_read(self):
        tools = {name: dict(spec) for name, spec in self.tool_specs.items()}
        tools["nativeJava"]["sha256"] = "0" * 64
        self.reject("NATIVE_SOURCE_SPEC_LIMIT", tool_specs=tools,
            source_specs=self.changed_sources("inputProducer", maxBytes=1048577))
        self.reject("NATIVE_SOURCE_SPEC_EXPECTED_BYTES", tool_specs=tools,
            source_specs=self.changed_sources("inputProducer", expectedBytes=1048577, maxBytes=1048577))
        self.reject("NATIVE_SOURCE_SPEC_LIMIT", tool_specs=tools,
            source_specs=self.changed_sources("study", maxBytes=4194305))

    def test_exact_producer_one_mib(self):
        raw = self.fixture + b" " * (1048576-len(self.fixture))
        self.dest.write_bytes(raw)
        specs = self.changed_sources("inputProducer", expectedBytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
        result = self.observe(source_specs=specs)
        self.assertEqual(1048576, result["sourceFacts"]["inputProducer"]["bytes"])
        self.assertEqual(hashlib.sha256(raw).hexdigest(), result["sourceFacts"]["inputProducer"]["sha256"])

    def test_spec_shape_bool_and_hash_rejected(self):
        specs = self.changed_sources("study", extra="unowned")
        self.reject("SOURCE_ROW_INVALID", source_specs=specs)
        self.reject("NATIVE_SOURCE_SPEC_EXPECTED_BYTES", source_specs=self.changed_sources("study", expectedBytes=True))
        self.reject("NATIVE_SOURCE_SPEC_SHA", source_specs=self.changed_sources("study", sha256="A" * 64))

    def test_tool_limit_preflight(self):
        specs = {name: dict(spec) for name, spec in self.tool_specs.items()}
        specs["nativeJavac"]["maxBytes"] = 8388609
        self.reject("NATIVE_TOOL_SPEC_LIMIT", tool_specs=specs)

    def test_spec_rows_are_snapshotted_before_reads(self):
        expected = self.source_specs["study"]["sha256"]
        def profile(frame, event, arg):
            if (event == "return" and frame.f_code.co_name == "_read_fact_map"
                    and frame.f_globals is self.observer.__dict__
                    and frame.f_locals.get("category") == "TOOL"):
                sys.setprofile(None)
                self.source_specs["study"]["sha256"] = "0" * 64
        sys.setprofile(profile)
        try:
            result = self.observe()
        finally:
            sys.setprofile(None)
        self.assertEqual(expected, result["sourceFacts"]["study"]["sha256"])
        self.assertEqual("0" * 64, self.source_specs["study"]["sha256"])

    def test_existing_directory_is_not_a_source(self):
        path = Path(self.source_specs["study"]["path"])
        path.rename(self.scratch / "preserved-study.java")
        path.mkdir()
        self.reject("SOURCE_READ_REGULAR")

    def test_same_content_source_replacement_between_passes_rejected(self):
        path = Path(self.source_specs["study"]["path"])
        def profile(frame, event, arg):
            if (event == "return" and frame.f_code.co_name == "_read_fact_map"
                    and frame.f_globals is self.observer.__dict__
                    and frame.f_locals.get("category") == "SOURCE"):
                sys.setprofile(None)
                raw = path.read_bytes()
                path.rename(self.scratch / "preserved-study.java")
                path.write_bytes(raw)
        sys.setprofile(profile)
        try:
            self.reject("SOURCE_CHANGED")
        finally:
            sys.setprofile(None)

    def test_real_source_junction_rejected(self):
        import _winapi
        alias = self.scratch / "owned-source-junction"
        _winapi.CreateJunction(str(self.core_dir), str(alias))
        try:
            snap = os.lstat(alias)
            self.assertEqual(0xA0000003, snap.st_reparse_tag)
            self.assertEqual(alias.resolve(), self.core_dir.resolve())
            self.reject("SOURCE_READ_REPARSE", source_specs=self.changed_sources("study",
                path=str(alias / "CohortTerminalTelemetryStudy.java")))
        finally:
            snap = os.lstat(alias)
            assert snap.st_reparse_tag == 0xA0000003 and alias.resolve() == self.core_dir.resolve()
            start = time.monotonic_ns()
            os.rmdir(alias)
            self.cleanups.append(dict(kind="JUNCTION_NODE_ONLY", tag="A0000003",
                exactOwnedTargetRevalidated=True, cleanupNanos=time.monotonic_ns()-start,
                nodeAbsent=not alias.exists()))
        self.assertFalse(alias.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
