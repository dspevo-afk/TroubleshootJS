# Fresh relay binding probe

This packet contains a private source transform for the finite fresh relay
stage diagnostic. It has no production GO and does not modify the exporter,
host adapter, planner, compactor, generator, observers, or runtime sources in
the repository.

The frozen base is `1327ca3bd359d7ffc3ec4a9969d648ad211812e7`. The selected
request is
`tsj-alpha/6/EASY/RELAY_OUTPUT/-397178238694963708`. The transform consumes
the borrowed private copies under
`docs/task-evidence/PCB/two-pad-span-probe-r1/export-frozen-source/private-inputs/common/`:

- `SparseNormalBoardDiagnostic.java`
- `verify-two-pad-span.ps1`

The audited borrowed-input SHA-256 values are
`135107e4101f15849e5dfc98a9d771dbe1be25a9434f7b5cf595c9ff5c82a707` for the
Java exporter and
`a38dda3888baf3212cdc8e50af5cc4d3b327504871e1d81c5e37df4e40bede11` for the
PowerShell adapter.

`relay_binding_patch.py` is pure in-memory Python. `adapt(exporter, adapter)`
returns changed source strings and an exact `{owner,label,old,new}` ledger.
Each replacement must occur once, and each input preserves its own newline
style. The module has no file I/O, project imports, or import-time side effects.

The exporter receives exactly three replacements inside `parseArguments`:

1. Extend the usage family list with `RELAY_OUTPUT`.
2. Bind the literal `RELAY_OUTPUT` family to `DifficultyProfile.EASY`.
3. Pin that family to signed seed `-397178238694963708L` while retaining both
   existing RC and composed family guards unchanged.

The adapter receives exactly one replacement: replace the composed-only
mandatory `TaskExportFamily` `ValidateSet` with relay-only. Root corrected the
released worker broadening before private source generation; original worker
bytes are retained privately. Its canonical signed-long seed
validation and all other parameters remain unchanged. The exporter’s existing
branches, assertions, export/disposal flow, and both observer classes are
outside the replacement spans and remain byte-for-byte unchanged.

Validation is limited to Python AST parsing, in-memory adaptation, exact
replacement-ledger checks, newline-style checks, and reverse round-trip
equality against the borrowed source strings. Java compilation, native export,
runtime data, browser, and stage observations are intentionally NOT RUN here;
the root task owns applying and freezing the private copies and all diagnostic
acceptance evidence.
