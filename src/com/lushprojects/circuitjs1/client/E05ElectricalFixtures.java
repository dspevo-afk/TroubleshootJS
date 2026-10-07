package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Concrete graphs for existing private solver proofs; no solver or player family. */
final class E05ElectricalFixtures {
    static final double PRIMARY_HENRIES = 4.0;
    static final double SECONDARY_OVER_PRIMARY_TURNS = .1;
    static final double COUPLING = .999;
    static final double DIRECT_CAP_FARADS = .000047;
    static final double DIRECT_BLEED_OHMS = 100000.0;
    static final double DIRECT_LOAD_OHMS = 20000.0;
    static final double SECONDARY_CAP_FARADS = .000470;
    static final double SECONDARY_BLEED_OHMS = 10000.0;
    static final double SECONDARY_LOAD_OHMS = 1000.0;

    private E05ElectricalFixtures() { }

    static Fixture rectifierBulk() {
        Fixture fixture = input();
        Point acA = new Point(192, 96);
        fixture.fuse = E05AcInputModel.fuse(new Point(64, 96), acA);
        fixture.elements.add(fixture.fuse);
        fixture.bridge = bridge(fixture.elements, acA, new Point(64, 256),
            new Point(384, 96), new Point(384, 256));
        fixture.bulk = capacitor(fixture.elements, fixture.bridge.positive,
            fixture.bridge.negative, DIRECT_CAP_FARADS);
        fixture.bleed = parallelResistor(fixture.elements, fixture.bridge,
            512, DIRECT_BLEED_OHMS);
        fixture.load = parallelResistor(fixture.elements, fixture.bridge,
            640, DIRECT_LOAD_OHMS);
        ground(fixture.elements, fixture.input.source.getPost(0));
        return fixture;
    }

    static Fixture isolatedTransformerBulk() {
        Fixture fixture = input();
        fixture.fuse = E05AcInputModel.fuse(new Point(64, 96), new Point(192, 96));
        fixture.elements.add(fixture.fuse);
        fixture.transformer = transformer(fixture.elements);
        // Fixture-only BE damps the measured open-primary trapezoidal voltage mode.
        // This is numerical dissipation, not physical winding loss or efficiency.
        // The separate transformerReference graph retains the A07 trapezoidal model.
        fixture.transformer.flags |= Inductor.FLAG_BACK_EULER;
        wire(fixture.elements, fixture.fuse.getPost(1), fixture.transformer.getPost(0));
        wire(fixture.elements, new Point(64, 256), fixture.transformer.getPost(2));
        // P1/P2/S1/S2 maps exactly to posts 0/2/1/3, PRIMARY/PRIMARY/SECONDARY/SECONDARY.
        fixture.bridge = bridge(fixture.elements, fixture.transformer.getPost(1),
            fixture.transformer.getPost(3), new Point(640, 160), new Point(640, 320));
        fixture.bulk = capacitor(fixture.elements, fixture.bridge.positive,
            fixture.bridge.negative, SECONDARY_CAP_FARADS);
        fixture.bleed = parallelResistor(fixture.elements, fixture.bridge,
            768, SECONDARY_BLEED_OHMS);
        fixture.load = parallelResistor(fixture.elements, fixture.bridge,
            896, SECONDARY_LOAD_OHMS);
        ground(fixture.elements, fixture.input.source.getPost(0));
        return fixture;
    }

    /** The transformer subgraph before rectification isolates the reference oracle.
     * reference: 0 primary numerical ground, 1 secondary numerical ground, 2 automatic.
     * bonds: 0 independent domains, 1 one real bond with no return, 2 a real closed return.
     */
    static Fixture transformerReference(int reference, int bonds, boolean reverseOrder) {
        if (reference < 0 || reference > 2 || bonds < 0 || bonds > 2)
            throw new IllegalArgumentException("Unsupported reference pilot variant");
        Fixture fixture = input();
        fixture.transformer = transformer(fixture.elements);
        wire(fixture.elements, new Point(64, 96), fixture.transformer.getPost(0));
        wire(fixture.elements, new Point(64, 256), fixture.transformer.getPost(2));
        fixture.load = resistor(fixture.transformer.getPost(1),
            fixture.transformer.getPost(3), SECONDARY_LOAD_OHMS);
        fixture.elements.add(fixture.load);
        if (bonds > 0) {
            fixture.bondA = resistor(fixture.transformer.getPost(0),
                fixture.transformer.getPost(3), 1000000.0);
            fixture.elements.add(fixture.bondA);
        }
        if (bonds > 1) {
            fixture.bondB = resistor(fixture.transformer.getPost(2),
                fixture.transformer.getPost(1), 1000000.0);
            fixture.elements.add(fixture.bondB);
        }
        if (reference == 0) ground(fixture.elements, fixture.input.source.getPost(0));
        if (reference == 1) ground(fixture.elements, fixture.transformer.getPost(3));
        if (reverseOrder) Collections.reverse(fixture.elements);
        return fixture;
    }

