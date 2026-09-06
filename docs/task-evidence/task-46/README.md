# Task 46 — versioned challenge replay and named streams

## Entry and bounded design (frozen before implementation)

Task 46 alone is authorized. Entry is the clean branch
`codex/task43p-final-recovery`, exactly reviewed HEAD
`134a0791b052d9a1928565e7b2318ac7284a357c`. Accepted Task 44 `10dcafa` and
Task 43P `a5e8efa` are ancestors. There is no intervening diff or pre-existing
tracked/untracked change. The prior Task 44/45 handoffs remain historical.

`ChallengeDescriptor` owns immutable requested identity: schema version, signed
64-bit root seed, generator ID/version, device-intent ID/version, difficulty
profile ID/version, the existing `PcbGeometryContractVersion`, and canonical
`GenerationConstraints`. It contains no runtime owner or random cursor. Schema
1 is a bounded ASCII, newline-delimited key/value encoding with a versioned
header, sorted keys, exact decimal seed text and versioned IDs (`id@version`).
Collections and constraint declarations are copied and canonically sorted;
duplicates, malformed/unknown fields and contradictory declarations reject.
Referenced identities can be described without being executable; replay resolves
them explicitly and rejects unsupported IDs/versions before generation.

`legacy-leaf@1` resolves the six existing `QuickPlayFamilyRegistry` family IDs
at intent version 1, `legacy-default@1`, geometry contract 3, and entirely
unspecified constraints. It calls `QuickPlayFamilyRegistry.generate(family, seed)`
directly, without Quick Play seed remapping or forced-fault overrides. Every
call creates a new `GeneratedBoardInstance`. Existing generation, fault/scenario
selection, geometry and normal admission retain their implementation and draw
order. Geometry-version drift rejects; other future leaf algorithm changes need
a new replay version and preservation or explicit rejection of old versions.

Named derivation version 1 hashes a length-framed ASCII tuple with FNV-1a-64:
offset `cbf29ce484222325`, prime `00000100000001b3`, XOR each unsigned byte then
multiply modulo 2^64. Each field is encoded as ASCII decimal byte length, `:`,
then its bytes. The tuple is exactly `tsj-named-seed`, `1`, root seed decimal,
device-intent ID, intent version decimal, scope (`device`/`block`), block key
(`-` for device), concern token, positive concern revision decimal, semantic key.
IDs reuse Task 44's ASCII grammar. Neither inventory, descriptor encoding,
generator/profile metadata, constraints nor geometry is hashed into every stream.
Changing intent identity or derivation version invalidates all its named seeds;
changing one concern revision affects only that tuple. Concern revisions name
logic namespaces; they do not claim that a future generator is implemented.

Concerns are `topology`, `block`, `values`, `support`, `fault`, `scenario`,
`placement`, `routing`, `presentation`. `block` and `values` require block scope;
the other concerns support device and block scope. Semantic keys isolate such
choices as R1 versus R2 values. Streams reopen at their beginning. Each fresh
local cursor uses SplitMix64: add `9e3779b97f4a7c15`, xor-shift 30/multiply
`bf58476d1ce4e5b9`, xor-shift 27/multiply `94d049bb133111eb`, xor-shift 31.
All arithmetic is Java `long` modulo 2^64 with unsigned right shifts, including
GWT's emulated longs. Bounded selection uses nonnegative 63-bit rejection
sampling, then indexes lexically sorted unique candidate IDs; empty or duplicate
sets reject. Changed eligibility can change selection despite unchanged seeds.
All new streams are **reserved, not consumed by legacy-leaf@1**.

Constraints are requests, separate from Task 41 measured evidence and eventual
admission. Version 1 has inclusive nonnegative signed-int count ranges for
blocks, components, domains, plausible physical owners, diagnostic depth,
input/power transitions, isolation actions, temporal samples and purposeful
auxiliaries; tri-state parallel ambiguity/temporal evidence requirements; and
allowed instrument IDs `DC_VOLTAGE`, `RESISTANCE`, `CONTINUITY`, `DIODE`.
Unspecified is explicit; exact zero and an explicitly empty instrument set are
specified requests. Structural bounds are encoding bounds, not calibrated
difficulty thresholds. Diagnostic depth means Task 41's trace-derived distinct
meter/transition/isolation/temporal count, never declared plan depth. Inverted
ranges and mathematically inconsistent temporal/depth requests reject. Every
specified constraint is reserved for later consumers and rejected by legacy
replay. Complaint/scoring/layout-complexity calibration and four presets remain
later work; no invented metric is used to admit a challenge.

