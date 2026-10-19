package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** RB56 drawing-space placement intent; no manufacturing or equipment safety rating. */
final class Rb56Placement {
    static final int LEGACY_VERSION = 1, COMPACT_VERSION = 2,
        REALISM_VERSION = 3, DENSE_VERSION = 4, LARGE_VERSION = 5, SINGLE_GROUP_VERSION = 6,
        GUARDED_VERSION = 7, NARROW_VERSION = 8, CONNECTED_VERSION = 9, PIN_LOCAL_VERSION = 10,
        VACANCY_VERSION = 11, ROLE_WIDTHS_VERSION12 = 12, NEAREST_VACANCY_VERSION13 = 13,
        CANVAS_OWNER_VERSION14 = 14, CURRENT_VERSION = 15, PASSIVE_POSE_VERSION16 = 16,
        SIZING_CONTROL_VERSION17 = 17, SOURCE_SIZED_VERSION18 = 18,
        LOCAL_POSE_CHOICE_VERSION19 = 19;
    static final String PRIMARY = "PRIMARY", SECONDARY = "SECONDARY";
    static final int BARRIER_DRAWING_UNITS = 80;
    private Rb56Placement() { }

    static boolean supportsVersion(int version) {
        return version == LEGACY_VERSION || version == COMPACT_VERSION ||
            version == REALISM_VERSION || version == DENSE_VERSION || version == LARGE_VERSION || version == SINGLE_GROUP_VERSION || version == GUARDED_VERSION || version == NARROW_VERSION || version == CONNECTED_VERSION || version == PIN_LOCAL_VERSION || version == VACANCY_VERSION || version == ROLE_WIDTHS_VERSION12 || version == NEAREST_VACANCY_VERSION13 || version == CURRENT_VERSION || version == CANVAS_OWNER_VERSION14 || version == PASSIVE_POSE_VERSION16 || version == SIZING_CONTROL_VERSION17 ||
            version == SOURCE_SIZED_VERSION18 || version == LOCAL_POSE_CHOICE_VERSION19;
    }

    /** Replay interpretation includes copper policy; packing density alone is not its identity. */
    static int densityVersion(int placementVersion) {
        if (!supportsVersion(placementVersion))
            throw new IllegalArgumentException("Unknown RB56 placement interpretation");
        if (placementVersion == ROLE_WIDTHS_VERSION12) return VACANCY_VERSION;
        if (placementVersion == NEAREST_VACANCY_VERSION13) return ROLE_WIDTHS_VERSION12;
        if (placementVersion == CANVAS_OWNER_VERSION14) return 13;
        return placementVersion == CURRENT_VERSION || placementVersion == PASSIVE_POSE_VERSION16 ||
            placementVersion == SIZING_CONTROL_VERSION17 ||
            placementVersion == SOURCE_SIZED_VERSION18 ||
            placementVersion == LOCAL_POSE_CHOICE_VERSION19 ? 14 : placementVersion;
    }

    static int passivePosePolicyVersion(int placementVersion) {
        if (!supportsVersion(placementVersion))
            throw new IllegalArgumentException("Unknown RB56 placement interpretation");
        if (placementVersion == PASSIVE_POSE_VERSION16) return PcbPassivePosePolicy.ORTHOGONAL;
        if (placementVersion == SIZING_CONTROL_VERSION17) return PcbPassivePosePolicy.SIZING_ONLY;
        return placementVersion == SOURCE_SIZED_VERSION18 ?
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL :
            placementVersion == LOCAL_POSE_CHOICE_VERSION19 ?
                PcbPassivePosePolicy.LOCAL_POSE_CHOICE : PcbPassivePosePolicy.LEGACY;
    }

    static PcbTraceWidthProfile widthProfile(int placementVersion) {
        if (!supportsVersion(placementVersion))
            throw new IllegalArgumentException("Unknown RB56 placement interpretation");
        return placementVersion == ROLE_WIDTHS_VERSION12 || placementVersion == NEAREST_VACANCY_VERSION13 || placementVersion == CURRENT_VERSION || placementVersion == CANVAS_OWNER_VERSION14 || placementVersion == PASSIVE_POSE_VERSION16 || placementVersion == SIZING_CONTROL_VERSION17 ||
            placementVersion == SOURCE_SIZED_VERSION18 || placementVersion == LOCAL_POSE_CHOICE_VERSION19 ?
            PcbTraceWidthProfile.ROLE_WIDTHS :
            PcbTraceWidthProfile.UNIFORM;
    }

