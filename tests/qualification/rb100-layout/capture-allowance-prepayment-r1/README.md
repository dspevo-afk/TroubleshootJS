# Capture allowance prepayment

The pure checked transform replaces the existing three-method allowance handoff.
It preserves the original input snapshot while accounting for the metered runtime
carrier and prepays the begin/merge controls and post-owner reads. See CONTRACT.

    python -I -B test_capture_allowance_source.py --study-source <pinned-current-Study.java> -v

Input236757B/SHAe20499b59ccedcdbaffd9e7bc1a2fdf5d3e0a4f96ccaed786656e701a5c5c103;
output239938B/SHA755ac9ff94478155c09298c8785df753da852483edd2888547977c6202969cbd.
The pure derive API takes exact Study and fragment bytes and owns no I/O.

Native replay needs the authenticated retained closure, selected JDK8,
source7/target7, explicit classpath and empty sourcepath. CaptureAllowanceCanary
takes SMOKE/SUITE, an owned empty directory and that classpath. Its snapshots and
budget credit are synthetic; no actual owner result or factory DATA is created.
Source-level five-field prepayment does not qualify actual capture execution.
The negative fresh-reader case uses only an interface proxy with zero calls.
No clean-clone full-D replay or normal-host/playable100 acceptance is promised.

Evidence: docs/task-evidence/Q100/capture-allowance-prepayment-r1. Existing carrier
and input owners keep their contracts. The caller identity/start/origin, actual
DTO/receipt, full semantics, host and lifecycle closures remain separate gates.
