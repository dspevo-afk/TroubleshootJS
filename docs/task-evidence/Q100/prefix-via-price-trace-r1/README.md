# Canonical via-price trace and cap audit R1

The two accepted traces first differ in search metrics at branch59, then in the
recorded geometry fingerprint at branch60. Their first231 branch identities
match. This is a finite source/receipt diagnosis; the treatment remains
**NOT_ADOPTED**, with no production change or playable100 layout.

Implementation46124173140938400eb4820ae36474a1ded5a337 normally pushed to
origin/codex/q100-physical-feasibility, exact SHA verified; completion
Gmail1a1ae9c49b9d2305 SENT_ONCE. The [publication receipt](PUBLICATION.md)
sends no second email.

[Result](result.json), [qualification](qualification.json),
[frozen plan](PLAN_R1.md), [source contract](SOURCE_CONTRACT.json),
[validation](VALIDATION.md) and [failures](FAILURES.md) preserve the boundary.
Inputs are exactly the two authored request0/attempt4 receipts from the
[published comparison](../prefix-via-price-first-request-r1/README.md),
under layout26/dependency28. No native generator or new seed was run.

## Recorded divergence

- At zero-based branch59, both identities are pass0/BIAS_AC/RENAC.1/CBIAS.1.
  Their prefix count H is3. The recorded geometry digest still matches, while
  branch expansions are2486(BASE) versus2619(RAMP), with other search metrics
  differing. Formula-derived branch prices are60 and63; prices were NOT_EXPORTED.
- At branch60 the geometry digest and byte length first differ. The digest hashes
  the UTF8 geometryFingerprint serialization of layout/regions/placements/pads/
  labels/traces/holes. It does not certify complete physical or search-state
  equality; the original geometry strings were not exported in these receipts.
- At branch231 identities first diverge: BASE pass2/D_SENSE/U2D.SENSOR/CFLT_D.1,
  RAMP pass1/D_SENSE/RPIN_D.1/CFLT_D.1. Later positions are not treated as matched
  branches. Repeated identities and equal digests are never deduplicated.

An independent selected PS5 parser reaches the same59/60/231 findings and branch/
expansion counts. All308 BASE rows sum to918222 expansions; all314 RAMP rows sum
to1000000. Cumulative counters, final totals and histograms reconcile with the
unchanged operation reader and prior qualification.

## Terminal budget facts

| Profile | Last net | H | Branch-start hole headroom | Maximum new branch flips | Expansions in branch | Frontier | Outcome |
|---|---|---:|---:|---:|---:|---:|---|
| BASE | A_COIL_LOW | 48 | 0 | 0 | 25 | 0 | NO_PATH |
| RAMP | C_COIL_LOW | 38 | 10 | 4 | 53998 | 30790 | TOTAL_SEARCH_LIMIT |

H is the snapshot's prefixViaCount, sourced from total holes.length; the capacity
predicate uses that same total. Headroom is measured from the branch's starting
prefix, not a remaining budget for any particular frontier node. RAMP's global
search cap is exhausted while its comparison remains incomplete. These are
different branches and establish no ten-VIA saving or feasible continuation.
Neither case proves whole-board impossibility, causal attribution or a valid
alternative path. The next milestone is a separately frozen canonical fixed-prefix
capture/reachability scope before another treatment.

The generic CLI is `python -B audit_trace.py BASE RAMP QUALIFICATION NEW_OUTPUT`.
It accepts only these pinned historical inputs and adjacent frozen plan/contract,
rejects incompatible input before output mutation and writes a bounded CREATE_NEW
artifact. Local source/transport paths and uncontrolled logs remain external.
Production/GWT/Player/electrical/population qualification is not renewed.
Full30 remains owner-excluded; HARD/PSYCHOTIC unavailable/unread/unrun here,
historical protected boundary UNCERTIFIED; playable100=0/betaHELD.
