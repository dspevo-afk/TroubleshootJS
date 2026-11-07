# P9 preserved failures and final qualification

Final gate status: **R15 PASS_FINITE_DIAGNOSTIC_ONLY; locally accepted.**
Publication is recorded separately in the current task report. The accepted candidate has the same 19 frozen inputs,
including reader `6314c3cf` and Study `545d3cd1`. Earlier failures, NOT RUN attempts
and missing proof remain recorded below; later success does not relabel them.

| Attempt | Observed result and correction |
| --- | --- |
| R1 | CANARY failed `INPUT_PREFLIGHT_INPUT_PINS_CANONICAL`. The PowerShell JSON escape loop appended escaped characters twice. The corrected serializer matched the genuine pins bytes in a focused component check. No compiler or native operation ran. |
| R2 | CANARY failed `INPUT_PREFLIGHT_ROLE_PIN_ROW`. Generated Router metadata belongs to the nested derived inventory, not the 27 top files. Root/path resolution and Router selection now use the authenticated origin, kind, role and relative path. No compiler or native operation ran. |
| R3 | CANARY passed SRC01, then failed `SOURCE_MUTATION` before SRC02 launched. Inventory helpers incorrectly expected a root prefix inside the relative path. Helpers now bind `origin` separately; an unintended parent-path echo was also removed. |
| R4 | All 12 source CLI controls and literal checks passed; the first host control failed. Its fixture supplied the valid digest. That path exposed a separate PowerShell bug: regex matching overwrote a local variable named `$matches` through automatic `$Matches`. Both defects were corrected. |
| R5 | Full CANARY **NOT RUN**. Review found that a wrong PLAN hash would reach a generic audit-process error. Host validation now checks all 27 top-file rows after its self pin and before output creation or auditor invocation. Focused checks are component proof only. |
| R6 | CANARY failed `HOST_CONTROL_EXIT` at HOST04. The child rejected the existing output correctly, but module naming advice polluted stdout. The pinned module import now uses `-DisableNameChecking`; error handling and module bytes remain unchanged. |
| R7 | CANARY failed at positive-host `SOURCE_PREFLIGHT`: the nested auditor exceeded the unchanged 60,000 ms limit; the failed call took 60,408.0721 ms. Its receipt was not returned: `process=null`, `terminationProven=false`. Termination remains **unknown**, not PASS. A separate diagnostic completed in 57,852.4369 ms and located repeated maximum-buffer read requests. The correction reads observed file size plus one byte after the existing size-limit guard, preserving limits and all identity, growth, hash and stability checks. |
| R8 | CANARY failed `P9_BRIDGE_MANIFEST_FORMAT` before compiler invocation. The original pinned 519-byte bridge manifest contains mixed CRLF/LF whitespace. The adapter now authenticates its exact original bytes before parsing JSON, retaining all three-row/name/length/hash checks. The manifest was not rewritten. |
| R9 | CANARY reached real compilation and native canaries, then failed `P8_CANARY_SCHEMA`. The adapter expected an invented schema rather than the pinned producer's `tsj-q100-geometric-reason-canary-v1`. Host and reader now bind that exact schema and reject aliases. These partial results were not whole-CANARY acceptance. |
| R10 | Full CANARY **NOT RUN**. Review found that the reader compared original bridge paths with the host's three scratch copies after scratch cleanup. The reader now supplies the authenticated scratch destination; source enumeration validates canonical destination names even after removal. Exactly three substitutions preserve the 1085-source list. No new file body is admitted by that enumeration. |
| R11 | CANARY failed `P9_HOST_RESULT` in the independent reader. PowerShell and Python serialized the same finite double differently. The reader now enforces strict compact finite ASCII JSON without requiring Python's spelling, while preserving duplicate/nonfinite/overflow/framing rejection, closed schemas, raw hashes and canonical operand checks. |
| R12 | CANARY failed `P9_OUTPUT_TREE_CENSUS`: all 54 files matched, but three parent directories of explicit empty control directories were missing from the reader's expected tree. Ancestor closure was corrected. Source review also found stale artifact operation labels: non-run artifacts must be `HOST`. That mapping was corrected; it was not a separately observed runtime failure. |
| R13 | Internal CANARY qualified: source12, literal12+16, host5, JDK8/P6/P8/P9 canaries, five native argument controls, actual reader plus five CLI negatives, and final source/output/cleanup checks completed. SUITE then passed its early host5 controls but failed during NULL: the P9 wrapper raised `IOException: P8 did not finish all 22 actual queries` at `readP8Evidence`. Genuine P8 completed the selected 14 queries (`queryCount=14`, `returned=14`, coarse14/fine0), while P6 retained its independent original22. No OBSERVE, TAP_THROW or old throw mode started. The outer controller exited2; this is not SUITE acceptance. |
| R14 | Internal CANARY qualified and all five native SUITE modes completed. The host remained `HOST_COMPLETE_READER_PENDING`; the whole run failed at `SUITE_READER_POSITIVE/P9_P8_SEMANTIC`, with `KeyError: pinsSha`. The P9 caller had omitted three required authenticated current-invocation fields when calling the genuine P8 ancestor. The outer call exited2 after 585,869.0098 ms; controller time was 585,341.0769 ms, with 32 returned processes. Native completion is not independent-reader or SUITE acceptance. |
| R15 | **PASS_FINITE_DIAGNOSTIC_ONLY**, locally accepted after the complete combined CANARY-to-SUITE run. Controller status `PASS_RESERVATION_SUITE_CONTROLS_ONLY`; independent reader status `PASS_RESERVATION_PROVENANCE_DIAGNOSTIC_ONLY`. All required controls, final source/output checks and process/resource closure passed. Publication remains pending. |

