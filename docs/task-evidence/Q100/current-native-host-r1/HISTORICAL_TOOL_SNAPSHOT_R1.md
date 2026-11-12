# Historical Source tool snapshot inspection

The caller can now inspect one authenticated historical Source preflight without
treating its tool declarations as current observations or prior-run authority.
This completes N67's metadata prerequisite. Normal prior qualification remains
closed at the N66 compatibility guard.

The three pinned inputs are config4894B/c8e937, Source manifest2124B/e5ea5a and
Source preflight1595B/8244eb from accepted Source commit
`5c745c3ff78cc16e19220b6c4a46ce541bdb58ad`. Root authenticated the named private
config through the existing reader and verified the two public files against that
commit. No contained file/root/tool path was followed. The inspector authenticates
all three byte buffers before decoding, checks their shapes and bindings, and
checks the preflight's closed DATA phase and zero prior-body/resource observations.

The canonical metadata digest is
`a6cdc79ba35bc4f65e462a81ebd35b7d97260580fed5d27b75b506a9a02cc9e3`.
Root constructed the literal expectation from the accepted control metadata before
the candidate returned a value. Origin is `ACCEPTED_SOURCE_PREFLIGHT_ONLY`.
Capture receipt binding, prior-run binding, fixed-role read authorization and
prior qualification are explicitly false.

The API uses the current authenticated reader and Source preflight, rechecks all
three original metadata tokens/bytes, current inputs, roots and empty output
directory, and writes no operational artifact. The default API and
`inspect-tool-snapshot` CLI print only the metadata summary. A strict boolean
in-process option returns the historical tool paths. The CLI has no such option.
The [contract](../../../../tests/qualification/rb100-layout/normal-prior-caller-r1/HISTORICAL_TOOL_SNAPSHOT_R1.md)
describes the boundary. Current Source preflight still has eight Source and five
tool rows; its schema and normal qualification path are unchanged.

Final-source checks passed:

- Five new pure tests; 125 total synthetic/NO_DATA methods, zero skips. This
  includes caller20, staging24, handoff18, historical tool6, normalizer23 and
  codec29. All were run fresh with Python3.13, isolation enabled, bytecode writes
  disabled and optimize0.
- Nine actual selected-Windows reader/API/CLI controls: path-free CLI summary,
  optional API DTO matched to the independent literal, three bad-hash refusals,
  three byte-identical file replacement refusals and strict-boolean rejection
  before I/O. The identity controls use a profile-event hook and real OS renames;
  they do not prove an external concurrent race. Summed outer process time
  8.223772s. Owned N/D and DATA output stayed empty; no manifest, journal,
  attempt, context or producer Source was written by inspection.
- Source Smoke1/Suite5 passed in 0.9378227s/4.0979113s summed outer process time.
  The current/historical mismatch still refuses before prior manifest, journal
  or body access. Expected nonzero exits are control results, not qualification.
- CODE Compile Smoke1/Suite4 passed in 18.4103934s/27.3280854s; Held Smoke1/Suite4
  in 32.3100668s/33.4223231s. HeldMain still reports the expected refusal, not a
  genuine producer success. The independent141 NO_DATA/2396 inherited class
  profile, both Study holds and exact held stderr remain unchanged.
- Root checked 68 existing function/class definitions byte-for-byte against N66;
  only the CLI parser/dispatcher changed among existing definitions. Thirty other
  accepted Source tuples were unchanged. Two independent, bounded Source reviews
  found no blocker; neither reviewer ran tests or opened private metadata/DATA.
- Query-only cleanup audit checked 55 known identities: 28 original outer exits,
  15 default children, eight CODE PS outers and four native leases. Existing
  receipts prove their exits/cleanup; no known new persistent test actor remains.
  One exact task-owned junction was removed and verified. Ordinary CODE fixtures
  and all failure evidence were retained.

The actual private commands were `run_snapshot_unit.py`, `run_snapshot_host.py
snapshot-host-r1`, `run_source_gates.py source-smoke-r1 Smoke` and
`source-suite-r1 Suite`, `run_caller_io_regressions.py`, the three maintained pure
regression drivers, and `code-integration/run_gates.py` with Compile/Held
Smoke/Suite. All used pinned inputs. Selected PS child environment used its
built-in module directory; controller environment was restored. Original process
identity/handle receipts were recorded at launch. The
[Source manifest](HISTORICAL_TOOL_SNAPSHOT_SOURCE_MANIFEST_R1.json) and
[proof](HISTORICAL_TOOL_SNAPSHOT_PROOF_R1.json) record exact pins and results.
Operation time includes normal wrapper cleanup; separate cleanup duration is
unmeasured where the existing receipt does not supply it. The outer output guard
is post-capture, not live.

The first two root setup assertions failed because the worker changed its
initially announced frozen pin before the final freeze. They launched no gate
process. Both scripts and failure disposition are preserved; fresh final-pin
attempts passed. No failed attempt was relabeled as a pass.

The current Source/tool recheck compares byte facts across observations, not
retained file tokens across them. Same-byte replacement between those
observations can pass. This does not establish atomic namespace, launch or
continuous identity authority. Full JDK/DLL/bootstrap/environment closure,
historical descendant handles, creator-death/concurrent-launch behavior and normal
native role policy remain unqualified.

No actual fixed20 artifact body, oldD/held-out/protected DATA, generated DATA
factory, oldnormal20, operational read journal or genuine/OriginalArm/graph/
capture/Session run was opened or performed. Protected-unread remains
UNCERTIFIED; oldnormal20 NOT REREAD. Full30 MEDIUM EXCLUDED; HARD/PSYCHOTIC
unavailable/unrun. Original to NULL/OBSERVE remains UNSPENT_GATED. Playable100=0;
beta HELD. CircuitJS truth, exact seeds/epochs/order and all budgets are unchanged.
GWT/visible Player NOT APPLICABLE; electrical/population NOT RENEWED; CI NOT RUN
with self-hosted runner registration still a prerequisite.

Next is a separately frozen contract for joining historical tool expectations to
exact prior receipts, run/Source identity, roots and allowlist. Source observation
metadata alone cannot replace the guard or authorize a prior-role read.

Implementation `2035ecba294d0d49eef81a935607b712693ffbdd` (Inspect authenticated historical Source tool metadata)
normally pushed to origin/codex/q100-physical-feasibility; exact remote SHA verified.
Completion Gmail `1a1cf93983c8ab1c` sent once. This documentation receipt sends no second email.
Final index9 PASS `84b13faa95870bfdd4286109ae6242cd1070588ff2be6d96a43a2864bc55cc4a`.
The first staging attempt refused sparse paths; explicit intended paths were staged
with `--sparse`, without changing checkout rules or Source. No gate was affected.
