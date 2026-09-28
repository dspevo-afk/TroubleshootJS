# Owned nonlinear LU zero-row early-exit review

**Scope:** source-only comparison of the frozen candidate against its stated baseline, plus static review of the added native contract source. No build, test, benchmark, or runtime gate was run.

Frozen digests independently verified:

- Baseline CirSim.java: F836BFC58BDFB87D67F25184EBCD51E60D9EB820D6CC5ACB966BCDE98A64296B
- Candidate CirSim.java: DEB593FF2471904A72690CF0332A53BC968A4C93F6F2AC28E3140FA59DC1E242
- Q30OwnedLuZeroRowContractTest.java: 797E0240B84977891E1F1852BED4CFDCBF11E6F13032ABEBDC482D32CE7A3544

## Review result

No source correctness blocker found in the frozen delta. The only CirSim.java change is an early break after the first row[j] != 0 in the preflight at candidate/src/com/lushprojects/circuitjs1/client/CirSim.java:8051-8070. This preserves the exact zero-row predicate for a well-formed row: the old loop accumulated whether any entry compared unequal to zero; the new loop stops on that same first witness. IEEE +0.0 and -0.0 both compare equal to zero, while finite nonzero values (including subnormals) compare unequal. The change performs no arithmetic or writes and leaves zero-row detection, matrix/pivot outputs on that return path, factorization, tie-breaking, and solve arithmetic unchanged. It reduces preflight reads to the first nonzero in each nonzero row; an all-zero row still requires a full scan.

The helper remains private to the owned nonlinear caller. The general lu_factor(..., workspace) overload retains its full finite-entry scan. The ownership contract is supported by the current caller path: analyzeCircuit simplifies then calls requireFiniteMatrix before later nonlinear solves (CirSim.java:2843); origMatrix is copied after simplification (:2965); each nonlinear trial restores it and calls element doStep() (:3358-3363); and the only dynamic matrix update in the doStep path is stampMatrix, which validates both the stamp and accumulated cell (:3188-3207). Thus this early exit is confined to the already-validated internal matrix path and does not remove validation from the independent-input API.

Cleanup and work ownership are unchanged: workspace.reset(n) remains before the scan and the finally { workspace.clear(); } covers the new early return as before (:8053-8070). The finite/nonlinear private helper hands successful matrices to the same shared factor routine; no pivot or solve code is in the diff.

The added native source is a meaningful, non-tautological oracle for this helper (candidate/tests/contracts/Q30OwnedLuZeroRowContractTest.java). It reflectively calls the private owned factor method but compares against the separate pre-optimization LuFactorizationChecks.originalFactor and originalSolve, then checks factor success, pivot vector, numeric factor matrix, and finite numeric solution (:13-23, :89-142). Fixtures cover empty/small matrices, off-diagonal and last-column nonzeros, subnormals, signed zeros, later-row pivot ties, zero rows at every row position, overflow cleanup, and reuse after failure (:38-85). Workspace counters and row references are checked after singular returns and arithmetic failure (:107-114, :184-187). The numeric == comparisons intentionally do not assert signed-zero bit preservation.

## Limits

This review establishes source-level equivalence only. The test does not measure row reads or performance; the explicit break makes the intended bound inspectable, but benefit on representative solver matrices still needs measured runs. The private helper contract assumes an internally owned, square matrix; behavior on malformed/ragged arrays is not preserved or tested and is outside this call path. The focused test invokes the helper directly; it is not an end-to-end nonlinear CircuitJS job. No runtime PASS is claimed.
