# Current V2 canonical codec source worksheet

**Status:** source draft only. Source hashes were checked before inspection. No
compile, test, native gate, result export, producer, or product-data access was
performed. Root owns all execution, integration, and publication.

## Pinned source closure

The following explicitly permitted Source pins were hash-checked against
SOURCE_INPUT_R1.json before reading:

- CURRENT_FLAT_STUDY.java:
  0a7e31b20dabd80a33e5dc5886507817cbc138ce96a37eb20ecc54a6e2ff93e0
- R_STUDY.java:
  88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf
- W_STUDY.java:
  1ae31054a430c00bf926ff4deff8091892bae267f05257aa0d869000226f7bd1
- CURRENT_PURE_KERNEL.py:
  8e8c374a089a13c4defea9c4b5b548eb1bc1242cddabc1faf468dba420695af4
- WIRE_R1.md:
  1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed

The implementation imports only Java Iterator, List, and Map. Its Source
closure is the existing
CohortTerminalTelemetryStudy.CanonicalJsonSink callback plus the current
canonical literal, integer, and string helpers. No sink implementation,
Meter, filesystem, producer, export entry point, or geometry serializer is
imported or copied.

CURRENT_FLAT_STUDY.java contains the selected sink interface and ordinary
formatter around lines 2451–2644; its R5 body/count/write/compare sinks and
1 MiB limit are around lines 2976–3109. The R formatter in R_STUDY.java is the
ordinary ASCII output precedent. W_STUDY.java carries a canary-only typed
geometry path; this codec does not call it. CURRENT_PURE_KERNEL.py fixes the
13 result keys, schema/mode/status, lowercase UUID/SHA grammar, depth 64, and
key-inclusive 1,000,000 visit policy. WIRE_R1.md fixes the 14 marker keys,
identity values, numeric maxima, and 656-byte bound.

## Body path

The stable method is writeBody(Map<String,Object>, CanonicalJsonSink). It first
calls the supplied sink before reading the body, then performs only bounded
13-entry header checks: exact root key order, fixed schema/mode/status,
lowercase UUID and SHA identities, the frozen wire-contract SHA, and Map kind
for the six container roots. Child Map contents remain ordinary data and are
not subjected to N/P07 semantic validation here.

The BodyWalk object and its 65-reference active ancestor array are bounded
formatter-control/VM state. They do not detach or copy the borrowed body graph
and create no copied-entry billing.

The body then takes one fused serialization traversal for count, write, and
read-compare sinks. Each value, map key, container iterator, entry, list child,
and string character is preceded by sink.beforeDataOperation before inspection.
That traversal emits the value as it validates it; there is no whole-body
prewalk at position zero and no second graph or body-byte counter. It preserves
Map/List iteration order under the standard Map contract and caller-exclusive stable ownership; it adds no per-map duplicate-entry sets or copies. Cycles are rejected using a bounded 65-reference active
ancestor stack.

The domain is null, Boolean, Integer, Long, printable ASCII String/key through
256 characters, List, and string-keyed Map. Other Number types, arrays, control
characters, non-ASCII, unsupported keys/values, depth above 64, and visits above
1,000,000 fail with path-free CodecFailure codes. Root depth is zero; map keys
add one visit but no depth; every value node, including each container and
scalar, adds one visit. Exactly 1,000,000 visits is allowed. This mirrors the
pure kernel's full-domain rules; the existing Java formatter's looser
value-only 1,048,576 ceiling cannot admit a body rejected by this policy.

Body strings use the ordinary R formatter's quote/backslash escaping and
printable-ASCII rules while validating and emitting each character through the
same sink. Integer and null/Boolean formatting use the existing current
canonical primitive helpers. Final LF is emitted through the supplied sink.
The selected R5 sink continues to own result byte limits, block admissions, and
the unchanged 5C+3 / 2R+32+M / 2C+2 / one-group export accounting. This codec
does not call Meter APIs or add export fees.

The fixed wire-contract SHA constant is
1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed. It pins
the protocol identity from WIRE_R1.md; it does not authenticate producer source.

## Marker path

Marker has a typed package constructor in this order: runId,
sourceManifestSha256, wireContractSha256, inputContextSha256, borrowed
resultDigest byte array, resultBytes, elapsedNanos, finalExportNanos,
totalGroups, totalUnits, maxGroupNanos, maximumUnitsPerGroup. The constructor
retains the digest reference directly; caller-owned strings and digest stay
stable through both methods and publication.

Marker.wireContractSha256 must equal the same frozen WIRE hash enforced for
the body, not just an arbitrary lowercase SHA-shaped string. This identifies
the supported protocol contract; it does not authenticate the producer.

The 14 output keys are emitted directly in WIRE order:
schema, status, runId, sourceManifestSha256, wireContractSha256,
inputContextSha256, resultSha256, resultBytes, elapsedNanos,
finalExportNanos, totalGroups, totalUnits, maxGroupNanos,
maximumUnitsPerGroup. Schema/status are fixed emitted literals. UUID and
four SHA identities are checked as lowercase ASCII; resultDigest is exactly
32 bytes. Numeric bounds are resultBytes 1–1,048,576; elapsedNanos
0–90,000,000,000; finalExportNanos 0–5,000,000,000; totalGroups 0–640;
totalUnits 0–Long.MAX_VALUE; maxGroupNanos 0–5,000,000,000; and
maximumUnitsPerGroup 0–32,768.

For B6 accounting, the bounded marker view is read directly from the existing
Marker: five borrowed references (four identity strings and the digest) plus
seven primitive longs. The typed constructor assigns these references/scalars
without cloning. Preflight and emission may read the same stable fields again.
Charge this bounded view, constructor assignment, and the count/write sink
position/ended counters within the existing single marker-finalization API
unit. There is no extra 12-field logical DTO copy and no digest clone. Each
count/write sink has only an int position and boolean ended control state.

countMarker emits through a bounded counting sink and allocates no marker
bytes. writeMarker validates, then writes exactly once into the caller's
pre-admitted, exact-size byte array. It does not call countMarker and creates
no marker Map, key array, hex String, or encoded-body array; digest hex is
written nibble-by-nibble from the borrowed digest. It verifies final LF and
exact target length. The 656-byte marker ceiling is separate from the
722-byte outer transport ceiling. WIRE's maximum arithmetic is 206 quoted key
bytes + 355 fixed quoted value bytes + 65 numeric digits + 30 punctuation/LF
bytes = 656.

## Failure codes and remaining proof

Body failures are path-free CodecFailure codes: BODY_SINK, BODY_ROOT_TYPE,
BODY_ROOT_ORDER, BODY_SCHEMA, BODY_MODE, BODY_STATUS, BODY_RUN_ID,
BODY_SOURCE_ID, BODY_WIRE_ID, BODY_CONTEXT_ID, BODY_ROOT_CONTAINER,
BODY_DEPTH, BODY_VISITS, BODY_CYCLE, BODY_STRING, BODY_MAP_KEY, and BODY_VALUE.
Marker failures are MARKER_NULL, MARKER_RUN_ID, MARKER_SOURCE_ID,
MARKER_WIRE_ID, MARKER_CONTEXT_ID, MARKER_RESULT_DIGEST,
MARKER_NUMERIC_BOUND, MARKER_TARGET, MARKER_LENGTH, and MARKER_OUTPUT.
Schema/status are constants, not caller-controlled Marker fields.

Root still owns the independent canonical-byte oracle, selected actual sink
gates, exact numeric/656 boundary canaries, compilation, and review. No runtime
or native acceptance is claimed. The source drafts do not enable exports,
producer calls, normal-host authority, or genuine full-D data.
