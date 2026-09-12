package com.lushprojects.circuitjs1.client;

/** Provider-local factory for the ordinary alternate NMOS implementation. */
final class AlternateNmosDriverProvider {
    private AlternateNmosDriverProvider() { }

    static ControlledIndicatorBlockContributions.Provider create() {
        return ControlledIndicatorBlockContributions.createNmosDriver(
            NmosDriverProfile.alternate());
    }
}
