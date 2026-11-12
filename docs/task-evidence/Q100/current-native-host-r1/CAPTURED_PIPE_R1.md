# Captured native pipe leases

N60 adds creator-only `LaunchCaptured` to the existing native launch core. The
same original process lease gains separate stdout and stderr readers. Legacy
PID-returning and retained no-pipe launches keep their behavior. The shared
bounded capture helper accepts the exact native lease, starts both managed read
tasks before polling and retains the existing per-stream cap-plus-one, cancellation,
deadline and EOF logic. The native coordinator adapter is a later boundary.

The core creates anonymous pipes and read-only NUL stdin before creation. Parent
read ends are noninheritable. `HANDLE_LIST` contains only the three child standard
handles alongside `JOB_LIST`; `STARTF_USESTDHANDLES` and inheritance are enabled
only for capture. Redundant parent child-end copies close after successful
creation and before resume. The query-only child job lifetime pin remains a
separate deliberate duplicate. The original creator job retains stop authority.

Partial setup owns only successful API results. In particular, failed
[CreatePipe outputs are indeterminate](https://learn.microsoft.com/en-us/windows/win32/api/namedpipeapi/nf-namedpipeapi-createpipe)
and are never adopted for cleanup. Parent read inheritance is cleared with
[SetHandleInformation](https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-sethandleinformation).
The child handle list follows the
[process attribute contract](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute).
Suspended failure rollback keeps the original process handle. Readers and process
ownership transfer after successful resume. Captured lease disposal requires both
original process exit and closed read handles, then closes its wrappers and handle.
The caller must separately prove job emptiness and terminal reader tasks.

Anonymous pipes have synchronous native handles and
[do not support native overlapped operations](https://learn.microsoft.com/en-us/windows/win32/ipc/anonymous-pipe-operations).
The selected .NET `FileStream` uses managed async reads over those handles. Early
real-host JDK finite output and simultaneous65,536-byte CODE streams prove that
chosen path; no native-overlapped claim follows. OS buffering can backpressure an
early writer while both reads start. Its advisory buffer size is distinct from
the enforced retained-byte caps. No second capture loop or replacement launcher
is introduced.

This qualification is for a serial, dedicated selected host. The handle list
restricts this child; the temporary inheritable writer/NUL handles could be
inherited by a different concurrent process creation in the same host. Such
concurrency is NOT QUALIFIED. A future coordinator must serialize or isolate all
relevant creation owners; a lock in this native class alone cannot protect
unrelated `Process.Start` calls. No global policy or private framework lock is used.

The [proof](CAPTURED_PIPE_PROOF_R1.json) and
[manifest](CAPTURED_PIPE_SOURCE_MANIFEST_R1.json) record selected-host interactions,
independent actor identities, exact bytes, operation/cleanup clocks, partial logs
and limits. The fixture emits concurrent literal O/E streams and can keep a writer
descendant alive after its parent exits. Existing fixture modes and their strict
CLI lengths remain. The selected JDK image is pinned; full compiler/framework/
environment/tool closure is NOT ATTESTED. Actor image and creation identity are
independently observed; JDK native-lease metadata is source-coupled.

Earlier failures remain failures. Initial maintained Smoke assumed exactly one
job member; it failed, and its cleanup also lacked retained read-handle proof.
The diagnostic revision preserved a root plus an unidentified extra member and
proved cleanup. That extra image is not retroactively recovered. A separate
diagnostic initially lost a naturally exited JDK PID during a fresh PID lookup;
original lease authority remained valid. The corrected live-only diagnostic does
not reclassify that failure. Subsequent tests record and vet allowed console-host
identities rather than silently ignoring extra PIDs. Root also restored the
fixture's old four/five-argument guards after a temporary six/seven-argument leak;
no final passing evidence is reused across that fixture change.

The first expanded Suite also failed: the child argument string included an
extra executable token, and post-disposal lease queries masked the primary
failure. After correcting argument ownership and retaining earlier facts, a
second Suite observed a valid child and pending readers with no captured bytes.
The new fixture parent redirects only stdin and immediately closes its writer;
this selects the framework's explicit standard-handle setup while leaving both
outputs unredirected. The child still starts through ordinary `Process.Start`
and inherits its job. The
[framework reference source](https://github.com/microsoft/referencesource/blob/main/System/services/monitoring/system/diagnosticts/Process.cs)
supports this bounded correction; installed framework closure is not attested.

A third Suite proved the expected bytes and pending readers but failed cleanup:
`HasExited` and an empty job preceded the retained child's actual wait signal.
The helper had ignored that wait result. It now requires the original handle to
signal under the unchanged five-second bound, preserves the child observer and
records closure facts before disposal. All three Suite failures remain FAIL.
Final Smoke and Suite use the corrected source; earlier source-only review is
not a substitute for those actual gates. Root also corrected an unrun Oracle
version assertion to the selected, pinned Temurin 8u502-b07 release reference.

Only two module regions change: the existing native class and shared receive
helper. [Source policy](CAPTURED_PIPE_SOURCE_POLICY_R1.json) conserves every raw
byte outside them under the existing CRLF checkout policy. Three current module
pins migrate only; qualifier bodies and the old PowerShell60c9 pin remain unchanged,
and the actual normal qualifier/harness is NOT RUN.
[Pin migration](CAPTURED_PIPE_PIN_MIGRATION_R1.json). Root owns integrated review;
leaves author bounded fixture/contract files or review source only and run no
tests. [Review scope](CAPTURED_PIPE_REVIEW_R1.json).

Final-source gates all pass on the selected PS5.1/Python host. Times below are
whole outer-wrapper operation clocks; per-case operation and cleanup clocks are
separate in the proof. Outer cleanup is not measured separately. Negative cases
verify expected failed operations and cleanup; those operations are not PASS.

| Gate | Cases | Wrapper operation |
| --- | ---: | ---: |
| Native pipe Smoke | 1 | 1.896 s |
| Native pipe Suite | 10 | 5.636 s |
| Original lease | 3 | 2.090 s |
| Creator job | 7 | 1.914 s |
| Shared bounded capture CODE | 30 | 9.136 s |
| Legacy process | 16 | 3.761 s |
| JDK tool controls | 3 | 1.830 s |
| Near-exit limit probes | 32 | 4.122 s |
| Original direct stopper | 4 | 1.645 s |
| Controlled captured rollback | 1 | 1.361 s |

The ten native Suite cases cover exact and below-cap duplex bytes, exit7,
stdout/stderr first excess, timeout, cancellation, a writer descendant after
natural parent exit with an outside sentinel, authority/missing/closed guards,
and finite pinned JDK output. Every accepted case proves its required original
process/member exits, empty job and terminal closed readers. The declared metadata
buffer control changes only one constructor argument, observes native122 before
resume and proves rollback after pipe allocation; effective CLR buffer capacity
is not measured or assumed zero. It does not prove an unmodified OS fault.

Final query-only resource audit records 165 known identities and
24 original outer exits. It grants no termination authority and does not
repair historical missing-handle evidence. No new persistent resource is known;
private test sources, receipts, expected-failure prefixes and earlier failures
remain preserved. Existing unrelated worktrees and uncertain old resources stay
untouched. Final input hashes are rechecked, including reused passing baseline
gates consumed by the unchanged final module and fixture.

This is the neutral pipe/reader prerequisite. Explicit CODE test caps do not
define native role policy. Contract preflight does not certify a production
native coordinator's preflight. Full tool/Source/JAR/return authority, creator-death
protection and genuine composition remain unqualified. No normal qualifier,
Study, OriginalArm, graph, Session, receipt/export or genuine operation runs.
Both Study holds stay exact throw-only; original-to-NULL/OBSERVE UNSPENT_GATED.
No prior/oldD/held-out/protected DATA or generated DATA factory is read. Full30
normal MEDIUM EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected unread
UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD. CircuitJS truth,
budgets, exact seeds, epochs and order stay fixed. Production Java/GWT/visible
Player NOT APPLICABLE; electrical/population NOT RENEWED. CI NOT RUN pending the
existing self-hosted runner prerequisite. Next is the bounded native coordinator
adapter and its explicit invocation/cleanup policy before trusted composition.

Implementation `752598fe62dbd470fbd3fbd278b081cb01f63a2b` (Capture verifier output through owned native
leases) was normally pushed to origin/codex/q100-physical-feasibility and the exact
remote SHA verified. Completion Gmail `1a1cdffdb4852ad2` was sent once; this
receipt sends no second email. The 16-path index audit passed with SHA
`91b58c692fc077df356337aee0fc13555d62473eb7815800f07c3f1620ef1a1f`.
Documentation-only receipt changes reuse all unchanged source-bound passing gates.
