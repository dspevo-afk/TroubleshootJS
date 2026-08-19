# TroubleshootJS Component Visual Realism Audit

Audit date: 2026-08-18

This is a read-only architecture and visual-design audit. No production code,
tests, build scripts, UI files, roadmap documents, or concurrent work were
changed. At the start of the audit the checkout already contained uncommitted
Task 38/NMOS-related changes in the main worktree; those changes were inspected
as part of the current implementation but were not edited. The separate
`ui-workbench-shell` worktree was not changed.

The rendered observations below are also consistent with the existing evidence
images under `docs/task-evidence/`, especially the LED, RC-capacitor, NPN, and
resistor replacement captures. No external visual references were used.

## 1. Executive Summary

TroubleshootJS already has a sound ownership boundary for improving static
component art. The electrical graph remains in CircuitJS; logical board
identity and stable pad/net IDs live in the troubleshooting board model; PCB
footprints own placement, pads, keep-outs, and route courtyards; and package-keyed
physical render providers own installed/tray body drawing, terminal points, hit
regions, and probe-target dispatch. `PcbWorkbenchRenderer` performs common
canvas traversal and transformation but does not decide component physics.

That boundary makes the highest-value visual improvements relatively low risk:
replace flat component fills with bounded procedural shapes, add restrained
shading/highlights, improve leads and pads, and polish the board surface while
leaving pads, traces, terminal ordering, probe points, selection bounds, and
physical identities unchanged.

The largest current visual debt is:

- axial resistors and diodes are rectangular blocks with single-color outlines;
- transistor bodies are blue circles with square outlines and family text rather
  than recognizable TO-92 packages;
- capacitors use simple disk/rectangle approximations and can be drawn outside
  the footprint keep-out assumptions;
- pads and traces are flat, uniform strokes with no solder-joint, copper-edge,
  or depth treatment;
- the PCB is a flat green rectangle with no edge, mounting, or surface-depth
  cues; and
- the PCB LED renderer has only a binary illuminated flag, a fixed red body,
  and a large opaque yellow aura. It does not use the continuous current-based
  brightness already present in `LEDElm`, nor the LED's RGB nameplate values.

The preferred LED direction is to expose the existing CircuitJS
current-to-brightness mapping through the existing operational-state seam. The
physical renderer should receive a normalized intensity and combine it with
the installed physical LED's nameplate color. It should render the lens and
internal emission as the primary effect, with a small color-matched halo only
at higher intensities. The existing boolean `isIlluminated()` threshold should
remain available for challenge/repair behavior so visual fidelity does not
silently change electrical acceptance rules.

Recommended future order:

1. bounded static axial-body and lead improvements;
2. a solver-backed continuous LED intensity seam, without visual redesign;
3. physical LED lens/core/bloom rendering driven by that intensity;
4. footprint-envelope reconciliation for capacitors and TO-92 packages,
   followed by shared package visual primitives; and
5. board, pad, trace, silkscreen, and depth polish.

The next roadmap milestone remains Task 38. This report does not start a visual
implementation milestone or authorize work outside the report itself.

## 2. Current Rendering Architecture

### 2.1 Actual rendering path

The normal generated-board path is:

```text
CircuitJS solver / GeneratedBoardInstance
    -> TroubleshootBoard components, pads, nets, and connection bindings
    -> BoardPhysicalSpecifications / PhysicalSpecification / PhysicalNameplate
    -> PhysicalPackage (stable package identity and terminal semantics)
    -> StandardPcbFootprintProviders -> PcbFootprint
    -> SeededPcbLayoutGenerator -> PcbBoardLayout
       (component placement, pad coordinates, routes, silkscreen, tray)
    -> PcbWorkbenchController
    -> PcbWorkbenchRenderer
       -> PhysicalPartRenderRegistry by PhysicalPackage
       -> PhysicalPartRenderProvider / PhysicalPartRenderer
       -> PhysicalPartRenderContext
       -> Graphics / GWT Canvas2D
```

At runtime, `CirSim.updateCircuit()` calls
`PcbWorkbenchController.draw()`. `PcbWorkbenchRenderer.draw()` clears the
canvas, draws the board/traces/pads/components/tray, and publishes developer
geometry when verification is enabled. It re-renders from current runtime state
on each scheduled repaint; the renderer does not maintain a second electrical
model.

### 2.2 Ownership map

| Concern | Current owner | What it actually owns |
| --- | --- | --- |
| Logical board identity | `TroubleshootBoard`, `BoardComponent`, `BoardPad`, `BoardNet` | Stable component, pad, terminal, and net IDs used by bindings and validation. |
| Physical specification | `BoardPhysicalSpecifications`, `BoardPhysicalDefinition`, `StandardPhysicalDefinitionProviders` | Typed package-compatible specifications and player-visible markings. |
| Physical part identity | `PhysicalPart` implementations and `PhysicalBoardRuntime` | Runtime-owned part ID, provenance, mount state, electrical backing, failure state, and render metadata. |
| Package identity | `PhysicalPackage`, `PhysicalPackages` | Stable package ID, terminal IDs, connector flag, and declared internal package connectivity. It does not currently contain dimensions or pixels. |
| PCB footprint | `PcbFootprintProvider`, `StandardPcbFootprintProviders`, `PcbFootprint` | `PcbComponentPlacement`, pad coordinates, keep-out, routing courtyard, and pad escape vectors. |
| Procedural layout | `SeededPcbLayoutGenerator`, `PcbBoardLayout` | Seeded placement, routing, board/tray rectangles, silkscreen records, route validation, and deterministic geometry fingerprints. |
| Board/traces/pads/tray drawing | `PcbWorkbenchRenderer` | Common canvas background, grid, board fill, trace strokes, pad circles, silkscreen text, tray chrome, transforms, and provider traversal. |
| Component body drawing | `StandardPhysicalPartRenderProviders` | Package-registered renderers for resistors, diodes, LEDs, NPN/NMOS, capacitors, connectors, and generic multi-terminal parts. Both installed and loose drawing are provider-owned. |
| Render context | `PhysicalPartRenderContext` | Screen transforms, pad lookups, mounted lead endpoints, loose-tray terminal points, component state, package/part access, and the current binary LED illumination query. |
| Selection/hit geometry | `PhysicalPartRenderGeometry`, `PhysicalPartRenderHitRegion`, `PcbWorkbenchRenderer` | Provider-returned terminal points, hit regions, and selection bounds consumed by generic component/part selection. |
| Probe geometry/targets | `PhysicalPartRenderProbeProviders`, `PhysicalPartRenderer`, `PcbWorkbenchRenderer` | Loose and installed terminal target creation, with actual CircuitJS-backed measurement endpoints. |
| Electrical LED brightness | `LEDElm` | Solved current, `maxBrightnessCurrent`, the logarithmic visual mapping, and RGB color application for the CircuitJS schematic renderer. |
| Generated LED operational state | `GeneratedComponentOperationalStates` | Current LED binding and the existing boolean `isIlluminated()` threshold. It currently exposes no continuous intensity. |

