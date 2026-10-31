# Independent fixed-prefix graph oracle prerequisite

Status: **locally accepted prerequisite; publication preparation.** Starting HEAD
`4e2ec4a017eaff2a86b6156662e29a57eca360e7`. This implements only
[PLAN Amendment A](PLAN.md), before the actual generator observation seam.
The [controlled evidence](oracle-prerequisite1.json) records source hashes,
selected-host commands/results, review, preserved development limits and scope.

The test-only [reader/oracle](../../../../tests/qualification/rb100-layout/read_fixed_prefix_graph.py)
validates an immutable generic directed two-face graph, computes an independent
minimum history-relaxed path cost and emits a bounded witness. It knows no PCB
net, pad or route predicates. No graph read by this prerequisite came from a real
board. Actual100 accepted layouts remain zero; no layout, electrical or Q100
qualification follows. HARD/PSYCHOTIC remain unavailable and unrun, with the prior
protected-read uncertainty still disclosed in the current task report.

The protocol uses state `face*cells+y*width+x`, NORTH/EAST/SOUTH/WEST bits1/2/4/8,
ordinary costs10/12 for primary/secondary faces, secondary initial cost2 and
via cost60. Via cells permit same-cell transitions between the two faces. The
model relaxes turn history, transition count and the global via allowance.
Exact keys/types, lengths and off-grid bits are checked. Input is capped at8MiB
before decoding; duplicate keys, nonfinite values, malformed UTF-8 and excessive
JSON nesting reject with static error codes. Case labels do not attest capture
lineage. The complete protocol and canonical digest rules are frozen in the plan.

On the verified Python3.13 interpreter:

```text
python -B tests/qualification/rb100-layout/test_read_fixed_prefix_graph.py
python -B tests/qualification/rb100-layout/read_fixed_prefix_graph.py graph.json
```

For this synthetic input the minimum relaxed cost is70 with witness states0,1,3:

```json
{"protocol":"p07-fixed-prefix-graph-v1","model":"DIRECTED_TWO_FACE_HISTORY_RELAXED","caseId":"ROOT_VIA_POSITIVE","width":2,"height":1,"primary":0,"steps":[2,0,2,0],"vias":[false,true],"goals":[false,false,false,true],"starts":[0]}
```

The CLI emits one JSON line. A valid graph returns exit0, including an unreachable
graph; malformed data returns1; unavailable/nonregular input or wrong argument
count returns2. Negative canary rejections are not accepted graphs. Every result
keeps capture/layout/electrical/Q100 acceptance flags false. A reachable result
stops at the first settled goal, so its explicitly named pre-termination counters
are not a full reachability census. Witness output retains at most64 states and
63 edge labels, plus full path counts and a digest.

Final-source validation:

- PASS12 synthetic contracts, including64 tiny directed step/via graphs and all
  16 singleton start/goal pairs per graph:1024 independent Floyd-Warshall cost
  comparisons. Literal tests cover true equal-cost choices, a70-state truncated
  witness, fixed canonical hashes, malformed input and immutable arrays.
- PASS10 actual selected-host CLI canaries through the maintained bounded process
  helper: reachable/unreachable, duplicate/type rejection, missing/directory input,
  oversized and exact8MiB files, deep JSON and row wrapping. All children terminate
  with the expected exit; CLI errors expose no supplied paths or raw input.
- Read-only independent source/test review finds no material blocker. The reviewer
  ran no gates. Root separately supplies the real CLI evidence the review required.
- Read-only source audit matches1597 execution files and both1199-file GWT input
  identities to the accepted production snapshot. Developer compiled output also
  matches. Production Java, native runner and module are unchanged. Native/GWT/
  Player gates are NOT APPLICABLE to this Python-only prerequisite and were not
  rerun. CI retains its existing self-hosted runner-registration prerequisite.

Owned process/output/log cleanup is proven by the maintained helper returning;
its cleanup duration is not separately measured. No browser, preview or electrical
graph is allocated. Raw streams and synthetic files stay in owned OS-temp scratch.
The initial development deep-nesting fixture failure and its artifact-retention
limits are recorded in the evidence; no failing parser assertion was relaxed.

Next: add the opt-in, staged observation seam through the actual generator and
router owners, prove source/prefix parity and producer fidelity against literal
geometry, then observe both exact authored100 variants under existing budgets.
That work and four fresh qualified layouts/runtime/solvability remain pending.
