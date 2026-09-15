package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Domain-to-presentation adapter. Mutable owners never cross into a render scene. */
final class WorkbenchPhysicalSceneSource {
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final Object identity=new Object();
    private final HashMap<String,String> mounts=new HashMap<String,String>();
    private final HashMap<String,Object> mountIdentities=new HashMap<String,Object>();
    private Object looseIdentity=new Object();
    private String previousKey, previousLoose;
    private WorkbenchPhysicalScene cached;
    WorkbenchPhysicalSceneSource(GeneratedBoardInstance instance, BoardModificationController modifications) {
        this.instance=instance; this.modifications=modifications;
    }
    WorkbenchPhysicalScene capture(PcbBoardSide face, int trayPage) {
        PcbBoardLayout layout=instance.getPcbLayout();
        PhysicalBoardRuntime runtime=instance.getPhysicalBoardRuntime();
        StringBuilder state=new StringBuilder().append(face).append('/').append(trayPage);
        StringBuilder loose=new StringBuilder().append(trayPage);
        List<PhysicalPart<?>> looseParts=new ArrayList<PhysicalPart<?>>();
        for(WorkbenchPartsProvider provider:runtime.getWorkbenchPartsProviders())looseParts.addAll(provider.getLooseParts());
        for(PhysicalPart<?> part:looseParts)loose.append('|').append(part.getId());
        List<PhysicalPart<?>> parts=new ArrayList<PhysicalPart<?>>();
        for(PhysicalPart<?> p:runtime.getPhysicalParts()) {
            parts.add(p);
            String slot=p.isInstalled()?p.getBoardSlot().getComponentId():null;
            state.append('|').append(p.getId()).append('/').append(slot).append('/').append(p.getOrientation());
            if(slot!=null) {
                StringBuilder mount=new StringBuilder(p.getId());
                for(GeneratedComponentConnectionBinding b:instance.getConnectionBindings().getForComponentOrEmpty(slot))
                    mount.append('/').append(modifications.isLeadConnected(slot,b.getPadId()));
                String signature=mount.toString();
                if(!signature.equals(mounts.get(slot))) { mounts.put(slot,signature); mountIdentities.put(slot,new Object()); }
                state.append('/').append(signature).append('/').append(instance.getOperationalStates().isIlluminated(slot));
            }
        }
        for(String id:instance.getBoard().getComponentIds()) if(runtime.getInstalledPart(id)==null && mounts.containsKey(id)) {
            mounts.remove(id); mountIdentities.remove(id);
        }
        String looseKey=loose.toString();
        if(!looseKey.equals(previousLoose)) { looseIdentity=new Object(); previousLoose=looseKey; }
        String key=state.toString();
        if(cached!=null && key.equals(previousKey) && cached.copper==instance.getCurrentConductorSnapshot())return cached;
        List<WorkbenchPhysicalScene.Pad> pads=new ArrayList<WorkbenchPhysicalScene.Pad>();
        for(PcbPadPlacement p:layout.getPads())pads.add(new WorkbenchPhysicalScene.Pad(instance.getBoard().getPad(p.getPadId()),p));
        List<WorkbenchPhysicalScene.Part> appearances=new ArrayList<WorkbenchPhysicalScene.Part>();
        List<WorkbenchPhysicalScene.Slot> slots=new ArrayList<WorkbenchPhysicalScene.Slot>();
        for(PcbComponentPlacement p:layout.getComponents())slots.add(new WorkbenchPhysicalScene.Slot(p));
        for(PhysicalPart<?> p:parts) {
            boolean mounted=p.isInstalled();
            String slot=mounted?p.getBoardSlot().getComponentId():null;
            PcbComponentPlacement placed=mounted?layout.getComponent(slot):null;
            List<WorkbenchPhysicalScene.Terminal> terminals=new ArrayList<WorkbenchPhysicalScene.Terminal>();
            Rectangle body;
            int looseIndex=looseParts.indexOf(p);
            boolean visible=mounted || (looseIndex>=0 && looseIndex/3==trayPage);
            if(mounted) {
                PhysicalPackageGeometry.Placement geometry=placed.getPhysicalGeometry().placedAt(placed.getPose());
                body=geometry.getBodyBounds();
                for(int i=0;i<p.getTerminalCount();i++) {
                    String padId=null;
                    for(String id:instance.getBoard().getComponent(slot).getPadIds())
                        if(instance.getBoard().getPad(id).getTerminalId().equals(p.getTerminal(i).getTerminalName()))padId=id;
                    GeneratedComponentConnectionBinding binding=instance.getConnectionBindings().getOrNull(padId);
                    boolean connected=binding==null || modifications.isLeadConnected(slot,padId);
                    Point point=geometry.getLeadEndPoint(i,!connected);
                    Point bodyPoint=geometry.getLeadBodyPoint(i), bendPoint=bodyPoint;
                    Rectangle bounds=geometry.getComponentLeadProbeBounds(i,!connected);
                    if(!connected) {
                        List<Rectangle> padKeepouts=new ArrayList<Rectangle>(),traces=new ArrayList<Rectangle>(),bodies=new ArrayList<Rectangle>();
                        for(PcbPadPlacement pad:layout.getPads())padKeepouts.add(pad.getProbeBounds());
                        for(PcbConductorGraph.Surface surface:instance.getCurrentConductorSnapshot().getGraph().getSurfaces())
                            if(surface.edgeId!=null && instance.getCurrentConductorSnapshot().hasEdge(surface.edgeId))traces.add(surface.getBounds());
                        for(PcbComponentPlacement other:layout.getComponents())
                            if(!slot.equals(other.getComponentId()) && runtime.getInstalledPart(other.getComponentId())!=null)
                                bodies.add(other.getPhysicalGeometry().placedAt(other.getPose()).getBodyBounds());
                        WorkbenchLiftedLeadAppearance lifted=WorkbenchLiftedLeadAppearance.project(placed.getPhysicalGeometry(),placed.getPose(),i,padKeepouts,traces,bodies);
                        point=lifted.getTipPoint(); bounds=lifted.getProbeBounds();
                        bodyPoint=lifted.getBodyPoint(); bendPoint=lifted.getBendPoint();
                    }
                    terminals.add(new WorkbenchPhysicalScene.Terminal(p.getTerminal(i).getTerminalName(),padId,i,point,bounds,connected,bodyPoint,bendPoint));
                }
            } else {
                LoosePartPose pose=LoosePartPose.forPart(p.getPackage(),p,layout.getPartsTray(),Math.max(0,looseIndex)%3);
                body=pose.getBodyBounds();
                for(int i=0;i<p.getTerminalCount();i++)
                    terminals.add(new WorkbenchPhysicalScene.Terminal(p.getTerminal(i).getTerminalName(),null,i,
                        pose.getTerminalPoint(i),pose.getProbeBounds(i),false,pose.getLeadBodyPoint(i),pose.getLeadBodyPoint(i)));
            }
            appearances.add(new WorkbenchPhysicalScene.Part(p.getId(),slot,
                mounted?instance.getBoard().getComponent(slot).getDisplayName():p.getPlayerVisibleNameplate().getDisplayName(),
                p.getPlayerVisibleNameplate(),p.getGeometryRealization(),p.getOrientation(),
                mounted?placed.getMountingSide():PcbBoardSide.TOP,mounted?placed.getRotation():PcbRotation.DEG_0,
                mounted?placed.getX():body.x,mounted?placed.getY():body.y,body,mounted,
                mounted && instance.getOperationalStates().isIlluminated(slot),visible,
                mounted?mountIdentities.get(slot):null,terminals));
        }
        cached=new WorkbenchPhysicalScene(identity,looseIdentity,layout.getBoardOutline(),layout.getPartsTray(),face,
            instance.getCurrentConductorSnapshot(),pads,appearances,instance.getBoard().getComponentIds(),slots);
        previousKey=key; return cached;
    }
}
