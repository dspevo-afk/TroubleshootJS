package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * The one device-owned assembly boundary for the Task 47 canary.
 *
 * <p>Resolution is delegated to {@link BoundedAssemblyPlan}; this class then
 * allocates one complete board graph and one physical runtime in a private
 * context.  It intentionally does not call a leaf family generator.  The
 * returned instance can be published by the already accepted fresh-owner
 * installation boundary.</p>
 */
final class BoundedGeneratedBoardAssembler {
    static final String FAMILY_ID = ComposedResistiveDeviceBehavior.FAMILY_ID;
    static final String TOPOLOGY_VARIANT_ID =
        ComposedResistiveDeviceBehavior.TOPOLOGY_VARIANT_ID;

    static final String POWER_INPUT_ID = "VIN_INPUT";
    // The bounded generator owns these existing model choices. A03 records
    // their exact values rather than relying on a later model default.
    static final String CONTROLLED_LED_MODEL = "default-led";
    static final double CONTROLLED_NMOS_THRESHOLD_VOLTS = 1.5;
    static final double CONTROLLED_NMOS_BETA = 10.0;
    private static final SeededPcbLayoutGenerator PCB_LAYOUT_GENERATOR =
        new SeededPcbLayoutGenerator(SeededPcbLayoutGenerator.LEGACY_VERSION);

    private BoundedGeneratedBoardAssembler() { }

    /**
     * A03's package/input description reuses the actual layout and nameplate
     * construction path. No CircuitElm or physical runtime is allocated here.
     */
    static PlanPhysicalChoices describePhysicalChoices(BoundedAssemblyPlan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing assembly plan");
        Context context = new Context(plan, null);
        context.buildBoardAndSpecifications();
        PcbBoardLayout layout = context.createPlannedLayout();
        layout.validateGeometry(context.board);
        return new PlanPhysicalChoices(layout, context.specifications, context.board);
    }

    /** Immutable copied choices; board coordinates and runtime owners are absent. */
    static final class PlanPhysicalChoices {
        private final Map<String, PhysicalGeometryRealization> packages;
        private final Map<String, Double> inputVoltages;
        private final int layoutVersion;

        PlanPhysicalChoices(PcbBoardLayout layout,
                BoardPhysicalSpecifications specifications, TroubleshootBoard board) {
            TreeMap<String, PhysicalGeometryRealization> packageMap =
                new TreeMap<String, PhysicalGeometryRealization>();
            for (PcbComponentPlacement placement : layout.getComponents())
                if (packageMap.put(placement.getComponentId(),
                        PhysicalGeometryRealization.fromPlacement(placement)) != null)
                    throw new IllegalArgumentException("Duplicate package realization");
            TreeMap<String, Double> inputs = new TreeMap<String, Double>();
            for (String inputId : board.getPowerInputIds()) {
                PowerInputNameplate plate = specifications.getPowerInputNameplate(inputId);
                if (plate == null) throw new IllegalArgumentException("Missing input nameplate");
                inputs.put(inputId, plate.getNominalVoltage());
            }
            this.packages = Collections.unmodifiableMap(packageMap);
            this.inputVoltages = Collections.unmodifiableMap(inputs);
            this.layoutVersion = layout.getLayoutAlgorithmVersion();
        }

        Map<String, PhysicalGeometryRealization> getPackages() { return packages; }
        Map<String, Double> getInputVoltages() { return inputVoltages; }
        int getLayoutVersion() { return layoutVersion; }
    }

    /** Meaningful private-construction boundaries exposed only to developer proofs. */
    enum Stage {
        MAPPING,
        MERGE,
        SECOND_BLOCK,
        REGISTRATION,
        VALIDATION
    }

    /** Optional fail-closed probe used by the bounded construction verifier. */
    interface FailureProbe {
        void after(Stage stage);
    }

    /**
     * Assemble a candidate without touching the current simulator owner.
     */
    static Result assemble(BoundedAssemblyRequest request) {
        return assemble(request, null);
    }

    /**
     * Assemble a normal diagnostic candidate with an explicit physical fault
     * owner.  Task 41 uses this route to evaluate each admitted owner while
     * retaining the same composition root and graph/runtime boundary.
     */
    static Result assembleForDiagnosticProof(BoundedAssemblyRequest request,
            String qualifiedTargetComponentId) {
        return assembleResolved(request, qualifiedTargetComponentId, null);
    }

