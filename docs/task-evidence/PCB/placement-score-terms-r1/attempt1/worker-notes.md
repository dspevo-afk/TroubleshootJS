# Placement score terms leaf notes

Status: implementation prepared; NOT RUN. No compile, native contract, browser, or
build result is claimed by this worker. Root owns registration, final input checks,
execution, independent review, restoration, and cleanup.

Only the designated isolated accepted-source planner and the new
`tests/contracts/PcbPlacementScoreTermsContractTest.java` were changed. The main
production checkout, registry, helpers, and other task packet files were not
edited. Current SHA-256 values:

- `PcbPlacementPlanner.java`: `678232d88d7953cc519835ece25326c741a4fcabc3f3ede684550a8f34d45848`
- `PcbPlacementScoreTermsContractTest.java`:
  `19962f6719299695fa814b8a0c6ebadff4a048588766575d9fbd4d143998ea45`

The fixture is limited to `Rb56Plan.resolve(42L, 15)`, its materialized board,
and one direct planner call with layout seed `5526715536693200536L`, density 14,
attempt 1. It binds source plan SHA
`4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59`, source
graph SHA `840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a`,
and the prior selected plan SHA
`d88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b`. The
selection binding points to the earlier `pocket-decision-cause-r1/focused-native.txt`
record with SHA-256
`ec3c40456877334d5bcdb4db850bcb456d617fe1445dc4037cf61468d7fe6cbe`.

That prior record pins QA at `(1690,1580)`, `DEG_0`, `TOP`, score
`1808.5128484848483`; it pins the same-pose alternative at original origin
sequence 6297, `(1390,1550)`, score `5948.889212121212`. Its raw, returned, and
selected plan fingerprints matched. The earlier raw-pocket conclusion remains
INCONCLUSIVE. These are fixture expectations, not a result from this unrun test.

The temporary observer is enabled only when the fixture installs it for the
matching layout seed, attempt, and density. It records ordinary origin trials,
including fit failures, under a 60000-record cap; it adds no origin, fit, score,
evaluation charge, route, or generator/selection run. It copies QA context,
normal/fallback loop identity, access envelopes, and the existing base score
additions in their existing order. A callback fingerprints the ordinary raw plan
after validation and before the existing compaction branch. The fixture compares
that hash with the returned and pinned selected hashes, and rechecks source-plan
and graph fingerprints after observation. Support endpoint span is reported as
the already weighted `endpointSpan * 3.0` cost.

Unrun fixture assertions require the pinned identities and scores, one captured
raw plan, 6800 contiguous origin records below the cap, attempt work within the
existing 60000 evaluation bound, and unchanged source plan and graph. Root should
review the emitted JSON and run the registered native contract before treating
any of these expectations as current PASS evidence.
