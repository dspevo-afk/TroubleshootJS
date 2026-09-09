# TroubleshootJS: Large-Board Architecture and Development Roadmap

**Edition:** 2.1, final amendment reconciliation of the approved-in-principle large-board plan
**Prepared:** 2026-09-06; amendment reconciliation: 2026-09-07
**Repository:** `dspevo-afk/TroubleshootJS`
**Destination:** `docs/ROADMAP.md`
**Owner input:** Edition 2.1 normalized UTF-8/LF SHA-256 `37114b3d32a4b04b8c00d2d939f63a9e7108b33f87ddc4dbb745a6bfc90e85be`
**Source baseline:** `3de4da1d3bad3ed532e6c327b24195bc3138ed15`, accepted Task49 on `codex/task43p-final-recovery`; direct Task48 parent `62c878b8f381e3418214a93a46d3e8d2d9693b3e`
**Task49 status:** ACCEPTED at the named SHA with its reviewed evidence, versions, catalog bounds and retained limitations.
**Current adoption state:** N00 document/lineage adoption is COMPLETE; Edition 2.1 is adopted against the accepted Task49 handoff. Repository publication follows the required final status-delta review, final document/staged checks and normal commit/push; the exact N00 commit SHA, remote verification and notification outcome belong only in the final handoff.
**Authority:** This is the adopted Edition 2.1 roadmap. It does not authorize implementation, change a branch, or certify any future capability; A01 requires separate owner authorization.

> **The destination has changed.** Routine 5–20-part boards, normal 20–40-part boards, advanced 40–60-part appliance/control boards, and a constrained but genuinely procedural approximately 100-part board are explicit targets. The old recommendation to treat 75–100 parts as unnecessary unless later justified is superseded. Algorithm choices remain evidence-driven; the product targets do not quietly disappear when an algorithm fails.
>
> **Historical preparation context:** Edition 2.1 was prepared while Task49 was active and before its final candidate was reviewed. N00 now reconciles the accepted handoff recorded below. Preserve this context as history; no earlier preparation text is a current Task49 status claim.

> **Amendment boundary:** Edition 2.1 preserves Edition 2.0's architecture, completed history, required Q60/Q100 targets, and Task49 protection. It incorporates the independent-review amendments in [I5]: honest dense-board inspection, small SMD architecture canaries, capability-conditional repair dependencies, rolling playable checks, bounded IC/MCU/display/instrument vocabulary, and staged CircuitJS import. These additions are planned contracts, not implemented features or a new source audit. A capability listed here is not automatically a release prerequisite.

## Document map

