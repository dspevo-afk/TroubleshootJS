# Task 45 — typed electrical ports and pure compatibility preflight

Accepted Task 44 starting SHA:
`10dcafa36fa9c40d97ee6460aa41df5836e8ea9b`, branch
`codex/task43p-final-recovery`. Task 44 was pushed with exact remote-SHA proof
and its authorized completion email was sent. Its [acceptance packet](../task-44/README.md)
is preserved. Task 45 is the second and final task in the owner's authorization;
Task 46 and runtime composition remain unstarted.

**Status: COMPLETE — ACCEPTED.**
After the initial blocked handoff, the owner explicitly authorized the bounded
browser-verifier repair and completion of Task 45. The current task-report
checkpoint records that repair's focused probes and independent delta review.
All 55 selected actual-host legacy routes qualify. Final independent evidence
reconciliation and disposable resource cleanup passed. This packet accompanies
the acceptance commit; normal publication and the authorized post-push email
are recorded in the final handoff with the exact remote SHA and send outcome.
Historical evidence below stays separate from current qualification; original
failures remain failures.

## Current acceptance summary

| Gate | Current result |
| --- | --- |
| Pure contracts and Task 44 regressions | PASS, JDK8, 247 + 202 assertions; the nine source/oracle/runner hashes below remain unchanged. The latest root run used the repaired verifier module. |
| Final production build | PASS, exit `0`, all five JDK8/GWT OBF permutations, 40.082 s compile / 1.102 s link; bootstrap `79da00fca23f43d2b4b6b370eeb922672784c0434ff73b34f2cd1ab0d5a82fe3`. |
| Affected process-boundary probes | PASS, all nine probes freshly on final module `a5420969...` and unchanged oracle, including strict empty-identity, deadline, retained-handle and ownership boundaries. |
| Independent review | PASS for pure contracts, integrated cleanup repair/oracle, fixture, all diagnostics and Task39 evidence naming. Final independent reconciliation verifies every fresh Task39 artifact and the 20-invocation / 55-route summary. No findings. Fixture delta review was static; runtime validation is separate. |
| Final legacy set after the fixture correction | PASS: 55/55 routes in 20 complete exit-0 invocations, with exact cleanup; fresh Task39 also validates its session-scoped evidence fix. Failed invocations remain failed. |
| Resource closure and handoff | PASS, no owned process remains; disposable Chrome profiles/package/archive removed with exact ownership checks. Three earlier policy-rejected Edge profiles and private evidence receipts remain. Acceptance commit is ready for normal push and post-push notification. |

## Final legacy qualification

**PASS: all 55 routes in 20 complete exit-0 invocations.** Each accepted log
matches the closed route identities and its pinned inputs. Each matched run
manifest reports complete browser, preview, profile and lease cleanup, with
no cleanup errors; profiles and claim files were independently checked absent.
Passing observations inside failed commands remain excluded.

All commands below invoke `scripts/verify-browser.ps1` through Windows
PowerShell 5.1 with `-TimeoutSeconds 90` and the pinned explicit
`-BrowserPath` for Chrome for Testing `152.0.7977.82`. Arrays are PowerShell
arrays passed through `-Command`; every command owns its preview and browsers.

