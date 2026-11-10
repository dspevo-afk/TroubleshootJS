# Capture-bound body staging contract

N31 is one atomic composition in CurrentAssemblyMetadata. It connects the
capture DTO's netIds to cohort derivation and carries exactly that same capture
Map into the pending body. It does not introduce a normal producer, new DTO,
validator, physics, source of authority or alternate cohort algorithm.

Source basis: metadata12486B/49f9930a..., codec43095B/92cd938f... and projections
10055B/c0398d31...; selected current originalDto671..728 and deriveCohort551..597.
The independent current pure kernel's selected _validate_original_capture and
validate_full_d_v2 Source retain semantic/receipt/identity/derived obligations.
They are not executed here. Snapshot main/assemble stay unconditional throws.

API: captureBoundBody(meter,setup,runId,sourceSha,contextSha,originalCapture,arms).
No separate captured-ID-list parameter. Reject any null required argument with
METADATA_ARGUMENTS before charges or reads. Caller owns stable unchanged setup,
capture Map/list, headers and arms exclusively during construction and through
subsequent preparation/export. Intended integration supplies the actual validated
capture DTO; synthetic fixtures qualify only this Map/list binding. No actual
DTO validity or authenticity is inferred from their acceptance.

Charge1 immediately before originalCapture.get("netIds"). Its fixed List type
guard shares that paid observation, matching existing cleanupView. Null/missing/
non-List value -> METADATA_CAPTURE_NET_IDS before setup/cohort work. Container
lookup exceptions propagate after the paid get. Borrow the list without copying;
call existing cohortBeforeBody, then pendingCleanupBody with the exact original
Map. No body is constructed until derivation succeeds. Preserve inherited cohort
length/duplicate/pair/disposition/requested-order errors and their order after
the new capture read. Maps/lists are not mutated; returned cohort lists remain
detached as their existing owner specifies. Full input stability and borrowed
lifetime are preconditions, not proved for concurrent/adversarial containers.

Retain cohortBeforeBody and pendingCleanupBody as independently tested component
owners. The new composition is the future integration boundary; it delegates
their work once each and adds no duplicate state. No actual normal caller is
enabled, no old compatibility format/resolver is added, and no test owner API is
retired merely to make this finite binding appear complete.

Fee: existing cohort264units/125copies/64chunks + pending body14/13/1 + one
binding read =279units/138copies/65chunks on the chosen valid synthetic input.
Only +1 unit/get, no new copies/chunks/explicit container/group/Meter/byte work.
This is component accounting, not whole-export/native-owner accounting. The
exact final-source native run must confirm it, with input preparation outside
measurement. Null arguments cost0; bad IDs value costs1; throwing get costs1.
Real640-closed-group rejection must occur before capture/setup reads or copies.

Finite synthetic proof: same capture/arms Map identities and pending slots;
non-prefix pair and literal independent60-ID requested order; detached cohort
lists; exactly one admitted capture get; unchanged setup/capture/arms trees;
missing/null/wrong-type netIds; short/duplicate/missing-pair vectors; changed
requested/disposition values and earlier capture-vs-setup precedence; every null
argument; propagated lookup exception; real group-cap refusal. A decoy captured
list may exist in a fixture but cannot be passed through this API. Source checks
must bind signature/delegation and exact inverse, not manufacture a golden from
the producer. Do not duplicate or weaken retained cohort/codec/receipt oracles.

Only ordinary synthetic Maps/Lists/scalars/SetupResult metadata plus actual
Meter execute. No actual/fake OriginalArm/ArmResult, graph, Session, factory,
positive originalDTO/executeArm/R9DTO, receiptfile, positive prepare/binding,
routing/exportV2 or genuine envelope. No actual cleanup/effects/geometry/SHA
truth, capture provenance, full DTO, authority, lifetime or whole-body zero-output
qualification. Normal caller/host/lifecycle integration remains unstarted.

All90s/640/5s/32768/copy1MiB/chunk4096/Source4MiB/fragment64KiB/opJSON8MiB/
result1MiB/depth64/key1m/class10k256MiB/compile-suite60s/reader180s/wrapper480s/
cleanup120s limits and exact seeds/epochs unchanged. HARD/PSYCHOTIC unavailable/
unrun; full30 EXCLUDED; oldnormal20 not reread; protectedUnread UNCERTIFIED;
playable100=0,betaHELD,genuineUNSPENT_GATED. Production/GWT/Player N/A;
electrical/population NOT RENEWED; CI NOT RUN. All gates initially NOT RUN.
