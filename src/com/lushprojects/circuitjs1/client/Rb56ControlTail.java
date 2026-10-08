package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Constructs only the declared E02/E04 control tail in its caller's one actual graph. */
final class Rb56ControlTail {
    static final class Result {
        final Rb56Plan plan;
        final LinearRegulatorElm regulator;
        final E04SensorControlModel.RailContract decisionRail;
        final E04SensorControlModel.Configuration configuration;
        final E04SensorControlModel.Variant variant;
        final Map<String, E04SensorControlModel.DecisionElement> decisions;
        final Map<String, ServiceRelayElm> relays;
        final Map<String, CircuitElm> backing;
        final Map<String, LimitedDcSupplyElm> sensorSources;
        final Map<String, BoundedExternalLoadElm> loads;
        final LimitedDcSupplyElm loadSource;
        private Result(Builder builder) {
            plan = builder.plan; regulator = builder.regulator;
            decisionRail = builder.decisionRail; configuration = builder.configuration; variant = builder.variant;
            decisions = Collections.unmodifiableMap(new TreeMap<String, E04SensorControlModel.DecisionElement>(builder.decisions));
            relays = Collections.unmodifiableMap(new TreeMap<String, ServiceRelayElm>(builder.relays));
            backing = Collections.unmodifiableMap(new TreeMap<String, CircuitElm>(builder.backing));
            sensorSources = Collections.unmodifiableMap(new TreeMap<String, LimitedDcSupplyElm>(builder.sensorSources));
            loads = Collections.unmodifiableMap(new TreeMap<String, BoundedExternalLoadElm>(builder.loads));
            loadSource = builder.loadSource;
        }
    }
    private Rb56ControlTail() { }
    static Result build(RelayOutputGenerator.Assembly assembly, Rb56Plan plan) {
        if (assembly == null || plan == null || !assembly.board.getId().equals(Rb56Plan.FAMILY_ID + "_BOARD"))
            throw new IllegalArgumentException("Missing or foreign RB56 control construction owner");
        // All logical nets belong to the parent assembly. In particular RAIL12
        // must already identify the adopted converter output, not another supply.
        for (Rb56Plan.Part part : plan.tailParts()) {
            BoardComponent declared = assembly.board.getComponent(part.id);
            if (declared == null || !part.type.equals(declared.getType()) ||
                    !part.physicalPackage.isEquivalentTo(declared.getPhysicalPackage()))
                throw new IllegalArgumentException("RB56 tail disagrees with its declared package: " + part.id);
            for (String net : part.netIds()) if (!assembly.nets.containsKey(net))
                throw new IllegalArgumentException("RB56 tail has no parent net anchor: " + net);
        }
        Builder builder = new Builder(assembly, plan);
        for (Rb56Plan.Part part : plan.tailParts()) builder.construct(part);
        if (builder.regulator == null || builder.loadSource == null ||
                builder.decisions.size() != plan.channelCount || builder.relays.size() != plan.channelCount ||
                builder.backing.size() != plan.tailParts().size())
            throw new IllegalStateException("Incomplete RB56 control tail");
        // The caller owns full-stage construction/abort and physical installation.
        // No second ground may bond CTRL_RETURN to the primary source reference.
        return new Result(builder);
    }

