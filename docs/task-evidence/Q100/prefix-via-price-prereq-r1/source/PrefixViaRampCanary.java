package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Native-only canary for the frozen-prefix via-price overlay. */
public final class PrefixViaRampCanary {
    private static int assertions;
    private static final int BASE = 60;
    private static final int VIA_LIMIT = 48;
    private static final int PRIMARY_LAYER = 0;
    private static final int SECONDARY_LAYER = 1;
    private static final int GRID = 10;
    private static final int TURN = 35;
    private static final int SECONDARY = 2;
    private static final int MAX_CAPTURE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ZERO_CAP_SOURCE_UNITS = 2000000;

    private PrefixViaRampCanary() { }

    public static void main(String[] args) {
        final long workStarted=System.nanoTime();
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        SharedByteBudget outputBudget = new SharedByteBudget(MAX_CAPTURE_BYTES);
        java.io.PrintStream boundedOut;
        java.io.PrintStream boundedErr;
        try {
            boundedOut = new java.io.PrintStream(
                new CappedOutputStream(originalOut,outputBudget),true,"UTF-8");
            boundedErr = new java.io.PrintStream(
                new CappedOutputStream(originalErr,outputBudget),true,"UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable",impossible);
        }
        System.setOut(boundedOut);
        System.setErr(boundedErr);
        Throwable failure = null;
        boolean observing = false;
        try {
            if (args != null && args.length == 1 && "--capture-overflow".equals(args[0])) {
                try {
                    System.out.write(new byte[MAX_CAPTURE_BYTES + 1],0,MAX_CAPTURE_BYTES + 1);
                    System.out.flush();
                    throw new AssertionError("oversized output was not rejected");
                } catch (CaptureLimitError expected) {
                    if (!outputBudget.overflowed())
                        throw new AssertionError("shared output cap did not latch before forwarding");
                    System.err.println("PREFIX_VIA_RAMP_CAPTURE_LIMIT");
                    System.err.flush();
                    throw expected;
                }
            }
            if (args != null && args.length != 0)
                throw new IllegalArgumentException("Canary accepts no arguments or --capture-overflow");
            verifyPriceArithmetic();
            verifyLiteralWeightedPaths();
            PrefixViaRampSupport.startObservation();
            observing = true;
            CopyEvidence copy = runRealDetachedCopyCanary();
            GuardEvidence guards = verifyActualTransitionGuards();
            String copyGate = copy == null ? "NOT_RUN" : "PASS";
            int copyHoles = copy == null ? -1 : copy.prefixHoles;
            int copyMaximumVias = copy == null ? -1 : copy.maximumVias;
            int baseViaCost = copy == null ? BASE : copy.baseViaCost;
            int sourcePrice = copy == null ? -1 : copy.sourceBranchPrice;
            int copiedBasePrice = copy == null ? -1 : copy.copiedBase60Price;
            int copiedRaisedPrice = copy == null ? -1 : copy.copiedRaisedPrice;
            int priceRows = PrefixViaRampSupport.stopObservation();
            observing = false;
            verifyObservationRows(priceRows,copy != null);
            requireOutputHealthy(boundedOut,boundedErr,outputBudget);
            final long workElapsedNanos=System.nanoTime()-workStarted;
            require(workElapsedNanos>=0 && workElapsedNanos<=5000000000L,
                "complete native canary work stays within the unchanged five-second unit bound");
            String summary = "{\"schema\":\"tsj-prefix-via-ramp-canary-v1\",\"scope\":" +
                "\"PREFIX_VIA_PRICE_PREREQUISITE_ONLY\",\"mode\":\"" +
                PrefixViaRampSupport.mode() + "\",\"assertions\":" + assertions +
                ",\"price\":\"PASS\",\"literalPath\":\"PASS\",\"realBranch\":\"" +
                (copy == null ? "NOT_RUN" : "PASS") + "\",\"zeroCapGuard\":\"" +
                guards.zeroCap + "\",\"saturatedCapGuard\":\"" + guards.saturatedCap +
                "\",\"copyGate\":\"" + copyGate + "\",\"copyPrefixHoles\":" + copyHoles +
                ",\"copyMaximumVias\":" + copyMaximumVias + ",\"baseViaCost\":" + baseViaCost +
                ",\"sourceBranchPrice\":" + sourcePrice + ",\"copiedBase60Price\":" + copiedBasePrice +
                ",\"copiedRaisedPrice\":" + copiedRaisedPrice +
                ",\"sourceUnchanged\":\"" + (copy == null ? "NOT_RUN" : "PASS") +
                "\",\"priceRows\":" + priceRows + ",\"captureOutput\":\"PASS\"," +
                "\"authoredRun\":\"NOT_RUN\",\"phase2\":\"NOT_RUN\"," +
                "\"canaryWorkElapsedNanos\":"+workElapsedNanos+",\"layoutQualification\":false}";
            System.out.println("PASS_PREFIX_VIA_RAMP_CANARY " + summary);
            System.out.flush();
            System.err.flush();
            requireOutputHealthy(boundedOut,boundedErr,outputBudget);
            require(System.nanoTime()-workStarted<=5000000000L,
                "canary report output also stays within the five-second unit bound");
        } catch (Throwable problem) {
            failure = problem;
            try {
                problem.printStackTrace(System.err);
                System.err.flush();
            } catch (CaptureLimitError overflow) {
                try {
                    System.err.println("PREFIX_VIA_RAMP_CAPTURE_LIMIT");
                    System.err.flush();
                } catch (CaptureLimitError ignored) { }
            } catch (Throwable ignored) { }
        } finally {
            if (observing) {
                try { PrefixViaRampSupport.stopObservation(); }
                catch (Throwable cleanupFailure) {
                    if (failure == null) failure = cleanupFailure;
                    else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
                }
            }
            try { PrefixViaRampSupport.clearObservation(); }
            catch (Throwable cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
            }
            if (failure != null) {
                try { failure.printStackTrace(boundedErr); boundedErr.flush(); }
                catch (Throwable captureFailure) { /* Nonzero exit remains mandatory. */ }
            }
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        if (failure != null) System.exit(1);
    }

    private static void requireOutputHealthy(java.io.PrintStream out,java.io.PrintStream err,
            SharedByteBudget budget) {
        if (budget.overflowed() || out.checkError() || err.checkError())
            throw new IllegalStateException("Prefix VIA canary output failed or exceeded its shared cap");
    }
    private static void verifyPriceArithmetic() {
        int[] holes = {0,1,12,24,36,47,48,49};
        int[] ramp = {60,61,75,90,105,118,120,120};
        int previous = -1;
        for (int i = 0; i < holes.length; i++) {
            int actual = PrefixViaRampSupport.price(BASE,holes[i],VIA_LIMIT);
            equal(ramp[i],actual,"literal price at H=" + holes[i]);
            require(actual >= previous,"prefix price is monotone");
            previous = actual;
            int expectedEffective = "RAMP".equals(PrefixViaRampSupport.mode()) ? ramp[i] : BASE;
            equal(expectedEffective,PrefixViaRampSupport.effectivePrice(BASE,holes[i],VIA_LIMIT),
                "compile-time treatment at H=" + holes[i]);
        }
        previous = -1;
        for (int h = 0; h <= VIA_LIMIT; h++) {
            int actual = PrefixViaRampSupport.price(BASE,h,VIA_LIMIT);
            require(actual >= previous,"frozen-prefix price is monotone for every legal hole count");
            previous = actual;
        }
        equal(BASE,PrefixViaRampSupport.price(BASE,0,0),"zero-cap price at empty prefix");
        equal(BASE,PrefixViaRampSupport.price(BASE,1,0),"zero-cap price is guarded before division");
        equal(BASE,PrefixViaRampSupport.effectivePrice(BASE,0,0),"zero-cap effective price");
        equal(120,PrefixViaRampSupport.effectivePrice(120,24,VIA_LIMIT),
            "detached explicit raised price remains constant");
        equal(0,PrefixViaRampSupport.effectivePrice(0,24,VIA_LIMIT),
            "zero-cost policy remains unchanged");
        expectInvalid(new Operation() { public int run() {
            return PrefixViaRampSupport.price(-1,0,VIA_LIMIT);
        }},"negative base rejected");
        expectInvalid(new Operation() { public int run() {
            return PrefixViaRampSupport.price(BASE,-1,VIA_LIMIT);
        }},"negative prefix rejected");
        expectInvalid(new Operation() { public int run() {
            return PrefixViaRampSupport.price(BASE,0,-1);
        }},"negative VIA maximum rejected");
        expectInvalid(new Operation() { public int run() {
            return PrefixViaRampSupport.price(Integer.MAX_VALUE,1,1);
        }},"signed-int result overflow rejected");
        equal(120,PrefixViaRampSupport.price(BASE,48,48),"full-cap price reaches 120");
        equal(120,PrefixViaRampSupport.price(BASE,400,200),"prefix is clamped to its maximum");
    }

    private static void verifyLiteralWeightedPaths() {
        PathFacts planar = inspect(planarPath());
        PathFacts alternate = inspect(alternatePath());
        equal(12,planar.planarEdges,"planar literal edge count");
        equal(2,planar.turns,"planar literal turn count");
        equal(0,planar.transitions,"planar literal transition count");
        equal(0,planar.holes,"planar literal hole count");
        equal(10,alternate.planarEdges,"alternate literal edge count");
        equal(0,alternate.turns,"alternate direction resets at its face transition");
        equal(1,alternate.transitions,"alternate literal transition count");
        equal(1,alternate.holes,"one transition consumes one abstract VIA hole");
        equal(PRIMARY_LAYER,planar.startLayer,"planar literal begins on the primary face");
        equal(PRIMARY_LAYER,planar.endLayer,"planar literal remains on the primary face");
        equal(SECONDARY_LAYER,alternate.startLayer,
            "alternate literal begins on the secondary face and pays its initial seed charge");
        equal(PRIMARY_LAYER,alternate.endLayer,
            "alternate literal crosses to the primary face before its remaining planar steps");
        equal(1,alternate.secondaryToPrimaryTransitions,
            "alternate VIA transitions from secondary to primary");

        int directDistance = 10 * GRID;
        int planarLength = planar.planarEdges * GRID;
        int alternateLength = alternate.planarEdges * GRID;
        equal(100,directDistance,"shared endpoints have direct length 100");
        equal(120,planarLength,"planar path length");
        equal(100,alternateLength,"alternate planar length");
        require(planarLength <= 3 * directDistance && alternateLength <= 3 * directDistance,
            "both literal paths satisfy the independent 3:1 length bound");
        require(planar.transitions <= 4 && alternate.transitions <= 4 &&
                planar.holes <= VIA_LIMIT && alternate.holes <= VIA_LIMIT,
            "literal paths remain within four-transition and 48-hole caps");

        int planarCost = 120 + 2 * TURN;
        int alternateInitialFaceCharge = alternate.startLayer == SECONDARY_LAYER ? SECONDARY : 0;
        equal(SECONDARY,alternateInitialFaceCharge,
            "alternate path starts on the actual nonprimary search face");
        int alternateAt75 = alternateLength + alternateInitialFaceCharge + 75;
        int alternateAt90 = alternateLength + alternateInitialFaceCharge + 90;
        equal(190,planarCost,"literal two-turn planar cost");
        equal(177,alternateAt75,"literal earlier-prefix alternate cost");
        equal(192,alternateAt90,"literal later-prefix alternate cost");
        require(alternateAt75 < planarCost,"literal H=12 alternate is cheaper");
        require(alternateAt90 > planarCost,"literal H=24 planar route is cheaper");
        equal(75,PrefixViaRampSupport.price(BASE,12,VIA_LIMIT),
            "independent path oracle uses literal earlier prefix price");
        equal(90,PrefixViaRampSupport.price(BASE,24,VIA_LIMIT),
            "independent path oracle uses literal later prefix price");
    }

    private static PathStep[] planarPath() {
        ArrayList<PathStep> path = new ArrayList<PathStep>();
        path.add(new PathStep(0,0,0));
        path.add(new PathStep(0,1,0));
        for (int x = 1; x <= 10; x++) path.add(new PathStep(x,1,0));
        path.add(new PathStep(10,0,0));
        return path.toArray(new PathStep[path.size()]);
    }

    private static PathStep[] alternatePath() {
        ArrayList<PathStep> path = new ArrayList<PathStep>();
        path.add(new PathStep(0,0,SECONDARY_LAYER));
        path.add(new PathStep(1,0,SECONDARY_LAYER));
        path.add(new PathStep(2,0,SECONDARY_LAYER));
        path.add(new PathStep(2,0,PRIMARY_LAYER));
        for (int x = 3; x <= 10; x++) path.add(new PathStep(x,0,PRIMARY_LAYER));
        return path.toArray(new PathStep[path.size()]);
    }

    private static PathFacts inspect(PathStep[] path) {
        require(path != null && path.length >= 2,"literal path is nonempty");
        Set<String> occupied = new HashSet<String>();
        PathFacts facts = new PathFacts();
        facts.startLayer = path[0].layer;
        facts.endLayer = path[path.length-1].layer;
        int direction = 4;
        for (int i = 0; i < path.length; i++) {
            PathStep step = path[i];
            require(step.x >= 0 && step.x <= 10 && step.y >= 0 && step.y <= 1 &&
                    (step.layer == 0 || step.layer == 1),"literal coordinate lies in its bounded grid");
            require(occupied.add(step.layer + ":" + step.x + ":" + step.y),
                "literal path does not repeat a same-face cell");
            if (i == 0) continue;
            PathStep prior = path[i - 1];
            if (step.layer != prior.layer) {
                require(step.x == prior.x && step.y == prior.y,
                    "literal face transition stays at one cell");
                facts.transitions++;
                if (prior.layer == SECONDARY_LAYER && step.layer == PRIMARY_LAYER)
                    facts.secondaryToPrimaryTransitions++;
                facts.holes++;
                direction = 4;
            } else {
                int dx = step.x - prior.x, dy = step.y - prior.y;
                require(Math.abs(dx) + Math.abs(dy) == 1,
                    "literal planar edge is one cardinal grid step");
                int nextDirection = dy < 0 ? 0 : dx > 0 ? 1 : dy > 0 ? 2 : 3;
                if (direction != 4 && direction != nextDirection) facts.turns++;
                direction = nextDirection;
                facts.planarEdges++;
            }
        }
        return facts;
    }

    /** Existing source-owned fixture starts from real prior Publisher branches. */
    private static CopyEvidence runRealDetachedCopyCanary() throws Exception {
        final Class<?> contract;
        try {
            contract = Class.forName(
                "com.lushprojects.circuitjs1.client.P07DetachedBranchReplayContractTest");
        } catch (ClassNotFoundException unavailable) {
            return null;
        }
        Method fresh = contract.getDeclaredMethod("freshSource",int.class);
        fresh.setAccessible(true);
        Object source = invoke(fresh,null,VIA_LIMIT);
        Class<?> sourceType = source.getClass();
        int prefixHoles = Array.getLength(field(source,"prefixIds"));
        int maximumVias = intField(source,"maximumVias");
        int baseViaCost = intField(source,"viaCost");
        Object sourceBranch = field(source,"branch");
        int sourceBranchPrice = intField(sourceBranch,"prefixViaPrice");
        int expectedBasePrice = PrefixViaRampSupport.effectivePrice(BASE,prefixHoles,maximumVias);
        require(prefixHoles == 2 && maximumVias == VIA_LIMIT && baseViaCost == BASE,
            "real source branch begins with two Publisher holes and the 48 VIA allowance");
        equal(expectedBasePrice,sourceBranchPrice,
            "real source branch freezes the mode-specific price at its actual prefix");

        Method buildBase = contract.getDeclaredMethod("buildDetached",sourceType,int.class);
        Method buildRaised = contract.getDeclaredMethod("buildDetachedAtCapAndCost",
            sourceType,int.class,int.class,int.class);
        Method verifyUnchanged = contract.getDeclaredMethod("verifySourceUnchanged",
            sourceType,String.class);
        buildBase.setAccessible(true);
        buildRaised.setAccessible(true);
        verifyUnchanged.setAccessible(true);
        Object baseReplay = null;
        Object raisedReplay = null;
        try {
            baseReplay = invoke(buildBase,null,source,32768);
            Object baseCopyBranch = field(baseReplay,"branch");
            int copiedBase60Price = intField(baseCopyBranch,"prefixViaPrice");
            equal(expectedBasePrice,copiedBase60Price,
                "real detached copy recomputes its price from its actual two-hole owner prefix");
            invoke(verifyUnchanged,null,source,"base-60 detached copy");
            equal(sourceBranchPrice,intField(sourceBranch,"prefixViaPrice"),
                "normal copy leaves the original frozen branch price unchanged");

            raisedReplay = invoke(buildRaised,null,source,32768,VIA_LIMIT,120);
            Object raisedCopyBranch = field(raisedReplay,"branch");
            int copiedRaisedPrice = intField(raisedCopyBranch,"prefixViaPrice");
            equal(120,copiedRaisedPrice,
                "real detached copy keeps the explicit raised owner scalar constant");
            invoke(verifyUnchanged,null,source,"explicit-raised detached copy");
            equal(sourceBranchPrice,intField(sourceBranch,"prefixViaPrice"),
                "raised copy leaves the original frozen branch price unchanged");

            return new CopyEvidence(prefixHoles,maximumVias,baseViaCost,sourceBranchPrice,
                copiedBase60Price,copiedRaisedPrice);
        } finally {
            releaseReplay(baseReplay);
            releaseReplay(raisedReplay);
        }
    }

    /** Calls the production path guard on actual empty and saturated source branches. */
    private static GuardEvidence verifyActualTransitionGuards() throws Exception {
        final Class<?> contract;
        try {
            contract = Class.forName(
                "com.lushprojects.circuitjs1.client.P07DetachedBranchReplayContractTest");
        } catch (ClassNotFoundException unavailable) {
            return new GuardEvidence("NOT_RUN","NOT_RUN");
        }
        Method fixtureMethod = contract.getDeclaredMethod("literalSourceFixture");
        fixtureMethod.setAccessible(true);
        Object fixture = invoke(fixtureMethod,null);
        TroubleshootBoard board = (TroubleshootBoard)field(fixture,"board");
        PcbBoardLayout layout = (PcbBoardLayout)field(fixture,"layout");
        SeededPcbLayoutGenerator.AttemptObserver observer =
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { }
            };
        PcbLayerRoutingPrototype.Session zero = PcbLayerRoutingPrototype.begin(
            board,layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,0);
        Object zeroBranch = null;
        Object zeroSearch = null;
        for (int units = 0; units < MAX_ZERO_CAP_SOURCE_UNITS; units++) {
            Object search = field(zero,"search");
            zeroSearch = search;
            if (search != null) {
                zeroBranch = field(search,"branch");
                if (zeroBranch != null) break;
            }
            if (zero.advanceSlice(1))
                throw new AssertionError("real zero-cap session completed before its first branch");
        }
        require(zeroBranch != null,"real zero-cap session exposes its first actual BranchSearch");
        require(intField(zeroSearch,"maximumVias") == 0 &&
                Array.getLength(field(zeroBranch,"holes")) == 0,
            "real zero-cap branch starts with no legal VIA capacity or prefix holes");
        require(!actualPathViaFits(zeroSearch,zeroBranch),
            "production pathViaFits rejects the first flip when maximumVias is zero");

        Method fresh = contract.getDeclaredMethod("freshSource",int.class);
        fresh.setAccessible(true);
        Object saturated = invoke(fresh,null,2);
        Object saturatedBranch = field(saturated,"branch");
        require(intField(saturated,"maximumVias") == 2 &&
                Array.getLength(field(saturated,"prefixIds")) == 2 &&
                Array.getLength(field(saturatedBranch,"holes")) == 2,
            "real saturated source branch starts at H=2 and M=2");
        require(!actualPathViaFits(field(saturated,"search"),saturatedBranch),
            "production pathViaFits rejects the next flip at a saturated two-hole prefix");
        return new GuardEvidence("PASS","PASS");
    }

