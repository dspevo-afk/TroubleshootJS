# Search predicate correction in progress

The original acceptance bounds remain fixed. Current12 is not accepted.

The first wider-width trial added a foreign-pad reservation expanded by the
trace half-width plus six units. That also changed SIGNAL9 searches, although
their physical width stayed9. The new routing fixture originally asserted this
additional search restriction. Those source-trial results and receipts remain.

An independent audit established the existing physical contracts: trace pairs
use their width-dependent six-unit edge gap; via lands retain six-unit
clearances; foreign trace-to-pad contact is rejected by the conductor builder's
actual closed-touch predicate. There is no generic six-unit trace-to-pad gap
in the established final layout validator. These are separate owners and rules.
`contract-audit.json` records the boundary; it does not establish the cause of
every lost route.

The bounded correction aligns the new role-profile pad search predicate with
actual width-bearing copper contact, including segment endpoints. It does not
remove pad checks or relax trace-pair, via, courtyard, isolation, access or
search/work/time limits. Uniform9 searches keep their original path.

The original new SIGNAL9-only rejection assertion is being replaced with an
independent physical-width test: a width13 step has literal stroke
`(214,214,23,13)` and touches the foreign26-unit land at `(237,227)`; it must be
rejected. Moving that land by one unit in each axis clears the actual stroke.
The width9 version has a two-unit gap and must remain allowed in either profile.
Both directions and endpoint contact are checked. This corrects an unintended
new test policy; all published area, void, saved-geometry and physical boundary
oracles remain unchanged.

The retained diagnostic also proves that simply widening saved11 paths is
invalid: both old layouts develop real courtyard/contact violations. New
production paths must therefore be rerouted and pass all full physical checks.
The exact wider courtyard goal-boundary correction passed focused tests but
did not resolve the two cohort regressions. No old path or diagnostic-completion
PASS is accepted as evidence of a valid current12 board.

Fresh focused tests, all six representatives, four untuned roots, saved-layout
guards and final production build/player checks are still required.
