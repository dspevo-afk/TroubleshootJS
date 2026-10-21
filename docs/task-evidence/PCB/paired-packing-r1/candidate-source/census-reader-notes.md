# Paired packing census reader

`compare_census.py` is a pure-Python reader for the maintained final normal-player
census. It accepts `--baseline`, `--current`, and `--output` paths; defaults are
the pinned `to92-molded-detail-r2/census-receipt.txt`, this folder's
`census-receipt.txt`, and this folder's `census-comparison.json`. Run
`python compare_census.py --canaries` for only the self-contained reader controls.
The normal command runs those controls before writing its report. It must run
without Python's `-O` option.

The baseline receipt is pinned to SHA-256
`e6194601188b138d55fa27eff4fd4e2bfd98a73ca64ed3316527a0ced9992c88`. The
reader pins the census emitters, independent metric implementation, packing
receipt, geometry and physical fingerprint implementations, and current packing
owners. It also checks source declarations for connected-pocket packing version
4, layout epoch 19, and receipt identity `normal-packing@3`. The receipt schema
remains `normal-packing@3`; the layout epoch is 19 in both baseline and current
rows.

Pinned source SHA-256 values:

| Source | SHA-256 |
| --- | --- |
| `tests/contracts/PcbNormalPackingCensusContractTest.java` | `d27dc575ff767da34a3d0eab70755777e65602f72162e634daa23757cf4691e8` |
| `tests/contracts/PcbFunctionalGroupingContractTest.java` | `c9396d0e93b9d042098f9e61efa0fa450e085fff96ae700b9690ebb64091a8d3` |
| `tests/contracts/PcbCanvasBoundaryContractTest.java` | `4aaa2d718ca2845eb0e38a0e6d9bcde5ed37144ea4f3199d795c99242e819aa6` |
| `src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java` | `724e4d6822eaeb3178efdfbaa30340ff12f0bdb1821734666b1a49df47ca5a8e` |
| `src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java` | `c7d2f17a3e4b2a892beae7856b5d00dce9af66f7d50d2c3f802ec74d8116cf68` |
| `src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java` | `5a3ea41332c85ff1ea263fd3952963fda1c845e9d9359460abcc271fdcd4d180` |
| `src/com/lushprojects/circuitjs1/client/PhysicalBoardFingerprint.java` | `e6b8a1bca49ff913e614171c65bc86df9f6a5a39ed28cb44744b8e88cf9daf06` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java` | `9dcf4917692f32071574ef5adf8f75aa55ef0e6a9a3159d3011549be2b4fbda3` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java` | `1144f0575cbbae802db890100a460fda9a8541c79459629c06769a4ee9d6fceb` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java` | `df561459c15fcd85840672971b1244bc019e92f58cfef4f81dc663d14f99f1b1` |
| `src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java` | `7f27c83d12f47579a7fea50b496c2f0320e833e412f0aebe82594a62c7184750` |

The reader requires all 44 accepted/finalized cases in source order: eleven
active families by roots `0, 17, 13, -9223372036854775808`. Each case must keep
its five observations in emitter order: independent whitespace, normal packing,
public title, public references, and lead/silkscreen. It rejects missing,
duplicate, reordered, rejected, or extra rows and verifies all eight suite
summaries account for the same 44 admissions.

Every case retains the exact execution policy, physical admission, logical and
placed counts, layout epoch, and before/after live-graph fingerprint. That
fingerprint's pinned source covers board, component, package, terminal, pad, net,
routing-role, and power-input identities; it does not include physical positions
or the layout epoch. Component IDs/types/names, public reference text, label IDs,
styles, target-pad IDs, counts, and row declarations are compared with strict
types and exact values. The visible lead inventory must retain terminal order
and rectangle sizes; each component's leads may only undergo one common rigid
translation. The reader recomputes the emitted lead/ink intersection list and
rejects any new overlap.

Four production measurements are checked independently for every case against
the pinned baseline: outline area, copper Manhattan length, via count, and
protected empty-rectangle area. Each must be nonworse. Outline area must equal
width times height, and the non-via hole inventory must remain exact. The
independent 10-unit sampler is also checked for grid dimensions/cap, occupied
and free cell bounds, free-fraction arithmetic, rectangle alignment/bounds,
rectangle area, and equality between its largest rectangle and the production
protected-void row. Sampler free-cell counts/fractions may change and are
reported; they are not promoted into extra regression gates or treated as proof
that a region is packable.

The explicit projection records every changed field and a reason in the JSON
report. Permitted changes are measured geometry/position, copper and route
work, derived sampler observations, and elapsed-time fields. Changed full
geometry digests are retained in row hashes and reported; a digest delta also
requires a separately visible non-clock geometry/work delta. Layout epoch,
admission, graph identity, metadata, public ink, and exact inventory are never
masked. Hashes are supporting evidence rather than a replacement for the
exposed identity and geometry checks.

`--canaries` includes passing clock and translated-geometry/improved-copper
controls, plus negative controls for admission, graph/component/pad identity,
public text, each of the four metric regressions, missing/reordered cases, new
lead/silkscreen collisions, and lead-shape changes. Canary PASS only verifies
the reader's projection and rejection paths; it does not certify production.

The maintained census source calls `layout.validateGeometry(board)` before
emitting rows. Its pinned implementation validates connected and lifted lead
probe bounds, routing geometry, title/label clearance, and route quality. The
reader does not rerun that Java validation. Receipts do not serialize all pad
and probe rectangles or routed trace vertices, so the reader cannot reconstruct
those coordinates or independently recompute complete geometry fingerprints.
The actual final-source native census and its source-pinned validation remain
required. This 44-case comparison makes no all-seed claim and does not establish
a global packing optimum; the separate exact target regression is outside this
corpus.
