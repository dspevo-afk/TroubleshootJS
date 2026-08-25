# Post-Task-43 Gate A — Branch Consolidation and Evidence Preservation

## Candidate scope and status

This is the Gate A evidence record for the documentation-only candidate. The
current shared checkout remains on `codex/post43-roadmap-redesign`; it was not
switched unexpectedly. The requested candidate branch pointer was created at
the exact clean starting tip:

```text
codex/post43-mainline-consolidation -> acd2c265a91908cb2656d7306936c13c2ddd96ea
```

No commit, tag, branch deletion, worktree deletion, stash operation, push, or
remote deletion was performed. The working tree is the reviewable Gate A
candidate. If the primary architect approves and commits it, the intended
Gate A publication is exactly one new commit whose parent is
`acd2c265a91908cb2656d7306936c13c2ddd96ea`; the final commit SHA is therefore
not invented here.

## Exact live starting refs

The local starting checkout was clean:

| Ref or fact | Exact value |
| --- | --- |
| Active branch | `codex/post43-roadmap-redesign` |
| Active `HEAD` | `acd2c265a91908cb2656d7306936c13c2ddd96ea` |
| `origin/codex/post43-roadmap-redesign` | `acd2c265a91908cb2656d7306936c13c2ddd96ea` |
| Accepted Task 43 implementation | `8245c79990647f6c40f53bc1dd9330ec2ccd22b4` |
| `origin/codex/task43-recovery-integration` | `8245c79990647f6c40f53bc1dd9330ec2ccd22b4` |
| Local `master` | `c0eb342b29165b8218a4b97b16fb8554fee42aff` |
| `origin/master` | `c0eb342b29165b8218a4b97b16fb8554fee42aff` |
| `origin/HEAD` | symbolic `refs/heads/master`, target `c0eb342b29165b8218a4b97b16fb8554fee42aff` |
| `origin` fetch/push URL | `https://github.com/dspevo-afk/TroubleshootJS.git` |
| `upstream` URL | `https://github.com/sharpie7/circuitjs1.git` |

The three source branches were live at the requested exact tips before the
imports: `codex/state-lifecycle-integrity-audit` at `792a6da80e9eca3750c74803ba72bc3b0fbdc2f4`,
`codex/verification-integrity-audit` at `14d97a6652e5158fc80006b0da8d71b5a514c54c`,
and `codex/task43-recovery-assessment` at
`4d39242817effa7f01711ea74f845a0189d7affc`.

The live confirmation was performed on 2026-08-24:

- `git fetch --no-tags origin` exited `0` and did not change any branch or
  worktree content.
- `git ls-remote --heads origin` returned the public heads recorded in the
  branch table below, including `origin/master` at `c0eb342…` and
  `origin/codex/post43-roadmap-redesign` at `acd2c265…`.
- `git ls-remote --symref origin HEAD` returned
  `ref: refs/heads/master` followed by
  `c0eb342b29165b8218a4b97b16fb8554fee42aff HEAD`.

## Canonical target lineage and fast-forward proof

The canonical accepted implementation is Task 43 at
`8245c79990647f6c40f53bc1dd9330ec2ccd22b4`. The current roadmap-redesign tip
`acd2c265a91908cb2656d7306936c13c2ddd96ea` is the clean authoring tip from
which the Gate A candidate was made; it contains the roadmap redesign and the
opt-in `AGENTS.md` governance update above the accepted implementation. The
candidate adds no production source.

Before the Gate A change, the local fast-forward proof was:

```text
merge-base(master, acd2c265a91908cb2656d7306936c13c2ddd96ea)
  = c0eb342b29165b8218a4b97b16fb8554fee42aff
master is ancestor of acd2c265a91908cb2656d7306936c13c2ddd96ea
  = true (git merge-base --is-ancestor exit 0)
rev-list --count master..acd2c265a91908cb2656d7306936c13c2ddd96ea
  = 29
```

