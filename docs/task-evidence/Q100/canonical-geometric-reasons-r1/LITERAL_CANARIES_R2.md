# Frozen P8 literal cases R2

Frozen before Java literal-canary implementation or execution. Root reviewed the
corrected source-grounded fixture draft and original maintained sources. Earlier
draft/its errors remain preserved. This finite set is independent of any future
canonical P8 reason distribution. API_SCHEMA_R1/CANARY_OUTPUT_R1 control fields.

All Router cases use actual permitsLayerStep on disposable, compared views,
original predicates/order, known endpoints and real constructed pad reservations.
Do not call predicates posthoc for explanations. Use a separate equal null-sink
query as return/counter/effect parity control where the call completes. Source,
pristine, geometry/effects, sink detachment and callback-reference drop are proved
before reporting each case. No occupyLayerPath in geometric cache fixtures.
Congestion/work-conflict deltas are0 for all these geometric literals. Direct
rules cases use per-call sinks and prove no stored sink/source mutation.

## Exact case ledger

Columns are id,scope,expectedSite,actualSites,fineEvents,coarseEvents. A dash in
expectedSite means JSON null; [] is an empty list. Preserve this exact order.
Scopes R=SYNTHETIC_ROUTER_LITERAL, I=INVALID_RULES_INPUT,
D=RULES_BOUNDARY_LITERAL, L=OBSERVER_LIFECYCLE.

| id | scope | expectedSite | actualSites | fine | coarse |
| --- | --- | --- | --- | --- | --- |
| START_ESCAPE_REJECT | R | START_ESCAPE_DIRECTION | [START_ESCAPE_DIRECTION] | 1 | 1 |
| START_ESCAPE_ALLOW | R | - | [] | 0 | 1 |
| END_ESCAPE_REJECT | R | END_ESCAPE_DIRECTION | [END_ESCAPE_DIRECTION] | 1 | 1 |
| END_ESCAPE_ALLOW | R | - | [] | 0 | 1 |
| EMPTY_UNRESERVED | R | - | [] | 0 | 1 |
| EMPTY_SAME_OWNER | R | - | [] | 0 | 1 |
| EMPTY_SINGLE_OWNER | R | EMPTY_FACE_RESERVATION | [EMPTY_FACE_RESERVATION] | 1 | 1 |
| EMPTY_MIXED_OWNER | R | EMPTY_FACE_RESERVATION | [EMPTY_FACE_RESERVATION] | 1 | 1 |
| ADJACENT_SAME_OWNER | R | - | [] | 0 | 1 |
| ADJACENT_SINGLE_OWNER | R | ADJACENT_RESERVATION | [ADJACENT_RESERVATION] | 1 | 1 |
| ADJACENT_MIXED_OWNER | R | ADJACENT_RESERVATION | [ADJACENT_RESERVATION] | 1 | 1 |
| RULES_UNKNOWN_NET | I | DOMAIN_UNKNOWN_NET | [DOMAIN_UNKNOWN_NET] | 1 | 0 |
| RULES_CORRIDOR | D | DOMAIN_CORRIDOR_OVERLAP | [DOMAIN_CORRIDOR_OVERLAP] | 1 | 0 |
| RULES_MEMBERSHIP | D | DOMAIN_MEMBERSHIP_SIDE | [DOMAIN_MEMBERSHIP_SIDE] | 1 | 0 |
| ROUTER_CORRIDOR | R | DOMAIN_CORRIDOR_OVERLAP | [DOMAIN_CORRIDOR_OVERLAP] | 1 | 1 |
| ROUTER_MEMBERSHIP | R | DOMAIN_MEMBERSHIP_SIDE | [DOMAIN_MEMBERSHIP_SIDE] | 1 | 1 |
| ROUTER_DOMAIN_ALLOW | R | - | [] | 0 | 1 |
| PAD_ESCAPE_REJECT | R | PAD_ESCAPE_INTERSECTION | [PAD_ESCAPE_INTERSECTION] | 1 | 1 |
| PAD_ESCAPE_ALLOW | R | - | [] | 0 | 1 |
| PROFILED_TOUCH_REJECT | R | PROFILED_PAD_TOUCH | [PROFILED_PAD_TOUCH] | 1 | 1 |
| PROFILED_TOUCH_ALLOW | R | - | [] | 0 | 1 |
| COURTYARD_REJECT | R | COMPONENT_COURTYARD | [COMPONENT_COURTYARD] | 1 | 1 |
| COURTYARD_ALLOW | R | - | [] | 0 | 1 |
| UNDERPASS_ALLOW | R | - | [] | 0 | 1 |
| UNDERPASS_DISABLED | R | COMPONENT_COURTYARD | [COMPONENT_COURTYARD] | 1 | 1 |
| EMPTY_REACHED_ALLOW | R | - | [] | 0 | 1 |
| MULTI_ENDPOINT_ORDER | R | END_ESCAPE_DIRECTION | [END_ESCAPE_DIRECTION,END_ESCAPE_DIRECTION] | 2 | 3 |
| REASON_THROW | L | START_ESCAPE_DIRECTION | [START_ESCAPE_DIRECTION] | 1 | 0 |
| ATTACHMENT_ROLES | L | - | [] | 0 | 0 |
| COPY_DEFAULT_NULL | L | - | [] | 0 | 0 |
| NULL_PARITY | L | - | [] | 0 | 1 |

No missing/extra/reordered case can qualify. A failure stays a failure; preserve
the receipt/source and diagnose against this independent literal. Do not update
the ledger to bless actual unexpected output. Source-based corrections need a
new explicit addendum and fresh affected final-source runtime proof.

## Fixed fixtures and owner expectations

