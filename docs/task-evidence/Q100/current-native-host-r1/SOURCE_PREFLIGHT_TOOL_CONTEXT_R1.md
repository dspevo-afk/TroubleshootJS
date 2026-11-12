# Current Source preflight and early prior-tool rejection

Current source preflight required obsolete PowerShell, harness and codec
identities. Historical tool incompatibilities were checked only after prior
metadata had been decoded. The caller now authenticates current Source/tools for
preflight and applies the preserved historical tool checks before prior reads.

Actual current Source preflight now passes through the selected PS/root harness
and existing process owner with empty owned N/D roots: eight Sources, five tools,
pinned public allowlist metadata, Source manifest and `roleRead: NOT_RUN`. The
historical normalizer's exact tool expectations remain unchanged. The caller
rejects their known mismatch before Source-manifest/journal/attempt/role reads.
The actual qualifier negative is an expected rejection, not prior qualification.
The [maintained contract](../../../../tests/qualification/rb100-layout/normal-prior-caller-r1/SOURCE_PREFLIGHT_TOOL_CONTEXT_R1.md)
describes the two boundaries.

The selected harness preserves the old script's operation/receipt/cleanup code
except three explicit Source bindings. Actual PS marker, child0 and child7 controls
pass. Its controller/child-only builtin Modules environment is restored; no user
or machine settings changed. Codec pin DE21 matches the current provider: every
old C1 byte is an exact prefix, with only result helpers appended. The normalizer
extraction reconstructs its original Source byte exactly when reversed; no fixed
historical check, order, error, nativeJava-manifest linkage or oracle changed.

Final selected Windows Python `-I -B` gates pass with no skips:

| Synthetic/NO_DATA gate | Methods |
| --- | ---: |
| prior-tool-pure-r1 | 6 |
| normalizer-authorities-r1 | 10 |
| normalizer-synthetic-r1 | 13 |
| codec-input-synthetic-r1 | 11 |
| codec-qualified-synthetic-r1 | 18 |
| caller-synthetic-r1 | 20 |
| staging-current-caller-r1 | 24 |
| handoff-current-caller-r1 | 18 |

These total120 methods. The Source Smoke1/Suite5 controls pass current preflight,
stale PS rejection, wrong config digest, wrong actual tool bytes, and the real
qualifier's historical-tool rejection before missing manifest/journal/role reads.
Root checks exact Source/tool manifest rows, empty roots/output and terminated
original processes. Summed outer PS operations are0.945s/4.106s; controller setup
is outside that sum. Inner invocation clocks include default receiver cleanup.

Changed caller Source is also freshly integrated with the independent CODE class
oracle and existing native process owner:

| CODE integration gate | Cases | Whole Python wrapper operation |
| --- | ---: | ---: |
| compile-smoke-r1 | 1 | 18.478s |
| compile-suite-r1 | 4 | 26.979s |
| held-smoke-r1 | 1 | 32.276s |
| held-suite-r1 | 4 | 33.236s |

All141 fresh NO_DATA major51 plus2396 inherited CODE classes remain exact:
2537classes/12196044B. Compiler exit0/quiet, Source identity guard, class mutation/
restoration, frame pins and unchanged held-main exit1/exact205B trace pass. Native
operation/internal cleanup/caller-job clocks are separate in the
[proof](SOURCE_PREFLIGHT_TOOL_CONTEXT_PROOF_R1.json); wrapper cleanup is not timed
separately. The [manifest](SOURCE_PREFLIGHT_TOOL_CONTEXT_SOURCE_MANIFEST_R1.json)
binds31 current Source/metadata inputs, tools/JARs and independent class authority.

Failures remain preserved: the first harness call could not find Get-FileHash in
its inherited module environment; an initial Source config setup caught the
obsolete codec pin before launching a child; Source SuiteR1 failed a root control
expectation that duplicated READER_ in the observer error code. Source-derived
prefix composition corrected that control; fresh final R2 gates pass. No failed
operation or SuiteR1 is relabeled PASS. Scoped final Source review found no blocker;
root owns actual execution, conservation, inputs, resources and publication.

The query-only audit covers54 known recorded identities:29 original outers,
13 default-process children, four native coordinator leases and eight CODE PS
outers. Original exit proofs remain; queries do not repair historical handles.
One exact owned junction node was removed and revalidated. No known new persistent
test actor remains; ordinary CODE fixtures and failures stay private. Full
bootstrap/JDK/DLL/environment, all historical descendant handles, creator-death,
concurrent starts and namespace atomicity remain unqualified. Outer output guard
is post-capture, not a live bound; normal native role caps remain unqualified.

A leaf's whole-report search and root's wider filename-only inventory of one
known historical CODE tree are disclosed. One named Source/tool metadata file
located an independently hash-pinned script; no actual prior body was opened or
historical run manifest followed. Protected-unread remains UNCERTIFIED.

No prior qualification positive, operational journal, actual fixed-role body,
oldD/held-out/protected DATA, actual DATA factory or genuine operation. Both Study
holds and original->NULL/OBSERVE remain closed. Full30 EXCLUDED; HARD/PSYCHOTIC
unavailable/unrun; oldnormal20 NOT REREAD; playable100=0; beta HELD. Product
budgets/seeds/epochs/order and CircuitJS truth are unchanged. Production Java/GWT/
Player N/A, electrical/population NOT RENEWED, CI NOT RUN/self-hosted runner
prerequisite. Main/E/Desktop work and uncertain old resources remain preserved.

Next: establish an explicit authenticated historical input/tool-context authority
before any real prior read or successful upstream return. Current Source facts
cannot be relabeled as historical facts, and historical pins are not relaxed.
