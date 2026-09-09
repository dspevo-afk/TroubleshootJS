package com.lushprojects.circuitjs1.client;

/**
 * Closed registry for the bounded provider set.  The IDs in this registry are
 * construction identities; durable contribution identities remain in the
 * resolved plan and are never inferred from a family switch here.
 */
final class StandardElectricalConstructionProviders {
    private static final ElectricalConstructionProvider RESISTIVE_SOURCE =
            new ResistiveProvider(ResistiveBlockContributions.SOURCE_TYPE_ID, true);
    private static final ElectricalConstructionProvider RESISTIVE_LOAD =
            new ResistiveProvider(ResistiveBlockContributions.LOAD_TYPE_ID, false);
    private static final ElectricalConstructionProvider CONTROLLED_DRIVER =
            new ControlledDriverProvider();
    private static final ElectricalConstructionProvider CONTROLLED_LOAD =
            new ControlledLoadProvider(ControlledIndicatorBlockContributions.VERSION);
    private static final ElectricalConstructionProvider CONTROLLED_VALUE_LOAD =
            new ControlledLoadProvider(ControlledIndicatorBlockContributions.VALUE_LOAD_VERSION);

    private StandardElectricalConstructionProviders() { }

    static ElectricalConstructionProvider provider(String providerId, int version) {
        if (ResistiveBlockContributions.SOURCE_TYPE_ID.equals(providerId) &&
                version == ResistiveBlockContributions.VERSION)
            return RESISTIVE_SOURCE;
        if (ResistiveBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ResistiveBlockContributions.VERSION)
            return RESISTIVE_LOAD;
        if (ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.VERSION)
            return CONTROLLED_DRIVER;
        if (ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.VERSION)
            return CONTROLLED_LOAD;
        if (ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.VALUE_LOAD_VERSION)
            return CONTROLLED_VALUE_LOAD;
        throw new IllegalArgumentException("Unknown electrical construction provider " +
                providerId + "@" + version);
    }

    static ElectricalConstructionProvider resistiveSource() { return RESISTIVE_SOURCE; }
    static ElectricalConstructionProvider resistiveLoad() { return RESISTIVE_LOAD; }
    static ElectricalConstructionProvider controlledDriver() { return CONTROLLED_DRIVER; }
    static ElectricalConstructionProvider controlledLoad() { return CONTROLLED_LOAD; }

    private static final class ResistiveProvider implements ElectricalConstructionProvider {
        private final String providerId;
        private final boolean source;

        ResistiveProvider(String providerId, boolean source) {
            this.providerId = providerId;
            this.source = source;
        }

