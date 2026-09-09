package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * The device-owned assembly boundary for the current bounded compositions.
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
    private static final SeededPcbLayoutGenerator PCB_LAYOUT_GENERATOR =
        new SeededPcbLayoutGenerator();

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
        ELECTRICAL,
        LAYOUT,
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

            context.begin(Stage.ELECTRICAL);
            context.buildCircuit();
            context.after(Stage.ELECTRICAL);

            context.begin(Stage.LAYOUT);
            context.bindMappingsAndLayout();
            context.after(Stage.LAYOUT);

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
        int getAllocatedElementCount() { return allocatedElementCount; }
        int getRegisteredPartCount() { return registeredPartCount; }
        int getMappedIdentityCount() { return mappedIdentityCount; }
        int getMergeCount() { return mergeCount; }
        boolean isCleanupSucceeded() { return cleanupSucceeded; }
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
        Map<String, EndpointManifest> getEndpointManifest() { return endpointManifest; }
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
        String getCapabilityId() { return capabilityId; }
        String getProviderId() { return providerId; }
        String getFaultId() { return faultId; }
        String getRepairComponentId() { return repairComponentId; }
        String getRepairSlotId() { return repairSlotId; }
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

        /** Resolve declared identities before graph allocation. */
        void prepareLogicalMapping() {
            mappedIdentityCount = board.getComponentIds().size() + board.getPadIds().size() +
                board.getNetIds().size();
            for (Map.Entry<String, FunctionalBlockDescriptor> entry :
                    plan.getNamespace().getBlocks().entrySet()) {
                String block = entry.getKey();
                FunctionalBlockDescriptor descriptor = entry.getValue();
                for (String component : descriptor.getComponents().keySet())
                    plan.idFor(block, EntityKind.COMPONENT, component);
                for (String pad : descriptor.getPads().keySet())
                    plan.idFor(block, EntityKind.PAD, pad);
                for (String endpoint : descriptor.getEndpoints().keySet())
                    plan.idFor(block, EntityKind.ENDPOINT, endpoint);
                for (String net : descriptor.getNetIds())
                    plan.netFor(block, net);
            }
        }

        /** Providers construct local elements; the device adapter owns cross-block joins. */
        void buildCircuit() {
            electricalConstruction = ElectricalConstructionContext.begin(
                plan.getElectricalRealizationSpec(), board, null);
            TreeMap<String, ContributionConstructionReceipt> contributions =
                new TreeMap<String, ContributionConstructionReceipt>();
            for (ElectricalRealizationSpec.ProviderDeclaration declaration :
                    plan.getElectricalRealizationSpec().getProviderDeclarations().values()) {
                if (declaration.isDeviceOwner()) continue;
                contributions.put(declaration.getOwnerKey(),
                    StandardElectricalConstructionProviders.provider(
                        declaration.getProviderId(), declaration.getProviderVersion()).construct(
                            declaration, electricalConstruction.scope(declaration.getOwnerKey(),
                                declaration.getProviderId(), declaration.getProviderVersion())));
            }
            BoundedElectricalDeviceConstructionAdapter.construct(plan, electricalConstruction,
                contributions);
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
            for (PhysicalConstructionPartDeclaration part : physicalMetadata.getDeclarations()
                    .getLocalParts()) {
                addDeclaredEndpointManifests(part);
            }
            mappedIdentityCount += endpointManifest.size();
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
