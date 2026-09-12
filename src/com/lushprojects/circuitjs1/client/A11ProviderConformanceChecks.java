package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared native/compiled declaration conformance. Actual solver proof is Task49. */
final class A11ProviderConformanceChecks {
    private static final String NMOS = "nmos-low-side-driver";
    private static final String ALT = "nmos-low-side-driver-alt";
    private static final String NPN = "npn-low-side-driver";
    private static int assertions, rejected, plans, parts;
    private interface Action { void run(); }
    private A11ProviderConformanceChecks() { }

    static String verify() {
        assertions = rejected = plans = parts = 0;
        final ConstructionProviderRegistry registry = ConstructionProviderRegistry.standard();
        final List<ConstructionProviderRegistry.Entry> entries = registry.entries();
        check(entries.size() == 9, "nine declared registrations");
        int paired = 0, joins = 0;
        Set<String> ids = new HashSet<String>();
        for (ConstructionProviderRegistry.Entry entry : entries) {
            check(ids.add(entry.getProviderId() + "@" + entry.getVersion()), "unique exact key");
            check(registry.get(entry.getProviderId(), entry.getVersion()) == entry,
                "lookup returns the registered immutable entry");
            check(StandardPhysicalConstructionProviders.provider(entry.getProviderId(),
                entry.getVersion()) == entry.getPhysical(), "physical registry has one authority");
            if (entry.isDeviceJoin()) joins++;
            else {
                paired++;
                check(StandardElectricalConstructionProviders.provider(entry.getProviderId(),
                    entry.getVersion()) == entry.getElectrical(), "electrical registry has one authority");
            }
            for (PhysicalPackage physical : entry.getPackages()) entry.requirePackage(physical);
        }
        check(paired == 7 && joins == 2, "all seven construction pairs and two explicit joins");
        List<LowSideRoleFamily.Provider> roles = registry.lowSideProviders();
        check(roles.size() == 3, "three role variants");
        String[] expected = { NMOS, ALT, NPN };
        for (int i = 0; i < expected.length; i++) {
            LowSideRoleFamily.Provider role = roles.get(i);
            check(expected[i].equals(role.getTypeId()), "independent canonical role order");
            ConstructionProviderRegistry.Entry entry = registry.get(role.getTypeId(), role.getVersion());
            check(entry.getControlledContribution() == role &&
                ControlledIndicatorBlockContributions.resolve(role.getTypeId(), role.getVersion()) == role &&
                LowSideRoleFamily.resolve(role.getTypeId(), role.getVersion()) == role,
                "role/contribution lookups share the exact registration");
            ControlledIndicatorDriverObservation observation = entry.getDriverObservation();
            check(observation.getProviderId().equals(role.getTypeId()) &&
                observation.getVersion() == role.getVersion() &&
                ControlledIndicatorDriverObservations.forProvider(role.getTypeId(), role.getVersion()) == observation,
                "diagnostic observation is paired by type and version");
        }
        negatives(registry);
        plan(BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forCanary(1L)));
        Set<String> exercised = new HashSet<String>();
        for (long seed : new long[] { 0L, 1L, 2L, 4L, 9L }) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(seed));
            plan(plan);
            GeneratedDiagnosticPlan diagnostic = GeneratedDiagnosticPlanCatalog.forAssembly(plan);
            check(diagnostic.getRepairActionIds().contains(WorkbenchOperation.CATALOG_INSTALL) &&
                diagnostic.getIsolationActionIds().contains(WorkbenchOperation.REMOVE),
                "declared diagnostic program exposes real isolation and catalog repair");
            for (String pad : diagnostic.getProbeTargetIds())
                check(plan.getElectricalRealizationSpec().getPadBinding(pad) != null, "diagnostic pad exists in electrical spec");
            for (ControlledIndicatorChannel channel : plan.getChannels()) {
                ComposedBlockContribution driver = plan.getBlocks().get(channel.getDriverKey());
                exercised.add(driver.getProviderTypeId());
                if (ALT.equals(driver.getProviderTypeId())) {
                    ElectricalRealizationSpec.ElementDeclaration element = plan.getElectricalRealizationSpec()
                        .getElementDeclaration(channel.getDriverKey(), "Q1");
                    check("NMOS".equals(element.getKind()) && "NMOS".equals(element.getModelId()) &&
                        element.getParameter("threshold") == 1.2 && element.getParameter("beta") == 6.0,
                        "alternate provider owns distinct declared model parameters");
                    check(element.getPostIndex("G") == 0 && element.getPostIndex("D") == 2 &&
                        element.getPostIndex("S") == 1, "alternate preserves actual CircuitJS terminal adapter");
                    check(driver.getResistor("RG").getResistanceOhms() == 680.0 &&
                        driver.getResistor("RG").isMutable() &&
                        "RG".equals(driver.getFaultSpec().getTargetComponentLocalId()),
                        "alternate owns the actual gate-resistor recipe, mutation and fault target");
                }
            }
        }
        check(exercised.equals(new HashSet<String>(Arrays.asList(expected))), "explicit plans cover every role");
        return "{\"protocol\":\"TSJ-A11-PROVIDERS-1\",\"status\":\"PASS\","
            + "\"scope\":\"provider-declarations\",\"entries\":" + entries.size()
            + ",\"paired\":" + paired + ",\"joins\":" + joins + ",\"roleVariants\":" + roles.size()
            + ",\"plans\":" + plans + ",\"parts\":" + parts + ",\"negativeCases\":" + rejected
            + ",\"assertions\":" + assertions + ",\"generatorVersion\":" + BoundedAssemblyRequest.GENERATOR_VERSION
            + ",\"solverEvidence\":\"separate-current-Task49-report\"}";
    }

    private static void plan(BoundedAssemblyPlan plan) {
        plans++;
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        PhysicalConstructionMetadata metadata = PhysicalConstructionMaterializer.describe(plan);
        PhysicalConstructionMaterializer.validateDeclarations(plan, spec, metadata.getDeclarations());
        Set<String> faults = new HashSet<String>();
        for (PhysicalConstructionPartDeclaration part : metadata.getDeclarations().getBoardParts()) {
            parts++;
            ConstructionProviderRegistry.Entry entry = ConstructionProviderRegistry.standard().get(
                part.getProviderId(), part.getProviderVersion());
            entry.requirePackage(part.getPhysicalPackage());
            check(spec.getPackageMap().getPackages().get(part.getComponentId())
                .isEquivalentTo(part.getPhysicalPackage()), "declaration consumes exact registered package");
            check(part.getTerminals().size() == part.getPhysicalPackage().getTerminalCount(),
                "all package pins have explicit physical/electrical correspondence");
            if (part.hasFault()) {
                faults.add(part.getComponentId());
                check(part.isMutableResistor() && part.getComponentId().equals(part.getRepairComponentId()),
                    "fault belongs to a declared serviceable repair owner");
            }
        }
        check(faults.equals(new HashSet<String>(plan.getDecisionOwners().values())),
            "diagnostic fault population equals physical serviceable population");
    }

    private static void negatives(final ConstructionProviderRegistry registry) {
        final ConstructionProviderRegistry.Entry nmos = registry.get(NMOS, 1);
        final ConstructionProviderRegistry.Entry npn = registry.get(NPN, 1);
        reject(new Action() { public void run() { isolated(Arrays.asList(nmos, nmos)); } }, "duplicate type/version");
        reject(new Action() { public void run() { registry.get(NMOS, 2); } }, "unsupported version");
        reject(new Action() { public void run() { registry.get("missing-provider", 1); } }, "unknown provider");
        reject(new Action() { public void run() {
            copy(null, nmos.getPhysical(), nmos.getControlledContribution(), nmos.getDriverObservation(), nmos.getPackages());
        } }, "physical-only recognition without explicit join");
        reject(new Action() { public void run() {
            copy(nmos.getElectrical(), null, nmos.getControlledContribution(), nmos.getDriverObservation(), nmos.getPackages());
        } }, "electrical-only recognition");
        reject(new Action() { public void run() {
            copy(npn.getElectrical(), nmos.getPhysical(), nmos.getControlledContribution(), nmos.getDriverObservation(), nmos.getPackages());
        } }, "conflicting electrical/physical pair");
        reject(new Action() { public void run() {
            copy(nmos.getElectrical(), nmos.getPhysical(), nmos.getControlledContribution(), null, nmos.getPackages());
        } }, "role missing observation");
        reject(new Action() { public void run() {
            copy(nmos.getElectrical(), nmos.getPhysical(), nmos.getControlledContribution(), npn.getDriverObservation(), nmos.getPackages());
        } }, "role/observation identity mismatch");
        reject(new Action() { public void run() {
            new ConstructionProviderRegistry(Arrays.asList(nmos), new PcbFootprintRegistry(),
                StandardPhysicalPartRenderProviders.createRegistry());
        } }, "missing footprint");
        reject(new Action() { public void run() {
            new ConstructionProviderRegistry(Arrays.asList(nmos), StandardPcbFootprintProviders.createRegistry(),
                new PhysicalPartRenderRegistry());
        } }, "missing renderer");
        reject(new Action() { public void run() {
            PhysicalPartRenderRegistry renderers = StandardPhysicalPartRenderProviders.createRegistry();
            PhysicalPackage original = PhysicalPackages.AXIAL_RESISTOR;
            PhysicalPackage conflicting = new PhysicalPackage(original.getId(), original.getTerminalIds(),
                new java.util.Vector<String>(), !original.isConnector(), original.getGeometry());
            renderers.hasProvider(conflicting);
        } }, "same ID with inequivalent render package");
        reject(new Action() { public void run() {
            PhysicalPartRenderRegistry partial = new PhysicalPartRenderRegistry();
            partial.register(PhysicalPackages.AXIAL_RESISTOR,
                StandardPhysicalPartRenderProviders.createRegistry().getProvider(PhysicalPackages.AXIAL_RESISTOR));
            new ConstructionProviderRegistry(Arrays.asList(nmos), StandardPcbFootprintProviders.createRegistry(), partial);
        } }, "one declared package lacks a renderer registration");
        final int[] rendererCalls = { 0 };
        new ConstructionProviderRegistry(Arrays.asList(nmos), StandardPcbFootprintProviders.createRegistry(),
            partAwareRenderers(rendererCalls, null));
        check(rendererCalls[0] == 0, "bootstrap checks registration without asking for a nonexistent part");
        reject(new Action() { public void run() {
            copy(nmos.getElectrical(), nmos.getPhysical(), nmos.getControlledContribution(), nmos.getDriverObservation(),
                Collections.<PhysicalPackage>emptyList());
        } }, "empty package claim");
        reject(new Action() { public void run() {
            ConstructionProviderRegistry.Entry missing = new ConstructionProviderRegistry.Entry(nmos.getElectrical(),
                nmos.getPhysical(), nmos.getControlledContribution(), nmos.getDriverObservation(),
                Arrays.asList(PhysicalPackages.AXIAL_RESISTOR), false);
            isolated(Arrays.asList(missing)).get(NMOS, 1).requirePackage(PhysicalPackages.TO92_NMOS);
        } }, "undeclared consumed package");
        reject(new Action() { public void run() {
            new ElectricalRealizationSpec.ElementDeclaration("driver", "Q1", "NMOS", "Q1",
                ElectricalRealizationSpec.posts("G", 0, "D", 1, "S", 2), "NMOS",
                ElectricalRealizationSpec.numbers("threshold", 1.2, "beta", 6.0));
        } }, "inconsistent real primitive pin mapping");
        reject(new Action() { public void run() {
            new ElectricalRealizationSpec.ElementDeclaration("driver", "Q1", "NMOS", "Q1",
                ElectricalRealizationSpec.posts("G", 0, "D", 2, "S", 1), "UNAVAILABLE_MODEL",
                ElectricalRealizationSpec.numbers("threshold", 1.2, "beta", 6.0));
        } }, "unavailable model hidden behind a known primitive");
        reject(new Action() { public void run() {
            new ElectricalRealizationSpec.ElementDeclaration("driver", "Q1", "NMOS", "Q1",
                ElectricalRealizationSpec.posts("G", 0, "D", 2, "S", 1), "NMOS",
                ElectricalRealizationSpec.numbers("threshold", 1.2, "beta", 0.0));
        } }, "invalid physical model parameter");
        immutable(new Action() { public void run() { registry.entries().clear(); } }, "entries immutable");
        immutable(new Action() { public void run() { registry.lowSideProviders().clear(); } }, "role view immutable");
        immutable(new Action() { public void run() { nmos.getPackages().clear(); } }, "package claim immutable");
    }

    private static void copy(ElectricalConstructionProvider electrical, PhysicalConstructionProvider physical,
            ControlledIndicatorBlockContributions.Provider contribution, ControlledIndicatorDriverObservation observation,
            java.util.Collection<PhysicalPackage> packages) {
        isolated(Arrays.asList(new ConstructionProviderRegistry.Entry(electrical, physical, contribution, observation, packages, false)));
    }
    private static ConstructionProviderRegistry isolated(List<ConstructionProviderRegistry.Entry> entries) {
        return new ConstructionProviderRegistry(entries, StandardPcbFootprintProviders.createRegistry(),
            StandardPhysicalPartRenderProviders.createRegistry());
    }
    private static void reject(Action action, String label) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { assertions++; rejected++; return; }
        catch (IllegalStateException expected) { assertions++; rejected++; return; }
        throw new AssertionError("A11 did not reject " + label);
    }
    private static void immutable(Action action, String label) {
        try { action.run(); }
        catch (UnsupportedOperationException expected) { assertions++; rejected++; return; }
        throw new AssertionError("A11 " + label);
    }
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError("A11 " + label);
    }

    /** Real-part conformance at the same constructor boundary used before publication. */
    static void verifyRenderParts(final CirSim sim) {
        final GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        final Object graph = sim.elmList;
        final PcbWorkbenchController owner = sim.pcbWorkbenchController;
        final PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getPhysicalParts().get(0);
        final int[] calls = { 0 };
        final PhysicalPartRenderRegistry aware = partAwareRenderers(calls, null);
        new ConstructionProviderRegistry(ConstructionProviderRegistry.standard().entries(),
            StandardPcbFootprintProviders.createRegistry(), aware);
        check(calls[0] == 0, "part-aware registration does not call a provider");
        new PcbWorkbenchRenderer(instance, sim.getBoardModificationController(), instance.getPcbLayout(), aware);
        check(calls[0] == instance.getPhysicalBoardRuntime().getPhysicalParts().size(),
            "workbench admission supplies every actual materialized part");
        check(aware.requireRenderer(part.getPackage(), part) != null, "actual part-aware dispatch succeeds");

        final int[] failedCalls = { 0 };
        final PhysicalPartRenderRegistry emptyRenderer = partAwareRenderers(failedCalls, part.getPackage().getId());
        new ConstructionProviderRegistry(ConstructionProviderRegistry.standard().entries(),
            StandardPcbFootprintProviders.createRegistry(), emptyRenderer);
        check(failedCalls[0] == 0, "registration cannot decide an instance-dependent renderer result");
        rendererFailure(new Action() { public void run() {
            new PcbWorkbenchRenderer(instance, sim.getBoardModificationController(), instance.getPcbLayout(), emptyRenderer);
        } }, "Physical render provider returned no renderer: " + part.getPackage().getId());
        check(failedCalls[0] > 0, "no-renderer failure exercised a real supported part");
        rendererFailure(new Action() { public void run() {
            new PcbWorkbenchRenderer(instance, sim.getBoardModificationController(), instance.getPcbLayout(),
                new PhysicalPartRenderRegistry());
        } }, "No physical render provider for package: ");
        final PhysicalPackage wrong = part.getPackage().isEquivalentTo(PhysicalPackages.AXIAL_RESISTOR) ?
            PhysicalPackages.TO92_NPN : PhysicalPackages.AXIAL_RESISTOR;
        rendererFailure(new Action() { public void run() {
            aware.requireRenderer(wrong, part);
        } }, "Physical render part package mismatch: ");
        check(sim.elmList == graph && sim.getGeneratedBoardInstance() == instance && sim.pcbWorkbenchController == owner,
            "renderer admission failures do not replace the live player owner");
        publishRenderParts(calls[0] - 1);
    }

    private static PhysicalPartRenderRegistry partAwareRenderers(final int[] calls, final String missingRenderer) {
        final PhysicalPartRenderRegistry standard = StandardPhysicalPartRenderProviders.createRegistry();
        PhysicalPartRenderRegistry result = new PhysicalPartRenderRegistry();
        for (final PhysicalPackage physical : standard.getRegisteredPackages()) result.register(physical,
            new PhysicalPartRenderProvider() { public PhysicalPartRenderer getRenderer(PhysicalPart<?> part) {
                if (part == null) throw new AssertionError("A11-R1 provider was called with a nonexistent part");
                calls[0]++;
                return physical.getId().equals(missingRenderer) ? null : standard.requireRenderer(physical, part);
            } });
        return result;
    }
    private static void rendererFailure(Action action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().startsWith(message), "renderer failure identifies its boundary"); return;
        }
        catch (IllegalStateException expected) {
            check(expected.getMessage().startsWith(message), "renderer failure identifies its boundary"); return;
        }
        throw new AssertionError("A11-R1 accepted " + message);
    }
    private static native void publishRenderParts(int parts) /*-{
        $doc.documentElement.setAttribute('data-tsj-a11-r1-report', JSON.stringify({status:'PASS',
            schema:1,materializedParts:parts,nullPartCalls:0,partAwareBootstrap:true,
            missingRegistrationRejected:true,inequivalentPartRejected:true,
            nullRendererRejectedBeforeWorkbench:true,liveOwnerUnchanged:true}));
    }-*/;
    static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-a11-report", report);
    }-*/;
}
