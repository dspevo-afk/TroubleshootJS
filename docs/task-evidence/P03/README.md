# P03 physical placement qualification

Candidate identity and final-source gates are shared with
[U01](../U01/candidate-provenance.json). Layout epoch 6, dependency interpretation
`tsj-generation-dependencies-v5`.

`PcbPlacementConstraints` supplies package access, public regions, domains,
connector anchors and explicit isolation barriers. `PcbPlacementPlanner` packs
actual package/selection/courtyard/escape envelopes into six bounded outline
candidates, then performs two local refinement passes. `PcbAccessPlanner`
requires every escape to join one common free channel. Final compaction retains
access demand and repeats that validation. Limits are 128 parts, 16,000 units per
axis, 64 million square units and 700,000 access cells.

PASS: 864,267 native placement assertions across RB15/RB30/RB56/RB100, seeds
0, 3 and -17, all six variants and an exact repeated selected variant. Independent
checks retain the original component IDs and package envelopes. Negatives cover
overlap, insufficient access, outward anchors, excessive growth, enclosed or
divided channels and impossible isolation. Three current LED seeds also complete
placement, routing and final geometry validation. Physical planning does not
consume a selected fault or live electrical values.

## Frozen comparison

[Corpus contract](../../../tests/benchmarks/p03-p04-corpus.json) and
[literal package inventory](../../../tests/benchmarks/p03-components.tsv) were
frozen before optimization. [Integrity audit](corpus-integrity.json) matches all
201 component identities, roles, regions, domains and package dimensions to the
original A01 inventory. [Measured results](placement-corpus.json) contain 36 rows:

| Planner | Accepted | Rejected | Meaning |
|---|---:|---:|---|
| Original flat v4 | 0 | 12 | Its 720×400 outline cannot contain even RB15's actual courtyards. |
| Authored inventory grid | 9 | 3 | Fixed region bands; RB100 exceeds the same outline budget. |
| Hierarchical | 12 | 0 | Every corpus board fits its real demand and access constraints. |

Area excludes the tray and uses simulation geometry units, not manufacturing
millimetres. Courtyard utilization is summed courtyard area divided by outline
area; demand utilization includes selection, escape, access and packing channels.
The accepted hierarchical outlines range from 2.37 to 16.46 million square units,
with roughly 14–16% courtyard utilization and 50–56% demand utilization. Empty
area remains explicit; these results do not establish optimal packing.

Timing scopes differ: hierarchical rows include six variants, independent
assertions and an exact repeat; baseline rows include one fixed placement and
validation. The results support bounded feasibility and area comparisons, not an
equal-work speedup claim. Free-channel access is not proof that all large nets
can be routed. Full heterogeneous playable qualification remains Q15 and later.