| Route arguments | Routes | Log SHA256 | Manifest SHA256 |
| --- | ---: | --- | --- |
| `-Rc -Seeds @(0,2,3)` | 3 | `7b1f7d69e9bac4d3d7c921a7151e69310a7a6590978e78b332447abefda44675` | `9494c1b77c11cd76958050b7fd62e98ca1682bd428f86f9e0b22ff3947ac9647` |
| `-Route challenge -Seeds @(0,2,3)` | 3 | `75a275dc7ae4f315c3fdaaa51df1fbfeca60dae736a4f561dcf65553d7c7ac5d` | `57e2bcc68bdce7e67b32903a19e902466cc632b41ea260d3a343de176308289f` |
| `-Route resistance -Seeds @(0,2,3)` | 3 | `ab5765daead99249983229ddc85f7b22563d7440683b67cddb7057ce22e57c83` | `0a03d13f7b99a7d62d7cc654cc1511ea63b739c17c869cbe76d175214852e65a` |
| `-Route meter -Seeds @(0,2,3)` | 3 | `3778a2002c8cced2fca86922f170f3aa3274924f12f3965b80fedd4a52a7abb6` | `132efef4454a62d0928053009626d8dfea94d002069309a07605fdc51f7edc81` |
| `-Route replacement -Seeds @(0,2,3)` | 3 | `ac620808f83bf3a8bae82bf25cda9b27cef70584d86f438871ec2056cc1e26ba` | `163c44d57aaedde2a3040392cc7f7f1169c87ac2a6995de1066c6c56d5351dc5` |
| `-Route challenge+replacement -Seeds @(0,2,3)` | 3 | `9a4649da1515fca84eb042e64494f31b5bc302ba45403fac2dbcd03c3a03074a` | `f347883c2a717646690c4df9cf89e01d6d656d3ae7c5374c3b1ce8b8487e12c6` |
| `-Diode -Seeds @(3)` | 1 | `27163e59e244858110bf0ae7e2e54f6aebf6683ba25ef2ae97c483db6d198e62` | `82cb69caa81f8d9b0db3243e09f0ccc6229af4d57b18128ce61720f19934e071` |
| `-Diode -Seeds @(0)` | 1 | `0b2067f62116e3660ce3b9e295d3e2181e4ff65912227c838d43021b2ad96273` | `980ce90f712252d725e32695b7b190df46e771ecd810094630186060c60bda83` |
| `-Diode -Seeds @(2)` | 1 | `510526f01be09f976208b3f80c3aa93a650dadb2ef70aca1e91fa33bddc38eed` | `b9fe99da5692b8435e74b6c00c8f919adc98850a084ec774d446f2e5274baad7` |
| `-Parallel -Seeds @(0)` | 1 | `2cc39ab57bca9032ebb495f7fdd99f220c8d11b22c9c4d9289b9f3c78d4561f3` | `4c9313b203121e2de0b5d9eeaf601ef7bbb0bbef1b662783e2a74c79452823bd` |
| `-Parallel -Seeds @(2)` | 1 | `af96b220dcf6e73103381190e42eec0c647f9d49b542727dbda511feecbb4895` | `f2a41147576e36966506d0f5cbc6d72308137d182315fa624a4048b9de8ad4f3` |
| `-Parallel -Seeds @(3)` | 1 | `d11e03c967cf65ba10d0d2fad7097c5cb543cefb579442d06ad0c98a27c07769` | `751ea824c89711ba6f86ff4924655659d68a721356f37078ebdb2c0d0bdef675` |
| `-Npn -Seeds @(0)` | 4 | `386f3aa78c2f3c37be22e3ebdae1cde4769d6249e8b0045af02fe3ea96a0834e` | `24536d48902e83c531bb842477e050797240df508c8675adb5270b07ec6c8e1d` |
| `-Npn -Seeds @(1)` | 4 | `58e8334bdd791b967c9d5efc0ff02db13fff77d023174a479e2c83f3b9a460f1` | `63c9f5c4394708e797926e64b1164074913cd3a40602ba0af09e6c830b7caf7d` |
| `-Npn -Seeds @(2)` | 4 | `ed9f0a27be6c512317e70bed3c95a62bd7e365db44d39b4778891032578c5233` | `db53d34fb3e41e32d663021fe96d0d56240f029d6b9048a06b6436e74bcad6b7` |
| `-Npn -Seeds @(3)` | 4 | `3dc7df37000f7f65649ff0d46378b5414f3fbecde3387934e93bb41f00853ba8` | `a9b84487f66010f2ae02fd3a2bd3656356c6e074e4e40ae03355e44af66737fb` |
| `-Nmos -Seeds @(0)` | 3 | `00e28eef75d90e86a7852d83ab07b237c9c00646ab73761ca679e8eab65362b4` | `69710bc3f5ee05055362cd5877357b3fa07cd78a78e33b68c7446070d559f5bf` |
| `-Nmos -Seeds @(1)` | 3 | `df6a31a622ddd063ca007e95b6904dd75741cec7d48b62580788a33a0c60d8d8` | `854d2122fde9c92b06a06b70208643306a60328198a530ab2813922c5d33b211` |
| `-Nmos -Seeds @(2)` | 3 | `d21657adab1981a1c21fc0c776f9686a461b9a25dec011db2caec85cfe2f5553` | `c6bb01ad94d9686252f400709b3bd83ad23909f3daa1915491bd44e83cbeac80` |
| `-Task39` | 6 | `833754744a26c5fda2886738a04a546f30aab4e6330aae67a5d628e288a6cb48` | `6c7b2b1223f67a01db86cceec01e72c9e54b0289993335322dce2ee995997e5f` |

Strict final qualification-summary SHA256:
`3ecdcb93b1e49b79f331678381f9dc76a31b96446c9c71ecb4f8ec605f9e8ac1`.
The summary records each exact route, run ID, UTC interval, command, module
and browser-verifier cohort, log/manifest hashes, cleanup and artifact evidence.
The final Task39 invocation ran from `2026-09-06T04:19:48.2374514Z` to
`2026-09-06T04:24:55.4356955Z`, run `537cd2daf30b497bb02adc54b9f1fb36`.

Completed earlier invocations use reviewed module versions that differ only
in failure-only diagnostics. Exact byte reconstructions establish the chain
to final `a5420969...`; source, oracle, browser binary and compiled outputs
remained pinned. The browser-verifier delta to `23e2b1d7...` is confined to
Task39 screenshot naming, outside all 49 reused routes. All six Task39 routes
ran freshly on that final verifier. Failed/unrun routes were never reused.

All nine registered Task39 screenshots decoded as nonblank `1424 x 849` PNGs.
Root inspected every image: NPN/NMOS show the separate load/control supplies,
the control action and the still-unrepaired customer-retest result; RC shows
its service ticket and power-cycle retest failure. RC has no preliminary
command buttons, so its initial and after-inputs images correctly match.
These are existing automated CDP checks, not manual visible-input proof.
The filenames are unique per session; identical image content is permitted.
No new player flow was introduced, and the diagnostic images remain with
their private run receipt instead of adding screenshot volume to the repository.
Root visual-audit receipt SHA256:
`c8587665f379c51c860b9d97ca6587bc54c1f74f09cacdfbc18670a9ecf493b3`.

The nine pure source/oracle/runner files remain identical to the 449-assertion
JDK8 run. The later loose-part fixture is outside that isolated class/source
path. No Java changed after the final fixture build at `01:59:05.9486467Z`;
the six compiled GWT outputs are unchanged. Subsequent PowerShell differences
are the reviewed failure-only browser diagnostics and Task39 screenshot names;
the production build and pure runner's successful bounded-process paths are
unchanged. This dependency audit supports the stated build/pure-suite reuse;
the final affected process probes and Task39 invocation ran freshly.

The 13 implementation/test/runner working-byte hashes are fixed by acceptance
fingerprint `301058d9eb0a111bf6343aee881665da28dbce0a4c0d29fdb6f0ae75c0f1ab13`.
It hashes UTF-8 rows of sorted repository-relative path, TAB and lowercase
SHA256, joined by LF without a trailing newline. The nine pure files are listed
below; the other four are the final module, Gate B oracle, Task39 browser
verifier and corrected loose-part fixture, each with its hash recorded here.
Documentation is excluded from that implementation fingerprint. The containing
Git commit records the complete accepted source and documentation candidate.

