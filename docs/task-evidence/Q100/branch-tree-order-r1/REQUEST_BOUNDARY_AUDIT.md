# Request boundary and observation audit

Status: source-only finding on the initial strategy seam, before runtime.
This file records the missing prerequisite, not completed implementation proof.

The real generator's `advanceMediumTwoLayerRoute` processes a COMPLETE stream by
obtaining its OrderingSummary, storing one OrderingSearchReceipt and outcome,
accounting cumulative work, retiring the P07 stream and returning false. That
is the correct request boundary. It is not generator completion. Another advance
would increment the generator attempt before the AttemptObserver is called, so
an observer sentinel is too late to prove no successor mutation. Throwing from
a routing diagnostic instead is an aborted operation, not a completed rejection.

The smallest missing seam is an opt-in immutable request-boundary snapshot after
the receipt/outcome and stream retirement. The diagnostic session must seal there
and reject subsequent advance before changing attempt, placement or request
state. It stays incomplete; no final medium-policy result or accepted-generator
result is invented. The harness reports request outcome separately, stops and
drops its live references. Normal generator entrypoints remain unsealed.

The context must derive from the actual generator-selected pristine placement:
request/index, selected attempt, eligible attempt order, named seeds and physical
fingerprint. Existing FixedPrefixCaptureContext already models these values;
reuse it instead of reconstructing attempt4 with a standalone planner. The actual
generator diagnostic already verifies request0/attempt4. Its direct fixed-attempt
branch diagnostic is an observation pattern, not a valid replacement experiment.

Thread the existing P07.Diagnostics callback into this actual ordering stream.
Its immutable BranchSnapshot includes pass, net, start/root pad and work/reached/
via counts. Collect at most3*(pads-nets), not a live graph every microcall. Compare
complete baseline/control records and pass0 parity. Record whether the guarded
chooser was exercised, and the first actual pass1 branch-selection divergence.

BranchSnapshot currently lacks a prefix-geometry hash; the fixed-prefix failure
lineage has one, but excludes FOUND branches. A bounded branch-start observation
is still needed before claiming exact geometry at the first divergence. Charge
any traversal to the same operation/group and retain only bounded immutable data.
If the stream ends FIRST_SUCCESS_UNGUIDED in pass0, pass1 is NOT_OBSERVED; the
experiment must not claim its treatment ran.

The source review ran no test, parser, JVM, native or authored operation and read
no raw capture or protected HARD body. Root assigned only the generator stop/
Diagnostics seam back to its source worker. Root owns the later observation
contract, wire freeze, actual tests, final gates and publication.
