package com.lushprojects.circuitjs1.client;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Leaf-owned input-role provider for the existing NPN and NMOS low-side leaves.
 * These exact IDs/relationships are declared by their existing generators and
 * external-power bindings. All numeric evidence comes from supplied nameplates.
 */
final class LowSideSwitchInputMetadata {
    private LowSideSwitchInputMetadata() { }

    static ElectricalBlockContract adapt(String instanceKey, Collection<ExternalBoardPowerInput> inputs,
            Collection<PowerInputNameplate> nameplates, Collection<BoardPad> pads) {
        ElectricalContractException.required(inputs, "inputs", instanceKey);
        ElectricalContractException.required(pads, "pads", instanceKey);
        Map<String, ElectricalPortContract.Role> roles = new TreeMap<String, ElectricalPortContract.Role>();
        roles.put("LOAD_VIN_INPUT", ElectricalPortContract.Role.RAIL);
        roles.put("CONTROL_VIN_INPUT", ElectricalPortContract.Role.CONTROL);
        if (inputs.size() != 2) invalid("inputs", instanceKey);
        for (ExternalBoardPowerInput input : inputs) {
            ElectricalContractException.required(input, "inputs", instanceKey);
            if ("LOAD_VIN_INPUT".equals(input.getId()))
                requireInput(input, "J1.1", "J1.2", "LOAD_SUPPLY");
            else if ("CONTROL_VIN_INPUT".equals(input.getId()))
                requireInput(input, "J2.1", "J2.2", "CONTROL_INPUT");
            else invalid("input.id", input.getId());
        }
        for (BoardPad pad : pads) {
            ElectricalContractException.required(pad, "pads", instanceKey);
            if ("J1.1".equals(pad.getId())) requireTerminal(pad, "J1", "1");
            else if ("J1.2".equals(pad.getId())) requireTerminal(pad, "J1", "2");
            else if ("J2.1".equals(pad.getId())) requireTerminal(pad, "J2", "1");
            else if ("J2.2".equals(pad.getId())) requireTerminal(pad, "J2", "2");
        }
        return LegacyInputPortMetadata.adapt(instanceKey, inputs, nameplates, pads, roles);
    }
    private static void requireInput(ExternalBoardPowerInput input, String positivePad, String returnPad, String positiveNet) {
        if (!positivePad.equals(input.getPositivePadId()) || !returnPad.equals(input.getReturnPadId()) ||
                !positiveNet.equals(input.getPositiveNetId()) || !"GND".equals(input.getReturnNetId()))
            invalid("input.mapping", input.getId());
    }
    private static void requireTerminal(BoardPad pad, String component, String terminal) {
        if (!component.equals(pad.getComponentId()) || !terminal.equals(pad.getTerminalId())) invalid("pad.terminal", pad.getId());
    }
    private static void invalid(String field, String id) {
        throw new ElectricalContractException(ElectricalContractException.Code.INVALID_REFERENCE, field, id,
            "Metadata does not match the declared low-side leaf input boundary");
    }
}
