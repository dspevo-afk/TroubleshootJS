# Legacy geometry oracle adaptation

The general layout identity is now version 17. `PcbBoardLayout.geometryFingerprint()`
therefore starts with `layout=17|`, while the saved RB56 geometry SHA-256 values in
`PcbCompactnessContractTest`, `PcbFunctionalGroupingContractTest`, and
`PcbPocketPackingContractTest` were recorded with a `layout=16|` prefix.

`PcbCompactnessContractTest.legacyGeometrySha256(layout)` first requires
`SeededPcbLayoutGenerator.CURRENT_VERSION == 17` and an actual `layout=17|`
fingerprint. It changes only that prefix back to `layout=16|` before hashing;
every remaining fingerprint byte is still covered by the original literal
expected hash. The saved hashes themselves are unchanged, so the checks still
pin the complete geometry body, including component and pad positions, copper,
holes, labels, and routing metadata.

Raw geometry hashes in reports and dynamic before/after immutability checks
continue to use the actual version-17 fingerprint. No saved geometry oracle was
updated or regenerated.

The Quick Play physical matrix now selects from `PlayerFamilyCatalog.families()`
and explicitly checks that the 12-entry registered view still contains held
`RB56_CONTROL`, while the 11-entry active normal-player view excludes it. The
matrix's normal family order and its existing acceptance, replay, diversity,
and root limits are unchanged.
