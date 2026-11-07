# P9 finite control catalog

Status: FROZEN before producer work. Every control and runtime result is NOT
RUN. No accepted-case output is read before selected-host CANARY qualification.

## Controller and selected implementation

run-controls.ps1 takes mandatory RepositoryRoot,PythonExe,JavaHome,InputPinsPath,
InputPinsSha256,OutputRoot,Mode(CANARY|SUITE), using the selected actual PS5.1 and
unchanged pinned bounded-process module. Authenticate self, tools, inputs and
every executed child. Preserve actual returned raw receipts/decoded UTF8 streams
before checking results. An invocation that never returns a receipt stays a
failed call with attempted argv/limit/wall/exception/Exception.Data, process=null
and terminationProven=false. No synthetic receipt or historical successful audit.

Before positive CANARY/SUITE: run twelve source controls, five early host controls,
and the selected Python literal child. Then run the actual positive host, consume
its complete actual parent/output bundle independently, and run five actual reader
CLI negatives. SUITE additionally runs the actual source-authenticated semantic
test child against the genuine accepted output. Positive CANARY must qualify
before starting the authored SUITE; CANARY itself contains no authored operation.
The controller's children and receipts live outside the host's54/106-leaf tree.

Use fresh unique owned external OS-temp roots for synthetic metadata negatives.
Never edit genuine input/source/output bytes or old manifests. Source negatives
may re-sign their explicitly synthetic new metadata to reach the intended inner
boundary; original ancestor manifests and real code bytes remain unchanged.
Fingerprint genuine paths/bytes/hashes before and after controls. Require actual
exit, termination proof, absent owned process/log roots and unchanged targets.
No traceback, substring, timeout, startup/import failure or unexpected code passes.

## Twelve source controls

The exact fixtures and actual auditor/maker boundary are SOURCE_INPUTS_R1.
Each control changes only its named boundary and must reject with its exact code
before any compiler/native invocation. The controller retains its real selected
Python receipt, attempted command and expected/actual code. Fixture metadata is
clearly synthetic; no negative may fabricate a positive whole-host result.

| ID | Mutation | First code |
|---|---|---|
| SRC01_PINS_DIGEST | pins digest argument becomes64 zeroes | P9_INPUT_PINS_HASH |
| SRC02_SELF | auditor self row hash becomes64 zeroes | P9_INPUT_SELF_PIN |
| SRC03_LEAF_HASH | named direct leaf hash becomes64 zeroes | P9_INPUT_FILE_HASH |
| SRC04_TOOL | selected executable identity differs | P9_INPUT_TOOL_IDENTITY |
| SRC05_ROOT_ALIAS | declared root binding is aliased | P9_INPUT_ROOT_BINDING |
| SRC06_EXTRA_TOP | add one undeclared top-level file row | P9_INPUT_TOP_CENSUS |
| SRC07_EXTRA_DERIVED | add a second derived output | P9_INPUT_DERIVED_CENSUS |
| SRC08_EDIT_SET | change one of the three allowed edit IDs | P9_OVERLAY_EDIT_SET |
| SRC09_PRESERVED | corrupt a preserved complement hash | P9_OVERLAY_PRESERVATION |
| SRC10_COMPILE_SET | duplicate/unlisted source or FQN | P9_INPUT_COMPILE_SET |
| SRC11_TRANSITIVE | omit one selected ancestor dependency | P9_INPUT_DEPENDENCY_CLOSURE |
| SRC12_TOTALS | corrupt one inventory sum | P9_INPUT_INVENTORY_ARITHMETIC |

Exact source reject framing/status/exit is SOURCE_INPUTS_R1. These are actual
auditor CLI controls. When a boundary needs a separately cloned synthetic P9 root,
the controller first verifies that the invoked auditor script is byte-identical
to the genuine pinned auditor. Keep the exact17/2 new-file namespace, canonical
root basename and frozen documents; only the named negative changes. SRC10 may
introduce a duplicate native Java FQN in that explicitly synthetic clone and
re-sign its new metadata so the compile-set audit is reached. No old ancestor
root, manifest or production file is copied, retargeted or modified. Negative
copies can never be accepted as the positive source root. Retain each real CLI
receipt; function-only tests are supplementary and cannot replace these twelve.

## Five early selected-host negatives