    private static final class Builder {
        final RelayOutputGenerator.Assembly assembly;
        final Rb56Plan plan;
        final RailRegulationContract regulation = RailRegulationContract.linear5V();
        final E04SensorControlModel.RailContract decisionRail = E04SensorControlModel.adaptE02Rail(regulation);
        final E04SensorControlModel.Configuration configuration = E04SensorControlModel.Configuration.defaults(decisionRail);
        final E04SensorControlModel.Variant variant;
        final TreeMap<String, E04SensorControlModel.DecisionElement> decisions = new TreeMap<String, E04SensorControlModel.DecisionElement>();
        final TreeMap<String, ServiceRelayElm> relays = new TreeMap<String, ServiceRelayElm>();
        final TreeMap<String, CircuitElm> backing = new TreeMap<String, CircuitElm>();
        final TreeMap<String, LimitedDcSupplyElm> sensorSources = new TreeMap<String, LimitedDcSupplyElm>();
        final TreeMap<String, BoundedExternalLoadElm> loads = new TreeMap<String, BoundedExternalLoadElm>();
        LinearRegulatorElm regulator;
        LimitedDcSupplyElm loadSource;
        int serial;
        Builder(RelayOutputGenerator.Assembly assembly, Rb56Plan plan) {
            this.assembly = assembly; this.plan = plan;
            variant = plan.sharedHystereticReference() ? E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE :
                E04SensorControlModel.Variant.DIRECT_THRESHOLD;
        }
        int nextX() { return 12000 + serial++ * 512; }
        void construct(Rb56Plan.Part part) {
            String[] nets = part.netIds();
            if (part.kind == Rb56Plan.Kind.SOURCE) {
                LimitedDcSupplyElm source = assembly.source(part.id, part.inputId, part.value, nets[0], nets[1], false);
                if (part.channel == null) loadSource = source;
                else {
                    // The connector marks the 5 V sensor range; its fresh real
                    // source starts at the declared Q60 LOW command, never a scenario flag.
                    source.configure(Rb56Plan.SENSOR_LOW_VOLTS, .25);
                    sensorSources.put(part.channel, source);
                }
                backing.put(part.id, source); return;
            }
            CircuitElm element;
            int[] posts;
            PhysicalSpecification specification;
            String label = part.type + " " + part.id;
            switch (part.kind) {
            case REGULATOR:
                regulator = new LinearRegulatorElm(nextX(), 400, regulation);
                regulator.drag(regulator.x + 96, 400); element = regulator;
                posts = new int[] {0, 1, 2, 3};
                specification = new BasicPhysicalSpecification("RB56_E02_LINEAR5V@1"); break;
            case DECISION:
                E04SensorControlModel.DecisionElement decision = new E04SensorControlModel.DecisionElement(
                    nextX(), 400, decisionRail, variant, configuration);
                decisions.put(part.channel, decision); element = decision;
                posts = new int[] {0, 1, 2, 3, 4};
                specification = new BasicPhysicalSpecification(decision.declarationIdentity()); break;
            case DRIVER:
                RelayDriverProvider driver = plan.driver(part.channel);
                element = driver.create(nextX(), 400); posts = driver.getPosts();
                specification = driver.getSpecification(part.id); label = "Low-side " + driver.getId() + " driver"; break;
            case RELAY:
                RelaySpecification relay = new RelaySpecification(part.value);
                ServiceRelayElm actual = relay.create(nextX(), 400);
                relays.put(part.channel, actual); element = actual; posts = RelaySpecification.POSTS;
                specification = relay; label = relay.label(); break;
            case RESISTOR:
                ResistorElm resistor = end(new ResistorElm(nextX(), 400), 80);
                resistor.setResistance(part.value); element = resistor; posts = new int[] {0, 1};
                specification = new ResistorNameplate(part.id, part.value, 5, .25); break;
            case CAPACITOR:
                CapacitorElm capacitor = end(new CapacitorElm(nextX(), 400), 80);
                capacitor.setCapacitance(part.value); capacitor.flags |= CapacitorElm.FLAG_BACK_EULER;
                capacitor.initialVoltage = 0; capacitor.reset();
                element = capacitor; posts = new int[] {0, 1};
                specification = capacitorSpecification(part); break;
            case DIODE:
                DiodeElm diode = end(new DiodeElm(nextX(), 400), 80);
                diode.modelName = "1N4148"; diode.setup(); element = diode; posts = new int[] {0, 1};
                specification = new DiodeNameplate(part.id, "1N4148 flyback diode", "1N4148"); break;
            case LED:
                LEDElm led = end(new LEDElm(nextX(), 400), 80);
                led.modelName = "default-led"; led.setup(); element = led; posts = new int[] {0, 1};
                specification = new LedNameplate(part.id, "Rail/output status LED", "default-led", led.colorR, led.colorG, led.colorB); break;
            case OUTPUT_HEADER:
                BoundedExternalLoadElm load = end(new BoundedExternalLoadElm(nextX(), 400, part.value), 80);
                loads.put(part.channel, load); element = load; posts = new int[] {0, 1};
                specification = new BasicPhysicalSpecification("RB56_EXTERNAL_LOAD_180OHM@1");
                label = "External load / 180 Ohm"; break;
            default: throw new IllegalArgumentException("Power-stage package cannot be constructed by the control tail: " + part.id);
            }
            assembly.part(part.id, part.type, part.physicalPackage, part.terminalIds(), nets, posts, element,
                part.physicalPackage.getTerminalCount() == 2);
            backing.put(part.id, element);
            assembly.specifications.addPhysicalDefinition(part.id, specification, new PhysicalNameplate(part.id, label), part.physicalPackage);
            if (part.kind == Rb56Plan.Kind.REGULATOR) RegulatorPhysicalMapping.mapComponentTerminals(assembly.board, part.id, regulator);
            if (part.kind == Rb56Plan.Kind.OUTPUT_HEADER)
                assembly.connections.declareConnectorHarness(part.id, element, 0, element, 1);
        }
    }
    /** Current-only Q60 catalog recipes, shared by value/rating/package rather than reference designator. */
    static CapacitorSpecification capacitorSpecification(Rb56Plan.Part part) {
        if (part.kind != Rb56Plan.Kind.CAPACITOR)
            throw new IllegalArgumentException("Not a control-tail capacitor: " + part.id);
        String recipe, marking;
        if (part.value == 1e-6 && part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR)) {
            recipe = "CAP_Q60_1UF_25V_CERAMIC_BE0"; marking = "1 uF / 25 V ceramic capacitor";
        } else if (part.value == 100e-9 && part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR)) {
            recipe = "CAP_Q60_100NF_25V_CERAMIC_BE0"; marking = "100 nF / 25 V ceramic capacitor";
        } else if (part.value == 2.2e-5 && part.physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR)) {
            recipe = "CAP_Q60_22UF_25V_ELECTROLYTIC_BE0"; marking = "22 uF / 25 V electrolytic capacitor";
        } else throw new IllegalArgumentException("Unsupported control-tail capacitor recipe: " + part.id);
        return new CapacitorSpecification(recipe, part.value, 10, 25, part.physicalPackage,
            new CapacitorNameplate("Capacitor", marking), 0, true, 0);
    }
    private static <T extends CircuitElm> T end(T element, int width) {
        element.x2 = element.x + width; element.y2 = element.y; element.setPoints(); return element;
    }
}
