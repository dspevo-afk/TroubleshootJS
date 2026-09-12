package com.lushprojects.circuitjs1.client;

/**
 * Optional family-owned transient functional test.  The generic challenge
 * lifecycle only asks this contract to exercise and classify the solved
 * circuit; it never needs to know a family or component name.
 */
interface GeneratedTemporalBehavior {
    /** Validate captured observation endpoints before any temporal callback runs. */
    void requireOwnedBy(GeneratedBoardInstance instance);

    /**
     * Returns the immutable recipe and healthy reference values consumed by
     * this behavior's proof.  The returned value must retain no live owner,
     * graph, solver, endpoint, or array reference.
     */
    GeneratedTemporalDependency getDependency(GeneratedBoardInstance instance);

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
