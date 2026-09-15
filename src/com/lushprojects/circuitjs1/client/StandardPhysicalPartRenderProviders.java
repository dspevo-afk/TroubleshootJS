package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Built-in physical render providers keyed by typed package definitions. */
final class StandardPhysicalPartRenderProviders {
    private StandardPhysicalPartRenderProviders() { }

    /** Explicit fixed renderer used by developer surface fixtures without a provider probe. */
    static PhysicalPartRenderer createMultiTerminalRenderer() { return new MultiTerminalRenderer(); }

    static PhysicalPartRenderRegistry createRegistry() {
        PhysicalPartRenderRegistry registry = new PhysicalPartRenderRegistry();
        registry.register(P06FactoryLinkFixtures.TEST_POINT, new FixedProvider(new MultiTerminalRenderer()));
        registry.register(PhysicalPackages.RELAY_SPDT, new FixedProvider(new RelayRenderer()));
        registry.register(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            new FixedProvider(new ConnectorRenderer()));
        registry.register(PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            new FixedProvider(new ConnectorRenderer()));
        registry.register(PhysicalPackages.AXIAL_RESISTOR,
            new FixedProvider(new ResistorRenderer()));
        registry.register(PhysicalPackages.AXIAL_FUSE, new FixedProvider(new FuseRenderer()));
        registry.register(PhysicalPackages.RAISED_FACTORY_LINK, new FixedProvider(new FactoryLinkRenderer()));
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
            hits.add(new PhysicalPartRenderHitRegion(context.getInstalledBodyBounds()));
            Vector<Rectangle> leads = new Vector<Rectangle>();
            for (PhysicalPartRenderTerminal terminal : terminals) {
                leads.add(terminal.getLeadBounds());
                for (Rectangle segment : context.getInstalledLeadSegmentBounds(terminal.getTerminalIndex()))
                    hits.add(new PhysicalPartRenderHitRegion(segment));
                if (terminal.getBoardPadProbeBounds() != null)
                    hits.add(new PhysicalPartRenderHitRegion(
                        terminal.getBoardPadProbeBounds()));
                hits.add(new PhysicalPartRenderHitRegion(
                    terminal.getComponentLeadProbeBounds()));
            }
            return new PhysicalPartRenderGeometry(terminals, hits, bounds,
                context.getInstalledBodyBounds(), leads, context.getInstalledDragBounds());
        }

        protected PhysicalPartRenderGeometry looseGeometry(PhysicalPartRenderContext context,
                Vector<PhysicalPartRenderTerminal> terminals, boolean reversed) {
            Rectangle bounds = context.getLooseSelectionBounds(reversed);
            Vector<PhysicalPartRenderHitRegion> hits = new Vector<PhysicalPartRenderHitRegion>();
            hits.add(new PhysicalPartRenderHitRegion(context.getLooseBodyBounds(reversed)));
            Vector<Rectangle> leads = new Vector<Rectangle>();
            for (PhysicalPartRenderTerminal terminal : terminals) {
                int index = terminal.getTerminalIndex();
                leads.add(terminal.getLeadBounds());
                hits.add(new PhysicalPartRenderHitRegion(terminal.getLeadBounds()));
                hits.add(new PhysicalPartRenderHitRegion(terminal.getPadBounds()));
                hits.add(new PhysicalPartRenderHitRegion(
                    terminal.getComponentLeadProbeBounds()));
                for (Rectangle surface : terminal.getProbeSurfaces())
                    hits.add(new PhysicalPartRenderHitRegion(surface));
            }
            return new PhysicalPartRenderGeometry(terminals, hits, bounds,
                context.getLooseBodyBounds(reversed), leads,
                context.getLooseDragBounds(reversed));
        }

