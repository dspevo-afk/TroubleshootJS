package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/** Plan metadata derived from a resolved composed namespace. */
final class GeneratedDiagnosticPlanCatalog {
    private GeneratedDiagnosticPlanCatalog() { }

    /**
     * Build the plan from the resolved device contribution.  This is the only
     * composed-device plan source: repeated channel keys, selected provider
     * pads, and support pads all come from the immutable plan namespace.
     */
    static GeneratedDiagnosticPlan forAssembly(BoundedAssemblyPlan plan) {
        if (plan == null || !plan.isControlledIndicator())
            throw new IllegalArgumentException("Controlled assembly plan is required");
        List<String> probes = new ArrayList<String>();
        String reference = null;
        for (FunctionalBlockDescriptor descriptor : plan.getRequest().getNamespaceDescriptors()) {
            TreeSet<String> localPads = new TreeSet<String>(descriptor.getPads().keySet());
            for (String localPad : localPads) {
                String qualified = plan.getNamespace().idFor(descriptor.getInstanceKey(),
                    EntityKind.PAD, localPad);
                probes.add(qualified);
                if (reference == null && descriptor.getInstanceKey().equals(
                        DeviceAdapterContract.POWER_ADAPTER_KEY) && localPad.endsWith(".2"))
                    reference = qualified;
            }
        }
        if (reference == null)
            throw new IllegalArgumentException("Controlled plan has no power reference pad");

        ArrayList<String> transitions = new ArrayList<String>();
        ArrayList<String> operations = new ArrayList<String>();
        ArrayList<String> temporal = new ArrayList<String>();
        ArrayList<String> domains = new ArrayList<String>();
        transitions.add("BOARD_POWER_ON");
        transitions.add("BOARD_POWER_OFF");
        domains.add("SUPPLY");
        domains.add("CONTROL");
        domains.add("SHARED_RETURN");
        domains.add("SUPPORT_PRESENT");
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            String high = channel.getHighOperationId();
            String low = channel.getLowOperationId();
            transitions.add(high);
            transitions.add(low);
            operations.add(high);
            operations.add(low);
            temporal.add(channel.getSampleId(true));
            temporal.add(channel.getSampleId(false));
            domains.add("CHANNEL_" + channel.getLabel().toUpperCase() + "_SWITCHED");
        }
        operations.add(GeneratedBoardOperationIds.CUSTOMER_RETEST);
        Collections.sort(probes);
        Collections.sort(transitions);
        Collections.sort(operations);
        Collections.sort(temporal);
        Collections.sort(domains);
        return new GeneratedDiagnosticPlan(
            "LOW_SIDE_INDICATOR_CHANNEL_PATH", reference,
            probes.toArray(new String[probes.size()]),
            new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
            transitions.toArray(new String[transitions.size()]),
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[0],
            operations.toArray(new String[operations.size()]),
            temporal.toArray(new String[temporal.size()]),
            domains.toArray(new String[domains.size()]),
            8 + (plan.getChannels().size() * 3), true, true, "NONE");
    }

}
