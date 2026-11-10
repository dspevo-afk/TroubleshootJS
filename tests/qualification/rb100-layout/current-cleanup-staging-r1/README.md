# Synthetic pending cleanup and detached R7 handoff

`CurrentAssemblyMetadata.pendingCleanupBody` explicitly stages a body with pending
prior, work and cleanup slots through the unchanged constructor. `installCleanup`
derives its views from that body, projects the existing R7 scalar and prepays its
single replacement. It preserves input maps and the existing cleanup limit.

The Source canary, independent reader and pure fixtures here qualify a synthetic
component sequence. Source pins and actual gate results are recorded in the
[evidence packet](../../../../docs/task-evidence/Q100/current-cleanup-staging-r1/README.md).
Private authenticated native dependency Sources/classes remain required; this
does not promise standalone public native replay or full-D producer acceptance.

Use isolated Python with bytecode disabled (`-I -B`). The reader takes `--case`
(`smoke` or `suite`), `--input-json` and `--reference-root`. The pure tests take
`--reference-root` and unittest options. Normal assembly and the genuine original
plus NULL/OBSERVE envelope remain closed or unspent; playable100 remains zero.