    /** Convenience entry for focused developer tests. */
    static Result assemble(long seed) {
        return assemble(BoundedAssemblyRequest.forCanary(seed));
    }

    /**
     * Assemble a request and invoke the supplied probe after every complete
     * private stage.  Pure request failures propagate before a mutable context
     * exists; failures after allocation are reported with a cleanup receipt.
     */
    static Result assemble(BoundedAssemblyRequest request, FailureProbe probe) {
        return assembleResolved(request, null, probe);
    }

    private static Result assembleResolved(BoundedAssemblyRequest request,
            String qualifiedTargetComponentId, FailureProbe probe) {
        // No mutable owner is allocated before this pure resolution step.
        BoundedAssemblyPlan plan = qualifiedTargetComponentId == null ?
            BoundedAssemblyPlan.resolve(request) :
            BoundedAssemblyPlan.resolveForDiagnosticFault(request,
                qualifiedTargetComponentId);
        return assemblePlan(plan, probe, null);
    }

    /** A03 replay validates all recorded choices before allocating elements. */
    static Result replay(RealizationManifest manifest) {
        return assemblePlan(A03RealizationReplay.resolve(manifest), null, manifest);
    }

    private static Result assemblePlan(BoundedAssemblyPlan plan, FailureProbe probe,
            RealizationManifest expectedManifest) {
        Context context = new Context(plan, probe);
        try {
            context.begin(Stage.MAPPING);
            context.buildBoardAndSpecifications();
            context.prepareLogicalMapping();
            context.after(Stage.MAPPING);

            context.begin(Stage.MERGE);
            context.buildPrimaryCircuit();
            context.after(Stage.MERGE);

            context.begin(Stage.SECOND_BLOCK);
            context.buildSecondaryCircuit();
            context.bindMappingsAndLayout();
            context.after(Stage.SECOND_BLOCK);

            context.begin(Stage.REGISTRATION);
            context.buildPhysicalRuntime();
            context.after(Stage.REGISTRATION);

            context.begin(Stage.VALIDATION);
            context.buildInstance();
            context.after(Stage.VALIDATION);
            return context.result(expectedManifest);
        } catch (Throwable failure) {
            boolean cleanupSucceeded = context.dispose(failure);
            throw new AssemblyFailure(context.getCurrentStage(), context.allocatedElementCount(),
                context.registeredPartCount(), context.mappedIdentityCount,
                context.mergeCount(), cleanupSucceeded, failure);
        }
    }

    /** Publish a prepared candidate through the accepted fresh-owner seam. */
    static Result install(CirSim sim, BoundedAssemblyRequest request,
            boolean attachWorkbench) {
        Result result = assemble(request);
        FreshGeneratedRuntimeInstallation.installComposition(sim, result.getInstance(),
            attachWorkbench);
        return result;
    }

    /** Stable qualified component identity used by device behavior and proofs. */
    static String qualifiedComponent(GeneratedBoardInstance instance, String block) {
        if (instance == null)
            throw new IllegalArgumentException("Missing composed board instance");
        return BoundedGeneratedBoardAssembler.componentId(instance.getCircuitFamilyId(),
            instance.getSeed(), block);
    }

    /** Stable qualified pad identity used by device behavior and proofs. */
    static String qualifiedPad(GeneratedBoardInstance instance, String block,
            int terminal) {
        if (instance == null)
            throw new IllegalArgumentException("Missing composed board instance");
        return BoundedGeneratedBoardAssembler.padId(instance.getCircuitFamilyId(),
            instance.getSeed(), block, terminal);
    }

    private static String componentId(String ignoredFamily, long ignoredSeed,
            String block) {
        if (ControlledIndicatorDeviceBehavior.FAMILY_ID.equals(ignoredFamily)) {
            String local = ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(block) ?
                "RG" : ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(block) ?
                "RLOAD" : block;
            return ControlledIndicatorBlockContributions.componentId(
                ControlledIndicatorBlockContributions.namespace(), block, local);
        }
        return "tsj-block-v1/resistive-coupling@1/" + block +
            "/component/R1";
    }

