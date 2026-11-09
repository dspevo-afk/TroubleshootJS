"""Root-owned Source tests; never imports an old builder or executes Java."""
import argparse
import ast
import copy
import hashlib
import json
from pathlib import Path
import re
import sys
import types
import unittest

parser = argparse.ArgumentParser()
parser.add_argument('--generator-source', required=True)
parser.add_argument('--members-source', required=True)
parser.add_argument('--source-root', required=True)
args, remainder = parser.parse_known_args()
sys.argv = [sys.argv[0], *remainder]
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize

WORK_KEYS = (
    'elapsedCutoffNanos','cleanupNanos','groupsBeforeExport','wrapperGroupsBeforeExport',
    'maxGroupNanosBeforeExport','maximumUnitsPerGroupBeforeExport','unitsBeforeExport',
    'setupUnits','setupGroups','setupCopiedEntries','setupCopyChunks',
    'originalCaptureOwnerUnits','originalCaptureWrapperUnits','originalCaptureCallbackUnits',
    'originalCaptureDiagnosticUnits','originalCaptureGroups','originalCaptureCopiedEntries',
    'originalCaptureCopyChunks','nullOwnerUnits','nullWrapperUnits','nullObserverUnits',
    'observeOwnerUnits','observeWrapperUnits','observeObserverUnits','observerUnits',
    'meterUnits','metadataUnits','metadataGroups','metadataCopiedEntries','metadataCopyChunks',
    'copiedEntries','copyChunks','copyChunkLimit','copiedEntryLimit')


def masked_java(text):
    """Mask comments/literals without changing offsets; pinned Java Source only."""
    out = list(text)
    index = 0
    while index < len(text):
        if text.startswith('//', index):
            end = text.find('\n', index)
            if end < 0:
                end = len(text)
        elif text.startswith('/*', index):
            end = text.find('*/', index + 2)
            if end < 0:
                raise AssertionError('UNCLOSED_SOURCE_COMMENT')
            end += 2
        elif text[index] in ('"', "'"):
            quote = text[index]
            end = index + 1
            while end < len(text):
                if text[end] == '\\':
                    end += 2
                elif text[end] == quote:
                    end += 1
                    break
                else:
                    end += 1
            else:
                raise AssertionError('UNCLOSED_SOURCE_LITERAL')
        else:
            index += 1
            continue
        for cursor in range(index, end):
            if out[cursor] != '\n':
                out[cursor] = ' '
        index = end
    return ''.join(out)


def member(text, pattern):
    mask = masked_java(text)
    found = list(re.finditer(pattern, mask))
    if len(found) != 1:
        raise AssertionError('SOURCE_MEMBER_COUNT:' + pattern)
    match = found[0]
    brace = mask.find('{', match.end())
    if brace < 0:
        raise AssertionError('SOURCE_MEMBER_BRACE')
    depth = 1
    end = brace + 1
    while depth and end < len(mask):
        depth += (mask[end] == '{') - (mask[end] == '}')
        end += 1
    if depth:
        raise AssertionError('SOURCE_MEMBER_UNCLOSED')
    return text[match.start():end], text[brace + 1:end - 1]


def load_source(path):
    raw = Path(path).read_bytes()
    assert 0 < len(raw) <= 4194304
    tree = ast.parse(raw)
    allowed = {'__future__', 'hashlib', 'json', 're', 'typing', 'collections'}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            assert all(item.name in allowed for item in node.names)
        if isinstance(node, ast.ImportFrom):
            assert node.level == 0 and node.module in allowed
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            assert node.func.id not in {'open','exec','eval','compile','__import__','input'}
    module = types.ModuleType('_root_authored_flat_generator')
    module.__file__ = str(path)
    sys.modules[module.__name__] = module
    exec(compile(raw, str(path), 'exec'), module.__dict__)
    return module


class FlatWorkOverlayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generator = load_source(args.generator_source)
        root = Path(args.source_root)
        cls.inputs = dict(w_source=(root/'W_STUDY.java').read_bytes(),
                          r_source=(root/'R_STUDY.java').read_bytes(),
                          q_source=(root/'Q_AUTHORED_STUDY.java').read_bytes(),
                          shared_source=(root/'SHARED_OWNER.java').read_bytes(),
                          authored_members_source=Path(args.members_source).read_bytes())

    def generate(self, **changes):
        return self.generator.derive_authored_flat_work(**{**self.inputs, **changes})

    def reject(self, **changes):
        with self.assertRaises(ValueError):
            self.generate(**changes)

    def test_repeated_emission_and_input_immutability(self):
        before = copy.deepcopy(self.inputs)
        first = self.generate()
        second = self.generate()
        self.assertEqual(first, second)
        self.assertEqual(self.inputs, before)
        self.assertIs(type(first.study_source), bytes)
        self.assertIs(type(first.provenance_json), bytes)
        self.assertLessEqual(len(first.study_source), 4194304)
        self.assertTrue(first.study_source.endswith(b'\n'))
        self.assertNotIn(b'\r', first.study_source)
        self.assertIs(type(json.loads(first.provenance_json)), dict)

    def test_all_four_parent_source_mutations_rejected(self):
        for name in ('w_source','r_source','q_source','shared_source'):
            with self.subTest(source=name):
                raw = self.inputs[name]
                self.reject(**{name: bytes([raw[0] ^ 1]) + raw[1:]})

    def test_wrong_parent_types_rejected(self):
        for name in ('w_source','r_source','q_source','shared_source'):
            with self.subTest(source=name):
                self.reject(**{name: 'not bytes'})

    def test_invalid_or_oversize_authored_source_rejected(self):
        for value in (b'', 'not bytes', b' ' * 4194305, b'\xff'):
            with self.subTest(kind=type(value).__name__, length=len(value)):
                self.reject(authored_members_source=value)

    def test_adopted_snapshot_and_meter_adapter_source_preserved(self):
        text = self.generate().study_source.decode('utf-8')
        parent = self.inputs['w_source'].decode('utf-8').replace('\r\n','\n')
        for pattern in (r'\bstatic\s+final\s+class\s+R5Snapshot\b',
                        r'\bstatic\s+final\s+class\s+SetupMeter\b',
                        r'\bstatic\s+final\s+class\s+Parent\b'):
            with self.subTest(pattern=pattern):
                self.assertEqual(member(text, pattern)[0], member(parent, pattern)[0])

    def test_flat_key_order_matches_independent_34_field_contract(self):
        text = self.generate().study_source.decode('utf-8')
        _, body = member(text, r'\br5WorkKey\s*\(\s*int\s+\w+\s*\)')
        rows = re.findall(r'case\s+(\d+)\s*:\s*return\s*"([A-Za-z0-9]+)"', body)
        self.assertEqual([int(row[0]) for row in rows], list(range(34)))
        self.assertEqual(tuple(row[1] for row in rows), WORK_KEYS)

    def test_generic_ledger_is_retired_and_entry_export_are_denied(self):
        text = self.generate().study_source.decode('utf-8')
        masked = masked_java(text)
        self.assertNotRegex(masked, r'\b(?:R7MeterPoint|R7MeterLedger)\b')
        _, main_body = member(text, r'\bpublic\s+static\s+void\s+main\s*\([^)]*\)\s*throws\s+Exception')
        self.assertRegex(masked_java(main_body), r'^\s*throw\s+new\s+')
        matches = list(re.finditer(r'\bstatic\s+R5ExportMetrics\s+exportR5\s*\(', masked))
        self.assertEqual(len(matches), 2)
        for match in matches:
            rest = text[match.start():]
            # Select the first overload without admitting an arbitrary second.
            one = re.search(r'\{([^{}]*)\}', rest)
            self.assertIsNotNone(one)
            export_body = masked_java(one[1])
            self.assertRegex(export_body, r'^\s*throw\s+new\s+')
            self.assertNotRegex(export_body, r'\b(?:meter|args|result|output|pre|finalizer)\s*\.')

    def test_plan_inventory_is_44_fields_without_owner_aliases(self):
        text = self.generate().study_source.decode('utf-8')
        _, plan = member(text, r'\bstatic\s+final\s+class\s+R7WorkPlan\b')
        prefix = plan[:re.search(r'\bprivate\s+R7WorkPlan\s*\(', plan).start()]
        prefix = masked_java(prefix)
        fields = []
        for row in prefix.split(';'):
            row = row.strip()
            if not row:
                continue
            match = re.fullmatch(r'(?:private\s+)?(?:final\s+)?(SetupMeter|R5Snapshot|long|boolean|java\.util\.Map\s*<\s*String\s*,\s*Object\s*>)\s+(.+)', row, re.S)
            self.assertIsNotNone(match, row)
            names = [name.strip() for name in match[2].split(',')]
            self.assertTrue(all(re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*', name) for name in names))
            fields.extend((match[1], name) for name in names)
        self.assertEqual(len(fields), 44)
        self.assertEqual(sum(kind == 'long' for kind, _ in fields), 38)
        self.assertEqual(sum(kind == 'boolean' for kind, _ in fields), 2)
        self.assertEqual(sum(kind not in ('long','boolean') for kind, _ in fields), 4)

    def test_freeze_equation_operation_inventory_is_unchanged(self):
        text = self.generate().study_source.decode('utf-8')
        _, body = member(text, r'\bprivate\s+void\s+freezeAfterCapture\s*\(\s*\)\s*throws\s+Exception')
        mask = masked_java(body)
        self.assertEqual(len(re.findall(r'\br7Subtract\s*\(', mask)), 18)
        self.assertEqual(len(re.findall(r'\br5Add\s*\(', mask)), 4)
        self.assertEqual(len(re.findall(r'\bMath\s*\.\s*max\s*\(', mask)), 2)
        self.assertNotRegex(mask, r'\bmeter\s*\.|\b(?:field|r5Number|r5Flag)\s*\(')

    def test_finalizer_has_one_body_read_no_live_meter_or_map_allocation(self):
        text = self.generate().study_source.decode('utf-8')
        _, body = member(text, r'\bpublic\s+void\s+finalizeWork\s*\(\s*long\s+\w+\s*\)\s*throws\s+Exception')
        self.assertEqual(len(re.findall(r'\.\s*get\s*\(\s*"work"\s*\)', body)), 1)
        mask = masked_java(body)
        self.assertNotRegex(mask, r'\bmeter\s*\.|\b(?:field|r5Number|r5Flag)\s*\(')
        self.assertNotRegex(mask, r'new\s+(?:java\.util\.)?(?:LinkedHashMap|HashMap|TreeMap)\b')
        self.assertRegex(mask, r'\bfinalized\s*=\s*true\s*;')
        _, verify = member(text, r'\bpublic\s+void\s+verifyBound\s*\(')
        self.assertNotRegex(verify, r'\.\s*get\s*\(')

    def test_real_detachment_inspections_and_additions_are_source_bound(self):
        text = self.generate().study_source.decode('utf-8')
        _, body = member(text, r'\bstatic\s+R7WorkPlan\s+captureR7WorkPlan\s*\(')
        mask = masked_java(body)
        self.assertEqual(len(re.findall(r'\br7Read(?:Object|Long|MapLong)\s*\(', mask)), 40)
        self.assertEqual(len(re.findall(r'\br5Add\s*\(', mask)), 11)
        charged = re.search(r'\bmeter\s*\.\s*charge\s*\(\s*R7_CAPTURE_ADDITION_UNITS\s*\)', mask)
        self.assertIsNotNone(charged)
        self.assertLess(charged.start(), re.search(r'\br5Add\s*\(', mask).start())
        self.assertIn('captureDetachedR7WorkPlan', mask)

    def test_frozen_prepayment_constants_match_independent_contract(self):
        text = self.generate().study_source.decode('utf-8')
        expected = {'R7_CAPTURE_ADDITION_UNITS':11, 'R7_WORK_ENTRY_COPIES':35,
                    'R7_PLAN_FIELD_COPIES':26, 'R7_CLOSED_SNAPSHOT_READS':19,
                    'R7_WORK_SLOTS':34, 'R7_FREEZE_UNITS':26,
                    'R7_FINALIZE_UNITS':38, 'R7_FINALIZE_ENTRY_COPIES':34}
        for name, value in expected.items():
            with self.subTest(name=name):
                found = re.findall(r'\b'+name+r'\s*=\s*([^;]+);', masked_java(text))
                self.assertEqual(len(found), 1)
                def number(node):
                    if isinstance(node, ast.Constant) and type(node.value) is int:
                        return node.value
                    if isinstance(node, ast.Name) and node.id in expected:
                        return expected[node.id]
                    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
                        return number(node.left)+number(node.right)
                    raise AssertionError('NONCONSTANT_PREPAYMENT')
                self.assertEqual(number(ast.parse(found[0].strip(), mode='eval').body), value)

    def test_foreign_outer_method_collision_rejected(self):
        marker=b'// @@R7_FLAT_PLAN_END@@'
        self.assertEqual(self.inputs['authored_members_source'].count(marker),1)
        mutated=self.inputs['authored_members_source'].replace(marker,
            b'static void accountResultTree() { }\n'+marker)
        self.reject(authored_members_source=mutated)

    def test_foreign_outer_type_and_constant_collisions_rejected(self):
        marker=b'// @@R7_FLAT_PLAN_END@@'
        for addition in (b'static final class R5Snapshot { }\n',
                         b'static final int R5_BLOCK=1;\n'):
            with self.subTest(addition=addition.decode('ascii')):
                self.reject(authored_members_source=self.inputs['authored_members_source'].replace(
                    marker,addition+marker))


if __name__ == '__main__':
    unittest.main()
