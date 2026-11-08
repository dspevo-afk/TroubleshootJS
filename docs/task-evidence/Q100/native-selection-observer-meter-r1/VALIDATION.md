# Validation

All compile/suite/host calls keep the60000ms cap. Every returned call below
proves termination and absent owned process roots/logs. Process and wrapper
times are separate; isolated cleanup duration was not measured. Expected
rejections are not ordinary exit0 PASS. RootCall maps every nonzero child exit
to wrapper exit1; the recorded child exit remains authoritative.

| Phase | Result | Child exit | Process ms | Wrapper total ms |
| --- | --- | --- | --- | --- |
| final-compile-call-r2 | PASS_FINAL_BOUNDED_COMPONENT | 0 | 1257.7298 | 1510.1470000000002 |
| native-canary-call-r2 | PASS_FINAL_BOUNDED_COMPONENT | 0 | 643.6398 | 904.8526 |
| main-denial-call-r2 | PASS_EXPECTED_ENTRY_REJECTION_EXIT1 | 1 | 348.1599 | 605.6354 |
| generator-unit-call-r3 | 12_PASS_1_SYMLINK_SKIP_COMPLEMENTED_BY4_REAL_JUNCTION_CASES | 0 | 473.69980000000004 | 732.498 |
| generator-positive-call-r3 | PASS_FINAL_BOUNDED_COMPONENT | 0 | 385.8619 | 640.3053 |
| public-unit-call-r1 | 12_PASS_1_SYMLINK_SKIP_COMPLEMENTED_BY4_REAL_JUNCTION_CASES | 0 | 556.2173 | 819.8648000000001 |
| public-positive-call-r1 | PASS_FINAL_BOUNDED_COMPONENT | 0 | 386.4361 | 649.0826000000001 |
| generator-occupied-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 370.4314 | 620.6812 |
| generator-bad-study-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 373.1369 | 627.2090000000001 |
| generator-bad-base-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 450.97560000000004 | 699.8844 |
| generator-bad-helper-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 453.41360000000003 | 702.2420000000001 |
| generator-study-junction-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 540.5862000000001 | 788.8275 |
| generator-base-junction-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 445.88820000000004 | 694.4859 |
| generator-helper-junction-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 367.0536 | 615.4695 |
| generator-output-junction-call-r2 | PASS_EXPECTED_GUARD_REJECTION_EXIT2 | 2 | 366.9768 | 613.0104 |

The actual final JDK8 source7/target7/UTF8 build consumes three sources: qualified
P native owner7bf4ab57, final D overlayd6485b0b and independent canary2203b4d1.
It produces220classes/656516B. Its46owner classes/228706B reproduce the accepted
P classes exactly. All145old D Study class names are shadowed by146fresh ones,
including DetachedHead; no old nested class fallback is needed. Empty sourcepath,
fresh classes first, pinned inherited D classes and GWT user/dev jars are explicit.
This is not a full1092-source overlay build or production GWT build.

Final canary15245 assertions/nine groups verifies literal ordinary90checks/
159units/33copies/onechunk; selected102checks/192units/43copies/twochunks; FOUND
91checks/160units. The head adds20read/copy units, twelve additional predicates
and one borrowed-head release unit compared with ordinary callbacks. Successful
selection predicate counts are2absent/14present. Stale snapshot+head rejection
uses3units on the separate Meter and changes no original state. These are
logical observer-operation credits, not primitive-instruction accounting.

Tests include actual shared-Meter positive, exact43-entry admission,42-entry and
intMAX-1 chunk rejection before either copy; malformed fields/presence; Java-int
priority wrap; immutable copies; first-failure finality; supported stale API;
RuntimeException/Error identity, mid-copy and post-commit release rollback; sink
guard restoration; actual P06 pass/cap/layer/minimum injected frames, raw queue/
index/context invariance and small-route/default/old-marker parity. Nine begins
and34head DTO copies belong only to the final canary invocation. Prior pre-fix
invocation counts remain separate. Retained suite Session counts are unmeasured.

Fresh three-test-source retained compilation produces30classes/107506B.
Terminal-only217421, branch30 and relaxed-bound2201 assertions pass on the final
owner overlay. The two relaxed clone canaries are native contract evidence,
not GWT deadlines, population or route qualification. Main220classes remain
byte-identical after those gates. The early actual adapter probe has14assertions
and no Session; it qualifies only the unchanged adapter path.

Python reports13declared cases:12PASS and1SKIP because os.symlink is unavailable
on this host. The skipped reparse case is complemented by actual final-source
study/base/helper/output junction negatives. Four other real CLI cases check
occupied output and bad study/base/helper hashes. All eight reject at the exact
expected code, before output mutation; sentinel and source targets remain intact.
All four owned junctions were identity-checked and removed by explicit literal
nonrecursive commands; lstat confirms absence. No environment protection or
budget was relaxed. The final public copy regenerates identical Java/proof bytes.

Final binary/tool audit rehashes4288inherited classes/19644833B and selected tools,
plus exact public/private code copies and unchanged production/D/WIRE/reader pins.
Prior1092-source build provenance is reused; no new full-source build is claimed.

Sanitized command templates; use canonical absolute paths and owned empty outputs:

```text
python -I -B <code>/test_selection_consumer_overlay.py --study <D-study> --base-generator <P-generator> --io-helper <cohort-exporter>
python -I -B <code>/generate_selection_consumer_overlay.py --study <D-study> --base-generator <P-generator> --io-helper <cohort-exporter> --output-dir <empty-output>
javac -source 7 -target 7 -encoding UTF-8 -classpath <fresh>;<pinned-D-classes>;<gwt-user>;<gwt-dev> -sourcepath <empty> -d <fresh> @<three-source-list>
java -ea -cp <same-classpath> com.lushprojects.circuitjs1.client.SelectionConsumerCanary
java -ea -cp <same-classpath> com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy FORBIDDEN_AUTHORED_MODE
```

The last command must reject immediately. Raw sources, toolchain, manifests,
classes and process receipts remain private; templates are not standalone replay.
No authored original/control/arm/retry, wire/reader run, GWT/Player/electrical,
physical/access/runtime-functional or population renewal occurred. Full30
EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; playable100zero/betaheld.
