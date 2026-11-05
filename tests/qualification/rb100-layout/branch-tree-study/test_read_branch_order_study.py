"""Source-only reader fixtures for the P07 branch-order study packet."""

from __future__ import annotations

import hashlib
import itertools
import json
import tempfile
import unittest
from pathlib import Path

import read_branch_order_study as reader


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_WIRE = Path("docs/task-evidence/Q100/branch-tree-study-r1/WIRE.md")
PARENT_HEAD = "273c7fce3b98ab68a83fc5ea395dba523a48a561"
RUN_ID = "d7b9d7e5-3cc0-4ed5-9f23-fec562db9035"
PAD_ROWS = (
    ("pad-a", "component-a", 0),
    ("pad-b", "component-b", 10),
    ("pad-c", "component-c", 30),
    ("pad-d", "component-d", 60),
)
PRISTINE_BYTES = 16


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _json_bytes(value, *, newline=True) -> bytes:
    raw = json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
                     allow_nan=False).encode("ascii")
    return raw + (b"\n" if newline else b"")


def _line(value) -> bytes:
    return _json_bytes(value)


def _pads(group=1):
    return [dict(record="PAD", group=group, padId=pad_id, netId="net-one",
                 componentId=component_id, x=x, y=0, role="SIGNAL")
            for pad_id, component_id, x in PAD_ROWS]


def _snapshot(pass_number, start_pad_id, *, total_expanded, reached_count=1,
              terminal="NO_PATH", branch_expanded=1, expanded_top=1, expanded_bottom=0,
              pads=None):
    rows = _pads() if pads is None else pads
    by_id = {row["padId"]: row for row in rows}
    start = by_id[start_pad_id]
    root = by_id["pad-a"]
    return {
        "pass": pass_number,
        "startX": start["x"], "startY": start["y"],
        "rootX": root["x"], "rootY": root["y"],
        "gridWidth": 10, "gridHeight": 10, "goalsTop": 2, "goalsBottom": 2,
        "prefixViaCount": 0, "reachedPadCount": reached_count,
        "initialManhattanDistance": abs(start["x"] - root["x"]),
        "minimumExploredManhattanDistance": 0,
        "branchExpanded": branch_expanded, "totalExpanded": total_expanded,
        "expandedTop": expanded_top, "expandedBottom": expanded_bottom,
        "frontierCount": 0, "bestStateCount": 1,
        "relaxedBoundWorkUnits": 0, "relaxedBoundOrdinaryProbes": 0,
        "relaxedBoundViaProbes": 0, "relaxedBoundRefreshes": 0,
        "relaxedBoundStarted": False, "relaxedBoundComplete": False,
        "relaxedBoundObservationChecked": False,
        "relaxedBoundLiveLayoutStable": False,
        "relaxedBoundRouterStateStable": False,
        "relaxedBoundRouterEffectsStable": False,
        "netId": "net-one", "startPadId": start_pad_id,
        "rootPadId": "pad-a", "terminalReason": terminal,
    }


