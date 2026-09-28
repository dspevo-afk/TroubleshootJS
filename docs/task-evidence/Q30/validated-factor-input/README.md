# Validated factor-input optimization

Accepted intermediate optimization; **Q30 remains BLOCKED / NOT ACCEPTED**.
Normal publication stays disabled. No push, later milestone, final acceptance
matrix, deadline change or scale-completion claim is included.

The sole production change adds a private nonlinear LU entry for the matrix
restored from finite-scanned origMatrix and updated through checked stampMatrix
writes. It removes duplicate per-cell finite predicates. Every zero-row check,
the common numerical factor body, general LU input validation, solver settling
and qualification proof remain. The [independent review](reviews/validated-factor-independent-review.md)
and [producer audit](reviews/finite-validation-audit.md) explain this ownership
invariant and its limits. Strengthened A07 canaries verify invalid baseline and
dynamic stamps, overflow phase, zero solves/accepted steps/sample publication,
and restoration without forcing a snapshot restore before its assertion.

The predeclared comparison used unaccepted plan-4 working source at base
e7a73e7fb0ce824cd51675e383225c50be2124d2. Each arm has 1,338 audited inputs.
Both include the same r3 A07 test update; only CirSim.java differs between arms.
No sampler was enabled. Each row starts a fresh browser process/context and
records cold qualification, its separate private-cache warm measurement, stable
inputs, all five host-error arrays and owned cleanup.

| Run | Arm | Seed / packages | Cold ms | Proof ms | Routing ms | Warm ms |
|---|---|---|---:|---:|---:|---:|
| 01 | candidate | 7 / 37 | 81,137 | 61,106 | 14,639 | 20,014 |
| 02 | control | 7 / 37 | 83,558 | 63,390 | 14,594 | 20,015 |
| 03 | control | 10387 / 20 | 25,019 | 17,276 | 5,761 | 7,569 |
| 04 | candidate | 10387 / 20 | 24,760 | 16,946 | 5,849 | 7,611 |
| 05 | candidate | 10014 / 40 | 103,346 | 74,354 | 22,468 | 28,679 |
| 06 | control | 10014 / 40 | 105,988 | 76,686 | 22,498 | 28,865 |
| 07 | control | 7 / 37 | 83,622 | 63,551 | 14,519 | 19,994 |
| 08 | candidate | 7 / 37 | 81,369 | 61,409 | 14,530 | 19,711 |

Paired cold savings are 2,421 / 259 / 2,642 / 2,253 ms, median 2,337 ms.
Proof savings are 2,284 / 330 / 2,332 / 2,142 ms, median 2,213 ms.
Routing differs by at most 88 ms per pair. Repeated seed-7 cold ranges are
64 ms control and 232 ms candidate; proof ranges are 161 and 303 ms.
This supports the repeated gain above two seconds. The single 20-part
330-ms proof difference alone does not establish a population gain.
The 40-part candidate still exceeds the frozen 90,000-ms production deadline.

All four complete-report comparisons pass outside explicitly enumerated timing
fields. Every cold run keeps five hypotheses: seed 10387 uses 230 hypothesis
work units, while seeds 7 and 10014 use 390. Warm private proof-cache hits use
one work unit; they are not factor reuse or normal proof-cache reuse.
Electrical values, physical mapping, deterministic identities, work, hypotheses,
service/retest proofs, cache isolation and cleanup remain exact.
The current strict readers execute their actual 43/44 negative cases on both arms.
Application fields retain their original wall-clock meaning; host duration and
wall-minus-monotonic drift are measured separately. These are focused private
comparisons, not the normal cold acceptance corpus or a margin guarantee.

Final working-candidate native4 PASS: 44.459 s; candidate/control production
GWT five-permutation builds PASS: 78.499 / 78.009 s. Both compiled A07 and
disabled-normal canaries, exact A07 readers and three corruptions PASS.
The standalone staged-index source has a separate boundary: committed plan-2
baseline, tree 886cb6d68e41dfb0906462c3c092442864fdb842, 1,337 inputs
(1,328 Git blobs plus nine pinned JARs). Native4 PASS 48.284 s, GWT5 PASS
80.962 s, compiled A07/strict/three corruptions PASS, host cleanup PASS 0.877 s.
The unaccepted normal-verifier API is absent from that source.

Root's working-source production refresh PASSes in 78.540 s. Its 1,525 consumed
inputs and complete 392-file WAR match the measured r3 candidate byte-for-byte,
which supports reuse of those compiled canaries for this identical artifact.
This does not qualify the surrounding plan-4 implementation or normal admission.

Failures remain visible: the r1 forced snapshot restore was rejected and removed
before gates; r2 native/build gates passed but both compiled arms failed the new
factor-overflow canary because simplification moved its stamp into the RHS.
The r3 fixture retains the DC source branch before simplification, reaching LU
overflow with the original factor=1/solve=0/accepted=0/samples=0 assertions.
No failed run is relabeled PASS.

The [plan and results](plan/sequence-result.json), per-run directories, gate
receipts and reviews retain the source and host boundaries. Raw application
reports and changed source overlays are byte-exact gzip. Baseline Git blobs plus
27 content overlays and 1,279 exact newline transforms reconstruct each arm;
the candidate adds only its CirSim overlay. Nine pinned JARs have path/size/hash
records; their bytes and generated WAR/browser profiles are excluded.

The executed current-plan4 validator is
[validate_current_q30_timing_pair.py](reproduction/validate_current_q30_timing_pair.py),
SHA-256 67430d7c56d872d115a7c38732bf60101cff52e8bf40f7af075213debc8e7e3b.
Its consumed current readers are preserved under reproduction/consumed.
The runner/sequence scripts preserve the actual commands; sanitized local-path
placeholders and original task-temp filenames must be rebound when reproducing.
An earlier plan-2 reader accidentally included during packaging was removed;
it was not the validator executed for these eight runs.

Root independently checked inventory and compressed bytes, personal-path removal,
both complete source reconstructions against the original fixtures, and all eight
raw application reports. inventory.json covers every stored artifact except
itself and inventory.sha256; that file is an unkeyed checksum. Metadata is
sanitized and its original/stored hashes are recorded separately.