| Family / state | PNG SHA256 |
| --- | --- |
| NPN / initial | `a2a8565365109167e7ced46391327dcb78a74b4bdd431ee6322a84f415eae52a` |
| NPN / after inputs | `fa215992b9e72c0bb084a374f4ce8da171b192636f21f8f5fa0c929089bcf0dc` |
| NPN / retest result | `e7efe503a1681cdd89d302f3ecf7da81ab359850b0b42730575c4628133f8c17` |
| NMOS / initial | `85c1122d2490065faab8d98a163aab17a3c54eb177e805417a44bd74f43aa46f` |
| NMOS / after inputs | `c965a4b845fc35313eabb5a74f1a1dcb4b38e40881784e47e313bcb3e8d79763` |
| NMOS / retest result | `916100892a618923f0602b407528cd0f12c02549846da983e17f69854ac5c3d4` |
| RC / initial | `89a5564c1d1a7cfa59b23f1d92b4ce5c281b44b8bb3e1d9fd61f3f3d93207ae7` |
| RC / after inputs | `89a5564c1d1a7cfa59b23f1d92b4ce5c281b44b8bb3e1d9fd61f3f3d93207ae7` |
| RC / retest result | `afdb837643712a6a8ddc48a3ea48faff0c85fd9321f46557b98efd630d121ca4` |

## Final review, resources and follow-up

Fresh read-only Luna review independently reconciled the final six-route
Task39 log and manifest, all six unique sessions and exact URLs, seven released
leases, current process/listener absence and nine valid artifacts. It confirmed
all 13 implementation hashes and the aggregate fingerprint, all nine pure
inputs and every compiled/oracle/browser pin. The complete strict summary is
20 accepted invocations / 55 routes / no missing cases. No findings or blockers
remain. Review neither authored the implementation nor the test oracle.

At `2026-09-06T04:30:54.9785692Z`, final operational reconciliation found no task
Chrome process or failed-run marker and no listeners on all eight recorded
older failed-run ports. It verified each exact owner/manifest/path, removed the
three retained Chrome profiles and the disposable pinned browser package/archive
through the existing owned-tree helper, and rechecked original failed-manifest
hashes unchanged. Package and archive absence were then independently checked.
Final resource receipt SHA256:
`2da6d918d86bb14db29544c4df60e295750b3dc8ef54af79039cf498b9243b94`.
Later failed profiles had already been individually reconciled; every accepted
run completed its own strict cleanup. No owned browser, preview, build or test
process remains. Private logs, manifests, artifact images and receipts are
retained outside the repository; operational reconciliation does not rewrite
failed lease/run journals or promote exit `2` to PASS.

The three original Edge profile targets were not retried: automatic approval
review had rejected deletion with `blocked by policy` despite owner approval.
They remain with the original failed-run records. This residual file cleanup
does not invalidate the separately qualified Chrome invocations.

**FOLLOW-UP — TEST/TOOL:** intermittent missing/empty process-identity
observations can still fail closed on this host. The proven repeated-attestation
exit case is repaired; new captured-data diagnostics improve later diagnosis.
No general Windows/provider reliability claim or full historical process
certification is made. There is no remaining Task 45 acceptance blocker.
Task 46 and runtime composition remain unstarted.

## Candidate and policy

Seven new package-local immutable contract/checker/adapter classes define
`TSJ-PORT-PREFLIGHT-1`. They use Task 44's unchanged descriptor/namespace seam.
No pre-existing runtime Java, solver, generator, seed selection, input binding,
PCB, physical identity, mutation, fault, repair, measurement, build command or
player-flow implementation changed. The separately authorized verifier repair
changes `VerifierIsolation.psm1` and its focused Gate B regression oracle. The
subsequent legacy failure also required a one-file developer-fixture correction
in `PhysicalPartRenderDeveloperVerifier.java`; its product call paths are intact.

The policy and closed positive/negative matrix are recorded in the current
task report and architecture. Compatibility requires declared range containment,
adequate group current capacity, compatible direction/drive and relevant digital
levels, permitted scoped references/isolation, and declared accessibility.
Unknown and not-applicable evidence remain distinct. Adapter input/output sides
are explicit, with no implicit conversion or live net merging. Legacy adapters
copy authoritative external-input/pad/nameplate data; nominal-only metadata does
not manufacture missing specifications. Compatibility is only a declared-policy
result, not solver validation, safety certification or challenge validity.

## Verifier repair history and qualification

The owner authorized a bounded cleanup repair after the initial blocked handoff.
The reproduced missing descendant already had two verified attestations in the
same active drain. Repeated discovery now consults only the original private
record/handle binding when that exact child has disappeared or positively exited.
It validates the complete candidate identity and every retained binding, then
uses the existing pinned-handle exit and two independent current-absence proofs.
Lookup, comparison and proof share one outer 500 ms budget. An unattested child,
changed identity, foreign capability, current/reused PID, inaccessible provider
or expired deadline remains an infrastructure failure. Current parent/ancestry,
complete graph, root/listener, profile, lease and post-close checks remain intact.

The shared final live-process guard now reports an observed exit through the
existing typed missing-process error, while a changed start identity remains an
unconditional failure. Only the exact descendant's typed disappearance can use
its earlier attestation. The shared start-time helper is unchanged.

The independent Gate B oracle exercises the actual descendant-discovery path
with real Process handles and controlled fixture exits. It covers first-ever
missing/exited children, repeated attested exits at initial lookup and during
fresh identity proof, the real final non-null exited-Process guard, candidate
and capability mutations, current PID reuse/presence, unavailable/late proof,
and absence of termination on the natural-exit path.

