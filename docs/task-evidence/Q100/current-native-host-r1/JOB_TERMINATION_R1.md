# Creator-owned job termination

N58 adds an explicit termination request to the existing Windows containment
owner. `CreateNew` retains authority through its original job handle;
`OpenExisting` remains a query-only observer. The observer is rejected before
an OS termination call. Once a creator requests termination, its later launches
are rejected, including if the OS request fails. Query, retry and disposal remain
available. No replacement launcher or process-tree enumeration grants authority.

Windows job termination is a request, so the caller must separately observe
completion. The existing launcher intentionally places a query-only lifetime
reference in its child. Closing the controller therefore need not close the last
job handle. N58 preserves that recovery behavior. These distinctions follow the
[Windows job documentation](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)
and [TerminateJobObject contract](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-terminatejobobject).
Creator-death protection is not qualified by this change.

The maintained contract compiles a small CODE-only .NET actor and runs the actual
native class on selected Windows PowerShell 5.1. The actor starts one ordinary
descendant; independent kernel-backed identity and named-job membership witnesses
identify both. An unrelated sentinel runs outside the target job. Each additional
job member must be one of at most two system console hosts, with exact canonical
image, hash, valid Microsoft signature and creation ordering. No extra PID is
silently ignored. The creator and observer snapshots must agree.

Before termination, the test retains observation handles for every vetted member.
One shared five-second deadline covers the native request, post-request launch
denial, empty-job observation and all retained handles signaling with exit code2.
Observation handles never authorize termination. The outside sentinel must survive;
its final cleanup uses the process owner's original retained process/start pair.
Request, closure and cleanup durations are separate.

Final validation and source identities are recorded in
[the proof](JOB_TERMINATION_PROOF_R1.json) and
[the manifest](JOB_TERMINATION_SOURCE_MANIFEST_R1.json). The contract command is:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File tests/contracts/VerifierContainmentContract.ps1 -ModulePath <module> -ExpectedModuleSha256 <hash> -ExpectedModuleBytes <bytes> -FixtureSourcePath <fixture.cs> -ExpectedFixtureSourceSha256 <hash> -EvidenceRoot <fresh-OS-temp-child> -Mode Suite
```

The selected host uses child-local built-in modules. `Add-Type` compiles the
fixture; its output image is authenticated before launch and checked against the
actors' actual image witnesses. This binds the measured artifact and host, not
the complete compiler/framework tool closure. The lab wrapper's whole operation
time includes its process wait; its outer cleanup is not measured separately.

Final-source gates PASS: job Smoke1 in2.111s, job Suite7 in2.046s, bounded
capture30 CODE cases in9.593s, legacy direct-child16 in3.574s and selected JDK8
tool controls3 in1.838s. Suite7 covers the live tree/sentinel plus empty/retry,
blank names, duplicate names, missing query name, missing executable and disposed
creator guards. Expected native failures are guard-test outcomes, not successful
process operations. The javac invalid-option exit2 remains EXPECTED_ERROR_NOT_PASS.
The capture30 CODE cases are unrelated to the excluded normal MEDIUM30.

Earlier failures remain failures. Two unchanged attempts to launch the selected
WindowsApps Python through JOB_LIST returned native error5, AccessDenied. Its
precise cause is unestablished; that path remains unqualified. A selected JDK8
creation/resume control and the compiled actor path work through the same owner.
Neither establishes a universal rule about WindowsApps or attests a JDK child's
actual image/exit. No security setting or breakaway policy was changed.

Early tree assertions initially assumed exactly two members, then immediate PID
absence. Actual Windows evidence identified two console hosts and asynchronous
exit. The final oracle vets all members and observes retained handles under the
original bound. Those corrections do not reclassify the old failures. An initial
maintained Smoke also failed before retaining member handles; cleanup could not
prove their exact-handle closure, and its error masked the original assertion.
The original trigger is not retroactively certified. The contract now retains
primary and cleanup failure details and compares unwrapped native errors.

The raw module prefix and suffix outside its existing native class are conserved;
the explicit CRLF checkout policy remains. Only three current module pins change.
Qualifier functions are AST-identical; the old PowerShell60c9 pin remains unchanged
and the actual normal qualifier/harness is NOT RUN. See
[source policy](JOB_TERMINATION_SOURCE_POLICY_R1.json) and
[pin migration](JOB_TERMINATION_PIN_MIGRATION_R1.json).

Root reviews the integrated changes and actual gates. Optional leaves review
source only and run no tests; the preserved Smoke functions are byte-identical
after Suite expansion. The later guard groups receive root review only. See
[the review scope](JOB_TERMINATION_REVIEW_R1.json). Existing native-launch rollback for a hypothetical
successful OS call with an invalid returned PID remains a nonblocking follow-up
to the future retained-handle launcher; that preexisting branch is unchanged.
Failure evidence, known CODE fixtures and unrelated worktrees/resources remain
preserved. Later query-only absence observations do not repair an earlier missing
handle proof.

A later query-only audit initially could not inspect one old descendant PID
(access denied). A read-only CIM observation reported a different creation time
and a system service using that PID. The original native-handle query stays
uncertified; the distinct CIM observation supports that the recorded old identity
is gone. Both records and the initial unresolved audit remain preserved. No
termination was attempted through the reused PID.

This accepts only creator-owned explicit shutdown. Captured native launch,
original child-handle transfer, pipes, native role caps, complete tool/environment
closure and trusted coordinator composition remain separate. No actual browser,
normal Study, original arm, capture graph, Session, receipt/export or genuine
operation is run. Both Study holds remain exact throw-only; original-to-NULL/OBSERVE
is UNSPENT_GATED. No oldnormal20, prior/oldD, held-out or protected DATA is read.
Full30 normal MEDIUM stays EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected
unread UNCERTIFIED; playable100=0; beta HELD. Budgets, seeds, epochs, order and
CircuitJS truth stay fixed. Production Java/GWT and visible Player are NOT
APPLICABLE; electrical/population evidence is NOT RENEWED. CI is NOT RUN pending
the existing self-hosted runner prerequisite. Next is a neutral captured-launch
adaptation through this same owner, with original handle and pipe lifecycle proof.
