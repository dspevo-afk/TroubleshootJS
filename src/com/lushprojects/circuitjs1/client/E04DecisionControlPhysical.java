package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Visible physical owner for E04's five-terminal solver-backed decision circuitry. */
final class E04DecisionControlPart extends FixedPhysicalPart<PhysicalSpecification> {
    private final E04SensorControlModel.DecisionElement element;

    E04DecisionControlPart(String id, PhysicalSpecification specification,
            PhysicalNameplate nameplate, E04SensorControlModel.DecisionElement element,
            PhysicalPartProvenance provenance) {
        super(id, requireSpecification(specification),
            requireNameplate(nameplate).forPhysicalPartId(id),
            PhysicalPackages.E04_DECISION_CONTROL_5,
            terminals(id, requireElement(element)), backing(element), provenance,
            PhysicalPartRenderProbeProviders.SERVICE, capabilities());
        this.element = element;
    }

    E04SensorControlModel.DecisionElement getElement() { return element; }

    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        if (padId == null)
            throw new IllegalArgumentException("Missing E04 decision board pad");
        for (PhysicalPartTerminal terminal : getTerminals())
            if (padId.endsWith("." + terminal.getTerminalName()))
                return terminal.getEndpoint();
        throw new IllegalArgumentException("Unknown E04 decision board pad: " + padId);
    }

    private static PhysicalSpecification requireSpecification(
            PhysicalSpecification value) {
        if (value == null)
            throw new IllegalArgumentException(
                "Missing E04 decision physical specification");
        return value;
    }

    private static PhysicalNameplate requireNameplate(PhysicalNameplate value) {
        if (value == null)
            throw new IllegalArgumentException("Missing E04 decision physical nameplate");
        return value;
    }

    private static E04SensorControlModel.DecisionElement requireElement(
            E04SensorControlModel.DecisionElement value) {
        if (value == null || value.getPostCount() != 5)
            throw new IllegalArgumentException(
                "E04 decision part requires five live posts");
        return value;
    }

    private static Vector<PhysicalPartTerminal> terminals(String id,
            E04SensorControlModel.DecisionElement element) {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        Vector<String> names =
            PhysicalPackages.E04_DECISION_CONTROL_5.getTerminalIds();
        for (int index = 0; index < names.size(); index++)
            result.add(new PhysicalPartTerminal(id, names.get(index),
                new CircuitPostMeasurementEndpoint(element, index)));
        return result;
    }

    private static Vector<CircuitElm> backing(
            E04SensorControlModel.DecisionElement element) {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(element);
        return result;
    }

    private static Vector<PhysicalPartCapability> capabilities() {
        Vector<PhysicalPartCapability> result =
            new Vector<PhysicalPartCapability>();
        result.add(new LoosePartInspectableCapability());
        return result;
    }
}

/** U2 slot owns all five detachable board-to-package attachment wires. */
final class E04DecisionControlSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final PhysicalSpecification intendedSpecification;
    private final WireElm[] attachments;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalMutationSlot.AttachmentState emptySlotAttachmentState;

    E04DecisionControlSlot(String componentId,
            PhysicalSpecification intendedSpecification,
            E04DecisionControlPart installedPart, WireElm[] attachments,
            PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 ||
                intendedSpecification == null || installedPart == null ||
                attachments == null || attachments.length != 5 ||
                physicalSlot == null)
            throw new IllegalArgumentException("Invalid E04 decision component slot");
        this.componentId = componentId;
        this.intendedSpecification = intendedSpecification;
        this.attachments = new WireElm[attachments.length];
        for (int index = 0; index < attachments.length; index++) {
            if (attachments[index] == null)
                throw new IllegalArgumentException("Missing E04 decision attachment");
            this.attachments[index] = attachments[index];
        }
        this.physicalSlot = physicalSlot;
        attach(installedPart);
        physicalSlot.install(installedPart);
        emptySlotAttachmentState = captureAttachmentState();
    }

    public String getComponentId() { return componentId; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public E04DecisionControlPart getInstalledPart() {
        return (E04DecisionControlPart) physicalSlot.getInstalledPart();
    }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }

    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof E04DecisionControlPart &&
            intendedSpecification.getSpecificationId().equals(
                part.getSpecification().getSpecificationId()) &&
            physicalSlot.getPhysicalPackage().isEquivalentTo(part.getPackage());
    }

    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part,
            BoardPad pad) {
        if (!acceptsPart(part) || pad == null ||
                !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign E04 decision terminal mapping");
        return ((E04DecisionControlPart) part).getTerminalForBoardPad(pad.getId());
    }

    public void installForMutation(PhysicalPart<?> candidate,
            PhysicalMutationScope scope) {
        if (!acceptsPart(candidate) || scope == null || !scope.owns(this))
            throw new IllegalArgumentException("Invalid E04 decision mutation install");
        attach((E04DecisionControlPart) candidate);
        scope.afterAttachmentWrite();
        physicalSlot.install(candidate);
        scope.afterSlotMountWrite();
    }

    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Foreign E04 decision removal");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new DecisionAttachmentState(attachments);
    }

    public void restoreAttachmentState(
            PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof DecisionAttachmentState))
            throw new IllegalArgumentException("Missing E04 decision attachment state");
        DecisionAttachmentState state = (DecisionAttachmentState) captured;
        for (int index = 0; index < attachments.length; index++) {
            attachments[index].x = state.coordinates[index * 4];
            attachments[index].y = state.coordinates[index * 4 + 1];
            attachments[index].x2 = state.coordinates[index * 4 + 2];
            attachments[index].y2 = state.coordinates[index * 4 + 3];
            attachments[index].setPoints();
        }
    }

    public void restoreEmptySlotAttachmentState(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Foreign E04 decision attachment restore");
        restoreAttachmentState(emptySlotAttachmentState);
    }

    private void attach(E04DecisionControlPart part) {
        for (int index = 0; index < attachments.length; index++) {
            CircuitMeasurementEndpoint endpoint = part.getTerminal(index).getEndpoint();
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Unsupported E04 decision endpoint");
            CircuitPostMeasurementEndpoint post =
                (CircuitPostMeasurementEndpoint) endpoint;
            Point point = post.getElement().getPost(post.getPostIndex());
            attachments[index].x2 = point.x;
            attachments[index].y2 = point.y;
            attachments[index].setPoints();
        }
    }

    private static final class DecisionAttachmentState
            implements PhysicalMutationSlot.AttachmentState {
        private final int[] coordinates = new int[20];

        DecisionAttachmentState(WireElm[] attachments) {
            for (int index = 0; index < attachments.length; index++) {
                coordinates[index * 4] = attachments[index].x;
                coordinates[index * 4 + 1] = attachments[index].y;
                coordinates[index * 4 + 2] = attachments[index].x2;
                coordinates[index * 4 + 3] = attachments[index].y2;
            }
        }
    }
}