### 2.3 Installed component geometry

There are two different meanings of installed geometry in the current code.

1. Electrical/PCB geometry is created by the footprint provider and layout
   generator. For example, the axial providers place two pads and create a
   `PcbComponentPlacement` with bounds, keep-out, and routing courtyard.
   Those coordinates are used by routing, pad mapping, and deterministic layout
   fingerprints.

2. Render/interaction geometry is created by the physical render provider.
   `BaseRenderer.installedGeometry()` currently uses the screen-space component
   placement rectangle as its selection bounds and single hit region. Its
   terminal points come from `PhysicalPartRenderContext.installedTerminals()`
   and ultimately `getComponentProbePoint()`/pad lookup. The body pixels are
   then drawn by the package-specific `drawInstalled()` method using those
   points and package-specific offsets.

Therefore the footprint does not directly draw the body, and the body drawing
does not automatically update the footprint or hit region. A visual body can
be made richer without changing PCB geometry only while it stays within the
existing visual/interaction envelope, or while the interaction geometry is
explicitly reviewed at the same time.

### 2.4 Loose-parts-tray geometry

`PcbBoardLayout` owns the tray rectangle. The layout generator initially creates
the tray and later calls `positionPartsTrayDisjointFromBoard()` to place it on a
valid side of the board. `PcbWorkbenchRenderer.drawTray()` obtains the current
runtime-owned loose parts, builds a loose `PhysicalPartRenderContext` for each
visible paginated part, asks its package renderer for loose geometry, and calls
`drawLoose()`.

The context computes loose terminal points from tray position, tray index, and
terminal count. `BaseRenderer.looseGeometry()` creates a tray-derived selection
rectangle and hit region around those terminal points. The loose body is again
drawn independently by the provider. Installed and loose renderers share
package-specific drawing helpers only by convention today; they do not share a
formal visual primitive layer.

### 2.5 Package, footprint, probe, body, and selection relationship

The important separation is:

| Layer | Stable data | Current implementation |
| --- | --- | --- |
| A. Electrical / PCB geometry | Pad IDs, pad coordinates, trace points, terminal IDs, courtyard, board placement | `PhysicalPackage`, `PcbFootprint`, `PcbPadPlacement`, `PcbComponentPlacement`, `PcbBoardLayout`, connection bindings. |
| B. Probe / hit geometry | Terminal screen points, probe radius, component/part hit regions, selection bounds | `PhysicalPartRenderGeometry`, `PhysicalPartRenderTerminal`, `PhysicalPartRenderHitRegion`, `PhysicalPartRenderContext`, and `PcbWorkbenchRenderer`. |
| C. Visual geometry | Body silhouette, highlights, bands, labels, shadows, lens/core/bloom | `drawInstalled()`/`drawLoose()` in `StandardPhysicalPartRenderProviders` and common board drawing in `PcbWorkbenchRenderer`. |

Safe visual work changes C while preserving A and B. In the current code, B is
not a precise body mask: standard installed hit geometry is generally the full
component placement rectangle, while probe clicks use terminal points and a
fixed `18 px` squared-distance radius. This is forgiving for troubleshooting,
but it means visual realism must not make a part appear probeable at a location
where the provider does not expose a terminal.

## 3. Component-by-Component Audit

### Resistors

**Owners and geometry.** The physical package is
`PhysicalPackages.AXIAL_RESISTOR`. `StandardPcbFootprintProviders.AxialProvider(0)`
creates a seeded horizontal two-pad footprint with a span of roughly 220--260
logical units, 34 logical body height, and a routing courtyard. Body drawing is
owned by `StandardPhysicalPartRenderProviders.ResistorRenderer`; value and
tolerance metadata come from `ResistorNameplate`, and the four bands come from
`ResistorColorCode`.

The installed renderer anchors the body at `pad1.x + 45` through `pad2.x - 45`
and draws leads to `getMountedLeadEnd(0/1)`. The loose renderer uses the loose
terminal points and a smaller body. Both use the same `drawResistorBody()` helper
but currently draw a rectangle and four full-height rectangular bands.

**Current realism.** The axial proportion is recognizable at normal zoom, but
the body is a flat tan rectangle with a hard dark outline. The leads are a
single solid gray/tan stroke with no exposed-to-body transition, highlight, or
end termination. Bands have equal spacing and equal width, including the gold
tolerance band; there is no deliberate gap before the tolerance band. There is
no cylindrical highlight or lower-edge shadow. The existing supported color-code
domain is intentionally narrow: integer values at least 10 ohms with exactly
five percent tolerance and a four-band code.

**Drawing-only opportunity.** A more convincing axial resistor can be drawn
without changing pad or probe geometry:

- keep the current left/right centerline anchors and body envelope;
- draw a central rounded/cylindrical body using a body rectangle plus end-cap
  ovals or a small polygon;
- use a beige/tan base with a darker lower edge and one restrained top highlight;
- make leads two-tone or two-pass, with a darker underside and a short body-end
  transition; and
- position three digit bands evenly but leave a visibly larger gap before the
  gold tolerance band.

The body should remain inside the existing placement/routing envelope at normal
scale. Installed and loose parts should use the same primitive with different
size parameters, not different electrical or physical identities. This is a
high-value, low-risk change if only `drawResistorBody()` and helper primitives
change.

### Diodes

**Owners and geometry.** `PhysicalPackages.AXIAL_DIODE` is placed by
`StandardPcbFootprintProviders.AxialProvider(1)` and drawn by
`StandardPhysicalPartRenderProviders.DiodeRenderer`. `DiodeNameplate` supplies
identity metadata. `PhysicalPartOrientation` and the renderer's reversed
installation checks determine which end receives the cathode band and `K`
polarity cue. Loose geometry reverses terminal display order when the installed
part is reversed, while the physical probe target still resolves to the actual
terminal endpoint.

