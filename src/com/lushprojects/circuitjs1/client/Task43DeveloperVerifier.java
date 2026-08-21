package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Random;
import java.util.Vector;

/** Developer proof for the package-owned physical geometry contract. */
final class Task43DeveloperVerifier {
    private Task43DeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null)
            throw new IllegalStateException("Task 43 requires a generated board");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        String beforeIdentity = boardIdentity(instance);
        PcbFootprintRegistry footprints = StandardPcbFootprintProviders.createRegistry();
        PhysicalPartRenderRegistry renderers = StandardPhysicalPartRenderProviders.createRegistry();
        Vector<PhysicalPackage> registered = footprints.getRegisteredPackages();
        Vector<PhysicalPackage> rendered = renderers.getRegisteredPackages();
        require(registered.size() == rendered.size(),
            "Footprint/render package registries have different sizes");
        for (int index = 0; index < registered.size(); index++)
            require(registered.get(index).isEquivalentTo(rendered.get(index)),
                "Footprint/render package registration diverged: " +
                registered.get(index).getId());

        for (PhysicalPackage physicalPackage : registered)
            verifyPackage(physicalPackage, footprints, renderers);
        verifyGeneratedIdentity(instance);
        verifySelectedGeometryLifecycleCanary();
        verifyNegativeCanaries(registered);
        PcbR2DeveloperVerifier.verify();
        require(beforeIdentity.equals(boardIdentity(instance)),
            "Task 43 package canaries changed generated board identities");
        sim.setCircuitTitle("Task 43 physical geometry verification passed");
    }

    private static void verifyPackage(PhysicalPackage physicalPackage,
            PcbFootprintRegistry footprints, PhysicalPartRenderRegistry renderers) {
        PhysicalPackageGeometry geometry = physicalPackage.getGeometry();
        Vector<String> packageTerminals = physicalPackage.getTerminalIds();
        Vector<PhysicalPackage.GeometryVariant> variants = physicalPackage.getGeometryVariants();
        require(geometry.getWidth() > 0 && geometry.getHeight() > 0,
            "Package has non-positive dimensions: " + physicalPackage.getId());
        require(packageTerminals.equals(geometry.getTerminalIds()),
            "Package terminal IDs are not stable in geometry: " + physicalPackage.getId());
        require(variants.size() > 0 && physicalPackage.getDefaultLooseGeometry() == geometry &&
                physicalPackage.getGeometryVariant(
                    physicalPackage.getDefaultLooseGeometryVariantKey()) != null,
            "Package has no explicit default loose projection: " + physicalPackage.getId());
        require(renderers.hasProvider(physicalPackage),
            "Package has no render provider: " + physicalPackage.getId());
        if (physicalPackage.isDeveloperGeneric())
            require(geometry.isDeveloperGeneric(),
                "Developer package lost its explicit generic marker: " + physicalPackage.getId());
        else
            require(!geometry.isDeveloperGeneric() &&
                    geometry.getGeometryContractVersionValue() ==
                        PcbGeometryContractVersion.CURRENT,
                "Production package lacks current authoritative geometry: " +
                    physicalPackage.getId());

        for (PhysicalPackage.GeometryVariant variant : variants) {
            PhysicalPackageGeometry variantGeometry = variant.getGeometry();
            require(physicalPackage.acceptsGeometry(variantGeometry) &&
                    variantGeometry.getTerminalIds().equals(packageTerminals) &&
                    variant.getKey().equals(physicalPackage.getGeometryVariantKey(variantGeometry)) &&
                    variant.getTransformKey().equals(
                        physicalPackage.getGeometryVariantTransformKey(variantGeometry)),
                "Package variant is not canonical: " + physicalPackage.getId() + "/" +
                    variant.getKey());
            verifyGeometrySurfaces(physicalPackage, variantGeometry);

            BoardComponent component = syntheticComponent(physicalPackage,
                "TASK43_" + physicalPackage.getId() + "_" + variant.getKey());
            PcbFootprint first = PcbFootprint.fromPhysicalPackage(component, 240, 180,
                variantGeometry);
            PcbFootprint second = PcbFootprint.fromPhysicalPackage(component, 240, 180,
                variantGeometry);
            require(first.geometryFingerprint().equals(second.geometryFingerprint()),
                "Same package/variant/placement did not resolve identically: " +
                    physicalPackage.getId() + "/" + variant.getKey());
            require(first.getPlacement().getGeometryVariantKey().equals(variant.getKey()) &&
                    first.getPlacement().getGeometryTransformKey().equals(
                        variant.getTransformKey()),
                "Placement lost selected package variant identity: " + physicalPackage.getId());
            require(first.geometryFingerprint().indexOf("looseVariant=" +
                    physicalPackage.getDefaultLooseGeometryVariantKey()) >= 0,
                "Placement fingerprint omits package-declared loose variant: " +
                    physicalPackage.getId());
            verifyPlacedGeometry(first, physicalPackage, component);
            verifyTranslation(first, physicalPackage, component);
        }

        verifySelectedPlacement(physicalPackage, footprints);
        verifyDeclaredCatalog(physicalPackage);
    }

    private static void verifySelectedPlacement(PhysicalPackage physicalPackage,
            PcbFootprintRegistry footprints) {
        BoardComponent component = syntheticComponent(physicalPackage,
            "TASK43_SELECTED_" + physicalPackage.getId());
        Rectangle outline = new Rectangle(0, 0, 1600, 1200);
        PcbFootprint first = footprints.create(component, 240, 180, new Random(43), outline);
        PcbFootprint second = footprints.create(component, 240, 180, new Random(43), outline);
        require(first.geometryFingerprint().equals(second.geometryFingerprint()),
            "Provider selection is not deterministic: " + physicalPackage.getId());
        require(physicalPackage.acceptsGeometry(first.getPlacement().getPhysicalGeometry()),
            "Provider selected undeclared geometry: " + physicalPackage.getId());

        if (physicalPackage.getGeometryVariantSelection() ==
                PhysicalPackage.GeometryVariantSelection.EDGE_ORIENTED) {
            PcbFootprint left = footprints.create(component, 200, 180, new Random(43), outline);
            PcbFootprint right = footprints.create(component, 1200, 180, new Random(43), outline);
            require("DEFAULT".equals(left.getPlacement().getGeometryVariantKey()),
                "Connector left orientation is not the base realization: " +
                    physicalPackage.getId());
            require("DEFAULT_MIRRORED_X".equals(right.getPlacement().getGeometryVariantKey()) &&
                    ("MIRROR_X".equals(right.getPlacement().getGeometryTransformKey()) ||
                        "DEVELOPER_MIRROR_X".equals(
                            right.getPlacement().getGeometryTransformKey())),
                "Connector right orientation is not the mirrored realization: " +
                    physicalPackage.getId());
        }
    }

    private static void verifyDeclaredCatalog(PhysicalPackage physicalPackage) {
        Vector<PhysicalPackage.GeometryVariant> variants = physicalPackage.getGeometryVariants();
        if (physicalPackage == PhysicalPackages.AXIAL_RESISTOR ||
                "AXIAL_RESISTOR".equals(physicalPackage.getId())) {
            require(variants.size() == 3 && variantWidth(physicalPackage, "SPAN_220") == 220 &&
                    variantWidth(physicalPackage, "SPAN_240") == 240 &&
                    variantWidth(physicalPackage, "SPAN_260") == 260,
                "Axial resistor catalog is not exactly 220/240/260");
        } else if (physicalPackage == PhysicalPackages.AXIAL_DIODE ||
                "AXIAL_DIODE".equals(physicalPackage.getId())) {
            require(variants.size() == 2 && variantWidth(physicalPackage, "SPAN_230") == 230 &&
                    variantWidth(physicalPackage, "SPAN_250") == 250,
                "Axial diode catalog is not exactly 230/250");
        } else if (physicalPackage == PhysicalPackages.THROUGH_HOLE_CONNECTOR_2 ||
                "THROUGH_HOLE_CONNECTOR_2".equals(physicalPackage.getId())) {
            require(variants.size() == 2 &&
                    physicalPackage.getGeometryVariant("DEFAULT") != null &&
                    physicalPackage.getGeometryVariant("DEFAULT_MIRRORED_X") != null,
                "Connector catalog lacks base and mirrored-right realizations");
        } else if (!physicalPackage.isDeveloperGeneric()) {
            require(variants.size() == 1 &&
                    "DEFAULT".equals(variants.get(0).getKey()) &&
                    "IDENTITY".equals(variants.get(0).getTransformKey()),
                "Fixed production package has an undeclared extra realization: " +
                    physicalPackage.getId());
        }
    }

    private static int variantWidth(PhysicalPackage physicalPackage, String key) {
        PhysicalPackage.GeometryVariant variant = physicalPackage.getGeometryVariant(key);
        return variant == null ? -1 : variant.getGeometry().getWidth();
    }

    private static void verifyGeometrySurfaces(PhysicalPackage physicalPackage,
            PhysicalPackageGeometry geometry) {
        Rectangle body = geometry.getBodyBounds();
        Rectangle keepOut = geometry.getBodyKeepOut();
        Rectangle courtyard = geometry.getRoutingCourtyard();
        Rectangle selection = geometry.getSelectionEnvelope();
        Rectangle drag = geometry.getDragEnvelope();
        require(contains(keepOut, body) && contains(courtyard, keepOut) &&
                contains(selection, body) && contains(drag, selection),
            "Package envelope containment failed: " + physicalPackage.getId());
        Vector<PhysicalPackageGeometry.Terminal> terminals = geometry.getTerminals();
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = terminals.get(index);
            Rectangle pad = terminal.getPadBounds();
            Rectangle boardProbe = terminal.getBoardPadProbeBounds();
            PhysicalPackageGeometry.Lead connected = terminal.getConnectedLead();
            PhysicalPackageGeometry.Lead lifted = terminal.getLiftedLead();
            require(terminal.getTerminalId().equals(physicalPackage.getTerminalIds().get(index)) &&
                    contains(boardProbe, pad) && contains(boardProbe, terminal.getPadCenter()) &&
                    terminal.getProbeBounds().equals(boardProbe) &&
                    contains(selection, boardProbe) && contains(drag, boardProbe) &&
                    contains(courtyard, pad) && contains(selection, connected.getBounds()) &&
                    contains(selection, lifted.getBounds()) && contains(drag, connected.getBounds()) &&
                    contains(drag, lifted.getBounds()) &&
                    connected.getEndPoint().equals(terminal.getPadCenter()) &&
                    !lifted.getEndPoint().equals(terminal.getPadCenter()) &&
                    !contains(boardProbe, lifted.getEndPoint()) &&
                    contains(body, connected.getBodyPoint()) &&
                    lifted.getBodyPoint().equals(connected.getBodyPoint()) &&
                    contains(connected.getComponentProbeBounds(),
                        connected.getComponentProbeCenter()) &&
                    contains(lifted.getComponentProbeBounds(),
                        lifted.getComponentProbeCenter()) &&
                    contains(selection, connected.getComponentProbeBounds()) &&
                    contains(selection, lifted.getComponentProbeBounds()) &&
                    contains(drag, connected.getComponentProbeBounds()) &&
                    contains(drag, lifted.getComponentProbeBounds()) &&
                    !boardProbe.intersects(connected.getComponentProbeBounds()) &&
                    !boardProbe.intersects(lifted.getBounds()) &&
                    !boardProbe.intersects(lifted.getComponentProbeBounds()) &&
                    !connected.isEquivalentTo(lifted) &&
                    Math.abs(terminal.getEscapeDx()) + Math.abs(terminal.getEscapeDy()) <= 1 &&
                    terminal.getEscapeLength() >= 0 &&
                    (terminal.getEscapeLength() == 0 || terminal.getEscapeDx() != 0 ||
                        terminal.getEscapeDy() != 0),
                "Terminal surface invariant failed: " + physicalPackage.getId() + "/" +
                    terminal.getTerminalId());
            for (int second = index + 1; second < terminals.size(); second++) {
                PhysicalPackageGeometry.Terminal other = terminals.get(second);
                require(!boardProbe.intersects(other.getBoardPadProbeBounds()) &&
                        !boardProbe.intersects(other.getConnectedLead().getComponentProbeBounds()) &&
                        !boardProbe.intersects(other.getLiftedLead().getComponentProbeBounds()) &&
                        !other.getBoardPadProbeBounds().intersects(
                            connected.getComponentProbeBounds()) &&
                        !other.getBoardPadProbeBounds().intersects(
                            lifted.getComponentProbeBounds()) &&
                        !connected.getComponentProbeBounds().intersects(
                            other.getConnectedLead().getComponentProbeBounds()) &&
                        !connected.getComponentProbeBounds().intersects(
                            other.getLiftedLead().getComponentProbeBounds()) &&
                        !lifted.getComponentProbeBounds().intersects(
                            other.getConnectedLead().getComponentProbeBounds()) &&
                        !lifted.getComponentProbeBounds().intersects(
                            other.getLiftedLead().getComponentProbeBounds()),
                    "Peer component lead probes overlap: " + physicalPackage.getId());
            }
        }
    }

    private static void verifyPlacedGeometry(PcbFootprint footprint,
            PhysicalPackage physicalPackage, BoardComponent component) {
        PcbComponentPlacement placement = footprint.getPlacement();
        PhysicalPackageGeometry geometry = placement.getPhysicalGeometry();
        require(physicalPackage.acceptsGeometry(geometry) &&
                placement.getPhysicalPackage() == physicalPackage,
            "Placed geometry is not the package's canonical object: " + physicalPackage.getId());
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(
            placement.getX(), placement.getY());
        require(placement.getWidth() == geometry.getWidth() &&
                placement.getHeight() == geometry.getHeight() &&
                placement.getKeepOut().equals(placed.getBodyKeepOut()) &&
                placement.getRoutingCourtyard().equals(placed.getRoutingCourtyard()) &&
                placement.getBodyBounds().equals(placed.getBodyBounds()) &&
                placement.getSelectionEnvelope().equals(placed.getSelectionEnvelope()) &&
                placement.getDragEnvelope().equals(placed.getDragEnvelope()),
            "Placed envelope diverged from package geometry: " + physicalPackage.getId());
        for (int index = 0; index < component.getPadIds().size(); index++) {
            PcbPadPlacement pad = footprint.getPad(component.getPadIds().get(index));
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            require(pad.getX() == placed.getPadPoint(index).x &&
                    pad.getY() == placed.getPadPoint(index).y &&
                    pad.getEscapeDx() == terminal.getEscapeDx() &&
                    pad.getEscapeDy() == terminal.getEscapeDy() &&
                    pad.getEscapeLength() == terminal.getEscapeLength() &&
                    pad.getPadBounds().equals(placed.getPadBounds(index)) &&
                    pad.getProbeBounds().equals(placed.getBoardPadProbeBounds(index)) &&
                    placement.getProbeBounds(index).equals(
                        placed.getBoardPadProbeBounds(index)),
                "Pad geometry diverged from package terminal: " + physicalPackage.getId());
        }
    }

    private static void verifyTranslation(PcbFootprint source, PhysicalPackage physicalPackage,
            BoardComponent component) {
        PcbFootprint translated = source.translated(640, 510);
        PcbComponentPlacement original = source.getPlacement();
        PcbComponentPlacement moved = translated.getPlacement();
        require(original.getPhysicalPackage() == moved.getPhysicalPackage() &&
                original.getPhysicalGeometry() == moved.getPhysicalGeometry() &&
                original.getGeometryRealization() == moved.getGeometryRealization() &&
                original.getGeometryVariantKey().equals(moved.getGeometryVariantKey()) &&
                original.getGeometryTransformKey().equals(moved.getGeometryTransformKey()) &&
                original.getGeometryContractVersionValue() ==
                    moved.getGeometryContractVersionValue(),
            "Translation changed selected physical identity: " + physicalPackage.getId());
        int dx = moved.getX() - original.getX();
        int dy = moved.getY() - original.getY();
        assertTranslated(original.getBodyBounds(), moved.getBodyBounds(), dx, dy);
        assertTranslated(original.getKeepOut(), moved.getKeepOut(), dx, dy);
        assertTranslated(original.getRoutingCourtyard(), moved.getRoutingCourtyard(), dx, dy);
        for (int index = 0; index < component.getPadIds().size(); index++) {
            assertTranslated(original.getPadBounds(index), moved.getPadBounds(index), dx, dy);
            assertTranslated(original.getProbeBounds(index), moved.getProbeBounds(index), dx, dy);
            assertTranslated(original.getBoardPadProbeBounds(index),
                moved.getBoardPadProbeBounds(index), dx, dy);
            assertTranslated(original.getComponentLeadProbeBounds(index),
                moved.getComponentLeadProbeBounds(index), dx, dy);
            assertTranslated(original.getComponentLeadProbeBounds(index, true),
                moved.getComponentLeadProbeBounds(index, true), dx, dy);
            assertTranslated(original.getLeadBounds(index), moved.getLeadBounds(index), dx, dy);
            assertTranslated(original.getLeadBounds(index, true),
                moved.getLeadBounds(index, true), dx, dy);
            assertTranslated(original.getPadPoint(index), moved.getPadPoint(index), dx, dy);
            assertTranslated(original.getBoardPadProbeCenter(index),
                moved.getBoardPadProbeCenter(index), dx, dy);
            assertTranslated(original.getComponentLeadProbeCenter(index),
                moved.getComponentLeadProbeCenter(index), dx, dy);
            assertTranslated(original.getComponentLeadProbeCenter(index, true),
                moved.getComponentLeadProbeCenter(index, true), dx, dy);
        }
    }

    private static void verifyNegativeCanaries(Vector<PhysicalPackage> registered) {
        PhysicalPackage production = null;
        PhysicalPackage developer = null;
        for (PhysicalPackage physicalPackage : registered) {
            if (physicalPackage.isDeveloperGeneric() && developer == null)
                developer = physicalPackage;
            if (!physicalPackage.isDeveloperGeneric() && production == null)
                production = physicalPackage;
        }
        require(production != null && developer != null,
            "Package registry lacks production/developer canary coverage");
        PhysicalPackageGeometry source = production.getGeometry();
        PhysicalPackageGeometry foreign = cloneGeometry(source,
            new PcbGeometryContractVersion(source.getGeometryContractVersionValue()));
        require(source.isEquivalentTo(foreign) && !production.acceptsGeometry(foreign),
            "Foreign same-ID geometry was accepted");
        expectRejectedPlacement(production, foreign);

        PhysicalPackageGeometry undeclared = cloneGeometry(source,
            new PcbGeometryContractVersion(source.getGeometryContractVersionValue() + 1));
        require(!production.acceptsGeometry(undeclared),
            "Undeclared geometry was accepted by the package catalog");

        expectRejectedNullPackage(production);
        expectRejectedGenericProductionGeometry(production);
        expectRejectedMalformedEscape(source);
        expectRejectedMalformedBoardProbe(source);
        expectRejectedCrossTerminalProbe(source);
        expectRejectedMalformedDetachedEndpoint(source);
        expectRejectedMalformedLiftedBounds(source);
        expectRejectedMalformedLiftedProbe(source);
        expectRejectedMalformedEnvelope(source);
        verifyVersionIdentity(production);

        PhysicalPackageGeometry generic = developer.getGeometry();
        PcbComponentPlacement genericProjection = PcbComponentPlacement.fromPhysicalGeometry(
            "TASK43_GENERIC_COMPAT", 40, 40, generic);
        require(genericProjection.getPhysicalPackage() != null &&
                genericProjection.getPhysicalPackage().isDeveloperGeneric() &&
                genericProjection.getPhysicalGeometry() == generic,
            "Generic compatibility projection did not become package-backed");
        expectRejectedPackageLessPlacement(production);
        boolean productionLooseRejected = false;
        try {
            PcbComponentPlacement.fromPhysicalGeometry("TASK43_PRODUCTION_LOOSE", 40, 40,
                source);
        } catch (IllegalArgumentException expected) {
            productionLooseRejected = true;
        }
        require(productionLooseRejected,
            "Loose placement inferred a production variant without a package");

        PhysicalPackage legacy = new PhysicalPackage("TASK43_LEGACY_GENERIC", 2);
        require(legacy.isDeveloperGeneric() && legacy.getGeometry().isDeveloperGeneric(),
            "Legacy no-geometry package boundary is not explicitly generic");
    }

    private static void verifyGeneratedIdentity(GeneratedBoardInstance instance) {
        String familyId = instance.getCircuitFamilyId();
        String topologyVariantId = instance.getTopologyVariantId();
        require(familyId != null && familyId.length() > 0 &&
                topologyVariantId != null && topologyVariantId.length() > 0,
            "Generated board is missing circuit family/topology identity");

        GeneratedChallengeDefinition challenge = instance.getChallengeDefinition();
        require(challenge != null && familyId.equals(challenge.getCircuitFamilyId()) &&
                topologyVariantId.equals(challenge.getTopologyVariantId()),
            "Generated challenge identity disagrees with board identity");

        GeneratedDiagnosticSolvabilityContract solvability =
            instance.getDiagnosticSolvabilityContract();
        require(solvability != null && familyId.equals(solvability.getFamilyId()) &&
                topologyVariantId.equals(solvability.getTopologyVariantId()) &&
                instance.getSeed() == solvability.getSeed(),
            "Diagnostic solvability identity disagrees with board identity");
        solvability.validate(instance);
    }

    private static void expectRejectedPlacement(PhysicalPackage physicalPackage,
            PhysicalPackageGeometry geometry) {
        boolean rejected = false;
        try {
            PcbComponentPlacement.fromPhysicalGeometry("TASK43_FOREIGN", 40, 40,
                physicalPackage, geometry);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Foreign package geometry placement was accepted");
    }

    private static void expectRejectedNullPackage(PhysicalPackage source) {
        boolean rejected = false;
        try {
            new PhysicalPackage("TASK43_NULL_PACKAGE", source.getTerminalIds(),
                new Vector<String>(), false, null);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Authoritative package constructor accepted null geometry");
    }

    private static void expectRejectedGenericProductionGeometry(PhysicalPackage source) {
        boolean rejected = false;
        try {
            new PhysicalPackage("TASK43_GENERIC_PRODUCTION", source.getTerminalIds(),
                new Vector<String>(), false,
                PhysicalPackageGeometry.generic(source.getTerminalIds(), false));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Production package constructor accepted generic geometry");
    }

    private static void expectRejectedMalformedEscape(PhysicalPackageGeometry source) {
        PhysicalPackageGeometry.Terminal first = source.getTerminal(0);
        boolean rejected = false;
        try {
            new PhysicalPackageGeometry.Terminal(first.getTerminalId(), first.getPadCenter(),
                first.getPadBounds(), first.getBoardPadProbeCenter(),
                first.getBoardPadProbeBounds(), first.getConnectedLead(), first.getLiftedLead(),
                1, 0, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Malformed escape canary was not rejected");
    }

    private static void expectRejectedMalformedBoardProbe(PhysicalPackageGeometry source) {
        PhysicalPackageGeometry.Terminal first = source.getTerminal(0);
        Vector<PhysicalPackageGeometry.Terminal> terminals = source.getTerminals();
        terminals.set(0, new PhysicalPackageGeometry.Terminal(first.getTerminalId(),
            first.getPadCenter(), first.getPadBounds(), first.getBoardPadProbeCenter(),
            new Rectangle(first.getPadCenter().x + 1000, first.getPadCenter().y + 1000, 30, 30),
            first.getConnectedLead(), first.getLiftedLead(), first.getEscapeDx(),
            first.getEscapeDy(), first.getEscapeLength()));
        expectRejectedGeometry(source, terminals, source.getBodyBounds(),
            source.getBodyKeepOut(), source.getRoutingCourtyard(),
            source.getSelectionEnvelope(), source.getDragEnvelope(),
            source.getGeometryContractVersion(), "malformed board-pad probe");
    }

    private static void expectRejectedCrossTerminalProbe(PhysicalPackageGeometry source) {
        Vector<PhysicalPackageGeometry.Terminal> terminals = source.getTerminals();
        if (terminals.size() < 2)
            return;
        PhysicalPackageGeometry.Terminal first = terminals.get(0);
        PhysicalPackageGeometry.Terminal second = terminals.get(1);
        Rectangle overlappingBoardProbe = first.getBoardPadProbeBounds().union(
            second.getBoardPadProbeBounds());
        terminals.set(1, new PhysicalPackageGeometry.Terminal(second.getTerminalId(),
            second.getPadCenter(), second.getPadBounds(), second.getBoardPadProbeCenter(),
            overlappingBoardProbe, second.getConnectedLead(), second.getLiftedLead(),
            second.getEscapeDx(), second.getEscapeDy(), second.getEscapeLength()));
        expectRejectedGeometry(source, terminals, source.getBodyBounds(),
            source.getBodyKeepOut(), source.getRoutingCourtyard(),
            source.getSelectionEnvelope(), source.getDragEnvelope(),
            source.getGeometryContractVersion(), "cross-terminal probe overlap");
    }

    private static void expectRejectedMalformedDetachedEndpoint(PhysicalPackageGeometry source) {
        PhysicalPackageGeometry.Terminal first = source.getTerminal(0);
        PhysicalPackageGeometry.Lead lifted = first.getLiftedLead();
        PhysicalPackageGeometry.Lead malformed = new PhysicalPackageGeometry.Lead(
            first.getPadCenter(), lifted.getBodyPoint(), lifted.getBounds(),
            lifted.getComponentProbeCenter(), lifted.getComponentProbeBounds());
        Vector<PhysicalPackageGeometry.Terminal> terminals = source.getTerminals();
        terminals.set(0, new PhysicalPackageGeometry.Terminal(first.getTerminalId(),
            first.getPadCenter(), first.getPadBounds(), first.getBoardPadProbeCenter(),
            first.getBoardPadProbeBounds(), first.getConnectedLead(), malformed,
            first.getEscapeDx(), first.getEscapeDy(), first.getEscapeLength()));
        expectRejectedGeometry(source, terminals, source.getBodyBounds(),
            source.getBodyKeepOut(), source.getRoutingCourtyard(),
            source.getSelectionEnvelope(), source.getDragEnvelope(),
            source.getGeometryContractVersion(), "malformed detached lifted endpoint");
    }

    private static void expectRejectedMalformedLiftedBounds(PhysicalPackageGeometry source) {
        PhysicalPackageGeometry.Terminal first = source.getTerminal(0);
        PhysicalPackageGeometry.Lead lifted = first.getLiftedLead();
        PhysicalPackageGeometry.Lead malformed = new PhysicalPackageGeometry.Lead(
            lifted.getEndPoint(), lifted.getBodyPoint(), first.getPadBounds(),
            lifted.getComponentProbeCenter(), lifted.getComponentProbeBounds());
        Vector<PhysicalPackageGeometry.Terminal> terminals = source.getTerminals();
        terminals.set(0, new PhysicalPackageGeometry.Terminal(first.getTerminalId(),
            first.getPadCenter(), first.getPadBounds(), first.getBoardPadProbeCenter(),
            first.getBoardPadProbeBounds(), first.getConnectedLead(), malformed,
            first.getEscapeDx(), first.getEscapeDy(), first.getEscapeLength()));
        expectRejectedGeometry(source, terminals, source.getBodyBounds(),
            source.getBodyKeepOut(), source.getRoutingCourtyard(),
            source.getSelectionEnvelope(), source.getDragEnvelope(),
            source.getGeometryContractVersion(), "malformed detached lifted bounds");
    }

    private static void expectRejectedMalformedLiftedProbe(PhysicalPackageGeometry source) {
        PhysicalPackageGeometry.Terminal first = source.getTerminal(0);
        PhysicalPackageGeometry.Lead lifted = first.getLiftedLead();
        PhysicalPackageGeometry.Lead malformed = new PhysicalPackageGeometry.Lead(
            lifted.getEndPoint(), lifted.getBodyPoint(), lifted.getBounds(),
            new Point(lifted.getComponentProbeCenter().x + 1000,
                lifted.getComponentProbeCenter().y + 1000), lifted.getComponentProbeBounds());
        Vector<PhysicalPackageGeometry.Terminal> terminals = source.getTerminals();
        terminals.set(0, new PhysicalPackageGeometry.Terminal(first.getTerminalId(),
            first.getPadCenter(), first.getPadBounds(), first.getBoardPadProbeCenter(),
            first.getBoardPadProbeBounds(), first.getConnectedLead(), malformed,
            first.getEscapeDx(), first.getEscapeDy(), first.getEscapeLength()));
        expectRejectedGeometry(source, terminals, source.getBodyBounds(),
            source.getBodyKeepOut(), source.getRoutingCourtyard(),
            source.getSelectionEnvelope(), source.getDragEnvelope(),
            source.getGeometryContractVersion(), "malformed detached lifted probe");
    }

    private static void expectRejectedMalformedEnvelope(PhysicalPackageGeometry source) {
        boolean rejected = false;
        try {
            new PhysicalPackageGeometry(source.getWidth(), source.getHeight(),
                source.getTerminals(), source.getBodyBounds(), source.getBodyKeepOut(),
                new Rectangle(source.getRoutingCourtyard().x + 500,
                    source.getRoutingCourtyard().y, source.getRoutingCourtyard().width,
                    source.getRoutingCourtyard().height), source.getSelectionEnvelope(),
                source.getDragEnvelope(), source.getGeometryContractVersion());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Malformed envelope canary was not rejected");
    }

    private static void expectRejectedGeometry(PhysicalPackageGeometry source,
            Vector<PhysicalPackageGeometry.Terminal> terminals, Rectangle body, Rectangle keepOut,
            Rectangle courtyard, Rectangle selection, Rectangle drag,
            PcbGeometryContractVersion version, String name) {
        boolean rejected = false;
        try {
            new PhysicalPackageGeometry(source.getWidth(), source.getHeight(), terminals, body,
                keepOut, courtyard, selection, drag, version);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, name + " canary was not rejected");
    }

    private static void verifyVersionIdentity(PhysicalPackage sourcePackage) {
        PhysicalPackageGeometry versionOne = cloneGeometry(sourcePackage.getGeometry(),
            new PcbGeometryContractVersion(PcbGeometryContractVersion.CURRENT));
        PhysicalPackageGeometry versionTwo = cloneGeometry(sourcePackage.getGeometry(),
            new PcbGeometryContractVersion(PcbGeometryContractVersion.CURRENT + 1));
        Vector<String> terminals = sourcePackage.getTerminalIds();
        PhysicalPackage firstPackage = new PhysicalPackage("TASK43_VERSION_IDENTITY", terminals,
            new Vector<String>(), false, versionOne);
        PhysicalPackage secondPackage = new PhysicalPackage("TASK43_VERSION_IDENTITY", terminals,
            new Vector<String>(), false, versionTwo);
        PcbComponentPlacement first = PcbComponentPlacement.fromPhysicalGeometry(
            "TASK43_VERSION_COMPONENT", 50, 50, firstPackage, versionOne);
        PcbComponentPlacement second = PcbComponentPlacement.fromPhysicalGeometry(
            "TASK43_VERSION_COMPONENT", 50, 50, secondPackage, versionTwo);
        require(first.getGeometryContractVersionValue() != second.getGeometryContractVersionValue() &&
                !first.geometryFingerprint().equals(second.geometryFingerprint()),
            "Geometry contract version is hidden from placement physical identity");
    }

    private static void expectRejectedPackageLessPlacement(PhysicalPackage source) {
        PhysicalPackageGeometry geometry = source.getGeometry();
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(40, 40);
        boolean rejected = false;
        try {
            new PcbComponentPlacement("TASK43_PACKAGELESS", 40, 40, geometry.getWidth(),
                geometry.getHeight(), placed.getBodyKeepOut(), placed.getRoutingCourtyard());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Package-less placement compatibility was accepted for production");
    }

    private static void verifySelectedGeometryLifecycleCanary() {
        String componentId = "TASK43_LIFECYCLE_R1";
        PhysicalPackage physicalPackage = PhysicalPackages.AXIAL_RESISTOR;
        PhysicalPackage.GeometryVariant span260Variant =
            physicalPackage.getGeometryVariant("SPAN_260");
        require(span260Variant != null, "Axial resistor SPAN_260 variant is missing");
        PhysicalPackageGeometry span260 = span260Variant.getGeometry();

        TroubleshootBoard board = new TroubleshootBoard("TASK43_LIFECYCLE_BOARD");
        board.addNet(new BoardNet("TASK43_LIFECYCLE_NET_1"));
        board.addNet(new BoardNet("TASK43_LIFECYCLE_NET_2"));
        board.addComponent(new BoardComponent(componentId, "AXIAL_RESISTOR",
            physicalPackage));
        board.addPad(new BoardPad(componentId + ".1", componentId, "1",
            "TASK43_LIFECYCLE_NET_1"));
        board.addPad(new BoardPad(componentId + ".2", componentId, "2",
            "TASK43_LIFECYCLE_NET_2"));
        board.validate();

        PcbFootprint span260Footprint = PcbFootprint.fromPhysicalPackage(
            board.getComponent(componentId), 240, 220, span260);
        PcbBoardLayout layout = new PcbBoardLayout(1200, 700,
            new Rectangle(20, 20, 900, 650), new Rectangle(960, 20, 220, 650));
        layout.addComponent(span260Footprint.getPlacement());
        for (PcbPadPlacement pad : span260Footprint.getPads())
            layout.addPad(pad);
        layout.validateAgainst(board);

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot slot = runtime.createSlot(componentId);
        ResistorElm element = new ResistorElm(96, 96);
        ResistorNameplate specification = new ResistorNameplate(componentId, 1000, 5);
        PhysicalResistorPart part = new PhysicalResistorPart(componentId + "_PART",
            specification, element, null, null, ResistorPartLocation.LOOSE);
        slot.install(part);
        runtime.bindGeometryRealizations(layout);
        runtime.validate();

        PhysicalGeometryRealization carrier = slot.getGeometryRealization();
        String carrierFingerprint = carrier == null ? null : carrier.fingerprint();
        String partId = part.getId();
        PhysicalPartTerminal[] terminals = new PhysicalPartTerminal[] {
            part.getTerminal(0), part.getTerminal(1)
        };
        CircuitMeasurementEndpoint[] endpoints = new CircuitMeasurementEndpoint[] {
            terminals[0].getEndpoint(), terminals[1].getEndpoint()
        };
        require(carrier != null && "SPAN_260".equals(carrier.getVariantKey()) &&
                part.getGeometryRealization() == carrier &&
                part.getElement() == element &&
                carrierFingerprint.equals(span260Footprint.getPlacement()
                    .getGeometryRealization().fingerprint()),
            "Selected geometry lifecycle did not bind the SPAN_260 carrier");

        PhysicalPart<?> removed = slot.remove();
        require(removed == part && !slot.isOccupied() && !part.isInstalled(),
            "Selected geometry lifecycle removal changed part identity");
        slot.install(part);
        runtime.validate();
        require(slot.getInstalledPart() == part && part.getId().equals(partId) &&
                slot.getGeometryRealization() == carrier &&
                part.getGeometryRealization() == carrier &&
                carrierFingerprint.equals(part.getGeometryRealization().fingerprint()) &&
                part.getTerminal(0) == terminals[0] && part.getTerminal(1) == terminals[1] &&
                part.getTerminal(0).getEndpoint() == endpoints[0] &&
                part.getTerminal(1).getEndpoint() == endpoints[1],
            "Selected geometry lifecycle changed part, terminal, endpoint, or carrier identity");

        PhysicalPackageGeometry span220 = physicalPackage.getGeometryVariant("SPAN_220")
            .getGeometry();
        PcbFootprint span220Footprint = PcbFootprint.fromPhysicalPackage(
            board.getComponent(componentId), 240, 220, span220);
        boolean rejected = false;
        try {
            slot.bindGeometryRealization(span220Footprint.getPlacement());
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected && slot.getGeometryRealization() == carrier &&
                part.getGeometryRealization() == carrier,
            "Selected geometry lifecycle accepted a SPAN_220 rebind");
    }

    private static PhysicalPackageGeometry cloneGeometry(PhysicalPackageGeometry source,
            PcbGeometryContractVersion version) {
        return new PhysicalPackageGeometry(source.getWidth(), source.getHeight(),
            source.getTerminals(), source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), version);
    }

    private static BoardComponent syntheticComponent(PhysicalPackage physicalPackage,
            String id) {
        BoardComponent component = new BoardComponent(id, "TASK43", physicalPackage);
        for (String terminalId : physicalPackage.getTerminalIds())
            component.addPadId(id + "." + terminalId);
        return component;
    }

    private static String boardIdentity(GeneratedBoardInstance instance) {
        TroubleshootBoard board = instance.getBoard();
        StringBuilder result = new StringBuilder();
        result.append("family=").append(instance.getCircuitFamilyId()).append('|')
            .append("topology=").append(instance.getTopologyVariantId()).append('|')
            .append("seed=").append(instance.getSeed()).append('|');
        GeneratedChallengeDefinition challenge = instance.getChallengeDefinition();
        if (challenge != null)
            result.append("challenge=").append(challenge.getId()).append(':')
                .append(challenge.getCircuitFamilyId()).append(':')
                .append(challenge.getTopologyVariantId()).append('|');
        GeneratedDiagnosticSolvabilityContract solvability =
            instance.getDiagnosticSolvabilityContract();
        if (solvability != null)
            result.append("solvability=").append(solvability.getRouteId()).append(':')
                .append(solvability.getFamilyId()).append(':')
                .append(solvability.getTopologyVariantId()).append(':')
                .append(solvability.getSeed()).append(':')
                .append(solvability.getAdmittedCandidateCount()).append(':')
                .append(solvability.getAdmittedPhysicalOwnerCount()).append('|');
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        result.append("components=").append(componentIds).append('|');
        for (String componentId : componentIds) {
            BoardComponent component = board.getComponent(componentId);
            result.append(componentId).append(" type=").append(component.getType())
                .append(" package=")
                .append(component.getPhysicalPackage().getId()).append(" terminals=")
                .append(component.getPhysicalPackage().getTerminalIds()).append(" pads=")
                .append(component.getPadIds()).append(';');
        }
        Vector<String> padIds = board.getPadIds();
        Collections.sort(padIds);
        result.append("pads=").append(padIds).append('|');
        for (String padId : padIds) {
            BoardPad pad = board.getPad(padId);
            result.append(padId).append(':').append(pad.getComponentId()).append(':')
                .append(pad.getTerminalId()).append(':').append(pad.getNetId()).append(';');
        }
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        result.append("nets=").append(netIds).append('|');
        for (String netId : netIds) {
            Vector<String> netPads = board.getNet(netId).getPadIds();
            Collections.sort(netPads);
            result.append(netId).append(':').append(netPads).append(';');
        }
        Vector<String> semanticIds = new Vector<String>();
        if (instance.getFamilyState() != null)
            for (GeneratedBoardOperation operation : instance.getOperationCatalog().getAll())
                semanticIds.add(operation.getStableId());
        Collections.sort(semanticIds);
        result.append("semantic=").append(semanticIds).append('|');
        appendRuntimeIdentity(result, instance.getPhysicalBoardRuntime());
        return result.toString();
    }

    private static void appendRuntimeIdentity(StringBuilder result,
            PhysicalBoardRuntime runtime) {
        result.append("slots=");
        if (runtime == null) {
            result.append("null|");
            return;
        }
        Vector<String> slotComponentIds = new Vector<String>();
        for (PhysicalBoardSlot slot : runtime.getSlots())
            slotComponentIds.add(slot.getComponentId());
        Collections.sort(slotComponentIds);
        for (String componentId : slotComponentIds) {
            PhysicalBoardSlot slot = runtime.getSlot(componentId);
            result.append(slot.getId()).append(':').append(slot.getComponentId())
                .append(" package=").append(slot.getPhysicalPackage().getId())
                .append(" pads=").append(slot.getPadIds())
                .append(" terminals=").append(slot.getTerminalIds())
                .append(" nets=").append(slot.getNetIds())
                .append(" realization=");
            appendRealization(result, slot.getGeometryRealization());
            PhysicalPart<?> installed = slot.getInstalledPart();
            result.append(" installed=")
                .append(installed == null ? "null" : installed.getId()).append(';');
            if (installed != null)
                appendPartIdentity(result, installed);
        }
        result.append("|parts=");
        Vector<String> partIds = new Vector<String>();
        for (PhysicalPart<?> part : runtime.getPhysicalParts())
            partIds.add(part.getId());
        Collections.sort(partIds);
        for (String partId : partIds)
            appendPartIdentity(result, runtime.getPart(partId));
        result.append('|');
    }

    private static void appendPartIdentity(StringBuilder result, PhysicalPart<?> part) {
        result.append("part[").append(part.getId()).append("] package=")
            .append(part.getPackage().getId()).append(" realization=");
        appendRealization(result, part.getGeometryRealization());
        result.append(" terminals=");
        for (PhysicalPartTerminal terminal : part.getTerminals())
            result.append(terminal.getId()).append('/')
                .append(terminal.getTerminalName()).append('@')
                .append(endpointIdentity(terminal.getEndpoint())).append(',');
        result.append(';');
    }

    private static void appendRealization(StringBuilder result,
            PhysicalGeometryRealization realization) {
        result.append(realization == null ? "null" : realization.fingerprint());
    }

    private static String endpointIdentity(CircuitMeasurementEndpoint endpoint) {
        if (endpoint == null)
            return "null";
        StringBuilder result = new StringBuilder();
        result.append(endpoint.getClass().getName()).append('#')
            .append(System.identityHashCode(endpoint));
        if (endpoint instanceof CircuitPostMeasurementEndpoint) {
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            result.append(" element#").append(System.identityHashCode(post.getElement()))
                .append(" post=").append(post.getPostIndex());
        }
        return result.toString();
    }

    private static void assertTranslated(Rectangle original, Rectangle moved, int dx, int dy) {
        require(moved.x - original.x == dx && moved.y - original.y == dy &&
                moved.width == original.width && moved.height == original.height,
            "Translated geometry did not preserve local structure");
    }

    private static void assertTranslated(Point original, Point moved, int dx, int dy) {
        require(moved.x - original.x == dx && moved.y - original.y == dy,
            "Translated point did not preserve local structure");
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return outer != null && inner != null && inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }

    private static boolean contains(Rectangle outer, Point point) {
        return outer != null && point != null && point.x >= outer.x && point.y >= outer.y &&
            (long) point.x <= (long) outer.x + outer.width &&
            (long) point.y <= (long) outer.y + outer.height;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
