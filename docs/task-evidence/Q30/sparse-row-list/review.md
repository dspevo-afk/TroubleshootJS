# Independent row-list review

Read-only Luna/MAX review of the frozen experimental two-file delta: no
correctness blocker found. The reviewer did not run tests or builds. Root owns
the gate receipts and rejection decision.

Workspace reset precedes the finite input scan. Lists contain only finite,
post-scaling nonzero lower entries, appended in ascending column order. Each
column processes rows in ascending order and each row consumes its list in
ascending order, preserving the historical nonzero subtraction sequence and
coefficient readiness. Pivoting moves numerical row references and their lists
and counts together. Scratch belongs to one CirSim; standalone calls allocate
fresh scratch. Independent historical factor/solve routines remain unchanged.
The shared-workspace nonfinite-input reset has no separate assertion, but reset
occurs before the scan; this is an optional coverage gap, not a found defect.
Current runtime, snapshot and inversion callers retain no individual row aliases.
Hypothetical external alias compatibility is not claimed. Signed-zero comparison
retains the existing numeric-equivalence limitation.

The reviewer inspected all five emitted GWT permutations. Their LU shapes agree.
The Safari bodies selected by the generated WebKit branch are candidate
`A21BEB0D0AFE3CC1B65EBCE803FA1EEB.cache.js` and accepted reference
`6D05FA9C4BD2A9E9EA0B62AC2CEAF7C0.cache.js`. Literal user-agent text was not
captured; selection is inferred from the generated loader and recorded Edge
executable. The candidate uses direct array accesses in its hot loop, with no
test getter bounds guards. It reloads each upper coefficient for each row's
retained lower entry; the accepted kernel hoists that coefficient once and
streams rows. List indirections, repeated loads and count reset are plausible
costs; the scalar accumulator avoids some writes. This inspection cannot assign
causal runtime shares to those operations.

The fresh measured pair is decisive for this experiment: candidate cold time
is 4,744 ms worse and proof time 4,943 ms worse. Root rejected and removed the
candidate after preserving its patch and evidence. No correctness-review PASS
is promoted to a performance or Q30-acceptance PASS.
