# TroubleshootJS: Current-Only Development Roadmap

**Edition:** 3.0, complete replacement for Edition 2.1<br>
**Prepared:** September 9, 2026<br>
**Repository:** `dspevo-afk/TroubleshootJS`<br>
**Drop-in destination:** `docs/ROADMAP.md`<br>
**Historical Edition 3.0 review baseline:** `cc3532e8d138424ce986aa8f9b76688ec315f0a0`<br>
**Historical baseline commit:** `Complete A04 provider-owned electrical construction`<br>
**Branch observed during review:** `codex/task43p-final-recovery`<br>
**Latest completed task:** **P02: Durable layer-aware copper and conductive-surface model**<br>
**Current checkpoint:** **P02 ACCEPTED; A10 THEN A11 AUTHORIZED.** The owner supplied the fresh independent delta-review PASS for the exact P02 candidate; F1 is resolved with no blocking findings. P02 passes 20 native suites, 3,209 P02 assertions, the five-permutation GWT build and twelve compiled routes. Closeout revalidated all 46 candidate files, 642 source/test inputs, eight runners/readers and five compiled permutations without drift. The Windows wrapper remains separately unqualified. A10 follows P02 publication; A11 follows A10. [P02 evidence and limits](task-evidence/P02/README.md).

> Build the current game. Historical development code, challenge versions, file formats, identifiers, reports and tests are not product contracts. Keep useful working behavior because the current game needs it, not because an old milestone happened to implement it that way.

This is a self-contained replacement roadmap, not an amendment to apply over the old one. No companion dependency file, old task report, Library attachment, external roadmap or historical appendix is required to interpret its future scope. Repository source/evidence paths in Section 10 establish the review basis, not mandatory frozen outputs. The roadmap does not itself execute changes; R00 was separately authorized and completed; A05, A06, A07 and A08 were separately authorized and are now qualified. Future tasks require their own authorization.

The 72 prior catalog identities remain traceable as feature/history labels, with one corrective node R00, for **73 catalog nodes**. Eleven are delivered/completed baseline entries, including A08, and 62 are unstarted feature/qualification nodes. Their old implementation details are not protected. Product requirements have not been deleted to reduce the apparent task count.

## Navigation

