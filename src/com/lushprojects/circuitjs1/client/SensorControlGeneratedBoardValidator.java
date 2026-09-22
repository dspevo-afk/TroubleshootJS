package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Structural and live-solver proof for the E04 sensor/control leaf. */
final class SensorControlGeneratedBoardValidator implements GeneratedBoardValidator {
    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        SensorControlFamilyState state = state(instance);
        requireResistor(instance, "RBIAS");
        requireResistor(instance, "RREF");
        requireResistor(instance, "RFB");
        requirePadNet(instance, "J1.1", "RAW_INPUT");
        requirePadNet(instance, "J1.2", "CONTROL_RETURN");
        requirePadNet(instance, "J2.1", "SENSOR_SOURCE");
        requirePadNet(instance, "J2.2", "CONTROL_RETURN");
        requirePadNet(instance, "J3.1", "CONTROL_OUTPUT_LOAD");
        requirePadNet(instance, "J3.2", "CONTROL_RETURN");
        requirePadNet(instance, "U1.INPUT", "RAW_INPUT");
        requirePadNet(instance, "U1.OUTPUT", "CONTROL_RAIL");
        requirePadNet(instance, "U1.RETURN", "CONTROL_RETURN");
        requirePadNet(instance, "U1.ENABLE", "CONTROL_ENABLE");
        requirePadNet(instance, "RBIAS.1", "SENSOR_SOURCE");
        requirePadNet(instance, "RBIAS.2", "CONDITIONED_SENSOR");
        requirePadNet(instance, "RREF.1", "CONTROL_RAIL");
        requirePadNet(instance, "RREF.2", "CONTROL_REFERENCE");
        requirePadNet(instance, "RFB.1", "CONTROL_OUTPUT");
        requirePadNet(instance, "RFB.2", "CONTROL_OUTPUT_LOAD");
        E04SensorControlModel.SensorControlBindings endpoints =
            state.getModel().getSensorControlBindings();
        requireEndpoint(instance, "J1.1", endpoints.getRawInputEndpoint());
        requireEndpoint(instance, "J1.2", endpoints.getRawReturnEndpoint());
        requireEndpoint(instance, "J2.1",
            endpoints.getExternalSensorSourceEndpoint());
        requireEndpoint(instance, "J2.2",
            endpoints.getExternalSensorReturnEndpoint());
        requireEndpoint(instance, "J3.1",
            endpoints.getLoadedOutputEndpoint());
        requireEndpoint(instance, "J3.2",
            endpoints.getReturnEndpoint());
        requirePhysicalPassiveMapping(instance, state, endpoints);
        requireRawPowerInput(instance);
        PhysicalRegulatorPart regulatorPart = requirePhysicalRegulatorPart(instance);
        if (regulatorPart == null) {
            requireEmptyRegulatorMapping(instance, state, endpoints);
            if (powerState != BoardPowerState.UNPOWERED)
                throw new IllegalStateException("E04 cannot power a board with U1 removed");
            return;
        }
        requireRegulatorMapping(instance, state, endpoints, regulatorPart);
        if (!state.getModel().hasE02Regulator() ||
                state.getModel().getE02RegulatorVariantId() == null)
            throw new IllegalStateException("E04 board has no selected E02 rail implementation");
        if (powerState == BoardPowerState.UNPOWERED) {
            if (state.getModel().isOutputHigh() ||
                    Math.abs(state.getModel().getOutputCurrent()) > .000001)
                throw new IllegalStateException("Unpowered E04 board still drives output");
            return;
        }
        E04SensorControlModel.SensorCondition prior = state.getCommandedCondition();
        CirSim sim = CircuitElm.sim;
        try {
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);
            if (!isHealthyLow(instance))
                throw new IllegalStateException("Healthy E04 board does not settle LOW");
            state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            if (!isHealthyHigh(instance))
                throw new IllegalStateException("Healthy E04 board does not settle HIGH");
        } finally {
            state.applyCondition(sim, prior);
        }
    }

    static boolean isHealthyLow(GeneratedBoardInstance instance) {
        SensorControlFamilyState state = state(instance);
        E04SensorControlModel model = state.getModel();
        return model.getControlState() == E04SensorControlModel.ControlState.LOW &&
            !model.isOutputHigh() && finite(model.getSensorNodeVoltage()) &&
            finite(model.getReferenceNodeVoltage()) &&
            model.getSensorNodeVoltage() < model.getReferenceNodeVoltage();
    }

    static boolean isHealthyHigh(GeneratedBoardInstance instance) {
        SensorControlFamilyState state = state(instance);
        E04SensorControlModel model = state.getModel();
        return model.getControlState() == E04SensorControlModel.ControlState.HIGH &&
            model.isOutputHigh() && finite(model.getSensorNodeVoltage()) &&
            finite(model.getReferenceNodeVoltage()) &&
            model.getSensorNodeVoltage() > model.getReferenceNodeVoltage() + .05 &&
            model.getOutputVoltage() > .05;
    }

    static double resistorCurrent(GeneratedBoardInstance instance, String componentId) {
        return Math.abs(requireResistor(instance, componentId).getCurrent());
    }

    static double padVoltage(GeneratedBoardInstance instance, String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings()
            .getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing E04 pad voltage endpoint: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getPostVoltage(post.getPostIndex());
    }

    private static void requirePhysicalPassiveMapping(GeneratedBoardInstance instance,
            SensorControlFamilyState state,
            E04SensorControlModel.SensorControlBindings endpoints) {
        String[] componentIds = { "RBIAS", "RREF", "RFB" };
        CircuitPostMeasurementEndpoint[] firstBoard = {
            endpoints.getExternalSensorSourceEndpoint(), endpoints.getRegulatorOutputBoardEndpoint(),
            endpoints.getOutputDriveEndpoint() };
        CircuitPostMeasurementEndpoint[] secondBoard = {
            endpoints.getConditionedSensorEndpoint(), endpoints.getReferenceEndpoint(),
            endpoints.getLoadedOutputEndpoint() };
        for (int index = 0; index < componentIds.length; index++) {
            String componentId = componentIds[index];
            E04SensorControlModel.PassiveBinding passive =
                state.getModel().getBoardOwnedPassive(componentId);
            if (passive == null || requireResistor(instance, componentId) != passive.getElement() ||
                    passive.getFaultIsolation() == null || passive.getOpenPath() == null)
                throw new IllegalStateException("E04 physical resistor seam is not model-owned: " +
                    componentId);
            requireConnection(instance, componentId, componentId + ".1",
                firstBoard[index], passive.getFirstEndpoint());
            requireConnection(instance, componentId, componentId + ".2",
                secondBoard[index], passive.getSecondEndpoint());
        }
    }

    private static void requireRegulatorMapping(GeneratedBoardInstance instance,
            SensorControlFamilyState state,
            E04SensorControlModel.SensorControlBindings endpoints,
            PhysicalRegulatorPart regulatorPart) {
        AbstractRailRegulatorElm selected = state.getModel().getSelectedE02Regulator();
        AbstractRailRegulatorElm regulator = regulatorPart.getElement();
        BoardComponent component = instance.getBoard().getComponent("U1");
        if (selected == null || regulator == null ||
                regulatorPart.getContract() != selected.getContract() || component == null ||
                !PhysicalPackages.TO220_REGULATOR_4.isEquivalentTo(component.getPhysicalPackage()) ||
                regulator.getPostCount() != 4 || component.getPadIds().size() != 4)
            throw new IllegalStateException("E04 U1 regulator package is missing or malformed");
        String[] terminals = { RailRegulationContract.INPUT_TERMINAL,
            RailRegulationContract.OUTPUT_TERMINAL,
            RailRegulationContract.RETURN_TERMINAL,
            RailRegulationContract.ENABLE_TERMINAL };
        int[] posts = { AbstractRailRegulatorElm.INPUT_POST,
            AbstractRailRegulatorElm.OUTPUT_POST,
            AbstractRailRegulatorElm.RETURN_POST,
            AbstractRailRegulatorElm.ENABLE_POST };
        CircuitPostMeasurementEndpoint[] expected = {
            endpoints.getRegulatorInputEndpoint(),
            endpoints.getRegulatorOutputEndpoint(),
            endpoints.getRegulatorReturnEndpoint(),
            endpoints.getRegulatorEnableEndpoint() };
        CircuitPostMeasurementEndpoint[] boardEndpoints = {
            endpoints.getRegulatorInputBoardEndpoint(),
            endpoints.getRegulatorOutputBoardEndpoint(),
            endpoints.getRegulatorReturnBoardEndpoint(),
            endpoints.getRegulatorEnableBoardEndpoint() };
        Vector<Integer> seen = new Vector<Integer>();
        for (int index = 0; index < terminals.length; index++) {
            String padId = "U1." + terminals[index];
            BoardPad pad = instance.getBoard().getPad(padId);
            if (pad == null || !component.getPadIds().contains(padId) ||
                    !terminals[index].equals(pad.getTerminalId()))
                throw new IllegalStateException("E04 U1 pad inventory is not bijective: " + padId);
            if (expected[index] == null || seen.contains(Integer.valueOf(posts[index])) ||
                    expected[index].getElement() != regulator ||
                    expected[index].getPostIndex() != posts[index] ||
                    regulator.getPost(posts[index]) == null)
                throw new IllegalStateException("E04 U1 pad is not bound to its exact regulator post: " +
                    padId);
            seen.add(Integer.valueOf(posts[index]));
            requireConnection(instance, "U1", padId, boardEndpoints[index], expected[index]);
        }
        if (seen.size() != 4)
            throw new IllegalStateException("E04 U1 regulator post mapping is not one-to-one");
        // The model retains the original selected rail identity for its
        // contract and board-side oracle.  A catalog replacement owns the
        // live U1 posts, while its output is still bridged only through the
        // persistent downstream copper anchor.
        if (endpoints.getRegulatorOutputBoardEndpoint() != boardEndpoints[1])
            throw new IllegalStateException("E04 rail copper anchor changed during U1 service");
    }

    private static void requireEmptyRegulatorMapping(GeneratedBoardInstance instance,
            SensorControlFamilyState state,
            E04SensorControlModel.SensorControlBindings endpoints) {
        AbstractRailRegulatorElm selected = state.getModel().getSelectedE02Regulator();
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot("U1");
        if (selected == null || slot == null || slot.isOccupied() ||
                instance.getComponentBindings().getSingleElement("U1") != selected)
            throw new IllegalStateException("E04 U1 removal left a foreign physical owner");
        CircuitPostMeasurementEndpoint[] boardEndpoints = {
            endpoints.getRegulatorInputBoardEndpoint(),
            endpoints.getRegulatorOutputBoardEndpoint(),
            endpoints.getRegulatorReturnBoardEndpoint(),
            endpoints.getRegulatorEnableBoardEndpoint() };
        int[] posts = { AbstractRailRegulatorElm.INPUT_POST,
            AbstractRailRegulatorElm.OUTPUT_POST,
            AbstractRailRegulatorElm.RETURN_POST,
            AbstractRailRegulatorElm.ENABLE_POST };
        for (int index = 0; index < posts.length; index++) {
            String terminal = PhysicalPackages.TO220_REGULATOR_4.getTerminalIds().get(index);
            GeneratedComponentConnectionBinding connection = instance.getConnectionBindings().get("U1",
                "U1." + terminal);
            CircuitMeasurementEndpoint expected = new CircuitPostMeasurementEndpoint(selected, posts[index]);
            if (connection == null || !GeneratedComponentConnectionBindings.sameEndpoint(
                    connection.getBoardEndpoint(), boardEndpoints[index]) ||
                    !GeneratedComponentConnectionBindings.sameEndpoint(
                    connection.getComponentEndpoint(), expected) || containsIdentity(
                    instance.getSimulationElements(), connection.getConnectionElement()))
                throw new IllegalStateException("E04 removed U1 retained a live copper bypass: " +
                    terminal);
        }
    }

    private static void requireRawPowerInput(GeneratedBoardInstance instance) {
        ExternalBoardPowerInput input = instance.getBoard().getPowerInput("CONTROL_RAIL_INPUT");
        if (input == null || !"J1.1".equals(input.getPositivePadId()) ||
                !"J1.2".equals(input.getReturnPadId()) ||
                !"RAW_INPUT".equals(input.getPositiveNetId()) ||
                !"CONTROL_RETURN".equals(input.getReturnNetId()))
            throw new IllegalStateException("E04 external power input is not the raw J1 boundary");
        ExternalPowerSimulationBinding binding = instance.getExternalPowerBindings()
            .getBinding("CONTROL_RAIL_INPUT");
        Vector<CircuitElm> sources = binding.getBackingElements();
        if (sources.size() != 2 || !(sources.get(0) instanceof E02FiniteSourceElm) ||
                !(sources.get(1) instanceof E02FiniteSourceElm))
            throw new IllegalStateException("E04 power binding does not control the two finite E02 sources");
    }

    /** Returns null only for a valid unpowered, empty physical U1 slot. */
    private static PhysicalRegulatorPart requirePhysicalRegulatorPart(
            GeneratedBoardInstance instance) {
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot("U1");
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart("U1");
        if (part == null) {
            if (slot == null || slot.isOccupied())
                throw new IllegalStateException("E04 U1 slot/part state disagrees");
            return null;
        }
        if (!(part instanceof PhysicalRegulatorPart) || slot == null || !slot.isOccupied() ||
                (!PhysicalPartProvenance.GENERATED_ORIGINAL.equals(part.getProvenance().getKind()) &&
                    !PhysicalPartProvenance.CATALOG_ACQUIRED.equals(part.getProvenance().getKind())) ||
                !PhysicalPackages.TO220_REGULATOR_4.isEquivalentTo(part.getPackage()) ||
                instance.getPhysicalSpecifications().getSpecification("U1") == null)
            throw new IllegalStateException("E04 U1 has no fixed physical runtime part/specification");
        return (PhysicalRegulatorPart) part;
    }

    private static SensorControlFamilyState state(GeneratedBoardInstance instance) {
        if (instance == null || !(instance.getFamilyState() instanceof SensorControlFamilyState))
            throw new IllegalStateException("E04 family state is missing");
        return (SensorControlFamilyState) instance.getFamilyState();
    }

    private static ResistorElm requireResistor(GeneratedBoardInstance instance,
            String componentId) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(componentId);
        if (!(element instanceof ResistorElm))
            throw new IllegalStateException("E04 component is not a resistor: " + componentId);
        return (ResistorElm) element;
    }

    private static void requireEndpoint(GeneratedBoardInstance instance, String padId,
            CircuitMeasurementEndpoint expected) {
        if (!GeneratedComponentConnectionBindings.sameEndpoint(
                instance.getSimulationBindings().getEndpoint(padId), expected))
            throw new IllegalStateException("E04 physical endpoint is not model-owned: " + padId);
    }

    private static void requireConnection(GeneratedBoardInstance instance,
            String componentId, String padId, CircuitMeasurementEndpoint boardExpected,
            CircuitMeasurementEndpoint componentExpected) {
        GeneratedComponentConnectionBinding connection = instance.getConnectionBindings()
            .get(componentId, padId);
        if (!GeneratedComponentConnectionBindings.sameEndpoint(connection.getBoardEndpoint(),
                boardExpected) || !GeneratedComponentConnectionBindings.sameEndpoint(
                connection.getComponentEndpoint(), componentExpected))
            throw new IllegalStateException("E04 physical resistor endpoint is not truthful: " +
                padId);
    }

    private static void requirePadNet(GeneratedBoardInstance instance, String padId,
            String netId) {
        BoardPad pad = instance.getBoard().getPad(padId);
        if (pad == null || !netId.equals(pad.getNetId()))
            throw new IllegalStateException("E04 topology mismatch at " + padId);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean containsIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        for (CircuitElm element : elements)
            if (element == target) return true;
        return false;
    }
}
