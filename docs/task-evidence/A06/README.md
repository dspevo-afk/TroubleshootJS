# A06 power, reference, isolation and operating-state contracts

Status: COMPLETE - QUALIFIED under the user's direct/solo implementation instruction.
Base: `0acb8f95a2573a98f84a93ece030e3f366d40051` on `codex/task43p-final-recovery`.
The containing commit identifies implementation and evidence. Actual commit,
remote verification and email outcomes are recorded only after publication.
A07 remains unstarted.

## Delivered behavior

`PowerDomainContract` holds immutable explicit rail/source/reference/isolation,
optional physical-earth and backfeed declarations over resolved nets. Source
capacity is separate from implemented current limiting. These declarations do
not create electrical connectivity or claim that a numerical ground is earth.

`PowerOperatingAssessment` distinguishes connected, partial and isolated source
states from driven, backfed, residual, discharged, unknown and out-of-envelope
rail observations. Unknown references, missing/nonfinite samples, stale source
commands and unknown storage obligations cannot grant active-meter readiness.
Equal labels do not join nets; disabled sources can still be backfed.

The current bounded device projects its already resolved sources and reference
into these declarations. Its installed runtime capability samples the actual
current owner's solved posts and power controls. Source-control revisions and
simulation-time/owner checks invalidate stale observations after switching,
reset, temporary meter work or board replacement. No second live state owner or
replacement physics engine is introduced.

Differential measurements require the declared reference relationship. A
permitted earth-referenced request still requires an actual owned connection;
this milestone does not ship an earth-connected scope. Powered-provider supply,
reset/clock/brownout, thresholds and finite-drive requirements are explicit;
current low-side selection consumes the narrow applicable requirements.

Stored-energy readiness now rejects NaN/infinite observations and combines
multiple readiness policies conservatively, independent of registration order.
The existing residual-voltage threshold and actual measurement overlay remain.
Current power declarations participate in manifest reconstruction; incomplete
old artifacts reject before mutable construction. No historical resolver added.

## Final qualification

Source digest: `0ca528d1cca6aed4ca6fc18a0bc04db8b019360e055d942f2730beb1aa35678a`.
The maintained source/script/web/execution tuple is in candidate-provenance.json.
All 924 source files match the final production build inventory.

| Gate | Actual result |
| --- | --- |
| Maintained native runner | PASS, exit 0; 16 Java suites; A06 140 assertions; report protocol 215; independent 22 seed / 9 value / 6 role cases; owned scratch cleanup. |
| Actual production build | PASS, JDK 8u502/GWT 2.7, five OBF permutations; 49.695 s compile / 1.160 s link. |
| A06 real solver proof | PASS; 82 shared pure assertions and 44 runtime assertions; actual isolated-source backfeed measured 2.5 V and 2.5 mA through the fixture link. |
| Compiled corpus | All six maintained routes PASS: A06 positive, expected A06 forced failure, debug-off, A03 reconstruction, current Task49 repair corpus and RC stored-energy proof. Negative failure is not relabeled application success. |
| JVM/GWT parity | Exact shared power/reference/provider decisions, identity vectors, three current manifests, six role and nine binary64 value cases. No old golden preservation. |
| Repeated instrument lifecycle | Actual successive loaded-DC and resistance measurements restore overlays; source state, reset/observation and stale-owner rejection checked. |
| Affected gameplay | Current controlled repair/retest and real RC charge/discharge, loose-part, instrument and repair checks pass from final compiled source. |
| Normal-player smoke | Real Win32 input shows 5 V, powered OHM blocked, repeated isolated same-net 0 Ohm, meter exit/reentry and new-owner stale-reading clearance; three inspected screenshots. |
| Review | Direct self-review and handwritten falsifiers. No independent-model review; user explicitly requested solo continuation. |

See native-build.json, compiled-summary.json and player-smoke.json for details.
The native stub does not certify GWT; the real final build and actual compiled
routes are separate. Pure fixtures do not alone certify modeled isolation.

## Screenshots

- 01-dc-supply-reading.png: ordinary probes display the actual 5 V supply.
- 02-powered-ohm-blocked.png: OHM mode displays POWER OFF while supplies remain on.
- 03-isolated-ohm-reading.png: actual power isolation permits a 0 Ohm same-net reading.

No debug/controller injection provided these player actions. Initial inaccurate
window-offset clicks are excluded from success claims. Reset is hidden from the
normal player accessibility tree and was not clicked; compiled reset/restore
checks supply that separate evidence. No independent OS input telemetry claim.

## Corrected findings and scope limits

SELF_REVIEW_FINDINGS.md records the reproduced unknown-reference, out-of-envelope
and unknown-brownout defects and their tested repairs. RC verifier entry now
establishes a real powered fixture and waits for the owning temporal verification
to return; it no longer starts inside that still-running call. Production settled
and powered-meter predicates were not weakened.

This is the A06 contract foundation with current consumers and bounded real
fixtures, not an arbitrary multi-rail/mains product, dynamic limiter, numerical
reference sensitivity qualification, new scope, MCU or A07 solver execution lane.
The present composed family has one explicit joined return and known non-storage
primitives. A future storage/source/reference model must supply truthful policy
rather than inherit this bounded projection. A07 owns further solver/reference
sensitivity work. No earlier generator behavior is protected.

## Resources and handoff

The final owned browser and preview were closed normally; standard stop released
port 58408 and the retained launcher exited 0. The initial task-preview parent
failure and exact-child cleanup are separately recorded, not hidden. No unrelated
process or old 8899 preview was touched. Scoped recovery evidence remains in the
existing task OS-temp directory for audit. Pre-existing Python cache artifacts
are excluded from staging. Direct root alone owns final commit/push/email.
