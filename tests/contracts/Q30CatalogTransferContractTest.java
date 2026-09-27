package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Native solver-backed Q30 catalog and cross-target capability contract. */
public final class Q30CatalogTransferContractTest {
    private static final long SEED = 13L;
    private static int assertions;

    private Q30CatalogTransferContractTest() { }

    public static void main(String[] args) {
        long started = System.nanoTime();
        TransferContext context = new TransferContext();
        Throwable failure = null;
        try {
            context.run();
        } catch (Throwable problem) {
            failure = problem;
        }
        try {
            context.cleanup();
        } catch (Throwable cleanupFailure) {
            if (failure == null) failure = cleanupFailure;
            else failure.addSuppressed(cleanupFailure);
        }
        System.out.println("Q30_CATALOG_TRANSFER_CLEANUP ownerDeleted=" +
            context.ownerDeleted + " graphEmpty=" + context.graphEmpty +
            " powerDisconnected=" + context.powerDisconnected +
            " capabilityRegistryCleared=" + context.capabilityRegistryCleared +
            " slotsRestored=" + context.slotsRestored +
            " nativeSolverModelResetInvoked=" + context.resetInvoked +
            " browserResetOutsideNativeFixture=true");
        if (failure != null) rethrow(failure);
        System.out.println("PASS: Q30 catalog transfer contracts " + assertions +
            " assertions elapsedMillis=" + ((System.nanoTime() - started) / 1000000L));
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new AssertionError(failure);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class TransferContext {
        final NativeCatalogCirSim sim = new NativeCatalogCirSim();
        final NativeCapabilityContext capabilityContext =
            new NativeCapabilityContext();
        final CirSim previousSimulator = CircuitElm.sim;
        GeneratedBoardInstance owner;
        PhysicalBoardRuntime runtime;
        BoardModificationController modifications;
        GeneratedChallengeController challenge;
        GeneratedBoardInstance previousOwner;
        BoardModificationController previousModifications;
        GeneratedChallengeController previousChallenge;
        Vector<CircuitElm> previousGraph;
        PhysicalPart<?> relayAOriginal;
        PhysicalPart<?> relayBOriginal;
        PhysicalPart<?> decisionAOriginal;
        PhysicalPart<?> decisionBOriginal;
        PhysicalPart<?> qaOriginal;
        PhysicalPart<?> qbOriginal;
        String relaySourceId;
        String relayTargetId;
        String decisionSourceId;
        String decisionTargetId;
        PhysicalPart<?> acquiredRelay;
        PhysicalPart<?> acquiredDecision;
        E04SensorControlModel.DecisionElement foreignDecision;
        boolean ownerDeleted;
        boolean graphEmpty;
        boolean powerDisconnected;
        boolean capabilityRegistryCleared;
        boolean slotsRestored;
        boolean resetInvoked;

        TransferContext() {
            previousOwner = sim.generatedBoardInstance;
            previousModifications = sim.boardModificationController;
            previousChallenge = sim.generatedChallengeController;
            previousGraph = sim.elmList;
        }

        void run() {
            CircuitElm.sim = sim;
            Q30ServiceFlowContractTest.configureSimulator(sim);
            owner = new Rb30Generator().generateNormalForQualification(SEED);
            check(owner.getSeed() == SEED && !owner.isDeveloperOnlyFaultRoute(),
                "seed-13 fixture is the accepted normal Q30 owner");
            check(owner.getPhysicalBoardRuntime().getPhysicalParts().size() == 37,
                "seed-13 normal owner retains the known 37 physical parts");
            installOwner();
            verifyCatalogIdentity();
            verifyDriverMetadata();
            acquiredRelay = transferRelay();
            acquiredDecision = transferDecision();
            serviceDriver("QA", "NMOS",
                NmosReplacementCatalog.CORRECT, true);
            serviceDriver("QB", "BJT",
                NpnReplacementCatalog.CORRECT, false);
            restoreAllOriginals();
            resetAndVerifyOwner();
        }

        private void installOwner() {
            sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.generatedBoardInstance = owner;
            runtime = owner.getPhysicalBoardRuntime();
            modifications = new BoardModificationController(sim, owner);
            sim.boardModificationController = modifications;
            runtime.installRegisteredCapabilities(sim, owner, modifications, 0);
            sim.boardPowerController.attach(owner.getExternalPowerBindings());
            sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
            runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);

            // Native fixtures retain the real current-owner gate while allowing
            // registered production capability strategies to run without a UI.
            challenge = new GeneratedChallengeController(sim, owner) {
                @Override boolean allowsWorkbenchInteraction() { return true; }
            };
            sim.generatedChallengeController = challenge;
            settle("initial normal owner");
            waitForRelayDischarge("KA");
            waitForRelayDischarge("KB");
        }

        private void verifyCatalogIdentity() {
            Rb30RelayService relayA = relayService("KA");
            Rb30RelayService relayB = relayService("KB");
            Rb30DecisionService decisionA = decisionService("U2A");
            Rb30DecisionService decisionB = decisionService("U2B");
            relayAOriginal = relayA.getMutationSlot().getInstalledPart();
            relayBOriginal = relayB.getMutationSlot().getInstalledPart();
            decisionAOriginal = decisionA.getOriginal();
            decisionBOriginal = decisionB.getOriginal();
            check(relayAOriginal != null && relayBOriginal != null &&
                    decisionAOriginal != null && decisionBOriginal != null,
                "Q30 transfer fixture has four installed original owners");

            PlayerShopCatalog shop = new PlayerShopCatalog(owner);
            PlayerShopCatalog.Category relays = shop.category("RELAY");
            PlayerShopCatalog.Entry relayChoice = findChoice(relays, null, "_5V");
            relaySourceId = relayChoice.acquisitionComponent;
            relayTargetId = oppositeChannel(relaySourceId, "KA", "KB");
            check(relays.entry(relayChoice.id) == relayChoice,
                "native relay catalog projection handle resolves to its selected entry");
            check(relays.entries().size() == 2 &&
                    relayChoice.specificationKey.equals(
                        new RelaySpecification(5).getSpecificationId()) &&
                    relayChoice.geometry.isEquivalentTo(
                        runtime.getSlot(relayTargetId).getGeometryRealization()),
                "native relay catalog projection deduplicates same specification and compatible " +
                    relayTargetId + " target geometry");

            PlayerShopCatalog.Category decisions = shop.category("SENSOR_CONTROL");
            PlayerShopCatalog.Entry decisionChoice = findChoice(decisions, null, null);
            decisionSourceId = decisionChoice.acquisitionComponent;
            decisionTargetId = oppositeChannel(decisionSourceId, "U2A", "U2B");
            String declaration = ((E04DecisionControlPart) decisionAOriginal)
                .getElement().declarationIdentity();
            check(decisions.entries().size() == 1 &&
                    decisionChoice.specificationKey.equals(declaration) &&
                    declaration.equals(((E04DecisionControlPart) decisionBOriginal)
                        .getElement().declarationIdentity()) &&
                    decisionChoice.geometry.isEquivalentTo(
                        runtime.getSlot(decisionTargetId).getGeometryRealization()),
                "native decision catalog choice uses the six-field declaration identity and " +
                    decisionTargetId + " target fit");
            check(decisionChoice.catalogId.equals(
                    decisionService(decisionSourceId).getCatalogId()),
                "native decision catalog choice resolves to the source " + decisionSourceId +
                    " acquisition catalog");
        }

        private void verifyDriverMetadata() {
            qaOriginal = runtime.getInstalledPart("QA");
            qbOriginal = runtime.getInstalledPart("QB");
            PhysicalSpecification qaDefinition =
                owner.getPhysicalSpecifications().getSpecification("QA");
            PhysicalSpecification qbDefinition =
                owner.getPhysicalSpecifications().getSpecification("QB");
            check(qaDefinition instanceof NmosSpecification &&
                    qbDefinition instanceof NpnSpecification,
                "seed-13 QA/QB declarations retain their NMOS and NPN specification types");
            check("QA".equals(qaDefinition.getSpecificationId()) &&
                    "QB".equals(qbDefinition.getSpecificationId()) &&
                    qaOriginal instanceof PhysicalNmosPart &&
                    qbOriginal instanceof PhysicalNpnPart &&
                    "QA".equals(qaOriginal.getId()) && "QB".equals(qbOriginal.getId()) &&
                    qaOriginal.getSpecification() == qaDefinition &&
                    qbOriginal.getSpecification() == qbDefinition,
                "Q30 physical owners bind the typed specification identities to QA and QB");

            NmosSpecification qaSpec = (NmosSpecification) qaDefinition;
            NpnSpecification qbSpec = (NpnSpecification) qbDefinition;
            NMosfetElm qaElement = ((PhysicalNmosPart) qaOriginal).getElement();
            NTransistorElm qbElement = ((PhysicalNpnPart) qbOriginal).getElement();
            check(qaElement.vt == qaSpec.getThresholdVoltage() &&
                    qaElement.beta == qaSpec.getBeta() &&
                    qaSpec.getThresholdVoltage() == 1.5 && qaSpec.getBeta() == 5,
                "QA NMOS physical metadata matches the live CircuitJS threshold and beta");
            check(qbElement.beta == qbSpec.getBeta() && qbSpec.getBeta() == 100,
                "QB NPN physical metadata matches the live CircuitJS beta");

            BoardComponent qaBoardComponent = owner.getBoard().getComponent("QA");
            BoardComponent qbBoardComponent = owner.getBoard().getComponent("QB");
            check("NMOS".equals(qaBoardComponent.getType()) &&
                    qaBoardComponent.getPhysicalPackage().isEquivalentTo(
                        PhysicalPackages.TO92_NMOS) &&
                    "BJT".equals(qbBoardComponent.getType()) &&
                    qbBoardComponent.getPhysicalPackage().isEquivalentTo(
                        PhysicalPackages.TO92_NPN),
                "Q30 board metadata preserves distinct QA NMOS and QB NPN identities");
        }

        private PhysicalPart<?> serviceDriver(String componentId,
                String categoryId, String expectedCatalogId, boolean nmos) {
            PhysicalPart<?> original = "QA".equals(componentId) ? qaOriginal : qbOriginal;
            PhysicalPart<?> foreign = "QA".equals(componentId) ? qbOriginal : qaOriginal;
            WorkbenchPartsProvider parts = runtime.getWorkbenchPartsProvider(componentId);
            PhysicalSlotMutationProvider mutation = runtime.getMutationProvider(componentId);
            check(parts != null && mutation instanceof CatalogAcquisitionProvider,
                componentId + " typed driver has the production catalog acquisition provider");

            PlayerShopCatalog.Category category =
                new PlayerShopCatalog(owner).category(categoryId);
            PlayerShopCatalog.Entry choice = findChoice(category, componentId, null);
            check(choice.catalogId.equals(expectedCatalogId) &&
                    choice.acquisitionComponent.equals(componentId),
                componentId + " catalog exposes compatible typed stock under its board identity");
            Vector<PhysicalPart<?>> before = parts.getLooseParts();
            PhysicalPart<?> acquired = acquireFromCatalog(componentId, choice, parts,
                before, componentId + " native catalog acquisition");
            check(acquired != null && acquired != original &&
                    runtime.getPart(acquired.getId()) == acquired &&
                    parts.ownsPart(acquired.getId()) && !acquired.isInstalled(),
                componentId + " acquires a real loose part in its owning inventory");
            check(nmos ? acquired instanceof PhysicalNmosPart :
                    acquired instanceof PhysicalNpnPart,
                componentId + " acquisition retains the board's transistor type");
            if (nmos) {
                NmosSpecification specification =
                    ((PhysicalNmosPart) acquired).getSpecification();
                NMosfetElm element = ((PhysicalNmosPart) acquired).getElement();
                check("NMOS_2N7000".equals(specification.getSpecificationId()) &&
                        element.vt == specification.getThresholdVoltage() &&
                        element.beta == specification.getBeta(),
                    "QA compatible catalog stock carries matching NMOS specification and element values");
            } else {
                NpnSpecification specification =
                    ((PhysicalNpnPart) acquired).getSpecification();
                NTransistorElm element = ((PhysicalNpnPart) acquired).getElement();
                check("NPN_2N3904".equals(specification.getSpecificationId()) &&
                        element.beta == specification.getBeta(),
                    "QB compatible catalog stock carries matching NPN specification and element values");
            }

            HashMap<String, CircuitMeasurementEndpoint> boardEndpoints =
                captureBoardEndpoints(componentId);
            Vector<CircuitElm> originalBacking =
                original.getElectricalBacking().getCircuitElements();
            invokeCapability(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,
                original), "remove " + componentId + " original transistor");
            check(runtime.getInstalledPart(componentId) == null &&
                    !original.isInstalled() && original.getBoardSlot() == null,
                componentId + " removal leaves the typed slot empty and original part loose");
            check(!runtime.isPartInstallableAt(foreign, componentId),
                componentId + " empty slot still rejects the opposite transistor type");
            verifyLooseTransistorIsolation(componentId, original, original,
                boardEndpoints, "original " + componentId + " after removal");

            Vector<CircuitElm> beforeForeign = new Vector<CircuitElm>(sim.elmList);
            WorkbenchOperation foreignOperation = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, foreign, componentId);
            check(!capabilityContext.isAvailable(foreignOperation) &&
                    !capabilityContext.dispatch(foreignOperation) &&
                    runtime.getInstalledPart(componentId) == null &&
                    beforeForeign.equals(sim.elmList),
                componentId + " capability rejects the opposite transistor type before graph mutation");

