# Current V2 canonical codec: source contract audit

**Scope:** source-only review of the proposed standalone body/marker codec. No
execution, tests, imports, native build, DATA, journal, old class, or legacy
module reads were performed. This is not runtime or export acceptance.

## Reviewed pins

The current plan and 13-entry source inventory are pinned as follows:

- `PLAN_R1.md` SHA-256 `99f400338b4ee4bc7255616e721a5a3e091a39f68041edcc112462235b6f404a`.
- `SOURCE_INPUT_R1.json` SHA-256 `530349352096d2c68b7cdf57728e679333a5b20208d1ec64fb637bb141be7a65`.
- `source/WIRE_R1.md` SHA-256 `1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed`.
- `source/CURRENT_PURE_KERNEL.py` SHA-256 `8e8c374a089a13c4defea9c4b5b548eb1bc1242cddabc1faf468dba420695af4`.
- `source/CURRENT_FLAT_STUDY.java` SHA-256 `0a7e31b20dabd80a33e5dc5886507817cbc138ce96a37eb20ecc54a6e2ff93e0`.
- `source/R_STUDY.java` SHA-256 `88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf`.

These match the inventory entries. Other listed source copies were not opened.

## Result body contract

`CURRENT_PURE_KERNEL.py:2042-2052, 2388-2391` fixes the exact ordered root
keys to:

`schema, mode, status, runId, sourceManifestSha256, wireContractSha256,
inputContextSha256, qualifiedPrior, originalCapture, cohort, arms, work, cleanup`.

The fixed root tags are `tsj-cohort-terminal-result-v2`,
`OBSERVED_COHORT_TERMINAL_TELEMETRY`, and `COMPLETED_DIAGNOSTIC_ONLY`. The
first seven root slots are strings; the remaining six are maps. `runId` uses
the lowercase 36-character UUID-shaped grammar
`[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`. The three
current identity digests are 64 lowercase hex characters. The
`wireContractSha256` field must additionally equal the frozen
`WIRE_CONTRACT_SHA256` (`CURRENT_PURE_KERNEL.py:2041, 2215, 2454`), pinning the
wire interpretation. `runId`, `sourceManifestSha256`, and
`inputContextSha256` remain syntax checks in this codec API; without an
independent runtime identity input it must not claim their authentication or
cross-object binding.

The Python domain at `CURRENT_PURE_KERNEL.py:2083-2084, 2139-2163` is null,
Boolean, signed-64-bit integer, printable ASCII string/key up to 256 characters,
list, and string-keyed map. It rejects other numeric types, arrays, controls,
non-ASCII text, unsupported keys, and over-depth/over-visit input. Depth is 64
(root value at depth zero). Each value node and each map key consumes one visit;
keys are counted at their containing map depth. A bounded traversal rejects
cycles without constructing a second body graph.

The Java ordinary formatter is a useful output precedent, not a complete V2
validator. `R_STUDY.java:2398-2607` and the selected flat copy at
`CURRENT_FLAT_STUDY.java:2452-2660` preserve map iteration order,
write compact ASCII JSON plus LF, restrict strings to printable ASCII and 256
characters, and accept only `Integer`/`Long` as numeric objects. But their
`CanonicalJsonVisits` limit is 1,048,576 **value nodes only** and does not count
map keys. V2 requires 1,000,000 visits including every map key. A V2 traversal
must count keys separately, without treating key visits as output bytes or
copy entries. Under V2's inclusive count, a valid body also satisfies the
ordinary writer's looser value-only ceiling.

The existing `writeCanonicalJsonCanaryValues` path adds the legacy R7
`geometryCanonical` 4096-character exception (`R_STUDY.java:2447-2480`; flat
copy `CURRENT_FLAT_STUDY.java:2500-2533`). It is incompatible with V2's
ordinary 256-character rule. Use only the ordinary path or a single V2
formatter that exactly reproduces it.

## Sink prepayment constraint

