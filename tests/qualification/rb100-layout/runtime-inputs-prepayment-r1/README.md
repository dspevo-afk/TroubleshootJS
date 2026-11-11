# Runtime input carrier prepayment

This checked transition replaces the earlier unmetered Parent.runtimeInputs API
with runtimeInputs(Arguments,SetupMeter). The existing native Meter pays before
the four reflected reads and native carrier allocation. The contract and literal
price expectations define admission, closed-start price and partial failures.
No normal entrypoint is enabled and no new carrier or counter ledger is added.

    python -I -B test_runtime_prepayment_source.py --study-source <pinned-current-Study.java> -v

The input is236157B/SHA19f74da6cf1fa210dc0204977935d92b7456f8de3e30dabc5481fc096d174a18;
output236757B/SHAe20499b59ccedcdbaffd9e7bc1a2fdf5d3e0a4f96ccaed786656e701a5c5c103.
The pure derive API takes exact Study and fragment bytes and owns no I/O.

Native replay requires the authenticated retained code closure, selected JDK8,
source7/target7, explicit classpath and empty sourcepath. Both canaries take
SMOKE/SUITE, an owned empty directory and that classpath. They construct actual
Parent/Meter/SetupMeter/Inputs holders; the prepayment canary uses actual Meter
APIs to exercise copy allowance and group capacity. No field-counter writes or
factory DATA loading. No actual OriginalArm, graph, Session, capture or receipt.
No standalone clean-clone full-D replay is promised by these finite components.

Evidence: docs/task-evidence/Q100/runtime-inputs-prepayment-r1. The old mapping
proof remains attached to its old candidate; its current consumer is migrated
here with all original mapping assertions. This prepayment does not qualify
normal caller identity/clock/order, actual capture/receipt or host lifecycle.
