# Q30 CDP CPU profile evidence

This bundle preserves one sampled Q30 coordinator run, its immediate fresh
unprofiled comparison, a small A07 profiler control, exact source/map identity,
and the failed profiler canaries. The captured browser target reached
`RUNNING:warm`; the cold and warm application reports both passed.

The profiled B7 run recorded cold elapsed **100,442 ms**, diagnostic proof
**71,720 ms**, and active routing **22,796 ms**. The immediate fresh unprofiled
B7 run recorded **87,473 ms**, **61,987 ms**, and **20,234 ms**, respectively.
Both exercised the same five hypotheses, 390 diagnostic proof units, and 468
generation jobs. Warm proof reused one cached entry and reported 79 work units;
the warm proof duration was 0 ms in both reports.

This is one paired timing comparison. The profiled cold run was 12,969 ms
slower, but sampling/debugger overhead and host variation were not separated.
Treat the profile as qualitative hotspot-ranking evidence, not absolute stage
costs or a deadline result. Existing low-intrusion stage profiles remain the
authoritative timing evidence. Inclusive stacks overlap and must not be summed.

The sampled profile ranked LU factorization at 34,258 ms inclusive and
23,061 ms self time, matrix stamping at 8,406 ms inclusive, LU solve at
7,798 ms self time, publication at 7,973 ms inclusive, and routing at
18,751 ms inclusive. The 1 ms capture covered 101.538 s, with 66,468 samples;
63,554 GWT samples mapped through the validated symbol map. Capture started
before navigation and stopped after the host observed `RUNNING:warm`, so a
short warm-boundary tail may be present.

The measured path was developer-only Q30 qualification with its isolated
300,000 ms deadline. **Normal 90,000 ms acceptance was not run or established.**

## Runner and source identity

The frozen temporary profiler runner had SHA-256
`b1586acaf9c76a0b29712b53b70bbb3cb086d0ff14056162d368018eede11fb6`. Its
maintained base, `tests/browser/compiled_attribute_acceptance.py`, had SHA-256
`4d0e724e563ff709397dffbddc200d7f52a1eb7ddff8cc68301fffcbe0deddf5` and was
unchanged during the runs. The runner delta was limited to 1,000 μs V8 CPU
sampling on the page target, host-observed warm-boundary stop, passive
`Debugger.scriptParsed`/script-source provenance, and validation against the
loaded GWT payload and its exact symbol map. It still accepted the application
through the existing DOM attributes; it did not call application controllers.
`runner.patch` is the actual unified diff from the maintained harness to the
frozen runner. `runner-patch-verification.json` records the baseline, frozen
runner and reconstructed SHA-256 values; applying the patch to the maintained
base reproduces the frozen runner byte-for-byte. Patch headers use sanitized
relative names and no source-path transformation was needed. The captured
in-page GWT source is not included.

For both Q30 runs, the input, source-tree, compiled-web, and runner hashes were
unchanged before and after. The sampled GWT bundle was linked by exact decoded
payload bytes, not by function-name guessing. The corresponding raw V8
profiles are gzip-compressed under `profiles/`; the exact used compressed
symbol map is included alongside them.

## Canary record

The initial A07 positive profile reached application `PASS:a07` but failed
profile attribution because no sampled GWT function matched a unique map row.
The initial negative run rejected the unsupported CDP method and cleaned up.
The corrected A07 positive run passed with exact payload-to-symbol-map
provenance; the negative r2 run retained the expected unsupported-method
failure. All four outcomes and their input/cleanup audits are retained in
`canaries.json`; failed canaries were not dropped.

`profiled-run-reader.txt`, `fresh-control-reader.txt`, and `proof-parity.json`
are compact reader outputs. `application-profiled-b7-report.json.gz` and
`application-control-b7-report.json.gz` preserve the actual full application
reports byte-for-byte (with deterministic gzip metadata) so a strict reader can
recheck cold/warm values and diagnostic evidence. `execution-receipts.json`
retains sanitized wrapper outcomes, operation timings, unchanged-input hashes
and cleanup receipts; `runner-delta.json` links the exact patch and verification
record. Raw wrapper JSON and host process inventories are not included. Raw V8
profiles and the exact used symbol map remain under `profiles/`.
