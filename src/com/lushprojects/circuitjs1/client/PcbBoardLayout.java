package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class PcbBoardLayout {
    private final int width;
    private final int height;
    private final Rectangle boardOutline;
    private final Rectangle partsTray;
    private final HashMap<String, PcbPadPlacement> pads =
        new HashMap<String, PcbPadPlacement>();
    private final HashMap<String, PcbComponentPlacement> components =
        new HashMap<String, PcbComponentPlacement>();
    private final Vector<PcbTraceGeometry> traces = new Vector<PcbTraceGeometry>();

    PcbBoardLayout(int width, int height, Rectangle boardOutline, Rectangle partsTray) {
        this.width = width;
        this.height = height;
        this.boardOutline = boardOutline;
        this.partsTray = partsTray;
    }

    void addPad(PcbPadPlacement pad) {
        if (pads.put(pad.getPadId(), pad) != null)
            throw new IllegalArgumentException("Duplicate PCB pad placement: " + pad.getPadId());
    }

    void addComponent(PcbComponentPlacement component) {
        if (components.put(component.getComponentId(), component) != null)
            throw new IllegalArgumentException("Duplicate PCB component placement: " +
                component.getComponentId());
    }

    void addTrace(PcbTraceGeometry trace) { traces.add(trace); }

    void validateAgainst(TroubleshootBoard board) {
        for (String padId : pads.keySet()) {
            BoardPad pad = board.getPad(padId);
            if (pad == null)
                throw new IllegalStateException("PCB layout references unknown pad: " + padId);
        }
        for (String componentId : components.keySet()) {
            if (board.getComponent(componentId) == null)
                throw new IllegalStateException("PCB layout references unknown component: " + componentId);
        }
        for (PcbTraceGeometry trace : traces) {
            if (board.getNet(trace.getNetId()) == null)
                throw new IllegalStateException("PCB layout references unknown net: " + trace.getNetId());
        }
        for (String padId : board.getPadIds()) {
            if (!pads.containsKey(padId))
                throw new IllegalStateException("PCB layout is missing pad: " + padId);
        }
    }

    int getWidth() { return width; }
    int getHeight() { return height; }
    Rectangle getBoardOutline() { return boardOutline; }
    Rectangle getPartsTray() { return partsTray; }
    PcbPadPlacement getPad(String padId) { return pads.get(padId); }
    PcbComponentPlacement getComponent(String componentId) { return components.get(componentId); }
    Vector<PcbPadPlacement> getPads() { return new Vector<PcbPadPlacement>(pads.values()); }
    Vector<PcbComponentPlacement> getComponents() {
        return new Vector<PcbComponentPlacement>(components.values());
    }
    Vector<PcbTraceGeometry> getTraces() { return new Vector<PcbTraceGeometry>(traces); }
}