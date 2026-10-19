# Root review before native runtime

Generator adapter SHA256:
`85b9f70ede596d9bbdeefd35dd72c48ce52d11b9cbe53433eecffff60a19664b`.
Shared fixture:
`f345b0b4d6dc513440d83c00c4cd38a4ae8a697de499133415845fc46116bfb0`.
Original wrapper:
`8e2894aa5fa000c893ed1d182a63b65f56129b36ee878fc6e5048dead18f086b`.
Translated wrapper:
`067bec1e394d951204785753ded3e968e5a3fb817b69e80ca9c9b7762474f8b4`.

Root reviewed the complete generator diff and shared fixture's setup, translation,
validation, route receipt, geometry metrics, canonical fingerprint and JSON paths.
The adapter adds a generic supplied-plan entry and result wrapper; existing router,
ranking, finalization and border-trim implementations are untouched. Plan.candidate
must equal the supplied attempt before Session creation. Requested attempt1 and
planner-local algorithm0 remain separate; the fixture derives the latter from
the existing density/repeated-region predicate and pins the complete plan SHA.

Each wrapper constructs one ordinary source plan, board and planner result.
The translated variant moves only QA's whole footprint by literal(-300,-30),
checks all53 other footprints, pose, physical package/contract, pad/net/terminal
identity, escapes and pad/probe rectangles, and preserves plan metadata. Fresh
placement validation precedes routing. Source/live graph and supplied/original
plan fingerprints are rechecked after routing and finalization.

The ordinary P07 session path owns routing and finalization. One supplied plan
is counted truthfully; no P05 or six-plan ranking is claimed. Existing constants
bound three orderings,1million total/100000 branch expansions,250000 cells and
32768 work quantum. The original must reproduce accepted final geometry and all
declared scalar metrics. Translated typed rejection remains explicitly REJECTED;
unexpected errors, invalid input, metric guard failure and source drift fail.
The existing whitespace guard<327600 is retained, as pre-runtime clarification
records. The independent reader checks both outcome branches, exact baseline,
whole QA/pad/probe translation and89-or-fewer declared boundary mutations; actual
synthetic count is recorded in reader-canaries.json (currently88 negative/2positive).
Synthetic checks are not route evidence. Runtime is NOT RUN at this review.

Route phase time is the existing P07 begin/slice accumulation, excluding
finalization. Root driver records the entire native phase including compilation,
both bounded JVMs and terminal cleanup, not per-JVM elapsed; cleanup duration
is not measured separately. Native60s/JVM and production90s/640/5s stay unchanged.
