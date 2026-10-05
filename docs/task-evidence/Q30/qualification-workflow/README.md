# Q30 qualification workflow

## Current source and eligibility

The current C621 source snapshot binds base `c621b55d2b071e064d5afd383451d2715e35940d`,
source identity `87bb04f707ccaf57f92c5d38fdbf4db199d63359c573725fd34bac61484226d7`,
snapshot SHA-256 `6fd6544fc12df1f1817452f56ee6fd43ceb177d0d08ea26c75f1b0c476b20fc3`,
and 1,355 raw application inputs; that inventory is unchanged. Normal Q30
registration remains false. Q30 is **NOT ACCEPTED and disabled**.

The maintained [`cold77-candidate-manifest.json`](cold77-candidate-manifest.json)
is schema 2, SHA-256
`4079468809f5569abe0c3632ff389751d7d6835d4deebeb1ae6234cc78bff25a`. It binds
the current input identity and the external matched helper to the exact bytes of
`cold77_batch.py`, `cold77_case_worker.py`, `serial_runner.py`, `receipts.py`, and
`windows_process_identity.py`. `receipts.load_workflow_manifest(root,
manifest_path)` validates that binding. Per-case receipts remain schema 1 with
the existing electrical reader, frozen seed order, and 90,000 ms / 640 / 5,000 ms
limits. No threshold or cohort size changed.

The actual private JDK8/GWT5 build PASS87.495s and1,355-member source archive
PASS_SOURCE_ARCHIVE_ONLY. Headed native Edge UI10 PASS for real20/30/40 guided
repair/customer retest, with full1,526 served-input hashes, durable outerCLI0,
cleanup and five inspected screenshots. Final selected-host workflow PASS88/88
under23 exact source hashes. Final reuse audit confirms unchanged public/private
compiler inputs, nine jars and357+6 compiled/deployment files.

Q30 acceptance is blocked. The private menu gate passed31 launches then failed
on the second random RB30 launch (ERROR91.616s, selected90,000ms budget).
Failed seed UNKNOWN; its retained replay is the predecessor accepted request.
Remaining launch and all required replay/stale/full-privacy checks NOT RUN.
Subsequent diagnostic logging is source-tested only. Release proves809 old
instances absent/77 ports closed but remains BLOCKED after the approved
16:32:46.188302Z quiet end. No cold rerun or changed application limit.
See [final checkpoint packet](simplification-20261005/README.md) for exact
epochs, limits, remaining gates and preservation uncertainty: six removed
scratch directories have no prior content inventory, though60 named raw pins
and all8 original untracked files are byte-verified.

## Maintained entrypoints and ownership

`qualify.py` is the phase entrypoint. Each invocation runs one named phase from
`audit-retained`, `release`, `private-prepare`, `private-build`, `private-menu`,
`private-archive`, or `visible`. Its schema-1 local run manifest binds the C621
`sourceSnapshot`, `sourceBinding`, tools, retained evidence, and an exact
`workflowSources` SHA-256 map. The map is frozen from the actual helper bytes;
the selected phase requires its own owners plus shared runner/receipt/process
owners. The dispatcher checks these pins before and after execution and accepts
a worker result only when its phase, manifest hash, and source binding match.
This helper map is separate from the 1,355-file source snapshot and from the
cold candidate manifest; helper files do not carry duplicate hard-coded hashes.

The dispatcher obtains `tools.python` from that local manifest. It must be the
selected physical Python 3.13 executable, not the Windows Store alias or an
ambient `sys.executable`. `serial_runner.run_command` owns host phases in native
Windows jobs; `run_serial` owns the ordered cold77 cohort. The shared process
identity owner records native process birth identity and handle-based cleanup.
`private_qualification.py` prepares, builds, checks the menu, and archives only
an OS-temp private copy; it does not enable Q30 in the checkout. `audit_release.py`
owns release checks, while `visible_repair.py` owns the headed repair flow.

Run a single phase from the repository root, using the exact physical executable
and frozen manifest paths already recorded in the task:

```powershell
& '<manifest tools.python: physical Python 3.13>' -B docs/task-evidence/Q30/qualification-workflow/qualify.py `
  --manifest '<frozen local run manifest>' --phase private-build `
  --output '<new direct child of the manifest OS-temp root>'
```

The output directory must be new and a direct child of the manifest’s task
OS-temp root. A result applies only to its named phase; every other phase remains
`NOT_RUN`. The separate `cold77_batch.py` entrypoint audits by default. A cold
launch still requires a fresh exact parent grant and the unchanged quiet-window
reserve. Historical task-local copies of cold runners and release wrappers are
retained as evidence, not current commands.

## Receipt contract and historical evidence

`receipts.py` requires separate application, host observation, operation and
cleanup fields. Application time comes from the solver-backed report; a host
deadline never manufactures an application timeout or substitutes its clock.
Missing measurements carry null with an explicit limitation where applicable.
Exact seed order, source/HEAD/plan binding and original caps are required;
omitted roots appear as NOT_RUN and make the sequence incomplete. Writes create
new files exclusively. A summary owns a deep copy of its receipts.

### Historical D37 cold77 cohort — not the current C621 result

The following cold evidence belongs to base `d37c3b3b8581abdeaa9b88b7bfb2349cff958d31`,
source identity `a43f8165921f08c309636d2bf1a971905fdbc9382885c0708788d79744e1fb51`,
and 1,354 inputs. It remains preserved as historical evidence and must not be
re-evaluated under the C621 binding. The old raw R5 diagnostic remains BLOCKED.
At that D37 checkpoint, the user authorized future runner and harness changes
outside the frozen 1,354-input application inventory. That scope did not certify
the current C621 helpers or replace fresh phase evidence.

