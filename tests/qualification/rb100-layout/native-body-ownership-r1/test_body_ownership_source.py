"""Current Source-only checks for body ownership and its four-root emitter."""

import argparse
from contextlib import contextmanager, redirect_stderr, redirect_stdout
import ast
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
from unittest import mock
import unittest


ROOT = Path(__file__).resolve().parent
_source_args = argparse.ArgumentParser(add_help=False)
_source_args.add_argument("--study-source", required=True)
_source_args.add_argument("--authored-root", required=True)
_source_args.add_argument("--helper-source", default=str(ROOT / "CurrentBodyOwnership.java"))
_source_options, _unittest_args = _source_args.parse_known_args()
sys.argv = [sys.argv[0], *_unittest_args]
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize

AUTHORED_ROOT = Path(_source_options.authored_root)
STUDY = Path(_source_options.study_source).read_bytes()
HELPER = Path(_source_options.helper_source).read_bytes()
TEMPLATE_NAME = "AuthoredFlatWorkMembers.java"
MODULE_NAME = "generate_authored_flat_work_overlay.py"
TEMPLATE = (AUTHORED_ROOT / TEMPLATE_NAME).read_bytes()
PURE_MODULE = (AUTHORED_ROOT / MODULE_NAME).read_bytes()
EMITTER_PATH = ROOT / "generate_body_ownership.py"
GENERATOR_TEXT = EMITTER_PATH.read_text(encoding="utf-8")
HELPER_TEXT = HELPER.decode("utf-8")

STUDY_BYTES = 234954
STUDY_SHA256 = "9ac45b374c8aeee7a912a4e1452435b71b278aeae0a288e5a75dd82a149c006b"
HELPER_BYTES = 3860
HELPER_SHA256 = "8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832"
TEMPLATE_BYTES = 22592
TEMPLATE_SHA256 = "8c2c6428e8a4c5ca45d912cd22d76f344c6ab968c11031e6fd0417974b852bde"
PURE_MODULE_BYTES = 18595
PURE_MODULE_SHA256 = "92ce7dc27bd08d395a1bc8155e4795d579885a8dbd26c765c2ee0219051e280c"
PREOWNERSHIP_BYTES = 234978
PREOWNERSHIP_SHA256 = "c8b23f3939f2b866516dc4eaa7a869d22b4fd7e1ed24ef0bbeaf7f113fff4ba5"
STRIPPED_SOURCE_BYTES = 234907
STRIPPED_SOURCE_SHA256 = "524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4"
FINAL_BYTES = 235025
FINAL_SHA256 = "48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00"
FLAT_HOOK = b"    CurrentAssemblyMetadata.installCleanup(meter,result,cleanupNanos);\n"
FLAT_RETURN = b"    return captureDetachedR7WorkPlan(meter,started,result,\n"
OWNERSHIP_ANCHOR = b"    /* One complete placeholder-body accounting pass before the Meter closes. */\n"
OWNERSHIP_INSERTION = b"    CurrentBodyOwnership.validate(meter,body);\n"
SCRATCH_PREFIX = "tsj-current-r7-body-tests-"
REPARSE_POINT = 0x400

SPEC = importlib.util.spec_from_file_location("generate_body_ownership", EMITTER_PATH)
EMITTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EMITTER)


def java_method_body(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for offset in range(opening, len(source)):
        if source[offset] == "{":
            depth += 1
        elif source[offset] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:offset]
    raise AssertionError("unterminated Java method: " + signature)


def _is_reparse(metadata):
    return bool(getattr(metadata, "st_file_attributes", 0) & REPARSE_POINT)


