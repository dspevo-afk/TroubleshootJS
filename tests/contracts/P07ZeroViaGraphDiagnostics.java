package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Test-only two-face ordinary-edge graph capture over fixed actual Router predicates. */
final class P07ZeroViaGraphDiagnostics {
    private static final int MAX_PATH_NODES = 4096;
    private static final int MAX_CELLS = 250000;
    private static final int MAX_ADVANCE_WORK = 32768;
    private static final int MAX_REACHED_PADS = 4096;
    private static final int MAX_LAYOUT_ROWS = 16384;
    private static final int MAX_SOURCE_TOKEN_CHARS = 32768;
    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};
    private static final String HEX = "0123456789abcdef";
    private static final String BOTTOM_SEPARATOR="\",\"bottomStepsHex\":\"";
    private static final String GOAL_SEPARATOR="\",\"goalFacesHex\":\"";
    private static final String BODY_SUFFIX="\"}}";

    private P07ZeroViaGraphDiagnostics() { }

    enum Target { D_SENSE, LOAD12, OUT_F }

    interface GraphInput {
        String sourceKind();
        int seedLayerMask();
        int pathNodeCount();
        int pathCellAt(int index);
        int pathXAt(int index);
        int pathYAt(int index);
        int pathLayerAt(int index);
        int pathDirectionAt(int index);
        int pathTransitionsAt(int index);
        int pathCostAt(int index);
        int pathHeuristicAt(int index);
        int pathSerialAt(int index);
        int pathKeyAt(int index);
        String pathTupleSha256();
        String goalFaceBitsSha256();
        int goalCellCount();
        int expectedGoalFaceMaskAt(int cell);
    }

    /** Stores only source references and scalar grid authority. */
    static final class Context {
        final Target target;
        private final PcbBoardLayout layout;
        private final PcbNetRouter.Router[] faces;
        private final PcbNetRouter.Router[] callerFaces;
        private final String net;
        private final PcbPadPlacement start;
        private final PcbPadPlacement root;
        private final PcbPadPlacement[] reached;
        private final boolean[] sourceGoalMask;
        private final GraphInput input;
        final int width, height, originX, originY, pitch, initialLayerMask;
        final int cells,expectedTraceWidth;

        Context(Target target, PcbBoardLayout layout, PcbNetRouter.Router[] faces, String net,
                PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
                boolean[] actualGoalMask, int width, int height, int originX, int originY,
                int pitch, int initialLayerMask, GraphInput input) {
            if (target == null || layout == null || faces == null || faces.length != 2 ||
                    faces[TOP] == null || faces[BOTTOM] == null || net == null || net.length() == 0 ||
                    start == null || root == null || reached == null || actualGoalMask == null ||
                    input == null || width < 1 || height < 1 || pitch < 1 ||
                    (initialLayerMask & ~3) != 0 || initialLayerMask == 0 ||
                    input.pathNodeCount() < 2 || input.pathNodeCount() > MAX_PATH_NODES)
                throw new IllegalArgumentException("Invalid P07 zero-via graph context");
            String expectedKind = target == Target.D_SENSE ? "D_SENSE_PUBLISHER_PATH" :
                target == Target.LOAD12 ? "LOAD12_ACCEPTED_PRE_RESULT" :
                "OUT_F_REJECTED_PUBLISHER_PATH";
            if (!expectedKind.equals(input.sourceKind()))
                throw new IllegalArgumentException("P07 zero-via target and source kind differ");
            long cellCount = (long) width * (long) height;
            if (cellCount < 1 || cellCount > MAX_CELLS || actualGoalMask.length != cellCount * 2L)
                throw new IllegalArgumentException("P07 zero-via graph grid or goal shape is outside its bound");
            long lastX = (long) originX + (long) (width - 1) * pitch;
            long lastY = (long) originY + (long) (height - 1) * pitch;
            if (lastX > Integer.MAX_VALUE || lastX < Integer.MIN_VALUE ||
                    lastY > Integer.MAX_VALUE || lastY < Integer.MIN_VALUE)
                throw new IllegalArgumentException("P07 zero-via graph grid coordinates overflow");
            if (layout.getPad(start.getPadId()) != start || layout.getPad(root.getPadId()) != root)
                throw new IllegalArgumentException("P07 zero-via graph endpoint identity differs from layout");
            TroubleshootBoard sourceBoard=(TroubleshootBoard)readField(faces[TOP],"board");
            if(sourceBoard==null || readField(faces[BOTTOM],"board")!=sourceBoard || sourceBoard.getNet(net)==null)
                throw new IllegalArgumentException("P07 face Routers do not share the actual target board/net");
            if(readField(faces[TOP],"layout")!=layout || readField(faces[BOTTOM],"layout")!=layout ||
                    readField(faces[TOP],"layer")!=PcbCopperLayer.TOP ||
                    readField(faces[BOTTOM],"layer")!=PcbCopperLayer.BOTTOM)
                throw new IllegalArgumentException("P07 Router layout/face identity differs from its graph slot");
            expectedTraceWidth=sourceBoard.getPlacementConstraints().getTraceWidthProfile().widthFor(
                sourceBoard.getNet(net).getRoutingRole());
            for(int face=0;face<2;face++) if(!net.equals(readField(faces[face],"activeNetId")) ||
                    ((Integer)readField(faces[face],"activeTraceWidth")).intValue()!=expectedTraceWidth)
                throw new IllegalArgumentException("P07 actual face Router is not prepared for its target net/trace width");
            boolean noPreResult = target == Target.D_SENSE || target == Target.OUT_F;
            if (reached.length > MAX_REACHED_PADS || input.seedLayerMask() != initialLayerMask ||
                    noPreResult && (input.pathTupleSha256() != null ||
                        input.goalFaceBitsSha256() != null || input.goalCellCount() != -1 ||
                        input.expectedGoalFaceMaskAt(0) != -1) ||
                    target == Target.LOAD12 &&
                        (!isSha256(input.pathTupleSha256()) ||
                            !isSha256(input.goalFaceBitsSha256()) ||
                            input.goalCellCount() < 0 || input.goalCellCount() > cellCount))
                throw new IllegalArgumentException("P07 zero-via source metadata is invalid");
            this.target = target;
            this.layout = layout;
            this.callerFaces = faces;
            this.faces = new PcbNetRouter.Router[]{faces[TOP],faces[BOTTOM]};
            this.net = net;
            this.start = start;
            this.root = root;
            this.reached = reached;
            this.sourceGoalMask = actualGoalMask;
            this.input = input;
            this.width = width;
            this.height = height;
            this.originX = originX;
            this.originY = originY;
            this.pitch = pitch;
            this.initialLayerMask = initialLayerMask;
            this.cells = (int) cellCount;
        }

        /** Small-fixture adapter retained for the literal native contract fixture. */
        Context(PcbBoardLayout layout, PcbNetRouter.Router[] faces, String net,
                PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
                boolean[] actualGoalMask, int width, int height, int originX, int originY,
                int pitch, int initialLayerMask, int[][] pathPhysical) {
            this(Target.D_SENSE, layout, faces, net, start, root, reached, actualGoalMask,
                width, height, originX, originY, pitch, initialLayerMask,
                new LiteralPhysicalInput(pathPhysical, initialLayerMask, start, originX,
                    originY, width, height, pitch));
        }
    }

    private static final int STREAM_END=-1, STREAM_WORK=-2;

    private static final class NumberScratch { char[] digits; int credits; }

    /** Fixed metadata fragments; source strings are viewed rather than concatenated. */
    private static final class TextToken {
        private final Object[] parts;
        private int index, offset, digitIndex, digitRead, numberStage;
        private NumberScratch ownNumbers;
        private long negativeValue;
        private boolean negative;
        private String active;
        TextToken(Object... parts) {
            if (parts == null || parts.length > 128)
                throw new IllegalArgumentException("Invalid bounded text metadata");
            this.parts = parts;
        }
        int nextUnit() {
            if(ownNumbers==null)ownNumbers=new NumberScratch();
            return nextUnit(ownNumbers);
        }
        int nextUnit(NumberScratch numbers) {
            if (active != null && offset < active.length()) return active.charAt(offset++);
            active = null;
            offset = 0;
            if (index == parts.length) return STREAM_END;
            Object part = parts[index];
            if (part instanceof TextToken) {
                int value = ((TextToken) part).nextUnit(numbers);
                if (value == STREAM_END) { index++; return STREAM_WORK; }
                return value;
            }
            if (part instanceof Integer || part instanceof Long) {
                if (numbers.digits == null) {
                    if (numbers.credits < 20) { numbers.credits++; return STREAM_WORK; }
                    numbers.digits = new char[20];
                    return STREAM_WORK;
                }
                char[] digits=numbers.digits;
                if (numberStage == 0) {
                    long value=((Number)part).longValue();
                    negative=value<0;
                    negativeValue=negative?value:-value; // handles Long.MIN_VALUE without overflow
                    digitIndex=20;
                    numberStage=1;
                }
                if (numberStage == 1) {
                    digits[--digitIndex]=(char)('0'-(int)(negativeValue%10));
                    negativeValue/=10;
                    if(negativeValue==0) {
                        if(negative)digits[--digitIndex]='-';
                        digitRead=digitIndex;
                        numberStage=3;
                    }
                    return STREAM_WORK;
                }
                if(digitRead<20) {
                    char value=digits[digitRead++];
                    if(digitRead==20) {numberStage=0;index++;}
                    return value;
                }
                throw new IllegalStateException("Invalid decimal fragment state");
            } else if (part == null) active = "null";
            else if (part instanceof String) active = (String) part;
            else if (part instanceof Boolean) active = ((Boolean) part).booleanValue() ? "true" : "false";
            else if (part instanceof Enum<?>) active = ((Enum<?>) part).name();
            else throw new IllegalArgumentException("Unsupported canonical text fragment");
            if (active.length() > MAX_SOURCE_TOKEN_CHARS)
                throw new IllegalStateException("Canonical text source fragment exceeds its bound");
            index++;
            // Metadata selection and cursor movement are constant work. Each call
            // still derives at most one decimal digit or emits one UTF-16 unit.
            return active.length()==0?STREAM_WORK:active.charAt(offset++);
        }
        int length() { return length(0); }
        private int length(int depth) {
            if (depth > 8) throw new IllegalStateException("Canonical metadata nesting exceeds its bound");
            long size = 0;
            for (Object part : parts) {
                if (part instanceof TextToken) size += ((TextToken) part).length(depth + 1);
                else if (part instanceof Integer || part instanceof Long) {
                    long value = ((Number) part).longValue();
                    if (value < 0) size++;
                    do { size++; value /= 10; } while (value != 0);
                } else if (part == null) size += 4;
                else if (part instanceof String) size += ((String) part).length();
                else if (part instanceof Boolean) size += ((Boolean) part).booleanValue() ? 4 : 5;
                else if (part instanceof Enum<?>) size += ((Enum<?>) part).name().length();
                else throw new IllegalArgumentException("Unsupported canonical metadata fragment");
            }
            if (size > Integer.MAX_VALUE) throw new IllegalStateException("Canonical metadata length overflow");
            return (int) size;
        }
    }
    private static TextToken text(Object... parts) { return new TextToken(parts); }
    private static TextToken fieldText(String value) {
        return value == null ? text(-1, ":") : text(value.length(), ":", value);
    }
    private static TextToken rectText(String name, Rectangle value) {
        return value == null ? text(name, "=null;") :
            text(name, "=", value.x, ",", value.y, ",", value.width, ",", value.height, ";");
    }
    private static TextToken pointText(Point value) {
        return value == null ? text("point=null;") : text("point=", value.x, ",", value.y, ";");
    }
    private static TextToken leadText(String name, PhysicalPackageGeometry.Lead lead) {
        return text(name, "=", pointText((Point)readField(lead,"endPoint")), pointText((Point)readField(lead,"bodyPoint")),
            rectText(name + "Bounds",(Rectangle)readField(lead,"bounds")),pointText((Point)readField(lead,"componentProbeCenter")),
            rectText(name + "Probe",(Rectangle)readField(lead,"componentProbeBounds")));
    }

    /** Resumable canonical PcbBoardLayout.geometryFingerprint() byte stream. */
    private static final class GeometryWalker {
        private final PcbBoardLayout layout;
        private final Map<?,?> componentSource,padSource,labelSource;
        private final Map<?,?> regionSource,holeSource;
        private final Vector<?> traceSource;
        private final TreeMap<String,Object> components=new TreeMap<String,Object>();
        private final TreeMap<String,Object> pads=new TreeMap<String,Object>();
        private final TreeMap<String,Object> labels=new TreeMap<String,Object>();
        private final TreeMap<TraceOrderKey,PcbTraceGeometry> traces=new TreeMap<TraceOrderKey,PcbTraceGeometry>();
        private final ArrayList<Object> regionRows;
        private final ArrayList<Object> regionKeys;
        private final ArrayList<Object> holeRows;
        private final ArrayList<Object> holeKeys;
        private final ArrayList<Vector<?>> regionComponentLists;
        private final ArrayList<PcbTraceGeometry> originalTraceRows;
        private final ArrayList<int[]> originalTraceXArrays;
        private final ArrayList<int[]> originalTraceYArrays;
        private final GeometryWalker baseline;
        private Iterator<? extends Map.Entry<?,?>> captureIterator;
        private Iterator<?> regionIterator,componentIterator,padIterator,labelIterator,traceIterator,holeIterator;
        private Iterator<Map.Entry<String,Object>> componentBaselineIterator,padBaselineIterator,labelBaselineIterator;
        private Iterator<Map.Entry<TraceOrderKey,PcbTraceGeometry>> traceBaselineIterator;
        private Vector<?> regionComponents;
        private Iterator<?> regionComponentIterator;
        private ComponentStream componentStream;
        private TraceStream traceStream;
        private TextToken pending;
        private String pendingMapKey;
        private Object pendingMapValue;
        private TraceOrderKey pendingTraceKey;
        private int pendingMapKind,sortCredits;
        private int pendingOffset,position,phase,capturePhase,traceIndex;
        private int width,height,version;
        private Rectangle outline,tray;
        private boolean done,utf8Finished;
        private final Utf8State geometryDigest=new Utf8State();
        private final NumberScratch numbers=new NumberScratch();
        private int lastByteCount;
        private long byteLength;
        private byte[] finalizedDigest;
        private GeometryWalker(PcbBoardLayout layout) { this(layout,null); }
        private GeometryWalker(PcbBoardLayout layout,GeometryWalker baseline) {
            this.layout=layout;
            this.baseline=baseline;
            componentSource=(Map<?,?>)readField(layout,"components");
            padSource=(Map<?,?>)readField(layout,"pads");
            labelSource=(Map<?,?>)readField(layout,"silkscreenLabels");
            regionSource=(Map<?,?>)readField(layout,"regions");
            holeSource=(Map<?,?>)readField(layout,"holes");
            traceSource=(Vector<?>)readField(layout,"traces");
            regionRows=new ArrayList<Object>(regionSource.size());
            regionKeys=new ArrayList<Object>(regionSource.size());
            regionComponentLists=new ArrayList<Vector<?>>(regionSource.size());
            holeRows=new ArrayList<Object>(holeSource.size());
            holeKeys=new ArrayList<Object>(holeSource.size());
            originalTraceRows=new ArrayList<PcbTraceGeometry>(traceSource.size());
            originalTraceXArrays=new ArrayList<int[]>(traceSource.size());
            originalTraceYArrays=new ArrayList<int[]>(traceSource.size());
            width=((Integer)readField(layout,"width")).intValue();
            height=((Integer)readField(layout,"height")).intValue();
            version=((Integer)readField(layout,"layoutAlgorithmVersion")).intValue();
            outline=(Rectangle)readField(layout,"boardOutline");
            tray=(Rectangle)readField(layout,"partsTray");
            if(componentSource.size()>MAX_LAYOUT_ROWS || padSource.size()>MAX_LAYOUT_ROWS ||
                    labelSource.size()>MAX_LAYOUT_ROWS || regionSource.size()>MAX_LAYOUT_ROWS ||
                    holeSource.size()>MAX_LAYOUT_ROWS || traceSource.size()>MAX_LAYOUT_ROWS)
                throw new IllegalStateException("P07 layout fingerprint source exceeds bounded row count");
            if(baseline!=null && (layout!=baseline.layout || componentSource!=baseline.componentSource ||
                    padSource!=baseline.padSource || labelSource!=baseline.labelSource ||
                    regionSource!=baseline.regionSource || holeSource!=baseline.holeSource ||
                    traceSource!=baseline.traceSource || outline!=baseline.outline || tray!=baseline.tray ||
                    width!=baseline.width || height!=baseline.height || version!=baseline.version ||
                    componentSource.size()!=baseline.componentSource.size() ||
                    padSource.size()!=baseline.padSource.size() || labelSource.size()!=baseline.labelSource.size() ||
                    regionSource.size()!=baseline.regionSource.size() || holeSource.size()!=baseline.holeSource.size() ||
                    traceSource.size()!=baseline.traceSource.size()))
                throw new IllegalStateException("P07 layout source/container identity or scalar shape changed");
            regionIterator=regionSource.entrySet().iterator();
            capturePhase=0;
            captureIterator=componentSource.entrySet().iterator();
        }
        static long storageUnits(PcbBoardLayout layout){
            return (long)((Map<?,?>)readField(layout,"regions")).size()*3L+
                (long)((Map<?,?>)readField(layout,"holes")).size()*2L+
                (long)((Vector<?>)readField(layout,"traces")).size()*3L;
        }
        int position(){return position;}
        String finishSha256(){if(!utf8Finished){finalizedDigest=geometryDigest.finish();utf8Finished=true;lastByteCount=geometryDigest.lastByteCount;byteLength+=lastByteCount;}return hex(finalizedDigest);}
        int finishByteCount(){if(utf8Finished)return 0;finalizedDigest=geometryDigest.finish();utf8Finished=true;lastByteCount=geometryDigest.lastByteCount;byteLength+=lastByteCount;return lastByteCount;}
        long byteLength(){return byteLength;}
        boolean identitiesComplete(){return baseline!=null&&regionRows.size()==baseline.regionRows.size()&&holeRows.size()==baseline.holeRows.size()&&originalTraceRows.size()==baseline.originalTraceRows.size()&&components.size()==baseline.components.size()&&pads.size()==baseline.pads.size()&&labels.size()==baseline.labels.size();}
        int lastByteCount(){return lastByteCount;}

        int nextUnit() {
            if(done)return STREAM_END;
            if(pendingMapKind!=0) {
                if(sortCredits>0){sortCredits--;return STREAM_WORK;}
                if(pendingMapKind==1)components.put(pendingMapKey,pendingMapValue);
                else if(pendingMapKind==2)pads.put(pendingMapKey,pendingMapValue);
                else if(pendingMapKind==3)labels.put(pendingMapKey,pendingMapValue);
                else if(pendingMapKind==4)traces.put(pendingTraceKey,(PcbTraceGeometry)pendingMapValue);
                pendingMapKind=0;pendingMapKey=null;pendingMapValue=null;pendingTraceKey=null;
                return STREAM_WORK;
            }
            if(pending!=null) {
                int value=pending.nextUnit(numbers);
                if(value>=0){position++;lastByteCount=geometryDigest.accept((char)value);byteLength+=lastByteCount;return value;}
                if(value==STREAM_WORK)return STREAM_WORK;
                pending=null;
            }
            TextToken token=nextToken();
            if(token==END_TOKEN){done=true;return STREAM_END;}
            pending=token;
            return STREAM_WORK;
        }

        private static final TextToken END_TOKEN=text("<END_P07_GEOMETRY>");
        private TextToken nextToken() {
            if(componentStream!=null) {
                TextToken value=componentStream.nextToken();
                if(value!=null)return value;
                if(!componentStream.isComplete())return null;
                componentStream=null;
                return text(";");
            }
            if(traceStream!=null) {
                TextToken value=traceStream.nextToken();
                if(value!=null)return value;
                traceStream=null;
                return null;
            }
            if(phase==0) {phase=1;return text("layout=",version,"|",PcbRouteMetrics.SCORE_VERSION,"|");}
            if(phase==1) {
                if(regionComponents!=null && regionComponentIterator!=null && regionComponentIterator.hasNext())
                    return fieldText((String)regionComponentIterator.next());
                regionComponents=null;regionComponentIterator=null;
                if(regionIterator.hasNext()) {
                    Map.Entry<?,?> regionEntry=(Map.Entry<?,?>)regionIterator.next();
                    PcbLayoutRegion region=(PcbLayoutRegion)regionEntry.getValue();
                    Vector<?> values=(Vector<?>)readField(region,"components");
                    int row=regionRows.size();
                    if(baseline!=null && (row>=baseline.regionRows.size() || baseline.regionRows.get(row)!=region || baseline.regionKeys.get(row)!=regionEntry.getKey() || baseline.regionComponentLists.get(row)!=values))throw changed("region key/identity/order/components changed");
                    regionRows.add(region);regionKeys.add(regionEntry.getKey());regionComponentLists.add(values);
                    regionComponents=values;regionComponentIterator=values.iterator();
                    return text("region:",fieldText(region.id),fieldText(region.label));
                }
                phase=2;
            }
            if(phase==2) {
                phase=3;
                return text(width,"x",height,"|",outline.x,",",outline.y,",",outline.width,",",outline.height,"|",
                    tray.x,",",tray.y,",",tray.width,",",tray.height,"|");
            }
            if(phase==3) {
                if(capturePhase!=0&&!components.isEmpty()) {
                    if(componentIterator==null){componentIterator=components.entrySet().iterator();if(baseline!=null)componentBaselineIterator=baseline.components.entrySet().iterator();}
                    if(componentIterator.hasNext()) {
                        Map.Entry<?,?> entry=(Map.Entry<?,?>)componentIterator.next();
                        if(baseline!=null){Map.Entry<String,Object> prior=componentBaselineIterator.next();if(prior.getKey()!=entry.getKey()||prior.getValue()!=entry.getValue())throw changed("component key/value identity changed");}
                        componentStream=new ComponentStream((PcbComponentPlacement)entry.getValue());
                        return text("C:");
                    }
                }
                if(capturePhase==0) {
                    if(captureIterator.hasNext()) {
                        Map.Entry<?,?> entry=captureIterator.next();
                        if(!(entry.getKey() instanceof String) || !(entry.getValue() instanceof PcbComponentPlacement))throw changed("component map row type changed");
                        queueMapInsert(1,(String)entry.getKey(),entry.getValue(),null,components.size());return null;
                    }
                    capturePhase=1;captureIterator=padSource.entrySet().iterator();return null;
                }
                phase=4;
            }
            if(phase==4) {
                if(pads.isEmpty() && capturePhase==1) { /* an empty source is valid */ }
                if(capturePhase!=1&&!pads.isEmpty()) {
                    if(padIterator==null){padIterator=pads.entrySet().iterator();if(baseline!=null)padBaselineIterator=baseline.pads.entrySet().iterator();}
                    if(padIterator.hasNext()) {
                        Map.Entry<?,?> entry=(Map.Entry<?,?>)padIterator.next();
                        if(baseline!=null){Map.Entry<String,Object> prior=padBaselineIterator.next();if(prior.getKey()!=entry.getKey()||prior.getValue()!=entry.getValue())throw changed("pad key/value identity changed");}
                        PcbPadPlacement pad=(PcbPadPlacement)entry.getValue();
                        Rectangle pb=(Rectangle)readField(pad,"padBounds"),qb=(Rectangle)readField(pad,"probeBounds");
                        return text("P:",pad.getPadId(),"@",pad.getX(),",",pad.getY()," escape=",pad.getEscapeDx(),",",pad.getEscapeDy(),",",pad.getEscapeLength(),
                            " attachment=",pad.getAttachment()," mount=",pad.getMountingSide()," exposure=",pad.getExposure(),
                            " pad=",pb.x,",",pb.y,",",pb.width,",",pb.height," probe=",qb.x,",",qb.y,",",qb.width,",",qb.height,";");
                    }
                }
                if(capturePhase==1) {
                    if(captureIterator.hasNext()) {
                        Map.Entry<?,?> entry=captureIterator.next();
                        if(!(entry.getKey() instanceof String) || !(entry.getValue() instanceof PcbPadPlacement))throw changed("pad map row type changed");
                        queueMapInsert(2,(String)entry.getKey(),entry.getValue(),null,pads.size());return null;
                    }
                    capturePhase=2;captureIterator=labelSource.entrySet().iterator();return null;
                }
                phase=5;
            }
            if(phase==5) {
                if(capturePhase!=2&&!labels.isEmpty()) {
                    if(labelIterator==null){labelIterator=labels.entrySet().iterator();if(baseline!=null)labelBaselineIterator=baseline.labels.entrySet().iterator();}
                    if(labelIterator.hasNext()) {
                        Map.Entry<?,?> entry=(Map.Entry<?,?>)labelIterator.next();
                        if(baseline!=null){Map.Entry<String,Object> prior=labelBaselineIterator.next();if(prior.getKey()!=entry.getKey()||prior.getValue()!=entry.getValue())throw changed("label key/value identity changed");}
                        PcbSilkscreenLabel label=(PcbSilkscreenLabel)entry.getValue();Rectangle bounds=(Rectangle)readField(label,"bounds");
                        return text("L:",entry.getKey(),"@",label.getText(),":",bounds.x,",",bounds.y,",",bounds.width,",",bounds.height,";");
                    }
                }
                if(capturePhase==2) {
                    if(captureIterator.hasNext()) {
                        Map.Entry<?,?> entry=captureIterator.next();
                        if(!(entry.getKey() instanceof String) || !(entry.getValue() instanceof PcbSilkscreenLabel))throw changed("label map row type changed");
                        queueMapInsert(3,(String)entry.getKey(),entry.getValue(),null,labels.size());return null;
                    }
                    capturePhase=3;traceIndex=0;return null;
                }
                phase=6;
            }
            if(phase==6) {
                if(capturePhase==3) {
                    if(traceIndex<traceSource.size()) {
                        PcbTraceGeometry trace=(PcbTraceGeometry)traceSource.elementAt(traceIndex);
                        if(baseline!=null && (traceIndex>=baseline.originalTraceRows.size() || baseline.originalTraceRows.get(traceIndex)!=trace))throw changed("trace vector identity/order changed");
                        int[] x=(int[])readField(trace,"xPoints"),y=(int[])readField(trace,"yPoints");
                        if(baseline!=null && (baseline.originalTraceXArrays.get(traceIndex)!=x || baseline.originalTraceYArrays.get(traceIndex)!=y))throw changed("trace point-array identity changed");
                        originalTraceRows.add(trace);
                        originalTraceXArrays.add(x);originalTraceYArrays.add(y);
                        TraceOrderKey key=new TraceOrderKey(trace,traceIndex);
                        queueMapInsert(4,null,trace,key,traces.size());traceIndex++;return null;
                    }
                    traceIterator=traces.values().iterator();if(baseline!=null)traceBaselineIterator=baseline.traces.entrySet().iterator();capturePhase=4;return null;
                }
                if(traceIterator!=null && traceIterator.hasNext()) {
                    PcbTraceGeometry trace=(PcbTraceGeometry)traceIterator.next();
                    if(baseline!=null && traceBaselineIterator.next().getValue()!=trace)throw changed("canonical trace identity/order changed");
                    traceStream=new TraceStream(trace);
                    return null;
                }
                phase=7;
            }
            if(phase==7) {
                if(holeIterator==null)holeIterator=holeSource.entrySet().iterator();
                if(holeIterator.hasNext()){
                    Map.Entry<?,?> holeEntry=(Map.Entry<?,?>)holeIterator.next();
                    PcbBoardHole hole=(PcbBoardHole)holeEntry.getValue();
                    if(baseline!=null && (holeRows.size()>=baseline.holeRows.size() || baseline.holeRows.get(holeRows.size())!=hole || baseline.holeKeys.get(holeRows.size())!=holeEntry.getKey()))throw changed("hole key/identity/order changed");
                    holeRows.add(hole);holeKeys.add(holeEntry.getKey());return text("H:",fieldText(hole.id),hole.kind,fieldText(hole.netId),hole.x,",",hole.y,",",hole.drillRadius,",",hole.landRadius,",",hole.exposure);
                }
                phase=8;return END_TOKEN;
            }
            return END_TOKEN;
        }
        private void queueMapInsert(int kind,String key,Object value,TraceOrderKey traceKey,int currentSize){
            pendingMapKind=kind;pendingMapKey=key;pendingMapValue=value;pendingTraceKey=traceKey;
            int keyUnits=kind==4?traceKey.comparisonUnits():key.length();
            sortCredits=treeInsertCredits(currentSize,keyUnits);
        }
        private static int treeInsertCredits(int size,int keyUnits){
            int value=size+2,log=0;while(value>1){value=(value+1)>>>1;log++;}
            long credits=(long)(2*log+1)*(Math.max(1,keyUnits)+2);
            if(credits>Integer.MAX_VALUE)throw new IllegalStateException("P07 canonical sort comparison credit overflow");
            return (int)credits;
        }
        private IllegalStateException changed(String text){return new IllegalStateException("P07 canonical layout "+text);}
    }

    private static final class TraceOrderKey implements Comparable<TraceOrderKey> {
        final PcbTraceGeometry trace;final int index;final String net,start,end,source;final PcbCopperLayer layer;final int width;
        TraceOrderKey(PcbTraceGeometry trace,int index){this.trace=trace;this.index=index;net=trace.getNetId();start=String.valueOf(trace.getStartPadId());end=String.valueOf(trace.getEndPadId());source=String.valueOf(trace.getSourceId());layer=trace.getLayer();width=trace.getWidth();}
        int comparisonUnits(){return net.length()+start.length()+end.length()+source.length()+32;}
        public int compareTo(TraceOrderKey other){
            int c=net.compareTo(other.net);if(c!=0)return c;
            c=start.compareTo(other.start);if(c!=0)return c;
            c=end.compareTo(other.end);if(c!=0)return c;
            c=layer.compareTo(other.layer);if(c!=0)return c;
            c=width<other.width?-1:width==other.width?0:1;if(c!=0)return c;
            c=source.compareTo(other.source);
            return c!=0?c:(index<other.index?-1:index==other.index?0:1);
        }
    }

    private static final class TraceStream {
        final PcbTraceGeometry trace;final int[] x,y;int phase,index;
        TraceStream(PcbTraceGeometry trace){this.trace=trace;x=(int[])readField(trace,"xPoints");y=(int[])readField(trace,"yPoints");}
        TextToken nextToken(){
            if(phase==0){
                phase=1;
                return text("T:",fieldText(trace.getSourceId()),trace.getLayer(),":",trace.getExposure(),":",trace.getNetId(),":",
                    trace.getStartPadId(),"-",trace.getEndPadId(),"@",
                    trace.getWidth()!=PcbTraceRules.TRACE_WIDTH?text("W",trace.getWidth(),":"):text());
            }
            if(index<x.length){int i=index++;return text(x[i],",",y[i],";");}
            return null;
        }
    }

    private static final class ComponentStream {
        final PcbComponentPlacement placement;final PhysicalPackage physicalPackage;
        final PhysicalPackageGeometry geometry;final PhysicalPackageGeometry.Placement placed;
        final PcbPackagePose pose;final Vector<?> packageTerminals,localTerminals,internalConnections;
        final PhysicalPackageGeometry.IsolationBody isolation;
        final Vector<?> isolationFirst,isolationSecond;
        int phase,pairFirst,pairSecond,localIndex,placedIndex,isolationList,isolationIndex,isolationRect;
        long constructionCredits=-1;
        ComponentStream(PcbComponentPlacement placement){
            this.placement=placement;physicalPackage=placement.getPhysicalPackage();geometry=placement.getPhysicalGeometry();
            pose=placement.getPose();placed=(PhysicalPackageGeometry.Placement)readField(placement,"placedGeometry");
            if(readField(placed,"source")!=geometry)
                throw new IllegalStateException("P07 placed terminal source differs from canonical local geometry");
            packageTerminals=(Vector<?>)readField(physicalPackage,"terminalIds");
            internalConnections=(Vector<?>)readField(physicalPackage,"internalConnections");
            localTerminals=(Vector<?>)readField(geometry,"terminals");
            isolation=geometry.getIsolationBody();
            isolationFirst=isolation==null?null:(Vector<?>)readField(isolation,"firstTerminals");
            isolationSecond=isolation==null?null:(Vector<?>)readField(isolation,"secondTerminals");
            if(packageTerminals.size()>256||localTerminals.size()>256||internalConnections.size()>65536)
                throw new IllegalStateException("P07 component terminal stream exceeds its bounded source size");
            pairSecond=1;
        }
        boolean isComplete(){return phase==9;}
        TextToken nextToken(){
            if(isComplete())return null;
            if(constructionCredits<0){
                constructionCredits=phase==0?1024:phase==3&&geometry.getRaisedCrossover()!=null?512:phase==6?256:
                    phase==7&&localIndex<localTerminals.size()?512:
                    phase==8&&placedIndex<localTerminals.size()?1024:0;
                if(phase==1&&pairFirst<packageTerminals.size()-1&&pairSecond<packageTerminals.size()){
                    String a=(String)packageTerminals.get(pairFirst),b=(String)packageTerminals.get(pairSecond);
                    if(a.length()>MAX_SOURCE_TOKEN_CHARS||b.length()>MAX_SOURCE_TOKEN_CHARS)
                        throw new IllegalStateException("P07 terminal identity exceeds text bound");
                    // Includes the two declared-membership scans, connection
                    // scans and the bounded forward/reverse temporary keys.
                    constructionCredits=(2L*packageTerminals.size()+2L*internalConnections.size()+16L)*
                        ((long)a.length()+b.length()+1L);
                }
                return null;
            }
            if(constructionCredits>0){constructionCredits--;return null;}
            constructionCredits=-1;
            return buildToken();
        }
        private TextToken buildToken(){
            if(phase==0){phase=1;return header();}
            if(phase==1){
                if(pairFirst<packageTerminals.size()-1){
                    if(pairSecond>=packageTerminals.size()){pairFirst++;pairSecond=pairFirst+1;return null;}
                    String first=(String)packageTerminals.get(pairFirst),second=(String)packageTerminals.get(pairSecond++);
                    return text("internal:",first,"=",second,"=",physicalPackage.isInternallyConnected(first,second),";");
                }
                phase=2;return null;
            }
            if(phase==2){phase=3;return geometryHeader();}
            if(phase==3){RaisedCrossoverGeometry raised=geometry.getRaisedCrossover();phase=4;return raised==null?null:text("raisedCrossover=",raised.fingerprint(),"|");}
            if(phase==4){
                if(isolation!=null){phase=5;isolationList=0;isolationIndex=0;return text("isolationBody=isolation-body-v1|");}
                phase=6;return null;
            }
            if(phase==5){
                if(isolationList==0){if(isolationIndex<isolationFirst.size())return fieldText((String)isolationFirst.get(isolationIndex++));isolationList=1;isolationIndex=0;return text("|");}
                if(isolationList==1){if(isolationIndex<isolationSecond.size())return fieldText((String)isolationSecond.get(isolationIndex++));isolationList=2;isolationRect=0;return null;}
                Rectangle r=(Rectangle)(isolationRect==0?readField(isolation,"firstCourtyard"):isolationRect==1?readField(isolation,"secondCourtyard"):readField(isolation,"bodySpan"));
                isolationRect++;
                if(isolationRect==3){isolationList=3;phase=6;return text("|",r.x,",",r.y,",",r.width,",",r.height,"|");}
                return text("|",r.x,",",r.y,",",r.width,",",r.height);
            }
            if(phase==6){phase=7;return text(rectText("localBody",geometry.getBodyBounds()),rectText("localKeepout",geometry.getBodyKeepOut()),rectText("localCourtyard",geometry.getRoutingCourtyard()),rectText("localSelection",geometry.getSelectionEnvelope()),rectText("localDrag",geometry.getDragEnvelope()));}
            if(phase==7){if(localIndex<localTerminals.size()){int i=localIndex++;return localTerminal((PhysicalPackageGeometry.Terminal)localTerminals.get(i),i);}phase=8;return null;}
            if(phase==8){if(placedIndex<localTerminals.size())return placedTerminal(placedIndex++);phase=9;return null;}
            return null;
        }
        private TextToken header(){
            return text("component=",placement.getComponentId(),"@",placement.getX(),",",placement.getY(),",",placement.getWidth(),",",placement.getHeight(),"|",
                "pose=",pose.fingerprint(),"|",rectText("body",placement.getBodyBounds()),rectText("keepout",placement.getKeepOut()),
                rectText("courtyard",placement.getRoutingCourtyard()),rectText("selection",placement.getSelectionEnvelope()),rectText("drag",placement.getDragEnvelope()),
                "package=",physicalPackage.getId(),"|connector=",physicalPackage.isConnector(),"|variant=",placement.getGeometryVariantKey(),
                "|transform=",placement.getGeometryTransformKey(),"|version=",placement.getGeometryContractVersionValue(),
                "|looseVariant=",physicalPackage.getDefaultLooseGeometryVariantKey(),"|developerGeneric=",physicalPackage.isDeveloperGeneric(),"|");
        }
        private TextToken geometryHeader(){return text("geometry=",geometry.getWidth(),"x",geometry.getHeight(),"|version=",geometry.getGeometryContractVersionValue(),"|developerGeneric=",geometry.isDeveloperGeneric(),"|");}
        private TextToken localTerminal(PhysicalPackageGeometry.Terminal t,int index){
            return text("localTerminal[",index,"]=",t.getTerminalId(),"|",pointText((Point)readField(t,"padCenter")),rectText("pad",(Rectangle)readField(t,"padBounds")),
                pointText((Point)readField(t,"boardPadProbeCenter")),rectText("boardProbe",(Rectangle)readField(t,"boardPadProbeBounds")),
                leadText("connected",(PhysicalPackageGeometry.Lead)readField(t,"connectedLead")),
                leadText("lifted",(PhysicalPackageGeometry.Lead)readField(t,"liftedLead")),
                "escape=",t.getEscapeDx(),",",t.getEscapeDy(),",",t.getEscapeLength()," attachment=",t.getAttachment(),";");
        }
        private Point boardPoint(Object owner,String name){
            return placed.getPose().toBoardPoint((Point)readField(owner,name),geometry.getWidth(),geometry.getHeight());
        }
        private Rectangle boardRectangle(Object owner,String name){
            return placed.getPose().toBoardRectangle((Rectangle)readField(owner,name),geometry.getWidth(),geometry.getHeight());
        }
        private TextToken placedTerminal(int index){
            PhysicalPackageGeometry.Terminal t=(PhysicalPackageGeometry.Terminal)localTerminals.get(index);
            PhysicalPackageGeometry.Lead connected=(PhysicalPackageGeometry.Lead)readField(t,"connectedLead");
            PhysicalPackageGeometry.Lead lifted=(PhysicalPackageGeometry.Lead)readField(t,"liftedLead");
            Point direction=placed.getPose().toBoardDirection(t.getEscapeDx(),t.getEscapeDy());
            return text("terminal[",index,"]:",t.getTerminalId(),"|",pointText(boardPoint(t,"padCenter")),rectText("pad",boardRectangle(t,"padBounds")),
                pointText(boardPoint(t,"boardPadProbeCenter")),rectText("boardProbe",boardRectangle(t,"boardPadProbeBounds")),
                pointText(boardPoint(connected,"componentProbeCenter")),rectText("connectedProbe",boardRectangle(connected,"componentProbeBounds")),
                pointText(boardPoint(lifted,"componentProbeCenter")),rectText("liftedProbe",boardRectangle(lifted,"componentProbeBounds")),
                pointText(boardPoint(connected,"endPoint")),pointText(boardPoint(connected,"bodyPoint")),rectText("connectedLead",boardRectangle(connected,"bounds")),
                pointText(boardPoint(lifted,"endPoint")),pointText(boardPoint(lifted,"bodyPoint")),rectText("liftedLead",boardRectangle(lifted,"bounds")),
                "escape=",direction.x,",",direction.y,",",t.getEscapeLength()," attachment=",t.getAttachment(),";");
        }
    }
