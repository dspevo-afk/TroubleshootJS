package com.lushprojects.circuitjs1.client;

/** Original coordinate lookup remains an independent oracle for analyzed adjacency. */
final class WireCurrentAdjacencyChecks {
    static void verify(CirSim sim) {
        verifyEndpoints(sim);
        double[] before = new double[sim.wireInfoList.size()];
        double[] expected = new double[before.length];
        for (int i = 0; i < before.length; i++) before[i] = sim.wireInfoList.get(i).wire.getCurrent();
        try {
            // READY is an ownership boundary, not a wire-display sampling
            // boundary. Temporal work can update companion-model currents
            // after the previous display sample. Run the retained coordinate
            // algorithm and the optimized algorithm from identical inputs.
            int index = 0;
            for (CirSim.WireInfo wire : sim.wireInfoList) {
                Point point = wire.wire.getPost(wire.post);
                double current = 0;
                for (CircuitElm neighbor : wire.neighbors)
                    current += neighbor.getCurrentIntoNode(neighbor.getNodeAtPoint(point.x, point.y));
                expected[index++] = wire.post == 0 ? current : -current;
                wire.wire.setCurrent(-1, expected[index - 1]);
            }
            restore(sim, before);
            sim.calcWireCurrents();
            for (int i = 0; i < expected.length; i++)
                if (!PowerDomainContract.finite(expected[i]) ||
                        expected[i] != sim.wireInfoList.get(i).wire.getCurrent())
                    throw new IllegalStateException("Cached wire current differs from original coordinate algorithm");
        } finally { restore(sim, before); }
    }

    private static void restore(CirSim sim, double[] currents) {
        for (int i = 0; i < currents.length; i++) sim.wireInfoList.get(i).wire.setCurrent(-1, currents[i]);
    }

    private static void verifyEndpoints(CirSim sim) {
        for (CircuitNode node : sim.nodeList) {
            if (node.voltageElements.length != node.links.size() ||
                    node.voltagePosts.length != node.links.size() || node.idealWires.length != node.links.size())
                throw new IllegalStateException("Analyzed voltage recipient count changed");
            for (int i = 0; i < node.links.size(); i++) {
                CircuitNodeLink link = node.links.get(i);
                if (node.voltageElements[i] != link.elm || node.voltagePosts[i] != link.num ||
                        node.idealWires[i] != (link.elm.getClass() == WireElm.class))
                    throw new IllegalStateException("Stale analyzed voltage recipient");
            }
        }
        if (sim.wireInfoList == null) throw new IllegalStateException("Missing analyzed wire adjacency");
        for (CirSim.WireInfo wire : sim.wireInfoList) {
            if (wire.currentNeighbors.length != wire.neighbors.size() ||
                    wire.currentNeighborPosts.length != wire.neighbors.size())
                throw new IllegalStateException("Wire adjacency count changed");
            Point point = wire.wire.getPost(wire.post);
            for (int i = 0; i < wire.neighbors.size(); i++) {
                CircuitElm neighbor = wire.neighbors.get(i);
                int post = neighbor.getNodeAtPoint(point.x, point.y);
                if (wire.currentNeighbors[i] != neighbor || wire.currentNeighborPosts[i] != post)
                    throw new IllegalStateException("Stale analyzed wire endpoint");
            }
        }
    }

    static boolean rejectsStaleEndpoint(CirSim sim) {
        for (CirSim.WireInfo wire : sim.wireInfoList) {
            if (wire.currentNeighborPosts.length == 0) continue;
            int before = wire.currentNeighborPosts[0];
            try {
                wire.currentNeighborPosts[0] = before + 1;
                try { verifyEndpoints(sim); }
                catch (IllegalStateException expected) { return true; }
                return false;
            } finally { wire.currentNeighborPosts[0] = before; }
        }
        throw new IllegalStateException("Wire adjacency negative canary has no neighbor");
    }
    private WireCurrentAdjacencyChecks() { }
}
