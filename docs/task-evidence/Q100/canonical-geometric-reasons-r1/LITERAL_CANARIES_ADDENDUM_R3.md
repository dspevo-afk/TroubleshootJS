# Source-derived BOTTOM domain oracle correction

Frozen before correcting/rerunning the Java canary. This supersedes ONLY the
domain courtyard-union/limit operands in LITERAL_CANARIES_R2.md; preserve that
original document, failed D62 source and actual R6 failure. All31case IDs/order/
scopes/sites/event counts, query coordinates and every cap remain unchanged.

Actual R6 final-source JDK8 compile passes1081sources; old P6 native passes849
maintained plus73adapter assertions. New P8 fails the direct RULES_CORRIDOR
operand assertion. Root did not bless the observed payload or shift queries.
Two read-only source audits independently found the frozen literal used TOP
geometry while BOTH maintained and P8 fixtures use BOTTOM/DEG_0 placement.

Source derivation: P06FactoryLinkFixtures.TEST_POINT has width40, pad center
(10,10), routing courtyard(0,0,40,20). The maintained P07 addPoint and P8
addDomainPoint use the same bottom reference-pad alignment. PcbPackagePose /
PcbPhysicalTransform mirrors local X on BOTTOM: board padX=poseX+40-10.
Thus pose origin=(targetX-30,targetY-10), and the routing courtyard is
[targetX-30,targetX+10] x [targetY-10,targetY+10]. No isolation body is present;
PcbPlacementConstraints.domainCourtyards returns that routing courtyard.

For PRIMARY centers170/270,y200, union=Rect(140,190,140,20), X140..280.
For SECONDARY centers530/630,y200, union=Rect(500,190,140,20), X500..640.
The original PcbTwoLayerRules.add uses a280,b500,gap80: middle390,
lowLimit350,highLimit430. The old160..300/520..660 and370/450 are the
TOP calculation and are incorrect for these unchanged BOTTOM fixtures.

| Case | site | boundary/labels/axis | low/high | first/last |
| --- | --- | --- | --- | --- |
| RULES_CORRIDOR | DOMAIN_CORRIDOR_OVERLAP | 0,PRIMARY,SECONDARY,horizontal | 350/430 | 400/410 |
| RULES_MEMBERSHIP | DOMAIN_MEMBERSHIP_SIDE | same | 350/430 | 530/540 |
| ROUTER_CORRIDOR | DOMAIN_CORRIDOR_OVERLAP | same | 350/430 | 356/375 |
| ROUTER_MEMBERSHIP | DOMAIN_MEMBERSHIP_SIDE | same | 350/430 | 516/535 |

Direct rectangles stay400,300,10,10 and530,300,10,10. Router steps stay
360,300->370,300 and520,300->530,300; width9 strokes remain356,296,19,9
and516,296,19,9. The positive310,300->320,300 remains left of350 and valid.
Unknown-domain sentinel/index fields do not change. No model/transform/routing
predicate implementation changes; this is an independently justified test-oracle
correction. Fresh affected final-source JDK8/native/source/consumer gates remain
required. It does not explain the canonical14 queries or qualify playable100.
