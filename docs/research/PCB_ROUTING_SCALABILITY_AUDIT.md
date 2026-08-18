# TroubleshootJS PCB Routing & Layout Scalability Audit

Audit scope: committed repository baseline `ef77856` (`master` /
`origin/master`) and the code reachable from it. The uncommitted Task 38/UI
work visible in the original worktree was intentionally not included or
modified. The prompt was read from `origin/codex/prompt-dropbox`.

Evidence labels used below:

- **Current** — directly observed in the repository code.
- **Inference** — a consequence of the current algorithms or data model; it is
  not presented as a benchmark.
- **Recommendation** — proposed future architecture, not implemented here.

## 1. Executive Summary

**How far can the current one-sided PCB generator plausibly scale, and what is
most likely to fail first?**

The reusable generator can plausibly produce believable small boards in the
rough range of the currently proven families: roughly 3–7 components, with
simple low-degree nets and generous one-sided routing space. A favorable
5–10-component board should also be possible, but that is an inference, not a
measured threshold. The real limit is topology, pin escape direction, package
courtyards, and connector ordering rather than a universal component count.

The first likely failure is **routing/placement rejection caused by congestion
and fixed single-layer crossing constraints**, not CircuitJS semantics. The
router routes nets sequentially on one 10-pixel grid, never rips up or reroutes
an earlier net, has no rotation, no layer, no via, no bus/plane, and no
jumper fallback. A later route can therefore make an otherwise solvable board
fail the entire attempt. At the same time, placement has no subsystem model
and does not call the router while scoring candidates. At higher density,
placement collisions and route rejection reinforce one another.

The current architecture is a good small-board training foundation because
logical net identity, visible copper, stable pad IDs, provider-owned footprints,
and geometry validation are separated. It should not be extended to composed
multi-subsystem boards by only enlarging the board rectangle. Medium boards
need region/subsystem-aware placement, better power/net distribution, route
quality telemetry, and bounded rip-up/reroute. A small number of explicit,
visible wire links or 0-ohm components is a useful intermediate escape hatch.
Two-sided/layer-aware routing should wait until real composed topologies show
that those one-sided tools are insufficient.

## 2. Current PCB Generation Pipeline

The actual path is:

1. A family generator creates a `TroubleshootBoard` containing stable
   `BoardComponent`, `BoardPad`, `BoardNet`, and external power-input IDs while
   it builds the CircuitJS elements and simulation bindings. The main examples
   are `LedIndicatorGenerator`, `DiodeProtectedIndicatorGenerator`,
   `ParallelDualIndicatorGenerator`, `RcDelayGenerator`, and
   `NpnLowSideSwitchGenerator`.
2. `TroubleshootBoard` owns the logical graph. `BoardPad` joins a component
   terminal to a stable logical net; it does not store a CircuitJS analyzed
   node number. `BoardSimulationBindings` separately maps stable pad IDs to
   live measurement endpoints.
3. `PcbFootprintRegistry` selects a provider by the typed
   `PhysicalPackage`. `StandardPcbFootprintProviders` owns the current
   through-hole connector, output header, axial resistor/diode, LED, TO-92
   NPN, capacitors, and generic multi-terminal footprint definitions.
4. `SeededPcbLayoutGenerator` consumes only the logical board and seed for the
   LED, diode-protected, parallel, and developer-canary layouts. It creates a
   `PcbBoardLayout`, places provider-produced `PcbFootprint` objects, routes
   stable net/pad relationships, places silkscreen objects, validates, and
   compacts the result.
5. `RcDelayPcbLayoutFactory` and
   `NpnLowSideSwitchPcbLayoutFactory` are separate deterministic factories.
   They place components, pads, traces, and labels with explicit coordinates;
   the NPN factory uses the registered TO-92 provider only for Q1. These are
   valid proof layouts, but they are not evidence that the generic placer/router
   scales to those boards.
6. `PcbBoardLayout.validateGeometry` checks stable references, bounds, body and
   routing courtyards, pad spacing, endpoint/net identity, Manhattan geometry,
   trace crossings/clearance, silkscreen collisions, and route-quality limits.
   The family generators pass the resulting layout into `GeneratedBoardInstance`.
7. `PcbWorkbenchRenderer` consumes the layout. It draws the outline, all traces,
   pads, provider-rendered installed parts, labels, and the parts tray. It does
   not determine electrical behavior or mutate copper.
8. `PcbWorkbenchRenderer` hit-tests loose parts, exposed leads of lifted or
   removed parts, and pads. `BoardPadProbeTarget` resolves a stable pad ID via
   `BoardSimulationBindings`; `ComponentLeadProbeTarget` resolves the current
   component-side endpoint for the captured physical part. There is no current
   trace-segment probe target for arbitrary exposed copper.

Important ownership boundaries:

