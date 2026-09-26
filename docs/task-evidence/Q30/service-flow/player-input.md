# Visible production workbench input

PASS, representative seed `0`, selected `DREV_OPEN`, final build recorded in
`player-build-receipt.json`. This is the explicit developer entry to the actual
Q30 challenge, not normal-player admission. The service verifier query parameter
was omitted: all actions below used real visible Browser controls.

1. Selected DC V; selected `J1 terminal 2` and clicked **Place black probe**;
   selected `DREV terminal A` and clicked **Place red probe**. Meter: **12 V**.
2. Selected `DREV terminal K` and placed red. Meter: **806.115 mV** at this
   live observation, consistent with failed downstream power. This is a live
   value, not a fixed expected simulator output.
3. Clicked DC V again to leave the mode. Clicked **Retest Customer**. The page
   displayed **Customer retest did not pass. Continue troubleshooting.**
4. Clicked **Board Power: ON**, verified **Board Power: OFF**, and selected
   DREV. Clicked **Lift lead A**; the card displayed **State: Lead lifted**
   and **Reconnect lead A**. Reconnected the lead, then removed the component.
5. The first **Install new diode** button was enabled while the other diode
   slots remained disabled. Installed the selected generic silicon diode.
6. Clicked **Board Power: OFF**, verified ON, and clicked **Retest Customer**.
   The page displayed **Customer retest passed. The reported behavior is
   resolved.** The service ticket changed to the repair-verified message.

Both retest click calls timed out waiting for input dispatch. The first startup
DOM read reported `target closed`; selecting the same existing tab handle again
recovered its ready workbench without restarting it. Later DOM reads and screenshots proved the actions had
executed and the respective final results were present; clicks were not repeated.
These host-response limits remain recorded and do not constitute latency
qualification. No hidden controller calls, injected state changes or forged
measurements were used as player input.

Four inspected screenshots: `player-01-supply.png` (board and placed probes;
meter scrolled out of this viewport), `player-02-unrepaired.png`,
`player-03-lifted.png`, and `player-04-repaired.png`. The first image's meter
reading is supported by `player-supply-dom.txt`, not by pixels.
`player-downstream-dom.txt` records the downstream observation. `player-final-dom.txt`
retains the final visible result. The preliminary `manual-initial-supply.png`
belongs to a superseded build and is not final acceptance evidence.

The player tab was closed through Browser after the visible flow. The automated
compiled census had completed in separate tabs beforehand.
No relay lead-lift claim is made: that production capability supports whole-part
removal and replacement only. Native and compiled population qualification cover
original-part reinstall and the correct replacements separately.
