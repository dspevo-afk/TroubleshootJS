# Connected-pocket packing contract worker notes

Base `2a73b949a02de718bd968c2920ba6045d7406c5a`, branch `codex/q60-faulted-profile-counts`.
This leaf owns only `tests/contracts/PcbConnectedPocketPackingContractTest.java`
and this note. The test exercises the package-private placement helper and
compactor orchestration with fixed fixture data; production files remain owned
by the root.

Contract source SHA256: `01D4EB99E5A25BA940A0C1323CCFE253354DC88037DFA6CF1A0C1D903060FEBE`.

The five named groups are `literalConnectedPocketPositive`,
`countedNoOpAndLegacyFlag`, `evaluationCeiling`, `cancellationAndScopeCleanup`,
and `singleFacePackingFlagDoesNotPrecompact`.
The test source contains 49 literal checks and
prints the marker `PASS: PCB connected pocket packing contracts assertions=N
fixtures=N` when executed. These counts are static source expectations only;
the test was not run in this worker.

The additional group calls the actual planner twice on the real 35-component
`Rb30Plan.reference(13).board()` inventory, seed 0 and candidate 0. Both
external constraint copies retain the board's parts, barriers, density, routing
layer, two-layer requirement, and trace-width profile while selecting the
default `THT_SINGLE_FACE@1` policy; their only declaration difference is
packing version 1 versus 2. It asserts matching candidate-0 geometry and
evaluation snapshots and verifies that neither planning call changes the
source graph or the board's stored constraints. This checks planner behavior
through actual invocations rather than reproducing the production predicate.

The positive board uses three declared `THROUGH_HOLE_LED` packages at A(120,100),
B(800,100), and C(120,800), plus the real `THROUGH_HOLE_CONNECTOR_2` J at
(120,400) using its declared `DEFAULT_MIRRORED_X` / `MIRROR_X` geometry variant
and LEFT anchor. It contains four placement demands, eight pads, and four nets;
the three LEDs share two independent three-pad nets, while J has two singleton
nets. Every demand declares region `all`, domain `board`, and a 20-unit access
margin. The fixture independently computes the envelope bounds from literal
package geometry: the source bounding box is (70,64)..(904,935), area 726414;
the expected result is (70,54)..(534,556), area 232928. The three LED pairwise
Manhattan distances sum to 2760 before and 640 after; each undirected pair is
counted from both endpoints on each of two nets, giving weighted wire demand
11040 and 2560. Expected final origins are A(120,100), B(270,90), C(430,90),
with J unchanged at (120,400). Pad coordinates, complete source graph identity,
source plan immutability, planner metrics, and deterministic repeat output are
checked separately.

The no-op fixture uses two mirrored, LEFT-anchored connector packages around a
single LED, with all six pads on singleton nets. Its declared package envelopes
give a literal 490-by-202 bounding box (area 98980), so the connector anchors
bound both axes and every candidate that leaves the current pocket cannot
improve either objective. Version 2 orchestration must count bounded rejected
trials while `samePlacement` remains true. Version 1 returns the exact input
plan. Separate cases start at 59999 evaluations to check the final allowed
trial and at 60000 to check exact exhaustion without further work. A cancelled
`GenerationJob` checks typed cancellation propagation and that exiting the
work scope clears the cancelled owner.

The checks are placement-only contracts. They do not execute the router, infer
electrical or manufacturing feasibility, or validate generated-board families.
The connected-pocket pass, medium family matrix, identity/session gates, actual
JDK8/GWT build, and requested visible screenshots remain root-owned.

Validation in this worker was static source inspection only. Java/unit,
native, GWT/build, routing, and browser commands were NOT RUN. No runtime
evidence was created and no other file was edited by this worker.