| Concern | Current owner | Audit result |
| --- | --- | --- |
| Logical component/net identity | `TroubleshootBoard`, `BoardComponent`, `BoardPad`, `BoardNet` | Stable IDs are explicit and independent of solver node numbers. |
| Package selection | `BoardComponent` / `PhysicalPackage` | Typed package identity reaches both footprint and render registries. |
| Footprint generation | `PcbFootprintRegistry`, `StandardPcbFootprintProviders` | Provider-owned placement, pads, keep-out, courtyard, and pad escapes. |
| Placement | `SeededPcbLayoutGenerator`; hand factories for RC/NPN | Topology-aware but not subsystem-aware; no rotation. |
| Pad coordinates | `PcbFootprint` or family factory | Stable pad IDs are retained; no layer or pad-shape data. |
| Keep-outs/courtyards | `PcbComponentPlacement` | Separate body keep-out and routing courtyard, both rectangular. |
| Pad escape vectors | `PcbPadPlacement` | One cardinal direction plus length, or no escape. |
| Net ordering | `SeededPcbLayoutGenerator.routeNets` | Lexicographic net IDs, then lexicographic pad IDs. |
| Route search | Private `SeededPcbLayoutGenerator.Router` | Coarse-grid A* with direction state and bend cost. |
| Width/clearance | `PcbTraceRules` | One fixed width and one fixed centerline clearance. |
| Silkscreen | `SeededPcbLayoutGenerator` or family factories; `PcbBoardLayout` validates | Collision-aware for the generic path; fixed for hand-authored paths. |
| Board outline/tray | `PcbBoardLayout`; `compactToContent`; tray invariant | Generic outline derives from occupied model geometry; tray is workbench chrome. |
| Determinism | Seeded `Random`, sorted IDs, fingerprints | Deterministic attempts are present, but phase streams are coupled. |
| Geometry/route validation | `PcbBoardLayout`, `PcbLayoutDeveloperVerifier` | Strong for the current one-sided representation; not manufacturing CAD. |
| Rendering/interaction | `PcbWorkbenchRenderer`, physical render providers | Provider-owned body/lead/probe geometry is separate from router geometry. |
| Electrical probe mapping | `BoardSimulationBindings`, probe target classes | Solver-backed and stable by pad/physical-part identity; traces themselves are not targets. |

## 3. Current Placement Model

### What is current

`TopologyPlacementGraph` is a component-level view of the logical board graph.
For every pair of pads on a net it creates a weighted link, skips explicitly
declared package-internal connections, gives two-pad nets weight 3.0, gives
larger shared nets weight 1.0, and multiplies links involving the selected
connector by 0.45. It is topology-aware, but it has no functional-subsystem,
power-domain, signal-direction, connector-bank, or role metadata.

`SeededPcbLayoutGenerator` anchors the lexicographically first connector near
the right edge of a fixed working outline and points its pad escapes inward. It
places the remaining components in descending order of links to already placed
components. Each candidate is attracted toward already placed pad positions,
then receives a seeded offset and is chosen from a fixed set of offsets. If
those candidates fail, a deterministic grid scan is used.

Placement acceptance currently requires:

- routing courtyards plus an additional 8-pixel padding to fit inside the
  working outline;
- padded routing courtyards not to intersect placed padded courtyards; and
- pad centers to be at least 26 pixels apart.

The current provider geometry is translated, not rotated. The generic placer
therefore keeps axial parts horizontal and uses each provider's fixed pin
layout. The connector is specially anchored; other connectors are not given a
general edge constraint. Placement does not run A* or a congestion estimate,
so it optimizes topology distance and spacing heuristics rather than actual
route feasibility.

### What is not current

- no subsystem regions or functional clustering beyond graph adjacency;
- no high-degree-net reservation or power/control separation;
- no orientation search or pin-order permutation;
- no route-aware placement loop;
- no dynamic board expansion during placement;
- no local placement repair after a route fails;
- no common scalable placement path for the RC and NPN hand-authored layouts.

### Inference

The heuristic is appropriate for small, recognizable through-hole boards. It
will tend to put electrically related parts near one another, but the same
behavior can pack unrelated branches around a high-degree rail and leave the
router to resolve all crossings afterward. A fixed connector anchor and fixed
pin escape directions make the relative terminal order more important than raw
component count.

## 4. Current Routing Model

The reusable router is a one-sided rectilinear router over a fixed 10-pixel
grid. For the generic path, the working outline is `720 x 400` inside a
`1040 x 520` canvas. The router grid is therefore 71 by 39 cells (2,769
locations), with five direction states per location, including the initial
no-direction state.

For each sorted logical net, the router takes the first sorted pad as an
anchor and routes every other externally reachable pad from that anchor. A
declared package-internal connection is skipped as copper. Same-net cells may
be reused at a reduced cost, so multi-pad nets can form a visible shared trunk,
although the route is still generated as a series of root-to-pad paths rather
than a true optimized net tree.

The A* state includes position and previous direction. Each step costs the grid
size, a bend costs 35, same-net copper costs 2, and a same-net clearance cell
costs 7. Different-net occupied or clearance cells are blocked. Component
routing courtyards are blocked except for the selected endpoint's explicit
escape corridor. The generated path retains every grid point so the renderer
and validator see the exact path decisions.

The model supports or does not support the requested capabilities as follows:

| Capability | Status | Evidence |
| --- | --- | --- |
| Manhattan routing | **Yes** | Router moves only up/down/left/right; validation rejects diagonal segments. |
| Arbitrary bend count | **Bounded** | A* can search multiple bends, but validation rejects more than 16. |
| Per-net sequential routing | **Yes** | Sorted nets and sorted pad endpoints are routed in order. |
| Obstacle avoidance | **Yes, rectangular/coarse** | Courtyards, occupied cells, clearance cells, and other pad centers are considered. |
| Crossing prevention | **Yes for unrelated visible traces** | A* occupancy plus final segment-crossing and clearance validation. |
| Shared copper/net merging | **Yes for same-net paths** | Same-net cells can be reused; validation skips same-net clearance/crossing rejection. |
| Buses | **No** | No bus/tree/net-class object or special distribution policy. |
| Rip-up/reroute | **No** | A failed later path aborts the whole attempt; earlier paths are not removed. |
| Vias | **No** | No via or layer field exists in the layout model. |
| Multiple copper layers | **No** | `PcbTraceGeometry` stores only x/y points and net/end pads. |
| 0-ohm jumpers | **No** | No generated link component or routing fallback exists. |
| Component rotation | **No** | Footprints are translated, not transformed. |
| Ground/power planes | **No** | Rails are ordinary routed `BoardNet` traces. |
| Trace-width classes | **No** | `PcbTraceRules.TRACE_WIDTH` is a single fixed value of 9. |