`normalize_cold_evidence.py` reads retained original fixtures, checks canonical
source identity, all source bytes, safe contained paths and maintained reader
bindings. [Normalized ledger](normalized-cold77.json) remains **FAIL/incomplete:
31 PASS/1 FAIL/45 NOT_RUN**. Root75 retains application TIMEOUT90,221ms while
host and cleanup pass. Independent seconds are rounded upward to integer
milliseconds; original floating values and raw hashes remain in provenance.
The maintained full cold77 auditor separately ran FAIL/incomplete.

`serial_runner.py` exposes `run_serial(spec, new_os_temp_root)`. A spec binds
sourceIdentity/baseHead/planSha256 and the unchanged limits; each ordered step
contains a signed-long rootSeed string, absolute executable argv with one
standalone `{receipt}` placeholder, and outerTimeoutMs. The caller supplies a
finite approved batch and a nonexistent absolute OS-temp output root. Commands
run serially, hidden, suspended until assigned to an owned kill-on-close Windows
job. The runner records PID, creation time, executable and command hash; it
captures at most1MiB per log and stops after the first failed or missing proof.
Later roots are explicit NOT_RUN. A step is bounded to180s host observation;
one15s cleanup deadline includes process/job shutdown,500ms accounting drain
and all output-reader waits. Readers own log flush/close; a reader still alive
or failed at the deadline makes cleanup FAIL. Persistent descendants still fail.
No global process termination or runtime setting changes occur.

Workers must produce receipts from actual maintained application/host auditors
and source checks. This runner validates and preserves those records; it does
not independently implement the electrical oracle or grant execution authority.
Missing cleanup proof remains failure even when emergency owned-job termination
succeeds. Valid application measurements survive an outer host failure.

### Historical D37 process and harness evidence

The following results apply to the D37 process-helper version and its selected
bundled CPython 3.12.14 Windows implementation; they are not current C621 phase
results:

- Historical baseline PASS36/36:22 receipt tests, five normalizer rejection fixtures and nine process
  tests. Test body1.992s, operation2.000s, no skips/errors/failures. Real canaries
  cover serial non-overlap,90,221ms mislabeled PASS, host timeout with descendant,
  persistent descendant after root exit, missing receipt, log overflow, existing
  output preservation, relative paths and changed-cap rejection.
- Early canary FAIL0.047s preserved. Root exit preceded Windows job accounting;
  its cleanup already proved signaled/job count zero. The corrected two-child
  canary PASS0.157s, both32ms accounting drains recorded separately as cleanup.
- Independent read-only process review PASS11 exact recorded instances absent
  or signaled; owned descendant jobs report zero active processes. No kill by
  PID was performed. This does not declare the shared desktop available.
- Independent static review PASS limited to receipt/normalizer source and path
  guards. Agent implementation was reviewed by root and actually executed here.

The historical shared-deadline correction passed39/39 (22 receipts/five normalizer/
12 actual Windows process tests), operation5.421s; outer5.562s/cleanup0.031s.
Real duplicated-writer pipe and stalled-sink negatives preserve application
measurements, return within the shortened1.5s test deadline and report remaining
root NOT_RUN. Fixtures then release their exact owned handles and prove readers
exit. No production budget was changed. The original36-test packet below is
from an earlier source version; the runner hash at that historical checkpoint is
`19931855b98aeb358f16dcbd94b6c69d81a221191bed2f07697a3ccdcffff5e6`.
[New source/test/resource evidence](../epoch15-root75-phase-and-harness-review/README.md)
preserves that distinction and the earlier failures. That historical batch
included the three additional tests and bound 1,354 D37 application/host inputs.

All fixtures and the early failure remain retained. The portable91-member
[canary packet](canary-evidence.tar.gz) has SHA256
`7ff9c08ea98d61e35ba14a5f78f0280c9ba214518d3d370da14a5e188d549a53`.
[Manifest](packet-manifest.json) pins raw/portable bytes and exact tested source;
[integration review](integration-review.json) and [resource review](resource-review.json)
record1,354 unchanged application inputs and process results. Personal local
path prefixes are replaced by labeled placeholders only in the portable copy.
Retained raw task roots remain authoritative.

### Historical D37 synthetic-harness invocation and handoff

The command below belongs to the retained D37 synthetic harness packet. It is
not the current `qualify.py` phase entrypoint:

```text
python -B docs/task-evidence/Q30/qualification-workflow/run_workflow_tests.py
```

The batch retains a fresh OS-temp launch/result record and every canary fixture.
It reports the planned120s batch budget; per-child execution/cleanup bounds are
enforced by the runner. A supported Windows Python is required. This is synthetic
harness validation, never electrical qualification or visible player-input QA.
At that packet checkpoint, no unchanged application rebuild was repeated after
the docs/tool addition; all1,354 previously consumed inputs were rehashed and
matched that packet's final gate evidence.

At the D37 packet handoff, recorded Q30 blockers were the fresh full cold77
failure, private enabled gates and supported visible20/30/40 diagnosis/repair/retest.
Root75's four matched diagnostics did not explain its failed cold run or establish
cohort acceptance. This is historical handoff status, not the current C621 gate
summary.
