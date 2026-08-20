package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Built-in physical render providers keyed by typed package definitions. */
final class StandardPhysicalPartRenderProviders {
    private StandardPhysicalPartRenderProviders() { }

    static PhysicalPartRenderRegistry createRegistry() {
        PhysicalPartRenderRegistry registry = new PhysicalPartRenderRegistry();
        registry.register(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            new FixedProvider(new ConnectorRenderer()));
        registry.register(PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            new FixedProvider(new ConnectorRenderer()));
        registry.register(PhysicalPackages.AXIAL_RESISTOR,
            new FixedProvider(new ResistorRenderer()));
        registry.register(PhysicalPackages.AXIAL_DIODE,
            new FixedProvider(new DiodeRenderer()));
        registry.register(PhysicalPackages.THROUGH_HOLE_LED,
            new FixedProvider(new LedRenderer()));
        registry.register(PhysicalPackages.TO92_NPN,
            new FixedProvider(new NpnRenderer()));
        registry.register(PhysicalPackages.TO92_NMOS,
            new FixedProvider(new NmosRenderer()));
        registry.register(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            new FixedProvider(new ElectrolyticCapacitorRenderer()));
        registry.register(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            new FixedProvider(new CeramicCapacitorRenderer()));
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
            Rectangle bounds = context.getInstalledSelectionBounds();
            Vector<PhysicalPartRenderHitRegion> hits = new Vector<PhysicalPartRenderHitRegion>();
            hits.add(new PhysicalPartRenderHitRegion(bounds));
            Vector<Rectangle> leads = new Vector<Rectangle>();
            for (PhysicalPartRenderTerminal terminal : terminals)
                leads.add(terminal.getLeadBounds());
            return new PhysicalPartRenderGeometry(terminals, hits, bounds,
                context.getInstalledBodyBounds(), leads, context.getInstalledDragBounds());
        }

        protected PhysicalPartRenderGeometry looseGeometry(PhysicalPartRenderContext context,
                Vector<PhysicalPartRenderTerminal> terminals, boolean reversed) {
            Rectangle bounds = context.getLooseSelectionBounds(reversed);
            Vector<PhysicalPartRenderHitRegion> hits = new Vector<PhysicalPartRenderHitRegion>();
            hits.add(new PhysicalPartRenderHitRegion(bounds));
            Vector<Rectangle> leads = new Vector<Rectangle>();
            for (int index = 0; index < terminals.size(); index++)
                leads.add(context.getLooseLeadBounds(index, reversed));
            return new PhysicalPartRenderGeometry(terminals, hits, bounds,
                context.getLooseBodyBounds(reversed), leads,
                context.getLooseDragBounds(reversed));
        }

