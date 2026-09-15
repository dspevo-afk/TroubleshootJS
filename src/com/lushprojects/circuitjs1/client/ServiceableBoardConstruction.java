package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Completes service ownership for supported physical packages before admission.
 * It receives construction data, never a fault selection or diagnostic recipe.
 * Existing typed mutation, inventory, stress and transaction owners are reused.
 */
final class ServiceableBoardConstruction {
    private ServiceableBoardConstruction() { }

    static void complete(TroubleshootBoard board, Vector<CircuitElm> elements,
            GeneratedComponentBindings components, GeneratedComponentConnectionBindings connections,
            BoardPhysicalSpecifications specifications, PhysicalBoardRuntime runtime) {
        Vector<String> ids = board.getComponentIds();
        Collections.sort(ids);
        for (String id : ids) {
            if (runtime.getWorkbenchPartsProvider(id) != null) continue;
            PhysicalPart<?> original = runtime.getInstalledPart(id);
            if (original == null) throw new IllegalStateException("Missing physical service part: " + id);
            PhysicalSpecification spec = original.getSpecification();
            if (spec instanceof BasicPhysicalSpecification || spec instanceof FactoryLinkSpecification) {
                completeBasic(board, elements, components, connections, runtime, original, id);
                continue;
            }
            if (!(spec instanceof ResistorNameplate || spec instanceof LedNameplate ||
                    spec instanceof DiodeNameplate || spec instanceof NpnSpecification ||
                    spec instanceof NmosSpecification || spec instanceof CapacitorSpecification)) continue;
            CircuitElm primary = components.getSingleElement(id);
            Vector<GeneratedComponentConnectionBinding> declared = connections.getForComponentOrEmpty(id);
            if (!declared.isEmpty() && declared.size() != original.getTerminalCount())
                throw new IllegalStateException("Incomplete physical service declaration: " + id);
            // Directly connected supporting devices need independent component
            // posts before a lead can be lifted. Copper endpoints remain fixed.
            if (declared.isEmpty()) relocate(primary, board, components, connections, elements);
            PhysicalPart<?> part = typedOriginal(original, primary, elements, components, id);
            WireElm[] leads = new WireElm[part.getTerminalCount()];
            for (int terminal = 0; terminal < leads.length; terminal++) {
                String name = part.getPackage().getTerminalIds().get(terminal);
                String padId = pad(board, id, name);
                CircuitMeasurementEndpoint endpoint = terminal(part, padId);
                CircuitMeasurementEndpoint boardEndpoint = board.getSimulationBindings().getEndpoint(padId);
                GeneratedComponentConnectionBinding binding = connections.getOrNull(padId);
                WireElm wire;
                if (binding == null) {
                    Point b = point(boardEndpoint), p = point(endpoint);
                    wire = new WireElm(b.x, b.y);
                    wire.x2 = p.x; wire.y2 = p.y; wire.setPoints();
                    elements.add(wire);
                    connections.bind(id, padId, boardEndpoint, endpoint, wire);
                } else {
                    if (!(binding.getConnectionElement() instanceof WireElm))
                        throw new IllegalStateException("Unsupported physical lead attachment: " + padId);
                    wire = (WireElm)binding.getConnectionElement();
                    connections.completeConstructionEndpoint(padId, endpoint);
                }
                // Existing two-terminal slots own board->first / second->board
                // attachments; transistor slots own board->component on all pins.
                boolean reverse = terminal == 1 && leads.length == 2;
                Point first = point(reverse ? endpoint : boardEndpoint);
                Point second = point(reverse ? boardEndpoint : endpoint);
                wire.x = first.x; wire.y = first.y; wire.x2 = second.x; wire.y2 = second.y; wire.setPoints();
                leads[terminal] = wire;
            }
            PhysicalBoardSlot slot = original.getBoardSlot();
            runtime.prepareServicePart(original, part);
            register(runtime, slot, part, leads);
        }
    }

