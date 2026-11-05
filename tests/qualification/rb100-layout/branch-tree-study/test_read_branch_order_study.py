"""Source-only reader fixtures for the P07 branch-order study packet."""

from __future__ import annotations

import hashlib
import itertools
import json
import base64
import copy
import tempfile
import unittest
from pathlib import Path

import read_branch_order_study as reader


REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_WIRE = Path("docs/task-evidence/Q100/outf-boundary-r1/WIRE.md")
PARENT_HEAD = "a1fa56f34e85e6f241993dcebd9e4c7e491ecda3"
RUN_ID = "d7b9d7e5-3cc0-4ed5-9f23-fec562db9035"
PAD_ROWS = (
    ("pad-a", "component-a", 0),
    ("pad-b", "component-b", 10),
    ("pad-c", "component-c", 30),
    ("pad-d", "component-d", 60),
)
PLUS_PAD_ROWS = (
    ("C1.+", "component-c", 0),
    ("R3.+", "component-d", 60),
    ("UAC.IN+", "component-a", 10),
    ("UAC.PRE_L+", "component-b", 30),
)
NET_PRIORITY_PAD_ROWS = (
    ("a-0", "net-alpha", "ca-0", 0, 0),
    ("a-1", "net-alpha", "ca-1", 100, 0),
    ("a-2", "net-alpha", "ca-2", 200, 0),
    ("b-0", "net-beta", "cb-0", 0, 0),
    ("b-1", "net-beta", "cb-1", 500, 0),
    ("b-2", "net-beta", "cb-2", 510, 0),
    ("g-0", "net-gamma", "cg-0", 0, 0),
    ("g-1", "net-gamma", "cg-1", 10, 0),
    ("s-0", "net-single", "cs-0", 10, 10),
    ("w-0", "net-wide", "cw-0", -1_000, -1_000),
    ("w-1", "net-wide", "cw-1", 1_000, 1_000),
)
CANONICAL_NET_IDS = ["net-alpha", "net-beta", "net-gamma", "net-wide", "net-single"]
MINIMUM_PAIR_NET_IDS = ["net-beta", "net-gamma", "net-alpha", "net-wide", "net-single"]
PROMOTED_CANONICAL_NET_IDS = ["net-wide", "net-alpha", "net-beta", "net-gamma", "net-single"]
PROMOTED_MINIMUM_PAIR_NET_IDS = ["net-wide", "net-beta", "net-gamma", "net-alpha", "net-single"]
PRISTINE_BYTES = 16


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _json_bytes(value, *, newline=True) -> bytes:
    raw = json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"),
                     allow_nan=False).encode("ascii")
    return raw + (b"\n" if newline else b"")


def _line(value) -> bytes:
    return _json_bytes(value)


def _pads(group=1, pad_rows=PAD_ROWS):
    rows = []
    for item in pad_rows:
        role = "SIGNAL"
        if len(item) == 3:
            pad_id, component_id, x = item
            net_id, y = "net-one", 0
        elif len(item) == 4:
            pad_id, net_id, component_id, x = item
            y = 0
        elif len(item) == 5:
            pad_id, net_id, component_id, x, y = item
        else:
            pad_id, net_id, component_id, x, y, role = item
        rows.append(dict(record="PAD", group=group, padId=pad_id, netId=net_id,
                         componentId=component_id, x=x, y=y, role=role))
    return rows


def _expected_net_ids(pads, strategy):
    by_net = {}
    for pad in pads:
        by_net.setdefault(pad["netId"], []).append(pad)
    if strategy == "CANONICAL":
        return sorted(by_net, key=lambda net: (-len(by_net[net]), net))
    paired = {}
    for net, rows in by_net.items():
        distances = [
            (abs(left["x"] - right["x"]) + abs(left["y"] - right["y"]),
             left["padId"], right["padId"])
            for index, left in enumerate(sorted(rows, key=lambda row: row["padId"]))
            for right in sorted(rows, key=lambda row: row["padId"])[index + 1:]
        ]
        if distances:
            paired[net] = min(distances)[0]
    return (sorted(paired, key=lambda net: (paired[net], net))
            + sorted(set(by_net) - set(paired)))


def _expected_callback_count(pads, *, yielded_prior=False, promotion_net=None,
                             net_order_ids=None, pass1_net_visits=None,
                             reach_pass1=True):
    by_net = {}
    for pad in pads:
        by_net.setdefault(pad["netId"], []).append(pad)
    canonical = _expected_net_ids(pads, "CANONICAL")
    if yielded_prior:
        count = sum(len(rows) - 1 for rows in by_net.values())
    else:
        failed = promotion_net if promotion_net is not None else canonical[0]
        count = 1 + sum(len(by_net[net]) - 1 for net in canonical[:canonical.index(failed)])
    if reach_pass1:
        visits = (pass1_net_visits if pass1_net_visits is not None else
                  [net for net in (net_order_ids or canonical) if len(by_net[net]) > 1][:1])
        count += (sum(len(by_net[net]) - 1 for net in visits)
                  if pass1_net_visits is not None else len(visits))
        count += 1 if any(len(rows) > 1 for rows in by_net.values()) else 0
    return count


def _snapshot(pass_number, start_pad_id, *, total_expanded, reached_count=1,
              terminal="NO_PATH", branch_expanded=1, expanded_top=1, expanded_bottom=0,
              pads=None, root_pad_id=None, net_id="net-one", prefix_via_count=0,
              relaxed_bound=False):
    rows = _pads() if pads is None else pads
    by_id = {row["padId"]: row for row in rows if row["netId"] == net_id}
    start = by_id[start_pad_id]
    root_id = min(by_id) if root_pad_id is None else root_pad_id
    root = by_id[root_id]
    return {
        "pass": pass_number,
        "startX": start["x"], "startY": start["y"],
        "rootX": root["x"], "rootY": root["y"],
        "gridWidth": 10, "gridHeight": 10, "goalsTop": 2, "goalsBottom": 2,
        "prefixViaCount": prefix_via_count, "reachedPadCount": reached_count,
        "initialManhattanDistance": (abs(start["x"] - root["x"])
                                     + abs(start["y"] - root["y"])),
        "minimumExploredManhattanDistance": 0,
        "branchExpanded": branch_expanded, "totalExpanded": total_expanded,
        "expandedTop": expanded_top, "expandedBottom": expanded_bottom,
        "frontierCount": 0, "bestStateCount": 1,
        "relaxedBoundWorkUnits": 2 if relaxed_bound else 0,
        "relaxedBoundOrdinaryProbes": 1 if relaxed_bound else 0,
        "relaxedBoundViaProbes": 1 if relaxed_bound else 0,
        "relaxedBoundRefreshes": 1 if relaxed_bound else 0,
        "relaxedBoundStarted": relaxed_bound, "relaxedBoundComplete": relaxed_bound,
        "relaxedBoundObservationChecked": relaxed_bound,
        "relaxedBoundLiveLayoutStable": relaxed_bound,
        "relaxedBoundRouterStateStable": relaxed_bound,
        "relaxedBoundRouterEffectsStable": relaxed_bound,
        "netId": net_id, "startPadId": start_pad_id,
        "rootPadId": root_id, "terminalReason": terminal,
    }


