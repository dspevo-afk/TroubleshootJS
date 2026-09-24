package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Provider-local relay owner for one Q30 channel.
 *
 * <p>The accepted E03 capability owns the five-terminal mutation transaction
 * and the real {@link ServiceRelayElm} physics.  Q30 composes one private
 * instance per relay, then gives each instance a distinct runtime and
 * workbench identity so KA and KB cannot share a singleton registration or
 * catalog inventory.</p>
 */
final class Rb30RelayService implements PhysicalBoardRuntimeCapability,
        PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    static final String CAPABILITY_PREFIX = "RB30_RELAY_SERVICE_";
    static final String WORKBENCH_PREFIX = "RB30_RELAY_WORKBENCH_";
    static final String CATALOG_PREFIX = "RB30_RELAY_STOCK_";

    private final String componentId;
    private final String capabilityId;
    private final String workbenchId;
    private final String fiveVoltCatalogId;
    private final String twelveVoltCatalogId;
    private final ReplaceableRelayCapability e03;

    Rb30RelayService(PhysicalBoardSlot physical, PhysicalRelayPart original,
            WireElm[] attachments) {
        if (physical == null || physical.getRuntime() == null || original == null ||
                !PhysicalPackages.RELAY_SPDT.isEquivalentTo(original.getPackage()))
            throw new IllegalArgumentException("Invalid RB30 relay service owner");
        componentId = physical.getComponentId();
        capabilityId = CAPABILITY_PREFIX + componentId;
        workbenchId = WORKBENCH_PREFIX + componentId;
        fiveVoltCatalogId = CATALOG_PREFIX + componentId + "_5V";
        twelveVoltCatalogId = CATALOG_PREFIX + componentId + "_12V";
        e03 = new ReplaceableRelayCapability(physical, original, attachments);
    }

    public String getCapabilityId() { return capabilityId; }
    public String getComponentId() { return componentId; }
    public PhysicalMutationSlot getMutationSlot() { return e03.getMutationSlot(); }
    public PhysicalPartInventory<?> getMutationInventory() {
        return e03.getMutationInventory();
    }
    public String getCatalogTitle() {
        return componentId + " relay replacement catalog";
    }
    public String getInstallNewLabel() {
        return "Install new " + componentId + " relay";
    }
    public boolean showOccupiedMessageWhenPowered() {
        return e03.showOccupiedMessageWhenPowered();
    }

    String getFiveVoltCatalogId() { return fiveVoltCatalogId; }
    String getTwelveVoltCatalogId() { return twelveVoltCatalogId; }

    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
        result.add(new WorkbenchCatalogEntry(fiveVoltCatalogId,
            new RelaySpecification(5).label()));
        result.add(new WorkbenchCatalogEntry(twelveVoltCatalogId,
            new RelaySpecification(12).label()));
        return result;
    }

    public Vector<PhysicalPart<?>> getLooseParts() {
        return e03.getLooseParts();
    }

    public String getPartLabel(PhysicalPart<?> part) {
        return e03.getPartLabel(part);
    }

    public PhysicalPart<?> getPart(String partId) {
        return e03.getPart(partId);
    }

    public boolean ownsPart(String partId) {
        return e03.ownsPart(partId);
    }

    public PhysicalSlotMutationProvider install(CirSim sim,
            GeneratedBoardInstance instance,
            BoardModificationController modifications,
            double initialSimulationTime) {
        PhysicalSlotMutationProvider delegate = e03.install(sim, instance,
            modifications, initialSimulationTime);
        if (delegate == null)
            return null;
        return new Controller(delegate);
    }

    private String toE03CatalogId(String catalogEntryId) {
        if (fiveVoltCatalogId.equals(catalogEntryId))
            return ReplaceableRelayCapability.COIL_5V;
        if (twelveVoltCatalogId.equals(catalogEntryId))
            return ReplaceableRelayCapability.COIL_12V;
        throw new IllegalArgumentException("Unknown " + componentId +
            " relay catalog choice");
    }

    private WorkbenchOperation toE03Operation(WorkbenchOperation operation) {
        if (operation == null ||
                !WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId()))
            return operation;
        String catalogId = operation.getCatalogEntryId();
        if (fiveVoltCatalogId.equals(catalogId))
            catalogId = ReplaceableRelayCapability.COIL_5V;
        else if (twelveVoltCatalogId.equals(catalogId))
            catalogId = ReplaceableRelayCapability.COIL_12V;
        return WorkbenchOperation.forCatalog(operation.getComponentId(), catalogId);
    }

    private final class Controller implements PhysicalSlotMutationProvider,
            PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
        private final PhysicalSlotMutationProvider delegate;

        Controller(PhysicalSlotMutationProvider delegate) {
            if (!(delegate instanceof PhysicalSlotMutationProvider.Scoped))
                throw new IllegalArgumentException(
                    "E03 relay provider did not expose a scoped slot");
            this.delegate = delegate;
        }

        public WorkbenchCapabilityMetadata getMetadata() {
            return new WorkbenchCapabilityMetadata(workbenchId,
                componentId + " relay workbench", "SLOT_OPERATIONS");
        }

        public String getOperationLabel(WorkbenchOperation operation) {
            if (operation == null)
                return "Modify " + componentId + " relay";
            return delegate.getOperationLabel(toE03Operation(operation));
        }

        public boolean supports(WorkbenchOperation operation) {
            return delegate.supports(toE03Operation(operation));
        }

        public boolean isAvailable(WorkbenchOperation operation,
                WorkbenchCapabilityContext context) {
            return delegate.isAvailable(toE03Operation(operation), context);
        }

        public boolean invoke(WorkbenchOperation operation,
                WorkbenchCapabilityContext context) {
            return delegate.invoke(toE03Operation(operation), context);
        }

        public String getComponentId() { return componentId; }

        public boolean ownsPart(String partId) {
            return delegate.ownsPart(partId);
        }

        public boolean removeInstalledPart() {
            return delegate.removeInstalledPart();
        }

        public boolean install(String partId) {
            return delegate.install(partId);
        }

        public boolean installNewFromCatalog(String catalogEntryId) {
            return delegate.installNewFromCatalog(
                toE03CatalogId(catalogEntryId));
        }

        public PhysicalMutationSlot getMutationSlot() {
            return ((PhysicalSlotMutationProvider.Scoped) delegate)
                .getMutationSlot();
        }

        public PhysicalPart<?> acquireFromCatalog(String catalogEntryId) {
            if (!(delegate instanceof CatalogAcquisitionProvider))
                throw new IllegalStateException(
                    "E03 relay provider lost catalog acquisition");
            return ((CatalogAcquisitionProvider) delegate).acquireFromCatalog(
                toE03CatalogId(catalogEntryId));
        }
    }
}
