# Current result-root constructor

Accepted only for the staged root constructor in [PROOF_R1.json](PROOF_R1.json).
The existing codec now owns construction as well as the authoritative header
constants and thirteen-key order. Normal assembly remains closed. Metadata/R7/
receipt-ticket composition and whole-result/host qualification remain separate.

The package-private `CurrentV2CanonicalCodec.createBody` prepays13 entry copies
and one map allocation, then returns one exact LinkedHashMap with thirteen ordered
entries. It borrows four child maps, preserves direct header references and creates
present-null qualifiedPrior/work slots. It performs no child getters, traversal,
deep copies, extra validation, meter close or manual counter writes. Success costs
exactly14 actual units/13 copied entries/one chunk. Fixed schema-key references
are included in the explicit logical entry-transfer model, not billed as runtime
Source DTO getters or individual VM operations. See the
[pre-execution contract](CONSTRUCTOR_CONTRACT_R1.md).

Header strings and child maps are caller-prevalidated and must remain exclusively
stable through later preparation, R7 and export. Construction supplies no header
authentication. Bad or incomplete values can be staged; the existing codec still
rejects them at serialization. Only the basic null-Meter API guard is added.
All six child slots must be Maps before serialization, after metadata and R7 have
filled the two placeholders. The independent ownership guard remains necessary.

## Final validation

| Gate | Result | Operation / wrapper ms |
| --- | --- | --- |
| Final public Source tests | PASS10 | 611.3926 / 844.5271 |
| Final public emitter replay | PASS; exact compiled codec bytes | 342.1488 / 577.8525 |
| Fresh final-source JDK8 target7 build | PASS861 Sources,2483 classes,11916805B,all major51 | 4803.6304 / 5036.0412 |
| Actual-Meter smoke, first native gate | PASS1 | 346.4219 / 586.4562 |
| Native constructor/header/oracle suite | PASS15 | 5470.0711 / 5882.264 |

The16 names and expected case protocol were frozen before native execution.
Cases prove literal key/header order, two null slots, four child identities,
fresh roots, unchanged inputs, zero observations of trap children, exact charges,
copy/group/deadline rejection and normal group start/roll. The640-group boundary
also proves13 copies are admitted before the allocation fee can be rejected.
Absence of a map allocation on that failure is established by exact Source order
and no returned root, not VM allocation telemetry.

Existing header errors still reject wrong run/source/context, schema/mode/status/
wire values, missing child Maps and the two incomplete slots. A completed generic
synthetic tree matches the unchanged independent
`CurrentV2CodecOracle.BODY_JSON` bytes and SHA256. Its children intentionally fail
normal full-D semantics; it is not a genuine capture or a full-result fixture.
Formatter/header/marker implementation bytes outside the1539-byte member insertion
are unchanged. The baseline18580-byte codec becomes20119 bytes; no header owner or
root-key array is duplicated.

Four selected emitter CLI controls exercise successful emission and rejection of
existing output, same-length altered baseline Source and altered member Source.
Exit2 is EXPECTED REJECTION, not native/producer success. Source tests also cover
strict unknown-argument rejection, unique anchors, limits and reparse checks.
No new OS API or global policy was introduced; the bounded stable IO pattern is
retained. The final public emitter reproduces the actual compiled codec exactly.
Final Source/tool/JAR/class bytes, actual compiler/native argv and process receipts
were reauthenticated after public replay; unchanged native runs were not rerun.
[Final gates](FINAL_GATES_R1.json), [case manifest](CASE_MANIFEST_R1.json) and
[build inventory](BUILD_INVENTORY_R1.json) bind the evidence.

## History, replay and limits

Before any execution, Root clarified the contract's shorthand meter type to the
actual CohortTerminalTelemetryStudy.SetupMeter. The initial contract raw bytes
remain preserved; its fee model did not change. An unreleased emitter draft's
unnecessary Windows API layer was removed in favor of the established IO pattern,
and isolated child Python/strict CLI/unittest forwarding were corrected before
first release. Those drafts were never executed; no complete raw-draft history
is claimed. All executed Source/native gates pass. See
[Source history and limits](SOURCE_HISTORY_AND_LIMITS_R1.json).

Root reviewed final Source, API, accounting and integration. A separate leaf
Source audit established ownership before design; it was not a final independent
code review or another native run. Existing independent formatter expectations
were preserved rather than replacing their oracle with constructor output.

Python used `-I -B`, optimize0. Source tests accept
`--source-root <pinned-source-root>`; the generator takes Source/component/existing
output roots and refuses to overwrite a file. Public baseline codec Source plus
the member are sufficient for Source emission. Native replay additionally needs
the private exact Study/Shared/PCB parents, two pinned GWT jars and Root freeze/
control manifests. Java used `-ea -Xmx512m` and only fresh classes plus those jars.
Actual producer Study/Mode/ArmRun and telemetry Study OriginalArm classes are
absent. No OriginalArm, ArmResult or electrical graph was fabricated.

Cases report operation and cleanup separately. Expected expired meters are
explicitly discarded without OS resources; unexpected cleanup errors fail. The
5.1s group case tests rejection after expiry, not successful work inside5s.
Selected children terminated and owned process roots/logs are absent. Separate
process cleanup duration was not measured. Controlled Sources/classes/history and
unrelated work remain; uncertain prior resources were untouched. No persistent
process or link was launched.

All90s/640groups/5s/32768units/copy1MiB/chunk4096 and other retained caps, seeds,
epochs, independent oracles and CircuitJS truth remain. GWT/visible Player are NOT
APPLICABLE: no production or visible flow changed. Electrical/population evidence
is NOT RENEWED. No actual prior DATA, old DATA class, protected/held-out DATA,
producer, Original, Session or routing run was accessed. Historical old-normal20
reads and the UNCERTIFIED protected-unread boundary remain. Full30 is EXCLUDED;
HARD/PSYCHOTIC unavailable/unrun. Playable100=0, beta HELD and genuine original then
exact NULL/OBSERVE remains UNSPENT_GATED.

Next is synthetic constructor/metadata/R7/receipt-ticket composition. Whole result,
actual host/lifecycle, normal assembly and playable Q100 remain unqualified.

Implementation `5ab70e8e7dd2b2fdb306d684c77c09adc17521b1` was normally pushed to
origin/codex/q100-physical-feasibility and verified remotely. Completion Gmail
`1a1c183b69b70eef` sent once; this documentation receipt sends no second email.
