# Qualified-prior paired Python API source worksheet R1

Status: SOURCE-ONLY DELIVERED; execution gates NOT RUN by this worker.
Authority: the root's `COMPONENT_CONTRACT_R1.md`, not a discovered legacy schema.
Only `context_codec.py`, `test_qualified_prior.py` and this worksheet are owned.
Pinned `source/` parents and repository files were not edited. No worker process,
listener, browser, class output, DATA artifact, journal or scratch copy persists.

## Current result schema and implementation

`QUALIFIED_PRIOR_SCHEMA = tsj-cohort-terminal-qualified-prior-v2`.
Exact ordered twelve keys:

1. schema
2. inputContextSha256
3. sourceParent
4. manifestSha256
5. sourceManifestSha256
6. readerSha256
7. resultSha256
8. operation1Sha256
9. parentReceiptSha256
10. priorRunId
11. priorOriginalReceipt
12. trialDisposition

`qualified_prior(context)` computes `context_sha256(context)` using the existing
canonical context validator/encoder, allocates a new top dictionary, and copies
the ten prior entries in existing order. The nested receipt/trial dictionaries
and trial lists retain input identity. It does not mutate input or deep-copy it.
The caller must retain an unmutated validated input for the borrowing lifetime.

`validate_qualified_prior(value, expected_input_context_sha256)` requires ordinary
dict type and exact twelve-key order, existing bounded JSON domain/no-alias walk,
the explicit current schema, canonical lowercase SHA-256 formatting for expected
and supplied current context hashes, and equality to the caller's expected hash.
It delegates the ten prior entries to the existing `_validate_prior`, including
nested receipt/operation binding and qualified trial semantics. A temporary
ten-key top dictionary adapts the existing validator; all nested objects remain
borrowed. It returns the original result dictionary unchanged.

No filesystem/process access is introduced into either API. Historical ten-key
prior maps and foreign/legacy schema tags reject; there is no fallback. Current
context hash binding is separate from all historical hash fields. Full-body
alias validation remains the existing canonical codec's responsibility: aliases
outside the qualified-prior subtree cannot be observed by this subtree API.
Neither API authenticates prior artifacts or supplies host receipt authority.

## Source evidence

Observed by PowerShell file inventory/hash and direct byte-prefix hashing only:

| File | Bytes | SHA-256 |
|---|---:|---|
| source/context_codec.py | 21653 | c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27 |
| source/test_context_codec.py | 13235 | 2a9479e97393273341eb44b71aa6870cb9c0661c87e8985e2bd4c83c193f7f36 |
| context_codec.py | 23259 | de21e233e27f93c8183101a89ed6fdb9a00812d69e1fe38cc47c3f3e573b3eb8 |
| test_qualified_prior.py | 15749 | ee546b0d7a4038ef866034d356651086c8d099d2a586d4457da835e1925a21c4 |

The candidate's first 21653 bytes hash to the exact pinned baseline codec SHA;
the entire baseline remains an unchanged prefix. All additions are appended.
No released candidate correction has been made after the above hashes were sent
to the root. Existing context schema, factory API, canonical encoder and Java
source emitter definitions are byte-preserved. Runtime equivalence is NOT RUN.

## Tests authored, not executed

The new test loader checks both source parent hashes, loads the candidate and
pinned baseline **codec** modules, and extracts only the pinned test module's
literal `_synthetic_context` FunctionDef using `ast.parse`. It compiles that one
definition with stdlib `copy` and the candidate `_CODEC` namespace. It never
executes the old test module's imports, loader, tests or other top-level code.
It accepts optional `--source-root` for the public Source parent directory;
default is adjacent `source/`. No controlled DATA file is read.

Independent literal schema/order assertions come from the root contract.
Canonical/factory expected byte lengths and SHA-256 literals come from the root's
pinned synthetic baseline oracles, not candidate output. Future execution checks
both literal oracles and candidate-versus-pinned-codec equality:

| Synthetic artifact | Expected bytes | Expected SHA-256 |
|---|---:|---|
| Canonical current context | 66485 | ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f |
| Generated input factory Java | 21404 | c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089 |

`rg -n '^    def test_'` observed **18** named new methods (and **11** named
methods in pinned `source/test_context_codec.py`). This is a source inventory,
not a runtime test or assertion count. Subtest iterations and assertion results
have not been measured or claimed.

New named test inventory:

- test_constructs_independent_current_schema_and_order
- test_new_top_map_borrows_nested_prior_objects
- test_producer_preserves_input_bytes_and_container_identities
- test_consumer_returns_original_and_preserves_valid_or_invalid_input
- test_rejects_top_shape_order_unknown_missing_and_legacy_prior
- test_rejects_legacy_or_foreign_schema_with_current_shape
- test_rejects_expected_current_sha_format
- test_rejects_result_current_sha_format_and_binding
- test_rejects_nonordinary_json_domain_and_mapping_subclasses
- test_rejects_nested_aliases_and_cycles
- test_receipt_and_trial_nested_key_order_remains_strict
- test_receipt_semantics_remain_bound_to_prior_operation
- test_prior_scalar_semantics_remain_current
- test_trial_semantics_remain_current
- test_producer_rejects_invalid_context_without_mutation
- test_canonical_context_bytes_match_pinned_baseline_and_literal_oracle
- test_factory_bytes_match_pinned_baseline_and_literal_oracle
- test_full_body_alias_validation_remains_a_separate_boundary

## Gates and next action

- Source pin and unchanged-prefix comparison: PASS (byte/hash observation).
- Static review of appended APIs and independent test oracles: completed by
  this author; no independent reviewer or runtime proof is claimed.
- Python import, AST compile, test execution, canonical/factory generation,
  native compilation/canary and whole-result host validation: NOT RUN.
- Normal assembly, Original/Session/routing invocation, actual DATA/journal
  reads, staging, commit, push and notification: NOT RUN; outside assignment.

Root owns review, any gate opening, execution and publication. Once authorized,
run this synthetic test file in the root-selected isolated Python host (optionally
passing `--source-root`), record observed counts/results, and then evaluate the
separate native component and host/whole-result boundaries. This worksheet does
not qualify a playable/public-beta product or spend the genuine original gate.
