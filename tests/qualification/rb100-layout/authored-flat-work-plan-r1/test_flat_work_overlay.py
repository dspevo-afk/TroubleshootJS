"""Current R7 Source-only flat-work overlay checks; never execute Java."""

import argparse
import ast
import hashlib
import json
from pathlib import Path
import re
import sys
import types
import unittest


ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("--study-source", required=True)
parser.add_argument("--authored-source", default=str(ROOT / "AuthoredFlatWorkMembers.java"))
parser.add_argument("--generator-source", default=str(ROOT / "generate_authored_flat_work_overlay.py"))
args, remainder = parser.parse_known_args()
sys.argv = [sys.argv[0], *remainder]
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize


STUDY_BYTES = 234954
STUDY_SHA256 = "9ac45b374c8aeee7a912a4e1452435b71b278aeae0a288e5a75dd82a149c006b"
AUTHORED_BYTES = 22592
AUTHORED_SHA256 = "8c2c6428e8a4c5ca45d912cd22d76f344c6ab968c11031e6fd0417974b852bde"
OLD_AUTHORED_BYTES = 22521
OLD_AUTHORED_SHA256 = "3a3034504f72451b2d792424f556a32edd866f68accb8c1b74f1ab5327f34eff"
STRIPPED_SOURCE_BYTES = 234907
STRIPPED_SOURCE_SHA256 = "524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4"
PREOWNERSHIP_BYTES = 234978
PREOWNERSHIP_SHA256 = "c8b23f3939f2b866516dc4eaa7a869d22b4fd7e1ed24ef0bbeaf7f113fff4ba5"
HOOK = b"    CurrentAssemblyMetadata.installCleanup(meter,result,cleanupNanos);\n"
RETURN_ANCHOR = b"    return captureDetachedR7WorkPlan(meter,started,result,\n"
OWNERSHIP_INSERTION = b"    CurrentBodyOwnership.validate(meter,body);\n"
OWNERSHIP_ANCHOR = b"    /* One complete placeholder-body accounting pass before the Meter closes. */\n"

WORK_KEYS = (
    "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport", "wrapperGroupsBeforeExport",
    "maxGroupNanosBeforeExport", "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport",
    "setupUnits", "setupGroups", "setupCopiedEntries", "setupCopyChunks",
    "originalCaptureOwnerUnits", "originalCaptureWrapperUnits", "originalCaptureCallbackUnits",
    "originalCaptureDiagnosticUnits", "originalCaptureGroups", "originalCaptureCopiedEntries",
    "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits", "nullObserverUnits",
    "observeOwnerUnits", "observeWrapperUnits", "observeObserverUnits", "observerUnits",
    "meterUnits", "metadataUnits", "metadataGroups", "metadataCopiedEntries",
    "metadataCopyChunks", "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit",
)


def masked_java(text):
    """Mask comments/literals without changing offsets in pinned Java Source."""
    out = list(text)
    index = 0
    while index < len(text):
        if text.startswith("//", index):
            end = text.find("\n", index)
            if end < 0:
                end = len(text)
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            if end < 0:
                raise AssertionError("UNCLOSED_SOURCE_COMMENT")
            end += 2
        elif text[index] in ('"', "'"):
            quote = text[index]
            end = index + 1
            while end < len(text):
                if text[end] == "\\":
                    end += 2
                elif text[end] == quote:
                    end += 1
                    break
                else:
                    end += 1
            else:
                raise AssertionError("UNCLOSED_SOURCE_LITERAL")
        else:
            index += 1
            continue
        for cursor in range(index, end):
            if out[cursor] != "\n":
                out[cursor] = " "
        index = end
    return "".join(out)


def member(text, pattern):
    mask = masked_java(text)
    found = list(re.finditer(pattern, mask))
    if len(found) != 1:
        raise AssertionError("SOURCE_MEMBER_COUNT:" + pattern)
    match = found[0]
    brace = mask.find("{", match.end())
    if brace < 0:
        raise AssertionError("SOURCE_MEMBER_BRACE")
    depth = 1
    end = brace + 1
    while depth and end < len(mask):
        depth += (mask[end] == "{") - (mask[end] == "}")
        end += 1
    if depth:
        raise AssertionError("SOURCE_MEMBER_UNCLOSED")
    return text[match.start():end], text[brace + 1:end - 1]


