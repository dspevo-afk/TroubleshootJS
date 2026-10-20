Draft-only fixture handoff for the r2 reference-lead-clearance scope.

- Patch SHA-256: 5ce168eefd3e8829ce7ff5eb35f48270153cefb9499fc94cf5e03124f2f5deb8
- Patch targets only `tests/contracts/PcbSilkscreenScaleContractTest.java` and preserves the current assertions.
- It adds literal legacy four-candidate checks for DEG_0/TOP and DEG_90/TOP, a copy-only initial-planning fixture, independent lead/body rectangles, exact occupied-envelope checks, nearest-nudge metadata and geometry checks, exact silkscreen-record stripping, and no-mutation rejection cases for populated and sealed layouts.
- Fixture expectations use literal placed lead/body geometry and do not derive expected values from `getLeadBounds`.
- Static `git apply --check` is the only permitted validation here. Java/native/build execution was not run; the patch depends on the future private r2 generator methods and must be applied only after root freezes that implementation scope.
Root owns this draft after interrupting the leaf. Original malformed transport preserved separately; corrected hunk counts/final LF passed root git apply --check --whitespace=error-all (exit0). All added code/context bytes retained. No Java/native result claimed.
