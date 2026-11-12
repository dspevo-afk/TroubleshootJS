# N70: non-admitting Source gate journal consumer

The V2 journal consumer replaces an arbitrary caller-gate SHA with a structured,
strictly bound Source-only result contract. A separate unconditional producer
hold rejects even a valid V2 receipt before an attempt or fixed-role call. The
N66 guard and both Study holds also remain. Actual producer execution and an
operational journal are not accepted. See the
[current contract](../../../../tests/qualification/rb100-layout/normal-prior-caller-r1/JOURNAL_CONSUMER_R1.md),
[gate definition](../../../../tests/qualification/rb100-layout/normal-prior-caller-r1/CALLER_GATE_DEFINITION_R1.json),
[source manifest](JOURNAL_CONSUMER_SOURCE_MANIFEST_R1.json) and
[proof](JOURNAL_CONSUMER_PROOF_R1.json).

The root-supplied whole-journal SHA is an external approval boundary, not proof
that a producer ran. Producer and invocation hashes in the receipt are references
only. A Source hash is not a passed-gate result. N71 must implement/review a
concrete producer and original launch/result/cleanup proof before any admission
change. The current contract adds no IO, CLI/config field, clock window or ledger.

Fresh final-source tests and timings, retained failures and the exact unchanged
input/class boundaries are recorded in the proof. Source-only synthetic positives
are distinct from known guard refusals and native Held refusal controls. None is
normal-prior qualification. GWT and visible Player checks are not applicable to
this Python-only change; electrical/population evidence is not renewed. CI was not
run; the self-hosted runner must still be registered.

No prior fixed20 role body, old D, held-out or protected DATA, actual DATA factory,
operational journal, normal qualification, OriginalArm, graph, capture or Session
was used. Original to NULL/OBSERVE remains unspent and gated. Full30 is excluded;
HARD/PSYCHOTIC remain unavailable and unrun. The protected-unread boundary remains
uncertified from the previously disclosed footprint; old normal20 was not reread.
Finite observations do not establish atomic namespace, continuous identity,
complete bootstrap/JDK/DLL/environment/descendant/creator-death/concurrency or
normal native-policy proof. The outer output guard is post-capture. All exact
seeds, model epochs, resource budgets and independent oracles are preserved.
Playable100=0; beta held.
