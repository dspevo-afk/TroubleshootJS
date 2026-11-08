# U R2 independent receipt pins and actual call closure

Read-only contract review found that first-snapshot/pre-post checks alone cannot
bind already-replaced native receipts. Add required context key nativeReceiptProof,
a canonical absolute path to the unchanged published T FINAL_GATES_R1.json, fixed
SHAaa88f01a93f7a1408268bbec37b9e271b87a7719df304b8e54a991e3d10c208a.
Its gates.ACCEPTANCE_R1.native.receipts provides independent seven-file native
pins. Bind external context runId/sourceManifestSha256/wireContractSha256 to
that same proof's gates.ACCEPTANCE_R1.context values (sourceSha256/wireSha256).
Compile seven-file pins come from the independently fixed producerManifest.compile.receipts.
Read/pin all seven authority files BEFORE owner-call validation and compare again
through the final admission phase. No arbitrary self-declared first snapshot.

Source inspection/actual directory inventory also finds two rootcaller logs.
Both T call directories contain exactly9 regular files: request.json,owner.json,
process.json,parent.json,result.json,stdout.txt,stderr.txt,wrapper.stdout,
wrapper.stderr. The inherited host kernel validates the seven authority files;
outer admission must check EXACT9 names, no extras/subdirectories/reparse entries.
Wrapper stderr must be empty. Parse bounded wrapper stdout through host_document
and require equality to the pinned result.json object. Snapshot and recheck all9.
These additional logs are existing caller evidence, not new process fields or a
new marker protocol. The earlier exact-seven wording referred to the authority
closure and is superseded for directory closure by this concrete actual9 shape.

CONTEXT_R1.json remains UNRUN for the new adapter and is preserved unchanged.
CONTEXT_R2.json adds only nativeReceiptProof; root freezes its external SHA before
any adapter runtime gate. PLAN_R1/WIRE_R1 remain preserved; this explicit current
contract refinement owns the additional binding/closure tests. Early actual
host-kernel PASS remains valid only for its stated seven-file kernel scope;
it did not claim the new full adapter or directory-closure acceptance.
No new native/authored invocation, source oracle or budget change. No data outside
owned generic proofs/class metadata/source is consumed. Full result/marker/R7
remains unqualified; protected/HARD restrictions unchanged.
