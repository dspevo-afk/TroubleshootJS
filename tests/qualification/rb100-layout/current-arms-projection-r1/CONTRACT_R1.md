# Current arms DTO projection contract R1

Small component prerequisite only. Do not enable main, assemble or R7 actual
aggregation. No OriginalArm/ArmResult instances, real/fake graph/Session/routing,
protected/heldout data, full-D execution or genuine envelope spend. Six DTO maps
are explicitly synthetic in canaries. Current Source authority and lifecycle stay
separate, current result semantics not accepted by this component.

Add CurrentResultProjections.arms(meter, nullId, nullRun, nullObservation,
nullCleanup, observeId, observeRun, observeObservation, observeCleanup).
All 9 null arguments reject IOException RESULT_PROJECTION_ARGUMENT before meter
or child observation/allocation. IDs and maps are caller-prevalidated, disjoint
and exclusively stable through later metadata, R7 and export. Non-null inputs
are borrowed unchanged; no map reads/iteration, cloning, authority validation,
observer/owner work, meter close or stored static/mutable state. Fixed roles NULL
and OBSERVE. Output is three fresh LinkedHashMaps: outer ordered null,observe;
each arm ordered armRole,armId,freshRun,observation,cleanup. The six child-map and
two ID references are preserved exactly. Source strings/counters are not rewritten.

Before each new arm map: meter.copy(5), meter.charge(1); before outer map:
meter.copy(2), meter.charge(1). Stores covered by those copy debits. Success exact
15 actual units /12 copies /3 chunks; leaves metadata open and finishes no group.
Partial admission failure returns nothing, leaves inputs unchanged, discards the
partial Meter; no retry/fabricated close. Existing methods remain byte-identical.

Independent expectations fixed before execution: null role NULL/id SYNTH_NULL,
observe role OBSERVE/id SYNTH_OBSERVE. Each child is distinct ordered one-entry
map with fixture key and literal marker (nullRun=N_RUN,nullObservation=N_OBS,
nullCleanup=N_CLEAN,observeRun=O_RUN,observeObservation=O_OBS,observeCleanup=O_CLEAN).
Canonical nested payload compared literally; identity/no-input-read assertions.
Synthetic payloads are deliberately not semantically valid fresh-run results.
Alias input passes projection then actual CurrentBodyOwnership rejects; semantic
false/malformed child content passes through, not accepted. Distinct fixture
passes existing ownership. All argument positions, copy allowance4/5/10/12,
640-group failure-before-read and success open-meter accounting must be checked.
Copy allowances exercised via actual setCopyAllowance; expected helper partial
(0/0/0),(6/5/1),(12/10/2),(15/12/3), respectively. Reserve may open an empty group
before copy-capacity failure; native openness observed and priced before acceptance.

Root owns independent Python literal bytes/counters reader and negative reader
canaries; no observed result becomes expectation. Final-source JDK8 compile with
reauthenticated N7 closure, major51/no shadow inventories; new SMOKE and SUITE
and existing projection SMOKE2/SUITE15 with actual readers. Actual selected JDK8
host controls early; Python -I -B optimize0. No GWT/Player (qualification-only).
One expensive matrix at a time, all inherited budgets retained. Failure evidence
preserved, no unresolved blocker publication; exact source freeze before native.
