# Q30 qualification checkpoint — paused before final release gates

Task base: `8ecaa02b729944d98f82dde56d12d2f4425197ba`, branch
`codex/q30-multirail-qualification`. Q30 remains disabled until the required
acceptance gates pass. Publication is local only; no push or email.

The original Q30 roadmap card is authoritative: approximately 30 purposeful
parts, held-out coverage within 20–40 parts, interacting multi-rail behavior,
structural variation, ordinary diagnostics/service/retest, and unchanged
90,000 ms cumulative / 640 shared work / 5,000 ms active-operation limits.
Accepted family ownership and physical policy are reused, subject to regression
checks. No later milestone is included.

## Saved stopping point — 2026-10-01

The user requested a short handoff and local save after the r9 cold corpus finished.
This is a progress checkpoint, not milestone acceptance. Q30 stays disabled.

The full fresh r9 run passed **77/77** declared roots with strict application,
host-input and cleanup checks; sequence exit 0. Nearest-rank cold elapsed p50 is
56,766 ms, p95 78,103 ms, maximum 82,325 ms; maximum shared work 536 and active
operation 1,451 ms. The 20/30/40 pilots took 20,356 / 29,269 / 76,395 ms.
The run began 21:10:00 UTC and ended 22:17:16 UTC. Frozen bound-plan SHA-256:
`42348d7db094ca98fbdc15e5d19239532ba557b00752a92278fe9caf34182ae0`.
Prepared source identity:
`843411cdfe0a8bb3e8d4100f800ad1e07eab29688f6fb74ddc8de8d2cc9e4690`.

Focused native checks, the actual r9 JDK8/GWT five-permutation build and six
compiled lifecycle/solver/export canaries pass. The [saved receipt package](checkpoint-r9/REPORT.md)
keeps earlier failures and the full77 receipts distinct. The source-attempt archive
reconstruction is still unverified after its LF correction; retain all original
sources and raw profiles in task temp until that follow-up passes.

Final native78 plus structural/service/sensitivity matrices, enabled shipping
build, compiled31, ordinary player33/three replays, visible 20/30/40 service flows,
and final acceptance review remain **NOT RUN**. No normal-player warm population
was measured. The planned 21-row D01 cohort must label its own warm construction
and preparation timing separately; root 35 is a specific P05 construction
rejection, not a warm pass. No timing budget or acceptance oracle was relaxed.
No task-owned process is left running; source, helpers, raw data and the unapplied
catalog patch are retained in `%LOCALAPPDATA%/Temp/q30-finish-0fa2512465`.

## Frozen inputs and sequence

`acceptance-plan.json` adopts all 77 roots and exact plan-4 identities from the
existing [scale manifest](../scale-plan-4/manifest-proposal.json). No root was
selected or replaced using a timing, routing, or solver result. The first cold
jobs are representative seeds 10387 (20 packages), 10226 (30), and 10014 (40),
followed by the remaining roots in the manifest's order. The pre-existing
manifest happens to cover every count; this is not a new roadmap requirement.
Every attempted candidate, rejection and failure is retained. Necessary fixes
trigger affected reruns, with prior failed receipts preserved.

The service and finer-step matrix selects the first representative for each
of the 18 topology axes, plus the three scale boundaries: 21 roots, each with
the complete five-fault population. The structural corpus retains the original
51-root batch and appended 26-root batch, within the maintained runner's cap.

## Working acceptance checklist

- Review/integrate scale implementation and exact replay epoch; focused native
  structural, service, electrical, temporal and sensitivity checks.
- Build the isolated ordinary-admission candidate; run authorization/cancellation
  canaries and the frozen cold corpus using fresh ordinary caches, standard
  asynchronous scheduling and complete production proof.
- Run the maintained full native matrix, structural corpus, compiled electrical,
  diagnostic/cache/lifecycle and existing-family regression gates; report cold
  and warm distributions with their actual sample counts and limits.
- Enable the registered family only after readiness; build and validate that
  final candidate through ordinary menu/New Board/replay/player service/retest,
  preserving disabled-family negative coverage with a dedicated fixture.
- Independently review final code/evidence, resolve material findings, update
  roadmap/architecture/checkpoint, inspect and commit intended paths locally.
- Retire verified task-owned processes/scratch. The previously retained inactive
  `q30n-765b06f9dd` export is outside this task's cleanup scope.

Final results, failures, commands and source/build identities will be recorded
here as these gates execute. An unchecked item is not a pass.
