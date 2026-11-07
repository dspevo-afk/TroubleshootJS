# Focused native canary output contract

Frozen before the P8 literal canary implementation. This is not the unfinished
BASE sidecar schema. Read API_SCHEMA_R1.md and focused host R1+R2 first.

Public native entry is package com.lushprojects.circuitjs1.client,
CanonicalGeometricReasonCanary.main(String[] args), requiring zero arguments.
Success prints exactly one UTF-8 JSON line after prefix
P8_GEOMETRIC_REASON_CANARY_COMPLETE followed by one space, exact LF. The JSON
has exact keys schema,status,assertions,caseCount,workNanos,groups,
maximumGroupNanos,totalObservationUnits,cases,layoutQualification.
schema is tsj-q100-geometric-reason-canary-v1; status PASS; assertions/caseCount
positive exact ints; workNanos/max/group/unit values are nonnegative exact ints.
layoutQualification is false. Each case object has exact keys id,status,scope,
expectedSite,actualSites,fineEvents,coarseEvents,sourcePreserved,
observersDetached,referencesDropped. id unique; status PASS; scope is
SYNTHETIC_ROUTER_LITERAL, INVALID_RULES_INPUT, RULES_BOUNDARY_LITERAL, or
OBSERVER_LIFECYCLE. expectedSite is nullable string, actualSites is string list,
event counts nonnegative; proof flags boolean true after cleanup, never defaults
substituted for a missing check. Allowed/no-fine controls use null expectedSite.

Case count equals cases length. Assertions count actual evaluated checks, not
estimated coverage. Normal null and observe queries run on separately compared
copies and retain result/counter/effect parity. Fine-before-coarse/first-owner,
roles/defaultnull/copy/detach/sentinel tests remain actual runtime checks.
Full final-source coverage must include every ten site family or record an
explicit unresolved gap; a preliminary subset cannot qualify P8 as complete.
New source-derived fixture refinements must be frozen in a literal addendum
before their code; no coordinate shifting merely to turn a test green.

Measure complete construction/query/metadata/cleanup units, including opaque
calls, with <=5s each, sum groups<=640, work<=90s, combined observation units
<=32768. Copy/comparison cursors use <=4096 entries/chunk and <=1048576 entries
per clone; incomplete/equal-false copies cannot attach. Callback timing nested
in query measurement is overlapping diagnostic time, not extra scheduling units.
Exception cases retain exact throwable identity and verify all cleanup first.

The final output/exit guard is a reserved measured group: after emitting/flushing
the single line, check that whole output group<=5s and total elapsed<=90s; fail
nonzero on breach. Its reserved unit/group is included in reported counts; its
actual duration is source-guarded after output and covered by full native process
wall/cleanup upper bound. Reported maximumGroupNanos covers completed pre-output
groups; the consumer must preserve this limit of the scalar rather than claim it
contains a duration measured only after its own serialization. Any post-marker
failure/nonzero exit makes the canary FAIL. No stderr on successful execution.

No P8 canonical BASE run, candidate result, route benefit, electrical renewal,
physical feasibility or Player behavior is established. Keep protected HARD/
PSYCHOTIC/full30 data unread/unrun and production source untouched.
