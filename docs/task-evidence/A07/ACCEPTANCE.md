# A07 acceptance and implementation boundary

Base: `6ffeb91aa4cf5a234ffaeaa1255a1791d239b4dd`. Scope: A07 only.
Root owns all source edits; reserve is reserved for independent read-only review.
Pre-existing untracked `tests/contracts/__pycache__/` is excluded.

| Gate | Production boundary / independent expectation | Evidence |
|---|---|---|
| Execution | CircuitJS analysis/stepping use exclusive leases, finite work and wall deadlines; nonfinite, singular and nonconvergent solves cannot publish | JVM contract tests and compiled real solver negatives |
| Observation | Exact context, owner, revision and accepted state; time rebases, source commands, failed trials and retired operations cannot mint readiness | JVM identity negatives, real A06 meter and storage regression |
| Private work | Detached original elements never stepped; explicit private lease survives yields; cancellation and stale callbacks cannot restore over successor | Compiled original-state equality, concurrent/cancel/stale callbacks |
| Scheduling | Accepted-step-only state, chronological/FIFO tie order, explicit reset phase and finite same-time feedback | Shared JVM/GWT synthetic state canary and real accepted-step use |
| Model pilots | Small relay, transformer, nonlinear and switched converter; declared predicates and timestep comparisons, explicit limits | Compiled solver results with model assumptions |
| Scale | A01 20/40/60/100 synthetic workload and counters; physical package qualification explicitly separate | Fresh measured matrix/work/time/allocation-proxy records |
| Current behavior | Maintained native contracts, JDK8/GWT all permutations, A06 compiled selection and affected ordinary player flow | Final candidate gate receipts/screenshots |
| Review/closure | Fresh non-author reserve review, targeted follow-up, curated documentation, normal commit/push/email | Actual outcomes only |

CircuitJS remains the only electrical solver. No A08 transaction framework, new
playable family, worker migration, MCU emulator, or historical compatibility layer.
Existing synchronous generated admission is not made into a second live solver.
Execution policy: UI batches yield after 8 ms; short operations and individual
step attempts are bounded by 500 ms. Existing temporal consumers retain their
200,000-step limit and have a separate 5,000 ms total bound. The initial shared
500 ms total incorrectly treated a 150,000-step RC discharge as a short UI batch;
the budgets are explicit, without changing its 5 us timestep or physical duration.
Model failures are characterized honestly; infrastructure failures are not PASS.

## Final qualification

All acceptance rows are satisfied by the final-source receipts and screenshots
in this directory: 17 native suites plus independent oracles and 259 report
assertions, production GWT 5/5, maintained-reader compiled 9/9, and final real
player input/repair/retest. One reserve read-only session completed initial
review plus two bounded deltas; all blockers reconciled and deltas PASS.
The failed CLI ownership preflight is NOT certified by equivalent browser proof.
Scale evidence is fresh-context replicates, not cached-context warm benchmarking.
A08 remains unstarted.
