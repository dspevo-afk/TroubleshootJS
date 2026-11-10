# Bounded current Source review

Root reviewed the exact transform, fragments, pinned baseline, fixture migration
and current producer relationship. A separate read-only reviewer first assessed
the domain decision, then reviewed the final six-file contract/fragment/transform/
test/baseline scope. It found no substantive issue and ran no tests/programs.
The Source API and actual runtime gates are separate evidence.

The projection reads six Strings and emits the conjunction of five exact equals
results (CurrentResultProjections.cleanup lines95-117,131-168,181). The original
DTO fragment transports source geometry fields without a format guard. The
approved InputSetup sourceIdentity64-lowerhex policy is partial and does not
establish the format of all six current output fields. Root and a read-only
explorer therefore rejected the draft's proposed SHA-format requirement. The
original draft is preserved privately; it was not implemented or tested.

The adopted String reader uses the existing BODY_STRING_LIMIT256 from the
unchanged writer. It performs a prepaid get, String type check and length check;
comparisons are bounded by256 characters. Empty/short/256-character printable
values may pass when summaries agree. The unchanged writer owns ASCII validation
later, so an ASCII-invalid matching string may fail after output begins. No SHA
shape or complete-body zero-output preflight is claimed. Specific new map/type/
length/summary failures precede output and leave object identities unchanged.

The final check order is template Map, six bounded Strings, Boolean summary,
five equalities, four eager conjunctions and summary equality. Existing ownership
and all earlier errors retain precedence. All new leaves are checked before
comparison, and matching false is valid. Extra33hooks/pass=2+18+3+5+4+1,
99 across three passes; total139hooks/42gets. Hooks are not native units. No
Meter/copy/group/output/mutation call is added. Inverse recovery conserves every
other Source byte, including the complete lower transport suffix.

The worker's five migrated Sources add existing producer fields to distinct
ordinary maps and directly authored minimal/bodyJson/wide literals/Python
expectations. Root read the maps and actual diffs. Canary edits are limited to
42 gets and139 hook expectations/labels;60/210,69/253,209/566 coverage remains.
The independent lower1177/6370 byte oracles and padding bounds are unchanged.
No serialized output was blessed as a golden.

Only synthetic Map/List/scalars, codec and actual Meter may execute. No receipt
files, positive prepare/binding, factory, actual/fake arm owner, graph, Session,
positive original DTO, executeArm/R9DTO, routing, exportV2 or genuine envelope.
Metadata consistency does not prove physical geometry, full DTO semantics,
authority or exclusive borrowed lifetime. Protected/held-out DATA and old actual
artifacts remain excluded; the preexisting unread boundary is UNCERTIFIED.