The runtime replay verifier and pure corpus execute behind `tsjDebug` plus an
explicit Task 46 verification request. Diagnostics publish only there, carry
canonical seed strings, and do not draw random values. Normal player DOM must
contain no Task 46 descriptor/seed/rejection output, even when the verification
parameter is supplied without debug mode. No new player flow is introduced.

## Closed acceptance set

| Gate / production path | Verifier and independent oracle | Fixtures / toolchain / dependencies |
| --- | --- | --- |
| A: descriptor and constraints | Actual-source contract suite: canonical round trip, copies, permutations, duplicate/malformed IDs, unsupported versions, contradictory/unspecified/empty declarations | JDK8; zero, negatives, 32-bit boundaries, 2^53 neighbors, high-bit values, both long endpoints |
| B: derivation, fresh streams and selection | Committed literal vectors independently calculated by a standalone integer reference; scoped tuple boundaries, draw isolation, optional insert/delete/permutation, diagnostics purity, candidate permutation/duplicate/empty/changed eligibility | Pure Java/GWT-compatible corpus plus independent reference; two accepted in-memory Task 44 block fixtures; no composed runtime |
| C: actual compiled-browser parity | Execute the same pure corpus in JDK8 and compiled GWT; compare canonical output bytes read from the developer route | Final OBF build; actual in-app Browser; seed transport remains text |
| D: real replay | Compare unchanged direct leaf generation with parsed-descriptor adapter; values, logical IDs/mappings, selected fault/scenario, geometry version/fingerprint, fresh board/runtime/parts, real healthy/faulted lifecycle | All six families: 0/2/3 for LED/diode/parallel/RC, 0/1/2 for NPN/NMOS, plus LED 4; 19 cases in bounded sequential browser runs |
| D: affected regression and privacy | Existing challenge, layout and Task 41 routes; normal-player initial/readiness and no debug output with ungated Task 46 parameter | LED challenge seed 3; existing all-family layout route; existing Task 41 corpus; normal LED seed 3 and RC seed 0 in Browser |
| E: accepted contracts and production | `scripts/verify-block-contracts.ps1`; final `scripts/build.ps1 -Target Compile -Style OBF` | Selected `.tools/jdk8-download/jdk8u502-b07`; Task44/45/46 actual source; all five GWT permutations |
| F: independent review and closure | Fresh Luna MAX read-only integrated review, affected delta reviews if repaired, evidence/dependency audit, intended diff/index/whitespace, normal push and exact remote SHA, authorized post-push Gmail | Reviewer authors neither implementation nor oracle; one expensive build/browser workload; existing ownership/cleanup helpers unchanged |

The full historical Gate B/43P and 55-route Task 45 campaign is not repeated.
Reuse is limited to unchanged solver, leaf generators, graph/mutation/instrument
owners, renderer/geometry providers, snapshot/runtime lanes and process trust
implementation after an explicit final dependency diff audit. Required Task 46
proofs above run freshly. Task 47 remains unstarted and the Composition Entry
Gate is not claimed by this milestone.

## Acceptance results

| Gate | Result / evidence |
| --- | --- |
| A/B — final actual-source contract suite | PASS, exit 0: 410 assertions, 36 negative cases, independently calculated 22-vector TSV/Java oracle; explicit inventory insertion/deletion/permutation and stream/selection isolation |
| C — final compiled GWT/JVM parity | PASS: identical 4,748-byte UTF-8 bodies, `parity-result.json`, `jvm-parity.txt`, `browser-parity.txt` |
| D — real replay | PASS: all 19 unique closed-corpus cases, `replay-results.json`; 13 earlier unchanged replay proofs reused under the exact audit below, six transistor cases on the final build; final LED 0 parity run also repeats replay |
| D — affected legacy and privacy | PASS: challenge LED 3; layout across all six families; Task 41 14-case corpus / 128 solver samples; ordinary LED 3 and RC 0 readiness, DOM/mounted attributes and accessibility privacy; `regression-results.json`, `privacy-results.json` |
| E — existing contracts | PASS, exit 0: Task 44 247 assertions / Task 45 202 assertions; actual JDK8; task-owned classes/scratch removed |
| E — final production build | PASS, exit 0: all five OBF permutations, 39.037 s compile / 1.030 s link; `compiled-permutations.json` |
| F — independent review | PASS: fresh Luna source review, targeted fixture-test delta, exact reuse audit and final artifact/cleanup review; no acceptance blocker |

