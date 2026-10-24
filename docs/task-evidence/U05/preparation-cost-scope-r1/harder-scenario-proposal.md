# Conditional harder-scenario research; NOT STARTED

Read-only leaf source review on `fc88419b`, while the owner chooses between
qualifying current content as MEDIUM and developing harder content. No edits,
builds, electrical experiments or protected-regression reads were delegated.
This proposal does not authorize a release/profile change or establish difficulty.

The smallest candidate found is an `RDB_HIGH_RESISTANCE` fault on two-channel
plans while retaining `KB_COIL_OPEN`. The existing RDB is a1kΩ resistor from
B_CMD to B_DRIVE. Reusing the current1GΩ selected-resistor fault model would
introduce another repair owner with existing circuit elements. It could create
B-output ambiguity between the drive resistor and coil. Common-rail coupling and
the full provider's OUTPUT_B sample may defeat that hypothesis immediately.

The explicit easy tree in this packet reads A-channel RDA and U1.OUTPUT, not
RDB. Invalidating that particular tree would be insufficient: the complete legal
observation set could still contain another two-reading strategy. Extra witness
steps, rails, packages or a mandatory authored tool sequence cannot supply a
lower bound. Player tools must remain free to exploit the real graph.

Potential owners, if this direction is selected:

- `Rb56Plan` declares the extra scenario/model interpretation; `Rb56Generator`
  applies the existing resistor fault to the selected RDB instance.
- `Rb56DiagnosticProvider` adds the correct repair catalog mapping and physical
  observations/endpoints; `Rb56PhysicalAssembly` retains its generic selected-
  resistor service path. No replacement solver or scenario-specific reading.
- `Rb56QualificationVerifier` and `Rb56FaultPopulationContractTest` currently
  expect five scenarios. A justified new population must migrate those contracts
  without weakening independent expectations or changing fixed old receipts.
- `Rb56InstrumentAdmissionContractTest` must prove B-pad identity/power/reference
  rules; U05 reduction/privacy contracts need equivalent-repair, one-probe and
  tolerance falsifiers. `DifficultyAssessment` currently reduces a declared set
  and does not classify HARD.

A possible finite experiment would predeclare exactly three two-channel plans,
one per SEPARATE_DIRECT, SHARED_DIRECT and SHARED_HYSTERETIC reference arrangement,
with seeds fixed before execution and a separate source/epoch binding. Prove
healthy operation, all six actual faults, physical replacement and customer retest.
Export the entire declared legal observation/mode set under real power, settling
and comparison rules. Compare `DiagnosticReduction` with an independent adaptive
decision-tree oracle. Require every legal two-reading tree within that set to
fail before claiming a three-reading lower bound there. Preserve90s/640/5s caps
and honest time/work failures; new faults may worsen the existing near-cap cases.

That is a proposed experiment, not current acceptance. Declared-set exhaustion
still does not prove a global arbitrary-tool/probe lower bound or human HARD
difficulty. If another two-reading tree survives, reject this candidate as a HARD
justification. Protected hold-out selection/execution stays unopened until a
defensible rule and development experiment have been separately frozen.
