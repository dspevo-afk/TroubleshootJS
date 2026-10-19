# Vacancy origin grid phase 1 worker notes

Status: phase 1 helper and contract fixture are prepared in the isolated task copy. Root integration and maintained runtime acceptance remain pending.

The copy began from the accepted native source at published HEAD 5c85b0a7cddfac801439ca12c3ca63b00e8440df and contained 1467 files. The copy is not a Git checkout. It now contains 1468 files after adding the requested contract test.

Owned copy files and final SHA-256:

- task-copy/src/com/lushprojects/circuitjs1/client/PcbVacancyTargets.java — C06BCEA03D0DF74E3C2B7565305D00DD8A1EF8ABBFCAD691FEFF3C2327901A8B
- task-copy/tests/contracts/PcbVacancyOriginGridContractTest.java — EB9D5DC97A3BEE5D7A8BCCC1122C131268DC54F4D7E8D0B7AE965EBB8F7B859B

The existing targets and nearest implementations were left unchanged. A source-only comparison against the accepted checkout found the copied source identical before the inserted helper and from largestPocket through the end of the file. targetsOnOriginGrid delegates through exactly one targets(...) call. The package-private originsOnGrid accepts the legacy vector directly, rejects nonpositive grids, null vectors/points and lists over five, returns an empty vector for empty input, and leaves all input objects untouched. Its output aligns bounds to global grid multiples using checked long operations, rounds center ties toward positive infinity, clamps the rounded center, emits center/TL/TR/BL/BR order and deduplicates within both five slots and the raw input count.

The new contract covers the literal blocked-top-band counterexample and exact expected points; unchanged grid-one output; negative coordinates and tie direction; grid-multiple and non-grid translations; exact and narrow no-grid fits; raw-vector validation, purity and output bound; integer limits and overflow; and the unchanged inclusive 20000-cell raster limit. Independent bounded geometric enumeration finds 16 legal origins in the open pocket and 40 in the known-band pocket; expected output vectors are literal fixtures.

Static source review: PASS. Existing-code comparison: PASS. Tests and runtime: NOT RUN by this worker. The new contract still needs root registration and execution with the maintained JDK8 command, alongside the existing legacy vacancy and nearest-preference suites. Routing, generation, planner integration, phase 2 and production policy were not changed or evaluated. The helper remains unused by production callers. The phase 2 caller boundary follows the root clarification: retain the raw targets vector for existing preference and reconstructed pocket score, and derive only ring-zero with originsOnGrid(raw, 10), avoiding a second raster. No planner hook was added.