# Task 22: Self-contained browser verification

## Preview bug and root cause

The committed `preview.ps1` declared `getContentType` after the blocking request
loop. A fresh PowerShell process could therefore reach the call before the
function declaration executed; the request catch converted that error to HTTP
400. The helper now appears before listener startup. No other request-time helper
has an order dependency.

## Clean-process preview validation

A brand-new PowerShell process ran the working-tree `scripts/preview.ps1` on
port 8955. Observed results:

| Request | Status | Content-Type |
|---|---:|---|
| `circuitjs.html?tsjChallenge=led&seed=3` | 200 | `text/html; charset=utf-8` |
| `circuitjs1/circuitjs1.nocache.js` | 200 | `application/javascript; charset=utf-8` |
| `circuitjs1/gwt/clean/clean.css` | 200 | `text/css; charset=utf-8` |
| `font/fontello.woff2` | 200 | `font/woff2` |
| `favicon.ico` | 404 | none |
| `does-not-exist.xyz` | 404 | none |
| `..%2fDEVELOPMENT.md` | 403 | HTTP.sys rejection before script dispatch |

Query strings did not affect file resolution. The traversal request never
reached repository content; Windows HTTP.sys rejected it at the listener edge.

## Browser-verification architecture

`scripts/verify-browser.ps1` launches the installed Microsoft Edge with a fresh
profile and fixed 1440x1000 viewport, connects through Edge's built-in DevTools
Protocol, enables runtime/page error capture before navigation, and applies a
finite timeout. It prints human-readable per-route results and exits nonzero on
failure; an unreachable test route was observed returning exit code 1.

Verifier routes publish only their genuine existing PASS/FAIL result as the
developer-only `data-tsj-verification` document attribute. The signal is set
after the verifier returns and does not alter solving, assertions, mutations, or
normal-player behavior.

Commands:

```powershell
.\scripts\verify-browser.ps1
.\scripts\verify-browser.ps1 -NormalPlayer
```

## Explicit JDK 8 production build

The explicit JDK 8 build compiled all five GWT browser permutations and linked
successfully into `war/circuitjs1`.

## Full 15-route matrix

| Seed | Resistance | Meter | Challenge | Replacement | Challenge + replacement |
|---:|---|---|---|---|---|
| 0 | PASS | PASS | PASS | PASS | PASS |
| 2 | PASS | PASS | PASS | PASS | PASS |
| 3 | PASS | PASS | PASS | PASS | PASS |

All 15 routes completed with zero timeouts, zero published verifier failures,
and zero captured unhandled JavaScript or failure-class console exceptions.

## Normal-player interaction

The production seed-3 player was exercised with CDP mouse and keyboard input,
using live DOM and visible-canvas geometry rather than controller calls. The PCB,
meter, power control, R1 actions, catalog, and initially empty Parts Tray all
rendered. Board power was turned off, the failed original was removed, and the
correct 1 kOhm catalog item was selected using keyboard input and installed.
Only the original remained loose. Power-on produced the solver-backed `Repair
verified. Indicator operating normally.` state. After power-off and removal, two
distinct 1 kOhm tray entries were visible. The healthy loose part read exactly
`1 kOhm` forward and `1 kOhm` reversed; `R1_ORIGINAL` read `OL`. No captured page
or relevant console error occurred.

## Task 19 architecture regression

`GeneratedBoardInstance` still owns only the generic
`GeneratedBoardFamilyState` reference and has no R1 slot, resistor inventory,
catalog, serial allocator, or component-specific getter. `LedIndicatorFamilyState`
continues to own all LED/R1 replacement state and explicitly rejects unrelated
family state.

## Files changed

- `scripts/preview.ps1`
- `scripts/verify-browser.ps1`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `DEVELOPMENT.md`
- `docs/CODEX_TASK_REPORT.md`

## Visible app

Command: `.\scripts\preview.ps1`

URL: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`

## Known limitations and recommended next task

The browser runner currently targets the installed Windows Edge path by default;
another Chromium executable can be supplied with `-BrowserPath`. Legacy GWT
DevMode remains compiler-development machinery, not the visible preview.

With the Task 19–22 validation gap closed, the next feature task may introduce a
new family-specific state implementation without adding component-specific state
back to `GeneratedBoardInstance`.
