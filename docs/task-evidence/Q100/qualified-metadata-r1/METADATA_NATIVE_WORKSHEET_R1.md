# Qualified-prior native metadata source worksheet R1

Status: SOURCE REVIEW ONLY. The Java source was written and reread; no compiler,
test, generated factory, native class, producer, Original, Session, routing,
DATA, or journal was invoked or consumed. Normal main/assemble stay untouched.
Root owns integration, runtime evidence and publication.

## Candidate and admitted parents

Authority is COMPONENT_CONTRACT_R1.md, the explicit new current result contract,
with base `3f8c6a64a7c29d0343613e34baf4e70eedd8b4c9`. The fifteen Source parents in
SOURCE_INPUT_R1.json were checked with Get-FileHash SHA256 and file Length: all
15 match the manifest. This is a Source-byte identity check, not DATA admission.
The first read-only hash-check command had a PowerShell pipeline parse error;
the corrected command returned all fifteen matches. No Java was run.

Direct API parents read for this implementation:

| Source | SHA256 | API facts consumed |
| --- | --- | --- |
| CURRENT_STUDY.java | 524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4 | Existing SetupMeter.charge/copy and SetupResult.data; no new wrapper or counter |
| CurrentV2InputSetup.java | 7baab4caf45741f00d311e3b3854c794204356bfa03c0f3271399d44087df6a4 | Exact LinkedHashMap domain, prior key order and 6+5n shape algorithm |
| CURRENT_RECEIPT_BINDING.java | 1df7e06085165d025ff79bdd0cdaa7f603c110cc26b6cc6f280d73f52e8d2e41 | Existing five-argument prepare and existing ticket return; actual meter start binding |

Created source: CurrentAssemblyMetadata.java, 5704 bytes, SHA256
`e2b6b05bc95b9295d15aba5305a188c1eb98cb39db4b689747e2f126a6835198`.
The assigned source did not exist before this write. There has been no source
correction or replacement of an earlier released candidate in this worker.

## Construction and failure boundary

The package-private static method is
`CurrentAssemblyMetadata.prepare(SetupMeter,long,SetupResult,String,Path,long,String,Map<String,Object>)`.
It returns the existing ReceiptContentBinding and declares throws Exception.
No new result/setup/ticket DTO, ledger, persistent lifecycle state, factory or
source bridge is added. An ordinary internal Java method call and primitive VM
instructions have no separate API fee in this logical census.

Basic null checks for meter/setup/current SHA/receipt/expected SHA/body are free
and precede data access. There is no positive-nanoTime requirement: started is
forwarded unchanged to ReceiptContentBinding.prepare, which binds the actual
meter start. There is no finish, snapshot, export or premature metadata close.

The body must be exactly LinkedHashMap, already contain qualifiedPrior and have
null in that slot. These four controls run before current SHA, input or receipt
work. The current SHA must be exactly 64 lowercase-hex characters. SetupResult
data is read once with an exact-LinkedHashMap bounded guard, then prior is read
once. The prior map must be exactly LinkedHashMap with ten keys in the validated
input-helper order. Every observation/control is charged before inspection.

The new exact LinkedHashMap owns twelve ordered entries:

1. schema = `tsj-cohort-terminal-qualified-prior-v2`
2. inputContextSha256 = supplied current context SHA
3. sourceParent
4. manifestSha256
5. sourceManifestSha256
6. readerSha256
7. resultSha256
8. operation1Sha256
9. parentReceiptSha256
10. priorRunId
11. priorOriginalReceipt
12. trialDisposition

The last ten values are ten actual reads from the validated input prior. Fixed
switch metadata supplies keys without an application key array. The two nested
objects are borrowed by identity; their children are not traversed or copied.
This requires the exclusive caller-owned, unmutated CurrentV2InputSetup result
and no other serialized-output alias. Prior field/domain/semantic validation is
the existing input helper's responsibility. Full result alias validation belongs
to the existing codec. These preconditions are not authenticated host evidence.
Current identity is not inserted into canonical context and no input is mutated.

All body/SHA/shape checks and copy/store admissions happen before the only body
write. ReceiptContentBinding.prepare is then called while metadata remains open.
Only its successful return permits the prepaid body.put. A null/shape/SHA error,
meter failure, receipt authority/path/content/deadline failure or other receipt
preparation exception therefore leaves body unchanged. Invalid attempts may
consume admitted work; no refund or invented counter adjustment occurs.