The hand-authored RC and NPN paths use `PcbTraceGeometry` directly. They can
express a carefully chosen shared trunk and avoid the generic router's search,
but they still have the same single-layer data model and validation rules.

## 5. Scaling Stress Analysis

These ranges are reasoned from the code, not benchmark measurements. The
current production proof covers only small families and developer canaries;
there is no repository benchmark corpus for 15, 30, 50, or 100 components.

| Board size | Likely behavior | First risks |
| --- | --- | --- |
| 5–10 components | Plausible for sparse, low-degree, mostly planar through-hole graphs. This is near the current proof envelope, not a guaranteed limit. | Fixed escape directions, route ordering, label candidates, and occasional whole-attempt rejection. |
| 15–25 components | Some sparse boards may fit if the topology is intentionally laid out; reliable generation is doubtful without region placement and route-aware scoring. | Courtyard packing, high-degree rail fanout, non-crossing order, A* attempts after earlier nets claim the grid, and label fallback scans. |
| 30–50 components | The current generic generator should be treated as beyond its intended range. A carefully hand-authored planar board may still validate, but generic generation will have a high rejection/latency risk. | Placement fallback cost, fixed outline capacity, trace/clearance pair checks, route detours, excessive bends, and deterministic retry exhaustion. |
| 50–100 components | Not realistically scalable in the current generic path. Enlarging the working rectangle may make some routes possible but does not provide better placement or better topology. | Congestion and crossing constraints, unreadable fit-to-canvas rendering, quadratic validation, large numbers of root-to-pad branches, and no recovery from a bad net order. |
| 100+ components | Outside the current architecture's plausible game-PCB envelope. It would require a composed-board placement/routing architecture first. | Nearly every stage: footprint/label density, placement search, A* occupancy, validation, renderer scale/hit targets, and one-sided topology limits. |

The important non-count conclusion is that a 20-component chain can be easier
than a six-component board with two tightly interleaved connectors and several
high-degree rails. Conversely, a high-degree same-net rail can be easy if it is
given a visible trunk corridor and its branches are placed in terminal order.

## 6. One-Sided Routing Limits

### Current behavior

All copper is represented as one visible layer of orthogonal centerlines. An
unrelated crossing or insufficient centerline clearance is rejected. The
router cannot pass over an occupied path, change layer, place a via, or insert a
link component. The final validator is stricter than a general through-hole
manufacturing router because it also reserves component routing courtyards to
keep traces visible and probeable.

Same-net connectivity is a useful exception: multiple paths may merge or
overlap, and a multi-pad net is routed from one stable root. This helps ordinary
GND/VIN branches, but it is not a bus or plane implementation. The root is the
lexicographically first pad, not a power-distribution decision.

### Inference: graph and placement limits

Planar connectivity is not sufficient by itself. A graph may be embeddable in a
plane, yet the chosen component locations, fixed pad order, escape corridors,
and reserved courtyards may force two required paths to cross. A non-planar
terminal arrangement has no one-layer solution without changing placement,
using a same-net merge, or adding a physical link/layer.

Dense shared power/control nets are not automatically non-planar because
same-net copper may merge. They become impractical when their fanout consumes
the corridors needed by unrelated signal nets, when a root-to-each-pad star is
longer than a trunk, or when connector pin order forces interleaving branches.
Multiple subsystems make this more likely because their inputs and outputs tend
to be distributed around several connectors.

Making the board larger helps with clearance, courtyard packing, and detours for
graphs that are planar under the chosen terminal order. It does not change a
crossing order, add a missing escape direction, provide an overpass, or make a
root-star distribution strategy semantically better. Board expansion also
increases the A* search area and can make the fit-to-canvas player view smaller.

## 7. Zero-Ohm Jumpers / Wire Links

### Current status

There is no 0-ohm resistor or wire-link routing infrastructure today. An axial
resistor footprint exists, but the generator does not synthesize a low-value
resistor when a route fails. The future roadmap's jumper work is a separate
physical repair capability, not a current router fallback.

### Recommendation

For generated-board routing, a link should be a real, visible logical board
component rather than invisible routing metadata. A plausible model is:

- a generated `LINK`/`JUMPER` component with a stable component ID and two
  stable pads on the two logical net segments;
- a dedicated physical package or an explicitly declared axial wire-link
  package with body, lead, courtyard, label, and probe geometry;
- a real CircuitJS `WireElm` or a numerically safe very-low-resistance backing,
  selected deliberately so the solver remains the electrical source of truth;
- normal `BoardPadProbeTarget`/component-lead mapping and normal layout
  validation; and
- visible copper from each original net to the link pads, with the link itself
  included in the generated board and its repair/scoring history.

The link may be implemented as an actual logical component or as a special
physical component bound to a simple solver wire, but it should not be a
renderer-only shortcut. Treating two originally disconnected nets as one
`BoardNet` would erase the physical crossing/link semantics and make probing
misleading.

A few links can improve realism for through-hole boards: a factory wire over a
crowded crossing, a power strap, or a visible 0-ohm configuration link. They
should be bounded and quality-scored. If a board needs many links, the links
are disguising a failed placement/router and are likely to make visual tracing
harder than a carefully introduced second layer.

## 8. Multilayer and Via Path

### Current data-model gap

