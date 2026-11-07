#!/usr/bin/env python3
"""Build the frozen P8 native-only geometric-reason overlays."""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUTPUT_DIR = ROOT / "overlays-r1"
CRLF = b"\r\n"

ROUTER_REL = Path("src/com/lushprojects/circuitjs1/client/PcbNetRouter.java")
RULES_REL = Path("src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java")
P6_ROUTER_BYTES = 131787
P6_ROUTER_SHA256 = "0432eed2ac4e84c647aee867cc1856bff7969280b094794bc0acb95907d66d94"
PRODUCTION_ROUTER_BYTES = 129081
PRODUCTION_ROUTER_SHA256 = "92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec"
PRODUCTION_RULES_BYTES = 24212
PRODUCTION_RULES_SHA256 = "b04f9906d726117412d62b6022ea5b8e2f26bf111063292b0d7560f51b0fb8cf"
PLAN_SHA256 = "0bf95e2de4d2662f7d57604c910dab89081abcc11a7597288b19ffdaecc9dc93"
API_SCHEMA_SHA256 = "915583d2594a74135bd3269c74a8c190cfb1d47c9d0aa28062b24578e17de7ff"

REASON_SITES = (
    "START_ESCAPE_DIRECTION",
    "END_ESCAPE_DIRECTION",
    "DOMAIN_UNKNOWN_NET",
    "DOMAIN_CORRIDOR_OVERLAP",
    "DOMAIN_MEMBERSHIP_SIDE",
    "EMPTY_FACE_RESERVATION",
    "ADJACENT_RESERVATION",
    "PAD_ESCAPE_INTERSECTION",
    "PROFILED_PAD_TOUCH",
    "COMPONENT_COURTYARD",
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_bytes(text: str) -> bytes:
    return text.encode("utf-8").replace(b"\n", CRLF)


def _validate_source(raw: bytes, expected_bytes: int, expected_sha: str, label: str) -> None:
    if not isinstance(raw, bytes):
        raise TypeError(label + " source must be bytes")
    if len(raw) != expected_bytes or sha256(raw) != expected_sha:
        raise ValueError(label + " bytes do not match the frozen source pin")
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(label + " source is not UTF-8") from exc
    if b"\xef\xbb\xbf" in raw[:3] or CRLF not in raw or b"\n" in raw.replace(CRLF, b""):
        raise ValueError(label + " newline/BOM shape differs from the frozen source")


def _add_edit(edits, source: bytes, anchor: bytes, replacement: bytes,
              label: str, *, after: bool = False) -> None:
    if source.count(anchor) != 1:
        raise ValueError("expected unique source fragment: " + label)
    start = source.index(anchor)
    if after:
        start += len(anchor)
    end = start if after else start + len(anchor)
    edits.append((start, end, replacement, label))


def _apply_edits(original: bytes, edits):
    edits.sort(key=lambda row: row[0])
    for first, second in zip(edits, edits[1:]):
        if first[1] > second[0]:
            raise ValueError("overlay edit fragments overlap")

    output = bytearray()
    preserved = []
    edit_records = []
    cursor = 0
    for start, end, replacement, label in edits:
        unchanged = original[cursor:start]
        output_start = len(output)
        output.extend(unchanged)
        if unchanged:
            preserved.append({
                "sourceOffset": cursor,
                "overlayOffset": output_start,
                "bytes": len(unchanged),
                "sha256": sha256(unchanged),
            })
        replacement_start = len(output)
        output.extend(replacement)
        edit_records.append({
            "label": label,
            "sourceOffset": start,
            "sourceBytes": end - start,
            "sourceSha256": sha256(original[start:end]),
            "overlayOffset": replacement_start,
            "overlayBytes": len(replacement),
            "overlaySha256": sha256(replacement),
        })
        cursor = end
    tail = original[cursor:]
    tail_start = len(output)
    output.extend(tail)
    if tail:
        preserved.append({
            "sourceOffset": cursor,
            "overlayOffset": tail_start,
            "bytes": len(tail),
            "sha256": sha256(tail),
        })
    overlay = bytes(output)

    expected_preserved = len(original) - sum(row["sourceBytes"] for row in edit_records)
    expected_size = len(original) + sum(
        row["overlayBytes"] - row["sourceBytes"] for row in edit_records)
    if sum(row["bytes"] for row in preserved) != expected_preserved:
        raise ValueError("unchanged-byte accounting mismatch")
    if len(overlay) != expected_size:
        raise ValueError("overlay size accounting mismatch")
    for row in preserved:
        source_start = row["sourceOffset"]
        output_start = row["overlayOffset"]
        length = row["bytes"]
        if original[source_start:source_start + length] != overlay[output_start:output_start + length]:
            raise ValueError("non-whitelisted source bytes changed")
    return overlay, preserved, edit_records


def _between(raw: bytes, start: bytes, end: bytes) -> bytes:
    if raw.count(start) != 1 or raw.count(end) != 1:
        raise ValueError("expected unique preservation boundary")
    first = raw.index(start)
    last = raw.index(end, first + len(start))
    if last < first:
        raise ValueError("preservation boundaries are reversed")
    return raw[first:last]


REASON_CLASS_AND_FIELD = source_bytes("""        interface FixedGraphReasonObserver {
            void onRejected(FixedGraphReason reason);
        }
        static final class FixedGraphReason {
            final String site, ownerKind;
            final int stage;
            final String startPadId, endpointPadId;
            final int fromGridX, fromGridY, toGridX, toGridY;
            final String ownerId, ownerNet;
            final int ownerIndex;
            final String reservationState, reservationValue;
            final String domainLow, domainHigh;
            final int domainHorizontal, domainLowLimit, domainHighLimit, domainFirst, domainLast;
            final int startEscape, endEscape, underpassEnabled;
            FixedGraphReason(String site, String ownerKind, int stage,
                    String startPadId, String endpointPadId,
                    int fromGridX, int fromGridY, int toGridX, int toGridY,
                    String ownerId, String ownerNet, int ownerIndex,
                    String reservationState, String reservationValue,
                    String domainLow, String domainHigh,
                    int domainHorizontal, int domainLowLimit, int domainHighLimit,
                    int domainFirst, int domainLast,
                    int startEscape, int endEscape, int underpassEnabled) {
                if (site == null || ownerKind == null || startPadId == null)
                    throw new IllegalArgumentException("Incomplete fixed graph reason");
                this.site=site; this.ownerKind=ownerKind; this.stage=stage;
                this.startPadId=startPadId; this.endpointPadId=endpointPadId;
                this.fromGridX=fromGridX; this.fromGridY=fromGridY;
                this.toGridX=toGridX; this.toGridY=toGridY;
                this.ownerId=ownerId; this.ownerNet=ownerNet; this.ownerIndex=ownerIndex;
                this.reservationState=reservationState; this.reservationValue=reservationValue;
                this.domainLow=domainLow; this.domainHigh=domainHigh;
                this.domainHorizontal=domainHorizontal; this.domainLowLimit=domainLowLimit;
                this.domainHighLimit=domainHighLimit; this.domainFirst=domainFirst;
                this.domainLast=domainLast; this.startEscape=startEscape;
                this.endEscape=endEscape; this.underpassEnabled=underpassEnabled;
            }
        }
        private FixedGraphReasonObserver fixedGraphReasonObserverForStudy;
""")

REASON_HELPERS = source_bytes("""        void attachFixedGraphReasonObserverForStudy(FixedGraphReasonObserver observer,
                Router expectedQuery,Router live,Router pristine,boolean comparisonPassed) {
            if(observer==null || expectedQuery==null || live==null || pristine==null ||
                    expectedQuery==live || expectedQuery==pristine || live==pristine)
                throw new IllegalArgumentException("Invalid fixed graph reason observer roles");
            if(this!=expectedQuery || this==live || this==pristine || !comparisonPassed)
                throw new IllegalArgumentException("Fixed graph reason observer requires a compared query copy");
            if(fixedGraphReasonObserverForStudy!=null)
                throw new IllegalStateException("Fixed graph reason observer is already attached");
            fixedGraphReasonObserverForStudy=observer;
        }
        void detachFixedGraphReasonObserverForStudy() {
            fixedGraphReasonObserverForStudy=null;
        }
        boolean hasFixedGraphReasonObserverForStudy() {
            return fixedGraphReasonObserverForStudy!=null;
        }
        private void reportFixedGraphReasonForStudy(String site,String ownerKind,int stage,
                String startPadId,String endpointPadId,int fromX,int fromY,int toX,int toY,
                String ownerId,String ownerNet,int ownerIndex,
                String reservationState,String reservationValue,
                String domainLow,String domainHigh,int domainHorizontal,
                int domainLowLimit,int domainHighLimit,int domainFirst,int domainLast,
                int startEscape,int endEscape,int underpassEnabled) {
            FixedGraphReasonObserver callback=fixedGraphReasonObserverForStudy;
            if(callback!=null)
                callback.onRejected(new FixedGraphReason(site,ownerKind,stage,startPadId,endpointPadId,
                    fromX,fromY,toX,toY,ownerId,ownerNet,ownerIndex,
                    reservationState,reservationValue,domainLow,domainHigh,domainHorizontal,
                    domainLowLimit,domainHighLimit,domainFirst,domainLast,
                    startEscape,endEscape,underpassEnabled));
        }
        private void reportPadFixedGraphReasonForStudy(String site,int stage,
                PcbPadPlacement startPad,PcbPadPlacement endPad,int fromX,int fromY,int toX,int toY,
                PcbPadPlacement ownerPad) {
            FixedGraphReasonObserver callback=fixedGraphReasonObserverForStudy;
            if(callback==null) return;
            callback.onRejected(new FixedGraphReason(site,"PAD",stage,startPad.getPadId(),
                endPad==null?null:endPad.getPadId(),fromX,fromY,toX,toY,
                ownerPad.getPadId(),null,-1,null,null,null,null,-1,0,0,0,0,-1,-1,-1));
        }
        private void reportIndexedPadFixedGraphReasonForStudy(String site,int stage,
                PcbPadPlacement startPad,PcbPadPlacement endPad,int fromX,int fromY,int toX,int toY,
                int ownerIndex) {
            FixedGraphReasonObserver callback=fixedGraphReasonObserverForStudy;
            if(callback==null) return;
            PcbPadPlacement ownerPad=pads[ownerIndex];
            callback.onRejected(new FixedGraphReason(site,"PAD",stage,startPad.getPadId(),
                endPad==null?null:endPad.getPadId(),fromX,fromY,toX,toY,
                ownerPad.getPadId(),padNets[ownerIndex],ownerIndex,
                null,null,null,null,-1,0,0,0,0,-1,-1,-1));
        }
        private void reportReservationFixedGraphReasonForStudy(String site,int fromX,int fromY,
                int toX,int toY,PcbPadPlacement startPad,PcbPadPlacement endPad,String reserved) {
            FixedGraphReasonObserver callback=fixedGraphReasonObserverForStudy;
            if(callback==null) return;
            boolean mixed=reserved.length()==0;
            callback.onRejected(new FixedGraphReason(site,"CACHED_RESERVATION",STAGE_TRAVERSE_REJECTED,
                startPad.getPadId(),endPad==null?null:endPad.getPadId(),fromX,fromY,toX,toY,
                null,mixed?null:reserved,-1,mixed?"MIXED":"SINGLE",reserved,
                null,null,-1,0,0,0,0,-1,-1,-1));
        }
        private void reportComponentFixedGraphReasonForStudy(PcbPadPlacement startPad,
                PcbPadPlacement endPad,int fromX,int fromY,int toX,int toY,
                PcbComponentPlacement component,int ownerIndex,
                boolean startEscape,boolean endEscape) {
            FixedGraphReasonObserver callback=fixedGraphReasonObserverForStudy;
            if(callback==null) return;
            callback.onRejected(new FixedGraphReason("COMPONENT_COURTYARD","COMPONENT",
                STAGE_TRAVERSE_REJECTED,startPad.getPadId(),endPad==null?null:endPad.getPadId(),
                fromX,fromY,toX,toY,component.getComponentId(),null,ownerIndex,
                null,null,null,null,-1,0,0,0,0,startEscape?1:0,endEscape?1:0,
                factoryUnderpasses?1:0));
        }
        private PcbTwoLayerRules.ReasonObserver fixedGraphDomainReasonObserverForStudy(
                final int fromX,final int fromY,final int toX,final int toY,
                final PcbPadPlacement startPad,final PcbPadPlacement endPad,final String net) {
            if(fixedGraphReasonObserverForStudy==null) return null;
            return new PcbTwoLayerRules.ReasonObserver() {
                public void onRejected(String site,int boundaryIndex,String low,String high,
                        boolean horizontal,int lowLimit,int highLimit,int first,int last) {
                    boolean policy=boundaryIndex<0;
                    FixedGraphReasonObserver callback=fixedGraphReasonObserverForStudy;
                    if(callback==null) return;
                    callback.onRejected(new FixedGraphReason(site,policy?"DOMAIN_POLICY":"DOMAIN_BOUNDARY",
                        STAGE_TRAVERSE_REJECTED,startPad.getPadId(),
                        endPad==null?null:endPad.getPadId(),fromX,fromY,toX,toY,
                        null,policy?net:null,boundaryIndex,null,null,low,high,
                        policy?-1:(horizontal?1:0),lowLimit,highLimit,first,last,-1,-1,-1));
                }
            };
        }
""")


def _router_guard_edits(original: bytes, edits) -> None:
    start_escape = source_bytes("""            if (startPad.getMountingSide()==layer.getFace() && startPad.getEscapeLength() > 0 && startDistance>=0 && startDistance<startPad.getEscapeLength() &&
                    startDx*startPad.getEscapeDy()==startDy*startPad.getEscapeDx() &&
                    (directionX(direction) != startPad.getEscapeDx() ||
                    directionY(direction) != startPad.getEscapeDy()))
                return false;""")
    start_escape_new = source_bytes("""            if (startPad.getMountingSide()==layer.getFace() && startPad.getEscapeLength() > 0 && startDistance>=0 && startDistance<startPad.getEscapeLength() &&
                    startDx*startPad.getEscapeDy()==startDy*startPad.getEscapeDx() &&
                    (directionX(direction) != startPad.getEscapeDx() ||
                    directionY(direction) != startPad.getEscapeDy())) {
                reportPadFixedGraphReasonForStudy("START_ESCAPE_DIRECTION",
                    STAGE_LEGAL_MOVE_REJECTED,startPad,endPad,fromX,fromY,nextX,nextY,startPad);
                return false;
            }""")
    _add_edit(edits, original, start_escape, start_escape_new, "start-escape-first-false-site")

    end_escape = source_bytes("""            if (endPad!=null && endPad.getMountingSide()==layer.getFace() && endPad.getEscapeLength() > 0 &&
                    (nextX-endX)*endPad.getEscapeDy()==(nextY-endY)*endPad.getEscapeDx() &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID>=0 &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID<endPad.getEscapeLength() &&
                    (directionX(direction) != -endPad.getEscapeDx() ||
                    directionY(direction) != -endPad.getEscapeDy()))
                return false;""")
    end_escape_new = source_bytes("""            if (endPad!=null && endPad.getMountingSide()==layer.getFace() && endPad.getEscapeLength() > 0 &&
                    (nextX-endX)*endPad.getEscapeDy()==(nextY-endY)*endPad.getEscapeDx() &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID>=0 &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID<endPad.getEscapeLength() &&
                    (directionX(direction) != -endPad.getEscapeDx() ||
                    directionY(direction) != -endPad.getEscapeDy())) {
                reportPadFixedGraphReasonForStudy("END_ESCAPE_DIRECTION",
                    STAGE_LEGAL_MOVE_REJECTED,startPad,endPad,fromX,fromY,nextX,nextY,endPad);
                return false;
            }""")
    _add_edit(edits, original, end_escape, end_escape_new, "end-escape-first-false-site")

    domain_call = source_bytes("                if (!domainRules.permits(net,stroke)) return false;")
    domain_call_new = source_bytes("""                if (!domainRules.permits(net,stroke,
                        fixedGraphDomainReasonObserverForStudy(fromX,fromY,toX,toY,
                            startPad,endPad,net))) return false;""")
    _add_edit(edits, original, domain_call, domain_call_new, "per-call-domain-reason-adapter")

    empty_face = source_bytes("                return reserved==null || reserved.equals(net);")
    empty_face_new = source_bytes("""                boolean allowed=reserved==null || reserved.equals(net);
                if(!allowed)
                    reportReservationFixedGraphReasonForStudy("EMPTY_FACE_RESERVATION",
                        fromX,fromY,toX,toY,startPad,endPad,reserved);
                return allowed;""")
    _add_edit(edits, original, empty_face, empty_face_new, "empty-face-reservation-single-evaluation")

    adjacent_reservation = source_bytes(
        "                if(reserved!=null && (reserved.length()==0 || !reserved.equals(net))) return false;")
    adjacent_reservation_new = source_bytes("""                if(reserved!=null && (reserved.length()==0 || !reserved.equals(net))) {
                    reportReservationFixedGraphReasonForStudy("ADJACENT_RESERVATION",
                        fromX,fromY,toX,toY,startPad,endPad,reserved);
                    return false;
                }""")
    _add_edit(edits, original, adjacent_reservation, adjacent_reservation_new,
              "adjacent-reservation-first-false-site")

    escape_reservation = source_bytes(
        "                    if(escapeReservations[index].intersects(move)) return false;")
    escape_reservation_new = source_bytes("""                    if(escapeReservations[index].intersects(move)) {
                        reportIndexedPadFixedGraphReasonForStudy("PAD_ESCAPE_INTERSECTION",
                            STAGE_TRAVERSE_REJECTED,startPad,endPad,fromX,fromY,toX,toY,index);
                        return false;
                    }""")
    _add_edit(edits, original, escape_reservation, escape_reservation_new,
              "pad-escape-reservation-first-false-site")

    profiled_touch = source_bytes(
        "                    if(checkProfiledPadTouches && PcbConductorBuilder.touch(stroke,padBounds[index]))\n                        return false;")
    profiled_touch_new = source_bytes("""                    if(checkProfiledPadTouches && PcbConductorBuilder.touch(stroke,padBounds[index])) {
                        reportIndexedPadFixedGraphReasonForStudy("PROFILED_PAD_TOUCH",
                            STAGE_TRAVERSE_REJECTED,startPad,endPad,fromX,fromY,toX,toY,index);
                        return false;
                    }""")
    _add_edit(edits, original, profiled_touch, profiled_touch_new,
              "profiled-pad-touch-first-false-site")

    courtyard = source_bytes("""                if (!startEscape && !endEscape && !(factoryUnderpasses && component.permitsUnderpass(layer, stroke)))
                    return false;""")
    courtyard_new = source_bytes("""                if (!startEscape && !endEscape && !(factoryUnderpasses && component.permitsUnderpass(layer, stroke))) {
                    reportComponentFixedGraphReasonForStudy(startPad,endPad,
                        fromX,fromY,toX,toY,component,index,startEscape,endEscape);
                    return false;
                }""")
    _add_edit(edits, original, courtyard, courtyard_new, "component-courtyard-first-false-site")


P6_BOUNDS_AND_ENDPOINT_LOOP = source_bytes("""        boolean permitsLayerStep(int ax,int ay,int bx,int by,String net,
                PcbPadPlacement start,PcbPadPlacement end) {
            return permitsLayerStep(ax,ay,bx,by,net,start,end,null);
        }
        /** P07 may terminate only at these already-published plated pads, never net peers. */
        boolean permitsLayerStep(int ax,int ay,int bx,int by,String net,
                PcbPadPlacement start,PcbPadPlacement end,PcbPadPlacement[] reachedTerminals) {
            int x=gridX(ax),y=gridY(ay),nx=gridX(bx),ny=gridY(by);
            if(x<0 || y<0 || nx<0 || ny<0 || x>=gridWidth || nx>=gridWidth ||
                    y>=gridHeight || ny>=gridHeight) return false;
            int direction=nx>x?1:nx<x?3:ny>y?2:0;
            if(permitsLayerStepToEndpoint(x,y,nx,ny,direction,net,start,end)) return true;
            if(reachedTerminals==null) return false;
            for(PcbPadPlacement reached:reachedTerminals) {
                if(reached==null) throw new IllegalArgumentException("Missing reached P07 terminal");
                if(reached.getPadId().equals(start.getPadId()) ||
                        end!=null && reached.getPadId().equals(end.getPadId())) continue;
                if(permitsLayerStepToEndpoint(x,y,nx,ny,direction,net,start,reached)) return true;
            }
            return false;
        }
""")

P6_COARSE_SEAM = source_bytes("""        void attachFixedGraphStageObserverForStudy(FixedGraphStageObserver observer,
                Router expectedQuery,Router live,Router pristine,boolean comparisonPassed) {
            if(observer==null || expectedQuery==null || live==null || pristine==null ||
                    expectedQuery==live || expectedQuery==pristine || live==pristine)
                throw new IllegalArgumentException("Invalid fixed graph stage observer roles");
            if(this!=expectedQuery || this==live || this==pristine || !comparisonPassed)
                throw new IllegalArgumentException("Fixed graph stage observer requires a compared query copy");
            if(fixedGraphStageObserverForStudy!=null)
                throw new IllegalStateException("Fixed graph stage observer is already attached");
            fixedGraphStageObserverForStudy=observer;
        }
        void detachFixedGraphStageObserverForStudy() {
            fixedGraphStageObserverForStudy=null;
        }
        boolean hasFixedGraphStageObserverForStudy() {
            return fixedGraphStageObserverForStudy!=null;
        }
        private boolean reportFixedGraphStageForStudy(String net,PcbPadPlacement start,
                PcbPadPlacement end,int x,int y,int nx,int ny,int direction,int stage,
                boolean allowed) {
            FixedGraphStageObserver callback=fixedGraphStageObserverForStudy;
            if(callback!=null)
                callback.onEndpoint(net,start.getPadId(),end==null?null:end.getPadId(),
                    x,y,nx,ny,direction,stage);
            return allowed;
        }
        private boolean permitsLayerStepToEndpoint(int x,int y,int nx,int ny,int direction,
                String net,PcbPadPlacement start,PcbPadPlacement end) {
            if(!isLegalMove(x,y,nx,ny,direction,gridX(start.getX()),gridY(start.getY()),
                    end==null?-1:gridX(end.getX()),end==null?-1:gridY(end.getY()),start,end))
                return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                    STAGE_LEGAL_MOVE_REJECTED,false);
            if(!canTraverse(x,y,nx,ny,start,end,net))
                return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                    STAGE_TRAVERSE_REJECTED,false);
            if(!canOccupy(nx,ny,net,start,end))
                return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                    STAGE_OCCUPANCY_REJECTED,false);
            return reportFixedGraphStageForStudy(net,start,end,x,y,nx,ny,direction,
                STAGE_ALLOWED,true);
        }
""")


def _build_router_overlay(raw: bytes):
    _validate_source(raw, P6_ROUTER_BYTES, P6_ROUTER_SHA256, "P6 PcbNetRouter overlay")
    edits = []
    field_anchor = source_bytes("        private FixedGraphStageObserver fixedGraphStageObserverForStudy;\n")
    _add_edit(edits, raw, field_anchor, REASON_CLASS_AND_FIELD,
              "fixed-graph-reason-api-and-default-null-field", after=True)

    _add_edit(edits, raw, P6_BOUNDS_AND_ENDPOINT_LOOP, REASON_HELPERS,
              "fixed-graph-reason-attachment-and-recording-api", after=True)
    _router_guard_edits(raw, edits)
    overlay, preserved, edit_records = _apply_edits(raw, edits)

    if raw.count(P6_BOUNDS_AND_ENDPOINT_LOOP) != 1 or overlay.count(P6_BOUNDS_AND_ENDPOINT_LOOP) != 1:
        raise ValueError("P6 Router bounds or endpoint loop changed")
    if raw.count(P6_COARSE_SEAM) != 1 or overlay.count(P6_COARSE_SEAM) != 1:
        raise ValueError("P6 coarse observer seam changed")
    can_occupy = _between(raw, source_bytes("        private boolean canOccupy("),
                          source_bytes("        private boolean canTraverse("))
    if overlay.count(can_occupy) != 1:
        raise ValueError("P6 canOccupy body changed")
    copy_block = _between(overlay, source_bytes("        private Router(Router source,boolean observationShell)"),
                          source_bytes("        private static PcbRoutingWork copyObservationWork"))
    if b"fixedGraphStageObserverForStudy" in copy_block or \
            b"fixedGraphReasonObserverForStudy" in copy_block or \
            b"copy.fixedGraphReasonObserverForStudy" in overlay:
        raise ValueError("fixed graph copies must leave both observer fields at default null")
    if b"fixedGraphReasonObserverForStudy" in raw:
        raise ValueError("unexpected pre-existing P8 reason observer")

    original_guard_fragments = (
        source_bytes("""if (startPad.getMountingSide()==layer.getFace() && startPad.getEscapeLength() > 0 && startDistance>=0 && startDistance<startPad.getEscapeLength() &&
                    startDx*startPad.getEscapeDy()==startDy*startPad.getEscapeDx() &&
                    (directionX(direction) != startPad.getEscapeDx() ||
                    directionY(direction) != startPad.getEscapeDy()))"""),
        source_bytes("""if (endPad!=null && endPad.getMountingSide()==layer.getFace() && endPad.getEscapeLength() > 0 &&
                    (nextX-endX)*endPad.getEscapeDy()==(nextY-endY)*endPad.getEscapeDx() &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID>=0 &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID<endPad.getEscapeLength() &&
                    (directionX(direction) != -endPad.getEscapeDx() ||
                    directionY(direction) != -endPad.getEscapeDy()))"""),
        source_bytes("reserved==null || reserved.equals(net)"),
        source_bytes("if(reserved!=null && (reserved.length()==0 || !reserved.equals(net)))"),
        source_bytes("if(escapeReservations[index].intersects(move))"),
        source_bytes("if(checkProfiledPadTouches && PcbConductorBuilder.touch(stroke,padBounds[index]))"),
        source_bytes("if (!startEscape && !endEscape && !(factoryUnderpasses && component.permitsUnderpass(layer, stroke)))"),
    )
    for fragment in original_guard_fragments:
        if raw.count(fragment) != 1 or overlay.count(fragment) != 1:
            raise ValueError("an original Router guard expression changed or was duplicated")

    reason_literals = (
        "START_ESCAPE_DIRECTION", "END_ESCAPE_DIRECTION", "EMPTY_FACE_RESERVATION",
        "ADJACENT_RESERVATION", "PAD_ESCAPE_INTERSECTION", "PROFILED_PAD_TOUCH",
        "COMPONENT_COURTYARD",
    )
    for site in reason_literals:
        if overlay.count(('"' + site + '"').encode("ascii")) != 1:
            raise ValueError("Router reason site is missing or duplicated: " + site)
    if overlay.count(b"reserved==null || reserved.equals(net)") != 1:
        raise ValueError("empty-face cached guard must be stored and evaluated exactly once")
    return overlay, preserved, edit_records


def make_router_overlay(raw: bytes) -> bytes:
    """Pure byte transform from the accepted P6 overlay to the P8 Router overlay."""
    return _build_router_overlay(raw)[0]


RULES_OBSERVER_INTERFACE = source_bytes("""    interface ReasonObserver {
        void onRejected(String site,int boundaryIndex,String low,String high,boolean horizontal,
                int lowLimit,int highLimit,int first,int last);
    }
""")

RULES_METHODS_OLD = source_bytes("""    /** Isolation corridors span the whole board and BOTH copper faces. */
    boolean permits(String net,Rectangle copper) {
        java.util.Set<String> domains=netDomains.get(net);
        if(domains==null) return false;
        return permitsDomains(domains,copper);
    }
    private boolean permitsDomain(String domain,Rectangle copper) {
        java.util.TreeSet<String> domains=new java.util.TreeSet<String>();
        domains.add(domain);
        return permitsDomains(domains,copper);
    }
    private boolean permitsDomains(java.util.Set<String> domains,Rectangle copper) {
        for(Boundary boundary:boundaries) {
            int first=boundary.horizontal?copper.x:copper.y;
            int last=first+(boundary.horizontal?copper.width:copper.height);
            if(last>boundary.lowLimit && first<boundary.highLimit) return false;
            if(last>boundary.lowLimit && domains.contains(boundary.low) ||
                    first<boundary.highLimit && domains.contains(boundary.high)) return false;
        }
        return true;
    }
""")

RULES_METHODS_NEW = source_bytes("""    /** Isolation corridors span the whole board and BOTH copper faces. */
    boolean permits(String net,Rectangle copper) {
        return permits(net,copper,null);
    }
    boolean permits(String net,Rectangle copper,ReasonObserver observer) {
        java.util.Set<String> domains=netDomains.get(net);
        if(domains==null) {
            if(observer!=null)
                observer.onRejected("DOMAIN_UNKNOWN_NET",-1,null,null,false,0,0,0,0);
            return false;
        }
        return permitsDomains(domains,copper,observer);
    }
    private boolean permitsDomain(String domain,Rectangle copper) {
        java.util.TreeSet<String> domains=new java.util.TreeSet<String>();
        domains.add(domain);
        return permitsDomains(domains,copper);
    }
    private boolean permitsDomains(java.util.Set<String> domains,Rectangle copper) {
        return permitsDomains(domains,copper,null);
    }
    private boolean permitsDomains(java.util.Set<String> domains,Rectangle copper,
            ReasonObserver observer) {
        int boundaryIndex=0;
        for(Boundary boundary:boundaries) {
            int first=boundary.horizontal?copper.x:copper.y;
            int last=first+(boundary.horizontal?copper.width:copper.height);
            if(last>boundary.lowLimit && first<boundary.highLimit) {
                if(observer!=null)
                    observer.onRejected("DOMAIN_CORRIDOR_OVERLAP",boundaryIndex,
                        boundary.low,boundary.high,boundary.horizontal,boundary.lowLimit,
                        boundary.highLimit,first,last);
                return false;
            }
            if(last>boundary.lowLimit && domains.contains(boundary.low) ||
                    first<boundary.highLimit && domains.contains(boundary.high)) {
                if(observer!=null)
                    observer.onRejected("DOMAIN_MEMBERSHIP_SIDE",boundaryIndex,
                        boundary.low,boundary.high,boundary.horizontal,boundary.lowLimit,
                        boundary.highLimit,first,last);
                return false;
            }
            boundaryIndex++;
        }
        return true;
    }
""")


def _build_rules_overlay(raw: bytes):
    _validate_source(raw, PRODUCTION_RULES_BYTES, PRODUCTION_RULES_SHA256,
                     "production PcbTwoLayerRules")
    edits = []
    class_anchor = source_bytes("final class PcbTwoLayerRules {\n")
    _add_edit(edits, raw, class_anchor, RULES_OBSERVER_INTERFACE,
              "per-call-domain-reason-interface", after=True)
    _add_edit(edits, raw, RULES_METHODS_OLD, RULES_METHODS_NEW,
              "observer-aware-single-domain-predicate-body")
    overlay, preserved, edit_records = _apply_edits(raw, edits)

    original_guards = (
        (source_bytes("if(last>boundary.lowLimit && first<boundary.highLimit)"), 2),
        (source_bytes("""if(last>boundary.lowLimit && domains.contains(boundary.low) ||
                    first<boundary.highLimit && domains.contains(boundary.high))"""), 1),
    )
    for guard, expected_count in original_guards:
        if raw.count(guard) != expected_count or overlay.count(guard) != expected_count:
            raise ValueError("an original domain condition changed or was duplicated")
    if b"private ReasonObserver" in overlay or b"private final ReasonObserver" in overlay:
        raise ValueError("the shared rules object must not retain a reason observer")
    if overlay.count(b"\"DOMAIN_UNKNOWN_NET\"") != 1 or \
            overlay.count(b"\"DOMAIN_CORRIDOR_OVERLAP\"") != 1 or \
            overlay.count(b"\"DOMAIN_MEMBERSHIP_SIDE\"") != 1:
        raise ValueError("domain reason sites are missing or duplicated")
    if overlay.count(b"boundaryIndex++") != 1:
        raise ValueError("boundary index must advance once after each nonrejecting boundary")
    return overlay, preserved, edit_records


def make_rules_overlay(raw: bytes) -> bytes:
    """Pure byte transform from the frozen E production rules source."""
    return _build_rules_overlay(raw)[0]


def _diff(original: bytes, overlay: bytes, source_name: str, overlay_name: str) -> bytes:
    before = original.decode("utf-8").splitlines()
    after = overlay.decode("utf-8").splitlines()
    text = "\n".join(difflib.unified_diff(
        before, after, fromfile=source_name, tofile=overlay_name, lineterm=""))
    return (text + "\n").encode("utf-8")


def _write_exact(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() == data:
            return
        raise SystemExit("refusing to replace an existing nonmatching artifact: " + str(path))
    path.write_bytes(data)


def build_artifacts(e_source_root: Path, p6_router_overlay: Path) -> dict:
    e_root = e_source_root.resolve(strict=True)
    p6_router_path = p6_router_overlay.resolve(strict=True)
    if not e_root.is_dir() or not p6_router_path.is_file():
        raise SystemExit("explicit E source root and P6 Router overlay file are required")

    production_router = (e_root / ROUTER_REL).read_bytes()
    _validate_source(production_router, PRODUCTION_ROUTER_BYTES,
                     PRODUCTION_ROUTER_SHA256, "E production PcbNetRouter")
    p6_router = p6_router_path.read_bytes()
    _validate_source(p6_router, P6_ROUTER_BYTES, P6_ROUTER_SHA256,
                     "accepted P6 PcbNetRouter overlay")
    rules = (e_root / RULES_REL).read_bytes()
    _validate_source(rules, PRODUCTION_RULES_BYTES, PRODUCTION_RULES_SHA256,
                     "E production PcbTwoLayerRules")
    plan = (ROOT / "PLAN_R1.md").read_bytes()
    api_schema = (ROOT / "API_SCHEMA_R1.md").read_bytes()
    if sha256(plan) != PLAN_SHA256 or sha256(api_schema) != API_SCHEMA_SHA256:
        raise SystemExit("frozen P8 plan/API schema SHA-256 mismatch")

    try:
        router_overlay, router_preserved, router_edits = _build_router_overlay(p6_router)
        rules_overlay, rules_preserved, rules_edits = _build_rules_overlay(rules)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc

    combined = router_overlay + rules_overlay
    site_counts = {
        site: combined.count(('"' + site + '"').encode("ascii"))
        for site in REASON_SITES
    }
    if any(count != 1 for count in site_counts.values()):
        raise SystemExit("the combined overlay does not emit each of the ten sites once")

    diff = _diff(p6_router, router_overlay,
                 "P6/overlays-r1/PcbNetRouter.java",
                 "P8/overlays-r1/PcbNetRouter.java")
    diff += _diff(rules, rules_overlay,
                  "E/src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java",
                  "P8/overlays-r1/PcbTwoLayerRules.java")
    builder_sha = sha256(Path(__file__).read_bytes())

    router_path = OUTPUT_DIR / "PcbNetRouter.java"
    rules_path = OUTPUT_DIR / "PcbTwoLayerRules.java"
    diff_path = OUTPUT_DIR / "overlay.diff"
    manifest_path = OUTPUT_DIR / "overlay-manifest.json"
    manifest = {
        "schema": "tsj-q100-geometric-reasons-overlay-manifest-r1",
        "builder": {"path": "build_overlay.py", "sha256": builder_sha},
        "frozenDocuments": {
            "PLAN_R1.md": {"sha256": sha256(plan)},
            "API_SCHEMA_R1.md": {"sha256": sha256(api_schema)},
        },
        "inputs": {
            "E production Router": {
                "path": "E/src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
                "bytes": len(production_router), "sha256": sha256(production_router),
            },
            "accepted P6 Router overlay": {
                "path": "P6/overlays-r1/PcbNetRouter.java",
                "bytes": len(p6_router), "sha256": sha256(p6_router),
            },
            "E production rules": {
                "path": RULES_REL.as_posix(),
                "bytes": len(rules), "sha256": sha256(rules),
            },
        },
        "outputs": {
            router_path.name: {"bytes": len(router_overlay), "sha256": sha256(router_overlay)},
            rules_path.name: {"bytes": len(rules_overlay), "sha256": sha256(rules_overlay)},
            diff_path.name: {"bytes": len(diff), "sha256": sha256(diff)},
        },
        "routerEdits": router_edits,
        "rulesEdits": rules_edits,
        "routerUnchangedSegments": router_preserved,
        "rulesUnchangedSegments": rules_preserved,
        "reasonSitesExactlyOnce": site_counts,
        "proof": {
            "nonWhitelistedBytesIdentical": True,
            "p6CoarseObserverSeamByteIdentical": True,
            "p6BoundsAndEndpointLoopByteIdentical": True,
            "p6CanOccupyBodyByteIdentical": True,
            "originalRouterGuardExpressionsRetained": True,
            "originalDomainGuardExpressionsRetained": True,
            "predicateOrder": ["isLegalMove", "canTraverse", "canOccupy"],
            "emptyFaceReservationPredicateEvaluatedOnce": True,
            "routerReasonObserverCopiedToFixedGraphCopies": False,
            "rulesObserverStoredOnSharedRulesObject": False,
            "rulesObserverIsPerCallOnly": True,
            "productionFilesChanged": False,
        },
    }
    manifest_bytes = (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode("utf-8")
    _write_exact(router_path, router_overlay)
    _write_exact(rules_path, rules_overlay)
    _write_exact(diff_path, diff)
    _write_exact(manifest_path, manifest_bytes)
    return {
        "status": "PASS_STATIC_OVERLAY_GENERATED",
        "builderSha256": builder_sha,
        "productionRouterSha256": sha256(production_router),
        "p6RouterOverlaySha256": sha256(p6_router),
        "rulesSourceSha256": sha256(rules),
        "routerOverlayBytes": len(router_overlay),
        "routerOverlaySha256": sha256(router_overlay),
        "rulesOverlayBytes": len(rules_overlay),
        "rulesOverlaySha256": sha256(rules_overlay),
        "diffBytes": len(diff),
        "diffSha256": sha256(diff),
        "manifestBytes": len(manifest_bytes),
        "manifestSha256": sha256(manifest_bytes),
        "reasonSiteCounts": site_counts,
        "routerUnchangedBytes": sum(row["bytes"] for row in router_preserved),
        "rulesUnchangedBytes": sum(row["bytes"] for row in rules_preserved),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--e-source-root", required=True, type=Path,
                        help="explicit E source repository root")
    parser.add_argument("--p6-router-overlay", required=True, type=Path,
                        help="explicit accepted P6 overlays-r1/PcbNetRouter.java")
    args = parser.parse_args()
    result = build_artifacts(args.e_source_root, args.p6_router_overlay)
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))


if __name__ == "__main__":
    main()
