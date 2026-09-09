# A03 closure reconciliation

Status: COMPLETE — QUALIFIED FOR PUBLICATION. The candidate is the complete
A03 working-tree delta above accepted A02 commit
`363244c0483ec665feff3cb8f40fab09851c7b98` on
`codex/task43p-final-recovery`. This record closes the historical runtime and
cleanup blocker without rewriting or deleting its evidence.

## Final candidate identity

- The checked source inventory has 899 entries; every recorded per-file SHA256
  in `candidate-source-files.json` matches the final checkout.
- The compiled v6 receipt's recomputed provenance matches the candidate:
  source `0815187350cfb5c9ebd847d7ee43434ad21102aab0dbdede3cf2f5029bead8c5`,
  scripts `db784f98424c5b461796e0f4ac23da0a60ed6d43beb20e00aa6aea659b3d4ca1`,
  verifier isolation
  `202867ead5c38202538ae0e22991ecd6bc5fb0d12f8c9e66860b23ef31425676`,
  preview
  `ecb510e020ee1c5322c312f0cbec049924903f13d693d660ff1eef6f93324ae4`,
  web `f2130ed99309780f05a48aafd28cc5f79c19b6f2afdace1fcf0efcfff806d1ff`,
  and 1,308-file execution
  `3852a2df416473cf8d755af8461612994c2e8277f680ef9a5065b3f67a7140b4`.
- The receipt repository identity is the current uncommitted candidate identity
  `20cd7fcd353094d844fb`; it is intentionally distinct from the accepted A02
  base commit. Subsequent changes in this reconciliation are documentation and
  evidence only, outside the source/script/web provenance inputs.

## Compiled runtime and compatibility evidence

`runtime-all-final-recovery-v6.json` is a compiled-browser protocol receipt
(`troubleshootjs-a03-browser-report-v1`) with `gate=All`,
`status=PASS`, run `7c51dad68fc9464daf54a5dc241f8aad`, nine routes and
`cleanup.success=true`.

| Route | Result retained by v6 |
| --- | --- |
| A03 identity/replay | `PASS:a03`; all 109 vectors, deterministic/manifest, solver-node, PCB-coordinate, stale-action and original-owner checks pass |
| Forced A03 failure | Expected `FAIL:a03:a03-explicit-failure-canary`; no success receipt is accepted |
| Debug disabled | No A03 developer report; normal readiness remains available |
| Task41 | 14 routes and 128 solver samples pass |
| Task46 | Four required report payloads pass |
| Task47 / Task48 / Task49 | Required reports pass with 1,540 / 755 / 1,053 assertions |
| A02 | Required regression protocol and status pass |

The v6 compiled A03 vector text byte-matches `jvm-vectors.txt` (17,505 bytes),
and the three compiled manifests byte-match the JVM receipts. Fresh accepted-base
comparisons also byte-match the final complete Task48 (755 assertions) and
Task49 (1,053 assertions) reports.

JDK 8u502 / GWT 2.7 compiled all five OBF production permutations from this
source candidate. The final complete
`scripts/verify-gate-b.ps1 -JavaHome <repository JDK 8u502>` run exited `0`
and reported `PASS:Gate B deterministic checks` for candidate
`363244c0483ec665feff3cb8f40fab09851c7b98`. It exercised the strict verifier
ownership and receipt matrix, including listener/PID/root-gone/late-markerless
checks, containment recovery, PID reuse, escaped-grandchild and null-command
helper negatives, natural/forced exits, unrelated-process survival, lease
isolation, and the final candidate receipt.

## Cleanup and retained historical evidence

The v6 durable manifest records complete cleanup: preview and CDP leases were
released, the browser recovery receipt closed, containment launched, and the
cleanup-error list is empty. Its preview PID `167612` and ports `58795` and
`60523` were subsequently verified absent.

The older run `6cdab3121a5446ca9a622be3357e0e07` remains preserved historical
failure evidence. Its legacy manifest is `cleanup-failed`, lacks a recovery
receipt and containment launch, and retains its old profile/claim/run records.
Its original PID `212748` and ports `58648` and `56588` are absent, but nothing
in that legacy run is relabeled as cleaned or removed. No unrelated browser
process was touched.

## Closure decision

The fresh independent read-only implementation and provenance review is PASS;
see [independent-final-review.md](independent-final-review.md). The only
nonblocking follow-up is unsupported interpretation-bearing saved-action
round-trip data: bounded A03 replay rejects it rather than silently assigning an
identity. With that limit recorded, the A03 implementation, verifier recovery,
compiled runtime, compatibility and cleanup gates are qualified for normal
publication. A04 remains unstarted.
