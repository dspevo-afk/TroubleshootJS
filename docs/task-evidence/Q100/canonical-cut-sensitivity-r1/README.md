# Canonical base directed cut sensitivity

**Publication state:** `LOCALLY_ACCEPTED_PUBLICATION_PENDING`
**Result status:** `PASS_CONDITIONAL_CUT_SENSITIVITY_ONLY`
**Scope:** `CANONICAL_BASE_DIRECTED_CUT_SENSITIVITY_ONLY`

This is a bounded, offline diagnostic of one accepted base graph. It asks whether
the captured directed planar graph reaches either accepted goal after opening the
exact local directed bits attributed to the two foreign-clearance owners.
It does not change the solver, board, captured graph, routing policy, or P07 search.

## Disposition

The frozen candidate and its selected host, consumer, source-stability, cleanup,
and negative gates passed. Repository integration and publication remain pending
root review. This draft makes no commit, push, email, or production-adoption claim.

The unchanged P6 parent proof returned `PASS_STAGE_OBSERVATIONS_ONLY`. The P7
analysis returned all four ordered cases and preserved the captured base. The
producer BFS, unchanged minimum-VIA oracle and independent consumer DFS agree.
Both the source proof before and after analysis passed. The result records all
route, layout, electrical, and Q100 acceptance fields as false and production
feasibility as `UNKNOWN`.

## Candidate identity

| Field | Accepted value |
|---|---|
| Board identity | BASE / SEPARATE |
| Root seed | `9007199254740993` |
| Placement seed | `-6587958367974952701` |
| Routing seed | `8445301674744508279` |
| Request / attempt / pass | `0 / 4 / 2` |
| Target net / branch | `A_COIL_LOW`, `QA.C → DA.A` |
| Receipt / lineage / prefix hole count | `307 / 77 / 48` |
| Grid | `478 × 309`, 147,702 cells, 295,404 directed-face states |
| Starts / initial face mask | `114148, 261850` / `3` |
| Accepted goals | `2` |
| Raw native graph SHA-256 | `ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a` |
| Canonical base graph SHA-256 | `ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a` |
| Prefix geometry SHA-256 | `6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb` |

The raw native and canonical graph digests are reported separately even though
they have the same value for this capture.

## Cut and case results

The complete outgoing cut contains 22 queries: 8 positive foreign-clearance
observations (4 attributed to `B_SENSE`, 4 to `B_REF`) and 14 geometric terminal
observations (9 `isLegalMove` rejections and 5 `canTraverse` rejections). The 14
geometric bits remain closed in every result.

| Case | Directed bits opened | Reached states | Directed edges examined | Goal hits | Minimum VIA count | Canonical graph SHA-256 |
|---|---:|---:|---:|---:|---:|---|
| CONTROL | 0 | 12 | 23 | 0 | `None` | `ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a` |
| B_SENSE_CUT_ONLY | 4 | 16 | 31 | 0 | `None` | `6ff4c26e50ae767d1293a2304f31a0e452fed370fe51f491b4d29f694d131bf7` |
| B_REF_CUT_ONLY | 4 | 16 | 31 | 0 | `None` | `ccfef0341fcfb7f1621e0880f5817aaf4bbd9da2197b22041c8cd0bb1b685c5d` |
| BOTH_OBSERVED_CUTS | 8 | 20 | 39 | 0 | `None` | `c84c8810c85065878dbcf4f2fdbd98f66bc0c586f1144bc6e8e7972eed2235b9` |

Every BFS completed its full reachable-state census. All four cases are
unreachable with `minimumViaCount: None` and `FRONTIER_EXHAUSTED`; none reaches
either captured goal. Counterfactual variants are marked
`actualCaptureAttested: false`. The base graph, starts, goals, vias, dimensions,
protocol, model, primary face, and case identity were preserved.

## Validation and resource evidence

- The selected literal suite passed all 21 tests. It uses a separate
  Floyd–Warshall closure for tiny fixtures and covers direction, face/start/goal
  semantics, owner subsets, malformed cut descriptors, full closure hashes, and
  work accounting.
- The actual CANARY and strict consumer passed. Seven selected-host negative
  cases passed for altered pins, boolean limits, unknown/duplicate keys, and
  output boundaries.
- The final semantic reader controller passed one synthetic copied-data control
  and 12 exact-code rejection cases. That synthetic control is a reader control,
  not proof from the actual worker result. Parent process, stdout, and stderr
  bytes remained identical to the original; all child processes were terminated.
- Transitive source/parent verification covered 1,084 rows and reported stable
  source bytes before and after analysis. No production source changed.
- P7 analysis used 401 measured groups; final transport and host closure bring
  the measured total to 403 of 640. The largest group was 362.658 ms. The largest
  single graph clone was 738,512 entries, below the 1,048,576 limit. Copy work
  was 1,329,320 entries in 330 units; BFS examined 64 popped states; 56 opaque
  calls were measured. Actual new P07 steps, advances, and expansions were all
  zero.
- Full host wall time was 88,805.2538 ms, below the 120,000 ms cleanup envelope.
  Parent qualification wall time was 40,649.793 ms; worker wall time was
  1,555.9037 ms; analysis time was 1,323.8701 ms.

The frozen spec SHA-256 is
`1f4113ee86424063b16ad13a4dfc9540af16099beeb0560fd6315bde691a0dd7`. The
analysis module SHA-256 is
`05d5172b04a9382aa196146e1b48744a48723fc216b1a376abfd4b22b80b448c`; the test
suite SHA-256 is
`d0e4d6a0f32e1d0d02e2784d07b68853fd35078676e746cc6cc2a1801cfb966e`. The result
also records the pinned graph codec, minimum-VIA oracle, capture reader, and
worker identities.

## Reproduction

The tiny suite can be replayed with the pinned P4 oracle directory:

```text
python test_cut_sensitivity.py --oracle-directory <pinned P4 oracles-r1 directory>
```

Reproducing the captured analysis requires the exact frozen P7 inputs, pinned
sources, selected host, and genuine successful P6 parent receipt. Use the
task-owned evidence bundle and its recorded hashes; do not substitute a parent
context or regenerate the accepted capture. Raw receipts and private failure
artifacts are kept outside this publication draft.

## Limits

The result establishes only that these eight specified local directed-bit
openings do not connect the captured base graph to its accepted goals. It does
not establish whole-net removal, unique trace ownership, routing benefit,
physical feasibility, or board impossibility. No electrical, layout, population,
generation, or P07 acceptance was renewed. CircuitJS remains electrical truth.

Playable100 remains `0`; beta remains held and full30 remains excluded. HARD and
PSYCHOTIC remain unavailable and unread/unrun here. The historical protected
unread boundary remains uncertified.
