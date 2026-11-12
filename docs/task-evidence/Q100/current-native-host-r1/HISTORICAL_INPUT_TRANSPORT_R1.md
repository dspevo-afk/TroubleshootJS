# Authenticated historical input transport

N69 replaces the qualification API's in-memory snapshot argument with three
explicit historical Source-control paths. The CLI now requires the same config,
Source-manifest and preflight paths. Both qualification and inspection use the
existing reader through shared helpers, with fixed byte counts, hashes and caps.
All three raw inputs authenticate before decoding.

The operation retains the three original read records locally. Qualification
rechecks their bytes and identities before the attempt marker/role read and during
final consumed-input validation. V2 attempt and evidence records, failure records
and the returned disposition bind the same fixed snapshot digest. The generated
Java `producerSourceToken` keeps its separate late output-readback meaning. There
is no generic lease, cache, alternative reader or dictionary-input alias.

The N66 guard remains first. The transported snapshot remains Source-only, with
four false capture/run/read-permission/qualification claims. Metadata transport
does not authorize a prior-role read, validate a prior capture or resolve journal
producer/gate/freshness policy. Current config/Source-manifest schemas, normalizer,
reader, observer, journal policy and both Study holds remain unchanged. The
[contract](../../../../tests/qualification/rb100-layout/normal-prior-caller-r1/HISTORICAL_INPUT_TRANSPORT_R1.md)
describes the boundary.

Final validation passed:

- 153 test methods, zero skips: caller32, actual-reader transport4, staging24,
  handoff18, snapshot5, expectation5, normalizer30, historical-tool6 and codec29.
  The actual-reader tests prove stable original tokens, all-three reads before
  decode, each bad-SHA refusal before decoding, and each same-byte replacement
  refusal. Six owned negative-control directories and old leaf identities remain
  retained; supplied Source metadata was never modified.
- Source Smoke1/Suite5 passed in 1.1490547s/4.1786177s summed outer process time.
  The CLI's three path arguments reach the existing closed guard correctly.
- Two actual qualification-API controls with valid and nonexistent metadata paths
  stopped at the unchanged current-tool guard, in 1.752656s. Transport past that
  guard was NOT REACHED. This proves closure, not a normal qualification positive.
- Nine inspector metadata API/CLI controls passed in 8.1329208s. They use the same
  shared reader/recheck implementation; their Source-only positive is not a
  guarded qualification-path positive. Profile-hook identity controls use real OS
  renames but do not prove an external concurrent race.
- Final CODE Compile Smoke1/Suite4 passed in 18.4531913s/27.052845s; Held
  Smoke1/Suite4 in 32.0407698s/32.9992665s. HeldMain remains the exact expected
  refusal. The independent141 NO_DATA plus2396 inherited class closure remains
  exact, with both Java Study holds unchanged.

Root checked 68 existing caller definitions byte-for-byte, including the current
guard, journal policy, expectation crypto and output-token owners. Eight existing
definitions own transport/record/CLI integration; two reader-backed helpers were
added. Thirty-one other accepted Source tuples are unchanged. Independent bounded
caller/test and IO/new-test reviews found no blocker; a final fixture-only delta
was separately reviewed. Reviewers ran no tests and read no actual metadata/data.

The first two caller test attempts failed on missing fields in one new synthetic
state fixture: `sourceManifest`, then `modules.normalizer`. Root read the complete
unchanged production recheck requirements, completed the fixture, and reran the
32-method suite successfully. Production assertions were preserved. Both failed
child exits, logs and Source freezes remain retained; they are not passing gates.
The expensive CODE matrix ran once, after focused tests and Source reviews.

Final query-only resource audit passed for 63 known identities: 34 original outers,
17 default children, eight CODE PS outers and four native leases. Their original
exit/cleanup receipts remain the primary proof. One exact task junction removal
was verified; no known new persistent test actor remains. Ordinary CODE fixtures,
the six metadata-control directories and failure evidence remain retained.
Historical missing handles and complete bootstrap closure are not repaired by
fresh PID queries.

Actual commands were the private transport test driver, caller IO regression
driver, Source Smoke/Suite, explicit-path guard driver, snapshot/expectation/
normalizer/tool/codec drivers, metadata inspector driver, and CODE Compile/Held
Smoke/Suite drivers, followed by Source/class/resource/index audits. Python ran
with `-I -B`, optimize0. Selected PS used its built-in module directory; controller
environment was restored. Original process identities/handles were recorded at
launch. Operation time includes normal wrapper cleanup; separate cleanup is
unmeasured where the existing receipt does not supply it. The outer output guard
is post-capture, not live. Exact pins and dispositions are in the
[Source manifest](HISTORICAL_INPUT_TRANSPORT_SOURCE_MANIFEST_R1.json) and
[proof](HISTORICAL_INPUT_TRANSPORT_PROOF_R1.json).

The test's bounded caller bootstrap hashes and executes the same buffer. It does
not establish caller source-path, ancestor, host or full runtime bootstrap closure.
Metadata token rechecks are point-in-time checks, not atomic namespace or
continuous identity authority. Current Source/tool observations still compare
byte facts across observations. Full JDK/DLL/environment, historical descendant
handles, creator-death/concurrent launches and normal native role policy remain
unqualified.

No actual fixed20 artifact body, normal qualification positive, operational read
journal, oldD/held-out/protected DATA, actual DATA factory, oldnormal20 or genuine/
OriginalArm/graph/capture/Session was read or run. Original to NULL/OBSERVE remains
UNSPENT_GATED. Full30 MEDIUM EXCLUDED; HARD/PSYCHOTIC unavailable/unrun;
protected-unread UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0 and beta HELD.
CircuitJS truth, exact product seeds/epochs/order, budgets and independent oracles
remain unchanged. GWT/visible Player NOT APPLICABLE; electrical/population NOT
RENEWED; CI NOT RUN pending its self-hosted runner.

Next is a separately frozen journal producer/passed-gate identity and one-use or
freshness contract, followed by exact prior receipt/run/Source/resource/root/
allowlist binding. A current caller Source hash alone is not a passed-gate receipt.
Those prerequisites must precede guard replacement or normal activation.
