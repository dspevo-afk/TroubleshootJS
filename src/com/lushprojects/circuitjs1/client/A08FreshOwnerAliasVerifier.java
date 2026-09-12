package com.lushprojects.circuitjs1.client;

/** Explicit mutable-owner aliases rejected through the real fresh-install entrypoint. */
final class A08FreshOwnerAliasVerifier {
    private int assertions;

    static int run(CirSim sim) { return new A08FreshOwnerAliasVerifier().verify(sim); }

    private int verify(CirSim sim) {
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        GeneratedBoardInstance clean = diode(original.getSeed());
        reject(sim, copy(clean, original.getPcbLayout(), clean.getOperationalStates(), null),
            "mutable owners");
        clean = diode(original.getSeed());
        reject(sim, copy(clean, clean.getPcbLayout(), original.getOperationalStates(), null),
            "mutable owners");
        clean = diode(original.getSeed());
        clean.getOperationalStates().replaceLed("LED1",
            (LEDElm) original.getComponentBindings().getElements("LED1").firstElement());
        reject(sim, clean, "foreign solver element");
        clean = diode(original.getSeed());
        // P01 made outlines and trace coordinate storage defensive. Attempts to copy through
        // exposed getters can no longer construct a shared mutable geometry owner.
        FreshGeneratedRuntimeInstallation.requireDisjoint(original,
            copy(clean, copyLayout(original.getPcbLayout(), true, true), clean.getOperationalStates(), null));
        assertions++;
        clean = diode(original.getSeed());
        // Equal geometry and immutable placement/package reuse are legitimate.
        FreshGeneratedRuntimeInstallation.requireDisjoint(original,
            copy(clean, copyLayout(original.getPcbLayout(), false, false), clean.getOperationalStates(), null));
        assertions++;

        Task41SimulationSnapshot saved = Task41SimulationSnapshot.capture(sim);
        try {
            GeneratedBoardInstance temporalOwner = new RcDelayGenerator().generate(0);
            FreshGeneratedRuntimeInstallation.install(sim, temporalOwner, false);
            clean = new RcDelayGenerator().generate(0);
            reject(sim, copy(clean, clean.getPcbLayout(), clean.getOperationalStates(),
                temporalOwner.getTemporalBehavior()), "mutable owners");
        } finally { saved.restore(sim); saved.assertRestored(sim); }
        callbackOwners(sim);
        familyOwners(sim);
        return assertions;
    }

