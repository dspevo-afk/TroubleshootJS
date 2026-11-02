# Descriptive history checks for captured routing witnesses

Accepted only for the exact two previously bound B6 graphs and the source below.
This tool measures their deterministic relaxed witnesses. Production feasibility
always remains UNKNOWN; it grants no route, layout, electrical or playable100
acceptance.

| Captured case | Relaxed witness | Descriptive turns/cost | History subset result |
| --- | --- | --- | --- |
| SHARED_HYSTERETIC_LINEAR |73states,71steps,1via; relaxed cost860 |5turns,175penalty,cost1035 |Prefix48+1 transition charges49 against request limit48 |
| SEPARATE_DIRECT_SWITCHING |269states,260steps,8vias; relaxed cost3116 |9turns,315penalty,cost3431 |Needs8 transitions; FULLER limit4 |

These reject the particular relaxed witnesses. They do not show that every
alternative path is infeasible, that the board is impossible, or why a production
search exhausted its branch/request budget. The next diagnostic should examine
alternative paths under the existing bounds before selecting any routing change.

The [frozen plan](PLAN.md) records exact source contract references, graph and
receipt pins, costs and limits. The summarizer reuses the unchanged graph oracle's
full in-memory witness; it does not implement another shortest-path search.
It checks each edge, endpoint, cost and witness digest, then counts planar turns
with a reset after VIA. The first step and the first step after a VIA have no
turn penalty; each later direction change adds35. The reported production-style
cost is descriptive for that witness, not a production optimum or accepted cost.

The selected history checks report the four-transition limit, first/consecutive
VIA restrictions, all-hole budget when adding a via, and pairwise via spacing.
The historical output field holeCountAfterAddition is the production gate's
prefix-hole-plus-transition charge, not a measured physical hole count.
Production checks that charge even if publication could reuse an existing hole.
The production spacing test rejects only when both absolute coordinate deltas
are less than20; exactly20 on either axis is permitted. Reported coordinates are
GRID_ORIGIN_RELATIVE_UNITS. Their deltas are translation-invariant. Spacing work
is capped at the first49 vias and exposes incomplete checks explicitly; both
measured witnesses are below that cap. Every acceptance flag remains false.

Validation:

- PASS27 integrated synthetic tests with literal costs/counts, VIA reset, fifth
  transition, hole boundaries,19/20 axis spacing, diagonal spacing, unreachable/
  zero-edge paths, strict malformed inputs and output behavior.
- PASS actual selected Python CLI for both pinned accepted cases, exit0 and
  termination proved;0.840s/0.704s including helper cleanup.
- PASS actual wrong-summary, corrupted-receipt and altered-metrics controls,
  expected exit2, termination proved and no acceptance output.
- PASS real temporary NTFS-junction rejection in the focused CLI tests.
- Source review found and closed an unpinned metrics-helper blocker before any
  accepted-graph run. Exact oracle and reviewed metrics hashes are now checked
  before execution. Final bounded source review found no remaining blocker;
  the reviewer ran no tests or graph data.
- PASS production-input audit:34 prior execution pins,1067 native inputs,
  Developer1202 inputs/357 outputs and Player1202 inputs unchanged. Fresh native/
  GWT/Player/Current gates are NOT APPLICABLE to this Python-only substep.
  Current war outputs are Developer outputs; they are not claimed as Player.

The [shared result](shared-witness.json) and [separate result](separate-witness.json)
are copied byte-exact; [qualification](qualification.json) records code/input/
process digests and the audited production boundary. Full paths are not published.
Raw receipts, rejected source copy and process evidence remain in task-owned
OS-temp scratch identified by the current checkpoint.

The reader accepts only summary9dab3b92 and receipt05b24b31, exactly the two graph
records e1937e17/5b1c80b2, and the fixed metadata from the accepted B6 packet.
The full receipt is hashed before graph interpretation. Metadata inherited from
B6 is explicitly labelled as prior proof. The pinned oracle remains
c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00.
No raw capture or numeric oracle was edited, and no production budget or default
behavior changed.

Use the verified Python3.13 interpreter, a fresh output and one bounded actor:

```text
python -B -m unittest -v test_fixed_prefix_witness_metrics test_read_fixed_prefix_witness
python -B tests/qualification/rb100-layout/read_fixed_prefix_witness.py --summary <accepted-summary> --summary-sha256 9dab3b92652734ff33b1c0bc183b896e7aaa51e6d30735eba6a8844ea7f64aa8 --receipt <accepted-native-receipt> --receipt-sha256 05b24b31c62fbd1d0fb14902dbb610384a1b58900339466726c4939c6e2f6086 --case shared --output <fresh-output>
```

Run the same command with --case separate for the other exact graph. Each actual
CLI child has60s; production90s/640/5s/500ms/P07caps/15s/120s remain unchanged.
No new seeds or normal qualification data were used. The owner-excluded full30
manifest export remains unrun; HARD/PSYCHOTIC unavailable/unrun, prior unread
certification UNCERTIFIED and public beta held. Accepted playable100 layouts0.
