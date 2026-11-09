# Qualification console framing correction

Accepted and published as `35e36eeec64319b35350e7ac6c488370bb241bee` for the parser correction and caller pin
migration only. Exact remote verified; Gmail `1a1beb3670d80f14` SENT_ONCE.
[Publication](PUBLICATION_R1.md). Actual prior qualification remains FAIL from the first
attempt and has not been rerun. Native producer/session calls remain zero.

The first fixed20 normal-only read followed the exclusive durable phase journal
created at2026-11-09T04:45:28Z. It exited2 with
NORMALIZER_QUALIFICATION_STDOUT_FRAMING in normalization. Authentication of all20
roles and resource observation were reached before that failure by published
caller control flow; this does not establish complete prior acceptance.
Operation724.3113ms, wrapper973.7947ms. The failed output root, exclusive attempt,
failure evidence, original config/manifest/journal and raw receipts are preserved.
No context/projection/Java DATA was produced. [Failure](FIRST_ACTUAL_FAILURE_R1.json)
and [journal summary](FIRST_PHASE_JOURNAL_GATE_R1.json) remain distinct from PASS.

Root diagnosis authenticated only the already-authorized normal qualification
stdout role through reader863. Its pinned812 bytes end with one CRLF. The summary
validator used the LF-only JSONL loader although the existing console adapter
already accepts a single LF or CRLF. The correction changes exactly that one
call site. The raw812-byte check,1024 cap, role hash,19 keys, types, identities,
claims, traversal44/43, elapsed reprs and independent work/P07/PAD/cleanup oracles
are unchanged. Global JSONL framing remains strict. An exact whole-module AST
comparison proves no other normalizer AST changes. Scoped independent SOURCE
review found no blocker to adapter reuse; root reviewed the integrated source.

Normalizer SHA256 `0b20db99ea0cd1efaf9780e40447dc329cc54be29ffea88e1b4b352882b27900`.
New regression SHA256 `aea841688e752443d51498ea82f22a6b47409d74ae00cce6fa58131c026a1ea5`.
Caller SHA256 `11c409d8105f58a2ad10406e14c9dad9835feb4c09c889b528da9e587f180871`.
Caller changes exactly its normalizer SOURCE/proof pins; its other AST is unchanged.
Normalizer proof `a0c35e7734d5e3df0cfba6875c7da294c08f19a390f8dbdb303012c7e5448b9d`;
caller migration/closed-phase proof `effd2149be15555c5d0b6f9fce7974e6ee34cae86697b5863228f1affc9cabc1`.

Fresh private69/public69 tests pass without skips: prior61 plus8 full-validator
regressions. Synthetic812-byte CRLF/LF positives, absent/multiple terminator,
malformed JSON, oversize, changed claim and unchanged strict JSONL behavior are
checked independently. The new suite fails on exact E872 predecessor source,
including its CRLF positive; that expected failure is retained. A selected host
canary authenticates the actual812-byte stdout and exact19 values, rejects four
malformed framings and rechecks role/journal identity. It proves only the console
boundary, never the whole prior. Operation366.4652ms/wrapper613.817ms.

Current caller19 tests and25 real selected CLI controls pass:4 SOURCE positives
and21 exact closed-phase rejections. One real junction is verified removed.
The successful synthetic CLI root is fully identity/containment/reparse checked
and removed; failed/historical roots remain. All selected children terminate,
with owned process roots/logs absent. Cleanup duration is unmeasured.
Older bootstrap/output matrices are not rerun or claimed as fresh proof.

Commands use the selected Python with -I -B under the maintained RootCall owner.
Run the seven prior normalizer suites, then:

```text
python -I -B test_normalizer_qualification_stdout.py -v
python -I -B test_qualify_prior.py -v
```

Caller units require TSJ_FIXED_ROLE_READER_PATH pointing to the pinned current
reader. The allowlist suite uses explicit public metadata/normalizer arguments.
The host canary accepts only explicit source/root/journal paths and hashes;
it reads one already-authorized normal role, never manifest-discovered paths.
Detailed phase/operation/wrapper timings and receipt hashes are in
[normalizer proof](NORMALIZER_FINAL_GATES_R1.json) and
[caller proof](CALLER_FINAL_GATES_R1.json). Raw private commands/receipts remain.

Next freeze a fresh empty output root, config and SOURCE manifest for these
published sources, then record a new normal-only journal before one deliberate
fixed20 qualification attempt. Never reuse the failed nonempty root or rerun
automatically. One genuine original plus exactly NULL/OBSERVE remains unspent/
gated. Full-D/H1 unqualified, playable100=0, betaHELD; caps/seeds/epochs/oracles
and CircuitJS truth unchanged. Full30EXCLUDED; HARD/PSYCHOTIC unavailable/unrun;
historical protected unreadUNCERTIFIED. Old D descriptor/result/generated-input
DATA, old builders/CLIs, manifest following and protected/held-out bodies remain
denied. GWT/Player NOT APPLICABLE; electrical/population proof NOT RENEWED.
