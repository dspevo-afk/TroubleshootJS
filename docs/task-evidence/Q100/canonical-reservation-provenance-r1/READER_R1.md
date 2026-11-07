# P9 independent reader R1

Status: FROZEN before producer work. Source, host, native and raw protocols are
bound by CONTRACT_SET_R1.json. No actual P9 observation or contributor set is
asserted here; all runtime gates remain NOT RUN.

## Seven-argument native interface and modes

The native entry receives exactly these seven application arguments, in order:

```text
MODE RUN_UUID P9_INPUT_SHA P8_WIRE_SHA P9_INVENTORY_SHA EMPTY_P8_DATA_DIR EMPTY_P9_DIR
```

Mode is exactly `NULL`, `OBSERVE`, or `TAP_THROW`; UUID and three digests use the authenticated host/source contract. Both output directories are authenticated, distinct, existing, empty directories. P9 writes its sidecar/index only to the P9 sibling directory. Preserve the P8 seven-file subtree and its sole stdout completion marker.

The outer operation retains exact fields from `HOST_R1`: `wrapperMode,p8Mode,runId,disposition,processKey,p8DataDirectory,p9Directory,workNanos,groups,observationUnits,p9Index`. Normal operation order is NULL, OBSERVE, TAP_THROW, followed by the genuine original P8 CALLBACK_THROW and REASON_THROW operations.

| P9 wrapperMode | p8Mode | P9 tap behavior and retained operands |
|---|---|---|
| `NULL` | `NULL` | No P9 registration, getter, DTO, PAD rows, or slot capture. |
| `OBSERVE` | `OBSERVE` | One target tap; retain one full header, 246 Router-order PAD rows, and footer containing the actual nullable slot. |
| `TAP_THROW` | `OBSERVE` | Build the same bounded DTO at the tap; throw `IllegalStateException("FIRST_RESERVATION_OPERANDS_CALLBACK")` once; catch only that exact instance inside the decorator, discard the DTO and continue the original P8 event. Retain only the two-line P9 control sidecar; do not retain PAD rows or slot. P8 must finish as a normal OBSERVE operation. |

TAP_THROW tests decorator containment and DTO disposal, not Router unwinding. It is separate from the original P8 CALLBACK_THROW and REASON_THROW; those calls have no P9 tap/sidecar and retain their genuine P8 contracts. The tap occurs at parent query 11's existing `canTraverse` lookup, after `prepareNet` and the one read of `reserved`, before the unchanged result path. It does not repeat a route predicate or mutate cache state. The P8 compact query ordinal is 10 and its parent ordinal is 11.

## Wire ownership

The exact raw sidecar schema is RAW_OPERANDS_AND_ORACLE_R1. The native index
is NATIVE_WORK_R1; the full selected process/output envelope is HOST_R1.

## Reader integration and P8 semantic kernels

The semantic entry consumes actual, authenticated P9 context:

```text
validate_reservation_suite(p9_run_root,p9_pins_path,p9_pins_sha256,
                           p9_parent_receipt_path,actual_p9_host,
                           actual_p9_process_bundle) -> result
```

`actual_p9_host` and `actual_p9_process_bundle` are parsed from the genuine P9 host result/parent receipt and actual bounded process receipts/streams. Authenticate P9 inputs/source rows before reading output. Build the P8 ancestry using the pinned P8 `load_semantic_parent(p8_pins,p8_pin_rows,p8_source_rows)` over the actual authenticated P8 pins/inventory retained as P9 inputs. Keep its genuine P4/P5/P6 modules, context, and cut. Never call P8 `validate_bundle` on a P9 root, relabel P9 pins as historical P8 pins, fabricate an old P8 host, or rewrite receipts/index entries/native bytes.

Call the pinned P8 `_validate_complete_operation(root,data,host,operation_row,process_bundle,parent_bundle,pins_sha,source_sha)` for each normal P8 result. `data` is a shallow map of the seven actual payload byte objects only: `op-1.jsonl`, `fixed-prefix.jsonl`, `prefix-blockers.json`, `prefix-stages.json`, `canonical-stage-index.json`, `prefix-geometric-reasons.jsonl`, and `canonical-geometric-prefix-index.json`. For TAP, map actual TAP P8 payload bytes under `runs/OBSERVE/data/` keys without copying or altering bytes; use operation projection exactly `{mode:"OBSERVE",runId,processKey:"tapThrow",workNanos:<actual P8 marker work>,groups:<actual raw GROUP count>,observationUnits:<actual P8 footer total>}` and genuine `process_bundle.raw["tapThrow"]`. Pass the actual P9 host and genuine P8 semantic parent. The P8 kernels independently validate P4 capture, P5 attribution/totals/cut, P6 stages, P8 sidecar/index, query identity, and cleanup.

Compare P8 NULL↔OBSERVE and NULL↔TAP via returned P8 structures using pinned P6 `_compare_capture_runs` and P8 `_compare_reason_modes`. The actual parity map has exactly `p4p5p6NullObserve,p4p5p6NullTap,p8NullObserve,p8NullTap`, all true only after the corresponding unchanged inner checks pass. TAP's P8 result must be semantically identical to OBSERVE; its P9 sidecar separately proves exactly one contained sentinel and DTO disposal. Only OBSERVE has a retained reservation slot. Independently derive its contributors from raw operands; count distinct Router pad indices that hit the target by rectangle OR touch (count an index once if both), and distinct contributor nets. Compare only the expected nullable slot with that captured slot. Keep contributor IDs inside private reader state. Public success output reports only `contributorNetCount`, `contributorPadCount`, `contributorSetSha256`, and nullable-state/digest fields; never raw operands or actual net/pad IDs. A mixed cache cell establishes one-cache provenance, not unique ownership or physical feasibility.

