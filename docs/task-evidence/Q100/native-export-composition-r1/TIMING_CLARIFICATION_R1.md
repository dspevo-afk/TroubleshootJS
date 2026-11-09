# Pre-execution timing clarification

The base contract98637750 remains unchanged. Its cleanup timing sentence lists
restoration among cleanup responsibilities. In the implemented two-case harness,
System.out is restored unconditionally in the exercise's finally block, before
the operation boundary is recorded. Thus operationNanos includes stdout capture,
flush/restoration/close; cleanupNanos includes fact capture/bounded result read,
identity checks and owned leaf removal. No stdout restoration time is claimed in
cleanupNanos. Restoration failure still fails the operation and never disappears
into a successful case. stdoutRestored must be true before the case summary.

This clarification changes no clock bounds, fees, counters, schema, reference
bytes, expected failure, cleanup proof or runtime/source cap. It precedes the
actual canary compile/run. The already generated calibration/reference consumes
the unchanged base contract and is unaffected by this timing presentation detail.
Root will freeze this addendum with the final Source review before execution.

The result's harness read must be genuinely bounded, not size-checked followed
by readAllBytes. Root requested a Source-only correction of the released canary
before first compilation/execution: standard READ/NOFOLLOW stream, at most the
32768-byte fixture plus one excess-detection byte, stable identity and exact size
checks. Raw released Source must be retained. No production/export owner changes.
