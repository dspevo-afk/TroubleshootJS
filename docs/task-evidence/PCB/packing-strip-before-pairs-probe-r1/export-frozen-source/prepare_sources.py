"""Prepare the private optional strip-before-pairs source arm."""

import difflib
import hashlib
import json
import runpy
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
CONTROL = (HERE.parent / "fresh-relay-stage-diagnostic-r1" /
           "export-frozen-source" / "private-inputs")
BASE = "3460f1c9e26161d910249a850adb65f562fcffbe"
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


def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha_text(text):
    return sha_bytes(text.encode("utf-8"))


def newline_style(source):
    if "\r\n" in source:
        return "\r\n"
    if "\r" in source:
        return "\r"
    return "\n"


def source_newlines(text, newline):
    return text.replace("\n", newline)


def replace_once(source, old, new, owner, label, changes):
    newline = newline_style(source)
    old = source_newlines(old, newline)
    new = source_newlines(new, newline)
    count = source.count(old)
    if count != 1:
        raise ValueError("%s replacement count is %d, expected 1" % (label, count))
    changes.append({"owner": owner, "label": label, "old": old, "new": new})
    return source.replace(old, new, 1)


def adapt_compactor(source):
    changes = []
    anchor = """    /** Pair search belongs only after selection of the routed incumbent. */
"""
    insertion = """    static PcbPlacementPlanner.Plan compactOptionalStrips(
            TroubleshootBoard board, PcbPlacementConstraints constraints,
            PcbPlacementPlanner.Plan input) {
        return compactStrips(board, constraints, input, true);
    }

""" + anchor
    source = replace_once(source, anchor, insertion, "compactor",
                          "add_optional_strip_wrapper", changes)
    return source, changes


def adapt_generator(source):
    changes = []
    old = """        session.packingPlan = PcbPlacementCompactor.compact(session.board, constraints, source);
"""
    new = """        session.packingPlan =
            MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(session.packingRoutePolicy) ?
                PcbPlacementCompactor.compactOptionalStrips(session.board, constraints, source) :
                PcbPlacementCompactor.compact(session.board, constraints, source);
"""
    source = replace_once(source, old, new, "generator",
                          "use_optional_strips_only_for_p05", changes)
    return source, changes


