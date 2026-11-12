# Original process-handle ownership

N59 adds creator-only `LaunchRetained` through the existing native job launcher's
single `CreateProcess` core. Legacy `Launch` keeps its PID result and immediate
process-handle close. The retained path reads PID, creation FILETIME and image
from the original handle before resume, then transfers that handle into a typed
lease after successful resume. It never reopens a PID to establish authority.
The child query-only job lifetime reference and default recovery remain intact.

The lease exposes bounded wait, signaled exit-code read and explicit disposal.
Active exit reads and live disposal are rejected; disposal retains ownership on
close failure and is idempotent after closure. Detached identity remains available
after close. A per-lease lock prevents closing during a wait. Finalization makes
a best-effort handle release, with a partial-constructor guard; it promises no
process death. The original creator job remains the exposed stop authority.
No pipes or bounded-process capture adapter are added to the lease.

The implementation follows the native contracts for
[creation times](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getprocesstimes),
[image paths](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-queryfullprocessimagenamew),
[process waits](https://learn.microsoft.com/en-us/windows/win32/api/synchapi/nf-synchapi-waitforsingleobject)
and [exit codes](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getexitcodeprocess).
Exit-code retrieval requires a signaled original handle first. Kernel creation
success now establishes rollback ownership before validating the returned PID.
A failed metadata read before resume terminates and waits on the original handle.

An actual final capture regression exposed a necessary integration correction:
the former bounded stopper rediscovered a PID after its direct child had emitted
five stderr bytes. It reported that the PID changed or exited before termination;
cleanup was unproven and readers were unclosed. The saved facts do not distinguish
the old stopper's exited-versus-replaced branch. That result remains FAIL.

The private direct-launch stopper now verifies creation identity on its original
`Process` handle, stops that object only if needed and accepts a stop exception
only when that same handle is already signaled. One original five-second budget
covers identity checks, stop and wait. Failure cleanup checks the original start
identity rather than requiring a current PID to disappear. Durable/adopted process
termination keeps its existing owner and checks. The fixture's independent identity
and absence assertions remain. The [.NET reference source](https://github.com/microsoft/referencesource/blob/main/System/services/monitoring/system/diagnosticts/Process.cs)
explains retained-handle reuse by `Process`; actual selected-host tests qualify
the measured path. This does not attest the complete installed framework.

The maintained lease contract compares original-handle metadata with independent
CODE actor witnesses, observes every vetted job member and the original lease,
checks active/bound/disposed guards, and requires an outside sentinel to survive.
The selected pinned JDK8 finite invocation supplies natural exit0 evidence through
the original lease; version text is not captured, and the JDK image witness is
not independent of the native owner. The actor image is independently observed.
Complete compiler, environment and tool closure remain unqualified.

A declared rollback control compiles the exact final native class with only the
image-buffer constructor capacity argument changed to zero. The CLR's effective
minimum buffer capacity is not measured or assumed to be zero. Real creation reaches
native error122 before resume; unchanged rollback leaves the job empty, no actor
state and no live uniquely named fixture. The forced operation is
EXPECTED_METADATA_FAILURE_NOT_PASS. This proves the controlled branch, not that
an unmodified OS metadata fault occurred. Production has no test hook. Allocation,
native close failure, finalizer execution and concurrency fault injection are
NOT RUN; those limits are not converted into success claims.

The [proof](PROCESS_LEASE_PROOF_R1.json) and
[manifest](PROCESS_LEASE_SOURCE_MANIFEST_R1.json) bind final sources, real host
receipts, controlled failures and separate operation/cleanup timings. Outer lab
wrapper times include their process wait; outer cleanup is unmeasured separately.
Final gates PASS: LeaseSuite3 in2.089s, LeaseSmoke1 in1.926s, default jobSuite7
in1.898s, capture30 CODE in9.244s, legacy16 in3.547s, JDK tool3 in1.843s,
near-exit32 in4.121s, direct-stop4 in1.780s and controlled rollback1 in1.359s.
Direct-stop cases cover correct and wrong creation identities after natural exit,
a live wrong-identity rejection followed by exact stop, and malformed wait bounds
that leave the live original untouched. The javac invalid-option exit2 remains
EXPECTED_ERROR_NOT_PASS. None of the CODE case counts denotes normal MEDIUM30.
Only three current module pin/length consumers migrate; qualifier functions and
the old PowerShell60c9 pin remain unchanged, and the normal harness is NOT RUN.
[Source policy](PROCESS_LEASE_SOURCE_POLICY_R1.json) conserves raw bytes outside
three declared regions under the existing CRLF checkout policy.
[Pin migration](PROCESS_LEASE_PIN_MIGRATION_R1.json).

Root reviews the integrated source, actual gates and failure evidence. Leaves
review source only and run no tests; the [review record](PROCESS_LEASE_REVIEW_R1.json)
states exact scope. Earlier Python JOB_LIST access-denied failures and the older
N57 unexplained failure remain preserved and unqualified. This new failure does
not recover the older trigger. Other worktrees, prior logs and uncertain resources
remain preserved. Cleanup authority never comes from a current PID lookup.

This is a prerequisite for captured native launch. Pipe setup, pre-resume reader
activation, role-specific caps, complete tool/Source/JAR/return authority and trusted
coordinator composition remain separate. Creator-death protection is unqualified.
No normal qualifier/harness/Study/OriginalArm/capture/graph/Session/receipt/export or
genuine operation runs. Both Study holds remain exact throw-only; original-to-NULL/
OBSERVE UNSPENT_GATED. No prior/oldD/held-out/protected DATA or generated DATA factory
is read. Full30 normal MEDIUM EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected
unread UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD. CircuitJS truth,
budgets, exact seeds, epochs and order stay fixed. Production Java/GWT and visible
Player are NOT APPLICABLE; electrical/population NOT RENEWED. CI NOT RUN pending
the existing self-hosted runner prerequisite. Next is captured-pipe preparation
through this same launcher and lease before trusted native composition.
