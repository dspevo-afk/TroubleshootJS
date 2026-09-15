package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

/** Watches actual public relay contact positions; private verification never makes sound. */
final class WorkbenchRelayFeedback {
    private Object owner;
    private final HashMap<Object, Integer> positions = new HashMap<Object, Integer>();

    int observe(Object currentOwner, Object part, int position) {
        if (owner != currentOwner) { owner = currentOwner; positions.clear(); }
        if (currentOwner == null || part == null || (position != 0 && position != 1)) return -1;
        Integer previous = positions.put(part, Integer.valueOf(position));
        return previous != null && previous.intValue() != position ? position : -1;
    }
    void clear() { owner = null; positions.clear(); }
    void update(CirSim sim) {
        if (sim.troubleshootDebug || sim.developerVerifierRunning ||
                (sim.generationCoordinator != null && sim.generationCoordinator.isRunning()) || sim.playerSessionController == null ||
                !sim.playerSessionController.isWorkbenchScreen()) { clear(); return; }
        if (!sim.isGeneratedRuntimeSettled()) return;
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        if (owner != current) { owner = current; positions.clear(); }
        for (PhysicalPart<?> part : current.getPhysicalBoardRuntime().getPhysicalParts()) {
            if (!(part instanceof PhysicalRelayPart) || !part.isInstalled()) continue;
            int changed = observe(current, part, ((PhysicalRelayPart)part).getElement().i_position);
            if (changed >= 0) play(changed == 1);
        }
    }
    private static native void play(boolean energized) /*-{
        if ($wnd.tsjBenchAudio) $wnd.tsjBenchAudio.relay(energized);
    }-*/;
}
