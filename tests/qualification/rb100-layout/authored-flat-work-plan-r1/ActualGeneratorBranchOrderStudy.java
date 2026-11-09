package com.lushprojects.circuitjs1.client;

/** Compile-only interface carrier. No Study, Mode, ArmRun or producer exists here.
 * The actual shared Meter implementation is compiled from its pinned Source.
 * This carrier cannot qualify or execute the original branch-order study. */
final class ActualGeneratorBranchOrderStudy {
    private ActualGeneratorBranchOrderStudy() {
        throw new UnsupportedOperationException("INTERFACE_CARRIER_ONLY");
    }
    interface TerminalPrefixInspector {
        void onTerminalPrefix(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String prefixFingerprint,
            String prefixSha256, int prefixBytes, int receiptBranchOrdinal, int groupOrdinal);
        void afterOwnersClosed(boolean ownersReleased, boolean contextRestored);
    }
    interface SharedOwnerBudget {
        void checkOuterDeadline();
        void admitOwnerGroup(int ordinal);
    }
}
