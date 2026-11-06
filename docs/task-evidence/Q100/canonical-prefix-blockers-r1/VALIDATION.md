# Final-source validation

All rows below are PASS for input-pins-r7 SHA
`1e570d555c919755d82e5e0f0c728425c7aa313c3bd2c123ebfd874832a7b636`.
Each selected PS5 command compiles1079 explicit inputs with the pinned JDK8,
checks source/bridges before and after native execution, runs the real producer
overflow negative, and removes its verified task-owned scratch. Compiler, native,
auditor and independent reader child termination is proven. The13-test selected
Python gate passed in0.014s; its consumed reader/tests remain unchanged.

| Mode | Declared work s | Native wall s | Conservative cleanup incl. overflow s | Outer wall s |
| --- | ---: | ---: | ---: | ---: |
| CANARY | 1.832814 | 2.006338 | 39.729385 | 85.047807 |
| CONTROL | 13.585280 | 13.723987 | 39.776714 | 96.964481 |
| CAPTURE | 13.657005 | 13.805484 | 39.609126 | 96.968633 |
| INSPECTOR_THROW | 13.638835 | 13.807869 | 53.697687 | 96.805166 |

Normal work excludes the actual owner cleanup only. Exception work is inclusive;
its cleanup upper charges the entire native wall plus post-source/scratch cleanup.
The overflow negative is added to each cleanup upper above. Exact values and raw
receipt hashes are in qualification.json. All remain below90s work/120s cleanup,
with640 groups/5s group/32768 owner steps/1M expansions/48holes/4flips unchanged.
Observation chunks4096, each clone<=1M entries, total observationunits<=32768;
64KiB lines/index/attribution,8MiB artifact/output,60s compile,480s native and1800s
outer limits remain enforced. Each inspector unit and the full ADVANCE are timed.

The native canary passes849 maintained plus46 adapter/helper assertions, including
29 inspector assertions. Literal copper/halo positives, same-net negatives,
shared clearance marker and courtyard/escape negatives all preserve source state.
Constructor, selector/lifecycle and released-reference guards are exercised;
the actual exception case separately proves cleanup rather than inferring it
from those literal guards.

Five actual host negatives reject changed pin SHA, input context, self pin,
source row and prerequisite-only misuse. No compiler/native starts in those cases.
Eight actual reader negatives reject source manifest, run context, lineage,
unknown occupied owner, empty sentinel used as a conflict owner, bad congestion
delta, trailing attribution EOF and false cleanup. Their attribution, index,
native trailer and host byte counts are resealed so semantic checks are reached.
Every child exits2/terminates with its exact expected reason; verified copy
workspaces are removed. These are rejection tests, not accepted routing runs.

Root reviewed the integrated native hook, inspector, host, reader and evidence.
Bounded agent/source review found/fixed the PS5 pin and earlier ownership
issues; it did not claim runtime acceptance and was not reused as a post-fix PASS.
Final acceptance rests on actual source-bound gates and root review.

GWT/Player/visible input/electrical/normal3 are NOT APPLICABLE to this
native-only change and are not renewed. Full30 is EXCLUDED by the owner. All850 production files match the accepted
parent. No browser is opened. HARD/PSYCHOTIC remain unread/unrun here and unavailable;
historical unread boundary UNCERTIFIED. Self-hosted runner registration remains a
prerequisite; no remote CI PASS is claimed. Old unrelated/uncertain resources and
earlier failures remain preserved.
