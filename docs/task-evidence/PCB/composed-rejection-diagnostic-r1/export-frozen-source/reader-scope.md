# Independent reader scope: composed packing-rejection attribution

This reader qualifies one already accepted public replay:
`tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-2750102463469069236`.
It observes the existing selected normal board and its single existing packing
probe. It does not rerun generation, add a route, change a budget, or infer an
unreported rejection cause.

The ordinary `PCB_SPARSE_NORMAL_DIAGNOSTIC` row is checked by loading the
previously accepted `random-composed-whitespace-r1/qualify.py` and calling its
pinned `load_published_reader()`. That published reader itself pins the sparse
diagnostic reader and its two original guarded transformations. The new reader
compares the complete ordinary row and whitespace sampler to the accepted
receipt. The only permitted difference is the integer `packingProbe.millis`
and the same single `;millis=N;` literal in `packingProbe.canonical`; the two
clock values must agree within each row. The original identity, inventory,
endpoint, graph, geometry, metric, whitespace and packing checks still run.
The final normal diagnostic marker, including `assertions=327`, must match the
accepted receipt exactly.

The task-only `PCB_PACKING_REJECTION_DIAGNOSTIC` records are validated as
typed observations. Each plan's UTF-8 canonical text is independently hashed
with SHA-256 and checked against its outline, canonical signed-long seeds and
P05 route policy. The sole outer `PACKING_CAUGHT` event must bind to the
accepted selected geometry. When its actual outer kind is `ROUTING`, the
immediately preceding diagnostic event must be a matching
`COMPACT_ROUTER_REJECTED`; the plan identity, candidate, outline, layout seed,
routing seed, policy and complete recovery statistics must match. The
router's typed reason must agree with its recovery reason, its message with
the wrapper's message, and any blocked net with the actual board's net inventory.
The assessment retains the correlated inner observation separately from the
outer wrapper and counts unrelated compact observations separately. The
correlated route work must report the observed five ordering passes and
305104 expansions. Earlier uncorrelated compact-route rejections are retained
as observations, not mislabeled as the packing probe. A placement, envelope or
quality outer catch is attributed only to its observed type/kind/reason; its
underlying finalization subtype remains unknown unless separately captured.

Invoke the independent qualifier with the exact private root:

```text
python -B qualify.py --private-root <private-root>
```

Before reading results it calls `export_inputs.record(private_root)` and checks
the frozen seal. Both `canaries` and `composed` phases must show successful
actual tool exits, separate post-exit cleanup receipts, matching driver-birth
records, and the exact before/after input-audit marker. The six-field execution
projection must equal its frozen baseline before and after. The ordered
parameter-binding canaries cover the noncanonical seed, excluded family and
an explicitly passed but absent private-generator file; the selected adapter's
existing file validation must reject each before compiler or native work. The
private rejection logs are checked against their recorded hashes and sizes,
without copying their raw stderr into the assessment.

The composed receipt must contain one ordinary diagnostic row, one whitespace
sampler row, one outer packing catch and the exact native completion markers.
The reader reruns the prior five row negatives and targeted rejection-event
negatives for selected-geometry binding, recovery counters, event correlation
and the inner canonical fingerprint. Counter controls update their canonical
records coherently; the correlation control rehashes a valid changed input plan
before requiring correlation to fail. A further original-row counter control
passes the retained row oracle before the exact nonclock comparison rejects it.
Every negative must reject at its intended assertion; acceptance is an error.
The assessment reports only this finite
replay and its observed attribution. It makes no production acceptance,
all-seed, manufacturing, optimality, new player-flow, or fresh matrix/build
claim. The independent board-surface review remains `NO_GO` for another paint
change.
