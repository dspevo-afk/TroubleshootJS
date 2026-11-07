# BASE input inventory contract R1

FROZEN input/source contract before its maker and auditor implementation.
This authorizes only those input producers/consumers; native, host and reason
reader schemas freeze separately before their implementations. No BASE run is
authorized by an input-only PASS. PLAN_R1 and SOURCE_INVENTORY_R1 control scope.

## Exact BASE pins shape

Use schema `tsj-q100-geometric-prefix-study-inputs-v1` with exactly these
top-level keys:

```text
schema, repositoryRoot, parent, canaryPrerequisite, p6Context,
sourceInventory, roles, files, toolPaths
```

Nested keys and value types:

```text
canaryPrerequisite = {
  root: canonical absolute directory path,
  pinsPath: canonical absolute file path,
  pinsSha256: lowercase SHA-256,
  qualificationPath: canonical absolute file path,
  qualificationSha256: lowercase SHA-256
}

p6Context = {
  pinsPath: canonical absolute file path,
  pinsSha256: lowercase SHA-256
}

sourceInventory = {
  path: canonical absolute file path,
  sha256: lowercase SHA-256
}

roles = {
  native: {
    study: canonical absolute file path,
    inspector: canonical absolute file path
  },
  process: {
    helper: canonical absolute source-row path,
    reader: canonical absolute file path,
    host: canonical absolute file path
  },
  input: {
    maker: canonical absolute file path,
    auditor: canonical absolute file path
  }
}

files = [{path, bytes, sha256}, ...]

toolPaths = {
  pythonExe: canonical absolute executable path,
  javaHome: canonical absolute directory path,
  powershellExe: canonical absolute executable path
}
```

All objects have exact key sets. Counts, byte lengths and hashes use strict
types; booleans are not integers. `repositoryRoot`, `canaryPrerequisite.root`
and `toolPaths.javaHome` are canonical no-reparse directories, not `files`
rows. The prerequisite pins and qualification, `sourceInventory`, every role
path except `process.helper`, and executable tool paths resolve to exactly one
`files` row. `javaHome` is bound by the `java.exe` and `javac.exe` rows.
`process.helper` resolves to the inherited source inventory as described
below. Rows are unique and deterministically ordered; no wildcard or directory
row substitutes for a file row.

## Roles and compile boundary

The two native roles are:

| Role | Exact relative path | Responsibility |
|---|---|---|
| `native.study` | `source/CanonicalGeometricPrefixStudy.java` | Main / wrapper; creates the genuine owner context, composes callbacks and runs modes |
| `native.inspector` | `source/CanonicalGeometricReasonInspector.java` | Owns the actual query and fine/coarse observation seam |

Both are new BASE Java files and each has a row in `files`. The host launches
`study` and compiles both. Keep their ownership separate.

The process and input roles are:

| Role | Exact relative path | Responsibility |
|---|---|---|
| `process.helper` | `scripts/VerifierIsolation.psm1` | Existing authenticated E source module for bounded compiler and child-process management |
| `process.reader` | `read_geometric_prefix.py` | Independent strict consumer and semantic projection |
| `process.host` | `run-study.ps1` | Source preflight, compile, native modes and source postflight |
| `input.maker` | `make_study_inputs.py` | Creates the inventory and BASE pins at new explicit paths |
| `input.auditor` | `audit_study_inputs.py` | Independently validates the pins and closure |

`VerifierIsolation.psm1` is already in the authenticated 1084 source rows. It
is not a new Python worker or a BASE-owned extra file. Resolve it by that exact
source row and hash; do not require a duplicate `files` row solely for this
role. The host does not replace the independent reader: it returns
`HOST_COMPLETE_READER_PENDING`, then the reader consumes the actual host
receipt and streams as a separate process.

The compile whitelist is exactly the authenticated CANARY R9 mode/bridge inputs
plus `native.study` and `native.inspector`. Derive inherited CANARY and P6
paths from their genuine manifests. Do not edit the CANARY `modeFiles`, append
BASE roles to that map, compile by directory glob, or treat Python/PowerShell
roles as Java source.

## Immutable parents and source inventory

