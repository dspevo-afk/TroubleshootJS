# Root75 phase review and bounded harness correction

Q30 remains **NOT ACCEPTED and disabled**. The original full cold77 failure is
unchanged:31 PASS/1 TIMEOUT/45 NOT_RUN; root75 took90,221ms against90,000ms.
Original77 roots/308 candidates,640 work and5,000ms active/coordinator limits
remain fixed. U06/U07/Q60 stay unstarted. No push/publication/email/deletion.

This packet records a read-only review of five retained root75 reports, two
counter-path failures, passive canaries and the tested serial-runner fix. It
does not contain another Q30 application run. All1,354 frozen consumed inputs
remain byte-identical to source `a43f8165921f08c309636d2bf1a971905fdbc9382885c0708788d79744e1fb51`
at application base `d37c3b3b8581abdeaa9b88b7bfb2349cff958d31`.

The failed report's HEALTHY stage consumed29,189ms/89 units, versus
16,846-17,206ms/89 in the four prior matched diagnostic runs. Its HYPOTHESES
stage consumed59,495ms/318 units, versus54,608-56,065ms/390 completed units.
Active stage wall intervals total88,823ms of90,221ms;1,398ms falls outside those
recorded intervals. The delay therefore occurs within application operation
intervals. These are wall clocks, not CPU clocks: computation, scheduling,
preemption and GC remain unresolved. Hypothesis averages compare different
completed prefixes and do not prove an identical-prefix speed difference.

All five reports bind the same first candidate:root75,39 packages/two channels,
fault DRIVE_A_OPEN, request hash `1be24d990467d0ba4346480df8b286ea50e2949c6fe63988c701598ea1f88890`,
plan canonical `11a0add611d8142e9fdc5553cd6584e8d682469bae7baea6bc54588e78721d80`,
manifest `70bb3c32fc6d13416c8c7b47dd996a155440d8948b9d472118679d893e2571a8`,
layout6626451485285559634/routing4130765676055363405. Hidden events and initial
cache population are zero. No fallback candidate was attempted. The failure's
generic matrix-observer scope mentions successful publication, but this run
never published; that label does not supply missing publication proof.

`GenerationJob` records stage operation wall time; `ForegroundGenerationClock`
subtracts hidden periods but does not measure CPU. Active-unit maxima come
from the existing isolated qualification patch, rather than unmodified live
Java. Coordinator maxima cover synchronous advance calls and exclude timer
gaps. HEALTHY bundles construction/setup/validation; no separate family/fault
CPU measurements exist in these retained receipts. Dave's later16:45 CPU/RAM
observation describes that later desktop moment, not the14:42 failed run.

The parent window ended16:55 UTC without a Q30 launch. The passive PDH
preflight failed `0x800007d6` (negative denominator) before persisting samples;
its reconstructed metadata explicitly records the missing samples/clocks.
A changed three-collection raw probe reproduced one invalid formatted reading
over1.141s on16 logical processors. Core4 idle-counter deltas exceeded its
timestamp deltas; the following formatted zero is not accepted as proof of
zero CPU. The PDH failure remains FAIL, with no Windows settings repair.
Earlier short actual sampler canaries PASS0.406s/0.422s are limited to those
intervals. The expired-window helper refusal PASS0.153s launched no application;
its active-case draft failed static review and remains unexecuted.

A separate class8 native passive canary PASS_DIAGNOSTIC_ONLY:five samples over
4.000s,16 processors, raw deltas/bracketed aggregate cross-checks, actual invalid
information-class rejection and backward-arithmetic rejection. Outer owned
process operation4.078s/cleanup0.016s, job0/readers complete. In-process cleanup
recorded0.000s at its observed clock resolution; this does not prove zero cost.
Microsoft warns that this native API and structure may change/disappear. Array
indices lack a documented stable processor identity; percentage derivation is
an inference. It is outside qualification, does not relabel PDH as PASS and
does not establish Q30 process CPU or available desktop resources.
Contracts: [native query](https://learn.microsoft.com/en-us/windows/win32/api/winternl/nf-winternl-ntquerysysteminformation),
[aggregate CPU](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getsystemtimes),
[PDH error codes](https://learn.microsoft.com/en-us/windows/win32/perfctrs/pdh-error-codes).

The independent review found two harness gaps. The serial-runner correction
is integrated:one15s deadline covers process/job/accounting shutdown and reader
completion; only the reader owns log flush/close. A reader still alive or failed
at that deadline makes cleanup FAIL and later roots NOT_RUN. PASS39/39 selected
Windows checks (22 receipts/five normalizer/12 process checks), operation5.421s,
zero skips/errors/failures. Outer process operation5.562s/cleanup0.031s. Both a
real duplicated pipe writer holding EOF open and a blocked sink return within
the shortened1.5s test cleanup deadline, preserve application measurements,
and prevent seed76 launch. Test-owned handles are then released and readers
confirmed stopped. The original production15s bound and all app limits remain.
One preparation manifest anticipated38 tests; actual planned39/ran39/PASS39 is
the authoritative result, with the preparation mistake disclosed.

Independent resource review covers12 exact serial-test process instances:
11 absent and one PID reused by a later WmiPrvSE process. No uncertain process
was terminated. Negative receipts retain their deadline-time cleanup FAIL;
the passing final test assertions prove the subsequently released fixture
reader exits. [Resource receipt](serial-test-resource-review.json) gives scope.

The compiled-attribute guard is a **workspace draft**, not integrated. It
requires exactly one matching compiled script attached to terminal document/
HTML backend IDs and requires terminal verification for stateMatch. A root
read-only audit PASS0.171s/cleanup0.016s checked all118 final packet members and
31/31 matching attachments; the earlier31-case cleanup failure is separately
preserved as FAIL_CLEANUP. This gap alone does not invalidate those retained
attachment receipts. Real Edge attached/absent/detached canaries are NOT_RUN
pending a newly coordinated window. The draft remains unverified on that path.

The147-member [portable packet](root75-phase-and-harness-review.tar.gz) SHA256
`230f799ffdbfd609ebcfbf0895b6c930b7be44a86aeb3a972c0821c394ba1bfa` contains raw/portable hashes, tested sources,
passive raw samples, exact failure metadata and frozen unintegrated drafts.
[Manifest](manifest.json) records limitations. Raw OS-temp roots remain
authoritative; portable copies replace personal path prefixes only.

Restart from the task workspace's `epoch15-final/` pointers and review-fixes.
Before a new timed root75 run, coordinate a fresh7-minute window with parent,
review/freeze the corrected helper, screen observed load and retain per-core/
global-memory/owned-process CPU evidence without changing the app/host/caps.
Stop after any nonpass. No window has been implicitly extended. Full cold77,
enabled-build/menu and supported visible20/30/40 repair/retest still block Q30.
Browser/computer/node_repl visible-input tools remain unavailable. Parent owns
independent acceptance review. No current task-owned runner remains active.
