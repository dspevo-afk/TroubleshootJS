"""Synthetic, data-free checks for check_contract.py."""
import ast
import copy
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import check_contract as checker


def authority_fixture():
    qhashes = {name: ("a" if i % 2 == 0 else "b") * 64
               for i, name in enumerate(checker.QUAL_NAMES)}
    lines = [
        "D_QUALIFICATION_SHA256 = " + repr(qhashes),
        "N_RUN_SHA256 = " + repr(checker.CLOSURE_ROWS[0][5]),
        "N_SOURCE_SHA256 = " + repr(checker.CLOSURE_ROWS[1][5]),
        "N_CLASS_SHA256 = " + repr(checker.CLOSURE_ROWS[2][5]),
        "N_READER_SHA256 = '84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52'",
        "PAD_HELPER_SOURCE_SHA256 = '5c39150dad347bfa7dba5b902f149b4da61551dddc76def5176c2dbc84853c64'",
        "PRIOR_OBSERVATION_SHA256 = 'd2dd656567031515aa520528f2cb8343c9252558b3108bcc4eb840c887e9edcb'",
        "def _pinned_rows(n_builder):",
        "    specs = [",
    ]
    for role, _allow_role, _path, size, digest in checker.CALL_ROWS:
        lines.append("        (%r, None, %d, %r)," % (role, size, digest))
    lines += [
        "    ]",
        "    limit = 8 * 1024 * 1024 if role in ('data-op-1', 'data-result') else 256 * 1024",
        "    return specs",
        "def _qualification_refs(n_builder):",
        "    for name in ('owner.json','parent.json','process.json','request.json','result.json','stderr.txt','stdout.txt'): pass",
        "    n_builder._read_file(None, 512 * 1024, None)",
        "def _observation_ref(n_builder):",
        "    return _read_ref(n_builder, 'qualification-projection', None, 3916, PRIOR_OBSERVATION_SHA256, 128 * 1024, 'OBS')",
        "def _closure_refs(n_builder, n_context):",
        "    specs = [",
    ]
    for role, _allow_role, _path, size, name, _digest in checker.CLOSURE_ROWS:
        lines.append("        (%r, None, %d, %s)," % (role, size, name))
    lines += [
        "    ]",
        "    _read_ref(n_builder, role, path, size, digest, 4 * 1024 * 1024, 'CLOSURE')",
    ]
    return "\n".join(lines), qhashes


def valid_allowlist(tree):
    source_rows, qhashes, _ = checker._source_rows(tree)
    return {
        "schema": "tsj-full-d-v2-normal-oracle-allowlist-v1",
        "status": "FROZEN_PINS_UNREAD_READER_GATES_REQUIRED",
        "readingEnabled": False,
        "roleCount": 20,
        "authoritySourceSha256": "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321",
        "helperSourceSha256": checker.ALLOWLIST_HELPER_SHA256,
        "noRecursiveManifestFollowing": True,
        "oldDDescriptorResultGeneratedInputDenied": True,
        "protectedAndHeldOutDenied": True,
        "qualificationProjectionIsCrosscheckOnly": True,
        "rootPathsArePrivateAndExternallyFixed": True,
        "rows": checker._expected_allowlist_rows(source_rows, qhashes),
    }