R13 NULL's genuine P8 work was 14,336,980,600 ns and its aggregate output was
5,667,809 bytes. Independent work remains P6 3335 plus P8-owned 2746 = 6081 units.
The outer call took 471,195.01 ms; controller time was 470,618.5803 ms, with 31
returned process receipts. Host cleanup reported no error, owned scratch was
absent, and the complete private failure bundle is retained. This closes those
R13 observations only; R7's unreturned nested process remains unknown.

The minimal Study `cff84769` correction binds query and returned counts to the
selected 14, with fine counts 0 or 14. Exact selected order and independent
original22 P6/P8 reader proofs remain unchanged; no new coarse-count guard was
added. A focused JDK8 compile of all 1085 sources passed in 6,975.4544 ms. A
read-only decoder component passed in 456.0652 ms against genuine R13 NULL
metadata (254 groups, 820 lines, 6081 units), rejected a wrong run identity and
verified reference release. It generated no new operation and does not qualify
SUITE.
After augmenting only the required invocation context, scoped genuine R14 NULL
P8 semantics passed in 30,596.0531 ms; the first private helper's SyntaxError was
preserved separately before that corrected attempt. The corresponding OBSERVE
P8 component passed in 30,307.753 ms. The next OBSERVE operand/slot reconstruction
passed, but its index-work check failed in 30,495.9867 ms: the old credit omitted
23,600 ns from its declared export interval. A separate synthetic protocol probe
also exposed NULL common-header scope; that was not R14's whole-run first failure.
None of these components renews whole-run acceptance.

Reader `6314c3cf` and Study `545d3cd1` correct the ancestry fields, NULL common-header
scope 10/11, and measured preflight, delegate-return and export boundaries. The
three post-return cleanup sites and DTO cleanup are measured separately and
subtracted; caps and oracles are unchanged. A fresh JDK8 compile of all 1085 sources
passed in 7,433.6863 ms. Actual native NULL/TAP serializer fixtures were both
accepted by the reader in a 384.0978 ms component; their flags do not prove
cleanup, native clock runtime or SUITE completion.
The final R15 root call exited0 in 637,103.9118 ms, with 14,344 stdout bytes,
empty stderr, proven termination and an absent owned process root. All source12,
host10, reader10, literal12+16 and semantic16 controls passed. All 38 returned
children had verified absent process/log roots; the 17 native-host child exits
were proven. Final inputs and outputs were unchanged and source was rechecked.
The independent diagnostic covers only the one authored BASE parent11 slot: it
reconstructed MIXED from two pads on two distinct nets, reproduced digest prefix
`75f2f591`, and passed all four parity flags. No contributor net identities are
published here. Native phase charge was 55,308.3788 ms; the composite cleanup
upper bound was 101,921.7177 ms, below 120,000 ms. The outer call remained below
1,800,000 ms.

Source-only timing review accepted the export interval in intermediate Study
`1a180b7e`, then identified a preflight handoff gap. Final Study `545d3cd1` places
the actual handoff immediately before the P8 call and the return timestamp before
P9 flush/check; cleanup remains separately measured. Final clock-formula, work,
source and semantic proofs passed without changing limits or oracles. These
results establish local finite diagnostic acceptance, not population, production
or player qualification. The source/evidence packet records this accepted candidate; publication is tracked
separately in the current task report.
A separate Win32 identity hardening change explicitly types the API arguments and
return values and checks `CloseHandle`. The earlier real positive had already
passed; no failure is attributed to it. The final-reader component recorded 25
positive checks, rejection of 24 wrong-tick and 24 missing-path cases, and an
unchanged handle count of 276. This is component evidence, not full-gate proof.

Original failed receipts, partial outputs, archived source and private failure
references through R14 remain retained. R5 and R10 remain NOT RUN, and the R7
unreturned nested process remains uncertain. Later passes cannot relabel them.
No global cleanup claim is made. All original budgets and independent oracles
remain in force; the audit timeout remains 60 seconds. This finite diagnostic makes no new
production, electrical, player, population or protected-case qualification claim.
