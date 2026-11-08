# Validation

All results below use the selected JDK8, isolated no-bytecode Python and maintained
root process launcher. Actual request, parent, owner, process and raw streams are
retained privately and hash-bound in RESULT_PROOF.json. Exit0 and proven removal
of the owned process root/logs are required; cleanup duration is not invented.

| Gate | Result | Child duration |
| --- | --- | --- |
| Fresh1092-source prepare | PASS | 29.7310669s |
| Actual selected JDK8 compile | PASS; expected204B warnings | 8.0661003s |
| Final source/class/compile provenance | PASS, FINALIZED_ONLY | 48.2368235s |
| Final four-fixture native canary | PASS;19347B/stdout, stderr0 | 0.7104511s |
| Actual exclusive canary host | PASS;592B derived summary | 30.3338482s |
| Actual occupied-output host control | PASS expected rejection; file unchanged | 33.6193191s |
| Actual bad manifest hash control | PASS expected rejection | 0.4689016s |
| Sole authored native comparison | PASS diagnostic export; no native retry | 17.6436517s |
| Final independent authored reader | PASS finite reference + both reconciliation gates | 30.1877774s |
| Final focused consumer tests | PASS76:63reader,3real parser,4prior identity,6role oracles | each below1s |
| Real-filesystem exact phase closures | PASS6, reused on identical builder | 0.4741863s |
| Final source-review exporter tests | PASS9 | 0.4584213s |
| Actual four-source review export | PASS; exact five-file readback | 0.4884194s |
| Actual exporter negative controls | PASS2 fresh (occupied, hash) +2 junctions reused on unchanged guards | each below0.5s |

The authored operation marker reports17.2367483s,465 groups,12695355 units,
max group1.2045748s and max32768 units. Native wrapper total17.9001503s and final
reader wrapper total30.4314639s are separate process measures. Actual cleanup
evidence is1.809ms at the top-level logical cleanup; OS log-root cleanup has no
isolated stopwatch. Both process paths prove termination and physical root/log
absence. Cooperative limits do not certify preemptive interruption.

Final export R16509 bytes/C5 chunks/M639 marker bytes independently gives28units,
33689 copied entries and12chunks. Final copies79558entries/13259chunks stay under
1048576; each segment is bounded4096. Actual original GROUP/PAD reconciliation
passes using the unchanged independent P07 parser, not supplied owner counters.

Original limits remain: authored90s/640groups/5s per group/32768units, native
wrapper480s, reader180s, prepare/compile60s, cleanup120s, one-million expansions,
48 holes and four transitions. Earlier90s/60s native/host caller requests were a
recorded mismatch to the wrapper contract, not a budget change or acceptance.

Production/GWT/visible Player gates: NOT APPLICABLE to this native-only change;
no new product/electrical/population qualification is claimed. Full30 EXCLUDED;
HARD/PSYCHOTIC NOT RUN/unavailable. All failed attempts stay FAIL in FAILURES.md.

Exporter resource closure: both original test links are physically absent and
their targets unchanged. Automatic approval review rejected the initial cleanup
request; the exact original unlink operation is UNCERTIFIED. A fresh owned-link
canary passed the simpler nonrecursive literal-path unlink with exit0, absent
link and unchanged target sentinel. No owned junction remains. This proves the
final resource state and that canary, not the rejected request's execution.
After review-only terminal-blank-line normalization, fresh9 tests and actual
export/occupied/hash gates pass. AST comparison proves both reused junction
guards run before any changed projection code; path/reader/writer/pin owners
are unchanged. No Git whitespace check was disabled.