P8 prior work must come from the actual validated `P6 stageTotals.totalObservationUnits`; P6 already includes its P5 prior units, so add it once to P8-owned units. Do not reuse stale prose that hard-codes zero: in the pinned callback-throw source path `priorObservationUnits` is zero while `totalObservationUnits` is the genuine nonzero 1646. Bind the marker, stage totals, footer, and operation row by equality; never substitute the prior count for the total.

Reader CLI supports exactly four application args: `pinsPath pinsSha outputRoot parentReceipt`. For CANARY it validates the actual host/source/process/output closure and returns a canary-only result with no authored target, operations, slot, or P8 parity. For SUITE it validates the actual complete P9 suite read-only. Semantic mutation belongs only to the separate test_provenance.py four-argument harness in CONTROLS_R1; never copy or edit positive host artifacts or write mutations to disk. Literal tests use the separate exact invocation `test_provenance.py --literal` (one application argument), authored synthetic fixtures only, with the authenticated selected Python/module from the pre-case CANARY. The controller runs this child outside the host output tree, so it adds no host leaf or process role.

The whole-CLI result schema is `tsj-q100-reservation-provenance-reader-result-v1` with the same exact top-level keys for CANARY and SUITE:

```text
schema,status,layoutQualification,scope,p9InputSha256,p9SourceSha256,p8WireSha256,runs,target,slotComparison,contributorNetCount,contributorPadCount,contributorSetSha256,p8Parity,sourceClosure,processes,outputClosure,limits
```

CANARY has `status="PASS_RESERVATION_HOST_CANARY_ONLY"`, `layoutQualification=false`, `scope="HOST_CANARY_ONLY"`, `runs=[]`, `target=null`, `slotComparison=null`, `contributorNetCount=0`, `contributorPadCount=0`, `contributorSetSha256=null`, and `p8Parity=null`. SUITE has `status="PASS_RESERVATION_PROVENANCE_DIAGNOSTIC_ONLY"`, `layoutQualification=false`, and `scope="ONE_AUTHORED_BASE_PARENT11_SLOT_ONLY"`.

For SUITE, `target` has exactly `queryOrdinal,parentOrdinal,layer,bit,fromGridX,fromGridY,toGridX,toGridY,slotAxis,slotGridX,slotGridY`, with values `10,11,1,8,382,238,381,238,"HORIZONTAL",381,238`. `slotComparison` has exactly `expectedState,capturedState,match,expectedValueSha256,capturedValueSha256`; map a nullable value of `null` to `NO_RESERVATION`, `""` to `MIXED`, and any nonempty net ID to `SINGLE_NET`. Never serialize the actual nullable string. Compute each value digest as `SHA256(ASCII("tsj-p9-nullable-v1") || 0x00 || compact-ASCII-JSON(nullableValue))`. Compute `contributorSetSha256` as `SHA256(ASCII("tsj-p9-contributors-v1") || 0x00 || compact-ASCII-JSON(sortedDistinctNetIds))`; the public result contains only this digest, contributor net count, and distinct hit-pad-index count.

SUITE `runs` has exactly five ordered rows, one each for `NULL,OBSERVE,TAP_THROW,CALLBACK_THROW,REASON_THROW`; each row has exactly `wrapperMode,p8Mode,runId,disposition,sidecar,index`. Sidecar/index are private actual-output refs `{path,bytes,sha256}` for the three P9 wrapper modes and null for both genuine original P8 throws. Bind refs to actual relative output paths and bytes; no copied positive artifacts. `p8Parity` has exactly `p4p5p6NullObserve,p4p5p6NullTap,p8NullObserve,p8NullTap`, all true after the actual authenticated inner-kernel comparisons.

`sourceClosure` has exactly the ten fields `status,pinsSha256,sourceInventorySha256,existingFiles,directFiles,derivedFiles,totalFiles,productionFiles,topFiles,rechecked`; `rechecked=true` follows actual final source re-audit. The current frozen metadata expects status `PASS_Q100_RESERVATION_INPUTS_ONLY` and counts `3437,17,2,3456,850,27` respectively. `processes` has exactly `hostReturned,hostTerminationProven,childProcessCount,allChildrenTerminated,allProcessRootsAbsent,readerExecutableSha256`; child count is 12 for CANARY and 17 for SUITE, and every boolean is true only after actual proofs. `outputClosure` is the actual host's closed relative file/directory refs and hashes, not a reconstruction. `limits` echoes the exact authenticated host limits; it may not be widened by the reader.

The literal-only CLI result is one compact JSON line plus LF, stderr empty, exit 0:

```json
{"schema":"tsj-q100-reservation-literal-result-v1","status":"PASS_LITERAL_ORACLE_ONLY","positiveCount":12,"negativeCount":16,"passedPositiveCount":12,"passedNegativeCount":16}
```

A literal failure exits 2, emits no stdout, and writes one `P9_PROVENANCE_LITERAL_REJECT <CODE>\n` line. The semantic reader success stdout is one compact JSON result line plus LF; all bytes, source rows and result fields obey the authenticated P9 closure limits. Reader failures use exit 2, empty stdout, and one LF line `P9_RESERVATION_READER_REJECT <CODE>`; unexpected exceptions become `UNEXPECTED_FAILURE`.

The twelve literal positives, sixteen semantic negatives and both test CLI
forms/results are defined by CONTROLS_R1. read_provenance.py never runs its own
negative mutations as part of a positive result.
