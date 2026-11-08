package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Focused native copy-allowance boundary checks against the transformed parent. */
public final class NativeCopyAllowanceCanary {
    private static final String STUDY =
        "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final int COPY_LIMIT = 1048576;
    private static final int COPY_CHUNK = 4096;

    private NativeCopyAllowanceCanary() { }

    public static void main(String[] args) throws Exception {
        require(args == null || args.length == 0, "canary accepts no arguments");
        Class<?> study = Class.forName(STUDY);
        Class<?> meterType = Class.forName(STUDY + "$Meter");
        Class<?> captureDataType = Class.forName(STUDY + "$CaptureData");
        Class<?> freshRunType = Class.forName(STUDY + "$FreshRun");
        Class<?> boardType = Class.forName(
            "com.lushprojects.circuitjs1.client.TroubleshootBoard");
        Class<?> layoutType = Class.forName(
            "com.lushprojects.circuitjs1.client.PcbBoardLayout");

        Field limitField = study.getDeclaredField("COPY_LIMIT");
        limitField.setAccessible(true);
        require(limitField.getInt(null) == COPY_LIMIT, "pinned default copy limit changed");

        Constructor<?> meterConstructor = meterType.getDeclaredConstructor(long.class);
        meterConstructor.setAccessible(true);
        Method setAllowance = meterType.getDeclaredMethod("setCopyAllowance", int.class);
        setAllowance.setAccessible(true);
        Method reserveCopy = study.getDeclaredMethod(
            "reserveCopy", freshRunType, meterType, int.class);
        reserveCopy.setAccessible(true);

        Object defaultMeter = newMeter(meterConstructor);
        require(intField(defaultMeter, "copyAllowance") == COPY_LIMIT,
            "new meter did not retain the default allowance");
        Throwable negative = invokeFailure(setAllowance, defaultMeter, Integer.valueOf(-1));
        require(negative instanceof IllegalArgumentException,
            "negative allowance was not rejected");
        Throwable tooLarge = invokeFailure(
            setAllowance, defaultMeter, Integer.valueOf(COPY_LIMIT + 1));
        require(tooLarge instanceof IllegalArgumentException,
            "allowance above the existing limit was not rejected");
        require(intField(defaultMeter, "copyAllowance") == COPY_LIMIT &&
                longField(defaultMeter, "copiedEntries") == 0,
            "invalid allowance changed meter state");

        Object exactMeter = newMeter(meterConstructor);
        setAllowance.invoke(exactMeter, Integer.valueOf(2));
        invokeRequired(reserveCopy, null, null, exactMeter, Integer.valueOf(2));
        require(longField(exactMeter, "copiedEntries") == 2,
            "exact aggregate copy allowance was not accepted");
        Throwable over = invokeFailure(reserveCopy, null, null, exactMeter, Integer.valueOf(1));
        require(over instanceof java.io.IOException,
            "one-over aggregate copy allowance was not rejected");
        Throwable belowUsed = invokeFailure(
            setAllowance, exactMeter, Integer.valueOf(1));
        require(belowUsed instanceof IllegalStateException &&
                intField(exactMeter, "copyAllowance") == 2 &&
                longField(exactMeter, "copiedEntries") == 2,
            "lowering allowance below recorded copies mutated meter state");

        Object zeroMeter = newMeter(meterConstructor);
        setAllowance.invoke(zeroMeter, Integer.valueOf(0));
        invokeRequired(reserveCopy, null, null, zeroMeter, Integer.valueOf(0));
        Throwable zeroOver = invokeFailure(
            reserveCopy, null, null, zeroMeter, Integer.valueOf(1));
        require(zeroOver instanceof java.io.IOException,
            "zero allowance admitted a data copy");

        Object chunkMeter = newMeter(meterConstructor);
        invokeRequired(reserveCopy, null, null, chunkMeter, Integer.valueOf(COPY_CHUNK));
        require(longField(chunkMeter, "copiedEntries") == COPY_CHUNK &&
                intField(chunkMeter, "copyChunks") == 1,
            "4096-entry copy did not fit exactly one chunk");
        Object oversizeChunkMeter = newMeter(meterConstructor);
        Throwable oversized = invokeFailure(
            reserveCopy, null, null, oversizeChunkMeter, Integer.valueOf(COPY_CHUNK + 1));
        require(oversized instanceof java.io.IOException &&
                longField(oversizeChunkMeter, "copiedEntries") == 0 &&
                intField(oversizeChunkMeter, "copyChunks") == 0,
            "4097-entry copy was not rejected before counter mutation");

        Object constrainedMeter = newMeter(meterConstructor);
        Object independentMeter = newMeter(meterConstructor);
        setAllowance.invoke(constrainedMeter, Integer.valueOf(0));
        require(intField(independentMeter, "copyAllowance") == COPY_LIMIT,
            "allowance change leaked across meters");
        invokeRequired(reserveCopy, null, null, independentMeter, Integer.valueOf(1));
        Throwable independentFailure = invokeFailure(
            reserveCopy, null, null, constrainedMeter, Integer.valueOf(1));
        require(independentFailure instanceof java.io.IOException &&
                longField(independentMeter, "copiedEntries") == 1 &&
                longField(constrainedMeter, "copiedEntries") == 0,
            "independent meter allowances or counters were coupled");

        Object restoreMeter = newMeter(meterConstructor);
        setAllowance.invoke(restoreMeter, Integer.valueOf(2));
        invokeRequired(reserveCopy, null, null, restoreMeter, Integer.valueOf(1));
        setAllowance.invoke(restoreMeter, Integer.valueOf(COPY_LIMIT));
        invokeRequired(reserveCopy, null, null, restoreMeter, Integer.valueOf(1));
        require(intField(restoreMeter, "copyAllowance") == COPY_LIMIT &&
                longField(restoreMeter, "copiedEntries") == 2,
            "default allowance restoration did not preserve already counted copies");

        Method layoutEntryCount = study.getDeclaredMethod("layoutEntryCount", layoutType);
        layoutEntryCount.setAccessible(true);
        Method layoutCount = study.getDeclaredMethod(
            "layoutCount", layoutType, String.class);
        layoutCount.setAccessible(true);
        Method inherit = captureDataType.getDeclaredMethod("inheritCopyAllowance", meterType);
        inherit.setAccessible(true);
        Method prepare = study.getDeclaredMethod(
            "prepareLegacyCaptureCopies", Object.class, layoutType, captureDataType);
        prepare.setAccessible(true);
        Method reserveFingerprint = study.getDeclaredMethod(
            "reserveFingerprintCopies", freshRunType, meterType, layoutType);
        reserveFingerprint.setAccessible(true);
        Method reservePadLands = study.getDeclaredMethod(
            "reservePadLandCopies", meterType, layoutType);
        reservePadLands.setAccessible(true);

        FixtureView open = fixture(
            "open", study, meterConstructor, meterType, captureDataType,
            boardType, layoutType);
        FixtureView blocked = fixture(
            "blocked", study, meterConstructor, meterType, captureDataType,
            boardType, layoutType);
        verifyFixtureCopyContract(layoutEntryCount, layoutCount, reserveFingerprint, meterConstructor, open);
        verifyFixtureCopyContract(layoutEntryCount, layoutCount, reserveFingerprint, meterConstructor, blocked);
        int openLayoutEntries = ((Integer)layoutEntryCount.invoke(null, open.layout)).intValue();
        int blockedLayoutEntries = ((Integer)layoutEntryCount.invoke(null, blocked.layout)).intValue();
        require(openLayoutEntries > 0 && openLayoutEntries <= COPY_CHUNK &&
                blockedLayoutEntries > 0 && blockedLayoutEntries <= COPY_CHUNK,
            "genuine failed-first fixture layout count left its bounded range");

        int openRequired = preflightCount(
            meterConstructor, inherit, prepare, open);
        int blockedRequired = preflightCount(
            meterConstructor, inherit, prepare, blocked);
        require(openRequired > 1 && blockedRequired > openRequired,
            "genuine open/blocked fixture copy counts were not distinguished");

        runCapturePreflightBoundary(
            meterConstructor, setAllowance, inherit, prepare, open, openRequired);
        runFingerprintBoundary(
            meterConstructor, setAllowance, reserveFingerprint, open.layout);
        runPadLandBoundary(
            meterConstructor, setAllowance, reservePadLands, layoutCount, open.layout);

        System.out.println(
            "NATIVE_COPY_ALLOWANCE_CANARY_PASS openLayoutEntries=" + openLayoutEntries +
            " openCaptureEntries=" + openRequired +
            " blockedLayoutEntries=" + blockedLayoutEntries +
            " blockedCaptureEntries=" + blockedRequired);
    }

