# Current arms projection oracle R1

This package verifies only the synthetic component canary for
CurrentResultProjections.arms. It authenticates the frozen contract,
CurrentBodyOwnership.java, and the admitted parent meter excerpts before it
accepts a canary summary.

The expected cases are derived from the contract, ownership traversal and meter
admission source. The child DTO maps are fixed synthetic markers. This oracle
does not inspect receipt or generated data, and it does not accept a fresh run,
validate semantic cleanup, enable assembly/R7, or qualify a genuine envelope.

The canary read counter wraps `get`, `containsKey`, `containsValue`, `size`,
`isEmpty`, `entrySet`, `keySet`, and `values`; it does not instrument every
Java 8 default `Map` method or map writes. Its zero-read/input-unchanged fields
are therefore bounded checks, not an exhaustive arbitrary-map runtime monitor.
The admitted projection implementation was separately source-reviewed for
borrowed-child reads and writes.

The CLI is intended to run with python -I -B (default optimization level 0).
Successful component summaries report
PASS_INDEPENDENT_CURRENT_ARMS_PROJECTION; invalid input exits 2 with a
REJECTED status and an allowlisted code.

Before execution, Source review corrected the draft ledgers: finishGroup keeps
its last groupUnits and increments only completed groups. The separate group
method excerpt is authenticated alongside the allowance excerpt. The original
four reader drafts are retained privately; no draft was executed.

Path guards compare directory identity (device, inode, type and birth identity)
and reject reparse points on both observations. They retain complete per-file
path/handle stability, size and timestamps. Directory content timestamps are not
identity evidence: unrelated activity in an ancestor such as the OS temp directory
must not invalidate an otherwise stable file. This is not an atomic directory
handle chain or a claim of full host authentication.