def _assert_owned_scratch(root):
    root = Path(root).absolute()
    temp_parent = Path(tempfile.gettempdir()).resolve(strict=True)
    if not root.name.startswith(SCRATCH_PREFIX) or root.parent != temp_parent:
        raise AssertionError("TASK_TEMP_OWNERSHIP")
    resolved_root = root.resolve(strict=True)
    metadata = os.lstat(root)
    if resolved_root != root or stat.S_ISLNK(metadata.st_mode) or _is_reparse(metadata):
        raise AssertionError("TASK_TEMP_REPARSE")
    for directory, directories, files in os.walk(root, followlinks=False):
        current = Path(directory).resolve(strict=True)
        if os.path.commonpath((str(resolved_root), str(current))) != str(resolved_root):
            raise AssertionError("TASK_TEMP_CONTAINMENT")
        for name in directories + files:
            child = Path(directory) / name
            child_meta = os.lstat(child)
            if stat.S_ISLNK(child_meta.st_mode) or _is_reparse(child_meta):
                raise AssertionError("TASK_TEMP_CHILD_REPARSE")
            resolved_child = child.resolve(strict=True)
            if os.path.commonpath((str(resolved_root), str(resolved_child))) != str(resolved_root):
                raise AssertionError("TASK_TEMP_CHILD_CONTAINMENT")


@contextmanager
def owned_scratch():
    temp_parent = Path(tempfile.gettempdir()).resolve(strict=True)
    root = Path(tempfile.mkdtemp(prefix=SCRATCH_PREFIX, dir=str(temp_parent))).absolute()
    _assert_owned_scratch(root)
    try:
        yield root
    finally:
        _assert_owned_scratch(root)
        shutil.rmtree(root)


def expected_preownership():
    if (STUDY.count(OWNERSHIP_INSERTION) != 1
            or STUDY.count(OWNERSHIP_ANCHOR) != 1):
        raise AssertionError("EXPECTED_CURRENT_OWNERSHIP_INSERTION")
    insertion_at = STUDY.index(OWNERSHIP_INSERTION)
    anchor_at = STUDY.index(OWNERSHIP_ANCHOR)
    if insertion_at + len(OWNERSHIP_INSERTION) != anchor_at:
        raise AssertionError("EXPECTED_CURRENT_OWNERSHIP_PLACEMENT")
    stripped = STUDY[:insertion_at] + STUDY[anchor_at:]
    if (len(stripped) != STRIPPED_SOURCE_BYTES
            or hashlib.sha256(stripped).hexdigest() != STRIPPED_SOURCE_SHA256):
        raise AssertionError("EXPECTED_STRIPPED_SOURCE_PIN")
    if stripped.count(FLAT_RETURN) != 1:
        raise AssertionError("EXPECTED_FLAT_RETURN_ANCHOR")
    offset = stripped.index(FLAT_RETURN)
    return stripped[:offset] + FLAT_HOOK + stripped[offset:]


def expected_final_source():
    source = expected_preownership()
    if source.count(OWNERSHIP_ANCHOR) != 1:
        raise AssertionError("EXPECTED_OWNERSHIP_ANCHOR")
    offset = source.index(OWNERSHIP_ANCHOR)
    return source[:offset] + OWNERSHIP_INSERTION + source[offset:]


def write_emit_roots(root, *, study=STUDY, helper=HELPER, template=TEMPLATE,
                     module=PURE_MODULE):
    source_root = root / "source"
    component_root = root / "component"
    authored_root = root / "authored"
    output_root = root / "output"
    for directory in (source_root, component_root, authored_root, output_root):
        directory.mkdir()
    (source_root / "CURRENT_STUDY.java").write_bytes(study)
    (component_root / "CurrentBodyOwnership.java").write_bytes(helper)
    (authored_root / TEMPLATE_NAME).write_bytes(template)
    (authored_root / MODULE_NAME).write_bytes(module)
    return source_root, component_root, authored_root, output_root


