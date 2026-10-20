# Routed slide compaction probe worker notes

Scope is limited to the private `PcbRoutedPassiveFeasibilityCensusContractTest.java`
adapter block, the new `PcbRoutedSlideCompactionContractTest.java` probe, and this
note. The adapter is based on the published feasibility source SHA
`d5fe5319cac1553ac6f4661ac7ee7f67fbf7a83a195c0fdee8015cfd1494f24b` and erases
back to that exact source. The final adapter source is `108637` bytes with SHA
`de4faa26d7a6e034c59c78c9e56a6894b11f0f3e74335fbdd543fc4271f56927`; the new
probe source is `59947` bytes with SHA
`24f8547931dfa74a86270de391c55f4f960f194a7b7fc3df61268d51b32948b6`.

The probe pins protocol SHA
`88123f76e526d42dce97bb656eda8ffdf4479ca947052cf819738e8e79aa93cb` and uses
the selected-slide callback after the accepted route, border trim, and generation
statistics assignment. Seed 5 records the private no-slide control. Seed 2
rederives the canonical 31 selected-frame moves and evaluates each independently
from one captured source. The unchanged `compactRouted` owner is called for the
control and every physically validated row; no route request, chaining, mutation,
or winner selection is added.

The private input is freshly materialized without labels, preserving components,
pads, regions, holes, traces, routing statistics, and recovery statistics. The
returned layout is normalized into another fresh unlabeled owner, its original
routing and recovery statistics are restored and checked, and the existing
silkscreen owner relabels it. Final output proof serializes the protocol's 18
fields after placement, routing geometry, route quality, trace clearance, explicit
two-layer, stable identity, label identity, independent conductor reachability,
trace provenance, and source immutability checks. It does not claim endpoint-only
or unrelated-geometry preservation for compactor output; the embedded feasibility
rows retain their published proof schema.

The five fixture groups are:

1. `selectedPlanFrameAndMetadata`
2. `freshPrivateLayoutAndStableIdentity`
3. `strictImprovementComparator`
4. `selectedObserverOwnershipAndFailureCleanup`
5. `quietWhitespaceOwnerCleanup`

The frame fixture now constructs two literal package-backed footprints and a
translated actual layout, then calls the real selected-slide reframe helper and
checks independent outline, pad-coordinate, metadata, region-membership, and
source-owner expectations. The identity fixture calls the real stable and label
canonicalizers on fresh unlabeled copies, checks signed generation statistics are
absent while routing/recovery statistics survive, and rejects trace-ID and label
target mutations. At worker handoff the quiet-sampler positive used a valid seed5 routed layout;
the root integration correction below replaces that unnecessary routed fixture.

Static checks completed: adapter marker erasure was compared byte-for-byte with the
published source; source and probe use one LF line ending and have no trailing
whitespace; the adapter has one begin/end marker pair; the probe has five fixture
calls and five fixture markers; Java 7-only syntax was retained; output field and
proof field order was checked against the pinned protocol; no endpoint-only proof
serialization is used by the new probe. Native unit, route wrapper, schema reader,
JDK/GWT build, runtime timing, full matrix, and unseen-board gates were **NOT RUN**
in this worker. Root-owned integration and native execution remain the source of
runtime evidence.

Root integration replaced the quiet positive's full seed5 generation with a
package-backed literal one-LED layout. No unit routing is needed for the output
flag boundary. Its envelope106,64,118,171 blocks columns8..21/rows4..22 in a40x30
GRID10 board; the right18x30 free rectangle independently expects54000. The
source generation-duration fixture is positive71ms; signed seeds remain negative.
Worker handoff source+note above remain exact in pre-freeze-source-review-1.
Final root source SHA 85a58e4714b5b5072aee76f74c3ca35f021fd1c3fe8bce9a072f4c9d22316eb8 (60408bytes). No native run yet.

Root native unit attempt1 compiled/passed old40/41assertions but rejected the
new literal fixture constructor: placement interpretation15 was incorrectly
used as retired board layout algorithm15. Both test-only layout constructors
now use current algorithm16; interpretation/schema/move cohort remain15.
Exact46frozen files+failure record retained in native-attempt-1. Corrected root
source SHA aa07c229d5281e5c67d35b4a3bf3b8db28bfb9432917193e96bd7a081d55a9ea (60474bytes); fresh seal/unit required.