The CANARY prerequisite is the unchanged
`canonical-geometric-reasons-r1/input-canary-pins-r9.json`, SHA-256
`11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a`, plus
`CANARY_QUALIFICATION_R1.json`, SHA-256
`755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276`.
Validate the qualification with its frozen schema and bind it to that exact
R9 pins digest. Authenticate the R9 inputs with the unchanged P8 auditor; its
maker and auditor rows must remain SHA-256
`af7569a88bbf3933c8dd0ac4b88f33c072c7524a374572b140d43e1f3ec952eb` and
`169344976669b4882674083696c51d7992e7f2c3f69f19573d5788829d7d5874`.
Never regenerate, retarget or rewrite those prerequisite pins, controls,
outputs or qualification.

`p6Context` uses the direct P6 input-pins path and SHA from R9. The frozen
digest is `b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771`;
the original P6 parent is `831d69a41d9504bfb845caef6942e9ef58c9d6d2`.
Validate its P4/P5/P6 roots through that genuine chain. Keep the original P6
`repositoryRoot`, `historicalRoot`, mode-role paths and source baseline
unchanged and separate from BASE `repositoryRoot`. Do not construct P6 pins
with the BASE root or reuse a reader on a relabeled P6 context.

`parent` is the BASE source worktree commit
`0891474cadc4b9e00d7911e81c124721a11efb87`. `sourceInventory` follows the
frozen `SOURCE_INVENTORY_R1.md` contract, SHA-256
`836b0d57086ceea09dc6db2579a970567cdc16c35b678594df88772c9c00ae21`. Its
file is `native-source-inventory.jsonl`, generated before BASE pins. Its 1084
rows exactly match the authenticated CANARY R9 `sourceRows` and current E
files; the qualified classifier yields 850 production files and zero changed
source rows. BASE pins do not duplicate the inherited 1084-row array. The
inventory path appears once in `files`, with its exact bytes and hash, and has
no absolute paths or self-reference. New BASE files are outside those 1084
rows and the 850-file production census.

`sourceInventory.sha256` is passed to native as a separate digest. The study
CLI has exactly six arguments:

```text
MODE RUN_UUID BASE_INPUT_PINS_SHA QUALIFIED_WIRE_SHA SOURCE_INVENTORY_SHA EMPTY_ABSOLUTE_DATA_DIR
```

`SOURCE_INVENTORY_SHA` is token five (zero-based argument index 4) and binds
`sourceSha256` in the native header/trailer and suite index. It is distinct
from the qualified wire digest; never use one in place of the other. The P6
study keeps its original five arguments.

## Exact files and authenticated imports

The BASE `files` census contains exactly the direct BASE inputs: R9 pins and
qualification, the original P6 pins, source inventory, frozen BASE control
documents, both native files, reader, host, maker, auditor, static tests,
`test_read_geometric_prefix.py`, the separate root-owned
`run-study-controls.ps1`, overlay outputs if any, and selected Python, JDK8
`java.exe` / `javac.exe`, and Windows PowerShell executable. The controls
script is an inventory input, not a `run-study.ps1` child dependency. Every
role except `process.helper` is represented. The BASE pins output itself is not
a row in its own `files` list. If an inherited input is already included by
another accepted row, include it once in the exact path set. Reject every
missing, duplicate or unexpected path and verify each row with stable bounded
reads.

R9's original files remain transitively closed by its pinned auditor and
`files` rows; do not copy or shadow them into BASE. Its `read_canary.py` remains
part of the immutable prerequisite but is not a direct BASE import unless a
required pure API is separately identified and authenticated.

The BASE reader directly uses the existing semantic modules:

| Parent | Module |
|---|---|
| P4 | `canonical-prefix-capture-r1/read_capture.py` |
| P5 | `canonical-prefix-blockers-r1/read_blockers.py` |
| P6 | `canonical-prefix-rejection-stages-r1/read_stages.py` |

Resolve each exact path and hash row through the genuine P6/P5/P4 input-pin
chain. Stable-read its bytes, verify them before compilation, then compile and
execute those already-authenticated bytes in memory and cache each module.
Never ordinary-import a sibling module, re-open unauthenticated code, or call a
full P4/P5/P6 reader on a relabeled BASE run. Every additional direct import
needs the same authenticated parent row and byte-cache proof before execution.

