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
            boolean leftEdge = x < outline.x + outline.width / 2;
            int padX = leftEdge ? x + 90 : x + 10;
            int escapeDx = leftEdge ? 1 : -1;
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            Vector<String> ids = component.getPadIds();
            int height = 130 + (pinCount - 2) * 40;
            int pitch = pinCount == 2 ? 60 : 40;
            for (int index = 0; index < pinCount; index++)
                pads.add(new PcbPadPlacement(ids.get(index), padX, y + 40 + index * pitch,
                    escapeDx, 0, 30));
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, 100, height,
                new Rectangle(x, y, 100, height), new Rectangle(x - 6, y - 6, 112, height + 12)),
                pads);
        }
    }

    private static class AxialProvider implements PcbFootprintProvider {
        private final int visualKind;

        AxialProvider(int visualKind) { this.visualKind = visualKind; }

        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            int span = visualKind == 0 ? 220 + random.nextInt(3) * 20 :
                230 + random.nextInt(2) * 20;
            Vector<String> ids = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            pads.add(new PcbPadPlacement(ids.get(0), x + 30, y + 30, -1, 0, 50));
            pads.add(new PcbPadPlacement(ids.get(1), x + span - 30, y + 30, 1, 0, 50));
            int bodyInset = visualKind == 0 ? 70 : 72;
            int bodyHeight = visualKind == 0 ? 34 : 32;
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, span, 70,
                new Rectangle(x + bodyInset, y + (70 - bodyHeight) / 2,
                    span - bodyInset * 2, bodyHeight),
                new Rectangle(x + 12, y + 5, span - 24, 60)), pads);
        }
    }

    /** Compact routed output header; only the selected power connector anchors the board. */
    private static class OutputHeaderProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            Vector<String> ids = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            pads.add(new PcbPadPlacement(ids.get(0), x + 20, y + 30, 0, -1, 30));
            pads.add(new PcbPadPlacement(ids.get(1), x + 70, y + 30, 0, -1, 30));
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, 100, 70,
                new Rectangle(x + 8, y + 8, 84, 54),
                new Rectangle(x - 6, y - 6, 112, 82)), pads);
        }
    }

    private static class LedProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            Vector<String> ids = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            pads.add(new PcbPadPlacement(ids.get(0), x + 20, y + 70, 0, 1, 35));
            pads.add(new PcbPadPlacement(ids.get(1), x + 60, y + 70, 0, 1, 35));
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, 90, 100,
                new Rectangle(x + 15, y + 12, 60, 60),
                new Rectangle(x + 6, y + 4, 78, 101)), pads);
        }
    }

    /** Three-lead through-hole TO-92 footprint with stable B/C/E pad order. */
    private static class NpnProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            if (component.getPadIds().size() != 3)
                throw new IllegalStateException("NPN provider requires B/C/E pads for " +
                    component.getId());
            Vector<String> ids = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            pads.add(new PcbPadPlacement(ids.get(0), x + 20, y + 90, -1, 0, 30));
            // Clear the inclusive courtyard boundary used by the PCB route validator.
            pads.add(new PcbPadPlacement(ids.get(1), x + 60, y + 90, 0, 1, 32));
            pads.add(new PcbPadPlacement(ids.get(2), x + 100, y + 90, 0, 1, 32));
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, 130, 125,
                new Rectangle(x + 28, y + 12, 74, 60),
                new Rectangle(x + 5, y + 4, 120, 118)), pads);
        }
    }

    private static class ElectrolyticCapacitorProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            Vector<String> ids = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            pads.add(new PcbPadPlacement(ids.get(0), x + 30, y + 30, 0, -1, 38));
            pads.add(new PcbPadPlacement(ids.get(1), x + 80, y + 30, 0, -1, 38));
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, 120, 120,
                new Rectangle(x + 15, y + 12, 90, 75),
                new Rectangle(x + 5, y + 4, 110, 110)), pads);
        }
    }

    private static class CeramicCapacitorProvider implements PcbFootprintProvider {
        public PcbFootprint create(BoardComponent component, int x, int y, Random random,
                Rectangle outline) {
            requireTwoPads(component);
            Vector<String> ids = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            pads.add(new PcbPadPlacement(ids.get(0), x + 20, y + 30, 0, -1, 30));
            pads.add(new PcbPadPlacement(ids.get(1), x + 60, y + 30, 0, -1, 30));
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, 90, 90,
                new Rectangle(x + 12, y + 16, 66, 45),
                new Rectangle(x + 5, y + 5, 80, 80)), pads);
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
            int columns = 1;
            int rows = (pinCount + columns - 1) / columns;
            int pitch = 40;
            int width = 150;
            int height = 60 + (rows - 1) * pitch;
            boolean connector = component.getPhysicalPackage().isConnector();
            boolean leftEdge = x < outline.x + outline.width / 2;
            int escapeDx = connector && !leftEdge ? -1 : 1;
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            Vector<String> ids = component.getPadIds();
            int padX = connector && !leftEdge ? x + 30 : x + width - 30;
            for (int index = 0; index < pinCount; index++) {
                int row = index / columns;
                int column = index % columns;
                int padY = y + 30 + row * pitch;
                pads.add(new PcbPadPlacement(ids.get(index), padX, padY, escapeDx, 0, 30));
            }
            return new PcbFootprint(new PcbComponentPlacement(component.getId(), x, y, width,
                height, new Rectangle(x + 10, y + 10, width - 50, height - 20),
                new Rectangle(x + 10, y + 10, width - 20, height - 20)), pads);
        }
    }
}
