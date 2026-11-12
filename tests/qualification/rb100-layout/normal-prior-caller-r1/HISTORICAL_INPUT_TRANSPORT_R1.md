# Historical expectation input transport

Qualification takes three explicit historical Source-control metadata paths through
the API and CLI: config, Source manifest and preflight evidence. The earlier
in-process snapshot dictionary argument is retired. The current config and Source
manifest schemas retain their eight Source and five tool rows.

Inspection and qualification share the existing reader-backed metadata helpers.
Each input keeps its fixed size, hash and cap. All three raw buffers authenticate
before decoding. The operation retains the three original byte buffers and read
tokens locally; it does not follow paths named inside those documents or introduce
a persisted lease, cache or alternative reader.

Qualification preserves the existing current preflight and N66 guard first. It
then authenticates metadata and derives declarations as expectations, before current
manifest/journal validation. It rechecks original historical bytes and identities
before the attempt marker and role read, and during the final consumed input check.
The inspector retains its current Source, root and empty-sink checks.

Attempt and evidence records use exclusive V2 schemas and bind
`historicalToolSnapshotSha256`. Ready, failure and success dispositions retain that
same digest. Historical input paths and file tokens are neither printed nor
persisted in these records. The existing generated-Java `producerSourceToken`
remains a late output readback token, with its existing stager ownership.

The snapshot remains `ACCEPTED_SOURCE_PREFLIGHT_ONLY`; its four capture, run,
read-permission and qualification flags remain false. Transport does not authorize
role reads or authenticate a prior capture. The N66 guard, independent journal,
fixed-role reader and full normalizer checks remain required. A real qualification
positive and actual prior-role reads remain unrun.

Journal producer authority, passed caller-gate identity and one-use/freshness
semantics are separate prerequisites. A current caller Source hash alone is not a
passed-gate receipt. This contract changes neither journal policy nor time budgets.

Both Study holds and the original to NULL/OBSERVE envelope remain closed. Full30
MEDIUM EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected-unread UNCERTIFIED;
oldnormal20 NOT REREAD; playable100=0; beta HELD. CircuitJS truth, exact seeds,
epochs, order, budgets and independent oracles remain unchanged.