**Current realism.** The body is a flat charcoal rectangle with a dark border.
The cathode is a silver rectangular strip at one body edge, and a `K` label is
drawn near the corresponding pad. Polarity is reasonably legible, which should
be preserved. The body has no rounded ends, cylindrical highlight, shadow, or
metallic lead transition. Installed and loose forms use the same rectangular
shortcut at different sizes.

**Drawing-only opportunity.** Use a shared axial-body primitive with a black or
near-black central rectangle and rounded end caps, then place a narrower metallic
silver cathode stripe slightly inset from the end. A narrow highlight along the
upper body edge and a darker lower edge would convey a cylinder without adding
many draw calls. The stripe must remain high-contrast and polarity-aware for
reversed installations; the existing `K` cue can remain as a secondary aid.
The pad centers, terminal order, orientation, and probe targets do not need to
change for this treatment.

### LEDs

**Owners and geometry.** `PhysicalPackages.THROUGH_HOLE_LED`,
`StandardPcbFootprintProviders.LedProvider`, and
`StandardPhysicalPartRenderProviders.LedRenderer` own the physical package,
footprint, and drawing path. `PhysicalLedPart` owns the physical LED identity,
installed/replacement backing, orientation, and `LedNameplate` metadata.
`LedSlotController` retargets the existing attachment wires and operational LED
binding when a replacement is installed.

The installed footprint has two pads at `x + 20` and `x + 60`, `y + 70`, a
90-by-100 placement, and a 60-by-60 keep-out around the nominal body. The
renderer draws leads from the mounted lead endpoints to a body centered above
the pads. The loose renderer uses tray terminal points and does not attempt to
show a powered state for a loose part.

**Current realism.** The physical lens is a solid red circular fill with a
small pink highlight and a white rectangular cathode cue. It is not visibly a
dome or translucent lens. The installed illuminated state adds a much larger
opaque yellow oval behind the red body. That aura is binary and not color
specific. The renderer calls the LED metadata adapter only for validation and
polarity; it does not use the nameplate RGB values for the body.

The normal-player evidence shows the readability benefit of the current red
body and `K` cue, but also the immersion cost: the lit part reads as a red disk
surrounded by a yellow sticker-like ring rather than a colored LED lens emitting
light. The selected-state rectangle is also the placement/hit rectangle, not a
precise lens silhouette.

**Drawing-only opportunity.** Keep pad centers, lead endpoints, orientation, and
the current lens envelope. Replace the disk with a procedural dome made from
layered, size-clamped shapes: a dark saturated body at zero intensity, an
offset specular highlight, a brighter internal core when conducting, and at
most one or two restrained color-matched halo layers at higher intensity. Use
the installed `LedNameplate` RGB values so red, green, blue, and future RGB
variants do not all render red. The first version can use solid interpolated
colors; no footprint or probe change is inherently required.

The continuous intensity plumbing is architecture-sensitive and is described
in sections 5--8.

### Electrolytic Capacitors

**Owners and geometry.** `PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR`,
`StandardPcbFootprintProviders.ElectrolyticCapacitorProvider`,
`CapacitorSpecification`, `CapacitorNameplate`, and
`StandardPhysicalPartRenderProviders.ElectrolyticCapacitorRenderer` own this
family. The package terminal IDs are `+` and `-`; the package and physical part
carry polarity semantics. The renderer draws plus/minus cues near the pads and
uses the permitted marking from the specification.

**Current realism.** The installed body is a dark blue circular fill with a
rectangular outline, a light vertical stripe, and small marking text. This
communicates polarity and a polarized component, but it does not read as a
cylindrical can: there is no distinct top ellipse, sidewall, top vent, or
controlled text placement. The loose body uses the same simplified disk-like
body at smaller scale.

**Geometry caution.** The installed renderer derives the center from pad points
and subtracts a fixed offset before applying a radius. The footprint provider's
placement and keep-out are separately defined. In the current formulas the
nominal body can extend above the placement/keep-out envelope rather than being
derived from it. This is visible in the compact RC board capture and makes a
larger can, top ellipse, or marking block more than a cosmetic change. Before
enlarging the body, reconcile the visual envelope with the footprint keep-out,
routing courtyard, selection bounds, and silkscreen validation.

**Recommended treatment.** Once the envelope is explicit, draw a central
sidewall rectangle with top and bottom ellipses, a negative stripe on the
sidewall, a small top-surface highlight/vent, and compact value/rating text.
Keep `+`/`-` terminal semantics and probe targets unchanged. If the desired can
does not fit the current placement, update package footprint geometry as a
separate, higher-risk task rather than silently drawing over adjacent copper.

### Ceramic Capacitors

**Owners and geometry.** `PhysicalPackages.RADIAL_CERAMIC_CAPACITOR`,
`StandardPcbFootprintProviders.CeramicCapacitorProvider`,
`CapacitorSpecification`, and `CeramicCapacitorRenderer` own this family. The
current provider uses a two-pad, compact through-hole placement, and the
renderer prints the `CapacitorNameplate` marking, commonly `104`.

**Current realism.** The body is a brown rectangular block with a dark outline
and text. It is readable but does not clearly distinguish a ceramic disc or
dipped radial part from a small rectangular package. Leads are plain strokes,
with no body-end transition or highlight.

**Geometry caution and recommendation.** The installed body is also computed
from pad offsets and can sit above the placement/keep-out rectangle. A disc or
dipped-body treatment can be drawing-only if kept inside the existing visual
envelope, using layered ovals/polygons and a compact marking. A change to body
diameter, lead pitch, orientation, or placement needs the footprint/keep-out
review described for electrolytics. The non-polarized terminal mapping should
not be changed for a visual update.

### Transistors / MOSFETs

