# Native creator-job capture coordinator

N61 is locally accepted for its finite serial-host scope. Normal-native and
genuine admission remain closed; implementation `0b231496e95b0b3628fa5912c86ba9714a6bc8dc` is published.

`Invoke-VerifierBoundedProcess` gains opt-in `CreatorJob` and `WorkingDirectory`
parameters. Selecting either requires both, paired explicit byte caps and an
explicit `CancellationToken`. It validates the exact native job object, positive
integral timeout/caps, absolute executable and working-directory paths, existing
directory and initially empty dedicated job. The existing native owner checks
creator capability before pipe allocation and live launch. Query denial can
retain an owned evidence directory; it does not grant launch or stop authority.

The executable and argument array remain the sole transport inputs. The existing
Windows serializer derives the complete argv0-inclusive command. The coordinator
calls the same `LaunchCaptured` and byte-state/receive/drain/facts helpers; it
does not introduce another launcher or capture loop. Omitting both native options
selects the unchanged direct-process body and its previous result/fact shape.

Native success requires the original root handle to signal, the current job to
be empty, reader tasks to be terminal, read handles closed and the process lease
disposed. The caller retains the job lifetime. Natural settlement observes the
original operation deadline and cancellation. Failure stops only through that
creator job, then proves root/current-job closure within the existing five-second
clock and uses the existing five-second bounded reader drain. Failed operations
retain bounded raw prefixes and typed operation/cleanup facts. Exit and identity
facts are snapshotted before disposal; no PID reopening or PID kill is used.

Native mode adds `CaptureFacts.NativeContainment`; failed calls also carry
`Exception.Data['VerifierNativeContainment']`, including the evidence-root path.
It distinguishes FILETIME from .NET ticks, reports original-root/current-job and
reader/lease closure, and keeps separate operation/cleanup phases and clocks.
`NativeExitCode` records the DWORD bits; `ProcessExitCode` and the returned exit
code preserve the existing signed Int32 semantics. No lease means no observed
image, FILETIME or exit code: these remain empty, zero or null as appropriate.
Nonzero child exits are transport results, not successful native study operations.

The scope is a serial dedicated host and exclusive caller job. Temporary
inheritable writer/NUL handles can leak into unrelated concurrent process
creation; no class-only lock could protect those other owners. Concurrency is
not qualified. A finite PID snapshot also cannot establish waits for every
historical descendant. The adapter claims original-root signal, current-job
emptiness and reader closure. Independent retained known-member handles qualify
only their finite fixtures. `DescendantHandleWaits` remains `NOT_ATTESTED`.

[Source policy](NATIVE_COORDINATOR_SOURCE_POLICY_R1.json) conserves every raw byte
outside the inserted helpers and invocation header/dispatch. The whole old direct
body, native class, existing reader and stopper implementations remain exact.
The module retains its CRLF checkout policy. Three
[current module pins](NATIVE_COORDINATOR_PIN_MIGRATION_R1.json) migrate only;
qualifier bodies and the old PowerShell60c9 pin stay unchanged and normal entrypoints
are not run. [Review scope](NATIVE_COORDINATOR_REVIEW_R1.json),
[source manifest](NATIVE_COORDINATOR_SOURCE_MANIFEST_R1.json) and
[proof](NATIVE_COORDINATOR_PROOF_R1.json) will record final evidence and limits.

Early selected-host JDK output and actual missing-executable failure pass. Initial
maintained Smoke failed because its assertion tried to canonicalize an empty
observed image for a process that never existed. Corrected Smoke preserves that
N/A identity and passes; the earlier failure stays FAIL. A separate literal
return-1 control compares the unchanged direct path with native capture and
proves signed -1/raw DWORD4294967295 without treating nonzero exit as success.
Source-only review does not substitute for the final actual host gates.

Expanded Suite failures also remain preserved: a missing envelope field,
uninitialized failure evidence, an emitted but unloaded observer DLL and an
observer collection-shape mismatch. A focused selected-host diagnostic found
that typed dictionary deserialization produced `ArrayList`; `DeserializeObject`
produced the strict object array required by the observer. The original failed
observer's inner detail was not retained and is not retroactively recovered.
Root also required the outside sentinel and monitor handles to signal before
disposal, following the bounded source review. Added request-boundary canaries
cover missing options, wrong types, relative/missing paths, closed and nonempty
jobs; the existing busy original root survives rejection. Their initial parser
and setup-count assertion failures remain FAIL; the final assertion compares the
exact before/after namespace. No budgets or behavioral assertions were weakened.

Final-source gates PASS on the selected PS5.1/Python host. The table shows
whole outer-wrapper operation time; case operation and cleanup clocks are separate
in the proof. Outer cleanup is not measured separately. Expected failed and
nonzero child operations are not successful native studies.

| Gate | Cases | Wrapper operation |
| --- | ---: | ---: |
| native-smoke-final-r3 | 2 | 2.167 s |
| native-suite-final-r8 | 11 | 8.009 s |
| pipe-suite-final-r2 | 10 | 5.775 s |
| lease-suite-final-r2 | 3 | 2.015 s |
| job-suite-final-r2 | 7 | 1.933 s |
| capture-final-r2 | 30 | 8.850 s |
| legacy-final-r2 | 16 | 3.444 s |
| tools-final-r2 | 3 | 1.856 s |
| near-exit-final-r2 | 32 | 4.038 s |
| direct-stop-final-r2 | 4 | 1.623 s |
| rollback-control-final-r4 | 1 | 1.302 s |
| signed-exit-r1 | 1 | 1.683 s |

The final query-only audit covers 271 known identities and 33 original
outer exits. It grants no termination authority and repairs no missing historical
handle proof. No new persistent resource is known. Private source variants,
commands, receipts, expected-failure prefixes and earlier failures stay preserved.
All consumed inputs for reused passing baselines are rechecked unchanged.

No native role budgets, full tool/environment/Source/JAR/return authority,
production native preflight, creator-death protection or genuine composition is
admitted here. Explicit CODE caps are test inputs. Both Study holds stay exact
throw-only; original-to-NULL/OBSERVE is UNSPENT_GATED. No normal qualifier,
harness, Study, OriginalArm, capture, graph, Session, receipt/export or genuine
operation runs. No prior/oldD/held-out/protected DATA or generated DATA factory is
read. Full30 normal MEDIUM remains EXCLUDED; HARD/PSYCHOTIC unavailable/unrun;
protected unread UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD.
CircuitJS truth, seeds, epochs, ordering and existing budgets remain unchanged.
Java/GWT/visible Player are NOT APPLICABLE to this tooling-only change;
electrical/population qualification is NOT RENEWED. CI is NOT RUN pending the
self-hosted runner prerequisite. Next is explicit invocation policy and trusted
tool/Source/return composition before any normal activation.

Implementation `0b231496e95b0b3628fa5912c86ba9714a6bc8dc` (Coordinate bounded native capture
through creator jobs) was normally pushed to origin/codex/q100-physical-feasibility
and the exact remote SHA verified. Completion Gmail `1a1ce458d4dd776d` was sent
once; this documentation-only receipt sends no second email. The15-path index
audit passed with SHA `7045a8c02582cc34f3feb7fcc582a8e2718f76b70325b88adc93013c708ac5e6`. All source-bound
passing evidence is reused only after rechecking unchanged consumed inputs.
