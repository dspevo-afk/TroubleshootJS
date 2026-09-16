# P09 qualification plan

Base: 5a9e544c41a343f3943211cf83fba85b05cfcd29

Calibrate physical bounds using nine current families at seed 0 and the existing
eleven Q15 regression seeds. Freeze limits before running the held-out structural
seeds: 11, 23, 37, 59, 83, 127, 251, 509, -11, -23, -127, -509.
Do not remove failures or tune limits against that held-out set.

The held-out set is structural routing/geometry/access evidence, not a random
playability population or a replacement for real CircuitJS admission.
Fresh native corpus, adversarial boundary checks, production GWT, compiled Q15
regressions and normal mouse-input checks qualify the enforced small-board gate.
Reuse the frozen P07 54-row strategy comparison only after an exact input audit.
Two-layer and raised-link production adoption may be rejected; do not force it.
The post-P09 arbitrary-seed Quick Play admission gate remains separate.