That is the pre-change 29-commit distance from the old `master` tip to the
current clean authoring tip. It must not be confused with the Gate A delta.
The final Gate A form is one new documentation/evidence commit on top of
`acd2c265…`; after that commit, `master..candidate` would be 30 commits and
the Gate A candidate itself would be one commit beyond its recorded parent.
No such final commit exists in this coder worktree, so no final candidate SHA,
master publication SHA, or push result is claimed.

The dedicated candidate branch pointer currently resolves to the parent tip,
not to an invented future commit. This preserves the requested branch identity
while leaving final publication and review to the primary architect.

## Branch classification

Classification is by actual local and `origin` refs observed at the live
confirmation. `CONTAINED` means the branch tip's substantive content is in the
canonical target lineage. `UNIQUE_DOCUMENTATION` means its unique branch delta
is one of the preserved reports. `INBOX_OR_PROMPT` means coordination input,
not accepted architecture. `ACTIVE_OR_UNKNOWN` is reserved for the current
candidate/source refs whose final publication or review disposition is still
active. There is no `UNIQUE_PRODUCTION` branch among the local/origin
TroubleshootJS refs in this inventory: no local/origin TroubleshootJS branch
has unique production required by Gate A, and no such branch content needs to
be imported. Separate `upstream/*` refs for `sharpie7/circuitjs1` are outside
the local/origin TroubleshootJS inventory and Gate A scope; this statement does
not deny upstream-unique production/assets, which are retained untouched.

### Local branches

| Local ref | Tip | Classification | Disposition |
| --- | --- | --- | --- |
| `master` | `c0eb342b29165b8218a4b97b16fb8554fee42aff` | `CONTAINED` | Retain as the canonical promotion target; fast-forward only after review/publication. |
| `codex/component-visual-audit` | `f5407b476955672ae7181971b440d2e38a5e6801` | `CONTAINED` | No registered worktree uses this branch. Its content is already contained in the target lineage; any later deletion remains gated by its archive tag, candidate/master publication, and owner rules. |
| `codex/integration-audits-ui` | `92df2401d865f81e9c68fb1e1b503a6b92fa8051` | `CONTAINED` | No registered worktree uses this branch. Its historical UI/audit lineage is contained; archive before any later deletion. |
| `codex/pcb-routing-scalability-audit` | `0681cc61d2fb92d06f8c1f0a571a4160eeded824` | `CONTAINED` | Its audit is already contained; active worktree remains. |
| `codex/post43-mainline-consolidation` | `acd2c265a91908cb2656d7306936c13c2ddd96ea` | `ACTIVE_OR_UNKNOWN` | No registered worktree uses this candidate ref; retain through review and final publication. |
| `codex/post43-roadmap-redesign` | `acd2c265a91908cb2656d7306936c13c2ddd96ea` | `ACTIVE_OR_UNKNOWN` | Current shared worktree/source branch; do not switch or delete. |
| `codex/procedural-generation-audit` | `1eebf1a9bd7dc2ab3aac5f4278d4ae8eba03f989` | `CONTAINED` | Its audit is already contained; active worktree remains. |
| `codex/state-lifecycle-integrity-audit` | `792a6da80e9eca3750c74803ba72bc3b0fbdc2f4` | `UNIQUE_DOCUMENTATION` | Exact report imported; retain until archive and worktree-retirement guards pass. |
| `codex/task43-recovery-assessment` | `4d39242817effa7f01711ea74f845a0189d7affc` | `UNIQUE_DOCUMENTATION` | Exact recovery assessment imported; retain until archive and worktree-retirement guards pass. |
| `codex/task43-recovery-checkpoint` | `239b52f0c1fd36eac5ccb65ad7dbe559474c1800` | `CONTAINED` | No registered worktree uses this branch. The historical checkpoint is contained; archive before any later deletion. |
| `codex/task43-recovery-integration` | `8245c79990647f6c40f53bc1dd9330ec2ccd22b4` | `CONTAINED` | No registered worktree uses this branch. It is the accepted Task 43 source; retain until master publication and archive guard pass. |
| `codex/troubleshooting-realism-audit` | `1875234c46b058b9400e86ad5716d7f536f12cf7` | `CONTAINED` | Its audit is already contained; active worktree remains. |
| `codex/ui-workbench-shell` | `7bbb9de591155da0db0c173cb26c5e621a622c6a` | `CONTAINED` | Its implementation is already contained; active worktree remains. |
| `codex/verification-integrity-audit` | `14d97a6652e5158fc80006b0da8d71b5a514c54c` | `UNIQUE_DOCUMENTATION` | Exact report imported; retain until archive and worktree-retirement guards pass. |

