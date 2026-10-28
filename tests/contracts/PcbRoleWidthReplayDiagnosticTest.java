package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Measurement-only width substitution on a frozen saved P07 geometry fixture. */
public final class PcbRoleWidthReplayDiagnosticTest {
    private static final long DEFAULT_SEED=-1022L;
    private static final int FIXTURE_VERSION=16;
    private static final String FIXTURE_REVISION="5d4de10b56c10fbf1012a2ac39324382074fc6a1";
    private static final String NEG1022_SHA=
        "1216897c60853a35008c3d2fd62965d6489ad997c2e3a62cb578b081dedd5be9";
    private static final String SEED42_SHA=
        "df2494c31141ac8c6ccb5065454ba70e2190d29d9690a195f518c1c268c5fb01";
    private PcbRoleWidthReplayDiagnosticTest() { }

    public static void main(String[] args) {
        if(args.length==0) {
            runSavedPlacement(DEFAULT_SEED);
            runSavedPlacement(42L);
        } else for(String value:args) runSavedPlacement(Long.parseLong(value));
        pointGoalBoundaryCanaries();
        System.out.println("PASS: PCB role width replay diagnostic complete");
    }

    private static void runSavedPlacement(long seed) {
        Rb56Plan savedPlan=Rb56Plan.resolve(seed,11);
        final TroubleshootBoard savedBoard=savedPlan.board();
        final FrozenFixture fixture=FrozenFixture.read(seed);
        final PcbBoardLayout saved=fixture.materialize(savedBoard);
        String expected=seed==-1022L?NEG1022_SHA:seed==42L?SEED42_SHA:null;
        String savedHash=PlayerSessionFingerprint.of(fixture.legacyCanonical);
        require(expected!=null && expected.equals(savedHash),
            "saved11 v"+FIXTURE_VERSION+" source geometry from "+FIXTURE_REVISION+
                " retains its exact frozen candidate hash for seed "+seed+
            " actual="+savedHash+" expected="+expected);
        require(fixture.legacyCanonical.equals(fixture.rebuildLegacyCanonical()),
            "saved11 v"+FIXTURE_VERSION+" decoded placements, pads, labels, holes and routes " +
                "reproduce the full frozen canonical geometry for seed "+seed);
        fixture.requireCanonicalMutationRejected();
        fixture.requireTrailingDataRejected();
        require(fixture.layoutVersion==FIXTURE_VERSION,
            "saved11 geometry fixture uses the recorded layout epoch for seed "+seed);
        int expectedAttempt=seed==-1022L?0:1;
        require(fixture.components.size()==savedBoard.getComponentIds().size() &&
                fixture.pads.size()==savedBoard.getPadIds().size(),
            "saved11 fixture retains the source board's exact component and pad populations for seed "+seed);
        require(fixture.traces.size()==(seed==-1022L?151:144) && fixture.holes.size()==48,
            "saved11 source retains its frozen route and via counts for seed "+seed);
        final String runtimeLayoutHash=PlayerSessionFingerprint.of(saved.geometryFingerprint());
        for(PcbTraceGeometry trace:saved.getTraces())
            require(trace.getWidth()==9,"saved11 source trace remains uniform9: "+trace.getNetId());

        Rb56Plan rolePlan=Rb56Plan.resolve(seed,12);
        final TroubleshootBoard roleBoard=rolePlan.board();
        require(savedPlan.layoutSeed==rolePlan.layoutSeed && savedPlan.routingSeed==rolePlan.routingSeed,
            "placement interpretation leaves seeded layout and routing streams unchanged");
        require(sameElectricalIdentity(savedBoard,roleBoard),
            "saved11 and private12 role board retain exact electrical identities");
        final PcbBoardLayout roleLayout=copyWithRoleWidths(saved,roleBoard);
        int widthChanges=0;
        for(PcbTraceGeometry trace:roleLayout.getTraces()) {
            BoardNet net=roleBoard.getNet(trace.getNetId());
            require(net!=null,"frozen route net remains in the role-width board: "+trace.getNetId());
            int expectedWidth=roleBoard.getPlacementConstraints().getTraceWidthProfile()
                .widthFor(net.getRoutingRole());
            require(trace.getWidth()==expectedWidth,
                "copied route uses its exact electrical-role width: "+trace.getNetId());
            if(expectedWidth!=9) widthChanges++;
        }
        require(widthChanges>0,"role-width copy changes the frozen width9 source on qualified roles");
        require(runtimeLayoutHash.equals(PlayerSessionFingerprint.of(saved.geometryFingerprint())),
            "the frozen saved11 layout remains byte-exact after diagnostic copy");
        require(nonRouteGeometry(saved).equals(nonRouteGeometry(roleLayout)),
            "diagnostic changes trace widths only and retains placements, pads, regions, holes and outline");
        require(tracePathSignature(saved).equals(tracePathSignature(roleLayout)),
            "diagnostic retains every original P07 route path and endpoint identity");

        System.out.println("PCB_ROLE_WIDTH_REPLAY_SOURCE seed="+seed+" hash="+savedHash+
            " fixtureRevision="+FIXTURE_REVISION+" fixtureVersion="+fixture.layoutVersion+
            " materializedVersion="+saved.getLayoutAlgorithmVersion()+
            " materializedHash="+runtimeLayoutHash+" attempt="+expectedAttempt+
            " traces="+saved.getTraces().size()+" vias="+saved.getHoleCount());
        report("validateRoutingGeometry",new Check() {
            public void run() { roleLayout.validateRoutingGeometry(roleBoard); }
        });
        report("validateGeometry",new Check() {
            public void run() { roleLayout.validateGeometry(roleBoard); }
        });
        report("PcbTwoLayerRules",new Check() {
            public void run() { new PcbTwoLayerRules(roleBoard,roleLayout).validate(roleLayout); }
        });
        report("PcbTraceClearance",new Check() {
            public void run() { PcbTraceClearance.validate(roleLayout.getTraces()); }
        });
        report("conductorGraphConnectivity",new Check() {
            public void run() {
                roleLayout.captureConductorGraph(roleBoard).pristine()
                    .requirePristineNetConnectivity(roleBoard);
            }
        });
        System.out.println("PCB_ROLE_WIDTH_REPLAY_PATH seed="+seed+
            " status=NOT_RUN detail=first-step-search-replay-deferred");
        System.out.println("PCB_ROLE_WIDTH_REPLAY_ACCEPTANCE seed="+seed+" status=NOT_PROVEN");
    }

