# Native CODE and selected tool byte facts

`observe_native_code_tools` observes exactly the six Source roles owned by the
current invocation adapter, plus `nativeJava`/`nativeJavac`. It takes already
authenticated reader, I/O-owner, observer and invocation-owner module objects
and externally authorized file specs. Binding guards preserve helper ownership;
hash syntax and supplied modules/specs do not establish provenance.

The adapter snapshots both spec maps/rows, validates all eight before content
access, and reuses the invocation owner's role/basename/ASCII path policy. Specs
retain the existing path/expectedBytes/maxBytes/sha256 shape. Source cap4MiB,
producer Source cap1MiB and executable cap8MiB stay unchanged. The observer's
existing validation/read/recheck helpers own the bytes and identity checks;
the reader owns canonical paths, reparse/type/handle/length/hash policy. There
is no second filesystem verifier, path resolver, serializer or manifest ledger.

This is a native-domain consumer of neutral helpers. The prior-input wrapper's
eight Source/five tool roles remain unchanged and are not relabeled. Both tool
and Source maps are read then identity-rechecked. The default result retains only
path/byte-count/SHA facts in fixed native role order. The keyword
`include_identity_tokens` defaults to False and requires an exact bool before
content access. True additionally returns `sourceTokens` and `toolTokens`, fresh
maps of the observer's original `(bytes, sha256, projected_identity)` tuples.
The projected identities contain immutable tuples, no bodies or live handles.
A trusted caller may retain these with the same authorized specs and invoke the
existing observer `_recheck_fact_map` at a later boundary. Same-byte replacement
must still reject against the original identity; a fresh observation alone loses
that comparison. No normal-ready status or admission result returns.
No resource directory, absence, harness/module link,
classpath, jar, dependency, class inventory, launch or process proof is observed.
The function neither selects/activates a JDK nor executes an executable.

The caller still owns authority for expected bytes and module/config/file
lifetime through later use. Fact observations are finite snapshots, not an
atomic external-namespace transaction. Row snapshots keep one consistent set
of expected inputs; they do not authorize a later caller configuration. The
opt-in token maps are in-process observations, not unforgeable capabilities,
serialized authority or a check-to-launch lock. Caller module/spec authentication
and exclusive lifetime remain required. No new reader or rechecker is added. The
N52 stager's separate endpoint restrictions and N53's real-return requirements
remain. Closed class inventory and trusted coordinator integration are later gates.

Selected Windows gates use exact five current core Source bytes, the explicit
NO_DATA producer staged by N52, and read-only pinned JDK8 java/javac files. Final
literal tests cover facts/order, both role domains, all limits/preflight, malformed
specs, wrong bytes/basenames, directory kind, same-content file replacement,
owned spec mutation and a real junction. Source/mutation fixtures are task-owned
CODE only. No donor tools or repository Source are changed. Root freezes all
inputs before/after actual isolated Python calls with original-handle receipts.
The identity-continuation gate adds later successful rechecks, strict flag
refusals before reads, and same-content Source/copied-tool replacements after
the adapter returns, while preserving each renamed original file identity.

No actual prior/fixed20/oldD/heldout/protected DATA or generated DATA factory,
normal qualifier/preflight/native/OriginalArm/capture/graph/Session/receipt/export/
genuine operation. Both Study holds remain; all budgets/seeds/epochs/order/oracles
stay fixed. Full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected unread
UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD. Existing owners and
Java/GWT/Player Source are unchanged; older scoped gates are retained only after
explicit input audit, not renewed as native or normal-host acceptance.
