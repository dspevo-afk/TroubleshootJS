# P9 native envelope and work R1

Status: FROZEN before producer work. Scope is the existing parent11 reservation;
all code, builds, capture and runtime gates remain NOT RUN.

## Entry and preflight

Entry CanonicalReservationProvenanceStudy.main(args), exactly 7: 0 mode NULL|OBSERVE|TAP_THROW; 1 lowercase run UUID; 2 p9PinsSha; 3 authenticated p8WireSha; 4 p9SourceSha; 5 existing empty real P8 data dir; 6 existing empty real P9 sibling dir. Require canonical absolute sibling dirs, no reparse/symlink, empty before producer/write.

Five native negative controls; each exits 1, emits no stdout, creates/changes no files or dirs: P9_BAD_MODE → INVALID_MODE arg 0; P9_BAD_UUID → INVALID_RUN_UUID arg 1; P9_BAD_INPUT_SHA → INVALID_INPUT_SHA256 arg 2; P9_NONEMPTY_DATA → OUTPUT_DIRECTORY_NOT_EMPTY arg 5; P9_NONEMPTY_P9 → same code arg 6. Both nonempty cases preserve ASCII sentinel P9_PREEXISTING_DATA_DO_NOT_CHANGE\n. Each rejection emits one stderr line P9_RESERVATION_PROVENANCE_PREFLIGHT_REJECT {compact JSON}\n; exact JSON keys schema,phase,code,argIndex; schema tsj-q100-reservation-provenance-preflight-v1, phase PREFLIGHT. Other arg-count/SHA/path failures use actual code/index.

NULL delegates P8 NULL without P9 registration/decorator/getter/capture; OBSERVE delegates P8 OBSERVE; TAP_THROW delegates OBSERVE with one P9 decorator sentinel. Existing CALLBACK_THROW/REASON_THROW use original P8 entrypoints without P9 registration/dir; both stop at parent 0, target absent. Failed P8 delegation has no accepted P9 index; host retains P8 System.exit(1) failures.

## Passive seam, identity, lifecycle

Keep P8 Study/Inspector/rules unchanged. Pinned Router overlay has only ATTACH_DECORATOR, DETACH_RELEASE, PASSIVE_SNAPSHOT edits. After existing compared-query role guard, decorate the fine reason observer only while registration is armed. Snapshot existing reservation report/already-read value after canTraverse.prepareNet. Never replay prepareNet/touch/legal/traverse/occupancy predicates or change cache writes; forward each P8 event once. Null registration avoids allocation/getters.

Frozen selected attachment order: [0,1,2,3,4,5,6,7,8,9,11,15,17,20], 14 successful attachments; attachment index 10 maps to parent 11. At target require layer 1, A_COIL_LOW, QA.C → DA.A, edge (382,238)->(381,238), traverse stage, cached-reservation kind, and actual empty-face/adjacent site. P8 reader binds this to its full 22 query/14 selected rows.

Registration retains no Router/Search/layout/query/pad arrays. Decorator holds query/original observer only while attached; P8 detach drops refs before clearing Router field. Require 14 attaches/detaches, zero open decorators/dangling wrapper refs; NULL 0/0. After P8 afterOwnersClosed, clear registration; after sidecar verify, drop DTO rows. P8 release/context/source/observer/ref proofs remain authoritative.

Outlet constant is 6, origin PINNED_FULLER_SOURCE: pinned P8 selection validates FULLER_TWO_LAYER (4 transitions); source Search passes PcbTwoLayerRules.VIA_LAND, defined as 6 by pinned rules. No getter or Router construction/copy change. Each pad has 25 scalar fields: existing 17 identity/role/center/face/attachment/escape/bounds/width values, 4 actual owner-courtyard coordinates, 4 actual prepared escape-corridor coordinates. Consumer derives corridor from raw geometry plus source 6 and compares prepared corridor (comparison evidence only).

## Modes, sidecar, index

