# Read-only predicate-stage source audit

Accepted parent831d69a41d9504bfb845caef6942e9ef58c9d6d2. Leaf
next_after_via_price inspected only explicit PcbNetRouter.java and
PcbLayerRoutingPrototype.java, with no edits, execution or protected-data access.
Root selected the native-only passive-callback overlay; no implementation yet.

PcbNetRouter.java exposes the two permitsLayerStep overloads at line1070: world
from/to coordinates, net, start/end pads, optionally reachedTerminals. After bounds
checking, the supplied end is tried first; then reachedTerminals in supplied order,
skipping IDs equal to start/end, with early success. P07 supplies reached PTH pads
sorted by ID. Private permitsLayerStepToEndpoint takes grid x/y/nx/ny/direction,
net and start/end pads. Its original short-circuit order is isLegalMove,
canTraverse, canOccupy. The latter's congestion/conflict observations cannot
identify which earlier predicate rejected a zero-delta query.

No rejection-stage observer exists. P07 FixedPrefixGraphObserver selects a lineage
and receives a completed graph; it does not observe edge predicate stages.
Reflection can inspect counters/grid but replaying the private predicates would
repeat effects and duplicate the endpoint loop outside Router. The frozen native
overlay will emit one passive terminal event from actual endpoint checks instead.

There is no persistent detached-copy discriminator. The constructor
Router(Router source, boolean observationShell) uses observationShell as an
argument guard only. beginFixedGraphObservationCopy returns FixedGraphCopyTask;
its constructor creates new Router(source,true). fixedGraphMutationRevision is
copied and state comparison proves equality, not the copy's role. P07's capture
owner separately owns pristineFaces and queryFaces.

For the native overlay attachment guard, the helper's actual live/pristine/query
identity arrays are the role authority. A setter may require this==expectedQuery,
this!=live, this!=pristine, nonnull distinct source/pristine references, and prior
completed copy/pristine comparison in the helper. Equal router state alone is
not permission to attach. Detach in finally and verify observers remain null on
live/pristine owners and after query exit/error. Do not invent duplicate physical
state or expose a Player observer API.

Relevant source locations: PcbNetRouter.java lines790/810/849/1070/1156/1177/
1619/1664; PcbLayerRoutingPrototype.java lines89/2507/2656/2726. Source bytes
are bound by the frozen1084-row baseline. This audit is not runtime proof, route
feasibility, physical finalization, electrical qualification or adoption.