    /** Shows whether the old symmetric inclusive goal envelope blocks a clear odd-width stroke. */
    private static void pointGoalBoundaryCanaries() {
        reportGoalBoundary(PcbRoleWidthRoutingContractTest
            .measureCourtyardGoalBoundary(11,105,245));
        reportGoalBoundary(PcbRoleWidthRoutingContractTest
            .measureCourtyardGoalBoundary(13,104,244));
        reportGoalBoundary(PcbRoleWidthRoutingContractTest
            .measureCourtyardGoalBoundary(9,106,246));
    }

    private static void reportGoalBoundary(
            PcbRoleWidthRoutingContractTest.GoalBoundaryMeasurement measurement) {
        System.out.println("PCB_ROLE_WIDTH_GOAL_BOUNDARY width="+measurement.width+
            " court="+rect(measurement.courtyard)+" clearStroke="+
            rect(measurement.clearStroke)+" clearPhysicalOverlap="+
            measurement.clearPhysicalOverlap+" clearFreeCell="+measurement.clearSearchFreeCell+
            " clearSearchGoal="+measurement.clearSearchGoal+
            " overlapStroke="+rect(measurement.overlapStroke)+" overlapPhysical="+
            measurement.overlapPhysical+" overlapSearchGoal="+
            measurement.overlapSearchGoal);
    }

    /**
     * Layout v16 has retired from production generation. Keep its two source
     * geometries as test data so this diagnostic never asks the current router
     * to reinterpret a historical route. The fixture stores the exact old
     * canonical text plus its decoded geometry records; both must agree.
     */
    private static final class FrozenFixture {
        private static final int MAGIC=0x50525731;
        final long seed;
        final int layoutVersion,width,height;
        final Rectangle outline,tray;
        final String legacyCanonical,scoreVersion;
        final byte[] rawPayload;
        final Vector<RegionRow> regions=new Vector<RegionRow>();
        final Vector<ComponentRow> components=new Vector<ComponentRow>();
        final Vector<PadRow> pads=new Vector<PadRow>();
        final Vector<LabelRow> labels=new Vector<LabelRow>();
        final Vector<HoleRow> holes=new Vector<HoleRow>();
        final Vector<TraceRow> traces=new Vector<TraceRow>();

