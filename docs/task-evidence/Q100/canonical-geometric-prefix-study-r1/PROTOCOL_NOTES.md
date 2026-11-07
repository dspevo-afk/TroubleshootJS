# Protocol and accounting notes

This explanatory document is not a native input. The separate qualification records acceptance.

The preserved `NATIVE_TRANSPORT_R1.md` has SHA-256
`62b55f64dabb23a6e46a40809254a55b2725afefa8c8460643cd778bcd6c6718`.
Its provisional sentence grouping identity IDs and `layer` as strings conflicts
with its requirement to preserve the exact inherited P6 identity. IDs are strings;
`layer` is integer 0 or 1 in P6 and P8. A boolean is not an integer layer. This
clarifies wording; the frozen bytes, wire fields and runtime types are unchanged.

Actual operation-receipt `GROUP` records plus three wrapper groups remain limited
to 640. Nested observation groups reconcile with charged observation units under
the separate 32,768-unit limit. Shared copy/comparison entry totals reconcile with
their 4,096-entry chunk units; the single-clone limit remains 1,048,576 entries.
Callback time overlaps its query; it is not an additional scheduling unit. The
terminal-prefix proof is outside the query and receives one metadata charge,
starting before callback preparation and allocation and including failure paths.
Complete work and group time retain their 90-second and 5-second limits.

The first fine callback in `REASON_THROW` runs before this target's graph selector
and deferred capture. Earlier rejected selector offers are legitimate. The
exception proof requires zero selected lineages and graph callbacks and no
retained lineage/snapshot, along with the actual candidate and terminal-prefix
proof. The graph SHA in the partial projections references the qualified NULL
parent capture. It does not claim that the throwing run captured a second graph.

The real request summary and P07 request-boundary event are finalized after an
advance completes. An early callback throw does not finalize them. The exception
proof therefore uses the existing actual owner/context cleanup and P5/P6/P8
evidence. The reader binds the completed 22-query parent projections and partial
receipt through the target branch to the completed NULL/P4 context. The normal
path retains its strict one-capture and completed-context checks. The exception
has exactly four files, one fine event, no coarse event, a null return value, and
an incomplete observation footer; it cannot qualify as a completed observation.

The selected unchanged process module returns eleven receipt fields. Transport
authority is UTF-8 of the original returned strings. It is not a claim that the
module preserves the original OS pipe bytes, BOMs, or invalid UTF-8 sequences.

These notes add no routing policy, model epoch, source budget, wire field, new
seed or physical-layout qualification.

Frozen source copies use per-packet `-text` attributes to preserve their bytes.
The three inherited CRLF files retain ordinary whitespace checks with CR-at-EOL
allowed. The frozen unified patch has two otherwise empty context lines whose
single prefix space is patch syntax. Only blank-at-EOL checking is disabled for
that patch artifact; the corresponding complete Java files keep ordinary checks,
and the patch's SHA-256 and exact overlay transformation remain bound. These are
per-path settings, as defined in the [Git attributes documentation](https://git-scm.com/docs/gitattributes).

The old CALLBACK_THROW uses P6's unchanged inner exception kernel with a local
descriptor derived from the actual native marker, runId and validated process
wall. It does not synthesize an old P6 host/index/receipt. Only a top-level copy
of P6 pins points to the already validated BASE NULL run; original pins and
module globals remain unchanged. P8 grants the old throw no work credit. Exact
exit 1 specializes the general nonzero wording to the pinned P6/P8 producers,
whose main methods call System.exit(1) on these intentional returns/failures.

Source-inventory keys are sorted. Reason JSONL has compact unique keys in its
native-authored order; the harness preserves parsed insertion order for exact
positive byte equality and mutations. Summary and input-manifest sorting remain
unchanged. Boolean values remain distinct from integer fields.
