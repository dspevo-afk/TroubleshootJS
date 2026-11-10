# Independent retained codec count ledger

The unchanged 32048-byte retained canary contains eight test families, not eight
cases. Root reviewed its case/assertion increments and helper calls. Family
totals in main order are:

| Family | Cases | Assertions |
| --- | ---: | ---: |
| Small literal | 1 | 5 |
| Wide count/write/compare | 1 | 13 |
| Real count byte cap | 1 | 6 |
| Headers/containers | 19 | 19 |
| Ordinary domain | 12 | 12 |
| Depth boundary | 2 | 4 |
| Visit boundary | 2 | 4 |
| Marker oracle/bounds | 25 | 31 |
| Total | 63 | 94 |

Headers have12 fixed rejection calls,6 root-container iterations and1 arm-list
case. Ordinary domain has1 positive and11 rejection cases. Marker has2 positive
cases,7 below-bound and6 above-bound cases (Long.MAX_VALUE has no successor),7
explicit null/header/digest cases,1 short-output and2 invalid-target cases.
The visit fixture helper independently asserts its exact count twice.

The R1 launch failed before assertions because the class was absent. R2 compiled
the three exact retained sources and the Java suite returned0 with63/94; its
private summary reader then failed by requiring cases=8. Both failures remain
preserved. R3 fixes the reader expectation using this Source ledger, freezes the
already compiled classes, and reruns only that focused native suite. No retained
test, literal, production Source, budget or exception assertion is weakened.
