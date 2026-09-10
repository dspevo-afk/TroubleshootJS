package com.lushprojects.circuitjs1.client;

/**
 * Compatibility entry for the retired single-channel qualification name.
 *
 * <p>The current compiled gate is owned by Task49 and resolves the repeated
 * device through its provider and assembly plan.  Keeping this entry point
 * preserves the existing developer URL while preventing a second verifier
 * from retaining the old singleton driver/load model.</p>
 */
final class Task48DeveloperVerifier {
    private Task48DeveloperVerifier() { }

    static String verify(CirSim sim, String seedText, boolean forcedFailure) {
        return Task49DeveloperVerifier.verifyForTask48(sim, seedText, forcedFailure);
    }
}