    private static boolean actualPathViaFits(Object search,Object branch) throws Exception {
        Object queue = field(branch,"queue");
        Object values = field(queue,"values");
        if (!values.getClass().isArray() || Array.getLength(values) == 0)
            throw new IllegalStateException("Actual BranchSearch has no initial path node");
        Object node = Array.get(values,0);
        Method fits = search.getClass().getDeclaredMethod("pathViaFits",node.getClass());
        fits.setAccessible(true);
        return ((Boolean)invoke(fits,search,node)).booleanValue();
    }

    private static void releaseReplay(Object replay) throws Exception {
        if (replay == null) return;
        Object handle = field(replay,"handle");
        Method release = handle.getClass().getDeclaredMethod("release");
        release.setAccessible(true);
        invoke(release,handle);
    }

    private static void verifyObservationRows(int count,boolean requireCopy) {
        require((requireCopy ? count > 0 : count >= 0) && count <= 4096,
            "observation count matches its executed fixture and stays within the row cap");
        boolean actualSource = false;
        boolean actualBaseCopy = false;
        boolean actualRaisedCopy = false;
        int expectedBasePrice = PrefixViaRampSupport.effectivePrice(BASE,2,VIA_LIMIT);
        for (int i = 0; i < count; i++) {
            String row = PrefixViaRampSupport.observationAt(i);
            require(row.startsWith("{\"schema\":\"tsj-prefix-via-ramp-price-v1\",\"mode\":\""+
                    PrefixViaRampSupport.mode()+"\",\"ordinal\":"+(i+1)+","),
                "PRICE receipt row has the frozen schema, mode and consecutive ordinal");
            require(row.endsWith("}") && row.indexOf("\"netId\":\"") >= 0 &&
                    row.indexOf("\"startPadId\":\"") >= 0 &&
                    row.indexOf("\"rootPadId\":\"") >= 0 &&
                    row.indexOf("\"pass\":") >= 0,
                "PRICE receipt carries escaped actual branch identities and pass");
            if (row.indexOf("\"prefixHoles\":2,\"maximumVias\":48,\"baseViaCost\":60,") >= 0) {
                if (row.endsWith("\"copy\":false}"))
                    actualSource = row.endsWith("\"branchViaPrice\":"+expectedBasePrice+
                        ",\"copy\":false}");
                if (row.endsWith("\"copy\":true}"))
                    actualBaseCopy = row.endsWith("\"branchViaPrice\":"+expectedBasePrice+
                        ",\"copy\":true}");
            }
            if (row.indexOf("\"prefixHoles\":2,\"maximumVias\":48,\"baseViaCost\":120,") >= 0 &&
                    row.endsWith("\"branchViaPrice\":120,\"copy\":true}"))
                actualRaisedCopy = true;
        }
        if (requireCopy)
            require(actualSource && actualBaseCopy && actualRaisedCopy,
                "actual source, normal copy and explicit raised copy emit matching H=2/M=48 receipts");
    }
    private static Object invoke(Method method,Object target,Object... args) throws Exception {
        try {
            return method.invoke(target,args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error)cause;
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            throw new IllegalStateException("Reflected native canary failed",cause);
        }
    }

