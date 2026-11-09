# R1 independent projection oracle

This reader checks only the frozen current-result-projections canary summary. It
does not validate or claim full Source acceptance.

## Independent expectations

The cohort expectation is rebuilt from the literal synthetic `SYNTH_N00` through
`SYNTH_N59` capture vector: `SYNTH_N37`, then `SYNTH_N11`, then every remaining
ID in original capture order. The expected compact cohort JSON is generated from
that independent vector.

Cleanup expectations use the ten declared output fields and literal
`ORIGINAL_CAPTURE_COPY_ONLY` scope. The three Source cleanup durations sum to
600; the scalar-below-sum fixture remains 599 and is expected to fail the
separate Source semantic check. The nine false cleanup projections remain
outputs and are marked `sourceSemanticRejectionExpected`; that marker does not
mean the projection itself accepted them.

The expected result census is kept in the oracle source and checked against the
three digest-pinned root fixtures. The reader never uses observed payloads,
counters, names, or clocks as expected values. Root fixture pins are:

- `PROJECTION_CONTRACT_R1.md`: `24f40e39a9d52704a51aefbdf05170c25056447009c4b25605e45ad9de04fd4f`
- `REFERENCE_R1.json`: `bd0c6f081a6a9d11c47befe531debc87ddbccf02541542253b202a6debe491bc`
- `CANARY_WIRE_R1.json`: `b5b20358c18806889b53c8394be627bd5078f0fd7c282e09e285f0f60a357b59`

The wire top-level and case key orders are exact. The compact child payload
strings are compared byte-for-byte. Boolean values are not accepted as integer
counters. `operationNanos` is a nonnegative signed-long fixture-inclusive
duration with no 90-second cap; total meter age is capped at 90 seconds and
group/open-group age at five seconds. A closed meter must report zero
open-group age. Pending expected failures must retain their exact partial
counters and discarded-meter disposition.

## Source pin and use

After source review, the root owns
`ROOT_ORACLE_SOURCE_R1.json` beside these files. Its required form is:

    {
      "status": "ROOT_ACCEPTED_PROJECTION_ORACLE_SOURCE_BEFORE_EXECUTION",
      "files": {
        "projection_oracle.py": "<lowercase sha256>",
        "verify_projection_r1.py": "<lowercase sha256>",
        "test_projection_oracle.py": "<lowercase sha256>",
        "ORACLE_NOTE_R1.md": "<lowercase sha256>"
      }
    }

The reader validates the four source hashes, then compiles and executes the
oracle from the exact authenticated raw byte buffer with `dont_inherit=True`
and `optimize=0`. It does not import the candidate source or use a pycache.
Reference inputs and observed summaries are read through bounded, duplicate-key
rejecting JSON paths with no-follow/reparse checks.

The CLI requires all three explicit arguments and accepts only `smoke` or
`suite`:

    python -I -B verify_projection_r1.py --case smoke --input-json <summary.json> --reference-root <fixture-dir>

It emits one compact JSON line. Successful verification returns
`PASS_INDEPENDENT_RESULT_PROJECTIONS`; rejection returns an allowlisted code
and exit status 2. The root should execute it only after accepting and writing
the source pin.

The pure test entry point accepts the reference root and unittest flags:

    python -I -B test_projection_oracle.py --reference-root <fixture-dir> -v

It contains 15 tests. Positive summaries are synthetic constructions from the
root reference/wire fixture using fixed 1,000-nanosecond clock values. They do
not run Java or represent native canary execution. The root owns all execution,
native gates, and final source-pin creation.
