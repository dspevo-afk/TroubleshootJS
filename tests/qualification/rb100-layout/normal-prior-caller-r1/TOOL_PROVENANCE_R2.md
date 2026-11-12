# Current observations and historical expectations

Normalizer authority V2 has two separate tool inputs. `currentExecutionToolFacts`
contains the current Source manifest digest and the current observer's five tool
rows. Its digest link must equal the unchanged, three-field
`currentSourceAuthority`. The caller's authenticated Source preflight owns these
observations; the pure normalizer checks their shape and link.

`historicalToolExpectations` contains the rows used to check historical process
receipts. These are expectations until the receipts, prior run/Source metadata,
streams and observed resources have passed their independent checks. The four
fixed historical tool pins and native Java's binding to the separate prior Source
manifest remain required. Historical receipt and argument checks use only this
field. Current observations never supply an implicit historical fallback.

The outer authority digest covers both fields. V1 and the old ambiguous
`toolFacts` alias are rejected. `currentSourceAuthority` stays exact and acyclic;
the current tools are not added to it or to the historical canonical context.
The maps may contain equal identities when those tools are the same; provenance
does not depend on requiring different versions.

The caller builder requires explicit historical expectation rows and takes
detached copies of both maps. The historical-input transport contract now supplies
the accepted N67 Source-only metadata from three explicit authenticated paths;
the earlier `historical_tool_snapshot` dictionary API is retired. The canonical
digest and four false authority flags remain required. These declarations supply
expected values, not read permission or capture/prior-run authority.

The N66 compatibility guard runs in its existing position, before any prior
manifest, phase journal, attempt or role access. The expectation-input check runs
immediately after that guard and before those operations. A supplied declaration
cannot bypass the guard, the independent phase journal, the fixed-role reader or
full receipt validation. CLI historical input transport is described in
`HISTORICAL_INPUT_TRANSPORT_R1.md`; normal activation remains unqualified.

This migration enables no normal qualification positive or genuine operation.
Both Study holds stay closed. Full30 MEDIUM remains EXCLUDED; HARD/PSYCHOTIC remain
unavailable and unrun; protected-unread remains UNCERTIFIED; oldnormal20 remains
NOT REREAD. Playable100=0 and beta HELD. CircuitJS truth, exact product seeds,
epochs, order, budgets and independent oracles are unchanged.
