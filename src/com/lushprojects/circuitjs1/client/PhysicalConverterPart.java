package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** One seven-terminal package retaining all four actual solver backing elements. */
final class PhysicalConverterPart extends FixedPhysicalPart<ConverterSpecification> {
    private final IsolatedConverterModuleModel module;

    PhysicalConverterPart(String id, ConverterSpecification specification,
            IsolatedConverterModuleModel module, PhysicalPartProvenance provenance) {
        super(id, specification, nameplate(id, specification), PhysicalPackages.ISOLATED_CONVERTER_7,
            terminals(id, specification, module), module.elements(), provenance,
            PhysicalPartRenderProbeProviders.SERVICE, capabilities());
        this.module = module;
    }

    IsolatedConverterModuleModel getModule() { return module; }
    CircuitPostMeasurementEndpoint terminal(String id) { return module.terminal(ConverterSpecification.terminalIndex(id)); }
    Vector<CircuitElm> auxiliaryElements() {
        Vector<CircuitElm> result = module.elements(); result.remove(0); return result;
    }

    private static PhysicalNameplate nameplate(String id, ConverterSpecification specification) {
        if (specification == null) throw new IllegalArgumentException("Missing converter specification");
        return new PhysicalNameplate(id, "Isolated converter", "Marking", specification.label());
    }
    private static Vector<PhysicalPartTerminal> terminals(String id,
            ConverterSpecification specification, IsolatedConverterModuleModel module) {
        specification.requireModel(module);
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        for (int i = 0; i < IsolatedConverterModuleModel.TERMINAL_COUNT; i++)
            result.add(new PhysicalPartTerminal(id, ConverterSpecification.terminalId(i), module.terminal(i)));
        return result;
    }
    private static Vector<PhysicalPartCapability> capabilities() {
        Vector<PhysicalPartCapability> result = new Vector<PhysicalPartCapability>();
        result.add(new LoosePartInspectableCapability()); return result;
    }
}