    private static String padId(String ignoredFamily, long ignoredSeed,
            String block, int terminal) {
        if (terminal < 1 || terminal > 2)
            throw new IllegalArgumentException("Invalid composed resistor terminal");
        if (ControlledIndicatorDeviceBehavior.FAMILY_ID.equals(ignoredFamily)) {
            String local = ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(block) ?
                "RG" : ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(block) ?
                "RLOAD" : block;
            return ControlledIndicatorBlockContributions.padId(
                ControlledIndicatorBlockContributions.namespace(), block, local + "." + terminal);
        }
        return "tsj-block-v1/resistive-coupling@1/" + block +
            "/pad/R1." + terminal;
    }

    /** Failure receipt preserving the original exception and private cleanup outcome. */
    static final class AssemblyFailure extends IllegalStateException {
        private final Stage stage;
        private final int allocatedElementCount;
        private final int registeredPartCount;
        private final int mappedIdentityCount;
        private final int mergeCount;
        private final boolean cleanupSucceeded;

        AssemblyFailure(Stage stage, int allocatedElementCount, int registeredPartCount,
                int mappedIdentityCount, int mergeCount, boolean cleanupSucceeded,
                Throwable cause) {
            super("Bounded assembly failed" +
                (stage == null ? "" : " after " + stage) + ": " +
                (cause == null ? "unknown failure" : cause.getMessage()), cause);
            this.stage = stage;
            this.allocatedElementCount = allocatedElementCount;
            this.registeredPartCount = registeredPartCount;
            this.mappedIdentityCount = mappedIdentityCount;
            this.mergeCount = mergeCount;
            this.cleanupSucceeded = cleanupSucceeded;
        }

        Stage getStage() { return stage; }
        Stage getFailureStage() { return stage; }
        int getAllocatedElementCount() { return allocatedElementCount; }
        int getRegisteredPartCount() { return registeredPartCount; }
        int getMappedIdentityCount() { return mappedIdentityCount; }
        int getMergeCount() { return mergeCount; }
        boolean isCleanupSucceeded() { return cleanupSucceeded; }
        boolean getCleanupSucceeded() { return cleanupSucceeded; }
        Throwable getOriginalFailure() { return getCause(); }
    }

    /** Successful immutable assembly result and its developer-readable mapping receipt. */
    static final class Result {
        private final GeneratedBoardInstance instance;
        private final BoundedAssemblyPlan plan;
        private final Map<String, RuntimeTarget> runtimeTargets;
        private final Map<String, EndpointManifest> endpointManifest;
        private final RealizationManifest realizationManifest;

        Result(GeneratedBoardInstance instance, BoundedAssemblyPlan plan,
                Map<String, RuntimeTarget> runtimeTargets,
                Map<String, EndpointManifest> endpointManifest) {
            this(instance, plan, runtimeTargets, endpointManifest, null);
        }

        Result(GeneratedBoardInstance instance, BoundedAssemblyPlan plan,
                Map<String, RuntimeTarget> runtimeTargets,
                Map<String, EndpointManifest> endpointManifest,
                RealizationManifest expectedManifest) {
            if (instance == null || plan == null || runtimeTargets == null ||
                    endpointManifest == null)
                throw new IllegalArgumentException("Incomplete bounded assembly result");
            this.instance = instance;
            this.plan = plan;
            this.runtimeTargets = Collections.unmodifiableMap(
                new TreeMap<String, RuntimeTarget>(runtimeTargets));
            this.endpointManifest = Collections.unmodifiableMap(
                new TreeMap<String, EndpointManifest>(endpointManifest));
            RealizationManifest observed = A03RealizationReplay.capture(plan,
                new PlanPhysicalChoices(instance.getPcbLayout(),
                    instance.getPhysicalSpecifications(), instance.getBoard()));
            if (expectedManifest != null && !expectedManifest.identityCanonical().equals(
                    observed.identityCanonical()))
                throw new IllegalStateException("Realization changed during bounded assembly");
            this.realizationManifest = expectedManifest == null ? observed : expectedManifest;
        }

