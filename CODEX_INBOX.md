# TASK 43 RECOVERY — 43R-3 CORRECTIVE RETURN

## Title
Repair 43R-3 Lifted Component Endpoint Identity Before Resuming 43R-8B

43R-8B IS BLOCKED.

Observed failure:

`FAIL:Lifted component target changed stable physical or endpoint identity: R1.1`

Exit code: `1`

This occurs in the unchanged `PhysicalPartRenderDeveloperVerifier` BEFORE the 43R-8B forced-negative canary.

The current 43R-8B candidate changes are uncommitted in exactly these four files:

- `PcbLayoutDeveloperVerifier.java`
- `CirSim.java`
- `Task43DeveloperVerifier.java`
- `verify-browser.ps1`

JDK 8 / GWT build and general Layout validation passed for the candidate work, but forced-negative validation and final review could not proceed.

No documentation update, commit, push, final Task 43 acceptance, or Task 44 work is permitted.

---

# OWNERSHIP DECISION

THIS FAILURE RETURNS TO 43R-3.

43R-3 owns:

- installed component rendering;
- lifted component-lead interaction;
- board-side versus component-side probing;
- stable physical target identity;
- correct measurement endpoint identity;
- lifecycle invalidation for lifted installed targets.

43R-4 owns later loose/removal/reinstallation lifecycle behavior and MUST be rerun as a regression after the 43R-3 corrective pass, but this specific failure occurs while the part is still mounted with a lifted lead.

43R-8B DOES NOT OWN THIS DEFECT.

Do not patch this inside 43R-8B.

---

# FIRST: PRESERVE THE 43R-8B CANDIDATE WORK

Before investigating or editing the lifecycle defect:

1. Inspect git status and diff.
2. Verify the only intended uncommitted 43R-8B changes are the four files listed above.
3. Preserve those exact changes safely in a named stash or equivalent isolated patch.
4. Do NOT discard them.
5. Do NOT commit them.
6. Restore a clean working tree before beginning the corrective 43R-3 investigation.

The preserved 43R-8B work will be reapplied ONLY after this earlier lifecycle frontier is independently accepted.

If unrelated changes exist, STOP and report them instead of hiding or combining them.

---

# ABSOLUTELY MANDATORY AGENT ORDERING

EVERY SINGLE SUBAGENT YOU SPAWN MUST USE MAX REASONING.

MAX.

NOT "EXTRA HIGH."
NOT "HIGH."
NOT SOMETHING YOU DECIDE TO CALL "MAX."

ACTUAL MAX REASONING.

This applies to investigators, reviewers, and any eventual coder.

MORE IMPORTANTLY:

YOU ARE ABSOLUTELY FORBIDDEN FROM SPAWNING A WRITE-CAPABLE CODER WHILE ANY READ-ONLY INVESTIGATOR IS STILL WORKING.

THIS IS NOT A SUGGESTION.

DO NOT:

1. spawn investigators;
2. immediately spawn a coder using only the information already in this inbox;
3. receive investigator findings later;
4. drip-feed those findings into an already-working coder.

THAT WORKFLOW IS FUCKING UNACCEPTABLE AND DEFEATS THE ENTIRE PURPOSE OF THE INVESTIGATION PHASE.

The required order is:

1. Spawn all approved READ-ONLY MAX investigators.
2. LET THEM FINISH.
3. Receive ALL investigator reports.
4. Reconcile their findings yourself.
5. Produce ONE complete architectural diagnosis and implementation contract.
6. Review that contract for contradictions.
7. ONLY THEN spawn ONE write-capable MAX coder with the COMPLETE reconciled specification.

The coder must receive the final answer, not participate in discovering what the answer is.

---

# ARCHITECT BEHAVIOR WHILE SUBAGENTS WORK

Once investigators are running, LEAVE THEM ALONE.

Do not constantly post status messages.
Do not narrate that they are still working.
Do not repeatedly inspect partial output.
Do not poll git status, timestamps, logs, process lists, or their progress just to prove they are alive.
Do not interrupt them with new instructions unless genuine new evidence makes the existing assignment invalid.

Sit idle and let them work.

Your job in this phase is:

- delegate clearly;
- wait;
- receive completed reports;
- reconcile them;
- perform the architect review.

That is it unless an actual blocker requires intervention.

Constant narration burns credits while contributing nothing.

Also: treat the subagents professionally. They are doing the detailed investigation and implementation work. Do not bark at them, badger them, or act as though completion speed matters more than correctness.

---

# KNOWN TECHNICAL EVIDENCE TO INVESTIGATE

The current committed code already exposes a specific suspicious seam.

`PhysicalPartRenderDeveloperVerifier`:

1. Locates the real `PhysicalPartTerminal` corresponding to the binding.
2. Captures:

   `stablePartId = candidate.part.getId()`

   `stableEndpoint = stableTerminal.getEndpoint()`