START uses P5 CanariesFixture(false,true): Q0=(200,250), QUERY,(+1,0,30),
Q1=(450,350). Reject220,250->210,250; allow220,250->230,250. Fine owner Q0.1.
END uses a simple manual top PTH fixture, Q0=(200,250),escape0 and
Q1=(310,250),(+1,0,30), both QUERY, with NO duplicate pad at Q1. Reject
310,250->320,250; allow320,250->310,250. Fine owner Q1.1. Use P5 pointPackage
and same manual8x8land/12x12probe construction. All layouts keep the P5
outline100,100,400,400 and board800x600/panel550,120,180,300 unless stated.

CACHE uses Q0=(200,260), Q1=(400,260), QUERY, zero escapes. Probe
290,260->300,260. Empty control adds no nearby pad. Same-owner adds QUERY pad
at300,250; single adds A0.1/A at300,250; mixed adds A0.1/A at300,250 and
AB0.1/AB at300,270. Manual lands8x8/probes12x12, default width9. Margin14
produces intersecting zero-escape rectangles286,236,28,28 and286,256,28,28.
Assert the actual selected horizontal edge cache is null/QUERY/A/empty-string
after preparation, respectively; target occupiedNet/clearanceNet stay null.
Single reports SINGLE/valueA/ownerNetA; mixed MIXED/value empty/ownerNetnull,
ownerIdnull/index-1. ADJACENT variants add a real C0/COURT pointPackage footprint
with origin350,350, away from the edge, disabling empty-face fast path. The
cache rejects before component scan. No pad/trace contributor is invented.

DOMAIN recreates maintained P07TwoLayerContractTest.barriers(), using its
P06FactoryLinkFixtures.TEST_POINT footprints centered170,270(PRIMARY) and
530,630(SECONDARY), y200, on BOTTOM, exact original addPoint transform.
Domain courtyard unions160..300 and520..660 give ordered PRIMARY/SECONDARY
horizontal boundary index0/limits370,450. The board1100x650 has
outline100,100,600,400. Direct rules rectangles: unknown UNDECLARED at400,300,
10,10; PRIMARY corridor400,300,10,10; PRIMARY membership530,300,10,10.
Unknown is invalid rules input only, since Router traceWidth rejects unknown
nets first. Rules callbacks have no coarse observer. Router TOP with P1.1/P2.1
PRIMARY endpoints: corridor360,300->370,300 yields actual stroke356,296,19,9
and domainFirst356/Last375; membership520,300->530,300 yields516,296,19,9
and516/535; allow310,300->320,300 remains PRIMARY. No nested OR operand cause
is asserted. Domain callback fields follow the exact boundary or unknown sentinel.

PAD_ESCAPE uses unchanged P5 CanariesFixture(true,true), FOREIGN endpoints
F0.1/F1.1, nonadjacent220,250->240,250. The QUERY/Q0.1 expanded escape
reservation is186,236,58,28; first offending pad is Q0.1/QUERY at its actual
array index. Positive250,250->270,250 is beyond244 and before C0's reservation
beginning276/courtyard280. The actual same-face C0 disables the legacy empty
fast path; do not force private branch flags to reach this site.

PROFILED_TOUCH reuses the exact maintained nonadjacent role-width construction:
HIGH/H1.1=(200,220), H2.1=(300,220), ROLE_WIDTHS13; SIGNAL/S.1 at260,240;
manual26x26lands. Query220,220->240,220 has stroke214,214,33,13, touches
foreign land starting247,227 at its closed corner while avoiding its escape
reservation. Positive S.1=(261,241) has a one-unit gap. Expected owner S.1/
SIGNAL/actual pad index. Keep the maintained PTH/profile/constraints constructors.

COURTYARD uses P5 CanariesFixture(true,false), QUERY/Q0.1/Q1.1, C0 placement
origin280,240 with courtyard280,240,40,20 and no endpoint escapes. Reject
nonadjacent310,250->330,250 after its reservation276..304; owner C0 and
existing startEscape/endEscape both false. Allow330,250->350,250. UNDERPASS
uses unchanged maintained PcbRoutingCourtyardIndexContractTest.Fixture(UNIFORM),
TOP/HIGH/highStart/highEnd, query-400,-180->-390,-180. True factoryUnderpasses
allows; setting only the disposable fixture's flag false rejects at component
RAISED_TOP with existing escape locals false and underpassEnabled0. Assert edge
reservation null and original footprint geometry preserved; do not rerun the
underpass predicate to explain the rejection.

EMPTY_REACHED uses P5 CanariesFixture(false,false), QUERY root Q1.1, edge
290,250->300,250 and exact new PcbPadPlacement[0]. MULTI reuses unchanged P6
multiEndpointFixture(): Q1.1,Q2.1 reject END_ESCAPE_DIRECTION in order, then
Q3.1 ALLOWED and stop before Q4.1; each fine event precedes matching coarse.
Do not use the duplicate-root MULTI fixture for the distinct END_ALLOW target.

REASON_THROW uses START's rejecting query, records first fine event, throws exact
new sentinel FIRST_GEOMETRIC_REASON_CALLBACK before coarse, retains throwable
identity, then detaches/drops/proves source cleanup before accepting. ATTACHMENT
rejects null, aliased, wrong-receiver, failed comparison and duplicate attach;
valid fine+coarse coexist. COPY proves copies of attached query retain neither
sink and default live/pristine are null. NULL_PARITY uses START's rejecting
query with fine sink absent and coarse sink present; compare normal result/
counters/effects to separately observed query, with no fine callback expected.

These are native observer/owner/lifecycle literals, not captured candidate
hardware or new route evidence. All work/clone/group/output limits remain.
Exact canonical14guard distribution remains NOT RUN. Protected HARD/PSYCHOTIC/
full30 excluded and unread/unrun here; historical unread boundary UNCERTIFIED.
