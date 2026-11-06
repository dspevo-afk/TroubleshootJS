# Routing parity and measured capture work

The frozen plan requires unchanged routing semantics and explicitly charged
capture work. CONTROL uses the prior strict whole-operation comparison, with
only HEADER runId/sourceManifestSha256 and GROUP elapsedBeforeOwnLineNanos
normalized. MISSING_TARGET also requires that strict control parity.

CAPTURE first runs the unchanged operation validator on all actual records,
including GROUP timing, owner steps, callback/serialization accounting, limits,
receipt closure and final cleanup bindings. Its parity projection then compares
all non-GROUP records in order, removing only their group coordinates, FOOTER
groupCount and these four RESULT logicalWork scheduling counters:
generatorAdvances, p07Slices, p07OwnerSteps and routingOwnerSteps.

HEADER arm flags remain exact, including legacy graphCapture=false: this means
there are no Publisher GRAPH records in operation1. The separate sidecar carries
the observed graph. Every candidate/PAD/NET_ORDER/BRANCH/DIAGNOSTIC_CLONE field,
full branch snapshot, geometry hash/length, routing outcome/receipt/context,
winner/physical/boundary field, clone count and expansion count remains exact.

Both complete logical-work ledgers and group counts are retained in the result.
The additional P07 owner steps must equal the actual snapshot captureWorkUnits.
Observed values are3005975 versus3006586, delta611; groups101 versus254.
This is no claim of identical scheduling, free graph work or increased limits.
Literal controls reject changed net order, pad net, arm flag, branch snapshot
and boolean substitutions in selection counts. Actual CLI controls also reject
wrong lineage/placement/source, broken chunks/EOF and failed cleanup evidence.