## Actual source operation census

Counts below are source-derived successful-path expectations, not observed
runtime counters. Copy admissions add actual meter units and must not be omitted
or counted again as twelve extra store units.

| Operation | Explicit charge units | Copy entries | Copy chunks | Actual units |
| --- | ---: | ---: | ---: | ---: |
| Body exact type, containsKey, get, combined required-null predicate | 4 | 0 | 0 | 4 |
| Current SHA length/control and 64 character/hex controls | 65 | 0 | 0 | 65 |
| SetupResult.data observation/type guard and data.get(prior) | 2 | 0 | 0 | 2 |
| Prior exact ordered shape, n=10 | 56 | 0 | 0 | 56 |
| Qualified map bounded allocation | 1 | 0 | 0 | 1 |
| Prepaid twelve new-map entries/stores | 0 | 12 | 1 | 12 |
| Ten prior Map.get value observations | 10 | 0 | 0 | 10 |
| Prepaid outer existing-slot copy and put | 1 | 1 | 1 | 2 |
| Component overhead | 139 | 13 | 2 | 152 |

Shape detail: type1 + size1 + size predicate1 + entrySet1 + iterator1 + final
false hasNext1 =6. Each of ten keys has hasNext1 + next1 + getKey1 + String type1
+ ordered equality1 =5. Thus 6+5*10=56. Loop induction belongs to the charged
item operation; switch-key selection and internal method calls do not inspect
input independently. The implementation uses the same census as InputSetup.

The component has 139 executed charge(1) calls and two copy calls on success.
copy(12) precedes map construction/all twelve stores. copy(1) and charge(1)
precede receipt preparation; the final outer put consumes that prepaid work.
No counter is read and no post-ticket source lookup occurs. This matches Root's
frozen 152-unit /13-entry /2-chunk overhead exactly.

The unchanged ReceiptContentBinding.prepare contributes actual units
`116+10P+6B`, entries `2P+8` and chunks3, where P includes the root of the canonical
path chain and B=ceil(expected receipt bytes/4096). Total successful staging is:

- actual units: `268+10P+6B`
- copied entries: `2P+21`
- copy chunks: `5`

Independent arithmetic examples for a future actual-Meter canary:

| P | B | Receipt tuple (units,entries,chunks) | Total tuple (units,entries,chunks) |
| ---: | ---: | --- | --- |
| 2 | 1 | (142,12,3) | (294,25,5) |
| 6 | 1 | (182,20,3) | (334,33,5) |
| 6 | 2 | (188,20,3) | (340,33,5) |
| 10 | 2 | (228,28,3) | (380,41,5) |

These tuples do not promise a fixed group count: actual reserve/copy machinery
retains its group limits and may roll groups. Native verification must use the
real selected wrapper and canonical path/block values, not a substitute ledger.

## Evidence and remaining gates

- PASS (Source-byte check): all fifteen admitted parents match SHA256 and bytes.
- PASS (manual Source review): operation inventory/order, schema literal, borrowed
  nested identities by construction, no input writes, deferred only body write,
  unchanged receipt API, no meter finish or caller integration.
- NOT RUN: compile, actual-Meter canary, positive/negative runtime cases, paired
  codec producer/consumer tests, production build and ordinary player flow.
- NOT APPLICABLE in this source-only worker: staging, commit, push, email and
  process cleanup. No persistent process or runtime resource was launched.

The root-owned canary still needs actual counters/order/identity/open-state
checks, invalid body/current-SHA/prior shapes, null preconditions, receipt
preparation failure with body unchanged, and existing-ticket final lifecycle
coverage. Manual construction review is not runtime proof. No factory, Original,
ArmResult or graph was fabricated and normal main/assembly were not reopened.

All existing 90s/640groups/5s/32768units/copy1MiB/chunk4096 and other Source,
class/process/cleanup caps remain. Full30 is EXCLUDED; HARD/PSYCHOTIC is
unavailable/unrun; old normal20 READ is historical with no reread; protected
unread remains UNCERTIFIED; genuine original,NULL,OBSERVE remains UNSPENT_GATED;
playable100=0 and beta=HELD. No broader host, whole-result, playable or release
qualification follows from this source seam.