### Origin branches

| Remote-tracking ref | Tip | Classification | Disposition |
| --- | --- | --- | --- |
| `origin/codex-inbox` | `3a5351f8ade672a6c898253b60e00b5729d6e759` | `INBOX_OR_PROMPT` | Obsolete inbox branch; preserve as historical reference until owner-authorized remote cleanup. |
| `origin/codex/component-visual-audit` | `f5407b476955672ae7181971b440d2e38a5e6801` | `CONTAINED` | Duplicate remote of the contained local audit branch; no remote deletion in Gate A. |
| `origin/codex/pcb-routing-scalability-audit` | `0681cc61d2fb92d06f8c1f0a571a4160eeded824` | `CONTAINED` | Duplicate remote of the contained local audit branch; no remote deletion in Gate A. |
| `origin/codex/post43-agents-governance-inbox` | `5deec96bae467a0e9a15881480a1c8291ef689f8` | `INBOX_OR_PROMPT` | Superseded governance prompt/inbox branch; no remote deletion in Gate A. |
| `origin/codex/post43-roadmap-redesign` | `acd2c265a91908cb2656d7306936c13c2ddd96ea` | `ACTIVE_OR_UNKNOWN` | Live remote of the current authoring branch; retain through candidate review. |
| `origin/codex/procedural-generation-audit` | `1eebf1a9bd7dc2ab3aac5f4278d4ae8eba03f989` | `CONTAINED` | Duplicate remote of the contained local audit branch; no remote deletion in Gate A. |
| `origin/codex/prompt-dropbox` | `236c7e6f37a9af9c5011ed73b1fcf5fcbb730f4e` | `INBOX_OR_PROMPT` | Superseded audit-prompt branch; no remote deletion in Gate A. |
| `origin/codex/state-lifecycle-integrity-audit` | `792a6da80e9eca3750c74803ba72bc3b0fbdc2f4` | `UNIQUE_DOCUMENTATION` | Remote source of the exact preserved lifecycle audit; retain until owner-authorized archive/deletion. |
| `origin/codex/task43-recovery-assessment` | `4d39242817effa7f01711ea74f845a0189d7affc` | `UNIQUE_DOCUMENTATION` | Remote source of the exact preserved recovery assessment; retain until owner-authorized archive/deletion. |
| `origin/codex/task43-recovery-checkpoint` | `239b52f0c1fd36eac5ccb65ad7dbe559474c1800` | `CONTAINED` | Duplicate remote of a contained checkpoint; no remote deletion in Gate A. |
| `origin/codex/task43-recovery-integration` | `8245c79990647f6c40f53bc1dd9330ec2ccd22b4` | `CONTAINED` | Published accepted Task 43 source; retain until canonical master publication is verified. |
| `origin/codex/troubleshooting-realism-audit` | `1875234c46b058b9400e86ad5716d7f536f12cf7` | `CONTAINED` | Duplicate remote of the contained local audit branch; no remote deletion in Gate A. |
| `origin/codex/ui-workbench-shell` | `7bbb9de591155da0db0c173cb26c5e621a622c6a` | `CONTAINED` | Duplicate remote of the contained local audit branch; no remote deletion in Gate A. |
| `origin/codex/verification-integrity-audit` | `14d97a6652e5158fc80006b0da8d71b5a514c54c` | `UNIQUE_DOCUMENTATION` | Remote source of the exact preserved verification audit; retain until owner-authorized archive/deletion. |
| `origin/master` | `c0eb342b29165b8218a4b97b16fb8554fee42aff` | `CONTAINED` | Old canonical pointer; fast-forward target only after review and publication. |

