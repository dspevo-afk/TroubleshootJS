# Minimum-via lower bounds for the two accepted captures

Accepted as a fixed-graph diagnostic only. The shared captured branch cannot
satisfy its transition budget; the separate capture is not excluded by that
budget. No production routing treatment or playable100 layout is accepted here.

| Exact captured case | Minimum VIA count | Available transitions | Necessary-condition result |
| --- | --- | --- | --- |
| SHARED_HYSTERETIC_LINEAR |1 |0, because prefix holes48/request48 |Every path in this fixed relaxed graph exceeds the gate |
| SEPARATE_DIRECT_SWITCHING |0 |4, because prefix holes39/request48 and policy4 |Not excluded; actual production feasibility remains UNKNOWN |

The prior weighted shortest witnesses used1/8 vias. The new count minimizes only
VIA transitions: every allowed directed planar edge costs0, a face toggle costs1,
and every encoded start begins at0. It ignores the weighted oracle's secondary
start surcharge, distance, turns and route history. The separate result therefore
demonstrates a relaxed alternative to its eight-via weighted shortest witness.
It supplies no path or cost for a production repair or route.

The source-bound capacity is max(0,min(4,48-prefixHoleCount)). Production
pathViaFits charges prefix holes plus each transition before checking whether
the physical publisher could reuse a hole. This is a budget charge, not a claim
that every transition physically creates a new hole. The [frozen plan](PLAN.md)
records the source references and exact graph/receipt pins. The prior witness
packet's historical field holeCountAfterAddition has the same gate meaning;
its prose is clarified without changing its source or raw results.

The shared result excludes all budget-conforming paths only for this captured
prefix and these encoded starts/goals. Other branches, prefixes, orderings and
placements remain unqualified; the whole board is not proved impossible. A
minimum within the cap does not establish production history, via spacing, path
interaction, scheduler limits, clearance, connectivity or finalization. Every
result retains UNKNOWN production feasibility and false acceptance flags.

The independent layered0/1 search closes the whole directed planar frontier at
each level before promoting VIA targets. Deferred targets can be reached at a
lower level by planar movement. The first goal pop establishes the minimum;
only complete frontier exhaustion means UNREACHABLE. Both actual cases ended
GOAL_POPPED, so their fullReachabilityCensus flags are false. uniqueStatesAdmitted
includes deferred first discoveries; counters describe work only through goal
termination. Search storage is one visited bytearray and two unsigned32 arrays,
bounded by500000 states; trusted-codec validation uses bounded transient copies.
No predecessor or full path is retained.

Validation:

- PASS17 final integrated tests, including1024 tiny directed-graph comparisons
  with an independent exhaustive simple-path oracle, literal0/1/2-VIA cases,
  deferred promotion, a longer zero-via alternative, malformed types, immutable
  inputs, stale hash rejection and strict CLI/output boundaries.
- PASS actual temporary NTFS-junction rejection. The unit framework reported
  0.239s; child plus helper cleanup was0.659s, exit0/termination proved.
- PASS both exact selected CLI cases, exit0/termination proved,0.531s/0.648s
  including helper cleanup under separate60s child limits.
- PASS actual wrong-summary/corrupted-receipt/modified-core controls, expected
  exit2/termination proved and no output. Elapsed0.179s/0.174s/0.482s including
  helper cleanup. Operation and cleanup are not separately exported.
- Final independent core/CLI source reviews clear. Review caught a direct-API
  graph/hash binding gap before accepted-data runs; the fix reuses the pinned
  codec and requires full frozen-value equality. Four pre-fix sources remain
  archived. Reviewers ran no tests or accepted graph data.
- Independent bounded evidence/inference review clear: hashes, counts, budget
  math, timing and fixed-graph limitations agree. No raw replay or tests run.
- An intermediate literal fixture expected2 examined VIA edges; the actual3
  includes the enabled return edge to an already visited state. That expectation
  error and its tool transcript are recorded separately in qualification; no
  production algorithm or weighted-cost oracle was changed to pass it.
- PASS unchanged-input audit:34 prior execution pins,1067 native inputs,
  Developer1202 inputs/357 outputs and Player1202 inputs. Fresh native/GWT/Player/
  Current/manual-input gates NOT APPLICABLE to this Python-only scope. Current
  war outputs are Developer outputs and are not claimed as Player outputs.

[Shared](shared-minimum-vias.json), [separate](separate-minimum-vias.json) and
[qualification](qualification.json) are controlled bounded outputs. Raw process
identities, input receipts, rejection copies and development sources remain in
task-owned OS-temp scratch named by the checkpoint. No private paths or full
graph/path exports are published.

```text
python -B -m unittest discover -s tests/qualification/rb100-layout -p test_*minimum_vias.py -v
python -B tests/qualification/rb100-layout/read_fixed_prefix_minimum_vias.py --summary <accepted-summary> --summary-sha256 9dab3b92652734ff33b1c0bc183b896e7aaa51e6d30735eba6a8844ea7f64aa8 --receipt <accepted-native-receipt> --receipt-sha256 05b24b31c62fbd1d0fb14902dbb610384a1b58900339466726c4939c6e2f6086 --case shared --output <fresh-output>
```

Use --case separate for the second capture. The CLI pins the previous strict
reader, immutable graph oracle and new minimum-via helper before execution; it
does not perform the production-budget comparison. Qualification owns that
source-bound inference. Original generation90s/640/5s/500ms/P07caps/browser15s/
cleanup120s remain unchanged. No new capture, seed or normal population was used.
The full30 manifest test stays owner-excluded/unrun; HARD/PSYCHOTIC unavailable/
unrun and prior unread certification UNCERTIFIED. Public beta held, playable100
layouts0. Next is a frozen routing treatment or necessary diagnostic addressing
the shared prefix budget and the separate relaxed alternative, with real
production/finalizer/access/runtime proof still required.
