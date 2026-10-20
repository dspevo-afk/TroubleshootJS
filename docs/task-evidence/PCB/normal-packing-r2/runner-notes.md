# Normal packing r2 runner notes

Status: **IMPLEMENTED, STATIC/TRANSPORT REVIEW PASS**. This leaf owns the r2
candidate input seal/audit, unit/routes phase runner, process birth cleanup
check, known-prefix sanitizer, strict suite transport, transport canary, and
these notes. Native execution, Java/GWT build, browser flow, screenshots,
staging, commit, push, and email are **NOT RUN** by this leaf.

The r2 runner accepts only `unit` and `routes` and always resolves the main
worktree from the packet with the four-parent repository path. It reads the
protocol scope `Actual active11 normal random generation; fixed44 retained-
route packing comparison` and the 60-second JVM/case limits, refuses
pre-existing phase artifacts, and requires `candidate-inputs.json`. Both
phases require a passing `baseline-validation.json`; `routes` also requires a
passing `unit-validation.json`. The candidate seal is audited before the
driver starts and again in cleanup, with a post-audit failure preserved
separately from any earlier native failure.

`inputs.py` re-audits the immutable r1 baseline through r1's
`audit-baseline`, checks all 13 r2 `baselineReference.fileSha256` pins, checks
the optional census `sourceHelperSha256`, and seals the old 1467 compiler
inputs plus the ten r1 Java contracts and four r2 Java additions as exactly
1481 files. `freeze-candidate` archives every changed source byte under
`candidate-source`; `audit-candidate` verifies the seal, gate-owner hashes,
source inventory, and archive without rewriting it. No r2 baseline copy is
created or rerun.

The process record captures the launching PowerShell parent and the current
driver PID, UTC start ticks, and executable before the phase starts.
`check_process_cleanup.ps1` revalidates each PID birth and executable and
reports `ABSENT`, `DIFFERENT_PROCESS`, `STILL_RUNNING`, or `UNKNOWN`. It never
terminates an uncertain process; unknown and still-running births fail
cleanup.

`Get-NormalPackingR2SuiteNames` preserves the strict nested two-value manifest
contract, canonical class-name validation, nonempty single-line markers, and
unique class and marker identities. The companion checker consumed the actual
r2 four-row unit manifest and eight-row route manifest, then rejected ten
flat, empty, malformed, duplicate, and multiline negative inputs. Its 12
cases are PowerShell transport checks only and do not execute Java suites.

`sanitize.py` accepts only `unit` and `routes`, requires passing timing and
birth-cleanup records, and replaces only the worktree, immutable r1 baseline
COPY, and optional JDK prefixes. It is idempotent and preserves phase output
when no declared prefix occurs. Published text is checked for known private
prefix removal after each phase.

Static validation passed for Python AST, PowerShell parser syntax, LF EOF and
trailing whitespace, four-parent main-worktree resolution, no termination
operation, r2-only phase names, source-count and seal field order, the current
1481-file source inventory, the r1 baseline audit, r2 protocol reference
pins, and the source-helper hash. The actual PowerShell transport canary
passed 12 cases: the four-row unit manifest, eight-row route manifest, and ten
malformed or duplicate negatives. No native result is implied by these checks.

Protocol and transport pins used by this review:

```text
protocol.json eb1f833bfd60574676ea1d34ad8d432f4b43fc68928bc8e0184af6b1dc223b47
unit-suites.json f6a1ad2bba9e34edff7b21f8ca98740e2d93d6bb9564c111ce6feaba21b07d1c
routes-suites.json 4c9760b9999a35451c1255d6c83c13f94f6279dc76c5acd4994faac21a7dc1d3
sourceHelperSha256 4b89b89ca7fc5d15488cf81e0cdb1b5fad96bdffde5083d6d9f91b7885b63596
suite-transport-canaries.json ad1b1e5bd60293cef9129762f07b993d641feb1a331d50594f170db74760fba1
```

Owned file fingerprints at review time:

```text
inputs.py 11390 bytes 4eda917d65db95fe620690ff8d4868d18396c6e91fdd8234defbe3f3d63aa660
run_native.ps1 4748 bytes d544fe5b8068996fbfda49b0e333ab940a5c223d4d0f4d0de9a9d03f725d27b0
check_process_cleanup.ps1 2154 bytes fa909baecba250fd3f87d34c8310d674214597d69b31c84251ba758f861ecdd4
sanitize.py 2163 bytes 7ce0883afc7479227472c26834810df960eed30fa06d69a5d25125871eee9fa9
suite_transport.ps1 1120 bytes f20fbcec194eebe5696cc98e5c46b94bb31d725e21c5ef110a28cf2e3ea31ce1
check_suite_transport.ps1 3187 bytes 9ca0a4d864ead41eca890f87011b6fe89ecba80ea76fcd20f417044c3254bd27
suite-transport-canaries.json 1531 bytes ad1b1e5bd60293cef9129762f07b993d641feb1a331d50594f170db74760fba1
```

The remaining native and assessment evidence is root-owned. Root must run the
fresh r2 unit/routes phases after freezing the candidate seal and prerequisite
validations, then retain any failure artifacts and run sanitization/cleanup
before publication.
