"""Source-locked generator, contract and real-tempfs tests; does not execute the generator CLI."""
import argparse
import hashlib
import importlib.util
import os
import stat
import sys
import tempfile
import unittest
from pathlib import Path

parser = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
parser.add_argument("--study", required=True)
parser.add_argument("--base-generator", required=True)
parser.add_argument("--io-helper", required=True)
paths, unittest_args = parser.parse_known_args()
STUDY = Path(paths.study)
BASE = Path(paths.base_generator)
HELPER = Path(paths.io_helper)
MODULE_PATH = Path(__file__).resolve().with_name("generate_selection_consumer_overlay.py")
spec = importlib.util.spec_from_file_location("selection_consumer_overlay_under_test", MODULE_PATH)
overlay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(overlay)

def source_slice(raw, start, end):
    a = raw.index(start)
    b = raw.index(end, a + len(start))
    return raw[a:b]

class SourceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parent = STUDY.read_bytes()
        cls.base = BASE.read_bytes()

    def test_exact_derived_bytes_and_preserved_terminal_owner(self):
        derived, changes = overlay.derive(self.parent)
        again, second_changes = overlay.derive(self.parent)
        self.assertEqual(derived, again)
        self.assertEqual(changes, overlay.CHANGES)
        self.assertEqual(second_changes, changes)
        self.assertIn(self.parent[
            self.parent.index(b"static final class DetachedTerminal {"):
            self.parent.index(b"/** No owner references; callbackGroupSamples", self.parent.index(
                b"static final class DetachedTerminal {"))
        ], derived)
        tuple_start = b"    private int validateTuple(DetachedTerminal t, String requestId) {\n"
        tuple_end = b"        return ordinal;\n    }\n"
        original_at = self.parent.index(tuple_start)
        original_end = self.parent.index(tuple_end, original_at) + len(tuple_end)
        self.assertIn(self.parent[original_at:original_end], derived)
        self.assertIn(b"static final int FIELD_COUNT=33, CALLBACK_CEILING=512, POST_CALL_BOUND=104, GROUP_CAPACITY=32768;", derived)
        self.assertIn(b"static final long COPY_CAPACITY=1048576L;", derived)
        self.assertEqual(derived.count(b"observeSelected(String requestId"), 2)
        self.assertIn(b"observe(requestId,snapshot,head,staleRejectionMeter);", derived)
        self.assertEqual(derived.count(b"private synchronized void observe(String requestId"), 1)

    def test_main_and_result_export_fail_before_prior_work(self):
        derived, _ = overlay.derive(self.parent)
        main = source_slice(derived, b"    public static void main(String[] args) {\n",
            b"    static final class Arguments {\n")
        self.assertEqual(main, overlay.MAIN)
        self.assertNotIn(b"Arguments.parse", main)
        self.assertNotIn(b"assemble(", main)
        result = source_slice(derived,
            b"    synchronized java.util.Map<String, Object> resultMap() {\n",
            b"    private void unit() {")
        self.assertEqual(result, overlay.RESULT_DENIAL)
        self.assertNotIn(b"new java.util.LinkedHashMap", result)
        self.assertIn(b"SELECTION_CONSUMER_COMPONENT_ONLY", main)
        self.assertIn(b"SELECTION_CONSUMER_NO_WIRE", result)

    def test_selection_schema_and_exact_validation_schedule(self):
        derived, _ = overlay.derive(self.parent)
        head = source_slice(derived, b"static final class DetachedHead {\n",
            b"/** No owner references; callbackGroupSamples")
        names = (b"branchOrdinal", b"cell", b"layer", b"direction", b"transitions",
            b"cost", b"storedHeuristic", b"serial", b"key", b"storedPriority")
        self.assertEqual(head.count(b"final int"), 1)
        self.assertTrue(all(name in head for name in names))
        validate = source_slice(derived,
            b"    private void validateSelection(DetachedTerminal t,DetachedHead h) {\n",
            b"    private void checkId(String value) {\n")
        self.assertEqual(validate.count(b"validation();"), 14)
        self.assertEqual(validate.split(b"if (h==null) return;")[0].count(b"validation();"), 2)
        for token in (b"branchOrdinal<1", b"h.cell<0", b"h.layer>1",
                b"h.direction>4", b"48-t.prefixViaCount", b"h.cost<0",
                b"h.storedHeuristic<0", b"h.serial<0",
                b"(((h.transitions*2+h.layer)*5+h.direction)*cells)+h.cell",
                b"h.cost+h.storedHeuristic", b"t.branchExpanded!=100000",
                b"t.totalExpanded>=1000000", b"t.bestStateCount<1"):
            self.assertIn(token, validate)

    def test_copy_capacity_and_live_release_schedule(self):
        derived, _ = overlay.derive(self.parent)
        core = source_slice(derived,
            b"    private synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot,\n",
            b"    private void checkOpen() {\n")
        for token in (b"requiredFields=FIELD_COUNT+(borrowedHead==null?0:10)",
                b"requiredChunks=borrowedHead==null?1:2",
                b"COPY_CAPACITY-requiredFields",
                b"Integer.MAX_VALUE-requiredChunks",
                b"Long.MAX_VALUE-requiredFields",
                b"meter.beginCopyChunk(10)", b"headCopies++",
                b"headReleases++; unit()", b"selected=oldSelected; selectedHead=oldSelectedHead"):
            self.assertIn(token, core)
        read_head = source_slice(derived,
            b"    private int readHead(PcbLayerRoutingPrototype.BranchLimitSelection s,int f) {\n",
            b"    private int validateTuple(DetachedTerminal t, String requestId) {\n")
        self.assertEqual(read_head.count(b"chargeReadCopy();"), 1)
        detach_head = source_slice(derived,
            b"    private DetachedHead detachHead(PcbLayerRoutingPrototype.BranchLimitSelection s) {\n",
            b"    private int validateTuple(DetachedTerminal t, String requestId) {\n")
        self.assertEqual(detach_head.count(b"readHead(s,"), 10)
        self.assertIn(b"chunks > Integer.MAX_VALUE-requiredChunks", core)
        counters = source_slice(derived, b"    private boolean countersNearOverflow(int requiredFields,int requiredChunks) {\n",
            b"    private void chargeReadCopy() {\n")
        self.assertIn(b"headCopies > Long.MAX_VALUE-512", counters)
        self.assertIn(b"headReleases > Long.MAX_VALUE-512", counters)

    def test_sink_and_core_share_existing_abort_boundary(self):
        derived, _ = overlay.derive(self.parent)
        sink = source_slice(derived,
            b"    static final class TerminalSink implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {\n",
            b"/* Replacement sink factory members")
        self.assertEqual(sink.count(b"meter.insideOwner=true"), 1)
        self.assertEqual(sink.count(b"finally { meter.insideOwner=false; }"), 1)
        self.assertIn(b"observer.observe(requestId,snapshot)", sink)
        self.assertIn(b"observer.observeSelected(requestId,snapshot,selection)", sink)
        core = source_slice(derived,
            b"    private synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot,\n",
            b"    private void checkOpen() {\n")
        self.assertIn(b"catch (Throwable t)", core)
        self.assertIn(b"if (failure != null) { poisoned=true; sealed=true; }", core)
        self.assertIn(b"if (failure != null && committed)", core)
        stale = source_slice(derived,
            b"    private void rejectStale(PcbLayerRoutingPrototype.BranchSnapshot snapshot,\n",
            b"    synchronized void seal() { sealed=true; }\n")
        self.assertIn(b"rejectionMeter.charge(1)", stale)
        self.assertIn(b"borrowedHead=null", stale)
        self.assertNotIn(b"headReleases++", stale)

    def test_source_size_pin_and_splice_guards(self):
        self.assertEqual(hashlib.sha256(self.parent).hexdigest(), overlay.STUDY_SHA256)
        self.assertEqual(hashlib.sha256(self.base).hexdigest(), overlay.BASE_GENERATOR_SHA256)
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.derive(self.parent + b"\n")
        self.assertEqual(caught.exception.code, "STUDY_HASH")
        for data in (b"absent", b"needle needle"):
            with self.subTest(data=data):
                with self.assertRaises(overlay.OverlayError) as caught:
                    overlay.replace_once(data, b"needle", b"replacement")
                self.assertEqual(caught.exception.code, "SPLICE_COUNT")
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.replace_once("not bytes", b"x", b"y")
        self.assertEqual(caught.exception.code, "SPLICE_TYPES")

    def test_cli_requires_exact_nonambiguous_pairs(self):
        good = ["--study", "a", "--base-generator", "b", "--io-helper", "c",
                "--output-dir", "d"]
        self.assertEqual(set(overlay.parse_args(good)),
            {"--study", "--base-generator", "--io-helper", "--output-dir"})
        bad = [
            good[:-2],
            ["--study", "a", "--study", "b", "--io-helper", "c", "--output-dir", "d"],
            ["--s", "a", "--base-generator", "b", "--io-helper", "c", "--output-dir", "d"],
            ["--study", "a\0b", "--base-generator", "b", "--io-helper", "c", "--output-dir", "d"],
            good + ["--extra", "x"],
            ["--study", "--base-generator", "--io-helper", "c", "--output-dir", "d"],
        ]
        for value in bad:
            with self.subTest(value=value):
                with self.assertRaises(overlay.OverlayError) as caught:
                    overlay.parse_args(value)
                self.assertEqual(caught.exception.code, "CLI_ARGUMENTS")

