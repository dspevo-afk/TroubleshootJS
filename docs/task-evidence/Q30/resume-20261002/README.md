# Q30 resume checkpoint — 2026-10-02

Q30 is **BLOCKED / NOT ACCEPTED** and remains disabled for normal players. This
is a local checkpoint requested at the safe boundary after the full native
attempt finished. No merge, rebase, push, publication or email was performed.
U06/U07/Q60 remain unstarted. The 90,000 ms cumulative, 640 shared work and
5,000 ms active-operation limits are unchanged.

The examined candidate was `bbbfcd684c4c226c66e5cc2ea673b3cf35c3ac68` on
`codex/q30-multirail-qualification`, plus the seven-line native fixture change
in `QuickPlayPhysicalMatrixContractTest.java`. That fixture recognizes only the
exact RB30 typed P05-only normal-policy rejection. It retains the twenty valid
boards per family, physical diversity, exact replay and all budget assertions.
No production source, catalog flag, save format or replay format changed.

## Results and limits

| Gate | Actual result | Evidence / limit |
| --- | --- | --- |
| Checkpoint inputs and cold77 reuse | PASS under audited boundary | All 1,341 r9 declared source inputs matched before the native-only fixture correction; thirteen added paths are the checkpointed qualification package. Production inputs remain byte-identical. Independent audit checked all 77 readers/specs/logs and source/archive hashes. Cold77 was not rerun. |
| First final native attempt | FAIL, exit 2, 354.104 s | Stopped at the physical matrix's unhandled typed P05 normal-policy rejection. Original failure is preserved. |
| Second final native attempt | FAIL, exit 2, 1,688.526 s | Twenty preceding suites PASS. Physical census: eleven families, 220 boards and 220 exact replays. Service: 105 attempted, 100 PASS, five seed-10 route failures. Runner cleanup PASS; separate cleanup elapsed time was not recorded. |
| Remaining native work | NOT RUN | The maintained runner defines 82 suites, correcting the older checkpoint's 78 count. It stopped at suite 21; 61 subsequent suites, sensitivity105 and structural51+26 remain without fresh final evidence. |
| Strict reader unit tests | PASS | Twelve tests, bundled Python 3.12.14, 0.005 s. This proves reader behavior, not electrical qualification. |
| Source-attempt archive | SOURCE ARCHIVE ONLY PASS | Eleven candidates, exact pinned-base/delta reconstruction, overlay extraction and raw baseline binding pass. Exact linked profile payload preserved. Archive acceptance remains NOT ASSESSED. |
| Final enabled JDK8/GWT build and compiled31 | NOT RUN | Retained isolated r9 build/canaries are audited historical evidence, not the enabled shipping build or final matrix. |
| Ordinary menu33 and exact replay3 | NOT RUN | Catalog enable patch remains unapplied. |
| Visible 20/30/40 diagnosis, repair and retest | BLOCKED / NOT RUN | Browser and Computer Use skills were read; their required node_repl JS tool and graphical control tools are not exposed in this session. No supported visible input or screenshots were produced. |
| Parent acceptance review | NOT RUN | Leaf audit checked checkpoint inputs/cold receipts and topology. It is not the parent's final integrated acceptance review. |

The reused cold77 measured p50 56,766 ms, p95 78,103 ms, maximum 82,325 ms,
maximum 536 work and 1,451 ms active operation. These isolated ordinary-admission
results do not establish warm timings, every raw-root topology's service
coverage, normal menu behavior or visible repair completion.

## Seed-10 blocker

All five failing service cases stop in `Rb30Generator.route` before the service
hypothesis loop: `Q30 physical layout rejected seed 10`. The frozen original is
36 packages, two channels, `RB30_CH2_SEPARATE_DIRECT_A_BJT_B_NMOS`, layout seed
`-5437028946546985473`, routing seed `6161596101315970262`. This is a selected
topology representative in the predeclared service/sensitivity and D01 cohort.

Cold root 10 passed in 47,555 ms / 434 work by rejecting candidate 0 (seed 10),
rejecting candidate 1, then publishing candidate 2 (`4354685564936845364`): a
24-package one-channel shared/hysteretic BJT board. Candidate 0's retained
failure is `P07_FULLER_TWO_LAYER:A_REF:BRANCH_SEARCH_LIMIT` after fourteen
deterministic layout attempts. That ordinary retry is valid admission evidence;
it does not prove service or sensitivity for the original two-channel plan.
No seed substitution, skipped case or weakened assertion was made.

Next action: diagnose the existing generator/physical routing owners for that
exact frozen plan under unchanged limits. A production fix changes the consumed
input boundary and requires affected native/build/compiled/cold requalification.
Any different acceptance cohort or oracle would need an explicit parent decision;
the current evidence must remain FAIL. Keep Q30 disabled until all acceptance.

## Archive repair and retained artifacts

