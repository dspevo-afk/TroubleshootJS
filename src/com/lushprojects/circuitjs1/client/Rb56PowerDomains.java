package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;

/** References and exposure obligations of the actual declared RB56 device. */
final class Rb56PowerDomains {
    private Rb56PowerDomains() { }

    static PowerDomainContract create(Rb56Plan plan, TroubleshootBoard board) {
        if (plan == null || board == null || !board.getId().equals(Rb56Plan.FAMILY_ID + "_BOARD"))
            throw new IllegalArgumentException("Missing RB56 power declaration owner");
        Vector<PowerDomainContract.Rail> rails = new Vector<PowerDomainContract.Rail>();
        for (String net : board.getNetIds()) {
            if ("AC_RETURN".equals(net) || "HV_RETURN".equals(net) ||
                    "CTRL_RETURN".equals(net) || "LOAD_RETURN".equals(net)) continue;
            boolean ac = "AC_LINE".equals(net) || "AC_FUSED".equals(net);
            boolean primary = ac || "HV_POS".equals(net) || "BIAS_AC".equals(net) ||
                "ENABLE_AC".equals(net) || "FB_AC".equals(net);
            boolean load = "LOAD12".equals(net) || net.startsWith("OUT_") ||
                net.startsWith("NC_") || net.startsWith("LED_OUT_");
            // Primary and control nodes can expose the actual C/L/coil stores through
            // the adopted semiconductor and resistor paths. This is an observation
            // obligation, not a declaration of conductive equivalence or a capacitor
            // on every net. Isolated relay contacts/load circuitry contain no storage.
            rails.add(new PowerDomainContract.Rail(net,
                ac ? "AC_RETURN" : primary ? "HV_RETURN" : load ? "LOAD_RETURN" : "CTRL_RETURN",
                load ? PowerDomainContract.StorageRequirement.NONE :
                    PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED));
        }
        Vector<PowerDomainContract.Source> sources = new Vector<PowerDomainContract.Source>();
        sources.add(new PowerDomainContract.Source("MAINAC", "AC_LINE",
            Range.known(-E05AcInputModel.PEAK_VOLTS, E05AcInputModel.PEAK_VOLTS),
            Scalar.unknown(), Scalar.known(E05AcInputModel.SERIES_OHMS),
            Scalar.notApplicable(), Drive.RESISTIVE_SOURCE));
        for (Rb56Plan.Part part : plan.tailParts()) if (part.inputId != null)
            sources.add(new PowerDomainContract.Source(part.inputId, part.netIds()[0],
                Range.known(0, part.value), Scalar.known(LowVoltageSourceModel.DEFAULT_LIMIT_AMPS),
                Scalar.known(LowVoltageSourceModel.OUTPUT_OHMS),
                Scalar.known(LowVoltageSourceModel.DEFAULT_LIMIT_AMPS), Drive.RESISTIVE_SOURCE));
        PowerDomainContract contract = new PowerDomainContract("RB56_AC_ISOLATED_CONTROL",
            Arrays.asList(new PowerDomainContract.Reference("AC_RETURN", "PRIMARY", null, false),
                new PowerDomainContract.Reference("HV_RETURN", "PRIMARY", null, false),
                new PowerDomainContract.Reference("CTRL_RETURN", "SECONDARY", null, false),
                new PowerDomainContract.Reference("LOAD_RETURN", "LOAD", null, false)),
            rails, sources, Collections.<PowerDomainContract.BackfeedPath>emptyList());
        contract.requireSourceCoverage(board.getPowerInputIds());
        return contract;
    }
}