| Current gate | Result and limits |
| --- | --- |
| Nine affected focused probes | PASS, all exit `0` on the final module/oracle pair after the failure-diagnostic delta. Exact probe names and boundaries are in the task report. |
| Fresh independent Luna delta review | PASS on the integrated final module/oracle below; reviewer authored neither implementation nor oracle. No unresolved correctness or ownership findings. |
| Early actual-host smoke | Diode seed `0` PASS, exit `0`, complete cleanup, after the earlier failures. A temporary missing-snapshot diagnostic message was never executed; original module bytes were restored exactly. This is supplementary host-path evidence. |
| First legacy matrix after the process repair, before the fixture correction | STOPPED ON FAILURE: LED 15/15, diode 3/3 and parallel 3/3 PASS with exit `0`; RC 0/3, all seeds fail solver restoration with `wire loop detected`, plus a separate seed-3 cleanup deadline failure (command exit `2`). Later groups NOT RUN. Source, module, oracle, browser and production-bootstrap fingerprints matched before each invocation. The seven groups / 12 invocations / 55 routes remain the closed set. |

Final module SHA256:
`a5420969284eb373f89aac3df978b1847b83fe445ee0f99fdfabf974feebffec`.
Final Gate B oracle SHA256:
`b3d1e1a96bc25a7306325b27d089855505d15de1205b1eb114ddf21af9f9cd36`.
The focused probe set is qualification of this affected boundary, not a claim
that the full historical Gate B or Task 43P certification matrices were rerun.

After the fixture correction, RC seeds `0,2,3` passed in one invocation with
complete cleanup and exit `0`; log SHA256
`7b1f7d69e9bac4d3d7c921a7151e69310a7a6590978e78b332447abefda44675`.
The subsequent LED invocation failed seed-0 challenge cleanup at a disappearing
child; 14 other routes passed, but its final exit was `2`. Its log SHA256 is
`9ddb79857d6dcd5120aa90913fca410076f7bb91165e7c88278cf91422611892` and original
manifest SHA256 is
`61ab0714159faa8e33435c18d6544c57887e4c3893be915ae08e6bf0973c9117`.
Operational reconciliation later proved all task-browser/run processes,
recorded root/parent/preview PIDs and ports absent and removed the failed profile;
the original failure manifest remains byte-identical. A bounded isolated
challenge diagnostic passed, with both temporary error-message annotations
unexecuted and the original module restored exactly. This is supplementary
evidence and does not upgrade the failed invocation.

Before further execution, Astra revised only the partition: the same 55 routes
use 16 invocations, with LED split into five `-Route` batches over `-Seeds 0,2,3`.
Each batch must independently return `0` with exact final cleanup. The 14 LED
observations from the failed command do not satisfy final acceptance. The full
RC invocation retains its proof after all pinned source, runner, verifier,
browser and six compiled-module hashes were rechecked unchanged; independent
Luna review inspected that dependency audit. No closed case or assertion is
removed, and no ownership deadline, browser setting or exit meaning changes.

The bounded LED challenge, resistance and meter batches each passed all three
seeds with exit `0` and complete cleanup. Replacement then failed on seed `2`
at the same initial missing-child boundary; that invocation remains exit `2`,
despite two passing routes. Its original manifest SHA256 is
`78eb3605e79ac669ca44b29ca77f0ab0247fa4d86715dbb43b09c0e36ce3a914` and its log
SHA256 is `c1d29dc835cc8367fb57c37d97d856526f05f28afdf6dfef9783fc7c8cf82b05`.
Fresh operational reconciliation proved its processes/listeners absent and
removed its profile without changing that manifest.

The final module now adds prior-binding count and sanitized Chromium type/subtype
to that already-selected failure. It reads only captured in-memory data, makes
no process/provider call, and preserves the same typed missing-process exception.
Fresh independent Luna review and all nine affected focused probes passed.
Replacing only this diagnostic block in memory exactly reconstructs the earlier
module hash `3ebbcac2db15ed6737a5e9086aab0cd65f4bc4e4d5c1d5b7ebeb96067d8a2d47`;
all other pinned inputs remain identical. The completed RC/challenge/resistance/
meter invocations retain their proof because this delta is unreachable on a
successful route; failed and unrun invocations do not. The remaining 43 routes
resume with replacement on the final diagnostic-capable module.

Replacement and challenge-plus-replacement subsequently passed all three seeds,
each invocation exiting `0` with complete cleanup. Diode then failed on seed `3`
because a current process observation carried an empty command-line identity.
That mandatory strict raw-identity check remains unchanged; the invocation stays
exit `2`. Its log SHA256 is
`89c5ab4d412410e0ffe9ad978ccd188cc914f2446b8e95269192d78066cb6e27`; original
manifest SHA256 is
`558410495e28649059b1c93b8826147b85f2646c7176bcba1f832fe951ad05e3`.
Fresh operational reconciliation proved processes/listeners absent and removed
only its profile, preserving the failure manifest. No source change followed.
Diode and parallel are now partitioned by seed, so the same 55 route identities
use 20 bounded invocations. Every invocation must independently exit `0` with
complete cleanup. The six complete invocations covering all 15 LED and three RC
routes retain proof; the failed diode invocation's two route observations do not.

The isolated diode seed-3 and seed-0 invocations then passed. Seed 2 failed at
the separate complete-process-snapshot boundary (missing PID); original manifest
SHA256 `39ee9d2794dd6d5203e048941f1844c0835287f65fd3f88d85316c9254089642`
and log SHA256 `0a62c98ca5e6a6beaae69c56e9dedfc06f1448acb8875c12c338602e37dfb35c`
remain failed. Operational reconciliation proved processes/listeners absent and
removed its profile, preserving the manifest. A second failure-only diagnostic
now records active drain/prior-binding counts, captured parent identity,
sanitized child type and function names at this snapshot boundary. It makes no
new provider query or ownership decision. All nine focused probes and fresh
independent Luna delta review passed on the final module above. In-memory
removal of only the new diagnostic and restoration of the touched line endings
reconstructs exact preceding module SHA256
`279052f98a67ca9b3c23a3f047aa755de1f51ce46b1053d62edb9d837dac1a05`.
The earlier reconstruction to `3ebbcac2...` and all other source/compiled/browser
pins also pass; completed successful invocations retain reviewed proof because
both diagnostic branches are unreachable on success.

