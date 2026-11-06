# Frozen independent geometry-row proof R1

FROZEN before helper JSON producer completion and any P5 native execution.
Original PLAN6128ea85 and PROTOCOLd03ab20b remain preserved. This closes the
independent trace-census proof; it changes no physical predicate, cap or oracle.

Add mandatory `prefixGeometryRows` to both inspector payload schemas (normal and
expected exception). It has exactly `traces` and `holes`. Copy these actual
prefix objects before probing, without repeating the252676-byte static prefix:

- trace row: [sourceId,netId,startPadId,endPadId,layer(0/1),exposure,width,xs,ys]
- hole row: [holeId,kind,netId,x,y,drill,land,exposure]

Use the existing canonical Java string ordering. Source IDs remain distinct
from net IDs; nullable source IDs follow the existing serializer contract.
No geometry parser or replacement physics is introduced. The independent reader
builds the existing oldbranchreader `_geometry_rows` input with these rows plus
actual P4 facts.rawPlacementFingerprint as staticPrefix, prefixGeometrySha256,
and prefixGeometryFingerprintUtf8Bytes. That helper must reconstruct the exact
accepted265894-byte prefix fingerprint/hash. The pristine placement is captured
before any traces/holes are added; that source boundary is unchanged.

`prefixTraceNetCounts` contains exactly the actual board's sorted net census,
including zero counts. Independent counts are Counter(row[1] for row in traces),
with zero for known nets absent from trace rows. Derive all48 hole kinds/counts
from structured rows and match P4 facts. This proves a net's association/count
among prefix trace objects; it does not assign a conflict cell to a unique trace
or prove a rerouting intervention works.

Add `geometryRowEntries` and `geometryRowUnits` to totals. Count entries as
sum(9+len(xs)+len(ys) per trace) +8*holeCount; enforce<=1048576. Geometry copying
uses at most4096 entries per checked unit; units are ceil(entries/4096), or0
when empty. Include those units in the existing32768 observation-unit cap and
actual ADVANCE/group timing. Grid `copyEntries` remains the sum of face-copy
counts, separate from geometry-row entries. Source/copy references are dropped
on both normal and deliberate-exception paths.

Root writes prefix-blockers.json with exactly one JSON object and final LF,
including that byte within the existing65536-byte cap. The helper returns bare
JSON object strings. The expected-exception marker's full line, including marker,
embedded inspector JSON and LF, must also fit65536 bytes before emission. Native
aggregate and shared stdout/stderr caps remain8MiB; failure is not a cap increase.

Source review only estimates the added trace/hole body from the13218-byte
fingerprint increment. Actual serialization must be measured and rejected if
the complete payload exceeds its fixed bound; no size PASS is assumed here.

## R2: actual mixed-clearance representation

FROZEN before final helper/reader completion and any P5 native execution.
This effective R2 retains the R1 geometry proof above. Original R1 SHA
`d5ea742f3c2be1faa167c991965a5932d227df054537e921f3533ca8ce70263a`
is preserved separately. The effective addendum pin now refers to this R2 file.

PcbNetRouter.reserve writes the empty string into clearanceNet when different
net halos overlap. occupiedNet stores actual nonempty net IDs. An empty clearance
string is the MIXED marker; null still means no clearance owner. Preserve the
actual empty string, do not normalize it to null or invent a board/trace owner.

Correct PROTOCOL_R1's target-owner rule: targetOccupiedNet is null or a known
board net ID; targetClearanceNet is null, the empty MIXED string, or a known board
net ID. Positive congestion delta requires a foreign occupied net, a foreign
clearance net, or the MIXED marker. Only real neighboring occupied-net IDs belong
in conflicts/census/trace associations; the marker never becomes a net entry.
The foreign-neighbor histogram times congestion-delta arithmetic is unchanged.
A marker does not independently identify every owner of a shared halo.

Add independent shared-halo A/AB positive and own-net-only negative controls.
Reject unknown nonempty net IDs and preserve the distinction between null and
empty string. No physical predicate, graph, route policy, bound or search changes.
