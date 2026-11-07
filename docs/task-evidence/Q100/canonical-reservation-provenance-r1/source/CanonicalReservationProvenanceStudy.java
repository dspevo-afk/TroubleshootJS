package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Passive, source-bound reservation operand capture around the genuine P8 run. */
public final class CanonicalReservationProvenanceStudy {
    private static final String SCHEMA = "tsj-q100-reservation-operands-v1";
    private static final String INDEX_SCHEMA = "tsj-q100-reservation-provenance-index-v1";
    private static final String PREFLIGHT_SCHEMA = "tsj-q100-reservation-provenance-preflight-v1";
    private static final String P8_SCHEMA = "tsj-canonical-geometric-prefix-complete-v1";
    private static final String P8_INDEX_SCHEMA = "tsj-canonical-geometric-prefix-index-v1";
    private static final String PREFIX_SHA =
        "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb";
    private static final String GRAPH_SHA =
        "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a";
    private static final String SENTINEL = "FIRST_RESERVATION_OPERANDS_CALLBACK";
    private static final long MAX_OPERATION_NANOS = 90000000000L;
    private static final long MAX_GROUP_NANOS = 5000000000L;
    private static final long MAX_CLEANUP_NANOS = 120000000000L;
    private static final long MAX_OUTPUT_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_LINE_BYTES = 65536;
    private static final int MAX_INDEX_BYTES = 65536;
    private static final int CHUNK_BYTES = 4096;
    private static final int MAX_UNITS = 32768;
    private static final int PAD_COUNT = 246;
    private static final int CAPTURE_UNITS = 6208;
    private static final int OBSERVE_SERIAL_UNITS = 6454;
    private static final int[] SELECTED = {0,1,2,3,4,5,6,7,8,9,11,15,17,20};
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static volatile Session activeSession;

    private CanonicalReservationProvenanceStudy() { }

