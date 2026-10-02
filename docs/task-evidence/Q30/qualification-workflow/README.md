# Future qualification receipt and serial execution workflow

Q30 is **NOT ACCEPTED and disabled**. These additive tools do not enable it or
replace the maintained qualification auditors. Original77 roots/308 candidates,
90,000ms cumulative application time,640 work units and5,000ms active/coordinator
limits remain unchanged. U06/U07/Q60 remain unstarted.

Dave explicitly authorized these future harness improvements after the cold
qualification failure. They live outside its frozen1,354-input inventory. Final
source identity is `a43f8165921f08c309636d2bf1a971905fdbc9382885c0708788d79744e1fb51`
at base `d37c3b3b8581abdeaa9b88b7bfb2349cff958d31`.

`receipts.py` requires separate application, host observation, operation and
cleanup fields. Application time comes from the solver-backed report; a host
deadline never manufactures an application timeout or substitutes its clock.
Missing measurements carry null with an explicit limitation where applicable.
Exact seed order, source/HEAD/plan binding and original caps are required;
omitted roots appear as NOT_RUN and make the sequence incomplete. Writes create
new files exclusively. A summary owns a deep copy of its receipts.

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
cleanup has10s plus at most5s after closing the job. A500ms job-accounting drain
after root exit is charged to cleanup; persistent descendants still fail.
No global process termination or runtime setting changes occur.

Workers must produce receipts from actual maintained application/host auditors
and source checks. This runner validates and preserves those records; it does
not independently implement the electrical oracle or grant execution authority.
Missing cleanup proof remains failure even when emergency owned-job termination
succeeds. Valid application measurements survive an outer host failure.

Validation on the actual selected bundled CPython3.12.14 Windows implementation:

- PASS36/36:22 receipt tests, five normalizer rejection fixtures and nine process
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

All fixtures and the early failure remain retained. The portable91-member
[canary packet](canary-evidence.tar.gz) has SHA256
`7ff9c08ea98d61e35ba14a5f78f0280c9ba214518d3d370da14a5e188d549a53`.
[Manifest](packet-manifest.json) pins raw/portable bytes and exact tested source;
[integration review](integration-review.json) and [resource review](resource-review.json)
record1,354 unchanged application inputs and process results. Personal local
path prefixes are replaced by labeled placeholders only in the portable copy.
Retained raw task roots remain authoritative.

Run the same small harness batch from the repository with:

```text
python -B docs/task-evidence/Q30/qualification-workflow/run_workflow_tests.py
```

The batch retains a fresh OS-temp launch/result record and every canary fixture.
It reports the planned120s batch budget; per-child execution/cleanup bounds are
enforced by the runner. A supported Windows Python is required. This is synthetic
harness validation, never electrical qualification or visible player-input QA.
No unchanged application rebuild was repeated after this docs/tool addition:
all1,354 previously consumed inputs were rehashed and match final gate evidence.

Required Q30 blockers remain the fresh full cold77 failure, private enabled gates
and supported visible20/30/40 diagnosis/repair/retest. Root75's four matched
diagnostics do not explain its failed cold run or establish cohort acceptance.
Parent acceptance review remains pending. No push, publication or email.
