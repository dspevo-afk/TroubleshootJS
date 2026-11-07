# P8 source review and preliminary fixture corrections

Source-only findings; no P8 compile/native/BASE execution or reason distribution
is established by this document. P7 remains accepted at316eba54/0891474c.

Three leaf audits covered ordered Router/rules guards and available operands,
P6 private query composition/work/cleanup, and reusable literal fixtures. Root
read the affected guards/callers and reviewed the frozen native API. A further
read-only independent API review was scoped CLEAR: no predicate-order,
single-evaluation or copy-sink blocker. Its scope excludes the implementation
and runtime. It requested explicit absent-boundary domainHorizontal=-1 and
nullable interrupted-call result semantics in the eventual transport addendum.
The latter full BASE transport remains unfrozen/unimplemented.

Pinned production Router92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec
and rulesb04f9906d726117412d62b6022ea5b8e2f26bf111063292b0d7560f51b0fb8cf
have the ordered guards in API_SCHEMA_R1.md. The accepted P6 Router overlay
0432eed2ac4e84c647aee867cc1856bff7969280b094794bc0acb95907d66d94
provides only coarse endpoint stages; its query copies are private to P6. P8
must own its separate compared views and actual full queries. No observer is
stored on shared rules; per-call callback propagation keeps one predicate body.

The ten first-false site labels are START_ESCAPE_DIRECTION,
END_ESCAPE_DIRECTION, DOMAIN_UNKNOWN_NET, DOMAIN_CORRIDOR_OVERLAP,
DOMAIN_MEMBERSHIP_SIDE, EMPTY_FACE_RESERVATION, ADJACENT_RESERVATION,
PAD_ESCAPE_INTERSECTION, PROFILED_PAD_TOUCH and COMPONENT_COURTYARD.
Domain membership's original OR stays intact; which inner membership operand
caused it is NOT_OBSERVED. Nested escape/underpass arithmetic is NOT_OBSERVED.
The first encountered pad/component/boundary/cache does not prove unique trace
ownership or physical/routing feasibility. P6's known9/5 coarse split is not a
new P8 outcome.

Root caught these fixture-draft problems before any execution:

1. P6's occupied A/AB copper halo produces mixed clearanceNet and an occupancy
   rejection. It cannot prove horizontalReservation/verticalReservation guard
   attribution. Those edge caches are rebuilt by prepareNet using pad escape
   rectangles and, for profiled pads, finite-width touch steps. New literal pad
   reservations must be constructed independently; geometric rejections should
   retain zero congestion/conflict deltas.
2. Adjacent steps use cached reservation guards and do not reach the slow pad
   scan. PAD_ESCAPE_INTERSECTION/PROFILED_PAD_TOUCH literals need a genuine
   nonadjacent/diagonal/zero-length path through the original scan. Legacy
   emptyComponentFace also returns from its fast path for nonadjacent queries;
   disable that fast path through the fixture's real same-face component, or
   use the maintained profiled construction. Do not skip guards manually.
3. The maintained role-width nonadjacent fixture supplies HIGH220,220->240,220,
   foreign S.1 at260,240, actual width13 stroke214,214,33,13; it touches the
   foreign land only at its closed corner. S.1 at261,241 is the independent
   one-unit-gap positive. This source fixture can exercise the slow profiled
   touch guard; the earlier adjacent width-touch fixture cannot identify it.
4. P07TwoLayerContractTest.barriers() says PRIMARY rectangle530,300,10,10 is
   opposite-domain reappearance. It is a membership-side literal, not proof of
   corridor-overlap attribution. Freeze a separate corridor literal from the
   exact placed boundary; the existing forbidden VIA400,300 is a corridor
   oracle. An unknown net is invalid-input evidence only.
5. A raw escape ends at230 but its reservation also includes width clearance.
   An asserted positive240->250 can remain inside the expanded rectangle.
   Freeze exact expanded bounds before code; never shift coordinates after an
   unexplained runtime failure merely to get PASS.

The original draft is preserved before correction. These are design-review
corrections, not failed native cases or accepted canonical observations. The
corrected literal plan must be frozen before canary code. Source transform/static
checks and real selected host canaries precede any exact BASE replay. All prior
P5/P6/P7 failures stay preserved. Production850/files, caps, seeds, epochs,
electrical truth and independent parent oracles are unchanged. Full30 excluded;
HARD/PSYCHOTIC unread/unrun here; historical unread boundary UNCERTIFIED.

## Actual overlay source review and static tests

The later independent read-only implementation review is scoped CLEAR for
builder e62fbb833e3751ff2f53b38958b5b8ddf946ff210b18cfcf3b5052e19ab15ec0,
Router overlay3bc847e015e1b379dcafcb1e39eb4964e65e7a57d6ef06ed2c710cca1ff48f2f,
rules overlay9e59ae5969b593617042bb2a571aab4037235a13f06e03379d341eaf80ae94d9
and manifest1a85cddc54a077c90828a534a87a4eac0f965fc9ad3305cbff9f4c329f1ed43e.
It checked actual first-false placement, original compound expressions, the
single-evaluation empty-face return, null-sink metadata guards, final immutable
event fields, copy-default-null and per-call rules propagation. It found no
obvious Java syntax/type issue by inspection; it did NOT compile or prove the
future inspector's finally detachment.

