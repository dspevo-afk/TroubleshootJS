# Resolved independent findings

## a08-review.md

STATUS: REVIEW ONLY

SCOPE: Fresh independent read-only review of the integrated A08 candidate above `c5a0d5e31ce9848b0e06b7c04c25866034d3d090` on `codex/task43p-final-recovery`. No edits, build, browser, staging, commit, push, email, or subagents.

FINDINGS:

- **BLOCKER — CODE:** Fresh-install disjointness is incomplete. [`FreshGeneratedRuntimeInstallation.java:163`]($REPO/src/com/lushprojects/circuitjs1/client/FreshGeneratedRuntimeInstallation.java:163) checks several owners, solver elements, parts, terminals, and capabilities, but not known mutable/reachable owners such as `pcbLayout`, `operationalStates`, and `temporalBehavior` held by [`GeneratedBoardInstance.java:16`]($REPO/src/com/lushprojects/circuitjs1/client/GeneratedBoardInstance.java:16). A candidate sharing those references can pass the guard and affect the original during installation or cleanup, violating the A08 acceptance that fresh installations never alias mutable owners. Extend the disjointness check to every mutable holder/reachable state owner and add negative alias cases for layout, LED state, and temporal behavior.

- **FOLLOW-UP — CODE:** [`PhysicalMutationIntent.java:82`]($REPO/src/com/lushprojects/circuitjs1/client/PhysicalMutationIntent.java:82) validates runtime slot identity but does not require the slot to be the exact predeclared scoped capability or require a registered provider. A same-package custom slot can therefore enter the transaction without declaration. Current production controllers use declared capabilities, so this is not observed in existing callers; add the identity/provider check and an unregistered-slot negative test.

- **FOLLOW-UP — CODE:** [`PhysicalBoardRuntime.java:495`]($REPO/src/com/lushprojects/circuitjs1/client/PhysicalBoardRuntime.java:495) rejects duplicate inventory views by object identity only. Separate views with the same runtime/inventory ID share backing storage and can pass admission; later structural validation may catch the ambiguity. Reject duplicate inventory IDs or shared-storage owners during admission.

- **FOLLOW-UP — TEST/TOOL:** [`verify-a03-browser.ps1:398`]($REPO/scripts/verify-a03-browser.ps1:398) and [`a04-report-contract.ps1:398`]($REPO/tests/contracts/a04-report-contract.ps1:398) validate synthetic report rows and exact cardinality, not that every row came from compiled provider receipts. The resistor case is reduced to marker strings in `A08MutationDeveloperVerifier`. The 444-assertion result is protocol validation only, not production/runtime proof.

VALIDATION:

- `git status --short --branch`: PASS; branch is correct, candidate remains uncommitted.
- `git diff --check`: PASS, exit 0; only LF/CRLF normalization warnings.
- Existing report-contract evidence: PASS, 444 assertions; explicitly not product qualification.
- JDK8/GWT build, compiled A08 runtime matrix, browser/player flow, and electrical settlement checks: **NOT RUN**, per request.

REVIEW: Independent read-only review complete; A08 is not qualified because of the fresh-owner blocker.

LIMITATIONS: No runtime reproduction or full acceptance gates were permitted.

GIT: No publication actions. Pre-existing `tests/contracts/__pycache__/` preserved.

RESOURCES: NONE.

NEXT: Repair fresh-owner disjointness, add the negative alias coverage, then rerun the required compiled/runtime/build/browser gates.

## a08-delta-review.md

STATUS: REVIEW ONLY — A08 NOT QUALIFIED

SCOPE: Fresh independent read-only review of the integrated candidate above `c5a0d5e31ce9848b0e06b7c04c25866034d3d090`. No edits, build, browser run, staging, commit, push, or email.

FINDINGS:

- **BLOCKER — CODE:** Fresh-owner disjointness omits the challenge behavior/callback graph. [`FreshGeneratedRuntimeInstallation.java:163`]($REPO/src/com/lushprojects/circuitjs1/client/FreshGeneratedRuntimeInstallation.java:163) checks the definition object identity but not its `behaviorContract` or `scenarioCatalog`. [`GeneratedChallengeDefinition.java:12`]($REPO/src/com/lushprojects/circuitjs1/client/GeneratedChallengeDefinition.java:12) stores both. The current controlled-indicator catalog captures its mutable behavior and presents by calling `setAllChannels` ([`ControlledIndicatorDeviceBehavior.java:165`]($REPO/src/com/lushprojects/circuitjs1/client/ControlledIndicatorDeviceBehavior.java:165), [`:181`]($REPO/src/com/lushprojects/circuitjs1/client/ControlledIndicatorDeviceBehavior.java:181)); those handles mutate captured solver elements ([`ElectricalConstructionContext.java:1691`]($REPO/src/com/lushprojects/circuitjs1/client/ElectricalConstructionContext.java:1691)). A fresh candidate can therefore wrap a shared catalog/behavior and mutate the retired board during challenge presentation, violating “fresh installation never aliases mutable owners.” Reject shared reachable behavior/scenario/diagnostic owners or establish an explicit immutable boundary, then add a controlled-indicator alias negative through the real install path.

