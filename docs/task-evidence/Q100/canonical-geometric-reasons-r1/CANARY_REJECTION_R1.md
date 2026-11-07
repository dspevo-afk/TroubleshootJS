# Focused canary rejection framing and failed-call evidence

Frozen before final host/reader source and execution. Substring matches inside
PowerShell tracebacks cannot prove a rejection code or stopped phase.

Host catch preserves its first failure, performs verified owned cleanup, then
writes exactly one stderr line: P8_CANARY_HOST_REJECT followed by one space and
a compact JSON object with exact keys code,phase,outputCreated,scratchCreated,
compilerInvoked,nativeInvoked,cleanupError. code/phase are nonempty strings;
four flags are actual exact booleans, initialized before validation and set only
at the corresponding actual boundary. Invoked means the maintained process API
was called, not fabricated process-start proof. cleanupError is null or the
actual cleanup failure string. Exit nonzero; do not append a default PowerShell
error display that may contain misleading source constants. Preserve an actual
post-output host-failure.json separately where output was created. No first-error
masking by final census/cleanup. Normal host success stdout stays its one marker.

Each of the five early host negatives requires one exact parsed frame, expected
code, nonempty phase, outputCreated/scratchCreated/compilerInvoked/nativeInvoked
all false and cleanupError null, plus proven actual selected-host exit, empty
stdout and unchanged/absent target as planned. Auditor validation may be invoked
before output creation; it is not a native canary or compiler invocation.

Reader errors write exactly P8_CANARY_READER_REJECT followed by one space and
the actual uppercase rejection code, one stderr LF line, nonzero exit/empty
stdout. The four parent controls require exact whole-frame code equality, not
Contains. This does not classify an unexpected traceback/startup error as the
expected rejection.

If the maintained bounded API throws rather than returning a process receipt,
the caller preserves a new CALL_FAILED_WITHOUT_RETURNED_PROCESS_RECEIPT record:
attempted executable/arguments/limit, measured wall, original exception text and
available exception Data, with terminationProven=false and process=null. Record
the phase and stop acceptance. Do not invent PID/start/exit/streams, claim proven
cleanup or discard the failure because the API supplied no raw receipt. Preserve
all returned genuine receipts unchanged. No automatic recovery or retry converts
that missing proof into PASS.

The parent consumer-closure timer begins immediately after the selected host
API returns and BEFORE parent receipt/stream writes. It includes those writes,
the independent reader invocation, its actual receipt and source/output checks;
assert full closure<=180s. Hash the genuine output inventory before and after
read-only reader controls. The controller also checks the whole outer<=1800s
after its final receipt write; any breach must exit nonzero even if a provisional
result file was already written. Such a file never constitutes accepted success.