    private static int intField(Object target,String name) throws Exception {
        Object value=field(target,name);
        if (!(value instanceof Integer))
            throw new IllegalStateException("Expected an actual int field: "+name);
        return ((Integer)value).intValue();
    }

    private static Object field(Object target,String name) throws Exception {
        if (target == null) throw new IllegalStateException("Missing actual diagnostic result");
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String stringField(Object target,String name) throws Exception {
        Object value = field(target,name);
        if (!(value instanceof String))
            throw new IllegalStateException("Actual diagnostic field is not a String: " + name);
        return (String)value;
    }

    private static void expectInvalid(Operation operation,String label) {
        try {
            operation.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void equal(int expected,int actual,String label) {
        require(expected == actual,label+" expected="+expected+" actual="+actual);
    }

    private static void require(boolean condition,String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }

    private interface Operation { int run(); }

    private static final class SharedByteBudget {
        private final long maximum;
        private long used;
        private boolean overflowed;
        SharedByteBudget(long maximum) { this.maximum=maximum; }
        synchronized void reserve(int length) {
            if (length < 0 || (long)length > maximum-used) {
                overflowed=true;
                throw new CaptureLimitError();
            }
            used += length;
        }
        synchronized boolean overflowed() { return overflowed; }
    }

    private static final class CappedOutputStream extends java.io.OutputStream {
        private final java.io.OutputStream delegate;
        private final SharedByteBudget budget;
        CappedOutputStream(java.io.OutputStream delegate,SharedByteBudget budget) {
            this.delegate=delegate; this.budget=budget;
        }
        public void write(int value) throws java.io.IOException {
            budget.reserve(1);
            delegate.write(value);
        }
        public void write(byte[] values,int offset,int length) throws java.io.IOException {
            if (values == null) throw new NullPointerException("values");
            if (offset < 0 || length < 0 || offset > values.length-length)
                throw new IndexOutOfBoundsException();
            budget.reserve(length);
            delegate.write(values,offset,length);
        }
        public void flush() throws java.io.IOException { delegate.flush(); }
        public void close() throws java.io.IOException { flush(); }
    }

    private static final class CaptureLimitError extends Error {
        private static final long serialVersionUID = 1L;
        CaptureLimitError() { super("PREFIX_VIA_RAMP_CAPTURE_LIMIT"); }
    }
    private static final class PathStep {
        final int x,y,layer;
        PathStep(int x,int y,int layer) { this.x=x; this.y=y; this.layer=layer; }
    }

    private static final class PathFacts {
        int planarEdges,turns,transitions,holes,startLayer,endLayer,secondaryToPrimaryTransitions;
    }

    private static final class CopyEvidence {
        final int prefixHoles,maximumVias,baseViaCost,sourceBranchPrice;
        final int copiedBase60Price,copiedRaisedPrice;
        CopyEvidence(int prefixHoles,int maximumVias,int baseViaCost,int sourceBranchPrice,
                int copiedBase60Price,int copiedRaisedPrice) {
            this.prefixHoles=prefixHoles; this.maximumVias=maximumVias;
            this.baseViaCost=baseViaCost; this.sourceBranchPrice=sourceBranchPrice;
            this.copiedBase60Price=copiedBase60Price; this.copiedRaisedPrice=copiedRaisedPrice;
        }
    }

    private static final class GuardEvidence {
        final String zeroCap,saturatedCap;
        GuardEvidence(String zeroCap,String saturatedCap) {
            this.zeroCap=zeroCap; this.saturatedCap=saturatedCap;
        }
    }
}
