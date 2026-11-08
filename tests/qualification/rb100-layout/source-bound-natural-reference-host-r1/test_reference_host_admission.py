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
        result = Path(path).resolve(strict=True)
        if not result.is_dir() or result.is_symlink():
            raise READER.ReaderError(code)
        return result

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
    return (json.dumps(value, ensure_ascii=True, sort_keys=True,
                       separators=(",", ":")) + "\n").encode("ascii")


def _context_paths(root):
    manifest = root / "producer.json"
    return {
        "schema": READER.ADMISSION_CONTEXT_SCHEMA,
        "runId": "12345678-1234-5678-9abc-def012345678",
        "sourceManifestSha256": READER.ADMISSION_SOURCE_MANIFEST_SHA256,
        "wireContractSha256": READER.ADMISSION_WIRE_SHA256,
        "producerManifest": str(manifest),
        "sourcePaths": {
            "owner": str(root / "owner.java"),
            "study": str(root / "study.java"),
            "driver": str(root / "driver.java"),
        },
        "classesRoot": str(root / "classes"),
        "classInventory": str(root / "fresh-inventory.json"),
        "classInventorySha256": "1" * 64,
        "inheritedClassesRoot": str(root / "inherited-classes"),
        "inheritedClassInventory": str(root / "inherited-inventory.json"),
        "inheritedClassInventorySha256": "2" * 64,
        "legacyInventory": str(root / "legacy-inventory.json"),
        "gwtUser": str(root / "gwt-user.jar"),
        "gwtDev": str(root / "gwt-dev.jar"),
        "java": str(root / "java.exe"),
        "javac": str(root / "javac.exe"),
        "powershell": str(root / "powershell.exe"),
        "rootCall": str(root / "RootCall.ps1"),
        "processModule": str(root / "VerifierIsolation.psm1"),
        "compileCall": str(root / "compile-call"),
        "sourceList": str(root / "sources.txt"),
        "emptySourcepath": str(root / "empty-sourcepath"),
        "nativeCall": str(root / "native-call"),
        "fixtureSpec": str(root / "fixture.json"),
        "nativeReceiptProof": str(manifest.parent / "FINAL_GATES_R1.json"),
    }


def _receipt_envelope(result=None):
    if result is None:
        result = {
            "status": "RETURNED", "elapsedMs": 1.0, "exitCode": 0,
            "terminationProven": True, "stdoutBytes": 0, "stderrBytes": 0,
            "processRootAbsent": True, "totalElapsedMs": 2.0,
        }
    result_raw = _dump(result)
    raw = {name: ("receipt:" + name).encode("ascii")
           for name in READER.ADMISSION_COMPILE_RECEIPT_NAMES}
    raw["result.json"] = result_raw
    raw["wrapper.stdout"] = result_raw
    raw["wrapper.stderr"] = b""
    hashes = {name: hashlib.sha256(raw[name]).hexdigest()
              for name in READER.ADMISSION_COMPILE_RECEIPT_NAMES}
    return raw, hashes, result


