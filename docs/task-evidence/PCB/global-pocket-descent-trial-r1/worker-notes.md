# Global pocket-descent worker note

COPY-only source for base `a3a0b851c1e5b80212b1d604a3962a8e77437893`. This worker owns six production sources in `src/com/lushprojects/circuitjs1/client/` and `tests/contracts/PcbGlobalPocketDescentContractTest.java`; it does not alter the main checkout or root-owned corpus/registry/harness files.

The trial adds private placement interpretation 22. It keeps interpretation 15 on density 14, general algorithm 16 unchanged, and versions 16–21 unsupported. Interpretation 22 selects density 15 and retains ROLE_WIDTHS. The existing density-15 trim/canvas branches now admit the trial version.

After the existing legal medium candidate-2 plan, `relaxMediumGlobalPockets` makes one canonical component-ID pass over only AXIAL_RESISTOR, AXIAL_DIODE, RADIAL_ELECTROLYTIC_CAPACITOR, and RADIAL_CERAMIC_CAPACITOR packages. It excludes anchored, connector, and isolation-body footprints. Each proposed rigid translation uses the existing vacancy domain, target generator, and medium grid snap; charges each distinct nonincumbent snapped proposal to the shared evaluation count; applies existing `fits`; and is retained only on a strict decrease of the unchanged whole-plan `PlacementScore.total` followed by unchanged full placement/access validation. Ties keep the incumbent. The optional pass exits at the existing 60,000 cap and leaves the current legal plan selected. No source pose, outline, inventory, random stream, route request, score weight, or candidate count changes.

The package-private `scorePlan(board, plan)` adapter is placed immediately before the existing private scorer, requires both inputs, and delegates directly to that scorer. The initial greedy medium placement score is unchanged.

The contract unit has 11 named fixtures:
`private22IdentityAndDensity`, `rawRepresentativeCandidateParity`, `fourPartNoLinkDescent`, `linkedPassiveDescent`, `eligibilityAndVersionGuards`, `isolationPackageExclusion`, `budget59999StopsAtCap`, `budget60000ReturnsImmediately`, `equalGlobalScoreRetainsIncumbent`, `accessEnvelopeNegativeBoundary`, and `scoreAdapterRejectsMissingInputs`. The no-link and linked fixtures compare against literal independent whole-score expectations and assert rigid identity-preserving movement. Raw parity covers roots -1017, 42, and 4; candidates 0, 1, 3, 4, and 5 are exact, while candidate 2 is bounded and globally non-increasing with a deterministic replay check. The access negative is explicitly a hypothetical validator-boundary canary: the lower-scored colliding proposal is rejected as ENVELOPE_ACCESS_OR_DOMAIN, but this fixture does not claim the helper generated or attempted that origin. A separate owned cost contract covers the score detailed MST/role/corridor/connector terms.

Static source review only. This worker did not compile or execute the unit, run native suites, or run the JDK8/GWT build. The required marker below is expected output, not observed output. Root owns review and fresh execution.

Expected marker: `PASS: PCB global pocket descent contracts assertions=N fixtures=11 evaluator=globalPocketDescent`.

Final COPY source SHA-256 values:

- `src/com/lushprojects/circuitjs1/client/Rb56Placement.java` — `DC6487D3285BD3E350C0F2B7F68172EA9262AE232B4F5B1F675988624C71EDD6`
- `src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java` — `5534541B3D218BDE55A76CC092D478850A6A8C01DB07C9BB5645F993D77FCC62`
- `src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java` — `FA205F0EAC1E6D67A7A43DA4A0ECF247000409ABBD6F9C1FCCB53B46999CBE3F`
- `src/com/lushprojects/circuitjs1/client/MediumBoardPhysicalPolicy.java` — `7F613A9F8C1DD8E23BBEC79A6678ADE3E43A468DAB01FE7D4580C5DE80CD6845`
- `src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java` — `0DFD1890ECEDF90350237152CAEE42CABFA7ABAE412386250FA74B9E09098CF6`
- `src/com/lushprojects/circuitjs1/client/PlayerSessionReplay.java` — `53FDA383AE1EDA0A295E9268C4E8DD1D1A1465C07DCCDE3AF00C72F6A11CBAF5`
- `tests/contracts/PcbGlobalPocketDescentContractTest.java` — `84D7BFA2686513CD38950A4655FE7509DCFC2A54E31BCD5AE4768B6F12A3B2D9`

Root pre-freeze review made the anonymous-captured fixture final for Java7, retained strict successful raw-plan parity rather than accepting paired rejection, added private22 exact signed-long boundary checks and raw before/after graph checks, and removed two routed-layout validations from unrouted placement fixtures. Full planner/access validation remains; actual routed physical checks belong to the unchanged paired corpus. These are preparation fixes/stronger identity checks, not runtime PASS claims. Named scenarios stay11; the raw scenario includes three fixed roots. Native remains NOT RUN.

Root restored the exact existing tight-candidate early return before freeze; candidate2 is non-tight and the new guard follows that return unchanged. No other candidate control flow is altered.
