# Indexed LU prototype review

Independent read-only review covered the two pending Java files against
`7a30a3fdcfa1dc9092d546edcb01bfa9ed435a36`. It found no numerical correctness
blocker: ascending set-row traversal preserves each subtraction sequence;
post-scaling membership handles underflow; both row bits are refreshed for
every previous column after a pivot; active masks reset before matrix validation.
The historical dense Crout factor/solve oracle remains independent. Root reviewed
the integrated diff and ran the native and compiled gates recorded here.

A nonblocking observation is retained: rejecting a negative or overflowing size
leaves old workspace metadata inspectable by package-private test helpers. The
next valid call resets its active region before use; production callers derive
the size from the current allocated CircuitJS matrix. No stale numerical reuse
path was found. Signed-zero bit identity is outside the existing equivalence claim.

The performance result is separately **FAIL**, with a 9,863-ms cold regression
against a fresh accepted control. Review of all five emitted GWT permutations
finds the same indexed-loop structure: empty packed-word scans, bit decoding,
range-checked mask writes and mask clearing remain. These are observable costs,
not measured attribution of the regression. The Edge receipt records its
executable, not its literal user agent; Safari/WebKit selection is inferred from
the generated selector and is not independently certified by a network trace.
The isolated phase profile must decide the next implementation step.

The independent reviewers made no edits and ran no tests or builds. Correctness
review does not accept the slower candidate or qualify Q30.
