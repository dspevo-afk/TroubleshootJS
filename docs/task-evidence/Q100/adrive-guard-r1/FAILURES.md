# Q100 A_DRIVE guard failure and limitation ledger

**Status:** Preserve all failures and boundaries for root review. Expected negative controls are labeled separately from unexpected failures.

## Earlier native guard attempts

1. **Query guard R1 — stale owner after handoff.** The operation failed with `STALE_OWNER: copied ordering owner/Search/layout lease changed` during `MEDIUM_SESSION_ADVANCE`; the Java operation time was 10,166 ms. The native wrapper wrote `NATIVE_RECEIPT_MISSING`, exit 2, and marked termination unproven, while the separate process-observation receipt recorded the child exit and termination as proven. The source sender's Search reference had been cleared after handoff. The retained-receiver-reference fix followed. Keep the wrapper result and process observation distinct; neither R1 receipt is a pass.

2. **Query guard R2 — same opaque stale-owner boundary.** It failed before advance with the same stale-owner exception; operation time was 9,444 ms. The wrapper again lacked a native receipt and reported termination unproven, while process observation recorded actual exit 2 and termination proven. An actual JDK 8 reflection canary then showed that `Field.get` results for both false and true were not reference-identical to the canonical Boolean objects. The implementation was corrected to check typed Boolean values and emit precise failure labels. The canary is limited to that reflection behavior, not routing evidence.

3. **Query guard R3 — native pass, reader failure.** The producer emitted a native PASS, but independent reader R1 rejected lineage because `lineage.source.digests.geometry` was the string `"null"`. `QZ/actual-lineage-difference-r1.json` is diagnostic-only and shows that sole reported difference against the expected geometry digest. The producer was corrected to use a charged verified snapshot and cross-bind against the raw base event. Fresh native R4 and independent reader R2 then passed their declared process scopes.

The early R1/R2 wrapper termination flag is not equivalent to the separate observed process-termination field. Generic wrapper failures with termination unproven remain failures and must not be normalized into routing rejection.

## Reader and helper failures retained

- The first Python guard-unit attempt had a fixture setup error because `helper_raw` was missing; class setup was corrected.
- Review found that API-call counts and controller-group counts had been conflated, and identified grid/base and cursor/branch-counter binding gaps. The boundary-check group-owner positive and negative controls were added. Keep these as distinct corrections; long `advance(1)` API-call counts are not controller groups.
- The first compiled helper canary failed on a PowerShell 5 `Count` property assumption before any build. The next canary reached its cases but failed because `Get-ValidatedNumber` was not in scope. The R3 helper canary passed its selected-helper cases after extraction was corrected. It does not certify compiled production gates.
- Actual guard-reader R1 failed on the null source geometry digest above. Its R2 process wrapper retains its original pending-root-review label; root now accepts the separately normalized VALIDATED_BOUNDED_ADRIVE_GUARD_WITNESS after final-source and gate review. The raw wrapper is unchanged.

## Current compiled sequence checker failure

Compiled final sequence R2 is **FAIL/STOPPED** at the aggregate check; preserve that result. The fresh Player build independently **PASS**ed in 87.7057332 seconds. The checker expected 357 archive entries before the build, but the actual fresh pre-build count was 0. The actual post-build output contains357 files and the five-permutation/link build log passes. The archive had not run when R2 stopped. Do not collapse this into either a Player build failure or a completed compiled matrix. R3 audited the existing Player build against all consumed current inputs and outputs, then created the actual357-file archive. Player privacy and Developer build passed. R3 then stopped on an invalid comparison of Player versus Developer input digests. The maintained build-identity owner includes variant and module in the digest, so those two values legitimately differ. Root retired that temporary aggregate and validates each variant against its own input identity, using the archived Player output and current Developer output. The failed R3 result is retained. Direct E06State10 and Current9 subsequently passed. The new distinct root per-variant review passes after checking all route statuses, cleanup fields, maintained input/output identities and all357 archived Player files. The failed R2/R3 aggregate results remain FAIL.

## Other final-gate harness corrections

