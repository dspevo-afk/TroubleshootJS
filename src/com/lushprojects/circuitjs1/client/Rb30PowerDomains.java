package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;

/** Explicit control and isolated load references for the Q30 device intent. */
final class Rb30PowerDomains {
    private Rb30PowerDomains() { }

    static PowerDomainContract create(Rb30Plan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing Q30 plan");
        TroubleshootBoard board = plan.board();
        Vector<PowerDomainContract.Rail> rails =
            new Vector<PowerDomainContract.Rail>();
        for (String id : board.getNetIds()) {
            if ("CTRL_RETURN".equals(id) || "LOAD_RETURN".equals(id))
                continue;
            boolean load = "LOAD12".equals(id) || id.startsWith("OUT_") ||
                id.startsWith("NC_") || id.startsWith("LED_OUT_");
            // Observe every known exposure to CIN/C5/optional C12 and relay
            // energy, including nodes reached through real components. This
            // is a discharge obligation, not a capacitor on each named net.
            // The isolated load domain and unrelated sensor inputs stay NONE.
            boolean storage = "RAW12".equals(id) || "FUSED12".equals(id) ||
                "RAIL12".equals(id) || "RAIL5".equals(id) || "EN5".equals(id) ||
                "REF_SHARED".equals(id) || "LED_FEED".equals(id) ||
                "LED12_FEED".equals(id);
            for (String channel : plan.channels()) {
                if ((channel + "_REF").equals(id) ||
                        (channel + "_CMD").equals(id) ||
                        (channel + "_DRIVE").equals(id) ||
                        (channel + "_COIL_LOW").equals(id))
                    storage = true;
                if ((plan.hasSensorInputFilter(channel) ||
                        plan.sharedHystereticReference()) &&
                        ((channel + "_SENSE").equals(id) ||
                         (channel + "_RAW").equals(id)))
                    storage = true;
            }
            rails.add(new PowerDomainContract.Rail(id,
                load ? "LOAD_RETURN" : "CTRL_RETURN",
                storage ? PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED :
                    PowerDomainContract.StorageRequirement.NONE));
        }
        Vector<PowerDomainContract.Source> sources =
            new Vector<PowerDomainContract.Source>();
        sources.add(source("MAIN12", "RAW12", 12, .25, .05, .25));
        for (String channel : plan.channels())
            sources.add(source("SENSOR_" + channel, channel + "_RAW",
                5, .25, .05, .25));
        sources.add(source("LOAD12", "LOAD12", 12, .25, .05, .25));
        PowerDomainContract contract = new PowerDomainContract(
            "RB30_MULTI_RAIL", Arrays.asList(
                new PowerDomainContract.Reference("CTRL_RETURN", "CONTROL",
                    null, false),
                new PowerDomainContract.Reference("LOAD_RETURN", "LOAD",
                    null, false)), rails, sources,
            Collections.<PowerDomainContract.BackfeedPath>emptyList());
        contract.requireSourceCoverage(board.getPowerInputIds());
        return contract;
    }

    private static PowerDomainContract.Source source(String id, String rail,
            double volts, double capacity, double resistance, double limit) {
        return new PowerDomainContract.Source(id, rail, Range.known(0, volts),
            Scalar.known(capacity), Scalar.known(resistance),
            Scalar.known(limit), Drive.RESISTIVE_SOURCE);
    }
}