def make_operation(arm, *, run_id=RUN_ID, source_manifest_sha256=None,
                   wire_sha256=None, padRows=PAD_ROWS, startIds=None,
                   diagnosticPrefixes=None, netOrderIds=None, yieldedPrior=False,
                   promotionNet=None, pass1NetVisits=None, expectedExpansions=None,
                   reachPass1=True):
    """Return a complete tiny JSONL operation and its post-close index entry."""
    if source_manifest_sha256 is None:
        source_manifest_sha256 = "1" * 64
    if wire_sha256 is None:
        wire_sha256 = "2" * 64
    observed = arm != 0
    diagnostic_prefixes = {} if diagnosticPrefixes is None else dict(diagnosticPrefixes)
    pads = _pads(group=1, pad_rows=padRows)
    net_ids = sorted({row["netId"] for row in pads})
    by_net = {net: sorted((row for row in pads if row["netId"] == net),
                          key=lambda row: row["padId"]) for net in net_ids}
    root_pad_id = min(row["padId"] for row in pads)
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
        "rootSeed": "42", "placementSeed": "5526715536693200536",
        "routingSeed": "4493419427463069983",
        "variant": "TINY", "layoutVersion": 26,
        "dependencyEpoch": "tsj-generation-dependencies-v28",
        "componentCount": len({row["componentId"] for row in pads}),
        "padCount": len(pads), "netCount": len(net_ids),
        "traceWidthProfile": "ROLE_WIDTHS",
    }
    arm_id, api, strategy, net_order_strategy, observed_flag, capture_flag = reader.ARMS[arm]
    header = {
        "record": "HEADER", "group": 0, "schema": reader.SCHEMA,
        "runId": run_id, "mode": "TINY_CANARY", "operation": arm,
        "arm": {"id": arm_id, "api": api, "strategy": strategy,
                "netOrderStrategy": net_order_strategy,
                "observed": observed_flag, "publisherCapture": capture_flag},
        "sourceManifestSha256": source_manifest_sha256,
        "wireSha256": wire_sha256,
        "candidate": candidate,
        "limits": dict(reader.LIMITS),
    }
    branches = []
    branches_by_pass = {0: [], 1: [], 2: []}
    diagnostic_events_by_pass = {0: [], 1: [], 2: []}
    diagnostic_events = []
    prefix_vias_by_pass = diagnostic_prefixes
    canonical_net_ids = _expected_net_ids(pads, "CANONICAL")
    failed_net = (None if yieldedPrior else
                  promotionNet if promotionNet is not None else canonical_net_ids[0])
    base_order_net_ids = _expected_net_ids(pads, net_order_strategy)
    if netOrderIds is not None:
        order_net_ids = list(netOrderIds)
    else:
        order_net_ids = list(base_order_net_ids)
        if failed_net is not None:
            order_net_ids.remove(failed_net)
            order_net_ids.insert(0, failed_net)

    def append_net_branches(pass_number, net_id, *, complete, terminal_last="FOUND"):
        net_pads = by_net[net_id]
        remaining = [row["padId"] for row in net_pads[1:]]
        reached = [net_pads[0]["padId"]]
        branch_count = len(remaining) if complete else min(1, len(remaining))
        for local_index in range(branch_count):
            start_pad_id = _expected_choice(pads, reached, remaining)
            prefix_vias = prefix_vias_by_pass.get(pass_number, 0)
            has_bound = (pass_number in prefix_vias_by_pass and local_index == 0
                         and not diagnostic_events_by_pass[pass_number])
            terminal = terminal_last if local_index == branch_count - 1 else "FOUND"
            snapshot = _snapshot(
                pass_number, start_pad_id, total_expanded=len(branches) + 1,
                reached_count=len(reached), terminal=terminal, pads=pads,
                root_pad_id=net_pads[0]["padId"], net_id=net_id,
                prefix_via_count=prefix_vias, relaxed_bound=has_bound)
            branch = {
                "record": "BRANCH", "operation": arm, "ordinal": len(branches),
                "group": pass_number + 1,
                "prefixSha256": _sha(
                    "same-prefix-pass-{}-branch-{}".format(
                        pass_number, len(branches)).encode("ascii")),
                "prefixBytes": 64, "snapshot": snapshot,
            }
            branches.append(branch)
            branches_by_pass[pass_number].append(branch)
            reached.append(start_pad_id)
            remaining.remove(start_pad_id)
            if has_bound:
                events = []
                for face_index in (0, 1):
                    event = {
                        "record": "DIAGNOSTIC_CLONE", "operation": arm,
                        "ordinal": len(diagnostic_events), "group": pass_number + 1,
                        "pass": pass_number, "netId": net_id,
                        "startPadId": start_pad_id, "rootPadId": net_pads[0]["padId"],
                        "faceIndex": face_index, "prefixViaCount": prefix_vias,
                        "ownerSteps": 1,
                    }
                    diagnostic_events.append(event)
                    diagnostic_events_by_pass[pass_number].append(event)
                    events.append(event)
                return events
        return []

    net_order_row = None
    if observed:
        if yieldedPrior:
            for net_id in canonical_net_ids:
                append_net_branches(0, net_id, complete=True, terminal_last="FOUND")
        else:
            for net_id in canonical_net_ids:
                if net_id == failed_net:
                    append_net_branches(0, net_id, complete=False, terminal_last="NO_PATH")
                    break
                append_net_branches(0, net_id, complete=True, terminal_last="FOUND")

        if reachPass1:
            row = {
                "record": "NET_ORDER", "operation": arm, "group": 2, "pass": 1,
                "strategy": net_order_strategy,
                "promotedNet": None if yieldedPrior else failed_net,
                "completedOrderings": 1, "yieldedOrderings": 1 if yieldedPrior else 0,
                "prefixSha256": context["pristineSha256"],
                "prefixBytes": context["pristineBytes"], "netIds": order_net_ids,
            }
            net_order_row = row

            paired_pass_one = [net for net in order_net_ids if len(by_net[net]) > 1]
            visits = pass1NetVisits if pass1NetVisits is not None else paired_pass_one[:1]
            for net_id in visits:
                append_net_branches(1, net_id,
                                    complete=pass1NetVisits is not None or len(visits) > 1)
            pass_two_net = next((net for net in canonical_net_ids if len(by_net[net]) > 1), None)
            if pass_two_net is not None:
                append_net_branches(2, pass_two_net, complete=False, terminal_last="NO_PATH")

        if startIds is not None:
            # A narrow fixture hook for endpoint/tie tests; it only adjusts the
            # first branch in each pass and never defines an expected order.
            for pass_number, start_id in enumerate(startIds):
                if branches_by_pass[pass_number]:
                    branch = branches_by_pass[pass_number][0]
                    net_id = branch["snapshot"]["netId"]
                    net_pads = by_net[net_id]
                    branch["snapshot"]["startPadId"] = start_id
                    branch["snapshot"]["startX"] = next(p["x"] for p in net_pads
                                                         if p["padId"] == start_id)
                    branch["snapshot"]["startY"] = next(p["y"] for p in net_pads
                                                         if p["padId"] == start_id)

    expansion_count = (len(branches) if observed else
                       (expectedExpansions if expectedExpansions is not None else
                        _expected_callback_count(pads, reach_pass1=reachPass1)))
    started_orderings = 3 if reachPass1 else 1

    receipt = {
        "attempt": 0, "startedOrderings": started_orderings,
        "completedOrderings": started_orderings,
        "yieldedOrderings": 0, "finalizedOrderings": 0,
        "selectedOrdering": -1, "expansions": expansion_count,
        "guidanceStarted": True, "comparisonComplete": reachPass1,
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
                        "p07OwnerSteps": 30 + len(diagnostic_events),
                        "diagnosticCloneSteps": len(diagnostic_events),
                        "routingOwnerSteps": 30, "expansions": expansion_count},
        "boundary": {"requestCount": 1, "outcomeCount": 1,
                     "routeRetired": True, "candidateRetired": True,
                     "generatorComplete": False, "eventObserved": observed,
                     "successorAdvanceCalls": 0},
    }

    # Census, clone observations, and callbacks belong to ADVANCE groups. The
    # plain control keeps the three owner slices but emits neither clone nor BRANCH rows.
    batches = [
        [header],
        pads + diagnostic_events_by_pass[0] + branches_by_pass[0],
        ([net_order_row] if net_order_row is not None else [])
        + diagnostic_events_by_pass[1] + branches_by_pass[1],
        diagnostic_events_by_pass[2] + branches_by_pass[2],
        [result],
    ]
    phases = ("SETUP", "ADVANCE", "ADVANCE", "ADVANCE", "REPORT")
    rows = []
    for ordinal, batch in enumerate(batches):
        rows.extend(batch)
        branches_here = [row for row in batch if row["record"] == "BRANCH"]
        pads_here = [row for row in batch if row["record"] == "PAD"]
        clones_here = [row for row in batch if row["record"] == "DIAGNOSTIC_CLONE"]
        is_advance = phases[ordinal] == "ADVANCE"
        hash_bytes = (sum(row["prefixBytes"] for row in branches_here)
                      + sum(row["prefixBytes"] for row in batch
                            if row["record"] == "NET_ORDER"))
        if ordinal == 1:
            hash_bytes += len(reader.pad_bytes(pads)) + context["pristineBytes"]
        rows.append({
            "record": "GROUP", "operation": arm, "ordinal": ordinal,
            "phase": phases[ordinal], "elapsedBeforeOwnLineNanos": 100,
            "generatorAdvances": 1 if is_advance else 0,
            "p07Slices": 1 if is_advance else 0,
            "p07Steps": 10 + sum(row["ownerSteps"] for row in clones_here)
            if is_advance else None,
            "diagnosticCloneSteps": sum(row["ownerSteps"] for row in clones_here),
            "accounting": "MEASURED" if is_advance else "NOT_APPLICABLE",
            "observerChecks": 1 if is_advance else 0,
            "callbacks": len(branches_here), "censusRows": len(pads_here),
            "netOrderRows": sum(row["record"] == "NET_ORDER" for row in batch),
            "hashInputBytes": hash_bytes,
            "serializedBeforeOwnLineBytes": sum(len(_line(row)) for row in batch),
            "publisherChunkRows": 0, "publisherResultRows": 0,
            "diagnosticCopiedEntries": 0, "diagnosticSerializedBytes": 0,
            "diagnosticHashBytes": 0, "diagnosticUnits": 0,
        })
    rows.append({"record": "FOOTER", "group": 4, "operation": arm,
                 "complete": True, "padCount": len(pads), "branchCount": len(branches),
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


def write_fixture(directory, repositoryRoot=REPOSITORY_ROOT, wirePath=None,
                  padRows=PAD_ROWS, startIds=None, diagnosticPrefixesByArm=None,
                  netOrderIdsByArm=None, yieldedPriorByArm=None,
                  promotionNetsByArm=None, pass1NetVisitsByArm=None,
                  reachPass1ByArm=None, publisherOutcome="PUBLISHED",
                  publisherVia="NEW", publisherNonPadGoal=False):
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
    operation_rows = {}
    reference_reach_pass1 = (reachPass1ByArm or {}).get(1, True)
    expected_plain_expansions = _expected_callback_count(
        _pads(group=1, pad_rows=padRows),
        yielded_prior=(yieldedPriorByArm or {}).get(1, False),
        promotion_net=(promotionNetsByArm or {}).get(1),
        net_order_ids=(netOrderIdsByArm or {}).get(1),
        pass1_net_visits=(pass1NetVisitsByArm or {}).get(1))
    if (reachPass1ByArm or {}).get(1) is False:
        expected_plain_expansions = _expected_callback_count(
            _pads(group=1, pad_rows=padRows),
            yielded_prior=(yieldedPriorByArm or {}).get(1, False),
            promotion_net=(promotionNetsByArm or {}).get(1), reach_pass1=False)
    for arm in range(5):
        option_arm = 3 if arm == 4 else arm
        def arm_option(mapping, default=None):
            mapping = mapping or {}
            return mapping.get(arm, mapping.get(option_arm, default))
        raw, entry = make_operation(arm, source_manifest_sha256=source_sha,
                                    wire_sha256=wire_sha, padRows=padRows,
                                    startIds=startIds,
                                    diagnosticPrefixes=arm_option(diagnosticPrefixesByArm),
                                    netOrderIds=arm_option(netOrderIdsByArm),
                                    yieldedPrior=arm_option(yieldedPriorByArm, False),
                                    promotionNet=arm_option(promotionNetsByArm),
                                    pass1NetVisits=arm_option(pass1NetVisitsByArm),
                                    expectedExpansions=expected_plain_expansions,
                                    reachPass1=arm_option(reachPass1ByArm,
                                                          reference_reach_pass1))
        operation_rows[arm] = [json.loads(line) for line in raw.splitlines()]
        operation_entries.append(entry)

    capture_rows = operation_rows[4]
    has_pass_one_found = any(row["record"] == "BRANCH"
                             and row["snapshot"]["pass"] == 1
                             and row["snapshot"]["terminalReason"] == "FOUND"
                             for row in capture_rows)
    if has_pass_one_found:
        target, _grid = _make_publisher_target(
            capture_rows, outcome=publisherOutcome, via=publisherVia,
            nonPadGoal=publisherNonPadGoal)
        following = next((row for row in capture_rows if row["record"] == "BRANCH"
                          and row["snapshot"]["pass"] == 1
                          and row["ordinal"] > target["branch"]["ordinal"]), None)
        if following is not None:
            following["prefixSha256"] = target["after"]["geometrySha256"]
            following["prefixBytes"] = target["after"]["geometryBytes"]
            following["snapshot"]["prefixViaCount"] = len(target["after"]["holes"])
        _install_publisher_capture(capture_rows, target)
        # The fifth arm adds observation only. Give its no-capture control the
        # same full source vectors, grid, and selected branch prefix.
        treatment_branches = [row for row in capture_rows if row["record"] == "BRANCH"]
        control_branches = [row for row in operation_rows[3] if row["record"] == "BRANCH"]
        if len(treatment_branches) == len(control_branches):
            for control, treatment in zip(control_branches, treatment_branches):
                control["snapshot"]["gridWidth"] = treatment["snapshot"]["gridWidth"]
                control["snapshot"]["gridHeight"] = treatment["snapshot"]["gridHeight"]
                control["snapshot"]["prefixViaCount"] = treatment["snapshot"]["prefixViaCount"]
                control["prefixSha256"] = treatment["prefixSha256"]
                control["prefixBytes"] = treatment["prefixBytes"]

    for arm in range(5):
        rows = operation_rows[arm]
        _repair_group_ledgers(rows)
        raw = b"".join(_line(row) for row in rows)
        (base / operation_entries[arm]["receipt"]["path"]).write_bytes(raw)
        operation_entries[arm]["receipt"] = {
            "path": "op-{}.jsonl".format(arm), "bytes": len(raw), "sha256": _sha(raw)}
        operation_entries[arm]["elapsedToCloseNanos"] = max(
            1000, 100 * sum(row["record"] == "GROUP" for row in rows))
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


def _event(rows, ordinal):
    return next(row for row in rows if row["record"] == "DIAGNOSTIC_CLONE"
                and row["ordinal"] == ordinal)


def _net_order(rows):
    return next(row for row in rows if row["record"] == "NET_ORDER")


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
        row["netOrderRows"] = sum(item["record"] == "NET_ORDER" for item in pending)
        chunks = [item for item in pending if item["record"] == "PUB_CHUNK"]
        pub_results = [item for item in pending if item["record"] == "PUB_RESULT"]
        row["publisherChunkRows"] = len(chunks)
        row["publisherResultRows"] = len(pub_results)
        row["diagnosticCloneSteps"] = sum(item.get("ownerSteps", 0)
                                          for item in pending
                                          if item["record"] == "DIAGNOSTIC_CLONE")
        if row["phase"] == "CAPTURE":
            row["diagnosticSerializedBytes"] = (
                sum(len(base64.b64decode(item["data"], validate=True)) for item in chunks)
                + sum(len(_line(item)) for item in pending
                      if item["record"] in ("PUB_CHUNK", "PUB_RESULT")))
        row["diagnosticUnits"] = (row["diagnosticCopiedEntries"]
                                  + row["diagnosticSerializedBytes"]
                                  + row["diagnosticHashBytes"])
        required_hash = sum(item["prefixBytes"] for item in pending
                            if item["record"] in ("BRANCH", "NET_ORDER"))
        row["hashInputBytes"] = max(row["hashInputBytes"],
                                    required_hash + row["diagnosticHashBytes"])
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


def _publisher_target(spec_path):
    rows = _read_rows(spec_path, 4)
    payload = b"".join(base64.b64decode(row["data"], validate=True)
                        for row in rows if row["record"] == "PUB_CHUNK")
    return json.loads(payload.decode("utf-8"))


def _repack_publisher_target(rows, target):
    first_chunk = next(i for i, row in enumerate(rows) if row["record"] == "PUB_CHUNK")
    result = copy.deepcopy(next(row for row in rows if row["record"] == "RESULT"))
    report = copy.deepcopy(next(row for row in rows
                                if row["record"] == "GROUP" and row["phase"] == "REPORT"))
    footer = copy.deepcopy(rows[-1])
    prefix = copy.deepcopy(rows[:first_chunk])
    report_group = sum(row["record"] == "GROUP" for row in prefix)
    result["group"] = report_group
    report["ordinal"] = report_group
    footer.update(group=report_group, groupCount=report_group + 1)
    rebuilt = prefix + [result, report, footer]
    _install_publisher_capture(rebuilt, target)
    _repair_group_ledgers(rebuilt)
    return rebuilt


def _rewrite_capture_target(spec_path, mutate, *, reseal_geometry=True):
    spec_path = Path(spec_path)
    rows = _read_rows(spec_path, 4)
    target = _publisher_target(spec_path)
    mutate(target)
    if reseal_geometry:
        for name in ("before", "after"):
            old = target[name]
            target[name] = _fixture_geometry(old["staticPrefix"], old["traces"], old["holes"])
        source_branch = next(row for row in rows
                             if row["record"] == "BRANCH"
                             and row["ordinal"] == target["branch"]["ordinal"])
        source_branch["prefixSha256"] = target["before"]["geometrySha256"]
        source_branch["prefixBytes"] = target["before"]["geometryBytes"]
    rebuilt = _repack_publisher_target(rows, target)
    raw = b"".join(_line(row) for row in rebuilt)
    (spec_path.parent / "op-4.jsonl").write_bytes(raw)
    spec = _load_json(spec_path)
    index_path = spec_path.parent / spec["studyIndex"]["path"]
    index = _load_json(index_path)
    index["operations"][4]["elapsedToCloseNanos"] = max(
        1000, 100 * sum(row["record"] == "GROUP" for row in rebuilt))
    _write_json(index_path, index)
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


def _expected_choice(pads, reached, remaining):
    by_id = {row["padId"]: row for row in pads}
    distances = {
        candidate: min(abs(by_id[candidate]["x"] - by_id[known]["x"])
                       + abs(by_id[candidate]["y"] - by_id[known]["y"])
                       for known in reached)
        for candidate in remaining
    }
    boundary = min(distances.values())
    return min(candidate for candidate, distance in distances.items()
               if distance == boundary)


def _fixture_field(value):
    if value is None:
        return "-1:"
    return "{}:{}".format(len(value.encode("utf-16-le")) // 2, value)


def _fixture_fingerprint(prefix, traces, holes):
    def text(value):
        return "null" if value is None else value

    def java_order(value):
        raw = value.encode("utf-16-be")
        return tuple(int.from_bytes(raw[i:i + 2], "big") for i in range(0, len(raw), 2))

    ordered_traces = sorted(
        traces,
        key=lambda row: (java_order(row[1]), java_order(text(row[2])),
                         java_order(text(row[3])), row[4], row[6],
                         java_order(text(row[0]))))
    output = [prefix]
    for source, net, start, end, layer, exposure, width, xs, ys in ordered_traces:
        output.append("T:{}{}:{}:{}:{}-{}@".format(
            _fixture_field(source), "TOP" if layer == 0 else "BOTTOM",
            exposure, net, text(start), text(end)))
        if width != 9:
            output.append("W{}:".format(width))
        output.extend("{},{};".format(x, y) for x, y in zip(xs, ys))
    for hole_id, kind, net, x, y, drill, land, exposure in sorted(
            holes, key=lambda row: java_order(row[0])):
        output.append("H:{}{}{}{},{},{},{},{}".format(
            _fixture_field(hole_id), kind, _fixture_field(net), x, y, drill, land, exposure))
    return "".join(output)


def _fixture_geometry(prefix, traces, holes):
    fingerprint = _fixture_fingerprint(prefix, traces, holes)
    raw = fingerprint.encode("utf-8")
    return dict(staticPrefix=prefix, traces=copy.deepcopy(traces),
                holes=copy.deepcopy(holes), geometrySha256=_sha(raw),
                geometryBytes=len(raw))


def _fixture_compact(points):
    compact = []
    for point in points:
        if len(compact) >= 2:
            a, b = compact[-2], compact[-1]
            if (a[0] == b[0] == point[0]) or (a[1] == b[1] == point[1]):
                compact.pop()
        compact.append(point)
    return compact


def _fixture_path_points(start, end, rejected=False):
    if rejected:
        assert start[1] == end[1] and start[0] - end[0] == 10
        return [(start[0], start[1]), (start[0] + 10, start[1]),
                (start[0] + 20, start[1]), (start[0] + 10, start[1]),
                (start[0], start[1]), (end[0], end[1])]
    points = [(start[0], start[1])]
    x, y = start
    while x != end[0]:
        x += 10 if end[0] > x else -10
        points.append((x, y))
    while y != end[1]:
        y += 10 if end[1] > y else -10
        points.append((x, y))
    return points


def _fixture_path_nodes(points, width, height, origin_x, origin_y, via_index=None):
    expanded = []
    active_layer = 0
    for index, point in enumerate(points):
        expanded.append((point[0], point[1], active_layer))
        if via_index == index:
            active_layer = 1 - active_layer
            expanded.append((point[0], point[1], active_layer))
    nodes = []
    cost = 0
    transitions = 0
    previous_direction = 4
    for index, (x, y, layer) in enumerate(expanded):
        cell = ((y - origin_y) // 10) * width + (x - origin_x) // 10
        if index == 0:
            direction = 4
        else:
            previous = expanded[index - 1]
            if previous[2] != layer:
                direction = 4
                transitions += 1
                cost += 60
            else:
                dx, dy = x - previous[0], y - previous[1]
                direction = {(0, -10): 0, (10, 0): 1, (0, 10): 2,
                             (-10, 0): 3}[(dx, dy)]
                cost += 10 + (35 if previous_direction not in (4, direction) else 0)
            previous_direction = direction
        key_value = (((transitions * 2 + layer) * 5 + direction) *
                     (width * height) + cell)
        nodes.append([cell, layer, x, y, direction, transitions, cost,
                      abs(points[-1][0] - x) + abs(points[-1][1] - y),
                      index, key_value, -1 if index == 0 else index - 1])
    return nodes


def _fixture_publisher_delta(nodes, branch, pads_by_id, net_roles, before_holes,
                             branch_number):
    net = branch["netId"]
    width = {"SIGNAL": 9, "CONTROL": 9, "SUPPLY": 11,
             "RETURN": 11, "HIGH_CURRENT": 13}[net_roles[net]]
    traces, holes = [], []
    known_holes = copy.deepcopy(before_holes)
    segments = 0
    piece_number = 0
    first = 0
    while first < len(nodes):
        end = first + 1
        while end < len(nodes) and nodes[end][1] == nodes[end - 1][1]:
            end += 1
        last = end - 1
        piece = nodes[first:end]
        if len(piece) > 1:
            compact = _fixture_compact([(node[2], node[3]) for node in piece])
            end_pad = None
            if last == len(nodes) - 1:
                end_pad = branch["foundTerminalPadId"]
                if end_pad is None and (nodes[last][2], nodes[last][3]) == (
                        pads_by_id[branch["rootPadId"]]["x"],
                        pads_by_id[branch["rootPadId"]]["y"]):
                    end_pad = branch["rootPadId"]
            traces.append([
                "p07/{}/{}/{}".format(net, branch_number, piece_number), net,
                branch["startPadId"] if first == 0 else None, end_pad, piece[0][1],
                "EXPOSED", width, [point[0] for point in compact],
                [point[1] for point in compact],
            ])
            segments += len(piece) - 1
            piece_number += 1
        if end < len(nodes):
            x, y = nodes[last][2], nodes[last][3]
            existing = [row for row in known_holes if row[3:5] == [x, y]]
            if not existing:
                row = ["p07-via/{}/{}/{}".format(net, branch_number, piece_number),
                       "VIA", net, x, y, 2, 6, "EXPOSED"]
                holes.append(row)
                known_holes.append(row)
            else:
                assert all(row[1] in ("VIA", "PLATED") and row[2] == net
                           for row in existing)
        first = end
    return traces, holes, segments


def _install_publisher_capture(rows, target):
    """Insert independently authored target chunks and CAPTURE ledgers."""
    result_index = next(i for i, row in enumerate(rows) if row["record"] == "RESULT")
    report_index = next(i for i, row in enumerate(rows)
                        if row["record"] == "GROUP" and row["phase"] == "REPORT")
    footer = copy.deepcopy(rows[-1])
    result = copy.deepcopy(rows[result_index])
    report = copy.deepcopy(rows[report_index])
    prefix = copy.deepcopy(rows[:result_index])
    checkpoints = target["checkpoints"]
    checkpoint_sequences = {}
    for name in ("found", "publisherSeen", "publisherTerminal", "orderingReady", "orderingResult"):
        checkpoint = checkpoints.get(name)
        if checkpoint is not None:
            checkpoint_sequences.setdefault(checkpoint["group"], set()).add(
                checkpoint["sequence"])
    for ordinal, sequences in checkpoint_sequences.items():
        group_row = next(row for row in prefix if row["record"] == "GROUP"
                         and row["ordinal"] == ordinal)
        group_row["observerChecks"] = max(group_row["observerChecks"], len(sequences))
    found_group = checkpoints["found"]["group"]
    copied_path_entries = 12 * len(target["path"]["nodes"])
    def copied_geometry_entries(value):
        return (sum(7 + len(trace[7]) + len(trace[8]) for trace in value["traces"])
                + 8 * len(value["holes"]))
    synchronous_entries = {
        found_group: len(target["branch"]["reachedPadIds"]) + 24
        + copied_geometry_entries(target["before"])}
    terminal_group = checkpoints["publisherTerminal"]["group"]
    after_fingerprint = _fixture_fingerprint(target["after"]["staticPrefix"],
                                           target["after"]["traces"],
                                           target["after"]["holes"])
    synchronous_entries[terminal_group] = (
        synchronous_entries.get(terminal_group, 0)
        + copied_geometry_entries(target["after"])
        + len(after_fingerprint.encode("utf-16-le")) // 2)
    for ordinal, entries in synchronous_entries.items():
        assert entries <= reader.LIMITS["snapshotEntries"]
        group_row = next(row for row in prefix if row["record"] == "GROUP"
                         and row["ordinal"] == ordinal)
        group_row["diagnosticCopiedEntries"] = entries
        group_row["diagnosticUnits"] = entries
    payload = _json_bytes(target, newline=False)
    chunks = [payload[offset:offset + reader.LIMITS["publisherChunkBytes"]]
              for offset in range(0, len(payload), reader.LIMITS["publisherChunkBytes"])]
    assert chunks and len(payload) <= reader.LIMITS["publisherPayloadBytes"]
    first_capture_group = sum(row["record"] == "GROUP" for row in prefix)
    capture_tail = []
    geometry_bytes = (target["before"]["geometryBytes"]
                      + target["after"]["geometryBytes"])
    for index, chunk in enumerate(chunks):
        group_ordinal = first_capture_group + index
        pub_chunk = {
            "record": "PUB_CHUNK", "operation": 4, "group": group_ordinal,
            "targetId": reader.PUBLISHER_TARGET_ID, "index": index,
            "offset": index * reader.LIMITS["publisherChunkBytes"],
            "bytes": len(chunk), "data": base64.b64encode(chunk).decode("ascii"),
        }
        batch = [pub_chunk]
        if index == len(chunks) - 1:
            batch.append({
                "record": "PUB_RESULT", "operation": 4, "group": group_ordinal,
                "targetId": reader.PUBLISHER_TARGET_ID, "payloadBytes": len(payload),
                "payloadSha256": _sha(payload), "chunkCount": len(chunks),
            })
        line_bytes = sum(len(_line(row)) for row in batch)
        hash_bytes = len(chunk) + len(_line(pub_chunk))
        if index == len(chunks) - 1:
            hash_bytes += geometry_bytes + len(_line(batch[-1]))
        copied_entries = len(chunk) + (copied_path_entries if index == 0 else 0)
        serialized_bytes = len(chunk) + line_bytes
        group = {
            "record": "GROUP", "operation": 4, "ordinal": group_ordinal,
            "phase": "CAPTURE", "elapsedBeforeOwnLineNanos": 100,
            "generatorAdvances": 0, "p07Slices": 0, "p07Steps": None,
            "diagnosticCloneSteps": 0, "accounting": "NOT_APPLICABLE",
            "observerChecks": 0, "callbacks": 0, "censusRows": 0,
            "netOrderRows": 0, "hashInputBytes": hash_bytes,
            "serializedBeforeOwnLineBytes": line_bytes,
            "publisherChunkRows": 1,
            "publisherResultRows": 1 if len(batch) == 2 else 0,
            "diagnosticCopiedEntries": copied_entries,
            "diagnosticSerializedBytes": serialized_bytes,
            "diagnosticHashBytes": hash_bytes,
            "diagnosticUnits": copied_entries + serialized_bytes + hash_bytes,
        }
        assert group["diagnosticUnits"] <= 32768
        capture_tail.extend(batch)
        capture_tail.append(group)
    report_group = first_capture_group + len(chunks)
    result["group"] = report_group
    report["ordinal"] = report_group
    footer.update(group=report_group,
                  groupCount=report_group + 1,
                  branchCount=sum(row["record"] == "BRANCH" for row in prefix))
    rows[:] = prefix + capture_tail + [result, report, footer]


def _make_publisher_target(rows, *, outcome="PUBLISHED", via="NEW", nonPadGoal=False):
    header = rows[0]
    pads = [row for row in rows if row["record"] == "PAD"]
    branches = [row for row in rows if row["record"] == "BRANCH"]
    result = next(row for row in rows if row["record"] == "RESULT")
    net_order = next(row for row in rows if row["record"] == "NET_ORDER")
    selected = next(row for row in branches
                    if row["snapshot"]["pass"] == 1
                    and row["snapshot"]["terminalReason"] == "FOUND")
    snapshot = selected["snapshot"]
    pads_by_id = {row["padId"]: row for row in pads}
    net_roles = {row["netId"]: row["role"] for row in pads}
    start_pad = pads_by_id[snapshot["startPadId"]]
    root_pad = pads_by_id[snapshot["rootPadId"]]
    terminal_id = None if nonPadGoal else snapshot["rootPadId"]
    endpoint = ((start_pad["x"] + 10, start_pad["y"]) if nonPadGoal
                else (root_pad["x"], root_pad["y"]))
    points = _fixture_path_points(
        (start_pad["x"], start_pad["y"]), endpoint, rejected=(outcome == "REJECTED"))
    via_index = None
    before_holes = []
    prefix_traces = []
    if via != "NONE" and not nonPadGoal and outcome == "PUBLISHED" and len(points) > 1:
        via_index = max(1, len(points) // 2)
        if via == "REUSE":
            vx, vy = points[via_index]
            before_holes.append(["prior-compatible-via", "VIA", snapshot["netId"],
                                 vx, vy, 2, 6, "EXPOSED"])
    if nonPadGoal:
        prefix_traces.append([
            "prefix/trunk", snapshot["netId"], None, None, 0, "EXPOSED", 9,
            [min(start_pad["x"], endpoint[0]), max(start_pad["x"], endpoint[0])],
            [start_pad["y"], start_pad["y"]],
        ])
    static_prefix = "fixture-static-prefix|"
    before = _fixture_geometry(static_prefix, prefix_traces, before_holes)
    # The native snapshot's historical name counts the whole hole array.
    snapshot["prefixViaCount"] = len(before_holes)
    selected["prefixSha256"] = before["geometrySha256"]
    selected["prefixBytes"] = before["geometryBytes"]
    branch_number = sum(row["snapshot"]["pass"] == 1 and
                        row["ordinal"] <= selected["ordinal"] for row in branches)
    min_x = min([row["x"] for row in pads] + [point[0] for point in points])
    max_x = max([row["x"] for row in pads] + [point[0] for point in points])
    min_y = min([row["y"] for row in pads] + [point[1] for point in points])
    max_y = max([row["y"] for row in pads] + [point[1] for point in points])
    assert all(value % 10 == 0 for value in (min_x, max_x, min_y, max_y))
    grid_width = (max_x - min_x) // 10 + 1
    grid_height = (max_y - min_y) // 10 + 1
    cells = grid_width * grid_height
    assert cells <= reader.LIMITS["cells"]
    snapshot["gridWidth"] = grid_width
    snapshot["gridHeight"] = grid_height
    nodes = _fixture_path_nodes(points, grid_width, grid_height, min_x, min_y, via_index)
    reached_pad_ids = {snapshot["rootPadId"]}
    reached_pad_ids.update(
        row["snapshot"]["startPadId"] for row in branches
        if row["snapshot"]["pass"] == 1
        and row["snapshot"]["netId"] == snapshot["netId"]
        and row["ordinal"] < selected["ordinal"])
    target_branch = dict(
        ordinal=selected["ordinal"],
        netVectorPosition=net_order["netIds"].index(snapshot["netId"]),
        netId=snapshot["netId"], startPadId=snapshot["startPadId"],
        rootPadId=snapshot["rootPadId"],
        reachedPadIds=sorted(reached_pad_ids),
        foundTerminalPadId=terminal_id,
        branchRecordGroup=selected["group"])
    target = {
        "schema": "p07-publisher-target-v1",
        "context": {
            "sourceManifestSha256": header["sourceManifestSha256"], "operation": 4,
            "rootSeed": header["candidate"]["rootSeed"],
            "placementSeed": header["candidate"]["placementSeed"],
            "routingSeed": header["candidate"]["routingSeed"],
            "attempt": result["context"]["selectedAttempt"], "request": 0, "pass": 1,
            "placementSha256": result["context"]["pristineSha256"],
        },
        "objects": {"generator": 1, "route": 2, "search": 3, "branch": 4,
                    "layout": 5, "publisher": 6,
                    "found": 10000 + len(nodes) - 1,
                    "terminal": None if terminal_id is None else 7},
        "branch": target_branch,
        "grid": {"width": grid_width, "height": grid_height, "cells": cells,
                 "originX": min_x, "originY": min_y, "pitch": 10,
                 "primaryLayer": 0, "maximumVias": 48},
        "checkpoints": {
            "found": {"group": 2, "sequence": 1},
            "publisherSeen": {"group": 2, "sequence": 2},
            "publisherTerminal": {"group": 3, "sequence": 3},
            "orderingReady": None if outcome == "REJECTED"
                               else {"group": 3, "sequence": 4},
            "orderingResult": {"group": 3, "sequence": 4 if outcome == "REJECTED" else 5},
        },
        "path": {"encoding":
                 "cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow",
                 "nodes": nodes,
                 "nodeTokens": [10000 + index for index in range(len(nodes))],
                 "foundGoal": True},
        "publisher": {},
        "ordering": {},
        "before": before,
        "after": None,
        "integrity": {"ownerBindingVerified": True, "immutablePathVerified": True,
                      "staticGeometryEqual": True, "prefixRowsConserved": True,
                      "sourceReferencesReleased": True},
        "limitations": {"routeAdoption": False, "layoutQualification": False,
                        "playable100": 0, "fullPhysicalProof": "NOT_RUN"},
    }
    traces, new_holes, segments = _fixture_publisher_delta(
        nodes, target_branch, pads_by_id, net_roles, before_holes, branch_number)
    after_traces = prefix_traces + traces
    after_holes = before_holes + new_holes
    target["after"] = _fixture_geometry(static_prefix, after_traces, after_holes)
    length = bends = 0
    last_direction = 4
    for previous, current in zip(nodes, nodes[1:]):
        if previous[1] != current[1]:
            last_direction = 4
        else:
            length += 10
            if last_direction not in (4, current[4]):
                bends += 1
            last_direction = current[4]
    direct = abs(start_pad["x"] - nodes[-1][2]) + abs(start_pad["y"] - nodes[-1][3])
    if outcome == "REJECTED":
        target["after"] = copy.deepcopy(before)
        target["publisher"] = {
            "outcome": "REJECTED", "stage": 1, "qualityIndex": len(nodes),
            "reversedSize": len(nodes), "storedLength": length,
            "storedBends": bends, "storedLastDirection": last_direction,
            "segments": 0, "reason": snapshot["netId"] + ":BRANCH_QUALITY_LIMIT",
            "sourceHandoff": False, "startReachedAfter": False,
        }
        target["ordering"] = {
            "routerStatus": "ROUTER_REJECTED",
            "routerReason": snapshot["netId"] + ":BRANCH_QUALITY_LIMIT",
            "readyLayoutToken": None, "readyPass": None,
            "finalizerStatus": "NOT_REACHED", "finalizedBefore": 0,
            "finalizedAfter": 0, "lastFailureDetail": None,
        }
    else:
        target["publisher"] = {
            "outcome": "PUBLISHED", "stage": 6, "qualityIndex": len(nodes),
            "reversedSize": len(nodes), "storedLength": length,
            "storedBends": bends, "storedLastDirection": last_direction,
            "segments": segments, "reason": None,
            "sourceHandoff": True, "startReachedAfter": True,
        }
        target["ordering"] = {
            "routerStatus": "READY", "routerReason": "net-one:NO_PATH",
            "readyLayoutToken": 5, "readyPass": 1,
            "finalizerStatus": "REJECTED", "finalizedBefore": 0,
            "finalizedAfter": 0, "lastFailureDetail": "fixture finalizer rejected",
        }
    assert target["path"]["nodeTokens"][-1] == target["objects"]["found"]
    assert (target["objects"]["terminal"] is None) == (terminal_id is None)
    assert any((direct == 0, length > direct * 3, bends > 16)) == (outcome == "REJECTED")
    return target, (grid_width, grid_height)


class BranchOrderReaderTests(unittest.TestCase):
    def _fixture(self, directory):
        return write_fixture(directory)

    def _net_order_fixture(self, directory, **overrides):
        options = dict(
            netOrderIdsByArm={1: PROMOTED_CANONICAL_NET_IDS,
                              2: PROMOTED_CANONICAL_NET_IDS,
                              3: PROMOTED_MINIMUM_PAIR_NET_IDS},
            promotionNetsByArm={1: "net-wide", 2: "net-wide", 3: "net-wide"},
            pass1NetVisitsByArm={
                1: ["net-wide", "net-alpha", "net-beta", "net-gamma"],
                2: ["net-wide", "net-alpha", "net-beta", "net-gamma"],
                3: ["net-wide", "net-beta", "net-gamma", "net-alpha"],
            })
        options.update(overrides)
        return write_fixture(Path(directory) / "fixture", padRows=NET_PRIORITY_PAD_ROWS,
                             **options)

    def _assert_rejects(self, spec_path, spec_sha, code):
        with self.assertRaises(ValueError) as caught:
            reader.validate_spec(spec_path, spec_sha)
        self.assertEqual(str(caught.exception), code)

    def test_literal_java_utf16_null_sort_width_fingerprint_preimage_and_sha(self):
        # Literal native-format oracle: Java compares surrogate code units, so
        # U+1F600 sorts before U+E000 despite Python's code-point order.
        traces = [
            ["\ue000", "net-one", None, None, 0, "EXPOSED", 9, [0, 10], [0, 0]],
            ["w", "net-one", None, None, 0, "COVERED", 11, [0, 10], [0, 0]],
            ["\U0001f600", "net-one", None, None, 0, "EXPOSED", 9, [0, 10], [0, 0]],
            [None, "net-one", None, None, 0, "EXPOSED", 9, [0, 10], [0, 0]],
        ]
        holes = [
            ["\ue000", "VIA", "net-one", 30, 0, 2, 6, "COVERED"],
            ["\U0001f600", "NON_PLATED", None, 20, 0, 2, 2, "EXPOSED"],
        ]
        expected = (
            "P\U0001f600|T:-1:TOP:EXPOSED:net-one:null-null@0,0;10,0;"
            "T:2:\U0001f600TOP:EXPOSED:net-one:null-null@0,0;10,0;"
            "T:1:\ue000TOP:EXPOSED:net-one:null-null@0,0;10,0;"
            "T:1:wTOP:COVERED:net-one:null-null@W11:0,0;10,0;"
            "H:2:\U0001f600NON_PLATED-1:20,0,2,2,EXPOSED"
            "H:1:\ue000VIA7:net-one30,0,2,6,COVERED")
        expected_sha = "7df85a22b163f06bbabe0b642cf877147ed97174556f908b8e71bc7eafe66eba"
        self.assertEqual(len(expected.encode("utf-8")), 263)
        self.assertEqual(_sha(expected.encode("utf-8")), expected_sha)
        self.assertEqual(_fixture_fingerprint("P\U0001f600|", traces, holes), expected)
        self.assertEqual(reader.geometry_fingerprint("P\U0001f600|", traces, holes), expected)
        snapshot = dict(staticPrefix="P\U0001f600|", traces=traces, holes=holes,
                        geometryBytes=263, geometrySha256=expected_sha)
        self.assertEqual(reader._geometry_rows(snapshot), (expected, expected.encode("utf-8")))

    def test_published_singleton_layer_piece_has_literal_nodes_trace_via_and_guard(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-publisher-literal-") as directory:
            spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
            target = _publisher_target(spec_path)
            self.assertEqual(target["path"]["nodes"], [
                [1, 0, 10, 0, 4, 0, 0, 10, 0, 29, -1],
                [0, 0, 0, 0, 3, 0, 10, 0, 1, 21, 0],
                [0, 1, 0, 0, 4, 1, 70, 0, 2, 133, 1],
            ])
            self.assertEqual(target["before"]["traces"], [])
            self.assertEqual(target["before"]["holes"], [])
            self.assertEqual(target["after"]["traces"], [
                ["p07/net-one/1/0", "net-one", "pad-b", None, 0,
                 "EXPOSED", 9, [10, 0], [0, 0]]])
            self.assertEqual(target["after"]["holes"], [
                ["p07-via/net-one/1/1", "VIA", "net-one", 0, 0, 2, 6, "EXPOSED"]])
            self.assertEqual(target["publisher"], dict(
                outcome="PUBLISHED", stage=6, qualityIndex=3, reversedSize=3,
                storedLength=10, storedBends=0, storedLastDirection=4, segments=1,
                reason=None, sourceHandoff=True, startReachedAfter=True))
            self.assertEqual(reader.validate_spec(spec_path, spec_sha)["status"],
                             "PASS_READER_FIXTURE_ONLY")

    def test_two_piece_publication_and_all_role_widths_have_manual_geometry(self):
        for role, width in (("SIGNAL", 9), ("SUPPLY", 11), ("HIGH_CURRENT", 13)):
            with self.subTest(role=role), tempfile.TemporaryDirectory(
                    prefix="branch-order-publisher-pieces-") as directory:
                pads = tuple((pad_id, "net-one", component, x, 0, role)
                             for pad_id, component, x in (
                                 ("pad-a", "component-a", 0), ("pad-b", "component-b", 40),
                                 ("pad-c", "component-c", 60), ("pad-d", "component-d", 80)))
                spec_path, spec_sha = write_fixture(Path(directory) / "fixture", padRows=pads)
                target = _publisher_target(spec_path)
                self.assertEqual(target["after"]["traces"], [
                    ["p07/net-one/1/0", "net-one", "pad-b", None, 0, "EXPOSED",
                     width, [40, 20], [0, 0]],
                    ["p07/net-one/1/1", "net-one", None, "pad-a", 1, "EXPOSED",
                     width, [20, 0], [0, 0]],
                ])
                self.assertEqual(target["after"]["holes"], [
                    ["p07-via/net-one/1/1", "VIA", "net-one", 20, 0, 2, 6, "EXPOSED"]])
                self.assertEqual([node[6] for node in target["path"]["nodes"]],
                                 [0, 10, 20, 80, 90, 100])
                self.assertEqual([target["publisher"][name] for name in
                                  ("storedLength", "storedBends", "storedLastDirection", "segments")],
                                 [40, 0, 3, 4])
                self.assertEqual(reader.validate_spec(spec_path, spec_sha)["status"],
                                 "PASS_READER_FIXTURE_ONLY")

    def test_compatible_via_reuse_preserves_old_hole_without_duplicate(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-via-reuse-") as directory:
            spec_path, spec_sha = write_fixture(Path(directory) / "fixture", publisherVia="REUSE")
            target = _publisher_target(spec_path)
            expected = [["prior-compatible-via", "VIA", "net-one", 0, 0, 2, 6, "EXPOSED"]]
            self.assertEqual(target["before"]["holes"], expected)
            self.assertEqual(target["after"]["holes"], expected)
            for arm in (3, 4):
                self.assertEqual(_branch(_read_rows(spec_path, arm), 1)["snapshot"]["prefixViaCount"], 1)
            self.assertEqual(target["publisher"]["segments"], 1)
            self.assertEqual(reader.validate_spec(spec_path, spec_sha)["status"],
                             "PASS_READER_FIXTURE_ONLY")

    def test_nonpad_trace_goal_keeps_terminal_null_and_preserves_prefix(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-nonpad-goal-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", publisherNonPadGoal=True)
            target = _publisher_target(spec_path)
            self.assertIsNone(target["objects"]["terminal"])
            self.assertIsNone(target["branch"]["foundTerminalPadId"])
            self.assertEqual(target["before"]["traces"], [
                ["prefix/trunk", "net-one", None, None, 0, "EXPOSED", 9, [10, 20], [0, 0]]])
            self.assertEqual(target["after"]["traces"], target["before"]["traces"] + [
                ["p07/net-one/1/0", "net-one", "pad-b", None, 0,
                 "EXPOSED", 9, [10, 20], [0, 0]]])
            self.assertEqual(target["after"]["holes"], [])
            self.assertEqual(reader.validate_spec(spec_path, spec_sha)["status"],
                             "PASS_READER_FIXTURE_ONLY")

    def test_quality_rejection_has_literal_guard_and_unchanged_geometry(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-publisher-rejected-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", publisherOutcome="REJECTED")
            target = _publisher_target(spec_path)
            self.assertEqual([node[2:4] for node in target["path"]["nodes"]],
                             [[10, 0], [20, 0], [30, 0], [20, 0], [10, 0], [0, 0]])
            self.assertEqual(target["publisher"], dict(
                outcome="REJECTED", stage=1, qualityIndex=6, reversedSize=6,
                storedLength=50, storedBends=1, storedLastDirection=3, segments=0,
                reason="net-one:BRANCH_QUALITY_LIMIT", sourceHandoff=False,
                startReachedAfter=False))
            self.assertEqual(target["before"], target["after"])
            self.assertEqual(target["ordering"]["finalizerStatus"], "NOT_REACHED")
            self.assertIsNone(target["checkpoints"]["orderingReady"])
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            self.assertEqual(result["publisherOutcomes"][-1], "REJECTED")

    def test_publisher_context_source_branch_and_object_splices_reject(self):
        cases = (
            (lambda d: d["context"].update(rootSeed="9007199254740993"), "PUB_CONTEXT_JOIN"),
            (lambda d: d["context"].update(placementSeed="-5526715536693200536"), "PUB_CONTEXT_JOIN"),
            (lambda d: d["context"].update(sourceManifestSha256="0" * 64), "PUB_CONTEXT_JOIN"),
            (lambda d: d["context"].update(request=1), "PUB_CONTEXT_JOIN"),
            (lambda d: d["branch"].update(startPadId="pad-c"), "PUB_BRANCH_JOIN"),
            (lambda d: d["branch"].update(branchRecordGroup=1), "PUB_BRANCH_JOIN"),
            (lambda d: d["branch"].update(reachedPadIds=["pad-a", "pad-a"]), "PUB_REACHED_SET"),
            (lambda d: d["branch"].update(netVectorPosition=1), "PUB_NET_VECTOR_JOIN"),
            (lambda d: d["objects"].update(generator=2), "PUB_OBJECT_ALIAS"),
            (lambda d: d["objects"].update(search=True), "PUB_OBJECT_TOKEN"),
            (lambda d: d["objects"].update(found=99999), "PUB_PATH_HEADER"),
            (lambda d: d["path"]["nodeTokens"].__setitem__(0, 2), "PUB_PATH_HEADER"),
            (lambda d: d["path"]["nodeTokens"].__setitem__(0, 10001), "PUB_PATH_HEADER"),
            (lambda d: d["objects"].update(terminal=None), "PUB_TERMINAL_OBJECT_JOIN"),
        )
        for mutate, code in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-publisher-splice-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_capture_target(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_publisher_path_parent_grid_key_direction_layer_and_goal_reject(self):
        def wrong_direction(d):
            d["path"]["nodes"][1][4] = 1
            d["path"]["nodes"][1][9] = 7

        cases = (
            (lambda d: d["path"]["nodes"][1].__setitem__(10, -1), "PUB_PATH_NODE_DOMAIN"),
            (lambda d: d["path"]["nodes"][1].__setitem__(2, 10), "PUB_PATH_GRID"),
            (lambda d: d["path"]["nodes"][1].__setitem__(9, 20), "PUB_PATH_KEY"),
            (wrong_direction, "PUB_PATH_DIRECTION"),
            (lambda d: d["path"]["nodes"][2].__setitem__(1, 2), "PUB_PATH_NODE_DOMAIN"),
            (lambda d: d["path"].update(foundGoal=False), "PUB_PATH_HEADER"),
            (lambda d: d["grid"].update(cells=8), "PUB_GRID_VALUES"),
            (lambda d: d["publisher"].update(storedLength=20), "PUB_STORED_GUARD"),
            (lambda d: d["publisher"].update(storedBends=1), "PUB_STORED_GUARD"),
            (lambda d: d["publisher"].update(storedLastDirection=3), "PUB_STORED_GUARD"),
        )
        for mutate, code in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-publisher-path-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_capture_target(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_publisher_delta_foreign_via_and_rejected_prefix_mutation_reject(self):
        cases = (
            ({}, lambda d: d["after"]["traces"][0].__setitem__(6, 11), "PUB_GEOMETRY_DELTA"),
            ({}, lambda d: d["after"]["traces"][0].__setitem__(0, "p07/net-one/2/0"),
             "PUB_GEOMETRY_DELTA"),
            ({}, lambda d: d["after"]["holes"].clear(), "PUB_GEOMETRY_DELTA"),
            ({}, lambda d: d["after"].update(staticPrefix="changed|"), "PUB_STATIC_PREFIX_CHANGED"),
            ({"publisherVia": "REUSE"},
             lambda d: d["after"]["holes"].clear(), "PUB_PREFIX_ROWS_NOT_CONSERVED"),
            ({"publisherVia": "REUSE"},
             lambda d: [d[name]["holes"][0].__setitem__(2, "foreign-net")
                        for name in ("before", "after")], "PUB_VIA_FOREIGN_OR_NPTH"),
            ({"publisherVia": "REUSE"},
             lambda d: [d[name]["holes"][0].__setitem__(slice(1, 3), ["NON_PLATED", None])
                        or d[name]["holes"][0].__setitem__(6, 2)
                        for name in ("before", "after")], "PUB_VIA_FOREIGN_OR_NPTH"),
            ({"publisherOutcome": "REJECTED"},
             lambda d: d["after"]["traces"].append(
                 ["unexplained", "net-one", None, None, 0, "EXPOSED", 9, [0, 10], [0, 0]]),
             "PUB_REJECTED_BOUNDARY"),
        )
        for options, mutate, code in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-publisher-delta-") as directory:
                spec_path, _ = write_fixture(Path(directory) / "fixture", **options)
                spec_path, spec_sha = _rewrite_capture_target(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_publisher_handoff_ready_finalizer_and_partial_capture_reject(self):
        cases = (
            (lambda d: d["publisher"].update(sourceHandoff=False), "PUB_PUBLISHED_BOUNDARY"),
            (lambda d: d["publisher"].update(startReachedAfter=False), "PUB_PUBLISHED_BOUNDARY"),
            (lambda d: d["publisher"].update(stage=5), "PUB_PUBLISHED_BOUNDARY"),
            (lambda d: d["publisher"].update(segments=0), "PUB_PUBLISHED_BOUNDARY"),
            (lambda d: d["checkpoints"].update(publisherSeen=None), "PUB_REQUIRED_CHECKPOINT"),
            (lambda d: d["checkpoints"].update(orderingReady=None), "PUB_READY_JOIN"),
            (lambda d: d["checkpoints"]["orderingResult"].update(sequence=4), "PUB_READY_JOIN"),
            (lambda d: d["ordering"].update(readyLayoutToken=99), "PUB_READY_JOIN"),
            (lambda d: d["ordering"].update(finalizerStatus="NOT_REACHED"), "PUB_FINALIZER_JOIN"),
            (lambda d: d["ordering"].update(lastFailureDetail=""), "PUB_REJECTED_COUNTER"),
            (lambda d: d["ordering"].update(finalizedAfter=1), "PUB_REJECTED_COUNTER"),
            (lambda d: d["ordering"].update(finalizerStatus="FINALIZED", finalizedAfter=1),
             "PUB_REQUEST_RECEIPT_JOIN"),
            (lambda d: d["integrity"].update(sourceReferencesReleased=False), "PUB_INTEGRITY"),
            (lambda d: d["integrity"].update(ownerBindingVerified=1), "PUB_INTEGRITY"),
            (lambda d: d["integrity"].update(sourceReferencesReleased=1), "PUB_INTEGRITY"),
            (lambda d: d["limitations"].update(routeAdoption=0), "PUB_LIMITATIONS"),
            (lambda d: d["limitations"].update(layoutQualification=0), "PUB_LIMITATIONS"),
            (lambda d: d["limitations"].update(playable100=False), "PUB_LIMITATIONS"),
        )
        cases += tuple((lambda d, value=outcome: d["publisher"].update(outcome=value),
                        "PUB_CAPTURE_INCOMPLETE") for outcome in
                       ("NOT_OBSERVED", "AMBIGUOUS", "UNSUPPORTED", "CANCELLED", "ERROR", "UNKNOWN"))
        for mutate, code in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-publisher-boundary-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_capture_target(spec_path, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_publisher_document_and_nested_key_sets_are_closed(self):
        sections = ((None, "PUB_TARGET_SCHEMA"), ("context", "PUB_CONTEXT_SCHEMA"),
                    ("objects", "PUB_OBJECTS_SCHEMA"), ("branch", "PUB_BRANCH_SCHEMA"),
                    ("grid", "PUB_GRID_SCHEMA"), ("path", "PUB_PATH_SCHEMA"),
                    ("checkpoints", "PUB_CHECKPOINT_SCHEMA"),
                    ("publisher", "PUB_PUBLISHER_SCHEMA"), ("ordering", "PUB_ORDERING_SCHEMA"),
                    ("before", "PUB_GEOMETRY_SCHEMA"), ("after", "PUB_GEOMETRY_SCHEMA"))
        for section, code in sections:
            for missing in (False, True):
                with self.subTest(section=section, missing=missing), tempfile.TemporaryDirectory(
                        prefix="branch-order-publisher-schema-") as directory:
                    spec_path, _ = self._fixture(Path(directory) / "fixture")

                    def mutate(d):
                        obj = d if section is None else d[section]
                        if missing:
                            # These fields are not consumed by the fixture repacker.
                            key = {None: "schema", "context": "placementSha256",
                                   "objects": "terminal", "branch": "netVectorPosition",
                                   "grid": "pitch", "path": "foundGoal",
                                   "checkpoints": "orderingReady", "publisher": "stage",
                                   "ordering": "routerReason", "before": "geometrySha256",
                                   "after": "geometrySha256"}[section]
                            obj.pop(key)
                        else:
                            obj["unexpected"] = True

                    spec_path, spec_sha = _rewrite_capture_target(
                        spec_path, mutate, reseal_geometry=False)
                    self._assert_rejects(spec_path, spec_sha, code)

    def test_publisher_chunks_reject_duplicate_missing_reordered_offset_and_bad_base64(self):
        def chunks(rows):
            return [row for row in rows if row["record"] == "PUB_CHUNK"]

        def duplicate(rows):
            row = chunks(rows)[0]
            rows.insert(rows.index(row) + 1, copy.deepcopy(row))

        def reorder(rows):
            first, second = chunks(rows)[:2]
            # Keep source-group identity valid to isolate transport order.
            for name in ("index", "offset", "bytes", "data"):
                first[name], second[name] = second[name], first[name]

        cases = (
            (duplicate, "PUB_CHUNK_ORDER", True),
            (lambda rows: rows.remove(chunks(rows)[0]), "PUB_CHUNK_ORDER", True),
            (reorder, "PUB_CHUNK_ORDER", True),
            (lambda rows: chunks(rows)[0].update(offset=1), "PUB_CHUNK_ORDER", True),
            (lambda rows: chunks(rows)[0].update(operation=3), "PUB_CHUNK_IDENTITY", True),
            (lambda rows: chunks(rows)[0].update(bytes=4097), "PUB_CHUNK_IDENTITY", True),
            (lambda rows: chunks(rows)[0].update(data="!"), "PUB_CHUNK_BASE64", False),
            (lambda rows: chunks(rows)[0].update(bytes=1), "PUB_CHUNK_BASE64", False),
        )
        for mutate, code, repair in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-publisher-chunks-") as directory:
                spec_path, _ = self._net_order_fixture(Path(directory))
                self.assertGreater(len(chunks(_read_rows(spec_path, 4))), 1)
                spec_path, spec_sha = _rewrite_operation(
                    spec_path, 4, mutate, repair_groups=repair)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_publisher_result_seal_and_missing_partial_target_reject(self):
        def result(rows):
            return next(row for row in rows if row["record"] == "PUB_RESULT")

        cases = (
            (lambda rows: result(rows).update(payloadSha256="0" * 64), "PUB_RESULT_SEAL"),
            (lambda rows: result(rows).update(payloadBytes=1), "PUB_RESULT_COUNTS"),
            (lambda rows: result(rows).update(chunkCount=999), "PUB_RESULT_COUNTS"),
            (lambda rows: rows.remove(result(rows)), "PUB_RESULT_BEFORE_REQUEST_RESULT"),
            (lambda rows: rows.__setitem__(slice(None), [row for row in rows
                 if row["record"] not in ("PUB_CHUNK", "PUB_RESULT")]),
             "PUB_RESULT_BEFORE_REQUEST_RESULT"),
        )
        for mutate, code in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-publisher-seal-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 4, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_capture_accounting_conserves_literal_bytes_and_no_owner_work(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-publisher-accounting-") as directory:
            spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
            rows = _read_rows(spec_path, 4)
            target = _publisher_target(spec_path)
            payload = b"".join(base64.b64decode(row["data"], validate=True)
                               for row in rows if row["record"] == "PUB_CHUNK")
            emitted = sum(len(_line(row)) for row in rows
                          if row["record"] in ("PUB_CHUNK", "PUB_RESULT"))
            groups = [row for row in rows if row["record"] == "GROUP"]
            self.assertEqual(sum(row["diagnosticSerializedBytes"] for row in groups),
                             len(payload) + emitted)
            self.assertEqual(sum(row["diagnosticHashBytes"] for row in groups),
                             len(payload) + emitted + target["before"]["geometryBytes"]
                             + target["after"]["geometryBytes"])
            # One reached ID plus24 captured metadata entries are synchronous;
            # the12 entries per raw node are copied later in CAPTURE.
            self.assertEqual(_group(rows, 2)["diagnosticCopiedEntries"], 25)
            literal_after = (
                "fixture-static-prefix|"
                "T:15:p07/net-one/1/0TOP:EXPOSED:net-one:pad-b-null@10,0;0,0;"
                "H:19:p07-via/net-one/1/1VIA7:net-one0,0,2,6,EXPOSED")
            self.assertEqual(_group(rows, 3)["diagnosticCopiedEntries"],
                             19 + len(literal_after))
            first_capture = next(row for row in groups if row["phase"] == "CAPTURE")
            self.assertEqual(first_capture["diagnosticCopiedEntries"], len(payload) + 36)
            for group in groups:
                self.assertEqual(group["diagnosticUnits"], group["diagnosticCopiedEntries"]
                                 + group["diagnosticSerializedBytes"] + group["diagnosticHashBytes"])
                if group["phase"] == "CAPTURE":
                    self.assertEqual(group["p07Slices"], 0)
                    self.assertIsNone(group["p07Steps"])
                    self.assertLessEqual(group["diagnosticUnits"], 32768)
            self.assertEqual(reader.validate_spec(spec_path, spec_sha)["status"],
                             "PASS_READER_FIXTURE_ONLY")

    def test_capture_accounting_missing_work_bytes_time_and_caps_reject(self):
        def capture(rows):
            return next(row for row in rows if row["record"] == "GROUP"
                        and row["phase"] == "CAPTURE")

        def add_hash_byte(rows):
            row = capture(rows)
            row["diagnosticHashBytes"] += 1
            row["diagnosticUnits"] += 1
            row["hashInputBytes"] += 1

        def add_serialized_byte(rows):
            row = capture(rows)
            row["diagnosticSerializedBytes"] += 1
            row["diagnosticUnits"] += 1

        def omit_snapshot(rows):
            row = _group(rows, 2)
            row["diagnosticCopiedEntries"] = row["diagnosticUnits"] = 0

        cases = (
            (lambda rows: capture(rows).pop("diagnosticHashBytes"), "GROUP_SCHEMA"),
            (lambda rows: capture(rows).update(diagnosticUnits=0), "DIAGNOSTIC_UNIT_SUM"),
            (lambda rows: capture(rows).update(diagnosticUnits=32769), "CAPTURE_DIAGNOSTIC_LIMIT"),
            (lambda rows: capture(rows).update(p07Steps=1), "CAPTURE_OWNER_WORK"),
            (lambda rows: capture(rows).update(p07Slices=1), "GROUP_LIMIT"),
            (lambda rows: capture(rows).update(elapsedBeforeOwnLineNanos=5_000_000_001), "GROUP_LIMIT"),
            (lambda rows: capture(rows).update(serializedBeforeOwnLineBytes=0), "GROUP_RECORD_COUNTS"),
            (lambda rows: capture(rows).update(hashInputBytes=0), "GROUP_HASH_BYTES"),
            (lambda rows: _group(rows, 2).update(diagnosticSerializedBytes=1, diagnosticUnits=26),
             "ADVANCE_DIAGNOSTIC_SCOPE"),
            (lambda rows: _group(rows, 2).update(
                diagnosticCopiedEntries=1_048_577, diagnosticUnits=1_048_577),
             "ADVANCE_DIAGNOSTIC_SCOPE"),
            (add_hash_byte, "DIAGNOSTIC_HASH_CENSUS"),
            (add_serialized_byte, "DIAGNOSTIC_OUTPUT_BYTES"),
            (omit_snapshot, "DIAGNOSTIC_SYNCHRONOUS_SNAPSHOT"),
        )
        for mutate, code in cases:
            with self.subTest(code=code, line=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-capture-work-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, 4, mutate, repair_groups=False)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_five_arm_fixture_and_strict_cli_output(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-reader-") as directory:
            spec_path, spec_sha = self._fixture(Path(directory) / "fixture")
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            self.assertEqual(result["kind"], "READER_FIXTURE")
            self.assertEqual(result["operations"], 5)
            self.assertEqual(result["branches"], [0, 3, 3, 3, 3])
            self.assertEqual(result["routeOutcomes"], ["TYPED_REQUEST_REJECTION"] * 5)
            self.assertEqual(result["publisherOutcomes"], [None, None, None, None, "PUBLISHED"])
            self.assertFalse(any(row["record"] == "NET_ORDER"
                                 for row in _read_rows(spec_path, 0)))
            self.assertEqual(result["netOrderComparison"],
                             {"status": "NO_DIVERGENCE", "netCount": 1})
            self.assertEqual(result["netOrderAudits"][4]["observedNetIds"], ["net-one"])
            self.assertEqual(result["netOrderAudits"][4]["minimumPairs"], [
                {"netId": "net-one", "distance": 10, "witnessPadIds": ["pad-a", "pad-b"]}])
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

    def test_plus_terminal_pad_ids_survive_five_arm_census_hash_and_choice_checks(self):
        starts = ("UAC.IN+", "UAC.IN+", "UAC.IN+")
        with tempfile.TemporaryDirectory(prefix="branch-order-plus-ids-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", padRows=PLUS_PAD_ROWS, startIds=starts)
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            self.assertEqual(result["branches"], [0, 3, 3, 3, 3])
            self.assertEqual(result["netOrderComparison"]["status"], "NO_DIVERGENCE")
            self.assertTrue(all(reader.token(row[0]) for row in PLUS_PAD_ROWS))

            manifest = _load_json(Path(spec_path).parent / "source-manifest.json")
            wire = next(row for row in manifest["files"] if row["role"] == "WIRE")
            self.assertEqual(wire["path"],
                             "docs/task-evidence/Q100/outf-boundary-r1/WIRE.md")
            for arm in range(5):
                rows = _read_rows(spec_path, arm)
                self.assertEqual(rows[0]["schema"], reader.SCHEMA)
                pads = [row for row in rows if row["record"] == "PAD"]
                self.assertEqual([row["padId"] for row in pads],
                                 ["C1.+", "R3.+", "UAC.IN+", "UAC.PRE_L+"])
                result_row = next(row for row in rows if row["record"] == "RESULT")
                self.assertEqual(result_row["context"]["padCensusSha256"],
                                 reader.sha(reader.pad_bytes(pads)))
                branches = [row for row in rows if row["record"] == "BRANCH"]
                self.assertEqual([row["snapshot"]["startPadId"] for row in branches],
                                 ["UAC.IN+"] * (3 if arm else 0))

    def test_full_net_order_oracle_covers_minimum_not_root_distance_ties_singleton_and_wide(self):
        visits = {
            1: ["net-wide", "net-alpha", "net-beta", "net-gamma"],
            2: ["net-wide", "net-alpha", "net-beta", "net-gamma"],
            3: ["net-wide", "net-beta", "net-gamma", "net-alpha"],
        }
        with tempfile.TemporaryDirectory(prefix="branch-order-net-order-positive-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", padRows=NET_PRIORITY_PAD_ROWS,
                netOrderIdsByArm={1: PROMOTED_CANONICAL_NET_IDS,
                                  2: PROMOTED_CANONICAL_NET_IDS,
                                  3: PROMOTED_MINIMUM_PAIR_NET_IDS},
                promotionNetsByArm={1: "net-wide", 2: "net-wide", 3: "net-wide"},
                pass1NetVisitsByArm=visits)
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            self.assertEqual(result["netOrderComparison"], {
                "status": "FOUND", "firstDifferingIndex": 1,
                "controlNetId": "net-alpha", "treatmentNetId": "net-beta"})
            control, treatment = result["netOrderAudits"][2:4]
            self.assertEqual(control["observedNetIds"], PROMOTED_CANONICAL_NET_IDS)
            self.assertEqual(treatment["observedNetIds"], PROMOTED_MINIMUM_PAIR_NET_IDS)
            self.assertEqual(control["expectedNetIds"], PROMOTED_CANONICAL_NET_IDS)
            self.assertEqual(treatment["minimumPairNetIds"], MINIMUM_PAIR_NET_IDS)
            self.assertEqual(treatment["expectedNetIds"], PROMOTED_MINIMUM_PAIR_NET_IDS)
            self.assertEqual(treatment["singletonNetIds"], ["net-single"])
            self.assertEqual(treatment["minimumPairs"], [
                {"netId": "net-alpha", "distance": 100,
                 "witnessPadIds": ["a-0", "a-1"]},
                {"netId": "net-beta", "distance": 10,
                 "witnessPadIds": ["b-1", "b-2"]},
                {"netId": "net-gamma", "distance": 10,
                 "witnessPadIds": ["g-0", "g-1"]},
                {"netId": "net-wide", "distance": 4000,
                 "witnessPadIds": ["w-0", "w-1"]},
            ])
            # The lexical witness is the first pair among the tied alpha minima;
            # equal minimum distances between beta/gamma use net-ID order.
            self.assertEqual(MINIMUM_PAIR_NET_IDS[:2], ["net-beta", "net-gamma"])
            self.assertNotEqual(MINIMUM_PAIR_NET_IDS,
                                ["net-gamma", "net-alpha", "net-beta",
                                 "net-wide", "net-single"])
            for arm, expected in ((1, visits[1]), (2, visits[2]), (3, visits[3])):
                rows = _read_rows(spec_path, arm)
                pass_one_nets = []
                for branch in (row for row in rows if row["record"] == "BRANCH"
                               and row["snapshot"]["pass"] == 1):
                    if not pass_one_nets or pass_one_nets[-1] != branch["snapshot"]["netId"]:
                        pass_one_nets.append(branch["snapshot"]["netId"])
                self.assertEqual(pass_one_nets, expected)

    def test_yielded_pass_zero_authorizes_null_promotion_only_with_complete_found_census(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-yielded-promotion-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", padRows=NET_PRIORITY_PAD_ROWS,
                netOrderIdsByArm={1: CANONICAL_NET_IDS, 2: CANONICAL_NET_IDS,
                                  3: MINIMUM_PAIR_NET_IDS},
                yieldedPriorByArm={1: True, 2: True, 3: True})
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            self.assertEqual(result["netOrderAudits"][2]["observedNetIds"], CANONICAL_NET_IDS)
            self.assertEqual(result["netOrderAudits"][3]["observedNetIds"], MINIMUM_PAIR_NET_IDS)
            for arm in (1, 2, 3, 4):
                rows = _read_rows(spec_path, arm)
                order = _net_order(rows)
                pass_zero = [row for row in rows if row["record"] == "BRANCH"
                             and row["snapshot"]["pass"] == 0]
                self.assertEqual(order["completedOrderings"], 1)
                self.assertEqual(order["yieldedOrderings"], 1)
                self.assertIsNone(order["promotedNet"])
                self.assertEqual(len(pass_zero), len(NET_PRIORITY_PAD_ROWS) - 5)
                self.assertTrue(all(row["snapshot"]["terminalReason"] == "FOUND"
                                    for row in pass_zero))

    def test_pass_one_not_reached_cannot_qualify_without_publisher_capture(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-not-reached-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", reachPass1ByArm={1: False, 2: False, 3: False})
            self._assert_rejects(spec_path, spec_sha, "PUB_RESULT_BEFORE_REQUEST_RESULT")
            for arm in range(5):
                self.assertFalse(any(row["record"] == "NET_ORDER"
                                     for row in _read_rows(spec_path, arm)))

    def test_net_order_record_presence_uniqueness_and_prebranch_position_are_closed(self):
        def remove_order(rows):
            rows.remove(_net_order(rows))

        def duplicate_order(rows):
            order = _net_order(rows)
            rows.insert(rows.index(order) + 1, copy.deepcopy(order))

        def move_order_after_first_pass_one_branch(rows):
            order = _net_order(rows)
            rows.remove(order)
            first = next(i for i, row in enumerate(rows)
                         if row["record"] == "BRANCH" and row["snapshot"]["pass"] == 1)
            rows.insert(first + 1, order)

        for arm, mutate, code in (
                (3, remove_order, "NET_ORDER_MISSING_OR_LATE"),
                (3, duplicate_order, "NET_ORDER_DUPLICATE"),
                (3, move_order_after_first_pass_one_branch, "NET_ORDER_MISSING_OR_LATE")):
            with self.subTest(arm=arm, code=code), tempfile.TemporaryDirectory(
                    prefix="branch-order-net-order-position-") as directory:
                spec_path, _ = self._net_order_fixture(Path(directory))
                spec_path, spec_sha = _rewrite_operation(spec_path, arm, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_net_order_schema_strategy_census_and_complete_vector_are_independent(self):
        cases = (
            (lambda rows: _net_order(rows).update(extra=True), "NET_ORDER_SCHEMA"),
            (lambda rows: _net_order(rows).pop("strategy"), "NET_ORDER_SCHEMA"),
            (lambda rows: _net_order(rows).update(strategy="CANONICAL"), "NET_ORDER_IDENTITY"),
            (lambda rows: _net_order(rows).__setitem__("pass", 2), "NET_ORDER_IDENTITY"),
            (lambda rows: _net_order(rows)["netIds"].pop(), "NET_ORDER_CENSUS"),
            (lambda rows: _net_order(rows)["netIds"].__setitem__(1,
                 _net_order(rows)["netIds"][0]), "NET_ORDER_CENSUS"),
            (lambda rows: _net_order(rows)["netIds"].__setitem__(0, "unknown-net"),
             "NET_ORDER_CENSUS"),
            (lambda rows: _net_order(rows)["netIds"].__setitem__(slice(0, 2),
                 ["net-beta", "net-wide"]),
             "NET_ORDER_VECTOR"),
        )
        for mutate, code in cases:
            with self.subTest(code=code, mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-net-order-schema-") as directory:
                spec_path, _ = self._net_order_fixture(Path(directory))
                spec_path, spec_sha = _rewrite_operation(spec_path, 3, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_net_order_counters_promotion_and_ambiguous_all_found_pass_are_rejected(self):
        def mark_final_pass_zero_found(rows):
            pass_zero = [row for row in rows if row["record"] == "BRANCH"
                         and row["snapshot"]["pass"] == 0]
            pass_zero[-1]["snapshot"]["terminalReason"] = "FOUND"

        cases = (
            (lambda rows: _net_order(rows).update(completedOrderings=2), "NET_ORDER_VALUES"),
            (lambda rows: _net_order(rows).update(yieldedOrderings=True), "NET_ORDER_VALUES"),
            (lambda rows: _net_order(rows).update(promotedNet="net-beta"),
             "NET_ORDER_FAILED_PROMOTION"),
            (mark_final_pass_zero_found, "NET_ORDER_AMBIGUOUS_PROMOTION"),
        )
        for mutate, code in cases:
            with self.subTest(code=code, mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-net-order-promotion-") as directory:
                spec_path, _ = self._net_order_fixture(Path(directory))
                spec_path, spec_sha = _rewrite_operation(spec_path, 3, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

        with tempfile.TemporaryDirectory(prefix="branch-order-net-order-yielded-bad-") as directory:
            spec_path, _ = self._net_order_fixture(
                Path(directory), yieldedPriorByArm={1: True, 2: True, 3: True},
                netOrderIdsByArm={1: CANONICAL_NET_IDS, 2: CANONICAL_NET_IDS,
                                  3: MINIMUM_PAIR_NET_IDS},
                pass1NetVisitsByArm={
                    1: ["net-alpha", "net-beta", "net-gamma", "net-wide"],
                    2: ["net-alpha", "net-beta", "net-gamma", "net-wide"],
                    3: ["net-beta", "net-gamma", "net-alpha", "net-wide"]})
            spec_path, spec_sha = _rewrite_operation(
                spec_path, 3, lambda rows: _net_order(rows).update(promotedNet="net-alpha"))
            self._assert_rejects(spec_path, spec_sha, "NET_ORDER_YIELDED_PROMOTION")

    def test_pass_one_callbacks_must_be_an_order_preserving_vector_subsequence(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-subsequence-") as directory:
            spec_path, spec_sha = self._net_order_fixture(
                Path(directory), pass1NetVisitsByArm={
                    1: ["net-alpha", "net-beta"], 2: ["net-alpha", "net-beta"],
                    3: ["net-alpha", "net-beta"]})
            with self.assertRaisesRegex(ValueError, "NET_ORDER_BRANCH_SUBSEQUENCE"):
                reader.validate_spec(spec_path, spec_sha)

    def test_net_order_group_row_and_hash_preimage_ledgers_are_required(self):
        cases = (
            (lambda rows: _group(rows, 2).pop("netOrderRows"), "GROUP_SCHEMA", False),
            (lambda rows: _group(rows, 2).update(netOrderRows=0),
             "GROUP_RECORD_COUNTS", False),
            (lambda rows: _group(rows, 2).update(serializedBeforeOwnLineBytes=0),
             "GROUP_RECORD_COUNTS", False),
            (lambda rows: _group(rows, 2).update(hashInputBytes=1),
             "GROUP_HASH_BYTES", False),
        )
        for mutate, code, repair in cases:
            with self.subTest(code=code, mutate=mutate.__code__.co_firstlineno), \
                    tempfile.TemporaryDirectory(prefix="branch-order-net-order-ledger-") as directory:
                spec_path, _ = self._net_order_fixture(Path(directory))
                spec_path, spec_sha = _rewrite_operation(
                    spec_path, 3, mutate, repair_groups=repair)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_plus_is_the_only_new_token_character(self):
        self.assertTrue(reader.token("UAC.IN+"))
        self.assertTrue(reader.token("UAC.PRE_L+"))
        self.assertTrue(reader.token("C1.+"))
        for pad_id in ("pad id", "pad\tid", "pad/id", 'pad"id', "pad-é"):
            with self.subTest(pad_id=pad_id), \
                    tempfile.TemporaryDirectory(prefix="branch-order-invalid-pad-id-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(
                    spec_path, 0, lambda rows, value=pad_id: _pad(rows, "pad-b").update(
                        padId=value))
                self._assert_rejects(spec_path, spec_sha, "PAD_VALUES_ORDER")

    def test_v1_through_v4_data_headers_are_rejected_without_compatibility_fallback(self):
        for version in ("v1", "v2", "v3", "v4"):
            with self.subTest(version=version), \
                    tempfile.TemporaryDirectory(prefix="branch-order-old-header-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(
                    spec_path, 0,
                    lambda rows, value=version:
                        rows[0].update(schema="p07-actual-branch-order-study-" + value))
                self._assert_rejects(spec_path, spec_sha, "HEADER_IDENTITY")

    def test_diagnostic_clone_pair_proves_two_steps_and_normalizes_control(self):
        prefixes = {1: {0: 31}, 2: {0: 31}, 3: {0: 31}}
        with tempfile.TemporaryDirectory(prefix="branch-order-clone-positive-") as directory:
            spec_path, spec_sha = write_fixture(
                Path(directory) / "fixture", diagnosticPrefixesByArm=prefixes)
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["status"], "PASS_READER_FIXTURE_ONLY")
            plain_rows = _read_rows(spec_path, 0)
            default_rows = _read_rows(spec_path, 1)
            plain = next(row for row in plain_rows if row["record"] == "RESULT")["logicalWork"]
            default = next(row for row in default_rows if row["record"] == "RESULT")["logicalWork"]
            self.assertEqual(plain["p07OwnerSteps"], 30)
            self.assertEqual(plain["diagnosticCloneSteps"], 0)
            self.assertEqual(plain["routingOwnerSteps"], 30)
            self.assertEqual(default["p07OwnerSteps"], 32)
            self.assertEqual(default["diagnosticCloneSteps"], 2)
            self.assertEqual(default["routingOwnerSteps"], 30)
            self.assertEqual([_event(default_rows, i)["faceIndex"] for i in range(2)], [0, 1])
            self.assertEqual(_group(default_rows, 1)["diagnosticCloneSteps"], 2)
            self.assertEqual(_group(default_rows, 1)["p07Steps"], 12)

    def test_missing_duplicate_and_late_diagnostic_pairs_are_rejected(self):
        cases = []

        def remove_second_face(rows):
            rows.remove(_event(rows, 1))

        cases.append((remove_second_face, "DIAGNOSTIC_CLONE_PAIR", True))

        def duplicate_first_face(rows):
            duplicate = copy.deepcopy(_event(rows, 0))
            duplicate["ordinal"] = 2
            branch = _branch(rows, 0)
            rows.insert(rows.index(branch), duplicate)

        cases.append((duplicate_first_face, "DIAGNOSTIC_CLONE_DUPLICATE", True))

        def move_pair_after_branch(rows):
            pair = [_event(rows, 0), _event(rows, 1)]
            for event in pair:
                rows.remove(event)
            branch = _branch(rows, 0)
            insert_at = rows.index(branch) + 1
            rows[insert_at:insert_at] = pair

        cases.append((move_pair_after_branch, "DIAGNOSTIC_CLONE_ORDER", True))
        for mutate, code, repair in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-clone-pair-") as directory:
                spec_path, _ = write_fixture(
                    Path(directory) / "fixture", diagnosticPrefixesByArm={1: {0: 31}})
                spec_path, spec_sha = _rewrite_operation(
                    spec_path, 1, mutate, repair_groups=repair)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_unmatched_misclassified_face_capacity_and_partial_proof_reject(self):
        cases = (
            (lambda rows: _event(rows, 0).update(startPadId="pad-c"),
             "DIAGNOSTIC_CLONE_BRANCH_MATCH"),
            (lambda rows: _event(rows, 0).update(netId="other-net"),
             "DIAGNOSTIC_CLONE_CENSUS"),
            (lambda rows: _event(rows, 0).update(faceIndex=2), "DIAGNOSTIC_CLONE_FACE"),
            (lambda rows: _event(rows, 0).update(prefixViaCount=48),
             "DIAGNOSTIC_CLONE_CAPACITY"),
            (lambda rows: _branch(rows, 0)["snapshot"].update(
                relaxedBoundRouterEffectsStable=False), "DIAGNOSTIC_CLONE_PROOF"),
            (lambda rows: _branch(rows, 0)["snapshot"].update(
                relaxedBoundStarted=False), "DIAGNOSTIC_CLONE_UNEXPECTED"),
        )
        for mutate, code in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-clone-proof-") as directory:
                spec_path, _ = write_fixture(
                    Path(directory) / "fixture", diagnosticPrefixesByArm={1: {0: 31}})
                spec_path, spec_sha = _rewrite_operation(spec_path, 1, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_zero_capacity_started_partial_bound_still_lacks_required_proof(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-zero-cap-partial-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_operation(
                spec_path, 1,
                lambda rows: _branch(rows, 0)["snapshot"].update(
                    prefixViaCount=48, relaxedBoundStarted=True,
                    relaxedBoundComplete=False))
            self._assert_rejects(spec_path, spec_sha, "DIAGNOSTIC_CLONE_PROOF")

    def test_diagnostic_clone_group_and_boolean_counters_are_exact(self):
        cases = (
            (lambda rows: _event(rows, 0).update(extra=True),
             "DIAGNOSTIC_CLONE_SCHEMA", True),
            (lambda rows: _event(rows, 0).pop("netId"),
             "DIAGNOSTIC_CLONE_SCHEMA", True),
            (lambda rows: _group(rows, 1).update(diagnosticCloneSteps=1),
             "DIAGNOSTIC_CLONE_GROUP_COUNT", False),
            (lambda rows: _group(rows, 1).update(diagnosticCloneSteps=True),
             "GROUP_NUMBER", False),
            (lambda rows: _event(rows, 0).update(ordinal=True),
             "DIAGNOSTIC_CLONE_IDENTITY", True),
            (lambda rows: _event(rows, 0).update(ownerSteps=True),
             "DIAGNOSTIC_CLONE_STEPS", True),
            (lambda rows: _event(rows, 0).update(faceIndex=True),
             "DIAGNOSTIC_CLONE_FACE", True),
        )
        for mutate, code, repair in cases:
            with self.subTest(code=code), \
                    tempfile.TemporaryDirectory(prefix="branch-order-clone-count-") as directory:
                spec_path, _ = write_fixture(
                    Path(directory) / "fixture", diagnosticPrefixesByArm={1: {0: 31}})
                spec_path, spec_sha = _rewrite_operation(
                    spec_path, 1, mutate, repair_groups=repair)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_diagnostic_classification_cannot_mask_raw_step_cap(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-clone-raw-cap-") as directory:
            spec_path, _ = write_fixture(
                Path(directory) / "fixture", diagnosticPrefixesByArm={1: {0: 31}})
            spec_path, spec_sha = _rewrite_operation(
                spec_path, 1, lambda rows: _group(rows, 1).update(
                    p07Steps=reader.LIMITS["p07Steps"] + 1))
            self._assert_rejects(spec_path, spec_sha, "P07_ACCOUNTING")

    def test_unexplained_raw_work_difference_still_fails_normalized_parity(self):
        prefixes = {1: {0: 31}, 2: {0: 31}, 3: {0: 31}}
        with tempfile.TemporaryDirectory(prefix="branch-order-clone-unexplained-") as directory:
            spec_path, _ = write_fixture(
                Path(directory) / "fixture", diagnosticPrefixesByArm=prefixes)

            def add_unclassified_step(rows):
                _group(rows, 1)["p07Steps"] += 1
                logical = next(row for row in rows if row["record"] == "RESULT")["logicalWork"]
                logical["p07OwnerSteps"] += 1
                logical["routingOwnerSteps"] += 1

            spec_path, spec_sha = _rewrite_operation(spec_path, 1, add_unclassified_step)
            self._assert_rejects(spec_path, spec_sha, "PLAIN_CONTROL_PARITY")

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

    def test_tiny_exhaustive_choice_oracle_keeps_nearest_for_every_arm_and_ties(self):
        # Exhaust all 3^3 placements around a fixed root. The branch policy is
        # canonical nearest in every pass and arm, with lexical ID ties.
        for xs in itertools.product((-1, 0, 1), repeat=3):
            pads = [dict(padId=pad_id, netId="net-one", componentId="c-" + pad_id,
                         x=x, y=0, role="SIGNAL")
                    for pad_id, x in zip(("pad-a", "pad-b", "pad-c", "pad-d"),
                                         (0,) + xs)]
            branches = []
            for pass_number in range(3):
                reached = ["pad-a"]
                remaining = ["pad-b", "pad-c", "pad-d"]
                start = _expected_choice(pads, reached, remaining)
                branches.append({"snapshot": _snapshot(
                    pass_number, start, total_expanded=pass_number + 1, pads=pads)})
            choices, total = reader.audit_choices(branches, pads, arm=3)
            self.assertEqual(total, 3)
            for pass_number, choice in enumerate(choices):
                expected_near = _expected_choice(
                    pads, ["pad-a"], ["pad-b", "pad-c", "pad-d"])
                self.assertEqual(choice["nearest"], expected_near)
                self.assertEqual(branches[pass_number]["snapshot"]["startPadId"],
                                 expected_near)

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

    def test_observed_control_and_pristine_order_prefix_mismatches_are_rejected(self):
        cases = (
            (2, lambda rows: _branch(rows, 1).update(prefixSha256="3" * 64),
             "OBSERVED_CONTROL_PARITY"),
            (3, lambda rows: _net_order(rows).update(prefixSha256="4" * 64),
             "NET_ORDER_PRISTINE_PREFIX"),
        )
        for arm, mutate, code in cases:
            with self.subTest(arm=arm), \
                    tempfile.TemporaryDirectory(prefix="branch-order-parity-") as directory:
                spec_path, _ = self._fixture(Path(directory) / "fixture")
                spec_path, spec_sha = _rewrite_operation(spec_path, arm, mutate)
                self._assert_rejects(spec_path, spec_sha, code)

    def test_treatment_later_branch_prefix_is_not_the_ordering_comparison(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-later-prefix-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec_path, spec_sha = _rewrite_operation(
                spec_path, 3, lambda rows: _branch(rows, 2).update(prefixSha256="4" * 64))
            # v5 capture/control parity also binds later treatment prefixes.
            spec_path, spec_sha = _rewrite_operation(
                spec_path, 4, lambda rows: _branch(rows, 2).update(prefixSha256="4" * 64))
            result = reader.validate_spec(spec_path, spec_sha)
            self.assertEqual(result["netOrderComparison"],
                             {"status": "NO_DIVERGENCE", "netCount": 1})

    def test_publisher_comparison_allows_capture_group_shift_but_preserves_semantics(self):
        with tempfile.TemporaryDirectory(prefix="branch-order-capture-group-shift-") as directory:
            spec_path, _ = self._fixture(Path(directory) / "fixture")
            spec = _load_json(spec_path)
            index = _load_json(spec_path.parent / spec["studyIndex"]["path"])
            operations = [reader.validate_operation(
                (spec_path.parent / entry["receipt"]["path"]).read_bytes(), entry,
                spec["sourceManifest"]["sha256"], index["wireSha256"], arm,
                index["runId"], "READER_FIXTURE")
                for arm, entry in enumerate(index["operations"])]
            expected = {"status": "NO_DIVERGENCE", "netCount": 1}
            self.assertEqual(reader.validate_study(operations), expected)

            # Comparison-unit regression for the actual tiny failure: inline
            # CAPTURE inserted91 transport groups before later P07 callbacks.
            # These parsed-only edits do not certify a raw GROUP ledger, process,
            # or native run; validate_operation above checks the original packet.
            shifted = copy.deepcopy(operations)
            later = next(row for row in shifted[4]["branches"] if row["snapshot"]["pass"] == 2)
            self.assertEqual(later["group"], 3)
            self.assertEqual(later["ordinal"], 2)
            later["group"] += 91
            self.assertEqual(later["group"], 94)
            self.assertEqual(reader.validate_study(shifted), expected)

            semantic_changes = (
                lambda row: row.update(ordinal=3),
                lambda row: row.update(prefixSha256="9" * 64),
                lambda row: row.update(prefixBytes=row["prefixBytes"] + 1),
                lambda row: row["snapshot"].update(startPadId="pad-c"),
                lambda row: row["snapshot"].update(terminalReason="FOUND"),
                lambda row: row["snapshot"].update(branchExpanded=2),
            )
            for mutate in semantic_changes:
                with self.subTest(semantic_line=mutate.__code__.co_firstlineno):
                    changed = copy.deepcopy(shifted)
                    branch = next(row for row in changed[4]["branches"]
                                  if row["snapshot"]["pass"] == 2)
                    mutate(branch)
                    with self.assertRaises(ValueError) as caught:
                        reader.validate_study(changed)
                    self.assertEqual(str(caught.exception), "PUBLISHER_CAPTURE_TREATMENT_PARITY")

            reordered = copy.deepcopy(shifted)
            reordered[4]["branches"][1:3] = reversed(reordered[4]["branches"][1:3])
            with self.assertRaises(ValueError) as caught:
                reader.validate_study(reordered)
            self.assertEqual(str(caught.exception), "PUBLISHER_CAPTURE_TREATMENT_PARITY")

            # Canonical observed-default/explicit control parity keeps its
            # original group-sensitive comparison; the exception is only3/4.
            for arm in (1, 2):
                with self.subTest(canonical_arm=arm):
                    changed = copy.deepcopy(shifted)
                    branch = next(row for row in changed[arm]["branches"]
                                  if row["snapshot"]["pass"] == 2)
                    branch["group"] += 91
                    with self.assertRaises(ValueError) as caught:
                        reader.validate_study(changed)
                    self.assertEqual(str(caught.exception), "OBSERVED_CONTROL_PARITY")

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
