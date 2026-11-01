# Active preview recovery

Locally validated; publication receipt pending. The controlled [validation.json](validation.json)
records source and evidence hashes. Raw authority, manifests, claims, profiles,
command lines and logs remain in the task-owned OS-temp evidence directory.

The separate `Invoke-VerifierRetainedActivePreviewRecovery` entrypoint recovers one
exact active preview and bound browser after their common launch parent exits.
It locks both claims, freshly validates ownership, completes the preview first,
then uses the existing parent-exit Browser.close lane and final run cleanup.
Ordinary ownerless and already-terminal-preview recovery remain strict.

Final module SHA-256:
`8b68e983c6c35b05ce10bd8a30378293701d02f72fee14d1c66cbc214729f896`.
The private BDE predecessor differs only in 23 LF-to-CRLF normalizations; its
original evidence stays attached to that source. Final source uses normal Git
checkout line endings. No Java, solver/model, epoch, seed or player change.

## Validation

| Check | Result | Scope and limits |
| --- | --- | --- |
| New synthetic contract | PASS, 131 assertions | Exact graph rejection, mocked lock rollback, source/order checks; not host proof |
| Existing retained contract | PASS, 192 assertions | Ownerless and terminal-preview regressions |
| Registered selectors | PASS, 6,412 / 16,133 ms | Actual WinPS 5.1, exit 0 and termination proved; two exclusion controls reject with exit 2; marker helper 8 passes |
| Exact earlier retained run | PASS | Private BDE source: API 81,786 ms; separate external proof 940 ms / 276 assertions; outer 83,587 ms |
| Ordered transport control | PASS | Final source: exact early EOF rejection 5,025 ms; natural helper exit 15,009 ms; later exact capture and handle disposal; outer 18,848 ms |
| Fresh active host | PASS | Final source: setup 31,298 ms; API 71,742 ms (reported 71,738); output capture 171 ms; final fresh OS proof 1,083 ms; case 106,395 ms; outer 107,007 ms |
| Final Current | PASS, 9/9 | 362.8384207 s, exit 0 / termination proved, all seven stability fields true; cleanup succeeds; browser shutdown 9,804 ms |
| Final consumed inputs | PASS, 11 comparisons | 25,128 ms; compiler identity and source/script/web/execution inventory match Current4 |
| Compiler output reuse | PASS | All 1,199 inputs / 357 outputs unchanged; prior actual JDK8/GWT Developer build retained; no new Java build required |
| Visible player flow | NOT APPLICABLE | Host-only changes |
| Full Java/electrical/player matrix, CI | NOT RUN | No new electrical claim; self-hosted runner setup remains a prerequisite |

The fresh host requires exact source/witness/claim associations before recovery,
then actual output closure and fresh child/preview/browser process absence, both
ports known empty, claims/profile absent, and preserved immutable ledger fields.
Its child writes a durable envelope before exit. The parent uses the actual exit
handle before recovery and captures output only afterwards. This avoids waiting
for EOF while the retained preview still holds an inherited pipe. The generic
bounded-process receipt and its EOF requirement are unchanged.

The API interval bounds its own cleanup from above; exact preview-stop/browser
shutdown subphase durations are not supplied by the recovery API. Current's exact
aggregate cleanup duration is also not supplied. Timing components are not
presented as one continuous cleanup measurement. Existing 500 ms, 15 s, 120 s,
90 s, 5 s and 640-step limits remain unchanged.

## Preserved failures

- Source review corrected CDP lock/handle intermediate-state guards and unintended
  assertion return values before the final-source host case.
- An early fixture overlapped a source edit and failed source stability. Synthetic
  issuer/type and CRLF marker-classifier mistakes are preserved with their corrected
  receipts; no pre-fix pass is reused for changed behavior.
- Read-only admission failures exposed an incorrect startup cleanup flag and an
  incorrect PSCustomObject accelerator comparison. Manifest and claims stayed
  unchanged. The corrected exact admission passed before recovery.
- The first transport diagnostic failed: a redirected descendant retained the
  outer pipe. Its explicit inherited-output negative rejected as expected. Both
  helpers exited naturally. The first ordered control then failed because its
  no-redirect helper selected a different launch path; its actors and handles
  were independently proved closed. The corrected channel control passed.
- Current3 failed preview startup at 549 ms against the existing 500 ms ownership
  limit. Fresh-host2 failed at 526 ms. Both preview-only cleanups and independent
  absence audits passed. Later successes establish no universal host-margin cure.
- Fresh-host1 failed before allocation because an initializer overwrote its path
  parameter. Fresh-host2's parent also read an obsolete timing field while
  reporting the child failure. Both caller defects were removed before fresh-host3.

The child now attempts normal owner cleanup once if final envelope persistence
fails. A pre-API source/import/eligibility rejection remains fail-closed and can
leave capture or resources unproved; it does not authorize unsafe fallback cleanup.

Q100 remains in progress: zero accepted 100-part physical layouts, private40/48
timing experiments not adopted, and the long CIN33 CPU profile/75 s capture target
not yet proved. HARD/PSYCHOTIC remain unavailable and unrun; protected data remains
unmaterialized, while the historical unread boundary is UNCERTIFIED. Next is a
source-bound successor of the independent observer canary, followed by the long
diagnostic CPU observation before another timing treatment.

## Maintained commands

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-current-contracts.ps1 -ActivePreviewRecoveryContractOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-current-contracts.ps1 -RetainedPreviewRecoveryContractOnly
```

The real host case is intentionally opt-in, must use selected WinPS 5.1, and must
be bounded externally to 600,000 ms. Run `tests/contracts/browser-active-preview-recovery.ps1`
with `-Case ActivePreview`, the actual `-RepositoryRoot`, a new OS-temp
`-EvidenceDirectory`, and the current module's exact `-ExpectedModuleSha256`.
Only one resource gate runs through cleanup at a time.
