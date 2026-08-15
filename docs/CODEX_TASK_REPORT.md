# Task 21: Post-refactor validation and preview hardening

## Preview hardening

`scripts/preview.ps1` now uses canonical root-prefix containment, rejects encoded
traversal with 404, ignores query strings for resolution, returns 404 for absent
files, handles malformed requests without terminating the listener, and closes
the listener in `finally`. It supplies content types for HTML, JavaScript, CSS,
JSON, image, SVG, font, and ICO assets, with octet-stream fallback.

Verified on port 8900: HTML returned `text/html`, the GWT bootstrap returned
`text/javascript`, and `..%2fDEVELOPMENT.md` returned 404.

## Build

The explicit JDK 8 production build completed all five GWT permutations and
linked the output successfully.

## Architecture and replacement regression

`GeneratedBoardInstance` contains only generic family state; LED-specific R1
slot, inventory, catalog, and serial allocation remain in
`LedIndicatorFamilyState`. Its `require` method explicitly rejects unrelated
family state. Task 18 replacement assertions remain unchanged.

## Visible application

Run `.\scripts\preview.ps1` and open:

`http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`

The production preview remains running on that URL.

## Files changed

- `scripts/preview.ps1`
- `docs/CODEX_TASK_REPORT.md`

## Remaining limitation

The project retains legacy GWT tooling for compilation; the production preview
is the supported visible-player workflow. The recommended next task is to resume
feature work only after the browser verifier automation environment is available.
