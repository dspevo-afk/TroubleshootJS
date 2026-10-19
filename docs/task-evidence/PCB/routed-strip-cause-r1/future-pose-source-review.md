# Separate passive quarter-turn feasibility review

Read-only source review at0031b8a5 by the optional leaf explorer; no files, tests,
processes or active observer copy changed. This is future-work context, not the
current routed-strip diagnostic's acceptance or evidence that rotation compacts
generated boards.

The existing `PcbPhysicalTransform` and `PhysicalPackageGeometry.placedAt` project
points, bounds, dimensions, pads, probes and escape vectors through quarter turns.
`PcbFootprint` retains terminal order and pad IDs. Standard axial resistor/diode
and radial capacitor catalogs declare only top/zero poses; standard providers
construct those poses, and `PcbComponentPlacement` rejects undeclared ones. Simply
widening the catalog neither changes the provider's chosen pose nor adds a planner
pose search. Any pose-choice randomness must be independent of the existing shared
provider random stream to preserve geometry-variant draws.

The slot/mutation scene carries the installed pose through lead projection,
lifting/removal/replacement. That source path is not proof that all polarized
markings render correctly: resistor bands infer horizontal/vertical while diode
and electrolytic providers use specialized offsets. Production-package fixtures
and actual visible repair/render verification are required before enabling new poses.

Catalog widening changes source identity and `PhysicalPackage.isEquivalentTo`.
The current physical-board fingerprint records the realized rotation, and replay
capture records layout/geometry versions without the whole allowed-pose catalog.
An explicit placement/model interpretation must own a future pose policy; the
package geometry contract version alone is not that policy. The exact version
owner and effect on current consumers need a scoped decision before edits.

The smallest prerequisite is independent standard resistor/diode/electrolytic/
ceramic90/270 pad/net/escape fixtures, polarized marking and lift/removal/replacement/
restore checks, and deterministic repeat/replay proof under that explicit epoch.
Only after those boundaries pass should a fixed-seed placement experiment compare
packing under unchanged candidate/routing/physical/time limits. No implementation,
version migration, all-pose proof or compactness claim is accepted here.

Source owners reviewed: `PcbPhysicalTransform.java:12`,
`PhysicalPackageGeometry.java:259`, `PcbFootprint.java:59`,
`PhysicalPackages.java:20`, `PhysicalPackage.java:263/309`,
`PcbComponentPlacement.java:43`, `PcbPlacementPlanner.java:105/114`,
`PhysicalBoardSlot.java:76`, `WorkbenchPhysicalSceneSource.java:63`,
`StandardPhysicalPartRenderProviders.java:334/391/636`,
`PhysicalBoardFingerprint.java:37`, `A03RealizationReplay.java:742`.