`origin/HEAD` is a symbolic remote alias, not an additional branch. The
`upstream/*` refs for `sharpie7/circuitjs1` are separate upstream/out-of-scope
material, not local/origin TroubleshootJS branches; they are not part of this
origin consolidation and are retained untouched, including any upstream-unique
production/assets.

## Exact preservation and content disposition

### Imported unchanged from unique documentation branches

| Candidate path | Source ref and commit | Source blob / verified candidate blob |
| --- | --- | --- |
| `docs/research/STATE_LIFECYCLE_INTEGRITY_AUDIT.md` | `codex/state-lifecycle-integrity-audit` at `792a6da80e9eca3750c74803ba72bc3b0fbdc2f4` | `989a9bd968825e6506c01280f2e6d9e698d0f864` |
| `docs/research/VERIFICATION_INTEGRITY_FALSE_PASS_AUDIT.md` | `codex/verification-integrity-audit` at `14d97a6652e5158fc80006b0da8d71b5a514c54c` | `1cbf7e4c277c254c549861c406366eee2201ec6c` |
| `docs/task-evidence/task-43/recovery-assessment.md` | `codex/task43-recovery-assessment` at `4d39242817effa7f01711ea74f845a0189d7affc` | `b4f6a3974a46a17868a86c66d9fa1e8ba452075d` |

The three files were restored with Git-native source-file restores. No report
text was rewritten. The normalized candidate hashes equal the source blob
hashes exactly.

### Already-contained and deliberately untouched

The following were already in the target lineage and remain unchanged:

- `docs/research/COMPONENT_VISUAL_REALISM_AUDIT.md`
- `docs/research/PCB_ROUTING_SCALABILITY_AUDIT.md`
- `docs/research/PROCEDURAL_GENERATION_SCALABILITY_AUDIT.md`
- `docs/research/TROUBLESHOOTING_REALISM_SOLVABILITY_AUDIT.md`
- `docs/task-evidence/task-43/recovery-history.md`
- Existing Task 43 image and evidence files.

The new authored evidence files are
`docs/research/AUDIT_STATUS.md` and this file. The only existing authored
documents updated by the candidate are `docs/ROADMAP.md` and
`docs/CODEX_TASK_REPORT.md`. No screenshots or unrelated cleanup were added.

### Superseded prompt-only and obsolete inbox content

The prompt-only `docs/prompts/` files from `origin/codex/prompt-dropbox`, the
unique `CODEX_INBOX.md` on `origin/codex-inbox`, and the unique
`codex_tasks/inbox/POST_TASK_43_AGENTS_GOVERNANCE.md` on
`origin/codex/post43-agents-governance-inbox` are not accepted architecture and
were not imported. Existing Task 43 inbox prompt files already in the target
lineage were left untouched as historical inputs. No unique production path
was found on any local/origin TroubleshootJS branch requiring preservation in
this candidate. Separate upstream refs may contain upstream-unique
production/assets; they are outside Gate A scope and are retained untouched.

## Planned archive tags

These are planned names only. No tag was created during candidate preparation.
Archive-tag creation is the post-review publication action and must occur
before any movement of `master` or any branch/worktree deletion. Each future
archive tag must resolve to the exact full object named below before its
corresponding branch can be considered for deletion:

