package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;

/** Fixed native canaries for nullable P07 terminal-branch observations. */
public final class P07BranchDiagnosticsContractTest {
    private static int checks;

    private P07BranchDiagnosticsContractTest() { }

    public static void main(String[] args) {
        enabledAndNullSinksPreserveAcceptedRoute();
        totalLimitIsReportedAtTheBranchBoundary();
        sinkAbortIsLatchedAndRequestLocal();
        System.out.println("PASS: P07 branch diagnostics contracts assertions=" + checks);
    }

    private static void enabledAndNullSinksPreserveAcceptedRoute() {
        P06FactoryLinkFixtures.Fixture plain = fixture();
        P06FactoryLinkFixtures.Fixture observed = fixture();
        String plainInput=plain.layout.geometryFingerprint();
        String observedInput=observed.layout.geometryFingerprint();
        PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        PcbLayerRoutingPrototype.Result expected=PcbLayerRoutingPrototype.route(plain.board,
            plain.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        final List<PcbLayerRoutingPrototype.BranchSnapshot> snapshots=
            new ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(observed.board,
            observed.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,policy.vias,
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    snapshots.add(snapshot);
                }
            });
        PcbLayerRoutingPrototype.Result actual=drain(session);

        require(expected.accepted() && actual.accepted(),
            "real P06 P07 control route succeeds with and without observation");
        sameResult(expected,actual,"enabled sink preserves route result and exact output geometry");
        require(plain.layout.geometryFingerprint().equals(plainInput) &&
            observed.layout.geometryFingerprint().equals(observedInput),
            "both normal and observed routing leave the caller's placement unchanged");
        require(!snapshots.isEmpty(),"observed route records terminal branch events");

