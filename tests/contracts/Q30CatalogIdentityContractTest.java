package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Catalog identity and declaration compatibility contract for Q30 services. */
public final class Q30CatalogIdentityContractTest {
    private static int assertions;

    public static void main(String[] args) {
        WorkbenchCatalogEntry legacy = new WorkbenchCatalogEntry("legacy-id", "same label");
        check("legacy-id".equals(legacy.getSpecificationKey()),
            "legacy catalog rows default their semantic key to the catalog ID");
        WorkbenchCatalogEntry explicit = new WorkbenchCatalogEntry(
            "provider-alias", "same label", "semantic-spec");
        check("semantic-spec".equals(explicit.getSpecificationKey()),
            "catalog rows retain their explicit semantic key");

        CirSim previous = CircuitElm.sim;
        CirSim sim = new CirSim(); sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        Fixture candidate = null;
        try {
            Rb30Plan plan = Rb30Plan.reference(13L);
            candidate = new Fixture(new Rb30Generator()
                .generateNormalForQualification(plan));
            verifyRelayAliases(candidate);
            verifyDistinctKeysNeverMerge(candidate);
            verifyDecisionDeclarationIdentity(candidate, plan);
            check(sim.elmList.isEmpty(), "catalog inspection never installs a detached graph");
        } finally {
            try {
                if (candidate != null) {
                    candidate.owner.getExternalPowerBindings().setConnected(false);
                    for (CircuitElm element : candidate.owner.getSimulationElements()) element.delete();
                }
            } finally { CircuitElm.sim = previous; }
        }
        System.out.println("PASS: Q30 catalog identity contracts assertions=" + assertions);
    }

    private static void verifyRelayAliases(Fixture candidate) {
        PlayerShopCatalog.Category category = new PlayerShopCatalog.Category("RELAY");
        category.add(candidate.relayServiceA);
        category.add(candidate.relayServiceB);
        check(category.entries().size() == 2,
            "same relay specifications from KA and KB merge by semantic key and fit");
        check(candidate.relayServiceA.getCatalogEntries().firstElement()
                .getSpecificationKey().equals(candidate.relayServiceB.getCatalogEntries()
                .firstElement().getSpecificationKey()),
            "relay aliases publish the same typed specification key");
    }

    private static void verifyDistinctKeysNeverMerge(Fixture candidate) {
        PlayerShopCatalog.Category category = new PlayerShopCatalog.Category("RELAY");
        category.add(new CatalogAlias(candidate.relayServiceA,
            new WorkbenchCatalogEntry("provider-a", "same public label", "SPEC_A")));
        category.add(new CatalogAlias(candidate.relayServiceB,
            new WorkbenchCatalogEntry("provider-b", "same public label", "SPEC_B")));
        check(category.entries().size() == 2,
            "different semantic specifications with the same label never merge");
        rejectLabel(category, category.entries().firstElement());
    }