The proposed `writeBody(Map<String,Object>, CanonicalJsonSink)` API is
compatible with the selected sink types. `CURRENT_FLAT_STUDY.java:2452-2460`
defines the callback; `R5BodySink/R5CountSink/R5WriteSink/R5CompareSink` at
3031-3160 use `beforeDataOperation` to admit work at the current output
position. The body ceiling remains the sink's 1,048,576-byte limit
(`2792, 3031-3069`). The current flat source keeps both exports and `main`
disabled (`29, 3283-3287`).

There is one pricing incompatibility if the implementation uses a whole-body
prewalk followed by the ordinary writer. While a prewalk leaves sink position
at zero, the real sink only credits output block zero; later input inspections
would happen before their corresponding 4096-byte output blocks were admitted.
Fuse schema/domain checks with byte writing in one traversal in the standalone
V2 formatter. Before each body, collection, key, value, and
string-character inspection, call the same sink's `beforeDataOperation`; in
particular, do this before iterator/`hasNext`/`next`/`get` and before each
`charAt`. Emit that item before advancing to later input so a near-boundary
inspection sees the current block's credit. Root key order/tags and the
key-inclusive visit counter can be checked in that traversal. The same
formatter call must serve count, write, and compare; do not sort maps, build a
second body graph, add a separate full scan, or use a fresh sink for a prewalk.
The 1,000,000 visit counter remains distinct from the sink's byte position.

This fused shape keeps the existing result-export fee at `5C+3`, copies at
`2R+32+M`, chunks at `2C+2`, and one export group, where `R` is result bytes
including LF and `C=ceil(R/4096)`. No duplicate result-byte accounting belongs
in the codec. Identity authority, work semantics, and the equality of identities
across body/marker remain the caller/exporter's responsibility.

## Marker contract and bound

`CURRENT_PURE_KERNEL.py:2046-2047, 2052, 2426-2443` and WIRE R1 fix the exact
ordered 14 keys:

`schema, status, runId, sourceManifestSha256, wireContractSha256,
inputContextSha256, resultSha256, resultBytes, elapsedNanos,
finalExportNanos, totalGroups, totalUnits, maxGroupNanos,
maximumUnitsPerGroup`.

Tags are `tsj-cohort-terminal-marker-v2` and `COMPLETED_DIAGNOSTIC`. The
`runId` grammar is the same lowercase UUID-shaped grammar; the four digest
strings are lowercase 64-hex values, and `wireContractSha256` must equal the
frozen `WIRE_CONTRACT_SHA256`. The codec does not authenticate run, source, or
context identity. A typed 32-byte result digest can be rendered directly as
lowercase hex nibbles without allocating a hex string.
Numeric maxima in key order are:

| Field | Maximum |
|---|---:|
| `resultBytes` | 1,048,576 |
| `elapsedNanos` | 90,000,000,000 |
| `finalExportNanos` | 5,000,000,000 |
| `totalGroups` | 640 |
| `totalUnits` | 9,223,372,036,854,775,807 |
| `maxGroupNanos` | 5,000,000,000 |
| `maximumUnitsPerGroup` | 32,768 |

Preflight field bounds before allocating the output byte array. The pure
`validate_marker_line` accepts nonnegative signed-long numerics alone; the full
binding further requires positive elapsed/export time and binds all fields to
the result/work. Those cross-field relations belong to the exporter, while the
codec applies the field maxima and the planned `resultBytes` range.

The maximum canonical marker is 656 bytes including LF:

- 206 bytes for the quoted key names.
- 355 bytes for fixed quoted values: schema 31, status 22, UUID 38, four
  64-character digests with quotes `4*66`.
- 65 decimal digits for the seven numeric maxima: `7+11+10+3+19+10+5`.
- 30 bytes for braces, 14 colons, 13 commas, and LF.

Thus `206+355+65+30=656`. The outer transport cap 722 is separate. Existing
R and flat marker implementations are legacy contracts: R uses descriptor V1
and bound 722 (`R_STUDY.java:2737, 3140-3178`); the flat R7 marker uses
`fixtureOracleSha256` and bound 666 (`CURRENT_FLAT_STUDY.java:2792,
3194-3230`). Neither schema nor bound is the V2 marker.