The current model has no layer identity in `PcbBoardLayout`,
`PcbTraceGeometry`, `PcbPadPlacement`, `PcbComponentPlacement`, or
`PcbTraceRules`. A trace is just `(netId, startPadId, endPadId, xPoints,
yPoints)`. A pad has a point and one cardinal escape corridor. A component has
rectangular bounds/keep-outs/courtyards but no side, rotation, or layer access.
The renderer draws every trace in the same copper style and has no board-flip or
layer-view state.

### Required later structures

The smallest credible two-sided extension would need:

1. A board layer/side enum and a route segment/path type carrying layer,
   centerline, and trace-width class.
2. Layer-aware pad access: through-hole pads visible/conductive from both sides,
   surface pads assigned to a side, and explicit escape direction in the
   selected side's coordinates.
3. A via primitive with stable ID, net identity, location, drill/annular
   geometry, start/end layer, and a visible renderer/probe policy.
4. Layer-specific occupancy and clearance in the router, plus via keep-outs and
   anti-pad/plane rules if planes are later introduced.
5. Component side/orientation and per-side body/courtyard semantics.
6. Validation that preserves stable pad/net identity across layers and rejects
   illegal via/trace/pad clearances.

The electrical mapping can remain stable: a via and all conductive segments
would resolve to the same `BoardNet`/pad identity, while the physical layout
records where that connection is visible. For through-hole parts, the existing
pad identity can remain probeable from a front/back view. For SMD pads and
buried traces, probe targets need a visible side/layer policy; a hidden net must
not silently become clickable.

### Renderer/controller fit

The current renderer/controller boundary could accommodate a board-view state
because screen coordinates already pass through `PcbWorkbenchRenderer` and
controller hit testing delegates to it. It cannot support a flip correctly by
only mirroring pixels: provider terminal geometry, labels, body side, trace
visibility, and probe marker coordinates would all need the same transform.

The recommended first step, when demanded by topology, is a data-only
front/back route model with all existing through-hole geometry defaulting to the
front and a validator/router that can place a trace on either layer. Add visible
layer selection/board flip and via probes in the same bounded feature. Do not
introduce invisible multilayer connectivity first and explain it later.

## 9. Ground / Power Distribution

### Current behavior

`VIN`, `GND`, `5 V`, `9 V`, and `12 V` are not special routing classes. They are
ordinary `BoardNet` IDs. The generic router sorts them lexicographically and
routes their pads using the same width, clearance, and obstacle rules as signal
nets. Same-net reuse provides a limited visible trunk. The NPN hand-authored
layout deliberately makes GND a shared trunk; that is a family-specific route,
not a reusable power-distribution service.

There is no ground plane, power plane, wider rail, star-distribution policy, or
subsystem-local rail abstraction. The renderer only shows copper that exists in
the layout, so current behavior does not create invisible magical connectivity.

### Recommendation

The least disruptive progression is:

- model high-connectivity rails as explicit visible trunks/trees, with branch
  points and quality metrics rather than a root-to-every-pad star;
- add optional net classes for rail width only when the renderer and clearance
  validator consume the same width;
- place a connector-edge power entry and local subsystem branches before signal
  routing; and
- use a visible copper-pour/plane object only after its geometry, exposed
  probeability, clearance, and short semantics are defined.

Star distribution is easy to understand while troubleshooting but can be long.
An explicit routed bus improves scalability and remains visually traceable.
A plane improves electrical scalability most, but complicates everything the
player sees: any exposed plane point could be a probe target, plane boundaries
must be drawn, other nets need clearances, and a plane connection cannot be
allowed to exist only in the solver.

Strategic vias and local power distribution are later-layer tools. They should
be added only with stable physical identity and visible copper semantics.

## 10. Subsystem-Aware Placement

The current placement graph knows electrical adjacency but not that a resistor
is a regulator part, a connector is a sensor input, or a transistor is an
output driver. `BoardComponent.type` and package identity are available, but no
placement API consumes functional role, subsystem, direction, or preferred
region. Current placement therefore clusters connected parts without
deliberately separating power entry, control, sensor, and output sections.

### Recommendation

Future composed boards should place coarse functional regions before placing
individual footprints. A minimal placement hint contract should carry:

- subsystem ID and role (power entry, regulator, control, sensor conditioning,
  driver, indicator, output connector);
- preferred/required board region and connector-edge anchor;
- upstream/downstream or source/sink relationships;
- high-degree net membership and preferred trunk corridor;
- approximate density/keep-out budget;
- orientation/pin-escape preference; and
- whether the component is a visual anchor, red herring, or service-access
  target.

This should improve realism, route length, congestion, readability, and
rejection rate because related parts have a physically plausible neighborhood
and long cross-board routes become exceptional. It must not expose the answer:
regions should be coherent but include seeded variation, auxiliary circuitry,
and enough routing ambiguity that the player still reasons from measurements
and traces.

## 11. Geometry Envelope Consistency

### Intended separation

The code correctly attempts to keep these concerns distinct:

- electrical pad/net geometry: `BoardPad` and `PcbPadPlacement`;
- component body keep-out: `PcbComponentPlacement.keepOut`;
- routing reserve: `PcbComponentPlacement.routingCourtyard`;
- selection/probe geometry: `PhysicalPartRenderGeometry` and provider terminal
  points; and
- visible body/lead geometry: package-specific render providers in
  `StandardPhysicalPartRenderProviders`.

This separation is architecturally healthy. The renderer does not decide
electrical connectivity, and the router does not inspect CircuitJS.

### Current consistency risks found in code

The envelopes are not currently tied by a shared provider contract:

- `PcbBoardLayout` validates component rectangles, keep-outs, and courtyards,
  but it only knows the rectangles returned by the footprint provider.
- `PcbWorkbenchRenderer` asks physical render providers to draw bodies from
  pad points and package-specific offsets. The installed selection region is
  generally `context.getComponentBounds()`, while the visible body may be
  above or outside that rectangle.
