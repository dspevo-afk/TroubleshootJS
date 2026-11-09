# Current result projections helper R1

Source-only component for `CurrentResultProjections.cohort` and
`CurrentResultProjections.cleanup`, under contract SHA-256
`24f40e39a9d52704a51aefbdf05170c25056447009c4b25605e45ad9de04fd4f`.
Frozen independent references: `REFERENCE_R1.json` SHA-256
`bd0c6f081a6a9d11c47befe531debc87ddbccf02541542253b202a6debe491bc` and
`CANARY_WIRE_R1.json` SHA-256
`b5b20358c18806889b53c8394be627bd5078f0fd7c282e09e285f0f60a357b59`.

- Cohort transfers the actual derived `netIds` and `requested` lists by identity
  into two ordered entries. Debit: 2 field reads, 1 map allocation, and 2 copied
  stores = 5 units, 2 copies, 1 chunk.
- Cleanup performs 23 charged map reads (7 capture/template and 8 per arm), then
  20 charged derivations (8 alias reductions, 2 context reductions, 5 geometry
  equalities plus 4 reductions, and 1 effects equality). It emits the ten
  contract fields in Source order. Debit: 55 units, 10 copies, 1 chunk,
  including one map allocation and one numeric materialization charge.
- Cleanup projects false values without acceptance checks and passes through the
  supplied work scalar. It does not reread duration fields, retain input maps,
  mutate inputs, add counters, or integrate with the normal assembler.

No compile, canary, test, or production build was run; root owns all gates and
integration review. This helper alone does not establish current result
acceptance or producer/caller proof.
