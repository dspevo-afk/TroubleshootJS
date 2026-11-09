# Current projection ownership and accounting findings

Source-only audit at f25dfcc880b69d830e24a2bff755ae0a0a25e74a, before runtime gates.

The consumer uses exact child key sets; mandatory root order is a separate rule.
The projection helpers choose the declared field-list order as a stable producer
convention. The current Java deriveCohort agrees with the independently admitted
ordering algorithm: the two supplied cohort IDs lead, followed by noncohort IDs
in captured order. It validates the prior disposition and requested vector and
returns new lists. Its 255-unit/122-copy/62-chunk Source census is unchanged.

The new cohort adapter does not repeat derivation or clone those lists again.
It reads the two fields and transfers their exclusive use into one new map.
The caller must relinquish any other body use and keep them stable. The actual
derived lists must differ from input/prior/capture containers. Whole-body alias
guarding remains necessary; projection alone does not authenticate the prior.
This resolves ownership by reusing the current derivation owner, rather than
introducing an independent ordering algorithm or duplicate persistent state.

R7 owns cleanup timing. Its actual capture path reads the original duration and
both arm durations, computes the aggregate once at Study3437, then stores that
scalar in the detached plan and final work ledger. SetupMeter has no cleanup
duration counter; work slots are zero placeholders before finalization. The
projection accepts the authoritative scalar, never reads those placeholders,
rereads/adds durations or retimes OS cleanup. Its 23 reads and 20 derived
operations explicitly include all geometry comparisons and boolean reductions.

Actual handoff remains unimplemented: the aggregate map must be inserted before
R7 body ownership, tree accounting and close3498-3505. Likewise the normal caller
must connect the derived cohort and current qualified metadata without relying
on the synthetic input profile. That profile has incompatible orderChanged=false;
it is not modified into a fake genuine prior. Component tests instead use fresh,
clearly synthetic ordinary Map/List Source views and call the existing derivation
method. No OriginalArm or ArmResult is fabricated or invoked.

False cleanup views and a scalar below a synthetic duration sum remain invalid
for the independent semantic consumer. Correct projection must preserve them;
these cases qualify faithful projection, not successful result acceptance.
Normal assembly remains SELECTED_WIRE_CANARY_ONLY. No active producer, full-D,
normal-host, electrical/population or playable Q100 proof follows from this seam.

Precode contract/reference/wire freezes precede the new helper/canary/oracle.
Source helper and canary reviews found their declared5/55-unit projections and
Source-compatible fixtures consistent. The actual old dependency closure and
selected JDK8 host positive/negative canaries passed reauthentication/execution;
new helper compilation/runtime and reader gates are still pending at this note.
An unreleased oracle parent-count assumption was corrected from one per process
to one per case (SMOKE2/SUITE15) before release/execution. No complete raw draft
history is claimed. All original Source/evidence and failures remain preserved.

No existing oracle, cap, seed or epoch changed. Full30 excluded; HARD/PSYCHOTIC
unavailable/unrun; historical protected-unread boundary UNCERTIFIED. Playable100
remains0/beta HELD; genuine oneoriginal then exact NULL/OBSERVE UNSPENT_GATED.