def adapt_exporter(source):
    changes = []
    call_anchor = """        PcbPlacementConstraints constraints = board.getPlacementConstraints();
"""
    call_new = call_anchor + """        runOptionalStripBudgetChecks(board, layout, constraints);
"""
    source = replace_once(source, call_anchor, call_new, "exporter",
                          "run_actual_optional_strip_budget_fixture", changes)

    marker = """    private static void requireExactInventories(TroubleshootBoard board,
"""
    helpers = """    private static void runOptionalStripBudgetChecks(TroubleshootBoard board,
            PcbBoardLayout layout, PcbPlacementConstraints constraints) {
        check(constraints.placementPackingVersion ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION,
            "optional strip fixture uses the connected packing contract");
        Vector<PcbFootprint> footprints = orderedFootprints(board, layout);
        check(footprints.size() == 9 && countPads(footprints) == 22,
            "optional strip fixture retains the accepted nine-part twenty-two-pad inventory");
        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String layoutBefore = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        int[] inputEvaluations = new int[] {59999, 60000, 0};
        for (int evaluations : inputEvaluations)
            runOptionalStripBudgetCheck(board, constraints, layout.getBoardOutline(),
                footprints, evaluations);
        check(graphBefore.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(board)) &&
                layoutBefore.equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
            "optional strip fixture preserves the live graph and selected layout");
    }

    private static void runOptionalStripBudgetCheck(TroubleshootBoard board,
            PcbPlacementConstraints constraints, Rectangle outline,
            Vector<PcbFootprint> footprints, int inputEvaluations) {
        PcbPlacementPlanner.Plan input = new PcbPlacementPlanner.Plan(outline, footprints,
            new Vector<PcbLayoutRegion>(), 0L, 0L, 4, inputEvaluations);
        String inputGeometry = PlayerSessionFingerprint.of(input.materialize().geometryFingerprint());
        PcbPlacementPlanner.Plan result = PcbPlacementCompactor.compactOptionalStrips(
            board, constraints, input);
        String inputGeometryAfter =
            PlayerSessionFingerprint.of(input.materialize().geometryFingerprint());
        boolean samePlan = PcbPlacementCompactor.samePlacement(input, result);
        boolean inputUnchanged = inputGeometry.equals(inputGeometryAfter);
        int parts = result.footprints.size();
        int pads = countPads(result.footprints);
        check(result.evaluations >= inputEvaluations &&
                result.evaluations <= PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS,
            "optional strip fixture remains within the shared placement evaluation cap");
        check(inputUnchanged, "optional strip fixture does not mutate its input plan");
        check(parts == input.footprints.size() && pads == countPads(input.footprints),
            "optional strip fixture preserves the plan inventory");
        if (inputEvaluations >= 59999)
            check(result == input && samePlan && result.evaluations == inputEvaluations,
                "optional strip budget guard returns the exact near-cap input");
        StringBuilder json = new StringBuilder(180);
        json.append('{').append('"').append("inputEvaluations").append('"').append(':')
            .append(inputEvaluations);
        json.append(',').append('"').append("resultEvaluations").append('"').append(':')
            .append(result.evaluations);
        json.append(',').append('"').append("samePlan").append('"').append(':')
            .append(samePlan);
        json.append(',').append('"').append("inputUnchanged").append('"').append(':')
            .append(inputUnchanged);
        json.append(',').append('"').append("parts").append('"').append(':')
            .append(parts);
        json.append(',').append('"').append("pads").append('"').append(':')
            .append(pads).append('}');
        System.out.println("PCB_OPTIONAL_STRIP_BUDGET_CHECKPOINT " + json.toString());
    }

    private static int countPads(Vector<PcbFootprint> footprints) {
        int result = 0;
        for (PcbFootprint footprint : footprints) result += footprint.getPads().size();
        return result;
    }

""" + marker
    source = replace_once(source, marker, helpers, "exporter",
                          "add_actual_budget_fixture_helpers", changes)
    return source, changes


def restore_exact(originals, outputs, changes):
    restored = dict(outputs)
    for change in reversed(changes):
        owner = change["owner"]
        if restored[owner].count(change["new"]) != 1:
            raise AssertionError("reverse count failed for " + change["label"])
        restored[owner] = restored[owner].replace(change["new"], change["old"], 1)
    if restored != originals:
        raise AssertionError("approved replacements did not restore exact source bytes")


def diff_text(before, after, before_name, after_name):
    return "".join(difflib.unified_diff(
        before.splitlines(True), after.splitlines(True),
        fromfile=before_name, tofile=after_name))


