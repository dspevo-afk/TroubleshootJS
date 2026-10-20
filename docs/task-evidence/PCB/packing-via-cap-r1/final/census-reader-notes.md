# Paired normal-packing census reader

`compare_census.py` compares the final maintained 44-board census against the
previous accepted census. By default, relative to this `final/` directory, it
reads `../../packing-metric-evidence-r1/final-r2/census-receipt.txt` and
`census-receipt.txt`, then writes `census-comparison.json`. The
paths can be overridden with `--baseline`, `--current`, and `--output`. The
default baseline is pinned to SHA-256
`e7bf2da7239d5d534b273adc399d60b2ba99a306863c1b773264202d81576578`.

The reader requires exactly 44 rows in each of the five emitted row families:
normal packing, whitespace, normal silk, reference ink, and lead/silk. It keeps
the source row order because whitespace rows have no family field; the unchanged
emitter prints each independent whitespace observation before its corresponding
packing row. It pairs those rows by emission order, root, and part count.

Acceptance requires the same ordered family/root corpus, all admissions still
`ACCEPTED` and `FINALIZED`, identical execution and admission identities, and
identical before/after graph fingerprints. The functional component inventory
(IDs, types, names, reference character counts), public label IDs/text/font/bold/
targets/sizes, board title and dimensions, component/terminal inventory, and
public label inventory must remain stable. A new label-to-lead overlap is
rejected. The baseline/current layout epochs are explicitly required to be
18/19. The production protected-void value must agree with the paired
independent sampler's largest-rectangle area.

The four production measurements are gated independently for every row:
outline area, Manhattan copper length, via count, and protected void (`whitespace`)
must each be no greater than baseline. The non-via hole population must remain
exact. The target seed is not one of the 44 census rows; its separate strict GO
assessment is not inferred from this comparison.

Every receipt row pair is represented in the output by raw-row SHA-256 values
and a leaf-level list of all changed fields with before/after values and a
specific reason. Only these differences are admitted:

- Geometry and placement: outline origin/dimensions, geometry hashes, public
  label positions, lead rectangles, and sampler coordinates/domain observations.
  The production outline area and protected-void measurements remain gated.
- Layout epoch and routing: layout algorithm version, trace count, generation
  attempt/work counters, and routing recovery details.
- Runtime observations: elapsed and maximum-advance milliseconds.
- The independent GRID10 sampler's sampled-cell count, occupied/free-cell counts,
  and free fraction. These are reported without a monotonicity gate because the
  sampled domain can change when geometry changes. Its paired largest rectangle
  area must still match the production protected-void value and is covered by
  that production metric gate.
- A reduction in previously reported label-to-lead overlaps; newly introduced
  overlaps are rejected.

No other changed field is masked. Geometry hashes are retained in the per-row
diffs as evidence, but are not used as substitutes for graph, identity, ink, or
metric checks. The receipt exposes graph SHA-256 values, not individual net/pad
ID lists; exact graph-hash equality is the maintained census's available oracle
for those identities. Component IDs and terminal counts are checked directly,
but the census rows do not enumerate physical package identifiers separately.

`--canaries` constructs in-memory fixtures and checks that admission, graph ID,
component ID, public target metadata, and each of the four metric regressions
are rejected while a bounded geometry/metric improvement is accepted. This
reader refuses Python optimized mode (`-O`) and uses explicit checks that remain
active regardless of assertion optimization. This worker did not execute the
reader, canaries, census, tests, build, or browser.
The root runs those checks against the sealed final receipt.

## Consumed-source hashes

Hashes were read from the assigned worktree before writing these reader files;
the sources are unchanged by this task.

| Input | SHA-256 |
| --- | --- |
| `packing-metric-evidence-r1/final-r2/census-receipt.txt` | `e7bf2da7239d5d534b273adc399d60b2ba99a306863c1b773264202d81576578` |
| `tests/contracts/PcbNormalPackingCensusContractTest.java` | `d27dc575ff767da34a3d0eab70755777e65602f72162e634daa23757cf4691e8` |
| `tests/contracts/PcbFunctionalGroupingContractTest.java` | `c9396d0e93b9d042098f9e61efa0fa450e085fff96ae700b9690ebb64091a8d3` |
| `tests/contracts/PcbCanvasBoundaryContractTest.java` | `4aaa2d718ca2845eb0e38a0e6d9bcde5ed37144ea4f3199d795c99242e819aa6` |
| `src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java` | `724e4d6822eaeb3178efdfba30340ff12f0bdb1821734666b1a49df47ca5a8e` |

## Root pre-execution canary correction

The released worker negative fixtures still carried current epoch18 after the
epoch19 guard was added. They would all reject for that unrelated reason. Before
any execution, root preserved the complete raw handoff and established a valid
18→19 frame with a positive compare; every negative now starts from that exact
valid frame and changes only its named condition. No production or native test
input changed. This is a reader-canary fix, not weakened acceptance.
