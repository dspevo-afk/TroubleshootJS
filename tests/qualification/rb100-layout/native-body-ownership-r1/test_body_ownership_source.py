"""Source-only checks for the bounded whole-body ownership component."""

import ast
import argparse
import hashlib
import importlib.util
import os
from pathlib import Path
import stat
import sys
import tempfile
from unittest import mock
import unittest


ROOT = Path(__file__).resolve().parent
_source_args = argparse.ArgumentParser(add_help=False)
_source_args.add_argument("--source-root", default=str(ROOT / "source"))
_source_options, _unittest_args = _source_args.parse_known_args()
SOURCE_ROOT = Path(_source_options.source_root).resolve()
STUDY = (SOURCE_ROOT / "CURRENT_STUDY.java").read_bytes()
HELPER = (ROOT / "CurrentBodyOwnership.java").read_bytes()
GENERATOR_PATH = ROOT / "generate_body_ownership.py"
GENERATOR_TEXT = GENERATOR_PATH.read_text(encoding="utf-8")
HELPER_TEXT = HELPER.decode("utf-8")
STUDY_SHA256 = "524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4"
HELPER_SHA256 = "8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832"
ANCHOR = b"    /* One complete placeholder-body accounting pass before the Meter closes. */\n"
INSERTION = b"    CurrentBodyOwnership.validate(meter,body);\n"

SPEC = importlib.util.spec_from_file_location("generate_body_ownership", GENERATOR_PATH)
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


class BodyOwnershipSourceTests(unittest.TestCase):
    def test_pinned_study_and_component_hashes(self):
        self.assertEqual(len(STUDY), 234907)
        self.assertEqual(hashlib.sha256(STUDY).hexdigest(), STUDY_SHA256)
        self.assertEqual(hashlib.sha256(HELPER).hexdigest(), HELPER_SHA256)
        self.assertEqual(EMITTER.PINNED_STUDY_SHA256, STUDY_SHA256)
        self.assertEqual(EMITTER.PINNED_HELPER_SHA256, HELPER_SHA256)

    def test_unique_anchor_insertion_preserves_every_other_source_byte(self):
        self.assertEqual(STUDY.count(ANCHOR), 1)
        generated = EMITTER.insert_at_unique_anchor(STUDY)
        offset = STUDY.index(ANCHOR)
        self.assertEqual(generated, STUDY[:offset] + INSERTION + STUDY[offset:])
        self.assertEqual(generated.replace(INSERTION, b"", 1), STUDY)
        with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_ANCHOR"):
            EMITTER.insert_at_unique_anchor(STUDY.replace(ANCHOR, b"", 1))
        with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_ANCHOR"):
            EMITTER.insert_at_unique_anchor(STUDY.replace(ANCHOR, ANCHOR + ANCHOR, 1))

    def test_emitter_is_ast_valid_and_has_only_the_three_root_inputs(self):
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
        self.assertEqual(flags, {"--source-root", "--component-root", "--output-root"})
        imported_names = {
            alias.name.split(".", 1)[0]
            for node in ast.walk(tree)
            if isinstance(node, (ast.Import, ast.ImportFrom))
            for alias in node.names
        }
        self.assertFalse({"subprocess", "glob"} & imported_names)
        self.assertIn('OUTPUT_NAME = "CohortTerminalTelemetryStudy.java"', GENERATOR_TEXT)

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

    def test_emit_is_exclusive_and_pin_failures_leave_no_output(self):
        with tempfile.TemporaryDirectory(prefix="body-ownership-source-") as temp:
            root = Path(temp)
            source_root = root / "source"
            component_root = root / "component"
            output_root = root / "output"
            source_root.mkdir()
            component_root.mkdir()
            output_root.mkdir()
            (source_root / EMITTER.STUDY_NAME).write_bytes(STUDY)
            (component_root / EMITTER.HELPER_NAME).write_bytes(HELPER)

            destination = EMITTER.emit(source_root, component_root, output_root)
            self.assertEqual(destination.name, "CohortTerminalTelemetryStudy.java")
            self.assertEqual(destination.read_bytes(), EMITTER.insert_at_unique_anchor(STUDY))
            destination.write_bytes(b"preserve-existing")
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_OUTPUT_EXISTS"):
                EMITTER.emit(source_root, component_root, output_root)
            self.assertEqual(destination.read_bytes(), b"preserve-existing")

            bad_source_output = root / "bad-source-output"
            bad_source_output.mkdir()
            (source_root / EMITTER.STUDY_NAME).write_bytes(STUDY + b"\n")
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_PIN"):
                EMITTER.emit(source_root, component_root, bad_source_output)
            self.assertFalse((bad_source_output / EMITTER.OUTPUT_NAME).exists())
            (source_root / EMITTER.STUDY_NAME).write_bytes(STUDY)

            bad_component_output = root / "bad-component-output"
            bad_component_output.mkdir()
            (component_root / EMITTER.HELPER_NAME).write_bytes(HELPER + b"\n")
            with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_COMPONENT_PIN"):
                EMITTER.emit(source_root, component_root, bad_component_output)
            self.assertFalse((bad_component_output / EMITTER.OUTPUT_NAME).exists())

    def test_source_reader_enforces_cap_and_rejects_link_and_snapshot_changes(self):
        with tempfile.TemporaryDirectory(prefix="body-ownership-reader-") as temp:
            root = Path(temp)
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

            symlink_metadata = real_lstat(sample)
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
                        attributes=getattr(symlink_metadata, "st_file_attributes", 0)
                        | EMITTER.REPARSE_POINT,
                    ),
                )

            try:
                link = root / "source-link.java"
                os.symlink(sample, link)
            except (NotImplementedError, OSError):
                link = None
            if link is not None:
                with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                    EMITTER._read_stable(link, "EMIT_SOURCE_INPUT")

            original_open = Path.open

            def mutate_before_open(candidate, mode="r", *args, **kwargs):
                if Path(candidate) == sample and mode == "rb":
                    with original_open(sample, "r+b") as changed:
                        changed.seek(0)
                        # Change length deterministically; same-size timestamp
                        # updates can share one filesystem timestamp quantum.
                        changed.write(b"after!!")
                        changed.flush()
                return original_open(candidate, mode, *args, **kwargs)

            with mock.patch.object(Path, "open", new=mutate_before_open):
                with self.assertRaisesRegex(EMITTER.EmitError, "EMIT_SOURCE_INPUT"):
                    EMITTER._read_stable(sample, "EMIT_SOURCE_INPUT")


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0]] + _unittest_args)
