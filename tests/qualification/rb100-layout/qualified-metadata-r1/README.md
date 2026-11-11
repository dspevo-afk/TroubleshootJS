# Current assembly metadata component

Current native builds use the [current five-file CODE inventory](../current-native-source-r1/README.md).
This directory retains its earlier component Source/proof; it is not the current
assembled native baseline.

`prepare` retains the qualified-prior metadata and receipt-binding behavior
recorded by the historical R1 evidence. The current helper also owns
`cohortBeforeBody`, a synchronous temporary view over a validated, exclusively
owned input. Its returned lists are detached; the raw prior view never enters the
result body and does not replace `prepare`.

Current Source qualification is linked from the
[RUN staging packet](../../../../docs/task-evidence/Q100/current-run-staging-r1/README.md).
Normal assembly, genuine capture and actual host authority remain closed.
