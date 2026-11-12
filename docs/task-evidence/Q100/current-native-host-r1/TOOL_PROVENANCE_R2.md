# Separate execution tools from historical expectations

N68 fixes the caller's conflation of current tool observations with the expected
tool identities used to validate historical receipts. Normalizer authority V2 has
separate `currentExecutionToolFacts` and `historicalToolExpectations` fields. The
former links current observer rows to the unchanged current Source authority; the
latter supplies expected values for historical receipt checks. Neither a metadata
declaration nor an outer self-digest proves that a capture happened.

The caller builder requires explicit historical rows and takes detached copies of
both maps. The in-process qualification API accepts the pinned N67 Source-only
snapshot as declarations. Its four authority flags must remain false; its digest
must be an exact lowercase hexadecimal string matching both the fixed digest and
recomputed canonical material. No current-tool fallback, V1 alias or CLI activation
was added. The existing N66 guard remains first; missing or invalid expectations
then reject before manifest, journal, attempt or role work. Full receipt/run,
reader, journal, resource and root checks remain independently required.

All historical pin checks and native Java's binding to the separate prior Source
manifest remain. Historical receipt and argument consumers use only expectations.
The current Source authority stays exact, three-field and acyclic. Generic pure
validation checks current row shape and the Source digest link; the caller owns
actual observation. The [contract](../../../../tests/qualification/rb100-layout/normal-prior-caller-r1/TOOL_PROVENANCE_R2.md)
describes this boundary.

Final validation covers 143 synthetic/NO_DATA test methods with zero skips: caller26,
normalizer-authority17, expectation5, snapshot5, historical-tool6, normalizer-JSON13,
codec29, staging24 and handoff18. All caller-consuming groups were rerun on final
R2 Source. The 65 unchanged normalizer/tool/codec methods retain R1 evidence after
auditing every declared Source/fixture row and the selected interpreter. Complete
bootstrap identity remains outside that audit.

Actual selected Windows controls passed:

- Source Smoke1/Suite5: 0.9554725s/4.1276443s summed outer process time. The known
  current/historical mismatch still refuses before prior access.
- Valid and invalid supplied declarations both reach that same closed guard:
  two controls, 1.7464797s. These expected nonzero exits are refusals, not normal
  qualification positives.
- Nine metadata API/CLI controls: 8.0305009s. Existing path-free output, strict
  option, bad-hash and original metadata-token replacement controls remain intact.
  Replacement uses a profile-event hook with real OS renames, not an external
  concurrent-race proof.
- CODE Compile Smoke1/Suite4: 17.9974597s/26.8701056s. Held Smoke1/Suite4:
  31.9243215s/33.1442988s. The held entrypoint still returns its exact expected
  refusal. Independent141 NO_DATA plus2396 inherited classes remain exact; both
  Study holds and all Java Source are unchanged.

Root checked 71 existing caller definitions byte-for-byte. Three existing caller
functions changed; two helpers were added. Fifty-one normalizer definitions remain
byte-identical; two receipt consumers differ only by the historical-key rename
after excluding the updated entrypoint docstring. The history-authority validator
and one current-tool shape/link helper own the schema change. Twenty-nine other
accepted Source tuples remain unchanged. Bounded independent Source reviews found
no blocker; the digest-type note was fixed, separately reviewed and tested. Neither
reviewer ran tests or opened actual data.

The final query-only resource audit passed for 115 known identities across both
retained Source attempts: 57 original outers, 34 default children, 16 CODE PS outers
and eight native leases. Original exit/cleanup receipts remain the primary proof;
fresh PID queries do not repair missing historical handles. No known new persistent
test actor remains. Two exact task junction removals were verified; ordinary CODE
fixtures and failure evidence remain retained.

Earlier failures remain failures. The first resource query encountered an
unrecorded Windows error other than its expected87; its exact cause is unknown.
The diagnostic query retained that assertion, logged each queried identity, then
failed on a premature future-fixture path. The final collector included both
attempts and existing fixtures and passed the same strict query assertions. An IO
aggregate controller also refused to overwrite its R1 filename after all three R2
child gates had passed. Root rechecked those child exit receipts and frozen inputs
and wrote a unique R2 aggregate without rerunning the tests. No failed controller
was relabeled as a passing operation.

Actual commands were the retained private `run_expectations_probe.py`, Source
Smoke/Suite, `run_declared_guard.py`, `run_snapshot_unit.py`, metadata host driver,
caller IO driver and CODE Compile/Held Smoke/Suite drivers, followed by final input,
class and resource audits. All use pinned Sources and selected tools. Python uses
`-I -B`, optimize0; selected PS uses its built-in module directory, with controller
environment restored. Original process identities/handles are recorded at launch.
Operation time includes normal wrapper cleanup; separate cleanup is unmeasured
where the existing receipt does not supply it. The outer guard is post-capture,
not live. Exact pins and dispositions are in the
[Source manifest](TOOL_PROVENANCE_SOURCE_MANIFEST_R2.json) and
[proof](TOOL_PROVENANCE_PROOF_R2.json).

No actual fixed20 artifact body, prior qualification positive, operational read
journal, oldD/held-out/protected DATA, generated DATA factory, oldnormal20 or genuine/
OriginalArm/graph/capture/Session operation was read or run. Original to
NULL/OBSERVE remains UNSPENT_GATED. Full30 MEDIUM EXCLUDED; HARD/PSYCHOTIC
unavailable/unrun; protected-unread UNCERTIFIED; oldnormal20 NOT REREAD.
Playable100=0 and beta HELD. CircuitJS truth, exact product seeds/epochs/order,
budgets and independent oracles remain unchanged. GWT/visible Player NOT APPLICABLE;
electrical/population NOT RENEWED; CI NOT RUN pending its self-hosted runner.
Normal role policy, full bootstrap/JDK/DLL/environment, atomic namespace,
historical descendant handles, creator-death and concurrent-launch behavior remain
unqualified.

Next is a separately frozen historical-input transport/lifetime and exact prior
receipt/run/Source/resource/journal/root/allowlist contract. That proof must precede
guard replacement or normal activation. This milestone establishes provenance
separation only.

Implementation `1bf0bc2e12b660865fb414b1de5db1425e46233d` (Separate execution tools from historical receipt expectations)
normally pushed to origin/codex/q100-physical-feasibility; exact remote SHA verified.
Completion Gmail `1a1cfbf1b97cd404` sent once; this documentation receipt sends no second email.
Final index11 PASS `9f9327fedf821ea56502df7c0704a60d6209db8992bdd3644ad7f99940a973da`.
