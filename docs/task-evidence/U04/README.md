# U04 session, Resources, Settings and real inventory

Status: COMPLETE; final integrated qualification PASS. [Current evidence](../REL-A/final-shop/README.md).

The user's target-bound purchase restriction is corrected. The website Shop
shows the full real component catalog, grouped by public type/specification and
physical fit. Buying a resistor puts it in the actual tray; compatible empty
slots are chosen separately during installation. Acquired part identity and
source inventory survive repeated cross-slot moves.

Current execution6c330f50 has fresh native33 (including76 U04 assertions),
GWT5 and [Alpha38/230 mutation checks](../REL-A/final-shop/alpha-continuity-summary.json)
PASS. The unchanged UI adapter has88 assertions and syntax PASS under the input
audit. Fresh actual [Settings/Resources/menu/focus/privacy](../REL-A/final-shop/ui-focus-continuity.json)
and [RB15 generic repair/retest](../REL-A/final-shop/rb15-repair-continuity.json)
also pass. Launcher and meter follow-up fixes have separate focused proof.
Final exact replay, cancellation, error recovery and all affected compiled gates
also pass; see the release ledger.

The typed Java session owns menu, preparation, ticket, workbench, retest, results
and errors. Exact replay preserves signed-long text and rejects unsupported
identities before mutation. Random launches share the qualified family seed
selector. Revocable session tokens and modal leases reject retained controls.
Retest completion waits for input restoration on the same owner/controller/result.

The product bridge exposes public complaints, labels, replay/build identity and
real catalog/loose counts. It contains no private faults, original specifications,
solver graph or diagnostic proof. Seven existing package adapters acquire actual
loose parts without changing installed slots; removal and installation remain
separate actions. Failed mutations compensate through the existing typed owners.

Prior execution5ea881de evidence (retained history, not current qualification):

- Native56 session/replay/owner assertions PASS in
  [native17](../REL-A/native-contracts.txt).
- [UI adapter49 assertions and syntax](ui-contracts.txt) PASS. This jsdom fixture
  is separate from actual Browser input.
- [Actual final UI inputs](ui-input-entry-final.json) PASS: collapsed/expanded
  menu focus, Settings wraps/exact draft/return/persistence/restoration,
  Resources wraps and inert background, invalid epoch/HARD rejection, and
  MEDIUM family filtering. [Settings](settings-entry-final.png) and
  [Resources](resources-entry-final.png) screenshots were inspected.
- [Early Task43P privacy reproduction](task43p-early-privacy-pass-final.json)
  PASS on the final build: ordinary LED3 ticket with debug=false and forced-source
  verifier flags, no private report or matching ARIA/title leak.
- [Final normal player inputs](player-input-entry-final.json) PASS: exact RC3,
  visible cancellation preserving RC3/OFF, normal RB15 draws42/17, both known
  bad exact seeds rejecting without losing seed17/OFF, and every digit of
  exact9007199254740993. [Replay failure](replay-error-entry-final.png) inspected.
- Final [RB15](../REL-A/final-entry-privacy/rb15-player-input.json) and
  [MEDIUM](../U05/player-input-entry-final.json) visible repairs PASS: real loose
  acquisition preserves the original, separate physical removal/installation
  changes retained probe readings, unrepaired checks reject and repaired
  functional retests reach Results. Final debug-off privacy inputs include all41
  verifier flags and Task43P forced-source parameters. Final ordinary
  [Quick Play entries](../REL-A/final-entry-privacy/quick-play-player-input.json)
  preserve public seed/build identity and reach usable workbenches.

The previous b508 build's [normal entry/replay/cancellation checks](player-runtime-b508.json),
[RB15 repair](../REL-A/rb15-player-runtime-b508.json) and
[MEDIUM repair/privacy](../U05/player-runtime-b508.json) remain evidence for that
candidate; the links above contain the fresh final workflows. Older
[development evidence](development-history.md) preserves its own limitations,
including the unsupported Browser right-button argument; no new native
right-mouse proof is claimed from accessible black-probe controls.

The optional Astra worker owned only JavaScript/CSS and its focused UI contract.
Root owns integration and acceptance. [Integrated review](../REL-A/root-review.md)
records actual review scopes, fixes and remaining proof boundaries.
