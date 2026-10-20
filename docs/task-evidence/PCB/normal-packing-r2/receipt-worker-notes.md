# Normal packing r2 probe receipt reader

This bounded leaf adds `PcbPackingProbeReceipt`, an immutable package-local
receipt for the single post-selection normal packing probe. Attempts are
strictly zero or one; zero-work receipts require zero orderings, expansions, and
milliseconds and use `NO_CHANGE` or `NO_HEADROOM`, and
an attempted receipt uses `KEPT`, `SELECTED`, or `ROUTE_REJECTED`. Route policy
is restricted to the existing P05/P07 identities, geometry values are exact
lowercase SHA-256 strings, and only `SELECTED` may differ from its baseline.
Per-request work remains bounded to 800 orderings and 1,000,000 expansions;
elapsed time is nonnegative. `requireGeometry` independently hashes the actual
layout fingerprint before accepting the selected geometry.

`MediumBoardPhysicalPolicy.Statistics` keeps every old constructor and primary
candidate counter unchanged, defaults the optional `packingProbe` to null, and
adds the receipt only through the trailing full-constructor overload. Its
canonical form has no r2 prefix for legacy/null receipts and appends the probe
canonical only when present. `MediumBoardNormalAdmission` requires packing 2
when a probe is present, binds the receipt to the accepted layout, requires
P07, and keeps probe work within the aggregate route totals. Existing six
placement/three-per-strategy, 800-order, expansion, route, and physical bounds
remain unchanged.

`PcbPackingProbeReceiptContractTest` covers zero/one-attempt positives, both
route identities, all dispositions, exact work/time boundaries, invalid field
and disposition combinations, lowercase hash validation, selected/non-selected
geometry rules, stale/null layout rejection, current layout fingerprint
binding, and legacy/new statistics canonical behavior. The empty current-layout
fixture uses layout algorithm version 17 and no production/native outcome.
The current terminal contract is 31 assertions across 5 fixture groups.

Static validation completed for this handoff:

- Scoped source checks, EOF/trailing-whitespace checks, and `git diff --check`
  passed for the owned tracked files; untracked packet files were checked by
  the same byte-level validator.
- No native run, Java/GWT build, browser run, staging, commit, push, or email
  was performed by this leaf.
- The r1 packet and all unrelated worktree edits remain preserved.

Working-tree SHA-256 pins:

```text
PcbPackingProbeReceipt.java f1454914f6edbad5a846e02218c8f50f27176d613bdfb32e74b633fd468d98f6
MediumBoardPhysicalPolicy.java 3e2b7ec248933446ed0c057bdddcd0dca7f5a831479df86dbc366dfbd4702803
MediumBoardNormalAdmission.java f1051e4ae0cb2bd7bcb7517fcd0be4c8ea7136f47a6ac4d0e180cc69a7b309bc
PcbPackingProbeReceiptContractTest.java fa6d0b46a48f256ab205503d3832f2b146eb026e1c00390d97b91f0bfd8cd6b2
```

Root integration after handoff: the zero-attempt positive fixture was corrected
to zero elapsed time; Long.MAX_VALUE time remains an attempted-receipt boundary.
The statistics fixture now supplies its route-outcome string vector. Both earlier
source/failed native attempts remain frozen. Current final unit PASS31 assertions
and five groups. The pins above are the historical leaf handoff; authoritative
current source pins are in candidate-inputs.json and its exact candidate-source.
