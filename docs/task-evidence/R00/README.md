# R00 qualification evidence

R00 implements one current development contract and closes all three A04
provider-boundary follow-ups. A05 remains unstarted. Base commit:
`cc3532e8d138424ce986aa8f9b76688ec315f0a0`, branch
`codex/task43p-final-recovery`.

## Candidate and gates

Final Java source SHA-256:
`e36de0c9eb252cf11ebc460e191773f31b59e1cae46f688bc8d24a0646d0b8d3`.
The final build and all nine compiled routes use this source. Exact input
digests are in [execution-provenance.json](execution-provenance.json) and
[final-provenance.json](final-provenance.json). Git records the complete
source, test, policy and evidence candidate in the containing commit.

| Gate | Result and evidence |
| --- | --- |
| Current native contracts | PASS, exit 0: 14 Java suites, independent 22 seed / 8 value vectors, and report protocol checks. [Native/build receipt](native-build.json). |
| Final production build | PASS, exit 0: actual `scripts/build.ps1`, JDK 8u502/GWT 2.7, all five OBF permutations; 61.327 s compile, 1.353 s link. |
| Current compiled corpus | PASS: A03, Task46, Task47, Task48, Task49, A02, A04, expected A04 forced failure and A04 debug-off. [Compact report hashes/results](compiled-summary.json). |
| Exact current JVM/GWT parity | PASS: shared identity vectors, current resistive seed 1 and controlled seed 2 manifests, signed seed vectors, eight value/fault/descriptor cases. No historical golden equality. |
| A04 physical boundary | PASS: 336 compiled context / 404 runtime assertions; native construction 272 / physical declarations 532. Six current resistive/controlled construction cases plus intentional mismatches and positive controls. |
| Real electrical and lifecycle behavior | PASS: Task47 2,305 assertions / 4 measurement cases / both repaired owners; Task48 755 / 6 / both owners; Task49 910 / 6 / both repaired pairs. Prior owners restored and candidate cleanup PASS. |
| Ordinary visible interaction | PASS: composed diagnosis, wrong repair rejected, correct repair verified, HIGH/LOW response, active meter and replacement/reset/power checks. Five inspected screenshots below. |
| Normal leaf routes | PASS: LED, diode, parallel, RC, NPN and NMOS, seed 3; visible boards, working retest controls, unrepaired retest rejected, no developer report. [Route observations](normal-leaf-routes.json). |
| Independent review | PASS after integrated and focused corrective reviews. [Scope and adjudication](independent-review.md). |
| Isolated CLI launcher | BLOCKED before application checks; two exit-2 ownership-deadline failures with cleanup PASS. This is not a product failure or a certified wrapper. [Failure records](environment-limitations.json). |

The final reader correction changes A02's expected protocol from retired
`TSJ-A02-1` to current `TSJ-A02-2`. Five focused cases accept the current report
and reject retired/unknown protocols, failure and unfinished execution; the
fresh report-contract run passes **114 assertions**, exit 0. All nine captured
reports pass the corrected maintained predicates and exact parity comparisons.

This last edit changes only the pure report reader and its tests. The captured
script digest is `afe44516...`; the final script digest is `698ee53a...`.
Java source and compiled web digests are identical. Therefore the actual final
build, native Java and compiled product results remain applicable; the changed
reader is freshly tested. It does not justify reuse of any earlier failing
Task47 verifier result.

## Three hardening dispositions

| Boundary | Implemented protection and qualified misuse |
| --- | --- |
| A: physical/electrical correspondence | Validate complete declared component, unit/package, pad, net and terminal relationships against the spec before allocation. Swapped nets/pads, foreign terminal owners and provider identities reject; legitimate current bridges and shared-package data controls pass. No arbitrary multi-unit runtime support is claimed. |
| B: backing provenance | Validate primary/secondary, attachment and fault backings against the precise participant and electrical relationship. A same-kind element elsewhere in the context cannot substitute. Explicit device-owned bridges remain supported. |
| C: coherent live ownership | Plan/spec/metadata/runtime and the context's exact issued receipt must agree before mutation. Equal-valued independent attempts, copied receipts, foreign runtimes, stale/aborted receipts and wrong metadata reject. Same-board context B abort cannot revoke context A's pad bindings, receipt or readiness. |

