package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fault-blind connector/fuse operations through the existing mutation transaction. */
final class ServiceSlotController implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final ReplaceableServiceBoardCapability capability;
    private final ServiceComponentSlot slot;

    ServiceSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, ReplaceableServiceBoardCapability capability) {
        this.sim = sim; this.instance = instance; this.modifications = modifications;
        this.capability = capability; slot = (ServiceComponentSlot)capability.getMutationSlot();
    }
    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("SERVICE_" + getComponentId(), "Physical service", "SLOT_OPERATIONS");
    }
    public String getComponentId() { return slot.getComponentId(); }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public boolean ownsPart(String id) { return capability.ownsPart(id); }
    public String getOperationLabel(WorkbenchOperation operation) {
        String id = operation.getId();
        if (WorkbenchOperation.INSTALL.equals(id)) return "Install as " + getComponentId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id)) return capability.getInstallNewLabel();
        if (WorkbenchOperation.RESTORE.equals(id)) return "Restore component";
        if (WorkbenchOperation.LIFT_LEAD.equals(id) || WorkbenchOperation.RECONNECT_LEAD.equals(id))
            return (WorkbenchOperation.LIFT_LEAD.equals(id) ? "Lift lead " : "Reconnect lead ") +
                instance.getBoard().getPad(operation.getPadId()).getTerminalId();
        return "Remove component";
    }
    public boolean supports(WorkbenchOperation op) {
        if (op == null || !getComponentId().equals(op.getComponentId())) return false;
        String id = op.getId();
        return WorkbenchOperation.REMOVE.equals(id) || WorkbenchOperation.INSTALL.equals(id) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(id) || WorkbenchOperation.LIFT_LEAD.equals(id) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(id) || WorkbenchOperation.RESTORE.equals(id);
    }
    public boolean isAvailable(WorkbenchOperation op, WorkbenchCapabilityContext context) {
        if (!supports(op) || !safe()) return false;
        String id = op.getId();
        if (WorkbenchOperation.INSTALL.equals(id))
            return instance.getPhysicalBoardRuntime().isPartInstallableAt(op.getPart(), getComponentId());
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return slot.isEmpty() && capability.catalogId().equals(op.getCatalogEntryId());
        if (slot.isEmpty() || (op.getPart() != null && op.getPart() != slot.getInstalledPart())) return false;
        if (WorkbenchOperation.REMOVE.equals(id)) return true;
        ComponentPhysicalState state = modifications.getComponentState(getComponentId());
        if (WorkbenchOperation.RESTORE.equals(id)) return state != ComponentPhysicalState.INSTALLED;
        BoardPad pad = op.getPadId() == null ? null : instance.getBoard().getPad(op.getPadId());
        if (pad == null || !getComponentId().equals(pad.getComponentId())) return false;
        boolean connected = modifications.isLeadConnected(getComponentId(), pad.getId());
        return WorkbenchOperation.LIFT_LEAD.equals(id) ?
            state == ComponentPhysicalState.INSTALLED && connected :
            state == ComponentPhysicalState.LEAD_LIFTED && !connected;
    }
    public boolean invoke(WorkbenchOperation op, WorkbenchCapabilityContext context) {
        if (!isAvailable(op, context)) return false;
        String id = op.getId();
        if (WorkbenchOperation.REMOVE.equals(id)) return removeInstalledPart();
        if (WorkbenchOperation.INSTALL.equals(id)) return install(op.getPart().getId());
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id)) return catalog(op.getCatalogEntryId(), true) != null;
        if (WorkbenchOperation.LIFT_LEAD.equals(id)) return modifications.liftLead(getComponentId(), op.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(id)) return modifications.reconnectLead(getComponentId(), op.getPadId());
        return modifications.restoreComponent(getComponentId());
    }
    public boolean removeInstalledPart() {
        requireSafe(); if (slot.isEmpty()) return false;
        PhysicalMutationScope scope = scope("remove", null, null);
        try {
            modifications.disconnectComponentForMutation(scope, getComponentId());
            scope.clearPart(); scope.commit();
        } catch (Throwable failure) { scope.abort(failure); PhysicalMutationScope.rethrow(failure); return false; }
        scope.closeAfterCommit(); finish(); return true;
    }
    public boolean install(String partId) {
        requireSafe(); PhysicalPart<?> candidate = instance.getPhysicalBoardRuntime().getPart(partId);
        if (!instance.getPhysicalBoardRuntime().isPartInstallableAt(candidate, getComponentId())) return false;
        PhysicalMutationScope scope = scope("install", candidate, null);
        try { install((PhysicalServicePart)candidate, scope); scope.commit(); }
        catch (Throwable failure) { scope.abort(failure); PhysicalMutationScope.rethrow(failure); return false; }
        scope.closeAfterCommit(); finish(); return true;
    }
    private void install(PhysicalServicePart part, PhysicalMutationScope scope) {
        scope.replacePrimaryBinding(part.primary());
        if (part.secondary() != null) scope.replaceAuxiliaryBinding(part.secondary());
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getForComponent(getComponentId()))
            scope.retargetEndpoint(binding, slot.getExpectedEndpoint(part, instance.getBoard().getPad(binding.getPadId())));
        scope.installPart(part); scope.restoreComponentGraph();
    }
    public PhysicalPart<?> acquireFromCatalog(String id) { return catalog(id, false); }
    public boolean installNewFromCatalog(String id) { return catalog(id, true) != null; }
    private PhysicalServicePart catalog(String id, boolean install) {
        requireSafe();
        if (!capability.catalogId().equals(id)) throw new IllegalArgumentException("Unknown service catalog entry");
        if (install && !slot.isEmpty()) return null;
        final PhysicalServicePart original = capability.original();
        final Vector<CircuitElm> backing = ServiceableBoardConstruction.newServiceBacking(
            original.getPackage(), original.primary(), instance.getSimulationElements());
        PhysicalMutationScope scope = scope(install ? "catalog" : "acquire", null, id);
        PhysicalServicePart part;
        try {
            part = scope.acquire(capability.inventory(), getComponentId() + "_CATALOG_PART",
                new PhysicalPartIdentityFactory<PhysicalServicePart>() {
                    public PhysicalServicePart create(String partId) {
                        PhysicalServicePart part = new PhysicalServicePart(partId, original.getSpecification(),
                            new PhysicalNameplate(partId, capability.catalogLabel()), original.getPackage(), backing,
                            new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED, partId));
                        slot.getPhysicalSlot().bindGeometryForAcquisition(part); return part;
                    }
                });
            for (CircuitElm element : backing) { scope.registerCanonicalElement(element); scope.appendActiveElement(element); }
            if (install) install(part, scope);
            scope.commit();
        } catch (Throwable failure) { scope.abort(failure); PhysicalMutationScope.rethrow(failure); return null; }
        scope.closeAfterCommit(); finish(); return part;
    }
    private boolean safe() {
        return sim.getGeneratedBoardInstance() == instance && sim.getBoardModificationController() == modifications &&
            !sim.activeMeasurementOverlay && sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            !instance.getPhysicalBoardRuntime().isMutationInProgress() &&
            !instance.getPhysicalBoardRuntime().isMutationOwnerQuarantined(getComponentId());
    }
    private void requireSafe() {
        if (!safe()) throw new BoardModificationRejectedException("Physical service requires electrically unpowered board");
    }
    private PhysicalMutationScope scope(String operation, PhysicalPart<?> part, String catalog) {
        return new PhysicalMutationScope(sim, instance, modifications, PhysicalMutationIntent.prepare(
            instance.getPhysicalBoardRuntime(), instance, modifications, slot, operation, null, catalog, part));
    }
    private void finish() {
        try {
            if (sim.getGeneratedChallengeController() != null) sim.getGeneratedChallengeController().invalidateCustomerRetest();
            sim.needAnalyze(); sim.requestGeneratedBoardVerification(); sim.refreshBoardModificationControls();
        } catch (Throwable failure) { sim.markGeneratedRuntimeFailure(instance, failure); PhysicalMutationScope.rethrow(failure); }
    }
}