    public static void main(String[] args) {
        long wrapperStarted = System.nanoTime();
        Arguments arguments;
        try {
            arguments = preflight(args);
        } catch (PreflightFailure rejection) {
            System.err.print("P9_RESERVATION_PROVENANCE_PREFLIGHT_REJECT " + rejection.json() + "\n");
            System.err.flush();
            System.exit(1);
            return;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
            return;
        }
        try {
            run(arguments, wrapperStarted);
        } catch (Throwable error) {
            if (activeSession != null) activeSession.dropAll();
            activeSession = null;
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(Arguments a, long wrapperStarted) throws Exception {
        PrintStream originalOut = System.out, originalErr = System.err;
        final Session session = new Session(a, wrapperStarted);
        TeeCaptureOutputStream outCapture = new TeeCaptureOutputStream(originalOut, MAX_LINE_BYTES);
        CountingTeeOutputStream errCapture = new CountingTeeOutputStream(originalErr);
        PrintStream teeOut = new PrintStream(outCapture, true, "UTF-8");
        PrintStream teeErr = new PrintStream(errCapture, true, "UTF-8");
        String[] p8Args = new String[] {
            a.p8Mode, a.runId, a.p9PinsSha, a.p8WireSha, a.p9SourceSha, a.p8Directory.toString()
        };
        long delegateStart = 0L;
        long delegateReturn = 0L;
        long streamRestoreCleanupNanos = 0L;
        activeSession = "NULL".equals(a.mode) ? null : session;
        try {
            System.setOut(teeOut);
            System.setErr(teeErr);
            requireGroup(elapsed(wrapperStarted, System.nanoTime()), "P9_PREFLIGHT");
            delegateStart = System.nanoTime();
            CanonicalGeometricPrefixStudy.main(p8Args);
            delegateReturn = System.nanoTime();
            teeOut.flush(); teeErr.flush();
            if (teeOut.checkError() || teeErr.checkError())
                throw new IOException("P8 stream capture or forwarding failed");
        } finally {
            long streamRestoreStarted = System.nanoTime();
            System.setOut(originalOut);
            System.setErr(originalErr);
            streamRestoreCleanupNanos = elapsed(streamRestoreStarted, System.nanoTime());
        }
        long preflightNanos = elapsed(wrapperStarted, delegateStart);
        requireGroup(preflightNanos, "P9_PREFLIGHT");
        long delegateStartOffset = preflightNanos;
        long delegateReturnOffset = elapsed(wrapperStarted, delegateReturn);
        session.exportCleanupNanos = streamRestoreCleanupNanos;
        long registrationCleanupStarted = System.nanoTime();
        if (activeSession == session) activeSession = null;
        session.exportCleanupNanos = saturatedAdd(session.exportCleanupNanos,
            elapsed(registrationCleanupStarted, System.nanoTime()));
        session.afterDelegateReturn();
        byte[] p8Stdout = outCapture.copyBytes();
        session.outboundBytes = outCapture.bytesWritten();
        long stdoutCleanupStarted = System.nanoTime();
        outCapture.clear();
        session.exportCleanupNanos = saturatedAdd(session.exportCleanupNanos,
            elapsed(stdoutCleanupStarted, System.nanoTime()));
        long p8StderrBytes = errCapture.bytesWritten();
        long finalP6CleanupNanos=0L;
        boolean completeIndexWritten=false;
        boolean normalCleanupDone=false;
        try {
            long exportStart = delegateReturn;
            P8Marker marker = parseP8Marker(p8Stdout, a, session.metadataMeter);
            ExportData export = readP8Evidence(a, marker, session);
            finalP6CleanupNanos=export.p6CleanupNanos;
            byte[] sidecar = serializeSidecar(a, session, export, marker);
            if (sidecar.length < 1 || sidecar.length > MAX_OUTPUT_BYTES)
                throw new IOException("P9 sidecar exceeds the inclusive native byte bound");
            String sidecarSha = sha256Pass(sidecar, session.byteMeter, "P9 sidecar prewrite hash");
            Path sidecarPath = a.p9Directory.resolve("reservation-operands.jsonl");
            writeNewChunked(sidecarPath, sidecar, session.byteMeter);
            FileEvidence sidecarEvidence = readBackHash(sidecarPath, sidecar.length,
                sidecarSha, session.byteMeter);
            long exportCleanupStart = System.nanoTime();
            session.dropDtoAndVerify();
            zero(sidecar); sidecar = null;
            zero(p8Stdout); p8Stdout = null;
            session.exportCleanupNanos = saturatedAdd(session.exportCleanupNanos,
                elapsed(exportCleanupStart, System.nanoTime()));
            long sidecarEnd = System.nanoTime();
            long exportNanos = elapsed(exportStart, sidecarEnd);
            requireGroup(exportNanos, "P9_SIDECAR_EXPORT");
            int serializationUnits = session.serializationMeter.finishExpected(
                "NULL".equals(a.mode) ? 20 : "TAP_THROW".equals(a.mode) ? 30 : OBSERVE_SERIAL_UNITS);
            int captureUnits = session.captureMeter.finishExpected(
                "NULL".equals(a.mode) ? 0 : CAPTURE_UNITS);
            int releaseUnits = session.releaseMeter.finishExpected(
                "NULL".equals(a.mode) ? 0 : CAPTURE_UNITS);
            int metadataUnits = session.metadataMeter.finish();
            int byteUnits = session.byteMeter.units();
            if(export.p8Units<0||export.p8Units>MAX_UNITS)
                throw new IOException("P8 observation units exceed their bounded source index");
            int p8Units=(int)export.p8Units;
            int p9IndexUnits = 4;
            int p9ExportUnits = serializationUnits + byteUnits + p9IndexUnits;
            int p9Units = captureUnits + releaseUnits + metadataUnits + p9ExportUnits;
            int totalUnits = p8Units + p9Units;
            if (p8Units < 0 || totalUnits > MAX_UNITS)
                throw new IOException("P8+P9 observation units exceed the frozen 32768 bound");
            int p9Groups = 3;
            int nativeWrapperGroups = 3;
            int totalGroups = export.actualReceiptGroups + nativeWrapperGroups + p9Groups;
            if (totalGroups > 640)
                throw new IOException("Actual P8 receipt plus wrapper groups exceed 640");
            long p9DelegateCleanup = session.p9DelegateCleanupNanos;
            long preIndexAggregate = marker.aggregateBytes + outCapture.bytesWritten() + p8StderrBytes +
                sidecarEvidence.bytes;
            Session.requireAggregate(preIndexAggregate);
            long creditCutoffOffset = elapsed(wrapperStarted, sidecarEnd);
            long p9CleanupNanos = p9DelegateCleanup + session.preflightCleanupNanos +
                session.exportCleanupNanos;
            long creditedWork = marker.workNanos - p9DelegateCleanup +
                (preflightNanos - session.preflightCleanupNanos) +
                (exportNanos - session.exportCleanupNanos);
            if("TAP_THROW".equals(a.mode)) creditedWork=0L;
            if (creditedWork < 0)
                throw new IOException("P9 credited work underflowed its measured segments");
            long maximumBeforeIndex = Math.max(preflightNanos, exportNanos);
            long measuredAtCutoff = creditCutoffOffset - export.p6CleanupNanos;
            if (measuredAtCutoff < 0 || p9CleanupNanos > MAX_CLEANUP_NANOS)
                throw new IOException("P9 cutoff/cleanup accounting is outside its bound");

            if (!session.referencesDropped())
                throw new IOException("P9 retained a Router/delegate/DTO reference before index construction");
            long indexStart = System.nanoTime();
            TreeMap<String,Object> index = buildIndex(a, marker, export, session,
                sidecarEvidence, preflightNanos, delegateStartOffset, delegateReturnOffset,
                p9DelegateCleanup, p9CleanupNanos, creditCutoffOffset, creditedWork,
                p8Units, captureUnits, releaseUnits, metadataUnits, serializationUnits,
                byteUnits, p9ExportUnits, p9Units, totalUnits, nativeWrapperGroups,
                p9Groups, totalGroups, maximumBeforeIndex, measuredAtCutoff,
                preIndexAggregate, p8StderrBytes);
            byte[] indexBody = JsonWriter.bytes(index);
            byte[] indexBytes = appendLf(indexBody);
            zero(indexBody); indexBody = null;
            if (indexBytes.length < 1 || indexBytes.length > MAX_INDEX_BYTES)
                throw new IOException("P9 index exceeds 64 KiB");
            long indexSerializeNanos = elapsed(indexStart, System.nanoTime());
            requireGroup(indexSerializeNanos, "P9 index serialization");
            long indexHashStart = System.nanoTime();
            String indexSha = sha256(indexBytes);
            long indexHashNanos = elapsed(indexHashStart, System.nanoTime());
            requireGroup(indexHashNanos, "P9 index hash");
            Path indexPath = a.p9Directory.resolve("reservation-index.json");
            long indexWriteStart = System.nanoTime();
            writeNewIndex(indexPath, indexBytes);
            requireGroup(elapsed(indexWriteStart,System.nanoTime()), "P9 index write");
            long indexReadStart = System.nanoTime();
            byte[] indexReadback = readBounded(indexPath, MAX_INDEX_BYTES);
            if (!Arrays.equals(indexBytes, indexReadback) || !indexSha.equals(sha256(indexReadback)))
                throw new IOException("P9 index changed after CREATE_NEW write");
            requireGroup(elapsed(indexReadStart,System.nanoTime()), "P9 index readback/hash");
            long indexDone = System.nanoTime();
            long indexGroupNanos = elapsed(indexStart, indexDone);
            requireGroup(indexGroupNanos, "P9_INDEX_FINALIZE");
            long completeAggregate = preIndexAggregate + indexBytes.length;
            Session.requireAggregate(completeAggregate);
            completeIndexWritten=true;
            zero(indexReadback); zero(indexBytes); indexReadback = null; indexBytes = null;
            session.dropAll();
            export.dropAll();
            if (activeSession != null)
                throw new IOException("P9 registration was not cleared after the P8 delegate");
            teeOut.flush(); teeErr.flush();
            if(teeOut.checkError()||teeErr.checkError())
                throw new IOException("P9 final output flush failed");
            normalCleanupDone=true;
        } finally {
            if (activeSession == session) activeSession = null;
            if(!normalCleanupDone) session.dropAll();
            zero(p8Stdout);
        }
        if(completeIndexWritten) {
            long fullFinal=elapsed(wrapperStarted,System.nanoTime());
            if(fullFinal-finalP6CleanupNanos>MAX_OPERATION_NANOS)
                throw new IOException("P9 final post-I/O work guard exceeded 90 seconds");
        }
    }

    static PcbNetRouter.Router.FixedGraphReasonObserver decorateForStudy(
            PcbNetRouter.Router router, PcbNetRouter.Router.FixedGraphReasonObserver observer) {
        Session current = activeSession;
        if (current == null) return observer;
        if (router == null || observer == null)
            throw new IllegalArgumentException("P9 observer decorator requires actual compared query roles");
        return current.decorate(router, observer);
    }

    static void detachForStudy(PcbNetRouter.Router.FixedGraphReasonObserver observer) {
        if (observer instanceof Decorator) ((Decorator)observer).detach();
    }

    static String[] runCanaries() {
        return Session.runLiteralCanaries();
    }

    /** Detached value-only DTO. Constructor arguments are borrowed and never retained. */
    static final class RouterOperands {
        Identity identity;
        int layer, outlineX, outlineY, outlineWidth, outlineHeight;
        int minX, minY, gridWidth, gridHeight;
        boolean checkProfiledPadTouches, emptyComponentFace;
        int selectedLayer;
        String activeNetId;
        int activeTraceWidth, escapeOutletLand;
        String escapeOutletLandOrigin;
        PadRow[] pads;
        String targetSlotValue, targetReservationState, targetSite;
        private boolean cleared;

        private RouterOperands() { }

        RouterOperands(PcbCopperLayer layerValue, PcbBoardLayout layoutValue,
                TroubleshootBoard boardValue, Rectangle outlineValue, int minXValue,
                int minYValue, int gridWidthValue, int gridHeightValue,
                boolean profileTouchesValue, boolean emptyFaceValue, String activeNet,
                int activeWidth, PcbPadPlacement[] sourcePads, String[] sourcePadNets,
                int[] sourcePadWidths, Rectangle[] sourcePadBounds, Rectangle[] sourceCorridors) {
            Session owner = activeSession;
            if (owner == null || owner.pendingIdentity == null || owner.pendingReason == null)
                throw new IllegalStateException("P9 operand snapshot has no actual target callback");
            owner.beginCapture();
            if (layerValue == null || layoutValue == null || boardValue == null || outlineValue == null ||
                    sourcePads == null || sourcePadNets == null || sourcePadWidths == null ||
                    sourcePadBounds == null || sourceCorridors == null ||
                    sourcePads.length != PAD_COUNT || sourcePadNets.length != PAD_COUNT ||
                    sourcePadWidths.length != PAD_COUNT || sourcePadBounds.length != PAD_COUNT ||
                    sourceCorridors.length != PAD_COUNT)
                throw new IllegalStateException("P9 source Router pad arrays differ from the fixed 246-pad census");
            identity = owner.pendingIdentity;
            owner.captureHeaderExtra(17);
            layer = layerValue.ordinal();
            selectedLayer = layerValue.ordinal();
            outlineX = outlineValue.x; outlineY = outlineValue.y;
            outlineWidth = outlineValue.width; outlineHeight = outlineValue.height;
            minX = minXValue; minY = minYValue; gridWidth = gridWidthValue; gridHeight = gridHeightValue;
            checkProfiledPadTouches = profileTouchesValue; emptyComponentFace = emptyFaceValue;
            activeNetId = activeNet;
            activeTraceWidth = activeWidth;
            escapeOutletLand = PcbTwoLayerRules.VIA_LAND;
            escapeOutletLandOrigin = "PINNED_FULLER_SOURCE";
            pads = new PadRow[PAD_COUNT];
            for (int index = 0; index < PAD_COUNT; index++) {
                PcbPadPlacement pad = sourcePads[index];
                String netId = sourcePadNets[index];
                Rectangle bounds = sourcePadBounds[index], corridor = sourceCorridors[index];
                if (pad == null || netId == null || bounds == null || corridor == null)
                    throw new IllegalStateException("P9 source Router has an incomplete actual pad row");
                BoardPad boardPad = boardValue.getPad(pad.getPadId());
                if (boardPad == null || !netId.equals(boardPad.getNetId()))
                    throw new IllegalStateException("P9 Router pad net differs from its actual BoardPad");
                String componentId = boardPad.getComponentId();
                PcbComponentPlacement component = layoutValue.getComponent(componentId);
                BoardNet net = boardValue.getNet(netId);
                if (component == null || net == null || net.getRoutingRole() == null)
                    throw new IllegalStateException("P9 Router pad lost its typed component/net owner");
                Rectangle courtyard = component.getRoutingCourtyard();
                if (courtyard == null) throw new IllegalStateException("P9 owner courtyard is missing");
                pads[index] = new PadRow(index, pad.getPadId(), componentId, netId,
                    net.getRoutingRole().name(), pad.getX(), pad.getY(),
                    pad.getMountingSide().name(), pad.getAttachment().name(),
                    pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength(),
                    bounds.x, bounds.y, bounds.width, bounds.height,
                    courtyard.x, courtyard.y, courtyard.width, courtyard.height,
                    corridor.x, corridor.y, corridor.width, corridor.height, sourcePadWidths[index]);
                owner.capturePad(25);
            }
            targetSlotValue = owner.pendingReason.reservationValue;
            targetReservationState = owner.pendingReason.reservationState;
            targetSite = owner.pendingReason.site;
            owner.captureFooter(11);
            owner.finishCapture();
        }

        void clear(UnitMeter meter) {
            if (cleared) return;
            meter.begin();
            if (pads != null) {
                for (int index = 0; index < pads.length; index++) {
                    PadRow row = pads[index];
                    if (row != null) row.clear(meter);
                    pads[index] = null;
                }
            }
            pads = null;
            if (identity != null) identity.clear(meter);
            identity = null;
            selectedLayer = 0; activeNetId = null; escapeOutletLandOrigin = null;
            targetSlotValue = null; targetReservationState = null; targetSite = null;
            meter.add(13 + 17 + 11);
            meter.finishExpected(CAPTURE_UNITS);
            cleared = true;
        }

        static RouterOperands syntheticCanary(Identity id,
                PcbNetRouter.Router.FixedGraphReason reason) {
            RouterOperands value = new RouterOperands();
            value.identity=id; value.layer=1; value.selectedLayer=1;
            value.outlineX=0; value.outlineY=0; value.outlineWidth=100; value.outlineHeight=100;
            value.minX=10; value.minY=10; value.gridWidth=9; value.gridHeight=9;
            value.checkProfiledPadTouches=true; value.emptyComponentFace=true;
            value.activeNetId="A_COIL_LOW"; value.activeTraceWidth=1;
            value.escapeOutletLand=6; value.escapeOutletLandOrigin="PINNED_FULLER_SOURCE";
            value.pads=new PadRow[PAD_COUNT];
            for(int i=0;i<value.pads.length;i++) value.pads[i]=PadRow.synthetic(i);
            value.targetSlotValue=reason.reservationValue;
            value.targetReservationState=reason.reservationState;
            value.targetSite=reason.site;
            return value;
        }
    }

    private static final class Decorator implements PcbNetRouter.Router.FixedGraphReasonObserver {
        private PcbNetRouter.Router router;
        private PcbNetRouter.Router.FixedGraphReasonObserver delegate;
        private Session owner;
        private final int attachmentOrdinal;
        private boolean detached;

        Decorator(Session session, PcbNetRouter.Router query,
                PcbNetRouter.Router.FixedGraphReasonObserver original, int ordinal) {
            owner = session; router = query; delegate = original; attachmentOrdinal = ordinal;
        }

        public void onRejected(PcbNetRouter.Router.FixedGraphReason reason) {
            Session current = owner;
            PcbNetRouter.Router query = router;
            PcbNetRouter.Router.FixedGraphReasonObserver original = delegate;
            if (detached || current == null || original == null)
                throw new IllegalStateException("P9 observer callback followed decorator detach");
            current.onEvent(this, attachmentOrdinal, query, original, reason);
        }

        void detach() {
            if (detached) throw new IllegalStateException("P9 decorator detach was duplicated");
            Session current = owner;
            long started = System.nanoTime();
            try {
                if (current == null) throw new IllegalStateException("P9 decorator lost its owner before detach");
                current.noteDetach(attachmentOrdinal);
            } finally {
                router = null; delegate = null; owner = null; detached = true;
                long duration = elapsed(started, System.nanoTime());
                if (current != null) {
                    current.noteDecoratorReferencesDropped(referencesDropped());
                    current.recordDelegateCleanup(duration);
                }
            }
        }

        boolean referencesDropped() {
            return detached && router == null && delegate == null && owner == null;
        }
    }

    private static final class Session {
        final Arguments arguments;
        final UnitMeter captureMeter = new UnitMeter("P9 capture");
        final UnitMeter releaseMeter = new UnitMeter("P9 release");
        final UnitMeter serializationMeter = new UnitMeter("P9 serialization");
        final UnitMeter metadataMeter = new UnitMeter("P9 metadata");
        final ByteMeter byteMeter = new ByteMeter();
        final boolean[] attached = new boolean[SELECTED.length];
        final boolean[] detached = new boolean[SELECTED.length];
        final boolean[] forwarded = new boolean[SELECTED.length];
        int attachmentCount, detachCount, openDecorators, forwardedFineEvents;
        int targetAttachmentOrdinal = -1, targetParentOrdinal = -1;
        int targetEvents, targetForwardedEvents, padRowsBuilt, padRowsRetained;
        boolean registrationArmed, sentinelObserved, allDecoratorReferencesDropped = true;
        boolean captureStarted, captureFinished, dtoReferencesDropped;
        long p9DelegateCleanupNanos, preflightCleanupNanos, exportCleanupNanos;
        long outboundBytes;
        Identity pendingIdentity;
        PcbNetRouter.Router.FixedGraphReason pendingReason;
        RouterOperands operands;

        Session(Arguments value, long started) {
            arguments = value;
            registrationArmed = !"NULL".equals(value.mode);
        }

        PcbNetRouter.Router.FixedGraphReasonObserver decorate(PcbNetRouter.Router query,
                PcbNetRouter.Router.FixedGraphReasonObserver original) {
            if (!registrationArmed || attachmentCount >= attached.length || query == null || original == null)
                throw new IllegalStateException("P9 fine attachment escaped its armed selected-query window");
            int ordinal = attachmentCount++;
            attached[ordinal] = true; openDecorators++;
            return new Decorator(this, query, original, ordinal);
        }

        void onEvent(Decorator decorator, int attachmentOrdinal, PcbNetRouter.Router query,
                PcbNetRouter.Router.FixedGraphReasonObserver original,
                PcbNetRouter.Router.FixedGraphReason reason) {
            if (reason == null || attachmentOrdinal < 0 || attachmentOrdinal >= attached.length ||
                    !attached[attachmentOrdinal] || detached[attachmentOrdinal] ||
                    forwarded[attachmentOrdinal] || openDecorators < 1)
                throw new IllegalStateException("P9 fine event is duplicated or detached");
            Object context = readField(original, "context");
            Identity current = Identity.fromContext(context);
            if (current.compactOrdinal != attachmentOrdinal)
                throw new IllegalStateException("P9 decoration order differs from P8 selected query order");
            boolean targetEvent=current.parentOrdinal==11;
            forwarded[attachmentOrdinal] = true;
            forwardedFineEvents++;
            if (targetEvent) {
                if (attachmentOrdinal != 10 || targetEvents != 0 ||
                        current.layer != 1 || current.bit != 8 || !current.selected ||
                        current.fromGridX != 382 || current.fromGridY != 238 ||
                        current.toGridX != 381 || current.toGridY != 238 ||
                        !"A_COIL_LOW".equals(current.netId) ||
                        !"QA.C".equals(current.startPadId) || !"DA.A".equals(current.rootPadId) ||
                        !"[]".equals(current.reachedPthPadIds))
                    throw new IllegalStateException("P9 target differs from the actual frozen P8 parent-11 query");
            if (reason.stage != PcbNetRouter.Router.STAGE_TRAVERSE_REJECTED ||
                        !"CACHED_RESERVATION".equals(reason.ownerKind) ||
                        !"EMPTY_FACE_RESERVATION".equals(reason.site) ||
                        reason.fromGridX != current.fromGridX || reason.fromGridY != current.fromGridY ||
                        reason.toGridX != current.toGridX || reason.toGridY != current.toGridY ||
                    !current.startPadId.equals(reason.startPadId) ||
                    reason.reservationValue == null || current.netId.equals(reason.reservationValue) ||
                    (reason.reservationValue.length()==0 && !"MIXED".equals(reason.reservationState)) ||
                    (reason.reservationValue.length()!=0 && !"SINGLE".equals(reason.reservationState)) ||
                    (reason.reservationValue.length()==0 && reason.ownerNet!=null) ||
                    (reason.reservationValue.length()!=0 && !reason.reservationValue.equals(reason.ownerNet)))
                    throw new IllegalStateException("P9 target reason differs from actual empty-face cached lookup");
                if (query == null)
                    throw new IllegalStateException("P9 target callback has no actual compared Router");
                targetEvents++;
                targetAttachmentOrdinal = attachmentOrdinal;
                targetParentOrdinal = current.parentOrdinal;
                pendingIdentity = current;
                pendingReason = reason;
                boolean identityTransferred=false;
                try {
                    operands = query.snapshotReservationOperandsForStudy();
                    if (operands == null || operands.pads == null || operands.pads.length != PAD_COUNT)
                        throw new IllegalStateException("P9 source snapshot omitted the full Router-order pad census");
                    if(operands.identity!=current)
                        throw new IllegalStateException("P9 snapshot did not transfer its detached identity value");
                    identityTransferred=true;
                    padRowsBuilt = operands.pads.length;
                    if ("TAP_THROW".equals(arguments.mode)) {
                        containTargetSentinel();
                    } else {
                        padRowsRetained = operands.pads.length;
                    }
                } finally {
                    pendingIdentity = null; pendingReason = null;
                    if(!identityTransferred) current.clear(null);
                    current = null; context = null;
                }
            } else {
                current.clear(null); current = null; context = null;
            }
            try {
                original.onRejected(reason);
                if(targetEvent) targetForwardedEvents++;
            } catch (Throwable failure) {
                throw failure;
            }
        }

        private void containTargetSentinel() {
            if (operands == null || sentinelObserved)
                throw new IllegalStateException("P9 tap sentinel was duplicated or had no DTO");
            IllegalStateException sentinel = new IllegalStateException(SENTINEL);
            Throwable caught = null;
            try {
                throw sentinel;
            } catch (IllegalStateException error) {
                caught = error;
            }
            if (caught != sentinel || caught.getClass() != IllegalStateException.class ||
                    !SENTINEL.equals(caught.getMessage()))
                throw new IllegalStateException("P9 tap failed to contain its unique sentinel");
            sentinelObserved = true;
            operands.clear(releaseMeter);
            operands = null;
            padRowsRetained = 0;
            dtoReferencesDropped = true;
        }

        void noteDetach(int ordinal) {
            if (ordinal < 0 || ordinal >= detached.length || !attached[ordinal] || detached[ordinal] ||
                    openDecorators <= 0)
                throw new IllegalStateException("P9 decorator detach has no matching attachment");
            detached[ordinal] = true; detachCount++; openDecorators--;
        }

        void noteDecoratorReferencesDropped(boolean dropped) {
            if (!dropped) allDecoratorReferencesDropped = false;
        }

        void recordDelegateCleanup(long nanos) {
            if (nanos < 0 || nanos > MAX_GROUP_NANOS)
                throw new IllegalStateException("P9 decorator release exceeded one five-second group");
            p9DelegateCleanupNanos = saturatedAdd(p9DelegateCleanupNanos, nanos);
        }

        void afterDelegateReturn() {
            if (registrationArmed) {
                if (attachmentCount != SELECTED.length || detachCount != SELECTED.length ||
                        openDecorators != 0 || forwardedFineEvents != SELECTED.length ||
                        targetEvents != 1 || targetForwardedEvents != 1)
                    throw new IllegalStateException("P9 observer/delegate lifecycle did not close after P8");
                for (int i = 0; i < attached.length; i++)
                    if (!attached[i] || !detached[i] || !forwarded[i])
                        throw new IllegalStateException("P9 selected-query event/cleanup census is incomplete");
            } else if (attachmentCount != 0 || detachCount != 0 || openDecorators != 0 ||
                    targetEvents != 0 || forwardedFineEvents != 0 || operands != null)
                throw new IllegalStateException("P9 NULL mode used a fine observer or source getter");
        }

        void beginCapture() {
            if (captureStarted || pendingIdentity == null || pendingReason == null)
                throw new IllegalStateException("P9 operand capture did not begin at one actual target event");
            captureStarted = true;
            captureMeter.begin();
            captureMeter.add(13);
            pendingIdentity.account(captureMeter);
        }

        void captureHeaderExtra(int count) {
            if (!captureStarted || captureFinished || count != 17)
                throw new IllegalStateException("P9 Router header capture count differs from its schema");
            captureMeter.add(count);
        }

        void capturePad(int count) {
            if (!captureStarted || captureFinished || count != 25)
                throw new IllegalStateException("P9 pad capture unit differs from the frozen 25 fields");
            captureMeter.add(count);
        }

        void captureFooter(int count) {
            if (!captureStarted || captureFinished || count != 11)
                throw new IllegalStateException("P9 OBSERVE footer capture count differs from its schema");
            captureMeter.add(count);
        }

        void finishCapture() {
            int expected = captureMeter.finishExpected(CAPTURE_UNITS);
            if (expected != CAPTURE_UNITS) throw new IllegalStateException("P9 capture units are incomplete");
            captureFinished = true;
        }

        void dropDtoAndVerify() {
            if ("NULL".equals(arguments.mode)) {
                dtoReferencesDropped = operands == null;
                return;
            }
            if ("TAP_THROW".equals(arguments.mode)) {
                if (operands != null || padRowsRetained != 0 || !sentinelObserved ||
                        !dtoReferencesDropped || releaseMeter.units() != CAPTURE_UNITS)
                    throw new IllegalStateException("P9 tap DTO was not released after sentinel containment");
                return;
            }
            if (operands == null || padRowsRetained != PAD_COUNT || !captureFinished)
                throw new IllegalStateException("P9 observation DTO is missing before sidecar release");
            operands.clear(releaseMeter);
            operands = null;
            // Historical sidecar row count; DTO release is proved independently below.
            dtoReferencesDropped = true;
        }

        boolean referencesDropped() {
            if (operands != null || pendingIdentity != null || pendingReason != null || openDecorators != 0)
                return false;
            return dtoReferencesDropped || "NULL".equals(arguments.mode);
        }

        void dropAll() {
            if (operands != null) {
                try { operands.clear(releaseMeter); } catch (Throwable ignored) { }
                operands = null;
            }
            if (pendingIdentity != null) pendingIdentity.clear(null);
            pendingIdentity = null; pendingReason = null;
        }

        static String[] runLiteralCanaries() {
            String[] ids = {"NULL_UNARMED","FORWARD_ONCE","TARGET_ONE_SHOT","DETACH_DROP",
                "CONTAIN_SENTINEL","UNEXPECTED_PROPAGATES","UNIT_LIMIT","INCLUSIVE_OUTPUT_LIMIT"};
            runCanaryNullUnarmed();
            runCanaryForwardOnce();
            runCanaryTargetOneShot();
            runCanaryDetachDrop();
            runCanaryContainSentinel();
            runCanaryUnexpectedPropagates();
            runCanaryUnitLimit();
            runCanaryOutputLimit();
            return ids;
        }

        private static void runCanaryNullUnarmed() {
            PcbNetRouter.Router.FixedGraphReasonObserver original = new CanaryObserver(null, false);
            if (decorateForStudy(null, original) != original)
                throw new IllegalStateException("P9 NULL_UNARMED allocated a decorator");
        }

        private static void runCanaryForwardOnce() {
            CanaryContext context = CanaryContext.other(0);
            CanaryObserver original = new CanaryObserver(context, false);
            Session fixture = Session.canary("OBSERVE");
            attachCanary(fixture, 0);
            Decorator decorator = new Decorator(fixture, CanaryRouterHolder.ROUTER, original, 0);
            try { decorator.onRejected(CanaryReason.other()); }
            finally { decorator.detach(); }
            if (original.calls != 1 || fixture.forwardedFineEvents != 1 || fixture.targetEvents != 0 ||
                    !decorator.referencesDropped())
                throw new IllegalStateException("P9 FORWARD_ONCE did not forward one unchanged P8 event");
        }

        private static void runCanaryTargetOneShot() {
            Session fixture = Session.canary("OBSERVE");
            fixture.attached[10] = true; fixture.attachmentCount = 11; fixture.openDecorators = 1;
            fixture.targetEvents = 1;
            CanaryObserver original = new CanaryObserver(CanaryContext.target(), false);
            Decorator decorator = new Decorator(fixture, CanaryRouterHolder.ROUTER, original, 10);
            boolean duplicateRejected = false;
            try { decorator.onRejected(CanaryReason.target()); }
            catch (IllegalStateException expected) {
                duplicateRejected = "P9 target differs from the actual frozen P8 parent-11 query".equals(
                    expected.getMessage());
            } finally { decorator.detach(); }
            if (!duplicateRejected || original.calls!=0 || fixture.targetEvents!=1 ||
                    fixture.detachCount!=1 || !decorator.referencesDropped())
                throw new IllegalStateException("P9 TARGET_ONE_SHOT did not exercise the actual duplicate-target guard");
            Identity identity=Identity.canaryTarget();
            RouterOperands synthetic=RouterOperands.syntheticCanary(identity,CanaryReason.target());
            if(synthetic.identity!=identity||synthetic.pads==null||synthetic.pads.length!=PAD_COUNT)
                throw new IllegalStateException("P9 TARGET_ONE_SHOT lacks the literal target DTO");
            synthetic.clear(fixture.releaseMeter);
            if(fixture.releaseMeter.units()!=CAPTURE_UNITS)
                throw new IllegalStateException("P9 TARGET_ONE_SHOT did not release all DTO scalar rows");
        }

        private static void runCanaryDetachDrop() {
            Session fixture = Session.canary("OBSERVE");
            fixture.attached[0] = true; fixture.attachmentCount = 1; fixture.openDecorators = 1;
            CanaryObserver original = new CanaryObserver(CanaryContext.other(0), false);
            Decorator decorator = new Decorator(fixture, CanaryRouterHolder.ROUTER, original, 0);
            decorator.detach();
            if (!decorator.referencesDropped() || fixture.detachCount != 1 || fixture.openDecorators != 0)
                throw new IllegalStateException("P9 DETACH_DROP retained decorator references");
        }

        private static void runCanaryContainSentinel() {
            Session fixture = Session.canary("TAP_THROW");
            fixture.captureStarted = true; fixture.captureFinished = true;
            fixture.captureMeter.begin(); fixture.captureMeter.add(CAPTURE_UNITS);
            fixture.captureMeter.finishExpected(CAPTURE_UNITS);
            RouterOperands synthetic = RouterOperands.syntheticCanary(Identity.canaryTarget(),
                CanaryReason.target());
            fixture.operands = synthetic; fixture.padRowsBuilt = PAD_COUNT;
            fixture.containTargetSentinel();
            if (!fixture.sentinelObserved || fixture.operands != null || fixture.padRowsRetained != 0 ||
                    fixture.releaseMeter.units() != CAPTURE_UNITS || !fixture.dtoReferencesDropped)
                throw new IllegalStateException("P9 CONTAIN_SENTINEL did not discard captured DTO");
        }

        private static void runCanaryUnexpectedPropagates() {
            Session fixture = Session.canary("OBSERVE");
            attachCanary(fixture, 0);
            CanaryObserver original = new CanaryObserver(CanaryContext.other(0), true);
            Decorator decorator = new Decorator(fixture, CanaryRouterHolder.ROUTER, original, 0);
            boolean propagated = false;
            try { decorator.onRejected(CanaryReason.other()); }
            catch (IllegalStateException error) { propagated = "UNEXPECTED".equals(error.getMessage()); }
            finally { decorator.detach(); }
            if (!propagated||!decorator.referencesDropped())
                throw new IllegalStateException("P9 UNEXPECTED_PROPAGATES swallowed foreign failure or retained wrapper refs");
        }

        private static void runCanaryUnitLimit() {
            UnitMeter meter = new UnitMeter("P9 unit canary");
            meter.begin(); meter.add(MAX_UNITS);
            if (meter.units() != MAX_UNITS) throw new IllegalStateException("P9 unit cap exact boundary failed");
            boolean rejected = false;
            try { meter.add(1); } catch (IllegalStateException expected) { rejected = true; }
            if (!rejected) throw new IllegalStateException("P9 unit overflow was accepted");
        }

        private static void runCanaryOutputLimit() {
            requireAggregate(MAX_OUTPUT_BYTES);
            boolean rejected = false;
            try { requireAggregate(MAX_OUTPUT_BYTES + 1L); }
            catch (IllegalStateException expected) { rejected = true; }
            if (!rejected) throw new IllegalStateException("P9 inclusive byte overflow was accepted");
        }

        private static void requireAggregate(long bytes) {
            if (bytes < 0 || bytes > MAX_OUTPUT_BYTES)
                throw new IllegalStateException("P9 inclusive operation output limit");
        }

        private static void attachCanary(Session fixture, int ordinal) {
            fixture.attached[ordinal] = true; fixture.attachmentCount++;
            fixture.openDecorators++;
        }

        static Session canary(String mode) {
            Arguments args = new Arguments(mode, "00000000-0000-4000-8000-000000000000",
                repeat('a',64), repeat('b',64), repeat('c',64), Paths.get("."), Paths.get("."));
            return new Session(args, System.nanoTime());
        }
    }

    private static final class Arguments {
        final String mode, p8Mode, runId, p9PinsSha, p8WireSha, p9SourceSha;
        final Path p8Directory, p9Directory;
        Arguments(String modeValue, String run, String pins, String wire, String source,
                Path p8, Path p9) {
            mode = modeValue; p8Mode = "NULL".equals(modeValue) ? "NULL" : "OBSERVE";
            runId = run; p9PinsSha = pins; p8WireSha = wire; p9SourceSha = source;
            p8Directory = p8; p9Directory = p9;
        }
    }

    private static final class PreflightFailure extends Exception {
        final String code;
        final Integer argumentIndex;
        PreflightFailure(String value, Integer index) { super(value); code = value; argumentIndex = index; }
        String json() {
            TreeMap<String,Object> row = new TreeMap<String,Object>();
            row.put("schema", PREFLIGHT_SCHEMA); row.put("phase", "PREFLIGHT");
            row.put("code", code); row.put("argIndex", argumentIndex);
            return new String(JsonWriter.bytes(row), StandardCharsets.UTF_8);
        }
    }

    private static Arguments preflight(String[] args) throws PreflightFailure, IOException {
        if (args == null || args.length != 7) throw new PreflightFailure("ARGUMENT_COUNT", null);
        if (!"NULL".equals(args[0]) && !"OBSERVE".equals(args[0]) && !"TAP_THROW".equals(args[0]))
            throw new PreflightFailure("INVALID_MODE", Integer.valueOf(0));
        if (args[1] == null || !UUID_PATTERN.matcher(args[1]).matches())
            throw new PreflightFailure("INVALID_RUN_UUID", Integer.valueOf(1));
        if (!isSha(args[2])) throw new PreflightFailure("INVALID_INPUT_SHA256", Integer.valueOf(2));
        if (!isSha(args[3])) throw new PreflightFailure("INVALID_WIRE_SHA256", Integer.valueOf(3));
        if (!isSha(args[4])) throw new PreflightFailure("INVALID_SOURCE_SHA256", Integer.valueOf(4));
        Path p8 = requireEmptySiblingDirectory(args[5], Integer.valueOf(5));
        Path p9 = requireEmptySiblingDirectory(args[6], Integer.valueOf(6));
        if (p8.equals(p9) || !p8.getParent().equals(p9.getParent()))
            throw new PreflightFailure("OUTPUT_DIRECTORY_RELATION", Integer.valueOf(6));
        return new Arguments(args[0], args[1], args[2], args[3], args[4], p8, p9);
    }

    private static Path requireEmptySiblingDirectory(String text, Integer index)
            throws PreflightFailure, IOException {
        try {
            Path path = Paths.get(text);
            if (!path.isAbsolute() || !path.normalize().toString().equals(text))
                throw new PreflightFailure("NONCANONICAL_OUTPUT_DIRECTORY", index);
            for (Path current = path; current != null; current = current.getParent())
                if (Files.isSymbolicLink(current))
                    throw new PreflightFailure("OUTPUT_DIRECTORY_REPARSE", index);
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ||
                    !path.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(path))
                throw new PreflightFailure("OUTPUT_DIRECTORY_INVALID", index);
            DirectoryStream<Path> entries = Files.newDirectoryStream(path);
            try {
                if (entries.iterator().hasNext())
                    throw new PreflightFailure("OUTPUT_DIRECTORY_NOT_EMPTY", index);
            } finally { entries.close(); }
            return path;
        } catch (PreflightFailure failure) {
            throw failure;
        } catch (Exception error) {
            throw new PreflightFailure("OUTPUT_DIRECTORY_INVALID", index);
        }
    }

    private static final class Identity {
        int compactOrdinal, parentOrdinal, layer, fromState, toState, bit;
        int fromGridX, fromGridY, toGridX, toGridY;
        int fromX, fromY, toX, toY, direction;
        boolean selected;
        String netId, startPadId, rootPadId, reachedPthPadIds;

        static Identity fromContext(Object context) {
            if (context == null) throw new IllegalStateException("P8 fine observer has no QueryContext");
            Identity id = new Identity();
            id.parentOrdinal = intField(context, "ordinal");
            id.compactOrdinal = intField(context, "compactOrdinal");
            id.layer = intField(context, "layer");
            id.fromState = intField(context, "fromState"); id.toState = intField(context, "toState");
            id.bit = intField(context, "bit");
            id.fromGridX = intField(context, "fromGridX"); id.fromGridY = intField(context, "fromGridY");
            id.toGridX = intField(context, "toGridX"); id.toGridY = intField(context, "toGridY");
            id.fromX = intField(context, "fromX"); id.fromY = intField(context, "fromY");
            id.toX = intField(context, "toX"); id.toY = intField(context, "toY");
            id.direction = intField(context, "direction");
            id.netId = stringField(context, "netId");
            id.startPadId = stringField(context, "startPadId");
            id.rootPadId = stringField(context, "rootPadId");
            id.reachedPthPadIds = stringField(context, "reachedPthPadIdsJson");
            id.selected = booleanField(context, "selected");
            if (!id.selected || id.compactOrdinal < 0 || id.compactOrdinal >= SELECTED.length ||
                    id.parentOrdinal != SELECTED[id.compactOrdinal] || id.layer < 0 || id.layer > 1 ||
                    id.netId == null || id.startPadId == null || id.rootPadId == null ||
                    id.reachedPthPadIds == null)
                throw new IllegalStateException("P8 QueryContext differs from actual selected-query identity");
            return id;
        }

        static Identity canaryTarget() {
            Identity id = new Identity(); id.compactOrdinal=10; id.parentOrdinal=11;
            id.layer=1; id.fromState=261848; id.toState=261847; id.bit=8;id.selected=true;
            id.fromGridX=382; id.fromGridY=238; id.toGridX=381; id.toGridY=238;
            id.fromX=3850; id.fromY=2410; id.toX=3840; id.toY=2410; id.direction=3;
            id.netId="A_COIL_LOW"; id.startPadId="QA.C"; id.rootPadId="DA.A";
            id.reachedPthPadIds="[]"; return id;
        }

        void account(UnitMeter meter) {
            meter.add(17);
        }

        TreeMap<String,Object> asJson() {
            TreeMap<String,Object> out = new TreeMap<String,Object>();
            out.put("layer", Integer.valueOf(layer)); out.put("fromState", Integer.valueOf(fromState));
            out.put("toState", Integer.valueOf(toState)); out.put("bit", Integer.valueOf(bit));
            out.put("selected", Boolean.valueOf(selected)); out.put("fromGridX", Integer.valueOf(fromGridX));
            out.put("fromGridY", Integer.valueOf(fromGridY)); out.put("toGridX", Integer.valueOf(toGridX));
            out.put("toGridY", Integer.valueOf(toGridY)); out.put("fromX", Integer.valueOf(fromX));
            out.put("fromY", Integer.valueOf(fromY)); out.put("toX", Integer.valueOf(toX));
            out.put("toY", Integer.valueOf(toY)); out.put("direction", Integer.valueOf(direction));
            out.put("netId", netId); out.put("startPadId", startPadId); out.put("rootPadId", rootPadId);
            out.put("reachedPthPadIds", new ArrayList<Object>());
            return out;
        }

        void clear(UnitMeter meter) {
            compactOrdinal=parentOrdinal=layer=fromState=toState=bit=0;
            fromGridX=fromGridY=toGridX=toGridY=0;
            fromX=fromY=toX=toY=direction=0;
            netId=null; startPadId=null; rootPadId=null; reachedPthPadIds=null;selected=false;
            if (meter != null) meter.add(17);
        }

    }

    private static final class PadRow {
        int index, x, y, escapeDx, escapeDy, escapeLength;
        int padX, padY, padWidth, padHeight;
        int courtyardX, courtyardY, courtyardWidth, courtyardHeight;
        int corridorX, corridorY, corridorWidth, corridorHeight, padTraceWidth;
        String padId, componentId, netId, routingRole, mountingSide, attachment;

        PadRow(int i, String pad, String component, String net, String role, int px, int py,
                String side, String attach, int dx, int dy, int length,
                int bx, int by, int bw, int bh, int cx, int cy, int cw, int ch,
                int ex, int ey, int ew, int eh, int traceWidth) {
            index=i; padId=pad; componentId=component; netId=net; routingRole=role;
            x=px; y=py; mountingSide=side; attachment=attach;
            escapeDx=dx; escapeDy=dy; escapeLength=length;
            padX=bx; padY=by; padWidth=bw; padHeight=bh;
            courtyardX=cx; courtyardY=cy; courtyardWidth=cw; courtyardHeight=ch;
            corridorX=ex; corridorY=ey; corridorWidth=ew; corridorHeight=eh;
            padTraceWidth=traceWidth;
        }

        TreeMap<String,Object> asJson() {
            TreeMap<String,Object> out = new TreeMap<String,Object>();
            out.put("kind", "pad"); out.put("index", Integer.valueOf(index));
            out.put("padId", padId); out.put("componentId", componentId); out.put("netId", netId);
            out.put("routingRole", routingRole); out.put("x", Integer.valueOf(x));
            out.put("y", Integer.valueOf(y)); out.put("mountingSide", mountingSide);
            out.put("attachment", attachment); out.put("escapeDx", Integer.valueOf(escapeDx));
            out.put("escapeDy", Integer.valueOf(escapeDy)); out.put("escapeLength", Integer.valueOf(escapeLength));
            out.put("padBounds", rect(padX,padY,padWidth,padHeight));
            out.put("ownerCourtyard", rect(courtyardX,courtyardY,courtyardWidth,courtyardHeight));
            out.put("preparedEscapeCorridor", rect(corridorX,corridorY,corridorWidth,corridorHeight));
            out.put("padTraceWidth", Integer.valueOf(padTraceWidth));
            return out;
        }

        void clear(UnitMeter meter) {
            index=x=y=escapeDx=escapeDy=escapeLength=0;
            padX=padY=padWidth=padHeight=0;
            courtyardX=courtyardY=courtyardWidth=courtyardHeight=0;
            corridorX=corridorY=corridorWidth=corridorHeight=padTraceWidth=0;
            padId=componentId=netId=routingRole=mountingSide=attachment=null;
            meter.add(25);
        }

        static PadRow synthetic(int i) {
            return new PadRow(i, "PAD"+i, "COMP"+(i%8), "NET"+(i%5), "SIGNAL",
                i, i, "TOP", "PLATED_THROUGH_HOLE", 0, 0, 0,
                i, i, 2, 2, i, i, 4, 4, i, i, 0, 0, 1);
        }
    }

    private static final class CanaryContext {
        int ordinal, compactOrdinal, layer, fromState, toState, bit;
        int fromGridX,fromGridY,toGridX,toGridY,fromX,fromY,toX,toY,direction;
        String netId,startPadId,rootPadId,reachedPthPadIdsJson;
        boolean selected=true;
        static CanaryContext other(int ordinal) {
            CanaryContext c=new CanaryContext(); c.ordinal=ordinal; c.compactOrdinal=0;
            c.layer=0; c.fromState=0; c.toState=1; c.bit=2;
            c.fromGridX=0;c.fromGridY=0;c.toGridX=1;c.toGridY=0;
            c.fromX=10;c.fromY=10;c.toX=20;c.toY=10;c.direction=1;
            c.netId="NET";c.startPadId="START";c.rootPadId="ROOT";c.reachedPthPadIdsJson="[]";
            return c;
        }
        static CanaryContext target() {
            CanaryContext c=other(11); c.compactOrdinal=10;c.layer=1;c.fromState=261848;c.toState=261847;
            c.bit=8;c.fromGridX=382;c.fromGridY=238;c.toGridX=381;c.toGridY=238;
            c.fromX=3850;c.fromY=2410;c.toX=3840;c.toY=2410;c.direction=3;
            c.netId="A_COIL_LOW";c.startPadId="QA.C";c.rootPadId="DA.A";return c;
        }
    }

    private static final class CanaryObserver implements PcbNetRouter.Router.FixedGraphReasonObserver {
        private final Object context;
        private final boolean fail;
        int calls;
        CanaryObserver(Object value, boolean shouldFail) { context=value; fail=shouldFail; }
        public void onRejected(PcbNetRouter.Router.FixedGraphReason reason) {
            calls++;
            if (fail) throw new IllegalStateException("UNEXPECTED");
        }
    }

    private static final class CanaryRouterHolder {
        static final PcbNetRouter.Router ROUTER = null;
    }

    private static final class CanaryReason {
        static PcbNetRouter.Router.FixedGraphReason other() {
            return new PcbNetRouter.Router.FixedGraphReason("OTHER","PAD",1,"START",null,
                0,0,1,0,null,null,-1,null,null,null,null,-1,0,0,0,0,-1,-1,-1);
        }
        static PcbNetRouter.Router.FixedGraphReason target() {
            return new PcbNetRouter.Router.FixedGraphReason("EMPTY_FACE_RESERVATION",
                "CACHED_RESERVATION",PcbNetRouter.Router.STAGE_TRAVERSE_REJECTED,"QA.C",null,
                382,238,381,238,null,null,-1,"MIXED","",null,null,-1,0,0,0,0,-1,-1,-1);
        }
    }

    private static final class P8Marker {
        String mode, runId, inputSha, sourceSha, wireSha, indexPath, indexSha;
        long indexBytes, aggregateBytes, workNanos;
    }

    private static final class ExportData {
        String p8IndexPath, p8IndexSha, p6IndexPath, p6IndexSha;
        long p8IndexBytes, p6IndexBytes, p8IndexAggregateBytes, p8Units, p6CleanupNanos;
        int actualReceiptGroups, receiptLines;
        long receiptBytes;
        String receiptSha;
        boolean p8CleanupComplete, p6OwnersReleased, p6ContextRestored;
        void dropAll() {
            p8IndexPath=p8IndexSha=p6IndexPath=p6IndexSha=receiptSha=null;
            p8IndexBytes=p6IndexBytes=p8IndexAggregateBytes=p8Units=p6CleanupNanos=0;
            actualReceiptGroups=receiptLines=0; receiptBytes=0;
        }
    }

    private static final class FileEvidence {
        final String path, sha256;
        final long bytes;
        byte[] raw;
        FileEvidence(String name,long length,String digest,byte[] value) {
            path=name;bytes=length;sha256=digest;raw=value;
        }
        void clear() { zero(raw); raw=null; }
        TreeMap<String,Object> asJson() {
            TreeMap<String,Object> out=new TreeMap<String,Object>();
            out.put("path",path);out.put("bytes",Long.valueOf(bytes));out.put("sha256",sha256);
            return out;
        }
    }

    private static final class ReceiptEvidence {
        long bytes;
        String sha256;
        int groups, lines;
    }

    private static P8Marker parseP8Marker(byte[] raw, Arguments a, UnitMeter metadata)
            throws IOException {
        if (raw == null || raw.length < 2 || raw.length > MAX_LINE_BYTES)
            throw new IOException("P8 success marker is outside its bounded stdout frame");
        String text = decodeUtf8(raw);
        final String prefix = "CANONICAL_GEOMETRIC_PREFIX_COMPLETE ";
        if (!text.startsWith(prefix) || !text.endsWith("\n") || text.indexOf('\r') >= 0 ||
                text.indexOf('\n') != text.length()-1)
            throw new IOException("P8 stdout is not exactly one successful completion frame");
        Object parsed = JsonParser.parse(text.substring(prefix.length(), text.length()-1), metadata);
        Map<String,Object> marker = objectValue(parsed, "P8 marker");
        requireKeys(marker, new String[] {"schema","mode","runId","inputSha256","sourceSha256",
            "wireSha256","index","aggregateBytes","workNanos"}, "P8 marker");
        if (!P8_SCHEMA.equals(stringValue(marker.get("schema"),"P8 schema")) ||
                !a.p8Mode.equals(stringValue(marker.get("mode"),"P8 mode")) ||
                !a.runId.equals(stringValue(marker.get("runId"),"P8 run ID")) ||
                !a.p9PinsSha.equals(stringValue(marker.get("inputSha256"),"P8 input SHA")) ||
                !a.p9SourceSha.equals(stringValue(marker.get("sourceSha256"),"P8 source SHA")) ||
                !a.p8WireSha.equals(stringValue(marker.get("wireSha256"),"P8 wire SHA")))
            throw new IOException("P8 marker identity differs from the authenticated P9 invocation");
        Map<String,Object> ref = objectValue(marker.get("index"), "P8 index reference");
        requireKeys(ref, new String[] {"path","bytes","sha256"}, "P8 index reference");
        P8Marker result = new P8Marker();
        result.mode = stringValue(marker.get("mode"),"P8 mode");
        result.runId = stringValue(marker.get("runId"),"P8 run ID");
        result.inputSha = stringValue(marker.get("inputSha256"),"P8 input SHA");
        result.sourceSha = stringValue(marker.get("sourceSha256"),"P8 source SHA");
        result.wireSha = stringValue(marker.get("wireSha256"),"P8 wire SHA");
        result.indexPath = leafName(stringValue(ref.get("path"),"P8 index path"));
        result.indexBytes = nonnegativeLong(ref.get("bytes"),"P8 index bytes");
        result.indexSha = stringValue(ref.get("sha256"),"P8 index SHA");
        result.aggregateBytes = nonnegativeLong(marker.get("aggregateBytes"),"P8 aggregate bytes");
        result.workNanos = nonnegativeLong(marker.get("workNanos"),"P8 reported work");
        if (!"canonical-geometric-prefix-index.json".equals(result.indexPath) ||
                result.indexBytes < 1 || result.indexBytes > MAX_INDEX_BYTES || !isSha(result.indexSha) ||
                result.aggregateBytes < result.indexBytes || result.aggregateBytes > MAX_OUTPUT_BYTES ||
                result.workNanos < 1 || result.workNanos > MAX_OPERATION_NANOS)
            throw new IOException("P8 marker index/work values are outside their frozen bounds");
        return result;
    }

    private static ExportData readP8Evidence(Arguments a, P8Marker marker, Session session)
            throws Exception {
        requireP8Names(a.p8Directory);
        Path p8IndexPath = a.p8Directory.resolve(marker.indexPath);
        FileEvidence p8IndexFile = readBoundedEvidence(p8IndexPath, MAX_INDEX_BYTES, session.byteMeter);
        if (p8IndexFile.bytes != marker.indexBytes || !p8IndexFile.sha256.equals(marker.indexSha)) {
            p8IndexFile.clear(); throw new IOException("P8 index marker reference differs from actual index bytes");
        }
        Object p8Parsed = JsonParser.parse(decodeUtf8(p8IndexFile.raw), session.metadataMeter);
        Map<String,Object> p8 = objectValue(p8Parsed,"P8 index");
        requireKeys(p8,new String[] {"schema","mode","runId","inputSha256","sourceSha256","wireSha256",
            "parentPrefixSha256","parentGraphSha256","selectedQueryOrdinals","parentStageIndex",
            "reasonSidecar","queryCount","returnedQueryCount","fineEventCount","coarseEventCount",
            "aggregateBytes","priorObservationUnits","p8ObservationUnits","totalObservationUnits",
            "cleanupKind","cleanupComplete"},"P8 index");
        if (!P8_INDEX_SCHEMA.equals(stringValue(p8.get("schema"),"P8 index schema")) ||
                !a.p8Mode.equals(stringValue(p8.get("mode"),"P8 index mode")) ||
                !a.runId.equals(stringValue(p8.get("runId"),"P8 index run ID")) ||
                !a.p9PinsSha.equals(stringValue(p8.get("inputSha256"),"P8 index input SHA")) ||
                !a.p9SourceSha.equals(stringValue(p8.get("sourceSha256"),"P8 index source SHA")) ||
                !a.p8WireSha.equals(stringValue(p8.get("wireSha256"),"P8 index wire SHA")) ||
                !PREFIX_SHA.equals(stringValue(p8.get("parentPrefixSha256"),"P8 prefix SHA")) ||
                !GRAPH_SHA.equals(stringValue(p8.get("parentGraphSha256"),"P8 graph SHA")) ||
                !"FULL_NATIVE_WALL_PLUS_POST_SOURCE".equals(stringValue(p8.get("cleanupKind"),"P8 cleanup kind")) ||
                !booleanValue(p8.get("cleanupComplete"),"P8 cleanup complete"))
            throw new IOException("P8 index identity or cleanup proof differs from the pinned P8 source");
        List<Object> selected = arrayValue(p8.get("selectedQueryOrdinals"),"P8 selected ordinals");
        if (selected.size()!=SELECTED.length) throw new IOException("P8 selected query census changed");
        for(int i=0;i<SELECTED.length;i++)
            if (nonnegativeLong(selected.get(i),"P8 selected ordinal")!=SELECTED[i])
                throw new IOException("P8 selected query order changed");
        Map<String,Object> parentRef=objectValue(p8.get("parentStageIndex"),"P8 P6 index reference");
        requireKeys(parentRef,new String[] {"path","bytes","sha256"},"P8 P6 reference");
        Map<String,Object> reasonRef=objectValue(p8.get("reasonSidecar"),"P8 reason sidecar reference");
        requireKeys(reasonRef,new String[] {"path","bytes","sha256"},"P8 reason reference");
        if (!"canonical-stage-index.json".equals(leafName(stringValue(parentRef.get("path"),"P6 index path"))) ||
                !"prefix-geometric-reasons.jsonl".equals(leafName(stringValue(reasonRef.get("path"),"P8 reason path"))) ||
                !isSha(stringValue(reasonRef.get("sha256"),"P8 reason SHA")) ||
                nonnegativeLong(reasonRef.get("bytes"),"P8 reason bytes") < 1)
            throw new IOException("P8 sidecar/index paths do not match their fixed output roles");
        long p8IndexAggregate=nonnegativeLong(p8.get("aggregateBytes"),"P8 index aggregate bytes");
        if (marker.aggregateBytes != p8IndexAggregate + p8IndexFile.bytes)
            throw new IOException("P8 marker aggregate does not include its exact index bytes");
        long p8Units=nonnegativeLong(p8.get("totalObservationUnits"),"P8 total units");
        long priorUnits=nonnegativeLong(p8.get("priorObservationUnits"),"P8 prior units");
        long p8Only=nonnegativeLong(p8.get("p8ObservationUnits"),"P8 units");
        if (p8Units != priorUnits+p8Only || p8Units > MAX_UNITS)
            throw new IOException("P8 hierarchical observation unit total is inconsistent");
        if (nonnegativeLong(p8.get("queryCount"),"P8 query count")!=SELECTED.length ||
                nonnegativeLong(p8.get("returnedQueryCount"),"P8 returned count")!=SELECTED.length)
            throw new IOException("P8 did not finish all 14 selected queries");
        long fine=nonnegativeLong(p8.get("fineEventCount"),"P8 fine event count");
        if (fine != ("NULL".equals(a.mode)?0:SELECTED.length))
            throw new IOException("P8 fine event count differs from the selected observation mode");

        String p6Name=leafName(stringValue(parentRef.get("path"),"P6 index path"));
        FileEvidence p6File=readBoundedEvidence(a.p8Directory.resolve(p6Name),MAX_INDEX_BYTES,session.byteMeter);
        long expectedP6Bytes=nonnegativeLong(parentRef.get("bytes"),"P6 ref bytes");
        String expectedP6Sha=stringValue(parentRef.get("sha256"),"P6 ref SHA");
        if (p6File.bytes!=expectedP6Bytes || !p6File.sha256.equals(expectedP6Sha) || !isSha(expectedP6Sha)) {
            p6File.clear(); p8IndexFile.clear();
            throw new IOException("P8 P6 reference differs from actual P6 index bytes");
        }
        Object p6Parsed=JsonParser.parse(decodeUtf8(p6File.raw),session.metadataMeter);
        Map<String,Object> p6=objectValue(p6Parsed,"P6 index");
        Map<String,Object> operation=objectValue(p6.get("operation"),"P6 operation");
        Map<String,Object> cleanup=objectValue(operation.get("cleanup"),"P6 cleanup");
        if (!"PASS".equals(stringValue(cleanup.get("status"),"P6 cleanup status")))
            throw new IOException("Actual P6 cleanup status is not PASS");
        boolean ownersReleased=booleanValue(cleanup.get("ownersReleased"),"P6 owners released");
        boolean contextRestored=booleanValue(cleanup.get("contextRestored"),"P6 context restored");
        long p6Cleanup=nonnegativeLong(cleanup.get("elapsedNanos"),"P6 cleanup nanos");
        if (!ownersReleased || !contextRestored || p6Cleanup < 1 || p6Cleanup > MAX_CLEANUP_NANOS)
            throw new IOException("Actual P6 cleanup proof is outside its frozen bounds");
        Map<String,Object> receiptRef=objectValue(operation.get("receipt"),"P6 receipt reference");
        requireKeys(receiptRef,new String[] {"path","bytes","sha256"},"P6 receipt reference");
        String receiptName=leafName(stringValue(receiptRef.get("path"),"P8 receipt path"));
        if (!"op-1.jsonl".equals(receiptName)) throw new IOException("P6 receipt path changed");
        ReceiptEvidence receipt=scanReceipt(a.p8Directory.resolve(receiptName),
            nonnegativeLong(receiptRef.get("bytes"),"P8 receipt bytes"),
            stringValue(receiptRef.get("sha256"),"P8 receipt SHA"),session);
        ExportData out=new ExportData();
        out.p8IndexPath=marker.indexPath;out.p8IndexSha=marker.indexSha;out.p8IndexBytes=marker.indexBytes;
        out.p8IndexAggregateBytes=p8IndexAggregate;out.p8Units=p8Units;
        out.p6IndexPath=p6Name;out.p6IndexSha=p6File.sha256;out.p6IndexBytes=p6File.bytes;
        out.p6CleanupNanos=p6Cleanup;out.p6OwnersReleased=ownersReleased;
        out.p6ContextRestored=contextRestored;out.p8CleanupComplete=true;
        out.actualReceiptGroups=receipt.groups;out.receiptLines=receipt.lines;
        out.receiptBytes=receipt.bytes;out.receiptSha=receipt.sha256;
        if (receipt.groups < 1 || receipt.lines < 1 || receipt.bytes > MAX_OUTPUT_BYTES)
            throw new IOException("Actual P8 operation receipt census is incomplete");
        p6File.clear();p8IndexFile.clear();
        p6=null;p8=null;p6Parsed=null;p8Parsed=null;operation=null;cleanup=null;receiptRef=null;
        return out;
    }

    private static void requireP8Names(Path directory) throws IOException {
        Set<String> expected=new HashSet<String>(Arrays.asList(new String[] {
            "op-1.jsonl","fixed-prefix.jsonl","prefix-blockers.json","prefix-stages.json",
            "canonical-stage-index.json","prefix-geometric-reasons.jsonl",
            "canonical-geometric-prefix-index.json"}));
        DirectoryStream<Path> stream=Files.newDirectoryStream(directory);
        try {
            for(Path entry:stream) {
                if(Files.isSymbolicLink(entry) || !Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS) ||
                        !expected.remove(entry.getFileName().toString()))
                    throw new IOException("P8 data directory contains an unexpected output");
            }
        } finally { stream.close(); }
        if(!expected.isEmpty()) throw new IOException("P8 data directory omitted a required output");
    }

