# Rejected/no-op orientation assessment

Base d00c4f3ef1cf8e7f4530b04af80a3213cc4d8854,2026-10-14.
Trial1's test used Rb56Plan.resolve(seed) without the explicit current version,
so it measured legacy1 against published9 area. Its FAIL is retained and is not
an accepted-current regression. Trial2 corrected the explicit version and passed
all six exact published9 geometries with one180-degree optocoupler each.

That is a no-op: StandardPcbFootprintProviders.OptocouplerProvider already emits
TOP/DEG_180, independently checked by Rb56GeometryContractTest. The advisory
orientation helper was discarded; before-planner.java.txt was restored byte-exact,
its temporary contract retained as trial-contract.java.txt, and the temporary
suite registration removed. No rotation catalog, replay or package changed.
Both native scratch cleanups passed. Raw failures/success remain.

Read-only Luna MAX downstream audit: actual pose transforms feed rendering,
pad/lead/loose hit mapping, terminal-name replacement and physical save fingerprints.
Chip pinout is canonical and already says board orientation may differ. Axial
packages currently declare onlyTOP/0; expanding quarter-turn poses would require
explicit catalog/provider/admission scope and matching lifecycle/routing checks.
The auditor changed no files and ran no tests. This assessment is not final-code
native/GWT/UI evidence. The next useful iteration is paired-net support placement.
