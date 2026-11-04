# Preserved failures and corrections

This packet is in progress. These failures are not qualification passes. Raw
artifacts and source archives remain in the task-owned OS-temp `adrive-graph-r1`
evidence directory. No earlier evidence was overwritten.

## Focused synthetic reader fixtures

`graph-python-r1` failed: 33 methods, 30 passed, one failure, one error and one
Windows file-symlink privilege skip; exit 1, termination proven, 0.7518993 s
including bounded child teardown (separate cleanup time not exported).
The vertical width-3 segment fixture expected horizontal cells `[0,1,2]` instead
of the independently derived vertical cells `[0,3]`. A positive framing fixture
omitted the required space after the graph marker. Two fixture lines were fixed;
the reader remained SHA `6782bfb539de2511bb9d466a37751cae01bfce4c349c7a7dcf7b62723a71285e`.
`graph-python-r2` passed 32 methods with the same one named privilege skip in
0.422369 s. This validates only that earlier reader, not subsequent source edits.

## Source review before the first native launch

Root review found cumulative group scan deltas incorrectly reused as per-call
deltas, an unqualified prefix reference, and an unassigned graph start-cell
field. The implementation was corrected before the first native launch.
These are source-review findings, not failing runtime controls.

An independent source-only reader review found that generic pad-constructor
acceptance admitted surface-pad states the actual routing Session rejects.
It also identified missing exclusion of the current start from reached PTH
terminals and missing mandatory root/reached terminal goal checks. Canonical
wire revision R10 tightens those existing owner preconditions; payload shape,
budgets and production model remain unchanged. Reader correction and its fresh
validation are pending at this checkpoint. The earlier reader PASS is not reused
for this changed behavior.

## Native compile failures

`query-graph-r1` failed before native suite execution or receipt creation.
Final-source manifest was
`ce64451b91aceb307506f18cd715578306cdfe2f9b429fb6faa2a663d157f4b4`.
The selected JDK8 compiler reported a missing closing parenthesis in the report
envelope call and an end-of-file parse error. The bounded PS5 child exited 2,
termination proven, 5.4382143 s including child teardown; the outer native gate
failed with `NATIVE_RECEIPT_MISSING`. Scratch cleanup was reported; separate
cleanup elapsed was not exported. The missing parenthesis was corrected.

`query-graph-r2` failed before native suite execution or receipt creation.
Manifest was `927605bf3a8a1c9a6fbe8013bd0d4c6333fa47763d4143412c98a9389f50b0d3`.
The compiler still reported end-of-file while parsing. The bounded PS5 child
exited 2, termination proven, 4.9332694 s including teardown; the outer gate
again failed with `NATIVE_RECEIPT_MISSING`. Scratch cleanup was reported; its
separate elapsed time was not exported. A missing closing brace of
`GraphCaptureBuilder` was located and corrected. `query-graph-r3` uses a fresh
namespace and manifest
`7857c6a7cb1f38289785c0ece2162b64d332cc31926c2fd4e65c6b4e03ee22c5`.

The first two failures are implementation compile failures, not host environment
failures or evidence about graph feasibility. No graph, routing, electrical,
physical, playable100 or publication success follows from these runs.

`query-graph-r3` compiled successfully, then failed at actual observer-task
initialization. The source assertion incorrectly required the constructor itself
to carry `private`; production declares a private task class with a package
constructor. Source inspection confirmed the real two-parameter reflective
signature (implicit enclosing branch and lineage). The assertion now requires
the private declaring class and actual constructor modifier/signature.
The failed operation recorded 403 controller units, 18.019 s operation time,
cleanup PASS in 1 ms and released live references; no task scan or graph began.
The bounded PS5 child exited 2 with termination proven in 49.3814106 s including
teardown. The native gate failed `NATIVE_RECEIPT_MISSING`; separate child cleanup
elapsed was not exported. This is a reflective contract error, not graph
feasibility evidence. Fresh `query-graph-r4` uses manifest
`fbb9befebaa76e9282e0477dac2d59498fce72a5bf562fe9c2ca8db483897f62`.