        RealizationManifest getRealizationManifest() { return realizationManifest; }
        GeneratedBoardInstance getInstance() { return instance; }
        BoundedAssemblyPlan getPlan() { return plan; }
        Map<String, RuntimeTarget> getRuntimeTargets() { return runtimeTargets; }
        Map<String, RuntimeTarget> getMappingReceipt() { return runtimeTargets; }
        Map<String, EndpointManifest> getEndpointManifest() { return endpointManifest; }
        Map<String, EndpointManifest> getChosenEndpointManifest() { return endpointManifest; }
        Map<String, EndpointManifest> getEndpointMappings() { return endpointManifest; }
    }

    /** Immutable physical/fault/repair ownership record; IDs resolve through the runtime. */
    static final class RuntimeTarget {
        private final String blockKey;
        private final String qualifiedComponentId;
        private final String slotId;
        private final String originalPartId;
        private final String inventoryViewId;
        private final String capabilityId;
        private final String providerId;
        private final String faultId;
        private final String repairComponentId;
        private final String repairSlotId;

        RuntimeTarget(String blockKey, String qualifiedComponentId, String slotId,
                String originalPartId, String inventoryViewId, String capabilityId,
                String providerId, String faultId, String repairComponentId,
                String repairSlotId) {
            this.blockKey = required(blockKey, "blockKey");
            this.qualifiedComponentId = required(qualifiedComponentId, "componentId");
            this.slotId = required(slotId, "slotId");
            this.originalPartId = required(originalPartId, "originalPartId");
            this.inventoryViewId = required(inventoryViewId, "inventoryViewId");
            this.capabilityId = required(capabilityId, "capabilityId");
            this.providerId = required(providerId, "providerId");
            this.faultId = required(faultId, "faultId");
            this.repairComponentId = required(repairComponentId, "repairComponentId");
            this.repairSlotId = required(repairSlotId, "repairSlotId");
        }

        String getBlockKey() { return blockKey; }
        String getQualifiedComponentId() { return qualifiedComponentId; }
        String getComponentId() { return qualifiedComponentId; }
        String getSlotId() { return slotId; }
        String getOriginalPartId() { return originalPartId; }
        String getInventoryViewId() { return inventoryViewId; }
        String getInventoryId() { return inventoryViewId; }
        String getCapabilityId() { return capabilityId; }
        String getProviderId() { return providerId; }
        String getFaultId() { return faultId; }
        String getRepairComponentId() { return repairComponentId; }
        String getRepairTargetComponentId() { return repairComponentId; }
        String getRepairSlotId() { return repairSlotId; }
        String getRepairTargetSlotId() { return repairSlotId; }
    }

    /** Immutable record of a chosen real board endpoint and its actual CircuitJS post. */
    static final class EndpointManifest {
        private final String key;
        private final String blockKey;
        private final String localEndpointId;
        private final String qualifiedPadId;
        private final String netId;
        private final CircuitElm element;
        private final int postIndex;

        EndpointManifest(String key, String blockKey, String localEndpointId,
                String qualifiedPadId, String netId, CircuitElm element, int postIndex) {
            this.key = required(key, "endpoint key");
            this.blockKey = required(blockKey, "endpoint block");
            this.localEndpointId = required(localEndpointId, "local endpoint");
            this.qualifiedPadId = required(qualifiedPadId, "qualified pad");
            this.netId = required(netId, "endpoint net");
            if (element == null || postIndex < 0 || postIndex >= element.getPostCount())
                throw new IllegalArgumentException("Invalid endpoint manifest post");
            this.element = element;
            this.postIndex = postIndex;
        }

        String getKey() { return key; }
        String getBlockKey() { return blockKey; }
        String getLocalEndpointId() { return localEndpointId; }
        String getQualifiedPadId() { return qualifiedPadId; }
        String getPadId() { return qualifiedPadId; }
        String getNetId() { return netId; }
        CircuitElm getElement() { return element; }
        String getElementType() { return element.getClass().getSimpleName(); }
        String getElementClassName() { return element.getClass().getName(); }
        int getPostIndex() { return postIndex; }
        CircuitPostMeasurementEndpoint asEndpoint() {
            return new CircuitPostMeasurementEndpoint(element, postIndex);
        }
    }

