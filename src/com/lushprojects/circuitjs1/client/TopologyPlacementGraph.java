package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

/**
 * A physical-placement view of the logical board graph.  It intentionally uses
 * BoardNet and BoardPad IDs rather than CircuitJS solver nodes so that the
 * layout remains stable when simulation implementation details change.
 */
class TopologyPlacementGraph {
    static class PadLink {
        private final String componentId;
        private final String padId;
        private final String otherComponentId;
        private final String otherPadId;
        private final String netId;
        private final double weight;

        PadLink(String componentId, String padId, String otherComponentId,
                String otherPadId, String netId, double weight) {
            this.componentId = componentId;
            this.padId = padId;
            this.otherComponentId = otherComponentId;
            this.otherPadId = otherPadId;
            this.netId = netId;
            this.weight = weight;
        }

        String getComponentId() { return componentId; }
        String getPadId() { return padId; }
        String getOtherComponentId() { return otherComponentId; }
        String getOtherPadId() { return otherPadId; }
        String getNetId() { return netId; }
        double getWeight() { return weight; }
    }

    private final HashMap<String, Vector<PadLink>> linksByComponent =
        new HashMap<String, Vector<PadLink>>();

    TopologyPlacementGraph(TroubleshootBoard board) {
        Vector<String> componentIds = board.getComponentIds();
        for (String componentId : componentIds)
            linksByComponent.put(componentId, new Vector<PadLink>());
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String netId : netIds) {
            Vector<String> padIds = board.getNet(netId).getPadIds();
            Collections.sort(padIds);
            for (int first = 0; first < padIds.size(); first++) {
                BoardPad firstPad = board.getPad(padIds.get(first));
                for (int second = first + 1; second < padIds.size(); second++) {
                    BoardPad secondPad = board.getPad(padIds.get(second));
                    if (firstPad.getComponentId().equals(secondPad.getComponentId()))
                        continue;
                    double weight = padIds.size() == 2 ? 3.0 : 1.0;
                    // The connector is an external anchor.  Its common return
                    // and supply pads still attract a cluster, but direct
                    // component-to-component links should form the functional
                    // chain or branch around that anchor.
                    if ("J1".equals(firstPad.getComponentId()) ||
                            "J1".equals(secondPad.getComponentId()))
                        weight *= .45;
                    addLink(firstPad.getComponentId(), new PadLink(
                        firstPad.getComponentId(), firstPad.getId(),
                        secondPad.getComponentId(), secondPad.getId(), netId, weight));
                    addLink(secondPad.getComponentId(), new PadLink(
                        secondPad.getComponentId(), secondPad.getId(),
                        firstPad.getComponentId(), firstPad.getId(), netId, weight));
                }
            }
        }
    }

    Vector<PadLink> getLinksFor(String componentId) {
        Vector<PadLink> result = linksByComponent.get(componentId);
        return result == null ? new Vector<PadLink>() : new Vector<PadLink>(result);
    }

    private void addLink(String componentId, PadLink link) {
        Vector<PadLink> links = linksByComponent.get(componentId);
        if (links == null) {
            links = new Vector<PadLink>();
            linksByComponent.put(componentId, links);
        }
        links.add(link);
    }
}