## Actual graph work limit

`query-graph-r4` failed at the unchanged 640-controller-unit limit, during the
actual task's ordinary-edge scan (stage 2). Operation elapsed was 26.491 s,
maximum unit elapsed 1.258 s, cleanup PASS in 1 ms, live references cleared and
the original source preserved. The bounded PS5 child exited 2 with termination
proven in 57.2588219 s including teardown; separate child cleanup elapsed was
not exported. No complete graph or qualified receipt was emitted.

The progress snapshot records 445 actual internal task calls, 2,670,740 copied
entries, 2,671,732 compared entries, 295,404 goal probes, 833,275 ordinary-edge
probes and zero VIA probes. Graph serialization and encoding had not started.
The first task initialization occupied controller unit 403. At the limit,
the remaining ordinary-edge scan, VIA scan, final live-state comparisons,
provenance, encoding, hashes, guard call and report had not completed. No cap
was raised and no assertion was waived. The capture remains INCOMPLETE; this
failure supplies no conclusion about route feasibility or board impossibility.

Root is investigating a source-backed capture prerequisite under the original
budgets before any further implementation or full acceptance matrix. The frozen
single-copy-quantum grouping and full-task observation design cannot simply be
relabelled as a passing graph capture.

## R11 report phase binding

`query-graph-r5` completed the actual task and packed graph, then failed the
final report reconciliation. The immutable capture report retained the
pre-guard continuation count, 1,835,182; the subsequent single unchanged guard
step correctly increased the measured total to 1,835,183. Report code compared
the earlier capture count directly with that later count. The fix names the
capture count separately, requires it to equal the capture-start count, requires
exactly one later guard step and binds wire work/final fields to the actual
post-guard counter. No assertion, operation budget or routing rule was relaxed.

The failed r5 manifest is
`6be0a7281384ca9591043afc24d06505102398cc5cd5bb117399bc966c4a8a9a`.
It recorded 620 units before the report, 621 after its charged report unit,
27.671 s operation snapshot, cleanup PASS in 1 ms and released live references.
Task work completed with 611 calls, 2,670,740 copied entries, 4,007,598 compared
entries, 295,404 goal probes, 1,178,468 edge probes and 147,702 VIA probes.
Packed graph size was 738,743 bytes. The bounded PS5 child exited 2 with
termination proven in 58.9992222 s including teardown. Separate child cleanup
elapsed was not exported. No qualified receipt was emitted; r5 remains FAIL.

Fresh `query-graph-r6`, manifest
`0c43547bd6324579d7e31bab9d284dc62e49741c9b47cba4e34488dbe2c2f8ec`,
passes the selected native host with exit 0, proven termination, before/after
input audit and complete four-record receipt in 59.052614 s including native
compilation and bounded child teardown. Separate host cleanup elapsed is not
exported. This is native-process evidence only; strict independent graph
consumer acceptance and the full final matrix remain pending at this checkpoint.

## Reader integration and source count corrections

`graph-reader-r1` rejected the r6 artifacts with exit 2, termination proven,
2.5569188 s including bounded child teardown. A separate diagnostic exposed
`KeyError: holeCount`: the graph adapter used guard-style names for the base
remainder source snapshot, whose actual fields are `holes` and `traces`. The
reader now uses the frozen base shape and rejects aliases explicitly; a new
literal fixture covers preservation, wrong counts and wrong field names.

`graph-reader-r2` then correctly rejected `HOLE_KIND_COUNT_MISMATCH`, exit 2,
termination proven, 2.6791452 s. The producer collected the actual per-kind array
but passed three unset duplicate builder fields into its immutable report. Those
duplicates were removed; report counts now come directly from the authoritative
array. Native r6 process PASS is therefore not accepted graph qualification.