        protected Vector<PhysicalPartRenderTerminal> installedTerminals(
                PhysicalPartRenderContext context) {
            Vector<PhysicalPartRenderTerminal> result = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++) {
                result.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    context.getBoardPadId(index), context.getComponentProbePoint(index),
                    context.getInstalledProbeBounds(index), context.getInstalledBoardPadPoint(index),
                    context.getInstalledBoardPadProbeBounds(index),
                    context.getInstalledPadBounds(index), context.getInstalledComponentLeadPoint(index),
                    context.getInstalledComponentLeadProbeBounds(index),
                    context.getInstalledLeadBodyPoint(index), context.getInstalledLeadEndPoint(index),
                    context.getInstalledLeadBounds(index)));
            }
            return result;
        }

        protected PhysicalPartRenderTerminal installedTerminal(
                PhysicalPartRenderGeometry geometry, int terminal) {
            PhysicalPartRenderTerminal result = geometry.getTerminal(terminal);
            if (result == null)
                throw new IllegalArgumentException("Unknown installed terminal: " + terminal);
            return result;
        }

        protected void drawInstalledLead(Graphics graphics, PhysicalPartRenderContext context, PhysicalPartRenderGeometry geometry,
                int terminal) {
            drawInstalledLead(graphics, context, geometry, terminal, Math.max(2, context.scale(3)));
        }

        protected void drawInstalledLead(Graphics graphics, PhysicalPartRenderContext context, PhysicalPartRenderGeometry geometry,
                int terminal, int width) {
            Vector<Point> path = context.getInstalledLeadPath(terminal);
            for (int i = 1; i < path.size(); i++) drawLead(graphics, path.get(i-1), path.get(i), width);
            if (!context.isLeadConnected(terminal)) {
                Rectangle tip = context.getInstalledComponentLeadProbeBounds(terminal);
                WorkbenchVisualTheme.ellipse(graphics, tip.x, tip.y, tip.width, tip.height, WorkbenchVisualTheme.METAL_LIGHT);
            }
        }

        protected Vector<PhysicalPartRenderTerminal> looseTerminals(
                PhysicalPartRenderContext context, boolean reversed) {
            Vector<PhysicalPartRenderTerminal> result = new Vector<PhysicalPartRenderTerminal>();
            for (int index = 0; index < context.getTerminalCount(); index++)
                result.add(new PhysicalPartRenderTerminal(index, context.getTerminalName(index),
                    null, context.getLooseTerminalPoint(index, reversed),
                    context.getLooseProbeBounds(index, reversed),
                    null, null, context.getLoosePadBounds(index, reversed),
                    context.getLooseComponentLeadPoint(index),
                    context.getLooseComponentLeadProbeBounds(index),
                    context.getLooseLeadBodyPoint(index), context.getLooseLeadEndPoint(index),
                    context.getLooseLeadBounds(index, reversed),
                    context.getLooseProbeSurfaces(index)));
            return result;
        }

        protected void drawLooseLeads(Graphics graphics, PhysicalPartRenderGeometry geometry,
                PhysicalPartRenderContext context) {
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals())
                drawLead(graphics, terminal.getLeadBodyPoint(), terminal.getLeadEndPoint(),
                    context.getLooseLeadStrokeWidth(terminal.getLeadBounds()));
        }

        protected void drawSelection(Graphics graphics, PhysicalPartRenderGeometry geometry) {
            Rectangle bounds = geometry.getSelectionBounds();
            graphics.setColor(WorkbenchVisualTheme.SELECTION);
            graphics.setLineWidth(2);
            graphics.drawRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8);
            graphics.setLineWidth(1);
        }

        protected void drawLead(Graphics graphics, Point start, Point end) {
            WorkbenchVisualTheme.lead(graphics, start, end, 3);
        }

        protected void drawLead(Graphics graphics, Point start, Point end, int width) {
            WorkbenchVisualTheme.lead(graphics, start, end, width);
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
            if (!context.isInstalledPartMounted())
                return;
            ResistorNameplate nameplate = RESISTOR_METADATA.require(context.getPart());
            Rectangle body = geometry.getBodyBounds();
            int leadWidth = Math.max(3, context.scale(4));
            PhysicalPartRenderTerminal lead1 = installedTerminal(geometry, 0);
            PhysicalPartRenderTerminal lead2 = installedTerminal(geometry, 1);
            drawInstalledLead(graphics, context, geometry, 0, leadWidth);
            drawInstalledLead(graphics, context, geometry, 1, leadWidth);
            drawResistorBody(graphics, context, nameplate, body);
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            ResistorNameplate nameplate = RESISTOR_METADATA.require(context.getPart());
            Rectangle body = geometry.getBodyBounds();
            drawLooseLeads(graphics, geometry, context);
            drawResistorBody(graphics, context, nameplate, body);
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "RESISTOR",
                body.x, body.y - context.scale(8));
            context.markBodyDrawn();
        }

        private void drawResistorBody(Graphics graphics, PhysicalPartRenderContext context,
                ResistorNameplate nameplate, Rectangle body) {
            WorkbenchVisualTheme.body(graphics, body, WorkbenchVisualTheme.RESISTOR,
                "#ece0c0", true);
            boolean horizontal = body.width >= body.height;
            int length = horizontal ? body.width : body.height;
            int bandWidth = Math.max(1, Math.min(context.scale(6), length / 9));
            ResistorColorBand[] bands = ResistorColorCode.getFourBandCode(nameplate);
            graphics.context.save();
            WorkbenchVisualTheme.roundedPath(graphics, body.x, body.y, body.width,
                body.height, Math.min(body.width, body.height) * .38);
            graphics.context.clip();
            for (int index = 0; index < bands.length; index++) {
                int position = length * (index == 3 ? 80 : 20 + index * 17) / 100;
                graphics.setColor(bandColor(bands[index]));
                if (horizontal)
                    graphics.fillRect(body.x + position - bandWidth / 2, body.y, bandWidth, body.height);
                else
                    graphics.fillRect(body.x, body.y + position - bandWidth / 2, body.width, bandWidth);
            }
            graphics.setColor("rgba(255,255,255,0.16)");
            if (horizontal)
                graphics.fillRect(body.x + 2, body.y + Math.max(1, body.height / 4),
                    Math.max(0, body.width - 4), Math.max(1, body.height / 7));
            else
                graphics.fillRect(body.x + Math.max(1, body.width / 4), body.y + 2,
                    Math.max(1, body.width / 7), Math.max(0, body.height - 4));
            graphics.context.restore();
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
            if (!context.isInstalledPartMounted()) return;
            DIODE_METADATA.require(context.getPart());
            boolean reversed = DIODE_METADATA.isReversed(context.getPart());
            Point first = installedTerminal(geometry, 0).getBoardPadPoint();
            Point second = installedTerminal(geometry, 1).getBoardPadPoint();
            if (first == null || second == null) return;
            drawInstalledLead(graphics, context, geometry, 0, Math.max(3, context.scale(4)));
            drawInstalledLead(graphics, context, geometry, 1, Math.max(3, context.scale(4)));
            Point cathode = reversed ? first : second;
            drawBody(graphics, context, geometry.getBodyBounds(), reversed ? second : first, cathode);
            graphics.setColor(WorkbenchVisualTheme.SILK);
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, 0, Math.max(9, context.scale(11))));
            graphics.drawString("K", cathode.x - context.scale(4), cathode.y + context.scale(26));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null) return;
            DIODE_METADATA.require(context.getPart());
            drawLooseLeads(graphics, geometry, context);
            Rectangle body = geometry.getBodyBounds();
            drawBody(graphics, context, body, geometry.getTerminal(0).getPoint(),
                geometry.getTerminal(1).getPoint());
            drawPartLabel(graphics, context, selected ? "SELECTED" : "DIODE",
                body.x, body.y - context.scale(8));
            context.markBodyDrawn();
        }

        private boolean isReversed(PhysicalPartRenderContext context) {
            return context.getPart() != null && DIODE_METADATA.isReversed(context.getPart());
        }

        private void drawBody(Graphics graphics, PhysicalPartRenderContext context,
                Rectangle body, Point anode, Point cathode) {
            WorkbenchVisualTheme.body(graphics, body, WorkbenchVisualTheme.BODY,
                WorkbenchVisualTheme.BODY_LIGHT, true);
            WorkbenchVisualTheme.polarityStripe(graphics, body, anode, cathode,
                Math.max(3, context.scale(7)), WorkbenchVisualTheme.METAL_LIGHT);
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
            if (!context.isInstalledPartMounted()) return;
            LedNameplate nameplate = LED_METADATA.require(context.getPart());
            Point first = installedTerminal(geometry, 0).getBoardPadPoint();
            Point second = installedTerminal(geometry, 1).getBoardPadPoint();
            if (first == null || second == null) return;
            drawInstalledLead(graphics, context, geometry, 0);
            drawInstalledLead(graphics, context, geometry, 1);
            boolean reversed = LED_METADATA.isReversed(context.getPart());
            Point cathode = reversed ? first : second;
            drawLens(graphics, context, nameplate, geometry.getBodyBounds(),
                reversed ? second : first, cathode, context.isIlluminated());
            graphics.setColor(WorkbenchVisualTheme.SILK);
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, 0, Math.max(9, context.scale(11))));
            graphics.drawString("K", cathode.x - context.scale(4), cathode.y + context.scale(26));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null) return;
            LedNameplate nameplate = LED_METADATA.require(context.getPart());
            drawLooseLeads(graphics, geometry, context);
            Rectangle body = geometry.getBodyBounds();
            drawLens(graphics, context, nameplate, body, geometry.getTerminal(0).getPoint(),
                geometry.getTerminal(1).getPoint(), false);
            drawPartLabel(graphics, context, selected ? "SELECTED" : "LED",
                body.x, body.y - context.scale(8));
            context.markBodyDrawn();
        }

        private boolean isReversed(PhysicalPartRenderContext context) {
            return context.getPart() != null && LED_METADATA.isReversed(context.getPart());
        }

        private void drawLens(Graphics graphics, PhysicalPartRenderContext context,
                LedNameplate nameplate, Rectangle b, Point anode, Point cathode, boolean illuminated) {
            WorkbenchVisualTheme.ellipse(graphics, b.x + 1, b.y + 2, b.width, b.height,
                WorkbenchVisualTheme.SHADOW);
            WorkbenchVisualTheme.ellipse(graphics, b.x, b.y, b.width, b.height,
                WorkbenchVisualTheme.BODY_EDGE);
            int rim = Math.max(1, Math.min(b.width, b.height) / 12);
            WorkbenchVisualTheme.ellipse(graphics, b.x + rim, b.y + rim,
                b.width - rim * 2, b.height - rim * 2, lensColor(nameplate, 24, illuminated ? 218 : 128));
            WorkbenchVisualTheme.ellipse(graphics, b.x + b.width * .25, b.y + b.height * .17,
                b.width * .35, b.height * .27, illuminated ? lensColor(nameplate, 174, 80) :
                "rgba(255,255,255,0.27)");
            graphics.context.save();
            WorkbenchVisualTheme.ellipsePath(graphics, b.x, b.y, b.width, b.height);
            graphics.context.clip();
            WorkbenchVisualTheme.polarityStripe(graphics, b, anode, cathode,
                Math.max(2, context.scale(4)), WorkbenchVisualTheme.SILK);
            graphics.context.restore();
        }

        private String lensColor(LedNameplate nameplate, int base, int range) {
            return "rgb(" + (base + Math.round(nameplate.getRed() * range)) + "," +
                (base + Math.round(nameplate.getGreen() * range)) + "," +
                (base + Math.round(nameplate.getBlue() * range)) + ")";
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
            if (!context.isInstalledPartMounted())
                return;
            Point base = installedTerminal(geometry, 0).getBoardPadPoint();
            Point collector = installedTerminal(geometry, 1).getBoardPadPoint();
            Point emitter = installedTerminal(geometry, 2).getBoardPadPoint();
            if (base == null || collector == null || emitter == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            drawInstalledLead(graphics, context, geometry, 0);
            drawInstalledLead(graphics, context, geometry, 1);
            drawInstalledLead(graphics, context, geometry, 2);
            drawTransistorBody(graphics, body, geometry.getTerminal(1).getLeadBodyPoint());
            graphics.setColor(WorkbenchVisualTheme.SILK);
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, Font.BOLD, Math.max(9, context.scale(11))));
            WorkbenchVisualTheme.marking(graphics, "NPN", body, Math.max(9, context.scale(11)),
                WorkbenchVisualTheme.SILK);
            graphics.drawString("B", base.x - context.scale(5), base.y + context.scale(22));
            graphics.drawString("C", collector.x - context.scale(5), collector.y + context.scale(22));
            graphics.drawString("E", emitter.x - context.scale(5), emitter.y + context.scale(22));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.max(1, Math.min(body.width, body.height) / 2);
            drawLooseLeads(graphics, geometry, context);
            drawTransistorBody(graphics, body, geometry.getTerminal(1).getLeadBodyPoint());
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "NPN",
                body.x, body.y - context.scale(8));
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
            if (!context.isInstalledPartMounted()) return;
            Point gate = installedTerminal(geometry, 0).getBoardPadPoint();
            Point drain = installedTerminal(geometry, 1).getBoardPadPoint();
            Point source = installedTerminal(geometry, 2).getBoardPadPoint();
            if (gate == null || drain == null || source == null) return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            drawInstalledLead(graphics, context, geometry, 0);
            drawInstalledLead(graphics, context, geometry, 1);
            drawInstalledLead(graphics, context, geometry, 2);
            drawTransistorBody(graphics, body, geometry.getTerminal(1).getLeadBodyPoint());
            graphics.setColor(WorkbenchVisualTheme.SILK);
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, Font.BOLD, Math.max(9, context.scale(11))));
            WorkbenchVisualTheme.marking(graphics, "NMOS", body, Math.max(9, context.scale(11)),
                WorkbenchVisualTheme.SILK);
            graphics.drawString("G", gate.x - context.scale(5), gate.y + context.scale(22));
            graphics.drawString("D", drain.x - context.scale(5), drain.y + context.scale(22));
            graphics.drawString("S", source.x - context.scale(5), source.y + context.scale(22));
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null) return;
            Rectangle body = geometry.getBodyBounds();
            drawLooseLeads(graphics, geometry, context);
            drawTransistorBody(graphics, body, geometry.getTerminal(1).getLeadBodyPoint());
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "NMOS",
                body.x, body.y - context.scale(8));
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
            if (!context.isInstalledPartMounted())
                return;
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Point plus = installedTerminal(geometry, 0).getBoardPadPoint();
            Point minus = installedTerminal(geometry, 1).getBoardPadPoint();
            if (plus == null || minus == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.min(body.width, body.height) / 2;
            int leadWidth = Math.max(3, context.scale(4));
            drawInstalledLead(graphics, context, geometry, 0, leadWidth);
            drawInstalledLead(graphics, context, geometry, 1, leadWidth);
            drawElectrolyticBody(graphics, context, specification, body, plus, minus);
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
            Point plus = geometry.getTerminal(0).getPoint();
            Point minus = geometry.getTerminal(1).getPoint();
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            int radius = Math.max(1, Math.min(body.width, body.height) / 2);
            drawLooseLeads(graphics, geometry, context);
            drawElectrolyticBody(graphics, context, specification, body, plus, minus);
            graphics.setColor("#f7f5e8");
            graphics.drawString("+", plus.x + context.scale(4), plus.y - context.scale(8));
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "CAPACITOR",
                body.x, body.y - context.scale(8));
            context.markBodyDrawn();
        }

        private void drawElectrolyticBody(Graphics graphics, PhysicalPartRenderContext context,
                CapacitorSpecification specification, Rectangle b, Point plus, Point minus) {
            WorkbenchVisualTheme.ellipse(graphics, b.x + 1, b.y + 2, b.width, b.height,
                WorkbenchVisualTheme.SHADOW);
            WorkbenchVisualTheme.ellipse(graphics, b.x, b.y, b.width, b.height,
                WorkbenchVisualTheme.BODY_EDGE);
            int rim = Math.max(1, Math.min(b.width, b.height) / 10);
            WorkbenchVisualTheme.ellipse(graphics, b.x + rim, b.y + rim,
                b.width - rim * 2, b.height - rim * 2, WorkbenchVisualTheme.BODY_LIGHT);
            graphics.context.save();
            WorkbenchVisualTheme.ellipsePath(graphics, b.x + rim, b.y + rim,
                b.width - rim * 2, b.height - rim * 2);
            graphics.context.clip();
            WorkbenchVisualTheme.polarityStripe(graphics, b, plus, minus,
                Math.max(2, Math.min(b.width, b.height) / 5), WorkbenchVisualTheme.METAL_LIGHT);
            graphics.context.restore();
            int inset = Math.max(2, Math.min(b.width, b.height) / 4);
            WorkbenchVisualTheme.ellipse(graphics, b.x + inset, b.y + inset,
                b.width - inset * 2, b.height - inset * 2, WorkbenchVisualTheme.METAL);
            WorkbenchVisualTheme.marking(graphics, specification.getNameplate().getMarking(),
                b, Math.max(8, context.scale(10)), WorkbenchVisualTheme.SILK);
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
            if (!context.isInstalledPartMounted())
                return;
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Point first = installedTerminal(geometry, 0).getBoardPadPoint();
            Point second = installedTerminal(geometry, 1).getBoardPadPoint();
            if (first == null || second == null)
                return;
            Rectangle body = geometry.getBodyBounds();
            int centerX = body.x + body.width / 2;
            int centerY = body.y + body.height / 2;
            drawInstalledLead(graphics, context, geometry, 0);
            drawInstalledLead(graphics, context, geometry, 1);
            drawCeramicBody(graphics, context, specification, centerX, centerY,
                body.width / 2, body.height / 2);
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (context.getPart() == null)
                return;
            CapacitorSpecification specification = CAPACITOR_METADATA.require(context.getPart());
            Rectangle body = geometry.getBodyBounds();
            drawLooseLeads(graphics, geometry, context);
            drawCeramicBody(graphics, context, specification, body.x + body.width / 2,
                body.y + body.height / 2, body.width / 2, body.height / 2);
            drawPartLabel(graphics, context, context.getPart().getId().equals(
                context.getRenderer().getSelectedPartForProvider()) ? "SELECTED" : "CERAMIC",
                body.x, body.y - context.scale(8));
            context.markBodyDrawn();
        }

        private void drawCeramicBody(Graphics graphics, PhysicalPartRenderContext context,
                CapacitorSpecification specification, int centerX, int centerY, int halfWidth,
                int halfHeight) {
            Rectangle body = new Rectangle(centerX - halfWidth, centerY - halfHeight,
                halfWidth * 2, halfHeight * 2);
            WorkbenchVisualTheme.ellipse(graphics, body.x + 1, body.y + 2, body.width, body.height,
                WorkbenchVisualTheme.SHADOW);
            WorkbenchVisualTheme.ellipse(graphics, body.x, body.y, body.width, body.height,
                "#8e5938");
            WorkbenchVisualTheme.ellipse(graphics, body.x + 1, body.y + 1,
                Math.max(1, body.width - 2), Math.max(1, body.height - 3), WorkbenchVisualTheme.CERAMIC);
            WorkbenchVisualTheme.ellipse(graphics, body.x + body.width * .2, body.y + body.height * .12,
                body.width * .4, body.height * .2, "rgba(255,237,199,0.24)");
            WorkbenchVisualTheme.marking(graphics, specification.getNameplate().getMarking(),
                body, Math.max(8, context.scale(10)), "#362919");
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
            if (!context.isInstalledPartMounted()) return;
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals())
                drawInstalledLead(graphics, context, geometry, terminal.getTerminalIndex());
            WorkbenchVisualTheme.body(graphics, geometry.getBodyBounds(), WorkbenchVisualTheme.CONNECTOR,
                WorkbenchVisualTheme.CONNECTOR_LIGHT, false);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Point point = terminal.getBoardPadPoint();
                if (point != null) drawScrew(graphics, point, terminal.getPadBounds());
            }
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            drawLooseLeads(graphics, geometry, context);
            WorkbenchVisualTheme.body(graphics, geometry.getBodyBounds(), WorkbenchVisualTheme.CONNECTOR,
                WorkbenchVisualTheme.CONNECTOR_LIGHT, false);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals())
                drawScrew(graphics, terminal.getPoint(), terminal.getPadBounds());
            context.markBodyDrawn();
        }

        private void drawScrew(Graphics graphics, Point point, Rectangle bounds) {
            int radius = Math.max(1, Math.min(bounds.width, bounds.height) / 2);
            WorkbenchVisualTheme.ellipse(graphics, point.x - radius, point.y - radius,
                radius * 2, radius * 2, WorkbenchVisualTheme.BODY_EDGE);
            WorkbenchVisualTheme.ellipse(graphics, point.x - radius + 1, point.y - radius + 1,
                Math.max(1, radius * 2 - 2), Math.max(1, radius * 2 - 2), WorkbenchVisualTheme.METAL_LIGHT);
            graphics.setColor(WorkbenchVisualTheme.METAL);
            graphics.setLineWidth(1);
            graphics.drawLine(point.x - radius / 2, point.y + radius / 2,
                point.x + radius / 2, point.y - radius / 2);
        }
    }

    private static class MultiTerminalRenderer extends BaseRenderer {
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            return installedGeometry(context, installedTerminals(context));
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return looseGeometry(context, looseTerminals(context, false), false);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            if (!context.isInstalledPartMounted()) return;
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals())
                drawInstalledLead(graphics, context, geometry, terminal.getTerminalIndex());
            drawPackageBody(graphics, context, geometry.getBodyBounds());
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, 0, Math.max(8, context.scale(10))));
            graphics.setColor(WorkbenchVisualTheme.SILK_SECONDARY);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Point point = terminal.getBoardPadPoint();
                if (point != null)
                    graphics.drawString(terminal.getTerminalId(), point.x + context.scale(9),
                        point.y - context.scale(9));
            }
            context.markBodyDrawn();
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            drawLooseLeads(graphics, geometry, context);
            drawPackageBody(graphics, context, geometry.getBodyBounds());
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Rectangle pad = terminal.getPadBounds();
                WorkbenchVisualTheme.ellipse(graphics, pad.x, pad.y, pad.width, pad.height,
                    WorkbenchVisualTheme.METAL_LIGHT);
                graphics.setColor(WorkbenchVisualTheme.TEXT);
                graphics.setFont(new Font(WorkbenchVisualTheme.FONT, 0, Math.max(8, context.scale(10))));
                Point point = terminal.getPoint();
                graphics.drawString(terminal.getTerminalId(), point.x + context.scale(7),
                    point.y - context.scale(7));
            }
            context.markBodyDrawn();
        }

        protected void drawPackageBody(Graphics graphics, PhysicalPartRenderContext context, Rectangle bounds) {
            WorkbenchVisualTheme.body(graphics, bounds, WorkbenchVisualTheme.BODY,
                WorkbenchVisualTheme.BODY_LIGHT, false);
        }
    }

    private static final class RelayRenderer extends MultiTerminalRenderer {
        protected void drawPackageBody(Graphics graphics, PhysicalPartRenderContext context, Rectangle bounds) {
            WorkbenchVisualTheme.body(graphics, bounds, "#b9c5b8", "#dce3d5", false);
            Rectangle label = new Rectangle(bounds.x + bounds.width / 8, bounds.y + bounds.height / 4,
                bounds.width * 3 / 4, bounds.height / 2);
            WorkbenchVisualTheme.marking(graphics, "SPDT", label, Math.max(9, context.scale(12)),
                WorkbenchVisualTheme.TEXT);
            graphics.setColor(WorkbenchVisualTheme.MUTED_TEXT);
            graphics.fillRect(bounds.x + Math.max(2, bounds.width / 10),
                bounds.y + bounds.height - Math.max(3, bounds.height / 7),
                Math.max(2, bounds.width / 7), Math.max(1, bounds.height / 18));
        }
    }

    private static final class FactoryLinkRenderer extends MultiTerminalRenderer {
        protected void drawPackageBody(Graphics graphics, PhysicalPartRenderContext context, Rectangle bounds) {
            // The blue sleeve is insulated: only the separately projected endpoint metal is probeable.
            WorkbenchVisualTheme.body(graphics, bounds, "#3e7491", "#80b6cf", true);
            WorkbenchVisualTheme.marking(graphics, "LINK", bounds, Math.max(8, context.scale(10)), "#eff8ff");
        }
    }

    private static final class FuseRenderer extends MultiTerminalRenderer {
        protected void drawPackageBody(Graphics graphics, PhysicalPartRenderContext context, Rectangle bounds) {
            WorkbenchVisualTheme.body(graphics, bounds, "#dedecd", "#f3f2e4", true);
            boolean horizontal = bounds.width >= bounds.height;
            int cap = Math.max(1, (horizontal ? bounds.width : bounds.height) / 6);
            graphics.setColor(WorkbenchVisualTheme.METAL_LIGHT);
            if (horizontal) {
                graphics.fillRect(bounds.x + 1, bounds.y + 1, cap, Math.max(1, bounds.height - 2));
                graphics.fillRect(bounds.x + bounds.width - cap - 1, bounds.y + 1, cap,
                    Math.max(1, bounds.height - 2));
            } else {
                graphics.fillRect(bounds.x + 1, bounds.y + 1, Math.max(1, bounds.width - 2), cap);
                graphics.fillRect(bounds.x + 1, bounds.y + bounds.height - cap - 1,
                    Math.max(1, bounds.width - 2), cap);
            }
            WorkbenchVisualTheme.marking(graphics, "FUSE", bounds, Math.max(8, context.scale(10)),
                WorkbenchVisualTheme.MUTED_TEXT);
        }
    }

    private static void drawTransistorBody(Graphics graphics, Rectangle b, Point leadRoot) {
        // The flat face follows the already transformed lead-root geometry.
        int dx = leadRoot.x - b.x - b.width / 2;
        int dy = leadRoot.y - b.y - b.height / 2;
        graphics.context.save();
        if (Math.abs(dx) > Math.abs(dy))
            graphics.clipRect(b.x + (dx < 0 ? b.width / 5 : 0), b.y,
                b.width * 4 / 5, b.height);
        else
            graphics.clipRect(b.x, b.y + (dy < 0 ? b.height / 5 : 0),
                b.width, b.height * 4 / 5);
        WorkbenchVisualTheme.ellipse(graphics, b.x, b.y, b.width, b.height, WorkbenchVisualTheme.BODY_EDGE);
        WorkbenchVisualTheme.ellipse(graphics, b.x + 1, b.y + 1,
            Math.max(1, b.width - 2), Math.max(1, b.height - 2), WorkbenchVisualTheme.BODY);
        WorkbenchVisualTheme.ellipse(graphics, b.x + b.width * .18, b.y + b.height * .1,
            b.width * .56, b.height * .2, WorkbenchVisualTheme.BODY_LIGHT);
        graphics.context.restore();
    }

    private static void drawPartLabel(Graphics graphics, PhysicalPartRenderContext context,
            String text, int x, int y) {
        graphics.setFont(new Font(WorkbenchVisualTheme.FONT, Font.BOLD, Math.max(11, context.scale(13))));
        graphics.setColor(WorkbenchVisualTheme.TEXT);
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
