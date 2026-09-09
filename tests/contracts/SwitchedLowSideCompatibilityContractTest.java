package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.ActiveLevel;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Behavior;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Direction;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Loading;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Role;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;

/** Direct preflight matrix for the bounded typed switched-low-side relation. */
public final class SwitchedLowSideCompatibilityContractTest {
    private static int assertions;

    private SwitchedLowSideCompatibilityContractTest() { }

    public static void main(String[] args) {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(0L);
        require(result(request).getDecision()
                == PortCompatibilityPreflight.Decision.COMPATIBLE,
                "bounded switched relation is compatible");
        verifyMissingJoin(request, ControlledIndicatorBlockContributions.CONTROL_CONNECTION_ID,
                "missing control join");
        verifyMissingJoin(request, ControlledIndicatorBlockContributions.POWER_CONNECTION_ID,
                "missing supply join");
        verifyMissingJoin(request, ControlledIndicatorBlockContributions.RETURN_CONNECTION_ID,
                "missing return join");
        verifyExtraTransitivePort(request);
        verifyUnknownEvidence(request);
        verifyOvercurrent(request);
        verifyActiveLowRelation(request);
        verifyActiveLowSource(request);
        verifyUnsafeOffVoltage(request);
        verifyBroadSupplyGuarantee(request);
        verifyUntypedOpenDrain(request);
        System.out.println("PASS: current switched-low-side compatibility "
                + assertions + " assertions");
    }

    private static void verifyMissingJoin(BoundedAssemblyRequest request,
            String connectionId, String message) {
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(request.getConnections());
        for (int i = 0; i < connections.size(); i++) {
            if (connectionId.equals(connections.get(i).getId())) {
                connections.remove(i);
                break;
            }
        }
        PortCompatibilityPreflight.Result result = result(request,
                request.getAllElectricalContracts(), connections);
        require(result.getDecision()
                != PortCompatibilityPreflight.Decision.COMPATIBLE,
                message + " is rejected closed");
    }

    private static void verifyExtraTransitivePort(BoundedAssemblyRequest request) {
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(request.getConnections());
        connections.add(new ElectricalConnection("extra-switched-port",
                Arrays.asList(ref("driver", "SWITCHED_SINK"),
                        ref("load", "SUPPLY"))));
        PortCompatibilityPreflight.Result result = result(request,
                request.getAllElectricalContracts(), connections);
        require(result.getDecision() == PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                "extra transitive switched port is rejected");
    }

    private static void verifyUnknownEvidence(BoundedAssemblyRequest request) {
        ElectricalBlockContract power = block(request, "power-adapter");
        ElectricalPortContract output = power.getPorts().get("POWER_OUT");
        ElectricalPortContract unknown = copy(output, output.getRole(),
                output.getDirection(), output.getBehavior(), output.getDrive(),
                output.getNominalVoltage(), Range.unknown(),
                output.getAllowedVoltage(), output.getLoading(),
                output.getCapacityAmps(), output.getDemandAmps(),
                output.getDigital());
        PortCompatibilityPreflight.Result result = result(request,
                replace(request.getAllElectricalContracts(), power,
                        replacePort(power, "POWER_OUT", unknown)),
                request.getConnections());
        require(result.getDecision()
                == PortCompatibilityPreflight.Decision.INSUFFICIENT_INFORMATION,
                "unknown supply evidence is rejected closed");
    }

    private static void verifyOvercurrent(BoundedAssemblyRequest request) {
        ElectricalBlockContract driver = block(request, "driver");
        ElectricalPortContract sink = driver.getPorts().get("SWITCHED_SINK");
        ElectricalPortContract undersized = copy(sink, sink.getRole(),
                sink.getDirection(), sink.getBehavior(), sink.getDrive(),
                sink.getNominalVoltage(), sink.getGuaranteedVoltage(),
                sink.getAllowedVoltage(), sink.getLoading(), Scalar.known(.010),
                sink.getDemandAmps(), sink.getDigital());
        PortCompatibilityPreflight.Result result = result(request,
                replace(request.getAllElectricalContracts(), driver,
                        replacePort(driver, "SWITCHED_SINK", undersized)),
                request.getConnections());
        require(result.getDecision() == PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                "switched sink overcurrent is rejected");
    }

    private static void verifyActiveLowRelation(BoundedAssemblyRequest request) {
        ElectricalConnection original = switched(request);
        SwitchedLowSideContract value = original.getSwitchedLowSideContract();
        SwitchedLowSideContract activeLow = new SwitchedLowSideContract(
                value.getSinkPort(), value.getLoadPort(), value.getSupplyPort(),
                value.getControlPort(), value.getReturnPorts(),
                value.getReferenceNetId(), value.getIsolationId(), false,
                value.getSinkCapacityAmps(), value.getLoadDemandAmps());
        PortCompatibilityPreflight.Result result = result(request,
                request.getAllElectricalContracts(), replaceConnection(
                        request.getConnections(), original,
                        new ElectricalConnection(original.getId(), original.getPorts(),
                                activeLow)));
        require(result.getDecision() == PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                "active-low switched relation is rejected");
    }

    private static void verifyActiveLowSource(BoundedAssemblyRequest request) {
        ElectricalBlockContract control = block(request, "control-adapter");
        ElectricalPortContract output = control.getPorts().get("CONTROL_OUT");
        ElectricalPortContract activeLow = copy(output, output.getRole(),
                output.getDirection(), output.getBehavior(), output.getDrive(),
                output.getNominalVoltage(), output.getGuaranteedVoltage(),
                output.getAllowedVoltage(), output.getLoading(),
                output.getCapacityAmps(), output.getDemandAmps(),
                new ElectricalPortContract.Digital(ActiveLevel.LOW,
                        output.getDigital().getLowMaximum(),
                        output.getDigital().getHighMinimum(),
                        output.getDigital().getInputLowMaximum(),
                        output.getDigital().getInputHighMinimum()));
        PortCompatibilityPreflight.Result result = result(request,
                replace(request.getAllElectricalContracts(), control,
                        replacePort(control, "CONTROL_OUT", activeLow)),
                request.getConnections());
        require(result.getDecision() == PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                "active-low control source is rejected");
    }

