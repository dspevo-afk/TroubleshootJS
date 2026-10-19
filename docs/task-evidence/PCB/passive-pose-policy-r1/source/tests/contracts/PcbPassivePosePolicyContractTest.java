package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;

/** Native-only policy, identity, and bounded visit-selection contract. */
public final class PcbPassivePosePolicyContractTest {
    private static int assertions;
    private static final int ORIGIN_X = 100;
    private static final int ORIGIN_Y = 200;

    private static final class VariantCase {
        final PhysicalPackage physicalPackage;
        final String variantKey;
        final String componentId;
        final int firstPadX;
        final int firstPadY;
        final int secondPadX;
        final int secondPadY;

        VariantCase(PhysicalPackage physicalPackage, String variantKey,
                String componentId, int firstPadX, int firstPadY,
                int secondPadX, int secondPadY) {
            this.physicalPackage = physicalPackage;
            this.variantKey = variantKey;
            this.componentId = componentId;
            this.firstPadX = firstPadX;
            this.firstPadY = firstPadY;
            this.secondPadX = secondPadX;
            this.secondPadY = secondPadY;
        }
    }

    private static final class Pair {
        final BoardComponent component;
        final PcbFootprint source;
        final PcbFootprint alternative;

        Pair(BoardComponent component, PcbFootprint source, PcbFootprint alternative) {
            this.component = component;
            this.source = source;
            this.alternative = alternative;
        }
    }

    private interface Attempt { void run(); }

    private PcbPassivePosePolicyContractTest() { }

    public static void main(String[] args) throws Exception {
        policyIdentityAndVersions();
        VariantCase[] cases = expectedCases();
        int pairCount = 0;
        Pair resistor220 = null;
        for (VariantCase item : cases) {
            Pair pair = verifyVariant(item);
            if ("SPAN_220".equals(item.variantKey)) resistor220 = pair;
            pairCount++;
        }
        check(pairCount == 7, "all seven canonical finite passive geometries covered");
        visitSequence(cases[0]);
        inactiveAndUnsupported(cases[0]);
        constraintCompatibility();
        sizingAndGridSeam(resistor220);
        System.out.println("PASS: PCB passive pose policy contracts assertions=" +
            assertions + " geometries=7 sequence=63/63 visits=126");
    }

    private static VariantCase[] expectedCases() {
        /* Turned pad points below are literal board coordinates, calculated
           independently from the declared local terminal coordinates. */
        return new VariantCase[] {
            new VariantCase(PhysicalPackages.AXIAL_RESISTOR, "SPAN_220", "R220",
                140, 230, 140, 390),
            new VariantCase(PhysicalPackages.AXIAL_RESISTOR, "SPAN_240", "R240",
                140, 230, 140, 410),
            new VariantCase(PhysicalPackages.AXIAL_RESISTOR, "SPAN_260", "R260",
                140, 230, 140, 430),
            new VariantCase(PhysicalPackages.AXIAL_DIODE, "SPAN_230", "D230",
                140, 230, 140, 400),
            new VariantCase(PhysicalPackages.AXIAL_DIODE, "SPAN_250", "D250",
                140, 230, 140, 420),
            new VariantCase(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR, "DEFAULT",
                "CE1", 190, 230, 190, 280),
            new VariantCase(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR, "DEFAULT",
                "CC1", 160, 220, 160, 260)
        };
    }

