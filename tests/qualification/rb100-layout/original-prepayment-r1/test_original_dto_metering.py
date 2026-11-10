"""Independent source-only contract tests for the original DTO meter transform.

This file checks byte pins and the Java source splice. It never executes Java
or invokes a successful originalDto payload.
"""
from __future__ import annotations

import argparse
import ast
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import types
import unittest


HERE = Path(__file__).resolve().parent
GENERATOR_PATH = HERE / "generate_original_dto_metering.py"
FRAGMENT_PATH = HERE / "OriginalDtoMetering.java.fragment"

SOURCE_NAME = "CohortTerminalTelemetryStudy.java"
FRAGMENT_NAME = "OriginalDtoMetering.java.fragment"
OUTPUT_NAME = "CohortTerminalTelemetryStudy.java"

SOURCE_BYTES = 235025
SOURCE_SHA256 = "48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00"
FRAGMENT_BYTES = 4870
FRAGMENT_SHA256 = "16173e6b40e11efb2c517a99a424fadd53cfeeb549a170682f5ed473a1cdf702"
OUTPUT_BYTES = 235950
OUTPUT_SHA256 = "f25010169adb2e4934a1303dc076cb948ba5f0c7160603a1fecd6b060e916541"

OLD_SPAN_BYTES = 3947
OLD_SPAN_SHA256 = "0435e8b21f22979554da6b33574a050b3a75fbae13c19abb084842c7721c3c68"
OLD_ANNOTATION_AND_METHOD = (
    b'    @SuppressWarnings("unchecked")\n'
    b'    static Map<String,Object> originalDto('
)
PAD_PROOF_DECLARATION = b"    static final class PadProof {"
METHOD_END = b"        return out;\n    }"
HELPER_DECLARATION = (
    b"    private static Object originalDtoMapRead("
    b"SetupMeter m,java.util.Map<?,?> source,String key) throws Exception {"
)

EXPECTED_FIELD_READS = Counter(
    [
        (b"original", b"captureData"),
        (b"original", b"legacyCapture"),
        (b"original", b"operation1"),
        (b"original", b"source"),
        (b"c", b"netIds"),
        (b"legacy", b"receiptOrdinal"),
        (b"legacy", b"lineageOrdinal"),
        (b"legacy", b"pass"),
        (b"c", b"policy"),
        (b"c", b"maximumExpansions"),
        (b"c", b"maximumVias"),
        (b"c", b"viaCost"),
        (b"c", b"policyTransitions"),
        (b"c", b"policyVias"),
        (b"c", b"branchStrategy"),
        (b"c", b"netStrategy"),
        (b"c", b"roleWidths"),
        (b"c", b"orderingStream"),
        (b"c", b"priorPromotedNetId"),
        (b"original", b"ownersReleased"),
        (b"original", b"contextRestored"),
        (b"original", b"cleanupNanos"),
    ]
)
EXPECTED_MAP_READS = Counter(
    [
        (b"op", b"receiptPath"),
        (b"op", b"receiptBytes"),
        (b"op", b"receiptSha256"),
        (b"op", b"requestIndex"),
        (b"op", b"selectedAttempt"),
        (b"op", b"rootSeed"),
        (b"op", b"placementSeed"),
        (b"op", b"routingSeed"),
        (b"src", b"templateGeometryBeforeSha256"),
        (b"src", b"templateGeometryAfterSha256"),
        (b"src", b"templateEffectsBeforeSha256"),
        (b"src", b"templateEffectsAfterSha256"),
        (b"src", b"templateTraceCount"),
        (b"src", b"templateHoleCount"),
    ]
)
EXPECTED_DIRECT_READS = Counter(
    [(b"args", b"runId"), (b"padProof", b"sha256"), (b"args", b"manifestSha")]
)

