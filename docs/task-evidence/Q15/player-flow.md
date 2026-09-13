# Q15 production player checks — PASS

These checks used real visible controls in the Codex in-app Browser against the
final five-permutation build identified by `candidate-provenance.json`. No
controller calls or injected application state supplied these player results.
All five JPEGs are original Browser screenshot bytes and were visually inspected.
The board canvas remains fixed while the actual sidebar scrolls to the selected
controls. These images are selected viewport states, not stitched page exports.

## Seed 0: NMOS, resistive conditioning, drive resistor open

Route: `?tsjChallenge=control-board&seed=0`.

1. Normal admission exposed sixteen component choices and the service complaint.
   **Retest Customer** failed before repair. The board and complaint are visible
   in [the initial state](player-01-unrepaired.jpg).
2. **Disconnect Supply 1 (+12V)** left Supply 2 (+5V) connected. **OHM**, terminal
   selectors for RDRIVE terminals 1/2 and **Place red/black probe** reported
   **POWER OFF**, correctly refusing the active resistance measurement.
3. **Board Power: ON** disconnected both sources. The same probes then read
   **199.96 kOhm**, reflecting the real parallel path through the two 100 kOhm
   resistors around the open drive resistor. This is not an isolated-part OL test.
4. Selecting **OHM** again exited the meter. **Inspect component: RDRIVE** and
   **Remove component** moved the original to the tray. The resistor catalog's
   **100000 Ohm +/-5%** entry and **Install new resistor** installed an alternative
   to the original 1 kOhm value.
5. Board power ON, **Set control HIGH**, **DC V** and J4 terminal selectors/probe
   buttons measured **11.092 V**. LOW measured **2.031 uV**; HIGH restored the
   output. Bench readings on HIGH were **11.996 V / 84.953 mA** and
   **5 V / 73.892 uA**.
6. **View bottom copper**, **Fit board**, then left/right clicks on J4's visible
   bottom-side pads retained the correct HIGH reading. **Retest Customer** passed
   with the alternative resistor. [Repaired bottom view](player-02-nmos-alternative-repaired.jpg)
   shows connected copper, the original in the tray and customer completion.

Normal DOM contained neither verification report attribute nor the private fault
identifier. No Browser warning/error was recorded. The completed tab was closed.

## Seed 1: BJT, resistive conditioning, relay coil open

Route: `?tsjChallenge=control-board&seed=1&tsjVerifyQ15=true&tsjQ15Fail=true`.
The deliberately supplied verifier flags had no effect without `tsjDebug=true`:
normal gameplay admitted, both verification attributes remained absent, and no
private family/fault/diagnostic answer appeared in the visible DOM.

1. Unrepaired customer retest failed. Power OFF and **Inspect component: K1**
   showed the actual **12 V coil / SPDT / 24 V DC, 0.5 A contacts** marking.
2. OHM across A1/A2 settled the isolated graph and read **52.886 kOhm** through
   remaining circuit paths. Removal was unavailable before that settled
   observation. Exiting OHM made **Remove component** available.
3. Removing K1 and installing the catalog's **5 V coil** relay, then powering ON
   and retesting, **failed**. Supply current rose to **156.243 mA** at **11.992 V**.
   [Wrong replacement](player-03-bjt-wrong-relay.jpg) shows the installed 5 V
   marking, the original in the tray and failed customer retest.
4. Power OFF and OHM measured the replacement coil at **124.999 Ohm**. Exiting OHM,
   removing the wrong relay and installing the **12 V coil** entry restored the
   intended rating. Original and wrong parts remained in the tray.
5. Power ON and DC V across J4 gave **11.093 V** on HIGH and **2.031 uV** on LOW.
   The HIGH bench read **11.996 V / 84.876 mA** and **5 V / 2.178 mA**.
6. HIGH, exit DC V, inspect K1, fit board and customer retest **passed**.
   [Correctly repaired BJT board](player-04-bjt-repaired.jpg) shows its 12 V
   marking, both removed parts and the successful customer result.

No warning/error was recorded for this flow.

## Visible cancellation and signed pad input

After the seed1 repair, **New control board** was exercised. An initial Browser
locator click on **Cancel board preparation** timed out resolving its DOM node;
the subsequent retry found no target because admission had completed. A later
coordinate-click attempt also lacked a confirmed cancellation result. These are
retained unsuccessful input attempts, not passing cancellation evidence. No
application terminal failure was reported. The generated timestamp seeds for
these supplemental admissions were not exposed by the normal UI or independently
recorded; they are excluded from the eleven-seed qualification and timing cohort.

A fresh **New control board** action followed by the visible DOM input API's
click on the observed **Cancel board preparation** node produced **Board
preparation cancelled.** The prior board, complaint, normal power controls and
bench readings returned. This establishes visible restoration; the compiled
seed3 cancellation canary separately proves exact owner/graph identity and
bounded handler cleanup.

On that restored normal board, DC V and **Fit board** exposed J1. Direct left
click on its +12 V pad and right click on its return measured **+12 V**. Reversing
the same clicks measured **-12 V** and visibly reversed red/black markers, with
no context menu. [Signed probes and cancellation](player-05-probes-cancelled.jpg)
shows both results. Selecting DC V again returned the idle display. No Browser
warning/error remained; the final tab was closed and the Browser tab list was
empty. User input queue latency was not separately timed.