def make_operation(arm, *, run_id=RUN_ID, source_manifest_sha256=None,
                   wire_sha256=None):
    """Return a complete tiny JSONL operation and its post-close index entry."""
    if source_manifest_sha256 is None:
        source_manifest_sha256 = "1" * 64
    if wire_sha256 is None:
        wire_sha256 = "2" * 64
    observed = arm != 0
    pads = _pads(group=1)
    context = {
        "requestIndex": 0,
        "selectedAttempt": 0,
        "eligibleAttempts": [0],
        "pristineSha256": _sha(b"tiny pristine board"),
        "pristineBytes": PRISTINE_BYTES,
        "padCensusSha256": reader.sha(reader.pad_bytes(pads)),
        "boardSha256": _sha(b"tiny board context"),
    }
    candidate = {
        "rootSeed": "0", "placementSeed": "0", "routingSeed": "0",
        "variant": "TINY", "layoutVersion": 26,
        "dependencyEpoch": "tsj-generation-dependencies-v28",
        "componentCount": 4, "padCount": 4, "netCount": 1,
        "traceWidthProfile": "ROLE_WIDTHS",
    }
    arm_id, api, strategy, observed_flag = reader.ARMS[arm]
    header = {
        "record": "HEADER", "group": 0, "schema": reader.SCHEMA,
        "runId": run_id, "mode": "TINY_CANARY", "operation": arm,
        "arm": {"id": arm_id, "api": api, "strategy": strategy,
                "observed": observed_flag},
        "sourceManifestSha256": source_manifest_sha256,
        "wireSha256": wire_sha256,
        "candidate": candidate,
        "limits": dict(reader.LIMITS),
    }
    branches = []
    if observed:
        starts = ("pad-b", "pad-d" if arm == 3 else "pad-b", "pad-b")
        for pass_number, start_pad_id in enumerate(starts):
            branches.append({
                "record": "BRANCH", "operation": arm, "ordinal": pass_number,
                "group": pass_number + 1,
                "prefixSha256": _sha(("same-prefix-pass-{}".format(pass_number)).encode("ascii")),
                "prefixBytes": 64,
                "snapshot": _snapshot(pass_number, start_pad_id,
                                      total_expanded=pass_number + 1, pads=pads),
            })

    receipt = {
        "attempt": 0, "startedOrderings": 3, "completedOrderings": 3,
        "yieldedOrderings": 0, "finalizedOrderings": 0,
        "selectedOrdering": -1, "expansions": 3,
        "guidanceStarted": True, "comparisonComplete": True,
        "termination": "net-one:NO_PATH",
    }
    result = {
        "record": "RESULT", "operation": arm, "group": 4,
        "proof": "COMPLETE", "route": "TYPED_REQUEST_REJECTION",
        "context": context, "receipt": receipt,
        "outcome": "complete typed tiny NO_PATH fixture",
        "winner": None,
        "physical": {"connectivity": "NOT_RUN", "clearanceKeepout": "NOT_RUN",
                     "layeredRaster": "NOT_RUN", "access": "NOT_RUN",
                     "functional": "NOT_RUN"},
        "logicalWork": {"generatorAdvances": 3, "p07Slices": 3,
                        "p07OwnerSteps": 30, "expansions": 3},
        "boundary": {"requestCount": 1, "outcomeCount": 1,
                     "routeRetired": True, "candidateRetired": True,
                     "generatorComplete": False, "eventObserved": observed,
                     "successorAdvanceCalls": 0},
    }

    # Census and callbacks belong to ADVANCE groups. The plain control has the
    # same three owner slices and work counters but deliberately emits no BRANCH.
    batches = [
        [header],
        pads + (branches[0:1] if observed else []),
        branches[1:2] if observed else [],
        branches[2:3] if observed else [],
        [result],
    ]
    phases = ("SETUP", "ADVANCE", "ADVANCE", "ADVANCE", "REPORT")
    rows = []
    for ordinal, batch in enumerate(batches):
        rows.extend(batch)
        branches_here = [row for row in batch if row["record"] == "BRANCH"]
        pads_here = [row for row in batch if row["record"] == "PAD"]
        is_advance = phases[ordinal] == "ADVANCE"
        hash_bytes = sum(row["prefixBytes"] for row in branches_here)
        if ordinal == 1:
            hash_bytes += len(reader.pad_bytes(pads)) + context["pristineBytes"]
        rows.append({
            "record": "GROUP", "operation": arm, "ordinal": ordinal,
            "phase": phases[ordinal], "elapsedBeforeOwnLineNanos": 100,
            "generatorAdvances": 1 if is_advance else 0,
            "p07Slices": 1 if is_advance else 0,
            "p07Steps": 10 if is_advance else None,
            "accounting": "MEASURED" if is_advance else "NOT_APPLICABLE",
            "observerChecks": 1 if is_advance else 0,
            "callbacks": len(branches_here), "censusRows": len(pads_here),
            "hashInputBytes": hash_bytes,
            "serializedBeforeOwnLineBytes": sum(len(_line(row)) for row in batch),
        })
    rows.append({"record": "FOOTER", "group": 4, "operation": arm,
                 "complete": True, "padCount": 4, "branchCount": len(branches),
                 "groupCount": 5})
    raw = b"".join(_line(row) for row in rows)
    entry = {
        "operation": arm, "armId": arm_id,
        "receipt": {"path": "op-{}.jsonl".format(arm),
                    "bytes": len(raw), "sha256": _sha(raw)},
        "elapsedToCloseNanos": 1000, "maxFullGroupNanos": 100,
        "cleanup": {"status": "PASS", "elapsedNanos": 1,
                    "ownersReleased": True, "contextRestored": True},
    }
    return raw, entry