class BodyOwnershipSourceTests(unittest.TestCase):
    def test_current_source_helper_and_template_match_independent_pins(self):
        self.assertEqual(len(STUDY), STUDY_BYTES)
        self.assertEqual(hashlib.sha256(STUDY).hexdigest(), STUDY_SHA256)
        self.assertEqual(len(HELPER), HELPER_BYTES)
        self.assertEqual(hashlib.sha256(HELPER).hexdigest(), HELPER_SHA256)
        self.assertEqual(len(TEMPLATE), TEMPLATE_BYTES)
        self.assertEqual(hashlib.sha256(TEMPLATE).hexdigest(), TEMPLATE_SHA256)
        self.assertEqual(len(PURE_MODULE), PURE_MODULE_BYTES)
        self.assertEqual(hashlib.sha256(PURE_MODULE).hexdigest(), PURE_MODULE_SHA256)
        self.assertEqual(EMITTER.PINNED_STUDY_SHA256, STUDY_SHA256)
        self.assertEqual(EMITTER.PINNED_HELPER_SHA256, HELPER_SHA256)
        self.assertEqual(EMITTER.PINNED_AUTHORED_MEMBERS_SHA256, TEMPLATE_SHA256)
        self.assertEqual(EMITTER.PINNED_AUTHORED_MEMBERS_BYTES, TEMPLATE_BYTES)
        self.assertEqual(EMITTER.PINNED_AUTHORED_MODULE_SHA256, PURE_MODULE_SHA256)
        self.assertEqual(EMITTER.PINNED_AUTHORED_MODULE_BYTES, PURE_MODULE_BYTES)

    def test_unique_ownership_anchor_insertion_preserves_every_other_source_byte(self):
        preownership = expected_preownership()
        self.assertEqual(len(preownership), PREOWNERSHIP_BYTES)
        self.assertEqual(hashlib.sha256(preownership).hexdigest(), PREOWNERSHIP_SHA256)
        self.assertEqual(preownership.count(OWNERSHIP_ANCHOR), 1)
        generated = EMITTER.insert_at_unique_anchor(preownership)
        offset = preownership.index(OWNERSHIP_ANCHOR)
        expected = preownership[:offset] + OWNERSHIP_INSERTION + preownership[offset:]
        self.assertEqual(generated, expected)
        self.assertEqual(generated.replace(OWNERSHIP_INSERTION, b"", 1), preownership)
        self.assertEqual(len(generated), FINAL_BYTES)
        self.assertEqual(hashlib.sha256(generated).hexdigest(), FINAL_SHA256)
        with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_ANCHOR"):
            EMITTER.insert_at_unique_anchor(preownership.replace(OWNERSHIP_ANCHOR, b"", 1))
        with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_ANCHOR"):
            EMITTER.insert_at_unique_anchor(
                preownership.replace(OWNERSHIP_ANCHOR, OWNERSHIP_ANCHOR + OWNERSHIP_ANCHOR, 1)
            )

    def test_emitter_ast_requires_four_roots_and_authenticates_module_bytes(self):
        tree = ast.parse(GENERATOR_TEXT)
        functions = {node.name for node in tree.body if isinstance(node, ast.FunctionDef)}
        self.assertTrue({"insert_at_unique_anchor", "emit", "main"}.issubset(functions))
        flags = {
            node.args[0].value
            for node in ast.walk(tree)
            if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Attribute)
            and node.func.attr == "add_argument"
            and node.args
            and isinstance(node.args[0], ast.Constant)
        }
        self.assertEqual(flags, {"--source-root", "--component-root", "--authored-root", "--output-root"})
        imports = {
            alias.name.split(".", 1)[0]
            for node in ast.walk(tree)
            if isinstance(node, (ast.Import, ast.ImportFrom))
            for alias in node.names
        }
        self.assertFalse({"subprocess", "glob", "importlib"} & imports)
        self.assertIn('OUTPUT_NAME = "CohortTerminalTelemetryStudy.java"', GENERATOR_TEXT)
        self.assertNotIn("W_STUDY.java", GENERATOR_TEXT)
        self.assertNotIn("R_STUDY.java", GENERATOR_TEXT)
        self.assertNotIn("Q_AUTHORED_STUDY.java", GENERATOR_TEXT)
        emit_node = next(node for node in tree.body
                         if isinstance(node, ast.FunctionDef) and node.name == "emit")
        root_reads = []
        for node in ast.walk(emit_node):
            if (isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                    and node.func.id == "_read_root_file" and len(node.args) >= 2):
                self.assertIsInstance(node.args[0], ast.Name)
                self.assertIsInstance(node.args[1], ast.Name)
                root_reads.append((node.args[0].id, node.args[1].id))
        self.assertCountEqual(root_reads, [
            ("source_root", "STUDY_NAME"),
            ("component_root", "HELPER_NAME"),
            ("authored_root", "AUTHORED_MODULE_NAME"),
            ("authored_root", "AUTHORED_MEMBERS_NAME"),
        ])

    def test_java_api_and_stable_failure_codes(self):
        self.assertIn("public final class CurrentBodyOwnership", HELPER_TEXT)
        self.assertIn(
            "public static void validate(CohortTerminalTelemetryStudy.SetupMeter meter,",
            HELPER_TEXT,
        )
        for code in (
            "BODY_OWNERSHIP_ARGUMENT",
            "BODY_OWNERSHIP_DEPTH",
            "BODY_OWNERSHIP_VISITS",
            "BODY_OWNERSHIP_VALUE",
            "BODY_OWNERSHIP_KEY",
            "BODY_OWNERSHIP_ALIAS",
        ):
            self.assertIn('"' + code + '"', HELPER_TEXT)
        self.assertIn("value instanceof Boolean", HELPER_TEXT)
        self.assertIn("value instanceof Integer", HELPER_TEXT)
        self.assertIn("value instanceof Long", HELPER_TEXT)
        self.assertIn("value instanceof String", HELPER_TEXT)
        self.assertIn("value instanceof Map", HELPER_TEXT)
        self.assertIn("value instanceof List", HELPER_TEXT)

    def test_null_meter_precedes_two_unit_prepayment_and_body_traversal(self):
        validate = java_method_body(HELPER_TEXT, "public static void validate(")
        null_check = validate.index("if (meter == null)")
        prepayment = validate.index("meter.charge(2)")
        allocation = validate.index("new Traversal(meter)")
        traversal = validate.index("traversal.visit(body, 0, false)")
        self.assertLess(null_check, prepayment)
        self.assertLess(prepayment, allocation)
        self.assertLess(allocation, traversal)

    def test_each_key_or_value_is_charged_before_depth_visit_and_kind_checks(self):
        visit = java_method_body(HELPER_TEXT, "void visit(Object value, int depth, boolean mapKey)")
        depth_charge = visit.index("meter.charge(1)")
        depth_check = visit.index("depth > MAX_DEPTH")
        visit_charge = visit.index("meter.charge(1)", depth_charge + 1)
        visit_check = visit.index("visits >= MAX_VISITS")
        kind_charge = visit.index("meter.charge(1)", visit_charge + 1)
        kind_check = visit.index("if (mapKey)")
        self.assertLess(depth_charge, depth_check)
        self.assertLess(depth_check, visit_charge)
        self.assertLess(visit_charge, visit_check)
        self.assertLess(visit_check, kind_charge)
        self.assertLess(kind_charge, kind_check)
        self.assertIn("MAX_DEPTH = 64", HELPER_TEXT)
        self.assertIn("MAX_VISITS = 1000000", HELPER_TEXT)
        self.assertIn("visits++", visit)

    def test_container_registry_rejects_any_repeated_map_or_list_identity(self):
        self.assertEqual(HELPER_TEXT.count("new IdentityHashMap<"), 1)
        register = java_method_body(HELPER_TEXT, "private void register(Object container)")
        ordered = (
            "meter.copy(1)",
            "meter.charge(1)",
            "containers.put(container, Boolean.TRUE)",
            "meter.charge(1)",
            "previous != null",
            "BODY_OWNERSHIP_ALIAS",
        )

        def sequential_positions(body, fragments):
            cursor = 0
            positions = []
            for fragment in fragments:
                position = body.index(fragment, cursor)
                positions.append(position)
                cursor = position + len(fragment)
            return positions

        positions = sequential_positions(register, ordered)
        self.assertEqual(positions, sorted(positions))
        with self.assertRaises(ValueError):
            sequential_positions(register.replace("meter.charge(1);", "", 1), ordered)
        self.assertIn("IdentityHashMap<Object, Boolean>", HELPER_TEXT)
        self.assertNotIn("containers.remove", HELPER_TEXT)
        self.assertNotIn("containers.clear", HELPER_TEXT)

    def test_map_and_list_observations_have_preceding_meter_charges(self):
        visit_map = java_method_body(HELPER_TEXT, "private void visitMap(")
        visit_list = java_method_body(HELPER_TEXT, "private void visitList(")
        map_order = (
            "meter.charge(1)", "map.entrySet()",
            "meter.charge(1)", "entries.iterator()",
            "meter.charge(1)", "iterator.hasNext()",
            "meter.charge(1)", "iterator.next()",
            "meter.charge(1)", "entry.getKey()",
            "visit(key, depth + 1, true)",
            "meter.charge(1)", "entry.getValue()",
            "visit(child, depth + 1, false)",
        )
        list_order = (
            "meter.charge(1)", "list.iterator()",
            "meter.charge(1)", "iterator.hasNext()",
            "meter.charge(1)", "iterator.next()",
            "visit(child, depth + 1, false)",
        )

        def sequential_positions(body, fragments):
            cursor = 0
            positions = []
            for fragment in fragments:
                position = body.index(fragment, cursor)
                positions.append(position)
                cursor = position + len(fragment)
            return positions

        map_positions = sequential_positions(visit_map, map_order)
        list_positions = sequential_positions(visit_list, list_order)
        self.assertEqual(map_positions, sorted(map_positions))
        self.assertEqual(list_positions, sorted(list_positions))
        for body, ordered in ((visit_map, map_order), (visit_list, list_order)):
            with self.assertRaises(ValueError):
                sequential_positions(body.replace("meter.charge(1);", "", 1), ordered)
        for body in (visit_map, visit_list):
            self.assertNotIn(".size()", body)
            self.assertNotIn(".get(", body)

    def test_independent_census_examples_match_the_frozen_contract(self):
        def census(maps, lists, map_entries, list_items):
            visits = 1 + 2 * map_entries + list_items
            units = 2 + 3 * visits + 6 * maps + 5 * lists + 4 * map_entries + 2 * list_items
            copies = maps + lists
            return units, copies, copies

        self.assertEqual(census(0, 0, 0, 0), (5, 0, 0))
        self.assertEqual(census(1, 0, 0, 0), (11, 1, 1))
        self.assertEqual(census(0, 1, 0, 0), (10, 1, 1))
        self.assertEqual(census(1, 0, 1, 0), (21, 1, 1))
        self.assertEqual(census(0, 1, 0, 3), (25, 1, 1))
        self.assertEqual(census(1, 1, 1, 2), (36, 2, 2))

    def test_emit_is_exclusive_and_all_pinned_inputs_fail_before_output(self):
        with owned_scratch() as root:
            source_root, component_root, authored_root, output_root = write_emit_roots(root)
            result = EMITTER.emit(source_root, component_root, authored_root, output_root)
            destination = result.destination
            self.assertEqual(destination.name, "CohortTerminalTelemetryStudy.java")
            self.assertEqual(destination.read_bytes(), expected_final_source())
            self.assertIs(type(result.provenance_json), bytes)
            self.assertLessEqual(len(result.provenance_json), 1048576)
            self.assertEqual(json.loads(result.provenance_json)["schema"],
                             "TSJ_CURRENT_R7_CLEANUP_SOURCE_EMIT_R1")

            destination.write_bytes(b"preserve-existing")
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_OUTPUT_EXISTS"):
                EMITTER.emit(source_root, component_root, authored_root, output_root)
            self.assertEqual(destination.read_bytes(), b"preserve-existing")

        corruptions = (
            ("study", "EMIT_SOURCE_PIN", {"study": STUDY + b"\n"}),
            ("helper", "EMIT_COMPONENT_PIN", {"helper": HELPER + b"\n"}),
            ("template", "EMIT_AUTHORED_FRAGMENT_PIN", {"template": TEMPLATE + b"\n"}),
            ("module", "EMIT_AUTHORED_MODULE_PIN", {"module": b'raise AssertionError("UNAUTHENTICATED_MODULE_EXECUTION")\n'}),
        )
        for name, code, changes in corruptions:
            with self.subTest(input=name), owned_scratch() as root:
                source_root, component_root, authored_root, output_root = write_emit_roots(root, **changes)
                with self.assertRaisesRegex(EMITTER.EmitError, code):
                    EMITTER.emit(source_root, component_root, authored_root, output_root)
                self.assertEqual(list(output_root.iterdir()), [])

    def test_source_reader_enforces_cap_reparse_and_pre_read_snapshot_identity(self):
        with owned_scratch() as root:
            oversized = root / "oversized.java"
            oversized.write_bytes(b"x" * (EMITTER.SOURCE_CAP + 1))
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                EMITTER._read_stable(oversized, "EMIT_SOURCE_INPUT")

            sample = root / "stable.java"
            sample.write_bytes(b"before")
            real_lstat = os.lstat

            class MarkedStat:
                def __init__(self, wrapped, *, mode=None, attributes=None):
                    self._wrapped = wrapped
                    self.st_mode = wrapped.st_mode if mode is None else mode
                    self.st_file_attributes = (
                        getattr(wrapped, "st_file_attributes", 0)
                        if attributes is None else attributes
                    )

                def __getattr__(self, name):
                    return getattr(self._wrapped, name)

            def marked_lstat(candidate, mode=None, attributes=None):
                metadata = real_lstat(candidate)
                if os.path.normcase(str(candidate)) == os.path.normcase(str(sample)):
                    return MarkedStat(metadata, mode=mode, attributes=attributes)
                return metadata

            source_metadata = real_lstat(sample)
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                EMITTER._assert_safe_path(
                    sample,
                    "EMIT_SOURCE_INPUT",
                    lstat=lambda candidate: marked_lstat(
                        candidate, mode=stat.S_IFLNK | stat.S_IRUSR
                    ),
                )
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                EMITTER._assert_safe_path(
                    sample,
                    "EMIT_SOURCE_INPUT",
                    lstat=lambda candidate: marked_lstat(
                        candidate,
                        attributes=getattr(source_metadata, "st_file_attributes", 0)
                        | REPARSE_POINT,
                    ),
                )

            original_open = Path.open

            def mutate_before_open(candidate, mode="r", *open_args, **kwargs):
                if Path(candidate) == sample and mode == "rb":
                    with original_open(sample, "r+b") as changed:
                        changed.seek(0)
                        changed.write(b"after!!")
                        changed.flush()
                return original_open(candidate, mode, *open_args, **kwargs)

            with mock.patch.object(Path, "open", new=mutate_before_open):
                with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                    EMITTER._read_stable(sample, "EMIT_SOURCE_INPUT")

            sample.write_bytes(b"before")
            before = os.stat(sample)

            def same_size_mtime_change(candidate, mode="r", *open_args, **kwargs):
                if Path(candidate) == sample and mode == "rb":
                    with original_open(sample, "r+b") as changed:
                        changed.seek(0)
                        changed.write(b"before")
                        changed.flush()
                    os.utime(sample, ns=(before.st_atime_ns, before.st_mtime_ns + 10000000))
                return original_open(candidate, mode, *open_args, **kwargs)

            with mock.patch.object(Path, "open", new=same_size_mtime_change):
                with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                    EMITTER._read_stable(sample, "EMIT_SOURCE_INPUT")

    def test_cli_writes_source_then_emits_canonical_full_manifest(self):
        with owned_scratch() as root:
            source_root, component_root, authored_root, output_root = write_emit_roots(root)
            old_argv = [
                "--source-root", str(source_root),
                "--component-root", str(component_root),
                "--output-root", str(output_root),
            ]
            old_stdout = io.StringIO()
            with mock.patch.object(EMITTER, "_read_stable",
                                   side_effect=AssertionError("INPUT_READ_BEFORE_ROOT_VALIDATION")), \
                    mock.patch.object(EMITTER, "_read_root_file",
                                      side_effect=AssertionError("INPUT_READ_BEFORE_ROOT_VALIDATION")), \
                    redirect_stdout(old_stdout), redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as raised:
                    EMITTER.main(old_argv)
            self.assertEqual(raised.exception.code, 2)
            self.assertEqual(old_stdout.getvalue(), "")
            self.assertEqual(list(output_root.iterdir()), [])

            stdout = io.StringIO()
            argv = [
                "--source-root", str(source_root),
                "--component-root", str(component_root),
                "--authored-root", str(authored_root),
                "--output-root", str(output_root),
            ]
            with redirect_stdout(stdout):
                self.assertEqual(EMITTER.main(argv), 0)
            raw = stdout.getvalue().encode("ascii")
            self.assertTrue(raw.endswith(b"\n"))
            self.assertNotIn(b"\r", raw)
            manifest = json.loads(raw)
            canonical = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("ascii") + b"\n"
            self.assertEqual(raw, canonical)
            self.assertEqual(manifest["schema"], "TSJ_CURRENT_R7_CLEANUP_SOURCE_EMIT_R1")
            self.assertEqual(manifest["status"], "SOURCE_ONLY")
            self.assertEqual(set(manifest), {
                "schema", "status", "limits", "inputs", "derivedSource", "finalSource", "pureProvenance",
            })
            self.assertEqual(manifest["limits"], {
                "sourceCapBytes": 4194304,
                "manifestCapBytes": 1048576,
            })
            self.assertNotIn(str(root).replace("\\", "/"), raw.decode("ascii"))
            self.assertNotRegex(raw.decode("ascii"), r"(?:[A-Za-z]:\\|/(?:home|Users|tmp)/)")

            expected_inputs = {
                "currentStudy": ("source-root-study", "CURRENT_STUDY.java", STUDY),
                "ownershipHelper": ("component-root-helper", "CurrentBodyOwnership.java", HELPER),
                "authoredModule": ("authored-root-pure-module", MODULE_NAME, PURE_MODULE),
                "authoredMembers": ("authored-root-template", TEMPLATE_NAME, TEMPLATE),
            }
            self.assertEqual(set(manifest["inputs"]), set(expected_inputs))
            for key, (role, basename, raw_input) in expected_inputs.items():
                with self.subTest(input=key):
                    record = manifest["inputs"][key]
                    self.assertEqual(record["role"], role)
                    self.assertEqual(record["basename"], basename)
                    self.assertEqual(record["bytes"], len(raw_input))
                    self.assertEqual(record["sha256"], hashlib.sha256(raw_input).hexdigest())

            derived = manifest["derivedSource"]
            final = manifest["finalSource"]
            self.assertEqual(derived["role"], "pre-ownership-current-study")
            self.assertEqual((derived["bytes"], derived["sha256"]),
                             (PREOWNERSHIP_BYTES, PREOWNERSHIP_SHA256))
            self.assertEqual((final["basename"], final["bytes"], final["sha256"]),
                             ("CohortTerminalTelemetryStudy.java", FINAL_BYTES, FINAL_SHA256))
            self.assertEqual(final["role"], "exclusive-output")
            pure = manifest["pureProvenance"]
            self.assertEqual(set(pure), {"bytes", "sha256", "ascii", "object"})
            pure_raw = pure["ascii"].encode("ascii")
            self.assertEqual(pure["bytes"], len(pure_raw))
            self.assertEqual(pure["sha256"], hashlib.sha256(pure_raw).hexdigest())
            self.assertEqual(json.loads(pure_raw), pure["object"])
            self.assertEqual(pure["object"]["status"], "SOURCE_ONLY")
            self.assertEqual(pure["object"]["limitsAndGates"]["runtimeBehavior"], "NOT_CLAIMED")
            self.assertEqual(pure["object"]["limitsAndGates"]["genuineOriginalNullObserve"],
                             "UNSPENT_GATED")
            destination = output_root / "CohortTerminalTelemetryStudy.java"
            self.assertEqual(destination.read_bytes(), expected_final_source())
            failure_stdout = io.StringIO()
            with redirect_stdout(failure_stdout), redirect_stderr(io.StringIO()):
                self.assertEqual(EMITTER.main(argv), 2)
            self.assertEqual(failure_stdout.getvalue(), "")
            self.assertEqual(destination.read_bytes(), expected_final_source())


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0]] + _unittest_args)
