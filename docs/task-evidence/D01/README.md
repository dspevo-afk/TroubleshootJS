# D01 diagnostic partitions and context-valid proof reuse

Date: 2026-09-22

## Delivered boundary

D01 adds a complete immutable diagnostic context key, a value-only proof cache,
and canonical full-population static executable partition plans. Serial proof
remains the authoritative admission oracle: a cold proof enumerates every
retained hypothesis and its repair/retest witness. The partition plan is a
value-only projection over that retained evidence; it validates the declared
program and canonical evidence routes without reopening a graph, meter, solver,
controller or owner. It cannot omit a hypothesis or certify a sampled population.

Warm reuse never restores a solver or graph. It revalidates the current structural
owner and creates a fresh owner/controller-bound receipt from immutable evidence.
Any source, model, declared temporal cache recipe, physical, diagnostic-program
or repair-catalog change changes the captured context key and prevents reuse. The
coordinator separately captures the raw temporal dependency of the live candidate
and rejects a change before proof or publication.

The cold receipt retains its full serial HYPOTHESES work count; a warm receipt
truthfully records one cache-reuse unit. Those operation receipts are therefore
not conflated. The qualification compares the complete retained proof value
(context, partition, repair semantics and exact samples) while independently
requiring the cold/warm work and solver-provenance difference.

The RC temporal proof keeps its healthy reference readings bit-exact in the
evidence receipt. Those readings are solved outputs, not declared cache inputs:
fresh equivalent owners key reuse on the complete temporal recipe/model/cache
epoch instead of a bucketed sample value. The raw temporal dependency remains a
separate live-candidate revalidation boundary, so a mutated reference still
rejects publication and old cache keys cannot collide.

## Coverage

`D01DiagnosticContractTest` rejects hash-only keys, sampled/truncated/foreign
population evidence, non-monotonic or unreachable routes, unsafe repair merging,
untrusted cache keys and foreign receipts. It validates E04 direct and hysteretic
seed contexts, their complete ordered three-locus population and finite-rail
source context invalidation. It also proves that an E04 variable sensor-source
request changes the captured immutable context. Focused native result:
**PASS — 167 assertions**.

The compiled A10 generation verifier runs actual cold and warm proof/cache paths
through `GenerationCoordinator`; the independent `a10_generation_report.py`
reader checks RB15, the larger controlled-indicator fixture and E04 direct/
hysteretic rows for complete evidence, repair witnesses, cache provenance,
bounded work and stable cold/warm values. The final [compiled browser report](a10-generation-report.json)
is **PASS**: 3,104 assertions, 24 frozen normal-corpus rows and eight D01
cold/warm rows. Normal rows explicitly carry `NORMAL_FROZEN_ATTEMPT` and the
unchanged 5,000 ms ceiling; the separate larger D01 fixtures explicitly carry
`D01_JOB_BUDGET` and the 90,000 ms whole-job ceiling. The fresh single-tab
controlled-indicator seed-2 cold normal attempt is 4,320 ms; its HYPOTHESES
stage records 2,824 ms serial reference plus 18 ms static-plan overhead, and
its warm receipt performs zero solver solves. The slowest D01-job fixture is
RB15 seed 0 at 14,241 ms, inside its stated 90,000 ms budget. The independent
`a10_generation_report.py` reader passes on the captured report. Earlier failed timing probes are retained in
[pre-optimization-failures.json](pre-optimization-failures.json); they document
the unchanged ceiling rather than becoming a new budget.

The rebuilt compiled Quick Play matrix also passes all 30 menu cases (three
instances of each of the ten current catalog families), two exact replay and
power-isolation cases, public-metadata absence, and owned-process cleanup. Its
compact persistent receipt is [quickplay-procedural-matrix.json](quickplay-procedural-matrix.json).
That post-build run caught an unintended public-difficulty effect from using the
partition's maximum route depth as a reading count: catalogued easy parallel
boards were rejected as medium. The fix keeps the static partition as the
execution-bound certificate while retaining the established diagnostic-reduction
reading count for difficulty classification; the permanent U05 canary and the
final full contract suite pass with that boundary.

## Limits

No shared mutable solver state is cached. `staticPlanOverheadMs` is the bounded
HYPOTHESES-stage residual after the serial reference, not a standalone cache
lookup timing or a universal performance claim. Wall-clock values are bounded
and reported, while correctness is based on complete evidence and cache
provenance.
