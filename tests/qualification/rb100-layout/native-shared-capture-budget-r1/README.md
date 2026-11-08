# Native original-capture shared budget

This standalone native prerequisite derives two exact external evidence inputs:

- T `FailureCohortRoutingStudy.java`: SHA-256 `c33f6f696fb52fedce583f19f47f3ff44ca01bca3c9b97410291d5284642d88b`.
- `ActualGeneratorBranchOrderStudy.java`: SHA-256 `264c89e58e0e29b3375520459d627d945ec28cd747065647181ce118d75dd9f6`.

The parents and authenticated native dependency closure are required private
evidence inputs, not vendored here. This is not a standalone checkout test.

```text
python -I -B generate_shared_budget_overlay.py --capture-source <exact-capture-parent> --owner-source <exact-study-parent> --output-dir <existing-empty-owned-directory>
```

The transformer checks names, hashes, exact-once replacements, empty output and
reparse-free path components; output creation is exclusive. Supply an owned
OS-temp directory. It is not a security sandbox against concurrent path changes.
Partial failed output is retained for diagnosis.

Compile both derived sources and `SharedCaptureBudgetCanary.java` with selected
JDK8 source/target1.7, UTF-8, empty sourcepath and authenticated dependencies.
Current production routing-owner classes must precede the historical classes.
Run `com.lushprojects.circuitjs1.client.SharedCaptureBudgetCanary` with assertions
and one absolute existing empty owned data directory argument. It exercises real
private group/deadline/adoption methods, without executing authored generation,
capture or any Session. The directory remains empty.

The selected native owner's old constructors preserve null/default behavior.
The opt-in constructor shares the producer's actual Meter. Group checks include
wrapper, open and live owner credits; actual owner ordinals are adopted once
before readback. The same producer-start clock reaches cooperative owner checks.
Existing group timing/units, local completion preflight and budgets remain.

See [acceptance, evidence and limits](../../../../docs/task-evidence/Q100/native-shared-capture-budget-r1/README.md).
