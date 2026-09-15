package com.lushprojects.circuitjs1.client;

/** Presentation attachment and stable-hit resolver. Electrical actions stay with existing owners. */
final class WorkbenchRenderHost implements PhysicalProbeProjection {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final PcbWorkbenchRenderer physicalView;
    private final WorkbenchPhysicalSceneSource source;
    private final CanvasWorkbenchBackend production;
    private WorkbenchRenderBackend active;
    private Object attachment;
    private Rectangle area=new Rectangle(0,0,1,1);
    WorkbenchRenderHost(CirSim sim,GeneratedBoardInstance instance,BoardModificationController modifications,PcbWorkbenchRenderer physicalView) {
        this.sim=sim; this.instance=instance; this.physicalView=physicalView;
        source=new WorkbenchPhysicalSceneSource(instance,modifications);
        production=new CanvasWorkbenchBackend(sim,physicalView);
        replace(production);
    }
    WorkbenchPhysicalScene scene() { return source.capture(physicalView.getViewingFace(),physicalView.getTrayPage()); }
    void draw(Graphics graphics,Rectangle viewport) {
        area=new Rectangle(viewport);
        if(active==null)return;
        if(active==production)production.beginFrame(graphics);
        active.present(scene(),area);
    }
    void replace(WorkbenchRenderBackend replacement) {
        if(replacement==null)throw new IllegalArgumentException("Missing renderer");
        if(active!=null)active.detach();
        attachment=new Object(); active=replacement;
        try { active.attach(scene(),attachment); }
        catch(RuntimeException failure) {
            active.detach(); active=null; attachment=null; throw failure;
        }
    }
    void restoreProduction() { replace(production); }
    boolean isProduction() { return active==production; }
    Point projectForDeveloperVerification(int x,int y,boolean board) { return active.project(x,y,board); }
    void detach() { if(active!=null)active.detach(); active=null; attachment=null; }
    WorkbenchRenderHit hit(int x,int y,boolean probe) {
        if(active==null || !area.contains(x,y))return null;
        // Refresh dynamic/mount state before accepting a hit from any engine.
        WorkbenchPhysicalScene current=scene();
        active.present(current,area);
        return active.hitTest(x,y,probe?WorkbenchRenderBackend.Intent.PROBE:WorkbenchRenderBackend.Intent.SELECT);
    }
    WorkbenchRenderHit dropHit(int x,int y) {
        if(active==null || !area.contains(x,y))return null;
        active.present(scene(),area);
        return active.hitTest(x,y,WorkbenchRenderBackend.Intent.DROP);
    }
    boolean accepts(WorkbenchRenderHit hit) {
        return active!=null && sim.getGeneratedBoardInstance()==instance && hit!=null &&
            hit.boardIdentity==scene().boardIdentity && hit.attachment==attachment && hit.kind!=null && hit.id!=null;
    }
    String selectedComponent(WorkbenchRenderHit hit) {
        return accepts(hit) && hit.kind==WorkbenchRenderHit.Kind.COMPONENT && scene().installed(hit.id)!=null?hit.id:null;
    }
    String selectedPart(WorkbenchRenderHit hit) {
        WorkbenchPhysicalScene.Part part=accepts(hit)?scene().part(hit.id):null;
        return hit!=null && hit.kind==WorkbenchRenderHit.Kind.PART && part!=null && !part.mounted && part.visibleInTray?hit.id:null;
    }
    ProbeTarget resolve(WorkbenchRenderHit hit) {
        if(!accepts(hit))return null;
        ProbeTarget target=null;
        if(hit.kind==WorkbenchRenderHit.Kind.PAD && canProbePad(hit.id))
            target=new BoardPadProbeTarget(sim,instance,hit.id,this);
        else if(hit.kind==WorkbenchRenderHit.Kind.LEAD && hit.secondaryId!=null && getComponentLeadPoint(hit.id,hit.secondaryId)!=null)
            target=new ComponentLeadProbeTarget(sim,instance,hit.id,hit.secondaryId,this);
        else if(hit.kind==WorkbenchRenderHit.Kind.LOOSE_TERMINAL && isLoosePartVisibleOnCurrentPage(hit.id)) {
            PhysicalPart<?> part=instance.getPhysicalBoardRuntime().getPart(hit.id);
            if(hit.terminal<0 || hit.terminal>=part.getTerminalCount())return null;
            PhysicalPartRenderProbeProvider provider=part.getRenderMetadata().getLooseProbeProvider();
            target=provider==null?new PhysicalPartProbeTarget(sim,instance,hit.id,hit.terminal,this):
                provider.createLooseProbeTarget(sim,instance,part,hit.terminal,this);
        }
        return target!=null && target.isValid()?target:null;
    }
    public boolean hasPad(String id) { return active!=null && scene().pad(id)!=null; }
    public boolean canProbePad(String id) {
        WorkbenchPhysicalScene current=scene();
        WorkbenchPhysicalScene.Pad pad=current.pad(id);
        if(active==null || pad==null || !pad.accessible(current.face))return false;
        // A different mounted package can cover an otherwise exposed pad.
        for(WorkbenchPhysicalScene.Part part:current.parts)
            if(part.mounted && part.side==current.face && !part.componentId.equals(pad.componentId) && part.body.rectangle().contains(pad.x,pad.y))return false;
        return true;
    }
    public Point getPadPoint(String id) {
        WorkbenchPhysicalScene.Pad pad=scene().pad(id);
        if (active != null) active.present(scene(), area);
        return active==null || pad==null?null:active.marker(WorkbenchRenderHit.Kind.PAD,id,null,-1);
    }
    public Point getComponentLeadPoint(String id,String padId) {
        WorkbenchPhysicalScene current=scene();
        if (active != null) active.present(current, area);
        WorkbenchPhysicalScene.Part part=current.installed(id);
        if(active==null || part==null || part.side!=current.face)return null;
        for(WorkbenchPhysicalScene.Terminal t:part.terminals)if(padId.equals(t.padId) && !t.connected)return active.marker(WorkbenchRenderHit.Kind.LEAD,id,padId,-1);
        return null;
    }
    public Point getLooseTerminalPoint(String id,int terminal) {
        WorkbenchPhysicalScene current=scene();
        if (active != null) active.present(current, area);
        WorkbenchPhysicalScene.Part part=current.part(id);
        if(active==null || part==null || part.mounted || !part.visibleInTray)return null;
        for(WorkbenchPhysicalScene.Terminal t:part.terminals)if(t.index==terminal)return active.marker(WorkbenchRenderHit.Kind.LOOSE_TERMINAL,id,null,terminal);
        return null;
    }
    public boolean isBoardPointVisible(Point point) { return active!=null && point!=null && area.contains(point.x,point.y); }
    public Object captureInstalledTargetIdentity(String id,String padId) {
        WorkbenchPhysicalScene.Part part=scene().installed(id);
        return part==null?null:part.mountIdentity;
    }
    public boolean isInstalledTargetIdentityCurrent(String id,String padId,Object token) {
        return active!=null && token!=null && token==captureInstalledTargetIdentity(id,padId);
    }
    public Object captureLooseProjectionToken() { return active==null?null:scene().looseIdentity; }
    public boolean isLooseProjectionTokenCurrent(Object token) { return active!=null && token!=null && token==scene().looseIdentity; }
    public boolean isLoosePartVisibleOnCurrentPage(String id) {
        WorkbenchPhysicalScene.Part part=scene().part(id);
        return active!=null && part!=null && !part.mounted && part.visibleInTray;
    }
}
