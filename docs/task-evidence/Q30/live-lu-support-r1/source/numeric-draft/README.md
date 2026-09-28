# Live structural LU numeric contract

tests/contracts/LuStructuralFactorizationContractTest.java is a source-only
contract fixture for the private nonlinear-owned LU entry point and
LuStructuralSupport. It compares supported factorization, pivots, and solves
against the unchanged full-scan LU path using raw double bits. It also uses the
independent LuFactorizationChecks Crout oracle for selected dense cases and
checks analytic solutions with a tolerance and residual.

The fixture covers interleaved balanced support components, later-row tie
selection, row-object swaps, singular-block zero-pivot fallback and sentinel
behavior, initial zero rows, signed zero and underflow, finite overflow
failures, dynamic and cancelled stamp coordinates, certificate invalidation,
wrong mappings and arrays, aliased rows, workspace cleanup/reuse, and a small
deterministic corpus of independently row/column-permuted blocks.

This worker did not run the fixture. Integration and execution belong to the
root task. The prepared source baseline used for API review is
scratch/pivot-lower-fusion-r2-preparation/candidate; only this scratch output
directory received writes.