private static final class EffectWalker {
    private final PcbNetRouter.Router router;
    private final TreeMap<String,Integer> conflicts;
    private final Iterator<Map.Entry<String,Integer>> conflictIterator;
    private final PcbRoutingWork work;
    private final StringBuilder decisions;
    private final Utf8State digest = new Utf8State();
    private final NumberScratch numbers = new NumberScratch();
    private TextToken pending, workPrefix;
    private int offset, position, phase, decisionOffset, metadataTag = -1, metadataCredits;
    private String finalDigest;
    private boolean digestFinished;

    EffectWalker(PcbNetRouter.Router router) { this(router, null); }

    EffectWalker(PcbNetRouter.Router router, EffectIdentity expected) {
        this.router = router;
        conflicts = router.conflicts;
        conflictIterator = conflicts.entrySet().iterator();
        work = router.work;
        decisions = work == null ? null : work.decisions;
        if (expected != null && (expected.router != router || expected.conflicts != conflicts ||
                expected.work != work || expected.decisions != decisions))
            throw new IllegalStateException("P07 Router effect source identity changed");
    }

    EffectIdentity identity() { return new EffectIdentity(router, conflicts, work, decisions); }
    int position() { return position; }

    int nextUnit() {
        for (;;) {
            if (pending != null) {
                int unit = pending.nextUnit(numbers);
                if (unit == STREAM_END) { pending = null; offset = 0; continue; }
                if (unit >= 0) { position++; digest.accept((char)unit); }
                return unit;
            }
            if (phase == 0) {
                if (chargeMetadata(0)) return STREAM_WORK;
                phase = 1;
                pending = text("fixed-graph-router-effects@1;expansions=", Integer.valueOf(router.expansions),
                    ";rawSegments=", Integer.valueOf(router.rawSegments),
                    ";congestionRejections=", Integer.valueOf(router.congestionRejections),
                    ";conflictCount=", Integer.valueOf(conflicts.size()));
                checkToken(pending); continue;
            }
            if (phase == 1) {
                if (conflictIterator.hasNext()) {
                    if (chargeMetadata(1)) return STREAM_WORK;
                    Map.Entry<String,Integer> entry = conflictIterator.next();
                    String key = entry.getKey();
                    pending = text(";conflict=", Integer.valueOf(key.length()), ":", key, "=", entry.getValue());
                    checkToken(pending); continue;
                }
                phase = 2;
            }
            if (phase == 2) {
                if (chargeMetadata(2)) return STREAM_WORK;
                if (work == null) {
                    phase = 5;
                    pending = text(";workLength=-1;work=null");
                } else {
                    workPrefix = text("routing-work@1;outcome=", work.outcome,
                        ";reason=", work.rejectionReason,
                        ";netAttempts=", Integer.valueOf(work.routeAttempts),
                        ";branchSearches=", Integer.valueOf(work.branchSearches),
                        ";orders=", Integer.valueOf(work.orderingPasses),
                        ";ripPasses=", Integer.valueOf(work.ripUpPasses),
                        ";ripped=", Integer.valueOf(work.rippedNets),
                        ";rerouted=", Integer.valueOf(work.reroutedNets),
                        ";expansions=", Integer.valueOf(work.expansions),
                        ";failures=", Integer.valueOf(work.candidateFailures),
                        ";rawSegments=", Integer.valueOf(work.rawSegments),
                        ";conflicts=", Integer.valueOf(work.congestionRejections), ";");
                    long length = (long)workPrefix.length() + decisions.length();
                    if (length > Integer.MAX_VALUE) throw new IllegalStateException("P07 Router work effect length overflow");
                    phase = 3;
                    pending = text(";workLength=", Integer.valueOf((int)length), ";work=");
                }
                checkToken(pending); continue;
            }
            if (phase == 3) { phase = 4; pending = workPrefix; continue; }
            if (phase == 4) {
                if (decisionOffset < decisions.length()) {
                    char value = decisions.charAt(decisionOffset++);
                    position++; digest.accept(value); return value;
                }
                phase = 5;
            }
            return STREAM_END;
        }
    }