    private static final class Context {
        private final BoundedAssemblyPlan plan;
        private final FailureProbe probe;
        private TroubleshootBoard board;
        private BoardPhysicalSpecifications specifications;
        private PhysicalConstructionMetadata physicalMetadata;
        private ElectricalConstructionContext electricalConstruction;
        private ConstructionReceipt constructionReceipt;
        private ContributionConstructionReceipt sourceReceipt;
        private ContributionConstructionReceipt loadReceipt;
        private ContributionConstructionReceipt driverReceipt;
        private DeviceJoinReceipt deviceReceipt;
        private BoundedElectricalDeviceConstructionAdapter.ResistiveStage resistiveStage;
        private GeneratedComponentBindings componentBindings;
        private GeneratedExternalPowerBindings powerBindings;
        private GeneratedComponentConnectionBindings connectionBindings;
        private PhysicalBoardRuntime runtime;
        private PhysicalMaterializationReceipt physicalReceipt;
        private PcbBoardLayout layout;
        private GeneratedBoardInstance instance;
        private final TreeMap<String, RuntimeTarget> runtimeTargets =
            new TreeMap<String, RuntimeTarget>();
        private final TreeMap<String, EndpointManifest> endpointManifest =
            new TreeMap<String, EndpointManifest>();
        private int mappedIdentityCount;
        private Stage currentStage;

        Context(BoundedAssemblyPlan plan, FailureProbe probe) {
            this.plan = plan;
            this.probe = probe;
        }

        void buildBoardAndSpecifications() {
            physicalMetadata = PhysicalConstructionMaterializer.describe(plan);
            board = physicalMetadata.getBoard();
            specifications = physicalMetadata.getSpecifications();
        }

        /** Complete the pure logical namespace mapping before graph allocation. */
        void prepareLogicalMapping() {
            if (plan.isControlledIndicator()) {
                prepareControlledLogicalMapping();
                return;
            }
            mappedIdentityCount = board.getComponentIds().size() + board.getPadIds().size() +
                board.getNetIds().size();
            for (String block : new String[] { "source", "load" }) {
                // Resolve every identity that the later graph/physical stages
                // will consume while the operation is still pure.
                plan.idFor(block, EntityKind.COMPONENT, "R1");
                plan.idFor(block, EntityKind.PAD, "R1.1");
                plan.idFor(block, EntityKind.PAD, "R1.2");
                plan.idFor(block, EntityKind.ENDPOINT, "R1_1");
                plan.idFor(block, EntityKind.ENDPOINT, "R1_2");
                plan.netFor(block, "RETURN");
            }
            plan.netFor("source", "SUPPLY");
            plan.netFor("source", "OUT");
        }

        private void prepareControlledLogicalMapping() {
            mappedIdentityCount = board.getComponentIds().size() + board.getPadIds().size() +
                board.getNetIds().size();
            for (String block : new String[] { "driver", "load" }) {
                ComposedBlockContribution contribution = plan.getBlocks().get(block);
                if (contribution == null)
                    throw new IllegalStateException("Missing controlled block mapping: " + block);
                for (String local : contribution.getResistors().keySet()) {
                    plan.idFor(block, EntityKind.COMPONENT, local);
                    ComposedBlockContribution.ResistorRecipe recipe =
                        contribution.getResistor(local);
                    plan.idFor(block, EntityKind.PAD, recipe.getFirstPadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getSecondPadLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getFirstEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getSecondEndpointLocalId());
                }
                for (ComposedBlockContribution.NmosRecipe recipe :
                        contribution.getNmosRecipes().values()) {
                    plan.idFor(block, EntityKind.COMPONENT, recipe.getComponentLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getGatePadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getDrainPadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getSourcePadLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getGateEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getDrainEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getSourceEndpointLocalId());
                }
                for (ComposedBlockContribution.LedRecipe recipe :
                        contribution.getLedRecipes().values()) {
                    plan.idFor(block, EntityKind.COMPONENT, recipe.getComponentLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getAnodePadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getCathodePadLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getAnodeEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getCathodeEndpointLocalId());
                }
                for (String net : contribution.getDescriptor().getNetIds())
                    plan.netFor(block, net);
            }
            for (DeviceAdapterContract adapter : plan.getDeviceAdapters()) {
                plan.idFor(adapter.getKey(), EntityKind.COMPONENT,
                    adapter.getComponentLocalId());
                plan.idFor(adapter.getKey(), EntityKind.PAD,
                    adapter.getComponentLocalId() + ".1");
                plan.idFor(adapter.getKey(), EntityKind.PAD,
                    adapter.getComponentLocalId() + ".2");
                plan.idFor(adapter.getKey(), EntityKind.ENDPOINT,
                    adapter.getComponentLocalId() + "_1");
                plan.idFor(adapter.getKey(), EntityKind.ENDPOINT,
                    adapter.getComponentLocalId() + "_2");
                for (String net : adapter.getDescriptor().getNetIds())
                    plan.netFor(adapter.getKey(), net);
            }
            for (String block : new String[] { "driver", "load" })
                for (String localNet : plan.getBlocks().get(block).getDescriptor().getNetIds())
                    plan.netFor(block, localNet);
        }