    private static FileEvidence readBoundedEvidence(Path path,int maximum,ByteMeter meter)
            throws IOException {
        if(Files.isSymbolicLink(path) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("P8 evidence path is not a regular file");
        long size=Files.size(path);
        if(size<1 || size>maximum) throw new IOException("P8 evidence file exceeds its frozen limit");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream((int)size);
        MessageDigest digest=newSha256();
        InputStream input=Files.newInputStream(path);
        try {
            byte[] buffer=new byte[CHUNK_BYTES];
            int total=0;
            while(total<size) {
                int read=(int)Math.min(CHUNK_BYTES,size-total);
                long started=System.nanoTime();
                readFully(input,buffer,read);
                bytes.write(buffer,0,read);digest.update(buffer,0,read);total+=read;
                meter.recordPassChunk(started,read);
            }
            if(input.read()!=-1) throw new IOException("P8 evidence grew during bounded read");
            if(total!=size) throw new IOException("P8 evidence changed during bounded read");
        } finally { input.close(); }
        return new FileEvidence(path.getFileName().toString(),size,hex(digest.digest()),bytes.toByteArray());
    }

    private static ReceiptEvidence scanReceipt(Path path,long expectedBytes,String expectedSha,
            Session session) throws IOException {
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||
                expectedBytes<1||expectedBytes>MAX_OUTPUT_BYTES||!isSha(expectedSha))
            throw new IOException("P8 receipt identity is outside its actual bounded ref");
        if(Files.size(path)!=expectedBytes) throw new IOException("P8 receipt size differs from P6 ref");
        ReceiptEvidence result=new ReceiptEvidence();
        MessageDigest digest=newSha256();
        ByteArrayOutputStream line=new ByteArrayOutputStream(512);
        InputStream input=Files.newInputStream(path);
        long total=0; boolean endedWithLf=false; int footers=0;
        try {
            byte[] buffer=new byte[CHUNK_BYTES];
            while(total<expectedBytes) {
                int read=(int)Math.min(CHUNK_BYTES,expectedBytes-total);
                long started=System.nanoTime();
                if(total+read>expectedBytes) throw new IOException("P8 receipt grew during bounded scan");
                readFully(input,buffer,read);
                digest.update(buffer,0,read);total+=read;
                for(int i=0;i<read;i++) {
                    int value=buffer[i]&255;
                    if(value=='\r') throw new IOException("P8 receipt contains CR bytes");
                    if(value=='\n') {
                        if(line.size()<2 || line.size()+1>MAX_LINE_BYTES)
                            throw new IOException("P8 receipt line is empty or oversized");
                        String record=receiptRecord(line.toByteArray());
                        session.metadataMeter.add(1);
                        result.lines++;
                        if("GROUP".equals(record)) result.groups++;
                        if("FOOTER".equals(record)) footers++;
                        line.reset();endedWithLf=true;
                    } else {
                        endedWithLf=false;
                        if(line.size()+1>=MAX_LINE_BYTES) throw new IOException("P8 receipt line exceeds 64 KiB");
                        line.write(value);
                    }
                }
                session.byteMeter.recordPassChunk(started,read);
            }
            if(input.read()!=-1) throw new IOException("P8 receipt grew during bounded scan");
        } finally { input.close(); line.reset(); }
        String sha=hex(digest.digest());
        if(total!=expectedBytes||!endedWithLf||line.size()!=0||!expectedSha.equals(sha)||footers!=1)
            throw new IOException("P8 receipt bytes/hash/LF footer differ from the actual P6 receipt ref");
        result.bytes=total;result.sha256=sha;return result;
    }

