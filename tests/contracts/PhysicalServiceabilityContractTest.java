package com.lushprojects.circuitjs1.client;

/** Construction/identity oracle. Actual electrical consequences have a separate compiled solver gate. */
public final class PhysicalServiceabilityContractTest {
    private static int assertions;
    public static void main(String[] args) {
        CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        for (String family : PlayerFamilyCatalog.families()) {
            String profile = "COMPOSED_CONTROLLED_INDICATOR".equals(family) ? "MEDIUM" : "EASY";
            GeneratedBoardInstance instance = new PlayerLaunchRequest(family, "0", profile)
                .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
            PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
            check(runtime.getWorkbenchPartsProviders().size() == instance.getBoard().getComponentIds().size(),
                family + " every physical position has a real catalog/service owner");
            for (String id : instance.getBoard().getComponentIds()) {
                PhysicalPart<?> part = runtime.getInstalledPart(id);
                PhysicalBoardInstallationProvider.Scoped capability = runtime.getScopedMutationCapability(id);
                check(capability != null && capability.getMutationSlot().getInstalledPart() == part,
                    family + "/" + id + " exact service slot");
                check(runtime.getInventoryIdForPart(part.getId()) != null, id + " original inventory provenance");
                WorkbenchOperation inspect = WorkbenchOperation.forPart(WorkbenchOperation.INSPECT_LOOSE, part);
                check(WorkbenchCapabilityDiscovery.find(part, inspect, runtime.getWorkbenchCapabilityRegistry()) != null,
                    family + "/" + id + " intrinsic loose inspection is available after removal");
                if (part instanceof PhysicalServicePart) check(part.getRenderMetadata().getLooseProbeProvider() != null,
                    family + "/" + id + " service part declares real loose-terminal projection");
                WorkbenchPartsProvider catalog = runtime.getWorkbenchPartsProvider(id);
                check(catalog != null && !catalog.getCatalogEntries().isEmpty(), family + "/" + id + " replacement available");
                check(instance.getConnectionBindings().getForComponent(id).size() == part.getTerminalCount(), id + " separable terminals");
                for (String padId : instance.getBoard().getComponent(id).getPadIds()) {
                    GeneratedComponentConnectionBinding binding = instance.getConnectionBindings().get(id, padId);
                    check(GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(),
                        capability.getMutationSlot().getExpectedEndpoint(part, instance.getBoard().getPad(padId))), id + " actual terminal");
                    CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint)binding.getBoardEndpoint();
                    CircuitPostMeasurementEndpoint p = (CircuitPostMeasurementEndpoint)binding.getComponentEndpoint();
                    check(!b.getElement().getPost(b.getPostIndex()).equals(p.getElement().getPost(p.getPostIndex())), id + " no direct copper bypass");
                }
            }
            verifyLiftedRenderEnvelopes(sim, instance);
            System.out.println("SERVICE_COVERAGE " + family + " parts=" + runtime.getSlots().size());
        }
        System.out.println("PASS: physical serviceability contracts assertions=" + assertions);
    }
    private static void verifyLiftedRenderEnvelopes(CirSim sim, GeneratedBoardInstance instance) {
        BoardModificationController modifications = new BoardModificationController(sim, instance);
        PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(instance, modifications, instance.getPcbLayout());
        PhysicalPartRenderRegistry registry = StandardPhysicalPartRenderProviders.createRegistry();
        for (String id : instance.getBoard().getComponentIds()) {
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(id);
            java.util.HashMap<String, Boolean> states = modifications.captureConnectionStates(id);
            for (String pad : instance.getBoard().getComponent(id).getPadIds()) {
                states.put(pad, Boolean.FALSE); modifications.restoreConnectionStatesForMutation(states);
                PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer,
                    instance.getPcbLayout().getComponent(id), part, part.getPackage(), -1, false);
                PhysicalPartRenderGeometry geometry = registry.requireRenderer(part.getPackage(), part).getInstalledGeometry(context);
                check(geometry != null && geometry.getTerminals().size() == part.getTerminalCount(),
                    instance.getCircuitFamilyId() + "/" + id + "/" + pad + " lifted renderer envelope is complete");
                states.put(pad, Boolean.TRUE); modifications.restoreConnectionStatesForMutation(states);
            }
        }
    }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