Diode seed 2 subsequently passed on the final unmodified module, exit `0`, with
complete cleanup; log SHA256
`510526f01be09f976208b3f80c3aa93a650dadb2ef70aca1e91fa33bddc38eed`.
Parallel seed 0 then passed. Parallel seed 2 returned exit `2` at the existing
empty current-command-line identity guard; this is not an application failure.
Its log SHA256 is
`b868d9c9422a3c7c5c5e88987fee2a72abecb834169a986c069170b9f0539b42` and
original manifest SHA256 is
`765381b4114c6a4a9121e8e619fd585f3cad6ec9ec378794532bffafb251ffc1`.
Fresh operational reconciliation proved all task processes and both listeners
absent and removed only that profile, leaving the failure manifest unchanged.
The static investigation confirmed that empty data alone cannot establish exit.
A reviewed temporary observer matched only that selected error, attempted the
existing retained-handle exit proof for observation, and always rethrew the
original failure. Its diagnostic run passed without entering the observer;
exact original module bytes were restored. This supplementary run does not
qualify the final gate. Subsequent parallel seeds 2 and 3 both passed on the
unmodified reviewed module with complete cleanup, completing the parallel group.
No semantic change to the empty current-identity guard was made.

All four NPN seed-0 faults then passed with complete cleanup. NPN seed 1 stopped
at the exact descendant-query raw-record guard before child process validation.
That error reported the owned parent PID rather than the child PID. The failed
invocation returned `2`, log SHA256
`1496124b39cb49f518bdc168bae7ab7030af27fbcd385ef4fcb4912e5e0c866a`; original
manifest SHA256 is
`ecf97ee8be889e2c6137e02a3514580f8a0c24d472a24650aa5dfd5dc7da2d5b`.
Operational reconciliation proved all task processes and both listeners absent
and removed its profile while preserving the failure manifest.

The final central raw-record diagnostic appends the already-validated child and
parent PIDs, active-scope and same-PID/same-parent binding counts, and at most
twelve caller function names only after the mandatory empty-identity failure
has been selected. It performs no provider/handle query or acceptance action.
Diagnostic computation is guarded: any failure restores the original error
message, and the original infrastructure throw remains unconditional. A binding
count is investigation context, never exit or ownership proof. Exact in-memory
removal of this block reconstructs the preceding full module SHA256
`6b4ee8556e32fbc474f5ab18f2b5f8b80e2669c38b1021fca3c15945af4780a2`.
Fresh independent Luna delta review passed and approved reuse of completed
successful invocations after this audit; failed/unrun cases remain unqualified.
All nine affected probes freshly passed from 03:48:04 to 03:50:24 UTC on
2026-09-06; results SHA256 is
`50b14f8a4687075281c74d9ec5d0d03a7e5cbfb14aa6c9b9e28b92d7a1272f8a`.
The remaining NPN/NMOS/Task39 invocations resumed on this reviewed candidate.

All selected NPN and NMOS invocations subsequently passed with exit `0` and
complete cleanup, taking the qualified set to 49 routes in 19 invocations.
The first six-route Task39 invocation stopped at its fifth session: the second
normal-player family attempted to reuse `task39-player-initial.png` in the
shared run evidence directory. The existing no-overwrite guard correctly
rejected it. All five started sessions, the preview and all six leases recorded
complete cleanup; profiles and claim files were absent. The command's exit `2`
and four prior PASS observations do not qualify any part of that invocation.
Original failure manifest SHA256:
`635148045001aaf96527e32af796c5f7e2c26de11085c12076bec0cacad86bec`;
log SHA256:
`6eb8c0cd0b5ffdc8cf16bc4b91d0780ef98fb3f405ceda62f609a9926f7a09db`.

The local fix in `verifyTask39NormalPlayer` prefixes its three filenames with
the existing session `RouteId`. The three normal-player sessions therefore
produce nine distinct names. The established lease creates that ID once as an
N-format GUID; the fix introduces no counter, global state, ownership or path
exception. Overwrite and containment checks, artifact registration, URLs,
buttons, oracles and cleanup remain intact. Four unique-anchor mutation and
exact-restoration preflights passed. In-memory reversal reconstructs original
verifier SHA256 `f42d3e7e8947cf7b37dfb88efc947c674da69ffa596462a254459ccd2c193a93`
from final SHA256 `23e2b1d7eca31eb71386ddf14eb077accb0d4b8f5009f1faaa7f4316b276a2a4`.
Fresh independent Luna delta review passed and confirmed that none of the 49
qualified routes calls this Task39-only function. Their prior evidence retains
validity after exact dependency checks; all six Task39 routes require a fresh
complete invocation on the final verifier. Screenshots from these existing
CDP checks are automated diagnostic evidence, not manual visible-input proof.

Earlier repaired-host attempts also hit independent snapshot or 500 ms proof
deadlines and returned exit `2`. One retained browser was closed by separately
recorded operational recovery after fresh exact ownership/listener proof; the
original failed manifest and lease journals were not rewritten. Neither that
recovery nor a later passing route promotes an earlier failure to PASS.

The stopped matrix's RC run `016d85a71ce94659a6f9c110cd92b383` reproduced the
application error on seeds `0,2,3`; RC runtime Java is unchanged by Tasks 44/45.
Root preserved the failure and assigned a bounded read-only restoration-path
investigation. Operational recovery closed the retained seed-3 root and eight
children, proved current process/listener absence and removed the profile.
Original failure-manifest SHA256 remains
`ae9e9fb78cb1689e2603a6b3921166040a315f52b1ee7de90494a6df185cab33`.
The RC log SHA256 is
`7dd579c32159292bedc75d10f10234e603ab40021575dbe4fc8d496455188dd0`.

