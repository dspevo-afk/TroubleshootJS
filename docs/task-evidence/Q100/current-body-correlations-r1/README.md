# Current body correlations: finite prerequisite accepted

The current qualification exporter now selects a higher-level codec entry for
three read-only correlations: prior/root input-context identity, fixed NULL and
OBSERVE arm roles, and cleanup/finalized-work duration equality. It validates
the existing header first and rejects a mismatch before encoding any bytes.
All three count/write/compare callsites migrate. The transport-only writeBody
entry and its independent encoding/domain/marker oracles remain unchanged.
This is a partial semantic contract, not whole-result qualification.

The maintained transform is a pure pinned bytes API with no file/CLI/producer
owner. It consumes the constructor-equipped current codec, not an old builder.
Source tests invert the transform to recover both exact inputs and prove only
the fragment insertion and three call names change. Contract, input/output pins
and exact public bytes are in SOURCE_MANIFEST_R1.json and the component's contract.
The exact fragment includes its separating blank line before the constructor;
its file-specific whitespace rule permits that pinned separator at fragment EOF.
No tested Source bytes change to satisfy repository line-ending checks.

| Gate | Result and scope |
| --- | --- |
| Source API | PASS:12 private and12 final public tests; incompatible, modified, mutable, oversize and already-derived inputs rejected |
| Final-source JDK8 | PASS:4 current Sources plus3 unchanged retained test Sources; source7/target7, all major51, no shadows |
| Current native | PASS:60 cases/210 assertions; valid literals and Integer/Long boundaries,48 exact-code preflight failures, fresh admission, unchanged body, real two-block R5 count metrics |
| Retained codec | PASS:63 cases/94 assertions across8 families, preserving1177/6370-byte literal oracles, domain/depth/visits, real1MiB cap and marker bounds |
| Independent Python | PASS:3 checks independently reconstruct the literal, two-block fixture and exact retained oracle identities |
| JVM rejection | Expected exit1 with exact CANARY_ARGUMENTS, not a positive run |
| Static review | PASS:root plus separate read-only agent; pins, conservation, all callers, bounds, admission and pre-encoding rejection |
| Receipt/input audit | PASS:10 selected process receipts reauthenticated;8 exit0,1 exact argument rejection,1 preserved failed launch |

The current compilation produced145 classes/376396 bytes with2408 dependencies/
11867237 bytes; retained test classes are inventoried separately in the proof.
Selected compiler operation times were1286.35ms and760.71ms; the current native
operation took365.74ms and final retained run484.71ms. Wrapper/operation times
are separate in private gate receipts. Termination and task-root/log absence were
proved for selected processes; isolated cleanup duration remains unmeasured.

Commands used the pinned JDK8 with `javac -source 7 -target 7 -encoding UTF-8
-Xlint:-options -classpath <frozen-current-closure> -sourcepath <empty> -d <output>`.
The current Source list was Study, codec, CurrentBodyCorrelationsCanary and
BodyCorrelationOracle; the retained compile used the existing codec canary and
its two oracle Sources. Native main classes were CurrentBodyCorrelationsCanary
and CurrentV2CanonicalCodecCanary with no arguments. The final extra-argument
control targeted the former. Python commands are in the component README.
The private host is not a standalone clean-clone build entrypoint.

Two harness failures remain preserved. R1 could not launch the absent retained
test class; R2 compiled it and Java returned0, then the private reader incorrectly
expected8 families as8 cases. R3 uses a Source-derived63/94 ledger and frozen
classes. Only the focused retained native suite reran. No body implementation,
literal, native assertion or budget changed. The ledger and FAILURES_R1.json
record the distinction; the failed launch is never counted as a passing gate.

Source review supports unchanged export fee/copy equations because the new
checks use existing block admission at position zero and add no Meter calls,
groups, copies or bytes. This is not per-field native-unit pricing. Real R5 count
equivalence was tested; full exportV2/write/compare/publication was NOT RUN.
Exclusive stable borrowed lifetime remains a precondition. Factory/SOURCE/receipt
authority and complete body semantics are not certified.

No actual/fake OriginalArm/ArmResult, graph, Session, factory, routing, positive
original DTO or genuine envelope ran. Production/GWT/Player are NOT APPLICABLE;
electrical/population evidence is NOT RENEWED; CI is NOT RUN. Existing limits,
exact seeds/epochs, data exclusions and full30 EXCLUDED remain. HARD/PSYCHOTIC
stay unavailable/unrun, old normal20 was not reread, protected-unread certification
remains UNCERTIFIED, playable100=0 and beta HELD. Prior scratch/failures, regular
hardlink fixtures and uncertain historical processes remain preserved.

Proof SHA256:031365e20a2aff7336901ed8e7e152a76ebafe33ecdc4ad303899b66d8715418.
Publication and notification state is recorded in PUBLICATION_R1.json.
Next implementation: bind copied qualified receipt metadata to the exact scalar
expectations supplied to the receipt binding, using synthetic metadata only.
