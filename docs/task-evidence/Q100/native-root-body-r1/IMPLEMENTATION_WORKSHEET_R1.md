# Implementation worksheet: current root-body constructor source

## Scope and authority

Implement one package-private CurrentV2CanonicalCodec.createBody method and
the bounded, pinned source emitter for that member. This work is limited to
source construction. It does not authorize normal assembly, metadata/R7
composition, host qualification, DATA, legacy builders, native execution, or
changes to the genuine capture gate.

Authority: CONSTRUCTOR_CONTRACT_R1.md, current SHA-256
b544bb8e9e41277b1593307a751231f562f8ac0669dc72822af7688eeb128789.
The pinned baseline codec is CurrentV2CanonicalCodec.java, SHA-256
76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522,
18,580 bytes. The admitted reference emitter is
source/generate_body_ownership.py, SHA-256
29ebb32d23cd4f66b000499cb0d599c678fcbe718bd4f91d726f0e49ef075970.

## Implemented source contract

createBody accepts the actual owner type
CohortTerminalTelemetryStudy.SetupMeter, three prevalidated header strings,
and four borrowed child maps. The only guard is a null meter, reported through
the existing failure("BODY_CONSTRUCTOR_METER") helper before any copy,
allocation, or child observation.

Successful construction calls meter.copy(13) and meter.charge(1) before one
java.util.LinkedHashMap<String, Object>(32) allocation, then performs 13
ordered puts using BODY_ROOT_KEYS[0..12] and returns the map. The fixed
header values and two null placeholders follow the contract. No input mutation,
child copy/traversal, extra validation, getter, snapshot, finish, export, or
manual meter update is present.

The first draft used the shorthand Study.SetupMeter from the initial contract
wording. Source review corrected it to the actual nested owner type before
source inventory release; no draft was executed or published. The root retained
the original contract history separately.

## Emitter and source controls

The emitter pins the baseline codec and exact member bytes, checks a unique
private-constructor anchor, inserts the member once, and writes only an
exclusive CurrentV2CanonicalCodec.java output. Reads and output are capped at
4 MiB. Path checks reject symlinks/reparse points; path and handle snapshots
check identity and stability, and snapshots intentionally exclude atime. The
emitter CLI accepts only its three declared arguments. The test harness alone
uses parse_known_args to accept --source-root and forward remaining unittest
arguments.

The source-only test file contains 10 tests covering positive exact insertion,
strict CLI arguments, source and member pin failures, exclusive output,
unique-anchor/order preservation, input/output bounds, the 13 indexed stores
and prepayment/allocation order, the actual meter type, atime-independent
snapshots, and mocked lstat reparse metadata rejection.

## Validation state and next action

Static review only: PASS for source inventory and contract alignment. The
Python tests, emitter, generation, compilation, native checks, and runtime gates
were NOT RUN by this leaf worker. No process, build output, or task scratch
resource was created. The root should review the exact source inventory and
then decide which source-only checks and permitted execution gates to run.
Normal main/assemble remain closed; the genuine original capture remains
UNSPENT_GATED. No metadata/R7/host completion or player qualification is
claimed.