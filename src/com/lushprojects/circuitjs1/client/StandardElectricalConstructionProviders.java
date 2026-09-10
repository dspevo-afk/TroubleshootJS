package com.lushprojects.circuitjs1.client;

import java.util.Map;

/**
 * Closed registry for the bounded provider set.  A provider owns both halves
 * of its construction contract: its pure electrical declarations and the
 * scoped CircuitJS allocation which consumes those declarations.
 */
final class StandardElectricalConstructionProviders {
    private static final String DEFAULT_LED_MODEL = "default-led";
    private static final String DEFAULT_NPN_MODEL = "default";
    private static final ElectricalConstructionProvider RESISTIVE_SOURCE =
            new ResistiveProvider(ResistiveBlockContributions.SOURCE_TYPE_ID);
    private static final ElectricalConstructionProvider RESISTIVE_LOAD =
            new ResistiveProvider(ResistiveBlockContributions.LOAD_TYPE_ID);
    private static final ElectricalConstructionProvider CONTROLLED_DRIVER =
            new NmosControlledDriverProvider();
    private static final ElectricalConstructionProvider CONTROLLED_NPN_DRIVER =
            new NpnControlledDriverProvider();
    private static final ElectricalConstructionProvider CONTROLLED_LOAD =
            new ControlledLoadProvider();
    private static final ElectricalConstructionProvider SUPPLY_PRESENT =
            new SupplyPresentProvider();

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
        if (ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.NPN_VERSION)
            return CONTROLLED_NPN_DRIVER;
        if (ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.LOAD_VERSION)
            return CONTROLLED_LOAD;
        if (SupplyPresentBlockContributions.TYPE_ID.equals(providerId) &&
                version == SupplyPresentBlockContributions.VERSION)
            return SUPPLY_PRESENT;
        throw new IllegalArgumentException("Unknown electrical construction provider " +
                providerId + "@" + version);
    }

    static ElectricalConstructionProvider resistiveSource() { return RESISTIVE_SOURCE; }
    static ElectricalConstructionProvider resistiveLoad() { return RESISTIVE_LOAD; }
    static ElectricalConstructionProvider controlledDriver() { return CONTROLLED_DRIVER; }
    static ElectricalConstructionProvider controlledNpnDriver() {
        return CONTROLLED_NPN_DRIVER;
    }
    static ElectricalConstructionProvider controlledLoad() { return CONTROLLED_LOAD; }
    static ElectricalConstructionProvider supplyPresent() { return SUPPLY_PRESENT; }

    private static void requireContribution(
            ComposedBlockContribution contribution, String providerId, int version) {
        if (contribution == null || !providerId.equals(contribution.getProviderTypeId()) ||
                version != contribution.getProviderVersion())
            throw new IllegalArgumentException("Mismatched electrical contribution");
    }

    private static void declareDescriptorChoices(
            ElectricalRealizationSpec.ContributionBuilder builder,
            ComposedBlockContribution contribution) {
        for (FunctionalBlockDescriptor.Parameter parameter :
                contribution.getDescriptor().getParameters().values())
            builder.choice("descriptor." + parameter.getId(), parameter.getValue());
    }

    private static void declareResolvedLoadChoices(
            ElectricalRealizationSpec.ContributionBuilder builder,
            ComposedBlockContribution contribution) {
        ControlledIndicatorValueSynthesis.ResolvedRecipe value =
                contribution.getResolvedValueRecipe();
        if (value == null)
            return;
        ControlledIndicatorValueSynthesis.Intent intent = value.getIntent();
        builder.choice("value.policy", FunctionalBlockDescriptor.Value.ofText(
                value.getPolicyId()));
        builder.choice("value.revision", FunctionalBlockDescriptor.Value.ofInteger(
                ControlledIndicatorValueSynthesis.VALUES_REVISION));
        builder.choice("value.catalog-entry", FunctionalBlockDescriptor.Value.ofText(
                value.getCatalogEntryId()));
        builder.choice("value.package", FunctionalBlockDescriptor.Value.ofText(
                value.getPackageId()));
        builder.choice("value.resistance-min-ohms", FunctionalBlockDescriptor.Value.ofDecimal(
                value.getResistanceMinimumOhms()));
        builder.choice("value.resistance-max-ohms", FunctionalBlockDescriptor.Value.ofDecimal(
                value.getResistanceMaximumOhms()));
        builder.choice("value.minimum-current-amps", FunctionalBlockDescriptor.Value.ofDecimal(
                value.getMinimumCurrentAmps()));
        builder.choice("value.maximum-current-amps", FunctionalBlockDescriptor.Value.ofDecimal(
                value.getMaximumCurrentAmps()));
        builder.choice("value.guarded-power-watts", FunctionalBlockDescriptor.Value.ofDecimal(
                value.getGuardedPowerWatts()));
        builder.choice("value.required-power-watts", FunctionalBlockDescriptor.Value.ofDecimal(
                value.getRequiredPowerWatts()));
        builder.choice("value.intent.source-minimum-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getSourceMinimumVolts()));
        builder.choice("value.intent.source-maximum-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getSourceMaximumVolts()));
        builder.choice("value.intent.load-minimum-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getLoadAcceptanceMinimumVolts()));
        builder.choice("value.intent.load-maximum-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getLoadAcceptanceMaximumVolts()));
        builder.choice("value.intent.sink-minimum-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getSinkMinimumVolts()));
        builder.choice("value.intent.sink-maximum-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getSinkMaximumVolts()));
        builder.choice("value.intent.sink-capacity-amps", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getSinkCapacityAmps()));
        builder.choice("value.intent.typed-demand-amps", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getTypedDemandAmps()));
        builder.choice("value.intent.target-minimum-current-amps", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getTargetMinimumCurrentAmps()));
        builder.choice("value.intent.led-minimum-forward-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getLedMinimumForwardVolts()));
        builder.choice("value.intent.led-maximum-forward-volts", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getLedMaximumForwardVolts()));
        builder.choice("value.intent.tolerance-fraction", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getModelToleranceFraction()));
        builder.choice("value.intent.power-headroom-factor", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getPowerHeadroomFactor()));
        builder.choice("value.intent.sink-headroom-factor", FunctionalBlockDescriptor.Value.ofDecimal(
                intent.getSinkHeadroomFactor()));
        builder.choice("value.intent.model", FunctionalBlockDescriptor.Value.ofText(
                intent.getModelId()));
        builder.choice("value.intent.package", FunctionalBlockDescriptor.Value.ofText(
                intent.getPackageId()));
    }

    private static double parameter(ComposedBlockContribution contribution, String id) {
        FunctionalBlockDescriptor.Parameter parameter = contribution.getDescriptor()
                .getParameters().get(id);
        if (parameter == null || parameter.getValue().getKind() ==
                FunctionalBlockDescriptor.Value.Kind.TEXT)
            throw new IllegalArgumentException("Missing numeric contribution parameter " + id);
        if (parameter.getValue().getKind() == FunctionalBlockDescriptor.Value.Kind.INTEGER)
            return parameter.getValue().getInteger();
        if (parameter.getValue().getKind() == FunctionalBlockDescriptor.Value.Kind.DECIMAL)
            return parameter.getValue().getDecimal();
        throw new IllegalArgumentException("Contribution parameter is not numeric " + id);
    }

    private static void requireContributionParameter(ComposedBlockContribution contribution,
            String id) {
        FunctionalBlockDescriptor.Parameter parameter = contribution.getDescriptor()
                .getParameters().get(id);
        if (parameter == null)
            throw new IllegalArgumentException("Missing contribution parameter " + id);
    }

    private static double choice(ElectricalRealizationSpec.ProviderDeclaration declaration,
            String elementId, String parameter) {
        return declaration.getElement(elementId).getParameter(parameter);
    }

    private static int integerChoice(ElectricalRealizationSpec.ProviderDeclaration declaration,
            String elementId, String parameter) {
        double value = choice(declaration, elementId, parameter);
        int integer = (int) value;
        if (value != integer)
            throw new IllegalArgumentException("Non-integer element choice " + elementId +
                    "/" + parameter);
        return integer;
    }

    private static void declareUnits(ElectricalConstructionContext.Scope scope,
            ComposedBlockContribution contribution) {
        for (String component : contribution.getDescriptor().getComponents().keySet())
            scope.declareUnit(component);
    }

    private static void wire(ElectricalConstructionContext.Scope scope, String id,
            Point first, Point second) {
        scope.wire(id, first.x, first.y, second.x, second.y);
    }

    private static void declareMutableResistorInfrastructure(
            ElectricalRealizationSpec.ContributionBuilder builder, String resistor) {
        builder.helper(resistor + "_FAULT_SWITCH", "SWITCH", resistor,
                ElectricalRealizationSpec.posts("1", 0, "2", 1));
        builder.helper(resistor + "_FIRST_ATTACHMENT", "WIRE", null,
                ElectricalRealizationSpec.posts("1", 0, "2", 1));
        builder.helper(resistor + "_SECOND_ATTACHMENT", "WIRE", null,
                ElectricalRealizationSpec.posts("1", 0, "2", 1));
        builder.helper(resistor + "_INPUT_TRACE", "WIRE", null,
                ElectricalRealizationSpec.posts("1", 0, "2", 1));
    }

    private static void declareMutableResistorPads(
            ElectricalRealizationSpec.ContributionBuilder builder, String resistor,
            String outputTrace) {
        builder.boardPad(resistor + ".1", resistor + "_INPUT_TRACE", "2",
                resistor + "_FIRST_ATTACHMENT");
        builder.boardPad(resistor + ".2", outputTrace, "1",
                resistor + "_SECOND_ATTACHMENT");
    }

    private static String ledModelId(String logicalModel) {
        if (logicalModel == null || logicalModel.length() == 0)
            throw new IllegalArgumentException("LED model is missing");
        if ("LED".equals(logicalModel))
            return DEFAULT_LED_MODEL;
        return logicalModel;
    }

    /** Model metadata is part of the pure electrical choice, not a runtime default. */
    private static Map<String, Double> ledParameters(String modelId) {
        DiodeModel.createModelMap();
        DiodeModel model = DiodeModel.modelMap.get(modelId);
        if (model == null)
            throw new IllegalArgumentException("Unknown LED model " + modelId);
        return ElectricalRealizationSpec.numbers(
                "flags", model.flags,
                "saturation-current", model.saturationCurrent,
                "series-resistance", model.seriesResistance,
                "emission-coefficient", model.emissionCoefficient,
                "breakdown-voltage", model.breakdownVoltage,
                "thermal-voltage", DiodeModel.vt,
                "vscale", model.vscale,
                "vdcoef", model.vdcoef,
                "forward-drop", model.fwdrop,
                "red", 1.0, "green", 0.0, "blue", 0.0);
    }

    private static Map<String, Double> npnParameters(double beta) {
        TransistorModel.createModelMap();
        TransistorModel model = TransistorModel.getModelWithName(DEFAULT_NPN_MODEL);
        if (model == null)
            throw new IllegalArgumentException("NPN default model is missing");
        return ElectricalRealizationSpec.numbers(
                "beta", beta,
                "model-flags", model.flags,
                "model-saturation-current", model.satCur,
                "model-inv-rolloff-forward", model.invRollOffF,
                "model-be-leakage-current", model.BEleakCur,
                "model-be-leakage-emission", model.leakBEemissionCoeff,
                "model-inv-rolloff-reverse", model.invRollOffR,
                "model-bc-leakage-current", model.BCleakCur,
                "model-bc-leakage-emission", model.leakBCemissionCoeff,
                "model-emission-forward", model.emissionCoeffF,
                "model-emission-reverse", model.emissionCoeffR,
                "model-inv-early-forward", model.invEarlyVoltF,
                "model-inv-early-reverse", model.invEarlyVoltR,
                "model-reverse-beta", model.betaR,
                "model-thermal-voltage", TransistorElm.vt);
    }

    private static final class ResistiveProvider implements ElectricalConstructionProvider {
        private final String providerId;

        ResistiveProvider(String providerId) { this.providerId = providerId; }
        public String getProviderId() { return providerId; }
        public int getVersion() { return ResistiveBlockContributions.VERSION; }

        public void declare(ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution) {
            requireContribution(contribution, providerId, getVersion());
            declareDescriptorChoices(builder, contribution);
            for (ComposedBlockContribution.ResistorRecipe recipe :
                    contribution.getResistors().values())
                        builder.resistor(recipe);
        }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, providerId, getVersion());
            ComposedBlockContribution contribution = declaration.getContribution();
            if (contribution == null || contribution.getResistors().isEmpty())
                throw new IllegalArgumentException("Resistive provider requires a resistor");
            int offset = 0;
            for (ComposedBlockContribution.ResistorRecipe recipe :
                    contribution.getResistors().values()) {
                int x = 260 + offset++ * 120;
                ElectricalConstructionContext.ElementHandle resistor = scope.resistor(
                        recipe.getComponentLocalId(), x, 160, x + 80, 160,
                        choice(declaration, recipe.getComponentLocalId(), "resistance"));
                ElectricalConstructionContext.SecondaryHandle secondary =
                        scope.secondaryOpenPath(recipe.getComponentLocalId() + "_SECONDARY",
                                resistor, 1);
                scope.bindComponent(recipe.getComponentLocalId(), resistor,
                        secondary.getElementHandle());
            }
            declareUnits(scope, contribution);
            return scope.finish();
        }
    }

    private static abstract class ControlledDriverProvider
            implements ElectricalConstructionProvider {
        protected abstract String controlResistorId();
        protected abstract int transistorReturnPostIndex();
        protected abstract String transistorControlTerminal();
        protected abstract String transistorSwitchedTerminal();
        protected abstract String transistorReturnTerminal();
        protected abstract void declareTransistor(
                ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution);
        protected abstract ElectricalConstructionContext.ElementHandle allocateTransistor(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope, Point controlNode);

        public void declare(ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution) {
            requireContribution(contribution, getProviderId(), getVersion());
            declareDescriptorChoices(builder, contribution);
            String control = controlResistorId();
            ComposedBlockContribution.ResistorRecipe controlRecipe =
                    contribution.getResistor(control);
            ComposedBlockContribution.ResistorRecipe pulldown =
                    contribution.getResistor("RPD");
            if (controlRecipe == null || pulldown == null || !controlRecipe.isMutable())
                throw new IllegalArgumentException("Controlled driver resistor declaration is incomplete");
            builder.resistor(controlRecipe);
            builder.resistor(pulldown);
            declareMutableResistorInfrastructure(builder, control);
            builder.helper("CONTROL_NODE_TRACE", "WIRE", null,
                    ElectricalRealizationSpec.posts("1", 0, "2", 1));
            builder.helper("CONTROL_RETURN_TRACE", "WIRE", null,
                    ElectricalRealizationSpec.posts("1", 0, "2", 1));
            declareTransistor(builder, contribution);
            declareMutableResistorPads(builder, control, "CONTROL_NODE_TRACE");
            builder.boardPad("RPD.1", "RPD", "1", null);
            builder.boardPad("RPD.2", "RPD", "2", null);
            builder.boardPad("Q1." + transistorControlTerminal(), "Q1",
                    transistorControlTerminal(), null);
            builder.boardPad("Q1." + transistorSwitchedTerminal(), "Q1",
                    transistorSwitchedTerminal(), null);
            builder.boardPad("Q1." + transistorReturnTerminal(), "Q1",
                    transistorReturnTerminal(), null);
        }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, getProviderId(), getVersion());
            ComposedBlockContribution contribution = declaration.getContribution();
            String control = controlResistorId();
            ComposedBlockContribution.ResistorRecipe controlRecipe =
                    contribution.getResistor(control);
            if (controlRecipe == null || contribution.getResistor("RPD") == null)
                throw new IllegalArgumentException("Controlled driver recipe is incomplete");
            ElectricalConstructionContext.ElementHandle resistor = scope.resistor(
                    control, 128, 96, 208, 96,
                    choice(declaration, control, "resistance"));
            Point first = scope.point(resistor, 0);
            Point second = scope.point(resistor, 1);
            ElectricalConstructionContext.ElementHandle faultSwitch = scope.switchElement(
                    control + "_FAULT_SWITCH", second.x, second.y,
                    second.x + 32, second.y);
            ElectricalConstructionContext.SecondaryHandle secondary =
                    scope.secondaryOpenPath(control + "_SECONDARY", faultSwitch, 1);
            Point secondaryPublic = scope.point(secondary.getElementHandle(), 1);
            wire(scope, control + "_INPUT_TRACE",
                    new Point(first.x - 96, first.y), new Point(first.x - 64, first.y));
            wire(scope, control + "_FIRST_ATTACHMENT",
                    new Point(first.x - 64, first.y), first);
            Point controlNode = new Point(secondaryPublic.x + 80, secondaryPublic.y);
            wire(scope, control + "_SECOND_ATTACHMENT", secondaryPublic, controlNode);

            ElectricalConstructionContext.ElementHandle pulldown = scope.resistor(
                    "RPD", controlNode.x, controlNode.y, controlNode.x,
                    controlNode.y + 80, choice(declaration, "RPD", "resistance"));
            ElectricalConstructionContext.ElementHandle transistor =
                    allocateTransistor(declaration, scope, controlNode);
            Point transistorControl = scope.point(transistor, 0);
            wire(scope, "CONTROL_NODE_TRACE", controlNode, transistorControl);
            wire(scope, "CONTROL_RETURN_TRACE", scope.point(pulldown, 1),
                    scope.point(transistor, transistorReturnPostIndex()));

            scope.bindComponent(control, resistor, secondary.getElementHandle());
            scope.bindComponent("RPD", pulldown, null);
            scope.bindComponent("Q1", transistor, null);
            declareUnits(scope, contribution);
            return scope.finish();
        }
    }

    private static final class NmosControlledDriverProvider extends ControlledDriverProvider {
        public String getProviderId() {
            return ControlledIndicatorBlockContributions.DRIVER_TYPE_ID;
        }
        public int getVersion() {
            return ControlledIndicatorBlockContributions.VERSION;
        }
        protected String controlResistorId() { return "RG"; }
        protected int transistorReturnPostIndex() { return 1; }
        protected String transistorControlTerminal() { return "G"; }
        protected String transistorSwitchedTerminal() { return "D"; }
        protected String transistorReturnTerminal() { return "S"; }

        protected void declareTransistor(ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution) {
            if (contribution.getNmosRecipes().isEmpty())
                throw new IllegalArgumentException("NMOS driver recipe is missing");
            ComposedBlockContribution.NmosRecipe recipe =
                    contribution.getNmosRecipes().get("Q1");
            if (recipe == null)
                throw new IllegalArgumentException("NMOS driver Q1 recipe is missing");
            builder.component("Q1", "NMOS", PhysicalPackages.TO92_NMOS,
                    recipe.getModelId(), ElectricalRealizationSpec.numbers(
                            "threshold", parameter(contribution, "threshold-volts"),
                            "beta", parameter(contribution, "beta")),
                    ElectricalRealizationSpec.posts("G", 0, "D", 2, "S", 1));
        }

        protected ElectricalConstructionContext.ElementHandle allocateTransistor(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope, Point controlNode) {
            return scope.nmos("Q1", controlNode.x + 160, controlNode.y,
                    controlNode.x + 240, controlNode.y,
                    choice(declaration, "Q1", "threshold"),
                    choice(declaration, "Q1", "beta"));
        }
    }

    private static final class NpnControlledDriverProvider extends ControlledDriverProvider {
        public String getProviderId() {
            return ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID;
        }
        public int getVersion() {
            return ControlledIndicatorBlockContributions.NPN_VERSION;
        }
        protected String controlResistorId() { return "RB"; }
        protected int transistorReturnPostIndex() { return 2; }
        protected String transistorControlTerminal() { return "B"; }
        protected String transistorSwitchedTerminal() { return "C"; }
        protected String transistorReturnTerminal() { return "E"; }

        protected void declareTransistor(ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution) {
            if (!contribution.getNmosRecipes().isEmpty())
                throw new IllegalArgumentException("NPN driver cannot declare an NMOS recipe");
            builder.component("Q1", "NPN", PhysicalPackages.TO92_NPN, DEFAULT_NPN_MODEL,
                    npnParameters(parameter(contribution, "beta")),
                    ElectricalRealizationSpec.posts("B", 0, "C", 1, "E", 2));
        }

        protected ElectricalConstructionContext.ElementHandle allocateTransistor(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope, Point controlNode) {
            return scope.npn("Q1", controlNode.x + 160, controlNode.y,
                    controlNode.x + 240, controlNode.y,
                    choice(declaration, "Q1", "beta"));
        }
    }

    private static final class ControlledLoadProvider
            implements ElectricalConstructionProvider {
        public String getProviderId() {
            return ControlledIndicatorBlockContributions.LOAD_TYPE_ID;
        }
        public int getVersion() {
            return ControlledIndicatorBlockContributions.LOAD_VERSION;
        }

        public void declare(ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution) {
            requireContribution(contribution, getProviderId(), getVersion());
            declareDescriptorChoices(builder, contribution);
            ComposedBlockContribution.ResistorRecipe resistor =
                    contribution.getResistor("RLOAD");
            ComposedBlockContribution.LedRecipe led =
                    contribution.getLedRecipes().get("LED1");
            if (resistor == null || led == null || !resistor.isMutable())
                throw new IllegalArgumentException("Controlled load declaration is incomplete");
            builder.resistor(resistor);
            declareMutableResistorInfrastructure(builder, "RLOAD");
            builder.helper("LOAD_LED_NODE_TRACE", "WIRE", null,
                    ElectricalRealizationSpec.posts("1", 0, "2", 1));
            String model = ledModelId(led.getModelId());
            builder.component("LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED,
                    model, ledParameters(model),
                    ElectricalRealizationSpec.posts("A", 0, "K", 1));
            declareMutableResistorPads(builder, "RLOAD", "LOAD_LED_NODE_TRACE");
            builder.boardPad("LED1.A", "LED1", "A", null);
            builder.boardPad("LED1.K", "LED1", "K", null);
            declareResolvedLoadChoices(builder, contribution);
        }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, getProviderId(), getVersion());
            ComposedBlockContribution contribution = declaration.getContribution();
            if (contribution == null || contribution.getResistor("RLOAD") == null ||
                    contribution.getLedRecipes().get("LED1") == null)
                throw new IllegalArgumentException("Controlled load recipe is incomplete");
            ElectricalConstructionContext.ElementHandle resistor = scope.resistor(
                    "RLOAD", 128, 176, 208, 176,
                    choice(declaration, "RLOAD", "resistance"));
            Point first = scope.point(resistor, 0);
            Point second = scope.point(resistor, 1);
            ElectricalConstructionContext.ElementHandle faultSwitch = scope.switchElement(
                    "RLOAD_FAULT_SWITCH", second.x, second.y, second.x + 32, second.y);
            ElectricalConstructionContext.SecondaryHandle secondary =
                    scope.secondaryOpenPath("RLOAD_SECONDARY", faultSwitch, 1);
            Point secondaryPublic = scope.point(secondary.getElementHandle(), 1);
            wire(scope, "RLOAD_INPUT_TRACE",
                    new Point(first.x - 96, first.y), new Point(first.x - 64, first.y));
            wire(scope, "RLOAD_FIRST_ATTACHMENT", new Point(first.x - 64, first.y), first);
            Point ledNode = new Point(secondaryPublic.x + 80, secondaryPublic.y);
            wire(scope, "RLOAD_SECOND_ATTACHMENT", secondaryPublic, ledNode);
            ElectricalConstructionContext.ElementHandle led = scope.led(
                    "LED1", ledNode.x + 80, ledNode.y, ledNode.x + 80, ledNode.y + 80,
                    declaration.getElement("LED1").getModelId(),
                    integerChoice(declaration, "LED1", "red"),
                    integerChoice(declaration, "LED1", "green"),
                    integerChoice(declaration, "LED1", "blue"));
            wire(scope, "LOAD_LED_NODE_TRACE", ledNode, scope.point(led, 0));
            scope.bindComponent("RLOAD", resistor, secondary.getElementHandle());
            scope.bindComponent("LED1", led, null);
            declareUnits(scope, contribution);
            return scope.finish();
        }
    }

    private static final class SupplyPresentProvider
            implements ElectricalConstructionProvider {
        public String getProviderId() { return SupplyPresentBlockContributions.TYPE_ID; }
        public int getVersion() { return SupplyPresentBlockContributions.VERSION; }

        public void declare(ElectricalRealizationSpec.ContributionBuilder builder,
                ComposedBlockContribution contribution) {
            requireContribution(contribution, getProviderId(), getVersion());
            declareDescriptorChoices(builder, contribution);
            ComposedBlockContribution.ResistorRecipe resistor =
                    contribution.getResistor(SupplyPresentBlockContributions.RSUP_COMPONENT_ID);
            ComposedBlockContribution.LedRecipe led = contribution.getLedRecipes()
                    .get(SupplyPresentBlockContributions.LED_COMPONENT_ID);
            if (resistor == null || led == null || resistor.isMutable() ||
                    contribution.getFaultSpec() != null)
                throw new IllegalArgumentException("Supply indicator declaration is incomplete");
            builder.resistor(resistor);
            builder.helper("SUPPLY_LED_NODE_TRACE", "WIRE", null,
                    ElectricalRealizationSpec.posts("1", 0, "2", 1));
            String model = ledModelId(led.getModelId());
            builder.component(SupplyPresentBlockContributions.LED_COMPONENT_ID, "LED",
                    PhysicalPackages.THROUGH_HOLE_LED, model, ledParameters(model),
                    ElectricalRealizationSpec.posts("A", 0, "K", 1));
            builder.boardPad("RSUP.1", "RSUP", "1", null);
            builder.boardPad("RSUP.2", "RSUP", "2", null);
            builder.boardPad("LED1.A", "LED1", "A", null);
            builder.boardPad("LED1.K", "LED1", "K", null);
        }

        public ContributionConstructionReceipt construct(
                ElectricalRealizationSpec.ProviderDeclaration declaration,
                ElectricalConstructionContext.Scope scope) {
            requireDeclaration(declaration, getProviderId(), getVersion());
            ComposedBlockContribution contribution = declaration.getContribution();
            ElectricalConstructionContext.ElementHandle resistor = scope.resistor(
                    SupplyPresentBlockContributions.RSUP_COMPONENT_ID, 128, 176, 208, 176,
                    choice(declaration, SupplyPresentBlockContributions.RSUP_COMPONENT_ID,
                        "resistance"));
            Point resistorSecond = scope.point(resistor, 1);
            ElectricalConstructionContext.ElementHandle led = scope.led("LED1",
                    288, 176, 288, 256, declaration.getElement("LED1").getModelId(),
                    integerChoice(declaration, "LED1", "red"),
                    integerChoice(declaration, "LED1", "green"),
                    integerChoice(declaration, "LED1", "blue"));
            Point ledAnode = scope.point(led, 0);
            wire(scope, "SUPPLY_LED_NODE_TRACE", resistorSecond, ledAnode);
            scope.bindComponent(SupplyPresentBlockContributions.RSUP_COMPONENT_ID,
                    resistor, null);
            scope.bindComponent(SupplyPresentBlockContributions.LED_COMPONENT_ID,
                    led, null);
            declareUnits(scope, contribution);
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
