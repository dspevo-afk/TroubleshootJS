# Passive sizing-control implementation notes

Schema: `pcb-passive-sizing-control-v1`
Candidate input: exact policy1504 copy prepared from accepted1467 plus the 49 sealed policy deltas.
Base source: `dbb2ea4281c3b7e9338d112dd0ef42e794105743`.

## Scope and static review

Added passive pose policy2 and explicit policy selection for existing planner visits. Policy2 derives the same TOP90 sizing alternative from the selected TOP0 package geometry as policy1, while ring and fallback selection return the original TOP0 footprint. The sizing gate remains limited to the existing repeated-control, medium, nonanchor candidate1 branch. The old three-argument visit selector has no compatibility overload; the only current planner callers in the copy pass the policy explicitly.

Placement17 maps to density14, role widths, two-layer routing, and pose policy2. `RB56_BOARD_NORMAL@3` keeps the 40..60 package and 2..7 terminal bounds and permits the same canonical passive TOP0/TOP90 geometry as @2. Its canonical policy field identifies policy2 as sizing-only with TOP0 visits. `GenerationRequest` rejects placement17 in both private request construction paths; private16 remains valid. The existing dependency canonicalization already adds a field for nonlegacy pose policies, so policy2 yields its own `placement.passive-pose-policy=2` field without changing older fields.

Static review found the `forVisit` production callers only in the planner’s ring and fallback searches. Default/current15, private16, generic-medium policy0, normal-family @1, and the existing @1/@2 admission canonical branches remain unchanged in the edited source. No files outside the assigned copy sources/tests and this packet note were edited.

## Owned-file SHA-256

Hashes identify the resulting assigned copy files, including assigned files left unchanged.

| File | SHA-256 |
|---|---|
| `src/com/lushprojects/circuitjs1/client/PcbPassivePosePolicy.java` | `8d3ee85bcda953a0567cbc4a11bcaf1d2102aa7780f543c91399cac52e9fe827` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java` | `f86cccf807da61af8aa909b47130db1c3be1bdab8e9376b5af8665a846c07716` |
| `src/com/lushprojects/circuitjs1/client/Rb56Placement.java` | `e3b6343039f535b9d1018685a4c18c12655f0b337b7b5a4ff7e47808d1782039` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java` | `340bb31fb61da254794d31c71ab9d4771b659a8666c9cbce6153d8e8f2ee065e` |
| `src/com/lushprojects/circuitjs1/client/MediumBoardNormalAdmission.java` | `9d20f67ad7cef4960fd8f8382c96958205a0d6b4ade847868f96f0125a753317` |
| `src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java` | `e77b23c1278ea74917bf925accd34d0a7377ed019dce61371f15c2c7d27dff50` |
| `src/com/lushprojects/circuitjs1/client/GenerationRequest.java` | `486eb94e303fa5a5cb31515e362b6674a1ffd4d44743ce9dc44c6b55dfd435b2` |
| `src/com/lushprojects/circuitjs1/client/GenerationDependencyContext.java` | `b8def340baae6eeeabe456b8efd7594ba9d5a50e3aca1aa22d8d89e79a9b2e6a` |
| `src/com/lushprojects/circuitjs1/client/Rb56Generator.java` | `6e3d2247bba5700ded757abe1e9b423b3948d67c461bc170e73bbc455bc3d6d0` |
| `src/com/lushprojects/circuitjs1/client/Rb56PlayerFamilyCapability.java` | `00a1b9927ca1a5bbace1413b0216dc03b6a76f2dd56b553fa7c7a698d604bcff` |
| `src/com/lushprojects/circuitjs1/client/Rb56Plan.java` | `a2a89ab38adde72985550da8af623c8bd4b7b2fbd19ef86db1876388d3d4bd8a` |
| `tests/contracts/PcbPassivePosePolicyContractTest.java` | `a5c8e68543e82472221f15e13e1a4df201c7298a1d8f77cc779f33470dd12f36` |
| `tests/contracts/PcbPassivePoseAdmissionContractTest.java` | `3ababdf5e316cfaae2c3a153d78cdb1b2e4722f3e59632acc23ef0d59f6d39ba` |

Line endings match each assigned file’s input form; the changed files have no trailing-whitespace lines.

## Limits and handoff

The policy tests were extended for all seven finite geometries, literal radial envelope asymmetry, policy1’s unchanged alternating visits, policy2 TOP0 selection through ordinals0..125 and representative fallback boundaries, immutable source geometry, incompatible contexts, @3 admission identity and pose limits, dependency namespace, and placement17 request rejection. Implementation remains in the designated isolated copy and is not integrated into the main source tree. These checks were not executed here. Java compilation/contracts, native gates, source/raw/route readers, and runtime output validation remain NOT RUN; root owns the freeze and required gates. No acceptance, route, or runtime claim is made.
