package com.lushprojects.circuitjs1.client;

/** A visible trace/via probes an actual endpoint on its CURRENT physical island. */
final class BoardCopperProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String surfaceId,padId;
    private final CopperProbeProjection projection;
    private final PcbConductorGraph.Snapshot copper;
    BoardCopperProbeTarget(CirSim sim,GeneratedBoardInstance instance,String surfaceId,CopperProbeProjection projection) {
        this.sim=sim; this.instance=instance; this.surfaceId=surfaceId; this.projection=projection;
        copper=instance.getCurrentConductorSnapshot();
        padId=PcbCopperProbeAccess.connectedPad(copper,surfaceId);
    }
    public boolean isValid() {
        return sim.getGeneratedBoardInstance()==instance && instance.getCurrentConductorSnapshot()==copper &&
            padId!=null && projection.canProbeCopper(surfaceId) && instance.getSimulationBindings().getEndpoint(padId)!=null;
    }
    public boolean isSameTarget(ProbeTarget other) {
        if(!(other instanceof BoardCopperProbeTarget)) return false;
        BoardCopperProbeTarget target=(BoardCopperProbeTarget)other;
        return instance==target.instance && copper==target.copper && surfaceId.equals(target.surfaceId);
    }
    public Point getMarkerPoint() {
        Point point=isValid()?projection.getCopperPoint(surfaceId):null;
        return projection.isBoardPointVisible(point)?point:null;
    }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return isValid()?instance.getSimulationBindings().getEndpoint(padId):null;
    }
    String getSurfaceId() { return surfaceId; }
}
