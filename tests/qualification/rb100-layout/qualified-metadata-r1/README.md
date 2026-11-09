# Current assembly metadata component

`prepare` retains the qualified-prior metadata and receipt-binding behavior
recorded by the historical R1 evidence. The current helper also owns
`cohortBeforeBody`, a synchronous temporary view over a validated, exclusively
owned input. Its returned lists are detached; the raw prior view never enters the
result body and does not replace `prepare`.

Current Source qualification is linked from the
[RUN staging packet](../../../../docs/task-evidence/Q100/current-run-staging-r1/README.md).
Normal assembly, genuine capture and actual host authority remain closed.