## Visible playthrough and screenshots

Setup used the existing normal URL
`circuitjs.html?tsjChallenge=controlled-indicator&seed=2`, without debug flags.
The already delivered **Open controlled indicator** control was also exercised
for board replacement. This establishes the existing composed route; it makes
no claim about A05's future alternate-provider selection or new menu flow.

All subsequent actions used visible buttons, catalog selections and board
clicks. No injected repair or private-state mutation was used. Left click
placed the red voltage probe and right click placed black; J1 measured 5 V.
Resistance mode while powered required power off. With power off, probing
RLOAD showed OL. Selecting the active meter again exited probe mode and
restored the ordinary context interaction.

The component service view showed the nominal 270-ohm part. The player removed
it, installed 100000 ohms through the catalog, powered on and retested: failure.
The service view then confirmed 100000 ohms. Removing it and installing 270
ohms restored the controlled indicator; customer retest verified the repair.
LOW extinguished the indicator and HIGH lit it. Completion intentionally
locks further meter/power/retest mutation controls. Opening a fresh board
cleared the parts/probes; repeating replacement while an unpowered meter was
active cleared that reading, and an explicit power-off remained off.

| Screenshot | What the actual image demonstrates |
| --- | --- |
| [01-unrepaired.jpg](01-unrepaired.jpg) | Initial unlit composed board and failed customer retest. |
| [02-unpowered-resistance.jpg](02-unpowered-resistance.jpg) | Power off, ordinary probes and OL resistance observation. |
| [03-wrong-repair-rejected.jpg](03-wrong-repair-rejected.jpg) | Installed wrong resistor, removed part in tray and rejected customer retest. |
| [04-repair-verified.jpg](04-repair-verified.jpg) | Correct replacement, lit indicator and the verified customer ticket. |
| [05-current-leaf-parallel.jpg](05-current-leaf-parallel.jpg) | Retained parallel leaf board, unequal indicator symptom and failed unrepaired retest. |

The composed playthrough was performed on source `519ddfd6...`; leaf receipts
carry preview source `d6519d4d...`. The sole subsequent Java edits were to the
developer-only Task47 verifier. Root and independent review checked that
ordinary routes do not execute that code. Player evidence is explicitly reused
on this unchanged dependency boundary; final compiled verification is fresh.

## Evidence route, limitations and resources

The built-in Browser navigated the same selected current developer URLs
declared in `scripts/verify-a03-browser.ps1`. Read-only DOM report attributes
were captured with source/script/web/execution stamps. An ephemeral collector
loaded the maintained pure route/report predicates through the PowerShell AST,
checked exact queries, all nine reports and one execution candidate, then
compared native/GWT bytes and eight independent value receipts. The compact
results preserve report hashes without committing large duplicate manifests.

The strict CLI smoke failed in `Start-VerifierOwnedPreview`, before browser or
application execution: its unchanged 500 ms port-ownership proof exceeded the
deadline. A bounded diagnostic measured one cold listener query at 639 ms.
Both failed attempts cleaned up. Core build, preview, start/stop and isolation
files are byte-identical to accepted A04; [the dependency audit](isolation-dependency-audit.json)
records those hashes. No full historical matrix, Task43 campaign or Gate B
recertification is claimed. Actual compiled checks, ordinary play and wrapper
certification remain separate evidence.

An initial Windows PowerShell build preflight hit a short-process identity
race and exited 2 before compilation. The unchanged build script passed under
PowerShell 7. Its isolated scratch was subsequently removed after identity and
path checks. An early convenience-preview stop failed because its launcher
had exited; exact saved process identity was then revalidated within 74 ms,
the owned handle stopped, and process/port absence proven. Later previews
retained their launcher and the unchanged standard stop script succeeded.
These failed invocations are not recorded as PASS.

Final task-owned Browser tab and preview are closed. Standard stop positively
released port 8899 and the retained launcher exited normally. Native/build
execution resources were reconciled. Unrelated historical artifacts and user
processes were untouched. All task-owned scratch was removed after evidence
curation and completed review; [resource closure](resource-closure.json)
records the checks. Git publication is recorded in the final handoff after
remote verification.