The host validates these before creating its success output/compiler scratch or
starting compiler/native work. Recomputed negative pins digests target the intended
inner self/leaf check, never bypass it. The target for existing-output is a separate
empty owned directory. The source-output target is a fresh nonexistent child of E.

| ID | Sole change | Exact code |
|---|---|---|
| HOST01_DIGEST | wrong supplied pins SHA | P9_INPUT_PINS_HASH |
| HOST02_SELF | host self-pin hash wrong | P9_INPUT_SELF_PIN |
| HOST03_EXTRA_PIN | PLAN_R1.md direct file hash wrong | P9_INPUT_FILE_HASH |
| HOST04_EXISTING_OUTPUT | existing separate empty output root | OUTPUT_EXISTS |
| HOST05_SOURCE_OUTPUT | nonexistent output inside E | EXTERNAL_OUTPUT_REQUIRED |

Require native/compile flags false, outputCreated=false, scratchCreated=false,
cleanupError=null, nonempty phase, exact code, empty stdout and actual exit1.
Stderr is exactly one HOST_R1 P9_RESERVATION_HOST_REJECT JSON frame (LF or PS CRLF).
Existing target stays empty; nonexistent targets stay absent. Preserve failed
process evidence according to HOST_R1 without mutating those target paths.

## Native canaries and five argument negatives

The positive host compiles exactly1085 final Java sources. It runs genuine P6 and
P8 canaries, the exact eight-case P9 native adapter canary, then all five native
preflight negatives from NATIVE_WORK_R1. Unsupported mode/UUID/input-SHA and both
nonempty output directories each preserve every other valid argument. Native
preflight failures exit1, stdout empty and exact one-line JSON rejection; validate
schema,phase,code,argIndex, actual selected executable/argv and cleanup. The sentinel
is file content, never a substitute for a native stderr frame. Both nonempty
directory sentinels and every other empty target directory must remain unchanged.

The original P6 CALLBACK_THROW and P8 REASON_THROW are SUITE operations, not these
argument controls. Preserve their original exception and partial-prefix oracles.
TAP_THROW is a normal-exit contained decorator/DTO control, never Router unwind.

## Five independent reader CLI negatives

First run read_provenance.py with the actual pins SHA, host root and real parent
receipt; require exit0 and the mode-specific status from READER_R1. Preserve a
parent receipt for that actual call. Then run these selected Python -I -B children
with the same genuine host root. Only synthetic parent metadata is copied; the
positive output stays byte-identical. Each negative has a30000ms limit within the
180000ms reader cap, empty stdout, actual exit2, proven termination and exactly
one LF stderr line P9_RESERVATION_READER_REJECT plus space and its exact code.

| ID | Sole change | Exact code |
|---|---|---|
| READER01_DIGEST | supplied pins SHA becomes64 zeroes | P9_INPUT_PINS_HASH |
| READER02_ELAPSED_TYPE | parent.elapsedMs=true | PARENT_ELAPSED_TYPE_INVALID |
| READER03_EXIT_TWO | parent.process.ExitCode=2 | PARENT_PROCESS_EXIT_INVALID |
| READER04_TERMINATION | parent.process.TerminationProven=false | PARENT_PROCESS_TERMINATION_INVALID |
| READER05_TIMING_ORDER | parent.elapsedMs=1.0 | PARENT_TIMING_ORDER_INVALID |

Require actual cleanupStartOffsetMs>1 before the final fixture. No altered process
object is ever accepted as positive evidence. Negative exit2 is a rejection control
result only; exit2 in any positive gate is FAIL.

## Literal and genuine semantic controls

READER_R1 defines the twelve independent literal positive fixtures and sixteen
exact semantic negative IDs/codes. test_provenance.py --literal runs those synthetic
fixtures before authored capture. Expected geometry and nullable aggregation come
from explicit literal values, never Java results or an actual contributor golden.
Selected Python and actual test/reader source bytes are authenticated by the
controller's pins before execution. Literal-only status certifies no authored case.

