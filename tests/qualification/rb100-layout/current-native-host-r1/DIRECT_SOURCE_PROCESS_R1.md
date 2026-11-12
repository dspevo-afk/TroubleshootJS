# Direct Source process contract

N71 supplies the process boundary needed before the N72 gate producer. It adds
`-Role PythonSource` to `VerifierCurrentCodeCompileContract.ps1` and an opt-in
`CaptureImageIdentity` to the existing direct bounded process owner. It grants
no journal, historical read, normal qualification, or Player admission.

The selected Python cannot be qualified through the attempted native job path
on this host: CreateProcess returned Win32 error5 before a lease was returned.
That failure remains a failure. PythonSource explicitly selects the existing
direct Process backend; it never falls back after trying native launch. The
fixed caller/reader suite is nonspawning. Direct mode proves its original root
process and bounded streams; it makes no atomic-job or descendant-termination
claim. Existing Compile/HeldMain remain their native creator-job roles.

The externally SHA-authenticated request has exactly nine fields:

| Field | Contract |
| --- | --- |
| FilePath | Selected pinned Python3.13 executable |
| Arguments | Exactly `-I`, `-B`, canonical absolute script, `-v` |
| TimeoutMilliseconds | Strict integer1..180000 |
| MaxStdoutBytes / MaxStderrBytes | Strict integer1048576 for each stream |
| WorkingDirectory | Explicit canonical physical directory |
| ScriptBytes / ScriptSha256 | Positive integer<=4MiB and exact approved Source SHA |
| ReaderSourcePath | Canonical path to the fixed23241-byte reader863a258c |

Frame authentication is bounded to64KiB. Script, reader, selected tools and paths
are checked before launch; script/reader bytes are checked after exit, including
failure paths. No reparse path is accepted. These are point-in-time tuple checks,
not atomic namespace or continuous source identity proof. Root's supplied frame
SHA approves bytes; it does not assert a suite passed.

The direct owner retains its original `System.Diagnostics.Process`. With exact
Boolean `CaptureImageIdentity=true`, both stream limits and a cancellation token
must be supplied explicitly. The option rejects native job/working-directory
mode. A small identity adapter queries PID, creation time and image using the
original process handle, never a PID reopen, and checks the same Process's ID and
start time. `CaptureFacts.ImageIdentity` has ProcessId, ProcessStartTicks,
ProcessCreationFileTime and ImagePath. Bounded readers exist before the new image
query so a getter failure can use actual cleanup/capture evidence. Unopted calls
retain their prior result shape. [Microsoft's API](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-queryfullprocessimagenamew)
describes querying the image using a process handle.

Source role runs serially in a dedicated host. It saves, sets and restores only
that host's process cwd and TSJ_FIXED_ROLE_READER_PATH, recording exact prior and
observed values. An already-present empty variable is rejected before mutation;
the environment dictionary preserves its empty value in the diagnostic. Separate
environment-restoration.json and working-directory-restoration.json are typed
schema/status receipts. Their embedded observations are projections. Failed
restoration, evidence writes or source reconciliation cannot produce a PASS.

Successful process receipts say
`SOURCE_PYTHON_PROCESS_ONLY_NOT_GATE_OR_ADMISSION`, with backend
`DIRECT_PROCESS_SERIAL_NONSPAWNING_SOURCE`, job `NOT_REQUESTED` and descendants
`NOT_ATTESTED`. A nonzero child, timeout, stream overflow or source mutation is a
failed operation even when a negative canary correctly observes it. Cleanup
failure is not clean-process proof. Output caps are enforced by the existing
byte-capture owner; the separate outer controller guard remains post-capture.

The complete gate producer must still authenticate config/Source/root identities,
bind copied caller/test imports, reconcile all exact36 cases, recheck original
tokens and obtain external root approval. N72 owns that composition. N66, the
journal's unconditional producer hold and both Study holds remain unchanged.
Current module/harness tuples migrate; historical receipt tool pins do not.