        private FrozenFixture(DataInputStream in,long expectedSeed,byte[] rawPayload) throws IOException {
            this.rawPayload=rawPayload;
            if(in.readInt()!=MAGIC) throw new IOException("Unknown role-width fixture format");
            seed=in.readLong();
            if(seed!=expectedSeed) throw new IOException("Role-width fixture seed mismatch");
            layoutVersion=in.readInt(); width=in.readInt(); height=in.readInt();
            outline=readRect(in); tray=readRect(in); legacyCanonical=readString(in);
            int prefix=legacyCanonical.indexOf('|');
            int scoreEnd=legacyCanonical.indexOf('|',prefix+1);
            if(prefix<0 || scoreEnd<0 || !legacyCanonical.startsWith("layout="))
                throw new IOException("Missing historical layout fingerprint prefix");
            scoreVersion=legacyCanonical.substring(prefix+1,scoreEnd);

            int count=readCount(in,128,"regions");
            for(int i=0;i<count;i++) {
                String id=readString(in),label=readString(in);
                int componentCount=readCount(in,128,"region components");
                Vector<String> ids=new Vector<String>();
                for(int j=0;j<componentCount;j++) ids.add(readString(in));
                regions.add(new RegionRow(id,label,ids));
            }
            count=readCount(in,128,"components");
            for(int i=0;i<count;i++) components.add(new ComponentRow(readString(in),
                in.readInt(),in.readInt(),in.readInt(),in.readInt(),readString(in),
                readString(in),readString(in),readString(in),readString(in)));
            count=readCount(in,512,"pads");
            for(int i=0;i<count;i++) {
                String id=readString(in); int x=in.readInt(),y=in.readInt();
                int dx=in.readInt(),dy=in.readInt(),length=in.readInt();
                Rectangle land=readRect(in),probe=readRect(in);
                pads.add(new PadRow(id,x,y,dx,dy,length,land,probe,readString(in),
                    readString(in),readString(in),readString(in)));
            }
            count=readCount(in,1024,"labels");
            for(int i=0;i<count;i++) labels.add(new LabelRow(readString(in),readString(in),
                readRect(in),in.readInt(),in.readBoolean(),readNullableString(in)));
            count=readCount(in,1024,"holes");
            for(int i=0;i<count;i++) holes.add(new HoleRow(readString(in),readString(in),
                readNullableString(in),in.readInt(),in.readInt(),in.readInt(),in.readInt(),
                readString(in)));
            count=readCount(in,4096,"traces");
            for(int i=0;i<count;i++) {
                String source=readString(in),net=readString(in),start=readNullableString(in),
                    end=readNullableString(in),layer=readString(in),exposure=readString(in);
                int traceWidth=in.readInt(),points=readCount(in,2048,"trace points");
                int[] xs=new int[points],ys=new int[points];
                for(int j=0;j<points;j++) { xs[j]=in.readInt(); ys[j]=in.readInt(); }
                traces.add(new TraceRow(source,net,start,end,layer,exposure,traceWidth,xs,ys));
            }
            if(in.read()!=-1) throw new IOException("Trailing role-width fixture data");
        }

        static FrozenFixture read(long seed) {
            String entryName=seed==-1022L?"seed-neg1022.gz":seed==42L?"seed-42.gz":null;
            if(entryName==null) throw new IllegalArgumentException("No frozen role-width seed "+seed);
            File archive=new File(System.getProperty("user.dir"),
                "tests/contracts/fixtures/PcbRoleWidthReplayV16Fixtures.zip");
            ZipFile zip=null;
            try {
                zip=new ZipFile(archive);
                if(zip.size()!=2 || zip.getEntry("seed-neg1022.gz")==null ||
                        zip.getEntry("seed-42.gz")==null)
                    throw new IOException("Unexpected role-width fixture archive entries");
                ZipEntry entry=zip.getEntry(entryName);
                if(entry==null) throw new IOException("Missing role-width fixture entry "+entryName);
                GZIPInputStream gzip=new GZIPInputStream(zip.getInputStream(entry));
                ByteArrayOutputStream payload=new ByteArrayOutputStream();
                try {
                    byte[] buffer=new byte[8192];
                    int count;
                    while((count=gzip.read(buffer))!=-1) payload.write(buffer,0,count);
                } finally { gzip.close(); }
                byte[] bytes=payload.toByteArray();
                DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
                try { return new FrozenFixture(input,seed,bytes); }
                finally { input.close(); }
            } catch(IOException failure) {
                throw new AssertionError("Cannot load frozen v16 role-width geometry from "+archive,
                    failure);
            } finally {
                if(zip!=null) try { zip.close(); } catch(IOException ignored) { }
            }
        }