def write_fixture(directory, repositoryRoot=REPOSITORY_ROOT, wirePath=None):
    """Write a pinned READER_FIXTURE and return ``(spec_path, spec_sha256)``."""
    root = Path(repositoryRoot).resolve()
    wire_path = Path(wirePath) if wirePath is not None else DEFAULT_WIRE
    if not wire_path.is_absolute():
        wire_path = root / wire_path
    wire_path = wire_path.resolve()
    reader_path = Path(reader.__file__).resolve()
    reader_rel = reader_path.relative_to(root).as_posix()
    wire_rel = wire_path.relative_to(root).as_posix()
    base = Path(directory)
    base.mkdir(parents=True, exist_ok=True)

    source_manifest = {
        "schema": "tsj-branch-order-sources-v1", "kind": "READER_FIXTURE",
        "parentHead": PARENT_HEAD, "repositoryRoot": str(root),
        "baseManifest": None,
        "files": [
            {"role": "READER", "path": reader_rel,
             "bytes": reader_path.stat().st_size, "sha256": _sha(reader_path.read_bytes())},
            {"role": "WIRE", "path": wire_rel,
             "bytes": wire_path.stat().st_size, "sha256": _sha(wire_path.read_bytes())},
        ],
    }
    source_raw = _json_bytes(source_manifest)
    source_path = base / "source-manifest.json"
    source_path.write_bytes(source_raw)
    source_sha = _sha(source_raw)
    wire_sha = source_manifest["files"][1]["sha256"]

    operation_entries = []
    for arm in range(4):
        raw, entry = make_operation(arm, source_manifest_sha256=source_sha,
                                    wire_sha256=wire_sha)
        (base / entry["receipt"]["path"]).write_bytes(raw)
        operation_entries.append(entry)
    index = {
        "schema": "tsj-branch-order-index-v1", "runId": RUN_ID,
        "kind": "READER_FIXTURE", "sourceManifestSha256": source_sha,
        "wireSha256": wire_sha, "operations": operation_entries,
    }
    index_raw = _json_bytes(index)
    index_path = base / "study-index.json"
    index_path.write_bytes(index_raw)
    spec = {
        "schema": "tsj-branch-order-reader-input-v1", "kind": "READER_FIXTURE",
        "sourceManifest": {"path": source_path.name, "bytes": len(source_raw),
                            "sha256": source_sha},
        "studyIndex": {"path": index_path.name, "bytes": len(index_raw),
                       "sha256": _sha(index_raw)},
        "transport": None,
    }
    spec_raw = _json_bytes(spec)
    spec_path = base / "study-spec.json"
    spec_path.write_bytes(spec_raw)
    return spec_path, _sha(spec_raw)


def _load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _write_json(path, value):
    Path(path).write_bytes(_json_bytes(value))


def _write_spec(spec_path, spec):
    raw = _json_bytes(spec)
    Path(spec_path).write_bytes(raw)
    return Path(spec_path), _sha(raw)


def _refresh_fixture(spec_path):
    """Repin changed fixture receipts/source and return the new spec digest."""
    spec_path = Path(spec_path)
    spec = _load_json(spec_path)
    source_ref = spec["sourceManifest"]
    source_path = spec_path.parent / source_ref["path"]
    source_raw = source_path.read_bytes()
    source = json.loads(source_raw.decode("utf-8"))
    source_sha = _sha(source_raw)
    spec["sourceManifest"] = {"path": source_ref["path"], "bytes": len(source_raw),
                              "sha256": source_sha}

    index_ref = spec["studyIndex"]
    index_path = spec_path.parent / index_ref["path"]
    index = _load_json(index_path)
    wire_sha = next((row["sha256"] for row in source["files"]
                     if row["role"] == "WIRE"), index["wireSha256"])
    index["sourceManifestSha256"] = source_sha
    index["wireSha256"] = wire_sha
    for arm, entry in enumerate(index["operations"]):
        receipt_path = spec_path.parent / entry["receipt"]["path"]
        raw = receipt_path.read_bytes()
        entry["receipt"] = {"path": entry["receipt"]["path"],
                            "bytes": len(raw), "sha256": _sha(raw)}
    index_raw = _json_bytes(index)
    index_path.write_bytes(index_raw)
    spec["studyIndex"] = {"path": index_ref["path"], "bytes": len(index_raw),
                          "sha256": _sha(index_raw)}
    return _write_spec(spec_path, spec)


def _read_rows(spec_path, arm):
    raw = (Path(spec_path).parent / "op-{}.jsonl".format(arm)).read_bytes()
    return [json.loads(line) for line in raw.splitlines()]


def _branch(rows, pass_number):
    return next(row for row in rows if row["record"] == "BRANCH"
                and row["snapshot"]["pass"] == pass_number)


def _group(rows, ordinal):
    return next(row for row in rows if row["record"] == "GROUP"
                and row["ordinal"] == ordinal)


def _pad(rows, pad_id):
    return next(row for row in rows if row["record"] == "PAD" and row["padId"] == pad_id)


def _repair_group_ledgers(rows):
    pending = []
    for row in rows:
        if row["record"] != "GROUP":
            pending.append(row)
            continue
        row["serializedBeforeOwnLineBytes"] = sum(len(_line(item)) for item in pending)
        row["callbacks"] = sum(item["record"] == "BRANCH" for item in pending)
        row["censusRows"] = sum(item["record"] == "PAD" for item in pending)
        required_hash = sum(item["prefixBytes"] for item in pending
                            if item["record"] == "BRANCH")
        row["hashInputBytes"] = max(row["hashInputBytes"], required_hash)
        pending = []
    footer = rows[-1]
    footer["padCount"] = sum(row["record"] == "PAD" for row in rows)
    footer["branchCount"] = sum(row["record"] == "BRANCH" for row in rows)
    footer["groupCount"] = sum(row["record"] == "GROUP" for row in rows)


