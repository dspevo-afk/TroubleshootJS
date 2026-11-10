# Current result body correlations

The current higher fixtures now also contain ownership/alias fields under the
[ownership summary preflight](../ownership-summary-r1/README.md). Native replay
uses that final assembled codec (34 admitted gets); earlier accepted snapshots
remain attached to their recorded commits. Lower transport oracles are unchanged.

The higher-level Java and Python fixtures in this directory now include cleanup
restoration fields under the [current restoration preflight](../cleanup-restoration-r1/README.md).
Native replay uses that final assembled codec. Earlier fixture snapshots remain
at the accepted body-correlation commit; the pure three-check Source stage below
is an intermediate stage. The lower transport oracles remain unchanged.

This finite qualification component rejects inconsistent prior context identity,
fixed arm roles, and cleanup duration before current result encoding. It does
not validate the whole result, authenticate its source or receipt, establish
borrowed lifetime, or authorize a genuine envelope.

`derive_body_correlations.derive(study, codec, fragment)` accepts the exact pinned
current Source bytes and returns the two derived Sources. It has no filesystem,
CLI, process, class-loading or producer interface. Its caller retains existing
Source I/O admission. Incompatible or already-derived inputs fail before return.
All three current export passes migrate to `writeCorrelatedBody`. The existing
`writeBody` remains the low-level canonical transport API; its literal, wide,
domain, depth, visits, marker and byte-cap oracles are preserved unchanged.

Run the 12 Source tests on one line:

    python -I -B test_body_correlations_source.py --study-source <pinned-current-Study.java> --codec-source <pinned-current-codec.java> -v

The Study input is236944 bytes/SHA25690f4c227d125587a7d1c8d3b324fe7fd375fd89af7a8a34a83a65a3c0d310bad;
the constructor-equipped codec is20119 bytes/SHA25604b016afa690d2154f0b970640bc30b7c4887b450622444f8f18329bc04c3672.
Use the current admitted Source pair, not old whole builders or data classes.
The test driver reads these explicit fixtures; it does not certify a Source I/O
host or publish result files.

Run the root's independent Python literal checks on one line:

    python -I -B test_literal_oracle.py --retained-oracle-root ../native-v2-codec-r1 -v

Native replay requires JDK8/target7 and the exact retained current dependency
closure. The private host is not a standalone clean-clone build command.
`CurrentBodyCorrelationsCanary` accepts no arguments. Its fixtures are ordinary
synthetic Map/List values; they deliberately do not represent a full valid DTO.
It never calls exportV2, an actual/fake arm owner, a factory, graph, Session,
routing, positive original DTO or a genuine run.

The checks use existing block admission, not per-field native-unit pricing.
Valid encoding and real R5 count fees are compared with the transport entry.
Full count/write/compare/publication and its fee equations are Source-reviewed,
not freshly executed here. Exclusive stable-body lifetime remains a precondition.
See CONTRACT_R1.md for exact errors and limits; evidence reports runtime scope.
