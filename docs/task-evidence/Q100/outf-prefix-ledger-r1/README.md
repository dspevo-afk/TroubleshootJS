# OUT_F saturated-prefix ledger audit

Accepted derived source/data audit; publication pending. [Frozen scope](PLAN.md),
[complete typed ledger](ledger.json), [preceding native graph qualification](../outf-graph-r1/README.md).
Commands, hashes, source review and explicit gate limits are in [validation](VALIDATION.md).
This reuses the accepted native packet; no production or diagnostic producer
code, protocol, epoch, budget, geometry or routing policy changed.

## Typed prefix

The exact prefix contains48 holes, all `VIA`, across11 other nets. OUT_F has
zero holes available for the existing-hole same-net lookup. These are actual
typed native rows, not an inference from hole IDs or aggregate counts.

| Net | VIA holes |
|---|---:|
| RAIL5 |11|
| CTRL_RETURN |9|
| LOAD12 |7|
| D_CMD |5|
| D_SENSE |5|
| A_SENSE |3|
| MON_SENSE |3|
| A_DRIVE |2|
| AUX_GATE |1|
| HV_POS |1|
| HV_RETURN |1|
| Total |48|

Every current-net pad is plated through-hole, TOP mounted and EXPOSED. Native
PcbCopperAccess therefore supplies copper on both faces; exposure is a separate
probe policy. Only CSTORE_F.2 is reached at this branch. DF.A and JOF.2 are not
automatically current goals merely because they belong to OUT_F.

| Pad | x | y | Branch role |
|---|---:|---:|---|
| CSTORE_F.2 |2720|820|Root/reached terminal|
| DF.A |1950|260|Unreached|
| JOF.2 |2320|1930|Unreached|
| RSH_F.1 |2960|740|Selected start|

All246 native pad IDs/coordinates join the PAD census. Attachment, mounting,
exposure, escape and bounds come directly from the source-owned native geometry
fingerprint. The complete48-row hole ledger includes kind/net/coordinates/drill/
land/exposure. No new native exporter was needed: the existing sealed PUB
document already carried this information.

## Current source guard

FULLER permits four transitions and at most48 total holes. The constructor
allows a request to lower that maximum. The actual selected maximum is not a
separate exported scalar here; its source upper bound of48 is sufficient.
For every possible transition count t0..3, `48+t+1` is49..52 and exceeds any
allowed maximum. `pathViaFits` runs before `viaFits`, so no transition reaches
the later same-net reuse/new-via geometry check at this prefix.

The later lookup checks exact coordinate and net identity, with no explicit hole-kind
test. All actual prefix rows here are VIA. A PTH pad is not a
`PcbBoardHole` row. Initial both-face seeding and reached-PTH goals are separate
from an intermediate layer flip; new-via geometry also checks pad clearances.
Do not treat a hypothetical physical PTH route or a relaxed via mask as an
admitted production transition.

The preceding independently checked ordinary graph has one TOP goal unreachable
and one BOTTOM goal at shortest length1700 versus limit960. Source review confirms
the capture uses the production ordinary-step predicate and the actual branch
goals. Dropping direction, turn cost and history makes its shortest length a
lower bound on production paths. With every transition blocked, that bound
excludes a length-eligible path for this exact fixed branch. It proves no
whole-board impossibility and does not
qualify a changed prefix, another ordering, hypothetical reuse or a playable100
layout. A subsequent routing experiment must separately prove its legal path,
publication, finalization, physical correspondence and customer behavior.

## Evidence and reuse boundary

Candidate root9007199254740993, placement-6587958367974952701,
routing8445301674744508279, request0/attempt4/pass1/ordinal248/vector28/group57.
The unchanged prefix has180 traces,48 holes,271606UTF8 bytes and SHA
cbc98315295693be7cddc31d0327f6f89e5819fedb2b4ad1d65a25e7decc2739.
Approved native receipt2067050B/f9d0b6ea;196 PUB chunks close557331B/dc18ace2.
The existing source-pinned v6 reader recomputes its complete five-arm report
exactly, including geometry, source/transport, BFS/Dijkstra and original limits.
The native geometry fingerprint is independently rebuilt from the exact typed
rows. Two pad membership scans and two hole/net counts reconcile; input and
source bytes close unchanged. Derivation17.9894465s, not a native generation or
cleanup duration. Full commands and source/data hashes are retained in external
QL=OS-temp task evidence/outf-prefix-ledger-r1 and its parent graph packet.

No new native/GWT/Player/electrical/normal3/full30 run is claimed. No source
implementation changed, so documentation-only validation reuses passing evidence
after auditing every consumed input. All original limits, independent oracles,
earlier failures and uncertain E06 resources remain preserved. HARD/PSYCHOTIC
unavailable/unrun; protected bodies unread/unrun here/historical unread
UNCERTIFIED; full30 owner-excluded. Playable100=0; beta held.

The preliminary proposal's positive placement sign and pending graph-acceptance
wording are historical transcription/status errors, corrected by the exact
native context and publication record. Its uncertainty about available typed
rows is resolved by decoding the approved sealed packet; no source was invented.

Next unstarted milestone: one separately frozen generic earlier-prefix or
endpoint-order routing experiment grounded in this ledger, with actual legal
publication/physical/runtime proof before adoption. No treatment is selected here.
