"""Prepare the private fixed optional P05 routing-stream source packet."""

import difflib
import hashlib
import json
import runpy
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
PRIOR = HERE.parent / "fresh-relay-stage-diagnostic-r1"
BORROW = PRIOR / "export-frozen-source" / "private-inputs"
BASE = "be7098819b3bdba15012a7f8d6869645bca0ca58"
ACTUAL_SOURCE_SEED = 3078526323410797175
EXPECTED_DERIVATIONS = (
    (0, 2926452371699457122),
    (1, -1259826540137006295),
    (-1, 1145332496697143667),
    (-(1 << 63), 721378348590188007),
    ((1 << 63) - 1, 426595435461648541),
    (ACTUAL_SOURCE_SEED, -7609816130686485171),
)
SOURCE_NAMES = (
    "common/SparseNormalBoardDiagnostic.java",
    "common/verify-two-pad-span.ps1",
    "control/SeededPcbLayoutGenerator.java",
    "control/PcbPlacementPlanner.java",
    "control/PcbPlacementCompactor.java",
)
OUTPUT_NAMES = (
    "common/SparseNormalBoardDiagnostic.java",
    "common/verify-two-pad-span.ps1",
    "variant/SeededPcbLayoutGenerator.java",
    "variant/PcbPlacementPlanner.java",
    "variant/PcbPlacementCompactor.java",
)


def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha_text(text: str) -> str:
    return sha_bytes(text.encode("utf-8"))


def newline_style(source: str) -> str:
    if "\r\n" in source:
        return "\r\n"
    if "\r" in source:
        return "\r"
    return "\n"


def with_newlines(text: str, newline: str) -> str:
    return text.replace("\n", newline)


def replace_once(source: str, old: str, new: str, owner: str,
                 label: str, changes: list) -> str:
    count = source.count(old)
    if count != 1:
        raise ValueError("%s replacement count is %d, expected 1" % (label, count))
    changes.append({"owner": owner, "label": label, "old": old, "new": new})
    return source.replace(old, new, 1)