    private static void verifyDecisionDeclarationIdentity(
            final Fixture candidate, Rb30Plan plan) {
        String first = candidate.serviceA.getOriginal().getSpecification()
            .getSpecificationId();
        String second = candidate.serviceB.getOriginal().getSpecification()
            .getSpecificationId();
        check(first.equals(second) && first.equals(
                candidate.serviceA.getOriginal().getElement().declarationIdentity()),
            "same Q30 decision declarations publish one immutable specification identity");
        check(candidate.serviceA.getSlot().acceptsPart(candidate.serviceB.getOriginal()) &&
                candidate.serviceB.getSlot().acceptsPart(candidate.serviceA.getOriginal()),
            "same-declaration U2A and U2B parts are compatible at either physical slot");
        PlayerShopCatalog.Category controllers = new PlayerShopCatalog.Category("SENSOR_CONTROL");
        controllers.add(candidate.serviceA); controllers.add(candidate.serviceB);
        check(controllers.entries().size() == 1,
            "identical controller declarations share one actual catalog choice");

        E04SensorControlModel.Variant differentVariant = plan.sharedHystereticReference ?
            E04SensorControlModel.Variant.DIRECT_THRESHOLD :
            E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE;
        E04SensorControlModel.RailContract rail =
            E04SensorControlModel.RailDeclaration.fiveVolt();
        final E04SensorControlModel.DecisionElement different =
            new E04SensorControlModel.DecisionElement(0, 0, rail, differentVariant,
                E04SensorControlModel.Configuration.defaults(rail));
        try {
        check(!candidate.serviceA.getOriginal().getElement()
                .hasSameDeclaration(different),
            "different Q30 decision declarations remain distinct");
        reject(new Runnable() {
            public void run() {
                candidate.serviceA.setActiveDecisionElement(different);
            }
        }, "different Q30 decision declaration is rejected by the owner");
        E04DecisionControlPart foreign = new E04DecisionControlPart("DIFFERENT_DECLARATION",
            new BasicPhysicalSpecification(different.declarationIdentity()),
            new PhysicalNameplate("DIFFERENT_DECLARATION", "Sensor controller"), different,
            new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED, "test-declaration"));
        check(!candidate.serviceA.getSlot().acceptsPart(foreign) &&
            !candidate.serviceB.getSlot().acceptsPart(foreign),
            "a different decision declaration cannot enter either qualified physical slot");
        } finally { different.delete(); }
    }

    private static void rejectLabel(PlayerShopCatalog.Category category,
            PlayerShopCatalog.Entry entry) {
        try {
            category.label(entry);
        } catch (IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("indistinguishable distinct specifications were exposed");
    }

    private static void reject(Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }

    private static final class Fixture {
        final GeneratedBoardInstance owner;
        final Rb30RelayService relayServiceA, relayServiceB;
        final Rb30DecisionService serviceA, serviceB;
        Fixture(GeneratedBoardInstance owner) {
            this.owner = owner;
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            relayServiceA = (Rb30RelayService) runtime.getScopedMutationCapability("KA");
            relayServiceB = (Rb30RelayService) runtime.getScopedMutationCapability("KB");
            serviceA = (Rb30DecisionService) runtime.getScopedMutationCapability("U2A");
            serviceB = (Rb30DecisionService) runtime.getScopedMutationCapability("U2B");
        }
    }

    private static final class CatalogAlias implements WorkbenchPartsProvider,
            PhysicalBoardInstallationProvider.Scoped {
        private final WorkbenchPartsProvider delegate;
        private final PhysicalBoardInstallationProvider.Scoped scoped;
        private final WorkbenchCatalogEntry entry;

        CatalogAlias(WorkbenchPartsProvider delegate, WorkbenchCatalogEntry entry) {
            if (!(delegate instanceof PhysicalBoardInstallationProvider.Scoped))
                throw new IllegalArgumentException("Catalog alias lacks a scoped provider");
            this.delegate = delegate;
            this.scoped = (PhysicalBoardInstallationProvider.Scoped) delegate;
            this.entry = entry;
        }

        public String getComponentId() { return delegate.getComponentId(); }
        public String getCatalogTitle() { return delegate.getCatalogTitle(); }
        public String getInstallNewLabel() { return delegate.getInstallNewLabel(); }
        public boolean showOccupiedMessageWhenPowered() {
            return delegate.showOccupiedMessageWhenPowered();
        }
        public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
            Vector<WorkbenchCatalogEntry> result = new Vector<WorkbenchCatalogEntry>();
            result.add(entry);
            return result;
        }
        public Vector<PhysicalPart<?>> getLooseParts() { return delegate.getLooseParts(); }
        public String getPartLabel(PhysicalPart<?> part) { return delegate.getPartLabel(part); }
        public PhysicalPart<?> getPart(String partId) { return delegate.getPart(partId); }
        public boolean ownsPart(String partId) { return delegate.ownsPart(partId); }
        public PhysicalMutationSlot getMutationSlot() { return scoped.getMutationSlot(); }
        public PhysicalPartInventory<?> getMutationInventory() {
            return scoped.getMutationInventory();
        }
        public PhysicalSlotMutationProvider install(CirSim sim,
                GeneratedBoardInstance instance,
                BoardModificationController modifications,
                double initialSimulationTime) {
            return ((PhysicalBoardInstallationProvider) delegate).install(sim, instance,
                modifications, initialSimulationTime);
        }
    }
}
