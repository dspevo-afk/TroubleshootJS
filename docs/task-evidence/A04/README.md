# A04 construction evidence

Status: COMPLETE - QUALIFIED. Accepted implementation base: `1d995d4f5b21142d4ca5643ede546afdfe51340f`. Branch: `codex/task43p-final-recovery`. A05 and later milestones remain unstarted. Publication is performed by the root owner after this qualification record; the completion notification identifies the final published commit.

A04 migrates the existing resistive and controlled-indicator versions 1/2/3 through constrained provider construction. One immutable resolved plan, one global private allocation/binding owner, device-owned joins, and provider-declared physical materialization preserve the existing electrical, physical, fault/repair, and replay contracts.

| Required gate | Final evidence | Result |
| --- | --- | --- |
| Native declarations and negative contracts | [Native/build summary](native-build-summary.md) | PASS: report 103; A03 shared 109 / replay 29; A02 candidate / geometry 66 / replay 112; package 37; construction 433; physical declarations 730 |
| Pure assembly and independent reference oracles | Native/build summary, unchanged Task46/47/48/49 oracles | PASS, exit 0, scoped scratch cleanup |
| Final production build | Native/build summary; [exact 987 input hashes](candidate-inputs.json) | PASS: JDK 8u502 / GWT 2.7, all five OBF permutations |
| Actual A04 compiled conformance | [Final A04 receipt](runtime-a04-final.json) | PASS: 204 context assertions / 238 runtime assertions; v1/v2/v3; forced failure; debug-off; original owner restored; cleanup=true |
| Existing compiled regressions | [Final nine-route receipt](runtime-regressions-final.json) | PASS: A03, Task41/46/47/48/49, A02; existing physical truth/repair checks; cleanup=true |
| Exact durable replay and bounded observations | [Compatibility reconciliation](compatibility-reconciliation.json) | PASS: JVM/GWT/baseline canonical vectors/manifests exact; node partitions preserved; only qualified numerical observation differences |
| Current execution provenance | [Recomputed 1,320-file provenance](execution-provenance.json) | Both final browser receipts match source/script/web/execution identity |
| Independent source review and repair deltas | [Independent review and adjudication](independent-review.md) | PASS; disclosed ordinary Luna usage failure, completed documented Reserve fallback; three nonblocking hardening follow-ups |

[Closure reconciliation](closure-reconciliation.md) records exact report differences, deadlines, evidence reuse, and retained limits. Full Task47/48/49 JSON is not mislabeled byte-identical: analyzed node numbers are transient, and the small live numerical differences remain within the unchanged original acceptance tolerances. Durable identities, recipes, packages, geometry, fault/repair metadata, and all other report fields remain exact.

Earlier development builds, the initial two-context oracle, and zero-route transport failures do not qualify this candidate. `compiled-browser.json` and `compiled-browser-tcp.json` remain historical failures. Root replaced the deficient oracle, repaired the real implementation defects, ran the final compiled paths, and retained the failed attempts separately. No failed cleanup was relabeled PASS and no historical A03 orphan evidence was removed.

The final A04 application gate used 90 seconds; the heavy existing nine-route gate used its supported 300-second route budget after an owned debugger diagnostic of the legacy layout workload. The separate 500 ms ownership contracts and verifier-isolation implementation were unchanged. No new UI interaction, IC/MCU/import provider, broad leaf conversion, or general rollback framework is claimed.
