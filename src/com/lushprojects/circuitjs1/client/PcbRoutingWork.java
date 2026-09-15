package com.lushprojects.circuitjs1.client;

/** Deterministic work accounting shared by every order and local repair of one placement. */
final class PcbRoutingWork {
    static final int MAX_GRID_CELLS = 700000;
    static final int SEARCH_EXPANSIONS_PER_CELL = 20;
    static final class Limits {
        static final int MAX_ORDERINGS=5, MAX_RIP_PASSES=2, MAX_VICTIMS=2,
            MAX_REROUTED_NETS=6, MAX_EXPANSIONS=1000000;
        static final Limits DEFAULT = new Limits(MAX_ORDERINGS,MAX_RIP_PASSES,MAX_VICTIMS,
            MAX_REROUTED_NETS,MAX_EXPANSIONS);
        final int orderings, ripUpPasses, victimsPerPass, reroutedNets, expansions;
        Limits(int orderings, int ripUpPasses, int victimsPerPass, int reroutedNets, int expansions) {
            if (orderings < 1 || orderings > MAX_ORDERINGS || ripUpPasses < 0 || ripUpPasses > MAX_RIP_PASSES ||
                    victimsPerPass < 0 || victimsPerPass > MAX_VICTIMS || reroutedNets < 0 ||
                    reroutedNets > MAX_REROUTED_NETS || expansions < 1 || expansions > MAX_EXPANSIONS)
                throw new IllegalArgumentException("Invalid bounded routing recovery limits");
            this.orderings = orderings;
            this.ripUpPasses = ripUpPasses;
            this.victimsPerPass = victimsPerPass;
            this.reroutedNets = reroutedNets;
            this.expansions = expansions;
        }
    }
    enum Outcome { SUCCESS, EXHAUSTED, ABORTED }
    static final class Statistics {
        final int routeAttempts, branchSearches, orderingPasses, ripUpPasses, rippedNets,
            reroutedNets, expansions, candidateFailures, rawSegments, congestionRejections;
        final Outcome outcome;
        final PcbNetRouter.Reason rejectionReason;
        final String decisions;
        Statistics(PcbRoutingWork work) {
            routeAttempts=work.routeAttempts; branchSearches=work.branchSearches;
            orderingPasses=work.orderingPasses; ripUpPasses=work.ripUpPasses;
            rippedNets=work.rippedNets; reroutedNets=work.reroutedNets;
            expansions=work.expansions; candidateFailures=work.candidateFailures;
            rawSegments=work.rawSegments; congestionRejections=work.congestionRejections;
            outcome=work.outcome; rejectionReason=work.rejectionReason; decisions=work.decisions.toString();
        }
        String toCanonical() {
            return "routing-work@1;outcome="+outcome+";reason="+rejectionReason+";netAttempts="+routeAttempts+
                ";branchSearches="+branchSearches+";orders="+orderingPasses+
                ";ripPasses="+ripUpPasses+";ripped="+rippedNets+";rerouted="+reroutedNets+
                ";expansions="+expansions+";failures="+candidateFailures+
                ";rawSegments="+rawSegments+";conflicts="+congestionRejections+";"+decisions;
        }
    }
    final Limits limits;
    final int netAttemptLimit;
    int routeAttempts, branchSearches, orderingPasses, ripUpPasses, rippedNets,
        reroutedNets, expansions, candidateFailures, rawSegments, congestionRejections;
    Outcome outcome=Outcome.ABORTED;
    PcbNetRouter.Reason rejectionReason;
    final StringBuilder decisions=new StringBuilder();
    PcbRoutingWork(Limits limits, int netCount) {
        if (limits==null || netCount<0 || netCount>(Integer.MAX_VALUE-Limits.MAX_REROUTED_NETS)/Limits.MAX_ORDERINGS)
            throw new IllegalArgumentException("Invalid routing request budget");
        this.limits=limits; netAttemptLimit=netCount*limits.orderings+limits.reroutedNets;
    }
    void beginNet(String net, boolean reroute) {
        if (routeAttempts>=netAttemptLimit || reroute && reroutedNets>=limits.reroutedNets)
            throw new PcbNetRouter.Rejected(PcbNetRouter.Reason.RECOVERY_LIMIT,"NET_ATTEMPT_BUDGET",net);
        routeAttempts++;
        if (reroute) reroutedNets++;
    }
    void expand(String net) {
        if (expansions>=limits.expansions)
            throw new PcbNetRouter.Rejected(PcbNetRouter.Reason.SEARCH_LIMIT,"TOTAL_SEARCH_WORK_BUDGET",net);
        expansions++;
    }
    Statistics snapshot() { return new Statistics(this); }
}