A synthetic, in-memory diagnostic changed only those counts in a copy of the
r6 graph and reached `POLICY_KEYS_INVALID`. No native file was changed and this
was never native acceptance. The reader had misspelled the wire's
`viaMaskExcludesEarlierPathViaSpacing` field. It now matches the frozen wire and
producer. A separate literal policy fixture rejects the misspelled alias and a
false exclusion flag. The current new suite has 35 methods, with 34 passing and
one named Windows file-symlink privilege skip; raw earlier 33/34-method evidence
remains preserved for its own source.

R7 native transport passed in 58.7152341 s; the corrected strict consumer r3
passed in 4.8633241 s. Root reviewed the actual complete graph, ledger, source
and cleanup. This remains evidence for source manifest `771dcfa7...`, rather
than final-source acceptance after the following memory correction.

## Final source review and bounded streaming correction

The independent source review found a blocker after that runtime pass: the
producer retained the complete `graphChars` prefix alongside its immutable byte
chunks. Root had allowed this duplication; it conflicted with R11's streaming
contract. The correction removes that field and uses one short-lived, at most
32,768-character chunk, then encodes/hashes that same chunk and retains only the
bytes. Scalar counters replace retained character state. Exact-fence completion
checks the cursor's actual empty stack/pending state without lookahead.

Native literal tests now exercise chunk sizes 1/7/32768, an exact 32768-byte
object, no empty completion group and rejection of mask 16. They run through
the existing focused P07 transport contract. A scoped source re-review resolved
the blocker and found no direct regression; that reviewer ran no tests.

The prior-source native 2/8/33 passes and six native query results remain attached
to `771dcfa7...`. They are not final-source passes. Root cancelled only the
verified task sequence parent (PID 34848, exact creation time, executable,
command line and source hash), leaving its current bounded child to finish.
The gate child 26076 then exited; its nested child 5188 exited 0 with termination
proven in 41.5787599 s and reported cleanup. No successor guard namespace was
created. Source mutation began only after that gate was absent. Ownership and
cancellation records remain in `source-revision-stop-r1`; the aggregate sequence
is CANCELLED, not PASS. No unrelated process was terminated.

Final streaming-source manifest is
`f27c588a5690e666a934c53d120c52a6911b46f5ba0db72ce8585decbbdb428c`.
Fresh native r8 passes in 57.0909191 s and strict consumer r4 in 4.8650202 s.
Root review r2 verifies the entire packed graph is byte-identical to the earlier
semantic witness: 738743 bytes, SHA
`0c73ce21580a9c4e2508a251425907da01d11c200483409870181a5d8d30ef11`.
The operation uses 621 units, maximum unit 1253587100 ns, total 26729128900 ns,
and cleanup PASS in 1 ms. Fresh final native and compiled gates are still
required; no budget or independent expectation was waived.

## Host harness corrections

The first reader-host syntax invocation rejected a changed source pin before
parsing the graph host. A late worker edit had removed outer native-artifact
snapshots and reduced verdict checks. Root restored artifact snapshots and
explicit source/verdict bindings, then passed selected PS5 syntax for four hosts
and Python AST for three preparers. Native definition preparation now requires
the actual exact v5 `pinsBytes` key. No rejected host version was executed.

The first regression preparation supplied the source-freeze manifest path,
while its contract requires the selected prerequisite's manifest path. It
rejected early; root also invoked the dependent gate without a definition,
which rejected before launch. Both are harness errors, not native test outcomes.
The corrected call uses `prereq-r1/inputs.json` (and final reruns use prereq-r2).
No checks were weakened and neither early invocation is counted as PASS.
## Controlled archive preparation: slow scanner, corrected before copying

Root's first archive-only helper used an unbounded email regular expression on
long packed-mask strings. It made no artifact copy and produced no acceptance
result. Root revalidated the exact helper Python PID16404, creation time,
executable, parent and unique command before stopping it; unified25955 exited1.
The corrected R2 helper uses a linear marker scan, verifies all four native
artifact SHA-256 values, and roundtrips the compressed stdout byte-exactly.
R2 passed. This is archive preparation only; native/reader artifacts were not
changed. Raw R1 helper and the ownership observation remain in QW.

