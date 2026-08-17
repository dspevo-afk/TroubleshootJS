package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Built-in physical render providers keyed by typed package definitions. */
final class StandardPhysicalPartRenderProviders {
    private StandardPhysicalPartRenderProviders() { }

    static PhysicalPartRenderRegistry createRegistry() {
        PhysicalPartRenderRegistry registry = new PhysicalPartRenderRegistry();
        registry.register(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            new FixedProvider(new ConnectorRenderer()));
        registry.register(PhysicalPackages.AXIAL_RESISTOR,
            new FixedProvider(new ResistorRenderer()));
        registry.register(PhysicalPackages.AXIAL_DIODE,
            new FixedProvider(new DiodeRenderer()));
        registry.register(PhysicalPackages.THROUGH_HOLE_LED,
            new FixedProvider(new LedRenderer()));
        registry.register(PhysicalPackages.MULTI_TERMINAL,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_3,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_3_ORDERED,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_4,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_5,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_6,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_3,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_4,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_5,
            new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.DEV_CANARY_CONNECTOR_6,
            new FixedProvider(new MultiTerminalRenderer()));
        return registry;
    }

    private static final class FixedProvider implements PhysicalPartRenderProvider {
        private final PhysicalPartRenderer renderer;

        FixedProvider(PhysicalPartRenderer renderer) { this.renderer = renderer; }

        public PhysicalPartRenderer getRenderer(PhysicalPart<?> part) { return renderer; }
    }

    private static abstract class BaseRenderer implements PhysicalPartRenderer {
        public ProbeTarget createInstalledProbeTarget(CirSim sim,
                PhysicalPartRenderContext context, int terminal) {
            return context.getRenderer().createInstalledProbeTargetForProvider(sim, context,
                terminal);
        }

        public ProbeTarget createLooseProbeTarget(CirSim sim,
                PhysicalPartRenderContext context, int terminal) {
            PhysicalPartRenderMetadata metadata = context.getPart() == null ? null :
                context.getPart().getRenderMetadata();
            if (metadata != null && metadata.getLooseProbeProvider() != null)
                return metadata.getLooseProbeProvider().createLooseProbeTarget(sim,
                    context.getInstance(), context.getPart(), terminal, context.getRenderer());
            return new PhysicalPartProbeTarget(sim, context.getInstance(),
                context.getPart().getId(), terminal, context.getRenderer());
        }

        protected PhysicalPartRenderGeometry installedGeometry(PhysicalPartRenderContext context,
                Vector<PhysicalPartRenderTerminal> terminals) {
            Rectangle bounds = context.isComponentRemoved() ? context.getRemovedComponentBounds() :
                context.getComponentBounds();
            Vector<PhysicalPartRenderHitRegion> hits = new Vector<PhysicalPartRenderHitRegion>();
            hits.add(new PhysicalPartRenderHitRegion(bounds));
            return new PhysicalPartRenderGeometry(terminals, hits, bounds);
        }

        protected PhysicalPartRenderGeometry looseGeometry(PhysicalPartRenderContext context,
                Vector<PhysicalPartRenderTerminal> terminals, boolean reversed) {
            Rectangle bounds = context.getLooseBounds(reversed);
            Vector<PhysicalPartRenderHitRegion> hits = new Vector<PhysicalPartRenderHitRegion>();
            hits.add(new PhysicalPartRenderHitRegion(bounds));
            return new PhysicalPartRenderGeometry(terminals, hits, bounds);
        }

