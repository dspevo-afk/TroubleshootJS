# Independent export composition oracle R1

## Scope

This is a source-only Python oracle for the pinned synthetic current-export
composition contract. `export_composition_oracle.py` imports only `json`,
`hashlib`, and `re`; it has no filesystem, process, environment, or producer
access. The CLI owns bounded input reading. The test module consumes only the
pre-code synthetic reference vector and its two pinned byte files.

The oracle accepts one `SMOKE` or `SUITE` report case. It checks the top-level
envelope and all 51 flat record keys, exact JSON types, printable ASCII strings,
nonnegative signed Java-long values, cleanup/status flags, measured clocks, and
the independently supplied path-entry count. It rebuilds the result bytes from
the ordered 13-key body and literal Source-derived prior, hashes those bytes,
then rebuilds and compares the ordered 14-key marker for a successful close.
For the expected receipt mutation, it checks the failed close ledger and
requires that no marker or returned metrics exist.

## Independent accounting

With independently supplied `P` equal to the reported contract path-entry count
(the canonical path-entry count plus one under this fixture convention):

| Quantity | Equation |
| --- | --- |
| Pre-export units `U0` | `28712 + 10P + 6` |
| Pre-export copied entries `C0` | `618 + 2P` |
| Pre-export copy chunks `K0` | `23` |
| Result chunks `C` | `ceil(R / 4096)` |
| Successful close units | `5C + 3` |
| Failed close attempt units | `5C + 1` |
| Successful close copied entries | `2R + 32 + M` |
| Failed close copied entries | `2R` |
| Successful close chunks | `2C + 2` |
| Failed close chunks | `2C` |

`R` comes from the independently rebuilt ASCII JSON body. `M` comes from the
independently rebuilt marker, whose digest is over the expected result bytes.
The body work map has all 34 Source-censused keys in literal order. Setup and
metadata nanoseconds, the real export cutoff, and successful closed-prefix
elapsed nanoseconds are the only clock facts accepted from a record; all other
ledger and returned metrics are derived. The active two-group profile also
requires derived metadata units at or below 32768. The cutoff must be positive,
no greater than 90 seconds, and bracketed by the before/after observations.
Individual closed measurements remain capped at five seconds. The negative
record's `afterExportElapsedNanos - exportCutoffNanos <= 5s` is a measured
assertion for that failed-ticket operation, not a source guarantee about every
failure.

The positive marker contains the independently derived result SHA-256, byte
length, elapsed clock fact, close-measure duration, total groups and units, and
derived maxima. Negative ticket failure is checked against the five-second
measured assertion and requires zero export measure, zero returned metrics,
an open/discarded Meter, and an empty marker.

## Source admission and I/O

The CLI and test explicitly load the pure module only after validating
`ROOT_ORACLE_SOURCE_R1.json` with status
`ROOT_ACCEPTED_ORACLE_SOURCE_BEFORE_EXECUTION` and exactly three pinned roles:
the oracle, CLI, and test. Root creates this acceptance file after review; the
candidate code contains no presumed hashes for it.

After all three hashes match, the CLI and test execute the oracle bytes returned
by that stable bounded read. Each sets `__file__`, compiles those exact bytes
with inherited flags disabled and optimization level zero, then executes the
code in a fresh module namespace. Neither uses a source loader or rereads the
module, so an existing bytecode cache cannot replace the authenticated source.

The actual-report reader uses a stable bounded file read with ancestor symlink
and reparse-point checks before and after reading. It compares file identity,
size, modification and change times, and reparse attributes, without comparing
access time. Input is capped at 1 MiB, candidate sources at 4 MiB, and JSON
rejects duplicate keys, floats, and nonstandard constants. Failures use
controlled `ORACLE_*` codes and exit status 2.

## Frozen unit references

The tests pin the pre-code synthetic calibration artifacts:

- `REFERENCE_VECTOR_R1.json`: 18,285 bytes,
  SHA-256 `1e0ea8682a7b7a90e85129475faf8a942eb5d84e587a992a084eb3c6f2afb97b`.
- `REFERENCE_RESULT_R1.bin`: 3,168 bytes,
  SHA-256 `ed6c27a049f278736ec97156bb335dba3563b4e5bdc581bb374c567f4b99eadc`.
- `REFERENCE_MARKER_R1.bin`: 619 bytes,
  SHA-256 `4dde7863dd0b8f58da47315e5dd67dafafa9426bf5aa93cb203a8d867b8cfc80`.

Thirteen pure unit cases cover the frozen positive and negative vectors, result and
marker byte changes, counter and clock changes, schema and path changes,
two-group profile rejection, zero cutoff, and a literal escaping control. They
do not create runtime goldens. This source-only worker authored the test suite
but did not import or execute it. Root owns acceptance, release pins, and the
selected real Python CLI canaries.

## Limits

The reference vector contains synthetic clocks and values only. It proves no
native timing, producer lifecycle, genuine OriginalArm behavior, full-D
semantics, protected-data readiness, or normal-host assembly. Native canaries
must use real clocks and the actual selected input/path. No generation, native
classes, production source, or old DATA were read or run for this oracle task.