def write_new(path, data):
    if path.exists():
        raise AssertionError("refusing to overwrite existing preparation file: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def main(private_root_text):
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    baseline = inputs["record"]()
    if baseline != inputs["read"](HERE / "baseline-inputs.json"):
        raise AssertionError("frozen strip-before-pairs baseline changed")
    if baseline["baseCommit"] != BASE:
        raise AssertionError("unexpected strip-before-pairs baseline commit")

    borrowed = {name: (CONTROL / name).read_bytes() for name in SOURCE_NAMES}
    expected_borrowed = baseline["controlPrivateInputs"]
    actual_borrowed = {name: sha_bytes(data) for name, data in borrowed.items()}
    if actual_borrowed != expected_borrowed:
        raise AssertionError("original relay control private inputs changed")

    exporter = borrowed["common/SparseNormalBoardDiagnostic.java"].decode("utf-8")
    adapter = borrowed["common/verify-two-pad-span.ps1"]
    generator = borrowed["control/SeededPcbLayoutGenerator.java"].decode("utf-8")
    planner = borrowed["control/PcbPlacementPlanner.java"]
    compactor = borrowed["control/PcbPlacementCompactor.java"].decode("utf-8")
    variant_exporter, exporter_changes = adapt_exporter(exporter)
    variant_generator, generator_changes = adapt_generator(generator)
    variant_compactor, compactor_changes = adapt_compactor(compactor)
    all_changes = exporter_changes + generator_changes + compactor_changes
    restore_exact(
        {"exporter": exporter, "generator": generator, "compactor": compactor},
        {"exporter": variant_exporter, "generator": variant_generator,
         "compactor": variant_compactor},
        all_changes)

    if variant_generator.count("compactOptionalStrips(") != 1:
        raise AssertionError("generator optional strip wrapper call count is not one")
    if variant_compactor.count("compactOptionalStrips(") != 1:
        raise AssertionError("compactor optional strip wrapper count is not one")
    if variant_exporter.count("PCB_OPTIONAL_STRIP_BUDGET_CHECKPOINT") != 1:
        raise AssertionError("budget fixture output prefix count is not one")
    if exporter.split("    private static void requireExactInventories", 1)[1] != \
            variant_exporter.split("    private static void requireExactInventories", 1)[1]:
        raise AssertionError("exporter existing assertion/serialization suffix changed")
    if generator.split("    private static int packingExpansionLimit", 1)[1] != \
            variant_generator.split("    private static int packingExpansionLimit", 1)[1]:
        raise AssertionError("generator routing and acceptance suffix changed")
    if compactor.split("    static boolean samePlacement", 1)[1] != \
            variant_compactor.split("    static boolean samePlacement", 1)[1]:
        raise AssertionError("compactor original strip/pair/routed suffix changed")

    private_root = Path(private_root_text).resolve()
    outputs = {
        "common/SparseNormalBoardDiagnostic.java": variant_exporter.encode("utf-8"),
        "common/verify-two-pad-span.ps1": adapter,
        "variant/SeededPcbLayoutGenerator.java": variant_generator.encode("utf-8"),
        "variant/PcbPlacementPlanner.java": planner,
        "variant/PcbPlacementCompactor.java": variant_compactor.encode("utf-8"),
    }
    output_hashes = {name: sha_bytes(data) for name, data in outputs.items()}
    baseline_seal = sha_bytes((HERE / "baseline-inputs.json").read_bytes())
    packet_paths = (
        HERE / "private-inputs.json",
        HERE / "source-review.json",
        HERE / "generator-diff.txt",
        HERE / "compactor-diff.txt",
        HERE / "exporter-diff.txt",
        HERE / "preparation-notes.md",
    )
    for path in packet_paths:
        if path.exists():
            raise AssertionError("refusing to overwrite existing packet file: " + str(path))

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
    checks = {
        "baselineAudit": "PASS",
        "originalControlPrivateInputs": "PASS",
        "generatorReverse": "PASS",
        "compactorReverse": "PASS",
        "exporterReverse": "PASS",
        "plannerUnchanged": "PASS",
        "adapterUnchanged": "PASS",
        "generatorSuffixUnchanged": "PASS",
        "compactorSuffixUnchanged": "PASS",
        "exporterSuffixUnchanged": "PASS",
        "runtimeBudgetFixture": "NOT RUN (Java/native leaf restriction)",
    }
    manifest = {
        "status": "PREPARED_PRIVATE_STRIP_BEFORE_PAIRS",
        "baseCommit": BASE,
        "baselineSealSha256": baseline_seal,
        "candidateInterpretation": "PRIVATE_OPTIONAL_STRIP_BEFORE_PAIRS@1;NOT_PRODUCTION_MODEL",
        "privateInputs": output_hashes,
        "controlPrivateInputs": actual_borrowed,
        "changes": changes_manifest,
        "checks": checks,
        "limits": "Pure source preparation only; Java compilation, native canaries, routing, browser, data reader, and GO decision are NOT RUN.",
    }
    (HERE / "private-inputs.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (HERE / "generator-diff.txt").write_text(
        diff_text(generator, variant_generator,
                  "control/SeededPcbLayoutGenerator.java",
                  "variant/SeededPcbLayoutGenerator.java"),
        encoding="utf-8", newline="\n")
    (HERE / "compactor-diff.txt").write_text(
        diff_text(compactor, variant_compactor,
                  "control/PcbPlacementCompactor.java",
                  "variant/PcbPlacementCompactor.java"),
        encoding="utf-8", newline="\n")
    (HERE / "exporter-diff.txt").write_text(
        diff_text(exporter, variant_exporter,
                  "common/SparseNormalBoardDiagnostic.java",
                  "variant/common/SparseNormalBoardDiagnostic.java"),
        encoding="utf-8", newline="\n")

    review = {
        "status": manifest["status"],
        "baseCommit": BASE,
        "baselineSealSha256": baseline_seal,
        "candidateInterpretation": manifest["candidateInterpretation"],
        "preReleaseCorrections": [
            {
                "attempt": "initial pure preparation",
                "exit": 1,
                "status": "REJECTED_BEFORE_OUTPUT",
                "reason": "static checkpoint-prefix assertion incorrectly expected zero occurrences",
            },
            {
                "attempt": "first successful source preparation",
                "exit": 0,
                "status": "REJECTED_BEFORE_SEAL",
                "badExporterSha256": "NOT_CAPTURED; root observed final manifest while source outputs were in flight",
                "badExcerpt": "json.append(\"{\"inputEvaluations\":\")",
                "reason": "Python source-template escaping produced malformed Java JSON string literals; caught by source inspection before compilation",
            },
        ],
        "privateInputs": output_hashes,
        "controlPrivateInputs": actual_borrowed,
        "changes": changes_manifest,
        "sourceReview": [
            "The variant generator changes only beginPackingProbe: P05 uses compactOptionalStrips and every other policy keeps the original compact call.",
            "The package wrapper delegates exactly to the existing optional-budget compactStrips path; compact, compactRouted, pair compaction, primary callers, and the shared 60000 cap remain unchanged.",
            "The exporter fixture uses orderedFootprints from the actual accepted board/layout, an actual outline, empty regions, metadata candidate 4, and input evaluations 59999, 60000, and 0.",
            "The fixture checks exact near-cap identity, cumulative evaluation bounds, input materialized geometry immutability, inventory preservation, and live graph/layout preservation, then emits three runtime checkpoint records.",
            "The original exporter suffix after requireExactInventories and the original generator/compactor suffixes are byte-for-byte preserved.",
        ],
        "checks": checks,
        "limits": manifest["limits"],
        "pythonPreparationExit": 0,
    }
    (HERE / "source-review.json").write_text(
        json.dumps(review, indent=2) + "\n", encoding="utf-8")

    notes = """# Private strip-before-pairs source preparation

Preparation completed with Python exit 0 after the frozen baseline audit.

Preparation history: the first pure invocation exited 1 before writing outputs
because a static check incorrectly expected zero runtime checkpoint-prefix
occurrences. A later output contained the malformed excerpt
json.append("{"inputEvaluations":"); root inspection caught the quote escaping
before compilation or sealing. Its hash was not captured: root saw the final
manifest while outputs were still in flight and initially misidentified final
SHA1892... as rejected. That hash belongs to the corrected char-quote builder.
The excerpt preserves the actual issue; no rejected-source digest is claimed.
Final pure preparation exited0. No pre-fix Java/native PASS exists.

Base: {base}

Request: {request}

Private root: unique task-owned OS-temp directory (private q60 pointer)

The prepared private arm contains exactly these five inputs:

- common/SparseNormalBoardDiagnostic.java (actual-inventory budget fixture)
- common/verify-two-pad-span.ps1 (original relay adapter, copied exact)
- variant/SeededPcbLayoutGenerator.java (P05 strip-before-pairs selection)
- variant/PcbPlacementPlanner.java (original relay planner, copied exact)
- variant/PcbPlacementCompactor.java (optional-strip wrapper)

The generator keeps the original stream, primary/preroute callers, P07 path,
pair pass, observers, metadata, and shared cumulative evaluation limit. Only
the P05 call in beginPackingProbe selects the wrapper. The wrapper delegates
to the existing compactStrips(..., true) path.

The exporter fixture uses the accepted physical board inventory and checks
input evaluations 59999, 60000, and 0. Java/native execution, routing,
browser, data-reader qualification, and GO decision are NOT RUN in this leaf.
""".format(base=BASE, request=baseline["request"])
    (HERE / "preparation-notes.md").write_text(notes, encoding="utf-8", newline="\n")
    print("PASS prepared private strip-before-pairs five; baseline, source reverses, suffix checks, and manifest sealed")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare_sources.py <private-root>")
    main(sys.argv[1])

