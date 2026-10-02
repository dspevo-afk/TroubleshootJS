# Q30 epoch 15 cold77 partial failure

This is failure evidence, not Q30 acceptance or publication approval. Q30 remains disabled for normal play.

## Recorded result

- Original frozen plan: 77 roots and 308 canonical candidate manifests; unchanged 90,000 ms / 640 work / 5,000 ms active-operation limits.
- Attempted roots 1–32 only. Roots 1–31 passed the application reader and browser host. Root 32, seed 75, returned application TIMEOUT at 90,221 ms, 221 ms over the unchanged deadline. Its browser host independently reached SCREEN_DONE and host cleanup passed.
- Roots 33-77 (45 roots) were not run. The pilot's three roots passed; cold-rest stopped at the first nonpass. The required full77 auditor ran and returned FAIL: Sequence incomplete: cold-rest.
- The timeout’s cause is unknown. This packet preserves the nonpass and does not substitute the host’s SCREEN_DONE for application PASS.
- The short compiled-canary controller’s separate CIM capture was missed after it exited. Its explicit caveat and actual host identity/cleanup receipts are retained.

The package excludes browser profiles and screenshots. Source binding records all 1,354 raw input hashes and rechecks them before publication. It does not claim visible-player QA or Q30 acceptance.
