package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PcbLayoutDeveloperVerifier {
    static void verify(CirSim sim) {
        verifyFamily("LED_INDICATOR");
        verifyFamily("DIODE_PROTECTED_INDICATOR");
        verifyFamily("PARALLEL_DUAL_INDICATOR");
        verifyFamily("RC_DELAY");
        verifyFamily("NPN_LOW_SIDE_SWITCH");
        verifyFamily("NMOS_LOW_SIDE_SWITCH");
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        PcbBoardLayout regenerated = generate(current.getCircuitFamilyId(), current.getSeed())
            .getPcbLayout();
        require(current.getPcbLayout().geometryFingerprint().equals(regenerated.geometryFingerprint()),
            "installed PCB geometry does not match deterministic regeneration");
    }

    private static void verifyFamily(String familyId) {
        GeneratedBoardInstance seed0Board = generate(familyId, 0);
        GeneratedBoardInstance seed0RepeatBoard = generate(familyId, 0);
        GeneratedBoardInstance seed2Board = generate(familyId, 2);
        GeneratedBoardInstance seed2RepeatBoard = generate(familyId, 2);
        GeneratedBoardInstance seed3Board = generate(familyId, 3);
        GeneratedBoardInstance seed3RepeatBoard = generate(familyId, 3);
        PcbBoardLayout seed0 = seed0Board.getPcbLayout();
        PcbBoardLayout seed0Repeat = seed0RepeatBoard.getPcbLayout();
        PcbBoardLayout seed2 = seed2Board.getPcbLayout();
        PcbBoardLayout seed2Repeat = seed2RepeatBoard.getPcbLayout();
        PcbBoardLayout seed3 = seed3Board.getPcbLayout();
        PcbBoardLayout seed3Repeat = seed3RepeatBoard.getPcbLayout();
        boolean planned = "LED_INDICATOR".equals(familyId) || "DIODE_PROTECTED_INDICATOR".equals(familyId) ||
            "PARALLEL_DUAL_INDICATOR".equals(familyId);
        verifyRouteQuality(seed0, seed0Board.getBoard(), planned);
        verifyRouteQuality(seed2, seed2Board.getBoard(), planned);
        verifyRouteQuality(seed3, seed3Board.getBoard(), planned);
        verifyLabels(seed0, seed0Board.getBoard());
        verifyLabels(seed2, seed2Board.getBoard());
        verifyLabels(seed3, seed3Board.getBoard());
        if ("PARALLEL_DUAL_INDICATOR".equals(familyId)) {
            verifyMultiPadNets(seed0, seed0Board.getBoard());
            verifyMultiPadNets(seed2, seed2Board.getBoard());
            verifyMultiPadNets(seed3, seed3Board.getBoard());
        }
        if ("NPN_LOW_SIDE_SWITCH".equals(familyId))
            verifyNpnFootprint(seed0, seed0Board.getBoard(), seed0Board.getSeed());
        if ("NMOS_LOW_SIDE_SWITCH".equals(familyId)) {
            verifyNmosFootprint(seed0, seed0Board.getBoard(), seed0Board.getSeed());
            verifyNmosControlRouting(seed0, seed0Board.getBoard());
        }
        if ("LED_INDICATOR".equals(familyId))
            verifySeedThreeLedEndpointRegression(seed3);
        require(seed0.geometryFingerprint().equals(seed0Repeat.geometryFingerprint()),
            familyId + " seed 0 is not reproducible");
        require(seed2.geometryFingerprint().equals(seed2Repeat.geometryFingerprint()),
            familyId + " seed 2 is not reproducible");
        require(seed3.geometryFingerprint().equals(seed3Repeat.geometryFingerprint()),
            familyId + " seed 3 is not reproducible");
        require(meaningfulDifferences(seed0, seed2) >= 2,
            familyId + " seeds 0 and 2 lack meaningful geometry variation");
        require(meaningfulDifferences(seed0, seed3) >= 2,
            familyId + " seeds 0 and 3 lack meaningful geometry variation");
        require(meaningfulDifferences(seed2, seed3) >= 2,
            familyId + " seeds 2 and 3 lack meaningful geometry variation");
    }

    private static void verifyRouteQuality(PcbBoardLayout layout, TroubleshootBoard board, boolean planned) {
        layout.validateRouteQuality();
        verifyCopperClearance(layout);
        for (PcbTraceGeometry trace : layout.getTraces()) {
            require(layout.getTraceBendCount(trace) <= 16,
                "route has too many bends: " + trace.getNetId());
            require(layout.getTraceDetourRatio(trace) <= 3.0,
                "route detour is too large: " + trace.getNetId());
            verifyEndpointEscape(layout, trace, true);
            verifyEndpointEscape(layout, trace, false);
        }
        require(layout.getRouteQualityScore(board) >= 0,
            "route quality score is invalid");
        require(layout.getCompactnessMetric() >= .40,
            "PCB content is too sparse for its derived outline: " +
                layout.getCompactnessMetric());
        int expectedEdgeMargin = 26;
        // Named RC/switch factories retain their explicit 26-unit template margin.
        if (planned) for (PcbPlacementConstraints.Part part : board.getPlacementConstraints().getParts())
            expectedEdgeMargin = Math.max(expectedEdgeMargin, part.accessMargin + 30);
        require(layout.getLargestEdgeMargin() == expectedEdgeMargin,
            "PCB margin disagrees with declared P03 service access: " + layout.getLargestEdgeMargin());
        verifyRoutingCourtyards(layout, board);
        require(PcbTraceRules.MIN_CENTERLINE_CLEARANCE ==
                PcbTraceRules.TRACE_WIDTH + PcbTraceRules.MIN_VISIBLE_CLEARANCE,
            "trace clearance rule is inconsistent with rendered width");
    }

    private static void verifyCopperClearance(PcbBoardLayout layout) {
        require(PcbTraceRules.TRACE_WIDTH > 0 &&
                PcbTraceRules.MIN_VISIBLE_CLEARANCE > 0 &&
                PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS > 0,
            "trace clearance rule is not positive");
        layout.validateTraceClearance();
    }

    private static void verifyEndpointEscape(PcbBoardLayout layout, PcbTraceGeometry trace,
            boolean start) {
        String padId = start ? trace.getStartPadId() : trace.getEndPadId();
        if (padId == null) {
            int[] bx=trace.getXPoints(), by=trace.getYPoints(); int endpoint=start?0:bx.length-1;
            boolean contact=false;
            for (PcbTraceGeometry trunk : layout.getTraces()) {
                if (trunk == trace || trunk.getLayer() != trace.getLayer() ||
                        !trunk.getNetId().equals(trace.getNetId())) continue;
                int[] tx=trunk.getXPoints(), ty=trunk.getYPoints();
                for (int i=1;i<tx.length;i++) {
                    int x=bx[endpoint], y=by[endpoint];
                    if ((tx[i]==tx[i-1] && x==tx[i] && y>=Math.min(ty[i],ty[i-1]) && y<=Math.max(ty[i],ty[i-1])) ||
                        (ty[i]==ty[i-1] && y==ty[i] && x>=Math.min(tx[i],tx[i-1]) && x<=Math.max(tx[i],tx[i-1])))
                        contact=true;
                }
            }
            require(contact,"branch lacks real same-layer/same-net trunk contact"); return;
        }
        PcbPadPlacement pad = layout.getPad(padId);
        if (pad.getEscapeLength() == 0)
            return;
        int[] x = trace.getXPoints();
        int[] y = trace.getYPoints();
        int index = start ? 1 : x.length - 2;
        int dx = start ? x[index] - x[0] : x[x.length - 1] - x[index];
        int dy = start ? y[index] - y[0] : y[y.length - 1] - y[index];
        int signX = dx == 0 ? 0 : dx < 0 ? -1 : 1;
        int signY = dy == 0 ? 0 : dy < 0 ? -1 : 1;
        int expectedX = start ? pad.getEscapeDx() : -pad.getEscapeDx();
        int expectedY = start ? pad.getEscapeDy() : -pad.getEscapeDy();
        require(signX == expectedX && signY == expectedY,
            "trace does not use pad escape direction: " + trace.getNetId() + " / " + padId);
    }

    private static void verifyLabels(PcbBoardLayout layout, TroubleshootBoard board) {
        require(layout.getSilkscreenLabel("board-title") != null,
            "board title label is missing");
        for (String componentId : board.getComponentIds())
            require(layout.getSilkscreenLabel("component:" + componentId) != null,
                "component label is missing: " + componentId);
        for (String powerInputId : board.getPowerInputIds()) {
            ExternalBoardPowerInput input = board.getPowerInput(powerInputId);
            verifyPowerLabel(layout, board, input.getPositivePadId());
            verifyPowerLabel(layout, board, input.getReturnPadId());
        }
    }

    private static void verifyPowerLabel(PcbBoardLayout layout, TroubleshootBoard board,
            String padId) {
        BoardPad pad = board.getPad(padId);
        require(pad != null, "power-input label references an unknown logical pad: " + padId);
        PcbSilkscreenLabel label = layout.getSilkscreenLabel("net:" + padId);
        require(label != null && padId.equals(label.getTargetPadId()),
            "power-input label is not tied to its logical pad: " + padId);
    }

    private static void verifySeedThreeLedEndpointRegression(PcbBoardLayout layout) {
        PcbComponentPlacement led = layout.getComponent("LED1");
        PcbPadPlacement anode = layout.getPad("LED1.A");
        PcbPadPlacement cathode = layout.getPad("LED1.K");
        require(anode.getEscapeDx() == 0 && anode.getEscapeDy() == 1 &&
                cathode.getEscapeDx() == 0 && cathode.getEscapeDy() == 1,
            "seed 3 LED pads do not use downward escape corridors");
        require(!containsInclusive(led.getKeepOut(), cathode.getX(),
                cathode.getY() + cathode.getEscapeLength()),
            "seed 3 LED cathode escape corridor does not leave its keep-out");
        PcbTraceGeometry groundTrace = null;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if ("GND".equals(trace.getNetId()) && ("LED1.K".equals(trace.getStartPadId()) ||
                    "LED1.K".equals(trace.getEndPadId()))) { groundTrace=trace; break; }
        }
        require(groundTrace != null, "seed 3 cathode copper endpoint is missing");
        verifyEndpointEscape(layout, groundTrace, "LED1.K".equals(groundTrace.getStartPadId()));
        PcbConductorGraph.Snapshot copper=layout.captureConductorGraph(
            TroubleshootBoardFixtures.createLedIndicatorBoard()).pristine();
        require(copper.padsConnected("J1.2","LED1.K"),"seed 3 ground-to-cathode copper is disconnected");
    }

    private static void verifyRoutingCourtyards(PcbBoardLayout layout, TroubleshootBoard board) {
        for (String componentId : board.getComponentIds()) {
            PcbComponentPlacement component = layout.getComponent(componentId);
            Rectangle body = component.getKeepOut();
            Rectangle courtyard = component.getRoutingCourtyard();
            require(courtyard.intersects(body), "routing courtyard misses component body: " +
                componentId);
            if ("RESISTOR".equals(board.getComponent(componentId).getType()) ||
                    "DIODE".equals(board.getComponent(componentId).getType()))
                require(courtyard.width > body.width || courtyard.height > body.height,
                    "component courtyard regression lacks lead-span margin: " + componentId);
            for (PcbTraceGeometry trace : layout.getTraces()) {
                int[] x = trace.getXPoints();
                int[] y = trace.getYPoints();
                for (int index = 1; index < x.length; index++) {
                    if (!segmentIntersects(courtyard, x[index - 1], y[index - 1], x[index],
                            y[index]))
                        continue;
                    boolean endpoint = traceTouchesComponentEndpoint(layout, board, component,
                        trace, x[index - 1], y[index - 1], x[index], y[index]);
                    require(endpoint, "trace passes beneath component routing courtyard: " +
                        componentId + " / " + trace.getNetId());
                }
            }
        }
    }

    private static boolean traceTouchesComponentEndpoint(PcbBoardLayout layout,
            TroubleshootBoard board, PcbComponentPlacement component, PcbTraceGeometry trace,
            int x1, int y1, int x2, int y2) {
        String[] endpointIds = { trace.getStartPadId(), trace.getEndPadId() };
        BoardPad firstPad=board.getPad(trace.getStartPadId()), lastPad=board.getPad(trace.getEndPadId());
        boolean sameEndpointComponent = firstPad != null && lastPad != null &&
            firstPad.getComponentId().equals(lastPad.getComponentId());
        for (String endpointId : endpointIds) {
            BoardPad boardPad = board.getPad(endpointId);
            if (boardPad == null || !component.getComponentId().equals(boardPad.getComponentId()))
                continue;
            PcbPadPlacement pad = layout.getPad(endpointId);
            if (sameEndpointComponent && !((x1 == pad.getX() && y1 == pad.getY()) ||
                    (x2 == pad.getX() && y2 == pad.getY())))
                continue;
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            int top = Math.min(y1, y2);
            int bottom = Math.max(y1, y2);
            if (y1 == y2)
                return pad.isInEscapeCorridor(Math.max(left, component.getRoutingCourtyard().x), y1) &&
                    pad.isInEscapeCorridor(Math.min(right,
                        component.getRoutingCourtyard().x + component.getRoutingCourtyard().width), y1);
            if (x1 == x2)
                return pad.isInEscapeCorridor(x1, Math.max(top, component.getRoutingCourtyard().y)) &&
                    pad.isInEscapeCorridor(x1, Math.min(bottom,
                        component.getRoutingCourtyard().y + component.getRoutingCourtyard().height));
        }
        return false;
    }

    private static boolean segmentIntersects(Rectangle rectangle, int x1, int y1,
            int x2, int y2) {
        return Math.max(Math.min(x1, x2), rectangle.x) <=
                Math.min(Math.max(x1, x2), rectangle.x + rectangle.width) &&
            Math.max(Math.min(y1, y2), rectangle.y) <=
                Math.min(Math.max(y1, y2), rectangle.y + rectangle.height);
    }

    private static void verifyMultiPadNets(PcbBoardLayout layout, TroubleshootBoard board) {
        verifyMultiPadNet(layout, board, "VIN", new String[] { "J1.1", "R1.1", "R2.1" });
        verifyMultiPadNet(layout, board, "GND", new String[] { "J1.2", "LED1.K", "LED2.K" });
    }

    private static void verifyNpnFootprint(PcbBoardLayout layout, TroubleshootBoard board,
            long seed) {
        require(board.getPad("Q1.B") != null && board.getPad("Q1.C") != null &&
            board.getPad("Q1.E") != null &&
            "BASE".equals(board.getPad("Q1.B").getNetId()) &&
            "COLLECTOR".equals(board.getPad("Q1.C").getNetId()) &&
            "GND".equals(board.getPad("Q1.E").getNetId()),
            "NPN layout verifier lost stable B/C/E pads");
        PcbComponentPlacement actualPlacement = layout.getComponent("Q1");
        PcbFootprint expected = StandardPcbFootprintProviders.createRegistry().create(
            board.getComponent("Q1"), actualPlacement.getX(), actualPlacement.getY(),
            new java.util.Random(seed), layout.getBoardOutline());
        require(samePlacement(actualPlacement, expected.getPlacement()),
            "NPN Q1 placement/body/courtyard diverges from TO-92 provider");
        Vector<PcbPadPlacement> expectedPads = expected.getPads();
        String[] terminalIds = { "Q1.B", "Q1.C", "Q1.E" };
        require(expectedPads.size() == terminalIds.length,
            "TO-92 provider did not expose three stable terminals");
        for (int index = 0; index < terminalIds.length; index++) {
            PcbPadPlacement expectedPad = expectedPads.get(index);
            PcbPadPlacement actualPad = layout.getPad(terminalIds[index]);
            require(expectedPad.getPadId().equals(terminalIds[index]) &&
                samePad(actualPad, expectedPad),
                "NPN Q1 " + terminalIds[index] +
                " diverges from TO-92 provider geometry");
        }
    }

    private static void verifyNmosFootprint(PcbBoardLayout layout, TroubleshootBoard board,
            long seed) {
        require(board.getPad("Q1.G") != null && board.getPad("Q1.D") != null &&
            board.getPad("Q1.S") != null &&
            "CONTROL_INPUT".equals(board.getPad("Q1.G").getNetId()) &&
            "DRAIN".equals(board.getPad("Q1.D").getNetId()) &&
            "GND".equals(board.getPad("Q1.S").getNetId()),
            "NMOS layout verifier lost stable G/D/S pads");
        PcbComponentPlacement actualPlacement = layout.getComponent("Q1");
        PcbFootprint expected = StandardPcbFootprintProviders.createRegistry().create(
            board.getComponent("Q1"), actualPlacement.getX(), actualPlacement.getY(),
            new java.util.Random(seed), layout.getBoardOutline());
        require(samePlacement(actualPlacement, expected.getPlacement()),
            "NMOS Q1 placement/body/courtyard diverges from registered provider");
        String[] terminalIds = { "Q1.G", "Q1.D", "Q1.S" };
        Vector<PcbPadPlacement> expectedPads = expected.getPads();
        require(expectedPads.size() == terminalIds.length,
            "NMOS provider did not expose three stable terminals");
        for (int index = 0; index < terminalIds.length; index++)
            require(expectedPads.get(index).getPadId().equals(terminalIds[index]) &&
                samePad(layout.getPad(terminalIds[index]), expectedPads.get(index)),
                "NMOS Q1 " + terminalIds[index] + " diverges from registered provider geometry");
    }

    private static void verifyNmosControlRouting(PcbBoardLayout layout, TroubleshootBoard board) {
        require(board.getNet("GATE_DRIVE") == null && board.getNet("GATE") == null &&
            board.getComponent("TP1") == null && board.getComponent("TP2") == null &&
            layout.getComponent("TP1") == null && layout.getComponent("TP2") == null &&
            layout.getPad("TP1.1") == null && layout.getPad("TP2.1") == null,
            "NMOS layout retained obsolete gate/control pseudo-geometry");
        require("CONTROL_INPUT".equals(board.getPad("J2.1").getNetId()) &&
            "CONTROL_INPUT".equals(board.getPad("RPD.1").getNetId()) &&
            "CONTROL_INPUT".equals(board.getPad("Q1.G").getNetId()),
            "NMOS layout control pads do not share CONTROL_INPUT");
        int count = 0;
        boolean rpd = false;
        boolean gate = false;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if (!"CONTROL_INPUT".equals(trace.getNetId()))
                continue;
            count++;
            require("J2.1".equals(trace.getStartPadId()),
                "NMOS control route does not start at J2.1");
            rpd |= "RPD.1".equals(trace.getEndPadId());
            gate |= "Q1.G".equals(trace.getEndPadId());
        }
        require(count == 2 && rpd && gate,
            "NMOS visible copper does not join J2.1 to the gate network");
    }

    private static boolean samePlacement(PcbComponentPlacement first,
            PcbComponentPlacement second) {
        return first.getComponentId().equals(second.getComponentId()) &&
            first.getX() == second.getX() && first.getY() == second.getY() &&
            first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight() &&
            first.getKeepOut().equals(second.getKeepOut()) &&
            first.getRoutingCourtyard().equals(second.getRoutingCourtyard());
    }

    private static boolean samePad(PcbPadPlacement first, PcbPadPlacement second) {
        return first.getPadId().equals(second.getPadId()) && first.getX() == second.getX() &&
            first.getY() == second.getY() && first.getEscapeDx() == second.getEscapeDx() &&
            first.getEscapeDy() == second.getEscapeDy() &&
            first.getEscapeLength() == second.getEscapeLength();
    }

    private static void verifyMultiPadNet(PcbBoardLayout layout, TroubleshootBoard board,
            String netId, String[] padIds) {
        int traceCount=0;
        for (PcbTraceGeometry trace : layout.getTraces()) if (netId.equals(trace.getNetId())) traceCount++;
        require(traceCount == padIds.length-1,"multi-pad net does not form its minimal tree");
        for (String id : padIds)
            require(board.getPad(id) != null && netId.equals(board.getPad(id).getNetId()),
                "multi-pad net is missing its stable pad: " + id);
        ArchitectureDeveloperVerifier.verifyRasterConnectivity(layout,board);
    }

    private static boolean containsInclusive(Rectangle rectangle, int x, int y) {
        return x >= rectangle.x && y >= rectangle.y &&
            x <= rectangle.x + rectangle.width && y <= rectangle.y + rectangle.height;
    }

    private static int meaningfulDifferences(PcbBoardLayout first, PcbBoardLayout second) {
        int differences = 0;
        if (!first.getBoardOutline().equals(second.getBoardOutline()))
            differences++;
        if (!first.componentGeometryFingerprint().equals(second.componentGeometryFingerprint()))
            differences++;
        if (!first.traceGeometryFingerprint().equals(second.traceGeometryFingerprint()))
            differences++;
        return differences;
    }

    private static GeneratedBoardInstance generate(String familyId, long seed) {
        if ("LED_INDICATOR".equals(familyId))
            return new LedIndicatorGenerator().generate(seed);
        if ("DIODE_PROTECTED_INDICATOR".equals(familyId))
            return new DiodeProtectedIndicatorGenerator().generate(seed);
        if ("PARALLEL_DUAL_INDICATOR".equals(familyId))
            return new ParallelDualIndicatorGenerator().generate(seed);
        if ("RC_DELAY".equals(familyId))
            return new RcDelayGenerator().generate(seed);
        if ("NPN_LOW_SIDE_SWITCH".equals(familyId))
            return new NpnLowSideSwitchGenerator().generate(seed);
        if ("NMOS_LOW_SIDE_SWITCH".equals(familyId))
            return new NmosLowSideSwitchGenerator().generate(seed);
        throw new IllegalArgumentException("Unsupported PCB verifier family: " + familyId);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