    private void callbackOwners(CirSim sim) {
        Task41SimulationSnapshot saved = Task41SimulationSnapshot.capture(sim);
        try {
            GeneratedBoardInstance original = controlled();
            FreshGeneratedRuntimeInstallation.install(sim, original, false);
            ControlledIndicatorDeviceBehavior behavior =
                (ControlledIndicatorDeviceBehavior)original.getBehaviorContract();
            String protectedDump = sim.dumpCircuit();
            boolean commanded = behavior.isCommandedOn();
            GeneratedBoardInstance clean = controlled();
            reject(sim, copyExecution(clean, clean.getBehaviorContract(), behavior,
                clean.getChallengeDefinition().getScenarioCatalog()), "foreign behavior owner");
            clean = controlled();
            reject(sim, copyExecution(clean, behavior, behavior,
                original.getChallengeDefinition().getScenarioCatalog()), "behavior callback owner");
            clean = controlled();
            reject(sim, copyExecution(clean, clean.getBehaviorContract(), clean.getBehaviorContract(),
                original.getChallengeDefinition().getScenarioCatalog()), "foreign behavior owner");
            clean = controlled();
            reject(sim, copyExecution(clean, clean.getBehaviorContract(), clean.getBehaviorContract(),
                original.getChallengeDefinition().getScenarioCatalog().reversedForDeveloperVerification()),
                "foreign behavior owner");
            clean = controlled();
            reject(sim, copyExecution(clean, clean.getBehaviorContract(), clean.getBehaviorContract(),
                behavior.createScenarioCatalog()), "foreign behavior owner");
            java.util.Vector<Object> reachable = new java.util.Vector<Object>();
            original.getChallengeDefinition().getScenarioCatalog().appendExecutionOwners(reachable);
            java.util.Vector<GeneratedScenario<GeneratedObservedBehavior>> rewrapped =
                new java.util.Vector<GeneratedScenario<GeneratedObservedBehavior>>();
            for (Object owner : reachable)
                if (owner instanceof GeneratedScenario)
                    rewrapped.add((GeneratedScenario<GeneratedObservedBehavior>)owner);
            clean = controlled();
            reject(sim, copyExecution(clean, clean.getBehaviorContract(), clean.getBehaviorContract(),
                new GeneratedScenarioCatalog<GeneratedObservedBehavior>(rewrapped)), "behavior callback owner");
            clean = controlled();
            ControlledIndicatorDeviceBehavior copiedBehavior = new ControlledIndicatorDeviceBehavior(
                behavior.getPlan(), behavior.getConstructionReceipt());
            reject(sim, copyExecution(clean, copiedBehavior, copiedBehavior, copiedBehavior.createScenarioCatalog()),
                "behavior callback owner");
            require(protectedDump.equals(sim.dumpCircuit()) && commanded == behavior.isCommandedOn(),
                "rejected callback graphs never command or alter the retired board");
            FreshGeneratedRuntimeInstallation.install(sim, controlled(), false);
            require(sim.isGeneratedRuntimeSettled(), "independent controlled behavior and scenario remain installable");
        } finally { saved.restore(sim); saved.assertRestored(sim); }
    }

    private void familyOwners(CirSim sim) {
        Task41SimulationSnapshot saved = Task41SimulationSnapshot.capture(sim);
        try {
            GeneratedBoardInstance original = controlled();
            FreshGeneratedRuntimeInstallation.install(sim, original, false);
            GeneratedBoardInstance clean = controlled();
            reject(sim, copyFamily(clean, ((ControlledIndicatorDeviceBehavior)original.getBehaviorContract())
                .createFamilyState(), clean.getTemporalBehavior()), "family state captures a foreign behavior");
            FreshGeneratedRuntimeInstallation.install(sim, controlled(), false);
            require(sim.isGeneratedRuntimeSettled(), "disjoint controlled family state installs");
            for (int kind = 0; kind < 2; kind++) {
                original = switchBoard(kind);
                FreshGeneratedRuntimeInstallation.install(sim, original, false);
                SwitchElm retiredSwitch = null;
                for (CircuitElm element : original.getSimulationElements())
                    if (element instanceof SwitchElm) { retiredSwitch = (SwitchElm)element; break; }
                require(retiredSwitch != null, "switch fixture exposes a real mutable switch");
                int position = retiredSwitch.position;
                clean = switchBoard(kind);
                GeneratedBoardFamilyState wrapped = kind == 0 ?
                    new NpnLowSideSwitchFamilyState(retiredSwitch) : new NmosLowSideSwitchFamilyState(retiredSwitch);
                reject(sim, copyFamily(clean, wrapped, clean.getTemporalBehavior()),
                    "family state captures a foreign control");
                require(retiredSwitch.position == position, "rejected control callback never toggles retired switch");
                FreshGeneratedRuntimeInstallation.install(sim, switchBoard(kind), false);
                require(sim.isGeneratedRuntimeSettled(), "disjoint switch-family callbacks install");
            }
            original = new RcDelayGenerator().generate(0);
            FreshGeneratedRuntimeInstallation.install(sim, original, false);
            clean = new RcDelayGenerator().generate(0);
            reject(sim, copyFamily(clean, new RcDelayFamilyState((RcDelayTemporalBehavior)
                original.getTemporalBehavior()), clean.getTemporalBehavior()), "family state captures a foreign temporal");
            CircuitElm retiredCapacitor = original.getComponentBindings().getSingleElement("C1");
            for (int endpoint = 0; endpoint < 2; endpoint++) {
                clean = new RcDelayGenerator().generate(0);
                CircuitElm ownCapacitor = clean.getComponentBindings().getSingleElement("C1");
                RcDelayTemporalBehavior temporal = new RcDelayTemporalBehavior(
                    new CircuitPostMeasurementEndpoint(endpoint == 0 ? retiredCapacitor : ownCapacitor, 0),
                    new CircuitPostMeasurementEndpoint(endpoint == 1 ? retiredCapacitor : ownCapacitor, 1), 5);
                reject(sim, copyFamily(clean, new RcDelayFamilyState(temporal), temporal),
                    "temporal state captures a foreign endpoint");
            }
            FreshGeneratedRuntimeInstallation.install(sim, new RcDelayGenerator().generate(0), false);
            require(sim.isGeneratedRuntimeSettled(), "disjoint RC temporal family installs");
        } finally { saved.restore(sim); saved.assertRestored(sim); }
    }

