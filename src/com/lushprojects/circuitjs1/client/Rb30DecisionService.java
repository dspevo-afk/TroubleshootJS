package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** One Q30 channel owns its own visible U2 package and solver decision. */
final class Rb30DecisionService
        implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    static final String ID_PREFIX = "RB30_DECISION_SERVICE_";
    static final String CATALOG_PREFIX = "RB30_DECISION_STOCK_";

    private final E04DecisionControlSlot slot;
    private final PhysicalPartInventory<E04DecisionControlPart> inventory;
    private final E04DecisionControlPart original;
    private final E04SensorControlModel.RailContract rail;
    private final E04SensorControlModel.Variant variant;
    private final E04SensorControlModel.Configuration configuration;
    private E04SensorControlModel.DecisionElement activeDecision;
    private Rb30DecisionController controller;

    Rb30DecisionService(E04DecisionControlSlot slot,
            PhysicalPartInventory<E04DecisionControlPart> inventory,
            E04DecisionControlPart original,
            E04SensorControlModel.RailContract rail,
            E04SensorControlModel.Variant variant,
            E04SensorControlModel.Configuration configuration) {
        if (slot == null || inventory == null || original == null ||
                rail == null || variant == null || configuration == null ||
                inventory.getRuntime() != slot.getPhysicalSlot().getRuntime() ||
                slot.getInstalledPart() != original ||
                original.getBoardSlot() != slot.getPhysicalSlot() ||
                !inventory.contains(original.getId()) ||
                inventory.get(original.getId()) != original)
            throw new IllegalArgumentException("Missing Q30 decision service owner");
        this.slot = slot;
        this.inventory = inventory;
        this.original = original;
        this.rail = rail;
        this.variant = variant;
        this.configuration = configuration;
        if (!original.getElement().hasSameDeclaration(
                createReplacementDecisionElement(0, 0)))
            throw new IllegalArgumentException("Q30 original decision declaration differs");
        this.activeDecision = original.getElement();
    }

    public String getCapabilityId() { return ID_PREFIX + getComponentId(); }
    String getCatalogId() { return CATALOG_PREFIX + getComponentId(); }
    public String getComponentId() { return slot.getComponentId(); }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public PhysicalPartInventory<?> getMutationInventory() { return inventory; }
    E04DecisionControlSlot getSlot() { return slot; }
    PhysicalPartInventory<E04DecisionControlPart> getInventory() { return inventory; }
    E04DecisionControlPart getOriginal() { return original; }
    E04SensorControlModel.DecisionElement getActiveDecisionElement() {
        return activeDecision;
    }
    void setActiveDecisionElement(
            E04SensorControlModel.DecisionElement element) {
        if (!original.getElement().hasSameDeclaration(element))
            throw new IllegalArgumentException("Incompatible Q30 decision replacement");
        activeDecision = element;
    }
    E04SensorControlModel.DecisionElement createReplacementDecisionElement(
            int x, int y) {
        return new E04SensorControlModel.DecisionElement(x, y, rail, variant,
            configuration);
    }

    public PhysicalSlotMutationProvider install(CirSim sim,
            GeneratedBoardInstance instance,
            BoardModificationController modifications,
            double initialSimulationTime) {
        controller = new Rb30DecisionController(sim, instance,
            modifications, this);
        return controller;
    }

    Rb30DecisionController getController() { return controller; }
    public String getCatalogTitle() { return "Sensor controller replacement catalog"; }
    public String getInstallNewLabel() { return "Install new sensor controller"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result =
            new Vector<WorkbenchCatalogEntry>();
        result.add(new WorkbenchCatalogEntry(getCatalogId(),
            "Compatible five-terminal sensor threshold controller"));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        result.addAll(inventory.getLooseParts());
        return result;
    }

    public String getPartLabel(PhysicalPart<?> part) {
        if (!(part instanceof E04DecisionControlPart) ||
                inventory.get(part.getId()) != part)
            throw new IllegalArgumentException("Foreign E04 decision inventory part");
        return part.getPlayerVisibleNameplate().getDisplayName();
    }

    public PhysicalPart<?> getPart(String partId) { return inventory.get(partId); }
    public boolean ownsPart(String partId) { return inventory.contains(partId); }
}

