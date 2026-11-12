# Source gate journal consumer contract

N70 delivers a non-admitting receipt consumer. Qualification remains closed at
the unchanged N66 compatibility guard and independently at the journal wrapper.
A structurally valid Source-only receipt always fails with
`PHASE_JOURNAL_PRODUCER_NOT_QUALIFIED`, before attempt creation or fixed-role reads.
No operational journal or actual producer is accepted by this change.

The exclusive journal schema is `tsj-q100-normal-prior-phase-journal-v2`.
V1 and its arbitrary `callerGateSha256` are retired. Existing current-config,
Source-manifest, roots, allowlist, reader-gate and normalizer-gate bindings remain.
The new `callerGate` requires exact schema, definition and Source-only scope;
the authenticated config's caller Source hash; the final test Source pin; selected
Python hash; producer Source and original invocation-receipt hash references;
and a strict PASS result with 36 tests, zero failures/errors/skips, exit 0 and
termination proven. Booleans cannot stand in for counters. The separate
[definition](CALLER_GATE_DEFINITION_R1.json) lists all case IDs, the fixed reader
pin and invocation. It does not include the caller hash, avoiding a self-hash cycle.

`_validate_phase_journal_contract` checks structure and bindings only. The three
existing operational seams call `_validate_phase_journal`, which adds the
unconditional producer hold. No CLI/config input, file read, token or ledger was
added. Reader and normalizer gate references do not assert those tests were run
as part of the caller's synthetic suite. The suite uses the real selected fixed
reader for its existing fixture tests and synthetic normalizer expectations.

The exact whole-journal SHA supplied by root is the external approval boundary.
Root may supply it only after reviewing a concrete producer Source and its
original launch, exact case outcomes, exit and cleanup receipt for these inputs.
Provenance hashes and PASS counters inside JSON are declarations, not evidence
that a process ran. N70 has no qualified producer and grants no operational
admission. N71 must implement and validate that producer/receipt boundary before
any separate change to this hold. The existing root identities and exclusive
attempt marker contribute one use in the same bound output directory while the
marker persists; they are not global anti-replay or wall-clock freshness proof.
No time window or budget has been invented.

Synthetic positive contract checks and real selected-host closed controls do not
qualify historical DATA or native normal policy. Both Study holds, all fixed20
role-body and genuine-operation restrictions, seeds, epochs, budgets and oracles
remain. Playable100 remains zero and beta held. Current evidence and limitations:
[N70 packet](../../../../docs/task-evidence/Q100/current-native-host-r1/JOURNAL_CONSUMER_R1.md).
