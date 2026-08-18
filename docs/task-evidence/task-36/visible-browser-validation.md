# Task 36 visible in-app Browser validation

Date: 2026-08-17

The local production preview was kept visible in the in-app Browser throughout
this player-facing check. The direct RC challenge used the normal player route
with seed 3; supplemental automated coverage used the deterministic verifier
routes listed in the Task 36 report.

Directly observed through visible clicks and probe placement:

- The initial RC PCB showed an electrolytic C1 with its `+`/`-` terminals,
  stripe/polarity rendering, `33 uF 16 V` marking, and a separate ceramic C2
  marked `104`. The service ticket was generic and exposed no fault answer.
- With power off, C1 was selected, removed, and then reinstalled through the
  visible replacement catalog. The repaired direct challenge subsequently
  reported `Repair verified. The controller delay is operating normally.`
- With real DC probes on C1's board pads and board power on, the rendered meter
  read `4.799 V`; this is the real `CapacitorElm` charge result, not a timer
  display.
- After the visible board-power-off action, resistance mode on the same pads
  immediately rendered `DISCHARGE`, then after natural solver decay a renewed
  resistance measurement rendered `227.291 mOhm`.
- A fresh visible Quick Play page loaded after the repaired RC session and
  showed a new generated Parallel Indicators board, customer ticket, empty
  parts tray, and Finish Job control.

Browser-control limitation:

The in-app Browser's required real-input surface focused the native capacitor
`<select>` but did not route visible arrow/type-ahead input to its alternative
options; menu-coordinate clicks activated the underlying install button. No
DOM mutation, synthetic selection, or Windows desktop automation was used to
work around that limitation. Consequently, the wrong-value installation was
not directly repeated in this visible pass. The focused automated RC verifier
does exercise both wrong low/high capacitor values, their rejected repair
status, and correct replacement; it passed in the same final production build.

Supplemental automated screenshots are preserved beside this file:
`rc-initial.png`, `rc-rising.png`, `rc-charged.png`, `rc-residual.png`,
`rc-discharge-refused.png`, `rc-discharge-ohm.png`,
`rc-discharge-continuity.png`, `rc-discharge-diode.png`,
`rc-discharged.png`, and `rc-repaired.png`.