    private boolean chargeMetadata(int tag) {
        if (metadataTag != tag) { metadataTag = tag; metadataCredits = 511; return true; }
        if (metadataCredits > 0) { metadataCredits--; return true; }
        metadataTag = -1;
        return false;
    }

    private void checkToken(TextToken token) {
        if (token.length() > MAX_SOURCE_TOKEN_CHARS)
            throw new IllegalStateException("P07 Router effect token exceeds its bounded source size");
    }

    String finishSha256() {
        if (!digestFinished) { finalDigest = hex(digest.finish()); digestFinished = true; }
        return finalDigest;
    }
}


    private static final class EffectIdentity {
        final PcbNetRouter.Router router;final TreeMap<String,Integer> conflicts;final PcbRoutingWork work;final StringBuilder decisions;
        EffectIdentity(PcbNetRouter.Router router,TreeMap<String,Integer> conflicts,PcbRoutingWork work,StringBuilder decisions){this.router=router;this.conflicts=conflicts;this.work=work;this.decisions=decisions;}
    }

    private static final class PathRow {
        final int cell,x,y,layer,direction,transitions,cost,heuristic,serial,key;
        PathRow(int cell,int x,int y,int layer,int direction,int transitions,int cost,int heuristic,int serial,int key){this.cell=cell;this.x=x;this.y=y;this.layer=layer;this.direction=direction;this.transitions=transitions;this.cost=cost;this.heuristic=heuristic;this.serial=serial;this.key=key;}
        int field(int index){switch(index){case 0:return cell;case 1:return x;case 2:return y;case 3:return layer;case 4:return direction;case 5:return transitions;case 6:return cost;case 7:return heuristic;case 8:return serial;case 9:return key;default:throw new IllegalArgumentException("Invalid P07 Node tuple field");}}
    }

    /** Shared typed-target cursor. Every loop body below consumes one charged step. */
    static final class Cursor {
        private static final int COPY_BASELINE=0,COMPARE_BASELINE=1,GEOMETRY_BEFORE=2,EFFECT_BEFORE=3,
            REACHED_COPY=6,REACHED_PAIRS=7,PATH_IMPORT=8,
            GOAL_COPY=9,SCAN=10,VERIFY_PATH=11,PATH_RECHECK=12,
            REACHED_RECHECK=13,VERIFY_AFTER=14,GEOMETRY_AFTER=15,EFFECT_AFTER=16,
            EMIT_PREFIX=18,EMIT_MASKS=19,EMIT_SUFFIX=20,FINALIZE=22,COMPLETE=23,
            INITIALIZE=24,GEOMETRY_SETUP_BEFORE=25,GEOMETRY_SETUP_AFTER=26,
            QUERY_PREP_GUARD=28,PREPARE_BODY=29;
        private Context context;
        private final long[] phaseWork = new long[32];
        private final long activeStart=System.nanoTime();
        private final PcbNetRouter.Router[] pristine={null,null},query={null,null};
        private PcbPadPlacement[] reachedSnapshot;
        private char[] goalFaces;
        private char[] topMasks,bottomMasks;
        private PathRow[] path;
        private final EffectIdentity[] effectBaseline=new EffectIdentity[2];
        private final String[] effectHashes=new String[2];
        private final int[] effectLengths=new int[2];
        private final MessageDigest tupleDigest,goalDigest;
        private PcbNetRouter.Router.FixedGraphCopyTask copyTask;
        private PcbNetRouter.Router.FixedGraphComparisonTask comparisonTask;
        private PcbNetRouter.Router copyShellRouter;
        private long copyShellCredits,copyShellUnits,copyRowCredits,copyRowUnits;
        private int copyRowStage=-1,copyRowOuter=-1;
        private Object copyRowSource;
        private boolean copyShellPending;
        private GeometryWalker geometryWalker;
        private GeometryWalker geometryBaseline;
        private long geometryByteLength;
        private EffectWalker effectWalker;
        private int stage=INITIALIZE,face,scanFace,scanCell,scanDirection;
        private int reachedIndex,reachedLeft,reachedRight,pathIndex,pathEdge;
        private int pathCheckRow,pathCheckField,afterFace,goalIndex;
        private int advanceCalls,lastWork,ordinaryQueries,goalCells,goalUnionCellCount,encodedCells;
        private int pathTransitionCount,pathFlipCount,pathPlanarEdgeCount;
        private int emitIndex,emitStream,bodyUnits,emittedUnits;
        private long initializationCredits,initializationUnits,geometrySetupCredits,geometrySetupUnits,
            bodyPreparationCredits,bodyPreparationUnits=10000;
        private int utf8CreditsPending;
        private int[] counterOffsets=new int[15];
        private long workSteps,copyEntries,comparisonEntries,geometryBytes,effectUnits;
        private long reachedPadChecks,pathNodes,pathEdgesVerified,tupleBytes,activeMillis;
        private boolean liveBeforeVerified,liveAfterVerified,liveEffectsUnchanged,goalVerified,originalPathVerified;
        private boolean complete,activeMeasured,tupleFinalized,aborted;
        private String pathTupleSha256,goalFaceBitsSha256,prefixGeometrySha256;
        private TextToken pendingTuple;
        private final NumberScratch tupleNumbers=new NumberScratch();
        private String bodyPrefix,bodySuffix;
        private String emitLiteral;
        private int emitLiteralOffset,emitLiteralTargetStream;
        private Result result;
        private static final String[] COUNTERS={"advanceCalls","workSteps","copyEntries","comparisonEntries",
            "geometryBytes","effectUnits","ordinaryQueries","goalCells","reachedPadChecks","encodedCells",
            "pathNodes","pathEdgesVerified","tupleBytes","serializedUtf16Units","activeMillis"};

        private Cursor(Context context){
            if(context==null)throw new IllegalArgumentException("Missing P07 zero-via graph context");
            this.context=context;
            initializationUnits=3L*context.cells+context.input.pathNodeCount()+context.reached.length;
            tupleDigest=digest();goalDigest=digest();
        }

        boolean advance(int maximumWork){
            if(complete){lastWork=0;return true;}
            if(aborted){lastWork=0;return false;}
            lastWork=0;
            try{
                if(maximumWork<1||maximumWork>MAX_ADVANCE_WORK)throw new IllegalArgumentException("P07 zero-via advance work must be in 1..32768");
                advanceCalls++;
                if(context.callerFaces[TOP]!=context.faces[TOP] || context.callerFaces[BOTTOM]!=context.faces[BOTTOM])
                    throw changed("caller replaced a retained physical face Router");
                int remaining=maximumWork;
                while(remaining>0&&!complete){
                    int enteredStage=stage;
                    if(utf8CreditsPending>0){utf8CreditsPending--;phaseWork[enteredStage]++;lastWork++;workSteps++;remaining--;continue;}
                    step();phaseWork[enteredStage]++;lastWork++;workSteps++;remaining--;
                }
                if(lastWork<1||lastWork>maximumWork)throw new IllegalStateException("P07 zero-via cursor made an invalid debit");
                if(complete&&result==null)buildResult();
                return complete;
            }catch(RuntimeException failure){abortAfterFailure();throw failure;}
            catch(Error failure){abortAfterFailure();throw failure;}
        }
        private void step(){
            if(stage==INITIALIZE){
                if(initializationCredits<initializationUnits)initializationCredits++;
                else{reachedSnapshot=new PcbPadPlacement[context.reached.length];goalFaces=new char[context.cells];topMasks=new char[context.cells];bottomMasks=new char[context.cells];path=new PathRow[context.input.pathNodeCount()];stage=COPY_BASELINE;}
            }else if(stage==COPY_BASELINE){
                if(copyTask==null&&copyShellStep())return;
                if(copyRowStep())return;
                int before=copyTask.copiedEntries();boolean done=copyTask.advanceOne(1);int after=copyTask.copiedEntries();
                if(after<before)throw changed("router copy counter regressed");copyEntries+=after-before;
                if(done){pristine[face]=copyTask.result();copyTask=null;copyRowStage=-1;comparisonTask=context.faces[face].beginFixedGraphComparison(pristine[face]);stage=COMPARE_BASELINE;}
            }else if(stage==COMPARE_BASELINE){
                if(compareRouterOne()){comparisonTask=null;face++;if(face==2){face=0;liveBeforeVerified=true;geometrySetupUnits=GeometryWalker.storageUnits(context.layout);geometrySetupCredits=0;stage=GEOMETRY_SETUP_BEFORE;}else stage=COPY_BASELINE;}
            }else if(stage==GEOMETRY_SETUP_BEFORE){
                if(geometrySetupCredits<geometrySetupUnits)geometrySetupCredits++;
                else{if(GeometryWalker.storageUnits(context.layout)!=geometrySetupUnits)throw changed("layout row storage shape changed before capture");geometryWalker=new GeometryWalker(context.layout);stage=GEOMETRY_BEFORE;}
            }else if(stage==GEOMETRY_BEFORE){
                int c=geometryWalker.nextUnit();if(c==STREAM_END){prefixGeometrySha256=geometryWalker.finishSha256();addGeometryBytes(geometryWalker.lastByteCount());geometryByteLength=geometryWalker.byteLength();geometryBaseline=geometryWalker;geometryWalker=null;face=0;effectWalker=new EffectWalker(context.faces[face]);stage=EFFECT_BEFORE;}
                else if(c>=0)addGeometryBytes(geometryWalker.lastByteCount());
            }else if(stage==EFFECT_BEFORE){
                int c=effectWalker.nextUnit();if(c==STREAM_END){effectHashes[face]=effectWalker.finishSha256();effectLengths[face]=effectWalker.position();effectBaseline[face]=effectWalker.identity();effectWalker=null;face++;if(face==2){
                    // Context and QUERY_PREP_GUARD require the current net/width.
                    // prepareNet then returns without changing any fixed-graph
                    // field; queries change only clone counters/conflicts/work.
                    // The complete live-vs-snapshot comparison still runs on
                    // both sides, and live effects have their separate oracle.
                    query[TOP]=pristine[TOP];query[BOTTOM]=pristine[BOTTOM];
                    face=0;stage=REACHED_COPY;
                }else effectWalker=new EffectWalker(context.faces[face]);}
                else if(c>=0)effectUnits++;
            }else if(stage==REACHED_COPY){
                if(reachedIndex>=context.reached.length){reachedLeft=0;reachedRight=1;stage=REACHED_PAIRS;}
                else{PcbPadPlacement pad=context.reached[reachedIndex];if(pad==null||context.layout.getPad(pad.getPadId())!=pad||pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE||pad==context.root)throw changed("invalid reached-PTH authority");reachedSnapshot[reachedIndex++]=pad;reachedPadChecks++;}
            }else if(stage==REACHED_PAIRS){
                if(reachedLeft>=reachedSnapshot.length-1){pathIndex=0;stage=PATH_IMPORT;}
                else if(reachedRight>=reachedSnapshot.length){reachedLeft++;reachedRight=reachedLeft+1;}
                else{if(reachedSnapshot[reachedLeft]==reachedSnapshot[reachedRight])throw changed("duplicate reached-PTH identity");reachedRight++;reachedPadChecks++;}
            }else if(stage==PATH_IMPORT){importPathOneUnit();}
            else if(stage==GOAL_COPY){copyGoalCell();}
            else if(stage==QUERY_PREP_GUARD){for(int i=0;i<2;i++)if(!context.net.equals(readField(query[i],"activeNetId"))||((Integer)readField(query[i],"activeTraceWidth")).intValue()!=context.expectedTraceWidth)throw changed("query Router would require unbounded prepareNet work");scanFace=scanCell=scanDirection=0;stage=SCAN;}
            else if(stage==SCAN){scanOneDirection();}
            else if(stage==VERIFY_PATH){
                verifyOnePathEdge();if(pathEdge==path.length-1){originalPathVerified=true;PathRow end=path[path.length-1];goalVerified=(nibble(goalFaces[end.cell])&(1<<end.layer))!=0;if(!goalVerified)throw changed("Publisher endpoint is absent from its face goal mask");pathCheckRow=0;pathCheckField=0;stage=PATH_RECHECK;}
            }else if(stage==PATH_RECHECK){
                if(pathCheckRow>=path.length){reachedIndex=0;stage=REACHED_RECHECK;}
                else{if(sourcePathField(pathCheckRow,pathCheckField)!=path[pathCheckRow].field(pathCheckField))throw changed("source Node tuple changed after graph scan");pathCheckField++;if(pathCheckField==10){pathCheckField=0;pathCheckRow++;}}
            }else if(stage==REACHED_RECHECK){
                if(reachedIndex>=reachedSnapshot.length){afterFace=0;comparisonTask=null;stage=VERIFY_AFTER;}
                else{PcbPadPlacement source=context.reached[reachedIndex];if(source!=reachedSnapshot[reachedIndex]||context.layout.getPad(source.getPadId())!=source)throw changed("reached-PTH source changed after scan");reachedIndex++;reachedPadChecks++;}
            }else if(stage==VERIFY_AFTER){
                if(comparisonTask==null)comparisonTask=context.faces[afterFace].beginFixedGraphComparison(pristine[afterFace]);
                if(compareRouterOne()){comparisonTask=null;afterFace++;if(afterFace==2){geometrySetupUnits=GeometryWalker.storageUnits(context.layout);geometrySetupCredits=0;stage=GEOMETRY_SETUP_AFTER;}}
            }else if(stage==GEOMETRY_SETUP_AFTER){
                if(geometrySetupCredits<geometrySetupUnits)geometrySetupCredits++;
                else{if(GeometryWalker.storageUnits(context.layout)!=geometrySetupUnits)throw changed("layout row storage shape changed before recheck");geometryWalker=new GeometryWalker(context.layout,geometryBaseline);stage=GEOMETRY_AFTER;}
            }else if(stage==GEOMETRY_AFTER){
                int c=geometryWalker.nextUnit();if(c==STREAM_END){addGeometryBytes(geometryWalker.finishByteCount());if(!geometryWalker.identitiesComplete()||geometryWalker.byteLength()!=geometryByteLength||!prefixGeometrySha256.equals(geometryWalker.finishSha256()))throw changed("full canonical layout geometry digest/length or source identity changed");geometryWalker=null;face=0;effectWalker=new EffectWalker(context.faces[face],effectBaseline[face]);stage=EFFECT_AFTER;}
                else if(c>=0)addGeometryBytes(geometryWalker.lastByteCount());
            }else if(stage==EFFECT_AFTER){
                int c=effectWalker.nextUnit();if(c==STREAM_END){if(effectWalker.position()!=effectLengths[face]||!effectHashes[face].equals(effectWalker.finishSha256()))throw changed("full canonical Router effect digest/length changed");effectWalker=null;face++;if(face==2){liveAfterVerified=true;liveEffectsUnchanged=true;stage=PREPARE_BODY;}else effectWalker=new EffectWalker(context.faces[face],effectBaseline[face]);}
                else if(c>=0)effectUnits++;
            }else if(stage==PREPARE_BODY){
                if(bodyPreparationCredits<bodyPreparationUnits)bodyPreparationCredits++;
                else{prepareBody();stage=EMIT_PREFIX;}
            }else if(stage==EMIT_PREFIX){
                if(emitIndex<bodyPrefix.length()){emitIndex++;emittedUnits++;}else{emitIndex=0;emitStream=0;stage=EMIT_MASKS;}
            }else if(stage==EMIT_MASKS){emitMaskOneUnit();}
            else if(stage==EMIT_SUFFIX){if(emitIndex<bodySuffix.length()){emitIndex++;emittedUnits++;}else{if(emittedUnits!=bodyUnits)throw changed("prepared diagnostic length differs from its serialized frame");stage=FINALIZE;}}
            else if(stage==FINALIZE){activeMillis=elapsedNanosSince(activeStart)/1000000L;activeMeasured=true;patchCounters();bodyPrefix=prefixBuilder.toString();complete=true;stage=COMPLETE;}
            else throw new IllegalStateException("Unknown P07 zero-via graph cursor stage");
        }
        private static final int MAX_PAYLOAD_UNITS=1000000;
        private static int builderGrowthCredits(StringBuilder builder){long old=builder.capacity(),next=Math.max(old*2L+2L,(long)builder.length()+1L),credits=old+next;if(credits>Integer.MAX_VALUE)throw new IllegalStateException("P07 diagnostic builder growth credit overflow");return (int)credits;}
        private boolean compareRouterOne(){int before=comparisonTask.comparedEntries();boolean done=comparisonTask.advanceOne(1);int after=comparisonTask.comparedEntries();if(after<before)throw changed("Router comparison counter regressed");comparisonEntries+=after-before;if(done&&!comparisonTask.equal())throw changed("live Router graph changed");return done;}
        private boolean copyShellStep(){
            PcbNetRouter.Router source=context.faces[face];
            if(!copyShellPending || copyShellRouter!=source){
                copyShellRouter=source;copyShellUnits=routerShellArrayUnits(source);copyShellCredits=0;copyShellPending=true;
            }
            if(copyShellCredits<copyShellUnits){copyShellCredits++;return true;}
            if(source!=context.faces[face] || routerShellArrayUnits(source)!=copyShellUnits)
                throw changed("Router copy shell source shape changed during precharge");
            copyTask=source.beginFixedGraphObservationCopy();copyShellPending=false;copyRowStage=-1;return true;
        }
        private static long routerShellArrayUnits(PcbNetRouter.Router router){
            String[] names={"occupiedNet","clearanceNet","pads","padNets","padWidths","padBounds",
                "escapeCorridors","escapeReservations","components","courtyards","allComponentIndexes",
                "componentCandidatesByColumn","collisionCourtyards","horizontalReservation",
                "verticalReservation","padAt"};
            long units=1;
            for(String name:names){Object value=readField(router,name);if(value!=null){if(!value.getClass().isArray())throw new IllegalStateException("P07 Router shell field is not an array: "+name);units+=Array.getLength(value);}}
            return units;
        }
        private boolean copyRowStep(){
            int sourceStage=((Integer)readField(copyTask,"stage")).intValue();
            String fieldName;
            if(sourceStage==0)fieldName="occupiedNet";else if(sourceStage==1)fieldName="clearanceNet";
            else if(sourceStage==2)fieldName="horizontalReservation";else if(sourceStage==3)fieldName="verticalReservation";
            else if(sourceStage==4)fieldName="padAt";else if(sourceStage==14)fieldName="componentCandidatesByColumn";
            else{copyRowStage=-1;copyRowOuter=-1;copyRowSource=null;copyRowCredits=copyRowUnits=0;return false;}
            int outer=((Integer)readField(copyTask,"outer")).intValue();
            Object sourceGrid=readField(context.faces[face],fieldName);
            Object targetRouter=readField(copyTask,"target");
            Object targetGrid=readField(targetRouter,fieldName);
            if(sourceGrid==null || outer>=Array.getLength(sourceGrid))return false;
            Object sourceRow=Array.get(sourceGrid,outer);
            Object targetRow=targetGrid==null?null:Array.get(targetGrid,outer);
            if(sourceRow==null || targetRow!=null){copyRowStage=-1;copyRowOuter=-1;copyRowSource=null;copyRowCredits=copyRowUnits=0;return false;}
            long rowLength=Array.getLength(sourceRow);
            if(copyRowStage!=sourceStage || copyRowOuter!=outer || copyRowSource!=sourceRow){
                copyRowStage=sourceStage;copyRowOuter=outer;copyRowSource=sourceRow;copyRowUnits=rowLength;copyRowCredits=0;
            }
            if(copyRowCredits<copyRowUnits){copyRowCredits++;return true;}
            if(((Integer)readField(copyTask,"stage")).intValue()!=sourceStage ||
                    ((Integer)readField(copyTask,"outer")).intValue()!=outer ||
                    Array.get(sourceGrid,outer)!=sourceRow || Array.get(targetGrid,outer)!=null)
                throw changed("Router copy row source changed during array-allocation precharge");
            copyRowStage=-1;copyRowOuter=-1;copyRowSource=null;copyRowCredits=copyRowUnits=0;
            return false;
        }
        private void addGeometryBytes(int bytes){if(bytes<0)throw changed("geometry UTF-8 byte count regressed");geometryBytes+=bytes;if(bytes>1)utf8CreditsPending+=bytes-1;if(geometryBytes>16000000L)throw changed("canonical geometry exceeds its bounded source size");}
        private void importPathOneUnit(){
            if(pendingTuple!=null){
                int value=pendingTuple.nextUnit(tupleNumbers);
                if(value>=0){tupleDigest.update((byte)value);tupleBytes++;return;}
                if(value==STREAM_WORK)return;
                pendingTuple=null;
            }
            if(pathIndex>=path.length){
                if(tupleFinalized)throw changed("path import advanced beyond its finalizer");
                tupleFinalized=true;pathTupleSha256=hex(tupleDigest.digest());
                if(context.input.pathTupleSha256()!=null&&!context.input.pathTupleSha256().equals(pathTupleSha256))throw changed("LOAD12 source path tuple hash mismatch");
                if((context.target==Target.D_SENSE||context.target==Target.OUT_F) &&
                        (context.input.pathTupleSha256()!=null || context.input.goalFaceBitsSha256()!=null ||
                            context.input.goalCellCount()!=-1))
                    throw changed("target without PreResult metadata supplied an external path/goal result");
                goalIndex=0;stage=GOAL_COPY;return;
            }
            int i=pathIndex;
            PathRow row=new PathRow(context.input.pathCellAt(i),context.input.pathXAt(i),context.input.pathYAt(i),context.input.pathLayerAt(i),
                context.input.pathDirectionAt(i),context.input.pathTransitionsAt(i),context.input.pathCostAt(i),context.input.pathHeuristicAt(i),
                context.input.pathSerialAt(i),context.input.pathKeyAt(i));
            validatePathRow(i,row);path[i]=row;pathNodes++;
            if(i>0){if(row.layer==path[i-1].layer)pathPlanarEdgeCount++;else{pathFlipCount++;pathTransitionCount++;}}
            TextToken tuple=text(i,",",i==0?-1:i-1,",",row.cell,",",row.x,",",row.y,",",row.layer,",",row.direction,",",
                row.transitions,",",row.cost,",",row.heuristic,",",row.serial,",",row.key);
            pendingTuple=i==0?tuple:text("\n",tuple);pathIndex++;
        }
        private void validatePathRow(int i,PathRow row){
            if(row.layer<TOP||row.layer>BOTTOM||row.direction<0||row.direction>4||row.transitions<0||row.transitions>4||row.cell!=cell(row.x,row.y,context.originX,context.originY,context.width,context.height,context.pitch))throw changed("invalid source Node cell or face");
            long expected=((((long)row.transitions*2+row.layer)*5+row.direction)*(long)context.cells)+row.cell;if(expected!=row.key)throw changed("source Node key is inconsistent");
            if(i==0){if(row.x!=context.start.getX()||row.y!=context.start.getY()||row.direction!=4||row.transitions!=0||(context.initialLayerMask&(1<<row.layer))==0)throw changed("source root does not match start pad/seed face");}
            else{PathRow previous=path[i-1];long distance=Math.abs((long)row.x-previous.x)+Math.abs((long)row.y-previous.y);
                if(row.layer==previous.layer){if(distance!=context.pitch||row.transitions!=previous.transitions||row.direction!=direction(previous.x,previous.y,row.x,row.y))throw changed("planar path edge is not one same-face grid step");}
                else if(row.cell!=previous.cell||row.x!=previous.x||row.y!=previous.y||row.direction!=4||row.transitions!=previous.transitions+1||pathFlipCount+1>4)throw changed("layer change is not a bounded coincident face flip");}
        }
        private int sourcePathField(int row,int field){switch(field){case 0:return context.input.pathCellAt(row);case 1:return context.input.pathXAt(row);case 2:return context.input.pathYAt(row);case 3:return context.input.pathLayerAt(row);case 4:return context.input.pathDirectionAt(row);case 5:return context.input.pathTransitionsAt(row);case 6:return context.input.pathCostAt(row);case 7:return context.input.pathHeuristicAt(row);case 8:return context.input.pathSerialAt(row);case 9:return context.input.pathKeyAt(row);default:throw new IllegalArgumentException();}}
        private void copyGoalCell(){
            int c=goalIndex++;boolean top=context.sourceGoalMask[c],bottom=context.sourceGoalMask[context.cells+c];
            int mask=(top?1:0)|(bottom?2:0);goalFaces[c]=(char)mask;goalCells++;
            if(mask!=0)goalUnionCellCount++;
            if(context.target==Target.LOAD12&&context.input.expectedGoalFaceMaskAt(c)!=mask)throw changed("live goal cell differs from immutable PreResult");
            if((context.target==Target.D_SENSE||context.target==Target.OUT_F) &&
                    context.input.expectedGoalFaceMaskAt(c)!=-1)
                throw changed("target without PreResult metadata supplied an external goal mask");
            goalDigest.update((byte)('0'+mask));
            if(goalIndex==context.cells){
                goalFaceBitsSha256=hex(goalDigest.digest());
                if(context.target==Target.LOAD12&&(!goalFaceBitsSha256.equals(context.input.goalFaceBitsSha256())||goalUnionCellCount!=context.input.goalCellCount()))throw changed("live goal hash/union differs from immutable PreResult");
                if((context.target==Target.D_SENSE||context.target==Target.OUT_F) &&
                        (context.input.goalFaceBitsSha256()!=null || context.input.goalCellCount()!=-1))
                    throw changed("target without PreResult metadata supplied an external goal result");
                stage=QUERY_PREP_GUARD;
            }
        }
        private void scanOneDirection(){int xIndex=scanCell%context.width,yIndex=scanCell/context.width,nxIndex=xIndex+DX[scanDirection],nyIndex=yIndex+DY[scanDirection];if(nxIndex>=0&&nxIndex<context.width&&nyIndex>=0&&nyIndex<context.height){int x=coordinate(context.originX,xIndex,context.pitch),y=coordinate(context.originY,yIndex,context.pitch),nx=coordinate(context.originX,nxIndex,context.pitch),ny=coordinate(context.originY,nyIndex,context.pitch);if(query[scanFace].permitsLayerStep(x,y,nx,ny,context.net,context.start,context.root,reachedSnapshot)){if(scanFace==TOP)topMasks[scanCell]=(char)(nibble(topMasks[scanCell])|(1<<scanDirection));else bottomMasks[scanCell]=(char)(nibble(bottomMasks[scanCell])|(1<<scanDirection));}ordinaryQueries++;}scanDirection++;if(scanDirection==4){scanDirection=0;scanCell++;if(scanCell==context.cells){scanCell=0;scanFace++;if(scanFace==2){pathEdge=0;stage=VERIFY_PATH;}}}}
        private void verifyOnePathEdge(){PathRow a=path[pathEdge],b=path[pathEdge+1];if(a.layer==b.layer){int d=direction(a.x,a.y,b.x,b.y);char[] masks=a.layer==TOP?topMasks:bottomMasks;if((nibble(masks[a.cell])&(1<<d))==0)throw changed("actual planar path edge is absent from captured router graph");}else if(a.cell!=b.cell||a.x!=b.x||a.y!=b.y||b.transitions!=a.transitions+1)throw changed("actual transition is not an annotated coincident flip");pathEdgesVerified++;pathEdge++;}
        private StringBuilder prefixBuilder;
        private void prepareBody(){
            prefixBuilder=new StringBuilder(1500);PathRow first=path[0],last=path[path.length-1];
            prefixBuilder.append("{\"schema\":\"p07-zero-via-graph-r5\",\"stability\":{\"prefixGeometrySha256\":\"").append(prefixGeometrySha256).append("\",\"liveBeforeVerified\":true,\"liveAfterVerified\":true,\"liveEffectsUnchanged\":true,\"goalVerified\":true,\"originalPathVerified\":true},\"grid\":{\"width\":").append(context.width).append(",\"height\":").append(context.height).append(",\"cells\":").append(context.cells).append(",\"originX\":").append(context.originX).append(",\"originY\":").append(context.originY).append(",\"pitch\":").append(context.pitch).append(",\"startCell\":").append(first.cell).append(",\"initialLayerMask\":").append(context.initialLayerMask).append(",\"endCell\":").append(last.cell).append(",\"endLayer\":").append(last.layer).append(",\"goalUnionCellCount\":").append(goalUnionCellCount).append("},\"path\":{\"nodeCount\":").append(path.length).append(",\"planarEdgeCount\":").append(pathPlanarEdgeCount).append(",\"transitionCount\":").append(pathTransitionCount).append(",\"tupleSha256\":\"").append(pathTupleSha256).append("\"},\"work\":{");
            for(int i=0;i<COUNTERS.length;i++){if(i>0)prefixBuilder.append(',');prefixBuilder.append('"').append(COUNTERS[i]).append("\":");counterOffsets[i]=prefixBuilder.length();for(int d=0;d<19;d++)prefixBuilder.append('0');}
            prefixBuilder.append("},\"masks\":{\"topStepsHex\":\"");if(prefixBuilder.length()>1500)throw changed("fixed metadata prefix exceeds its precharged capacity");bodyPrefix=prefixBuilder.toString();bodySuffix="\"}}";
            bodyUnits=bodyPrefix.length()+3*context.cells+BOTTOM_SEPARATOR.length()+GOAL_SEPARATOR.length()+bodySuffix.length();if(bodyUnits>MAX_PAYLOAD_UNITS)throw changed("diagnostic frame exceeds one-megabyte bound");
        }
        private void emitMaskOneUnit(){
            if(emitLiteral!=null){
                emitLiteralOffset++;emittedUnits++;
                if(emitLiteralOffset==emitLiteral.length()){emitLiteral=null;emitLiteralOffset=0;emitStream=emitLiteralTargetStream;emitIndex=0;}
                return;
            }
            if(emitStream==0){
                if(emitIndex<context.cells){int c=emitIndex++;topMasks[c]=HEX.charAt(nibble(topMasks[c]));encodedCells++;emittedUnits++;}
                else{emitLiteral=BOTTOM_SEPARATOR;emitLiteralTargetStream=1;}
            }else if(emitStream==1){
                if(emitIndex<context.cells){int c=emitIndex++;bottomMasks[c]=HEX.charAt(nibble(bottomMasks[c]));encodedCells++;emittedUnits++;}
                else{emitLiteral=GOAL_SEPARATOR;emitLiteralTargetStream=2;}
            }else if(emitIndex<context.cells){
                int c=emitIndex++,mask=nibble(goalFaces[c]);
                if(context.sourceGoalMask[c]!=((mask&1)!=0)||context.sourceGoalMask[context.cells+c]!=((mask&2)!=0))throw changed("live goal source changed after graph scan");
                if(context.target==Target.LOAD12&&context.input.expectedGoalFaceMaskAt(c)!=mask)throw changed("emitted goal nibble differs from PreResult");
                goalFaces[c]=HEX.charAt(mask);encodedCells++;emittedUnits++;
            }else{emitIndex=0;stage=EMIT_SUFFIX;}
        }
        private void patchCounters(){
            long[] values={advanceCalls,workSteps+1,copyEntries,comparisonEntries,geometryBytes,effectUnits,ordinaryQueries,goalCells,
                reachedPadChecks,encodedCells,pathNodes,pathEdgesVerified,tupleBytes,bodyUnits,activeMillis};
            for(int i=0;i<values.length;i++)prefixBuilder.replace(counterOffsets[i],counterOffsets[i]+19,fixed19(values[i]));
        }
        private void buildResult(){
            PathRow first=path[0],last=path[path.length-1];
            result=new Result(context,prefixGeometrySha256,pathTupleSha256,goalFaceBitsSha256,liveBeforeVerified,liveAfterVerified,
                liveEffectsUnchanged,goalVerified,originalPathVerified,first.cell,last.cell,last.layer,path.length,pathTransitionCount,
                pathPlanarEdgeCount,goalUnionCellCount,ordinaryQueries,goalCells,encodedCells,advanceCalls,workSteps,copyEntries,
                comparisonEntries,geometryBytes,effectUnits,reachedPadChecks,pathNodes,pathEdgesVerified,tupleBytes,bodyUnits,
                activeMillis,bodyPrefix,topMasks,bottomMasks,goalFaces,phaseWork.clone());
            // Ownership transfer: completed cursors cannot alter or expose these arrays.
            topMasks=bottomMasks=goalFaces=null;
            context=null;
            pristine[TOP]=pristine[BOTTOM]=query[TOP]=query[BOTTOM]=null;
            reachedSnapshot=null;path=null;
            effectBaseline[TOP]=effectBaseline[BOTTOM]=null;
            geometryWalker=null;geometryBaseline=null;effectWalker=null;
            copyTask=null;comparisonTask=null;copyShellRouter=null;copyRowSource=null;
            prefixBuilder=null;pendingTuple=null;bodyPrefix=null;bodySuffix=null;emitLiteral=null;
        }
        private IllegalStateException changed(String message){return new IllegalStateException("P07 zero-via "+message);}
        int lastWork(){return lastWork;} boolean isComplete(){return complete;}
        boolean isAborted(){return aborted;}
        boolean sourcesReleased(){
            return context==null&&pristine[TOP]==null&&pristine[BOTTOM]==null&&
                query[TOP]==null&&query[BOTTOM]==null&&reachedSnapshot==null&&goalFaces==null&&
                topMasks==null&&bottomMasks==null&&path==null&&effectBaseline[TOP]==null&&
                effectBaseline[BOTTOM]==null&&copyTask==null&&comparisonTask==null&&
                copyShellRouter==null&&copyRowSource==null&&geometryWalker==null&&
                geometryBaseline==null&&effectWalker==null&&pendingTuple==null&&
                prefixBuilder==null&&bodyPrefix==null&&bodySuffix==null&&emitLiteral==null;
        }
        void cancel(){
            if(complete||aborted)return;
            aborted=true;lastWork=0;releaseIncompleteSources();
        }
        private void abortAfterFailure(){
            if(result!=null)return;
            complete=false;aborted=true;releaseIncompleteSources();
        }
        private void releaseIncompleteSources(){
            context=null;pristine[TOP]=pristine[BOTTOM]=query[TOP]=query[BOTTOM]=null;
            reachedSnapshot=null;goalFaces=null;topMasks=null;bottomMasks=null;path=null;
            effectBaseline[TOP]=effectBaseline[BOTTOM]=null;copyTask=null;comparisonTask=null;
            copyShellRouter=null;copyRowSource=null;geometryWalker=null;geometryBaseline=null;
            effectWalker=null;pendingTuple=null;prefixBuilder=null;bodyPrefix=null;bodySuffix=null;
            emitLiteral=null;copyShellPending=false;copyRowStage=-1;
        }
        String failureProgressJson(){
            return "{\"complete\":"+complete+",\"stage\":"+stage+",\"advanceCalls\":"+advanceCalls+
                ",\"workSteps\":"+workSteps+",\"copyEntries\":"+copyEntries+
                ",\"comparisonEntries\":"+comparisonEntries+",\"geometryBytes\":"+geometryBytes+
                ",\"effectUnits\":"+effectUnits+",\"ordinaryQueries\":"+ordinaryQueries+
                ",\"phaseWork\":"+java.util.Arrays.toString(phaseWork)+"}";
        }
        Result result(){if(!complete||result==null)throw new IllegalStateException("P07 zero-via graph result is incomplete");return result;}
    }

    static Cursor begin(Context context){return new Cursor(context);}

    static final class Result {
        final Target target;final String sourceKind,pathTupleSha256,goalFaceBitsSha256,prefixGeometrySha256;
        final int width,height,cells,originX,originY,pitch,startCell,endCell,endLayer,initialLayerMask,pathNodeCount,pathTransitionCount,pathPlanarEdgeCount,goalUnionCellCount,advanceCalls,ordinaryQueries,goalCells,encodedCells;
        final long workSteps,copyEntries,comparisonEntries,geometryBytes,effectUnits,reachedPadChecks,pathNodes,pathEdgesVerified,tupleBytes,serializedUtf16Units,activeMillis;
        final boolean liveBeforeVerified,liveAfterVerified,liveEffectsUnchanged,goalVerified,originalPathVerified;
        private final String header;private char[] top,bottom,goals;private final long[] phaseWork;
        private boolean bodyReleased;
        private Result(Context c,String geometry,String tuple,String goalHash,boolean before,boolean after,boolean effects,boolean goal,boolean path,int start,int end,int endLayer,int nodes,int transitions,int planar,int union,int queries,int goalCells,int encoded,int calls,long steps,long copies,long comparisons,long geometryBytes,long effectUnits,long reachedChecks,long pathNodes,long pathEdges,long tupleBytes,long serialized,long millis,String header,char[] top,char[] bottom,char[] goals,long[] phases){
            target=c.target;sourceKind=c.input.sourceKind();pathTupleSha256=tuple;goalFaceBitsSha256=goalHash;prefixGeometrySha256=geometry;width=c.width;height=c.height;cells=c.cells;originX=c.originX;originY=c.originY;pitch=c.pitch;startCell=start;endCell=end;this.endLayer=endLayer;initialLayerMask=c.initialLayerMask;pathNodeCount=nodes;pathTransitionCount=transitions;pathPlanarEdgeCount=planar;goalUnionCellCount=union;advanceCalls=calls;ordinaryQueries=queries;this.goalCells=goalCells;encodedCells=encoded;workSteps=steps;copyEntries=copies;comparisonEntries=comparisons;this.geometryBytes=geometryBytes;this.effectUnits=effectUnits;reachedPadChecks=reachedChecks;this.pathNodes=pathNodes;pathEdgesVerified=pathEdges;this.tupleBytes=tupleBytes;serializedUtf16Units=serialized;activeMillis=millis;liveBeforeVerified=before;liveAfterVerified=after;liveEffectsUnchanged=effects;goalVerified=goal;originalPathVerified=path;this.header=header;this.top=top;this.bottom=bottom;this.goals=goals;phaseWork=phases;if(top==null||bottom==null||goals==null||top.length!=cells||bottom.length!=cells||goals.length!=cells||bodyLength()!=serialized)throw new IllegalStateException("Immutable graph body has inconsistent dimensions");
        }
        int bodyLength(){
            if(bodyReleased)throw new IllegalStateException("Immutable graph body masks were released");
            return header.length()+top.length+bottom.length+goals.length+BOTTOM_SEPARATOR.length()+GOAL_SEPARATOR.length()+BODY_SUFFIX.length();
        }
        char bodyCharAt(int index){
            int length=bodyLength();
            if(index<0||index>=length)throw new IndexOutOfBoundsException();
            if(index<header.length())return header.charAt(index);
            index-=header.length();
            if(index<top.length)return top[index];
            index-=top.length;
            if(index<BOTTOM_SEPARATOR.length())return BOTTOM_SEPARATOR.charAt(index);
            index-=BOTTOM_SEPARATOR.length();
            if(index<bottom.length)return bottom[index];
            index-=bottom.length;
            if(index<GOAL_SEPARATOR.length())return GOAL_SEPARATOR.charAt(index);
            index-=GOAL_SEPARATOR.length();
            if(index<goals.length)return goals[index];
            index-=goals.length;
            return BODY_SUFFIX.charAt(index);
        }
        long[] phaseWorkCopy(){return phaseWork.clone();}
        boolean bodyReleased(){return bodyReleased;}
        void releaseBodyMasks(){
            if(!bodyReleased){top=null;bottom=null;goals=null;bodyReleased=true;}
        }
        void writeBody(java.io.PrintStream output){
            if(output==null||output.getClass()!=java.io.PrintStream.class)
                throw new IllegalArgumentException("Graph output requires an exact PrintStream");
            if(bodyReleased)throw new IllegalStateException("Immutable graph body masks were released");
            output.print(header);output.print(top);output.print(BOTTOM_SEPARATOR);output.print(bottom);
            output.print(GOAL_SEPARATOR);output.print(goals);output.print(BODY_SUFFIX);
        }
        String failureProgressJson(){
            return "{\"complete\":true,\"advanceCalls\":"+advanceCalls+",\"workSteps\":"+workSteps+
                ",\"copyEntries\":"+copyEntries+",\"comparisonEntries\":"+comparisonEntries+
                ",\"geometryBytes\":"+geometryBytes+",\"effectUnits\":"+effectUnits+
                ",\"ordinaryQueries\":"+ordinaryQueries+",\"phaseWork\":"+java.util.Arrays.toString(phaseWork)+"}";
        }
        char maskCharAt(int stream,int cell){if(stream<0||stream>2||cell<0||cell>=cells)throw new IndexOutOfBoundsException();return (stream==0?top:stream==1?bottom:goals)[cell];}
    }

    private static final java.util.HashMap<String,Field> FIELD_CACHE=new java.util.HashMap<String,Field>();
    private static Object readField(Object owner,String name){
        if(owner==null)throw new IllegalArgumentException("Missing application source for read-only field capture");
        String key=owner.getClass().getName()+"#"+name;Field value=FIELD_CACHE.get(key);
        if(value==null){Class<?> type=owner.getClass();while(type!=null&&value==null){try{value=type.getDeclaredField(name);}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
            if(value==null)throw new IllegalStateException("Missing read-only application field: "+key);
            try{value.setAccessible(true);}catch(RuntimeException inaccessible){throw new IllegalStateException("Cannot read application source field: "+key,inaccessible);}
            FIELD_CACHE.put(key,value);
        }
        try{return value.get(owner);}catch(IllegalAccessException inaccessible){throw new IllegalStateException("Cannot read application source field: "+key,inaccessible);}
    }
    private static boolean isSha256(String value){if(value==null||value.length()!=64)return false;for(int i=0;i<value.length();i++)if("0123456789abcdef".indexOf(value.charAt(i))<0)return false;return true;}
    private static String fixed19(long value){if(value<0)throw new IllegalArgumentException("Negative P07 graph counter");String digits=Long.toString(value);if(digits.length()>19)throw new IllegalStateException("P07 graph counter exceeds its fixed JSON span");StringBuilder out=new StringBuilder(19);for(int i=digits.length();i<19;i++)out.append(' ');return out.append(digits).toString();}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte item:bytes){int n=item&255;if(n<16)out.append('0');out.append(Integer.toHexString(n));}return out.toString();}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException missing){throw new IllegalStateException("SHA-256 is required for P07 graph identity",missing);}}
    private static final class Utf8State {
        private final MessageDigest digest=digest();private boolean pendingHigh;
        int lastByteCount;
        int accept(char value){int count=0;if(pendingHigh){if(Character.isLowSurrogate(value)){int cp=Character.toCodePoint((char)pendingHighValue,value);write(0xF0|((cp>>18)&7));write(0x80|((cp>>12)&63));write(0x80|((cp>>6)&63));write(0x80|(cp&63));pendingHigh=false;lastByteCount=4;return 4;}write('?');count++;pendingHigh=false;}
            if(Character.isHighSurrogate(value)){pendingHigh=true;pendingHighValue=value;lastByteCount=count;return count;}
            if(Character.isLowSurrogate(value)){write('?');count++;}
            else if(value<=0x7f){write(value);count++;}
            else if(value<=0x7ff){write(0xc0|(value>>6));write(0x80|(value&63));count+=2;}
            else{write(0xe0|(value>>12));write(0x80|((value>>6)&63));write(0x80|(value&63));count+=3;}
            lastByteCount=count;return count;}
        private char pendingHighValue;
        private void write(int value){digest.update((byte)value);}
        byte[] finish(){lastByteCount=0;if(pendingHigh){write('?');lastByteCount=1;pendingHigh=false;}return digest.digest();}
    }
    private static int direction(int fromX,int fromY,int toX,int toY){if(toY<fromY)return 0;if(toX>fromX)return 1;if(toY>fromY)return 2;if(toX<fromX)return 3;throw new IllegalArgumentException("P07 path contains a zero-length planar edge");}
    private static final class LiteralPhysicalInput implements GraphInput {
        private final int[][] path;private final int[] transitions;private final int seedMask,originX,originY,width,height,pitch;
        private final String kind,pathHash,goalHash;private final int goalCount,expectedGoalMask;
        LiteralPhysicalInput(int[][] source,int seedMask,PcbPadPlacement start,int originX,int originY,int width,int height,int pitch){
            this(source,seedMask,start,originX,originY,width,height,pitch,
                "D_SENSE_PUBLISHER_PATH",null,null,-1,-1);
        }
        LiteralPhysicalInput(int[][] source,int seedMask,PcbPadPlacement start,int originX,int originY,
                int width,int height,int pitch,String kind,String pathHash,String goalHash,
                int goalCount,int expectedGoalMask){
            path=source;this.seedMask=seedMask;this.originX=originX;this.originY=originY;
            this.width=width;this.height=height;this.pitch=pitch;this.kind=kind;
            this.pathHash=pathHash;this.goalHash=goalHash;this.goalCount=goalCount;
            this.expectedGoalMask=expectedGoalMask;
            if(path==null||path.length<2||path.length>MAX_PATH_NODES||path[0]==null||path[0].length<3||
                    path[0][0]!=start.getX()||path[0][1]!=start.getY())
                throw new IllegalArgumentException("Invalid literal graph path");
            transitions=new int[path.length];
            for(int i=1;i<path.length;i++){
                if(path[i]==null||path[i].length<3)throw new IllegalArgumentException("Invalid literal graph path row");
                transitions[i]=transitions[i-1]+(path[i][2]==path[i-1][2]?0:1);
            }
        }
        public String sourceKind(){return kind;}public int seedLayerMask(){return seedMask;}public int pathNodeCount(){return path.length;}
        private int[] row(int i){if(i<0||i>=path.length)throw new IndexOutOfBoundsException();return path[i];}
        public int pathCellAt(int i){int[] r=row(i);return cell(r[0],r[1],originX,originY,width,height,pitch);}public int pathXAt(int i){return row(i)[0];}public int pathYAt(int i){return row(i)[1];}public int pathLayerAt(int i){return row(i)[2];}
        public int pathDirectionAt(int i){if(i==0)return 4;int[] a=row(i-1),b=row(i);return a[2]==b[2]?direction(a[0],a[1],b[0],b[1]):4;}
        public int pathTransitionsAt(int i){row(i);return transitions[i];}public int pathCostAt(int i){return i*2;}public int pathHeuristicAt(int i){return 0;}public int pathSerialAt(int i){return i;}
        public int pathKeyAt(int i){int t=pathTransitionsAt(i),layer=pathLayerAt(i),d=pathDirectionAt(i),c=pathCellAt(i);return (int)(((((long)t*2+layer)*5+d)*(long)width*height)+c);}
        public String pathTupleSha256(){return pathHash;}public String goalFaceBitsSha256(){return goalHash;}
        public int goalCellCount(){return goalCount;}public int expectedGoalFaceMaskAt(int cell){return expectedGoalMask;}
    }

    static void runContracts() {
        numericFragmentContracts();
        outfTargetAndStreamContracts();
        incompleteCursorReleaseContracts();
        Fixture fixture = new Fixture("P07_ZERO_VIA_GRAPH_MAIN");
        PcbNetRouter.Router[] faces = fixture.faces();
        PcbPadPlacement start = fixture.layout.getPad("START.1");
        PcbPadPlacement root = fixture.layout.getPad("ROOT.1");
        PcbPadPlacement reached = fixture.layout.getPad("REACHED.1");
        PcbPadPlacement[] reachedPads = new PcbPadPlacement[] {reached};

        // A top-face foreign-copper owner distinguishes the two actual Router
        // graphs while the bottom-face source positive control remains clear.
        int foreignX = 650, foreignY = 550;
        occupy(faces[TOP], foreignX, foreignY, "BLOCK");
        require(!faces[TOP].permitsLayerStep(foreignX - 10, foreignY, foreignX, foreignY,
                    "N", start, root, reachedPads) &&
                faces[BOTTOM].permitsLayerStep(foreignX - 10, foreignY, foreignX, foreignY,
                    "N", start, root, reachedPads),
            "foreign copper blocks only its owning actual face graph");

        PcbPadPlacement blocker = fixture.layout.getPad("BLOCKER.1");
        require(blocker.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                blocker.getEscapeLength() >= 20 &&
                (blocker.getEscapeDx() != 0 || blocker.getEscapeDy() != 0),
            "reservation canary uses the declared valid through-hole connector geometry");
        int towardX = blocker.getX() + blocker.getEscapeDx() * 20;
        int towardY = blocker.getY() + blocker.getEscapeDy() * 20;
        int nearX = blocker.getX() + blocker.getEscapeDx() * 10;
        int nearY = blocker.getY() + blocker.getEscapeDy() * 10;
        require(!faces[TOP].permitsLayerStep(towardX, towardY, nearX, nearY,
                    "N", start, root, reachedPads),
            "foreign plated-terminal escape reservation rejects an ordinary approach edge");

        int[][] path = withFourCoincidentFlips(fixture.sourcePath());
        require(path.length == 70,
            "literal positive-control fixture has 65 planar edges and four coincident face flips");
        boolean[] goals = fixture.actualGoals(faces, root, reachedPads);
        requireActualSourcePath(faces, path, start, root, reachedPads);
        int[] topOracle = directMasks(faces[TOP], fixture, start, root, reachedPads);
        int[] bottomOracle = directMasks(faces[BOTTOM], fixture, start, root, reachedPads);
        int literalCell = cell(350, 300, fixture);
        int literalBottomMask = literalFourWayMask(faces[BOTTOM], fixture, start, root, reachedPads,
            350, 300);
        int expectedQueries = (int) (2L * (4L * fixture.cells - 2L * fixture.width - 2L * fixture.height));
        require(expectedQueries == 2 * (4 * fixture.cells - 2 * fixture.width - 2 * fixture.height),
            "independent rectangular-grid formula counts every in-bounds directed neighbor");

        int expectedGoalPopulation = countGoalCells(goals, fixture.cells);
        String geometryBefore = fixture.layout.geometryFingerprint();
        String topEffectsBefore = faces[TOP].fixedGraphObservationEffects();
        String bottomEffectsBefore = faces[BOTTOM].fixedGraphObservationEffects();
        int[][] callerPath = copyPath(path);
        PcbPadPlacement[] callerReached = new PcbPadPlacement[] {reached};
        Context context = new Context(fixture.layout, faces, "N", start, root, callerReached,
            goals, fixture.width, fixture.height, fixture.originX, fixture.originY, fixture.pitch,
            3, callerPath);
        require(context.input.pathXAt(0) == callerPath[0][0],
            "literal fixture view retains its live source path; later mutation must fail recheck");
        require(context.reached == callerReached && context.sourceGoalMask == goals,
            "context binds the exact live reached-pad and goal-array authorities");

        Cursor one = begin(context);
        drain(one, 1);
        Result resultOne = one.result();
        Cursor seven = begin(context);
        drain(seven, 7);
        Result resultSeven = seven.result();
        Cursor maximum = begin(context);
        drain(maximum, MAX_ADVANCE_WORK);
        Result resultMaximum = maximum.result();

        require(resultOne.ordinaryQueries == expectedQueries &&
                resultSeven.ordinaryQueries == expectedQueries &&
                resultMaximum.ordinaryQueries == expectedQueries,
            "both faces query exactly every in-bounds directed ordinary edge");
        require(expectedGoalPopulation > 3 && expectedGoalPopulation < fixture.cells &&
                resultOne.goalCells == fixture.cells && resultSeven.goalCells == fixture.cells &&
                resultMaximum.goalCells == fixture.cells &&
                resultOne.encodedCells == 3 * fixture.cells &&
                resultSeven.encodedCells == 3 * fixture.cells &&
                resultMaximum.encodedCells == 3 * fixture.cells,
            "goal-cell positions and three encoded streams match frozen N and 3N wire counts");
        require(sameSemanticGraph(resultOne, resultSeven) && sameSemanticGraph(resultOne, resultMaximum),
            "chunk sizes 1, 7 and 32768 preserve the exact two-face graph and positive controls");
        require(resultOne.originalPathVerified && resultOne.goalVerified &&
                resultOne.pathTransitionCount == 4 && resultOne.pathPlanarEdgeCount == 65 &&
                resultOne.liveBeforeVerified && resultOne.liveAfterVerified &&
                resultOne.liveEffectsUnchanged &&
                resultOne.copyEntries > 0 && resultOne.comparisonEntries > 0 &&
                resultOne.workSteps >= resultOne.copyEntries + resultOne.comparisonEntries +
                    resultOne.geometryBytes + resultOne.effectUnits + resultOne.ordinaryQueries +
                    resultOne.goalCells + resultOne.reachedPadChecks + resultOne.pathNodes +
                    resultOne.pathEdgesVerified + resultOne.tupleBytes +
                    resultOne.serializedUtf16Units,
            "source-path, live-state and conservative nested-work evidence is complete");
        String expectedGeometryDigest = sha256(geometryBefore);
        require(resultOne.prefixGeometrySha256.equals(expectedGeometryDigest),
            "full canonical geometry digest matches production: actual=" + resultOne.prefixGeometrySha256 +
                " expected=" + expectedGeometryDigest);
        long expectedGeometryBytes = 2L * geometryBefore.getBytes(StandardCharsets.UTF_8).length;
        require(resultOne.geometryBytes == expectedGeometryBytes,
            "canonical UTF-8 byte count covers both traversals: actual=" + resultOne.geometryBytes +
                " expected=" + expectedGeometryBytes);
        require(one.effectHashes[TOP].equals(sha256(topEffectsBefore)) &&
                one.effectHashes[BOTTOM].equals(sha256(bottomEffectsBefore)) &&
                one.effectLengths[TOP] == topEffectsBefore.length() &&
                one.effectLengths[BOTTOM] == bottomEffectsBefore.length(),
            "both effect streams match the independent complete production fingerprints and UTF-16 lengths");
        require(isLowerHex(resultOne, 0, fixture.cells) &&
                isLowerHex(resultOne, 1, fixture.cells) &&
                isLowerHex(resultOne, 2, fixture.cells) &&
                resultOne.serializedUtf16Units == resultOne.bodyLength() &&
                resultOne.encodedCells == 3 * fixture.cells,
            "all three canonical masks contain one lowercase hex digit per cell");
        require((nibble(resultOne.maskCharAt(0, cell(path[0][0], path[0][1], fixture))) & 4) != 0 &&
                (nibble(resultOne.maskCharAt(2, cell(root.getX(), root.getY(), fixture))) & 1) != 0 &&
                (nibble(resultOne.maskCharAt(2, cell(reached.getX(), reached.getY(), fixture))) & 3) == 3,
            "literal S-bit and two-face multi-goal nibbles use TOP=1 and BOTTOM=2");
        require(compareMasksWithIndependentPredicates(resultOne, topOracle, bottomOracle, goals, fixture),
            "each source cell/direction bit matches the independent literal actual-Router query oracle");
        require(nibble(resultOne.maskCharAt(1, literalCell)) == literalBottomMask,
            "literal N/E/S/W calls encode bits 1/2/4/8 in the frozen order");
        require(!edge(resultOne, 0, cell(foreignX - 10, foreignY, fixture), 1) &&
                edge(resultOne, 1, cell(foreignX - 10, foreignY, fixture), 1),
            "foreign copper edge is represented on the correct face and direction bit");
        int interiorGoal = fixture.interiorTraceGoal(faces[TOP], root, reached);
        require(interiorGoal >= 0 && (nibble(resultOne.maskCharAt(2, interiorGoal)) & 1) != 0 &&
                expectedGoalPopulation > 3,
            "actual same-net copper interior and multiple physical targets remain in the goal union");
        String body = renderFixtureBody(one.result());
        require(body.equals(renderFixtureBody(one.result())) && body.length()==resultOne.bodyLength(),
            "the owned immutable masks serialize repeatedly with the exact independently measured frame length");
        require(one.context==null && one.pristine[TOP]==null && one.pristine[BOTTOM]==null &&
                one.query[TOP]==null && one.query[BOTTOM]==null && one.reachedSnapshot==null &&
                one.path==null && one.effectBaseline[TOP]==null && one.effectBaseline[BOTTOM]==null &&
                one.geometryWalker==null && one.geometryBaseline==null && one.effectWalker==null &&
                one.copyTask==null && one.comparisonTask==null && one.copyShellRouter==null &&
                one.copyRowSource==null && one.prefixBuilder==null && one.pendingTuple==null &&
                one.bodyPrefix==null && one.bodySuffix==null && one.emitLiteral==null &&
                one.topMasks==null && one.bottomMasks==null && one.goalFaces==null &&
                one.sourcesReleased() && one.advance(1) && one.lastWork()==0 && one.result()==resultOne,
            "completed cursors release live sources and transferred masks before repeat completion");
        final java.io.ByteArrayOutputStream subclassBytes=new java.io.ByteArrayOutputStream();
        java.io.PrintStream subclass=new java.io.PrintStream(subclassBytes) {
            public void print(char[] value) { throw new AssertionError("Private masks reached an overridable sink"); }
        };
        boolean subclassRejected=false;
        try {resultOne.writeBody(subclass);} catch(IllegalArgumentException expected) {subclassRejected=true;}
        finally {subclass.close();}
        require(subclassRejected && subclassBytes.size()==0 && body.equals(renderFixtureBody(resultOne)),
            "unsupported output subclasses are rejected before any body or mask exposure");
        require(body.startsWith("{\"schema\":\"p07-zero-via-graph-r5\",\"stability\":{") &&
                body.contains("\"ordinaryQueries\":") && resultOne.ordinaryQueries == expectedQueries &&
                body.contains("\"topStepsHex\":\"") && body.contains("\"bottomStepsHex\":\"") &&
                body.contains("\"goalFacesHex\":\""),
            "completed immutable body exposes the frozen stability/grid/work/masks payload");
        require(geometryBefore.equals(fixture.layout.geometryFingerprint()) &&
                topEffectsBefore.equals(faces[TOP].fixedGraphObservationEffects()) &&
                bottomEffectsBefore.equals(faces[BOTTOM].fixedGraphObservationEffects()),
            "both live face routers and the layout retain their pre-capture state and effects");

        directionalReachedCanary(fixture, faces, start, root, reachedPads);
        alreadyCopiedMutationCanary();
        sourceMutationCanaries();
        invalidFaceFlipCanaries();
        geometryAndEffectMutationCanaries();
        collectionKeyMutationCanary("regions");
        collectionKeyMutationCanary("holes");
        contextFaceBindingCanaries();
        System.out.println("PASS: P07 zero-via graph contracts assertions=" + Contract.checks);
    }

    private static void outfTargetAndStreamContracts() {
        Fixture fixture=new Fixture("P07_ZERO_VIA_GRAPH_OUT_F_TARGET");
        PcbNetRouter.Router[] faces=fixture.faces();
        PcbPadPlacement start=fixture.layout.getPad("START.1");
        PcbPadPlacement root=fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached={fixture.layout.getPad("REACHED.1")};
        boolean[] goals=fixture.actualGoals(faces,root,reached);
        int[][] path=fixture.sourcePath();
        LiteralPhysicalInput input=new LiteralPhysicalInput(path,3,start,fixture.originX,fixture.originY,
            fixture.width,fixture.height,fixture.pitch,"OUT_F_REJECTED_PUBLISHER_PATH",null,null,-1,-1);
        Context context=new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
            fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,input);
        Cursor cursor=begin(context);
        drain(cursor,MAX_ADVANCE_WORK);
        Result result=cursor.result();
        require(result.target==Target.OUT_F &&
                "OUT_F_REJECTED_PUBLISHER_PATH".equals(result.sourceKind) &&
                result.initialLayerMask==3 && result.originalPathVerified && result.goalVerified,
            "OUT_F accepts the explicit rejected-publisher source kind and both actual seed faces");
        String printStreamBody=renderFixtureBody(result);
        StringBuilder incremental=new StringBuilder(result.bodyLength());
        for(int index=0;index<result.bodyLength();index++)incremental.append(result.bodyCharAt(index));
        String counterKey="\"serializedUtf16Units\":";
        int counterOffset=printStreamBody.indexOf(counterKey)+counterKey.length();
        StringBuilder paddedCounter=new StringBuilder(19);
        String counterDigits=Long.toString(result.serializedUtf16Units);
        for(int i=counterDigits.length();i<19;i++)paddedCounter.append(' ');
        paddedCounter.append(counterDigits);
        require(printStreamBody.contentEquals(incremental) && counterOffset>=counterKey.length() &&
                printStreamBody.substring(counterOffset,counterOffset+19).contentEquals(paddedCounter),
            "incremental immutable body access preserves exact PrintStream bytes and 19-column spaces");
        long[] phases=result.phaseWorkCopy();
        long phaseTotal=0;
        for(long phase:phases){if(phase<0)throw new AssertionError("Negative phase work");phaseTotal+=phase;}
        require(phases.length==32 && phaseTotal==result.workSteps &&
                phases.length==result.phaseWorkCopy().length,
            "safe phase-work copy exposes all 32 reconciled nonnegative owner counters");
        phases[0]=-1;
        require(result.phaseWorkCopy()[0]>=0,
            "mutating an exported phase-work copy cannot alter the immutable Result");

        boolean rejected=false;
        try {
            GraphInput wrongKind=new LiteralPhysicalInput(path,3,start,fixture.originX,fixture.originY,
                fixture.width,fixture.height,fixture.pitch,"D_SENSE_PUBLISHER_PATH",null,null,-1,-1);
            new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
                fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,wrongKind);
        } catch(IllegalArgumentException expected) {rejected=true;}
        require(rejected,"OUT_F rejects a source kind borrowed from the D_SENSE target");
        rejected=false;
        try {
            GraphInput preResult=new LiteralPhysicalInput(path,3,start,fixture.originX,fixture.originY,
                fixture.width,fixture.height,fixture.pitch,"OUT_F_REJECTED_PUBLISHER_PATH",
                sha256("path metadata"),sha256("goal metadata"),1,-1);
            new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
                fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,preResult);
        } catch(IllegalArgumentException expected) {rejected=true;}
        require(rejected,"OUT_F rejects fabricated external PreResult hashes and counts");
        rejected=false;
        try {
            GraphInput goalOracle=new LiteralPhysicalInput(path,3,start,fixture.originX,fixture.originY,
                fixture.width,fixture.height,fixture.pitch,"OUT_F_REJECTED_PUBLISHER_PATH",null,null,-1,0);
            new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
                fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,goalOracle);
        } catch(IllegalArgumentException expected) {rejected=true;}
        require(rejected,"OUT_F rejects an external goal-mask oracle even with null hashes");

        boolean bounds=false;
        try {result.bodyCharAt(result.bodyLength());} catch(IndexOutOfBoundsException expected) {bounds=true;}
        require(bounds,"incremental body access rejects an index at the stream boundary");
        result.releaseBodyMasks();
        require(result.bodyReleased(),"the bounded output owner can release transferred masks after streaming");
        boolean released=false;
        try {result.bodyCharAt(0);} catch(IllegalStateException expected) {released=true;}
        require(released,"released Result masks cannot be read through the incremental adapter");
    }

    private static void incompleteCursorReleaseContracts() {
        Fixture fixture=new Fixture("P07_ZERO_VIA_GRAPH_OUT_F_RELEASE");
        PcbNetRouter.Router[] faces=fixture.faces();
        PcbPadPlacement start=fixture.layout.getPad("START.1"),root=fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached={fixture.layout.getPad("REACHED.1")};
        boolean[] goals=fixture.actualGoals(faces,root,reached);
        int[][] path=fixture.sourcePath();
        LiteralPhysicalInput input=new LiteralPhysicalInput(path,3,start,fixture.originX,fixture.originY,
            fixture.width,fixture.height,fixture.pitch,"OUT_F_REJECTED_PUBLISHER_PATH",null,null,-1,-1);

        Cursor cancelled=begin(new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
            fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,input));
        advanceUntilLiveCopyTask(cancelled);
        require(cancelled.context!=null && cancelled.copyTask!=null && cancelled.copyShellRouter!=null &&
                !((Boolean)readField(cancelled.copyTask,"complete")).booleanValue(),
            "cancellation begins with a live retained Context, Router and copy task");
        long cancelledWork=cancelled.workSteps;
        cancelled.cancel();
        requireIncompleteCursorReleased(cancelled,cancelledWork,
            "explicit cancellation releases active Router-copy references");

        Cursor capAbort=begin(new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
            fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,input));
        advanceUntilLiveEffectWalker(capAbort);
        require(capAbort.context!=null && capAbort.pristine[TOP]!=null &&
                capAbort.geometryBaseline!=null && capAbort.effectWalker!=null &&
                capAbort.stage==Cursor.EFFECT_BEFORE,
            "capture-cap abort begins with live Context, Router snapshot, geometry and effect walkers");
        long capAbortWork=capAbort.workSteps;
        int remainingCaptureUnits=0;
        if(remainingCaptureUnits<1) capAbort.cancel();
        requireIncompleteCursorReleased(capAbort,capAbortWork,
            "a frozen capture-cap abort releases active geometry/effect-walker references");

        Cursor error=begin(new Context(Target.OUT_F,fixture.layout,faces,"N",start,root,reached,goals,
            fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,input));
        advanceUntilLiveEffectWalker(error);
        require(error.context!=null && error.pristine[TOP]!=null && error.geometryBaseline!=null &&
                error.effectWalker!=null,
            "source-binding error begins with live Context, Router snapshot and walkers");
        long errorWork=error.workSteps;
        PcbNetRouter.Router originalTop=faces[TOP];
        String originalTopEffects=originalTop.fixedGraphObservationEffects();
        String successorEffects=faces[BOTTOM].fixedGraphObservationEffects();
        boolean rejected=false;
        try {faces[TOP]=faces[BOTTOM];error.advance(1);}
        catch(IllegalStateException expected) {rejected=true;}
        finally {faces[TOP]=originalTop;}
        require(rejected && error.isAborted(),
            "a late source-binding error aborts the active capture before a successor graph is read");
        requireIncompleteCursorReleased(error,errorWork,
            "a late source-binding error releases active geometry/effect-walker references");
        require(originalTopEffects.equals(originalTop.fixedGraphObservationEffects()) &&
                successorEffects.equals(faces[BOTTOM].fixedGraphObservationEffects()),
            "the aborted stale cursor leaves both restored live Routers unchanged");
    }

    private static void advanceUntilLiveCopyTask(Cursor cursor) {
        long start=cursor.workSteps;
        while(cursor.copyTask==null) {
            if(cursor.isComplete() || cursor.isAborted() || cursor.workSteps-start>=250000L)
                throw new AssertionError("Cursor did not reach a live Router copy task within its fixture bound");
            cursor.advance(1);
        }
    }

    private static void advanceUntilLiveEffectWalker(Cursor cursor) {
        long start=cursor.workSteps;
        while(cursor.effectWalker==null) {
            if(cursor.isComplete() || cursor.isAborted() || cursor.workSteps-start>=250000L)
                throw new AssertionError("Cursor did not reach a live effect walker within its fixture bound");
            cursor.advance(1);
        }
    }

    private static void requireIncompleteCursorReleased(Cursor cursor,long frozenWork,String message) {
        require(!cursor.isComplete() && cursor.isAborted() && cursor.sourcesReleased() &&
                cursor.context==null && cursor.copyTask==null && cursor.comparisonTask==null &&
                cursor.copyShellRouter==null && cursor.copyRowSource==null && cursor.geometryWalker==null &&
                cursor.geometryBaseline==null && cursor.effectWalker==null && cursor.path==null &&
                cursor.reachedSnapshot==null,
            message);
        cursor.cancel();
        require(cursor.isAborted() && cursor.sourcesReleased() && cursor.workSteps==frozenWork,
            "repeated cancellation preserves released cursor state and charged work");
        require(!cursor.advance(1) && cursor.lastWork()==0 && cursor.workSteps==frozenWork,
            "released cursor cannot resume stale work or change successor state");
        boolean hidden=false;
        try {cursor.result();} catch(IllegalStateException expected) {hidden=true;}
        require(hidden,"an aborted cursor cannot expose a successful Result");
    }
    private static String renderFixtureBody(Result result) {
        if(result.cells>10000)throw new IllegalArgumentException("Full String rendering is limited to the small fixture");
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        java.io.PrintStream output=new java.io.PrintStream(bytes);
        try {
            result.writeBody(output);
            if(output.checkError())throw new AssertionError("Fixture body stream failed");
        } finally {output.close();}
        return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
    }

    private static void numericFragmentContracts() {
        TextToken token=text(Integer.MIN_VALUE,"|",Integer.MAX_VALUE,"|",Long.MIN_VALUE,"|",Long.MAX_VALUE,
            "|",0,"|",-1,"|",10,"|",-10,"|",1000,"|",-1000,"|",text("tail:",7),
            text("", "|", true, "|", null, "|", PcbCopperLayer.TOP, "|", "\u03a9\ud83d\ude00"));
        String expected="-2147483648|2147483647|-9223372036854775808|9223372036854775807|0|-1|10|-10|1000|-1000|tail:7|true|null|TOP|\u03a9\ud83d\ude00";
        StringBuilder actual=new StringBuilder();
        Utf8State hash=new Utf8State();
        int work=0;
        while(true){
            int value=token.nextUnit();
            if(value==STREAM_END)break;
            if(++work>10000)throw new AssertionError("Numeric fragment fixture exceeded its bounded work");
            if(value>=0){actual.append((char)value);hash.accept((char)value);}
        }
        require(expected.equals(actual.toString())&&token.length()==expected.length(),
            "incremental decimal fragments match literal signed extrema, zero and power-of-ten expectations");
        require(hex(hash.finish()).equals(sha256(expected)),
            "incremental numeric fragment bytes match an independent complete UTF-8 hash");
    }

    private static void collectionKeyMutationCanary(String collection) {
        Fixture fixture=new Fixture("P07_ZERO_VIA_GRAPH_KEYS_"+collection);
        Map<String,Object> rows=(Map<String,Object>)readField(fixture.layout,collection);
        require(rows.isEmpty(),"key canary begins with the controlled empty "+collection+" map");
        if("regions".equals(collection)) {
            Vector<String> members=new Vector<String>();
            members.add("START");
            fixture.layout.addRegion(new PcbLayoutRegion("KEY_ORIGINAL","Fixture region",members));
        } else fixture.layout.addHole(new PcbBoardHole("KEY_ORIGINAL",PcbBoardHole.Kind.NON_PLATED,
            null,640,570,4,4,null,null,PcbCopperAccess.Exposure.EXPOSED));
        PcbNetRouter.Router[] faces=fixture.faces();
        PcbPadPlacement start=fixture.layout.getPad("START.1"),root=fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached={fixture.layout.getPad("REACHED.1")};
        boolean[] goals=fixture.actualGoals(faces,root,reached);
        Cursor cursor=begin(new Context(fixture.layout,faces,"N",start,root,reached,goals,
            fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,fixture.sourcePath()));
        advanceUntilStage(cursor,Cursor.SCAN);
        String before=fixture.layout.geometryFingerprint();
        Object value=rows.remove("KEY_ORIGINAL");
        rows.put("KEY_REPLACED",value);
        require(before.equals(fixture.layout.geometryFingerprint()),
            "key-only corruption keeps canonical values unchanged and isolates the identity guard");
        requireRejected(cursor,"same-value "+collection+" map key replacement is rejected");
    }

    private static void contextFaceBindingCanaries() {
        Fixture fixture=new Fixture("P07_ZERO_VIA_GRAPH_CONTEXT_FACES");
        PcbNetRouter.Router[] faces=fixture.faces();
        PcbPadPlacement start=fixture.layout.getPad("START.1"),root=fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached={fixture.layout.getPad("REACHED.1")};
        boolean[] goals=fixture.actualGoals(faces,root,reached);
        boolean rejected=false;
        try { new Context(fixture.layout,new PcbNetRouter.Router[]{faces[BOTTOM],faces[TOP]},"N",start,root,reached,
            goals,fixture.width,fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,fixture.sourcePath()); }
        catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("layout/face");}
        require(rejected,"Context rejects swapped physical face Routers before capture");
        Fixture other=new Fixture("P07_ZERO_VIA_GRAPH_OTHER_LAYOUT");
        PcbNetRouter.Router[] foreign={new PcbNetRouter.Router(other.layout,fixture.board,fixture.outline,0,
                P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP),
            new PcbNetRouter.Router(other.layout,fixture.board,fixture.outline,0,
                P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.BOTTOM)};
        rejected=false;
        try { new Context(fixture.layout,foreign,"N",start,root,reached,goals,fixture.width,fixture.height,
            fixture.originX,fixture.originY,fixture.pitch,3,fixture.sourcePath()); }
        catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("layout/face");}
        require(rejected,"Context rejects same-board Routers belonging to another layout");
        Cursor retained=begin(new Context(fixture.layout,faces,"N",start,root,reached,goals,fixture.width,
            fixture.height,fixture.originX,fixture.originY,fixture.pitch,3,fixture.sourcePath()));
        PcbNetRouter.Router originalTop=faces[TOP];
        try {
            faces[TOP]=faces[BOTTOM];
            requireRejected(retained,"replacing a caller face slot after binding is rejected");
        } finally { faces[TOP]=originalTop; }
    }

    private static void directionalReachedCanary(Fixture fixture, PcbNetRouter.Router[] faces,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached) {
        PcbPadPlacement target = reached[0];
        int towardX = target.getX() + target.getEscapeDx() * 20;
        int towardY = target.getY() + target.getEscapeDy() * 20;
        int nearX = target.getX() + target.getEscapeDx() * 10;
        int nearY = target.getY() + target.getEscapeDy() * 10;
        require(target.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                target.getEscapeLength() >= 20 && target.getEscapeDx() != 0 &&
                faces[TOP].permitsLayerStep(towardX, towardY, nearX, nearY,
                    "N", start, root, reached) &&
                !faces[TOP].permitsLayerStep(nearX, nearY, towardX, towardY,
                    "N", start, root, reached),
            "actual PTH endpoint authority allows directed approach and rejects reverse escape traversal");
    }

    private static void alreadyCopiedMutationCanary() {
        Fixture fixture = new Fixture("P07_ZERO_VIA_GRAPH_MUTATION");
        PcbNetRouter.Router[] faces = fixture.faces();
        PcbPadPlacement start = fixture.layout.getPad("START.1");
        PcbPadPlacement root = fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached = new PcbPadPlacement[] {fixture.layout.getPad("REACHED.1")};
        boolean[] goals = fixture.actualGoals(faces, root, reached);
        Context context = new Context(fixture.layout, faces, "N", start, root, reached,
            goals, fixture.width, fixture.height, fixture.originX, fixture.originY,
            fixture.pitch, 3, fixture.sourcePath());
        Cursor cursor = begin(context);
        while (cursor.copyEntries == 0) {
            cursor.advance(1);
            if (cursor.isComplete()) throw new AssertionError("Copy canary unexpectedly completed before any copy entry");
        }
        occupy(faces[TOP], fixture.originX, fixture.originY, "BLOCK");
        String failedLiveState=faces[TOP].fixedGraphObservationEffects();
        boolean rejected = false;
        try { drain(cursor, MAX_ADVANCE_WORK); }
        catch (IllegalStateException expected) { rejected = true; }
        long failedWork=cursor.workSteps;
        require(rejected && !cursor.isComplete() && cursor.isAborted() && cursor.sourcesReleased(),
            "mutation after Router rows were copied rejects and releases the active cursor sources");
        require(!cursor.advance(1) && cursor.lastWork()==0 && cursor.workSteps==failedWork &&
                failedLiveState.equals(faces[TOP].fixedGraphObservationEffects()),
            "failed copied-source validation cannot resume stale work or mutate its live Router");
        boolean hidden = false;
        try { cursor.result(); } catch (IllegalStateException expected) { hidden = true; }
        require(hidden, "a failed live-state comparison cannot publish a partial graph");
    }

    private static int[] directMasks(PcbNetRouter.Router router, Fixture fixture,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached) {
        int[] masks = new int[fixture.cells];
        for (int cell = 0; cell < fixture.cells; cell++) {
            int xIndex = cell % fixture.width, yIndex = cell / fixture.width;
            int x = coordinate(fixture.originX, xIndex, fixture.pitch);
            int y = coordinate(fixture.originY, yIndex, fixture.pitch);
            for (int direction = 0; direction < 4; direction++) {
                int nxIndex = xIndex + DX[direction], nyIndex = yIndex + DY[direction];
                if (nxIndex < 0 || nxIndex >= fixture.width || nyIndex < 0 || nyIndex >= fixture.height)
                    continue;
                int nx = coordinate(fixture.originX, nxIndex, fixture.pitch);
                int ny = coordinate(fixture.originY, nyIndex, fixture.pitch);
                if (router.permitsLayerStep(x, y, nx, ny, "N", start, root, reached))
                    masks[cell] |= 1 << direction;
            }
        }
        return masks;
    }

    private static void requireActualSourcePath(PcbNetRouter.Router[] routers, int[][] path,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached) {
        for (int i = 0; i + 1 < path.length; i++) {
            int[] from = path[i], to = path[i + 1];
            if (from[2] != to[2]) {
                if (from[0] != to[0] || from[1] != to[1])
                    throw new AssertionError("Literal P07 face flip does not preserve its physical cell at index=" + i);
                continue;
            }
            boolean allowed = routers[from[2]].permitsLayerStep(from[0], from[1], to[0], to[1],
                "N", start, root, reached);
            if (!allowed)
                throw new AssertionError("Literal P07 zero-via source edge is forbidden at index=" +
                    i + " from=" + from[0] + "," + from[1] + " to=" + to[0] + "," + to[1] +
                    " layer=" + from[2]);
        }
    }

    private static int[][] withFourCoincidentFlips(int[][] planar) {
        if (planar.length != 66) throw new AssertionError("Literal planar path shape changed");
        int[][] result = new int[70][3];
        for (int i = 0; i < 5; i++) {
            result[i][0] = planar[0][0];
            result[i][1] = planar[0][1];
            result[i][2] = i % 2 == 0 ? PcbCopperLayer.TOP.ordinal() : PcbCopperLayer.BOTTOM.ordinal();
        }
        for (int i = 1; i < planar.length; i++)
            System.arraycopy(planar[i], 0, result[i + 4], 0, 3);
        return result;
    }

    private static void sourceMutationCanaries() {
        Fixture fixture = new Fixture("P07_ZERO_VIA_GRAPH_SOURCE_MUTATION");
        PcbNetRouter.Router[] faces = fixture.faces();
        PcbPadPlacement start = fixture.layout.getPad("START.1");
        PcbPadPlacement root = fixture.layout.getPad("ROOT.1");
        PcbPadPlacement reachedPad = fixture.layout.getPad("REACHED.1");
        PcbPadPlacement[] reached = new PcbPadPlacement[] {reachedPad};
        boolean[] goals = fixture.actualGoals(faces, root, reached);
        int[][] path = fixture.sourcePath();
        Context context = new Context(fixture.layout, faces, "N", start, root, reached,
            goals, fixture.width, fixture.height, fixture.originX, fixture.originY,
            fixture.pitch, 3, path);
        require(context.input instanceof LiteralPhysicalInput &&
                ((LiteralPhysicalInput)context.input).path == path,
            "literal context retains the caller-owned source path array");
        Cursor pathCursor = begin(context);
        advanceUntilStage(pathCursor, Cursor.PATH_RECHECK);
        path[0][2] = PcbCopperLayer.BOTTOM.ordinal();
        requireRejected(pathCursor, "mutation of a captured source path row is rejected");

        PcbPadPlacement[] reachedTwo = new PcbPadPlacement[] {reachedPad};
        boolean[] goalsTwo = fixture.actualGoals(faces, root, reachedTwo);
        Context reachedContext = new Context(fixture.layout, faces, "N", start, root, reachedTwo,
            goalsTwo, fixture.width, fixture.height, fixture.originX, fixture.originY,
            fixture.pitch, 3, fixture.sourcePath());
        Cursor reachedCursor = begin(reachedContext);
        advanceUntilStage(reachedCursor, Cursor.REACHED_RECHECK);
        reachedTwo[0] = null;
        requireRejected(reachedCursor, "mutation of the caller-owned reached-pad list is rejected");

        PcbPadPlacement[] goalReached = new PcbPadPlacement[] {reachedPad};
        boolean[] liveGoals = fixture.actualGoals(faces, root, goalReached);
        Context goalContext = new Context(fixture.layout, faces, "N", start, root, goalReached,
            liveGoals, fixture.width, fixture.height, fixture.originX, fixture.originY,
            fixture.pitch, 3, fixture.sourcePath());
        Cursor goalCursor = begin(goalContext);
        advanceUntilStage(goalCursor, Cursor.EMIT_MASKS);
        while (goalCursor.emitStream < 2) goalCursor.advance(1);
        int cell = goalCursor.emitIndex;
        if (cell >= fixture.cells) throw new AssertionError("Goal-emission mutation canary passed the goal span");
        liveGoals[cell] = !liveGoals[cell];
        requireRejected(goalCursor, "mutation of the live goal source after graph scan is rejected");
    }

    private static void invalidFaceFlipCanaries() {
        Fixture fixture = new Fixture("P07_ZERO_VIA_GRAPH_BAD_FLIPS");
        PcbNetRouter.Router[] faces = fixture.faces();
        PcbPadPlacement start = fixture.layout.getPad("START.1");
        PcbPadPlacement root = fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached = new PcbPadPlacement[] {fixture.layout.getPad("REACHED.1")};
        boolean[] goals = fixture.actualGoals(faces, root, reached);
        int[][] fifth = new int[6][3];
        for (int i = 0; i < fifth.length; i++) {
            fifth[i][0] = start.getX();
            fifth[i][1] = start.getY();
            fifth[i][2] = i % 2 == 0 ? PcbCopperLayer.TOP.ordinal() : PcbCopperLayer.BOTTOM.ordinal();
        }
        rejectPath(fixture, faces, start, root, reached, goals, fifth,
            "a fifth coincident face flip is rejected");
        int[][] differentCell = {{start.getX(), start.getY(), PcbCopperLayer.TOP.ordinal()},
            {start.getX() + fixture.pitch, start.getY(), PcbCopperLayer.BOTTOM.ordinal()}};
        rejectPath(fixture, faces, start, root, reached, goals, differentCell,
            "a face flip at another cell is rejected");
    }

    private static void geometryAndEffectMutationCanaries() {
        Fixture geometryFixture = new Fixture("P07_ZERO_VIA_GRAPH_GEOMETRY_MUTATION");
        PcbNetRouter.Router[] geometryFaces = geometryFixture.faces();
        PcbPadPlacement geometryStart = geometryFixture.layout.getPad("START.1");
        PcbPadPlacement geometryRoot = geometryFixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] geometryReached = new PcbPadPlacement[] {geometryFixture.layout.getPad("REACHED.1")};
        boolean[] geometryGoals = geometryFixture.actualGoals(geometryFaces, geometryRoot, geometryReached);
        Context geometryContext = new Context(geometryFixture.layout, geometryFaces, "N", geometryStart,
            geometryRoot, geometryReached, geometryGoals, geometryFixture.width, geometryFixture.height,
            geometryFixture.originX, geometryFixture.originY, geometryFixture.pitch, 3,
            geometryFixture.sourcePath());
        Cursor geometryCursor = begin(geometryContext);
        advanceUntilStage(geometryCursor, Cursor.SCAN);
        geometryFixture.layout.addHole(new PcbBoardHole("P07_GEOMETRY_MUTATION",
            PcbBoardHole.Kind.NON_PLATED, null, 640, 570, 4, 4, null, null,
            PcbCopperAccess.Exposure.EXPOSED));
        requireRejected(geometryCursor, "a changed canonical geometry source is rejected before publication");

        Fixture effectFixture = new Fixture("P07_ZERO_VIA_GRAPH_EFFECT_MUTATION");
        PcbNetRouter.Router[] effectFaces = effectFixture.faces();
        PcbPadPlacement effectStart = effectFixture.layout.getPad("START.1");
        PcbPadPlacement effectRoot = effectFixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] effectReached = new PcbPadPlacement[] {effectFixture.layout.getPad("REACHED.1")};
        boolean[] effectGoals = effectFixture.actualGoals(effectFaces, effectRoot, effectReached);
        Context effectContext = new Context(effectFixture.layout, effectFaces, "N", effectStart,
            effectRoot, effectReached, effectGoals, effectFixture.width, effectFixture.height,
            effectFixture.originX, effectFixture.originY, effectFixture.pitch, 3,
            effectFixture.sourcePath());
        Cursor effectCursor = begin(effectContext);
        advanceUntilStage(effectCursor, Cursor.SCAN);
        effectFaces[TOP].conflicts.put("P07_EFFECT_MUTATION", Integer.valueOf(1));
        requireRejected(effectCursor, "a changed canonical Router effect source is rejected before publication");
    }

    private static void rejectPath(Fixture fixture, PcbNetRouter.Router[] faces,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
            boolean[] goals, int[][] path, String message) {
        Context context = new Context(fixture.layout, faces, "N", start, root, reached,
            goals, fixture.width, fixture.height, fixture.originX, fixture.originY,
            fixture.pitch, 3, path);
        Cursor cursor = begin(context);
        requireRejected(cursor, message);
    }

    private static void advanceUntilStage(Cursor cursor, int expectedStage) {
        while (cursor.stage != expectedStage) {
            cursor.advance(1);
            if (cursor.isComplete()) throw new AssertionError("Cursor completed before requested source-validation stage");
        }
    }

    private static void requireRejected(Cursor cursor, String message) {
        boolean rejected = false;
        try { drain(cursor,MAX_ADVANCE_WORK); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected && !cursor.isComplete(), message);
        require(cursor.isAborted() && cursor.sourcesReleased(),
            "a failed cursor releases every retained graph source before returning");
        boolean hidden = false;
        try { cursor.result(); } catch (IllegalStateException expected) { hidden = true; }
        require(hidden, "a rejected source mutation or path cannot publish a partial graph");
    }

    private static int literalFourWayMask(PcbNetRouter.Router router, Fixture fixture,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
            int x, int y) {
        int mask = 0;
        if (router.permitsLayerStep(x, y, x, y - fixture.pitch, "N", start, root, reached)) mask |= 1;
        if (router.permitsLayerStep(x, y, x + fixture.pitch, y, "N", start, root, reached)) mask |= 2;
        if (router.permitsLayerStep(x, y, x, y + fixture.pitch, "N", start, root, reached)) mask |= 4;
        if (router.permitsLayerStep(x, y, x - fixture.pitch, y, "N", start, root, reached)) mask |= 8;
        return mask;
    }

    private static boolean compareMasksWithIndependentPredicates(Result result, int[] top,
            int[] bottom, boolean[] goals, Fixture fixture) {
        if (top.length != fixture.cells || bottom.length != fixture.cells ||
                goals.length != fixture.cells * 2) return false;
        for (int cell = 0; cell < fixture.cells; cell++) {
            if (nibble(result.maskCharAt(0, cell)) != top[cell] ||
                    nibble(result.maskCharAt(1, cell)) != bottom[cell]) return false;
            int expectedGoals = (goals[cell] ? 1 : 0) | (goals[fixture.cells + cell] ? 2 : 0);
            if (nibble(result.maskCharAt(2, cell)) != expectedGoals) return false;
            int x = cell % fixture.width, y = cell / fixture.width;
            if (y == 0 && ((top[cell] | bottom[cell]) & 1) != 0) return false;
            if (x == fixture.width - 1 && ((top[cell] | bottom[cell]) & 2) != 0) return false;
            if (y == fixture.height - 1 && ((top[cell] | bottom[cell]) & 4) != 0) return false;
            if (x == 0 && ((top[cell] | bottom[cell]) & 8) != 0) return false;
        }
        return true;
    }

    private static boolean sameSemanticGraph(Result a, Result b) {
        return a.width == b.width && a.height == b.height && a.cells == b.cells &&
            a.originX == b.originX && a.originY == b.originY && a.pitch == b.pitch &&
            a.startCell == b.startCell && a.endCell == b.endCell && a.endLayer == b.endLayer &&
            a.initialLayerMask == b.initialLayerMask && a.ordinaryQueries == b.ordinaryQueries &&
            a.goalCells == b.goalCells && a.encodedCells == b.encodedCells &&
            a.copyEntries == b.copyEntries && a.comparisonEntries == b.comparisonEntries &&
            a.liveBeforeVerified == b.liveBeforeVerified && a.liveAfterVerified == b.liveAfterVerified &&
            a.liveEffectsUnchanged == b.liveEffectsUnchanged && a.goalVerified == b.goalVerified &&
            a.originalPathVerified == b.originalPathVerified &&
            a.goalUnionCellCount == b.goalUnionCellCount &&
            a.pathTupleSha256.equals(b.pathTupleSha256) &&
            a.goalFaceBitsSha256.equals(b.goalFaceBitsSha256) &&
            a.prefixGeometrySha256.equals(b.prefixGeometrySha256) &&
            sameMasks(a,b);
    }

    private static boolean sameMasks(Result a, Result b) {
        for(int stream=0;stream<3;stream++) for(int cell=0;cell<a.cells;cell++)
            if(a.maskCharAt(stream,cell)!=b.maskCharAt(stream,cell)) return false;
        return true;
    }

    private static int countGoalCells(boolean[] goals, int cells) {
        int count = 0;
        for (int i = 0; i < cells; i++) if (goals[i] || goals[cells + i]) count++;
        return count;
    }

    private static void drain(Cursor cursor, int chunk) {
        int guard = 0;
        while (!cursor.isComplete()) {
            boolean done = cursor.advance(chunk);
            if (cursor.lastWork() < 1 || cursor.lastWork() > chunk ||
                    done != cursor.isComplete())
                throw new AssertionError("P07 zero-via graph exceeded or misreported its work chunk");
            if (++guard > 2000000)
                throw new AssertionError("P07 zero-via graph fixture exceeded its bounded resume guard");
        }
    }

    private static boolean isLowerHex(Result result, int stream, int expectedLength) {
        if (result == null || result.cells != expectedLength) return false;
        for (int i = 0; i < expectedLength; i++) if (HEX.indexOf(result.maskCharAt(stream,i)) < 0) return false;
        return true;
    }

    private static boolean edge(Result result, int stream, int fromCell, int direction) {
        return (nibble(result.maskCharAt(stream,fromCell)) & (1 << direction)) != 0;
    }

    private static int nibble(char value) {
        int result = HEX.indexOf(value);
        if (result >= 0) return result;
        if (value >= 0 && value <= 15) return value;
        throw new IllegalArgumentException("Invalid P07 zero-via mask nibble");
    }

    private static int direction(int[] from, int[] to) {
        if (to[1] < from[1]) return 0;
        if (to[0] > from[0]) return 1;
        if (to[1] > from[1]) return 2;
        if (to[0] < from[0]) return 3;
        throw new IllegalArgumentException("P07 zero-via source path contains a zero-length edge");
    }

    private static int cell(int x, int y, Fixture fixture) {
        return cell(x, y, fixture.originX, fixture.originY, fixture.width, fixture.height, fixture.pitch);
    }

    private static int cell(int x, int y, int originX, int originY, int width, int height, int pitch) {
        long dx = (long) x - originX, dy = (long) y - originY;
        if (dx < 0 || dy < 0 || dx % pitch != 0 || dy % pitch != 0) return -1;
        long cx = dx / pitch, cy = dy / pitch;
        if (cx >= width || cy >= height) return -1;
        return (int) (cy * width + cx);
    }

    private static int coordinate(int origin, int index, int pitch) {
        long value = (long) origin + (long) index * pitch;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("P07 zero-via cell coordinate overflowed");
        return (int) value;
    }

    private static int[][] copyPath(int[][] path) {
        int[][] copy = new int[path.length][3];
        for (int i = 0; i < path.length; i++) System.arraycopy(path[i], 0, copy[i], 0, 3);
        return copy;
    }

    private static void occupy(PcbNetRouter.Router router, int x, int y, String net) {
        Vector<Point> point = new Vector<Point>();
        point.add(new Point(x, y));
        router.occupyLayerPath(point, net);
    }

    private static long elapsedNanosSince(long started) {
        long elapsed = System.nanoTime() - started;
        return elapsed < 0 ? 0 : elapsed;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int unsigned = item & 255;
                if (unsigned < 16) hex.append('0');
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required for P07 zero-via graph identity", missing);
        }
    }

    private static final class Fixture {
        final Rectangle outline = new Rectangle(100, 100, 600, 500);
        final int width = (outline.width - 20) / 10 + 1;
        final int height = (outline.height - 20) / 10 + 1;
        final int originX = outline.x + 10, originY = outline.y + 10, pitch = 10;
        final int cells = width * height;
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        private PcbNetRouter.Router[] cachedFaces;

        Fixture(String id) {
            initializeCircuitJS();
            board = new TroubleshootBoard(id);
            board.addNet(new BoardNet("N"));
            board.addNet(new BoardNet("BLOCK", BoardNet.RoutingRole.RETURN));
            layout = new PcbBoardLayout(900, 700, outline, new Rectangle(720, 120, 150, 520));
            addTestPoint("START", "N", 210, 210, PcbBoardSide.BOTTOM, PcbRotation.DEG_180);
            addTestPoint("ROOT", "N", 260, 210, PcbBoardSide.BOTTOM, PcbRotation.DEG_0);
            addHeader("REACHED", "N", "N", 450, 450);
            addHeader("BLOCKER", "BLOCK", "BLOCK", 550, 150);
            Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
            for (String component : board.getComponentIds())
                parts.add(new PcbPlacementConstraints.Part(component, "p07",
                    "zero-via graph fixture", "p07", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            board.validate();
            layout.validateAgainst(board);
            PcbNetRouter.Router top = new PcbNetRouter.Router(layout, board, outline, 0,
                P06FactoryLinkFixtures.OBSERVER, PcbCopperLayer.TOP);
            PcbTraceGeometry topTree = top.route("N", "ROOT.1", "REACHED.1");
            layout.addTrace(topTree);
            layout.validateAgainst(board);
            PcbNetRouter.Router bottom = new PcbNetRouter.Router(layout, board, outline, 0,
                P06FactoryLinkFixtures.OBSERVER, PcbCopperLayer.BOTTOM);
            cachedFaces = new PcbNetRouter.Router[] {top, bottom};
        }

        PcbNetRouter.Router[] faces() {
            return cachedFaces;
        }

        int[][] sourcePath() {
            int[][] result = new int[66][3];
            int index = 0;
            for (int column = 0; column < 6; column++) {
                int x = 210 + column * 10;
                int startY = (column & 1) == 0 ? 210 : 310;
                int endY = (column & 1) == 0 ? 310 : 210;
                if (column > 0) {
                    result[index][0] = x;
                    result[index][1] = startY;
                result[index++][2] = PcbCopperLayer.TOP.ordinal();
                }
                int step = endY > startY ? 10 : -10;
                for (int y = column == 0 ? startY : startY + step;
                        step > 0 ? y <= endY : y >= endY; y += step) {
                    result[index][0] = x;
                    result[index][1] = y;
                    result[index++][2] = PcbCopperLayer.TOP.ordinal();
                }
            }
            if (index != result.length || result[index - 1][0] != 260 || result[index - 1][1] != 210)
                throw new AssertionError("Literal P07 zero-via path fixture has an invalid 66-node endpoint");
            return result;
        }

        boolean[] actualGoals(PcbNetRouter.Router[] routers, PcbPadPlacement root,
                PcbPadPlacement[] reached) {
            boolean[] result = new boolean[cells * 2];
            for (int face = 0; face < 2; face++) {
                // Explicit fixture preparation. ownsLayerGridCell may return
                // before prepareNet on a face with no copper for this net.
                routers[face].freeLayerCell(root.getX(), root.getY(), "N");
                PcbCopperLayer layer = face == TOP ? PcbCopperLayer.TOP : PcbCopperLayer.BOTTOM;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                    if (routers[face].ownsLayerGridCell(x, y, "N")) result[face * cells + y * width + x] = true;
                if (PcbCopperAccess.hasCopper(root, layer)) {
                    int rootCell = cell(root.getX(), root.getY(), this);
                    result[face * cells + rootCell] = true;
                }
                for (PcbPadPlacement pad : reached) if (PcbCopperAccess.hasCopper(pad, layer)) {
                    int padCell = cell(pad.getX(), pad.getY(), this);
                    result[face * cells + padCell] = true;
                }
            }
            return result;
        }

        int interiorTraceGoal(PcbNetRouter.Router router, PcbPadPlacement root,
                PcbPadPlacement reached) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int px = coordinate(originX, x, pitch), py = coordinate(originY, y, pitch);
                if (router.ownsLayerGridCell(x, y, "N") &&
                        (px != root.getX() || py != root.getY()) &&
                        (px != reached.getX() || py != reached.getY()))
                    return y * width + x;
            }
            return -1;
        }

        private void addTestPoint(String id, String net, int x, int y, PcbBoardSide side,
                PcbRotation rotation) {
            PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
            PcbPackagePose reference = new PcbPackagePose(100, 100, rotation, side);
            Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
            addPackage(id, physical, new String[] {net}, new PcbPackagePose(
                100 + x - local.x, 100 + y - local.y, rotation, side));
        }

        private void addHeader(String id, String firstNet, String secondNet, int x, int y) {
            PhysicalPackage physical = PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            PcbPackagePose pose = new PcbPackagePose(x - 90, y - 40,
                PcbRotation.DEG_0, PcbBoardSide.TOP);
            addPackage(id, physical, new String[] {firstNet, secondNet}, pose);
        }

        private void addPackage(String id, PhysicalPackage physical, String[] nets,
                PcbPackagePose pose) {
            BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
            board.addComponent(component);
            Vector<String> terminals = physical.getTerminalIds();
            for (int i = 0; i < terminals.size(); i++)
                board.addPad(new BoardPad(id + "." + terminals.get(i), id, terminals.get(i), nets[i]));
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
                physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
    }

    private static void initializeCircuitJS() {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
    }

    private static final class Contract {
        static int checks;
        static void check(boolean value, String message) {
            checks++;
            if (!value) throw new AssertionError(message);
        }
    }

    private static void require(boolean value, String message) {
        Contract.check(value, message);
    }
}
