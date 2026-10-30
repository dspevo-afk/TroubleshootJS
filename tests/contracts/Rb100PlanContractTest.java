package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/** Independent exact-inventory contract; it does not construct or power CircuitJS elements. */
public final class Rb100PlanContractTest {
    private static int assertions;

    public static void main(String[] args) {
        Rb100Plan shared = Rb100Plan.create(123456789012345L,
            Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR);
        Rb100Plan sharedAgain = Rb100Plan.create(123456789012345L,
            Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR);
        Rb100Plan separate = Rb100Plan.create(123456789012345L,
            Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING);
        verifyIdentityAndVariants(shared, sharedAgain, separate);
        verifyInventory(shared);
        verifyInventory(separate);
        verifyBaseImportOverrides(shared);
        verifyBaseImportOverrides(separate);
        verifyElectricalDeclarations(shared);
        verifyElectricalDeclarations(separate);
        verifyDefensiveCopies(shared);
        verifyRejectsInvalidLookups(shared);
        verifyExactLongSeeds();
        verifyPlayerCatalogIsolation();
        System.out.println("PASS: RB100 plan contracts assertions=" + assertions +
            " packages=100 variants=2 scope=INVENTORY_AND_DECLARATION_ONLY");
    }

    private static void verifyIdentityAndVariants(Rb100Plan shared, Rb100Plan sharedAgain,
            Rb100Plan separate) {
        require(Rb100Plan.FAMILY_ID.equals("RB100_CONTROL_PILOT") &&
                Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                Rb100Plan.CIN33_ESR_OHMS == 1 && Rb100Plan.PACKAGE_COUNT == 100,
            "The private family, schema version and package target are fixed");
        require(shared.seed == 123456789012345L && shared.variant ==
                Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR &&
                shared.canonical().equals(sharedAgain.canonical()),
            "Exact signed-long seed and repeated variant produce one canonical identity");
        NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            shared.seed, Rb100Plan.FAMILY_ID, Rb100Plan.PLAN_VERSION);
        long expectedBaseSeed = streams.deviceSeed(NamedRandomStreams.Concern.TOPOLOGY, 1,
            "base-control");
        require(shared.base.seed == expectedBaseSeed && shared.base.channelCount == 2 &&
                shared.base.physicalPackageCount() == 51 &&
                shared.base.referenceArrangement == Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC &&
                !shared.base.hasFiveVoltIndicator && !shared.base.hasTwelveVoltIndicator &&
                shared.base.sensorInputFilterMask == 3 && shared.base.outputIndicatorMask == 0 &&
                !shared.base.hasFiveVoltBleeder,
            "The 51-package base is exactly the configured two-channel shared-hysteretic RB56 plan");
        require(separate.base.seed == expectedBaseSeed && separate.base.channelCount == 2 &&
                separate.base.referenceArrangement == Rb56Plan.ReferenceArrangement.SEPARATE_DIRECT,
            "The separate/direct variant changes only its declared base reference arrangement");
        require(shared.driver("C").getId().equals("NMOS") &&
                shared.driver("D").getId().equals("NMOS") &&
                separate.driver("C").getId().equals("NMOS") &&
                separate.driver("D").getId().equals("NMOS"),
            "C and D use the fixed declared NMOS driver model in both variants");
        require(shared.driver("A").getId().equals(shared.base.driver("A").getId()) &&
                shared.driver("B").getId().equals(shared.base.driver("B").getId()) &&
                shared.driver("E").getId().equals("NMOS") && shared.driver("F").getId().equals("NMOS") &&
                shared.driver("AUX").getId().equals("NMOS") && shared.driver("INV").getId().equals("BJT"),
            "Fixed and inherited driver declarations preserve their assigned model choices");
        require(shared.referenceNet("A").equals(shared.base.referenceNet("A")) &&
                shared.referenceNet("B").equals(shared.base.referenceNet("B")) &&
                shared.referenceNet("C").equals(shared.base.referenceNet("A")) &&
                shared.referenceNet("D").equals(shared.base.referenceNet("B")) &&
                separate.referenceNet("C").equals(separate.base.referenceNet("A")) &&
                separate.referenceNet("D").equals(separate.base.referenceNet("B")),
            "C and D inherit the intended A and B reference nodes in both structural alternatives");
        require(!shared.canonical().equals(separate.canonical()) &&
                shared.physicalPackageCount() == separate.physicalPackageCount() &&
                shared.parts().size() == 100 && shared.tailParts().size() == 80,
            "The two declared alternatives are distinct and retain a 100-package/80-tail census");
        String canonical = shared.canonical();
        for (String contract : new String[] {
                "LOAD12:12V@0.5A", "SENSORS:0V/5V@0.25A",
                "RELAY-LOADS:4x180ohm", "DC-LOADS:2x(180ohm+10mH)",
                "MONITOR:1ohm", "MONITOR-FILTER:10kohm*100nF=1ms",
                "MON-REF:19kohm/1kohm", "driver-C-D=NMOS",
                "RB100-SOURCE-MODEL=FAMILY_SCOPED", "GLOBAL-DEFAULT=0.25A-UNCHANGED",
                "load-topology=shared-LOAD12-positive+CTRL_RETURN-common-non-isolated+E-F-low-side" })
            require(canonical.contains(contract), "Canonical identity pins model bound: " + contract);
        require(canonical.contains(";electrical-model=4;CIN33-series-ESR=1.0;"),
            "Explicit capacitor ESR model revision does not alter topology seed derivation");
    }

    private static void verifyInventory(Rb100Plan plan) {
        String[] addedIds = {
            "JSC", "U2C", "RSC", "RPIN_C", "CFLT_C", "RDC", "RPD_C", "QC", "DC", "KC",
            "JSD", "U2D", "RSD", "RPIN_D", "CFLT_D", "RDD", "RPD_D", "QD", "DD", "KD",
            "RDE", "RPD_E", "QE", "DE", "RSH_E", "CSTORE_E",
            "RDF", "RPD_F", "QF", "DF", "RSH_F", "CSTORE_F",
            "U33", "CIN33", "C33",
            "RMON", "RMON_IN", "UMON", "CMON", "RMON_H", "RMON_L",
            "RBAUX", "RPUAUX", "QINV", "QAUX", "JOC", "JOD", "JOE", "JOF"
        };
        Set<String> expected = new HashSet<String>();
        for (Rb56Plan.Part part : plan.base.parts()) expected.add(part.id);
        for (String id : addedIds) require(expected.add(id), "Independent fixture has a unique addition ID: " + id);
        require(addedIds.length == 49 && plan.base.physicalPackageCount() == 51 &&
                expected.size() == Rb100Plan.PACKAGE_COUNT && plan.physicalPackageCount() == 100,
            "Independent base-plus-49 census equals exactly 100 packages");

        TroubleshootBoard board = plan.board();
        require((Rb100Plan.FAMILY_ID + "_BOARD").equals(board.getId()) &&
                board.getComponentIds().size() == 100 && board.getPowerInputIds().size() == 6,
            "The declaration-only board includes all 100 packages and six declared external inputs");
        Set<String> actual = new HashSet<String>();
        for (Rb100Plan.Part part : plan.parts()) {
            require(actual.add(part.id) && expected.contains(part.id),
                "Every part is unique and belongs to the independently listed inventory: " + part.id);
            require(part.purpose != null && part.purpose.length() > 0 &&
                    part.physicalPackage.getTerminalCount() == part.terminalIds().length,
                "Every package carries a purpose and its actual package terminal census: " + part.id);
            BoardComponent component = board.getComponent(part.id);
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            require(component != null && part.type.equals(component.getType()) &&
                    component.getPhysicalPackage().isEquivalentTo(part.physicalPackage) &&
                    component.getPadIds().size() == terminals.length,
                "One plan declaration supplies its actual board package and pad population: " + part.id);
            for (int index = 0; index < terminals.length; index++) {
                String padId = part.id + "." + terminals[index];
                BoardPad pad = board.getPad(padId);
                require(pad != null && part.id.equals(pad.getComponentId()) &&
                        terminals[index].equals(pad.getTerminalId()) && nets[index].equals(pad.getNetId()) &&
                        component.getPadIds().contains(padId),
                    "Every typed terminal maps to its independent net declaration: " + padId);
            }
        }
        require(actual.equals(expected), "The actual package IDs equal the complete base plus addition manifest");
        Vector<Rb100Plan.Part> ordered = plan.parts();
        int orderIndex = 0;
        for (Rb56Plan.Part part : plan.base.parts())
            require(ordered.get(orderIndex++).id.equals(part.id),
                "The imported RB56 package order remains unchanged: " + part.id);
        for (String id : addedIds)
            require(ordered.get(orderIndex++).id.equals(id),
                "The 49-package model addition order remains unchanged: " + id);
        require(orderIndex == ordered.size(), "The complete package order has no extra entries");
        for (String netId : board.getNetIds()) {
            BoardNet net = board.getNet(netId);
            require(net != null && !net.getPadIds().isEmpty(), "Every declared board net has a physical pad: " + netId);
            for (String padId : net.getPadIds())
                require(netId.equals(board.getPad(padId).getNetId()), "Net-to-pad identity is reciprocal: " + padId);
        }
        Set<String> expectedInputs = new HashSet<String>(Arrays.asList(
            new String[] {"MAINAC", "LOAD12", "SENSOR_A", "SENSOR_B", "SENSOR_C", "SENSOR_D"}));
        require(expectedInputs.equals(new HashSet<String>(board.getPowerInputIds())),
            "Only the declared AC, load and four customer sensor inputs are exposed");
        verifyInput(board, "SENSOR_C", "JSC.1", "JSC.2", "C_RAW", "CTRL_RETURN");
        verifyInput(board, "SENSOR_D", "JSD.1", "JSD.2", "D_RAW", "CTRL_RETURN");
        verifyInput(board, "LOAD12", "JLOAD.1", "JLOAD.2", "LOAD12", "CTRL_RETURN");
        require(plan.base != null,
            "The declaration retains its typed base plan without importing placement demand");
    }

    private static void verifyBaseImportOverrides(Rb100Plan plan) {
        String[] overrideParts = {"JLOAD", "JOA", "JOB"};
        int overrides = 0;
        for (Rb56Plan.Part source : plan.base.parts()) {
            Rb100Plan.Part imported = plan.part(source.id);
            require(source.type.equals(imported.type) && source.kind.name().equals(imported.kind.name()) &&
                    source.physicalPackage.isEquivalentTo(imported.physicalPackage) &&
                    source.value == imported.value && same(source.channel, imported.channel) &&
                    same(source.inputId, imported.inputId),
                "Imported base package fields remain unchanged: " + source.id);
            String[] sourceTerminals = source.terminalIds();
            String[] importedTerminals = imported.terminalIds();
            String[] sourceNets = source.netIds();
            String[] importedNets = imported.netIds();
            require(Arrays.equals(sourceTerminals, importedTerminals),
                "Imported base terminal identities remain unchanged: " + source.id);
            for (int index = 0; index < sourceTerminals.length; index++) {
                boolean expectedOverride = contains(overrideParts, source.id) &&
                    "2".equals(sourceTerminals[index]);
                if (expectedOverride) {
                    overrides++;
                    require("LOAD_RETURN".equals(sourceNets[index]) &&
                            "CTRL_RETURN".equals(importedNets[index]),
                        "Only the named load-return terminal is remapped: " + source.id + "." + sourceTerminals[index]);
                } else {
                    require(sourceNets[index].equals(importedNets[index]),
                        "Every other imported base pad retains its original net: " + source.id + "." + sourceTerminals[index]);
                }
            }
        }
        require(overrides == 3, "Exactly JLOAD.2, JOA.2 and JOB.2 are imported return-net overrides");
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) if (candidate.equals(value)) return true;
        return false;
    }

    private static boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static void verifyElectricalDeclarations(Rb100Plan plan) {
        TroubleshootBoard board = plan.board();
        for (String channel : new String[] {"C", "D"}) {
            String raw = channel + "_RAW", sense = channel + "_SENSE";
            String command = channel + "_CMD", drive = channel + "_DRIVE";
            pin(board, "JS" + channel + ".1", raw);
            pin(board, "JS" + channel + ".2", "CTRL_RETURN");
            pin(board, "U2" + channel + ".SENSOR", sense);
            pin(board, "U2" + channel + ".REFERENCE", plan.referenceNet(channel));
            pin(board, "U2" + channel + ".RAIL", "RAIL5");
            pin(board, "U2" + channel + ".OUTPUT", command);
            pin(board, "U2" + channel + ".RETURN", "CTRL_RETURN");
            pin(board, "RS" + channel + ".1", raw);
            pin(board, "RS" + channel + ".2", sense);
            pin(board, "RPIN_" + channel + ".1", sense);
            pin(board, "RPIN_" + channel + ".2", "CTRL_RETURN");
            pin(board, "CFLT_" + channel + ".1", sense);
            pin(board, "CFLT_" + channel + ".2", "CTRL_RETURN");
            pin(board, "RD" + channel + ".1", command);
            pin(board, "RD" + channel + ".2", drive);
            pin(board, "RPD_" + channel + ".1", drive);
            pin(board, "RPD_" + channel + ".2", "CTRL_RETURN");
            RelayDriverProvider driver = plan.driver(channel);
            String[] terminals = driver.getTerminals();
            pin(board, "Q" + channel + "." + terminals[0], drive);
            pin(board, "Q" + channel + "." + terminals[1], channel + "_COIL_LOW");
            pin(board, "Q" + channel + "." + terminals[2], "AUX_RETURN");
            pin(board, "D" + channel + ".A", channel + "_COIL_LOW");
            pin(board, "D" + channel + ".K", "RAIL5");
            pin(board, "K" + channel + ".A1", "RAIL5");
            pin(board, "K" + channel + ".A2", channel + "_COIL_LOW");
            pin(board, "K" + channel + ".COM", "LOAD12");
            pin(board, "K" + channel + ".NC", "NC_" + channel);
            pin(board, "K" + channel + ".NO", "OUT_" + channel);
        }
        switchedOutput(board, "E", "C_CMD");
        switchedOutput(board, "F", "D_CMD");

        pin(board, "U33.INPUT", "RAIL12"); pin(board, "U33.OUTPUT", "RAIL3V3");
        pin(board, "U33.RETURN", "CTRL_RETURN"); pin(board, "U33.ENABLE", "RAIL12");
        pin(board, "CIN33.+", "RAIL12"); pin(board, "CIN33.-", "CTRL_RETURN");
        pin(board, "C33.1", "RAIL3V3"); pin(board, "C33.2", "CTRL_RETURN");
        pin(board, "RMON.1", "MON_SENSE"); pin(board, "RMON.2", "CTRL_RETURN");
        pin(board, "RMON_IN.1", "MON_SENSE"); pin(board, "RMON_IN.2", "MON_FILTER");
        pin(board, "UMON.SENSOR", "MON_FILTER"); pin(board, "UMON.REFERENCE", "MON_REF");
        pin(board, "UMON.RAIL", "RAIL3V3"); pin(board, "UMON.OUTPUT", "MON_CMD");
        pin(board, "UMON.RETURN", "CTRL_RETURN");
        pin(board, "CMON.1", "MON_FILTER"); pin(board, "CMON.2", "CTRL_RETURN");
        pin(board, "RMON_H.1", "RAIL3V3"); pin(board, "RMON_H.2", "MON_REF");
        pin(board, "RMON_L.1", "MON_REF"); pin(board, "RMON_L.2", "CTRL_RETURN");
        require(plan.part("RMON").value == 1 && plan.part("RMON_H").value == 19000 &&
                plan.part("RMON_L").value == 1000 && plan.part("RMON_IN").value == 10000 &&
                plan.part("CMON").value == 100e-9 &&
                plan.part("RMON_IN").purpose.equals("MONITOR_FILTER_SERIES_RESISTOR") &&
                plan.part("CMON").purpose.equals("MONITOR_FILTER") &&
                Math.abs(plan.part("RMON_IN").value * plan.part("CMON").value - 0.001) < 1e-15 &&
                Rb100Plan.MONITOR_FILTER_SECONDS == 0.001,
            "Independent monitor shunt, 10k/100nF 1ms filter and 19k/1k threshold are retained");

        pin(board, "RBAUX.1", "MON_CMD"); pin(board, "RBAUX.2", "AUX_BASE");
        pin(board, "RPUAUX.1", "RAIL5"); pin(board, "RPUAUX.2", "AUX_GATE");
        pin(board, "QINV.B", "AUX_BASE"); pin(board, "QINV.C", "AUX_GATE");
        pin(board, "QINV.E", "CTRL_RETURN");
        pin(board, "QAUX.G", "AUX_GATE"); pin(board, "QAUX.D", "AUX_RETURN");
        pin(board, "QAUX.S", "CTRL_RETURN");
        require(!"CTRL_RETURN".equals(board.getPad("QC." + plan.driver("C").getTerminals()[2]).getNetId()) &&
                !"CTRL_RETURN".equals(board.getPad("QD." + plan.driver("D").getTerminals()[2]).getNetId()) &&
                !"CTRL_RETURN".equals(board.getNet("AUX_RETURN").getId()) &&
                board.getNet("GND") == null && board.getNet("AUX_GND") == null,
            "C/D coil returns remain an interlocked distinct net without a secondary ground declaration");
        require(board.getNet("LOAD_RETURN") == null,
            "No obsolete load-return island remains after the external return joins CTRL_RETURN");

        pin(board, "JLOAD.1", "LOAD12"); pin(board, "JLOAD.2", "CTRL_RETURN");
        pin(board, "JOA.1", "OUT_A"); pin(board, "JOA.2", "CTRL_RETURN");
        pin(board, "JOB.1", "OUT_B"); pin(board, "JOB.2", "CTRL_RETURN");
        pin(board, "JOC.1", "OUT_C"); pin(board, "JOC.2", "CTRL_RETURN");
        pin(board, "JOD.1", "OUT_D"); pin(board, "JOD.2", "CTRL_RETURN");
        pin(board, "JOE.1", "LOAD12"); pin(board, "JOE.2", "OUT_E");
        pin(board, "JOF.1", "LOAD12"); pin(board, "JOF.2", "OUT_F");
        require(plan.part("JLOAD").purpose.equals("EXTERNAL_DC_LOAD_BUS_INPUT") &&
                plan.part("JOA").purpose.equals("EXTERNAL_DC_LOAD_BUS_RELAY_OUTPUT_INTERFACE") &&
                plan.part("JOB").purpose.equals("EXTERNAL_DC_LOAD_BUS_RELAY_OUTPUT_INTERFACE") &&
                plan.part("JOC").purpose.equals("EXTERNAL_DC_LOAD_BUS_RESISTIVE_OUTPUT_INTERFACE") &&
                plan.part("JOD").purpose.equals("EXTERNAL_DC_LOAD_BUS_RESISTIVE_OUTPUT_INTERFACE") &&
                plan.part("JOE").purpose.equals("EXTERNAL_DC_LOAD_BUS_R_PLUS_L_OUTPUT_INTERFACE") &&
                plan.part("JOF").purpose.equals("EXTERNAL_DC_LOAD_BUS_R_PLUS_L_OUTPUT_INTERFACE"),
            "External interfaces identify the DC load bus and each output load type accurately");
        for (String id : new String[] {"JOC", "JOD", "JOE", "JOF"})
            require(plan.part(id).value == Rb100Plan.EXTERNAL_LOAD_OHMS &&
                    plan.part(id).physicalPackage == PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
                "Each physical output header declares the actual external 180-ohm load contract: " + id);
        require(Rb100Plan.EXTERNAL_LOAD_HENRIES == 0.01 &&
                Rb100Plan.LOAD12_VOLTS == 12 && Rb100Plan.LOAD12_MAX_AMPS == 0.5 &&
                Rb100Plan.SENSOR_LOW_VOLTS == 0 && Rb100Plan.SENSOR_HIGH_VOLTS == 5 &&
                Rb100Plan.SENSOR_MAX_AMPS == 0.25,
            "Family-scoped source and R+L model parameters match the canonical contract");
    }

    private static void switchedOutput(TroubleshootBoard board, String channel, String command) {
        pin(board, "RD" + channel + ".1", command);
        pin(board, "RD" + channel + ".2", channel + "_DRIVE");
        pin(board, "RPD_" + channel + ".1", channel + "_DRIVE");
        pin(board, "RPD_" + channel + ".2", "MON_SENSE");
        pin(board, "Q" + channel + ".G", channel + "_DRIVE");
        pin(board, "Q" + channel + ".D", channel + "_DRAIN");
        pin(board, "Q" + channel + ".S", "MON_SENSE");
        pin(board, "D" + channel + ".A", "OUT_" + channel);
        pin(board, "D" + channel + ".K", "LOAD12");
        pin(board, "RSH_" + channel + ".1", "OUT_" + channel);
        pin(board, "RSH_" + channel + ".2", channel + "_DRAIN");
        pin(board, "CSTORE_" + channel + ".1", "LOAD12");
        pin(board, "CSTORE_" + channel + ".2", "OUT_" + channel);
        require(board.getComponent("Q" + channel).getPhysicalPackage() == PhysicalPackages.TO92_NMOS &&
                board.getComponent("CSTORE_" + channel).getPhysicalPackage() ==
                    PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "E/F use their declared NMOS and ceramic storage packages: " + channel);
    }

    private static void verifyDefensiveCopies(Rb100Plan plan) {
        Vector<Rb100Plan.Part> parts = plan.parts();
        parts.clear();
        require(plan.parts().size() == 100, "The returned package vector cannot clear plan inventory");
        Vector<Rb100Plan.Part> tail = plan.tailParts();
        tail.clear();
        require(plan.tailParts().size() == 80, "The returned tail vector cannot clear plan inventory");
        Rb100Plan.Part part = plan.part("JSC");
        String original = part.netIds()[0];
        String[] nets = part.netIds();
        nets[0] = "MUTATED";
        String[] terminals = part.terminalIds();
        terminals[0] = "MUTATED";
        require(original.equals(part.netIds()[0]) && "1".equals(part.terminalIds()[0]),
            "Returned terminal and net arrays cannot mutate the declaration");
    }

    private static void verifyRejectsInvalidLookups(Rb100Plan plan) {
        boolean rejected = false;
        try { Rb100Plan.create(0, null); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "A missing structural variant is rejected");
        rejected = false;
        try { plan.part("UNKNOWN_PACKAGE"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Unknown package lookup is rejected");
        rejected = false;
        try { plan.part(null); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Null package lookup is rejected");
        for (String channel : new String[] {null, "G", "E"}) {
            rejected = false;
            try { plan.referenceNet(channel); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "Unknown reference channel is rejected: " + channel);
        }
    }

    private static void verifyExactLongSeeds() {
        long[] seeds = {Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993L,
            9007199254740993L, 0L, -1L};
        for (long seed : seeds) {
            Rb100Plan plan = Rb100Plan.create(seed, Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING);
            require(plan.seed == seed && plan.canonical().contains(";seed=" + Long.toString(seed) + ";"),
                "Canonical identity preserves exact signed-long seed " + Long.toString(seed));
        }
    }

    private static void verifyPlayerCatalogIsolation() {
        require(!PlayerFamilyCatalog.isRegistered(Rb100Plan.FAMILY_ID) &&
                !PlayerFamilyCatalog.families().contains(Rb100Plan.FAMILY_ID),
            "The private RB100 plan is absent from both installed and enabled player catalogs");
    }

    private static void verifyInput(TroubleshootBoard board, String id, String positivePad,
            String returnPad, String positiveNet, String returnNet) {
        ExternalBoardPowerInput input = board.getPowerInput(id);
        require(input != null && positivePad.equals(input.getPositivePadId()) &&
                returnPad.equals(input.getReturnPadId()) && positiveNet.equals(input.getPositiveNetId()) &&
                returnNet.equals(input.getReturnNetId()), "External source terminals retain their actual board mapping: " + id);
    }

    private static void pin(TroubleshootBoard board, String padId, String expectedNet) {
        BoardPad pad = board.getPad(padId);
        require(pad != null && expectedNet.equals(pad.getNetId()),
            "Independent expected net for " + padId + " is " + expectedNet);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
