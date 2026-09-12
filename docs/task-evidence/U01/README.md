# U01 viewport qualification

Candidate: branch `codex/task43p-final-recovery`, base
`0487555d6bd2e5e8ed4b01652b847d0fffcb900c`. The final source and five production
permutations are identified by [candidate-provenance.json](candidate-provenance.json).

Implemented one composed board transform, permanent zoom/pan, fit and public
region/component/terminal navigation, a separate fixed tray, board-face targeting,
and cursor-centred held-Space inspection. A positive subpixel physical feature
retains one raster pixel in both rendering and targeting; overlapping terminals
remain ambiguous. The pointer remains free. Leaving the canvas, focus loss,
pointer cancellation, modal entry, detach or replacement dismisses inspection.

## Evidence

- PASS: actual JDK8/GWT production build, five permutations, exit 0;
  [build receipt](build-summary.json). Final compilation 82.499 s, linking 1.622 s.
- PASS before R1: 27 maintained native Java suites and independent seed/value/role/
  report readers; [native receipt](native-summary.json). Fresh post-R1 U01 has
  1,233 numeric assertions; affected A11/A04/P01 checks also pass.
- PASS: [compiled U01 report](compiled-report.json), 1,237 assertions and six live
  pad/endpoint/marker correspondences. The fixture must draw before PASS is issued.
- PASS: [forced failure](forced-negative.json) issues no PASS report.
- PASS: four actual production [structural viewport trials](viewport-performance.json).
  Mean provider draw time was 1.09/1.91/3.39/6.12 ms for 15/30/56/100 parts;
  mean target resolution was 0.60/1.85/3.77/6.31 ms. These are coarse millisecond
  timer aggregates, not end-to-end input latency, percentiles or FPS promises.
- PASS: real user-held Space generated 52 trusted moves with 52 distinct composed
  views in one complete gesture; release restored the exact original permanent
  transform. [Event audit](operator-events.json), [analysis](operator-summary.json).
  The user confirmed tracking/restoration and noted the free cursor at the edge.
- PASS: real left/right clicks on visible 0805 and SOT-23 terminals, wheel zoom,
  Shift-drag pan, Space tap, flip and fit. Probe markers follow the same geometry;
  bottom view shows only the bottom-mounted surface packages. Fixture observations
  use actual active CircuitJS endpoints, shared across structural parts.

The browser automation API supplies Space taps, not a held key across moves.
The user's held gesture is retained across the final change: permanent/composed
camera arithmetic and native movement/release handlers are unchanged. The final
rectangle rasterization, generation readiness guard and tray spacing have fresh
native/build/compiled checks. The four controller cancellation cycles do not
claim separate real native blur, modal and pointercancel trials.

The final routing optimization and developer failure diagnostics are the only
source inputs changed before the final A11-R1 repair. The exact reuse boundary is
recorded in [evidence-reuse-audit.json](evidence-reuse-audit.json); the final
production build and affected compiled gates are fresh. The owner's subsequent
R1 repair and focused requalification are recorded in
[A11-R1](../A11-R1/README.md); that audit explicitly retains prior operator and
timing evidence without claiming a complete post-R1 rerun.

## Visible evidence and limits

[Overview](overview.png), [top SMD probes](smd-top-probes.png),
[bottom SMD probes](smd-bottom-probes.png), [100-part overview](100-part-overview.png).
The first three are the audited final3 surface trials; the 100-part image was
refreshed after R1 using Fit board on the actual combined fixture.
The generated diagnosis/repair/retest sequence is recorded with
[P04](../P04/README.md). Large fixtures measure rendering and targeting, not
complete larger electrical circuits. The player SMD catalog, minimap, two-layer
autorouting and later qualification milestones remain outside this scope.

Development failures are retained in [retained-failures.json](retained-failures.json).
The historical Windows CLI browser wrapper is not certified by these Browser
interactions. The completed pre-R1 A10 regression passed 1,273 assertions and
24 attempts; Task41/A09 reports also passed their strict readers. Fresh final-R1
Task49, surface, privacy and normal repair gates pass. The [root review](root-review.json)
found no blocker. [Cleanup receipt](resource-cleanup.json): owned preview stopped;
scratch cleanup was policy-blocked and two startup error tabs remain. A11-D1
remains nonblocking coverage debt.
