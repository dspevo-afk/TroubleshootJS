# Q30 two-layer component-count scaling: structural investigation

**Result:** A connected synthetic repeated-channel graph routes with the unchanged
P07 `FULLER_TWO_LAYER` prototype at every tested size from 20 through 80 in
the initial three seeds, and at 80 in all ten expanded seeds. At 90 it routes
9/10, and its outcomes are nonmonotonic thereafter. The largest single routed
board in this bounded sample is **180 parts (2/3 seeds)**, the upper test bound;
the global maximum is **unmeasured**. `RESTRICTED_TWO_LAYER` routes 0/61
requests at the sizes where it was tested. This is a developer-only structural
capability result, not Q30 or normal-player qualification.

## Input and method

Accepted source object: `e0c368855a3891acd4673e94ce9afa732390e2bf`.
The archive was extracted into a unique task-owned OS-temp directory. The
ordinary checkout and Q30 source worktree were read only. No production code,
P09 policy, factory-link behavior, router cost or router budget was changed.

The exact synthetic graph is in `Q30TwoLayerScaling.java`. Five core packages
represent the input connector, fuse, four-terminal regulator and input/output
capacitors. Each seven-package channel has a sensor connector, reference
resistor, five-terminal controller, three-terminal driver, five-terminal relay,
output connector and coil diode. Shared `VIN`, `VRAW`, `V5` and `GND` nets join
the power core to all channels; each channel has its own `SNS`, `REF`, `DRV`,
`COIL` and `OUT` nets. Remainder packages at intermediate counts are connected
reference shunts or output loads to return. A component/net incidence traversal
requires one connected graph and no singleton nets. It is not a complete graph
or several independent boards placed together. The package shapes are
synthetic but physically plated through-hole, with 2-, 3-, 4- and 5-pin mixes.

The floorplan has a power row and one row per channel. The fixed 2300-unit
outline width and 240-unit channel-row pitch make height and area grow with
channel count; each seed deterministically moves each package by at most 10
units in X and Y. Intermediate sizes differ in auxiliary count, and adding a
channel changes topology and outline height. A 95-part failure followed by
100-part successes therefore does not imply that adding parts improves a fixed
board. There is one placement per size/seed; no placement search or cherry-picked
retry. This floorplan is intentionally more regular than the exact Q30 pilot.

Each P07 policy uses the accepted 10-unit grid, three ordering attempts,
100,000 expansions per branch, 1,000,000 total expansions, original trace and
via clearance, route-quality checks, and its own 8- or 48-via cap. A separate
JVM had a 15-second wall bound per request; none timed out. A `SUCCESS` counts
only if the unchanged input fingerprint survives, full `validateGeometry`
passes, `PcbTwoLayerRules` passes, the pristine conductor graph proves every
net connected, and both copper faces plus plated vias have nonzero geometry.
Full geometry needs reference labels, which this harness places in an unused
outline margin after routing; this does not qualify product markings. Layout
validation checks shorts, trace/pad/via clearance, package courtyards and route
quality. There is no solver-backed electrical, diagnostic, service or player
proof. Top/bottom length is drawn centerline length, not deduplicated copper.

## Synthetic results and interpretation

There are **156 raw requests**: 95 fuller (72 successes) and 61 restricted
(zero successes). Every failure has a bounded router outcome; there are no
timeouts, exceptions, invalid accepted boards or input mutations. The
highest tested size with a ten-seed sweep and ten successes is **80**. At 90
one of ten fails, 95 has zero of seven, and 100 has five of ten. Above 100,
several sizes still produce individual successes, including 160 and 180.
Thus 80 is a fully repeatable *sampled fixture size*, not a universal practical
limit. At 80 the successful median and maximum via counts are both 48, and the
p95 expansion count is 974,119. A 48-via route at the policy cap does not
prove the cap is the sole cause of subsequent failures; `NO_PATH`, branch
search/quality limits and the total search limit are recorded separately.

