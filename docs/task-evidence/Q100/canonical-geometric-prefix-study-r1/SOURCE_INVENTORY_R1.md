# BASE source inventory R1

FROZEN before the source producer. PLAN_R1 controls scope. This contract adds no
source rows or production changes. Inventory is data, never executable code.

BASE input pins have `sourceInventory` with exactly `path` and `sha256`, a
canonical absolute private-runtime path and lowercase SHA-256. The same path
also occurs exactly once in `files` with its exact bytes and hash. Generate the
inventory before the pins; neither file contains its own hash.

The inventory is UTF-8 without BOM, exact LF including final LF, compact JSONL.
For every line use the equivalent of Python `json.dumps(value, sort_keys=True,
separators=(',', ':'), ensure_ascii=True) + '\n'`. No duplicate keys, nonfinite
numbers, extra keys, blank lines, absolute source paths or unchecked rows.

First line has exactly these keys and values:

```json
{"kind":"header","productionCount":850,"rowCount":1084,"schema":"tsj-q100-geometric-prefix-source-v1","sourceParent":"0891474cadc4b9e00d7911e81c124721a11efb87"}
```

Exactly 1084 following rows are ordered by their unique repository-relative
POSIX `path`. Each has exactly `kind`, `path`, `bytes`, `sha256`, `role`.
`kind` is `source`; the other four fields equal the authenticated inherited
sourceRows entry without normalization or reinterpretation. Integers are exact
nonnegative integers, never booleans. Retain the qualified role classifier.

The last line has exactly `kind="footer"` and `rowCount=1084`. The complete
inventory is at most 1048576 bytes and each line at most 65536 bytes including
LF. It contains no runtime observations, result hashes or self-references.

The maker checks current files against the qualified CANARY sourceRows before
writing this new exclusive-create inventory. The independent auditor checks
canonical framing, exact rows/counts/classifier, exact SHA/bytes against pins,
and every current source file using qualified stable reads. Host preflight,
pre-native and post-source closure consume the same immutable file.

New native CLI is exactly six arguments:

```text
MODE RUN_UUID BASE_INPUT_PINS_SHA QUALIFIED_WIRE_SHA SOURCE_INVENTORY_SHA EMPTY_ABSOLUTE_DATA_DIR
```

The source inventory digest is argument index 4 (the fifth token). It is
distinct from the qualified wire digest. Fine-sidecar `inputSha256` binds the
BASE pins digest; `sourceSha256` binds this inventory digest. The P8 index and
native trailer bind both digests explicitly. Native does not re-open the
inventory; the actual host authenticates it and supplies its pinned digest.
Original P6Study keeps its original five arguments and schema.

Public summaries use relative paths and hashes. Private runtime pins may
contain canonical absolute paths; never publish those private paths or logs.