    private static GeneratedBoardInstance switchBoard(int kind) {
        return kind == 0 ? new NpnLowSideSwitchGenerator().generate(0) :
            new NmosLowSideSwitchGenerator().generate(0);
    }

    private static GeneratedBoardInstance copyFamily(GeneratedBoardInstance source,
            GeneratedBoardFamilyState family, GeneratedTemporalBehavior temporal) {
        return new GeneratedBoardInstance(source.getBoard(), source.getSimulationElements(), source.getSeed(),
            source.getCircuitFamilyId(), source.getTopologyVariantId(), source.getDescription(),
            source.getComponentBindings(), source.getExternalPowerBindings(), source.getConnectionBindings(),
            source.getBehaviorContract(), source.getPcbLayout(), source.getPhysicalSpecifications(), source.getFaultBinding(),
            source.getOperationalStates(), source.getChallengeDefinition(), family, source.getPhysicalBoardRuntime(),
            temporal, source.isDeveloperOnlyFaultRoute(), source.getFaultCandidates(), null, source.getDiagnosticProvider());
    }

    private static GeneratedBoardInstance controlled() {
        return BoundedGeneratedBoardAssembler.assemble(BoundedAssemblyRequest.forControlledIndicator(3)).getInstance();
    }

    private static GeneratedBoardInstance copyExecution(GeneratedBoardInstance source,
            GeneratedChallengeBehaviorContract boardBehavior, GeneratedChallengeBehaviorContract definitionBehavior,
            GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios) {
        GeneratedChallengeDefinition old = source.getChallengeDefinition();
        GeneratedChallengeDefinition definition = new GeneratedChallengeDefinition(old.getId(),
            old.getCircuitFamilyId(), old.getTopologyVariantId(), old.getSelectionSeed(), scenarios,
            old.getCompletionText(), old.getFault(), old.getFaultBinding(), definitionBehavior);
        return new GeneratedBoardInstance(source.getBoard(), source.getSimulationElements(), source.getSeed(),
            source.getCircuitFamilyId(), source.getTopologyVariantId(), source.getDescription(),
            source.getComponentBindings(), source.getExternalPowerBindings(), source.getConnectionBindings(),
            boardBehavior, source.getPcbLayout(), source.getPhysicalSpecifications(), source.getFaultBinding(),
            source.getOperationalStates(), definition, source.getFamilyState(), source.getPhysicalBoardRuntime(),
            source.getTemporalBehavior(), source.isDeveloperOnlyFaultRoute(), source.getFaultCandidates(), null,
            boardBehavior instanceof GeneratedDiagnosticProvider ?
                (GeneratedDiagnosticProvider)boardBehavior : source.getDiagnosticProvider());
    }