For SUITE only, test_provenance.py accepts exactly four arguments
PINS_PATH PINS_SHA OUTPUT_ROOT PARENT_RECEIPT. It authenticates its own bytes,
actual reader and full genuine bundle, then invokes the same actual strict operand,
index and semantic functions on private in-memory copies of the cached OBSERVE
bytes/context. Each of the sixteen negative IDs must produce its exact code; keep
an unchanged genuine component call as the positive control. No filesystem mutation
or re-serialized replacement positive host/receipt/native artifact is permitted.
The result is one compact sorted-key ASCII JSON line plus LF, <=65536 bytes,
schema=tsj-q100-reservation-semantic-controls-v1; exact keys schema,status,
syntheticFixtures,passed,positiveControl,cases,genuineOutputsUnchanged,
layoutQualification. status=PASS_SYNTHETIC_RESERVATION_CONTROLS_ONLY;
syntheticFixtures=true,passed=16,positiveControl=true,genuineOutputsUnchanged=true,
layoutQualification=false. Cases are exact id,expectedCode,actualCode,status rows
in catalog order; status=PASS_EXPECTED_REJECTION. Actual exit0 and source/output
closure through return are mandatory. On any failed fixture exit2, stdout empty,
one P9_PROVENANCE_SEMANTIC_REJECT <CODE> LF stderr line; retain the actual receipt.

## Aggregate limits, authority and reporting

Every selected source-auditor/compiler/native/reader call preserves full actual
wall separately from operation work and cleanup. Reader and semantic full closure
each<=180s; reader negatives<=30s; compile60/audit60/native480/outer1800/cleanup120s.
Reserve enough remaining outer time before starting a child and enforce the final
outer guard after controller output/closure. No pass marker overrides final exit.
The host's shared40MiB cap binds all five operation partitions plus host evidence,
each individually<=8MiB. Retained controller success evidence, including its nested
host and synthetic metadata, must also stay within40MiB with each physical leaf
counted once; this grants no additional native/host/suite bytes. Failed evidence
retention is separate and remains FAIL under the existing HOST_R1 failure-only caps.

Controller summary keys: schema,status,mode,inputSha256,sourceSha256,sourceControls,
hostControls,literal,host,consumer,readerControls,semantic,inputUnchanged,
outputUnchanged,allReturnedProcessesTerminated,limits,layoutQualification.
schema=tsj-q100-reservation-controls-v1; status PASS_HOST_CANARY_CONTROLS_ONLY or
PASS_RESERVATION_SUITE_CONTROLS_ONLY. Each retained child result has exactly
name,expectedExit,actualExit,expectedCode,actualCode,process,wallMs,terminationProven;
process is an exact path/bytes/sha256 ref to the retained unchanged returned raw
object, never a reconstruction. In CANARY semantic
is null. Genuine positives have expectedCode/actualCode=null. Inputs/output flags
and allReturnedProcessesTerminated must be true, layoutQualification=false.
The summary and its one-line P9_RESERVATION_CONTROLS_COMPLETE stdout marker remain
provisional until actual controller exit0 and parent final wall/source/output checks.
Large actual raw receipts/streams are retained as files, not embedded in a65536-byte
summary; summary process refs bind those unchanged files.
No returned raw object is modified to make it fit.

Record PASS/FAIL/BLOCKED/NOT RUN/NOT APPLICABLE truthfully and preserve every failed
attempt. No production build or visible Player gate is renewed by this native-only
diagnostic. No actual contributor identity/raw operand enters public summaries.
Full30 is owner-excluded; HARD/PSYCHOTIC unavailable/unrun, protected bodies unread
here and historical unread UNCERTIFIED. Playable100=0 and betaHELD.

## Twelve independent literal positives

Run the 12 fixed synthetic fixtures in `test_provenance.py --literal` before any accepted-case capture. They test the offline oracle only and do not contain actual board operands or expected contributor sets.

