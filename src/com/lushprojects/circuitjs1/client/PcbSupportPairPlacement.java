package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Advisory package-local support placement from two distinct authoritative nets. */
final class PcbSupportPairPlacement {
    private PcbSupportPairPlacement() { }

    static final class Pair {
        final String ownerId, firstPadId, secondPadId;
        final PcbPadPlacement firstAnchor, secondAnchor;
        final Point target;
        Pair(String owner,String first,String second,PcbPadPlacement a,PcbPadPlacement b,Point target) {
            ownerId=owner;firstPadId=first;secondPadId=second;
            firstAnchor=a;secondAnchor=b;this.target=target;
        }
        long endpointSpan(PcbFootprint source) {
            PcbPadPlacement a=source.getPad(firstPadId),b=source.getPad(secondPadId);
            return Math.abs((long)a.getX()-firstAnchor.getX())+Math.abs((long)a.getY()-firstAnchor.getY())+
                Math.abs((long)b.getX()-secondAnchor.getX())+Math.abs((long)b.getY()-secondAnchor.getY());
        }
    }

    /** No circuit-role claim: a paired-net resistor may be feedback or enable,
     * and a capacitor may be filtering or decoupling. Status LEDs and connectors
     * do not gain this advisory preference. Missing/unplaced peers leave the
     * established connected target authoritative. */
    static Pair find(TroubleshootBoard board,PcbPlacementConstraints constraints,
            PcbFootprint source,Vector<PcbFootprint> placed,Point reference) {
        if(board==null || constraints==null || source==null || placed==null || reference==null)
            throw new IllegalArgumentException("Missing PCB support-pair inputs");
        BoardComponent component=board.getComponent(source.getPlacement().getComponentId());
        if(component==null || component.getPhysicalPackage().isConnector() || component.getPadIds().size()!=2)
            return null;
        String type=component.getType();
        if(!("CAPACITOR".equals(type) || "RESISTOR".equals(type) || "DIODE".equals(type) || "ZENER".equals(type)))
            return null;
        String first=component.getPadIds().get(0),second=component.getPadIds().get(1);
        String firstNet=board.getPad(first).getNetId(),secondNet=board.getPad(second).getNetId();
        if(firstNet.equals(secondNet))return null;
        Pair best=null;boolean bestRegion=false;long bestDistance=Long.MAX_VALUE;
        PcbPlacementConstraints.Part demand=constraints.get(component.getId());
        for(PcbFootprint peer:placed) {
            String owner=peer.getPlacement().getComponentId();
            BoardComponent other=board.getComponent(owner);
            if(other==null || owner.equals(component.getId()) || other.getPhysicalPackage().isConnector() ||
                    other.getPadIds().size()<3)continue;
            String a=null,b=null;
            for(String id:other.getPadIds()) {
                BoardPad pad=board.getPad(id);
                if(firstNet.equals(pad.getNetId()) && constraints.domainForPad(board,first).equals(
                        constraints.domainForPad(board,id)) && (a==null || id.compareTo(a)<0))a=id;
                if(secondNet.equals(pad.getNetId()) && constraints.domainForPad(board,second).equals(
                        constraints.domainForPad(board,id)) && (b==null || id.compareTo(b)<0))b=id;
            }
            if(a==null || b==null)continue;
            PcbPadPlacement pa=peer.getPad(a),pb=peer.getPad(b);
            PcbPadPlacement sa=source.getPad(first),sb=source.getPad(second);
            int originX=source.getPlacement().getX(),originY=source.getPlacement().getY();
            Point target=new Point(PcbCoordinateSystem.checkedInt(Math.round(
                ((long)pa.getX()-(sa.getX()-originX)+(long)pb.getX()-(sb.getX()-originX))/2.0)),
                PcbCoordinateSystem.checkedInt(Math.round(
                ((long)pa.getY()-(sa.getY()-originY)+(long)pb.getY()-(sb.getY()-originY))/2.0)));
            PcbPlacementConstraints.Part otherDemand=constraints.get(owner);
            boolean sameRegion=demand.regionId.equals(otherDemand.regionId);
            long distance=Math.abs((long)target.x-reference.x)+Math.abs((long)target.y-reference.y);
            if(best==null || sameRegion && !bestRegion || sameRegion==bestRegion &&
                    (distance<bestDistance || distance==bestDistance && owner.compareTo(best.ownerId)<0)) {
                best=new Pair(owner,first,second,pa,pb,target);bestRegion=sameRegion;bestDistance=distance;
            }
        }
        return best;
    }
}