        /** Allocate the source half and the explicit output merge point. */
        void buildPrimaryCircuit() {
            if (electricalConstruction == null)
                electricalConstruction = ElectricalConstructionContext.begin(
                    plan.getElectricalRealizationSpec(), board, null);
            if (plan.isControlledIndicator()) {
                ElectricalRealizationSpec.ProviderDeclaration driverDeclaration =
                    plan.getElectricalRealizationSpec().getProviderDeclaration("driver");
                ElectricalRealizationSpec.ProviderDeclaration loadDeclaration =
                    plan.getElectricalRealizationSpec().getProviderDeclaration("load");
                driverReceipt = StandardElectricalConstructionProviders.provider(
                    driverDeclaration.getProviderId(), driverDeclaration.getProviderVersion())
                    .construct(driverDeclaration,
                        electricalConstruction.scope("driver", driverDeclaration.getProviderId(),
                            driverDeclaration.getProviderVersion()));
                loadReceipt = StandardElectricalConstructionProviders.provider(
                    loadDeclaration.getProviderId(), loadDeclaration.getProviderVersion())
                    .construct(loadDeclaration,
                        electricalConstruction.scope("load", loadDeclaration.getProviderId(),
                            loadDeclaration.getProviderVersion()));
                deviceReceipt = BoundedElectricalDeviceConstructionAdapter.constructControlled(
                    plan, electricalConstruction, driverReceipt, loadReceipt);
                electricalConstruction.finish();
                adoptConstructionReceipt();
                return;
            }
            sourceReceipt = StandardElectricalConstructionProviders.provider(
                plan.getBlocks().get("source").getProviderTypeId(),
                plan.getBlocks().get("source").getProviderVersion()).construct(
                plan.getElectricalRealizationSpec().getProviderDeclaration("source"),
                electricalConstruction.scope("source",
                    plan.getBlocks().get("source").getProviderTypeId(),
                    plan.getBlocks().get("source").getProviderVersion()));
            resistiveStage = BoundedElectricalDeviceConstructionAdapter.beginResistive(
                electricalConstruction, sourceReceipt);
        }

        /** Local controlled graph construction now belongs to bounded providers. */

        /** Allocate the load half, complete board endpoint mapping, and route layout. */
        void buildSecondaryCircuit() {
            if (plan.isControlledIndicator()) {
                if (constructionReceipt == null)
                    throw new IllegalStateException("Controlled graph was not allocated");
                return;
            }
            loadReceipt = StandardElectricalConstructionProviders.provider(
                plan.getBlocks().get("load").getProviderTypeId(),
                plan.getBlocks().get("load").getProviderVersion()).construct(
                plan.getElectricalRealizationSpec().getProviderDeclaration("load"),
                electricalConstruction.scope("load",
                    plan.getBlocks().get("load").getProviderTypeId(),
                    plan.getBlocks().get("load").getProviderVersion()));
            deviceReceipt = resistiveStage.finish(loadReceipt);
            electricalConstruction.finish();
            adoptConstructionReceipt();
        }

        private void adoptConstructionReceipt() {
            constructionReceipt = electricalConstruction.getReceipt();
            componentBindings = constructionReceipt.getComponentBindings();
            powerBindings = constructionReceipt.getPowerBindings();
            connectionBindings = constructionReceipt.getConnectionBindings();
        }

        private CircuitElm element(String ownerKey, String elementId) {
            if (constructionReceipt == null)
                throw new IllegalStateException("Electrical construction receipt is missing");
            return constructionReceipt.getElement(ownerKey, elementId).getElement();
        }

        private SwitchElm switchElement(String ownerKey, String elementId) {
            return (SwitchElm) element(ownerKey, elementId);
        }