The bounded source diagnostic located six different synthetic loose-part wires
at each repeated terminal position; all production RC wires had resolved wire
information. This is **BLOCKER — TEST/TOOL**, not a defect in the RC topology.
`LooseProjectionLifecycleFixture` instantiated six `LooseRenderCanaryPart`
objects with the same hard-coded backing-wire coordinates, creating unintended
parallel wire loops. The private fixture factory now accepts a base coordinate;
the concurrent parts use `1000 + index * 256`, with the existing 32-unit terminal
pitch. Other factory calls retain their default coordinates. Endpoint object
identity, package geometry, live instrument checks and strict solver restoration
are preserved. Fresh independent review, final production build and legacy
qualification are required for this additional oracle path. Temporary source
diagnostics were restored exactly; no solver diagnostic patch is retained.

Corrected fixture SHA256:
`1cc9ac99146c7151aa24981c8b9a684edf516962525cdbc53212a05385dfaa75`.

Fresh independent Luna static delta review passed that exact fixture change,
with no findings. The final JDK8/GWT build passed all five OBF permutations,
40.082 s compile / 1.102 s link, exit `0`. Current bootstrap SHA256:
`79da00fca23f43d2b4b6b370eeb922672784c0434ff73b34f2cd1ab0d5a82fe3`.
Current build-log SHA256:
`ff4c4383877ec857b175ddbae1177692d4ba8d4e3ab2b5977f703c0d6e9e312d`.
The closed legacy set restarted with RC first against this reviewed candidate;
the original seed `0` reproduction now passes with complete cleanup. Each
invocation pins all five compiled permutation files, the bootstrap, both
restored diagnostic source files, the fixture, pure contracts and verifier.

## Initial focused and production gates before the verifier repair

| Gate | Exact command / oracle | Result |
| --- | --- | --- |
| Actual-source contracts | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-block-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07` | PASS, exit `0`; Task 44 nine groups / 247 assertions, Task 45 202 assertions; exact scratch cleanup. Root and oracle author ran separately. |
| Final production build | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF` | PASS, exit `0`; JDK8 `1.8.0_502`, GWT2.7, all five OBF permutations; compile 36.468 s / link 2.163 s. |
| Fresh independent review | Read-only Luna MAX `task45_review`, original requirement, integrated candidate, actual call boundaries and independent focused checks | PASS, no findings; independent JDK8 exit `0` (247 + 202 assertions), exact cleanup; whitespace and all nine manifest hashes matched. |
| Legacy generation/electrical parity | Closed commands below, unchanged owned browser/preview verifier, final build | BLOCKED: LED 15/15 exit `0`; diode exit `2` with unproven descendant cleanup on seeds 0/3; remaining commands NOT RUN. |
| Final reconciliation | Exact nine-file source/test/runner manifest, unchanged runtime dependencies, unstaged inspection, whitespace, empty index and actual status | PASS; only the 13 intended Task 45 paths are changed/new. Documentation-only closure preserves all nine tested hashes. |
| Final staging/publication | Explicit staging, commit, normal push and exact remote SHA; post-push Gmail | NOT RUN because mandatory legacy parity remains blocked. Task 44's published SHA remains equal to `origin`. |

The small direct Java-main harness compiles every new class explicitly with
JDK8 `-source 7 -target 7 -encoding UTF-8` and empty source/class paths. It therefore
exercises the new code despite its intentionally absent live application callers.
The bootstrap-classpath warning is diagnostic; the selected compiler/runtime
are verified JDK8. The production build remains a separate gate.

The independent oracle checks literal namespace addresses and stable failure
codes; full-range containment versus overlap; equal-voltage and three-port driver
conflicts, split connections and local aliases; aggregate loading; unrelated GND
references and forbidden isolation joins; digital thresholds/active levels;
accessibility; malformed numeric/reference/connection data; unknown versus not
applicable; repeated/reordered calls and defensive copying; all five adapter
kinds, bypass and isolated sides; legacy exact input mappings and separate
load/control nominal values. No production encoder generates expected IDs.

The root adjudicated two production defects before final validation: unknown
port diagnostics now use full v1 namespace addresses, and logic-to-analog
transfer returns insufficient/unsupported information. The oracle explicitly
distinguishes `UNKNOWN_ISOLATION` on an unqualified return join from
`REFERENCE_UNPROVEN` on a dependent signal. These cases are part of the final
candidate review; an earlier accommodated diagnostic run is not acceptance.