        protected Vector<PhysicalPartRenderTerminal> installedTerminals(
                PhysicalPartRenderContext context) {
            Vector<PhysicalPartRenderTerminal> result = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++)
                result.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    context.getBoardPadId(index), context.getComponentProbePoint(index),
                    context.getInstalledProbeBounds(index), context.getInstalledPadBounds(index),
                    context.getInstalledLeadBounds(index)));
            return result;
        }

        protected Vector<PhysicalPartRenderTerminal> looseTerminals(
                PhysicalPartRenderContext context, boolean reversed) {
            Vector<PhysicalPartRenderTerminal> result = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++)
                result.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    null, context.getLooseTerminalPoint(index, reversed),
                    context.getLooseProbeBounds(index, reversed),
                    context.getLoosePadBounds(index, reversed),
                    context.getLooseLeadBounds(index, reversed)));
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

    private static final class CapacitorMetadataAdapter {
        CapacitorSpecification require(PhysicalPart<?> part) {
            PhysicalPartRenderMetadata metadata = requireMetadata(part, "capacitor");
            if (!(metadata.getVisualSpecification() instanceof CapacitorSpecification))
                throw new IllegalStateException("Capacitor package has non-capacitor metadata: " +
                    part.getId());
            return (CapacitorSpecification) metadata.getVisualSpecification();
        }
    }

    private static final ResistorMetadataAdapter RESISTOR_METADATA =
        new ResistorMetadataAdapter();
    private static final DiodeMetadataAdapter DIODE_METADATA =
        new DiodeMetadataAdapter();
    private static final LedMetadataAdapter LED_METADATA =
        new LedMetadataAdapter();
    private static final CapacitorMetadataAdapter CAPACITOR_METADATA =
        new CapacitorMetadataAdapter();

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
            Rectangle body = geometry.getBodyBounds();
            Point bodyLead1 = context.getInstalledLeadBodyPoint(0);
            Point bodyLead2 = context.getInstalledLeadBodyPoint(1);
            drawLead(graphics, bodyLead1, context.getMountedLeadEnd(0),
                Math.max(3, context.scale(4)));
            drawLead(graphics, bodyLead2, context.getMountedLeadEnd(1),
                Math.max(3, context.scale(4)));
            drawResistorBody(graphics, context, nameplate, body);
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

        private void drawResistorBody(Graphics graphics, PhysicalPartRenderContext context,
                ResistorNameplate nameplate, Rectangle body) {
            graphics.setColor("#d9c79b");
            graphics.fillRect(body.x, body.y, body.width, body.height);
            graphics.setColor("#302a22");
            graphics.drawRect(body.x, body.y, body.width, body.height);
            ResistorColorBand[] bands = ResistorColorCode.getFourBandCode(nameplate);
            for (int index = 0; index < bands.length; index++) {
                int x = body.x + body.width * (index + 1) / 5;
                graphics.setColor(bandColor(bands[index]));
                graphics.fillRect(x - Math.max(2, context.scale(3)), body.y,
                    Math.max(4, context.scale(6)), body.height);
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
            Rectangle body = geometry.getBodyBounds();
            Point leftPad = context.getBoardPadPoint(0);
            Point rightPad = context.getBoardPadPoint(1);
            if (leftPad == null || rightPad == null)
                return;
            drawLead(graphics, context.getInstalledLeadBodyPoint(0), context.getMountedLeadEnd(0),
                Math.max(3, context.scale(4)));
            drawLead(graphics, context.getInstalledLeadBodyPoint(1), context.getMountedLeadEnd(1),
                Math.max(3, context.scale(4)));
            graphics.setColor("#282c31");
            graphics.fillRect(body.x, body.y, body.width, body.height);
            graphics.setColor("#111315");
            graphics.drawRect(body.x, body.y, body.width, body.height);
            drawCathodeBand(graphics, reversed ? body.x : body.x + body.width,
                body.y + body.height / 2, body.height, reversed, context);
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
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            drawLedLead(graphics, context.getMountedLeadEnd(0),
                context.getInstalledLeadBodyPoint(0));
            drawLedLead(graphics, context.getMountedLeadEnd(1),
                context.getInstalledLeadBodyPoint(1));
            graphics.setColor("#b5232d");
            if (context.isIlluminated()) {
                graphics.setColor("#ffdc4f");
                graphics.fillOval(centerX - radius - context.scale(9), centerY - radius - context.scale(9),
                    radius * 2 + context.scale(18), radius * 2 + context.scale(18));
                graphics.setColor("#b5232d");
            }
            graphics.fillOval(body.x, body.y, body.width, body.height);
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

    /** TO-92 package renderer; B/C/E geometry and loose targets stay here. */
    private static final class NpnRenderer extends BaseRenderer {
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
            Point base = context.getBoardPadPoint(0);
            Point collector = context.getBoardPadPoint(1);
            Point emitter = context.getBoardPadPoint(2);
            if (base == null || collector == null || emitter == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            drawLead(graphics, context.getMountedLeadEnd(0),
                context.getInstalledLeadBodyPoint(0));
            drawLead(graphics, context.getMountedLeadEnd(1),
                context.getInstalledLeadBodyPoint(1));
            drawLead(graphics, context.getMountedLeadEnd(2),
                context.getInstalledLeadBodyPoint(2));
            graphics.setColor("#2f6680");
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#c7e0ea");
            graphics.drawRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#eef5f1");
            graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(9, context.scale(11))));
            graphics.drawString("NPN", centerX - context.scale(16), centerY + context.scale(4));
            graphics.drawString("B", base.x - context.scale(5), base.y + context.scale(22));
            graphics.drawString("C", collector.x - context.scale(5), collector.y + context.scale(22));
            graphics.drawString("E", emitter.x - context.scale(5), emitter.y + context.scale(22));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            Point base = context.getLooseTerminalPoint(0, false);
            Point collector = context.getLooseTerminalPoint(1, false);
            Point emitter = context.getLooseTerminalPoint(2, false);
            int centerX = (collector.x + emitter.x) / 2;
            int centerY = base.y;
            int radius = Math.max(15, context.scale(21));
            drawLead(graphics, base, new Point(centerX - radius, centerY));
            drawLead(graphics, collector, new Point(centerX - context.scale(7), centerY));
            drawLead(graphics, new Point(centerX + context.scale(7), centerY), emitter);
            graphics.setColor("#2f6680");
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#c7e0ea");
            graphics.drawRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "NPN",
                Math.min(base.x, Math.min(collector.x, emitter.x)) + context.scale(20),
                centerY - context.scale(27));
            context.markBodyDrawn();
        }
    }

    /** TO-92-like NMOS renderer; physical terminals are G/D/S, never fault markings. */
    private static final class NmosRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            return installedGeometry(context, installedTerminals(context));
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return looseGeometry(context, looseTerminals(context, false), false);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.isComponentRemoved() || context.getPart() == null) return;
            Point gate = context.getBoardPadPoint(0);
            Point drain = context.getBoardPadPoint(1);
            Point source = context.getBoardPadPoint(2);
            if (gate == null || drain == null || source == null) return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            drawLead(graphics, context.getMountedLeadEnd(0),
                context.getInstalledLeadBodyPoint(0));
            drawLead(graphics, context.getMountedLeadEnd(1),
                context.getInstalledLeadBodyPoint(1));
            drawLead(graphics, context.getMountedLeadEnd(2),
                context.getInstalledLeadBodyPoint(2));
            graphics.setColor("#2f6680");
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#c7e0ea");
            graphics.drawRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#eef5f1");
            graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(9, context.scale(11))));
            graphics.drawString("NMOS", centerX - context.scale(20), centerY + context.scale(4));
            graphics.drawString("G", gate.x - context.scale(5), gate.y + context.scale(22));
            graphics.drawString("D", drain.x - context.scale(5), drain.y + context.scale(22));
            graphics.drawString("S", source.x - context.scale(5), source.y + context.scale(22));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null) return;
            Point gate = context.getLooseTerminalPoint(0, false);
            Point drain = context.getLooseTerminalPoint(1, false);
            Point source = context.getLooseTerminalPoint(2, false);
            int centerX = (drain.x + source.x) / 2;
            int centerY = gate.y;
            int radius = Math.max(15, context.scale(21));
            drawLead(graphics, gate, new Point(centerX - radius, centerY));
            drawLead(graphics, drain, new Point(centerX - context.scale(7), centerY));
            drawLead(graphics, new Point(centerX + context.scale(7), centerY), source);
            graphics.setColor("#2f6680");
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#c7e0ea");
            graphics.drawRect(centerX - radius, centerY - radius, radius * 2, radius * 2);
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "NMOS",
                Math.min(gate.x, Math.min(drain.x, source.x)) + context.scale(20),
                centerY - context.scale(27));
            context.markBodyDrawn();
        }
    }

    /** Radial, polarized package provider. Its plus/minus geometry stays package-owned. */
    private static final class ElectrolyticCapacitorRenderer extends BaseRenderer {
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
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Point plus = context.getBoardPadPoint(0);
            Point minus = context.getBoardPadPoint(1);
            if (plus == null || minus == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            drawLead(graphics, context.getMountedLeadEnd(0),
                context.getInstalledLeadBodyPoint(0), Math.max(3, context.scale(4)));
            drawLead(graphics, context.getMountedLeadEnd(1),
                context.getInstalledLeadBodyPoint(1), Math.max(3, context.scale(4)));
            drawElectrolyticBody(graphics, context, specification, centerX, centerY, radius);
            graphics.setColor("#f7f5e8");
            graphics.drawString("+", plus.x - context.scale(5), plus.y - context.scale(10));
            graphics.drawString("-", minus.x - context.scale(4), minus.y - context.scale(10));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Point plus = context.getLooseTerminalPoint(0, false);
            Point minus = context.getLooseTerminalPoint(1, false);
            int centerX = (plus.x + minus.x) / 2;
            int centerY = plus.y;
            int radius = Math.max(13, context.scale(20));
            drawLead(graphics, plus, new Point(centerX - context.scale(10), centerY));
            drawLead(graphics, new Point(centerX + context.scale(10), centerY), minus);
            drawElectrolyticBody(graphics, context, specification, centerX, centerY, radius);
            graphics.setColor("#f7f5e8");
            graphics.drawString("+", plus.x + context.scale(4), plus.y - context.scale(8));
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "CAPACITOR",
                plus.x + context.scale(20), plus.y - context.scale(26));
            context.markBodyDrawn();
        }

        private void drawElectrolyticBody(Graphics graphics, PhysicalPartRenderContext context,
                CapacitorSpecification specification, int centerX, int centerY, int radius) {
            graphics.setColor("#35576d");
            graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            graphics.setColor("#182833");
            graphics.drawRect(centerX - radius + context.scale(2),
                centerY - radius + context.scale(2), radius * 2 - context.scale(4),
                radius * 2 - context.scale(4));
            graphics.setColor("#d9e1df");
            graphics.fillRect(centerX + radius / 3, centerY - radius + context.scale(3),
                Math.max(4, context.scale(7)), radius * 2 - context.scale(6));
            graphics.setColor("#eff4ed");
            graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(8, context.scale(10))));
            graphics.drawString(specification.getNameplate().getMarking(),
                centerX - radius + context.scale(4), centerY + context.scale(4));
        }
    }

    /** Compact non-polarized ceramic provider with its code marking rendered on the body. */
    private static final class CeramicCapacitorRenderer extends BaseRenderer {
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
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Point first = context.getBoardPadPoint(0);
            Point second = context.getBoardPadPoint(1);
            if (first == null || second == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            drawLead(graphics, context.getMountedLeadEnd(0),
                context.getInstalledLeadBodyPoint(0));
            drawLead(graphics, context.getMountedLeadEnd(1),
                context.getInstalledLeadBodyPoint(1));
            drawCeramicBody(graphics, context, specification, centerX, centerY,
                body.width / 2, body.height / 2);
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Point first = context.getLooseTerminalPoint(0, false);
            Point second = context.getLooseTerminalPoint(1, false);
            int centerX = (first.x + second.x) / 2;
            drawLead(graphics, first, new Point(centerX - context.scale(12), first.y));
            drawLead(graphics, new Point(centerX + context.scale(12), first.y), second);
            drawCeramicBody(graphics, context, specification, centerX, first.y,
                Math.max(12, context.scale(17)), Math.max(10, context.scale(13)));
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "CERAMIC",
                first.x + context.scale(20), first.y - context.scale(26));
            context.markBodyDrawn();
        }

        private void drawCeramicBody(Graphics graphics, PhysicalPartRenderContext context,
                CapacitorSpecification specification, int centerX, int centerY, int halfWidth,
                int halfHeight) {
            graphics.setColor("#bd8a54");
            graphics.fillRect(centerX - halfWidth, centerY - halfHeight, halfWidth * 2,
                halfHeight * 2);
            graphics.setColor("#5b402c");
            graphics.drawRect(centerX - halfWidth, centerY - halfHeight, halfWidth * 2,
                halfHeight * 2);
            graphics.setColor("#2b211a");
            graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(8, context.scale(10))));
            graphics.drawString(specification.getNameplate().getMarking(),
                centerX - halfWidth + context.scale(3), centerY + context.scale(4));
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
            Rectangle bounds = context.getInstalledBodyBounds();
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
                    context.getBoardPadId(index), context.getComponentProbePoint(index),
                    context.getInstalledProbeBounds(index), context.getInstalledPadBounds(index),
                    context.getInstalledLeadBounds(index)));
            return installedGeometry(context, terminals);
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return looseGeometry(context, looseTerminals(context, false), false);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            Rectangle bounds = context.getInstalledBodyBounds();
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
