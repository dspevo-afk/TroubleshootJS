# Source findings before native export

Luna/MAX read-only explorer `sparse_layout_scope` inspected the accepted e877c2cf
candidate. No source change or runtime claim came from that review.

The two public replay seeds are accepted identities. `PlayerLaunchRequest.parse`
sets candidateSearch=false; `random` selects a new bounded search. The exporter
therefore parses the exact replay, resolves a fresh plan cache and constructs
one owner. Feeding these identities through a New Board root selector would
change their meaning. Both use SMALL_BOARD_EXECUTION@1, including composed with
the MEDIUM display profile. No P07 requirement follows from that display label.

RC's layout factory invokes ProceduralPcbLayout.generate. The composed normal
plan uses BoundedGeneratedBoardAssembler.Context.createPlannedLayout and the
same procedural owner. PhysicalConstructionMaterializer supplies ordinary
THT_SINGLE_FACE constraints with EDGE anchors and20 access margin. Final
exported constraints remain the authority for the two particular instances.

The small one-face session's successful routePlan calls finishRoutedPlan and
PcbPlacementCompactor.compactRouted. Successful alternates also reach that owner.
An unchanged compactStrips result can skip a second route; the optional packing
probe can skip extra routing on NO_CHANGE/no headroom. Thus a smaller screenshot
is not waiting for a missing invocation. Existing compactRouted accepts legal
cuts only while retaining access envelopes, copper, holes and valid geometry.
It can legitimately return unchanged input.

Selected geometry must be observed before choosing another experiment. The
existing whitespace sampler protects package/access, both copper faces, holes,
silk and declared isolation on grid10. Its version argument is an observation
tag; free cells are expressly not packability proof. No alternative raster,
discarded candidate reconstruction, optimizer or production change is used.

Source references: PlayerLaunchRequest30-47; GenerationRequest280-338;
PlayerFamilyCatalog130-140; PhysicalConstructionMaterializer85-115;
RcDelayPcbLayoutFactory13-14; BoundedGeneratedBoardAssembler561-565;
ProceduralPcbLayout13-51; SeededPcbLayoutGenerator182-185,243-275,1031-1092;
PcbPlacementCompactor70-141; PcbNormalPackingCensusContractTest35-79,151-232;
PcbFunctionalGroupingContractTest568-643. Exact consumed source bytes are
already included in baseline-inputs.json's1482-file map, not line-number or
working-tree-name assumptions. No optimality/manufacturing/all-seed claim.
