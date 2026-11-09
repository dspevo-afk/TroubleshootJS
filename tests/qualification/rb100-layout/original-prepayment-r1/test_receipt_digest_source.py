"""Frozen Source/CLI controls. Execution belongs to root, not the authoring worker."""
import contextlib
import argparse
import importlib.util
import io
import json
from pathlib import Path
import re
import tempfile
import sys
import unittest

ROOT = Path(__file__).resolve().parent
_parser=argparse.ArgumentParser(add_help=False)
_parser.add_argument("--source-root",type=Path,default=ROOT/"source")
_args,_unittest_args=_parser.parse_known_args()
SOURCE=_args.source_root
sys.argv=[sys.argv[0],*_unittest_args]
SPEC = importlib.util.spec_from_file_location("receipt_digest_generator", ROOT / "generate_receipt_digest.py")
g = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(g)

SMOKE = ["file_empty", "file_1", "file_4096", "file_4097", "file_8192", "file_8mib",
         "file_overcap_9mib", "file_expired_before_body", "file_capacity_before_body",
         "file_empty_closed_meter", "file_unmetered_4097"]
SUITE = ["short_one_byte_4097", "short_2048_4096", "short_2048_4097", "chunk_4096_8192",
         "chunk_8192_8192", "empty_eof_probe", "zero_progress", "early_eof", "extra_byte",
         "overreturn", "read_error", "error_after_prefix", "expiry_during_read",
         "expiry_during_eof", "bounded_final_read", "eof_read_error"]


class ReceiptDigestSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (SOURCE / "SHARED_OWNER.java").read_bytes()
        cls.fragment = (ROOT / "ReceiptDigestMembers.java.fragment").read_bytes()
        cls.canary = (ROOT / "OriginalReceiptDigestCanary.java").read_text(encoding="utf-8")

    def test_exact_span_and_unchanged_bytes(self):
        output, proof = g.render(self.source, self.fragment)
        start = self.source.index(g.START)
        end = self.source.index(g.AFTER, start)
        self.assertEqual(output, self.source[:start] + self.fragment[:-1] + self.source[end:])
        self.assertEqual(proof["unchangedPrefixSha256"], g.sha256(self.source[:start]))
        self.assertEqual(proof["unchangedSuffixSha256"], g.sha256(self.source[end:]))
        self.assertEqual(proof["emittedSha256"], g.sha256(output))
        self.assertEqual(output.count(g.UNMETERED), 1)
        self.assertTrue(proof["changedOnlyReceiptDigestSpan"])

    def test_source_pin_rejects_mutation_truncation_and_newline_change(self):
        for source in (self.source + b"\n", self.source[:-1],
                       self.source.replace(b"RECEIPT_LIMIT=8L", b"RECEIPT_LIMIT=9L", 1),
                       self.source.replace(b"\n", b"\r\n")):
            with self.subTest(size=len(source)):
                with self.assertRaisesRegex(ValueError, "SHARED_OWNER_SOURCE_PIN"):
                    g.render(source, self.fragment)

    def test_fragment_pin_rejects_mutation_truncation_and_extra_members(self):
        for fragment in (self.fragment[:-1], self.fragment + b"\n",
                         self.fragment.replace(b"4095L", b"4096L", 1),
                         self.fragment + b"private static int extra;\n"):
            with self.subTest(size=len(fragment)):
                with self.assertRaisesRegex(ValueError, "RECEIPT_DIGEST_FRAGMENT_PIN"):
                    g.render(self.source, fragment)

    def test_cli_invalid_inputs_create_no_output_or_directory(self):
        with tempfile.TemporaryDirectory(prefix="tsj-receipt-source-negative-") as temp:
            base = Path(temp)
            source = base / "source.java"
            fragment = base / "members.fragment"
            output = base / "never-created" / "FailureCohortRoutingStudy.java"
            for source_bytes, fragment_bytes, error in (
                    (self.source + b"x", self.fragment, "SHARED_OWNER_SOURCE_PIN"),
                    (self.source, self.fragment + b"x", "RECEIPT_DIGEST_FRAGMENT_PIN")):
                source.write_bytes(source_bytes)
                fragment.write_bytes(fragment_bytes)
                with self.assertRaisesRegex(ValueError, error):
                    g.main(["--source", str(source), "--fragment", str(fragment), "--output", str(output)])
                self.assertFalse(output.parent.exists())

    def test_cli_emits_parity_and_preserves_existing_release(self):
        with tempfile.TemporaryDirectory(prefix="tsj-receipt-source-cli-") as temp:
            output = Path(temp) / "emitted" / "FailureCohortRoutingStudy.java"
            captured = io.StringIO()
            with contextlib.redirect_stdout(captured):
                proof = g.main(["--source",str(SOURCE/"SHARED_OWNER.java"),"--output", str(output)])
            self.assertEqual(json.loads(captured.getvalue()), proof)
            emitted = output.read_bytes()
            self.assertEqual(emitted, g.render(self.source, self.fragment)[0])
            with self.assertRaises(FileExistsError):
                g.main(["--source",str(SOURCE/"SHARED_OWNER.java"),"--output", str(output)])
            self.assertEqual(output.read_bytes(), emitted)

    def test_prepayment_precedes_stream_hash_and_buffer(self):
        text = self.fragment.decode("utf-8")
        self.assertEqual(text.count("meter.charge("), 1)
        charge = text.index("meter.charge((expectedSize+4095L)/4096L)")
        self.assertLess(text.index("Files.size(path)"), charge)
        self.assertLess(text.index("expectedSize>RECEIPT_LIMIT"), charge)
        for work in ("Files.newInputStream(path)", "MessageDigest.getInstance", "new byte[8192]"):
            self.assertLess(charge, text.index(work))
        helper = text[text.index("private static Digest fileDigestRead"):]
        self.assertNotIn(".charge(", helper)
        self.assertNotIn(".reserve(", text)
        self.assertNotIn("beginGroup", text)
        self.assertNotIn("finishGroup", text)

    def test_exact_read_guards_and_deadline_brackets(self):
        text = self.fragment.decode("utf-8")
        for guard in ("Math.min((long)buffer.length,expectedSize-count)", "amount<0",
                      "amount==0", "amount>length", "extra!=-1", "finalSize!=result.bytes",
                      "result.bytes!=expectedSize", "!regular || symlink"):
            self.assertIn(guard, text)
        for operation in ("int amount=input.read(buffer,0,length);", "digest.update(buffer,0,amount);",
                          "int extra=input.read();", "byte[] hash=digest.digest();",
                          "long expectedSize=Files.size(path);", "long finalSize=Files.size(path);",
                          "boolean regular=Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS);",
                          "boolean symlink=Files.isSymbolicLink(path);"):
            pos = text.index(operation)
            self.assertTrue(text[pos + len(operation):].lstrip().startswith("if(meter!=null) meter.check();"))
        self.assertEqual(text.count("new Digest("), 1)

    def test_actual_owner_canary_and_frozen_inventory(self):
        for label, expected in (("SMOKE", SMOKE), ("SUITE", SUITE)):
            match = re.search(r"String\[\] " + label + r"=\{(.*?)\};", self.canary, re.S)
            self.assertIsNotNone(match)
            self.assertEqual(re.findall(r'"([a-z0-9_]+)"', match.group(1)), expected)
        self.assertIn("new CohortTerminalTelemetryStudy.Parent()", self.canary)
        self.assertIn("new CohortTerminalTelemetryStudy.SetupMeter(p,meter)", self.canary)
        self.assertIn('p.call("fileDigest",new Class<?>[]{Path.class,p.meterType}', self.canary)
        self.assertIn("NativeFixture() throws Exception { m.charge(0); }", self.canary)
        for forbidden in (".original(", 'call("captureOriginal"', "new OriginalArm", "new ArmResult",
                          "new Session", "runFresh(", "ProcessBuilder", "Runtime.getRuntime"):
            self.assertNotIn(forbidden, self.canary)
        self.assertLessEqual(len(self.canary.splitlines()), 500)


if __name__ == "__main__":
    unittest.main()
