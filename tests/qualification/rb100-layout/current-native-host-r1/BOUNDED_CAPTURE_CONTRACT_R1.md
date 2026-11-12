# Opt-in byte capture through the existing process owner

`Invoke-VerifierBoundedProcess` keeps its first three positional parameters.
Supplying either `MaxStdoutBytes`, `MaxStderrBytes` or `CancellationToken` selects
the new mode. Both byte limits must then be explicit positive integral scalars
at most Int32.MaxValue; the token must be exactly a managed CancellationToken.
Pre-cancellation and invalid options reject before log creation or launch.
Omitting all three options retains the previous path. No stream budget defaults
or native consumer policy are inferred from JSON-body or lab output limits.

Each pipe has one 4096-byte buffer and one outstanding asynchronous read. An
operation read requests at most min(4096, remaining+1). The extra byte proves
overflow; retained bytes never exceed the selected cap. Overflow is a failed
operation, never successful truncation. The existing retained process/start
identity authorizes direct-child termination. Cleanup then drains both pipes
while discarding under the existing five-second bound and closes readers.
Task terminality, closure and unproven cleanup are visible in capture facts.
Failure-prefix writes are attempted even if stop, drain or disposal failed.

Success retains the existing result fields and adds `CaptureFacts`. Failures
carry that object in `Exception.Data['VerifierCapture']`, plus the retained
evidence path. Per-stream ByteCount and SHA describe the retained prefix;
ObservedBytes counts operation reads; DiscardedDuringCleanup is separate.
OperationMilliseconds and CleanupMilliseconds are measured separately.
FailureLogsRetained is true only after both prefix writes succeed. Facts are
observations, not native admission or a complete process/tree provenance receipt.

Cancellation and deadline checks are cooperative, including a final settlement
check before success. Synchronous log/hash work is checked after returning; it
is not preempted mid-call. Byte limits do not bound total process RSS or cleanup
I/O. Stream text uses the existing reader's initial encoding; ASCII text and raw
UTF-8 byte boundaries are tested. General decoded UTF-8/BOM behavior is outside
this contract. Only the default legacy path retains its previous decoder path.

The CODE-only maintained canary uses explicit module, fixture, shared witness
and selected executable hashes, fresh OS-temp evidence, Windows PowerShell5.1
and Python `-I -B`. Two controlled hooks test failed-cleanup evidence and a
settlement deadline after real capture; both restore the original functions.
They are declared fault/latency controls, not proof of an actual OS close fault.

No new launcher, descendant authority, JAR/compiler/source coordinator, native
role-cap policy or normal activation is added. Browser-specific job containment
is a separate existing owner; adapting it remains next. Normal Study holds,
genuine gate, electrical truth, seeds, epochs and existing budgets stay fixed.