Any CANARY run data BASE reads directly must be separately enumerated and
linked to the immutable qualification; an unpinned directory is not data.
Runtime outputs and receipts are not source rows, controls or code roles; their
exact names and fields belong in the separately frozen result contract.

## Maker, auditor and Windows rules

The maker CLI is exactly:

```text
make_study_inputs.py REPO_ROOT CANARY_PINS_PATH CANARY_PINS_SHA CANARY_QUALIFICATION_PATH CANARY_QUALIFICATION_SHA SOURCE_INVENTORY_OUTPUT PINS_OUTPUT
```

It derives its own source root from the script, takes selected tools from the
authenticated CANARY pins, verifies source closure, writes the inventory
first, and then creates the pins. Both outputs use exclusive creation; if pin
creation fails after the inventory is written, preserve that inventory as
failure evidence. It does not overwrite an existing path.

The auditor CLI is exactly:

```text
audit_study_inputs.py PINS_PATH PINS_SHA
```

It emits one bounded canonical JSON PASS marker with status
`PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY`, `pinsSha256`,
`sourceInventorySha256`, `sourceRows: 1084`, `productionFiles: 850`, and
`extraFiles: N`. Exit zero is required. It independently checks exact keys,
parent links, source and file census, pins, reparse boundaries and stability.

Every Python child is the pinned interpreter invoked with `-I -B`. Authenticate
source bytes against a verified row before importing code; do not use
`PYTHONPATH` or ordinary sibling imports. The host uses
`scripts/VerifierIsolation.psm1` for bounded compiler/native children and
preserves actual process image, exit, stream, termination and cleanup proof.

Retain the audited Windows path and metadata rules: inspect raw paths and
reparse ancestors before resolving; when constructing PowerShell from
`SystemRoot`, resolve the actual existing directory spelling before validating
it. Compare `(dev, ino, size, mtime, nlink)` across `stat` and `fstat`; compare
`ctime` only within path-stat samples or within handle-stat samples, never
between them. Accept `Path` objects with `isinstance(value, Path)`. Verify the
actual Windows process image and pinned bytes, not `sys.executable` or a
command-name alias. Runtime pins may contain canonical absolute Windows paths;
public source inventories and summaries use repository-relative POSIX paths
and hashes, never a user's profile, checkout or temp path.

This draft authorizes no BASE run. Preserve the frozen 14-query question,
prior P8/P6 results and budgets in the separate runtime contract. Keep CANARY
evidence distinct from BASE observations; no HARD/PSYCHOTIC access, full30
scope, new seed, production change or player qualification is implied.

## Exact BASE-owned direct file allowlist

The following 17 paths relative to this contract's directory are the complete
BASE-owned direct input set. Historical drafts/private launch/checkpoint files
are not runtime inputs. All final files must exist and be authenticated before
pins generation; unfinished code cannot generate a qualified input manifest.

```text
PLAN_R1.md
SOURCE_INVENTORY_R1.md
INPUTS_R1.md
NATIVE_TRANSPORT_R1.md
HOST_R1.md
READER_R1.md
CONTROL_CASES_R1.md
CONTRACT_SET_R1.json
PREREQUISITE_REUSE_REVIEW_R1.md
source/CanonicalGeometricPrefixStudy.java
source/CanonicalGeometricReasonInspector.java
read_geometric_prefix.py
run-study.ps1
make_study_inputs.py
audit_study_inputs.py
run-study-controls.ps1
test_read_geometric_prefix.py
```

Add exactly the external source inventory, CANARY pins/qualification, genuine
P6 pins and four tool executable rows (Python, PowerShell, java, javac). No new
BASE overlays/builders or extra static suite are consumed: reuse their frozen
CANARY sources through the authenticated parent closure. Every directly loaded
old helper/oracle source is verified from that genuine closure before cached
execution. The four executable paths are file rows; javaHome and prerequisite
root are canonical directories bound to their real child files, never fake
file rows. The base input pins itself has no self row. The expected direct
files count is 25 when all these distinct paths are present.

CONTRACT_SET_R1.json binds the final control documents without its own hash;
all native/host/reader contract acceptance must precede any actual new BASE
run. It does not authorize a partially implemented host to report success.
