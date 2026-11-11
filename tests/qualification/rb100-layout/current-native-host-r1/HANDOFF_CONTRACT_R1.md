# Original output identity and ephemeral producer Source token

The current caller owns five files: its attempt marker and the four original
context/projection/producer/pending-evidence outputs. Each exclusive write can
already return the reader token matched to the written handle. The qualifier now
retains all five tokens in memory and supplies them to its final exact closure.
Each final read must match its original token, including canonical path, stable
file identity and complete leaf snapshot through the existing reader projection.
Same bytes in a replacement file are insufficient. No token ledger is persisted.

`_validate_output_closure(..., expected_write_tokens=..., include_source_token=True)`
requires the exact five-name token map. After final closure/name/root checks it
adds only `producerSourceToken` for `current-input.java`. Passing the map without
requesting the token retains the default fact shape and still enforces original
identity. Current tokenless helper consumers retain their byte/readback scope;
that path does not establish original write-handle continuity. The actual
qualifier always supplies its original five tokens.

The in-process `qualify(..., include_source_token=True)` API places the optional
token at `result['outputClosure']['producerSourceToken']`, available only after
the normal final consumed-input, closure and root checks succeed. Default/CLI
result shape stays unchanged; no CLI flag requests a token. Pending evidence is
still written as pending before final checks and contains no token. The token
is an ephemeral identity fact, not a replacement for trusted invocation/return
authority. No serialized dict, hash spelling, pending record or same-shaped
test result can establish a real successful normal qualifier invocation.

A future authenticated phase coordinator must own that actual invocation and
exclusive endpoint lifetime. It may pass the returned token together with the
matching Source length/SHA into the N52 stager. No such coordinator or normal
activation is delivered here. Actual qualifier/CLI wiring is Source-reviewed
only; no normal qualifier positive or DATA read runs in this milestone.

Selected Windows finite gates use authored five-file CODE byte fixtures and the
explicit NO_DATA producer. They validate real writer/closure/token-to-stager I/O,
all five same-content replacements, wrong/malformed/missing/extra tokens,
changed bytes/names/roots, token-request guards and default helper facts. The
fixture contents deliberately do not constitute normal prior-input evidence.
Existing N52 staging24 and output-owner6 cases are freshly renewed on the changed
caller Source. Root freezes selected Sources before/after actual -I/-B processes
and owns their original process-handle receipts. Leaf review is Source-only.

Path/handle snapshots require exclusive endpoint lifetime; they are not an atomic
external-namespace transaction. N52 retains its documented same-volume constraint.
Failure outputs remain retained, with no overwrite/retry/refund. Source/class/
tool/launch and semantic/receipt/lifecycle closure remain required before normal
use. Old whole-caller Source-bound gates are not renewed by these finite tests.

No actual fixed20/oldD/heldout/protected DATA or generated DATA factory, native/
OriginalArm/capture/graph/Session/receipt/export/genuine operation. Both Study
entrypoints remain held. Budgets, seeds/order, epochs and electrical oracles stay
fixed; full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected unread
UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD. Java/GWT/Player
unchanged/NOT APPLICABLE; electrical/population NOT RENEWED; CI NOT RUN pending
self-hosted runner registration.