    private static void runCapturePreflightBoundary(
            Constructor<?> meterConstructor, Method setAllowance, Method inherit,
            Method prepare, FixtureView fixture, int required) throws Exception {
        requirePrepareFailure(
            meterConstructor, setAllowance, inherit, prepare, fixture, 0, required,
            "zero allowance");
        requirePrepareFailure(
            meterConstructor, setAllowance, inherit, prepare, fixture, required / 2, required,
            "partial allowance");
        requirePrepareFailure(
            meterConstructor, setAllowance, inherit, prepare, fixture, required - 1, required,
            "one-over allowance");

        Object exactMeter = newMeter(meterConstructor);
        setAllowance.invoke(exactMeter, Integer.valueOf(required));
        Object exactCapture = newCaptureData();
        inherit.invoke(exactCapture, exactMeter);
        prepare.invoke(null, fixture.search, fixture.live, exactCapture);
        require(intField(exactCapture, "copyAllowance") == required &&
                intField(exactCapture, "copiedEntries") == required,
            "exact genuine-fixture callback preflight did not fit");
    }

    private static void requirePrepareFailure(
            Constructor<?> meterConstructor, Method setAllowance, Method inherit,
            Method prepare, FixtureView fixture, int allowance, int required, String label)
            throws Exception {
        Object meter = newMeter(meterConstructor);
        setAllowance.invoke(meter, Integer.valueOf(allowance));
        Object capture = newCaptureData();
        inherit.invoke(capture, meter);
        Throwable failure = invokeFailure(prepare, null, fixture.search, fixture.layout, capture);
        require(failure instanceof java.io.IOException &&
                intField(capture, "copiedEntries") == 0,
            label + " did not reject before recording capture copies");
        require(required > allowance, label + " fixture boundary was not over capacity");
    }

