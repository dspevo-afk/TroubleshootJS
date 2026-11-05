# Earlier branch-tree choice: bounded source audit

Status: source-only findings,2026-11-04 local. No experiment or runtime result.
Parent is `21e990e128b6f374da0cba3bbfd680b951f1b2cf`.

The accepted A_DRIVE length diagnostic excludes only its cost240 copied fixed
prefix. Repeating that same graph cannot satisfy the necessary570/four-transition
guard. A new earlier-choice experiment must start from the original placement;
changing a detached current branch cannot undo previously published copper.

The source-only leaf review identified these existing owners:

- `SeededPcbLayoutGenerator.Session` selects the actual placement and opens the
  P07 ordering stream. It finalizes yielded routes and records candidate outcomes
  (`SeededPcbLayoutGenerator.java:518,647,670`).
- P07 starts each failed ordering from `template.copyForRouting()`, tries at most
  three orders and promotes the previous failed net for role-width boards
  (`PcbLayerRoutingPrototype.java:1139,1240,1953`). Internal pass0/1/2 is zero-based;
  result.orderings counts attempts and the generator stores count-1.
- ROLE_WIDTHS roots at its first sorted pad and always chooses nearest-to-reached
  for each remaining branch (`PcbLayerRoutingPrototype.java:1969,1977`).
- P05 already has a private canonical farthest-to-reached chooser: maximize each
  remaining pad's minimum Manhattan distance to any reached pad; ties keep the
  first remaining pad (`PcbNetRouter.java:312,353`). P07 has no strategy input.

The named historical quality-scalar packet records unadopted cost120 and
width-first net-order trials. The roadmap also records unadopted private
quality-frontier/medoid-root trials. The source audit found no separately named
medoid packet and does not certify exhaustive inspection of every private trial.
Root choice and cost/net order are distinct from the proposed farthest branch
rule; no blanket claim that all alternatives are new is made.

An early copied-owner receipt called its actual100 remainder unrun at that time.
That historical statement is superseded by the later published LOAD12 remainder,
A_DRIVE guard and graph captures. The current roadmap and accepted length packet
govern the present checkpoint. No historical pre-run wording restores a blocker.

The selected first counterfactual keeps default VIA cost60, cap48 and all three
net orders. Only ROLE_WIDTHS branch selection during internal pass1 changes to
farthest-to-reached; passes0/2 stay nearest. This separates one generic choice
from the old cost240 detached replay. A whole-request cost240 study or changing
all three passes is outside this first scope. No default adoption follows.

Implementation source review identified a legacy non-role exception not captured
by the initial summary: legacy-uniform non-RETURN pass2 takes the lexical branch.
R2 clarifies that every non-role path, including this exception, stays unchanged
even when the diagnostic strategy is explicitly selected. The experiment changes
only ROLE_WIDTHS/pass1; no new nearest rule is imposed elsewhere. R1 is preserved
externally, and no test or authored run preceded the clarification.

This review used targeted source and named packet reads only. It ran no parser,
test, JVM, native or browser actor and read no raw capture or protected HARD body.
The root owns the contract, tests, acceptance and publication. See [PLAN.md](PLAN.md).