**Owners and geometry.** The current physical package set contains
`PhysicalPackages.TO92_NPN` and, in the in-progress Task 38 worktree state,
`PhysicalPackages.TO92_NMOS`. Their footprints are registered separately by
`StandardPcbFootprintProviders.NpnProvider` and `NmosProvider`. Installed and
loose body drawing is in `NpnRenderer` and `NmosRenderer` in
`StandardPhysicalPartRenderProviders`. `PhysicalNpnPart` exposes B/C/E in
physical order. `PhysicalNmosPart` exposes G/D/S and explicitly maps that
physical order to CircuitJS's legacy G/S/D post order. That mapping is an
electrical/probe concern and must not be inferred from visual text.

**Current realism.** Both renderers currently draw nearly the same blue filled
circle with a square outline, a family label (`NPN` or `NMOS`), and pin letters
near the pad centers. This is easy to recognize as a three-terminal teaching
part, but it is not a convincing TO-92 package: there is no flat front, curved
rear, molded edge, lead bend, or package highlight. The current NPN/NMOS
renderer code duplicates most geometry.

The footprint has three stable pads and a relatively generous routing courtyard.
The nominal body formulas are based on the pad positions and can extend beyond
the narrower physical keep-out in the vertical direction, so a larger or more
realistic TO-92 body should be validated against both the keep-out and routing
courtyard.

**Recommended architecture.** A future package-level `To92VisualPrimitive`
can own body silhouette, front-face highlight, rear curve, lead bends, and
optional package text. The caller supplies terminal points and terminal labels
from the physical package. NPN, NMOS, PNP, and PMOS electrical semantics remain
separate in their physical specifications, CircuitJS elements, terminal
bindings, fault ownership, and probe targets. The shared primitive must not
decide transistor type or remap pins.

PNP and PMOS physical packages/render providers are not currently present in
the inspected package registry. Their eventual addition should reuse the body
primitive only after their own stable terminal IDs, package definitions,
footprints, and electrical post mappings are specified.

### Connectors / Miscellaneous

`ConnectorRenderer` handles the registered two-terminal connector and output
header packages. It currently draws a flat teal rectangle with a border and
large circular terminal graphics. The pad graphics use a light fill and dark
cross/slot-like marks, but there is no socket depth, plastic bevel, pin-1 key,
or solder transition. The connector footprint provider determines pad pitch,
escape direction, and body placement; those must remain unchanged for static
art improvements.

`MultiTerminalRenderer` is a generic/developer fallback. It draws a flat slate
rectangle and terminal circles/labels, and its loose renderer is empty. This is
acceptable as a canary/fallback but is immersion-breaking if used for a
player-visible future package. A future header or IC package should get a
package-specific renderer rather than adding more type switches to the generic
renderer.

## 4. PCB / Board Visual Audit

### Current board drawing

`PcbWorkbenchRenderer` currently draws:

- a light gray canvas and regular grid;
- a flat `#0d5b3d` rectangular board fill with a light border;
- all copper traces as the same brown/orange stroke, with a fixed minimum
  visible width;
- pads as a single gold/orange filled circle and a dark drill circle;
- silkscreen text in one of two light colors; and
- a flat gray parts tray.

The visual evidence confirms that the board is very readable for a training
simulator, but it looks like a diagram placed on a green rectangle rather than
a physical one-sided PCB. There are no board-edge thickness cues, mounting
holes, solder mask variation, copper edge highlights, trace-to-pad fillets,
solder joints, vias, component shadows, or subtle part depth. Trace width is
consistent, which is good for readability, but every trace has the same flat
color and end treatment. The reference labels are useful and intentionally
clear, but their visual treatment is uniformly flat.

### Safe board polish

The following can be added without changing route topology or probe coordinates:

- a darker/lighter inner board edge painted inside the existing outline;
- a restrained board highlight or shadow band that stays inside the rectangle;
- two-pass trace rendering using a darker edge and a lighter centerline;
- rounded copper line caps or small trace-to-pad transitions, provided the
  centerline and route points remain the same;
- layered through-hole pads: solder-mask opening/annulus, metallic ring,
  dark drill, and a tiny highlight, with the existing pad center preserved;
- small deterministic component shadows drawn before component bodies; and
- silkscreen color/weight refinements that preserve existing label bounds and
  target-pad substitutions.

Pad display size deserves care: board-pad probe hit testing uses the renderer's
fixed `HIT_RADIUS_SQ`, not the painted radius, so making a pad visually smaller
than its probe affordance can mislead players. Conversely, a large painted pad
must not obscure a neighboring pad or trace.

### Higher-risk board changes

Changing the board outline from a rectangle, moving pads, changing trace
centerlines or routing width, adding real vias, rotating footprints, or adding
backside/jumper layers affects layout generation, geometry validation, hit
testing, deterministic fingerprints, or route semantics. These should be
separate PCB-model tasks, not bundled with component shading. A visual shadow
must also respect the current draw order (traces, pads, components,
silkscreen) so it does not look like copper or block a troubleshooting cue.

## 5. Current LED Rendering Problem

The electrical and visual paths currently diverge at the operational-state
boundary.

`LEDElm` owns the actual solved `current`, the configurable
`maxBrightnessCurrent`, RGB values, and a continuous current-to-color mapping.
`GeneratedComponentOperationalStates` stores the live `LEDElm` for each bound
LED, but exposes only:

```text
isIlluminated(componentId) = led.current >= 0.001 A
```

`PhysicalPartRenderContext.isIlluminated()` calls that boolean method. The
physical `LedRenderer` then chooses between no aura and a fixed-size opaque
yellow aura. The body itself remains a fixed red fill, and the loose renderer
does not use a solver state at all.

The information loss is therefore:

```text
CircuitJS LEDElm.current
    -> LEDElm's continuous brightness calculation (used only by schematic draw)
    -> GeneratedComponentOperationalStates boolean threshold
    -> PhysicalPartRenderContext.isIlluminated()
    -> LedRenderer binary aura / fixed red body
```

The physical renderer never receives the normalized continuous value, never
uses `LedNameplate` RGB values for emission, and does not distinguish a dim
forward-biased LED from a nominally bright one. This is a presentation loss,
not an electrical-simulation limitation.

The boolean threshold should not be removed casually. It is used by generated
board validators and repair validators as a functional predicate. The correct
addition is a continuous visual accessor alongside the existing predicate.

## 6. CircuitJS LED Brightness Analysis

The exact existing calculation is in `LEDElm.draw()`.

### 6.1 Inputs and default