_METHOD_DECLARATION = re.compile(
    rb"(?m)^[ \t]*(?:(?:private|public|protected)[ \t]+)?"
    rb"static[ \t]+[^\r\n]*?\b([A-Za-z_$][A-Za-z0-9_$]*)[ \t]*\("
)
_READ_CALL = re.compile(
    rb'r7ReadObject\(m,([A-Za-z_$][A-Za-z0-9_$]*),"([A-Za-z0-9_$]+)"\)'
)
_MAP_READ_CALL = re.compile(
    rb'originalDtoMapRead\(m,(op|src),"([A-Za-z0-9_$]+)"\)'
)
_OLD_FIELD_CALL = re.compile(rb'field\(([^,]+),"([A-Za-z0-9_$]+)"\)')
_OLD_MAP_READ = re.compile(rb'(op|src)\.get\("([A-Za-z0-9_$]+)"\)')
_ALLOCATIONS = re.compile(
    rb"new java\.util\.(?:ArrayList|LinkedHashMap)<[^>]+>\("
)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _one_span(raw: bytes, start_token: bytes, end_token: bytes) -> tuple[int, int, bytes]:
    start = raw.find(start_token)
    if start < 0 or raw.find(start_token, start + 1) >= 0:
        raise AssertionError("expected one source start boundary")
    end = raw.find(end_token, start + len(start_token))
    if end < 0 or raw.find(end_token, end + 1) >= 0:
        raise AssertionError("expected one source end boundary")
    return start, end, raw[start:end]


def _source_and_fragment_parts(study_source: bytes, fragment: bytes):
    old_start, old_end, old_span = _one_span(
        study_source, OLD_ANNOTATION_AND_METHOD, PAD_PROOF_DECLARATION
    )
    old_method_end = old_span.find(METHOD_END)
    if old_method_end < 0 or old_span.find(METHOD_END, old_method_end + 1) >= 0:
        raise AssertionError("expected one originalDto return boundary")
    old_method_end += len(METHOD_END)
    old_method = old_span[:old_method_end]

    helper_start = fragment.find(HELPER_DECLARATION)
    if helper_start < 0 or fragment.find(HELPER_DECLARATION, helper_start + 1) >= 0:
        raise AssertionError("expected one originalDtoMapRead helper")
    new_method_end = fragment.find(METHOD_END)
    if new_method_end < 0 or fragment.find(METHOD_END, new_method_end + 1) >= 0:
        raise AssertionError("expected one metered originalDto return boundary")
    new_method_end += len(METHOD_END)
    if fragment[new_method_end:helper_start] != b"\n\n":
        raise AssertionError("unexpected method/helper separator")
    new_method = fragment[:new_method_end]
    helper = fragment[helper_start:]
    return (old_start, old_end, old_span, old_method,
            new_method, helper)


def _expected_provenance(study_source: bytes, fragment: bytes) -> dict:
    (old_start, old_end, old_span, old_method,
     new_method, helper) = _source_and_fragment_parts(study_source, fragment)
    prefix = study_source[:old_start]
    suffix = study_source[old_end:]
    return {
        "format": "TSJ_ORIGINAL_DTO_METERING_SOURCE_PROVENANCE_R1",
        "status": "PASS_SOURCE_TRANSFORM_ONLY",
        "inputs": [
            {
                "role": "current_r7_study_source",
                "bytes": SOURCE_BYTES,
                "sha256": SOURCE_SHA256,
            },
            {
                "role": "metered_fragment",
                "bytes": FRAGMENT_BYTES,
                "sha256": FRAGMENT_SHA256,
            },
        ],
        "methods": {
            "originalDto_before": {
                "bytes": len(old_method),
                "sha256": _sha256(old_method),
            },
            "originalDto_after": {
                "bytes": len(new_method),
                "sha256": _sha256(new_method),
            },
            "originalDtoMapRead": {
                "bytes": len(helper),
                "sha256": _sha256(helper),
            },
        },
        "conservedOutside": {
            "prefixBytes": len(prefix),
            "prefixSha256": _sha256(prefix),
            "suffixBytes": len(suffix),
            "suffixSha256": _sha256(suffix),
            "totalBytes": len(prefix) + len(suffix),
            "joinedSha256": _sha256(prefix + suffix),
        },
        "sourceOnlyPrice": {
            "logicalUnits": 154,
            "fieldReads": 25,
            "mapReads": 14,
            "containers": 8,
            "copySlots": 105,
            "copyChunks": 2,
            "finishCalls": 0,
        },
        "output": {
            "role": OUTPUT_NAME,
            "bytes": OUTPUT_BYTES,
            "sha256": OUTPUT_SHA256,
        },
    }