| ID | Literal fixture | Expected |
|---|---|---|
| `P9L01_EMPTY` | no rectangles or touch edges | nullable slot `null` |
| `P9L02_SINGLE` | one intersecting `N1` reservation | `N1` internally |
| `P9L03_SAME_NET` | two `N1` reservations | `N1` internally |
| `P9L04_MIXED` | one `N1` plus one `N2` | empty-string mixed sentinel |
| `P9L05_STRICT_EDGE` | rect `(0,0,10,1)` vs `(10,0,10,1)` | strict intersection false |
| `P9L06_CLOSED_TOUCH` | stroke `(0,0,19,9)`, land `(19,2,4,4)` | closed-edge touch true |
| `P9L07_ROLE_PROFILE` | five BoardNet role names | widths `13/11/11/9/9` |
| `P9L08_PTH_FACE` | PTH on either face; surface pad only TOP | copper on both PTH faces; surface only TOP |
| `P9L09_OPPOSITE_FACE` | center `(20,30)`, east escape 20, TOP mount/BOTTOM active, widths 9/11 | point corridor inflates 15 to `(5,15,30,30)`; edge `(20,30,10,1)` intersects |
| `P9L10_NEGATIVE_DIVISION` | origin 0, size 3, physical delta -1 | start index 0; end index -1 |
| `P9L11_OUTLET_ROUNDING` | courtyard `(0,0,30,40)`, pad `(20,20)`, east escape length 10, outlet 6, same face | distance 10, outlet 17, length 20, corridor `(20,20,20,0)` |
| `P9L12_TOUCH_GATE` | active width 9; land `(0,0,300,10)`, center `(300,5)`, escape 0; edge `(0,5,10,1)` | point-corridor margin misses; touch path reserves only when flag true |

## Sixteen literal semantic negatives

Each fixture mutates only synthetic in-memory input and asserts the exact first code. These are not source-manifest or host controls.

- `P9N01_CRLF_OR_MISSING_FINAL_LF` → `P9_OPERAND_FRAMING`.
- `P9N02_DUPLICATE_JSON_KEY` → `P9_OPERAND_JSON`.
- `P9N03_EXTRA_OR_MISSING_HEADER_KEY` → `P9_HEADER_FIELDS`.
- `P9N04_PAD_REORDERED_OR_DUPLICATED` → `P9_PAD_ORDER`.
- `P9N05_PAD_MISSING_OR_EXTRA` → `P9_PAD_COUNT`.
- `P9N06_BOOL_OR_FLOAT_AS_INTEGER` → `P9_INTEGER_TYPE`.
- `P9N07_UNKNOWN_ROLE_OR_ENUM` → `P9_PAD_ROLE`.
- `P9N08_ROLE_WIDTH_DISAGREEMENT` → `P9_PAD_WIDTH`.
- `P9N09_BAD_GRID_DERIVATION` → `P9_GRID_DERIVATION`.
- `P9N10_WRONG_PINNED_OUTLET_ORIGIN_OR_VALUE` → `P9_OUTLET_SOURCE_BINDING`.
- `P9N11_INCONSISTENT_COURTYARD_FOR_COMPONENT` → `P9_OWNER_COURTYARD` (mutate the second pad of one component; P4 supplies no courtyard truth).
- `P9N12_PREPARED_CORRIDOR_DIFFERS_FROM_DERIVATION` → `P9_CORRIDOR_PARITY`.
- `P9N13_WRONG_TARGET_OR_QUERY_IDENTITY` → `P9_QUERY_IDENTITY`.
- `P9N14_CAPTURED_SLOT_DIFFERS_FROM_ORACLE` → `P9_SLOT_MISMATCH`.
- `P9N15_TAP_SENTINEL_NOT_CAUGHT_ONCE_OR_DTO_NOT_DISCARDED` → `P9_TAP_THROW_PROOF`.
- `P9N16_UNEXPECTED_SIDECAR_FILE_OR_INDEX_REFERENCE` → `P9_OUTPUT_CENSUS`.

The two host-owned no-write controls are `P9_NONEMPTY_DATA` (P8 data argument index 5) and `P9_NONEMPTY_P9` (P9 sibling argument index 6). Their pre-existing sentinel file contents are exactly `P9_PREEXISTING_DATA_DO_NOT_CHANGE\n` and must remain unchanged. The native process returns the exact rejection JSON frame specified by NATIVE_WORK; sentinel bytes are file contents, not stderr. Do not duplicate that native frame schema in this reader contract.

The literal CLI result is one compact JSON line plus LF, stderr empty, exit 0:

```json
{"schema":"tsj-q100-reservation-literal-result-v1","status":"PASS_LITERAL_ORACLE_ONLY","positiveCount":12,"negativeCount":16,"passedPositiveCount":12,"passedNegativeCount":16}
```

A literal failure exits 2, emits no stdout, and writes one `P9_PROVENANCE_LITERAL_REJECT <CODE>\n` line. The semantic reader success stdout is one compact JSON result line plus LF; all bytes, source rows and result fields obey the authenticated P9 closure limits.