    private static Pair verifyVariant(VariantCase expected) throws Exception {
        PhysicalPackage.GeometryVariant variant =
            expected.physicalPackage.getGeometryVariant(expected.variantKey);
        check(variant != null && expected.physicalPackage.acceptsGeometry(
            variant.getGeometry()), "declared finite geometry " + expected.variantKey);
        BoardComponent component = component(expected.componentId,
            expected.physicalPackage);
        PcbFootprint source = PcbFootprint.fromPhysicalPackage(component,
            new PcbPackagePose(ORIGIN_X, ORIGIN_Y, PcbRotation.DEG_0,
                PcbBoardSide.TOP), variant.getGeometry());
        String before = source.geometryFingerprint();
        PcbFootprint alternative = PcbPassivePosePolicy.alternativeFor(component,
            source, PcbPassivePosePolicy.ORTHOGONAL, true);
        check(alternative != null, "eligible canonical package gets one alternate " +
            expected.variantKey);

        PcbComponentPlacement original = source.getPlacement();
        PcbComponentPlacement turned = alternative.getPlacement();
        check(original.getComponentId().equals(turned.getComponentId()) &&
                original.getPhysicalPackage() == expected.physicalPackage &&
                turned.getPhysicalPackage() == expected.physicalPackage &&
                original.getPhysicalGeometry() == variant.getGeometry() &&
                turned.getPhysicalGeometry() == variant.getGeometry() &&
                original.getGeometryVariantKey().equals(expected.variantKey) &&
                turned.getGeometryVariantKey().equals(expected.variantKey),
            "component, canonical package, and selected geometry identity " +
                expected.variantKey);
        check(original.getX() == turned.getX() && original.getY() == turned.getY() &&
                original.getRotation() == PcbRotation.DEG_0 &&
                turned.getRotation() == PcbRotation.DEG_90 &&
                original.getMountingSide() == PcbBoardSide.TOP &&
                turned.getMountingSide() == PcbBoardSide.TOP,
            "origin and top-face pose identity " + expected.variantKey);

        Vector<String> componentPads = component.getPadIds();
        Vector<PcbPadPlacement> originalPads = source.getPads();
        Vector<PcbPadPlacement> turnedPads = alternative.getPads();
        check(originalPads.size() == componentPads.size() &&
                turnedPads.size() == componentPads.size(),
            "pad count preserved " + expected.variantKey);
        for (int index = 0; index < componentPads.size(); index++)
            check(componentPads.get(index).equals(originalPads.get(index).getPadId()) &&
                    componentPads.get(index).equals(turnedPads.get(index).getPadId()),
                "stable pad identity " + expected.variantKey + " index=" + index);
        check(turnedPads.get(0).getX() == expected.firstPadX &&
                turnedPads.get(0).getY() == expected.firstPadY &&
                turnedPads.get(1).getX() == expected.secondPadX &&
                turnedPads.get(1).getY() == expected.secondPadY,
            "independent literal TOP90 terminal spots " + expected.variantKey);
        check(source.geometryFingerprint().equals(before),
            "alternate creation leaves TOP0 source immutable " + expected.variantKey);

        verifyConservativeSizing(expected, source, alternative);
        return new Pair(component, source, alternative);
    }

    private static void verifyConservativeSizing(VariantCase expected,
            PcbFootprint source, PcbFootprint alternative) throws Exception {
        int margin = 20;
        Rectangle original = PcbPlacementPlanner.envelope(source, margin);
        Rectangle turned = PcbPlacementPlanner.envelope(alternative, margin);
        long originalDemand = demandProduct(original);
        long turnedDemand = demandProduct(turned);
        long expectedDemand = Math.max(originalDemand, turnedDemand);
        int expectedWidth = Math.max(original.width, turned.width);
        int expectedHeight = Math.max(original.height, turned.height);

        Class<?> itemType = Class.forName(PcbPlacementPlanner.class.getName() + "$Item");
        Constructor<?> constructor = itemType.getDeclaredConstructor(PcbFootprint.class,
            PcbPlacementConstraints.Part.class, BoardComponent.class, Integer.TYPE,
            Boolean.TYPE);
        constructor.setAccessible(true);
        PcbPlacementConstraints.Part demand = new PcbPlacementConstraints.Part(
            expected.componentId, "domain", "Domain", "region",
            PcbPlacementConstraints.Anchor.NONE, margin);
        Object item = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.ORTHOGONAL, Boolean.TRUE);
        check(readRectangle(itemType, item, "envelope").equals(original) &&
                readRectangle(itemType, item, "alternateEnvelope").equals(turned),
            "item stores both real source and TOP90 access envelopes " +
                expected.variantKey);
        check(readLong(itemType, item, "sizingDemandArea") == expectedDemand &&
                readInt(itemType, item, "sizingEnvelopeWidth") == expectedWidth &&
                readInt(itemType, item, "sizingEnvelopeHeight") == expectedHeight,
            "conservative demand and extents cover both poses " + expected.variantKey);

