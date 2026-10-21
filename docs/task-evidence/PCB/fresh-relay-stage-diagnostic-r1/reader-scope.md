# Fresh relay receipt reader

`qualify.py` exposes `validate_receipt(receipt: str) -> dict` for exactly one
ordinary public replay:
`tsj-alpha/6/EASY/RELAY_OUTPUT/-397178238694963708`.

The reader verifies the published sparse-row oracle against the frozen
`production.borrowedPublishedInputs` SHA-256, then applies exactly two in-memory
changes: the expected family/profile/seed tuple and placement-packing epoch 3 to
4. It preserves that oracle's row, graph, physical mapping, metric, and
whitespace-sampler assertions, and runs its five existing identity/mapping/
independent-metric/epoch negative controls. The native diagnostic assertion
marker is parsed for the exact relay identity and a positive assertion count;
the reader does not hard-code a prior family’s count.

For `packing-span-checkpoint@1`, it requires SOURCE, ORDINARY, PAIRED, and FINAL
in that order for every supported packing4 disposition. It hashes each
canonical plan, parses its component poses and pad fingerprints, verifies the
full component/terminal-pad identity against the exported layout, and
independently recomputes every exact-two-pad net span from the canonical
coordinates. It checks stable candidate/seeds/origin, grid10 nonincreasing
outline dimensions, the shared 60,000 evaluation bound, and FINAL equality to
PAIRED except for phase. For a retained disposition, SOURCE spans must match
the selected incumbent up to coordinate translation; for SELECTED, the actual
selected spans may differ and are bounded by the FINAL proposal.

The exported `planCandidate` is retained with that name; it is the resolved
planner variant and is not treated as an outer generator attempt index.

The source-backed necessary-pair screen uses exported net endpoints,
two-terminal counts, anchors, regions, domains, and linked terminal-domain
identity. It reports the observed counts and requires zero pairs pass all those
necessary conditions. This does not claim routing feasibility, prove an
alternative layout, or inspect unexported isolation-body geometry.

Typed packing-cause events are checked using the schema validator in
`composed-rejection-diagnostic-r1/qualify.py`, pinned in the reader by SHA-256
`461a8283ebfb1e056ab9e106127b2af9f9cc7c2121acf76d0e705393791e7be7`. The
selected-baseline binding is supplied from this receipt’s packing probe. Any
outer catch must bind to the actual FINAL checkpoint; a routing catch must
correlate to its immediately preceding inner rejection and actual probe
counters. Earlier unrelated inner routing events remain allowable. Cause
negative controls run only when the receipt contains an outer catch.

The root wrapper owns actual-host canaries, frozen private-source seals,
execution provenance, separate native tool exit records, driver-birth cleanup,
and the native focused-suite final marker. This reader accepts receipt text
only and makes no claim about those external records, visual cleanup, a
placement/routing optimum, production acceptance, or broader seeds and
families. No native export, build, test suite, or sample-receipt execution is
performed as part of this file’s preparation.

Root preseal review replaces the runpy-copy baseline assignment with one exact
source-literal adaptation and exec into the event validator namespace; positive
and invalid-baseline in-memory canaries verify this binding without sample data.
Root also returns all parsed component poses/changed IDs, requires the source/
visual nine-part inventory and exactly one final marker across families, and
adds FINAL counted-work and valid-hash canonical-suffix relation negatives.
Original released reader is retained privately; no native/data gate preceded
these corrections.