def load_source(path):
    raw = Path(path).read_bytes()
    assert 0 < len(raw) <= 4194304
    tree = ast.parse(raw)
    allowed = {"__future__", "hashlib", "json", "re", "typing", "collections", "dataclasses"}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            assert all(item.name in allowed for item in node.names)
        if isinstance(node, ast.ImportFrom):
            assert node.level == 0 and node.module in allowed
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            assert node.func.id not in {"open", "exec", "eval", "compile", "__import__", "input"}
    module = types.ModuleType("_root_current_flat_work_generator")
    module.__file__ = str(path)
    sys.modules[module.__name__] = module
    exec(compile(raw, str(path), "exec"), module.__dict__)
    return module


class FlatWorkOverlayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generator = load_source(args.generator_source)
        cls.current_source = Path(args.study_source).read_bytes()
        cls.authored_members_source = Path(args.authored_source).read_bytes()

    def generate(self, **changes):
        inputs = {
            "current_source": self.current_source,
            "authored_members_source": self.authored_members_source,
        }
        inputs.update(changes)
        return self.generator.derive_current_flat_work(**inputs)

    def reject(self, expected_code=None, **changes):
        with self.assertRaises(self.generator.OverlayError) as raised:
            self.generate(**changes)
        if expected_code is not None:
            self.assertIn(expected_code, str(raised.exception))

    def expected_preownership_source(self):
        self.assertEqual(len(OWNERSHIP_INSERTION), 47)
        self.assertEqual(self.current_source.count(OWNERSHIP_INSERTION), 1)
        self.assertEqual(self.current_source.count(OWNERSHIP_ANCHOR), 1)
        insertion_at = self.current_source.index(OWNERSHIP_INSERTION)
        anchor_at = self.current_source.index(OWNERSHIP_ANCHOR)
        self.assertEqual(insertion_at + len(OWNERSHIP_INSERTION), anchor_at)
        stripped = self.current_source[:insertion_at] + self.current_source[anchor_at:]
        self.assertEqual(len(stripped), STRIPPED_SOURCE_BYTES)
        self.assertEqual(hashlib.sha256(stripped).hexdigest(), STRIPPED_SOURCE_SHA256)
        self.assertEqual(stripped.count(RETURN_ANCHOR), 1)
        offset = stripped.index(RETURN_ANCHOR)
        return stripped[:offset] + HOOK + stripped[offset:]

    def test_current_api_is_pure_deterministic_and_returns_bounded_bytes(self):
        before = (bytes(self.current_source), bytes(self.authored_members_source))
        first = self.generate()
        second = self.generate()
        self.assertEqual(first, second)
        self.assertEqual((self.current_source, self.authored_members_source), before)
        self.assertIs(type(first.study_source), bytes)
        self.assertIs(type(first.provenance_json), bytes)
        self.assertLessEqual(len(first.study_source), 4194304)
        self.assertTrue(first.study_source.endswith(b"\n"))
        self.assertNotIn(b"\r", first.study_source)
        provenance = json.loads(first.provenance_json)
        self.assertIs(type(provenance), dict)
        self.assertEqual(provenance["schema"], "TSJ_CURRENT_R7_AUTHORED_SOURCE_R1")
        self.assertEqual(provenance["status"], "SOURCE_ONLY")
        self.assertEqual(first.provenance_json,
                         (json.dumps(provenance, sort_keys=True, separators=(",", ":"),
                                     ensure_ascii=True) + "\n").encode("ascii"))
        self.assertTrue(first.provenance_json.isascii())
        self.assertTrue(first.provenance_json.endswith(b"\n"))
        self.assertNotIn(b"\r", first.provenance_json)
        self.assertNotRegex(first.provenance_json.decode("ascii"), r"(?:[A-Za-z]:\\|/(?:home|Users|tmp)/)")
        self.assertEqual(provenance["limits"], {
            "maxSourceBytes": 4194304,
            "maxOutputBytes": 4194304,
            "maxProvenanceBytes": 1048576,
        })
        current_record = provenance["inputs"]["currentStudy"]
        authored_record = provenance["inputs"]["authoredMembers"]
        self.assertEqual((current_record["role"], current_record["bytes"],
                          current_record["sha256"], current_record["encoding"],
                          current_record["lineEndings"]),
                         ("CURRENT_STUDY.java", STUDY_BYTES, STUDY_SHA256, "ASCII", "LF"))
        self.assertEqual((authored_record["role"], authored_record["bytes"],
                          authored_record["sha256"], authored_record["encoding"],
                          authored_record["lineEndings"]),
                         ("AuthoredFlatWorkMembers.java", AUTHORED_BYTES,
                          AUTHORED_SHA256, "ASCII", "LF"))
        transformation = provenance["sourceTransformation"]
        self.assertEqual(transformation["ownershipInsertion"]["lineBytes"], 47)
        self.assertEqual(transformation["ownershipInsertion"]["lineSha256"],
                         hashlib.sha256(OWNERSHIP_INSERTION).hexdigest())
        self.assertEqual(transformation["ownershipInsertion"]["sourceOffset"],
                         self.current_source.index(OWNERSHIP_INSERTION))
        self.assertTrue(transformation["ownershipInsertion"]["strippedBeforePureDerivation"])
        self.assertTrue(transformation["ownershipInsertion"]["retainedForBodyEmitter"])
        capture = transformation["captureMethod"]
        self.assertTrue(capture["coreMatchesUnmodifiedTemplate"])
        self.assertTrue(capture["replacementOnly"])
        self.assertEqual(capture["installerLineBytes"], 71)
        self.assertEqual(capture["installerLineSha256"], hashlib.sha256(HOOK).hexdigest())
        self.assertTrue(transformation["byteConservation"]["unchangedOutsideCaptureMethod"])
        self.assertEqual(transformation["byteConservation"]["unchangedMembers"], [
            "main_HOLD_body",
            "assemble_HOLD_body",
            "captureDetachedR7WorkPlan_API_and_core",
            "all_non_capture_methods_and_source_bytes",
        ])
        self.assertEqual(provenance["output"]["role"], "pre-ownership-current-study")
        self.assertEqual((provenance["output"]["bytes"], provenance["output"]["sha256"]),
                         (PREOWNERSHIP_BYTES, PREOWNERSHIP_SHA256))
        self.assertEqual(provenance["limitsAndGates"]["runtimeBehavior"], "NOT_CLAIMED")
        self.assertEqual(provenance["limitsAndGates"]["genuineOriginalNullObserve"], "UNSPENT_GATED")
        tree = ast.parse(Path(args.generator_source).read_bytes())
        functions = {node.name for node in tree.body if isinstance(node, ast.FunctionDef)}
        self.assertIn("derive_current_flat_work", functions)
        self.assertNotIn("derive_authored_flat_work", functions)
        api = next(node for node in tree.body
                   if isinstance(node, ast.FunctionDef) and node.name == "derive_current_flat_work")
        self.assertEqual([arg.arg for arg in api.args.kwonlyargs],
                         ["current_source", "authored_members_source"])
        self.assertEqual(api.args.args, [])
        self.assertIsNone(api.args.vararg)
        self.assertIsNone(api.args.kwarg)

    def test_current_input_and_authored_template_are_independently_pinned(self):
        self.assertEqual(len(self.current_source), STUDY_BYTES)
        self.assertEqual(hashlib.sha256(self.current_source).hexdigest(), STUDY_SHA256)
        self.assertEqual(len(self.authored_members_source), AUTHORED_BYTES)
        self.assertEqual(hashlib.sha256(self.authored_members_source).hexdigest(), AUTHORED_SHA256)
        self.assertEqual(len(HOOK), 71)
        self.assertEqual(self.authored_members_source.count(HOOK), 1)
        original_template = self.authored_members_source.replace(HOOK, b"", 1)
        self.assertEqual(len(original_template), OLD_AUTHORED_BYTES)
        self.assertEqual(hashlib.sha256(original_template).hexdigest(), OLD_AUTHORED_SHA256)
        self.assertEqual(self.generator.CURRENT_SOURCE_SHA256, STUDY_SHA256)
        self.assertEqual(self.generator.CURRENT_SOURCE_BYTES, STUDY_BYTES)
        self.assertEqual(self.generator.PRE_OWNERSHIP_SOURCE_BYTES, STRIPPED_SOURCE_BYTES)
        self.assertEqual(self.generator.PRE_OWNERSHIP_SOURCE_SHA256, STRIPPED_SOURCE_SHA256)
        self.assertEqual(self.generator.AUTHORED_MEMBERS_SHA256, AUTHORED_SHA256)
        self.assertEqual(self.generator.AUTHORED_MEMBERS_BYTES, AUTHORED_BYTES)
        template_text = self.authored_members_source.decode("ascii")
        _, capture_body = member(template_text, r"\bstatic\s+R7WorkPlan\s+captureR7WorkPlan\s*\(")
        self.assertEqual(capture_body.count(HOOK.decode("ascii")), 1)
        self.assertIn(HOOK.decode("ascii") + RETURN_ANCHOR.decode("ascii"), capture_body)
        self.assertLess(capture_body.index("CurrentAssemblyMetadata.installCleanup("),
                        capture_body.index("return captureDetachedR7WorkPlan("))
        with self.subTest(source="current"):
            self.reject("SOURCE_PIN", current_source=bytes([self.current_source[0] ^ 1])
                        + self.current_source[1:])
        with self.subTest(source="authored"):
            self.reject("FRAGMENT_PIN", authored_members_source=
                        bytes([self.authored_members_source[0] ^ 1]) + self.authored_members_source[1:])

    def test_exact_preownership_output_matches_independent_byte_delta(self):
        expected = self.expected_preownership_source()
        result = self.generate()
        self.assertEqual(len(expected), PREOWNERSHIP_BYTES)
        self.assertEqual(hashlib.sha256(expected).hexdigest(), PREOWNERSHIP_SHA256)
        self.assertEqual(result.study_source, expected)
        self.assertEqual(len(result.study_source), PREOWNERSHIP_BYTES)
        self.assertEqual(hashlib.sha256(result.study_source).hexdigest(), PREOWNERSHIP_SHA256)
        self.assertEqual(self.generator.EXPECTED_OUTPUT_BYTES, PREOWNERSHIP_BYTES)
        self.assertEqual(self.generator.EXPECTED_OUTPUT_SHA256, PREOWNERSHIP_SHA256)
        self.assertEqual(result.study_source.replace(HOOK, b"", 1),
                         self.expected_preownership_source().replace(HOOK, b"", 1))

    def test_wrong_input_types_and_invalid_or_oversize_sources_are_rejected(self):
        self.reject("SOURCE_TYPE", current_source="not bytes")
        self.reject("SOURCE_TYPE", current_source=bytearray(self.current_source))
        self.reject("FRAGMENT_TYPE_OR_SIZE", authored_members_source="not bytes")
        self.reject("FRAGMENT_TYPE_OR_SIZE", authored_members_source=bytearray(self.authored_members_source))
        self.reject("SOURCE_SIZE", current_source=b"")
        self.reject("SOURCE_SIZE", current_source=b"x" * 4194305)
        self.reject(current_source=b"\xef\xbb\xbf" + self.current_source)
        self.reject(current_source=b"\xff")
        self.reject(current_source=self.current_source.replace(b"\n", b"\r\n", 1))
        for value in (b"", b" " * 4194305, b"\xff",
                      b"\xef\xbb\xbf" + self.authored_members_source,
                      self.authored_members_source.replace(b"\n", b"\r\n", 1)):
            with self.subTest(kind=type(value).__name__, length=len(value)):
                self.reject(authored_members_source=value)

    def test_adopted_snapshot_and_meter_adapter_source_are_byte_preserved(self):
        output = self.generate().study_source.decode("utf-8")
        source = self.current_source.decode("utf-8")
        for pattern in (r"\bstatic\s+final\s+class\s+R5Snapshot\b",
                        r"\bstatic\s+final\s+class\s+SetupMeter\b",
                        r"\bstatic\s+final\s+class\s+Parent\b"):
            with self.subTest(pattern=pattern):
                self.assertEqual(member(output, pattern)[0], member(source, pattern)[0])

    def test_flat_key_order_matches_independent_34_field_contract(self):
        text = self.generate().study_source.decode("utf-8")
        _, body = member(text, r"\br5WorkKey\s*\(\s*int\s+\w+\s*\)")
        rows = re.findall(r'case\s+(\d+)\s*:\s*return\s*"([A-Za-z0-9]+)"', body)
        self.assertEqual([int(row[0]) for row in rows], list(range(34)))
        self.assertEqual(tuple(row[1] for row in rows), WORK_KEYS)

    def test_generic_ledger_is_retired_and_entry_exports_are_denied(self):
        text = self.generate().study_source.decode("utf-8")
        masked = masked_java(text)
        self.assertNotRegex(masked, r"\b(?:R7MeterPoint|R7MeterLedger)\b")
        _, main_body = member(text, r"\bpublic\s+static\s+void\s+main\s*\([^)]*\)\s*throws\s+Exception")
        self.assertRegex(masked_java(main_body), r"^\s*throw\s+new\s+")
        matches = list(re.finditer(r"\bstatic\s+R5ExportMetrics\s+exportR5\s*\(", masked))
        self.assertEqual(len(matches), 2)
        for match in matches:
            rest = text[match.start():]
            one = re.search(r"\{([^{}]*)\}", rest)
            self.assertIsNotNone(one)
            export_body = masked_java(one[1])
            self.assertRegex(export_body, r"^\s*throw\s+new\s+")
            self.assertNotRegex(export_body, r"\b(?:meter|args|result|output|pre|finalizer)\s*\.")

    def test_plan_inventory_is_44_fields_without_owner_aliases(self):
        text = self.generate().study_source.decode("utf-8")
        _, plan = member(text, r"\bstatic\s+final\s+class\s+R7WorkPlan\b")
        prefix = plan[:re.search(r"\bprivate\s+R7WorkPlan\s*\(", plan).start()]
        prefix = masked_java(prefix)
        fields = []
        for row in prefix.split(";"):
            row = row.strip()
            if not row:
                continue
            match = re.fullmatch(r"(?:private\s+)?(?:final\s+)?(SetupMeter|R5Snapshot|long|boolean|java\.util\.Map\s*<\s*String\s*,\s*Object\s*>)\s+(.+)", row, re.S)
            self.assertIsNotNone(match, row)
            names = [name.strip() for name in match[2].split(",")]
            self.assertTrue(all(re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", name) for name in names))
            fields.extend((match[1], name) for name in names)
        self.assertEqual(len(fields), 44)
        self.assertEqual(sum(kind == "long" for kind, _ in fields), 38)
        self.assertEqual(sum(kind == "boolean" for kind, _ in fields), 2)
        self.assertEqual(sum(kind not in ("long", "boolean") for kind, _ in fields), 4)

    def test_freeze_equation_operation_inventory_is_unchanged(self):
        text = self.generate().study_source.decode("utf-8")
        _, body = member(text, r"\bprivate\s+void\s+freezeAfterCapture\s*\(\s*\)\s*throws\s+Exception")
        mask = masked_java(body)
        self.assertEqual(len(re.findall(r"\br7Subtract\s*\(", mask)), 18)
        self.assertEqual(len(re.findall(r"\br5Add\s*\(", mask)), 4)
        self.assertEqual(len(re.findall(r"\bMath\s*\.\s*max\s*\(", mask)), 2)
        self.assertNotRegex(mask, r"\bmeter\s*\.|\b(?:field|r5Number|r5Flag)\s*\(")

    def test_finalizer_has_one_body_read_no_live_meter_or_map_allocation(self):
        text = self.generate().study_source.decode("utf-8")
        _, body = member(text, r"\bpublic\s+void\s+finalizeWork\s*\(\s*long\s+\w+\s*\)\s*throws\s+Exception")
        self.assertEqual(len(re.findall(r"\.\s*get\s*\(\s*\"work\"\s*\)", body)), 1)
        mask = masked_java(body)
        self.assertNotRegex(mask, r"\bmeter\s*\.|\b(?:field|r5Number|r5Flag)\s*\(")
        self.assertNotRegex(mask, r"new\s+(?:java\.util\.)?(?:LinkedHashMap|HashMap|TreeMap)\b")
        self.assertRegex(mask, r"\bfinalized\s*=\s*true\s*;")
        _, verify = member(text, r"\bpublic\s+void\s+verifyBound\s*\(")
        self.assertNotRegex(verify, r"\.\s*get\s*\(")

    def test_real_detachment_inspections_and_additions_are_source_bound(self):
        text = self.generate().study_source.decode("utf-8")
        _, body = member(text, r"\bstatic\s+R7WorkPlan\s+captureR7WorkPlan\s*\(")
        mask = masked_java(body)
        self.assertEqual(len(re.findall(r"\br7Read(?:Object|Long|MapLong)\s*\(", mask)), 40)
        self.assertEqual(len(re.findall(r"\br5Add\s*\(", mask)), 11)
        charged = re.search(r"\bmeter\s*\.\s*charge\s*\(\s*R7_CAPTURE_ADDITION_UNITS\s*\)", mask)
        self.assertIsNotNone(charged)
        self.assertLess(charged.start(), re.search(r"\br5Add\s*\(", mask).start())
        self.assertIn("captureDetachedR7WorkPlan", mask)

    def test_frozen_prepayment_constants_match_independent_contract(self):
        text = self.generate().study_source.decode("utf-8")
        expected = {"R7_CAPTURE_ADDITION_UNITS": 11, "R7_WORK_ENTRY_COPIES": 35,
                    "R7_PLAN_FIELD_COPIES": 26, "R7_CLOSED_SNAPSHOT_READS": 19,
                    "R7_WORK_SLOTS": 34, "R7_FREEZE_UNITS": 26,
                    "R7_FINALIZE_UNITS": 38, "R7_FINALIZE_ENTRY_COPIES": 34}
        for name, value in expected.items():
            with self.subTest(name=name):
                found = re.findall(r"\b" + name + r"\s*=\s*([^;]+);", masked_java(text))
                self.assertEqual(len(found), 1)

                def number(node):
                    if isinstance(node, ast.Constant) and type(node.value) is int:
                        return node.value
                    if isinstance(node, ast.Name) and node.id in expected:
                        return expected[node.id]
                    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
                        return number(node.left) + number(node.right)
                    raise AssertionError("NONCONSTANT_PREPAYMENT")

                self.assertEqual(number(ast.parse(found[0].strip(), mode="eval").body), value)

    def test_synthetic_parser_and_method_collision_oracles(self):
        parse = self.generator._assert_unique_declarations
        plan_class = "static final class R7WorkPlan implements R5WorkFinalizer { }\n"
        fragment = "static void accountResultTree() { }\n" + plan_class + "static final int NEW_LIMIT = 7;\n"
        retained = ("class Retained {\n"
                    "  static void existingOwner() { }\n"
                    "  static final int OLD_LIMIT = 3;\n"
                    "}\n")
        parse(fragment, retained)
        capture = "static R7WorkPlan captureR7WorkPlan() {\n    return null;\n}"
        span = self.generator._capture_span(capture, "SYNTHETIC_CAPTURE")
        self.assertEqual(capture[slice(*span)], capture)
        for source, code in (("static R7WorkPlan other() { }", "SYNTHETIC_MISSING"),
                             (capture + "\n" + capture, "SYNTHETIC_DUPLICATE")):
            with self.subTest(code=code):
                with self.assertRaisesRegex(self.generator.OverlayError, code):
                    self.generator._capture_span(source, code)
        with self.assertRaisesRegex(self.generator.OverlayError, "FRAGMENT_NAME_COLLISION"):
            parse("static void accountResultTree() { }\n" + plan_class,
                  "class Retained {\n  static void accountResultTree() { }\n}\n")

    def test_synthetic_type_and_constant_collisions_are_rejected(self):
        parse = self.generator._assert_unique_declarations
        plan_class = "static final class R7WorkPlan implements R5WorkFinalizer { }\n"
        cases = (
            (plan_class + "static final class R5Snapshot { }\n",
             "class Retained {\n  static final class R5Snapshot { }\n}\n"),
            (plan_class + "static final int R5_BLOCK = 2;\n",
             "class Retained {\n  static final int R5_BLOCK = 1;\n}\n"),
        )
        for fragment, retained in cases:
            with self.subTest(fragment=fragment.strip()):
                with self.assertRaisesRegex(self.generator.OverlayError, "FRAGMENT_NAME_COLLISION"):
                    parse(fragment, retained)


if __name__ == "__main__":
    unittest.main()
