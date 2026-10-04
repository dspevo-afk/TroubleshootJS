# Q30 R4 actual window 1: retained failure

One already-authorized normal-entrypoint attempt ended with DIAGNOSTIC_FAIL and
exit code 1. Diagnostic setup reported ValueError: diagnostic setup crossed
held-script attachment; final-scope capture reported CaptureIdentityError.
The original script was aborted. FAIL_CAPTURE has no metric delta or stable
terminal scope. The application report is unavailable and its outcome is null.
The case operation lasted 0.313187700 seconds and did not time
out; this is not the 90-second application timeout. The exact failed predicate
remains unknown because the receipt has no predicate operands.

The normal entrypoint ran for 6.2363098 seconds. Host
cleanup passed in 1.2621435 seconds. Capture cleanup
passed and disabled Performance. Outer and inner jobs reached zero active
processes; both output drains completed. The independent release check found
11 recorded PIDs absent, port 52550 closed, and zero terminations.

The release receipt's raw secondsAfterEntrypointExit value is preserved unchanged
as 14400.5279744. Its UTC timestamps give 0.5279744 seconds; the
raw field differs by 14,400 seconds. The derived interval is recorded separately
in actual-result.json.

Both input audits passed. All six candidate source pins match the R4 manifest,
and the 1,354 repository / 1,526 prepared-app counts and audited identities match
before and after. The fresh offline preflight passed in
1.3964178 seconds. The preflight process baseline,
host process inventories, large input manifests, launch receipts and logs remain
at their original locations and are bound by SHA-256 and byte count only; no
baseline inventory or raw log content is copied here. Copied JSON receipts have
personal absolute paths scrubbed. Raw source receipts were not modified.

Q30 remains disabled / NOT ACCEPTED with the original 31 PASS, one root75 TIMEOUT
at 90.221 seconds, and 45 NOT RUN. Limits remain 90,000 ms, 640 shared-work units
and 5,000 ms active operation. Root75's performance cause is UNKNOWN. The full
source archive gate is pending; U06, U07 and Q60 are unstarted. No production
Java/GWT change or build was part of this preservation task.

Packet manifest SHA-256: 83cc8e59e2ae90352073ad842c93b743bbc1fca296c2ceacfe12c3b21ac41004. Validation was offline receipt, hash,
binding and privacy-scrub review only; no candidate source was imported or
executed and no new live run was made.
