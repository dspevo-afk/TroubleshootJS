# Independent column-reference LU review

Read-only Luna/MAX review of the frozen CirSim/LuFactorizationChecks delta
against d079528: no actionable correctness blocker. The reviewer ran no tests
or builds; root owns native, compiled and timing evidence.

After scaling column k, its list contains exactly the distinct row objects with
nonzero lower entries below k. Later pivots exchange complete row references
only below that finalized column, whose entries are not subsequently written.
At column j, ascending k traversal hoists each coefficient once and preserves
each cell's subtraction order. Row order within one k can change after pivots;
those writes are independent under the audited unique-row caller contract.

The zero scaling skip follows the full finite input scan and finite multiplier
guard. Nonzero factors still compute, validate and store their scaled values;
underflowed zero is omitted only afterward. Numeric equality is tested;
signed-zero bit identity is not promised.

Workspace growth reserves n slots per column, whose maximum population is
n-j-1. Reset clears previous active state, and `finally` clears every populated
reference/count on success, singular return, nonfinite input and overflow.
Shrink/grow reuse leaves no higher-capacity references from prior calls.
Standalone calls have separate scratch; instance scratch is used synchronously.
No matrix references survive a completed call. Package-private callers retaining
individual row aliases are not a supported contract; current callers were audited.

The historical dense factor and solve oracles remain unchanged. Tests cover
dense and sparse patterns, 0–81 dimensions including prior word boundaries,
explicit later pivots, underflow, negative multiplier/negative zero, singular and
nonfinite inputs through both APIs, overflow after population, subsequent valid
reuse, all scratch slots/counts, and inversion. Failure-side partial matrices
can differ in independent row visitation order; both algorithms reject the
numerical failure, and caller restoration must still pass compiled A07.

This is a scoped correctness review, not a timing or Q30-acceptance PASS.
