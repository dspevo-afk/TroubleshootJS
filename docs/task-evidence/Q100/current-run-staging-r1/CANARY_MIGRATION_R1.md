# Current V2 input canary migration R1

## Scope and frozen inputs

This is a fresh edited copy at `CurrentV2InputSetupCanary.java`, based on
`source/CurrentV2InputSetupCanary.java` (SHA-256
`a0b04875aefe8bd133a268689c43f509469ff168953e43482d0626d6b4649c2d`). The frozen
contract is `CONTRACT_R1.md` (SHA-256
`1e03b5b290eaf5acf3f6a628c72b629c5edeeea11446fb127ed554c81d76354c`); the fresh
fixture reference is `FIXTURE_REFERENCE_R1.json` (SHA-256
`9ff0fe9fb4080a42066c9e4feb689428cdf1404d4e47eba3d7c8cee787da0023`). The
identified factory source is `CohortTerminalTelemetryInputV2.java` (SHA-256
`b9b1dc6c6b61f50a5a97259d31783302c4b77ec5a938dd88b70fe7c8225e10e6`).

## Changes

- Both prior and capture trial checks now require `orderChanged=true`, matching
  the fresh RUN profile.
- Requested IDs are checked against the literal index order
  `[43,50,0..42,44..49,51..59]`. The same checker still verifies type, value,
  uniqueness, and cohort inclusion.
- Captured IDs continue to be checked in original `0..59` order. No captured
  list is reordered.
- `TRIAL_JOIN` now mutates capture `orderChanged` to `false`, the rejected
  historical profile.

No canary cases or `need` assertions were added or removed. The maintained
coverage remains the contract's 81 cases and 12,089 assertions by source-diff
review; those runtime totals were not re-executed here.

## Review and validation

- **PASS — input identity review:** the contract, fixture reference, baseline
  canary, and named fresh factory hashes match the assigned identities.
- **PASS — source diff review:** the requested order is pair-first and literal;
  captured order remains canonical; case definitions and assertion sites are
  unchanged aside from the requested expectation/mutation updates.
- **PASS — file format:** edited Java copy is UTF-8 without BOM and uses LF line
  endings.
- **NOT RUN — compilation and canary execution:** explicitly excluded by the
  assignment. Therefore 81/12,089 are preserved by source review, not newly
  observed runtime output.

Source-review note: `TRIAL_JOIN` still rejects the false mutation at the earlier
`checkTrial(... orderChanged=true)` policy assertion, before the later cross-map
join loop. This preserves the negative case's rejection of the false RUN input,
but that case does not independently reach the join comparison. No change was
made to case order or assertion count.

## R2 correction after actual native failure

The first final-source native regression failed with
`NEGATIVE_ACCEPTED_REQUESTED_COHORT_SUBSET`: both requested mutations still used
old index43. Under the pair-first fixture A is now at index0. Both mutations now
replace index0 with NET_143, removing A while preserving uniqueness. All other
indexed mutations were Source-reviewed: capture43/50/10 stay unchanged;
requested duplicate1 still duplicates NET_000 at2; requested size59 still removes
one item. No case/assertion was removed or expected rejection weakened. The failed
Source, compiled classes, process receipts and output remain in the first private
candidate. Fresh final-source gates are required for this corrected candidate.
