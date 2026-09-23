package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Structural and live-solver proof for the E04 sensor/control leaf. */
final class SensorControlGeneratedBoardValidator implements GeneratedBoardValidator {
    /** Initial construction-only correspondence, before player mutation. */
    static void requireStaticPhysicalOwners(GeneratedBoardInstance instance) {
        E04SensorControlModel model = state(instance).getModel();
        BoardComponent u2 = instance.getBoard().getComponent("U2");
        if (u2 == null || !PhysicalPackages.E04_DECISION_CONTROL_5.isEquivalentTo(
                    u2.getPhysicalPackage()) || u2.getPadIds().size() != 5 ||
                instance.getPhysicalSpecifications().getSpecification("U2") == null ||
                instance.getComponentBindings().getSingleElement("U2") !=
                    model.getDecisionElement())
            throw new IllegalStateException("E04 solver decision lacks a physical U2 owner");
        String[] terminals = { "SENSOR", "REFERENCE", "RAIL", "OUTPUT", "RETURN" };
        String[] nets = { "CONDITIONED_SENSOR", "CONTROL_REFERENCE", "CONTROL_RAIL",
            "CONTROL_OUTPUT", "CONTROL_RETURN" };
        CircuitMeasurementEndpoint[] boardEndpoints = {
            model.getPhysicalSensorBoardEndpoint(), model.getPhysicalReferenceBoardEndpoint(),
            model.getSensorControlBindings().getRegulatorOutputBoardEndpoint(),
            model.getPhysicalOutputBoardEndpoint(), model.getPhysicalReturnBoardEndpoint() };
        for (int i = 0; i < terminals.length; i++) {
            String pad = "U2." + terminals[i];
            requirePadNet(instance, pad, nets[i]);
            requireEndpoint(instance, pad, boardEndpoints[i]);
            requireConnection(instance, "U2", pad, boardEndpoints[i],
                new CircuitPostMeasurementEndpoint(model.getDecisionElement(), i));
        }
        requireSupportResistor(instance, model, "RREF_LOW", "CONTROL_REFERENCE",
            "CONTROL_RETURN", model.getPhysicalReferenceBoardEndpoint(),
            model.getPhysicalReturnBoardEndpoint());
        if (model.hasRegenerativeFeedback())
            requireSupportResistor(instance, model, "RFB_HYST", "CONTROL_OUTPUT",
                "CONDITIONED_SENSOR", model.getPhysicalOutputBoardEndpoint(),
                model.getPhysicalSensorBoardEndpoint());
        else if (instance.getBoard().getComponent("RFB_HYST") != null)
            throw new IllegalStateException("Direct E04 variant has ghost feedback hardware");
    }

