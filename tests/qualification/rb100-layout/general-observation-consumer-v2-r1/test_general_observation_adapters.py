"""General observation V2 consumer-adapter tests.

This suite consumes one hash-pinned accepted generic R packet as six DTO
fixtures. The full-reader calls below are context projections only; they do not
construct a capture, original run, or whole-result admission claim.
"""
import argparse
import copy
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
import uuid
from collections.abc import Mapping


PACKET_SHA256 = "897db05949756ba10e376494de15bd869fd838355aa67ce2ebc6d38a0e3d801f"
EXPECTED_KEYS = ("observerUnits", "copiedEntries", "copyChunks", "state")
EXPECTED = {
    "NULL": (0, 0, 0, "NOT_ENABLED"),
    "FOUND": (160, 33, 1, "NO_FAILURE"),
    "BRANCH_SEARCH_LIMIT": (192, 43, 2, "FIRST_NON_FOUND"),
    "NO_PATH": (159, 33, 1, "FIRST_NON_FOUND"),
    "TOTAL_SEARCH_LIMIT": (159, 33, 1, "FIRST_NON_FOUND"),
    "QUALITY_LIMIT": (0, 0, 0, "NON_BRANCH_REJECTION"),
}
CASE_PATHS = (
    ("complete", "NULL"),
    ("complete", "FOUND"),
    ("blocked", "BRANCH_SEARCH_LIMIT"),
    ("blocked", "NO_PATH"),
    ("blocked", "TOTAL_SEARCH_LIMIT"),
    ("nonBranchQuality", "QUALITY_LIMIT"),
)


