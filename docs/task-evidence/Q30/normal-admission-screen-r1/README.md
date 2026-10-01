# Q30 isolated normal-admission screen

2026-10-01. **The frozen three-job production-path screen passed. Q30 remains
BLOCKED / NOT ACCEPTED and disabled for normal players.** No scale expansion,
optimization, U06/U07/Q60 work, push or email was performed.

## Exact candidate and result rows

All three rows use application baseline
`41f02f1b9f6c32cb8cf4ffbf9e8bd2bb3ea2476b` plus application harness SHA256
`1d286f0a0a6d816cb7c6a951328801d51d0b460bd1496a0ed0121211f9c3b074`.
The branch checkpoint started at `5dbde3bbf928a4af4ac79d63a923a97eb4a47f81`.
The [overlay package](../../../../tests/qualification/q30-normal-screen/README.md),
[final compiled inputs](validation/compile-inputs-final.json) and
[machine-readable rows](screen-results.json) separate baseline, delta and results.

| Order | Plan | Root seed | Actual packages | Cold generation ms | Shared work | Max active operation ms | Headroom to 90,000 ms | Attempt / admission / publication | Cleanup |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| 1 | 3 | 7 | 33 | 70,569 | 468 | 838 | 19,431 | candidate 0; physical PASS, MEDIUM PASS, diagnostic/repair/completion PASS; published | PASS |
| 2 | 3 | 64 | 35 | 66,464 | 446 | 1,095 | 23,536 | candidate 0; physical PASS, MEDIUM PASS, diagnostic/repair/completion PASS; published | PASS |
| 3 | 3 | 13 | 37 | 75,606 | 464 | 1,193 | 14,394 | candidate 0; physical PASS, MEDIUM PASS, diagnostic/repair/completion PASS; published | PASS |

Each root was executed once, in the frozen order, without a replacement or favorable
rerun. No screening candidate was rejected and no retry candidate ran. Actual plan
canonicals, package counts and all four prospective manifests matched the frozen
plan. The independent jobs retain deterministic candidate search and shared retry
budgets even though candidate zero passed in each job.

Raw application receipts and host results are in
[seed 7](cold-01-seed7/cases/001-cold-7.report.json),
[seed 64](cold-02-seed64/cases/001-cold-64.report.json), and
[seed 13](cold-03-seed13/cases/001-cold-13.report.json).
Each contains the six-stage work/time ledger, canonical request, exact signed-long
seeds, scope checks, actual inventory, computed difficulty, five completed hypothesis
proofs and 185 solver samples, cold-cache counters and owner cleanup. Each final
reader verdict is `APP_PASS` with separate `HOST_PASS`.

## Execution and equivalence boundary

The request is exactly
`PlayerLaunchRequest.random("RB30_CONTROL", exactDecimalSeed, "MEDIUM").generation()`:
non-private, search=true, quickPlay=false, explicit completion, four candidates under
one **90,000 ms / 640 shared work / 5,000 ms active-operation** allowance.
`NORMAL_MEDIUM_EXECUTION@2` retains `MEDIUM_BOARD_NORMAL@1`. No 75/80-second gate
was introduced. Existing solver, router, recipes, proof workload and budgets are
unchanged; none of the dirty scale implementation was imported.

Each job uses a fresh Edge process/profile and simulator, its ordinary instance-owned
proof cache with reuse enabled, one ordinary miss, zero hits and zero private-cache
activity. There is no prior proof warming. The normal asynchronous coordinator,
Timer continuation, watchdog and foreground clock run with `troubleshootDebug=false`.
Zero hidden-document events were observed. Standard normal installation's temporary
internal verifier flag is permitted after exact job binding; it is not a debug
clock or a private measurement request.

The unavoidable differences are explicit: the isolated module boots the harness,
the scoped authority exempts only disabled-family catalog eligibility, scalar
telemetry is collected, and the completed test owner is immediately retired before
restoring the predecessor. This is a normal coordinator admission screen, not a
visible player click/repair-flow certification. The shipping catalog and entry point
are unchanged. Authorization is checked before start, on current-job checks and
before publication; the ordinary physical, difficulty, electrical, diagnostic,
repair/completion and publication owners still execute.

## Timing and host accounting

| Seed | Start to callback ms | Browser startup ms | Navigation through terminal capture s | Harness owner cleanup ms | Host teardown s | Host start through cleanup ms |
|---|---:|---:|---:|---:|---:|---:|
| 7 | 70,570 | 265 | 71.110 | 2 | 1.297 | 74,836 |
| 64 | 66,465 | 297 | 66.984 | 2 | 1.141 | 70,448 |
| 13 | 75,607 | 265 | 76.125 | 1 | 0.828 | 79,262 |

Cold generation is the normal job's cumulative foreground clock, including normal
yields and publication. The callback wall measurements agree within 1 ms. Browser
startup, terminal serialization/capture and test-owner/host cleanup are separately
reported above. None was subtracted to turn a rejected job into a pass. The final
column is the host's timestamp span, not the application deadline.

Routing timers were 15,612 / 9,879 / 16,294 ms; proof timers were
50,384 / 51,659 / 54,323 ms. These are nested measurements and are **not added** to
stage or job time. Maximum coordinator advances were 839 / 1,095 / 1,194 ms;
they are wrapper measurements distinct from the active-operation guard. Aborted
canaries use null for released nested timer owners rather than falsely reporting
zero work.

