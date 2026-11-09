# Independent canary assertion census R3

Root reconciliation of the final A0B04875 canary Source after the reader's
11765 expectation failed. Preserve that reader failure and its prior manual
ledger. No Java Source, assertion, debit, cap or native result is changed.
This census follows actual `need()` calls, including nested helper invocations;
it does not use the subject Meter or native output as its counting algorithm.

| Executed Source path | Assertions |
| --- | ---: |
| main argument/ledger/hash controls | 7 |
| positive result controls: 22 direct plus 14 typed Meter helper guards | 36 each |
| positive/adversarial factory: forbid guard1, pre-dataJson controls4, typed helper guards4 | 9 each |
| independent projection oracle, below | 5580 each |
| fresh trees: root inequality1, two 16-container collections32, freshness16 | 49 |
| header rejection paths, including helper guards | 39 |
| 62 body rejections: factory9, exact failure code1, header calls1, tree calls1 | 744 |

Two positive reads therefore yield
`7 + 2*(36+9+5580) + 49 + 39 + 744 = 12089` assertions. Cases remain
`2 + 17 + 10*3 + 32 = 81`.

## Independent projection oracle

The generic ASCII tree contributes4644: 256 non-key nodes times three basic
guards=768; two extra container guards times16=32; key guards/length/visit
checks69*3 plus913 key characters=1120; 221 scalar-string length guards plus
2484 characters=2705; 19 Integer/Boolean type guards=19. Total4644.

The rest follows literal helper invocations, with ordered-key checks n+2,
checkedMap n+3, value1, text/integer/bool3, checkedList3, checkNetList181,
checkTrial216 plus its caller's value/checkedMap12, and an explicit trial join70.

| Oracle section | Assertions |
| --- | ---: |
| Root order7 + ASCII tree4644 + three inventory controls | 4654 |
| prior map14 + parent4 + six digests18 + UUID4 | 40 |
| receipt map9 + path3 + bytes3 + digest3 + binding2 + ordinals6 | 26 |
| prior trial | 228 |
| capture map13 + cohort4 + captured list184 + capture trial228 | 429 |
| two join map/value pairs4 + trial joins70 | 74 |
| control/outcome6 + pass3 + failed3 + promoted3 + index3 + two bindings2 | 20 |
| owner map14 + board3 + numeric15 + strategies6 + flags6 | 44 |
| seeds map7 + three values9 | 16 |
| identity map12 + hashes15 + counts6 | 33 |
| PAD map7 + format3 + rows3 + digest3 | 16 |
| Total | 5580 |

The prior manual census missed14 typed Meter guards per positive, four typed
guards per factory spy, sixteen final fresh-container guards, and twelve oracle
guards per positive. Difference is `2*14 + 64*4 + 16 + 2*12 = 324`.
`11765 + 324 = 12089` confirms the independent recomputation.

The Java suite exited0, but R2 evidence acceptance remains FAIL until a separate
strict retained-evidence audit checks this Source-derived expectation and all
unchanged input/class/process pins. No native rerun is needed for this reader-only
correction. Genuine one-original then NULL/OBSERVE remains UNSPENT_GATED.
