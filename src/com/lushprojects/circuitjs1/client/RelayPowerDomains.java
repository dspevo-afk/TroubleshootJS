package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;

/** Resolved references of the bounded isolated output channel; no earth bond. */
final class RelayPowerDomains {
    static PowerDomainContract create() {
        Vector<PowerDomainContract.Rail> rails = new Vector<PowerDomainContract.Rail>();
        for(String net : new String[]{"CTRL_SUPPLY","CMD","DRIVE","COIL_LOW"})
            rails.add(new PowerDomainContract.Rail(net,"CTRL_RETURN",PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED));
        for(String net : new String[]{"CONTACT_SUPPLY","CONTACT_OUT","NC"})
            rails.add(new PowerDomainContract.Rail(net,"CONTACT_RETURN",PowerDomainContract.StorageRequirement.NONE));
        return new PowerDomainContract("ISOLATED_RELAY_OUTPUT",Arrays.asList(
            new PowerDomainContract.Reference("CTRL_RETURN","CONTROL",null,false),
            new PowerDomainContract.Reference("CONTACT_RETURN","LOAD",null,false)),rails,
            Arrays.asList(source("COIL_INPUT","CTRL_SUPPLY",5),source("COMMAND_INPUT","CMD",5),
                source("LOAD_INPUT","CONTACT_SUPPLY",12)),Collections.<PowerDomainContract.BackfeedPath>emptyList());
    }
    private static PowerDomainContract.Source source(String id,String rail,double volts) {
        return new PowerDomainContract.Source(id,rail,Range.known(0,volts),Scalar.known(.5),
            Scalar.known(.05),Scalar.known(.25),Drive.RESISTIVE_SOURCE);
    }
}