        PcbBoardLayout materialize(TroubleshootBoard board) {
            PcbBoardLayout layout=new PcbBoardLayout(width,height,outline,tray);
            for(ComponentRow row:components) {
                BoardComponent component=board.getComponent(row.id);
                require(component!=null,"v16 fixture component remains present: "+row.id);
                PhysicalPackage physical=component.getPhysicalPackage();
                require(physical!=null,"v16 fixture component has a current package owner: "+row.id);
                PhysicalPackageGeometry geometry=physical.getGeometry();
                PhysicalPackage.GeometryVariant variant=physical.getGeometryVariant(row.variant);
                if(variant!=null) geometry=variant.getGeometry();
                PcbPackagePose pose=new PcbPackagePose(row.x,row.y,
                    PcbRotation.valueOf(row.rotation),PcbBoardSide.valueOf(row.side));
                require(physical.supportsPose(pose),
                    "current package can represent frozen v16 pose for "+row.id);
                PcbComponentPlacement placement=PcbComponentPlacement.fromPhysicalGeometry(
                    row.id,pose,physical,geometry);
                require(row.fingerprint.startsWith("component="+row.id+"@"+row.x+","+row.y+
                    ","+row.width+","+row.height+"|pose="+row.x+","+row.y+
                    "|rotation="+row.rotation+"|mount="+row.side+"|"),
                    "frozen component fingerprint agrees with its decoded id and pose: "+row.id);
                require(row.fingerprint.contains("package="+row.packageId+"|") &&
                    row.fingerprint.contains("|variant="+row.variant+"|"),
                    "frozen component fingerprint retains its recorded package variant: "+row.id);
                layout.addComponent(placement);
            }
            for(RegionRow row:regions) layout.addRegion(new PcbLayoutRegion(row.id,row.label,row.ids));
            for(PadRow row:pads) {
                PcbPadPlacement pad=new PcbPadPlacement(row.id,row.x,row.y,row.escapeDx,
                    row.escapeDy,row.escapeLength,row.land,row.probe,
                    PcbTerminalAttachment.valueOf(row.attachment),PcbBoardSide.valueOf(row.side),
                    PcbCopperAccess.Exposure.valueOf(row.exposure));
                require(row.fingerprint.equals(pad.geometryFingerprint()),
                    "decoded pad fields reproduce the frozen pad geometry: "+row.id);
                layout.addPad(pad);
            }
            for(LabelRow row:labels) layout.addSilkscreenLabel(new PcbSilkscreenLabel(row.id,
                row.text,row.bounds,row.fontSize,row.bold,row.targetPad));
            for(HoleRow row:holes) {
                PcbBoardHole.Kind kind=PcbBoardHole.Kind.valueOf(row.kind);
                boolean plated=kind!=PcbBoardHole.Kind.NON_PLATED;
                layout.addHole(new PcbBoardHole(row.id,kind,row.net,row.x,row.y,row.drillRadius,
                    row.landRadius,plated?PcbCopperLayer.TOP:null,
                    plated?PcbCopperLayer.BOTTOM:null,PcbCopperAccess.Exposure.valueOf(row.exposure)));
            }
            for(TraceRow row:traces) layout.addTrace(new PcbTraceGeometry(row.sourceId,row.netId,
                row.startPadId,row.endPadId,PcbCopperLayer.valueOf(row.layer),
                PcbCopperAccess.Exposure.valueOf(row.exposure),row.xs,row.ys,row.width));
            requireFrozenEndpointsReachPads(board);
            return layout;
        }