/** Catalog, inventory and installation owner for the visible E04 U2 package. */
final class E04DecisionControlBoardCapability
        implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    static final String ID = "E04_DECISION_CONTROL_SERVICE";
    static final String CATALOG_ID = "E04_DECISION_CONTROL_STOCK";

    private final E04DecisionControlSlot slot;
    private final PhysicalPartInventory<E04DecisionControlPart> inventory;
    private final E04DecisionControlPart original;
    private final E04SensorControlModel model;
    private E04DecisionControlSlotController controller;

    E04DecisionControlBoardCapability(E04DecisionControlSlot slot,
            PhysicalPartInventory<E04DecisionControlPart> inventory,
            E04DecisionControlPart original, E04SensorControlModel model) {
        if (slot == null || inventory == null || original == null || model == null)
            throw new IllegalArgumentException("Missing E04 decision service owner");
        this.slot = slot;
        this.inventory = inventory;
        this.original = original;
        this.model = model;
    }

    public String getCapabilityId() { return ID; }
    public String getComponentId() { return slot.getComponentId(); }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public PhysicalPartInventory<?> getMutationInventory() { return inventory; }
    E04DecisionControlSlot getSlot() { return slot; }
    PhysicalPartInventory<E04DecisionControlPart> getInventory() { return inventory; }
    E04DecisionControlPart getOriginal() { return original; }
    E04SensorControlModel getModel() { return model; }

    public PhysicalSlotMutationProvider install(CirSim sim,
            GeneratedBoardInstance instance,
            BoardModificationController modifications,
            double initialSimulationTime) {
        controller = new E04DecisionControlSlotController(sim, instance,
            modifications, this);
        return controller;
    }

    E04DecisionControlSlotController getController() { return controller; }
    public String getCatalogTitle() { return "Sensor Controller Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new sensor controller"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result =
            new Vector<WorkbenchCatalogEntry>();
        result.add(new WorkbenchCatalogEntry(CATALOG_ID,
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

/** Bounded graph/identity mutation controller for the visible E04 U2. */
final class E04DecisionControlSlotController
        implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
    private static final int START_X = 32000;
    private static final int START_Y = 24000;
    private static final int STEP = 160;

    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final E04DecisionControlBoardCapability capability;

    E04DecisionControlSlotController(CirSim sim,
            GeneratedBoardInstance instance,
            BoardModificationController modifications,
            E04DecisionControlBoardCapability capability) {
        if (sim == null || instance == null || modifications == null ||
                capability == null)
            throw new IllegalArgumentException("Missing E04 decision controller context");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("E04_DECISION_" + getComponentId(),
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
            throw new IllegalArgumentException("Unknown E04 decision catalog entry");
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
        final E04SensorControlModel model = capability.getModel();
        final E04SensorControlModel.DecisionElement previous =
            model.getActiveDecisionElement();
        if (previous == replacement) return;
        scope.setProviderCompensation(
            new PhysicalMutationScope.ProviderCompensation() {
                public void compensate() {
                    model.setActiveDecisionElement(previous);
                }
            });
        model.setActiveDecisionElement(replacement);
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
                return capability.getModel().createReplacementDecisionElement(x, y);
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
                "Sensor controller replacement requires electrically unpowered board");
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
        return E04DecisionControlBoardCapability.CATALOG_ID.equals(catalogEntryId);
    }
}