The original helper assumed HEAD equals the prepared base. The tested helper now
verifies that the frozen base is an ancestor of the observed checkpoint and still
archives the exact frozen object. The reverse baseline check failed for raw mixed
CRLF/LF files. The replacement fallback hashes each of the four raw originals
against the frozen snapshot, applies the pinned baseline forward in task scratch,
and requires exact effective source sizes and hashes. It preserves the reverse
failure evidence; global Git settings and baseline patch bytes were not changed.

`archive-q30-sources.ps1` is the exact v4 helper that passed the smoke and full11
runs. Earlier runtime/parse failures and task scratch remain in OS temp. The
compact manifest and transport receipt are checked in; the full 52,793,342-byte
archive remains in the resume temp directory with SHA-256
`7a9558f8ffbf2737561e661c3aaceaea46619708f9dccb319393260a5abfd8a9`.
Manifest SHA-256:
`fdd2e7134347302e84e68614be01f0094efc7a0ec0e78b203a33c8ced2e38f91`.

Artifact roots (relative to the user's `%LOCALAPPDATA%/Temp`):

- `q30-finish-0fa2512465`: original frozen exports, JDK, cold receipts, original and
  resumed native logs/results, all failed archive attempts, final full11 archive
  directory, compiled plan, catalog patch and existing resume helpers.
- `q30-resume-20261001-c798b16a`: clean disabled shipping export (`app`, not built),
  input/topology receipts and `source-attempts-final11.tar.gz` with its transport
  receipt. No isolated overlay or enable patch was applied to this export.
- `q30-source-archive-c2124e4a2fad4d4ba626486778e4c699` and
  `q30-source-archive-ae02ad77e56b4e40ad0864687a03b14f`: retained task-owned archive
  scratch. No evidence deletion is authorized.

`native-results.json` records exact seeds, phases, elapsed time, suite coverage,
failures and log hashes. Its three compressed UTF-8 logs redact only personal
absolute paths; original files remain unchanged. `seed10-cohort-blocker.json`
binds the cold report and retry manifests. `input-audit.json`,
`repository-topology.json`, `archive-repair-results.json`, `archive-manifest.json`,
`source-archive-transport.json` and `reader-tests.json` preserve the other checks.

## Git and worktree map

Read-only audit verified 32 registered worktrees, sixteen local branches and
eighteen live origin heads with `git ls-remote --symref`; no fetch or ref changes.
The Q30 checkout and `Desktop/TroubleshootJS` share the same common Git directory.
Origin is `https://github.com/dspevo-afk/TroubleshootJS.git`.

At audit time master was `9dc06141190da3a44ebe12015a4f5656f0f40ef5`; desktop
`codex/task43p-final-recovery` was `e0c368855a3891acd4673e94ce9afa732390e2bf`
(62 commits after master, one ahead of its live origin branch). Q30 checkpoint
`bbbfcd6` was a direct descendant 42 commits after desktop, 104 after master,
and 37 ahead of live origin Q30 `745d537de5b18bd7ec2ae769fc00d3696e5af6bd`.
The local checkpoint commit containing this packet adds one more Q30 commit.

Desktop has seven unrelated tracked solver/verifier edits plus untracked data.
Other worktrees contain duplicate or distinct uncommitted amendments and an
untracked decision-service draft. They were read, compared where relevant and
preserved. Seventeen listed prunable targets are absent and one is inaccessible;
none was pruned. Dirty state does not prove process ownership.

Do not combine all branch tips mechanically: several branches are already
ancestors and some dirty sibling copies are byte-identical. After acceptance and
an explicit integration target, Q30 could fast-forward the unchanged desktop
branch, but its unrelated dirty changes must first be reviewed separately by the
parent. No integration is authorized or performed in this checkpoint.

## Restart

1. Read the current report, this packet and parent review. Verify branch, HEAD,
   preserved caches, seed-10 evidence and input boundary before edits.
2. Fix the exact routing/cohort blocker through existing owners. Use focused real
   native canaries before another expensive matrix. Retained `run_final_native.ps1`
   has a no-overwrite output guard; choose a new task-owned output tag or invoke
   `scripts/verify-current-contracts.ps1` with the frozen21/first51 parameters from
   `native-results.json`. Run the appended26 cohort separately. One expensive
   build/test matrix at a time.
3. Finish all 82 native suites and final source JDK8/GWT build through
   `scripts/build.ps1`, then the predeclared 31 compiled gates (including D01's
   21 roots). Preserve D01 root35's expected P05 construction rejection.
4. Resolve supported visible control access. Complete actual production menu33,
   exact replay3, and 20/30/40 unrepaired/repair/retest input evidence before final
   acceptance and catalog enablement. Do not certify visible QA with injected
   controller calls or headless verifier receipts.
5. Parent owns final acceptance and the next unstarted roadmap phase. No push,
   email, merge, later milestone or evidence deletion is authorized here.

All task wrappers have finished. Native cleanup passes and no task-owned
test/build/browser/server process remains running. The four pre-existing Python
cache directories remain untracked. Architecture ownership is unchanged.