| Planned tag name | Required target |
| --- | --- |
| `archive/20260824-master-c0eb342` | `c0eb342b29165b8218a4b97b16fb8554fee42aff` |
| `archive/20260824-codex-post43-roadmap-redesign-acd2c26` | `acd2c265a91908cb2656d7306936c13c2ddd96ea` |
| `archive/20260824-stash-43r8b-ca68570` | `ca6857049e92dfe7c5457d4069cb22470d48b935` |
| `archive/20260824-codex-component-visual-audit-f5407b4` | `f5407b476955672ae7181971b440d2e38a5e6801` |
| `archive/20260824-codex-integration-audits-ui-92df240` | `92df2401d865f81e9c68fb1e1b503a6b92fa8051` |
| `archive/20260824-codex-pcb-routing-scalability-audit-0681cc6` | `0681cc61d2fb92d06f8c1f0a571a4160eeded824` |
| `archive/20260824-codex-procedural-generation-audit-1eebf1a` | `1eebf1a9bd7dc2ab3aac5f4278d4ae8eba03f989` |
| `archive/20260824-codex-state-lifecycle-integrity-audit-792a6da` | `792a6da80e9eca3750c74803ba72bc3b0fbdc2f4` |
| `archive/20260824-codex-task43-recovery-assessment-4d39242` | `4d39242817effa7f01711ea74f845a0189d7affc` |
| `archive/20260824-codex-task43-recovery-checkpoint-239b52f` | `239b52f0c1fd36eac5ccb65ad7dbe559474c1800` |
| `archive/20260824-codex-task43-recovery-integration-8245c79` | `8245c79990647f6c40f53bc1dd9330ec2ccd22b4` |
| `archive/20260824-codex-troubleshooting-realism-audit-1875234` | `1875234c46b058b9400e86ad5716d7f536f12cf7` |
| `archive/20260824-codex-ui-workbench-shell-7bbb9de` | `7bbb9de591155da0db0c173cb26c5e621a622c6a` |
| `archive/20260824-codex-verification-integrity-audit-14d97a6` | `14d97a6652e5158fc80006b0da8d71b5a514c54c` |
| `archive/20260824-codex-inbox-3a5351f` | `3a5351f8ade672a6c898253b60e00b5729d6e759` |
| `archive/20260824-codex-post43-agents-governance-inbox-5deec96` | `5deec96bae467a0e9a15881480a1c8291ef689f8` |
| `archive/20260824-codex-prompt-dropbox-236c7e6` | `236c7e6f37a9af9c5011ed73b1fcf5fcbb730f4e` |

The active roadmap and candidate refs remain untagged during candidate
preparation: `codex/post43-roadmap-redesign` has the planned archive target
listed above, while `codex/post43-mainline-consolidation` and any current
remote-tracking ref where applicable remain retained until final publication.
The stash archive tag is for the complete stash merge commit; **KEEP / NO
APPLY / NO DROP** remains required.

## Safe deletion candidates and retained branches

No deletion occurred. Conditional future candidates are:

- Local contained, no-registered-worktree branches
  `codex/component-visual-audit`, `codex/integration-audits-ui`, and
  `codex/task43-recovery-checkpoint` after their planned archive tags resolve
  and the final candidate/master publication is verified. The component-audit
  branch remains subject to its owner rules as well.
- `codex/task43-recovery-integration` after the accepted baseline is archived
  and the canonical `master` fast-forward is verified; its content is already
  contained and its accepted SHA is explicitly preserved.
- Contained or prompt-only remote branches only after the corresponding
  archive tag, candidate/master publication, owner authorization, and a
  separate remote-deletion decision. Gate A performs no remote deletion.
- Active-worktree branches and unique-documentation branches only after their
  worktrees are clean, released, and removed by an authorized later action.

Retained now are `master`, the current roadmap branch, the candidate branch,
all unique documentation source branches until the exact imports are reviewed,
all branches with registered active worktrees, the accepted Task 43 remote
branch, all remote tracking refs, the stash, and all upstream/out-of-scope refs.
A branch marked `CONTAINED` is not permission to delete it while its worktree
is active, and no-worktree status alone does not waive its archive,
publication, or owner guards.