Actual browser: Microsoft Edge **154.0.4258.37**, Windows desktop, visible foreground
window, fresh profile per case. GWT loaded permutation
`7247CC9D86D564651F0BC146A31D6453`, SHA256
`f5a85bc1577b79781abde4baed37fe8c35dcfb257c8a293b325fb2c3a8e89203`, in all three jobs.
Response bytes match the built file. All per-run source/web/runner before-and-after
audits pass. No simultaneous build or generation matrix ran during the screen;
ordinary desktop activity and light evidence handling were not eliminated. These
single samples are not a performance improvement comparison or a tail estimate.

The three terminal screenshots were visually inspected. They show the restored
landing page and screen outcome after test-owner retirement. They do not constitute
manual player-input evidence or a certification of other browser wrappers.

## Validation, failures and review

- **PASS:** focused native seven-suite run: plan 2,256; request 446;
  measurement-budget 25; normal-policy 325; A10 generation 190; visual/clock 150;
  initial isolated guards 15. Added boundary checks then passed 26 assertions.
  After the runtime scope correction, isolated guards **27**, normal-policy 325
  and A10 190 passed again. Other four suites exercise unchanged baseline owners;
  their earlier evidence is retained, not relabeled as a fresh final run.
- **PASS:** final-source JDK `1.8.0_502` / GWT 2.7.0 build through
  `scripts/build.ps1`, all five permutations. The corrected build compiled in
  85.832 s and linked in 1.527 s. Build logs and exact dependency hashes are retained.
- **PASS:** final compiled unauthorized normal Q30 launch rejected before mutation;
  cancellation and scope-loss canaries each reached physical admission and one
  HYPOTHESES unit, then restored the predecessor. Outcomes were respectively
  CANCELLED and STALE; test receipts are CANARY_PASS, not cold-screen PASS rows.
  Both used fresh profiles and standard scheduling. Their recorded job times were
  20,143 and 20,356 ms; these are interrupted lifecycle tests, not timing controls.
- **PASS:** six strict-reader tests including contract corruptions, missing proof,
  cleanup, warm/private-cache, deadline, plan hash and legitimate difficulty rejection;
  all three real receipts revalidated with the final reader. Synthetic tests are not
  measurements. Actual loaded-resource and artifact hashes also match.
- **PASS:** package reconstruction reproduces every final compiled source byte from
  the frozen export plus overlay. Text line endings are explicitly recorded.
- **PASS:** real Edge host positive canary; the wrong-state negative produced its
  expected **TIMEOUT**, with cleanup PASS. Host transport success is distinct from
  application admission. Initial missing Playwright was a retained infrastructure
  failure; Playwright 1.63.0 was installed only in task-owned temp dependencies.
- **Retained failures:** initial native fixture omitted its coordinator; it was fixed.
  The first compiled cancellation canary failed at HEALTHY, 66 work units, because
  the new scope incorrectly rejected the normal installer's internal verifier flag.
  Its outer snapshot assertion also failed. The authority and exact-owner restore
  fixes were rebuilt and tested before any screening job started. Initial package
  replay exposed mixed newline bytes; recorded line-ending replay fixed it. None of
  these failures is a successful admission result or a substituted cold-screen row.
- **PASS:** independent read-only source review after the fixes found no remaining
  blocker in scope, ordinary scheduling/cache use or exact-owner cleanup. Root
  reviewed the integrated overlay and evidence. No independent full matrix was run.
- **NOT RUN:** full acceptance matrix, broader scale/held-out population qualification,
  arbitrary-seed reliability, visible player interaction acceptance, or later milestones.

There were three frozen cold screening jobs and three partial pre-screen lifecycle
jobs (the failed early canary plus the two corrected lifecycle tests). Unauthorized
canaries started no job. No private qualification or full proof warming run was used.

## Preservation and closeout

Only the isolated harness package, this evidence packet and the authoritative report
checkpoint belong to this task. The 172 pre-existing Q30 changed/untracked files and
96 desktop files are preserved by hash; the 26 tracked Q30 scale edits stay unstaged.
Shipping architecture/catalog/roadmap status is unchanged. Personal absolute paths
in host logs are replaced by stable tokens; app receipt bytes are unchanged.
[Evidence hashes](evidence-files.json) retain original and stored identities;
large input manifests are deterministic gzip. Browser profiles, dependencies and
compiled binaries are excluded from the repository.

Resource and preservation closeout is recorded in `closeout.json`. No push or email
is authorized. The next unstarted work is broader Q30 scale/full acceptance, only
under new authorization. This screen does not enable Q30 or start U06, U07 or Q60.

**OS-temp cleanup BLOCKED:** automatic approval review rejected both the combined
verification/deletion command and the subsequent exact-literal PowerShell deletion,
reporting only "blocked by policy." The verified owned export `q30n-765b06f9dd`
therefore remains under OS temp. Its containment, 10,659 entries and absence of
reparse points were checked; no task-owned browser/JVM/Python/Node process remained.
No alternate deletion mechanism was used. This limitation is separate from the
passing application owner restoration and host process/listener cleanup.
