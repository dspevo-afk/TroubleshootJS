# Medium placement cost worker note

Owned addition: `tests/contracts/PcbMediumPlacementCostContractTest.java` in the accepted COPY `[TASK_COPY]`. The single named fixture has 13 complete footprints and 14 board pads. It reuses `P06FactoryLinkFixtures.TEST_POINT` geometry with a declared (-1,0,30) terminal escape and fixed DEG_0/TOP pose; J1 is the stock `THROUGH_HOLE_CONNECTOR_2`. The fixture declares `ROLE_TRACE@1` with `requiresTwoLayerRouting=false` and no traces. Its source calls both `board.validate()` and complete `PcbPlacementPlanner.validate(...)`; these calls were not executed in this worker.

Independent literal expectations for the one fixture:

- Four SIGNAL pads A(100,100), B(500,100), C(100,500), D(500,500) make a 1,200 Manhattan MST (three 400 edges). Their six-pair sum is 3,200. Sorted Prim tie handling selects A-B, A-C, then B-D; X is a separate singleton SIGNAL net.
- Role-weighted span is SIGNAL 1,200×1 + SUPPLY 400×6 + RETURN 400×6 + HIGH_CURRENT 400×4 + CONTROL 400×2 = 8,400. SUPPLY connects J1.1(1000,700) to S(1400,700); RETURN, HIGH_CURRENT, and CONTROL each have two pads 400 apart.
- The P06 point package has a 40×20 routing courtyard. X's pad at (500,300) places its courtyard at [490,530)×[290,310); only the selected B-D corridor overlaps it, by 30×20=600. The existing max(1, area/100) term is therefore 6.
- Region-center bbox spans are CORE 800, POWER 600, and IO 600, totaling 2,000. Only J1.1 has a same-net nonconnector peer, at Manhattan distance 400; J1.2 is a singleton net.
- Outline area is 2,400×1,200=2,880,000; planner courtyard and demand areas are literal 123,456 and 789,012. Normalized area terms are 2,880, 123, and 789. Total is 8,400×8 + 2,000×4 + 400×6 + 6×12 + 2,880 + 123 + 789 = 81,464.

The unit also has typed null-board/null-plan rejection canaries, compares the adapter against the existing board-aware `PlacementCandidate` score, repeats scoring, reverses complete footprint input order, and fingerprints the board constraints/nets/pads and original plan around scoring. Static inspection confirmed the test calls the assigned `MediumBoardPhysicalPolicy.scorePlan(board, plan)` adapter and that the existing scorer contains the referenced MST, role, region, connector, corridor, and total terms.

Static source review only. Native contract execution, Java compilation, GWT build, and all runtime checks are **NOT RUN** by this worker per assignment. Fixture count: 1. Marker: `PASS: PCB medium placement cost contracts assertions=15 fixtures=1 evaluator=mediumWholePlanCost` (expected output only; not observed).

Final test source SHA-256: `164C07CD22300775370FDC56C1CC1E9F25F80CE61B794B86D5CA3E33C5DB17B0`.

Root pre-freeze integration review made the captured fixture variable final for the maintained Java7 source level, added explicit constraints.validate(board), and removed the personal COPY path from this note. Literal coordinates, geometry, all cost expectations, assertion count15 and named fixture count1 are unchanged. Native validation remains NOT RUN.
