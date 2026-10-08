package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Mounts physical owners onto the caller's already constructed RB56 graph. */
final class Rb56PhysicalAssembly {
    private Rb56PhysicalAssembly() { }

    /** The caller adds power/readiness contracts before GeneratedBoardInstance completes service ownership. */
    static PhysicalBoardRuntime build(final RelayOutputGenerator.Assembly a, Rb56Plan plan,
            Rb56PowerStageAssembly power, Rb56ControlTail.Result tail,
            ReplaceableRelayCapability.DischargeGuard relayGuard) {
        return build(a, plan, power, tail, relayGuard, null);
    }

    static PhysicalBoardRuntime build(final RelayOutputGenerator.Assembly a, Rb56Plan plan,
            Rb56PowerStageAssembly power, Rb56ControlTail.Result tail,
            ReplaceableRelayCapability.DischargeGuard relayGuard, GeneratedFaultBinding selectedFault) {
        if (a == null || plan == null || power == null || tail == null ||
                tail.plan != plan || relayGuard == null ||
                !a.board.getId().equals(Rb56Plan.FAMILY_ID + "_BOARD"))
            throw new IllegalArgumentException("Missing or foreign RB56 physical construction owner");
        a.requireCompleteManifest();
        registerPowerSpecifications(a, plan, power);
        requireSelectedFault(a, plan, selectedFault);
        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(a.board);
        for (Rb56Plan.Part declaration : plan.parts()) {
            String id = declaration.id;
            BoardComponent component = a.board.getComponent(id);
            if (component == null || !declaration.type.equals(component.getType()) ||
                    !declaration.physicalPackage.isEquivalentTo(component.getPhysicalPackage()))
                throw new IllegalArgumentException("RB56 physical package differs from its plan: " + id);
            PhysicalSpecification spec = a.specifications.getSpecification(id);
            PhysicalNameplate label = a.specifications.getNameplate(id);
            if (spec == null || label == null ||
                    !declaration.physicalPackage.isEquivalentTo(a.specifications.getPackage(id)))
                throw new IllegalStateException("Missing RB56 physical definition: " + id);
            Vector<CircuitElm> backing = declaration.kind == Rb56Plan.Kind.POWER ?
                power.backing(id) : singleton(tail.backing.get(id));
            requireBacking(a, declaration, backing);
            CircuitElm primary = backing.get(0);
            GeneratedFaultBinding originalFault = selectedFault != null &&
                id.equals(selectedFault.getFault().getTargetComponentId()) ? selectedFault : null;
            PhysicalBoardSlot slot = runtime.createSlot(id);
            if (spec instanceof ConverterSpecification) {
                if (!"UAC".equals(id) || !backing.equals(power.stage.module.elements()))
                    throw new IllegalStateException("RB56 converter has foreign auxiliary backing");
                PhysicalConverterPart part = new PhysicalConverterPart(id, (ConverterSpecification)spec,
                    power.stage.module, provenance(id));
                requireOuterTerminals(a, id, part);
                slot.install(part);
            } else if (spec instanceof CapacitorSpecification) {
                if (!(primary instanceof CapacitorElm) || backing.size() > 2 ||
                        backing.size() == 2 && !(backing.get(1) instanceof ResistorElm))
                    throw new IllegalStateException("RB56 capacitor has foreign series backing: " + id);
                PhysicalCapacitorPart part = new PhysicalCapacitorPart(id, (CapacitorSpecification)spec,
                    label.forPhysicalPartId(id), (CapacitorElm)primary,
                    backing.size() == 2 ? (ResistorElm)backing.get(1) : null,
                    null, CapacitorPartLocation.INSTALLED, provenance(id));
                requireOuterTerminals(a, id, part);
                slot.install(part);
            } else if (spec instanceof InductorSpecification || spec instanceof ZenerSpecification ||
                    spec instanceof OptocouplerSpecification) {
                PhysicalServicePart part = new PhysicalServicePart(id, spec, label,
                    declaration.physicalPackage, backing, provenance(id));
                requireOuterTerminals(a, id, part);
                slot.install(part);
            } else if (declaration.kind == Rb56Plan.Kind.DECISION) {
                if (primary != tail.decisions.get(declaration.channel))
                    throw new IllegalStateException("RB56 decision has foreign solver ownership: " + id);
                E04DecisionControlPart part = new E04DecisionControlPart(id + "_ORIGINAL", spec, label,
                    (E04SensorControlModel.DecisionElement)primary, provenance(id));
                requireOuterTerminals(a, id, part);
                E04DecisionControlSlot decisionSlot = new E04DecisionControlSlot(id, spec, part,
                    leads(a, declaration), slot);
                PhysicalPartInventory<E04DecisionControlPart> inventory =
                    new PhysicalPartInventory<E04DecisionControlPart>(runtime,
                        id + "_REPLACEMENTS", E04DecisionControlPart.class);
                inventory.add(part);
                runtime.registerCapability(new Rb30DecisionService(decisionSlot, inventory, part,
                    tail.decisionRail, tail.variant, tail.configuration));
            } else if (declaration.kind == Rb56Plan.Kind.RELAY) {
                if (!(spec instanceof RelaySpecification) || primary != tail.relays.get(declaration.channel))
                    throw new IllegalStateException("RB56 relay has foreign solver ownership: " + id);
                PhysicalRelayPart part = new PhysicalRelayPart(id + "_ORIGINAL",
                    (RelaySpecification)spec, (ServiceRelayElm)primary, originalFault, provenance(id));
                requireOuterTerminals(a, id, part);
                runtime.registerCapability(new ReplaceableRelayCapability("RB56_RELAY_" + id,
                    slot, part, leads(a, declaration), relayGuard));
            } else if (originalFault != null && spec instanceof ResistorNameplate) {
                ResistorNameplate intended = (ResistorNameplate)spec;
                // This is the existing closed stress boundary added by ordinary completion.
                ResistorSecondaryOpenPath secondary = ResistorSecondaryOpenPath.create(
                    new CircuitPostMeasurementEndpoint(primary, 1),
                    new ResistorSecondaryOpenPath.AllocationObserver() {
                        public void allocated(CircuitElm element) { a.elements.add(element); }
                    });
                a.components.bindAuxiliaryComponentElement(id, secondary.getSimulationElement());
                CircuitPostMeasurementEndpoint publicTerminal = secondary.getPublicTerminal();
                a.connections.completeConstructionEndpoint(id + ".2", publicTerminal);
                WireElm[] declaredLeads = leads(a, declaration);
                Point publicSecond = publicTerminal.getElement().getPost(publicTerminal.getPostIndex());
                declaredLeads[1].x = publicSecond.x; declaredLeads[1].y = publicSecond.y;
                declaredLeads[1].setPoints();
                PhysicalResistorPart part = new PhysicalResistorPart(id, intended, intended,
                    label.forPhysicalPartId(id), (ResistorElm)primary, originalFault, secondary,
                    ResistorPartLocation.INSTALLED, provenance(id));
                requireOuterTerminals(a, id, part);
                PhysicalPartInventory<PhysicalResistorPart> inventory =
                    new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                        id + "_REPLACEMENTS", PhysicalResistorPart.class);
                inventory.add(part);
                // The existing provider makes complete() retain this exact bound original.
                runtime.registerCapability(new ReplaceableResistorBoardCapability(
                    "RB56_FAULT_RESISTOR_" + id,
                    new ReplaceableComponentSlot(id, intended, part, declaredLeads[0], declaredLeads[1], slot),
                    inventory, ResistorReplacementCatalog.forSpecification(intended)));
            } else {
                requireOrdinaryServiceRecipe(declaration, spec, primary);
                slot.install(PhysicalFoundationPartFactory.fromBoardBindings(id, spec, label,
                    declaration.physicalPackage, a.board.getSimulationBindings(), primary, provenance(id)));
            }
        }
        // GeneratedBoardInstance owns the single ServiceableBoardConstruction.complete call.
        // Neither service completion nor runtime.finishConstruction belongs to this builder.
        return runtime;
    }

    private static void requireSelectedFault(RelayOutputGenerator.Assembly a,
            Rb56Plan plan, GeneratedFaultBinding selected) {
        if (selected == null) return;
        GeneratedFault fault = selected.getFault();
        Rb56Plan.Part declaration = plan.part(fault.getTargetComponentId());
        if (declaration == null || !Rb56Plan.FAMILY_ID.equals(fault.getCircuitFamilyId()) ||
                fault.getSelectionSeed() != plan.seed)
            throw new IllegalArgumentException("Foreign RB56 physical fault declaration");
        CircuitElm actual = a.components.getSingleElement(declaration.id);
        if (selected.getEffect().getValueMutationTarget() != actual ||
                !(fault.getType() == GeneratedFaultType.RESISTOR_INCORRECT_VALUE && actual instanceof ResistorElm ||
                  fault.getType() == GeneratedFaultType.RELAY_COIL_OPEN && actual instanceof ServiceRelayElm))
            throw new IllegalArgumentException("RB56 fault is not bound to its exact original backing");
    }

    private static void registerPowerSpecifications(RelayOutputGenerator.Assembly a,
            Rb56Plan plan, Rb56PowerStageAssembly power) {
        PhysicalSpecification c5 = a.specifications.getSpecification("C5");
        if (!(c5 instanceof CapacitorSpecification))
            throw new IllegalStateException("Missing shared RB56 control-tail ceramic recipe");
        CapacitorSpecification ceramic = (CapacitorSpecification)c5;
        if (!"CAP_Q60_1UF_25V_CERAMIC_BE0".equals(ceramic.getSpecificationId()) ||
                !ceramic.hasExplicitModelRecipe() || ceramic.getCapacitanceFarads() != 1e-6 ||
                ceramic.getTolerancePercent() != 10 || ceramic.getRatedVoltage() != 25 ||
                !ceramic.getPhysicalPackage().isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR) ||
                ceramic.getEsrOhms() != 0 || ceramic.getIntegrationFlags() != CapacitorElm.FLAG_BACK_EULER ||
                ceramic.getInitialVoltage() != 0)
            throw new IllegalStateException("RB56 control-tail ceramic recipe differs from the power capacitors");
        for (Rb56Plan.Part part : plan.powerParts()) {
            if ("JAC".equals(part.id)) {
                // The actual AC connector/source already supplied its public input markings.
                if (a.specifications.getSpecification(part.id) == null)
                    throw new IllegalStateException("Missing actual AC connector declaration");
                continue;
            }
            if (a.specifications.getSpecification(part.id) != null)
                throw new IllegalStateException("RB56 power specification already has another owner: " + part.id);
            CircuitElm actual = power.primary(part.id);
            PhysicalSpecification spec;
            PhysicalNameplate label;
            if ("UAC".equals(part.id)) {
                ConverterSpecification converter = new ConverterSpecification();
                converter.requireModel(power.stage.module);
                spec = converter; label = new PhysicalNameplate(part.id, "Isolated converter", "Marking", converter.label());
            } else if ("CBULK".equals(part.id) || "COUT".equals(part.id) ||
                    "CBIAS".equals(part.id) || "CFB".equals(part.id)) {
                CapacitorSpecification capacitor;
                if ("CBULK".equals(part.id)) capacitor = new CapacitorSpecification(
                    "CAP_Q60_47UF_250V_ELECTROLYTIC_BE0", .000047, 10, 250,
                    PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
                    new CapacitorNameplate("Capacitor", "47 uF / 250 V electrolytic capacitor"), 0, true, 0);
                else if ("COUT".equals(part.id)) capacitor = new CapacitorSpecification(
                    "CAP_Q60_470UF_25V_ELECTROLYTIC_ESR_100MOHM_BE0", .000470, 10, 25,
                    PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
                    new CapacitorNameplate("Capacitor", "470 uF / 25 V electrolytic capacitor"), .1, true, 0);
                else capacitor = ceramic;
                spec = capacitor; label = capacitor.getNameplate().forPhysicalPartId(part.id);
            } else if ("L1".equals(part.id)) {
                spec = InductorSpecification.STANDARD;
                label = new PhysicalNameplate(part.id, "Output inductor", "Marking", InductorSpecification.STANDARD.catalogLabel());
            } else if ("DZ1".equals(part.id)) {
                spec = ZenerSpecification.STANDARD;
                label = new PhysicalNameplate(part.id, "Feedback zener", "Marking", ZenerSpecification.STANDARD.catalogLabel());
            } else if ("UFB".equals(part.id)) {
                spec = OptocouplerSpecification.STANDARD;
                label = new PhysicalNameplate(part.id, "Feedback optocoupler", "Marking", OptocouplerSpecification.STANDARD.catalogLabel());
            } else if ("RESISTOR".equals(part.type) && actual instanceof ResistorElm) {
                double watts = "RPRIMARY".equals(part.id) ? Rb56PowerStage.PRIMARY_BLEED_MIN_WATTS : .25;
                if (((ResistorElm)actual).getResistance() != part.value)
                    throw new IllegalStateException("RB56 resistor differs from its declaration: " + part.id);
                ResistorNameplate resistor = new ResistorNameplate(part.id, part.value, 5, watts);
                spec = resistor; label = new PhysicalNameplate(part.id, "Resistor " + part.id,
                    "Marking", resistor.getDisplayValue() + " / " + watts + " W");
            } else if ("DIODE".equals(part.type) && actual.getClass() == DiodeElm.class) {
                String model = ((DiodeElm)actual).modelName;
                spec = new DiodeNameplate(part.id, "Silicon diode", model);
                label = new PhysicalNameplate(part.id, "Silicon diode", "Model", model);
            } else if ("F1".equals(part.id) && actual instanceof ProtectionFuseElm) {
                spec = FuseSpecification.E05_AC;
                label = FuseSpecification.E05_AC.nameplate(part.id);
            } else throw new IllegalStateException("Missing RB56 power physical recipe: " + part.id);
            a.specifications.addPhysicalDefinition(part.id, spec, label, part.physicalPackage);
        }
    }

    private static Vector<CircuitElm> singleton(CircuitElm element) {
        if (element == null) throw new IllegalStateException("Missing actual RB56 control-tail backing");
        Vector<CircuitElm> result = new Vector<CircuitElm>(); result.add(element); return result;
    }

    private static void requireBacking(RelayOutputGenerator.Assembly a,
            Rb56Plan.Part declaration, Vector<CircuitElm> backing) {
        if (backing == null || backing.isEmpty())
            throw new IllegalStateException("Missing RB56 package backing: " + declaration.id);
        for (CircuitElm element : backing) if (element == null || !a.elements.contains(element))
            throw new IllegalStateException("RB56 package backing is outside its actual graph: " + declaration.id);
        // External sources initially back connector declarations without being private package elements.
        if (declaration.inputId != null && !a.components.hasComponentBinding(declaration.id)) return;
        Vector<CircuitElm> auxiliary = new Vector<CircuitElm>(backing); auxiliary.remove(0);
        if (a.components.getSingleElement(declaration.id) != backing.get(0) ||
                !a.components.getAuxiliaryElements(declaration.id).equals(auxiliary))
            throw new IllegalStateException("RB56 package has incomplete declared auxiliary ownership: " + declaration.id);
    }

    private static void requireOuterTerminals(RelayOutputGenerator.Assembly a,
            String id, PhysicalPart<?> part) {
        if (a.connections.getForComponent(id).size() != part.getTerminalCount())
            throw new IllegalStateException("RB56 package requires all stationary board leads: " + id);
        for (PhysicalPartTerminal terminal : part.getTerminals()) {
            String padId = id + "." + terminal.getTerminalName();
            GeneratedComponentConnectionBinding binding = a.connections.get(id, padId);
            if (!GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), terminal.getEndpoint()) ||
                    !GeneratedComponentConnectionBindings.sameEndpoint(binding.getBoardEndpoint(),
                        a.board.getSimulationBindings().getEndpoint(padId)) ||
                    !(binding.getConnectionElement() instanceof WireElm))
                throw new IllegalStateException("RB56 package terminal differs from its actual declared lead: " + padId);
        }
    }

    private static WireElm[] leads(RelayOutputGenerator.Assembly a, Rb56Plan.Part declaration) {
        String[] terminals = declaration.terminalIds();
        WireElm[] result = new WireElm[terminals.length];
        for (int i = 0; i < result.length; i++) {
            CircuitElm connection = a.connections.get(declaration.id, declaration.id + "." + terminals[i]).getConnectionElement();
            if (!(connection instanceof WireElm) || !a.elements.contains(connection))
                throw new IllegalStateException("RB56 package has no actual detachable lead: " + declaration.id);
            result[i] = (WireElm)connection;
        }
        return result;
    }

    /** These existing owners safely complete a one-element fixed declaration during instance construction. */
    private static void requireOrdinaryServiceRecipe(Rb56Plan.Part part,
            PhysicalSpecification spec, CircuitElm primary) {
        if (spec instanceof ResistorNameplate && primary instanceof ResistorElm ||
                spec instanceof DiodeNameplate && primary.getClass() == DiodeElm.class ||
                spec instanceof LedNameplate && primary instanceof LEDElm ||
                spec instanceof NpnSpecification && primary instanceof NTransistorElm ||
                spec instanceof NmosSpecification && primary instanceof NMosfetElm ||
                spec instanceof FuseSpecification && "F1".equals(part.id) && primary instanceof ProtectionFuseElm ||
                spec instanceof BasicPhysicalSpecification &&
                    (part.inputId != null || part.kind == Rb56Plan.Kind.OUTPUT_HEADER ||
                        part.kind == Rb56Plan.Kind.REGULATOR && primary instanceof LinearRegulatorElm)) return;
        throw new IllegalStateException("RB56 package has no existing ordinary service owner: " + part.id);
    }

    private static PhysicalPartProvenance provenance(String id) {
        return new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, id);
    }
}