Every tested outline exceeds the P09 normal envelope: even the 20-part board
has 2,438,000 area units and 2300-unit width, above the 2,250,000-area and
2048-edge limits, and P09 admits at most 16 packages and no vias. The 180-part
board spans 15,134,000 area units, 537 pads and degree-129 return. Large
successes demonstrate the P07 structural router's bounded capability under
this fixture; they are not plausible current normal-play layouts.

Percentiles below use nearest rank over **all attempts**, including failures:
`sorted[ceil(q*n)-1]`. Successful-route geometry columns use medians across
successful rows only. `R` is restricted; `F` is fuller. `n/a` means a policy
was not run or no route succeeded. All sizes and failure distributions remain
machine-readable in `summary.json`; all individual outcomes, seed, package
mix, complete net-degree list, face lengths/segments, vias, expansions, setup,
route and validation times are in `results.jsonl` and the eight original raw
batch receipts.

### Population, outcomes and work

| Parts | Channels + aux | Area / grid cells | Pads / nets / max degree | R passes; expansions p50 / p95 | F passes; expansions p50 / p95 | F route ms p50 / p95 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20 | 2 + 1 | 2,438,000 / 24,045 | 56 / 14 / 15 | 0/3; 186,460 / 289,729 | 3/3; 122,238 / 134,377 | 293 / 389 |
| 25 | 2 + 6 | 2,438,000 / 24,045 | 66 / 14 / 20 | 0/3; 216,777 / 221,619 | 3/3; 130,037 / 135,177 | 291 / 301 |
| 30 | 3 + 4 | 2,990,000 / 29,541 | 83 / 19 / 23 | 0/3; 153,365 / 241,373 | 3/3; 162,309 / 168,911 | 413 / 416 |
| 33 | 4 + 0 | 3,542,000 / 35,037 | 96 / 24 / 24 | 0/10; 94,225 / 194,925 | 10/10; 117,792 / 438,245 | 361 / 931 |
| 35 | 4 + 2 | 3,542,000 / 35,037 | 100 / 24 / 26 | 0/3; 141,057 / 143,627 | 3/3; 152,604 / 174,090 | 394 / 486 |
| 40 | 5 + 0 | 4,094,000 / 40,533 | 117 / 29 / 29 | 0/3; 107,768 / 170,033 | 3/3; 143,106 / 475,778 | 495 / 1,100 |
| 45 | 5 + 5 | 4,094,000 / 40,533 | 127 / 29 / 34 | 0/3; 54,536 / 59,225 | 3/3; 182,501 / 203,514 | 580 / 606 |
| 50 | 6 + 3 | 4,646,000 / 46,029 | 144 / 34 / 37 | 0/3; 68,326 / 86,084 | 3/3; 182,522 / 197,749 | 654 / 669 |
| 56 | 7 + 2 | 5,198,000 / 51,525 | 163 / 39 / 41 | 0/3; 185,635 / 205,738 | 3/3; 187,210 / 203,170 | 831 / 839 |
| 60 | 7 + 6 | 5,198,000 / 51,525 | 171 / 39 / 45 | 0/3; 130,579 / 192,073 | 3/3; 214,310 / 227,779 | 856 / 943 |
| 70 | 9 + 2 | 6,302,000 / 62,517 | 205 / 49 / 51 | 0/3; 160,851 / 352,336 | 3/3; 208,076 / 831,134 | 877 / 3,534 |
| 80 | 10 + 5 | 6,854,000 / 68,013 | 232 / 54 / 59 | 0/3; 254,899 / 351,952 | 10/10; 567,153 / 974,119 | 2,656 / 5,229 |
| 90 | 12 + 1 | 7,958,000 / 79,005 | 266 / 64 / 65 | 0/3; 292,974 / 302,807 | 9/10; 609,029 / 790,684 | 2,865 / 3,434 |
| 95 | 12 + 6 | 7,958,000 / 79,005 | 276 / 64 / 70 | not run | 0/7; 460,673 / 746,751 | 2,461 / 4,560 |
| 100 | 13 + 4 | 8,510,000 / 84,501 | 293 / 69 / 73 | 0/3; 313,512 / 314,519 | 5/10; 647,522 / 1,000,000 | 2,989 / 6,218 |
| 105 | 14 + 2 | 9,062,000 / 89,997 | 310 / 74 / 76 | 0/3; 317,948 / 318,418 | 1/3; 562,909 / 596,423 | 2,822 / 3,054 |
| 110 | 15 + 0 | 9,614,000 / 95,493 | 327 / 79 / 79 | 0/3; 298,348 / 299,625 | 2/3; 558,948 / 567,634 | 2,789 / 3,479 |
| 120 | 16 + 3 | 10,166,000 / 100,989 | 354 / 84 / 87 | 0/3; 318,529 / 318,563 | 2/3; 673,520 / 742,705 | 4,304 / 4,395 |
| 140 | 19 + 2 | 11,822,000 / 117,477 | 415 / 99 / 101 | 0/3; 325,081 / 327,519 | 0/3; 733,266 / 739,327 | 4,718 / 5,623 |
| 160 | 22 + 1 | 13,478,000 / 133,965 | 476 / 114 / 115 | not run | 1/3; 979,355 / 1,000,000 | 6,203 / 6,289 |
| 180 | 25 + 0 | 15,134,000 / 150,453 | 537 / 129 / 129 | not run | 2/3; 840,509 / 879,791 | 6,918 / 7,766 |