def parse_suite_args(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generated-dir", required=True)
    parser.add_argument("--packet", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--wire-sha", required=True)
    return parser.parse_args(argv)


def canonical_existing_path(value, label, directory=False):
    path = Path(value)
    if not path.is_absolute():
        raise ValueError(label + " must be absolute")
    resolved = path.resolve(strict=True)
    if os.path.normcase(str(resolved)) != os.path.normcase(str(path)):
        raise ValueError(label + " must be canonical")
    if directory and not resolved.is_dir():
        raise ValueError(label + " must be a directory")
    if not directory and not resolved.is_file():
        raise ValueError(label + " must be a regular file")
    return resolved


def load_standalone_module(path, module_name):
    """Execute only the supplied generated standalone source in its own module."""
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None:
        raise ValueError("generated module spec unavailable")
    module = types.ModuleType(module_name)
    module.__file__ = str(path)
    module.__spec__ = spec
    module.__package__ = ""
    sys.modules[module_name] = module
    source = path.read_text(encoding="utf-8")
    exec(compile(source, str(path), "exec"), module.__dict__)
    return module


def load_fixture(args):
    generated_dir = canonical_existing_path(args.generated_dir, "generated directory", True)
    packet_path = canonical_existing_path(args.packet, "packet")
    generic_path = generated_dir / "read_selected_observation_v2.py"
    full_path = generated_dir / "read_full_observation_component_v2.py"
    if not generic_path.is_file() or not full_path.is_file():
        raise ValueError("required generated reader is missing")

    generic = load_standalone_module(generic_path, "_q100_generated_generic_observation")
    full = load_standalone_module(full_path, "_q100_generated_full_observation_component")

    # Pin bytes before allowing the generic reader to parse JSON.
    raw = packet_path.read_bytes()
    actual_sha = hashlib.sha256(raw).hexdigest()
    if actual_sha != PACKET_SHA256:
        raise ValueError("accepted generic packet hash mismatch")
    packet = generic.parse_packet(raw)
    summary = generic.validate_packet(
        packet, args.run_id, args.source_sha, args.wire_sha
    )
    if summary.get("cases") != 6 or summary.get("fullD") is not False:
        raise ValueError("generic reader did not accept the six-case generic fixture")

    cases = {}
    for group, name in CASE_PATHS:
        case = packet[group][name]
        if case.get("caseId") != name:
            raise ValueError("fixture case identity mismatch")
        cases[name] = case

    return {
        "generated_dir": generated_dir,
        "generic_path": generic_path,
        "full_path": full_path,
        "generic": generic,
        "full": full,
        "raw": raw,
        "packet": packet,
        "cases": cases,
        "summary": summary,
        "run_id": args.run_id,
        "source_sha": args.source_sha,
        "wire_sha": args.wire_sha,
    }


def context_projection(case):
    """Build only the explicitly allowed DTO context projection."""
    context = case["context"]
    pads_by_id = {
        pad_id: (pad["netId"], pad["x"], pad["y"])
        for pad_id, pad in context["padsById"].items()
    }
    pads_by_net = {}
    for pad_id, (net_id, _x, _y) in pads_by_id.items():
        pads_by_net.setdefault(net_id, set()).add(pad_id)
    grid = (context["gridWidth"], context["gridHeight"])
    fresh_run = {
        "work": {"advanceCalls": context["callbackOpportunities"]},
        "ordering": {
            "requestedNetIds": context["requestedNetIds"],
            "traversedNetIds": context["traversedNetIds"],
        },
    }
    facts = {
        "outcome": context["outcome"],
        "terminalFailure": context["terminalFailure"],
    }
    return fresh_run, facts, None, pads_by_id, pads_by_net, grid


def full_adapter_result(full, case):
    fresh_run, facts, captured, pads_by_id, pads_by_net, grid = context_projection(case)
    return full._validate_observation(
        case["observation"],
        case["armRole"],
        fresh_run,
        facts,
        captured,
        pads_by_id,
        pads_by_net,
        grid,
    )


def kernel_result(generic, case):
    context = case["context"]
    _fresh_run, _facts, _captured, pads_by_id, pads_by_net, grid = context_projection(case)
    kernel = generic.ObservationV2Kernel
    return kernel.validate_observation(
        case["observation"],
        case["armRole"],
        context["callbackOpportunities"],
        context["requestedNetIds"],
        context["traversedNetIds"],
        pads_by_id,
        pads_by_net,
        grid,
        context["outcome"],
        context["terminalFailure"],
    )


def result_facts(test, result, expected):
    test.assertIsInstance(result, Mapping)
    test.assertEqual(tuple(result.keys()), EXPECTED_KEYS)
    test.assertEqual(
        tuple(result[key] for key in EXPECTED_KEYS),
        expected,
    )
    with test.assertRaises(TypeError):
        result["observerUnits"] = -1


def set_head(case, change):
    head = case["observation"]["selectedHead"]
    change(head)


class GeneralObservationAdapterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if FIXTURE is None:
            raise RuntimeError("suite arguments were not initialized")
        cls.generic = FIXTURE["generic"]
        cls.full = FIXTURE["full"]
        cls.packet = FIXTURE["packet"]
        cls.raw = FIXTURE["raw"]
        cls.cases = FIXTURE["cases"]
        cls.branch = cls.cases["BRANCH_SEARCH_LIMIT"]

    def test_six_accepted_packet_cases_project_through_both_adapters(self):
        for name, case in self.cases.items():
            with self.subTest(case=name):
                expected = EXPECTED[name]
                result_facts(self, kernel_result(self.generic, case), expected)
                result_facts(self, full_adapter_result(self.full, case), expected)
                entries, chunks, units = self.generic._observation_accounting(
                    case["observation"]
                )
                self.assertEqual((entries, chunks, units), (expected[1], expected[2], expected[0]))

    def _assert_both_reject(self, case):
        with self.assertRaises(self.generic.ObservationV2Error):
            kernel_result(self.generic, case)
        with self.assertRaises(self.full.ReaderError):
            full_adapter_result(self.full, case)

    def test_branch_head_and_context_mutations_rejected_independently(self):
        mutations = []

        def missing_head(case):
            case["observation"]["selectedHead"] = None

        mutations.append(("head_absent", missing_head))

        def bad_key(case):
            set_head(case, lambda head: head.update(key=head["key"] + 1))

        mutations.append(("head_key", bad_key))

        def bad_priority(case):
            set_head(case, lambda head: head.update(storedPriority=head["storedPriority"] + 1))

        mutations.append(("head_priority", bad_priority))

        def bad_serial(case):
            set_head(case, lambda head: head.update(serial=-1))

        mutations.append(("head_serial", bad_serial))

        def bool_is_not_an_integer(case):
            set_head(case, lambda head: head.update(branchOrdinal=True))

        mutations.append(("head_boolean_type", bool_is_not_an_integer))

        def extra_head_key(case):
            set_head(case, lambda head: head.update(unexpected=0))

        mutations.append(("head_extra_key", extra_head_key))

        def missing_head_key(case):
            def change(head):
                head.pop(next(iter(head)))

            set_head(case, change)

        mutations.append(("head_missing_key", missing_head_key))

        def reordered_head_keys(case):
            def change(head):
                items = list(head.items())
                head.clear()
                head.update(reversed(items))

            set_head(case, change)

        mutations.append(("head_key_order", reordered_head_keys))

        def callback_not_final(case):
            observation = case["observation"]
            context = case["context"]
            context["callbackOpportunities"] = 2
            observation.update(
                callbackCount=2,
                foundCallbackCount=1,
                nonFoundCallbackCount=1,
                selectedCallbackOrdinal=1,
                transientTupleCopies=3,
                transientFieldCopies=76,
                observerUnits=261,
                copiedEntries=76,
                copyChunks=3,
            )

        mutations.append(("selected_callback_not_final", callback_not_final))

        def bad_pad_context(case):
            pad = next(iter(case["context"]["padsById"].values()))
            pad["x"] += 1

        mutations.append(("context_pad_coordinate", bad_pad_context))

        def bad_grid_context(case):
            case["context"]["gridWidth"] += 1

        mutations.append(("context_grid", bad_grid_context))

        def bad_callback_count(case):
            case["observation"]["callbackCount"] = 0

        mutations.append(("callback_count", bad_callback_count))

        for field, value in (
            ("transientTupleCopies", 1),
            ("transientFieldCopies", 42),
            ("copyChunks", 1),
            ("copiedEntries", 42),
            ("observerUnits", 191),
        ):
            def change_count(case, field=field, value=value):
                case["observation"][field] = value

            mutations.append((field, change_count))

        for name, change in mutations:
            with self.subTest(mutation=name):
                case = copy.deepcopy(self.branch)
                change(case)
                self._assert_both_reject(case)

    def test_generic_fixture_keeps_its_narrow_profile_over_general_kernel(self):
        # The general snapshot kernel accepts a multi-net DTO and returns its
        # selected ordinal; the fixed R packet profile must still reject it.
        case = self.branch
        context = case["context"]
        snapshot = case["observation"]["selectedSnapshot"]
        first_net = context["requestedNetIds"][0]
        other_net = first_net + "_OTHER"
        requested = [first_net, other_net]
        traversed = [first_net, other_net]
        pads_by_id = {
            pad_id: (pad["netId"], pad["x"], pad["y"])
            for pad_id, pad in context["padsById"].items()
        }
        pads_by_id["P_EXTRA"] = (other_net, 1, 1)
        pads_by_net = {
            first_net: {
                pad_id for pad_id, (net_id, _x, _y) in pads_by_id.items()
                if net_id == first_net
            },
            other_net: {"P_EXTRA"},
        }
        grid = (context["gridWidth"], context["gridHeight"])
        ordinal = self.generic.ObservationV2Kernel.validate_snapshot(
            snapshot, requested, traversed, pads_by_id, pads_by_net, grid
        )
        self.assertIs(type(ordinal), int)

        multi_net_packet = copy.deepcopy(self.packet)
        null_context = multi_net_packet["complete"]["NULL"]["context"]
        null_context["requestedNetIds"] = [null_context["requestedNetIds"][0], other_net]
        null_context["padsById"]["P_EXTRA"] = {
            "netId": other_net, "x": 1, "y": 1
        }
        with self.assertRaises(ValueError):
            self.generic.validate_packet(
                multi_net_packet,
                FIXTURE["run_id"],
                FIXTURE["source_sha"],
                FIXTURE["wire_sha"],
            )

        # V=103 is within the general kernel equation, but outside this packet's
        # frozen six-case fixture count (V=102 for the branch-limit case).
        varied_v = copy.deepcopy(self.branch)
        observation = varied_v["observation"]
        observation["validationCount"] = 103
        observation["observerUnits"] = 193
        result_facts(
            self,
            kernel_result(self.generic, varied_v),
            (193, 43, 2, "FIRST_NON_FOUND"),
        )
        result_facts(
            self,
            full_adapter_result(self.full, varied_v),
            (193, 43, 2, "FIRST_NON_FOUND"),
        )
        fixed_v_packet = copy.deepcopy(self.packet)
        fixed_v_case = fixed_v_packet["blocked"]["BRANCH_SEARCH_LIMIT"]
        fixed_v_case["observation"]["validationCount"] = 103
        fixed_v_case["observation"]["observerUnits"] = 193
        fixed_v_case["meterDelta"]["units"] = 193
        with self.assertRaises(ValueError):
            self.generic.validate_packet(
                fixed_v_packet,
                FIXTURE["run_id"],
                FIXTURE["source_sha"],
                FIXTURE["wire_sha"],
            )

        # Generic line framing remains strict even when the JSON object itself
        # is otherwise the accepted packet.
        with self.assertRaises(ValueError):
            self.generic.parse_packet(self.raw + b"\n")

    def test_full_component_cli_denies_before_reading_a_path(self):
        bogus = Path(tempfile.gettempdir()) / (
            "tsj-general-observation-deny-" + uuid.uuid4().hex + ".json"
        )
        self.assertFalse(bogus.exists())
        result = subprocess.run(
            [
                sys.executable,
                "-I",
                "-B",
                str(FIXTURE["full_path"]),
                str(bogus),
            ],
            capture_output=True,
            timeout=30,
        )
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, b"")
        self.assertEqual(
            result.stderr,
            b"GENERAL_OBSERVATION_COMPONENT_ONLY" + os.linesep.encode("ascii"),
        )
        self.assertFalse(bogus.exists())


FIXTURE = None


def main(argv=None):
    global FIXTURE
    args = parse_suite_args(sys.argv[1:] if argv is None else argv)
    FIXTURE = load_fixture(args)
    unittest.main(argv=[sys.argv[0]], verbosity=2)
    return 0


if __name__ == "__main__":
    main()
