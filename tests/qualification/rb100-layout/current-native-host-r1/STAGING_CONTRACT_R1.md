# Opaque producer Source staging

`current_native_staging.stage_producer_source` composes the existing authenticated
reader and current caller's root/exclusive-write/readback owners. Its inputs are
already authenticated module objects, two independently bound endpoint configs,
and the verified upstream Source's exact byte count, SHA and read token. Hash
syntax and module-function binding do not confer upstream authority. This adapter
does not ingest a prior-input status record or treat pending evidence as success.

Only `current-input.java` is read from the sealed upstream directory. Identical
bytes are written exclusively as `CohortTerminalTelemetryInputV2.java` in a
separate scratch-child directory, at most1MiB. The adapter never decodes Source,
changes Java content, extends upstream output closure, launches a process, or
creates an admission/manifest ledger. Reference roots must agree across endpoints;
the inspected output roots must not overlap each other or either reference root.
Existing path/handle/reparse checks remain authoritative.
Overlap comparisons fail closed across drives; this finite adapter requires all
compared output/reference directories on the same volume.

The existing `_write_exclusive` now optionally returns its original readback
token, already compared with the written handle. Its default byte/SHA return is
unchanged. The stager requests that token and compares later target readbacks
against it. A fresh read after the writer returns cannot recover the original
identity after a same-content replacement. Source bytes/token and endpoint roots
are rechecked before returning facts. Errors retain any task-owned partial output;
there is no overwrite, retry, refund or cleanup of unrelated files.

These are bounded pathname/handle snapshots, not an atomic transaction against
arbitrary hostile namespace mutation. The caller still owns exclusive endpoint
lifetime and authenticated upstream handoff. Returned tokens/facts are ephemeral
staging facts, not normal-input or native-host acceptance. Full source/class/tool/
launch and result/receipt/lifecycle closure remain separate.

Finite actual Windows gates use only authored CODE and the explicit NO_DATA ABI
stub. Independent literal byte/identity checks cover exact rename/copy,1MiB,
wrong expectations before write, O_EXCL sentinels, containment, alias/overlap,
root replacement, real junction/hardlink controls and retained partial failure.
Profile/syscall injections create owned mutation states; they do not certify an
external concurrent attacker. The existing output-owner canary renews its default
return and six I/O cases. Root freezes the lab driver/bootstrap Source before
launch; the maintained bootstrap authenticates reader/caller, then the reader
authenticates stager and explicit NO_DATA Source. This lab bootstrap is not a
normal host coordinator.

No actual DATA/fixed20, OriginalArm, capture, graph, Session, receipt/export or
genuine operation is run. Both Study entrypoints remain held. Budgets, epochs,
seeds/order and independent electrical oracles are unchanged. Full30 EXCLUDED;
HARD/PSYCHOTIC unavailable/unrun; protected unread UNCERTIFIED; oldnormal20 NOT
REREAD; playable100=0; beta HELD; genuine original->exactNULL/OBSERVE UNSPENT_GATED.
Production Java/GWT/Player are unchanged; their gates are NOT APPLICABLE here.
Old caller Source-bound gates are not renewed by these scoped I/O checks.
