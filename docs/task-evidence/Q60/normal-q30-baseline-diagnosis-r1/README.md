# Q60 normal Q30 baseline diagnosis

Current B source is published `9efe177e3508b3db67434759c0a8a7673bd92c2e` on
`codex/q60-faulted-profile-counts`. This is a documentation checkpoint, with
zero production, controller or build changes. Q60 remains **IN PROGRESS / UNACCEPTED**.

The original normal control 108's failure cause was never retained. Its normal
player completion uses its own callback rather than the default generation log;
the ERROR snapshot drops progress and does not expose the exception. The closed
browser cannot supply that missing data. Timing near 90 seconds is consistent with
a deadline, but does not prove which terminal failure occurred.

## Actual fixed comparison

Both arms use genuine normal Prepare input for RB30_CONTROL/MEDIUM/10014,
fresh profiles, the same reference Edge transport and original 90 s / 640 work / 5 s limits.
A serves the exact archived build 25 assets; B serves current build 29 assets.
All 392 providers per arm and actual selected cache bodies were attested. The
1,222-input maps differ in five production count paths; CirSim/LuChecks bytes
match. No source was rebuilt or copied into a second project.

| Run | Measurement | Application | Exact job milliseconds / work |
| --- | --- | --- | --- |
| 120 LED transport canary | PASS | PASS; ticket/workbench/menu | 419 / 59 |
| 121 B1 | PASS | PASS; ticket/workbench/menu | 87,840 / 448 |
| 122 A1 | PASS | PASS; ticket/workbench/menu | 89,053 / 448 |
| 123 A2 | PASS | PASS; ticket/workbench/menu | 79,390 / 448 |
| 124 B2 | FAIL | Public ERROR | UNEXPOSED |
| 125 Prepare/Cancel reader canary | PASS | Expected CANCELLED; admission N/A | 1,710 / 8 |
| 126 separate cause follow-up | PASS | PASS; ticket/workbench/menu | 88,857 / 448 |

124 observed ERROR after 91.0280901 s; maximum sampled work 433 is not terminal
work. Its reader failed on omitted `Throwable.cause`, followed by the retained
final orphan-audit error because classification had not been reached. It stays
FAIL. The source-bound correction accepts default-null causes only for verified
Cancelled/Deadline routes; failed jobs still require real non-null exceptions.
125 exercises the actual Cancelled omission. Deadline omission and partial-error
retention have static proof only. 126 does not replace 124, and the original paired
measurement remains **INCOMPLETE**. No repeat cohort or best-result selection.

## Limiter and load decision

Exact PASS controls require 448 work, including 390 HYPOTHESES units. HYPOTHESES
sampled active-unit wall telemetry is 66.902–74.564 s; it excludes pauses/yields and
is not CPU or whole-stage timing. Archived A varies by 9.663 s with identical work.
The narrowest observed PASS margin is 947 ms;126 leaves 1,143 ms. These results do
not establish a speedup, regression or reliable deadline clearance.

Whole-recording CPU busy is 16.2587/16.8732/16.4680/19.2054% for 121–124 and
11.5131% for 126, normalized over 16 logical processors. QEMU headless remains
active at roughly 0.27–0.33 core equivalents. 124 additionally records two new
Java identities with30.96875 observed CPU seconds during partial ~12-second
windows. Their ownership and failure causality are unknown. No unrelated
process was terminated. There was no overlap between owned expensive gates;
the historical audit covers 255 retained intervals, not historical CPU parity.
Thermal/frequency, per-core scheduling and JIT/GC parity were not measured.

**Decision:** retain the published count fix and restored numerical baseline;
select no production optimization from this incomplete comparison. Reuse the
rejected-storage archive and zero-hit cache census without another experiment.
A future correctness-preserving candidate that reduces hypothesis proof cost needs a complete
baseline comparison and the original real admission/player gates.

All actual source checks and browser/application/native/CPU cleanup pass within
their separate scopes, including cleanup of failed 124. Its measurement does not
pass. Independent closure review verified 1,855 distinct frozen hashes across
121–126; root preserved the exact eight original untracked files and controller.

Current restored-source full6 is NOT RERUN. Historical typed-prototype 55/60
timeouts remain FAIL; normal RB56/HARD/save-load are held. Exact 60 visible/session
is NOT RUN, and actual modest hardware is BLOCKED/unavailable. Original 91.616 s
unknown failure, six missing inventories and late BLOCKED audit remain preserved.
No Core/Android/server work or email.

[Sanitized receipts and hashes](baseline-diagnosis-evidence-r1.json) retain each
result and distinct clocks. Raw private process/profile/transport archives stay
outside the repository; the packet identifies them by relative artifact ID/hash.