        int found=0,previousTotal=0;
        int[] previousPrefixByPass={-1,-1,-1};
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:snapshots) {
            require(snapshot.pass>=0 && snapshot.pass<PcbLayerRoutingPrototype.ORDERINGS &&
                notEmpty(snapshot.netId) && notEmpty(snapshot.startPadId) &&
                notEmpty(snapshot.rootPadId),"terminal branch identity is complete");
            require(snapshot.gridWidth>0 && snapshot.gridHeight>0 &&
                (long)snapshot.gridWidth*snapshot.gridHeight<=PcbLayerRoutingPrototype.MAX_CELLS &&
                snapshot.goalsTop+snapshot.goalsBottom>0,
                "terminal branch records actual bounded grid and face goals");
            int netPadCount=observed.board.getNet(snapshot.netId).getPadIds().size();
            require(snapshot.prefixViaCount>=0 && snapshot.prefixViaCount<=policy.vias &&
                snapshot.reachedPadCount>=1 && snapshot.reachedPadCount<=netPadCount,
                "terminal branch records the bounded published-via prefix and connected tree size");
            require(previousPrefixByPass[snapshot.pass]<0 ||
                snapshot.prefixViaCount>=previousPrefixByPass[snapshot.pass],
                "published-via prefix does not decrease within a routing pass");
            previousPrefixByPass[snapshot.pass]=snapshot.prefixViaCount;
            require(snapshot.initialManhattanDistance>=0 &&
                (snapshot.branchExpanded==0 ? snapshot.minimumExploredManhattanDistance==-1 :
                    snapshot.minimumExploredManhattanDistance>=0),
                "Manhattan witnesses describe only the initial and actually expanded states");
            require(snapshot.branchExpanded==snapshot.expandedTop+snapshot.expandedBottom &&
                snapshot.totalExpanded>=snapshot.branchExpanded &&
                snapshot.totalExpanded>=previousTotal && snapshot.frontierCount>=0 &&
                snapshot.bestStateCount>=0,
                "face census, cumulative expansions and frontier counts are consistent");
            previousTotal=snapshot.totalExpanded;
            if("FOUND".equals(snapshot.terminalReason)) {
                found++;
                require(snapshot.branchExpanded>0,
                    "FOUND is emitted only after expanding its actual goal state");
            } else require(isTerminalReason(snapshot.terminalReason),
                "every event names a real search terminal reason");
        }
        require(found>0 && previousTotal<=actual.expansions,
            "at least one real branch reaches FOUND and its cumulative count fits the result");
    }

    private static void totalLimitIsReportedAtTheBranchBoundary() {
        P06FactoryLinkFixtures.Fixture plain=fixture();
        P06FactoryLinkFixtures.Fixture observed=fixture();
        String before=observed.layout.geometryFingerprint();
        PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        PcbLayerRoutingPrototype.Result expected=PcbLayerRoutingPrototype.route(plain.board,
            plain.layout,policy,P06FactoryLinkFixtures.OBSERVER,1,policy.vias);
        final List<PcbLayerRoutingPrototype.BranchSnapshot> snapshots=
            new ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(observed.board,
            observed.layout,policy,P06FactoryLinkFixtures.OBSERVER,1,policy.vias,
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    snapshots.add(snapshot);
                }
            });
        PcbLayerRoutingPrototype.Result actual=drain(session);
        sameResult(expected,actual,"single-expansion cap outcome is sink/null invariant");
        require(!actual.accepted() && actual.expansions==1 && snapshots.size()==1,
            "real capped search emits one terminal branch without changing its expansion cap");
        PcbLayerRoutingPrototype.BranchSnapshot snapshot=snapshots.get(0);
        require("TOTAL_SEARCH_LIMIT".equals(snapshot.terminalReason) &&
            snapshot.branchExpanded==1 && snapshot.totalExpanded==1 &&
            snapshot.expandedTop+snapshot.expandedBottom==1 &&
            snapshot.prefixViaCount>=0 && snapshot.prefixViaCount<=policy.vias &&
            snapshot.reachedPadCount>=1 &&
            snapshot.reachedPadCount<=observed.board.getNet(snapshot.netId).getPadIds().size(),
            "total-search exhaustion is captured before Session handles its typed rejection");
        require(observed.layout.geometryFingerprint().equals(before) &&
            observed.layout.getTraces().isEmpty() && observed.layout.getHoles().isEmpty(),
            "bounded rejected route publishes no partial copper or vias to its input");
    }

    private static void sinkAbortIsLatchedAndRequestLocal() {
        final P06FactoryLinkFixtures.Fixture aborted=fixture();
        P06FactoryLinkFixtures.Fixture baseline=fixture();
        String before=aborted.layout.geometryFingerprint();
        final IllegalStateException abort=new IllegalStateException("diagnostic sink abort");
        final PcbLayerRoutingPrototype.BranchSnapshot[] captured=
            new PcbLayerRoutingPrototype.BranchSnapshot[1];
        final int[] calls={0};
        PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(aborted.board,
            aborted.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,policy.vias,
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    calls[0]++;
                    captured[0]=snapshot;
                    throw abort;
                }
            });
        RuntimeException first=advanceUntilFailure(session);
        require(first==abort && calls[0]==1 && captured[0]!=null,
            "sink exception aborts immediately after exactly one immutable branch event");
        String receipt=signature(captured[0]);
        RuntimeException retry=advanceFailure(session);
        RuntimeException result=readResultFailure(session);
        require(retry==abort && result==abort && calls[0]==1 &&
            signature(captured[0]).equals(receipt),
            "retry and result access rethrow the original abort without a second callback");
        require(aborted.layout.geometryFingerprint().equals(before) &&
            aborted.layout.getTraces().isEmpty() && aborted.layout.getHoles().isEmpty(),
            "aborting an owned route cannot mutate the caller's placement");

        PcbLayerRoutingPrototype.Result afterAbort=PcbLayerRoutingPrototype.route(
            aborted.board,aborted.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        PcbLayerRoutingPrototype.Result expected=PcbLayerRoutingPrototype.route(
            baseline.board,baseline.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        sameResult(expected,afterAbort,"an aborted diagnostic sink cannot leak into the next null-sink request");
        require(afterAbort.accepted() && baseline.layout.getTraces().isEmpty() &&
            aborted.layout.geometryFingerprint().equals(before),
            "next null-sink request succeeds while both caller placements remain owned by callers");
    }

    private static P06FactoryLinkFixtures.Fixture fixture() {
        return new P06FactoryLinkFixtures.Fixture(true,0,0,true);
    }

    private static PcbLayerRoutingPrototype.Result drain(PcbLayerRoutingPrototype.Session session) {
        int slices=0;
        while(!session.advanceSlice())
            if(++slices>2000000) throw new AssertionError("bounded P07 session did not complete");
        return session.result();
    }

    private static RuntimeException advanceUntilFailure(PcbLayerRoutingPrototype.Session session) {
        try {
            int slices=0;
            while(!session.advanceSlice())
                if(++slices>2000000) throw new AssertionError("aborting P07 sink was not reached");
        } catch(RuntimeException failure) { return failure; }
        throw new AssertionError("diagnostic callback should abort its owned request");
    }

    private static RuntimeException advanceFailure(PcbLayerRoutingPrototype.Session session) {
        try { session.advanceSlice(); }
        catch(RuntimeException failure) { return failure; }
        throw new AssertionError("aborted request retry should rethrow its original failure");
    }

    private static RuntimeException readResultFailure(PcbLayerRoutingPrototype.Session session) {
        try { session.result(); }
        catch(RuntimeException failure) { return failure; }
        throw new AssertionError("aborted request cannot produce a route result");
    }

    private static void sameResult(PcbLayerRoutingPrototype.Result expected,
            PcbLayerRoutingPrototype.Result actual,String message) {
        require(expected.accepted()==actual.accepted() &&
            expected.outcome.equals(actual.outcome) && expected.expansions==actual.expansions &&
            expected.orderings==actual.orderings && expected.vias==actual.vias &&
            expected.links==actual.links,message+" accounting parity");
        if(expected.accepted())
            require(expected.layout.geometryFingerprint().equals(actual.layout.geometryFingerprint()) &&
                expected.layout.traceGeometryFingerprint().equals(actual.layout.traceGeometryFingerprint()),
                message+" exact geometry parity");
    }

    private static boolean isTerminalReason(String reason) {
        return "NO_PATH".equals(reason) || "TOTAL_SEARCH_LIMIT".equals(reason) ||
            "BRANCH_SEARCH_LIMIT".equals(reason);
    }

    private static String signature(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        return snapshot.pass+"|"+snapshot.netId+"|"+snapshot.startPadId+"|"+
            snapshot.rootPadId+"|"+snapshot.startX+"|"+snapshot.startY+"|"+
            snapshot.rootX+"|"+snapshot.rootY+"|"+snapshot.gridWidth+"|"+
            snapshot.gridHeight+"|"+snapshot.goalsTop+"|"+snapshot.goalsBottom+"|"+
            snapshot.prefixViaCount+"|"+snapshot.reachedPadCount+"|"+
            snapshot.initialManhattanDistance+"|"+snapshot.minimumExploredManhattanDistance+"|"+
            snapshot.branchExpanded+"|"+snapshot.totalExpanded+"|"+snapshot.expandedTop+"|"+
            snapshot.expandedBottom+"|"+snapshot.frontierCount+"|"+
            snapshot.bestStateCount+"|"+snapshot.terminalReason;
    }

    private static boolean notEmpty(String value) {
        return value!=null && value.length()>0;
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
