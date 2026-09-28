# Validator and audit provenance

The exact r2 pair validator and checker/canary receipt are included with their original hashes in `inventory.json`. The exact validator binds the predeclared plan, candidate source, host runner, frozen helper/checker/receipt, strict readers, raw reports, and 51 metadata negative canaries. The final validation receipt records the completed r2 invocation and 43 strict corruption cases for each arm.

The legacy helper and final-input audit script in this bundle are path-redacted archival copies. The helper's two absolute defaults and the audit script's one absolute fixture default were replaced; their original SHA-256 values are recorded in `inventory.json`. The original helper hash remains pinned by the exact validator and validation receipt. Do not treat the redacted helper as the helper used for the PASS or expect it to satisfy the validator's unchanged hash check. The actual run used the original helper identified by its SHA-256. No personal absolute paths are included in this package.

`validate_current_preflight_pair.r1.py` is retained as historical review context only. Root review found API mismatches, a missing runner hash binding, and static-guard issues; it was not the passing r2 validator. Those were validator-draft defects, not browser failures.

Root review found one personal forward-slash repository default in the archived runner, missed by the initial packaging privacy scan. Its archival copy is now redacted and its original hash retained in the inventory. This changes no executed file or raw result.
