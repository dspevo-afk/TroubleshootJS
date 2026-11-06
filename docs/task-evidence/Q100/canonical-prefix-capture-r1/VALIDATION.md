# Final validation

Final input manifest SHA
`ca06e1ab3c1a7bcb11dab90cba7b64dc9d95b031b09d76678f408d9fa0f0f7e0`.
1084 current/historical source rows, exactly two allowed native-test deltas,
24 extra inputs, unchanged850 production files. Native commands use the selected
Windows PowerShell5.1, JDK8 and VerifierIsolation implementation, with explicit
1077-source compiler lists, exact argument/bridge/binary hashes and PID/start
bindings. Root separately binds each outer process to its host launch record.

| Final gate | Status | Work seconds | Native wall seconds | Conservative cleanup seconds | Including overflow negative seconds | Outer wall seconds |
|---|---|---:|---:|---:|---:|---:|
| CANARY4 | PASS_CANARY_ONLY | 1.7358782 | 1.9699138 | 39.2118986 | 39.3712190 | 83.0571937 |
| CONTROL3 | PASS_CONTROL_ONLY | 13.2991908 | 13.4401034 | 39.0723681 | 39.2166560 | 95.2117871 |
| CAPTURE2 | PASS_CAPTURE_DIAGNOSIS_ONLY | 13.8912005 | 14.0356442 | 38.5320099 | 38.6845231 | 94.9721865 |
| MISSING_TARGET1 | PASS_EXPECTED_MISSING_TARGET | 14.4435212 | 14.6122587 | 49.0963226 | 49.2515799 | 107.9940603 |

Work excludes only actual ArmResult cleanup; canary uses its measured work clock.
Cleanup includes native residual and post-source/scratch work, separately from
operation time. Every native/compile/auditor child terminated; successful scratch
was removed. Missing-target native exit1 is required, not a passing route. All
four actual producer8MiB overflow negatives pass. The OS-pipe ReadToEndAsync path
is not claimed to have an independent buffer cap.

The generic command form is selected PS5 `-NoProfile -File run-native.ps1`
with explicit Mode, RepositoryRoot, BaseManifestPath/Sha256, InputPinsPath/Sha256,
OutputRoot, JavaHome and PythonExe. Then selected Python runs
`-B read_capture.py RUN_ROOT PINS_PATH PINS_SHA MODE`. The external process
receipts preserve exact local arguments and source manifests. The reader invokes
only the unchanged operation1 validator; the five-arm study is not qualified.

CANARY4 runs849 maintained fixed-prefix assertions plus15 adapter guards,
including the frozen footer literal. CONTROL3 reproduces P2's canonical receipt
with its original normalizations. CAPTURE2 preserves all routing semantics and
accounts611 added P07 steps; maximum owner group is1.2839592s, serialization is
also charged and bounded. Groups254+3 remain below640. The index/sidecar/op1
aggregate5596928B is below8388608B. The missing target offers2 lineages but selects
and captures0, releases owners/context, and leaves no index/sidecar.

PASS22 final literal methods in0.014s using selected Python3.13:
`-B -m unittest -v test_read_capture test_capture_controls test_zero_capacity`.
Tests cover chunk framing/strict types/duplicates/EOF/hash/Base64, parsed payloads,
ordinal identity, exact routing parity versus separately measured work, cleanup,
directed edge/layer semantics and zero-capacity VIA exclusion.

PASS four actual selected PS5 host negatives: input hash, context, self pin and
source row. All reject before compiler/native creation. PASS seven actual selected
Python reader CLI negatives: wrong transport source, missing footer, wrong lineage,
wrong placement, chunk offset, boolean selection and unproven cleanup. The EOF
and payload cases reseal outer hashes/lengths so the intended deeper check is
reached. These cloned records are explicitly mutated rejection fixtures, never
passing native-run evidence. The positive actual capture and expected missing
target are separately validated.

PASS independent `-B audit_zero_capacity.py RUN_ROOT PINS_PATH PINS_SHA` in
50.106079s. It first revalidates actual capture/source/process/control evidence,
then runs a bounded directed planar BFS independent of the minimum-VIA search.
It visits12 states, examines23 edges and no vertical transitions, and exhausts
without a goal. Both real start faces are retained. The maintained minimum-VIA
oracle also exhausts its relaxed graph. This is fixed-graph connectivity only;
route/layout/electrical/Q100 acceptance flags remain false.

Production, electrical models, epochs, budgets and Player flow are unchanged.
Fresh GWT/visible Player/electrical/population gates are NOT APPLICABLE to this
native-only diagnostic; no such qualification is renewed. Whole candidate
envelope and playable100 remain NOT RUN/unqualified. Full30 is EXCLUDED by owner;
HARD/PSYCHOTIC remain unavailable/unread/unrun here. Earlier optional real reparse
check remains NOT_RUN. Root reviewed the integrated source and evidence; optional
agent reviews were bounded/source-only, with no independent runtime review.


Final selected source/input close also PASS in17.5424249s after all runtime and
rejection controls;1084 historical/current rows and24 extra inputs still match.
The delivered code/control/oracle copies match their tested raw byte hashes.
