# Exact NPN sparse-board diagnosis

**Read-only diagnosis PASS; no board-size improvement or production change.**

Base `ce6f3fef9ac86b3157f0ecac08ab53350022134d`, branch
`codex/q60-faulted-profile-counts`. The exact accepted ordinary replay is
`tsj-alpha/6/EASY/NPN_LOW_SIDE_SWITCH/380469407799690561`. Its original actual
production [screenshot](../to92-molded-detail-r2/npn-generated-fitted.jpg) and
public identity are hash-bound in the baseline. This packet adds no player run
or screenshot. The task exporter constructs once from that public replay,
without random candidate selection or substituting the developer fixed layout.

| Metric | Selected board | Existing P05 proposal |
| --- | ---: | ---: |
| Outline area | 628530 | 628530 |
| Copper Manhattan length | 4300 | 3730 |
| Vias | 0 | 0 |
| Largest protected empty rectangle | 80000 | 80000 |

The proposal reduces copper by13.26% but has no area improvement. Packing3's
four strict predicates therefore retain the incumbent correctly. No rejected
proposal geometry was reconstructed. Selected outline is1022x615 drawing
units, with7 parts,15 pads,6 nets,9 traces and no holes. The largest protected
empty rectangle is at(740,40),320x250. Free cells do not establish packability.

Final complete access envelopes put the left/right extremes at J1x70/J2x1032,
with30 units to each board edge. LED1's envelope starts aty84; RPD reachesy625,
with44/30 units to the board's top/bottom. The complete-envelope projections
leave only two20-unit x gaps and one19-unit y gap before any copper guard,
pad/hole/silk or legal-cut checks. Guarded copper bounding spans arex151..951
andy135..605. These selected-board observations explain why a large corner
void is not itself a full-width/full-height removable strip. They do not prove
that relocation cannot shrink the board. `envelope-analysis.json` is bound to
the raw receipt and its small descriptive helper.

The optional source leaf confirmed the ordinary procedural bottom-face THT,
SMALL_BOARD_EXECUTION@1, layout19/packing3, standard20-unit access margins and
two edge anchors. A second leaf owned only the new task Java/notes: changes are
limited to comment and exact NPN argument pins; construction, validators,
exports and nested disposal remain the accepted sparse observer's code. Root
reviewed those actual diffs and hashes. Both leaves ran no gates or browser.

Root commands/results:

- `inputs.py freeze-baseline` before adaptation; `freeze-export` before execution.
  All1,482 application/contract and24 machinery inputs remain exact, along with
  13 published artifacts, two baseline helpers and eight export artifacts.
- `prepare_export.py`: six guarded private verifier-adapter changes, five from
  the accepted observer plus NPN-only family binding. The native PowerShell
  parser accepted the actual adapter and two wrappers. This is a task adapter,
  not an unchanged maintained-suite invocation.
- `run_export.ps1 -Phase canaries`: actualexit0,0.7274123s. Real invalid-seed and
  invalid-family binding each exit1 before JDK/compiler/native work. Raw stderr
  stays private/hash-bound. This does not test every Java argument rejection.
- `run_export.ps1 -Phase npn`: actual JDK8/native PASS161, actualexit0,
  43.0031078s. All maintained Java definitions compile; selected task class runs.
- Each actual tool completion preceded separate `record_exit.ps1` observations:
  all four recorded driver births exited. Phase times include maintained
  terminal cleanup; cleanup is not separately timed. Root driver observations
  have their own elapsed values. No preview/browser/listener was launched.
- `qualify.py`: PASS exact public identity, admission/access/geometry, unchanged
  selected geometry/logical declarations, canonical endpoint/net inventories,
  independently recomputed four metrics and correct packing disposition. Four
  fresh single-condition negatives reject the expected specific assertion.
- `analyze_envelopes.py`: PASS descriptive bounds/projections only. Native log
  is public raw-exact, with a second private raw copy; no path projection needed.

Source/input identity is audited before and after both phases. No production,
maintained test/script, RNG, seed transport, physical allowance, selection
predicate, routing/search cap or epoch changed. Previous source/build/player
evidence remains applicable across unchanged consumed inputs; this packet does
not rerun GWT, electrical/player flow or the full matrix. Logical graph hashes
do not fingerprint transient CircuitJS solver state. No optimum, manufacturing,
performance or all-seed claim. Four earlier blocked profile removals and seven
unrelated worktree groups remain untouched. No main merge or email.

Next is a source-backed, bounded relocation hypothesis using these measured
envelopes and strict unchanged metrics; no planner experiment is selected here.