            check(runtime.isPartInstallableAt(acquired, componentId),
                componentId + " empty slot accepts the owned compatible catalog part");
            WorkbenchOperation install = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, acquired, componentId);
            check(capabilityContext.isAvailable(install) &&
                    capabilityContext.dispatch(install),
                componentId + " production capability installs the acquired transistor");
            settle("native " + componentId + " compatible transistor installed");

            PhysicalMutationSlot slot = ((PhysicalSlotMutationProvider.Scoped) mutation)
                .getMutationSlot();
            Vector<CircuitElm> acquiredBacking =
                acquired.getElectricalBacking().getCircuitElements();
            check(runtime.getInstalledPart(componentId) == acquired &&
                    acquired.isInstalled() && acquired.getBoardSlot() == slot.getPhysicalSlot() &&
                    runtime.getWorkbenchPartsProviderForPart(acquired.getId()) == parts &&
                    runtime.getMutationProviderForPart(acquired.getId()) == mutation &&
                    sim.elmList.containsAll(originalBacking) &&
                    sim.elmList.containsAll(acquiredBacking),
                componentId + " install mounts new backing while preserving the original loose backing");
            check(owner.getComponentBindings().getElements(componentId)
                    .containsAll(acquiredBacking) &&
                    !owner.getComponentBindings().getElements(componentId)
                        .containsAll(originalBacking),
                componentId + " component bindings select the replacement as the installed owner");
            Vector<String> expectedTerminals = owner.getBoard()
                .getComponent(componentId).getPhysicalPackage().getTerminalIds();
            Vector<String> seenTerminals = new Vector<String>();
            check(boardEndpoints.size() == 3 && expectedTerminals.size() == 3,
                componentId + " retains three physical terminals and persistent board endpoints");
            for (GeneratedComponentConnectionBinding binding :
                    owner.getConnectionBindings().getForComponent(componentId)) {
                BoardPad pad = owner.getBoard().getPad(binding.getPadId());
                check(componentId.equals(pad.getComponentId()) &&
                        expectedTerminals.contains(pad.getTerminalId()) &&
                        !seenTerminals.contains(pad.getTerminalId()) &&
                        modifications.isLeadConnected(componentId, binding.getPadId()) &&
                        sim.elmList.contains(binding.getConnectionElement()) &&
                        boardEndpoints.get(binding.getPadId()) == binding.getBoardEndpoint() &&
                        GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getComponentEndpoint(),
                            slot.getExpectedEndpoint(acquired, pad)) &&
                        !GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getComponentEndpoint(),
                            slot.getExpectedEndpoint(original, pad)) &&
                        endpointsHaveDistinctPosts(binding.getBoardEndpoint(),
                            binding.getComponentEndpoint()),
                    componentId + " active copper lead binds the board endpoint to the installed " +
                        pad.getTerminalId() + " on the replacement part");
                seenTerminals.add(pad.getTerminalId());
            }
            check(seenTerminals.containsAll(expectedTerminals),
                componentId + " board pads retain the full package terminal map");

            Vector<CircuitElm> staleGraph = new Vector<CircuitElm>(sim.elmList);
            check(!capabilityContext.isAvailable(install) &&
                    !capabilityContext.dispatch(install) &&
                    runtime.getInstalledPart(componentId) == acquired &&
                    staleGraph.equals(sim.elmList),
                componentId + " capability rejects a stale mounted install without graph change");
            invokeCapability(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,
                acquired), "remove acquired " + componentId + " transistor");
            check(runtime.getInstalledPart(componentId) == null &&
                    !acquired.isInstalled() && acquired.getBoardSlot() == null &&
                    parts.ownsPart(acquired.getId()),
                componentId + " removed catalog stock remains available in its source inventory");
            verifyLooseTransistorIsolation(componentId, acquired, original,
                boardEndpoints, "acquired " + componentId + " after removal");
            installOriginal(componentId, original,
                "restore original " + componentId + " transistor");
            check(runtime.getInstalledPart(componentId) == original &&
                    original.isInstalled() && sim.elmList.containsAll(originalBacking),
                componentId + " original solver part and physical identity restore after service");
            verifyInstalledTransistorConnections(componentId, original, boardEndpoints,
                "restored original " + componentId);
            return acquired;
        }

        private void verifyLooseTransistorIsolation(String componentId,
                PhysicalPart<?> loosePart, PhysicalPart<?> canonicalPart,
                HashMap<String, CircuitMeasurementEndpoint> boardEndpoints,
                String label) {
            PhysicalSlotMutationProvider.Scoped provider =
                (PhysicalSlotMutationProvider.Scoped) runtime.getMutationProvider(componentId);
            PhysicalMutationSlot slot = provider.getMutationSlot();
            Vector<CircuitElm> looseBacking =
                loosePart.getElectricalBacking().getCircuitElements();
            Vector<CircuitElm> canonicalBacking =
                canonicalPart.getElectricalBacking().getCircuitElements();
            check(!loosePart.isInstalled() && loosePart.getBoardSlot() == null &&
                    sim.elmList.containsAll(looseBacking) &&
                    owner.getSimulationElements().containsAll(looseBacking),
                label + " retains active solver backing for independent loose-part access");
            check(slot.isEmpty() && runtime.getInstalledPart(componentId) == null &&
                    owner.getComponentBindings().getElements(componentId)
                        .containsAll(canonicalBacking),
                label + " leaves the slot empty with its canonical generated backing identity");

            Vector<String> expectedTerminals = owner.getBoard()
                .getComponent(componentId).getPhysicalPackage().getTerminalIds();
            Vector<String> seenTerminals = new Vector<String>();
            for (GeneratedComponentConnectionBinding binding :
                    owner.getConnectionBindings().getForComponent(componentId)) {
                BoardPad pad = owner.getBoard().getPad(binding.getPadId());
                CircuitMeasurementEndpoint looseEndpoint =
                    slot.getExpectedEndpoint(loosePart, pad);
                check(!modifications.isLeadConnected(componentId, binding.getPadId()) &&
                        !sim.elmList.contains(binding.getConnectionElement()),
                    label + " keeps detachable " + pad.getTerminalId() +
                        " lead out of the active solver graph");
                check(componentId.equals(pad.getComponentId()) &&
                        expectedTerminals.contains(pad.getTerminalId()) &&
                        !seenTerminals.contains(pad.getTerminalId()) &&
                        boardEndpoints.get(binding.getPadId()) == binding.getBoardEndpoint() &&
                        GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getComponentEndpoint(),
                            slot.getExpectedEndpoint(canonicalPart, pad)) &&
                        !GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getBoardEndpoint(), looseEndpoint) &&
                        endpointsHaveDistinctPosts(binding.getBoardEndpoint(), looseEndpoint) &&
                        !GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getBoardEndpoint(), binding.getComponentEndpoint()) &&
                        endpointsHaveDistinctPosts(binding.getBoardEndpoint(),
                            binding.getComponentEndpoint()),
                    label + " preserves separate board and loose " + pad.getTerminalId() +
                        " endpoints without a board shunt");
                seenTerminals.add(pad.getTerminalId());
            }
            check(seenTerminals.containsAll(expectedTerminals) &&
                    boardEndpoints.size() == expectedTerminals.size(),
                label + " disconnects all package terminals from persistent board endpoints");
        }

        private void verifyInstalledTransistorConnections(String componentId,
                PhysicalPart<?> installed, HashMap<String, CircuitMeasurementEndpoint> boardEndpoints,
                String label) {
            PhysicalSlotMutationProvider.Scoped provider =
                (PhysicalSlotMutationProvider.Scoped) runtime.getMutationProvider(componentId);
            PhysicalMutationSlot slot = provider.getMutationSlot();
            for (GeneratedComponentConnectionBinding binding :
                    owner.getConnectionBindings().getForComponent(componentId)) {
                BoardPad pad = owner.getBoard().getPad(binding.getPadId());
                check(modifications.isLeadConnected(componentId, binding.getPadId()) &&
                        sim.elmList.contains(binding.getConnectionElement()) &&
                        boardEndpoints.get(binding.getPadId()) == binding.getBoardEndpoint() &&
                        GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getComponentEndpoint(),
                            slot.getExpectedEndpoint(installed, pad)) &&
                        endpointsHaveDistinctPosts(binding.getBoardEndpoint(),
                            binding.getComponentEndpoint()),
                    label + " reconnects the persistent board endpoint to installed " +
                        pad.getTerminalId());
            }
        }

        private boolean endpointsHaveDistinctPosts(CircuitMeasurementEndpoint first,
                CircuitMeasurementEndpoint second) {
            if (!(first instanceof CircuitPostMeasurementEndpoint) ||
                    !(second instanceof CircuitPostMeasurementEndpoint)) return false;
            CircuitPostMeasurementEndpoint firstPost =
                (CircuitPostMeasurementEndpoint) first;
            CircuitPostMeasurementEndpoint secondPost =
                (CircuitPostMeasurementEndpoint) second;
            Point firstPoint = firstPost.getElement().getPost(firstPost.getPostIndex());
            Point secondPoint = secondPost.getElement().getPost(secondPost.getPostIndex());
            return firstPoint != null && secondPoint != null &&
                !firstPoint.equals(secondPoint);
        }

        private PhysicalPart<?> transferRelay() {
            String sourceId = relaySourceId;
            String targetId = relayTargetId;
            PhysicalPart<?> sourceOriginal = relayOriginal(sourceId);
            PhysicalPart<?> targetOriginal = relayOriginal(targetId);
            WorkbenchPartsProvider sourceParts = runtime.getWorkbenchPartsProvider(sourceId);
            WorkbenchPartsProvider targetParts = runtime.getWorkbenchPartsProvider(targetId);
            PlayerShopCatalog.Entry choice = findChoice(
                new PlayerShopCatalog(owner).category("RELAY"), sourceId, "_5V");
            Vector<PhysicalPart<?>> before = sourceParts.getLooseParts();
            PhysicalPart<?> acquired = acquireFromCatalog(sourceId, choice, sourceParts,
                before, sourceId + " native catalog acquisition");
            check(acquired != null && runtime.getPart(acquired.getId()) == acquired &&
                    sourceParts.ownsPart(acquired.getId()) &&
                    !targetParts.ownsPart(acquired.getId()) && !acquired.isInstalled(),
                sourceId + " owns the acquired loose relay while " + targetId + " does not");
            check(!runtime.isPartInstallableAt(acquired, targetId),
                "source-owned " + sourceId + " relay stays blocked while occupied " +
                    targetId + " is retained");
            HashMap<String, CircuitMeasurementEndpoint> boardEndpoints =
                captureBoardEndpoints(targetId);
            invokeCapability(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,
                targetOriginal), "remove " + targetId + " original relay");
            check(runtime.getSlot(targetId).getInstalledPart() == null &&
                    runtime.isPartInstallableAt(acquired, targetId),
                targetId + " target slot is empty and still admits the source-owned relay");

            PhysicalPart<?> foreign = decisionAOriginal;
            WorkbenchOperation foreignOperation = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, foreign, targetId);
            Vector<CircuitElm> graphBefore = new Vector<CircuitElm>(sim.elmList);
            check(!capabilityContext.isAvailable(foreignOperation) &&
                    !capabilityContext.dispatch(foreignOperation) &&
                    runtime.getSlot(targetId).getInstalledPart() == null &&
                    graphBefore.equals(sim.elmList),
                "native capability rejects foreign physical type before " + targetId +
                    " graph mutation");

            WorkbenchOperation install = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, acquired, targetId);
            check(capabilityContext.isAvailable(install),
                "native capability exposes " + sourceId + " relay installation at " + targetId);
            check(capabilityContext.dispatch(install),
                "native capability installs the " + sourceId + " relay at " + targetId);
            settle("native " + sourceId + " relay installed at " + targetId);
            verifyCrossInstall(sourceId, targetId, sourceOriginal, targetOriginal,
                acquired, targetParts, boardEndpoints);

            Vector<CircuitElm> staleGraph = new Vector<CircuitElm>(sim.elmList);
            check(!capabilityContext.isAvailable(install) &&
                    !capabilityContext.dispatch(install) &&
                    runtime.getInstalledPart(targetId) == acquired &&
                    staleGraph.equals(sim.elmList),
                "native capability rejects stale mounted " + sourceId + " relay at " +
                    targetId + " without graph change");
            removeAndRestore(sourceId, targetId, acquired, targetOriginal,
                sourceParts, targetParts);
            return acquired;
        }

        private PhysicalPart<?> transferDecision() {
            String sourceId = decisionSourceId;
            String targetId = decisionTargetId;
            PhysicalPart<?> sourceOriginal = decisionOriginal(sourceId);
            PhysicalPart<?> targetOriginal = decisionOriginal(targetId);
            WorkbenchPartsProvider sourceParts = runtime.getWorkbenchPartsProvider(sourceId);
            WorkbenchPartsProvider targetParts = runtime.getWorkbenchPartsProvider(targetId);
            PlayerShopCatalog.Entry choice = findChoice(
                new PlayerShopCatalog(owner).category("SENSOR_CONTROL"), sourceId, null);
            Vector<PhysicalPart<?>> before = sourceParts.getLooseParts();
            PhysicalPart<?> acquired = acquireFromCatalog(sourceId, choice, sourceParts,
                before, sourceId + " native catalog acquisition");
            check(acquired instanceof E04DecisionControlPart &&
                    sourceParts.ownsPart(acquired.getId()) &&
                    !targetParts.ownsPart(acquired.getId()) && !acquired.isInstalled(),
                sourceId + " owns the acquired loose decision while " + targetId + " does not");
            check(!runtime.isPartInstallableAt(acquired, targetId),
                "source-owned " + sourceId + " decision stays blocked while occupied " +
                    targetId + " is retained");
            HashMap<String, CircuitMeasurementEndpoint> boardEndpoints =
                captureBoardEndpoints(targetId);
            invokeCapability(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,
                targetOriginal), "remove " + targetId + " original decision");
            check(runtime.getSlot(targetId).getInstalledPart() == null &&
                    runtime.isPartInstallableAt(acquired, targetId),
                targetId + " target slot is empty and still admits the source-owned decision");

            E04SensorControlModel.DecisionElement current =
                ((E04DecisionControlPart) targetOriginal).getElement();
            E04SensorControlModel.Variant differentVariant =
                Rb30Plan.resolve(SEED).sharedHystereticReference ?
                    E04SensorControlModel.Variant.DIRECT_THRESHOLD :
                    E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE;
            E04SensorControlModel.RailContract rail =
                E04SensorControlModel.RailDeclaration.fiveVolt();
            E04SensorControlModel.Configuration configuration =
                E04SensorControlModel.Configuration.defaults(rail);
            foreignDecision = new E04SensorControlModel.DecisionElement(0, 0,
                rail, differentVariant, configuration);
            E04DecisionControlPart wrongDeclaration = new E04DecisionControlPart(
                "Q30_FOREIGN_DECISION_DECLARATION",
                new BasicPhysicalSpecification(foreignDecision.declarationIdentity()),
                new PhysicalNameplate("Q30_FOREIGN_DECISION_DECLARATION",
                    "Foreign sensor controller"), foreignDecision,
                new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED,
                    "Q30_FOREIGN_DECISION_DECLARATION"));
            Vector<CircuitElm> graphBefore = new Vector<CircuitElm>(sim.elmList);
            boolean setterRejected = false;
            try {
                decisionService(targetId).setActiveDecisionElement(foreignDecision);
            } catch (IllegalArgumentException expected) {
                setterRejected = true;
            }
            WorkbenchOperation wrongOperation = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, wrongDeclaration, targetId);
            check(setterRejected &&
                    !runtime.isPartInstallableAt(wrongDeclaration, targetId) &&
                    !capabilityContext.isAvailable(wrongOperation) &&
                    !capabilityContext.dispatch(wrongOperation) &&
                    runtime.getSlot(targetId).getInstalledPart() == null &&
                    decisionService(targetId).getActiveDecisionElement() == current &&
                    graphBefore.equals(sim.elmList),
                "native capability rejects wrong six-field decision declaration before " +
                    targetId + " mutation");

            WorkbenchOperation install = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, acquired, targetId);
            check(capabilityContext.isAvailable(install),
                "native capability exposes " + sourceId + " decision installation at " + targetId);
            check(capabilityContext.dispatch(install),
                "native capability installs the " + sourceId + " decision at " + targetId);
            settle("native " + sourceId + " decision installed at " + targetId);
            verifyCrossInstall(sourceId, targetId, sourceOriginal, targetOriginal,
                acquired, targetParts, boardEndpoints);
            check(decisionService(targetId).getActiveDecisionElement() ==
                    ((E04DecisionControlPart) acquired).getElement(),
                targetId + " current decision follows the transferred live decision element");

            Vector<CircuitElm> staleGraph = new Vector<CircuitElm>(sim.elmList);
            check(!capabilityContext.isAvailable(install) &&
                    !capabilityContext.dispatch(install) &&
                    runtime.getInstalledPart(targetId) == acquired &&
                    staleGraph.equals(sim.elmList),
                "native capability rejects stale mounted " + sourceId + " decision at " +
                    targetId + " without graph change");
            removeAndRestore(sourceId, targetId, acquired, targetOriginal,
                sourceParts, targetParts);
            if (foreignDecision != null) {
                foreignDecision.delete();
                foreignDecision = null;
            }
            return acquired;
        }

        private void verifyCrossInstall(String sourceId, String targetId,
                PhysicalPart<?> sourceOriginal, PhysicalPart<?> targetOriginal,
                PhysicalPart<?> acquired, WorkbenchPartsProvider targetParts,
                HashMap<String, CircuitMeasurementEndpoint> boardEndpoints) {
            PhysicalSlotMutationProvider source = runtime.getMutationProvider(sourceId);
            PhysicalSlotMutationProvider target = runtime.getMutationProvider(targetId);
            PhysicalMutationSlot targetSlot = ((PhysicalSlotMutationProvider.Scoped) target)
                .getMutationSlot();
            check(runtime.getInstalledPart(sourceId) == sourceOriginal &&
                    sourceOriginal.isInstalled() &&
                    runtime.getPart(targetOriginal.getId()) == targetOriginal &&
                    targetParts.ownsPart(targetOriginal.getId()) &&
                    runtime.getInstalledPart(targetId) == acquired &&
                    acquired.isInstalled() && acquired.getBoardSlot() ==
                        targetSlot.getPhysicalSlot() &&
                    source.ownsPart(acquired.getId()) &&
                    !targetParts.ownsPart(acquired.getId()) &&
                    runtime.getWorkbenchPartsProviderForPart(acquired.getId()) ==
                        runtime.getWorkbenchPartsProvider(sourceId) &&
                    runtime.getMutationProviderForPart(acquired.getId()) == source,
                sourceId + " -> " + targetId +
                    " preserves source inventory and target slot identity");
            for (GeneratedComponentConnectionBinding binding :
                    owner.getConnectionBindings().getForComponent(targetId)) {
                BoardPad pad = owner.getBoard().getPad(binding.getPadId());
                    check(boardEndpoints.get(binding.getPadId()) == binding.getBoardEndpoint() &&
                        GeneratedComponentConnectionBindings.sameEndpoint(
                            binding.getComponentEndpoint(),
                            targetSlot.getExpectedEndpoint(acquired, pad)),
                    sourceId + " -> " + targetId +
                        " board copper remains mapped to the transferred part terminal " +
                        binding.getPadId());
            }
            for (CircuitElm element : acquired.getElectricalBacking().getCircuitElements())
                check(sim.elmList.contains(element),
                    sourceId + " -> " + targetId +
                        " transferred part backing remains in the active CircuitJS graph");
            check(owner.getComponentBindings().getElements(targetId)
                    .containsAll(acquired.getElectricalBacking().getCircuitElements()),
                sourceId + " -> " + targetId +
                    " target component bindings own the transferred live backing");
        }

        private void removeAndRestore(String sourceId, String targetId,
                PhysicalPart<?> transferred, PhysicalPart<?> original,
                WorkbenchPartsProvider sourceParts,
                WorkbenchPartsProvider targetParts) {
            invokeCapability(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, transferred),
                "remove transferred " + sourceId + " -> " + targetId + " part");
            check(runtime.getInstalledPart(targetId) == null && !transferred.isInstalled() &&
                    transferred.getBoardSlot() == null &&
                    runtime.getWorkbenchPartsProviderForPart(transferred.getId()) ==
                        sourceParts && sourceParts.ownsPart(transferred.getId()) &&
                    !targetParts.ownsPart(transferred.getId()) &&
                    targetParts.ownsPart(original.getId()),
                "removing " + sourceId + " -> " + targetId +
                    " returns an owned loose source identity");
            BoardModificationController saved = sim.boardModificationController;
            BoardModificationController stale = new BoardModificationController(sim, owner);
            sim.boardModificationController = stale;
            try {
                WorkbenchOperation staleOperation = WorkbenchOperation.forPartAtSlot(
                    WorkbenchOperation.INSTALL, transferred, targetId);
                Vector<CircuitElm> graphBefore = new Vector<CircuitElm>(sim.elmList);
                check(!capabilityContext.isAvailable(staleOperation) &&
                        !capabilityContext.dispatch(staleOperation) &&
                        runtime.getSlot(targetId).getInstalledPart() == null &&
                        graphBefore.equals(sim.elmList),
                    "stale native capability owner rejects the loose cross-target candidate before mutation");
            } finally {
                sim.boardModificationController = saved;
            }
            installOriginal(targetId, original, "restore original " + targetId + " part");
            check(runtime.getInstalledPart(targetId) == original && original.isInstalled(),
                "target original part identity is preserved after cross-target cleanup");
        }

        private void restoreAllOriginals() {
            check(runtime.getInstalledPart("KA") == relayAOriginal &&
                    runtime.getInstalledPart("KB") == relayBOriginal &&
                    runtime.getInstalledPart("U2A") == decisionAOriginal &&
                    runtime.getInstalledPart("U2B") == decisionBOriginal &&
                    runtime.getInstalledPart("QA") == qaOriginal &&
                    runtime.getInstalledPart("QB") == qbOriginal,
                "relay, controller and both typed driver originals survive service cleanup");
            slotsRestored = true;
        }

        private void resetAndVerifyOwner() {
            check(sim.boardPowerController.isElectricallyUnpowered(),
                "transfer fixture remains electrically isolated before native reset");
            resetNativeSolverAndModel();
            resetInvoked = true;
            settle("native CircuitJS solver/model reset after transfer cleanup");
            check(runtime.getInstalledPart("KA") == relayAOriginal &&
                    runtime.getInstalledPart("KB") == relayBOriginal &&
                    runtime.getInstalledPart("U2A") == decisionAOriginal &&
                    runtime.getInstalledPart("U2B") == decisionBOriginal &&
                    runtime.getInstalledPart("QA") == qaOriginal &&
                    runtime.getInstalledPart("QB") == qbOriginal &&
                    sim.elmList.containsAll(owner.getSimulationElements()),
                "native reset preserves restored original ownership and active graph membership");
        }

        /**
         * Apply the production reset's solver/model portion in the native
         * fixture. The browser reset also clears InstrumentController targets,
         * starts the UI timer, and repaints; those browser-only effects remain
         * outside this JVM contract.
         */
        private void resetNativeSolverAndModel() {
            check(sim.getGeneratedBoardInstance() == owner,
                "native reset starts with the current generated owner");
            sim.invalidateGeneratedOwnerWork();
            if (sim.generatedChallengeController != null)
                sim.generatedChallengeController.invalidateCustomerRetest();
            sim.analyzeFlag = true;
            sim.t = sim.timeStepAccum = 0;
            sim.solverExecutor.retire();
            sim.timeStepCount = 0;
            for (int i = 0; i != sim.elmList.size(); i++)
                sim.getElm(i).reset();
            for (int i = 0; i != sim.scopeCount; i++)
                sim.scopes[i].resetGraph(true);
            runtime.resetForBoardReset();
            sim.requestGeneratedBoardVerification();
        }

        private void installOriginal(String componentId, PhysicalPart<?> original,
                String label) {
            WorkbenchOperation operation = WorkbenchOperation.forPartAtSlot(
                WorkbenchOperation.INSTALL, original, componentId);
            check(capabilityContext.isAvailable(operation),
                label + " is native capability-available");
            check(capabilityContext.dispatch(operation),
                label + " invokes the real native capability");
            settle(label);
        }

        private void invokeCapability(WorkbenchOperation operation, String label) {
            check(capabilityContext.isAvailable(operation),
                label + " is native capability-available");
            check(capabilityContext.dispatch(operation),
                label + " invokes the real native capability");
            settle(label);
        }

        private void settle(String label) {
            if (sim.generatedBoardVerificationPending || sim.analyzeFlag)
                check(sim.generatedBoardVerificationPending && sim.analyzeFlag,
                    label + " queues the real solver verification boundary");
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            GeneratedRuntimeInvariant.verify(owner, modifications, sim.elmList);
            GeneratedBoardVerifier.verify(owner, sim.boardPowerController.getState(),
                modifications, sim.elmList, false);
            owner.getConnectionBindings().validateAgainst(owner.getBoard(),
                owner.getSimulationElements(), owner.getComponentBindings(),
                owner.getExternalPowerBindings(), owner.getFaultBinding());
            check(!runtime.isMutationInProgress() && !runtime.isMutationQuarantined(),
                label + " leaves the real mutation owner clean");
            // Consume only the generated verification flags after the actual solver and
            // independent graph verifiers have run.  No voltage state is forged.
            sim.generatedBoardVerificationPending = false;
            sim.generatedBoardVerificationAnalyzed = false;
            sim.analyzeFlag = false;
            sim.dcAnalysisFlag = false;
        }

        private void waitForRelayDischarge(String componentId) {
            int steps = 0;
            while (!Rb30RelayService.isDischarged(owner, componentId) && steps < 50) {
                sim.advanceGeneratedTemporalProfile(.100);
                steps++;
            }
            check(Rb30RelayService.isDischarged(owner, componentId),
                componentId + " reaches actual solver discharge before service");
        }

        private HashMap<String, CircuitMeasurementEndpoint> captureBoardEndpoints(
                String componentId) {
            HashMap<String, CircuitMeasurementEndpoint> result =
                new HashMap<String, CircuitMeasurementEndpoint>();
            for (GeneratedComponentConnectionBinding binding :
                    owner.getConnectionBindings().getForComponent(componentId))
                result.put(binding.getPadId(), binding.getBoardEndpoint());
            return result;
        }

        private PhysicalPart<?> findNewLoose(WorkbenchPartsProvider provider,
                Vector<PhysicalPart<?>> before) {
            PhysicalPart<?> found = null;
            for (PhysicalPart<?> part : provider.getLooseParts()) {
                if (before.contains(part)) continue;
                if (found != null)
                    throw new AssertionError("catalog acquisition created more than one loose part");
                found = part;
            }
            return found;
        }

        private PhysicalPart<?> acquireFromCatalog(String componentId,
                PlayerShopCatalog.Entry choice, WorkbenchPartsProvider sourceParts,
                Vector<PhysicalPart<?>> before, String label) {
            PhysicalSlotMutationProvider provider = runtime.getMutationProvider(componentId);
            check(provider instanceof CatalogAcquisitionProvider,
                label + " uses the production catalog acquisition provider");
            check(componentId.equals(choice.acquisitionComponent),
                label + " uses the source component selected by the catalog projection");
            PhysicalPart<?> acquired = ((CatalogAcquisitionProvider) provider)
                .acquireFromCatalog(choice.catalogId);
            check(acquired != null, label + " returns a real loose physical part");
            settle(label);
            return findNewLoose(sourceParts, before);
        }

        private PlayerShopCatalog.Entry findChoice(PlayerShopCatalog.Category category,
                String acquisitionComponent, String catalogSuffix) {
            for (PlayerShopCatalog.Entry entry : category.entries())
                if ((acquisitionComponent == null ||
                        acquisitionComponent.equals(entry.acquisitionComponent)) &&
                        (catalogSuffix == null || entry.catalogId.endsWith(catalogSuffix)))
                    return entry;
            throw new AssertionError("Missing native catalog choice for " +
                acquisitionComponent + "/" + category.id + "/" + catalogSuffix);
        }

        private Rb30RelayService relayService(String componentId) {
            return (Rb30RelayService) runtime.getScopedMutationCapability(componentId);
        }

        private Rb30DecisionService decisionService(String componentId) {
            return (Rb30DecisionService) runtime.getScopedMutationCapability(componentId);
        }

        private String oppositeChannel(String sourceId, String first, String second) {
            if (first.equals(sourceId)) return second;
            if (second.equals(sourceId)) return first;
            throw new AssertionError("Catalog source " + sourceId +
                " is outside the expected Q30 channel pair " + first + "/" + second);
        }

        private PhysicalPart<?> relayOriginal(String componentId) {
            if ("KA".equals(componentId)) return relayAOriginal;
            if ("KB".equals(componentId)) return relayBOriginal;
            throw new AssertionError("Unknown Q30 relay channel " + componentId);
        }

        private PhysicalPart<?> decisionOriginal(String componentId) {
            if ("U2A".equals(componentId)) return decisionAOriginal;
            if ("U2B".equals(componentId)) return decisionBOriginal;
            throw new AssertionError("Unknown Q30 decision channel " + componentId);
        }

        void cleanup() {
            Throwable failure = null;
            try {
                if (owner != null) {
                    try {
                        if (sim.getGeneratedBoardInstance() == owner &&
                                runtime != null) {
                            if (!sim.boardPowerController.isElectricallyUnpowered()) {
                                sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
                                runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
                            }
                            waitForRelayDischarge("KA");
                            waitForRelayDischarge("KB");
                            if (relayTargetId != null)
                                restoreIfNeeded(relayTargetId,
                                    relayOriginal(relayTargetId));
                            if (decisionTargetId != null)
                                restoreIfNeeded(decisionTargetId,
                                    decisionOriginal(decisionTargetId));
                            restoreIfNeeded("QA", qaOriginal);
                            restoreIfNeeded("QB", qbOriginal);
                            if (relaySourceId != null && decisionSourceId != null)
                                check(runtime.getInstalledPart(relaySourceId) ==
                                        relayOriginal(relaySourceId) &&
                                        runtime.getInstalledPart(decisionSourceId) ==
                                        decisionOriginal(decisionSourceId),
                                    "cleanup retains dynamically selected source original owners");
                            slotsRestored = relaySourceId != null && relayTargetId != null &&
                                decisionSourceId != null && decisionTargetId != null &&
                                runtime.getInstalledPart("KA") == relayAOriginal &&
                                runtime.getInstalledPart("KB") == relayBOriginal &&
                                runtime.getInstalledPart("U2A") == decisionAOriginal &&
                                runtime.getInstalledPart("U2B") == decisionBOriginal &&
                                runtime.getInstalledPart("QA") == qaOriginal &&
                                runtime.getInstalledPart("QB") == qbOriginal;
                        }
                    } catch (Throwable problem) { failure = problem; }
                    try {
                        if (foreignDecision != null) {
                            foreignDecision.delete();
                            foreignDecision = null;
                        }
                    } catch (Throwable problem) { failure = append(failure, problem); }
                    try {
                        owner.getExternalPowerBindings().setConnected(false);
                        powerDisconnected = owner.getExternalPowerBindings().areAllDisconnected();
                    } catch (Throwable problem) { failure = append(failure, problem); }
                    try {
                        for (CircuitElm element : owner.getSimulationElements()) element.delete();
                        sim.elmList.removeAllElements();
                        ownerDeleted = true;
                        graphEmpty = sim.elmList.isEmpty();
                    } catch (Throwable problem) { failure = append(failure, problem); }
                    try { sim.boardPowerController.detach(); }
                    catch (Throwable problem) { failure = append(failure, problem); }
                    try {
                        if (runtime != null) {
                            runtime.clearMutationProviders();
                            capabilityRegistryCleared =
                                runtime.getWorkbenchCapabilityRegistry()
                                    .getRuntimeCapabilities().isEmpty() &&
                                runtime.getMutationProvider("KA") == null &&
                                runtime.getMutationProvider("KB") == null &&
                                runtime.getMutationProvider("U2A") == null &&
                                runtime.getMutationProvider("U2B") == null &&
                                runtime.getMutationProvider("QA") == null &&
                                runtime.getMutationProvider("QB") == null;
                        } else capabilityRegistryCleared = true;
                    } catch (Throwable problem) { failure = append(failure, problem); }
                }
            } finally {
                sim.generatedBoardInstance = previousOwner;
                sim.boardModificationController = previousModifications;
                sim.generatedChallengeController = previousChallenge;
                if (previousGraph != null) sim.elmList = previousGraph;
                CircuitElm.sim = previousSimulator;
            }
            if (failure != null) rethrow(failure);
        }

        /**
         * Minimal native context used by production capability strategies.
         * This mirrors the controller's registry lookup and strategy execution
         * without constructing browser widgets or claiming UI coverage.
         */
        private final class NativeCapabilityContext
                implements WorkbenchCapabilityContext {
            private WorkbenchCapabilityStrategy find(WorkbenchOperation operation) {
                return WorkbenchCapabilityDiscovery.find(
                    operation == null ? null : operation.getPart(), operation,
                    runtime == null ? null : runtime.getWorkbenchCapabilityRegistry());
            }

            public boolean isAvailable(WorkbenchOperation operation) {
                WorkbenchCapabilityStrategy capability = find(operation);
                return capability != null &&
                    capability.isAvailable(operation, this);
            }

            public boolean dispatch(WorkbenchOperation operation) {
                WorkbenchCapabilityStrategy capability = find(operation);
                return capability != null &&
                    capability.isAvailable(operation, this) &&
                    capability.invoke(operation, this);
            }
        }

        private void restoreIfNeeded(String componentId, PhysicalPart<?> original) {
            if (componentId == null || original == null ||
                    runtime.getInstalledPart(componentId) == original) return;
            PhysicalPart<?> installed = runtime.getInstalledPart(componentId);
            if (installed != null) {
                PhysicalSlotMutationProvider provider = runtime.getMutationProvider(componentId);
                if (!provider.removeInstalledPart())
                    throw new IllegalStateException("cleanup could not remove " + componentId);
                settle("cleanup remove " + componentId);
            }
            installOriginal(componentId, original, "cleanup restore " + componentId);
        }

        private static Throwable append(Throwable primary, Throwable secondary) {
            if (primary == null) return secondary;
            if (secondary != primary) primary.addSuppressed(secondary);
            return primary;
        }
    }

    /** Native fixture keeps production needAnalyze semantics without repaint/UI callbacks. */
    private static final class NativeCatalogCirSim extends CirSim {
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }

        @Override void refreshBoardModificationControls() { }
        @Override void repaint() { }
    }
}
