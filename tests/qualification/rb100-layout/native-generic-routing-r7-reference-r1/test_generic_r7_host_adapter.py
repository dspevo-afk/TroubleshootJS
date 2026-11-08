"""Host admission tests for the current generic R7 native reference.

The default suite uses synthetic file metadata and never invokes a producer.
Root may additionally provide the actual root-frozen context and external
returned-capture digest after its native gate has completed.
"""
import argparse
import ctypes
import hashlib
import importlib.util
import io
import json
import os
import stat
import struct
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path


READER = None
ACTUAL = None


class _LocalCheckedIO:
    def _read_regular(self, path, limit, code):
        raw = Path(path).read_bytes()
        if len(raw) > limit:
            raise READER.ReaderError(code)
        return raw

    def _checked_directory(self, path, code):
        path = Path(path)
        resolved = path.resolve(strict=True)
        info = path.lstat()
        if (resolved != path or not stat.S_ISDIR(info.st_mode)
                or stat.S_ISLNK(info.st_mode)
                or getattr(info, "st_file_attributes", 0) & 0x400):
            raise READER.ReaderError(code)
        return resolved

    def _directory_names(self, path, code):
        return sorted(entry.name for entry in Path(path).iterdir())

    def _write_exclusive(self, path, raw, limit, code):
        if len(raw) > limit:
            raise READER.ReaderError(code)
        with Path(path).open("xb") as stream:
            written = stream.write(raw)
        if written != len(raw):
            raise READER.ReaderError(code)


def _dump(value):
    return (json.dumps(value, ensure_ascii=True, allow_nan=False,
                       separators=(",", ":")) + "\n").encode("ascii")


def _context(root):
    files = (
        "producerManifest", "sourceList", "classInventory", "inheritedClassInventory",
        "legacyInventory", "sourceProof", "sourceGenerator", "gwtUser", "gwtDev",
        "java", "javac", "powershell", "rootCall", "processModule", "fixtureSpec",
        "markerBound", "expectedNativeCall", "returnedCapture",
    )
    paths = {name: str(root / (name + ".data")) for name in files}
    paths.update({
        "emptySourcepath": str(root / "empty-sourcepath"),
        "classesRoot": str(root / "fresh-classes"),
        "inheritedClassesRoot": str(root / "inherited-classes"),
        "compileCall": str(root / "compile-producer-r1"),
        "nativeCall": str(root / "native-reference-r1"),
        "nativeOutput": str(root / "native-output-r1"),
    })
    return {
        "schema": READER.ADMISSION_CONTEXT_SCHEMA,
        "runId": "12345678-1234-4234-8234-123456789abc",
        "sourceManifestSha256": "a" * 64,
        "wireContractSha256": READER.ADMISSION_WIRE_SHA256,
        "fixtureOracleSha256": READER.ADMISSION_FIXTURE_SHA256,
        "producerManifest": paths["producerManifest"],
        "sourcePaths": {
            "owner": str(root / "owner.java"),
            "study": str(root / "study.java"),
            "driver": str(root / "driver.java"),
        },
        "sourceList": paths["sourceList"],
        "emptySourcepath": paths["emptySourcepath"],
        "classesRoot": paths["classesRoot"],
        "classInventory": paths["classInventory"],
        "classInventorySha256": "b" * 64,
        "inheritedClassesRoot": paths["inheritedClassesRoot"],
        "inheritedClassInventory": paths["inheritedClassInventory"],
        "inheritedClassInventorySha256": "c" * 64,
        "legacyInventory": paths["legacyInventory"],
        "inheritedSourcePaths": {
            "runOwner": str(root / "run-owner.java"),
            "fixtureSource": str(root / "fixture-source.java"),
        },
        "sourceProof": paths["sourceProof"],
        "sourceGenerator": paths["sourceGenerator"],
        "gwtUser": paths["gwtUser"],
        "gwtDev": paths["gwtDev"],
        "java": paths["java"],
        "javac": paths["javac"],
        "powershell": paths["powershell"],
        "rootCall": paths["rootCall"],
        "processModule": paths["processModule"],
        "compileCall": paths["compileCall"],
        "nativeCall": paths["nativeCall"],
        "nativeOutput": paths["nativeOutput"],
        "fixtureSpec": paths["fixtureSpec"],
        "markerBound": paths["markerBound"],
        "expectedNativeCall": paths["expectedNativeCall"],
        "returnedCapture": paths["returnedCapture"],
    }


