package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Private RB100 physical-domain intent; it grants no player or P09 admission. */
final class Rb100Placement {
    static final int INTENT_VERSION = 9;
    static final String PHYSICAL_POLICY_ID = MediumBoardPhysicalPolicy.ID;
    static final int PHYSICAL_POLICY_VERSION = MediumBoardPhysicalPolicy.VERSION;
    static final int PLACEMENT_DENSITY_VERSION = 14;
    static final boolean REQUIRES_TWO_LAYER_ROUTING = true;
    static final PcbTraceWidthProfile TRACE_WIDTH_PROFILE = PcbTraceWidthProfile.ROLE_WIDTHS;

    private Rb100Placement() { }

    /**
     * Derive physical demands from the one authored RB100 inventory. The net
     * domain partition and isolation corridor are the existing RB56 contract.
     * Intent9 selects the existing current density14 placement interpretation,
     * retaining ROLE_TRACE@1, the corrected inherited PRE_L role,
     * family-authored functional regions and bounded medium two-layer policy.
     */
    static PcbPlacementConstraints create(Rb100Plan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing RB100 placement plan");
        Vector<PcbPlacementConstraints.Part> demands =
            new Vector<PcbPlacementConstraints.Part>();
        for (Rb100Plan.Part part : plan.parts()) {
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            if (terminals.length == 0 || terminals.length != nets.length ||
                    terminals.length != part.physicalPackage.getTerminalCount())
                throw new IllegalArgumentException("Incomplete RB100 terminal-domain declaration: " +
                    part.id);

            Vector<PcbPlacementConstraints.TerminalDomain> terminalDomains =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            boolean primary = false;
            boolean secondary = false;
            for (int index = 0; index < terminals.length; index++) {
                String domain = Rb56Placement.domain(nets[index]);
                if (Rb56Placement.PRIMARY.equals(domain)) primary = true;
                else if (Rb56Placement.SECONDARY.equals(domain)) secondary = true;
                else throw new IllegalArgumentException("Unknown RB100 physical domain: " +
                    part.id + "." + terminals[index]);
                terminalDomains.add(new PcbPlacementConstraints.TerminalDomain(
                    terminals[index], domain));
            }

            boolean mixed = primary && secondary;
            boolean declaredIsolator =
                part.physicalPackage.isEquivalentTo(PhysicalPackages.ISOLATED_CONVERTER_7) ||
                part.physicalPackage.isEquivalentTo(PhysicalPackages.OPTOCOUPLER_4);
            boolean hasIsolationBody =
                part.physicalPackage.getGeometry().getIsolationBody() != null;
            if (mixed && (!declaredIsolator || !hasIsolationBody))
                throw new IllegalArgumentException(
                    "RB100 conductive package crosses the isolation corridor: " + part.id);
            if (!mixed && hasIsolationBody)
                throw new IllegalArgumentException(
                    "RB100 insulating package lacks both terminal domains: " + part.id);

            String packingDomain = primary ? Rb56Placement.PRIMARY : Rb56Placement.SECONDARY;
            PcbPlacementConstraints.Anchor anchor = PcbPlacementConstraints.Anchor.NONE;
            if (part.physicalPackage.isConnector())
                anchor = Rb56Placement.PRIMARY.equals(packingDomain) ?
                    PcbPlacementConstraints.Anchor.LEFT : PcbPlacementConstraints.Anchor.RIGHT;
            demands.add(new PcbPlacementConstraints.Part(part.id, regionId(part), "Circuit",
                packingDomain, anchor, 20,
                mixed ? terminalDomains : new Vector<PcbPlacementConstraints.TerminalDomain>()));
        }

        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier(Rb56Placement.PRIMARY,
            Rb56Placement.SECONDARY, Rb56Placement.BARRIER_DRAWING_UNITS));
        // Select the existing private MEDIUM/P07 policy before the board's
        // set-once placement owner is attached. Package, region, domain,
        // anchor, barrier, width and density demands retain their values.
        PcbPlacementConstraints physicalDemand =
            new PcbPlacementConstraints(demands, barriers, PcbCopperLayer.TOP,
                PHYSICAL_POLICY_ID, PHYSICAL_POLICY_VERSION,
                PLACEMENT_DENSITY_VERSION, REQUIRES_TWO_LAYER_ROUTING,
                TRACE_WIDTH_PROFILE)
                .withConnectedPocketPacking();
        return ProceduralPcbLayout.toBottomRoutingConstraints(physicalDemand);
    }

    private static String regionId(Rb100Plan.Part part) {
        String purpose = part.purpose;
        if ("AC_INPUT".equals(purpose)) {
            if ("JAC".equals(part.id) && part.kind == Rb100Plan.Kind.POWER &&
                    "MAINAC".equals(part.inputId))
                return "ac-entry";
            throw unknownRegion(part);
        }
        if (oneOf(purpose, "AC_FUSING", "AC_RECTIFICATION",
                "PRIMARY_ENERGY_STORAGE", "PRIMARY_BLEEDER"))
            return "ac-entry";
        if (oneOf(purpose, "ISOLATED_POWER_CONVERSION", "CONVERTER_ENABLE_BIAS",
                "CONVERTER_BIAS_FILTER", "CONVERTER_FREEWHEEL", "12V_OUTPUT_INDUCTOR",
                "12V_OUTPUT_FILTER", "12V_OUTPUT_BLEEDER", "ISOLATED_POWER_FEEDBACK",
                "FEEDBACK_REFERENCE_DIODE", "FEEDBACK_SENSE_RESISTOR",
                "CONVERTER_FEEDBACK_PULLUP", "CONVERTER_FEEDBACK_FILTER"))
            return "isolated-conversion";
        if (oneOf(purpose, "5V_LINEAR_REGULATOR", "REGULATOR_ENABLE_BIAS",
                "5V_REGULATOR_INPUT_FILTER", "5V_REGULATOR_OUTPUT_FILTER",
                "3V3_LINEAR_REGULATION", "3V3_AVERAGED_SWITCHING_REGULATION",
                "3V3_INPUT_FILTER", "3V3_OUTPUT_FILTER"))
            return "regulation";
        if ("SHARED_REFERENCE_UPPER_LEG".equals(purpose)) {
            if ("RREF_H".equals(part.id) && part.channel == null)
                return "sensor-reference";
            throw unknownRegion(part);
        }
        if ("SHARED_REFERENCE_LOWER_LEG".equals(purpose)) {
            if ("RREF_L".equals(part.id) && part.channel == null)
                return "sensor-reference";
            throw unknownRegion(part);
        }
        if ("EXTERNAL_DC_LOAD_BUS_INPUT".equals(purpose)) {
            if ("JLOAD".equals(part.id) && part.kind == Rb100Plan.Kind.SOURCE &&
                    "LOAD12".equals(part.inputId))
                return "external-interfaces";
            throw unknownRegion(part);
        }
        if (part.kind == Rb100Plan.Kind.OUTPUT_HEADER) {
            return outputInterfaceRegion(part);
        }
        if ("EXTERNAL_SENSOR_INPUT".equals(purpose)) {
            String channel = channelFromId(part.id, "JS");
            if (channel != null && (part.channel == null || channel.equals(part.channel)) &&
                    ("SENSOR_" + channel).equals(part.inputId))
                return channelRegion(channel);
            throw unknownRegion(part);
        }
        if (oneOf(purpose, "SEPARATE_REFERENCE_UPPER_LEG",
                "SEPARATE_REFERENCE_LOWER_LEG"))
            return separateReferenceRegion(part);
        if (oneOf(purpose, "SENSOR_THRESHOLD_DECISION", "SENSOR_SERIES_RESISTOR",
                "SENSOR_RETURN_BIAS", "SENSOR_INPUT_FILTER", "DRIVER_INPUT_RESISTOR",
                "DRIVER_INPUT_PULLDOWN", "RELAY_COIL_LOW_SIDE_SWITCH",
                "RELAY_COIL_FLYBACK", "LOAD_CHANGEOVER_RELAY",
                "HYSTERETIC_SENSOR_FEEDBACK")) {
            return channelRegion(coreChannel(part));
        }
        if ("E".equals(part.channel) || "F".equals(part.channel)) {
            if (oneOf(purpose, "SWITCH_GATE_RESISTOR", "SWITCH_GATE_MONITOR_BIAS",
                    "OUTPUT_LOW_SIDE_SWITCH", "OUTPUT_FLYBACK", "OUTPUT_CURRENT_SHUNT",
                    "OUTPUT_STORAGE_CAPACITOR"))
                return "output-" + part.channel;
            throw unknownRegion(part);
        }
        if ("MON".equals(part.channel)) {
            if (oneOf(purpose, "MONITOR_SHUNT", "MONITOR_FILTER_SERIES_RESISTOR",
                    "MONITOR_DECISION", "MONITOR_FILTER", "MONITOR_REFERENCE_HIGH",
                    "MONITOR_REFERENCE_LOW"))
                return "monitor";
            throw unknownRegion(part);
        }
        if (interlockRegion(part)) return "interlock";
        throw unknownRegion(part);
    }

    private static String outputInterfaceRegion(Rb100Plan.Part part) {
        if ("JOA".equals(part.id) && "A".equals(part.channel) ||
                "JOB".equals(part.id) && "B".equals(part.channel)) {
            if ("EXTERNAL_DC_LOAD_BUS_RELAY_OUTPUT_INTERFACE".equals(part.purpose))
                return "external-interfaces";
        } else if ("JOC".equals(part.id) && "C".equals(part.channel) ||
                "JOD".equals(part.id) && "D".equals(part.channel)) {
            if ("EXTERNAL_DC_LOAD_BUS_RESISTIVE_OUTPUT_INTERFACE".equals(part.purpose))
                return "external-interfaces";
        } else if ("JOE".equals(part.id) && "E".equals(part.channel) ||
                "JOF".equals(part.id) && "F".equals(part.channel)) {
            if ("EXTERNAL_DC_LOAD_BUS_R_PLUS_L_OUTPUT_INTERFACE".equals(part.purpose))
                return "external-interfaces";
        }
        throw unknownRegion(part);
    }

    private static String separateReferenceRegion(Rb100Plan.Part part) {
        if ("RREF_HA".equals(part.id) &&
                "SEPARATE_REFERENCE_UPPER_LEG".equals(part.purpose) ||
                "RREF_LA".equals(part.id) &&
                "SEPARATE_REFERENCE_LOWER_LEG".equals(part.purpose)) {
            if (part.channel != null && !"A".equals(part.channel))
                throw unknownRegion(part);
            return "channel-A";
        }
        if ("RREF_HB".equals(part.id) &&
                "SEPARATE_REFERENCE_UPPER_LEG".equals(part.purpose) ||
                "RREF_LB".equals(part.id) &&
                "SEPARATE_REFERENCE_LOWER_LEG".equals(part.purpose)) {
            if (part.channel != null && !"B".equals(part.channel))
                throw unknownRegion(part);
            return "channel-B";
        }
        throw unknownRegion(part);
    }

    private static String coreChannel(Rb100Plan.Part part) {
        String channel = null;
        String purpose = part.purpose;
        if ("SENSOR_THRESHOLD_DECISION".equals(purpose))
            channel = channelFromId(part.id, "U2");
        else if ("SENSOR_SERIES_RESISTOR".equals(purpose))
            channel = channelFromId(part.id, "RS");
        else if ("SENSOR_RETURN_BIAS".equals(purpose))
            channel = channelFromId(part.id, "RPIN_");
        else if ("SENSOR_INPUT_FILTER".equals(purpose))
            channel = channelFromId(part.id, "CFLT_");
        else if ("DRIVER_INPUT_RESISTOR".equals(purpose))
            channel = channelFromId(part.id, "RD");
        else if ("DRIVER_INPUT_PULLDOWN".equals(purpose)) {
            channel = channelFromId(part.id, "RPD_");
            if (channel == null) channel = channelFromId(part.id, "RPD");
        } else if ("RELAY_COIL_LOW_SIDE_SWITCH".equals(purpose))
            channel = channelFromId(part.id, "Q");
        else if ("RELAY_COIL_FLYBACK".equals(purpose))
            channel = channelFromId(part.id, "D");
        else if ("LOAD_CHANGEOVER_RELAY".equals(purpose))
            channel = channelFromId(part.id, "K");
        else if ("HYSTERETIC_SENSOR_FEEDBACK".equals(purpose)) {
            channel = channelFromId(part.id, "RFB_");
            if (!"A".equals(channel) && !"B".equals(channel)) channel = null;
        }
        if (!isCoreChannel(channel) ||
                (part.channel != null && !channel.equals(part.channel)))
            throw unknownRegion(part);
        return channel;
    }

    private static String channelFromId(String id, String prefix) {
        if (!id.startsWith(prefix)) return null;
        String suffix = id.substring(prefix.length());
        return isCoreChannel(suffix) ? suffix : null;
    }

    private static boolean interlockRegion(Rb100Plan.Part part) {
        return "RBAUX".equals(part.id) && "AUX".equals(part.channel) &&
                "INTERLOCK_BASE_RESISTOR".equals(part.purpose) ||
            "RPUAUX".equals(part.id) && "AUX".equals(part.channel) &&
                "INTERLOCK_GATE_PULLUP".equals(part.purpose) ||
            "QINV".equals(part.id) && "INV".equals(part.channel) &&
                "MONITOR_COMMAND_INVERTER".equals(part.purpose) ||
            "QAUX".equals(part.id) && "AUX".equals(part.channel) &&
                "AUXILIARY_RETURN_INTERLOCK".equals(part.purpose);
    }

    private static boolean isCoreChannel(String channel) {
        return "A".equals(channel) || "B".equals(channel) ||
            "C".equals(channel) || "D".equals(channel);
    }

    private static String channelRegion(String channel) {
        if (!isCoreChannel(channel))
            throw new IllegalArgumentException("Unknown RB100 functional channel: " + channel);
        return "channel-" + channel;
    }

    private static boolean oneOf(String value, String... candidates) {
        for (String candidate : candidates) if (candidate.equals(value)) return true;
        return false;
    }

    private static IllegalStateException unknownRegion(Rb100Plan.Part part) {
        return new IllegalStateException("RB100 part has no authored functional region: " +
            part.id + " channel=" + part.channel + " input=" + part.inputId +
            " purpose=" + part.purpose);
    }
}
