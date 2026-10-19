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
        PcbFootprint sizingAlternative = PcbPassivePosePolicy.alternativeFor(component,
            source, PcbPassivePosePolicy.SIZING_ONLY, true);
        PcbFootprint sourceSizedAlternative = PcbPassivePosePolicy.alternativeFor(component,
            source, PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, true);
        check(alternative != null, "eligible canonical package gets one alternate " +
            expected.variantKey);
        check(sizingAlternative != null &&
                sizingAlternative.geometryFingerprint().equals(alternative.geometryFingerprint()),
            "policy2 derives the same geometry-bound TOP90 sizing alternative " +
                expected.variantKey);
        check(sourceSizedAlternative != null &&
                sourceSizedAlternative.geometryFingerprint().equals(
                    alternative.geometryFingerprint()),
            "policy3 derives the same geometry-bound TOP90 placement alternative " +
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

        verifyConservativeSizing(expected, source, alternative, sizingAlternative,
            sourceSizedAlternative);
        return new Pair(component, source, alternative);
    }

    private static void verifyConservativeSizing(VariantCase expected,
            PcbFootprint source, PcbFootprint alternative,
            PcbFootprint sizingAlternative, PcbFootprint sourceSizedAlternative)
            throws Exception {
        int margin = 20;
        String sourceBeforeItems = source.geometryFingerprint();
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
                readInt(itemType, item, "sizingEnvelopeHeight") == expectedHeight &&
                readLong(itemType, item, "guardDemandArea") == expectedDemand,
            "conservative demand, extents, and guard cover both poses " +
                expected.variantKey);

        Object legacyItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.LEGACY, Boolean.TRUE);
        check(readRectangle(itemType, legacyItem, "alternateEnvelope") == null &&
                readLong(itemType, legacyItem, "sizingDemandArea") == originalDemand &&
                readInt(itemType, legacyItem, "sizingEnvelopeWidth") == original.width &&
                readInt(itemType, legacyItem, "sizingEnvelopeHeight") == original.height &&
                readLong(itemType, legacyItem, "guardDemandArea") == originalDemand,
            "policy0 keeps its original source sizing and demand guard " +
                expected.variantKey);

        Object sizingItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.SIZING_ONLY, Boolean.TRUE);
        check(readRectangle(itemType, sizingItem, "alternateEnvelope").equals(turned) &&
                readRectangle(itemType, sizingItem, "alternateEnvelope").equals(
                    PcbPlacementPlanner.envelope(sizingAlternative, margin)) &&
                readLong(itemType, sizingItem, "sizingDemandArea") ==
                    readLong(itemType, item, "sizingDemandArea") &&
                readInt(itemType, sizingItem, "sizingEnvelopeWidth") ==
                    readInt(itemType, item, "sizingEnvelopeWidth") &&
                readInt(itemType, sizingItem, "sizingEnvelopeHeight") ==
                    readInt(itemType, item, "sizingEnvelopeHeight") &&
                readLong(itemType, sizingItem, "guardDemandArea") == expectedDemand,
            "policy2 retains policy1's conservative sizing and guard basis " +
                expected.variantKey);

        Object sourceSizedItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, Boolean.TRUE);
        check(readRectangle(itemType, sourceSizedItem, "alternateEnvelope").equals(turned) &&
                readRectangle(itemType, sourceSizedItem, "alternateEnvelope").equals(
                    PcbPlacementPlanner.envelope(sourceSizedAlternative, margin)) &&
                readLong(itemType, sourceSizedItem, "sizingDemandArea") == originalDemand &&
                readInt(itemType, sourceSizedItem, "sizingEnvelopeWidth") == original.width &&
                readInt(itemType, sourceSizedItem, "sizingEnvelopeHeight") == original.height &&
                readLong(itemType, sourceSizedItem, "guardDemandArea") == expectedDemand &&
                readLong(itemType, sourceSizedItem, "guardDemandArea") ==
                    readLong(itemType, item, "sizingDemandArea"),
            "policy3 ranks by source TOP0 while retaining the actual alternate and " +
                "conservative guard " + expected.variantKey);

        Object inactiveItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.ORTHOGONAL, Boolean.FALSE);
        check(readRectangle(itemType, inactiveItem, "alternateEnvelope") == null &&
                readLong(itemType, inactiveItem, "sizingDemandArea") == originalDemand &&
                readInt(itemType, inactiveItem, "sizingEnvelopeWidth") == original.width &&
                readInt(itemType, inactiveItem, "sizingEnvelopeHeight") == original.height,
            "inactive policy branch retains exact TOP0 sizing " + expected.variantKey);

        Object inactiveSizingItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.SIZING_ONLY, Boolean.FALSE);
        check(readRectangle(itemType, inactiveSizingItem, "alternateEnvelope") == null &&
                readLong(itemType, inactiveSizingItem, "sizingDemandArea") == originalDemand &&
                readInt(itemType, inactiveSizingItem, "sizingEnvelopeWidth") == original.width &&
                readInt(itemType, inactiveSizingItem, "sizingEnvelopeHeight") == original.height &&
                readLong(itemType, inactiveSizingItem, "guardDemandArea") == originalDemand,
            "inactive policy2 branch retains exact TOP0 sizing " + expected.variantKey);

        Object inactiveSourceSizedItem = constructor.newInstance(source, demand,
            component(expected.componentId, expected.physicalPackage),
            PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, Boolean.FALSE);
        check(readRectangle(itemType, inactiveSourceSizedItem, "alternateEnvelope") == null &&
                readLong(itemType, inactiveSourceSizedItem, "sizingDemandArea") ==
                    originalDemand &&
                readInt(itemType, inactiveSourceSizedItem, "sizingEnvelopeWidth") ==
                    original.width &&
                readInt(itemType, inactiveSourceSizedItem, "sizingEnvelopeHeight") ==
                    original.height &&
                readLong(itemType, inactiveSourceSizedItem, "guardDemandArea") ==
                    originalDemand,
            "inactive policy3 branch retains exact TOP0 sizing " + expected.variantKey);

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
                    original.width != turned.width && original.height != turned.height &&
                    readLong(itemType, sourceSizedItem, "sizingDemandArea") ==
                        demandProduct(new Rectangle(85, 142, 150, 192)) &&
                    readLong(itemType, sourceSizedItem, "guardDemandArea") == 44520L,
                "literal electrolytic asymmetry source-sizes ranking but keeps the max guard");
        }
        check(source.geometryFingerprint().equals(sourceBeforeItems),
            "policy0/1/2/3 Item construction leaves source TOP0 geometry immutable " +
                expected.variantKey);
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
        final String sourceBefore = source.geometryFingerprint();
        final String alternativeBefore = alternative.geometryFingerprint();
        int originalCount = 0;
        int alternateCount = 0;
        for (long visit = 0; visit < 126; visit++) {
            PcbFootprint selected = PcbPassivePosePolicy.forVisit(source,
                alternative, PcbPassivePosePolicy.ORTHOGONAL, visit);
            PcbFootprint sourceSizedSelected = PcbPassivePosePolicy.forVisit(source,
                alternative, PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, visit);
            check(sourceSizedSelected == selected,
                "policy3 exactly matches policy1 schedule ordinal=" + visit);
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
        for (long visit = 0; visit < 126; visit++)
            check(PcbPassivePosePolicy.forVisit(source, alternative,
                    PcbPassivePosePolicy.SIZING_ONLY, visit) == source,
                "policy2 sizing visits retain source TOP0 ordinal=" + visit);
        long[] fallbackVisits = new long[] {126, 127, 59999};
        for (long visit : fallbackVisits) {
            PcbFootprint policy1 = PcbPassivePosePolicy.forVisit(source, alternative,
                PcbPassivePosePolicy.ORTHOGONAL, visit);
            check(PcbPassivePosePolicy.forVisit(source, alternative,
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, visit) == policy1,
                "policy3 fallback visits retain policy1 parity ordinal=" + visit);
            check(PcbPassivePosePolicy.forVisit(source, alternative,
                    PcbPassivePosePolicy.SIZING_ONLY, visit) == source,
                "policy2 fallback visits retain source TOP0 ordinal=" + visit);
        }
        long[] boundaryVisits = new long[] {Long.MAX_VALUE - 1L, Long.MAX_VALUE};
        for (long visit : boundaryVisits) {
            PcbFootprint policy1 = PcbPassivePosePolicy.forVisit(source, alternative,
                PcbPassivePosePolicy.ORTHOGONAL, visit);
            check(PcbPassivePosePolicy.forVisit(source, alternative,
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, visit) == policy1 &&
                    policy1 == ((visit & 1L) == 0L ? source : alternative),
                "policy3 preserves policy1 parity at long boundary ordinal=" + visit);
            check(PcbPassivePosePolicy.forVisit(source, alternative,
                    PcbPassivePosePolicy.SIZING_ONLY, visit) == source,
                "policy2 retains TOP0 at long boundary ordinal=" + visit);
        }
        check(PcbPassivePosePolicy.forVisit(source, null,
                PcbPassivePosePolicy.ORTHOGONAL, 125) == source &&
                PcbPassivePosePolicy.forVisit(source, null,
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, 125) == source,
            "null alternate always preserves source");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.forVisit(source, alternative,
                PcbPassivePosePolicy.ORTHOGONAL, -1);
        }}, "negative visit ordinal rejected");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.forVisit(source, alternative, 4, 0);
        }}, "unknown visit policy4 rejected");
        check(PcbPassivePosePolicy.forVisit(source, alternative,
                PcbPassivePosePolicy.LEGACY, 1) == source,
            "legacy policy retains TOP0 even when given an unused alternate");
        check(source.geometryFingerprint().equals(sourceBefore) &&
                alternative.geometryFingerprint().equals(alternativeBefore),
            "policy1, policy2, and policy3 visit selection leaves both templates immutable");
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
                PcbPassivePosePolicy.ORTHOGONAL, false) == null &&
                PcbPassivePosePolicy.alternativeFor(component, source,
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, false) == null,
            "ineligible planner branches have no alternate pose");

        PhysicalPackage unsupported = PhysicalPackages.AXIAL_FUSE;
        check(!PcbPassivePosePolicy.supportsPackage(unsupported),
            "uncatalogued axial fuse package is outside the canonical allowlist");
        BoardComponent other = component("F1", unsupported);
        PcbFootprint otherSource = PcbFootprint.fromPhysicalPackage(other,
            new PcbPackagePose(ORIGIN_X, ORIGIN_Y, PcbRotation.DEG_0,
                PcbBoardSide.TOP), unsupported.getGeometry());
        check(PcbPassivePosePolicy.alternativeFor(other, otherSource,
                PcbPassivePosePolicy.ORTHOGONAL, true) == null &&
                PcbPassivePosePolicy.alternativeFor(other, otherSource,
                    PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL, true) == null,
            "unsupported package stays at its provider pose under policies1 and3");

        PhysicalPackage sameName = PhysicalPackage.developerPackageWithGenericGeometry(
            PhysicalPackages.AXIAL_RESISTOR.getId(),
            PhysicalPackages.AXIAL_RESISTOR.getTerminalIds(), new Vector<String>(), false);
        check(!PcbPassivePosePolicy.supportsPackage(sameName),
            "package-name equality cannot substitute for canonical identity");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.alternativeFor(component, source, 9, true);
        }}, "unknown helper policy rejected");
        rejects(new Attempt() { public void run() {
            PcbPassivePosePolicy.alternativeFor(component, source, 4, true);
        }}, "unknown helper policy4 rejected");
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
        PcbPlacementConstraints sourceSized = constraints("MEDIUM_BOARD", 1, 14,
            true, PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL);
        check(sourceSized.passivePosePolicyVersion ==
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL,
            "explicit policy3 accepted only in its supported context");
        check(PcbPassivePosePolicy.supportsVersion(0) &&
                PcbPassivePosePolicy.supportsVersion(1) &&
                PcbPassivePosePolicy.supportsVersion(2) &&
                PcbPassivePosePolicy.supportsVersion(3) &&
                !PcbPassivePosePolicy.supportsVersion(-1) &&
                !PcbPassivePosePolicy.supportsVersion(4),
            "only policy versions0, 1, 2, and 3 are recognized");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 14, true, 4);
        }}, "constraint rejects unknown policy version4");
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
        PcbPlacementConstraints sizingOnly = constraints("MEDIUM_BOARD", 1, 14,
            true, PcbPassivePosePolicy.SIZING_ONLY);
        check(sizingOnly.passivePosePolicyVersion == PcbPassivePosePolicy.SIZING_ONLY,
            "policy2 is accepted only in its supported sizing context");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 13, true, PcbPassivePosePolicy.SIZING_ONLY);
        }}, "policy2 requires density14");
        rejects(new Attempt() { public void run() {
            constraints("OTHER_POLICY", 1, 14, true, PcbPassivePosePolicy.SIZING_ONLY);
        }}, "policy2 requires MEDIUM_BOARD@1");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 2, 14, true, PcbPassivePosePolicy.SIZING_ONLY);
        }}, "policy2 rejects an incompatible physical-policy epoch");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 14, false, PcbPassivePosePolicy.SIZING_ONLY);
        }}, "policy2 requires two-layer routing");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 13, true,
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL);
        }}, "policy3 requires density14");
        rejects(new Attempt() { public void run() {
            constraints("OTHER_POLICY", 1, 14, true,
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL);
        }}, "policy3 requires MEDIUM_BOARD@1");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 2, 14, true,
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL);
        }}, "policy3 rejects an incompatible physical-policy epoch");
        rejects(new Attempt() { public void run() {
            constraints("MEDIUM_BOARD", 1, 14, false,
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL);
        }}, "policy3 requires two-layer routing");
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

    private static void policyIdentityAndVersions() throws Exception {
        check(PcbPassivePosePolicy.LEGACY == 0 &&
                PcbPassivePosePolicy.ORTHOGONAL == 1 &&
                PcbPassivePosePolicy.SIZING_ONLY == 2 &&
                PcbPassivePosePolicy.SOURCE_SIZED_ORTHOGONAL == 3,
            "canonical policy version identifiers");
        Method selector = PcbPassivePosePolicy.class.getDeclaredMethod("forVisit",
            PcbFootprint.class, PcbFootprint.class, Integer.TYPE, Long.TYPE);
        int selectorMethods = 0;
        for (Method method : PcbPassivePosePolicy.class.getDeclaredMethods())
            if ("forVisit".equals(method.getName())) selectorMethods++;
        check(selector.getParameterTypes().length == 4 && selectorMethods == 1,
            "visit selector requires an explicit policy and has no legacy overload");
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
