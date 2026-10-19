# Read-only selector and crop audit

Audited at202aa5e6, accepted private14/density13 and the exact archived rejected15/
density14 algorithm. This source map is not runtime evidence or acceptance.

`MediumBoardPhysicalPolicy.score` uses placement proxies: role-weighted pad MST,
region spread, connector distance, corridor blockage and preliminary extents.
`SeededPcbLayoutGenerator` captures ranked attempts before rewriting the routing
prefix. Repeated-control boards retain4/2/5, then policy-local successful routes
permit bounded narrow/support substitutions. Consequently preliminary rank alone
cannot establish which attempt was requested or rejected.

`considerMediumLayout` compares actual returned outline area first for both
density13 and14, then route quality, preliminary score and attempt. The dense
packing comparator does not apply. Required two-layer routing selects successful
P07 even when P05 could be smaller. There is no established omitted-area bug.

Prefix position selects the attempt; it is not an independent route seed. P05
receives the candidate attempt and session routing seed. P07 receives materialized
placement, board, policy and observer with fresh local passes. Both use the same
normalized placement-seed variation for finalization. Moving an unchanged raw plan
between slots therefore does not itself alter intended routing variation. Prior
outcomes can change eligibility; host cancellation remains a separate boundary.

`finishRoutedPlan` first optionally compacts the routed plan, adds silkscreen,
validates, computes occupied bounds and a uniform edge margin. RB56 accessMargin20
sets a requested minimum50; protruding access envelopes may increase it. Initial
crop rejection invokes `edgeMarginIncludingOutline`, which retains the prior
validated planner outline as an upper bound, then revalidates. Thus actual margin
must be measured before attributing empty space to the minimum margin.

`getOccupiedContentBounds` includes body, keepout, courtyard, selection and drag
envelopes, pads, connected/lifted leads and probe bounds, copper, vias and silk.
It is not painted-ink-only. The parts tray is excluded. `compactToContent` rigidly
translates occupied geometry and sets each outline extent to content+2*margin.
Later finalization changes tray/canvas, not the PCB's internal geometry.

Source owners: `MediumBoardPhysicalPolicy.java`67–129,405–507;
`SeededPcbLayoutGenerator.java`260–269,279–409,463–565,586–669,787–923;
`PcbNetRouter.java`50–93,183–203; `PcbLayerRoutingPrototype.java`69–118,372–423;
`PcbPlacementCompactor.java`37; `PcbBoardLayout.java`793–935.

Next evidence is limited to existing Statistics fields: chronological outcomes,
placement scores and winner quality, paired with exact archived selected layouts.
Only if successful alternatives remain ambiguous is per-route comparator telemetry
needed. No weights, seed substitutions, acceptance thresholds or budgets change.
