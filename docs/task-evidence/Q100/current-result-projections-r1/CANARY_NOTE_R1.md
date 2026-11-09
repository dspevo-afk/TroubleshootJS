# Current result projections canary R1

Source-only delivery: `CurrentResultProjectionsCanary.java`, same package as the
admitted Study, Java 7 syntax. No compilation, JVM invocation, smoke/suite,
protocol-negative invocation, independent-reader run, production build, or
browser interaction was performed by this worker. Root owns those gates.

Inputs were checked against the frozen SHA-256 identities:

- PROJECTION_CONTRACT_R1.md: 24f40e39a9d52704a51aefbdf05170c25056447009c4b25605e45ad9de04fd4f
- REFERENCE_R1.json: bd0c6f081a6a9d11c47befe531debc87ddbccf02541542253b202a6debe491bc
- CANARY_WIRE_R1.json: b5b20358c18806889b53c8394be627bd5078f0fd7c282e09e285f0f60a357b59

## Protocol and cases

One exact `SMOKE` or `SUITE` argument is required. Missing, wrong, or extra
arguments produce mode INVALID, status FAIL, error CANARY_ARGUMENTS, empty names
and cases, zero Parent constructions, and exit 1 before constructing Parent.
Normal invocation uses the exact frozen top and case field sets. Any failed case
makes the invocation fail and exit 1. The payloadJson field contains compact JSON
from the actual helper result, without a line feed; no expected result payload is
embedded in Java. The two serializer methods are copied unchanged from admitted
ExportCompositionCanary lines 534-569 (ASCII, depth 4, 1 MiB).

SMOKE (2):

- cohort_transfer
- cleanup_true

SUITE (15):

- cohort_requested_mismatch
- cohort_duplicate_capture
- cohort_missing_capture
- cohort_incompatible_disposition
- cleanup_null_alias_false
- cleanup_observe_alias_false
- cleanup_geometry_false
- cleanup_effects_false
- cleanup_capture_context_false
- cleanup_null_context_false
- cleanup_observe_context_false
- cleanup_owner_false
- cleanup_scalar_below_sum
- argument_guards
- group_guard_before_read

## Observation boundaries

Each case constructs its actual Parent and native Meter through the admitted
SetupMeter adapter. Parent may load dependency classes, but no owner, capture,
Session, board, routing, producer, or graph call is made. The cohort fixture uses
ordinary maps/lists and actual deriveCohort; it never fabricates OriginalArm,
ArmResult, or Cohort. Its 60 SYNTH_N00..59 IDs, pair 37/11, and literal requested
order are synthetic fixture inputs, not prior/original authority.

The positive cohort checks that both output lists are the exact actual derived
lists, and are detached from every external fixture container. Cleanup checks
that no fixture container occurs in its output tree. All fixture snapshots walk
entrySet, preserving the shared CountingMap.get counter exclusively for actual
helper reads: 23 per cleanup output and zero before a group-guard rejection.
Snapshots include duration metadata [100, 200, 300] outside helper projection
views; the only duration passed to the helper is scalar 600, or 599 in its named
negative case. All nine cleanup false/below-sum outputs mark
sourceSemanticRejectionExpected; this component does not validate full D.

Native counters are observed through Study.field, and helper deltas are sampled
immediately around actual helper calls, including exceptional returns. Native
check() executes after helper/derive errors, after helper returns, and before
recording final fields. Positive groups finish normally. Expected errors discard
their Meter without finish. Argument guards make three actual helper calls,
require RESULT_PROJECTION_ARGUMENT, and leave zero Meter work. Group guard makes
640 real one-unit charge/finish groups and requires the native IOException on the
next helper admission before the first map read. No original-owner credits,
counter writes, or synthesized measured groups are used.

operationNanos includes Parent/fixture preparation and case checks. Native final
elapsed values come from actual Meter started/groupStarted fields and a current
System.nanoTime. No owned OS resources exist and no cleanup duration is invented.
The frozen counter tuples are asserted in the canary; root's independent reader
remains authoritative for expected JSON payloads and the full output protocol.

## Limits and handoff

Only these two assigned files were written. Repository edits/untracked files and
other workers' files were preserved. No staging, commit, push, email, processes,
or child workers were created. All executable gates are NOT RUN by this worker.
Electrical/population proof is NOT RENEWED. Normal producer integration remains
gated; actual prior/original authority and genuine original/NULL/OBSERVE execution
remain unproved. Production/GWT/player-flow gates are NOT APPLICABLE here.
