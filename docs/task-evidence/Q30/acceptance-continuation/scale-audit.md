# Current scale boundary

Read-only audit of base `745d537`; no scale expansion is claimed.

`Rb30Plan.SupportVariant` selects only 33, 35 or 37 actual packages. The core
count follows the physical declarations in `Rb30Plan.board()`:

| Functional group | Packages |
|---|---:|
| Main connector, fuse, reverse diode, input capacitor | 4 |
| Regulator, input/output capacitors, enable resistor | 4 |
| Two decision packages, series sensor resistors, raw-input pull-downs | 6 |
| Separate references, or shared reference plus two feedback resistors | 4 |
| Two transistor/bias/drive/flyback/relay paths | 10 |
| Two sensor connectors, load supply connector, two output connectors | 5 |
| Core total | 33 |
| Optional status resistor and LED | +2 |
| Optional two sensor-filter capacitors | +2 |

These parts perform electrical functions. Removing raw sensor pull-downs is
not a safe count reduction: the finite input source cannot sink feedback
current, and those resistors define LOW. Removing protection, regulator
support or relay suppression likewise requires a different proved recipe.
The shared-reference arrangement has feedback resistors in place of the
second divider; it is structurally different but does not lower the count.

The exact unimplemented counts in the requested band are **20–32, 34, 36,
38–40**. This does not assert that every integer count must have its own
recipe, or that smaller purposeful boards are impossible. It records what
the present implementation and corpus actually contain. The three existing
midrange counts do not establish broad 20–40 coverage.

A compact architecture must retain meaningful repeated outputs and interacting
regulated/control domains. A single-channel deletion would avoid the actual
Q30 deliverable. A new dual-output implementation can instead investigate
existing BJT/NMOS direct-load providers, with its own source/return contract,
fault population, observations and physical service. This is a new recipe,
not a relabeling of the accepted relay design. Its package count, electrical
validity and diagnostic solvability have not been demonstrated here.

The independent read-only audit identified a possible 26-part dual-channel
low-side recipe with a shared direct reference and common-return main-supply
loads. It would preserve two sensor/decision paths but change the isolated
load-domain contract. The existing typed low-side role's 5-V-class/16-mA
envelope does not qualify the Q30 12-V/180-ohm loads. Even that proposal leaves
20–25 unexplained; it is not an implementation or evidence for the full band.

A larger recipe also needs an electrically justified subsystem and observable
effects. Adding passive parts solely to reach 40 would not satisfy the task.
No topology, identity, rejection, service or replay gates may be bypassed to
make either endpoint appear supported. Normal publication remains disabled.

One proposed 40-part recipe is the 37-part board plus two series output-current
shunts and a shared isolated-load fuse. This proposal has potential observable
electrical effects, but needs loaded-voltage, overload/trip, probe mapping and
service proof. It has not been constructed, routed or qualified. The relevant
implementation owners are the family plan, generator, topology validator,
power domains, behavior and diagnostic provider. Generic request/coordinator
code must remain untouched. Recipe/epoch and independent count/replay/rejection
oracles would need coordinated updates; relaxing the medium admission's
20–40 eligibility bound supplies none of that evidence.
