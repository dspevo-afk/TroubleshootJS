"""Independent literal fixtures; no producer import, capture, or golden output.

Run with -I -B. Optional --base-generator PATH --io-helper PATH enables real
read-only CLI tests using the public pinned checked-I/O owners.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("selected_reader_under_test", HERE / "read_selected_observation_v2.py")
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
RUN = "01234567-89ab-cdef-0123-456789abcdef"
SOURCE = "a" * 64
WIRE = "b" * 64
BASE_PATH = IO_PATH = None


def snapshot(reason):
    branch, total, minimum = {"BRANCH_SEARCH_LIMIT": (100000, 100001, 0),
                              "NO_PATH": (0, 0, -1),
                              "TOTAL_SEARCH_LIMIT": (1, 1000000, 0)}[reason]
    return {"pass": 2, "netId": "NET_TEST", "startPadId": "P1", "rootPadId": "P2",
            "startX": -20, "startY": 10, "rootX": 0, "rootY": 10,
            "gridWidth": 10, "gridHeight": 10, "goalsTop": 1, "goalsBottom": 0,
            "prefixViaCount": 0, "reachedPadCount": 1,
            "initialManhattanDistance": 20, "minimumExploredManhattanDistance": minimum,
            "branchExpanded": branch, "totalExpanded": total,
            "expandedTop": branch, "expandedBottom": 0,
            "frontierCount": 1 if reason == "BRANCH_SEARCH_LIMIT" else 0,
            "bestStateCount": 1 if reason == "BRANCH_SEARCH_LIMIT" else 0,
            "terminalReason": reason, "relaxedBoundStarted": False,
            "relaxedBoundComplete": False, "relaxedBoundObservationChecked": False,
            "relaxedBoundLiveLayoutStable": False, "relaxedBoundRouterStateStable": False,
            "relaxedBoundRouterEffectsStable": False, "relaxedBoundWorkUnits": 0,
            "relaxedBoundOrdinaryProbes": 0, "relaxedBoundViaProbes": 0,
            "relaxedBoundRefreshes": 0}


def case(name):
    blocked = name in ("BRANCH_SEARCH_LIMIT", "NO_PATH", "TOTAL_SEARCH_LIMIT")
    selected = snapshot(name) if blocked else None
    branch = name == "BRANCH_SEARCH_LIMIT"
    callbacks = int(blocked or name == "FOUND")
    v, u, entries, chunks = {"NULL": (0, 0, 0, 0), "FOUND": (91, 160, 33, 1),
                             "BRANCH_SEARCH_LIMIT": (102, 192, 43, 2),
                             "NO_PATH": (90, 159, 33, 1),
                             "TOTAL_SEARCH_LIMIT": (90, 159, 33, 1),
                             "QUALITY_LIMIT": (0, 0, 0, 0)}[name]
    total = selected["totalExpanded"] if blocked else (1 if name == "FOUND" else 0)
    head = {"branchOrdinal": 1, "cell": 1, "layer": 1, "direction": 4,
            "transitions": 1, "cost": 7, "storedHeuristic": 3, "serial": 0,
            "key": 1901, "storedPriority": 10} if branch else None
    state = {"NULL": "NOT_ENABLED", "FOUND": "NO_FAILURE",
             "QUALITY_LIMIT": "NON_BRANCH_REJECTION"}.get(name, "FIRST_NON_FOUND")
    disposition, reason = {"NULL": ("NOT_RUN", "OBSERVER_DISABLED"),
                           "FOUND": ("FOUND", "FOUND"),
                           "BRANCH_SEARCH_LIMIT": ("CAP_REJECTED", "NET_TEST:BRANCH_SEARCH_LIMIT"),
                           "NO_PATH": ("NO_PATH", "NET_TEST:NO_PATH"),
                           "TOTAL_SEARCH_LIMIT": ("CAP_REJECTED", "NET_TEST:TOTAL_SEARCH_LIMIT"),
                           "QUALITY_LIMIT": ("QUALITY_REJECTED", "QUALITY_LIMIT")}[name]
    meta_entries, meta_chunks = {"NULL": (18, 1), "FOUND": (36, 2),
                                 "BRANCH_SEARCH_LIMIT": (79, 4), "NO_PATH": (69, 3),
                                 "TOTAL_SEARCH_LIMIT": (69, 3), "QUALITY_LIMIT": (36, 2)}[name]
    return {"caseId": name,
            "provenance": "INJECTED_ACTUAL_P06_SESSION" if blocked else "LITERAL_CALLBACK_FIXTURE",
            "armRole": "NULL" if name == "NULL" else "OBSERVE",
            "observation": {"schema": "tsj-cohort-terminal-observation-v2",
                            "mode": "NULL" if name == "NULL" else "OBSERVE", "state": state,
                            "callbackCount": callbacks, "foundCallbackCount": int(name == "FOUND"),
                            "nonFoundCallbackCount": int(blocked),
                            "firstNetOrdinal": 1 if callbacks else None,
                            "lastNetOrdinal": 1 if callbacks else None,
                            "lastCallbackTotalExpanded": total if callbacks else None,
                            "selectedCallbackOrdinal": 1 if blocked else None,
                            "transientTupleCopies": chunks, "transientFieldCopies": entries,
                            "validationCount": v, "observerUnits": u, "copiedEntries": entries,
                            "copyChunks": chunks, "selectedSnapshot": selected, "selectedHead": head},
            "context": {"requestedNetIds": ["NET_TEST"],
                        "traversedNetIds": ["NET_TEST"] if callbacks else [],
                        "padsById": {"P1": {"netId": "NET_TEST", "x": -20, "y": 10},
                                     "P2": {"netId": "NET_TEST", "x": 0, "y": 10}},
                        "gridWidth": 10, "gridHeight": 10,
                        "outcome": {"disposition": disposition, "reason": reason, "expanded": total},
                        "terminalFailure": {"failedNetId": "NET_TEST", "ownerReason": reason,
                                            "searchPass": 2} if blocked else None,
                        "callbackOpportunities": callbacks},
            "meterDelta": {"units": u, "copiedEntries": entries, "copyChunks": chunks},
            "metadata": {"copiedEntries": meta_entries, "copyChunks": meta_chunks}}


def packet():
    return {"schema": "tsj-selected-observation-wire-canary-v2",
            "status": "GENERIC_OBSERVATIONS_RECORDED_ONLY", "runId": RUN,
            "sourceManifestSha256": SOURCE, "wireContractSha256": WIRE,
            "complete": {"NULL": case("NULL"), "FOUND": case("FOUND")},
            "blocked": {"BRANCH_SEARCH_LIMIT": case("BRANCH_SEARCH_LIMIT"),
                        "NO_PATH": case("NO_PATH"), "TOTAL_SEARCH_LIMIT": case("TOTAL_SEARCH_LIMIT")},
            "nonBranchQuality": {"QUALITY_LIMIT": case("QUALITY_LIMIT")},
            "genericCallbackThrow": {"caughtSameThrowable": True, "constructorEntriesBefore": 2,
                                     "constructorEntriesAfter": 3, "callbackExitMeterUnits": 100,
                                     "afterInvokeMeterUnits": 101},
            "directOwnerAbortReuse": {"sentinelKind": "GENERIC_SENTINEL", "caughtSameThrowable": True,
                                      "lastWork": 0, "diagnosticFailureSameThrowable": True,
                                      "searchCleared": True, "candidateCleared": True,
                                      "resultCleared": True, "retrySameThrowable": True,
                                      "sourcePlacementUnchanged": True, "harnessAliasesDropped": True},
            "sourceReview": {"scope": "GENERIC_OBSERVATION_V2_ONLY",
                             "physicalOwnerSha256": "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c",
                             "studyParentSha256": "d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd",
                             "headSemantics": "SELECTED_AND_POPPED_BEFORE_REFRESH_NOT_PATH_WITNESS",
                             "injectedSessionBegins": 3,
                             "otherSessionCounts": "SEPARATE_EXISTING_GENERIC_HELPERS"}}


def line(value):
    return (json.dumps(value, separators=(",", ":"), ensure_ascii=True) + "\n").encode("ascii")


def at(value, path):
    for key in path:
        value = value[key]
    return value


class DomainTests(unittest.TestCase):
    def accept(self, p):
        return reader.validate_packet(p, RUN, SOURCE, WIRE)

    def reject_change(self, path, replacement):
        p = packet()
        at(p, path[:-1])[path[-1]] = replacement
        with self.assertRaises(reader.ReaderError):
            self.accept(p)

    def test_six_literal_cases(self):
        p = packet()
        result = self.accept(reader.parse_packet(line(p)))
        self.assertEqual((result["cases"], result["headCases"], result["fullD"]), (6, 1, False))
        self.assertEqual(result["status"], "PASS_GENERIC_OBSERVATION_WIRE_ONLY")
        b = p["blocked"]["BRANCH_SEARCH_LIMIT"]
        self.assertEqual(b["meterDelta"], {"units": 192, "copiedEntries": 43, "copyChunks": 2})
        self.assertEqual(b["metadata"], {"copiedEntries": 79, "copyChunks": 4})

    def test_versions_identity_and_keys(self):
        for path, value in [(("schema",), "tsj-cohort-terminal-canary-v1"),
                            (("runId",), RUN.upper()), (("sourceManifestSha256",), "c" * 64),
                            (("wireContractSha256",), "d" * 64),
                            (("complete", "FOUND", "observation", "schema"), "tsj-cohort-terminal-observation-v1"),
                            (("sourceReview", "injectedSessionBegins"), 4)]:
            with self.subTest(path=path):
                self.reject_change(path, value)
        for path in [(), ("blocked",), ("blocked", "NO_PATH"),
                     ("blocked", "NO_PATH", "observation"),
                     ("blocked", "NO_PATH", "observation", "selectedSnapshot")]:
            for operation in ("extra", "missing", "order"):
                p = packet()
                obj = at(p, path)
                if operation == "extra":
                    obj["unexpected"] = 0
                elif operation == "missing":
                    del obj[next(iter(obj))]
                else:
                    key = next(iter(obj))
                    obj[key] = obj.pop(key)
                with self.subTest(path=path, operation=operation), self.assertRaises(reader.ReaderError):
                    self.accept(p)

    def test_bad_numbers_and_types(self):
        prefix = ("blocked", "BRANCH_SEARCH_LIMIT", "observation")
        for field in ("callbackCount", "foundCallbackCount", "nonFoundCallbackCount",
                      "validationCount", "observerUnits", "copiedEntries", "copyChunks"):
            for replacement in (True, 1.0, "1", None, -1, 2147483648):
                with self.subTest(field=field, replacement=replacement):
                    self.reject_change(prefix + (field,), replacement)
        self.reject_change(prefix + ("selectedSnapshot", "relaxedBoundComplete"), 0)
        self.reject_change(("sourceReview", "injectedSessionBegins"), True)
        self.reject_change(("genericCallbackThrow", "caughtSameThrowable"), 1)

    def test_context_snapshot_binding(self):
        base = ("blocked", "NO_PATH")
        for path, replacement in [(("context", "requestedNetIds"), ["OTHER"]),
                                  (("context", "traversedNetIds"), []),
                                  (("context", "gridWidth"), 11),
                                  (("context", "padsById", "P1", "x"), 42),
                                  (("context", "padsById", "P2", "netId"), "OTHER"),
                                  (("context", "terminalFailure", "searchPass"), 1),
                                  (("context", "callbackOpportunities"), 0),
                                  (("observation", "selectedSnapshot", "netId"), "OTHER"),
                                  (("observation", "selectedSnapshot", "rootPadId"), "P1"),
                                  (("observation", "selectedSnapshot", "frontierCount"), 1),
                                  (("observation", "selectedSnapshot", "minimumExploredManhattanDistance"), 0)]:
            with self.subTest(path=path):
                self.reject_change(base + path, replacement)
        self.reject_change(("complete", "NULL", "context", "traversedNetIds"), ["NET_TEST"])
        self.reject_change(("nonBranchQuality", "QUALITY_LIMIT", "context", "terminalFailure"), {})

    def test_head_domain_and_java_wrap(self):
        path = ("blocked", "BRANCH_SEARCH_LIMIT", "observation", "selectedHead")
        for field, value in [("branchOrdinal", 0), ("cell", 100), ("layer", 2),
                             ("direction", 5), ("transitions", 49), ("cost", -1),
                             ("storedHeuristic", -1), ("serial", -1), ("key", 1902),
                             ("storedPriority", 11), ("cell", True), ("cost", 2147483648)]:
            with self.subTest(field=field, value=value):
                self.reject_change(path + (field,), value)
        self.reject_change(path, None)
        self.reject_change(("blocked", "NO_PATH", "observation", "selectedHead"), at(packet(), path))
        for cost, heuristic, priority in [(2147483647, 1, -2147483648),
                                           (2147483647, 2147483647, -2), (0, 0, 0)]:
            p = packet()
            head = at(p, path)
            head.update(cost=cost, storedHeuristic=heuristic, storedPriority=priority)
            self.accept(p)
        p = packet()
        head = at(p, path)
        head.update(cell=99, layer=1, direction=4, transitions=48, key=48999)
        self.accept(p)
        self.reject_change(("blocked", "BRANCH_SEARCH_LIMIT", "observation", "selectedSnapshot", "bestStateCount"), 0)

    def test_callback_and_accounting_equations(self):
        prefix = ("blocked", "BRANCH_SEARCH_LIMIT")
        for path, value in [(("observation", "selectedCallbackOrdinal"), 0),
                            (("observation", "lastCallbackTotalExpanded"), 100000),
                            (("observation", "transientTupleCopies"), 1),
                            (("observation", "transientFieldCopies"), 33),
                            (("observation", "copyChunks"), 1),
                            (("observation", "copiedEntries"), 33),
                            (("observation", "observerUnits"), 191),
                            (("meterDelta", "units"), 193),
                            (("meterDelta", "copiedEntries"), 79),
                            (("metadata", "copiedEntries"), 69),
                            (("metadata", "copyChunks"), 3)]:
            with self.subTest(path=path):
                self.reject_change(prefix + path, value)
        # Internally consistent but not the frozen fixture's reported work.
        p = packet()
        b = p["blocked"]["BRANCH_SEARCH_LIMIT"]
        b["observation"].update(validationCount=106, observerUnits=196)
        b["meterDelta"]["units"] = 196
        with self.assertRaises(reader.ReaderError):
            self.accept(p)

    def test_snapshot_scalar_and_state_failures(self):
        prefix = ("blocked", "BRANCH_SEARCH_LIMIT", "observation", "selectedSnapshot")
        for field, value in [("pass", True), ("pass", 3), ("gridHeight", 250001),
                             ("goalsTop", 101), ("prefixViaCount", 49),
                             ("reachedPadCount", 3), ("initialManhattanDistance", -1),
                             ("branchExpanded", 99999), ("totalExpanded", 99999),
                             ("totalExpanded", 1000000), ("expandedBottom", 1),
                             ("minimumExploredManhattanDistance", 21),
                             ("frontierCount", -1), ("bestStateCount", -1),
                             ("relaxedBoundWorkUnits", 1), ("relaxedBoundComplete", True),
                             ("relaxedBoundRouterStateStable", True)]:
            with self.subTest(field=field, value=value):
                self.reject_change(prefix + (field,), value)
        self.reject_change(("blocked", "TOTAL_SEARCH_LIMIT", "observation", "selectedSnapshot", "totalExpanded"), 999999)
        for path, value in [(("complete", "FOUND", "observation", "state"), "FIRST_NON_FOUND"),
                            (("complete", "NULL", "observation", "selectedCallbackOrdinal"), 0),
                            (("nonBranchQuality", "QUALITY_LIMIT", "observation", "callbackCount"), 1),
                            (("complete", "FOUND", "provenance"), "INJECTED_ACTUAL_P06_SESSION"),
                            (("blocked", "NO_PATH", "caseId"), "FOUND")]:
            with self.subTest(path=path):
                self.reject_change(path, value)

    def test_general_derived_bounds_and_relaxed_variants(self):
        for name, vmax, umax in [("NO_PATH", 94, 163), ("BRANCH_SEARCH_LIMIT", 106, 196)]:
            for exhausted in (False, True):
                c = case(name)
                s, o = c["observation"]["selectedSnapshot"], c["observation"]
                s.update(relaxedBoundStarted=True, relaxedBoundComplete=True,
                         prefixViaCount=48 if exhausted else 0,
                         relaxedBoundObservationChecked=exhausted,
                         relaxedBoundLiveLayoutStable=exhausted,
                         relaxedBoundRouterStateStable=exhausted,
                         relaxedBoundRouterEffectsStable=exhausted,
                         relaxedBoundWorkUnits=7, relaxedBoundOrdinaryProbes=2,
                         relaxedBoundViaProbes=1, relaxedBoundRefreshes=1)
                reader._snapshot(s, c["context"])
                if name == "BRANCH_SEARCH_LIMIT" and exhausted:
                    o["selectedHead"].update(transitions=0, key=901)
                if o["selectedHead"] is not None:
                    reader._head(o["selectedHead"], s)
                for v, units in [(1, 91 if name == "BRANCH_SEARCH_LIMIT" else 70), (vmax, umax)]:
                    o.update(validationCount=v, observerUnits=units)
                    reader._observation_accounting(o)
                o.update(validationCount=vmax + 1, observerUnits=umax + 1)
                with self.assertRaises(reader.ReaderError):
                    reader._observation_accounting(o)
                # A relaxed-domain variant never becomes a fixed fixture packet.
                p = packet()
                p["blocked"][name] = c
                with self.assertRaises(reader.ReaderError):
                    self.accept(p)

    def test_source_and_helper_ledgers(self):
        for path, value in [(("sourceReview", "physicalOwnerSha256"), SOURCE),
                            (("sourceReview", "headSemantics"), "PATH_WITNESS"),
                            (("genericCallbackThrow", "constructorEntriesAfter"), 4),
                            (("genericCallbackThrow", "afterInvokeMeterUnits"), 102),
                            (("genericCallbackThrow", "afterInvokeMeterUnits"), 20971521),
                            (("directOwnerAbortReuse", "lastWork"), 1),
                            (("directOwnerAbortReuse", "searchCleared"), False),
                            (("directOwnerAbortReuse", "sentinelKind"), "")]:
            with self.subTest(path=path):
                self.reject_change(path, value)


class FramingTests(unittest.TestCase):
    def test_rejected_bytes(self):
        good = line(packet())
        bad = [good[:-1], good + b"\n", good.replace(b"\n", b"\r\n"),
               b" " + good, good.replace(b'"schema":', b'"schema": '),
               good.replace(b'"caseId":"NULL"', b'"caseId":"NULL","caseId":"NULL"'),
               good.replace(b'"callbackCount":1', b'"callbackCount":1.0', 1),
               good.replace(b'"callbackCount":1', b'"callbackCount":NaN', 1),
               good.replace(b'"callbackCount":1', b'"callbackCount":Infinity', 1),
               good.replace(b'"lastWork":0', b'"lastWork":-0'),
               good.replace(b'"NET_TEST"', b'"NET_\\u0054EST"', 1),
               good.replace(b'"NET_TEST"', b'"NET_\\u0001EST"', 1),
               good.replace(b'"NET_TEST"', b'"NET_\xffEST"', 1),
               b"{}\n{}\n", b"[]\n", b"{}" + b" " * 1048576 + b"\n"]
        for index, raw in enumerate(bad):
            with self.subTest(index=index), self.assertRaises(reader.ReaderError):
                reader.parse_packet(raw)

    def test_tree_limits(self):
        for value in ("a" * 257, "a\n", 1.0, float("nan"), (1,), {1: 1}):
            with self.subTest(kind=type(value)), self.assertRaises(reader.ReaderError):
                reader._tree(value)
        deep = 0
        for _ in range(65):
            deep = [deep]
        with self.assertRaises(reader.ReaderError):
            reader._tree(deep)
        cyclic = []
        cyclic.append(cyclic)
        with self.assertRaises(reader.ReaderError):
            reader._tree(cyclic)
        with self.assertRaises(reader.ReaderError):
            reader._tree([0] * 1048576)

    def test_exact_cli_arguments(self):
        flags = ["--input", "--run-id", "--source-sha", "--wire-sha", "--base-generator", "--io-helper"]
        good = [part for flag in flags for part in (flag, "value")]
        self.assertEqual(len(reader.parse_args(good)), 6)
        for bad in (good[:-1], good + ["--unknown", "value"],
                    ["--unknown"] + good[1:], ["--input", "x"] + good[2:10] + ["--input", "y"]):
            with self.assertRaises(reader.ReaderError):
                reader.parse_args(bad)

    def test_bootstrap_rejects_unpinned_file(self):
        with tempfile.TemporaryDirectory(prefix="tsj-selected-reader-bootstrap-") as temp:
            path = Path(temp).resolve() / "helper.py"
            path.write_bytes(b"raise RuntimeError('must never execute')\n")
            with self.assertRaises(reader.ReaderError) as failure:
                reader._bootstrap_base(str(path))
            self.assertEqual(failure.exception.code, "SW_BASE_HASH")


class CheckedCliTests(unittest.TestCase):
    def setUp(self):
        if BASE_PATH is None or IO_PATH is None:
            self.skipTest("explicit public pinned loader paths not supplied")

    def invoke(self, path, base=None, helper=None, isolated=True):
        command = [sys.executable] + (["-I", "-B"] if isolated else ["-B"])
        command += [str(HERE / "read_selected_observation_v2.py"), "--input", str(path),
                    "--run-id", RUN, "--source-sha", SOURCE, "--wire-sha", WIRE,
                    "--base-generator", str(base or BASE_PATH), "--io-helper", str(helper or IO_PATH)]
        return subprocess.run(command, capture_output=True, timeout=180)

    def rejected(self, result):
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, b"")
        self.assertRegex(result.stderr.decode("ascii"), r"\ASW_[A-Z_]+" + os.linesep + r"\Z")

    def test_actual_checked_read_and_cli(self):
        with tempfile.TemporaryDirectory(prefix="tsj-selected-reader-cli-") as temp:
            root = Path(temp).resolve()
            path = root / "input.json"
            raw = line(packet())
            path.write_bytes(raw)
            names = sorted(child.name for child in root.iterdir())
            result = self.invoke(path)
            self.assertEqual((result.returncode, result.stderr), (0, b""))
            summary = json.loads(result.stdout)
            self.assertEqual(summary["inputSha256"], hashlib.sha256(raw).hexdigest())
            self.assertEqual(summary["inputBytes"], len(raw))
            self.assertEqual(summary["fullD"], False)
            self.assertEqual(path.read_bytes(), raw)
            self.assertEqual(sorted(child.name for child in root.iterdir()), names)
            # Existing unrelated output is untouched; this CLI has no output path.
            occupied = root / "canary.json"
            occupied.write_bytes(b"preserved\n")
            self.assertEqual(self.invoke(path).returncode, 0)
            self.assertEqual(occupied.read_bytes(), b"preserved\n")
            self.rejected(self.invoke(path, isolated=False))
            self.rejected(self.invoke(root))
            self.rejected(self.invoke(root / "missing.json"))
            self.rejected(self.invoke(str(root) + "/../" + root.name + "/input.json"))
            self.rejected(self.invoke(str(root) + "/./input.json"))
            bad_helper = root / "bad.py"
            bad_helper.write_bytes(b"raise RuntimeError('must not execute')\n")
            self.rejected(self.invoke(path, base=bad_helper))
            self.rejected(self.invoke(path, helper=bad_helper))
            path.write_bytes(raw + b"\n")
            self.rejected(self.invoke(path))
            path.write_bytes(raw)
            p = packet()
            p["blocked"]["BRANCH_SEARCH_LIMIT"]["observation"]["copyChunks"] = 1
            path.write_bytes(line(p))
            self.rejected(self.invoke(path))

    def test_actual_input_symlink_rejected(self):
        with tempfile.TemporaryDirectory(prefix="tsj-selected-reader-link-") as temp:
            root = Path(temp).resolve()
            path, link = root / "input.json", root / "linked.json"
            path.write_bytes(line(packet()))
            try:
                link.symlink_to(path)
            except OSError as failure:
                self.skipTest("OS symlink prerequisite unavailable: " + type(failure).__name__)
            self.rejected(self.invoke(link))


if __name__ == "__main__":
    arguments = sys.argv[1:]
    for flag in ("--base-generator", "--io-helper"):
        if flag in arguments:
            index = arguments.index(flag)
            if index + 1 >= len(arguments):
                raise SystemExit("missing checked-I/O test argument")
            if flag == "--base-generator":
                BASE_PATH = arguments[index + 1]
            else:
                IO_PATH = arguments[index + 1]
            del arguments[index:index + 2]
    unittest.main(argv=[sys.argv[0]] + arguments)