3. Before mutation, it establishes that:

   `binding.getComponentEndpoint()`

and

   `stableTerminal.getEndpoint()`

refer to the same underlying CircuitJS element/post.

4. After lifting the lead, it acquires the actual target through:

   `renderer.findProbeTarget(...)`

5. It then requires:

   - correct component ID;
   - correct pad ID;
   - same stable physical part ID;
   - `liftedTarget.getMeasurementEndpoint() == stableEndpoint`;
   - correct component-side marker position.

The failure occurs at this composite invariant for `R1.1`.

Meanwhile, the committed `PcbWorkbenchRenderer` component-side target construction currently creates `ComponentLeadProbeTarget` using:

`binding.getComponentEndpoint()`

rather than explicitly obtaining the endpoint from the mounted `PhysicalPartTerminal`.

`ComponentLeadProbeTarget.isValid()` accepts endpoints that are not object-identical when they refer to the same `CircuitPostMeasurementEndpoint` element/post.

`GeneratedComponentConnectionBinding.componentEndpoint` is mutable.

`PhysicalPartTerminal.endpoint` is immutable for the lifetime of that physical terminal.

THEREFORE A STRONG CURRENT HYPOTHESIS IS:

The lifted target is electrically pointing at the correct CircuitJS post but is carrying a different `CircuitMeasurementEndpoint` object from the endpoint identity owned by the physical part terminal.

That would explain why ordinary electrical equivalence succeeds while the stable physical endpoint identity canary fails.

THIS IS A HYPOTHESIS, NOT PERMISSION TO PATCH IT BLINDLY.

---

# PHASE A: READ-ONLY INVESTIGATION

Spawn 2 or 3 READ-ONLY MAX investigators with distinct responsibilities.

## Investigator A: Endpoint ownership and lifecycle

Determine:

- What object is supposed to own component-side physical terminal endpoint identity?
- Is `PhysicalPartTerminal.getEndpoint()` the canonical stable endpoint for the lifetime of a physical part?
- Why does `GeneratedComponentConnectionBinding` also retain a `componentEndpoint`?
- Why is `GeneratedComponentConnectionBinding.componentEndpoint` mutable?
- Under what legitimate operations does `setComponentEndpoint()` run?
- Should a `ComponentLeadProbeTarget` created for a physical lead carry:
  - the binding endpoint;
  - the `PhysicalPartTerminal` endpoint;
  - another canonical endpoint identity?
- Does lifting a lead intentionally replace electrical infrastructure while preserving the physical terminal endpoint?
- Would using the physical terminal endpoint preserve real CircuitJS electrical truth?

## Investigator B: Full 43R-3 interaction/lifecycle regression

Trace the exact lifecycle for:

- connected R1.1;
- lift R1.1;
- component-side probe acquisition;
- board-side probe acquisition;
- reconnect;
- relift;
- graph-only removal if applicable;
- final physical removal.

Determine whether the failure is isolated to endpoint-object identity or whether any:

- physical part ID;
- terminal ID;
- lifecycle token;
- marker geometry;
- component state;
- endpoint backing;
- target invalidation

also changes incorrectly.

## Investigator C: Coupling/adversarial review

Inspect every consumer of:

- `GeneratedComponentConnectionBinding.getComponentEndpoint()`
- `GeneratedComponentConnectionBinding.setComponentEndpoint()`
- `PhysicalPartTerminal.getEndpoint()`
- `ComponentLeadProbeTarget` constructors
- `ComponentLeadProbeTarget.getMeasurementEndpoint()`
- `ComponentLeadProbeTarget.isValid()`
- `ComponentLeadProbeTarget.isSameTarget()`

Determine whether changing target endpoint sourcing would break:

- resistance;
- voltage;
- continuity;
- diode testing;
- lifted-lead measurement;
- fault infrastructure;
- removal;
- replacement;
- Task 39;
- Task 40;
- Task 41;
- 43R-4 loose-part lifecycle.

ALL INVESTIGATORS MUST FINISH BEFORE A CODER EXISTS.

---

# PRIMARY ARCHITECT SYNTHESIS

After ALL investigators finish, reconcile their evidence.

Classify the defect as exactly one of:

- `IMPLEMENTATION_FAILURE`
- `REALIZATION_INFEASIBLE`
- `ARCHITECTURAL_CONTRADICTION`
- `EXTERNAL_BLOCKER`

Current provisional classification is `IMPLEMENTATION_FAILURE`, but you must prove it.

Produce a complete written ownership statement answering:

1. Which object owns stable physical-terminal endpoint identity?
2. Which object owns mutable board/component connection state?
3. Whether electrical equivalence and physical endpoint identity are intentionally different concepts.
4. Which endpoint a `ComponentLeadProbeTarget` must return.
5. What must remain stable across lift/reconnect.
6. What must become invalid when lifecycle state changes.
7. Why the proposed repair preserves CircuitJS as electrical source of truth.
8. Exact files the coder may change.
9. Exact regression matrix.
10. Exact negative canaries.

