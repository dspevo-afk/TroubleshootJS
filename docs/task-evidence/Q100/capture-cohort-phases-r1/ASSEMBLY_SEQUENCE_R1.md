# Pre-arm capture/cohort association contract to freeze next

Root and the bounded independent Source reviewer agree on the sequencing gap.
This is preparation, not an implementation acceptance or a defect in N31's
completed-arms component. The selected current arm signature requires raw Cohort
before execution; its projected Map is not that arm argument. The source map
requires exactly NULL then OBSERVE, followed by completed-arms body/work staging.

Current captureBoundBody accepts completed arms and derives internally once.
It cannot itself supply the pre-arm Cohort. A separate pre-arm derivation followed
by that API would derive twice; its extra owner work cannot be omitted from
accounting. A mutable placeholder arms Map would violate the stable prevalidated
arms precondition. The actual normal producer remains unimplemented/gated.

Freeze a two-phase interface in the existing CurrentAssemblyMetadata owner:

1. Before either arm, derive one raw Cohort from the validated setup and the
   stable capture DTO's netIds. Bind that exact capture Map and raw Cohort as one
   association, without an independently supplied ID vector or later mismatched
   capture/cohort pair. Reuse existing derivation; no alternate ordering algorithm.
2. The future caller supplies that same raw Cohort to NULL and OBSERVE exactly
   once each. No third arm, extra control, native retry or fake owner fixture.
3. After complete arms exist, project the already-derived Cohort once and stage
   the body with the same capture Map and arms. Do not rederive during this step.
4. Existing owners still fill qualified prior, work and cleanup before R7/export.
   The selected owners do not establish the work-slot installer; select its
   explicitly current Source contract before integrating that boundary.

An internal association carrier is a candidate, not an authority or receipt
ticket. Its representation and accessibility must prevent a separately supplied
Map/Cohort mismatch. Define lifetime, alias release and whether completion is
single-use before implementation. Preserve exclusive stable metadata ownership
through both arms, preparation and export; do not claim adversarial/concurrent
container authority. Never serialize a helper/owner object into the wire graph.

Price all new holder allocations, stores, field observations, guards and any
consumption/alias release before execution. No final price is established here.
The accepted279/138/65 describes N31's completed-arms composition only. Existing
caps and independent work/tree/receipt/identity/parity/finite/cleanup oracles
remain authoritative. Do not bless a new golden or relabel a generic snapshot.

Required finite proof, once the contract is frozen: one capture lookup and one
derivation; exact same raw Cohort and capture Map across phase boundaries;
one projection; literal order/detachment; unchanged inputs; invalid argument and
inherited cohort errors; all admission failures before observation/mutation;
alias/consumption/reset rules; exact phase and aggregate Meter fees. Use ordinary
synthetic metadata and actual Meter/Cohort holders, never fake OriginalArm or
ArmResult. Existing independent cases remain preserved or deliberately migrated
under current-source ownership. Actual producer/semantic/host/genuine proof is
separate and still unspent. No extra Source or DATA is admitted by this plan.

Reviewer read only SOURCE_INPUT/SELECTION, native/wire contract docs, the current
arm declaration and three assembly owners. Root separately read the two selected
pure-kernel methods. Neither reviewer nor root executed target code/tests.
No task-report history or DATA was read in this N32 review. Future report reviews
receive pre-extracted bounded prefix files, not the full report path.