    private GeneratedBoardInstance diode(long seed) { return new DiodeProtectedIndicatorGenerator().generate(seed); }

    private void reject(CirSim sim, GeneratedBoardInstance candidate, String message) {
        Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        PcbBoardLayout layout = owner.getPcbLayout();
        String geometry = coordinates(layout);
        boolean illuminated = owner.getOperationalStates() != null && owner.getOperationalStates().isIlluminated("LED1");
        boolean rejected = false;
        try { FreshGeneratedRuntimeInstallation.install(sim, candidate, false); }
        catch (IllegalArgumentException failure) { rejected = failure.getMessage().contains(message); }
        require(rejected, "alias rejected by its exact ownership check: " + message);
        before.assertRestored(sim);
        require(owner == sim.getGeneratedBoardInstance() && !FreshGeneratedRuntimeInstallation.isInProgress(sim),
            "alias rejection precedes live installation");
        require(geometry.equals(coordinates(layout)) && (owner.getOperationalStates() == null ||
            illuminated == owner.getOperationalStates().isIlluminated("LED1")),
            "alias rejection leaves original geometry and operational bindings untouched");
    }

    private static GeneratedBoardInstance copy(GeneratedBoardInstance source, PcbBoardLayout layout,
            GeneratedComponentOperationalStates states, GeneratedTemporalBehavior temporal) {
        return new GeneratedBoardInstance(source.getBoard(), source.getSimulationElements(), source.getSeed(),
            source.getCircuitFamilyId(), source.getTopologyVariantId(), source.getDescription(),
            source.getComponentBindings(), source.getExternalPowerBindings(), source.getConnectionBindings(),
            source.getBehaviorContract(), layout, source.getPhysicalSpecifications(), source.getFaultBinding(),
            states, source.getChallengeDefinition(), source.getFamilyState(), source.getPhysicalBoardRuntime(),
            temporal, source.isDeveloperOnlyFaultRoute(), source.getFaultCandidates(), null, source.getDiagnosticProvider());
    }

    private static PcbBoardLayout copyLayout(PcbBoardLayout source, boolean sharedOutline, boolean sharedTrace) {
        PcbBoardLayout result = new PcbBoardLayout(source.getWidth(), source.getHeight(),
            sharedOutline ? source.getBoardOutline() : new Rectangle(source.getBoardOutline()),
            new Rectangle(source.getPartsTray()), source.getLayoutAlgorithmVersion());
        for (PcbPadPlacement pad : source.getPads()) result.addPad(pad);
        for (PcbComponentPlacement part : source.getComponents()) result.addComponent(part);
        for (PcbSilkscreenLabel label : source.getSilkscreenLabels()) result.addSilkscreenLabel(label);
        for (PcbTraceGeometry trace : source.getTraces()) {
            result.addTrace(new PcbTraceGeometry(trace.getNetId(), trace.getStartPadId(), trace.getEndPadId(),
                sharedTrace ? trace.getXPoints() : copyCoordinates(trace.getXPoints()), copyCoordinates(trace.getYPoints())));
        }
        return result;
    }

    private static int[] copyCoordinates(int[] source) {
        int[] copy = new int[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static String coordinates(PcbBoardLayout layout) {
        StringBuilder result = new StringBuilder();
        Rectangle b = layout.getBoardOutline(), t = layout.getPartsTray();
        result.append(b.x).append(':').append(b.y).append(':').append(b.width).append(':').append(b.height)
            .append('/').append(t.x).append(':').append(t.y).append(':').append(t.width).append(':').append(t.height);
        for (PcbTraceGeometry trace : layout.getTraces()) {
            for (int x : trace.getXPoints()) result.append(':').append(x);
            for (int y : trace.getYPoints()) result.append(':').append(y);
        }
        return result.toString();
    }

    private void require(boolean condition, String reason) {
        assertions++;
        if (!condition) throw new IllegalStateException("A08 fresh alias: " + reason);
    }
}
