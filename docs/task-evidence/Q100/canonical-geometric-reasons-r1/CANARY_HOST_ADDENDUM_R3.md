# Continuous native and final closure timing

Frozen before focused-host execution. An independent source review identified
that copying each returned child's streams/receipt before cleanupStartOffset
would fall outside a timer that measures only the module call. This clarification
closes that gap; it supersedes R2's sum-only native charge. No cap changes.

Record nativePhaseStartOffsetMs from the host's monotonic entry immediately
before the FIRST native canary invocation. Record cleanupStartOffsetMs once,
immediately before post-native source/scratch closure. The continuous interval
between those offsets includes both FULL selected module calls through return,
every intervening stream/receipt copy/write, validation, second-child setup and
all other host work in that interval. Do not stop/start this charged phase around
per-child receipts. Separate per-child full module-call walls remain reported.

Native charge = cleanupStartOffsetMs - nativePhaseStartOffsetMs. It must be
nonnegative and >=sum of the two measured native module-call walls. Report the
difference as nativePhaseOverheadMs; never credit it as negative/free time.

Parent starts its monotonic wall immediately before Invoke-VerifierBoundedProcess
for the host, and stops only after it returns with stream drain/log-root deletion
and proven exit. Consumer requires all timing values finite/nonnegative,
0<=nativePhaseStartOffsetMs<=cleanupStartOffsetMs<=parentFullHostElapsedMs,
and consistent phase arithmetic before computing any bound. No max(0,...) clamp
may conceal inconsistent timing evidence.

Final conservative aggregate cleanup upper bound = native charge +
(parentFullHostElapsedMs-cleanupStartOffsetMs), under original120000ms. This
equals parentFullHostElapsedMs-nativePhaseStartOffsetMs, but retain separate
native and final-tail fields for audit. The final tail covers post-source checks,
owned scratch removal, final receipt/stream writes, exit and module cleanup.
Host startup/launch overhead can only overcount. Compile/preparation remains
outside the charged native/tail interval and gets no invented full-host120s cap.

Writing the parent receipt, copying its streams and independent final source/
output validation occur in a separately measured, bounded180s consumer-closure
phase. Report those timings and actual results explicitly; they are neither free
native work nor evidence that the host returned before its final I/O. Any missing
process/source/output/cleanup proof prevents acceptance.