- `compactToContent` derives the board from courtyards, 32-pixel pad boxes,
  trace-width boxes, and labels. It does not include provider-rendered body
  geometry, lead width, actual font bounds, or provider hit regions.

Concrete code-level examples at the normal scale are worth correcting before
dense placement:

- The axial resistor is a comparatively consistent case: its rendered
  34-pixel body is inside the provider's roughly 60-pixel routing courtyard at
  the normal scale. This is an implementation detail, not a shared contract.
- The radial electrolytic footprint places pads around `y + 30` and reserves a
  courtyard beginning around `y + 4`, but the renderer centers its body at
  `minPadY - 34` with radius 31. Its visible body can extend materially above
  both the placement and routing courtyard.
- The ceramic provider has a smaller but similar top extension: its rendered
  body is centered above the pad pair while its courtyard starts below that
  visual extent.

These are code-derived risks, not a claim that every current seed visibly
fails. They mean the router can believe a corridor is clear while the renderer
draws a body or lead there, and compaction can derive a board outline that does
not contain every rendered pixel. The NPN developer verifier checks Q1 against
the registered TO-92 footprint, but the general provider/render envelope
relationship is not validated for every package.

Silkscreen has a second, smaller mismatch risk: `textWidth` estimates width by
character count, while the renderer uses browser font metrics. Validation uses
the estimated rectangle, not measured rendered glyph bounds.

### Recommendation

Have each package provider expose separately, but consistently, its pad
geometry, body envelope, routing keep-out, selection envelope, and probe
terminals in one logical coordinate system. Keep the categories separate in the
model, then add canaries asserting that rendered body/lead bounds are inside
the intended body/courtyard contract and that compacted board bounds include
the provider geometry. Do not make the router consume renderer drawing code.

## 12. Deterministic Seed Behavior

### Current behavior

Family generation uses a `Random(seed)` for values/topology/fault selection.
The generic layout generator derives a fresh attempt stream with
`seed ^ (0x9E3779B97F4A7C15L * (attempt + 1))`. Every attempt is deterministic,
and the generator keeps the lowest route-quality score among the first five
viable candidates, up to 80 attempts. Net IDs, pad IDs, and component IDs are
sorted in the placement/routing decisions. Geometry fingerprints sort the
component/pad/label identities and fully order trace endpoints.

This is enough for reproducible current seeds and developer verification. The
layout has separate random construction from the family generator, so a layout
does not consume the circuit generator's live `Random` object.

### Coupling and stability risks

Within one layout attempt, connector placement, footprint random dimensions,
prototype creation, component offsets, and candidate order share a `Random`.
Adding a random draw to one provider or changing placement order can therefore
scramble all later decisions. Attempt streams are isolated from one another,
but placement, routing, labels, and retry policy do not have independently
derived sub-seeds.

`PcbBoardLayout` stores components, pads, and labels in `HashMap`s, while many
accessors return `HashMap.values()` without sorting. Fingerprints normalize most
of this for verification, but renderer draw/hit-test order and any future
algorithm that iterates an accessor can still inherit collection-order
assumptions.

### Recommendation

Keep the public challenge seed, but derive named sub-seeds such as:

`layout = derive(seed, familyVersion, "layout")`

`placement = derive(layout, "placement", attempt)`

`routing = derive(layout, "routing", attempt, netId, routeCandidate)`

`labels = derive(layout, "labels", attempt)`

Use stable ID ordering everywhere, including renderer traversal and any future
quality sampling. Record generator version, seed, attempt, rejection reason,
and layout fingerprint in developer mode. This lets a placement improvement
preserve circuit/fault identity while intentionally changing only layout
geometry.

## 13. Failure / Retry Strategy

### Current behavior

The generic generator retries the entire attempt. Any exception from placement,
routing, silkscreen, compaction, tray placement, or geometry validation is
stored as the last failure and causes the next independent attempt. It does not
enlarge the working outline, change net order, remove a route, or locally move a
component after a failed route. If no attempt succeeds, it throws a single
failure containing the last message.

The generic path stops after five viable candidates, even if fewer than 80
attempts have been tried, and returns the best score among those candidates.
The RC and NPN factories do not have this retry loop; invalid hand-authored
geometry throws directly.

Specific failure handling is therefore:

| Failure | Current response |
| --- | --- |
| Placement collision/no candidate | Try another complete seeded attempt; eventually fail. |
| Route cannot be found | Abort the current complete attempt; no rip-up/reroute. |
| Trace crossing/clearance | Reject during or after the attempt; regenerate complete candidate. |
| Silkscreen conflict | Try deterministic label candidates/scan; then reject attempt. |
| Board/tray bounds | Reject attempt; no outline growth. |
| Route-quality violation | Reject attempt; no quality repair. |

### Recommendation

Use a staged failure pipeline: cheap logical/package checks, placement fit,
coarse congestion estimate, route candidate generation, bounded local
rip-up/reroute, silkscreen placement, quality validation, then bounded global
regeneration. Preserve the current all-or-nothing candidate boundary, but return
structured rejection data so the generator can learn whether it is failing on
packing, one net, clearance, labels, or board bounds. Only expand the board or
change layer/link policy at an explicit bounded stage; do not hide failure by
silently creating disconnected copper.

## 14. Routing Quality Metrics

### Current metrics

`PcbBoardLayout.validateRouteQuality` hard-rejects zero/direct-invalid traces,
more than 16 bends, detour greater than 3× direct Manhattan distance,
duplicate points, repeated/overlapping segments, and self-intersections.
`validateTraceClearance` checks every unrelated trace segment pair against the
15-pixel centerline minimum. `getRouteQualityScore` adds weighted costs for
trace length, bends, detour, pairwise same-net pad distance, board area,
unused area, courtyard area, edge margin, excessive courtyard gaps, and gives a
small credit for same-net centerline reuse. Candidate selection uses this score.

