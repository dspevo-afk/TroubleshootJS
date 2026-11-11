# Metered capture allowance handoff

The current input read requires a pristine Meter and returns the original closed
SetupResult snapshot. The separately metered runtime carrier follows that read.
Its10units/4copiedEntries/1chunk/1closed-startgroup cannot be erased or folded into
a new input snapshot. The checked transition replaces only the existing begin,
merge and captureWithSetup methods; it preserves the carrier, SetupResult and
both normal entrypoint throws. It adds no ledger, holder, resolver or schema.

Begin prepays30 units:4setup reads+12native Meter reads+6checked arithmetic+
3controls+2counter writes+1setter+1post-setter read+1finish-open inspection.
After precharge it requires exact setup+carrier+begin units, exact setup+1 closed
group, setup+4 copies/setup+1 chunks, full allowance and inactive owner state.
Its open control group has30 units. It then resets only capture-local copy
counters, sets remaining=1048576-(setupCopies+4), and closes the control group.
The exact original input snapshot and borrowed map remain unchanged.

The canonical caller arrives closed after input read and carrier. A pre-existing
zero-unit open group can be observationally equivalent after precharge; no
nonzero extra work is admitted by the exact ledger. This does not authenticate
exclusive caller history. Native timing/budget checks still apply to that group.
The actual owner budget begins only after the handoff has closed the wrapper.

captureWithSetup retains the actual owner call, then prepays5 before its five
owner-result field inspections. Merge prepays32:4setup reads+12Meter reads+
8checked arithmetic+3controls+3mutations+post-setter1+finish-open1. It requires the
37-unit post-owner read/merge group, actual owner-local prior/group state and
the same remaining allowance. It restores setup+carrier+owner-local copies and
chunks exactly once, restores full allowance, then closes. Its minimum unit delta
over the original input snapshot is10+30+5+32=77 plus native owner-call wrapper
work. The separate later9-unit attribution charge pays only its own controls.
Merge's owner-local groupCount is distinct from the later whole-phase group total.

Source-priced transient reflection machinery/adapter locals do not introduce
logical copy entries. Structural Parent/raw Meter binding is protocol admission.
Native R7 computes copy/chunk metadata residual after setup/owner/fresh subtraction;
the current independent reader adds those categories back. The carrier4/1 can
therefore survive as existing metadata residual without changing schema/oracles.
This Source algebra does not supply actual original/result or full-host proof.

Admission or later errors retain actual prepaid units, open/closed groups and,
after mutation begins, counter/allowance changes. No refund, rollback or retry.
Discard failed attempts. Keep the same real producer-start/Meter and exclusive
borrowed-map lifetime; neither source/clock origin nor snapshot origin is proved
by structural binding and scalar equality alone. All existing caps remain.

Native canaries use actual Meter APIs and ordinary synthetic SetupResult snapshots,
not validated input data. Positive merge uses actual synthetic budget credit
beginOriginalOwner/admitOwnerGroup/adoptOriginalOwner with no actual or fake
OriginalArm/result, graph, Session or owner work. Five-unit result-read credit is
synthetic; the actual positive captureWithSetup path remains NOT RUN/genuine gated.
Its foreign-Parent rejection is exercised before any debit/owner call. The current
input helper is freshly compiled only for a negative carrier-before-fresh check;
its Factory proxy asserts zero methods. No input factory DATA or positive RUN81.

Expectation JSON records pre-native literals. Its pristine-null variants are
Source-derived examples; FIXTURE_EXPECTATIONS binds the actual null-snapshot
post-carrier/post-begin cases. Later actual results are in the evidence packet.
No failed output was used to alter a fee, fixture expectation or oracle.

Production/GWT/Player N/A for this qualification-only change; final-source JDK8
and finite native gates are required. Electrical/population not renewed; CI not
run/self-hosted runner prerequisite. Normal main/assemble and genuine original ->
exact NULL/OBSERVE remain held. Full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun;
playable100=0,beta HELD; protected-unread boundary UNCERTIFIED; old normal20 not reread.
All seeds, epochs, independent oracles and90s/640groups/5s/32768-per-group/
copy1048576/chunk4096 and all other budgets stay unchanged.