    private static void verifyUnsafeOffVoltage(BoundedAssemblyRequest request) {
        ElectricalBlockContract driver = block(request, "driver");
        ElectricalPortContract sink = driver.getPorts().get("SWITCHED_SINK");
        ElectricalPortContract unsafe = copy(sink, sink.getRole(),
                sink.getDirection(), sink.getBehavior(), sink.getDrive(),
                sink.getNominalVoltage(), sink.getGuaranteedVoltage(),
                Range.known(.0, 5.4), sink.getLoading(), sink.getCapacityAmps(),
                sink.getDemandAmps(), sink.getDigital());
        PortCompatibilityPreflight.Result result = result(request,
                replace(request.getAllElectricalContracts(), driver,
                        replacePort(driver, "SWITCHED_SINK", unsafe)),
                request.getConnections());
        require(result.getDecision() == PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                "unsafe off-state drain range is rejected");
    }

    private static void verifyBroadSupplyGuarantee(BoundedAssemblyRequest request) {
        ElectricalBlockContract power = block(request, "power-adapter");
        ElectricalPortContract output = power.getPorts().get("POWER_OUT");
        ElectricalPortContract broad = copy(output, output.getRole(),
                output.getDirection(), output.getBehavior(), output.getDrive(),
                output.getNominalVoltage(), Range.known(4.0, 5.5),
                output.getAllowedVoltage(), output.getLoading(),
                output.getCapacityAmps(), output.getDemandAmps(),
                output.getDigital());
        PortCompatibilityPreflight.Result result = result(request,
                replace(request.getAllElectricalContracts(), power,
                        replacePort(power, "POWER_OUT", broad)),
                request.getConnections());
        require(result.getDecision() == PortCompatibilityPreflight.Decision.INCOMPATIBLE,
                "overbroad supply guarantee is rejected");
    }

    private static void verifyUntypedOpenDrain(BoundedAssemblyRequest request) {
        ElectricalConnection original = switched(request);
        PortCompatibilityPreflight.Result result = result(request,
                request.getAllElectricalContracts(), replaceConnection(
                        request.getConnections(), original,
                        new ElectricalConnection(original.getId(), original.getPorts())));
        require(result.getDecision()
                == PortCompatibilityPreflight.Decision.INSUFFICIENT_INFORMATION,
                "untyped open-drain transfer remains fail-closed");
    }

    private static PortCompatibilityPreflight.Result result(BoundedAssemblyRequest request) {
        return result(request, request.getAllElectricalContracts(), request.getConnections());
    }

    private static PortCompatibilityPreflight.Result result(BoundedAssemblyRequest request,
            Collection<ElectricalBlockContract> blocks,
            Collection<ElectricalConnection> connections) {
        return PortCompatibilityPreflight.check(
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION,
                blocks, connections);
    }

    private static ElectricalConnection switched(BoundedAssemblyRequest request) {
        for (ElectricalConnection connection : request.getConnections())
            if (ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID
                    .equals(connection.getId())) return connection;
        throw new AssertionError("missing typed switched connection");
    }

    private static ElectricalBlockContract block(BoundedAssemblyRequest request,
            String key) {
        for (ElectricalBlockContract block : request.getAllElectricalContracts())
            if (key.equals(block.getDescriptor().getInstanceKey())) return block;
        throw new AssertionError("missing block " + key);
    }

    private static List<ElectricalBlockContract> replace(
            Collection<ElectricalBlockContract> blocks,
            ElectricalBlockContract oldBlock, ElectricalBlockContract replacement) {
        ArrayList<ElectricalBlockContract> result =
                new ArrayList<ElectricalBlockContract>();
        for (ElectricalBlockContract block : blocks)
            result.add(block == oldBlock ? replacement : block);
        return result;
    }

    private static ElectricalBlockContract replacePort(ElectricalBlockContract block,
            String portId, ElectricalPortContract replacement) {
        ArrayList<ElectricalPortContract> ports =
                new ArrayList<ElectricalPortContract>();
        for (ElectricalPortContract port : block.getPorts().values())
            ports.add(port.getId().equals(portId) ? replacement : port);
        return new ElectricalBlockContract(block.getDescriptor(), ports,
                block.getAdapters().values());
    }

    private static List<ElectricalConnection> replaceConnection(
            Collection<ElectricalConnection> connections,
            ElectricalConnection oldConnection,
            ElectricalConnection replacement) {
        ArrayList<ElectricalConnection> result =
                new ArrayList<ElectricalConnection>();
        for (ElectricalConnection connection : connections)
            result.add(connection == oldConnection ? replacement : connection);
        return result;
    }

    private static ElectricalConnection.PortRef ref(String block, String port) {
        return new ElectricalConnection.PortRef(block, port);
    }

    private static ElectricalPortContract copy(ElectricalPortContract original,
            Role role, Direction direction, Behavior behavior, Drive drive,
            Scalar nominal, Range guaranteed, Range allowed, Loading loading,
            Scalar capacity, Scalar demand, ElectricalPortContract.Digital digital) {
        return new ElectricalPortContract(original.getId(), role, direction,
                behavior, drive, original.getDomain(), nominal, guaranteed, allowed,
                loading, capacity, demand, digital, original.getMergePolicy(),
                original.getAccessRequirement(), original.getAccessProvision());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