class MarkerTests(unittest.TestCase):
    def test_maximum_canonical_marker_is_656_bytes(self):
        raw = checker.validate_marker(checker.maximum_marker_fixture())
        self.assertEqual(len(raw), 656)
        self.assertEqual(raw[-1:], b"\n")
        self.assertEqual(tuple(checker.validate_marker_bytes(raw).keys()), checker.MARKER_KEYS)

    def test_marker_rejects_negative_and_non_integer_numeric_values(self):
        for bad in (True, 1.0, -1, checker.MARKER_CAPS["totalGroups"] + 1):
            marker = checker.maximum_marker_fixture()
            marker["totalGroups"] = bad
            with self.subTest(value=bad), self.assertRaises(checker.ContractError):
                checker.validate_marker(marker)

    def test_marker_rejects_invalid_header_hash_schema_and_order(self):
        mutations = (
            ("schema", "old-marker"),
            ("runId", "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"),
            ("inputContextSha256", "g" * 64),
        )
        for key, value in mutations:
            marker = checker.maximum_marker_fixture()
            marker[key] = value
            with self.subTest(key=key), self.assertRaises(checker.ContractError):
                checker.validate_marker(marker)
        marker = checker.maximum_marker_fixture()
        marker = dict(reversed(list(marker.items())))
        with self.assertRaises(checker.ContractError):
            checker.validate_marker(marker)

    def test_marker_reader_rejects_duplicate_keys_and_noncanonical_line(self):
        with self.assertRaises(checker.ContractError):
            checker.validate_marker_bytes(b'{"schema":"x","schema":"y"}\n')
        raw = checker.validate_marker(checker.maximum_marker_fixture())
        with self.assertRaises(checker.ContractError):
            checker.validate_marker_bytes(raw + b"\n")

    def test_contract_equations_and_design_facts_are_checked(self):
        bound = {
            "schema": "tsj-full-d-v2-marker-bound-v1",
            "status": "FROZEN_SOURCE_CONTRACT_ONLY",
            "actualRuntimeProof": False,
            "requiresNativePreflightBounds": True,
            "orderedKeys": list(checker.MARKER_KEYS),
            "limits": [{"key": key, "maximum": str(cap)}
                       for key, cap in checker.MARKER_CAPS.items()],
            "keyQuotedBytes": 206, "fixedQuotedValueBytes": 355,
            "numberDigitBytes": 65, "punctuationAndLf": 30,
            "boundBytes": 656, "transportCeilingBytes": 722,
        }
        wire = "## Marker\nexact ordered14 keys:\n" + ",\n".join(checker.MARKER_KEYS) + ".\n## Admission"
        terms = (
            "Original boundary | 9units:5 source inspections and4 checked arithmetic operations",
            "Capture sums | 11 checked additions", "Flat work construction | Copy35 logical entries",
            "The plan has44 fields:4 identity refs", "Plan | Allocate1unit, copy26 detached constructor longs",
            "Snapshot | Existing allocateR5Snapshot1unit and19 closed-native field/list inspections",
            "Freeze | 26units:18 checked subtractions,4 checked additions,2 Math.max operations",
            "Finalize | Copy34 numeric wrapper replacements. Prepay38units",
        )
        context = " ".join(key + "=" + value for key, value in checker.SEEDS.items())
        result = checker._validate_marker_contract(bound, wire, "\n".join(terms), context)
        self.assertEqual(result["maximumBytes"], 656)
        bad = copy.deepcopy(bound)
        bad["boundBytes"] = 657
        with self.assertRaises(checker.ContractError):
            checker._validate_marker_contract(bad, wire, "\n".join(terms), context)


class SourceLiteralTests(unittest.TestCase):
    def test_java_methods_do_not_count_as_instance_fields(self):
        plan = """{
    private final SetupMeter meter;
    private final R5Snapshot pre;
    private final java.util.Map<String,Object> work;
    private long first,second;
    private boolean frozen,finalized;
    private R7WorkPlan(long started) { }
    private long value(int index,long elapsed) { return elapsed; }
}"""
        self.assertEqual(checker._plan_field_counts(plan), (3, 2, 2))
        more = plan.replace("    private R7WorkPlan(",
                            "    private long additional;\n    private R7WorkPlan(")
        self.assertEqual(checker._plan_field_counts(more), (3, 3, 2))

    def test_ast_literal_contract_builds_twenty_non_opened_role_expectations(self):
        source, _ = authority_fixture()
        tree = ast.parse(source)
        doc = valid_allowlist(tree)
        result = checker._validate_allowlist(doc, tree)
        self.assertEqual(result, {"rolesCompared": 20, "oracleDataPathsOpened": 0})
        doc["rows"][0]["sha256"] = "0" * 64
        with self.assertRaises(checker.ContractError):
            checker._validate_allowlist(doc, tree)

    def test_ast_literal_mutations_fail_closed(self):
        source, _ = authority_fixture()
        mutated = source.replace("377138", "377139", 1)
        with self.assertRaises(checker.ContractError):
            checker._source_rows(ast.parse(mutated))
        mutated = source.replace("512 * 1024", "512 * 1024 + 1", 1)
        with self.assertRaises(checker.ContractError):
            checker._source_rows(ast.parse(mutated))


if __name__ == "__main__":
    unittest.main()
