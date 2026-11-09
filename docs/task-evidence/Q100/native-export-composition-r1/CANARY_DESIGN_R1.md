# ExportCompositionCanary source design

Scope: two synthetic, source-only cases against the admitted current V2 setup,
constructor, metadata, detached R7 plan, receipt ticket, and `exportV2`. There
is no producer, OriginalArm, ArmResult behavior, graph, Session, or route. The
four borrowed child maps are distinct and empty. The original owner credit is a
primitive one-group fixture. The actual native clock and all group measurements
remain live observations.

Each case creates a fresh 4096-byte LF-terminated receipt beneath an exclusive
empty canonical task root. It records the path count, hash blocks, receipt hash,
and R5 file identities before the producer clock. Setup, constructor with four
prepaid attribution reads, metadata, and real detached R7 capture share the same
Meter, start, body, snapshot, and ticket. The plan is checked as unfinalized and
passed directly to `exportV2`; the export owns its one-shot finalization and
ticket verification.

SMOKE uses a null hook. SUITE uses the existing after-result-write hook to alter
the receipt's first byte from x to y, preserving its size, modification time, and
file identity. It expects `FINAL_SHA_MISMATCH` after actual result readback,
with an open partial export group, no marker, and no returned metrics. That
Meter is discarded without `finish`. The actual stdout marker is bounded to
656 bytes, captured in a test PrintStream, and System.out is restored in a
`finally` block before the case summary.

Operation timing ends before cleanup. Cleanup reads the actual result within the
32 KiB fixture limit using a no-follow stream that reads at most one rejection
byte beyond the limit, converts actual result and marker bytes to lowercase hex,
captures native Meter and returned R5 metrics, checks the ledger equations and
clock bounds, then verifies and removes only the known result, receipt, and case
directory leaves. It checks the task root is empty. A failed cleanup forces
FAIL. The Python driver supplies the independent literal body/marker oracle and
all 34 work equations; this Java canary does not serialize an expected body via
the production codec.

The export's `finally` restores stdout before the operation boundary, so
`operationNanos` includes that restoration. `cleanupNanos` measures the later
read-only metric capture, result stream closure, identity checks, and leaf removal.

No Java compilation, native run, fixture generation, protected input, or
publication is performed in this worker assignment. Root owns review, freeze,
execution, independent oracle gates, and publication. The pre-code synthetic
reference vector is a Python calibration artifact, never a substitute for native
clock measurements.