### Valid fuller-route geometry

| Parts | Fuller passes | Vias median / max | Top / bottom segments median | Top / bottom length median | Typical failure when present |
| ---: | ---: | ---: | ---: | ---: | --- |
| 20 | 3/3 | 15 / 19 | 46 / 69 | 6,500 / 7,150 | none |
| 25 | 3/3 | 25 / 26 | 51 / 82 | 8,910 / 8,980 | none |
| 30 | 3/3 | 25 / 26 | 77 / 96 | 11,930 / 9,550 | none |
| 33 | 10/10 | 27 / 33 | 66.5 / 116.5 | 8,015 / 12,120 | none |
| 35 | 3/3 | 28 / 35 | 81 / 114 | 11,790 / 11,130 | none |
| 40 | 3/3 | 35 / 38 | 86 / 141 | 9,900 / 14,590 | none |
| 45 | 3/3 | 29 / 36 | 109 / 146 | 16,920 / 14,440 | none |
| 50 | 3/3 | 34 / 42 | 111 / 169 | 16,050 / 16,690 | none |
| 56 | 3/3 | 39 / 45 | 116 / 197 | 16,070 / 19,360 | none |
| 60 | 3/3 | 36 / 42 | 134 / 202 | 20,780 / 20,240 | none |
| 70 | 3/3 | 42 / 46 | 142 / 246 | 18,660 / 25,200 | none |
| 80 | 10/10 | 48 / 48 | 158.5 / 292.5 | 22,365 / 30,670 | none |
| 90 | 9/10 | 48 / 48 | 194 / 268 | 26,760 / 29,190 | BRANCH_SEARCH_LIMIT (1) |
| 95 | 0/7 | n/a / n/a | n/a / n/a | n/a / n/a | BRANCH_SEARCH_LIMIT (7) |
| 100 | 5/10 | 48 / 48 | 201 / 317 | 30,960 / 35,280 | BRANCH_SEARCH_LIMIT (3), NO_PATH (1), TOTAL_SEARCH_LIMIT (1) |
| 105 | 1/3 | 48 / 48 | 232 / 324 | 31,220 / 35,330 | BRANCH_QUALITY_LIMIT (1), NO_PATH (1) |
| 110 | 2/3 | 48 / 48 | 240.5 / 332 | 31,260 / 37,265 | BRANCH_QUALITY_LIMIT (1) |
| 120 | 2/3 | 48 / 48 | 239.5 / 393 | 32,880 / 46,970 | NO_PATH (1) |
| 140 | 0/3 | n/a / n/a | n/a / n/a | n/a / n/a | BRANCH_SEARCH_LIMIT (1), NO_PATH (2) |
| 160 | 1/3 | 48 / 48 | 324 / 529 | 38,250 / 65,540 | BRANCH_SEARCH_LIMIT (1), TOTAL_SEARCH_LIMIT (1) |
| 180 | 2/3 | 48 / 48 | 345.5 / 602.5 | 39,060 / 77,320 | BRANCH_SEARCH_LIMIT (1) |

