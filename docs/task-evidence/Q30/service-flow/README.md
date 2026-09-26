# Q30 actual challenge and service continuation

Base: independently accepted Q30-P1
`fac582c1150273c01d09bc704dd796c1bf49a135`, isolated
`codex/q30-multirail-qualification`. Full Q30 remains BLOCKED and unregistered.

## Scope and pre-implementation audit

The P1 workbench constructed a healthy developer fixture with an empty fault
population, no challenge/family state and an always-false repair predicate.
`Rb30Plan` and `Rb30Generator` already declared these five candidates:

| Candidate | Physical target | Existing service owner |
| --- | --- | --- |
| DREV_OPEN | DREV | Replaceable diode capability |
| REN_OPEN | REN | Replaceable resistor capability |
| SENSOR_A_OPEN | RSA | Replaceable resistor capability |
| DRIVE_A_OPEN | RDA | Replaceable resistor capability |
| RELAY_B_COIL_OPEN | KB | Scoped Rb30 relay adapter over E03 |

U2A/U2B have scoped decision adapters and KA has its own relay adapter, but
they are supporting parts rather than additional selected fault hypotheses.
The previous relay test proved registration and catalog isolation, while the
metadata test proved construction/render geometry. Neither proved live service.

This slice connects the actual candidate to `GeneratedChallengeController`, a
provider diagnostic recipe, the existing workbench mutation providers, and
`GeneratedCustomerRetestProfile`. It keeps every candidate in the population.
The customer recipe drives both LOW, A only, B only and both HIGH, measuring
the causal E02 rail and each loaded output against its own local return. It
restores previous sensor commands and never connects a source or repowers the
board. The original fault stays with its removed physical part.

## Admission boundary

`PcbTwoLayerRules.requireDeveloperAdmission` still requires the developer flag
for Q30 copper. D01's proof service and controller admission both reject that
flag. This slice does not change those shared boundaries, P09, the 48-via cap,
the physical policy or the normal catalog. The declared diagnostic program can
run through the production observation executor on a developer challenge;
such measurements and service evidence are not a D01 admission receipt.

## Validation

This bounded actual diagnosis/service/repair slice is PASS. Full Q30 remains
BLOCKED and unregistered. The maintained full native run passed
67 Java suites plus independent seed/value/role
oracles, 474 report assertions and 23 non-live listener assertions; exit 0 and
scratch cleanup are recorded in `native-full.log` and `native-full-receipts.log`.
The final affected native run (`native-preparation-final.log`) passes all ten
cases and 2,426 service assertions, plus 110 relay and 1,713 metadata assertions
(4,249 total), with exit 0 and verified scratch cleanup. It calls the actual
healthy/faulted preparation callbacks before the independent output oracle.
The native service fixture supplies only UI readiness after real solver profiles,
so compiled controller lifecycle and visible input remain separate gates.
The native runner attempts all ten cases in separate children with the existing
60-second child limit and checks their exact seed/fault receipts. An early
aggregate timeout is retained; partitioning the newly added ten-case workload
does not change CircuitJS's 500 ms accepted-step or 5 s temporal-call limits.

Reproduction uses the maintained commands with a local JDK 8 installation:

```powershell
./scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite Q30ServiceFlowContractTest
./scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -ReceiptOutputPath docs/task-evidence/Q30/service-flow/native-full-receipts.log
./scripts/build.ps1 -JavaHome <JDK8>
./scripts/start-preview.ps1 -Port 8903
```

The compiled census opens a fresh Browser tab for each seed `0`, `37` and
each of the five fault IDs above, using this production developer entry:

```text
http://127.0.0.1:8903/circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Bench=true&tsjQ30Seed=37&tsjQ30Fault=SENSOR_A_OPEN&tsjQ30Service=true
```

It reads the published workbench/service DOM receipts and preview source/web
digests, saves terminal outcomes, then closes each completed tab. Read-only
CDP dispatch timeouts are counted separately; only a terminal service PASS is
accepted. This automatic developer route is separate from the visible player
input evidence. The independent reader requires one same-build record for
every seed/fault pair, 37 actual DC samples per record, correct output truth,
and all twenty seed-local pairs separated beyond their combined tolerances:

