# Preserved preparation and gate failures

These failures remain failures. Exact source revisions, process receipts and
task-owned scratch are retained in the external P5 evidence root. No production
physics, assertion, independent oracle or budget was relaxed to resolve them.

- Root's source review found that the first inspector borrowed Search.faces and
  cleared its entries during cleanup. This was fixed before execution by cloning
  only the reference wrapper. The original helper bytes remain preserved.
- A preliminary producer-only CANARY passed the selected source auditor but
  failed before compilation because the host expected P4's old preflight marker.
  The host marker was corrected; raw failure and the empty scratch remain.
- Full CANARY R1 reached the actual JDK8 compiler and failed on a duplicate
  vector helper and two footprint-to-pad type errors. The duplicate was removed;
  unchanged fixture pad IDs are now resolved through layout.getPad. No native
  case ran in that attempt. Source and failed compile scratch remain.
- Full CANARY R2 compiled1079 inputs and passed the producer overflow negative,
  then failed the foreign occupied-cell literal canary. The fixture always added
  an unrelated COURT pad on the probe's source cell. The actual router could
  reject before reaching canOccupy. C0 is now conditional, and the courtyard
  probe is separate from its pad. The original native failure is preserved.
- Read-only integration review found a truncated62-character PS5 hash in the
  strict reader. It would reject every valid host run. The pin now matches the
  actual64-character executable hash; the original reader remains archived.
- CANARY R3 passed the actual native gate, but its reader CLI rejected unchanged
  files as FILE_CHANGED. The selected Windows pathname and open-handle stat APIs
  report different ctime semantics. The fix compares common file identity across
  APIs and ctime within each API pair, retaining actual mutation and replacement
  rejection. Twelve focused tests, including real OS file cases, pass. The old
  reader, rejected CLI receipt and exact metadata diagnosis remain preserved;
  R3 is not reused as final source acceptance.
- CANARY R4 passed native execution, but the reader rejected a process-path
  contract. The host recorded the selected Python path and input-pin argument
  with forward slashes while the reader requires canonical Windows spellings.
  The host now canonicalizes parameters after their absolute/reparse checks and
  before process launch. Path confinement, executable hashes and exact argument
  comparisons remain enforced. R4 receipts remain preserved; fresh R5 gates
  are required after this host change.
- R5 CANARY and CONTROL passed their native/reader gates. CAPTURE ran successfully
  but its reader rejected CUT_GRAPH_REDERIVATION: the reader incorrectly included
  disabled edges whose destinations remained inside the reachable set. The frozen
  outgoing cut excludes those internal edges. The existing tiny directed fixture
  also asserted that wrong definition and was corrected using its explicit
  two-edge outgoing cut; a separate multiple-start fixture now tests the boundary.
  The frozen22 queries, graph, native producer and mathematical cut definition
  remain unchanged. All13 focused tests pass, and a focused derivation from the
  pinned actual graph exactly matches the frozen input. R5 is preserved rather
  than relabelled as full acceptance; fresh R6 case gates are required.
- R6 CANARY, CONTROL and CAPTURE passed native and reader validation. The actual
  deliberate exception passed native cleanup, but its reader raised a missing
  context-key error: it requested p4Operation while the parent loader exposes
  p4CaptureOperation. That lookup is corrected without changing the validator.
  Focused validation of the preserved exception payload/partial receipt passes;
  it does not renew R7 case acceptance. Fresh R7 gates are required. A private
  diagnostic initially used a nonexistent helper name, failed before acceptance,
  and was corrected to the existing receipt-prefix function; its failed command
  is retained. Thirteen focused tests pass after the lookup correction.
- The first semantic-negative controller copied its eight bounded fixtures but
  failed before launching any reader: its local variable collided with PowerShell's
  read-only Host variable. The controller variable was renamed; all eight original
  workspaces/receipts remain preserved. This private controller is outside the31
  pinned inputs, so the fix changes no tested native/reader/host source and does
  not require renewing the passing R7 case gates. Fresh semantic negatives and
  final source closure remain required.

The early input-hash negative and five host negatives are expected rejections,
not positive native acceptance. Source-only reviews and literal tests are not
substitutes for actual CONTROL, attributed CAPTURE and exception cleanup gates.

Old P4 and earlier failure evidence, unrelated Main/Desktop changes and uncertain
E06 browser/preview resources remain preserved. An earlier optional junction check
was NOT RUN after automatic approval review rejected it as blocked by policy;
no link was created and no equivalent retry was attempted. It is outside P5.