## Active worktrees

At the pre-edit live inventory inspection, all pre-existing active worktrees
were clean. The current authoring worktree is intentionally dirty now because
it contains this uncommitted Gate A candidate; the other seven listed
worktrees remain clean. Their paths and branch dispositions are:

| Worktree path | Branch / `HEAD` | Gate A disposition |
| --- | --- | --- |
| `C:\Users\david\Desktop\TroubleshootJS` | `codex/post43-roadmap-redesign` / `acd2c265…` | Current authoring worktree; retained and not switched. |
| `C:\Users\david\AppData\Local\Temp\TroubleshootJS-task43-recovery-assessment` | `codex/task43-recovery-assessment` / `4d392428…` | Clean unique-documentation source; retained until owner releases it. |
| `C:\Users\david\Desktop\pcb-routing-scalability-audit` | `codex/pcb-routing-scalability-audit` / `0681cc61…` | Clean contained audit worktree; retained until owner releases it. |
| `C:\Users\david\Desktop\state-lifecycle-integrity-audit` | `codex/state-lifecycle-integrity-audit` / `792a6da8…` | Clean unique-documentation source; retained until owner releases it. |
| `C:\Users\david\Desktop\TroubleshootJS\.worktrees\procedural-generation-audit` | `codex/procedural-generation-audit` / `1eebf1a9…` | Clean contained audit worktree; retained until owner releases it. |
| `C:\Users\david\Desktop\TroubleshootJS\.worktrees\troubleshooting-realism-audit` | `codex/troubleshooting-realism-audit` / `1875234c…` | Clean contained audit worktree; retained until owner releases it. |
| `C:\Users\david\Desktop\TroubleshootJS\.worktrees\ui-workbench-shell` | `codex/ui-workbench-shell` / `7bbb9de5…` | Clean contained implementation worktree; retained until owner releases it. |
| `C:\Users\david\Desktop\verification-integrity-audit` | `codex/verification-integrity-audit` / `14d97a66…` | Clean unique-documentation source; retained until owner releases it. |

## Stash disposition

The sole stash is retained exactly and was not applied, popped, or dropped:

```text
stash@{0} = ca6857049e92dfe7c5457d4069cb22470d48b935
parents   = f23a903145103bd2e7a2bf697f5c7c7db20c30e9
            ac114dbde85d5cc88693e7ba0ba18a8b6d055936
subject   = On codex/task43-recovery-integration: 43R-8B candidate preserved before 43R-3 corrective return
```

Its exact changed paths are:

- `scripts/verify-browser.ps1` — 77 additions/changes; an older candidate
  script, superseded by later accepted integration.
- `src/com/lushprojects/circuitjs1/client/CirSim.java` — 7 changes.
- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java` —
  114 changes.
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java` — 2
  changes.

The three production verifier/CirSim files in the stash match the current
accepted target content. The script candidate is historical and superseded.
The disposition is explicitly **KEEP / NO APPLY / NO DROP**; the stash is
evidence, not a candidate implementation source.

## Unreachable-object evidence

`git fsck --full --unreachable --no-reflogs` reported:

```text
11 unreachable commits
68 unreachable blobs
122 unreachable trees
```

The dangling commits are classified as follows:

- **WIP/index residue (10 commits):**
  `bb04b976a9e889350139f36c3672e1f19ccbf518` (index),
  `fa8c372528b48aad12e302b215cf7ce1b8c6dd31` (index),
  `d30fc56b3ede17084dc8aa96d7050c6c4ad3e45a` (WIP),
  `c9a000c9946de1e132b81f05ac0822cb1f2287a2` (WIP),
  `fa349ba65fb8d975a93a0842e4640627cea5c54d` (index),
  `953934705365edcbd52b141484a781cb8210803e` (WIP),
  `6dc8567f5b21316c21c6566f7a421694d174a92e` (index),
  `0262e4c50fde01cc3f4ed5c71629c855ebbc2769` (index),
  `de6700edf4fd37502f707e9b3b95488217694493` (WIP), and
  `e1f016f3ac322a027eeb79edfced212a60b3d36c` (WIP). These are stash/index
  merge residue from the historical audit integration work.