def adapt_generator(source: str) -> tuple:
    newline = newline_style(source)
    changes = []

    helper_anchor = with_newlines(
        "    /** One optional candidate follows the unchanged, fully routed winner. */",
        newline)
    helper_new = with_newlines(
        "    static long optionalPackingRoutingSeed(long base) {\n"
        "        return new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,\n"
        "            base, \"pcb-packing-route\", 1).deviceSeed(\n"
        "            NamedRandomStreams.Concern.ROUTING, 1, \"optional-p05-proposal\");\n"
        "    }\n\n"
        "    /** One optional candidate follows the unchanged, fully routed winner. */",
        newline)
    source = replace_once(source, helper_anchor, helper_new, "generator",
                          "add_optional_packing_routing_seed_helper", changes)

    stage_old = with_newlines(
        "        // Keep the exact post-route compaction plan in its routing coordinate\n"
        "        // frame. The final display crop may translate pads by a non-grid amount.\n"
        "        PcbPlacementPlanner.Plan source = session.bestPlan;\n"
        "        TaskPackingSpanObservation.emit(\"SOURCE\", session.board,\n"
        "            source, session.seed, session.routingSeed);\n"
        "        session.packingPlan = PcbPlacementCompactor.compact(session.board, constraints, source);\n"
        "        TaskPackingSpanObservation.emit(\"ORDINARY\", session.board,\n"
        "            session.packingPlan, session.seed, session.routingSeed);\n"
        "        session.packingPlan = PcbPlacementCompactor.compactPairedVacancies(\n"
        "            session.board, constraints, session.packingPlan);\n"
        "        TaskPackingSpanObservation.emit(\"PAIRED\", session.board,\n"
        "            session.packingPlan, session.seed, session.routingSeed);\n"
        "        TaskPackingSpanObservation.emit(\"FINAL\", session.board,\n"
        "            session.packingPlan, session.seed, session.routingSeed);\n",
        newline)
    stage_new = with_newlines(
        "        // Keep the exact post-route compaction plan in its routing coordinate\n"
        "        // frame. The final display crop may translate pads by a non-grid amount.\n"
        "        PcbPlacementPlanner.Plan source = session.bestPlan;\n"
        "        final long plannedPackingRoutingSeed =\n"
        "            MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(session.packingRoutePolicy) ?\n"
        "                optionalPackingRoutingSeed(session.routingSeed) : session.routingSeed;\n"
        "        TaskPackingSpanObservation.emit(\"SOURCE\", session.board,\n"
        "            source, session.seed, plannedPackingRoutingSeed);\n"
        "        session.packingPlan = PcbPlacementCompactor.compact(session.board, constraints, source);\n"
        "        TaskPackingSpanObservation.emit(\"ORDINARY\", session.board,\n"
        "            session.packingPlan, session.seed, plannedPackingRoutingSeed);\n"
        "        session.packingPlan = PcbPlacementCompactor.compactPairedVacancies(\n"
        "            session.board, constraints, session.packingPlan);\n"
        "        TaskPackingSpanObservation.emit(\"PAIRED\", session.board,\n"
        "            session.packingPlan, session.seed, plannedPackingRoutingSeed);\n"
        "        TaskPackingSpanObservation.emit(\"FINAL\", session.board,\n"
        "            session.packingPlan, session.seed, plannedPackingRoutingSeed);\n",
        newline)
    source = replace_once(source, stage_old, stage_new, "generator",
                          "report_planned_optional_p05_seed_at_stages", changes)

    advance_old = with_newlines(
        "        session.observer.check(session.bestPlan.candidate);\n"
        "        try {\n"
        "            RoutedCandidate routedCandidate;\n"
        "            if (session.packingRoute != null) {\n"
        "                long started = System.currentTimeMillis();\n"
        "                boolean complete;\n"
        "                try {\n"
        "                    complete = session.packingRoute.advanceSlice();\n"
        "                } finally {\n"
        "                    session.statistics.routeMillis += System.currentTimeMillis() - started;\n"
        "                }\n"
        "                if (!complete) return false;\n"
        "                PcbLayerRoutingPrototype.Result routed = session.packingRoute.result();\n"
        "                session.packingRoute = null;\n"
        "                session.statistics.routes += routed.orderings;\n"
        "                session.statistics.expansions += routed.expansions;\n"
        "                MediumBoardPhysicalPolicy.PlacementCandidate placement =\n"
        "                    new MediumBoardPhysicalPolicy.PlacementCandidate(session.board,\n"
        "                        session.bestPlan.candidate, session.packingPlan);\n"
        "                routedCandidate = finishMediumTwoLayerRoute(session.board, session.seed,\n"
        "                    placement, routed);\n"
        "            } else {\n"
        "                int variation = (int) ((session.seed % 4 + 4) % 4);\n"
        "                routedCandidate = routePlan(session.board, session.seed, variation,\n"
        "                    session.bestPlan.candidate, session.observer, session.routingSeed,\n"
        "                    session.statistics, session.packingPlan, true);\n"
        "            }\n"
        "            PcbBoardLayout candidate = routedCandidate.layout;\n"
        "            if (session.envelope != null) session.envelope.requireBounds(session.board, candidate);\n"
        "            PcbPackingMetrics metrics;\n"
        "            try {\n"
        "                metrics = PcbPackingMetrics.measure(session.board, candidate);\n"
        "            } catch (PcbPackingMetrics.Unavailable unsupportedGrid) {\n"
        "                session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence\n"
        "                    .candidateUnavailable(session.packingBaselineMetrics, unsupportedGrid.getMessage());\n"
        "                return finishPackingProbe(session, \"KEPT\");\n"
        "            }\n"
        "            session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence\n"
        "                .compared(session.packingBaselineMetrics, metrics);\n"
        "            if (PcbPackingMetrics.strictlyImproves(session.packingBaselineMetrics, metrics)) {\n"
        "                session.bestLayout = candidate;\n"
        "                session.bestPlan = routedCandidate.plan;\n"
        "                session.bestArea = metrics.area;\n"
        "                session.bestScore = candidate.getRouteQualityScore(session.board);\n"
        "                if (session.mediumPolicy) {\n"
        "                    session.mediumSelectedPlacementScore =\n"
        "                        new MediumBoardPhysicalPolicy.PlacementCandidate(session.board,\n"
        "                            session.bestPlan.candidate, session.bestPlan).cheapScore;\n"
        "                    session.mediumSelectedRouteQualityMilli = routeQualityMilli(session.bestScore);\n"
        "                }\n"
        "                return finishPackingProbe(session, \"SELECTED\");\n"
        "            }\n"
        "            return finishPackingProbe(session, \"KEPT\");\n"
        "        } catch (CandidateRejected rejectedRoute) {\n"
        "            TaskPackingObservation.emit(\"PACKING_CAUGHT\", \"PACKING_EXPECTED_CATCH\",\n"
        "                session.packingPlan, session.seed, session.routingSeed,\n"
        "                session.packingBaselineHash, session.packingRoutePolicy,\n"
        "                rejectedRoute, rejectedRoute.getKind().name(), null, null, rejectedRoute.recovery);\n"
        "            return finishPackingProbe(session, \"ROUTE_REJECTED\");\n"
        "        } catch (PcbPlacementPlanner.Rejected rejectedAccess) {\n"
        "            TaskPackingObservation.emit(\"PACKING_CAUGHT\", \"PACKING_EXPECTED_CATCH\",\n"
        "                session.packingPlan, session.seed, session.routingSeed,\n"
        "                session.packingBaselineHash, session.packingRoutePolicy,\n"
        "                rejectedAccess, \"PLACEMENT\", rejectedAccess.reason, null, null);\n"
        "            return finishPackingProbe(session, \"ROUTE_REJECTED\");\n"
        "        } catch (SupportedEnvelope.Rejected rejectedEnvelope) {\n"
        "            TaskPackingObservation.emit(\"PACKING_CAUGHT\", \"PACKING_EXPECTED_CATCH\",\n"
        "                session.packingPlan, session.seed, session.routingSeed,\n"
        "                session.packingBaselineHash, session.packingRoutePolicy,\n"
        "                rejectedEnvelope, \"ENVELOPE\", rejectedEnvelope.reason.name(), null, null);\n"
        "            return finishPackingProbe(session, \"ROUTE_REJECTED\");\n"
        "        } catch (PcbBoardLayout.RouteQualityRejectedException rejectedQuality) {\n"
        "            TaskPackingObservation.emit(\"PACKING_CAUGHT\", \"PACKING_EXPECTED_CATCH\",\n"
        "                session.packingPlan, session.seed, session.routingSeed,\n"
        "                session.packingBaselineHash, session.packingRoutePolicy,\n"
        "                rejectedQuality, \"QUALITY\", null, null, null);\n"
        "            return finishPackingProbe(session, \"ROUTE_REJECTED\");\n"
        "        }\n"
        "    }\n\n",
        newline)
    if advance_old.count("session.packingPlan, session.seed, session.routingSeed,") != 4:
        raise ValueError("expected four outer packing catch seed observations")
    advance_new = advance_old.replace(
        with_newlines("        session.observer.check(session.bestPlan.candidate);\n", newline),
        with_newlines(
            "        session.observer.check(session.bestPlan.candidate);\n"
            "        final long plannedPackingRoutingSeed =\n"
            "            MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(session.packingRoutePolicy) ?\n"
            "                optionalPackingRoutingSeed(session.routingSeed) : session.routingSeed;\n",
            newline),
        1)
    advance_new = advance_new.replace(
        with_newlines("session.observer, session.routingSeed,", newline),
        with_newlines("session.observer, plannedPackingRoutingSeed,", newline),
        1)
    advance_new = advance_new.replace(
        with_newlines("session.packingPlan, session.seed, session.routingSeed,", newline),
        with_newlines("session.packingPlan, session.seed, plannedPackingRoutingSeed,", newline),
    )
    source = replace_once(source, advance_old, advance_new, "generator",
                          "route_p05_and_report_outer_seed", changes)

    return source, changes


