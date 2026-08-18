package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Central scenario wording and solver predicates for the first families. */
final class GeneratedScenarioLibrary {
    private GeneratedScenarioLibrary() {
    }

    static GeneratedScenarioCatalog<GeneratedObservedBehavior> ledIndicator() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> candidates =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "LED_INDICATOR_DARK", "INDICATOR_DOES_NOT_LIGHT", "Indicator does not light.",
            GeneratedObservedBehavior.DARK_INDICATOR, new DarkIndicatorCompatibility("LED1")));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(candidates);
    }

    static GeneratedScenarioCatalog<GeneratedObservedBehavior> parallelIndicators() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> candidates =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "PARALLEL_ASYMMETRIC_INDICATORS", "INDICATORS_DO_NOT_MATCH",
            "The two indicators do not behave the same.",
            GeneratedObservedBehavior.ASYMMETRIC_INDICATORS,
            new ParallelAsymmetryCompatibility()));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(candidates);
    }

    static GeneratedScenarioCatalog<GeneratedObservedBehavior> diodeIndicator(
            boolean includeDeveloperShort) {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> candidates =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "DIODE_INDICATOR_DARK", "INDICATOR_DOES_NOT_LIGHT", "Indicator does not light.",
            GeneratedObservedBehavior.DARK_INDICATOR, new DarkIndicatorCompatibility("LED1")));
        if (includeDeveloperShort)
            candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
                "DIODE_INDICATOR_BRIGHT", "INDICATOR_UNUSUALLY_BRIGHT",
                "The indicator is brighter than expected.",
                GeneratedObservedBehavior.DIODE_SHORT_HIGH_CURRENT,
                new DiodeShortHighCurrentCompatibility()));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(candidates);
    }

    static GeneratedScenarioCatalog<GeneratedObservedBehavior> rcDelay() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> candidates =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "RC_DELAY_IMMEDIATE", "STARTS_TOO_SOON",
            "The controller responds immediately after power-up.",
            GeneratedObservedBehavior.RC_DELAY_TOO_FAST,
            new RcTemporalCompatibility()));
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "RC_DELAY_NO_START", "DOES_NOT_REACH_OPERATING_STATE",
            "The controller never responds after power-up.",
            GeneratedObservedBehavior.RC_DELAY_STUCK_LOW,
            new RcTemporalCompatibility()));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(candidates);
    }

    static GeneratedScenarioCatalog<GeneratedObservedBehavior> npnLowSideSwitch() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> candidates =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "NPN_LOAD_NOT_SWITCHING", "CONTROLLED_LOAD_DOES_NOT_SWITCH_ON",
            "The controlled load does not switch on.",
            GeneratedObservedBehavior.NPN_LOAD_NOT_SWITCHING,
            new NpnLoadCompatibility(false), new NpnLoadPresentation()));
        candidates.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "NPN_LOAD_STUCK_ACTIVE", "CONTROLLED_LOAD_STAYS_ACTIVE",
            "The controlled load stays active when control is low.",
            GeneratedObservedBehavior.NPN_LOAD_STUCK_ACTIVE,
            new NpnLoadCompatibility(true), new NpnLoadPresentation()));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(candidates);
    }

    private static class DarkIndicatorCompatibility
            implements GeneratedScenarioCompatibility<GeneratedObservedBehavior> {
        private final String ledId;

        DarkIndicatorCompatibility(String ledId) {
            this.ledId = ledId;
        }

        public boolean matches(GeneratedBoardInstance instance,
                BoardModificationController modifications, BoardPowerState powerState,
                GeneratedObservedBehavior observedBehavior) {
            if (powerState != BoardPowerState.POWERED || instance.getOperationalStates() == null ||
                    instance.getComponentBindings().getSingleElement(ledId) == null)
                return false;
            CircuitElm element = instance.getComponentBindings().getSingleElement(ledId);
            return element instanceof LEDElm &&
                Math.abs(((LEDElm) element).getCurrent()) < .001 &&
                !instance.getOperationalStates().isIlluminated(ledId);
        }
    }

    private static class ParallelAsymmetryCompatibility
            implements GeneratedScenarioCompatibility<GeneratedObservedBehavior> {
        public boolean matches(GeneratedBoardInstance instance,
                BoardModificationController modifications, BoardPowerState powerState,
                GeneratedObservedBehavior observedBehavior) {
            if (powerState != BoardPowerState.POWERED)
                return false;
            ResistorElm r1 = resistor(instance, "R1");
            ResistorElm r2 = resistor(instance, "R2");
            LEDElm led1 = led(instance, "LED1");
            LEDElm led2 = led(instance, "LED2");
            double branch1 = Math.abs(r1.getCurrent());
            double branch2 = Math.abs(r2.getCurrent());
            boolean illuminationDiffers = instance.getOperationalStates().isIlluminated("LED1") !=
                instance.getOperationalStates().isIlluminated("LED2");
            return branch1 >= 0 && branch2 >= 0 &&
                (illuminationDiffers || Math.abs(branch1 - branch2) > .001);
        }

        private ResistorElm resistor(GeneratedBoardInstance instance, String id) {
            CircuitElm element = instance.getComponentBindings().getSingleElement(id);
            if (!(element instanceof ResistorElm))
                throw new IllegalStateException("Parallel scenario binding is not a resistor: " + id);
            return (ResistorElm) element;
        }

        private LEDElm led(GeneratedBoardInstance instance, String id) {
            CircuitElm element = instance.getComponentBindings().getSingleElement(id);
            if (!(element instanceof LEDElm))
                throw new IllegalStateException("Parallel scenario binding is not an LED: " + id);
            return (LEDElm) element;
        }
    }

    private static class DiodeShortHighCurrentCompatibility
            implements GeneratedScenarioCompatibility<GeneratedObservedBehavior> {
        public boolean matches(GeneratedBoardInstance instance,
                BoardModificationController modifications, BoardPowerState powerState,
                GeneratedObservedBehavior observedBehavior) {
            if (powerState != BoardPowerState.POWERED)
                return false;
            CircuitElm diodeElement = instance.getComponentBindings().getSingleElement("D1");
            CircuitElm resistorElement = instance.getComponentBindings().getSingleElement("R1");
            CircuitElm ledElement = instance.getComponentBindings().getSingleElement("LED1");
            if (!(diodeElement instanceof DiodeElm) || !(resistorElement instanceof ResistorElm) ||
                    !(ledElement instanceof LEDElm))
                return false;
            double supply = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
                .getNominalVoltage();
            double resistance = ((ResistorElm) resistorElement).getResistance();
            double expectedHealthyCurrent = (supply - .7 - 2.1) / resistance;
            double ledCurrent = Math.abs(((LEDElm) ledElement).getCurrent());
            double diodeDrop = voltage(instance, "D1.A") - voltage(instance, "D1.K");
            return Math.abs(diodeElement.getCurrent()) < .000001 && diodeDrop <= .01 &&
                ledCurrent > expectedHealthyCurrent * 1.05 &&
                instance.getOperationalStates().isIlluminated("LED1");
        }

        private double voltage(GeneratedBoardInstance instance, String padId) {
            CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
                instance.getSimulationBindings().getEndpoint(padId);
            return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
        }
    }

    private static class RcTemporalCompatibility
            implements GeneratedScenarioCompatibility<GeneratedObservedBehavior> {
        public boolean matches(GeneratedBoardInstance instance,
                BoardModificationController modifications, BoardPowerState powerState,
                GeneratedObservedBehavior observedBehavior) {
            GeneratedTemporalBehavior temporal = instance.getTemporalBehavior();
            return powerState == BoardPowerState.POWERED && temporal != null &&
                temporal.getObservedBehavior() == observedBehavior;
        }
    }

    private static class NpnLoadCompatibility
            implements GeneratedScenarioCompatibility<GeneratedObservedBehavior> {
        private final boolean stuckActive;

        NpnLoadCompatibility(boolean stuckActive) { this.stuckActive = stuckActive; }

        public boolean matches(GeneratedBoardInstance instance,
                BoardModificationController modifications, BoardPowerState powerState,
                GeneratedObservedBehavior observedBehavior) {
            if (powerState != BoardPowerState.POWERED ||
                    !(instance.getFamilyState() instanceof NpnLowSideSwitchFamilyState))
                return false;
            NpnLowSideSwitchFamilyState state =
                (NpnLowSideSwitchFamilyState) instance.getFamilyState();
            boolean priorCommandedOn = state.isCommandedOn();
            CirSim sim = CircuitElm.sim;
            if (sim != null)
                sim.beginObservationalValidation();
            try {
                if (stuckActive) {
                    state.setCommandedOn(sim, false);
                    return NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) > .005;
                }
                state.setCommandedOn(sim, true);
                return NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) < .000001;
            } finally {
                try {
                    state.setCommandedOn(sim, priorCommandedOn);
                } finally {
                    if (sim != null)
                        sim.endObservationalValidation();
                }
            }
        }
    }

    private static class NpnLoadPresentation
            implements GeneratedScenarioPresentation<GeneratedObservedBehavior> {
        public void present(CirSim sim, GeneratedBoardInstance instance,
                GeneratedObservedBehavior observedBehavior) {
            if (!(instance.getFamilyState() instanceof NpnLowSideSwitchFamilyState) ||
                    (observedBehavior != GeneratedObservedBehavior.NPN_LOAD_NOT_SWITCHING &&
                        observedBehavior != GeneratedObservedBehavior.NPN_LOAD_STUCK_ACTIVE))
                throw new IllegalStateException("NPN scenario presentation has no family state");
            boolean commandedOn = observedBehavior == GeneratedObservedBehavior.NPN_LOAD_NOT_SWITCHING;
            ((NpnLowSideSwitchFamilyState) instance.getFamilyState()).setCommandedOn(sim,
                commandedOn);
        }
    }
}