Root ran run-static-tests.ps1 through selected PS5.1 and the maintained bounded
process API. All11 independent static tests PASS in0.011s; selected Python child
wall374.9071ms, exit0/termination proven and original verifier ProcessRoot absent.
Builder/test/controller hashes unchanged before/after. Receipt static-tests-r1.json
retains raw actual PID/start/executable/arguments/streams. Static checks preserve
the old coarse API/chain, bounds/loop, copy-shell constructor, occupancy body,
legal guard text and deterministic pure transform; corrupt source/newlines/BOM
and generated-overlay-as-input are rejected. This is PASS_STATIC_ONLY, not JDK8,
native reason/cleanup, canonical capture or physical qualification.

Before final pin creation, root removed personal absolute source/tool paths from
the reusable static test/controller sources: test_overlay.py now takes an
explicit repository-root argument, and controllers take explicit tool/root
parameters. A fresh selected-PS5.1 bounded run static-tests-r2.json passes the same
11 tests in0.011s, child376.9958ms/exit0/proven termination/cleaned process root.
Final test SHA4c289494d41daf4b627e09402d7e958bd7db0bda418cf8d9dc5f28bb67eebb2e;
static controller7c7143e4148ab6d169f6c4a0d2874b7de30694f468b217803660740d2939c9e9.
The original R1 remains evidence for its earlier bytes; R2 is the current static
test proof. This portability correction changes no fixture or expected assertion.

The later import-boundary review found ordinary transitive imports were still
exposed to script-directory/PYTHONPATH lookup under -B alone. PYTHON_ISOLATION_R1
freezes -I -B for new P8 maker/auditor/tests/consumer invocations and entry guards
before non-builtin imports. Authenticated source execution still uses pinned
cached bytes. The fresh selected PS5.1/module static-tests-r3.json passes11
unchanged assertions in0.011s, child436.0441ms/exit0/proven termination/cleaned
process root; actual arguments are -I,-B,test script,explicit repository root.
Current test SHAa2778a50105a691b0408004edcafcce38931fa1408516439cc665a7c173f20f0;
controller008c6ae57756c3138babaa007ae1d92f165e5794d7a6b3bd48cda2a42f5c7170.
R3 supersedes R2 only for this current invocation/source proof; earlier runs stay.

Further pre-execution review caught a one-sided control allowlist addition
between maker/auditor, incomplete parent receipt/consumer closure timing,
substring-only rejection checks, no terminal outer-write guard and insufficient
failed-call evidence. The allowlist now has consistency assertions; frozen
rejection/closure addenda require exact frames, actual pre-compile flags,
continuous timing, post-write limits, genuine output fingerprint preservation,
and explicit missing-process proof on an API throw. Root controller implements
those changes; focused real host/native/consumer gates are still NOT RUN here.
The selected PS5.1 source preflight also AST-parsed root controllers and exercised
the suppressed ScriptBlock mutation form successfully; that tiny fixture is not
a native process-wrapper or full-controller qualification.

The final focused host source3d445e2810be56d7b9ef9c62a52e4fd1120c5d3eed3c15699303c29ac744acb4
has a fresh independent scoped CLEAR for PS5 collection handling, exact-I,-B
auditor arguments, prevalidation before output creation, unchanged returned
process/stream receipt provenance, continuous R3 native interval and retention
of scratch when process termination is unproven. This is source-only review.

Root then exercised one REAL selected-host path through the unchanged bounded
module, before any compile/native gate. Early harness R1 supplied raw forward-
slash paths where canonical Windows paths were required: FAIL intended digest
test, actual NONCANONICAL_PATH/HOST_CONTEXT rejection,662.9593ms/exit1/proven
termination, no output/scratch/compiler/native invocation. Preserve its exact
receipt and harness; do not reclassify it as a digest PASS. Only the harness
was corrected to canonicalize all parameters. Fresh R2 passes the intended
INPUT_PINS_HASH/HOST_CONTEXT rejection in734.8038ms, exit1/proven termination,
actual verifier root absent, empty stdout/exact one rejection frame/all four
phase flags false/cleanupError null, output absent and host bytes unchanged.
early-digest-parent-r1/r2.json retain original raw returned process fields.
This is PASS_EXPECTED_DIGEST_REJECTION_ONLY, not final five-control, JDK8,
native literal, source-auditor import, consumer or BASE replay qualification.

The root controller's revised closure/exact-frame/fingerprint/outer paths also
received a scoped static CLEAR at its reviewed bytes. Root subsequently kept
the original process-call error primary if recording that failure itself fails;
this small logging correction is root-reviewed, not a claimed fresh independent
runtime PASS. Actual controller acceptance must require exit0/proven termination
and the final wall guard, even if a provisional result/marker already exists.
