# Q30 scale design audit — not implemented or qualified

Performance remains the first hard gate. Current qualified structures still
count 33/35/37 packages; this audit supplies no scale PASS and changes no recipe,
fault catalog, corpus, deadline or work allowance. Read-only Luna/MAX review
inspected the accepted 7a30 source; no tests or builds were run for this design.

A scalable discrete family can use the existing package/model vocabulary with
this count formula:

`8 common packages + 10 per sensor/output channel + reference parts + support`

The eight common packages are J1, F1, DREV, U1, CIN, C5, REN and JLOAD. Each
channel contributes its E04 decision element, sensor resistor and pull-down,
sensor connector, five relay/driver packages and output connector. Separate
direct references need two resistors per channel. A shared direct reference
would use two resistors total; that arrangement is not yet qualified. C12 adds
one, status adds two, and sensor filtering adds one capacitor per channel.

| Design target | Purposeful construction | Qualification still needed |
| --- | --- | --- |
| 20–21 | One complete channel, separate reference, C12 optional | Full single-channel intent, diagnosis, physical mapping and cap-omission proof |
| 30–31 | Two channels, shared direct reference, C12 optional | New reference topology and loading/partial-power proof |
| 33/35/37 | Existing two-channel recipes | Preserve existing behavior and all five hypotheses; timing remains failed |
| 40 | Existing 37-part filtered/status recipe, two real output LED/resistor pairs, omit C12 | Indicator loading/state behavior, service and routing, cap-omission proof |

The single-channel variant can retain five meaningful roles with fault owners
DREV, REN, its A sensor resistor, A drive resistor and KA relay coil. That is a
new declared variant catalog; the existing two-channel plans must retain all
their existing owners and hypotheses. No absent B-channel test may silently be
treated as passing. Both distinct control/load returns and the main/isolated
load supplies remain. Removing C12 needs explicit electrical and residual-energy
coverage: its current upstream net is declared to require storage observation.

Three channels can structurally count to 40 with a shared direct reference and
no C12, but are not a viable shortcut under the current proof program. Adding
only three C-channel hypotheses to the existing five costs 624 proof units at
78 each; the 14 other units leave only two of the frozen 640 units for routing.
A full per-channel catalog has still more hypotheses and input combinations.
Do not drop hypotheses, observations or raise the work cap to fit that design.

The smaller implementation path uses current discrete models and purposeful
support options instead of new integrated relay modules or connector packages.
It still requires explicit population/support axes in Rb30Plan, construction
and catalog changes in Rb30Generator, variant-aware topology/behavior/diagnostic
and power-domain owners, and independent plan/corpus/service verification.
Supporting indicators can follow the existing status-indicator scope, with
their real loading proved; any newly advertised indicator fault must enter the
catalog and budget calculation. Generic StagedFamilyCapability,
GenerationRequest and GenerationCoordinator need not acquire family branches.

This is not complete 20–40 coverage. Intermediate package counts, legitimate
optional subsystem combinations, representative/held-out seeds, every physical
admission and full applicable electrical proof still need implementation and
qualification after the primary timing bottleneck is controlled. No handcrafted
per-integer recipes or inert padding are proposed.
