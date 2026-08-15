package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class TroubleshootBoard {
    private final String id;
    private final HashMap<String, BoardComponent> components =
        new HashMap<String, BoardComponent>();
    private final HashMap<String, BoardPad> pads = new HashMap<String, BoardPad>();
    private final HashMap<String, BoardNet> nets = new HashMap<String, BoardNet>();
    private final HashMap<String, ExternalBoardPowerInput> powerInputs =
        new HashMap<String, ExternalBoardPowerInput>();
    private final BoardSimulationBindings simulationBindings = new BoardSimulationBindings(this);

    TroubleshootBoard(String id) {
        requireId(id, "board");
        this.id = id;
    }

    String getId() {
        return id;
    }

    void addComponent(BoardComponent component) {
        requireId(component.getId(), "component");
        if (components.containsKey(component.getId()))
            throw new IllegalArgumentException("Duplicate board component: " + component.getId());
        components.put(component.getId(), component);
    }

    void addNet(BoardNet net) {
        requireId(net.getId(), "net");
        if (nets.containsKey(net.getId()))
            throw new IllegalArgumentException("Duplicate board net: " + net.getId());
        nets.put(net.getId(), net);
    }

    void addPad(BoardPad pad) {
        requireId(pad.getId(), "pad");
        if (pads.containsKey(pad.getId()))
            throw new IllegalArgumentException("Duplicate board pad: " + pad.getId());
        BoardComponent component = components.get(pad.getComponentId());
        if (component == null)
            throw new IllegalArgumentException("Unknown board component: " + pad.getComponentId());
        BoardNet net = nets.get(pad.getNetId());
        if (net == null)
            throw new IllegalArgumentException("Unknown board net: " + pad.getNetId());
        pads.put(pad.getId(), pad);
        component.addPadId(pad.getId());
        net.addPadId(pad.getId());
    }

    void addPowerInput(ExternalBoardPowerInput powerInput) {
        requireId(powerInput.getId(), "power input");
        if (powerInputs.containsKey(powerInput.getId()))
            throw new IllegalArgumentException("Duplicate board power input: " + powerInput.getId());
        validatePowerInput(powerInput);
        powerInputs.put(powerInput.getId(), powerInput);
    }

    BoardComponent getComponent(String componentId) {
        return components.get(componentId);
    }

    BoardPad getPad(String padId) {
        return pads.get(padId);
    }

    Vector<String> getPadIds() {
        Vector<String> padIds = new Vector<String>();
        for (BoardPad pad : pads.values())
            padIds.add(pad.getId());
        return padIds;
    }

    BoardNet getNet(String netId) {
        return nets.get(netId);
    }

    Vector<String> getNetIds() {
        Vector<String> netIds = new Vector<String>();
        for (BoardNet net : nets.values())
            netIds.add(net.getId());
        return netIds;
    }

    ExternalBoardPowerInput getPowerInput(String powerInputId) {
        return powerInputs.get(powerInputId);
    }

    Vector<String> getPowerInputIds() {
	Vector<String> powerInputIds = new Vector<String>();
	for (ExternalBoardPowerInput powerInput : powerInputs.values())
	    powerInputIds.add(powerInput.getId());
	return powerInputIds;
    }

    BoardSimulationBindings getSimulationBindings() {
        return simulationBindings;
    }

    void validate() {
        for (BoardPad pad : pads.values()) {
            if (!components.containsKey(pad.getComponentId()))
                throw new IllegalArgumentException("Unknown board component: " + pad.getComponentId());
            if (!nets.containsKey(pad.getNetId()))
                throw new IllegalArgumentException("Unknown board net: " + pad.getNetId());
        }
        for (ExternalBoardPowerInput powerInput : powerInputs.values())
            validatePowerInput(powerInput);
    }

    private void validatePowerInput(ExternalBoardPowerInput powerInput) {
        BoardPad positivePad = pads.get(powerInput.getPositivePadId());
        BoardPad returnPad = pads.get(powerInput.getReturnPadId());
        if (positivePad == null || returnPad == null)
            throw new IllegalArgumentException("Power input references an unknown board pad");
        if (!nets.containsKey(powerInput.getPositiveNetId()) ||
                !nets.containsKey(powerInput.getReturnNetId()))
            throw new IllegalArgumentException("Power input references an unknown board net");
        if (!positivePad.getNetId().equals(powerInput.getPositiveNetId()) ||
                !returnPad.getNetId().equals(powerInput.getReturnNetId()))
            throw new IllegalArgumentException("Power input pad and net definitions do not match");
    }

    private static void requireId(String id, String kind) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Missing board " + kind + " ID");
    }
}