def _rewrite_operation(spec_path, arm, mutate, *, repair_groups=True):
    rows = _read_rows(spec_path, arm)
    mutate(rows)
    if repair_groups:
        _repair_group_ledgers(rows)
    raw = b"".join(_line(row) for row in rows)
    (Path(spec_path).parent / "op-{}.jsonl".format(arm)).write_bytes(raw)
    return _refresh_fixture(spec_path)


def _rewrite_manifest(spec_path, mutate):
    spec_path = Path(spec_path)
    spec = _load_json(spec_path)
    manifest_path = spec_path.parent / spec["sourceManifest"]["path"]
    manifest = _load_json(manifest_path)
    mutate(manifest)
    _write_json(manifest_path, manifest)
    return _refresh_fixture(spec_path)


def _rewrite_index(spec_path, mutate):
    spec_path = Path(spec_path)
    spec = _load_json(spec_path)
    index_path = spec_path.parent / spec["studyIndex"]["path"]
    index = _load_json(index_path)
    mutate(index)
    _write_json(index_path, index)
    index_raw = index_path.read_bytes()
    spec["studyIndex"] = {"path": spec["studyIndex"]["path"],
                          "bytes": len(index_raw), "sha256": _sha(index_raw)}
    return _write_spec(spec_path, spec)


def _expected_choice(pads, reached, remaining, *, farthest=False):
    by_id = {row["padId"]: row for row in pads}
    distances = {
        candidate: min(abs(by_id[candidate]["x"] - by_id[known]["x"])
                       + abs(by_id[candidate]["y"] - by_id[known]["y"])
                       for known in reached)
        for candidate in remaining
    }
    boundary = (max if farthest else min)(distances.values())
    return min(candidate for candidate, distance in distances.items()
               if distance == boundary)