def adapt_exporter(source: str) -> tuple:
    newline = newline_style(source)
    changes = []
    assertion_block = with_newlines(
        "        if (\"RELAY_OUTPUT\".equals(request.family)) {\n"
        "            check(SeededPcbLayoutGenerator.optionalPackingRoutingSeed(0L) ==\n"
        "                    2926452371699457122L,\n"
        "                \"optional routing seed vector 0 matches frozen reference\");\n"
        "            check(SeededPcbLayoutGenerator.optionalPackingRoutingSeed(1L) ==\n"
        "                    -1259826540137006295L,\n"
        "                \"optional routing seed vector 1 matches frozen reference\");\n"
        "            check(SeededPcbLayoutGenerator.optionalPackingRoutingSeed(-1L) ==\n"
        "                    1145332496697143667L,\n"
        "                \"optional routing seed vector -1 matches frozen reference\");\n"
        "            check(SeededPcbLayoutGenerator.optionalPackingRoutingSeed(Long.MIN_VALUE) ==\n"
        "                    721378348590188007L,\n"
        "                \"optional routing seed vector LONG_MIN matches frozen reference\");\n"
        "            check(SeededPcbLayoutGenerator.optionalPackingRoutingSeed(Long.MAX_VALUE) ==\n"
        "                    426595435461648541L,\n"
        "                \"optional routing seed vector LONG_MAX matches frozen reference\");\n"
        "            check(SeededPcbLayoutGenerator.optionalPackingRoutingSeed(\n"
        "                    3078526323410797175L) == -7609816130686485171L,\n"
        "                \"optional routing seed actual SOURCE value matches frozen reference\");\n"
        "        }\n",
        newline)
    anchor = with_newlines("        Request request = parseArguments(args);\n", newline)
    source = replace_once(source, anchor, anchor + assertion_block, "exporter",
                          "assert_optional_routing_seed_vectors", changes)
    return source, changes


