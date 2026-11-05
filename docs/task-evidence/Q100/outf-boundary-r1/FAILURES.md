# OUT_F diagnostic failures and limits

The following paragraphs preserve the status at each attempt. Final fresh gates
are recorded in README/qualification; they do not reclassify these failures.

Reader units R1: FAIL. Actual67 methods ran in14.086s with94 failed assertions/
subtests and15 errors. The selected process exited1 with termination proven;
source stayed stable. Wrapper time14.5023654s includes process cleanup; a separate
cleanup interval is NOT_EXPORTED. Most cases were masked by fixture REPORT GROUP
records carrying an extra forbidden group key. A malformed-checkpoint fixture
also raised TypeError while repacking instead of reaching the strict consumer.
The complete process receipt, source snapshots and stderr remain externally in
reader-units-v5-r1. No native proof or partial suite PASS is inferred. R2 fixes
those fixture causes; its fresh gate remains pending at this checkpoint.

Reader units R2: FAIL,67 methods/14.590s,2 failed assertions and1 error; wrapper
14.9940283s including process cleanup, separate cleanup NOT_EXPORTED. Exit1,
termination proven and source stable. Reuse fixture snapshots omitted an existing
hole from prefixViaCount. Review also found the reader counted only VIA-kind
holes even though the actual source field is BranchSearch.holes.length. R3 fixes
the fixture counts and checks every hole kind against that source field. Foreign/
NPTH and prefix-deletion negative expectations stay intact. Complete R2 receipts
and source snapshots remain external; no earlier result is reclassified.

Before implementation, one source-map note lost the placement seed minus sign.
Root corrected it against accepted qualification before freeze. The original note
is preserved externally; there was no wrong-seed execution. Producer/reader
source reviews then exposed serialization-accounting and nested-type ambiguity.
The R2 wire clarification was frozen before affected execution and raises no cap.

R4 amends only the newly chosen diagnostic snapshot cap32k->1048576 entries.
Accepted v4 target prefix271606bytes cannot fit32k, even at the minimum possible
UTF16 size. Actual after native fingerprint observation remains required. Earlier
plan/wire freezes and the exact old receipt/index pins are preserved; all original
routing/time/group/cleanup and1MiB preimage bounds remain. CAPTURE stays32768
diagnostic units per group. This amendment preceded every v5 data runtime; no
native failure was erased, reclassified or avoided after execution.

CLI R1: whole-wrapper FAIL after32 per-case expected results. PS5 could not emit
the GenericList through @($results), so no aggregate receipt was produced. All
inputs, child receipts and the verified junction unlink/target-preservation proof
remain under cli-prep-r1/runs/cli-v5-r1. R2 uses explicit ToArray and reruns every
control in a fresh namespace. No partial R1 result is promoted to whole-run PASS.

Source review of writer3604 found3 blockers before compilation: missing path
verification cursor, metadata arrays undercounted205 versus at least208 entries,
and duplicate UTF8 encoding at chunk overflow. Writer942d fixes those3; bounded
source re-review is CLEAR_SOURCE_ONLY for those changes, not runtime acceptance.

JDK8 compile/tiny host R1: FAIL at COMPILE on writer942d/source manifest ecd3881e
(full lowercase pin is retained externally). The actual compiler exited1 with
termination proven and7 diagnostics: Object-typed layout used as PcbBoardLayout,
and missing sha256Digest factory. Driver elapsed25.2844009s; outer26.0659259s
includes child teardown. Native launch was NOT_REACHED; no tiny path/publication
or cleanup qualification is inferred. Failed scratch,1075-source command, bridges,
process receipts and stderr remain in host-prep-r2/host-tiny-v5-r1. Writera70f fixes
the declared layout type and standard SHA256 factory only. Fresh actual compile
and native gates are still required; the original failure remains FAIL.

Tiny host R2: FAIL at INDEPENDENT_READER/PUBLISHER_CAPTURE_TREATMENT_PARITY.
Actual JDK8 compile and native process both exited0 with termination proven; all
five per-operation reports parsed before the cross-arm comparison failed. Outer
109.4194393s includes child teardown; scratch was removed. Root inspection of all
291 treatment/capture branches found zero differences after excluding transport
group IDs. Inline charged CAPTURE groups shifted pass2 group43->134. Results,
requests, net vectors, raw routing work, geometry and diagnostic-clone rows match.
R3 reader excludes only operation/group for that3/4 semantic comparison, while
individual group binding/accounting and canonical1/2 comparisons stay strict.
The new comparison regression also rejects changed branch semantics and order.
Old host R2 remains FAIL; fresh host R3 is required and separately reviewed.

Prior accepted net-priority failures remain in its own packet. They are not new
OUT_F successes. The old E06R1 cleanup failure and uncertain consumed-close
resources remain unresolved and are outside this scope's cleanup authority.

Final controls orchestration: two root invocations selected the default shell
instead of explicit PS5. Both rejected SELECTED_PS51_REQUIRED before creating a
namespace; neither is a host-control PASS. Explicit PS5 wrapper R1 then failed
after the expected manifest rejection because it read the deliberately negative
child LASTEXITCODE. R2 reads the asserted structured receipt instead; manifest
and raw-source controls passed in fresh namespaces. Its bridge control used the
AUTHORED manifest with TINY mode and correctly failed HOST_SOURCE_IDENTITY before
bridge generation. It remains FAIL/not bridge proof. Root verified termination,
exact scratch containment/creation and the single preflight file, retained that
file and removed only that owned scratch in0.1961919s. Fresh final bridge R4 uses
the actual tiny manifest and passes expected FROZEN_BRIDGE_MANIFEST_MISMATCH,
no compiler/native launch and owned scratch removal0.0784067s. Full controls are
not inferred from the partially failed orchestration wrapper.

The first final-audit invocation supplied a dotted run-name disallowed by its
argument contract; it failed before output or file audits. Fresh valid-name run
PASS compares every1080 native and1202 production input in Z and clean Y.

Final tiny R3 and authored R1 actual JDK8/native/reader/root reviews PASS only for
the diagnostic scope. The authored actual Publisher quality rejection remains a
negative routing outcome, not a layout PASS. Independent reader/source/runtime
proofs and exact timing envelopes are recorded in qualification.json. Separate
cleanup/final-receipt close are NOT_EXPORTED. No fresh production gate claimed.
