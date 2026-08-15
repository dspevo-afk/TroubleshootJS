# Task 19: Family-agnostic generated-board state

`GeneratedBoardInstance` no longer owns LED/R1/resistor repair state. Its generic
family-state reference now points to `LedIndicatorFamilyState`, which owns the R1
slot, resistor inventory/catalog, and physical catalog serial allocation. Generic
simulation-element ownership remains on the board instance.

The R1 controller and LED-only UI, probe, validator, and verifier code obtain
state through `LedIndicatorFamilyState.require(instance)`, which fails explicitly
for an unrelated generated family state.

Validation: explicit JDK 8 production build completed five GWT permutations and
linked successfully. The normal player URL remains available at
http://127.0.0.1:8888/circuitjs.html?tsjChallenge=led&seed=3.

Files changed: generated-board state boundary, LED generator/controller/UI/probe/
validator/verifier callers, architecture documentation, and this report.

No new component family was added. The recommended next task is a separately
scoped family implementation using its own `GeneratedBoardFamilyState`.
