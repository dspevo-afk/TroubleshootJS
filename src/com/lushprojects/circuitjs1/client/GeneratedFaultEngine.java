package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Package-private v1 boundary for seeded, solver-backed generated faults. */
class GeneratedFaultEngine {
    private GeneratedFaultEngine() {
    }

    static GeneratedFaultCandidate resistorOpen(String id, String familyId, long seed,
            String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.RESISTOR_OPEN,
            componentId, familyId, seed), new SwitchOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate resistorIncorrectValue(String id, String familyId,
            long seed, String componentId, ResistorElm resistor, double healthyValue,
            double effectiveValue) {
        if (healthyValue <= 0 || effectiveValue <= 0 ||
                Double.isNaN(healthyValue) || Double.isInfinite(healthyValue) ||
                Double.isNaN(effectiveValue) || Double.isInfinite(effectiveValue) ||
                Math.abs(effectiveValue - healthyValue) <= Math.max(1e-9,
                    Math.abs(healthyValue) * 1e-9))
            throw new IllegalArgumentException("Invalid resistor fault value");
        return candidate(new GeneratedFault(id, GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
            componentId, familyId, seed, healthyValue, effectiveValue),
            new ResistorIncorrectValueFaultEffect(resistor, healthyValue, effectiveValue));
    }

    static GeneratedFaultCandidate diodeOpen(String id, String familyId, long seed,
            String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.DIODE_OPEN,
            componentId, familyId, seed), new SwitchOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate diodeShort(String id, String familyId, long seed,
            String componentId, SwitchElm bypassSwitch) {
        return diodeShort(id, familyId, seed, componentId, bypassSwitch, true);
    }

    static GeneratedFaultCandidate capacitorOpen(String id, String familyId, long seed,
            String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.CAPACITOR_OPEN,
            componentId, familyId, seed), new SwitchOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate capacitorPositiveLeadOpen(String id, String familyId,
            long seed, String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.CAPACITOR_OPEN,
            componentId, familyId, seed), new CapacitorPositiveLeadOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate capacitorShort(String id, String familyId, long seed,
            String componentId, SwitchElm bypassSwitch) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.CAPACITOR_SHORT,
            componentId, familyId, seed), new SwitchParallelShortFaultEffect(bypassSwitch));
    }

    static GeneratedFaultCandidate capacitorShuntShort(String id, String familyId, long seed,
            String componentId, ResistorElm bypassResistor, SwitchElm positiveLeadSwitch) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.CAPACITOR_SHORT,
            componentId, familyId, seed), new CapacitorShortFaultEffect(bypassResistor,
                positiveLeadSwitch));
    }

    static GeneratedFaultCandidate diodeShort(String id, String familyId, long seed,
            String componentId, SwitchElm bypassSwitch, boolean compatible) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.DIODE_SHORT,
            componentId, familyId, seed), new SwitchParallelShortFaultEffect(bypassSwitch), compatible);
    }

    static GeneratedFaultCandidate transistorCollectorOpen(String id, String familyId,
            long seed, String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.TRANSISTOR_CE_OPEN,
            componentId, familyId, seed), new TransistorCollectorOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate transistorCeShort(String id, String familyId,
            long seed, String componentId, ResistorElm bypassResistor) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.TRANSISTOR_CE_SHORT,
            componentId, familyId, seed), new TransistorCeShortFaultEffect(bypassResistor));
    }

    static GeneratedFaultCandidate baseResistorOpen(String id, String familyId,
            long seed, String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.BASE_RESISTOR_OPEN,
            componentId, familyId, seed), new SwitchOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate loadPathOpen(String id, String familyId,
            long seed, String componentId, SwitchElm switchElement) {
        return candidate(new GeneratedFault(id, GeneratedFaultType.LOAD_PATH_OPEN,
            componentId, familyId, seed), new SwitchOpenFaultEffect(switchElement));
    }

    static GeneratedFaultCandidate connectorOpenPath(String id, String familyId, long seed,
            String componentId, SwitchElm switchElement) {
        return connectorOpenPath(id, familyId, seed, componentId, switchElement, true);
    }

    static GeneratedFaultCandidate connectorOpenPath(String id, String familyId, long seed,
            String componentId, SwitchElm switchElement, boolean compatible) {
        GeneratedFault fault = new GeneratedFault(id, GeneratedFaultType.CONNECTOR_OPEN_PATH,
            componentId, familyId, seed);
        GeneratedFaultBinding binding = new GeneratedFaultBinding(fault,
            new SwitchOpenFaultEffect(switchElement));
        return new GeneratedFaultCandidate(binding, compatible);
    }

    static GeneratedFaultCandidate incompatible(GeneratedFault fault,
            GeneratedFaultEffect effect) {
        return new GeneratedFaultCandidate(new GeneratedFaultBinding(fault, effect), false);
    }

    static GeneratedFaultCandidate select(long seed, Vector<GeneratedFaultCandidate> candidates) {
        Vector<GeneratedFaultCandidate> compatible = new Vector<GeneratedFaultCandidate>();
        for (GeneratedFaultCandidate candidate : candidates)
            if (candidate != null && candidate.isCompatible())
                compatible.add(candidate);
        if (compatible.isEmpty())
            throw new IllegalStateException("No compatible generated fault candidates");
        int index = (int) (seed % compatible.size());
        if (index < 0)
            index += compatible.size();
        return compatible.elementAt(index);
    }

    static GeneratedFaultCandidate select(GeneratedFaultType requiredType,
            Vector<GeneratedFaultCandidate> candidates) {
        for (GeneratedFaultCandidate candidate : candidates)
            if (candidate != null && candidate.isCompatible() &&
                candidate.getFault().getType() == requiredType)
                return candidate;
        throw new IllegalStateException("No compatible generated fault candidate for type: " +
            requiredType);
    }

    static void clearAll(Vector<GeneratedFaultCandidate> candidates) {
        for (GeneratedFaultCandidate candidate : candidates)
            if (candidate != null)
                candidate.getBinding().setApplied(false);
    }

    private static GeneratedFaultCandidate candidate(GeneratedFault fault,
            GeneratedFaultEffect effect) {
        return candidate(fault, effect, true);
    }

    private static GeneratedFaultCandidate candidate(GeneratedFault fault,
            GeneratedFaultEffect effect, boolean compatible) {
        return new GeneratedFaultCandidate(new GeneratedFaultBinding(fault, effect), compatible);
    }
}
