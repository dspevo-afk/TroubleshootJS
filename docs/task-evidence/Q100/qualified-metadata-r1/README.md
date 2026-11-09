# Qualified-prior metadata and receipt staging

Accepted only for the bounded component in [PROOF_R1.json](PROOF_R1.json).
Normal assembly remains closed. This adds the current result-level schema,
its paired Python producer/validator, and native metadata construction followed
by the existing receipt preparation. It grants no genuine producer authority.

`context_codec.qualified_prior` creates twelve ordered fields under
`tsj-cohort-terminal-qualified-prior-v2`; `validate_qualified_prior` checks that
schema, order, expected current context hash, JSON domain, nested prior semantics
and aliases. The previous 21,653-byte codec prefix is unchanged. Canonical input
context remains 66,485 bytes and generated Java remains 21,404 bytes, both exactly
equal to the pinned independent baseline outputs. No input schema/model changes.

`CurrentAssemblyMetadata.prepare` requires an exclusively owned, unmutated,
validated InputSetup result and an owned body with a null qualifiedPrior slot.
It creates one top map and borrows the two nested prior objects. It prepares
the existing receipt ticket before replacing the body slot, so preparation failure
leaves the body unchanged. It leaves metadata open and returns the existing ticket.
There is no new DTO, ledger, input mutation, meter close or normal caller wiring.

The helper checks current-hash format; authenticating the actual current hash is
a future caller precondition. A different valid lowercase hash is intentionally
accepted by the helper and rejected against the expected hash by the paired
consumer. Whole-body alias exclusion and host authentication remain separate.
The [contract](COMPONENT_CONTRACT_R1.md) fixes these boundaries before execution.

## Validation

| Gate | Result | Operation / wrapper ms |
| --- | --- | --- |
| Final public existing context tests | PASS11 | 694.818 / 925.5247 |
| Final public qualified-prior tests | PASS18 | 746.5232 / 978.6661 |
| Fresh final-source JDK8 target7 build | PASS862 Sources,2481 classes,11936633B,all major51 | 5020.0866 / 5262.7458 |
| Actual Windows receipt/meter smoke, first native gate | PASS7 | 445.9801 / 690.3523 |
| Native negative/lifecycle suite | PASS17 | 556.4672 / 787.7192 |
| Paired Java output / Python producer and consumer | PASS; exact1805B, expected-hash rejection | Root audit, no separate phase timer |

The24 named records exercise30 metadata attempts: six successes and24 expected
rejections. Six final ticket checks include one expected changed-content rejection
after restoring the file's timestamp. Case names/order were frozen before execution;
no assertion total was invented. [Final gates](FINAL_GATES_R1.json),
[case manifest](CASE_MANIFEST_R1.json) and [build inventory](BUILD_INVENTORY_R1.json)
bind the evidence. Source/API/census/cleanup review found no blocker; independent
review was Source-only, not another native execution.

The helper overhead is152 actual units/13 copied entries/two chunks. Including
the existing receipt ticket it is268+10P+6B units,2P+21 entries/five chunks, where
P includes the canonical path root and B is ceil(receipt bytes/4096). The actual
P10 fixtures measured374/41/5 for one block and380/41/5 for two blocks. Input setup
retained10007/478/3 and its closed group. Only the harness closes metadata later.

The Root final-audit script first selected an ambiguous anchor, then incorrectly
changed it to CRLF, then added a redundant LF to an already framed canonical
value. These audit failures remain failures; all raw script revisions are retained.
Source inspection fixed the unique LF anchor and exact one-LF comparison. The
initial line-ending diagnosis was incorrect and is explicitly corrected in
[FAILURES_R1.json](FAILURES_R1.json). Component Source never changed. The final R4
audit reauthenticated unchanged Source tests, compile/native actual argv and
process receipts, tools, JARs and every class byte; no unchanged JVM was rerun.

## Replay and limits

Python used `-I -B`, optimize0. The new public test is adjacent to the current
codec and accepts `--source-root <pinned-source-root>` for the two baseline Source
files identified in [SOURCE_INPUT_R1.json](SOURCE_INPUT_R1.json). It extracts only
the explicit synthetic fixture function from pinned test Source; it does not run
the old test module body or any old normalizer/whole consumer. Baseline context
tests run against the current adjacent codec. Private pinned Java parents remain
required for native replay. The public drivers must be copied to an admitted
task-owned root with those exact inputs and Root freeze/control manifests; this
packet is not a standalone normal host or permission to invoke old assembly.

Native Java used `-ea -Xmx512m`, fresh classes and two pinned GWT JARs. No old
classes enter the classpath; actual producer Study/Mode/ArmRun and OriginalArm
classes are absent. Explicit malformed SetupResult wrappers are negative guard
probes only. Successful tests use actual InputSetup/Meter and partial body fixtures,
with no fabricated OriginalArm, ArmResult or graph.

Each case reports operation and exact-leaf cleanup time separately. All created
case files/directories were removed; the smoke's synthetic metadata and its owned
directory are intentionally retained, and the suite directory is empty. Selected
children terminated and owned process roots/logs are absent. Separate process
cleanup duration was not measured. No persistent process or link was launched;
uncertain prior resources and unrelated checkouts remain untouched.

GWT/visible Player are NOT APPLICABLE: no production or visible flow changed.
Electrical/population evidence is NOT RENEWED. All existing budgets, exact seeds,
epochs, independent oracles and CircuitJS truth remain. No actual prior DATA,
old DATA class, protected/held-out DATA, producer, Original, Session or routing
run was accessed. Historical old-normal20 reads and the UNCERTIFIED protected
unread boundary remain. Full30 is EXCLUDED; HARD/PSYCHOTIC remain unavailable/unrun.
Playable100=0, beta HELD and genuine original then exact NULL/OBSERVE UNSPENT_GATED.

Next is the current body/assembly composition prerequisite and existing R7 close/
ticket flow. Full result, normal host/lifecycle, actual original caller and playable
Q100 remain unqualified.

Published implementation `d374e29b33fa9649f332571d64255b804746953a` was normally pushed
to origin/codex/q100-physical-feasibility and verified remotely. Completion Gmail
`1a1c118fb5dedb5a` was sent once. This documentation receipt sends no second email.
