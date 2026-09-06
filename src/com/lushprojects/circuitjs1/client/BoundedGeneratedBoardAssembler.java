package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
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

    private static final String BOARD_ID = "RESISTIVE_COUPLING";
    private static final String POWER_INPUT_ID = "VIN_INPUT";
    private static final double SUPPLY_VOLTAGE = 5.0;
    private static final SeededPcbLayoutGenerator PCB_LAYOUT_GENERATOR =
        new SeededPcbLayoutGenerator();

    private BoundedGeneratedBoardAssembler() { }

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
        // No mutable owner is allocated before this pure resolution step.
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
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
            return context.result();
        } catch (Throwable failure) {
            boolean cleanupSucceeded = context.dispose();
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
        return "tsj-block-v1/resistive-coupling@1/" + block +
            "/component/R1";
    }

    private static String padId(String ignoredFamily, long ignoredSeed,
            String block, int terminal) {
        if (terminal < 1 || terminal > 2)
            throw new IllegalArgumentException("Invalid composed resistor terminal");
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

        Result(GeneratedBoardInstance instance, BoundedAssemblyPlan plan,
                Map<String, RuntimeTarget> runtimeTargets,
                Map<String, EndpointManifest> endpointManifest) {
            if (instance == null || plan == null || runtimeTargets == null ||
                    endpointManifest == null)
                throw new IllegalArgumentException("Incomplete bounded assembly result");
            this.instance = instance;
            this.plan = plan;
            this.runtimeTargets = Collections.unmodifiableMap(
                new TreeMap<String, RuntimeTarget>(runtimeTargets));
            this.endpointManifest = Collections.unmodifiableMap(
                new TreeMap<String, EndpointManifest>(endpointManifest));
        }

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
        private Vector<CircuitElm> elements = new Vector<CircuitElm>();
        private GeneratedComponentBindings componentBindings;
        private GeneratedExternalPowerBindings powerBindings;
        private GeneratedComponentConnectionBindings connectionBindings;
        private PhysicalBoardRuntime runtime;
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
            board = new TroubleshootBoard(BOARD_ID);
            String sourceSupply = plan.netFor("source", "SUPPLY");
            String output = plan.netFor("source", "OUT");
            String returned = plan.netFor("source", "RETURN");
            board.addNet(new BoardNet(sourceSupply));
            board.addNet(new BoardNet(output));
            board.addNet(new BoardNet(returned));

            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            board.addComponent(new BoardComponent("J1", "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
            board.addComponent(new BoardComponent(sourceComponent, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR));
            board.addComponent(new BoardComponent(loadComponent, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR));

            board.addPad(new BoardPad("J1.1", "J1", "1", sourceSupply));
            board.addPad(new BoardPad("J1.2", "J1", "2", returned));
            board.addPad(new BoardPad(plan.idFor("source", EntityKind.PAD, "R1.1"),
                sourceComponent, "1", sourceSupply));
            board.addPad(new BoardPad(plan.idFor("source", EntityKind.PAD, "R1.2"),
                sourceComponent, "2", output));
            board.addPad(new BoardPad(plan.idFor("load", EntityKind.PAD, "R1.1"),
                loadComponent, "1", output));
            board.addPad(new BoardPad(plan.idFor("load", EntityKind.PAD, "R1.2"),
                loadComponent, "2", returned));
            board.addPowerInput(new ExternalBoardPowerInput(POWER_INPUT_ID, "J1.1", "J1.2",
                sourceSupply, returned));
            board.validate();

            specifications = new BoardPhysicalSpecifications();
            specifications.addPhysicalDefinition("J1",
                new BasicPhysicalSpecification("J1_CONNECTOR"),
                new PhysicalNameplate("J1", "Power input connector"),
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
            for (String block : new String[] { "source", "load" }) {
                ComposedBlockContribution contribution = plan.getBlocks().get(block);
                String componentId = plan.idFor(block, EntityKind.COMPONENT, "R1");
                ResistorNameplate resistor = new ResistorNameplate(componentId,
                    contribution.getResistanceOhms(), 5.0,
                    contribution.getRatedWatts());
                specifications.addPhysicalDefinition(componentId, resistor,
                    new PhysicalNameplate(componentId, "Physical resistor markings",
                        "Markings", "Color bands"), PhysicalPackages.AXIAL_RESISTOR);
            }
            specifications.addPowerInputNameplate(new PowerInputNameplate(POWER_INPUT_ID,
                SUPPLY_VOLTAGE));
        }

        /** Complete the pure logical namespace mapping before graph allocation. */
        void prepareLogicalMapping() {
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

        /** Allocate the source half and the explicit output merge point. */
        void buildPrimaryCircuit() {
            double sourceResistance = plan.getBlocks().get("source").getResistanceOhms();

            // Add each element immediately after construction.  A later drag,
            // parameter write, or binding failure therefore still leaves the
            // private candidate's cleanup list complete.
            DCVoltageElm supply = new DCVoltageElm(snap(100), snap(320));
            add(supply);
            supply.drag(100, 160);
            supply.maxVoltage = SUPPLY_VOLTAGE;
            SwitchElm isolation = new SwitchElm(snap(100), snap(160));
            add(isolation);
            isolation.drag(180, 160);
            SwitchElm connector = new SwitchElm(snap(180), snap(160));
            add(connector);
            connector.drag(220, 160);
            WireElm supplyTrace = wire(220, 160, 230, 160);
            WireElm sourceFirstAttachment = wire(230, 160, 260, 160);
            ResistorElm sourceResistor = resistor(260, 160, 340, 160, sourceResistance);
            ResistorSecondaryOpenPath sourceSecondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(sourceResistor, 1));
            add(sourceSecondary.getSimulationElement());
            WireElm sourceSecondAttachment = wire(372, 160, 420, 160);

            // The output trace is the actual private merge boundary.  The
            // load half is deliberately allocated only after the MERGE hook.
            WireElm outputTrace = wire(420, 160, 500, 160);

            componentBindings = new GeneratedComponentBindings(board);
            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            componentBindings.bindComponent(sourceComponent, sourceResistor);
            componentBindings.bindAuxiliaryComponentElement(sourceComponent,
                sourceSecondary.getSimulationElement());

            powerBindings = new GeneratedExternalPowerBindings(board);
            Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
            powerElements.add(supply);
            powerElements.add(isolation);
            powerBindings.bindPowerInput(POWER_INPUT_ID,
                new ExternalPowerSimulationBinding(powerElements,
                    new SwitchExternalPowerControl(isolation)));

            this.connectionBindings = new GeneratedComponentConnectionBindings(board);
            BoardSimulationBindings bindings = board.getSimulationBindings();
            bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(connector, 1));
            String sourcePad1 = plan.idFor("source", EntityKind.PAD, "R1.1");
            String sourcePad2 = plan.idFor("source", EntityKind.PAD, "R1.2");
            bindings.bindPad(sourcePad1, new CircuitPostMeasurementEndpoint(supplyTrace, 1));
            bindings.bindPad(sourcePad2, new CircuitPostMeasurementEndpoint(outputTrace, 0));
            connectionBindings.bind(sourceComponent, sourcePad1,
                bindings.getEndpoint(sourcePad1), new CircuitPostMeasurementEndpoint(sourceResistor, 0),
                sourceFirstAttachment);
            connectionBindings.bind(sourceComponent, sourcePad2,
                bindings.getEndpoint(sourcePad2), sourceSecondary.getPublicTerminal(),
                sourceSecondAttachment);

            this.sourceResistor = sourceResistor;
            this.sourceSecondary = sourceSecondary;
            this.sourceFirstAttachment = sourceFirstAttachment;
            this.sourceSecondAttachment = sourceSecondAttachment;
            this.connector = connector;
            this.supply = supply;
            this.isolation = isolation;
            this.supplyTrace = supplyTrace;
            this.outputTrace = outputTrace;
        }

        /** Allocate the load half, complete board endpoint mapping, and route layout. */
        void buildSecondaryCircuit() {
            double loadResistance = plan.getBlocks().get("load").getResistanceOhms();
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            WireElm loadFirstAttachment = wire(500, 160, 580, 160);
            ResistorElm loadResistor = resistor(580, 160, 660, 160, loadResistance);
            ResistorSecondaryOpenPath loadSecondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(loadResistor, 1));
            add(loadSecondary.getSimulationElement());
            WireElm loadSecondAttachment = wire(692, 160, 740, 160);
            WireElm returnTrace = wire(740, 160, 740, 320);
            GroundElm ground = new GroundElm(snap(740), snap(320));
            add(ground);
            ground.drag(740, 352);
            WireElm returnBottom = wire(100, 320, 740, 320);

            componentBindings.bindComponent(loadComponent, loadResistor);
            componentBindings.bindAuxiliaryComponentElement(loadComponent,
                loadSecondary.getSimulationElement());

            BoardSimulationBindings bindings = board.getSimulationBindings();
            bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
            String loadPad1 = plan.idFor("load", EntityKind.PAD, "R1.1");
            String loadPad2 = plan.idFor("load", EntityKind.PAD, "R1.2");
            bindings.bindPad(loadPad1, new CircuitPostMeasurementEndpoint(outputTrace, 1));
            bindings.bindPad(loadPad2, new CircuitPostMeasurementEndpoint(returnTrace, 0));
            connectionBindings.bind(loadComponent, loadPad1,
                bindings.getEndpoint(loadPad1), new CircuitPostMeasurementEndpoint(loadResistor, 0),
                loadFirstAttachment);
            connectionBindings.bind(loadComponent, loadPad2,
                bindings.getEndpoint(loadPad2), loadSecondary.getPublicTerminal(),
                loadSecondAttachment);

            this.loadResistor = loadResistor;
            this.loadSecondary = loadSecondary;
            this.loadFirstAttachment = loadFirstAttachment;
            this.loadSecondAttachment = loadSecondAttachment;
            this.ground = ground;
            this.returnTrace = returnTrace;
            // returnBottom is a real owned graph element; retaining it is
            // unnecessary after add(), since disposal walks the complete list.
            if (returnBottom == null)
                throw new IllegalStateException("Missing return path");
        }

        private ResistorElm sourceResistor;
        private ResistorElm loadResistor;
        private ResistorSecondaryOpenPath sourceSecondary;
        private ResistorSecondaryOpenPath loadSecondary;
        private WireElm sourceFirstAttachment;
        private WireElm sourceSecondAttachment;
        private WireElm loadFirstAttachment;
        private WireElm loadSecondAttachment;
        private SwitchElm connector;
        private DCVoltageElm supply;
        private SwitchElm isolation;
        private GroundElm ground;
        private WireElm supplyTrace;
        private WireElm outputTrace;
        private WireElm returnTrace;

        void bindMappingsAndLayout() {
            layout = PCB_LAYOUT_GENERATOR.generate(board, plan.getRequest().getDescriptor()
                .getRootSeed());
            layout.validateGeometry(board);
            for (String block : new String[] { "source", "load" }) {
                String componentId = plan.idFor(block, EntityKind.COMPONENT, "R1");
                for (int terminal = 1; terminal <= 2; terminal++) {
                    String padId = plan.idFor(block, EntityKind.PAD, "R1." + terminal);
                    CircuitMeasurementEndpoint endpoint = board.getSimulationBindings()
                        .getEndpoint(padId);
                    if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                        throw new IllegalStateException("Composed mapping has no CircuitJS endpoint: " +
                            padId);
                    CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
                    endpointManifest.put(block + "/R1_" + terminal,
                        new EndpointManifest(block + "/R1_" + terminal, block,
                            "R1_" + terminal, padId, board.getPad(padId).getNetId(),
                            post.getElement(), post.getPostIndex()));
                }
                mappedIdentityCount += 2;
                if (board.getComponent(componentId) == null)
                    throw new IllegalStateException("Composed mapping has no component: " +
                        componentId);
            }
            // The fixed connector endpoints are retained in the same receipt.
            addFoundationManifest("J1.1", "J1", "1");
            addFoundationManifest("J1.2", "J1", "2");
        }

        private void addFoundationManifest(String key, String component, String terminal) {
            CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(key);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Composed connector mapping has no endpoint: " + key);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            endpointManifest.put(key, new EndpointManifest(key, component, component + "_" + terminal,
                key, board.getPad(key).getNetId(), post.getElement(), post.getPostIndex()));
        }

        void buildPhysicalRuntime() {
            runtime = new PhysicalBoardRuntime(board);
            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            ComposedBlockContribution sourceContribution = plan.getBlocks().get("source");
            ComposedBlockContribution loadContribution = plan.getBlocks().get("load");
            requireContribution(sourceContribution, "source");
            requireContribution(loadContribution, "load");
            PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
            PhysicalBoardSlot sourceSlot = runtime.createSlot(sourceComponent);
            PhysicalBoardSlot loadSlot = runtime.createSlot(loadComponent);
            PhysicalPartInventory<PhysicalResistorPart> sourceInventory =
                new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                    sourceComponent + "/inventory/replacements", PhysicalResistorPart.class);
            PhysicalPartInventory<PhysicalResistorPart> loadInventory =
                new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                    loadComponent + "/inventory/replacements", PhysicalResistorPart.class);
            ResistorReplacementCatalog sourceCatalog = new ResistorReplacementCatalog();
            ResistorReplacementCatalog loadCatalog = new ResistorReplacementCatalog();

            ResistorNameplate sourceSpecification =
                (ResistorNameplate) specifications.getSpecification(sourceComponent);
            ResistorNameplate loadSpecification =
                (ResistorNameplate) specifications.getSpecification(loadComponent);
            PhysicalNameplate sourcePlayerNameplate = specifications.getNameplate(sourceComponent);
            PhysicalNameplate loadPlayerNameplate = specifications.getNameplate(loadComponent);

            Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
            GeneratedFaultCandidate sourceCandidate = GeneratedFaultEngine.resistorIncorrectValue(
                sourceComponent + "/fault/" + sourceContribution.getFaultLocalId(), FAMILY_ID,
                plan.getRequest().getDescriptor().getRootSeed(), sourceComponent,
                sourceResistor, sourceSpecification.getNominalResistanceOhms(),
                sourceContribution.getFaultEffectiveOhms());
            GeneratedFaultCandidate loadCandidate = GeneratedFaultEngine.resistorIncorrectValue(
                loadComponent + "/fault/" + loadContribution.getFaultLocalId(), FAMILY_ID,
                plan.getRequest().getDescriptor().getRootSeed(), loadComponent,
                loadResistor, loadSpecification.getNominalResistanceOhms(),
                loadContribution.getFaultEffectiveOhms());
            candidates.add(sourceCandidate);
            candidates.add(loadCandidate);
            GeneratedFaultEngine.clearAll(candidates);
            validateContributionFault(sourceContribution, sourceComponent, sourceCandidate);
            validateContributionFault(loadContribution, loadComponent, loadCandidate);
            GeneratedFaultCandidate selected = "source".equals(plan.getFaultBlockKey()) ?
                sourceCandidate : loadCandidate;
            GeneratedFault fault = selected.getFault();
            GeneratedFaultBinding faultBinding = selected.getBinding();

            PhysicalResistorPart sourceOriginal = new PhysicalResistorPart(
                sourceComponent + "/part/original", sourceSpecification, sourceSpecification,
                sourcePlayerNameplate, sourceResistor, fault == sourceCandidate.getFault() ?
                    faultBinding : null, sourceSecondary, ResistorPartLocation.INSTALLED,
                new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                    sourceComponent));
            PhysicalResistorPart loadOriginal = new PhysicalResistorPart(
                loadComponent + "/part/original", loadSpecification, loadSpecification,
                loadPlayerNameplate, loadResistor, fault == loadCandidate.getFault() ?
                    faultBinding : null, loadSecondary, ResistorPartLocation.INSTALLED,
                new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                    loadComponent));
            sourceInventory.add(sourceOriginal);
            loadInventory.add(loadOriginal);
            ReplaceableComponentSlot sourceComponentSlot = new ReplaceableComponentSlot(
                sourceComponent, sourceSpecification, sourceOriginal, sourceFirstAttachment,
                sourceSecondAttachment, sourceSlot);
            ReplaceableComponentSlot loadComponentSlot = new ReplaceableComponentSlot(
                loadComponent, loadSpecification, loadOriginal, loadFirstAttachment,
                loadSecondAttachment, loadSlot);
            String sourceCapabilityId = sourceComponent + "/capability/replaceable-resistor";
            String loadCapabilityId = loadComponent + "/capability/replaceable-resistor";
            ReplaceableResistorBoardCapability sourceCapability =
                new ReplaceableResistorBoardCapability(sourceCapabilityId, sourceComponentSlot,
                    sourceInventory, sourceCatalog);
            ReplaceableResistorBoardCapability loadCapability =
                new ReplaceableResistorBoardCapability(loadCapabilityId, loadComponentSlot,
                    loadInventory, loadCatalog);
            runtime.registerCapability(sourceCapability);
            runtime.registerCapability(loadCapability);

            validateContributionRequirements(sourceContribution, loadContribution, null);

            String sourceRepairComponent = plan.idFor("source", EntityKind.COMPONENT,
                sourceContribution.getRepairLocalComponentId());
            String loadRepairComponent = plan.idFor("load", EntityKind.COMPONENT,
                loadContribution.getRepairLocalComponentId());
            validateRepairTarget(sourceContribution, sourceRepairComponent, sourceCatalog);
            validateRepairTarget(loadContribution, loadRepairComponent, loadCatalog);

            FixedPhysicalPart<BasicPhysicalSpecification> connectorPart =
                PhysicalFoundationPartFactory.fromBoardBindings("J1",
                    (BasicPhysicalSpecification) specifications.getSpecification("J1"),
                    specifications.getNameplate("J1"), PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                    board.getSimulationBindings(), connector,
                    new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "J1"));
            j1Slot.install(connectorPart);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();

            runtimeTargets.put("source", new RuntimeTarget("source", sourceComponent,
                sourceSlot.getId(), sourceOriginal.getId(), sourceInventory.getInventoryId(),
                sourceCapabilityId, sourceContribution.getProviderTypeId(),
                sourceCandidate.getFault().getId(), sourceRepairComponent,
                runtime.getSlot(sourceRepairComponent).getId()));
            runtimeTargets.put("load", new RuntimeTarget("load", loadComponent,
                loadSlot.getId(), loadOriginal.getId(), loadInventory.getInventoryId(),
                loadCapabilityId, loadContribution.getProviderTypeId(),
                loadCandidate.getFault().getId(), loadRepairComponent,
                runtime.getSlot(loadRepairComponent).getId()));
            this.candidates = candidates;
            this.selectedCandidate = selected;
        }

        private void requireContribution(ComposedBlockContribution contribution,
                String block) {
            if (contribution == null)
                throw new IllegalStateException("Missing resolved " + block +
                    " contribution");
            if (contribution.getFaultLocalId() == null ||
                    contribution.getFaultLocalId().length() == 0 ||
                    !finite(contribution.getFaultEffectiveOhms()) ||
                    contribution.getFaultEffectiveOhms() <= 0.0 ||
                    contribution.getRepairLocalComponentId() == null ||
                    contribution.getRepairLocalComponentId().length() == 0)
                throw new IllegalStateException("Incomplete resolved " + block +
                    " contribution metadata");
        }

        private void validateContributionFault(ComposedBlockContribution contribution,
                String componentId, GeneratedFaultCandidate candidate) {
            String expectedId = componentId + "/fault/" + contribution.getFaultLocalId();
            GeneratedFault fault = candidate == null ? null : candidate.getFault();
            if (fault == null || !expectedId.equals(fault.getId()) ||
                    !componentId.equals(fault.getTargetComponentId()) ||
                    !finite(fault.getEffectiveValue()) ||
                    fault.getEffectiveValue() != contribution.getFaultEffectiveOhms())
                throw new IllegalStateException("Resolved contribution fault metadata " +
                    "does not match the generated candidate: " + componentId);
        }

        private void validateRepairTarget(ComposedBlockContribution contribution,
                String repairComponentId, ResistorReplacementCatalog catalog) {
            if (runtime.getSlot(repairComponentId) == null ||
                    runtime.getWorkbenchPartsProvider(repairComponentId) == null ||
                    catalog.getEntries().isEmpty())
                throw new IllegalStateException("Resolved contribution repair target is " +
                    "not executable: " + repairComponentId);
            if (!contribution.getComponentLocalId().equals(
                    contribution.getRepairLocalComponentId()))
                throw new IllegalStateException("Unsupported composed repair component: " +
                    contribution.getRepairLocalComponentId());
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
            instance = new GeneratedBoardInstance(board, elements,
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

        Result result() {
            return new Result(instance, plan, runtimeTargets, endpointManifest);
        }

        void begin(Stage stage) {
            if (stage == null)
                throw new IllegalArgumentException("Assembly stage is required");
            currentStage = stage;
        }

        Stage getCurrentStage() { return currentStage; }

        int allocatedElementCount() { return elements.size(); }
        int registeredPartCount() { return runtime == null ? 0 : runtime.getPhysicalParts().size(); }
        int mergeCount() { return plan.getMergeProvenance().size(); }

        void after(Stage stage) {
            if (probe != null)
                probe.after(stage);
        }

        boolean dispose() {
            boolean success = true;
            try {
                if (powerBindings != null && powerBindings.hasControlsForAllInputs())
                    powerBindings.setConnected(false);
            } catch (Throwable failure) {
                success = false;
            }
            // Candidate elements have never been placed in the active graph.
            // delete() only needs the simulator for slider cleanup, so avoid
            // calling it in pure JVM construction tests without an initialized
            // CircuitElm owner.
            if (CircuitElm.sim != null) {
                for (int index = elements.size() - 1; index >= 0; index--) {
                    try {
                        elements.get(index).delete();
                    } catch (Throwable failure) {
                        success = false;
                    }
                }
            }
            return success;
        }

        private void add(CircuitElm... values) {
            for (CircuitElm value : values) {
                if (value == null)
                    throw new IllegalArgumentException("Composed graph contains a null element");
                if (elements.contains(value))
                    throw new IllegalStateException("Composed graph allocated an element twice");
                elements.add(value);
            }
        }

        private WireElm wire(int x, int y, int x2, int y2) {
            WireElm result = new WireElm(snap(x), snap(y));
            add(result);
            result.drag(x2, y2);
            return result;
        }

        private ResistorElm resistor(int x, int y, int x2, int y2, double resistance) {
            ResistorElm result = new ResistorElm(snap(x), snap(y));
            add(result);
            result.drag(x2, y2);
            result.setResistance(resistance);
            return result;
        }

        private int snap(int value) {
            if (CircuitElm.sim == null)
                throw new IllegalStateException("CircuitJS owner is required for graph allocation");
            return CircuitElm.sim.snapGrid(value);
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