def _valid_manifest():
    classes = [
        {"path": "PcbLayerRoutingPrototype$%02d.class" % index,
         "bytes": 1, "sha256": "1" * 64}
        for index in range(46)
    ]
    compile_receipts = {
        name: hashlib.sha256(("compile:" + name).encode("ascii")).hexdigest()
        for name in READER.ADMISSION_RECEIPT_NAMES
    }
    jars = [
        {"role": "GWT_DEPENDENCY", "bytes": READER.ADMISSION_TOOL_LIMITS[role],
         "sha256": READER.ADMISSION_TOOL_SHA256[role]}
        for role in ("gwtUser", "gwtDev")
    ]
    return {
        "schema": "tsj-generic-r7-native-producer-manifest-v1",
        "scope": "GENERIC_NATURAL_R7_REFERENCE_ONLY",
        "parentCommit": READER.ADMISSION_PARENT_COMMIT,
        "wireContractSha256": READER.ADMISSION_WIRE_SHA256,
        "fixtureOracleSha256": READER.ADMISSION_FIXTURE_SHA256,
        "sources": [
            {"role": "PcbLayerRoutingPrototype.java", "bytes": 1,
             "sha256": READER.ADMISSION_OWNER_SHA256},
            {"role": "CohortTerminalTelemetryStudy.java", "bytes": 1,
             "sha256": "2" * 64},
            {"role": "GenericRoutingReferenceCanary.java", "bytes": 1,
             "sha256": "3" * 64},
        ],
        "inheritedSources": [
            {"role": "SHARED_RUN_OWNER", "bytes": 1,
             "sha256": READER.ADMISSION_RUN_OWNER_SHA256},
            {"role": "GENERIC_FIXTURE", "bytes": 1,
             "sha256": READER.ADMISSION_FIXTURE_SOURCE_SHA256},
        ],
        "sourceProofSha256": "4" * 64,
        "generatorSha256": "5" * 64,
        "classes": classes,
        "classCount": len(classes),
        "classBytes": len(classes),
        "ownerClassCount": len(classes),
        "ownerByteEqualQualifiedP": True,
        "namespace": {
            "status": "PASS_EXPLICIT_CONSTANT_CLASS_CLOSURE",
            "freshClassCount": len(classes),
            "explicitReferencesChecked": 0,
            "allFreshNamespaceReferencesResolveFresh": True,
            "classMajor": 51,
            "limitation": "EXPLICIT_CONSTANT_CLASS_ONLY_NO_REFLECTION_CLOSURE_CLAIM",
        },
        "inheritedClosure": {
            "classBytes": READER.ADMISSION_INHERITED_CLASS_BYTES,
            "classFilesRehashed": READER.ADMISSION_INHERITED_CLASS_COUNT,
            "inventorySha256": READER.ADMISSION_LEGACY_INVENTORY_SHA256,
            "jars": jars,
            "toolsRehashed": True,
        },
        "compile": {
            "phase": "compile-producer-r1",
            "status": "PASS",
            "childExitCode": 0,
            "processElapsedMs": 1.0,
            "wrapperTotalElapsedMs": 2.0,
            "terminationProven": True,
            "ownedRootAndLogsAbsent": True,
            "isolatedCleanupDurationMs": None,
            "receipts": compile_receipts,
        },
        "classpathOrder": "FRESH_THEN_PINNED_D_GWT",
        "newAuthoredInvocations": 0,
    }