These checks reject absurd paths, and they are appropriate for a small game
generator. They do not measure:

- route order sensitivity or how close a route came to exhausting alternatives;
- per-net congestion/fanout difficulty;
- connector fanout realism or interleaved terminal order;
- branch-tree quality beyond shared centerline reuse;
- long parallel runs and visual congestion below the hard clearance threshold;
- subsystem crossings, component isolation, or visual trace-followability;
- renderer body/lead overlap or actual font readability; or
- layer/link count because those concepts do not exist yet.

### Recommendation

Add simple game-appropriate metrics before full CAD optimization:

- total and percentile trace length normalized by board diagonal;
- maximum and mean detour ratio and bend count;
- high-degree-net fanout length and branch/corridor congestion;
- percentage of route cells in a near-clearance band;
- connector-to-subsystem and subsystem-to-subsystem crossing count;
- component density, probe-target separation, and readable-label area;
- same-net trunk reuse versus repeated root-star length;
- number of generated link components, if enabled; and
- route failure/retry count by net and by stage.

Use hard rejection for unsafe/unreadable cases and a weighted score for choosing
among valid alternatives. Keep electrical connectivity as a prerequisite, not a
quality tradeoff.

## 15. Troubleshooting Readability

### Current strengths

Visible one-sided copper, stable component references, recognizable through-hole
parts, explicit pads, collision-aware labels, and no unrelated trace crossings
make the current small boards visually traceable. Stable pad IDs also let the
player probe a real logical endpoint while the solver remains authoritative.

### Scaling concerns

- `PcbWorkbenchRenderer.updateTransform` fits the entire fixed canvas and caps
  scale; there is no current zoom/pan strategy for a larger board.
- Pad hit testing uses an 18-pixel radius while model pad spacing only requires
  26 logical pixels. At a reduced fit scale, neighboring target regions can
  become ambiguous before the validator reports any overlap.
- Traces are drawn before pads/components, but there is no trace-segment probe
  target. A player cannot currently click arbitrary exposed copper between
  pads.
- Selection uses provider geometry, while routing uses rectangular courtyards;
  the two can disagree about where a part visually occupies space.
- More branches, longer detours, or a large number of labels can make the board
  visually chaotic even when geometry validation succeeds.
- A future layer view must distinguish front/back copper and make vias/hidden
  segments understandable; otherwise a second layer would reduce rather than
  improve troubleshooting readability.

The hardest board should be difficult because the player must identify the
relevant subsystem and interpret ambiguous measurements, not because traces
are uniformly tangled. Subsystem regions, visible power trunks, labels with
reserved readability space, and controlled variation are more useful than
random density.

## 16. Coffee-Maker / Controller Exercises

### A. Basic coffee-maker controller

A plausible first composed board might contain power entry, a low-voltage
supply, a control section, a sensor input, a relay or MOSFET output, an
indicator, and two or more connectors. The current generic path could plausibly
handle only a reduced planar version: one connector anchor, a short power chain,
one control branch, and one output path with currently supported packages. The
current standard providers do not provide a general regulator, relay, IC, or
sensor package; a custom package would need a provider and render contract.

Likely one-sided pressure points are the high-degree ground/rail nets, the
control-to-driver path crossing the sensor path, connector pin order, and the
physical separation needed to make power entry and output sections believable.
The current NPN seven-component board demonstrates that a hand-authored route
can be made to validate, but its explicit coordinates are not a scalable
composed-board solution.

### B. Denser fan or pump controller

A fan/pump controller with multiple inputs, outputs, status indicators, a
driver, protection, and possibly more than one rail creates several connector
anchors and repeated shared nets. The current graph can represent the logical
connections, and a carefully hand-authored planar layout could be made to pass
the validator, but the generic placer has no multi-connector edge policy and
the router has no net priority, rip-up, bus, or layer escape.

The first new infrastructure pressure would likely be subsystem-aware regions
and visible rail distribution, followed by bounded route alternatives. If
several independent signal paths must cross in the chosen terminal order, a
small number of explicit wire links may be credible. Repeated crossings or
many links should gate a two-sided route experiment instead.

## 17. Performance Considerations

No runtime measurements are claimed in this report. The following hotspots are
algorithmic consequences of the code.

### Router/search

The generic router allocates `gridWidth x gridHeight x 5` cost and predecessor
arrays per route. On the current 71×39 grid, the state count is 13,845. A* can
visit much of that state space for a difficult route. For each candidate cell,
`canOccupy` scans component courtyards and all pads, while `canTraverse` scans
component courtyards again. A rough worst-case shape is therefore

`O(routes × grid-cells × (components + pads))`,

plus priority-queue costs and path reconstruction. This is a complexity
description, not a timing result.

### Placement

`TopologyPlacementGraph` creates links for every pair of pads on each net,
which is quadratic in the pad count of a high-degree net. Component selection
repeatedly searches the placed vector for linked components. Candidate `fits`
checks placed courtyards and pad pairs. The fallback scan can inspect many
grid candidates for every remaining component, and dense boards are the cases
most likely to reach that fallback.

### Validation

Component-body and pad-spacing checks are pairwise. Trace crossing and
clearance checks are pairwise in traces and nested over their segment counts.
Silkscreen checks compare labels against components, pads, traces, and other
labels. With many multi-pad nets, trace count is `sum(netPadCount - 1)` for
externally routed pads, so validation grows with branch count as well as
component count.

