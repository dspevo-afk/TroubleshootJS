# Source-only seams for the later P8 BASE replay

Read-only leaf findings; no captured payloads, BASE replay or new reason
distribution was read/run for this memo. This is not the unfinished P8 transport
contract and authorizes no producer implementation before that contract freezes.
The current focused CANARY is a prerequisite, not completion of the P8 question.

Keep the genuine original P6 full validation separate, using its original run,
pins and source context. Never pass P8 data through P6.validate_run/main or forge
an old root/index/receipt. A new P8 outer validator must bind its own actual
source, process, artifacts/index, cleanup, runId and source-manifest SHA. These
unchanged inner semantic validators accept actual new context explicitly:

- P4 read_capture.py74b05764a1b1cea405b92f50b6ca781ef44a56c0e61680f6abd399701e7be480:
  _operation(raw, entry, run_id, input_sha, branch_reader) delegates to the pinned
  branch reader validate_operation with WIRE,operation1,AUTHORED, then enforces
  exact candidate seeds/variant/epochs/selector/pad census. Use the NEW actual
  operation bytes and its NEW index entry, not historical P6 metadata.
- P4 _capture_transport(raw,index,operation,run_id,input_sha) binds actual header,
  graph/facts lengths and hashes, validates facts-to-operation/geometry/H48 and
  computes the independent zero-VIA diagnosis. decode_transport(raw,True) also
  returns the graph needed by the frozen P5 cut validator. Feasibility/admission
  remain unknown/false; an offline graph oracle is not a replacement solver.
- P5 read_blockers.pyc20bfc3daaa7fb8f7c428b47f29288cc6889c814f488c7caf1128023c214032b:
  _validate_cut_document(path,input_sha,graph,facts) rechecks its frozen identity
  and independently rederives the cut. Its cutQueries are row authority; the
  Java query list and observed events are emitters, not an oracle.
- P5 _validate_attribution(raw,payload,mode,info,facts,operation,context,closure,
  pins_sha,cut_document) validates original attribution framing/identity,
  geometry,22queries and totals. P6's actual call supplies CAPTURE,
  info={runId,workNanos}, actual new replay inputs and the validated cut. Its
  context requires the genuinely pinned branchReader; closure supplies the
  authenticated PcbTraceRules source row for clearance. Do not fabricate a
  historical P5/P6 context. Keep P6's explicit returned-copy/comparison-unit
  bindings. Other modes must follow actual inspector configuration, not guesses.
- P6 read_stages.py1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e:
  _validate_stage_payload(raw,payload,mode,run_id,pins_sha,facts,operation,
  p5_attribution,p5_totals,cut_document,work_nanos,p5,branch_reader) validates
  the unchanged P6 stage sidecar for the actual P8 run/source SHA. It preserves
  old P6 schema/scope/wire, exact graph/prefix/target/grid, full22 identities,
  endpoint stages, source/effect/lifecycle and work constraints. It validates no
  P8 reason rows, source manifests or process/index envelope on its own.
- P6 _assert_stage_parity/_normalized_stage omit events/run/source/timing for
  semantic NULL/OBSERVE comparison. They cannot certify P8 fine events. Existing
  P5 _validate_capture_parity and P4 _compare_capture are separate route/facts
  parent-parity predicates; retain them when asserting that same predicate.

P6 Study9936a10cb7f6fc90bdfa2b3ddd99f318264d3d25a6954a169f8c91702c2c830b
passes one TerminalPrefixInspector into actual ArmRun operation1. Its composite
fans genuine onTerminalPrefix arguments to P5 then P6, binds prior units, and
fans afterOwnersClosed with failure aggregation. The later P8-owned study can
add its own third inspector there, leaving both original inspectors and their
full22 semantics intact. P6 query copies are private; the chosen P8 inspector
owns its separate bounded compared views and14 additional actual full queries.
The fine Router/rules hook is already implemented by the P8 native overlay;
coarse events alone do not explain those guards.

Full replay must freeze nested query identity/cleanup/work/footer/index and
mode-specific exception accounting first. In particular, preserve old P6
CALLBACK_THROW prior-unit/selection semantics rather than assume a normal
composite completed; new REASON_THROW is distinct. Count hierarchical prior
P5+P6 units once, then actual P8 work, with no nested callback double credit.
All caps, exact roots/ordinals/H48, electrical truth and independent oracles
remain. HARD/PSYCHOTIC/full30 stay excluded; no production/Player admission.