        private void requireFrozenEndpointsReachPads(TroubleshootBoard board) {
            java.util.HashMap<String,PadRow> byId=new java.util.HashMap<String,PadRow>();
            for(PadRow pad:pads) {
                byId.put(pad.id,pad);
                BoardPad boardPad=board.getPad(pad.id);
                require(boardPad!=null,"frozen pad retains its source board identity: "+pad.id);
            }
            java.util.HashSet<String> sourceIds=new java.util.HashSet<String>();
            for(TraceRow trace:traces) {
                require(sourceIds.add(trace.sourceId),"frozen trace source ids are unique");
                require(board.getNet(trace.netId)!=null,
                    "frozen route retains its source board net: "+trace.sourceId);
                require(trace.width==9,"frozen v16 route retains width9: "+trace.netId);
                require(trace.xs.length>=2,"frozen route has a connected point sequence: "+trace.sourceId);
                for(int i=1;i<trace.xs.length;i++)
                    require(trace.xs[i]==trace.xs[i-1] || trace.ys[i]==trace.ys[i-1],
                        "frozen route remains Manhattan: "+trace.sourceId);
                requireEndpointNet(trace,trace.startPadId,"start",board);
                requireEndpointNet(trace,trace.endPadId,"end",board);
                requireEndpointConnected(trace,trace.startPadId,0,"start",byId);
                requireEndpointConnected(trace,trace.endPadId,trace.xs.length-1,"end",byId);
            }
        }

        private void requireEndpointNet(TraceRow trace,String padId,String role,
                TroubleshootBoard board) {
            if(padId==null) return;
            BoardPad pad=board.getPad(padId);
            require(pad!=null && trace.netId.equals(pad.getNetId()),
                "frozen route "+role+" pad preserves its electrical net identity: "+trace.sourceId);
        }

        void requireCanonicalMutationRejected() {
            require(!traces.isEmpty() && traces.firstElement().xs.length>0,
                "frozen fixture has a route coordinate for the corruption canary");
            TraceRow route=traces.firstElement();
            int original=route.xs[0];
            route.xs[0]=original==Integer.MAX_VALUE?original-1:original+1;
            String corrupted=rebuildLegacyCanonical();
            route.xs[0]=original;
            require(!legacyCanonical.equals(corrupted) &&
                    legacyCanonical.equals(rebuildLegacyCanonical()),
                "a corrupted frozen route record fails the historical canonical identity check");
        }

        void requireTrailingDataRejected() {
            byte[] corrupted=Arrays.copyOf(rawPayload,rawPayload.length+1);
            corrupted[corrupted.length-1]=1;
            DataInputStream input=new DataInputStream(new ByteArrayInputStream(corrupted));
            try {
                new FrozenFixture(input,seed,corrupted);
                throw new AssertionError("Frozen fixture parser accepted trailing data");
            } catch(IOException rejected) {
                require("Trailing role-width fixture data".equals(rejected.getMessage()),
                    "trailing fixture bytes are rejected after all recorded fields: "+rejected);
            }
        }

        private void requireEndpointConnected(TraceRow trace,String padId,int point,String role,
                java.util.Map<String,PadRow> byId) {
            int x=trace.xs[point],y=trace.ys[point];
            if(padId!=null) {
                PadRow pad=byId.get(padId);
                require(pad!=null,"frozen route "+role+" pad exists: "+trace.sourceId);
                require(x>=pad.land.x && y>=pad.land.y &&
                    (long)x<= (long)pad.land.x+pad.land.width &&
                    (long)y<= (long)pad.land.y+pad.land.height,
                    "frozen route "+role+" point lands inside its recorded pad: "+trace.sourceId);
                return;
            }
            boolean shared=false;
            for(TraceRow other:traces) if(other!=trace && trace.netId.equals(other.netId) &&
                    pointOnTrace(other,x,y)) { shared=true; break; }
            if(!shared) for(HoleRow hole:holes) if(trace.netId.equals(hole.net) &&
                    "VIA".equals(hole.kind) && x==hole.x && y==hole.y) { shared=true; break; }
            require(shared,"frozen route "+role+
                " has no explicit pad and joins same-net copper or a via: "+trace.sourceId);
        }

        private static boolean pointOnTrace(TraceRow trace,int x,int y) {
            for(int i=1;i<trace.xs.length;i++) {
                int x1=trace.xs[i-1],x2=trace.xs[i],y1=trace.ys[i-1],y2=trace.ys[i];
                if(x1==x2 && x==x1 && y>=Math.min(y1,y2) && y<=Math.max(y1,y2) ||
                        y1==y2 && y==y1 && x>=Math.min(x1,x2) && x<=Math.max(x1,x2))
                    return true;
            }
            return false;
        }

