# Routed passive leg census worker note

Scope: read-only endpoint-leg advisory for accepted15 / density14, phase
FINALIZED_ROUTE_CANDIDATE. Base source commit: 66f8d67476288e90fca797e4fb0957c13c550dd8.
Only the assigned private-copy contract test was added. No production source,
routing policy, acceptance rule, fixture helper, or native runner was changed.

The recorder uses the existing successful-route observer and prior-owner
restoration API. It records every callback in callback order, analyzes only
P07_FULLER_TWO_LAYER attempt 2, and includes all placed components in sorted
canonical ID order. Summary/callback/part/pad/incident/direction keys and order,
reason enums, roots, and six-fixture count match protocol.json. A trace index
from the immutable callback layout is retained alongside source ID and net ID,
so repeated source IDs do not collapse separate trace pieces.

For each eligible part, the advisory sums signed Manhattan length changes for
all explicitly incident immediate endpoint legs under each fixed +/-10 axis
shift. A direction is rejected if any terminal lacks explicit incidence, an
endpoint coordinate or net disagrees, a leg is zero, the same trace joins both
own terminals, an unmatched centerline contact exists, or a leg is not parallel
to the shift, collapses, or reverses. Width and layer are recorded as identity
provenance. No geometry is moved, copied, or rerouted; the diagnostic does not
assess pad/land shape, copper clearance, junction/contact, board/access/domain
constraints, routability, manufacturing, or shrinkage. Physical feasibility is
NOT_EVALUATED even when an endpoint direction is structurally eligible.

The six named source-core fixtures are:
- positiveStartEndCollinearIndependentDeltas
- orthogonalAndNonincidentRejection
- collapseReversalZeroLengthAndCoordinateMismatch
- sameTraceAndExtraCenterlineContactRejection
- packageAnchorConnectorAndIsolationEligibility
- deterministicOrderAndReadonlyIdentity

The existing six groups now include literal checks for negative-X -20 and
opposed same-axis zero-sum deltas; missing explicit endpoints without a known
centerline contact; a one-pad terminal-count rejection; null trace source IDs;
trace-net mismatch; a later same-trace return through a matched start pad; and
eligible duplicate source IDs at distinct trace indices with literal +10/-10
incident contributions. The fixture-group count and protocol schema are
unchanged.

Static source review: PASS for protocol field/order alignment, callback
restoration in a finally block, immutable trace snapshots, requested literal
branch assertions, and one final LF with no trailing whitespace. This is not
runtime evidence. Contract fixtures:
NOT RUN. Fixed-root route census for seeds 5 then 2: NOT RUN. Native, JDK8/GWT,
player, and full matrix gates: NOT RUN by leaf assignment. The encoded graph and
per-callback geometry equality assertions have not been observed executing.

No process was launched. No cleanup or erasure was performed; the assigned
private copy remains available for root integration. No staging, commit, push,
or notification was performed.

Private-copy source SHA-256:
6C74D1F473E97F42A24D566CFE852D866F7F9D2D69D37CEB11FF5963825F6E60