    /** One demand per declared package; the plan remains the only package inventory. */
    static PcbPlacementConstraints create(Rb56Plan plan) {
        return create(plan, plan == null ? LEGACY_VERSION : plan.placementInterpretationVersion);
    }
    static PcbPlacementConstraints create(Rb56Plan plan, int placementVersion) {
        if (!supportsVersion(placementVersion))
            throw new IllegalArgumentException("Unknown RB56 placement interpretation");
        if (plan == null) throw new IllegalArgumentException("Missing RB56 placement plan");
        if (plan.placementInterpretationVersion != placementVersion)
            throw new IllegalArgumentException("RB56 placement differs from its plan interpretation");
        Vector<PcbPlacementConstraints.Part> demands = new Vector<PcbPlacementConstraints.Part>();
        for (Rb56Plan.Part part : plan.parts()) {
            String[] terminals = part.terminalIds(), nets = part.netIds();
            Vector<PcbPlacementConstraints.TerminalDomain> domains =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            boolean primary = false, secondary = false;
            for (int index = 0; index < terminals.length; index++) {
                String domain = domain(nets[index]);
                primary |= PRIMARY.equals(domain); secondary |= SECONDARY.equals(domain);
                domains.add(new PcbPlacementConstraints.TerminalDomain(terminals[index], domain));
            }
            boolean mixed = primary && secondary;
            // Only the two actual insulating packages may bridge these domains.
            if (mixed && !(part.physicalPackage.isEquivalentTo(PhysicalPackages.ISOLATED_CONVERTER_7) ||
                    part.physicalPackage.isEquivalentTo(PhysicalPackages.OPTOCOUPLER_4)))
                throw new IllegalArgumentException("RB56 conductive package crosses the isolation corridor: " + part.id);
            if (!mixed && part.physicalPackage.getGeometry().getIsolationBody() != null)
                throw new IllegalArgumentException("RB56 insulating package lacks both terminal domains: " + part.id);
            String packingDomain = primary ? PRIMARY : SECONDARY;
            String region = region(part);
            PcbPlacementConstraints.Anchor anchor = PcbPlacementConstraints.Anchor.NONE;
            if (!mixed && part.physicalPackage.isConnector())
                anchor = PRIMARY.equals(packingDomain) ? PcbPlacementConstraints.Anchor.LEFT :
                    PcbPlacementConstraints.Anchor.RIGHT;
            demands.add(new PcbPlacementConstraints.Part(part.id, region, label(region),
                packingDomain, anchor, 20,
                mixed ? domains : new Vector<PcbPlacementConstraints.TerminalDomain>()));
        }
        Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier(PRIMARY, SECONDARY, BARRIER_DRAWING_UNITS));
        // This is the existing structural routing algorithm, not Q30 normal eligibility.
        return new PcbPlacementConstraints(demands, barriers, PcbCopperLayer.BOTTOM,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION,
            densityVersion(placementVersion),
            placementVersion == REALISM_VERSION || placementVersion == DENSE_VERSION || placementVersion == LARGE_VERSION || placementVersion == SINGLE_GROUP_VERSION || placementVersion == GUARDED_VERSION || placementVersion == NARROW_VERSION || placementVersion == CONNECTED_VERSION || placementVersion == PIN_LOCAL_VERSION || placementVersion == VACANCY_VERSION || placementVersion == ROLE_WIDTHS_VERSION12 || placementVersion == NEAREST_VACANCY_VERSION13 || placementVersion == CURRENT_VERSION || placementVersion == CANVAS_OWNER_VERSION14 || placementVersion == PASSIVE_POSE_VERSION16 || placementVersion == SIZING_CONTROL_VERSION17 ||
            placementVersion == SOURCE_SIZED_VERSION18 || placementVersion == LOCAL_POSE_CHOICE_VERSION19,
            widthProfile(placementVersion), passivePosePolicyVersion(placementVersion));
    }

    static String domain(String net) {
        return "AC_LINE".equals(net) || "AC_RETURN".equals(net) || "AC_FUSED".equals(net) ||
            "HV_POS".equals(net) || "HV_RETURN".equals(net) || "ENABLE_AC".equals(net) ||
            "FB_AC".equals(net) || "BIAS_AC".equals(net) ? PRIMARY : SECONDARY;
    }

    /** Functional grouping only; no second package census or physical coordinates. */
    private static String region(Rb56Plan.Part part) {
        String id = part.id;
        if ("UAC".equals(id) || "RENAC".equals(id) || "CBIAS".equals(id)) return "conversion";
        if ("UFB".equals(id) || "DZ1".equals(id) || "RSENSE".equals(id) ||
                "RFB".equals(id) || "CFB".equals(id)) return "feedback";
        if (part.kind == Rb56Plan.Kind.POWER) {
            for (String net : part.netIds()) if (PRIMARY.equals(domain(net))) return "entry";
            return "conversion-output";
        }
        if ("LOAD12".equals(part.inputId)) return "load-supply";
        if (id.equals("RLED") || id.equals("LED1") || id.equals("RLED12") || id.equals("LED12"))
            return "rail-status";
        if (id.equals("RREF_H") || id.equals("RREF_L")) return "sensor-reference";
        // Sensor decision, hysteresis and relay actuation share one channel lane.
        for (String channel : new String[] {"A", "B"}) {
            // Optional output status belongs to the external load circuit.
            if (id.equals("RLEDOUT_" + channel) || id.equals("LEDOUT_" + channel))
                return "load-supply";
            if (id.equals("JS" + channel) || id.equals("U2" + channel) || id.equals("RS" + channel) ||
                    id.equals("RPIN_" + channel) || id.equals("CFLT_" + channel) || id.equals("RFB_" + channel) ||
                    id.equals("RREF_H" + channel) || id.equals("RREF_L" + channel))
                return "channel-" + channel;
            if (channel.equals(part.channel) || id.equals("RD" + channel) || id.equals("RPD" + channel))
                return "channel-" + channel;
        }
        return "control-power";
    }

    private static String label(String region) {
        if ("entry".equals(region)) return "AC entry / DC bus";
        if ("conversion".equals(region)) return "Isolated converter";
        if ("conversion-output".equals(region)) return "12 V output";
        if ("feedback".equals(region)) return "Feedback";
        if ("load-supply".equals(region)) return "Load supply";
        if ("rail-status".equals(region)) return "Rail status";
        if ("sensor-reference".equals(region)) return "Sensor reference";
        if ("channel-A".equals(region)) return "Channel A";
        if ("channel-B".equals(region)) return "Channel B";
        return "5 V control rail";
    }
}