### Retry and board growth

The generic generator can repeat placement, routing, labels, compaction, and
validation up to 80 times, seeking five viable candidates. A future larger
working outline would increase grid memory and search area directly. Before
adding large-board support, measure generation time by stage, attempts per
seed, rejection reason, cells expanded per route, maximum open-queue size,
route/segment counts, placement fallback use, validation time, peak memory,
render scale, and probe-target separation across a fixed seeded corpus.

## 18. Current Strengths

1. **Electrical/physical separation:** `PcbBoardLayout` uses stable board IDs,
   while CircuitJS and `BoardSimulationBindings` remain the electrical truth.
2. **Provider boundary:** package selection, footprint geometry, installed/loose
   rendering, and physical probe geometry are not hard-coded into the generic
   router/controller path.
3. **Small-board determinism:** sorted IDs, derived attempt streams, bounded
   retries, and geometry fingerprints make current layouts reproducible.
4. **Real visible copper:** the renderer consumes route geometry; a missing route
   is not silently replaced by a fake meter value or hidden net connection.
5. **Useful validation:** stable endpoint ownership, courtyards, escape
   corridors, crossings, clearance, silkscreen, detours, bends, and compactness
   are checked for the one-sided representation.
6. **Same-net handling:** multi-pad rails can share visible copper, and package
   internal connections are explicitly declared rather than inferred from
   component type.
7. **Proof-family pragmatism:** hand-authored RC/NPN layouts can exercise new
   electrical families without pretending the generic generator already
   handles every topology.

## 19. Current Scalability Blockers

1. Fixed-size, single-layer routing with no rip-up/reroute, via, layer, or link
   escape.
2. Placement that is adjacency-aware but not subsystem-aware, route-aware,
   rotation-aware, or multi-connector-aware.
3. Lexicographic sequential net routing and root-star multi-pad routing, which
   make result quality and success order-sensitive.
4. Fixed-width/fixed-clearance rules with no rail/bus/net-class distribution
   policy.
5. Mixed architecture: the reusable generic path and hand-authored family
   factories do not share a scalable placement/routing problem model.
6. Provider footprint and renderer body/selection envelopes are not checked by a
   common geometry contract; capacitors and some axial bodies can extend beyond
   the router's reserved envelope.
7. Whole-attempt retries with no structured rejection telemetry, local repair,
   board growth policy, or route alternative search.
8. Quadratic or nested scans in topology construction, placement, route
   validation, and silkscreen validation.
9. Fit-to-canvas rendering and fixed hit radii that will make larger boards
   harder to inspect and probe.
10. Coupled random decisions and unsorted map accessors that can make layout
    changes harder to reason about even though current fingerprints are stable.

## 20. Recommended Incremental Architecture

Keep the existing electrical source of truth and introduce explicit stages
around it:

```text
TroubleshootBoard / solver-validated family
                 |
                 v
        Physical package + envelope providers
                 |
                 v
     Placement problem + subsystem/region hints
                 |
                 v
       Placement solution + congestion estimate
                 |
                 v
        Route problem + net classes + obstacles
                 |
                 v
    Route trees / bounded alternatives / link policy
                 |
                 v
   Geometry + connectivity + quality validation report
                 |
                 v
      PcbBoardLayout -> renderer / probe mapping
```

The key contracts should be:

- **Logical board contract:** stable component, pad, net, terminal, and power
  IDs remain independent of physical coordinates and CircuitJS node numbers.
- **Envelope contract:** a package provider supplies pad coordinates, body
  envelope, routing keep-out/courtyard, selection envelope, and probe terminal
  geometry in a shared coordinate frame. The categories remain separate, but
  validation can compare them.
- **Placement contract:** a `PlacementProblem` contains footprints, regions,
  connector anchors, high-degree nets, orientation options, and deterministic
  hints; a solution reports collisions, margins, and congestion.
- **Routing contract:** a `RouteProblem` contains terminal escapes, obstacles,
  net classes, existing visible copper, and layer/link policy. A route is a
  connectivity-bearing tree/path object, not only an untyped array of points.
- **Validation contract:** electrical connectivity, physical legality,
  readability, and quality produce structured results. A valid layout remains a
  prerequisite for challenge generation.
- **Renderer/probe contract:** renderer consumes physical geometry and exposes
  only visible/probeable conductive features. If a layer, plane, via, or link
  carries electricity, it receives stable visible identity and measurement
  semantics.
- **Seed contract:** derived sub-seeds and sorted stable IDs keep circuit/fault
  selection independent from placement/routing evolution.

This is an incremental adapter architecture, not a general CAD rewrite. The
current `PcbBoardLayout` can remain the renderer-facing product while internal
placement/routing problem objects become richer.

## 21. Prioritized Recommendations

1. **Fix the geometry envelope contract first.** Include provider-rendered body
   and lead extents in compaction/validation canaries without coupling the
   router to drawing code.
2. **Add layout telemetry and a seeded stress corpus.** Record stage timings,
   rejection causes, grid expansions, route metrics, and renderer scale before
   choosing component-count targets.
3. **Add subsystem/region placement and connector-edge constraints.** Include
   high-degree rail corridors and orientation choices; score actual route
   feasibility, not only pad distance.
4. **Improve high-degree-net routing.** Generate visible trunks/trees and route
   critical or high-fanout nets with explicit priority rather than relying on
   lexicographic order.
5. **Add bounded rip-up/reroute and local placement alternatives.** Preserve
   deterministic limits and structured failure rather than hiding failure in
   arbitrary regeneration.
6. **Introduce a small, visible link-component fallback.** Limit and score
   generated 0-ohm/wire links; do not use them to mask widespread congestion.
