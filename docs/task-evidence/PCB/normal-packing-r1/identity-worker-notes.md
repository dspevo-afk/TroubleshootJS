# Normal packing identity reader

This leaf change adds the explicit `PcbPlacementConstraints.withConnectedPocketPacking()` value-copy API. Legacy constructors remain packing interpretation `1`; the normal shared path opts into `PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION == 2`. The copy preserves declared parts and barriers, routing face, physical policy identity, density interpretation, two-layer requirement, and trace-width profile. Repeating the opt-in returns the same already-selected packing-2 instance without allocating another copy.

`ProceduralPcbLayout` preserves every physical property while converting a top declaration to the bottom routing face and verifies that an already-bottom shared normal declaration arrived with packing 2. The Rb15, sensor-control, controlled-indicator, and Rb30 normal providers explicitly opt into packing 2 before crossing that bottom-face boundary. RB56 remains on the legacy default-1 interpretation. `GenerationDependencyContext` emits `placement.packing=2` only for the nondefault interpretation so default-1 canonical bytes retain their existing body. The public replay epoch is `tsj-alpha/6`; alpha/5, unknown epochs, malformed seeds, and noncanonical signed-long text remain rejected before installation.

The focused `PcbNormalPackingIdentityContractTest` covers immutable copy/preservation, repeated-opt-in identity, top-to-bottom conversion, explicit rejection of an already-bottom legacy-1 declaration, shared-normal and private-RB56 provider identities, current layout version 17, current public epoch parsing, signed `Long.MIN_VALUE`/`Long.MAX_VALUE`, stale epoch, unknown epoch, plus-sign, leading-zero, and overflow rejection. The existing quick-play/session identity consumers and independent quick-play evidence use epoch 6 for current positive fixtures and retain an explicit alpha/5 negative.

Static validation completed for this handoff:

- Java source syntax and scoped references were inspected; no native run, GWT build, browser run, staging, commit, or push was performed by this leaf.
- The new contract is intended for the root test registry and remains bounded to identity/admission behavior; it does not claim physical packing gain, route success, cache publication, or screenshots.
- The bottom-face immutable set-once boundary means a caller that creates a bottom declaration must opt into packing 2 before entering `ProceduralPcbLayout.declare`; the adapter fails closed if that owner boundary is violated.

Root follow-up gates remain the focused compiled contract, normal-family physical matrix, cache/session identity checks, final JDK8/GWT build, and visible New Board flow. Existing unowned browser fixtures still need the root's epoch-6 migration if they are part of the selected gate.

Handoff SHA-256 pins (working-tree bytes):

```text
PcbPlacementConstraints.java 09820735d1d380cc05e0010829370c5466a18c3446d3f7c233575a2a3fe3c896
ProceduralPcbLayout.java da9a5512b3a47e22951e6e1dc2b45e432c65c5e5f01b5c177d73a7d016d4e308
Rb15Plan.java 5aae021a178c25537ea89f8e32c539866f161b124b34f2e0d1b34ecb716b3fe2
SensorControlGenerator.java dd4093dbea1e7abb92930220d6770dbc93826a256924615e56dc06cac14221c2
PhysicalConstructionMaterializer.java 4f2215713a1b810396bf79beee3684ec60785cdf43214f79810b2f3161fb037f
Rb30Plan.java 6fc8a68043cc99d0dffe2d3dfce10de2bddaed0c8b15b2b0a398956bf13f3eda
GenerationDependencyContext.java d17b9e705454c5068b1a061d4bfc84a2b198477ae5c7a0dc7fabb61ce0a4f47c
PlayerLaunchRequest.java 84a687f7767bc06a4b1cade8f3747c70928e698d6797e56088e4eb15a1e673dd
U06SessionDeveloperVerifier.java 4d3785da473502a777d78cf238c2de68f0b2b4bd7dcee4bc040d165ce421bc01
PcbNormalPackingIdentityContractTest.java b4e8ba24f0f96cdc6adf0dc240ee3b1090ffa4abab3430ce2cbcd25e41937ecd
```
