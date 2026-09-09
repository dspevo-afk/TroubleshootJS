# A04 independent source review and root adjudication

## Verdict: PASS with nonblocking hardening follow-ups

The implementation and test authors did not supply their own independent approval. Earlier fresh Luna MAX reviews identified real bridge, terminal, completion, and revocation defects; root repaired them and obtained targeted delta reviews. The final constructor/configuration-order delta received a separate Luna MAX read-only PASS.

The final integrated reviewer first ran as `gpt-5.6-luna` with MAX reasoning and default non-Fast speed. That invocation exhausted ordinary usage before a verdict and is explicitly NOT counted as PASS. The same independent read-only review thread resumed through the project's documented `gpt-reserve` fallback with MAX reasoning and returned `REVIEW ONLY - PASS (no blocker found)`. The runtime exposed `gpt-reserve`, not verified regular Luna identity; that distinction was disclosed. Neither reviewer implementation nor its test oracle was authored by this reviewer.

The final review covered v1/v2/v3 physical mappings, fault ownership, provider declarations, one-context ownership, typed device bridges, private coordinates, cleanup revocation, and downstream replay contracts. It independently inspected the physical-declaration test's exact v1 candidates and controlled mappings. Source whitespace checks passed. The physical slice was rechecked after its writer completed.

The review was static: it did not claim to run the final GWT/browser/cleanup or parity gates. Root separately executed and reconciled those gates on the exact final candidate, as recorded in `native-build-summary.md`, `runtime-a04-final.json`, `runtime-regressions-final.json`, `candidate-inputs.json`, and `execution-provenance.json`. The review's statement that those gates remained root-owned is not a remaining failure after the recorded root PASS results.

## Nonblocking follow-ups

1. Add earlier defensive pad/component/net cross-validation when constructing arbitrary physical declarations (`PhysicalConstructionProvider` and `PhysicalConstructionMaterializer`).
2. Strengthen declaration-time ownership correspondence for secondary, attachment, and fault backings, beyond the present typed element and current provider mapping checks.
3. Add an explicit plan/spec/receipt identity assertion at the internal materializer entry point before future callers can supply independently assembled inputs.

Root adjudication: retain these as bounded defense-in-depth work for expanded provider use. The current materializer rejects aborted receipts and foreign physical boards; the only current construction coordinator supplies the same resolved plan, its metadata, and its completed context receipt. Providers do not receive a second runtime or unrestricted materializer authority. Actual current mappings and both migrations pass the independent terminal/physical/solver and repair tests. These findings do not establish a failure on an authorized current construction path and do not justify starting a new mutation framework or reopening A03.

The immutable-state, unsupported interpretation/import, and bounded-category limitations remain explicit. No hypothetical hostile-Java sandbox guarantee is claimed.
