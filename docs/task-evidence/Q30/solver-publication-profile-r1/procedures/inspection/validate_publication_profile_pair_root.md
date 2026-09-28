# Q30 publication-profile pair validator

This reader composes the frozen current-plan-4 timing adapter with the
source-bound publication-profile metadata validator. It reads completed
candidate/control artifacts, runs the adapter's host and strict-reader checks,
and writes only a new pair summary. It does not start a host, build, browser,
or measurement and makes no production or zero-overhead claim.

Frozen dependencies:

| Dependency | SHA256 |
| --- | --- |
| `validate_current_q30_timing_pair.py` | `67430d7c56d872d115a7c38732bf60101cff52e8bf40f7af075213debc8e7e3b` |
| `solver-publication-profile-metadata-validator.py` | `2504b4e478636bb60b98e195c94a371de89734a563ca6e7bd392670c71771dfd` |

The candidate arm uses an in-memory allowlist only for these report paths:

```text
/publicationProfileRequested
/publicationProfileQuery
/coldPublicationProfile
/warmPublicationProfile
/cold/publicationProfile
/warm/publicationProfile
```

The candidate query must contain exactly `tsjQ30PublicationProfile=true` as its
only profile query. Controls use the frozen adapter unchanged and must have no
publication-profile fields or profile query. Candidate cold/warm nested
snapshots are validated with `validate` and `self_test`; each root duplicate
must compare exactly with its nested `publicationProfile` before any projection.

The strict reader receives both original parsed reports, including the
candidate profile fields. Only afterward are the six allowlisted candidate
fields removed from a deep comparison copy. The preserved adapter then removes
its declared timing fields. All remaining report values must compare exactly;
raw report bytes and SHA256/length are rechecked after validation.

CLI:

```text
python -B validate_publication_profile_pair_root.py \
  <scratch> <repo> <candidateLabel> <controlLabel> \
  --tag <fresh-tag> [--output <new-summary.json>]
```

Optional `--timing-adapter` and `--metadata-validator` paths are accepted, but
their bytes must match the frozen hashes above. Source, runtime, gate, and
report binding identity checks remain owned by the root execution binding.
