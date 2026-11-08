# Frozen normal-prior byte reader

Accepted for byte-reader component scope only; publication is pending final
index review. The module authenticates the exact 20 frozen role entries and
returns bytes with path-free evidence. It does not decode role bodies, follow
manifest paths or establish prior semantic qualification. No actual normal-prior
body was read for this substep.

The reader validates canonical local Windows paths and every ancestor, rejects
reparse points, checks containment and opened-handle identity, bounds each read,
and verifies exact known lengths and SHA-256 before returning bytes. It reads
and compares every role again, then rechecks root and allowlist identity. The
allowlist bytes and typed metadata are pinned. Only the two pinned stderr roles
permit empty content. No normal-data CLI is provided and import performs no I/O.

The later caller must independently freeze the correct private N/D roots and
record the operational phase transition. The frozen allowlist remains marked
unread; this component is not authorization to open those roles. It does not
claim an atomic multi-file snapshot. The remaining normalizer, independent pure
parsers, host and native assembly are still unstarted and gated by the
[parent plan](../full-d-v2-contract-r1/PLAN_R1.md).

Final selected Python-I-B tests: 7 methods, 6 PASS and 1 SKIP because directory
symlink creation requires unavailable Windows privilege. A separate actual
PowerShell directory junction exercises reparse rejection and returns the exact
REPARSE code; it was identity-checked and removed nonrecursively. Two selected-OS
positives authenticate synthetic bytes and the actual pinned allowlist metadata.
Thirteen controls produce expected exit 2, empty stdout and exact rejection codes:
SHA, length, maximum size, boolean bounds, forbidden zero, unknown role, extra
root, noncanonical root, path escape, junction, directory-as-file and contract
mutation. These are EXPECTED_REJECTION, never completed normal-input proof.

Final private units take 458.053 ms operation / 709.8276 ms wrapper; synthetic
positive 350.8091 / 602.3408 ms and actual metadata positive 346.0317 / 590.2642 ms.
Public byte-identical source reproduces the same test outcomes and exact positive
summaries (units 445.098 / 714.2029 ms). Every returned process is proven terminated
and its owned root/logs absent. Isolated cleanup duration is unmeasured.

Source review corrected metadata equality/fixture serialization, test import under
-I, zero-case setup and cleanup safety before execution. The first unit run left
one skipped fixture; its exact child-time interval, identities, expected tree and
synthetic content were verified before nonrecursive removal. Test-only cleanup
was fixed and final private/public gates rerun. No unexpected runtime failure,
weakened assertion, budget/oracle change or hidden skip is claimed. Raw prior
receipts and drafts remain private; sanitized evidence preserves their hashes.

Reader SHA256: `3114b1abe807e73de4cdd78939b5ab76c2623cf3de501b193a746219b16160a4`.
Final test SHA256: `e07ef529e0bf701c36d62c764d1a4e00d6437a1e49ee5d74fc94ed02d657cf20`.
Final gate SHA256: `23d1c35b3f1b611ba49c8664233b84332524a0bb0623841fdcd0b66e7d8f7306`.

The published code folder contains reader, unittest source and synthetic-only OS
canary. On the selected Windows Python, run `python -I -B test_fixed_role_reader.py -v`.
The canary takes reader-source path, a task-owned fixture root named synthetic-io-r1,
and a case. For positive I/O use N/fixture.bin containing the ASCII bytes
SYNTHETIC_FIXED_ROLE_IO_R1 followed by LF and an empty D directory. Metadata-only
case contract-positive adds the explicit pinned allowlist path as its last argument.
The selected RootCall/PowerShell/tool identities and raw receipt pins are retained
in [OS controls](OS_CONTROLS_R1.json) and [final gates](FINAL_GATES_R1.json).
Do not substitute actual oracle roots for synthetic tests.

No native/Java/GWT/producer/Session run occurred. Production/Player/electrical/
population gates are NOT APPLICABLE and not renewed. Full-D/H1 and playable100
remain unqualified; playable100=0 and beta HELD. Full30 is excluded; HARD/PSYCHOTIC
remain unavailable and unrun; historical protected unread remains UNCERTIFIED.
All caps, exact seeds, model interpretation and independent oracles are unchanged.
Unrelated work and uncertain old resources are preserved. Private synthetic
fixtures contain no links; no new persistent process remains.

Publication index check initially rejected CRLF in two pinned evidence files.
File-specific cr-at-eol attributes preserve their bytes; actual trailing-space
canaries still reject. See [preserved correction](INDEX_WHITESPACE_FAILURE_R1.json).
No reader/test/contract/tool input changed, so passing product gates remain bound.

Next is current context normalizer and pure independent parser extraction, with
explicit generated-input signatures and entry accounting before execution.