        private PcbBoardLayout createPlannedLayout() {
            return plan.isControlledIndicator() ?
                ControlledIndicatorPcbLayoutFactory.create(board, specifications, plan) :
                PCB_LAYOUT_GENERATOR.generate(board,
                    plan.getRequest().getDescriptor().getRootSeed());
        }

        void bindMappingsAndLayout() {
            layout = createPlannedLayout();
            layout.validateGeometry(board);
            for (PhysicalConstructionPartDeclaration part : physicalMetadata.getDeclarations()
                    .getDeviceParts())
                addDeclaredEndpointManifests(part);
            int mappedLocalEndpoints = 0;
            for (PhysicalConstructionPartDeclaration part : physicalMetadata.getDeclarations()
                    .getLocalParts()) {
                addDeclaredEndpointManifests(part);
                mappedLocalEndpoints += part.getTerminals().size();
            }
            if (plan.isControlledIndicator())
                mappedIdentityCount += board.getPadIds().size();
            else
                mappedIdentityCount += mappedLocalEndpoints;
        }

        private void addDeclaredEndpointManifests(PhysicalConstructionPartDeclaration part) {
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
                String key = terminal.getManifestKey();
                String padId = terminal.getPadId();
                CircuitMeasurementEndpoint endpoint = board.getSimulationBindings()
                    .getEndpoint(padId);
                if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                    throw new IllegalStateException("Physical declaration has no CircuitJS endpoint: " +
                        padId);
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
                if (endpointManifest.put(key, new EndpointManifest(key,
                        terminal.getManifestBlockKey(), terminal.getEndpointId(), padId,
                        board.getPad(padId).getNetId(), post.getElement(), post.getPostIndex())) != null)
                    throw new IllegalStateException("Duplicate physical endpoint manifest: " + key);
            }
        }

        void buildPhysicalRuntime() {
            runtime = new PhysicalBoardRuntime(board);
            physicalReceipt = PhysicalConstructionMaterializer.materialize(runtime,
                physicalMetadata, plan, constructionReceipt);
            runtimeTargets.clear();
            runtimeTargets.putAll(physicalReceipt.getRuntimeTargets());
            candidates = new Vector<GeneratedFaultCandidate>(
                physicalReceipt.getCandidates());
            selectedCandidate = physicalReceipt.getSelectedCandidate();
        }

        private void validateContributionRequirements(
                ComposedBlockContribution sourceContribution,
                ComposedBlockContribution loadContribution,
                GeneratedBoardFamilyState familyState) {
            for (ComposedBlockContribution contribution : new ComposedBlockContribution[] {
                    sourceContribution, loadContribution }) {
                for (String requirement : contribution.getInputRequirements()) {
                    if (!"BOARD_POWER".equals(requirement) || board.getPowerInput(POWER_INPUT_ID) == null ||
                            powerBindings == null || !powerBindings.hasControlsForAllInputs())
                        throw new IllegalStateException("Unsupported or unsatisfied contribution " +
                            "input requirement: " + requirement);
                }
                for (String requirement : contribution.getRetestRequirements()) {
                    if (!"STEADY_DC_POWERED".equals(requirement) ||
                            (familyState != null &&
                                (familyState.getCustomerRetestProfile() == null ||
                                 familyState.getOperationCatalog() == null ||
                                 familyState.getCustomerRetestProfile().getRequiredPowerTransition() == null ||
                                 familyState.getCustomerRetestProfile().getRequiredInputTransition() == null ||
                                 familyState.getOperationCatalog().find(
                                     GeneratedBoardOperationIds.CUSTOMER_RETEST) == null)))
                        throw new IllegalStateException("Unsupported contribution retest requirement: " +
                            requirement);
                }
            }
        }

        private Vector<GeneratedFaultCandidate> candidates;
        private GeneratedFaultCandidate selectedCandidate;