def _capture(context, result_raw=b"synthetic native result\n"):
    call = {
        "phase": Path(context["nativeCall"]).name,
        "status": "PASS",
        "childExitCode": 0,
        "processElapsedMs": 2.0,
        "wrapperTotalElapsedMs": 3.0,
        "terminationProven": True,
        "ownedRootAndLogsAbsent": True,
        "isolatedCleanupDurationMs": None,
        "receipts": {
            name: hashlib.sha256(("native:" + name).encode("ascii")).hexdigest()
            for name in READER.ADMISSION_RECEIPT_NAMES
        },
    }
    return {
        "schema": READER.ADMISSION_CAPTURE_SCHEMA,
        "expectedBeforeCallSha256": "a" * 64,
        "call": call,
        "nativeDirectory": context["nativeOutput"],
        "outputNames": ["result.json"],
        "outputFiles": [{
            "role": "result.json", "bytes": len(result_raw),
            "sha256": hashlib.sha256(result_raw).hexdigest(),
        }],
        "status": READER.ADMISSION_CAPTURE_STATUS,
        "newAuthoredInvocations": 0,
    }


def _call_directory(root, names=None, result=None, wrapper=None):
    call = root / ("native-reference-r1-" + str(len(list(root.iterdir()))))
    call.mkdir()
    if result is None:
        result = _dump({"status": "RETURNED", "exitCode": 0})
    if wrapper is None:
        wrapper = result
    raw = {
        "owner.json": b"owner",
        "parent.json": b"parent",
        "request.json": b"request",
        "process.json": b"process",
        "result.json": result,
        "stdout.txt": b"",
        "stderr.txt": b"",
        "wrapper.stdout": wrapper,
        "wrapper.stderr": b"",
    }
    for name, data in raw.items():
        (call / name).write_bytes(data)
    if names:
        for name in names:
            (call / name).write_bytes(b"extra")
    return call, raw


def _create_junction(link, target):
    if os.name != "nt":
        raise OSError("junction tests require Windows")
    link = str(link)
    target = str(target)
    os.mkdir(link)
    substitute_raw = ("\\??\\" + target).encode("utf-16-le")
    printed_raw = target.encode("utf-16-le")
    path_buffer = substitute_raw + b"\x00\x00" + printed_raw + b"\x00\x00"
    payload = struct.pack(
        "<IHHHHHH", 0xA0000003, 8 + len(path_buffer), 0,
        0, len(substitute_raw), len(substitute_raw) + 2, len(printed_raw),
    ) + path_buffer
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    create_file = kernel.CreateFileW
    create_file.argtypes = (ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32,
                            ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32,
                            ctypes.c_void_p)
    create_file.restype = ctypes.c_void_p
    device_control = kernel.DeviceIoControl
    device_control.argtypes = (ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p,
                               ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32,
                               ctypes.POINTER(ctypes.c_ulong), ctypes.c_void_p)
    device_control.restype = ctypes.c_int
    close_handle = kernel.CloseHandle
    close_handle.argtypes = (ctypes.c_void_p,)
    close_handle.restype = ctypes.c_int
    handle = create_file(link, 0x40000000, 0, None, 3,
                         0x02000000 | 0x00200000, None)
    invalid = ctypes.c_void_p(-1).value
    if handle == invalid:
        os.rmdir(link)
        raise OSError(ctypes.get_last_error(), "CreateFileW failed")
    returned = ctypes.c_ulong()
    buffer = ctypes.create_string_buffer(payload)
    failure = None
    try:
        if not device_control(handle, 0x000900A4, buffer, len(payload), None, 0,
                              ctypes.byref(returned), None):
            failure = OSError(ctypes.get_last_error(), "FSCTL_SET_REPARSE_POINT failed")
    finally:
        if not close_handle(handle) and failure is None:
            failure = OSError(ctypes.get_last_error(), "CloseHandle failed")
    if failure is not None:
        os.rmdir(link)
        raise failure


