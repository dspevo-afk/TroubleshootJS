# Task 43P Evidence

This directory is the durable evidence namespace for the developer-only
Task 43P cross-boundary reconciliation route. It is not visible-player
`@Browser` evidence and it does not authorize Owner Review or Task 44.

## Route contract

- Positive corpus: `scripts/verify-browser.ps1 -Task43P`, covering LED, diode,
  RC, NPN, NMOS, and parallel families for the selected seeds.
- Forced negative: `scripts/verify-browser.ps1 -Task43PForcedNegative`.
  A reached forced-negative route must return application/canary exit `1`
  only after the anchored Java diagnostic is observed. Browser, CDP, timeout,
  ownership, cleanup, and unproven results are exit `2`.
- Each reached positive route publishes `data-tsj-task43p-evidence`; the
  wrapper captures the parsed payload plus its own baseline, HEAD, dirty state,
  source/verifier SHA-256, file count, and run-owned evidence path. Java/GWT
  evidence is rejected if it attempts to emit any of those repository claims.
- `Task43PPhysicalTruthDeveloperVerifier` gathers independent raw logical-board,
  raw layout/copper, renderer, package, and solver-post observations, then
  sends both the positive path and source-snapshot negatives through one
  canonical validator. The negative fixtures include omitted-terminal,
  renderer pad offset, renderer lead-only offset, raw-copper gap/net, solver
  endpoint/retained-board-binding identity, post/node/connection geometry,
  mirror-transform, and internally self-consistent
  wrong-mapping cases. They do not mutate production source, renderer
  geometry, board metadata, or CircuitJS; they are same-canonical-path source
  snapshots, not runtime mutation acceptance.
- `scripts/verify-task43p-source-experiments.ps1` applies nine separately
  recorded producer-path mutations in isolated disposable source copies:
  renderer pad `J1.1 +20px`, renderer lead `J1.1 +20px`, raw-copper endpoint
  gap, raw logical-net mismatch, fixed and detachable solver identity
  redirects, package/mirror transform mismatch, internally self-consistent
  R1 pad-net remapping, and omitted manifest terminal. It records exact
  before/after/restore SHA-256 values and repository-state identity, compiles
  all five GWT permutations for every case, and attempts the compiled
  extraction route. Browser/preview-blocked experiments remain exit
  `2`/`UNPROVEN`; compile success is not runtime mutation proof.

## Current candidate status

Repair baseline/HEAD before the Coder edits: `20f83535163070a0688fcc0958715e6bc827d445`.
The repair candidate is uncommitted. The positive/forced-negative Browser routes did
not reach the application on the current host: browser startup and cleanup
ownership proof stopped at WMI `Access denied`. Consequently no runtime JSON
is claimed here, and A-I/triad results remain `UNPROVEN` or explicitly
partial in the candidate's structured payload design. The Foreman must repeat
the routes with visible built-in `@Browser` validation and a host that can
prove exact browser/process ownership.

The latest wrapper-owned source/verifier state audit retained the published
baseline HEAD, SHA-256 digest
`e1a30e2b16648e5c56caaf6318a96bf0a65e19fc1158aff365c948f336498baa`, and file
count `856`; the candidate worktree remained dirty with 12 status lines and
the same expected paths.

The static route/validation record is
[`candidate-manifest.json`](candidate-manifest.json); its runtime fields are
explicitly `UNPROVEN` where the page was not reached.

The latest combined isolated source record is
`task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json` under the
run-owned temporary evidence directory. It contains nine separate producer
experiments. Each compiled with exit `0`, attempted the bounded runtime route
against its still-mutated source image, returned exit `2` because this host
cannot construct `System.Net.HttpListener`, stopped its disposable preview,
restored exact source bytes, and proved unchanged repository
HEAD/status/source digest/file count.

## Required interpretation

Absence of a JSON run artifact is an infrastructure limitation, not a pass.
The Java/GWT compile proves only that the developer-only route is buildable;
it does not prove the missing runtime A-I lanes, same-owner transaction
semantics, request/board/session epochs, or visible-player behavior. The
disposable source harness proves source-path compilation, exact restoration,
and a bounded runtime attempt when available; this host's preview failure
leaves all nine mutations `UNPROVEN`/exit `2`. Without runtime extraction and
visible built-in `@Browser`, Task 43P remains unproven.

## Delta remediation loop — 2026-08-30

The additional reviewer blockers were repaired in the same uncommitted
candidate. The disposable harness now passes an explicit disposable
repository/`war`/`scripts` triplet to the repository-root wrapper and requires
the preview identity to match that triplet before extraction can run. The
wrapper keeps actual checkout HEAD/status/source SHA-256/file-count provenance
in the repository wrapper; it does not treat the disposable tree as Git
provenance.