        public String getProviderId() { return providerId; }
        public int getVersion() { return ResistiveBlockContributions.VERSION; }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, providerId, getVersion());
            ComposedBlockContribution.ResistorRecipe recipe =
                    declaration.getContribution().getResistor("R1");
            if (recipe == null)
                throw new IllegalArgumentException("Resistive provider requires R1 recipe");
            ElectricalConstructionContext.ElementHandle resistor = scope.resistor(
                    "R1", 260, 160, 340, 160, recipe.getResistanceOhms());
            ElectricalConstructionContext.SecondaryHandle secondary =
                    scope.secondaryOpenPath("R1_SECONDARY", resistor, 1);
            scope.bindComponent("R1", resistor, secondary.getElementHandle());
            scope.declareUnit("R1");
            return scope.finish();
        }
    }

    private static final class ControlledDriverProvider
            implements ElectricalConstructionProvider {
        private static final double NMOS_THRESHOLD_VOLTS = ElectricalRealizationSpec.CONTROLLED_NMOS_THRESHOLD_VOLTS;
        private static final double NMOS_BETA = ElectricalRealizationSpec.CONTROLLED_NMOS_BETA;

        public String getProviderId() {
            return ControlledIndicatorBlockContributions.DRIVER_TYPE_ID;
        }
        public int getVersion() { return ControlledIndicatorBlockContributions.VERSION; }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, getProviderId(), getVersion());
            ComposedBlockContribution contribution = declaration.getContribution();
            ComposedBlockContribution.ResistorRecipe rgRecipe = contribution.getResistor("RG");
            ComposedBlockContribution.ResistorRecipe rpdRecipe = contribution.getResistor("RPD");
            ComposedBlockContribution.NmosRecipe q1Recipe =
                    contribution.getNmosRecipes().get("Q1");
            if (rgRecipe == null || rpdRecipe == null || q1Recipe == null)
                throw new IllegalArgumentException("Controlled driver recipe is incomplete");

            ElectricalConstructionContext.ElementHandle rg = scope.resistor(
                    "RG", 432, 96, 512, 96, rgRecipe.getResistanceOhms());
            Point rgPost = scope.point(rg, 1);
            ElectricalConstructionContext.ElementHandle rgFault = scope.switchElement(
                    "RG_FAULT_SWITCH", rgPost.x, rgPost.y, rgPost.x + 32, rgPost.y);
            ElectricalConstructionContext.SecondaryHandle rgSecondary =
                    scope.secondaryOpenPath("RG_SECONDARY", rgFault, 1);
            scope.wire("RG_FIRST_ATTACHMENT", 368, 96, 432, 96);
            Point rgPublic = scope.point(rgSecondary.getElementHandle(), 1);
            scope.wire("RG_SECOND_ATTACHMENT", rgPublic.x, rgPublic.y, 640, 96);
            ElectricalConstructionContext.ElementHandle rpd = scope.resistor(
                    "RPD", 640, 96, 640, 176, rpdRecipe.getResistanceOhms());
            ElectricalConstructionContext.ElementHandle q1 = scope.nmos(
                    "Q1", 720, 288, 800, 288,
                    NMOS_THRESHOLD_VOLTS, NMOS_BETA);
            Point gate = scope.point(q1, 0);
            scope.wire("GATE_NODE_TRACE", 640, 96, gate.x, gate.y);

            scope.bindComponent("RG", rg, rgSecondary.getElementHandle());
            scope.bindComponent("RPD", rpd, null);
            scope.bindComponent("Q1", q1, null);
            scope.declareUnit("RG");
            scope.declareUnit("RPD");
            scope.declareUnit("Q1");
            return scope.finish();
        }
    }

    private static final class ControlledLoadProvider
            implements ElectricalConstructionProvider {
        private static final String LOGICAL_LED_MODEL = "LED";
        private static final String CIRCUITJS_LED_MODEL = ElectricalRealizationSpec.CONTROLLED_LED_MODEL;
        private final int version;

        ControlledLoadProvider(int version) { this.version = version; }
        public String getProviderId() {
            return ControlledIndicatorBlockContributions.LOAD_TYPE_ID;
        }
        public int getVersion() { return version; }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, getProviderId(), getVersion());
            ComposedBlockContribution contribution = declaration.getContribution();
            ComposedBlockContribution.ResistorRecipe loadRecipe =
                    contribution.getResistor("RLOAD");
            ComposedBlockContribution.LedRecipe ledRecipe =
                    contribution.getLedRecipes().get("LED1");
            if (loadRecipe == null || ledRecipe == null)
                throw new IllegalArgumentException("Controlled load recipe is incomplete");
            ElectricalConstructionContext.ElementHandle rload = scope.resistor(
                    "RLOAD", 340, 176, 420, 176, loadRecipe.getResistanceOhms());
            Point rloadPost = scope.point(rload, 1);
            ElectricalConstructionContext.ElementHandle rloadFault = scope.switchElement(
                    "RLOAD_FAULT_SWITCH", rloadPost.x, rloadPost.y,
                    rloadPost.x + 32, rloadPost.y);
            ElectricalConstructionContext.SecondaryHandle rloadSecondary =
                    scope.secondaryOpenPath("RLOAD_SECONDARY", rloadFault, 1);
            Point rloadPublic = scope.point(rloadSecondary.getElementHandle(), 1);
            scope.wire("RLOAD_SECOND_ATTACHMENT", rloadPublic.x, rloadPublic.y, 540, 176);
            scope.wire("RLOAD_FIRST_ATTACHMENT", 280, 176, 340, 176);
            scope.wire("LOAD_NODE_TRACE", 540, 176, 620, 176);
            String model = LOGICAL_LED_MODEL.equals(ledRecipe.getModelId()) ?
                    CIRCUITJS_LED_MODEL : ledRecipe.getModelId();
            if (model == null || model.length() == 0)
                throw new IllegalArgumentException("Controlled LED model is missing");
            ElectricalConstructionContext.ElementHandle led = scope.led(
                    "LED1", 620, 176, 620, 256, model, 1, 0, 0);
            scope.bindComponent("RLOAD", rload, rloadSecondary.getElementHandle());
            scope.bindComponent("LED1", led, null);
            scope.declareUnit("RLOAD");
            scope.declareUnit("LED1");
            return scope.finish();
        }
    }

    private static void requireDeclaration(
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            String providerId, int version) {
        if (declaration == null || !providerId.equals(declaration.getProviderId()) ||
                version != declaration.getProviderVersion() || declaration.getContribution() == null)
            throw new IllegalArgumentException("Mismatched electrical provider declaration");
    }
}