        String rebuildLegacyCanonical() {
            StringBuilder result=new StringBuilder();
            result.append("layout=").append(layoutVersion).append('|').append(scoreVersion).append('|');
            Vector<RegionRow> orderedRegions=new Vector<RegionRow>(regions);
            Collections.sort(orderedRegions,new Comparator<RegionRow>() {
                public int compare(RegionRow a,RegionRow b) { return a.id.compareTo(b.id); }
            });
            for(RegionRow row:orderedRegions) {
                result.append("region:").append(PcbConductorGraph.field(row.id))
                    .append(PcbConductorGraph.field(row.label));
                for(String id:row.ids) result.append(PcbConductorGraph.field(id));
            }
            result.append(width).append('x').append(height).append('|');
            appendRect(result,outline); result.append('|'); appendRect(result,tray); result.append('|');
            Vector<ComponentRow> orderedComponents=new Vector<ComponentRow>(components);
            Collections.sort(orderedComponents,new Comparator<ComponentRow>() {
                public int compare(ComponentRow a,ComponentRow b) { return a.id.compareTo(b.id); }
            });
            for(ComponentRow row:orderedComponents) result.append("C:")
                .append(row.fingerprint).append(';');
            Vector<PadRow> orderedPads=new Vector<PadRow>(pads);
            Collections.sort(orderedPads,new Comparator<PadRow>() {
                public int compare(PadRow a,PadRow b) { return a.id.compareTo(b.id); }
            });
            for(PadRow row:orderedPads) result.append("P:").append(row.fingerprint).append(';');
            Vector<LabelRow> orderedLabels=new Vector<LabelRow>(labels);
            Collections.sort(orderedLabels,new Comparator<LabelRow>() {
                public int compare(LabelRow a,LabelRow b) { return a.id.compareTo(b.id); }
            });
            for(LabelRow row:orderedLabels) result.append("L:").append(row.id).append('@')
                .append(row.text).append(':').append(rect(row.bounds)).append(';');
            Vector<TraceRow> orderedTraces=new Vector<TraceRow>(traces);
            Collections.sort(orderedTraces,new Comparator<TraceRow>() {
                public int compare(TraceRow a,TraceRow b) {
                    int value=a.netId.compareTo(b.netId);
                    if(value!=0) return value;
                    value=String.valueOf(a.startPadId).compareTo(String.valueOf(b.startPadId));
                    if(value!=0) return value;
                    value=String.valueOf(a.endPadId).compareTo(String.valueOf(b.endPadId));
                    if(value!=0) return value;
                    value=PcbCopperLayer.valueOf(a.layer).compareTo(PcbCopperLayer.valueOf(b.layer));
                    if(value!=0) return value;
                    if(a.width!=b.width) return a.width<b.width?-1:1;
                    return String.valueOf(a.sourceId).compareTo(String.valueOf(b.sourceId));
                }
            });
            for(TraceRow row:orderedTraces) {
                result.append("T:").append(PcbConductorGraph.field(row.sourceId))
                    .append(row.layer).append(':').append(row.exposure).append(':')
                    .append(row.netId).append(':').append(row.startPadId).append('-')
                    .append(row.endPadId).append('@');
                if(row.width!=9) result.append('W').append(row.width).append(':');
                for(int i=0;i<row.xs.length;i++)
                    result.append(row.xs[i]).append(',').append(row.ys[i]).append(';');
            }
            Vector<HoleRow> orderedHoles=new Vector<HoleRow>(holes);
            Collections.sort(orderedHoles,new Comparator<HoleRow>() {
                public int compare(HoleRow a,HoleRow b) { return a.id.compareTo(b.id); }
            });
            for(HoleRow row:orderedHoles) result.append("H:")
                .append(PcbConductorGraph.field(row.id)).append(row.kind)
                .append(PcbConductorGraph.field(row.net)).append(row.x).append(',').append(row.y)
                .append(',').append(row.drillRadius).append(',').append(row.landRadius)
                .append(',').append(row.exposure);
            return result.toString();
        }

