# Unexpected pre-output process-call failure evidence

Frozen before final host execution. The five EXPECTED host negatives retain the
exact seven-key single-line frame in CANARY_REJECTION_R1; no extra diagnostics
are allowed for an expected rejection to qualify.

If the maintained bounded process API unexpectedly throws without a returned
receipt before task output exists, preserve the attempted-call evidence in ONE
additional stderr line P8_CANARY_HOST_CALL_FAILURE followed by a compact JSON
CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT record. It contains phase, attempted
executable/arguments/limit, measured wall, original exception text and available
Data, process=null and terminationProven=false. Emit this line plus the normal
failure frame, exit nonzero, and never encode diagnostic JSON into code/phase or
pretend it is a cleanupError. The parent's genuine raw stderr receipt preserves
both lines; its expected-negative one-frame matcher must reject this path.

When task output exists, retain the same object in host-failure.json. No unknown
PID is invented and no output directory is created before strict input validation
just to log the failure. Each diagnostic line remains<=64KiB and all capture
limits remain. If full diagnostics cannot fit, report that missing proof as a
distinct failure; do not convert it to an expected rejection or success. No
temporary evidence directory outside the existing owned output is required.