## Marker API and copy boundary

The proposed typed `Marker` fields and direct count/write API have no root-shape
or ordering incompatibility. `countMarker` can emit to a bounded integer-only
sink and must not allocate marker bytes, a marker map, key arrays, encoded
strings, or a hex character array. After the caller admits exact `M`, it
allocates one `byte[M]`; `writeMarker` fills it once, enforces `M<=656`, and
checks exact final length/LF without calling a second full count pass.

Borrow the caller-owned digest array and body tree through the passes; do not
clone the 32-byte digest or retain a detached body graph. This preserves the
existing `+32` digest-copy component of `2R+32+M` rather than adding a codec
copy. The exclusive-ownership assumption is required to keep count/write/
compare consistent. A private V2 marker sink is needed, or the legacy sink must
be tightly constrained, because the current selected flat sink's bound is 666.
The old `r5Marker` must not be used for V2 tags. The worksheet must account for
the bounded `Marker` view (four string references, one borrowed digest reference,
seven longs), its construction, and the integer-only count/write sink controls
within the existing single marker-finalization unit. Do not introduce a second
12-field DTO copy or digest clone; either would change the fixed/copy budget.

## Audit result

No API or root-shape incompatibility was found under the plan's borrowed,
exclusive-ownership assumption. Two source-pricing conditions remain: body
domain/schema validation must be interleaved with serialization, and marker
view/counter construction must fit the existing marker-finalization unit. A
separate whole-tree prewalk or added marker copies/units are not covered by the
current position-based sink and export formula. With those conditions frozen
in the source worksheet, the 656-byte proof and direct marker representation
preserve the existing export formula. This report is source-only; current
exports remain disabled and no native or runtime result is claimed.

## Released candidate review

The final source-only review covered `CurrentV2CanonicalCodec.java` SHA-256
`76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522`,
`CODEC_SOURCE_WORKSHEET_R1.md` SHA-256
`9e4a7bdc4ec002edfe1f8d2f69163589570d0534aed125a1d95ee945f6695f34`, and
`CurrentV2CanonicalCodecCanary.java` SHA-256
`485d831f8455476bfffc3e23db1907c1f0fb972766dd265453d161c2244b41bd`.
The final canary includes the `LinkedHashMap` import required by its invalid
map-key fixture. The worksheet explicitly states that marker wire identity
equals the frozen wire hash.
The body formatter uses the frozen root order/tags and exact wire hash, then
performs the key-inclusive visit/depth/cycle checks in its fused traversal.
Sink admissions precede the body/collection/key/value operations and string
character reads. The marker implementation checks its fixed wire hash and
numeric bounds, counts through its bounded integer sink, and writes the
caller-sized array directly. No source blocker was found in these candidates.

The source canary contains independent 1,177-byte C1 and 6,370-byte C2 body
oracles; the latter asserts two units through the actual `R5CountSink`. Its
root admission and descendant-prefix guards cover the count, write, and
repeat/compare traversal shape, while write/compare bytes use `MemorySink`.
The added byte-cap case builds a lazy 300,000-null list, independently checks
that its visit count is below 1,000,000 and a lower bound on its encoded size
exceeds 1 MiB, then asserts that the real count sink stops at byte 1,048,576,
charges 256 blocks, and admits no copy entries/chunks. `DiscardSink` is used
only for the depth and visit-boundary cases; those checks do not establish a
byte cap or Meter behavior. Marker canary source compares the exact 656-byte
maximum line against the independent oracle, checks one final LF/key order,
and exercises below/above numeric bounds and malformed identities/targets.
Its anonymous inner classes use final captured locals and Java-7-compatible
syntax.

These are source assertions and source review only. No canary, compiler,
native gate, full host path, or runtime test was executed in this review.
Actual R5 write/compare sink accounting and V2 export integration remain
outside this standalone codec review; the selected flat exports are still
disabled.
