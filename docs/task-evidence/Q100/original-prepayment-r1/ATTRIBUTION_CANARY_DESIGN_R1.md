# Original attribution canary design, revision 1

## Scope

This is a source-only component canary for the inline nine-unit original
attribution boundary. It embeds the exact OriginalAttribution.java.fragment
at the single /*__ORIGINAL_ATTRIBUTION_INLINE__*/ marker. The arithmetic and
scalar guards in the generated Study are called reflectively, including
r5Add, r7Subtract, r7BoundaryLong, and r7BoundaryInt; the canary does not
reimplement those operations.

The fixture uses the emitted Study's actual Parent, Meter, SetupMeter,
and existing SetupResult constructor. The owner side is only a primitive
int groupCount fixture. The canary does not construct an OriginalArm,
ArmResult, owner, session, graph, or routing path, and calls no producer.

## Independent cases frozen in the source template

The template freezes these 21 names in SOURCE_EXPECTED_CASES, and emits the
names, per-case rows, observed assertion count, and status as one JSON line:

1. normal_positive_real_setup_wrapper
2. zero_wrapper
3. units_near_long_max_accept
4. groups_at_640_accept
5. boundary_long_wrong_type
6. boundary_int_wrong_type
7. boundary_long_null
8. boundary_int_null
9. boundary_long_negative
10. boundary_int_negative
11. setup_units_negative
12. setup_groups_negative
13. units_post_below_setup_underflow
14. units_delta_below_nine_underflow
15. groups_underflow
16. final_groups_zero
17. final_groups_above_640
18. wide_int_group_sum_does_not_wrap
19. meter_capacity_before_any_reads
20. meter_expired_deadline_before_any_reads
21. r5_add_long_overflow_direct_unreachable_valid_int_domain

The positive fixture performs actual SetupMeter.charge(101)/finish and
charge(37)/finish operations, then uses groupCount=3. The expected inline
result is wrapper units 37 and attribution groups 4 after exactly one nine-unit
prepayment. The zero-wrapper case independently expects zero. The near-maximum
counter case starts at Long.MAX_VALUE-9, allowing the actual meter charge to
reach Long.MAX_VALUE; its setup counter is Long.MAX_VALUE-32768, so the
expected wrapper amount is 32759. A separate boundary accepts exactly 640 groups.
The wide-int case uses Integer.MAX_VALUE + 1 - setupGroups(1): correct widened
arithmetic gives 2147483647 and rejects with R7_BOUNDARY_GROUPS; a wrapped int
sum would instead underflow.

Wrong boxed types, nulls, and negative returned values are supplied only by the
canary field spy as cached-value overrides. They never mutate the subject meter
or the primitive fixture. The spy checks the actual meter-unit increase before
the first reflected source read, exact read order (units, groupCount, groups),
the exact nine-unit debit, unchanged group/copy/chunk counters, and the expected
open state. Capacity and expired-deadline cases expect zero reads and zero debit.
Setup counter negatives, unit subtraction underflow, group subtraction underflow,
zero and over-limit groups are checked with independent scalar literals. The
generic r5Add(Long.MAX_VALUE,1) overflow case is explicitly outside the valid
int-to-long attribution domain.

## Pinned source reviewed

- CURRENT_STUDY.java: SHA-256
  802d051413bdd0c5dde25bf327f29cbe18a633f82fa4d2f29d1c15996c1c1847
- SHARED_OWNER.java: SHA-256
  4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4
- Q_AUTHORED_STUDY.java: SHA-256
  d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd
- PREPAYMENT_R1.md: SHA-256
  2d6968320ad1707e3eb913429f7b23c6ebd7ee27a1ad1d3b05fec5197e2fed39
- COMPONENT_CONTRACT_R1.md: read from the task-owned component directory;
  no hash was supplied in SOURCE_INPUT_R1.json.

The source emitter and fragment hashes remain root-owned. This design records
only the canary file and its listed source review; it does not qualify genuine
caller authority or invoke the producer.

## Validation status and limits

- Source inspection: PASS for the named pinned snapshots and contract.
- Canary source authored: PASS as a draft source artifact.
- Compile, execution, tests, native invocation, and DATA/classes/journal access:
  NOT RUN by this leaf assignment.
- Expected assertion count and result status: NOT RUN; they are generated from
  observed checks when the root later executes the authorized native canary.
