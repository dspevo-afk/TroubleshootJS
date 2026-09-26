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
    private static final double DISCHARGED_VOLTS = .05;
    private static final ReplaceableRelayCapability.DischargeGuard DISCHARGE_GUARD =
        new ReplaceableRelayCapability.DischargeGuard() {
            public boolean isDischarged(GeneratedBoardInstance owner,
                    String componentId) {
                return Rb30RelayService.isDischarged(owner, componentId);
            }
        };

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
        e03 = new ReplaceableRelayCapability(physical, original, attachments,
            DISCHARGE_GUARD);
    }

    /** Q30 relay service is gated by energy at this relay's five physical pins. */
    static boolean isDischarged(GeneratedBoardInstance owner,
            String componentId) {
        if (owner == null || !Rb30Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()) ||
                !("KA".equals(componentId) || "KB".equals(componentId)))
            return false;
        BoardComponent component = owner.getBoard().getComponent(componentId);
        if (component == null || !PhysicalPackages.RELAY_SPDT.isEquivalentTo(
                component.getPhysicalPackage()) ||
                owner.getPhysicalBoardRuntime().getSlot(componentId) == null)
            return false;

        Vector<CircuitElm> elements = owner.getSimulationElements();
        double controlReturn = padVoltage(owner, elements, "J1.2");
        double loadReturn = padVoltage(owner, elements, "JLOAD.2");
        double[] pinVoltages = new double[RelaySpecification.TERMINALS.length];
        for (int index = 0; index < pinVoltages.length; index++) {
            String terminal = RelaySpecification.TERMINALS[index];
            double reference = index < 2 ? controlReturn : loadReturn;
            pinVoltages[index] = padVoltage(owner, elements,
                componentId + "." + terminal) - reference;
        }

        double coilCurrent = 0;
        PhysicalPart<?> installed = owner.getPhysicalBoardRuntime()
            .getInstalledPart(componentId);
        if (installed != null) {
            if (!(installed instanceof PhysicalRelayPart)) return false;
            ServiceRelayElm relay = ((PhysicalRelayPart) installed).getElement();
            if (!elements.contains(relay)) return false;
            coilCurrent = relay.coilCurrent;
        }
        return safeReadings(pinVoltages, coilCurrent);
    }

    /** Values are each terminal's voltage relative to its own local return. */
    static boolean safeReadings(double[] terminalVolts, double coilAmps) {
        if (terminalVolts == null ||
                terminalVolts.length != RelaySpecification.TERMINALS.length ||
                !PowerDomainContract.finite(coilAmps) ||
                Math.abs(coilAmps) >= RelayOutputBehavior.DISCHARGED_AMPS)
            return false;
        for (int index = 0; index < terminalVolts.length; index++)
            if (!PowerDomainContract.finite(terminalVolts[index]) ||
                    Math.abs(terminalVolts[index]) >= DISCHARGED_VOLTS)
                return false;
        return true;
    }

    private static double padVoltage(GeneratedBoardInstance owner,
            Vector<CircuitElm> elements, String padId) {
        if (owner.getBoard().getPad(padId) == null) return Double.NaN;
        CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings()
            .getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            return Double.NaN;
        CircuitPostMeasurementEndpoint post =
            (CircuitPostMeasurementEndpoint) endpoint;
        CircuitElm element = post.getElement();
        if (!elements.contains(element) || post.getPostIndex() < 0 ||
                post.getPostIndex() >= element.getPostCount())
            return Double.NaN;
        return element.getPostVoltage(post.getPostIndex());
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
