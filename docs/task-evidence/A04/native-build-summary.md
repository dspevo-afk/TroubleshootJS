# A04 final native and production-build evidence

Candidate: the A04 delta above `1d995d4f5b21142d4ca5643ede546afdfe51340f`, branch `codex/task43p-final-recovery`. The exact 987 source/script/test inputs are in `candidate-inputs.json`; they were unchanged from the final native/build run through both final compiled runs. Runtime provenance independently covers 1,320 execution files.

Toolchain: repository Temurin JDK 8u502 / GWT 2.7; installed CPython 3.12.8 for the independent Python oracles. Processes were launched noninteractively with closed standard input. No dependency, solver, golden, or accepted electrical-contract changes were made to pass these gates.

| Gate | Actual command / production path | Result |
| --- | --- | --- |
| A04 report protocol and native contracts | `scripts/verify-a04-contracts.ps1 -JavaHome <repository-jdk8> -ReceiptOutputPath <owned-temp-receipt>` | Exit 0; report protocol 103, A03 shared vectors 109 / replay 29, A02 candidate PASS / geometry 66 / replay 112, package map 37, construction 433, physical declarations 730; scratch cleanup PASS |
| Pure assembly and independent references | `scripts/verify-assembly-contracts.ps1 -JavaHome <repository-jdk8> -PythonExe <installed-python312> -ReceiptOutputPath <owned-temp-task47> -ControlledReceiptOutputPath <owned-temp-task48> -SynthesizedReceiptOutputPath <owned-temp-task49>` | Exit 0; Task46 independent 22 vectors; Task47 pure 269; Task48 pure 224 and switched compatibility 12; Task49 synthesis 137; independent Task47/48/49 references each cover eight seeds; scratch cleanup PASS |
| Final production compile | `scripts/build.ps1 -JavaHome <repository-jdk8> -Style OBF -Target Compile` | Exit 0; all five OBF permutations compiled and linked; 58.185 s compile, 1.222 s link |
| Canonical replay parity | Final native receipts compared byte-for-byte with compiled A03 reports and accepted A03 fixtures | PASS: 17,505-byte shared vectors and 20,121 / 63,897 / 66,531-byte v1/v2/v3 manifests; SHA256s in `compatibility-reconciliation.json` |

The native runner compiles the actual A04 production classes. Its existing isolated Task35 verifier replacement fails closed if called; it does not substitute for the production GWT build or compiled electrical gates. Native declaration counts are not claimed as solver assertions.

The full raw development transcripts remain external task scratch. Curated final browser receipts, final source inputs, and provenance identify the qualified candidate without committing repeated raw logs or redundant manifest copies.

Final foundation cross-check on the unchanged candidate also passed: `scripts/verify-block-contracts.ps1 -JavaHome <repository-jdk8>` ran Task44 (9 groups, 247 assertions) and Task45 (202 assertions); `scripts/verify-challenge-contracts.ps1 -JavaHome <repository-jdk8> -PythonExe <installed-python312>` ran the 22-vector independent seed oracle and Task46 (410 assertions, 36 negative cases). Both exited 0 and removed their owned scratch. These are fresh final checks, not inferred from compilation.
