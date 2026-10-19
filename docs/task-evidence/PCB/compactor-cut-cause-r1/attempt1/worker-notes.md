# Worker notes: bounded compactor cut observer

This worker changed only the accepted1467 scratch COPY files assigned by root:

- `src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java`
- `tests/contracts/PcbCompactorCutObserverContractTest.java`

The task COPY is bound to base `8b88311959d3faa8df2fefa4e31b0dcd9594ff8f` on
`codex/q60-faulted-profile-counts`. The unchanged production source at that base
has SHA-256 `A7F7DFCA5C85C17380BE868EDA6175DEE551172D7695BF6A2CF18012E844F732`.
The observer-instrumented source has SHA-256
`709A52A901AB54C5DABF9FFA5F121E805A68129621C38A239DFA32E4C996B759`; the
new contract test has SHA-256
`785F82D0F082FF3EB6339A2AD8777E0C3DFAECF30CD4A9D23C122C6BFE3319ED`.

The production hook is package-private, defaults to `null`, snapshots once for
each axis, and exposes only primitive cut metadata and outcome strings. A cut
callback follows the existing cut append and removal update. Successful
unchanged axes end as `NOCUT`; changed axes end as `ACCEPTED`,
`PLACEMENT_REJECTED` with the existing reason, or `QUALITY_REJECTED` with the
existing kind name. The existing narrow catches remain narrow, so an unexpected
physical `IllegalStateException` still propagates. Removing the nested observer
interface/field, axis snapshot and callbacks, restoring `if(removed==0)continue;`,
and restoring the original two catch bodies reconstructs the accepted source;
root's source-review helper can verify byte identity.

The unit has three literal fixture cases through `compactRouted` and clears the
static observer in `finally` for every observed call:

- Straight copper: declared endpoint distance and route length are 700 before
  and 140 after; expected x cut is coordinate 740, amount 560, and output
  outline width 440. The no-observer run compares full plan/layout fingerprints
  and routing work counters with the observed run.
- Barrier: package courtyards are `[70,110]` and `[190,230]`, leaving the
  literal required gap of 80. Vertical terminal escapes keep planner envelopes
  from extending into the x gap; expected x cut coordinate 170/amount 20
  reduces the physical barrier gap to 60 and is expected to be rejected with
  `ENVELOPE_ACCESS_OR_DOMAIN`. The x projection must remain unchanged.
- U route: endpoint distance `D=1000`, height `H=750`, and path length
  `L=2500<=3D`. The expected hypothetical x cut is coordinate 1035/amount 850;
  it leaves `D=150` and `L=1650>3D`. The x axis is expected to retain its
  original geometry with `QUALITY_REJECTED/DETOUR`; the independent y cut is
  coordinate 935/amount 680 and may succeed, producing height 420.

During static review I found that the initial fixture reused a zero-escape P06
test point, which `PcbAccessPlanner.validate` rejects before `compactRouted`.
The fixture now derives an authoritative small package from that geometry with
explicit 30-unit escapes, and places the copper on the footprints' TOP side so
the physical courtyard checks apply. The revised literal cut arithmetic above
accounts for those escapes. This is a fixture correction, not a runtime result.

No native contract, Java/GWT build, maintained verifier, browser run, or source
retirement check was run by this worker. The `PASS: PCB compactor cut observer
contracts assertions=N fixtures=3 evaluator=compactRouted` marker is configured
in the unit but has not been observed. Root owns final integration, source
review/freeze, and maintained native execution. Possible remaining failures are
fixture-construction or geometry-validator disagreements that only the
maintained native run can settle.
