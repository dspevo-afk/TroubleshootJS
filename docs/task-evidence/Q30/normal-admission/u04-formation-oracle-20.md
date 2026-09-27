# Successful catalog formation: solver-state boundary

The diagnostic GWT19 aggregate **FAIL**ed after 583.456 seconds in composed
case 40. Its exact first difference was canonical element 7, `NTransistorElm`:

```text
before: t 16000 16000 16080 16000 0 1 2.9493369103320677e-31 2.752067730968951e-38 100 default
after:  t 16000 16000 16080 16000 0 1 2.9493366253179973e-31 -2.949336622368664e-38 100 default
```

`TransistorElm.dump()` serializes two live junction-voltage differences at
zero-based tokens 7 and 8. The remaining fields identify the element's type,
coordinates, flags, polarity, gain and model. The independent diagnostic shows
those configuration fields unchanged. Real removal, installation and restoration
invoke settlement, so these near-zero numerical solver values need not retain
their earlier bit pattern. No solver voltages should be overwritten to make a
snapshot compare equal.

The bounded correction is limited to the successful formation/restoration
oracle: ignore those two known transient fields and the already-excluded
simulation time while retaining exact configuration, graph identity, endpoints,
attachments, inventory, serial and fault checks. Failed mutation compensation
continues to require the original raw exact-state comparison. Other element
types receive no new transient exemption. The oracle must reject malformed
transistor dumps and independently altered static fields.

This is a developer-verifier correction, not a physics or player-state change.
The diagnostic run restored its predecessor in 1 ms; its 1,522-input audit and
host cleanup passed. Its explicit failure canary passed separately in 0.761
seconds. GWT19 remains a retained failed aggregate, not acceptance evidence.
The corrected source still requires the full aggregate and independent reader.
