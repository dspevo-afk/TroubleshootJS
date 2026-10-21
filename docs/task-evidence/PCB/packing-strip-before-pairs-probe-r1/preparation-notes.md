# Private strip-before-pairs source preparation

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

Base: 3460f1c9e26161d910249a850adb65f562fcffbe

Request: tsj-alpha/6/EASY/RELAY_OUTPUT/-397178238694963708

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