1. [Executive verdict and changed decisions](#executive)
2. [Evidence boundary and Task49 impact](#evidence)
3. [Target architecture and ownership](#architecture)
4. [Functional taxonomy and reference boards](#reference-boards)
5. [Architecture decision tables](#decisions)
6. [Scalability, model, diagnostic and test plans](#scale-plan)
7. [Milestone catalog and execution graph](#milestones)
8. [Audit reconciliation and risk register](#reconciliation)
9. [Migration, history, adoption and sources](#migration)

All sections describe a proposed architecture unless explicitly marked **CODE**, **RETAINED EVIDENCE**, or **PRIOR EXPERIMENT**. IDs A/P/U/E/D/Q/REL/X, plus the later MCU/IMPORT lanes, replace the *remaining* old task numbers; Tasks1–49 retain their identities. The migration table accounts for the former Tasks50–89. Historical evidence remains historical, not a standing instruction to repeat Task43.

<a id="executive"></a>
# 1. Executive verdict

## 1.1 Direct answers

| Question | Reconciled judgment | What would establish or falsify it |
|---|---|---|
| Can the project credibly target procedural 50–60-part appliance/control boards? | **Yes as an engineering direction, not as a proven present capability.** Constrained hierarchical composition, a causal model vocabulary, a new physical realization boundary, and scalable admission are required. | Q60 must qualify heterogeneous generated boards with distinct internal implementations, real mains/low-voltage behavior within a declared model envelope, ordinary diagnosis/repair, and measured performance. |
| Is a constrained 100-part board a reasonable stretch target? | **Yes.** It is not an arbitrary-netlist guarantee and cannot be satisfied by tiling a tiny board or drawing inert filler. | Q100 requires approximately 100 physical packages with actual purpose, generated structural variation, full correspondence, playable diagnostics, and the same declared resource policy. |
| Can CircuitJS remain the solver? | **Retain it as the working choice.** The inspected code has circuit stamping, transformer coupling, relay/inductor models, and time-domain stepping. That supports investigation, not a large-board or offline-converter performance guarantee. [R05–R08] | A07 and E06 test the difficult small models and 20/40/60/100-part solver fixtures early. Replace or restructure a narrow solver boundary only if recorded evidence defeats the required fidelity or cost contract. |
| Should the block architecture evolve or be replaced? | **Preserve the accepted identity, port, recipe, and runtime foundations; replace the bounded device-specific realization and diagnostic dispatch seams.** Do not rewrite the entire application. [R02, R09] | A04/A05/A11 must add ordinary variants and repeated blocks without new device branches in generic construction, diagnostics, routing, or UI. |
| Do we need two layers? | **Design the physical substrate for two layers now and require a real comparison prototype early.** Limited two-layer routing with explicit via costs is the preferred advanced-board path, subject to P07/P09 evidence. | Compare the same circuits under one layer, sparse crossovers, restricted two layers, and fuller two layers before committing the advanced content library. No measured success-rate claim is made here. |
| What role should factory jumpers play? | Sparse, explicitly raised routing crossovers, not a substitute for competent placement or an unlimited workaround. | P06 proves underpasses and electrical ownership. P09 freezes count/density/cost policy from matched-corpus evidence. Player repair jumpers remain different objects. |
| Biggest blockers? | Device-specific construction; centralized proof; missing durable/layer-aware copper; coordinate/view coupling; incomplete domain/model semantics; singleton-sensitive solver execution. | The early A/P foundation gates address these before 30/60-part consumers accumulate. |

**There is no honest architectural promise that eliminates all future surprises.** The purpose of this roadmap is to expose the expensive failure modes with small, falsifiable experiments before dozens of features depend on them. More development time is useful when it buys a missing contract or evidence. Time spent building speculative frameworks is not insurance.

## 1.2 What changes from the previous roadmap

The previous plan correctly protected a small playable product. It did not treat 50–60 parts and a constrained 100-part board as commitments. This edition makes the following deliberate changes:

* Hierarchical composition, explicit implementation variants, dynamic board envelopes, coherent navigation, and scalable diagnostic ownership become foundations, not optional improvements after content growth.
* Two-layer representation and a real routing/view/probe prototype move before copper-repair APIs and before advanced-board expansion. Full unrestricted two-layer search remains a choice, not a mandate.
* Source/reference semantics, stored-energy readiness, AC measurement, and the limits of simplified power-converter models move before mains-bearing content. A DC source wearing a transformer drawing is not an acceptable converter.
* Small high-risk solver/model pilots and 60/100-part structural cost probes happen early. Fully playable qualification still progresses through 15, 30, 56, and 100 physical parts.
* Production diagnostic proof is separated from developer orchestration. Proof optimization is constrained by an explicit hypothesis set and valid receipts, not by selectively forgetting difficult faults.
* Alpha is an intermediate low-voltage learning release, not the end of the product. Beta requires the advanced Q60 surface. The mature target includes Q100 and genuinely calibrated PSYCHOTIC content.
* Save/replay, event history, model versions, and long-session ownership are designed early enough to avoid rebuilding them after layers, cuts, damage, and complex inventory arrive.

This is not permission to implement every algorithm in a routing textbook. It is permission, when each milestone is authorized, to make necessary architectural changes instead of preserving weak seams merely to keep a short task list.

## 1.3 Final amendment policy: difficult boards, honest bench access

**Visual difficulty is allowed. Interaction dishonesty is not.** Dense, intimidating, cluttered-looking but electrically purposeful boards need not be comfortably readable at fit-board scale. Tracing, recognizing markings, identifying subsystems and understanding state may require real work. The player must nevertheless be able to inspect an exposed surface, magnify it, place the intended probe accurately, operate every required control, and obtain every advertised observation.

The Spacebar loupe in U01 provides temporary, cursor-centered physical inspection without changing the circuit or creating oversized invisible targets. This is inspection assistance, not diagnostic guidance: it does not identify the faulty region, label hidden nets, disclose original values, or suggest the next correct probe. Necessary markings and surfaces must become inspectable at supported magnification; an inaccessible pin or dishonest hit region is not legitimate difficulty.

Long-range ICs, basic MCU control, displays, richer instruments and import-to-challenge are now explicit lanes. Their interfaces influence foundations now; their implementation is later and tied to actual consuming content. The baseline 40–60-part qualification may use component-level repair with no E08 trace actions. Neither Q60 nor Q100 waits for MCU, display, import, signal-injection or logic-analyzer work unless its chosen family explicitly uses that capability. The required scale targets and expert-reasoning PSYCHOTIC goal are unchanged.

<a id="evidence"></a>
# 2. Evidence boundary and current-task protection

## 2.1 What was actually examined for Edition 2.0

**HISTORICAL CODE / CONNECTED DATA (Edition 2.0 preparation):** At that pinned read, the development branch pointed to Task48 SHA `62c878b8f381e3418214a93a46d3e8d2d9693b3e`. Its published report recorded fixed-value, small-layout, two-owner composition and no Task49 implementation at that time. The owner's statement that Task49 was underway governed that historical preparation context; it was not evidence that its local implementation had been reviewed. [R01]

**SOURCE INPUT:** The entire new large-board brief, the prior architecture audit, and the prior replacement roadmap were available as local attachments. The brief supplies the changed targets and required investigations. The earlier audit supplies its fourteen findings, explicit coverage limits, source index, and helper-level experiments. Neither is treated as an implementation certificate. [I1–I3]

**FRESH TARGETED READS:** This reconciliation reread relevant construction, admission, candidate filtering, fixed generator bounds, package geometry, raw trace representation, shared simulator state, transformer, relay, floating-reference, and scope-sampling code. The source ledger identifies the actual ranges. Other detailed paths already present in the conversation and the same-SHA audit were reused as prior evidence, not relabeled fresh all-source inspection. [R02–R11]

**HISTORICAL NOT PERFORMED (Edition 2.0 preparation):** No production edits, JDK8/GWT build, browser playthrough, large-board generation benchmark, 100-part circuit solve, full source sweep, local Task49 inspection, or independent subagent review was performed for that planning document. No subagents were available or represented as having run in that preparation. Container network retrieval was unavailable; connected GitHub reads supplied the source excerpts. Document dependency checks and arithmetic are not application tests. These limits do not override the accepted Task49 packet or the current N00 checks.

The earlier audit's 33-file coverage remains limited. Its extracted Java/helper experiments were not production acceptance tests. The new roadmap does not turn those limitations into a claim of whole-project correctness.

**HISTORICAL EDITION 2.1 PREPARATION:** The complete Edition 2.0 roadmap and its graph, plus the complete final-amendment brief [I5], were the basis of this revision before N00 adoption. No new repository/source investigation, runtime benchmark, Task49 inspection or independent subagent review was claimed by that preparation. The original source evidence and audit findings below retain their dates, scope and uncertainty. New engineering details are proposed acceptance contracts derived from the amendments, not newly discovered implementation facts. N00's subsequent document-preservation, dependency and coverage checks and independent review are recorded separately below as completed adoption evidence; this preparation text remains historical and does not certify implementation.

## 2.2 Preserved foundations and seams to replace

| Current foundation | Preserve | Change deliberately |
|---|---|---|
| Stable local descriptors and namespaced IDs | Logical component/terminal identities, explicit references, deterministic encoding. | Give durable device buses explicit identities; do not persist a union-find representative as the only meaning of a joined net. |
| Typed electrical preflight | Pure rejection before allocation; unknown is not compatible; references do not merge by label. | Add runtime source state, loading, sequencing, instrument reference, and isolation proof. Port metadata alone is insufficient. |
| One generated aggregate and physical inventory | One installed challenge, one authoritative mutable graph, one physical-part registry. | Constrained construction providers and private proof contexts, not one complete runtime per block. |
| Package-owned geometry | Terminal identity, mounted/loose geometry, legal interaction envelopes. | One pose/side transform; layer-aware copper access; frozen snapshots; no mutable alias behind a cached fingerprint. |
| CircuitJS measurement/solver boundary | Real electrical observations and active stimulus; functional repair/retest. | Reusable bounded stepping and observation receipts independent of UI frames; qualify floating references and instrument loading. |
| Bounded resistor transactions and fresh-owner installation | Proven compensation, disjoint owners, failure isolation. | Generalize through a second real part category before broad mutation; do not claim arbitrary same-owner rollback is already supported. |
| Named randomness and versioned replay | Exact signed seed transport and concern isolation. | Pin implementation/model/geometry/route/proof versions and accepted choices where public replay requires them. |
| Existing tests and physical correspondence | Known falsifiers, positive controls, endpoint truth. | Provider conformance and representative scale corpora rather than multiplying every seed by every feature. |

## 2.3 Task49 impact

**Disposition: accepted at `3de4da1d3bad3ed532e6c327b24195bc3138ed15`.** Task49 establishes one bounded intent-driven value-synthesis proof for the controlled-indicator LED load. Its single immutable resolved-value recipe remains compatible with the long-term construction architecture and is the input to N00 and later providers.

The accepted version boundary is `bounded-assembler@3`, `controlled-indicator@1`, `resistor-led-load@2`, `controlled-led-load-e12@1`, and geometry version 3. Task48's legacy controlled route remains `bounded-assembler@2`. Task49 admits the existing axial catalog candidates 270 ohms / 5% / 0.25 W and 330 ohms / 5% / 0.22 W. Generator2 remains fixed at 330 ohms / 0.25 W with its exact legacy meaning; named Task49 selection uses the block-scoped VALUES stream and does not reseed unrelated fault, scenario, placement, routing or presentation decisions.

The accepted contract retains exact legacy Task48 version preservation, canonical finite catalog choices, truthful ratings and supported package identity, one recipe consumed by graph, physical specification and replacement semantics, actual healthy CircuitJS validation, and no new design math in the generic assembler.

Three retained boundaries preserve the accepted Task49 scope:

1. The resolved-value result is a block contribution, not a new independent board/runtime owner. It remains transportable as immutable data into A04.
2. Original resistor values remain available through physical bands and legitimate measurement, not an added numeric original-value panel. The accepted result does not override original-value privacy.
3. The bounded load and one physical package do not establish arbitrary intent enforcement or multiple package choices. The constrained result is not a general design API.

Future review of a changed descriptor must still reject silently reinterpreting an old descriptor, independently hardcoding the selected value elsewhere, or accepting formula-only behavior. Those are boundaries on future changes; the larger roadmap is not a reason to reopen the accepted Task49 implementation.

**Eligibility follow-up resolved in A02:** Canonical admission now drives selection, counts and live proof enumeration, with explicit hypothesis identity and fail-closed population checks. The two accepted controlled candidates remain admitted and serviceable; fresh Task48/49 full-report comparisons are unchanged, so their accepted meanings are preserved. The prior compatibility/type-only mismatch remains documented in the A02 baseline evidence; A01 is not reopened.

Retained limits are one load-block value policy, two admitted generated values and one truthful axial resistor package; no manufacturing certification, arbitrary part search, support-block or Task50 implementation, PCB scaling improvement, diagnostic eligibility redesign, bend-count correction or connected-placement correction. The [accepted Task49 evidence packet](task-evidence/task-49/README.md), its [independent review](task-evidence/task-49/review.json) and the Task49 report remain the qualification source. N00 imports this exact SHA, contract, evidence and limits without inventing another approval artifact or reopening the accepted implementation.

<a id="architecture"></a>
# 3. Target architecture

## 3.1 One immutable design; one live electrical truth

```text
DeviceIntent + versioned SupportedEnvelope + exact root seed
  |
  v
Functional requirements and region relationships
  -> compatible BlockFamily / ImplementationVariant choices
  -> explicit interfaces, source/reference domains and device buses
  -> resolved values, model choices, package realizations
  -> immutable ElectricalRealizationPlan
  |
  +-> cheap semantic / physical-demand checks
  +-> private healthy solver proof
  +-> PhysicalRealizationPlan: outlines, poses, copper, vias, factory links
  +-> independent physical connectivity and accessible-target proof
  +-> bounded fault hypotheses + executable diagnostic/repair plans
  +-> proof receipts + symptom projection + difficulty assessment
  |
  v
QualifiedChallengeArtifact
  |
  v
atomic fresh-owner publication
  |
  v
ONE live challenge/runtime + physical parts + current connectivity
  -> CircuitJS -> observations -> instruments / retest / rendering
```

These are responsibility names, not an instruction to create one class per box. Use existing collaborators where they fit. A block hierarchy is a hierarchy of *design contributions*, not nested `GeneratedBoardInstance` objects. Design math may reject or propose; it cannot generate player voltage readings. Rendering may display a solved state; it cannot create connectivity.

## 3.2 Functional roles are requirements, not frozen circuits

A role states required behavior at its interfaces: operating voltage/current range, input polarity, loading, reference domains, allowable timing, protection requirements, observations, and repair affordances. A block family offers named, qualified implementation variants. Each variant has explicit assumptions and guarantees, its own local topology and values, model fidelity, physical package options, and diagnostic contributions.

For example, a low-voltage switched-load requirement might resolve to a BJT low-side stage or an NMOS low-side stage. They need not have identical gate/base inputs or diagnostic readings. Compatibility includes the adapter and drive capability needed to make each realization satisfy the same device function. Selecting a high-side implementation is not accomplished by renaming a low-side net. A five-volt supply role may resolve to a linear regulator when input/headroom/load permit it, a qualified buck model, or an isolated-converter-plus-regulator chain. These implementations are not universally interchangeable.

The device grammar decides which alternatives are valid together. It prohibits incompatible source architectures, impossible timing, unsupported references, or unsafe measurement requirements before expensive search. Optional support has a real electrical function and a documented interface effect. No random part is added merely to increase a difficulty score.

At least two genuinely different implementations and repeated instances must prove each important extension seam before it is called generic. Avoid a universal circuit language: use typed Java data/contracts and explicit providers compatible with the existing build. Registry bootstrap may change when adding a provider; generic engines should not acquire provider-specific branches.

## 3.3 Ownership map

| Concern | Authoritative owner | Forbidden second authority |
|---|---|---|
| Functional intent and permitted device topology | Device grammar / requirement resolver | Renderer or scenario text choosing electrical topology. |
| Local electrical implementation | Versioned block/element provider through a constrained construction context | A block privately publishing a second board, source registry, inventory or fault engine. |
| Global IDs and explicit connections | Device namespace / bus resolver | Lexicographic representative, collection index, physical position or solver node number as durable ID. |
| Resolved part value/rating | Immutable recipe from Task49 and later compatible providers | Independent constants in assembler, markings, replacement catalogs and behavior checks. |
| Pristine conductive structure | Versioned physical realization with conductor graph and provenance | A net label pretending two disconnected pieces of copper are joined. |
| Current connectivity after actions | Mutation-owned conductor/part state, projected to the one live solver | Renderer deciding a cut electrically, or UI state directly toggling repair success. |
| Electrical behavior | CircuitJS element/model implementations | External ideal supply or scripted animation making the board appear functional. |
| Model fidelity and exposed observations | Versioned model contract plus independent qualification | A hidden averaging shortcut that fabricates switching waveforms or omits visible parts. |
| Probe accessibility | Package/surface definitions transformed into the active view | Oversized invisible hitboxes or debug coordinate shortcuts. |
| Diagnostic eligibility and proof | Production hypothesis/plan service | Family-specific developer switch, hidden target ID, or timing-dependent sampling as proof. |
| Evidence reuse | Immutable, complete-key proof receipt | Sharing mutable candidate graphs, trusting a stale PASS, or hashing only the root seed. |
| Session/history/save | Session coordinator and versioned semantic state | Serialized solver matrix/node identities or a second inventory in the Shop. |

## 3.4 The three graphs must be explicit

The **logical design graph** records intended component terminals and bus relationships. The **physical conductor graph** records actual pad surfaces, copper edges, plated barrels, vias, and factory connections. The **live solver graph** is the electrical realization of current part and conductor state.

They are related views, not interchangeable structures. Their transformations require provenance and correspondence checks. A pristine connected bus can split after a trace cut without renaming every original component or pretending the original bus name still guarantees conductivity. A repair can reconnect it by a different allowed path. Union-find is useful for deriving current connected components; its root is not a permanent repair identity.

Before trace cutting, conductor identity must survive harmless redraw, pan, zoom, serialization and lossless path subdivision. Saved actions must carry the physical realization version/fingerprint and a semantic segment/locus identity. A later reroute that changes the physical conductor network requires explicit incompatibility handling or a validated mapping; nearest-pixel repair migration is forbidden.

## 3.5 Solver isolation and scheduling

**CODE:** `CircuitElm.sim` is static; existing scope code also reads `CirSim.theSim`. Constructing two Java objects in the same realm does not prove two independent simulators. [R05, R08]

A07 initially exposes serialized, private proof execution through a narrow adapter around the existing backend. Each proof has a request identity, immutable inputs, owned elements, bounded stepping, explicit completion/failure, and cleanup. It cannot advance the player's live circuit or publish stale state. Immutable placement/routing candidates may later be evaluated in parallel if the environment supports it. Parallel live CircuitJS candidates require independent execution realms or proven instance-scoped state, and their own qualification.

Do not assume that moving the existing compiler output into a Web Worker makes DOM-linked GWT code worker-safe. Worker extraction, separate browser contexts, or backend isolation is a conditional engineering decision after the dependency inventory and serial benchmark. Task41's accepted fresh-candidate/untouched-owner discipline remains valid until a replacement earns equivalence evidence.

## 3.6 Time, energy, and fidelity are part of the contract

The simulation clock, user-interface clock, generation-work counters and observation windows are different. A slower computer may yield more often or fail a resource deadline; it must not silently choose a different circuit, fault, event schedule or accepted observation.

Models declare what they preserve: static I–V behavior, loading, startup, dropout, stored energy, control response, failure behavior, relevant bandwidth, and observable terminals. A macro-model is acceptable inside CircuitJS when its terminal currents/voltages and state evolve causally from the actual electrical inputs. Qualification must cover load variation, loss of input, enable, residual energy, protection and the faults advertised for that model.

An averaged converter cannot claim a resolved switching waveform. A block-level abstraction cannot display a dozen individually probeable, removable external parts while ignoring their state. Either those visible parts causally participate in the model, the diagnostic vocabulary is explicitly limited and independently proved, or the abstraction is represented honestly as one packaged module. Package count does not count hidden mathematical elements and cannot be padded with decorations.

## 3.7 Versioned artifacts, not an eternal frozen application

A qualified challenge pins the descriptor schema, device grammar, block implementations, named-stream derivation/revisions, value policy, electrical model set, package geometry, placement/router policy, physical realization, fault library and proof semantics needed for its supported meaning. They need not all be independent top-level fields immediately; one versioned manifest can reference them. The requirement is complete interpretation, not a large number of version numbers.

Stable identity is distinct from stable selection. Adding a new eligible implementation or fault can change a new generator's selection distribution. It cannot silently change an old supported version. Preserve the old resolver or reject it explicitly according to the published support policy. Semantic equivalence does not require pixel-identical cosmetic shading.

For public reproducibility, preserve both the request and a digest of the resolved artifact; persist the resolved choice manifest where regeneration alone would be fragile. Floating-point solver results use declared tolerances and physical predicates, not an unsupported promise of bit-identical trajectories across every browser.

## 3.8 Forward-compatible IC, state and capability contracts

The long-range electrical vocabulary uses the same role → family → implementation → resolved recipe → physical realization chain. An IC is a physical package with declared electrical pins, not a shortcut around power, loading, probing or repair. A provider may implement several functional units inside one package, but it must state the shared power/reset/clock relationships and unit-to-pin correspondence. A quad device or resistor network is counted as one physical package when represented as one; separate schematic units cannot silently become separately replaceable packages.

A powered logic output is not an unconditional Boolean voltage source. Its model declares supply/reset dependence, thresholds, drive topology, finite drive/loading, input behavior, output limits and the supported power-loss/brownout envelope. Analog ICs likewise declare supply and input/output operating limits, saturation and reference dependence. Unsupported common-mode, timing or loading conditions cannot be converted into falsely precise readings. Exact commercial-part fidelity is not presumed from a familiar functional label such as “555-style” or “ULN2003-style.”

Stateful providers participate in A07's simulation-time execution, A08's lifecycle and U06's explicit state schemas. Their state transitions occur at the accepted electrical/event boundary; repeated nonlinear trial iterations cannot accidentally advance a counter twice. Internal clock phase, pending semantic events, reset priority, startup initialization and event-tie ordering are part of the versioned model contract. Feedback that cannot settle within the finite event budget rejects or reports a supported numerical failure, rather than spinning indefinitely or sampling browser frames.

These compatibility requirements belong to foundation contracts now. E09–E14 and MCU-1/MCU-2 implement the actual later vocabulary. Synthetic shape/event canaries do not qualify a real microcontroller, and the foundation does not wait for a finished MCU, display or importer.

## 3.9 Imported designs converge on native challenge services

```text
CircuitJS file bytes + source/content hash + parser/import version
  -> isolated parse/load interpretation
  -> element capability classification + ambiguity report
  -> author-approved import manifest: sources, controls, outputs, healthy intent
  -> stable logical design + package/unit/terminal mappings
  -> the SAME native resolved-plan and healthy-verification services
  -> the SAME physical placement/routing/layers/correspondence services
  -> the SAME supported fault/diagnostic/repair/retest services
  -> qualified challenge artifact + replay provenance
  -> the SAME fresh-owner publication and player workbench
```

The importer is an input adapter and authoring boundary, not a second electrical solver, fault engine, board runtime, PCB renderer or inventory. Reuse/adapt the real CircuitJS parser/load semantics where possible, but isolate them from the player's installed graph. Parsing must not execute an embedded script, fetch arbitrary external resources or replace a live owner as a side effect.

Imported schematic coordinates may be inputs to recovering CircuitJS's electrical connectivity during interpretation. They are not automatically physical PCB positions and cannot be retained as the durable identity of components, pins, nets or repairs. A deterministic import-local identity assignment is persisted in the accepted manifest. Exact content replay reuses that mapping; changed files receive an explicit new interpretation or reviewed migration, not a guessed cross-version identity match. Symmetric/duplicate elements and ambiguous multi-unit packages need explicit canonicalization or author annotation rather than unstable array-order naming.

A solved imported circuit is not automatically a meaningful challenge. The author identifies or accepts unambiguous suggestions for sources, controls, customer states, outputs and healthy expectations. The importer must not manufacture a customer's intended function from whatever measurements happen to be present. Unsupported electrical, physical, serviceability or functional contracts produce structured capability results before challenge publication. Section 6.11 and IMPORT-1 through IMPORT-5 define the bounded implementation sequence.

<a id="reference-boards"></a>
# 4. Functional taxonomy and permanent reference boards

## 4.1 Initial implementation vocabulary

| Functional role | First useful variants | Assumptions that distinguish them | Required proof before general selection |
|---|---|---|---|
| Load switching | BJT low-side; NMOS low-side; later PNP/PMOS high-side | Drive polarity, source/sink capacity, voltage domain, load type, clamp path. | Same end function with materially different topology; no central branch; drive/thermal margins; legal diagnosis. |
| Rail generation | Linear regulator; bounded buck; isolated offline stage plus regulator | Input headroom, load, isolation, startup, efficiency model and bandwidth. | Input/load/enable/fault sweeps; no power from nowhere; no implicit return merge. |
| Input protection | Reverse diode; qualified MOSFET reverse protection; fuse/clamp/filter variants | Loss, current bounds, direction, surge/fault vocabulary. | Intended healthy effect and supported overload behavior; protection is not an unconditional safety flag. |
| Relay control | BJT coil driver; MOSFET coil driver; later qualified integrated driver | Coil current/energy, suppression, contact topology and reference isolation. | Coil/contact separation, pickup/dropout, both driver implementations, physical fault/repair ownership. |
| Sensor conditioning | Divider + filter; bridge/differential stage; qualified active conditioning | Input impedance, reference, sensor stimulus and operating range. | Player-operable stimulus; loading/reference sensitivity; at least two meaningful configurations. |
| Threshold/interlock | Comparator with pull network; Schmitt/hysteresis variant; bounded logic interlock | Supply state, output drive, thresholds, feedback and timing. | Sweeps, hysteresis if advertised, missing-reference/partial-power cases, repair/retest. |
| Indication | Resistor LED; transistor-buffered indicator; supply-present monitor | Current target, polarity, loading, dependency on a real rail. | Original marking truth; state-dependent observation; not a unique visible fault marker. |
| Timing | RC/triggered timer; qualified oscillator with frequency measurement | Actual timing components, supply range, reset/trigger and instrument bandwidth. | Solver-time observations and suitable player instruments, not configured-value readout. |
| Current sensing | Shunt + voltage observation; qualified amplified/differential stage | Common mode, burden, return path and measurement range. | Actual branch current relationship and meter interaction. |
| Interconnection | Keyed headers; bus joins; isolation barrier; factory crossover | Pin order, conductor layers, permitted domain joins, physical access. | Exact terminal mapping, underpass/barrel/via semantics, no labels-as-connectivity. |

The taxonomy is deliberately finite. A role may initially have one qualified implementation but must not pretend to offer interchangeability. No integrated implementation is counted as dozens of components because its internal model has dozens of elements. New package pin counts must be supported through explicit terminal contracts, not another special-case six-pin ceiling.

## 4.2 Counting rule

A physical component is a distinct mounted package with a physical identity and a real role: connector, resistor, capacitor, diode, transistor, IC, relay, transformer, fuse, or explicit factory link. A packaged bridge rectifier counts as one; four discrete diodes count as four. A plated via is tracked separately, not used to inflate the component total. External bench sources/loads, internal model elements and temporary meter stimuli do not count as PCB components. Loose inventory is measured separately.

The counts below are **TARGET allocations**, not schematic designs, manufacturing BOMs, proven generated netlists, or benchmark measurements. Exact component choices and values are resolved and qualified in the owning electrical milestones. Every entry must receive a unique identity and a purpose in the actual fixture manifest.

## 4.3 RB15: approximately 15-part low-voltage control board

A 12 V bench-fed control board with input protection, a sensor/interlock input, one switched relay load, and a status indicator. Use two alternative driver implementations and at least one useful support variation.

| Functional allocation | Physical parts |
|---|---:|
| Input connector, fuse, reverse protection and input capacitor | 4 |
| Driver, drive resistor, bias resistor, flyback diode and relay | 5 |
| Sensor connector and two passive conditioning components | 3 |
| Indicator LED and limiting resistor | 2 |
| Output connector | 1 |
| **Total target** | **15** |

For this first fixture the sensor/interlock is an externally player-operated low-voltage stimulus with passive conditioning; it does not assume the later comparator library. This is the first real repeated/replaceable provider proof, not merely a larger resistor ladder. Q15 covers generation, physical access, live control, two plausible fault owners, repair and retest. An architecture fixture may initially use existing component backings without claiming that every future fault is supported.

## 4.4 RB30: approximately 30-part multi-rail control board

A low-voltage board with a 12 V source, 5 V regulated/control rail, two sensor channels, two output drivers, indication and real connectors. The allocation is 4 entry/protection parts, 4 regulator/filter parts, 8 sensor-conditioning parts, 10 dual-driver/output parts, 2 status parts and 2 additional connectors: **30 physical packages**.

Required structural variants include alternative BJT/MOSFET driver populations and at least two accepted sensor/reference arrangements. The 5 V rail must be produced by the modeled regulator, not an unrelated external ideal rail. Q30 qualifies 20–40 parts as normal procedural content, including partial-power and loading cases. It is not satisfied by merely instantiating RB15 twice with no interaction.

## 4.5 RB56: the 50–60-part appliance/control north star

```text
simulated 120 VAC input
  -> entry protection / inrush / filtering
  -> bridge rectification and bulk storage
  -> qualified isolated conversion
  -> 12 V secondary rail
       -> relay drivers and switched external loads
       -> 5 V regulation
            -> sensor conditioning, references and interlocks
            -> controls and indication
```

| Region and allocation | Physical parts |
|---|---:|
| Mains connector, fuse, clamp, inrush and input-filter component | 5 |
| Packaged bridge, bulk capacitor, discharge path and high-frequency bypass | 4 |
| Offline primary/control/magnetic/clamp/bias implementation | 12 |
| Isolated feedback and output-sense implementation | 4 |
| Secondary rectification and output filtering | 3 |
| 5 V regulator and local input/output storage | 3 |
| Two relay-driver channels, five packages each | 10 |
| Two sensor-interface channels, four packages each | 8 |
| Decision/reference circuitry | 3 |
| Status indicator pair | 2 |
| Two load/output connectors | 2 |
| **Total target** | **56** |

The 12-part conversion allocation is a *budget for a real topology*, not authority to draw twelve generic shapes around a behavioral 12 V source. E06 must decide whether individually represented switching/magnetic/control parts retain credible observations under an averaged model. Any part whose removal, parameter change or terminal probing is advertised must matter. An opaque converter module is a legitimate separate implementation variant but counts as one package; it cannot be used to claim this 56-part fixture without reallocating the remaining real functions.

The primary return, isolated secondary return and protective-earth/chassis concepts are separate. Not all implementations have all three. Unconnected chassis symbols are not convenient ground supplies. Both primary and secondary board faces, meaningful insulation regions, polarity, residual charge and permitted instruments are part of qualification. This is simulated educational circuitry, not a construction or safety-certification design.

Two different internal architectures must be capable of similar customer complaints. For example, loss of load operation can involve the supply chain, control/interlock or output stage. The complaint is derived from a solved customer operating sequence, not from the chosen culprit. A candidate that requires guessing among unrepaired indistinguishable causes is rejected or placed in a genuinely equivalent repair class.

## 4.6 RB100: constrained approximately 100-part stretch board

Start with the functional architecture of RB56, not a copied board image, and add individually justified subsystems:

| Additional function | Added packages | Running target |
|---|---:|---:|
| RB56 functional base | 56 | 56 |
| Two additional relay/output channels | 10 | 66 |
| Two DC power-output channels with drive/protection | 12 | 78 |
| Two more sensor/conditioning channels | 8 | 86 |
| 3.3 V domain and local conditioning | 4 | 90 |
| Current-sense/monitoring function | 4 | 94 |
| Independent healthy auxiliary/interlock function | 4 | 98 |
| Additional interface connectors | 2 | 100 |

The eventual recipe may redistribute these counts while remaining approximately 100 real packages. It must include heterogeneous package sizes, multi-pin parts, shared rails, nontrivial fan-out, real cross-region dependencies and multiple accepted implementation choices. At least two generated internal realizations must differ structurally while preserving the specified end function. Parameter jitter and duplicated identical channels alone are inadequate diversity evidence.

Q100 is a required mature-target qualification, not an optional benchmark that can be silently removed. It does not force all normal play to use 100 parts and does not claim every conceivable 100-part netlist will route. Limits on domain count, package mix, links, vias, temporal bandwidth and hypothesis population are published with the supported envelope.

## 4.7 Each reference board advances through independent evidence states

| Evidence state | Meaning | First owner / later confirmation |
|---|---|---|
| Architecture fixture | Counted roles, interfaces, domains, model/fault requirements and failure expectations are specified. | A01 specifies all four early. |
| Structural/generation fixture | Actual immutable recipe and global identities are produced, including invalid cases. | A04/A05; Q15/Q30/Q60/Q100 progressively qualify real content. |
| Physical routing fixture | Actual package geometry, net membership, routing policy and layer/crossover evidence exist. | P03–P09 include reduced and full-count structural probes before the full electronics library exists. |
| Solver fixture | Real or explicitly bounded model-backed circuit is analyzed and stepped; cost/fidelity recorded. | A07/E06 run small difficult pilots and 20/40/60/100 aggregate loads early. |
| Diagnostic fixture | Selected hypothesis population, legal observation policy and reachable repair/retest are proved. | A09/D01; qualification gates integrate exact content. |
| Playable qualification | Normal UI, correct markings, navigation, inputs, instruments, repair, save where advertised, and resource budgets pass. | Q15, Q30, Q60, Q100. |

A physical-only 100-part fixture is useful early evidence, but it is not an electrically generated or playable 100-part board. A solver-only 100-part fixture is not a PCB. Evidence labels must never collapse these distinctions.


---

## 4.8 Long-range component and instrument coverage

This table accounts for the requested vocabulary without creating a milestone for every part. Each entry is a family-level plan, not a promise that every named device is currently implemented. Existing smaller milestone scopes stay intact; later families consume their contracts. An entry does not become a Q60/Q100 prerequisite unless the qualified family uses it.

| Capability group | Explicit coverage | Placement and qualification boundary |
|---|---|---|
| Passive parts and networks | Resistors, resistor networks, capacitors/electrolytics, inductors, chokes, transformers, potentiometers/trimmers, thermistors, MOVs and fuses. | Existing E01/E02/E04/E05 plus later E09/E12 models and X02 only when damage behavior is advertised. Distinguish one packaged network from independent replaceable parts; no inert filler. |
| Basic semiconductor and protection families | Standard and Zener diodes, TVS/clamp devices, bridge rectifiers, BJTs and MOSFETs. | E01–E06 retain their bounded content. Later provider additions must qualify thresholds, polarity, limits, loading and fault effects actually modeled. No full transient/EMC claim. |
| AC switching | SCRs, TRIACs, optotriacs, optocouplers, zero-cross sensing/control; heater, AC motor/solenoid and isolated AC switching examples. | E13 after source/reference, AC and instrument contracts. Each variant states latching/commutation/load limits; not required for a relay-switched baseline Q60. |
| Small analog ICs | Comparator, Schmitt comparator, op-amp, follower/buffer, amplifier, threshold/reference and useful active-filter functions. | E04 remains the initial bounded proof; E09 adds an explicit powered analog-IC family. Input/output/supply envelope and saturation are qualified; no invented rail-to-rail behavior. |
| Combinational logic | Inverter, AND, OR, NAND, NOR, XOR and Schmitt-trigger functions. | E09. Actual supply/input/output pin behavior, finite loading and powered/unpowered semantics; no universal HDL. |
| Sequential logic and clocks | SR latch, D flip-flop, useful JK flip-flop, counters, simple shift registers; crystals, ceramic resonators, oscillator modules and RC clocks. | E10. State, clocks, reset/set, initialization, partial power and timing are deterministic and instrument-observable. Crystals are not decorative and RF fidelity is not promised. |
| Timing | 555-style monostable/astable/triggered configurations; transistor/RC, comparator/Schmitt and qualified alternate IC implementations. | E07. Reset/enable, threshold/control pins and surrounding timing components are causal within each qualified configuration. |
| Specialized small ICs | Transistor-array/ULN2003-style drivers; analog switches; mux/demux; voltage references; simple current-sense amplifiers; optocouplers/isolated digital interfaces; LED/display and simple power-driver functions; reset/brownout supervisors. | E12 demand-selected provider groups. Their acceptance is local to the selected function; no blanket catalog implementation or Q60 prerequisite. |
| Basic microcontrollers | Power/reset/brownout, GPIO direction/threshold/loading, pulls, limited ADC, PWM/timers, deterministic startup and bounded appliance-control behavior. | MCU-1 compares implementation methods; MCU-2 qualifies the chosen bounded runtime and electrical board integration. No arbitrary firmware/IDE or hidden-firmware puzzle. |
| Displays | Single-digit common-anode/common-cathode seven-segment LEDs, segment pins/current limits and justified segment faults; later multi-digit scanning, drivers, status codes, bargraphs or indicator arrays. | E11. Display output comes from actual segment currents and simulation-time scan behavior, not scenario text. Graphical LCD/OLED remains outside the baseline plan unless a later product need justifies it. |
| Sensors | Thermistor, photoresistor, Hall-effect, reed/limit switch, pressure abstraction, potentiometric position and simple current-sense input. | E04 extensions and E12 where a powered IC is needed. Required conditions have normal player stimuli; failure hypotheses distinguish sensor, supply, reference and interconnect. |
| Machine/user inputs | Pushbuttons, toggles, DIP switches, useful rotary selectors, configuration jumpers, connectors and headers. | A05/E04/U04 with source/operation contracts. Configuration is a real modeled connection/state, not an answer selector. |
| Loads and outputs | LEDs/displays, buzzers, relay coils/contacts, solenoids, DC motors/fans, heaters and generic external loads. | E01/E03/E11/E13/E14. Off-board loads remain connector-bound external objects and never inflate PCB component counts. |
| Reversing/motor control | Relay reversing, discrete BJT/MOSFET H-bridges and a simple packaged motor driver. | E14. Diagnose supply, drive, current path, protection, direction, enable and load response; no detailed commutation or arbitrary motor firmware. |
| Power and references | Simulated 120 VAC, rectification/bulk storage/isolation/offline conversion, 24/12/5/3.3 V domains, LDO/buck variants, protection/current limits, partial power/backfeed, reference devices and useful reset supervisors. | Preserve A06/A07/E01–E06. E12/MCU providers add narrower contracts when used; no claims of construction safety or unmodeled converter dynamics. |
| Interconnect and physical substrate | Terminal blocks, headers, keyed/ribbon-style multi-pin connectors, real raised factory crossovers, vias, top/bottom copper and surface pads. | P01/P02/P06/P07; actual cable/harness use needs explicit external connectivity. Factory crossovers remain distinct from E08 player repair wires. |
| Bench instruments | Existing/planned DC V, AC V, resistance, continuity, diode, capacitance when justified, frequency and scope; later two channels, conditional logic capture, injection, current insertion and ESR. | U02/U03/U09–U12 and X08. Every instrument states electrical interaction, range, reference, limitations, cleanup and physical operation. Listing is not enablement. |
| Community circuit content | Broad but explicitly supported CircuitJS import, including sources/controls/loads, package mapping, healthy intent, faults and replay. | IMPORT-1–IMPORT-5 use the native architecture and publish a capability matrix. Unsupported files get reasons, not silently altered netlists. |

## 4.9 Optional evolution of the reference-board families

The RB15/RB30/RB56/RB100 base allocations and count rules above remain unchanged. The following are additional future variants, not replacements for their primary qualification fixtures and not mandatory additions to each board. Each adopted variant receives its own manifest, actual package count, model/instrument bundle and qualification receipt at the corresponding Q gate.

| Future variant | Purpose and contents | Additional consumed capabilities |
|---|---|---|
| RB30-CONTROL | Add a comparator/interlock or 555-style timing alternative to the existing low-voltage sensor/output purpose. | E09 for powered IC implementation, E07 for timer configurations, E10 only for stateful logic used; U03 where time observations are required. |
| RB56-LOGIC | A discrete/logic-controlled appliance implementation with powered latch/flip-flop/interlock behavior and an encoded status output. | E09/E10; E11 for a seven-segment status display or another declared status-output provider; E07 only if selected timing uses it. |
| RB56-MCU | Bounded MCU with real sensor inputs, relay/MOSFET outputs and seven-segment status; power/reset/clock/supply defects remain plausible external causes. | MCU-2, E11 and the actual source/sensor/driver providers. No firmware decompilation; tools selected from the proven observation plan. |
| RB100-MIXED | Optional mixed analog/logic/MCU/multi-rail/relay/AC-control realization, structurally different from the base recipe. | Only the E09–E14/MCU/instrument capabilities actually selected. It cannot turn those into prerequisites for the base Q100 family. |

At least one future variant in this lane must exercise a stateful logic provider, an explicit IC package, a display or encoded status output, and alternate implementation families. E10/E11/MCU-2 maintain that coverage as their later integration deliverables. A variant can satisfy it without an MCU. The existence of this future demonstration does not block the baseline Q60/Q100 or change their required-target status.

<a id="decisions"></a>
# 5. Required architecture decisions

## 5.1 One-layer versus two-layer strategy

The scores below are **ESTIMATED architectural judgments**, not measured routing rates. Five is favorable, one is unfavorable. For complexity rows, a higher score means *simpler to implement and qualify*. The comparison assumes a competent implementation of each strategy on this project's intended structured boards, not arbitrary nets. P07 replaces these provisional expectations with recorded matched-corpus evidence; it may change the scores without changing the 60/100-part targets.

| Dimension | One layer only | One layer + sparse factory crossovers | Restricted two-layer, penalized transitions | Fuller two-layer search |
|---|---:|---:|---:|---:|
| Expected routing headroom | 2 | 3 | 4 | 5 |
| Implementation simplicity | 5 | 3 | 2 | 1 |
| Rendering simplicity | 5 | 4 | 3 | 2 |
| Player comprehensibility | 5 | 4 | 4 | 3 |
| Probe/access simplicity | 5 | 4 | 3 | 3 |
| Trace-cut semantics simplicity | 4 | 3 | 2 | 2 |
| Repair semantics simplicity | 4 | 3 | 3 | 2 |
| Fault-model integration simplicity | 4 | 3 | 3 | 2 |
| Expected total performance potential | 3 | 3 | 4 | 3 |
| Deterministic implementation feasibility | 5 | 5 | 5 | 5 |
| Plausible control-board appearance | 4 | 4 | 5 | 5 |
| Usefulness for heterogeneous 50–60 parts | 2 | 3 | 5 | 5 |
| Usefulness for constrained 100 parts | 1 | 3 | 4 | 5 |

**Recommended path:** preserve the old one-layer resolver; introduce an explicit two-layer-capable conductor/surface model; implement correct one-layer routing and sparse crossover prototypes; compare restricted and fuller two-layer prototypes; freeze a hybrid policy. Routine small content should prefer readable one-layer boards. Advanced content should normally allow two copper layers with a cost for vias and unnecessary transitions. Full two-layer search should be enabled where it earns better success/quality/cost results than the restricted policy.

A hybrid policy selects a supported fabrication/inspection style from the versioned content envelope. Difficulty may restrict permitted layout styles or assistance, but may not hide the board's only required observable surface or make a fault impossible to reach. A user must be able to inspect either physical face. An optional translucent opposite-side overlay is a view aid, not a new conductor or a secretly probeable front-side object.

Two layers do not double the number of components that can fit. Package area and human access still constrain placement. At a fixed grid, adding a second layer approximately doubles layer-indexed search states and introduces transition edges; fewer detours or retries might offset that cost. That is a **CODE-DERIVED model expectation**, not a runtime measurement. Independently routed layers still require a single combined connectivity/ownership proof.

External grounding: KiCad's documented model distinguishes tracks on copper layers, plated through-hole pads, non-plated holes and vias. That supports the physical primitive vocabulary here, not a proposal to import its entire editor or manufacturing rule system. [W1]

## 5.2 Functional block architecture comparison

| Concern | Current bounded path | Proposed mature boundary |
|---|---|---|
| Meaning of a block | Known contribution shapes plus device-specific construction. | A named electrical purpose with qualified implementation variants. |
| Repeated instances | Explicit driver/load/source names in a bounded plan. | Stable device-assigned role instance keys; local IDs remain unchanged under insertion/reordering. |
| Values | Task48 constants; Task49 supplies the accepted resolved recipe. | One immutable resolved recipe, consumed by construction, physical specs and catalog expectations. |
| Electrical construction | Generic-named assembler knows concrete devices. | Block/element provider emits typed construction contributions through one owned context. |
| Interconnection | Explicit proposed joins and union aliases. | Typed device buses and explicit inter-domain adapters; union representatives are derived. |
| Physical layout | Small authored factory or flat generic layout. | Device-owned hierarchical realization using provider-local shape/escape constraints. |
| Diagnostics | Central family-dependent proof and action catalogs. | Provider plans plus a device-level production observation/repair admission service. |
| Variants | New branches and coordinated integration. | Registry-owned implementations with declared assumptions/guarantees and conformance tests. |
| Failures | A mix of typed contracts and broad runtime exceptions. | Typed stage outcomes; unexpected exceptions are defects, not routine retry instructions. |
| Extension success | A small fixture passes. | An independent ordinary variant is added without generic-engine edits and passes conformance plus representative integration. |

No design is declared generic because it accepts a `Map<String,Object>` or has a provider interface. Ordinary new block work must actually avoid shared construction/diagnostic surgery. A genuinely new physical device model still legitimately needs electrical, package, mutation, observation and qualification work.

## 5.3 Placement strategies

| Strategy | Benefit | Failure mode | Roadmap disposition |
|---|---|---|---|
| Flat global placement | Simple whole-board freedom; useful baseline on small graphs. | High-fan-out attraction can swamp functional structure; search grows; important connectors and domains become awkward. | Retain as baseline and possible local refinement, not the sole advanced algorithm. |
| Region-based placement | Constrains power, isolation, connectors and service access. | Rigid boxes waste space or strand cross-region nets. | Foundational regional constraints, with movable boundaries and quantified interface demand. |
| Hierarchical placement | Select major regions, then blocks, then package poses; limits global search. | A locally optimal region may create impossible interface routing. | Preferred primary strategy with explicit interface/escape capacity and coarse global feedback. |
| Authored templates | Reliable topology-specific physical proof and benchmark controls. | A template library can become a family clone collection and mask general failure. | Retain local patterns and reference controls; do not count a fixed full-board template as general procedural qualification. |
| Hybrid hierarchy plus bounded refinement | Reuses local engineering while allowing meaningful board-level variety. | More phases and contracts; stale geometry or nondeterministic refinement if ownership is weak. | Recommended. Finite aspect/anchor/pose alternatives and controlled placement feedback, all versioned. |

The placement problem uses nets as hyperedges or explicit bus demands, not the pairwise clique in the existing topology graph as a planarity oracle. A net with many pads is one conductor network, not a requirement to draw an edge between every pair. Coarse inter-region channel demand must be checked before committing all package placements.

## 5.4 Routing strategies

| Technique | What it addresses | Classification / decision | Required falsifier |
|---|---|---|---|
| Existing sequential router | Small one-layer routes with fixed grid/escapes. | Preserve as a versioned baseline. | Known infeasible case remains a bounded rejection; source changes do not silently reinterpret old output. |
| Canonical order and constrained-net priority | Avoid easy routes consuming scarce escapes/channels first. | Foundational. Net role comes from metadata, not a name containing GND. | Reordered declaration input produces the same result; constrained escape fixture improves without hidden shorts. |
| Multi-terminal trees/trunks | Excess star length and duplicated high-fan-out branches. | Likely needed; bounded tree/MST-like heuristics before advanced optimization. | Every pad connects; overlapping same-net branches are counted once in actual copper metrics. |
| Escape/channel planning | Package exits and region bottlenecks. | Foundational for larger heterogeneous boards. | A plan with adequate area but impossible interface capacity rejects before detailed routing. |
| Bounded rip-up/reroute | Earlier legal routes blocking later nets. | Planned capability with fixed recovery budget. | Deterministic exhaustion and no publication of an intermediate overlapping candidate. |
| Negotiated congestion | Persistent congestion not solved economically by local recovery. | Conditional measured extension within P05; not automatically required. | Congested cases improve under a frozen budget; hard isolation/clearance rules remain hard in accepted output. |
| Sparse factory crossovers | A small number of geometrically awkward one-layer crossings. | Explicit supported prototype and policy, not an unlimited fallback. | Copper really passes beneath the raised link without accidental contact; excessive link demand rejects or selects an allowed two-layer strategy. |
| Restricted two-layer routing | Cross-region crossings and rail/signal separation with limited transitions. | Preferred advanced path after P07 comparison. | Cross-layer overlap is not an electrical join without a plated pad/via; every real transition is present. |
| Fuller two-layer routing | Remaining advanced cases needing both layers more freely. | Conditional enablement behind the same physical contracts. | Better whole-pipeline results, not just lower wirelength at unacceptable via count or probe complexity. |
| Arbitrary multilayer/planes/optimal Steiner solver | Manufacturing/high-density goals beyond the stated target. | Do not build now. | Reconsider only through a new owner-approved product requirement. |

VTR documents bounded Pathfinder-style iterations and present/historical congestion costs. These are useful algorithmic precedents for P05, not evidence that an FPGA router is a drop-in PCB router or that its default parameters fit TroubleshootJS. [W2]

## 5.5 Diagnostic admission alternatives

Let H be admitted fault hypotheses, O executable observation actions, and K the cost of one fresh candidate's construction/installation/solve/repair proof. Current-style brute-force work is approximately H×K plus signature comparisons. If comparisons use O observations, a naive all-pairs comparison adds roughly H²×O comparisons. These are **analysis models**, not measured execution times.

| Strategy | Benefit | Correctness limit | Decision |
|---|---|---|---|
| Rebuild/install/repair every hypothesis through the UI owner | Strong existing end-to-end behavior. | Expensive; couples proof to live session and family dispatch. | Keep as a small independent reference oracle. |
| Static serviceability and compatibility filtering | Cheaply eliminates impossible, unsupported or unrepairable candidates. | Cannot establish numerical separation or healthy operation alone. | Mandatory first stage with one canonical predicate. |
| Provider-owned legal observation plans | Reuses localized electrical knowledge. | Local separability can vanish under loading or shared rails. | Mandatory, followed by whole-device checks. |
| Grouped signatures and adaptive decision policies | Shares observations and avoids unnecessary branches. | Equivalent signatures only justify merging when required repair semantics are also equivalent. | D01 implements after correct baseline proof. |
| Immutable proof receipts | Reuses unchanged exact contexts and provider evidence. | Key must include model, recipe, loads, domain/reference state, observation policy and applicable physical access. | Cache only proved-safe immutable evidence. |
| Incremental solver reuse | Could reduce restamping and repeated construction. | Mutable hidden state, source state and old probes can contaminate proofs. | Conditional after A07/A08; never assume same-owner rollback. |
| Sampling or pruning the fault population | Bounded developer experiments and intentional content selection. | A sample cannot prove the omitted alternatives are distinguishable. | Allowed for profiling or selecting an explicit advertised hypothesis set, never as a substitute for its proof. |

A selected challenge must have a legal policy that distinguishes its admitted non-equivalent alternatives. Tests may use representative combinations; the *runtime claim* must still match the exact candidate set it admits. Hitting a proof budget yields a classified rejection, not an automatic PASS or hidden shrinking of the hypothesis list.

<a id="scaling"></a>
## 5.6 Bounded microcontroller implementation decision

MCU-1 must compare these alternatives using the same small electrical I/O and reset/time/state fixture before selecting an implementation. The assessments below are design tradeoffs, not benchmark or implementation claims. Basic MCU support is a planned product goal; arbitrary firmware is not.

| Approach | Benefit to investigate | Principal risk | Required decision evidence |
|---|---|---|---|
| Deterministic behavioral MCU model | Narrow, explicit appliance/control semantics, bounded state and direct electrical pin contracts. | Scripted outputs could ignore real supply/reset/input loading or conceal an implausible internal behavior. | Power-loss/reset/brownout, loaded GPIO, ADC/PWM timing, causal external-clock behavior when exposed, repeatable saved state and runtime cost. |
| Constrained state-machine/control-script model | Reusable control programs and interlocks without a general CPU or IDE. | A universal DSL or unrestricted evaluator could become a second simulator, security surface or hidden firmware puzzle. | Finite instruction/state set, deterministic budgets, typed pin/event operations, sandboxed/data-only input, explicit failure behavior and qualified observable black-box functions. |
| Limited emulation of one very small real architecture | A narrow real execution model may improve confidence in specific timing/state behavior. | Instruction/cycle/peripheral fidelity, firmware packaging and verification can dominate the product. | License/provenance review when implementation is chosen; actually implemented instructions/peripherals; bounded workload, reset/power/clock coupling, supported firmware contract and no implication of arbitrary binary compatibility. |
| Narrow hybrid or another justified approach | Behavioral pins/peripherals plus a small deterministic control engine may preserve useful semantics with less scope. | Two authorities can disagree about time, drive state, supply or persistence. | One declared owner of electrical pin behavior and control state; parity against the common fixture; explicit limits and absence of duplicated physics. |

The initial comparison covers only powered/unpowered/reset, digital I/O and direction, pulls, limited ADC-like input, PWM/timer output, startup, one sensor/interlock/relay sequence and saved state. Add multiplexed display driving only with an accepted E11 provider. Prefer the least complex approach that passes these contracts; do not announce a winner from terminology alone. A failure to meet timing/causality requirements returns a bounded redesign decision, not a false MCU implementation claim.

## 5.7 Import capability negotiation, not “everything imports”

Every source element must appear in the interpretation report with one primary disposition and any unresolved conditions. A missing physical/fault capability cannot be disguised as successful challenge conversion.

| Element disposition | Meaning | Required handling |
|---|---|---|
| Directly supported PCB component | An accepted element/model, package, pin map and physical role exist. | Map it through normal providers and retain exact provenance. Fault selection still needs separate serviceability/diagnostic qualification. |
| Supported with package choice | Electrical interpretation is supported but more than one real physical mapping is possible. | Require an explicit compatible package/unit grouping choice; do not silently choose a misleading pin map. |
| Supported external source/load/control | It belongs to the bench/customer interface rather than the PCB population. | Map to a declared source, load, connector or player stimulus with operating states and references. |
| Supported as an honest packaged module | The supported model is an opaque terminal-level function. | Count and expose one package; do not draw fictitious serviceable internals. |
| Electrically supported, not physically serviceable | The solver can represent it but physical observation or repair is not yet qualified. | Report the limitation. It may remain healthy fixed support only if the selected challenge contract permits it; it is not automatically a fault owner. If its presence defeats physicalization or intended operation, reject the challenge. |
| Unsupported | Parsing, model, physical mapping or required behavior is outside the accepted subset. | Name the exact element/contract and stage. Preserve source, never silently delete, substitute or relabel it as supported. |

An aggregate import can be NEEDS_AUTHOR_INPUT, UNSUPPORTED_CAPABILITY, HEALTHY_VERIFICATION_FAILED, PHYSICALIZATION_REJECTED, DIAGNOSTIC_REJECTED, CANCELLED or QUALIFIED. Exact serialized names are selected at implementation, but these meanings must remain distinct. “Circuit parses” and “healthy solve converges” are partial receipts, not final qualification.

<a id="scale-plan"></a>
# 6. Scale, physics, interaction, and qualification plan

## 6.1 Mandatory component scaling table

Only Task48's seven-part authored proof is supported by the inspected retained qualification. The following five requested bands use **ESTIMATED mixed-package planning ranges**. Counts depend heavily on connectors, IC pin counts, package mix, net fan-out and layout style. Segment estimates refer to meaningful canonical route segments, not every ten-unit grid vertex. They are workload design aids, never pass thresholds or forecasts.

| Physical parts | Estimated pads | Estimated logical nets | Estimated canonical route segments | Primary fan-out/placement concern | Validation/solver/diagnostic concern | Required architecture |
|---:|---:|---:|---:|---|---|---|
| 10 | 20–40 | 8–18 | 25–80 | A connector or a shared return can dominate a small cluster. | Fixed overhead and false metrics can dominate; physical count says little about nonlinear steps. | Current comparison baseline; corrected metrics; exact IDs and recipes. |
| 20 | 45–85 | 15–35 | 60–180 | Package area, escapes and first multi-region joins. | Fixed generic outline already fails some mixtures; repeated proof overhead grows with H. | Dynamic sizing, poses, first hierarchy, production proof boundary. |
| 40 | 95–180 | 30–65 | 140–400 | Multiple connectors, buses, mixed multi-pin packages and rails. | Segment/pad candidate-pair work and loading interactions become material suspects. | Trunks, bounded recovery, indexed candidates, coherent viewport, Q30 envelope. |
| 60 | 145–270 | 45–100 | 240–700 | Isolation region, conversion, repeated drivers and shared support. | Nonlinear/time-scale behavior and candidate separation can cost more than placement. | Layer policy, credible converters, AC/scope semantics, proof receipts and Q60. |
| 100 | 250–460 | 75–160 | 450–1,300 | Global channel demand, interface cuts, high-fan-out rails and dense local regions. | Long sessions, proof population, waveform storage and raw route complexity need separate bounds. | Hierarchical refinement, qualified two-layer/link policy, bounded scheduling, Q100. |

The second table makes the per-band algorithm concerns explicit. It is **CODE-DERIVED for the existing mechanisms and ESTIMATED for future populations**, not a runtime forecast. Let C be components, P pads, S canonical conductive segments, H admitted fault hypotheses, O diagnostic observations and G routing-grid states. None of H, S or the reduced solver matrix size is determined by C alone.

| Physical scale | Placement / route demand | Physical validation | Solver concern | Diagnostic concern | Rendering / targeting concern |
|---|---|---|---|---|---|
| ~10 | Flat placement and sequential routing are useful controls; compare actual connector/escape constraints. | Small all-pairs oracle is affordable as a reference, not assumed production timing. | A small nonlinear model can still dominate; record reduced matrix and subiterations. | Verify all declared small H and O directly. | Confirm exact pad/lead correspondence before optimization. |
| ~20 | Multiple anchors and repeated blocks make region interfaces relevant; fixed-area failures depend on package mix. | Record P/S growth and repeated validation passes, not just C. | Source/protection/relay models add internal state not counted as packages. | Full candidate installs become a visible cost; split proof stages. | Fit-only projection may shrink targets; navigation becomes a supported feature. |
| ~40 | Coarse regions plus local refinement and multi-terminal trees become the preferred baseline. | Canonical segments and broad-phase candidate checks must agree with the oracle. | Nonlinear state/load cases, restamping and active-meter analyses need independent budgets. | H(H−1)/2 pair comparisons may be significant, but repeated solves often cost more. | Cache static projections with exact invalidation; qualify actual visible target density. |
| ~60 | Larger heterogeneous packages, domain barriers and inter-region traffic require qualified layer/channel policy. | Per-layer contact, barrels, cuts and stale-cache negatives matter; worst-case contact checks remain quadratic. | Offline conversion and mixed time scales may dominate a much larger but linear board. | Local certificates require global loading/reference context; adaptive partitions and receipts must preserve coverage. | Both board faces, powered state, instruments and lifted/loose parts must remain usable together. |
| ~100 | Bounded hierarchical search, congestion recovery and a constrained package/net envelope are required. | Large raw grid walks cannot become the durable trace representation; test dense adversarial cases as well as normal boards. | Aggregate matrix cost and time windows are measured separately from component count; isolated backend only if qualified. | Cold proof cost, memory, cancellation and the declared hypothesis population are all release dimensions. | Test full-count navigation and long-session inventories; no hidden-hitbox or invisible-layer shortcut. |

For the current style of dense matrix implementation, factorization cost depends on reduced matrix dimension, and nonlinear iterations may require repeated factorization. Linear matrices can reuse a factorization while their coefficients remain unchanged. The prior code review identified these paths, but it did not measure their limits. [I2 S21] A07 therefore records matrix dimension and actual factorization/restamp counts rather than converting “100 physical parts” into an invented solver runtime.

The raw grid representation may contain many more segments. With S segments, an all-pairs pass examines S(S−1)/2 unordered pairs: 1,000 gives 499,500; 5,000 gives 12,497,500. A spatial broad phase reduces likely comparisons but does not guarantee subquadratic worst-case behavior for arbitrarily overlapping data. Dense adversarial fixtures remain necessary.

The existing generic router has a 720×400 working outline, grid step 10, at most 80 attempts and a target of five viable candidates. These are **CODE** facts, not future design limits. [R03] The earlier packing result, one connector plus seventeen shortest-span resistors exceeding its available inflated-courtyard area, remains a specific necessary-area counterexample. It is not a universal eighteen-part limit. [I2, F4]

For a rectangular routing grid, direction-aware state count scales approximately with grid width × grid height × allowed layers × direction states. Growing both physical dimensions and refining the grid can be much more expensive than adding another board layer. P03/P04 therefore compare outline/grid/channel choices jointly, instead of fixing a poor grid and repeatedly inflating the board.

## 6.2 Supported envelope, not a magic component limit

Every qualified generator/profile carries a versioned envelope covering: physical package count and mix; pin count; net count and maximum fan-out; number of source/reference domains; region count and interface demand; allowed aspect ratios and area; allowed rotations; copper layers; trace/clearance classes; link/via budgets; route representation size; candidate hypotheses; nonlinear/model classes; simulation windows; live and loose inventory; and minimum usable surface separation at a supported inspection zoom/loupe scale, not mandatory comfort at fit-board overview.

Area is selected from actual package/keepout demand plus a measured routing allowance and bounded shape alternatives. Numerical coordinate units are explicit. Legacy canvas units must not be silently relabeled millimeters or used to claim manufacturing clearance. Scaling a physical envelope and zooming the camera are different operations.

The contract rejects requests it does not support. Rejection categories include declaration error, unsupported model, incompatible domain, physical infeasibility lower bound, placement exhaustion, route exhaustion, physical validation failure, numerical nonconvergence, diagnostic ambiguity, repair-unreachable, stale/cancelled work, and infrastructure failure. Unknown exceptions are defects. They must not be swallowed as ordinary failed layout attempts for another 79 retries. [R03]

## 6.3 Copper, packages, and two-layer interaction

P01/P02 establish board-space integer/fixed-grid geometry, checked coordinate arithmetic, immutable snapshots, explicit package pose, and a conductor graph. A pose combines permitted rotation, board side and translation while retaining terminal IDs. Mirroring a viewed board does not exchange a transistor's gate and drain or rename a relay's contacts.

The conductor model distinguishes: top copper, bottom copper, plated through-hole pad surfaces and barrels, non-plated holes, through vias, trace junctions, exposed/tented access, and elevated factory links. This is a bounded two-layer/2.5D model, not a volumetric CAD kernel. Package body/assembly courtyards, layer-specific copper keepouts and actual probe accessibility are separate rules. A body on the component face does not automatically block all backside copper; a permitted backside trace does not magically become probeable through the body from the front.

Cross-layer traces at the same x/y do not join merely because their projections cross. Same-layer copper contact is checked using the accepted width/contact semantics. A valid plated barrel joins its declared layer surfaces; an NPTH does not. The renderer consumes this result and cannot invent a backside bridge. Independent validation reconstructs contacts from geometry/layer declarations and compares them with intended connectivity.

Factory links have raised-conductor and underpass rules. Their cost includes count, density, route quality, board area and readability. P09 freezes numerical limits after comparison; this document does not invent a universal number of acceptable jumpers. The policy must prefer an allowed alternative layer strategy rather than quietly produce dozens of crossovers. Factory-link changes that alter the electrical/physical realization invalidate the affected proof receipt and rerun relevant healthy/diagnostic checks.

Trace cutting acts on a declared physical locus. A cut severs the correct conductor edge and may or may not disconnect a bus if another actual path exists. Wrong cuts remain possible. Plated barrels, vias and inaccessible surfaces cannot be cut through a front-side pixel gesture unless a real supported operation exists. E08 proves jumper, cut and repair semantics together before they become a major challenge family.

## 6.4 Mains, power domains, and educational model fidelity

The new target includes simulated 120 VAC, rectified high-voltage storage, isolated and non-isolated supplies, 24/12/5/3.3 V rails, partial power, backfeed, current limits, protection and instrument references. These are explicit modeling requirements, not evidence that current metadata or existing low-voltage controllers already implement them.

**CODE:** CircuitJS's inspected floating-node path stamps a 100 MΩ numerical connection to the solver reference for otherwise unconnected nodes. [R06] Numerical gauge handling must not become a physical earth conductor, a hidden power return, a fictitious meter result, or an apparent failure of isolation. A06/A07 test insertion/reordering, independent floating supplies, transformer/relay isolation, high-impedance measurement and instrument loading. Merely deleting the stabilizer is not a valid fix; singularity and convergence must remain well-defined.

| Function | First modeling approach | Required behavior | Explicit limit |
|---|---|---|---|
| AC source and entry | CircuitJS AC source with bounded source impedance and actual isolation switches. | RMS/peak conventions, polarity, source state, partial power and load effects. | No utility-grid or safety certification claim. |
| Bridge and bulk storage | Actual diodes/bridge-equivalent connections and capacitor model. | Rectification, ripple within supported sampling, charging and discharge, open/short effects. | No claimed surge/EMC fidelity without a qualified model. |
| Transformer/isolation | Existing coupled-winding backend qualified with realistic bounded parameters. | Separate winding references, energy transfer, load dependence and accessible terminal measurements. | No magnetic-core design/saturation claim from the simple linear coupled-inductor model. [R07] |
| Offline converter | Compare switching-detail pilot with a causally stamped averaged/behavioral model. | Input draw, available power, enable, startup/dropout, load regulation, energy storage and admitted faults. | No fabricated switching waveform or inert external parts. |
| Regulator/buck/LDO | Qualified rail-producing provider, not a separate ideal source. | Headroom, line/load behavior, enable, overload behavior and relevant storage. | Only the documented operating envelope and failure vocabulary. |
| Relay/driver | Qualify current relay model and explicitly modeled driver/clamp. | Pickup/dropout, coil/contact separation, contact leakage/resistance and fault semantics. | The existing relay approximation and reported current behavior need testing; do not assume ideal open contacts. [R07] |
| Fuse/clamp/protection | Rating/state model tied to solved current/voltage and simulated duration. | Healthy survival, bounded overload response and actual graph changes. | No arbitrary damage roll; no compliance or fire-risk certification. |
| Sensor/interlock | Solver-backed input/conditioning with ordinary player stimulus. | Reference, loading, threshold and partial-power behavior. | No hidden-only switch that the player cannot reproduce. |

Manufacturer documentation establishes that real flyback controllers can provide isolated constant-voltage/current regulation and have explicit startup/control behavior. It is background for selecting causal observables, not a CircuitJS validation result or a component-level design recipe. [W3]

“Board power off” means the selected real sources are isolated. It does not mean all capacitors are discharged, that an external load cannot backfeed, or that every reference is at earth potential. Active meters require both the necessary source isolation and the relevant stored-energy/readiness contract. Start conservatively with all relevant board sources isolated; relax to domain-local permissions only with a proved reachable-energy boundary. Do not derive permission solely from a UI toggle.

A conventional earth-referenced scope lead and an isolated differential measurement are different modeled connections. The player-facing instrument must clearly state which it is. A reference lead that creates a real connection must affect the graph; an unsupported hazardous/common-mode measurement must fail explicitly rather than report a plausible but false number. Tektronix documents the distinction between earth-referenced scope commons and floating/differential measurements; that is why the simulated connection semantics must be explicit. [W4] These are simulated training contracts, not instructions for live mains work.

## 6.5 Large-board viewport and accessibility

One `BoardViewTransform` or equivalent owns board-to-screen and inverse mapping, device-pixel ratio, pan, zoom, active face, and region focus. Pads, leads, selection, traces, hit tests and probe markers consume the same transformed geometry. The parts tray and instrument controls are screen-space workbench chrome and must not force the entire PCB to shrink.

Require fit-board, zoom-to-selection, ordinary pan, temporary cursor-centered Spacebar inspection, and a way to recover orientation after focusing a region. Fit-board is an overview, not a guarantee that every part or marking is readable without inspection. A minimap is conditional on measured navigation difficulty. Face flipping must keep semantic targets stable and invalidate only genuinely inaccessible/stale targets. Hidden backside geometry cannot silently capture a front-side click. If targets overlap on screen, provide explicit visible disambiguation or require zoom rather than enlarging invisible hit areas.

Static copper/package projections may be cached by immutable realization and view version. Dynamic part state, lifted leads, damage, selection and solver intensity have separate invalidation. Level-of-detail may simplify shading and text in the overview. It must preserve current probe locations and must make necessary polarity, terminals, physical markings and copper inspectable through supported zoom or the loupe; it must not erase them from the inspection view or require an inaccessible observation. An unreadable overview is not itself a board-rejection reason. Accessibility exposes the same physical/public information, not hidden original values or fault IDs.

## 6.6 Diagnostic proof at scale

A diagnostic hypothesis has a stable identity containing physical owner, fault mode and relevant parameterization. Type-only or owner-only deduplication is insufficient when the same part can fail in several ways. Candidate counting, selection, serviceability and proof enumeration use one eligibility result. Rejected hypotheses retain a reason; they are not silently forgotten.

Plans name public-semantic actions, targets and input states. They may be adaptive: an observation partitions the hypothesis set and determines which *legal diagnostic action* is useful next. An internal proof may test that policy, but the normal UI must not turn it into an answer-revealing autopilot. A technician remains free to choose another valid route.

Admission has four layers: static structure/serviceability; provider-level behavioral conformance; whole-device numerical separation under declared operating conditions; and reachable physical repair with customer retest. Parallel paths, shared rails and alternate implementations can invalidate a local proof, so a block certificate is not a whole-board admission shortcut.

Receipts are keyed by the complete relevant immutable input context: implementation/model/recipe versions, topology and loads, source/reference states, fault population, observation/repair policy, simulation settings, and physical/access evidence. A physical layout change can preserve a purely electrical receipt while invalidating accessibility evidence, but that distinction must be explicit. A new model or topology invalidates the relevant electrical proof. Wall-clock timestamps are provenance, not identity.

Cache signature partitions and replay stable observations where justified. Do not share live mutable graphs merely because their descriptors match. If a numerical action depends on temporal state, the receipt includes its initial-state and simulation-time contract. Reusing an endpoint after board replacement is never valid. Proof interruption yields CANCELLED/STALE, not a partial PASS.

## 6.7 Performance contract and reference machine

The brief explicitly rejects ordinary three-minute admission as success. That is a product constraint, not a measured current time. Qualification must set materially more responsive cold/warm budgets rather than silently accepting that delay or hiding it behind pre-cached demonstrations. No measured 60/100-part budgets exist in this review. A01 must select and name an actual modest reference machine and record CPU, RAM, integrated/discrete graphics, OS/browser versions, resolution, device-pixel ratio, power mode, build hash, background conditions and cold/warm-cache protocol. The owner's fast desktop may be a secondary test host; it is not automatically a modest baseline.

Candidate **UX TARGETS**, to be ratified after A01 pilot measurements rather than advertised as current capability: ordinary input response within about 100 ms at the 95th percentile; responsive pan/zoom at at least 30 frames per second during the prescribed navigation test; generation/proof work that yields regularly instead of locking the UI. These are proposed product goals, not benchmark results. Generation time, memory, solver throughput and cancellation latency must receive explicit numeric budgets in A01 based on the selected host and pilot results. A release gate cannot pass with those fields still unspecified.

Record cold and warm generation separately, including p50/p95, worst case and failure counts across a preregistered corpus. Count every attempt and rejection, not only successful seeds. Freeze budgets before optimization and holdout qualification. If a target is missed, optimize, constrain an openly declared envelope or request an owner-approved budget change; do not quietly revise the threshold to match a bad result. Multi-minute ordinary admission is not acceptable under the brief.

| Stage | Deterministic work counters | Observed performance / resource data |
|---|---|---|
| Grammar/value resolution | Choices, static rejections, finite candidates. | Elapsed time and allocations. |
| Placement | Outline/pose alternatives, candidate evaluations, fallbacks. | Time, peak geometry and index memory. |
| Routing | Expanded states, queue peak, nets, recovery passes, vias, links. | Time, peak resident data and rejected-attempt cost. |
| Validation | Broad-phase pairs, exact contacts, DSU operations, full/incremental passes. | Time and independently verified equivalence. |
| Solver | Reduced matrix size, voltage sources, nonlinear subiterations, restamps, accepted simulation steps. | Simulated seconds per real second, convergence failures, memory. |
| Diagnostics | Hypotheses, observations, partitions, solves, repair/retest runs, cache hits with provenance. | Cold/warm admission time and cancellation latency. |
| View/input | Visible primitives, transformed targets, index queries, redraw invalidations. | Frame cost, input-to-feedback latency and peak projection data. |
| Long session | Parts acquired/removed, active/inactive elements, history and waveform samples. | Retained heap/objects, cleanup after owner replacement, degradation slope. |

Deterministic budgets bound work and guarantee a reproducible success or exhaustion decision. Wall-clock deadlines may cancel with a resource/infrastructure result; they must not pick a different successful candidate. Parallel results are committed in canonical candidate order, never “whichever finishes first.”

## 6.8 Test architecture and independent oracles

Use cheap pure contracts and provider conformance for exhaustive small finite states: IDs, source permissions, pin maps, declared poses, stage outcomes, candidate predicates, recipe/version parsing and short failure-stage tables. Use deterministic structural corpora for package mixes, connector order, domains, fan-out and physical congestion. Use selected solver integration for meaningful combinations of loading, state, fault, repair and model fidelity. Use pairwise coverage only when interactions are not known to require higher-order combinations; it is a test-design heuristic, not a correctness theorem.

Maintain three deliberately independent references: a small brute-force geometric contact checker; a simple serial full-candidate diagnostic proof; and direct model/physics fixtures. Fast spatial indexes, cached receipts and incremental validation must agree with these on small exhaustive and adversarial cases. A production plan and a verifier derived from the same incorrect recipe are not independent evidence.

Every layer gets intentional negatives: malformed data; aliased/mutated geometry; illegal via; accidental cross-domain join; unsatisfied role; absent source; stale callback; active meter contamination; nonfinite solve; impossible healthy target; indistinguishable non-equivalent faults; cut on the wrong physical layer; unsupported old version; and a forced failure that reaches terminal FAIL/nonzero status rather than RUNNING forever.

Maintain a training/development corpus and a frozen holdout corpus drawn from the same advertised envelope. Report acceptance rate, quality distribution and stage failures; do not select only attractive examples. Exact rates and sample sizes are frozen by the qualifying task before changes are evaluated. The small finite package/pose space can be exhaustive even when the full circuit cross-product cannot.

Long-session qualification includes repeated replacements, legitimate retained loose parts, measurements, source operations, waveform sampling, saves/resumes and board succession. An initial planning corpus should include at least a hundred owner/repair cycles and a separate accumulated-inventory case, with larger stress cases chosen from measured growth. Those are **test targets**, not product limits or evidence of a leak. Preserve user-owned parts/history unless a published user action disposes of them.

Final source candidates require the actual JDK8/GWT production build and selected normal-player browser workflows. Reuse previous evidence only with an exact dependency justification. Successful Computer Use interaction does not certify a failed CDP wrapper. No test failure is reclassified as PASS because another route worked.


---

## 6.9 Rolling playable integration during architecture work

Maintain a small living end-to-end canary at the following existing boundaries. These are acceptance clauses, not new numbered milestones. Use the smallest current real challenge and the ordinary workbench/repair capabilities already accepted at that point, so the canary does not manufacture a dependency on a later release or instrument.

| Boundary | Required route through the changed seam | Must not be substituted |
|---|---|---|
| After A05 | Generation → new provider/variant construction where available → installation → normal player interaction → diagnosis → physical repair → retest. | An isolated contribution fixture, a build-only PASS or a legacy-only route presented as proof of the new provider. |
| After A09 and again after A10 | Production fault hypotheses → proof; then staged generation/proof/publication under A10 → normal actions → diagnosis → repair → customer retest. | Developer-only private state manipulation, stale evidence after consumed code changes or a proof requiring hidden controls. |
| After P04 | Actual generated PCB → placement/routing representation → rendering → independent physical correspondence → ordinary probing and component repair/retest where relevant. | A routed picture with no playable electrical backing or isolated geometry tests only. |
| After U01 | Overview → pan/permanent zoom → Space press/move/release → supported board flip/layer view → probing/component interaction → repair/retest. | Giant invisible hitboxes, changed target identity, comfort-at-overview as a new fairness requirement or debug-only coordinate input. |

The canaries remain small and relatively inexpensive. They are not permission to rerun every historical Task43 matrix, create screenshot spam or reopen accepted history. They do not replace Q15/Q30/Q60/Q100. Dependency-aware evidence reuse remains available, but changed integration seams receive actual fresh proof. A failed canary blocks dependent architecture expansion until the affected seam works; unrelated optional lanes need not stop.

## 6.10 IC/MCU/display timing and bench-instrument contracts

**ICs and state:** Power and reference pins are modeled participants. Digital thresholds, hysteresis, output drive limits, loading, reset/set priority and initialization are explicit. The unknown/unsupported timing region is not arbitrarily converted into a deterministic zero. Model documentation distinguishes a qualified deterministic approximation from an unmodeled real-device effect. No metastability or RF behavior is promised simply because a flip-flop or crystal appears.

**MCU control:** The initial program is a bounded, versioned black-box appliance function. The player troubleshoots power, reset, clocks, pins, sensors, drivers and loads, not hidden source code. Pin-stuck, package-dead or internal modeled-state faults are admitted only with an observable, repairable contract; an external held reset or missing pull-up remains an external cause. VCC/reset loss cannot leave an output magically driving its previous voltage. High-impedance/off, backfeed, pulls, analog conversion and PWM are modeled only within stated limits.

**State and time:** Event phase, clock source, initialization, provider version, modeled registers/latches, timers, ADC/PWM state and semantic pending events are captured when exact resume is advertised. A resume/restart difference is visible. At different paint rates the accepted simulation-time event/observation sequence remains the same. State clocks do not secretly advance during a cancelled proof or use host sleep as simulated elapsed time. Shared solver globals continue to forbid assumed independent numerical contexts.

**Clock vocabulary:** Crystal, resonator, oscillator-module and RC-clock models declare which oscillation/startup/clock-loss observables are supported. A visible external oscillator network must influence the MCU or sequential device when that network is the selected clock source. Otherwise the package declares an internal oscillator and no decorative external crystal is added. Missing-clock diagnosis requires a legitimate available observation, not a hidden register inspection.

**Display causality:** Common-anode/common-cathode pin maps and current-limiting paths are explicit. A single-digit display derives lit segments from solved currents. A multiplexed display uses actual digit-select/segment-drive timing and a bounded perceptual integration of those solved currents for rendering; the renderer does not advance simulation or read a desired numeral from scenario metadata. Duty cycle, missing common/driver, open segment, supply collapse, missing scan and unsupported sampling are independently testable. Status/error codes can be outputs of the modeled control function; they must not disclose a selected hidden fault ID.

**Useful instruments, bounded scope:** U03 stays the one-channel foundation; U09 is the planned two-channel troubleshooting expansion. U10 is a conditional logic probe followed, when justified, by 2–4-channel capture. U11 supplies conditional real electrical signal injection. U12 qualifies current insertion and optionally ESR only with a defensible model and content need; X08 retains conditional capacitance. No release waits for these extensions unless it advertises them or a selected diagnostic policy needs them.

For every enabled instrument, publish the electrical connection/stimulus, input burden/loading, range and overrange, reference constraints, timing/bandwidth, unavailable states, cancellation/cleanup and visible player operation. Two scope channels may share a common reference only if that physical instrument model says so; differential math does not secretly provide galvanic isolation. Logic thresholds use the relevant supplied reference, not a global 5 V assumption. A current mode requires a real in-series path and modeled burden/protection policy rather than reading an arbitrary hidden branch current. ESR cannot be inferred from a configured metadata field and shown as a measurement.

## 6.11 CircuitJS import-to-challenge implementation contract

The long-range goal is broad support for ordinary community circuits inside a declared capability envelope, not an arbitrary-file guarantee. IMPORT-1 establishes a complete small passive/DC route; later import stages expand accepted element/behavior groups. The pipeline below applies at every stage.

1. **Ingest reproducibly and safely.** Retain source bytes or an explicitly resolvable source artifact, content hash, detected format, parser/model versions and bounded input limits. Reuse/adapt CircuitJS parser/load semantics where possible. Parse in an owned staging context; malformed data, oversized graphs, recursive constructs, unsupported models or embedded external-resource/script requests cannot mutate the player's current board. Imported names/labels are untrusted display text.
2. **Interpret connectivity before physical placement.** Recover the actual supported CircuitJS connection semantics. Assign and persist stable import-local component/unit/terminal/bus identities. Schematic wire vertices and coordinates may explain electrical connection in the source, but are not PCB placements or durable identity keys. Resolve symmetric duplicates and multi-unit packages explicitly. Reordered or edited input is a new content/interpretation version unless a reviewed mapping proves continuity.
3. **Classify every element.** Use the six dispositions in Section 5.7 and an explicit support matrix. Record parse support, electrical support, package mapping, external role, observation capability, fault/serviceability and model limitations separately. An unsupported element never disappears silently.
4. **Resolve sources, controls and loads.** Determine with author confirmation where ambiguous whether each ideal source/switch/ground/load is an on-board component, external connector/source/load, customer control, stimulus or unsupported construct. An ideal simulator voltage source is not automatically a mounted PCB package. External loads do not inflate board counts.
5. **Declare functional intent.** The manifest/wizard records actual power sources/references, allowed operating states, player stimuli, customer outputs and healthy predicates/windows. Automatic suggestions are allowed only when unambiguous and author-confirmed as required. Examples such as “12 V input,” “fan output” or “status LED” are author intent, not facts guessed from a convenient net name. No expected function means no automatic faulted challenge.
6. **Map physical components.** Choose supported packages, real pin order, designators, ratings/specifications, mounting sides and useful region constraints. Verify equivalence of the imported electrical function before/after mapping. No electrical topology or part values are rewritten merely to make routing easy; an explicitly permitted representation change requires independent correspondence and a new receipt.
7. **Verify healthy operation first.** Run the real interpreted graph across all declared relevant input/power states using the normal execution service. Convergence alone is insufficient. Unsupported or contradictory intended healthy behavior stops challenge generation before fault injection.
8. **Enumerate only legitimate faults.** Use the native canonical hypothesis service with supported physical owners, qualified effects, actual serviceability and a meaningful symptom. Seeded selection is mandatory. Electrically supported but unserviceable fixed support is not automatically a candidate.
9. **Reject bad faulted candidates honestly.** No-op symptoms, unsupported numerical failure, inaccessible observations, unavailable stimuli, impossible repairs or indistinguishable non-equivalent causes reject with a stage/reason. Do not relax diagnostic correctness because the circuit came from a user.
10. **Use native physicalization and play.** Normal placement, routing, layers/vias/crossovers, physical validation, renderer, probing, repair and customer retest apply. Routing failure rejects a realization or exhausts the finite normal search; it never licenses an imported-only hidden connection or topology rewrite.
11. **Pin replay interpretation.** The qualified artifact records input hash plus available source, import/parser schema and versions, accepted mappings/unit grouping, package choices, functional manifest, values/models, physical realization and policy, selected fault, observation/repair contract and proof versions. A file hash alone is not a replay recipe. No live solver-node or source-coordinate repair identities are serialized.
12. **Explain the envelope.** Maintain a versioned element/import-feature support matrix and a per-file report with exact unsupported elements, ambiguity and failure stage. Successful subsets are published as subsets. Separate author-visible source/manifest details from ordinary technician-facing complaint/UI/share projections. A user who supplied a schematic may remember it; no false anti-cheat claim is made.

IMPORT-1 covers small supported passive/DC circuits. IMPORT-2 expands diode/transistor/RC families. IMPORT-3 expands accepted small-IC/control content, with MCU/display/timer capabilities only when actually consumed. IMPORT-4 adds supported multi-rail/relay/dynamic circuits; mains/offline conversion is separately conditional on E05/E06. IMPORT-5 qualifies a broader, versioned supported subset through a frozen varied import corpus. IMPORT-3 and IMPORT-4 need not become a needless serial chain when their actual supported capabilities are independent.

## 6.12 Grouped fault-library expansion and serviceability

These are future capability categories, not a requirement to implement every fault now. A09 owns canonical hypotheses and eligibility; electrical providers own the causal healthy/fault model; A08/E08 own applicable physical actions; X01/X02 own later intermittent/damage evolution. Every admitted mode requires a supported healthy model, physical owner, accessible observations, reachable repair and functional retest.

| Owner family | Future modes within a defensible model | Required guardrail |
|---|---|---|
| Resistor/network | Open, supported value drift high/low. | Physical markings describe nominal identity, not a hidden fault readout; network/package repair boundaries are real. |
| Capacitor | Open, short, reduced capacitance, leakage; ESR degradation only with an accepted equivalent model. | Stored energy, active-meter restrictions and timing/frequency observations remain causal; no metadata-only ESR/capacitance reading. |
| Diode/Zener/TVS | Open, short, leakage and supported changed clamp behavior; reversed installation where relevant. | Qualify polarity, reference, load and stress envelope; do not promise unmodeled transient physics. |
| BJT/MOSFET | Open junction/path, short path, stuck conduction, supported base/gate drive damage. | Distinguish package defects from upstream driver/supply/reference causes and secondary damage. |
| IC/MCU | Dead package, supported stuck input/output or drive degradation; clock/reset/supply effects; modeled state faults only when meaningful. | Missing external clock/reset/supply is not automatically an IC fault. No required decompilation or private-register answer. |
| Relay | Coil open, stuck-open/closed contacts, coil/driver issues, modeled high contact resistance. | Coil/contact/driver ownership stays distinct; real load and source limitations apply. |
| Connector/interconnect | Open terminal, modeled high-resistance/poor connection, broken trace; later via/open-conductor faults. | Stable terminal/conductor locus and accepted repair action are required. Trace/via repair content adds E08 only when consumed. |
| Power path | Fuse open, regulator/rectifier/startup/feedback failure, partial rail collapse. | Every counted visible part affects actual power/feedback behavior; use permitted source/instrument contracts. |
| Sensor/input | Open, short, biased or stuck response, supply/reference failure. | Normal player stimulus and separating observations exist across operating states. |
| Later composite behavior | Intermittent events, secondary damage, carefully bounded multiple faults. | Existing X01/X02/X06 entry rules remain; no automatic enabling or unexplained randomness. |

Condition-related symptoms can be difficult without identifying the exact failed semiconductor internally. Equivalent repairs are acceptable only when the admitted hypothesis class and physical repair semantics genuinely agree. Do not prune inconvenient causes or treat a package label as proof of an internal fault.

<a id="milestones"></a>
# 7. Milestone catalog and execution graph

## 7.1 How to execute this plan

The identifiers below replace only future work after accepted Task49 and N00. They are dependency nodes, not permission to launch every worker at once. A card is a bounded roadmap contract, not an implementation prompt. Its authorized implementation may split into reviewed sub-checkpoints when the concrete design needs them; record the split without weakening its parent acceptance claim.

**Status after N00 adoption:** T49 is accepted at `3de4da1d3bad3ed532e6c327b24195bc3138ed15`; N00 document/lineage adoption is COMPLETE and Edition 2.1 is adopted. A01 and A02 are now IMPLEMENTED — ACCEPTED; A03 and every later card remain UNSTARTED. Repository publication follows the required final status-delta review, final document/staged checks and normal commit/push handoff. No `[x]` here means “expected to pass.” T48 remains the accepted historical prerequisite of T49. A required evaluation may legitimately choose not to adopt a sophisticated algorithm; that is a completed decision, not a claim that the declined algorithm was implemented.

**Conditional use rule:** A content/profile/release that consumes an optional capability adds that capability's accepted implementation as a hard dependency. Otherwise, its decision/skip does not block unrelated work. Every qualification gate tests the actual current integration, including new provider state in saves, measurements and retest. An earlier PASS never automatically qualifies later consumers.

**Work order after accepted T49 and N00 adoption:** A01 → A02 → A03. Then begin explicitly authorized, non-overlapping architecture/physical tracks. Read-only investigation may run concurrently; implementation begins only after all prerequisite investigations for that boundary have returned and been reconciled. One owner integrates shared-core changes. Separate directories are not proof of independent runtime contracts.

**Early hard-question lanes:** A06/A07 bring source/reference and solver feasibility forward. P01/P02 freeze coordinate/copper identity before P03–P09. A04/A05 replace construction knowledge before additional blocks. A09/D01 replace diagnostic centralization before large hypothesis sets. Do not postpone E06's small converter pilots until a complete RB56 is assembled.

**Model/delegation policy:** Follow the current AGENTS.md and explicitly requested runtime settings. For the owner's requested arrangement use Astra MAX as root and actual Luna MAX leaf investigators/coders/reviewers, normal speed where exposed, no invented model metadata or silent downgrade. The Edition 2.1 planning preparation used no subagents; that historical statement does not describe the current N00 writer/reviewer sequence. Future implementation must never start a coder while prerequisite investigators are still working. Send material findings and decisions, not liveness commentary; preserve independent review and treat workers professionally.

## 7.2 Phase map and qualification ladders

| Lane | Main nodes | Boundary that must not be skipped |
|---|---|---|
| Adoption | T49 (accepted) → N00 (complete) | Exact accepted Task49 handoff, document/dependency checks and independent review; publication follows the final handoff contract and no N00 SHA is fabricated. |
| Architecture | A01–A11 | Stable manifests, constrained providers, source/solver ownership, fault/diagnostic contracts. |
| Physical | P01–P09 with U01 | Layer/copper identity before routing consumers, two-layer comparison before advanced policy. |
| Electrical vocabulary | E01–E08 core; E09–E14 later groups | Source consequences and reference/measurement models before mains and harmful repair; later IC/display/AC-control/motor groups only for consuming content. |
| Workbench/session | U01–U08 core; U09–U12 later instruments | Coherent transforms, Spacebar inspection and state/history; later instruments only when qualified and consumed. |
| Scale | Q15 → Q30 → Q60 → Q100 | Structural, physical, solver, diagnostic and player stages pass independently. |
| Product release | REL-A → REL-B → REL-1 | Alpha is intermediate; beta needs Q60; mature advertised stretch needs Q100. |
| Advanced skill | D01, U05, X01–X08, X05 | Difficulty uses proved observations and skilled-user evidence, not raw part count. |
| Basic MCU | MCU-1 → MCU-2 | Compare bounded methods before implementing causal I/O/time/state; not arbitrary firmware. |
| CircuitJS import | IMPORT-1 → IMPORT-2 → independent IMPORT-3/IMPORT-4 coverage → IMPORT-5 | Explicit capability negotiation and healthy intent before native physical/fault qualification; not universal import. |

Several streams can progress before a release. Q30 and the physical-layer investigation need not wait for an alpha announcement. E05/E06 pilots need not wait for beta. X01/X02 production expansion remains after beta by preferred product sequence, while their interfaces and solver-time/state needs are represented earlier. A01/A07 explicitly probe full-count structural/solver costs long before Q100's final playable qualification.

A recommended planning order, subject to actual dependencies and separately granted scope, is:

```text
Accepted T49 handoff: `3de4da1d3bad3ed532e6c327b24195bc3138ed15`
N00 document/lineage adoption (COMPLETE; Edition 2.1 adopted)
A01, A02, A03
A04/A05 and A06/A07 and P01/P02
A08/A09/A11; U01; P03/P04; E01/E03
A10; Q15; U04/U05; REL-A when ready
P05/P06/P07/P08/P09; U02/U03; E02/E04; D01
Q30; U06/U07
E05/E06; E08 only for a trace-repair-consuming family; Q60; HARD calibration; REL-B
Q100 and advanced X work as separately authorized
X05 expert calibration; X09 support; REL-1
```

This is a readable planning order, not a second dependency definition. The exact prerequisites on each card govern; for example E02 requires E01 but does not require finishing REL-A. The historical Edition 2.0 package named `research/RECONCILED_ROADMAP_GRAPH.json`; that companion is absent from this checkout and is not a dependency. The in-document card graph in Sections 7.2–7.3 is authoritative here, and no missing artifact is inferred or fabricated.

**Preferred timing for the new lanes:** After stable native small/multi-rail board contracts, E09/E10/E11 can expand control and display vocabulary in separately authorized steps. U09 follows the useful one-channel scope; U10–U12 require a demonstrated diagnostic use. Specialized E12–E14, basic MCU implementation and broad import qualification are long-range work, commonly after the advanced beta unless the owner explicitly selects an earlier consuming family. Small compatibility canaries belong to the foundations now. This preference does not add REL-B, Q60 or Q100 as a hard prerequisite of the providers being qualified, and it does not add those providers as hard prerequisites of the base releases.

## 7.3 Roadmap cards

## Current work

<a id="m-t49"></a>
### T49 · Intent-driven value synthesis v1: accepted bounded contract

**Type / status:** ACCEPTED — completed task; `3de4da1d3bad3ed532e6c327b24195bc3138ed15` on `codex/task43p-final-recovery`.

**Purpose and reason:** Preserve the accepted one-block value-synthesis proof as the bounded input to the new roadmap. The larger product vision does not expand or reinterpret this result.

**Architectural owner / affected systems:** Existing value/contribution owners, bounded request/plan, physical specifications and CircuitJS integration.

**Hard prerequisites:** accepted Task48, direct parent `62c878b8f381e3418214a93a46d3e8d2d9693b3e`.

**Must not be coupled:** No Task50, new router, generic diagnostic redesign, or roadmap adoption while the current writer is working.

**Exact deliverable:** One immutable resolved recipe from a finite standard/catalog candidate set, deterministic VALUES selection, truthful ratings, and explicit replay version boundary. The accepted versions are `bounded-assembler@3`, `controlled-indicator@1`, `resistor-led-load@2`, `controlled-led-load-e12@1`, and geometry version 3; the legacy controlled route remains `bounded-assembler@2`.

**Acceptance:** The [retained Task49 packet](task-evidence/task-49/README.md) and its [independent review](task-evidence/task-49/review.json) record real healthy solver behavior, both admitted repair owners, correct markings/catalog correspondence, legacy Task48 replay, negative rejection and independent review under the original prompt.

**Important negative tests:** Impossible intent; under-rated candidate; order-sensitive selection; duplicated nominal value authority; old descriptor routed through a changed algorithm; original numeric-value leakage.

**Performance / scalability evidence:** The [accepted Task49 evidence packet](task-evidence/task-49/README.md), its [independent review](task-evidence/task-49/review.json) and source candidate, with recorded reuse and fresh checks. This card does not add a larger-board or general synthesis claim.

**Architectural risk:** Overgeneralizing a narrow result or declaring completion without compiled/player evidence.

**Expected extension and scale effects:** Pluggability: supplies the value authority later providers consume. Scale: avoids repeated value tables; does not qualify larger boards.

**Replay / versioning:** Generator3 uses the accepted Task49 value policy and block-scoped VALUES stream. Generator2 remains fixed at 330 ohms / 0.25 W with its exact legacy meaning; accepted Task48/49 descriptors retain their reviewed meaning.

**Accepted catalog and retained limits:** The two admitted generated values are 270 ohms / 5% / 0.25 W / `AXIAL_RESISTOR` and 330 ohms / 5% / 0.22 W / `AXIAL_RESISTOR`. The result remains one load-block policy and one truthful axial resistor package. It does not claim manufacturing certification, a general circuit-design engine, arbitrary part search, support-block or Task50 work, PCB scaling, diagnostic eligibility, bend-count or connected-placement correction.

**Task49 impact:** Accepted contract and immutable recipe are preserved by all later milestones; N00 adopts this exact result without reopening its implementation.

**Direct later dependents:** [N00](#m-n00).


## Adoption

<a id="m-n00"></a>
### N00 · Post-49 acceptance, lineage and roadmap adoption

**Type / status:** Required adoption gate; COMPLETE — document/lineage adoption and Edition 2.1 accepted. Repository publication follows the final handoff contract.

**Purpose and reason:** Prevent the new plan from trampling the actual Task49 result or resurrecting obsolete milestones.

**Architectural owner / affected systems:** Owner review, current task report and roadmap/evidence maintenance; exact Git lineage and owner-input preservation before authorized publication.

**Hard prerequisites:** [T49](#m-t49).

**Must not be coupled:** No forced branch merge, branch deletion, broad cleanup, or implementation bundled with adoption.

**Exact deliverable:** Accepted Task49 SHA and qualification linked into the current checkpoint; this edition adopted with one immediate next authorized boundary and explicit old-task migration. N00 changes only `docs/ROADMAP.md` and `docs/CODEX_TASK_REPORT.md`.

**Acceptance:** Task48 ancestry, actual Task49 versions, limitations, evidence and review are reconciled. Focused document/dependency checks PASS and the fresh independent Turing Luna MAX document review PASS; repository publication is handled by the final status-delta, staged-check and normal commit/push handoff. Existing evidence and unrelated work remain intact; no future task is silently marked complete.

**Important negative tests:** Stale master; unreviewed descendant; invented approval; active concurrent writer; dropped Task49 limitation; historical appendix interpreted as current instructions.

**Performance / scalability evidence:** Exact branch/SHA/input hash, owner-original preservation diff and evidence references, including the final normalized UTF-8/LF roadmap hash in the current task report. Document-only checks do not require a compiler or browser; the exact N00 commit SHA is recorded only in the final handoff after publication.

**Architectural risk:** Stale replacement document overwriting newer truth.

**Expected extension and scale effects:** Pluggability: no code effect. Scale: makes later qualification traceable.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract and immutable recipe; N00 is the completed adoption gate before A01.

**Direct later dependents:** [A01](#m-a01).


## Architecture foundations

<a id="m-a01"></a>
### A01 · Reference-board manifests and reproducible measurement harness

**Type / status:** Required early evidence gate; IMPLEMENTED — ACCEPTED after the
bounded qualification-integrity corrections, final sequential aggregate-timing
correction and fresh independent review.

**Purpose and reason:** Expose 60/100-part costs early instead of discovering them after the content library depends on untested assumptions.

**Architectural owner / affected systems:** Fixture/corpus tooling and evidence schema, with adapters to existing generation, geometry, solver and browser paths.

**Hard prerequisites:** [N00](#m-n00).

**Must not be coupled:** Do not wait for mains, a finished generic router, or playable RB100 to begin cost probes; do not claim these probes qualify those capabilities.

**Exact deliverable:** RB15/RB30/RB56/RB100 functional inventories; architecture-stage manifests; versioned workload counters; an identified reference machine; cold/warm run protocol and frozen qualification budget process. An owner-authorized desktop exception is valid when the host is fully identified and the evidence explicitly makes no modest-machine portability claim; a separate modest-host run remains future evidence.

**Corrective-pass closure:** The collector fails closed on wrapper exceptions
while retaining diagnostic terminal/report/cleanup/error fields; the checker
enforces finite bounded timing and trace intervals; and preview-served execution
provenance is bound through Java and the collector to the checker. Because the
16 attempts execute serially, the final checker additionally requires
`totalElapsedMs >= sum(attempt.elapsedMs)` while permitting legitimate overhead.
Fresh deterministic regressions, dependency-audited two-corpus Browser evidence,
forced-failure/debug-off canaries and independent review are recorded in the
[A01 evidence packet](task-evidence/A01/README.md), its `corrected-*` summaries
and its `sequential-*` summaries. The single-corpus checker allowance and absent
injected cleanup-failure canary remain documented future hardening items. The
initial integrity correction is recorded in
`fdabfea1ab6ea0324ba39d76b6ad4173e9bea869`; its published handoff tip is
`4b1e6668ec03440210ee2617c92ea57fa8d7cb0b`, and the final sequential correction
commit is named in the publication handoff.

**Acceptance:** Count physical packages separately from solver elements; expose pads/nets/raw and canonical segments, hypothesis count and matrix metrics. Run available small baselines plus bounded synthetic 20/40/60/100 structural/solver pilots; record unsupported stages rather than fake playable boards. Each 16-attempt corpus records 32 accepted solver steps (64 across pilot and holdout), browser viewport/DPR, timing p50/p95/worst, failure outcomes, memory availability and bounded cancellation.

**Important negative tests:** Inert filler counted as function; only successful seeds retained; timing influencing candidate identity; undefined machine; wall-clock claims without traces.

**Performance / scalability evidence:** Baseline distributions, failures and counters. Set numeric stage/resource budgets before each qualification run, using measured pilots and user latency targets.

**Architectural risk:** Benchmarks optimized for attractive fixtures or instrumented code changing semantics.

**Expected extension and scale effects:** Pluggability: one reusable conformance/measurement entry. Scale: early separation of solver, router, proof and UI costs.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [A02](#m-a02), [A07](#m-a07), [P08](#m-p08).

**Current implementation evidence (2026-09-07):** RB15/RB30/RB56/RB100
architecture manifests, the versioned CircuitJS 20/40/60/100 ladder route,
pilot/holdout receipts, frozen budgets, debug-off suppression and forced-failure
cleanup are implemented and pass their focused checks. The final checker closes
the sequential aggregate-timing false-pass; source/build/execution identities,
regressions and dependency-audited receipts are recorded in
[`docs/task-evidence/A01/`](task-evidence/A01/README.md). The required fresh
independent review passed; no A02 work is started by this implementation.

<a id="m-a02"></a>
### A02 · Close known predicate and geometry correctness seams

**Type / status:** Required correctness gate; IMPLEMENTED — ACCEPTED.

**Purpose and reason:** Fix the small known inconsistencies before trusting expanded metrics or fault populations: audit F3, F5 and F6.

**Architectural owner / affected systems:** Candidate eligibility/identity; PcbBoardLayout bend metrics; connected-placement coordinate arithmetic and focused tests.

**Hard prerequisites:** [A01](#m-a01).

**Must not be coupled:** No global rerouter, Task43 reopening, or full provider redesign inside these corrections.

**Exact deliverable:** One admitted-candidate predicate, explicit hypothesis keys, direction-based bend semantics, documented local/global coordinates, and surgical versioned corrections after a preserved baseline.

**Acceptance:** Compatible but unserviceable candidates are excluded consistently; same-owner distinct supported hypotheses are not silently merged; straight subdivision does not add bends; translated inputs translate targets exactly once. Re-run affected old and new path tests.

**Important negative tests:** Compatible/unserviceable open; two hypotheses on one owner; unequal collinear segments; reversal versus bend; translation and boundary inputs; candidate count/proof divergence.

**Performance / scalability evidence:** Repository-native reproductions of prior helper findings and before/after metrics. Prove reachability/impact separately from helper correctness.

**Architectural risk:** A metric fix silently changing old replay, or local patches masking an incomplete placement model.

**Expected extension and scale effects:** Pluggability: one eligibility contract. Scale: reliable input metrics and fewer misleading retries.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**A02 acceptance evidence (2026-09-08):** Repository-native before/after
reproductions and focused Candidate/Geometry66/Replay112 checks PASS. One
canonical admitted population and explicit same-owner hypothesis keys now feed
selection/count/proof; bend counting uses direction; connected placement applies
world translation once. Final JDK8/GWT2.7 five-permutation build, live NPN/LED/
diode/parallel proofs, restored 14-route Task41 corpus and 755/1053-assertion
Task48/49 replay checks PASS. The legacy full reports match every baseline field.
Independent Luna MAX review and targeted repair review PASS. See
[A02 evidence](task-evidence/A02/README.md) for identities, retained failures,
limits, dependency-audited evidence reuse and cleanup. A03 was unstarted at that handoff; its current status is below.

**Direct later dependents:** [A03](#m-a03), [A09](#m-a09), [P01](#m-p01).

<a id="m-a03"></a>
### A03 · Stable design identities, device buses and complete replay manifests

**Type / status:** Required foundation; COMPLETE — QUALIFIED FOR PUBLICATION.
The final source, compiled runtime, cleanup/recovery, compatibility and Gate B
evidence are closed in [A03 evidence](task-evidence/A03/README.md). A04 and later
milestones remain UNSTARTED.

**Purpose and reason:** Keep optional/repeated blocks, alternative topologies, physical repairs and future saves from inheriting unstable union-find or collection identities.

**Architectural owner / affected systems:** BlockNamespace, descriptor/replay adapters, device-bus resolution and proposed immutable realization manifest.

**Hard prerequisites:** [A02](#m-a02).

**Must not be coupled:** No serialization of CircuitJS node numbers, arbitrary migrations, cloud accounts or mandatory mutable saves.

**Exact deliverable:** Stable role-instance and bus keys; explicit local aliases and variant-local identities; pinned generator/value/model/package/geometry/routing/proof versions; canonical resolved-choice records where needed for replay.

**Acceptance:** Insertion/reordering leaves unchanged semantic identities intact; replacing a topology does not pretend unlike internal terminals are the same; explicit buses survive lexically earlier aliases; unsupported versions reject without reinterpretation.

**Important negative tests:** Lexically earlier joined net; repeated block type; namespace collision; missing version; silently rounded long seed; stale saved cut against a different realization.

**Performance / scalability evidence:** JVM/GWT exact identity vectors and corpus replay; distinguish exact discrete identity from tolerance-based electrical reproduction.

**Architectural risk:** An all-purpose identity framework or an impossible eternal replay promise.

**Expected extension and scale effects:** Pluggability: variants declare identities locally. Scale: safe composition, caching and persistence keys.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future state/import compatibility now:** Reserve explicit versioned model-state schemas and model/program identifiers for later sequential logic and MCU providers, plus import-origin identity and interpretation-manifest references for IMPORT-1. Pure fixtures may prove field preservation and safe unknown-version rejection; no MCU or importer implementation is required here. Imported file hashes are provenance, not automatic device-bus IDs. Source drawing coordinates and transient solver nodes cannot become durable package, terminal or saved-action identities.

**Direct later dependents:** [A04](#m-a04), [A06](#m-a06), [P01](#m-p01), [P02](#m-p02), [U01](#m-u01), [U06](#m-u06), [MCU-1](#m-mcu-1), [IMPORT-1](#m-import-1).

<a id="m-a04"></a>
### A04 · Constrained provider-owned electrical construction

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Replace concrete-device construction in BoundedGeneratedBoardAssembler before more devices depend on its switches.

**Architectural owner / affected systems:** Immutable electrical realization plan, block construction providers and one restricted global allocation/binding context.

**Hard prerequisites:** [A03](#m-a03).

**Must not be coupled:** No broad leaf-family conversion, universal DSL, circuit-design math inside the assembler, or provider-owned full PCB.

**Exact deliverable:** Provider seam proven by the existing resistive canary and controlled-indicator path; terminal/part/fault mappings; a single global board/runtime; private solver-witness coordinates isolated from physical layout coordinates.

**Acceptance:** Providers allocate only their declared contributions through the context; cross-block joins are device-owned; every element/part/binding has one owner; coincident solver coordinates cannot create unintended joins; legacy versioned realizations remain qualified.

**Important negative tests:** Duplicate allocation; undeclared terminal; bypassed device join; foreign runtime; failed halfway construction; accidental coordinate contact; same recipe independently re-derived.

**Performance / scalability evidence:** Construction conformance, independent terminal correspondence and failure cleanup; record central-file edits for the two migrations.

**Architectural risk:** Replacing explicit code with a universal circuit DSL or allowing providers to construct nested live simulations.

**Expected extension and scale effects:** Pluggability: removes ordinary device knowledge from the assembler. Scale: repeatable local construction without competing owners.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future IC/import construction compatibility now:** A physical package may contain several modeled electrical units with shared supply pins; one visible package is not necessarily one CircuitJS element. Keep an explicit provider-declared unit-to-package/terminal map and honest counts. Later powered logic, display and MCU providers, and imported designs, must enter this same constrained construction context. Do not allocate a second live runtime or an imported-only assembler. Prove the mapping shape with small data/conformance canaries now; leave concrete IC/MCU/import libraries to their named lanes.

**Direct later dependents:** [A05](#m-a05), [A08](#m-a08), [A09](#m-a09), [A11](#m-a11), [E02](#m-e02), [IMPORT-1](#m-import-1).

<a id="m-a05"></a>
### A05 · Functional-role families, alternate implementations and repeated instances

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Make functional composition produce genuinely different circuits rather than repeated fixed templates.

**Architectural owner / affected systems:** Device-intent resolver, block-family/variant registry, typed assumptions/guarantees and existing value recipes.

**Hard prerequisites:** [A04](#m-a04).

**Must not be coupled:** No arbitrary netlist generator, every electronics topology, or new difficulty labels.

**Exact deliverable:** At least two qualified implementations of one role, such as BJT and NMOS low-side control, plus repeated instances and a purposeful healthy-support contribution.

**Acceptance:** The same device requirement can select structurally different valid circuits; adapters/loading assumptions are explicit; unchanged local values keep named streams; support performs a real function and participates in retest.

**Important negative tests:** Variant needing unavailable drive current; unsupported high-side substitution; unconnected decorative support; repeated-instance collision; topology chosen from selected fault metadata.

**Performance / scalability evidence:** Variant conformance and structural-diversity reports that exclude coordinate/value-only changes from topology diversity.

**Architectural risk:** False interchangeable-role guarantees or exponential unconstrained variant combinations.

**Expected extension and scale effects:** Pluggability: ordinary variants live in providers. Scale: hierarchical vocabulary with cheap incompatibility pruning.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Rolling playable integration canary:** Before dependent architecture expands, run the smallest currently supported playable composed challenge through actual generation, installation, ordinary player input, diagnosis, repair and customer retest. Exercise the new variant/provider construction path where available. This is one small integration receipt, not four new milestones or the full historical browser matrix. If the new path cannot yet host the complete loop, close that integration gap inside A05 rather than substituting a metadata-only fixture or claiming only the legacy path proves it.

**Direct later dependents:** [A10](#m-a10), [P03](#m-p03), [E02](#m-e02), [E03](#m-e03), [E04](#m-e04), [E07](#m-e07), [E09](#m-e09), [E11](#m-e11), [E13](#m-e13), [E14](#m-e14), [IMPORT-2](#m-import-2), [Q15](#m-q15).

<a id="m-a06"></a>
### A06 · Power, reference, isolation and operating-state contracts

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Design multi-rail and mains/low-voltage semantics before source and instrument implementations encode conflicting meanings.

**Architectural owner / affected systems:** Typed domain/preflight contracts, source/load capability descriptions, operating-state model and measurement-reference policy.

**Hard prerequisites:** [A03](#m-a03).

**Must not be coupled:** No regulator library, offline converter implementation, mains certification or settings-owned power semantics.

**Exact deliverable:** Explicit rail/source/reference/earth identities, isolation boundaries, allowed joins, partial-power states, source bounds, backfeed paths and stored-energy readiness requirements.

**Acceptance:** Common labels never establish a join; permitted references and source contention are checked; aggregate OFF is not mistaken for discharged or globally zero potential; differential and earth-referenced instruments have distinct declared behavior.

**Important negative tests:** Common GND strings across isolated domains; one live rail; disabled source backfed through another block; scope return short; missing reference; floating absolute voltage presented as authoritative.

**Performance / scalability evidence:** Pure state/connection matrices and small modeled fixtures, including numerical-reference sensitivity reserved for A07.

**Architectural risk:** Metadata claiming runtime isolation or turning simulator policy into safety certification.

**Expected extension and scale effects:** Pluggability: source and instrument providers declare contracts. Scale: typed multi-domain composition.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future powered-IC contract now:** Allow providers to declare power, reset, clock, brownout, digital-input thresholds, output topology/limits and partial-power/backfeed assumptions alongside analog loading/reference contracts. UNKNOWN and model-out-of-envelope states remain explicit; adding a digital output does not grant an unlimited ideal driver. These are compatibility requirements for E09/E10/MCU-2, not permission to implement an MCU during A06.

**Direct later dependents:** [A07](#m-a07), [A09](#m-a09), [P03](#m-p03), [U02](#m-u02), [E01](#m-e01), [E05](#m-e05), [E09](#m-e09), [E12](#m-e12), [MCU-1](#m-mcu-1).

<a id="m-a07"></a>
### A07 · Bounded solver execution, observation and high-risk model pilots

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Find whether CircuitJS can support the intended dynamics and proof workload before a large provider library relies on it.

**Architectural owner / affected systems:** CircuitJS adapter, serialized execution owner, stepping/settlement/observation boundary and profiling fixtures.

**Hard prerequisites:** [A01](#m-a01), [A06](#m-a06).

**Must not be coupled:** Do not wait for full E06 or Q100. Prototype hard model classes in private fixtures; no wholesale solver replacement without a recorded failed requirement.

**Exact deliverable:** Bounded simulation-time execution independent of paint; explicit numeric failures and cancellation; reference-sensitivity tests; isolated small relay/transformer/nonlinear/converter pilots; matrix-cost probes at 20/40/60/100 parts.

**Acceptance:** One solver owner per legacy static context; independent CirSim objects are not assumed isolated; observations identify settled simulation state; no fake stabilization current becomes a physical component; convergence and model limits are reported.

**Important negative tests:** Concurrent ownership attempt; stale result publication; nonfinite/singular solve; nonconvergence; time-step sensitivity; false earth reference; cancelled proof mutating the player board.

**Performance / scalability evidence:** Reduced matrix size, nonlinear subiterations, accepted steps, restamps, simulation/wall time, memory and latency. Compare model fidelity before choosing simplifications.

**Architectural risk:** Parallelizing singleton-sensitive elements or hiding a fidelity defect behind a faster ideal model.

**Expected extension and scale effects:** Pluggability: solver access through a narrow execution API. Scale: measured headroom and a path to isolated workers only if needed.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future discrete-event compatibility now:** Reserve one deterministic simulation-time event boundary for sequential IC/MCU/scan-state providers. Update causal device state at accepted simulation steps or explicitly scheduled event instants, not each browser frame or repeated nonlinear trial iteration. Simultaneous-event ordering, startup/clock phase, cancellation and a finite feedback/delta-iteration limit must be explicit. A small synthetic scheduled-state canary is sufficient here. Concrete clock, MCU and display providers qualify their actual time resolution and cost later; no CPU emulator or second electrical simulation is required.

**Direct later dependents:** [A08](#m-a08), [A09](#m-a09), [A10](#m-a10), [U02](#m-u02), [U03](#m-u03), [U10](#m-u10), [E01](#m-e01), [E06](#m-e06), [E10](#m-e10), [MCU-1](#m-mcu-1).

<a id="m-a08"></a>
### A08 · Multi-provider mutation and failure-isolated physical lifecycle

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Generalize proven resistor compensation through a second real mutable part category before relay, capacitor and copper actions proliferate.

**Architectural owner / affected systems:** PhysicalBoardRuntime, slot/part providers, mutation transaction boundary, source/measurement settlement and fresh-owner installation.

**Hard prerequisites:** [A04](#m-a04), [A07](#m-a07).

**Must not be coupled:** No universal undo engine, unbounded nested transactions or migration of every old provider in one task.

**Exact deliverable:** Restricted prepare/commit/abort interface; immutable operation intent; validation and compensation receipts; at least resistor plus diode/capacitor lifecycle conformance.

**Acceptance:** Wrong compatible replacements remain possible; original fault and secondary damage remain distinct; partial failures restore owned state or leave a declared non-actionable isolated failure; fresh installation never aliases mutable owners.

**Important negative tests:** Failure after every new write stage; stale part/slot; re-entrant action; retained probe after replacement; cleanup failure; foreign inventory entry; mutated original reused as fresh candidate.

**Performance / scalability evidence:** Targeted failure-stage matrix and actual repair/measurement flows. Record temporary and retained object ownership.

**Architectural risk:** Claiming arbitrary deep rollback from a bounded transaction or creating a global snapshot god object.

**Expected extension and scale effects:** Pluggability: common lifecycle with provider-owned electrical mutations. Scale: predictable repair cost and state integrity.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future stateful-package lifecycle now:** The prepare/commit/abort and fresh-owner contracts must accommodate a provider's explicit resettable/serializable internal state, scheduled events and drive configuration without storing executable callbacks as durable state. Removing a powered IC, replacing it, losing its supply and resetting its model are different operations. Later stateful providers extend this contract with real failure tests; do not claim generic deep rollback or implement every IC now.

**Direct later dependents:** [A11](#m-a11), [U02](#m-u02), [U06](#m-u06), [U11](#m-u11), [U12](#m-u12), [E01](#m-e01), [E03](#m-e03), [E06](#m-e06), [E08](#m-e08), [E09](#m-e09), [E11](#m-e11), [E13](#m-e13), [E14](#m-e14), [MCU-2](#m-mcu-2), [IMPORT-1](#m-import-1), [Q15](#m-q15), [X02](#m-x02), [X06](#m-x06), [X08](#m-x08).

<a id="m-a09"></a>
### A09 · Production fault hypotheses and executable diagnostic providers

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Remove ordinary production admission dependence on family-specific developer dispatch without removing the proof.

**Architectural owner / affected systems:** Production hypothesis/diagnostic service; block-owned fault and observation/repair providers; developer verifier as independent client.

**Hard prerequisites:** [A02](#m-a02), [A04](#m-a04), [A06](#m-a06), [A07](#m-a07).

**Must not be coupled:** No brute-force all-conceivable-fault catalog, multiple faults, unsafe snapshot reuse or reduced correctness to save time.

**Exact deliverable:** Canonical hypothesis IDs, eligibility and serviceability, executable observation/actions, repair equivalence semantics and device-level complaint/retest integration.

**Acceptance:** At least two implemented block/variant paths use production providers; the hypothesis set is stable and nonempty; every retained hypothesis has legal observations and a reachable repair; developer-only fixtures cannot become normal admission.

**Important negative tests:** Same owner with different fault mechanisms; missing repair; identical observations but non-equivalent repairs; hidden-answer-based plan; unavailable input/instrument; stale proof or empty proof accepted.

**Performance / scalability evidence:** Comparison with the existing serial proof on the supported corpus; independent falsifiers and actual player-action reachability.

**Architectural risk:** Renaming Task41DeveloperVerifier while leaving all family branches centralized.

**Expected extension and scale effects:** Pluggability: new fault/variant adds a provider contribution. Scale: structured hypothesis accounting for D01.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Rolling playable integration canary:** Run one real playable challenge through the new production hypotheses and proof, publication, ordinary diagnosis, physical repair and customer retest. The developer verifier may independently observe the result but may not supply a hidden action that the player cannot perform. This canary is repeated at A10 with its staging/job boundary and is not a substitute for Q15/Q30/Q60/Q100.

**Future fault vocabulary compatibility now:** Hypotheses distinguish a package defect, an external supply/reference/reset/clock failure and a symptom at an output. The phrase “MCU output dead” is not automatically an MCU-owned fault. A fault may enter normal play only when its healthy model supports the effect, the physical owner is identified, advertised observations separate non-equivalent causes, and repair/retest is reachable. The grouped long-range fault matrix in Section 6.12 maps later categories without demanding their implementation at this gate.

**Direct later dependents:** [A10](#m-a10), [A11](#m-a11), [U05](#m-u05), [E04](#m-e04), [E07](#m-e07), [E09](#m-e09), [E13](#m-e13), [E14](#m-e14), [MCU-1](#m-mcu-1), [IMPORT-1](#m-import-1), [D01](#m-d01), [X01](#m-x01).

<a id="m-a10"></a>
### A10 · Staged generation jobs, deterministic budgets and proof receipts

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Keep hierarchical generation bounded, cancellable and diagnosable before the variant space grows.

**Architectural owner / affected systems:** Generation job coordinator over immutable plans, stage results, solver/physical services and atomic publication.

**Hard prerequisites:** [A05](#m-a05), [A07](#m-a07), [A09](#m-a09), [P02](#m-p02).

**Must not be coupled:** No full Cartesian generation, arbitrary parallel solver contexts, or published PASS before final stage qualification.

**Exact deliverable:** Cheap descriptor/domain/value/demand checks; healthy solve; physical realization/access; bounded hypothesis proof; symptom projection; versioned receipts and structured rejection stages.

**Acceptance:** Expected incompatibility/routing exhaustion differs from programming error and infrastructure failure; original owner survives failed/cancelled jobs; same manifest follows the same candidate order; receipts include all consumed model/input/physical dependencies.

**Important negative tests:** Unexpected RuntimeException swallowed as an ordinary retry; altered source/load against cached proof; stale job wins publication; wall-clock race selects a candidate; missing stage receipt.

**Performance / scalability evidence:** Per-stage counts, cold/warm timings, cache provenance and cancellation latency against A01 budgets. Serial reference behavior remains available.

**Architectural risk:** A coordinator that owns every electrical rule or a cache that certifies the wrong candidate.

**Expected extension and scale effects:** Pluggability: services expose stage contracts. Scale: cheap rejection first and reproducible work ceilings.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Rolling playable integration canary:** Exercise one small real challenge through production fault hypotheses, staged generation, proof, atomic publication, ordinary player actions, diagnosis, repair and customer retest. Reuse the A09 scenario shape, not an obsolete PASS tied to different consumed code. No GUI shortcut, injected answer or empty proof is acceptable. Keep the run bounded and curated; it does not reopen Task43 or expand into a combinatorial browser matrix.

**Native/imported origin compatibility now:** Permit a resolved design origin to be native generation or a later accepted import manifest. Both converge on the same immutable plans, source controls, physical realization, fault admission, proof receipts and publication. Origin-specific ingestion cannot bypass healthy verification or produce a second PCB pipeline. Receipt keys must eventually include imported content/manifest/model versions and stateful initial-state/event contracts when consumed; shape/version canaries suffice until those lanes are implemented.

**Direct later dependents:** [U04](#m-u04), [U05](#m-u05), [U06](#m-u06), [IMPORT-1](#m-import-1), [D01](#m-d01), [Q15](#m-q15).

<a id="m-a11"></a>
### A11 · Provider conformance, registry consistency and extension-cost proof

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Make the 8–9/10 pluggability goal falsifiable rather than an architecture-document compliment.

**Architectural owner / affected systems:** Provider/registry bootstrap, reusable conformance harness and source dependency checks.

**Hard prerequisites:** [A04](#m-a04), [A08](#m-a08), [A09](#m-a09).

**Must not be coupled:** No cosmetic mass splitting of large files, automatic reflection-based discovery, or dedicated cleanup of harmless legacy menus.

**Exact deliverable:** One declared provider registration surface; tests for model/terminal/package/mutation/diagnostic consistency; an ordinary additional variant implemented as an extension exercise.

**Acceptance:** An ordinary supported variant requires no new device-specific branch in generic assembly, diagnosis, routing or UI. Novel physics may change the adapter explicitly. Missing or conflicting providers fail before publication.

**Important negative tests:** Duplicate type/version; missing footprint/renderer; inconsistent pin mapping; feature recognized in one registry but not another; generic layer importing an individual device implementation.

**Performance / scalability evidence:** Actual changed-file/category ledger, test/build cost and preserved old routes. Score by core knowledge changed, not an arbitrary two-file limit.

**Architectural risk:** Generating boilerplate or another universal registry before interfaces have real consumers.

**Expected extension and scale effects:** Pluggability: measurable provider-local extension. Scale: qualification cost grows with contracts, not combinations.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future capability negotiation now:** Model availability, package/terminal mapping, fault serviceability, observation instruments and serializable state are separate capability claims. A provider accepted electrically does not thereby qualify for physical challenge import. Allow an explicit support matrix to be generated from the same declared registrations later, without making every imported CircuitJS element or every listed future IC a current implementation obligation.

**Direct later dependents:** [U07](#m-u07), [IMPORT-1](#m-import-1), [IMPORT-5](#m-import-5), [Q15](#m-q15), [REL-A](#m-rel-a), [Q100](#m-q100).


## Physical realization

<a id="m-p01"></a>
### P01 · Physical coordinates, immutable poses and package orientation

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Decouple physical board extent from screen size and establish one truthful transform before larger layouts or layers.

**Architectural owner / affected systems:** Package geometry/placement, board coordinate contract, immutable physical snapshots and pose adapters.

**Hard prerequisites:** [A02](#m-a02), [A03](#m-a03).

**Must not be coupled:** No arbitrary continuous rotation, manufactured-millimeter claims, 3D mesh engine or route redesign.

**Exact deliverable:** Documented bounded integer/fixed-point units; package-declared rotations; separate side/mount/view transforms; copied immutable arrays/envelopes; overflow-safe coordinate operations.

**Acceptance:** Allowed rotations transform all pads, leads, courtyards, escape directions and probe surfaces together without renaming terminals; view flip is not electrical remapping; mutations cannot alter a frozen fingerprint through an exposed array.

**Important negative tests:** Aliased PcbTraceGeometry arrays; 90-degree pin-order permutation; mirrored polarity; integer overflow; nominal shape changed without version; body transformed but probe left behind.

**Performance / scalability evidence:** Exhaustive declared finite poses and independent forward/inverse transform checks; legacy geometry/version regressions.

**Architectural risk:** Repeating Task43 by giving drawing and interaction different geometry authorities.

**Expected extension and scale effects:** Pluggability: packages declare valid poses locally. Scale: larger extents without shrinking or warping parts.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Developer-only SMD architecture canaries:** Include one two-terminal 0805-style passive and one SOT-23-style three-terminal package, with an optional SOIC-style multi-pin canary when useful. Qualify package-local surface pads, a declared component mounting side, finite allowed rotations, terminal identity, body/pad/lead projection and correct front/back viewing. Board-view reflection is not a change to the mounted part's electrical pin mapping.

These fixtures test the geometry contract; they do not enable SMD gameplay, lead-lift/reflow rules, a broad package library or manufacturing DRC. Use representative geometry rather than claiming vendor-dimensional accuracy. Through-hole and surface-mount capabilities must be explicit: do not assign a drilled/plated barrel, both-face terminal or through-hole removal affordance merely because a generic package constructor used to assume one.

**Direct later dependents:** [P02](#m-p02), [P03](#m-p03), [U01](#m-u01), [E03](#m-e03), [E09](#m-e09), [E11](#m-e11).

<a id="m-p02"></a>
### P02 · Durable layer-aware copper and conductive-surface model

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Establish the physical connectivity identity needed by vias, layers, cuts and saves before routing consumers harden.

**Architectural owner / affected systems:** PhysicalRealizationPlan/conductor graph, pad/surface definitions, correspondence projection and read-only geometry views.

**Hard prerequisites:** [P01](#m-p01), [A03](#m-a03).

**Must not be coupled:** This is not a full two-layer autorouter, Gerber model, plane solver or permission to expose unqualified hidden copper.

**Exact deliverable:** Stable copper edges/junctions and provenance; top/bottom layer IDs; plated barrels, vias and non-plated holes; physical access policy; immutable pristine and mutation-owned current connectivity.

**Acceptance:** Same-layer contact and legal barrels connect exactly; projected crossing on different layers does not; a cut removes its physical edge, not every branch on a logical bus; solver projection is bijective where required and audited where one part has many internal elements.

**Important negative tests:** Via without valid layer endpoints; NPTH conducts; hidden same-net bridge; cut identity from polyline index; local rerender renames copper; unrelated layers short at a crossing.

**Performance / scalability evidence:** Small independent geometric/conductor oracle and round-trip identity fixtures. Include both layer and one-layer compatibility cases.

**Architectural risk:** Collapsing logical net identity into current connectivity or keeping mutable geometry behind proof caches.

**Expected extension and scale effects:** Pluggability: new physical actions consume conductor contracts. Scale: avoids a later layers/cuts/save format rewrite.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Surface-mount conductor/access canaries:** Consume P01's 0805-style and SOT-23-style fixtures. A surface pad contacts copper on its declared layer only and has no plated barrel unless a separate real via/barrel is explicitly present. A pin is not duplicated on both faces; a backside conductor connected through a real via is a different accessible surface with proven correspondence, not a fabricated second copy of the component terminal. Component mounting side, copper layer and viewed face are separate properties.

Positives cover valid surface-pad contact, an explicit via to another layer and front/back projection. Negatives cover a phantom barrel, opposite-layer short at the same XY coordinate, unexposed backside pin capture, rotated pin permutation and auto-generated cross-layer connectivity. Preserve these developer-only canaries in the P08 independent oracle and U01 loupe tests. No SMD-dominant/BGA product, paste/stencil model, reflow workflow or unrestricted SMD catalog is authorized.

**Direct later dependents:** [A10](#m-a10), [P03](#m-p03), [P04](#m-p04), [P06](#m-p06), [P08](#m-p08), [U01](#m-u01), [U06](#m-u06), [E05](#m-e05), [E08](#m-e08).

<a id="m-p03"></a>
### P03 · Demand-based board sizing and hierarchical placement

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Replace the fixed generic placement area with constrained physical planning suitable for multiple functional regions.

**Architectural owner / affected systems:** Device physical-demand estimator, bounded outline/aspect candidates, regional placement and local refinement service.

**Hard prerequisites:** [A05](#m-a05), [A06](#m-a06), [P01](#m-p01), [P02](#m-p02).

**Must not be coupled:** No universal CAD placer, cosmetic footprint shrinkage or electrical topology change to force a fit.

**Exact deliverable:** Package/courtyard demand, connector anchors, domain barriers, fan-out corridors and access constraints; multiple bounded outlines/aspects; hierarchical placement with limited global feedback.

**Acceptance:** RB15/RB30 and larger structural fixtures place according to real envelopes; repeated variants fit without IDs changing; local region convenience cannot strand an inter-region net; layout is independent of selected fault.

**Important negative tests:** Area-only feasibility claim; connector facing outward; disconnected island region; insufficient access; unlimited outline growth; visually labeled faulty region; impossible domain separation accepted.

**Performance / scalability evidence:** Matched flat/authored/hierarchical corpus, area/utilization definitions, rejection reasons and cost. Retain a fixed baseline before optimization.

**Architectural risk:** Rigid regions reducing routability or giant empty boards disguising poor search.

**Expected extension and scale effects:** Pluggability: roles supply physical constraints, not coordinates. Scale: explicit board demand and routing space.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [P04](#m-p04), [Q15](#m-q15).

<a id="m-p04"></a>
### P04 · Canonical multi-terminal routing and escape planning

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Route actual net demand efficiently and truthfully instead of inheriting root-star and raw-grid assumptions.

**Architectural owner / affected systems:** Net routing service, escape/channel planner, canonical path representation and versioned route scoring.

**Hard prerequisites:** [P02](#m-p02), [P03](#m-p03).

**Must not be coupled:** No exact universal Steiner optimizer, planes, clearance relaxation or change to component electrical values.

**Exact deliverable:** Typed net priorities, bounded same-net tree/trunk candidates, explicit endpoint escape constraints, canonical contact-preserving segments and deterministic tie breaking.

**Acceptance:** All required terminals join; trunks and branches retain provenance; simplification preserves escape/contact witnesses; reported bends and unique reuse are accurate; heuristic guarantees match actual edge costs.

**Important negative tests:** Orphan branch; shortcut crossing courtyard; duplicated copper reward; zero-length segment; heuristic advertised optimal while overestimating reuse costs; rail inferred from a GND string.

**Performance / scalability evidence:** Raw-versus-canonical segment counts, expansions, length, bends, congestion and matched-corpus route outcomes.

**Architectural risk:** Canonicalization erasing repair loci or new optimality claims unsupported by the search.

**Expected extension and scale effects:** Pluggability: net roles drive generic routing. Scale: shorter paths and smaller validation/render workloads.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Rolling playable integration canary:** Route an actual small generated board using the new representation, render its PCB, independently verify physical/solver correspondence, and complete ordinary probing, diagnosis, repair and retest where the challenge requires them. Isolated geometry fixtures alone do not qualify P04. Use the current accepted playable runtime and physical actions; do not add a hard prerequisite on later E08 trace-cut gameplay, U01's full navigation feature set, or Q15. The canary only requires the playable path already maintained through A05 and the available physical interaction adapter.

**Direct later dependents:** [P05](#m-p05), [P06](#m-p06), [P08](#m-p08), [IMPORT-1](#m-import-1), [Q15](#m-q15).

<a id="m-p05"></a>
### P05 · Bounded rerouting and congestion recovery

**Type / status:** Required recovery evaluation; sophisticated negotiated congestion conditional; UNSTARTED.

**Purpose and reason:** Provide principled recovery from route-order congestion before compensating with excessive links or board area.

**Architectural owner / affected systems:** Route candidate scheduler, obstacle occupancy and deterministic conflict selection.

**Hard prerequisites:** [P04](#m-p04).

**Must not be coupled:** No compulsory Pathfinder clone or universal routing guarantee. Algorithm choice follows measured PCB evidence, not FPGA analogy alone.

**Exact deliverable:** Alternate net ordering and capped rip-up/reroute with structured exhaustion. Negotiated congestion is a measured optional extension within a separately frozen sub-scope.

**Acceptance:** Recorded order-sensitive fixtures improve without relaxing final legality; only a fully valid final occupancy is published; deterministic caps stop difficult cases; expected exhaustion is not classified as code corruption.

**Important negative tests:** Unbounded retry; temporary cross-net overlap published; stale occupancy after rip-up; candidate chosen by finish time; harder fixtures silently removed.

**Performance / scalability evidence:** Compare acceptance rate, expansions, pass count and quality on held-out target fixtures. Report worse cases as well as improvement.

**Architectural risk:** Complex heuristics consuming more work than they save or crossing legality confused with search cost.

**Expected extension and scale effects:** Pluggability: generic recovery independent of device type. Scale: addresses practical congestion while keeping a finite budget.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [P07](#m-p07), [P09](#m-p09).

<a id="m-p06"></a>
### P06 · True raised factory-crossover prototype and sparse-link policy

**Type / status:** Required prototype; routine link use conditional on P09; UNSTARTED.

**Purpose and reason:** Qualify factory links as real physical crossovers before using them as a routing escape hatch.

**Architectural owner / affected systems:** Factory-link package/provider, layer/height-aware obstacle policy and construction/correspondence adapter.

**Hard prerequisites:** [P02](#m-p02), [P04](#m-p04).

**Must not be coupled:** No player repair wire semantics, unlimited jumper coverage, ideal zero-resistance stamp singularities or fake courtyard exemptions.

**Exact deliverable:** One raised link with explicit conductive endpoints and insulated/clear underpass region; stable identity and electrical backing; candidate count/density/cost policy proposal.

**Acceptance:** Copper can pass underneath only where declared; it does not contact the elevated conductor; endpoint copper contacts remain correct; link removal and probe access follow truthful package behavior.

**Important negative tests:** Ordinary axial resistor renamed zero-ohm with blocked underpass; intersecting projection short; unlimited links; link inserted without rerunning affected electrical/diagnostic proof.

**Performance / scalability evidence:** Matched difficult fixtures comparing link count, area saved, clarity and routing effort. Prototype success is not blanket normal-play adoption.

**Architectural risk:** A geometry exception for one fixture becoming invisible connectivity everywhere.

**Expected extension and scale effects:** Pluggability: one real component capability. Scale: sparse useful crossovers, not a substitute for layers.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [P07](#m-p07), [P09](#m-p09).

<a id="m-p07"></a>
### P07 · Required two-layer routing, viewing and interaction comparison

**Type / status:** Mandatory investigation and working prototype; UNSTARTED.

**Purpose and reason:** Determine with actual matched fixtures whether limited two-layer routing is the correct advanced-board strategy.

**Architectural owner / affected systems:** Layer-aware routing prototype, via provider, per-layer validation and physical-side interaction.

**Hard prerequisites:** [P05](#m-p05), [P06](#m-p06), [U01](#m-u01).

**Must not be coupled:** No more than two copper layers, full CAD feature set, plane pours or forced unrestricted via use.

**Exact deliverable:** One-layer, sparse-link, restricted-two-layer and fuller-two-layer runs on the same netlists and package sets; real top/bottom inspection, plated transitions and via penalties.

**Acceptance:** Opposite-layer crossings stay isolated; plated pads/vias join only intended copper; underside targets are reachable through explicit view/flip; primary/secondary barriers constrain both layers; no invisible underside acceptance.

**Important negative tests:** Wrong via net; via crosses forbidden domain region; top hit selects bottom-only trace; board flip remaps terminals; unrendered layer carries required repair path.

**Performance / scalability evidence:** Acceptance/rejection, runtime, expansions, via/link count, length, readability and probe/cut implications for RB30 and 56/100 structural fixtures.

**Architectural risk:** Assuming a second layer automatically doubles success, or benchmarking routing without its user-facing cost.

**Expected extension and scale effects:** Pluggability: shared layer contract across consumers. Scale: early feasibility evidence for 60/100 targets.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [P09](#m-p09).

<a id="m-p08"></a>
### P08 · Scalable physical validation and projection caches

**Type / status:** Required scale qualification; individual optimizations evidence-selected; UNSTARTED.

**Purpose and reason:** Control geometry costs while preserving the independent correctness boundary.

**Architectural owner / affected systems:** Physical validator, spatial broad-phase index, immutable geometry snapshots and projection-cache invalidation.

**Hard prerequisites:** [P02](#m-p02), [P04](#m-p04), [A01](#m-a01).

**Must not be coupled:** No spatial index solely for elegance; do not replace exact checks with bounding-box guesses.

**Exact deliverable:** Measured hotspot fixes such as broad-phase contacts, canonical paths and versioned caches; a simple brute-force reference retained for small/adversarial cases.

**Acceptance:** Fast and reference validators agree; no cache survives a changed pose, layer, copper state, package or policy; incremental edits validate all affected neighbors and connectivity, followed by periodic/full qualification checks.

**Important negative tests:** Changed array under cached hash; missed cell-boundary collision; stale deleted via; new cross-net contact outside dirty region; topology split missed by local-only checks.

**Performance / scalability evidence:** Actual candidate-pair counts, memory and validation time at each scale, including pathological dense contacts.

**Architectural risk:** Optimizing away the only independent oracle or assuming spatial indexing has a universal subquadratic worst case.

**Expected extension and scale effects:** Pluggability: uniform geometry query boundary. Scale: measured broad-phase and cache gains.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [P09](#m-p09).

<a id="m-p09"></a>
### P09 · Production physical envelope and layer-strategy qualification

**Type / status:** Required physical policy gate; UNSTARTED.

**Purpose and reason:** Freeze the actual routing/view/interaction policy before medium-board content and physical repair build on it.

**Architectural owner / affected systems:** SupportedEnvelope registry and physical pipeline qualification.

**Hard prerequisites:** [P05](#m-p05), [P06](#m-p06), [P07](#m-p07), [P08](#m-p08), [U01](#m-u01).

**Must not be coupled:** No claim of completed Q60/Q100 playability; no forced feature implementation merely because a prototype exists.

**Exact deliverable:** Versioned bounds for board size/aspect, package/pose mix, domains, net degree, route/via/link costs, access and resource usage; preferred limited-two-layer policy confirmed or rejected with evidence.

**Acceptance:** All admitted corpus boards are electrically corresponding, legible and probeable; rejection is explicit; no profile can silently disable necessary physical truth. Restrict scope honestly when an unproven policy remains.

**Important negative tests:** Part-count-only support label; untested via density; hidden layer selectable as ordinary copper; link cap bypass; route below display/access floor.

**Performance / scalability evidence:** Frozen held-out structural corpus and quality/performance distributions, not one attractive screenshot.

**Architectural risk:** A permissive envelope that accepts cases the physical pipeline cannot support or an overfitted whitelist advertised as procedural generality.

**Expected extension and scale effects:** Pluggability: content requests a declared envelope. Scale: evidence-bound 30/60/100 physical evolution.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [E08](#m-e08), [IMPORT-4](#m-import-4), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100).


## Workbench and product

<a id="m-u01"></a>
### U01 · Coherent large-board viewport and side-aware targeting

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Allow realistically dense overviews while guaranteeing truthful magnified inspection and accurate probing of exposed physical targets.

**Architectural owner / affected systems:** Workbench viewport/renderer, one forward/inverse transform, target resolver and marker projection.

**Hard prerequisites:** [P01](#m-p01), [P02](#m-p02), [A03](#m-a03).

**Must not be coupled:** No mandatory 3D engine, touchscreen redesign or answer-highlighting region map.

**Exact deliverable:** Pan, permanent zoom, fit-board/selection and functional-region navigation; separate tray chrome; board-side flipping and layer visibility; explicit ambiguous-target handling; and a cursor-centered temporary Spacebar loupe. Minimap only if navigation evidence justifies it.

**Acceptance:** Target identity survives every view operation; markers track the same physical terminal; exposed markings and targets are accurately inspectable at supported zoom/loupe scale; releasing Space restores the exact captured permanent view; flipping is not electrical remapping; hidden or occluded copper is not accidentally hit. Dense or intimidating overview appearance is allowed.

**Important negative tests:** Device-pixel-ratio/resize mismatch; oversized invisible hitbox; arbitrary overlap ownership; probe or selected part retargeted by magnification; stuck loupe after lost key-up/blur; Space typed in a dialog activating the board; wheel changing permanent zoom while held; SMD pin acquiring a false opposite-face target; tray geometry stretching with board zoom.

**Performance / scalability evidence:** Input/frame measurements on 15/30/56/100 structural fixtures and real operator probe trials.

**Architectural risk:** Two independent transform systems or level of detail that removes essential markings/copper.

**Expected extension and scale effects:** Pluggability: render providers consume a view transform. Scale: navigable larger boards without shrinking physical truth.

**Replay / versioning:** Pure camera/loupe changes do not change challenge replay, package, placement, trace, conductor, ProbeTarget or accessibility identity. A separately versioned electrical/physical realization change remains subject to the existing replay contract.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Spacebar inspection contract:** Holding Space over the active board starts temporary magnification centered on the current mouse position. Moving the pointer while held moves the inspected region. Releasing Space immediately restores the exact saved permanent camera state, rather than recomputing fit-board. Optional wheel adjustment while held affects temporary magnification only. Pan, permanent zoom, fit-board/selection, board flipping and ordinary layer visibility remain available outside the temporary gesture.

Use one composed view transform and its inverse for the displayed frame, pointer resolution and probe markers. Capture the permanent view once at activation; keep the loupe's center/scale transient. Do not recursively calculate the inspection center from an already magnified result. Rendering and hit resolution for an input event must use the same frame-consistent transformation. Pointer placement or component interaction through the loupe resolves the actual underlying accessible physical surface, with unchanged target ownership. No second hit map or giant invisible click rectangle is allowed.

The view operation must not mutate board-space coordinates, package/placement/trace geometry, conductive identity, ProbeTarget identity, hit-test ownership, solver state, challenge replay or accessibility semantics. Release, focus loss, cancellation and a modal transition end the transient view safely without leaving Space latched; repeats do not overwrite the saved view. Text inputs, browser shortcuts and unrelated controls do not become board commands. A resize may require reprojecting the same saved camera into the new viewport but must not silently change its permanent pan/zoom or run fit-board. An equivalent accessible inspection control may expose the same public physical information, never hidden diagnostic answers.

**SMD canary integration:** Reuse the developer-only 0805-style and SOT-23-style P01/P02 fixtures on their actual mounting faces. Verify exposed pads/lead surfaces through normal zoom, loupe, finite rotation and front/back views. A same-coordinate location on the opposite face is not automatically an electrical terminal. Optional SOIC canaries follow the same rule.

**Rolling playable check:** After U01, an existing small real board must pass overview, pan, permanent zoom, press/move/release loupe, supported flip/layer views, red/black probing, component interaction, diagnosis, repair and retest. Check stable physical target identity across all views and exact permanent-camera restoration. Record input latency and transient-view allocations without imposing overview comfort as a fairness rule.

**Direct later dependents:** [P07](#m-p07), [P09](#m-p09), [U02](#m-u02), [U04](#m-u04), [U08](#m-u08), [E08](#m-e08), [E11](#m-e11), [IMPORT-1](#m-import-1), [Q15](#m-q15), [X09](#m-x09).

<a id="m-u02"></a>
### U02 · Reference-aware measurements and shared observation boundary

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Provide trustworthy multi-domain and AC measurements before mains-bearing challenges depend on them.

**Architectural owner / affected systems:** Instrument providers, finite-load/stimulus adapters, observation service and source/energy readiness.

**Hard prerequisites:** [A06](#m-a06), [A07](#m-a07), [A08](#m-a08), [U01](#m-u01).

**Must not be coupled:** No direct reads of configured R/C as a substitute for active measurement; no new model fidelity promised by the instrument.

**Exact deliverable:** Consistent DC differential, AC/RMS policy, polarity, loading, clipping/overrange, sample windows and domain/reference semantics; preserve active-meter transactions and normal controls.

**Acceptance:** Each reading derives from CircuitJS samples with declared bandwidth/window; source isolation and residual energy are checked; meter loading is modeled; no absolute floating voltage is reported as meaningful earth potential.

**Important negative tests:** Wrong RMS convention; DC offset silently included/excluded; unloaded fake voltmeter; cross-reference short ignored; power-on during stimulus; stale post-mutation samples.

**Performance / scalability evidence:** Known waveforms and high-impedance/floating-domain fixtures, plus actual visible red/black input and cleanup.

**Architectural risk:** Measurement convenience altering the circuit invisibly or reporting more accuracy than the model supports.

**Expected extension and scale effects:** Pluggability: modes share observation/lifecycle services. Scale: no repeated bespoke solver manipulation per instrument.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Instrument envelope extension rule:** DC voltage, AC voltage, resistance, continuity, diode, frequency/scope and conditionally capacitance retain their real interaction contracts. U09 adds two-channel scope; U10 adds conditional logic tools; U11 adds conditional injection; U12 reserves current insertion and evidence-justified ESR. Every enabled mode states electrical loading/stimulus, range/overrange, reference, bandwidth or timing where relevant, cleanup, and visible physical operation. Listing a mode here does not enable it or create a prerequisite for an unrelated board.

**Direct later dependents:** [U03](#m-u03), [U08](#m-u08), [U10](#m-u10), [U11](#m-u11), [U12](#m-u12), [E05](#m-e05), [E09](#m-e09), [E12](#m-e12), [IMPORT-1](#m-import-1), [Q30](#m-q30), [X08](#m-x08).

<a id="m-u03"></a>
### U03 · Oscilloscope and frequency with solver-time fidelity

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Make dynamic faults observable before timers or offline conversion claim diagnostic readiness.

**Architectural owner / affected systems:** Solver observation stream, scope/frequency instrument providers and bounded waveform buffers.

**Hard prerequisites:** [U02](#m-u02), [A07](#m-a07).

**Must not be coupled:** No requirement for U09 two-channel expansion, U10 digital analysis or protocol decoding at this one-channel foundation; no switching-ripple view for a model that omits it.

**Exact deliverable:** One-channel initial scope, reference choice, trigger, time/voltage scale, finite sample policy and frequency extraction; no invented waveform for averaged models.

**Acceptance:** Waveforms and frequency agree with actual solved time; aliasing/bandwidth/insufficient-window states are explicit; buffer memory is bounded; mutation/power/probe changes invalidate observations correctly.

**Important negative tests:** UI-frame samples mistaken for simulation time; out-of-band switching drawn smoothly; reference short; stale waveform after replacement; frequency read from component metadata.

**Performance / scalability evidence:** Linear/nonlinear temporal fixtures, timestep comparisons, trigger failures and input/frame cost while solving.

**Architectural risk:** A copied legacy Scope UI carrying hidden element/global-owner assumptions into the physical workbench.

**Expected extension and scale effects:** Pluggability: temporal tools consume one observation contract. Scale: bounded data instead of unlimited sample retention.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Preserved long-range scope goal:** U03 is the one-channel foundation, not the final capability ceiling. U09 extends it to a useful two-channel troubleshooting scope with independent vertical scales, shared solver-time alignment/timebase, edge trigger, qualified AC/DC coupling, explicit references, supported differential views, frequency/period and bounded waveform retention. Advanced protocol analysis is separate and conditional, not required by either scope stage. No advertised switching trace may be synthesized from an averaged model that lacks that waveform.

**Direct later dependents:** [U09](#m-u09), [E06](#m-e06), [E07](#m-e07), [E10](#m-e10), [E13](#m-e13), [E14](#m-e14), [IMPORT-4](#m-import-4), [Q60](#m-q60), [X01](#m-x01).

**Conditional later consumers:** [E11](#m-e11) when qualifying multiplexed/scanned behavior requiring solver-time waveform observations.; [IMPORT-2](#m-import-2) when selected temporal import requires waveform/frequency observations.

<a id="m-u04"></a>
### U04 · Explicit sessions, Resources, Settings and honest catalog surface

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Provide a usable product loop without making menu or Shop code an electrical owner.

**Architectural owner / affected systems:** Session coordinator, typed launch request, public reference content, presentation settings and catalog projections.

**Hard prerequisites:** [A10](#m-a10), [U01](#m-u01).

**Must not be coupled:** No dependency on every future catalog family, scoring, economy or mobile support.

**Exact deliverable:** Menu/ticket/workbench/retest/results transitions, cancellation/error handling, seed/replay entry, generic Resources and persistent presentation/accessibility settings; existing catalogs shown truthfully.

**Acceptance:** Normal UI cannot inspect fault/private-original metadata; settings do not alter physics; Shop acquisition uses the real inventory and installation remains a separate action; unsupported features are absent or unmistakably unavailable.

**Important negative tests:** Fake cart; old session callback; hidden answer in ARIA/title; close/reopen loses owner isolation; UI determines repair success; unsupported profile accepted.

**Performance / scalability evidence:** Selected normal-player browser workflows, keyboard/modal focus and privacy inspection.

**Architectural risk:** A new frontend framework or parallel inventory built to hide placeholders.

**Expected extension and scale effects:** Pluggability: typed session and catalog requests. Scale: one product orchestration surface across many devices.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future authoring boundary:** Later CircuitJS import enters through an author/import wizard, not the ordinary technician-facing workbench's hidden-answer fields. Imported source, mapping choices and declared healthy expectations remain authoring/developer data. Normal play retains public markings, available controls and observations only. A user who authored the circuit may already know its design; this is not a promise to erase that knowledge or to secure client-side hidden information.

**Direct later dependents:** [U05](#m-u05), [U06](#m-u06), [IMPORT-1](#m-import-1), [REL-A](#m-rel-a), [X09](#m-x09).

<a id="m-u05"></a>
### U05 · Computed difficulty and staged profile calibration

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Make difficulty a proved property of diagnosis, not the number of parts or a hidden change to physics.

**Architectural owner / affected systems:** DifficultyProfile/Assessment, diagnostic evidence, physical envelope and assistance policy.

**Hard prerequisites:** [A09](#m-a09), [A10](#m-a10), [U04](#m-u04), [Q15](#m-q15).

**Must not be coupled:** Initial U05 completion does not enable HARD or PSYCHOTIC. Those need Q60/REL-B or X05 evidence respectively.

**Exact deliverable:** Versioned complexity features and initial EASY/MEDIUM calibration; explicit unavailable HARD/PSYCHOTIC states; a protocol for Q30/Q60 HARD and X05 expert calibration.

**Acceptance:** Requested profile constrains generation; admitted evidence matches; controls/markings/instruments remain available; required hypothesis reduction and legal action depth are measured without forcing one exact human route.

**Important negative tests:** Raw component count as sole score; one-probe answer despite advanced label; unavailable instrument; assistance leaking the selected owner; tolerance changes per difficulty.

**Performance / scalability evidence:** Corpus separation and user trials; later profiles require fresh calibrated receipts at their release gates.

**Architectural risk:** A formula given authority over human difficulty or duplicated per-difficulty generators.

**Expected extension and scale effects:** Pluggability: features contribute evidence, not labels. Scale: several honest difficulty dimensions.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [REL-A](#m-rel-a), [REL-B](#m-rel-b), [X05](#m-x05), [X07](#m-x07).

<a id="m-u06"></a>
### U06 · Semantic history, durable resume and distinct sharing contracts

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Avoid inventing persistence after layers, physical repairs and larger inventories already rely on transient identity.

**Architectural owner / affected systems:** Session history, versioned save/restore coordinator, part inventory serialization and replay adapters.

**Hard prerequisites:** [U04](#m-u04), [A08](#m-a08), [A03](#m-a03), [P02](#m-p02), [A10](#m-a10).

**Must not be coupled:** No scoring, multiple faults, cloud account or economy required. New E08/X02 state providers must later extend and requalify this contract.

**Exact deliverable:** Pristine descriptor sharing distinct from mutable-state artifacts; semantic operation/history schema; bounded saved state including part identity, conductor changes, source state and supported dynamic state.

**Acceptance:** Fresh reconstruction reproduces supported physical/electrical state without serializing matrix/node identities; old versions explicitly resolve or reject; stateful elements either serialize necessary internal state or expose an honest restart/resume distinction.

**Important negative tests:** Nearest-pixel cut migration; overwritten original part; resumed capacitor silently discharged; partial file accepted; stale cached proof trusted after state import; fault answer in visible share text.

**Performance / scalability evidence:** Round-trip current mutations, corrupted/incompatible saves, replay across compiled environments and bounded artifact size.

**Architectural risk:** Claiming exact mid-transient resume without preserving model state or binding saves to runtime object addresses.

**Expected extension and scale effects:** Pluggability: provider state contracts. Scale: saves survive larger designs and longer sessions.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Future provider-state and imported-artifact compatibility:** Sequential ICs and MCU models later supply bounded schemas for registers/control state, GPIO directions/latches, ADC/PWM/timer state as modeled, reset/brownout state, clock phase, simulation time and pending semantic events. Their providers requalify resume; unsupported state yields an explicit restart/incompatible result, not a misleading exact-resume claim. The current U06 gate need not wait for those providers.

Imported challenge artifacts later retain retrievable source content (or an explicitly user-resolved source dependency), its hash, parser/import versions, accepted mappings and healthy manifest alongside the normal physical/fault/proof versions. A hash without the bytes is not a reproducible import. Do not auto-fetch arbitrary embedded URLs or serialize solver matrices, transient node numbers, host closures or executable code. Imported-source sharing is deliberate; source or manifest details must not leak through normal-player report text.

**Direct later dependents:** [U07](#m-u07), [MCU-2](#m-mcu-2), [IMPORT-5](#m-import-5), [REL-B](#m-rel-b), [X02](#m-x02), [X04](#m-x04), [X07](#m-x07), [X09](#m-x09).

<a id="m-u07"></a>
### U07 · Long-session, inventory and browser reliability envelope

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Qualify the growing bench and session, not just the original component count.

**Architectural owner / affected systems:** Runtime lifetime/disposal, inventory/history/waveform retention and browser performance tests.

**Hard prerequisites:** [U06](#m-u06), [Q30](#m-q30), [A11](#m-a11).

**Must not be coupled:** No global process killing, silent history truncation or unrequested destructive cleanup.

**Exact deliverable:** Frozen long-session workloads with repeated acquisition/removal, measurement, saves, owner replacement and large loose inventories; memory/latency ownership receipts.

**Acceptance:** Legitimately retained parts stay probeable; replaced owners release task-owned handlers/graphs; active versus inactive elements are explained; performance and memory remain within the declared workload budget.

**Important negative tests:** Accidental disposal of retained loose part; new owner holds old graph; growing waveform arrays; stale receipt after reset; cleanup removes user resources.

**Performance / scalability evidence:** Growth slopes, heap/retained object proxies, active element counts, input latency and cold/warm browser comparisons. Extend at Q60/Q100.

**Architectural risk:** Calling expected inventory growth a leak or masking a leak by deleting player-owned parts.

**Expected extension and scale effects:** Pluggability: explicit lifecycle conformance. Scale: large board plus long session remains usable.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [Q60](#m-q60), [REL-B](#m-rel-b), [Q100](#m-q100).

<a id="m-u08"></a>
### U08 · Envelope-safe physical markings and dynamic indication

**Type / status:** Planned polish; required only where advertised or needed for legibility; UNSTARTED.

**Purpose and reason:** Preserve the old visual-realism goals without letting cosmetic drawing become a second geometry or electrical authority.

**Architectural owner / affected systems:** Physical render providers, immutable nameplates, solved operational-state projection and accessibility presentation.

**Hard prerequisites:** [U01](#m-u01), [U02](#m-u02), [Q15](#m-q15).

**Must not be coupled:** Not a blocker for a correctly readable alpha, solver model, relay or routing algorithm; no photorealistic/3D framework.

**Exact deliverable:** Restrained axial/pad/copper/board surface improvements and continuous LED intensity from one qualified solver-derived accessor; installed/loose presentation shares the same physical truth.

**Acceptance:** Original bands and polarity remain correct and readable; no geometry, probe or copper identity changes from cosmetic work; loose LEDs remain unpowered; signed current, saturation and replacement rebinding are handled; brightness is never the sole required diagnostic clue.

**Important negative tests:** Renderer recomputes its own brightness physics; negative current treated as positive illumination; shadow hides terminal; numeric original value leaked through accessibility text; visual change retargets a probe.

**Performance / scalability evidence:** Before/after geometry fingerprints, physical rendering correspondence, low-zoom readability and frame cost; independent intensity edge cases and an equivalent electrical observation.

**Architectural risk:** Pretty output masking a loss of physical identity or introducing a renderer-owned behavior model.

**Expected extension and scale effects:** Pluggability: providers own appearance within declared envelopes. Scale: clearer inspection with bounded drawing cost.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.


## Later bench instruments

<a id="m-u09"></a>
### U09 · Useful two-channel troubleshooting oscilloscope

**Type / status:** Planned later scope expansion; required only when advertised or consumed; UNSTARTED.

**Purpose and reason:** Extend the accepted one-channel foundation into a practical technician instrument without becoming a high-end digital scope simulator.

**Architectural owner / affected systems:** Existing observation/trigger providers, instrument-reference boundary, scope views and bounded waveform storage.

**Hard prerequisites:** [U03](#m-u03).

**Must not be coupled:** No logic analyzer, protocol decoder, arbitrary channel count or blanket Q60/Q100 prerequisite. One-channel U03 remains independently useful.

**Exact deliverable:** Two channels with independent vertical scales, a shared solver-time timebase, selectable edge trigger, qualified AC/DC coupling, frequency/period measurements, explicit input references and supported differential views. Retained waveforms use bounded storage and provenance.

**Acceptance:** Both traces represent actual electrically loaded inputs sampled under one time contract; channel skew, aliasing and insufficient windows are explicit. Shared grounds follow the selected physical instrument model; differential math does not confer isolation. Freeze/unfreeze, channel removal, power changes, mutation and board succession invalidate or retain samples according to explicit provenance.

**Important negative tests:** Channels sample different paint frames; hidden common-ground short; false isolated differential input; clipping shown as clean waveform; stale second channel after replacement; averaged converter draws invented switching pulses; unbounded history.

**Performance / scalability evidence:** Same-time known waveform/phase fixtures, timestep/window comparisons, trigger and coupling tests, real two-probe interactions and memory/frame cost on the reference host. These are future required measurements, not current results.

**Architectural risk:** Copying a rich UI before reference/loading/time semantics are shared, or conflating differential voltage arithmetic with a differential instrument.

**Expected extension and scale effects:** Pluggability: adds a consumer of U03 rather than a second sampler. Scale: two-channel costs and buffers stay bounded.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Expansion does not retroactively make U03 depend on U09. Optional advanced protocol analysis requires a later distinct product decision, not a checkbox in this milestone.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-u10"></a>
### U10 · Conditional logic probe and small logic capture

**Type / status:** Conditional on a supported digital diagnostic need; UNSTARTED.

**Purpose and reason:** Expose useful logic-state and edge observations for sequential logic, scanned displays or MCU I/O without a full logic-analyzer product.

**Architectural owner / affected systems:** Threshold/reference-aware instrument providers over the accepted solver-time observation stream.

**Hard prerequisites:** [U02](#m-u02), [A07](#m-a07), [E10](#m-e10).

**Must not be coupled:** No Saleae clone, broad protocol decoding, fixed universal logic voltage or mandatory MCU/display support.

**Exact deliverable:** Begin with a logic probe showing qualified LOW/HIGH/unknown/pulse states. Add bounded 2–4-channel edge/state capture only if a selected diagnostic policy demonstrates the need. Use explicit thresholds, hysteresis where modeled, finite loading, reference and range.

**Acceptance:** State and edge capture derive from solved pin voltages and simulation time; unknown thresholds, unpowered domains, floating pins and out-of-range signals remain honest. Capture depth/event rate is finite; reset/probe/mutation/cancel behavior is defined. A real digital challenge gains a legal observation path, not private register access.

**Important negative tests:** Reads an internal GPIO Boolean instead of loaded pin voltage; global 5 V threshold on another rail; frame-based pulses; ambiguous voltage forced HIGH; reference mismatch ignored; lost-edge data silently displayed as complete; stale event buffer.

**Performance / scalability evidence:** Compare edge timestamps and threshold classifications against independent small electrical fixtures, including close events and changed paint rates. Measure event/buffer limits and real player capture operation.

**Architectural risk:** A digital convenience instrument revealing internal state or promising bandwidth its sampling cannot support.

**Expected extension and scale effects:** Pluggability: reuses electrical reference and observation contracts. Scale: bounded state/event capture avoids waveform overcollection.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Entry receipt identifies the consuming family and why U03 or ordinary DMM observations are insufficient or needlessly burdensome. A logic probe can qualify without implementing multi-channel capture. No base qualification gate waits for this optional lane.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-u11"></a>
### U11 · Conditional bench signal generation and diagnostic injection

**Type / status:** Conditional on a real analog/control diagnostic use; UNSTARTED.

**Purpose and reason:** Support signal tracing and controlled excitation of amplifiers, filters, sensor conditioning and comparator/timing circuits using a real electrical source.

**Architectural owner / affected systems:** Existing source/stimulus registration, measurement/mutation lifecycle and deterministic waveform generation within CircuitJS.

**Hard prerequisites:** [E01](#m-e01), [U02](#m-u02), [A08](#m-a08).

**Must not be coupled:** No inject-the-correct-answer button, hidden circuit repair, unlimited ideal drive or automatic source selection based on the selected fault.

**Exact deliverable:** A bounded waveform/source set with amplitude, offset, frequency, reference, source impedance, allowed connection and current/voltage limits. The source is physically attached to a legitimate accessible point and removed through the owned lifecycle.

**Acceptance:** Injection changes the solved graph and measured response; it interacts with existing sources and loading according to its declared model. Source conflict, overrange, partial power, cancellation and board replacement have safe explicit outcomes. The player chooses the experiment; scenario metadata does not choose a secret correct waveform.

**Important negative tests:** Post-solve waveform overlay; existing source magically disabled; dangling injected element after cancellation; floating reference mistaken for ground; bypasses component fault; infinite drive hides loading; stale scheduled source event.

**Performance / scalability evidence:** Known transfer/threshold fixtures, source-contention and finite-impedance tests, actual connect/adjust/disconnect workflow and memory/cleanup under repeated use.

**Architectural risk:** Creating another independent source owner or an electrical shortcut disguised as a diagnostic aid.

**Expected extension and scale effects:** Pluggability: uses E01/A08 source and mutation contracts. Scale: one bounded source policy rather than custom injection per circuit.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Only the consuming family adds this dependency. No bench generator is required merely because E09 includes amplifiers or a future analog filter could use one.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-u12"></a>
### U12 · Conditional current insertion and advanced DMM diagnostics

**Type / status:** Conditional independent meter capabilities, not an all-modes bundle; UNSTARTED.

**Purpose and reason:** Reserve honest current and ESR diagnostics without conflating configured component parameters with actual instrument measurements.

**Architectural owner / affected systems:** DMM mode providers, accessible connection/mutation contracts, finite instrument models and capacitor model qualification.

**Hard prerequisites:** [U02](#m-u02), [A08](#m-a08), [E01](#m-e01).

**Must not be coupled:** No requirement to implement current and ESR together; no mandatory ESR for capacitance, no current clamp inferred from inaccessible internal branches, no blanket release dependency.

**Exact deliverable:** For current mode, a defensible in-series insertion path with burden, range/overrange and modeled protection/connection semantics. For ESR, only after content establishes need and a supported capacitor equivalent model exists, a finite electrical stimulus/observation method with power/discharge readiness and stated limitations.

**Acceptance:** Current display is derived from the actual inserted instrument branch under the solved circuit. Misconnection has the declared real electrical outcome or is explicitly unsupported; it is not silently repaired. ESR is derived from an actual supported test response, not a saved ESR field, and states its frequency/parallel-path limits. Each capability qualifies its own cleanup/reference/physical workflow.

**Important negative tests:** Current taken from a hidden configured branch; no insertion burden; graph not restored on exit; powered ESR stimulus; direct CapacitorElm metadata read; leakage/parallel path misreported as exact ESR; unsupported model displayed as measured zero.

**Performance / scalability evidence:** Per-capability independent known-circuit, reference, overrange and failure-restoration tests; bounded stimulus/sample windows; actual player connection and repeated-use evidence.

**Architectural risk:** A meter feature that looks familiar but teaches a physically false measurement method.

**Expected extension and scale effects:** Pluggability: shares U02 observation and A08 mutation ownership. Scale: only demanded meter providers and finite transactions are added.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** X08 remains the separate conditional capacitance milestone. A static qualified ESR-equivalent capacitor model does not inherently require X02 dynamic damage or X01 intermittency. Missing model support blocks ESR alone, not current mode or unrelated Q60/Q100 content.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.


## Electrical vocabulary

<a id="m-e01"></a>
### E01 · Source limits, external loads and protection foundation

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Establish electrical consequences before richer faults and player wires can create shorts or overloads.

**Architectural owner / affected systems:** Source/load providers, CircuitJS current-limit behavior, protection/fuse models and damage observations.

**Hard prerequisites:** [A06](#m-a06), [A07](#m-a07), [A08](#m-a08).

**Must not be coupled:** No mains certification, complete power-electronics library or dependency on trace repair.

**Exact deliverable:** Bounded low-voltage sources and loads, true disconnect, current limit/readout, elementary protection and declared stress response; real bench controls where scenario permits.

**Acceptance:** Limiting changes the solved circuit rather than clamping displayed current; backfeed and partial isolation behave consistently; unsafe simulated actions have modeled consequences or an explicit unsupported-operation result.

**Important negative tests:** Ideal source short accepted without consequence; limiter only changes UI; all-sources-off computed from one source; fuse reset silently heals a physical failed part.

**Performance / scalability evidence:** Short/overload/load-step fixtures, energy and settlement checks, real user power controls.

**Architectural risk:** Fake protection or uncontrolled ideal-source singularities.

**Expected extension and scale effects:** Pluggability: reusable source and protection contracts. Scale: multi-source realism without per-device safety hacks.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [U11](#m-u11), [U12](#m-u12), [E02](#m-e02), [E03](#m-e03), [E05](#m-e05), [E08](#m-e08), [E11](#m-e11), [E13](#m-e13), [E14](#m-e14), [IMPORT-1](#m-import-1), [Q15](#m-q15), [X02](#m-x02).

<a id="m-e02"></a>
### E02 · Rail-producing regulator implementations

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Provide real reusable 12/5/3.3 V rail behavior with distinct implementation choices.

**Architectural owner / affected systems:** Regulation block families, value/rating recipes, source/load assumptions and model providers.

**Hard prerequisites:** [E01](#m-e01), [A04](#m-a04), [A05](#m-a05).

**Must not be coupled:** No offline converter design, arbitrary buck magnetics optimizer or automatic support for every nominal rail.

**Exact deliverable:** At least a bounded linear regulation implementation and an explicitly qualified alternative or averaged switching variant; enable/dropout/load limits and physical component mappings.

**Acceptance:** Output follows input, load, reference, enable and limits through the solver; different variants meet the same declared role under different assumptions; unsupported operating regions reject.

**Important negative tests:** Hidden ideal output source independent of input; output short not reflected at input; power created without a declared model balance; misleading switching waveform from an average model.

**Performance / scalability evidence:** Input/load/enable sweeps, dissipation and timestep sensitivity, per-variant diagnostic conformance.

**Architectural risk:** A convenient abstraction hiding the failures technicians need to diagnose.

**Expected extension and scale effects:** Pluggability: interchangeable qualified rail roles. Scale: multi-rail systems without family clones.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [E04](#m-e04), [E06](#m-e06), [IMPORT-4](#m-import-4), [Q30](#m-q30).

<a id="m-e03"></a>
### E03 · Relay and switched-output families with alternate drivers

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Prove electromechanical composition and driver diversity on a useful low-voltage controlled load.

**Architectural owner / affected systems:** Relay package/model, BJT/NMOS driver providers, protection and fault/mutation contracts.

**Hard prerequisites:** [E01](#m-e01), [A05](#m-a05), [A08](#m-a08), [P01](#m-p01).

**Must not be coupled:** No contact arcing/EMC certification or mains load admission before E05 and the applicable instruments.

**Exact deliverable:** Coil/contact separation, genuine inductive/flyback behavior in the declared envelope, at least two driver variants and meaningful coil/contact/driver fault owners.

**Acceptance:** Contacts do not join control and load domains; physical terminals map correctly; coil and contact failures are distinguishable with available actions; replacement and energized/de-energized retest work.

**Important negative tests:** Shared return introduced across relay isolation; incorrect terminal order; off-contact leakage ignored by a claimed diagnostic; missing flyback behavior; driver variant lacks control drive.

**Performance / scalability evidence:** Existing RelayElm limitations explicitly qualified; small dynamic fixtures plus normal physical repair and retest.

**Architectural risk:** Assuming an inherited approximate relay model already supports all requested diagnostic mechanisms.

**Expected extension and scale effects:** Pluggability: real variant and multi-terminal provider proof. Scale: reusable output channels, not copied device branches.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [E14](#m-e14), [IMPORT-4](#m-import-4), [Q15](#m-q15), [Q30](#m-q30).

**Conditional later consumers:** [MCU-2](#m-mcu-2) when selected MCU board uses the qualified relay/output family.

<a id="m-e04"></a>
### E04 · Sensor conditioning, references and control decisions

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Add unfamiliar but understandable control behavior beyond a single high/low switch.

**Architectural owner / affected systems:** Sensor/stimulus, conditioning, divider/reference and comparator/interlock providers.

**Hard prerequisites:** [E02](#m-e02), [A05](#m-a05), [A09](#m-a09).

**Must not be coupled:** No broad op-amp library, unbounded noisy analog campaign or fake sensor animation.

**Exact deliverable:** Player-operated sensor conditions, at least two compatible conditioning/control implementations, explicit loading/hysteresis where modeled and bounded fault loci.

**Acceptance:** A player can sweep relevant conditions and observe actual threshold/state behavior; thresholds and references are solver-backed; healthy support has purpose; assumptions compose with the selected rail.

**Important negative tests:** Hidden stimulus only verifier can set; comparator output asserted from metadata; undefined reference; ambiguous failed sensor versus missing supply with no legal separating observation.

**Performance / scalability evidence:** Boundary/threshold/loading matrices, diagnostic plans and ordinary input/retest workflows.

**Architectural risk:** A general state-machine engine introduced before concrete control uses establish its contract.

**Expected extension and scale effects:** Pluggability: alternate sensor/control roles. Scale: realistic interactions and richer fault hypotheses.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Vocabulary placement:** This bounded sensor/comparator proof remains unchanged in size. It establishes the seam later used for thermistors, photoresistors, Hall/reed/limit/pressure/position/current-sense inputs, potentiometers and trimmers. A stimulus needed for diagnosis must be player-operable and affect the modeled input. E09 extends explicit comparator/Schmitt/op-amp/buffer/amplifier/reference/filter IC models with supply, common-mode, output, loading and saturation limits; it is not a new prerequisite for this existing E04 proof.

**Direct later dependents:** [Q30](#m-q30).

**Conditional later consumers:** [MCU-2](#m-mcu-2) when selected MCU board uses the qualified sensor/conditioning family.

<a id="m-e05"></a>
### E05 · AC input, rectification, bulk energy and isolation qualification

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Build the physical/electrical mains foundation before an appliance board can be advertised.

**Architectural owner / affected systems:** AC source, bridge/diode, capacitor, transformer/isolation, protection and measurement providers.

**Hard prerequisites:** [E01](#m-e01), [A06](#m-a06), [U02](#m-u02), [P02](#m-p02).

**Must not be coupled:** No physical high-voltage construction guide, actual equipment safety certification, EMI/filter-attenuation claim or complete offline converter yet.

**Exact deliverable:** Bounded simulated 120 VAC input path, rectification/bulk storage, discharge behavior, separate reference domains and traceable safety-related physical constraints without certification claims.

**Acceptance:** Waveform conventions and energy state are explicit; isolated returns are not joined by numerical stabilization; AC/DC instruments show supported observations; each visible protection/power component has a causal modeled role.

**Important negative tests:** RMS/peak convention mismatch; output alive with disconnected source and no energy source; bulk capacitor instantly cleared by OFF; isolated domains silently grounded; NPTH or opposite-layer copper bypasses barrier.

**Performance / scalability evidence:** Small independently checked AC/rectifier/storage/isolation fixtures and model sensitivity before RB56 integration.

**Architectural risk:** Teaching false reference or discharge behavior because simplified models were not qualified.

**Expected extension and scale effects:** Pluggability: foundational mains-side providers. Scale: credible mixed-domain composition.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [E06](#m-e06), [E13](#m-e13), [Q60](#m-q60), [X08](#m-x08).

**Conditional later consumers:** [IMPORT-4](#m-import-4) when input includes accepted AC/mains/isolation behavior.

<a id="m-e06"></a>
### E06 · Causal offline-converter model and fidelity decision gate

**Type / status:** Required advanced-board blocker and early model decision; UNSTARTED.

**Purpose and reason:** Test the hardest appliance-board model before a 56-part product is built around an ideal-supply shortcut.

**Architectural owner / affected systems:** Power-conversion block/model provider, fidelity contract and independent electrical/diagnostic qualification.

**Hard prerequisites:** [E05](#m-e05), [E02](#m-e02), [A07](#m-a07), [U03](#m-u03), [A08](#m-a08).

**Must not be coupled:** No commercial SMPS design tool, magnetics optimization, EMC certification or hidden external physics engine replacing CircuitJS.

**Exact deliverable:** An isolated offline-conversion proof with input dependence, startup/enable/feedback/load response, power-flow assumptions and bounded fault response. Compare detailed versus averaged implementations on explicit observables.

**Acceptance:** Every visible external switch, transformer, rectifier, capacitor and feedback part participates causally; omitted dynamics are disclosed and unavailable as observations/faults; an opaque module counts as one package, not twelve decorative parts.

**Important negative tests:** Feedback resistor disconnected with no effect; input fuse open but output remains powered; averaged model displays invented switch pulses; isolated transformer drawn over shared ground; unsupported oscillatory transient declared stable.

**Performance / scalability evidence:** Small high-risk model pilots run as early as A07 permits; E06 freezes fidelity, convergence and runtime evidence before Q60. Include input/output energy and reference sensitivity.

**Architectural risk:** A model that is fast but diagnostically dishonest, or too detailed to meet the required interactive budget.

**Expected extension and scale effects:** Pluggability: explicit fidelity-aware converter implementations. Scale: enables realistic mains regions without transistor-level modeling of everything.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [Q60](#m-q60).

**Conditional later consumers:** [IMPORT-4](#m-import-4) when input includes the accepted offline-converter model.

<a id="m-e07"></a>
### E07 · Triggered timers, oscillators and frequency behavior

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Add temporal difficulty only after players have truthful ways to observe it.

**Architectural owner / affected systems:** Timer/oscillator block variants, trigger/stimulus capabilities and temporal diagnostic contracts.

**Hard prerequisites:** [U03](#m-u03), [A05](#m-a05), [A09](#m-a09).

**Must not be coupled:** Not required for static Q30/Q60 variants unless their advertised behavior uses it; no MCU/protocol framework.

**Exact deliverable:** One triggered timing role and one periodic-signal role with bounded distinct implementations, solver-time behavior and repairable faults.

**Acceptance:** Timing/frequency derive from solved state; player triggers and sampling windows can reproduce the diagnostic plan; unsupported high-frequency behavior remains unavailable.

**Important negative tests:** Configured component value returned as measured frequency; hidden trigger; timing based on paint speed; aliasing mistaken for real fault; reset erases unexplained stored state.

**Performance / scalability evidence:** Step-size and bandwidth qualification plus normal trigger/probe/retest sequences.

**Architectural risk:** Inventing a scripted logic simulator beside CircuitJS or assuming all temporal faults are separable.

**Expected extension and scale effects:** Pluggability: temporal providers use existing role/instrument seams. Scale: useful complexity without unrestricted digital systems.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Explicit 555-style implementation family:** Include a qualified 555-style timer as an important implementation candidate for monostable, astable and triggered timing, with supply, trigger, reset/enable, threshold and control-pin behavior within a declared envelope. Qualify each supported configuration rather than claiming every 555 variant or exact commercial part behavior. External timing resistors/capacitors and control/reset connections must causally affect timing and outputs.

Compare suitable 555-based, transistor/RC, comparator/Schmitt and later qualified timing-IC implementations under the same functional role. E10 later adds crystals, ceramic resonators, RC clocks and oscillator modules where useful; a real clock must disappear or change when its modeled external clock network fails. Do not advertise RF-level crystal/startup fidelity or a waveform absent from the chosen model. Surrounding-component faults may explain an incorrect frequency without assuming the timer package is dead.

**Direct later dependents:** [IMPORT-4](#m-import-4).

**Conditional later consumers:** [E10](#m-e10) when selected sequential/clock variant uses the accepted timer/oscillator implementation.; [IMPORT-3](#m-import-3) when input uses a supported 555/timer implementation.

<a id="m-e08"></a>
### E08 · Player repair jumpers, copper cuts and physical restoration

**Type / status:** Planned important repair capability; a required dependency only for content or releases consuming these actions; UNSTARTED.

**Purpose and reason:** Add general repair actions only after copper identity, layers and source consequences are coherent.

**Architectural owner / affected systems:** Mutation-owned conductor graph, player wire inventory, accessible-surface targets and solver projection.

**Hard prerequisites:** [E01](#m-e01), [A08](#m-a08), [P02](#m-p02), [U01](#m-u01), [P09](#m-p09).

**Must not be coupled:** No arbitrary CAD editing, automatic correct repair, infinite current protection or claim of supported saved repair state until U06 requalification.

**Exact deliverable:** Distinct player jumper objects; selected copper-edge cuts; direct restoration or bypass; layer/side access; history/save provider extensions and functional retest.

**Acceptance:** Cut opens the selected physical edge, preserving other same-net branches; wrong wiring has real consequences; permitted alternative repairs can pass; flip/zoom/save do not retarget actions; underpasses remain physically distinct.

**Important negative tests:** Cut all logical net branches; cut hidden underside from top view; factory link confused with player wire; renamed segment after simplification; jumper destroys unrelated probe identity.

**Performance / scalability evidence:** Branched nets, plated pads, vias, parallel paths and repair-equivalent outcomes; actual visible operation and failure rollback.

**Architectural risk:** A renderer-owned cut system or persistence tied to route array indices.

**Expected extension and scale effects:** Pluggability: actions consume conductor contracts. Scale: one repair model for both one- and two-layer boards.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

**Conditional later consumers:** [Q60](#m-q60) when selected Q60 family advertises or consumes trace cutting, player-installed jumper, physical trace restoration or bypass repair.


## Later electrical vocabulary

<a id="m-e09"></a>
### E09 · Powered analog and combinational IC provider vocabulary

**Type / status:** Planned later grouped IC capability; consumed families select the subset; UNSTARTED.

**Purpose and reason:** Make ordinary small IC packages explicit extensions rather than ideal logic widgets with hidden supplies or a central new circuit dispatcher.

**Architectural owner / affected systems:** Versioned electrical model/block providers, shared-package terminal mapping, package geometry, fault/repair and reference-aware observations.

**Hard prerequisites:** [A05](#m-a05), [A06](#m-a06), [A08](#m-a08), [A09](#m-a09), [U02](#m-u02), [P01](#m-p01).

**Must not be coupled:** No universal gate-level HDL, entire op-amp library, firmware engine, fake rail-to-rail guarantee or automatic Q60/Q100 dependency.

**Exact deliverable:** A common powered-IC contract plus bounded analog and combinational provider subfamilies. Analog coverage includes comparator/Schmitt comparator, op-amp, follower/buffer, amplifier and threshold/reference/active-filter variants where useful. Logic coverage includes inverter, AND/OR/NAND/NOR/XOR and Schmitt-trigger functions. Select representative initial implementations and record exactly which are qualified.

**Acceptance:** Real supply/input/output pins, permitted supply/common-mode ranges, input thresholds, output limits, finite drive/loading, saturation and power-loss behavior match the selected model. Multi-unit packages retain one physical owner and correct shared pins. At least one analog and one combinational circuit passes physical probing, a meaningful fault, package-level repair and retest.

**Important negative tests:** Unlimited ideal output; output active after supply loss; missing reference; hidden supply pin; duplicated physical package for schematic units; unsupported rail-to-rail operation accepted; failure mode not supported by healthy model; wrong pin order.

**Performance / scalability evidence:** Small independent operating-envelope and loading matrices, allowed/invalid package maps, actual pin measurements and end-to-end variant conformance. Record nonlinear/convergence cost instead of assuming every model is cheap.

**Architectural risk:** Naming familiar ICs without their relevant electrical limits or making a universal abstraction from one example.

**Expected extension and scale effects:** Pluggability: powered analog/logic variants remain provider-local. Scale: useful control diversity without per-device engine edits.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** E04 remains the earlier bounded comparator/sensor proof and is not rewritten. E07 may implement a bounded 555-style provider before this broader vocabulary; shared contracts can later be reconciled without making every analog/logic type a timer prerequisite.

**Direct later dependents:** [E10](#m-e10), [E12](#m-e12), [MCU-2](#m-mcu-2), [IMPORT-3](#m-import-3).

<a id="m-e10"></a>
### E10 · Sequential logic, clocks and reset-state vocabulary

**Type / status:** Planned later stateful electrical capability; UNSTARTED.

**Purpose and reason:** Add real sequencing causes such as missing clock, held reset or loading rather than diagnosing every control symptom as a dead package.

**Architectural owner / affected systems:** Stateful IC providers using accepted simulation-time events, clock/reset models and physical pin/fault contracts.

**Hard prerequisites:** [E09](#m-e09), [A07](#m-a07), [U03](#m-u03).

**Must not be coupled:** No universal digital simulator, RF oscillator fidelity, arbitrary firmware, MCU prerequisite or browser-frame-driven state.

**Exact deliverable:** Representative SR latch, D flip-flop, useful JK flip-flop, counter and simple shift-register providers, qualified incrementally. Explicit clock/reset/set, startup initialization, power-loss/brownout where modeled and output loading. Include bounded crystal/resonator, oscillator-module or RC clock alternatives according to actual content needs.

**Acceptance:** Transitions occur on declared simulation-time events and loaded pin conditions. Reset/set/clock priority and indeterminate/out-of-envelope regions are specified. A missing external clock stops the stateful function when that clock is selected; power/reset loss behaves causally. Internal state can be reconstructed or explicitly resumed under U06 when advertised. At least one future reference variant exercises stateful logic, an IC package and observable encoded/status output.

**Important negative tests:** State advances per paint or twice per nonlinear iteration; spontaneous default HIGH from unknown input; hidden clock persists after external oscillator removal; reset ignored on power change; invalid initialization silently accepted; loaded output differs from unmeasured internal bit.

**Performance / scalability evidence:** Finite truth/state transition cases, bounded simultaneous-edge/feedback cases, clock-phase and timestep comparisons, clock-loss/reset diagnosis and real repair/retest. Measure events per accepted step and retained state.

**Architectural risk:** Unbounded zero-time event loops or deterministic-looking behavior unsupported at the model timing boundary.

**Expected extension and scale effects:** Pluggability: stateful parts use shared lifecycle/time contracts. Scale: finite state/event vocabulary gives meaningful complexity without CPU-scale simulation.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** External clock components matter when visible and functionally selected. A model declaring an internal oscillator must not draw an inert external crystal. Sequential source/clock fixtures use real electrical events; later MCU implementation is not needed to test them. E07 is required only for a variant actually using its timer implementation, not all E10 providers.

**Conditional prerequisites:** [E07](#m-e07) only when selected sequential/clock variant uses the accepted timer/oscillator implementation.

**Direct later dependents:** [U10](#m-u10), [MCU-2](#m-mcu-2), [IMPORT-3](#m-import-3).

<a id="m-e11"></a>
### E11 · Seven-segment and simple scanned-display providers

**Type / status:** Planned later display capability; staged static then scanned support; UNSTARTED.

**Purpose and reason:** Make missing segments, dim digits, failed commons and scan/driver problems into real electrical symptoms rather than drawing hidden scenario numbers.

**Architectural owner / affected systems:** Display package/model/renderer providers, segment-current observations and existing source/drive/physical repair services.

**Hard prerequisites:** [A05](#m-a05), [A08](#m-a08), [E01](#m-e01), [P01](#m-p01), [U01](#m-u01).

**Must not be coupled:** No mandatory MCU or E10 for a directly driven single digit; no graphical LCD/OLED system, layout answer leak or display requirement for base Q60/Q100.

**Exact deliverable:** Single-digit seven-segment LED package with explicit common-anode/common-cathode alternatives, actual segment pins, current limiting and justified open-segment faults. Later bounded multi-digit multiplexing, driver ICs, scanned status/error codes, bargraphs or indicator arrays as demanded.

**Acceptance:** Pin maps and segment currents agree in electrical, package, rendered and probe views. Common/segment resistance and drive limits are real. Multiplexed light output is a view of actual simulation-time drive/current history with bounded integration; no scenario-number shortcut. Missing common, driver/scan, supply or segment causes a distinct modeled symptom and lawful repair/retest.

**Important negative tests:** Numeral read from fault/scenario metadata; common polarity silently swapped; no current limiting; LED lit without current; visual refresh creates scan timing; dead digit displayed correctly by UI; each internal segment counted as a separate package; unbounded brightness history.

**Performance / scalability evidence:** Finite segment/pin and polarity matrix, current/drive cases and real player symptom diagnosis. Multiplexed qualification adds solver-time waveform/duty-cycle tests and bounded render/storage cost.

**Architectural risk:** Rendering a plausible display while the electrical model never actually drives its pins.

**Expected extension and scale effects:** Pluggability: one package/display provider serves direct, logic and later MCU drive. Scale: multiplexing is bounded and shares observations.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Conditional prerequisites:** [U03](#m-u03) only when: Qualifying multiplexed/scanned behavior requiring solver-time waveform observations.

**Capability boundary:** Initial direct-drive qualification has no MCU dependency. Scanned-display qualification adds U03 for required time observations and uses qualified electrical drivers or a small deterministic electrical test source. A whole logic/MCU-controlled display board consumes both providers in its own family manifest; the display provider and MCU provider must not hard-depend on each other.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

**Conditional later consumers:** [MCU-2](#m-mcu-2) when selected MCU board uses a seven-segment/scanned display.; [IMPORT-3](#m-import-3) when input uses the qualified display provider.

<a id="m-e12"></a>
### E12 · Specialized driver, reference and interface IC families

**Type / status:** Planned later demand-selected provider groups; UNSTARTED.

**Purpose and reason:** Cover high-value appliance/control building blocks while keeping each implementation bounded and independently qualified.

**Architectural owner / affected systems:** Electrical model and family providers, explicit pin/package mappings, assumptions/guarantees and diagnostic conformance.

**Hard prerequisites:** [E09](#m-e09), [A06](#m-a06), [U02](#m-u02).

**Must not be coupled:** No requirement to build every named IC or a new milestone per device; no implicit isolation, perfect reference or unlimited drive.

**Exact deliverable:** Incremental provider groups for ULN2003-style transistor-array drivers; analog switches and mux/demux; voltage references/current-sense amplifiers; optocouplers/qualified isolated digital interfaces; display/simple power drivers; reset/brownout supervisors where useful.

**Acceptance:** Each selected group declares supplies, shared pins, references, loading, enable/selection behavior, output limits, isolation where modeled and a credible operating envelope. A visible optocoupler has separate causal input/output sides; a mux routes an actual modeled path; an array shares one physical package with defined channel semantics. Faults are serviceable only at honest physical boundaries.

**Important negative tests:** Labels substitute for isolation; ideal mux ignores on-path/loading behavior; wrong common pin; one failed array channel becomes a fictitious loose package; disabled driver still supplies current; reference unaffected by removed causal parts; unsupported transient claim.

**Performance / scalability evidence:** Group-specific small operating/selection/loading matrices and one meaningful integrated diagnostic case per adopted provider; exact supported and unsupported functions are published.

**Architectural risk:** An enormous undifferentiated IC library or false equivalence among devices sharing a name.

**Expected extension and scale effects:** Pluggability: ordinary driver/interface additions use provider contracts. Scale: heterogeneous functions without generic-engine surgery.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Group acceptance is incremental and consumers require only the group used. No broad catalog-complete receipt is inferred from the first transistor-array example. Keyed/ribbon multi-pin interconnect can mature through existing package/interconnect providers without becoming a separate required electronics engine.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

**Conditional later consumers:** [IMPORT-3](#m-import-3) when input uses an accepted specialized IC/interface group.

<a id="m-e13"></a>
### E13 · AC semiconductor switching and zero-cross control

**Type / status:** Planned later AC-control family; not required for relay-only Q60; UNSTARTED.

**Purpose and reason:** Represent useful heater, AC motor/solenoid and optically isolated control alternatives beyond relay contacts.

**Architectural owner / affected systems:** SCR/TRIAC/optotriac/zero-cross block providers, source/load models, isolation/reference policy and temporal diagnostics.

**Hard prerequisites:** [E05](#m-e05), [E01](#m-e01), [A05](#m-a05), [A08](#m-a08), [A09](#m-a09), [U03](#m-u03).

**Must not be coupled:** No full mains transient, RF/EMC, contact-arcing or commercial-device fidelity; no dependency for a baseline relay-controlled appliance family.

**Exact deliverable:** Representative SCR/TRIAC latching paths, optotriac/optocoupler input/output behavior, zero-cross detection/control and a bounded switched-load application. Qualify only the selected phase/zero-cross/load modes.

**Acceptance:** Gate/control, latching/holding and supported commutation behavior arise from the modeled current/voltage/time state. Isolation and zero-cross observation use actual terminals. Drive loss, load change, stuck/open switch and missing zero-cross causes remain observable and repairable with the advertised tools.

**Important negative tests:** Boolean switch with no latching behavior while advertised as a TRIAC; zero crossing read from a scenario clock; isolated sides share a physical return; inductive behavior claimed outside envelope; missing input power but load remains driven; unavailable scope observation.

**Performance / scalability evidence:** Known AC source/load and gate-timing fixtures, current-zero/voltage-zero distinctions when supported, timestep sensitivity and actual diagnosis/retest under the declared load envelope.

**Architectural risk:** A familiar mains-control symbol teaching electrical behavior the simplified model does not preserve.

**Expected extension and scale effects:** Pluggability: AC output functions are alternate qualified implementations. Scale: realistic appliance diversity within a bounded temporal budget.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** E05/E06 remain the native mains/power foundation. A particular AC-switching family adds E13; Q60/Q100 do not inherit it merely because AC exists elsewhere on a reference board.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

**Conditional later consumers:** [IMPORT-4](#m-import-4) when input uses accepted semiconductor AC switching.

<a id="m-e14"></a>
### E14 · Bounded reversing, H-bridge and motor/load control

**Type / status:** Planned later motor-control family; UNSTARTED.

**Purpose and reason:** Support diagnosis of direction/enable, drive, supply and protection in electrical reversing systems without an embedded motor-control project.

**Architectural owner / affected systems:** Relay/discrete/package output-stage providers, bounded external load/plant models and source/diagnostic services.

**Hard prerequisites:** [E01](#m-e01), [E03](#m-e03), [A05](#m-a05), [A08](#m-a08), [A09](#m-a09), [U03](#m-u03).

**Must not be coupled:** No detailed motor commutation, arbitrary firmware, full mechanics/fluid simulation or off-board loads counted as PCB parts.

**Exact deliverable:** Compare relay reversing, discrete BJT/MOSFET H-bridge and simple packaged-driver variants for an explicitly supported load. Include current path, permitted switching sequence, protection, direction and enable semantics.

**Acceptance:** Actual solved output/current changes with drive, supply and load; illegal overlap or shoot-through has the selected real modeled consequence or is explicitly unsupported, not silently repaired. Player-operated load/stimulus and allowed protection/current limit behavior are declared. External feedback is modeled only when the family consumes it.

**Important negative tests:** Motor animation declares direction with no electrical change; high/low drives ignore reference; reverse command bypasses power limits; impossible ideal driver current; clockless fake PWM; missing flyback/protection effect; invisible second source drives load.

**Performance / scalability evidence:** Small direction/enable/load and drive-failure corpus, timing/current-limit tests, output probing and repair/retest. Measure solver/event cost for each implementation before larger integration.

**Architectural risk:** A plant abstraction hiding the electrical symptom or a driver model too detailed for the advertised interactive envelope.

**Expected extension and scale effects:** Pluggability: alternative output implementations satisfy explicit role contracts. Scale: causal off-board loads keep PCB counts and runtime honest.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

**Conditional later consumers:** [IMPORT-4](#m-import-4) when input uses the accepted motor-control family.


## Basic microcontroller lane

<a id="m-mcu-1"></a>
### MCU-1 · Bounded microcontroller approach comparison and contract

**Type / status:** Planned later required decision before basic MCU implementation; UNSTARTED.

**Purpose and reason:** Choose a credible basic appliance-controller model before firmware/runtime assumptions fossilize.

**Architectural owner / affected systems:** Model fidelity and electrical/event-state contract design; read-only comparative prototype/evidence work when this future milestone is authorized.

**Hard prerequisites:** [A03](#m-a03), [A06](#m-a06), [A07](#m-a07), [A09](#m-a09).

**Must not be coupled:** No Arduino/PlatformIO/MPLAB substitute, general firmware emulator, CPU architecture project or requirement that Q60/Q100 use an MCU.

**Exact deliverable:** Compare deterministic behavioral MCU, constrained state-machine/control script, limited emulation of one tiny architecture and a justified narrow hybrid/alternative. Use the same bounded GPIO/reset/ADC/PWM/sensor-interlock fixture and select one method with an explicit support envelope.

**Acceptance:** Chosen method states pin drive/loading, supplies/reset/brownout, clocks, initialization, control-state/time ownership, deterministic replay, resume support, observables, failure/repair semantics, program format and execution budgets. No arbitrary firmware promise. A qualified black-box application can be diagnosed electrically without decompilation.

**Important negative tests:** Selection justified only by model name; output continues without VCC/reset; external crystal is decorative; program reads hidden fault ID; event time uses browser frames; unbounded control script; save format stores executable callbacks.

**Performance / scalability evidence:** Matched limited prototypes or executable feasibility fixtures, exact model versions, event/solve counts, source-loading cases, memory and reference-host runtime. Explain any untested alternative instead of claiming a benchmark comparison that was not run.

**Architectural risk:** Choosing realistic-sounding emulation that consumes the project, or a behavioral model with fake causality.

**Expected extension and scale effects:** Pluggability: a small qualified control-program/IO interface rather than firmware-specific core branches. Scale: explicit time/event/state budgets.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Basic MCU support remains a planned destination even if the first candidate approach fails. Record a bounded redesign and missing proof, not a claim that all firmware or all appliance MCUs are supported. Real firmware execution, if later selected at all, remains one narrow separately approved product scope.

**Direct later dependents:** [MCU-2](#m-mcu-2).

<a id="m-mcu-2"></a>
### MCU-2 · Causal basic MCU runtime and appliance-control integration

**Type / status:** Planned later bounded MCU implementation and qualification; UNSTARTED.

**Purpose and reason:** Implement the selected qualified black-box controller and prove real electrical operation around and through it.

**Architectural owner / affected systems:** Powered stateful MCU package/model provider, deterministic bounded program/control contract, accepted event scheduler and provider-state serialization.

**Hard prerequisites:** [MCU-1](#m-mcu-1), [E09](#m-e09), [E10](#m-e10), [A08](#m-a08), [U06](#m-u06).

**Must not be coupled:** No arbitrary firmware import/IDE, required decompilation, hidden-state diagnostic answer or automatic requirement for every reference board.

**Exact deliverable:** Powered/unpowered/reset and modeled brownout states; GPIO directions/pulls/thresholds/finite drive; limited ADC-like inputs; PWM/timer outputs; deterministic startup and a sensor/interlock/output application. Expose only supported clock/ADC/peripheral behavior. Include a board integration and resume/replay receipt.

**Acceptance:** VCC/reset/clock/input changes causally alter operation; a loaded output is measured electrically rather than by reading its internal latch. External resistors/oscillators/reset/drivers/sensors participate where shown. Program/model/state versions replay exactly at declared discrete semantics with numerical tolerances stated. A meaningful fault can be isolated and repaired using ordinary tools without hidden firmware knowledge.

**Important negative tests:** Supply absent but outputs still driven; reset does not cancel output/events; duplicate time advancement; unqualified ADC precision; output stuck fault indistinguishable from missing pull-up but admitted; corrupted snapshot silently accepted; failed proof publishes control state to current player owner.

**Performance / scalability evidence:** Startup/power/reset/brownout and direction/loading matrices; clock/ADC/PWM time fixtures; fresh-process state resume; varied paint-rate comparison; cancelled-job isolation; one actual board diagnosis/repair/retest and measured event/memory/runtime cost.

**Architectural risk:** Invisible control software bypassing CircuitJS or persisted internal state making replay noncausal.

**Expected extension and scale effects:** Pluggability: MCU variants/programs consume provider-local contracts. Scale: bounded state and peripherals instead of an arbitrary embedded platform.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** A planned RB56-MCU variant may add E11 seven-segment status and E03/E04 sensor/output providers; its own capability manifest carries those dependencies. MCU-2 can first qualify with a smaller LED/output board and need not wait for Q60 or Q100. Later missing-clock, stuck-pin or dead-package faults require separate supported effect/repair evidence; modeled-state corruption cannot require decompiling firmware.

**Conditional prerequisites:** [E11](#m-e11) only when selected MCU board uses a seven-segment/scanned display. [E03](#m-e03) only when selected MCU board uses the qualified relay/output family. [E04](#m-e04) only when selected MCU board uses the qualified sensor/conditioning family.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

**Conditional later consumers:** [IMPORT-3](#m-import-3) when input uses the specifically supported basic MCU model; not arbitrary firmware.


## CircuitJS import-to-challenge lane

<a id="m-import-1"></a>
### IMPORT-1 · CircuitJS import foundation and passive/DC challenge proof

**Type / status:** Planned later complete bounded import vertical slice; UNSTARTED.

**Purpose and reason:** Establish the entire honest source-to-playable pipeline on small supported passive/DC circuits before expanding its parser/model coverage.

**Architectural owner / affected systems:** Isolated CircuitJS parser/load adapter, import capability matrix and author manifest/wizard feeding native plan/qualification/publication services.

**Hard prerequisites:** [A03](#m-a03), [A04](#m-a04), [A08](#m-a08), [A09](#m-a09), [A10](#m-a10), [A11](#m-a11), [P04](#m-p04), [U01](#m-u01), [U02](#m-u02), [U04](#m-u04), [E01](#m-e01).

**Must not be coupled:** No universal CircuitJS-file promise, parallel imported-board renderer, arbitrary external downloads/scripts, automatic healthy-intent invention or change to native generation.

**Exact deliverable:** Retained source/hash/parser/import versions; exact per-element classification into the six Section 5.7 dispositions; stable logical IDs and package/unit/pin mappings; source/control/load role choices; author-declared healthy states/outputs; native PCB, seeded serviceable fault and a complete diagnosis/repair/retest challenge for the finite passive/DC subset.

**Acceptance:** Parsing occurs in an owned staging context and cannot alter current play. Every source element is accounted for. Original connectivity matches the interpreted model; schematic coordinates are not PCB placement/identity. Healthy function is verified before fault injection. Ambiguities require explicit author choices; unsupported cases explain exact elements/contracts. Native physical/correspondence/diagnostic services qualify final play and exact replay.

**Important negative tests:** Unknown element silently deleted; ideal source drawn as an invented PCB part; grounded isolated reference; input parsed but healthy target unknown; root seed only replay with missing source bytes; duplicate/symmetric identity drift; guessed package; file alters live owner; unsafe name/script/URL or oversized input.

**Performance / scalability evidence:** Frozen small import corpus with valid, malformed, ambiguous and unsupported files; electrical equivalence and pin-map negatives; real wizard/player flow; deterministic fault/replay; parse/generation/diagnostic cost and source-size limits.

**Architectural risk:** A convenient parser shortcut publishing an unserviceable or functionless challenge.

**Expected extension and scale effects:** Pluggability: one origin adapter into native engines. Scale: cheap negotiation before expensive physical/diagnostic work.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Use existing parser semantics where practical; identify any unsupported format/model rather than maintaining an unrelated silent parser. All twelve Section 6.11 contracts apply even to this small subset. Preserve source artifacts in replay packages or disclose the required missing source dependency; a hash by itself is insufficient.

**Direct later dependents:** [IMPORT-2](#m-import-2).

<a id="m-import-2"></a>
### IMPORT-2 · Diode, transistor and RC import support

**Type / status:** Planned later supported-subset expansion; UNSTARTED.

**Purpose and reason:** Expand ordinary analog/DC community imports without weakening healthy verification, package mapping or serviceability.

**Architectural owner / affected systems:** Import mapping providers and native diode/BJT/MOSFET/capacitor, source, observation and diagnostic contracts.

**Hard prerequisites:** [IMPORT-1](#m-import-1), [A05](#m-a05).

**Must not be coupled:** No all-semiconductor support, IC/MCU dependency, new physics from parser names or unqualified temporal scope claim.

**Exact deliverable:** Versioned mappings for selected supported diode/transistor/RC constructs, initial-state interpretation, model/polarity correspondence, explicit package choices and supported fault modes. Retain the IMPORT-1 ingestion and manifest contracts.

**Acceptance:** Each admitted circuit has declared input/output/healthy conditions and real supported nonlinear/temporal behavior. Capacitor state and active-meter readiness are explicit. Imported model parameters are retained or explicitly rejected if unsupported. Package/mutation/fault correspondence is checked through the native workbench.

**Important negative tests:** Transistor pin order guessed; custom model silently replaced; polarized capacitor reversed; parser drops initial energy; healthy imported circuit has no meaningful target; no-op/unrepairable fault accepted; temporal observation assumed available.

**Performance / scalability evidence:** Small nonlinear and RC import corpus with polarity, unsupported-model and initial-state cases; before/after electrical checks, selected real repairs and stage-specific cost reports.

**Architectural risk:** Claiming broad support from a few circuit names while model details or pin mappings are lost.

**Expected extension and scale effects:** Pluggability: mapping providers extend the existing import support matrix. Scale: bounded per-category qualification rather than new imported runtime.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** A selected temporal diagnostic adds an accepted instrument such as U03 only when actually needed. RC/DC content does not automatically wait for all timing or digital libraries.

**Conditional prerequisites:** [U03](#m-u03) only when selected temporal import requires waveform/frequency observations.

**Direct later dependents:** [IMPORT-3](#m-import-3), [IMPORT-4](#m-import-4).

<a id="m-import-3"></a>
### IMPORT-3 · Supported small-IC and control-circuit imports

**Type / status:** Planned later IC/control import subset; UNSTARTED.

**Purpose and reason:** Import qualified powered analog, combinational and sequential constructs with their real supplies, state and package groupings.

**Architectural owner / affected systems:** IC import mapping providers, accepted model/state contracts and author-confirmed physical/functional manifests.

**Hard prerequisites:** [IMPORT-2](#m-import-2), [E09](#m-e09), [E10](#m-e10).

**Must not be coupled:** No arbitrary IC catalog, MCU firmware conversion, automatic package grouping guess or mandatory display/MCU for all files.

**Exact deliverable:** Supported IC/control mappings including multi-unit packages and shared supplies, clock/reset/initialization semantics and declared source/control/output behavior. Explicitly classify electrically supported but nonserviceable constructs.

**Acceptance:** Mapped packages preserve physical pin/unit ownership and powered/stateful semantics. Healthy and faulted behavior uses accepted observations and time contracts. Hidden simulator supplies must be mapped honestly or rejected; missing functional intent remains a wizard requirement. Replay pins state/model/import interpretation.

**Important negative tests:** One schematic unit becomes a separate replaceable package; implicit supply fabricated; clock/reset discarded; internal Boolean exposed as pin measurement; unsupported custom IC accepted; required physical pin inaccessible; unsaved sequential state changes replay.

**Performance / scalability evidence:** Finite IC/control corpus with missing-supply, ambiguous-unit and unsupported-element negatives; actual pin probes, diagnosis/repair/retest and exact manifest replay.

**Architectural risk:** A schematic abstraction that cannot be given an honest physical package or observable fault.

**Expected extension and scale effects:** Pluggability: physical mapping and stateful providers remain explicit. Scale: reusable import capabilities rather than per-circuit special cases.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** A file using a 555-style timer, display, specialized interface or basic MCU adds the accepted E07/E11/E12/MCU-2 capability it consumes. No arbitrary firmware interpretation is added by this importer stage.

**Conditional prerequisites:** [E07](#m-e07) only when input uses a supported 555/timer implementation. [E11](#m-e11) only when input uses the qualified display provider. [E12](#m-e12) only when input uses an accepted specialized IC/interface group. [MCU-2](#m-mcu-2) only when input uses the specifically supported basic MCU model; not arbitrary firmware.

**Direct later dependents:** [IMPORT-5](#m-import-5).

**Conditional later consumers:** [IMPORT-4](#m-import-4) when input consumes IC/control mappings from that accepted stage.

<a id="m-import-4"></a>
### IMPORT-4 · Multi-rail, relay and dynamic-circuit imports

**Type / status:** Planned later independent power/dynamic import subset; UNSTARTED.

**Purpose and reason:** Qualify imported circuits whose behavior depends on multiple sources/references, switching, timing or external loads.

**Architectural owner / affected systems:** Import source/domain/operating manifests and existing power, relay, temporal, physical and diagnostic services.

**Hard prerequisites:** [IMPORT-2](#m-import-2), [E02](#m-e02), [E03](#m-e03), [E07](#m-e07), [U03](#m-u03), [P09](#m-p09).

**Must not be coupled:** No requirement to finish IMPORT-3 for an otherwise supported non-IC circuit; no arbitrary mains/converter or high-frequency fidelity promise.

**Exact deliverable:** Versioned mappings for qualified multi-rail, relay and dynamic circuits, source/reference and initial-state annotations, finite operational profiles and instrument requirements. Reuse the same supported physical envelope and bounded generation jobs.

**Acceptance:** Partial power, source contention, backfeed, stored energy and clock/switching observations match the supported model. Declared customer operating sequences establish healthy behavior and faulty symptoms. Required source/load/control actions are player-operable; layer/copper mapping and physical repair remain native.

**Important negative tests:** Every ground label merged; bench load counted as PCB part; ideal regulator substituted silently; hidden switching waveform; scope reference shorts ignored; partial-power false-off; circuit converges only under an undeclared verifier stimulus.

**Performance / scalability evidence:** Cross-domain and temporal import corpus with reference/power/stimulus negatives, solver/copper correspondence, real operating sequence and current replay/save integration when advertised.

**Architectural risk:** Importing structurally valid files whose references, dynamics or operating contract are not supported.

**Expected extension and scale effects:** Pluggability: power/dynamic adapters reuse accepted provider contracts. Scale: more useful inputs without a new solver or router.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** Mains/isolation or offline-conversion files require E05/E06 respectively; AC semiconductor or motor-control files require E13/E14 when used. IC-containing files require the relevant IMPORT-3 mappings. These are file-specific capability requirements, not all unconditional prerequisites of this stage.

**Conditional prerequisites:** [IMPORT-3](#m-import-3) only when input consumes IC/control mappings from that accepted stage. [E05](#m-e05) only when input includes accepted AC/mains/isolation behavior. [E06](#m-e06) only when input includes the accepted offline-converter model. [E13](#m-e13) only when input uses accepted semiconductor AC switching. [E14](#m-e14) only when input uses the accepted motor-control family.

**Direct later dependents:** [IMPORT-5](#m-import-5).

<a id="m-import-5"></a>
### IMPORT-5 · Broad supported CircuitJS subset and community qualification

**Type / status:** Planned later import product qualification; not universal import; UNSTARTED.

**Purpose and reason:** Turn the bounded import slices into a maintainable broadly useful product feature with a transparent versioned support envelope.

**Architectural owner / affected systems:** Import support registry/matrix, independent corpus qualification, author workflow, source/replay packaging and normal player services.

**Hard prerequisites:** [IMPORT-3](#m-import-3), [IMPORT-4](#m-import-4), [A11](#m-a11), [U06](#m-u06).

**Must not be coupled:** No guarantee for every imaginable CircuitJS element/format, every future IC/instrument or arbitrary firmware; no Q60/Q100 import prerequisite.

**Exact deliverable:** A published element/import-feature matrix, categorized community-style holdout corpus, deterministic capability reports, usable author annotation flow and portable source-plus-manifest replay. Selected physical scale bands are declared and qualified, not inferred from file parsing.

**Acceptance:** Every included element/model combination either enters the shared native pipeline truthfully or receives an exact unsupported/needs-input reason. No silent drops or electrical rewrites. Healthy intent precedes seeded faults; diagnosis/serviceability/repair and physical correspondence pass on the accepted subset. Old supported source formats/versions replay or reject with a clear policy.

**Important negative tests:** Success rate calculated after deleting unsupported inputs; failed files hidden; source bytes absent from supposedly portable artifact; edited file reuses stale proof; author source leaks through normal gameplay text; importer support matrix contradicts actual registration; all high-cost cases filtered without published bounds.

**Performance / scalability evidence:** Frozen valid/invalid/ambiguous/unsupported holdout with per-stage outcomes and modest-host cost; native/imported same-circuit parity; source hash/manifest and corrupted-state tests; actual author-to-player flow. Real community files require permission/provenance handling at implementation, not assumptions here.

**Architectural risk:** Marketing a curated import demo as universal conversion or allowing parser/version drift to invalidate physical replay.

**Expected extension and scale effects:** Pluggability: mappings follow registered capabilities. Scale: finite supported subsets and honest rejection avoid unbounded arbitrary-netlist obligations.

**Replay / versioning:** Pin each consumed model, provider, package, program/import or instrument policy version as relevant; preserve already supported Task48/49 and native/imported replay interpretations.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits. This is later separately authorized work after N00; consume the accepted recipe and foundation contracts.

**Capability boundary:** IMPORT-3 and IMPORT-4 converge here but retain their earlier independent progress. A chosen imported Q60/Q100 family also passes that scale gate; native Q60/Q100 does not wait for IMPORT-5. The author may know the original schematic; normal-player privacy still prevents accidental answer disclosure, not determined local inspection.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.


## Diagnostic scale

<a id="m-d01"></a>
### D01 · Scalable diagnostic partitions and context-valid proof reuse

**Type / status:** Required foundation; UNSTARTED.

**Purpose and reason:** Prevent proof cost from overtaking simulation while keeping the entire admitted hypothesis contract honest.

**Architectural owner / affected systems:** Production diagnostic planner, observation partitions, proof receipt cache and serial reference verifier.

**Hard prerequisites:** [A09](#m-a09), [A10](#m-a10), [Q15](#m-q15).

**Must not be coupled:** No shared mutable solver-state cache, brute-force every conceivable fault or random candidate sampling presented as exhaustive admission.

**Exact deliverable:** Structural/serviceability pruning; provider-local evidence with global context checks; bounded adaptive observation plans; valid repair equivalence; complete-key receipt reuse.

**Acceptance:** Every retained hypothesis is covered by a legal separating plan or genuine equivalent repair class; sampled testing never certifies unsampled hypotheses; context changes invalidate local receipts; candidate order is deterministic.

**Important negative tests:** Locally healthy block loaded differently in device; cache omits source state/model version; identical observations but incompatible repair; arbitrary top-N candidate truncation; shortcut bypasses final global proof.

**Performance / scalability evidence:** Hypotheses/observations/solves/repairs and cold/warm time versus simple serial proof on RB15 and larger evolving fixtures.

**Architectural risk:** Fast-looking admission achieved by weakening the claim or a planner becoming another centralized family switch.

**Expected extension and scale effects:** Pluggability: diagnostic contracts stay provider-owned. Scale: fewer repeated full installs without unsafe same-owner reuse.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [X01](#m-x01), [X05](#m-x05), [X06](#m-x06).


## Scale gates

<a id="m-q15"></a>
### Q15 · Heterogeneous 15-part procedural control-board qualification

**Type / status:** Required scale gate; UNSTARTED.

**Purpose and reason:** Prove the construction/ownership architecture with a useful board larger than the old bounded indicator.

**Architectural owner / affected systems:** RB15 device intent and independent integrated qualification across generation, physical, solver, diagnostic and player layers.

**Hard prerequisites:** [A05](#m-a05), [A08](#m-a08), [A10](#m-a10), [A11](#m-a11), [P03](#m-p03), [P04](#m-p04), [U01](#m-u01), [E01](#m-e01), [E03](#m-e03).

**Must not be coupled:** No mains, two-layer normal-play requirement, scoring or advanced profile needed for this gate.

**Exact deliverable:** A real approximately 15-part low-voltage multi-block board with purposeful support, relay/control/load behavior and at least two structurally different qualified implementations.

**Acceptance:** Manifest-to-playable stages pass; the same functional intent yields different internal designs; every counted package is causal; all admitted faults have legal repair/retest; held-out seeds remain within the declared 5–20-part envelope.

**Important negative tests:** Authored full-board layout disguised as general procedural proof; copied tile; unsupported hypothesis; inaccessible terminal; fixed answer despite changed internal topology.

**Performance / scalability evidence:** Generation/route/proof distributions and normal-player workflows on the named machine. Record authored references separately.

**Architectural risk:** Calling a single fixture an entire supported range or satisfying diversity with resistor values alone.

**Expected extension and scale effects:** Pluggability: first integrated extension proof. Scale: routine-small envelope, not yet 40/60/100 support.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [U05](#m-u05), [U08](#m-u08), [D01](#m-d01), [Q30](#m-q30), [REL-A](#m-rel-a).

<a id="m-q30"></a>
### Q30 · Normal 20–40-part multi-rail procedural qualification

**Type / status:** Required normal-medium gate; UNSTARTED.

**Purpose and reason:** Prove the middle scale where multiple regions, domains and alternate implementations become normal.

**Architectural owner / affected systems:** RB30 device-intent family and integrated supported-envelope qualification.

**Hard prerequisites:** [Q15](#m-q15), [E02](#m-e02), [E03](#m-e03), [E04](#m-e04), [D01](#m-d01), [P09](#m-p09), [U02](#m-u02).

**Must not be coupled:** No need for every future instrument or advanced intermittent/damage feature; temporal variants require E07/U03 when used.

**Exact deliverable:** Approximately 30-part heterogeneous multi-rail control board plus a held-out 20–40-part corpus, repeated output channels and structurally different implementations of shared roles.

**Acceptance:** Generation, routing, domain behavior, partial power, normal diagnostics and repair meet the frozen budget; no generic layer gains device-specific branches; selected layer strategy is inspectable and comprehensible.

**Important negative tests:** Only easy flat netlists; per-family geometry exceptions; backfeed ignored; cross-domain meter error; deferred unproved hypothesis admitted to keep success rate high.

**Performance / scalability evidence:** Cold/warm p50/p95, failure distribution, matrix/proof counts, navigation/probe trials and extension-cost ledger.

**Architectural risk:** Provider-local proofs assumed sufficient for interacting multi-rail loads.

**Expected extension and scale effects:** Pluggability: ordinary mixed content uses the same services. Scale: normal medium support becomes evidence, not expectation.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [U07](#m-u07), [Q60](#m-q60).


## Release gates

<a id="m-rel-a"></a>
### REL-A · Limited desktop alpha: architecture learning release

**Type / status:** Required intermediate release gate; UNSTARTED.

**Purpose and reason:** Obtain real player feedback without pretending the final large-board target has already been achieved.

**Architectural owner / affected systems:** Release qualification over the accepted small-board profile and session surface.

**Hard prerequisites:** [Q15](#m-q15), [U04](#m-u04), [U05](#m-u05), [A11](#m-a11).

**Must not be coupled:** No scoring, economy, mobile, all instruments, Q100 or PSYCHOTIC required.

**Exact deliverable:** Explicit low-voltage 5–20-part alpha support statement, EASY/MEDIUM availability, usable Resources/Settings, replay and honest catalog/navigation.

**Acceptance:** Advertised routes pass diagnostic/physical/player/privacy/focus gates; known limitations and bug-report identity are documented; unsupported mains/layers/profiles are not advertised.

**Important negative tests:** Developer entry mistaken for normal play; unknown replay silently substituted; modal input reaches hidden board; mock feature presented as gameplay.

**Performance / scalability evidence:** Curated normal-player acceptance corpus, selected browser coverage, source/build identity and failure receipts.

**Architectural risk:** Alpha becoming an excuse to abandon Q60/Q100 or being blocked by unrelated future content.

**Expected extension and scale effects:** Pluggability: checks integrated boundaries. Scale: provides human evidence, not a large-board certificate.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [REL-B](#m-rel-b).


## Scale gates

<a id="m-q60"></a>
### Q60 · Advanced 40–60-part appliance/control-board qualification

**Type / status:** Required primary product target; UNSTARTED.

**Purpose and reason:** Deliver the new primary product target, including meaningful mains-side power conversion and low-voltage control/output behavior.

**Architectural owner / affected systems:** RB56 intent family and whole-device electrical/physical/diagnostic qualification.

**Hard prerequisites:** [Q30](#m-q30), [E05](#m-e05), [E06](#m-e06), [U03](#m-u03), [U07](#m-u07), [P09](#m-p09), [D01](#m-d01).

**Must not be coupled:** No arbitrary 60-part netlist promise; no blanket dependency on E08, MCU, display, import, every logic IC, two-channel scope, logic analyzer or signal injection. Every operation/model actually advertised by the chosen family must still be qualified.

**Exact deliverable:** The approximately 56-part reference board and varied 40–60-part derivatives with mains entry, causal isolated conversion, multiple rails, sensors, relays and support; alternative real internal implementations.

**Acceptance:** Each declared reference-board stage passes; package count is honest; mains/isolation/storage/loading behavior meets model contracts; all required measurements and repairs are accessible; admission and interactive budgets pass; HARD calibration can use measured evidence.

**Important negative tests:** Twelve decorative converter parts around an ideal source; averaged model exposes invented switching observations; only selected easy faults proved; hidden underside repair; version mismatch hidden by regeneration.

**Performance / scalability evidence:** Frozen mixed-topology holdout corpus, actual cold generation and normal diagnosis/repair/retest, model/energy sensitivity, long-session and modest-machine performance.

**Architectural risk:** Component count hiding inadequate model fidelity or combinatorial proof cost.

**Expected extension and scale effects:** Pluggability: serious heterogeneous content without core surgery. Scale: real advanced support, not merely structural routing.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Conditional prerequisites:** [E08](#m-e08) only when the selected Q60 family advertises or consumes trace cutting, player-installed jumpers, physical trace restoration or bypass repair. Component-only diagnosis and replacement remain a valid complete Q60 route. The family capability manifest must state which path is qualified; a release advertising E08 cannot inherit a component-only receipt as proof of trace repair.

**Amended qualification boundary:** MCU, IC/display variants and imported origins are independent capability bundles, not mandatory baseline implementations. Requalify Q60 for each added family at its actual advertised scale and tools. U06/U07 test only installed state providers: their presence in this hard prerequisite chain does not indirectly make E08 or future MCU/import state mandatory.

**Direct later dependents:** [REL-B](#m-rel-b), [Q100](#m-q100), [X05](#m-x05), [X06](#m-x06).


## Release gates

<a id="m-rel-b"></a>
### REL-B · Advanced desktop beta and HARD-profile release

**Type / status:** Required advanced release gate; UNSTARTED.

**Purpose and reason:** Release the advanced appliance/control surface only after its full operating and session behavior is credible.

**Architectural owner / affected systems:** Release, profile calibration, source/version manifests and published support envelope.

**Hard prerequisites:** [Q60](#m-q60), [REL-A](#m-rel-a), [U05](#m-u05), [U06](#m-u06), [U07](#m-u07).

**Must not be coupled:** PSYCHOTIC, Q100, economy, mobile and optional advanced faults are not beta prerequisites unless advertised.

**Exact deliverable:** Beta support for EASY/MEDIUM/HARD and the qualified 40–60-part family, durable sessions/replay as advertised, source/reference instrumentation and honest layer policy.

**Acceptance:** HARD has a fresh Q60-based calibration receipt; every advertised repair/instrument/source/catalog/save state is validated; unsupported fidelity is documented; non-spoiling bug reports reproduce failures.

**Important negative tests:** Outdated U05 receipt enabling HARD; saved copper actions unsupported by current state provider; failed cold generation hidden behind a warm cached demo; old source graph reused after reset.

**Performance / scalability evidence:** Release matrix of real workflows, browser/performance limits, recovery failures and known defects.

**Architectural risk:** Treating feature checklist completion as reliability or letting release labels weaken evidence.

**Expected extension and scale effects:** Pluggability: full provider integration remains consistent. Scale: advanced use publicly supportable.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [X01](#m-x01), [X02](#m-x02), [X04](#m-x04), [X05](#m-x05), [X09](#m-x09), [REL-1](#m-rel-1).


## Scale gates

<a id="m-q100"></a>
### Q100 · Constrained 100-part procedural stretch qualification

**Type / status:** Required architectural stretch target, not an optional backlog; UNSTARTED.

**Purpose and reason:** Prove the explicit stretch target without arbitrary-netlist promises or tiling tricks.

**Architectural owner / affected systems:** RB100 intent family, supported-envelope policy and independent scale qualification.

**Hard prerequisites:** [Q60](#m-q60), [U07](#m-u07), [P09](#m-p09), [D01](#m-d01), [A11](#m-a11).

**Must not be coupled:** No need to route every arbitrary 100-part netlist. Early 100-part pilots remain mandatory even though final Q100 follows Q60.

**Exact deliverable:** Approximately 100 purposeful physical packages in a heterogeneous structured appliance/control design, with bounded variant choice, domains, layer policy and a complete playable diagnostic contract.

**Acceptance:** At least the declared constrained RB100 family passes generation through normal play, repair, save/replay and long-session checks; every package and required fault is causal; no undocumented whitelist or deterministic seed rescue substitutes for the supported corpus.

**Important negative tests:** Copied independent tiles; external loads counted as board parts; removed hard hypotheses; invisible copper; unlimited jumpers; memory/latency outside frozen budget; structural PASS relabeled playable PASS.

**Performance / scalability evidence:** Holdout success/failure rate, cold/warm stage budgets, worst accepted cases, matrix/proof/input/frame/memory metrics and expert navigation trials.

**Architectural risk:** Compounding weaknesses that were harmless at 30 parts; restricted fixtures advertised as universal routing.

**Expected extension and scale effects:** Pluggability: new complex device remains composition data/providers. Scale: legitimate declared 75–100 stretch envelope.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Consumed-capability boundary:** Q100 remains required. Its default hard chain inherits the corrected component-repair-capable Q60 path, not unconditional E08 or all later vocabulary. A mixed analog/logic/MCU/display/AC-control RB100 variant is planned as an optional extension of the reference family; qualifying that variant requires its actual providers and instruments, not adding them to every RB100 implementation. The exact supported realization and capability bundle must be stated in each receipt.

**Direct later dependents:** [REL-1](#m-rel-1).


## Advanced capability

<a id="m-x01"></a>
### X01 · Deterministic intermittent and state-dependent faults

**Type / status:** Required long-range advanced capability; profile use separately admitted; UNSTARTED.

**Purpose and reason:** Introduce real intermittent troubleshooting only after observation and diagnostic planning can support it.

**Architectural owner / affected systems:** Fault event scheduler, solver-time state transitions and temporal diagnostic providers.

**Hard prerequisites:** [A09](#m-a09), [D01](#m-d01), [U03](#m-u03), [REL-B](#m-rel-b).

**Must not be coupled:** No universal contact physics, automatic PSYCHOTIC label or requirement that every advanced board be intermittent.

**Exact deliverable:** A small bounded vocabulary such as intermittent open, startup dropout and state-dependent contact behavior with reproducible events and causal graph changes.

**Acceptance:** Players can reproduce/capture required conditions through available controls; event time is solver-owned; cancellation/reset/save semantics are explicit; no fake flashing meter.

**Important negative tests:** Wall-clock or unseeded failure; event affects new owner; omitted state after resume; diagnostic plan needs a hidden trigger.

**Performance / scalability evidence:** Event/timestep/observation tests and normal capture/retest trials, with timing-budget qualification.

**Architectural risk:** Random frustration mistaken for difficulty.

**Expected extension and scale effects:** Pluggability: events are fault providers. Scale: bounded temporal hypotheses.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-x02"></a>
### X02 · Expanded causal stress and secondary damage

**Type / status:** Required long-range consequence capability; UNSTARTED.

**Purpose and reason:** Model consequences beyond the original resistor case without confounding initial faults and player-caused failures.

**Architectural owner / affected systems:** Part stress/damage providers, solver observations, lifetime/history and source limits.

**Hard prerequisites:** [E01](#m-e01), [A08](#m-a08), [U06](#m-u06), [REL-B](#m-rel-b).

**Must not be coupled:** Does not depend on X01 intermittency or X03 thermal unless the selected model actually consumes them.

**Exact deliverable:** Selected diode/capacitor/transistor/MOSFET/relay/fuse stress states and failure transitions, with supported time and energy approximations.

**Acceptance:** Safe operation survives; excess solved stress accumulates predictably; failures change actual electrical behavior; original fault versus new damage and physical part identity persist through repair/resume.

**Important negative tests:** Random damage without stress cause; visible damage hint reveals unobserved answer; replacing a part retains wrong predecessor state; saved state heals a damaged component.

**Performance / scalability evidence:** Boundary/time/source-limit fixtures, stage-failure tests, state serialization and causal-history checks.

**Architectural risk:** False precision or state growth that invalidates earlier proofs without requalification.

**Expected extension and scale effects:** Pluggability: damage belongs to component providers. Scale: composable consequences.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [X03](#m-x03), [X04](#m-x04).

<a id="m-x03"></a>
### X03 · Thermal approximation where diagnosis justifies it

**Type / status:** Conditional: documented diagnostic or failure-timing need; UNSTARTED.

**Purpose and reason:** Add heating/cooling only when an admitted scenario needs thermal timing or an observable physical clue.

**Architectural owner / affected systems:** Derived thermal state provider from solved dissipation and bounded time history.

**Hard prerequisites:** [X02](#m-x02).

**Must not be coupled:** No finite-element thermal solver, photoreal camera or prerequisite for ordinary saves/returns.

**Exact deliverable:** One documented heating/cooling model and its supported observations, parameters and interaction with failure thresholds.

**Acceptance:** Temperature follows power/time with explicit limits; cooling and resume are coherent; an exposed clue is physically justified and does not simply identify the selected fault.

**Important negative tests:** Hot marker from fault ID; arbitrary instant cooling; double-counted damage energy; unobserved internal temperature leaked as an answer.

**Performance / scalability evidence:** Known power/time traces, time-step sensitivity and diagnostic utility compared with existing observations.

**Architectural risk:** A decorative thermal camera or unsupported quantitative realism.

**Expected extension and scale effects:** Pluggability: optional derived-state provider. Scale: bounded extra state only where used.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-x04"></a>
### X04 · Persistent customer returns and service intervals

**Type / status:** Planned advanced capability; UNSTARTED.

**Purpose and reason:** Extend immediate retest into plausible later consequences while preserving the actual serviced board.

**Architectural owner / affected systems:** Job history, service-interval simulator and complaint projection over current part/damage state.

**Hard prerequisites:** [X02](#m-x02), [U06](#m-u06), [REL-B](#m-rel-b).

**Must not be coupled:** Thermal and intermittency are dependencies only for return models that use them; no economy required.

**Exact deliverable:** A bounded return scenario from a causally inadequate repair, with preserved inventory, conductor modifications and service history.

**Acceptance:** A repair may pass an immediate test yet later fail through the implemented stress/event model; the new complaint is derived from resulting behavior, not a scripted mandatory return.

**Important negative tests:** Every job returns regardless of repair; original board replaced with a new hidden challenge; historical identity lost; unimplemented thermal/intermittent cause invoked.

**Performance / scalability evidence:** Saved interval/resume cases, same-seed causal replay and normal second-visit diagnosis.

**Architectural risk:** Storytelling overriding electrical state.

**Expected extension and scale effects:** Pluggability: complaints consume outcome evidence. Scale: meaningful longitudinal sessions.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-x05"></a>
### X05 · Expert-calibrated PSYCHOTIC admission

**Type / status:** Required mature-product profile gate; UNSTARTED.

**Purpose and reason:** Produce difficulty that challenges experienced technicians through reasoning rather than patience or pixel hunting.

**Architectural owner / affected systems:** Advanced profile calibration, diagnostic evidence and blinded technician evaluation.

**Hard prerequisites:** [Q60](#m-q60), [U05](#m-u05), [D01](#m-d01), [REL-B](#m-rel-b).

**Must not be coupled:** Q100 and multiple faults are not necessary for every PSYCHOTIC challenge. A profile using X01/X02/X03 adds those as explicit dependencies.

**Exact deliverable:** Versioned expert profile using several plausible owners, interacting domains/loading/control, alternate topologies, partial power and multi-stage legal diagnosis; temporal/damage variants added only with their providers.

**Acceptance:** Experienced testers encounter meaningful hypothesis reduction and multiple valid routes on held-out designs; required information remains observable; all accepted faults remain repairable; no hidden essential control or fake symptom.

**Important negative tests:** Raw 100-part count substitutes for challenge; one probe always reveals owner; unverifiable intermittent timing; inaccessible pin; bogus circuit behavior used to mislead.

**Performance / scalability evidence:** Blinded trials with skilled electronics troubleshooters, action traces, failure analysis and repeat-exposure/variant transfer. Publish uncertainty rather than claiming professional difficulty from a formula.

**Architectural risk:** Overfitting to one tester or conflating unfamiliar UI with electrical complexity.

**Expected extension and scale effects:** Pluggability: evidence-driven profiles reuse providers. Scale: advanced reasoning independent of board size.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Stateful/digital expert-content opportunities:** Later E09/E10/MCU/display/AC-control variants may deepen PSYCHOTIC through a latch held in reset by a rising rail, a missing clock, a missing external pull-up, a shifted comparator reference, a PWM driver collapsing under load, a failed zero-cross path, a timing-network drift or a dead multiplexed digit/scan path. Several subsystems may produce the same customer complaint. These are optional evidence-backed implementations, not a requirement that every expert board contain an MCU or 100 parts. Normal public pin/function information and bench inspection remain available; no hidden-firmware decompilation, inaccessible pins or answer-revealing UI is allowed.

**Direct later dependents:** [REL-1](#m-rel-1).

<a id="m-x06"></a>
### X06 · Bounded multiple-fault decision and optional proof

**Type / status:** Conditional; explicit skip is valid; UNSTARTED.

**Purpose and reason:** Permit controlled combinations only when they add genuine diagnostic value beyond one fault.

**Architectural owner / affected systems:** Joint hypothesis provider, interaction/repair equivalence and device-level admission.

**Hard prerequisites:** [D01](#m-d01), [A08](#m-a08), [Q60](#m-q60).

**Must not be coupled:** Not a prerequisite for PSYCHOTIC, scoring, beta or basic history.

**Exact deliverable:** A decision receipt; if justified, one small explicitly compatible two-fault family with staged repair and retest.

**Acceptance:** Joint effects are proved, not inferred from individual-fault passes; masking and emergent ambiguity reject; each physical owner is retained; the joint hypothesis set and work budget stay bounded.

**Important negative tests:** Multiply every fault with every other fault; one repair masks second failure; independently valid faults assumed jointly fair; count used as difficulty label.

**Performance / scalability evidence:** Exhaustive selected small joint corpus and comparison with single-fault challenge utility.

**Architectural risk:** Combinatorial proof growth and confusing root cause with secondary damage.

**Expected extension and scale effects:** Pluggability: bounded composable hypotheses. Scale: deliberately controlled joint work.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-x07"></a>
### X07 · Optional diagnostic-efficiency scoring

**Type / status:** Optional product capability; UNSTARTED.

**Purpose and reason:** Provide useful feedback without changing electrical behavior or forcing one correct sequence.

**Architectural owner / affected systems:** Observed action/history scoring service and result presentation.

**Hard prerequisites:** [U06](#m-u06), [U05](#m-u05).

**Must not be coupled:** No prerequisite for save/resume, multi-fault, returns, alpha or beta.

**Exact deliverable:** Transparent optional scoring from supported observations/actions, unnecessary replacements and consequences, with privacy-safe explanations.

**Acceptance:** Identical supported play yields the same score; valid alternative diagnostic routes are not arbitrarily penalized; disabling scoring changes no challenge state.

**Important negative tests:** Hidden target identity gives bonuses; wall-clock computer performance penalizes a player; score changes fault physics; unreached answer leaked in results.

**Performance / scalability evidence:** Action-trace replay and diverse successful repair routes, plus explanation review.

**Architectural risk:** Gamification rewarding guessing or discouraging legitimate troubleshooting.

**Expected extension and scale effects:** Pluggability: pure observer over history. Scale: bounded aggregation.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.

<a id="m-x08"></a>
### X08 · Conditional active capacitance measurement

**Type / status:** Conditional on an admitted diagnostic need; UNSTARTED.

**Purpose and reason:** Add capacitance mode only when a real diagnostic gap justifies a defensible active method.

**Architectural owner / affected systems:** Active instrument provider, energy readiness and temporary CircuitJS stimulus.

**Hard prerequisites:** [E05](#m-e05), [U02](#m-u02), [A08](#m-a08).

**Must not be coupled:** No prerequisite on an oscillator merely because it had a lower old task number; not required for all advanced profiles.

**Exact deliverable:** One bounded measurement method with declared range, uncertainty, in/out-of-circuit limits and cleanup.

**Acceptance:** Result derives from the solver response, not configured capacitance; residual charge and parallel paths are handled explicitly; the mode improves a supported diagnostic plan.

**Important negative tests:** Direct CapacitorElm value read; charged-device stimulus; missing cleanup; parallel circuit yields a falsely precise part value.

**Performance / scalability evidence:** Standard known circuits, range/failure cases and useful player diagnostic proof.

**Architectural risk:** A convenience feature claiming unsupported accuracy.

**Expected extension and scale effects:** Pluggability: another instrument provider. Scale: bounded additional proof cost.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.


## Productization

<a id="m-x09"></a>
### X09 · Mature packaging, accessibility and support policy

**Type / status:** Required mature-product support gate; UNSTARTED.

**Purpose and reason:** Make the qualified product usable and supportable beyond the development environment.

**Architectural owner / affected systems:** Release packaging, public documentation, accessibility audit and supported-browser/offline/deployment policy.

**Hard prerequisites:** [REL-B](#m-rel-b), [U01](#m-u01), [U04](#m-u04), [U06](#m-u06).

**Must not be coupled:** Touch/mobile, cloud accounts, economy and hosted deployment remain separately scoped decisions, not assumed prerequisites.

**Exact deliverable:** Reproducible distribution, attribution/license review, non-spoiling bug reports, full promised desktop accessibility and explicit support/retirement/version policy.

**Acceptance:** Install/launch/replay/save recovery works on supported environments; advertised controls remain accessible without hidden answers; legal/dependency notices and distribution permissions are reviewed against actual dependencies.

**Important negative tests:** Hosted-only accidental data loss; stale service worker loads incompatible code; support claim for untested browser; screen-reader reveals hidden value; missing attribution.

**Performance / scalability evidence:** Release-artifact provenance, accessibility trials and clean-machine smoke; deployment security review if hosting is added.

**Architectural risk:** Packaging/version drift or a public promise broader than tested behavior.

**Expected extension and scale effects:** Pluggability: contributor documentation reflects real seams. Scale: supportable larger product.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** [REL-1](#m-rel-1).


## Release gates

<a id="m-rel-1"></a>
### REL-1 · Mature product readiness with 60-part core and 100-part stretch

**Type / status:** Required final product gate; UNSTARTED.

**Purpose and reason:** Close the promised product, not merely the numbered queue.

**Architectural owner / affected systems:** Owner release decision over advertised capability manifests, independent reviews and qualification receipts.

**Hard prerequisites:** [Q100](#m-q100), [X05](#m-x05), [X09](#m-x09), [REL-B](#m-rel-b).

**Must not be coupled:** No completion requirement for optional X03/X06/X07/X08, mobile, economy or universal PCB design unless the product explicitly advertises them.

**Exact deliverable:** Mature supported surface including advanced Q60 families, constrained Q100 capability, all four calibrated profiles, truthful model/layer limits and maintained replay/session behavior.

**Acceptance:** All advertised contracts pass their exact final corpus; no unresolved severe correctness/fidelity/ownership blocker remains; expert difficulty and modest-machine budgets are evidenced; compatibility policy is actionable.

**Important negative tests:** Stretch feature disabled but still advertised; benchmark-only 100-part proof; unreviewed final source delta; missing old replay resolution; cosmetic profile label.

**Performance / scalability evidence:** Final representative release matrix, independent review, reproducible build/distribution and documented remaining optional work.

**Architectural risk:** Treating the roadmap as proof that success was inevitable.

**Expected extension and scale effects:** Pluggability: mature extension-cost target demonstrated. Scale: explicit core and constrained stretch commitments realized.

**Replay / versioning:** New contracts are versioned; accepted Task48/49 descriptors retain their reviewed meaning.

**Task49 impact:** Preserve the accepted bounded Task49 contract, versions, catalog bounds and retained limits; consume its immutable recipe after N00.

**Direct later dependents:** None unconditionally; later use is governed by the consuming capability manifest.



---

<a id="reconciliation"></a>
# 8. Audit reconciliation, risk register and remaining brick walls

## 8.1 Disposition of all fourteen original findings

These findings retain the confidence limits of the original audit. Rechecking an unchanged source mechanism is not a fresh production reproduction. “Close” means the owning milestone supplies the named evidence; nothing in this table declares a fix already implemented.

| Finding | Baseline evidence and confidence | New disposition / owner | Minimum closure evidence and new risk |
|---|---|---|---|
| **F1: device construction concentrated in the bounded assembler** | HIGH, high-confidence code finding. Fresh source still allocates the controlled circuit and knows its parts/constants. [R02; I2 S7–S9] | Task49 supplies resolved values; **A04/A05/A11** replace ordinary device-specific construction and prove variant-local extension. Required before more content relies on this pattern. | Migrate two real implementations and add an ordinary variant without a new generic branch. Avoid a universal DSL or provider-owned nested simulation. |
| **F2: production admission calls family-specific developer orchestration** | HIGH, proven call linkage, not measured large-board cost. `validateLive` still calls `Task41DeveloperVerifier`. [R09] | **A09/D01** own production hypothesis/plan execution and scale proof. Keep developer tests as independent consumers. | Provider-owned plans, parity with the serial reference, failure isolation and explicit population accounting. Merely renaming the verifier is not closure. |
| **F3: candidate eligibility and proof enumeration disagree — resolved in A02** | HIGH before extension, code-supported latent mismatch; current two-owner pool does not demonstrate failure. Controlled proof filters compatibility/type and deduplicates by target, not the full admitted predicate. [R10; I2 S20/E2] | **A02**, before new fault populations; A09 retains complete hypothesis identity. | Compatible-but-unserviceable negative, multiple faults on one owner, count/selection/proof consistency. Do not reduce the count to hide mismatches. |
| **F4: fixed generic physical capacity** | HIGH, proven inequality for one package mixture, not a universal eighteen-part limit. Current outline remains 720 × 400. [R03; I2 E2] | **A01/P03/P09**. Dynamic bounded sizing and hierarchical placement are now required foundations because the product target changed. | Preserve baseline failures, measure mixed packages and route demand, qualify actual larger envelopes. Larger outlines must not merely transfer the problem into unreadable rendering. |
| **F5: bend-count semantics — resolved in A02** | MEDIUM, prior extracted helper execution; whole-player failure not demonstrated. Unequal collinear segment lengths count as bends. [I2 S13/E1] | **A02**, before new physical corpus scores are trusted. | Direction-change and subdivision-invariance tests, appropriate reversal/self-intersection handling, affected version/replay qualification. |
| **F6: connected-placement extra center offset — resolved in A02** | MEDIUM, prior arithmetic/translation reproduction; actual rejection impact unmeasured. [I2 S11/E1] | **A02/P01**, before hierarchical placement reuses the calculation. | Repository-native translated-world test and a coherent local/global coordinate contract. Preserve before/after evidence rather than changing several heuristics at once. |
| **F7: raw grid vertices and repeated segment-pair work** | MEDIUM, high-confidence mechanism; timings and practical limit unmeasured. [I2 S11/S13] | **P04/P08**, with A01 measurements. | Canonical contact-preserving paths, accurate unique-reuse metrics, independent broad-phase equivalence, measured cost. Simplification must not erase escape witnesses or repair loci. |
| **F8: fit-only view and minimum visible sizes** | MEDIUM, code-derived interaction risk for larger boards; no measured hundred-part UI failure. [I2 S15] | **U01**, now a required foundation, not deferred polish. | One transform and coherent visible/hit/marker geometry, zoom/side access, ordinary operator trials. Do not solve it with oversized invisible targets. |
| **F9: lexicographic merged-net representative** | MEDIUM, proven canonicalization mechanism. Unchanged local IDs remain stable, while a representative can change with a new alias. [I2 S9] | **A03/P02**, before general optional/repeated composition, cuts and persistence. | Stable device-bus identity or validated retained aliases; earlier lexical insertion test; no saved identity bound solely to a DSU root. |
| **F10: numerical floating-reference ties** | MEDIUM in the old bounded product; a high-priority future correctness boundary for isolated/high-impedance domains. Current solver stamps 100 MΩ reference ties. [R06] | **A06/A07/E05/U02**, before isolated mains content. | Differential/reference sensitivity, insertion/order invariance where appropriate, instrument loading and singularity behavior. Do not blindly delete stabilization or present it as physical ground. |
| **F11: bench/runtime growth beyond initial board** | MEDIUM, moderate-confidence scale hypothesis; no leak proved. Inventory and retained parts are legitimate state. [I2 S16/S17] | **U06/U07/Q60/Q100**. | Repeated-session growth, active/inactive elements, legitimate loose-part access, owner disposal and bounded buffers. Never “fix” retention by silently deleting player parts. |
| **F12: long semantic IDs and generic return labels** | LOW–MEDIUM, high-confidence future presentation debt. [I2 S11/S15] | **A03/U01/U04/A06**. | Short physical designators and meaningful domain/reference labels are separate projections of stable IDs; no hidden answer leakage. |
| **F13: false dependency chains** | MEDIUM, roadmap inference rather than production defect. [I2 S3] | **This edition's DAG and migration table**, maintained by N00. | Actual consumers determine prerequisites; release labels and lower task numbers do not invent them. Parallel writers must still respect shared ownership. |
| **F14: unused visualKind / thin wrappers / old menus** | LOW, harmless boilerplate or legacy ugliness in the reviewed scope. [I2 S12/S21] | **Leave alone**, or remove incidentally when an authorized change naturally owns it. | A local test/diff is sufficient if cleaned. No standalone refactor campaign and no prerequisite on the 100-part target. |

The old timing recommendations in I2 are superseded where they conflict with the explicit larger target. The findings themselves are not inflated into “the whole application is broken.” In particular, F4 is a limitation of the current generic placer, not proof that every authored board fails, and F11 is not a discovered memory leak.

## 8.2 Additional findings or implications exposed by this reconciliation

| ID | Evidence / confidence | Meaning for the new architecture | Owning gate |
|---|---|---|---|
| **N1: trace geometry exposes mutable array aliases** | CODE, proven representation: constructor retains input arrays and getters return them. No current user-visible exploit or corruption was executed. [R04] | Cached fingerprints, immutable plans and incremental validation cannot trust such an object as a frozen value. Copy/freeze under a versioned boundary; do not cache around mutable aliases. | P01/P02/P08. |
| **N2: no durable copper-segment/layer identity in current trace carrier** | CODE, proven fields in `PcbTraceGeometry`; it carries net and endpoint IDs plus coordinate arrays. [R04] | Via, cross-layer contact, segment repair and save identity need an explicit conductor model. This cannot safely be postponed until a renderer has already implemented layers. | A03/P02 before P04/E08/U06. |
| **N3: singleton-sensitive solver and observation context** | CODE, `CircuitElm.sim` is static and ScopePlot reads `CirSim.theSim`. [R05/R08] | Creating two CirSim instances in one execution realm does not prove isolation. Start serialized; qualify a real isolated backend before parallel numerical proofs. | A07/A10. |
| **N4: all RuntimeExceptions treated as layout-attempt failures** | CODE, `generate` catches RuntimeException and proceeds through its attempt loop. [R03] | A new programmer error could be mislabeled congestion/exhaustion. Expected search rejection needs a typed result; unexpected faults must retain diagnostics and stop the affected job. | A10/P04/P05. |
| **N5: inherited transformer/relay models have bounded semantics** | CODE, selected transformer and relay model ranges; no fresh numerical fidelity test. [R07] | Coupled winding equations and approximate relay contacts are useful starting points, not proof of offline-converter dynamics, contact leakage observations or power-domain realism. | A07/E03/E05/E06. |
| **N6: physical population and model fidelity can conflict** | INFERENCE from the new RB56/RB100 target and the proposed use of behavioral converters. | A low-cost model that ignores individually visible external parts cannot support their promised measurements/faults. An opaque module must be counted and exposed honestly. | A01/A05/E06 before Q60. |

These are not reasons to abandon the existing code. They are concrete reasons to establish ownership and fidelity boundaries before larger consumers make them expensive to change.

## 8.3 Five ways this plan could still hit a wall two years later

### Wall 1: functional contracts are too weak to compose real circuits

**Failure:** Every block passes in isolation, but mixed variants fail from loading, reference, headroom, startup or feedback interactions. “Equivalent role” turns out to mean only that both have a similarly named port.

**Early detection:** A05 proves structurally distinct implementations against assumptions/guarantees, then Q15/Q30 combine them under changing loads and source states. E06 validates the hard converter boundary before RB56 becomes product content.

**Design correction already in this plan:** Electrical plans carry explicit source/reference/loading/model contracts and causally relevant physical components. Local certificates never replace whole-device healthy and diagnostic proof. A new variant that cannot meet the role is rejected or gets an explicit adapter/role subtype, not a special-case UI patch.

**Residual uncertainty:** The exact useful library and model fidelity cannot be fully specified before the real pilots. A failed model test must change the model scope or adapter, not silently reduce the 60/100-part product requirement.

### Wall 2: logical net, physical copper and live repair identity diverge

**Failure:** The board can draw two layers but trace cutting still operates on a logical bus. Vias, parallel paths and saved cuts start disconnecting the wrong branch. Fixing it requires replacing the routing, mutation and persistence representations together.

**Early detection:** P02 introduces a conductor graph and validates branched nets, layer crossings and barrels before production layer routing, E08 cuts or U06 saves. P01 prevents mutable geometry aliases from defeating those proofs.

**Design correction already in this plan:** Stable device buses describe design intent; stable conductor loci describe physical connectivity; current mutation state projects to the solver. Lossless segment subdivision preserves identity or explicit provenance; actual reroutes are versioned and incompatible saves reject safely.

**Residual uncertainty:** The cheapest general graph-to-CircuitJS projection for arbitrary cuts still needs a focused implementation and performance proof. It is not solved merely by naming three graphs.

### Wall 3: diagnostic proof consumes the entire generation budget

**Failure:** Dozens of owners, variants and temporal states multiply full candidate installations and repeated repairs. A performance patch starts sampling faults and admitting boards without proving their advertised hypothesis set.

**Early detection:** A01 records proof work separately; A09 provides complete hypothesis accounting; D01 compares optimized partitions/receipts against a serial reference before Q30 expands the normal range.

**Design correction already in this plan:** Cheap serviceability and structural pruning, provider-owned plans, global-context checks, bounded adaptive observations and complete-key receipts. Qualified hypotheses may be limited by a declared content envelope, but omitted candidates are not falsely counted as admitted or proved. The chosen fault itself always has the required whole-device evidence.

**Residual uncertainty:** Some real circuit variants may be diagnostically indistinguishable with the available instruments. Reject or add a genuine observation/repair capability; do not manipulate meter readings to force separation.

### Wall 4: mixed time scales or solver globals defeat interactivity

**Failure:** A mains converter needs tiny steps while a relay or sensor sequence needs seconds; repeated proofs monopolize the UI. A worker “optimization” accidentally shares static CircuitJS state or loses model state between slices.

**Early detection:** A07 starts small stiff/nonlinear and aggregate 60/100-part pilots early. E06 compares necessary detailed observations with a qualified averaged model. U03 measures waveform fidelity and data cost separately from drawing.

**Design correction already in this plan:** One serialized numerical owner initially; bounded stepping, cancellation and observation semantics; explicit fidelity/observation limitations; immutable transferable plans. An isolated execution realm or backend extraction is conditional on measured need and requires a real isolation proof.

**Residual uncertainty:** No benchmark here establishes that the current solver meets the full target. A recorded repeated failure after appropriate model/adapter optimization can justify replacing a narrow execution/matrix boundary. Age alone cannot.

### Wall 5: technically valid boards are unpleasant or not genuinely difficult

**Failure:** RB100 generates and solves but users cannot follow references, distinguish layers or reach dense terminals. PSYCHOTIC is slow navigation and random guessing rather than technical reasoning.

**Early detection:** U01 includes operator targeting on full-count structural fixtures; U05 measures diagnostic features; Q30/Q60 include actual play; X05 uses skilled electronics troubleshooters and unseen variants.

**Design correction already in this plan:** Coherent pan/zoom/side access, short physical labels, no invisible enlarged targets, observable power/control interactions, multiple legal diagnostic paths and held-out expert calibration. The UI may help inspect a physical region without highlighting the culprit.

**Residual uncertainty:** Human difficulty and enjoyment cannot be certified from code alone. User trials are part of the architecture evidence, not decorative post-release polish.

### Additional wall: MCU state or simulated time becomes a second truth

**Failure:** Control outputs continue after supply/reset loss, depend on UI frames, lose clock phase on resume, or require hidden firmware knowledge to diagnose. A behavioral shortcut appears functional but ignores exposed external clock/reset/driver parts.

**Detection and deadline:** A03/A06/A07/A08/U06 define state/time/ownership compatibility now. MCU-1 compares implementations with an identical electrical fixture; MCU-2 must pass power/reset/brownout, loaded GPIO, clock loss, ADC/PWM timing, reproducible initialization, interrupted-proof and saved-state tests before an MCU-consuming family is advertised. Measure events/accepted step, memory and runtime on the named host; a paint-rate test is not a solver performance benchmark.

**Mitigation:** One solver-owned electrical pin model and bounded simulation-time control state; a versioned qualified black-box program; semantic pending events in snapshots; no arbitrary firmware, UI-owned outputs or unqualified exact resume. External circuitry remains causal when drawn and exposed. This does not block a non-MCU Q60/Q100 family.

### Additional wall: imported schematics cannot become honest physical challenges

**Failure:** Solver nodes or schematic coordinates become permanent identities, an ideal source is drawn as a PCB part, unsupported constructs disappear, packages/pins are guessed, or the importer invents “healthy” behavior from a file with no declared purpose. An imported-only renderer or fault shortcut bypasses normal serviceability.

**Detection and deadline:** A03/A04/A10/A11/U04/U06 reserve identity, origin, capability and artifact boundaries now. IMPORT-1 must close the complete passive/DC path including an ambiguous-source/intent case before the accepted subset expands. IMPORT-2–IMPORT-5 repeat correspondence, supported-package, meaningful-fault and replay checks for each new category; none may inherit a parsing-only PASS as challenge qualification.

**Mitigation:** Reuse actual parser semantics in a bounded staging context, preserve input content/hash/version, persist accepted mappings, classify every element, request author annotation only where needed, verify healthy intent before fault injection, and use the native physical/diagnostic/mutation/publication pipeline. Unsupported formats or unavailable source bytes fail explicitly. Import is not a prerequisite for native Q60/Q100.

## 8.4 Ranked architectural risk register

Severity below describes **impact if the future risk materializes**, not a claim that the current small playable product has that defect. Likelihood is an engineering judgment based on the inspected seams and new requirements; it is not a statistical prediction.

| Risk | Severity | Likelihood | Earliest detection | Mitigation / accountable boundary | Deadline |
|---|---|---|---|---|---|
| RISK01: weak variant assumptions allow electrically incompatible composition | HIGH | High | A05 mixed-variant tests | Typed assumptions/guarantees, actual adapter and whole-device proof | Before Q30 content library expands |
| RISK02: physical copper identity cannot support layers/cuts/resume | HIGH | High without change | P02 branched/layer fixtures | Three-graph provenance, stable loci and versioned immutable realization | Before P04, E08 or U06 consumers |
| RISK03: converter abstraction removes diagnostic causality | HIGH | High | A07 pilot / E06 model gate | Compare fidelity tiers, require causal visible parts and explicit observables | Before Q60 board implementation depends on model |
| RISK04: numerical ground/reference assumptions create false isolation/readings | HIGH | Medium–high | A06/A07 floating fixtures | Separate physical references from solver gauge; finite instrument models | Before E05 mains/isolation admission |
| RISK05: repeated full diagnostic proofs dominate generation | HIGH | High | A01 counters / D01 comparison | Structured hypothesis set, partitions, receipts, deterministic budgets | Before Q30 normal content expands |
| RISK06: unsafe concurrency aliases solver or old session state | HIGH | Medium–high | A07/A10 failure tests | Serialized owner first; isolated backend only after explicit qualification | Before any parallel numerical execution |
| RISK07: fixed area/region rigidity prevents realistic package mix | HIGH | High for current generic path | A01/P03 demand/placement corpus | Bounded dynamic sizing, hierarchy plus global feedback, explicit barriers | Before Q30 physical support claim |
| RISK08: route representation/validation becomes dominant | MEDIUM–HIGH | Medium | P04/P08 counts and timing | Canonical segments, broad-phase candidates, independent exact oracle | Before Q60 budget freeze |
| RISK09: variant/candidate explosion creates unpredictable work | HIGH | High without bounds | A10 stage/exhaustion tests | Constraint pruning, finite vocabulary, declared budgets and typed failures | Before generic production generation |
| RISK10: geometry/proof cache silently accepts stale state | HIGH | Medium | P01/P08/D01 adversarial tests | Immutable snapshots, complete keys, explicit invalidation, reference parity | Before any cache used for admission |
| RISK11: UI transforms and hidden layers break target truth | HIGH | Medium–high | U01/P07 operator trials | One transform, side/access policy, visible disambiguation | Before two-layer normal play |
| RISK12: long inventory/history grows without lifetime boundaries | MEDIUM–HIGH | Medium; leak unproved | U07 repeated-session workload | Accountable retention/disposal and bounded observation buffers | Before Q60 long-session claim |
| RISK13: replay promises exceed numerical/model compatibility | HIGH | Medium | A03/U06 cross-version tests | Version manifests, supported tolerance/state contract and safe rejection | Before public saved-state/sharing promises |
| RISK14: expensive test Cartesian product overwhelms development | MEDIUM–HIGH | High without layering | A11 qualification-cost ledger | Small exhaustive conformance, selected integration, independent holdout | Before multiple new block families |
| RISK15: hard labels reward clutter, guessing or UI familiarity | MEDIUM–HIGH | Medium–high | U05 and skilled-user trials | Evidence features plus blinded expert calibration | Before HARD/PSYCHOTIC publication |
| RISK16: stale roadmap or pipeline status causes unauthorized work | HIGH | Medium | N00 and each handoff | One live task checkpoint, explicit owner authorization, immutable evidence | Before post-49 implementation |
| RISK17: earlier software assumptions leak into mains safety claims | HIGH | Medium | E05 and X09 review | Explicit educational model limits; no manufactured safety certificate | Before public mains content |

A high-impact unproved risk gets a detection experiment and deadline. It does not automatically get a rewrite. When a gate falsifies the proposed approach, update its design and downstream dependency contract before implementation proceeds; do not keep moving the pass threshold.

**Additional amendment risks (proposed, not newly observed code defects):**

| ID | Severity / likelihood judgment | Early detection | Required mitigation before consumption |
|---|---|---|---|
| RISK18: transient loupe changes identity or leaves stale camera/input state | HIGH / medium | U01 transformation and ordinary input canaries | One temporary view composition, exact permanent-state restoration, lifecycle cancellation and unchanged physical/accessibility identity. |
| RISK19: through-hole assumptions make surface packages impossible | HIGH / medium | P01/P02 developer 0805/SOT-23 fixtures and U01 integration | Explicit mounting side and surface-pad layer, no implied barrel/both-face pin; full SMD gameplay stays deferred. |
| RISK20: stateful IC/MCU model ignores power or loses replay phase | HIGH / medium | A07/U06 compatibility, MCU-1/MCU-2 electrical and resume tests | Bounded accepted-step events, explicit reset/clock/state, finite pin drive and complete model/program/version receipts. |
| RISK21: imported file has no meaningful healthy or serviceable interpretation | HIGH / high for arbitrary input | IMPORT-1 complete pipeline, staged support matrix | Explicit source/control/package/intent negotiation; honest unsupported result; native qualification with retained source provenance. |
| RISK22: future vocabulary becomes an accidental blanket release dependency | HIGH / medium | Document DAG plus per-family capability closure at N00 and qualification | No unconditional new-lane ancestors of Q60/Q100; E08 only for consumed repair actions; safe conditional capability selection and acyclic resolved bundles. |

## 8.5 Architecture change and compatibility policy

Use incremental replacement behind versioned boundaries. Keep legacy Task48 and the actual approved Task49 replay adapters as compatibility paths until an explicit supported-version decision retires them. New providers do not inherit a requirement to reproduce an old bug under a new version. Conversely, a bug fix must not silently alter an accepted old descriptor that the product still promises to replay.

Named streams stabilize random *concerns and tuples*, not the output of every selection under a changed eligible population. An added compatible variant may legitimately change the selected candidate if the versioned eligible set changes. Preserve unchanged stream tuples and declare the algorithm/population version; do not promise that an expanded fault library can never change fault selection. Durable identity must remain separate from the sampled choice.

A proof receipt is an evidence object, not a second authorization registry. The current task report and the owner's actual approval remain authoritative for accepting an implementation. A simulator runtime owns mutable parts and conductive state; the generation manifest and immutable recipes do not become a competing live truth store.

## 8.6 Do not build yet

| Attractive expansion | Disposition | Trigger that could justify it |
|---|---|---|
| Universal circuit-design DSL or symbolic synthesizer | Do not build. Use constrained providers with explicit role/variant contracts. | Repeated real implementations demonstrate stable declarative structure that materially reduces errors. |
| Wholesale CircuitJS/GWT/JDK rewrite | Do not begin from age or file size alone. | A07/E06 show a required fidelity/runtime/API constraint that cannot be addressed with a narrow qualified adapter or extraction. |
| Parallel live CirSim instances in one realm | Forbidden as an assumed optimization. | A real isolated execution design proves state separation and deterministic result publication. |
| Arbitrary deep same-owner snapshots | Deferred. Keep the accepted fresh-owner/limited transaction model. | A concrete measured need plus full state inventory, failure restoration and equivalence proof. |
| Exact global Steiner routing / unbounded negotiated search | Overkill until measured. | P05/P07 show the bounded alternatives cannot support the agreed corpus within its budgets. |
| More than two layers, planes and buried/blind microvia systems | Outside this target. | A later explicit product change and demonstrated failure of the constrained two-layer envelope. |
| Mandatory manufacturing CAD/DRC/Gerber support | Not part of the product. | Separate product decision, not a workaround for simulator correctness. |
| SMD-dominant or BGA-scale boards | Full gameplay remains later; developer-only 0805/SOT-23 architecture canaries are required in P01/P02/U01 now. | A later product use case and qualified package/service/access models; canaries alone do not enable SMD gameplay, BGA, reflow or manufacturing DRC. |
| High-fidelity EMC, RF, contact arcing, magnetics optimization | Not required for the stated educational scope. | A specific advertised observation/failure needs a separately qualified model. |
| Full microcontroller firmware/protocol ecosystem | Still deferred; basic bounded MCU support is explicitly planned in MCU-1/MCU-2. | A separate later product decision for one narrow real architecture only after comparison; no arbitrary firmware, general IDE, CPU-architecture project or protocol ecosystem is implied by basic MCU I/O. |
| Minimap, GPU rendering, WebGL or complex spatial trees everywhere | Conditional. | Measured navigation/render/index workload exceeds the declared budget; a small fix is insufficient. |
| Many simultaneous faults as default | Do not build. | X06 proves a small joint-fault family adds fair diagnostic value. |
| Economy, progression locks and money | Separate optional product decision. | Real catalogs/history exist and a gameplay need is demonstrated; no electrical correctness depends on money. |
| Touch/mobile rewrite | Separate responsive/accessibility scope. | Desktop behavior and target access are stable and the owner chooses mobile as an advertised platform. |
| “Secure” client-side hidden fault encryption | Not a substitute for non-spoiling UI. | A separate anti-cheat/network product requirement; no promise that locally simulated secrets cannot be inspected. |
| Cosmetic source cleanup before foundations | Defer unless directly adjacent. | Proven bug, dependency leak or measured development cost; size alone is not a defect. |

### Modernization decision threshold

Revisit a specific legacy boundary when one of these is recorded: the required model cannot converge or represent an advertised observable under a defensible approximation; serialized execution cannot meet measured responsiveness and cannot be isolated cleanly; required browser APIs cannot be integrated through a narrow compatible bridge; supported build/runtime dependencies cannot be maintained securely/reproducibly; or repeated measured build cost blocks reasonable qualification despite layered tests. Investigate the smallest replacement first: observation/execution owner, isolated computation wrapper, model component, build toolchain or matrix backend. A frontend or complete simulation rewrite is a separate decision with migration, parity and rollback evidence.

## 8.7 Non-negotiable completion discipline

Freeze each authorized milestone's supported corpus, model limits and acceptance criteria before implementation. Concrete newly found correctness defects can add necessary tests; unrelated historical problems and aesthetic wishes cannot expand the finish line forever. An unexpected runtime error, failed negative control or violated ownership boundary is not ordinary generation rejection.

Finish with the actual final source candidate, selected affected regression gates, independent review, accurate limitations, intentional staged diff, normal configured-upstream publication and the current repository notification protocol. Do not claim a publication, cleanup or email that did not occur. Keep the current task report compact and preserve detailed evidence separately.

Neither this document nor its dependency graph authorizes ongoing multi-task execution. The owner chooses scope. A future broad authorization may group independent nodes, but prerequisites and one-writer/shared-resource constraints remain real.


---

## 8.8 Final capability-gap sweep and dispositions

The requested vocabulary is mapped in Section 4.8 and the new cards. The following additional gaps were considered as architectural possibilities, not discovered implementation defects. No new milestone per component is created. “Now” means foundation compatibility or a small developer canary in this roadmap after N00 adoption, never an expansion of the accepted Task49 scope.

| Candidate | Classification | Placement / reason / limit |
|---|---|---|
| Shared-package units, common IC power and resistor-network identity | FOUNDATIONAL NOW | A04/A11/P01 and import manifests must distinguish logical units from one physical package; prevents false part counts and impossible individual-unit repair. No immediate IC library required. |
| Versioned provider state, clock phase and bounded event ordering | FOUNDATIONAL NOW | A03/A07/A08/U06. A shape/synthetic-event fixture prevents later MCU/scan/save incompatibility without implementing firmware now. |
| Surface-only pads and face-specific mounting | ARCHITECTURAL CANARY NOW | P01/P02/U01 mandatory 0805/SOT-23 examples; optional SOIC; no BGA/reflow/catalog expansion. |
| Untrusted imported names, models, external references and input size | FOUNDATIONAL NOW | A03/A10/U04 define trust/origin boundaries; IMPORT-1 implements bounded parser isolation, explicit unsupported resources and safe display. No arbitrary script execution or silent network fetch. |
| Watchdog and reset/brownout supervisors | PLANNED LATER | E12/MCU-2 only for a selected control model; deterministic reset behavior and electrical cause. No full embedded-debug environment. |
| Nonvolatile configuration/calibration/EEPROM-like state | CONDITIONAL | Add a narrow provider-state contract under MCU-2/U06 if a board depends on retained configuration; record power-loss/update semantics. No generic memory/firmware emulator. |
| Calibration and trimmer service actions | PLANNED LATER | E04/E09/U04 may expose a real adjustment when a diagnostic/retest contract needs it; adjustment is electrical, not a “repair correctly” button. |
| Harness pin swaps, high-resistance contacts and cracked-solder-style opens | CONDITIONAL | A09 with E01/E08 or an accepted connector/part mutation provider. Require a precise physical locus, modeled effect and actual repair; not random visual damage. |
| Battery-backed rails, supercapacitor/backup state | CONDITIONAL | E01/A06/U06 when a selected board needs residual/independent power; no new chemistry model or assumption that global OFF removes all energy. |
| Fan tachometer, load feedback and simple electromechanical plant coupling | CONDITIONAL | E04/E14 bounded external-load/stimulus providers when causality matters. Do not require full motor commutation, mechanical/thermal fluid simulation or count off-board plant parts. |
| UART/I2C/SPI and bus-held/stuck-line diagnosis | CONDITIONAL | Later E10/E12/MCU/U10 content decision with real loading, pull-ups, timing and observable bus behavior; no protocol ecosystem or decoder prerequisite for present goals. |
| Probe clips, shared grounds, meter burden/fuse and access to coated copper | FOUNDATIONAL NOW for truthful connection/access; richer interactions CONDITIONAL | P02/U02 define what is exposed and how the instrument connects. U09/U12 qualify common-reference and current-path behavior. Cosmetic clips or scraping/rework gestures are not universal prerequisites. |
| Keypads, encoders and scanned input matrices | CONDITIONAL | E04/E10/MCU-2 when a real control family needs them; external electrical scanning and user input are causal, not a scenario-state shortcut. |
| Graphical LCD/OLED, unrestricted firmware/IDE, full HDL/protocol stack, RF/EMC, BGA/mobile-phone density and manufacturing reflow/DRC | OUT OF SCOPE for this plan's required core | Separate evidence-backed product decision only; basic MCU/logic/display/import capability does not imply any of these. |

## 8.9 Capability manifests prevent dependency inflation

A qualification instance names its design origin, functional family/variant, package/model versions, instruments, source/load controls, diagnostic policy, physical repair actions and persisted state. Resolve only those accepted providers into that instance's dependency closure. A roadmap lane's existence is not an automatic dependency edge.

The hard graph removes E08 from Q60. A trace-repair Q60 instance adds E08; a component-only Q60 instance does not. An RB56-MCU instance adds MCU-2 and the actual display/driver/instrument capabilities it consumes; the baseline RB56 stays valid without them. An imported family adds the minimum accepted importer stage covering its input plus normal model/physical/diagnostic qualification, not every stage or every community element. Q100 remains required under the same rule.

The new implementation lanes are E09–E14, MCU-1/MCU-2, U09–U12 and IMPORT-1–IMPORT-5. They have no unconditional edge into Q15/Q30/Q60/Q100, REL-A, REL-B or REL-1. User-authorized future product adoption can select them without changing that rule. A release advertising one of them must include its current integration receipt; conditional never means untested advertised functionality.

Run cycle checks on both the hard graph and the resolved conditional graph for each chosen family. Detect indirect dependencies through session/save/reliability cards, not only direct edges. Preferred implementation sequence is not a hard dependency, and a provider must not depend on the completed board whose purpose is to qualify that provider. Shared production code still requires coherent integration ownership even when two roadmap lanes are logically independent.

<a id="migration"></a>
# 9. Migration, preserved history and adoption

## 9.1 Mapping the former Tasks50–89

The new IDs replace future tasks, not completed implementation history. The old task names remain searchable below. An old task's intent may now span an early architecture boundary and a later capability/qualification. Do not execute an old prompt solely because a former number is lower.

| Former milestone | New owner(s) | Disposition and reason |
|---|---|---|
| Task49: intent-driven value synthesis | T49 (accepted) → N00; consumed by A04/A05 | Preserve the accepted bounded contract, exact versions/catalog values, legacy generator2 meaning and retained limits. No redesign merely for convenience. |
| Pre-Task50 eligibility/physical-smoke gate | A01/A02, then A04/A05/P03/Q15 | Superseded as an active milestone. Its reproducible reference/scale measurement groundwork moves to A01; canonical candidate eligibility, bend-count and connected-placement corrections move to A02; broader physical/router scalability continues in later P-track gates. |
| Task50: purposeful auxiliary/support block | A05/Q15; integrated through later reference boards | The former standalone sequencing is superseded by the new functional/provider/content architecture. Keep real functional support through provider construction and unaffected-function retest instead of another bounded assembler branch. |
| Task51: composed acceptance/rejection pipeline | A09/A10/D01 | Split production proof ownership, generation orchestration and scalable diagnostic strategy. |
| Task52: layout stress corpus/telemetry | A01, P03–P09, Q15/Q30/Q60/Q100 | Start measurements early, then qualify every explicit scale band. Separate structural probes from playable claims. |
| Task53: region-aware placement | P01/P03 | Promote coordinate/pose correctness and demand-based hierarchical placement to foundations. |
| Task54: high-degree tree/trunk routing | P04 | Integrate into a coherent multi-terminal routing baseline with semantic priorities. |
| Task55: bounded rip-up/reroute | P05 | Required recovery investigation, with negotiated congestion only when evidence justifies it. |
| Task56: generated link fallback | P06/P09 | Require a truthful raised-crossover prototype; sparse use and policy remain measured choices. |
| Task56(A): two-sided prototype | P02/P07/P09/U01 | No longer “only after everything else fails.” Representation and working comparison are mandatory early investigations. |
| Task57: supported physical scalability | P09 plus Q15/Q30/Q60/Q100 | Split physical envelope from electrical/diagnostic/player qualification at actual scale. |
| Task58: difficulty foundation | U05/A09/D01 | Computed evidence, legal plans and profile constraints, not raw part counts. |
| Task59: EASY/MEDIUM calibration | U05/REL-A | Preserve truthful initial profiles while advanced profiles wait for their evidence. |
| Task60: Resources | U04 | Can use existing physical/public information without waiting for every future feature. |
| Task61: Settings/shell truthfulness | U04/X09 | Presentation/accessibility settings and honest navigation; no physics or inventory ownership in the shell. |
| Task62 and Task62A: menu, sessions, replay/results | A03/U04/U06 | Identity and session boundaries precede richer persistence. Menu remains orchestration only. |
| Task63: desktop alpha | REL-A | Intermediate qualified low-voltage release, not the final target or a reason to defer expensive architectural questions. |
| Task64: static axial/PCB realism | U08 | Retain as envelope-safe polish, not a prerequisite for unrelated circuits. |
| Task65: continuous LED physical lens | U08/U02 | One solved intensity source; accessibility-safe equivalent observations; no second brightness physics. |
| Task66: relay-driver block | E03/A05 | Alternate BJT/MOSFET implementations, proper package/fault semantics, source foundations first. |
| Task67 and Task67A: independent rails/source/reference contracts | A06/A07/E01 | Move architecture early; no circular dependence on a relay that already needs it. |
| Task68: regulator/multi-rail block | E02/E06 | Separate low-voltage regulation from high-risk offline conversion and its fidelity gate. |
| Task69: sensor/comparator | E04/A05 | Player-stimulated alternate conditioning/control implementations with explicit references/loading. |
| Task70 and Task70A: authoritative Shop/catalog | U04/A08/A11 | Existing providers may supply an honest catalog without waiting for all future part families. |
| Task71: player jumpers | E08 | Requires source consequences, conductor identity and side/access semantics; distinct from factory links. |
| Task72: copper identity and cutting | P02/E08 | Move identity before routing/layers/persistence consumers; later implement physical cutting. |
| Task73: trace repair | E08/U06 | Functional alternatives and saved conductor state share one mutation model. |
| Task74 and Task74A: fuse/protection | E01/E03/E05/X02 | Basic source/protection semantics early; richer part stress only when models are qualified. |
| Task75 and Task75A: bench supply/current limit/source consequences | A06/E01/U02 | Real source behavior before harmful repairs or short-fault admission; not a late UI clamp. |
| Task76: scope | A07/U02/U03 | Shared bounded observations and instrument references before temporal/mains diagnostic content. |
| Task77: triggered timer | E07 | Only after normal observation and player trigger exist. |
| Task78: frequency/oscillator | U03/E07 | Measurement and block behavior remain separate, solver-time-backed contracts. |
| Task79: capacitance decision/proof | X08 | Conditional diagnostic need; no false dependency on an unrelated oscillator. |
| Task80: HARD calibration | U05/Q30/Q60/REL-B | Real advanced corpus, source/repair/instrument capabilities actually consumed, and fresh calibration. |
| Task80(A): beta | REL-B | Now explicitly requires the primary 40–60-part product surface. |
| Task81: intermittency | X01 | Deterministic real graph/state events with ordinary capture/retest. |
| Task82: secondary damage | X02 | Solved cause, duration and retained physical owner; not automatically dependent on intermittency. |
| Task83: thermal approximation | X03 | Conditional diagnostic or timing value, not compulsory scenery. |
| Task84: customer returns/history | U06/X04 | Persist actual serviced state; thermal/intermittent dependencies only where the chosen model consumes them. |
| Task85: PSYCHOTIC | X05 | Explicit expert calibration and held-out variant trials. No promise that component count alone creates difficulty. |
| Task86: multiple-fault decision | X06 | Conditional bounded joint proof, never default arbitrary combinations. |
| Task87 and Task87A: history/scoring | U06/X07 | Semantic history early; scoring optional observer, not a save prerequisite. |
| Task88: mutable save/resume | A03/P02/A08/U06/U07 | Schema and ownership early; actual stateful resume must prove supported model state, not serialize transient solver nodes. |
| Task89, Task89A and Task89B: sharing | A03/U04/U06 | Pristine challenge sharing separate from mutable progress. Versions, privacy and safe rejection apply to both. |
| Former post-beta appliance-scale backlog | E05/E06/Q60 | Promoted to an explicit primary product target, not deferred until a future product-need debate. |
| Former 75–100-part optional stress concept | A01/A07/P07/Q100 | Promoted to early architecture probes and required final constrained stretch qualification. |
| Mature/1.0 previously unnumbered | REL-1 | Explicit readiness node for the actually promised mature surface; not merely exhaustion of tasks. |

### What the new numbering prevents

The old prompt “do Task50 next” is ambiguous after N00 adopts this edition. Use the new ID and title in all new prompts and evidence directories. Existing `task-49` records keep their original identity. New evidence can use `docs/task-evidence/A04/` or an equally consistent documented convention; do not rename old evidence merely for aesthetic uniformity.

Each node may be implemented in smaller coherent reviewed changes, but no parent becomes accepted until all of its actual required deliverables pass. Prototypes, architecture fixtures, implemented providers, playable qualification and release gates remain distinct statuses.

<a id="completed-ledger"></a>
## 9.2 Preserved completed ledger

The following summarizes accepted history through the accepted Task49 baseline at `3de4da1d3bad3ed532e6c327b24195bc3138ed15`, whose direct parent is the pinned Task48 baseline. It is carried forward from the previous roadmap, not recertified by this planning exercise. Detailed contemporary requirements, failures and qualification remain in the repository's immutable history and existing task-evidence directories. Task49's accepted contract, evidence and retained limits are recorded in the T49 card and packet.

| Task | Accepted completed result |
|---|---|
| 1 | Reproducible JDK8/GWT build and development workflow. |
| 2 | Improved red/black probe controls and instrument pointer behavior. |
| 3 | Measurement endpoint and CircuitJS adapter boundary. |
| 4 | Active-measurement session and initial board-power safety boundary. |
| 5 | Stable board, component, pad, net, binding, and external-input model. |
| 6 | First seeded generated LED-indicator family and logical/simulation binding. |
| 7 | Family-agnostic generated-board ownership and solver-gated verification. |
| 8 | Real external board-power isolation distinct from simulation RUN/STOP. |
| 9 | Solver-backed resistance measurement and hardened transaction lifecycle. |
| 10 | Continuity policy over the resistance primitive. |
| 11 | Finite-compliance diode test and semantic probe/cleanup correction. |
| 12 | Reversible lead lift, reconnect, remove, and restore graph mutations. |
| 13 | Interactive PCB-primary workbench and physical parts tray. |
| 14 | Solver-validated open-resistor challenge and gated lifecycle. |
| 15 | Electrically real resistor replacement, physical isolation, and functional repair. |
| 16 | Solver-backed 10 MOhm DC voltmeter loading and lifted-lead behavior. |
| 17 | Retained-probe refresh correctness and stable physical-part probe identity. |
| 18 | Unlimited resistor catalog and normal-player replacement validation. |
| 19 | Family-agnostic generated-board replacement state boundary. |
| 20 | Visible development-preview repair. |
| 21 | Post-refactor validation and preview hardening. |
| 22 | Self-contained deterministic browser verification. |
| 23 | Replaceable silicon-diode challenge family. |
| 24 | Replaceable LED identity and persistent preview behavior. |
| 25 | First seeded procedural one-sided PCB layout generator. |
| 26 | Routing, clearance, escape, and physical-believability hardening. |
| 27 | Genuine parallel circuit, KCL, and in-circuit parallel measurement. |
| 28 | Compact topology-aware placement, derived outline, and routing courtyards. |
| 29 | Component-identification fidelity and original-value privacy. |
| 30 | Generic functional completion contract. |
| 31 | Seeded fault engine and compatible real graph effects. |
| 32 | Solver-compatible scenarios and customer complaints. |
| 33 | Wrong-repair semantics and post-repair solver validation. |
| 34 | Resistor ratings and solver-derived stress/damage v1. |
| 34(A) | Physical runtime, workbench, renderer, and instrument extensibility hardening. |
| 35 | Generalized physical specifications, catalogs, packages, and inventory identity. |
| 35(A) | Quick Play and normal-player Finish Job loop. |
| 36 | Capacitor foundation, stored-energy safety, and RC temporal family. |
| 37 | NPN low-side switch and corrected control/state/layout behavior. |
| 38 | NMOS low-side switch and corrected physical control/gate topology. |
| 39 | Player-operable inputs and solver-backed customer retest. |
| 40 | Physical fault-locus and serviceability admission. |
| 41 | Diagnostic solvability proof and deterministic complexity evidence. |
| 42 | Existing-family diagnostic diversity, including a second LED physical owner. |
| 43 | Versioned package geometry and physical interaction-envelope contract, with final integrated acceptance. |
| Post-43 Gate A/B | Mainline/evidence consolidation and verification/isolation qualification, under their recorded bounds. |
| 43P | Bounded runtime/integrity reconciliation, C1/C2 correction, and accepted handoff. Historical wider-proof limits are not erased. |
| 44 | Immutable functional block descriptions and stable namespaces. |
| 45 | Typed electrical-domain/port metadata and pure compatibility preflight. |
| 46 | Versioned challenge descriptor, named streams, constraints, and explicit legacy replay. |
| Composition Entry Gate | Settled actionability, stale-work guards, fresh-owner publication, bounded resistor compensation, and selected correspondence/cleanup proof. |
| 47 | Two-block developer resistive canary and bounded contribution/assembly contracts. |
| 48 | Playable composed controlled-indicator proof with normal input, diagnosis, physical repair, and customer retest. |
| 49 | **ACCEPTED** at `3de4da1d3bad3ed532e6c327b24195bc3138ed15`; direct parent `62c878b8f381e3418214a93a46d3e8d2d9693b3e`, with the bounded versions, values, evidence and retained limits recorded in the T49 card. |

## 9.3 Adoption and current checkpoint

This file is a replacement roadmap, not an `AGENTS.md`, `ARCHITECTURE.md` or task-report replacement. Task49 is accepted at the named SHA. N00 is the completed document/lineage adoption gate: it inspected the accepted evidence and preserved its limitations while adopting this Edition 2.1 roadmap.

Do not overwrite the task report with this document, mark the proposed classes as already implemented, force a branch merge or delete old worktrees. The current accepted baseline is the actual reviewed Task49 handoff at `3de4da1d3bad3ed532e6c327b24195bc3138ed15`, not an imagined commit containing this roadmap. The pinned Task48 SHA remains the direct parent and historical source-read baseline. Publication of this adoption candidate still follows the final status-delta review, final document/staged checks and normal commit/push contract; its exact SHA, remote verification and notification outcome belong in the final handoff.

**Immediate next boundary after N00 adoption:** A01 — Reference-board manifests and reproducible measurement harness — was separately authorized and is now IMPLEMENTED — ACCEPTED. A02 is now IMPLEMENTED — ACCEPTED; A03 is the next unstarted boundary. No support-block, former Task50 or large-board implementation starts automatically because the roadmap is installed.

The standalone Markdown contains the active plan in full. Historical package references to the old audit, dependency/traceability manifest and document-check results are background provenance only; absent companions are not runtime dependencies or additional approval bureaucracy.

## 9.4 Keeping the roadmap useful

After each accepted node, update only its real status and source/evidence references, the current checkpoint, and any dependency consequences. Keep exactly one immediate next authorized scope. Record reasons for rejected algorithms and missing support rather than re-running abandoned experiments from scratch.

If evidence changes a design, record: original assumption; failing fixture; measured or observed behavior; alternatives considered; chosen correction; versions affected; reused evidence invalidated; and downstream gates requiring requalification. Do not rewrite failed evidence as though the successful design had always existed.

Practical pluggability is reviewed at A11, Q30, Q60 and Q100 using real feature additions. The mature goal is roughly 8–9/10: an ordinary supported block/variant is provider-local and does not add device knowledge to generic systems. A truly novel electrical model may properly need a solver adapter, package, mutation/fault semantics and tests. Counting total changed files without classifying their responsibilities is not the metric.

<a id="sources"></a>
## 9.5 Sources and actual coverage

### Planning inputs

| ID | Input | Use and boundary |
|---|---|---|
| **I1** | Owner's attached `Pasted markdown(9).md`, full large-board architecture brief | Authoritative changed product targets, required investigations and roadmap deliverables. Its statements about current code were checked where indicated, not assumed universal truth. |
| **I2** | `TroubleshootJS_Architecture_Roadmap_Audit.md`, prior audit at the pinned Task48 SHA | Fourteen findings, named 33-file coverage, source S1–S23 index and prior E1/E2 helper experiments. The supplied package named `research/TROUBLESHOOTJS_ARCHITECTURE_AUDIT_2026-09-06.md`; that historical companion is absent from this checkout. Its old small-product priorities are superseded. |
| **I3** | Prior supplied replacement `ROADMAP.md`, edition post-Task49 1.0 | Completed ledger, previous dependency corrections and migration coverage. This edition replaces its future sequence; it is not an additional live roadmap. |
| **I4** | In-conversation authorized Task49 prompt and owner's active-work statement | Historical input that protected current bounded value synthesis before review; its earlier no-completion inference is superseded by the accepted Task49 handoff above. |
| **I5** | Owner's complete final-amendment brief, `Pasted markdown(10).md` | Historical authority for this preservation-first Edition 2.1 reconciliation, explicit additions, conditional Q60/E08 correction and Task49 protection. The named archive `research/ROADMAP_AMENDMENT_BRIEF_2026-09-07.md` is not present in this checkout. |
| **I6** | Complete supplied `TroubleshootJS_Reconciled_Roadmap.md`, Edition 2.0, and its bundled graph | Exact amendment base. Existing cards/decisions/history are retained except the requested scoped amendments and their consistency updates. Source hash and preservation checks are in the document receipt. |

### Edition 2.0 source reads, preserved as historical evidence

All repository links below are pinned to `62c878b8f381e3418214a93a46d3e8d2d9693b3e`. “Read” denotes source inspection, not executed acceptance. Fresh ranges overlap the prior audit and must not be added to its file count as if every file were new.

| ID | Pinned source | Actual inspection / use |
|---|---|---|
| **R01** | [`CODEX_TASK_REPORT.md`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/docs/CODEX_TASK_REPORT.md#L1-L67) | Current Task48 qualification/status; separately fetched branch collection. Source report is retained evidence, not rerun tests. |
| **R02** | [`BoundedGeneratedBoardAssembler.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/BoundedGeneratedBoardAssembler.java#L570-L815) | Fresh selected construction, explicit fixed controlled-device allocation and solver/board binding. |
| **R03** | [`SeededPcbLayoutGenerator.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java#L1-L89) | Fresh fixed outline/grid/attempt bounds, generate loop and RuntimeException handling. |
| **R04** | [`PcbTraceGeometry.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/PcbTraceGeometry.java) | Fresh full short carrier; fields, constructor aliases and array getters. |
| **R05** | [`CircuitElm.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/CircuitElm.java#L35-L130) | Fresh selected static simulator context, element state and initialization. |
| **R06** | [`CirSim.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/CirSim.java#L2320-L2430) | Fresh selected node/reference handling and 100 MΩ numerical stabilization. |
| **R07** | [`TransformerElm.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/TransformerElm.java#L1-L260); [`RelayElm.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/RelayElm.java#L1-L300) | Fresh selected coupled-winding stamping and approximate coil/contact model, including reported current limitations. |
| **R08** | [`Scope.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/Scope.java#L1-L175) | Fresh selected ScopePlot sampling, ring storage and global simulator-time dependency. |
| **R09** | [`GeneratedDiagnosticSolvabilityAdmission.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/GeneratedDiagnosticSolvabilityAdmission.java#L1-L70) | Fresh production validateLive/developer-verifier call boundary and physical access checks. |
| **R10** | [`Task41DeveloperVerifier.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/Task41DeveloperVerifier.java#L277-L360) | Fresh candidate grouping/filter/deduplication and per-candidate installation. |
| **R11** | [`PhysicalPackageGeometry.java`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/src/com/lushprojects/circuitjs1/client/PhysicalPackageGeometry.java#L1-L230) | Fresh selected immutable geometry/copies, translation and horizontal mirror projection. |
| **R12** | [`ROADMAP.md`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/docs/ROADMAP.md); [`ARCHITECTURE.md`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/docs/ARCHITECTURE.md); [`AGENTS.md`](https://github.com/dspevo-afk/TroubleshootJS/blob/62c878b8f381e3418214a93a46d3e8d2d9693b3e/AGENTS.md) | Prior same-SHA reads in this conversation/audit, used for contracts and history; not labeled new exhaustive reads. |

The branch collection was also fetched directly through connected GitHub at the pinned historical baseline and listed `codex/task43p-final-recovery` at the Task48 SHA, with `master` at the older post-43 baseline. No published Task49 implementation was present in that historical read. The accepted Task49 handoff is now the named SHA recorded at the top; the older source read cannot override it.

### Prior source evidence reused at the same immutable baseline

I2's Appendix A records the actual earlier 33-file review. Relevant reused paths include `BoundedAssemblyRequest`, `BoundedAssemblyPlan`, `ChallengeDescriptor`, `NamedRandomStreams`, `BlockNamespace`, `PortCompatibilityPreflight`, `TopologyPlacementGraph`, `PhysicalPackages`, `PcbFootprint`, `PcbBoardLayout`, the workbench controller/renderer, generated instance/runtime, fresh installation, resistor transaction, challenge lifecycle, measurement adapter/controller, instrument providers, power controller, fault candidate/contracts and selected development/build documentation.

Those paths are not represented as newly executed or exhaustively re-reviewed here. The unchanged source pin makes them useful prior evidence, not a whole-project clean bill of health. The prior audit's helper-level bend/placement experiments and packing arithmetic remain **PRIOR EXPERIMENT/CODE-DERIVED**, not live browser findings.

### External primary references retained from Edition 2.0

| ID | Primary reference | Limited use |
|---|---|---|
| **W1** | [KiCad PCB Editor documentation, 7.0](https://docs.kicad.org/7.0/en/pcbnew/pcbnew.html) | Primary reference for pad/layer/plating vocabulary. Through-hole pads have plated multi-layer connectivity; NPTH has no electrical connection. Not a routing-performance benchmark. |
| **W2** | [VTR/VPR command-line documentation](https://docs.verilogtorouting.org/en/latest/vpr/command_line_usage/) | Primary reference for bounded router iterations and present/history congestion controls. FPGA routing context, not proof that Pathfinder is a drop-in PCB solution. |
| **W3** | [Texas Instruments UCC28713 product documentation](https://www.ti.com/product/UCC28713) | Example of an isolated flyback regulation/control function whose startup, load and input dependence motivate model observables. Not a qualified CircuitJS model. |
| **W4** | [Tektronix Floating Oscilloscope Measurements and Operator Protection](https://www.tek.com/fr/documents/technical-brief/floating-oscilloscope-measurements-and-operator-protection) | Primary source distinguishing earth-referenced commons from floating/differential measurement. Used for conceptual reference modeling, not as live-work instructions. |

External references supply physical vocabulary and examples of real model/algorithm concepts. They do not establish that TroubleshootJS currently implements those concepts, that a specific PCB algorithm will meet the target, or that CircuitJS has passed the proposed mains/100-part tests. All scored strategy comparisons and future architecture choices are this roadmap's judgments.

### Verification performed on this document

The historical Edition 2.1 preparation receipt described checks for preserved and added milestone IDs, required card fields, known hard/conditional prerequisite references, graph acyclicity, reverse dependencies, former-task coverage, audit findings, completed history, base reference-board allocations, internal links and package integrity. That preparation receipt treated Task49 as active; it is historical evidence and does not certify N00. N00's completed document/dependency checks verified those preservation properties against the accepted Task49 lineage, the in-document card graph, the conditional Q60 closure, the retained Q100/PSYCHOTIC contracts, and the current two-file diff: 72 unique milestone IDs, 248 hard edges and 16 conditional edges, acyclic graphs, reverse references, internal links, history/future fields and 12 named negative canaries all passed. These structural checks do not prove implementation feasibility or absence of future design defects.

**These are document checks only.** They are not production builds, solver benchmarks, browser or player validation, or GitHub Actions certification. The fresh Turing Luna MAX review is an independent document review, not runtime evidence. N00 adds no production, test, script, source or configuration changes and does not authorize A01 or later implementation.

## 9.6 Final architectural position

Retain the simulator and the useful identity/geometry/runtime foundations. Replace the central construction and diagnostic seams before the library grows. Make copper identity, domain/reference behavior and model fidelity explicit before layers, mains, cuts and persistence depend on them. Run early difficult-model and full-count cost probes, then qualify progressively richer real boards. Treat the 40–60-part goal and the constrained 100-part goal as commitments to investigate and deliver, not wishes to remove when inconvenient.

The plan cannot guarantee that no difficult redesign remains. It can require that each expensive assumption earns evidence while it is still local enough to change. That is the difference between deliberate architecture and simply hoping later tasks will repair earlier shortcuts.
