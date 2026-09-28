# Q30 solver publication profile validator

This validator is a source-bound, report-local reader for the private solver
publication probe. It does not start a host, run a browser, compare controls,
remove report fields, or certify electrical proof.

The observed source inputs at draft freeze were:

| Source | SHA256 |
| --- | --- |
| `Q30SolverPublicationProfile.java` | `6b11cbcb7913a254a712edaff290eb64e5c8c21744f9ed2e094f102d70796afe` |
| `Q30CoordinatorQualificationVerifier.java` | `0923d722da643ea7b3174775b1c076f89c8b1abdc402e936b4de8032c3b04bdd` |

If either source changes, recompute the hashes and inspect the JSON-producing
code before using this validator. A schema change requires a validator change;
the validator must not silently accept a new shape.

## Accepted snapshot

The closed root object has exactly these keys:

```text
sampleInterval, valid, frozen, clock,
timerErrors, lateErrors, unknownPathErrors, openSamples,
eligible, selected, completed, failed, sampledElapsedMs, phases
```

`sampleInterval` is integer `512`; `valid` and `frozen` are boolean `true`.
All counters are nonnegative integers. The four lifecycle/timer error counters
are zero, and `clock` is `performance.now` exactly when total selected samples
is positive; otherwise it is `not-observed`.

`phases` is an ordered six-element array with these exact names:

```text
applySolvedRightSide
rollbackVoltagePublication
wireRefreshImmediateAcceptedStep
wireRefreshDelayedOwnedAcceptedStep
wireRefreshDeferredUiBatch
acceptedStepBookkeeping
```

Each phase object is closed and contains `name`, `eligible`, `selected`,
`completed`, `failed`, `elapsedMs`, `zeroDurationCount`, `timerErrors`,
`rawDurationCount`, `rawDurationsDropped`, `counterOverflow`, and
`rawDurationsMs`. Selection is exactly `floor(eligible / 512)` and
`completed + failed == selected`. `timerErrors == 0` and
`counterOverflow == false` are required.

The raw contract is `rawDurationCount == min(selected, 64)`,
`rawDurationsDropped == selected - rawDurationCount`, and the array length
equals `rawDurationCount`. Durations are finite and nonnegative. Their sum is
at most `elapsedMs`; when no durations were dropped, it must equal `elapsedMs`
within `max(1e-9, 1e-9 * max(1, abs(left), abs(right)))`. Zero-duration counts
must equal visible zero samples when undropped, and otherwise remain between
visible zero samples and visible zero samples plus dropped samples.

Root totals equal the exact integer phase sums, and `sampledElapsedMs` equals
the phase `elapsedMs` sum within the same tight tolerance. The result records
the observed sampled sum and never extrapolates a whole-operation duration.

`validate(profile, label)` returns compact validated totals and per-phase
counts. `self_test(actual_profile, label)` first validates the actual snapshot,
then rejects mutations covering duplicate/missing/unknown phases, missing and
extra keys, wrong types, nonfinite and backwards elapsed values, count and raw
array mismatches, invalid/drop/raw/clock/open/late flags, and total mismatch.

## Read-only report CLI

The CLI reads a completed application report and validates both duplicate
snapshots without rewriting it:

```text
python -B solver-publication-profile-metadata-validator.py \
  <raw-report.json> --label <row-label>
```

For each of `cold` and `warm`, it requires exact equality between
`<phase>.publicationProfile` and the terminal duplicate
`<phase>PublicationProfile`, then runs the snapshot validator and its real
corruption canaries. The application report's original proof and lifecycle
checks remain the responsibility of the strict application reader.