The canonical physical validator now compares the observed raw logical net-ID
set with the manifest-derived net-ID set, including empty unmanifested nets.
The solver negative sorts source observations and prefers a different element
with the same node/class/post when available, then chooses an independently
incompatible class/post/node endpoint, with a deterministic invalid node source
observation fallback. The canonical solver check re-fetches every live
BoardSimulationBindings endpoint and compares it with the immutable
GeneratedBoardEndpointOracle captured at generated-board composition. A
detachable binding endpoint must match that oracle and its detachable geometry;
non-detachable pads use the oracle while the installed fixed-generated part is
checked for package/terminal ownership, with an endpoint cross-check only for
foundation FixedPhysicalPart terminals. Missing or mismatched ownership is a
verifier failure, and detachable geometry is checked only for detachable pads.
Java evidence uses a strict exact schema;
unknown or aliased repository-authority fields are rejected before evidence
persistence. The Java state fingerprints are named verifier-design state,
not repository digests. Post-module wrapper evidence directory creation and
text/byte writes use typed infrastructure helpers, so I/O failures remain
exit `2`. The negative corpus is now nine source-snapshot
fixtures, retaining the prior eight and adding an empty unmanifested-net
case.

Targeted producer-path evidence is retained together in
`task43p-source-experiments-1ee260b7a3c94b609830e475f3dc4491.json` (renderer,
raw copper, and solver binding entries are separate within that record).

Each record reports compile exit `0`, runtime extraction attempted against the
mutated source, runtime exit `2`/`UNPROVEN`, exact target-byte restoration,
preview stopped, and unchanged repository state. The host failed before
disposable identity completion because `System.Net.HttpListener` is
unsupported, so no runtime mutation rejection or visible `@Browser` proof is
claimed. The wrapper
execution-root contract was also exercised against an unreachable loopback
URL and returned captured child exit `2` after the typed infrastructure
failure.

After final schema hardening, the wrapper-owned candidate state remained HEAD
`20f83535163070a0688fcc0958715e6bc827d445`, source/verifier digest
`e1a30e2b16648e5c56caaf6318a96bf0a65e19fc1158aff365c948f336498baa`, file
count `856`, dirty `true`, with 12 status lines. The linked source-experiment
record remains a historical run snapshot and does not supersede this current
candidate state.

## Final remediation corpus and NPN correction — 2026-08-30

The NPN independent manifest now records the generator's complete load-side
truth: `J1.1` is `LOAD_SUPPLY / WireElm / 0`, and `LED1.K` is
`COLLECTOR / WireElm / 0`; the remaining NPN terminals remain aligned with
`NpnLowSideSwitchGenerator` and its package bindings.

The nine-case source corpus is retained in
`task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json`. Every case
compiled all five OBF permutations with exit `0`, attempted the matching
disposable-root route while its producer source was mutated, and returned
runtime exit `2`/`UNPROVEN` before preview identity because
`System.Net.HttpListener` is unsupported on this host. All nine cases have
`runtimeAgainstMutatedSource=true`, exact byte restoration, stopped preview
proof, unchanged repository HEAD/status/source digest/file count, and zero
cleanup errors. No case is presented as runtime mutation acceptance or visible
`@Browser` evidence.

The repository wrapper now encloses its HEAD/status/source enumeration and
SHA-256/count computation in a typed infrastructure boundary; standard file,
process, ownership, transport, and timeout exceptions are also fail-closed
when a route catch reports a failure. Java evidence remains constrained by the
strict nested schema and cannot persist repository-authority aliases.

## Final solver identity delta — 2026-08-30

The solver validator now performs the live endpoint lookup for every manifest
pad and independently joins it to a retained producer endpoint. The immutable
`GeneratedBoardEndpointOracle` is captured by `GeneratedBoardInstance` at the
composition boundary, before the verifier-ready live lookup seam. A detachable
pad cross-checks the exact `GeneratedComponentConnectionBinding` board endpoint
and its detachable connection geometry. A non-detachable pad uses the retained
board oracle; its installed `FIXED_GENERATED` part is checked for package,
terminal, and fixed-boundary consistency, with a terminal endpoint cross-check
only for `FixedPhysicalPart` foundation parts. Internal NPN/NMOS resistor and
LED terminals are therefore not substituted for board trace endpoints. Both
paths check exact element identity, post index, independent class/post/net/node
facts, owned-element membership, and finite voltage. A missing or mismatched
oracle, binding, fixed part, package, or terminal is a verifier failure.

The refreshed disposable source record
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json`
compiled all nine producer mutations with exit `0`, attempted runtime
extraction against the matching disposable root while each mutation was
present, and returned exit `2`/UNPROVEN before preview identity because this
host cannot construct `System.Net.HttpListener`. All nine restored exact target
bytes, stopped their previews, and proved unchanged HEAD/status/source
digest/file count. The fixed J1.1 and detachable RC C1.+ mutations redirect
only the live post-capture `BoardSimulationBindings` lookup, so the retained
board oracle is not copied from the mutated live map. No runtime solver
rejection or visible @Browser result is claimed.