/** Bounded graph/identity mutation controller for one Q30 decision slot. */
final class Rb30DecisionController
        implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
    private static final int START_X = 32000;
    private static final int START_Y = 24000;
    private static final int STEP = 160;

    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final Rb30DecisionService capability;

    Rb30DecisionController(CirSim sim,
            GeneratedBoardInstance instance,
            BoardModificationController modifications,
            Rb30DecisionService capability) {
        if (sim == null || instance == null || modifications == null ||
                capability == null)
            throw new IllegalArgumentException("Missing Q30 decision controller context");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("RB30_DECISION_" + getComponentId(),
            "Sensor controller workbench", "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify sensor controller";
        if (WorkbenchOperation.INSTALL.equals(operation.getId()))
            return "Install as " + getComponentId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId()))
            return capability.getInstallNewLabel();
        if (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ||
                WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId())) {
            BoardPad pad = instance.getBoard().getPad(operation.getPadId());
            return (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ?
                "Lift lead " : "Reconnect lead ") +
                (pad == null ? operation.getPadId() : pad.getTerminalId());
        }
        if (WorkbenchOperation.RESTORE.equals(operation.getId()))
            return "Restore component";
        return "Remove sensor controller";
    }

    public boolean supports(WorkbenchOperation operation) {
        if (operation == null ||
                !getComponentId().equals(operation.getComponentId()))
            return false;
        String id = operation.getId();
        return WorkbenchOperation.INSTALL.equals(id) ||
            WorkbenchOperation.REMOVE.equals(id) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(id) ||
            WorkbenchOperation.LIFT_LEAD.equals(id) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(id) ||
            WorkbenchOperation.RESTORE.equals(id);
    }

    public boolean isAvailable(WorkbenchOperation operation,
            WorkbenchCapabilityContext context) {
        if (!supports(operation) || !isSafeMutationAvailable()) return false;
        E04DecisionControlSlot slot = capability.getSlot();
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return slot.isEmpty() && hasCatalogEntry(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return operation.getPart() instanceof E04DecisionControlPart &&
                capability.ownsPart(operation.getPart().getId()) &&
                instance.getPhysicalBoardRuntime().isPartInstallableAt(
                    operation.getPart(), getComponentId());
        if (WorkbenchOperation.REMOVE.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation);
        if (WorkbenchOperation.LIFT_LEAD.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState(getComponentId()) ==
                    ComponentPhysicalState.INSTALLED &&
                hasConnectedPad(operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState(getComponentId()) ==
                    ComponentPhysicalState.LEAD_LIFTED &&
                hasDisconnectedPad(operation.getPadId());
        if (WorkbenchOperation.RESTORE.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState(getComponentId()) !=
                    ComponentPhysicalState.INSTALLED;
        return false;
    }

    public boolean invoke(WorkbenchOperation operation,
            WorkbenchCapabilityContext context) {
        if (!isAvailable(operation, context)) return false;
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return installNewFromCatalog(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return install(operation.getPart().getId());
        if (WorkbenchOperation.REMOVE.equals(id))
            return removeInstalledPart();
        if (WorkbenchOperation.LIFT_LEAD.equals(id))
            return modifications.liftLead(getComponentId(), operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(id))
            return modifications.reconnectLead(getComponentId(), operation.getPadId());
        return modifications.restoreComponent(getComponentId());
    }

    public String getComponentId() { return capability.getSlot().getComponentId(); }
    public boolean ownsPart(String partId) { return capability.ownsPart(partId); }
    public PhysicalMutationSlot getMutationSlot() { return capability.getSlot(); }

    public boolean removeInstalledPart() {
        requireSafeMutation();
        if (capability.getSlot().isEmpty()) return false;
        PhysicalMutationScope scope = newScope("remove", null, null);
        try {
            selectActiveDecision(capability.getOriginal().getElement(), scope);
            modifications.disconnectComponentForMutation(scope, getComponentId());
            scope.clearPart();
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutation();
        return true;
    }

    public boolean install(String partId) {
        requireSafeMutation();
        if (!capability.getSlot().isEmpty()) return false;
        PhysicalPart<?> candidate = instance.getPhysicalBoardRuntime().getPart(partId);
        if (!(candidate instanceof E04DecisionControlPart) ||
                !capability.ownsPart(partId) ||
                !instance.getPhysicalBoardRuntime().isPartInstallableAt(
                    candidate, getComponentId()))
            return false;
        E04DecisionControlPart part = (E04DecisionControlPart) candidate;
        PhysicalMutationScope scope = newScope("install", part, null);
        try {
            selectActiveDecision(part.getElement(), scope);
            installPart(part, scope);
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutation();
        return true;
    }

    public boolean installNewFromCatalog(String catalogEntryId) {
        return catalogPart(catalogEntryId, true) != null;
    }

    public PhysicalPart<?> acquireFromCatalog(String catalogEntryId) {
        return catalogPart(catalogEntryId, false);
    }

    private E04DecisionControlPart catalogPart(String catalogEntryId,
            boolean install) {
        requireSafeMutation();
        if (!hasCatalogEntry(catalogEntryId))
            throw new IllegalArgumentException("Unknown Q30 decision catalog entry");
        final E04DecisionControlSlot slot = capability.getSlot();
        if (install && !slot.isEmpty()) return null;
        final E04SensorControlModel.DecisionElement element = createBacking();
        final E04DecisionControlPart original = capability.getOriginal();
        PhysicalMutationScope scope = newScope(install ? "catalog" : "acquire",
            null, catalogEntryId);
        E04DecisionControlPart part;
        try {
            part = scope.acquire(capability.getInventory(),
                getComponentId() + "_CATALOG_PART",
                new PhysicalPartIdentityFactory<E04DecisionControlPart>() {
                    public E04DecisionControlPart create(String partId) {
                        E04DecisionControlPart created =
                            new E04DecisionControlPart(partId,
                                original.getSpecification(),
                                original.getPlayerVisibleNameplate(), element,
                                new PhysicalPartProvenance(
                                    PhysicalPartProvenance.CATALOG_ACQUIRED,
                                    partId));
                        slot.getPhysicalSlot().bindGeometryForAcquisition(created);
                        return created;
                    }
                });
            scope.registerCanonicalElement(element);
            scope.appendActiveElement(element);
            if (install) {
                selectActiveDecision(element, scope);
                installPart(part, scope);
            }
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return null;
        }
        scope.closeAfterCommit();
        finishMutation();
        return part;
    }

    private void installPart(E04DecisionControlPart part,
            PhysicalMutationScope scope) {
        scope.replacePrimaryBinding(part.getElement());
        for (GeneratedComponentConnectionBinding binding :
                instance.getConnectionBindings().getForComponent(getComponentId()))
            scope.retargetEndpoint(binding,
                part.getTerminalForBoardPad(binding.getPadId()));
        scope.installPart(part);
        scope.restoreComponentGraph();
    }

    private void selectActiveDecision(
            final E04SensorControlModel.DecisionElement replacement,
            PhysicalMutationScope scope) {
        final E04SensorControlModel.DecisionElement previous =
            capability.getActiveDecisionElement();
        if (previous == replacement) return;
        scope.setProviderCompensation(
            new PhysicalMutationScope.ProviderCompensation() {
                public void compensate() {
                    capability.setActiveDecisionElement(previous);
                }
            });
        capability.setActiveDecisionElement(replacement);
    }

    private E04SensorControlModel.DecisionElement createBacking() {
        Vector<CircuitElm> occupied = instance.getSimulationElements();
        for (int index = 0; ; index++) {
            int x = START_X + (index % 24) * STEP;
            int y = START_Y + (index / 24) * STEP;
            if (!occupied(occupied, x, y - 32) &&
                    !occupied(occupied, x, y - 16) &&
                    !occupied(occupied, x, y) &&
                    !occupied(occupied, x + 96, y) &&
                    !occupied(occupied, x, y + 32))
                return capability.createReplacementDecisionElement(x, y);
        }
    }

    private boolean occupied(Vector<CircuitElm> elements, int x, int y) {
        for (CircuitElm element : elements)
            for (int post = 0; post < element.getPostCount(); post++) {
                Point point = element.getPost(post);
                if (point != null && point.x == x && point.y == y) return true;
            }
        return false;
    }

    private void requireSafeMutation() {
        if (!isSafeMutationAvailable())
            throw new BoardModificationRejectedException(
                "Q30 sensor controller replacement requires electrically unpowered board");
    }

    private boolean isSafeMutationAvailable() {
        return sim.getGeneratedBoardInstance() == instance &&
            sim.getBoardModificationController() == modifications &&
            !sim.activeMeasurementOverlay && sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            !instance.getPhysicalBoardRuntime().isMutationInProgress() &&
            !instance.getPhysicalBoardRuntime().isMutationOwnerQuarantined(
                getComponentId());
    }

    private void finishMutation() {
        try {
            if (sim.getGeneratedChallengeController() != null)
                sim.getGeneratedChallengeController().invalidateCustomerRetest();
            sim.needAnalyze();
            sim.requestGeneratedBoardVerification();
            sim.refreshBoardModificationControls();
        } catch (Throwable failure) {
            sim.markGeneratedRuntimeFailure(instance, failure);
            PhysicalMutationScope.rethrow(failure);
        }
    }

    private PhysicalMutationScope newScope(String operation,
            PhysicalPart<?> requestedPart, String catalogEntryId) {
        PhysicalMutationIntent intent = PhysicalMutationIntent.prepare(
            instance.getPhysicalBoardRuntime(), instance, modifications,
            capability.getSlot(), operation, null, catalogEntryId, requestedPart);
        return new PhysicalMutationScope(sim, instance, modifications, intent);
    }

    private boolean matchesInstalledPart(WorkbenchOperation operation) {
        return operation.getPart() == null || operation.getPart() ==
            capability.getSlot().getInstalledPart();
    }

    private boolean hasConnectedPad(String padId) {
        return hasPad(padId) &&
            modifications.isLeadConnected(getComponentId(), padId);
    }

    private boolean hasDisconnectedPad(String padId) {
        return hasPad(padId) &&
            !modifications.isLeadConnected(getComponentId(), padId);
    }

    private boolean hasPad(String padId) {
        BoardPad pad = padId == null ? null : instance.getBoard().getPad(padId);
        return pad != null && getComponentId().equals(pad.getComponentId());
    }

    private boolean hasCatalogEntry(String catalogEntryId) {
        return capability.getCatalogId().equals(catalogEntryId);
    }
}