def _load_generator_from_same_directory(path: Path):
    """Compile exact sibling bytes into a private module; import no project data."""
    source_bytes = path.read_bytes()
    module_name = "_private_original_dto_metering_test_module"
    module = types.ModuleType(module_name)
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[module_name] = module
    code = compile(source_bytes, str(path), "exec", dont_inherit=True)
    exec(code, module.__dict__)
    return module, source_bytes


def _cli_tree(root: Path):
    source_root = root / "source-root"
    component_root = root / "component-root"
    output_root = root / "output-root"
    source_root.mkdir()
    component_root.mkdir()
    output_root.mkdir()
    return source_root, component_root, output_root


def _write_cli_inputs(source_root: Path, component_root: Path,
                      study_source: bytes, fragment: bytes):
    (source_root / SOURCE_NAME).write_bytes(study_source)
    (component_root / FRAGMENT_NAME).write_bytes(fragment)


def _run_cli(source_root: Path, component_root: Path, output_root: Path):
    return subprocess.run(
        [
            sys.executable,
            "-I",
            "-B",
            str(GENERATOR_PATH),
            "--source-root",
            str(source_root),
            "--component-root",
            str(component_root),
            "--output-root",
            str(output_root),
        ],
        cwd=str(output_root.parent),
        capture_output=True,
        check=False,
        timeout=30,
    )


def _is_reparse_entry(entry) -> bool:
    attributes = getattr(entry, "st_file_attributes", 0) or 0
    return stat.S_ISLNK(entry.st_mode) or bool(attributes & 0x400)


def _assert_within_owned_root(root: Path, path: Path, *, resolve: bool):
    owned_root = Path(root).resolve(strict=True)
    candidate = Path(path)
    candidate = candidate.resolve(strict=True) if resolve else Path(os.path.abspath(candidate))
    try:
        common = Path(os.path.commonpath([str(owned_root), str(candidate)]))
    except ValueError as exc:
        raise AssertionError("link path escaped its task-owned temporary directory") from exc
    if os.path.normcase(str(common)) != os.path.normcase(str(owned_root)):
        raise AssertionError("link path escaped its task-owned temporary directory")


def _create_directory_junction(junction: Path, target: Path):
    return subprocess.run(
        ["cmd.exe", "/d", "/c", "mklink", "/J", str(junction), str(target)],
        cwd=str(junction.parent),
        capture_output=True,
        text=True,
        check=False,
        timeout=15,
    )


class OriginalDtoMeteringContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        study_path = globals().get("STUDY_SOURCE_PATH")
        if study_path is None:
            raise RuntimeError("pass --study-source with the pinned Java Source file")
        cls.study_path = Path(study_path)
        cls.study_source = cls.study_path.read_bytes()
        cls.fragment = FRAGMENT_PATH.read_bytes()
        cls.generator, cls.generator_source = _load_generator_from_same_directory(
            GENERATOR_PATH
        )
        if len(cls.study_source) != SOURCE_BYTES or _sha256(cls.study_source) != SOURCE_SHA256:
            raise AssertionError("study source does not match the admitted pin")
        if len(cls.fragment) != FRAGMENT_BYTES or _sha256(cls.fragment) != FRAGMENT_SHA256:
            raise AssertionError("metered fragment does not match the reviewed pin")
        cls.result = cls.generator.derive_original_dto_metering(
            study_source=cls.study_source,
            metered_fragment_source=cls.fragment,
        )
        cls.parts = _source_and_fragment_parts(cls.study_source, cls.fragment)

    def _assert_api_error(self, expected_code, **kwargs):
        with self.assertRaises(self.generator.GenerationError) as caught:
            self.generator.derive_original_dto_metering(**kwargs)
        self.assertEqual(caught.exception.code, expected_code)
        self.assertEqual(str(caught.exception), expected_code)

    def _assert_cli_error(self, completed, code, output_root):
        self.assertEqual(completed.returncode, 2)
        self.assertEqual(completed.stdout, b"")
        self.assertEqual(completed.stderr, (code + os.linesep).encode("ascii"))
        self.assertEqual(list(output_root.iterdir()), [])

    def test_pinned_result_type_and_output_hash(self):
        self.assertTrue(isinstance(self.result, tuple))
        self.assertEqual(
            getattr(type(self.result), "_fields", None),
            ("study_source", "provenance_json"),
        )
        self.assertEqual(type(self.result).__name__, "GenerationResult")
        self.assertIs(type(self.result.study_source), bytes)
        self.assertIs(type(self.result.provenance_json), bytes)
        self.assertEqual(len(self.result.study_source), OUTPUT_BYTES)
        self.assertEqual(_sha256(self.result.study_source), OUTPUT_SHA256)

    def test_pure_repeatability_and_input_immutability(self):
        original_source = self.study_source
        original_fragment = self.fragment
        with tempfile.TemporaryDirectory(prefix="tsj-original-dto-pure-") as scratch:
            previous_cwd = os.getcwd()
            try:
                os.chdir(scratch)
                first = self.generator.derive_original_dto_metering(
                    study_source=original_source,
                    metered_fragment_source=original_fragment,
                )
                second = self.generator.derive_original_dto_metering(
                    study_source=original_source,
                    metered_fragment_source=original_fragment,
                )
            finally:
                os.chdir(previous_cwd)
            self.assertEqual(list(Path(scratch).iterdir()), [])
        self.assertEqual(first, second)
        self.assertEqual(first, self.result)
        self.assertEqual(original_source, self.study_source)
        self.assertEqual(original_fragment, self.fragment)
        self.assertEqual(self.study_path.read_bytes(), self.study_source)
        self.assertEqual(FRAGMENT_PATH.read_bytes(), self.fragment)

    def test_exact_source_splice_and_outside_conservation(self):
        old_start, old_end, old_span, _, _, _ = self.parts
        self.assertEqual(len(old_span), OLD_SPAN_BYTES)
        self.assertEqual(_sha256(old_span), OLD_SPAN_SHA256)
        self.assertTrue(self.fragment.endswith(b"    }"))
        self.assertFalse(self.fragment.endswith(b"\n"))

        replacement = self.fragment.rstrip(b"\n") + b"\n\n"
        expected = (
            self.study_source[:old_start]
            + replacement
            + self.study_source[old_end:]
        )
        self.assertEqual(self.result.study_source, expected)
        self.assertEqual(self.result.study_source[:old_start], self.study_source[:old_start])
        suffix_start = old_start + len(replacement)
        self.assertEqual(self.result.study_source[suffix_start:], self.study_source[old_end:])
        self.assertEqual(len(expected), OUTPUT_BYTES)
        self.assertEqual(_sha256(expected), OUTPUT_SHA256)

    def test_legacy_business_method_recovers_after_stripping_metering(self):
        _, _, old_span, old_method, new_method, _ = self.parts
        reads = list(_READ_CALL.finditer(new_method))
        self.assertEqual(len(reads), 25)
        self.assertEqual(Counter((m.group(1), m.group(2)) for m in reads),
                         EXPECTED_FIELD_READS + EXPECTED_DIRECT_READS)

        def restore_field(match):
            source = match.group(1)
            name = match.group(2)
            if (source, name) == (b"args", b"runId"):
                return b"args.runId"
            if (source, name) == (b"args", b"manifestSha"):
                return b"args.manifestSha"
            if (source, name) == (b"padProof", b"sha256"):
                return b"padProof.sha256"
            return b'field(' + source + b',"' + name + b'")'

        recovered = _READ_CALL.sub(restore_field, new_method)
        map_reads = list(_MAP_READ_CALL.finditer(recovered))
        self.assertEqual(len(map_reads), 14)
        self.assertEqual(Counter((m.group(1), m.group(2)) for m in map_reads),
                         EXPECTED_MAP_READS)
        recovered = _MAP_READ_CALL.sub(
            lambda m: m.group(1) + b'.get("' + m.group(2) + b'")',
            recovered,
        )
        recovered, allocation_charge_count = re.subn(
            rb"(?m)^        m\.charge\(1\);\n", b"", recovered
        )
        self.assertEqual(allocation_charge_count, 8)
        self.assertEqual(recovered + b"\n\n", old_span)
        self.assertTrue(old_span.startswith(old_method))
        self.assertEqual(old_span[len(old_method):], b"\n\n")

    def test_independent_read_census_and_preserved_charge_scope(self):
        _, _, old_span, old_method, new_method, helper = self.parts
        old_fields = Counter(
            (match.group(1).strip(), match.group(2))
            for match in _OLD_FIELD_CALL.finditer(old_method)
        )
        old_maps = Counter(
            (match.group(1), match.group(2))
            for match in _OLD_MAP_READ.finditer(old_method)
        )
        direct = Counter(
            (match.group(1), match.group(2))
            for match in re.finditer(
                rb"\b(args|padProof)\.(runId|manifestSha|sha256)\b", old_method
            )
        )
        self.assertEqual(old_fields, EXPECTED_FIELD_READS)
        self.assertEqual(old_maps, EXPECTED_MAP_READS)
        self.assertEqual(direct, EXPECTED_DIRECT_READS)
        self.assertEqual(len(old_fields) + len(old_maps) + len(direct), 39)
        self.assertEqual(old_method.count(b"m.charge(2);"), 1)
        self.assertEqual(old_method.count(b"m.charge(1);"), 0)
        self.assertEqual(old_method.count(b"m.copy(60);"), 1)
        self.assertEqual(old_method.count(b"m.copy(45);"), 1)

        self.assertEqual(new_method.count(b"r7ReadObject(m,"), 25)
        self.assertEqual(new_method.count(b"originalDtoMapRead(m,"), 14)
        self.assertEqual(new_method.count(b"m.charge(1);"), 8)
        self.assertEqual(new_method.count(b"m.charge(2);"), 1)
        self.assertEqual(new_method.count(b"m.copy(60);"), 1)
        self.assertEqual(new_method.count(b"m.copy(45);"), 1)
        self.assertEqual(new_method.count(b"new java.util.ArrayList<String>("), 1)
        self.assertEqual(new_method.count(b"new java.util.LinkedHashMap<String,Object>()"), 7)
        self.assertEqual(new_method.count(b"Integer.valueOf(246)"), 1)
        self.assertEqual(
            new_method.count(b'r7ReadObject(m,c,"policy").toString()'), 1
        )
        self.assertNotIn(b"m.finish(", new_method)
        self.assertEqual(helper.count(b"source.get(key)"), 1)
        self.assertEqual(helper.count(b"m.charge(1);"), 1)
        self.assertEqual(2 + 25 + 14 + 8 + 60 + 45, 154)

    def test_metered_fragment_inventory_lazy_guard_and_allocation_order(self):
        _, _, _, _, new_method, helper = self.parts
        declarations = [match.group(1) for match in _METHOD_DECLARATION.finditer(self.fragment)]
        self.assertEqual(declarations, [b"originalDto", b"originalDtoMapRead"])
        self.assertEqual(self.fragment.count(HELPER_DECLARATION), 1)
        self.assertNotIn(b"field(", new_method)
        self.assertNotIn(b"op.get(", new_method)
        self.assertNotIn(b"src.get(", new_method)

        guard = (
            b'if (captureId==null || !UUID.matcher(captureId).matches() || '
            b'captureId.equals(r7ReadObject(m,args,"runId")))'
        )
        self.assertEqual(new_method.count(guard), 1)
        self.assertLess(new_method.index(b"m.charge(2);"), new_method.index(guard))
        self.assertLess(guard.index(b"captureId==null"), guard.index(b"UUID.matcher"))
        self.assertLess(
            guard.index(b"UUID.matcher"),
            guard.index(b"r7ReadObject(m,args,"),
        )

        allocations = list(_ALLOCATIONS.finditer(new_method))
        self.assertEqual(len(allocations), 8)
        for allocation in allocations:
            line_start = new_method.rfind(b"\n", 0, allocation.start()) + 1
            prior_line_end = line_start - 1
            prior_line_start = new_method.rfind(b"\n", 0, prior_line_end) + 1
            prior_line = new_method[prior_line_start:prior_line_end].strip()
            self.assertEqual(prior_line, b"m.charge(1);")

        self.assertLess(new_method.index(b"m.copy(60);"), allocations[0].start())
        self.assertEqual(new_method[allocations[0].start():].count(b"new java.util.LinkedHashMap"), 7)
        first_map = next(m for m in allocations if b"LinkedHashMap" in m.group(0))
        self.assertLess(new_method.index(b"m.copy(45);"), first_map.start())

        null_guard = helper.index(b"if (source==null)")
        charged = helper.index(b"m.charge(1);")
        map_get = helper.index(b"return source.get(key);")
        self.assertLess(null_guard, charged)
        self.assertLess(charged, map_get)
        self.assertEqual(helper.count(b"source.get(key)"), 1)
        self.assertEqual(helper.count(b"containsKey"), 0)
        self.assertEqual(helper.count(b".put("), 0)

    def test_main_assemble_and_r7_reader_are_outside_the_splice(self):
        old_start, old_end, _, _, _, _ = self.parts
        replacement = self.fragment.rstrip(b"\n") + b"\n\n"
        expected = self.study_source[:old_start] + replacement + self.study_source[old_end:]
        self.assertEqual(expected, self.result.study_source)
        prefix = self.study_source[:old_start]
        suffix = self.study_source[old_end:]
        emitted_suffix = self.result.study_source[old_start + len(replacement):]
        self.assertEqual(self.result.study_source[:len(prefix)], prefix)
        self.assertEqual(emitted_suffix, suffix)

        # These production holds and the existing field reader remain byte-identical.
        for token in (
            b"static void main(",
            b"static Comparison assemble(",
            b"private static Object r7ReadObject(",
        ):
            self.assertEqual(self.study_source.count(token), 1)
            self.assertEqual(self.result.study_source.count(token), 1)
            position = self.study_source.index(token)
            if position < old_start:
                self.assertIn(token, prefix)
            else:
                self.assertIn(token, suffix)

    def test_canonical_provenance_matches_independent_expected_record(self):
        expected = _expected_provenance(self.study_source, self.fragment)
        expected_bytes = (
            json.dumps(expected, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
            + "\n"
        ).encode("ascii")
        self.assertEqual(self.result.provenance_json, expected_bytes)
        self.assertTrue(self.result.provenance_json.endswith(b"\n"))
        self.assertNotIn(b"\r", self.result.provenance_json)
        parsed = json.loads(self.result.provenance_json)
        self.assertEqual(parsed, expected)

    def test_generator_loader_uses_only_same_directory_source_bytes(self):
        self.assertEqual(Path(self.generator.__file__).resolve(), GENERATOR_PATH.resolve())
        tree = ast.parse(self.generator_source.decode("utf-8", "strict"))
        imports = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imports.update(alias.name.split(".", 1)[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom):
                if node.level:
                    imports.add("relative-import")
                elif node.module:
                    imports.add(node.module.split(".", 1)[0])
        self.assertFalse(imports & {"DATA", "data", "SourceReferences"})
        top_level_functions = {
            node.name for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
        }
        self.assertIn("derive_original_dto_metering", top_level_functions)
        self.assertIn("main", top_level_functions)
        dynamic_code_calls = [
            node.func.id
            for node in ast.walk(tree)
            if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Name)
            and node.func.id in {"eval", "exec", "execfile"}
        ]
        self.assertEqual(dynamic_code_calls, [])

    def test_api_rejects_wrong_types_sizes_and_mutated_pins_before_decode(self):
        self._assert_api_error(
            "SOURCE_TYPE",
            study_source=bytearray(self.study_source),
            metered_fragment_source=self.fragment,
        )
        self._assert_api_error(
            "FRAGMENT_TYPE",
            study_source=self.study_source,
            metered_fragment_source=memoryview(self.fragment),
        )
        self._assert_api_error(
            "SOURCE_SIZE",
            study_source=b"x" * (4 * 1024 * 1024 + 1),
            metered_fragment_source=self.fragment,
        )
        self._assert_api_error(
            "FRAGMENT_SIZE",
            study_source=self.study_source,
            metered_fragment_source=b"x" * (64 * 1024 + 1),
        )

        invalid_utf8_source = b"\xff" + self.study_source[1:]
        self._assert_api_error(
            "SOURCE_SHA256",
            study_source=invalid_utf8_source,
            metered_fragment_source=self.fragment,
        )
        invalid_utf8_fragment = b"\xff" + self.fragment[1:]
        self._assert_api_error(
            "FRAGMENT_SHA256",
            study_source=self.study_source,
            metered_fragment_source=invalid_utf8_fragment,
        )

    def test_cli_positive_writes_pinned_output_before_provenance(self):
        with tempfile.TemporaryDirectory(prefix="tsj-original-dto-cli-") as temporary:
            root = Path(temporary)
            source_root, component_root, output_root = _cli_tree(root)
            _write_cli_inputs(source_root, component_root, self.study_source, self.fragment)
            self.assertEqual((source_root / SOURCE_NAME).resolve().parent, source_root.resolve())
            self.assertEqual((component_root / FRAGMENT_NAME).resolve().parent,
                             component_root.resolve())
            before_source = (source_root / SOURCE_NAME).read_bytes()
            before_fragment = (component_root / FRAGMENT_NAME).read_bytes()

            completed = _run_cli(source_root, component_root, output_root)

            self.assertEqual(completed.returncode, 0)
            self.assertEqual(completed.stderr, b"")
            self.assertEqual(completed.stdout, self.result.provenance_json)
            output = output_root / OUTPUT_NAME
            self.assertTrue(output.is_file())
            self.assertEqual(output.read_bytes(), self.result.study_source)
            self.assertEqual(_sha256(output.read_bytes()), OUTPUT_SHA256)
            self.assertEqual([item.name for item in output_root.iterdir()], [OUTPUT_NAME])
            self.assertEqual((source_root / SOURCE_NAME).read_bytes(), before_source)
            self.assertEqual((component_root / FRAGMENT_NAME).read_bytes(), before_fragment)

    def test_cli_rejects_appended_source_or_fragment_without_output(self):
        for role in ("source", "fragment"):
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                prefix="tsj-original-dto-append-"
            ) as temporary:
                root = Path(temporary)
                source_root, component_root, output_root = _cli_tree(root)
                source_bytes = self.study_source + b"\n" if role == "source" else self.study_source
                fragment_bytes = self.fragment + b"\n" if role == "fragment" else self.fragment
                _write_cli_inputs(source_root, component_root, source_bytes, fragment_bytes)
                completed = _run_cli(source_root, component_root, output_root)
                self._assert_cli_error(
                    completed,
                    "SOURCE_SHA256" if role == "source" else "FRAGMENT_SHA256",
                    output_root,
                )

    def test_cli_uses_fixed_input_leaf_names_and_rejects_missing_leafs(self):
        for role in ("source", "fragment"):
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                prefix="tsj-original-dto-missing-"
            ) as temporary:
                root = Path(temporary)
                source_root, component_root, output_root = _cli_tree(root)
                if role == "source":
                    (component_root / FRAGMENT_NAME).write_bytes(self.fragment)
                    expected_code = "SOURCE_MISSING"
                else:
                    (source_root / SOURCE_NAME).write_bytes(self.study_source)
                    expected_code = "FRAGMENT_MISSING"
                completed = _run_cli(source_root, component_root, output_root)
                self._assert_cli_error(completed, expected_code, output_root)

    def test_cli_rejects_hardlinked_input(self):
        with tempfile.TemporaryDirectory(prefix="tsj-original-dto-hardlink-") as temporary:
            root = Path(temporary)
            source_root, component_root, output_root = _cli_tree(root)
            target = root / "source-target.bin"
            target.write_bytes(self.study_source)
            link_path = source_root / SOURCE_NAME
            _assert_within_owned_root(root, target, resolve=True)
            _assert_within_owned_root(root, link_path, resolve=False)
            try:
                os.link(target, link_path)
            except (OSError, NotImplementedError) as exc:
                self.fail(
                    "required hard-link canary could not be created: "
                    + type(exc).__name__
                    + ": "
                    + str(exc)
                )
            (component_root / FRAGMENT_NAME).write_bytes(self.fragment)
            completed = _run_cli(source_root, component_root, output_root)
            self._assert_cli_error(completed, "SOURCE_LINKED", output_root)

    def test_cli_rejects_symlinked_input_or_windows_directory_junction(self):
        with tempfile.TemporaryDirectory(prefix="tsj-original-dto-symlink-") as temporary:
            root = Path(temporary)
            source_root, component_root, output_root = _cli_tree(root)
            link_path = source_root / SOURCE_NAME
            target_file = root / "source-target.bin"
            target_file.write_bytes(self.study_source)
            _assert_within_owned_root(root, link_path, resolve=False)
            _assert_within_owned_root(root, target_file, resolve=True)
            file_symlink_created = False
            junction_created = False
            junction_target = None
            (component_root / FRAGMENT_NAME).write_bytes(self.fragment)
            try:
                try:
                    os.symlink(target_file, link_path)
                    file_symlink_created = True
                    _assert_within_owned_root(root, link_path, resolve=True)
                except (OSError, NotImplementedError) as exc:
                    if os.name != "nt":
                        self.fail(
                            "required symbolic-link canary could not be created: "
                            + type(exc).__name__
                            + ": "
                            + str(exc)
                        )
                    if os.path.lexists(link_path):
                        entry = os.lstat(link_path)
                        self.assertTrue(_is_reparse_entry(entry))
                        _assert_within_owned_root(root, link_path, resolve=False)
                        os.unlink(link_path)

                    junction_target = root / "source-target-root"
                    junction_target.mkdir()
                    junction_file = junction_target / SOURCE_NAME
                    junction_file.write_bytes(self.study_source)
                    _assert_within_owned_root(root, junction_target, resolve=True)
                    source_root.rmdir()
                    try:
                        junction_result = _create_directory_junction(source_root, junction_target)
                    except (OSError, subprocess.SubprocessError) as junction_error:
                        if os.path.lexists(source_root):
                            junction_entry = os.lstat(source_root)
                            junction_created = _is_reparse_entry(junction_entry)
                        self.fail(
                            "required Windows directory-junction canary could not be created: "
                            + type(junction_error).__name__
                            + ": "
                            + str(junction_error)
                        )
                    if os.path.lexists(source_root):
                        junction_entry = os.lstat(source_root)
                        junction_created = _is_reparse_entry(junction_entry)
                    self.assertEqual(
                        junction_result.returncode,
                        0,
                        "required Windows directory-junction canary failed: "
                        + junction_result.stdout
                        + junction_result.stderr,
                    )
                    junction_entry = os.lstat(source_root)
                    self.assertTrue(_is_reparse_entry(junction_entry))
                    _assert_within_owned_root(root, source_root, resolve=False)
                    _assert_within_owned_root(root, source_root, resolve=True)

                completed = _run_cli(source_root, component_root, output_root)
                self._assert_cli_error(completed, "SOURCE_REPARSE", output_root)
            finally:
                if file_symlink_created:
                    link_entry = os.lstat(link_path)
                    self.assertTrue(_is_reparse_entry(link_entry))
                    _assert_within_owned_root(root, link_path, resolve=False)
                    _assert_within_owned_root(root, link_path, resolve=True)
                    os.unlink(link_path)
                    self.assertTrue(target_file.is_file())
                if junction_created:
                    junction_entry = os.lstat(source_root)
                    self.assertTrue(_is_reparse_entry(junction_entry))
                    _assert_within_owned_root(root, source_root, resolve=False)
                    _assert_within_owned_root(root, source_root, resolve=True)
                    os.rmdir(source_root)
                    self.assertTrue((junction_target / SOURCE_NAME).is_file())

    def test_cli_existing_output_is_not_overwritten(self):
        with tempfile.TemporaryDirectory(prefix="tsj-original-dto-exclusive-") as temporary:
            root = Path(temporary)
            source_root, component_root, output_root = _cli_tree(root)
            _write_cli_inputs(source_root, component_root, self.study_source, self.fragment)
            output = output_root / OUTPUT_NAME
            sentinel = b"preexisting output must remain unchanged\n"
            output.write_bytes(sentinel)
            completed = _run_cli(source_root, component_root, output_root)
            self.assertEqual(completed.returncode, 2)
            self.assertEqual(completed.stdout, b"")
            self.assertEqual(completed.stderr, ("OUTPUT_EXISTS" + os.linesep).encode("ascii"))
            self.assertEqual(output.read_bytes(), sentinel)
            self.assertEqual([item.name for item in output_root.iterdir()], [OUTPUT_NAME])


def _parse_test_arguments(argv):
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--study-source", required=True)
    return parser.parse_known_args(argv)


if __name__ == "__main__":
    _arguments, _unittest_arguments = _parse_test_arguments(sys.argv[1:])
    STUDY_SOURCE_PATH = Path(_arguments.study_source)
    unittest.main(argv=[sys.argv[0], *_unittest_arguments], verbosity=2)
