# R10 rejected; R11 private observer redesign

R10 is INCOMPLETE and NOT ADOPTED. At the unchanged 640-unit cap, its actual
task still needed 345,193 ordinary-edge probes and 147,702 VIA probes. Four
4,096-probe scans per group require at least 22 further edge groups, ten VIA
groups and one finalizer: at least **673 total groups**, even ignoring final
comparisons, metadata, guard and report. The independent arithmetic audit is
`r10-work-limit-audit-r1.json` in the task-owned temporary evidence directory.
It validates the recorded failure and lower bound, not a successful graph.

A read-only source review found no earlier reusable full graph snapshot.
The production task creates that snapshot only at finish. Router occupancy
queries update counters/conflicts, so the real query/pristine clones and full
before/after comparisons remain necessary. R11 retains those owners and proofs.

R11 explicitly revises this unpublished observer's grouping and representation:

- It batches at most four existing copy/comparison calls, each still limited
  to 32,768 entries. Its conservative observer-entry bound is now
  `32768 * internalCalls`, at most 131,072 per graph group. This changes R10's
  provisional one-copy-call grouping; it is not described as unchanged R10.
- It represents every mask losslessly: lowercase hexadecimal step nibbles and
  ASCII `0`/`1` goal/VIA digits. New private schema and protocol versions reject
  the old unpublished format. The reader independently decodes every state and
  retains all previous boundary, source, goal, path and counter expectations.
- It serializes, encodes and hashes the same bounded ASCII chunk in one charged
  group, without a whole large string conversion. Each advance emits at most
  32,768 characters and hashes at most 32,768 bytes. Provenance walks at most
  32,768 states per metadata group, preserving every attribution check.

The original 90-second operation, 640 enclosing units, 5-second unit deadline
and 32,768 actual P07 owner steps per unit remain unchanged. Observer entry work
remains separate from P07 continuation steps. Existing four-call/2,560-call
ceilings, routing/request/grid/VIA/transition/access caps, artifact/row bounds,
cleanup/shutdown limits, source truth and independent old oracles stay fixed.
No production Java, electrical behavior, seed interpretation or epoch changes.

The source-only review found this versioned observer redesign compatible with
those original constraints. Roughly 616 total groups is a design estimate,
not proof of fit or a PASS. Final-source native timing, exact ledger accounting,
complete masks, source parity, independent reader validation and cleanup must
all pass before the full acceptance matrix or publication. Earlier source and
failure evidence remains preserved. No route feasibility or playable100 claim.
