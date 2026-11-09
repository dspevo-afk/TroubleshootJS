# RUN input policy and pre-body cohort staging

The native input profile required `orderChanged=false` for `RUN`, while the
shared owner emits `RUN / TRIAL_EXECUTED` only when order changed and the existing
cohort derivation requires `true`. This prerequisite corrects the native profile
and qualifies setup-to-cohort compatibility using a fresh synthetic input.

`CurrentAssemblyMetadata.cohortBeforeBody` borrows the validated raw prior in a
temporary one-entry view, calls the existing derivation owner, and returns its
detached lists through the established projection. It leaves input and captured
order unchanged. The view never enters the result body and does not replace
receipt-bound metadata preparation, whose method body is unchanged.

The frozen contract predates implementation. The new synthetic context changes
both flags and the requested vector to the literal pair-first stable order;
captured60 and the A/B positions43/50 remain unchanged. Its identities are in
`FIXTURE_REFERENCE_R1.json`. It is not genuine prior or original-owner evidence.
The unchanged independent codec checks structural context; native input policy
and cohort derivation enforce the stricter RUN and requested-order semantics.

Expected debits are independently Source-censused: setup10007units/478copies/
3chunks; staging264/125/64, including the4/1/1 temporary view and existing255/122/62
derivation plus5/2/1 projection. Combined10271/603/67 with two closed groups after
the caller's successful finish. Every rejected Meter is discarded without finish.
The maintained input regression preserves81cases/12089assertions; its old false
RUN profile and Source pins remain historical, rather than current authority.

The corrected candidate is locally accepted for this synthetic component scope.
[PROOF_R1.json](PROOF_R1.json) binds Source freeze
`bb445458907e7ab757e0bb5bc2acfffd6c9352bba5909a91ca43051f3705cdd9`.

| Final gate | Result | Operation / wrapper elapsed |
| --- | --- | --- |
| JDK8 final-source compile,5 Sources/26 major51 classes/113139 bytes | PASS | 1276.4981 /1518.2091ms |
| Maintained native81 cases/12089 assertions | PASS | 460.9465 /691.7376ms |
| Native9-case setup/staging suite | PASS | 389.5344 /634.5127ms |
| Private actual-output independent reader | PASS | 345.5267 /576.3959ms |
| Private12 structural profile tests | PASS | 741.6312 /969.0917ms |
| Fresh public12 profile tests | PASS | 522.9073 /752.9913ms |
| Public actual-output independent reader | PASS | 344.7177 /581.4962ms |

The accepted863-Source/2486-class compile closure was fully reauthenticated and
reused. Five superseded classes were excluded from a verified dependency copy;
the accepted projection helper was added, leaving2482 dependency classes. Fresh
classes and dependencies have no duplicate/shadow paths. Two real JDK8 host
controls, one selected reader positive, eight expected-exit2 reader negatives
(including the real existing junction), and two expected-exit1 JVM protocol
controls pass. All20 final candidate process receipts/inputs are reauthenticated;
termination and owned-root/log absence are proved. Isolated process-cleanup
duration is unmeasured. The helper owns no OS resource. No native rerun followed
public copying: all compiled/runtime Source bytes were compared exactly, and the
public pure and reader entry points ran freshly.

The [first native regression failure](failed-candidate-r1/FAILURE_R1.json) remains
FAIL. The pair-first fixture moved A from requested index43 to0, but the cohort-
subset negative still replaced index43 and was accepted. Both requested mutation
targets were corrected to0; all12 indexed mutations were reviewed independently,
and the complete final-source build/regression passed again in a fresh candidate.
The old Source, classes, output and process receipts remain preserved. No case,
assertion or expected rejection was weakened. Authoring/format preflight failures
and draft Source-review corrections are recorded by [SOURCE_HISTORY_R1.json](SOURCE_HISTORY_R1.json).

Root reviewed the integrated Source/diff. Independent Source reviews cover the
two helpers and the corrected indexed mutations; no independent native run is
claimed. Receipt-bound `prepare` is Source-reauthenticated and compiled here, but
its unchanged behavior was not rerun. Reader partial failed-setup units are
bounded rather than independently censused; staging deltas are exact. Null-guard
checks use an already validated closed setup, then add zero staging work. The
640-group fixture closes its preparatory groups; no failed operation is followed
by a caller finish. Factory snapshots and test assertions are testbench work,
outside the helper debit contract. The prior-view equivalence with later metadata
is Source-only; this packet grants no later metadata/receipt integration proof.

The publication includes sanitized Source projections and synthetic fixtures.
Native execution still requires the pinned private dependency Source/class and
selected-host closure; the packet does not promise standalone public replay.
Process cleanup duration is separately recorded; successful termination and
absence checks do not invent a measured isolated duration.

Normal assembly, R7 scalar-to-cleanup insertion, full-D semantics, actual host
authority and lifecycle remain unqualified. The genuine original then exact
NULL/OBSERVE envelope remains UNSPENT_GATED. No owner/OriginalArm/ArmResult is
fabricated, and no producer, graph, Session or routing operation is run.
Production, GWT and Player are unchanged, so those builds/visible checks are
NOT APPLICABLE; electrical/population qualification is NOT RENEWED and CI is
NOT RUN. All original budgets, exact seeds, epochs and CircuitJS truth remain.
Playable100=0/betaHELD; full30EXCLUDED; HARD/PSYCHOTIC unavailable/unrun and
protected-unread boundary UNCERTIFIED. Old normal20 stays historical READ with
no reread; protected/held-out DATA and old actual DATA/classes remain denied.

Next is the R7 scalar handoff under an explicit body-staging contract, before
whole-result/full-D/actual-host acceptance or any genuine-envelope spend.
