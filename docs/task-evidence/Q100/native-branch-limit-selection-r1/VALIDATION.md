# Validation

PASS applies only to the native component boundary. All returned calls below
prove termination and absent owned process roots/logs. The process and wrapper
times are separate; isolated cleanup duration was not measured. Each compile or
suite has the unchanged 60000 ms limit.

| Phase | Result | Exit | Process ms | Wrapper total ms |
| --- | --- | --- | --- | --- |
| final-compile-call-r2 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 1159.2942 | 1417.0342 |
| native-canary-call-r2 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 674.738 | 931.851 |
| retained-terminal-call-r2 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 981.4384 | 1225.1398000000002 |
| retained-branch-call-r2 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 471.11850000000004 | 719.2857 |
| retained-relaxed-call-r2 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 904.0594000000001 | 1145.8037000000002 |
| public-unit-call-r1 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 453.3013 | 890.9411 |
| public-positive-call-r1 | PASS_BOUNDED_NATIVE_COMPONENT | 0 | 385.339 | 638.8786 |
| native-canary-call-r1 | FAIL_PRESERVED | 1 | 524.75 | 769.4477 |

The final three-source JDK8 compile consumes the derived owner, exact public
canary and retained terminal-only test. It produces 60 classes/300342 bytes;
46 owner classes/228706 bytes match the earlier owner-only probe byte for byte.
The sole compiler warning is the expected Java7 bootstrap-classpath warning.
The classpath places fresh classes first, then the pinned D R4 classes and GWT
2.7 user/dev jars. The sourcepath is an explicitly empty directory. This is not
a full1092-source overlay or GWT build.

Canary R2: eight case groups,587 assertions,16 fixtures,18 actual begin calls and
one detached copy. It checks literal comparator ties; stale identity rejection;
the ten scalar fields; full old/new terminal context equality; raw post-pop
queue/index images and expansion invariance; total-cap precedence and empty
queue; older stored h against a real completed relaxed field; immutable DTOs;
RuntimeException/Error identity, clearing/latching and fresh reuse; detached
mode/caller separation; actual successful and blocked small-route parity.
The cap counters and queue frames are explicitly injected generic frames.
The failed R1 run did not return a Session count. Retained suite Session counts
are unmeasured; no combined invocation count is inferred.

Retained terminal-only217421, branch30 and relaxed-bound2201 assertions pass.
The relaxed suite's two clone canaries are native contract evidence, not GWT
deadline or authored route qualification. No new authored original/control/arm
ran. The previously accepted original plus two arms remain immutable.

Eight focused Python tests and the real positive CLI pass from the final public
copy. Five earlier real selected-host negatives pass by expected exit2 rejection:
occupied output, bad source hash, bad helper hash, input junction and output
junction. Reuse is bounded to identical generator/helper/parent bytes; the final
positive regenerates identical owner and source-proof bytes. Both owned junction
identities were revalidated and each literal nonrecursive unlink returned0;
links are absent and targets unchanged. Earlier controls and receipts remain.

The early actual dependency audit authenticates1092 sources/4288 classes/
19644833 bytes. Final acceptance rehashes all4288 inherited class files, the
pinned manifests, selected fixture/retained-test sources and tools unchanged.
It does not rerun the inherited authored driver or population qualification.

Generator commands (use canonical absolute paths and an existing empty owned
OS-temp output; Python must always use -I -B):

```text
python -I -B <code>/test_branch_limit_selection_overlay.py --source <owner> --io-helper <cohort-exporter>
python -I -B <code>/generate_branch_limit_selection_overlay.py --source <owner> --io-helper <cohort-exporter> --output-dir <empty-output>
javac -source 7 -target 7 -encoding UTF-8 -classpath <fresh-classes>;<pinned-D-classes>;<gwt-user>;<gwt-dev> -sourcepath <empty-sourcepath> -d <fresh-classes> @<exact-three-source-list>
java -ea -cp <same-classpath> com.lushprojects.circuitjs1.client.BranchLimitSelectionCanary
java -ea -cp <same-classpath> com.lushprojects.circuitjs1.client.P07TerminalOnlyDiagnosticsContractTest
java -ea -cp <same-classpath> com.lushprojects.circuitjs1.client.P07BranchDiagnosticsContractTest
java -ea -cp <same-classpath> com.lushprojects.circuitjs1.client.P07RelaxedDistanceBoundContractTest
```

These are sanitized command templates, not standalone replay: pinned inherited
native classes/toolchain/raw process receipts remain external. The maintained
native runner has no owner-overlay selector; running it unchanged tests the
production owner instead. Do not treat that as overlay proof.

Production/GWT/visible Player gates: NOT APPLICABLE to this native-only change,
not renewed. Full30 EXCLUDED; HARD/PSYCHOTIC NOT RUN and unavailable. D consumer,
Meter/wire, authored capture, physical/access/runtime and playable100 adoption:
NOT RUN/NOT ACCEPTED. All existing routing/time/copy/group limits remain.