    private static String receiptRecord(byte[] raw) throws IOException {
        try {
            String line=decodeUtf8(raw);
            String prefix="{\"record\":\"";
            if(!line.startsWith(prefix)) throw new IOException("P8 receipt row lacks its actual record field first");
            int end=line.indexOf('"',prefix.length());
            if(end<0||end+1>=line.length()||line.charAt(end+1)!=','&&line.charAt(end+1)!='}')
                throw new IOException("P8 receipt record field is malformed");
            String value=line.substring(prefix.length(),end);
            if(value.length()==0) throw new IOException("P8 receipt record value is empty");
            return value;
        } finally { zero(raw); }
    }

    private static byte[] serializeSidecar(Arguments a,Session session,ExportData export,
            P8Marker marker) throws IOException {
        session.serializationMeter.begin();
        ByteArrayOutputStream output=new ByteArrayOutputStream(65536);
        TreeMap<String,Object> header=new TreeMap<String,Object>();
        header.put("kind","header");header.put("schema",SCHEMA);header.put("runId",a.runId);
        header.put("inputSha256",a.p9PinsSha);header.put("sourceSha256",a.p9SourceSha);
        header.put("wireSha256",a.p8WireSha);header.put("wrapperMode",a.mode);
        header.put("p8Mode",a.p8Mode);header.put("parentPrefixSha256",PREFIX_SHA);
        header.put("parentGraphSha256",GRAPH_SHA);
        header.put("queryOrdinal",Integer.valueOf(10));
        header.put("parentOrdinal",Integer.valueOf(11));
        header.put("captureState","NULL".equals(a.mode)?"NOT_REGISTERED":
            "TAP_THROW".equals(a.mode)?"DTO_DISCARDED":"CAPTURED");
        RouterOperands operands=session.operands;
        if("OBSERVE".equals(a.mode)) {
            if(operands==null||operands.identity==null||operands.pads==null||operands.pads.length!=PAD_COUNT)
                throw new IOException("P9 OBSERVE DTO disappeared before sidecar serialization");
            header.put("identity",operands.identity.asJson());
            header.put("grid",Integer.valueOf(10));
            header.put("outline",rect(operands.outlineX,operands.outlineY,
                operands.outlineWidth,operands.outlineHeight));
            header.put("minX",Integer.valueOf(operands.minX));header.put("minY",Integer.valueOf(operands.minY));
            header.put("gridWidth",Integer.valueOf(operands.gridWidth));
            header.put("gridHeight",Integer.valueOf(operands.gridHeight));
            header.put("selectedLayer",Integer.valueOf(operands.selectedLayer));
            header.put("requestedNetId",operands.identity.netId);
            header.put("traceWidthProfile","ROLE_WIDTHS");header.put("profileIdentity","ROLE_TRACE@1");
            header.put("emptyComponentFace",Boolean.valueOf(operands.emptyComponentFace));
            header.put("checkProfiledPadTouches",Boolean.valueOf(operands.checkProfiledPadTouches));
            header.put("escapeOutletLand",Integer.valueOf(operands.escapeOutletLand));
            header.put("escapeOutletLandOrigin",operands.escapeOutletLandOrigin);
        }
        appendJsonLine(output,header,session.serializationMeter);
        if("OBSERVE".equals(a.mode)) {
            for(int i=0;i<PAD_COUNT;i++) {
                PadRow row=operands.pads[i];
                if(row==null||row.index!=i) throw new IOException("P9 PAD rows lost actual Router order");
                appendJsonLine(output,row.asJson(),session.serializationMeter);
            }
            if(session.padRowsRetained!=PAD_COUNT)
                throw new IOException("P9 retained PAD row census differs from the exact 246 rows");
        }
        TreeMap<String,Object> footer=new TreeMap<String,Object>();
        footer.put("kind","footer");footer.put("complete",Boolean.TRUE);footer.put("runId",a.runId);
        footer.put("wrapperMode",a.mode);footer.put("p8Mode",a.p8Mode);
        if("OBSERVE".equals(a.mode)) {
            footer.put("recordCount",Integer.valueOf(248));footer.put("padCount",Integer.valueOf(PAD_COUNT));
            footer.put("requestedNetId",operands.identity.netId);footer.put("activeNetId",operands.activeNetId);
            footer.put("activeTraceWidth",Integer.valueOf(operands.activeTraceWidth));
            footer.put("slotValue",operands.targetSlotValue);
        } else {
            footer.put("recordCount",Integer.valueOf(2));
            footer.put("captureState","NULL".equals(a.mode)?"NOT_REGISTERED":"DTO_DISCARDED");
            if("TAP_THROW".equals(a.mode)) footer.put("tapThrow",tapThrowJson(session));
        }
        appendJsonLine(output,footer,session.serializationMeter);
        byte[] bytes=output.toByteArray();
        session.serializationMeter.finishExpected("NULL".equals(a.mode)?20:
            "TAP_THROW".equals(a.mode)?30:OBSERVE_SERIAL_UNITS);
        if(bytes.length>MAX_OUTPUT_BYTES) throw new IOException("P9 sidecar exceeds eight MiB");
        return bytes;
    }

