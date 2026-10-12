package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Pure view-space placement for required PCB labels; no board/simulation access. */
final class PcbScreenLabelLayout {
    static final int GAP=2;
    private static final int STEP=4, INNER_RADIUS=24, OWNER_RADIUS=96;
    enum Kind { REFERENCE, NET, TITLE, PROVIDER }

    static final class Label {
        final Graphics.MeasuredText text;
        final Kind kind;
        final Point owner;
        Label(Graphics.MeasuredText text, Kind kind) {
            this.text=text; this.kind=kind;
            owner=transform(text,text.ownerX,text.ownerY);
        }
        Rectangle inkAtScreen(int x,int y) {
            Graphics.MeasuredText t=text;
            double[][] offsets={{-t.left,-t.ascent},{t.right,-t.ascent},
                {-t.left,t.descent},{t.right,t.descent}};
            int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE;
            int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
            for(int i=0;i<offsets.length;i++) {
                int px=(int)Math.round(x+t.a*offsets[i][0]+t.c*offsets[i][1]);
                int py=(int)Math.round(y+t.b*offsets[i][0]+t.d*offsets[i][1]);
                minX=Math.min(minX,px);maxX=Math.max(maxX,px);
                minY=Math.min(minY,py);maxY=Math.max(maxY,py);
            }
            return new Rectangle(minX,minY,Math.max(1,maxX-minX),Math.max(1,maxY-minY));
        }
        Point originalScreen() { return transform(text,text.originalX,text.originalY); }
        Point userPoint(int x,int y) { return inverse(text,x,y); }
        private static Point transform(Graphics.MeasuredText t,double x,double y) {
            return new Point((int)Math.round(t.a*x+t.c*y+t.e),
                (int)Math.round(t.b*x+t.d*y+t.f));
        }
        private static Point inverse(Graphics.MeasuredText t,double x,double y) {
            double determinant=t.a*t.d-t.b*t.c;
            if(Math.abs(determinant)<0.000001)return null;
            double sx=x-t.e,sy=y-t.f;
            return new Point((int)Math.round((t.d*sx-t.c*sy)/determinant),
                (int)Math.round((-t.b*sx+t.a*sy)/determinant));
        }
        private static Rectangle transformedBounds(Graphics.MeasuredText t,
                double left,double top,double right,double bottom) {
            Point a=transform(t,left,top),b=transform(t,right,top),
                c=transform(t,left,bottom),d=transform(t,right,bottom);
            int x=Math.min(Math.min(a.x,b.x),Math.min(c.x,d.x));
            int r=Math.max(Math.max(a.x,b.x),Math.max(c.x,d.x));
            int y=Math.min(Math.min(a.y,b.y),Math.min(c.y,d.y));
            int bot=Math.max(Math.max(a.y,b.y),Math.max(c.y,d.y));
            return new Rectangle(x,y,Math.max(1,r-x),Math.max(1,bot-y));
        }
    }
    static final class Placement {
        final Label label; final int screenX,screenY,userX,userY;
        final Rectangle ink; final Point leaderFrom,leaderTo;
        Placement(Label label,int sx,int sy,int ux,int uy,Rectangle ink,
                Point leaderFrom,Point leaderTo) {
            this.label=label;screenX=sx;screenY=sy;userX=ux;userY=uy;
            this.ink=ink;this.leaderFrom=leaderFrom;this.leaderTo=leaderTo;
        }
    }
    static final class Result {
        final Vector<Placement> placements=new Vector<Placement>();
        final Vector<String> failures=new Vector<String>();
        boolean allPlaced(){return failures.isEmpty();}
    }
    private static final class Candidate {
        final int x,y; final Rectangle ink; final long distance;
        Candidate(int x,int y,Rectangle ink,Point owner) {
            this.x=x;this.y=y;this.ink=ink;
            long dx=(long)x-owner.x,dy=(long)y-owner.y;distance=dx*dx+dy*dy;
        }
    }
    private PcbScreenLabelLayout() { }

