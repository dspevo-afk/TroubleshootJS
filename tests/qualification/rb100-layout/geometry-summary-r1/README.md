# Source-geometry summary preflight

Current higher fixtures use the [complete arm projection check](../arm-projection-r1/README.md)
with65 Map gets/4120 hooks. Earlier frozen acceptance stays attached to its
recorded candidate; independent lower transport oracles are unchanged.

Current higher fixtures use the [cohort projection check](../cohort-projection-r1/README.md)
with59 Map gets/4052 hooks. Earlier frozen acceptance remains attached to its
recorded candidate; lower transport oracles are unchanged.

Current higher fixtures/canaries now use the [nested cleanup schema check](../cleanup-schema-r1/README.md)
with47 gets/154 hooks. Prior frozen acceptance and pure Source stages remain
attached to their recorded candidates; lower transport oracles are unchanged.

Current higher fixtures/canaries now use the [copy-effects summary preflight](../copy-effects-summary-r1/README.md)
with46 gets/152 hooks. Prior frozen acceptance and pure Source stages remain
attached to their recorded candidates; lower transport oracles are unchanged.

The current writeCorrelatedBody entry checks sourceGeometryUnchanged against
five exact comparisons among six recorded geometry strings, after ownership
checks and before encoding. Six Strings must have length at most the existing
BODY_STRING_LIMIT256; the summary must be a strict Boolean. Matching false is
valid. This is metadata agreement, not actual geometry preservation.

The reviewed current Sources do not prove64-lowerhex format for all six fields.
No such rule is added. Matching empty/short/256-character printable strings may
pass; the ordinary formatter still enforces printable ASCII afterward. This is
not complete DTO or SHA-shape validation, nor whole-body zero-output preflight.
Specific new map/type/length/summary failures occur before encoding.

The extension reuses Map and Boolean owners and adds one private String reader
using the existing limit. Eight gets and controls, five equals, four eager ANDs
and one summary comparison add33 hooks/pass,99 over three. Total139 correlation
hooks/42 gets. Hooks are not native units; no Meter/group/copy/byte/mutation
operation is added. Lower writeBody and exact independent1177/6370 byte oracles
are unchanged. Current higher fixtures migrate to existing producer fields.

`derive_geometry_summary.derive(codec, checks, reader)` admits only the pinned
28660-byte codec/SHA2560faa081daf3c6b478424c088759488fa2d6ae784574b5e3362993a8ef4b732c7
and reviewed fragments. Inverse recovery conserves all other bytes. The pure API
owns no filesystem, CLI, class loading or producer interface; existing caller
owns admitted Source I/O. CONTRACT_R1.md defines ordering, errors and limits.

Run12 Source tests:

    python -I -B test_geometry_summary_source.py --codec-source <pinned-current-CurrentV2CanonicalCodec.java> -v

Run independent literal tests in current-body-correlations-r1 with its documented
command. Native replay requires the exact retained dependency closure and
JDK8target7; the private host is not a standalone clean-clone build entrypoint.
GeometrySummaryCanary accepts no arguments. Its tests use only ordinary synthetic
Map/List/scalars, codec and actual Meter.

No receipt files, positive prepare/binding, factory, actual/fake arm owner, graph,
Session, positive original DTO, executeArm/R9DTO, routing, exportV2 or genuine
envelope. Full export fees are Source inference; actual same-body count is a
separate test. Whole-result/authority/lifetime/actual geometry remain unqualified.
GWT/Player N/A, electrical/population NOT RENEWED, CI NOT RUN. All budgets,
exact seeds/epochs and exclusions unchanged: full30 EXCLUDED, HARD/PSYCHOTIC
unavailable/unrun, oldnormal20 not reread, protectedUnread UNCERTIFIED,
playable100=0, beta HELD, genuine UNSPENT_GATED.
