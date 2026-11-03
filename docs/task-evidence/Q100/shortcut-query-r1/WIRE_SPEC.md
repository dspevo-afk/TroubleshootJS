# Revision15 wire contract

The current10 scalar/path records remain in their existing order with all
sourceRevision values15. Their component scope/false qualification flags remain.
Append the following two records; closed receipt order is therefore12 records.

RB100_SHORTCUT_QUERY_EVENT exact top keys: protocolVersion(1),sourceRevision(15),
runId,operationId(QUALITY_PROBE),scope(ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY),
binding,diagnostic,feasibility(UNKNOWN),legalShortcutProof(false),
layoutQualification(false),routeAdoption(false).

binding exact keys: branchIdentityVerified(true),sourcePublisherUnchanged(true),
pass(1),netId,startPadId,rootPadId,reachedPthPadIds,foundTerminalPadId(null/string),
endCell,endLayer,goalMaskLength. IDs match actual retained BranchSearch/Publisher;
reached PTH IDs retain canonical source order. End is actual found Node, never
inferred from root. goalMaskLength=2*cells, actual end/layer goal is true.

diagnostic is helper bodyJson() with exact keys:

- stability: prefixGeometrySha256,liveBeforeVerified(true),liveAfterVerified(true),
  liveEffectsUnchanged(true),goalVerified(true),originalPathVerified(true).
- family: order(I_J_ORIENTATION_X_FIRST_0_Y_FIRST_1),minimumSaving,maximumLength,
  maximumBends(16),candidateCount,candidateTupleSha256,replacementEdgeVisits,
  distinctDirectedEdges. Tuple hash is SHA256 of compact ASCII JSON arrays
  [i,j,orientation,saving,bends] in canonical order, matching independent oracle.
- work: advanceCalls,workSteps,copyEntries,comparisonEntries,queryCalls,originalPathQueries,
  reusedEdgeVisits,activeMillis. Natural counters; workSteps conservatively debits bounded
  copy/comparison allowances and enumeration/query operations, including unused allowance when a task finishes early. Entry counters remain actual task counters. activeMillis accumulates nanoseconds through payload assembly and excludes the final constant-time Result wrapper and JSON serialization. The helper requires exclusive synchronous source ownership from begin through completion; equality flags do not certify arbitrary concurrent mutation during initial copying. queryCalls
  counts all family queries PLUS additional original-path control queries;
  originalPathQueries counts DISTINCT original edges absent from family. Original
  path/family intersections must already be allowed. reusedEdgeVisits counts only
  family replacement visits minus distinct family edges. No claim
  that this is default router timing or uninstrumented routing work.
- edges: sorted fromCell then toCell; each exact keys fromCell,toCell,allowed.
  Include every distinct directed replacement edge for the entire qualified
  geometric family, even if another edge already rejects its candidate.
- candidates: canonical i,j,orientation; exact keys i,j,orientation,length,bends,
  allowed. Allowed iff all its replacement edge results are true. Prefix/suffix
  are unchanged actual found-path segments; no new endpoint authority.
- result: queryQualifiedCount,minimumQualifiedLength(null/int),
  firstQualified(null/object with i,j,orientation,length,bends). First means
  canonical first allowed candidate, not a production selection/ranking policy.

PASS: RB100 quality shortcut contracts exact keys protocolVersion(1),
sourceRevision(15),status(PASS),runId,operationId(QUALITY_PROBE),events(1),
queryQualifiedCount,scope(ACTUAL_QUERY_CLONE_SHORTCUT_FAMILY_ONLY),
feasibility(UNKNOWN),legalShortcutProof(false),layoutQualification(false),
routeAdoption(false).

Core actual target path remains66/650/210/6/0. Reader independently derives
family2011/visits33771/edges892 and tuple hash from that path; it must validate
both false and true edge results and candidate conjunctions, all identities,
source/receipt/hash binding and exact finite proof/claim boundaries. No data are
predetermined for allowed edges or query-qualified count. Runtime outcome unknown.
Source input schema p07-quality-shortcut-input-v1, native run schema
q100-quality-shortcut-native-run-v1, reader result schema
q100-quality-shortcut-reader-result-v1; suite Rb100QualityShortcutContractTest.
Native1070=850+214+6/revision15; actual diagnostic run has one suite. Focused
fixture is a separate source-identical maintained native run, never a substitute.

## Required real-input positive canary, amended before gates

Before final live-state comparisons, replay the actual found path's65 directed
ordinary edges on the SAME query clone and endpoint authorities. Reuse already
queried family intersections and require them true; query only distinct original
edges absent from family, requiring true. Any false means query-context failure,
not absence of shortcuts. Keep edges/candidates wire family-only. For the actual
captured path, independent offline geometry finds62 original edges in the892
family and3 absent: originalPathQueries3, totalqueryCalls895. Derive those from
the path rather than hardcoding. All65 original steps are covered, with no extra
budget or product operation. The generic bbox bound4354 still covers the union.