def _create_junction(link, target):
    if os.name != "nt":
        raise OSError("Windows junction tests require Windows")
    link = str(link)
    target = str(target)
    os.mkdir(link)
    substitute = "\\??\\" + target
    printed = target
    substitute_raw = substitute.encode("utf-16-le")
    printed_raw = printed.encode("utf-16-le")
    path_buffer = (substitute_raw + b"\x00\x00" + printed_raw + b"\x00\x00")
    data_length = 8 + len(path_buffer)
    payload = struct.pack(
        "<IHHHHHH", 0xA0000003, data_length, 0,
        0, len(substitute_raw), len(substitute_raw) + 2, len(printed_raw),
    ) + path_buffer
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    create_file = kernel.CreateFileW
    create_file.argtypes = (ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32,
                            ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p)
    create_file.restype = ctypes.c_void_p
    device_control = kernel.DeviceIoControl
    device_control.argtypes = (ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p,
                               ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32,
                               ctypes.POINTER(ctypes.c_ulong), ctypes.c_void_p)
    device_control.restype = ctypes.c_int
    close_handle = kernel.CloseHandle
    close_handle.argtypes = (ctypes.c_void_p,)
    close_handle.restype = ctypes.c_int
    handle = create_file(
        link, 0x40000000, 0, None, 3, 0x02000000 | 0x00200000, None)
    invalid = ctypes.c_void_p(-1).value
    if handle == invalid:
        os.rmdir(link)
        raise OSError(ctypes.get_last_error(), "CreateFileW failed")
    returned = ctypes.c_ulong()
    payload_buffer = ctypes.create_string_buffer(payload)
    failure = None
    try:
        ok = device_control(
            handle, 0x000900A4, payload_buffer, len(payload),
            None, 0, ctypes.byref(returned), None)
        if not ok:
            failure = OSError(ctypes.get_last_error(), "FSCTL_SET_REPARSE_POINT failed")
    except Exception as error:
        failure = error
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
        raise AssertionError("Expected owned directory junction")
    return (str(path), info.st_dev, info.st_ino, info.st_mode, attributes, tag,
            getattr(info, "st_birthtime_ns", info.st_ctime_ns), os.readlink(path))