Final bootstrap SHA256:
`95f40c7b7b4d830170da7176d46fb92a4410798d05005bf788edddab722ef028`
(equal to Task 44's bootstrap). Private build-log SHA256:
`fb17a63a1117dcb2a20748a6609f21e5378427487a6b98a0446f13438374f97e`.
Private focused-log SHA256:
`448ed9d8b12a2cd6fb12137a6b8d3ec0351ce1f9ac0953c6112ae92375e05f93`.

## Closed legacy gates and initial results before the verifier repair

Each command uses `powershell.exe -NoProfile -ExecutionPolicy Bypass` to invoke
`scripts/verify-browser.ps1`, the existing owned browser/preview implementation and
`-TimeoutSeconds 90`. Arrays are passed as PowerShell arrays through `-Command`.
No external preview URL or Task43P-only startup flag is supplied.

The installed Edge `152.0.4191.62` failed descendant ownership cleanup. The
bounded equivalent uses the existing explicit `-BrowserPath` option with
standalone Chrome for Testing `152.0.7977.82`, downloaded into unique task-owned
OS-temp storage from [Google's official release](https://googlechromelabs.github.io/chrome-for-testing/).
No installation, global setting, browser-argument change, profile reuse,
verifier edit or deadline/ownership/assertion change was made. The official
HTTPS archive matched the Google response's content-MD5. Archive SHA256:
`460016c1ddba882bf253445175ab240dd947fa349bd720daeb0e311c17e74540`;
executable SHA256:
`ea36dd818a90176f1a70616f0363d9be527229389a6c073a0b1688b9e73f67e9`.
The downloaded testing executable is unsigned; no Authenticode claim is made.
The same verifier's `-Route resistance -Seeds 0` probe passed with exit `0`,
complete cleanup, and 26,961 ms elapsed / 65,254 ms remaining route time before
the full set began. This is behavioral parity through the unchanged verifier,
not a new Gate B browser/process certification.

The root explicitly selected this supported executable configuration after the
Edge failure. Read-only Luna inspection confirmed exact configured-executable
ownership on the existing path and inspected the successful probe's real
CDP/process/listener/preview/profile/lease receipt. This does not waive any
route, assertion, deadline or cleanup requirement. The full LED result below
passes under that configuration; the later diode failure still blocks parity.

| Route arguments | Coverage | Result |
| --- | --- | --- |
| `-Seeds 0,2,3` | LED, five lifecycle routes per explicit seed | PASS, all 15 routes; command exit `0`, exact cleanup |
| `-Diode -Seeds 0,2,3` | Diode family | BLOCKED, command exit `2`; seed 2 PASS, seeds 0/3 cleanup unproven |
| `-Parallel -Seeds 0,2,3` | Parallel family | NOT RUN after repeated cleanup failures |
| `-Rc -Seeds 0,2,3` | RC family | NOT RUN after repeated cleanup failures |
| `-Npn -Seeds 0,1,2,3` | Four forced faults per seed; deterministic 9/12/5 V load versus 5 V control nameplates | NOT RUN after repeated cleanup failures |
| `-Nmos -Seeds 0,1,2` | Three forced faults per seed; stable G/D/S, control-net mapping and solver checks | NOT RUN after repeated cleanup failures |
| `-Task39` | NPN/NMOS healthy operation and separate control/load behavior, RC retest and existing player-control checks | NOT RUN after repeated cleanup failures |

Run sequentially. Exit `2`, missing proof, timeout or unproven cleanup is never
PASS. Repeated infrastructure failures require a specific prerequisite diagnosis
before continuing unchanged runs. No Task43P recovery matrix/source falsifier,
new process-certification platform or fabricated screenshot gate is selected.
For execution, the NPN and NMOS seed sets are split into one invocation per
listed seed, retaining every four-/three-fault route. This keeps each live run
ledger small; it does not remove a selected input or assertion. Each invocation
must independently return `0` with exact cleanup. A temporary sequential command
wrapper records exits and expected/observed route counts and stops on failure.

### Final Chrome results and missing prerequisite

The final LED command returned `0`: run
`c434d8c139eb422ababdeee0d65e1841`, created `2026-09-05T23:08:40.7599378Z`,
completed `23:19:43.5914267Z`. All 15 sessions and all 16 port leases record
complete cleanup; the run's cleanup errors are empty. This is the full selected
LED proof, separate from the earlier single-route probe.

The next command was the unchanged verifier with `-Diode -Seeds @(0,2,3)`.
Run `b19ee4dedcae4fdcb2a41b665dedb502` recorded one PASS (seed 2) and two
infrastructure failures. Both the verifier and the exit-preserving wrapper
returned `2`; the remaining closed commands were not launched.

| Route / phase | Operation and failure | Elapsed / remaining route time | Cleanup outcome |
| --- | --- | --- | --- |
| Diode seed 0 / browser cleanup | Could not inspect descendant PID `340900`; last CDP `Runtime.evaluate`, stage `complete` | 34,329 / 57,881 ms | Unproven; later drain did not reach a bounded fixed point |
| Diode seed 0 / final route cleanup | Same cleanup failure | 50,392 / 41,820 ms | Failed; root PID `349904` later disappeared before complete descendant proof |
| Diode seed 2 / route cleanup | Route PASS | 45,920 / 46,520 ms | Complete |
| Diode seed 3 / browser cleanup | Could not inspect descendant PID `166788`; last CDP `Runtime.evaluate`, stage `complete` | 33,383 / 59,382 ms | Unproven |
| Diode seed 3 / final route cleanup | Port `49882` release blocked by uncertain ownership | 50,439 / 42,327 ms | Failed; root PID `303700` later disappeared before complete descendant proof |

The run ended at `23:23:40.9041799Z` with cleanup state
`infrastructure-failure`; browser ports `54068` and `49882` retained active lease
journals and profiles. The preview at port `54056` and seed-2 browser lease
completed cleanup. A completed CDP evaluation does not establish an application
PASS when cleanup remains unproven.

**BLOCKER / TEST/TOOL:** required legacy proof cannot be qualified through the
existing descendant-ownership cleanup boundary on this host. Edge and two
Chrome diode cases reproduce disappearing/uninspectable descendant failures.
The exact host/provider cause remains unresolved; no contract-code regression
is established. The smallest prerequisite is a separately scoped verifier/host
correction that can preserve and prove descendant identity, drain completion,
profile ownership and lease release under these cases, with its affected
positive/negative checks. It must retain the existing 500 ms ownership proofs,
isolation and exit semantics. Ignoring missing PIDs or weakening cleanup would
not satisfy the requirement. This task expressly excludes browser/process
isolation redesign, so no speculative verifier patch or unchanged full retry
was added. Diode, Parallel, RC, NPN, NMOS and Task39 remain required before
Task 45 acceptance/publication.

### Final resource reconciliation

At `2026-09-05T23:31:46.6704775Z`, the existing complete process-ownership helper
qualified both configured browsers. All 14 known failed-run parent/root/child
PIDs and all run-marked processes were absent; no task Chrome executable was
running. The existing listener helper proved absence on all eight failed-run
ports (`62739`, `62753`, `60489`, `57709`, `54056`, `54068`, `64426`, `49882`).

With exact receipt ownership and fresh process/listener absence, the existing
`Remove-VerifierOwnedTree` successfully removed the two retained Chrome profiles
and the downloaded task-only browser package. Its provenance was retained
separately. The three previously rejected Edge profile targets were not retried.
No task-only process, server, browser or build remains running. Three Edge
profiles, failed run/lease records and the unique private execution receipt
directory remain. Historical cleanup results and manifest bytes were preserved;
post-run file removal does not retroactively make the diode verifier PASS.

Private receipt hashes (raw records remain outside the repository):

| Receipt | SHA256 |
| --- | --- |
| Full LED log | `854689b929ecf39c782e4b7c795135aca4afad0ba4ff96e81fda3b9f8a91427d` |
| Diode log | `e17826c5bc81489c5633698f038c3f07826bf8c99149175ad6ba69e9ceff9a7a` |
| Unmodified diode failure manifest | `d825ae5d452ad002a852d7d99a24ec92c1c6028928ddd177d284892236b32b69` |
| Closed-wrapper result | `7e78a29be882e0f92cc354703dea015fc71fba723df5a70435c372404be783b5` |
| Final resource reconciliation | `8d2ee8b7ed522803d157ee43948f7b54d95b5c689cc34c9f723d7cc7c8500ff1` |

### Failed Edge attempt and resource reconciliation

The original default LED command reached `seed=0 resistance` and `seed=0 meter`.
Both reported browser-descendant ownership/cleanup failures: a disappearing or
uninspectable descendant followed by failure to reach a bounded drain fixed
point. Resistance recorded 17,224 ms at its initial cleanup failure and
34,414 ms at final route cleanup failure (57,768 ms remaining). Meter recorded
7,375 ms initially and 25,029 ms at final route cleanup failure (67,599 ms
remaining). Both last CDP operations were `Runtime.evaluate`, stage `complete`.
No qualified application/parity result is inferred from that operation state.

The root stopped the owned exec session as the default loop continued into
`seed=0 challenge`. The host interruption returned `1`; this is an interrupted
run with infrastructure failures, not an application failure or qualified
verifier exit. No remaining command was retried unchanged on Edge. The failure
manifest is retained without rewriting its pending/failed cleanup states.

Existing complete process-ownership and listener helpers subsequently found no
known or run-marked processes and no listeners on its four leased ports.
Disposable profile cleanup through `Remove-VerifierOwnedTree` failed when its
physical-path proof could not open an Edge metadata path. Automatic approval
review then rejected native recursive profile deletion with `blocked by policy`.
The user explicitly authorized deleting the three profiles. Fresh ownership,
containment, full no-reparse and process/listener absence checks passed, but
automatic review again rejected the validated native deletion and then the
narrower command naming the exact three literal paths. No further deletion
attempt was made. Three profiles therefore remain quarantined
with the failed run's records under the OS-temp verifier run
`20cd7fcd353094d844fb/cbc31697b4c642df95b2436c31293904`. This does not upgrade the
failed verifier cleanup to PASS. It is separate from the required final Chrome
run's own exact cleanup result. Private interrupted-run and reconciliation logs
remain in the task's unique receipt directory.

A subsequent full Chrome command failed before any browser session when preview
port-lease ownership exceeded its existing bounded monotonic deadline. Run
`935a3f26ac5443138c7c95298221b47c` retained complete cleanup, an absent terminated
preview, released lease and no browser sessions. Its failure is not a circuit
result. The initial PowerShell `-Command` wrapper mapped a nonzero child status
to host `1`; later invocations explicitly propagate `$LASTEXITCODE`, verified
with a cheap exit-2 canary. Neither status is treated as application failure or
PASS. The final Chrome run uses this exact exit-preserving invocation.

## Tested candidate file manifest

The first seven paths are under `src/com/lushprojects/circuitjs1/client/`.
The accepted Task 44 HEAD plus these working-byte hashes identify the reviewed
pure Task 45 source candidate. The containing acceptance commit includes this
manifest and the separately pinned tooling changes above. Documentation-only
evidence updates do not change these tested source, oracle and runner bytes.

| File | SHA256 |
| --- | --- |
| `ElectricalContractException.java` | `44d8c7650fde3316d2d12b49636372901cd62dcf42620fd7e634c9e225b04cff` |
| `ElectricalPortContract.java` | `78e188b31b484a20e0e0b8ffbaa9fae263569b582e9514a041f4fa21e8fe9369` |
| `ElectricalBlockContract.java` | `ba4e363bbc26e35f4cb7ae31ec44cb1ee09fde33760a2471c2b5faea37022f5c` |
| `ElectricalConnection.java` | `d464ebb174699aeba4c40fc883d137836f17663411ed4a20b4c29b7fb3da194c` |
| `PortCompatibilityPreflight.java` | `19f5acce9e7213959b97868703b02333cac4e5060e74dd8aca12f60dd673142a` |
| `LegacyInputPortMetadata.java` | `ce92e821d386aee56eb39480bf7747019a2e71daa09733e8999bb19f99ca10d1` |
| `LowSideSwitchInputMetadata.java` | `1ea4ae3f64a9f16a79e45f30a960eb6fd2bf94313e5223414901a856c467174f` |
| `scripts/verify-block-contracts.ps1` | `cc10ea74cf9223d525c02f2d8375fe7eb6ae7113a00c63fbd08e55e29b1ac4a4` |
| `tests/contracts/ElectricalPortContractTest.java` | `ac4c01a0c8ad55734be4e182b212393c38a0922d81039432fbed56a784b39794` |

The CI workflow remains absent from the default branch; no CI dispatch is
claimed and default-branch installation remains separate preparation. Historical
43P acceptance is retained with its original limits; this batch does not certify
runtime composition.