        Object inactiveItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.ORTHOGONAL, Boolean.FALSE);
        check(readRectangle(itemType, inactiveItem, "alternateEnvelope") == null &&
                readLong(itemType, inactiveItem, "sizingDemandArea") == originalDemand &&
                readInt(itemType, inactiveItem, "sizingEnvelopeWidth") == original.width &&
                readInt(itemType, inactiveItem, "sizingEnvelopeHeight") == original.height,
            "inactive policy branch retains exact TOP0 sizing " + expected.variantKey);

        if (expected.physicalPackage ==
                PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR) {
            checkRectangle(original, 85, 142, 150, 192,
                "literal electrolytic TOP0 access envelope");
            checkRectangle(turned, 86, 165, 172, 170,
                "literal electrolytic TOP90 access envelope");
            check(expectedDemand == 44520L && turnedDemand > originalDemand &&
                    expectedDemand == Math.max(demandProduct(
                    new Rectangle(85, 142, 150, 192)), demandProduct(
                    new Rectangle(86, 165, 172, 170))) &&
                    original.width != turned.width && original.height != turned.height,
                "electrolytic escape rotation contributes the larger sizing demand");
        }
    }

    private static long demandProduct(Rectangle envelope) {
        return (long)(envelope.width + PcbPlacementPlanner.CHANNEL) *
            (envelope.height + PcbPlacementPlanner.CHANNEL);
    }

    private static Rectangle readRectangle(Class<?> itemType, Object item,
            String fieldName) throws Exception {
        Field field = itemType.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Rectangle)field.get(item);
    }

    private static long readLong(Class<?> itemType, Object item,
            String fieldName) throws Exception {
        Field field = itemType.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(item);
    }

    private static int readInt(Class<?> itemType, Object item,
            String fieldName) throws Exception {
        Field field = itemType.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(item);
    }

    private static void checkRectangle(Rectangle actual, int x, int y,
            int width, int height, String message) {
        check(actual.x == x && actual.y == y && actual.width == width &&
                actual.height == height, message);
    }

    private static BoardComponent component(String componentId,
            PhysicalPackage physicalPackage) {
        BoardComponent component = new BoardComponent(componentId,
            physicalPackage.getId(), physicalPackage, componentId);
        for (String terminal : physicalPackage.getTerminalIds())
            component.addPadId(componentId + "." + terminal);
        return component;
    }

    private static void visitSequence(VariantCase item) {
        PhysicalPackage.GeometryVariant variant =
            item.physicalPackage.getGeometryVariant(item.variantKey);
        BoardComponent component = component(item.componentId, item.physicalPackage);
        final PcbFootprint source = PcbFootprint.fromPhysicalPackage(component,
            new PcbPackagePose(ORIGIN_X, ORIGIN_Y, PcbRotation.DEG_0,
                PcbBoardSide.TOP), variant.getGeometry());
        final PcbFootprint alternative = PcbPassivePosePolicy.alternativeFor(component,
            source, PcbPassivePosePolicy.ORTHOGONAL, true);
        int originalCount = 0;
        int alternateCount = 0;
        for (long visit = 0; visit < 126; visit++) {
            PcbFootprint selected = PcbPassivePosePolicy.forVisit(source,
                alternative, visit);
            if ((visit & 1L) == 0L) {
                check(selected == source, "even visit keeps TOP0 ordinal=" + visit);
                originalCount++;
            } else {
                check(selected == alternative,
                    "odd visit selects TOP90 ordinal=" + visit);
                alternateCount++;
            }
        }
        check(originalCount == 63 && alternateCount == 63,
            "126 visits retain an exact 63/63 pose balance");
        check(PcbPassivePosePolicy.forVisit(source, null, 125) == source,
            "null alternate always preserves source");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.forVisit(source, alternative, -1);
        }}, "negative visit ordinal rejected");
    }

    private static void inactiveAndUnsupported(VariantCase supported) throws Exception {
        final BoardComponent component = component(supported.componentId,
            supported.physicalPackage);
        final PhysicalPackage.GeometryVariant variant =
            supported.physicalPackage.getGeometryVariant(supported.variantKey);
        final PcbFootprint source = PcbFootprint.fromPhysicalPackage(component,
            new PcbPackagePose(ORIGIN_X, ORIGIN_Y, PcbRotation.DEG_0,
                PcbBoardSide.TOP), variant.getGeometry());
        check(PcbPassivePosePolicy.alternativeFor(component, source,
                PcbPassivePosePolicy.LEGACY, true) == null,
            "legacy policy has no alternate pose");
        check(PcbPassivePosePolicy.alternativeFor(component, source,
                PcbPassivePosePolicy.ORTHOGONAL, false) == null,
            "ineligible planner branch has no alternate pose");

        PhysicalPackage unsupported = PhysicalPackages.AXIAL_FUSE;
        check(!PcbPassivePosePolicy.supportsPackage(unsupported),
            "uncatalogued axial fuse package is outside the canonical allowlist");
        BoardComponent other = component("F1", unsupported);
        PcbFootprint otherSource = PcbFootprint.fromPhysicalPackage(other,
            new PcbPackagePose(ORIGIN_X, ORIGIN_Y, PcbRotation.DEG_0,
                PcbBoardSide.TOP), unsupported.getGeometry());
        check(PcbPassivePosePolicy.alternativeFor(other, otherSource,
                PcbPassivePosePolicy.ORTHOGONAL, true) == null,
            "unsupported package stays at its provider pose");

        PhysicalPackage sameName = PhysicalPackage.developerPackageWithGenericGeometry(
            PhysicalPackages.AXIAL_RESISTOR.getId(),
            PhysicalPackages.AXIAL_RESISTOR.getTerminalIds(), new Vector<String>(), false);
        check(!PcbPassivePosePolicy.supportsPackage(sameName),
            "package-name equality cannot substitute for canonical identity");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.alternativeFor(component, source, 9, true);
        }}, "unknown helper policy rejected");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.alternativeFor(component,
                PcbFootprint.fromPhysicalPackage(component,
                    new PcbPackagePose(ORIGIN_X, ORIGIN_Y, PcbRotation.DEG_90,
                        PcbBoardSide.TOP), variant.getGeometry()),
                PcbPassivePosePolicy.ORTHOGONAL, true);
        }}, "eligible source must be canonical TOP0");

        Method method = PcbPassivePosePolicy.class.getDeclaredMethod("alternativeFor",
            BoardComponent.class, PcbFootprint.class, Integer.TYPE, Boolean.TYPE);
        Class<?>[] parameterTypes = method.getParameterTypes();
        check(parameterTypes.length == 4,
            "alternative API has only component, source, version, and branch inputs");
        for (Class<?> parameterType : parameterTypes)
            check(parameterType != java.util.Random.class &&
                    parameterType != PcbFootprintRegistry.class,
                "alternative API cannot draw randomness or consult a registry");
    }

    private static void constraintCompatibility() {
        PcbPlacementConstraints legacy = constraints("MEDIUM_BOARD", 1, 14,
            true, PcbPassivePosePolicy.LEGACY);
        check(legacy.passivePosePolicyVersion == PcbPassivePosePolicy.LEGACY,
            "explicit legacy constructor version");
        PcbPlacementConstraints oldConstructor = new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            "MEDIUM_BOARD", 1, 14, true, PcbTraceWidthProfile.UNIFORM);
        check(oldConstructor.passivePosePolicyVersion == PcbPassivePosePolicy.LEGACY,
            "existing maximum constructor defaults to legacy policy0");

        PcbPlacementConstraints explicit = constraints("MEDIUM_BOARD", 1, 14,
            true, PcbPassivePosePolicy.ORTHOGONAL);
        check(explicit.passivePosePolicyVersion == PcbPassivePosePolicy.ORTHOGONAL,
            "explicit policy1 accepted only in its supported context");
        check(PcbPassivePosePolicy.supportsVersion(0) &&
                PcbPassivePosePolicy.supportsVersion(1) &&
                !PcbPassivePosePolicy.supportsVersion(-1) &&
                !PcbPassivePosePolicy.supportsVersion(2),
            "only policy versions0 and1 are recognized");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 14, true, 2);
        }}, "constraint rejects unknown policy version");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 13, true,
                PcbPassivePosePolicy.ORTHOGONAL);
        }}, "policy1 requires density14");
        rejects(new Attempt() { public void run() {
            constraints("OTHER_POLICY", 1, 14, true,
                PcbPassivePosePolicy.ORTHOGONAL);
        }}, "policy1 requires MEDIUM_BOARD@1");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 2, 14, true,
                PcbPassivePosePolicy.ORTHOGONAL);
        }}, "policy1 rejects an incompatible physical-policy epoch");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 14, false,
                PcbPassivePosePolicy.ORTHOGONAL);
        }}, "policy1 requires two-layer routing");
    }

    private static PcbPlacementConstraints constraints(String policyId,
            int policyVersion, int densityVersion, boolean twoLayer,
            int passivePosePolicyVersion) {
        return new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            policyId, policyVersion, densityVersion, twoLayer,
            PcbTraceWidthProfile.UNIFORM, passivePosePolicyVersion);
    }

    private static void sizingAndGridSeam(Pair pair) throws Exception {
        check(pair != null, "literal resistor fixture available for planner seam");
        Rectangle originalEnvelope = PcbPlacementPlanner.envelope(pair.source, 0);
        Rectangle turnedEnvelope = PcbPlacementPlanner.envelope(pair.alternative, 0);
        check(originalEnvelope.width > 0 && originalEnvelope.height > 0 &&
                turnedEnvelope.width > 0 && turnedEnvelope.height > 0,
            "real physical/access envelopes remain positive for both templates");
        Method gridOrigin = PcbPlacementPlanner.class.getDeclaredMethod(
            "mediumGridOrigin", PcbFootprint.class, Integer.TYPE, Rectangle.class,
            Integer.TYPE, Boolean.TYPE);
        gridOrigin.setAccessible(true);
        Rectangle outline = new Rectangle(0, 0, 320, 320);
        int originalClip = ((Integer)gridOrigin.invoke(null, pair.source, 0,
            outline, 0, false)).intValue();
        int turnedClip = ((Integer)gridOrigin.invoke(null, pair.alternative, 0,
            outline, 0, false)).intValue();
        check(originalClip != turnedClip,
            "real planner grid clipping reads the selected footprint envelope");
        check(originalClip >= 0 && turnedClip >= 0,
            "both clipped origins remain inside the actual outline floor");
    }

    private static void policyIdentityAndVersions() {
        check(PcbPassivePosePolicy.LEGACY == 0 &&
                PcbPassivePosePolicy.ORTHOGONAL == 1,
            "canonical policy version identifiers");
        check(PcbPassivePosePolicy.supportsPackage(PhysicalPackages.AXIAL_RESISTOR) &&
                PcbPassivePosePolicy.supportsPackage(PhysicalPackages.AXIAL_DIODE) &&
                PcbPassivePosePolicy.supportsPackage(
                    PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR) &&
                PcbPassivePosePolicy.supportsPackage(
                    PhysicalPackages.RADIAL_CERAMIC_CAPACITOR),
            "four canonical passive package references supported");
        check(!PcbPassivePosePolicy.supportsPackage(null),
            "null package unsupported");
    }

    private static void rejects(Attempt attempt, String message) {
        try {
            attempt.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