        void buildInstance() {
            if (plan.isControlledIndicator()) {
                buildControlledInstance();
                return;
            }
            ComposedResistiveDeviceBehavior behavior =
                new ComposedResistiveDeviceBehavior(plan);
            GeneratedBoardFamilyState familyState = behavior.createFamilyState();
            validateContributionRequirements(plan.getBlocks().get("source"),
                plan.getBlocks().get("load"), familyState);
            GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
                behavior.createScenarioCatalog();
            GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
                "RESISTIVE_COUPLING_CHALLENGE", FAMILY_ID, TOPOLOGY_VARIANT_ID,
                plan.getRequest().getDescriptor().getRootSeed(), scenarios,
                "Repair verified. The resistive transfer path operates normally.",
                selectedCandidate.getFault(), selectedCandidate.getBinding(), behavior);
            GeneratedDiagnosticSolvabilityContract diagnostic =
                GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(FAMILY_ID,
                    TOPOLOGY_VARIANT_ID, plan.getRequest().getDescriptor().getRootSeed(), candidates);
            instance = new GeneratedBoardInstance(board, constructionReceipt.getElements(),
                plan.getRequest().getDescriptor().getRootSeed(), FAMILY_ID,
                TOPOLOGY_VARIANT_ID,
                "Generated resistive coupling canary, seed " +
                    plan.getRequest().getDescriptor().getRootSeed(),
                componentBindings, powerBindings, connectionBindings, behavior, layout,
                specifications, selectedCandidate.getBinding(), null, challenge, familyState,
                runtime, null, true, candidates, diagnostic);
            // Construction has already run the strict board/connection/runtime
            // checks.  Repeat the cheap detached checks before publication.
            board.validate();
            layout.validateGeometry(board);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();
        }

        private void buildControlledInstance() {
            SwitchElm controlledControlCommand = switchElement("device", "CONTROL_COMMAND");
            ControlledIndicatorDeviceBehavior behavior =
                new ControlledIndicatorDeviceBehavior(plan, controlledControlCommand);
            GeneratedBoardFamilyState familyState = behavior.createFamilyState();
            GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
                behavior.createScenarioCatalog();
            GeneratedComponentOperationalStates operationalStates =
                new GeneratedComponentOperationalStates();
            if (physicalReceipt == null)
                throw new IllegalStateException("Physical materialization receipt is missing");
            physicalReceipt.bindOperationalStates(operationalStates);
            GeneratedFault fault = selectedCandidate.getFault();
            GeneratedFaultBinding faultBinding = selectedCandidate.getBinding();
            GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
                "CONTROLLED_INDICATOR_CHALLENGE",
                ControlledIndicatorDeviceBehavior.FAMILY_ID,
                ControlledIndicatorDeviceBehavior.TOPOLOGY_VARIANT_ID,
                plan.getRequest().getDescriptor().getRootSeed(), scenarios,
                "Repair verified. The controlled indicator switches normally.",
                fault, faultBinding, behavior);
            instance = new GeneratedBoardInstance(board, constructionReceipt.getElements(),
                plan.getRequest().getDescriptor().getRootSeed(),
                ControlledIndicatorDeviceBehavior.FAMILY_ID,
                ControlledIndicatorDeviceBehavior.TOPOLOGY_VARIANT_ID,
                "Generated controlled indicator, seed " +
                    plan.getRequest().getDescriptor().getRootSeed(),
                componentBindings, powerBindings, connectionBindings, behavior, layout,
                specifications, faultBinding, operationalStates, challenge, familyState, runtime, null,
                false, candidates, null);
            board.validate();
            layout.validateGeometry(board);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();
        }

        Result result(RealizationManifest expectedManifest) {
            return new Result(instance, plan, runtimeTargets, endpointManifest, expectedManifest);
        }

        void begin(Stage stage) {
            if (stage == null)
                throw new IllegalArgumentException("Assembly stage is required");
            currentStage = stage;
        }

        Stage getCurrentStage() { return currentStage; }

        int allocatedElementCount() {
            return electricalConstruction == null ? 0 :
                electricalConstruction.getAllocatedElementCount();
        }
        int registeredPartCount() { return runtime == null ? 0 : runtime.getPhysicalParts().size(); }
        int mergeCount() { return plan.getMergeProvenance().size(); }

        void after(Stage stage) {
            if (probe != null)
                probe.after(stage);
        }

        boolean dispose(Throwable originalFailure) {
            if (electricalConstruction == null)
                return true;
            try {
                return electricalConstruction.abort(originalFailure);
            } catch (Throwable cleanupFailure) {
                if (originalFailure != null && originalFailure != cleanupFailure)
                    originalFailure.addSuppressed(cleanupFailure);
                return false;
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
