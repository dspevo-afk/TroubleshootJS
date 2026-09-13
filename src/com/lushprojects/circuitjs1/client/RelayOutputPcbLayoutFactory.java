package com.lushprojects.circuitjs1.client;

import java.util.Random;

/** Bounded single-layer channel layout, with explicit clearance between the two isolated domains. */
final class RelayOutputPcbLayoutFactory {
    static PcbBoardLayout create(TroubleshootBoard board, long seed) {
        PcbBoardLayout layout = new PcbBoardLayout(1820,1300,new Rectangle(20,20,1800,1260),
            new Rectangle(1840,40,250,360),SeededPcbLayoutGenerator.CURRENT_VERSION);
        part(layout,board,"J1",80,300,seed,false);
        part(layout,board,"J2",80,1000,seed+1,false);
        part(layout,board,"RDRIVE",500,1000,seed+2,false);
        part(layout,board,"RPD",250,750,seed+3,false);
        part(layout,board,"Q1",950,300,seed+4,false);
        part(layout,board,"K1",600,220,seed+5,false);
        part(layout,board,"D1",600,450,seed+6,false);
        part(layout,board,"J3",1400,500,seed+7,false);
        part(layout,board,"J4",1400,1000,seed+8,false);
        boolean bjt=board.getPad("Q1.B")!=null;
        String base=bjt?"Q1.B":"Q1.G",collector=bjt?"Q1.C":"Q1.D",emitter=bjt?"Q1.E":"Q1.S";
        trace(layout,board,"J1.1","K1.A1",200,340,580,340);
        trace(layout,board,"K1.A1","D1.A",580,340,580,480);
        trace(layout,board,"K1.A2",collector,920,340,920,426,1010,426);
        trace(layout,board,"K1.A2","D1.K",920,340,920,480,850,480);
        trace(layout,board,base,"RPD.1",940,390,940,180,1100,180,1100,730,230,730,230,780);
        trace(layout,board,base,"RDRIVE.2",940,390,940,180,1100,180,1100,970,780,970,780,1030);
        trace(layout,board,"J2.1","RDRIVE.1",200,1040,200,980,480,980,480,1030);
        trace(layout,board,"J1.2","J2.2",200,400,200,500,60,500,60,1170,200,1170,200,1100);
        trace(layout,board,"J1.2","RPD.2",200,400,200,500,60,500,60,880,520,880,530,880,530,780);
        trace(layout,board,"J1.2",emitter,200,400,200,500,60,500,60,850,212,850,212,550,1050,550,1050,426);
        trace(layout,board,"K1.COM","J3.1",670,210,670,80,1300,80,1300,540,1380,540);
        trace(layout,board,"K1.NO","J4.1",830,210,830,120,1200,120,1200,1040,1380,1040);
        trace(layout,board,"J3.2","J4.2",1380,600,1340,600,1340,820,1600,820,1600,1180,1380,1180,1380,1100);
        PcbRouteCanonicalizer.canonicalize(layout);
        label(layout,"J1",90,280); label(layout,"J2",90,980);
        label(layout,"RDRIVE",540,1070); label(layout,"RPD",300,830);
        label(layout,"Q1",960,285); label(layout,"K1",640,425); label(layout,"D1",760,440);
        label(layout,"J3",1420,480); label(layout,"J4",1420,980);
        layout.addSilkscreenLabel(new PcbSilkscreenLabel("board-title","TSJ ISOLATED OUTPUT",
            new Rectangle(300,1220,180,18),12,false,null));
        layout.compactToContent(40+(int)((seed%4+4)%4)*10,40,26);
        layout.positionPartsTrayDisjointFromBoard(); layout.validateGeometry(board); return layout;
    }
    private static void part(PcbBoardLayout layout,TroubleshootBoard board,String id,int x,int y,long seed,boolean rotate) {
        BoardComponent component=board.getComponent(id);
        PhysicalPackageGeometry geometry=component.getPhysicalPackage().geometryForPlacement(new Random(seed),x,layout.getBoardOutline());
        PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,new PcbPackagePose(x,y,
            rotate?PcbRotation.DEG_180:PcbRotation.DEG_0,PcbBoardSide.TOP),geometry);
        layout.addComponent(footprint.getPlacement()); for(PcbPadPlacement pad:footprint.getPads())layout.addPad(pad);
    }
    private static void trace(PcbBoardLayout layout,TroubleshootBoard board,String start,String end,int... middle) {
        PcbPadPlacement a=layout.getPad(start),b=layout.getPad(end);
        int count=middle.length/2+2; int[] x=new int[count],y=new int[count];
        x[0]=a.getX(); y[0]=a.getY(); x[count-1]=b.getX(); y[count-1]=b.getY();
        for(int i=1;i<count-1;i++){x[i]=middle[(i-1)*2];y[i]=middle[(i-1)*2+1];}
        layout.addTrace(new PcbTraceGeometry(board.getPad(start).getNetId(),start,end,x,y));
    }
    private static void label(PcbBoardLayout layout,String id,int x,int y) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:"+id,id,new Rectangle(x,y,id.length()*8+4,18),12,false,null));
    }
}
