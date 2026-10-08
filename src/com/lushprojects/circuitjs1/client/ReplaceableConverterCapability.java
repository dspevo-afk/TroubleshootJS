package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Seven-terminal module adapter over the existing scoped physical transaction. */
final class ReplaceableConverterCapability implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    static final String ID = "REPLACEABLE_CONVERTER";
    private final ConverterSlot slot;
    private final PhysicalPartInventory<PhysicalConverterPart> inventory;

    ReplaceableConverterCapability(PhysicalBoardSlot physical, PhysicalConverterPart original, WireElm[] attachments) {
        slot = new ConverterSlot(physical, original, attachments);
        inventory = new PhysicalPartInventory<PhysicalConverterPart>(physical.getRuntime(),
            physical.getComponentId() + "_CONVERTERS", PhysicalConverterPart.class);
        inventory.add(original);
    }
    public String getCapabilityId() { return ID + "_" + getComponentId(); }
    public String getComponentId() { return slot.getComponentId(); }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public PhysicalPartInventory<?> getMutationInventory() { return inventory; }
    public String getCatalogTitle() { return "Converter Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new converter"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }
    private String catalogId() { return getComponentId() + "_ISOLATED_CONVERTER"; }
    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> out = new Vector<WorkbenchCatalogEntry>();
        out.add(new WorkbenchCatalogEntry(catalogId(), slot.specification.label())); return out;
    }
    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> out = new Vector<PhysicalPart<?>>(); out.addAll(inventory.getLooseParts()); return out;
    }
    public String getPartLabel(PhysicalPart<?> part) {
        if (part == null || inventory.get(part.getId()) != part)
            throw new IllegalArgumentException("Foreign converter inventory part");
        return part.getPlayerVisibleNameplate().getWorkbenchDetailValue();
    }
    public PhysicalPart<?> getPart(String id) { return inventory.get(id); }
    public boolean ownsPart(String id) { return inventory.contains(id); }
    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, double time) {
        if (owner.getPhysicalBoardRuntime() != slot.physical.getRuntime())
            throw new IllegalArgumentException("Foreign converter installation");
        return new Controller(sim, owner, modifications);
    }

    private final class Controller implements PhysicalSlotMutationProvider,
            PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
        private final CirSim sim;
        private final GeneratedBoardInstance owner;
        private final BoardModificationController modifications;
        Controller(CirSim sim, GeneratedBoardInstance owner, BoardModificationController modifications) {
            this.sim = sim; this.owner = owner; this.modifications = modifications;
        }
        public String getComponentId() { return slot.getComponentId(); }
        public PhysicalMutationSlot getMutationSlot() { return slot; }
        public boolean ownsPart(String id) { return inventory.contains(id); }
        public WorkbenchCapabilityMetadata getMetadata() {
            return new WorkbenchCapabilityMetadata(getCapabilityId(), "Converter workbench", "SLOT_OPERATIONS");
        }
        public String getOperationLabel(WorkbenchOperation op) {
            if (WorkbenchOperation.REMOVE.equals(op.getId())) return "Remove component";
            if (WorkbenchOperation.INSTALL.equals(op.getId())) return "Install as " + getComponentId();
            if (WorkbenchOperation.LIFT_LEAD.equals(op.getId())) return "Lift lead";
            if (WorkbenchOperation.RECONNECT_LEAD.equals(op.getId())) return "Reconnect lead";
            if (WorkbenchOperation.RESTORE.equals(op.getId())) return "Restore component";
            return "Install new converter";
        }
        public boolean supports(WorkbenchOperation op) {
            if (op == null || !getComponentId().equals(op.getComponentId())) return false;
            String id = op.getId();
            return WorkbenchOperation.REMOVE.equals(id) || WorkbenchOperation.INSTALL.equals(id) ||
                WorkbenchOperation.CATALOG_INSTALL.equals(id) || WorkbenchOperation.LIFT_LEAD.equals(id) ||
                WorkbenchOperation.RECONNECT_LEAD.equals(id) || WorkbenchOperation.RESTORE.equals(id);
        }
        private boolean safe() {
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            if (sim.getGeneratedBoardInstance() != owner || sim.getBoardModificationController() != modifications ||
                    !sim.isChallengeInteractionEnabled() || sim.activeMeasurementOverlay ||
                    !sim.getBoardPowerController().isElectricallyUnpowered() || runtime.isMutationInProgress() ||
                    runtime.isMutationQuarantined()) return false;
            CircuitMeasurementEndpoint red = owner.getSimulationBindings().getEndpoint(slot.padId("IN+"));
            CircuitMeasurementEndpoint black = owner.getSimulationBindings().getEndpoint(slot.padId("IN-"));
            if (!(red instanceof CircuitPostMeasurementEndpoint) || !(black instanceof CircuitPostMeasurementEndpoint)) return false;
            return runtime.getActiveMeasurementReadiness((CircuitPostMeasurementEndpoint)red,
                (CircuitPostMeasurementEndpoint)black, sim.getBoardPowerController().getState(), true).isReady();
        }
        public boolean isAvailable(WorkbenchOperation op, WorkbenchCapabilityContext context) {
            if (!supports(op) || !safe()) return false;
            String id = op.getId();
            if (WorkbenchOperation.CATALOG_INSTALL.equals(id)) return slot.isEmpty() && catalogId().equals(op.getCatalogEntryId());
            if (WorkbenchOperation.INSTALL.equals(id)) return slot.isEmpty() &&
                op.getPart() instanceof PhysicalConverterPart &&
                owner.getPhysicalBoardRuntime().isPartInstallableAt(op.getPart(), getComponentId());
            if (slot.isEmpty() || op.getPart() != null && op.getPart() != slot.getInstalledPart()) return false;
            if (WorkbenchOperation.REMOVE.equals(id)) return true;
            ComponentPhysicalState state = modifications.getComponentState(getComponentId());
            if (WorkbenchOperation.RESTORE.equals(id)) return state != ComponentPhysicalState.INSTALLED;
            BoardPad pad = op.getPadId() == null ? null : owner.getBoard().getPad(op.getPadId());
            if (pad == null || !getComponentId().equals(pad.getComponentId())) return false;
            boolean connected = modifications.isLeadConnected(getComponentId(), pad.getId());
            return WorkbenchOperation.LIFT_LEAD.equals(id) ? state == ComponentPhysicalState.INSTALLED && connected :
                state == ComponentPhysicalState.LEAD_LIFTED && !connected;
        }
        public boolean invoke(WorkbenchOperation op, WorkbenchCapabilityContext context) {
            if (!isAvailable(op, context)) return false;
            String id = op.getId();
            if (WorkbenchOperation.REMOVE.equals(id)) return removeInstalledPart();
            if (WorkbenchOperation.INSTALL.equals(id)) return install(op.getPart().getId());
            if (WorkbenchOperation.CATALOG_INSTALL.equals(id)) return installNewFromCatalog(op.getCatalogEntryId());
            if (WorkbenchOperation.LIFT_LEAD.equals(id)) return modifications.liftLead(getComponentId(), op.getPadId());
            if (WorkbenchOperation.RECONNECT_LEAD.equals(id)) return modifications.reconnectLead(getComponentId(), op.getPadId());
            return modifications.restoreComponent(getComponentId());
        }
        public boolean removeInstalledPart() { return mutate("remove", null, null); }
        public boolean install(String id) {
            PhysicalPart<?> candidate = owner.getPhysicalBoardRuntime().getPart(id);
            if (!(candidate instanceof PhysicalConverterPart) ||
                    !owner.getPhysicalBoardRuntime().isPartInstallableAt(candidate, getComponentId())) return false;
            return mutate("install", (PhysicalConverterPart)candidate, null);
        }
        public boolean installNewFromCatalog(String id) { requireCatalog(id); return mutate("catalog", null, id); }
        public PhysicalPart<?> acquireFromCatalog(String id) {
            requireSafe(); requireCatalog(id);
            PhysicalMutationScope scope = scope("acquire", null, id);
            PhysicalConverterPart part;
            try { part = acquire(scope); scope.commit(); }
            catch (Throwable failure) { scope.abort(failure); PhysicalMutationScope.rethrow(failure); return null; }
            scope.closeAfterCommit(); finish(); return part;
        }
        private void requireCatalog(String id) {
            if (!catalogId().equals(id)) throw new IllegalArgumentException("Unknown converter catalog choice");
        }
        private void requireSafe() {
            if (!safe()) throw new BoardModificationRejectedException("Disconnect supplies and discharge stored energy before replacing the converter");
        }
        private PhysicalMutationScope scope(String operation, PhysicalConverterPart part, String catalog) {
            return new PhysicalMutationScope(sim, owner, modifications, PhysicalMutationIntent.prepare(
                owner.getPhysicalBoardRuntime(), owner, modifications, slot, operation, null, catalog, part));
        }
        private PhysicalConverterPart acquire(PhysicalMutationScope scope) {
            PhysicalConverterPart part = scope.acquire(inventory, getComponentId() + "_CATALOG_PART",
                new PhysicalPartIdentityFactory<PhysicalConverterPart>() {
                    public PhysicalConverterPart create(String id) {
                        IsolatedConverterModuleModel module = allocateModule(owner.getSimulationElements());
                        try {
                            PhysicalConverterPart part = new PhysicalConverterPart(id, slot.specification, module,
                                new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED, id));
                            slot.physical.bindGeometryForAcquisition(part); return part;
                        } catch (Throwable failure) {
                            for (CircuitElm element : module.elements()) element.delete();
                            PhysicalMutationScope.rethrow(failure); return null;
                        }
                    }
                });
            for (CircuitElm element : part.getModule().elements()) {
                scope.registerCanonicalElement(element); scope.appendActiveElement(element);
            }
            return part;
        }
        private boolean mutate(String operation, PhysicalConverterPart requested, String catalog) {
            requireSafe();
            if ("remove".equals(operation) ? slot.isEmpty() : !slot.isEmpty()) return false;
            PhysicalMutationScope scope = scope(operation, requested, catalog);
            try {
                if ("remove".equals(operation)) {
                    modifications.disconnectComponentForMutation(scope, getComponentId()); scope.clearPart();
                } else {
                    PhysicalConverterPart part = catalog == null ? requested : acquire(scope);
                    scope.replacePrimaryBinding(part.getModule().converter);
                    scope.replaceAuxiliaryBindings(part.auxiliaryElements());
                    for (GeneratedComponentConnectionBinding binding : owner.getConnectionBindings().getForComponent(getComponentId()))
                        scope.retargetEndpoint(binding, part.terminal(owner.getBoard().getPad(binding.getPadId()).getTerminalId()));
                    scope.installPart(part); scope.restoreComponentGraph();
                }
                scope.commit();
            } catch (Throwable failure) { scope.abort(failure); PhysicalMutationScope.rethrow(failure); return false; }
            scope.closeAfterCommit(); finish(); return true;
        }
        private void finish() {
            try {
                if (sim.getGeneratedChallengeController() != null) sim.getGeneratedChallengeController().invalidateCustomerRetest();
                sim.needAnalyze(); sim.requestGeneratedBoardVerification(); sim.refreshBoardModificationControls();
            } catch (Throwable failure) { sim.markGeneratedRuntimeFailure(owner, failure); PhysicalMutationScope.rethrow(failure); }
        }
    }

    private static IsolatedConverterModuleModel allocateModule(Vector<CircuitElm> occupied) {
        for (int attempt = 0; attempt < 4096; attempt++) {
            IsolatedConverterModuleModel candidate = new IsolatedConverterModuleModel(48000 + attempt * 512, 48000);
            boolean collision = false;
            for (CircuitElm element : candidate.elements()) for (int post = 0; post < element.getPostCount(); post++)
                for (CircuitElm existing : occupied) for (int other = 0; other < existing.getPostCount(); other++)
                    if (element.getPost(post).equals(existing.getPost(other))) collision = true;
            if (!collision) return candidate;
            for (CircuitElm element : candidate.elements()) element.delete();
        }
        throw new IllegalStateException("Converter backing allocation exhausted");
    }

    private static final class ConverterSlot implements PhysicalMutationSlot {
        final PhysicalBoardSlot physical;
        final ConverterSpecification specification;
        final WireElm[] attachments;
        final AttachmentState emptySlotAttachmentState;
        ConverterSlot(PhysicalBoardSlot physical, PhysicalConverterPart original, WireElm[] attachments) {
            if (physical == null || original == null || attachments == null || attachments.length != 7)
                throw new IllegalArgumentException("Converter needs seven attachments");
            this.physical = physical; this.specification = original.getSpecification(); this.attachments = new WireElm[7];
            for (int i = 0; i < 7; i++) {
                if (attachments[i] == null) throw new IllegalArgumentException("Missing converter attachment");
                this.attachments[i] = attachments[i];
            }
            if (physical.getInstalledPart() == null) physical.install(original);
            else if (physical.getInstalledPart() != original) throw new IllegalArgumentException("Foreign converter slot owner");
            emptySlotAttachmentState = captureAttachmentState();
        }
        public String getComponentId() { return physical.getComponentId(); }
        String padId(String terminal) {
            for (int i = 0; i < physical.getTerminalIds().size(); i++)
                if (terminal.equals(physical.getTerminalIds().get(i))) return physical.getPadIds().get(i);
            throw new IllegalStateException("Missing converter pad: " + terminal);
        }
        public PhysicalBoardSlot getPhysicalSlot() { return physical; }
        public PhysicalConverterPart getInstalledPart() { return (PhysicalConverterPart)physical.getInstalledPart(); }
        public boolean isEmpty() { return !physical.isOccupied(); }
        public boolean acceptsPart(PhysicalPart<?> part) {
            return part instanceof PhysicalConverterPart && part.getPackage().isEquivalentTo(PhysicalPackages.ISOLATED_CONVERTER_7) &&
                specification.getSpecificationId().equals(part.getSpecification().getSpecificationId());
        }
        public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
            if (!acceptsPart(part) || pad == null || !getComponentId().equals(pad.getComponentId()))
                throw new IllegalArgumentException("Foreign converter pad");
            return ((PhysicalConverterPart)part).terminal(pad.getTerminalId());
        }
        public void installForMutation(PhysicalPart<?> part, PhysicalMutationScope scope) {
            if (!scope.owns(this) || !acceptsPart(part)) throw new IllegalArgumentException("Foreign converter scope");
            for (int i = 0; i < 7; i++) {
                CircuitPostMeasurementEndpoint endpoint = ((PhysicalConverterPart)part).terminal(ConverterSpecification.terminalId(i));
                Point point = endpoint.getElement().getPost(endpoint.getPostIndex());
                attachments[i].x2 = point.x; attachments[i].y2 = point.y; attachments[i].setPoints(); scope.afterAttachmentWrite();
            }
            physical.install(part); scope.afterSlotMountWrite();
        }
        public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
            if (!scope.owns(this)) throw new IllegalArgumentException("Foreign converter scope");
            PhysicalPart<?> part = physical.remove(); scope.afterSlotClearWrite(); return part;
        }
        public void restoreEmptySlotAttachmentState(PhysicalMutationScope scope) {
            if (scope == null || !scope.owns(this)) throw new IllegalArgumentException("Foreign converter scope");
            restoreAttachmentState(emptySlotAttachmentState);
        }
        private final class State implements AttachmentState {
            final ConverterSlot owner = ConverterSlot.this;
            final int[] xy = new int[14];
            State() { for (int i = 0; i < 7; i++) { xy[2*i] = attachments[i].x2; xy[2*i+1] = attachments[i].y2; } }
        }
        public AttachmentState captureAttachmentState() { return new State(); }
        public void restoreAttachmentState(AttachmentState snapshot) {
            if (!(snapshot instanceof State) || ((State)snapshot).owner != this)
                throw new IllegalArgumentException("Foreign converter attachment snapshot");
            State state = (State)snapshot;
            for (int i = 0; i < 7; i++) {
                attachments[i].x2 = state.xy[2*i]; attachments[i].y2 = state.xy[2*i+1]; attachments[i].setPoints();
            }
        }
    }
}
