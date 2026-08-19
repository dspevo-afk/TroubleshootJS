package com.lushprojects.circuitjs1.client;

/** Factory for the passive observation profile shared by simple families. */
final class GeneratedCustomerRetestProfiles {
    private GeneratedCustomerRetestProfiles() { }

    static GeneratedCustomerRetestProfile observation(String stableId,
            String playerInstruction, String requiredPowerTransition,
            String requiredInputTransition, String observableOutput,
            String timingAndRepetition, String unaffectedFunctions) {
        return new GeneratedCustomerRetestProfile(stableId, playerInstruction,
            requiredPowerTransition, requiredInputTransition, observableOutput,
            timingAndRepetition, unaffectedFunctions,
            new GeneratedCustomerRetestProfile.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim,
                    GeneratedBoardInstance instance) {
                if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, instance))
                    return GeneratedCustomerRetestSupport.failure();
                GeneratedRepairStatus status = instance.getBehaviorContract().getRepairStatus(
                    instance, sim.getBoardModificationController(),
                    sim.getBoardPowerController().getState(), sim.activeMeasurementOverlay);
                return status == GeneratedRepairStatus.CORRECTLY_RESTORED ?
                    GeneratedCustomerRetestSupport.success() :
                    GeneratedCustomerRetestSupport.failure();
            }
        });
    }
}