    static Result place(Vector<Label> labels,Vector<Rectangle> geometry,
            Rectangle board,Rectangle view) {
        Result result=new Result();
        Vector<Label> ordered=new Vector<Label>(labels);
        Collections.sort(ordered,new Comparator<Label>() {
            public int compare(Label a,Label b) {
                int k=a.kind.compareTo(b.kind);return k!=0?k:a.text.id.compareTo(b.text.id);
            }
        });
        Vector<Rectangle> occupied=new Vector<Rectangle>();
        for(Rectangle r:geometry)occupied.add(copy(r));
        for(Label label:ordered) {
            Point original=label.originalScreen();
            Rectangle originalInk=label.inkAtScreen(original.x,original.y);
            if(!contains(view,label.owner)) {
                // Preserve natural canvas clipping for labels whose owner is offscreen.
                // They must not be pulled into the viewport gutter or reported as
                // placement failures just because the board is panned elsewhere.
                if(intersects(view,originalInk))occupied.add(expand(originalInk,GAP));
                Point user=new Point(label.text.originalX,label.text.originalY);
                result.placements.add(new Placement(label,original.x,original.y,user.x,user.y,
                    originalInk,null,null));
                continue;
            }
            Candidate selected=null;
            if(inside(view,originalInk)&&clear(originalInk,occupied))
                selected=new Candidate(original.x,original.y,originalInk,label.owner);
            if(selected==null) selected=findLocal(label,occupied,view,INNER_RADIUS,STEP);
            if(selected==null) selected=findLocal(label,occupied,view,OWNER_RADIUS,STEP);
            if(selected==null) selected=findGutter(label,occupied,board,view);
            boolean retainedAtOriginal=false;
            if(selected==null) {
                // Preserve required text even when a collision-free slot does not
                // exist. The result records the placement limit for diagnostics.
                result.failures.add(label.text.id);
                selected=new Candidate(original.x,original.y,originalInk,label.owner);
                retainedAtOriginal=true;
            }
            Point user=label.userPoint(selected.x,selected.y);
            if(user==null) {
                if(!retainedAtOriginal)result.failures.add(label.text.id+":singular-transform");
                selected=new Candidate(original.x,original.y,originalInk,label.owner);
                user=new Point(label.text.originalX,label.text.originalY);
                retainedAtOriginal=true;
            }
            Point from=null,to=null;
            if(!retainedAtOriginal&&(selected.x!=original.x||selected.y!=original.y)) {
                from=label.owner;to=nearestPoint(label.owner,selected.ink);
            }
            Rectangle reserved=expand(selected.ink,GAP);
            occupied.add(reserved);
            result.placements.add(new Placement(label,selected.x,selected.y,
                user.x,user.y,selected.ink,from,to));
        }
        return result;
    }
    private static Candidate findLocal(Label label,Vector<Rectangle> occupied,
            Rectangle view,int maxRadius,int step) {
        Vector<Candidate> candidates=new Vector<Candidate>();
        Point o=label.owner;
        if(o==null)return null;
        int limit=maxRadius/step;
        for(int dy=-limit;dy<=limit;dy++)for(int dx=-limit;dx<=limit;dx++) {
            int x=o.x+dx*step,y=o.y+dy*step;
            long dist=(long)dx*dx+(long)dy*dy;
            if(dist> (long)limit*limit)continue;
            Point user=label.userPoint(x,y);if(user==null)continue;
            Rectangle ink=label.inkAtScreen(x,y);
            if(inside(view,ink)&&clear(ink,occupied))candidates.add(new Candidate(x,y,ink,o));
        }
        Collections.sort(candidates,new Comparator<Candidate>() {
            public int compare(Candidate a,Candidate b) {
                if(a.distance!=b.distance)return a.distance<b.distance?-1:1;
                if(a.y!=b.y)return a.y<b.y?-1:1;
                return a.x==b.x?0:(a.x<b.x?-1:1);
            }
        });
        if(candidates.size()==0)return null;
        return candidates.elementAt(0);
    }
    private static Candidate findGutter(Label l,Vector<Rectangle> occupied,
            Rectangle board,Rectangle view) {
        int width=Math.max(1,(int)Math.ceil(l.text.width));
        int[][] slots=new int[32][2];int n=0;
        for(int i=1;i<=8;i++) {
            int x=board.x+(board.width*i)/9,y=board.y+(board.height*i)/9;
            slots[n++]=new int[]{x,board.y-4};slots[n++]=new int[]{x,board.y+board.height+4};
            slots[n++]=new int[]{board.x-4,y};slots[n++]=new int[]{board.x+board.width+width+4,y};
        }
        Vector<Candidate> candidates=new Vector<Candidate>();
        for(int i=0;i<n;i++) {
            int x=slots[i][0],y=slots[i][1];Point user=l.userPoint(x,y);
            if(user==null)continue;Rectangle ink=l.inkAtScreen(x,y);
            if(inside(view,ink)&&clear(ink,occupied))candidates.add(new Candidate(x,y,ink,l.owner));
        }
        Collections.sort(candidates,new Comparator<Candidate>() {
            public int compare(Candidate a,Candidate b) {
                if(a.distance!=b.distance)return a.distance<b.distance?-1:1;
                if(a.y!=b.y)return a.y<b.y?-1:1;
                return a.x==b.x?0:(a.x<b.x?-1:1);
            }
        });return candidates.size()==0?null:candidates.elementAt(0);
    }
    private static boolean clear(Rectangle r,Vector<Rectangle> occupied) {
        Rectangle padded=expand(r,GAP);
        for(Rectangle o:occupied)if(intersects(padded,o))return false;
        return true;
    }
    private static boolean inside(Rectangle area,Rectangle r) {
        return r.x>=area.x&&r.y>=area.y&&r.x+r.width<=area.x+area.width&&
            r.y+r.height<=area.y+area.height;
    }
    private static boolean contains(Rectangle area,Point p) {
        return p!=null&&p.x>=area.x&&p.y>=area.y&&
            p.x<area.x+area.width&&p.y<area.y+area.height;
    }
    private static boolean intersects(Rectangle a,Rectangle b) {
        return a.x<b.x+b.width&&b.x<a.x+a.width&&a.y<b.y+b.height&&b.y<a.y+a.height;
    }
    private static Rectangle expand(Rectangle r,int n) {
        return new Rectangle(r.x-n,r.y-n,r.width+2*n,r.height+2*n);
    }
    private static Rectangle copy(Rectangle r){return new Rectangle(r.x,r.y,r.width,r.height);}
    private static Point nearestPoint(Point p,Rectangle r) {
        int x=Math.max(r.x,Math.min(p.x,r.x+r.width));
        int y=Math.max(r.y,Math.min(p.y,r.y+r.height));return new Point(x,y);
    }
}