def _junction_identity(path):
    info = os.lstat(path)
    attributes = getattr(info, "st_file_attributes", 0)
    tag = getattr(info, "st_reparse_tag", 0)
    if not (attributes & 0x400) or tag != 0xA0000003:
        raise AssertionError("expected owned directory junction")
    return (str(path), info.st_dev, info.st_ino, info.st_mode, attributes, tag,
            getattr(info, "st_birthtime_ns", info.st_ctime_ns), os.readlink(path))


class GenericR7HostAdapterTests(unittest.TestCase):
    def test_cli_requires_exact_five_literal_pairs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            values = {
                "--context": str(root / "context.json"),
                "--capture-proof-sha": "a" * 64,
                "--base-generator": str(root / "base.py"),
                "--io-helper": str(root / "io.py"),
                "--output-dir": str(root / "output"),
            }
            argv = [part for pair in values.items() for part in pair]
            self.assertEqual(READER.parse_args(argv), values)
            for bad in (argv[:-2], argv + ["--context", values["--context"]]):
                with self.assertRaises(READER.ReaderError):
                    READER.parse_args(bad)
            alias = list(argv)
            alias[1] = str(root) + "\\subdir\\..\\context.json"
            with self.assertRaises(READER.ReaderError):
                READER.parse_args(alias)

    def test_context_schema_wire_and_path_aliases(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            context = _context(root)
            self.assertIs(READER.admission_context_paths(context), context)
            parsed = READER.admission_parse_context(_dump(context))
            self.assertEqual(parsed, context)
            extra = dict(context)
            extra["unexpected"] = True
            with self.assertRaises((READER.ReaderError, READER.RH_ReaderError)):
                READER.admission_context_paths(extra)
            wrong_wire = dict(context)
            wrong_wire["wireContractSha256"] = "0" * 64
            with self.assertRaises(READER.ReaderError):
                READER.admission_context_paths(wrong_wire)
            aliased = dict(context)
            aliased["sourceList"] = aliased["sourcePaths"]["owner"]
            with self.assertRaises(READER.ReaderError):
                READER.admission_context_paths(aliased)

    def test_independent_compile_and_six_argument_native_argv(self):
        context = {
            "classesRoot": r"C:\task\fresh-classes",
            "inheritedClassesRoot": r"C:\task\inherited-classes",
            "gwtUser": r"C:\sdk\gwt-user-2.7.0.jar",
            "gwtDev": r"C:\sdk\gwt-dev-2.7.0.jar",
            "emptySourcepath": r"C:\task\empty-sourcepath",
            "sourceList": r"C:\task\compiler-sources.txt",
            "javac": r"C:\jdk8\bin\javac.exe",
            "java": r"C:\jdk8\bin\java.exe",
            "runId": "12345678-1234-4234-8234-123456789abc",
            "sourceManifestSha256": "a" * 64,
            "wireContractSha256": READER.ADMISSION_WIRE_SHA256,
            "fixtureOracleSha256": READER.ADMISSION_FIXTURE_SHA256,
            "nativeOutput": r"C:\task\native-output-r1",
        }
        actual = READER.admission_expected_invocations(context)
        classpath = (r"C:\task\fresh-classes;C:\task\inherited-classes;"
                     r"C:\sdk\gwt-user-2.7.0.jar;C:\sdk\gwt-dev-2.7.0.jar")
        compile_args = [
            "-source", "7", "-target", "7", "-encoding", "UTF-8",
            "-classpath", classpath, "-sourcepath", r"C:\task\empty-sourcepath",
            "-d", r"C:\task\fresh-classes", "@" + r"C:\task\compiler-sources.txt",
        ]
        native_args = [
            "-Xmx1024m", "-cp", classpath,
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy",
            "GENERIC_ROUTING_R7_REFERENCE_V1",
            "12345678-1234-4234-8234-123456789abc", "a" * 64,
            READER.ADMISSION_WIRE_SHA256, READER.ADMISSION_FIXTURE_SHA256,
            r"C:\task\native-output-r1",
        ]
        self.assertEqual(actual["compile"], (
            r"C:\jdk8\bin\javac.exe", compile_args, 60_000,
            "warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
            "1 warning\r\n"))
        self.assertEqual(actual["native"], (
            r"C:\jdk8\bin\java.exe", native_args, 480_000, ""))

    def test_source_list_uses_frozen_role_order_and_literal_paths(self):
        context = {
            "sourcePaths": {
                "owner": r"C:\task\owner.java",
                "study": r"C:\task\study.java",
                "driver": r"C:\task\driver.java",
            },
        }
        self.assertEqual(tuple(row[0] for row in READER.ADMISSION_SOURCE_ROWS), (
            "PcbLayerRoutingPrototype.java", "CohortTerminalTelemetryStudy.java",
            "GenericRoutingReferenceCanary.java"))
        self.assertEqual(tuple(row[1] for row in READER.ADMISSION_SOURCE_ROWS),
                         ("owner", "study", "driver"))
        self.assertEqual(READER.admission_source_list_bytes(context),
                         b'"C:/task/owner.java"\n"C:/task/study.java"\n'
                         b'"C:/task/driver.java"\n')

    def test_expected_native_call_binds_context_tools_sources_and_classes(self):
        with tempfile.TemporaryDirectory() as directory:
            context = _context(Path(directory))
            manifest = _valid_manifest()
            tools = [
                {"role": Path(context[role]).name, "bytes": 1,
                 "sha256": READER.ADMISSION_TOOL_SHA256[role]}
                for role in READER.ADMISSION_TOOL_ORDER
            ]
            native_file, native_args, _limit, _stderr = (
                READER.admission_expected_invocations(context)["native"])
            expected = {
                "schema": READER.ADMISSION_NATIVE_EXPECTED_SCHEMA,
                "context": {
                    "runId": context["runId"],
                    "sourceManifestSha256": context["sourceManifestSha256"],
                    "wireContractSha256": context["wireContractSha256"],
                    "fixtureOracleSha256": context["fixtureOracleSha256"],
                    "nativeOutput": context["nativeOutput"],
                },
                "executable": native_file,
                "arguments": native_args,
                "sourceManifestSha256": context["sourceManifestSha256"],
                "tools": tools,
                "sources": manifest["sources"],
                "classes": manifest["classes"],
                "status": READER.ADMISSION_NATIVE_EXPECTED_STATUS,
            }
            raw = _dump(expected)
            admitted = READER.admission_validate_expected_call(
                raw, hashlib.sha256(raw).hexdigest(), context, manifest, tools)
            self.assertEqual(admitted, expected)
            # Material snapshots retain the ordered five tool records as a
            # tuple; JSON necessarily carries those same records as a list.
            admitted_tuple = READER.admission_validate_expected_call(
                raw, hashlib.sha256(raw).hexdigest(), context, manifest, tuple(tools))
            self.assertEqual(admitted_tuple, expected)
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_expected_call(
                    raw, hashlib.sha256(raw).hexdigest(), context, manifest,
                    tuple(reversed(tools)))
            bad_args = json.loads(raw.decode("ascii"))
            bad_args["arguments"][-1] = r"C:\task\other-output"
            bad_raw = _dump(bad_args)
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_expected_call(
                    bad_raw, hashlib.sha256(bad_raw).hexdigest(), context, manifest, tools)
            bad_classes = json.loads(raw.decode("ascii"))
            bad_classes["classes"][0]["sha256"] = "0" * 64
            bad_raw = _dump(bad_classes)
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_expected_call(
                    bad_raw, hashlib.sha256(bad_raw).hexdigest(), context, manifest, tools)

    def test_cli_preserves_kernel_rejection_owner_and_nonzero_exit(self):
        stdout = io.BytesIO()
        stderr = io.StringIO()

        class _Stdout:
            buffer = stdout

        with mock.patch.object(READER, "parse_args", return_value={}), \
                mock.patch.object(READER, "admission_main",
                                  side_effect=READER.GR7_ReaderError("SYNTHETIC_TEST")), \
                mock.patch.object(READER.sys, "stdout", _Stdout()), \
                mock.patch.object(READER.sys, "stderr", stderr):
            code = READER.main([])
        self.assertEqual(code, 2)
        self.assertEqual(stdout.getvalue(), b"")
        self.assertEqual(stderr.getvalue(), "GR7_SYNTHETIC_TEST\n")

    def test_native_output_raw_path_alias_is_rejected_before_expected_call_read(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            alias = str(root) + "\\subdir\\..\\native-output"
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_capture_and_expected(
                    None, {"nativeOutput": alias}, "a" * 64, {}, {})

    def test_source_manifest_profile_pins_owner_and_dynamic_class_inventory(self):
        manifest = _valid_manifest()
        admitted = READER.admission_validate_manifest(_dump(manifest), {})
        self.assertEqual(admitted["classCount"], 46)
        self.assertEqual(admitted["ownerClassCount"], 46)
        bad_owner = json.loads(_dump(manifest).decode("ascii"))
        bad_owner["sources"][0]["sha256"] = "0" * 64
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_manifest(_dump(bad_owner), {})
        bad_rows = json.loads(_dump(manifest).decode("ascii"))
        bad_rows["classes"][1]["path"] = bad_rows["classes"][0]["path"]
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_manifest(_dump(bad_rows), {})

    def test_source_hash_pin_rejects_changed_source(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.java"
            path.write_bytes(b"changed source")
            with self.assertRaises(READER.ReaderError):
                READER.admission_check_file(
                    _LocalCheckedIO(), str(path), 64, "SOURCE", "f" * 64, 14)

    def test_fresh_class_inventory_and_tree_are_rehashed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            classes = root / "classes"
            classes.mkdir()
            raw_class = b"synthetic class bytes"
            (classes / "Example.class").write_bytes(raw_class)
            rows = [{"path": "Example.class", "bytes": len(raw_class),
                     "sha256": hashlib.sha256(raw_class).hexdigest()}]
            sidecar = root / "fresh-inventory.json"
            sidecar_raw = READER.admission_inventory_bytes(rows, 1, len(raw_class))
            sidecar.write_bytes(sidecar_raw)
            actual = READER.admission_validate_tree(
                str(classes), str(sidecar), sidecar_raw,
                hashlib.sha256(sidecar_raw).hexdigest(), rows, 1, len(raw_class))
            self.assertEqual(actual["rows"], (("Example.class", len(raw_class)),))
            (classes / "Example.class").write_bytes(b"replacement bytes")
            with self.assertRaises((READER.ReaderError, READER.RH_ReaderError)):
                READER.admission_validate_tree(
                    str(classes), str(sidecar), sidecar_raw,
                    hashlib.sha256(sidecar_raw).hexdigest(), rows, 1, len(raw_class))

    def test_external_capture_digest_strictly_binds_zero_exit_and_seven_receipts(self):
        with tempfile.TemporaryDirectory() as directory:
            context = _context(Path(directory))
            capture = _capture(context)
            raw = _dump(capture)
            digest = hashlib.sha256(raw).hexdigest()
            self.assertEqual(READER.admission_validate_capture_proof(raw, digest, context), capture)
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_capture_proof(raw, "0" * 64, context)
            bad_status = json.loads(raw.decode("ascii"))
            bad_status["call"]["status"] = "FAIL_ORDINARY_POSITIVE"
            bad_status_raw = _dump(bad_status)
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_capture_proof(
                    bad_status_raw, hashlib.sha256(bad_status_raw).hexdigest(), context)
            bool_exit = json.loads(raw.decode("ascii"))
            bool_exit["call"]["childExitCode"] = False
            bool_raw = _dump(bool_exit)
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_capture_proof(
                    bool_raw, hashlib.sha256(bool_raw).hexdigest(), context)
            extra_receipt = json.loads(raw.decode("ascii"))
            extra_receipt["call"]["receipts"]["untrusted.json"] = "e" * 64
            extra_raw = _dump(extra_receipt)
            with self.assertRaises((READER.ReaderError, READER.RH_ReaderError)):
                READER.admission_validate_capture_proof(
                    extra_raw, hashlib.sha256(extra_raw).hexdigest(), context)

    def test_call_directory_exact_nine_wrapper_and_receipt_pins(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result_raw = _dump({"status": "RETURNED", "exitCode": 0})
            call, raw = _call_directory(root, result=result_raw)
            wrong_pins = {name: "0" * 64 for name in READER.ADMISSION_RECEIPT_NAMES}
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_snapshot_call(
                    _LocalCheckedIO(), {"nativeCall": str(call)}, "nativeCall",
                    r"C:\jdk\java.exe", [], 480_000, "", wrong_pins, call.name)
            self.assertIn("RECEIPT_PIN", caught.exception.code)

            mismatch_wrapper = _dump({"status": "RETURNED", "exitCode": 0, "extra": True})
            call2, raw2 = _call_directory(root, result=result_raw, wrapper=mismatch_wrapper)
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_snapshot_call(
                    _LocalCheckedIO(), {"nativeCall": str(call2)}, "nativeCall",
                    r"C:\jdk\java.exe", [], 480_000, "",
                    {name: hashlib.sha256(raw2[name]).hexdigest()
                     for name in READER.ADMISSION_RECEIPT_NAMES}, call2.name)
            self.assertIn("WRAPPER_RESULT", caught.exception.code)

    def test_call_closure_rejects_extra_files_and_boolean_exit_code(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            call, raw = _call_directory(root, names=["extra.log"])
            pins = {name: hashlib.sha256(raw[name]).hexdigest()
                    for name in READER.ADMISSION_RECEIPT_NAMES}
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_snapshot_call(
                    _LocalCheckedIO(), {"nativeCall": str(call)}, "nativeCall",
                    r"C:\jdk\java.exe", [], 480_000, "", pins, call.name)
            self.assertIn("EXACT_NINE_FILES", caught.exception.code)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bool_result = _dump({"status": "RETURNED", "exitCode": False})
            call, raw = _call_directory(root, result=bool_result)
            pins = {name: hashlib.sha256(raw[name]).hexdigest()
                    for name in READER.ADMISSION_RECEIPT_NAMES}
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_snapshot_call(
                    _LocalCheckedIO(), {"nativeCall": str(call)}, "nativeCall",
                    r"C:\jdk\java.exe", [], 480_000, "", pins, call.name)
            self.assertIn("RESULT_EXIT_CODE_TYPE", caught.exception.code)

    def test_resource_lexists_and_material_recheck_reject_replacement(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resource = root / "owned.log"
            resource.write_bytes(b"left behind")
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_resource_absent(str(resource), "RESOURCE")
            self.assertIn("REMAINS", caught.exception.code)
            resource.unlink()
            READER.admission_resource_absent(str(resource), "RESOURCE")

            first = {"files": {"source": ("source", 1, "a", (1,))},
                     "directories": {}, "trees": {}, "calls": {},
                     "resultRaw": b"result", "markerRaw": b"marker"}
            changed = dict(first)
            changed["calls"] = {"native": ("receipt-dir", ("changed",))}
            with self.assertRaises(READER.ReaderError):
                READER.admission_compare_material(first, changed)

    def test_output_preflight_and_exclusive_write_reject_aliases_or_occupancy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            context = _context(root / "inputs")
            output = root / "accepted"
            output.mkdir()
            READER.admission_validate_output_location(context, str(output))
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_output_location(
                    context, str(output), (str(output / "base-generator.py"),))
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_output_location(context, str(root))

            existing = output / "result.json"
            existing.write_bytes(b"preserve")
            with self.assertRaises(READER.ReaderError):
                READER.admission_write_accepted(
                    _LocalCheckedIO(), output, b"replacement result", b"marker\n")
            self.assertEqual(existing.read_bytes(), b"preserve")

    def test_real_junction_and_dangling_reparse_path_are_rejected(self):
        if os.name != "nt":
            self.skipTest("real junction check requires Windows")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target"
            target.mkdir()
            target_identity = target.stat()
            junction = root / "junction"
            _create_junction(junction, target)
            launch = _junction_identity(junction)
            try:
                with self.assertRaises((READER.ReaderError, READER.RH_ReaderError)):
                    READER.admission_canonical_path(str(junction), "JUNCTION", "directory")
                now = target.lstat()
                self.assertTrue(stat.S_ISDIR(now.st_mode))
                self.assertEqual((now.st_dev, now.st_ino),
                                 (target_identity.st_dev, target_identity.st_ino))
                os.rmdir(target)
                with self.assertRaises(READER.ReaderError) as caught:
                    READER.admission_resource_absent(str(junction), "DANGLING")
                self.assertIn("REMAINS", caught.exception.code)
            finally:
                self.assertEqual(_junction_identity(junction), launch)
                os.rmdir(launch[0])
                self.assertFalse(os.path.lexists(junction))
            self.assertFalse(os.path.lexists(target))


class ActualContextAcceptanceTests(unittest.TestCase):
    def test_root_controlled_actual_context_and_external_capture(self):
        if ACTUAL is None:
            self.skipTest("root context and external capture digest not supplied")
        context_path, capture_sha, base_path, io_path = ACTUAL
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory) / "accepted"
            output_dir.mkdir()
            stdout_capture = io.BytesIO()
            stderr_capture = io.StringIO()

            class _Stdout:
                buffer = stdout_capture

            old_stdout, old_stderr = sys.stdout, sys.stderr
            try:
                sys.stdout = _Stdout()
                sys.stderr = stderr_capture
                code = READER.main([
                    "--context", context_path,
                    "--capture-proof-sha", capture_sha,
                    "--base-generator", base_path,
                    "--io-helper", io_path,
                    "--output-dir", str(output_dir),
                ])
            finally:
                sys.stdout, sys.stderr = old_stdout, old_stderr
            self.assertEqual(code, 0, stderr_capture.getvalue())
            summary = json.loads(stdout_capture.getvalue().decode("ascii"))
            self.assertEqual(summary["schema"], READER.ADMISSION_SUMMARY_SCHEMA)
            self.assertEqual(summary["status"], READER.ADMISSION_SUMMARY_STATUS)
            self.assertTrue(summary["consumedInputsRechecked"])
            self.assertTrue(summary["R7actualCounterProof"])
            accepted_result = output_dir / "result.json"
            accepted_marker = output_dir / "marker.txt"
            self.assertEqual(hashlib.sha256(accepted_result.read_bytes()).hexdigest(),
                             summary["resultSha256"])
            self.assertEqual(hashlib.sha256(accepted_marker.read_bytes()).hexdigest(),
                             summary["markerSha256"])


def _load_reader(reader_path):
    global READER
    spec = importlib.util.spec_from_file_location("tsj_generated_generic_r7_host_reader", reader_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("generated reader cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    READER = module


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--generated-reader")
    group.add_argument("--generated-dir")
    parser.add_argument("--context")
    parser.add_argument("--capture-proof-sha")
    parser.add_argument("--base-generator")
    parser.add_argument("--io-helper")
    parser.add_argument("--verbosity", type=int, default=2)
    args = parser.parse_args()
    reader_path = (Path(args.generated_reader) if args.generated_reader else
                   Path(args.generated_dir) / "read_source_bound_generic_r7_v1.py")
    provided = (args.context, args.capture_proof_sha, args.base_generator, args.io_helper)
    if any(provided):
        if not all(provided):
            parser.error("actual acceptance requires context, capture digest, base generator, and I/O helper")
        globals()["ACTUAL"] = (args.context, args.capture_proof_sha,
                               args.base_generator, args.io_helper)
    _load_reader(str(reader_path))
    unittest.main(argv=[sys.argv[0]], verbosity=args.verbosity)


if __name__ == "__main__":
    main()