1. [Product direction and current-only policy](#policy)
2. [Reviewed baseline and R00 exit conditions](#baseline)
3. [Target architecture and capability boundaries](#architecture)
4. [Functional taxonomy and reference boards](#reference-boards)
5. [Architecture decisions and alternatives](#decisions)
6. [Scale, physics, interaction and validation](#validation)
7. [Milestone catalog and execution order](#milestones)
8. [Capability-conditional dependency rules](#capabilities)
9. [Risk register, non-goals and completion](#risks)
10. [Review basis, adoption and change ledger](#sources)

<a id="policy"></a>
# 1. Product direction and current-only policy

## 1.1 What the game is

TroubleshootJS is a free, open-source, PCB-first electronics troubleshooting game around CircuitJS. The player receives an incomplete customer complaint, examines an unfamiliar board, chooses measurements, isolates faults, makes physical repairs and verifies restored customer function. It is not a schematic quiz, generic PCB CAD package or a test-framework demonstration.

Generation should produce meaningful structural variation: roles, implementations, sensible values, packages, support circuits, layout and fault choices. Healthy auxiliary circuitry is allowed and must perform a real function. Wrong repairs remain possible and have their modeled electrical consequences. Valid alternative repairs can succeed when they restore the required behavior. Difficulty comes from electrical reasoning, not inaccessible controls or hidden simulator tricks.

The targets remain routine 5-20-part boards, normal 20-40-part boards, advanced 40-60-part appliance/control boards and a constrained genuinely procedural approximately 100-part family. Q60 and Q100 are required targets. The EASY/MEDIUM/HARD/PSYCHOTIC progression remains; PSYCHOTIC is expert reasoning over interacting electrical conditions, not padding the board with inert parts.

**Visual difficulty is allowed. Interaction dishonesty is not.** A dense board need not be comfortable to understand at overview scale. Required markings, exposed terminals and controls must be accurately inspectable and usable through supported zoom/loupe/side views. No enormous invisible hit regions, hidden essential switches or visual disclosure of the selected fault.

## 1.2 Explicit owner decision: no historical development compatibility

No backward compatibility with prior TroubleshootJS implementations is required unless the owner later approves a concrete external support contract. This includes previous challenge generators, layouts, replay/saved-state formats, numerical version identifiers, public/internal class names, constructors, aliases, registries, serialized identifiers, snapshots, golden reports and test expectations.

An intentional correction may change old seeds' selected circuits, geometry, fault distributions, ID spellings, output bytes, report shape and construction order. Update all current callers and tests. Remove the obsolete path rather than add a compatibility facade. Reject incompatible artifacts before changing live state; do not silently reinterpret old bytes as the current format.

Reproducibility remains a current requirement: the same supported current implementation, inputs/settings and seed produce the same discrete selected design. Transport full signed-long seeds exactly. Named random streams keep unrelated concerns isolated within that current contract. A build/schema/model epoch or a single current manifest can identify interpretation; it does not require retaining old implementations. Keep distinct currently useful variants as variants, not historical revisions disguised as variants.

Current save/resume, reset and in-game history are not abolished. U06 qualifies the current supported format. Development updates may invalidate old saves/challenges. An alpha, beta or mature release label does not automatically establish an indefinite compatibility promise. Any future promise must identify the real users/data, supported versions, cost and retirement policy and receive explicit owner approval.

## 1.3 What still matters

Keep real solver-backed behavior, coherent active state, identity through current operations, correct physical/electrical correspondence, truthful measurements, privacy of the answer, reproducibility and failure isolation. These are current game requirements, not immunity for any old implementation. Existing tests are evidence of behavior, not authority over the owner's revised contract.

CircuitJS is the working electrical backbone because replacing it would require substantial new evidence, not because its menus, API spellings, file readers or internal code are sacred. Retire unused upstream-facing compatibility surfaces when actual caller analysis demonstrates a benefit and current behavior remains correct. Do not casually delete numerical stabilization or replace the solver/build chain during R00. Legal notices, credentials, unrelated files, the user's processes and active work remain protected; permission to retire code is not permission for destructive repository or machine cleanup.

## 1.4 Current validation instead of historical equality

| Retired obligation | Current requirement |
|---|---|
| Old generator revisions still execute | Current useful content executes; incompatible old artifacts reject clearly. |
| An old seed produces old pixels/IDs/reports | Current generation is deterministic, physically correct and usable. |
| Exact old stage/element counts | Current construction ownership, endpoint truth and failure cleanup are correct. |
| Task48/49 manifests/reports remain byte-identical | Current recipes, packages, values, ratings, faults and repair behavior agree across layers. |
| Every old test and development route must pass | Applicable current invariants have independent, effective tests. Retired behavior tests are removed or replaced. |
| Historical compatibility tests count as gameplay | A real current challenge can be diagnosed, repaired and retested through ordinary workbench actions. |

A failed applicable correctness test must be repaired or replaced with a reviewed better oracle, not ignored. An obsolete behavior test is not a blocker once its contract is intentionally retired and replacement coverage exists. Historical equality is never used to preserve a known bug.

<a id="baseline"></a>
# 2. Reviewed baseline and R00 exit conditions

## 2.1 Historical A04 review basis for Edition 3.0

A04 at `cc3532e8d138424ce986aa8f9b76688ec315f0a0` is the starting implementation, not something to revert. Its recorded evidence reports passing native checks, the five-permutation JDK8/GWT build, compiled A04 conformance, the then-selected electrical/repair regressions, cleanup and independent static review. The recorded review is not represented as having run the browser gates; the implementation root ran those separately.

The new scoped construction and physical declaration boundaries are useful. Materialization is still bounded to fixed parts and mutable resistors. Multi-unit package fixtures prove a data/conformance shape, not a working IC/MCU library. No A05, large-board, importer, general mutation rollback or save UI is qualified by A04.

This planning review read the exact commit, targeted implementation/callers and the recorded evidence. It did not independently rerun a full production build or browser playthrough. An attempted independent native test invocation was blocked by the execution interface and supplied no result; recorded passing receipts remain attributed to the A04 implementation run. Document checks for this edition do not certify the application.

The committed Edition 2.1 roadmap has contradictory status text: its A04 card says complete/qualified, while Section 7.1 still calls A04 validation-blocked and directs qualification closure. This edition removes that contradiction. At preparation, R00 was the deliberate next policy/consolidation correction, not a claim that A04 was unfinished. R00 is now accepted; the current checkpoint above and milestone cards govern later authorized work.

## 2.2 Concrete cleanup findings

| Finding at reviewed baseline | Required disposition |
|---|---|
| Leaf replay supports old and corrected layout revisions; older placement/scoring still has reachable defaults | Remove obsolete revision dispatch and make the corrected current implementation authoritative across current callers. Keep genuinely different current leaf families. |
| Historical fixed-value controlled route and version-1 fault reinterpretation | Use one truthful current recipe per useful content variant. Fix the descriptor itself to describe its real fault and target; remove special reinterpretation of an OPEN wrapper as an incorrect-value fault. |
| Old and durable IDs coexist partly to preserve historical report text | Use explicit current component/terminal/bus identities in the affected plan-to-runtime flow. Remove history-only aliases/translators; keep legitimate logical/physical/solver mapping boundaries. |
| Physical materializer/declaration ordering depends on controlled versus resistive history | Enumerate declared current nets/parts/owners deterministically. Do not require old component/slot order, exact intermediate count or hardcoded source/driver/load naming in generic code. |
| Three A04 physical-provider hardening follow-ups | Close them before new A05 providers can depend on the boundary: full pad/component/net correspondence; secondary/attachment/fault backing ownership; same plan/spec/metadata/receipt construction provenance. |
| Package-less rejecting constructors and implicit package selection | Remove obsolete entry points after migrating current callers. Require explicit package choice where the current architecture needs it. |
| Whole historical report/golden equality and duplicated gate wrappers | Establish current contract checks and one maintained invocation surface; retain useful independent oracles and test-runner safety. Historical scripts may be deleted after useful coverage is migrated. |

These findings do not prove a currently reachable player crash. The missing materializer checks are documented nonblocking limitations under the old closed coordinator; expansion makes them worth fixing now. Residual family knowledge in a supposedly generic boundary is architectural debt even when the current two fixtures pass.

## 2.3 R00 implementation checkpoints

**R00-A: current contract and inventory.** Confirm the actual branch/HEAD/diff, read applicable instructions and callers, and adopt this policy in AGENTS.md/ARCHITECTURE.md/ROADMAP.md before implementation. Produce one compact inventory classifying actual retirement candidates as obsolete revision, current feature, useful test oracle, current domain mapping or unrelated upstream infrastructure. Select the current content and replacement tests. Do not create a new permanent policy framework or a milestone per deletion.

**R00-B: consolidate the current pipeline.** Build forward from A04. Retire obsolete versions, behavior, API entry points and history-only mappings. Keep or migrate current simple resistor fixtures and current controlled-indicator content; do not remove useful families to evade test failures. Normalize the actual contribution fault data so validation, injection and repair agree without legacy branches. Migrate current identity and physical declarations together. Remove concrete device/net/order knowledge from the generic materializer where declarations already supply it. Do not merely move a giant switch into a renamed generic helper.

**R00-C: enforce the provider boundary.** Before any physical mutation, establish that plan, immutable electrical spec, metadata and live construction receipt belong to the same construction attempt. Matching IDs or equal serialized contents alone do not establish live ownership. Validate declared component/pad/net/terminal coverage against the spec and package map, including exact allowed relationships. Secondary paths, attachment wires and fault backings must belong to the correct declared physical/electrical participant, not just have a matching class/kind. Keep explicit device-owned bridges and legitimate shared package relationships. Add misuse tests plus valid controls; no universal hostile-Java sandbox or deep rollback engine.

**R00-D: current tests and playable acceptance.** Remove obsolete tests after migrating their still-useful invariants. Keep current seed transport/determinism, independent geometry/electrical checks, and negative ownership/failure tests. Run one final production build and focused compiled verification against the integrated candidate. Exercise ordinary diagnosis, removal/replacement, wrong repair, correct repair, power/reset and customer retest on a current challenge traversing the affected path.

A composed fixture may use an existing developer launch solely to select/install the board if that is its only current entry. Record this limitation, then use ordinary visible workbench actions; no private state mutation may substitute for diagnosis/repair. Do not claim the normal menu already uses the new pipeline. A05 must close normal-player launch integration for its new provider/variant path. Also check affected currently available leaf routes. A leaf-only playthrough cannot establish that new composed construction works.

## 2.4 Closed R00 exit set

1. Obsolete revision selectors, bug-preserving algorithms, compatibility-only APIs and fault reinterpretations in the scoped retirement inventory are removed; remaining matches have a specific current purpose.
2. Current useful content traverses a coherent current construction/identity/physical path. Unknown/retired artifact or provider inputs fail before live mutation.
3. All three A04 boundary follow-ups have explicit current implementation and negative tests, or have been eliminated by a simpler design whose behavior is independently tested.
4. Tests assert current game contracts. No test is required solely to reproduce old report bytes, historical ID strings, incidental solver node numbering or stage counts. Current JVM/GWT parity and tolerated electrical checks remain when actually relevant.
5. The actual production build, focused compiled electrical/physical checks, ordinary workbench behavior and affected lifecycle/cleanup gates pass on the integrated candidate. Unsupported or blocked gates are reported honestly, never marked passed.
6. One fresh independent read-only review checks the integrated diff, original revised requirement and relevant tests, with a focused delta review after any repairs.
7. AGENTS.md, ARCHITECTURE.md and this roadmap agree: R00 complete only after qualification and, at that closure, A05 next/unstarted. Later separately authorized work updates the active checkpoint rather than reopening this historical exit condition. The current task report has a compact checkpoint; old checkpoints do not remain active instructions.

R00 is one corrective task with these checkpoints. Do not freeze a new series of R00-era replay versions or create another long historical certification campaign.

<a id="architecture"></a>
# 3. Target architecture and capability boundaries

## 3.1 One resolved design; one active electrical truth

```text
Current device intent + supported envelope + exact seed
  -> functional roles and region relationships
  -> compatible current family/implementation choices
  -> explicit interfaces, source/reference domains and device buses
  -> immutable resolved values, model choices, packages and electrical plan
  -> cheap semantic/physical-demand checks
  -> private healthy CircuitJS validation
  -> physical realization, copper/access/correspondence validation
  -> canonical serviceable fault hypotheses and bounded diagnostic proof
  -> current complaint/presentation projection
  -> atomic installation of one player runtime
  -> ordinary measurement, physical mutation, repair and customer retest
```

Keep request, resolved immutable plan and mutable installed state distinct. A replay artifact records the current resolved design and interpretation; it is not a second live truth store. Providers declare local contributions and supported capabilities. Generic orchestration coordinates stages and owned publication; it does not learn individual resistor/transistor/family names.

## 3.2 Ownership map

| Concern | Owner and boundary |
|---|---|
| Functional intent and variant selection | Current device-intent resolver and provider registration; no selected-fault-based topology cheating. |
| Local parts/terminals/values/models | Explicit immutable provider contributions and resolved recipes. |
| Device buses and cross-block joins | Device-owned declarations and one constrained construction context. |
| Solver elements and mutable bindings | One active construction/runtime owner; no provider-owned nested simulator. |
| Physical packages/poses/copper | Physical realization providers and generic current materialization with exact correspondence. |
| Fault eligibility, observations and repair classes | Production hypothesis/diagnostic services with provider-local supported behavior. |
| Measurements and instrument stimulus | Shared CircuitJS observation/active-measurement boundary. |
| Physical repair, inventory and damage | Runtime and part/conductor lifecycle providers; one mutation owner. |
| Camera, loupe, rendering and accessibility | Shared view transformation and non-spoiling projections; no electrical authority. |
| Current save/resume and imports | Input/reconstruction adapters into native current services, not alternate runtimes. |
| Test execution and evidence | Independent consumers of current contracts, separate from player admission and physical truth. |

## 3.3 Three graphs, not three competing truths

The logical design graph describes intended components, terminals and buses. The physical conductor graph describes pads, copper edges, vias, barrels and current cuts/repairs. The live solver graph implements the active electrical state. A cut can split conductors belonging to one intended bus; a legitimate repair can reconnect them differently. Those representations need explicit mappings, not one collapsed identifier.

Stable semantic identity is not a union-find root, a solver node number, array position, pixel coordinate or old report string. It survives harmless current redraw/view operations and supported mutations. Replacing a topology creates distinct internal terminals rather than pretending incompatible pins are identical. Source/reference labels do not join circuits. Current physical realization fingerprints invalidate stale actions and caches; a genuinely changed realization may reject old development data.

## 3.4 Construction and pluggability

A provider has a compact declaration of its electrical units, part/package ownership, terminals, resolved values, fault/serviceability and supported physical behavior. One package may contain multiple modeled units with shared power/reset/clock terminals. One unit is not automatically one solver element or one replaceable part. A quad IC or resistor network is one physical package when modeled that way.

The restricted context owns allocations/bindings and records ownership before failing initialization can strand resources. Cross-owner joins go through explicit device contracts. Completion requires declared bindings; failure/abort revokes candidate receipts and releases only owned state. A generic materializer must not decide that a driver always has RG/RPD/Q1 or that a load always owns LED_NODE. Small existing devices prove the seam; A05 proves real alternate/repeated consumers and A11 measures extension cost.

Preserve short physical designators separately from long semantic identity. Original component values are visible only through legitimate markings/measurement, not an added original-value answer panel. Supply/runtime parameters, physical ratings and replacement semantics consume one resolved authority.

## 3.5 Solver, time and model limits

CircuitJS remains the working solver. Its singleton-sensitive context is not made independent by constructing two Java objects. Start with serialized owned proof execution and bounded simulation-time stepping. Private proofs cannot advance the player's circuit or publish stale state. Parallel geometry search is a separate decision; parallel live solvers require real isolation and their own evidence. GWT code is not presumed worker-safe merely because it is placed behind a worker wrapper.

Simulation time, rendering time, work counters and wall-clock cancellation are distinct. Host speed can change yielding or resource failure, not choose a different successful fault/design. Stateful IC/MCU/display models advance on accepted simulation steps or declared scheduled events, not browser frames or repeated nonlinear trial evaluations. Specify tie ordering, reset/startup, phase and finite feedback work.

Models declare supported I-V/loading, startup, dropout, storage, bandwidth, protection, fault behavior and observables. An averaged converter can be legitimate but cannot claim resolved switching waveforms or decorative individually probeable parts with no causal role. Numerical reference stabilization is not a physical ground or invisible component the player can repair. Qualified observations use declared physical predicates and numerical tolerances, not bit-identical trajectories across all browsers.

## 3.6 Future seams without speculative frameworks

Small architecture canaries must keep shared-package units, surface-only pads, stateful scheduling, explicit design origin and separate capability claims representable. The required 0805/SOT-23 fixtures and small scheduled-state checks remain. They do not qualify finished ICs, MCUs, SMD gameplay or imports. Add concrete serialization/model fields when a real current consumer requires them; do not retain an unused general future-state framework solely because a hypothetical old manifest encoded it.

Electrical support, physical realization, diagnostic observation, repair serviceability, dynamic state and import capability are separate claims. A recognized CircuitJS element is not automatically a replaceable PCB component or a fair fault candidate.

## 3.7 Twelve-step import-to-challenge contract

IMPORT-1 establishes the complete bounded passive/DC path; later stages expand supported elements. The importer is an authoring/input adapter into native current services.

1. Retain source bytes or an explicitly user-resolved source artifact, hash, current parser/interpretation identity and bounded size/work limits. Parse in isolated owned staging; no script execution, arbitrary embedded network fetch or current-board mutation.
2. Recover the supported schematic electrical connectivity before PCB placement. Persist explicit import-local component/unit/terminal/bus assignments; source coordinates and solver nodes are not durable repair IDs.
3. Account for every source element using the six dispositions in Section 5.7: directly supported PCB component; supported with package choice; supported external source/load/control; supported as an honest packaged module; electrically supported, not physically serviceable; unsupported. Unresolved conditions are explicit. No silent drops or silent model/package substitutions.
4. Resolve sources, controls, references and loads. Where ambiguous, the author identifies external versus on-board roles; an ideal source is not automatically a PCB package.
5. Declare healthy customer intent: allowed inputs/power/control sequences, outputs and meaningful expected behavior. Suggest only unambiguous facts; do not guess intent from net labels or current measurements.
6. Map supported packages, units, shared pins, polarity, values/ratings and physical roles without silently rewriting electrical topology to ease routing.
7. Verify actual healthy behavior over declared operating states through the shared CircuitJS execution boundary. Convergence alone is not function.
8. Enumerate only supported, causal and physically serviceable faults with stable current hypothesis identity and seeded selection.
9. Reject no-op symptoms, unmodeled numerical behavior, inaccessible observations, impossible repairs and indistinguishable non-equivalent causes. Do not relax the native diagnostic contract for imported input.
10. Use the native physicalization, layer/copper/access, renderer, probing, inventory, repair and retest services. Routing exhaustion is an explicit bounded rejection, not permission for hidden wires.
11. Record current source interpretation, accepted mappings, packages/models/values, healthy intent, physical realization, selected fault and relevant proof/state dependencies. A hash without source bytes is not a replay recipe; obsolete development interpretations may reject.
12. Publish an exact element/feature support matrix and per-file unsupported/needs-input reasons. Share source/manifest deliberately; do not leak hidden fault/answer information in ordinary player complaint, accessibility or share projections.

Changed input is a new interpretation unless an explicit current mapping proves continuity. This is not a historical file-migration service. Full arbitrary import, scripting or community ecosystem is not promised.

<a id="reference-boards"></a>
# 4. Functional taxonomy and reference boards

These are current product targets and comparison fixtures, not constraints to reproduce old generator outputs. Counts are purposeful physical packages, not simulated element totals or drawings. Component allocations remain design budgets until qualified by the owning content task.

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

Required structural variants include alternative BJT/MOSFET driver populations and at least two accepted sensor/reference arrangements. The 5 V rail must be produced by the modeled regulator, not an unrelated external ideal rail. Q30 qualifies 20-40 parts as normal procedural content, including partial-power and loading cases. It is not satisfied by merely instantiating RB15 twice with no interaction.

## 4.5 RB56: the 50-60-part appliance/control north star

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
| Physical routing fixture | Actual package geometry, net membership, routing policy and layer/crossover evidence exist. | P03-P09 include reduced and full-count structural probes before the full electronics library exists. |
| Solver fixture | Real or explicitly bounded model-backed circuit is analyzed and stepped; cost/fidelity recorded. | A07/E06 run small difficult pilots and 20/40/60/100 aggregate loads early. |
| Diagnostic fixture | Selected hypothesis population, legal observation policy and reachable repair/retest are proved. | A09/D01; qualification gates integrate exact content. |
| Playable qualification | Normal UI, correct markings, navigation, inputs, instruments, repair, save where advertised, and resource budgets pass. | Q15, Q30, Q60, Q100. |

A physical-only 100-part fixture is useful early evidence, but it is not an electrically generated or playable 100-part board. A solver-only 100-part fixture is not a PCB. Evidence labels must never collapse these distinctions.


---


## 4.8 Extended vocabulary and bench capabilities

| Group | Included direction | Qualification boundary |
|---|---|---|
| Passive and interconnect | Resistors/networks, capacitors, diodes/Zeners/TVS, fuses, connectors, testpoints, factory links and later copper repair. | Honest packages/pins, causal modeled effects and current serviceability. |
| Switching and output | BJT/NMOS/PNP/PMOS where supported, relay drivers, regulators, isolated converters, SCR/TRIAC, zero-cross, reversing/H-bridge and external loads. | Drive/source/reference/loading/energy limits and meaningful control sequences. |
| Analog/control ICs | Comparator/Schmitt, op-amp/buffer/amplifier, reference, active filter, driver array, analog switch/mux, current-sense and optocoupled interfaces. | Real power/shared pins, finite drive and declared operating envelope. |
| Logic and timing | Combinational logic, latches/flip-flops, counters, shift registers, 555-style timers, RC/crystal/resonator/oscillator alternatives. | Shared simulation time, reset/startup/clock loss, finite events and honest bandwidth. |
| MCU and displays | One bounded MCU approach; limited GPIO/ADC/PWM/timers; seven-segment static and qualified scanned displays/status outputs. | Causal pin behavior, package ownership, current state/resume and no private-answer shortcut. |
| Bench instruments | Reference-aware DMM, one-channel scope then two channels; conditional logic probe/capture, electrical signal injection, in-series current, ESR and capacitance. | Actual electrical connections, burden, range, reference, supported timing, lifecycle and diagnostic need. |
| Imports | Passive/DC, nonlinear/RC, selected IC/control, power/dynamic and broad supported community subset. | Complete twelve-step native pipeline with author intent and explicit unsupported results. |
| Advanced diagnosis | Intermittent events, causal secondary damage/thermal, customer returns, optional scoring and bounded multiple faults. | Real causal effects, reproducibility, observable hypotheses and reachable repair; expert difficulty calibration. |

Optional RB15/RB30/RB56/RB100 variants may incorporate IC, MCU, display or imported-design capabilities. They inherit only the capabilities actually consumed. They do not replace the base allocation with inert decorative parts or make every native reference board wait for all later lanes.

## 4.9 Grouped fault library

| Owner group | Supported future failure vocabulary | Guardrail |
|---|---|---|
| Resistors/networks | Open and supported value drift high/low. | Nominal markings are not a hidden fault readout; shared-package repair is real. |
| Capacitors | Open, short, reduced capacitance, leakage; ESR only with a qualified model. | Stored energy and electrical diagnostic response, never metadata-only measurement. |
| Diodes/Zeners/TVS | Open/short, leakage, changed clamp and reversed installation as modeled. | Polarity, load, reference and stress envelope are qualified. |
| BJT/MOSFET | Open/short paths, stuck conduction and supported drive damage. | Distinguish device defects from source/drive/reference faults. |
| IC/MCU | Dead package, supported stuck pins/drive degradation, clock/reset/state faults when meaningful. | External missing reset/clock/supply is not automatically an internal package defect; no firmware decompilation. |
| Relay/load control | Coil open, stuck contacts, high contact resistance and driver faults. | Separate coil/contact/driver physical and electrical ownership. |
| Connector/copper | Open terminal, high-resistance connection, broken trace; later via/conductor faults. | Precise physical locus and a real repair operation; E08 only when consumed. |
| Power path | Fuse/regulator/rectifier/startup/feedback faults and partial rail collapse. | Every visible component matters to the modeled function. |
| Sensors/inputs | Open, short, biased/stuck response and reference/supply failure. | Relevant stimulus is player-operable and separates plausible causes. |
| Composite/time | Intermittent behavior, secondary damage and bounded multiple faults. | No unexplained randomness; qualify joint effects rather than infer them from isolated passes. |

A valid equivalent-repair class can avoid demanding identification of an inaccessible internal semiconductor. It cannot be used to hide non-equivalent causes that need different repairs.

## 4.10 Additional capability-gap dispositions

These are the Edition 2.1 gap categories carried into the current-only plan. "Foundational" means a necessary current seam or small canary, not permission to implement the entire future feature now.

| Gap or capability | Disposition | Owner and limit |
|---|---|---|
| Shared-package units, common IC supplies and resistor-network identity | Foundational now | A04/R00/A11/P01 keep explicit package/unit/pin correspondence and honest counts; actual new IC runtime support belongs to its later consumer. |
| Provider state, clock phase and deterministic event ordering | Foundational seam now | A03/A07/A08/U06 support current identity and finite state/events; small scheduled-state checks, not a firmware framework. |
| Surface-only pads and face-specific mounting | Required architecture canaries now | P01/P02/U01 exercise 0805/SOT-23, optional SOIC, without enabling BGA/reflow or a full SMD catalog. |
| Untrusted import names/models/resources and input size | Foundational trust boundary; implementation later | A03/A10/U04 allow explicit provenance and safe presentation; IMPORT-1 implements isolated bounded ingestion, not arbitrary script/network access. |
| Watchdog and reset/brownout supervisors | Planned later | E12/MCU-2 for a selected causal control model, with deterministic reset behavior. |
| EEPROM-like configuration/calibration or nonvolatile state | Conditional | MCU-2/U06 only when current content needs retention; define power-loss/update semantics, not a general memory/firmware emulator. |
| Calibration and trimmer service actions | Planned later | E04/E09/U04 expose actual electrical adjustment when diagnosis/retest needs it; never a hidden correct-repair button. |
| Harness pin swaps, high-resistance contacts and cracked-solder-style opens | Conditional | A09 with appropriate E01/E08 or part/connector mutation provider; precise physical locus, causal effect and real repair. |
| Battery-backed rails and supercapacitor/backup state | Conditional | E01/A06/U06 when a family consumes independent/residual power; no assumption that global OFF discharges everything. |
| Fan tachometer, load feedback and bounded plant coupling | Conditional | E04/E14 when external electrical response matters; no full motor/mechanical/fluid simulator or off-board package-count inflation. |
| UART/I2C/SPI and held/stuck bus lines | Conditional | Later E10/E12/MCU/U10 only with finite electrical loading/pulls/timing and available observations; no protocol ecosystem prerequisite. |
| Probe clips, common grounds, burden/fuses and coated-copper access | Foundational truth; richer gestures conditional | P02/U02 set access/reference rules; U09/U12 qualify consumed scope/current behavior. Cosmetic clips or scraping are not universal blockers. |
| Keypads, encoders and scanned input matrices | Conditional | E04/E10/MCU-2 when a current control family needs them; real scanning/input, not a scenario shortcut. |
| Graphical LCD/OLED, arbitrary firmware/IDE/HDL/protocol stacks, RF/EMC, phone/BGA density and manufacturing reflow/DRC | Outside required core | Separate evidence-backed product authorization; basic MCU/display/import support does not imply these projects. |

<a id="decisions"></a>
# 5. Architecture decisions and alternatives

The comparison dimensions below remain planning decisions. They do not claim measured success rates or require preserving old implementations in the shipped runtime. A small independent reference algorithm may remain test-only when it gives a useful current oracle.

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
| Usefulness for heterogeneous 50-60 parts | 2 | 3 | 5 | 5 |
| Usefulness for constrained 100 parts | 1 | 3 | 4 | 5 |

**Recommended path:** use one corrected current one-layer implementation while introducing an explicit two-layer-capable conductor/surface model; implement correct one-layer routing and sparse crossover prototypes; compare restricted and fuller two-layer prototypes; freeze a hybrid policy. Routine small content should prefer readable one-layer boards. Advanced content should normally allow two copper layers with a cost for vias and unnecessary transitions. Full two-layer search should be enabled where it earns better success/quality/cost results than the restricted policy.

A hybrid policy selects a supported fabrication/inspection style from the versioned content envelope. Difficulty may restrict permitted layout styles or assistance, but may not hide the board's only required observable surface or make a fault impossible to reach. A user must be able to inspect either physical face. An optional translucent opposite-side overlay is a view aid, not a new conductor or a secretly probeable front-side object.

Two layers do not double the number of components that can fit. Package area and human access still constrain placement. At a fixed grid, adding a second layer approximately doubles layer-indexed search states and introduces transition edges; fewer detours or retries might offset that cost. That is a
## 5.2 Functional block architecture comparison

| Concern | Current bounded path | Proposed mature boundary |
|---|---|---|
| Meaning of a block | Known contribution shapes plus device-specific construction. | A named electrical purpose with qualified implementation variants. |
| Repeated instances | Explicit driver/load/source names in a bounded plan. | Stable device-assigned role instance keys; local IDs remain unchanged under insertion/reordering. |
| Values | The delivered current controlled load has an immutable bounded resolved recipe. | One immutable resolved recipe, consumed by construction, physical specs and catalog expectations. |
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
| Current sequential router | Small current one-layer routes with fixed grid/escapes. | Retain only while it is useful current functionality or a test-only oracle; replace obsolete runtime revisions. | Known infeasible cases reject within bounds; current corrected outputs are independently validated, not matched to old coordinates. |
| Canonical order and constrained-net priority | Avoid easy routes consuming scarce escapes/channels first. | Foundational. Net role comes from metadata, not a name containing GND. | Reordered declaration input produces the same result; constrained escape fixture improves without hidden shorts. |
| Multi-terminal trees/trunks | Excess star length and duplicated high-fan-out branches. | Likely needed; bounded tree/MST-like heuristics before advanced optimization. | Every pad connects; overlapping same-net branches are counted once in actual copper metrics. |
| Escape/channel planning | Package exits and region bottlenecks. | Foundational for larger heterogeneous boards. | A plan with adequate area but impossible interface capacity rejects before detailed routing. |
| Bounded rip-up/reroute | Earlier legal routes blocking later nets. | Planned capability with fixed recovery budget. | Deterministic exhaustion and no publication of an intermediate overlapping candidate. |
| Negotiated congestion | Persistent congestion not solved economically by local recovery. | Conditional measured extension within P05; not automatically required. | Congested cases improve under a frozen budget; hard isolation/clearance rules remain hard in accepted output. |
| Sparse factory crossovers | A small number of geometrically awkward one-layer crossings. | Explicit supported prototype and policy, not an unlimited fallback. | Copper really passes beneath the raised link without accidental contact; excessive link demand rejects or selects an allowed two-layer strategy. |
| Restricted two-layer routing | Cross-region crossings and rail/signal separation with limited transitions. | Preferred advanced path after P07 comparison. | Cross-layer overlap is not an electrical join without a plated pad/via; every real transition is present. |
| Fuller two-layer routing | Remaining advanced cases needing both layers more freely. | Conditional enablement behind the same physical contracts. | Better whole-pipeline results, not just lower wirelength at unacceptable via count or probe complexity. |
| Arbitrary multilayer/planes/optimal Steiner solver | Manufacturing/high-density goals beyond the stated target. | Do not build now. | Reconsider only through a new owner-approved product requirement. |

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


## 5.6 Bounded MCU approach

| Alternative | Useful benefit | Main cost/risk | Required comparison |
|---|---|---|---|
| Deterministic behavioral provider | Small causal appliance-control model integrated with existing solver time. | Accidentally ignores real pins/loading or becomes a scenario oracle. | Same VCC/reset/GPIO/ADC/PWM fixture and diagnostic observables. |
| Restricted state machine/control script | Explicit state/transitions and bounded event vocabulary. | New execution language/security scope or insufficient electrical coupling. | Finite work, pin-driven transitions, reset/startup and no arbitrary code. |
| One tiny-architecture emulator | More authentic instruction/program timing when it materially matters. | CPU/peripheral/library complexity unrelated to player diagnosis. | Evidence that required observations justify additional cost. |
| Narrow hybrid | Combines electrical I/O with bounded internal control. | Duplicate state/time owners and unclear fidelity. | One authoritative event/state contract and matched observables. |

MCU-1 records an evidence-based choice; MCU-2 implements only that choice. No hidden firmware inspection is required to solve the game.

## 5.7 Import capability negotiation

Each source element receives one primary disposition and any unresolved conditions. These are current capability distinctions, not historical file-support promises.

| Element disposition | Meaning and required handling |
|---|---|
| Directly supported PCB component | A current element/model, package, pin map and physical role exist. Map through native providers with provenance; fault serviceability and diagnosis still require separate qualification. |
| Supported with package choice | Electrical interpretation works but multiple physical mappings are possible. Require an explicit compatible package or unit-grouping choice, never a guessed pin map. |
| Supported external source/load/control | The element is a bench/customer participant rather than a PCB package. Map its real source/load/connector/stimulus role, operating states and references. |
| Supported as an honest packaged module | The supported model is an opaque terminal-level function. Expose/count one real package without fictitious serviceable internal parts. |
| Electrically supported, not physically serviceable | The solver model exists but physical observation/repair is not qualified. Explain the limit; allow healthy fixed support only when the selected challenge permits it. It is not automatically a fault owner, and the challenge rejects if its presence defeats required physicalization or function. |
| Unsupported | Parsing, model, physical mapping or required behavior is outside the current subset. Name the exact element/contract/stage; retain the source without silently dropping, substituting or calling it supported. |

Aggregate results distinguish NEEDS_AUTHOR_INPUT, UNSUPPORTED_CAPABILITY, HEALTHY_VERIFICATION_FAILED, PHYSICALIZATION_REJECTED, DIAGNOSTIC_REJECTED, CANCELLED and QUALIFIED. These meanings stay distinct; implementation may choose serialized names without historical compatibility obligations. Parsing and convergence are partial evidence, not playable qualification. The twelve-step contract in Section 3 remains required for admitted imports.

<a id="validation"></a>
# 6. Scale, physics, interaction and validation

## 6.1 Scale bands and workload design

The following five bands use **ESTIMATED mixed-package planning ranges**. Counts depend heavily on connectors, IC pin counts, package mix, net fan-out and layout style. Segment estimates refer to meaningful canonical route segments, not every ten-unit grid vertex. They are workload design aids, never pass thresholds or forecasts.

| Physical parts | Estimated pads | Estimated logical nets | Estimated canonical route segments | Primary fan-out/placement concern | Validation/solver/diagnostic concern | Required architecture |
|---:|---:|---:|---:|---|---|---|
| 10 | 20-40 | 8-18 | 25-80 | A connector or a shared return can dominate a small cluster. | Fixed overhead and false metrics can dominate; physical count says little about nonlinear steps. | Current comparison baseline; corrected metrics; exact IDs and recipes. |
| 20 | 45-85 | 15-35 | 60-180 | Package area, escapes and first multi-region joins. | Fixed generic outline already fails some mixtures; repeated proof overhead grows with H. | Dynamic sizing, poses, first hierarchy, production proof boundary. |
| 40 | 95-180 | 30-65 | 140-400 | Multiple connectors, buses, mixed multi-pin packages and rails. | Segment/pad candidate-pair work and loading interactions become material suspects. | Trunks, bounded recovery, indexed candidates, coherent viewport, Q30 envelope. |
| 60 | 145-270 | 45-100 | 240-700 | Isolation region, conversion, repeated drivers and shared support. | Nonlinear/time-scale behavior and candidate separation can cost more than placement. | Layer policy, credible converters, AC/scope semantics, proof receipts and Q60. |
| 100 | 250-460 | 75-160 | 450-1,300 | Global channel demand, interface cuts, high-fan-out rails and dense local regions. | Long sessions, proof population, waveform storage and raw route complexity need separate bounds. | Hierarchical refinement, qualified two-layer/link policy, bounded scheduling, Q100. |

The second table makes the per-band algorithm concerns explicit. It is **PLANNING ESTIMATES and algorithmic risk hypotheses**, not a runtime forecast. Let C be components, P pads, S canonical conductive segments, H admitted fault hypotheses, O diagnostic observations and G routing-grid states. None of H, S or the reduced solver matrix size is determined by C alone.

| Physical scale | Placement / route demand | Physical validation | Solver concern | Diagnostic concern | Rendering / targeting concern |
|---|---|---|---|---|---|
| ~10 | Flat placement and sequential routing are useful controls; compare actual connector/escape constraints. | Small all-pairs oracle is affordable as a reference, not assumed production timing. | A small nonlinear model can still dominate; record reduced matrix and subiterations. | Verify all declared small H and O directly. | Confirm exact pad/lead correspondence before optimization. |
| ~20 | Multiple anchors and repeated blocks make region interfaces relevant; fixed-area failures depend on package mix. | Record P/S growth and repeated validation passes, not just C. | Source/protection/relay models add internal state not counted as packages. | Full candidate installs become a visible cost; split proof stages. | Fit-only projection may shrink targets; navigation becomes a supported feature. |
| ~40 | Coarse regions plus local refinement and multi-terminal trees become the preferred baseline. | Canonical segments and broad-phase candidate checks must agree with the oracle. | Nonlinear state/load cases, restamping and active-meter analyses need independent budgets. | H(H−1)/2 pair comparisons may be significant, but repeated solves often cost more. | Cache static projections with exact invalidation; qualify actual visible target density. |
| ~60 | Larger heterogeneous packages, domain barriers and inter-region traffic require qualified layer/channel policy. | Per-layer contact, barrels, cuts and stale-cache negatives matter; worst-case contact checks remain quadratic. | Offline conversion and mixed time scales may dominate a much larger but linear board. | Local certificates require global loading/reference context; adaptive partitions and receipts must preserve coverage. | Both board faces, powered state, instruments and lifted/loose parts must remain usable together. |
| ~100 | Bounded hierarchical search, congestion recovery and a constrained package/net envelope are required. | Large raw grid walks cannot become the durable trace representation; test dense adversarial cases as well as normal boards. | Aggregate matrix cost and time windows are measured separately from component count; isolated backend only if qualified. | Cold proof cost, memory, cancellation and the declared hypothesis population are all release dimensions. | Test full-count navigation and long-session inventories; no hidden-hitbox or invisible-layer shortcut. |

For the current style of dense matrix implementation, factorization cost depends on reduced matrix dimension, and nonlinear iterations may require repeated factorization. Linear matrices can reuse a factorization while their coefficients remain unchanged. The prior code review identified these paths, but it did not measure their limits. A07 therefore records matrix dimension and actual factorization/restamp counts rather than converting “100 physical parts” into an invented solver runtime.

The raw grid representation may contain many more segments. With S segments, an all-pairs pass examines S(S−1)/2 unordered pairs: 1,000 gives 499,500; 5,000 gives 12,497,500. A spatial broad phase reduces likely comparisons but does not guarantee subquadratic worst-case behavior for arbitrarily overlapping data. Dense adversarial fixtures remain necessary.

The existing generic router has a 720×400 working outline, grid step 10, at most 80 attempts and a target of five viable candidates. These are **CODE** facts, not future design limits. The earlier packing result, one connector plus seventeen shortest-span resistors exceeding its available inflated-courtyard area, remains a specific necessary-area counterexample. It is not a universal eighteen-part limit.

For a rectangular routing grid, direction-aware state count scales approximately with grid width × grid height × allowed layers × direction states. Growing both physical dimensions and refining the grid can be much more expensive than adding another board layer. P03/P04 therefore compare outline/grid/channel choices jointly, instead of fixing a poor grid and repeatedly inflating the board.

## 6.2 Supported envelope, not a magic component limit

Every qualified generator/profile carries a versioned envelope covering: physical package count and mix; pin count; net count and maximum fan-out; number of source/reference domains; region count and interface demand; allowed aspect ratios and area; allowed rotations; copper layers; trace/clearance classes; link/via budgets; route representation size; candidate hypotheses; nonlinear/model classes; simulation windows; live and loose inventory; and minimum usable surface separation.

Area is selected from actual package/keepout demand plus a measured routing allowance and bounded shape alternatives. Numerical coordinate units are explicit. Legacy canvas units must not be silently relabeled millimeters or used to claim manufacturing clearance. Scaling a physical envelope and zooming the camera are different operations.

The contract rejects requests it does not support. Rejection categories include declaration error, unsupported model, incompatible domain, physical infeasibility lower bound, placement exhaustion, route exhaustion, physical validation failure, numerical nonconvergence, diagnostic ambiguity, repair-unreachable, stale/cancelled work, and infrastructure failure. Unknown exceptions are defects. They must not be swallowed as ordinary failed layout attempts for another 79 retries.

## 6.3 Copper, packages, and two-layer interaction

P01/P02 establish board-space integer/fixed-grid geometry, checked coordinate arithmetic, immutable snapshots, explicit package pose, and a conductor graph. A pose combines permitted rotation, board side and translation while retaining terminal IDs. Mirroring a viewed board does not exchange a transistor's gate and drain or rename a relay's contacts.

The conductor model distinguishes: top copper, bottom copper, plated through-hole pad surfaces and barrels, non-plated holes, through vias, trace junctions, exposed/tented access, and elevated factory links. This is a bounded two-layer/2.5D model, not a volumetric CAD kernel. Package body/assembly courtyards, layer-specific copper keepouts and actual probe accessibility are separate rules. A body on the component face does not automatically block all backside copper; a permitted backside trace does not magically become probeable through the body from the front.

Cross-layer traces at the same x/y do not join merely because their projections cross. Same-layer copper contact is checked using the accepted width/contact semantics. A valid plated barrel joins its declared layer surfaces; an NPTH does not. The renderer consumes this result and cannot invent a backside bridge. Independent validation reconstructs contacts from geometry/layer declarations and compares them with intended connectivity.

Factory links have raised-conductor and underpass rules. Their cost includes count, density, route quality, board area and readability. P09 freezes numerical limits after comparison; this document does not invent a universal number of acceptable jumpers. The policy must prefer an allowed alternative layer strategy rather than quietly produce dozens of crossovers. Factory-link changes that alter the electrical/physical realization invalidate the affected proof receipt and rerun relevant healthy/diagnostic checks.

Trace cutting acts on a declared physical locus. A cut severs the correct conductor edge and may or may not disconnect a bus if another actual path exists. Wrong cuts remain possible. Plated barrels, vias and inaccessible surfaces cannot be cut through a front-side pixel gesture unless a real supported operation exists. E08 proves jumper, cut and repair semantics together before they become a major challenge family.

## 6.4 Mains, power domains, and educational model fidelity

The new target includes simulated 120 VAC, rectified high-voltage storage, isolated and non-isolated supplies, 24/12/5/3.3 V rails, partial power, backfeed, current limits, protection and instrument references. These are explicit modeling requirements, not evidence that current metadata or existing low-voltage controllers already implement them.

Numerical reference stabilization and gauge handling must not become a physical earth conductor, a hidden power return, a fictitious meter result, or an apparent failure of isolation. A06/A07 test insertion/reordering, independent floating supplies, transformer/relay isolation, high-impedance measurement and instrument loading. Merely deleting the stabilizer is not a valid fix; singularity and convergence must remain well-defined.

| Function | First modeling approach | Required behavior | Explicit limit |
|---|---|---|---|
| AC source and entry | CircuitJS AC source with bounded source impedance and actual isolation switches. | RMS/peak conventions, polarity, source state, partial power and load effects. | No utility-grid or safety certification claim. |
| Bridge and bulk storage | Actual diodes/bridge-equivalent connections and capacitor model. | Rectification, ripple within supported sampling, charging and discharge, open/short effects. | No claimed surge/EMC fidelity without a qualified model. |
| Transformer/isolation | Existing coupled-winding backend qualified with realistic bounded parameters. | Separate winding references, energy transfer, load dependence and accessible terminal measurements. | No magnetic-core design/saturation claim from the simple linear coupled-inductor model. |
| Offline converter | Compare switching-detail pilot with a causally stamped averaged/behavioral model. | Input draw, available power, enable, startup/dropout, load regulation, energy storage and admitted faults. | No fabricated switching waveform or inert external parts. |
| Regulator/buck/LDO | Qualified rail-producing provider, not a separate ideal source. | Headroom, line/load behavior, enable, overload behavior and relevant storage. | Only the documented operating envelope and failure vocabulary. |
| Relay/driver | Qualify current relay model and explicitly modeled driver/clamp. | Pickup/dropout, coil/contact separation, contact leakage/resistance and fault semantics. | The existing relay approximation and reported current behavior need testing; do not assume ideal open contacts. |
| Fuse/clamp/protection | Rating/state model tied to solved current/voltage and simulated duration. | Healthy survival, bounded overload response and actual graph changes. | No arbitrary damage roll; no compliance or fire-risk certification. |
| Sensor/interlock | Solver-backed input/conditioning with ordinary player stimulus. | Reference, loading, threshold and partial-power behavior. | No hidden-only switch that the player cannot reproduce. |

“Board power off” means the selected real sources are isolated. It does not mean all capacitors are discharged, that an external load cannot backfeed, or that every reference is at earth potential. Active meters require both the necessary source isolation and the relevant stored-energy/readiness contract. Start conservatively with all relevant board sources isolated; relax to domain-local permissions only with a proved reachable-energy boundary. Do not derive permission solely from a UI toggle.

A conventional earth-referenced scope lead and an isolated differential measurement are different modeled connections. The player-facing instrument must clearly state which it is. A reference lead that creates a real connection must affect the graph; an unsupported hazardous/common-mode measurement must fail explicitly rather than report a plausible but false number. These are simulated training contracts, not instructions for live mains work.

## 6.5 Large-board viewport and accessibility

One `BoardViewTransform` or equivalent owns board-to-screen and inverse mapping, device-pixel ratio, pan, zoom, active face, and region focus. Pads, leads, selection, traces, hit tests and probe markers consume the same transformed geometry. The parts tray and instrument controls are screen-space workbench chrome and must not force the entire PCB to shrink.

Require fit-board, zoom-to-selection, ordinary pan, and a way to recover orientation after focusing a region. A minimap is conditional on measured navigation difficulty. Face flipping must keep semantic targets stable and invalidate only genuinely inaccessible/stale targets. Hidden backside geometry cannot silently capture a front-side click. If targets overlap on screen, provide explicit visible disambiguation or require zoom rather than enlarging invisible hit areas.

Static copper/package projections may be cached by immutable realization and view version. Dynamic part state, lifted leads, damage, selection and solver intensity have separate invalidation. Level-of-detail may simplify shading and text; it may not erase necessary polarity, terminals, fault-relevant markings, current probe locations or the only understandable route across a barrier. Accessibility exposes the same physical/public information, not hidden original values or fault IDs.

## 6.6 Diagnostic proof at scale

A diagnostic hypothesis has a stable identity containing physical owner, fault mode and relevant parameterization. Type-only or owner-only deduplication is insufficient when the same part can fail in several ways. Candidate counting, selection, serviceability and proof enumeration use one eligibility result. Rejected hypotheses retain a reason; they are not silently forgotten.

Plans name public-semantic actions, targets and input states. They may be adaptive: an observation partitions the hypothesis set and determines which *legal diagnostic action* is useful next. An internal proof may test that policy, but the normal UI must not turn it into an answer-revealing autopilot. A technician remains free to choose another valid route.

Admission has four layers: static structure/serviceability; provider-level behavioral conformance; whole-device numerical separation under declared operating conditions; and reachable physical repair with customer retest. Parallel paths, shared rails and alternate implementations can invalidate a local proof, so a block certificate is not a whole-board admission shortcut.

Receipts are keyed by the complete relevant immutable input context: implementation/model/recipe versions, topology and loads, source/reference states, fault population, observation/repair policy, simulation settings, and physical/access evidence. A physical layout change can preserve a purely electrical receipt while invalidating accessibility evidence, but that distinction must be explicit. A new model or topology invalidates the relevant electrical proof. Wall-clock timestamps are provenance, not identity.

Cache signature partitions and replay stable observations where justified. Do not share live mutable graphs merely because their descriptors match. If a numerical action depends on temporal state, the receipt includes its initial-state and simulation-time contract. Reusing an endpoint after board replacement is never valid. Proof interruption yields CANCELLED/STALE, not a partial PASS.


## 6.7 Performance contract and reference host

Use the delivered A01 methodology and actual current host/corpus receipts, not a newly invented benchmark. The exact modest reference host and its CPU/RAM/graphics, OS/browser, resolution/device-pixel ratio, power/background conditions, build and cold/warm protocol must be documented for qualification. The owner's fast desktop is not automatically the modest-machine baseline.

The product direction rejects routine multi-minute admission as acceptable gameplay. A04's documented 300-second Task41 developer-route window is not a player latency budget. The original planning goals of approximately 100 ms p95 ordinary input response and at least 30 fps prescribed navigation are **UX targets**, not measurements established by this review. Rerate generation, cancellation, memory, stepping and rendering budgets from actual pilots before the corresponding qualification; do not silently raise a failing threshold.

Report p50/p95/worst time, failures and rejection stages over a predeclared corpus. Count unsuccessful attempts and cold starts. Keep deterministic work caps separate from wall-clock cancellation; a race or slower host must not pick a different successful candidate. Parallel candidates, if introduced, publish in canonical order rather than first-finisher order.

| Stage | Work/correctness counters | Cost evidence |
|---|---|---|
| Resolution | Eligible choices, static rejects, value candidates. | Time and allocations. |
| Placement/routing | Outlines/poses, candidates, states/queue, net degree, reroute passes, vias/links. | Cold/warm time, peak data, failed-attempt cost. |
| Physical validation | Raw/canonical segments, candidate pairs, exact contacts, connectivity operations. | Reference/current equivalence and measured hotspots. |
| Solver | Reduced matrix, nonlinear subiterations, restamps, accepted simulation steps. | Simulated/wall time, convergence failures and memory. |
| Diagnostic proof | Hypotheses, observations, partitions, solves, repair/retest and context-valid cache hits. | Admission/cancellation time and complete coverage. |
| View/input | Visible primitives, transforms, targeting and redraw invalidation. | Frame cost and input latency. |
| Long session | Acquired/loose parts, active/inactive elements, history and sample retention. | Retained-object growth, owner release and latency slope. |

## 6.8 Current test architecture

Use four layers: cheap pure descriptor/identity/geometry/provider checks; focused actual CircuitJS electrical and lifecycle checks; a small current ordinary-player integration set; and larger held-out scale/reliability qualification only at the appropriate gates. Independent handwritten or simple reference oracles must not simply call the same production helper being tested.

Retain exact current integer/seed/identity encoding checks where required; retain actual JVM/GWT parity where consumed; evaluate electrical observations with declared physical expectations and tolerances. Historical report equality, previous-build pixels and incidental solver nodes are not authoritative. Test-only device fixtures can be modernized without preserving old generator descriptors.

Important negatives include malformed/partial input, mismatched plan/spec/receipt, cross-owner binding, wrong terminal/net, aliased immutable geometry, illegal via/layer contact, source contention, stale callback/publication, active meter contamination, unsupported/empty hypothesis sets, unavailable controls, numerical failure and wrong repair accepted. A forced failure must reach a terminal failure rather than hang or emit a passing receipt.

Separate application failure, unsupported/resource exhaustion and verification-infrastructure failure. A skipped, blocked, timed-out or absent test is not a pass. A visible playthrough may establish a player behavior gate but does not certify a broken CDP wrapper. Manual/automated evidence may be chosen to match the current gate before running it; do not change the gate after failure merely to obtain a green result.

Use one inexpensive preview/transport preflight when needed, not repeated full builds while the host cannot launch. Reuse unchanged infrastructure certification with an explicit dependency check. Full Gate B recertification is required only when the changed tool/resource boundary actually consumes it; it is not an automatic consequence of new gameplay source or report formatting. Resource cleanup still uses exact owned identities and no broad process killing.

## 6.9 Rolling playable integration

| Boundary | Required current path | Not sufficient |
|---|---|---|
| R00 | Corrected current construction/identity, ordinary diagnosis, wrong and valid repair, retest, reset/power and affected owner cleanup. | Only deleting tests, a build-only pass or a leaf path that bypasses the changed composed seam. |
| A05 | New provider/variant generation, normal-player launch, installation, diagnosis, physical repair and retest. | Metadata-only variant, hidden private repair or old leaf-only playthrough. |
| A09 and A10 | Production hypothesis proof; then bounded generation/publication; ordinary repair/retest. | Developer orchestration supplying a hidden action or a stale receipt. |
| P04 | Current PCB placement/routing, independent correspondence, visible probing and current repair. | A routed picture with no electrical backing. |
| U01 | Overview, pan/permanent zoom, Space loupe lifecycle, supported flip/layer access, probing and repair/retest. | Giant hitboxes, debug-only coordinates or camera changes that alter identity. |

Keep these canaries small and relevant. They do not reopen the historical Task43 campaign or replace Q15/Q30/Q60/Q100. Changed seams require current proof; unaffected proof can be reused with exact dependency reasoning. Stop dependent expansion when the current route is broken, without blocking unrelated optional investigations.

## 6.10 Long-session and corpus policy

Use development and frozen holdout corpora drawn from the advertised current envelope. Record success/failure and quality distributions rather than cherry-picking attractive seeds. An initial long-session planning workload includes at least one hundred owner/repair cycles and a separate accumulated-inventory case, adjusted by measured growth. These are test targets, not proven current performance or arbitrary product limits.

Legitimately retained loose parts and in-game history are not memory leaks. Replaced owners should release their handlers, temporary sources, solver graphs and stale callbacks. Bound waveform/history storage explicitly where appropriate. Current saves/resumes, source changes and repeated repair interact with that lifecycle and need combined checks.

## 6.11 Stateful devices and instruments

Stateful devices declare supplies, reset/clock priority, input thresholds/loading, initialization and supported bandwidth. Unknown or unsupported timing is not silently converted to a convenient logical zero. External clock networks must causally drive the device when shown; otherwise the package declares an internal clock. Display brightness integrates actual solved segment currents over bounded simulation-time history; the renderer cannot read a desired numeral or advance state.

Every enabled instrument declares electrical connection/stimulus, burden/loading, range/overrange, reference, timing window, unavailable states and cancellation/cleanup. Two scope channels share a ground only when the instrument model says so. Differential math does not create galvanic isolation. Current measurement needs an actual series path; ESR/capacitance need actual supported stimulus-response behavior. Private model fields are not measurements.

<a id="milestones"></a>
# 7. Milestone catalog and execution order

## 7.1 Status and authority

T49, N00, A01-A09, R00 and P01/P02 are accepted baseline entries. Their implementations may be replaced; their historic passing outputs are not acceptance conditions. The current checkpoint and individual cards record their evidence and limits. A10 then A11 are the current authorized sequence. Qualification prefers the built-in Browser for current compiled and player checks; the unchanged isolated CLI launcher's ownership deadline remains a separately reported infrastructure limitation.

A task-specific prompt authorizes a bounded scope. Follow current AGENTS.md for orchestration, ownership, review, publication and safety; R00 replaced its contradictory blanket compatibility wording under the owner's explicit instruction. A current correct requirement outranks an obsolete test. Do not start writers before relevant shared-interface investigations are reconciled.

The hard prerequisites on the cards are authoritative. The phase map is a readable sequence, not a second conflicting dependency system. Conditional capabilities are resolved per Section 8. All future foundation expansion descends from R00; this prevents another active writer from extending the old contract while it is being retired.

## 7.2 Preferred work order

| Lane | Order and boundary |
|---|---|
| Current authorized sequence | Close accepted P02 publication, then A10 and A11. |
| Early foundations | A05, A06/A07 and P01/P02, after their actual prerequisites; shared code has one owner. |
| Working small-board platform | A08/A09/A11, U01, P03/P04, E01/E03, A10, Q15, U04/U05 and REL-A when its scope is ready. |
| Normal boards | P05-P09, U02/U03, E02/E04, D01, Q30, U06/U07. |
| Advanced boards | Early E06 feasibility through A07; full E05/E06, Q60 and HARD calibration, REL-B. E08 only for consuming trace-repair content. |
| Mature target | Q100, separately selected advanced X work, X05 expert calibration, X09 and REL-1. |
| Later vocabulary | E09-E14, U09-U12, MCU-1/2 and IMPORT-1..5 at their actual dependencies and chosen content priority, not as blanket native-release blockers. |

A01/A07 investigate 60/100-part structural/solver costs long before final playable qualification. Q30 and physical-layer investigation do not wait for an alpha announcement when their prerequisites are available. Later optional lanes may be prioritized explicitly without claiming that all are required for the base game.

## 7.3 Milestone cards

The shared current-only policy and validation rules apply to every card. There is no per-card historical replay preservation clause. Additional provider/model state is qualified only when a feature consumes it.

<a id="m-t49"></a>

### T49 · Intent-driven value synthesis baseline

**Status:** DELIVERED BASELINE.

**Hard prerequisites:** None in the current catalog; delivered pre-roadmap baseline.

**Delivered basis:** `3de4da1d3bad3ed532e6c327b24195bc3138ed15`. One immutable, bounded resolved-value recipe for the controlled-indicator load. Electrical values, markings, ratings and replacement semantics consume the same recipe.

**Current disposition:** Fixed historical generator revisions are not supported merely because this task introduced them.

**Evidence boundary:** Recorded implementation/evidence, not a fresh runtime certification by this roadmap. Changes are permitted when they improve the current game; revalidate affected current behavior.

**Direct hard dependents:** [N00](#m-n00).

<a id="m-n00"></a>

### N00 · Prior roadmap adoption

**Status:** DELIVERED BASELINE.

**Hard prerequisites:** [T49](#m-t49)

**Delivered basis:** `55cac1d`. The previous roadmap was adopted. This is historical document bookkeeping, not a compatibility engine.

**Current disposition:** No second lineage-preservation ceremony or mandatory byte-preserved completed ledger.

**Evidence boundary:** Recorded implementation/evidence, not a fresh runtime certification by this roadmap. Changes are permitted when they improve the current game; revalidate affected current behavior.

**Direct hard dependents:** [A01](#m-a01).

<a id="m-a01"></a>

### A01 · Reference-board manifests and reproducible measurement harness

**Status:** DELIVERED BASELINE.

**Hard prerequisites:** [N00](#m-n00)

**Delivered basis:** `1eff2278ccd53181951c19d2965f0b32d9f0a126`. Reference manifests, workload counters and bounded solver/structural pilots. Retain useful measurement methodology and distinguish synthetic fixtures from playable boards.

**Current disposition:** Intentionally changed fixtures may be rebaselined; old manifests and timings are not immutable product outputs.

**Evidence boundary:** Recorded implementation/evidence, not a fresh runtime certification by this roadmap. Changes are permitted when they improve the current game; revalidate affected current behavior.

**Direct hard dependents:** [A02](#m-a02), [A07](#m-a07), [P08](#m-p08).

<a id="m-a02"></a>

### A02 · Candidate, bend-count and placement correctness baseline

**Status:** DELIVERED BASELINE.

**Hard prerequisites:** [A01](#m-a01)

**Delivered basis:** `363244c0483ec665feff3cb8f40fab09851c7b98`. Consistent candidate eligibility/hypothesis accounting, direction-based bend counts, and corrected connected-placement arithmetic.

**Current disposition:** R00 removes obsolete arithmetic/scoring and historical snapshot branches rather than reverting these fixes.

**Evidence boundary:** Recorded implementation/evidence, not a fresh runtime certification by this roadmap. Changes are permitted when they improve the current game; revalidate affected current behavior.

**Direct hard dependents:** [A03](#m-a03), [A09](#m-a09), [P01](#m-p01).

<a id="m-a03"></a>

### A03 · Stable design identities and current resolved-design manifests

**Status:** DELIVERED BASELINE.

**Hard prerequisites:** [A02](#m-a02)

**Delivered basis:** `1d995d4f5b21142d4ca5643ede546afdfe51340f`. Explicit role/instance and device-bus identities plus bounded resolved-design manifests, separate from transient solver nodes.

**Current disposition:** R00 consolidates current identity consumers and retires historical replay translations. Persistence and import are not already implemented.

**Evidence boundary:** Recorded implementation/evidence, not a fresh runtime certification by this roadmap. Changes are permitted when they improve the current game; revalidate affected current behavior.

**Direct hard dependents:** [A04](#m-a04), [P01](#m-p01), [P02](#m-p02), [U01](#m-u01), [U06](#m-u06), [MCU-1](#m-mcu-1), [IMPORT-1](#m-import-1).

<a id="m-a04"></a>

### A04 · Provider-owned electrical construction baseline

**Status:** DELIVERED BASELINE.

**Hard prerequisites:** [A03](#m-a03)

**Delivered basis:** `cc3532e8d138424ce986aa8f9b76688ec315f0a0`. Scoped electrical construction, explicit device joins, package/unit declarations and bounded physical materialization for existing resistor and controlled-indicator content.

**Current disposition:** R00 removes historical staging/interpretation paths, corrects residual generic-layer family knowledge, and closes three provider-boundary hardening follow-ups before expansion.

**Evidence boundary:** Recorded implementation/evidence, not a fresh runtime certification by this roadmap. Changes are permitted when they improve the current game; revalidate affected current behavior.

**Direct hard dependents:** [R00](#m-r00), [A08](#m-a08), [A09](#m-a09), [A11](#m-a11), [E02](#m-e02), [IMPORT-1](#m-import-1).

<a id="m-r00"></a>

### R00 · Current-only development baseline and provider-boundary cleanup

**Status:** COMPLETE - QUALIFIED, 2026-09-09. Current source/build/native/compiled/player gates and independent review pass; [evidence and limits](task-evidence/R00/README.md). The isolated CLI wrapper is not certified by the alternative product evidence.

**Hard prerequisites:** [A04](#m-a04)

**Priority / applicability:** Required immediate correction, before A05 and all new foundation expansion.

**Purpose and reason:** Remove the compatibility obligations already embedded across A02-A04 before A05 adds more consumers. Build forward from the delivered A04 commit, not from an old checkout.

**Architectural owner / affected systems:** Current request/resolved-plan/identity pipeline; electrical and physical declarations; bounded materializer; current verification entry points; AGENTS.md, ARCHITECTURE.md and this roadmap.

**Must not be coupled:** No A05 alternate-family feature, universal provider DSL, full leaf-to-composition rewrite, full persistence service, build migration, solver rewrite or blanket verifier-isolation rewrite. Stale documentation is fixed here, not a separate milestone.

**Exact deliverable:** One current supported interpretation per live content variant; obsolete replay/geometry/scoring/snapshot branches removed; truthful typed fault recipes; explicit package construction; current identity flow without historical-report adapters; checked declaration/plan/spec/receipt correspondence; generic enumeration of declared nets, parts and ownership; one current acceptance entry point with focused tests and a real workbench check.

**Acceptance:** All R00 exit conditions in Section 2 pass. Old artifact versions are rejected clearly before live mutation. Old report bytes, generator outputs, ID spellings, intermediate element counts and stage order are not gates. Current useful leaf families and composed content are not silently removed to make tests pass. All three A04 hardening findings are closed or superseded by a simpler boundary with equivalent current negative checks.

**Important negative tests:** Wrong plan/spec/receipt even with matching string IDs; swapped part/pad/net; foreign or opposite-terminal backing; inappropriate secondary/fault/attachment backing; stale or aborted receipt; missing/duplicate ownership; old descriptor silently reinterpreted; current fault kind mislabeled; unknown current provider; failed build of an actual supported path; wrong repair accepted.

**Performance / scalability evidence:** Record focused test/build cost and current generation success/failure outcomes. Run one final production build after integration and selected compiled/current-player checks. No full historical matrix or full Gate B recertification unless its consumed implementation changes.

**Architectural risk:** A deletion-only patch can hide lost coverage or move device knowledge to a new god class. Removing the historical adapter is not enough if an old default still selects the obsolete algorithm.

**Direct hard dependents:** [A05](#m-a05), [A06](#m-a06), [P01](#m-p01).

<a id="m-a05"></a>

### A05 · Functional-role families, alternate implementations and repeated instances

**Status:** COMPLETE - QUALIFIED. Final source/native/build/compiled/player evidence and independent closure review pass. Publication outcomes are recorded in the final handoff.

**Implementation:** Current generator 5 selects NMOS/NPN drivers for two independently commanded channels with genuine supply-present support. Current construction, physical mapping, solver reservations and reconstruction consume provider-owned declarations. The accepted bounded corpus and remaining limits are in [A05 evidence](task-evidence/A05/README.md). Full source review, native/build/compiled/ordinary-player checks are recorded separately; this does not qualify A06, Q15 or larger-board work.

**Hard prerequisites:** [R00](#m-r00)

**Priority / applicability:** Required foundation

**Purpose and reason:** Make functional composition produce genuinely different circuits rather than repeated fixed templates.

**Architectural owner / affected systems:** Device-intent resolver, block-family/variant registry, typed assumptions/guarantees and existing value recipes.

**Must not be coupled:** No arbitrary-netlist generator, every electronic topology, new difficulty label, or dormant historical compatibility adapter. Existing current leaf content may keep an independent current provider until deliberate migration; it may not keep obsolete algorithm revisions solely for replay.

**Exact deliverable:** At least two qualified implementations of one role, such as BJT and NMOS low-side control, plus repeated instances and a purposeful healthy-support contribution.

**Acceptance:** At least two structurally different implementations satisfy one current device intent through the same construction contracts; repeated instances have collision-free identity; genuine healthy support affects solved function and participates in retest. No new ordinary device-specific branches appear in generic assembly, physical materialization, replay capture, routing or UI. Current deterministic concern isolation is checked without historical generator parity.

**Important negative tests:** Variant needing unavailable drive current; unsupported high-side substitution; unconnected decorative support; repeated-instance collision; topology chosen from selected fault metadata.

**Performance / scalability evidence:** Variant conformance and structural-diversity reports that exclude coordinate/value-only changes from topology diversity.

**Architectural risk:** False interchangeable-role guarantees or exponential unconstrained variant combinations.

**Expected extension and scale effects:** Pluggability: ordinary variants live in providers. Scale: hierarchical vocabulary with cheap incompatibility pruning.

**Rolling playable integration:** Generate and install an actual current composed challenge through the new provider/variant path, then use ordinary player controls for diagnosis, physical repair and customer retest. If this path is still developer-only, close the normal-player launch integration here. An old leaf-only playthrough or metadata-only fixture cannot qualify A05.

**Direct hard dependents:** [A10](#m-a10), [P03](#m-p03), [E02](#m-e02), [E03](#m-e03), [E04](#m-e04), [E07](#m-e07), [E09](#m-e09), [E11](#m-e11), [E13](#m-e13), [E14](#m-e14), [IMPORT-2](#m-import-2), [Q15](#m-q15).

<a id="m-a06"></a>

### A06 · Power, reference, isolation and operating-state contracts

**Status:** COMPLETE - QUALIFIED. Current power/reference/provider contracts, real backfeed and meter lifecycle proof, affected repair/RC regressions, final JDK8/GWT build and ordinary player smoke pass. Direct self-review only, per the user's solo override; no independent-model review claimed.

**Hard prerequisites:** [R00](#m-r00)

**Priority / applicability:** Required foundation

**Purpose and reason:** Design multi-rail and mains/low-voltage semantics before source and instrument implementations encode conflicting meanings.

**Architectural owner / affected systems:** Typed domain/preflight contracts, source/load capability descriptions, operating-state model and measurement-reference policy.

**Must not be coupled:** No regulator library, offline converter implementation, mains certification or settings-owned power semantics.

**Exact deliverable:** Explicit rail/source/reference/earth identities, isolation boundaries, allowed joins, partial-power states, source bounds, backfeed paths and stored-energy readiness requirements.

**Acceptance:** Common labels never establish a join; permitted references and source contention are checked; aggregate OFF is not mistaken for discharged or globally zero potential; differential and earth-referenced instruments have distinct declared behavior.

**Important negative tests:** Common GND strings across isolated domains; one live rail; disabled source backfed through another block; scope return short; missing reference; floating absolute voltage presented as authoritative.

**Performance / scalability evidence:** Pure state/connection matrices and small modeled fixtures, including numerical-reference sensitivity reserved for A07.

**Architectural risk:** Metadata claiming runtime isolation or turning simulator policy into safety certification.

**Expected extension and scale effects:** Pluggability: source and instrument providers declare contracts. Scale: typed multi-domain composition.

**Future provider contract:** Keep power/reset/clock/brownout, thresholds, finite drive, loading and partial-power assumptions representable. Unknown or out-of-envelope state is explicit. Use a small current consumer/canary, not an unused MCU framework.

**Delivered scope/evidence:** See [A06 evidence](task-evidence/A06/README.md). Native A06 140 assertions; 82 shared compiled decisions and 44 runtime assertions. Current family retains one declared joined return; Earth scope, physical current limiter, arbitrary source/storage models and A07 numerical-reference sensitivity are not claimed. Unknown state fails closed.

**Direct hard dependents:** [A07](#m-a07), [A09](#m-a09), [P03](#m-p03), [U02](#m-u02), [E01](#m-e01), [E05](#m-e05), [E09](#m-e09), [E12](#m-e12), [MCU-1](#m-mcu-1).

<a id="m-a07"></a>

### A07 · Bounded solver execution, observation and high-risk model pilots

**Status:** COMPLETE as the bounded execution/observation foundation.

**Review correction:** [Schematic-replacement lifecycle](task-evidence/A07-reload-fix/README.md) retires the old accepted-event clock and callbacks during non-retaining imports/reloads and undo/redo. Fresh native 17-suite/271-report, production GWT 5/5, compiled 9/9 and independent review checks pass; original manual player evidence remains historical.

**Evidence:** [A07 qualification](task-evidence/A07/README.md): native 17 suites, production GWT 5/5, maintained-reader compiled 9/9, final visible player repair/retest, and independent read-only review/deltas PASS.

**Qualification limits:** The A06 CLI attempt failed ownership preflight before app execution. The authorized isolated browser proved the maintained A07 route selection, not that wrapper. Scale rows are fresh-context replicates, not cached-context warm benchmarks or physical Q60/Q100 qualification. A08 remains unstarted.

**Hard prerequisites:** [A01](#m-a01), [A06](#m-a06)

**Priority / applicability:** Required foundation

**Purpose and reason:** Find whether CircuitJS can support the intended dynamics and proof workload before a large provider library relies on it.

**Architectural owner / affected systems:** CircuitJS adapter, serialized execution owner, stepping/settlement/observation boundary and profiling fixtures.

**Must not be coupled:** Do not wait for full E06 or Q100. Prototype hard model classes in private fixtures; no wholesale solver replacement without a recorded failed requirement.

**Exact deliverable:** Bounded simulation-time execution independent of paint; explicit numeric failures and cancellation; reference-sensitivity tests; isolated small relay/transformer/nonlinear/converter pilots; matrix-cost probes at 20/40/60/100 parts.

**Acceptance:** One solver owner per singleton-sensitive CircuitJS context; observations identify the solved simulation state; cancellation cannot advance the player graph; numerical stabilization is not counted or displayed as a physical part. Report singularity, nonconvergence and model limits. GWT/JDK modernization is a separate evidence-driven decision, not a compatibility duty.

**Important negative tests:** Concurrent ownership attempt; stale result publication; nonfinite/singular solve; nonconvergence; time-step sensitivity; false earth reference; cancelled proof mutating the player board.

**Performance / scalability evidence:** Reduced matrix size, nonlinear subiterations, accepted steps, restamps, simulation/wall time, memory and latency. Compare model fidelity before choosing simplifications.

**Architectural risk:** Parallelizing singleton-sensitive elements or hiding a fidelity defect behind a faster ideal model.

**Expected extension and scale effects:** Pluggability: solver access through a narrow execution API. Scale: measured headroom and a path to isolated workers only if needed.

**Future state boundary:** Provide one deterministic accepted-step or scheduled simulation-time boundary for stateful IC/MCU/display providers. Define simultaneous-event ordering, startup/clock phase, cancellation and finite delta/feedback work. A synthetic scheduled-state canary is sufficient now; do not implement a CPU emulator or parallel live CirSim instances.

**Direct hard dependents:** [A08](#m-a08), [A09](#m-a09), [A10](#m-a10), [U02](#m-u02), [U03](#m-u03), [U10](#m-u10), [E01](#m-e01), [E06](#m-e06), [E10](#m-e10), [MCU-1](#m-mcu-1).

<a id="m-a08"></a>

### A08 · Multi-provider mutation and failure-isolated physical lifecycle

**Status:** COMPLETE - QUALIFIED for the bounded current resistor/diode lifecycle.

**Delivered/evidence:** [A08 acceptance](task-evidence/A08/README.md): restricted intent/scope/receipts; actual resistor and diode compensation; fresh-owner/callback/captured-control rejection; native 17 suites/466 report assertions, production GWT 5/5, compiled routes 14/14, A08 1,108 assertions with 90 diode partial writes and five installation failures, native-input diagnosis/wrong repair/correction/retest, and independent final delta review PASS.

**Qualification limits:** Current supported provider ownership, not universal rollback or an arbitrary Java plugin sandbox. Compiled application/reader checks are separate from CLI ownership/CDP-wrapper certification. A09 admission qualification is recorded separately below.

**Hard prerequisites:** [A04](#m-a04), [A07](#m-a07)

**Priority / applicability:** Required foundation

**Purpose and reason:** Generalize proven resistor compensation through a second real mutable part category before relay, capacitor and copper actions proliferate.

**Architectural owner / affected systems:** PhysicalBoardRuntime, slot/part providers, mutation transaction boundary, source/measurement settlement and fresh-owner installation.

**Must not be coupled:** No universal undo engine, unbounded nested transactions or migration of every current provider in one task. Obsolete entry points touched by the conversion are removed rather than left as shims.

**Exact deliverable:** Restricted prepare/commit/abort interface; immutable operation intent; validation and compensation receipts; at least resistor plus diode/capacitor lifecycle conformance.

**Acceptance:** Wrong compatible replacements remain possible; original fault and secondary damage remain distinct; partial failures restore owned state or leave a declared non-actionable isolated failure; fresh installation never aliases mutable owners.

**Important negative tests:** Failure after every new write stage; stale part/slot; re-entrant action; retained probe after replacement; cleanup failure; foreign inventory entry; mutated original reused as fresh candidate.

**Performance / scalability evidence:** Targeted failure-stage matrix and actual repair/measurement flows. Record temporary and retained object ownership.

**Architectural risk:** Claiming arbitrary deep rollback from a bounded transaction or creating a global snapshot god object.

**Expected extension and scale effects:** Pluggability: common lifecycle with provider-owned electrical mutations. Scale: predictable repair cost and state integrity.

**Future stateful lifecycle:** The restricted lifecycle accommodates provider-owned internal state and scheduled events without persisting executable callbacks. Removal, replacement, supply loss and model reset are distinct operations. Add actual stateful qualification only with consuming content.

**Direct hard dependents:** [A11](#m-a11), [U02](#m-u02), [U06](#m-u06), [U11](#m-u11), [U12](#m-u12), [E01](#m-e01), [E03](#m-e03), [E06](#m-e06), [E08](#m-e08), [E09](#m-e09), [E11](#m-e11), [E13](#m-e13), [E14](#m-e14), [MCU-2](#m-mcu-2), [IMPORT-1](#m-import-1), [Q15](#m-q15), [X02](#m-x02), [X06](#m-x06), [X08](#m-x08).

<a id="m-a09"></a>

### A09 · Production fault hypotheses and executable diagnostic providers

**Status:** COMPLETE - OWNER ACCEPTED AFTER POST-PUSH CHAT REVIEW. Published implementation `eee65df0e4fd45bc4547ee9639d6a05be1ba6d06`; native contracts, five-permutation GWT build, compiled A09 proof, affected A08/Task49 routes, and real native player diagnosis/repair/retest are retained PASS evidence. The owner accepted the subsequent chat review (no blocking code findings; documentation follow-up reconciled). Earlier quota-limited Luna/Reserve attempts remain incomplete, not successful reviews. Documentation closure reverified the unchanged fingerprints without rerunning production gates. See [A09 acceptance](task-evidence/A09/README.md).

**Hard prerequisites:** [A02](#m-a02), [A04](#m-a04), [A06](#m-a06), [A07](#m-a07)

**Priority / applicability:** Required foundation

**Purpose and reason:** Remove ordinary production admission dependence on family-specific developer dispatch without removing the proof.

**Architectural owner / affected systems:** Production hypothesis/diagnostic service; block-owned fault and observation/repair providers; developer verifier as independent client.

**Must not be coupled:** No brute-force all-conceivable-fault catalog, multiple faults, unsafe snapshot reuse or reduced correctness to save time.

**Exact deliverable:** Canonical hypothesis IDs, eligibility and serviceability, executable observation/actions, repair equivalence semantics and device-level complaint/retest integration.

**Acceptance:** At least two implemented block/variant paths use production providers; the hypothesis set is stable and nonempty; every retained hypothesis has legal observations and a reachable repair; developer-only fixtures cannot become normal admission.

**Important negative tests:** Same owner with different fault mechanisms; missing repair; identical observations but non-equivalent repairs; hidden-answer-based plan; unavailable input/instrument; stale proof or empty proof accepted.

**Performance / scalability evidence:** Measure the supported serial proof corpus and compare new provider behavior using current physical/electrical invariants and independent falsifiers, not exact historical report equality.

**Architectural risk:** Renaming Task41DeveloperVerifier while leaving all family branches centralized.

**Expected extension and scale effects:** Pluggability: new fault/variant adds a provider contribution. Scale: structured hypothesis accounting for D01.

**Rolling playable integration:** Exercise one real current challenge through production hypotheses/proof, publication, ordinary diagnosis, physical repair and retest. Developer verification is an independent client, not a hidden action source.

**Future fault vocabulary:** Keep package defects distinct from missing external power/reference/reset/clock. Admitted effects need a causal healthy model, an identified physical owner, observable distinction or a genuine equivalent repair, and reachable repair/retest.

**Direct hard dependents:** [A10](#m-a10), [A11](#m-a11), [U05](#m-u05), [E04](#m-e04), [E07](#m-e07), [E09](#m-e09), [E13](#m-e13), [E14](#m-e14), [MCU-1](#m-mcu-1), [IMPORT-1](#m-import-1), [D01](#m-d01), [X01](#m-x01).

<a id="m-a10"></a>

### A10 · Staged generation jobs, deterministic budgets and proof receipts

**Status:** AUTHORIZED - UNSTARTED. P01 and P02 are accepted; close P02 publication before implementation. No A10 staging, caching, cancellation or publication code is claimed yet.

**Hard prerequisites:** [A05](#m-a05), [A07](#m-a07), [A09](#m-a09), [P02](#m-p02)

**Priority / applicability:** Required foundation

**Purpose and reason:** Keep hierarchical generation bounded, cancellable and diagnosable before the variant space grows.

**Architectural owner / affected systems:** Generation job coordinator over immutable plans, stage results, solver/physical services and atomic publication.

**Must not be coupled:** No full Cartesian generation, arbitrary parallel solver contexts, or published PASS before final stage qualification.

**Exact deliverable:** Cheap descriptor/domain/value/demand checks; healthy solve; physical realization/access; bounded hypothesis proof; symptom projection; versioned receipts and structured rejection stages.

**Acceptance:** Expected incompatibility/routing exhaustion differs from programming error and infrastructure failure; original owner survives failed/cancelled jobs; same manifest follows the same candidate order; receipts include all consumed model/input/physical dependencies.

**Important negative tests:** Unexpected RuntimeException swallowed as an ordinary retry; altered source/load against cached proof; stale job wins publication; wall-clock race selects a candidate; missing stage receipt.

**Performance / scalability evidence:** Per-stage counts, cold/warm timings, cache provenance and cancellation latency against A01 budgets. Serial reference behavior remains available.

**Architectural risk:** A coordinator that owns every electrical rule or a cache that certifies the wrong candidate.

**Expected extension and scale effects:** Pluggability: services expose stage contracts. Scale: cheap rejection first and reproducible work ceilings.

**Rolling playable integration:** Run current staged generation, healthy solve, physical validation, complete hypothesis proof and atomic publication through ordinary diagnosis/repair/retest. Reuse the scenario shape, not old passing receipts after consumed code changes.

**Future design origins:** Native generation and later author-approved imports enter the same immutable resolved-plan services. Design origin is provenance, not a second runtime/PCB pipeline. Add actual import/state receipt fields when consumed; retain only inexpensive explicit seams before then.

**Direct hard dependents:** [U04](#m-u04), [U05](#m-u05), [U06](#m-u06), [IMPORT-1](#m-import-1), [D01](#m-d01), [Q15](#m-q15).

<a id="m-a11"></a>

### A11 · Provider conformance, registry consistency and extension-cost proof

**Status:** UNSTARTED.

**Hard prerequisites:** [A04](#m-a04), [A08](#m-a08), [A09](#m-a09)

**Priority / applicability:** Required foundation

**Purpose and reason:** Make the 8-9/10 pluggability goal falsifiable rather than an architecture-document compliment.

**Architectural owner / affected systems:** Provider/registry bootstrap, reusable conformance harness and source dependency checks.

**Must not be coupled:** No cosmetic mass splitting of large files, automatic reflection-based discovery, or dedicated cleanup of harmless legacy menus.

**Exact deliverable:** One declared provider registration surface; tests for model/terminal/package/mutation/diagnostic consistency; an ordinary additional variant implemented as an extension exercise.

**Acceptance:** An ordinary supported variant requires provider-local additions and one declared registration surface, with no new device-specific branch in generic assembly, physical materialization, diagnosis, replay serialization, routing or UI. Novel physics can change an explicit adapter. Missing/conflicting registrations fail before publication; record actual central knowledge changed, not an arbitrary file-count slogan.

**Important negative tests:** Duplicate type/version; missing footprint/renderer; inconsistent pin mapping; feature recognized in one registry but not another; generic layer importing an individual device implementation.

**Performance / scalability evidence:** Actual changed-file/category ledger, test/build cost and current supported routes. Score by core knowledge changed, not an arbitrary two-file limit.

**Architectural risk:** Generating boilerplate or another universal registry before interfaces have real consumers.

**Expected extension and scale effects:** Pluggability: measurable provider-local extension. Scale: qualification cost grows with contracts, not combinations.

**Future capabilities:** Model availability, package/pin mapping, physical serviceability, instruments and serializable state are separate claims. A future import support matrix derives from the same registrations. Electrical support alone does not qualify a physical challenge.

**Direct hard dependents:** [U07](#m-u07), [IMPORT-1](#m-import-1), [IMPORT-5](#m-import-5), [Q15](#m-q15), [REL-A](#m-rel-a), [Q100](#m-q100).

<a id="m-p01"></a>

### P01 · Physical coordinates, immutable poses and package orientation

**Status:** ACCEPTED. Fresh independent review passed with no blocking findings, and the owner authorized publication. All reviewed code/test hashes match; closeout only reconciles documentation. The Windows native wrapper and optional native-player smoke remain explicitly unqualified, not relabeled PASS. [Evidence and limits](task-evidence/P01/README.md).

**Hard prerequisites:** [A02](#m-a02), [A03](#m-a03), [R00](#m-r00)

**Priority / applicability:** Required foundation

**Purpose and reason:** Decouple physical board extent from screen size and establish one truthful transform before larger layouts or layers.

**Architectural owner / affected systems:** Package geometry/placement, board coordinate contract, immutable physical snapshots and pose adapters.

**Must not be coupled:** No arbitrary continuous rotation, manufactured-millimeter claims, 3D mesh engine or route redesign.

**Exact deliverable:** Documented bounded integer/fixed-point units; package-declared rotations; separate side/mount/view transforms; copied immutable arrays/envelopes; overflow-safe coordinate operations.

**Acceptance:** Allowed rotations transform all pads, leads, courtyards, escape directions and probe surfaces together without renaming terminals; view flip is not electrical remapping; mutations cannot alter a frozen fingerprint through an exposed array.

**Important negative tests:** Aliased PcbTraceGeometry arrays; 90-degree pin-order permutation; mirrored polarity; integer overflow; nominal shape changed without version; body transformed but probe left behind.

**Performance / scalability evidence:** Exhaustive declared finite current poses, independent forward/inverse transform checks and affected current physical/probe behavior. No historical geometry replay requirement.

**Architectural risk:** Repeating Task43 by giving drawing and interaction different geometry authorities.

**Expected extension and scale effects:** Pluggability: packages declare valid poses locally. Scale: larger extents without shrinking or warping parts.

**SMD architecture canaries:** Required developer-only 0805-style and SOT-23-style fixtures, optional SOIC, exercise explicit mounting side, package-declared rotation and surface-pad geometry. No implicit through-hole barrel or both-face terminal. These are architecture checks, not SMD gameplay, BGA or reflow support.

**Direct hard dependents:** [P02](#m-p02), [P03](#m-p03), [U01](#m-u01), [E03](#m-e03), [E09](#m-e09), [E11](#m-e11).

<a id="m-p02"></a>

### P02 · Durable layer-aware copper and conductive-surface model

**Status:** ACCEPTED. The owner supplied a fresh independent delta-review PASS for candidate `0326cdb8a09ae8ee2cd309d178cad29fa09a9340e81bb075be3ea1303796f5b8`. F1 is resolved with no blocking findings; the stale checkpoint count is corrected. Fresh review passed 20 native suites, 3,209 P02 assertions, the original 500-fixture probe, 96 additional fixtures/four rejection cases and all 12 compiled routes. The five-permutation build and unchanged dependencies were hash-revalidated. Earlier quota failures remain historical evidence. Physical cut/restore snapshots and projection contracts do not enable E08 gameplay. [Evidence and limits](task-evidence/P02/README.md).

**Hard prerequisites:** [P01](#m-p01), [A03](#m-a03)

**Priority / applicability:** Required foundation

**Purpose and reason:** Establish the physical connectivity identity needed by vias, layers, cuts and saves before routing consumers harden.

**Architectural owner / affected systems:** PhysicalRealizationPlan/conductor graph, pad/surface definitions, correspondence projection and read-only geometry views.

**Must not be coupled:** This is not a full two-layer autorouter, Gerber model, plane solver or permission to expose unqualified hidden copper.

**Exact deliverable:** Stable copper edges/junctions and provenance; top/bottom layer IDs; plated barrels, vias and non-plated holes; physical access policy; immutable pristine and mutation-owned current connectivity.

**Acceptance:** Same-layer contact and legal barrels connect exactly; projected crossing on different layers does not; a cut removes its physical edge, not every branch on a logical bus; solver projection is bijective where required and audited where one part has many internal elements.

**Important negative tests:** Via without valid layer endpoints; NPTH conducts; hidden same-net bridge; cut identity from polyline index; local rerender renames copper; unrelated layers short at a crossing.

**Performance / scalability evidence:** Small independent geometric/conductor oracle and round-trip identity fixtures. Include current one-layer and two-layer physical correctness cases.

**Architectural risk:** Collapsing logical net identity into current connectivity or keeping mutable geometry behind proof caches.

**Expected extension and scale effects:** Pluggability: new physical actions consume conductor contracts. Scale: avoids a later layers/cuts/save format rewrite.

**SMD architecture canaries:** Use the P01 surface-package fixtures to prove face/layer-specific copper access and absence of an invented plated barrel. A top-only surface pad cannot be probed from the underside just because projected coordinates coincide.

**Current identity rule:** Conductor identity survives view changes and supported current path subdivision. A genuine reroute is a new current realization; old development artifacts may be rejected. Do not build a historical cut-migration service.

**Direct hard dependents:** [A10](#m-a10), [P03](#m-p03), [P04](#m-p04), [P06](#m-p06), [P08](#m-p08), [U01](#m-u01), [U06](#m-u06), [E05](#m-e05), [E08](#m-e08).

<a id="m-p03"></a>

### P03 · Demand-based board sizing and hierarchical placement

**Status:** UNSTARTED.

**Hard prerequisites:** [A05](#m-a05), [A06](#m-a06), [P01](#m-p01), [P02](#m-p02)

**Priority / applicability:** Required foundation

**Purpose and reason:** Replace the fixed generic placement area with constrained physical planning suitable for multiple functional regions.

**Architectural owner / affected systems:** Device physical-demand estimator, bounded outline/aspect candidates, regional placement and local refinement service.

**Must not be coupled:** No universal CAD placer, cosmetic footprint shrinkage or electrical topology change to force a fit.

**Exact deliverable:** Package/courtyard demand, connector anchors, domain barriers, fan-out corridors and access constraints; multiple bounded outlines/aspects; hierarchical placement with limited global feedback.

**Acceptance:** RB15/RB30 and larger structural fixtures place according to real envelopes; repeated variants fit without IDs changing; local region convenience cannot strand an inter-region net; layout is independent of selected fault.

**Important negative tests:** Area-only feasibility claim; connector facing outward; disconnected island region; insufficient access; unlimited outline growth; visually labeled faulty region; impossible domain separation accepted.

**Performance / scalability evidence:** Matched flat/authored/hierarchical corpus, area/utilization definitions, rejection reasons and cost. Record a fixed comparison corpus before optimization; the old implementation need not ship.

**Architectural risk:** Rigid regions reducing routability or giant empty boards disguising poor search.

**Expected extension and scale effects:** Pluggability: roles supply physical constraints, not coordinates. Scale: explicit board demand and routing space.

**Direct hard dependents:** [P04](#m-p04), [Q15](#m-q15).

<a id="m-p04"></a>

### P04 · Canonical multi-terminal routing and escape planning

**Status:** UNSTARTED.

**Hard prerequisites:** [P02](#m-p02), [P03](#m-p03)

**Priority / applicability:** Required foundation

**Purpose and reason:** Route actual net demand efficiently and truthfully instead of inheriting root-star and raw-grid assumptions.

**Architectural owner / affected systems:** Net routing service, escape/channel planner, canonical path representation and versioned route scoring.

**Must not be coupled:** No exact universal Steiner optimizer, planes, clearance relaxation or change to component electrical values.

**Exact deliverable:** Typed net priorities, bounded same-net tree/trunk candidates, explicit endpoint escape constraints, canonical contact-preserving segments and deterministic tie breaking.

**Acceptance:** All required terminals join; trunks and branches retain provenance; simplification preserves escape/contact witnesses; reported bends and unique reuse are accurate; heuristic guarantees match actual edge costs.

**Important negative tests:** Orphan branch; shortcut crossing courtyard; duplicated copper reward; zero-length segment; heuristic advertised optimal while overestimating reuse costs; rail inferred from a GND string.

**Performance / scalability evidence:** Raw-versus-canonical segment counts, expansions, length, bends, congestion and matched-corpus route outcomes.

**Architectural risk:** Canonicalization erasing repair loci or new optimality claims unsupported by the search.

**Expected extension and scale effects:** Pluggability: net roles drive generic routing. Scale: shorter paths and smaller validation/render workloads.

**Direct hard dependents:** [P05](#m-p05), [P06](#m-p06), [P08](#m-p08), [IMPORT-1](#m-import-1), [Q15](#m-q15).

<a id="m-p05"></a>

### P05 · Bounded rerouting and congestion recovery

**Status:** UNSTARTED.

**Hard prerequisites:** [P04](#m-p04)

**Priority / applicability:** Required recovery evaluation; sophisticated negotiated congestion conditional

**Purpose and reason:** Provide principled recovery from route-order congestion before compensating with excessive links or board area.

**Architectural owner / affected systems:** Route candidate scheduler, obstacle occupancy and deterministic conflict selection.

**Must not be coupled:** No compulsory Pathfinder clone or universal routing guarantee. Algorithm choice follows measured PCB evidence, not FPGA analogy alone.

**Exact deliverable:** Alternate net ordering and capped rip-up/reroute with structured exhaustion. Negotiated congestion is a measured optional extension within a separately frozen sub-scope.

**Acceptance:** Recorded order-sensitive fixtures improve without relaxing final legality; only a fully valid final occupancy is published; deterministic caps stop difficult cases; expected exhaustion is not classified as code corruption.

**Important negative tests:** Unbounded retry; temporary cross-net overlap published; stale occupancy after rip-up; candidate chosen by finish time; harder fixtures silently removed.

**Performance / scalability evidence:** Compare acceptance rate, expansions, pass count and quality on held-out target fixtures. Report worse cases as well as improvement.

**Architectural risk:** Complex heuristics consuming more work than they save or crossing legality confused with search cost.

**Expected extension and scale effects:** Pluggability: generic recovery independent of device type. Scale: addresses practical congestion while keeping a finite budget.

**Direct hard dependents:** [P07](#m-p07), [P09](#m-p09).

<a id="m-p06"></a>

### P06 · True raised factory-crossover prototype and sparse-link policy

**Status:** UNSTARTED.

**Hard prerequisites:** [P02](#m-p02), [P04](#m-p04)

**Priority / applicability:** Required prototype; routine link use conditional on P09

**Purpose and reason:** Qualify factory links as real physical crossovers before using them as a routing escape hatch.

**Architectural owner / affected systems:** Factory-link package/provider, layer/height-aware obstacle policy and construction/correspondence adapter.

**Must not be coupled:** No player repair wire semantics, unlimited jumper coverage, ideal zero-resistance stamp singularities or fake courtyard exemptions.

**Exact deliverable:** One raised link with explicit conductive endpoints and insulated/clear underpass region; stable identity and electrical backing; candidate count/density/cost policy proposal.

**Acceptance:** Copper can pass underneath only where declared; it does not contact the elevated conductor; endpoint copper contacts remain correct; link removal and probe access follow truthful package behavior.

**Important negative tests:** Ordinary axial resistor renamed zero-ohm with blocked underpass; intersecting projection short; unlimited links; link inserted without rerunning affected electrical/diagnostic proof.

**Performance / scalability evidence:** Matched difficult fixtures comparing link count, area saved, clarity and routing effort. Prototype success is not blanket normal-play adoption.

**Architectural risk:** A geometry exception for one fixture becoming invisible connectivity everywhere.

**Expected extension and scale effects:** Pluggability: one real component capability. Scale: sparse useful crossovers, not a substitute for layers.

**Direct hard dependents:** [P07](#m-p07), [P09](#m-p09).

<a id="m-p07"></a>

### P07 · Required two-layer routing, viewing and interaction comparison

**Status:** UNSTARTED.

**Hard prerequisites:** [P05](#m-p05), [P06](#m-p06), [U01](#m-u01)

**Priority / applicability:** Mandatory investigation and working prototype

**Purpose and reason:** Determine with actual matched fixtures whether limited two-layer routing is the correct advanced-board strategy.

**Architectural owner / affected systems:** Layer-aware routing prototype, via provider, per-layer validation and physical-side interaction.

**Must not be coupled:** No more than two copper layers, full CAD feature set, plane pours or forced unrestricted via use.

**Exact deliverable:** One-layer, sparse-link, restricted-two-layer and fuller-two-layer runs on the same netlists and package sets; real top/bottom inspection, plated transitions and via penalties.

**Acceptance:** Opposite-layer crossings stay isolated; plated pads/vias join only intended copper; underside targets are reachable through explicit view/flip; primary/secondary barriers constrain both layers; no invisible underside acceptance.

**Important negative tests:** Wrong via net; via crosses forbidden domain region; top hit selects bottom-only trace; board flip remaps terminals; unrendered layer carries required repair path.

**Performance / scalability evidence:** Acceptance/rejection, runtime, expansions, via/link count, length, readability and probe/cut implications for RB30 and 56/100 structural fixtures.

**Architectural risk:** Assuming a second layer automatically doubles success, or benchmarking routing without its user-facing cost.

**Expected extension and scale effects:** Pluggability: shared layer contract across consumers. Scale: early feasibility evidence for 60/100 targets.

**Direct hard dependents:** [P09](#m-p09).

<a id="m-p08"></a>

### P08 · Scalable physical validation and projection caches

**Status:** UNSTARTED.

**Hard prerequisites:** [P02](#m-p02), [P04](#m-p04), [A01](#m-a01)

**Priority / applicability:** Required scale qualification; individual optimizations evidence-selected

**Purpose and reason:** Control geometry costs while preserving the independent correctness boundary.

**Architectural owner / affected systems:** Physical validator, spatial broad-phase index, immutable geometry snapshots and projection-cache invalidation.

**Must not be coupled:** No spatial index solely for elegance; do not replace exact checks with bounding-box guesses.

**Exact deliverable:** Measured hotspot fixes such as broad-phase contacts, canonical paths and versioned caches; a simple brute-force reference retained for small/adversarial cases.

**Acceptance:** Fast and reference validators agree; no cache survives a changed pose, layer, copper state, package or policy; incremental edits validate all affected neighbors and connectivity, followed by periodic/full qualification checks.

**Important negative tests:** Changed array under cached hash; missed cell-boundary collision; stale deleted via; new cross-net contact outside dirty region; topology split missed by local-only checks.

**Performance / scalability evidence:** Actual candidate-pair counts, memory and validation time at each scale, including pathological dense contacts.

**Architectural risk:** Optimizing away the only independent oracle or assuming spatial indexing has a universal subquadratic worst case.

**Expected extension and scale effects:** Pluggability: uniform geometry query boundary. Scale: measured broad-phase and cache gains.

**Reference oracle rule:** Keep the simple brute-force validator only as an independent small-case correctness oracle. It is not a shipped historical runtime and must not retain obsolete scoring bugs. Fast/current and reference implementations agree on the current physical contract.

**Direct hard dependents:** [P09](#m-p09).

<a id="m-p09"></a>

### P09 · Production physical envelope and layer-strategy qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [P05](#m-p05), [P06](#m-p06), [P07](#m-p07), [P08](#m-p08), [U01](#m-u01)

**Priority / applicability:** Required physical policy gate

**Purpose and reason:** Freeze the actual routing/view/interaction policy before medium-board content and physical repair build on it.

**Architectural owner / affected systems:** SupportedEnvelope registry and physical pipeline qualification.

**Must not be coupled:** No claim of completed Q60/Q100 playability; no forced feature implementation merely because a prototype exists.

**Exact deliverable:** Versioned bounds for board size/aspect, package/pose mix, domains, net degree, route/via/link costs, access and resource usage; preferred limited-two-layer policy confirmed or rejected with evidence.

**Acceptance:** All admitted corpus boards are electrically corresponding, legible and probeable; rejection is explicit; no profile can silently disable necessary physical truth. Restrict scope honestly when an unproven policy remains.

**Important negative tests:** Part-count-only support label; untested via density; hidden layer selectable as ordinary copper; link cap bypass; route below display/access floor.

**Performance / scalability evidence:** Frozen held-out structural corpus and quality/performance distributions, not one attractive screenshot.

**Architectural risk:** A permissive envelope that accepts cases the physical pipeline cannot support or an overfitted whitelist advertised as procedural generality.

**Expected extension and scale effects:** Pluggability: content requests a declared envelope. Scale: evidence-bound 30/60/100 physical evolution.

**Direct hard dependents:** [E08](#m-e08), [IMPORT-4](#m-import-4), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100).

<a id="m-u01"></a>

### U01 · Coherent large-board viewport and side-aware targeting

**Status:** UNSTARTED.

**Hard prerequisites:** [P01](#m-p01), [P02](#m-p02), [A03](#m-a03)

**Priority / applicability:** Required foundation

**Purpose and reason:** Prevent larger boards from becoming microscopic or dishonest to probe.

**Architectural owner / affected systems:** Workbench viewport/renderer, one forward/inverse transform, target resolver and marker projection.

**Must not be coupled:** No mandatory 3D engine, touchscreen redesign or answer-highlighting region map.

**Exact deliverable:** Pan, permanent zoom, fit-board/selection, functional-region navigation, separate tray chrome, explicit board-side/layer view, truthful ambiguous-target handling, and a temporary cursor-centered Spacebar inspection loupe. Add a minimap only if measured navigation warrants it.

**Acceptance:** Rendering, targeting, probe markers and accessibility use the same composed transform. Space press captures the permanent view; move tracks the cursor; optional temporary wheel magnification does not mutate permanent zoom; release restores exactly. Focus loss, modal entry, pointer cancellation and scene replacement dismiss the loupe safely. Exposed surfaces/markings are inspectable; hidden or occluded copper cannot be hit; board flip does not remap electrical identity. Dense intimidating overview is allowed.

**Important negative tests:** Stuck loupe on focus/modal loss; pan/zoom changed after release; markers drift; huge invisible targets; wrong-layer pad hit; top-only SMD pad exposed on bottom; transform used by rendering but not input; hidden fault data in accessibility labels.

**Performance / scalability evidence:** Input/frame measurements on 15/30/56/100 structural fixtures and real operator probe trials.

**Architectural risk:** Two independent transform systems or level of detail that removes essential markings/copper.

**Expected extension and scale effects:** Pluggability: render providers consume a view transform. Scale: navigable larger boards without shrinking physical truth.

**SMD integration:** Carry P01/P02 0805/SOT-23 developer fixtures through visible view/flip/loupe and legitimate probing. The player-facing SMD catalog remains later.

**Rolling playable integration:** Perform overview, pan/zoom, Space press/move/release, supported flip, ordinary probing, component interaction and repair/retest on a current generated board.

**Direct hard dependents:** [P07](#m-p07), [P09](#m-p09), [U02](#m-u02), [U04](#m-u04), [U08](#m-u08), [E08](#m-e08), [E11](#m-e11), [IMPORT-1](#m-import-1), [Q15](#m-q15), [X09](#m-x09).

<a id="m-u02"></a>

### U02 · Reference-aware measurements and shared observation boundary

**Status:** UNSTARTED.

**Hard prerequisites:** [A06](#m-a06), [A07](#m-a07), [A08](#m-a08), [U01](#m-u01)

**Priority / applicability:** Required foundation

**Purpose and reason:** Provide trustworthy multi-domain and AC measurements before mains-bearing challenges depend on them.

**Architectural owner / affected systems:** Instrument providers, finite-load/stimulus adapters, observation service and source/energy readiness.

**Must not be coupled:** No direct reads of configured R/C as a substitute for active measurement; no new model fidelity promised by the instrument.

**Exact deliverable:** Consistent DC differential, AC/RMS policy, polarity, loading, clipping/overrange, sample windows and domain/reference semantics; preserve active-meter transactions and normal controls.

**Acceptance:** Each reading derives from CircuitJS samples with declared bandwidth/window; source isolation and residual energy are checked; meter loading is modeled; no absolute floating voltage is reported as meaningful earth potential.

**Important negative tests:** Wrong RMS convention; DC offset silently included/excluded; unloaded fake voltmeter; cross-reference short ignored; power-on during stimulus; stale post-mutation samples.

**Performance / scalability evidence:** Known waveforms and high-impedance/floating-domain fixtures, plus actual visible red/black input and cleanup.

**Architectural risk:** Measurement convenience altering the circuit invisibly or reporting more accuracy than the model supports.

**Expected extension and scale effects:** Pluggability: modes share observation/lifecycle services. Scale: no repeated bespoke solver manipulation per instrument.

**Direct hard dependents:** [U03](#m-u03), [U08](#m-u08), [U10](#m-u10), [U11](#m-u11), [U12](#m-u12), [E05](#m-e05), [E09](#m-e09), [E12](#m-e12), [IMPORT-1](#m-import-1), [Q30](#m-q30), [X08](#m-x08).

<a id="m-u03"></a>

### U03 · Oscilloscope and frequency with solver-time fidelity

**Status:** UNSTARTED.

**Hard prerequisites:** [U02](#m-u02), [A07](#m-a07)

**Priority / applicability:** Required foundation

**Purpose and reason:** Make dynamic faults observable before timers or offline conversion claim diagnostic readiness.

**Architectural owner / affected systems:** Solver observation stream, scope/frequency instrument providers and bounded waveform buffers.

**Must not be coupled:** No multi-channel analyzer, digital protocol decoder or guaranteed switching-ripple view for a model that omits it.

**Exact deliverable:** One-channel initial scope, reference choice, trigger, time/voltage scale, finite sample policy and frequency extraction; no invented waveform for averaged models.

**Acceptance:** Waveforms and frequency agree with actual solved time; aliasing/bandwidth/insufficient-window states are explicit; buffer memory is bounded; mutation/power/probe changes invalidate observations correctly.

**Important negative tests:** UI-frame samples mistaken for simulation time; out-of-band switching drawn smoothly; reference short; stale waveform after replacement; frequency read from component metadata.

**Performance / scalability evidence:** Linear/nonlinear temporal fixtures, timestep comparisons, trigger failures and input/frame cost while solving.

**Architectural risk:** A copied legacy Scope UI carrying hidden element/global-owner assumptions into the physical workbench.

**Expected extension and scale effects:** Pluggability: temporal tools consume one observation contract. Scale: bounded data instead of unlimited sample retention.

**Direct hard dependents:** [U09](#m-u09), [E06](#m-e06), [E07](#m-e07), [E10](#m-e10), [E13](#m-e13), [E14](#m-e14), [IMPORT-4](#m-import-4), [Q60](#m-q60), [X01](#m-x01).

<a id="m-u04"></a>

### U04 · Explicit sessions, Resources, Settings and honest catalog surface

**Status:** UNSTARTED.

**Hard prerequisites:** [A10](#m-a10), [U01](#m-u01)

**Priority / applicability:** Required foundation

**Purpose and reason:** Provide a usable product loop without making menu or Shop code an electrical owner.

**Architectural owner / affected systems:** Session coordinator, typed launch request, public reference content, presentation settings and catalog projections.

**Must not be coupled:** No dependency on every future catalog family, scoring, economy or mobile support.

**Exact deliverable:** Menu/ticket/workbench/retest/results transitions, cancellation/error handling, seed/replay entry, generic Resources and persistent presentation/accessibility settings; existing catalogs shown truthfully.

**Acceptance:** Normal UI cannot inspect fault/private-original metadata; settings do not alter physics; Shop acquisition uses the real inventory and installation remains a separate action; unsupported features are absent or unmistakably unavailable.

**Important negative tests:** Fake cart; old session callback; hidden answer in ARIA/title; close/reopen loses owner isolation; UI determines repair success; unsupported profile accepted.

**Performance / scalability evidence:** Selected normal-player browser workflows, keyboard/modal focus and privacy inspection.

**Architectural risk:** A new frontend framework or parallel inventory built to hide placeholders.

**Expected extension and scale effects:** Pluggability: typed session and catalog requests. Scale: one product orchestration surface across many devices.

**Direct hard dependents:** [U05](#m-u05), [U06](#m-u06), [IMPORT-1](#m-import-1), [REL-A](#m-rel-a), [X09](#m-x09).

<a id="m-u05"></a>

### U05 · Computed difficulty and staged profile calibration

**Status:** UNSTARTED.

**Hard prerequisites:** [A09](#m-a09), [A10](#m-a10), [U04](#m-u04), [Q15](#m-q15)

**Priority / applicability:** Required foundation

**Purpose and reason:** Make difficulty a proved property of diagnosis, not the number of parts or a hidden change to physics.

**Architectural owner / affected systems:** DifficultyProfile/Assessment, diagnostic evidence, physical envelope and assistance policy.

**Must not be coupled:** Initial U05 completion does not enable HARD or PSYCHOTIC. Those need Q60/REL-B or X05 evidence respectively.

**Exact deliverable:** Versioned complexity features and initial EASY/MEDIUM calibration; explicit unavailable HARD/PSYCHOTIC states; a protocol for Q30/Q60 HARD and X05 expert calibration.

**Acceptance:** Requested profile constrains generation; admitted evidence matches; controls/markings/instruments remain available; required hypothesis reduction and legal action depth are measured without forcing one exact human route.

**Important negative tests:** Raw component count as sole score; one-probe answer despite advanced label; unavailable instrument; assistance leaking the selected owner; tolerance changes per difficulty.

**Performance / scalability evidence:** Corpus separation and user trials; later profiles require fresh calibrated receipts at their release gates.

**Architectural risk:** A formula given authority over human difficulty or duplicated per-difficulty generators.

**Expected extension and scale effects:** Pluggability: features contribute evidence, not labels. Scale: several honest difficulty dimensions.

**Direct hard dependents:** [REL-A](#m-rel-a), [REL-B](#m-rel-b), [X05](#m-x05), [X07](#m-x07).

<a id="m-u06"></a>

### U06 · Semantic history, durable resume and distinct sharing contracts

**Status:** UNSTARTED.

**Hard prerequisites:** [U04](#m-u04), [A08](#m-a08), [A03](#m-a03), [P02](#m-p02), [A10](#m-a10)

**Priority / applicability:** Required foundation

**Purpose and reason:** Avoid inventing persistence after layers, physical repairs and larger inventories already rely on transient identity.

**Architectural owner / affected systems:** Session history, versioned save/restore coordinator, part inventory serialization and replay adapters.

**Must not be coupled:** No scoring, multiple faults, cloud account or economy required. New E08/X02 state providers must later extend and requalify this contract.

**Exact deliverable:** Separate pristine challenge sharing from mutable session saves. Provide current semantic operations/history, part/inventory identity, physical modifications, source state and explicitly supported dynamic state. Use a current schema/build or model epoch; incompatible development saves reject without mutation and without a migration chain.

**Acceptance:** Current-format reconstruction reproduces declared physical/electrical state without solver matrix/node identity. Stateful elements serialize necessary current internal state or disclose restart versus exact resume. File validation completes before publication. No historic descriptor, old save reader, byte-identical previous-build replay or auto-migration is required.

**Important negative tests:** Nearest-pixel cut migration; overwritten original part; resumed capacitor silently discharged; partial file accepted; stale cached proof trusted after state import; fault answer in visible share text.

**Performance / scalability evidence:** Round-trip current mutations, corrupted/incompatible saves, replay across compiled environments and bounded artifact size.

**Architectural risk:** Claiming exact mid-transient resume without preserving model state or binding saves to runtime object addresses.

**Expected extension and scale effects:** Pluggability: provider state contracts. Scale: saves survive larger designs and longer sessions.

**Future state and imports:** When consumed, provider schemas cover modeled registers/latches, GPIO drive, clock phase, timers/ADC/PWM, simulation time and semantic pending events. Imported artifacts include retrievable source bytes, content hash, current accepted interpretation/mappings and healthy intent. No arbitrary URL fetching, host closures, executable payloads or private-answer leakage. These implementations are not prerequisites until advertised.

**Direct hard dependents:** [U07](#m-u07), [MCU-2](#m-mcu-2), [IMPORT-5](#m-import-5), [REL-B](#m-rel-b), [X02](#m-x02), [X04](#m-x04), [X07](#m-x07), [X09](#m-x09).

<a id="m-u07"></a>

### U07 · Long-session, inventory and browser reliability envelope

**Status:** UNSTARTED.

**Hard prerequisites:** [U06](#m-u06), [Q30](#m-q30), [A11](#m-a11)

**Priority / applicability:** Required foundation

**Purpose and reason:** Qualify the growing bench and session, not just the original component count.

**Architectural owner / affected systems:** Runtime lifetime/disposal, inventory/history/waveform retention and browser performance tests.

**Must not be coupled:** No global process killing, silent history truncation or unrequested destructive cleanup.

**Exact deliverable:** Frozen long-session workloads with repeated acquisition/removal, measurement, saves, owner replacement and large loose inventories; memory/latency ownership receipts.

**Acceptance:** Legitimately retained parts stay probeable; replaced owners release task-owned handlers/graphs; active versus inactive elements are explained; performance and memory remain within the declared workload budget.

**Important negative tests:** Accidental disposal of retained loose part; new owner holds old graph; growing waveform arrays; stale receipt after reset; cleanup removes user resources.

**Performance / scalability evidence:** Growth slopes, heap/retained object proxies, active element counts, input latency and cold/warm browser comparisons. Extend at Q60/Q100.

**Architectural risk:** Calling expected inventory growth a leak or masking a leak by deleting player-owned parts.

**Expected extension and scale effects:** Pluggability: explicit lifecycle conformance. Scale: large board plus long session remains usable.

**Direct hard dependents:** [Q60](#m-q60), [REL-B](#m-rel-b), [Q100](#m-q100).

<a id="m-u08"></a>

### U08 · Envelope-safe physical markings and dynamic indication

**Status:** UNSTARTED.

**Hard prerequisites:** [U01](#m-u01), [U02](#m-u02), [Q15](#m-q15)

**Priority / applicability:** Planned polish; required only where advertised or needed for legibility

**Purpose and reason:** Improve current physical realism without letting cosmetic drawing become a second geometry or electrical authority.

**Architectural owner / affected systems:** Physical render providers, immutable nameplates, solved operational-state projection and accessibility presentation.

**Must not be coupled:** Not a blocker for a correctly readable alpha, solver model, relay or routing algorithm; no photorealistic/3D framework.

**Exact deliverable:** Restrained axial/pad/copper/board surface improvements and continuous LED intensity from one qualified solver-derived accessor; installed/loose presentation shares the same physical truth.

**Acceptance:** Original bands and polarity remain correct and readable; no geometry, probe or copper identity changes from cosmetic work; loose LEDs remain unpowered; signed current, saturation and replacement rebinding are handled; brightness is never the sole required diagnostic clue.

**Important negative tests:** Renderer recomputes its own brightness physics; negative current treated as positive illumination; shadow hides terminal; numeric original value leaked through accessibility text; visual change retargets a probe.

**Performance / scalability evidence:** Before/after geometry fingerprints, physical rendering correspondence, low-zoom readability and frame cost; independent intensity edge cases and an equivalent electrical observation.

**Architectural risk:** Pretty output masking a loss of physical identity or introducing a renderer-owned behavior model.

**Expected extension and scale effects:** Pluggability: providers own appearance within declared envelopes. Scale: clearer inspection with bounded drawing cost.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-u09"></a>

### U09 · Useful two-channel troubleshooting oscilloscope

**Status:** UNSTARTED.

**Hard prerequisites:** [U03](#m-u03)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Extend the useful one-channel scope when current content benefits from simultaneous observations.

**Architectural owner / affected systems:** Shared observation/sampling service, scope channels and workbench presentation.

**Must not be coupled:** No general laboratory suite, network scope, mains isolation certification or mandatory U10/U11/U12 dependency.

**Exact deliverable:** Two independently scaled channels on one simulation-time base; selectable edge trigger; qualified AC/DC coupling, reference models and differential math; frequency/period and bounded retained waveforms.

**Acceptance:** Both traces come from actually loaded electrical inputs. Channel skew, aliasing and insufficient windows are explicit. Shared grounds follow the selected instrument model; differential subtraction does not create isolation. Freeze/resume, channel removal, power, probe, mutation and owner changes handle buffer provenance correctly.

**Important negative tests:** False isolation; channel cross-wiring; unrelated acquisition clocks; stale capture after owner replacement; one channel silently drops loading; clipped/undersampled data presented as exact.

**Performance / scalability evidence:** Two-channel sampled fixtures, phase/time alignment, buffer caps and input/frame latency during solving.

**Architectural risk:** Duplicating the scope engine or confusing display history with simulation state.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-u10"></a>

### U10 · Conditional logic probe and small logic capture

**Status:** UNSTARTED.

**Hard prerequisites:** [U02](#m-u02), [A07](#m-a07), [E10](#m-e10)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Supply a legal diagnostic observation when qualified digital content actually needs one.

**Architectural owner / affected systems:** Reference-aware electrical observation providers and bounded logic presentation.

**Must not be coupled:** No protocol decoder ecosystem, HDL simulator or blanket release dependency; enable only with a consuming family.

**Exact deliverable:** First a logic probe with LOW/HIGH/unknown/pulse states; then bounded 2-4-channel edge capture only when a demonstrated current diagnostic use warrants it. Declare loading, thresholds, hysteresis, range, reference and sampling.

**Acceptance:** Logic states and transitions derive from solved pin voltages and simulation time. Unpowered, floating, ambiguous and out-of-range inputs stay explicit. Capture rate/depth is bounded; reset, cancellation, probe moves and owner changes are coherent. No private-register reading substitutes for pin measurement.

**Important negative tests:** Global 5 V threshold assumption; floating input called LOW; frame-rate edge counting; omitted probe loading; hidden latch revealed instead of terminal voltage.

**Performance / scalability evidence:** Threshold/range sweeps, clock/edge fixtures, capacity/exhaustion and current diagnostic playthrough.

**Architectural risk:** A metadata-only digital instrument or unbounded event log.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-u11"></a>

### U11 · Conditional bench signal generation and diagnostic injection

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [U02](#m-u02), [A08](#m-a08)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Allow player-chosen causal stimulus when a real analog or control diagnostic need exists.

**Architectural owner / affected systems:** Owned source/instrument provider and the current mutation/measurement lifecycle.

**Must not be coupled:** No arbitrary signal library or required feature for boards that do not need injection.

**Exact deliverable:** Bounded waveform types with amplitude, offset, frequency, reference, impedance and voltage/current limits; legitimate physical attachment points and owned removal.

**Acceptance:** Injection changes the solved circuit and loaded response. Source conflict, partial power, overload and cancellation have explicit modeled or unsupported outcomes. Ordinary controls select the experiment; private scenario metadata never chooses the correct signal.

**Important negative tests:** Decorative waveform; source remains after exit; wrong owner mutated; injection bypasses isolation; ideal unlimited driver where finite impedance was promised.

**Performance / scalability evidence:** Healthy/faulted stimulus fixtures, loaded response, cancellation/cleanup and one current diagnostic benefit.

**Architectural risk:** A second source owner or generator UI determining electrical behavior.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-u12"></a>

### U12 · Conditional current insertion and advanced DMM diagnostics

**Status:** UNSTARTED.

**Hard prerequisites:** [U02](#m-u02), [A08](#m-a08), [E01](#m-e01)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Fill real current-measurement or ESR diagnostic gaps rather than adding misleading readouts.

**Architectural owner / affected systems:** Active-instrument source/connection model, protected insertion and DMM UI.

**Must not be coupled:** No all-modes requirement. X08 remains the separate conditional capacitance measurement lane.

**Exact deliverable:** Current mode uses an actual in-series path with declared burden, range/overrange, reference and protection. ESR is separately conditional on a qualified capacitor model and uses a bounded electrical stimulus/response method.

**Acceptance:** Current comes from the inserted instrument branch, not arbitrary private branch metadata. Misconnection has the declared modeled consequence or is explicitly unsupported. ESR states frequency, charge-readiness, uncertainty and parallel-path limits and is never a configured-value readout.

**Important negative tests:** Parallel ammeter treated as harmless; missing burden; retained test source; hidden ESR field returned as a measurement; charged capacitor treated as ready.

**Performance / scalability evidence:** Current/range/burden and misconnection fixtures; separate ESR electrical and diagnostic evidence if enabled.

**Architectural risk:** Bundling unrelated modes or implying precision outside a supported physical model.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-e01"></a>

### E01 · Source limits, external loads and protection foundation

**Status:** UNSTARTED.

**Hard prerequisites:** [A06](#m-a06), [A07](#m-a07), [A08](#m-a08)

**Priority / applicability:** Required foundation

**Purpose and reason:** Establish electrical consequences before richer faults and player wires can create shorts or overloads.

**Architectural owner / affected systems:** Source/load providers, CircuitJS current-limit behavior, protection/fuse models and damage observations.

**Must not be coupled:** No mains certification, complete power-electronics library or dependency on trace repair.

**Exact deliverable:** Bounded low-voltage sources and loads, true disconnect, current limit/readout, elementary protection and declared stress response; real bench controls where scenario permits.

**Acceptance:** Limiting changes the solved circuit rather than clamping displayed current; backfeed and partial isolation behave consistently; unsafe simulated actions have modeled consequences or an explicit unsupported-operation result.

**Important negative tests:** Ideal source short accepted without consequence; limiter only changes UI; all-sources-off computed from one source; fuse reset silently heals a physical failed part.

**Performance / scalability evidence:** Short/overload/load-step fixtures, energy and settlement checks, real user power controls.

**Architectural risk:** Fake protection or uncontrolled ideal-source singularities.

**Expected extension and scale effects:** Pluggability: reusable source and protection contracts. Scale: multi-source realism without per-device safety hacks.

**Direct hard dependents:** [U11](#m-u11), [U12](#m-u12), [E02](#m-e02), [E03](#m-e03), [E05](#m-e05), [E08](#m-e08), [E11](#m-e11), [E13](#m-e13), [E14](#m-e14), [IMPORT-1](#m-import-1), [Q15](#m-q15), [X02](#m-x02).

<a id="m-e02"></a>

### E02 · Rail-producing regulator implementations

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [A04](#m-a04), [A05](#m-a05)

**Priority / applicability:** Required foundation

**Purpose and reason:** Provide real reusable 12/5/3.3 V rail behavior with distinct implementation choices.

**Architectural owner / affected systems:** Regulation block families, value/rating recipes, source/load assumptions and model providers.

**Must not be coupled:** No offline converter design, arbitrary buck magnetics optimizer or automatic support for every nominal rail.

**Exact deliverable:** At least a bounded linear regulation implementation and an explicitly qualified alternative or averaged switching variant; enable/dropout/load limits and physical component mappings.

**Acceptance:** Output follows input, load, reference, enable and limits through the solver; different variants meet the same declared role under different assumptions; unsupported operating regions reject.

**Important negative tests:** Hidden ideal output source independent of input; output short not reflected at input; power created without a declared model balance; misleading switching waveform from an average model.

**Performance / scalability evidence:** Input/load/enable sweeps, dissipation and timestep sensitivity, per-variant diagnostic conformance.

**Architectural risk:** A convenient abstraction hiding the failures technicians need to diagnose.

**Expected extension and scale effects:** Pluggability: interchangeable qualified rail roles. Scale: multi-rail systems without family clones.

**Direct hard dependents:** [E04](#m-e04), [E06](#m-e06), [IMPORT-4](#m-import-4), [Q30](#m-q30).

<a id="m-e03"></a>

### E03 · Relay and switched-output families with alternate drivers

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [A05](#m-a05), [A08](#m-a08), [P01](#m-p01)

**Priority / applicability:** Required foundation

**Purpose and reason:** Prove electromechanical composition and driver diversity on a useful low-voltage controlled load.

**Architectural owner / affected systems:** Relay package/model, BJT/NMOS driver providers, protection and fault/mutation contracts.

**Must not be coupled:** No contact arcing/EMC certification or mains load admission before E05 and the applicable instruments.

**Exact deliverable:** Coil/contact separation, genuine inductive/flyback behavior in the declared envelope, at least two driver variants and meaningful coil/contact/driver fault owners.

**Acceptance:** Contacts do not join control and load domains; physical terminals map correctly; coil and contact failures are distinguishable with available actions; replacement and energized/de-energized retest work.

**Important negative tests:** Shared return introduced across relay isolation; incorrect terminal order; off-contact leakage ignored by a claimed diagnostic; missing flyback behavior; driver variant lacks control drive.

**Performance / scalability evidence:** Existing RelayElm limitations explicitly qualified; small dynamic fixtures plus normal physical repair and retest.

**Architectural risk:** Assuming an inherited approximate relay model already supports all requested diagnostic mechanisms.

**Expected extension and scale effects:** Pluggability: real variant and multi-terminal provider proof. Scale: reusable output channels, not copied device branches.

**Direct hard dependents:** [E14](#m-e14), [IMPORT-4](#m-import-4), [Q15](#m-q15), [Q30](#m-q30).

<a id="m-e04"></a>

### E04 · Sensor conditioning, references and control decisions

**Status:** UNSTARTED.

**Hard prerequisites:** [E02](#m-e02), [A05](#m-a05), [A09](#m-a09)

**Priority / applicability:** Required foundation

**Purpose and reason:** Add unfamiliar but understandable control behavior beyond a single high/low switch.

**Architectural owner / affected systems:** Sensor/stimulus, conditioning, divider/reference and comparator/interlock providers.

**Must not be coupled:** No broad op-amp library, unbounded noisy analog campaign or fake sensor animation.

**Exact deliverable:** Player-operated sensor conditions, at least two compatible conditioning/control implementations, explicit loading/hysteresis where modeled and bounded fault loci.

**Acceptance:** A player can sweep relevant conditions and observe actual threshold/state behavior; thresholds and references are solver-backed; healthy support has purpose; assumptions compose with the selected rail.

**Important negative tests:** Hidden stimulus only verifier can set; comparator output asserted from metadata; undefined reference; ambiguous failed sensor versus missing supply with no legal separating observation.

**Performance / scalability evidence:** Boundary/threshold/loading matrices, diagnostic plans and ordinary input/retest workflows.

**Architectural risk:** A general state-machine engine introduced before concrete control uses establish its contract.

**Expected extension and scale effects:** Pluggability: alternate sensor/control roles. Scale: realistic interactions and richer fault hypotheses.

**Direct hard dependents:** [Q30](#m-q30).

<a id="m-e05"></a>

### E05 · AC input, rectification, bulk energy and isolation qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [A06](#m-a06), [U02](#m-u02), [P02](#m-p02)

**Priority / applicability:** Required foundation

**Purpose and reason:** Build the physical/electrical mains foundation before an appliance board can be advertised.

**Architectural owner / affected systems:** AC source, bridge/diode, capacitor, transformer/isolation, protection and measurement providers.

**Must not be coupled:** No physical high-voltage construction guide, actual equipment safety certification, EMI/filter-attenuation claim or complete offline converter yet.

**Exact deliverable:** Bounded simulated 120 VAC input path, rectification/bulk storage, discharge behavior, separate reference domains and traceable safety-related physical constraints without certification claims.

**Acceptance:** Waveform conventions and energy state are explicit; isolated returns are not joined by numerical stabilization; AC/DC instruments show supported observations; each visible protection/power component has a causal modeled role.

**Important negative tests:** RMS/peak convention mismatch; output alive with disconnected source and no energy source; bulk capacitor instantly cleared by OFF; isolated domains silently grounded; NPTH or opposite-layer copper bypasses barrier.

**Performance / scalability evidence:** Small independently checked AC/rectifier/storage/isolation fixtures and model sensitivity before RB56 integration.

**Architectural risk:** Teaching false reference or discharge behavior because simplified models were not qualified.

**Expected extension and scale effects:** Pluggability: foundational mains-side providers. Scale: credible mixed-domain composition.

**Direct hard dependents:** [E06](#m-e06), [E13](#m-e13), [Q60](#m-q60), [X08](#m-x08).

<a id="m-e06"></a>

### E06 · Causal offline-converter model and fidelity decision gate

**Status:** UNSTARTED.

**Hard prerequisites:** [E05](#m-e05), [E02](#m-e02), [A07](#m-a07), [U03](#m-u03), [A08](#m-a08)

**Priority / applicability:** Required advanced-board blocker and early model decision

**Purpose and reason:** Test the hardest appliance-board model before a 56-part product is built around an ideal-supply shortcut.

**Architectural owner / affected systems:** Power-conversion block/model provider, fidelity contract and independent electrical/diagnostic qualification.

**Must not be coupled:** No commercial SMPS design tool, magnetics optimization, EMC certification or hidden external physics engine replacing CircuitJS.

**Exact deliverable:** An isolated offline-conversion proof with input dependence, startup/enable/feedback/load response, power-flow assumptions and bounded fault response. Compare detailed versus averaged implementations on explicit observables.

**Acceptance:** Every visible external switch, transformer, rectifier, capacitor and feedback part participates causally; omitted dynamics are disclosed and unavailable as observations/faults; an opaque module counts as one package, not twelve decorative parts.

**Important negative tests:** Feedback resistor disconnected with no effect; input fuse open but output remains powered; averaged model displays invented switch pulses; isolated transformer drawn over shared ground; unsupported oscillatory transient declared stable.

**Performance / scalability evidence:** Small high-risk model pilots run as early as A07 permits; E06 freezes fidelity, convergence and runtime evidence before Q60. Include input/output energy and reference sensitivity.

**Architectural risk:** A model that is fast but diagnostically dishonest, or too detailed to meet the required interactive budget.

**Expected extension and scale effects:** Pluggability: explicit fidelity-aware converter implementations. Scale: enables realistic mains regions without transistor-level modeling of everything.

**Direct hard dependents:** [Q60](#m-q60).

<a id="m-e07"></a>

### E07 · Triggered timers, oscillators and frequency behavior

**Status:** UNSTARTED.

**Hard prerequisites:** [U03](#m-u03), [A05](#m-a05), [A09](#m-a09)

**Priority / applicability:** Required foundation

**Purpose and reason:** Add temporal difficulty only after players have truthful ways to observe it.

**Architectural owner / affected systems:** Timer/oscillator block variants, trigger/stimulus capabilities and temporal diagnostic contracts.

**Must not be coupled:** Not required for static Q30/Q60 variants unless their advertised behavior uses it; no MCU/protocol framework.

**Exact deliverable:** One triggered timing role and one periodic-signal role with bounded distinct implementations, solver-time behavior and repairable faults.

**Acceptance:** Timing/frequency derive from solved state; player triggers and sampling windows can reproduce the diagnostic plan; unsupported high-frequency behavior remains unavailable.

**Important negative tests:** Configured component value returned as measured frequency; hidden trigger; timing based on paint speed; aliasing mistaken for real fault; reset erases unexplained stored state.

**Performance / scalability evidence:** Step-size and bandwidth qualification plus normal trigger/probe/retest sequences.

**Architectural risk:** Inventing a scripted logic simulator beside CircuitJS or assuming all temporal faults are separable.

**Expected extension and scale effects:** Pluggability: temporal providers use existing role/instrument seams. Scale: useful complexity without unrestricted digital systems.

**Implementation vocabulary:** Include a bounded 555-style implementation among timing alternatives where appropriate. Real supplies, timing/control pins, loading, reset and supported operating limits apply. A known part label is not an exact commercial-model guarantee.

**Direct hard dependents:** [IMPORT-4](#m-import-4).

<a id="m-e08"></a>

### E08 · Player repair jumpers, copper cuts and physical restoration

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [A08](#m-a08), [P02](#m-p02), [U01](#m-u01), [P09](#m-p09)

**Priority / applicability:** Planned important capability; required only for consuming content or advertised operations.

**Purpose and reason:** Add real player copper repair operations when content consumes them. Component-only advanced boards do not depend on this capability.

**Architectural owner / affected systems:** Mutation-owned conductor graph, player wire inventory, accessible-surface targets and solver projection.

**Must not be coupled:** No arbitrary CAD editing, automatic correct repair, infinite current protection or claim of supported saved repair state until U06 requalification.

**Exact deliverable:** Distinct player jumper objects; selected copper-edge cuts; direct restoration or bypass; layer/side access; history/save provider extensions and functional retest.

**Acceptance:** Cut opens the selected physical edge, preserving other same-net branches; wrong wiring has real consequences; permitted alternative repairs can pass; flip/zoom/save do not retarget actions; underpasses remain physically distinct.

**Important negative tests:** Cut all logical net branches; cut hidden underside from top view; factory link confused with player wire; renamed segment after simplification; jumper destroys unrelated probe identity.

**Performance / scalability evidence:** Branched nets, plated pads, vias, parallel paths and repair-equivalent outcomes; actual visible operation and failure rollback.

**Architectural risk:** A renderer-owned cut system or persistence tied to route array indices.

**Expected extension and scale effects:** Pluggability: actions consume conductor contracts. Scale: one repair model for both one- and two-layer boards.

**Type and scope:** Planned important repair capability; required only for content or release claims that consume cuts, repair jumpers, copper restoration or bypass actions.

**Dependency condition:** Q60 and Q100 do not have an unconditional E08 prerequisite. A specific trace-repair family adds E08 and requalifies its current session/history providers.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-e09"></a>

### E09 · Powered analog and combinational IC provider vocabulary

**Status:** UNSTARTED.

**Hard prerequisites:** [A05](#m-a05), [A06](#m-a06), [A08](#m-a08), [A09](#m-a09), [U02](#m-u02), [P01](#m-p01)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Add useful small ICs without bypassing real supplies, loading, limits or physical ownership.

**Architectural owner / affected systems:** Powered-IC electrical providers, shared-package/pin maps and package-level repair.

**Must not be coupled:** No complete commercial library or exact-device fidelity promise; no automatic prerequisite for base relay-only boards.

**Exact deliverable:** Incremental analog and combinational groups: comparator/Schmitt, op-amp, buffer/follower, amplification/reference/filter variants; inverter, AND/OR/NAND/NOR/XOR and Schmitt logic. Select bounded representative implementations and publish exactly which are qualified.

**Acceptance:** Actual supply/input/output pins, common-mode and supply ranges, finite drive, thresholds, saturation and power-loss behavior match the model. Multi-unit packages have one physical owner and honest shared pins. At least one analog and one combinational circuit pass physical probing, meaningful fault, package repair and retest.

**Important negative tests:** Ideal output while unpowered; separate replacement of one internal unit; swapped shared supply; missing loading; out-of-range operation reported as exact.

**Performance / scalability evidence:** Small powered/unpowered/input/load sweeps, package/unit mapping checks and integrated workbench examples.

**Architectural risk:** An IC label becoming a shortcut to hidden ideal behavior.

**Direct hard dependents:** [E10](#m-e10), [E12](#m-e12), [MCU-2](#m-mcu-2), [IMPORT-3](#m-import-3).

<a id="m-e10"></a>

### E10 · Sequential logic, clocks and reset-state vocabulary

**Status:** UNSTARTED.

**Hard prerequisites:** [E09](#m-e09), [A07](#m-a07), [U03](#m-u03)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Make state, clocks and reset causal and diagnosable before stateful control content grows.

**Architectural owner / affected systems:** Stateful electrical providers on the shared simulation-time event boundary.

**Must not be coupled:** No HDL engine, arbitrary firmware, RF/metastability claim or automatic full MCU requirement.

**Exact deliverable:** Incremental representative SR latch, D flip-flop, useful JK flip-flop, counter and shift-register providers; explicit reset/set/clock/startup priority and output loading. Qualified crystal/resonator, oscillator-module or RC alternatives only where consumed.

**Acceptance:** Transitions occur on declared electrical and simulation-time events, not paint frames or nonlinear trial iterations. Missing selected external clock stops the function; power/reset loss is causal. Indeterminate/out-of-envelope regions are honest. Current resume is qualified when advertised and future reference variants exercise stateful logic with observable output.

**Important negative tests:** Double count per solver iteration; ignored reset; phantom external crystal; output held indefinitely after power loss; lost phase on claimed exact resume.

**Performance / scalability evidence:** Event-order, timestep and clock-loss fixtures, finite feedback budgets and current probe/scope diagnosis.

**Architectural risk:** Creating a second clock or electrical truth through a UI event loop.

**Direct hard dependents:** [U10](#m-u10), [MCU-2](#m-mcu-2), [IMPORT-3](#m-import-3).

<a id="m-e11"></a>

### E11 · Seven-segment and simple scanned-display providers

**Status:** UNSTARTED.

**Hard prerequisites:** [A05](#m-a05), [A08](#m-a08), [E01](#m-e01), [P01](#m-p01), [U01](#m-u01)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Show actual electrically driven status without reading the intended answer from scenario data.

**Architectural owner / affected systems:** Physical display packages, electrical segment/drive models and render projections.

**Must not be coupled:** No graphical LCD/OLED stack; scanning consumers add E10/A07 or MCU capabilities only when actually used.

**Exact deliverable:** Single-digit seven-segment package with common-anode and common-cathode variants, real pins, current limiting and meaningful open-segment faults; later bounded scanning, drivers, arrays/bargraphs and status codes as needed.

**Acceptance:** Pin maps, polarity and segment currents agree across solver, package, renderer and probe targets. Multiplexed brightness is a bounded view of actual digit/segment drive history; rendering cannot advance the model. Supply, common, segment, driver and scan failures have causal supported symptoms and package/part repair.

**Important negative tests:** Desired numeral rendered without current; wrong common polarity; unlimited drive; invisible scan loss; inaccessible common pin; stale brightness after removal.

**Performance / scalability evidence:** Static segment-current fixtures, optional duty-cycle/scan evidence and one integrated current display diagnosis.

**Architectural risk:** A display painted over a state label rather than a circuit.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-e12"></a>

### E12 · Specialized driver, reference and interface IC families

**Status:** UNSTARTED.

**Hard prerequisites:** [E09](#m-e09), [A06](#m-a06), [U02](#m-u02)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Add compact practical control vocabulary without one roadmap milestone per part.

**Architectural owner / affected systems:** Demand-selected powered-IC groups using common electrical/physical contracts.

**Must not be coupled:** Only needed groups are implemented; no commercial-device completeness or universal interface support.

**Exact deliverable:** Incremental ULN2003-style driver arrays; analog switches and mux/demux; references/current-sense amplifiers; optocouplers and qualified isolated interfaces; simple display/power drivers; reset/brownout supervisors.

**Acceptance:** Each chosen group declares supplies, shared pins, references, finite loading/drive, enable/selection and supported limits. Optocoupler sides stay electrically separate but causally linked. A mux routes an actual modeled path. Arrays have one replaceable package with honest channel semantics.

**Important negative tests:** Optocoupler joins grounds; ideal array with no common/supply effect; disconnected enable; channels counted as separate packages; out-of-envelope voltage magically clipped.

**Performance / scalability evidence:** Per-group current/load/isolation and control sweeps plus meaningful fault/serviceability checks.

**Architectural risk:** A broad catalog without qualified electrical or repair behavior.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-e13"></a>

### E13 · AC semiconductor switching and zero-cross control

**Status:** UNSTARTED.

**Hard prerequisites:** [E05](#m-e05), [E01](#m-e01), [A05](#m-a05), [A08](#m-a08), [A09](#m-a09), [U03](#m-u03)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Enable causal AC-control troubleshooting when a selected family needs it.

**Architectural owner / affected systems:** SCR/TRIAC, optically coupled control, zero-cross observation and supported load providers.

**Must not be coupled:** Not a prerequisite for relay-only Q60; no mains hardware certification or unrestricted switching model.

**Exact deliverable:** Representative SCR/TRIAC latching paths, optotriac/optocoupler input-output behavior, real zero-cross detection/control and a bounded switched-load application. Explicitly select phase, zero-cross and load modes.

**Acceptance:** Gate drive, holding/latching and commutation follow actual modeled voltage/current/time. Isolation and zero-cross signals use real terminals. Drive loss, stuck/open switching and missing zero-cross causes are observable, serviceable and repairable with advertised instruments.

**Important negative tests:** TRIAC follows arbitrary Boolean state; nonzero-current commutation ignored; optotriac shorts domains; render-time zero crossing; absent load dependencies.

**Performance / scalability evidence:** Current/phase/hold and timing fixtures, supported inductive/resistive envelope and normal diagnostic actions.

**Architectural risk:** Implying arbitrary AC/power fidelity from one simple load fixture.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-e14"></a>

### E14 · Bounded reversing, H-bridge and motor/load control

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [E03](#m-e03), [A05](#m-a05), [A08](#m-a08), [A09](#m-a09), [U03](#m-u03)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Support reversing and drive faults through credible electrical consequences.

**Architectural owner / affected systems:** Output-stage implementation variants, current paths and bounded external-load/stimulus models.

**Must not be coupled:** No full motor commutation or plant dynamics unless a separately chosen observation needs them.

**Exact deliverable:** Compare relay reversing, discrete BJT/MOSFET H-bridge and a simple packaged-driver variant for an explicitly supported load. Define enable, direction, permitted sequences, current paths and protection. Add external feedback only when consumed.

**Acceptance:** Solved output/current responds to supply, drive and load. Illegal overlap/shoot-through has declared modeled consequences or is rejected as unsupported rather than silently repaired. Player-operable stimulus and controls make supported faults diagnosable.

**Important negative tests:** Both legs driven with no consequence; independent scenario direction state; missing flyback/return path; invisible off-board feedback required to solve a fault.

**Performance / scalability evidence:** Drive-state/load/partial-power cases, transition/current limits and workbench repair/retest.

**Architectural risk:** Starting a full mechanical motor simulator to support a bounded electrical lesson.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-mcu-1"></a>

### MCU-1 · Bounded microcontroller approach comparison and contract

**Status:** UNSTARTED.

**Hard prerequisites:** [A03](#m-a03), [A06](#m-a06), [A07](#m-a07), [A09](#m-a09)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Choose a small causal control model before building an embedded ecosystem.

**Architectural owner / affected systems:** Electrical execution adapter, provider-owned state and bounded program/model description.

**Must not be coupled:** No arbitrary firmware support, general IDE, CPU-architecture project or protocol ecosystem.

**Exact deliverable:** Compare deterministic behavioral MCU, bounded state-machine/control script, limited emulation of one tiny architecture and justified narrow hybrid using the same GPIO/reset/ADC/PWM/sensor-interlock fixture. Select one method and explicit support envelope.

**Acceptance:** The choice states supply/reset/brownout, pin loading/finite drive, clocks, initialization, time/state ownership, current replay/resume, observables, fault/repair semantics and finite execution budgets. Players can diagnose the black-box function electrically without firmware decompilation.

**Important negative tests:** Program ignores VCC/reset; outputs are ideal private-state values; host time advances device state; arbitrary scripts execute; unsupported peripherals silently succeed.

**Performance / scalability evidence:** Matched pilot observables, execution costs, determinism and integration risks, not speculative benchmark promises.

**Architectural risk:** Choosing an emulator before the required diagnostic fidelity is understood.

**Direct hard dependents:** [MCU-2](#m-mcu-2).

<a id="m-mcu-2"></a>

### MCU-2 · Causal basic MCU runtime and appliance-control integration

**Status:** UNSTARTED.

**Hard prerequisites:** [MCU-1](#m-mcu-1), [E09](#m-e09), [E10](#m-e10), [A08](#m-a08), [U06](#m-u06)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Implement the selected small MCU approach through the ordinary native game pipeline.

**Architectural owner / affected systems:** One selected bounded MCU provider, shared simulation events and current session reconstruction.

**Must not be coupled:** Only the selected bounded program/peripherals; no arbitrary firmware or automatic display/import dependency.

**Exact deliverable:** Powered/unpowered/reset and modeled brownout states; GPIO directions/pulls/thresholds/finite drive; limited ADC-like inputs; PWM/timers; deterministic startup and a sensor/interlock/output application. Include physical board integration and current-format resume/replay.

**Acceptance:** VCC/reset/selected clock and input changes causally alter behavior. Loaded outputs are measured electrically. External pulls, clocks, reset networks, sensors and drivers affect the function when shown. State reconstruction matches the declared current semantics/tolerances. A meaningful fault is diagnosed and repaired without private firmware state.

**Important negative tests:** External reset misclassified as dead MCU; invisible ideal drive; removed MCU schedules new events; duplicated resume timer; unsupported state falsely resumed exactly.

**Performance / scalability evidence:** Supply/reset/clock/load/state sweeps and one ordinary-player board diagnosis, repair, retest and supported resume.

**Architectural risk:** A second live simulator, timer owner or private answer source inside the MCU wrapper.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-import-1"></a>

### IMPORT-1 · CircuitJS import foundation and passive/DC challenge proof

**Status:** UNSTARTED.

**Hard prerequisites:** [A03](#m-a03), [A04](#m-a04), [A08](#m-a08), [A09](#m-a09), [A10](#m-a10), [A11](#m-a11), [P04](#m-p04), [U01](#m-u01), [U02](#m-u02), [U04](#m-u04), [E01](#m-e01)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Turn a finite supported passive/DC input into an honest playable challenge, not merely parse a schematic.

**Architectural owner / affected systems:** Isolated import interpretation and authoring adapter feeding the native resolved-plan pipeline.

**Must not be coupled:** No arbitrary CircuitJS file support, community catalog or unrestricted parser resources.

**Exact deliverable:** Retained source bytes/hash/current parser identity; six-way per-element capability accounting; stable import-local IDs and package/unit/pins; source/control/load roles; author-declared healthy states/outputs; native PCB, seeded serviceable fault and complete diagnosis/repair/retest.

**Acceptance:** All twelve import steps in Section 3 apply. Parsing cannot mutate current play or execute embedded content. Every element is accounted for. Connectivity is interpreted before physical placement; ambiguous role/intent requires author input. Healthy function precedes faults. Final physical, diagnostic and current-reproduction checks use native services.

**Important negative tests:** Dropped unsupported part; schematic coordinate used as repair ID; missing healthy intent guessed; hidden ideal supply; script/network side effect; import-only shortcut around native validation.

**Performance / scalability evidence:** Varied finite passive/DC corpus with failures, capability reports and one full playable vertical slice.

**Architectural risk:** A parallel imported-only assembler or false universal-import promise.

**Direct hard dependents:** [IMPORT-2](#m-import-2).

<a id="m-import-2"></a>

### IMPORT-2 · Diode, transistor and RC import support

**Status:** UNSTARTED.

**Hard prerequisites:** [IMPORT-1](#m-import-1), [A05](#m-a05)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Expand the supported subset without weakening the complete import-to-play contract.

**Architectural owner / affected systems:** Current nonlinear/temporal import mappings into existing native providers.

**Must not be coupled:** No arbitrary analog models; unsupported elements retain explicit capability results.

**Exact deliverable:** Selected diode/transistor/RC construct mappings, actual model/polarity correspondence, explicit package choices and initial-state interpretation; supported causal faults and serviceability.

**Acceptance:** Each admitted design has declared inputs, outputs and healthy conditions with real qualified nonlinear/temporal behavior. Preserve the requested source model parameters within the current interpretation or explicitly reject unsupported ones. Charge/readiness, package mapping, mutation and faults pass native checks.

**Important negative tests:** Reversed diode silently corrected; unsupported transistor model replaced without declaration; charged capacitor initialized incorrectly; source edit reuses stale mapping.

**Performance / scalability evidence:** Model/polarity/timing fixtures and varied small current nonlinear import playthroughs.

**Architectural risk:** Treating syntax support as electrical or physical support.

**Direct hard dependents:** [IMPORT-3](#m-import-3), [IMPORT-4](#m-import-4).

<a id="m-import-3"></a>

### IMPORT-3 · Supported small-IC and control-circuit imports

**Status:** UNSTARTED.

**Hard prerequisites:** [IMPORT-2](#m-import-2), [E09](#m-e09), [E10](#m-e10)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Import useful small control circuits without inventing package or supply semantics.

**Architectural owner / affected systems:** IC/unit/package interpretation, powered/stateful providers and author intent.

**Must not be coupled:** MCU, display and timer capability is added only for inputs that actually consume it; no blanket IMPORT-4 dependency.

**Exact deliverable:** Selected IC/control mappings including multi-unit packages, shared supplies, clocks/reset/startup and sources/controls/outputs. Classify electrically supported but nonserviceable constructs explicitly.

**Acceptance:** Physical package/pin/unit ownership and powered/stateful behavior remain truthful. Healthy/fault proof uses supported observations and simulation time. Hidden supplies are mapped honestly or rejected. Missing customer intent needs author input. Current artifacts record actual interpretation and model state.

**Important negative tests:** Internal units split into replaceable packages; floating hidden supply made ideal; private IC register used for diagnosis; unsupported timing implicitly assumed.

**Performance / scalability evidence:** Small IC holdout fixtures, supply/reset/timing variation and supported current physical repair.

**Architectural risk:** An importer that recognizes an IC name but cannot represent a real supported package.

**Direct hard dependents:** [IMPORT-5](#m-import-5).

<a id="m-import-4"></a>

### IMPORT-4 · Multi-rail, relay and dynamic-circuit imports

**Status:** UNSTARTED.

**Hard prerequisites:** [IMPORT-2](#m-import-2), [E02](#m-e02), [E03](#m-e03), [E07](#m-e07), [U03](#m-u03), [P09](#m-p09)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Support declared dynamic circuits and multiple references through usable current instruments.

**Architectural owner / affected systems:** Power/dynamic source and initial-state interpretation with shared native qualification.

**Must not be coupled:** Mains/offline inputs add E05/E06 only when consumed; no automatic serial dependence on IMPORT-3.

**Exact deliverable:** Qualified multi-rail/relay/dynamic mappings, source/reference and initial-state annotations, finite customer operating sequences and instrument requirements. Use native bounded jobs and physical envelope.

**Acceptance:** Partial power, contention, backfeed, stored energy and clock/switch observations match the supported model. Customer sequences define healthy and faulty function. Necessary controls are player-accessible and layer/copper/repair behavior remains native.

**Important negative tests:** Isolated returns merged by label; unsupported stored energy discarded; required control unavailable; imported model bypasses source protection; current interpretation uses stale cached proof.

**Performance / scalability evidence:** Varied supported dynamic sequences, reference sensitivity, current physical/playable correspondence and bounded work.

**Architectural risk:** A large successful solve being mistaken for a qualified diagnosable challenge.

**Direct hard dependents:** [IMPORT-5](#m-import-5).

<a id="m-import-5"></a>

### IMPORT-5 · Broad supported CircuitJS subset and community qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [IMPORT-3](#m-import-3), [IMPORT-4](#m-import-4), [A11](#m-a11), [U06](#m-u06)

**Priority / applicability:** Planned later or conditional capability. Required only for its selected consuming content/advertised support; not a blanket base-release prerequisite.

**Purpose and reason:** Publish a broad but explicit usable subset instead of claiming every file imports.

**Architectural owner / affected systems:** Import support matrix, author workflow, current portable artifacts and holdout qualification.

**Must not be coupled:** No full community ecosystem, arbitrary source execution or unconditional dependency for native releases.

**Exact deliverable:** Element/import-feature matrix generated from current capabilities, varied community-style corpus, deterministic reports, usable author annotation and source-plus-current-manifest sharing. Declare actual qualified physical scale bands.

**Acceptance:** Every included element/model either enters the shared pipeline truthfully or has an exact unsupported/needs-input result. No silent drops or electrical rewrites. Healthy intent, seeded faults, diagnosis/serviceability and physical repair pass on the chosen subset. Incompatible old interpretations/formats may reject; no historical reader is required.

**Important negative tests:** Hash with no source bytes; known unsupported constructs disappear; scale inferred from parsing; unknown interpretation accepted; source/answer leaks through normal share text.

**Performance / scalability evidence:** Frozen varied holdout including failures, author usability, bounded artifact size and current reconstruction.

**Architectural risk:** Scope expanding to universal import or indefinite parser/model compatibility.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-d01"></a>

### D01 · Scalable diagnostic partitions and context-valid proof reuse

**Status:** UNSTARTED.

**Hard prerequisites:** [A09](#m-a09), [A10](#m-a10), [Q15](#m-q15)

**Priority / applicability:** Required foundation

**Purpose and reason:** Prevent proof cost from overtaking simulation while keeping the entire admitted hypothesis contract honest.

**Architectural owner / affected systems:** Production diagnostic planner, observation partitions, proof receipt cache and serial reference verifier.

**Must not be coupled:** No shared mutable solver-state cache, brute-force every conceivable fault or random candidate sampling presented as exhaustive admission.

**Exact deliverable:** Structural/serviceability pruning; provider-local evidence with global context checks; bounded adaptive observation plans; valid repair equivalence; complete-key receipt reuse.

**Acceptance:** Every retained hypothesis is covered by a legal separating plan or genuine equivalent repair class; sampled testing never certifies unsampled hypotheses; context changes invalidate local receipts; candidate order is deterministic.

**Important negative tests:** Locally healthy block loaded differently in device; cache omits source state/model version; identical observations but incompatible repair; arbitrary top-N candidate truncation; shortcut bypasses final global proof.

**Performance / scalability evidence:** Hypotheses/observations/solves/repairs and cold/warm time versus simple serial proof on RB15 and larger evolving fixtures.

**Architectural risk:** Fast-looking admission achieved by weakening the claim or a planner becoming another centralized family switch.

**Expected extension and scale effects:** Pluggability: diagnostic contracts stay provider-owned. Scale: fewer repeated full installs without unsafe same-owner reuse.

**Direct hard dependents:** [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [X01](#m-x01), [X05](#m-x05), [X06](#m-x06).

<a id="m-q15"></a>

### Q15 · Heterogeneous 15-part procedural control-board qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [A05](#m-a05), [A08](#m-a08), [A10](#m-a10), [A11](#m-a11), [P03](#m-p03), [P04](#m-p04), [U01](#m-u01), [E01](#m-e01), [E03](#m-e03)

**Priority / applicability:** Required scale gate

**Purpose and reason:** Prove the construction/ownership architecture with a useful board larger than the old bounded indicator.

**Architectural owner / affected systems:** RB15 device intent and independent integrated qualification across generation, physical, solver, diagnostic and player layers.

**Must not be coupled:** No mains, two-layer normal-play requirement, scoring or advanced profile needed for this gate.

**Exact deliverable:** A real approximately 15-part low-voltage multi-block board with purposeful support, relay/control/load behavior and at least two structurally different qualified implementations.

**Acceptance:** Manifest-to-playable stages pass; the same functional intent yields different internal designs; every counted package is causal; all admitted faults have legal repair/retest; held-out seeds remain within the declared 5-20-part envelope.

**Important negative tests:** Authored full-board layout disguised as general procedural proof; copied tile; unsupported hypothesis; inaccessible terminal; fixed answer despite changed internal topology.

**Performance / scalability evidence:** Generation/route/proof distributions and normal-player workflows on the named machine. Record authored references separately.

**Architectural risk:** Calling a single fixture an entire supported range or satisfying diversity with resistor values alone.

**Expected extension and scale effects:** Pluggability: first integrated extension proof. Scale: routine-small envelope, not yet 40/60/100 support.

**Direct hard dependents:** [U05](#m-u05), [U08](#m-u08), [D01](#m-d01), [Q30](#m-q30), [REL-A](#m-rel-a).

<a id="m-q30"></a>

### Q30 · Normal 20-40-part multi-rail procedural qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [Q15](#m-q15), [E02](#m-e02), [E03](#m-e03), [E04](#m-e04), [D01](#m-d01), [P09](#m-p09), [U02](#m-u02)

**Priority / applicability:** Required normal-medium gate

**Purpose and reason:** Prove the middle scale where multiple regions, domains and alternate implementations become normal.

**Architectural owner / affected systems:** RB30 device-intent family and integrated supported-envelope qualification.

**Must not be coupled:** No need for every future instrument or advanced intermittent/damage feature; temporal variants require E07/U03 when used.

**Exact deliverable:** Approximately 30-part heterogeneous multi-rail control board plus a held-out 20-40-part corpus, repeated output channels and structurally different implementations of shared roles.

**Acceptance:** Generation, routing, domain behavior, partial power, normal diagnostics and repair meet the frozen budget; no generic layer gains device-specific branches; selected layer strategy is inspectable and comprehensible.

**Important negative tests:** Only easy flat netlists; per-family geometry exceptions; backfeed ignored; cross-domain meter error; deferred unproved hypothesis admitted to keep success rate high.

**Performance / scalability evidence:** Cold/warm p50/p95, failure distribution, matrix/proof counts, navigation/probe trials and extension-cost ledger.

**Architectural risk:** Provider-local proofs assumed sufficient for interacting multi-rail loads.

**Expected extension and scale effects:** Pluggability: ordinary mixed content uses the same services. Scale: normal medium support becomes evidence, not expectation.

**Direct hard dependents:** [U07](#m-u07), [Q60](#m-q60).

<a id="m-rel-a"></a>

### REL-A · Limited desktop alpha: architecture learning release

**Status:** UNSTARTED.

**Hard prerequisites:** [Q15](#m-q15), [U04](#m-u04), [U05](#m-u05), [A11](#m-a11)

**Priority / applicability:** Required intermediate release gate

**Purpose and reason:** Obtain real player feedback without pretending the final large-board target has already been achieved.

**Architectural owner / affected systems:** Release qualification over the accepted small-board profile and session surface.

**Must not be coupled:** No scoring, economy, mobile, all instruments, Q100 or PSYCHOTIC required.

**Exact deliverable:** Explicit low-voltage 5-20-part alpha support statement, EASY/MEDIUM availability, usable Resources/Settings, replay and honest catalog/navigation.

**Acceptance:** Advertised current routes pass electrical, physical, diagnosis/repair/retest, privacy and focus checks. Record current limitations and reproducible bug-report identity. No unsupported mains, layers, profiles or historical save/challenge support is advertised.

**Important negative tests:** Developer entry mistaken for normal play; unknown replay silently substituted; modal input reaches hidden board; mock feature presented as gameplay.

**Performance / scalability evidence:** Curated normal-player acceptance corpus, selected browser coverage, source/build identity and failure receipts.

**Architectural risk:** Alpha becoming an excuse to abandon Q60/Q100 or being blocked by unrelated future content.

**Expected extension and scale effects:** Pluggability: checks integrated boundaries. Scale: provides human evidence, not a large-board certificate.

**Direct hard dependents:** [REL-B](#m-rel-b).

<a id="m-q60"></a>

### Q60 · Advanced 40-60-part appliance/control-board qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [Q30](#m-q30), [E05](#m-e05), [E06](#m-e06), [U03](#m-u03), [U07](#m-u07), [P09](#m-p09), [D01](#m-d01)

**Priority / applicability:** Required primary product target

**Purpose and reason:** Deliver the new primary product target, including meaningful mains-side power conversion and low-voltage control/output behavior.

**Architectural owner / affected systems:** RB56 intent family and whole-device electrical/physical/diagnostic qualification.

**Must not be coupled:** No claim of arbitrary 60-part netlists; no multiple faults or every temporal feature required, but every advertised operation/model must be qualified.

**Exact deliverable:** The approximately 56-part reference board and varied 40-60-part derivatives with mains entry, causal isolated conversion, multiple rails, sensors, relays and support; alternative real internal implementations.

**Acceptance:** Each declared reference-board stage passes; package count is honest; mains/isolation/storage/loading behavior meets model contracts; all required measurements and repairs are accessible; admission and interactive budgets pass; HARD calibration can use measured evidence.

**Important negative tests:** Twelve decorative converter parts around an ideal source; averaged model exposes invented switching observations; only selected easy faults proved; hidden underside repair; version mismatch hidden by regeneration.

**Performance / scalability evidence:** Frozen mixed-topology holdout corpus, actual cold generation and normal diagnosis/repair/retest, model/energy sensitivity, long-session and modest-machine performance.

**Architectural risk:** Component count hiding inadequate model fidelity or combinatorial proof cost.

**Expected extension and scale effects:** Pluggability: serious heterogeneous content without core surgery. Scale: real advanced support, not merely structural routing.

**Capability condition:** A component-repair-only RB56 has no E08 prerequisite. Trace cutting, player jumpers, restoration or bypass content adds E08. MCU, displays, importer and extra instruments are dependencies only when the selected family actually consumes them; otherwise the base Q60 remains independent.

**Direct hard dependents:** [REL-B](#m-rel-b), [Q100](#m-q100), [X05](#m-x05), [X06](#m-x06).

<a id="m-rel-b"></a>

### REL-B · Advanced desktop beta and HARD-profile release

**Status:** UNSTARTED.

**Hard prerequisites:** [Q60](#m-q60), [REL-A](#m-rel-a), [U05](#m-u05), [U06](#m-u06), [U07](#m-u07)

**Priority / applicability:** Required advanced release gate

**Purpose and reason:** Release the advanced appliance/control surface only after its full operating and session behavior is credible.

**Architectural owner / affected systems:** Release, profile calibration, source/version manifests and published support envelope.

**Must not be coupled:** PSYCHOTIC, Q100, economy, mobile and optional advanced faults are not beta prerequisites unless advertised.

**Exact deliverable:** Beta support for EASY/MEDIUM/HARD and the qualified 40-60-part family, durable sessions/replay as advertised, source/reference instrumentation and honest layer policy.

**Acceptance:** HARD has a fresh Q60-based calibration receipt; every advertised repair/instrument/source/catalog/save state is validated; unsupported fidelity is documented; non-spoiling bug reports reproduce failures.

**Important negative tests:** Outdated U05 receipt enabling HARD; saved copper actions unsupported by current state provider; failed cold generation hidden behind a warm cached demo; old source graph reused after reset.

**Performance / scalability evidence:** Release matrix of real workflows, browser/performance limits, recovery failures and known defects.

**Architectural risk:** Treating feature checklist completion as reliability or letting release labels weaken evidence.

**Expected extension and scale effects:** Pluggability: full provider integration remains consistent. Scale: advanced use publicly supportable.

**Direct hard dependents:** [X01](#m-x01), [X02](#m-x02), [X04](#m-x04), [X05](#m-x05), [X09](#m-x09), [REL-1](#m-rel-1).

<a id="m-q100"></a>

### Q100 · Constrained 100-part procedural stretch qualification

**Status:** UNSTARTED.

**Hard prerequisites:** [Q60](#m-q60), [U07](#m-u07), [P09](#m-p09), [D01](#m-d01), [A11](#m-a11)

**Priority / applicability:** Required architectural stretch target, not an optional backlog

**Purpose and reason:** Prove the explicit stretch target without arbitrary-netlist promises or tiling tricks.

**Architectural owner / affected systems:** RB100 intent family, supported-envelope policy and independent scale qualification.

**Must not be coupled:** No need to route every arbitrary 100-part netlist. Early 100-part pilots remain mandatory even though final Q100 follows Q60.

**Exact deliverable:** Approximately 100 purposeful physical packages in a heterogeneous structured appliance/control design, with bounded variant choice, domains, layer policy and a complete playable diagnostic contract.

**Acceptance:** At least the declared constrained RB100 family passes generation through normal play, repair, save/replay and long-session checks; every package and required fault is causal; no undocumented whitelist or deterministic seed rescue substitutes for the supported corpus.

**Important negative tests:** Copied independent tiles; external loads counted as board parts; removed hard hypotheses; invisible copper; unlimited jumpers; memory/latency outside frozen budget; structural PASS relabeled playable PASS.

**Performance / scalability evidence:** Holdout success/failure rate, cold/warm stage budgets, worst accepted cases, matrix/proof/input/frame/memory metrics and expert navigation trials.

**Architectural risk:** Compounding weaknesses that were harmless at 30 parts; restricted fixtures advertised as universal routing.

**Expected extension and scale effects:** Pluggability: new complex device remains composition data/providers. Scale: legitimate declared 75-100 stretch envelope.

**Capability condition:** The approximately 100-part stretch remains required, not an optional backlog. Resolve only capabilities used by the selected family. Do not turn every later vocabulary or importer lane into a release prerequisite.

**Direct hard dependents:** [REL-1](#m-rel-1).

<a id="m-x01"></a>

### X01 · Deterministic intermittent and state-dependent faults

**Status:** UNSTARTED.

**Hard prerequisites:** [A09](#m-a09), [D01](#m-d01), [U03](#m-u03), [REL-B](#m-rel-b)

**Priority / applicability:** Required long-range advanced capability; profile use separately admitted

**Purpose and reason:** Introduce real intermittent troubleshooting only after observation and diagnostic planning can support it.

**Architectural owner / affected systems:** Fault event scheduler, solver-time state transitions and temporal diagnostic providers.

**Must not be coupled:** No universal contact physics, automatic PSYCHOTIC label or requirement that every advanced board be intermittent.

**Exact deliverable:** A small bounded vocabulary such as intermittent open, startup dropout and state-dependent contact behavior with reproducible events and causal graph changes.

**Acceptance:** Players can reproduce/capture required conditions through available controls; event time is solver-owned; cancellation/reset/save semantics are explicit; no fake flashing meter.

**Important negative tests:** Wall-clock or unseeded failure; event affects new owner; omitted state after resume; diagnostic plan needs a hidden trigger.

**Performance / scalability evidence:** Event/timestep/observation tests and normal capture/retest trials, with timing-budget qualification.

**Architectural risk:** Random frustration mistaken for difficulty.

**Expected extension and scale effects:** Pluggability: events are fault providers. Scale: bounded temporal hypotheses.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-x02"></a>

### X02 · Expanded causal stress and secondary damage

**Status:** UNSTARTED.

**Hard prerequisites:** [E01](#m-e01), [A08](#m-a08), [U06](#m-u06), [REL-B](#m-rel-b)

**Priority / applicability:** Required long-range consequence capability

**Purpose and reason:** Model consequences beyond the original resistor case without confounding initial faults and player-caused failures.

**Architectural owner / affected systems:** Part stress/damage providers, solver observations, lifetime/history and source limits.

**Must not be coupled:** Does not depend on X01 intermittency or X03 thermal unless the selected model actually consumes them.

**Exact deliverable:** Selected diode/capacitor/transistor/MOSFET/relay/fuse stress states and failure transitions, with supported time and energy approximations.

**Acceptance:** Safe operation survives; excess solved stress accumulates predictably; failures change actual electrical behavior; original fault versus new damage and physical part identity persist through repair/resume.

**Important negative tests:** Random damage without stress cause; visible damage hint reveals unobserved answer; replacing a part retains wrong predecessor state; saved state heals a damaged component.

**Performance / scalability evidence:** Boundary/time/source-limit fixtures, stage-failure tests, state serialization and causal-history checks.

**Architectural risk:** False precision or state growth that invalidates earlier proofs without requalification.

**Expected extension and scale effects:** Pluggability: damage belongs to component providers. Scale: composable consequences.

**Direct hard dependents:** [X03](#m-x03), [X04](#m-x04).

<a id="m-x03"></a>

### X03 · Thermal approximation where diagnosis justifies it

**Status:** UNSTARTED.

**Hard prerequisites:** [X02](#m-x02)

**Priority / applicability:** Conditional: documented diagnostic or failure-timing need

**Purpose and reason:** Add heating/cooling only when an admitted scenario needs thermal timing or an observable physical clue.

**Architectural owner / affected systems:** Derived thermal state provider from solved dissipation and bounded time history.

**Must not be coupled:** No finite-element thermal solver, photoreal camera or prerequisite for ordinary saves/returns.

**Exact deliverable:** One documented heating/cooling model and its supported observations, parameters and interaction with failure thresholds.

**Acceptance:** Temperature follows power/time with explicit limits; cooling and resume are coherent; an exposed clue is physically justified and does not simply identify the selected fault.

**Important negative tests:** Hot marker from fault ID; arbitrary instant cooling; double-counted damage energy; unobserved internal temperature leaked as an answer.

**Performance / scalability evidence:** Known power/time traces, time-step sensitivity and diagnostic utility compared with existing observations.

**Architectural risk:** A decorative thermal camera or unsupported quantitative realism.

**Expected extension and scale effects:** Pluggability: optional derived-state provider. Scale: bounded extra state only where used.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-x04"></a>

### X04 · Persistent customer returns and service intervals

**Status:** UNSTARTED.

**Hard prerequisites:** [X02](#m-x02), [U06](#m-u06), [REL-B](#m-rel-b)

**Priority / applicability:** Planned advanced capability

**Purpose and reason:** Extend immediate retest into plausible later consequences while preserving the actual serviced board.

**Architectural owner / affected systems:** Job history, service-interval simulator and complaint projection over current part/damage state.

**Must not be coupled:** Thermal and intermittency are dependencies only for return models that use them; no economy required.

**Exact deliverable:** A bounded return scenario from a causally inadequate repair, with preserved inventory, conductor modifications and service history.

**Acceptance:** A repair may pass an immediate test yet later fail through the implemented stress/event model; the new complaint is derived from resulting behavior, not a scripted mandatory return.

**Important negative tests:** Every job returns regardless of repair; original board replaced with a new hidden challenge; historical identity lost; unimplemented thermal/intermittent cause invoked.

**Performance / scalability evidence:** Saved interval/resume cases, same-seed causal replay and normal second-visit diagnosis.

**Architectural risk:** Storytelling overriding electrical state.

**Expected extension and scale effects:** Pluggability: complaints consume outcome evidence. Scale: meaningful longitudinal sessions.

**Current persistence rule:** Persist current customer-return history and changed physical state for the supported development format. In-game history is valuable gameplay state; it does not require reopening every previous executable or historical save schema.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-x05"></a>

### X05 · Expert-calibrated PSYCHOTIC admission

**Status:** UNSTARTED.

**Hard prerequisites:** [Q60](#m-q60), [U05](#m-u05), [D01](#m-d01), [REL-B](#m-rel-b)

**Priority / applicability:** Required mature-product profile gate

**Purpose and reason:** Produce difficulty that challenges experienced technicians through reasoning rather than patience or pixel hunting.

**Architectural owner / affected systems:** Advanced profile calibration, diagnostic evidence and blinded technician evaluation.

**Must not be coupled:** Q100 and multiple faults are not necessary for every PSYCHOTIC challenge. A profile using X01/X02/X03 adds those as explicit dependencies.

**Exact deliverable:** Versioned expert profile using several plausible owners, interacting domains/loading/control, alternate topologies, partial power and multi-stage legal diagnosis; temporal/damage variants added only with their providers.

**Acceptance:** Experienced testers encounter meaningful hypothesis reduction and multiple valid routes on held-out designs; required information remains observable; all accepted faults remain repairable; no hidden essential control or fake symptom.

**Important negative tests:** Raw 100-part count substitutes for challenge; one probe always reveals owner; unverifiable intermittent timing; inaccessible pin; bogus circuit behavior used to mislead.

**Performance / scalability evidence:** Blinded trials with skilled electronics troubleshooters, action traces, failure analysis and repeat-exposure/variant transfer. Publish uncertainty rather than claiming professional difficulty from a formula.

**Architectural risk:** Overfitting to one tester or conflating unfamiliar UI with electrical complexity.

**Expected extension and scale effects:** Pluggability: evidence-driven profiles reuse providers. Scale: advanced reasoning independent of board size.

**Direct hard dependents:** [REL-1](#m-rel-1).

<a id="m-x06"></a>

### X06 · Bounded multiple-fault decision and optional proof

**Status:** UNSTARTED.

**Hard prerequisites:** [D01](#m-d01), [A08](#m-a08), [Q60](#m-q60)

**Priority / applicability:** Conditional; explicit skip is valid

**Purpose and reason:** Permit controlled combinations only when they add genuine diagnostic value beyond one fault.

**Architectural owner / affected systems:** Joint hypothesis provider, interaction/repair equivalence and device-level admission.

**Must not be coupled:** Not a prerequisite for PSYCHOTIC, scoring, beta or basic history.

**Exact deliverable:** A decision receipt; if justified, one small explicitly compatible two-fault family with staged repair and retest.

**Acceptance:** Joint effects are proved, not inferred from individual-fault passes; masking and emergent ambiguity reject; each physical owner is retained; the joint hypothesis set and work budget stay bounded.

**Important negative tests:** Multiply every fault with every other fault; one repair masks second failure; independently valid faults assumed jointly fair; count used as difficulty label.

**Performance / scalability evidence:** Exhaustive selected small joint corpus and comparison with single-fault challenge utility.

**Architectural risk:** Combinatorial proof growth and confusing root cause with secondary damage.

**Expected extension and scale effects:** Pluggability: bounded composable hypotheses. Scale: deliberately controlled joint work.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-x07"></a>

### X07 · Optional diagnostic-efficiency scoring

**Status:** UNSTARTED.

**Hard prerequisites:** [U06](#m-u06), [U05](#m-u05)

**Priority / applicability:** Optional product capability

**Purpose and reason:** Provide useful feedback without changing electrical behavior or forcing one correct sequence.

**Architectural owner / affected systems:** Observed action/history scoring service and result presentation.

**Must not be coupled:** No prerequisite for save/resume, multi-fault, returns, alpha or beta.

**Exact deliverable:** Transparent optional scoring from supported observations/actions, unnecessary replacements and consequences, with privacy-safe explanations.

**Acceptance:** Identical supported play yields the same score; valid alternative diagnostic routes are not arbitrarily penalized; disabling scoring changes no challenge state.

**Important negative tests:** Hidden target identity gives bonuses; wall-clock computer performance penalizes a player; score changes fault physics; unreached answer leaked in results.

**Performance / scalability evidence:** Action-trace replay and diverse successful repair routes, plus explanation review.

**Architectural risk:** Gamification rewarding guessing or discouraging legitimate troubleshooting.

**Expected extension and scale effects:** Pluggability: pure observer over history. Scale: bounded aggregation.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-x08"></a>

### X08 · Conditional active capacitance measurement

**Status:** UNSTARTED.

**Hard prerequisites:** [E05](#m-e05), [U02](#m-u02), [A08](#m-a08)

**Priority / applicability:** Conditional on an admitted diagnostic need

**Purpose and reason:** Add capacitance mode only when a real diagnostic gap justifies a defensible active method.

**Architectural owner / affected systems:** Active instrument provider, energy readiness and temporary CircuitJS stimulus.

**Must not be coupled:** No prerequisite on an oscillator merely because it had a lower old task number; not required for all advanced profiles.

**Exact deliverable:** One bounded measurement method with declared range, uncertainty, in/out-of-circuit limits and cleanup.

**Acceptance:** Result derives from the solver response, not configured capacitance; residual charge and parallel paths are handled explicitly; the mode improves a supported diagnostic plan.

**Important negative tests:** Direct CapacitorElm value read; charged-device stimulus; missing cleanup; parallel circuit yields a falsely precise part value.

**Performance / scalability evidence:** Standard known circuits, range/failure cases and useful player diagnostic proof.

**Architectural risk:** A convenience feature claiming unsupported accuracy.

**Expected extension and scale effects:** Pluggability: another instrument provider. Scale: bounded additional proof cost.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="m-x09"></a>

### X09 · Mature packaging, accessibility and support policy

**Status:** UNSTARTED.

**Hard prerequisites:** [REL-B](#m-rel-b), [U01](#m-u01), [U04](#m-u04), [U06](#m-u06)

**Priority / applicability:** Required mature-product support gate

**Purpose and reason:** Make the qualified product usable and supportable beyond the development environment.

**Architectural owner / affected systems:** Release packaging, public documentation, accessibility audit and supported-browser/offline/deployment policy.

**Must not be coupled:** Touch/mobile, cloud accounts, economy and hosted deployment remain separately scoped decisions, not assumed prerequisites.

**Exact deliverable:** Reproducible distribution, attribution/license/dependency review, non-spoiling bug reports, promised desktop accessibility and an explicit current-format support/retirement statement. Maintain a single current reader until a separately authorized compatibility promise creates a real requirement.

**Acceptance:** Install, launch, current challenge reproduction, current saves and recovery work on advertised environments. Published support explicitly states that development updates may invalidate artifacts. Alpha, beta or version 1 labels do not silently create cross-version support promises. Review actual distribution permissions and notices before release.

**Important negative tests:** Hosted-only accidental data loss; stale service worker loads incompatible code; support claim for untested browser; screen-reader reveals hidden value; missing attribution.

**Performance / scalability evidence:** Release-artifact provenance, accessibility trials and clean-machine smoke; deployment security review if hosting is added.

**Architectural risk:** Packaging/version drift or a public promise broader than tested behavior.

**Expected extension and scale effects:** Pluggability: contributor documentation reflects real seams. Scale: supportable larger product.

**Direct hard dependents:** [REL-1](#m-rel-1).

<a id="m-rel-1"></a>

### REL-1 · Mature product readiness with 60-part core and 100-part stretch

**Status:** UNSTARTED.

**Hard prerequisites:** [Q100](#m-q100), [X05](#m-x05), [X09](#m-x09), [REL-B](#m-rel-b)

**Priority / applicability:** Required final product gate

**Purpose and reason:** Close the promised product, not merely the numbered queue.

**Architectural owner / affected systems:** Owner release decision over advertised capability manifests, independent reviews and qualification receipts.

**Must not be coupled:** No completion requirement for optional X03/X06/X07/X08, mobile, economy or universal PCB design unless the product explicitly advertises them.

**Exact deliverable:** Mature current product with advanced Q60 families, qualified constrained Q100, four calibrated profiles, truthful model/layer limits and current challenge/session behavior.

**Acceptance:** All currently advertised contracts pass their final corpus. No unresolved severe correctness, fidelity or ownership defect remains. Expert difficulty and modest-machine budgets have evidence. Any cross-version compatibility promise must be separately owner-approved; maturity alone does not create one.

**Important negative tests:** Stretch feature disabled but still advertised; benchmark-only 100-part proof; unreviewed final source delta; broken current reproduction or unsupported-artifact rejection; cosmetic profile label.

**Performance / scalability evidence:** Final representative release matrix, independent review, reproducible build/distribution and documented remaining optional work.

**Architectural risk:** Treating the roadmap as proof that success was inevitable.

**Expected extension and scale effects:** Pluggability: mature extension-cost target demonstrated. Scale: explicit core and constrained stretch commitments realized.

**Direct hard dependents:** None in this catalog; any actual consuming capability must still be qualified..

<a id="capabilities"></a>
# 8. Capability-conditional dependency rules

The base hard graph is not the graph of every possible future product. A selected family names the current providers it actually uses: design origin, source/reference/load controls, packages/models, instruments, repair actions, state and diagnostic policy. Add only those capability prerequisites to that qualification instance. A release advertising a feature needs its current integrated evidence; merely mentioning a lane does not add a dependency.

| Capability | Add only when | Typical possible consumers |
|---|---|---|
| [E08](#m-e08) | The family advertises trace cuts, player repair jumpers, restoration or bypass actions. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [U06](#m-u06), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [U09](#m-u09) | Two-channel scope is advertised or required for diagnosis. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [U10](#m-u10) | A selected digital diagnostic policy requires logic probing/capture. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [U11](#m-u11) | A selected diagnostic policy requires signal injection. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [U12](#m-u12) | Current insertion or qualified ESR is advertised or needed. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [E09](#m-e09) | A selected family actually includes a powered IC capability in this group. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [E10](#m-e10) | A selected family actually includes sequential logic; E11 scanning may consume it. | [E11](#m-e11), [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [E11](#m-e11) | A selected family includes the supported display/scan capability. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [E12](#m-e12) | A selected family uses one of the specialized provider groups. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [E13](#m-e13) | A selected family uses SCR/TRIAC or zero-cross AC control. | [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [E14](#m-e14) | A selected family uses reversing/H-bridge/load control. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [MCU-2](#m-mcu-2) | A selected family or imported input actually consumes the bounded MCU. | [IMPORT-3](#m-import-3), [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [IMPORT-1](#m-import-1) | A qualification instance is a passive/DC imported design. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [IMPORT-2](#m-import-2) | An imported instance needs this nonlinear/RC subset. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [IMPORT-3](#m-import-3) | An imported instance needs the selected IC/control subset. | [Q15](#m-q15), [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-A](#m-rel-a), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [IMPORT-4](#m-import-4) | An imported instance needs the selected power/dynamic subset. | [Q30](#m-q30), [Q60](#m-q60), [Q100](#m-q100), [REL-B](#m-rel-b), [REL-1](#m-rel-1) |
| [IMPORT-5](#m-import-5) | A release advertises the broader community-qualified import product. | [REL-B](#m-rel-b), [REL-1](#m-rel-1) |

MCU/display/import/extra-instrument lanes have no unconditional path into the base Q15/Q30/Q60/Q100 or release prerequisites. E08 is absent from the base Q60 hard prerequisites. An RB56-MCU family adds MCU-2 and the exact display/driver/instrument capabilities it uses; an ordinary component-repair RB56 does not. Imported families add the minimal importer stage and its real native capabilities, not every stage.

E11 scanned implementations add the actual event/state/driver capability selected, whether E10, MCU-2 or another qualified current provider; static displays do not wait for an MCU. Imported mains/offline designs add E05/E06; those are not blanket passive/DC importer dependencies. Conditional support never means an advertised feature is untested.

Validate cycles and prerequisites for the hard graph and for every chosen conditional bundle. Do not allow a provider to require the completed board whose purpose is to qualify it. A scope needing both a provider-state qualification and board-level acceptance can sequence provider/local proof first, then consuming integration, without inventing a cycle. Preferred timing does not override actual data/runtime dependencies or shared-file ownership.

<a id="risks"></a>
# 9. Risk register, non-goals and completion

## 9.1 Original audit findings reconciled to the current direction

These are design/maintenance risks with different evidence levels, not a claim that the whole game is broken. Earlier audit labels remain useful references; they do not mandate preserving earlier code or rerunning earlier campaigns.

| Finding | Current disposition and owner | Required evidence |
|---|---|---|
| F1: centralized device construction | A04 delivered a useful seam; R00 removes remaining historical/generic-layer coupling; A05/A11 prove extension. | Two current content paths, declaration-driven materialization and an ordinary variant without new generic device branches. |
| F2: developer-owned production proof | A09/D01 separate production hypothesis execution from developer tests. | Current reachable diagnostic actions, complete hypothesis accounting and independent falsifiers. |
| F3: inconsistent candidate eligibility | A02 delivered a correction; retain current semantics through R00/A09. | Serviceability, same-owner distinct hypotheses and selection/count/proof agreement. |
| F4: fixed physical capacity | P03/P09 plus A01 counters. | Mixed packages, current variable outlines/placement and usable larger boards, not merely a bigger image. |
| F5: incorrect bend counting | Corrected in A02; retire old metric in R00. | Direction/subdivision-invariance and independently correct route scores. |
| F6: connected-placement center offset | Corrected in A02; retire obsolete arithmetic in R00, extend coordinate contract in P01. | Translation, local pad offset and boundary tests on the current caller. |
| F7: raw vertices and repeated contact work | P04/P08. | Contact-preserving canonical segments, current oracle equivalence and measured hotspots. |
| F8: fit-only interaction | U01. | One transform for view/hit/marker/accessibility, Space loupe, side access and real player input. |
| F9: union-find representatives used as identity | A03 added explicit identities; R00 consolidates their current consumers; P02 adds conductor semantics. | Earlier lexical alias/reorder tests and no persistent solver/DSU/pixel IDs. |
| F10: floating-reference numerical ties | A06/A07/U02/E05. | Reference sensitivity, burden/isolation behavior and honest unsupported states; do not blindly delete stabilizing solver mechanisms. |
| F11: bench/session growth | U06/U07 and scale gates. | Distinguish legitimate retained parts from leaked owners; bound histories/waveforms and measure actual growth. |
| F12: semantic IDs leak into presentation | R00/A03 plus U01/U04/A06. | Short current physical designators and meaningful reference labels, separate from identity/private answers. |
| F13: invented dependency chains | This edition and per-family capability resolution. | Acyclic hard/resolved graphs; no blanket MCU/import/E08 release dependency; honest actual consumers. |
| F14: unused wrappers/visual tags/old menus | Delete verified obsolete code when the R00 inventory or a current task owns its callers. Retain useful current adapters. | Caller analysis and current tests, not deletion quotas or source age. |

## 9.2 Additional current risks

| Risk | Owning response |
|---|---|
| Materializer accepts foreign/mismatched plan/spec/receipt or backing relationships | R00 closes the three A04 follow-ups before provider expansion with valid and deliberately wrong-input cases. |
| Cleanup becomes another permanent framework or historical version | One R00 correction, compact checkpoint and current tests; no new epoch-specific resolver family or certification hierarchy. |
| “Generic” code still names driver/load/RG/RLOAD/LED_NODE | R00 declaration-driven core; A05 proves another variant; A11 examines where knowledge was added, not only file counts. |
| Two physical units are silently forced to equal two packages | Keep explicit package/unit mapping, prove small shared-unit shape now, qualify actual runtime support with later IC consumers. Do not claim arbitrary multi-unit materialization from a data-only canary. |
| Solver/model fidelity cannot meet the advanced target | A07 small high-risk pilots and E06 compare bounded current alternatives before full RB56; change the necessary boundary on evidence. |
| Diagnostic work grows faster than the circuit library | A09/D01 valid local/global proof contracts, complete context keys and measured coverage; no cheating by omitting difficult hypotheses. |
| Dense layers create unprobeable or misleading boards | P01/P02/U01 surface/mount/loupe checks and P07/P09 real side/layer comparison. |
| Stateful IC/MCU creates a second time/state truth | One accepted-step/event owner, finite event work, power/reset effects and current resume qualification. |
| Import looks successful but lacks a meaningful repairable challenge | Twelve-step contract, author healthy intent, explicit support matrix and native playthroughs. |
| Long-range lanes consume all near-term effort | Small necessary seams now, actual later libraries only for selected content, visible working integration at named boundaries. |

## 9.3 Do not build without a concrete need

No universal circuit-design DSL or symbolic synthesizer; no blanket CircuitJS/GWT/JDK/frontend rewrite; no assumed parallel live solver contexts; no arbitrary deep snapshot/rollback system; no unbounded globally optimal router; no more-than-two-layer manufacturing CAD/Gerbers; no BGA/mobile-phone density or reflow simulator; no high-fidelity RF/EMC/arcing/magnetics design; no arbitrary firmware/HDL/protocol ecosystem; no economy/progression dependency; no client-side encryption pretending to secure local simulated answers; no GPU/WebGL/minimap/spatial-tree project absent measured need.

These are scope controls, not preservation guarantees. Replace an inherited boundary when real model fidelity, API, runtime isolation, secure/reproducible maintenance or measured development cost requires it. Investigate the smallest effective replacement first and qualify current behavior. No old class or test has veto power merely because it already exists.

## 9.4 Definition of done

Freeze a compact current acceptance set before implementation. New evidence of a real current correctness defect can add an appropriate test; unrelated archaeology or aesthetics cannot move the finish line indefinitely. Resolve blockers, run affected current checks on the integrated candidate, obtain one independent review and perform targeted delta checks after repairs.

Document what changed and why, evidence and its limits, current support scope, real follow-ups, branch/commit and next unstarted task. Keep report/log artifacts small enough to be useful. Git is the archive; old reports may remain historical records but are not byte-preserved product gates and are not active instructions. Do not keep many copied whole-repository hash inventories or repetitive manifests solely for ceremonial provenance; record the candidate and relevant dependency evidence needed to identify the executed code.

Use normal scoped staging and configured-upstream publication under AGENTS.md. No history rewriting, broad reset/clean, unrelated branch deletion, user-data loss or process-name-based killing. Final source/build, current tests, independent review, safe owned cleanup and honest publication/email status remain real responsibilities.

<a id="sources"></a>
# 10. Review basis, adoption and change ledger

## 10.1 Source basis and limits

This edition is grounded in the source Edition 2.1 product scope and its adopted roadmap at the reviewed commit, the exact A04 diff and selected current construction/materialization/identity call paths. The earlier Edition 2.0 detailed architecture comparison tables, reference allocations and planning ranges are retained as design proposals where the Edition 2.1 amendments did not replace them. They are not new benchmarks or claims of current scale qualification.

| Source | What it supports |
|---|---|
| Git commit `cc3532e8d138424ce986aa8f9b76688ec315f0a0` and parent `1d995d4f5b21142d4ca5643ede546afdfe51340f` | Actual A04 change boundary, published implementation and preceding A03 base. |
| `docs/ROADMAP.md` at that commit, especially Sections 7.1 and A04 | Edition 2.1 scope, actual dependency cards and contradictory stale A04 status text. |
| [A04 evidence](task-evidence/A04/README.md) and [closure reconciliation](task-evidence/A04/closure-reconciliation.md) | Reported current scope, compiled/electrical/repair results, comparison policy, environment limits and bounded materializer capability. |
| [A04 independent review](task-evidence/A04/independent-review.md) | Static review attribution, three nonblocking boundary-hardening findings and their original scope rationale. |
| [A04 native/build summary](task-evidence/A04/native-build-summary.md) | Recorded actual native/build commands and results, not a fresh rerun by this author. |
| `PhysicalConstructionMaterializer.java`, `PhysicalConstructionProvider.java`, `PhysicalConstructionPartDeclaration.java` | Current family/order coupling, historical fault reinterpretation and declaration/backing/receipt validation boundaries. |
| `ElectricalRealizationSpec.java`, `ElectricalConstructionContext.java`, `BoundedGeneratedBoardAssembler.java` | Scoped ownership and spec/receipt relationships, current construction coordination and historical version consumers. |
| Earlier A02/A03 evidence and the current owners they introduced | Corrected metrics/placement, current identity concepts and the historical replay/report obligations selected for retirement. |
| Owner instructions in this conversation, September 9, 2026 | No historical development compatibility requirement; keep the game working; a full replacement roadmap and correction-or-A05 prompt. |

The proposed R00 design and sequence are engineering judgments derived from those observations. The review is targeted, not an exhaustive bug-free certificate for the repository. No deletion count, usage-credit savings, percentage of wasted development time or large-board performance guarantee is claimed.

## 10.2 Adoption

Replace the full `docs/ROADMAP.md` with this file. During R00, reconcile current HEAD and any intervening work first. Update contradictory compatibility clauses in AGENTS.md and implemented ownership in ARCHITECTURE.md; do not just append a new rule below contradictory old rules. Existing historical reports remain historical. Their assertions are not imported as new required gates.

No external graph file is required. The optional validation report delivered with this edition is generated from its cards and is not a competing authority. If the repository later maintains a machine-readable graph, regenerate it from the current cards and validate all references/cycles; do not point to a missing old `research/RECONCILED_ROADMAP_GRAPH.json`.

After actual R00 completion, record its commit/evidence and mark A05 as the next unstarted node. Later completions update one current checkpoint and their card status. Do not rewrite a historical success as failure merely because the implementation has intentionally changed, and do not reuse that success as qualification of a changed current contract.

## 10.3 Edition 3.0 change ledger

| Change | Effect |
|---|---|
| Retire blanket historical compatibility | Removes Task48/49 preservation clauses, obsolete descriptor obligations and historical report/golden equality from future acceptance. |
| Add R00 after delivered A04 | One bounded current-only cleanup plus the three materializer hardening checks before new consumers. |
| Update early frontier dependencies | A05 depends on R00; A06 depends on R00; P01 also depends on R00. This places future expansion behind the reset without changing the product targets. |
| Reconcile current status | A04 is delivered at the reviewed SHA; stale validation-blocked text is removed; A05 remains unstarted. |
| Retain corrected A02 and useful A03/A04 concepts | No rollback of real fixes; implementations and data encodings may be simplified or replaced. |
| Carry all Edition 2.1 capability lanes forward | IC/logic/display, MCU, instruments, import and grouped fault vocabulary remain explicitly scoped. |
| Retain dense-board interaction amendments | Spacebar loupe, shared transforms, focus/cancel behavior and 0805/SOT-23 architecture canaries remain. |
| Keep E08 capability-conditional | Component-repair-only Q60/Q100 are not blocked by trace-repair gameplay. |
| Replace historical qualification with current acceptance | One integrated build, focused current electrical/physical/ownership tests and ordinary-player checks, with honest infrastructure evidence separation. |
| Make roadmap self-contained | Full cards, scope, reference allocations, comparisons, limits, risks and dependency rules are here; no required external roadmap patch or graph. |

**Success is a maintainable current game that works. It is not a perfect reenactment of every previous development milestone.**