`maxBrightnessCurrent` defaults to approximately `0.01 A` in both constructors.
When a serialized LED contains a brightness-current token, that token replaces
the default. `colorR`, `colorG`, and `colorB` are normalized values in the
`0.0`--`1.0` range.

### 6.2 Formula

The current is normalized first:

```text
r = current / maxBrightnessCurrent
```

Only a positive ratio enters the logarithmic mapping:

```text
brightness255 = 255 * (1 + 0.2 * ln(r))    when r > 0
```

The result is clamped to `[0, 255]`. For non-positive current, the value stays
at zero and is then clamped. The RGB output is:

```text
(colorR * brightness255,
 colorG * brightness255,
 colorB * brightness255)
```

converted to integer channel values for the CircuitJS canvas renderer.

### 6.3 Behavior of the curve

With the default `maxBrightnessCurrent = 0.01 A`:

- zero or negative current produces no emitted color;
- the logarithmic expression reaches zero at approximately
  `r = exp(-5) = 0.00674`, or about `67 microamps`;
- a positive current below that ratio is visually clamped to zero;
- `r = 0.1` produces approximately `138/255` brightness;
- `r = 0.5` produces approximately `220/255` brightness;
- `r = 1.0` produces full `255/255` brightness; and
- current above `maxBrightnessCurrent` saturates at full brightness rather than
  becoming brighter without limit.

The curve is suitable as the initial TroubleshootJS baseline. It is already a
perceptual/logarithmic mapping and is tied to the solved diode current. A
second arbitrary equation in the PCB renderer would create two electrical-to-
visual truths and would make replacement, fault, and overcurrent behavior
harder to reason about.

One edge case should be documented before exposing the value: the CircuitJS
edit range permits a zero `maxBrightnessCurrent`, while the current formula
assumes a positive denominator. The default/generated path is positive, but a
future accessor should decide whether to preserve current behavior or define a
safe positive fallback for malformed/editable models. It should not silently
use absolute current, because the existing sign behavior correctly makes
reverse current non-emissive.

## 7. Recommended LED Intensity Architecture

The preferred data flow is:

```text
LEDElm.current
    -> one CircuitJS-owned brightness helper/accessor
       (the existing maxBrightnessCurrent + logarithmic curve)
    -> normalized visual intensity in [0.0, 1.0]
    -> GeneratedComponentOperationalStates.getIlluminationIntensity(id)
    -> PhysicalPartRenderContext.getIlluminationIntensity()
    -> LedRenderer
       -> installed LedNameplate RGB
       -> lens/core/highlight/halo layers
```

Recommended ownership decisions:

1. Keep the current-to-brightness formula in CircuitJS LED code. The cleanest
   small seam is a package-private `LEDElm` accessor such as a normalized visual
   brightness method, backed by a shared helper if the schematic and physical
   renderers need the same calculation. The formula must exist in one place.

2. Extend `GeneratedComponentOperationalStates` with a continuous intensity
   query while retaining `isIlluminated()`. The operational object already owns
   the live `LEDElm` binding and already supports `replaceLed()`, so replacement
   identity will continue to follow the installed solver element naturally.

3. Add a context-level intensity accessor. The physical provider should ask the
   context for visual state, not cast `PhysicalPart` to `PhysicalLedPart` and
   reach directly into a solver element. That keeps package/provider ownership
   and replacement identity intact.

4. Use the installed physical part's `LedNameplate` for base RGB. The physical
   part already carries typed render metadata and the current renderer already
   validates that metadata. The solver supplies intensity; the physical
   specification supplies the physical lens color.

5. Keep loose/removed parts at zero visual intensity. They have no installed
   board location in the current rendering contract and should remain physically
   present but unpowered in the tray.

6. Do not make the renderer advance time, alter current, or infer stress. A
   repaint should read the latest solved value. Existing CircuitJS repaint/update
   scheduling is the right place for the state to become visible.

A future generic visual-state abstraction may replace the LED-specific name if
other packages gain continuous state (relay coil, motor speed, thermal state),
but adding a narrow continuous method to the existing operational seam is the
lowest-risk first step. It does not require a generic graphics or electrical
rewrite.

## 8. Proposed Physical LED Appearance

The intensity thresholds below are conceptual presentation bands, not a second
electrical curve. The renderer should receive the normalized value from
`LEDElm` and interpolate continuously within them.

| State | Lens/body | Internal emission | Halo |
| --- | --- | --- | --- |
| OFF (`0` or effectively zero) | Dark, saturated translucent-looking colored plastic; dome silhouette and a persistent small specular highlight. | None. | None. |
| DIM | Slightly brighter lens and one faint internal luminous oval. Keep the body edge and highlight visible. | Low, color-matched core. | None or a barely visible inner ring. |
| NORMAL | Clearly illuminated colored lens with a brighter center and retained highlight. | Bright core, preferably a small warm/white mix toward the center while preserving LED color at the edge. | One small color-matched layer. |
| BRIGHT / saturated | Lens volume reads as lit rather than replaced by a flat blob; central emission is strongest. | Near-white center blended with the LED color, bounded by the lens. | One or two restrained color-matched layers; no giant opaque aura. |

The first implementation should work with the current `Graphics` abstraction by
using layered solid fills and color interpolation. The abstraction currently
has rectangles, ovals, polygons, lines, text, clipping, line width, and line
dash support, but no public gradient, alpha, line-cap, or save operation. The
underlying GWT `Context2d` supports richer operations and existing CircuitJS
renderers use it directly, but physical renderers should prefer a small shared
wrapper/primitives layer rather than each provider reaching into Canvas2D.

The existing `Graphics.fillOval()` assumes a circular shape: it uses the width
for both the arc center's vertical coordinate and radius. Current physical
uses are mostly square, so the issue is not prominent, but a future elliptical
LED highlight or capacitor top should either use a corrected helper or an
explicitly reviewed Canvas2D primitive.

Because the existing curve clamps above `maxBrightnessCurrent`, excessive
current naturally reaches full visual brightness rather than becoming
unboundedly brighter. That is appropriate for the first implementation. A
future damage system may add a derived failed/stressed visual state, but it
must be driven by solver stress/damage state and never by an independent glow
equation.

## 9. Safe Static Rendering Improvements

The following changes can be implemented as drawing-only work if the current
anchors and envelopes are preserved.

### 9.1 Shared procedural primitives

Create small package-private drawing helpers, either adjacent to the provider
or in a dedicated physical-rendering helper file, for:

