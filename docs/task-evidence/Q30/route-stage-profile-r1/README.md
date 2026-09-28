# Q30 routing-stage profile R1

Status: PASS_COARSE_PRIVATE_ATTRIBUTION_ONLY. Normal Q30 acceptance remains BLOCKED while timing, scale, and other acceptance gates are unresolved; the catalog remains disabled as a consequence. No push or publication is made by this packet.

This packet records the private routing-stage profile comparison on the frozen 1,340-input base and 1,343-input candidate. It is a coarse attribution result, not an optimization, integration, or Q30 acceptance result. The three fresh seed-10014 rows are control, profile, control. Cold application totals are 98,580 ms, 97,199 ms, and 96,985 ms; proof/routing fields are 69,374/22,983 ms, 68,529/22,539 ms, and 68,214/22,560 ms. The profile is inside the 1,595 ms control cold range and its routing field is 21 ms below the lower control.

The profile has 22 phases and 7 work counts, with 2,656 calls and 5,312 clock reads per cold or warm arm, zero errors, and frozen measurements. The monotonic partitions are P05 route 1,403.7 ms with 247.100000068545 ms residual and P07 slice 21,139.2 ms with 19,923.799999997 ms residual. The P07 residual is A* plus publisher/state work, not pure A*. P07 RouterSetup is nested in SearchSetup and is not subtracted twice. P05 validators have zero calls because all three P05 attempts reject before reaching them, not because validation was disabled.

Gates: native 9 completed in 39.004 s with 145 new collector assertions; GWT 5 completed in 80.934 s; A07, disabled-normal, and 3 strict-negative canaries PASS. Two full non-timing comparisons PASS with 29 metadata mutations per cold/warm arm and 43 strict negatives per arm.

Physical route outcomes are retained from raw reports: all three P05 attempts reject; P07 evaluates attempts 3, 1, 4 as reject, success, reject and selects attempt 1. Exact raw payloads are stored for the three measurement rows and two canaries. Strict wrappers/readers and host, runtime, process, input, and cleanup receipts are stored separately. Inspection copies redact personal paths; raw reports and exact source payloads are not reserialized.

The original sequence reader failure and pair-1 failure remain preserved. The accepted pair-1 and pair-2 reader-r2 validations document the narrowly scoped argparse Path-to-string repair. The source audit, 11 changed overlay payloads, parent map, measurement plan, source-review originals/correction, root interpretation/helper, gates, and helper references are inventoried with source, payload, and stored hashes.

Limits: one profile observation with two controls; code shape and timer impact are not separated; no zero-overhead or precise production-cost claim. Application elapsed/proof/routing fields are wall-clock values while profile scopes use monotonic performance.now boundaries. All cold rows exceed the frozen normal 90,000 ms deadline. No build, browser, full Q30 matrix, or normal acceptance gate was run by this packet worker; root owns final review and publication.