- **FOLLOW-UP — TEST/TOOL:** A08 protocol vectors are synthetic and explicitly independent of compiled receipts ([`a04-report-contract.ps1:398`]($REPO/tests/contracts/a04-report-contract.ps1:398)); the production report is self-emitted marker/count data ([`A08MutationDeveloperVerifier.java:49`]($REPO/src/com/lushprojects/circuitjs1/client/A08MutationDeveloperVerifier.java:49)). Also, debug-off readiness casts fields to `[bool]` without type validation ([`verify-a03-browser.ps1:735`]($REPO/scripts/verify-a03-browser.ps1:735)); current bridge output is boolean, so no live false-string pass was demonstrated.

VALIDATION: `git diff --check` PASS (exit 0; line-ending warnings only). Branch/status inspected with per-command `safe.directory`; expected uncommitted A08 changes remain. Existing 444-assertion report-contract evidence was treated as protocol-only.

REVIEW: Complete independent read-only review. No further confirmed production blocker found in the repaired declaration, inventory-ID, or diode-readiness changes.

LIMITATIONS: JDK/GWT build, compiled A08 matrix, browser/player flow, and runtime alias reproduction were not run per request. Pre-delta compiled logs do not qualify this candidate.

GIT/RESOURCES: No publication actions; pre-existing `tests/contracts/__pycache__/` preserved. Resources: NONE.

NEXT: Repair reachable fresh-owner behavior/callback aliasing, add the negative test, then rerun root-owned native, compiled, and browser gates.

## a08-callback-review.md

STATUS: REVIEW ONLY — A08 delta not qualified

SCOPE: Read-only review of the uncommitted callback-owner repair on `codex/task43p`, HEAD `c5a0d5e31ce9848b0e06b7c04c25866034d3d090`. No edits, build, browser, staging, commit, push, or email.

REVIEW: **BLOCKER — nested family-state callback aliases remain undetected.**

`requireDisjoint` rejects only the same family-state object, while `executionOwners()` walks operation/retest objects and executor identities but not captured fields ([FreshGeneratedRuntimeInstallation.java]($REPO/src/com/lushprojects/circuitjs1/client/FreshGeneratedRuntimeInstallation.java:163), [FreshGeneratedRuntimeInstallation.java]($REPO/src/com/lushprojects/circuitjs1/client/FreshGeneratedRuntimeInstallation.java:226)). The family-state interface has no owner declaration ([GeneratedBoardFamilyState.java]($REPO/src/com/lushprojects/circuitjs1/client/GeneratedBoardFamilyState.java:3)).

Concrete current paths:

- Controlled family: a fresh `FamilyState` can capture the retired behavior; its HIGH/LOW and retest callbacks dereference it ([ControlledIndicatorDeviceBehavior.java]($REPO/src/com/lushprojects/circuitjs1/client/ControlledIndicatorDeviceBehavior.java:593)).
- NPN/NMOS: a fresh family state can capture an old `SwitchElm`; callbacks toggle it ([NpnLowSideSwitchFamilyState.java]($REPO/src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchFamilyState.java:11), [NpnLowSideSwitchFamilyState.java]($REPO/src/com/lushprojects/circuitjs1/client/NpnLowSideSwitchFamilyState.java:79)). Scenario selection invokes those operations during installation ([GeneratedChallengeController.java]($REPO/src/com/lushprojects/circuitjs1/client/GeneratedChallengeController.java:74)).
- RC: `RcDelayFamilyState` captures temporal behavior, while temporal behavior accepts endpoint objects without candidate ownership validation ([RcDelayFamilyState.java]($REPO/src/com/lushprojects/circuitjs1/client/RcDelayFamilyState.java:9), [RcDelayTemporalBehavior.java]($REPO/src/com/lushprojects/circuitjs1/client/RcDelayTemporalBehavior.java:39)).

These fresh wrappers evade identity checks yet can mutate/read retired state. This violates “reject before mutation.”

PASS (static review):

- Scenario owner declaration, owner collection, and reverse-copy preservation are present ([GeneratedScenario.java]($REPO/src/com/lushprojects/circuitjs1/client/GeneratedScenario.java:86)).
- Definition/behavior/receipt checks and direct callback identity checks are present ([FreshGeneratedRuntimeInstallation.java]($REPO/src/com/lushprojects/circuitjs1/client/FreshGeneratedRuntimeInstallation.java:202)).
- A08 debug-off predicates require actual booleans and reject developer markers/reports ([verify-a03-browser.ps1]($REPO/scripts/verify-a03-browser.ps1:735)); contract vectors cover malformed readiness and leaked reports ([a04-report-contract.ps1]($REPO/tests/contracts/a04-report-contract.ps1:467)).

VALIDATION: `git diff --check` PASS (exit 0; only line-ending warnings). Build/browser/runtime results: NOT RUN per request; historical 17-suite/14-route evidence predates this delta.

GIT: Worktree remains unchanged and dirty with the pre-existing candidate edits and `tests/contracts/__pycache__/`. No publication actions.

NEXT: Add explicit family-state captured-owner validation (no reflection/deep-copy framework needed), add negative install cases for controlled/NPN/NMOS/RC aliases, then rerun fresh A08 qualification.
