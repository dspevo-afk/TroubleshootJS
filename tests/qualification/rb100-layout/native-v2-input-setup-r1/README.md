# Synthetic native V2 input/setup component

`CurrentV2InputSetup.java` is the isolated adapter; its canary uses the actual
shared Meter and a current-codec synthetic factory. The historical Java Sources,
debits and81-case/12089-assertion acceptance are bound by
[ACCEPTED_SOURCE_R1.json](ACCEPTED_SOURCE_R1.json) and the
[evidence packet](../../../../docs/task-evidence/Q100/native-v2-input-setup-r1/README.md).

The maintained helper now requires `orderChanged=true` for RUN trials. Its canary
uses the fresh pair-first synthetic profile and preserves all 81 cases and 12,089
assertions. Current Sources and evidence are in the
[RUN staging packet](../../../../docs/task-evidence/Q100/current-run-staging-r1/README.md).
The current byte pins are [ACCEPTED_SOURCE_R2.json](ACCEPTED_SOURCE_R2.json).
The old false RUN profile and its pins remain historical evidence; they must not
be reused as a successful current native input.

Private pinned Study, Shared owner, PCB, receipt and synthetic factory Source
dependencies remain required. Normal assembly and the genuine producer remain
closed. This directory does not promise standalone replay or playable Q100.