- two-pass metal leads with stable endpoints;
- axial bodies with rounded/tapered ends;
- cylindrical can bodies with side/top layers;
- dome/lens bodies with highlights and bounded core layers;
- pad annulus/drill/highlight layers; and
- optional component shadows.

The provider should pass geometry and visual metadata to helpers. Helpers should
not inspect CircuitJS elements, component IDs, or fault state.

### 9.2 Component static improvements

- Resistors: rounded/tapered tan body, better band positions, lead transitions,
  and a subtle lower shadow, all using existing body left/right/y/height.
- Diodes: black cylindrical body with rounded ends, a bright metallic cathode
  stripe, and a tiny upper highlight; preserve reversed polarity logic.
- LEDs: a dark colored off-lens dome, persistent highlight, and a small flat
  front/edge cue that retains `K` readability. Dynamic brightness follows the
  intensity task rather than a new static formula.
- Electrolytics: improve only interior shading until the footprint envelope is
  reconciled; then add a top ellipse, sidewall, stripe, vent, and compact
  marking.
- Ceramics: replace the brown rectangle with a bounded disc/dipped silhouette
  and preserve `104` or other permitted marking.
- TO-92: replace the circle/square with a shared package silhouette and feed
  terminal labels from package metadata.
- Connectors: add bevels, socket/pin depth, and pin-1 cues without moving pad
  centers.

### 9.3 Board static improvements

Start with pad annuli, copper edge highlights, board-edge depth, and restrained
component shadows. These provide more physicality per draw call than adding
texture noise or photorealistic assets. Keep the current board outline,
trace centerlines, pad centers, silkscreen bounds, and deterministic layout
fingerprints unchanged.

## 10. Architecture-Sensitive Improvements

### 10.1 Continuous LED state seam

This is the first change that crosses the CircuitJS-to-physical-render boundary.
It should modify only the existing LED brightness accessor/helper,
`GeneratedComponentOperationalStates`, `PhysicalPartRenderContext`, and the LED
provider path. It must preserve the boolean validator contract and prove that
the value follows replacement `LEDElm` bindings.

### 10.2 Explicit visual envelope versus interaction geometry

The current `PhysicalPartRenderGeometry` combines terminal points, hit regions,
and selection bounds, while body pixels are implicit in `draw*()`. This works
for the current compact art but makes it easy for a richer body to outgrow its
selection/keep-out assumptions. A future bounded contract could keep:

- electrical/PCB geometry in the footprint;
- terminal/probe/hit/selection geometry in the provider geometry; and
- an explicit visual envelope or draw-only bounds for body/shadow/glow.

That would let a visual body be larger than a hit region deliberately, with
validation for overlap and readability, instead of accidentally. It is not
needed for the first axial drawing pass if bodies stay inside the current
envelope.

### 10.3 Capacitor and TO-92 footprint reconciliation

The capacitor and transistor renderers currently derive bodies from pad points,
while their footprint providers separately declare keep-outs/courtyards. A
future package-visual task should either derive the body envelope from the
footprint metadata or update both declarations together. It must rerun layout
validation, silkscreen overlap validation, route clearance checks, provider
render canaries, and normal-player screenshots across representative seeds.

### 10.4 Shared package visual semantics

`PhysicalPackage` currently defines terminal identity/connectivity but not visual
dimensions. Adding package visual metadata can be useful, but it must not turn
electrical family names into rendering dispatch. A package visual descriptor
should describe form factor, pin arrangement, polarity cues, and allowed
markings; electrical specifications continue to own CircuitJS model semantics.

### 10.5 Graphics abstraction improvements

Adding public `save/restore`, alpha, line caps, gradients, and a small path
surface to `Graphics` would make procedural depth safer and reduce direct GWT
Canvas2D coupling. It is moderate risk because the existing CircuitJS code often
uses `g.context` directly and the physical providers currently use only the
small wrapper. The initial visual pass can avoid this change with solid layered
colors; alpha/gradient support can be a separate task after the primitive API
is agreed.

## 11. Performance Considerations

`PcbWorkbenchRenderer.draw()` redraws the board, traces, pads, all components,
labels, and the tray on each CircuitJS repaint. The current art is inexpensive:
mostly one fill and one outline per feature. Layered bodies, alpha, gradients,
shadows, and glows multiply draw calls on every frame.

Recommended performance constraints:

- keep static component improvements to a small constant number of shapes per
  part;
- use at most one or two halo layers and skip them below a low intensity
  threshold;
- do not create per-pixel effects, blur kernels, or external bitmap assets;
- clamp and integer-round scaled dimensions, with a simple fallback at small
  zoom levels;
- avoid repeating expensive text or gradient construction inside loops when a
  solid fallback is adequate;
- keep dynamic LED work limited to installed illuminated parts; and
- never add a timer or solver step to the renderer merely to animate a glow.

If larger boards make full redraws expensive, a later optimization can cache
static board/trace/pad/silkscreen layers in an offscreen canvas and redraw only
dynamic parts and interaction overlays. That is a rendering optimization, not a
reason to duplicate electrical state or alter the generated layout.

Any future seeded visual variation must be derived from the existing board seed
and stable component/package identity. It must not use unseeded randomness or
change the electrical/PCB generation stream.

## 12. Prioritized Backlog

### Tier 1 — High value / low risk

1. **Axial component primitives.** Improve resistor and diode silhouettes,
   leads, bands, cathode stripe, and highlights while preserving current
   anchors, terminal points, and installed/tray bounds.
2. **LED static lens.** Replace the flat red disk with a bounded off-lens dome,
   polarity cue, and highlight. Use physical nameplate color but retain a
   binary-state compatibility fallback until continuous intensity is available.
3. **Pad and copper polish.** Layer annular pads, drill holes, trace edge/center
   strokes, and small solder-like transitions without changing pad/trace
   centerlines or hit radii.
4. **Lead and component depth.** Add shared lead shading and restrained shadows
   under bodies, with strict z-order and no interaction-state changes.
5. **Silkscreen/board surface polish.** Improve contrast, board-edge cues, and
   label treatment inside existing validated bounds.

### Tier 2 — High value / moderate risk

1. **Continuous LED intensity seam.** Expose the existing `LEDElm` mapping,
   preserve `isIlluminated()`, and pass normalized intensity through operational
   state and render context.
