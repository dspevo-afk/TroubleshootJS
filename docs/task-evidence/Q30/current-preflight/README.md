# Current private LU preflight profile — evidence bundle

This package records a **single-seed private diagnostic measurement**, not a production optimization or normal-player acceptance. The sampler adds observation only; the LU algorithm and factor result are not reused. The production Q30 normal catalog remains disabled. The separate 40-package normal-timing blocker remains unresolved; this package does not certify the 90-second acceptance bound or any full matrix.

## Candidate and input identity

The measurement plan was frozen before the run as `current-preflight-01-profile-10014` followed by `current-preflight-02-off-10014`, both seed `10014`. Each used a fresh Edge host context/process. The exact plan, both host summaries, strict-reader receipts, raw browser reports, six before/after input manifests, and final input audit are included. The final audit is PASS: all 1,339 source-only fixture inputs match the source audit, and the compiled canaries plus both measured runs have the same complete source/web/runner manifest hashes. The JDK8/GWT build is the maintained five-permutation final-source build recorded in `records/gwt-five-permutation-r2.json`.

The 1,338-input baseline is the reconstructed candidate arm of the committed [validated-factor-input bundle](../validated-factor-input/README.md), including its unaccepted plan-4 working changes. Commit `e7a73e7fb0ce824cd51675e383225c50be2124d2` supplies only its Git foundation. The linked manifest and reconstruction recipe are pinned in the overlay provenance; the bare Git tree is insufficient. `source/r2-source-overlay.tar.gz` contains only the exact three changed files and one added contract test; member hashes are listed in `source/source-overlay-provenance.json`. The full baseline source tree, generated WAR/JARs, browser profiles, and build outputs are intentionally not copied.

## Gates and outcomes

- Focused native sampler/solver/temporal/budget contracts: PASS, 47.4215832 s, exit 0.
- Maintained JDK8/GWT five-permutation build: PASS, 81.1025758 s, exit 0.
- Compiled A07 and disabled-normal canaries: PASS, 6.0823117 s; owned cleanup PASS, 0.9375114 s. A07 strict-reader negative cases: 3.
- Profile/off paired strict validation: PASS. Both arms report exact request and proof parity outside declared timing/profile fields; profile/off input parity PASS; each strict reader ran 43 corruption cases; the pair validator ran 51 metadata negative canaries.
- Both browser runs and compiled canaries report host cleanup PASS. The five browser error arrays are empty; the fresh pair's host monotonic durations are 134.6474503 s and 135.6555107 s. The two pair cleanup durations are 0.9033369 s and 1.1818034 s.

| Arm | Cold app elapsed | Proof | Routing | Work / hypotheses | Host result |
| --- | ---: | ---: | ---: | ---: | --- |
| Profile | 102,757 ms | 73,810 ms | 22,466 ms | 490 / 390 | PASS; no timeout; report matched |
| Query-off | 103,323 ms | 74,275 ms | 22,521 ms | 490 / 390 | PASS; no timeout; report matched |

The single pair is not an overhead or speed comparison. The profile arm was 566 ms faster in app cold elapsed and about 1.008 s faster in host monotonic time; one noisy pair cannot estimate instrumentation cost. The source review notes that copy/compare timing excludes the per-factor profile branch/counter and compilation effects.

## What the sample measured

Across 742,391 cold factors, the deterministic sparse sampler recorded 1,453 matrix traversals and 10,269,420 full cell reads. First-nonzero left-to-right scans would have probed 2,490,017 cells (24.2469%); diagonal-first scans would have probed 2,606,252 cells (25.378% and therefore more). The sample contained 122,114 rows, 148 nonzero diagonals and no exact-zero rows. The selected zero-row preflight timer accumulated about 10.6 ms over 1,453 selected calls.

The 5.416 s value in the final audit is a rough extrapolation from sparse deterministic samples using an uncalibrated browser `performance.now()` clock. It is not a direct cumulative measurement or a promised speedup. The preflight timer excludes workspace reset/clear, sample traversal and LU arithmetic; the separately reported 36.9 ms capture/compare bracket excludes per-factor branch/counter and code-generation cost. A possible next experiment is a left-to-right short-circuit zero-row scan that still checks every row; it is not implemented here.

## Reproduction and privacy boundaries

The exact plan, reports, validation receipt, source audit and run input manifests are hash-inventoried. Each `*.json.gz` raw report/manifest decompresses to the original bytes; the browser app reports are not redacted. Host result JSON copies have only local filesystem root strings replaced with descriptive placeholders; original hashes are retained. The runner and imported helper/audit scripts are path-redacted archival copies for privacy, while the exact r2 validator and recorded original dependency hashes are retained. See `validator/PROVENANCE.md` before attempting to rerun a validator; the redacted helper intentionally does not match the validator's frozen original hash.

`inventory.json` lists byte length and SHA-256 for every package file other than itself; compressed items also list uncompressed length/hash. No personal absolute paths are included; standard executable locations remain as host evidence. This bundle is evidence for the one-seed investigation only. It does not complete the full matrix or resolve the normal-player admission blocker.

The original sampler r1 sources are retained under `source/r1-unrun/`. Root review found that native early-return/exception cases proved timing started but not that it completed. R2 adds the completion counter before the native-clock early return and explicit 2/1/1 completion assertions. R1 was not run or accepted. The initial reader draft and its helper-API repairs are also retained; browser reports were not changed to repair the reader.
