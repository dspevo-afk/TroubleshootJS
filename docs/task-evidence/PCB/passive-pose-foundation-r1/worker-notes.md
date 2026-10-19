# Passive pose foundation worker notes

Status: implementation prepared; native compile and execution are **NOT RUN** by
this worker. Root owns integrated gates and runtime evidence.

The isolated native source copy changes only the `AXIAL_RESISTOR`, `AXIAL_DIODE`,
`RADIAL_ELECTROLYTIC_CAPACITOR`, and `RADIAL_CERAMIC_CAPACITOR` declarations.
Those declarations now share the existing cardinal-rotation list through a
narrow package constructor and retain TOP-only mounting sides. IDs, ordered
terminals, package-internal connections, finite geometry variants, canonical
geometry objects, variant selection, defaults, and seeded draw behavior remain
as before. The generic cardinal list was not widened; other packages retain
their prior declarations.

`PcbPassivePoseFoundationContractTest` contains seven independent geometry
fixtures: three resistor spans, two diode spans, and the two radial defaults. It
checks all four top poses (28 package/pose cases, including 21 turned cases),
with independent local geometry tables and matrix-based placed expectations.
Twelve additional literal spot checks cover 90/180/270 for one non-square
resistor, one non-square diode, and each radial default. The contract also
covers standard typed providers remaining TOP0, paired seeded variant draws,
stable component/pad/terminal/net identities, pad and probe bounds, connected
and lifted lead geometry, copper escape vectors, stock part slot admission, and
negative bottom, foreign geometry/descriptor, old same-ID TOP0 descriptor,
fuse, connector, and isolation-package cases. Its success line reports the
actual assertion count; that count is intentionally **not reported as passed**
until the root runs it.

The dependency identity fixture invokes the real private
`GenerationDependencyContext.appendPackage` by reflection with paired package
inputs, then compares package-local realization and TOP0 placement fingerprints.
It scopes the assertion to the rotation capability included in package
canonicalization; it does not prove whole-cache behavior. The standard provider
fixture stays at TOP0, and the current supported envelope remains TOP0-only, so
these tests do not establish generated-board admission at turned poses.

No measurements, solver behavior, rotation selection, packing benefit, lift or
replacement lifecycle, replay migration, or player-flow completion is claimed.
The copied Java source and new contract have not been compiled or run here; root
must perform the owned native gates and resolve any source/API compatibility
issues before accepting results.
