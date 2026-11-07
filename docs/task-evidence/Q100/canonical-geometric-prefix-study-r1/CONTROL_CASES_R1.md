# P8 fixed finite controls R1

FROZEN before the controls producer and reader tests. PLAN_R1, INPUTS_R1,
SOURCE_INVENTORY_R1 and the final native/host/reader contracts control scope.
The root owns run-study-controls.ps1; the reader owner owns
test_read_geometric_prefix.py. No control mutates genuine input/output evidence.

The controls CLI has mandatory RepositoryRoot, PythonExe, JavaHome,
InputPinsPath, InputPinsSha256, OutputRoot, and Mode (CANARY or SUITE). Selected
PS5.1 and the unchanged bounded-process module run every host/reader/test child.
Outputs use fresh task-owned OS-temp paths; preserve every returned raw process
receipt and failure. The controller is itself a pinned direct file input.

## Host rejection controls

Five actual selected-host children, before the positive host. Use the same
Mode as the positive. Variant pins are separately labelled synthetic negative
fixtures; their own digest is recomputed so the intended inner check is reached.

| id | sole intentional mutation | required code |
|---|---|---|
| digest | pass 64 zeroes instead of actual pins SHA | INPUT_PINS_HASH |
| self-pin | host files row sha256 becomes 64 zeroes | SELF_PIN |
| extra-pin | PLAN_R1.md files row sha256 becomes 64 zeroes | EXTRA_INPUT_HASH |
| existing-output | target is a separately created empty directory | OUTPUT_EXISTS |
| source-output | fresh nonexistent target inside source repository | EXTERNAL_OUTPUT_REQUIRED |

Require nonzero exit, empty stdout, proven termination and removed process log
root. Stderr is exactly one line `P8_PREFIX_HOST_REJECT <JSON>` (PS CRLF allowed).
JSON exact keys: code,phase,outputCreated,scratchCreated,compilerInvoked,
nativeInvoked,cleanupError. Required code exact; phase nonempty string; all four
actual flags false and cleanupError null. Existing target remains empty; fresh
targets remain absent. No substring/traceback match qualifies.

## New native preflight controls

The positive CANARY/SUITE host compiles once, then runs the original P6 canary,
frozen 31-case P8 literal canary and these four actual new-study children before
BASE. Every other argument is valid, including the actual pinned digests.
Use synthetic UUID 00000000-0000-4000-8000-000000000008 where valid UUID is needed.

| role | mutation | code | argIndex |
|---|---|---|---|
| P8_BAD_MODE | mode NOT_A_MODE | INVALID_MODE | 0 |
| P8_BAD_UUID | UUID NOT_A_UUID | INVALID_RUN_UUID | 1 |
| P8_BAD_INPUT_SHA | input digest not-a-sha | INVALID_INPUT_SHA256 | 2 |
| P8_NONEMPTY_DATA | existing sentinel in data directory | OUTPUT_DIRECTORY_NOT_EMPTY | 5 |

The sentinel is exactly UTF-8 `P8_PREEXISTING_DATA_DO_NOT_CHANGE\n`. Empty data
directories remain empty; the nonempty directory retains exactly that unchanged
file. Native stdout empty, nonzero exit, exact native preflight frame/schema
from NATIVE_TRANSPORT_R1, proven termination, removed stream roots. A syntactically
valid but incorrect 64-hex digest is not a native syntax control: only the host
authenticates the manifest digest. All full preflight child wall time is charged.

## Genuine positive and reader CLI controls

After positive selected host exit0/termination/cleanup, run the independent
reader on its actual bundle and actual parent receipt. CANARY may report only
PASS_Q100_GEOMETRIC_PREFIX_HOST_CANARY_ONLY; SUITE must report
PASS_Q100_GEOMETRIC_PREFIX_OBSERVATIONS_ONLY. Both have layoutQualification=false.
Fingerprint every output path/byte/hash before and after all read-only controls.
Record parent full call and consumer full closure separately.

