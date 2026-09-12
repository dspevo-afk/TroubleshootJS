package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Free-channel witness independent of area and region labels. Does not claim completed net routing. */
final class PcbAccessPlanner {
    static final int GRID=10;
    static int validate(Rectangle outline,Vector<PcbFootprint> footprints) {
        int width=outline.width/GRID+1,height=outline.height/GRID+1;
        if((long)width*height>700000) throw new PcbPlacementPlanner.Rejected("ACCESS_GRID_BUDGET");
        boolean[] blocked=new boolean[width*height],reached=new boolean[width*height];
        for(PcbFootprint footprint:footprints) {
            Rectangle r=footprint.getPlacement().getRoutingCourtyard(); int margin=(PcbTraceRules.TRACE_WIDTH+1)/2;
            int minX=Math.max(0,(r.x-margin-outline.x)/GRID),maxX=Math.min(width-1,(r.x+r.width+margin-outline.x+GRID-1)/GRID);
            int minY=Math.max(0,(r.y-margin-outline.y)/GRID),maxY=Math.min(height-1,(r.y+r.height+margin-outline.y+GRID-1)/GRID);
            for(int y=minY;y<=maxY;y++) for(int x=minX;x<=maxX;x++) blocked[y*width+x]=true;
        }
        int[] queue=new int[width*height]; int head=0,tail=0;
        // All terminals must reach one shared free component. Seeding every edge
        // separately would incorrectly bless regions divided by a spanning wall.
        for(int y=0;y<height&&tail==0;y++) for(int x=0;x<width&&tail==0;x++) if(x==0||y==0||x==width-1||y==height-1) {
            int key=y*width+x;if(!blocked[key]) { reached[key]=true;queue[tail++]=key; }
        }
        while(head<tail) {
            if((head%4096)==0) GenerationWorkScope.check();
            int key=queue[head++],x=key%width,y=key/width;
            for(int next:new int[]{x>0?key-1:-1,x+1<width?key+1:-1,y>0?key-width:-1,y+1<height?key+width:-1})
                if(next>=0&&!blocked[next]&&!reached[next]) { reached[next]=true; queue[tail++]=next; }
        }
        for(PcbFootprint footprint:footprints) for(PcbPadPlacement pad:footprint.getPads()) {
            // Complete the declared escape before joining a free channel. The envelope check
            // separately proves no neighboring body obstructs that local escape.
            int ex=pad.getX()+pad.getEscapeDx()*(pad.getEscapeLength()+GRID*2);
            int ey=pad.getY()+pad.getEscapeDy()*(pad.getEscapeLength()+GRID*2);
            int gx=(int)Math.round((ex-outline.x)/(double)GRID),gy=(int)Math.round((ey-outline.y)/(double)GRID);
            if(gx<0||gy<0||gx>=width||gy>=height||!reached[gy*width+gx])
                throw new PcbPlacementPlanner.Rejected("DISCONNECTED_ESCAPE_CHANNEL");
        }
        return tail;
    }
    private PcbAccessPlanner() { }
}