        private static Rectangle readRect(DataInputStream in) throws IOException {
            return new Rectangle(in.readInt(),in.readInt(),in.readInt(),in.readInt());
        }
        private static int readCount(DataInputStream in,int maximum,String field) throws IOException {
            int count=in.readInt();
            if(count<0 || count>maximum) throw new IOException("Invalid "+field+" count");
            return count;
        }
        private static String readString(DataInputStream in) throws IOException {
            int length=in.readInt();
            if(length<0 || length>1024*1024) throw new IOException("Invalid fixture string length");
            byte[] bytes=new byte[length]; in.readFully(bytes);
            return new String(bytes,StandardCharsets.UTF_8);
        }
        private static String readNullableString(DataInputStream in) throws IOException {
            return in.readBoolean()?readString(in):null;
        }
        private static void appendRect(StringBuilder out,Rectangle value) {
            out.append(rect(value));
        }
        private static String rect(Rectangle value) {
            return value.x+","+value.y+","+value.width+","+value.height;
        }
    }

    private static final class RegionRow {
        final String id,label; final Vector<String> ids;
        RegionRow(String id,String label,Vector<String> ids) {
            this.id=id; this.label=label; this.ids=ids;
        }
    }
    private static final class ComponentRow {
        final String id,rotation,side,variant,packageId,fingerprint;
        final int x,y,width,height;
        ComponentRow(String id,int x,int y,int width,int height,String rotation,String side,
                String variant,String packageId,String fingerprint) {
            this.id=id; this.x=x; this.y=y; this.width=width; this.height=height;
            this.rotation=rotation; this.side=side; this.variant=variant;
            this.packageId=packageId; this.fingerprint=fingerprint;
        }
    }
    private static final class PadRow {
        final String id,attachment,side,exposure,fingerprint;
        final int x,y,escapeDx,escapeDy,escapeLength;
        final Rectangle land,probe;
        PadRow(String id,int x,int y,int escapeDx,int escapeDy,int escapeLength,
                Rectangle land,Rectangle probe,String attachment,String side,String exposure,
                String fingerprint) {
            this.id=id; this.x=x; this.y=y; this.escapeDx=escapeDx; this.escapeDy=escapeDy;
            this.escapeLength=escapeLength; this.land=land; this.probe=probe;
            this.attachment=attachment; this.side=side; this.exposure=exposure;
            this.fingerprint=fingerprint;
        }
    }
    private static final class LabelRow {
        final String id,text,targetPad; final Rectangle bounds;
        final int fontSize; final boolean bold;
        LabelRow(String id,String text,Rectangle bounds,int fontSize,boolean bold,String targetPad) {
            this.id=id; this.text=text; this.bounds=bounds; this.fontSize=fontSize;
            this.bold=bold; this.targetPad=targetPad;
        }
    }
    private static final class HoleRow {
        final String id,kind,net,exposure; final int x,y,drillRadius,landRadius;
        HoleRow(String id,String kind,String net,int x,int y,int drillRadius,int landRadius,
                String exposure) {
            this.id=id; this.kind=kind; this.net=net; this.x=x; this.y=y;
            this.drillRadius=drillRadius; this.landRadius=landRadius; this.exposure=exposure;
        }
    }
    private static final class TraceRow {
        final String sourceId,netId,startPadId,endPadId,layer,exposure;
        final int width; final int[] xs,ys;
        TraceRow(String sourceId,String netId,String startPadId,String endPadId,String layer,
                String exposure,int width,int[] xs,int[] ys) {
            this.sourceId=sourceId; this.netId=netId; this.startPadId=startPadId;
            this.endPadId=endPadId; this.layer=layer; this.exposure=exposure;
            this.width=width; this.xs=xs; this.ys=ys;
        }
    }

    private static PcbBoardLayout copyWithRoleWidths(PcbBoardLayout source,
            TroubleshootBoard roleBoard) {
        PcbBoardLayout copy=new PcbBoardLayout(source.getWidth(),source.getHeight(),
            source.getBoardOutline(),source.getPartsTray(),source.getLayoutAlgorithmVersion());
        for(PcbComponentPlacement component:source.getComponents()) copy.addComponent(component);
        for(PcbLayoutRegion region:source.getRegions()) copy.addRegion(region);
        for(PcbPadPlacement pad:source.getPads()) copy.addPad(pad);
        for(PcbSilkscreenLabel label:source.getSilkscreenLabels()) copy.addSilkscreenLabel(label);
        for(PcbBoardHole hole:source.getHoles()) copy.addHole(hole);
        for(PcbTraceGeometry trace:source.getTraces()) {
            BoardNet net=roleBoard.getNet(trace.getNetId());
            if(net==null) throw new IllegalStateException("Missing role net "+trace.getNetId());
            copy.addTrace(new PcbTraceGeometry(trace.getSourceId(),trace.getNetId(),
                trace.getStartPadId(),trace.getEndPadId(),trace.getLayer(),trace.getExposure(),
                trace.getXPoints(),trace.getYPoints(),roleBoard.getPlacementConstraints()
                    .getTraceWidthProfile().widthFor(net.getRoutingRole())));
        }
        return copy;
    }

