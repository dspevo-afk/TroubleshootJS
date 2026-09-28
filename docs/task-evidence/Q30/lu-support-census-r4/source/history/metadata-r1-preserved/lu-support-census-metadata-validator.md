# LU support-census metadata validator

This source-only helper is the independent reader for the private Q30 LU
support census.  It exposes `validate_report(profile, phase)` for `phase` equal
to `cold` or `warm`, plus `selftest()`.  A full raw Q30 report must contain the
matching `coldSupportCensus` or `warmSupportCensus`, declare
`supportCensusRequested: true`, and retain the exact query marker
`tsjQ30SupportCensus=true`.  An extracted census object with the exact census
field set is also accepted for a wrapper that has already selected and audited
the full report.

The validator is fail-closed.  It requires the finalized schema field names
`scopeComplete`, `completeCapture`, `ipvtTraceComplete`, and `ipvtPrefix`; all
Java long counters must be canonical signed-long decimal strings.  The
selection interval is exactly 4096, the selected count must equal
`floor(eligibleFactorCalls / 4096)`, and selected samples must be nonzero and
no greater than the producer cap of 256.  A complete accepted capture requires
`valid`, `frozen`, `scopeComplete`, and `completeCapture` true, every selected
factor successful, every trace complete, a complete sample array, and zero
failed, dropped, scan, diagnostic, lifecycle, and overflow counters.  The
runtime's unavailable fallback observation remains explicit: top-level and
opportunity `zeroPivotObservationAvailable` must be false, and `exact` must be
false.  A sentinel hit is retained as a risk fact; it is not converted into an
observed fallback.

For every sample it validates the bounded matrix size (`0 <= n <= 512`), the
exact defensive `ipvtPrefix` length, and each domain condition `k <= ipvt[k] <
n`.  It independently checks graph labels, component IDs, sorted row/column
members, component rectangle edge bounds, nonfinite counts, and the component
edge sum.  It independently recomputes the two-direction stable-partition
bijection.  Support added/removed/unchanged counts must reconcile with current
and original edge counts; value differences must cover support changes; the
five-pass cell-read count must equal `5*n*n`; and the report-level cell-read
sum must equal its samples.

The opportunity reader replays the supplied `ipvtPrefix` over copied row
component labels using the producer's order: pivot and lower domains are
counted before the current row swap, upper domains use the selected row label
against later columns, and pivot/lower domains remain overlapping metrics.  It
recomputes full and local candidate counts, cross-group pivots, and zero-risk
flags and compares every serialized value.  It never sums overlapping metrics
or interprets structural counts as elapsed time, numeric work, allocation, or
savings.

`selftest()` uses one synthetic stable two-component `n=3` capture for both
cold and warm phases, then checks 14 mutations: numeric and leading-zero long
forms, invalid capture flags, failed/sample-count metadata, component members,
pivot domain, replay counts, partition stability, query identity, an
unsupported exact claim, and an incomplete IPVT trace.  This is only a Python
reader check; it is not evidence from Java, GWT, a browser, a host, or a real
LU run.  Failed or incomplete runtime metadata is rejected here; the pair
wrapper remains responsible for preserving its raw report and explaining the
failure rather than treating it as accepted evidence.

## Frozen provenance

The validator is intended for the finalized wired draft whose source hashes
are:

- `Q30LuSupportCensus.java` SHA-256
  `1C2AEAA150A766F4E6DCDDACAA9F8865B58274F9E160294D90B8073646BFD5B5`
- `Q30CoordinatorQualificationVerifier.java` SHA-256
  `E610DE476DF73C4F303E28182BFFE8E4BAE2893E62DB69E10341BE7DF9C777E1`
- `source-manifest.json` SHA-256
  `B1B0EEA88EEB71C12F1FBC757289CFF722FB33689BABE13A69F02062FE8EFE17`
- `lu-support-census-metadata-validator.py` SHA-256
  `E0DFC6E12D40EE0A19D78CB18F7999B7AFDC1916D190F26F1252E96090E1C23D`

The collector and verifier are source-only artifacts.  No host, browser,
build, native contract, or qualification run was performed for this helper.
The root task must independently validate report placement, full Q30 status,
cleanup, parity, and any real execution evidence around this metadata reader.
