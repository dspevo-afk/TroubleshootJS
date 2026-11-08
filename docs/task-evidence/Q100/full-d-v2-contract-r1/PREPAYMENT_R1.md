# Flat authored work prepayment contract

Source-audited contract; no native execution or claimed runtime counters. R's old26
counts constructor scalar inputs, not all plan fields or numeric replacements.
Independent leaf audit corrected one initial count: capture has11 checked adds,
not10, because callbackUnits+extraCallbackUnits is an additional operation. The first
actual source worksheet also found18 residual subtractions, not17: the nested
originalGroups-originalOwnerGroups subtraction is distinct. Both earlier
miscounts and the failed worksheet are preserved in CONTRACT_CORRECTION_R1.json.

The port retains one flat34-key work map. The plan has44 fields:4 identity refs
(meter/pre/body/work),38 longs and2 lifecycle booleans. Of the longs,26 are
constructor inputs including started;12 are derived fields frozen afterward.
No OriginalArm, ArmResult, graph or callback owner is retained by the plan.

| Phase | Required accounting before the operation |
| --- | --- |
| Original boundary | 9units:5 source inspections and4 checked arithmetic operations (3subtract/1add). Subtract9 from the raw units delta for originalWrapper attribution. |
| Authored source detachment | 40 helper-charged inspections:setup4/original12/NULL12/OBSERVE12. |
| Capture sums | 11 checked additions:original callback1/observer1/cleanup2/freshgroups1/freshcopies3/freshchunks3. |
| Existing body work slot | 2units for containsKey/get; require the pre-existing value null. |
| Flat work construction | Copy35 logical entries (34slots plus existing body.work replacement); map allocation1unit and outer put1unit. |
| Plan | Allocate1unit, copy26 detached constructor longs. The bounded allocation/control operation retains four identity refs; this is not a graph copy or VM allocation census. |
| Snapshot | Existing allocateR5Snapshot1unit and19 closed-native field/list inspections prepaid before metadata close. Preserve exact W checkpoint implementation. |
| Freeze | 26units:18 checked subtractions,4 checked additions,2 Math.max operations and2 bounded binding/bounds controls. Twelve derived primitive fields are outputs of these operations, not source DTO copies. |
| Finalize | Copy34 numeric wrapper replacements. Prepay38units:34 slot writes,1 body.work read,1 bounded identity/binding check,1 method lifecycle guard/retirement operation and1 elapsed checked subtraction. |

Every helper inspection is charged immediately before inspecting its source.
Arithmetic and deferred finalization work is prepaid while metadata is open.
Work topology and body/work identities are fixed before checkpoint capture.
Finalization only replaces34 existing numeric values. It may not allocate maps,
read live OriginalArm/ArmResult state, reopen metadata or charge the live Meter.
Separate frozen/finalized checks constitute one bounded method lifecycle
operation in this explicit API model. Invalid/reused finalization fails closed.

The post-finalize r5ValidateWork has three checked additions plus fixed shape/
identity/limit checks. These remain inside the first bounded export operation
(schema/owned-output finalization), not in capture sums. Count/write/read-compare,
digest and marker finalization retain5C+3 units,2R+32+M copy admissions,2C+2 chunks
and one group. Primitive VM allocations/instructions are unmeasured. Do not mix
this logical API accounting with a claim to count all scalar CPU instructions.

The new qualified-prior result map adds current inputContextSha256 and schema to
ten historical fields:12 copied entries and one bounded map allocation, under
ordinary metadata accounting. No current identity is inserted into canonical
context data. Original copies/chunks are already merged into the Meter; subtract
attribution once when deriving residual metadata, never add them again.

Input compatibility checks, including exact seeds/cohort/owner/source shape,
must finish before captureOriginal. Charge their bounded field/string/list work
before inspection and include their actual copies/units/groups in SetupResult
before computing the original capture allowance. Do not add unrecorded setup
copies after its snapshot. Actual source/template/PAD proof still follows the
real capture; static input validation cannot fabricate that proof.

Source implementation must expose auditable named constants/loops matching this
table and receive a final source review. If source changes the inventory or
operations, revise this design explicitly before any producer invocation; do
not bless old26/19/722 constants or a pre-fix PASS. All global limits unchanged.
