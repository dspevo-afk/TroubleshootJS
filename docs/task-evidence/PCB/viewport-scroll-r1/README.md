# Local toolbar scrolling accepted

At319x670, native toolbar focus scrolled the GWT ancestor and moved the PCB
canvas out of view. Actual public LED3 reproduced30px movement; the previous
private54 capture showed383px. The baseline failures remain in this packet.

The static toolbar TBODY and header shell now own horizontal scrollports within
the unchanged112px dock. The positioned TABLE remains the meter containing block,
so the absolute bench projection stays outside TBODY clipping. Native controls
and top-layer popovers retain their owners. The existing bridge-guarded focus
listener reveals focus-visible chrome only inside its own scrollport; pointer
focus, meter controls and popovers are excluded. No ancestor reset, meter
reparenting, Java, graph, probe, PCB geometry, seed or interpretation change.
The containing-block clipping rule is described by the primary CSS specification:
https://drafts.csswg.org/css2/visufx.html#overflow .

Root final DOM runs PASS: U04 130 assertions/1.346501s; bench meter380/0.3986517s.
Pinned jsdom26.1.0's1,236 dependency files remain byte-identical. Fixture corrections
are explicit in fixture-review.json: missing available(), first visible menu
control, and detached inertness plus reattached stale-lease rejection. Original
wrap/stale-owner expectations remain. Initial duplicate-const parseFAIL and
reverse-focusFAIL are retained; an intermediate stale-card failure was observed
by the worker but has no separately retained raw log. DOM mocks are not input proof.

Actual final production JDK8/GWT5 build PASS94.9011382s including bounded cleanup;
cleanup duration not separately measured. First recorder incorrectly marked null
LASTEXITCODE as FAIL for a normally returning PowerShell script. Original recorder
result remains; maintained numeric child-exit check, normal completion, compile/
link markers and no remaining Java process justify the corrected build receipt.

Actual public LED3/319 canary PASS:3px-visible Settings edge click opens the modal;
Tab reveals its full button with local header scroll39; canvas staysx0. PartsTray
scrolls only the ribbon30px, vertical0; meter and popover remain visible. Actual
meter drag10px/10px, arrow movement/Home reset and J1 left-red/right-black probing
work; DC V displays12V. Actual normal1280 desktop Fit/Settings via keyboard/
Close/PartsTray also pass. A new tab initially inherited319; that capture was
renamed narrow and1280 was explicitly applied/observed before the desktop check.

Final private root42 current14 cold54 retry PASS85.321s/622work/max1.535s. Actual
unrepaired customer retest fails. Narrow Fit/PartsTray/Tab keeps canvasx0 and root
scroll0; full Settings focus, meter and popover remain visible. Actual FileReader
loads preserved saved13 in87.308s/622work with a fresh owner/prior retirement.
OFF and Part30/RDA survive. Narrow and1280 desktop controls remain local; powerON/
customer retest/Close pass and all7 owner cleanup flags are true, pendingfalse.
Total qualifier wall230.628s includes cold, restoration and player input. Limits
stay90,000ms/640work/5,000ms per unit. Both browser warning/error logs are empty.

The first same-source run cold86.885s and functional restore/customer/Close all
passed, but restore elapsed90.048s fails the strict wall gate. Its exact reports,
DOM and captures remain under timing-attempt1. Source inspection explains that
GenerationJob checkpoints before atomic publication, then samples elapsed time
afterward without revoking a committed owner. No timer, budget or oracle was
changed. One same-seed/file/source retry passed; this is not an all-run timing,
modest-host or performance-margin claim. Both outcomes remain visible.

Earlier CSS canary fixed horizontal movement but failed vertical fit; its exact
source/capture remain under attempt1. Candidate2 was launched only, NOT RUN.
Refined CSS then exposed partial keyboard Settings focus; that failure remains
under focus-before-adapter. The final JS focus adapter corrects it. Five selected
inspected unedited captures: focus-adapter-keyboard.jpg, focus-adapter-probes.jpg,
final-private-narrow.jpg, final-private-desktop.jpg, final-public-desktop.jpg.
Private captures are from the first functionally passing run on the same frozen
candidate; they provide visual scope, not strict timing proof. Other captures
are retained as canary/diagnostic evidence.

Selected56 native proof is reused only at the byte-audited unchanged Java/scripts/
jars/Java-fixture boundary from accepted5ad170cd. The broad1,447 snapshot includes
the changed Node mjs fixture and is not wholly unchanged. Accepted compiled-web
reconstruction matches; fresh GWT output is identical except the two UI assets.
Fresh final1,447 input audits and all five independently expected served identity
fields pass. Current web0730f55f3a2e55a3795c415dfbc30b2772e0177a24bf3ee93bdec8abbf2df8a0.
No new shrinkage/electrical claims. Full native matrix and independent electrical
oracles NOT RUN. Normal advanced/HARD, all-seed and manufacturing gates stay held.

Root reviewed the actual integrated source/test diff. Optional later packing-map
agent followup did not run due selected-model capacity; it supplies no proof and
is not required. check_acceptance.py checks the final recorded gates and requires
the prior timing failure remain explicit. All owned previews/tabs/viewport and
both exact temp directories are released. A combined force-cleanup command was
rejected before execution; smaller literal removals without force succeeded after
containment/reparse/inventory/hash verification. Original five untracked entries,
shared toolchains and unrelated HTTP.sys PID4 are preserved. No main merge/email.
Next PCB scope: candidate0-only advisory spatial ordering of mixed package
isolation courtyards, under strict14-case routed regression gates.
