# Final compiled developer census

PASS: ten terminal service receipts on one final source/web build; all ten tabs closed through Browser after capture. Normal and D01 admission remain false.

Independent reader: 37 samples per row (370 total), twenty distinct seed-local fault pairs, seven rejected negative canaries. See `receipt-checks.log`.

| Seed | Fault | Setup ms | Routing ms (within setup) | Service ms | Read timeouts |
| --- | --- | ---: | ---: | ---: | ---: |
| 0 | DREV_OPEN | 30312 | 20083 | 38705 | 3 |
| 0 | DRIVE_A_OPEN | 30768 | 21283 | 40300 | 3 |
| 0 | RELAY_B_COIL_OPEN | 29093 | 19861 | 64739 | 4 |
| 0 | REN_OPEN | 29526 | 20126 | 34358 | 3 |
| 0 | SENSOR_A_OPEN | 30333 | 21094 | 39996 | 3 |
| 37 | DREV_OPEN | 26475 | 16517 | 38159 | 3 |
| 37 | DRIVE_A_OPEN | 27439 | 17684 | 40704 | 1 |
| 37 | RELAY_B_COIL_OPEN | 25674 | 16657 | 59145 | 3 |
| 37 | REN_OPEN | 25982 | 16689 | 33518 | 4 |
| 37 | SENSOR_A_OPEN | 26883 | 17374 | 43427 | 0 |

Setup is the pre-service workbench qualification. Service is a separate asynchronous operation; it excludes tab cleanup. Read timeouts are transport observations, never inferred PASS results. No cold/warm distribution or p50/p95 claim is made.

Final build: `build-preparation-final.log` (JDK8/GWT5 PASS).
Source digest: `9f21cc68ed6488734ddc8f20d48152c17ea9b4b36bfb6b40e7f9d15d82314c90`.
Web digest: `9fce14f38cf45310bd520b3ab0e44346b930977d7e591c726cbb0b4b5dec0b47`.

Previous candidate failures and partial passes remain in `attempts.md`; they are not omitted or folded into this final-candidate census.
