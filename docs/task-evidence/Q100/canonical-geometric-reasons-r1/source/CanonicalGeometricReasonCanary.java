package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/** Literal native checks for the P8 first geometric guard reason observer. */
public final class CanonicalGeometricReasonCanary {
    private static final String PREFIX = "P8_GEOMETRIC_REASON_CANARY_COMPLETE ";
    private static final String SCHEMA = "tsj-q100-geometric-reason-canary-v1";
    private static final int CHUNK = 4096;
    private static final int MAX_CLONE_ENTRIES = 1048576;
    private static final long MAX_GROUP_NANOS = 5000000000L;
    private static final long MAX_WORK_NANOS = 90000000000L;
    private static final long MAX_OBSERVATION_UNITS = 32768L;
    private static final String FIRST_REASON_THROW = "FIRST_GEOMETRIC_REASON_CALLBACK";
    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    private static final Ledger LEDGER = new Ledger();

    private CanonicalGeometricReasonCanary() { }

    public static void main(String[] args) {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("P8 literal canary accepts no arguments");
        long processStart = System.nanoTime();
        LEDGER.start(processStart);
        ArrayList<CaseRow> rows = new ArrayList<CaseRow>();

        rows.add(routerCase("START_ESCAPE_REJECT", "SYNTHETIC_ROUTER_LITERAL",
            "START_ESCAPE_DIRECTION", p6Fixture(false, true),
            query("QUERY", "Q0.1", "Q1.1", 220, 250, 210, 250, false, true,
                false, false, "START_ESCAPE_DIRECTION", padEndpoint("START_ESCAPE_DIRECTION",
                    "Q0.1", 0), coarse("Q1.1", 0), null), null));
        rows.add(routerCase("START_ESCAPE_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            p6Fixture(false, true), query("QUERY", "Q0.1", "Q1.1", 220, 250, 230, 250,
                false, true, true, false, null, null, coarse("Q1.1", 3), null), null));
        rows.add(routerCase("END_ESCAPE_REJECT", "SYNTHETIC_ROUTER_LITERAL",
            "END_ESCAPE_DIRECTION", endEscapeFixture(),
            query("QUERY", "Q0.1", "Q1.1", 310, 250, 320, 250, false, true,
                false, false, "END_ESCAPE_DIRECTION", padEndpoint("END_ESCAPE_DIRECTION",
                    "Q1.1", 0), coarse("Q1.1", 0), null), null));
        rows.add(routerCase("END_ESCAPE_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            endEscapeFixture(), query("QUERY", "Q0.1", "Q1.1", 320, 250, 310, 250,
                false, true, true, false, null, null, coarse("Q1.1", 3), null), null));

        rows.add(cacheCase("EMPTY_UNRESERVED", CacheOwner.NONE, false, null, null, null));
        rows.add(cacheCase("EMPTY_SAME_OWNER", CacheOwner.SAME, false, null, null, null));
        rows.add(cacheCase("EMPTY_SINGLE_OWNER", CacheOwner.SINGLE, false,
            "EMPTY_FACE_RESERVATION", reservation("EMPTY_FACE_RESERVATION", "SINGLE", "A"), null));
        rows.add(cacheCase("EMPTY_MIXED_OWNER", CacheOwner.MIXED, false,
            "EMPTY_FACE_RESERVATION", reservation("EMPTY_FACE_RESERVATION", "MIXED", ""), null));
        rows.add(cacheCase("ADJACENT_SAME_OWNER", CacheOwner.SAME, true, null, null, null));
        rows.add(cacheCase("ADJACENT_SINGLE_OWNER", CacheOwner.SINGLE, true,
            "ADJACENT_RESERVATION", reservation("ADJACENT_RESERVATION", "SINGLE", "A"), null));
        rows.add(cacheCase("ADJACENT_MIXED_OWNER", CacheOwner.MIXED, true,
            "ADJACENT_RESERVATION", reservation("ADJACENT_RESERVATION", "MIXED", ""), null));

        rows.add(rulesCase("RULES_UNKNOWN_NET", "INVALID_RULES_INPUT", "UNDECLARED",
            new Rectangle(400, 300, 10, 10), "DOMAIN_UNKNOWN_NET", -1, null, null,
            false, 0, 0, 0, 0));
        rows.add(rulesCase("RULES_CORRIDOR", "RULES_BOUNDARY_LITERAL", "PRIMARY",
            new Rectangle(400, 300, 10, 10), "DOMAIN_CORRIDOR_OVERLAP", 0,
            "PRIMARY", "SECONDARY", true, 350, 430, 400, 410));
        rows.add(rulesCase("RULES_MEMBERSHIP", "RULES_BOUNDARY_LITERAL", "PRIMARY",
            new Rectangle(530, 300, 10, 10), "DOMAIN_MEMBERSHIP_SIDE", 0,
            "PRIMARY", "SECONDARY", true, 350, 430, 530, 540));

        rows.add(routerCase("ROUTER_CORRIDOR", "SYNTHETIC_ROUTER_LITERAL",
            "DOMAIN_CORRIDOR_OVERLAP", domainRouterFixture(),
            query("PRIMARY", "P1.1", "P2.1", 360, 300, 370, 300, false, true,
                false, false, "DOMAIN_CORRIDOR_OVERLAP",
                domain("DOMAIN_CORRIDOR_OVERLAP", "PRIMARY", "SECONDARY", 1,
                    350, 430, 356, 375), coarse("P2.1", 1), null), null));
        rows.add(routerCase("ROUTER_MEMBERSHIP", "SYNTHETIC_ROUTER_LITERAL",
            "DOMAIN_MEMBERSHIP_SIDE", domainRouterFixture(),
            query("PRIMARY", "P1.1", "P2.1", 520, 300, 530, 300, false, true,
                false, false, "DOMAIN_MEMBERSHIP_SIDE",
                domain("DOMAIN_MEMBERSHIP_SIDE", "PRIMARY", "SECONDARY", 1,
                    350, 430, 516, 535), coarse("P2.1", 1), null), null));
        rows.add(routerCase("ROUTER_DOMAIN_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            domainRouterFixture(), query("PRIMARY", "P1.1", "P2.1", 310, 300, 320, 300,
                false, true, true, false, null, null, coarse("P2.1", 3), null), null));

        rows.add(routerCase("PAD_ESCAPE_REJECT", "SYNTHETIC_ROUTER_LITERAL",
            "PAD_ESCAPE_INTERSECTION", p6Fixture(true, true),
            query("FOREIGN", "F0.1", "F1.1", 220, 250, 240, 250, false, true,
                false, false, "PAD_ESCAPE_INTERSECTION",
                indexedPad("PAD_ESCAPE_INTERSECTION", "Q0.1", "QUERY", 1),
                coarse("F1.1", 1), null), null));
        rows.add(routerCase("PAD_ESCAPE_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            p6Fixture(true, true), query("FOREIGN", "F0.1", "F1.1", 250, 250, 270, 250,
                false, true, true, false, null, null, coarse("F1.1", 3), null), null));

        rows.add(routerCase("PROFILED_TOUCH_REJECT", "SYNTHETIC_ROUTER_LITERAL",
            "PROFILED_PAD_TOUCH", profiledTouchFixture(false),
            query("HIGH", "H1.1", "H2.1", 220, 220, 240, 220, false, true,
                false, false, "PROFILED_PAD_TOUCH",
                indexedPad("PROFILED_PAD_TOUCH", "S.1", "SIGNAL", 1),
                coarse("H2.1", 1), null), null));
        rows.add(routerCase("PROFILED_TOUCH_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            profiledTouchFixture(true), query("HIGH", "H1.1", "H2.1", 220, 220, 240,
                220, false, true, true, false, null, null, coarse("H2.1", 3), null), null));

        rows.add(routerCase("COURTYARD_REJECT", "SYNTHETIC_ROUTER_LITERAL",
            "COMPONENT_COURTYARD", p6Fixture(true, false),
            query("QUERY", "Q0.1", "Q1.1", 310, 250, 330, 250, false, true,
                false, false, "COMPONENT_COURTYARD",
                component("C0", 1, 0, 0, 1), coarse("Q1.1", 1), null), null));
        rows.add(routerCase("COURTYARD_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            p6Fixture(true, false), query("QUERY", "Q0.1", "Q1.1", 330, 250, 350, 250,
                false, true, true, false, null, null, coarse("Q1.1", 3), null), null));
        rows.add(routerCase("UNDERPASS_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            underpassFixture(true), query("HIGH", "HIGH_START.1", "HIGH_END.1",
                -400, -180, -390, -180, false, true, true, false, null, null,
                coarse("HIGH_END.1", 3), null), null));
        rows.add(routerCase("UNDERPASS_DISABLED", "SYNTHETIC_ROUTER_LITERAL",
            "COMPONENT_COURTYARD", underpassFixture(false),
            query("HIGH", "HIGH_START.1", "HIGH_END.1", -400, -180, -390, -180,
                false, true, false, false, "COMPONENT_COURTYARD",
                component("RAISED_TOP", 1, 0, 0, 0), coarse("HIGH_END.1", 1),
                new QueryVerifier() {
                    public void verify(Fixture f, PcbNetRouter.Router query,
                            PcbNetRouter.Router parity) {
                        String value = reservationAt(query, "horizontalReservation",
                            -400, -180);
                        LEDGER.check(value == null,
                            "underpass fixture query edge has no reservation cache owner");
                    }
                }), null));

        rows.add(routerCase("EMPTY_REACHED_ALLOW", "SYNTHETIC_ROUTER_LITERAL", null,
            p6Fixture(false, false), query("QUERY", "Q0.1", "Q1.1", 290, 250, 300,
                250, true, false, true, false, null, null, coarse("Q1.1", 3), null),
            new QueryVerifier() {
                public void verify(Fixture f, PcbNetRouter.Router query,
                        PcbNetRouter.Router parity) {
                    LEDGER.check(f.reached != null && f.reached.length == 0,
                        "empty reached-PTH literal is a present zero-length list");
                }
            }));
        rows.add(routerCase("MULTI_ENDPOINT_ORDER", "SYNTHETIC_ROUTER_LITERAL",
            "END_ESCAPE_DIRECTION", multiEndpointFixture(),
            multiEndpointQuery(), null));
        rows.add(routerCase("REASON_THROW", "OBSERVER_LIFECYCLE",
            "START_ESCAPE_DIRECTION", p6Fixture(false, true),
            query("QUERY", "Q0.1", "Q1.1", 220, 250, 210, 250, false, true,
                false, true, "START_ESCAPE_DIRECTION",
                new ReasonFactory[] {padEndpoint("START_ESCAPE_DIRECTION", "Q0.1", 0)},
                new CoarseExpectation[0], null), null));

        rows.add(attachmentRolesCase());
        rows.add(copyDefaultNullCase());
        rows.add(nullParityCase());

        long outputStart = LEDGER.beginReservedGroup();
        validateFrozenRows(rows);
        long preOutputWork = elapsed(processStart, System.nanoTime());
        if (preOutputWork > MAX_WORK_NANOS)
            throw new IllegalStateException("P8 literal canary exceeded its 90-second work bound");
        if (LEDGER.units + 1L > MAX_OBSERVATION_UNITS)
            throw new IllegalStateException("P8 literal canary exceeded its observation-unit bound");
        if (LEDGER.maximumGroupNanos > MAX_GROUP_NANOS)
            throw new IllegalStateException("P8 literal canary exceeded a five-second group bound");
        if (LEDGER.assertions < 1)
            throw new IllegalStateException("P8 literal canary has no evaluated assertions");

        String json = outputJson(rows, preOutputWork, LEDGER.maximumGroupNanos,
            LEDGER.units + 1L);
        System.out.print(PREFIX);
        System.out.print(json);
        System.out.print('\n');
        System.out.flush();
        long outputGroup = elapsed(outputStart, System.nanoTime());
        LEDGER.finishReservedGroup(outputStart, outputGroup);
        if (elapsed(processStart, System.nanoTime()) > MAX_WORK_NANOS)
            throw new IllegalStateException("P8 literal canary exceeded its final 90-second bound");
    }

    private static CaseRow cacheCase(final String id, final CacheOwner owner,
            final boolean adjacent, String site, final ReasonFactory expected,
            final QueryVerifier additional) {
        LEDGER.beginCaseGroup();
        QuerySpec spec = query("QUERY", "Q0.1", "Q1.1", 290, 260, 300, 260,
            false, true, owner == CacheOwner.NONE || owner == CacheOwner.SAME,
            false, site, expected, coarse("Q1.1", site == null ? 3 : 1),
            new QueryVerifier() {
                public void verify(Fixture fixture, PcbNetRouter.Router query,
                        PcbNetRouter.Router parity) {
                    String expectedOwner = owner == CacheOwner.NONE ? null :
                        owner == CacheOwner.SAME ? "QUERY" :
                        owner == CacheOwner.SINGLE ? "A" : "";
                    String cached = reservationAt(query, "horizontalReservation", 290, 260);
                    LEDGER.check(equal(expectedOwner, cached),
                        id + " selected horizontal edge cache owner is literal");
                    LEDGER.check(reservationAt(query, "occupiedNet", 290, 260) == null &&
                        reservationAt(query, "clearanceNet", 290, 260) == null,
                        id + " does not synthesize occupied or clearance copper");
                    if (adjacent) {
                        LEDGER.check(!booleanField(query, "emptyComponentFace"),
                            id + " has a real same-face component and reaches adjacent cache");
                    } else {
                        LEDGER.check(booleanField(query, "emptyComponentFace"),
                            id + " uses the empty-face reservation branch");
                    }
                }
            });
        final QueryVerifier verifier = spec.verifier;
        spec.verifier = new QueryVerifier() {
            public void verify(Fixture fixture, PcbNetRouter.Router query,
                    PcbNetRouter.Router parity) {
                verifier.verify(fixture, query, parity);
                if (additional != null) additional.verify(fixture, query, parity);
            }
        };
        return routerCase(id, "SYNTHETIC_ROUTER_LITERAL", site,
            cacheFixture(owner, adjacent), spec, null);
    }

    private static CaseRow routerCase(String id, String scope, String expectedSite,
            FixtureFactory factory, QuerySpec spec, QueryVerifier additionalVerifier) {
        long groupStart = LEDGER.beginCaseGroup();
        Fixture fixture = null;
        PcbNetRouter.Router source = null;
        PcbNetRouter.Router live = null, pristine = null, observed = null, parity = null;
        ReasonCapture fine = null, parityFine = null;
        StageCapture coarse = null, parityCoarse = null;
        StageCapture reportedCoarse = null;
        List<PcbNetRouter.Router.FixedGraphReason> reported = null;
        QueryOutcome observedOutcome = null;
        Throwable observedFailure = null;
        Throwable sentinel = spec.throwReason ?
            new IllegalStateException(FIRST_REASON_THROW) : null;
        boolean observedAttached = false, parityAttached = false;
        CaseRow row = new CaseRow(id, scope, expectedSite);
        try {
            fixture = factory.create();
            spec.id = id;
            fixture.start = fixture.layout.getPad(spec.startPadId);
            fixture.end = spec.endPadId == null ? null : fixture.layout.getPad(spec.endPadId);
            if (spec.reachedSupplied && !spec.useFixtureReached)
                fixture.reached = new PcbPadPlacement[0];
            LEDGER.check(equal(expectedSite, spec.expectedSite),
                id + " case row expected site agrees with its frozen query literal");
            LEDGER.check(fixture != null && fixture.router != null && fixture.start != null &&
                fixture.layout != null && fixture.board != null,
                id + " fixture has its maintained source context");
            String geometryBefore = fixture.geometryBefore;
            String sourceEffectsBefore = fixture.effectsBefore;
            source = fixture.router;
            LEDGER.check(geometryBefore != null && sourceEffectsBefore != null,
                id + " captured source geometry and effects before observation");
            LEDGER.check(!source.hasFixedGraphReasonObserverForStudy() &&
                !source.hasFixedGraphStageObserverForStudy(),
                id + " source starts without either observer");

            live = copyBounded(source).router;
            pristine = copyBounded(source).router;
            observed = copyBounded(source).router;
            parity = copyBounded(source).router;
            LEDGER.check(live != null && pristine != null && observed != null && parity != null,
                id + " all four bounded copies completed");
            LEDGER.check(!live.hasFixedGraphReasonObserverForStudy() &&
                !live.hasFixedGraphStageObserverForStudy() &&
                !pristine.hasFixedGraphReasonObserverForStudy() &&
                !pristine.hasFixedGraphStageObserverForStudy(),
                id + " live and pristine copies retain null observers");
            LEDGER.check(compareBounded(live, pristine).equal,
                id + " live and pristine fixed graphs compare equal");
            LEDGER.check(compareBounded(observed, pristine).equal,
                id + " observed query copy compares equal before attach");
            LEDGER.check(compareBounded(parity, pristine).equal,
                id + " parity query copy compares equal before attach");

            coarse = new StageCapture();
            fine = new ReasonCapture(sentinel, coarse.order);
            observed.attachFixedGraphReasonObserverForStudy(fine, observed, live,
                pristine, true);
            observedAttached = true;
            observed.attachFixedGraphStageObserverForStudy(coarse, observed, live,
                pristine, true);
            LEDGER.check(observed.hasFixedGraphReasonObserverForStudy() &&
                observed.hasFixedGraphStageObserverForStudy(),
                id + " fine and coarse observers coexist on the compared query copy");

            if ("NULL_PARITY".equals(id)) {
                parityCoarse = new StageCapture();
                parity.attachFixedGraphStageObserverForStudy(parityCoarse, parity, live,
                    pristine, true);
                parityAttached = true;
            }

            observedOutcome = invokeQuery(observed, fixture, spec);
            observedFailure = observedOutcome.failure;
            if (!spec.throwReason)
                checkOutcome(id + " observed", observedOutcome, false, null);
            if (!spec.throwReason)
                LEDGER.check(observedOutcome.allowed == spec.allowed,
                    id + " observed return matches the independent allowed/rejected literal");
            checkNoCongestion(id + " observed", observed);
            if (!spec.throwReason && !"NULL_PARITY".equals(id)) {
                QueryOutcome parityOutcome = invokeQuery(parity, fixture, spec);
                checkOutcome(id + " null-sink parity", parityOutcome, false, null);
                LEDGER.check(parityOutcome.allowed == spec.allowed,
                    id + " null-sink return matches the independent allowed/rejected literal");
                checkNoCongestion(id + " null-sink parity", parity);
                LEDGER.check(equal(Boolean.valueOf(observedOutcome.allowed),
                    Boolean.valueOf(parityOutcome.allowed)), id + " return parity");
                LEDGER.check(equal(observed.fixedGraphObservationEffects(),
                    parity.fixedGraphObservationEffects()), id + " effects/counter parity");
                LEDGER.check(observed.expansions == parity.expansions &&
                    observed.rawSegments == parity.rawSegments &&
                    observed.congestionRejections == parity.congestionRejections,
                    id + " explicit router counter parity");
                LEDGER.check(compareBounded(observed, parity).equal,
                    id + " post-query predicate graph parity");
            } else if ("NULL_PARITY".equals(id)) {
                verifyRouterEvents(id + " observed reference", fixture, observed, fine.events,
                    coarse, new ReasonFactory[] {
                        padEndpoint("START_ESCAPE_DIRECTION", "Q0.1", 0)
                    }, new CoarseExpectation[] {coarse("Q1.1", 0)}, spec, false);
                QueryOutcome parityOutcome = invokeQuery(parity, fixture, spec);
                checkOutcome(id + " fine-null parity", parityOutcome, false, null);
                LEDGER.check(parityOutcome.allowed == spec.allowed,
                    id + " fine-null return matches the independent rejected literal");
                checkNoCongestion(id + " fine-null parity", parity);
                LEDGER.check(equal(Boolean.valueOf(observedOutcome.allowed),
                    Boolean.valueOf(parityOutcome.allowed)), id + " observed/null return parity");
                LEDGER.check(equal(observed.fixedGraphObservationEffects(),
                    parity.fixedGraphObservationEffects()), id + " observed/null effects parity");
                LEDGER.check(compareBounded(observed, parity).equal,
                    id + " observed/null predicate graph parity");
                LEDGER.check(parityFine == null && !parity.hasFixedGraphReasonObserverForStudy() &&
                    parity.hasFixedGraphStageObserverForStudy(),
                    id + " measured query has no fine sink and retains its coarse sink");
            }

            reported =
                "NULL_PARITY".equals(id) ?
                    Collections.<PcbNetRouter.Router.FixedGraphReason>emptyList() : fine.events;
            reportedCoarse = "NULL_PARITY".equals(id) ? parityCoarse : coarse;
            ReasonFactory[] expectedReasons = "NULL_PARITY".equals(id) ?
                new ReasonFactory[0] : spec.reasons;
            CoarseExpectation[] expectedCoarse = "NULL_PARITY".equals(id) ?
                new CoarseExpectation[] {coarse(spec.endPadId, 0)} : spec.coarse;
            verifyRouterEvents(id, fixture, observed, reported, reportedCoarse,
                expectedReasons, expectedCoarse, spec, "NULL_PARITY".equals(id));
            if (spec.verifier != null) spec.verifier.verify(fixture, observed, parity);
            if (additionalVerifier != null)
                additionalVerifier.verify(fixture, observed, parity);

            if (spec.throwReason) {
                LEDGER.check(fine.events.size() == 1 && coarse.events.isEmpty(),
                    id + " records the first fine event and stops before the coarse callback");
            }

            boolean sourceEqual = compareBounded(source, pristine).equal;
            LEDGER.check(sourceEqual, id + " live source equals its pristine copy after query");
            LEDGER.check(equal(sourceEffectsBefore, source.fixedGraphObservationEffects()),
                id + " live source effects are unchanged after query");
            LEDGER.check(equal(geometryBefore, fixture.layout.geometryFingerprint()),
                id + " source layout geometry is unchanged after query");
            LEDGER.check(!source.hasFixedGraphReasonObserverForStudy() &&
                !source.hasFixedGraphStageObserverForStudy() &&
                !pristine.hasFixedGraphReasonObserverForStudy() &&
                !pristine.hasFixedGraphStageObserverForStudy(),
                id + " source and pristine remain observer-free");

            row.actualSites.addAll(siteList(reported));
            row.fineEvents = reported.size();
            row.coarseEvents = reportedCoarse == null ? 0 : reportedCoarse.events.size();
            row.sourcePreserved = true;
            observedOutcome = null;
        } finally {
            if (observed != null) {
                observed.detachFixedGraphStageObserverForStudy();
                observed.detachFixedGraphReasonObserverForStudy();
            }
            if (parity != null) {
                parity.detachFixedGraphStageObserverForStudy();
                parity.detachFixedGraphReasonObserverForStudy();
            }
            boolean detached = (observed == null ||
                    !observed.hasFixedGraphReasonObserverForStudy() &&
                    !observed.hasFixedGraphStageObserverForStudy()) &&
                (parity == null || !parity.hasFixedGraphReasonObserverForStudy() &&
                    !parity.hasFixedGraphStageObserverForStudy());
            LEDGER.check(detached, id + " both sinks detach on every exit path");
            row.observersDetached = detached;
            if (fine != null) fine.clear();
            if (coarse != null) coarse.clear();
            if (parityFine != null) parityFine.clear();
            if (parityCoarse != null) parityCoarse.clear();
            fine = null; coarse = null; parityFine = null; parityCoarse = null;
            reported = null; reportedCoarse = null;
            observed = null; parity = null; live = null; pristine = null; source = null;
            if (fixture != null) fixture.drop();
            fixture = null;
            row.referencesDropped = detached && fine == null && coarse == null &&
                parityFine == null && parityCoarse == null && observed == null &&
                parity == null && live == null && pristine == null && source == null &&
                fixture == null &&
                reported == null && reportedCoarse == null;
            LEDGER.check(row.referencesDropped, id + " query and callback references are dropped");
        }
        if (spec.throwReason) {
            LEDGER.check(row.sourcePreserved && row.observersDetached && row.referencesDropped,
                id + " source/effects and observer cleanup proofs precede exception acceptance");
            LEDGER.check(observedFailure == sentinel &&
                observedFailure.getClass() == IllegalStateException.class &&
                FIRST_REASON_THROW.equals(observedFailure.getMessage()),
                id + " preserves exact first-reason throwable identity after cleanup");
            observedFailure = null;
        }
        finishGroup(id, groupStart);
        return row;
    }

    private static QueryOutcome invokeQuery(PcbNetRouter.Router router, Fixture fixture,
            QuerySpec spec) {
        QueryOutcome outcome = new QueryOutcome();
        long start = System.nanoTime();
        try {
            if (spec.useFixtureReached || spec.reachedSupplied)
                outcome.allowed = router.permitsLayerStep(spec.fromX, spec.fromY,
                    spec.toX, spec.toY, spec.net, fixture.start, fixture.end,
                    spec.reachedSupplied ? fixture.reached : null);
            else
                outcome.allowed = router.permitsLayerStep(spec.fromX, spec.fromY,
                    spec.toX, spec.toY, spec.net, fixture.start, fixture.end);
        } catch (Throwable failure) {
            outcome.failure = failure;
        } finally {
            LEDGER.unit(idOf(spec) + " complete Router query", elapsed(start, System.nanoTime()));
        }
        return outcome;
    }

    private static String idOf(QuerySpec spec) {
        return spec.id == null ? "Router query" : spec.id;
    }

    private static void checkOutcome(String label, QueryOutcome outcome,
            boolean expectFailure, Throwable sentinel) {
        if (expectFailure) {
            LEDGER.check(outcome.failure == sentinel,
                label + " returned the exact required throwable object");
            return;
        }
        if (outcome.failure != null) rethrow(outcome.failure);
        LEDGER.check(outcome.failure == null, label + " completed normally");
    }

    private static void verifyRouterEvents(String id, Fixture fixture,
            PcbNetRouter.Router router,
            List<PcbNetRouter.Router.FixedGraphReason> reasons, StageCapture coarse,
            ReasonFactory[] expectedReasons, CoarseExpectation[] expectedCoarse,
            QuerySpec spec, boolean fineNull) {
        LEDGER.check(reasons.size() == expectedReasons.length,
            id + " fine event count matches the frozen literal");
        ArrayList<String> sites = siteList(reasons);
        ArrayList<String> wantedSites = new ArrayList<String>();
        for (ReasonFactory expected : expectedReasons) {
            PcbNetRouter.Router.FixedGraphReason literal = expected.create(fixture, router, spec);
            wantedSites.add(literal.site);
        }
        LEDGER.check(sites.equals(wantedSites), id + " fine site sequence matches the frozen literal");
        for (int i = 0; i < expectedReasons.length && i < reasons.size(); i++) {
            PcbNetRouter.Router.FixedGraphReason expected =
                expectedReasons[i].create(fixture, router, spec);
            checkReason(id + " reason[" + i + "]", reasons.get(i), expected);
        }

        int coarseCount = coarse == null ? 0 : coarse.events.size();
        LEDGER.check(coarseCount == expectedCoarse.length,
            id + " coarse event count matches the frozen literal");
        if (coarse != null) {
            for (int i = 0; i < expectedCoarse.length && i < coarse.events.size(); i++) {
                CoarseEvent actual = coarse.events.get(i);
                CoarseExpectation expected = expectedCoarse[i];
                LEDGER.check(equal(actual.endpointPadId, expected.endpointPadId) &&
                    actual.stage == expected.stage,
                    id + " coarse endpoint order/stage[" + i + "] is literal");
                LEDGER.check(equal(actual.startPadId, fixture.start.getPadId()) &&
                    actual.fromGridX == gridX(router, spec.fromX) &&
                    actual.fromGridY == gridY(router, spec.fromY) &&
                    actual.toGridX == gridX(router, spec.toX) &&
                    actual.toGridY == gridY(router, spec.toY) &&
                    actual.direction == coarseDirection(router, spec),
                    id + " coarse callback records the actual query identity/coordinates");
            }
        }

        if (!fineNull && expectedReasons.length > 0 && coarse != null && !spec.throwReason) {
            LEDGER.check(coarse.order.size() >= reasons.size(),
                id + " shared callback-order capture contains every fine event");
            for (int reasonIndex = 0; reasonIndex < reasons.size(); reasonIndex++) {
                PcbNetRouter.Router.FixedGraphReason reason = reasons.get(reasonIndex);
                int fineAt = findFineOrder(coarse.order, reason);
                int coarseAt = findMatchingCoarseOrder(coarse.order, reason, fineAt + 1);
                LEDGER.check(fineAt >= 0 && coarseAt > fineAt,
                    id + " fine guard callback precedes its matching coarse callback");
            }
        }

        if (spec.throwReason)
            LEDGER.check(coarseCount == 0 && reasons.size() == 1,
                id + " expected exception interrupts before coarse callback");
        if (expectedReasons.length == 0 && !fineNull)
            LEDGER.check(reasons.isEmpty(), id + " allowed control has no fine rejection");
    }

    private static int findFineOrder(List<OrderEvent> order,
            PcbNetRouter.Router.FixedGraphReason reason) {
        for (int i = 0; i < order.size(); i++) {
            OrderEvent event = order.get(i);
            if (event.fine && equal(event.endpointPadId, reason.endpointPadId) &&
                    equal(event.site, reason.site)) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingCoarseOrder(List<OrderEvent> order,
            PcbNetRouter.Router.FixedGraphReason reason, int after) {
        for (int i = Math.max(0, after); i < order.size(); i++) {
            OrderEvent event = order.get(i);
            if (!event.fine && event.stage == reason.stage &&
                    equal(event.endpointPadId, reason.endpointPadId) &&
                    event.fromGridX == reason.fromGridX && event.fromGridY == reason.fromGridY &&
                    event.toGridX == reason.toGridX && event.toGridY == reason.toGridY)
                return i;
        }
        return -1;
    }

    private static void checkReason(String label,
            PcbNetRouter.Router.FixedGraphReason actual,
            PcbNetRouter.Router.FixedGraphReason expected) {
        LEDGER.check(actual != null, label + " event is present");
        if (actual == null) return;
        LEDGER.check(equal(actual.site, expected.site) &&
            equal(actual.ownerKind, expected.ownerKind) && actual.stage == expected.stage,
            label + " site/owner kind/stage fields match the independent literal");
        LEDGER.check(equal(actual.startPadId, expected.startPadId) &&
            equal(actual.endpointPadId, expected.endpointPadId) &&
            actual.fromGridX == expected.fromGridX && actual.fromGridY == expected.fromGridY &&
            actual.toGridX == expected.toGridX && actual.toGridY == expected.toGridY,
            label + " actual endpoint and grid coordinates match the query");
        LEDGER.check(equal(actual.ownerId, expected.ownerId) &&
            equal(actual.ownerNet, expected.ownerNet) && actual.ownerIndex == expected.ownerIndex,
            label + " first owner identity/index matches the source descriptor");
        LEDGER.check(equal(actual.reservationState, expected.reservationState) &&
            equal(actual.reservationValue, expected.reservationValue),
            label + " cache state/value retains null, single, and mixed distinctions");
        LEDGER.check(equal(actual.domainLow, expected.domainLow) &&
            equal(actual.domainHigh, expected.domainHigh) &&
            actual.domainHorizontal == expected.domainHorizontal &&
            actual.domainLowLimit == expected.domainLowLimit &&
            actual.domainHighLimit == expected.domainHighLimit &&
            actual.domainFirst == expected.domainFirst &&
            actual.domainLast == expected.domainLast,
            label + " domain operands match the original boundary guard");
        LEDGER.check(actual.startEscape == expected.startEscape &&
            actual.endEscape == expected.endEscape &&
            actual.underpassEnabled == expected.underpassEnabled,
            label + " courtyard escape and underpass locals match the evaluated branch");
    }

    private static CaseRow rulesCase(String id, String scope, String net,
            Rectangle copper, String site, int boundaryIndex, String low, String high,
            boolean horizontal, int lowLimit, int highLimit, int first, int last) {
        long groupStart = LEDGER.beginCaseGroup();
        DomainFixture fixture = null;
        RuleCapture capture = new RuleCapture();
        CaseRow row = new CaseRow(id, scope, site);
        try {
            fixture = domainFixture();
            String geometryBefore = fixture.layout.geometryFingerprint();
            boolean result;
            long started = System.nanoTime();
            result = fixture.rules.permits(net, new Rectangle(copper), capture);
            LEDGER.unit(id + " per-call PcbTwoLayerRules query", elapsed(started, System.nanoTime()));
            LEDGER.check(!result, id + " rejects the literal physical rectangle");
            LEDGER.check(capture.events.size() == 1,
                id + " records exactly the first direct-rules reason");
            if (!capture.events.isEmpty()) {
                RuleEvent actual = capture.events.get(0);
                LEDGER.check(equal(actual.site, site) && actual.boundaryIndex == boundaryIndex &&
                    equal(actual.low, low) && equal(actual.high, high) &&
                    actual.horizontal == horizontal && actual.lowLimit == lowLimit &&
                    actual.highLimit == highLimit && actual.first == first && actual.last == last,
                    id + " exact direct-rules callback operands match literal geometry");
            }
            long nullStarted = System.nanoTime();
            boolean nullResult = fixture.rules.permits(net, new Rectangle(copper), null);
            LEDGER.unit(id + " null-observer rules parity call",
                elapsed(nullStarted, System.nanoTime()));
            LEDGER.check(nullResult == result, id + " null-observer rule result parity");
            LEDGER.check(equal(geometryBefore, fixture.layout.geometryFingerprint()),
                id + " direct rules call preserves layout geometry");
            LEDGER.check(noRuleObserverFields(), id + " rules object stores no observer field");
            if (!capture.events.isEmpty()) row.actualSites.add(capture.events.get(0).site);
            row.fineEvents = capture.events.size();
            row.coarseEvents = 0;
            row.sourcePreserved = true;
        } finally {
            capture.clear();
            capture = null;
            if (fixture != null) fixture.drop();
            fixture = null;
            row.observersDetached = true;
            row.referencesDropped = capture == null && fixture == null;
            LEDGER.check(row.referencesDropped, id + " direct callback/context references are dropped");
            finishGroup(id, groupStart);
        }
        return row;
    }

    private static boolean noRuleObserverFields() {
        for (Field field : PcbTwoLayerRules.class.getDeclaredFields())
            if (PcbTwoLayerRules.ReasonObserver.class.isAssignableFrom(field.getType()))
                return false;
        return true;
    }

    private static CaseRow attachmentRolesCase() {
        long groupStart = LEDGER.beginCaseGroup();
        final String id = "ATTACHMENT_ROLES";
        Fixture fixture = null;
        PcbNetRouter.Router live = null, pristine = null, query = null, other = null;
        ReasonCapture fine = null;
        StageCapture coarse = null;
        CaseRow row = new CaseRow(id, "OBSERVER_LIFECYCLE", null);
        try {
            fixture = p6Fixture(false, false).create();
            String geometryBefore = fixture.geometryBefore;
            String effectsBefore = fixture.effectsBefore;
            live = copyBounded(fixture.router).router;
            pristine = copyBounded(fixture.router).router;
            query = copyBounded(fixture.router).router;
            other = copyBounded(fixture.router).router;
            fine = new ReasonCapture(null);
            coarse = new StageCapture();
            exerciseAttachmentRoles(id, fixture, live, pristine, query, other,
                fine, coarse, geometryBefore, effectsBefore);
            row.sourcePreserved = true;
        } finally {
            if (query != null) {
                query.detachFixedGraphStageObserverForStudy();
                query.detachFixedGraphReasonObserverForStudy();
            }
            boolean detached = query == null || !query.hasFixedGraphReasonObserverForStudy() &&
                !query.hasFixedGraphStageObserverForStudy();
            row.observersDetached = detached;
            if (fine != null) fine.clear(); if (coarse != null) coarse.clear();
            fine = null; coarse = null;
            query = null; other = null; live = null; pristine = null;
            if (fixture != null) fixture.drop(); fixture = null;
            row.referencesDropped = detached && fine == null && coarse == null &&
                query == null && other == null && live == null && pristine == null && fixture == null;
            LEDGER.check(row.observersDetached && row.referencesDropped,
                id + " detaches sinks and drops query/source references");
            finishGroup(id, groupStart);
        }
        return row;
    }

    private static void exerciseAttachmentRoles(final String id, final Fixture fixture,
            final PcbNetRouter.Router l, final PcbNetRouter.Router p,
            final PcbNetRouter.Router q, final PcbNetRouter.Router o,
            final ReasonCapture fineSink, final StageCapture coarseSink,
            final String geometryBefore, final String effectsBefore) {
        LEDGER.check(compareBounded(l, p).equal && compareBounded(q, p).equal &&
            compareBounded(o, p).equal,
            id + " role fixtures are complete equal observation copies");
        expectThrows(id + " null observer", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphReasonObserverForStudy(null, q, l, p, true);
            }});
        expectThrows(id + " null coarse observer", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphStageObserverForStudy(null, q, l, p, true);
            }});
        expectThrows(id + " query/live alias", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphReasonObserverForStudy(fineSink, q, q, p, true);
            }});
        expectThrows(id + " query/pristine alias", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphReasonObserverForStudy(fineSink, q, l, q, true);
            }});
        expectThrows(id + " live/pristine alias", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphReasonObserverForStudy(fineSink, q, l, l, true);
            }});
        expectThrows(id + " wrong receiver", IllegalArgumentException.class,
            new Action() { public void run() {
                o.attachFixedGraphReasonObserverForStudy(fineSink, q, l, p, true);
            }});
        expectThrows(id + " failed comparison", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphReasonObserverForStudy(fineSink, q, l, p, false);
            }});
        expectThrows(id + " coarse alias", IllegalArgumentException.class,
            new Action() { public void run() {
                q.attachFixedGraphStageObserverForStudy(coarseSink, q, q, p, true);
            }});
        q.attachFixedGraphReasonObserverForStudy(fineSink, q, l, p, true);
        q.attachFixedGraphStageObserverForStudy(coarseSink, q, l, p, true);
        LEDGER.check(q.hasFixedGraphReasonObserverForStudy() &&
            q.hasFixedGraphStageObserverForStudy(), id + " accepts independent fine/coarse sinks");
        expectThrows(id + " duplicate fine attach", IllegalStateException.class,
            new Action() { public void run() {
                q.attachFixedGraphReasonObserverForStudy(new ReasonCapture(null), q,
                    l, p, true);
            }});
        expectThrows(id + " duplicate coarse attach", IllegalStateException.class,
            new Action() { public void run() {
                q.attachFixedGraphStageObserverForStudy(new StageCapture(), q, l, p, true);
            }});
        q.detachFixedGraphReasonObserverForStudy();
        q.detachFixedGraphReasonObserverForStudy();
        q.detachFixedGraphStageObserverForStudy();
        q.detachFixedGraphStageObserverForStudy();
        LEDGER.check(!q.hasFixedGraphReasonObserverForStudy() &&
            !q.hasFixedGraphStageObserverForStudy(), id + " detach is idempotent");
        LEDGER.check(equal(geometryBefore, fixture.layout.geometryFingerprint()) &&
            equal(effectsBefore, fixture.router.fixedGraphObservationEffects()) &&
            compareBounded(fixture.router, p).equal,
            id + " role validation and attachment preserve source state");
        LEDGER.check(!fixture.router.hasFixedGraphReasonObserverForStudy() &&
            !fixture.router.hasFixedGraphStageObserverForStudy() &&
            !l.hasFixedGraphReasonObserverForStudy() &&
            !l.hasFixedGraphStageObserverForStudy() &&
            !p.hasFixedGraphReasonObserverForStudy() &&
            !p.hasFixedGraphStageObserverForStudy(),
            id + " no observer is attached to live or pristine");
    }

    private static CaseRow copyDefaultNullCase() {
        long groupStart = LEDGER.beginCaseGroup();
        final String id = "COPY_DEFAULT_NULL";
        Fixture fixture = null;
        PcbNetRouter.Router live = null, pristine = null, query = null, copy = null;
        ReasonCapture fine = null;
        StageCapture coarse = null;
        CaseRow row = new CaseRow(id, "OBSERVER_LIFECYCLE", null);
        try {
            fixture = p6Fixture(false, false).create();
            String geometryBefore = fixture.geometryBefore;
            String effectsBefore = fixture.effectsBefore;
            live = copyBounded(fixture.router).router;
            pristine = copyBounded(fixture.router).router;
            query = copyBounded(fixture.router).router;
            LEDGER.check(compareBounded(query, pristine).equal,
                id + " query copy compares equal before attachment");
            LEDGER.check(!fixture.router.hasFixedGraphReasonObserverForStudy() &&
                !fixture.router.hasFixedGraphStageObserverForStudy() &&
                !live.hasFixedGraphReasonObserverForStudy() &&
                !live.hasFixedGraphStageObserverForStudy() &&
                !pristine.hasFixedGraphReasonObserverForStudy() &&
                !pristine.hasFixedGraphStageObserverForStudy(),
                id + " default live/pristine observer fields are null");
            fine = new ReasonCapture(null); coarse = new StageCapture();
            query.attachFixedGraphReasonObserverForStudy(fine, query, live, pristine, true);
            query.attachFixedGraphStageObserverForStudy(coarse, query, live, pristine, true);
            copy = copyBounded(query).router;
            LEDGER.check(copy != null && !copy.hasFixedGraphReasonObserverForStudy() &&
                !copy.hasFixedGraphStageObserverForStudy(),
                id + " copied router retains neither observer sink");
            LEDGER.check(query.hasFixedGraphReasonObserverForStudy() &&
                query.hasFixedGraphStageObserverForStudy(),
                id + " original query still owns both attached sinks");
            LEDGER.check(equal(geometryBefore, fixture.layout.geometryFingerprint()) &&
                equal(effectsBefore, fixture.router.fixedGraphObservationEffects()) &&
                compareBounded(fixture.router, pristine).equal,
                id + " copying observers leaves source and geometry unchanged");
            row.sourcePreserved = true;
        } finally {
            if (query != null) {
                query.detachFixedGraphStageObserverForStudy();
                query.detachFixedGraphReasonObserverForStudy();
            }
            boolean detached = (query == null || !query.hasFixedGraphReasonObserverForStudy() &&
                !query.hasFixedGraphStageObserverForStudy()) &&
                (copy == null || !copy.hasFixedGraphReasonObserverForStudy() &&
                    !copy.hasFixedGraphStageObserverForStudy());
            row.observersDetached = detached;
            if (fine != null) fine.clear(); if (coarse != null) coarse.clear();
            fine = null; coarse = null;
            copy = null; query = null; live = null; pristine = null;
            if (fixture != null) fixture.drop(); fixture = null;
            row.referencesDropped = detached && fine == null && coarse == null &&
                copy == null && query == null && live == null && pristine == null && fixture == null;
            LEDGER.check(row.observersDetached && row.referencesDropped,
                id + " detaches and drops original/copied observer references");
            finishGroup(id, groupStart);
        }
        return row;
    }

    private static CaseRow nullParityCase() {
        LEDGER.beginCaseGroup();
        return routerCase("NULL_PARITY", "OBSERVER_LIFECYCLE", null,
            p6Fixture(false, true), query("QUERY", "Q0.1", "Q1.1", 220, 250, 210,
                250, false, true, false, false, null, null,
                new CoarseExpectation[] {coarse("Q1.1", 0)}, null), null);
    }

    private static QuerySpec multiEndpointQuery() {
        return new QuerySpec("MULTI_ENDPOINT_ORDER", "QUERY", "Q0.1", "Q1.1",
            310, 250, 320, 250, true, true, true, false,
            "END_ESCAPE_DIRECTION",
            new ReasonFactory[] {
                padEndpoint("END_ESCAPE_DIRECTION", "Q1.1", 0),
                padEndpoint("END_ESCAPE_DIRECTION", "Q2.1", 0)
            }, new CoarseExpectation[] {coarse("Q1.1", 0), coarse("Q2.1", 0),
                coarse("Q3.1", 3)}, null);
    }

    private static QuerySpec query(String net, String start, String end,
            int fromX, int fromY, int toX, int toY, boolean reachedSupplied,
            boolean useFixtureReached, boolean allowed, boolean throwReason,
            String expectedSite, ReasonFactory reason, CoarseExpectation coarse,
            QueryVerifier verifier) {
        return query(net, start, end, fromX, fromY, toX, toY, reachedSupplied,
            useFixtureReached, allowed, throwReason, expectedSite,
            reason == null ? new ReasonFactory[0] : new ReasonFactory[] {reason},
            coarse == null ? new CoarseExpectation[0] : new CoarseExpectation[] {coarse}, verifier);
    }

    private static QuerySpec query(String net, String start, String end,
            int fromX, int fromY, int toX, int toY, boolean reachedSupplied,
            boolean useFixtureReached, boolean allowed, boolean throwReason,
            String expectedSite, ReasonFactory[] reasons, CoarseExpectation[] coarse,
            QueryVerifier verifier) {
        QuerySpec spec = new QuerySpec(null, net, start, end, fromX, fromY, toX, toY,
            reachedSupplied, useFixtureReached, allowed, throwReason, expectedSite,
            reasons, coarse, verifier);
        return spec;
    }

    private static ReasonFactory padEndpoint(final String site, final String ownerId,
            final int stage) {
        return new ReasonFactory() {
            public PcbNetRouter.Router.FixedGraphReason create(Fixture f,
                    PcbNetRouter.Router router, QuerySpec spec) {
                ExpectedReason e = baseReason(f, router, spec, site, "PAD", stage,
                    "Q2.1".equals(ownerId) ? "Q2.1" : spec.endPadId);
                e.ownerId = ownerId; e.ownerNet = null; e.ownerIndex = -1;
                return e.build();
            }
        };
    }

    private static ReasonFactory indexedPad(final String site, final String ownerId,
            final String ownerNet, final int stage) {
        return new ReasonFactory() {
            public PcbNetRouter.Router.FixedGraphReason create(Fixture f,
                    PcbNetRouter.Router router, QuerySpec spec) {
                ExpectedReason e = baseReason(f, router, spec, site, "PAD", stage,
                    spec.endPadId);
                e.ownerId = ownerId; e.ownerNet = ownerNet;
                e.ownerIndex = padIndex(f.layout, ownerId);
                return e.build();
            }
        };
    }

    private static ReasonFactory reservation(final String site, final String state,
            final String value) {
        return new ReasonFactory() {
            public PcbNetRouter.Router.FixedGraphReason create(Fixture f,
                    PcbNetRouter.Router router, QuerySpec spec) {
                ExpectedReason e = baseReason(f, router, spec, site,
                    "CACHED_RESERVATION", 1, spec.endPadId);
                e.ownerIndex = -1; e.ownerNet = "MIXED".equals(state) ? null : value;
                e.reservationState = state; e.reservationValue = value;
                return e.build();
            }
        };
    }

    private static ReasonFactory domain(final String site, final String low,
            final String high, final int horizontal, final int lowLimit,
            final int highLimit, final int first, final int last) {
        return new ReasonFactory() {
            public PcbNetRouter.Router.FixedGraphReason create(Fixture f,
                    PcbNetRouter.Router router, QuerySpec spec) {
                ExpectedReason e = baseReason(f, router, spec, site,
                    "DOMAIN_BOUNDARY", 1, spec.endPadId);
                e.ownerIndex = 0; e.domainLow = low; e.domainHigh = high;
                e.domainHorizontal = horizontal; e.domainLowLimit = lowLimit;
                e.domainHighLimit = highLimit; e.domainFirst = first; e.domainLast = last;
                return e.build();
            }
        };
    }

    private static ReasonFactory component(final String ownerId, final int stage,
            final int startEscape, final int endEscape, final int underpass) {
        return new ReasonFactory() {
            public PcbNetRouter.Router.FixedGraphReason create(Fixture f,
                    PcbNetRouter.Router router, QuerySpec spec) {
                ExpectedReason e = baseReason(f, router, spec,
                    "COMPONENT_COURTYARD", "COMPONENT", stage, spec.endPadId);
                e.ownerId = ownerId; e.ownerNet = null;
                e.ownerIndex = componentIndex(f.layout, ownerId);
                e.startEscape = startEscape; e.endEscape = endEscape;
                e.underpassEnabled = underpass;
                return e.build();
            }
        };
    }

    private static ExpectedReason baseReason(Fixture f, PcbNetRouter.Router router,
            QuerySpec spec, String site, String ownerKind, int stage, String endpoint) {
        ExpectedReason e = new ExpectedReason();
        e.site = site; e.ownerKind = ownerKind; e.stage = stage;
        e.startPadId = f.start.getPadId(); e.endpointPadId = endpoint;
        e.fromGridX = gridX(router, spec.fromX); e.fromGridY = gridY(router, spec.fromY);
        e.toGridX = gridX(router, spec.toX); e.toGridY = gridY(router, spec.toY);
        return e;
    }

    private static FixtureFactory p6Fixture(final boolean courtyard, final boolean escaped) {
        return new FixtureFactory() {
            public Fixture create() { return p6View(courtyard, escaped, false); }
        };
    }

    private static FixtureFactory multiEndpointFixture() {
        return new FixtureFactory() {
            public Fixture create() { return p6View(false, false, true); }
        };
    }

    private static Fixture p6View(boolean courtyard, boolean escaped, boolean multi) {
        try {
            Class<?> owner = Class.forName(
                "com.lushprojects.circuitjs1.client.CanonicalPrefixStageInspector");
            Method method = owner.getDeclaredMethod(multi ? "multiEndpointFixture" :
                "blockerCanaryFixture", multi ? new Class<?>[0] :
                    new Class<?>[] {Boolean.TYPE, Boolean.TYPE});
            method.setAccessible(true);
            Object view = multi ? method.invoke(null) : method.invoke(null,
                Boolean.valueOf(courtyard), Boolean.valueOf(escaped));
            Fixture f = new Fixture();
            f.board = (TroubleshootBoard)readField(view, "board");
            f.layout = (PcbBoardLayout)readField(view, "layout");
            f.start = (PcbPadPlacement)readField(view, "start");
            f.end = (PcbPadPlacement)readField(view, "root");
            f.reached = (PcbPadPlacement[])readField(view, "reached");
            f.router = (PcbNetRouter.Router)readField(view, "router");
            f.geometryBefore = (String)readField(view, "geometryBefore");
            f.effectsBefore = (String)readField(view, "effectsBefore");
            return f;
        } catch (Throwable failure) { rethrow(failure); return null; }
    }

    private static FixtureFactory endEscapeFixture() {
        return new FixtureFactory() {
            public Fixture create() {
                PhysicalPackage pkg = canaryPointPackage();
                TroubleshootBoard board = new TroubleshootBoard("P8_END_ESCAPE_LITERAL");
                board.addNet(new BoardNet("QUERY"));
                PcbBoardLayout layout = new PcbBoardLayout(800, 600,
                    new Rectangle(100, 100, 400, 400), new Rectangle(550, 120, 180, 300));
                addManualCanaryPad(board, layout, pkg, "Q0", "QUERY", 200, 250, 0, 0, 0);
                addManualCanaryPad(board, layout, pkg, "Q1", "QUERY", 310, 250, 1, 0, 30);
                board.setPlacementConstraints(PcbPlacementConstraints.standard(board));
                Fixture f = new Fixture();
                f.board = board; f.layout = layout; f.start = layout.getPad("Q0.1");
                f.end = layout.getPad("Q1.1");
                f.router = router(layout, board, layout.getBoardOutline());
                f.captureBaseline(); return f;
            }
        };
    }

    private static FixtureFactory cacheFixture(final CacheOwner owner,
            final boolean adjacent) {
        return new FixtureFactory() {
            public Fixture create() {
                PhysicalPackage pkg = canaryPointPackage();
                TroubleshootBoard board = new TroubleshootBoard("P8_CACHE_LITERAL");
                board.addNet(new BoardNet("QUERY"));
                board.addNet(new BoardNet("A"));
                board.addNet(new BoardNet("AB"));
                board.addNet(new BoardNet("COURT"));
                PcbBoardLayout layout = new PcbBoardLayout(800, 600,
                    new Rectangle(100, 100, 400, 400), new Rectangle(550, 120, 180, 300));
                addBareManualPad(board, layout, pkg, "Q0", "QUERY", 200, 260, 0, 0, 0);
                addBareManualPad(board, layout, pkg, "Q1", "QUERY", 400, 260, 0, 0, 0);
                if (owner == CacheOwner.SAME)
                    addBareManualPad(board, layout, pkg, "S0", "QUERY", 300, 250, 0, 0, 0);
                if (owner == CacheOwner.SINGLE || owner == CacheOwner.MIXED)
                    addBareManualPad(board, layout, pkg, "A0", "A", 300, 250, 0, 0, 0);
                if (owner == CacheOwner.MIXED)
                    addBareManualPad(board, layout, pkg, "AB0", "AB", 300, 270, 0, 0, 0);
                if (adjacent) {
                    BoardComponent c0 = new BoardComponent("C0", pkg.getId(), pkg, "C0");
                    board.addComponent(c0);
                    layout.addComponent(PcbComponentPlacement.fromPhysicalGeometry("C0",
                        new PcbPackagePose(350, 350, PcbRotation.DEG_0, PcbBoardSide.TOP),
                        pkg, pkg.getGeometry()));
                }
                board.setPlacementConstraints(PcbPlacementConstraints.standard(board));
                Fixture f = new Fixture(); f.board = board; f.layout = layout;
                f.start = layout.getPad("Q0.1"); f.end = layout.getPad("Q1.1");
                f.router = router(layout, board, layout.getBoardOutline());
                f.captureBaseline(); return f;
            }
        };
    }

    private static FixtureFactory domainRouterFixture() {
        return new FixtureFactory() {
            public Fixture create() {
                DomainFixture domain = buildDomainFixture();
                Fixture f = new Fixture(); f.board = domain.board; f.layout = domain.layout;
                f.start = f.layout.getPad("P1.1"); f.end = f.layout.getPad("P2.1");
                f.router = router(f.layout, f.board, f.layout.getBoardOutline());
                f.captureBaseline(); return f;
            }
        };
    }

    private static DomainFixture domainFixture() { return buildDomainFixture(); }

    private static DomainFixture buildDomainFixture() {
        TroubleshootBoard board = new TroubleshootBoard("P8_DOMAIN_LITERAL");
        board.addNet(new BoardNet("PRIMARY")); board.addNet(new BoardNet("SECONDARY"));
        PcbBoardLayout layout = new PcbBoardLayout(1100, 650,
            new Rectangle(100, 100, 600, 400), new Rectangle(820, 120, 200, 400));
        addDomainPoint(board, layout, "P1", "PRIMARY", 170, 200);
        addDomainPoint(board, layout, "P2", "PRIMARY", 270, 200);
        addDomainPoint(board, layout, "S1", "SECONDARY", 530, 200);
        addDomainPoint(board, layout, "S2", "SECONDARY", 630, 200);
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        for (String id : board.getComponentIds()) {
            String domain = id.startsWith("P") ? "PRIMARY" : "SECONDARY";
            parts.add(new PcbPlacementConstraints.Part(id, domain, domain, domain,
                PcbPlacementConstraints.Anchor.NONE, 20));
        }
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 80));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts, barriers));
        board.validate(); layout.validateAgainst(board);
        DomainFixture f = new DomainFixture(); f.board = board; f.layout = layout;
        f.rules = new PcbTwoLayerRules(board, layout);
        return f;
    }

    private static void addDomainPoint(TroubleshootBoard board, PcbBoardLayout layout,
            String id, String net, int x, int y) {
        PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
        PcbPackagePose reference = new PcbPackagePose(100, 100, PcbRotation.DEG_0,
            PcbBoardSide.BOTTOM);
        Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(100 + x - local.x,
            100 + y - local.y, PcbRotation.DEG_0, PcbBoardSide.BOTTOM);
        BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
        board.addComponent(component); board.addPad(new BoardPad(id + ".1", id, "1", net));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
            physical.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static FixtureFactory profiledTouchFixture(final boolean separated) {
        return new FixtureFactory() {
            public Fixture create() {
                TroubleshootBoard board = new TroubleshootBoard(
                    "ROLE_WIDTH_NONADJACENT_PAD_TOUCH");
                board.addNet(new BoardNet("HIGH", BoardNet.RoutingRole.HIGH_CURRENT));
                board.addNet(new BoardNet("SIGNAL", BoardNet.RoutingRole.SIGNAL));
                PhysicalPackage pkg = PhysicalPackage.developerPackageWithGenericGeometry(
                    "ROLE_WIDTH_NONADJACENT_PAD_TOUCH_PACKAGE", singleton("1"),
                    new Vector<String>(), false);
                addBareComponent(board, "H1", "HIGH", pkg);
                addBareComponent(board, "H2", "HIGH", pkg);
                addBareComponent(board, "S", "SIGNAL", pkg);
                Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
                for (String id : board.getComponentIds())
                    parts.add(new PcbPlacementConstraints.Part(id, "route-test", "Route test",
                        "board", PcbPlacementConstraints.Anchor.NONE, 20));
                board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                    new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                    PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                    PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                    PcbTraceWidthProfile.ROLE_WIDTHS));
                board.validate();
                PcbBoardLayout layout = new PcbBoardLayout(600, 450,
                    new Rectangle(100, 100, 400, 300), new Rectangle(520, 120, 60, 250));
                PcbPadPlacement start = manualProfilePad("H1.1", 200, 220);
                PcbPadPlacement end = manualProfilePad("H2.1", 300, 220);
                PcbPadPlacement foreign = separated ? manualProfilePad("S.1", 261, 241) :
                    manualProfilePad("S.1", 260, 240);
                layout.addPad(start); layout.addPad(end); layout.addPad(foreign);
                Fixture f = new Fixture(); f.board = board; f.layout = layout;
                f.start = start; f.end = end;
                f.router = router(layout, board, layout.getBoardOutline());
                f.captureBaseline(); return f;
            }
        };
    }

    private static FixtureFactory underpassFixture(final boolean enabled) {
        return new FixtureFactory() {
            public Fixture create() {
                try {
                    Class<?> type = Class.forName(
                        "com.lushprojects.circuitjs1.client.PcbRoutingCourtyardIndexContractTest$Fixture");
                    Constructor<?> constructor = type.getDeclaredConstructor(PcbTraceWidthProfile.class);
                    constructor.setAccessible(true);
                    Object source = constructor.newInstance(PcbTraceWidthProfile.UNIFORM);
                    Fixture f = new Fixture();
                    f.board = (TroubleshootBoard)readField(source, "board");
                    f.layout = (PcbBoardLayout)readField(source, "layout");
                    f.start = (PcbPadPlacement)readField(source, "highStart");
                    f.end = (PcbPadPlacement)readField(source, "highEnd");
                    Rectangle outline = (Rectangle)readField(source, "outline");
                    f.router = router(f.layout, f.board, outline);
                    f.router.factoryUnderpasses = enabled;
                    f.captureBaseline(); return f;
                } catch (Throwable failure) { rethrow(failure); return null; }
            }
        };
    }

    private static PcbNetRouter.Router router(PcbBoardLayout layout,
            TroubleshootBoard board, Rectangle outline) {
        return new PcbNetRouter.Router(layout, board, outline, 0, NOOP, PcbCopperLayer.TOP);
    }

    private static void addBareManualPad(TroubleshootBoard board, PcbBoardLayout layout,
            PhysicalPackage pkg, String componentId, String net, int x, int y,
            int dx, int dy, int escape) {
        BoardComponent component = new BoardComponent(componentId, pkg.getId(), pkg, componentId);
        board.addComponent(component);
        board.addPad(new BoardPad(componentId + ".1", componentId, "1", net));
        layout.addPad(new PcbPadPlacement(componentId + ".1", x, y, dx, dy, escape,
            new Rectangle(x - 4, y - 4, 8, 8), new Rectangle(x - 6, y - 6, 12, 12),
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP));
    }

    private static void addManualCanaryPad(TroubleshootBoard board, PcbBoardLayout layout,
            PhysicalPackage pkg, String componentId, String net, int x, int y,
            int dx, int dy, int escape) {
        addBareManualPad(board, layout, pkg, componentId, net, x, y, dx, dy, escape);
    }

    private static void addBareComponent(TroubleshootBoard board, String id, String net,
            PhysicalPackage pkg) {
        board.addComponent(new BoardComponent(id, pkg.getId(), pkg, id));
        board.addPad(new BoardPad(id + ".1", id, "1", net));
    }

    private static PcbPadPlacement manualProfilePad(String id, int x, int y) {
        return new PcbPadPlacement(id, x, y, 0, 0, 0,
            new Rectangle(x - 13, y - 13, 26, 26),
            new Rectangle(x - 17, y - 17, 34, 34),
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
    }

    private static PhysicalPackage canaryPointPackage() {
        try {
            Class<?> owner = Class.forName(
                "com.lushprojects.circuitjs1.client.CanonicalPrefixStageInspector");
            Method method = owner.getDeclaredMethod("canaryPointPackage");
            method.setAccessible(true);
            return (PhysicalPackage)method.invoke(null);
        } catch (Throwable failure) { rethrow(failure); return null; }
    }

    private static Vector<String> singleton(String value) {
        Vector<String> result = new Vector<String>(); result.add(value); return result;
    }

    private static CopyResult copyBounded(PcbNetRouter.Router source) {
        CopyResult result = new CopyResult();
        PcbNetRouter.Router.FixedGraphCopyTask task = null;
        try {
            long started = System.nanoTime();
            task = source.beginFixedGraphObservationCopy();
            LEDGER.unit("fixed-graph copy setup", elapsed(started, System.nanoTime()));
            int previous = 0;
            for (;;) {
                started = System.nanoTime();
                boolean done = task.advanceOne(CHUNK);
                int entries = task.copiedEntries();
                long duration = elapsed(started, System.nanoTime());
                LEDGER.unit("fixed-graph copy chunk", duration);
                if (entries < previous || entries > MAX_CLONE_ENTRIES)
                    throw new IllegalStateException("P8 copy exceeds the frozen entry bound");
                result.entries = entries;
                if (done) { result.router = task.result(); task = null; return result; }
                previous = entries;
            }
        } finally { task = null; source = null; }
    }

    private static ComparisonResult compareBounded(PcbNetRouter.Router first,
            PcbNetRouter.Router second) {
        ComparisonResult result = new ComparisonResult();
        PcbNetRouter.Router.FixedGraphComparisonTask task = null;
        try {
            long started = System.nanoTime();
            task = first.beginFixedGraphComparison(second);
            LEDGER.unit("fixed-graph comparison setup", elapsed(started, System.nanoTime()));
            int previous = 0;
            for (;;) {
                started = System.nanoTime();
                boolean done = task.advanceOne(CHUNK);
                int entries = task.comparedEntries();
                long duration = elapsed(started, System.nanoTime());
                LEDGER.unit("fixed-graph comparison chunk", duration);
                if (entries < previous)
                    throw new IllegalStateException("P8 comparison cursor regressed");
                result.entries = entries;
                if (done) { result.equal = task.equal(); task = null; return result; }
                previous = entries;
            }
        } finally { task = null; first = null; second = null; }
    }

    private static String reservationAt(PcbNetRouter.Router router, String field,
            int physicalX, int physicalY) {
        try {
            String[][] grid = (String[][])readField(router, field);
            int minX = ((Integer)readField(router, "minX")).intValue();
            int minY = ((Integer)readField(router, "minY")).intValue();
            int gridUnit = ((Integer)readStaticField(PcbNetRouter.class, "GRID")).intValue();
            int gx = (physicalX - minX) / gridUnit;
            int gy = (physicalY - minY) / gridUnit;
            if (grid == null || gx < 0 || gy < 0 || gx >= grid.length ||
                    grid[gx] == null || gy >= grid[gx].length)
                throw new IllegalStateException("P8 literal cache coordinate is outside its grid");
            return grid[gx][gy];
        } catch (Throwable failure) { rethrow(failure); return null; }
    }

    private static boolean booleanField(Object owner, String field) {
        try { return ((Boolean)readField(owner, field)).booleanValue(); }
        catch (Throwable failure) { rethrow(failure); return false; }
    }

    private static int gridX(PcbNetRouter.Router router, int physicalX) {
        return gridCoordinate(router, physicalX, "minX");
    }

    private static int gridY(PcbNetRouter.Router router, int physicalY) {
        return gridCoordinate(router, physicalY, "minY");
    }

    private static int coarseDirection(PcbNetRouter.Router router, QuerySpec spec) {
        int dx = gridX(router, spec.toX) - gridX(router, spec.fromX);
        int dy = gridY(router, spec.toY) - gridY(router, spec.fromY);
        return dx > 0 ? 1 : dx < 0 ? 3 : dy > 0 ? 2 : 0;
    }

    private static int gridCoordinate(PcbNetRouter.Router router, int physical,
            String originField) {
        try {
            int origin = ((Integer)readField(router, originField)).intValue();
            int grid = ((Integer)readStaticField(PcbNetRouter.class, "GRID")).intValue();
            int delta = physical - origin;
            if (delta % grid != 0)
                throw new IllegalStateException("P8 literal query is not aligned to the routing grid");
            return delta / grid;
        } catch (Throwable failure) { rethrow(failure); return 0; }
    }

    private static int padIndex(PcbBoardLayout layout, String id) {
        Vector<PcbPadPlacement> pads = layout.getPads();
        for (int i = 0; i < pads.size(); i++)
            if (id.equals(pads.get(i).getPadId())) return i;
        throw new IllegalStateException("P8 expected pad owner is absent: " + id);
    }

    private static int componentIndex(PcbBoardLayout layout, String id) {
        Vector<PcbComponentPlacement> components = layout.getComponents();
        for (int i = 0; i < components.size(); i++)
            if (id.equals(components.get(i).getComponentId())) return i;
        throw new IllegalStateException("P8 expected component owner is absent: " + id);
    }

    private static Object readField(Object owner, String name) throws Exception {
        if (owner == null) throw new IllegalArgumentException("Missing reflected owner");
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object readStaticField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true);
        return field.get(null);
    }

    private static void expectThrows(String label, Class<? extends Throwable> expected,
            Action action) {
        Throwable actual = null;
        long started = System.nanoTime();
        try { action.run(); } catch (Throwable failure) { actual = failure; }
        LEDGER.unit(label, elapsed(started, System.nanoTime()));
        LEDGER.check(actual != null && actual.getClass() == expected,
            label + " rejects with exact " + expected.getSimpleName());
    }

    private static void finishGroup(String id, long started) {
        if (!LEDGER.caseOpen || started != LEDGER.caseStart)
            throw new IllegalStateException("P8 case group lifecycle is inconsistent at " + id);
        long duration = elapsed(started, System.nanoTime());
        LEDGER.groups++;
        LEDGER.caseGroups++;
        LEDGER.caseOpen = false;
        LEDGER.lastGroupEnd = System.nanoTime();
        if (duration > LEDGER.maximumGroupNanos) LEDGER.maximumGroupNanos = duration;
        if (duration > MAX_GROUP_NANOS)
            throw new IllegalStateException(id + " exceeded its five-second complete-group limit");
        if (LEDGER.groups > 640)
            throw new IllegalStateException("P8 literal canary exceeded its 640-group bound");
    }

    private static long elapsed(long start, long finish) {
        long duration = finish - start;
        return duration < 0L ? Long.MAX_VALUE : duration;
    }

    private static void checkNoCongestion(String label, PcbNetRouter.Router router) {
        PcbRoutingWork.Statistics work = router.work.snapshot();
        LEDGER.check(router.congestionRejections == 0 && work.congestionRejections == 0 &&
            router.conflicts.isEmpty(), label + " has zero congestion/conflict deltas");
    }

    private static void validateFrozenRows(List<CaseRow> rows) {
        final String r = "SYNTHETIC_ROUTER_LITERAL";
        final String i = "INVALID_RULES_INPUT";
        final String d = "RULES_BOUNDARY_LITERAL";
        final String l = "OBSERVER_LIFECYCLE";
        String[] ids = {
            "START_ESCAPE_REJECT", "START_ESCAPE_ALLOW", "END_ESCAPE_REJECT",
            "END_ESCAPE_ALLOW", "EMPTY_UNRESERVED", "EMPTY_SAME_OWNER",
            "EMPTY_SINGLE_OWNER", "EMPTY_MIXED_OWNER", "ADJACENT_SAME_OWNER",
            "ADJACENT_SINGLE_OWNER", "ADJACENT_MIXED_OWNER", "RULES_UNKNOWN_NET",
            "RULES_CORRIDOR", "RULES_MEMBERSHIP", "ROUTER_CORRIDOR",
            "ROUTER_MEMBERSHIP", "ROUTER_DOMAIN_ALLOW", "PAD_ESCAPE_REJECT",
            "PAD_ESCAPE_ALLOW", "PROFILED_TOUCH_REJECT", "PROFILED_TOUCH_ALLOW",
            "COURTYARD_REJECT", "COURTYARD_ALLOW", "UNDERPASS_ALLOW",
            "UNDERPASS_DISABLED", "EMPTY_REACHED_ALLOW", "MULTI_ENDPOINT_ORDER",
            "REASON_THROW", "ATTACHMENT_ROLES", "COPY_DEFAULT_NULL", "NULL_PARITY"
        };
        String[] scopes = {
            r,r,r,r,r,r,r,r,r,r,r,i,d,d,r,r,r,r,r,r,r,r,r,r,r,r,r,l,l,l,l
        };
        String[] expected = {
            "START_ESCAPE_DIRECTION", null, "END_ESCAPE_DIRECTION", null,
            null, null, "EMPTY_FACE_RESERVATION", "EMPTY_FACE_RESERVATION",
            null, "ADJACENT_RESERVATION", "ADJACENT_RESERVATION", "DOMAIN_UNKNOWN_NET",
            "DOMAIN_CORRIDOR_OVERLAP", "DOMAIN_MEMBERSHIP_SIDE",
            "DOMAIN_CORRIDOR_OVERLAP", "DOMAIN_MEMBERSHIP_SIDE", null,
            "PAD_ESCAPE_INTERSECTION", null, "PROFILED_PAD_TOUCH", null,
            "COMPONENT_COURTYARD", null, null, "COMPONENT_COURTYARD", null,
            "END_ESCAPE_DIRECTION", "START_ESCAPE_DIRECTION", null, null, null
        };
        String[][] actualSites = {
            {"START_ESCAPE_DIRECTION"}, {}, {"END_ESCAPE_DIRECTION"}, {},
            {}, {}, {"EMPTY_FACE_RESERVATION"}, {"EMPTY_FACE_RESERVATION"},
            {}, {"ADJACENT_RESERVATION"}, {"ADJACENT_RESERVATION"},
            {"DOMAIN_UNKNOWN_NET"}, {"DOMAIN_CORRIDOR_OVERLAP"},
            {"DOMAIN_MEMBERSHIP_SIDE"}, {"DOMAIN_CORRIDOR_OVERLAP"},
            {"DOMAIN_MEMBERSHIP_SIDE"}, {}, {"PAD_ESCAPE_INTERSECTION"}, {},
            {"PROFILED_PAD_TOUCH"}, {}, {"COMPONENT_COURTYARD"}, {}, {},
            {"COMPONENT_COURTYARD"}, {},
            {"END_ESCAPE_DIRECTION", "END_ESCAPE_DIRECTION"},
            {"START_ESCAPE_DIRECTION"}, {}, {}, {}
        };
        int[] fine = {1,0,1,0,0,0,1,1,0,1,1,1,1,1,1,1,0,1,0,1,0,1,0,0,1,0,2,1,0,0,0};
        int[] coarse = {1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,3,0,0,0,1};
        LEDGER.check(rows.size() == 31 && ids.length == 31 && scopes.length == 31 &&
            expected.length == 31 && actualSites.length == 31 && fine.length == 31 &&
            coarse.length == 31 && LEDGER.caseGroups == 31,
            "frozen P8 literal ledger has 31 cases and 31 completed case groups");
        for (int index = 0; index < rows.size() && index < ids.length; index++) {
            CaseRow row = rows.get(index);
            ArrayList<String> expectedSites = new ArrayList<String>();
            for (String site : actualSites[index]) expectedSites.add(site);
            LEDGER.check(row.id.equals(ids[index]) && row.scope.equals(scopes[index]) &&
                equal(row.expectedSite, expected[index]) && row.actualSites.equals(expectedSites) &&
                row.fineEvents == fine[index] && row.coarseEvents == coarse[index],
                "frozen P8 ledger row " + index + " matches id/scope/site sequence/counts");
            LEDGER.check(row.sourcePreserved && row.observersDetached && row.referencesDropped,
                "frozen P8 ledger row " + row.id + " includes all three completed proofs");
        }
    }

    private static ArrayList<String> siteList(
            List<PcbNetRouter.Router.FixedGraphReason> reasons) {
        ArrayList<String> result = new ArrayList<String>();
        for (PcbNetRouter.Router.FixedGraphReason reason : reasons) result.add(reason.site);
        return result;
    }

    private static String outputJson(List<CaseRow> rows, long workNanos,
            long maximumGroupNanos, long units) {
        StringBuilder out = new StringBuilder(18000);
        out.append("{\"schema\":").append(quote(SCHEMA))
            .append(",\"status\":\"PASS\"")
            .append(",\"assertions\":").append(LEDGER.assertions)
            .append(",\"caseCount\":").append(rows.size())
            .append(",\"workNanos\":").append(workNanos)
            .append(",\"groups\":").append(LEDGER.groups + 1)
            .append(",\"maximumGroupNanos\":").append(maximumGroupNanos)
            .append(",\"totalObservationUnits\":").append(units)
            .append(",\"cases\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i != 0) out.append(',');
            CaseRow row = rows.get(i);
            out.append("{\"id\":").append(quote(row.id))
                .append(",\"status\":\"PASS\"")
                .append(",\"scope\":").append(quote(row.scope))
                .append(",\"expectedSite\":").append(row.expectedSite == null ? "null" :
                    quote(row.expectedSite))
                .append(",\"actualSites\":[");
            for (int j = 0; j < row.actualSites.size(); j++) {
                if (j != 0) out.append(',');
                out.append(quote(row.actualSites.get(j)));
            }
            out.append(']')
                .append(",\"fineEvents\":").append(row.fineEvents)
                .append(",\"coarseEvents\":").append(row.coarseEvents)
                .append(",\"sourcePreserved\":").append(row.sourcePreserved)
                .append(",\"observersDetached\":").append(row.observersDetached)
                .append(",\"referencesDropped\":").append(row.referencesDropped)
                .append('}');
        }
        return out.append("],\"layoutQualification\":false}").toString();
    }

    private static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 0x20) {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        return out.append('"').toString();
    }

    private static boolean equal(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void rethrow(Throwable failure) {
        while ((failure instanceof java.lang.reflect.InvocationTargetException) &&
                ((java.lang.reflect.InvocationTargetException)failure).getCause() != null)
            failure = ((java.lang.reflect.InvocationTargetException)failure).getCause();
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new IllegalStateException("P8 literal canary operation failed", failure);
    }

    private enum CacheOwner { NONE, SAME, SINGLE, MIXED }

    private interface FixtureFactory { Fixture create(); }
    private interface ReasonFactory {
        PcbNetRouter.Router.FixedGraphReason create(Fixture fixture,
            PcbNetRouter.Router router, QuerySpec spec);
    }
    private interface QueryVerifier {
        void verify(Fixture fixture, PcbNetRouter.Router query, PcbNetRouter.Router parity);
    }
    private interface Action { void run(); }

    private static final class Fixture {
        TroubleshootBoard board;
        PcbBoardLayout layout;
        PcbNetRouter.Router router;
        PcbPadPlacement start, end;
        PcbPadPlacement[] reached;
        String geometryBefore, effectsBefore;
        void captureBaseline() {
            geometryBefore = layout.geometryFingerprint();
            effectsBefore = router.fixedGraphObservationEffects();
        }
        void drop() {
            board = null; layout = null; router = null; start = null; end = null; reached = null;
            geometryBefore = null; effectsBefore = null;
        }
    }

    private static final class DomainFixture {
        TroubleshootBoard board;
        PcbBoardLayout layout;
        PcbTwoLayerRules rules;
        void drop() { board = null; layout = null; rules = null; }
    }

    private static final class QuerySpec {
        String id;
        final String net, startPadId, endPadId;
        final int fromX, fromY, toX, toY;
        final boolean reachedSupplied, useFixtureReached, allowed, throwReason;
        final String expectedSite;
        final ReasonFactory[] reasons;
        final CoarseExpectation[] coarse;
        QueryVerifier verifier;
        QuerySpec(String id, String net, String startPadId, String endPadId,
                int fromX, int fromY, int toX, int toY, boolean reachedSupplied,
                boolean useFixtureReached, boolean allowed, boolean throwReason,
                String expectedSite, ReasonFactory[] reasons,
                CoarseExpectation[] coarse, QueryVerifier verifier) {
            this.id = id; this.net = net; this.startPadId = startPadId;
            this.endPadId = endPadId; this.fromX = fromX; this.fromY = fromY;
            this.toX = toX; this.toY = toY; this.reachedSupplied = reachedSupplied;
            this.useFixtureReached = useFixtureReached; this.allowed = allowed;
            this.throwReason = throwReason; this.expectedSite = expectedSite;
            this.reasons = reasons; this.coarse = coarse; this.verifier = verifier;
        }
    }

    private static final class ExpectedReason {
        String site, ownerKind;
        int stage;
        String startPadId, endpointPadId;
        int fromGridX, fromGridY, toGridX, toGridY;
        String ownerId, ownerNet;
        int ownerIndex = -1;
        String reservationState, reservationValue;
        String domainLow, domainHigh;
        int domainHorizontal = -1, domainLowLimit, domainHighLimit, domainFirst, domainLast;
        int startEscape = -1, endEscape = -1, underpassEnabled = -1;
        PcbNetRouter.Router.FixedGraphReason build() {
            return new PcbNetRouter.Router.FixedGraphReason(site, ownerKind, stage,
                startPadId, endpointPadId, fromGridX, fromGridY, toGridX, toGridY,
                ownerId, ownerNet, ownerIndex, reservationState, reservationValue,
                domainLow, domainHigh, domainHorizontal, domainLowLimit, domainHighLimit,
                domainFirst, domainLast, startEscape, endEscape, underpassEnabled);
        }
    }

    private static final class ReasonCapture
            implements PcbNetRouter.Router.FixedGraphReasonObserver {
        final ArrayList<PcbNetRouter.Router.FixedGraphReason> events =
            new ArrayList<PcbNetRouter.Router.FixedGraphReason>();
        final ArrayList<OrderEvent> order;
        final Throwable sentinel;
        ReasonCapture(Throwable sentinel) { this(sentinel, null); }
        ReasonCapture(Throwable sentinel, ArrayList<OrderEvent> order) {
            this.sentinel = sentinel; this.order = order;
        }
        public void onRejected(PcbNetRouter.Router.FixedGraphReason reason) {
            events.add(reason);
            if (order != null) order.add(OrderEvent.fine(reason));
            if (sentinel != null) throw (RuntimeException)sentinel;
        }
        void clear() { events.clear(); if (order != null) order.clear(); }
    }

    private static final class StageCapture
            implements PcbNetRouter.Router.FixedGraphStageObserver {
        final ArrayList<CoarseEvent> events = new ArrayList<CoarseEvent>();
        final ArrayList<OrderEvent> order = new ArrayList<OrderEvent>();
        public void onEndpoint(String netId, String startPadId, String endpointPadId,
                int fromGridX, int fromGridY, int toGridX, int toGridY,
                int direction, int stage) {
            events.add(new CoarseEvent(netId, startPadId, endpointPadId,
                fromGridX, fromGridY, toGridX, toGridY, direction, stage));
            order.add(OrderEvent.coarse(startPadId, endpointPadId,
                fromGridX, fromGridY, toGridX, toGridY, stage));
        }
        void clear() { events.clear(); order.clear(); }
    }

    private static final class CoarseEvent {
        final String netId, startPadId, endpointPadId;
        final int fromGridX, fromGridY, toGridX, toGridY, direction, stage;
        CoarseEvent(String netId, String startPadId, String endpointPadId,
                int fromGridX, int fromGridY, int toGridX, int toGridY,
                int direction, int stage) {
            this.netId = netId; this.startPadId = startPadId;
            this.endpointPadId = endpointPadId; this.fromGridX = fromGridX;
            this.fromGridY = fromGridY; this.toGridX = toGridX; this.toGridY = toGridY;
            this.direction = direction; this.stage = stage;
        }
    }

    private static final class OrderEvent {
        final boolean fine;
        final String site, endpointPadId;
        final int stage, fromGridX, fromGridY, toGridX, toGridY;
        private OrderEvent(boolean fine, String site, String endpointPadId, int stage,
                int fromGridX, int fromGridY, int toGridX, int toGridY) {
            this.fine = fine; this.site = site; this.endpointPadId = endpointPadId;
            this.stage = stage; this.fromGridX = fromGridX; this.fromGridY = fromGridY;
            this.toGridX = toGridX; this.toGridY = toGridY;
        }
        static OrderEvent fine(PcbNetRouter.Router.FixedGraphReason reason) {
            return new OrderEvent(true, reason.site, reason.endpointPadId, reason.stage,
                reason.fromGridX, reason.fromGridY, reason.toGridX, reason.toGridY);
        }
        static OrderEvent coarse(String start, String endpoint, int fromX, int fromY,
                int toX, int toY, int stage) {
            return new OrderEvent(false, null, endpoint, stage, fromX, fromY, toX, toY);
        }
    }

    private static final class CoarseExpectation {
        final String endpointPadId;
        final int stage;
        CoarseExpectation(String endpointPadId, int stage) {
            this.endpointPadId = endpointPadId; this.stage = stage;
        }
    }
    private static CoarseExpectation coarse(String endpoint, int stage) {
        return new CoarseExpectation(endpoint, stage);
    }

    private static final class QueryOutcome {
        boolean allowed;
        Throwable failure;
    }
    private static final class CopyResult {
        PcbNetRouter.Router router;
        int entries;
    }
    private static final class ComparisonResult {
        boolean equal;
        int entries;
    }

    private static final class RuleCapture implements PcbTwoLayerRules.ReasonObserver {
        final ArrayList<RuleEvent> events = new ArrayList<RuleEvent>();
        public void onRejected(String site, int boundaryIndex, String low, String high,
                boolean horizontal, int lowLimit, int highLimit, int first, int last) {
            events.add(new RuleEvent(site, boundaryIndex, low, high, horizontal,
                lowLimit, highLimit, first, last));
        }
        void clear() { events.clear(); }
    }
    private static final class RuleEvent {
        final String site, low, high;
        final int boundaryIndex, lowLimit, highLimit, first, last;
        final boolean horizontal;
        RuleEvent(String site, int boundaryIndex, String low, String high,
                boolean horizontal, int lowLimit, int highLimit, int first, int last) {
            this.site = site; this.boundaryIndex = boundaryIndex; this.low = low;
            this.high = high; this.horizontal = horizontal; this.lowLimit = lowLimit;
            this.highLimit = highLimit; this.first = first; this.last = last;
        }
    }

    private static final class CaseRow {
        final String id, scope, expectedSite;
        final ArrayList<String> actualSites = new ArrayList<String>();
        int fineEvents, coarseEvents;
        boolean sourcePreserved, observersDetached, referencesDropped;
        CaseRow(String id, String scope, String expectedSite) {
            this.id = id; this.scope = scope; this.expectedSite = expectedSite;
        }
    }

    private static final class Ledger {
        long assertions, units, groups, caseGroups, maximumGroupNanos;
        long lastGroupEnd, caseStart;
        boolean caseOpen;
        void start(long started) {
            assertions = units = groups = caseGroups = maximumGroupNanos = 0L;
            lastGroupEnd = started; caseStart = 0L; caseOpen = false;
        }
        long beginCaseGroup() {
            if (caseOpen) return caseStart;
            long now = System.nanoTime();
            recordMetadataGap("inter-case construction/row bookkeeping", now);
            caseStart = now; caseOpen = true;
            return caseStart;
        }
        long beginReservedGroup() {
            if (caseOpen) throw new IllegalStateException("P8 output began inside a case group");
            long now = System.nanoTime();
            recordMetadataGap("final case-to-output bookkeeping", now);
            return System.nanoTime();
        }
        private void recordMetadataGap(String label, long now) {
            long gap = elapsed(lastGroupEnd, now);
            groups++;
            unit(label, gap);
            if (gap > maximumGroupNanos) maximumGroupNanos = gap;
            if (gap > MAX_GROUP_NANOS)
                throw new IllegalStateException("P8 metadata gap exceeded five seconds: " + label);
            lastGroupEnd = now;
        }
        void finishReservedGroup(long started, long duration) {
            long measured = elapsed(started, System.nanoTime());
            groups++;
            if (duration < 0L || duration > MAX_GROUP_NANOS ||
                    measured < 0L || measured > MAX_GROUP_NANOS)
                throw new IllegalStateException("P8 final output/exit group exceeded five seconds");
            if (measured > maximumGroupNanos) maximumGroupNanos = measured;
            if (groups > 640)
                throw new IllegalStateException("P8 literal canary exceeded its 640-group bound");
            lastGroupEnd = System.nanoTime();
        }
        void check(boolean condition, String message) {
            assertions++;
            if (!condition) throw new IllegalStateException("P8 literal canary: " + message);
        }
        void unit(String label, long duration) {
            units++;
            if (units > MAX_OBSERVATION_UNITS)
                throw new IllegalStateException("P8 observation-unit bound exceeded at " + label);
            if (duration < 0L || duration > MAX_GROUP_NANOS)
                throw new IllegalStateException("P8 measured unit outside five-second bound: " + label);
        }
    }
}
