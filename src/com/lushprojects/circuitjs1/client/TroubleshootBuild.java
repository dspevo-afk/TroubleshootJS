package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.GWT;

/** Deferred binding makes developer entry points unreachable in the player build. */
final class TroubleshootBuild {
    private TroubleshootBuild() { }

    static boolean isDeveloper() {
        return !GWT.isScript() || ((TroubleshootBuildConfiguration)
            GWT.create(TroubleshootBuildConfiguration.class)).isDeveloper();
    }
}
