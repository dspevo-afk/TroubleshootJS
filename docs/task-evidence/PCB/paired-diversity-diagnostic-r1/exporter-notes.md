# Paired-packing LED diversity diagnostic

`export-source/SparseNormalBoardDiagnostic.java` is copied from the frozen
`tests/contracts/QuickPlayPhysicalMatrixContractTest.java` source. It retains
the catalog assertions, normal generation and exact replay paths, root sequence,
20 accepted-board target, 48-root bound, rejection handling, physical and
novelty uniqueness checks, and every original placement, route, copper, macro,
and outline diversity assertion. The selection is restricted to the registered
`LED_INDICATOR` family.

The diagnostic accepts exactly
`--family LED_INDICATOR --seed 0`. Here `--seed 0` is a canonical carrier for
the LED batch starting ordinal; it is not a replay seed and is not passed to a
specialized generator. The original root function derives every actual signed
seed from successive zero-based ordinals.

Before the original physical-duplicate assertion, each board whose exact replay
matches emits one `PCB_PAIR_DIVERSITY_DIAGNOSTIC` row. The row carries the root
ordinal and canonical signed seed, any earlier seed with the same physical
fingerprint, full physical and novelty canonical fingerprints, the existing
normalized pose string and exact outline, and the selected packing receipt with
its recorded metric evidence. These fields come from public physical geometry
and existing receipt data; the observation does not alter the family, seed,
generation, replay, or assertions. The original duplicate check remains in
place, so a repeated realization still fails at that assertion.

`make_diagnostic.py` verifies its frozen source inputs and the supplied paired-
pocket adapter, then writes the current adapter, no-hook adapter, private
generator copy, diffs, and manifest only to the caller-supplied empty output
directory. The no-hook copy removes only the two paired-compactor call lines;
the current adapter compiles the checked-in WIP source. No compiler or native
execution is claimed here. These files are task-only developer diagnostics, not
normal-player UI or a production behavior change.
