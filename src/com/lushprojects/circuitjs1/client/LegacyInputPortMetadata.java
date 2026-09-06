package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessProvision;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessRequirement;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Behavior;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Digital;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Direction;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Domain;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Loading;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Role;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;

/**
 * Read-only view of a monolithic leaf's external-input contribution, not a board
 * or an assembler. Callers provide the actual board input/pad/nameplate metadata
 * and leaf-owned roles. No values are inferred from labels or simulation state.
 */
final class LegacyInputPortMetadata {
    private LegacyInputPortMetadata() { }

    static ElectricalBlockContract adapt(String instanceKey, Collection<ExternalBoardPowerInput> inputs,
            Collection<PowerInputNameplate> nameplates, Collection<BoardPad> pads, Map<String, Role> roles) {
        ElectricalContractException.id(instanceKey, "instanceKey");
        ElectricalContractException.required(inputs, "inputs", instanceKey);
        ElectricalContractException.required(nameplates, "nameplates", instanceKey);
        ElectricalContractException.required(pads, "pads", instanceKey);
        ElectricalContractException.required(roles, "roles", instanceKey);
        TreeMap<String, ExternalBoardPowerInput> inputCopies = new TreeMap<String, ExternalBoardPowerInput>();
        TreeMap<String, BoardPad> padCopies = new TreeMap<String, BoardPad>();
        TreeMap<String, Scalar> nominalCopies = new TreeMap<String, Scalar>();
        TreeMap<String, Role> roleCopies = new TreeMap<String, Role>();
        for (Map.Entry<String, Role> entry : roles.entrySet()) {
            String id = ElectricalContractException.id(entry.getKey(), "input.role.id");
            Role role = ElectricalContractException.required(entry.getValue(), "input.role", id);
            if (role != Role.RAIL && role != Role.CONTROL)
                invalid(ElectricalContractException.Code.CONTRADICTORY_FIELD, "input.role", id);
            roleCopies.put(id, role);
        }
        for (ExternalBoardPowerInput input : inputs) {
            ElectricalContractException.required(input, "inputs", instanceKey);
            ExternalBoardPowerInput copy = new ExternalBoardPowerInput(
                ElectricalContractException.id(input.getId(), "input.id"),
                ElectricalContractException.id(input.getPositivePadId(), "input.positivePad"),
                ElectricalContractException.id(input.getReturnPadId(), "input.returnPad"),
                ElectricalContractException.id(input.getPositiveNetId(), "input.positiveNet"),
                ElectricalContractException.id(input.getReturnNetId(), "input.returnNet"));
            if (inputCopies.put(copy.getId(), copy) != null) invalid(ElectricalContractException.Code.DUPLICATE_DECLARATION, "inputs", copy.getId());
            if (copy.getPositivePadId().equals(copy.getReturnPadId()) || copy.getPositiveNetId().equals(copy.getReturnNetId()))
                invalid(ElectricalContractException.Code.INVALID_REFERENCE, "input.return", copy.getId());
            Role role = roleCopies.get(copy.getId());
            if (role != Role.RAIL && role != Role.CONTROL) invalid(ElectricalContractException.Code.MISSING_FIELD, "input.role", copy.getId());
        }
        if (inputCopies.isEmpty() || !inputCopies.keySet().equals(roleCopies.keySet()))
            invalid(ElectricalContractException.Code.INVALID_REFERENCE, "input.roles", instanceKey);
        for (BoardPad pad : pads) {
            ElectricalContractException.required(pad, "pads", instanceKey);
            BoardPad copy = new BoardPad(ElectricalContractException.id(pad.getId(), "pad.id"),
                ElectricalContractException.id(pad.getComponentId(), "pad.component"),
                ElectricalContractException.id(pad.getTerminalId(), "pad.terminal"),
                ElectricalContractException.id(pad.getNetId(), "pad.net"));
            if (padCopies.put(copy.getId(), copy) != null) invalid(ElectricalContractException.Code.DUPLICATE_DECLARATION, "pads", copy.getId());
        }
        for (PowerInputNameplate nameplate : nameplates) {
            ElectricalContractException.required(nameplate, "nameplates", instanceKey);
            String id = ElectricalContractException.id(nameplate.getPowerInputId(), "nameplate.input");
            double nominal = nameplate.getNominalVoltage();
            if (!inputCopies.containsKey(id)) invalid(ElectricalContractException.Code.INVALID_REFERENCE, "nameplate.input", id);
            if (Double.isNaN(nominal) || Double.isInfinite(nominal) || nominal <= 0)
                invalid(ElectricalContractException.Code.INVALID_SCALAR, "nameplate.nominalVoltage", id);
            if (nominalCopies.put(id, Scalar.known(nominal)) != null)
                invalid(ElectricalContractException.Code.DUPLICATE_DECLARATION, "nameplates", id);
        }

        TreeMap<String, BoardPad> usedPads = new TreeMap<String, BoardPad>();
        TreeSet<String> nets = new TreeSet<String>();
        List<FunctionalBlockDescriptor.Role> localRoles = new ArrayList<FunctionalBlockDescriptor.Role>();
        List<FunctionalBlockDescriptor.Port> localPorts = new ArrayList<FunctionalBlockDescriptor.Port>();
        List<ElectricalPortContract> portContracts = new ArrayList<ElectricalPortContract>();
        TreeSet<String> generatedPortIds = new TreeSet<String>();
        for (ExternalBoardPowerInput input : inputCopies.values()) {
            BoardPad positive = requirePad(padCopies, input.getPositivePadId(), input.getPositiveNetId());
            BoardPad returned = requirePad(padCopies, input.getReturnPadId(), input.getReturnNetId());
            usedPads.put(positive.getId(), positive); usedPads.put(returned.getId(), returned);
            nets.add(positive.getNetId()); nets.add(returned.getNetId());
            String id = input.getId(), returnId = id + ".return";
            if (!generatedPortIds.add(id) || !generatedPortIds.add(returnId))
                invalid(ElectricalContractException.Code.DUPLICATE_DECLARATION, "generatedPorts", id);
            LocalRef positiveRef = new LocalRef(EntityKind.PAD, positive.getId()), returnRef = new LocalRef(EntityKind.PAD, returned.getId());
            localRoles.add(new FunctionalBlockDescriptor.Role(id, FunctionalBlockDescriptor.Requirement.REQUIRED, Arrays.asList(positiveRef, returnRef)));
            localPorts.add(new FunctionalBlockDescriptor.Port(id, id, positiveRef));
            localPorts.add(new FunctionalBlockDescriptor.Port(returnId, id, returnRef));
            Role role = roleCopies.get(id);
            Domain domain = Domain.unknownIsolation(input.getReturnNetId());
            Scalar nominal = nominalCopies.containsKey(id) ? nominalCopies.get(id) : Scalar.unknown();
            portContracts.add(new ElectricalPortContract(id, role, Direction.INPUT, Behavior.SINK, Drive.NONE,
                domain, nominal, Range.notApplicable(), Range.unknown(), Loading.UNKNOWN,
                Scalar.notApplicable(), Scalar.unknown(), role == Role.CONTROL ? Digital.unknownInput() : Digital.notApplicable(),
                MergePolicy.UNKNOWN, AccessRequirement.CONNECTABLE, AccessProvision.UNKNOWN));
            portContracts.add(new ElectricalPortContract(returnId, Role.RETURN, Direction.BIDIRECTIONAL, Behavior.PASSIVE, Drive.NONE,
                domain, Scalar.notApplicable(), Range.notApplicable(), Range.notApplicable(), Loading.NONE,
                Scalar.notApplicable(), Scalar.notApplicable(), Digital.notApplicable(),
                MergePolicy.UNKNOWN, AccessRequirement.CONNECTABLE, AccessProvision.UNKNOWN));
        }
        TreeMap<String, TreeSet<String>> componentTerminals = new TreeMap<String, TreeSet<String>>();
        List<FunctionalBlockDescriptor.Endpoint> endpoints = new ArrayList<FunctionalBlockDescriptor.Endpoint>();
        List<FunctionalBlockDescriptor.Pad> localPads = new ArrayList<FunctionalBlockDescriptor.Pad>();
        for (BoardPad pad : usedPads.values()) {
            if (!componentTerminals.containsKey(pad.getComponentId())) componentTerminals.put(pad.getComponentId(), new TreeSet<String>());
            componentTerminals.get(pad.getComponentId()).add(pad.getTerminalId());
            endpoints.add(new FunctionalBlockDescriptor.Endpoint(pad.getId(), pad.getComponentId(), pad.getTerminalId()));
            localPads.add(new FunctionalBlockDescriptor.Pad(pad.getId(), pad.getId(), pad.getNetId()));
        }
        List<FunctionalBlockDescriptor.Component> components = new ArrayList<FunctionalBlockDescriptor.Component>();
        for (String component : componentTerminals.keySet()) components.add(new FunctionalBlockDescriptor.Component(component,
            "external-input-boundary", componentTerminals.get(component)));
        FunctionalBlockDescriptor descriptor = new FunctionalBlockDescriptor("legacy-external-inputs", 1, instanceKey,
            Collections.<FunctionalBlockDescriptor.Parameter>emptyList(), components, nets, endpoints, localPads, localRoles, localPorts);
        return new ElectricalBlockContract(descriptor, portContracts, Collections.<ElectricalBlockContract.Adapter>emptyList());
    }

    private static BoardPad requirePad(Map<String, BoardPad> pads, String id, String net) {
        BoardPad pad = pads.get(id);
        if (pad == null || !pad.getNetId().equals(net)) invalid(ElectricalContractException.Code.INVALID_ATTACHMENT, "input.pad.net", id);
        return pad;
    }
    private static void invalid(ElectricalContractException.Code code, String field, String id) {
        throw new ElectricalContractException(code, field, id, "Invalid external-input metadata");
    }
}