    private static void requireSupportResistor(GeneratedBoardInstance instance,
            E04SensorControlModel model, String id, String firstNet, String secondNet,
            CircuitMeasurementEndpoint firstBoard, CircuitMeasurementEndpoint secondBoard) {
        BoardComponent component = instance.getBoard().getComponent(id);
        E04SensorControlModel.PassiveBinding passive = model.getBoardSupportPassive(id);
        if (component == null || passive == null ||
                !PhysicalPackages.AXIAL_RESISTOR.isEquivalentTo(component.getPhysicalPackage()) ||
                component.getPadIds().size() != 2 ||
                instance.getPhysicalSpecifications().getSpecification(id) == null ||
                instance.getComponentBindings().getSingleElement(id) != passive.getElement())
            throw new IllegalStateException("E04 solver passive lacks physical owner: " + id);
        String[] pads = { id + ".1", id + ".2" };
        CircuitMeasurementEndpoint[] boardEndpoints = { firstBoard, secondBoard };
        String[] nets = { firstNet, secondNet };
        for (int i = 0; i < pads.length; i++) {
            requirePadNet(instance, pads[i], nets[i]);
            requireEndpoint(instance, pads[i], boardEndpoints[i]);
            GeneratedComponentConnectionBinding connection =
                instance.getConnectionBindings().get(id, pads[i]);
            if (!GeneratedComponentConnectionBindings.sameEndpoint(
                    connection.getBoardEndpoint(), boardEndpoints[i]) ||
                    !(connection.getConnectionElement() instanceof WireElm) ||
                    !instance.getSimulationElements().contains(connection.getConnectionElement()))
                throw new IllegalStateException("E04 physical support lead mismatch: " + pads[i]);
        }
    }

    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        SensorControlFamilyState state = state(instance);
        requireDecisionControlMapping(instance, state.getModel());
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
            state.getModel().getPhysicalReturnBoardEndpoint());
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

    /** Follow the actually installed U2 while retaining immutable board-side copper. */
    private static void requireDecisionControlMapping(GeneratedBoardInstance instance,
            E04SensorControlModel model) {
        BoardComponent component = instance.getBoard().getComponent("U2");
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalBoardSlot slot = runtime.getSlot("U2");
        PhysicalPart<?> installed = runtime.getInstalledPart("U2");
        E04SensorControlModel.DecisionElement expected;
        if (installed == null) {
            if (slot == null || slot.isOccupied())
                throw new IllegalStateException("E04 U2 slot/part state disagrees");
            expected = model.getDecisionElement();
        } else {
            if (!(installed instanceof E04DecisionControlPart) || slot == null ||
                    !slot.isOccupied() || installed.getBoardSlot() != slot)
                throw new IllegalStateException("E04 U2 has no physical decision owner");
            expected = ((E04DecisionControlPart) installed).getElement();
        }
        if (component == null || slot == null || expected == null ||
                !PhysicalPackages.E04_DECISION_CONTROL_5.isEquivalentTo(
                    component.getPhysicalPackage()) || component.getPadIds().size() != 5 ||
                instance.getPhysicalSpecifications().getSpecification("U2") == null ||
                instance.getComponentBindings().getSingleElement("U2") != expected ||
                model.getActiveDecisionElement() != expected)
            throw new IllegalStateException("E04 live decision mapping disagrees with physical U2");

        String[] terminals = { "SENSOR", "REFERENCE", "RAIL", "OUTPUT", "RETURN" };
        String[] nets = { "CONDITIONED_SENSOR", "CONTROL_REFERENCE", "CONTROL_RAIL",
            "CONTROL_OUTPUT", "CONTROL_RETURN" };
        CircuitPostMeasurementEndpoint[] boardEndpoints = {
            model.getPhysicalSensorBoardEndpoint(), model.getPhysicalReferenceBoardEndpoint(),
            model.getSensorControlBindings().getRegulatorOutputBoardEndpoint(),
            model.getPhysicalOutputBoardEndpoint(), model.getPhysicalReturnBoardEndpoint() };
        for (int index = 0; index < terminals.length; index++) {
            String padId = "U2." + terminals[index];
            requirePadNet(instance, padId, nets[index]);
            requireEndpoint(instance, padId, boardEndpoints[index]);
            requireImmutableBoardEndpoint(instance, padId, boardEndpoints[index]);
            requireConnection(instance, "U2", padId, boardEndpoints[index],
                new CircuitPostMeasurementEndpoint(expected, index));
        }
    }

    /**
     * Preserve the model's semantic passive seam while following a legitimate
     * empty slot or catalog replacement for the component-side element.
     */
    private static void requirePhysicalResistorMapping(GeneratedBoardInstance instance,
            E04SensorControlModel model, String componentId, String firstNet,
            String secondNet, CircuitPostMeasurementEndpoint firstBoard,
            CircuitPostMeasurementEndpoint secondBoard, boolean requiresFaultSeam) {
        E04SensorControlModel.PassiveBinding passive =
            model.getBoardOwnedPassive(componentId);
        BoardComponent component = instance.getBoard().getComponent(componentId);
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        PhysicalPart<?> installed = runtime.getInstalledPart(componentId);
        PhysicalResistorPart installedResistor = null;
        CircuitMeasurementEndpoint firstComponent;
        CircuitMeasurementEndpoint secondComponent;
        ResistorSecondaryOpenPath expectedOpenPath;
        ResistorElm expectedElement;

        if (passive == null || component == null || slot == null ||
                !PhysicalPackages.AXIAL_RESISTOR.isEquivalentTo(
                    component.getPhysicalPackage()) || component.getPadIds().size() != 2 ||
                instance.getPhysicalSpecifications().getSpecification(componentId) == null ||
                (requiresFaultSeam && (passive.getFaultIsolation() == null ||
                    passive.getOpenPath() == null)))
            throw new IllegalStateException("E04 physical resistor seam is malformed: " +
                componentId);

        if (installed == null) {
            if (slot.isOccupied())
                throw new IllegalStateException("E04 resistor slot/part state disagrees: " +
                    componentId);
            expectedElement = passive.getElement();
            firstComponent = passive.getFirstEndpoint();
            secondComponent = passive.getSecondEndpoint();
            expectedOpenPath = passive.getOpenPath();
        } else {
            if (!(installed instanceof PhysicalResistorPart) || !slot.isOccupied() ||
                    installed.getBoardSlot() != slot)
                throw new IllegalStateException("E04 resistor has no physical owner: " +
                    componentId);
            installedResistor = (PhysicalResistorPart) installed;
            expectedElement = installedResistor.getElement();
            firstComponent = installedResistor.getPublicTerminal(0);
            secondComponent = installedResistor.getPublicTerminal(1);
            expectedOpenPath = installedResistor.getSecondaryOpenPath();
            if (installedResistor.isOriginal() &&
                    (expectedElement != passive.getElement() ||
                     !GeneratedComponentConnectionBindings.sameEndpoint(firstComponent,
                        passive.getFirstEndpoint()) ||
                     !GeneratedComponentConnectionBindings.sameEndpoint(secondComponent,
                        passive.getSecondEndpoint())))
                throw new IllegalStateException("E04 original resistor lost its model seam: " +
                    componentId);
        }

        if (requireResistor(instance, componentId) != expectedElement)
            throw new IllegalStateException("E04 resistor binding lost its installed owner: " +
                componentId);
        Vector<CircuitElm> auxiliary = instance.getComponentBindings()
            .getAuxiliaryElements(componentId);
        if ((expectedOpenPath == null && !auxiliary.isEmpty()) ||
                (expectedOpenPath != null && (auxiliary.size() != 1 ||
                    auxiliary.firstElement() != expectedOpenPath.getSimulationElement())))
            throw new IllegalStateException("E04 resistor auxiliary binding is stale: " +
                componentId);

        String firstPad = componentId + ".1";
        String secondPad = componentId + ".2";
        requirePadNet(instance, firstPad, firstNet);
        requirePadNet(instance, secondPad, secondNet);
        requireEndpoint(instance, firstPad, firstBoard);
        requireEndpoint(instance, secondPad, secondBoard);
        requireImmutableBoardEndpoint(instance, firstPad, firstBoard);
        requireImmutableBoardEndpoint(instance, secondPad, secondBoard);
        requireConnection(instance, componentId, firstPad, firstBoard, firstComponent);
        requireConnection(instance, componentId, secondPad, secondBoard, secondComponent);
    }

    private static void requirePhysicalPassiveMapping(GeneratedBoardInstance instance,
            SensorControlFamilyState state,
            E04SensorControlModel.SensorControlBindings endpoints) {
        E04SensorControlModel model = state.getModel();
        String[] componentIds = { "RBIAS", "RREF", "RFB", "RREF_LOW" };
        CircuitPostMeasurementEndpoint[] firstBoard = {
            endpoints.getExternalSensorSourceEndpoint(), endpoints.getRegulatorOutputBoardEndpoint(),
            model.getPhysicalOutputBoardEndpoint(), model.getPhysicalReferenceBoardEndpoint() };
        CircuitPostMeasurementEndpoint[] secondBoard = {
            model.getPhysicalSensorBoardEndpoint(), model.getPhysicalReferenceBoardEndpoint(),
            endpoints.getLoadedOutputEndpoint(), model.getPhysicalReturnBoardEndpoint() };
        String[] firstNets = { "SENSOR_SOURCE", "CONTROL_RAIL", "CONTROL_OUTPUT",
            "CONTROL_REFERENCE" };
        String[] secondNets = { "CONDITIONED_SENSOR", "CONTROL_REFERENCE",
            "CONTROL_OUTPUT_LOAD", "CONTROL_RETURN" };
        for (int index = 0; index < componentIds.length; index++) {
            String componentId = componentIds[index];
            requirePhysicalResistorMapping(instance, model, componentId,
                firstNets[index], secondNets[index], firstBoard[index],
                secondBoard[index], index < 3);
        }
        if (model.hasRegenerativeFeedback())
            requirePhysicalResistorMapping(instance, model, "RFB_HYST",
                "CONTROL_OUTPUT", "CONDITIONED_SENSOR",
                model.getPhysicalOutputBoardEndpoint(),
                model.getPhysicalSensorBoardEndpoint(), false);
        else if (instance.getBoard().getComponent("RFB_HYST") != null)
            throw new IllegalStateException("Direct E04 variant has ghost feedback hardware");
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
            new CircuitPostMeasurementEndpoint(regulator,
                AbstractRailRegulatorElm.INPUT_POST),
            new CircuitPostMeasurementEndpoint(regulator,
                AbstractRailRegulatorElm.OUTPUT_POST),
            new CircuitPostMeasurementEndpoint(regulator,
                AbstractRailRegulatorElm.RETURN_POST),
            new CircuitPostMeasurementEndpoint(regulator,
                AbstractRailRegulatorElm.ENABLE_POST) };
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
            requireEndpoint(instance, padId, boardEndpoints[index]);
            requireImmutableBoardEndpoint(instance, padId, boardEndpoints[index]);
            requireConnection(instance, "U1", padId, boardEndpoints[index], expected[index]);
        }
        if (seen.size() != 4)
            throw new IllegalStateException("E04 U1 regulator post mapping is not one-to-one");
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
                    connection.getComponentEndpoint(), expected) || !containsIdentity(
                    instance.getSimulationElements(), connection.getConnectionElement()))
                throw new IllegalStateException("E04 removed U1 lost its canonical lead identity: " +
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

    private static void requireImmutableBoardEndpoint(GeneratedBoardInstance instance,
            String padId, CircuitMeasurementEndpoint expected) {
        if (!GeneratedComponentConnectionBindings.sameEndpoint(
                instance.getDeveloperBoardEndpointOracle().getEndpoint(padId), expected))
            throw new IllegalStateException("E04 immutable board endpoint changed: " + padId);
    }

    private static void requireConnection(GeneratedBoardInstance instance,
            String componentId, String padId, CircuitMeasurementEndpoint boardExpected,
            CircuitMeasurementEndpoint componentExpected) {
        GeneratedComponentConnectionBinding connection = instance.getConnectionBindings()
            .get(componentId, padId);
        if (connection == null ||
                !GeneratedComponentConnectionBindings.sameEndpoint(connection.getBoardEndpoint(),
                boardExpected) || !GeneratedComponentConnectionBindings.sameEndpoint(
                connection.getComponentEndpoint(), componentExpected) ||
                !(connection.getConnectionElement() instanceof WireElm) ||
                !containsIdentity(instance.getSimulationElements(),
                    connection.getConnectionElement()))
            throw new IllegalStateException("E04 physical component endpoint is not truthful: " +
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
