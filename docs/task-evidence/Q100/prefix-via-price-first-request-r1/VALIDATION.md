# Validation and source boundary

Source parent3eb2a701eedcaf3e5b717ac7a3e6712510074f3a. PLAN571a288d8252ead69c5df528673d0c0cb8d2c27e2f653432aeb121969b86c531;
PROTOCOL429feb12df57037ecdb204b9f82e52a0e21e296faf8c0933486b995e1e7aa740.
Actual22-input manifesta4786afdca13bcc7c8890be06faaed1bf4e3eeab68ff0608b8cd58f660bbf1d7.
Baseline1084-row manifest8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab.
Final derived qualification SHA256b4bb35cfbe2f6452f416c7cd9b7e283490a4fc8b42820817154a9c2e55a1b57e.

## Actual commands and results

The selected PS5 executable ran `run-first-request.ps1` through the unchanged
VerifierIsolation bounded process owner, with explicit mandatory arguments.
Sequential profile/kind pairs were BASE/TINY_CANARY, BASE/AUTHORED,
RAMP/AUTHORED. Each run used a unique external output and native scratch root.
The adapter receives KIND UUID INPUT_SHA WIRE_SHA EMPTY_ABSOLUTE_DATA_DIR.
The narrow reader CLI is `read_first_request.py RUN_ROOT INPUT_PINS INPUT_SHA PROFILE KIND`,
with TINY_NATIVE normalized for its first case. All three return
PASS_ONE_REQUEST_DIAGNOSTIC_ONLY; all managed children terminate and scratch is removed.

| Command | Work seconds | Native wall seconds | Cleanup upper seconds | Outer seconds |
|---|---:|---:|---:|---:|
| BASE/TINY | 5.4702402 | 5.6461214 | 38.4641248 | 88.2022481 |
| BASE/AUTHORED | 13.2428076 | 13.3964692 | 38.5489685 | 94.6014689 |
| RAMP/AUTHORED | 13.2458189 | 13.4223041 | 38.9026820 | 94.9598508 |

Work excludes only the actual returned ArmResult cleanup. Host cleanup upper is
nativeWall minus wrapperWork plus post-source/scratch work; it includes context
cleanup and residual transport/overhead conservatively. Adding the separately
measured overflow-negative wall yields38.6086069/38.6858216/39.0366063s, also<120.
Actual ArmResult cleanup is0.2673/1.3883/1.2900ms. Transport wall is not operation CPU.
Pre-output exact wrapper measurements and post-output deadline checks are separate;
an exact final-close timestamp is NOT_EXPORTED. Child exit0 is required.

Maximum full old groups are0.8057327/1.2825483/1.2964624s; preparation/post groups
also remain below5s. Combined groups55/103/106 stay below640. Work90s, P07 slice32768,
total expansions1000000, receipt8MiB/line64KiB, holes48/transitions4, cleanup120s,
compile60s, native480s and outer1800s remain unchanged. RAMP reaches the existing
search cap; it does not complete the search comparison or qualify a layout.

## Focused controls

- PASS: final selected Python `-B -m unittest -v test_read_first_request`;
  ten methods, nine PASS and one explicitly NOT_RUN optional real reparse fixture.
  Elapsed0.004s. Strict duplicates/types/encoding, descriptor/entry/cleanup, process
  identity, ordinary file size and the exact three-field normalization are covered.
- PASS: four final selected PS5 CLI negatives: wrong input SHA, repository context,
  source hash and wrapper identity. Each exits1 with termination proof before
  output creation or a compiler/native child. Their earlier input-r1 controls
  remain preserved; final input-r2 controls are authoritative.
- PASS: nine actual selected-reader CLI controls: one archived positive plus eight
  negatives for truncated receipt, descriptor hash, cleanup flag, native arguments,
  compiler start ticks, wrapper groups, capture byte count and targeted missing EOF.
  The targeted EOF case rebinds byte count and reaches TRAILER_LINE_CLOSURE.
- PASS: three real selected-producer combined stdout/stderr8MiB overflow negatives,
  each rejects an oversized single write before stdout, emits the marker, exits
  nonzero and terminates. No independently capped OS pipe is claimed.
- PASS: each actual1077-input compiler source list, exact three copied bridges,
  all1084 baseline rows and22 extras, selected binaries, actual intents/argv/PIDs/
  exact start ticks, descriptor/receipt pins and complete EOF are checked. Source
  audit runs before compile, before native and after native. Closing input hashes
  and nine public byte copies are independently reconciled again during derivation.

Final wrapper3ad73a67e37d140d28fd394bd511406e1b336f7044c75f343d649386d100a6f8;
readere27bfeab6d41ac32cbf5bcd1dd87c4a04186a833da21d2e1a68286f2a7310497;
testc60bf4aa6e3d07ecb34c8df2981191873a9609267c70df83dd1e4c84cb7c2e8c.
The immutable router/support overlay hashes and all delivered file pins are in
qualification.json. Original production router759831c9 remains unchanged.

Root reviewed the integrated source. Optional independent host review was static
only; its wrapper-identity finding was fixed before native work. Wrapper reporting/
timing findings were also fixed before compilation. No independent final runtime
review is claimed. Native runs pass on their first Phase2 attempts. Production
GWT/Player/functional regression gates are NOT_RUN; production sources and visible
flows were not changed, and no such qualification is renewed.