7. **Add two-sided/layer-aware routing only when stress cases demonstrate the
   need.** Build layer/via/probe/render semantics together, not as an invisible
   router shortcut.

## 22. Recommended Bounded Future Tasks

These are audit recommendations only; they do not authorize starting a roadmap
milestone.

| Task | Scope | Acceptance evidence |
| --- | --- | --- |
| Envelope consistency canary | Extend provider geometry metadata and compactness checks for every registered package; no electrical changes. | Body/lead/selection/courtyard bounds agree for current families and rendered board remains inside outline. |
| Layout stress telemetry | Add developer-only counters and a deterministic corpus runner. | Reports attempts, rejection stage, cells expanded, route counts, quality metrics, and timings without changing normal UI. |
| Region-aware placement | Add subsystem/connector-edge hints, orientation candidates, and a dynamic placement outline while preserving stable IDs. | Seeded medium fixtures show lower route failure and preserve readable regions; no hidden connectivity. |
| Net-tree and congestion routing | Replace root-only fanout with explicit same-net tree candidates and high-degree/power routing policy. | Multi-pad fixtures validate all endpoints, visible trunk geometry, and quality improvements. |
| Bounded rip-up/reroute | Add deterministic alternate routes and limited backtracking after a route blocks a later net. | A fixture that fails only from net order succeeds without allowing unrelated crossings/clearance violations. |
| Visible generated link component | Add one physical link package and solver-backed board component, capped by policy. | Link is rendered, selectable, probeable, present in stable fingerprints, and electrically changes only its declared graph. |
| Two-sided route prototype | Add front/back traces, through-hole pads, vias, layer-aware validation, and a visible board-view policy. | A crossing fixture is solvable with visible layer/via identity and normal pad/trace measurement semantics. |

The first three are appropriate before medium composed boards. Link components
are an intermediate fallback after placement/routing quality is measured.
Layered routing is a later, topology-gated task.

## 23. File/Class Watchlist

### Logical and electrical boundary

- `src/com/lushprojects/circuitjs1/client/TroubleshootBoard.java`
- `BoardComponent.java`, `BoardPad.java`, `BoardNet.java`
- `BoardSimulationBindings.java`
- `GeneratedBoardInstance.java`
- `LedIndicatorGenerator.java`, `DiodeProtectedIndicatorGenerator.java`,
  `ParallelDualIndicatorGenerator.java`, `RcDelayGenerator.java`,
  `NpnLowSideSwitchGenerator.java`

### Placement, footprint, routing, and validation

- `SeededPcbLayoutGenerator.java`
- `TopologyPlacementGraph.java`
- `PcbBoardLayout.java`
- `PcbComponentPlacement.java`, `PcbFootprint.java`, `PcbPadPlacement.java`,
  `PcbTraceGeometry.java`, `PcbTraceRules.java`, `PcbSilkscreenLabel.java`
- `PcbFootprintProvider.java`, `PcbFootprintRegistry.java`,
  `StandardPcbFootprintProviders.java`
- `RcDelayPcbLayoutFactory.java`, `NpnLowSideSwitchPcbLayoutFactory.java`

### Renderer, physical geometry, and probes

- `PcbWorkbenchRenderer.java`, `PcbWorkbenchController.java`
- `PhysicalPartRenderContext.java`, `PhysicalPartRenderGeometry.java`,
  `PhysicalPartRenderHitRegion.java`
- `StandardPhysicalPartRenderProviders.java`
- `BoardPadProbeTarget.java`, `ComponentLeadProbeTarget.java`,
  `PhysicalPartProbeTarget.java`

### Verification and future integration points

- `PcbLayoutDeveloperVerifier.java`
- `ArchitectureDeveloperVerifier.java`
- `PhysicalFoundationDeveloperVerifier.java`
- `PhysicalSpecificationDeveloperVerifier.java`
- `docs/ROADMAP.md` (Task 59 is the larger-board placement/routing milestone)
- `docs/ARCHITECTURE.md` (current implementation boundary)

## 24. Risks and Open Questions

- **Topology versus count:** What seeded topology corpus should define “small,”
  “medium,” and “large” without inventing a universal component threshold?
- **Planarity policy:** Should the generator move components aggressively to
  preserve one-sided routing, or should it introduce a visible link earlier?
- **Power semantics:** When does a visible trunk stop being realistic enough to
  justify a plane, and how should exposed plane copper be probed?
- **Link physics:** Should a generated 0-ohm part use a CircuitJS wire or a
  small resistance to avoid singular solver behavior, and how should it be
  represented in repair history?
- **Envelope authority:** Should body/lead render providers report bounds, or
  should footprint providers own a shared physical envelope object consumed by
  both renderer and validator?
- **Layer UX:** How should a player distinguish a back trace, via, and
  through-hole pad while following a circuit on a front-facing workbench?
- **Probe coverage:** Is pad/lead probing sufficient for the intended game, or
  must arbitrary visible copper, vias, and planes become first-class targets?
- **Determinism compatibility:** Which geometry changes should preserve old seed
  layouts for reproducibility, and which require a generator-version bump?
- **Collection ordering:** Should all layout accessors return stable-ID-sorted
  vectors before any new routing or rendering feature relies on their order?
- **Failure observability:** How should normal challenge generation reject an
  electrically valid but visually absurd board without exposing private fault
  data to the player?
- **Mixed layout paths:** Should RC/NPN-style factories remain explicit proof
  fixtures, or should they migrate to the same placement/routing problem model
  before more circuit families are composed?
- **Roadmap sequencing:** Task 38 is the immediate roadmap milestone, while
  larger-board routing is Task 59. Any change to that order should be proposed
  explicitly rather than inferred from this audit.