## Exact Q30 control

This is the pre-P1 control; later generic medium floorplanning raised fuller
routing on the actual Q30 candidate to 54/72. The P1
[review repair](../../p1-review-repair/README.md) is awaiting independent
acceptance. The concurrent matched Q30 investigation used the **root-plan-equivalent
33-package pilot**, 82 pads, 24/25 nets, degree 15/16 and 72 placement
candidates over 12 seeds. With unchanged P07 policies, restricted succeeds
0/72 and fuller succeeds **6/72 on five seeds**, using 36–46 vias. Its six
successful areas are 4,658,000–5,610,000 units. The synthetic 33-part input
here has 96 pads, 24 nets, degree 24, a 3,542,000-unit regular outline and
one placement for each of ten seeds; restricted succeeds 0/10 and fuller
10/10 with 18–33 vias. The package geometry, net incidence, region placement
and candidate population differ. These results show the router can handle
more components on some layouts, while the actual Q30 manifest and floorplan
remain a harder routing problem. Neither result admits Q30 to normal play.

## Reproduction and limits

`compile_scaling.ps1` compiles the exact accepted Java client and this harness
with JDK 8; it substitutes only the native-verifier stub used by the maintained
native contract runner. The source archive needs the existing GWT JARs copied
into `.tools/gwt-2.7.0`. For example, from a task-owned temp archive:

```powershell
$env:JDK8_HOME = '<JDK8_HOME>'
& '<EVIDENCE_DIR>\compile_scaling.ps1' -SourceRoot '<ACCEPTED_SOURCE_ARCHIVE>' -JavaHome $env:JDK8_HOME
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 20,25,30,33,35,40,45,50,56,60 --seeds 3,17,42 --output '<EVIDENCE_DIR>\sweep-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 70,80,90,100 --seeds 3,17,42 --output '<EVIDENCE_DIR>\extended-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 90,95,100 --seeds 0,1,11,23,37,59,83 --policies FULLER_TWO_LAYER --output '<EVIDENCE_DIR>\boundary-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 105,110,120 --seeds 3,17,42 --output '<EVIDENCE_DIR>\high-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 80 --seeds 0,1,11,23,37,59,83 --policies FULLER_TWO_LAYER --output '<EVIDENCE_DIR>\eighty-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 140 --seeds 3,17,42 --output '<EVIDENCE_DIR>\largest-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 160,180 --seeds 3,17,42 --policies FULLER_TWO_LAYER --output '<EVIDENCE_DIR>\upper-raw.txt'
python '<EVIDENCE_DIR>\drive_scaling.py' --source-root '<ACCEPTED_SOURCE_ARCHIVE>' --counts 33 --seeds 0,1,11,23,37,59,83 --output '<EVIDENCE_DIR>\thirtythree-raw.txt'
python '<EVIDENCE_DIR>\summarize_scaling.py'
```

The final portable compiler and one-row driver canary passed with JDK
`1.8.0_502`; the canary reproduced the 20-part seed-3 fuller route and
134,377 expansions. This native structural investigation did **not** run a
production GWT build, CircuitJS, a browser, P09 admission, diagnostic proofs
or player interactions. The accepted source archive, normal checkout and Q30
worktree were not changed by this worker. The raw batches were generated while
the harness maximum-count guard was extended from 60 to 140 to 180; that guard
is the only harness behavior changed between batches and does not affect any
accepted count. The delivered harness permits up to 180, an intentional
experiment bound rather than a claimed router maximum.