class FilesystemTests(unittest.TestCase):
    def setUp(self):
        self.temp = Path(tempfile.gettempdir()).resolve(strict=True)
        self.root = Path(tempfile.mkdtemp(prefix="tsj-selection-consumer-unit-", dir=str(self.temp)))
        self.out = self.root / "out"
        self.out.mkdir()
        self.links = []
        self.addCleanup(self.cleanup)

    def options(self, out=None, study=STUDY, base=BASE, helper=HELPER):
        return {"--study": str(study), "--base-generator": str(base),
                "--io-helper": str(helper), "--output-dir": str(out or self.out)}

    def make_link(self, target, link, directory=False):
        try:
            os.symlink(str(target), str(link), target_is_directory=directory)
        except (OSError, NotImplementedError) as failure:
            self.skipTest("filesystem symlink control unavailable: " + type(failure).__name__)
        self.links.append(link)
        return link

    def test_real_positive_exclusive_two_file_output_and_sanitized_proof(self):
        proof = overlay.generate(self.options())
        self.assertEqual({p.name for p in self.out.iterdir()},
            {"CohortTerminalTelemetryStudy.java", "SOURCE_PROOF.json"})
        self.assertEqual(proof["studyParentSha256"], overlay.STUDY_SHA256)
        self.assertEqual(proof["baseGeneratorSha256"], overlay.BASE_GENERATOR_SHA256)
        self.assertEqual(hashlib.sha256((self.out / "CohortTerminalTelemetryStudy.java").read_bytes()).hexdigest(),
            proof["derivedSha256"])
        raw = (self.out / "SOURCE_PROOF.json").read_text(encoding="ascii")
        self.assertNotIn(str(STUDY), raw)
        self.assertNotIn(str(BASE), raw)
        self.assertNotIn(str(HELPER), raw)
        self.assertEqual(proof["authoredInvocations"], 0)

    def test_occupied_output_is_rejected_without_overwrite(self):
        sentinel = self.out / "keep.txt"
        sentinel.write_bytes(b"PRESERVE")
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.generate(self.options())
        self.assertEqual(caught.exception.code, "OUTPUT_NOT_EMPTY")
        self.assertEqual(sentinel.read_bytes(), b"PRESERVE")

    def test_bad_study_parent_rejects_before_output_write(self):
        bad = self.root / "bad-study.java"
        bad.write_bytes(STUDY.read_bytes() + b"\n")
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.generate(self.options(study=bad))
        self.assertEqual(caught.exception.code, "STUDY_HASH")
        self.assertEqual(list(self.out.iterdir()), [])

    def test_bad_base_and_helper_are_hash_rejected_before_execution(self):
        executed = self.root / "executed.txt"
        payload = ("from pathlib import Path\nPath("+repr(str(executed))+
            ").write_text('EXECUTED',encoding='ascii')\n").encode("ascii")
        bad_base = self.root / "bad-base.py"
        bad_base.write_bytes(payload)
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.load_base_generator(str(bad_base))
        self.assertEqual(caught.exception.code, "BASE_GENERATOR_HASH")
        self.assertFalse(executed.exists())

        bad_helper = self.root / "bad-helper.py"
        bad_helper.write_bytes(payload)
        base_module = overlay.load_base_generator(str(BASE))
        with self.assertRaises(base_module.OverlayError) as caught:
            base_module.load_io(str(bad_helper))
        self.assertEqual(caught.exception.code, "IO_HELPER_HASH")
        self.assertFalse(executed.exists())

    def test_noncanonical_base_parent_is_rejected(self):
        bad_path = str(BASE.parent / "missing" / ".." / BASE.name)
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.load_base_generator(bad_path)
        self.assertIn(caught.exception.code,
            {"BASE_GENERATOR_CANONICAL", "BASE_GENERATOR_READ"})

    def test_reparse_study_helper_base_and_output_controls(self):
        study_link = self.make_link(STUDY, self.root / "study-link.java")
        with self.assertRaises(ValueError) as caught:
            overlay.generate(self.options(study=study_link))
        self.assertEqual(type(caught.exception).__name__, "ExportError")
        self.assertEqual(caught.exception.code, "OWNER_SOURCE_CANONICAL")
        self.assertEqual(list(self.out.iterdir()), [])

        base_link = self.make_link(BASE, self.root / "base-link.py")
        with self.assertRaises(overlay.OverlayError) as caught:
            overlay.load_base_generator(str(base_link))
        self.assertEqual(caught.exception.code, "BASE_GENERATOR_REPARSE")

        helper_link = self.make_link(HELPER, self.root / "helper-link.py")
        base_module = overlay.load_base_generator(str(BASE))
        with self.assertRaises(base_module.OverlayError) as caught:
            base_module.load_io(str(helper_link))
        self.assertEqual(caught.exception.code, "IO_HELPER_REPARSE")

        out_link = self.make_link(self.out, self.root / "out-link", directory=True)
        with self.assertRaises(ValueError) as caught:
            overlay.generate(self.options(out=out_link))
        self.assertEqual(type(caught.exception).__name__, "ExportError")
        self.assertEqual(caught.exception.code, "OUTPUT_DIRECTORY_CANONICAL")
        self.assertEqual(list(self.out.iterdir()), [])

    def cleanup(self):
        self.assertTrue(self.root.exists())
        root_info = self.root.lstat()
        self.assertTrue(stat.S_ISDIR(root_info.st_mode) and not stat.S_ISLNK(root_info.st_mode))
        self.assertEqual(self.root.parent.resolve(strict=True), self.temp)
        self.assertEqual(self.root.resolve(strict=True), self.root)
        allowed_links = {link.name for link in self.links}
        allowed_files = {"bad-study.java", "bad-base.py", "bad-helper.py", "executed.txt"}
        for item in tuple(self.root.iterdir()):
            if item == self.out:
                out_info = item.lstat()
                self.assertTrue(stat.S_ISDIR(out_info.st_mode) and not stat.S_ISLNK(out_info.st_mode))
                self.assertEqual(item.resolve(strict=True), item)
                allowed_out = {"CohortTerminalTelemetryStudy.java", "SOURCE_PROOF.json", "keep.txt"}
                for child in tuple(item.iterdir()):
                    info = child.lstat()
                    self.assertIn(child.name, allowed_out)
                    self.assertTrue(stat.S_ISREG(info.st_mode) and
                        not (getattr(info, "st_file_attributes", 0) & 0x400))
                    child.unlink()
                item.rmdir()
            elif item.name in allowed_links:
                info = item.lstat()
                self.assertTrue(stat.S_ISLNK(info.st_mode) or
                    (getattr(info, "st_file_attributes", 0) & 0x400))
                item.unlink()
            else:
                info = item.lstat()
                self.assertIn(item.name, allowed_files)
                self.assertTrue(stat.S_ISREG(info.st_mode) and
                    not (getattr(info, "st_file_attributes", 0) & 0x400))
                item.unlink()
        self.root.rmdir()

if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *unittest_args])
