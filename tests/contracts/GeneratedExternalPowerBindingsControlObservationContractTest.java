package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Currentness contracts for the captured generated-board power controls. */
public final class GeneratedExternalPowerBindingsControlObservationContractTest {
    private static int assertions;

    private GeneratedExternalPowerBindingsControlObservationContractTest() { }

    public static void main(String[] args) {
        acceptsUnchangedObservation();
        appendedBindingInvalidatesOldObservation();
        abortedOwnerInvalidatesObservation();
        connectionChangeAndRestoreStillInvalidates();
        rawControlStateChangeInvalidates();
        missingBindingRemainsInvalid();
        uncontrolledBindingRetainsItsCapturedState();
        System.out.println("PASS: generated power control observation contracts assertions=" + assertions);
    }

    private static void acceptsUnchangedObservation() {
        Fixture fixture = new Fixture(1, true);
        GeneratedExternalPowerBindings.ControlObservation observation = fixture.bindings.observeControls();
        check(observation.isCurrent(), "unchanged complete control observation remains current");
    }

    private static void appendedBindingInvalidatesOldObservation() {
        Fixture fixture = new Fixture(1, true);
        GeneratedExternalPowerBindings.ControlObservation before = fixture.bindings.observeControls();
        check(before.isCurrent(), "complete observation is current before another declared input is added");

        addPowerInput(fixture.board, 2);
        fixture.bind("VIN2", new MutableControl(true));
        check(!before.isCurrent(), "appending the newly declared input binding invalidates the old observation");
        check(fixture.bindings.observeControls().isCurrent(), "a fresh observation accepts the appended complete registry");
    }

    private static void abortedOwnerInvalidatesObservation() {
        Fixture fixture = new Fixture(1, true);
        GeneratedExternalPowerBindings.ControlObservation observation = fixture.bindings.observeControls();
        check(observation.isCurrent(), "owner is live before abort");
        fixture.bindings.clearForAbortedConstruction(fixture.board);
        check(!observation.isCurrent(), "aborting construction invalidates prior observations");
    }

    private static void connectionChangeAndRestoreStillInvalidates() {
        Fixture fixture = new Fixture(1, true);
        ExternalPowerSimulationBinding binding = fixture.bindings.getBinding("VIN1");
        GeneratedExternalPowerBindings.ControlObservation observation = fixture.bindings.observeControls();
        binding.setConnected(false);
        binding.setConnected(true);
        check(binding.isConnected(), "connection state is restored to its captured value");
        check(binding.getConnectionRevision() == 2, "both connection transitions advance the revision");
        check(!observation.isCurrent(), "connection change and restoration invalidates by revision");
    }

    private static void rawControlStateChangeInvalidates() {
        Fixture fixture = new Fixture(1, true);
        long revision = fixture.bindings.getBinding("VIN1").getConnectionRevision();
        GeneratedExternalPowerBindings.ControlObservation observation = fixture.bindings.observeControls();
        fixture.controls.get(0).connected = false;
        check(fixture.bindings.getBinding("VIN1").getConnectionRevision() == revision,
            "raw control mutation bypasses the binding revision");
        check(!observation.isCurrent(), "live connection read catches raw underlying control mutation");
    }

    private static void missingBindingRemainsInvalid() {
        Fixture fixture = new Fixture(2, false);
        fixture.bind("VIN1", new MutableControl(true));
        GeneratedExternalPowerBindings.ControlObservation incomplete = fixture.bindings.observeControls();
        check(!incomplete.isCurrent(), "observation with a declared but missing binding is not current");

        fixture.bind("VIN2", new MutableControl(false));
        check(!incomplete.isCurrent(), "later binding does not make an earlier incomplete observation current");
        check(fixture.bindings.observeControls().isCurrent(), "new observation is current once every input is bound");
    }

    private static void uncontrolledBindingRetainsItsCapturedState() {
        TroubleshootBoard board = boardWithInputs(1);
        GeneratedExternalPowerBindings bindings = new GeneratedExternalPowerBindings(board);
        bindings.bindPowerInput("VIN1", new ExternalPowerSimulationBinding(backingElements()));
        GeneratedExternalPowerBindings.ControlObservation observation = bindings.observeControls();
        check(!bindings.getBinding("VIN1").hasControl(), "test binding has no external control");
        check(observation.isCurrent(), "unchanged observation remains current when a binding has no control");
    }

    private static TroubleshootBoard boardWithInputs(int count) {
        TroubleshootBoard board = new TroubleshootBoard("CONTROL_OBSERVATION");
        for (int index = 1; index <= count; index++) addPowerInput(board, index);
        board.validate();
        return board;
    }

    private static void addPowerInput(TroubleshootBoard board, int index) {
        String input = "VIN" + index;
        String component = "J" + index;
        String positiveNet = "VIN_NET" + index;
        String returnNet = "GND_NET" + index;
        String positivePad = component + ".1";
        String returnPad = component + ".2";
        board.addNet(new BoardNet(positiveNet));
        board.addNet(new BoardNet(returnNet));
        board.addComponent(new BoardComponent(component, "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, component));
        board.addPad(new BoardPad(positivePad, component, "1", positiveNet));
        board.addPad(new BoardPad(returnPad, component, "2", returnNet));
        board.addPowerInput(new ExternalBoardPowerInput(input, positivePad, returnPad,
            positiveNet, returnNet));
    }

    private static Vector<CircuitElm> backingElements() {
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(new ResistorElm(0, 0));
        return elements;
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final GeneratedExternalPowerBindings bindings;
        final Vector<MutableControl> controls = new Vector<MutableControl>();

        Fixture(int inputCount, boolean bindAll) {
            board = boardWithInputs(inputCount);
            bindings = new GeneratedExternalPowerBindings(board);
            if (bindAll) {
                for (int index = 1; index <= inputCount; index++)
                    bind("VIN" + index, new MutableControl(true));
            }
        }

        void bind(String id, MutableControl control) {
            controls.add(control);
            bindings.bindPowerInput(id, new ExternalPowerSimulationBinding(backingElements(), control));
        }
    }

    private static final class MutableControl implements ExternalPowerControl {
        boolean connected;

        MutableControl(boolean connected) { this.connected = connected; }

        public void setConnected(boolean connected) { this.connected = connected; }
        public boolean isConnected() { return connected; }
    }
}