```powershell
python -B docs/task-evidence/Q30/service-flow/check_receipts_canaries.py docs/task-evidence/Q30/service-flow/compiled-service-receipts.json
```

`native-full-inputs.sha256` records the changed source/test/runner inputs of the
full native run. `input-audit.txt` lists the four subsequent changed inputs:
the developer workbench and service verifiers, Q30 behavior, and its native
service fixture. These correct unsupported KB lead service, explicit adaptive
configuration, and the real LOW-before-fault preparation for regenerative
feedback. The provider, electrical service owners, generator, runner and shared
models remain unchanged. The three affected Q30 native suites pass freshly;
existing family/oracle results are reused across this audited boundary. The fresh GWT
build and compiled census qualify developer verifier changes. The final input
manifest is `candidate-inputs.sha256`. Earlier failures remain in `attempts.md`.
Evidence logs redact personal paths and normalize line endings/trailing spaces
from wrapped warnings. Their outcomes, warnings and failure messages are retained.

`build-preparation-final.log`: PASS/exit 0, actual JDK 8u502/GWT, all five permutations;
compile 85.395 s and link 1.405 s. The previous build's visible player PASS and
all five seed-0 compiled services remain historical; their input configuration
differs. The final [compiled census](compiled-results.md) passes all ten cases
on one source/web build. Its independent reader accepts 370 samples and all
twenty distinguishable fault pairs; seven corrupted-receipt canaries reject.
The final [visible player flow](player-input.md) passes meter observation,
unrepaired retest, lead lift/reconnect, removal/replacement and repaired retest
with four inspected screenshots. Its build digests match the complete census.

Representative and held-out boards remain 33-part examples; this is not the complete 20–40-part
population qualification.

## Review and limits

Root reviewed the integrated changes. A read-only independent review found no
blocker in behavior, diagnostic ownership, generator and native service
expectations. A separate async review found stale cleanup/report publication
risks; root fixed them and that reviewer verified the exact-owner guards at
both boundaries. Source reviewers did not run native tests or the build. A separate
reviewer ran the independent receipt reader and seven negative canaries to exit 0
(`receipt-checks.log`). A capability
audit identified the unsupported relay lead operation; the verifier now records
that boundary explicitly. Receipt review also led to independently checking the
DC tolerance formula and exact booleans/numeric types.
The held-out preparation failure also received read-only review: real LOW input
conditioning, the production profile callbacks in native coverage, and the
shared ready-and-settled boundary address the missing lifecycle check.

The diagnostic observation program intentionally finishes with HIGH inputs;
it is not a general snapshot/restore API. Customer retest restores prior inputs
in `finally` while the exact owner/graph remain current. The developer verifier
starts from the HIGH fault profile and restores its saved pre-Q30 owner on a
current-owner failure. Stale callbacks do not touch successor owners or their
DOM receipts. External rebind/cancel interleavings have source-review coverage,
not an additional compiled race test in this slice.
The developer service census requires no concurrent manual interaction. Its
timer job is not registered with the same-owner Reset cancellation path, and
settled controls can become available between stages. Same-owner Reset, power
or input changes during that automated run are unsupported and unqualified;
cancellation/input locking remains a follow-up for broader Q30 lifecycle work.
The visible player test omits `tsjQ30Service` and uses the ordinary controllers.
The early workbench receipt is published before service and its `serviceMillis`
is zero. Service duration comes exclusively from the separate service receipt's
`elapsedMillis`; neither duration is a cold/warm performance qualification.

Final preview PID 37052 was stopped with the maintained identity-checked wrapper;
port 8903 was positively released (`preview-stop-preparation-final.log`). Native
and reader scratch cleanup passed. All live qualification/player tabs closed.
Three earlier crashed Browser tabs remain inaccessible under Browser's crash-URL
policy, so their cleanup is unproved. [Resource accounting](resources.md).

## Remaining full-Q30 gates

Production D01 solvability/admission for medium boards, broader mutation and
alternative-repair qualification, normal-player entry/privacy/replay, the
20–40-part structural variant corpus, and complete cold/warm p50/p95 and
failure-distribution measurements remain required. Q60 is not started.
