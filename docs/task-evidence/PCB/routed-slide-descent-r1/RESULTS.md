# Bounded generic private descent: useful cumulative reductions

Base80303258be31da72e238455a05068c2015ee84d4. Both fixed informed roots5then2
use the identical canonical first-strict slide/compaction search, current15/
density14, after actual selection/border trim/generation statistics. Limits
were declared before native outcomes:4accepted transitions and128evaluated
component-direction proposals per root. No extra route or original selected
mutation occurs. Production remains unchanged.

| Root | Accepted steps | Area before → after | Reduction | Copper before → after | Vias | Protected void before → after | Termination |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 5 | 1 | 9172744 → 9154104 | 18640 / 0.2032107% | 38610 → 38590 | 48 → 48 | 1354500 → 1354500 | NO_STRICT_IMPROVEMENT |
| 2 | 4 | 9178092 → 9102412 | 75680 / 0.8245723% | 51860 → 51730 | 48 → 48 | 1807000 → 1781000 | STEP_CAP |

Every accepted result beats current source, current no-slide control, initial
selected source and initial no-slide control on area. Copper sum, vias and the
same protected-empty-rectangle sampler never exceed any of those four. Copper
here is summed trace Manhattan length, with overlaps counted. No metric or
physical acceptance threshold was relaxed.

The actual accepted sequences are seed5 REN(+10,0), and seed2 D3(+10,0),
D4(+10,0), RFB(+10,0), RFB(+10,0). These names describe observed generic choices;
the implementation has no part/seed-specific selection rule. Seed2 step areas
are9159172,9140252,9121332,9102412; copper51840,51810,51770,51730; protected
void1800500,1794000,1787500,1781000. The first two endpoint slides have analytic
copper delta0; both RFB slides have delta-20 before compaction. Seed5's accepted
slide has analytic delta0 and its compaction reduces copper20.

Seed5's current census has25eligible nonincreasing directions in each of its
two rounds. It attempts10then25, with18physical admissions and17rejections.
Seed2's census sizes are31,29,29,29; canonical attempted prefixes are8,8,17,17,
with32physical admissions and18rejections. Total85attempts,50admitted compactor
invocations,35physical rejections, six matched controls and five accepted
updates. Invocation counts include unchanged-owner compactor returns. All six
actual controls return unchanged owners and retain source scalar metrics.

Seed5 rejections comprise8placement access/domain,7foreign-net contact and
2label/copper overlap cases. Seed2 has6placement access/domain,8trace/component
courtyard and4foreign-net contacts. Pre-compaction moves retain their rigid
original labels under the published feasibility contract; label regeneration
uses the common owner after compaction. The two seed5 label rejections remain
explicit. No per-axis compactor cause or global optimum is inferred.

Each legal pre-compaction row retains its published16field full physical,
endpoint-only, pad-pair and route-provenance proof. Every control and compacted
result has18field placement/full geometry/routing/quality/trace-clearance/
explicit two-layer/pad-pair/provenance/stable-identity/semantic-label/source-
immutability proof. The next plan is reframed to actual normalized geometry,
then every unique component/pad placement is compared. Every retained source is
rechecked after the entire chain; fresh final metrics equal the accepted output.
Private generation receipts remain explicitly unassigned.

Complete current censuses contain all parts and pad/net IDs, with detailed
incidents for eligible parts. The reader independently derives directions,
affected trace-index counts and net-pair hashes for every round. Seed5's131pads/
8515pairs yield93320b4ee979986b37aa1c34040d2ffa5f5fdf86eff43ef0de37b43fe038f856;
seed2's135pads/9045pairs yield9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7.
This supplies a current offline pad-map check for seed5, absent in the preceding
packet. Complete native trace paths and compactor ownership remain native proof
responsibilities. Original selected42non-time fields, whitespace, source graph/
geometry and route/work counts reproduce both accepted15 baselines exactly.

Final validation:

| Gate | Result | Actual scope |
| --- | --- | --- |
| Maintained JDK8 native unit4 | PASS, exit0,45.0499992s | 40/41/36/25assertions,6/6/5/6groups |
| Maintained JDK8 native routes1 | PASS, exit0,64.0683345s | Actual fixed5then2, six ordered reports, generic private chain |
| Independent reader | PASS80cases,8positive/72negative | Root fresh pure replay, strict types/order/chains/proofs/limits/pins |
| Actual selected PowerShell transport | PASS12cases | Four-unit and one-route nested manifests,10negative shapes |
| Root integrated review | PASS | Ten source deltas, exact published/accepted inverse,28frozen gates |
| Owned cleanup | PASS | COPY1473→1467 exact restoration/removal, private pointer removed,4driver births EXITED |
| GWT/player/solver/repair/fullmatrix/unseen/manufacturing | NOT RUN | Production1475/default15/general16 unchanged |

Native elapsed times include compilation and maintained terminal cleanup;
cleanup duration is NOT SEPARATELY MEASURED. Separate recorded-birth verification
does not terminate a process. Native callback times are2448ms(seed5),2925ms(seed2),
not a general timing guarantee. Only one expensive native phase ran at a time.
Optional review covered algorithm/API scope only; no independent final-source
review is claimed. Root owns integrated acceptance.

Actual commands, using the configured JDK8/Python/PowerShell runtime:

```text
python -X utf8 -B prepare.py
python -X utf8 -B declare_protocol.py
python -X utf8 -B bootstrap_host.py
pwsh -NoProfile -File check_suite_transport.ps1
python -X utf8 -B declare_reversal.py
python -X utf8 -B review_source.py
python -X utf8 -B inputs.py freeze
pwsh -NoProfile -File run_native.ps1 -Phase unit -JavaHome <JDK8> -PythonExe <Python>
pwsh -NoProfile -File check_process_cleanup.ps1 -Phase unit
python -X utf8 -B sanitize.py unit <JDK8>
python -X utf8 -B assess.py unit
pwsh -NoProfile -File run_native.ps1 -Phase routes -JavaHome <JDK8> -PythonExe <Python>
pwsh -NoProfile -File check_process_cleanup.ps1 -Phase routes
python -X utf8 -B sanitize.py routes <JDK8>
python -X utf8 -B assess.py routes
python -X utf8 -B analyze.py
python -X utf8 -B inputs.py audit
python -X utf8 -B retire_copy.py
python -X utf8 -B review_packet.py
```

The root additionally replayed the pure reader canaries and checked top-level
definition uniqueness, exact receipt/source pins, in-memory compilation and
source whitespace before freezing. Initial reader invocation used unsupported
`--phase unit`; argparse exited2 before output mutation. The positional command
then passed against unchanged native inputs. The invocation failure record is
reader-invocation-1.json; there was no failed Java/native phase or hidden retry.
Pre-freeze review replaced mirrored fixture logic with the actual shared driver,
removed an unsupported unchanged-control assumption and bounded current trace
indices. No native outcome informed those changes.

Native seal8c1c0a121455a4e02d85253c3df03c456d27dc1ae8044673ee29c764d0928ad5;
protocole047c6b87e72538b7b161449b7938b80c8e1ec9c081bea27ba40c26d3c4b73ba;
root review57bc9be9f5a2faaffef94fed0e9640d3639173f65a558906e27f017e5445f1c6.

Seed2 reaches the declared step cap, so further opportunity is unknown. Seed5's
no-improvement result is limited to its25direction cohort and current labels.
Next qualify the same bounded generic search on all14 informed regression roots
before any production proposal. No main merge or email; original five unrelated
untracked entries remain preserved. No owned process/COPY/browser/preview remains.
