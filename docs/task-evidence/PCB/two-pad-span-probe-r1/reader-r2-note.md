# Corrected stage-outline reader

The first actual post-control reader exited1 after its original row/sampler/cause
checks passed. It incorrectly required all four outlines to be equal. Existing
PcbPlacementCompactor.compactAxis copies the origin and subtracts grid10 strip
width/height; planner single/pair moves preserve the input outline. Root inspected
those actual owners. SOURCE1530x1040 contracted to1520x1040 at ORDINARY and stayed
there; this is documented compaction, not a changed production result.

R2 requires stable candidate/seeds, identical origin, positive nonincreasing
width/height in multiples of10 through SOURCE→ORDINARY→PAIRED. FINAL must still
exactly match PAIRED or, when any cap is violated, SOURCE geometry with PAIRED
counted evaluations. A new valid-hash outline-expansion negative tests the revised
boundary. All initial assertions, native sources, frozen R1 helper bytes and
receipts remain preserved. The control evidence is reused only with exact sealed
native inputs and receipt hashes. R2 itself is sealed before variant execution.
No native rerun or old reader PASS is claimed.
