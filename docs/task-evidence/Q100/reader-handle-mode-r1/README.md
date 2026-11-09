# Windows reader handle-kind correction

Accepted and published as `ee23ef185056a9510161efb6c999ac78c0a4251e` on
origin/codex/q100-physical-feasibility, exact remote SHA verified.
Gmail `1a1bdc57fe3d1c7b` SENT_ONCE; [publication](PUBLICATION_R1.md).
Final proof SHA256 `090f4a82e8cc87aebb645124a540783dab4b73097402458d16eb1961d40e46ca`.

The first actual caller preflight failed while authenticating the selected
Windows Python executable. The shared reader compared full st_mode between a
path and its open handle. For Python, PowerShell and JDK executables, path stat
reports0100777 while handle stat reports0100666. Their device/inode/type/size/
mtime/attributes and full before/after path snapshots match. The harness/module
files have matching modes. The original exit2/FILE_CHANGED positive failure is
preserved, with operation352.932ms/wrapper600.3566ms; it is not acceptance.
The metadata-only diagnosis is scoped to these observed files, not all hosts.

The existing reader owner now compares stat.S_IFMT(mode) across path/handle,
retaining dev/inode/file-kind/size/mtime/attributes. Exact full path snapshots
before and after reading still include all mode bits, ctime and the other
metadata. Regular-file, canonical path/ancestor, reparse, byte-size/hash and
final reread checks remain unchanged. All other reader module AST nodes match.
No alternate resolver or permissive Windows reader was added.

Current reader SHA256
`00f6d135b0f86c87f389ebb0efd45aa50b804f7c1fe263b02ffe62bb75b9fbd3`;
test SHA256 `b0f775a403307dad9dfdd1152fb70ea4a1639e9b27b3ce19e8acdfa7c517af06`.
Original3114 and its source/gates are historical. A scoped independent source
review found no blocker; it did not run or certify root runtime gates.

Fresh private/public selected suites each run9 tests:8 PASS/1 SKIP for directory
symlink privilege. The new Windows .exe-named synthetic file is read as ordinary
bytes, never launched. Independent identity-field tests retain file-kind and
every other handle-identity discriminator while keeping path snapshots distinct.
The actual PowerShell directory junction gate rejects REPARSE and is verified
and removed nonrecursively; target bytes remain unchanged. It covers the required
reparse boundary despite the unit symlink SKIP.

All five actual selected tool files now authenticate through the corrected owner
in both private/public canaries. A wrong digest on the real selected Python
executable rejects SHA256 with exit2. Both synthetic byte and actual frozen
allowlist metadata positives pass in each location. All13 existing selected-OS
negative controls retain their exact codes: hash, length, size, bool bounds,
forbidden zero, role/root, noncanonical path, escape, junction, directory-as-file
and contract mutation. These exit2 controls are EXPECTED_REJECTION, never normal
prior acceptance. Public source copies match tested bytes exactly.

Private unit464.3535ms operation/708.7072ms wrapper; actual selected tools
348.6288/595.317. Public unit466.8308/720.3776; tools458.161/693.0387;
actual tool digest rejection362.2882/603.9862. Selected returned processes have
proven termination and owned roots/logs absent. Isolated cleanup duration is
unmeasured. No persistent process or link remains. Private source/synthetic
fixtures/receipts and earlier failure/diagnostic evidence are retained. Public
diagnostic evidence omits private file IDs and paths.

Code remains tests/qualification/rb100-layout/fixed-role-reader-r1. Reproduce
units with selected Python-I-B:

```text
python -I -B test_fixed_role_reader.py -v
python -I -B tool_read_canary.py --reader-source fixed_role_reader.py --tool-config <explicit-five-tool-SOURCE-config>
```

The tool config is an explicit five-row list of role/path/sha256 for the selected
Python, PowerShell, Java, root-call harness and process module. Known tool hashes
are in the proof; canonical local paths must be supplied independently. Never
substitute prior role bodies, generated DATA, classes or manifest-discovered paths.
The unchanged reader_os_canary.py and synthetic-only cases remain reproducible
under the original reader packet's fixture instructions.

No actual20-role body, old D descriptor/result/generated-input, old builder/
module/CLI, native producer/routing/Session/original or operational DATA phase
was read/run. Actual observer/caller/source/root/resource facts and normal-prior
qualification remain incomplete. One genuine original plus NULL/OBSERVE remains
unspent/gated. Production/GWT/Player NOT APPLICABLE; electrical/population proof
not renewed. Caps/seeds/epochs/oracles unchanged; Full30EXCLUDED;
HARD/PSYCHOTIC unavailable/unrun; historical protected unreadUNCERTIFIED;
playable100=0/betaHELD/full-D-H1 unqualified. Next finish the current observed-fact
adapter and caller gates before journaling the fixed20 normal-only read.
