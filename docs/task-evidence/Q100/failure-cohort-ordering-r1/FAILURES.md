# Development failures and reuse boundaries

The final R3 finite diagnostic is accepted and published; details are in
[QUALIFICATION_R1.json](QUALIFICATION_R1.json).
Original failed sources, receipts and private evidence remain retained. Later
success does not erase failed attempts or extend earlier component scope.

| Attempt or boundary | Observation and correction |
| --- | --- |
| Initial full-adapter worker | Returned NO_SOURCE_CHANGED because of its execution window. Root split and integrated bounded fragments. No compiler or routing result was produced by that worker. |
| Wire/initial integration | Review corrected passes0 to passes1 only at the constructed-Search mutation boundary; serialized initial Session remains passes0. Two pre-freeze assertions exposed missing terminal LF. Original bytes were archived and framing clarified before wire freeze. A constructor-signature text mismatch also rejected before target Java creation; it was not a compiler failure. |
| Partial-source components | Source14fae6a0 compiled in992.402ms against the old library; source448bda66 component compiled in982.0549ms. Neither component qualified the closed final source or consumers. |
| Full preparation R1 | FAIL NATIVE_SOURCE_FRAMING after18.2596327s/childexit2; compiler NOT RUN and stage empty. One CRLF at the integrated helper fragment's final brace violated framing. Failed source657c/helper5c were archived; only that line ending was normalized. No assertion, pin or budget was relaxed. |
| Full build/native R2 | Closed preparation, compile, finalization and native execution passed for source448bda66. The later source correction required fresh R3 gates; R2 build/native results were not reused as final-byte proof. |
| Authored independent consumer R2 | FAIL BF_TERMINAL_FAILURE_BINDING after28.1365581s. The reader required terminal ordinal == advanceCalls. Actual owner lifecycle clears the exhausted Search, then makes one final finishRejected call; ordinal+1 == advanceCalls is the exact relation. The reader was corrected and two ordinal mutants added. The old22-test PASS was not reused. Initial correction testing also exposed two outdated literal-canary ordinals; those fixtures were corrected before the final24-test pass. |
| Scoped Java review | Found that a non-NO_PATH skipped trial must retain a proven failed ID when terminal evidence exists, as the frozen wire requires. Root corrected the one conditional argument and reader equality, producing final sourcedc53245d. A post-fix read-only check closed this specific finding; it was not a full independent code/reader/electrical review. |
| Publication-summary preparation | First attempt assumed each control row contained statusPASS. Actual strict rows have exactly case/rejectedWith. The preparation logic was corrected to require those two keys,41 unique expected BF-code cases and the real all-expected flag. No runtime assertion, control or gate was weakened. |
| Final R3 | All15 fresh runtime gates, current-source byte/privacy checks and root resource closure passed. Integrated publication diff and staged-source hash checks also passed. This accepts only the recorded finite diagnostic. |

Final-helper real read/hash controls passed two positives/four specific negatives;
its bad-name/nonempty-stage/junction controls rejected with expected childexit2.
Native malformed-argument/junction controls rejected with expected childexit1.
All15 returned final processes have verified termination and absent process/log
roots. Parent timings include cleanup; a separately isolated OS-cleanup duration
is unavailable. No missing proof, unexpected exit or timeout is called PASS.

Control remains NO_PATH on B_COIL_LOW; cohort trial remains CAP_REJECTED on
D_COIL_LOW:BRANCH_SEARCH_LIMIT. Both have null layouts and whole-layout finalizers
NOT_RUN. These independently checked outcomes establish neither routing
impossibility nor an adopted treatment or production budget-reset/restart policy.
