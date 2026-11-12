# Historical Source tool metadata

The accepted Source preflight from commit
`5c745c3ff78cc16e19220b6c4a46ce541bdb58ad` provides a bounded reference for old
tool declarations. It does not establish which tools produced captured N/D
receipts. This inspector authenticates that reference separately from current
tool observations and keeps those limitations in its result.

Three exact Source-control artifacts are eligible: config4894B/c8e937,
Source manifest2124B/e5ea5a and preflight evidence1595B/8244eb. All three byte
buffers must match their independent pins before any JSON decoding. The pure
validator checks config/spec shapes, ordered Source/tool facts, cross-document
hash/root/count bindings, and the preflight's zero body/resource observations and
closed journal/data phase. Paths in the config are metadata; they are not followed.

The resulting `tsj-q100-source-tool-snapshot-metadata-v1` value identifies its
origin as `ACCEPTED_SOURCE_PREFLIGHT_ONLY`. It contains the accepted commit,
document digests, allowlist/root digest, ordered path/byte-count/hash tool rows and
a canonical snapshot digest. Capture receipt binding, prior-run binding, fixed-role
read authorization and prior qualification are all explicitly false. These are
Source observation metadata, not live tool observations or a permission/token.

`inspect_historical_tool_snapshot()` first authenticates the current configured
runtime and Source preflight. It uses the existing reader for the three named
metadata files, retains their original read tokens, and rechecks their bytes and
identities plus current config/Source/tools/root bindings before returning. It
requires an empty data-output directory and writes no manifest, journal, attempt,
context or producer Source. Existing 1MiB config/manifest and64KiB journal-domain
bounds apply; no reader policy or cap is widened.

The current preflight recheck compares source/tool byte facts across observations.
It does not retain their file identity tokens between observations. The three
snapshot files do retain those tokens. Neither check establishes an atomic
namespace, continuous identity, launch authority or complete tool bootstrap.

The CLI `inspect-tool-snapshot` uses current `--config`/`--config-sha` plus explicit
`--historical-config`, `--historical-source-manifest` and `--historical-preflight`
paths. It prints a path-free metadata summary. The in-process strict boolean
`include_tool_snapshot=True` adds the pathful DTO for a trusted consumer; the CLI
has no pathful-output option. This opt-in does not turn the DTO into authority.

The current preflight manifest retains its existing meaning and fields. The
inspector does not feed the snapshot into `_make_normalizer_authority()` or
`_validate_prior_tool_compatibility()`. The N66 guard continues to reject the
current/historical mismatch before prior manifest/journal/role reads. A later
qualified contract must bind separately attributable tool expectations to exact
captured receipts, prior run/source identities, roots and the allowlist before
that guard can be replaced or any role read permitted. Native Java's prior
source-manifest binding remains required after any separately authorized decode.

No prior qualification positive, actual fixed-role body, oldD/held-out/protected
DATA, actual DATA factory or genuine operation. Both Study holds and original to
NULL/OBSERVE remain closed. Full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun;
protected-unread UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD.
CircuitJS truth, seeds/epochs/order and all product budgets remain unchanged.
Production GWT/Player N/A; electrical/population NOT RENEWED; CI NOT RUN pending
the self-hosted runner prerequisite.
