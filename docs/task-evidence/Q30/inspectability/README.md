# Q30-P1 compiled workbench inspection

The developer-only preview uses the production CircuitJS PCB workbench, the
actual `Rb30Plan` assembly and the selected `MEDIUM_BOARD@1` routed layout.
It is not Q30 normal-player admission or a diagnosis, service, repair or
customer-retest proof. A generated LED challenge bootstraps the debug verifier,
so its inherited screen heading is a preview limitation, not Q30 product copy.

## Failed compiled attempts preserved

The first five-permutation JDK 8/GWT build routed seed 0 with P07 fuller but
rejected `RSB` because the provider attached non-resistor metadata. The exact
receipt is [pre-metadata-failure.json](pre-metadata-failure.json). Typed Q30
part metadata and a seven-topology native contract repaired that cause.

The next compiled build routed seeds 0 and 37, then failed before owner transfer
because the verifier called a normal-family ownership check on its developer
fixture with no family state. Both original owners remained intact; see
[pre-runtime-failure.json](pre-runtime-failure.json). The verifier now checks
Q30 fixture ownership directly and reports the failed phase and cleanup state.

The phase-instrumented build reached copper-face proof on both seeds, with
82/82 pads resolvable on each face and no overview or local pad limits. Its
first selected via marker rasterized into the drill exclusion at overview
scale. [pre-via-failure.json](pre-via-failure.json) preserves those two failures.
The verifier now requires an exact production copper hit on each annular land
at a bounded local inspection zoom, with matching solver endpoint and captured
target invalidation after flipping.

## Final-source compiled inspection

The `p1-gwt-build-final-source.log` build compiled five permutations and linked with
exit 0. The task-owned preview on port 8901 retained successful seed 0 and
held-out seed 37 boards. [Machine-readable receipts](compiled-workbench-results.json)
record `PASS`, the selected `P07_FULLER_TWO_LAYER` route, `MEDIUM_BOARD@1`,
33 packages, 82 pads, **82/82 overview and locally inspectable pad targets on
both faces**, no pad/overview limits, real top and bottom trace targets, and a
plated via whose face lands map to the same live CircuitJS endpoint. Each
final-source run completed 1,497 verifier assertions. The Q30 owner remained
retained for visible input, and normal admission remained false. An invalid
`tsjQ30Seed=bad-seed` emitted `FAIL` at phase `seed` with the original owner
preserved. When P07 and Q30 flags were both present, Q30 ran and passed; P07
did not retain a competing bench.

In the in-app Browser, the seed-0 top face showed the entry, regulator,
sensor/reference, drivers, relays and outputs as separate part groups. A
visible **View bottom copper** click showed only the underside trace view;
**View top copper** restored the part face. **Zoom +** enlarged bottom copper,
and **Shift-drag** panned it. After **Fit bench**, the **DC V** mode accepted a
left click on red `J1.1` and right click on black `J1.2`; the live meter read
**11.996 V**. Seed 37 also flipped to its bottom copper through the visible
control. No injected controller call is counted as player input.

Representative production screenshots:

- [Seed 0 top overview](seed0-top-overview.png)
- [Seed 0 bottom overview](seed0-bottom-overview.png)
- [Seed 0 bottom zoom and pan](seed0-bottom-zoom-pan.png)
- [Seed 0 top live probes](seed0-top-live-probes.png)
- [Held-out seed 37 top overview](seed37-top-overview.png)

These images were captured on the immediately preceding `p1-gwt-build-via.log`
build. The final-source edits affect only failure cleanup, seed-failure
reporting, policy identity assertion and combined debug flag precedence.
Renderer, layout, route, pad and copper hit inputs are unchanged. Final-source
positive receipts reproduce the exact seed-0/37 layout and copper identities,
so the visible-input screenshots are reused across that audited boundary. The
[pre-cleanup positive receipts](pre-cleanup-positive.json) compare identity,
layout, copper IDs, package/pad counts and target counts field by field; both
seeds match.

At the 1280×720 Browser viewport, some top part pin labels are small at fit
scale and the right sidebar extends horizontally beyond the viewport. Local
zoom makes individual pads and annular lands accessible. The board itself and
meter remain usable, but this preview does not prove a normal-player Q30
complaint or repair flow.

Independent review confirmed the failure-only stale power-binding risk is
closed: retirement detaches only the Q30 binding, and restoration preserves
the original or a successor binding. If a source control itself throws while
disconnecting, the stopped Q30 owner is quarantined and reports a failed
cleanup; that fault-injection limit is not a PASS. The final-source maintained
native suite subsequently passed 66 Java suites, independent oracles, report
protocol and cleanup; see the [full log](../p1-native-full.log) and
[receipt](../p1-native-full-receipt.txt). The preview process was task-owned (PID 22924,
start tick 639258245886285851); its [identity-checked stop receipt](preview-stop-receipt.txt)
proves process termination and release of port 8901.