        protected Vector<PhysicalPartRenderTerminal> installedTerminals(
                PhysicalPartRenderContext context) {
            Vector<PhysicalPartRenderTerminal> result = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++)
                result.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    context.getBoardPadId(index), context.getComponentProbePoint(index)));
            return result;
        }

        protected Vector<PhysicalPartRenderTerminal> looseTerminals(
                PhysicalPartRenderContext context, boolean reversed) {
            Vector<PhysicalPartRenderTerminal> result = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++)
                result.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    null, context.getLooseTerminalPoint(index, reversed)));
            return result;
        }

        protected void drawSelection(Graphics graphics, PhysicalPartRenderGeometry geometry) {
            Rectangle bounds = geometry.getSelectionBounds();
            graphics.setColor("#f4d35e");
            graphics.setLineWidth(4);
            graphics.drawRect(bounds.x - 8, bounds.y - 8, bounds.width + 16, bounds.height + 16);
            graphics.setLineWidth(1);
        }

        protected void drawLead(Graphics graphics, Point start, Point end) {
            graphics.setColor("#a8adb0");
            graphics.setLineWidth(3);
            graphics.drawLine(start.x, start.y, end.x, end.y);
            graphics.setLineWidth(1);
        }

        protected void drawLead(Graphics graphics, Point start, Point end, int width) {
            graphics.setColor("#a8adb0");
            graphics.setLineWidth(width);
            graphics.drawLine(start.x, start.y, end.x, end.y);
            graphics.setLineWidth(1);
        }
    }

    /**
     * The package provider owns the component-specific metadata cast.  Body
     * rendering never depends on the concrete installed-part implementation;
     * fixed generated parts and inventory parts expose the same typed seam.
     */
    private static final class ResistorMetadataAdapter {
        ResistorNameplate require(PhysicalPart<?> part) {
            PhysicalPartRenderMetadata metadata = requireMetadata(part, "resistor");
            if (!(metadata.getVisualSpecification() instanceof ResistorNameplate))
                throw new IllegalStateException("Resistor package has non-resistor metadata: " +
                    part.getId());
            return (ResistorNameplate) metadata.getVisualSpecification();
        }
    }

    private static final class DiodeMetadataAdapter {
        DiodeNameplate require(PhysicalPart<?> part) {
            PhysicalPartRenderMetadata metadata = requireMetadata(part, "diode");
            if (!(metadata.getVisualSpecification() instanceof DiodeNameplate))
                throw new IllegalStateException("Diode package has non-diode metadata: " +
                    part.getId());
            return (DiodeNameplate) metadata.getVisualSpecification();
        }

        boolean isReversed(PhysicalPart<?> part) {
            require(part);
            return part.getRenderMetadata().isReversedInstallation();
        }
    }

    private static final class LedMetadataAdapter {
        LedNameplate require(PhysicalPart<?> part) {
            PhysicalPartRenderMetadata metadata = requireMetadata(part, "LED");
            if (!(metadata.getVisualSpecification() instanceof LedNameplate))
                throw new IllegalStateException("LED package has non-LED metadata: " +
                    part.getId());
            return (LedNameplate) metadata.getVisualSpecification();
        }

        boolean isReversed(PhysicalPart<?> part) {
            require(part);
            return part.getRenderMetadata().isReversedInstallation();
        }
    }

    private static final ResistorMetadataAdapter RESISTOR_METADATA =
        new ResistorMetadataAdapter();
    private static final DiodeMetadataAdapter DIODE_METADATA =
        new DiodeMetadataAdapter();
    private static final LedMetadataAdapter LED_METADATA =
        new LedMetadataAdapter();

    private static PhysicalPartRenderMetadata requireMetadata(PhysicalPart<?> part,
            String packageName) {
        if (part == null || part.getRenderMetadata() == null)
            throw new IllegalStateException("Missing " + packageName + " render metadata");
        return part.getRenderMetadata();
    }

    private static final class ResistorRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            return installedGeometry(context, installedTerminals(context));
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return looseGeometry(context, looseTerminals(context, false), false);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.isComponentRemoved() || context.getPart() == null)
                return;
            ResistorNameplate nameplate = RESISTOR_METADATA.require(context.getPart());
            Point pad1 = context.getBoardPadPoint(0);
            Point pad2 = context.getBoardPadPoint(1);
            if (pad1 == null || pad2 == null)
                return;
            int bodyY = pad1.y - (context.getComponentState() == ComponentPhysicalState.LEAD_LIFTED ?
                context.scale(28) : 0);
            int bodyLeft = pad1.x + context.scale(45);
            int bodyRight = pad2.x - context.scale(45);
            drawLead(graphics, new Point(bodyLeft, bodyY), context.getMountedLeadEnd(0),
                Math.max(3, context.scale(4)));
            drawLead(graphics, new Point(bodyRight, bodyY), context.getMountedLeadEnd(1),
                Math.max(3, context.scale(4)));
            drawResistorBody(graphics, context, nameplate, bodyLeft, bodyRight, bodyY,
                Math.max(22, context.scale(34)));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            ResistorNameplate nameplate = RESISTOR_METADATA.require(context.getPart());
            Point lead1 = context.getLooseTerminalPoint(0, false);
            Point lead2 = context.getLooseTerminalPoint(1, false);
            int bodyLeft = lead1.x + context.scale(24);
            int bodyRight = lead2.x - context.scale(24);
            drawLead(graphics, lead1, new Point(bodyLeft, lead1.y));
            drawLead(graphics, new Point(bodyRight, lead2.y), lead2);
            drawResistorBody(graphics, context, nameplate, bodyLeft, bodyRight, lead1.y,
                context.scale(28));
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "RESISTOR",
                lead1.x + context.scale(20), lead1.y - context.scale(26));
            context.markBodyDrawn();
        }

        private void drawResistorBody(Graphics graphics, PhysicalPartRenderContext context,
                ResistorNameplate nameplate, int left, int right, int y, int height) {
            graphics.setColor("#d9c79b");
            graphics.fillRect(left, y - height / 2, right - left, height);
            graphics.setColor("#302a22");
            graphics.drawRect(left, y - height / 2, right - left, height);
            ResistorColorBand[] bands = ResistorColorCode.getFourBandCode(nameplate);
            for (int index = 0; index < bands.length; index++) {
                int x = left + (right - left) * (index + 1) / 5;
                graphics.setColor(bandColor(bands[index]));
                graphics.fillRect(x - Math.max(2, context.scale(3)), y - height / 2,
                    Math.max(4, context.scale(6)), height);
            }
        }

        protected void drawLead(Graphics graphics, Point start, Point end, int width) {
            graphics.setColor("#c6c2b2");
            graphics.setLineWidth(width);
            graphics.drawLine(start.x, start.y, end.x, end.y);
            graphics.setLineWidth(1);
        }
    }

    private static final class DiodeRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            return installedGeometry(context, installedTerminals(context));
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            boolean reversed = isReversed(context);
            return looseGeometry(context, looseTerminals(context, reversed), reversed);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.isComponentRemoved() || context.getPart() == null)
                return;
            DIODE_METADATA.require(context.getPart());
            boolean reversed = DIODE_METADATA.isReversed(context.getPart());
            Point leftPad = context.getBoardPadPoint(0);
            Point rightPad = context.getBoardPadPoint(1);
            if (leftPad == null || rightPad == null)
                return;
            int bodyY = leftPad.y - (context.getComponentState() == ComponentPhysicalState.LEAD_LIFTED ?
                context.scale(28) : 0);
            int bodyLeft = leftPad.x + context.scale(42);
            int bodyRight = rightPad.x - context.scale(42);
            drawLead(graphics, new Point(bodyLeft, bodyY), context.getMountedLeadEnd(0),
                Math.max(3, context.scale(4)));
            drawLead(graphics, new Point(bodyRight, bodyY), context.getMountedLeadEnd(1),
                Math.max(3, context.scale(4)));
            int bodyHeight = Math.max(22, context.scale(32));
            graphics.setColor("#282c31");
            graphics.fillRect(bodyLeft, bodyY - bodyHeight / 2, bodyRight - bodyLeft, bodyHeight);
            graphics.setColor("#111315");
            graphics.drawRect(bodyLeft, bodyY - bodyHeight / 2, bodyRight - bodyLeft, bodyHeight);
            drawCathodeBand(graphics, reversed ? bodyLeft : bodyRight,
                bodyY, bodyHeight, reversed, context);
            graphics.drawString("K", reversed ? leftPad.x - context.scale(5) :
                rightPad.x - context.scale(5), rightPad.y + context.scale(28));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            boolean reversed = DIODE_METADATA.isReversed(context.getPart());
            Point anode = context.getLooseTerminalPoint(0, reversed);
            Point cathode = context.getLooseTerminalPoint(1, reversed);
            Point left = anode.x < cathode.x ? anode : cathode;
            Point right = anode.x < cathode.x ? cathode : anode;
            int bodyLeft = left.x + context.scale(24);
            int bodyRight = right.x - context.scale(24);
            drawLead(graphics, left, new Point(bodyLeft, left.y));
            drawLead(graphics, new Point(bodyRight, right.y), right);
            graphics.setColor("#282c31");
            graphics.fillRect(bodyLeft, left.y - context.scale(14), bodyRight - bodyLeft,
                context.scale(28));
            graphics.setColor("#111315");
            graphics.drawRect(bodyLeft, left.y - context.scale(14), bodyRight - bodyLeft,
                context.scale(28));
            drawCathodeBand(graphics, cathode.x < anode.x ? bodyLeft : bodyRight, left.y,
                context.scale(28), cathode.x < anode.x, context);
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "DIODE",
                left.x + context.scale(20), left.y - context.scale(26));
            context.markBodyDrawn();
        }

        private boolean isReversed(PhysicalPartRenderContext context) {
            return context.getPart() != null && DIODE_METADATA.isReversed(context.getPart());
        }

        private void drawCathodeBand(Graphics graphics, int edge, int y, int height, boolean left,
                PhysicalPartRenderContext context) {
            int width = Math.max(5, context.scale(8));
            graphics.setColor("#d8dde0");
            graphics.fillRect(left ? edge : edge - width, y - height / 2, width, height);
        }
    }

    private static final class LedRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            return installedGeometry(context, installedTerminals(context));
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            boolean reversed = isReversed(context);
            return looseGeometry(context, looseTerminals(context, reversed), reversed);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.isComponentRemoved() || context.getPart() == null)
                return;
            LED_METADATA.require(context.getPart());
            Point anode = context.getBoardPadPoint(0);
            Point cathode = context.getBoardPadPoint(1);
            if (anode == null || cathode == null)
                return;
            int centerX = (anode.x + cathode.x) / 2;
            int centerY = anode.y - context.scale(33) -
                (context.getComponentState() ==
                    ComponentPhysicalState.LEAD_LIFTED ? context.scale(28) : 0);
            int radius = Math.max(16, context.scale(25));
            drawLedLead(graphics, context.getMountedLeadEnd(0),
                new Point(centerX - context.scale(10), centerY + context.scale(15)));
            drawLedLead(graphics, context.getMountedLeadEnd(1),
                new Point(centerX + context.scale(10), centerY + context.scale(15)));
            graphics.setColor("#b5232d");
            if (context.isIlluminated()) {
                graphics.setColor("#ffdc4f");
                graphics.fillOval(centerX - radius - context.scale(9), centerY - radius - context.scale(9),
                    radius * 2 + context.scale(18), radius * 2 + context.scale(18));
                graphics.setColor("#b5232d");
            }
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#f36a6f");
            graphics.fillOval(centerX - radius / 2, centerY - radius / 2, radius / 2, radius / 2);
            graphics.setColor("#f3efe4");
            boolean reversed = LED_METADATA.isReversed(context.getPart());
            graphics.fillRect(reversed ? centerX - radius : centerX + radius - context.scale(6),
                centerY - radius / 2, Math.max(3, context.scale(5)), radius);
            graphics.drawString("K", (reversed ? anode : cathode).x - context.scale(4),
                cathode.y + context.scale(27));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            boolean reversed = LED_METADATA.isReversed(context.getPart());
            Point anode = context.getLooseTerminalPoint(0, reversed);
            Point cathode = context.getLooseTerminalPoint(1, reversed);
            int centerX = (anode.x + cathode.x) / 2;
            int centerY = anode.y;
            int radius = Math.max(11, context.scale(16));
            drawLedLead(graphics, anode, new Point(centerX - radius, centerY));
            drawLedLead(graphics, new Point(centerX + radius, centerY), cathode);
            graphics.setColor("#b5232d");
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#f36a6f");
            graphics.fillOval(centerX - radius / 2, centerY - radius / 2, radius / 2, radius / 2);
            boolean cathodeLeft = cathode.x < anode.x;
            graphics.setColor("#f3efe4");
            graphics.fillRect(cathodeLeft ? centerX - radius : centerX + radius - context.scale(4),
                centerY - radius / 2, Math.max(3, context.scale(4)), radius);
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "LED",
                Math.min(anode.x, cathode.x) + context.scale(20), centerY - context.scale(26));
            context.markBodyDrawn();
        }

        private boolean isReversed(PhysicalPartRenderContext context) {
            return context.getPart() != null && LED_METADATA.isReversed(context.getPart());
        }

        private void drawLedLead(Graphics graphics, Point start, Point end) {
            graphics.setColor("#a6b8ad");
            graphics.setLineWidth(3);
            graphics.drawLine(start.x, start.y, end.x, end.y);
            graphics.setLineWidth(1);
        }
    }

    private static final class ConnectorRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            return installedGeometry(context, installedTerminals(context));
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return looseGeometry(context, looseTerminals(context, false), false);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            Rectangle bounds = context.getComponentBounds();
            graphics.setColor("#2d8f71");
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            graphics.setColor("#b8ead7");
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            drawConnectorPads(graphics, context);
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) { }

        private void drawConnectorPads(Graphics graphics, PhysicalPartRenderContext context) {
            for (int index = 0; index < context.getTerminalCount(); index++) {
                Point pad = context.getBoardPadPoint(index);
                if (pad == null)
                    continue;
                int radius = Math.max(13, context.scale(20));
                graphics.setColor("#b8c8c2");
                graphics.fillOval(pad.x - radius, pad.y - radius, radius * 2, radius * 2);
                graphics.setColor("#4d5b57");
                graphics.setLineWidth(3);
                graphics.drawLine(pad.x - radius / 2, pad.y, pad.x + radius / 2, pad.y);
                if (index == 0)
                    graphics.drawLine(pad.x, pad.y - radius / 2, pad.x, pad.y + radius / 2);
                graphics.setLineWidth(1);
            }
        }
    }

    private static final class MultiTerminalRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            Vector<PhysicalPartRenderTerminal> terminals = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++)
                terminals.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    context.getBoardPadId(index), context.getProviderTerminalPoint(index)));
            return installedGeometry(context, terminals);
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return looseGeometry(context, looseTerminals(context, false), false);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            Rectangle bounds = context.getComponentBounds();
            boolean connector = context.getPhysicalPackage().isConnector();
            graphics.setColor(connector ? "#2d8f71" : "#485b69");
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            graphics.setColor(connector ? "#b8ead7" : "#d6e2ea");
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Point pad = context.getProviderTerminalPoint(terminal.getTerminalIndex());
                if (pad == null)
                    continue;
                graphics.setColor("#b8c8c2");
                int radius = Math.max(10, context.scale(16));
                graphics.fillOval(pad.x - radius, pad.y - radius, radius * 2, radius * 2);
                graphics.setColor("#4d5b57");
                graphics.drawString(terminal.getTerminalId(), pad.x - context.scale(4),
                    pad.y + context.scale(4));
            }
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) { }
    }

    private static void drawPartLabel(Graphics graphics, PhysicalPartRenderContext context,
            String text, int x, int y) {
        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, context.scale(13))));
        graphics.setColor("#3d484c");
        graphics.drawString(text, x, y);
    }

    private static String bandColor(ResistorColorBand band) {
        if (band == ResistorColorBand.BLACK) return "#222222";
        if (band == ResistorColorBand.BROWN) return "#7d4a2d";
        if (band == ResistorColorBand.RED) return "#b5232d";
        if (band == ResistorColorBand.ORANGE) return "#cc6c2b";
        if (band == ResistorColorBand.YELLOW) return "#e0ba36";
        if (band == ResistorColorBand.GREEN) return "#278456";
        if (band == ResistorColorBand.BLUE) return "#355caa";
        if (band == ResistorColorBand.VIOLET) return "#7754a1";
        if (band == ResistorColorBand.GRAY) return "#73777b";
        if (band == ResistorColorBand.WHITE) return "#e8e8e4";
        if (band == ResistorColorBand.GOLD) return "#c7a33b";
        throw new IllegalArgumentException("Unsupported resistor band: " + band);
    }
}
