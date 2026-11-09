# Qualified-prior metadata and receipt staging contract

Root current design decision before implementation/execution. This explicitly
defines a new current result-level contract with its owned producer and consumer;
it does not claim discovery of a legacy schema. Base3f8c6a64. Fifteen admitted
Source parents are pinned in SOURCE_INPUT_R1.json. No actual DATA/journal, old
classes, producer/original/Session/routing invocation is authorized. Normal main/
assembly remain closed; genuine original,NULL,OBSERVE stays UNSPENT_GATED.

## Schema authority and ownership

QUALIFIED_PRIOR_SCHEMA = `tsj-cohort-terminal-qualified-prior-v2`.
Ordered12 keys: schema,inputContextSha256,sourceParent,manifestSha256,
sourceManifestSha256,readerSha256,resultSha256,operation1Sha256,parentReceiptSha256,
priorRunId,priorOriginalReceipt,trialDisposition. The last ten retain the existing
input-prior semantic rules; current context SHA is separate from historical data.
Do not insert these two new fields into canonical input context.

The owned current context_codec.py APIs will construct this result map and validate
its exact schema/order, expected current SHA, ordinary JSON/domain/alias rules
and existing prior semantics. Existing context/factory behavior and bytes must
remain unchanged. Historical formats are rejected by the new API; no fallback.
Native constructor literal must match this frozen authority and paired tests.

New top map owns12 entries; nested prior receipt/trial remain borrowed exclusively
from validated input. Input must be the unmutated result of CurrentV2InputSetup
under caller ownership. No deep copy under12 credits, no input mutation, no alias
elsewhere in the serialized output. Full-body alias validation remains with the
existing codec; this component's source binding supplies no genuine host authority.

## Native component and exact logical census

CurrentAssemblyMetadata.prepare(meter,started,setup,currentContextSha,receipt,
expectedReceiptBytes,expectedReceiptSha,body) returns existing ReceiptContentBinding.
It creates only the qualified-prior map, prepares the actual-authority receipt
ticket while metadata is open, then replaces the existing null body.qualifiedPrior
slot. No new result/setup/ticket DTO or ledger. Normal caller composition stays
disabled. All compatibility checks happen before body mutation; receipt failure
leaves the caller body unchanged. Leave Meter open for the existing R7 close.

- Basic null arguments fail before source access.
- Body exact LinkedHashMap guard1, containsKey1, get1, combined required-null-slot
  predicate1:4 units, before input or receipt work.
- Current SHA length1 plus64 lowercase-hex character controls:65 units on success.
- SetupResult.data observation/type guard1; root data.get("prior")1:2 units.
- Ordered10-key prior shape:6+5n=56 units (type,size,size predicate,entrySet,
  iterator,final false hasNext; per key hasNext,next,getKey,type,ordered equality).
  Loop induction belongs to its charged item operation; no app key array.
- Map allocation1 and copy12 before construction/stores; copy12 adds12 actual
  units and1 chunk. Ten actual prior Map.get observations add10 units. Twelve
  stores are covered by the twelve logical copy entries, not charged twice.
- Prepay outer existing body-slot replacement with copy1 plus1 put unit before
  receipt preparation. This adds2 actual units,1 entry,1 chunk. Perform the put
  only after ReceiptContentBinding.prepare succeeds. No later unrecorded access.

Successful component overhead =4+65+2+56+1+12+10+2 =152 units,13 entries,2 chunks.
Receipt.prepare retains its existing `116+10P+6B` actual unit delta, `2P+8`
entries and3 chunks; P is canonical path-chain length including root, B is
ceil(expected receipt bytes/4096). Total staging delta =268+10P+6B units,
2P+21 copied entries,5 chunks, actual Meter groups. Internal method invocation/
primitive VM operations add no independent data observation. Invalid attempts
may consume prepaid work; never refund or fabricate counters.

## Validation sequence

Root reviews/fixes the actual Source inventory before generation/compile/native.
Paired pure producer/consumer tests cover schema/order/current-hash/legacy rejection,
nested semantics/aliasing and unchanged canonical input/factory output. Native
canary uses the actual InputSetup/Meter with a root-generated synthetic current
factory and real task-owned receipt files. Tests compare delta to the independent
path/block formula, exact map order, input identity/borrowed nested objects, open
metadata, unchanged body on preparation failure and no snapshot/export premature
close. No fabricated OriginalArm/ArmResult or graph. Actual host and whole-result
semantics remain separate gates; no public-beta/playable claim.

All existing90s/640groups/5s/32768units/copy1MiB/chunk4096 and other Source/class/
process/cleanup caps, exact seeds, epochs, independent oracles and CircuitJS truth
remain. Full30EXCLUDED/HARD-PSYCHOTICunavailable-unrun/oldnormal20READhistorically-no
reread/protectedunreadUNCERTIFIED/playable100=0/betaHELD. Root owns publication;
workers are leaves and preserve other edits/released Source before corrections.
