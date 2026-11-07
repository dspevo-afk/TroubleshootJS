# Source-only review and independent literal expectations

Status: SOURCE_ONLY; no measured count or runtime qualification.

The original P07 pass2 first branch uses sorted pad IDs, removes the lexical root
and selects the pending pad through chooseBranchPad with only that root reached.
First-branch reachedPthTerminals excludes that root; empty extra-target array and
null are equivalent for Router.permitsLayerStep. FULLER permits both faces.
Do not use degenerate start=end: Router.isLegalMove would apply both outward-start
and inward-end directional constraints on one mounting-face endpoint.
Dedicated query-only routers may prepare net caches/revisions/rejection counters;
never reuse them for a treatment Search.

One query matrix is4cardinals*2faces*2orientations=16calls/pair,8/orientation.
A review message incorrectly said32/16 respectively; root corrected this arithmetic
before implementation. No metric count, threshold or old gate was changed.

Independent fixture-owner source review:
TO92_NPN QA.C local(60,90), escape(0,+1,36), fixture(300,300).
AXIAL_DIODE DA.A local(30,30), escape(-1,0,50), fixture(430,300).
Both endpoints mountTOP. OPEN's BOTTOM emptyComponentFace bypasses component-body
and mounted-face directional restrictions. Forward(300,300)->(300,310) and reverse
(430,300)->(420,300) width13 strokes touch no foreign QA.B(260,300), QA.E(340,300),
DA.K(600,300) or opposite endpoint land. Traversed start reservations belong to
A_COIL_LOW. These two literal witness steps are expected TRUE.

BLOCKED has real8x8 PTH lands centered E(320,300), W(280,300), N(300,280),
S(300,320), each a foreign net. Closed-edge width13 horizontal E stroke
x[294,317] meets land[316,324]; W stroke[284,307] meets[276,284].
N/S have corresponding y intervals and transverse overlap. reservePadTouchSteps
therefore marks every cardinal first edge foreign on BOTTOM. TOP's QA.C escape
already rejectsN/E/W; S is rejected by foreign reservation. Expected forward mask
is zero on bothfaces:0/8 permitted. This oracle was derived before runtime.
Other observed OPEN/reverse-blocked bits are not independently certified here.

Cold Router construction and width preparation have synchronous loops. Source
inspection proves no general5s runtime bound; real canaries and actual authored
work must meet unchanged group limits. Local degree neither predicts an entire
path nor explains foreign-prefix blockage. No ordering law is frozen/adopted.

Reviews were bounded/read-only; production and protected data were not changed.
Original existing nativecanary must still demonstrate blocked result through real
Search, independent of this new metric. Full production/Player/population proof
is not renewed by these source observations.
