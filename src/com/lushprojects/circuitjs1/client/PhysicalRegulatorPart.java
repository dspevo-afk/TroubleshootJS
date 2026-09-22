package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Physical identity for the selected four-terminal E02 rail regulator.
 *
 * <p>The board declaration remains a physical specification, while the live
 * electrical identity is the exact {@link AbstractRailRegulatorElm} and its
 * immutable rail contract.  Keeping those two pieces together is what lets a
 * removed original be inspected or reinstalled without introducing a second
 * rail model.</p>
 */
final class PhysicalRegulatorPart extends FixedPhysicalPart<PhysicalSpecification> {
    private final AbstractRailRegulatorElm element;
    private final RailRegulationContract contract;

    PhysicalRegulatorPart(String id, PhysicalSpecification specification,
            PhysicalNameplate playerVisibleNameplate,
            AbstractRailRegulatorElm element, PhysicalPartProvenance provenance) {
        this(id, specification, playerVisibleNameplate, element,
            element == null ? null : element.getContract(), provenance);
    }

    PhysicalRegulatorPart(String id, PhysicalSpecification specification,
            PhysicalNameplate playerVisibleNameplate,
            AbstractRailRegulatorElm element, RailRegulationContract contract,
            PhysicalPartProvenance provenance) {
        super(id, requireSpecification(specification), requireNameplate(playerVisibleNameplate).forPhysicalPartId(id),
            PhysicalPackages.TO220_REGULATOR_4,
            terminals(id, requireElement(element), requireContract(element, contract)),
            backing(element), provenance, PhysicalPartRenderProbeProviders.SERVICE,
            capabilities());
        this.element = element;
        this.contract = contract;
    }

    AbstractRailRegulatorElm getElement() { return element; }
    RailRegulationContract getContract() { return contract; }
    RailRegulationContract getRailContract() { return contract; }

    CircuitMeasurementEndpoint getPublicTerminal(int terminal) {
        return getTerminal(terminal).getEndpoint();
    }

    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        if (padId == null)
            throw new IllegalArgumentException("Missing regulator board pad");
        String prefix = getId() + ".";
        for (int index = 0; index < getTerminalCount(); index++) {
            String terminalId = getTerminal(index).getTerminalName();
            if ((prefix + terminalId).equals(padId) ||
                    padId.endsWith("." + terminalId))
                return getPublicTerminal(index);
        }
        throw new IllegalArgumentException("Unknown regulator board pad: " + padId);
    }

    private static PhysicalSpecification requireSpecification(PhysicalSpecification specification) {
        if (specification == null)
            throw new IllegalArgumentException("Missing regulator physical specification");
        return specification;
    }

    private static PhysicalNameplate requireNameplate(PhysicalNameplate nameplate) {
        if (nameplate == null)
            throw new IllegalArgumentException("Missing regulator physical nameplate");
        return nameplate;
    }

    private static AbstractRailRegulatorElm requireElement(AbstractRailRegulatorElm element) {
        if (element == null || element.getPostCount() != 4)
            throw new IllegalArgumentException("Regulator part requires the four-post rail model");
        return element;
    }

    private static RailRegulationContract requireContract(AbstractRailRegulatorElm element,
            RailRegulationContract contract) {
        if (contract == null || element.getContract() != contract)
            throw new IllegalArgumentException("Regulator part contract is not the live rail contract");
        if (!PhysicalPackages.TO220_REGULATOR_4.getTerminalIds().equals(contract.getTerminalIds()))
            throw new IllegalArgumentException("Regulator contract does not fit the four-terminal package");
        return contract;
    }

    private static Vector<PhysicalPartTerminal> terminals(String id,
            AbstractRailRegulatorElm element, RailRegulationContract contract) {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        Vector<String> terminalIds = PhysicalPackages.TO220_REGULATOR_4.getTerminalIds();
        for (int index = 0; index < terminalIds.size(); index++) {
            int post = postFor(index);
            result.add(new PhysicalPartTerminal(id, terminalIds.get(index),
                new CircuitPostMeasurementEndpoint(element, post)));
        }
        return result;
    }

    private static int postFor(int terminal) {
        if (terminal == 0) return AbstractRailRegulatorElm.INPUT_POST;
        if (terminal == 1) return AbstractRailRegulatorElm.OUTPUT_POST;
        if (terminal == 2) return AbstractRailRegulatorElm.RETURN_POST;
        if (terminal == 3) return AbstractRailRegulatorElm.ENABLE_POST;
        throw new IllegalArgumentException("Invalid regulator terminal: " + terminal);
    }

    private static Vector<CircuitElm> backing(AbstractRailRegulatorElm element) {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(element);
        return result;
    }

    private static Vector<PhysicalPartCapability> capabilities() {
        Vector<PhysicalPartCapability> result = new Vector<PhysicalPartCapability>();
        result.add(new LoosePartInspectableCapability());
        return result;
    }
}
