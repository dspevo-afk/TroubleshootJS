package com.lushprojects.circuitjs1.client;

/**
 * Optional family-owned transient functional test.  The generic challenge
 * lifecycle only asks this contract to exercise and classify the solved
 * circuit; it never needs to know a family or component name.
 */
interface GeneratedTemporalBehavior {
    /** Exercises the installed healthy graph before the generated fault is applied. */
    void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance instance);

    void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance instance);

    void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState);

    GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay);

    GeneratedObservedBehavior getObservedBehavior();
}
