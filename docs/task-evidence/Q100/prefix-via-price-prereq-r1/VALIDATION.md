# Phase1 validation

## Source boundary

Parent30b2a1cbbb02b39dbc275a82755902e61043b714; repository production router
759831c91345aedab9e78d8c83785019f7e59287a18f4f99f7af14876776c474 is unchanged.
Overlay router70cacaacc97cb3f01a1ad6221753e4d80a05479ba6663cd5dc6474a00dc4fb21;
readable patch d43cff5c3f7f06f4f27cfea5e3e63e9f0218537de1a1901babba25fb01512eb7.
The patch adds a final branch price, normal/copy initialization and observations,
and uses that price at the flip and relaxed-distance-bound owners. No legality,
seed, ordering, production epoch or budget changes.

QR denotes the external prefix-via-ramp-r1 task evidence. Accepted overlays-r5
manifest3eae9f5b82cd7a9b8e3c4c478e93baa4517550d1ae72046ca9c0ed5076548a7e;
input-pins-r4 dd1247bde28ba4ea87373e90422e5ae3e1a9e602c9b7f512c15dbcfe961e7a1d.
It binds19 extras and accepted1084-row source manifest (1080 BASE_SOURCE),
8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab.
All baseline/extra hashes close before compilation, before native execution and
after exit. Copied bridges are hashed after copying, before native execution and
after exit; source-list bytes also close unchanged. The frozen native bridge
manifest remains e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b.

## Executed commands

The selected pinned Python3.13/JDK8/PS5 and unchanged VerifierIsolation were used.
Exact local invocations are retained externally; sanitized command shapes:

```text
python -B source/prepare_overlay.py REPOSITORY NEW_OVERLAY_ROOT
python -B make_inputs.py REPOSITORY BASE_MANIFEST BRIDGE_ROOT OVERLAY_ROOT NEW_PINS
powershell5 -NoProfile -ExecutionPolicy Bypass -File run-canary.ps1 -Mode BASE ...
python -B read_canary.py BASE_RUN INPUT_PINS INPUT_SHA BASE
powershell5 -NoProfile -ExecutionPolicy Bypass -File run-canary.ps1 -Mode RAMP ...
python -B read_canary.py RAMP_RUN INPUT_PINS INPUT_SHA RAMP
python -B -m unittest -v test_read_canary.py
```

The argument-based input emitter reproduces accepted pin bytes/hash exactly.
The delivered source note is a sanitized documentation derivation (original
5950a013, publishedcd8a4ab6); its original absolute checkout spelling is excluded.
Replaying the delivered builder with that note reproduces every compiler-source
byte and the readable patch exactly. Only note provenance in the emitted
manifest changes (replayc8b37644); no new native run or broader replay promise is
inferred from that metadata-only difference.
The host compiles1076 explicit sources; the old branch-study WRITER and unrun
authored STUDY are excluded. Compilation60s, canary90s child limit, native
ceiling480s, cleanup120s and outer1800s are retained or tightened. Every compiler/
native child exits with termination proven. Successful scratch is removed after
creation-identity/reparse/containment checks.

## Final results

| Gate | BASE3 | RAMP1 |
|---|---:|---:|
| Native assertions |221 PASS|221 PASS|
| Real prefix H/M |2/48|2/48|
| Actual source/normal copy price |60/60|62/62|
| Explicit raised copy |120|120|
| Actual zero/saturated guard |PASS/PASS|PASS/PASS|
| Measured canary work |0.6662132s|0.365132s|
| Native process wall |0.7780066s|0.4966348s|
| Post-source/scratch interval |37.8952622s|37.7994707s|
| Cleanup conservative upper |38.8305816s|38.4352868s|
| Outer elapsed |81.0105672s|80.5685346s|
| Strict independent reading |PASS|PASS|

Each actual output-limit negative attempts one8MiB+1 chunk through the selected
producer, exits nonzero with termination proven, emits no stdout and retains the
capture-limit marker on bounded stderr. Normal stdout has one exact schema
object and EOF. Source restoration, actual compiler/native argv/PID/start
binding and source-list/bridge hashes are independently checked.

PASS final ten Python methods; ten real selected CLI controls (one positive and
nine negatives: missing copy proof, wrong price, false authored claim, cleanup
over-limit, different compiler, changed source list, missing cap failure, extra
output, duplicate JSON); and three real PS5 source/hash negatives rejected before
creating output/children. Optional junction negative is NOT_RUN as recorded in
[failures](FAILURES.md).

Root owns integrated review and acceptance. Independent source reviews found
issues and supplied no runtime proof; root resolved their findings through the
actual gates. No independent final runtime reviewer is claimed.

Phase2 authored comparison, qualification-controller, GWT/Player/electrical/
normal3/full30: NOT_RUN (full30 owner-excluded). No changed player flow exists.
Production ownership is unchanged; no app architecture/build change is needed
for this external-only native prerequisite. Frozen scope limits remain binding.