## Final-source Player privacy cleanup failure

Final native prerequisite2/focused8/native33 and all seven predecessor
native/independent-reader pairs pass on F27. The actual Player JDK8/GWT build
passes in91.8376966s;357 output files were archived before any Developer build.
The first Q100Player route and seven-JavaScript developer-marker scan PASS, but
the overall gate is FAIL/exit2, termination proven,44.1573965s. Cleanup failed
in11.673s during protectedHelperCensus: native and direct CIM child-set counts
disagreed. The verifier retained the browser/lease/profile rather than using
uncertain ownership. Bound browserport56677/root12292 and its exact run manifest
are preserved; preview56668 has its own recorded completed cleanup.

This is not a Player privacy acceptance. Root is using the unchanged maintained
retained-browser recovery against that exact manifest; its result is separate
and cannot promote the original failed gate. Any fresh gate uses a new namespace
and the same current Player build with audited unchanged inputs. No source fix,
budget increase or relaxed process identity oracle has been made.

The unchanged maintained recovery subsequently PASSed in47.228326s total;
the exact root exited, both56677/56668 had no listeners and the durable manifest
recorded cleanup complete/errors[]. This does not change the original FAIL.
Saved native/CIM sets are absent, so the original count mismatch's cause remains
unproven; source-only review found the count comparison deliberately fails closed.
Fresh `q100-player-adrive-graph-r2` on the same audited Player build PASSes in
75.981056s, cleanup44.492s/120s and reported BrowserShutdown8.720s/15s, with no
cleanup errors. The final compiled reviewer R2 changes only two privacy-namespace
references; all identity/absence/budget oracles remain unchanged. Both runs,
recovery snapshots and original modulefb07 remain preserved.

## Normal3 preflight receipt paths

The first final Normal3 sequence stopped during ValidateInputs in0.6511787s:
four expected Player/Developer receipt-path literals still used the preceding
task's build names. It rejected the actual current after.json receipts before
creating the runtime root, exporting candidates or allocating a host. This is
FAIL_STOPPED, not population proof. Root preserved the original wrapper sources
and failed sequence, corrected exactly those four literals, and moved the fresh
sequence to `normal3-final-sequence-r2`. Cleanup helper/control references follow
that fresh namespace; their identity/absence assertions are unchanged.

Corrected24-source package pins are
`60417ea846cca67f153fdc2ddce67e04e06fa662c1e1306ba377139938ccc779`;
18 immutable source snapshots and all9 SDK files still match. Selected PS5
syntax checks for all four changed hosts and the new source audit PASS. The
actual corrected sequence is running; no runtime PASS is inferred from these
source checks. Production/native1079 and Python44 inputs did not change.

Final closure: corrected sequenceR2 passes all six phases in369.4309782s;
the independent result accepts exactly the declared3development roots, each on
its first attempt. Actual host cleanup PASS1.4597139s, no owned survivors;
wrong-receipt rejection and exact profile deletion PASS1.744s, with raw evidence
and marker retained and no process terminated. Final source audit PASS covers
native1079/Python44/SDK9/Normal3selected24/supplementalhosts28 and selected
executables. All original failed records remain FAIL; none were overwritten or
used as current acceptance. No feasibility search, source treatment, budget
increase, full30 or playable100 qualification occurred.

## Publication inspection

Initial staging rejected new packet paths outside the selected sparse checkout;
root used `git add --sparse` for the exact intended files, without changing
sparsity settings. Cached whitespace checking found one new blank EOF line in
this file; it was removed and the same check passed. The first supplemental
privacy scanner falsely recognized keyboard `arrows/Home/Escape` and an `http`
URL as absolute paths. Root inspected its two matches and corrected token
boundaries; runtime source/oracles were untouched. Raw captures remain in
task-owned scratch under the repository ignore guidance; only controlled
summary metadata is explicitly staged alongside code/Markdown.