- **Duplicate historical residue (1 commit):**
  `0d17ce3aad2ce673dc0803928a168a015f674894`, “Harden Task 40/41 diagnostic
  serviceability contract,” has the same stable patch-id
  `24d4ee694019342ffaac23183ef5a0ec8cc79978` as reachable `7abba4a`; it is
  retained by the object database but is not canonical.

No Task 43 binary-only evidence was found in the unreachable residue: the
unreachable-blob/path scan found zero dangling binary blobs referenced by
`docs/task-evidence/task-43/`. The 11 commits, 68 blobs, and 122 trees are
retained only because they are present in the current object database; they
are not canonical or durably ref-anchored. No garbage collection or prune was
run. Future cleanup must keep the no-GC/prune guard until any desired WIP is
explicitly anchored or discarded.

## Live GitHub API evidence

The public GitHub REST endpoints were queried on 2026-08-24:

- [`GET /repos/dspevo-afk/TroubleshootJS`](https://api.github.com/repos/dspevo-afk/TroubleshootJS)
  returned HTTP `200`: public, non-archived fork, default branch `master`,
  `open_issues_count: 0`, and HTML URL
  `https://github.com/dspevo-afk/TroubleshootJS`.
- [`GET /repos/dspevo-afk/TroubleshootJS/actions/workflows`](https://api.github.com/repos/dspevo-afk/TroubleshootJS/actions/workflows)
  returned HTTP `200` with `total_count: 0`.
- [`GET /repos/dspevo-afk/TroubleshootJS/actions/runs?per_page=1`](https://api.github.com/repos/dspevo-afk/TroubleshootJS/actions/runs?per_page=1)
  returned HTTP `200` with `total_count: 0`.
- [`GET /repos/dspevo-afk/TroubleshootJS/branches/master/protection`](https://api.github.com/repos/dspevo-afk/TroubleshootJS/branches/master/protection)
  returned HTTP `401 Unauthorized`; protection was unreadable without owner
  credentials and is therefore deferred to Gate B/owner action.

## Deletion guards and rollback plan

No branch or worktree deletion is authorized by this candidate. Any later
deletion must stop immediately if any guard fails:

1. Create and verify the pre-Gate-A `master` archive tag
   `archive/20260824-master-c0eb342` at exactly
   `c0eb342b29165b8218a4b97b16fb8554fee42aff` before moving `master`.
2. Verify every planned archive tag resolves to the exact SHA in the tag table;
   stop on tag collision or a mismatched target.
3. Verify all unique report/recovery blobs and already-contained evidence before
   deleting any source branch; stop on unarchived or unexplained unique content.
4. Verify every active worktree is clean and no branch selected for deletion is
   checked out in any registered worktree; stop on a dirty active worktree or
   unknown worktree ownership.
5. Publish the candidate and fast-forward `master` before any remote branch
   deletion. Never delete a remote source before candidate/master publication
   is independently verified.
6. Require a strict fast-forward/non-force update only. Stop on divergence,
   collision, a non-fast-forward request, or an unexpected remote ref.
7. Keep the stash and unreachable residue; no apply/pop/drop, GC, or prune.

Archive tags provide rollback for any later local branch-reference cleanup:
recreate a deleted local ref at its exact archive tag, restore the preserved
files from the tag, and re-run the hash/path checks. If a future published
`master` needs correction, use a reviewed forward revert or corrective commit;
do not force-move the remote branch. Remote deletions are separately
recoverable only by recreating the branch from its verified archive tag and
must remain an owner-authorized action.

The final stop conditions are therefore explicit: no divergence, collision,
dirty active worktree, unarchived unique content, or unknown branch ownership
may be passed as Gate A completion evidence.
