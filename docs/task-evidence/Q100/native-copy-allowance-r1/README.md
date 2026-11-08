# Native copy-allowance prerequisite

Status: accepted and published as `7fe4cddb82b3ccda54e603f3e19a6e4007fcc94e` on
`origin/codex/q100-physical-feasibility`; exact remote SHA verified.
Completion Gmail `1a1b893e9af1226b` sent once.

The historical capture adapter admits copies against its full global limit,
then resets shared copy counters to callback-local counts. A caller's already
allocated setup data can therefore disappear from in-flight checks. This exact
source overlay adds a per-Meter effective allowance and carries it into capture
preflight, ordinary copy reservation, fingerprint and pad-land checks. Defaults,
the global limit, chunk4096 and structural bounds remain unchanged.

The checked transformer and focused canary are retained under
`tests/qualification/rb100-layout/native-copy-allowance-r1/`.
[The readable diff](parent-overlay.diff) is the complete native-parent change.
No production Java/GWT source, electrical model, routing policy, seed, epoch or
admission changed. Current production owner c382f3bc remains byte-identical.

Actual selected JDK8 checks pass: final focused canary, the existing N default
canary and the independent unchanged N semantic reader. The final effective
task classpath contains16 parent classes and2 final canary classes;75 current
owner dependency classes and4139 prior classes match their retained inventories.
The compile strategy and failed earlier revisions are explicit in
[validation](VALIDATION.md), [source proof](SOURCE_PROOF.json) and
[failure history](FAILURES.md).

Callback dispatch order is SOURCE_ONLY. Runtime checks exercise the real
preflight helper with genuine in-flight fixture searches and actual counters;
they do not certify a rejected legacy terminal callback dispatch. No authored100
capture or cohort Session ran. Full R2 accounting/export/host/native/consumer
integration remains NOT RUN/NOT ACCEPTED and requires an explicit extra native
source substitution in its contract. This does not qualify playable100 layouts.

All existing limits and independent oracles remain. Full30 is EXCLUDED;
HARD/PSYCHOTIC are unavailable and unrun. Protected bodies were unread here;
the historical unread boundary remains UNCERTIFIED. Playable100=0; beta HELD.