    private static void runFingerprintBoundary(
            Constructor<?> meterConstructor, Method setAllowance, Method reserveFingerprint,
            Object layout) throws Exception {
        Object defaultMeter = newMeter(meterConstructor);
        reserveFingerprint.invoke(null, null, defaultMeter, layout);
        int required = (int)longField(defaultMeter, "copiedEntries");
        require(required > 0, "genuine fixture fingerprint copy census was empty");

        Object exactMeter = newMeter(meterConstructor);
        setAllowance.invoke(exactMeter, Integer.valueOf(required));
        reserveFingerprint.invoke(null, null, exactMeter, layout);
        require(longField(exactMeter, "copiedEntries") == required,
            "exact fingerprint allowance was not accepted");

        Object shortMeter = newMeter(meterConstructor);
        setAllowance.invoke(shortMeter, Integer.valueOf(required - 1));
        Throwable failure = invokeFailure(
            reserveFingerprint, null, null, shortMeter, layout);
        require(failure instanceof java.io.IOException &&
                longField(shortMeter, "copiedEntries") == 0,
            "fingerprint wrapper ignored its remaining allowance");
    }

    private static void runPadLandBoundary(
            Constructor<?> meterConstructor, Method setAllowance, Method reservePadLands,
            Method layoutCount, Object layout) throws Exception {
        int pads = ((Integer)layoutCount.invoke(null, layout, "pads")).intValue();
        int required = 11 * pads;
        require(pads > 0 && pads <= COPY_CHUNK,
            "genuine fixture pad count left its chunk bound");

        Object exactMeter = newMeter(meterConstructor);
        setAllowance.invoke(exactMeter, Integer.valueOf(required));
        reservePadLands.invoke(null, exactMeter, layout);
        require(longField(exactMeter, "copiedEntries") == required,
            "exact pad-land copy allowance was not accepted");

        Object shortMeter = newMeter(meterConstructor);
        setAllowance.invoke(shortMeter, Integer.valueOf(required - 1));
        Throwable failure = invokeFailure(reservePadLands, null, shortMeter, layout);
        require(failure instanceof java.io.IOException &&
                longField(shortMeter, "copiedEntries") == 0,
            "pad-land wrapper ignored its remaining allowance");
    }

    private static int preflightCount(
            Constructor<?> meterConstructor, Method inherit, Method prepare,
            FixtureView fixture) throws Exception {
        Object meter = newMeter(meterConstructor);
        Object capture = newCaptureData();
        inherit.invoke(capture, meter);
        require(intField(capture, "copyAllowance") == COPY_LIMIT,
            "default meter allowance was not carried into CaptureData");
        prepare.invoke(null, fixture.search, fixture.live, capture);
        int count = intField(capture, "copiedEntries");
        require(count > 0 && count <= COPY_LIMIT,
            "genuine fixture callback preflight count left the aggregate bound");
        return count;
    }

