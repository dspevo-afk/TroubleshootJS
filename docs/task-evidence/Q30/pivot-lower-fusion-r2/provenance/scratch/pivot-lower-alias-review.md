# LU row-alias review

No blocker found for the frozen pivot/lower-row candidate. The general `CirSim.lu_factor(double[][], ...)` documentation describes an `n × n` input (CirSim.java:8017–8020), but neither documents nor rejects aliased row arrays. I therefore do not treat distinct row identity as a formal API precondition.

The current callers allocate distinct rows: `circuitMatrix` and `origMatrix` at CirSim.java:2814/2819; the resized matrix at 2934; `CustomTransformerElm.xformMatrix` at CustomTransformerElm.java:308; and inverse scratch at CirSim.java:8907. The production factor paths are CirSim.java:2850 and 3387; `invertMatrix` uses its caller-owned input at 8904.

For duplicate row references, precollection retains one reference per lower matrix index, in the same order as the prior indexed pass. While the shared lower value remains nonzero, both versions perform the same repeated scaling and row-update sequence. If an earlier visit underflows the shared value to zero, the old loop skips later aliases, while precollection may multiply zero by the already-finite multiplier again and then drop the zero entry. That can change only the sign bit of zero: the extra product remains finite, no zero-multiplier row reaches upper updates in either version, and no failure classification or nonzero matrix value changes. Existing numeric equality treats `+0.0` and `-0.0` as equal; no universal raw-bit-equivalence claim is made.

Read-only source reasoning only. No tests or gates were run.
