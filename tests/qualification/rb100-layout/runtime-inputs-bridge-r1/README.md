# Runtime input carrier bridge

The checked transform adds Parent.runtimeInputs to the existing held native
adapter. It maps the current parsed identities/output directory into the existing
native carrier with exact references. No Map conversion or new carrier is added.
Both normal entrypoints remain disabled. See CONTRACT_R1.md for scope and limits.

    python -I -B test_runtime_inputs_source.py --study-source <pinned-current-Study.java> -v

Input235615B/SHA3fe46f10773b5f04c1c7a4b89f2390f21681658568c8ce0de4f32faee1193890;
output236157B/SHA19f74da6cf1fa210dc0204977935d92b7456f8de3e30dabc5481fc096d174a18.
The pure derive API takes study and fragment bytes and owns no I/O/producer entry.

Native replay needs the exact retained dependency closure, selected JDK8,
source7/target7, explicit classpath and empty sourcepath. RuntimeInputsCanary takes
SMOKE/SUITE, an owned empty directory and that classpath. It allows the inspected
owner charset/regex initialization and constructs only the real Inputs carrier.
Factory DATA namespaces remain blocked. It creates no Meter or OriginalArm and
invokes no capture. No standalone clean-clone full-D replay is promised here.

Evidence: docs/task-evidence/Q100/runtime-inputs-bridge-r1. This finite mapping
proof does not qualify metered caller prepayment, identity/clock authority,
receipt/actual-capture behavior, full result semantics or normal host execution.