P9 writes reservation-operands.jsonl. Header/pad/footer keysets/order match RAW_OPERANDS_AND_ORACLE_R1.md. OBSERVE: header + 246 ordered pads + footer. NULL: header/footer, zero pads. TAP_THROW: header/footer, zero retained pads. TAP builds the bounded DTO, throws/catches only its exact sentinel inside decorator, discards pad DTOs, then forwards P8 event once. Budget/I/O/assertion/source/cleanup errors propagate. This proves decorator containment/DTO release, not Router callback unwinding; TAP credit is zero.

Index exact top keys: schema,wrapperMode,p8Mode,runId,inputSha256,sourceSha256,p8Index,sidecar,capture,work,bytes,cleanup,status,layoutQualification; schema tsj-q100-reservation-provenance-index-v1. P8 index/sidecar refs are path,bytes,sha256 under their named dirs. Status PROVISIONAL_NATIVE_RESULT; layoutQualification=false. Preserve P8 seven files/index and sole CANONICAL_GEOMETRIC_PREFIX_COMPLETE stdout line; P9 adds no stdout.

Capture exact keys: state,registrationArmed,fineAttachments,decoratorsDetached,openDecorators,targetAttachmentOrdinal,targetParentOrdinal,targetEvents,padRowsBuilt,padRowsRetained,sentinelObserved,forwardedFineEvents,targetForwardedEvents. Values in key order:
- NULL: NONE,false,0,0,0,null,null,0,0,0,false,0,0.
- OBSERVE: COMPLETE,true,14,14,0,10,11,1,246,246,false,14,1.
- TAP_THROW: DISCARDED_AFTER_SENTINEL,true,14,14,0,10,11,1,246,0,true,14,1.

Cleanup exact keys: p8AfterOwnersClosed,p8OwnersReleased,p8ContextRestored,p8SourcePreserved,p8ObserversDetached,p8ReferencesDropped,p9RegistrationCleared,p9DecoratorDetachCount,p9OpenDecorators,p9DecoratorReferencesDropped,p9DtoReferencesDropped. Accepted modes require true proof flags, expected attach/detach counts, zero open decorators and dropped refs.

Bytes exact keys: p8DataBytes,p8StdoutBytes,p8StderrBytes,p9SidecarBytes,preIndexAggregateBytes. Last sums prior fields, excluding P9 index. After readback, check complete aggregate including index/streams <=8 MiB; host recounts.

## Work and bounded accounting

Work exact keys: delegateStartOffsetNanos,delegateReturnOffsetNanos,p8ReportedWorkNanos,delegateCleanupNanos,p9DelegateCleanupNanos,outsideWorkSegments,p9CleanupNanos,creditCutoffOffsetNanos,creditedWorkNanos,p8Units,p9CaptureUnits,p9ReleaseUnits,p9MetadataUnits,p9SerializationUnits,p9ByteUnits,p9IndexUnits,p9ExportUnits,p9Units,totalUnits,actualReceiptGroups,nativeWrapperGroups,p9Groups,totalGroups,maximumP9GroupNanosBeforeIndex,measuredWorkAtIndexCutoffNanos,finalGuardFormula. Offsets start at P9 wrapper start. P8 marker workNanos excludes Arm cleanup; delegateCleanupNanos is actual cleanup.elapsedNanos in canonical-stage-index.json, validated by P8 consumer.

Outside segments are ordered/nonoverlapping, each exact keys phase,startOffsetNanos,endOffsetNanos,units,groupCount,maximumGroupNanos,cleanupNanos: P9_PREFLIGHT (wrapper start to delegate start), P9_SIDECAR_EXPORT (delegate return through sidecar CREATE_NEW/readback/hash). p9DelegateCleanupNanos measures P9 wrapper drop inside P8; outside cleanup is separately measured. p9CleanupNanos is their sum. Cutoff follows sidecar verify/ref release, before P9 index build. NULL/OBSERVE creditedWorkNanos = p8ReportedWorkNanos - p9DelegateCleanupNanos + sum(outside segment duration - cleanupNanos). Capture is inside P8 query/ADVANCE; do not add twice. TAP and original throws credit zero. No Arm cleanup, P8 tail, P9 cleanup, index build/write, post-cutoff guard or flush is credited.

