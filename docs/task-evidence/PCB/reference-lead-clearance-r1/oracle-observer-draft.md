# Reference lead oracle observer draft

This packet contains a draft insertion patch for the
PcbCompactnessContractTest legacyGeometrySha256 helper. It is not applied to
Java source. The observer is intended for a temporary, root-controlled export
from the accepted layout-17 baseline while the existing layout-16 SHA literals
and checks remain unchanged.

The draft is based on HEAD f724f9d7f56b7e5c8ff0022581dc4772cb6aa85b on branch
codex/q60-faulted-profile-counts. Existing worktree edits and evidence remain
untouched.

The proposed PCB_REFERENCE_ORACLE JSON line carries the actual layout-17
canonical fingerprint and its SHA-256, the computed legacy layout-16 SHA-256
that the helper returns to its existing caller, and a normalized layout-17
fingerprint plus SHA-256. It also emits the complete label inventory sorted by
ID: id, text, [x,y,width,height] bounds, fontSize, bold, and targetPadId. Null
target pad IDs are emitted as JSON null; strings use the contract's existing
JSON quote helper.

The fixed top-level JSON key order is schema, layoutVersion, layoutUnchanged,
graphSnapshot, layoutFingerprint, layoutSha256, legacyLayout16Sha256,
normalizedLayoutFingerprint, normalizedLayoutSha256, normalization,
referenceLabelCount, referenceLabelRecords, labelCount, labels. Each reference
record contains id, record, normalizedRecord; each label contains id, text,
bounds, fontSize, bold, targetPadId.

Normalization is narrowly defined. It changes only the leading layout=17|
field to layout=<LAYOUT_EPOCH>|, then replaces x/y only in exact
L:component:*@text:x,y,width,height; records with <REF_X>,<REF_Y>. Before
replacing a record, the observer requires exactly one matching framed record
in the actual canonical string; duplicate or missing records abort the export.
It verifies that every placed component has its corresponding component:<id>
label. Label IDs and inventories are checked for duplicates. All label records,
including non-reference labels, must match their exact canonical framing.

The observer compares the canonical layout fingerprint before and after its
read-only inventory and requires byte equality. This helper receives only a
PcbBoardLayout, so a raw electrical graph snapshot is unavailable and the JSON
states that limitation explicitly. It does not run a new physical simulation,
move geometry, change routing, or replace any existing assertion or return
value. The legacy hash emitted is the actual computed value; the frozen caller
literals remain the independent acceptance check.

Static inspection covered the current helper, the existing JSON quote helper,
the canonical label-record framing in PcbBoardLayout.geometryFingerprint,
and the label accessors. No tests, builds, native phases, or observer executions
were run. Consequently, this draft contains no runtime PASS claim and no
captured oracle receipt.
