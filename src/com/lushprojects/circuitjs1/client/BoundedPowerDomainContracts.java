package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;

/** Current device-owned projection of already preflighted, explicitly joined nets. */
final class BoundedPowerDomainContracts {
    private BoundedPowerDomainContracts() { }
    static PowerDomainContract fromPlan(BoundedAssemblyPlan plan) {
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        TreeSet<String> returned = new TreeSet<String>();
        for (ElectricalRealizationSpec.PowerInputSpec input : spec.getPowerInputs().values())
            returned.add(input.getReturnNetId());
        if (returned.size() != 1)
            throw new IllegalArgumentException("Current composed recipe requires one explicit resolved return");
        String reference = returned.first();
        // The current provider set contains no energy storage. A future unknown
        // primitive must declare its storage policy instead of inheriting NONE.
        for (ElectricalRealizationSpec.ElementDeclaration e : spec.getElementDeclarations().values()) {
            String kind = e.getKind();
            if (!("RESISTOR".equals(kind) || "NMOS".equals(kind) || "NPN".equals(kind) ||
                    "LED".equals(kind) || "WIRE".equals(kind) || "SWITCH".equals(kind) ||
                    "VOLTAGE".equals(kind) || "GROUND".equals(kind) || "FAULT_HELPER".equals(kind)))
                throw new IllegalArgumentException("Missing storage policy for model " + kind);
        }
        ArrayList<PowerDomainContract.Rail> rails = new ArrayList<PowerDomainContract.Rail>();
        for (String net : spec.getExpectedNetIds())
            if (!net.equals(reference)) rails.add(new PowerDomainContract.Rail(net, reference,
                PowerDomainContract.StorageRequirement.NONE));
        TreeMap<String,ElectricalPortContract> outputs = new TreeMap<String,ElectricalPortContract>();
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters())
            outputs.put(adapter.getExternalInputId(), adapter.getElectricalContract().getPorts().get(adapter.getOutputPortId()));
        ArrayList<PowerDomainContract.Source> sources = new ArrayList<PowerDomainContract.Source>();
        for (ElectricalRealizationSpec.PowerInputSpec input : spec.getPowerInputs().values()) {
            ElectricalPortContract port = outputs.get(input.getInputId());
            if (plan.isControlledIndicator() && port == null)
                throw new IllegalArgumentException("Source has no declared electrical output");
            sources.add(new PowerDomainContract.Source(input.getInputId(), input.getPositiveNetId(),
                port == null ? Range.known(5, 5) : port.getGuaranteedVoltage(),
                port == null ? Scalar.unknown() : port.getCapacityAmps(),
                Scalar.known(LowVoltageSourceModel.OUTPUT_OHMS), Scalar.known(LowVoltageSourceModel.DEFAULT_LIMIT_AMPS),
                port == null ? Drive.STIFF_VOLTAGE : port.getDrive()));
        }
        // GroundElm fixes numerical potential. It supplies no physical earth bond.
        return new PowerDomainContract(plan.getRequest().getDescriptor().getDeviceIntent().toCanonical(),
            Collections.singletonList(new PowerDomainContract.Reference(reference, "current-resolved-return", null, false)),
            rails, sources, Collections.<PowerDomainContract.BackfeedPath>emptyList());
    }
}