    private static Fixture input() {
        Fixture fixture = new Fixture();
        fixture.input = E05AcInputModel.create(-256, 96,
            new Point(64, 96), new Point(64, 256));
        fixture.elements.addAll(fixture.input.elements);
        return fixture;
    }

    private static TransformerElm transformer(Vector<CircuitElm> elements) {
        TransformerElm transformer = new TransformerElm(256, 160);
        transformer.drag(384, 256);
        transformer.inductance = PRIMARY_HENRIES;
        transformer.ratio = SECONDARY_OVER_PRIMARY_TURNS;
        transformer.couplingCoef = COUPLING;
        transformer.flags &= ~Inductor.FLAG_BACK_EULER; // Existing A07 trapezoidal model.
        elements.add(transformer);
        return transformer;
    }

    private static Bridge bridge(Vector<CircuitElm> elements, Point acA,
            Point acB, Point positive, Point negative) {
        // An actual closed series switch supplies the private D1-open test.
        // It is test/fault infrastructure, never an extra advertised package.
        Point linkEnd = new Point(acA.x + 96, acA.y);
        SwitchElm openDiode = end(new SwitchElm(acA.x, acA.y), linkEnd.x, linkEnd.y);
        elements.add(openDiode);
        DiodeElm d1 = diode(linkEnd, positive), d2 = diode(acB, positive);
        DiodeElm d3 = diode(negative, acA), d4 = diode(negative, acB);
        elements.add(d1); elements.add(d2); elements.add(d3); elements.add(d4);
        return new Bridge(positive, negative, new DiodeElm[] {d1, d2, d3, d4}, openDiode);
    }

    private static DiodeElm diode(Point anode, Point cathode) {
        DiodeElm diode = end(new DiodeElm(anode.x, anode.y), cathode.x, cathode.y);
        // Construction must not depend on a user's last selected diode model.
        diode.modelName = "default"; diode.model = null; diode.setup();
        return diode;
    }

    private static CapacitorElm capacitor(Vector<CircuitElm> elements,
            Point positive, Point negative, double farads) {
        CapacitorElm capacitor = end(new CapacitorElm(positive.x, positive.y),
            negative.x, negative.y);
        capacitor.capacitance = farads; capacitor.initialVoltage = 0;
        capacitor.flags |= CapacitorElm.FLAG_BACK_EULER;
        capacitor.reset(); // New owner only. OFF must never reset this capacitor.
        elements.add(capacitor);
        return capacitor;
    }

    private static ResistorElm parallelResistor(Vector<CircuitElm> elements,
            Bridge bridge, int x, double ohms) {
        ResistorElm resistor = resistor(new Point(x, bridge.positive.y),
            new Point(x, bridge.negative.y), ohms);
        elements.add(resistor);
        wire(elements, bridge.positive, resistor.getPost(0));
        wire(elements, bridge.negative, resistor.getPost(1));
        return resistor;
    }

    private static ResistorElm resistor(Point a, Point b, double ohms) {
        ResistorElm resistor = end(new ResistorElm(a.x, a.y), b.x, b.y);
        resistor.setResistance(ohms); return resistor;
    }

    private static void wire(Vector<CircuitElm> elements, Point a, Point b) {
        if (a.x != b.x || a.y != b.y)
            elements.add(end(new WireElm(a.x, a.y), b.x, b.y));
    }

    private static void ground(Vector<CircuitElm> elements, Point at) {
        elements.add(end(new GroundElm(at.x, at.y), at.x, at.y + 32));
    }

    private static <T extends CircuitElm> T end(T element, int x, int y) {
        element.x2 = x; element.y2 = y; element.setPoints(); return element;
    }

    static final class Fixture {
        final Vector<CircuitElm> elements = new Vector<CircuitElm>();
        E05AcInputModel.Input input;
        ProtectionFuseElm fuse;
        TransformerElm transformer;
        Bridge bridge;
        CapacitorElm bulk;
        ResistorElm bleed, load, bondA, bondB;
    }

    static final class Bridge {
        final Point positive, negative;
        final DiodeElm[] diodes;
        final SwitchElm openDiode;
        Bridge(Point positive, Point negative, DiodeElm[] diodes, SwitchElm openDiode) {
            this.positive = positive; this.negative = negative;
            this.diodes = diodes; this.openDiode = openDiode;
        }
    }
}
