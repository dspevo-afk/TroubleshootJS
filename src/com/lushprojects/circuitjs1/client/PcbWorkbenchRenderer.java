package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PcbWorkbenchRenderer {
    private static final int PAD_RADIUS = 13;
    private static final int DRILL_RADIUS = 5;
    private static final int HIT_RADIUS_SQ = 18 * 18;

    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbBoardLayout layout;
    private Rectangle canvasArea = new Rectangle(0, 0, 1, 1);
    private double scale = 1;
    private int offsetX;
    private int offsetY;
    private String selectedComponentId;
    private String selectedPartId;
    private int trayPage;
    private static final int PARTS_PER_TRAY_PAGE = 3;

    PcbWorkbenchRenderer(GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout) {
        this.instance = instance;
        this.modifications = modifications;
        this.layout = layout;
    }

    void draw(Graphics graphics, Rectangle area) {
        updateTransform(area);
        graphics.setColor("#e8ece9");
        graphics.fillRect(0, 0, area.width, area.height);
        graphics.setColor("#d3d9d4");
        for (int x = 0; x < area.width; x += 32)
            graphics.drawLine(x, 0, x, area.height);
        for (int y = 0; y < area.height; y += 32)
            graphics.drawLine(0, y, area.width, y);

        drawBoard(graphics);
        drawTray(graphics);
    }

    private void drawBoard(Graphics graphics) {
        Rectangle outline = screenRect(layout.getBoardOutline());
        graphics.setColor("#0d5b3d");
        graphics.fillRect(outline.x, outline.y, outline.width, outline.height);
        graphics.setColor("#b5dfc8");
        graphics.setLineWidth(2);
        graphics.drawRect(outline.x, outline.y, outline.width, outline.height);

        graphics.setColor("#b56c2f");
        graphics.setLineWidth(Math.max(5, scaleInt(9)));
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] sourceX = trace.getXPoints();
            int[] sourceY = trace.getYPoints();
            for (int index = 1; index < sourceX.length; index++)
                graphics.drawLine(screenX(sourceX[index - 1]), screenY(sourceY[index - 1]),
                    screenX(sourceX[index]), screenY(sourceY[index]));
        }
        graphics.setLineWidth(1);

        for (PcbPadPlacement pad : layout.getPads())
            drawPad(graphics, pad);
        for (PcbComponentPlacement component : layout.getComponents())
            drawComponent(graphics, component);

        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(14))));
        graphics.setColor("#d9f1e3");
        graphics.drawString("TSJ LED INDICATOR", screenX(75), screenY(100));
        graphics.setFont(new Font("sans-serif", 0, Math.max(10, scaleInt(12))));
        graphics.drawString(getPowerInputLabel(), screenX(90), screenY(175));
        graphics.drawString("GND", screenX(90), screenY(345));
    }

    private void drawPad(Graphics graphics, PcbPadPlacement pad) {
        Point point = getPadPoint(pad.getPadId());
        int radius = Math.max(8, scaleInt(PAD_RADIUS));
        int drill = Math.max(4, scaleInt(DRILL_RADIUS));
        graphics.setColor("#d79a43");
        graphics.fillOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
        graphics.setColor("#26312e");
        graphics.fillOval(point.x - drill, point.y - drill, drill * 2, drill * 2);
    }

    private void drawComponent(Graphics graphics, PcbComponentPlacement placement) {
        BoardComponent component = instance.getBoard().getComponent(placement.getComponentId());
        if (component == null)
            return;
        if (placement.getComponentId().equals(selectedComponentId)) {
            Rectangle bounds = screenRect(placement);
            graphics.setColor("#f4d35e");
            graphics.setLineWidth(4);
            graphics.drawRect(bounds.x - 8, bounds.y - 8, bounds.width + 16, bounds.height + 16);
            graphics.setLineWidth(1);
        }
        if ("RESISTOR".equals(component.getType()))
            drawResistor(graphics, placement);
        else if ("LED".equals(component.getType()))
            drawLed(graphics, placement);
        else if ("CONNECTOR".equals(component.getType()))
            drawConnector(graphics, placement);
    }

    private void drawResistor(Graphics graphics, PcbComponentPlacement placement) {
        ComponentPhysicalState state = modifications.getComponentState(placement.getComponentId());
        if (state == ComponentPhysicalState.REMOVED)
            return;
        Vector<String> padIds = instance.getBoard().getComponent(placement.getComponentId()).getPadIds();
        Point pad1 = getPadPoint(padIds.get(0));
        Point pad2 = getPadPoint(padIds.get(1));
        boolean lead1Connected = modifications.isLeadConnected(placement.getComponentId(), padIds.get(0));
        boolean lead2Connected = modifications.isLeadConnected(placement.getComponentId(), padIds.get(1));
        int bodyY = pad1.y - (state == ComponentPhysicalState.LEAD_LIFTED ? scaleInt(28) : 0);
        int bodyLeft = pad1.x + scaleInt(45);
        int bodyRight = pad2.x - scaleInt(45);

        graphics.setColor("#c6c2b2");
        graphics.setLineWidth(Math.max(3, scaleInt(4)));
        Point lead1End = lead1Connected ? pad1 : getComponentLeadPoint(placement.getComponentId(), padIds.get(0));
        Point lead2End = lead2Connected ? pad2 : getComponentLeadPoint(placement.getComponentId(), padIds.get(1));
        graphics.drawLine(bodyLeft, bodyY, lead1End.x, lead1End.y);
        graphics.drawLine(bodyRight, bodyY, lead2End.x, lead2End.y);
        graphics.setLineWidth(1);

        int bodyHeight = Math.max(22, scaleInt(34));
        graphics.setColor("#d9c79b");
        graphics.fillRect(bodyLeft, bodyY - bodyHeight / 2, bodyRight - bodyLeft, bodyHeight);
        graphics.setColor("#302a22");
        graphics.drawRect(bodyLeft, bodyY - bodyHeight / 2, bodyRight - bodyLeft, bodyHeight);
        drawResistorBands(graphics, getInstalledResistorPart(placement.getComponentId()), bodyLeft, bodyRight, bodyY,
            bodyHeight);

        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(14))));
        graphics.setColor("#f2f5e9");
        graphics.drawString(placement.getComponentId(), screenX(placement.getX() + 92),
            screenY(placement.getY() - 14));
    }

    private void drawLed(Graphics graphics, PcbComponentPlacement placement) {
        Vector<String> padIds = instance.getBoard().getComponent(placement.getComponentId()).getPadIds();
        Point anode = getPadPoint(padIds.get(0));
        Point cathode = getPadPoint(padIds.get(1));
        int centerX = (anode.x + cathode.x) / 2;
        int centerY = anode.y - scaleInt(33);
        int radius = Math.max(16, scaleInt(25));
        graphics.setColor("#a6b8ad");
        graphics.setLineWidth(3);
        graphics.drawLine(anode.x, anode.y, centerX - scaleInt(10), centerY + scaleInt(15));
        graphics.drawLine(cathode.x, cathode.y, centerX + scaleInt(10), centerY + scaleInt(15));
        graphics.setLineWidth(1);
        graphics.setColor("#b5232d");
        if (instance.getOperationalStates().isIlluminated(placement.getComponentId())) {
            graphics.setColor("#ffdc4f");
            graphics.fillOval(centerX - radius - scaleInt(9), centerY - radius - scaleInt(9),
                radius * 2 + scaleInt(18), radius * 2 + scaleInt(18));
            graphics.setColor("#b5232d");
        }
        graphics.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        graphics.setColor("#f36a6f");
        graphics.fillOval(centerX - radius / 2, centerY - radius / 2, radius / 2, radius / 2);
        graphics.setColor("#f3efe4");
        graphics.fillRect(centerX + radius - scaleInt(6), centerY - radius / 2,
            Math.max(3, scaleInt(5)), radius);
        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(14))));
        graphics.drawString(placement.getComponentId(), screenX(placement.getX() + 10),
            screenY(placement.getY() - 18));
        graphics.drawString("K", cathode.x - scaleInt(4), cathode.y + scaleInt(27));
    }

    private void drawConnector(Graphics graphics, PcbComponentPlacement placement) {
        Rectangle bounds = screenRect(placement);
        graphics.setColor("#2d8f71");
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        graphics.setColor("#b8ead7");
        graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        BoardComponent component = instance.getBoard().getComponent(placement.getComponentId());
        for (String padId : component.getPadIds()) {
            Point pad = getPadPoint(padId);
            int radius = Math.max(13, scaleInt(20));
            graphics.setColor("#b8c8c2");
            graphics.fillOval(pad.x - radius, pad.y - radius, radius * 2, radius * 2);
            graphics.setColor("#4d5b57");
            graphics.setLineWidth(3);
            graphics.drawLine(pad.x - radius / 2, pad.y, pad.x + radius / 2, pad.y);
            graphics.setLineWidth(1);
        }
        graphics.setColor("#f2f5e9");
        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(14))));
        graphics.drawString(placement.getComponentId(), bounds.x + scaleInt(38),
            bounds.y - scaleInt(12));
    }

    private void drawTray(Graphics graphics) {
        Rectangle tray = screenRect(layout.getPartsTray());
        graphics.setColor("#c9ced0");
        graphics.fillRect(tray.x, tray.y, tray.width, tray.height);
        graphics.setColor("#778084");
        graphics.drawRect(tray.x, tray.y, tray.width, tray.height);
        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(13))));
        graphics.setColor("#3d484c");
        graphics.drawString("PARTS TRAY", tray.x + scaleInt(20), tray.y + scaleInt(30));
        Vector<PhysicalResistorPart> visible = getVisibleLooseParts();
        if (visible.isEmpty()) {
            graphics.setFont(new Font("sans-serif", 0, Math.max(11, scaleInt(12))));
            graphics.drawString("No removed parts", tray.x + scaleInt(20), tray.y + scaleInt(70));
        }
        int index = 0;
        for (PhysicalResistorPart part : visible)
            drawTrayResistor(graphics, part, index++);
        if (getTrayPageCount() > 1) {
            graphics.setFont(new Font("sans-serif", 0, Math.max(10, scaleInt(11))));
            graphics.drawString("Page " + (trayPage + 1) + " of " + getTrayPageCount(),
                tray.x + scaleInt(20), tray.y + tray.height - scaleInt(15));
        }
    }

    private void drawTrayResistor(Graphics graphics, PhysicalResistorPart part, int index) {
        Point lead1 = getLoosePartLeadPoint(part.getId(), 0);
        Point lead2 = getLoosePartLeadPoint(part.getId(), 1);
        int bodyLeft = lead1.x + scaleInt(24);
        int bodyRight = lead2.x - scaleInt(24);
        graphics.setColor("#a8adb0");
        graphics.setLineWidth(3);
        graphics.drawLine(lead1.x, lead1.y, bodyLeft, lead1.y);
        graphics.drawLine(bodyRight, lead2.y, lead2.x, lead2.y);
        graphics.setLineWidth(1);
        graphics.setColor("#d9c79b");
        graphics.fillRect(bodyLeft, lead1.y - scaleInt(14), bodyRight - bodyLeft, scaleInt(28));
        graphics.setColor("#302a22");
        graphics.drawRect(bodyLeft, lead1.y - scaleInt(14), bodyRight - bodyLeft, scaleInt(28));
        drawResistorBands(graphics, part, bodyLeft, bodyRight, lead1.y, scaleInt(28));
        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(13))));
        graphics.drawString(part.getId().equals(selectedPartId) ? "SELECTED" : "RESISTOR",
            lead1.x + scaleInt(20), lead1.y - scaleInt(26));
    }

    ProbeTarget findProbeTarget(CirSim sim, int screenX, int screenY) {
        for (PhysicalResistorPart part : getVisibleLooseParts()) {
            for (int terminal = 0; terminal < 2; terminal++) {
                Point point = getLoosePartLeadPoint(part.getId(), terminal);
                if (Graphics.distanceSq(point.x, point.y, screenX, screenY) <= HIT_RADIUS_SQ)
                    return new PhysicalResistorPartProbeTarget(sim, instance, part.getId(), terminal, this);
            }
        }
        for (PcbComponentPlacement component : layout.getComponents()) {
            Vector<GeneratedComponentConnectionBinding> bindings =
                instance.getConnectionBindings().getForComponentOrEmpty(component.getComponentId());
            if (bindings.isEmpty() || modifications.getComponentState(component.getComponentId()) ==
                    ComponentPhysicalState.INSTALLED)
                continue;
            for (GeneratedComponentConnectionBinding binding : bindings) {
                Point point = getComponentLeadPoint(binding.getComponentId(), binding.getPadId());
                if (point != null && Graphics.distanceSq(point.x, point.y, screenX, screenY) <=
                        HIT_RADIUS_SQ) {
                    PhysicalResistorPart part = getInstalledResistorPart(binding.getComponentId());
                    if (part == null)
                        continue;
                    return new ComponentLeadProbeTarget(sim, instance, binding.getComponentId(),
                        binding.getPadId(), this, part.getId(), binding.getComponentEndpoint());
                }
            }
        }
        for (PcbPadPlacement pad : layout.getPads()) {
            Point point = getPadPoint(pad.getPadId());
            if (Graphics.distanceSq(point.x, point.y, screenX, screenY) <= HIT_RADIUS_SQ)
                return new BoardPadProbeTarget(sim, instance, pad.getPadId(), this);
        }
        return null;
    }

    String findComponentId(int screenX, int screenY) {
        for (PcbComponentPlacement component : layout.getComponents()) {
            if ("R1".equals(component.getComponentId()) && instance.getR1Slot().isEmpty())
                continue;
            Vector<GeneratedComponentConnectionBinding> bindings =
                instance.getConnectionBindings().getForComponentOrEmpty(component.getComponentId());
            if (!bindings.isEmpty() && modifications.getComponentState(component.getComponentId()) ==
                    ComponentPhysicalState.REMOVED) {
                Point lead1 = getComponentLeadPoint(component.getComponentId(),
                    instance.getBoard().getComponent(component.getComponentId()).getPadIds().get(0));
                if (lead1 != null) {
                    Rectangle trayPart = new Rectangle(lead1.x - scaleInt(8),
                        lead1.y - scaleInt(35), scaleInt(145), scaleInt(70));
                    if (trayPart.contains(screenX, screenY))
                        return component.getComponentId();
                }
            } else if (screenRect(component).contains(screenX, screenY)) {
                return component.getComponentId();
            }
        }
        return null;
    }

    String findPartId(int screenX, int screenY) {
        for (PhysicalResistorPart part : getVisibleLooseParts()) {
            Point lead1 = getLoosePartLeadPoint(part.getId(), 0);
            Rectangle trayPart = new Rectangle(lead1.x - scaleInt(8), lead1.y - scaleInt(35),
                scaleInt(145), scaleInt(70));
            if (trayPart.contains(screenX, screenY))
                return part.getId();
        }
        return null;
    }

    Point getPadPoint(String padId) {
        PcbPadPlacement pad = layout.getPad(padId);
        return pad == null ? null : new Point(screenX(pad.getX()), screenY(pad.getY()));
    }

    Point getComponentLeadPoint(String componentId, String padId) {
        PcbPadPlacement pad = layout.getPad(padId);
        if (pad == null)
            return null;
        ComponentPhysicalState state = modifications.getComponentState(componentId);
        Vector<String> padIds = instance.getBoard().getComponent(componentId).getPadIds();
        int terminalIndex = padIds.indexOf(padId);
        if (state == ComponentPhysicalState.REMOVED) {
            Rectangle tray = layout.getPartsTray();
            return new Point(screenX(tray.x + (terminalIndex == 0 ? 18 : tray.width - 18)),
                screenY(tray.y + 125));
        }
        int direction = terminalIndex == 0 ? 1 : -1;
        if (modifications.isLeadConnected(componentId, padId))
            return new Point(screenX(pad.getX() + direction * 25), screenY(pad.getY() - 20));
        return new Point(screenX(pad.getX() + direction * 20), screenY(pad.getY() - 28));
    }

    boolean hasPad(String padId) { return layout.getPad(padId) != null; }
    Point getLoosePartLeadPoint(String partId, int terminal) {
        int index = 0;
        for (PhysicalResistorPart part : getVisibleLooseParts()) {
            if (part.getId().equals(partId)) {
                Rectangle tray = layout.getPartsTray();
                int y = tray.y + 70 + index * 48;
                return new Point(screenX(tray.x + (terminal == 0 ? 18 : tray.width - 18)), screenY(y));
            }
            index++;
        }
        return null;
    }

    int getTrayPage() { return trayPage; }
    int getTrayPageCount() {
        int count = instance.getResistorInventory().getLooseParts().size();
        return Math.max(1, (count + PARTS_PER_TRAY_PAGE - 1) / PARTS_PER_TRAY_PAGE);
    }
    void setTrayPage(int page) {
        trayPage = clampTrayPageValue(page);
        if (selectedPartId != null && getLoosePartLeadPoint(selectedPartId, 0) == null)
            selectedPartId = null;
    }
    void clampTrayPage() { trayPage = clampTrayPageValue(trayPage); }

    private Vector<PhysicalResistorPart> getVisibleLooseParts() {
        Vector<PhysicalResistorPart> loose = instance.getResistorInventory().getLooseParts();
        clampTrayPage();
        Vector<PhysicalResistorPart> result = new Vector<PhysicalResistorPart>();
        int start = trayPage * PARTS_PER_TRAY_PAGE;
        for (int index = start; index < loose.size() && index < start + PARTS_PER_TRAY_PAGE; index++)
            result.add(loose.get(index));
        return result;
    }

    private int clampTrayPageValue(int page) {
        return Math.max(0, Math.min(page, getTrayPageCount() - 1));
    }

    ResistorColorBand[] getResistorBands(String componentId) {
        PhysicalResistorPart installed = getInstalledResistorPart(componentId);
        if (installed != null)
            return ResistorColorCode.getFourBandCode(installed.getNameplate());
        ResistorNameplate nameplate = instance.getPhysicalSpecifications()
            .getResistorNameplate(componentId);
        if (nameplate == null)
            throw new IllegalStateException("Missing resistor nameplate: " + componentId);
        return ResistorColorCode.getFourBandCode(nameplate);
    }
    String getPowerInputLabelForDeveloperVerification() { return getPowerInputLabel(); }
    void setSelectedComponentId(String componentId) { selectedComponentId = componentId; }
    String getSelectedComponentId() { return selectedComponentId; }
    void setSelectedPartId(String partId) { selectedPartId = partId; }
    String getSelectedPartId() { return selectedPartId; }

    private PhysicalResistorPart getInstalledResistorPart(String componentId) {
        if ("R1".equals(componentId))
            return instance.getR1Slot().getInstalledPart();
        return null;
    }

    private void drawResistorBands(Graphics graphics, PhysicalResistorPart part, int left, int right,
            int y, int height) {
        if (part == null)
            return;
        ResistorColorBand[] bands = ResistorColorCode.getFourBandCode(part.getNameplate());
        for (int index = 0; index < bands.length; index++) {
            int x = left + (right - left) * (index + 1) / 5;
            graphics.setColor(getBandColor(bands[index]));
            graphics.fillRect(x - Math.max(2, scaleInt(3)), y - height / 2,
                Math.max(4, scaleInt(6)), height);
        }
    }

    private void updateTransform(Rectangle area) {
        canvasArea = area;
        scale = Math.min((area.width - 30) / (double) layout.getWidth(),
            (area.height - 30) / (double) layout.getHeight());
        if (scale > 1.35)
            scale = 1.35;
        offsetX = (area.width - scaleInt(layout.getWidth())) / 2;
        offsetY = (area.height - scaleInt(layout.getHeight())) / 2;
    }

    private int screenX(int x) { return offsetX + scaleInt(x); }
    private int screenY(int y) { return offsetY + scaleInt(y); }
    private int scaleInt(int value) { return (int) Math.round(value * scale); }
    private Rectangle screenRect(Rectangle rectangle) {
        return new Rectangle(screenX(rectangle.x), screenY(rectangle.y),
            scaleInt(rectangle.width), scaleInt(rectangle.height));
    }
    private Rectangle screenRect(PcbComponentPlacement component) {
        return new Rectangle(screenX(component.getX()), screenY(component.getY()),
            scaleInt(component.getWidth()), scaleInt(component.getHeight()));
    }

    private String getPowerInputLabel() {
        Vector<String> powerInputIds = instance.getBoard().getPowerInputIds();
        if (powerInputIds.size() != 1)
            return "VIN";
        PowerInputNameplate nameplate = instance.getPhysicalSpecifications()
            .getPowerInputNameplate(powerInputIds.get(0));
        return nameplate == null ? "VIN" : nameplate.getDisplayLabel();
    }

    private String getBandColor(ResistorColorBand band) {
        if (band == ResistorColorBand.BLACK)
            return "#222222";
        if (band == ResistorColorBand.BROWN)
            return "#7d4a2d";
        if (band == ResistorColorBand.RED)
            return "#b5232d";
        if (band == ResistorColorBand.ORANGE)
            return "#cc6c2b";
        if (band == ResistorColorBand.YELLOW)
            return "#e0ba36";
        if (band == ResistorColorBand.GREEN)
            return "#278456";
        if (band == ResistorColorBand.BLUE)
            return "#355caa";
        if (band == ResistorColorBand.VIOLET)
            return "#7754a1";
        if (band == ResistorColorBand.GRAY)
            return "#73777b";
        if (band == ResistorColorBand.WHITE)
            return "#e8e8e4";
        if (band == ResistorColorBand.GOLD)
            return "#c7a33b";
        throw new IllegalArgumentException("Unsupported resistor band: " + band);
    }
}
