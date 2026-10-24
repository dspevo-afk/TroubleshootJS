# Held public families reject before preparation

On base `fc88419b4e4dfe5f58cede8e23ce9a9cfab388f4`, the current replay
`tsj-alpha/6/MEDIUM/RB56_CONTROL/0` passed public parsing because registration
and an available difficulty were sufficient. The coordinator refused the family
before replacing the electrical owner, but the session had already begun and
entered ERROR. The public menu now refuses that replay inline, retaining its
current board and view.

`PlayerLaunchRequest` reuses the existing normal-player-enabled family guard.
Both Quick Play support paths require that flag too. Registration remains
available for private qualification; the coordinator still guards before cancel
and publication. No family/profile flag, wire epoch, candidate order, signed-long
transport, limit or electrical model changed. Enabled exact requests keep their
existing profile semantics, independent of broad-seed declarations. The valid
normal content and wire bytes are unchanged; no interpretation-version bump is
needed for earlier refusal of already-held content.

| Gate | Actual result |
| --- | --- |
| Pre-fix native canary | Expected FAIL, actual1 /41.418s, exact constructor admits the held family;1378 inputs unchanged, cleanup PASS. The current-epoch/available-profile oracle is retained byte-for-byte. |
| Post-fix identity canary | PASS201 assertions, actual0 /42.272s,1378 unchanged, cleanup PASS. |
| Final native6 | Actual0 /59.625s;66293 counted assertions plus uncounted QuickPlay construction PASS;1378 unchanged, cleanup PASS. Full matrix/independent population oracles NOT RUN. |
| Actual final JDK8/GWT build | Actual0 /98.717s, five permutations,1191 source/build/served-UI inputs unchanged. |
| Actual loaded production build | `0583868ADE0F4D276EA89F45DF5AEF21`; exact localhost script response matches final built file. |
| Real player replay/repair input | PASS within the flow below; five useful screenshots inspected. |
| Final consumed-input audit | PASS: native1378/build1191 and loaded compiled file unchanged after UI work. |
| Owned resources | Tabs52/53 closed; both preview child births revalidated, maintained stop actual0 with state/listener absence; both drivers ended0. No profiler. |

Native commands use `scripts/verify-current-contracts.ps1 -JavaHome JDK8 -Suite`
with `QuickPlayContractTest`, `QuickPlayGateContractTest`, `U04SessionContractTest`,
`U06SessionSaveContractTest`, `RelBSessionIdentityContractTest`, and
`A10DependencyContractTest`. Build command is
`scripts/build.ps1 -JavaHome JDK8 -Style OBF -Target Compile`.
Command elapsed values include their own completion/cleanup; separate native
cleanup duration was not instrumented. Preview stop operations are separately
timed:2.136s pre-fix and2.151s post-fix. [Receipts](final-native.json),
[build](final-build.json), [loaded binding](compiled-binding.json),
[final audit](final-input-audit.json), [source review](source-review.json).

Real production input uses the normal menu and visible controls, without injected
controller actions. Before the fix, the held replay reached
[Board preparation failed](pre-fix-error.jpg). After the fix:

1. Accept ordinary LED/3, switch power OFF, open menu and share the current replay.
2. Submit the same held replay. The [inline refusal](post-fix-refusal.jpg) keeps
   MENU, the `LED_INDICATOR/3` public identity and power OFF; Resume remains usable.
3. Resume and power ON. The [unrepaired customer retest fails](retained-unrepaired.jpg).
4. Power OFF; inspect R1's BROWN/BLACK/RED/GOLD bands; remove it; acquire1000Ω
   from Shop; select Part4 in Parts Tray and install as Circuit/R1. Power ON;
   [customer retest passes](retained-repaired.jpg).
5. Return to menu and submit `tsj-alpha/6/EASY/LED_INDICATOR/9007199254740993`.
   The [new ticket](valid-long-replay-ticket.jpg) shares the exact long seed.
   Accept it, switch power OFF, close the tab, and stop the exact owned preview.

[Full flow and observation limits](player-flow.json). Some immediate AX snapshots
were stale; terminal DOM/screenshots supply proof. A pre-fix native AX click failed
shadow-root lookup; semantic keyboard/menu input and later terminal observation
were used. A post-removal Component lookup timed out after selection was cleared;
fresh DOM led to the actual Parts Tray path. No wrapper certification follows.
Pre/post viewport sizes differ, so this is behavior evidence, not layout evidence.
Public identity/power continuity and repair/retest were observed; no hidden browser
object identity assertion was injected. Native codec/private identity checks are
separate from UI replay proof. No new advanced normal-save, population, capacity,
HARD calibration or protected-regression claim. The product choice in the
[preparation-cost analysis](../../U05/preparation-cost-scope-r1/README.md) remains
pending. Root reviewed the integrated source/callers/tests; no independent review
was required or claimed. Five older blocked profiles and old tab20 were untouched.