Astra owns integration, runtime/browser, docs and publication. Luna leaf model
`gpt-5.6-luna`, reasoning `max` were explicitly requested for disjoint production,
oracle and fresh review assignments. The speed control is unavailable; no global
settings changed. Pre-existing preview state at port 8897 and historical
quarantined resources are outside Task 46 ownership. Only this task's own preview,
tab and disposable scratch are subject to its cleanup.

## Qualification notes

The selected tools are Temurin JDK8 `1.8.0_502`, GWT 2.7.0 and Python 3.12.14.
The focused commands are:

```powershell
scripts/verify-block-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07
scripts/verify-challenge-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -PythonExe <selected-python> -ParityOutputPath docs/task-evidence/task-46/jvm-parity.txt
scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF
```

`-ParityOutputPath` writes the actual captured Java corpus body as UTF-8 without
a BOM. Redirecting a PowerShell console log converts embedded LF to CRLF; those
log bytes are not the canonical Java output. The exported body and the compiled
browser's returned DOM string are compared directly, without numeric conversion
or newline normalization. The production code, literal oracle and assertions
execute on both platforms; this is not a JavaScript reimplementation.

Each replay URL is `/circuitjs.html?tsjChallenge=<family>&seed=<seed>&tsjDebug=true&tsjVerifyTask46=true&running=true`.
The existing Browser API reads only published DOM attributes. Receipts check the
actual family and exact decimal seed in the returned summary before recording
PASS, compare the whole parity body and preserve the semantic snapshot digest.
The replay verifier compares both fresh generations, then independently installs
and solves each through the existing healthy/faulted/scenario lifecycle. Its
fresh cleanup creates a third owner and verifies restored semantic readiness.
All 18 unsupported-input checks reject before runtime construction. The initial
legacy route passes existing diagnostic admission; paired developer installs
retain their narrower scope and do not claim that admission a second time.

Development failures are retained as failures, not qualification results:

- Constraint field order, metric alignment, nested descriptor delimiters and
  header/range diagnostic classification were corrected before the passing
  focused corpus. No literal seed expectations were derived from production.
- A CRLF-sensitive marker regex made the first completed Java corpus invocation
  exit 2. The harness was repaired and rerun; the failed invocation is not PASS.
- The first compiled debug route failed because debug mode suppressed the
  workbench required by normal admission. Only the explicit Task 46 debug route
  now constructs it; no admission guard was weakened.
- Replay initially compared `PhysicalPartMountState` using default object text.
  Its varying allocation identity was diagnosed with the first differing row,
  then replaced by the installed flag and existing semantic slot ID. Runtime
  owner/element/part/terminal inequality assertions remain mandatory.
- Some in-app Browser commands exceeded their 3000 ms transport deadline while
  synchronous CircuitJS verification was executing. These command failures are
  recorded separately. Reading the same completed page establishes the actual
  verifier result; it does not retroactively make the timed-out command pass.
  Navigation and result collection were separated after the repeated signature.
- The first RC privacy check used the LED retest button label and failed its
  readiness check. The observed RC control is `Power-cycle and Retest Customer`;
  a corrected exact-label check passed on the same loaded page, without a
  product change. The failure remains in `privacy-results.json`. The preview
  had completed shutdown after this failed check; final RC inspection used the
  already loaded production page, not a mock or replacement page.

## Dependency audit and reuse limits

Against reviewed Task 45 HEAD, the only modified pre-existing Java file is
`CirSim.java`. Its delta adds the double-gated Task 46 route, string evidence
publication, failure reporting and the explicit verifier's workbench condition.
With the Task 46 flag false, the old workbench condition is equivalent and the
new execution branch is inert. All other Task 46 production classes are new.

The following accepted dependency lanes have no source diff: six leaf
generators and `QuickPlayFamilyRegistry`; existing seed parsing/remapping;
fault/scenario engines and `GeneratedChallengeController`; CircuitJS solver and
element implementations; physical graph, mutation, measurement and power owners;
Task 41 admission/snapshot/verification; geometry version, placement/routing and
renderer providers; Task 44/45 contracts; and the build/preview/process-trust
implementation, including `VerifierIsolation.psm1` and `verify-browser.ps1`.

Consequently, historical Gate B/43P and unaffected Task 45 55-route evidence
retain only their originally qualified claims and limitations. They do not
certify the new descriptor, streams, replay adapter or browser route. Those
proofs run freshly here. This task uses the existing run-owned preview helpers
and an in-app Browser tab; it does not claim a fresh strict CDP-wrapper campaign
or a new cleanup/protocol certification. No 500 ms ownership limit, exit-code
contract or process-discovery implementation was changed.