class ReferenceHostAdmissionTests(unittest.TestCase):
    def test_cli_accepts_exact_five_literal_pairs_and_rejects_aliases(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            values = {
                "--context": str(root / "context.json"),
                "--context-sha": "a" * 64,
                "--output-dir": str(root / "output"),
                "--base-generator": str(root / "base.py"),
                "--io-helper": str(root / "io.py"),
            }
            argv = [item for pair in values.items() for item in pair]
            parsed = READER.parse_args(argv)
            self.assertEqual(parsed, values)
            with self.assertRaises(READER.ReaderError):
                READER.parse_args(argv[:-2])
            with self.assertRaises(READER.ReaderError):
                READER.parse_args(argv + ["--context", values["--context"]])
            aliased = list(argv)
            aliased[1] = str(root / "subdir" / ".." / "context.json")
            with self.assertRaises(READER.ReaderError):
                READER.parse_args(aliased)

    def test_context_schema_external_digest_and_path_aliases(self):
        with tempfile.TemporaryDirectory() as directory:
            context = _context_paths(Path(directory))
            self.assertIs(READER.admission_validate_context_values(context), context)
            raw = _dump(context)
            digest = hashlib.sha256(raw).hexdigest()
            self.assertEqual(READER.admission_parse_context(raw, digest), context)
            with self.assertRaises(READER.ReaderError):
                READER.admission_parse_context(raw, "0" * 64)
            extra = dict(context)
            extra["unexpected"] = True
            with self.assertRaises(READER.RH_ReaderError):
                READER.admission_validate_context_values(extra)
            wrong_wire = dict(context)
            wrong_wire["wireContractSha256"] = "0" * 64
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_context_values(wrong_wire)
            aliased = dict(context)
            aliased["sourceList"] = aliased["sourcePaths"]["owner"]
            with self.assertRaises(READER.ReaderError):
                READER.admission_validate_context_values(aliased)

    def test_compile_and_native_argv_are_independently_reconstructed(self):
        self.assertEqual(READER.ADMISSION_COMPILE_STDERR,
                         "warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
                         "1 warning\r\n")
        context = {
            "classesRoot": r"C:\task\fresh-classes",
            "inheritedClassesRoot": r"C:\task\inherited-classes",
            "gwtUser": r"C:\sdk\gwt-user-2.7.0.jar",
            "gwtDev": r"C:\sdk\gwt-dev-2.7.0.jar",
            "emptySourcepath": r"C:\task\empty-sourcepath",
            "sourceList": r"C:\task\compiler-sources.txt",
            "javac": r"C:\jdk8\bin\javac.exe",
            "java": r"C:\jdk8\bin\java.exe",
            "runId": "12345678-1234-5678-9abc-def012345678",
            "sourceManifestSha256": READER.ADMISSION_SOURCE_MANIFEST_SHA256,
            "wireContractSha256": READER.ADMISSION_WIRE_SHA256,
        }
        calls = READER.admission_expected_invocations(context)
        classpath = r"C:\task\fresh-classes;C:\task\inherited-classes;C:\sdk\gwt-user-2.7.0.jar;C:\sdk\gwt-dev-2.7.0.jar"
        compile_literal = [
            "-source", "7", "-target", "7", "-encoding", "UTF-8",
            "-classpath", classpath, "-sourcepath", r"C:\task\empty-sourcepath",
            "-d", r"C:\task\fresh-classes", "@" + r"C:\task\compiler-sources.txt",
        ]
        native_literal = [
            "-Xmx1024m", "-cp", classpath,
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy",
            "GENERIC_ROUTING_REFERENCE_V1", "12345678-1234-5678-9abc-def012345678",
            READER.ADMISSION_SOURCE_MANIFEST_SHA256, READER.ADMISSION_WIRE_SHA256,
        ]
        self.assertEqual(calls["compile"], (
            r"C:\jdk8\bin\javac.exe", compile_literal, 60_000,
            READER.ADMISSION_COMPILE_STDERR))
        self.assertEqual(calls["native"], (r"C:\jdk8\bin\java.exe", native_literal, 480_000, ""))

    def test_pinned_source_role_order_is_literal(self):
        self.assertEqual(READER.ADMISSION_SOURCE_ROWS, (
            ("PcbLayerRoutingPrototype.java", "owner", 172_568,
             "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"),
            ("CohortTerminalTelemetryStudy.java", "study", 229_656,
             "8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14"),
            ("GenericRoutingReferenceCanary.java", "driver", 22_088,
             "68c78e29148d062969943b6ddf77bba15e3ca352ce03b16000eb9dfcded9e9c0"),
        ))

    def test_source_hash_pin_rejects_modified_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.java"
            path.write_bytes(b"changed source")
            with self.assertRaises(READER.ReaderError):
                READER.admission_check_file(
                    _LocalCheckedIO(), str(path), 64, "SOURCE", "f" * 64, 14)

    def test_class_inventory_and_tree_use_real_host_kernel(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            classes = root / "classes"
            classes.mkdir()
            data = b"synthetic class bytes"
            class_path = classes / "Example.class"
            class_path.write_bytes(data)
            rows = [{"path": "Example.class", "bytes": len(data),
                     "sha256": hashlib.sha256(data).hexdigest()}]
            sidecar = root / "class-inventory.json"
            raw = READER.admission_inventory_bytes(rows, 1, len(data))
            sidecar.write_bytes(raw)
            verified = READER.admission_validate_tree(
                str(classes), str(sidecar), raw, hashlib.sha256(raw).hexdigest(),
                rows, 1, len(data))
            self.assertEqual(verified["snapshot"]["rows"], (("Example.class", len(data)),))
            class_path.write_bytes(b"replaced bytes")
            with self.assertRaises(READER.RH_ReaderError):
                READER.admission_validate_tree(
                    str(classes), str(sidecar), raw, hashlib.sha256(raw).hexdigest(),
                    rows, 1, len(data))

    def test_receipt_wrapper_stream_pins_and_strict_exit_type(self):
        raw, pins, result = _receipt_envelope()
        self.assertEqual(READER.admission_validate_call_envelope("nativeCall", raw, pins), result)
        mismatch = dict(raw)
        mismatch["wrapper.stdout"] = _dump({"status": "RETURNED", "exitCode": 0})
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_call_envelope("nativeCall", mismatch, pins)
        mismatch = dict(raw)
        mismatch["wrapper.stderr"] = b"unexpected\n"
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_call_envelope("nativeCall", mismatch, pins)
        bad_pins = dict(pins)
        bad_pins["stdout.txt"] = "0" * 64
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_call_envelope("nativeCall", raw, bad_pins)
        bool_exit = dict(result)
        bool_exit["exitCode"] = False
        bool_raw, bool_pins, _ = _receipt_envelope(bool_exit)
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_call_envelope("compileCall", bool_raw, bool_pins)

    def test_native_receipt_proof_binds_context_and_exact_seven_pins(self):
        context = {
            "runId": "12345678-1234-5678-9abc-def012345678",
            "sourceManifestSha256": READER.ADMISSION_SOURCE_MANIFEST_SHA256,
            "wireContractSha256": READER.ADMISSION_WIRE_SHA256,
        }
        proof_context = {
            "runId": context["runId"],
            "sourceSha256": context["sourceManifestSha256"],
            "wireSha256": context["wireContractSha256"],
        }
        receipts = {name: "a" * 64 for name in READER.ADMISSION_COMPILE_RECEIPT_NAMES}
        proof = {"gates": {"ACCEPTANCE_R1": {
            "context": proof_context, "native": {"receipts": receipts},
        }}}
        self.assertEqual(READER.admission_validate_native_proof(_dump(proof), context), receipts)
        bad_context = dict(proof_context)
        bad_context["sourceSha256"] = "0" * 64
        bad = {"gates": {"ACCEPTANCE_R1": {
            "context": bad_context, "native": {"receipts": receipts},
        }}}
        with self.assertRaises(READER.ReaderError):
            READER.admission_validate_native_proof(_dump(bad), context)
        bad_receipts = dict(receipts)
        bad_receipts["extra.json"] = "b" * 64
        bad = {"gates": {"ACCEPTANCE_R1": {
            "context": proof_context, "native": {"receipts": bad_receipts},
        }}}
        with self.assertRaises(READER.RH_ReaderError):
            READER.admission_validate_native_proof(_dump(bad), context)

    def test_call_directory_requires_exact_nine_regular_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            call = root / "call"
            call.mkdir()
            context = {"compileCall": str(call)}
            names = list(READER.ADMISSION_CALL_NAMES)
            for name in names[:-1]:
                (call / name).write_bytes(b"")
            args = ("javac.exe", [], 60_000, "")
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_snapshot_call(
                    _LocalCheckedIO(), context, "compileCall", *args,
                    {name: "0" * 64 for name in READER.ADMISSION_COMPILE_RECEIPT_NAMES})
            self.assertIn("EXACT_NINE_FILES", caught.exception.code)
            (call / names[-1]).write_bytes(b"")
            (call / "extra.log").write_bytes(b"")
            with self.assertRaises(READER.ReaderError) as caught:
                READER.admission_snapshot_call(
                    _LocalCheckedIO(), context, "compileCall", *args,
                    {name: "0" * 64 for name in READER.ADMISSION_COMPILE_RECEIPT_NAMES})
            self.assertIn("EXACT_NINE_FILES", caught.exception.code)

    def test_file_replacement_between_snapshots_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "input.bin"
            replacement = root / "replacement.bin"
            path.write_bytes(b"same bytes")
            first_raw, first_record = READER.admission_check_file(
                _LocalCheckedIO(), str(path), 64, "INPUT", hashlib.sha256(b"same bytes").hexdigest())
            replacement.write_bytes(first_raw)
            os.replace(replacement, path)
            _second_raw, second_record = READER.admission_check_file(
                _LocalCheckedIO(), str(path), 64, "INPUT", hashlib.sha256(b"same bytes").hexdigest())
            self.assertNotEqual(first_record, second_record)
            left = {"files": {"input": first_record}, "directories": {}, "trees": {},
                    "calls": {}, "packetRaw": b""}
            right = {"files": {"input": second_record}, "directories": {}, "trees": {},
                     "calls": {}, "packetRaw": b""}
            with self.assertRaises(READER.ReaderError):
                READER.admission_compare_material(left, right)

    def test_exclusive_output_refuses_occupied_target_without_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory)
            output = output_dir / READER.ADMISSION_OUTPUT_NAME
            output.write_bytes(b"preserve")
            with self.assertRaises(READER.ReaderError):
                READER.admission_write_exclusive(_LocalCheckedIO(), output_dir, b"replacement")
            self.assertEqual(output.read_bytes(), b"preserve")

    def test_real_junction_is_rejected_and_dangling_junction_is_not_absent(self):
        if os.name != "nt":
            self.skipTest("real junction proof requires Windows")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "target"
            target.mkdir()
            junction = root / "junction"
            self.assertEqual(root.resolve(strict=True), root)
            self.assertFalse(root.is_symlink())
            self.assertEqual(target.parent.resolve(strict=True), root)
            target_identity = target.stat()
            _create_junction(junction, target)
            launch = _junction_identity(junction)
            try:
                with self.assertRaises(READER.RH_ReaderError):
                    READER.admission_canonical_path(str(junction), "JUNCTION", "directory")
                self.assertEqual(target.parent.resolve(strict=True), root)
                target_now = target.lstat()
                self.assertTrue(stat.S_ISDIR(target_now.st_mode))
                self.assertEqual((target_now.st_dev, target_now.st_ino),
                                 (target_identity.st_dev, target_identity.st_ino))
                self.assertFalse(getattr(target_now, "st_file_attributes", 0) & 0x400)
                self.assertEqual(list(target.iterdir()), [])
                os.rmdir(target)
                with self.assertRaises(READER.ReaderError) as caught:
                    READER.admission_resource_absent(str(junction), "DANGLING_RESOURCE")
                self.assertIn("REMAINS", caught.exception.code)
            finally:
                self.assertEqual(junction.parent.resolve(strict=True), root)
                self.assertEqual(_junction_identity(junction), launch)
                os.rmdir(launch[0])
                self.assertFalse(os.path.lexists(junction))
            self.assertFalse(os.path.lexists(target))


class ActualContextAcceptanceTests(unittest.TestCase):
    def test_root_controlled_actual_context(self):
        if ACTUAL is None:
            self.skipTest("root context and checked-I/O paths not supplied")
        context_path, context_sha, base_path, io_path = ACTUAL
        with tempfile.TemporaryDirectory() as directory:
            output_dir = Path(directory) / "out"
            output_dir.mkdir()
            stdout_capture = io.BytesIO()

            class _Stdout:
                buffer = stdout_capture

            old_stdout, old_stderr = sys.stdout, sys.stderr
            stderr_capture = io.StringIO()
            try:
                sys.stdout = _Stdout()
                sys.stderr = stderr_capture
                code = READER.main([
                    "--context", context_path,
                    "--context-sha", context_sha,
                    "--output-dir", str(output_dir),
                    "--base-generator", base_path,
                    "--io-helper", io_path,
                ])
            finally:
                sys.stdout, sys.stderr = old_stdout, old_stderr
            self.assertEqual(code, 0, stderr_capture.getvalue())
            summary_raw = stdout_capture.getvalue()
            summary = json.loads(summary_raw.decode("ascii"))
            self.assertEqual(summary["schema"], READER.ADMISSION_SUMMARY_SCHEMA)
            self.assertEqual(summary["status"], READER.ADMISSION_SUMMARY_STATUS)
            self.assertTrue(summary["consumedInputsRechecked"])
            output = output_dir / READER.ADMISSION_OUTPUT_NAME
            self.assertTrue(output.is_file())
            self.assertEqual(hashlib.sha256(output.read_bytes()).hexdigest(), summary["inputSha256"])


def _load_reader(reader_path):
    global READER
    spec = importlib.util.spec_from_file_location("tsj_generated_reference_host_reader", reader_path)
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
    parser.add_argument("--context-sha")
    parser.add_argument("--base-generator")
    parser.add_argument("--io-helper")
    parser.add_argument("--verbosity", type=int, default=2)
    args = parser.parse_args()
    reader_path = (Path(args.generated_reader) if args.generated_reader else
                   Path(args.generated_dir) / "read_source_bound_reference_v1.py")
    if args.context or args.context_sha or args.base_generator or args.io_helper:
        if not all((args.context, args.context_sha, args.base_generator, args.io_helper)):
            parser.error("actual context requires --context, --context-sha, --base-generator, and --io-helper")
        ACTUAL = (args.context, args.context_sha, args.base_generator, args.io_helper)
        globals()["ACTUAL"] = ACTUAL
    _load_reader(str(reader_path))
    unittest.main(argv=[sys.argv[0]], verbosity=args.verbosity)


if __name__ == "__main__":
    main()
