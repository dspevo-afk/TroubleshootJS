# R7 cleanup credit canary notes

## Status

Source correction complete; **compile and execution NOT RUN**. The preserved N2
native suite failure remains recorded in `PRESERVED_NATIVE_FAILURE_R2.json`.
This N3 update does not claim native acceptance or change the R5 validator.

## Frozen inputs

- `CONTRACT_R1.md` SHA-256: `212ac9daea16bafeae9feb397bfbfaafe9d752260dfe0d4a2346a364f7351443`
- `REFERENCE_R1.json` SHA-256: `d5744cfe7118d1f480e88d3fabec06749c32cba1c84cd0cf5379307d670d549d`
- `WIRE_R1.json` SHA-256: `3d0ba4ed424ae0251bfa7d151cd4da6a043f41b5183858d117906c3584ced375`

## Source correction

`CleanupStagingCanary.java` now covers SMOKE2 and SUITE22 in the frozen reference
order. `write_group_guard` expects 639 completed groups before its open 32702-unit
group; the installer closes group 640 before the native wrapper rejects the next
write charge.

The three successful `r7_handoff_*` fixtures use the real native
`beginOriginalOwner()`, `admitOwnerGroup(0)`, and `adoptOriginalOwner(1)` meter
calls while the meter is closed and before metadata work. They create no owner
object or arm result. The detached plan receives `originalGroups=1` and
`originalOwnerGroups=1`; its frozen work values require two total groups before
export, one wrapper group, one original capture group, and one metadata group.
The expected aggregate remains 18576 units, 132 copies, 19 chunks, and one closed
wrapper group; installer-only row deltas remain 67/11/2.

The added `r7_zero_credit_reject` case uses the same pending body, prior slot, and
installer sequence without owner credit. The unchanged detached R7 path must
close its group and raise `IOException:R5_METADATA_SNAPSHOT`. The canary then
calls the native meter check without suppressing its errors and records the
closed 18576/132/19 totals, priorGroups 0, changed body, installed cleanup,
unchanged inputs, empty payload, and no extra finish after the failure.

## Source review and limits

- Frozen input hashes: **PASS**.
- Static review of admitted `CURRENT_STUDY.java` R5Snapshot/R7 plan code and
  existing native meter-credit call shape: **PASS**, read-only.
- Canary Java compile: **NOT RUN**.
- Native SMOKE/SUITE: **NOT RUN**.
- Independent JSON reader/runtime comparison: **NOT RUN**.
- No Source, validator, oracle, native output, or preserved N2 failure file was
  modified. No OS resources were created.
- Genuine owner/arm, graph, Session, routing, protected data, and historical
  full-D profile remain outside this fixture; the genuine envelope remains
  `UNSPENT_GATED`.

Root owns native compilation/execution, independent reader acceptance, and any
further corrections.
