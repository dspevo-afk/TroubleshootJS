# Q30 epoch 15 native full and append evidence

This is a scoped evidence packet for the audited native-full and native-append contract batches. It does not establish overall Q30 acceptance or authorize enabling/publishing Q30.

- Base HEAD: `d37c3b3b8581abdeaa9b88b7bfb2349cff958d31`.
- Candidate source identity: `a43f8165921f08c309636d2bf1a971905fdbc9382885c0708788d79744e1fb51`; layout epoch 15.
- Frozen source inventory: 1354 raw files; all hashes were checked before and after archive creation.
- Normal-player Q30 catalog: disabled according to the bound source audit.
- Frozen plan: 77 unique structural roots (native-full 51 + native-append 26); 21 service/sensitivity roots.
- Limits audited from the per-root receipts: 90000 ms/job, 640 shared work units, 5000 ms/active operation.

## Terminal batches

| Batch | Suites | Structural rows | Native audit | Independent cleanup | Wrapper elapsed | Cleanup elapsed |
| --- | ---: | ---: | --- | --- | ---: | --- |
| native-full | 82 | 51 (47 accepted, 4 explicit rejects) | PASS | PASS | 4035.361 s | NOT RECORDED separately |
| native-append | 1 | 26 (23 accepted, 3 explicit rejects) | PASS | PASS | 196.324 s | NOT RECORDED separately |

Native-full also audited 105/105 service and 105/105 sensitivity cases and the independent seed/value/role, report, and listener receipts. Seven parity/seed/role/value sidecars are retained.

The 77 structural cases are reported once as the 51+26 partition; this packet does not multiply that count into 308 or label these native contract batches as cold qualification timing. Wrapper elapsed is the recorded batch duration; the maintained wrapper reports cleanup elapsed as NOT RECORDED separately, and no cleanup duration is inferred from timestamps.

The archive contains sanitized terminal result JSON, logs, complete receipts, independent native audits, wrapper and live-child ownership samples, cleanup reviews, the exact plan, both 1,354-row source-hash inventories, source audit, and audited parity sidecars. Each member records raw and portable SHA-256; the whole tar.gz hash is in the adjacent manifest. Source file bodies, `.git`, caches, screenshots, profiles, and global process inventories are excluded. Personal absolute paths and credential-like values are scrubbed; raw evidence remains in the task root.

Cold77, compiled31, menu/replay, archive/visible QA, and independent parent acceptance are separate gates. This packet claims none of them and makes no publication claim.