    private static FixtureView fixture(
            String kind, Class<?> study, Constructor<?> meterConstructor,
            Class<?> meterType, Class<?> captureDataType, Class<?> boardType,
            Class<?> layoutType) throws Exception {
        Class<?> fixtures = Class.forName(
            "com.lushprojects.circuitjs1.client.FailedFirstFixtures");
        Method factory = fixtures.getDeclaredMethod(kind);
        factory.setAccessible(true);
        Object fixture = factory.invoke(null);
        Object layout = field(fixture, "layout");
        Object board = field(fixture, "board");

        Class<?> diagnosticsType = Class.forName(
            "com.lushprojects.circuitjs1.client.PcbLayerRoutingPrototype$Diagnostics");
        Method newFreshSession = study.getDeclaredMethod(
            "newFreshSession", boardType, layoutType, captureDataType,
            diagnosticsType, meterType);
        newFreshSession.setAccessible(true);
        Object meter = newMeter(meterConstructor);
        Object session = newFreshSession.invoke(null, board, layout, null, null, meter);
        setIntField(session, "pass", 2);
        setIntField(session, "passes", 0);

        Method advance = session.getClass().getDeclaredMethod("advanceSlice", int.class);
        advance.setAccessible(true);
        Object search = null;
        for (int attempt = 0; attempt < 1024; attempt++) {
            advance.invoke(session, Integer.valueOf(1));
            search = field(session, "search");
            if (search != null && field(search, "branch") != null) break;
            if (booleanField(session, "complete")) break;
        }
        require(search != null && field(search, "branch") != null,
            "genuine failed-first fixture Session did not reach a real Search branch");
        Object owner = field(search, "owner");
        require(owner == session, "real Search owner was not the actual fixture Session");
        Object ownedTemplate = field(session, "template");
        require(ownedTemplate == field(owner, "template"),
            "Search owner template was not the Session-owned copy");
        Object live = field(session, "candidate");
        require(live != null && live == field(search, "layout"),
            "real Search did not use the actual Session candidate");
        require(ownedTemplate != layout && live != ownedTemplate && live != layout,
            "fixture source, Session-owned template, and live candidate identities were conflated");
        return new FixtureView(layout, ownedTemplate, live, search, session);
    }

    private static void verifyFixtureCopyContract(
            Method layoutEntryCount, Method layoutCount, Method reserveFingerprint,
            Constructor<?> meterConstructor, FixtureView fixture) throws Exception {
        Object[] copies = new Object[] {
            fixture.layout, fixture.ownedTemplate, fixture.live
        };
        int expectedEntries = ((Integer)layoutEntryCount.invoke(null, fixture.layout)).intValue();
        int expectedPads = ((Integer)layoutCount.invoke(null, fixture.layout, "pads")).intValue();
        Object fingerprintMeter = newMeter(meterConstructor);
        reserveFingerprint.invoke(null, null, fingerprintMeter, fixture.layout);
        String expectedGeometry = geometryFingerprint(fixture.layout);
        require(expectedEntries > 0 && expectedEntries <= COPY_CHUNK &&
                expectedPads > 0 && expectedPads <= COPY_CHUNK,
            "genuine fixture source geometry exceeded its bounded source contract");
        require(((Integer)layoutCount.invoke(null, fixture.layout, "traces")).intValue() == 0 &&
                ((Integer)layoutCount.invoke(null, fixture.layout, "holes")).intValue() == 0,
            "genuine fixture source unexpectedly contained routing copper");
        for (Object copy : copies) {
            if (copy != fixture.layout)
                reserveFingerprint.invoke(null, null, fingerprintMeter, copy);
            require(((Integer)layoutEntryCount.invoke(null, copy)).intValue() == expectedEntries,
                "Session copy changed the exact fixture layout-entry count");
            require(((Integer)layoutCount.invoke(null, copy, "pads")).intValue() == expectedPads,
                "Session copy changed the exact fixture pad count");
            require(((Integer)layoutCount.invoke(null, copy, "traces")).intValue() == 0 &&
                    ((Integer)layoutCount.invoke(null, copy, "holes")).intValue() == 0,
                "Session source or candidate introduced routing copper");
            if (copy != fixture.layout)
                require(expectedGeometry.equals(geometryFingerprint(copy)),
                    "Session source/owned-template/candidate geometry diverged");
        }
        require(longField(fingerprintMeter, "copiedEntries") <= COPY_LIMIT,
            "source/owned-template/candidate fingerprint copies exceeded the meter limit");
    }

    private static String geometryFingerprint(Object layout) throws Exception {
        Method fingerprint = layout.getClass().getDeclaredMethod("geometryFingerprint");
        fingerprint.setAccessible(true);
        return (String)fingerprint.invoke(layout);
    }

    private static Object newCaptureData() throws Exception {
        Class<?> type = Class.forName(STUDY + "$CaptureData");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object newMeter(Constructor<?> constructor) throws Exception {
        return constructor.newInstance(Long.valueOf(System.nanoTime()));
    }

    private static Object invokeRequired(
            Method method, Object receiver, Object... arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static Throwable invokeFailure(
            Method method, Object receiver, Object... arguments) throws Exception {
        try {
            method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            return failure.getCause();
        }
        return null;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static long longField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(target);
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FixtureView {
        final Object layout;
        final Object ownedTemplate;
        final Object live;
        final Object search;
        final Object session;
        FixtureView(Object layout, Object ownedTemplate, Object live,
                Object search, Object session) {
            this.layout=layout;
            this.ownedTemplate=ownedTemplate;
            this.live=live;
            this.search=search;
            this.session=session;
        }
    }
}
