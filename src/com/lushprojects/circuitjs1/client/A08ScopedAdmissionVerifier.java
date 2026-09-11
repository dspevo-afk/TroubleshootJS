package com.lushprojects.circuitjs1.client;

/** Same-storage and undeclared-wrapper negatives, independent of mutation receipts. */
final class A08ScopedAdmissionVerifier {
    static int run(CirSim sim) {
        GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        final PhysicalMutationSlot real = ReplaceableDiodeBoardCapability.require(board).getSlot();
        PhysicalMutationSlot wrapper = new PhysicalMutationSlot() {
            public String getComponentId() { return real.getComponentId(); }
            public PhysicalBoardSlot getPhysicalSlot() { return real.getPhysicalSlot(); }
            public PhysicalPart<?> getInstalledPart() { return real.getInstalledPart(); }
            public boolean isEmpty() { return real.isEmpty(); }
            public boolean acceptsPart(PhysicalPart<?> part) { return real.acceptsPart(part); }
            public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
                return real.getExpectedEndpoint(part, pad);
            }
            public AttachmentState captureAttachmentState() { throw new AssertionError("Undeclared slot was admitted"); }
            public void restoreAttachmentState(AttachmentState state) { throw new AssertionError("Undeclared slot wrote state"); }
            public void installForMutation(PhysicalPart<?> part, PhysicalMutationScope scope) { throw new AssertionError(); }
            public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) { throw new AssertionError(); }
        };
        Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        boolean rejected = false;
        try { PhysicalMutationIntent.prepare(board.getPhysicalBoardRuntime(), board,
            sim.getBoardModificationController(), wrapper, "remove"); }
        catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains("exact declared installed provider");
        }
        if (!rejected || board.getPhysicalBoardRuntime().isMutationInProgress())
            throw new IllegalStateException("A08 undeclared wrapper obtained mutation authority");
        before.assertRestored(sim);

        GeneratedBoardInstance fixture = new DiodeProtectedIndicatorGenerator().generate(0);
        PhysicalBoardRuntime runtime = fixture.getPhysicalBoardRuntime();
        runtime.registerCapability(new DuplicateInventoryDeclaration(ReplaceableDiodeBoardCapability.require(fixture)));
        rejected = false;
        try { runtime.validateSupportedCompositionProviders(); }
        catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains("ambiguous inventory storage");
        }
        if (!rejected) throw new IllegalStateException("A08 duplicate inventory storage was admitted");
        before.assertRestored(sim);
        return 4;
    }

    private static final class DuplicateInventoryDeclaration implements PhysicalBoardRuntimeCapability,
            PhysicalBoardInstallationProvider.Scoped {
        private final ReplaceableDiodeBoardCapability actual;
        private final PhysicalPartInventory<PhysicalDiodePart> distinctView;
        DuplicateInventoryDeclaration(ReplaceableDiodeBoardCapability actual) {
            this.actual = actual;
            distinctView = new PhysicalPartInventory<PhysicalDiodePart>(actual.getInventory().getRuntime(),
                actual.getInventory().getInventoryId(), PhysicalDiodePart.class);
        }
        public String getCapabilityId() { return "A08_DUPLICATE_INVENTORY_NEGATIVE"; }
        public PhysicalMutationSlot getMutationSlot() { return actual.getSlot(); }
        public PhysicalPartInventory<?> getMutationInventory() { return distinctView; }
        public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance board,
                BoardModificationController modifications, double initialTime) {
            throw new AssertionError("Duplicate inventory reached live installation");
        }
    }
}
