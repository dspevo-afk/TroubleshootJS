# Synthetic RUN input and pre-body cohort staging

Current integration uses the [two-phase capture/cohort component](../capture-cohort-phases-r1/README.md), which supplies raw Cohort before arms and stages the body afterward. Earlier integration pointers below describe historical stages.

Integration now has a [capture-bound body component](../capture-bound-body-r1/README.md)
that derives the cohort from the same capture Map retained in the body. This
directory's independent staging seam and historical candidate remain scoped;
the new binding grants no normal producer or actual capture authority.

The maintained input helper requires `orderChanged=true` for `RUN` trials,
matching the shared owner. `CurrentAssemblyMetadata.cohortBeforeBody` borrows the
validated prior through a synchronous temporary view, then returns detached
cohort lists before body construction. It does not prepare qualified metadata or
enable the normal producer.

This directory supplies a fresh explicitly synthetic factory/context, the new
compatibility canary, literal-order/census reader and structural profile tests.
The existing input canary retains all 81 cases and 12,089 assertions. Source pins,
native results, dependency reuse and limitations are recorded in the
[evidence packet](../../../../docs/task-evidence/Q100/current-run-staging-r1/README.md).
Private pinned dependency Sources/classes remain required for native execution;
this is not a standalone producer or a playable Q100 qualification.

The pure test command takes `--reference-root` for this directory and
`--codec-source` for the unchanged `current-context-kernel-r1/context_codec.py`.
Use isolated Python with bytecode disabled (`-I -B`). The independent reader takes
`--input-json` for actual canary stdout and `--reference-root` for this directory.
