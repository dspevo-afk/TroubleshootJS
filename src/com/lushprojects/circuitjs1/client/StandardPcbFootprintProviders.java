package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

/** Built-in package providers. Each provider owns only its own package geometry. */
final class StandardPcbFootprintProviders {
    private StandardPcbFootprintProviders() { }

    static PcbFootprintRegistry createRegistry() {
        PcbFootprintRegistry registry = new PcbFootprintRegistry();
        registry.register(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, new ConnectorProvider());
        registry.register(PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            new OutputHeaderProvider());
        registry.register(PhysicalPackages.AXIAL_RESISTOR, new AxialProvider(0));
        registry.register(PhysicalPackages.AXIAL_DIODE, new AxialProvider(1));
        registry.register(PhysicalPackages.THROUGH_HOLE_LED, new LedProvider());
        registry.register(PhysicalPackages.TO92_NPN, new NpnProvider());
        registry.register(PhysicalPackages.TO92_NMOS, new NmosProvider());
        registry.register(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new ElectrolyticCapacitorProvider());
        registry.register(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            new CeramicCapacitorProvider());
        registry.register(PhysicalPackages.MULTI_TERMINAL, new MultiTerminalProvider(3, 6));
        registry.register(PhysicalPackages.DEV_CANARY_3, new MultiTerminalProvider(3, 3));
        registry.register(PhysicalPackages.DEV_CANARY_3_ORDERED,
            new MultiTerminalProvider(3, 3));
        registry.register(PhysicalPackages.DEV_CANARY_4, new MultiTerminalProvider(4, 4));
        registry.register(PhysicalPackages.DEV_CANARY_5, new MultiTerminalProvider(5, 5));
        registry.register(PhysicalPackages.DEV_CANARY_6, new MultiTerminalProvider(6, 6));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_3,
            new MultiTerminalProvider(3, 3));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_4,
            new MultiTerminalProvider(4, 4));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_5,
            new MultiTerminalProvider(5, 5));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_6,
            new MultiTerminalProvider(6, 6));
        return registry;
    }

    private static void requireTwoPads(BoardComponent component) {
        if (component.getPadIds().size() != 2)
            throw new IllegalStateException("Two-terminal provider received " +
                component.getPadIds().size() + " pads for " + component.getId());
    }

    private static class ConnectorProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            int pinCount = component.getPadIds().size();
            if (pinCount < 2 || pinCount > 6)
                throw new IllegalStateException("Connector provider received " + pinCount +
                    " pads for " + component.getId() + "; expected 2-6");
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    private static class AxialProvider implements PcbFootprintProvider {
        private final int visualKind;

        AxialProvider(int visualKind) { this.visualKind = visualKind; }

        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    /** Compact routed output header; only the selected power connector anchors the board. */
    private static class OutputHeaderProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    private static class LedProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    /** Three-lead through-hole TO-92 footprint with stable B/C/E pad order. */
    private static class NpnProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            if (component.getPadIds().size() != 3)
                throw new IllegalStateException("NPN provider requires B/C/E pads for " +
                    component.getId());
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    /** Compact TO-92-like NMOS footprint with explicit G/D/S pad order. */
    private static class NmosProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            if (component.getPadIds().size() != 3)
                throw new IllegalStateException("NMOS provider requires G/D/S pads for " +
                    component.getId());
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    private static class ElectrolyticCapacitorProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    private static class CeramicCapacitorProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }

    /** Deterministic pin-header geometry for generic multi-terminal packages. */
    private static class MultiTerminalProvider implements PcbFootprintProvider {
        private final int minimumPins;
        private final int maximumPins;

        MultiTerminalProvider(int minimumPins, int maximumPins) {
            this.minimumPins = minimumPins;
            this.maximumPins = maximumPins;
        }

        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            int pinCount = component.getPadIds().size();
            if (pinCount < minimumPins || pinCount > maximumPins)
                throw new IllegalStateException("Multi-terminal provider received " + pinCount +
                    " pads for " + component.getId() + "; expected " + minimumPins + "-" +
                    maximumPins);
            return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
        }
    }
}
