# Bounded process capture and cancellation

N57 adds an opt-in raw-byte capture mode to the existing process owner. Callers
must supply both per-stream caps; no defaults or native policy are invented.
The existing three-argument path remains unchanged. Capture detects the first
excess byte, retains bounded prefixes, handles cooperative cancellation and
uses the original direct-child stop/log/cleanup owners. Operation and cleanup
times are separate. Unproven cleanup remains a failed operation with evidence.
The [contract](../../../../tests/qualification/rb100-layout/current-native-host-r1/BOUNDED_CAPTURE_CONTRACT_R1.md)
defines fields, timing, byte domains and limits.

Final-source Windows gates PASS:

- Smoke2: exact caps and a live1MiB flood stopped at the first excess byte.
- Suite30 **CODE process cases**: per-stream below/exact/above, duplex65536 each,
  UTF-8 byte boundaries, exits0/7, real timeout/cancellation, exact prelaunch
  guards, legacy output and two controlled failure/latency cases.
- Fresh N56 legacy16 on the final owner and shared kernel witness.
- Selected JDK8 java/javac version and expected invalid-option error transport3.
  The javac exit2 operation is EXPECTED_ERROR_NOT_PASS; no compiler build passed
  by treating that exit as success. These use explicit test caps, not native policy.
- Focused32 rapid near-exit excess-output operations on the final owner.

The final five wrappers take2.188/9.160/3.593/1.818/4.675s respectively. These
are whole lab-wrapper times; API operation/cleanup durations are separate in
the [proof](CAPTURE_PROOF_R1.json). The [manifest](CAPTURE_SOURCE_MANIFEST_R1.json)
binds exact final inputs. All selected tools and Source bytes were rechecked.
JDK images are requested/pinned, not independently observed at their child
handles; the Python kernel witness independently checks image/PID/start/argv.

R4 once failed because the short one-over-cap case lacked its required partial
logs. The original test did not preserve the underlying cleanup exception, so
its precise trigger cannot be recovered. That FAIL remains a FAIL. Added
diagnostics, a later unchanged-owner suite and32 focused cases passed; those
results do not reclassify it. Source review exposed conditional prefix writes
that could lose evidence when cleanup failed. Prefix writes now run independently
of stop/drain/disposal success, with a separate retention fact. A real-child
test first drains with the actual helper, then injects an IOException: it requires
operation failure, CleanupProven=false, retained prefixes and exact hook restore.
The original OS/cleanup trigger was not reproduced; the controlled regression
proves the evidence path, not that historical trigger.

Root also added final cancellation/deadline checks after synchronous settlement.
A real-child case delays real fact collection1100ms under an unchanged1000ms
test budget, requires a settlement timeout and restores the helper. Both controls
are explicitly labeled failed operations; later ordinary calls still pass.

The module uses explicit `text eol=crlf` checkout policy. Original prefix/suffix
bytes outside the owned region are conserved exactly, including here-strings;
an earlier LF prototype was abandoned before final gates. Git canonical and
tested working-byte hashes are both recorded in [Source policy](CAPTURE_SOURCE_POLICY_R1.json).
Only three current consumer module pins/lengths migrate; qualifier functions
are AST-identical. The old60c9 PowerShell pin remains unchanged, and the actual
qualifier/harness is NOT RUN. [Pin migration](CAPTURE_PIN_MIGRATION_R1.json).

Root reviewed source, cases, streams, identities, failure evidence and diffs.
Leaves reviewed source only, including final evidence and settlement changes;
they ran no gates. [Actual review scope](CAPTURE_REVIEW_R1.json).
The fixture reuses the shared Win32 witness rather than duplicating it.
Original wrapper exits and query-only witness-identity observations establish
the recorded resource closure. Known failure logs and ordinary CODE fixtures
remain retained. The old R4 log root was not recorded, searched for or deleted.
Unrelated worktrees, uncertain old resources and earlier failures stay preserved.

This qualifies the finite opt-in direct-child boundary. Descendant containment,
complete tool/environment/Source/JAR/return authority, native stream policy and
trusted coordinator composition remain unqualified. Checks are cooperative;
they do not preempt synchronous I/O. UTF-8 tests qualify bytes, not general
decoded text/BOM semantics; success cleanup tests check rooted path absence
without independently checking the expected namespace. No normal-ready claim
follows from these tests. Earlier N55/N54/electrical evidence is not renewed.

The process Suite30 is unrelated to the excluded30 normal MEDIUM cases. No
prior/fixed20/oldD/heldout/protected DATA, generated DATA factory, normal Study,
OriginalArm/capture/graph/Session/receipt/export or genuine operation ran.
Both Study holds stay exact throw-only; original-to-NULL/OBSERVE UNSPENT_GATED.
Full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected unread UNCERTIFIED;
oldnormal20 NOT REREAD; playable100=0; beta HELD. Budgets, seeds, epochs, order
and CircuitJS truth stay fixed. Production Java/GWT and visible Player gates
are NOT APPLICABLE; population/electrical NOT RENEWED. CI NOT RUN/self-hosted
runner prerequisite remains. Next is pre-escape descendant containment through
the existing job owner, then role policy and trusted native composition.

Implementation `f383d5da924b27b16724e94b8f24f4429c51a230` normally pushed to
origin/codex/q100-physical-feasibility; exact remote SHA verified. Completion
Gmail `1a1cd2e76f4f624f` sent once; this receipt sends no second email.
