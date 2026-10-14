# Verifier raw-byte audit

The recorded raw SHA-256 for `scripts/verify-current-contracts.ps1` is
`2ba96234766479964d68ca3acdc5dd11100fe86867b51fa97090157dfbcf013f` in
`nearest-vacancy-preference-r1/final-inputs.json`. The current worktree bytes
hash to `246ed496842315d533e18dbf0a1b768761fbbb3e2f391ba56a15d3dca34827c3`.
Converting the current file to LF yields the accepted HEAD blob exactly
(`4b8043de5cfa73fa3ba97eb15127f7a4a0dbf0d701f05520785db3cc2e832a62`), while
the checked-out file is uniformly CRLF.

`recover_script_eol.py` tested current, all-LF, all-CRLF, recent verifier
registry-line variants, and 65,536 combinations of the 16 most recent
line-blame groups. None matched the recorded raw hash. This is an unresolved
raw-byte provenance mismatch; it does not establish that the recorded input
content differed from HEAD. No production, verifier, or test source was changed
for this diagnosis. The root will establish fresh qualification inputs from
the reviewed canonical checkout rather than infer or relax the old hash.

See `script-eol-recovery.json` for the measured hashes and candidate counts.