    private static boolean sameElectricalIdentity(TroubleshootBoard first,TroubleshootBoard second) {
        Vector<String> firstNets=first.getNetIds(),secondNets=second.getNetIds();
        Vector<String> firstParts=first.getComponentIds(),secondParts=second.getComponentIds();
        Vector<String> firstPads=first.getPadIds(),secondPads=second.getPadIds();
        Collections.sort(firstNets); Collections.sort(secondNets);
        Collections.sort(firstParts); Collections.sort(secondParts);
        Collections.sort(firstPads); Collections.sort(secondPads);
        if(!firstNets.equals(secondNets) || !firstParts.equals(secondParts) ||
                !firstPads.equals(secondPads)) return false;
        for(String id:firstPads) {
            BoardPad a=first.getPad(id),b=second.getPad(id);
            if(!a.getComponentId().equals(b.getComponentId()) ||
                    !a.getTerminalId().equals(b.getTerminalId()) || !a.getNetId().equals(b.getNetId()))
                return false;
        }
        return true;
    }

    private static String nonRouteGeometry(PcbBoardLayout layout) {
        StringBuilder out=new StringBuilder(layout.getWidth()+"x"+layout.getHeight()+"|")
            .append(rect(layout.getBoardOutline())).append('|').append(rect(layout.getPartsTray()))
            .append('|').append(layout.componentGeometryFingerprint()).append('|')
            .append(layout.silkscreenGeometryFingerprint()).append('|');
        Vector<PcbPadPlacement> pads=layout.getPads();
        Collections.sort(pads,new Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement a,PcbPadPlacement b) {
                return a.getPadId().compareTo(b.getPadId());
            }
        });
        for(PcbPadPlacement pad:pads) out.append(pad.geometryFingerprint()).append(';');
        for(PcbLayoutRegion region:layout.getRegions()) {
            out.append("region=").append(region.id).append(':').append(region.label).append(':');
            for(String component:region.getComponentIds()) out.append(component).append(',');
        }
        for(PcbBoardHole hole:layout.getHoles()) out.append("hole=").append(hole.fingerprint()).append(';');
        return out.toString();
    }

    private static String tracePathSignature(PcbBoardLayout layout) {
        Vector<PcbTraceGeometry> traces=layout.getTraces();
        Collections.sort(traces,new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry a,PcbTraceGeometry b) {
                return a.getSourceId().compareTo(b.getSourceId());
            }
        });
        StringBuilder out=new StringBuilder();
        for(PcbTraceGeometry trace:traces)
            out.append(trace.getSourceId()).append(':').append(trace.getNetId()).append(':')
                .append(trace.getStartPadId()).append('-').append(trace.getEndPadId()).append(':')
                .append(trace.getLayer()).append(':').append(trace.getExposure()).append(':')
                .append(Arrays.toString(trace.getXPoints())).append(':')
                .append(Arrays.toString(trace.getYPoints())).append(';');
        return out.toString();
    }

    private static String rect(Rectangle value) {
        return value.x+","+value.y+","+value.width+","+value.height;
    }
    private static void report(String name,Check check) {
        try {
            check.run();
            System.out.println("PCB_ROLE_WIDTH_REPLAY_CHECK "+name+" status=LEGAL");
        } catch(RuntimeException failure) {
            System.out.println("PCB_ROLE_WIDTH_REPLAY_CHECK "+name+" status=REJECTED reason="+
                oneLine(failure));
        }
    }
    private static String oneLine(Throwable value) {
        String message=value.getMessage();
        return value.getClass().getSimpleName()+(message==null?"":":"+message.replace('\n',' '));
    }
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    private interface Check { void run(); }
}
