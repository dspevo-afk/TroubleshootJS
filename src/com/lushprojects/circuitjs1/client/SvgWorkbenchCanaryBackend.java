package com.lushprojects.circuitjs1.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/** Tiny developer-only second engine. It cannot access a challenge, runtime, solver or mutation provider. */
final class SvgWorkbenchCanaryBackend implements WorkbenchRenderBackend {
    private final Element container;
    private Element overlay;
    private WorkbenchPhysicalScene scene;
    private Object attachment;
    private PcbViewport.Transform boardProjection, benchProjection;
    SvgWorkbenchCanaryBackend(Element container) { this.container=container; }
    public void attach(WorkbenchPhysicalScene scene,Object attachment) {
        if(overlay!=null)throw new IllegalStateException("SVG renderer already attached");
        this.scene=scene; this.attachment=attachment;
        overlay=Document.get().createDivElement();
        overlay.setAttribute("data-tsj-renderer-canary","svg");
        overlay.setAttribute("style","position:absolute;inset:0;pointer-events:none;background:#e8edef;overflow:hidden");
        container.appendChild(overlay);
    }
    public void present(WorkbenchPhysicalScene scene,Rectangle viewport) {
        if(overlay==null)throw new IllegalStateException("Detached SVG renderer");
        this.scene=scene;
        Rectangle total=scene.board.rectangle().union(scene.tray.rectangle());
        double scale=Math.min(Math.max(1,viewport.width-100)/(double)total.width,
            Math.max(1,viewport.height-100)/(double)total.height);
        double x=(viewport.width-total.width*scale)/2-total.x*scale;
        double y=(viewport.height-total.height*scale)/2-total.y*scale;
        boardProjection=new PcbViewport.Transform(scale,x,y,scene.board.x*2+scene.board.width,scene.face==PcbBoardSide.BOTTOM);
        benchProjection=new PcbViewport.Transform(scale,x,y,0,false);
        StringBuilder svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='100%' height='100%' viewBox='0 0 ")
            .append(viewport.width).append(' ').append(viewport.height).append("' role='img' aria-label='Developer SVG physical board'>")
            .append("<text x='16' y='24' fill='#344c59' font-family='sans-serif' font-size='14'>RENDER-0 / SVG canary / same physical board</text>");
        rect(svg,boardProjection.project(scene.board.rectangle()),"#e0e9df","#607660");
        rect(svg,benchProjection.project(scene.tray.rectangle()),"#d4d9db","#7d898e");
        for(PcbConductorGraph.Surface s:scene.copper.getGraph().getSurfaces()) {
            if(s.edgeId==null || !scene.copper.hasEdge(s.edgeId) || !s.canProbe(scene.face))continue;
            PcbConductorGraph.Edge e=scene.copper.getGraph().getEdges().get(s.edgeId);
            PcbConductorGraph.Junction a=scene.copper.getGraph().getJunctions().get(e.first),b=scene.copper.getGraph().getJunctions().get(e.second);
            Point p=project(a.x,a.y,true),q=project(b.x,b.y,true);
            svg.append("<path fill='none' stroke='#6e987e' stroke-width='").append(Math.max(1,scale*PcbTraceRules.TRACE_WIDTH))
                .append("' d='M").append(p.x).append(' ').append(p.y).append('L').append(q.x).append(' ').append(q.y).append("'/>");
        }
        for(WorkbenchPhysicalScene.Part p:scene.parts) {
            if((p.mounted && p.side!=scene.face) || (!p.mounted && !p.visibleInTray))continue;
            Rectangle b=(p.mounted?boardProjection:benchProjection).project(p.body.rectangle());
            rect(svg,b,p.illuminated?"#f3d782":"#c6cfd4","#344c59");
            svg.append("<text x='").append(b.x).append("' y='").append(b.y-5).append("' fill='#344c59' font-family='sans-serif' font-size='11'>")
                .append(escape(p.label)).append("</text>");
            for(WorkbenchPhysicalScene.Terminal t:p.terminals) {
                Point root=project(t.bodyX,t.bodyY,p.mounted),bend=project(t.bendX,t.bendY,p.mounted);
                Point at=project(t.x,t.y,p.mounted);
                svg.append("<polyline fill='none' stroke='#8195a5' stroke-width='2' points='")
                    .append(root.x).append(',').append(root.y).append(' ').append(bend.x).append(',').append(bend.y)
                    .append(' ').append(at.x).append(',').append(at.y).append("'/>");
                if(!p.mounted || !t.connected)rect(svg,(p.mounted?boardProjection:benchProjection).project(t.probe.rectangle()),"#c4cfd4","#8195a5");
            }
        }
        for(WorkbenchPhysicalScene.Pad p:scene.pads)if(p.accessible(scene.face))dot(svg,project(p.x,p.y,true),"#b6a778");
        overlay.setInnerHTML(svg.append("</svg>").toString());
    }
    public Point project(int x,int y,boolean board) {
        PcbViewport.Transform projection=board?boardProjection:benchProjection;
        return projection==null?null:projection.project(new Point(x,y));
    }
    public WorkbenchRenderHit hitTest(int x,int y,Intent intent) {
        if(attachment==null || boardProjection==null)return null;
        if(intent==Intent.DROP)return drop(x,y);
        boolean probe=intent==Intent.PROBE;
        WorkbenchRenderHit candidate=null;
        if(probe) {
            for(WorkbenchPhysicalScene.Pad p:scene.pads)if(p.accessible(scene.face) && boardProjection.project(p.probe.rectangle()).contains(x,y)) {
                if(candidate!=null)return null;
                candidate=hit(WorkbenchRenderHit.Kind.PAD,p.id,null,-1);
            }
            if(candidate!=null)return candidate;
        }
        for(WorkbenchPhysicalScene.Part p:scene.parts) {
            if((p.mounted && p.side!=scene.face) || (!p.mounted && !p.visibleInTray))continue;
            PcbViewport.Transform projection=p.mounted?boardProjection:benchProjection;
            if(!probe && projection.project(p.body.rectangle()).contains(x,y))return hit(p.mounted?WorkbenchRenderHit.Kind.COMPONENT:WorkbenchRenderHit.Kind.PART,p.mounted?p.componentId:p.id,null,-1);
            if(probe)for(WorkbenchPhysicalScene.Terminal t:p.terminals)if(!t.connected && projection.project(t.probe.rectangle()).contains(x,y))
                return hit(p.mounted?WorkbenchRenderHit.Kind.LEAD:WorkbenchRenderHit.Kind.LOOSE_TERMINAL,p.mounted?p.componentId:p.id,t.padId,t.index);
        }
        return null;
    }
    public Point marker(WorkbenchRenderHit.Kind kind,String id,String secondaryId,int terminal) {
        if(scene==null)return null;
        if(kind==WorkbenchRenderHit.Kind.PAD) {
            WorkbenchPhysicalScene.Pad p=scene.pad(id);
            return p==null?null:project(p.x,p.y,true);
        }
        WorkbenchPhysicalScene.Part p=kind==WorkbenchRenderHit.Kind.COMPONENT || kind==WorkbenchRenderHit.Kind.LEAD?scene.installed(id):scene.part(id);
        if(p==null)return null;
        if(kind==WorkbenchRenderHit.Kind.COMPONENT || kind==WorkbenchRenderHit.Kind.PART)
            return project(p.body.x+p.body.width/2,p.body.y+p.body.height/2,p.mounted);
        for(WorkbenchPhysicalScene.Terminal t:p.terminals)
            if((kind==WorkbenchRenderHit.Kind.LEAD && secondaryId!=null && secondaryId.equals(t.padId)) ||
                    (kind==WorkbenchRenderHit.Kind.LOOSE_TERMINAL && terminal==t.index))return project(t.x,t.y,p.mounted);
        return null;
    }
    private WorkbenchRenderHit drop(int x,int y) {
        if(projected(scene.tray,false).contains(x,y))return hit(WorkbenchRenderHit.Kind.TRAY,"parts-tray",null,-1);
        WorkbenchRenderHit candidate=null;
        for(WorkbenchPhysicalScene.Slot slot:scene.slots)if(slot.side==scene.face && projected(slot.dropBounds,true).contains(x,y)) {
            if(candidate!=null)return null;
            candidate=hit(WorkbenchRenderHit.Kind.SLOT,slot.id,null,-1);
        }
        return candidate;
    }
    private Rectangle projected(WorkbenchPhysicalScene.Bounds b,boolean board) {
        Point a=project(b.x,b.y,board),z=project(b.x+b.width,b.y+b.height,board);
        return new Rectangle(Math.min(a.x,z.x),Math.min(a.y,z.y),Math.max(1,Math.abs(a.x-z.x)),Math.max(1,Math.abs(a.y-z.y)));
    }
    private WorkbenchRenderHit hit(WorkbenchRenderHit.Kind kind,String id,String secondary,int terminal) {
        return new WorkbenchRenderHit(scene.boardIdentity,attachment,kind,id,secondary,terminal);
    }
    public void detach() {
        if(overlay!=null)overlay.removeFromParent();
        overlay=null; scene=null; attachment=null; boardProjection=null; benchProjection=null;
    }
    private static void rect(StringBuilder s,Rectangle r,String fill,String stroke) {
        s.append("<rect x='").append(r.x).append("' y='").append(r.y).append("' width='").append(r.width)
            .append("' height='").append(r.height).append("' fill='").append(fill).append("' stroke='").append(stroke).append("'/>");
    }
    private static void dot(StringBuilder s,Point p,String color) {
        s.append("<circle cx='").append(p.x).append("' cy='").append(p.y).append("' r='4' fill='").append(color).append("' stroke='#344c59'/>");
    }
    private static String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}
