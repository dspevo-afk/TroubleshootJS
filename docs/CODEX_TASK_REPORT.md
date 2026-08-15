# Task 20: Visible preview repair

## Root cause

The port-8888 process was the legacy GWT 2.7 `DevMode` launcher. Its generated
bootstrap includes the obsolete hosted-mode plugin path (`circuitjs1.devmode.js`)
and is not a reliable modern browser preview. The host request returned 200 and
port 9876 was reachable, but those facts did not establish that a browser could
initialize the application.

## Fix

Added `scripts/preview.ps1`, a dependency-free localhost static server for the
already compiled `war` output. It validates the production bootstrap before
listening and prints the normal player URL. `DEVELOPMENT.md` now distinguishes
this reliable preview from legacy GWT development mode.

Use:

```powershell
.\scripts\preview.ps1
```

Visible player URL:

`http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`

The preview server was started and verified to return both the player host page
and `circuitjs1/circuitjs1.nocache.js` with HTTP 200.

## Architecture regression

Task 19 remains intact: `GeneratedBoardInstance` holds generic family state
only; `LedIndicatorFamilyState` owns the R1 slot, resistor inventory/catalog,
and serial allocation, and rejects the wrong family state.

## Files changed

- `scripts/preview.ps1`
- `DEVELOPMENT.md`
- `docs/CODEX_TASK_REPORT.md`

## Remaining limitation

Legacy GWT 2.7 DevMode remains available for compiler development but is not the
recommended visible-player workflow. The static preview should be used for
normal interactive inspection.
