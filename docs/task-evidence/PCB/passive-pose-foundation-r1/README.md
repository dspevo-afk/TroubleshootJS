# Passive pose foundation: native prerequisite PASS

Base `9f29107ccf9c21c84558c71ac9d334760e1a97a0`. This is an isolated
mechanical prerequisite for the continuing PCB packing work. Production remains
unchanged; rotations are not selected by the generator in this probe.

The copied catalog gives four canonical passive descriptors TOP 0/90/180/270
capability: axial resistor, axial diode, radial electrolytic and radial ceramic
capacitor. IDs, terminal order, finite geometry catalogs, defaults, selection,
geometry version and all other package declarations stay unchanged. Standard
typed providers and real replacement parts resolve the shared canonical
descriptors. Package equivalence, typed registry and fit validation remain strict.

The [pre-runtime proposal](proposal.json) fixes the acceptance boundary. Four
maintained native suites cover independent mechanical literals and stock/registry
admission, all fourteen informed source bindings, and exact routed reproductions
for roots 42 and -1022. The reused adapter preserves the original authoritative
physical/routing fixture body. The full fourteen-row accepted baseline is copied
byte for byte; no new golden may replace a failed old-version expectation.

RB56 placement interpretation 15 maps to density 14. The separate general layout
algorithm is already version 16. Preparation corrected a test declaration that
had conflated those epochs before any freeze or runtime. Neither version changes
here. Package capabilities are included in `GenerationDependencyContext` package
canonical values; a capability change must invalidate that identity even if the
actual TOP0 board remains geometrically identical. This probe does not claim
reuse of an old dependency cache.

Commands are run from the established worktree with the maintained bundled
Python and JDK8 paths held only in private local state:

```text
python -X utf8 -B prepare.py
python -X utf8 -B prepare_gates.py
python -X utf8 -B check_final_rows.py
python -X utf8 -B review_source.py
python -X utf8 -B register.py
python -X utf8 -B inputs.py freeze
powershell -File run_native.ps1 -JavaHome <JDK8> -PythonExe <Python>
powershell -File check_process_cleanup.ps1
python -X utf8 -B inputs.py audit
python -X utf8 -B sanitize.py <JDK8>
python -X utf8 -B final_rows.py
python -X utf8 -B retire_copy.py
```

The first native run **FAILed in43.4825193s**: stock capacitor allocation calls
`CircuitElm.drag`, which requires an initialized simulator grid. The pure suite
stopped at that missing fixture prerequisite; the three later suites were NOT
RUN. [Attempt1](attempt1/archive-sha256.json) retains its exact frozen source,
gates, logs, timing, input audit and successful terminal/driver cleanup. Root
added a scoped real `CirSim` with the maintained16-unit grid and finally restored
both prior simulator owners. Production capability source, geometry literals,
baseline, reader and budgets were unchanged.

The final JDK8/native4 run **PASSed in60.2059417s/exit0**, including compilation,
four bounded JVMs and maintained terminal cleanup; cleanup duration was not
separately measured. The pure suite passes999 assertions over28 package/pose
cases and12 hand-computed turned spot checks. Source bindings pass114 wrapper
assertions plus the unchanged shared graph checks for all14 informed roots.
Both current15 routed cases reproduce all42 non-timing fields exactly, including
source/graph/intent, exact signed-long seeds, selected geometry, metrics, rank and
route/work decisions. Native pose projection and stock-slot binding do not prove
live electrical repair or turned-part generated-board admission.

The sealed reader's pre-runtime synthetic checks pass: final rows1positive/
49negative, source bindings1/117, terminal result1/21 and child receipt1/6.
The unchanged reader passes actual final outputs. Frozen1471 inputs derive from
accepted1467 plus three overrides and four new contracts; all archived source
and18 final gate owners stay exact. Root reviewed the integrated diff, literal
geometry and identity boundaries; optional leaf work supplied implementation and
bounded source planning, not an independent runtime certification.

Maintained cleanup and both driver birth-exit checks PASS. Accepted1467 copied
inputs were restored exactly before the contained non-reparse source copy and
private state were removed. Main1475 stays exact. The raw cleanup receipt retains
an inherited wording error (`routed-strip`); the actual frozen prefix guard is
`tsj-passive-pose-`. [Root review](root-review.json) records that label erratum
without rewriting consumed evidence. No test, browser, preview, profile or source
copy remains active. Original five unrelated untracked entries/shared tools stay
preserved.

GWT, player input, solver mutation, removal/lifting, restore and
polarity-marking acceptance are NOT RUN for this retired native probe. Production
is unchanged; earlier native/GWT/player evidence remains scoped to its exact
audited1475 inputs. No packing benefit,
population, manufacturing or general timing claim follows from this foundation.
Any enabled pose policy needs an explicit replay interpretation and independent
random concern or deterministic no-draw choice before production promotion.
The [next-policy source review](next-policy-source-review.md) identifies the
separate density, replay, admission, work and marking owners; it does not establish
a successful placement policy.

Root owns integration, fixtures outside the pure foundation suite, frozen gates,
runtime and acceptance. One leaf owns only the copied catalog and pure fixture.
Original unrelated work and shared tools are preserved; no main merge or email.
