# Frozen allowlist admission correction

Accepted locally; normal commit/push and one correction completion email pending.
Final proof SHA256 `ac3476c8968fcdee0de34eaa122cf86f8bf163c023da38adfad877d1dc6a60ee`.

Caller integration exposed a real defect in the preceding normalizer: its
allowlist validator expected counts/fields/oracleBodiesRead/sourceSha256, which
the frozen public metadata does not contain. It also rejected the null summary
limits on six D qualification receipt rows. The previous56 synthetic tests and
source review did not establish this actual-metadata positive. Actual normal-prior
qualification had not been claimed and no prior role body had been opened.

The original public metadata canary returned exit2/ALLOWLIST_KEYS. Its command,
source/input hashes, raw receipt hashes and timing remain in
[preserved failure](PUBLISHED_METADATA_FAILURE_R1.json). The nested call's
PASS_EXPECTED_REJECTION means the reproduction harness observed the requested
failure; the intended positive metadata gate FAILED. It is never normal-prior
or component admission evidence. Operation462.804ms/wrapper721.576ms; isolated
cleanup duration unmeasured. No product oracle or published metadata was changed.

The fix changes only _validate_allowlist_bytes. It uses the actual frozen
authoritySourceSha256/helperSourceSha256 envelope, preserves their exact source
pins and permits null summaryParseMaxBytes when expectedBytes is null. The actual
SHA-pinned metadata has seven D qualification rows: six null limits and stdout1024.
The metadata SHA remains c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91.
All other normalizer module AST nodes are unchanged; the API, role hashes,
auth-before-decode order, semantics and policies stay intact.

Corrected normalizer SHA256
`e8725dff3be9a1d8cf136c7ac89778ffcb0aefd54e809538caad8dcf7143aaa8`.
New regression SHA256
`85a9cf129922a42bddb3665c6488c443d8ae5cf904137d498936ac4c7e9f4455`.
Use this current source; source582422e5 in the preceding private workspace and
its evidence is historical. A scoped independent SOURCE review compared the
actual public metadata, corrected validator and unchanged fixed-role reader3114
and found no blocker. Runtime and real caller facts are separate qualifications.

Fresh corrected-source private and public suites each pass61 tests/0skips:
the prior56 synthetic cases plus five actual-metadata/private-mutation regressions.
The new cases authenticate the public metadata, check nullable D summary limits,
prove public admission reaches ROLE_BYTE_MAP with an empty role map and no callback
invocations, reject a public mutated digest, and reject private malformed schema/
summary mutations. The fixed digest is checked before any test decodes metadata.
No role path is opened or followed. Both private/public metadata CLI positives
match exactly; operation360.22/367.1105ms and wrapper604.0057/610.2044ms.
Regression5 takes376.3189/611.5727ms private and379.3681/620.3861ms public.
All selected processes return zero with proven termination and owned roots/logs
absent. Cleanup duration remains unmeasured. No new persistent process/link.

Code stays in tests/qualification/rb100-layout/current-context-kernel-r1. Repeat
the six commands in the preceding component packet, then the new regression:

```text
python -I -B test_normalizer_allowlist.py --allowlist <published-NORMAL_ORACLE_ALLOWLIST_R1.json> --normalizer-source normalize_prior.py -v
python -I -B allowlist_admission_canary_r1.py --allowlist <published-NORMAL_ORACLE_ALLOWLIST_R1.json> --normalizer-source normalize_prior.py --normalizer-sha256 e8725dff3be9a1d8cf136c7ac89778ffcb0aefd54e809538caad8dcf7143aaa8
```

These commands read public contract METADATA only, never actual20-role DATA.
The fixed-role reader, codec and kernel source/tool inputs recheck unchanged;
their older native/JDK/OS evidence is not renewed here. Production/GWT and visible
Player checks are NOT APPLICABLE; electrical/population proof is not renewed.
Actual source/callback/tool-module/root/resource caller facts and actual prior
acceptance are NOT RUN. No operational read-phase transition or genuine producer
invocation occurred. One original plus exactly NULL/OBSERVE stays unspent/gated.
Full30EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; historical protected unread
UNCERTIFIED; playable100=0/betaHELD; full-D/H1 unqualified. Caps/seeds/epochs/oracles
unchanged. Next implement/freeze the normal-only caller/read phase, qualify the
pinned prior and then assemble the current host/native path. Existing unrelated
work, source/synthetic fixtures, failure evidence and uncertain resources remain.
