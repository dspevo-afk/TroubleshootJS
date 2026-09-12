# A11-R1 renderer contract repair

The owner supplied an independent PASS review of published A11 commit `0487555`,
with this bounded repair requested during U01/P03/P04. A11 was not restarted.

Construction bootstrap now checks explicit renderer registration and full
`PhysicalPackage` equivalence. It does not ask a part-aware provider to render a
nonexistent part. `PhysicalPartRenderRegistry.requireRenderer` checks an actual
part's package and rejects a missing provider or renderer with a clear error.
The workbench validates every materialized part before attachment/publication;
installed and tray dispatch use the same guard. An empty slot has no installed
part geometry. Developer surface fixtures use an explicit fixed-renderer factory.
No dummy part, individual provider branch or alternate NMOS behavior was added.

Native conformance passes 429 assertions and 20 negative cases. A provider that
throws on a null part registers without being called. Missing registries, partial
package registration and a same-ID inequivalent package still reject. Focused
A04 construction (680), P01 poses (8,357) and U01 viewport (1,233) assertions pass,
as does the independent A11 reader (125). The final production JDK8/GWT build
passes all five permutations: 82.499 s compilation and 1.622 s linking.

The compiled Task49 route exercises the same admission constructor using actual
materialized parts. Its separate renderer report records successful part-aware
dispatch, rejection of a null renderer before workbench construction completes,
missing registration and wrong-package rejection, and unchanged live ownership.
PASS: [15 actual materialized parts](compiled-renderer-report.json), zero null-part
calls and every required rejection/ownership check. The fresh Task49 report passes
397 assertions and four real solver cases; its maintained strict reader, nine-seed
value and six-seed role oracles pass. Compiled A11 declarations pass 429/20 and
the independent reader passes 125 assertions.

Fresh P01 checks pass 316 runtime assertions across 16 SMD poses; P02 preserves
exact copper correspondence on LED, diode-protected and parallel boards. U01
passes 1,237 assertions/six live targets and the actual 100-part draw. Forced failure
and normal-player privacy pass. A final real LED seed3 diagnosis, 1 kOhm catalog
replacement and successful customer retest also pass after R1.

Per the owner's instruction, the complete matrix is not repeated after R1.
[Reuse audit](reuse-audit.json) identifies every changed input and the boundary
of the prior U01/P03/P04 generation, diagnostic, operator and timing evidence.
It does not claim a final-R1 A10 or Task41 rerun.

**A11-D1 remains nonblocking coverage debt.** Physical NMOS parameters are
derived from the validated electrical declaration rather than independently
compared by the physical backing validator. No broad refactor was performed.