ONLY AFTER THIS SYNTHESIS IS COMPLETE MAY YOU SPAWN ONE MAX CODER.

---

# DO NOT WEAKEN THE CANARY

The following shortcut is FORBIDDEN unless the investigators prove the existing contract itself is wrong:

Changing:

`liftedTarget.getMeasurementEndpoint() == stableEndpoint`

into a looser:

`sameCircuitPostEndpoint(...)`

or equivalent merely to make the verifier green.

The entire point of the canary is stable PHYSICAL endpoint identity.

Do not convert an identity invariant into electrical equivalence because the implementation currently fails it.

Similarly forbidden:

- deleting the assertion;
- special-casing R1.1;
- accepting either endpoint;
- rebuilding the endpoint during every probe;
- changing target equality to hide the drift;
- changing the verifier before proving the verifier contract wrong.

---

# PHASE B: ONE COHERENT IMPLEMENTATION

After the investigation is complete and the architect approves the design:

Spawn ONE write-capable MAX coder.

The coder receives the COMPLETE reconciled design.

The coder must implement one coherent repair only.

Likely relevant systems may include, depending on the approved design:

- `PcbWorkbenchRenderer`
- `ComponentLeadProbeTarget`
- `PhysicalPart` / `PhysicalPartTerminal` endpoint lookup
- `GeneratedComponentConnectionBinding` only if ownership analysis proves it necessary
- `PhysicalPartRenderDeveloperVerifier` only for genuinely missing positive/negative coverage

Do not modify unrelated Task 43 architecture.

Do not touch:

- fixed RC/NPN/NMOS routing;
- package geometry unless investigators prove an architectural contradiction;
- Task 43 final verifier candidate files currently preserved from 43R-8B;
- Task 44;
- `AGENTS.md`.

---

# 43R-3 CORRECTIVE VALIDATION

At minimum prove:

## CONNECTED

- R1.1 resolves only as board-side target.
- No component-side target is exposed.

## LIFTED

- board-side target remains valid.
- component-side target becomes independently reachable.
- physical part ID is unchanged.
- component ID is unchanged.
- pad ID is unchanged.
- physical terminal identity is unchanged.
- component-side target returns the canonical stable physical-terminal endpoint.
- marker point is the detached component-side surface.
- board-side and component-side endpoints remain correctly distinct electrically.

## RECONNECT

- old component-side target becomes invalid.
- marker disappears.
- board-side target remains correct.

## RELIFT

- newly acquired component target refers to the same physical terminal identity as required by the approved lifecycle contract.
- stale target remains stale.

## REMOVAL

- installed interaction invalidates correctly.

## REINSTALL / 43R-4 REGRESSION

- physical part ID behavior remains correct.
- terminal IDs remain correct.
- endpoint lifecycle follows the previously accepted 43R-4 contract.
- loose target behavior remains correct.
- page lifecycle remains correct.

Also run all existing 43R-3 / `PhysicalPartRenderDeveloperVerifier` positive and negative canaries.

Run relevant Task 39/40/41 regressions.

Run JDK 8 / GWT validation required by the recovery roadmap.

---

# INDEPENDENT REVIEW

After the coder finishes and validation passes:

Spawn an independent READ-ONLY MAX reviewer.

The reviewer must determine:

- whether endpoint ownership is now coherent;
- whether the verifier was improperly weakened;
- whether target identity survives lift correctly;
- whether stale targets invalidate correctly;
- whether 43R-4 lifecycle behavior remains intact;
- whether electrical behavior changed;
- whether the repair exceeded owning scope.

Reviewer returns:

`PASS`

or

`BLOCKERS` with exact evidence.

Reviewer makes NO edits.

---

# CORRECTIVE CHECKPOINT

Only after reviewer PASS:

- update the appropriate task report/evidence;
- inspect git diff/status;
- stage ONLY the corrective milestone files;
- run `git diff --cached --check`;
- commit the 43R-3 corrective repair as its own checkpoint.

Do not mix the preserved 43R-8B candidate into this commit.

Do not push.

---

# RESUME 43R-8B

ONLY after the corrective checkpoint is independently accepted:

1. Reapply the preserved 43R-8B candidate changes.
2. Resolve conflicts semantically, not mechanically.
3. Re-run JDK 8 / GWT build.
4. Re-run general Layout validation.
5. Re-run the complete `PhysicalPartRenderDeveloperVerifier`.
6. Confirm the R1.1 lifecycle failure is gone.
7. Continue the originally planned 43R-8B forced-negative canary.
8. Perform final review only if every preceding gate passes.

If another earlier lifecycle failure appears, STOP again and return it to its owning milestone.

Do not patch earlier-contract failures inside final acceptance.

Do not push.
Do not begin Task 44.
