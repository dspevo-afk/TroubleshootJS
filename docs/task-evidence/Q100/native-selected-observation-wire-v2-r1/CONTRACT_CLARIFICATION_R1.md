# Materialization and fixture admission clarifications

No protocol ID, budget, equation or expected field value changes.

The four fixed materialization inspection/operation charges apply only to
OBSERVE. materializeObservation(null,m) uses existing nullObservation(m), exactly
18entries/onechunk and no extra four charges. Current executeArm and the generic
driver share this same materialization owner for both paths.

Direct put operations in both terminal33 and head10 DTO writers avoid allocating
temporary key/value arrays before copying into the output map. This scope owns
that materializer and its callers. Preserve the terminal DTO's exact fields,
constructor, schema/order/values and validation; change only writer mechanics.
The map-entry precharges33/10 and total79 remain unchanged, now without extra
transient array slots being silently represented as map-only copies. No callback
or routing work changes. Historical D/Q sources and evidence stay immutable.

The pure general observation accounting/domain helper tests the derived
94D+12H/163D+33H bounds with valid relaxed-state variants. The fixed packet
admission remains narrower: exact fixture V91/102/90/0 and all selected blocked
snapshot relaxed flagsfalse/work0. Do not widen a fixed packet profile to pass
a general-domain vector. NULL/QUALITY traversal is empty, FOUND/blocked has the
single declared net. This is general domain versus fixture admission, not a
compatibility or caller-selected validation mode.
