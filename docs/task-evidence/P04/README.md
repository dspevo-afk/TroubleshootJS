# P04 routing qualification

Candidate identity and final-source gates are shared with
[U01](../U01/candidate-provenance.json). The current route score version is
`unique-copper-tree-v1`.

`PcbNetRouter` routes typed net priorities with two bounded root candidates,
explicit endpoint escape reservations and successive branches onto existing
same-net copper. Every branch retains its source identity and physical contact.
The Manhattan heuristic uses the actual minimum edge cost of 2. This guarantees
a lower bound for each search, not a globally optimal tree.

PASS: 6,408 native routing assertions, six real tree branches and nine accepted
LED/diode/parallel cases at seeds 0, 3 and -17. The
[matched route corpus](routing-corpus.json) records raw/canonical segments,
expansions, length, unique length, reuse, bends, congestion rejections and cost.
Raw paths have 176–373 segments; canonical paths have 15–31. Independent unit
rasters and exact conductor graphs agree before/after canonicalization, including
source, terminal, layer, escape and branch-contact identities.

Negatives remove a real branch, attempt a courtyard shortcut, duplicate source
copper, triple-count overlap, introduce a zero-length segment and infer a rail
from a `GND` string. An independent weighted Dijkstra oracle checks the selected
router's actual distance field with costs 2/7/10 and walls; it detects the former
overestimate. A10's 80-attempt negative now uses genuinely incompatible distinct-net
escape clearance; the old outward-edge case belongs to P03's placement rejection.

The compiled generation benchmark exposed repeated geometry copying inside the
search loop: LED seed 0 took 6,816 ms against the unchanged 5,000 ms ceiling.
Each routing attempt now snapshots its immutable pad, courtyard and escape data
once. [Exact comparison](routing-stability.json) preserves all 18 accepted/rejected
outcomes, geometry/conductor digests, expansions and congestion counts across the
nine cases. [Earlier timings](routing-before-optimization.json) remain available;
the route corpus records the final native timings. No search budget, clearance,
candidate order or benchmark ceiling changed. The subsequent complete compiled
[A10 report](generation-report.json) passed 1,273 assertions across 24 attempts;
LED seed0 first admission fell to 1,042 ms. RC admission/repeat remained
49,318/48,952 ms, with maximum active units 3,824/3,711 ms. The 90-second job,
640-unit work and five-second active-unit/benchmark limits are unchanged.
Earlier failed attempts remain in the three `a10-*-failure.json` receipts.

## Compiled and player evidence

The completed U01/P03/P04 candidate passed 27 native suites, compiled A10 and
Task41/A09, with maintained strict readers. After A11-R1, the final JDK8/GWT build,
affected native checks, Task49/A11, independent value/role oracles, U01/P01/P02
and privacy/negative checks passed freshly. The owner waived a complete matrix
repeat after R1; [the input audit](../A11-R1/reuse-audit.json) states the boundary.
The real player canary was repeated after R1 using generated LED seed 3: failed customer retest,
unpowered R1 measured at 100 kΩ, visible brown/black/red/gold markings identifying
1 kΩ, removal, catalog 1 kΩ replacement, power on and successful customer retest.
Earlier ordinary left/right clicks and final accessible terminal/probe controls
agree. [Player receipt](player-flow.json) distinguishes the captures; both
screenshots below are from the fresh final-R1 run.

[Unrepaired state](unrepaired.png), [repaired state](repaired.png).
No injected controller calls serve as player-input evidence. The tray retains the
removed part separately from board zoom/pan. Current authored controlled/RC/NPN/
NMOS layout factories remain authored; generic placement/routing currently serves
LED, diode-protected and parallel indicator leaves. Larger routed content,
congestion recovery, vias and planes remain later milestones. See the current
task checkpoint for final regression results, retained failures and cleanup.
