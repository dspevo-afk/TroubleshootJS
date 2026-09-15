package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only physical appearance. Coordinates are board/workbench units, never screen identities. */
final class WorkbenchPhysicalScene {
    static final class Bounds {
        final int x, y, width, height;
        Bounds(Rectangle value) { x=value.x; y=value.y; width=value.width; height=value.height; }
        Rectangle rectangle() { return new Rectangle(x,y,width,height); }
    }
    static final class Pad {
        final String id, componentId, terminalId, netId;
        final int x, y;
        final Bounds land, probe;
        final PcbTerminalAttachment attachment;
        final PcbBoardSide mountingSide;
        final PcbCopperAccess.Exposure exposure;
        Pad(BoardPad pad, PcbPadPlacement placed) {
            id=pad.getId(); componentId=pad.getComponentId(); terminalId=pad.getTerminalId(); netId=pad.getNetId();
            x=placed.getX(); y=placed.getY(); land=new Bounds(placed.getPadBounds()); probe=new Bounds(placed.getProbeBounds());
            attachment=placed.getAttachment(); mountingSide=placed.getMountingSide(); exposure=placed.getExposure();
        }
        boolean accessible(PcbBoardSide face) {
            return exposure == PcbCopperAccess.Exposure.EXPOSED &&
                (attachment == PcbTerminalAttachment.PLATED_THROUGH_HOLE || mountingSide == face);
        }
    }
    static final class Terminal {
        final String name, padId;
        final int index, x, y, bodyX, bodyY, bendX, bendY;
        final Bounds probe;
        final boolean connected;
        Terminal(String name, String padId, int index, Point point, Rectangle probe, boolean connected, Point body, Point bend) {
            this.name=name; this.padId=padId; this.index=index; x=point.x; y=point.y;
            this.probe=new Bounds(probe); this.connected=connected;
            bodyX=body.x; bodyY=body.y; bendX=bend.x; bendY=bend.y;
        }
    }
    static final class Slot {
        final String id;
        final PcbBoardSide side;
        final Bounds dropBounds;
        Slot(PcbComponentPlacement p) { id=p.getComponentId(); side=p.getMountingSide(); dropBounds=new Bounds(p.getDragEnvelope()); }
    }
    static final class Part {
        final String id, componentId, label;
        final PhysicalNameplate nameplate;
        final PhysicalGeometryRealization geometry;
        final PhysicalPartOrientation orientation;
        final PcbBoardSide side;
        final PcbRotation rotation;
        final Bounds body;
        final int poseX, poseY;
        final boolean mounted, illuminated, visibleInTray;
        final Object mountIdentity;
        final List<Terminal> terminals;
        Part(String id, String componentId, String label, PhysicalNameplate nameplate,
                PhysicalGeometryRealization geometry, PhysicalPartOrientation orientation,
                PcbBoardSide side, PcbRotation rotation, int poseX, int poseY, Rectangle body,
                boolean mounted, boolean illuminated, boolean visibleInTray, Object mountIdentity,
                List<Terminal> terminals) {
            this.id=id; this.componentId=componentId; this.label=label; this.nameplate=nameplate;
            this.geometry=geometry; this.orientation=orientation; this.side=side; this.rotation=rotation;
            this.poseX=poseX; this.poseY=poseY; this.body=new Bounds(body); this.mounted=mounted;
            this.illuminated=illuminated; this.visibleInTray=visibleInTray; this.mountIdentity=mountIdentity;
            this.terminals=Collections.unmodifiableList(new ArrayList<Terminal>(terminals));
        }
    }
    final Object boardIdentity, revision, looseIdentity;
    final Bounds board, tray;
    final PcbBoardSide face;
    final PcbConductorGraph.Snapshot copper;
    final List<Pad> pads;
    final List<Part> parts;
    final List<String> componentIds;
    final List<Slot> slots;
    WorkbenchPhysicalScene(Object boardIdentity, Object looseIdentity, Rectangle board, Rectangle tray,
            PcbBoardSide face, PcbConductorGraph.Snapshot copper, List<Pad> pads,
            List<Part> parts, List<String> componentIds, List<Slot> slots) {
        this.boardIdentity=boardIdentity; this.looseIdentity=looseIdentity; revision=new Object();
        this.board=new Bounds(board); this.tray=new Bounds(tray); this.face=face; this.copper=copper;
        this.pads=Collections.unmodifiableList(new ArrayList<Pad>(pads));
        this.parts=Collections.unmodifiableList(new ArrayList<Part>(parts));
        this.componentIds=Collections.unmodifiableList(new ArrayList<String>(componentIds));
        this.slots=Collections.unmodifiableList(new ArrayList<Slot>(slots));
    }
    Pad pad(String id) { for (Pad p:pads) if(p.id.equals(id))return p; return null; }
    Part part(String id) { for(Part p:parts)if(p.id.equals(id))return p; return null; }
    Part installed(String id) { for(Part p:parts)if(p.mounted && id.equals(p.componentId))return p; return null; }
}
