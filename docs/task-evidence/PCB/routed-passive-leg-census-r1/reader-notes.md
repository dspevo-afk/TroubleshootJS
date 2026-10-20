# Routed passive leg census reader notes

The bounded offline reader is assess.py. It pins the accepted15 protocol,
the three published successful-route-pool inputs, both one-row suite manifests,
and the pure support functions from
global-pocket-descent-trial-r1/assess.py. It reads unit or routes phase
text, child receipt, timing, and suite inputs; rejects duplicate or unexpected
JSON keys; preserves closed field order; and refuses to overwrite its phase
artifacts. The routes reader consumes exactly seed5 and seed2 at version15.

For each root it compares the whitespace row and all 42 selected-case fields
except the bounded elapsedMillis field with the published route corpus. It
requires the selected live graph hash for both summary graph anchors, requires
same-callback geometry hashes to remain equal, and matches every observed
successful callback to the ordered SUCCESS entries in statisticsCanonical.
Target filtering is fixed to P07_FULLER_TWO_LAYER attempt2. Target parts are
canonical component, pad, incident, and direction order; non-target callbacks
carry no parts. The reader recomputes structural direction reasons and signed
endpoint-leg Manhattan deltas from the reported pad and incident facts,
including net, endpoint, zero-length, same-trace, parallel, collapse, and
reverse checks. Aggregates are recomputed and require four direction results
per reported target part.

Package eligibility and centerline contact are source-owned facts because the
receipt does not carry placement constraint flags or complete trace paths. The
reader checks their enum, package, flag/count, and downstream consistency
without claiming independent package or whole-trace contact proof. It accepts
a null traceSourceId only as the declared missing-source fact and rejects
empty source IDs. Physical feasibility, clearance, access, domain, junction,
routability, manufacturing, geometry movement, shrinkage, fitness, and policy
remain NOT_EVALUATED.

check_assessment.py ran 42 in-memory cases: 12 positive reader cases and 30
rejected mutations. Nine literal reader fixtures exercise signed copper
deltas (-20, 0, and +20), null source, missing endpoint, net mismatch, extra
centerline contact, same-trace terminals, collapse, and reverse reporting;
each is passed through _validate_part and checked for its exact reasons and
deltas. The Java contract fixtureCount marker remains six. The rejected corpus
covers duplicate and unexpected keys, strict boolean typing, foreign graph
anchors, selected 42-field drift, callback sequence and target filtering,
package eligibility including target-package inconsistency, incident facts,
same-trace identity, parallel/collapse/reverse/delta checks, contact
consistency, aggregates, timing, suite marker order, receipt mismatch, stale
unit prerequisite, and protocol/input pins. Results and consumed hashes are in
assessment-canary-results.json.

No native process, JVM, GWT build, browser, or production phase was run by
this leaf. The unit and fixed-root routes gates remain for the root workflow;
this reader evidence does not certify their execution.