A JSON scalar leaf (primitive/enum/string/null) costs one entry; object values recurse, key names cost zero, empty arrays cost zero; rectangle = 4 scalar leaves. Pad = 25 entries. Header/footer scalar-leaf counts: OBSERVE H=47,F=11; NULL H=13,F=7; TAP H=13,F=17. Thus OBSERVE capture and release each 246*25+47+11=6208 entries. OBSERVE serialization is 246*26+47+11=6454 (pad row includes kind); NULL serialization=20; TAP=30. Capture/release/serialization use 4096-entry chunks, with exact short tails. Count only declared bounded-source metadata scalar reads in p9MetadataUnits.

Byte units count each actual 4096-byte pass: P8 parent-index read/hash and P9 sidecar prewrite hash, CREATE_NEW write, readback/hash. For S sidecar bytes each pass costs ceil(S/4096). P9 index <=64 KiB; p9IndexUnits=4 (serialize, expected hash, CREATE_NEW write, readback/hash). p9ExportUnits=p9SerializationUnits+p9ByteUnits+p9IndexUnits; p9Units=p9CaptureUnits+p9ReleaseUnits+p9MetadataUnits+p9ExportUnits. NULL has no capture/release units. Time each unit <=5 s. p8Units=P8 totalObservationUnits; totalUnits=p8Units+p9Units<=32768.

Three new outside complete groups on success: P9_PREFLIGHT, P9_SIDECAR_EXPORT, P9_INDEX_FINALIZE, each <=5 s. Callback/copy units nest within existing P8 selected ADVANCE, not new scheduler groups. totalGroups=actual receipt GROUP count + 3 P8 wrapper groups + p9Groups<=640. Do not count callback events/pad chunks twice; full P8 selected ADVANCE <=5 s. P9 adds no Router clone; existing clone/expansion/4096 chunk/48-hole/four-flip caps remain.

After all P9 writes/readbacks enforce (System.nanoTime()-wrapperStarted)-delegateCleanupNanos <= 90,000,000,000. This includes P8 unreported tail and P9 final I/O/cleanup, subtracting only authenticated Arm cleanup. Record cutoff measurement/formula before index write. Index remains provisional; actual exit 0 plus independent final source/output checks proves post-index guard; index cannot prove its later write/guard.

## Literal adapter canaries

Zero-arg P9 canary runs exactly: NULL_UNARMED, FORWARD_ONCE, TARGET_ONE_SHOT, DETACH_DROP, CONTAIN_SENTINEL, UNEXPECTED_PROPAGATES, UNIT_LIMIT, INCLUSIVE_OUTPUT_LIMIT. Synthetic DTO fixtures only. Success emits P9_RESERVATION_CANARY_COMPLETE JSON (schema tsj-q100-reservation-canary-v1), exact keys schema,status,passed,cases,layoutQualification; status PASS_LITERAL_NATIVE_ADAPTER_ONLY, passed=8, ordered IDs above, layoutQualification=false. Failure is nonzero.

## Preserved limits

Keep 90s work, 640 groups, 5s groups/units, 32,768 units, 1,048,576 clones, 4096 clone chunks, 1,000,000 expansions, 48 holes/four flips, 64 KiB lines/index, 8 MiB native/host, 40 MiB suite; compile/audit/native/reader/outer/cleanup bounds 60/60/480/180/1,800/120s. No cap/oracle changes.

All seven companion contracts are frozen together by CONTRACT_SET_R1.json.
Implementation, compile and runtime gates remain NOT RUN.

Scalar accounting: TAP_THROW constructs and releases the same virtual OBSERVE
DTO (6208 capture and6208 release units) before discarding it; its retained
control sidecar serialization is30 scalar units. NULL capture/release are0.
Read/hash each actual P6 and P8 index exactly once in4096-byte chunks; include
both passes in p9ByteUnits, plus the three declared sidecar byte passes.
The four index units are reserved before index serialization; completion is
certified only by the final post-I/O guards and actual successful process exit.
finalGuardFormula is exactly P9_ELAPSED_MINUS_P6_ARM_CLEANUP;
measuredWorkAtIndexCutoffNanos=creditCutoffOffsetNanos-delegateCleanupNanos.
registrationArmed records whether registration was armed earlier, while cleanup
proves it is now cleared. padRowsRetained counts serialized sidecar rows, not
remaining live DTOs. All DTO references are dropped before index construction.
