# P7 host/consumer source bindings R3

Frozen before host/worker/consumer implementation or any execution. Pure core and
test source work may be in progress under the already-frozen R1/R2 API; this
clarifies source roles and consumer transport without changing that pure API.

The two additional exact input keys required by R2 are addendum and
addendumSha256. Its exact files census also pins this R3 document and sibling
read_cut_sensitivity.py and run-cut-sensitivity.ps1, resolved next to the declared
worker. Consumer/host self pins must match those specific names/directory; no
alternate legacy resolver. Also pin the unchanged VerifierIsolation module and
both source inventories. These bindings add no graph semantics or budgets.

Consumer CLI: RUN_ROOT_ABSOLUTE SPEC_ABSOLUTE SPEC_SHA MODE, MODE CANARY/ANALYZE.
Success schema tsj-canonical-cut-sensitivity-reader-v1, status PASS_CANARY_ONLY or
PASS_CONDITIONAL_SENSITIVITY_ONLY, with route/layout/electrical/q100 flags false.
The consumer independently reconstructs variant masks and planar closure (DFS/
literal expectations distinct from producer BFS), reuses the pinned minimum-VIA
oracle and checks actual source/process/argument/index/EOF/hash/work/cleanup proof.
It must not use the producer's whole result as its own expected answer.

The sourceManifest JSON schema is tsj-canonical-cut-source-inventory-v1, exact
keys schema,parent,repositoryRoot,parentSourceRoot,files. parent is23f94f8f;
files is the unchanged1084-row P6 source inventory with role/path/bytes/sha256.
Verify every row in both the current D repository and immutable C parent source
root against those same bytes. The host performs this full transitive source
gate before/after the worker; worker directly consumed files are checked within
its work clock. No fabricated P6 context substitutes D for its genuine C source.

Host receipts live in a newly owned run root. The worker output argument is its
new empty data child directory; sensitivity-result.json is relative to that child.
The host creates that empty child immediately before worker launch, so no parent
receipt or host artifact pollutes the worker's empty output boundary.

The host receipt schema is tsj-canonical-cut-sensitivity-host-v1, exact keys:
schema,status,mode,specSha256,host,sourceBefore,sourceAfter,parent,worker,work,
cleanup,outerWallMs. status is HOST_COMPLETE_READER_PENDING. host has pid,
startTicks,executable,script,scriptSha256. sourceBefore/sourceAfter each have
status,sourceRows,sourceFiles,extraFiles,elapsedMs; status PASS, sourceRows1084,
sourceFiles2168. parent is null in CANARY, otherwise receipt (relative
parent-process.json),sha256,wallMs. worker has receipt (worker-process.json),sha256,
wallMs. work has indexElapsedNanos,residualUpperNanos,finalGroups,
maximumGroupNanos,fullWorkerWallNanos. cleanup has status PASS,accounting
FULL_PARENT_WORKER_POST_SOURCE,conservativeUpperMs,boundMs120000,
allProcessesTerminated true,temporaryOutputRetainedAsEvidence true.
parent/worker process objects are the unchanged Invoke-VerifierBoundedProcess
receipt shape, with exact selected executable/arguments, exit0, empty stderr,
TerminationProven true and recorded PID/startTicks. Their stdout/stderr are also
written as exact UTF8 bytes to the corresponding .stdout/.stderr files, checked
against the process objects. No whole-parent proof is claimed in CANARY.

Result work.prerequisiteWallMs is null: the worker cannot measure a prerequisite
that the host already completed. Its measured value is host.parent.wallMs only.
CANARY has no prerequisite. No unknown duration is represented as a measured zero.
