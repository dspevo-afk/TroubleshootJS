# Next geometric rejection-reason study

**State:** `NOT_STARTED`
**Purpose:** Explain the already captured geometric terminal outcomes without
changing the accepted graph, route policy, or electrical model.

## Frozen starting point

Use the accepted P7 BASE / SEPARATE capture only: root seed
`9007199254740993`, placement seed `-6587958367974952701`, routing seed
`8445301674744508279`, request 0 / attempt 4 / pass 2, `A_COIL_LOW`,
`QA.C → DA.A`, receipt 307 / lineage 77 / H48. Preserve raw graph SHA-256
`ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a`, prefix
geometry SHA-256
`6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb`, the
22-query order, and the 14-row geometric classification.

P7 recorded eight positive foreign-clearance observations (four for each of
`B_SENSE` and `B_REF`) and 14 geometric terminal outcomes: nine rejected by
`isLegalMove` and five rejected by `canTraverse`. The eight positive local bit
openings were insufficient to reach either goal. The 14 geometric bits remained
closed. This next study explains those existing outcomes; it does not add new
opening masks or query rows.

## Questions to answer

1. For each of the nine `isLegalMove` rejections, which evaluated subpredicate
   made that original query terminal? Candidate predicate families to verify
   against the pinned implementation include escape-vector checks and start/end
   pad constraints. Do not assume these are the complete or exact source names.
2. For each of the five `canTraverse` rejections, which evaluated subpredicate
   made that query terminal? Candidate families include domain/corridor checks,
   pad escape reservations or touches, and component body/courtyard checks.
   Confirm the actual order and short-circuit behavior from the pinned source.
3. Preserve the existing positive-owner evidence as captured. Generic mixed-owner
   evidence remains mixed; do not infer unique trace ownership from a clearance
   result.
4. Preserve the observed root endpoint `DA.A` and the empty additional
   reached-PTH pad list as valid facts.

## Method and constraints

1. Freeze the exact source files, call sites, event schema, caps, timeouts, and
   exception/cleanup behavior before implementation. Pin every consumed byte and
   retain the original parent receipt.
2. Use a passive, generic observer at the existing `isLegalMove` and
   `canTraverse` boundaries. Preserve predicate order, single evaluation,
   short-circuit behavior, return values, exceptions, cleanup, and object
   identity. Record which predicates the original call actually evaluated and
   its terminal decision; do not re-evaluate predicates to create an explanation.
3. Revisit only the original 22 query identities in their captured order. Require
   the same 8 positive and 14 geometric terminal outcomes, with the same 9/5
   split. Do not add queries, mutate graph bits or create a new hypothetical
   frontier. When genuine live observations require replay, use the existing
   qualified native BASE operation and its original search policy and budgets;
   report that replay's actual work separately from observer work.
4. Add sanitized literal tests for reason ordering, short-circuit behavior,
   endpoint identity, mixed ownership, malformed descriptors, exceptions, and
   observer cleanup. Exercise the selected host's actual source/hash/process
   boundaries before any accepted-case read.
5. Verify base graph, prefix geometry, query identity/order, owner attribution,
   process state, source inventory, and cleanup before and after observation.
   Count the observer's actual work under frozen original caps; do not invent
   groups or relax existing limits.

## Acceptance and exclusions

Accept only if all 22 original query records reconcile exactly, all 14 geometric
rows have source-backed terminal-reason evidence, the 8 positive observations
remain unchanged, and the observer leaves predicates, inputs, outputs, processes,
and cleanup state unchanged. Report undecidable or unevaluated predicates as such
rather than guessing.

This plan does not authorize physical routing adoption, whole-net removal,
unique-owner claims, route-benefit claims, board-impossibility claims, broader
prefix changes, new electrical/population qualification, or production adoption.
Any broader-prefix or physical-routing study needs its own frozen scope and
acceptance evidence. P7's conclusion remains limited to its eight exact local
directed-bit openings.
