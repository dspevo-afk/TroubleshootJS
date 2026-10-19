# Passive pose policy core worker notes

Base source identity: `5e6b1f66f31b9826b3583f8ad025f3b686f6d005`.

The bounded core adds `passivePosePolicyVersion` to placement constraints, with
all existing constructors defaulting to policy0. Policy1 is accepted only for
density14, `MEDIUM_BOARD@1`, and two-layer routing. Its helper builds one TOP90
template from the already selected canonical TOP0 geometry for the four exact
passive package references and alternates existing visits by local ordinal.

The planner precomputes the alternate while constructing eligible candidate1
items (medium population, repeated-control regions, and non-anchors). Its sizing
uses the maximum TOP0/TOP90 access-envelope demand and extents; the original
TOP0 envelope remains the source for advisory placement preferences. Ring and
fallback visit counts and the existing evaluation cap are unchanged.

The native contract source covers all seven finite geometries, literal turned
pad locations, stable component/pad/geometry identity, source immutability,
63/63 alternation over126 visits, inactive and invalid contexts, constructor
compatibility, conservative demand/extents, and the real planner grid-clipping
seam. It records the radial electrolytic TOP0 envelope `(85,142,150,192)` and
TOP90 envelope `(86,165,172,170)` at origin `(100,200)` with margin20; the
turned escape envelope is wider and drives the larger demand product.

Validation status: source review only. Compiler, native contract execution,
solver, placement/routing acceptance corpus, runtime, GWT build, and player-flow
validation were not run by this worker. Generation/version glue, admission,
identity, replay, and final gates remain with the root integration owner.