No source-falsifier mutation is planned, so mutation-anchor preflight is NOT
APPLICABLE. There is no new visible player flow. Normal-player DOM/accessibility
and real screenshots check the privacy boundary; historical visible repair and
probe-control proofs remain limited to their unchanged implementation paths.

The final source/test/harness fingerprint is
`1b21500d2c07f5b2f9c082f83203a4be96e4948c6bf396b4fb5c9d1ba494cc23`, with all
12 file hashes in `source-candidate.json`. The first 13 replay cases (LED,
diode, parallel and RC) qualified on
`0d1d1e1d57fa0e38ac274a6fcd08d7a61abc7d758fb196b354651ee7e3925942`, retained
in `replay-candidate-before-fixture-test.json`. An exact hash comparison of all
12 inputs found only `Task46ContractVectors.java` changed: its in-memory fixture
test now explicitly walks optional insertion/deletion/permutation and unchanged
fault/scenario candidates. Descriptor, constraints, derivation, replay,
diagnostics, CirSim integration and every existing runtime dependency stayed
byte-identical. Astra reuses those 13 semantic replay proofs under that audit.

The final 410-assertion corpus runs freshly in Java and compiled GWT on LED 0;
the whole canonical UTF-8 body is 4,748 bytes on both platforms, SHA256
`5fb0d7105ae0fb8f8e8e90a5f2ee69dd32101a85eade19923405a97519359625`.
Its repeated LED 0 semantic snapshot equals the earlier snapshot exactly.
`parity-result.json` records this separate final-corpus check. The earlier
342-assertion/4,651-byte parity body is retained as historical evidence only;
it is not substituted for the final corpus. All six transistor replay cases,
the affected legacy routes and normal privacy checks use the final build.
Task 44/45 pure-suite inputs also remain byte-identical after their fresh passes.

## Curated player evidence and resources

`normal-led-3.png` and `normal-rc-0.png` are real in-app Browser captures of the
ordinary production pages, each with an ignored `tsjVerifyTask46=true` parameter
and no debug flag. They show the board, enabled meter/power controls and normal
service ticket without replay details or fault IDs. Both images were inspected
and are nonblank. `privacy-results.json` separately checks body text, mounted
attributes and accessibility; pixels alone do not prove hidden-DOM privacy.

`resource-cleanup.json` records the existing helper's successful exact preview
cleanup: process termination/absence, listener inspection/absence, released
claim and mutex, complete release journal, wrapper exit 0 and no remaining
task-created tab. An additional current listener check confirmed absence.
The prior preview state at port 8897 was unchanged. No task-owned live process,
listener or tab remains. Raw qualification/failure receipts are deliberately
retained outside the repository; normal build output remains in `war`.

**FOLLOW-UP — TEST/TOOL:** an unexpected `AssertionError` from the pure corpus
would leave the developer route at RUNNING until a browser timeout, because
CirSim's outer verifier handler catches `RuntimeException`. This fails closed
and does not affect any passing assertion, replay or ordinary player flow.
Independent review classified it as nonblocking; this milestone does not widen
into a verifier exception-handling cleanup.

Task 47 and runtime composition remain unstarted. Completion of this immutable
contract/replay milestone does not claim the Composition Entry Gate, deep
same-owner transactions, new assembled physics or a composed player flow.

## Independent acceptance and handoff

Fresh read-only Luna review authored neither the implementation nor its oracle.
It verified all 12 final source hashes, all five compiled hashes, exact final
parity, all 19 distinct replay receipts, the 13-case dependency reuse, existing
regression data, privacy/screenshot hashes and the raw cleanup manifest. Source,
targeted fixture-test delta and final evidence reviews are PASS with no blocker.
The unexpected-AssertionError diagnostic issue above remains a follow-up only.

The developer diagnostic API reports deterministic rejection code/field for
unsupported descriptors; the compiled debug verifier executes and checks that
path for all 18 negative canaries. The published supported descriptor correctly
reports supported resolution and its summary records the negative-canary count.
No extra rejection UI or raw descriptor query is required for this bounded API.

The enclosing commit contains the accepted candidate. Normal publication uses
the configured `origin/codex/task43p-final-recovery` upstream; the final task
handoff records the exact verified SHA and the authorized post-push Gmail result.