class BranchOrderReaderTests(unittest.TestCase):
    def _fixture(self, directory):
        return write_fixture(directory)

    def _assert_rejects(self, spec_path, spec_sha, code):
        with self.assertRaises(ValueError) as caught:
            reader.validate_spec(spec_path, spec_sha)
        self.assertEqual(str(caught.exception), code)

    def test_four_arm_fixture_and_strict_cli_output(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-reader-") as directory:
            spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            self.assertEqual(result["kind"], "READER_FIXTURE")
            self.assertEqual(result["operations"], 4)
            self.assertEqual(result["branches"], [0, 3, 3, 3])
            self.assertEqual(result["routeOutcomes"], ["TYPED_REQUEST_REJECTION"] * 4)
            self.assertEqual(result["divergence"], {
                "status": "FOUND", "controlOrdinal": 1, "treatmentOrdinal": 1,
                "netId": "net-one", "rootPadId": "pad-a", "nearest": "pad-b",
                "farthest": "pad-d",
                "prefixSha256": _sha(b"same-prefix-pass-1"),
            })
            self.assertEqual(result["limitations"]["rootNativeAcceptance"],
                             "NOT_GRANTED_BY_READER")
            output_path = Path(directory) / "cli-result.json"
            self.assertIsNone(reader.main(["--spec", str(spec_path), "--spec-sha256", spec_sha,
                                           "--output", str(output_path)]))
            self.assertEqual(json.loads(output_path.read_text(encoding="utf-8"))["status"],
                             "PASS_READER_FIXTURE_ONLY")
            with self.assertRaises(FileExistsError):
                reader.main(["--spec", str(spec_path), "--spec-sha256", spec_sha,
                             "--output", str(output_path)])

    def test_strict_json_rejects_duplicate_float_nonfinite_bom_and_invalid_utf8(self):
        invalid = (
            (b'{"x":1,"x":2}', "JSON_DUPLICATE_KEY"),
            (b'{"x":1.0}', "JSON_NONINTEGER"),
            (b'{"x":NaN}', "JSON_NONINTEGER"),
            (b'\xef\xbb\xbf{}', "JSON_BOM"),
        )
        for raw, code in invalid:
            with self.subTest(raw=raw), self.assertRaises(ValueError) as caught:
                reader.strict_json(raw)
            self.assertEqual(str(caught.exception), code)
        with self.assertRaises(UnicodeDecodeError):
            reader.strict_json(b"{\xff}")

    def test_strict_json_rejects_negative_zero_and_surrogate(self):
        for raw, code in ((b'{"x":-0}', "JSON_NEGATIVE_ZERO"),
                          (b'{"x":"\\ud800"}', "JSON_SURROGATE")):
            with self.subTest(raw=raw), self.assertRaises(ValueError) as caught:
                reader.strict_json(raw)
            self.assertEqual(str(caught.exception), code)

    def test_spec_schema_rejects_unknown_and_missing_fields(self):
        for change in (lambda spec: spec.update(extra=True),
                       lambda spec: spec.pop("transport")):
            with self.subTest(change=change.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-schema-") as directory:
                spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
                spec = _load_json(spec_path)
                change(spec)
                spec_path, changed_sha = _write_spec(spec_path, spec)
                self._assert_rejects(spec_path, changed_sha, "SPEC_SCHEMA")

    def test_source_manifest_schema_rejects_unknown_and_missing_fields(self):
        for change in (lambda manifest: manifest.update(extra=True),
                       lambda manifest: manifest.pop("baseManifest")):
            with self.subTest(change=change.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-source-schema-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_manifest(spec_path, change)
                self._assert_rejects(spec_path, spec_sha, "SOURCE_MANIFEST_SCHEMA")

    def test_source_manifest_requires_exact_reader_and_wire_roles(self):
        mutations = (
            (lambda manifest: manifest["files"][0].update(role="TEST"), "SOURCE_ROLES"),
            (lambda manifest: manifest["files"][1].update(role="READER"),
             "READER_SOURCE_PATH"),
            (lambda manifest: manifest["files"][0].update(path="../escape.py"),
             "SOURCE_FILE_PATH_ROLE"),
        )
        for mutate, code in mutations:
            with self.subTest(code=code, mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-roles-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_manifest(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_source_wire_byte_pin_is_checked(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-wire-pin-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_manifest(
                spec_path, lambda manifest: manifest["files"][1].update(sha256="0" * 64))
            self._assert_rejects(spec_path, spec_sha, "FILE_PIN")

    def test_index_schema_and_arm_order_are_closed(self):
        for mutate, code in (
                (lambda index: index.update(extra=True), "INDEX_SCHEMA"),
                (lambda index: index.pop("wireSha256"), "INDEX_SCHEMA"),
                (lambda index: index["operations"][1].update(armId="WRONG"),
                 "INDEX_OPERATION_ORDER")):
            with self.subTest(code=code, mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-index-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_index(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_header_schema_rejects_unknown_and_missing_fields(self):
        for mutate in (lambda rows: rows[0].update(extra=True),
                       lambda rows: rows[0].pop("wireSha256")):
            with self.subTest(mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-header-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 0, mutate)
                self._assert_rejects(spec_path, spec_sha, "HEADER_SCHEMA")

    def test_wrong_seed_role_and_arm_are_rejected(self):
        cases = (
            (0, lambda rows: rows[0]["candidate"].update(rootSeed="+0"),
             "SEED_TRANSPORT"),
            (0, lambda rows: _pad(rows, "pad-b").update(role="POWER"), "NET_ROLE_CENSUS"),
            (1, lambda rows: rows[0]["arm"].update(observed=False), "ARM_IDENTITY"),
        )
        for arm, mutate, code in cases:
            with self.subTest(arm=arm, code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-identity-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, arm, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_duplicate_and_missing_pad_rows_are_rejected(self):
        mutations = (
            (lambda rows: _pad(rows, "pad-b").update(padId="pad-a"), "PAD_CENSUS"),
            (lambda rows: rows.pop(2), "FOOTER_CENSUS"),
        )
        for mutate, code in mutations:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-pads-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 0, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_duplicate_net_root_and_endpoint_mismatches_are_rejected(self):
        cases = (
            (lambda rows: _pad(rows, "pad-b").update(netId="net-two"), "PAD_CENSUS"),
            (lambda rows: _branch(rows, 2)["snapshot"].update(rootPadId="pad-b"),
             "BRANCH_ROOT_REACHED"),
            (lambda rows: _branch(rows, 2)["snapshot"].update(startX=11), "BRANCH_ENDPOINT"),
            (lambda rows: _branch(rows, 2)["snapshot"].update(netId="missing-net"),
             "BRANCH_TERMINAL"),
        )
        for mutate, code in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-endpoint-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 1, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_tiny_exhaustive_choice_oracle_covers_nearest_farthest_and_ties(self):
        # Exhaust all 3^3 placements around a fixed root. This oracle finds the
        # extremal distance first, then independently chooses the lexical ID
        # among equal-distance candidates.
        for xs in itertools.product((-1, 0, 1), repeat=3):
            pads = [dict(padId=pad_id, netId="net-one", componentId="c-" + pad_id,
                         x=x, y=0, role="SIGNAL")
                    for pad_id, x in zip(("pad-a", "pad-b", "pad-c", "pad-d"),
                                         (0,) + xs)]
            branches = []
            for pass_number in range(3):
                reached = ["pad-a"]
                remaining = ["pad-b", "pad-c", "pad-d"]
                choose_far = pass_number == 1
                start = _expected_choice(pads, reached, remaining, farthest=choose_far)
                branches.append({"snapshot": _snapshot(
                    pass_number, start, total_expanded=pass_number + 1, pads=pads)})
            choices, total = reader.audit_choices(branches, pads, arm=3)
            self.assertEqual(total, 3)
            for pass_number, choice in enumerate(choices):
                expected_near = _expected_choice(
                    pads, ["pad-a"], ["pad-b", "pad-c", "pad-d"])
                expected_far = _expected_choice(
                    pads, ["pad-a"], ["pad-b", "pad-c", "pad-d"], farthest=True)
                self.assertEqual(choice["nearest"], expected_near)
                self.assertEqual(choice["farthest"], expected_far)
                self.assertEqual(branches[pass_number]["snapshot"]["startPadId"],
                                 expected_far if pass_number == 1 else expected_near)

    def test_multireached_branch_requires_found_then_updates_reached_set(self):
        pads = _pads()
        sequence = ((0, "pad-b", "FOUND", 1, 1),
                    (0, "pad-c", "NO_PATH", 2, 2),
                    (1, "pad-b", "NO_PATH", 3, 1))
        branches = []
        for pass_number, start, terminal, total, reached_count in sequence:
            branches.append({"snapshot": _snapshot(
                pass_number, start, total_expanded=total, reached_count=reached_count,
                terminal=terminal, pads=pads)})
        choices, total = reader.audit_choices(branches, pads, arm=1)
        self.assertEqual(total, 3)
        self.assertEqual(choices[1]["reached"], ("pad-a", "pad-b"))
        with self.assertRaises(ValueError) as caught:
            reader.audit_choices([branches[0], dict(branches[1], snapshot=dict(
                branches[1]["snapshot"], reachedPadCount=1))], pads, arm=1)
        self.assertEqual(str(caught.exception), "BRANCH_ROOT_REACHED")

    def test_tail_found_is_not_promoted_to_a_published_route(self):
        raw, entry = make_operation(1)
        rows = [json.loads(line) for line in raw.splitlines()]
        _branch(rows, 2)["snapshot"]["terminalReason"] = "FOUND"
        _repair_group_ledgers(rows)
        parsed = reader.validate_operation(
            b"".join(_line(row) for row in rows), entry, "1" * 64, "2" * 64,
            1, RUN_ID, "READER_FIXTURE")
        self.assertEqual(parsed["branches"][-1]["snapshot"]["terminalReason"], "FOUND")
        self.assertIsNone(parsed["result"]["winner"])
        self.assertEqual(parsed["result"]["receipt"]["yieldedOrderings"], 0)
        self.assertEqual(parsed["result"]["route"], "TYPED_REQUEST_REJECTION")

    def test_expansion_total_and_face_census_are_independent_checks(self):
        cases = (
            (lambda rows: _branch(rows, 1)["snapshot"].update(totalExpanded=9),
             "CUMULATIVE_EXPANSIONS"),
            (lambda rows: _branch(rows, 0)["snapshot"].update(expandedBottom=1),
             "BRANCH_EXPANSIONS"),
        )
        for mutate, code in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-expansion-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 1, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_receipt_expansion_sum_must_match_branch_rows(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-receipt-sum-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")

            def overstate_receipt(rows):
                result = next(row for row in rows if row["record"] == "RESULT")
                result["receipt"]["expansions"] = 4
                result["logicalWork"]["expansions"] = 4

            spec_path, spec_sha = _rewrite_operation(spec_path, 1, overstate_receipt)
            self._assert_rejects(spec_path, spec_sha, "RECEIPT_EXPANSION_SUM")

    def test_total_search_limit_must_be_the_final_observed_callback(self):
        def continue_after_total(rows):
            _branch(rows, 1)["snapshot"]["terminalReason"] = "TOTAL_SEARCH_LIMIT"
            result = next(row for row in rows if row["record"] == "RESULT")
            result["receipt"]["termination"] = "TOTAL_SEARCH_LIMIT"
            result["receipt"]["comparisonComplete"] = False

        with tempfile.TemporaryDirectory(prefix="branch-order-total-continuation-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_operation(spec_path, 1, continue_after_total)
            self._assert_rejects(spec_path, spec_sha, "TOTAL_LIMIT_CONTINUATION")

    def test_total_search_limit_receipt_cannot_claim_complete_comparison(self):
        def falsely_complete_total(rows):
            _branch(rows, 2)["snapshot"]["terminalReason"] = "TOTAL_SEARCH_LIMIT"
            result = next(row for row in rows if row["record"] == "RESULT")
            result["receipt"]["termination"] = "TOTAL_SEARCH_LIMIT"

        with tempfile.TemporaryDirectory(prefix="branch-order-total-complete-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_operation(spec_path, 1, falsely_complete_total)
            self._assert_rejects(spec_path, spec_sha, "RECEIPT_COMPARISON")

    def test_missing_ordering_pass_proof_is_rejected(self):
        def remove_last_pass(rows):
            rows[:] = [row for row in rows if not (
                row["record"] == "BRANCH" and row["snapshot"]["pass"] == 2)]
            result = next(row for row in rows if row["record"] == "RESULT")
            result["receipt"]["expansions"] = 2
            result["logicalWork"]["expansions"] = 2

        with tempfile.TemporaryDirectory(prefix="branch-order-missing-pass-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_operation(spec_path, 1, remove_last_pass)
            self._assert_rejects(spec_path, spec_sha, "MISSING_ORDERING_BRANCH_PROOF")

    def test_observed_control_and_causal_prefix_mismatches_are_rejected(self):
        cases = (
            (2, lambda rows: _branch(rows, 1).update(prefixSha256="3" * 64),
             "OBSERVED_CONTROL_PARITY"),
            (3, lambda rows: _branch(rows, 1).update(prefixSha256="4" * 64),
             "DIVERGENCE_CAUSAL_PROOF"),
        )
        for arm, mutate, code in cases:
            with self.subTest(arm=arm), \
                    tempfile.TemporaryDirectory(prefix="branch-order-parity-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, arm, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_pass_zero_treatment_must_preserve_control_prefix(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-pass-zero-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_operation(
                spec_path, 3, lambda rows: _branch(rows, 0).update(prefixSha256="5" * 64))
            self._assert_rejects(spec_path, spec_sha, "PASS0_PARITY")

    def test_p07_step_and_group_time_caps_are_enforced(self):
        cases = (
            (lambda rows: _group(rows, 1).update(p07Steps=reader.LIMITS["p07Steps"] + 1),
             "P07_ACCOUNTING"),
            (lambda rows: _group(rows, 1).update(
                elapsedBeforeOwnLineNanos=reader.LIMITS["groupNanos"] + 1), "GROUP_LIMIT"),
        )
        for mutate, code in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-caps-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 1, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_operation_timing_and_max_group_counters_are_bounded(self):
        mutations = (
            lambda index: index["operations"][0].update(
                elapsedToCloseNanos=reader.LIMITS["operationNanos"] + 1),
            lambda index: index["operations"][0].update(
                maxFullGroupNanos=reader.LIMITS["groupNanos"] + 1),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-operation-time-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_index(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, "FINAL_TIMING_BOUND")

    def test_cleanup_ownership_and_cumulative_cleanup_limit_are_enforced(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-cleanup-owner-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_index(
                spec_path, lambda index: index["operations"][0]["cleanup"].update(
                    ownersReleased=False))
            self._assert_rejects(spec_path, spec_sha, "CLEANUP_PROOF")
        with tempfile.TemporaryDirectory(prefix="branch-order-cleanup-total-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")

            def over_cleanup(index):
                for entry in index["operations"]:
                    entry["cleanup"]["elapsedNanos"] = 30_000_000_001

            spec_path, spec_sha = _rewrite_index(spec_path, over_cleanup)
            self._assert_rejects(spec_path, spec_sha, "CUMULATIVE_CLEANUP_LIMIT")

    def test_boolean_values_cannot_masquerade_as_numeric_counters(self):
        cases = (
            (lambda rows: _group(rows, 1).update(elapsedBeforeOwnLineNanos=True), "GROUP_NUMBER"),
            (lambda rows: _branch(rows, 0).update(ordinal=True), "BRANCH_IDENTITY"),
            (lambda rows: _branch(rows, 0)["snapshot"].update({"pass": True}), "SNAPSHOT_TYPES"),
        )
        for mutate, code in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-bool-number-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 1, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_receipt_requires_final_newline_and_rejects_blank_trailing_line(self):
        for suffix, code, append in ((b"", "RECEIPT_ENCODING_SIZE", False),
                                     (b"\n", "LINE_SIZE_EMPTY", True)):
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-truncation-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                receipt = Path(spec_path).parent / "op-0.jsonl"
                raw = receipt.read_bytes()
                receipt.write_bytes(raw + suffix if append else raw[:-1])
                spec_path, spec_sha = _refresh_fixture(spec_path)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_out_of_order_pad_census_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-record-order-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")

            def swap_pads(rows):
                pad_indices = [i for i, row in enumerate(rows) if row["record"] == "PAD"]
                rows[pad_indices[0]], rows[pad_indices[1]] = (
                    rows[pad_indices[1]], rows[pad_indices[0]])

            spec_path, spec_sha = _rewrite_operation(spec_path, 0, swap_pads)
            self._assert_rejects(spec_path, spec_sha, "PAD_CENSUS")

    def test_unreconciled_receipt_bytes_fail_the_index_pin(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-byte-pin-") as directory:
            spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
            receipt = Path(spec_path).parent / "op-0.jsonl"
            receipt.write_bytes(receipt.read_bytes() + b" ")
            self._assert_rejects(spec_path, spec_sha, "FILE_PIN")

    def test_wrong_spec_digest_fails_before_fixture_validation(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-spec-pin-") as directory:
            spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
            self._assert_rejects(spec_path, "0" * 64, "SPEC_DIGEST")

    def _transport_fixture(self, directory):
        # Unit-test transport predicates after a simulated, already checked
        # source closure. This is never native process/identity evidence.
        spec_path, _ = self._fixture(Path(directory) / 'fixture')
        spec = _load_json(spec_path)
        index_path = spec_path.parent / spec['studyIndex']['path']
        index = _load_json(index_path); index['kind'] = 'TINY_NATIVE'
        _write_json(index_path, index)
        spec['kind'] = 'TINY_NATIVE'
        spec['studyIndex'].update(bytes=index_path.stat().st_size,
                                  sha256=_sha(index_path.read_bytes()))
        cleanup = sum(row['cleanup']['elapsedNanos'] for row in index['operations'])
        elapsed = sum(row['elapsedToCloseNanos'] for row in index['operations'])
        transport = dict(schema='tsj-branch-order-native-transport-v1', kind='TINY_NATIVE',
            sourceManifestSha256=spec['sourceManifest']['sha256'],
            indexSha256=spec['studyIndex']['sha256'], compileExitCode=0, exitCode=0,
            terminationProven=True, sourcesStable=True, processId=42, processStartTicks='12345',
            executableSha256='e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce',
            compilerExecutableSha256='61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64',
            powershellExecutableSha256='60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f',
            isolationModuleSha256='fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c',
            compilerTerminationProven=True, nativeElapsedNanos=elapsed+cleanup+100000,
            cleanupElapsedNanos=cleanup+50000, cleanupStatus='PASS')
        return spec_path, spec, index, transport

    def _check_transport_fixture(self, spec_path, spec, index, transport):
        from unittest.mock import patch
        p = spec_path.parent / 'simulated-transport.json'; _write_json(p, transport)
        spec['transport'] = dict(path=p.name,bytes=p.stat().st_size,sha256=_sha(p.read_bytes()))
        _, expected = _write_spec(spec_path,spec)
        with patch.object(reader,'check_source_manifest',
                          return_value=({'kind':'TINY_NATIVE'},index['wireSha256'],[])):
            return reader.validate_spec(spec_path,expected)

    def test_native_transport_metadata_retains_root_acceptance_boundary(self):
        with tempfile.TemporaryDirectory(prefix='branch-order-transport-unit-') as directory:
            p,spec,index,t = self._transport_fixture(directory)
            result = self._check_transport_fixture(p,spec,index,t)
            self.assertEqual(result['status'],'VALIDATED_NATIVE_REPORT_ONLY_ROOT_ACCEPTANCE_REQUIRED')
            self.assertEqual(result['limitations']['rootNativeAcceptance'],'NOT_GRANTED_BY_READER')

    def test_native_elapsed_includes_full_transport_cleanup_not_only_arm_sum(self):
        with tempfile.TemporaryDirectory(prefix='branch-order-transport-time-') as directory:
            p,spec,index,t = self._transport_fixture(directory)
            t['nativeElapsedNanos'] = sum(row['elapsedToCloseNanos'] +
                row['cleanup']['elapsedNanos'] for row in index['operations'])
            with self.assertRaisesRegex(ValueError,'NATIVE_TIME_CENSUS'):
                self._check_transport_fixture(p,spec,index,t)

    def test_native_transport_rejects_exit_two_false_termination_and_bool_exit(self):
        for field,value in (('exitCode',2),('terminationProven',False),('compileExitCode',False),
                            ('executableSha256','0'*64),('cleanupElapsedNanos',120000000001)):
            with self.subTest(field=field), tempfile.TemporaryDirectory(
                    prefix='branch-order-transport-negative-') as directory:
                p,spec,index,t = self._transport_fixture(directory);t[field]=value
                with self.assertRaisesRegex(ValueError,'NATIVE_TRANSPORT_PROOF'):
                    self._check_transport_fixture(p,spec,index,t)


if __name__ == "__main__":
    unittest.main()