- Native focused8 passed. The first native aggregate stopped before regression execution because root renamed a snapshot schema label required by the frozen v5 host. The replacement producer restores the exact existing label while preserving candidate/source bindings, suite list and budgets. Fresh regression-r3 passes all33 suites, including original22. The passing focused8 is reused only after a fresh complete1079-input/critical13 audit found the consumed source unchanged. The failed aggregate and regression-r2 metadata remain preserved.
- The first legacy170 wrapper stopped before any Python method because PowerShell5 wrapped the JSON array as one value. Direct assignment followed by array wrapping restores the actual40-row pin census without weakening it. The replacement run completes168 passes and the two declared Windows file-symlink skips.
- The first final-source negative-host control expected a wrapper-failure receipt even though the deliberate bad manifest is rejected before wrapper launch. Its harness failure and logs remain. The replacement records the bounded process observation first and verifies the exact pre-launch rejection, termination and absence of native runtime artifacts.
- Normal3 orchestration paths and the new compiled-review status were aligned before any Normal3 actor ran. Earlier unrun helper sources remain preserved; no receipt was fabricated or copied into a success slot.

## Expected negative controls (not unexpected failures)

- Wrong input-spec SHA: reader exits 2 with `INPUT_SPEC_SHA256_MISMATCH`.
- Wrong command identity: reader exits 2 with `NATIVE_RUN_IDENTITY_INVALID`; the fixture was a labeled synthetic clone and the original command was untouched.
- Actual selected CLI controls: mutually exclusive mode and wrong suite both reject before compile as expected.
- Junction canary: regular-file read succeeds while selected resolve/read/bootstrap negatives reject; exact owned link cleanup passes.

These outcomes are PASS for their stated negative-control contracts only. They are not native routing qualification.

## Final scope and unavailable evidence

The old170 methods now complete168 passes plus two declared privilege skips; the new26 methods complete25 passes plus one declared privilege skip. Focused8/native33(original22) pass. All six existing diagnostic/consumer pairs pass. Fresh Normal3 completes all six phases with independent validation, followed by exact owned-profile cleanup and its wrong-receipt rejection control. The raw sequence retains PROFILE_CLEANUP_PENDING; the later distinct cleanup receipts close that boundary without rewriting the sequence. Normal3 is limited to roots `-2230691771308388107`, `6702341860553104274`, and `-8360266000248569150` under the existing four-candidate envelope. Full30 is owner-excluded.

Source resumption/finalizer/result-taking and physical/electrical oracles are NOT RUN. Independent two-face access is unavailable. Protected HARD data remains unread and unrun in this scope; historical unread status is UNCERTIFIED. HARD/PSYCHOTIC remain unavailable and unrun. The A_DRIVE guard witness itself does not establish final routing feasibility, board impossibility, route adoption, ordinary comparison, playable100, or beta release. Native cleanup timing outside the Java event and separate reader cleanup duration are NOT EXPORTED.

## Final metadata-only harness corrections

- Actual PS5 parameter-default `$PSScriptRoot` was empty, while the script-body
  value was correct. A selected-host canary establishes that boundary; the audit
  now resolves its empty root in the body while retaining the exact-root guard.
  Production identity audit R2 and post-Normal3 R3 pass.
- Old-reader sequence R1 stopped before Python because its runner pin named an
  intermediate source. Fresh R2 sources pin the reviewed runner and distinguish
  the unchanged remainder reader's VALIDATED_CONCLUSIVE_EXPERIMENT_OUTCOME from
  the other five PASS verdicts. All six fresh reads pass in sequence output R3.
  No consumer, oracle or raw native artifact changed.
- Final source-audit R1 counted every tests/contracts path as Java, including
  its PowerShell runner. R2 filters the same closed census by .java:850 production,
  223 contract Java and6 static inputs. All1079 hashes/bytes still match6260778c.
  R1 remains a failed metadata check; R2 passes without rerunning passing gates.

Final root acceptance is limited to this finite diagnostic and the stated
regression gates. No failed aggregate or earlier pre-fix PASS has been promoted
into final proof. The exact-profile helper terminated no processes; the host
runner owns listener/actor cleanup. The marker and raw host results remain.
