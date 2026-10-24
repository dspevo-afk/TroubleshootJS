# Preparation-cost feature: NO_GO on current development

At published `fc88419b4e4dfe5f58cede8e23ce9a9cfab388f4`, all three R9 development
captures admit this retrospective adaptive tree:

1. Powered DC: `DRIVER_A_COMMAND`, `RDA.1` against `UAC.OUT-`.
2. Its high branch contains opaque repair classes0/1; read `DRIVER_A_INPUT`,
   `RDA.2` against `UAC.OUT-`. Its low branch contains classes2/3/4; read
   `REGULATED_5V`, `U1.OUTPUT` against `UAC.OUT-`.

Each second reading distinguishes the remaining repair classes under the actual
exported comparison tolerances. The provider/plan/power-domain/reference-policy
source audit maps all three to the same `CTRL_RETURN` / `SECONDARY` differential
reference group. The later OFF/Ω/decay steps are unnecessary to this tree; their
witness length cannot establish unavoidable diagnostic cost.

The independent [literal-tree reader](read_counterexample.py) pins all three raw
capture hashes, build/source bindings and the four relevant source files. Actual
`python -B docs/task-evidence/U05/preparation-cost-scope-r1/read_counterexample.py`
returns0, proving the three explicit trees and rejecting15 synthetic negative
canaries (collapsed second-reading distinctions, broken first branches, altered
repair census and tolerance). [Actual result](counterexample.json).

This proves a two-reading witness within the certified observations. It does not
prove an executed player-adaptive order, arbitrary tool/probe optimality, minimum
input changes or human difficulty. The captures contain the HIGH condition only;
the program's initial LOW→HIGH setup is not a measured global minimum. Existing
MEDIUM also uses LOW/HIGH conditions. No additional feature or HARD label follows.
Protected regression was not read, and no production source or public gate changed.

The product direction is pending: qualify the existing content in MEDIUM while
HARD remains unavailable, or keep it gated while developing harder scenarios.
This asks for a content/release preference, not another play test. Independent
public replay admission hardening can proceed in either direction.
