# Ownership summary preflight

The current codec's writeCorrelatedBody entry checks copied capture ownership
and both arm alias summaries after its existing restoration checks, before
encoding. Fourteen strict Boolean leaves must satisfy the three equations in
CONTRACT_R1.md. This validates metadata agreement, not actual owner release,
absence of aliases, complete result validity, source authority or borrowed lifetime.

The extension adds53 sink hook calls per successful pass, including14 Map gets.
Total correlation preflight is106 hooks and34 gets. Hooks are not native units:
block zero is already admitted; no Meter/group/copy/byte operation is added.
The private Boolean reader now accepts an error code; its old signature is
retired and existing restoration errors retain precedence. The lower writeBody
transport API and its independent1177/6370 literal oracles remain unchanged.
Current higher-level Java/Python fixtures migrate to the stronger contract.

`derive_ownership_summary.derive(codec, checks, reader)` accepts only the reviewed
25156-byte codec/SHA256344223e4f3d12bc9c1bb219bca80a8c711ce99aed8fe4b309c1043da6b5f4dc4
and two pinned fragments. It changes only the existing private reader and five
calls plus appended checks; inverse recovery conserves every other byte. It owns
no filesystem, CLI, class loading or producer interface. Callers retain existing
admitted Source I/O.

Run12 Source tests:

    python -I -B test_ownership_summary_source.py --codec-source <pinned-current-CurrentV2CanonicalCodec.java> -v

Run the independent literal tests in current-body-correlations-r1 with its
documented command. Native replay needs the frozen current dependency closure
and JDK8target7; the private host is not a standalone clean-clone build entrypoint.
OwnershipSummaryCanary accepts no arguments. Its truth rows use both capture
values and all32 masks independently for each arm, with flipped-summary rejection.

Tests use synthetic Map/List/scalars, codec and the actual Meter only. No receipt
files, positive prepare/binding, factory, actual/fake arm owner, graph, Session,
positive original DTO, routing, exportV2 or genuine envelope runs. Full export
fees are Source-reviewed; real count equivalence is tested separately. GWT/Player
N/A, electrical/population NOT RENEWED, CI NOT RUN. All budgets, exact seeds/epochs
and data exclusions remain: full30 EXCLUDED, HARD/PSYCHOTIC unavailable/unrun,
old normal20 not reread, protectedUnread UNCERTIFIED, playable100=0, beta HELD,
genuine envelope UNSPENT_GATED.