Four parent-only synthetic negatives retain the genuine output root unchanged:

| id | mutation | required code |
|---|---|---|
| reader-elapsed-type | parent.elapsedMs=true | PARENT_ELAPSED_TYPE_INVALID |
| reader-exit-two | parent.process.ExitCode=2 | PARENT_PROCESS_EXIT_INVALID |
| reader-termination | parent.process.TerminationProven=false | PARENT_PROCESS_TERMINATION_INVALID |
| reader-timing-order | parent.elapsedMs=1.0 | PARENT_TIMING_ORDER_INVALID |

Require genuine cleanupStartOffsetMs>1 before the last fixture. One additional
actual reader CLI child passes 64 zeroes for pins SHA and must reject exactly
INPUT_PINS_HASH. Each rejection is one exact LF stderr line
`P8_PREFIX_READER_REJECT <CODE>\n`, empty stdout, nonzero exit, termination proven
and removed log roots. Failure/import/startup exceptions never qualify as the
expected rejection. Use a 30000ms control limit, within the original 180000ms.

## Source-authenticated semantic controls (SUITE only)

Run selected Python -I -B test_read_geometric_prefix.py with exactly four args:
PINS_PATH PINS_SHA OUTPUT_ROOT PARENT_RECEIPT. The test authenticates its own
file and actual reader bytes before cached execution, validates the genuine
bundle, then calls the actual reason validator on in-memory copies of its
parsed sidecar/context. Raw malformed framing is passed through the actual
reason parser. No fabricated positive host receipt or new Router query is made.
An unchanged genuine sidecar is the positive function control. Mutations below
are independent synthetic semantic fixtures, not additional native evidence.

| id | sole mutation | required code |
|---|---|---|
| header-input | header inputSha256 becomes 64 zeroes | REASON_HEADER_BINDING |
| header-source | header sourceSha256 becomes 64 zeroes | REASON_HEADER_BINDING |
| query-order | swap first two query records | REASON_QUERY_ORDER |
| query-identity | first identity.startPadId becomes UNKNOWN_PAD | REASON_QUERY_IDENTITY |
| event-coordinates | first fine event toGridX increases by1 | REASON_EVENT_CORRELATION |
| owner-type | first fine event ownerIndex=true | REASON_EVENT_FIELD_TYPE |
| returned-null | first OBSERVE allowed becomes null | REASON_QUERY_RESULT |
| missing-footer | remove final footer and its LF | REASON_FRAMING |
| units | footer totalObservationUnits increases by1 | REASON_UNIT_ARITHMETIC |
| event-extra-key | first fine event gets unexpectedField=0 | REASON_EVENT_KEYS |
| missing-fine | first OBSERVE fineEvents becomes empty | REASON_EVENT_COUNT |
| null-fine | first NULL row gets matching OBSERVE fine event | REASON_MODE_EVENTS |

Validate field types and exact keys before semantic correlations so intended
fixtures reach these exact codes. Reader must not remap unexpected exceptions
to expected rejections. Harness success is one bounded JSON line with schema
tsj-q100-geometric-prefix-semantic-controls-v1, status
PASS_SYNTHETIC_REASON_CONTROLS_ONLY, syntheticFixtures=true, passed=12,
positiveControl=true, cases=[{id,expectedCode,actualCode,status}],
genuineOutputsUnchanged=true, layoutQualification=false. Each status is
PASS_EXPECTED_REJECTION. Failure exits nonzero; preserve its actual receipt.

The independent consumer closure remains <=180s through receipt writes and
source/output closure. Semantic harness gets <=180s separately; negative CLI
controls <=30s each; the entire outer controller remains <=1800s after final
write/output. Reserve enough time before starting any child. Preserve original
compile60s/native480s/cleanup120s/work90s/640groups/5s/unit/32768-unit limits.
Controller success requires all applicable controls, selected exits, raw
receipts, cleanup and final wall guard. CANARY never claims BASE or runs the
SUITE-only synthetic reason cases. Any final nonzero exit overrides PASS text.
