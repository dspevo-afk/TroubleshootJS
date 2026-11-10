# Independent cleanup-staging reader and oracle

## Frozen inputs

The reader authenticates the frozen contract, reference, and wire bytes before
decoding their JSON or reading the canary report:

| File | SHA-256 | Bytes |
|---|---|---:|
| `CONTRACT_R1.md` | `212ac9daea16bafeae9feb397bfbfaafe9d752260dfe0d4a2346a364f7351443` | 7674 |
| `REFERENCE_R1.json` | `d5744cfe7118d1f480e88d3fabec06749c32cba1c84cd0cf5379307d670d549d` | 5782 |
| `WIRE_R1.json` | `3d0ba4ed424ae0251bfa7d151cd4da6a043f41b5183858d117906c3584ced375` | 875 |

The root supplies `ROOT_ORACLE_SOURCE_R1.json` after the oracle source is frozen.
Its exact ordered JSON shape is `{"sha256":"<lowercase sha256>","bytes":N}`;
`bytes` counts the raw `cleanup_oracle.py` bytes. The reader rejects any other
shape, duplicate field, float, non-finite number, or source-byte mismatch. It
compiles and executes only those authenticated raw bytes in memory with
`optimize=0`; it does not import the oracle by module name or use a source-file
fallback or source pycache.

## Protocol and limits

Run the reader as:

```text
python -I -B verify_cleanup_staging_r1.py --case <smoke-or-suite> --input-json <report> --reference-root <N>
```

The command uses isolated mode, disables bytecode writes, and keeps the default
optimization level at zero. The CLI accepts either order of the three unique
options. A valid report prints one small JSON summary with status
`PASS_INDEPENDENT_CLEANUP_STAGING_READER`, the selected case count,
`normalHostAcceptance:false`, and `genuineEnvelope:"UNSPENT_GATED"`. Any
rejected or unavailable input exits 2 with a short error code and no path or OS
exception text.

Reads require regular files and compare descriptor and path identity/size/time
before and after the read. Reparse points are rejected while walking each
ancestor from root to leaf, including when a later target component is absent.
The Windows reader keeps within-API ctime checks and cross-API handle/path
identity checks; it does not equate the two APIs' ctime values.
Canary reports are capped at 1 MiB and oracle source at 4 MiB. JSON parsing
rejects duplicate object fields, floats, non-finite values, invalid UTF-8, and
nesting deeper than 64. Counter checks require exact integers; booleans do not
count as integers.

The accepted report has exact ordered top-level and case keys from `WIRE_R1`,
exact `SMOKE`/`SUITE` names and order from `REFERENCE_R1`, `PASS` status,
`parentConstructions` equal to the selected case count (SMOKE 2, SUITE 22), and
an empty top-level error. The report contains 24 fixed cases. Case phases must
be one of the five bounded operational phase names:
`pending_constructor`, `cleanup_install`, `guard`, `pending_header`, or
`r7_handoff`.

## Independent expectations

The oracle carries the frozen 14/13/1 constructor, 67/11/2 installer, and
18576/132/19 R7 handoff counter vectors as literals. It also binds the 640-group
and 32768-unit guard fixtures, each rejection's exact class/message, the 27/4/6
nested-read counts, unchanged-input and failure-discard flags, and all staged
cleanup payloads. The R7 work map uses the reference's 34-key order and literal
zero, limit, and aggregate values. Only clock-bound fields are taken from the
candidate row after checking the frozen 5-second/90-second limits; the R7
cutoff must be positive and no greater than that row's total elapsed time.

Valid R7 cases carry one real synthetic native owner credit: row
`priorGroups=1`; work has `groupsBeforeExport=2`, `wrapperGroupsBeforeExport=1`,
`originalCaptureGroups=1`, and `metadataGroups=1`. A separate
`r7_zero_credit_reject` preserves zero credits and the exact downstream
`IOException:R5_METADATA_SNAPSHOT` after the one closed R7 group. Its 18576/132/19
totals and installer-only 67/11/2 helper delta remain required; the body has
already changed and cleanup is installed, but payload and canonical bytes stay
empty/zero because export is not attempted. It is a downstream rejection,
separate from installer atomicity.

Constructor and install payloads are independently constructed from the frozen
synthetic maps, including the null/observe alias and geometry controls. R7
payloads are checked against the independent canonical body map, including the
qualified-prior scope fixture, the complete work map, input arms, and projected
cleanup. `r7_handoff_alias_false` requires both the synthetic null-arm input
flag and projected null-arm result to be false. Constructor/install
`canonicalBytes` stays zero; R7 bytes equal the compact body UTF-8 length plus
its final newline.

The 16-method pure test file builds fresh SMOKE and SUITE reports from these
frozen fixtures with fixed clock values. It covers accepted cases, bad payloads and
counters, boolean counters, duplicate fields, case order, clocks and elapsed
cutoff binding, Parent counts, R7 map values and key order, arm mapping, the
below-duration-sum scalar, rejection slot/finalizer flags, and rejection of the
existing `Documents and Settings` Windows junction before a missing child. It
does not create a reparse point or skip that check. Run it with an explicit
fixture root using:

```text
python -I -B test_cleanup_oracle.py --reference-root <N>
```

It never reads native canary outputs or calls Java/R7 helpers.

## Evidence and limits

This assignment was source-only. The reader, oracle, and pure tests were not
executed. The real native canary and final root source pin were not available
when this source was drafted. Therefore this is not reader PASS evidence, host
acceptance, Java/GWT validation, full-D semantic acceptance, or genuine-envelope
acceptance. `UNSPENT_GATED` remains unchanged.
