package com.lushprojects.circuitjs1.client;

/** Adapter for the established production canvas painter and physical hit geometry. */
final class CanvasWorkbenchBackend implements WorkbenchRenderBackend {
    private final PcbWorkbenchRenderer renderer;
    private final CirSim sim;
    private WorkbenchPhysicalScene scene;
    private Object attachment;
    private Graphics frame;
    CanvasWorkbenchBackend(CirSim sim, PcbWorkbenchRenderer renderer) { this.sim=sim; this.renderer=renderer; }
    void beginFrame(Graphics graphics) { frame=graphics; }
    public void attach(WorkbenchPhysicalScene scene, Object attachment) { this.scene=scene; this.attachment=attachment; }
    public void present(WorkbenchPhysicalScene scene, Rectangle viewport) {
        if(attachment==null)throw new IllegalStateException("Detached canvas renderer");
        this.scene=scene;
        try { if(frame!=null)renderer.draw(frame,viewport); } finally { frame=null; }
    }
    public Point project(int x, int y, boolean board) {
        return board?renderer.screenPointForProvider(new Point(x,y)):renderer.screenWorkbenchPointForProvider(new Point(x,y));
    }
    public WorkbenchRenderHit hitTest(int x, int y, Intent intent) {
        if(attachment==null)return null;
        if(intent==Intent.DROP)return drop(x,y);
        if(intent==Intent.SELECT) {
            String part=renderer.findPartId(x,y);
            if(part!=null)return hit(WorkbenchRenderHit.Kind.PART,part,null,-1);
            String component=renderer.findComponentId(x,y);
            return component==null?null:hit(WorkbenchRenderHit.Kind.COMPONENT,component,null,-1);
        }
        ProbeTarget target=renderer.findProbeTarget(sim,x,y);
        if(target instanceof BoardPadProbeTarget)
            return hit(WorkbenchRenderHit.Kind.PAD,((BoardPadProbeTarget)target).getPadId(),null,-1);
        if(target instanceof ComponentLeadProbeTarget) {
            ComponentLeadProbeTarget lead=(ComponentLeadProbeTarget)target;
            return hit(WorkbenchRenderHit.Kind.LEAD,lead.getComponentIdForDeveloperVerification(),lead.getPadIdForDeveloperVerification(),-1);
        }
        if(target instanceof PhysicalPartProbeTarget) {
            PhysicalPartProbeTarget loose=(PhysicalPartProbeTarget)target;
            return hit(WorkbenchRenderHit.Kind.LOOSE_TERMINAL,loose.getPartId(),null,loose.getTerminalIndex());
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
    public void detach() { attachment=null; scene=null; frame=null; }
}