2. **Dynamic LED lens illumination.** Drive lens/core/color-matched bloom from
   the real intensity; validate off, dim, nominal, replacement, reversed, and
   faulted states.
3. **Shared package primitives.** Factor axial, radial-can, lens, and TO-92
   visual geometry without moving terminals or reassigning electrical meaning.
4. **Footprint-envelope reconciliation.** Align capacitor and TO-92 body
   envelopes with keep-outs, routing courtyards, selection bounds, and
   silkscreen validation before increasing package size.
5. **Graphics capability seam.** Add only the Canvas2D wrapper capabilities
   needed by the primitives, with explicit save/restore discipline and low-zoom
   fallbacks.

### Tier 3 — Later / higher complexity

1. Advanced soft bloom, lighting, and color/perceptual calibration across LED
   colors and package sizes.
2. Package-specific manufacturing details such as vents, molded seams, socket
   cavities, solder fillets, mounting holes, and backside/via representation.
3. Stress, heat, discoloration, and damage-state visuals driven by the future
   solver-derived damage system.
4. SMD packages, component rotation, multiple board layers, and richer board
   outlines. These affect placement, routing, hit testing, and deterministic
   generation and should not be smuggled into a cosmetic pass.

## 13. Recommended Implementation Sequence

The following are bounded future tasks, not work performed by this audit.

| Task | Scope | Likely files/classes | Validation and verifier changes | Conflict/concurrency |
| --- | --- | --- | --- | --- |
| A. Static axial realism | Resistor/diode body, bands, cathode stripe, leads, and shared solid-color primitives. No pad, footprint, or state changes. | `StandardPhysicalPartRenderProviders`; a new package-private primitive helper if useful; `Graphics` only if strictly necessary. | Existing `verify-renderer-boundary.ps1`; `PhysicalPartRenderDeveloperVerifier`; installed/loose browser captures for resistor and diode; confirm stable pad/terminal geometry. | High file conflict with the in-progress Task 38 edits to `StandardPhysicalPartRenderProviders`. Integrate after that file settles or isolate changes behind a separate helper. |
| B. LED intensity seam | Expose the existing `LEDElm` normalized curve; add continuous operational-state and context accessors; keep boolean validators unchanged. No visual redesign yet. | `LEDElm`; `GeneratedComponentOperationalStates`; `PhysicalPartRenderContext`; possibly a small shared brightness helper; `GeneratedBoardInstance` only if a generic seam requires it. | Pure formula tests for default/low/nominal/high/negative current; replacement binding proof; existing LED/diode/parallel/stress verifiers; ensure no solver mutation. | Mostly independent of Task 38 electrically, but LED provider integration in the next task will touch the shared renderer file. |
| C. Dynamic LED appearance | Use intensity plus installed `LedNameplate` RGB for off/dim/normal/bright lens/core/halo rendering. | `StandardPhysicalPartRenderProviders.LedRenderer`; shared lens/color helpers; possibly `Graphics`. | Visible browser captures at multiple currents/seeds; normal and replacement LED routes; reversed installation; check `K` cue and probe points; no damage behavior. | Shared renderer-file conflict with Task 38; safest after Task 38 or with explicit ownership of provider sections. |
| D. Package-envelope audit | Reconcile electrolytic/ceramic/TO-92 body formulas with footprint placement, keep-out, routing courtyard, selection, and silkscreen. Factor a common TO-92 visual primitive while preserving B/C/E and G/D/S semantics. | `StandardPcbFootprintProviders`; `StandardPhysicalPartRenderProviders`; `PhysicalPartRenderGeometry`; `PcbLayoutDeveloperVerifier`; package/specification classes only if metadata is needed. | All deterministic layout fingerprints; route/clearance/silkscreen validation; provider canaries; NPN/NMOS/RC seeds; normal-player screenshots. | High conflict with Task 38 package/footprint/provider files. Do not run concurrently in the same checkout without explicit file ownership. |
| E. PCB surface polish | Board edge, pads, trace treatment, shadows, and silkscreen refinement with unchanged geometry. | `PcbWorkbenchRenderer`; possibly `Graphics`; perhaps `PcbTraceRules` only for visual constants, not route semantics. | Renderer-boundary script; layout fingerprints; pad/probe hit checks; browser captures at small and large boards; performance sampling. | Usually independent of package work, but coordinate with presentation-layer changes if they touch canvas sizing or draw order. |
| F. Interaction-envelope contract | If richer bodies need to exceed current rectangles, make visual envelope, selection/hit geometry, and probe geometry explicit and separately verified. | `PhysicalPartRenderGeometry`, `PhysicalPartRenderHitRegion`, `PhysicalPartRenderer`, `PhysicalPartRenderContext`, `PcbWorkbenchRenderer`, render verifier. | Positive/negative hit tests, terminal probe tests, loose tray pagination, body-overlap checks, deterministic render canaries. | Architecture-sensitive and should follow, not precede, the first bounded static pass. |

Task A can be implemented independently of solver behavior once the Task 38
renderer file is no longer concurrently edited. Task B is the best candidate
for separate electrical/render-state work, but its provider-facing integration
should be coordinated with Task C. Task D should wait for all active package
additions to settle. None of these tasks should be treated as permission to
advance the roadmap automatically.

## 14. File/Class Watchlist

### Primary physical rendering