def restore_exact(originals: dict, outputs: dict, changes: list) -> None:
    restored = dict(outputs)
    for change in reversed(changes):
        owner = change["owner"]
        if restored[owner].count(change["new"]) != 1:
            raise AssertionError("reverse count failed for " + change["label"])
        restored[owner] = restored[owner].replace(change["new"], change["old"], 1)
    if restored != originals:
        raise AssertionError("approved replacements did not restore exact source bytes")


def diff_text(before: str, after: str, before_name: str, after_name: str) -> str:
    return "".join(difflib.unified_diff(
        before.splitlines(True), after.splitlines(True),
        fromfile=before_name, tofile=after_name))


def write_new(path: Path, data: bytes) -> None:
    if path.exists():
        raise AssertionError("refusing to overwrite existing preparation file: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main(private_root_text: str) -> None:
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    baseline = inputs["record"]()
    if baseline != inputs["read"](HERE / "baseline-inputs.json"):
        raise AssertionError("frozen packing-route baseline changed")
    if baseline["baseCommit"] != BASE:
        raise AssertionError("unexpected packing-route baseline commit")

    seed = runpy.run_path(str(HERE / "seed_reference.py"))
    vector_payload = seed["vector_payload"]()
    expected_vectors = [
        {"sourceSeed": str(source_seed), "derivedSeed": str(derived_seed)}
        for source_seed, derived_seed in EXPECTED_DERIVATIONS
    ]
    if vector_payload["vectors"] != expected_vectors:
        raise AssertionError("independent seed vectors do not match frozen literals")
    if vector_payload["actualSourceSeed"] != str(ACTUAL_SOURCE_SEED):
        raise AssertionError("unexpected actual SOURCE routing seed")

    borrowed = {name: (BORROW / name).read_bytes() for name in SOURCE_NAMES}
    expected_borrowed = baseline["controlPrivateInputs"]
    actual_borrowed = {name: sha_bytes(data) for name, data in borrowed.items()}
    if actual_borrowed != expected_borrowed:
        raise AssertionError("borrowed control private inputs changed")

    exporter = borrowed["common/SparseNormalBoardDiagnostic.java"].decode("utf-8")
    adapter = borrowed["common/verify-two-pad-span.ps1"].decode("utf-8")
    generator = borrowed["control/SeededPcbLayoutGenerator.java"].decode("utf-8")
    planner = borrowed["control/PcbPlacementPlanner.java"]
    compactor = borrowed["control/PcbPlacementCompactor.java"]
    variant_exporter, exporter_changes = adapt_exporter(exporter)
    variant_generator, generator_changes = adapt_generator(generator)
    all_changes = exporter_changes + generator_changes
    restore_exact(
        {"exporter": exporter, "generator": generator},
        {"exporter": variant_exporter, "generator": variant_generator},
        all_changes)

    exporter_suffix_marker = "        CirSim prior = CircuitElm.sim;"
    if exporter.split(exporter_suffix_marker, 1)[1] != \
            variant_exporter.split(exporter_suffix_marker, 1)[1]:
        raise AssertionError("exporter assertions/export suffix changed")
    observer_marker = "/** Task-only observer appended after the unchanged normal exporter. */"
    if exporter.split(observer_marker, 1)[1] != variant_exporter.split(observer_marker, 1)[1]:
        raise AssertionError("exporter observer classes changed")
    route_method_marker = "    private RoutedCandidate routePlan("
    if generator.split(route_method_marker, 1)[1] != variant_generator.split(route_method_marker, 1)[1]:
        raise AssertionError("inner routePlan observer changed")
    p07_marker = "        if (MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(session.packingRoutePolicy)) {"
    if generator.split(p07_marker, 1)[1].split("        return true;", 1)[0] != \
            variant_generator.split(p07_marker, 1)[1].split("        return true;", 1)[0]:
        raise AssertionError("P07 route setup changed")
    if planner != borrowed["control/PcbPlacementPlanner.java"] or \
            compactor != borrowed["control/PcbPlacementCompactor.java"]:
        raise AssertionError("variant planner/compactor source changed unexpectedly")
    if variant_generator.count("static long optionalPackingRoutingSeed(long base)") != 1:
        raise AssertionError("optional stream helper count is not one")
    if variant_generator.count("optional-p05-proposal") != 1:
        raise AssertionError("optional stream semantic key count is not one")

    private_root = Path(private_root_text).resolve()
    outputs = {
        "common/SparseNormalBoardDiagnostic.java": variant_exporter.encode("utf-8"),
        "common/verify-two-pad-span.ps1": borrowed["common/verify-two-pad-span.ps1"],
        "variant/SeededPcbLayoutGenerator.java": variant_generator.encode("utf-8"),
        "variant/PcbPlacementPlanner.java": planner,
        "variant/PcbPlacementCompactor.java": compactor,
    }
    output_hashes = {name: sha_bytes(data) for name, data in outputs.items()}
    baseline_seal = sha_bytes((HERE / "baseline-inputs.json").read_bytes())
    private_inputs_path = HERE / "private-inputs.json"
    source_review_path = HERE / "source-review.json"
    vectors_path = HERE / "derivation-vectors.json"
    generator_diff_path = HERE / "variant-generator-diff.txt"
    exporter_diff_path = HERE / "exporter-diff.txt"
    notes_path = HERE / "preparation-notes.md"
    for path in (private_inputs_path, source_review_path, vectors_path,
                 generator_diff_path, exporter_diff_path, notes_path):
        if path.exists():
            raise AssertionError("refusing to overwrite existing packet file: " + str(path))

    # Freeze the independent vectors before any private source copy is exposed.
    vectors_bytes = (json.dumps(vector_payload, indent=2, sort_keys=True) + "\n").encode("utf-8")
    vectors_path.write_bytes(vectors_bytes)
    for name, data in outputs.items():
        write_new(private_root / name, data)

    changes_manifest = []
    for change in all_changes:
        changes_manifest.append({
            "owner": change["owner"],
            "label": change["label"],
            "replacements": 1,
            "oldSha256": sha_text(change["old"]),
            "newSha256": sha_text(change["new"]),
        })
    manifest = {
        "status": "PREPARED_FIXED_OPTIONAL_P05_STREAM",
        "baseCommit": BASE,
        "baselineSealSha256": baseline_seal,
        "request": baseline["request"],
        "candidateInterpretation": baseline["candidateInterpretation"],
        "privateInputs": output_hashes,
        "controlPrivateInputs": actual_borrowed,
        "derivationVectorsSha256": sha_bytes(vectors_bytes),
        "changes": changes_manifest,
        "checks": {
            "baselineAudit": "PASS",
            "seedReference": "PASS",
            "generatorReverse": "PASS",
            "exporterReverse": "PASS",
            "plannerUnchanged": "PASS",
            "compactorUnchanged": "PASS",
            "exporterSuffixUnchanged": "PASS",
            "observerClassesUnchanged": "PASS",
            "innerRoutePlanUnchanged": "PASS",
            "p07RouteSetupUnchanged": "PASS",
        },
        "limits": "Pure source preparation only; Java compilation, native canaries, routing, browser, data reader, and GO decision are NOT RUN.",
    }
    private_inputs_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    generator_diff_path.write_text(
        diff_text(generator, variant_generator,
                  "control/SeededPcbLayoutGenerator.java",
                  "variant/SeededPcbLayoutGenerator.java"),
        encoding="utf-8", newline="\n")
    exporter_diff_path.write_text(
        diff_text(exporter, variant_exporter,
                  "common/SparseNormalBoardDiagnostic.java",
                  "variant/common/SparseNormalBoardDiagnostic.java"),
        encoding="utf-8", newline="\n")

    review = {
        "status": "PREPARED_FIXED_OPTIONAL_P05_STREAM",
        "baseCommit": BASE,
        "baselineSealSha256": baseline_seal,
        "request": baseline["request"],
        "candidateInterpretation": baseline["candidateInterpretation"],
        "borrowedControlPrivateSources": actual_borrowed,
        "privateInputs": output_hashes,
        "derivationVectors": vector_payload,
        "changes": changes_manifest,
        "sourceReview": [
            "The generator adds one package-access pure optionalPackingRoutingSeed helper using the predeclared NamedRandomStreams tuple.",
            "Only the optional P05 routePlan argument in advancePackingProbe receives the derived seed; Session.routingSeed, compactProbe callers, P07 setup, planners, and compactors remain unchanged.",
            "SOURCE/ORDINARY/PAIRED/FINAL passive stage observations and all four outer typed catches report the derived P05 seed or base P07 seed; the inner routePlan observer receives its actual route argument unchanged.",
            "Exporter assertions compare the helper with six independently frozen explicit long literals, guarded to RELAY_OUTPUT, before the existing assertion/export/disposal suffix.",
        ],
        "checks": manifest["checks"],
        "limits": manifest["limits"],
        "pythonPreparationExit": 0,
    }
    source_review_path.write_text(json.dumps(review, indent=2) + "\n", encoding="utf-8")

    notes = """# Fixed optional P05 routing stream preparation

Preparation completed with Python exit 0 after the frozen baseline audit.

Base: `{base}`

Request: `{request}`

Private root: `unique task-owned OS-temp directory (private q60 pointer)`

The exact accepted control private five were audited from the prior relay
packet. The prepared manifest exposes these five variant paths:

- `common/SparseNormalBoardDiagnostic.java` (bounded relay seed assertions)
- `common/verify-two-pad-span.ps1` (copied exact relay adapter)
- `variant/SeededPcbLayoutGenerator.java` (fixed optional P05 stream)
- `variant/PcbPlacementPlanner.java` (copied exact planner)
- `variant/PcbPlacementCompactor.java` (copied exact compactor)

The generator patch adds one package-access helper, changes only the optional
P05 route argument, and reports planned seeds at the four passive stages and
the four outer typed catches. The P07 setup, `Session.routingSeed`, inner
route-plan observer, planners, and compactor are preserved. The exporter adds
six guarded helper-versus-literal assertions before its existing suffix.

Independent source seed: `{actual_source}`
Independent derived seed: `{actual_derived}`
Derivation vectors: `derivation-vectors.json`

Pure preparation checks: baseline audit PASS; seed reference PASS; generator
and exporter reverse checks PASS; planner/compactor and exporter observer
suffix checks PASS. Java compilation, native canaries, routing, browser, data
reader, and GO decision are NOT RUN in this leaf.
""".format(
        base=BASE,
        request=baseline["request"],
        private_root=private_root,
        actual_source=vector_payload["actualSourceSeed"],
        actual_derived=vector_payload["actualDerivedSeed"],
    )
    notes_path.write_text(notes, encoding="utf-8", newline="\n")
    print("PASS prepared fixed optional P05 stream private five; baseline, vectors, reverse checks, and source diffs sealed")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare_sources.py <private-root>")
    main(sys.argv[1])