    private static TreeMap<String,Object> tapThrowJson(Session session) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        out.put("attempted",Boolean.TRUE);out.put("caught",Boolean.TRUE);
        out.put("sameThrowable",Boolean.TRUE);out.put("exceptionClass","java.lang.IllegalStateException");
        out.put("message",SENTINEL);out.put("count",Integer.valueOf(1));
        out.put("dtoPadCount",Integer.valueOf(session.padRowsBuilt));
        out.put("dtoDiscarded",Boolean.valueOf(session.dtoReferencesDropped));
        out.put("slotDiscarded",Boolean.valueOf(session.dtoReferencesDropped));
        out.put("queryContinued",Boolean.valueOf(session.targetForwardedEvents==1));
        return out;
    }

    private static void appendJsonLine(ByteArrayOutputStream output,Object value,UnitMeter meter)
            throws IOException {
        byte[] line=JsonWriter.bytes(value,meter);
        try {
            if(line.length<2||line.length+1>MAX_LINE_BYTES)
                throw new IOException("P9 JSONL line exceeds the exact 64 KiB wire bound");
            for(byte b:line) if(b=='\r'||b=='\n') throw new IOException("P9 JSONL line contains raw line break");
            output.write(line,0,line.length);output.write('\n');
        } finally { zero(line); }
    }

    private static TreeMap<String,Object> buildIndex(Arguments a,P8Marker marker,ExportData export,
            Session session,FileEvidence sidecar,long preflightNanos,long delegateStartOffset,
            long delegateReturnOffset,long p9DelegateCleanup,long p9Cleanup,long cutoffOffset,
            long creditedWork,int p8Units,int captureUnits,int releaseUnits,int metadataUnits,
            int serializationUnits,int byteUnits,int exportUnits,int p9Units,int totalUnits,
            int nativeWrapperGroups,int p9Groups,int totalGroups,long maxBeforeIndex,
            long measuredAtCutoff,long preIndexAggregate,long p8StderrBytes) {
        TreeMap<String,Object> index=new TreeMap<String,Object>();
        index.put("schema",INDEX_SCHEMA);index.put("wrapperMode",a.mode);index.put("p8Mode",a.p8Mode);
        index.put("runId",a.runId);index.put("inputSha256",a.p9PinsSha);
        index.put("sourceSha256",a.p9SourceSha);index.put("p8Index",fileRef(export.p8IndexPath,
            export.p8IndexBytes,export.p8IndexSha));
        index.put("sidecar",sidecar.asJson());index.put("capture",captureJson(a,session));
        index.put("work",workJson(a,marker,export,session,preflightNanos,delegateStartOffset,
            delegateReturnOffset,p9DelegateCleanup,p9Cleanup,cutoffOffset,creditedWork,
            p8Units,captureUnits,releaseUnits,metadataUnits,serializationUnits,byteUnits,
            exportUnits,p9Units,totalUnits,nativeWrapperGroups,p9Groups,totalGroups,
            maxBeforeIndex,measuredAtCutoff));
        TreeMap<String,Object> bytes=new TreeMap<String,Object>();
        bytes.put("p8DataBytes",Long.valueOf(marker.aggregateBytes));
        bytes.put("p8StdoutBytes",Long.valueOf(session.outboundBytes));
        bytes.put("p8StderrBytes",Long.valueOf(p8StderrBytes));
        bytes.put("p9SidecarBytes",Long.valueOf(sidecar.bytes));
        bytes.put("preIndexAggregateBytes",Long.valueOf(preIndexAggregate));
        index.put("bytes",bytes);index.put("cleanup",cleanupJson(a,export,session));
        index.put("status","PROVISIONAL_NATIVE_RESULT");index.put("layoutQualification",Boolean.FALSE);
        return index;
    }

    private static TreeMap<String,Object> captureJson(Arguments a,Session s) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        String state="NULL".equals(a.mode)?"NONE":"TAP_THROW".equals(a.mode)?
            "DISCARDED_AFTER_SENTINEL":"COMPLETE";
        out.put("state",state);out.put("registrationArmed",Boolean.valueOf(s.registrationArmed));
        out.put("fineAttachments",Integer.valueOf(s.attachmentCount));
        out.put("decoratorsDetached",Integer.valueOf(s.detachCount));
        out.put("openDecorators",Integer.valueOf(s.openDecorators));
        out.put("targetAttachmentOrdinal",s.targetAttachmentOrdinal<0?null:Integer.valueOf(s.targetAttachmentOrdinal));
        out.put("targetParentOrdinal",s.targetParentOrdinal<0?null:Integer.valueOf(s.targetParentOrdinal));
        out.put("targetEvents",Integer.valueOf(s.targetEvents));
        out.put("padRowsBuilt",Integer.valueOf(s.padRowsBuilt));
        out.put("padRowsRetained",Integer.valueOf(s.padRowsRetained));
        out.put("sentinelObserved",Boolean.valueOf(s.sentinelObserved));
        out.put("forwardedFineEvents",Integer.valueOf(s.forwardedFineEvents));
        out.put("targetForwardedEvents",Integer.valueOf(s.targetForwardedEvents));
        return out;
    }

    private static TreeMap<String,Object> cleanupJson(Arguments a,ExportData x,Session s) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        out.put("p8AfterOwnersClosed",Boolean.TRUE);
        out.put("p8OwnersReleased",Boolean.valueOf(x.p8CleanupComplete));
        out.put("p8ContextRestored",Boolean.valueOf(x.p8CleanupComplete));
        out.put("p8SourcePreserved",Boolean.valueOf(x.p8CleanupComplete));
        out.put("p8ObserversDetached",Boolean.valueOf(x.p8CleanupComplete));
        out.put("p8ReferencesDropped",Boolean.valueOf(x.p8CleanupComplete));
        out.put("p9RegistrationCleared",Boolean.valueOf(activeSession==null));
        out.put("p9DecoratorDetachCount",Integer.valueOf(s.detachCount));
        out.put("p9OpenDecorators",Integer.valueOf(s.openDecorators));
        out.put("p9DecoratorReferencesDropped",Boolean.valueOf(s.allDecoratorReferencesDropped));
        out.put("p9DtoReferencesDropped",Boolean.valueOf(s.dtoReferencesDropped||"NULL".equals(a.mode)));
        return out;
    }

    private static TreeMap<String,Object> workJson(Arguments a,P8Marker marker,ExportData x,Session s,
            long preflightNanos,long delegateStartOffset,long delegateReturnOffset,
            long p9DelegateCleanup,long p9Cleanup,long cutoffOffset,long creditedWork,
            int p8Units,int captureUnits,int releaseUnits,int metadataUnits,int serializationUnits,
            int byteUnits,int exportUnits,int p9Units,int totalUnits,int nativeWrapperGroups,
            int p9Groups,int totalGroups,long maxBeforeIndex,long measuredAtCutoff) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        out.put("delegateStartOffsetNanos",Long.valueOf(delegateStartOffset));
        out.put("delegateReturnOffsetNanos",Long.valueOf(delegateReturnOffset));
        out.put("p8ReportedWorkNanos",Long.valueOf(marker.workNanos));
        out.put("delegateCleanupNanos",Long.valueOf(x.p6CleanupNanos));
        out.put("p9DelegateCleanupNanos",Long.valueOf(p9DelegateCleanup));
        ArrayList<Object> segments=new ArrayList<Object>();
        segments.add(segment("P9_PREFLIGHT",0L,delegateStartOffset,0,
            1,preflightNanos,s.preflightCleanupNanos));
        segments.add(segment("P9_SIDECAR_EXPORT",delegateReturnOffset,cutoffOffset,
            s.metadataMeter.units()+s.serializationMeter.units()+s.byteMeter.units(),
            1,elapsedFromOffsets(cutoffOffset,delegateReturnOffset),s.exportCleanupNanos));
        out.put("outsideWorkSegments",segments);
        out.put("p9CleanupNanos",Long.valueOf(p9Cleanup));
        out.put("creditCutoffOffsetNanos",Long.valueOf(cutoffOffset));
        out.put("creditedWorkNanos",Long.valueOf(creditedWork));
        out.put("p8Units",Integer.valueOf(p8Units));out.put("p9CaptureUnits",Integer.valueOf(captureUnits));
        out.put("p9ReleaseUnits",Integer.valueOf(releaseUnits));out.put("p9MetadataUnits",Integer.valueOf(metadataUnits));
        out.put("p9SerializationUnits",Integer.valueOf(serializationUnits));out.put("p9ByteUnits",Integer.valueOf(byteUnits));
        out.put("p9IndexUnits",Integer.valueOf(4));out.put("p9ExportUnits",Integer.valueOf(exportUnits));
        out.put("p9Units",Integer.valueOf(p9Units));out.put("totalUnits",Integer.valueOf(totalUnits));
        out.put("actualReceiptGroups",Integer.valueOf(x.actualReceiptGroups));
        out.put("nativeWrapperGroups",Integer.valueOf(nativeWrapperGroups));
        out.put("p9Groups",Integer.valueOf(p9Groups));out.put("totalGroups",Integer.valueOf(totalGroups));
        out.put("maximumP9GroupNanosBeforeIndex",Long.valueOf(maxBeforeIndex));
        out.put("measuredWorkAtIndexCutoffNanos",Long.valueOf(measuredAtCutoff));
        out.put("finalGuardFormula","P9_ELAPSED_MINUS_P6_ARM_CLEANUP");
        return out;
    }

    private static TreeMap<String,Object> segment(String phase,long start,long end,int units,
            int groups,long maxGroup,long cleanup) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        out.put("phase",phase);out.put("startOffsetNanos",Long.valueOf(start));
        out.put("endOffsetNanos",Long.valueOf(end));out.put("units",Integer.valueOf(units));
        out.put("groupCount",Integer.valueOf(groups));out.put("maximumGroupNanos",Long.valueOf(maxGroup));
        out.put("cleanupNanos",Long.valueOf(cleanup));return out;
    }

    private static String sha256Pass(byte[] raw,ByteMeter meter,String label) throws IOException {
        if(raw==null) throw new IOException(label+" has no byte array");
        MessageDigest digest=newSha256();
        for(int offset=0;offset<raw.length;offset+=CHUNK_BYTES) {
            int length=Math.min(CHUNK_BYTES,raw.length-offset);long started=System.nanoTime();
            digest.update(raw,offset,length);meter.recordPassChunk(started,length);
        }
        return hex(digest.digest());
    }

    private static void writeNewChunked(Path path,byte[] raw,ByteMeter meter) throws IOException {
        OutputStream output=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
        try {
            for(int offset=0;offset<raw.length;offset+=CHUNK_BYTES) {
                int length=Math.min(CHUNK_BYTES,raw.length-offset);long started=System.nanoTime();
                output.write(raw,offset,length);meter.recordPassChunk(started,length);
            }
            output.flush();
        } finally { output.close(); }
    }

    private static FileEvidence readBackHash(Path path,long expectedBytes,String expectedSha,
            ByteMeter meter) throws IOException {
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||
                Files.size(path)!=expectedBytes)
            throw new IOException("P9 sidecar output file differs from its CREATE_NEW identity");
        MessageDigest digest=newSha256();
        InputStream input=Files.newInputStream(path);
        try {
            long offset=0;byte[] buffer=new byte[CHUNK_BYTES];
            while(offset<expectedBytes) {
                int expected=(int)Math.min(CHUNK_BYTES,expectedBytes-offset);
                long started=System.nanoTime();readFully(input,buffer,expected);
                digest.update(buffer,0,expected);offset+=expected;
                meter.recordPassChunk(started,expected);
            }
            if(input.read()!=-1) throw new IOException("P9 sidecar grew during readback");
        } finally { input.close(); }
        String sha=hex(digest.digest());
        if(!expectedSha.equals(sha)) throw new IOException("P9 sidecar hash changed after CREATE_NEW write");
        return new FileEvidence(path.getFileName().toString(),expectedBytes,sha,null);
    }

    private static void writeNewIndex(Path path,byte[] raw) throws IOException {
        java.nio.channels.FileChannel output=java.nio.channels.FileChannel.open(path,
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try {
            ByteBuffer bytes=ByteBuffer.wrap(raw);
            while(bytes.hasRemaining()) output.write(bytes);
            output.force(true);
        } finally { output.close(); }
    }

    private static byte[] readBounded(Path path,int maximum) throws IOException {
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("P9 index readback is not a regular file");
        long size=Files.size(path);
        if(size<1||size>maximum) throw new IOException("P9 index readback exceeds 64 KiB");
        byte[] bytes=Files.readAllBytes(path);
        if(bytes.length!=size) throw new IOException("P9 index changed during readback");
        return bytes;
    }

    private static TreeMap<String,Object> rect(int x,int y,int width,int height) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        out.put("x",Integer.valueOf(x));out.put("y",Integer.valueOf(y));
        out.put("width",Integer.valueOf(width));out.put("height",Integer.valueOf(height));
        return out;
    }

    private static TreeMap<String,Object> fileRef(String path,long bytes,String sha) {
        TreeMap<String,Object> out=new TreeMap<String,Object>();
        out.put("path",path);out.put("bytes",Long.valueOf(bytes));out.put("sha256",sha);return out;
    }

    private static byte[] appendLf(byte[] raw) {
        byte[] out=Arrays.copyOf(raw,raw.length+1);out[raw.length]='\n';return out;
    }

    private static Object readField(Object owner,String name) {
        if(owner==null) throw new IllegalStateException("Cannot read null P8 callback owner field "+name);
        Class<?> type=owner.getClass();
        while(type!=null) {
            try {
                Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
            } catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
            catch(Exception error) { throw new IllegalStateException("Could not read P8 callback field "+name,error); }
        }
        throw new IllegalStateException("P8 callback field is unavailable: "+name);
    }

    private static int intField(Object owner,String name) {
        Object value=readField(owner,name);
        if(!(value instanceof Integer)) throw new IllegalStateException("P8 field is not an int: "+name);
        return ((Integer)value).intValue();
    }

    private static boolean booleanField(Object owner,String name) {
        Object value=readField(owner,name);
        if(!(value instanceof Boolean)) throw new IllegalStateException("P8 field is not a boolean: "+name);
        return ((Boolean)value).booleanValue();
    }

    private static String stringField(Object owner,String name) {
        Object value=readField(owner,name);
        if(value!=null&&!(value instanceof String)) throw new IllegalStateException("P8 field is not a String: "+name);
        return (String)value;
    }

    private static void requireKeys(Map<String,Object> value,String[] expected,String label) {
        Set<String> keys=new HashSet<String>(Arrays.asList(expected));
        if(value==null||value.size()!=keys.size()||!value.keySet().equals(keys))
            throw new IllegalStateException(label+" key set differs from its exact schema");
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> objectValue(Object value,String label) {
        if(!(value instanceof Map)) throw new IllegalStateException(label+" is not a JSON object");
        return (Map<String,Object>)value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> arrayValue(Object value,String label) {
        if(!(value instanceof List)) throw new IllegalStateException(label+" is not a JSON array");
        return (List<Object>)value;
    }

    private static String stringValue(Object value,String label) {
        if(!(value instanceof String)) throw new IllegalStateException(label+" is not a JSON string");
        return (String)value;
    }

    private static boolean booleanValue(Object value,String label) {
        if(!(value instanceof Boolean)) throw new IllegalStateException(label+" is not a JSON boolean");
        return ((Boolean)value).booleanValue();
    }

    private static long nonnegativeLong(Object value,String label) {
        if(!(value instanceof Long)) throw new IllegalStateException(label+" is not an integer JSON scalar");
        long number=((Long)value).longValue();
        if(number<0) throw new IllegalStateException(label+" is negative");
        return number;
    }

    private static String leafName(String path) {
        if(path==null||path.length()==0||path.indexOf('/')>=0||path.indexOf('\\')>=0||
                path.equals(".")||path.equals(".."))
            throw new IllegalStateException("Referenced artifact path is not one direct child name");
        return path;
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length>=3&&bytes[0]==(byte)0xef&&bytes[1]==(byte)0xbb&&bytes[2]==(byte)0xbf)
            throw new IOException("P8/P9 evidence has a missing byte array or UTF-8 BOM");
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try { return decoder.decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException error) { throw new IOException("Evidence is not strict UTF-8",error); }
    }

    private static boolean isSha(String value) {
        return value!=null&&SHA_PATTERN.matcher(value).matches();
    }

    private static String repeat(char value,int count) {
        char[] chars=new char[count];Arrays.fill(chars,value);return new String(chars);
    }

    private static void requireGroup(long nanos,String label) throws IOException {
        if(nanos<0||nanos>MAX_GROUP_NANOS) throw new IOException(label+" exceeded its five-second group bound");
    }

    private static long elapsed(long start,long end) {
        if(end<start) return Long.MAX_VALUE;
        return end-start;
    }

    private static long elapsedFromOffsets(long end,long start) {
        if(end<start) throw new IllegalStateException("P9 work offsets are not monotonic");
        return end-start;
    }

    private static long saturatedAdd(long first,long second) {
        if(first<0||second<0||Long.MAX_VALUE-first<second) return Long.MAX_VALUE;
        return first+second;
    }

    private static MessageDigest newSha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
    }

    private static String sha256(byte[] bytes) { return hex(newSha256().digest(bytes)); }

    private static String hex(byte[] bytes) {
        char[] digits="0123456789abcdef".toCharArray();char[] result=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++) {
            int value=bytes[i]&255;result[i*2]=digits[value>>>4];result[i*2+1]=digits[value&15];
        }
        return new String(result);
    }

    private static void readFully(InputStream input,byte[] buffer,int length) throws IOException {
        int read=0;
        while(read<length) {
            int count=input.read(buffer,read,length-read);
            if(count<0) throw new IOException("Unexpected EOF in bounded evidence chunk");
            if(count==0) continue;
            read+=count;
        }
    }

    private static void zero(byte[] bytes) {
        if(bytes!=null) Arrays.fill(bytes,(byte)0);
    }

    private static final class UnitMeter {
        private final String label;
        private int count, inGroup, finishedValue=-1;
        private long groupStarted, maximumGroupNanos;
        private boolean started, finished;

        UnitMeter(String name) { label=name; }

        void begin() {
            if(finished) throw new IllegalStateException(label+" meter was already finished");
            if(started) throw new IllegalStateException(label+" meter was started twice");
            started=true;groupStarted=System.nanoTime();
        }

        private void beginIfNeeded() {
            if(!started) { started=true;groupStarted=System.nanoTime(); }
        }

        void add(int entries) {
            if(entries<0) throw new IllegalArgumentException(label+" received negative entries");
            if(finished) throw new IllegalStateException(label+" meter received entries after finish");
            beginIfNeeded();
            if(entries>MAX_UNITS-count) throw new IllegalStateException(label+" exceeded 32768 entries");
            int remaining=entries;
            while(remaining>0) {
                int take=Math.min(remaining,CHUNK_BYTES-inGroup);
                count+=take;inGroup+=take;remaining-=take;
                if(inGroup==CHUNK_BYTES) {
                    recordGroup();inGroup=0;groupStarted=System.nanoTime();
                }
            }
        }

        int finishExpected(int expected) {
            if(expected<0||expected>MAX_UNITS) throw new IllegalArgumentException(label+" invalid expected count");
            if(finished) {
                if(finishedValue!=expected) throw new IllegalStateException(label+" was finished with a different expected count");
                return count;
            }
            if(!started) { started=true;groupStarted=System.nanoTime(); }
            if(count!=expected) throw new IllegalStateException(label+" entry count differs from its frozen schema");
            recordGroup();finished=true;finishedValue=expected;return count;
        }

        int finish() {
            if(finished) return count;
            if(!started) { started=true;groupStarted=System.nanoTime(); }
            recordGroup();finished=true;finishedValue=count;return count;
        }

        private void recordGroup() {
            long duration=elapsed(groupStarted,System.nanoTime());
            if(duration>maximumGroupNanos) maximumGroupNanos=duration;
            if(duration>MAX_GROUP_NANOS)
                throw new IllegalStateException(label+" 4096-entry unit exceeded five seconds");
        }

        int units() { return count; }
        long maximumGroupNanos() { return maximumGroupNanos; }
    }

    private static final class ByteMeter {
        private int count;
        void recordPassChunk(long started,int length) throws IOException {
            if(length<1||length>CHUNK_BYTES) throw new IOException("P9 byte pass has an invalid 4096-byte chunk");
            requireGroup(elapsed(started,System.nanoTime()),"P9 byte pass chunk");
            if(count>=MAX_UNITS) throw new IOException("P9 byte units exceeded the 32768 entry cap");
            count++;
        }
        int units() { return count; }
    }

    private static final class WipingByteArrayOutputStream extends ByteArrayOutputStream {
        WipingByteArrayOutputStream() { super(); }
        WipingByteArrayOutputStream(int size) { super(size); }
        void wipe() { Arrays.fill(buf,(byte)0);reset(); }
    }

    private static final class TeeCaptureOutputStream extends OutputStream {
        private final OutputStream downstream;
        private final int maximumLineBytes;
        private final WipingByteArrayOutputStream captured=new WipingByteArrayOutputStream(256);
        private long total;
        private int currentLineBytes;
        private boolean cleared;
        TeeCaptureOutputStream(OutputStream target,int maxLine) {
            if(target==null||maxLine<1) throw new IllegalArgumentException("Invalid bounded P8 stdout tee");
            downstream=target;maximumLineBytes=maxLine;
        }
        public void write(int value) throws IOException {
            if(cleared) throw new IOException("P8 stdout tee was already cleared");
            if(total>=MAX_OUTPUT_BYTES) throw new IOException("P8 stdout exceeded the native byte bound");
            int byteValue=value&255;
            if(byteValue=='\r') throw new IOException("P8 stdout marker contains CR");
            if(byteValue=='\n') currentLineBytes=0;
            else {
                if(currentLineBytes+1>=maximumLineBytes)
                    throw new IOException("P8 stdout line exceeded 64 KiB");
                currentLineBytes++;
            }
            captured.write(byteValue);downstream.write(byteValue);total++;
        }
        public void write(byte[] bytes,int offset,int length) throws IOException {
            if(bytes==null) throw new NullPointerException("bytes");
            if(offset<0||length<0||offset+length>bytes.length) throw new IndexOutOfBoundsException();
            for(int i=0;i<length;i++) write(bytes[offset+i]);
        }
        public void flush() throws IOException { downstream.flush(); }
        byte[] copyBytes() {
            if(cleared) throw new IllegalStateException("P8 stdout tee was cleared before capture");
            return captured.toByteArray();
        }
        long bytesWritten() { return total; }
        void clear() { captured.wipe();cleared=true; }
    }

    private static final class CountingTeeOutputStream extends OutputStream {
        private final OutputStream downstream;
        private long total;
        CountingTeeOutputStream(OutputStream target) {
            if(target==null) throw new IllegalArgumentException("Missing P8 stderr destination");
            downstream=target;
        }
        public void write(int value) throws IOException {
            if(total>=MAX_OUTPUT_BYTES) throw new IOException("P8 stderr exceeded the native byte bound");
            downstream.write(value);total++;
        }
        public void write(byte[] bytes,int offset,int length) throws IOException {
            if(bytes==null) throw new NullPointerException("bytes");
            if(offset<0||length<0||offset+length>bytes.length) throw new IndexOutOfBoundsException();
            if(length>MAX_OUTPUT_BYTES-total) throw new IOException("P8 stderr exceeded the native byte bound");
            downstream.write(bytes,offset,length);total+=length;
        }
        public void flush() throws IOException { downstream.flush(); }
        long bytesWritten() { return total; }
    }

    private static final class JsonWriter {
        static byte[] bytes(Object value) { return bytes(value,null); }

        static byte[] bytes(Object value,UnitMeter meter) {
            StringBuilder out=new StringBuilder(1024);
            appendValue(out,value,meter);
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }

        private static void appendValue(StringBuilder out,Object value,UnitMeter meter) {
            if(value==null) { leaf(meter);out.append("null");return; }
            if(value instanceof String) { leaf(meter);appendString(out,(String)value);return; }
            if(value instanceof Boolean) { leaf(meter);out.append(((Boolean)value).booleanValue()?"true":"false");return; }
            if(value instanceof Integer || value instanceof Long) { leaf(meter);out.append(value.toString());return; }
            if(value instanceof Map) {
                Map<?,?> map=(Map<?,?>)value;
                TreeMap<String,Object> sorted=new TreeMap<String,Object>();
                for(Map.Entry<?,?> entry:map.entrySet()) {
                    if(!(entry.getKey() instanceof String))
                        throw new IllegalArgumentException("JSON object contains a non-string key");
                    sorted.put((String)entry.getKey(),entry.getValue());
                }
                out.append('{');boolean first=true;
                for(Map.Entry<String,Object> entry:sorted.entrySet()) {
                    if(!first) out.append(',');first=false;
                    appendString(out,entry.getKey());out.append(':');
                    appendValue(out,entry.getValue(),meter);
                }
                out.append('}');return;
            }
            if(value instanceof List) {
                List<?> list=(List<?>)value;out.append('[');
                for(int i=0;i<list.size();i++) {
                    if(i!=0) out.append(',');appendValue(out,list.get(i),meter);
                }
                out.append(']');return;
            }
            throw new IllegalArgumentException("Unsupported JSON value type: "+value.getClass().getName());
        }

        private static void leaf(UnitMeter meter) { if(meter!=null) meter.add(1); }

        private static void appendString(StringBuilder out,String value) {
            validateUnicode(value);out.append('"');
            final char[] hex="0123456789abcdef".toCharArray();
            for(int i=0;i<value.length();i++) {
                char c=value.charAt(i);
                switch(c) {
                    case '"': out.append("\\\"");break;
                    case '\\': out.append("\\\\");break;
                    case '\b': out.append("\\b");break;
                    case '\f': out.append("\\f");break;
                    case '\n': out.append("\\n");break;
                    case '\r': out.append("\\r");break;
                    case '\t': out.append("\\t");break;
                    default:
                        if(c<0x20) out.append("\\u00").append(hex[(c>>>4)&15]).append(hex[c&15]);
                        else out.append(c);
                }
            }
            out.append('"');
        }

        static void validateUnicode(String value) {
            for(int i=0;i<value.length();i++) {
                char c=value.charAt(i);
                if(Character.isHighSurrogate(c)) {
                    if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(i+1)))
                        throw new IllegalArgumentException("Unpaired high surrogate in JSON string");
                    i++;
                } else if(Character.isLowSurrogate(c))
                    throw new IllegalArgumentException("Unpaired low surrogate in JSON string");
            }
        }
    }

    private static final class JsonParser {
        static Object parse(String text,UnitMeter meter) throws IOException {
            if(text==null) throw new IOException("JSON input is null");
            Parser parser=new Parser(text,meter);
            Object result=parser.value(0);parser.whitespace();
            if(parser.position!=text.length()) throw new IOException("JSON input has trailing bytes");
            return result;
        }

        private static final class Parser {
            final String text;
            final UnitMeter meter;
            int position;
            Parser(String value,UnitMeter units) { text=value;meter=units; }

            Object value(int depth) throws IOException {
                if(depth>64) throw new IOException("JSON nesting exceeds the bounded parser depth");
                whitespace();
                if(position>=text.length()) throw new IOException("JSON value is truncated");
                char c=text.charAt(position);
                if(c=='{') return object(depth+1);
                if(c=='[') return array(depth+1);
                if(c=='"') { String value=string();leaf();return value; }
                if(c=='t') { literal("true");leaf();return Boolean.TRUE; }
                if(c=='f') { literal("false");leaf();return Boolean.FALSE; }
                if(c=='n') { literal("null");leaf();return null; }
                if(c=='-'||(c>='0'&&c<='9')) { Long value=number();leaf();return value; }
                throw new IOException("JSON contains an invalid value token");
            }

            Map<String,Object> object(int depth) throws IOException {
                position++;whitespace();TreeMap<String,Object> result=new TreeMap<String,Object>();
                if(consume('}')) return result;
                for(;;) {
                    whitespace();
                    if(position>=text.length()||text.charAt(position)!='"')
                        throw new IOException("JSON object key is not a string");
                    String key=string();whitespace();expect(':');
                    if(result.containsKey(key)) throw new IOException("JSON object repeats a key");
                    Object item=value(depth);result.put(key,item);whitespace();
                    if(consume('}')) return result;
                    expect(',');
                }
            }

            List<Object> array(int depth) throws IOException {
                position++;whitespace();ArrayList<Object> result=new ArrayList<Object>();
                if(consume(']')) return result;
                for(;;) {
                    result.add(value(depth));whitespace();
                    if(consume(']')) return result;
                    expect(',');
                }
            }

            String string() throws IOException {
                expect('"');StringBuilder result=new StringBuilder();
                while(position<text.length()) {
                    char c=text.charAt(position++);
                    if(c=='"') {
                        String value=result.toString();
                        try { JsonWriter.validateUnicode(value); }
                        catch(IllegalArgumentException invalid) { throw new IOException("JSON string has invalid Unicode",invalid); }
                        return value;
                    }
                    if(c<0x20) throw new IOException("JSON string contains an unescaped control character");
                    if(c!='\\') { result.append(c);continue; }
                    if(position>=text.length()) throw new IOException("JSON escape is truncated");
                    char escape=text.charAt(position++);
                    switch(escape) {
                        case '"': result.append('"');break;
                        case '\\': result.append('\\');break;
                        case '/': result.append('/');break;
                        case 'b': result.append('\b');break;
                        case 'f': result.append('\f');break;
                        case 'n': result.append('\n');break;
                        case 'r': result.append('\r');break;
                        case 't': result.append('\t');break;
                        case 'u': result.append(unicodeEscape());break;
                        default: throw new IOException("JSON string contains an invalid escape");
                    }
                }
                throw new IOException("JSON string is unterminated");
            }

            char unicodeEscape() throws IOException {
                if(position+4>text.length()) throw new IOException("JSON Unicode escape is truncated");
                int value=0;
                for(int i=0;i<4;i++) {
                    int digit=Character.digit(text.charAt(position++),16);
                    if(digit<0) throw new IOException("JSON Unicode escape is malformed");
                    value=(value<<4)|digit;
                }
                return (char)value;
            }

            Long number() throws IOException {
                int start=position;
                if(consume('-')&&position>=text.length()) throw new IOException("JSON integer is truncated");
                if(position<text.length()&&text.charAt(position)=='0') {
                    position++;
                    if(position<text.length()&&text.charAt(position)>='0'&&text.charAt(position)<='9')
                        throw new IOException("JSON integer has a leading zero");
                } else {
                    if(position>=text.length()||text.charAt(position)<'1'||text.charAt(position)>'9')
                        throw new IOException("JSON integer has no digits");
                    while(position<text.length()&&text.charAt(position)>='0'&&text.charAt(position)<='9') position++;
                }
                if(position<text.length()) {
                    char next=text.charAt(position);
                    if(next=='.'||next=='e'||next=='E'||next=='+')
                        throw new IOException("JSON integer uses a noninteger representation");
                }
                try { return Long.valueOf(Long.parseLong(text.substring(start,position))); }
                catch(NumberFormatException invalid) { throw new IOException("JSON integer is outside signed 64-bit range",invalid); }
            }

            void literal(String value) throws IOException {
                if(!text.regionMatches(position,value,0,value.length()))
                    throw new IOException("JSON literal is malformed");
                position+=value.length();
            }

            void whitespace() {
                while(position<text.length()) {
                    char c=text.charAt(position);
                    if(c==' '||c=='\t'||c=='\r'||c=='\n') position++;
                    else break;
                }
            }

            void expect(char wanted) throws IOException {
                whitespace();
                if(position>=text.length()||text.charAt(position)!=wanted)
                    throw new IOException("JSON expected punctuation "+wanted);
                position++;
            }

            boolean consume(char wanted) {
                if(position<text.length()&&text.charAt(position)==wanted) { position++;return true; }
                return false;
            }

            void leaf() { if(meter!=null) meter.add(1); }
        }
    }
}
