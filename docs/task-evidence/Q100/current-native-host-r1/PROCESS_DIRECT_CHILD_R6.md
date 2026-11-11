# Existing Windows direct-child process contract

N56 adds a maintained opt-in contract and a small CODE-only Python witness for
the unchanged `Invoke-VerifierBoundedProcess` implementation. It closes a test
coverage gap before native coordinator composition. It grants no normal native
transport or Study execution authority.

Final-source R6 Windows checks PASS: Smoke2 and Suite16, exit0, in1.582s and
3.742s including wrapper startup/import. Cases exercise literal argv edge values
(empty, spaces, hash, semicolon, quote, trailing backslash, Unicode), exact
stdout/stderr, distinct child exit0/7, three rapid returns, a real1500ms timeout,
eight malformed timeout types/values and two malformed argument shapes. The
fixture uses Win32 current-process creation time, image path and raw command
line, plus image SHA and Python argv. Positive PID/start ticks match the owner's
retained launch identity. The timeout operation is EXPECTED_TIMEOUT_NOT_PASS;
the contract passes because the exact timeout/cleanup behavior was observed.
The malformed requests require exact pre-start guard messages and no fixture
state; no OS process-creation audit was performed.

The [source/tool manifest](PROCESS_SOURCE_MANIFEST_R6.json) binds final inputs.
The [proof projection](PROCESS_PROOF_R6.json) preserves phase times, original
wrapper handles and final case facts. [The maintained contract](../../../../tests/qualification/rb100-layout/current-native-host-r1/PROCESS_CONTRACT_R1.md)
describes invocation and limits. Selected Python3.13 runs with `-I -B`, optimize0;
the actual selected Windows PowerShell5.1 image is495616B/d8f182..., version
10.0.26100.9818, with a valid Microsoft Windows signature. This differs from the
old qualifier's60c9... pin. That pin stays unchanged and is not renewed; the
actual qualifier is NOT RUN. System built-in modules were selected for this
canary, not certified as a complete tool closure.

Early actual-host smoke failed before child launch because inherited PS7 module
search paths prevented PS5.1 Utility autoload. A child-local `PSModulePath` using
the selected SystemPS sibling Modules directory fixed it; global environment,
module installations and execution policy remain unchanged. Subsequent test
failures exposed a nonexported existing parser, Python text-newline translation
and an omitted infrastructure-message prefix in the negative oracle. The
contract calls the parser through its exact module scope, the fixture writes
literal ASCII bytes, and the oracle includes the unchanged original prefix.
Failures and their frozen source/state/output receipts remain retained. No
assertion, owner, budget or electrical oracle was weakened.

R5 strengthened exact argv guards and complete counts; R6 also requires nonblank
absolute returned log paths before the cleanup-absence check. Both final-source
gates were freshly run. Root reviewed the integrated source, all16 case results,
stdout/stderr, identities, receipts and limits. The leaf's source-only review
found no blocker; its cleanup-oracle observation was fixed and separately
reviewed. The leaf ran no gates. The initially delegated implementation produced
no persisted file before root reassumed ownership; root wrote both test files.

All12 lab-wrapper exits were observed through their original Popen handles.
Successful child log roots were removed by the unchanged owner. Four expected
timeout evidence directories and ordinary fixture states remain deliberately
retained; no new persistent process, listener, browser or reparse node remains.
Operation times include owner cleanup; separate successful cleanup is
UNMEASURED, not zero. The private outer lab also bounds output only after capture.

The owner still uses unbounded `ReadToEndAsync`, has no cancel parameter and
stops only its direct child. Early output byte limits, cancellation, descendant
containment, external namespace races, complete tool/JAR/compile authority and
real qualifier-return/native coordinator composition remain unqualified. These
limits prevent promotion of this canary to normal transport acceptance. The
standalone contract is not inserted into the full Java verifier in this substep.

No actual prior/fixed20/oldD/heldout/protected DATA or generated DATA factory was
opened or run. Both Study holds remain exact throw-only; genuine original-to-
NULL/OBSERVE remains UNSPENT_GATED. Budgets, seeds, epochs, order and CircuitJS
truth stay unchanged. Full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected
unread UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD. Production
Java/GWT and visible Player checks are NOT APPLICABLE; electrical/population
evidence is NOT RENEWED. CI NOT RUN/self-hosted runner prerequisite remains.
Previous N55 and earlier evidence is retained, not refreshed by this process
test. Unrelated worktrees and old failures remain preserved.