- `src/com/lushprojects/circuitjs1/client/StandardPhysicalPartRenderProviders.java` — package registration, base lead/selection helpers, and all current installed/loose component renderers.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderer.java` — provider contract for installed/tray geometry, drawing, and probe targets.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderRegistry.java` — package-keyed provider lookup and package-equivalence validation.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderContext.java` — screen/pad/tray/lead geometry and current binary illumination seam.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderGeometry.java` — terminals, hit regions, and selection bounds.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderHitRegion.java` and `PhysicalPartRenderTerminal.java` — interaction geometry records.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderMetadata.java` — typed visual specification, orientation, and loose-probe metadata.
- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderProbeProviders.java` — typed loose probe-target dispatch.

### PCB/layout geometry

- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java` — board, trace, pad, silkscreen, tray, transform, and generic hit/probe orchestration.
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchController.java` — canvas entry point and workbench repaint/UI integration.
- `src/com/lushprojects/circuitjs1/client/Graphics.java` and `Color.java` — current Canvas2D wrapper limits and color conversion.
- `src/com/lushprojects/circuitjs1/client/StandardPcbFootprintProviders.java` — package-specific pad/body placement, keep-out, and routing courtyard declarations.
- `src/com/lushprojects/circuitjs1/client/PcbFootprint.java`, `PcbComponentPlacement.java`, and `PcbPadPlacement.java` — footprint and component/pad geometry records.
- `src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java` — deterministic placement/routing generation.
- `src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java` — geometry, trace, silkscreen, tray, and overlap/route validation.
- `src/com/lushprojects/circuitjs1/client/PcbTraceRules.java` — current trace width and clearance constants.
- `src/com/lushprojects/circuitjs1/client/PcbSilkscreenLabel.java` — label bounds, baseline, font, and target pad metadata.

### Package and physical identity

- `src/com/lushprojects/circuitjs1/client/PhysicalPackage.java` and `PhysicalPackages.java` — stable package identity, terminal ordering, and declared internal connectivity.
- `src/com/lushprojects/circuitjs1/client/PhysicalPart.java` and `PhysicalPartRenderMetadata.java` — generic physical identity/render metadata boundary.
- `src/com/lushprojects/circuitjs1/client/PhysicalResistorPart.java`, `PhysicalDiodePart.java`, `PhysicalLedPart.java`, `PhysicalCapacitorPart.java`, `PhysicalNpnPart.java`, and `PhysicalNmosPart.java` — acquired physical identities, solver backing, orientation, and render metadata.
- `src/com/lushprojects/circuitjs1/client/ResistorNameplate.java`, `DiodeNameplate.java`, `LedNameplate.java`, `CapacitorSpecification.java`, `CapacitorNameplate.java`, `NpnSpecification.java`, and `NmosSpecification.java` — typed visual/electrical metadata.
- `src/com/lushprojects/circuitjs1/client/StandardPhysicalDefinitionProviders.java` — specification-to-package registration.

### LED electrical/state path

- `src/com/lushprojects/circuitjs1/client/LEDElm.java` — solved current, brightness curve, max brightness current, and RGB schematic rendering.
- `src/com/lushprojects/circuitjs1/client/GeneratedComponentOperationalStates.java` — live LED binding, replacement binding, and current boolean threshold.
- `src/com/lushprojects/circuitjs1/client/GeneratedBoardInstance.java` — generated board state access.
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java` and related family generators — creation/binding of live `LEDElm` instances and physical parts.
- `src/com/lushprojects/circuitjs1/client/LedSlotController.java` — replacement/removal retargeting for the live LED backing and operational binding.

### Verification

- `src/com/lushprojects/circuitjs1/client/PhysicalPartRenderDeveloperVerifier.java` — provider discovery, body draw canaries, terminal/hit geometry, and loose probe-target checks.
- `src/com/lushprojects/circuitjs1/client/ArchitectureDeveloperVerifier.java` — architecture route including render/provider and PCB canaries.
- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java` — deterministic layout, footprint, route, and seed-family checks.
- `scripts/verify-renderer-boundary.ps1` — static boundary guard against component dispatch in the generic renderer and missing provider/probe seams.
- `scripts/verify-browser.ps1` — normal-player and developer browser routes, including LED, diode, capacitor, NPN, and geometry evidence paths.

## 15. Risks and Open Questions

1. **Concurrent baseline.** The main worktree contains uncommitted Task 38/NMOS
   changes, including package, footprint, physical-part, provider, and
   verifier additions. This audit intentionally did not edit them. Future
   visual work touching `StandardPhysicalPartRenderProviders` or
   `StandardPcbFootprintProviders` must wait for or coordinate with that work.

2. **Capacitor/TO-92 envelope mismatch.** The current visual body formulas are
   not uniformly derived from footprint keep-outs. The first implementation
   should establish a tested visual envelope before adding larger bodies,
   shadows, or markings.

3. **Graphics API boundary.** The underlying Canvas2D can do gradients, alpha,
   transforms, and line caps, but the `Graphics` wrapper exposes only part of
   that surface. Direct `graphics.context` use would be expedient but would
   spread GWT-specific rendering knowledge through providers. Decide whether a
   small wrapper extension is warranted before advanced shading.

4. **LED color source.** `LedNameplate` already stores RGB values, while the
   live `LEDElm` also stores RGB values. Future code should define which typed
   physical metadata is authoritative for the PCB lens and verify that catalog
   replacements, fixed parts, and serialized LEDs agree. The solver must remain
   authoritative for intensity, not color-independent visual state.

5. **LED zero denominator.** The current default is positive, but the editable
   max-brightness-current range includes zero. A new accessor should define this
   malformed edge case without changing normal generated behavior.

6. **Signed current.** The existing formula emits only for positive current.
   The physical renderer must not use `abs(current)`, or a reversed/faulted LED
   could appear lit while the solver reports reverse current.

7. **Binary functional semantics.** Existing validators use the `.001 A`
   boolean illumination threshold. Continuous rendering must be additive and
   must not alter completion, complaint, or fault compatibility semantics.

8. **Small-board readability.** Procedural realism can reduce clarity at low
   zoom. Every primitive needs minimum-size rules and a readable polarity/
   terminal fallback. A physically richer body is not an improvement if it
   hides the lead or `K`, `+/-`, B/C/E, or G/D/S cues.

9. **Dynamic state and repaint cadence.** Intensity should be read during the
   existing board draw after a real solve. If a future temporal family exposes
   slowly changing LED current, validate that the current repaint/update path
   presents it without adding renderer-owned timers or fake interpolation.

10. **Determinism and validation evidence.** Visual-only changes should not
    alter layout fingerprints. If visual variation is introduced, derive it
    from seed and stable identity. Add focused visual/geometry canaries and
    browser captures rather than relying only on source inspection; the current
    verifier validates provider contracts and geometry, not pixel fidelity.

11. **No current PNP/PMOS package path.** CircuitJS contains transistor/MOSFET
    electrical classes, but the inspected physical package registry currently
    has NPN and NMOS paths only. Future PNP/PMOS visual reuse must follow
    explicit package/electrical integration rather than a renderer-only alias.

12. **Scope isolation.** This report is research only. It does not implement
    visual improvements, change the roadmap, or begin a new milestone.