    private static void completeBasic(TroubleshootBoard board, Vector<CircuitElm> elements,
            GeneratedComponentBindings components, GeneratedComponentConnectionBindings connections,
            PhysicalBoardRuntime runtime, PhysicalPart<?> original, String id) {
        PhysicalPackage pkg = original.getPackage();
        boolean connector = pkg.isEquivalentTo(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2) ||
            pkg.isEquivalentTo(PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2);
        // Developer-only arbitrary packages retain their declared fixed behavior.
        // The playable-family coverage gate rejects any unowned current position.
        boolean factoryLink = original.getSpecification() instanceof FactoryLinkSpecification;
        if (!connector && !factoryLink && !pkg.isEquivalentTo(PhysicalPackages.AXIAL_FUSE)) return;
        Vector<CircuitElm> backing;
        WireElm[] docking = new WireElm[0];
        if (connector) {
            backing = newServiceBacking(pkg, null, elements); elements.addAll(backing);
            if (components.hasComponentBinding(id))
                components.completeServiceBinding(id, backing.get(0), backing.get(1));
            else {
                components.bindComponent(id, backing.get(0)); components.bindAuxiliaryComponentElement(id, backing.get(1));
            }
            CircuitPostMeasurementEndpoint[] harness = connections.getConnectorHarness(id);
            if (harness != null) {
                docking = new WireElm[2];
                for (int i = 0; i < 2; i++) {
                    preserveCopper(harness[i], board, components, connections, elements);
                    Point free = freeServicePoint(elements);
                    movePost(harness[i], free);
                    docking[i] = wire(free, backing.get(i).getPost(1)); elements.add(docking[i]);
                }
            }
        } else if (factoryLink) {
            backing = new Vector<CircuitElm>(); backing.add(components.getSingleElement(id));
            FactoryLinkSpecification.requireBacking(original.getSpecification(), pkg, backing);
            if (connections.getForComponentOrEmpty(id).isEmpty())
                relocate(backing.get(0), board, components, connections, elements);
        } else if (pkg.isEquivalentTo(PhysicalPackages.AXIAL_FUSE) && components.hasComponentBinding(id) &&
                components.getSingleElement(id) instanceof ProtectionFuseElm) {
            backing = new Vector<CircuitElm>(); backing.add(components.getSingleElement(id));
            if (connections.getForComponentOrEmpty(id).isEmpty()) relocate(backing.get(0), board, components, connections, elements);
        } else throw mismatch(id);
        PhysicalServicePart part = new PhysicalServicePart(original.getId(),
            original.getSpecification(), original.getPlayerVisibleNameplate(), pkg, backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, id));
        WireElm[] leads = new WireElm[2];
        for (int i = 0; i < 2; i++) {
            String padId = pad(board, id, pkg.getTerminalIds().get(i));
            CircuitMeasurementEndpoint b = board.getSimulationBindings().getEndpoint(padId);
            CircuitMeasurementEndpoint p = part.getTerminal(i).getEndpoint();
            GeneratedComponentConnectionBinding binding = connections.getOrNull(padId);
            if (binding == null) {
                leads[i] = wire(point(b), point(p)); elements.add(leads[i]);
                connections.bind(id, padId, b, p, leads[i]);
            } else {
                if (!(binding.getConnectionElement() instanceof WireElm)) throw mismatch(id);
                leads[i] = (WireElm)binding.getConnectionElement();
                Point bp = point(b), pp = point(p);
                leads[i].x = bp.x; leads[i].y = bp.y; leads[i].x2 = pp.x; leads[i].y2 = pp.y; leads[i].setPoints();
                connections.completeConstructionEndpoint(padId, p);
            }
        }
        PhysicalBoardSlot slot = original.getBoardSlot(); runtime.prepareServicePart(original, part);
        PhysicalPartInventory<PhysicalServicePart> inventory = new PhysicalPartInventory<PhysicalServicePart>(
            runtime, id + "_REPLACEMENTS", PhysicalServicePart.class); inventory.add(part);
        runtime.registerCapability(new ReplaceableServiceBoardCapability(new ServiceComponentSlot(slot, part, leads, docking,
            connections.getConnectorHarness(id)),
            inventory, part));
    }

    static Vector<CircuitElm> newServiceBacking(PhysicalPackage pkg, CircuitElm prototype, Vector<CircuitElm> active) {
        Vector<CircuitElm> occupied = new Vector<CircuitElm>(active), result = new Vector<CircuitElm>();
        if (pkg == PhysicalPackages.RAISED_FACTORY_LINK) {
            Vector<CircuitElm> check = new Vector<CircuitElm>(); check.add(prototype);
            FactoryLinkSpecification.requireBacking(FactoryLinkSpecification.STANDARD, pkg, check);
            Point p = freeServicePoint(occupied);
            result.add(FactoryLinkSpecification.STANDARD.createElement(p.x, p.y));
        } else if (pkg.isEquivalentTo(PhysicalPackages.AXIAL_FUSE)) {
            if (!(prototype instanceof ProtectionFuseElm)) throw mismatch(pkg.getId());
            Point p = freeServicePoint(occupied);
            ProtectionFuseElm fuse = new ProtectionFuseElm(p.x, p.y);
            fuse.x2 = p.x + 16; fuse.y2 = p.y; fuse.setPoints();
            fuse.resistance = ((ProtectionFuseElm)prototype).resistance;
            fuse.i2t = ((ProtectionFuseElm)prototype).i2t; result.add(fuse);
        } else if (pkg.isEquivalentTo(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2) ||
                pkg.isEquivalentTo(PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2)) {
            for (int i = 0; i < 2; i++) {
                Point p = freeServicePoint(occupied); WireElm pin = wire(p, new Point(p.x + 16, p.y));
                result.add(pin); occupied.add(pin);
            }
        } else throw mismatch(pkg.getId());
        return result;
    }

    private static WireElm wire(Point first, Point second) {
        WireElm wire = new WireElm(first.x, first.y);
        wire.x2 = second.x; wire.y2 = second.y; wire.setPoints(); return wire;
    }
    private static Point freeServicePoint(Vector<CircuitElm> elements) {
        for (int trial = 0; trial < 8192; trial++) {
            int x = 48000 + trial * 64, y = 48000; boolean free = true;
            for (CircuitElm element : elements) for (int i = 0; i < element.getPostCount(); i++) {
                Point p = element.getPost(i); if (p.y == y && p.x >= x && p.x <= x + 16) free = false;
            }
            if (free) return new Point(x, y);
        }
        throw new IllegalStateException("No free service coordinates");
    }
    private static void movePost(CircuitPostMeasurementEndpoint endpoint, Point to) {
        CircuitElm element = endpoint.getElement();
        if (element.getPostCount() != 2 || endpoint.getPostIndex() > 1)
            throw new IllegalStateException("Unsupported external cable boundary");
        if (endpoint.getPostIndex() == 0) { element.x = to.x; element.y = to.y; }
        else { element.x2 = to.x; element.y2 = to.y; }
        element.setPoints();
    }
    private static void preserveCopper(CircuitPostMeasurementEndpoint moved, TroubleshootBoard board,
            GeneratedComponentBindings components, GeneratedComponentConnectionBindings connections, Vector<CircuitElm> elements) {
        CircuitMeasurementEndpoint persistent = null;
        for (String pad : board.getPadIds()) {
            CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(pad);
            if (GeneratedComponentConnectionBindings.sameEndpoint(endpoint, moved)) {
                if (persistent == null) {
                    persistent = copperAnchor(endpoint, board, components, connections, elements);
                }
                board.getSimulationBindings().completeServiceAnchor(pad, endpoint, persistent);
                connections.completeConstructionBoardEndpoint(pad, persistent);
            }
        }
    }

    private static PhysicalPart<?> typedOriginal(PhysicalPart<?> original, CircuitElm primary,
            Vector<CircuitElm> elements, GeneratedComponentBindings components, String componentId) {
        String id = original.getId();
        PhysicalSpecification spec = original.getSpecification();
        PhysicalPartProvenance provenance = new PhysicalPartProvenance(
            PhysicalPartProvenance.GENERATED_ORIGINAL, componentId);
        if (spec instanceof ResistorNameplate) {
            if (!(primary instanceof ResistorElm)) throw mismatch(componentId);
            if (original instanceof PhysicalResistorPart &&
                    ((PhysicalResistorPart)original).getSecondaryOpenPath() != null) return original;
            ResistorSecondaryOpenPath secondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(primary, 1));
            elements.add(secondary.getSimulationElement());
            components.bindAuxiliaryComponentElement(componentId, secondary.getSimulationElement());
            return new PhysicalResistorPart(id, (ResistorNameplate)spec, (ResistorNameplate)spec,
                original.getPlayerVisibleNameplate(), (ResistorElm)primary, null, secondary,
                ResistorPartLocation.INSTALLED, provenance);
        }
        if (spec instanceof LedNameplate) {
            if (!(primary instanceof LEDElm)) throw mismatch(componentId);
            return original instanceof PhysicalLedPart ? original : new PhysicalLedPart(id,
                (LedNameplate)spec, (LedNameplate)spec, (LEDElm)primary, false,
                LedPartLocation.INSTALLED, provenance);
        }
        if (spec instanceof DiodeNameplate) {
            if (!(primary instanceof DiodeElm)) throw mismatch(componentId);
            return original instanceof PhysicalDiodePart ? original : new PhysicalDiodePart(id,
                (DiodeNameplate)spec, (DiodeNameplate)spec, (DiodeElm)primary, null, false,
                DiodePartLocation.INSTALLED, provenance);
        }
        if (spec instanceof NpnSpecification) {
            if (!(primary instanceof NTransistorElm)) throw mismatch(componentId);
            return original instanceof PhysicalNpnPart ? original : new PhysicalNpnPart(id,
                (NpnSpecification)spec, original.getPlayerVisibleNameplate().forPhysicalPartId(id), (NTransistorElm)primary,
                null, NpnPartLocation.INSTALLED, provenance);
        }
        if (spec instanceof NmosSpecification) {
            if (!(primary instanceof NMosfetElm)) throw mismatch(componentId);
            return original instanceof PhysicalNmosPart ? original : new PhysicalNmosPart(id,
                (NmosSpecification)spec, original.getPlayerVisibleNameplate().forPhysicalPartId(id), (NMosfetElm)primary,
                null, NmosPartLocation.INSTALLED, provenance);
        }
        if (spec instanceof CapacitorSpecification) {
            if (!(primary instanceof CapacitorElm)) throw mismatch(componentId);
            return original instanceof PhysicalCapacitorPart ? original : new PhysicalCapacitorPart(id,
                (CapacitorSpecification)spec, original.getPlayerVisibleNameplate().forPhysicalPartId(id),
                (CapacitorElm)primary, null, CapacitorPartLocation.INSTALLED, provenance);
        }
        throw mismatch(componentId);
    }

    private static void register(PhysicalBoardRuntime runtime, PhysicalBoardSlot slot,
            PhysicalPart<?> part, WireElm[] wires) {
        String id = slot.getComponentId();
        if (part instanceof PhysicalResistorPart) {
            PhysicalResistorPart resistor = (PhysicalResistorPart)part;
            PhysicalPartInventory<PhysicalResistorPart> inventory = new PhysicalPartInventory<PhysicalResistorPart>(
                runtime, id + "_REPLACEMENTS", PhysicalResistorPart.class); inventory.add(resistor);
            runtime.registerCapability(new ReplaceableResistorBoardCapability(key(runtime, ReplaceableResistorBoardCapability.ID, id),
                new ReplaceableComponentSlot(id, resistor.getSpecification(), resistor, wires[0], wires[1], slot),
                inventory, new ResistorReplacementCatalog()));
        } else if (part instanceof PhysicalLedPart) {
            PhysicalLedPart led = (PhysicalLedPart)part;
            PhysicalPartInventory<PhysicalLedPart> inventory = new PhysicalPartInventory<PhysicalLedPart>(
                runtime, id + "_REPLACEMENTS", PhysicalLedPart.class); inventory.add(led);
            runtime.registerCapability(new ReplaceableLedBoardCapability(
                new LedComponentSlot(id, led.getSpecification(), led, wires[0], wires[1], slot), inventory,
                new LedReplacementCatalog(), key(runtime, ReplaceableLedBoardCapability.ID, id)));
        } else if (part instanceof PhysicalDiodePart) {
            PhysicalDiodePart diode = (PhysicalDiodePart)part;
            PhysicalPartInventory<PhysicalDiodePart> inventory = new PhysicalPartInventory<PhysicalDiodePart>(
                runtime, id + "_REPLACEMENTS", PhysicalDiodePart.class); inventory.add(diode);
            runtime.registerCapability(new ReplaceableDiodeBoardCapability(
                new DiodeComponentSlot(id, diode.getSpecification(), diode, wires[0], wires[1], slot), inventory,
                new DiodeReplacementCatalog(), key(runtime, ReplaceableDiodeBoardCapability.ID, id)));
        } else if (part instanceof PhysicalNpnPart) {
            PhysicalNpnPart transistor = (PhysicalNpnPart)part;
            PhysicalPartInventory<PhysicalNpnPart> inventory = new PhysicalPartInventory<PhysicalNpnPart>(
                runtime, id + "_REPLACEMENTS", PhysicalNpnPart.class); inventory.add(transistor);
            runtime.registerCapability(new ReplaceableNpnBoardCapability(
                new NpnComponentSlot(id, transistor.getSpecification(), transistor, wires[0], wires[1], wires[2], slot), inventory,
                new NpnReplacementCatalog(), key(runtime, ReplaceableNpnBoardCapability.ID, id)));
        } else if (part instanceof PhysicalNmosPart) {
            PhysicalNmosPart transistor = (PhysicalNmosPart)part;
            PhysicalPartInventory<PhysicalNmosPart> inventory = new PhysicalPartInventory<PhysicalNmosPart>(
                runtime, id + "_REPLACEMENTS", PhysicalNmosPart.class); inventory.add(transistor);
            runtime.registerCapability(new ReplaceableNmosBoardCapability(
                new NmosComponentSlot(id, transistor.getSpecification(), transistor, wires[0], wires[1], wires[2], slot), inventory,
                new NmosReplacementCatalog(), key(runtime, ReplaceableNmosBoardCapability.ID, id)));
        } else if (part instanceof PhysicalCapacitorPart) {
            PhysicalCapacitorPart capacitor = (PhysicalCapacitorPart)part;
            PhysicalPartInventory<PhysicalCapacitorPart> inventory = new PhysicalPartInventory<PhysicalCapacitorPart>(
                runtime, id + "_REPLACEMENTS", PhysicalCapacitorPart.class); inventory.add(capacitor);
            runtime.registerCapability(new ReplaceableCapacitorBoardCapability(
                new CapacitorComponentSlot(id, capacitor.getSpecification(), capacitor, wires[0], wires[1], slot), inventory,
                new CapacitorReplacementCatalog(capacitor.getSpecification().getPhysicalPackage()),
                key(runtime, ReplaceableCapacitorBoardCapability.ID, id)));
        } else throw mismatch(id);
    }

    private static String key(PhysicalBoardRuntime runtime, String base, String id) {
        return runtime.getCapability(base) == null ? base : base + "_" + id;
    }
    private static IllegalStateException mismatch(String id) {
        return new IllegalStateException("Physical service specification/backing mismatch: " + id);
    }
    private static String pad(TroubleshootBoard board, String id, String terminal) {
        for (String pad : board.getComponent(id).getPadIds())
            if (terminal.equals(board.getPad(pad).getTerminalId())) return pad;
        throw new IllegalStateException("Missing service terminal: " + id + "/" + terminal);
    }
    private static CircuitMeasurementEndpoint terminal(PhysicalPart<?> part, String pad) {
        if (part instanceof PhysicalDiodePart) return ((PhysicalDiodePart)part).getTerminalForBoardPad(pad);
        if (part instanceof PhysicalLedPart) return ((PhysicalLedPart)part).getTerminalForBoardPad(pad);
        for (PhysicalPartTerminal terminal : part.getTerminals())
            if (pad.endsWith("." + terminal.getTerminalName())) return terminal.getEndpoint();
        throw new IllegalStateException("Missing physical service endpoint: " + pad);
    }
    private static Point point(CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint)) throw new IllegalStateException("Service requires actual CircuitJS posts");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint)endpoint;
        return post.getElement().getPost(post.getPostIndex());
    }
    private static void relocate(CircuitElm primary, TroubleshootBoard board, GeneratedComponentBindings components,
            GeneratedComponentConnectionBindings connections, Vector<CircuitElm> elements) {
        for (String pad : board.getPadIds()) {
            CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)board.getSimulationBindings().getEndpoint(pad);
            if (endpoint.getElement() == primary) {
                CircuitMeasurementEndpoint persistent = copperAnchor(endpoint, board, components, connections, elements);
                board.getSimulationBindings().completeServiceAnchor(pad, endpoint, persistent);
                connections.completeConstructionBoardEndpoint(pad, persistent);
            }
        }
        // Translate a directly wired supporting device to an unused electrical
        // island; new owned attachments restore exactly its previous net map.
        for (int trial = 0; trial < 4096; trial++) {
            int dx = 16000 - primary.x + (trial % 32) * 512;
            int dy = 16000 - primary.y + (trial / 32) * 512;
            boolean free = true;
            for (int i = 0; i < primary.getPostCount() && free; i++) {
                Point p = primary.getPost(i);
                for (CircuitElm other : elements) for (int j = 0; j < other.getPostCount(); j++) {
                    Point q = other.getPost(j);
                    if (q.x == p.x + dx && q.y == p.y + dy) free = false;
                }
            }
            if (free) { primary.move(dx, dy); return; }
        }
        throw new IllegalStateException("No free service backing coordinates");
    }

    /** Reuse a persistent copper post instead of adding a redundant dangling wire. */
    private static CircuitMeasurementEndpoint copperAnchor(CircuitMeasurementEndpoint endpoint, TroubleshootBoard board,
            GeneratedComponentBindings components, GeneratedComponentConnectionBindings connections, Vector<CircuitElm> elements) {
        Point at = point(endpoint);
        for (CircuitElm candidate : elements) {
            if (!(candidate instanceof WireElm) || connections.isConnectionElement(candidate)) continue;
            boolean physical = false;
            for (String id : board.getComponentIds()) {
                if (components.isElementBoundToComponent(id, candidate)) physical = true;
                CircuitPostMeasurementEndpoint[] harness = connections.getConnectorHarness(id);
                if (harness != null) for (CircuitPostMeasurementEndpoint contact : harness)
                    if (contact.getElement() == candidate) physical = true;
            }
            if (physical) continue;
            for (int post = 0; post < candidate.getPostCount(); post++)
                if (candidate.getPost(post).equals(at)) return new CircuitPostMeasurementEndpoint(candidate, post);
        }
        WireElm anchor = wire(at, freeAnchor(elements)); elements.add(anchor);
        return new CircuitPostMeasurementEndpoint(anchor, 0);
    }

    private static Point freeAnchor(Vector<CircuitElm> elements) {
        for (int trial = 0; trial < 4096; trial++) {
            int x = 64000 + trial * 32, y = 32000;
            boolean free = true;
            for (CircuitElm element : elements) for (int i = 0; i < element.getPostCount(); i++) {
                Point p = element.getPost(i);
                if (p.x == x && p.y == y) free = false;
            }
            if (free) return new Point(x, y);
        }
        throw new IllegalStateException("No free copper anchor coordinates");
    }
}
